# Plan UP-0611-01 — Agent 本地 Archive 只读文件工具（ls / grep / read）

> **状态**：`VERIFY`（待 Reviewer 审 / 125 联测）  
> **活跃目录**：`upgrade_to_settle/` · 完工后 → `upgrade_to_settle/done/`

---

## 0. Plan 元信息

| 字段 | 内容 |
|---|---|
| **Plan ID** | **UP-0611-01** |
| **标题** | Agent 只读访问 `D:/archive` 材料目录（ls / grep / read）+ 多模态扩展机制 |
| **状态** | `DRAFT` |
| **优先级** | **P1** |
| **目标版本** | v1.2（或 v1.1.x 小版本，PM 可裁 scope） |
| **代码基线** | `main` ≥ `dd7caae`（含 T-0611-20 find_project 多 variant 本地改动待合入） |
| **触发** | Co-test：用户期望 Agent 能读 `D:/archive` 最原始材料；当前仅 MySQL `parsed_text` + 预览 API，**无 Agent 文件工具** |
| **负责人（PM）** | （待填） |
| **架构师** | Auto · 2026-06-11 |

### 完成条件

- [ ] §4 全部实现项完成
- [ ] §5 commit 留痕
- [ ] §6 Review PASS
- [ ] §7 125 联测 + 单测通过
- [ ] 需求 §5.6.7、架构 `07-archive-fs-agent-tools.md` 已落地

---

## 1. 需求追溯（Analyst）

| 字段 | 内容 |
|---|---|
| **Agent** | Auto（需求分析） |
| **时间** | 2026-06-11 |
| **摘要** | 补齐 Agent 对本地 archive 只读能力；安全边界 `D:/archive` 材料区；预留多模态 |

### 1.1 业务背景

- 投委会秘书问材料细节时，**MySQL FULLTEXT** 可能漏掉未入库解析、格式复杂段落，或需要对照**原始文件名 / 目录结构**。
- 联测确认：上传文件在 `D:/archive/files/`，解析副本在 `D:/archive/parsed/`，但 **Agent 工具链不读磁盘**（见 Co-test §1.10、`deployment_log` §9）。
- 用户明确要求：在材料文件夹内允许 **类似 ls、grep** 的只读访问；**禁止**越界到 `logs/`、`config/`；后续扩展 **多模态**（PDF 页图、扫描件 OCR 等）。

### 1.2 需求锚点

| 文档 | 章节 | 要点 |
|---|---|---|
| [`docs/requirements/REQUIREMENTS.md`](../docs/requirements/REQUIREMENTS.md) | **§5.6.7**（新增） | Archive 只读文件工具：ls / grep / read；根目录白名单；DB 路径绑定 |
| [`docs/requirements/REQUIREMENTS.md`](../docs/requirements/REQUIREMENTS.md) | §5.6.2 | 工具清单扩展（与 MySQL 工具并存，不替代） |
| [`docs/requirements/REQUIREMENTS.md`](../docs/requirements/REQUIREMENTS.md) | §9.2 安全 | 路径 traversal 防护、审计、大小上限 |
| [`docs/requirements/AGENT-REQUIREMENTS.md`](../docs/requirements/AGENT-REQUIREMENTS.md) | §4.5 | 思考过程 / 工具链体验 |
| [`docs/requirements/ARCH-DECOMPOSITION.md`](../docs/requirements/ARCH-DECOMPOSITION.md) | **RI-70**（新增） | 本 plan 对应 RI |
| [`test-to-settle/round-2026-06-11-v1.1-deploy.md`](../test-to-settle/round-2026-06-11-v1.1-deploy.md) | §1.10 | 联测：无 MD 索引、Agent 仅 MySQL |

### 1.3 产品验收标准

- [ ] Agent 在锁定项目后，可对**该项目材料**执行 `archive_fs` **list**（列目录）与 **grep**（搜正文关键词）
- [ ] **grep / read** 默认走 `parsed` 区；`files` 区仅允许白名单扩展名（`.txt` `.md` `.csv` `.json` `.xml` `.html`）
- [ ] 任意 `../`、绝对路径、`D:/windows` 等 **403/ERROR**，写 audit_log
- [ ] 单次 read ≤ **512KB**（可配）；grep 最多返回 **200 行**（可配）
- [ ] **禁止** write / delete / upload
- [ ] 单元测试覆盖 path traversal + 正常 list/grep
- [ ] `AgentSystemPrompt` 更新工具说明；ReAct 5 步内可完成「某项目材料目录下 grep 某词」

---

## 2. 架构追溯（Architect）

| 字段 | 内容 |
|---|---|
| **Agent** | Auto（架构） |
| **时间** | 2026-06-11 |
| **摘要** | 门面工具 + PathGuard + 可插拔 Action；不破坏现有 MySQL 工具 |

### 2.1 架构锚点

| 文档 | 章节 | 设计决策 |
|---|---|---|
| [`docs/architecture/07-archive-fs-agent-tools.md`](../docs/architecture/07-archive-fs-agent-tools.md) | 全文 | **主设计文档** |
| [`docs/architecture/02-backend-layer-architecture.md`](../docs/architecture/02-backend-layer-architecture.md) | § Agent 包 | 新增 `agent/tool/archive/*` |
| [`docs/architecture/03-frontend-component-architecture.md`](../docs/architecture/03-frontend-component-architecture.md) | §9 | AgentStepsPanel 展示新 tool 名 |
| [`docs/architecture/05-deployment-and-environment.md`](../docs/architecture/05-deployment-and-environment.md) | storage 路径 | `app.storage.file-root` / `parsed-root` |

### 2.2 设计摘要

```text
AgentEngine.dispatchTool("archive_fs", args)
        │
        ▼
ArchiveFsTool (AgentTool 门面)
        │
        ├── ArchivePathGuard   ← 唯一合法根：file-root + parsed-root
        ├── ArchiveMaterialPathResolver ← 可选：materialVersionId → DB storage_path
        └── ArchiveFsActionRegistry
                 ├── ListAction      (ls)
                 ├── GrepAction      (grep)
                 ├── ReadTextAction  (read)
                 └── (future) ReadImageAction / ReadPdfPageAction  ← 多模态扩展点
```

**与 MySQL 工具关系**：

| 场景 | 优先 |
|---|---|
| 项目定位 | `find_project`（不变） |
| 跨库统计 / 结构化 | `query_mysql` |
| 已解析全文检索 | `search_fulltext`（仍默认） |
| 目录结构 / 原始文件 / parsed 未入库 | **`archive_fs`**（新增） |

---

## 3. PM 范围与决策

| 字段 | 内容 |
|---|---|
| **Agent** | Auto（PM） |
| **时间** | 2026-06-11 |
| **摘要** | v1.2 第一期只做文本 ls/grep/read；多模态只留接口 |

| 项 | 决策 |
|---|---|
| **做** | `archive_fs` 门面；list / grep / read_text；PathGuard；DB 解析 materialVersionId；审计日志；单测 |
| **不做（本期）** | PDF 二进制 grep；写文件；读 `logs/` `config/`；前端新 UI |
| **预留** | `ArchiveFsAction` 接口 + Spring 注入列表；RI-71 多模态另开 plan |
| **风险** | 大文件 DoS → 严格 maxBytes / maxLines / 超时；PDF 误 read → 扩展名白名单 |
| **估时** | BE 2～3d · 测试 0.5d · 文档 0.5d |

---

## 4. 开发说明（Implementer 执行清单）

### 4.1 改动文件清单

| 类型 | 路径 | 说明 |
|---|---|---|
| **新增** | `backend/.../agent/tool/archive/ArchiveFsTool.java` | `AgentTool`，name=`archive_fs` |
| **新增** | `backend/.../agent/tool/archive/ArchivePathGuard.java` | 双 root 校验、`resolve(zone, relativePath)` |
| **新增** | `backend/.../agent/tool/archive/ArchiveMaterialPathResolver.java` | `materialVersionId` → `storage_path` / `parsed_text_path` |
| **新增** | `backend/.../agent/tool/archive/ArchiveFsAction.java` | 接口 `String actionName(); ToolResult execute(ArchiveFsContext ctx)` |
| **新增** | `backend/.../agent/tool/archive/ArchiveFsContext.java` | guard + args + 审计 user |
| **新增** | `backend/.../agent/tool/archive/ListArchiveAction.java` | ls：非递归或一层子项（可配 `maxEntries=100`） |
| **新增** | `backend/.../agent/tool/archive/GrepArchiveAction.java` | grep：按行 contains/regex，仅文本 |
| **新增** | `backend/.../agent/tool/archive/ReadTextArchiveAction.java` | read：截断 + charset UTF-8 |
| **新增** | `backend/.../agent/tool/archive/ArchiveFsActionRegistry.java` | 构造注入 `List<ArchiveFsAction>` |
| **新增** | `backend/src/test/java/.../archive/ArchivePathGuardTest.java` | traversal、双 root |
| **新增** | `backend/src/test/java/.../archive/ArchiveFsToolTest.java` | list/grep mock FS 或 temp dir |
| **修改** | `backend/.../agent/prompt/AgentSystemPrompt.java` | 工具 7→8，增加 `archive_fs` 说明与 few-shot |
| **修改** | `backend/.../common/StorageService.java` | 可选：抽取 `resolveUnderRoot` 供 PathGuard 复用（避免重复） |
| **修改** | `docs/requirements/REQUIREMENTS.md` | §5.6.7（已加） |
| **修改** | `docs/architecture/07-archive-fs-agent-tools.md` | 同步实现细节 |

### 4.2 工具 JSON 契约（LLM 调用）

**工具名**：`archive_fs`

```json
{
  "thought": "要在该项目某材料 parsed 目录里搜关键词",
  "tool": "archive_fs",
  "args": {
    "action": "grep",
    "zone": "parsed",
    "materialVersionId": 12345,
    "pattern": "抵押物",
    "maxLines": 50
  }
}
```

**args 字段**：

| 字段 | 必填 | 说明 |
|---|---|---|
| `action` | ✓ | `list` \| `grep` \| `read` |
| `zone` | ✓ | `files` \| `parsed` |
| `materialVersionId` | 与 relativePath 二选一 | 推荐：DB 查相对路径，防 LLM 瞎编路径 |
| `relativePath` | 与 materialVersionId 二选一 | 相对 `file-root` 或 `parsed-root` |
| `pattern` | grep 必填 |  substring 或简单 regex（禁止 ReDoS：长度≤200） |
| `maxLines` | | grep 默认 100，上限 200 |
| `maxBytes` | | read 默认 262144，上限 524288 |

**返回**（ToolResult.ok data）：

```json
{
  "action": "grep",
  "zone": "parsed",
  "path": "material-12/v3/report.pdf.txt",
  "matches": [{"lineNo": 42, "text": "…"}],
  "truncated": false
}
```

### 4.3 安全（必须实现）

1. **合法根**：仅 `app.storage.file-root`、`app.storage.parsed-root`（默认 `D:/archive/files`、`D:/archive/parsed`）。
2. **禁止**：`log-root`、`config`、仓库源码、任意绝对路径。
3. **解析**：`Path.normalize()` + `startsWith(root)`；与 `StorageService.resolveUnderRoot` 同逻辑。
4. **RBAC**：沿用 JWT；可选：仅 admin/pm/secretary 可调 `archive_fs`（committee 只读 parsed）— **PM 裁**：v1 与 QA 同权，只读即可。
5. **审计**：`audit_log` 记录 action、zone、path 摘要、username（复用现有审计切面或 tool 内显式写）。
6. **资源上限**：read/grep 超时 5s（可配）。

### 4.4 多模态扩展机制（本期只注册接口）

```java
public interface ArchiveFsAction {
    String actionName();
    ToolResult execute(ArchiveFsContext ctx);
}
```

- 未来 plan（RI-71）：`ReadPdfPageAction`、`ReadImageAction` 实现同一接口，`@Component` 自动进 Registry。
- **禁止**在 `ArchiveFsTool` 内 hardcode switch 无 default；未知 action → 明确 ERROR 列出 supported。

### 4.5 实现步骤（勾选）

- [ ] **S1** `ArchivePathGuard` + 单测（`..`、符号链接若 Windows 需文档说明）
- [ ] **S2** `ArchiveMaterialPathResolver`（查 `material_version`，校验 material→project 与 `ctx.projectCode` 一致）
- [ ] **S3** `ListArchiveAction` / `GrepArchiveAction` / `ReadTextArchiveAction`
- [ ] **S4** `ArchiveFsTool` + Registry  wiring
- [ ] **S5** 更新 `AgentSystemPrompt` + `AgentFewShots` 示例（先 find_project → archive_fs grep）
- [ ] **S6** 集成测试：H2 环境用 `@TempDir` 模拟 root；或 test profile 指向 temp
- [ ] **S7** 125 联测脚本 / Co-test 步骤写入 §7

### 4.6 测试

| 类型 | 内容 |
|---|---|
| 单测 | PathGuard traversal；grep 行数截断；扩展名拒绝 `.exe` |
| 集成 | AgentIntegrationTest 新增场景：mock 或 testcontainers 不适用 → `@TempDir` + 替换 StorageProperties |
| 联测 | admin → 知识库 →「在 lmz 项目材料里搜 xxx」→ 思考过程含 `archive_fs` |

### 4.7 禁止事项

- ❌ 不删改 `test-to-settle` 既有 bug 状态（除非 VERIFY）
- ❌ 不在本期实现写文件 / shell 执行
- ❌ 不让 LLM 直接传绝对路径

---

## 5. 实现留痕（Implement Agent）

| **Agent** | Sisyphus |
|---|---|
| **时间** | 2026-06-11 |
| **摘要** | 全套实现：PathGuard + 3 Actions + ArchiveFsTool + AgentSystemPrompt 工具声明 |

| 项 | Commit | 说明 | 状态 |
|---|---|---|---|
| ArchivePathGuard | (当前) | 双 root 白名单校验 | `DONE` |
| ArchiveFsContext + ArchiveFsAction + Registry | (当前) | 门面框架 | `DONE` |
| ListArchiveAction | (当前) | ls 非递归 | `DONE` |
| GrepArchiveAction | (当前) | substring grep | `DONE` |
| ReadTextArchiveAction | (当前) | 截断 read | `DONE` |
| ArchiveFsTool | (当前) | AgentTool 实现 | `DONE` |
| AgentSystemPrompt 工具 7→8 | (当前) | archive_fs 说明 | `DONE` |

---

## 6. 评审（Reviewer Agent）

| 字段 | 内容 |
|---|---|
| **Agent** | *待填写* |
| **时间** | |
| **摘要** | |

| 结论 | 意见 |
|---|---|
| `PENDING` | |

---

## 7. 验收与归档

### 7.1 验收记录

| # | 项 | 结果 | 时间 |
|---|---|---|---|
| 1 | 单测 `ArchivePathGuardTest` 全绿 | | |
| 2 | Agent prompt 含 `archive_fs` | | |
| 3 | 125：list + grep 联测 | | |

### 7.2 结论

| 项 | 内容 |
|---|---|
| **Plan 状态** | `DRAFT` |
| **归档路径** | `upgrade_to_settle/done/plan-2026-06-11-archive-local-fs-tools.md` |

### 7.3 变更记录

| 日期 | 作者 | 变更 |
|---|---|---|
| 2026-06-11 | Auto | 创建 plan；需求 §5.6.7 + 架构 07 + RI-70 |

---

*Plan UP-0611-01 · [`upgrade_to_settle/README.md`](README.md)*
