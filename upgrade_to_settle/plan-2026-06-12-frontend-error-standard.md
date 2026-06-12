# 前端 errorHandler 标准化 + 上报选型

> **来源**：`test-to-settle/complexity.md` C-0611-07（升格自 round-0611 T-0611-17）
> **状态**：`VERIFY` · **类型**：`UPGRADE` · **PM 拍板**：2026-06-12

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-12-frontend-error-standard` |
| **类型** | `UPGRADE` |
| **Case 状态** | `OPEN` |
| **标题** | 前端 errorHandler 标准化 + toast 提示 + 上报选型 |
| **需求锚点** | `docs/requirements/REQUIREMENTS.md` §13.2.5（健壮性） |
| **架构锚点** | `docs/architecture/03-frontend-component-architecture.md` §6（错误边界） |
| **关联 bug** | T-0611-16/17（已 CLOSED） |
| **关联 plan** | UP-0611-04 chat-ui（已 CLOSED done/） |

---

## 1. 任务描述

### 1.1 背景

`app.config.errorHandler` 在 `main.ts` 里**只 console.warn**——临时打补丁：
- 用户侧仍可能看到"页面挂掉但无任何提示"
- 异常未上报，运维无法感知
- 无 toast 提示，不符合"5 秒返回"目标

### 1.2 方案拍板（PM + 架构 2026-06-12 09:55）

| 维度 | 决策 |
|---|---|
| **强制纳入规范** | ✅ 写进 `docs/operations/DEV-STANDARDS.md` §X（前端 errorHandler 强制） |
| **toast 提示** | ✅ `ElMessage.error("操作失败，请刷新或联系运维")` |
| **上报选型** | 🟡 **本期不上 Sentry**（运维有 182 自监控）<br>改用 `console.error` + 自建 `/api/client-error` 上报到后端，log 到 `audit_log.type=CLIENT_ERROR` |
| **是否统一** | ✅ 全局 errorHandler 唯一入口；业务代码不直接 catch + 静默 |

### 1.3 做

- 升级 `main.ts` `app.config.errorHandler`：
  - `console.error` 完整堆栈
  - `ElMessage.error` 用户提示
  - 上报到 `/api/client-error`（新建端点）
- 新建 `ClientErrorController` POST `/api/client-error`：
  - 接收 `{ message, stack, url, userId, timestamp }`
  - 写 `audit_log` type=CLIENT_ERROR
  - 异步（不阻塞前端）
- `DEV-STANDARDS.md` §X 加规范：
  - 全局 errorHandler **必须**
  - 业务代码 try-catch 后**必须**重新 throw 或显式上报
  - **禁止** `catch (e) { /* ignore */ }`
- 加 ELint 规则（可选）：禁止 `catch (e) {}` 空块
- 单测：mock global error → 触发 errorHandler → 验证 toast + 上报

### 1.4 不做

- ❌ 不接 Sentry / 阿里云 ARMS（运维说 182 自监控够用）
- ❌ 不做 SourceMap 上传（前端构建不做 source-map 内联）
- ❌ 不加 PII 脱敏（v1.1 脱敏规则延 v2）

### 1.5 验收

- [ ] 全局 errorHandler 触发 → 用户看到 toast
- [ ] 上报到 `/api/client-error` → `audit_log` 有 CLIENT_ERROR 行
- [ ] `DEV-STANDARDS.md` §X 写明规范
- [ ] 单测覆盖 3 个 case：组件异常 / 路由异常 / 全局异常
- [ ] 业务代码无 `catch (e) {}` 空块

---

## 2. 开发说明

| 路径 | 说明 |
|---|---|
| `frontend/src/main.ts` | errorHandler 升级：toast + 上报 |
| `frontend/src/api/clientError.ts` | 新增上报 SDK（fetch + catch） |
| `backend/.../controller/ClientErrorController.java` | 新增 POST 端点 |
| `backend/.../service/AuditLogService.java` | 加 `logClientError()` 方法 |
| `docs/operations/DEV-STANDARDS.md` | §X 前端错误处理规范 |
| `backend/src/test/.../controller/ClientErrorControllerTest.java` | 单测 |
| `frontend/src/__tests__/errorHandler.spec.ts` | 单测 |

---

## 3. Agent Blocks

> 顺序：`Coder` ↔ `Reviewer` → **`Closer`（必）**

<!-- 从 Coder 块开始 -->

## 3. Agent Blocks

### Coder — Sisyphus (2026-06-12)

| 字段 | 内容 |
|---|---|
| **Agent** | Sisyphus |
| **时间** | 2026-06-12 |
| **改动清单** | `ClientErrorController.java` + `clientError.ts` + `main.ts` errorHandler + `AuditLogService.logClientError()` + `DEV-STANDARDS.md` §12 |

**实现项**：
- `ClientErrorController.java`：POST `/api/client-error`，接收 `{ message, stack, url, userId, timestamp }`
- `AuditLogService.logClientError()`：写入 `audit_log (type=CLIENT_ERROR, action=CLIENT_ERROR)`
- `clientError.ts`：`reportError()` SDK，console.error + toast + fetch 上报
- `main.ts`：errorHandler 升级为 `reportError()` + `unhandledrejection` 监听
- `DEV-STANDARDS.md` §12：前端错误处理规范

---

## 4. Reviewer

| Agent | 时间 | 结论 |
|---|---|---|
| | | |
