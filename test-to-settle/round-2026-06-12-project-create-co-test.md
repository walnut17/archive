# Co-test 0612 — 立项流程验收（上传 → 预填 → 保存）

> **操作时间线**：[`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) §12  
> **关联 UPGRADE**：[`plan-2026-06-12-project-create-upload-first`](../upgrade_to_settle/plan-2026-06-12-project-create-upload-first.md) · RI-16 / RI-17  
> **轮次状态**：`OPEN`

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `round-2026-06-12-project-create-co-test` |
| **类型** | `DEBUG` |
| **Case 状态** | `OPEN` |
| **环境 / 基线** | `182.168.1.125`，`main` = `954054e`（qa-agent 已重启；验收前端 `:5173` dev） |
| **范围** | 新建项目：上传 → 预填 → 保存；列表 → 详情（含未完成 draft 项目） |
| **TASKS** | 暂不占行（Co-test 先记问题；待 Coder 开工时再加 DEBUG 行） |

---

## 1. 任务描述（Recorder / Co-test）

> 来源 Co-test → `DEPLOY`。产品规则 / 多模块 → [`upgrade_to_settle/plan-2026-06-12-project-create-upload-first`](../upgrade_to_settle/plan-2026-06-12-project-create-upload-first.md)。

### 1.1 复现路径（Operator 0612 新轮）

1. 登录 admin → 项目管理 → 「+ 新建项目」
2. 上传 **PDF** 立项/尽调材料 → 跳转预填表单
3. 核对 **项目编号**、**投资金额**、**业务类别** 下拉

**路径 B（列表 → 详情）**

1. 新建项目流程中退出（或留下未完成 draft）→ 回到 **项目列表**
2. 对未完成项目点 **「详情」**（编辑入口亦可能受影响）

### 1.2 Bug / 验收清单

| ID | 来源 | 严重度 | 现象 / 期望 | 子项状态 |
|---|---|---|---|---|
| **T-0612-09** | DEPLOY | **P0** | **项目编号**：表单显示 `DRAFT-xxxxxxxx` 且输入框 **disabled**（`ProjectForm` 在加载 draft 项目时 `isEdit=true`）。**期望**：按现有 **两个编号系列** 之一生成（界面可选系列，选哪个走对应序列）；**也允许**用户手工填编号（提交前校验是否已被占用）。对齐 RI-17「立项：系统生成立项编号」+ 业务方两个系列规则。**实现锚点**：`ProjectCreateStagingService` 写死 `DRAFT-`；`ProjectForm.vue` `:disabled="isEdit"` | **ESCALATED** → upgrade plan §1.3 |
| **T-0612-10** | DEPLOY | **P0** | **金额预填**：上传 PDF 后「投资金额(万)」仍为 **0**，未识别出文档内金额。**期望**：抽取链路至少预填 `amount`/`amountWan`（RI-16 预填率）。**可能原因**（待 Coder 核实）：① 上传后立即 `extract-preview` 时 `parsed_text` 尚未就绪（race）；② Python/Java extract 成功但 JSON 缺 `amount`；③ 前端 `applyExtracted` 仅 `typeof amount === 'number'` 才写入。**锚点**：`POST /api/projects/extract-preview` · `qa-agent` `/v1/extract/project-fields` · `ProjectForm.runExtractPreview` | **OPEN** |
| **T-0612-11** | DEPLOY | **P1** | **业务类别枚举**：下拉为硬编码 `股权类/固收类/混合类/其他`（`archive.ts` `projectCategoryOptions`），与业务口径不符。**期望**默认选项：**债权包投资、单笔债权投资、股权投资、债权池投资、组合投资**（立项 staging 当前还写死 `category="其他"`）。 | **ESCALATED** → upgrade plan §1.3 |
| **T-0612-12** | DEPLOY | **P0** | **项目详情路由失败**：列表点「详情」白屏/不跳转；Console：`[Vue Router warn]: uncaught error during route navigation` · `TypeError: Failed to fetch dynamically imported module: http://182.168.1.125:5173/src/views/ProjectDetail.vue` · `clientError.ts` Unhandled Promise rejection。**期望**：正常进入项目详情页（议案列表等）。**复现**：新建项目退出后，对未完成（含 `DRAFT-*`）项目点详情。**锚点**：`ProjectList.goDetail` → `router/index.ts` 懒加载 `ProjectDetail.vue`；子依赖 `DiffViewer` → `jsondiffpatch` / `dompurify`。**根因假设（Guide）**：`:5173` **dev 模式**下首次懒加载 `ProjectDetail` 触发 Vite `optimizeDeps` 重优化 + 页面 reload，动态 `import()` 在途失败；`npm run build` 本地 **可通过**（非生产构建问题）。**临时规避**：整页刷新后再点详情；或重启 `npm run dev` 预热依赖。**建议修复**：`vite.config.ts` `optimizeDeps.include` 预置 `jsondiffpatch`、`jsondiffpatch/formatters/html`、`dompurify` | **OPEN** |

### 1.3 代码 / 需求锚点（Recorder）

| 项 | 位置 |
|---|---|
| Staging 草稿编号 | `backend/.../ProjectCreateStagingService.java` L36–44 |
| 编号框锁定 | `frontend/src/views/ProjectForm.vue` L102–107、L196–197 |
| 类别硬编码 | `frontend/src/api/archive.ts` L41 |
| 抽取与预填 | `ProjectController.extractPreview` · `qa-agent/app/services/extract.py` |
| 需求 | RI-16（上传预填）· RI-17（立项编号 / series）· SUPPLEMENTARY 字典化（长期） |
| 详情懒加载 | `frontend/src/router/index.ts` · `ProjectDetail.vue` · `DiffViewer.vue` |
| Vite dev | `frontend/vite.config.ts`（`host: 0.0.0.0`，125 经 LAN IP 访问） |

### 1.4 T-0612-12 Console 原文（Operator）

```text
ProjectList.vue:62 [Vue Router warn]: uncaught error during route navigation:
ProjectList.vue:62 TypeError: Failed to fetch dynamically imported module:
  http://182.168.1.125:5173/src/views/ProjectDetail.vue
clientError.ts:21 [ClientError] TypeError: Failed to fetch dynamically imported module: ...
projects:1 Uncaught (in promise) TypeError: Failed to fetch dynamically imported module: ...
```

---

## 2. Agent Blocks

> Co-test 阶段仅 Recorder 落盘；Coder / Reviewer 待 upgrade plan 或本 round 排期后追加。

---

## 3. 关单检查

- [ ] T-0612-09 / 11 已在 upgrade plan 验收通过或 WONTFIX 说明
- [ ] T-0612-10 125 复测：PDF 上传后金额有预填或明确 failure banner
- [ ] T-0612-12：列表 → 详情可进入；dev 模式无 dynamic import 失败
- [ ] Reviewer **CLOSED** · `git mv` → [`done/`](done/README.md)
