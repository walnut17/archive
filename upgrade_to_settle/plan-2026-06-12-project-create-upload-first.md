# plan-2026-06-12-project-create-upload-first — RI-16 立项「上传优先」

> **状态**：`DRAFT`（Co-test §12 增补 T-0612-09/10/11 · 待 PM/架构拍板后进 TASKS）
> **触发**：Co-test 0612 Step 2e · T-0612-05 · [`deployment_log`](../docs/operations/deployment_log.md) §10 / §12

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-12-project-create-upload-first` |
| **类型** | `UPGRADE` |
| **Case 状态** | `DRAFT` |
| **标题** | 新建项目流程：上传材料 → LLM 预填 → 表单提交 |
| **需求 / 架构锚点** | RI-16 · [`ARCH-DECOMPOSITION.md`](../docs/requirements/ARCH-DECOMPOSITION.md) §5.11.4 · MOD-06 部分已实现 |
| **complexity** | [`C-0612-01`](../test-to-settle/complexity.md) |

---

## 1. 任务描述（PM / 架构）

### 做

- **主路径**：「+ 新建项目」→ **先上传材料**（单文件即可 MVP）→ 解析/抽取 → **预填**项目表单 → 用户核对 → 提交
- 复用已有后端：`POST /api/projects/extract-preview`、`POST /api/projects` + `materialVersionId`
- 失败兜底：抽取失败仍展示表单 + 明确 banner（已有 `ProjectForm` failureType 模式）
- 项目编号：与 RI-17 对齐（见 §1.3 Co-test 0612 增补 — **不再接受 DRAFT 占位锁死**）
- 业务类别：立项默认五类（见 §1.3）

### 不做（本 plan）

- 批量多文件上传 + 统一元数据（见 SUPPLEMENTARY 批量上传需求，另开 plan）
- 议案 / 维护 3 类入口（RI-18，另开 plan）
- 知识库多轮 500（T-0612-04，留在 DEBUG round）

### 验收（Co-test / 125）

| # | Given | When | Then |
|---|---|---|---|
| 1 | admin 登录 | 项目管理 → 「+ 新建项目」 | **首屏为上传**（或上传+说明），**不是**空结构化表单 |
| 2 | 已选 PDF/Word 并上传成功 | 等待解析/抽取 | 跳转或同页展示**预填**表单（名称/金额等至少 1 项有值） |
| 3 | 预填表单 | 改字段 → 保存 | 创建成功；列表可见新项目 |
| 4 | 抽取失败（无 GLM key 或坏文件） | 仍保存手工填写 | 有 failure banner；必填校验仍生效 |
| 5 | 旧链 `/projects/new?materialVersionId=` | 直接打开 | 仍可用（兼容 MOD-06 深链） |
| 6 | 上传 PDF 立项材料 | 进入预填表单 | **投资金额**有预填或明确 failure banner（T-0612-10） |
| 7 | 新建项目表单 | 打开编号区 | **非** `DRAFT-*` 锁死；可选 **两个系列** 自动生成 **或** 手工填号 + 占用校验（T-0612-09） |
| 8 | 业务类别下拉 | 新建/编辑 | 含 **债权包投资、单笔债权投资、股权投资、债权池投资、组合投资**（T-0612-11） |

### 1.3 Co-test 0612 §12 增补（`954054e` 基线）

| 源 Bug | 现象 | PM/架构拍板要点 |
|---|---|---|
| **T-0612-09** | `ProjectCreateStagingService` 创建 `DRAFT-{uuid}`；`ProjectForm` 加载 draft 后编号 **disabled** | ① staging **不再**把 DRAFT 当正式编号展示；② UI：**系列选择器**（现有两个系列，具体 code/prefix 以 125 库 `proposal_series` / 业务口径为准）→ 调生成器取下一号；③ **手工模式**：可编辑 + `GET/POST` 校验 `project.code` 唯一；④ 保存时把 DRAFT 替换为正式编号或更新同一记录 |
| **T-0612-10** | PDF 上传后金额 **0** | ① 确认 `material_version.parsed_text` 在 extract 前是否已就绪（必要时前端 poll / 后端同步 parse）；② extract 返回 `amount` 字段映射到 `amountWan`；③ 失败时 banner 勿静默 0 |
| **T-0612-11** | `projectCategoryOptions` = 股权类/固收类/混合类/其他 | FE 默认五类；staging 勿写死 `其他`；长期仍走字典表（SUPPLEMENTARY） |

**关联 round**：[`round-2026-06-12-project-create-co-test`](../test-to-settle/round-2026-06-12-project-create-co-test.md)

---

## 2. 开发说明（架构师草案 · Coder 只读）

| 路径 | 操作 | 说明 |
|---|---|---|
| `frontend/src/views/ProjectList.vue` | 改 | `goCreate()` 不再直跳空表单；进上传向导或 `/projects/new/upload` |
| `frontend/src/views/ProjectCreateUpload.vue`（或等价） | **新增** | 选文件 → 调材料上传 API → 拿 `materialVersionId` → `router.push({ name:'project-form', query:{ materialVersionId }})` |
| `frontend/src/views/ProjectForm.vue` | 小改 | 无 `materialVersionId` 时 redirect 回上传步或提示「请先上传材料」 |
| `frontend/src/router/index.ts` | 改 | 注册上传步路由；`projects/new` 默认指上传步 |
| `backend/.../ProjectController` | 可选 | MVP 可不改；若 PM 要求「无 materialVersionId 禁止 create」再加校验 |
| `docs/requirements/ARCH-DECOMPOSITION.md` | 文档 | RI-16 验收勾选项与实现对齐说明 |

**依赖**：材料上传 API 已有（议案详情页 `MaterialVersionService.upload` 链路）；需确认**立项场景**下 material/proposal 占位创建顺序（架构拍板）。

**估算（草案）**：FE 2d · BE 0.5～1d（若需立项占位）· Co-test 0.5d

---

## 3. Agent Blocks

> 待 TASKS 占坑后 Coder 开工。格式见 [`CASE-FORMAT.md`](../CASE-FORMAT.md)。

---

## 4. 关单检查

- [ ] §1 验收 5 条在 125 Co-test 通过
- [ ] complexity **C-0612-01** 已删行
- [ ] round T-0612-05 标「已转本 plan」
- [ ] Reviewer **CLOSED** · `git mv` → [`done/`](done/README.md)
