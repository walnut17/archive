# 投委会档案管理系统 — 开发标准与交付规范

> 文档说明: 任何开发 Agent(包括我、团队成员、未来的自动化工具)启动本项目开发前必读
> 版本: v1 / 2026-06-08
> 阅读对象: 写代码的 Agent / Code Reviewer / 集成者
> 配套: `REQUIREMENTS-v1.md` / `ARCHITECTURE-v2.md` / `DB-SCHEMA-v2.md` / `TEAM-ARCHIVE.md`

---

## 1. 项目概览

| 项 | 值 |
|---|---|
| 项目名 | 投委会档案管理系统 |
| 仓库 | `git@gitee.com:frisker/projects-online.git` |
| 工作分支 | `minimax` |
| 部署目标 | Windows Server 2012 R2,单机 |
| 后端 | Spring Boot 3.3 + JPA + MySQL 8.0.16 |
| 前端 | Vue 3 + TypeScript + Element Plus + Vite + Pinia |
| 部署 | WinSW + Caddy + MySQL |
| 检索 | MySQL FULLTEXT ngram(**不向量化**) |
| LLM | 智谱 GLM-4-Flash(可切换 OpenAI / Mock) |

---

## 2. 仓库结构(必读)

```
projects-online/
├── backend/
│   ├── src/main/java/com/archive/
│   │   ├── ArchiveApplication.java
│   │   ├── common/        # ApiResponse / GlobalExceptionHandler / StorageService
│   │   ├── config/        # ConfigJsonLoader / AuditorAware
│   │   ├── controller/    # Auth/Health/Project/Proposal/Material/MaterialVersion/Qa/Todo/Dict/Trigger/...
│   │   ├── dto/           # Request/Response/Page
│   │   ├── entity/        # BaseEntity + 实体
│   │   ├── repository/    # JpaRepository
│   │   ├── security/      # JwtUtil / JwtAuthFilter / SecurityConfig
│   │   ├── service/       # 业务服务
│   │   ├── engine/        # [新] TriggerEngine / TimepointExtractor / ComparisonEngine
│   │   ├── provider/      # [新] LLMProvider 接口 + GLM/OpenAI/Mock 实现
│   │   └── extractor/     # [新] ExtractionEngine / 内置 extractor
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/init.sql                # 沿用
│   │   ├── db/migration/M2-*.sql      # 沿用
│   │   └── db/migration/v2-*.sql      # [新] v2 schema(详见 DB-SCHEMA-v2.md)
│   ├── startup.ps1
│   └── healthcheck.ps1
├── frontend/src/
│   ├── api/               # http.ts + 各模块 *.ts
│   ├── router/            # index.ts
│   ├── store/             # auth.ts / todo.ts / dict.ts
│   ├── views/             # Login/Layout/Dashboard + 业务页
│   └── components/        # 通用组件
├── docs/
│   ├── REQUIREMENTS-v1.md         # 业务需求
│   ├── ARCHITECTURE-v2.md         # 架构方案
│   ├── DB-SCHEMA-v2.md            # 数据库 schema
│   ├── SIMILAR-PRODUCTS.md        # 调研
│   ├── ARCH-REUSE-AUDIT.md        # 沿用评估
│   ├── DEV-STANDARDS.md           # 本文档
│   ├── TEAM-ARCHIVE.md            # 团队档案
│   ├── M1-README.md               # 沿用
│   ├── M1-TEST-TASKS.md           # 沿用
│   ├── LESSONS-LEARNED.md         # 沿用
│   └── M*-TEST-TASKS.md           # [新] 每个 milestone 一个
├── deploy/
│   └── caddy/Caddyfile
├── .mavis/plans/          # [新] 多个 .plan 文件,见下文
├── .gitignore             # 含 .ssh/
└── README.md              # 接手必读
```

---

## 3. 开发环境(统一标准)

### 3.1 本地必备

| 工具 | 版本 | 安装位置 | 备注 |
|---|---|---|---|
| JDK | 17.0.2 | `C:\Program Files\Java\jdk-17.0.2\` | 配 `JAVA_HOME` |
| Maven | 3.9.6 | `C:\Program Files\apache-maven-3.9.6\` | 配 `M2_HOME` |
| Node.js | 20.x LTS | `C:\Program Files\nodejs\` | 自带 npm |
| MySQL | 8.0.16 | `C:\Program Files\MySQL\MySQL Server 8.0\` | 已运行 |
| PowerShell | 5.x(自带) | - | Server 2012 R2 默认 |
| Git | 最新 | - | 账号 key 已配 Gitee |
| Caddy | 2.x | `D:\archive\apps\caddy\` | 部署用 |
| WinSW | 2.x | `D:\archive\apps\winsw\` | 部署用 |

> **沙箱注意**: 沙箱里 JDK 和 Maven 装在 `/workspace/.tools/`,**不是**系统盘。

### 3.2 IDE 推荐

- **后端**: IntelliJ IDEA / VS Code + Java 扩展
- **前端**: VS Code + Volar / WebStorm
- **格式化**:
  - Java: Google Java Style(4 空格缩进)
  - TypeScript: 项目自带 `.editorconfig` + Prettier(2 空格缩进)
  - **Lombok**: 必须用,减少样板代码

### 3.3 环境变量(后端)

`D:\archive\config\config.json`:
```json
{
  "jwt": {
    "secret": "<openssl rand -base64 32 生成, >= 32 byte>",
    "expirationSeconds": 28800
  },
  "storage": {
    "root": "D:/archive/files",
    "parsedRoot": "D:/archive/parsed"
  },
  "llm": {
    "provider": "glm",                    // glm / openai / mock
    "glm": {
      "apiKey": "<智谱 API key>",
      "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
      "model": "glm-4-flash"
    },
    "openai": {
      "apiKey": "...",
      "baseUrl": "https://api.openai.com/v1",
      "model": "gpt-4o-mini"
    },
    "mock": {
      "responseDelayMs": 100
    }
  },
  "trigger": {
    "maxRetries": 3,
    "reminderDays": 7
  }
}
```

> **重要**: 编码 **UTF-8 无 BOM**。`ConfigJsonLoader` 已有 BOM 跳过逻辑。

---

## 4. 编码规范(强制)

### 4.1 命名约定

#### 后端 Java
- 类名: `PascalCase`,如 `ProjectService`
- 方法: `camelCase`,如 `getProjectById`
- 字段: `camelCase`,**DB 列 snake_case**(JPA `@Column` 映射)
- 常量: `UPPER_SNAKE_CASE`
- 包名: 全小写,`com.archive.{layer}`

#### 前端 TypeScript
- 组件: `PascalCase`,如 `ProjectList.vue`
- 变量/函数: `camelCase`
- 类型/接口: `PascalCase`,以 `I` 开头可选(`Project` / `IProject`)
- 枚举值: `UPPER_SNAKE_CASE`
- 路由 path: kebab-case,`/project-list`

#### 数据库
- 表名: `snake_case` 单数,如 `material_version`
- 列名: `snake_case`,如 `project_id`
- 主键: `id BIGINT AUTO_INCREMENT`
- 时间: `created_at` / `updated_at` DATETIME
- 金额: `DECIMAL(18,2)`,**严禁 FLOAT/DOUBLE**
- 字符串: `VARCHAR(N)` 显式长度,不用 `TEXT` 做普通字段

### 4.2 注释规范

**Java**: 公共类和方法必须有 Javadoc,复杂逻辑行内注释。

```java
/**
 * 解析材料版本的全文,异步执行.
 * 解析成功后自动触发字段抽取和时点抽取.
 *
 * @param versionId 材料版本 ID
 */
@Transactional
public void parseVersion(Long versionId) { ... }
```

**TypeScript**: 关键函数加 JSDoc,**禁止 `as any`**(除非真没办法)。

```typescript
/**
 * 获取项目列表
 * @param query 分页 + 搜索条件
 * @returns 分页结果
 */
export async function listProjects(query: ProjectQuery): Promise<Page<Project>> {
  return getData<Page<Project>>(await http.get('/api/projects', { params: query }))
}
```

### 4.3 错误处理

#### 后端
- 业务异常用 `NoSuchElementException`(404) / `IllegalArgumentException`(400) / `IllegalStateException`(400)
- 严禁 `try-catch` 吞异常
- Service 层**不**返回 `null`,用 `Optional<T>` 或抛异常
- 全局异常由 `GlobalExceptionHandler` 统一处理,Controller 不写 try-catch

#### 前端
- 用 axios 拦截器统一处理业务码(非 0 弹错)
- async 函数必须 `try-catch` 或 `.catch()`
- 用户操作失败的错误**用 `ElMessage`** 提示,不要用 `alert()`

### 4.4 安全规范

- **密码**: BCrypt 哈希,不存明文
- **JWT secret**: 从 `config.json` 读,不硬编码,**不**用默认值(生产必检)
- **SQL 注入**: 用 JPA `#{}` 命名参数或 `@Query` 绑定,**禁止字符串拼接**
- **路径遍历**: 文件操作走 `StorageService.resolveUnderRoot()`,**禁止** `new File(userInput)`
- **LLM 输出**: 当 JSON 用时**必须 try-parse**,失败兜底,不能崩
- **审计**: 所有写操作 + 登录/登出 → `audit_log`

---

## 5. Git 规范(强制)

### 5.1 分支策略

| 分支 | 用途 | 谁推 |
|---|---|---|
| `main` | 生产分支 | **只走 PR**,不直推 |
| `minimax` | 集成/开发分支 | dev 推 |
| `feature/*` | 单个功能分支 | dev 推 |
| `fix/*` | bug 修复分支 | dev 推 |

> 本项目**当前活跃分支是 `minimax`**。**任何 push 都到 `minimax`**,main 走 PR/受保护流程。

### 5.2 Commit 格式(强制)

```
<type>(<scope>,<milestone>): <subject>

<body>

<footer>
```

**type**:
- `feat`: 新功能
- `fix`: bug 修复
- `refactor`: 重构(无功能变化)
- `docs`: 文档
- `test`: 测试
- `chore`: 杂项(依赖、配置)
- `style`: 格式化
- `perf`: 性能

**scope**:
- 模块名: `auth` / `project` / `material` / `todo` / `trigger` / `dict` / `archive` / `qa`
- 或层级: `backend` / `frontend` / `db` / `docs` / `deploy`

**milestone**:
- 对应的 milestone 标签: `M0` / `M1` / `M2` / `M3` / `M4` / `M5`
- 或 `repo`(跟里程碑无关)

**subject**: 50 字符内,中文/英文都行,简洁

**示例**:
```
feat(backend,M3-1): TriggerEngine 支持 JSON 条件 + 默认 10 条预置规则

- 新增 engine/TriggerEngine.java
- 新增 entity/TriggerRule + TriggerAction
- 新增 service/TriggerService
- 预置规则 10 条(见 seed.sql)
- API: GET/POST/PUT/DELETE /api/projects/{id}/trigger-rules
- 测试: TriggerEngineTest 覆盖 6 个条件组合

Refs: SUPPLEMENTARY-REQUIREMENTS § P3
```

### 5.3 推送流程(强制)

```bash
# 1. 工作前
git pull origin minimax

# 2. 改完一块就 commit 一次(不囤积)
git add <files>
git commit -m "..."

# 3. 立即 push
git push origin minimax

# 4. 沙箱内
export GIT_SSH_COMMAND="ssh -i /workspace/projects-online-clone/.ssh/archive_deploy -o IdentitiesOnly=yes"
```

**严禁**:
- 一次 commit 跨 3 个 milestone
- commit 信息写 "fix" / "update" / "改了点东西"
- 把 `.ssh/` / `target/` / `node_modules/` / `*.class` 推进去
- 一天结束才 push 一次

### 5.4 .gitignore(已有,不准改)

```
.ssh/
target/
build/
node_modules/
.idea/
.vscode/
*.iml
*.log
*.class
*.jar
*.war
__pycache__/
.pytest_cache/
.DS_Store
Thumbs.db
```

---

## 6. 测试规范

### 6.1 测试层次

| 层次 | 工具 | 覆盖目标 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito | Service / Engine 业务逻辑 |
| 集成测试 | @SpringBootTest + Testcontainers | API 端到端 |
| 端到端 | 手工 + 浏览器 | 业务场景 |
| 编译验证 | mvn compile + npm run build | 类型 + 依赖 |

### 6.2 测试覆盖率目标

| 模块 | 目标 |
|---|---|
| Engine(Trigger / Timepoint / Comparison) | >= 80% |
| Service(关键) | >= 60% |
| Service(普通) | >= 30% |
| Controller | 0%(集成测试覆盖) |

### 6.3 测试约定

- **Given-When-Then** 风格
- 测试数据用 `test-data.sql` 或 `@Sql`,**不**在测试代码里硬编码
- 外部依赖(LLM / 文件系统)用 Mock,**不**真实调用
- 测试**必须**独立可跑,不依赖执行顺序

### 6.4 端到端测试任务清单

每个 milestone 必有一个 `M{N}-TEST-TASKS.md`,列:
- 每个功能怎么验证
- 期望结果
- 实际结果(测了填)
- 截图(用 `ElMessage` / `ElNotification` 提示,或在文档里描述)

---

## 7. 交付规范(强制,任何 Agent 完工前自查)

### 7.1 完工自查清单(Commit 前过一遍)

- [ ] **代码**: 改完,本机 `mvn compile` / `npm run build` 0 错
- [ ] **测试**: 新功能有测试,跑过 PASS
- [ ] **格式化**: `mvn formatter:validate` / `eslint` 0 警告
- [ ] **依赖**: 没用未在 pom.xml / package.json 里的库
- [ ] **配置**: 没硬编码 IP/端口/密钥
- [ ] **日志**: 不打印敏感信息(密码/token)
- [ ] **注释**: 公共类/方法有 Javadoc
- [ ] **commit 信息**: 符合 §5.2 格式,scope 和 milestone 标了
- [ ] **push**: 推到 `minimax`,**不**直推 `main`

### 7.2 完工"交回物"清单(交给 Reviewer)

每个功能/模块完工,提交 PR / Commit 时必须包含:

1. **代码改动文件清单**(git diff 摘要)
2. **新文件清单**(路径 + 一句话用途)
3. **测试报告**(通过的测试用例 + 覆盖率)
4. **自测结果**(在浏览器/curl 上跑过,贴输出)
5. **文档更新**(如改了 API 端点,同步更新 API 文档)
6. **踩坑记录**(踩了填到 `../reviews/LESSONS-LEARNED.md`)
7. **截图**(UI 改动必带,前后对比)
8. **已知问题 / 留给后人的 TODO**

> **不交清单的 commit 不被接受**(CI/Reviewer 卡住)。

### 7.3 跨 Agent 协作的"接口契约"

- **新 API 端点**: 在 `docs/API-ENDPOINTS.md` 加一行,标 method + path + 用途 + 入参/出参
- **新数据库表**: 在 `DB-SCHEMA-v2.md` 加一段,标 DDL + 索引 + 备注
- **新 LLM Provider**: 在 `LLMProvider` 接口加,实现 + 配置示例
- **新前端页面**: 在 `ARCHITECTURE-v2.md` 路由表加,标 path + 用途

**两个 Agent 改同一文件**: 先在群里/会话里说一下,谁先动谁 commit,后动的人 rebase/merge。

---

## 8. 性能与稳定性基线

| 指标 | 基线 | 测量方法 |
|---|---|---|
| 首页加载 | < 2 秒(100 个项目) | 浏览器 DevTools Network |
| API 响应(P95) | < 500ms(无 LLM) | 日志 / actuator |
| API 响应(P95) | < 30 秒(有 LLM) | 日志 |
| 上传→解析完成 | < 5 分钟(50MB) | UI 上看 parseStatus |
| FULLTEXT 检索 | < 3 秒(10 万字库) | 知识库问答页面 |
| 后端启动 | < 30 秒 | 启动日志 |

**超出基线 = 优化任务**,写到对应 milestone 的 plan。

---

## 9. 沟通与同步

### 9.1 会话上下文(沙箱内)

- 沙箱易失:`/opt` / `/tmp` / `/root/.ssh` 容器层 reset 会丢
- **持久化**: `/workspace/` 下任何路径
- **重要**:
  - SSH key: `/workspace/projects-online-clone/.ssh/`
  - JDK: `/workspace/.tools/jdk-17.0.2/`
  - Maven: `/workspace/.tools/apache-maven-3.9.6/`
  - 工具缓存: `/root/.maven/`(Maven 依赖)

### 9.2 跨会话交接

- 重要决策写到 `../reviews/LESSONS-LEARNED.md` 或 agent memory
- 任何 Agent 启动前**先读**:
  1. `README.md`(接手必读)
  2. `../reviews/LESSONS-LEARNED.md`(踩坑列表)
  3. `../operations/TEAM-ARCHIVE.md`(团队档案)
  4. `../requirements/REQUIREMENTS.md`(业务背景)
  5. `../architecture/ARCHITECTURE-v2.md`(架构)
  6. `../architecture/DB-SCHEMA-v2.md`(DB)

---

## 10. 常见反模式(严禁)

| 反模式 | 后果 | 正确做法 |
|---|---|---|
| `as any` 抹类型 | 运行时崩 | 写真类型,加泛型 |
| 字符串拼 SQL | SQL 注入 | 用 JPA `#{}` |
| 改一个文件 3 个 milestone 一起 commit | 难回滚 | 拆 commit |
| 改完不测就 push | 线上崩 | commit 前 `mvn compile` + `npm run build` |
| 改 main 分支 | 受保护,推不上去 | 只推 `minimax` |
| 直接 `git add .` | 误传 .ssh/ / target/ | `git add <具体文件>` |
| 配置文件写死密钥 | 泄漏 | 全走 `config.json` + 环境变量 |
| 后端用 float 算金额 | 精度错 | `DECIMAL(18,2)` + BigDecimal |
| 改业务表没改审计 | 合规漏 | 所有写操作走 `@Audited` 或手动 audit_log |
| 触发规则循环触发自己 | 死循环 | 加循环检测,见 TriggerEngine 注释 |

---

## 11. 版本号与变更日志

- **当前版本**: v1.0(M0~M2 已落)
- **本批次目标**: v2.0(完成 REQUIREMENTS-v1 全部内容)
- **变更日志**: 任何 milestone 完成,更新根目录 `CHANGELOG.md`

---

## 12. 前端错误处理规范（2026-06-12 强制）

### 12.1 全局 errorHandler

`src/main.ts` 必须注册 `app.config.errorHandler`。当前实现：

```typescript
app.config.errorHandler = (err, instance, info) => {
  reportError(err, `Vue error: ${info}`)
}
```

`reportError()` 来自 `src/api/clientError.ts`，行为：
1. `console.error` 输出完整堆栈
2. `ElMessage.error` 显示用户提示：「操作失败，请刷新或联系运维」
3. 异步 `POST /api/client-error` 上报到后端 `audit_log`

### 12.2 禁止项

- ❌ `catch (e) {}` — 空块禁止。至少 `console.error` + 显式上报。
- ❌ 业务代码吞异常不提示用户

### 12.3 强制项

- ✅ 任何 try-catch 后必须重新 throw 或调用 `reportError()`
- ✅ 所有组件不可预见的运行时异常由全局 errorHandler 兜底，组件不应自行 `window.onerror`

---

*违反本规范的代码,Reviewer 有权退回重做。*
