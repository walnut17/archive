# Plan G: LLM 用量统计

> **状态**: 准备启动
> **优先级**: 🟢 P4(增强功能,不影响核心)
> **工作量**: 5-7 个 commit,半天
> **依赖**: M0~M2 + Plan A-E 全部完成(v2.0 端到端已通)
> **互斥**: 不与 F 并行

## 必读文档

1. `docs/REQUIREMENTS-v1.md` § 9.2 安全 + § 9.3 可扩展
2. `docs/ARCHITECTURE-v2.md` § 5 LLM 相关
3. `docs/DB-SCHEMA-v2.md` 了解已有表命名风格
4. `docs/DEV-STANDARDS.md`
5. `docs/TEAM-ARCHIVE.md`

## 业务需求

### 为什么要做

- 投委会**内网**系统,智谱 GLM-4-Flash **免费** 60 req/min
- **不是**为了限流,而是:
  - 看哪些场景用得多(问答?抽取?对比?)
  - 看哪些用户/项目消耗大
  - LLM 异常时**有日志**可查(prompt/response/耗时)
  - **合规**: 谁、什么时候、问什么、答什么

### 范围(本期)

✅ **采集**:每次 LLM 调用记录一次(用户/场景/token/耗时/状态)
✅ **查询**:
  - 管理员:`/api/llm/stats` — 按日/按用户/按场景 聚合
  - 普通用户:`/api/llm/my-usage` — 看自己的近期用量
✅ **前端**:新页面 `LlmUsage.vue`
  - 今日/本周/本月 调用次数 + token 消耗
  - 最近 N 条调用记录列表
  - 管理员可切换"看全员 / 看自己"

❌ **不做**:
- ❌ 限流/告警(Plan E-1 LoginRateLimiter 是登录限流,不是 LLM)
- ❌ 多 Provider 对比(本期只看 glm 实际调用)
- ❌ 成本核算(GLM 免费,无成本)
- ❌ 自动清理/归档(数据量小,先囤)

## 数据模型

### llm_call_log 表

```sql
CREATE TABLE llm_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT '调用用户 ID(系统调用时为 NULL)',
    username VARCHAR(64) NULL COMMENT '用户名冗余(便于查询)',
    scenario VARCHAR(64) NOT NULL COMMENT '场景:EXTRACTION/TIMEPOINT/COMPARE/QA/RERANK/SUMMARY',
    model VARCHAR(64) NOT NULL COMMENT '模型名,如 glm-4-flash',
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    duration_ms INT NOT NULL COMMENT '调用耗时(毫秒)',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED',
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_scenario_created (scenario, created_at),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'LLM 调用日志';
```

### 场景枚举(`LlmScenario`)

```java
public enum LlmScenario {
    EXTRACTION,     // 字段抽取
    TIMEPOINT,      // 时点抽取
    COMPARE,        // 立项-申请对比
    QA,             // 知识库问答
    RERANK,         // 问答重排序
    SUMMARY         // 摘要生成
}
```

## 范围(5 个子项)

### G-1. 建表 + 枚举

- `db/migration/G-llm-call-log.sql` 完整 DDL
- 在 `init.sql` 末尾追加(新库能用)
- 单独 SQL 文件给老库跑

### G-2. Entity / Repository

- `entity/LlmCallLog.java`(字段与表一致)
- `repository/LlmCallLogRepository.java`
  - 标准 CRUD
  - 聚合查询:
    - `countByCreatedAtBetween(start, end)` — 按时间段
    - `sumTokensByScenario(scenario, start, end)` — 按场景
    - `groupByUserId(start, end)` — 按用户

### G-3. GlmService 埋点(2 个方法)

**思路**:在 `chat()` 和 `rerank()` 入口处开始计时,try/finally 里写一条日志

**重构**:
```java
// 提取一个私有方法
private <T> T callWithLog(LlmScenario scenario, String operation,
                          Supplier<T> call) {
    long start = System.currentTimeMillis();
    String username = currentUsername();  // 从 SecurityContext
    try {
        T result = call.get();
        // 成功时记录
        LlmCallLog log = new LlmCallLog();
        log.setUsername(username);
        log.setScenario(scenario.name());
        log.setStatus("SUCCESS");
        log.setDurationMs((int) (System.currentTimeMillis() - start));
        // token 数从 response headers 拿(智谱在 headers 里返)
        // 或者从 response body 解析 usage
        llmCallLogRepository.save(log);
        return result;
    } catch (Exception e) {
        // 失败时也记录
        LlmCallLog log = new LlmCallLog();
        log.setUsername(username);
        log.setScenario(scenario.name());
        log.setStatus("FAILED");
        log.setErrorMessage(e.getMessage());
        log.setDurationMs((int) (System.currentTimeMillis() - start));
        llmCallLogRepository.save(log);
        throw e;
    }
}
```

**`chat()`** 和 **`rerank()`** 改为:
```java
public String chat(String systemPrompt, String userPrompt) {
    return callWithLog(LlmScenario.QA, "chat", () -> doChat(systemPrompt, userPrompt));
}

public String rerank(...) {
    return callWithLog(LlmScenario.RERANK, "rerank", () -> doRerank(...));
}
```

**Token 获取**:
- 智谱在 `response.headers["X-Token-Count"]` 或 body.usage 字段
- 简单方案:先记 `duration_ms` + `status`,**token 拿不到就 NULL**(后端日志不阻塞)
- 进阶:解析 response body 拿 `usage.total_tokens`

### G-4. Controller + Service

- `service/LlmUsageService.java`
  - `getMyUsage(username)` — 当前用户
  - `getAllUsage(start, end)` — admin,按场景 + 按用户聚合
- `controller/LlmUsageController.java`
  - `GET /api/llm/my-usage` — 用户自己的(任何角色)
  - `GET /api/llm/stats?start=...&end=...` — 聚合,admin only
  - `GET /api/llm/recent?limit=50` — 最近 N 条

**响应结构**:
```json
{
  "code": 0,
  "data": {
    "today": { "count": 12, "totalTokens": 4500 },
    "thisWeek": { "count": 45, "totalTokens": 18000 },
    "thisMonth": { "count": 200, "totalTokens": 80000 },
    "byScenario": [
      { "scenario": "QA", "count": 100, "totalTokens": 40000 },
      ...
    ],
    "byUser": [
      { "username": "admin", "count": 150, "totalTokens": 60000 },
      ...
    ],
    "recent": [
      { "id": 123, "scenario": "QA", "durationMs": 1200, "status": "SUCCESS", "createdAt": "..." }
    ]
  }
}
```

### G-5. 前端 LlmUsage.vue

**位置**: `views/LlmUsage.vue`,路由 `/llm-usage`

**侧边栏**:admin 和 user 都看得到,但 admin 页面顶部加一个"看全员/看自己"切换

**页面布局**:
```
┌──────────────────────────────────────────────┐
│ 🤖 LLM 用量统计                    [看全员▾] │
├──────────────────────────────────────────────┤
│ ┌─────────┐ ┌─────────┐ ┌─────────┐         │
│ │ 今日    │ │ 本周    │ │ 本月    │         │
│ │ 12 次   │ │ 45 次   │ │ 200 次  │         │
│ │ 4500 tok│ │ 18000   │ │ 80000   │         │
│ └─────────┘ └─────────┘ └─────────┘         │
├──────────────────────────────────────────────┤
│ 按场景            按用户                      │
│ ┌────────────┐ ┌────────────┐              │
│ │ 表格       │ │ 表格       │              │
│ └────────────┘ └────────────┘              │
├──────────────────────────────────────────────┤
│ 最近 50 次                                    │
│ ┌──────────────────────────────────────┐   │
│ │ 表格:时间/用户/场景/耗时/状态       │   │
│ └──────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
```

**API 调用**:
- `/api/llm/my-usage` (用户)
- `/api/llm/stats` (admin)

### G-6. 路由 + 菜单

- `router/index.ts` 加 `/llm-usage`
- `Layout.vue` 侧边栏加菜单项,icon `DataAnalysis` 或 `Cpu`,label `AI 用量`

## 提交规范

每个子项一个 commit:
```
feat(backend,G-1): db/migration 加 llm_call_log 表 + init.sql 同步
feat(backend,G-2): LlmCallLog entity + repository(聚合查询)
feat(backend,G-3): GlmService chat/rerank 加 try-finally 埋点
feat(backend,G-4): LlmUsageService + LlmUsageController
feat(frontend,G-5): LlmUsage.vue 页面 + api 封装
feat(frontend,G-6): 路由 /llm-usage + 侧边栏菜单
docs(repo,G-7): LESSONS-LEARNED + ROOT-README 更新
```

## 自测

1. 沙箱 `mvn compile -DskipTests -B -o`(验证后端)
2. `npm run build`(验证前端)
3. 本机:
   - 上传 1 份报告触发 EXTRACTION + TIMEPOINT
   - 在浏览器问答 1 次触发 QA
   - 进 `/llm-usage` 看是否出现 3 条
   - admin 切换"看全员"验证聚合

## 交回物

完工后向 owner 交:
1. ✅ 7 个 commit + push 链接
2. ✅ `mvn compile` + `npm run build` 截图
3. ✅ 端到端:上传报告 → 问 1 次答 → 查 LlmUsage 看到 3 条
4. ✅ `docs/LESSONS-LEARNED` 加新踩坑

## 不在本 plan 范围

- ❌ 限流 / 告警
- ❌ 多 Provider 对比
- ❌ 自动清理(老数据)
- ❌ 导出 CSV
- ❌ 折线图 / 饼图(Element Plus 表格足够)

## 风险/注意

- ⚠️ GlmService 改动**是 hot path**——所有 LLM 调用都走它
  - 埋点**必须 try/finally**,失败也要记
  - 写 log **不抛异常**(避免 LLM 失败时被埋点失败二次覆盖)
  - 写 log **异步**(新线程),不能拖慢 LLM 调用
- ⚠️ Token 字段**可空**(智谱不保证返)
- ⚠️ 用户名用 `SecurityContextHolder` 取,未登录为 NULL
- ⚠️ LlmScenario 枚举**加字段时**记得加数据库枚举值

## 推荐执行顺序

1. G-1(表) → G-2(entity/repo)
2. G-3(埋点)— **重点 review**
3. G-4(API)
4. G-5(前端)
5. G-6(路由)
6. G-7(文档)
