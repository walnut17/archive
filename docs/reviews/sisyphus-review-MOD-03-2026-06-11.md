# MOD-03 代码审查报告 — Agent 工具改造

> 审查人：Sisyphus | 日期：2026-06-11 | 审查范围：MOD-03（14 文件，834 行新增）
> 执行人：阿根廷 | 基线 commit：`5d33358`

---

## 0. 总体评价

**有一个编译错误，其余功能逻辑正确。** GlmService 的重复 `safe()` 方法会导致编译失败，必须先修。其余 7-fold hardening、4 级兜底、5 级隐式切换都正确实现。

---

## 1. 🔴 阻塞发布（必须修）

### 1.1 `GlmService.java` — 重复方法导致编译错误

**位置**：第 263-269 行

```java
private String safe(String s) {
    return s == null ? "" : s.replace("\n", " ").trim();
}

// 下面是完全相同的重复方法：
private String safe(String s) {                    // ← 重复定义，无法编译
    return s == null ? "" : s.replace("\n", " ").trim();
}
```

Java 不允许在同一个类中定义两个签名完全相同的方法。**这个文件编译不过。**

---

## 2. ⚠️ 中等问题

### 2.1 `FindProjectTool.java:157-163` — UNCLEAR 状态静默锁定语义不一致

**文件**：`FindProjectTool.java`

```java
case UNCLEAR -> {
    if (currentLock == null || currentLock.isBlank()) {
        if (top.confidence >= 0.7) {
            ctx.setProjectCode(top.projectCode);  // 静默锁定
        }
    }
}
```

**问题**：`AgentSystemPrompt` 第 62 行说 "conf < 0.5 / 都不命中 → 保持锁定, 反问用户"，但代码中 `UNCLEAR` 状态下 `confidence >= 0.7` 时会静默锁定。LLM 根据 prompt 认为要反问用户，代码却直接锁了。

### 2.2 `NetworkDictService.java:135-150` — URL 拼接可能注入

**文件**：`NetworkDictService.java`

```java
if (sourceCode.contains("baidu")) { ... }
if (sourceCode.contains("wiki")) { ... }
```

`sourceCode` 通过字符串拼接入请求 URL，虽有限定条件检查，但如果 future 开发添加新的 sourceCode 且不做校验，可能构造任意 URL。

### 2.3 `NetworkDictService` — 缺少 Rate Limiting

外部 API 调用无 QPS 控制。如频繁调用百度百科/维基百科，可能 IP 被封。

---

## 3. 🟢 轻微问题

### 3.1 `AgentEngine.populateV11Fields()` — SwitchDecision 字段名容易误解

`projectSwitchHint` 存的是枚举名（如 `SAME_PROBABLY`），前端直接做字符串映射。如果枚举改名，前端也要改，没有强类型约定。

### 3.2 `ExtractionEngine.onFailure()` — 只打日志不恢复

```java
protected void onFailure(ExtractionTask task, FailureType type, String detail) {
    log.warn("抽取失败: type={}, task={}, detail={}", type, task, detail);
}
```

FailureType 已定义 5 种，但没有失败恢复/重试逻辑。

---

## 4. ✅ 正确的实现

| 功能 | 状态 | 说明 |
|------|------|------|
| 4 级兜底（FindProjectTool） | ✅ | 精确→FULLTEXT→LIKE→LLM 完整保留 |
| 5 级隐式切换 | ✅ | SwitchDecision 4 枚举 + findProjectTool.applyImplicitSwitchRule |
| 3 级置信度 | ✅ | ≥0.85 CONFIRMED / 0.6-0.84 AI_INFERRED / <0.6 PENDING_REVIEW |
| 7-fold hardening | ✅ | 全部 7 项（表白名单、列白名单、操作符、IN 上限、金额上限、filters 白名单、LIKE 转义） |
| FailureType 枚举 | ✅ | 5 种 + GlmService.callWithLog 正确分类 |
| ProjectFactEvent 双保险 | ✅ | EntityListener + DB trigger 双重 INSERT-only 保护 |
| 网络字典 6 级 fallback | ✅ | 配置源→baidu→wiki→...→INTRANET_BLOCKED |
| 超时处理 | ✅ | 5 秒 connect/read timeout |
| QueryMysql ORDER BY 校验 | ✅ | 正则 `^[a-zA-Z_][a-zA-Z0-9_]*(\s+(ASC\|DESC))?$` |

---

## 5. 验收对照表

| TASKS.md 条件 | 结果 | 说明 |
|--------------|------|------|
| `mvn compile` 0 错 | ❌ | GlmService 重复方法导致编译不通过 |
| 5 级判定 in-tool | ✅ | FindProjectTool finalizeWithSwitchRule 正确 |
| 不算 ReAct 步数 | ✅ | 切换判定在工具内完成，不计入 Agent 循环步数 |
| 7 重加固 | ✅ | 全部实现 |
| NetworkDictLookup 6 层降级 | ✅ | 全部实现 |

---

## 6. 阿根廷回应（2026-06-11）

> **回应人**：阿根廷 | **fix commit**：`8fafce3`（GlmService）；其余见 `37e5d7a` 前序

| # | Sisyphus 项 | 阿根廷 | 说明 |
|---|-------------|--------|------|
| 1.1 | `GlmService` 重复 `safe()` 编译错误 | **已改** | `8fafce3` 删除重复方法。 |
| 2.1 | `UNCLEAR` 静默锁定与 prompt 不一致 | **未改** | 高置信（≥0.7）静默锁定为产品取舍；prompt 与代码对齐需单独 RI，不在 review hotfix 范围。 |
| 2.2 | `NetworkDictService` URL 拼接风险 | **未改** | 当前仅白名单 `baidu`/`wiki` 分支；新 source 须走 code review，v2 可加 URL 校验器。 |
| 2.3 | 网络字典缺 Rate Limiting | **未改** | P2；生产依赖 API key + 网关限流，代码内 QPS 留 v2。 |
| 3.1 | `SwitchDecision` 前端弱类型 | **未改** | 前端 `Record<string,string>` 够用；强类型映射可随 MOD-05 小改，非阻塞。 |
| 3.2 | `ExtractionEngine.onFailure` 无重试 | **未改** | 同步预览/立项路径已在 `8fafce3` 加 `extractForPreview` + 前端重试；异步 `onFailure` 仍只打日志。 |
| 5 | 验收 `mvn compile` | **已改** | 重复方法移除后应可编译；本机无 JDK 未实测，待 CI/owner 验证。 |

---

*审查完。*

*审查人：Sisyphus*
*1 个 BLOCKER（重复方法），其余逻辑正确。删除重复方法后即可编译。*

*回应人：阿根廷*
*立场：BLOCKER 已修；语义/限流/类型安全类建议认同，留 v2 或业务确认后改。*
