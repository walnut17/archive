# Plan D: Phase 2.5 — UX 增强(批量上传 + 智能摘要)

> **状态**: 准备启动(等 Plan C 完成核心功能)
> **优先级**: 🟢 P2.5(关键 UX 改进)
> **工作量**: 3-5 个 commit,半天到 1 天
> **依赖**: Plan C 完成(尤其是 C-3 实体)
> **互斥**: 不与 C 并行

## 必读文档(启动前)

1. `docs/REQUIREMENTS-v1.md` § 4.3 贷后 + § 5.5 立项-申请对比
2. `docs/ARCHITECTURE-v2.md`
3. `docs/DB-SCHEMA-v2.md`
4. `docs/DEV-STANDARDS.md`

## 范围(2 个子项)

### D-1. P2-5 批量上传材料

**目标**: 用户一次选多个文件,自动创建 material + version

**后端**:
- `controller/MaterialController.java` 新增:
  ```
  POST /api/proposals/{proposalId}/materials/batch-upload
  Content-Type: multipart/form-data
  Body: files[] (multiple), defaultCategory, defaultTags
  ```
- `service/MaterialService.batchUpload()`:
  - 每个文件 → 创建 1 Material + 1 MaterialVersion
  - 标题默认 = 文件名(去扩展名)
  - 分类走 defaultCategory
  - 状态 = "草稿"
  - 触发 Tika 解析(同单文件)
  - 返回创建的 Material 列表(含 versionId)
- 事务保护,失败回滚

**前端**:
- `views/ProposalDetail.vue` 材料列表加"批量上传"按钮
- 选文件 → 立即上传 → 显示进度 → 刷新列表
- 不弹设置弹窗(简化:上传后单独编辑元数据)
- 行内快速编辑:双击标题改、el-select 改分类

**测试**:
- 后端: batchUpload 测 5 个文件
- 前端: 浏览器选 5 个文件上传

**验收**: 在议案详情页点"批量上传" → 选 5 个文件 → 一次性创建 5 条材料 + 5 个版本。

### D-2. P2-6 议案摘要自动提取

**目标**: 状态变"已提交"时,若 summary 为空,LLM 自动从立项/申请报告里抽

**后端**:
- `service/ProposalService.submitProposal()`:
  - 状态从"草稿"变"已提交"时触发
  - 触发条件:`proposal.summary` 为 null 或空字符串
  - 信息来源优先级:
    1. material.category='立项报告' 的解析内容
    2. material.category='申请报告' 的解析内容
    3. 该议案下任意已解析材料的最新版本
  - 调 LLM(走 Provider 层):"请从以下材料内容中提取 200-500 字摘要:项目背景 / 主要风险 / 审议要点"
  - 写入 `proposal.summary`
  - 在 `proposal.remark` 追加 `[摘要由系统自动生成于 {{datetime}}]`
  - 失败不阻塞状态流转,记录 warn 日志

**前端**:
- `views/ProposalDetail.vue` 摘要展示:
  - 若 `remark` 含 "[摘要由系统自动生成" 标识 → 显示 `<el-tag type="info">自动生成</el-tag>`
  - 加"重新提取"按钮(调同接口)

**测试**:
- 单元测试 ProposalService.submitProposal 触发条件
- 端到端: 创建议案留空 summary,上传 DOCX,改状态为"已提交",看 summary 自动填充

**验收**:
- 创建一个议案留空 summary
- 上传 DOCX 立项报告并解析成功
- 改状态"已提交"
- 刷新后看到 summary 自动填充 200-500 字,带"自动生成"标签

## 提交规范

```
feat(backend,D-1): MaterialController 批量上传 + MaterialService.batchUpload
feat(frontend,D-1): ProposalDetail 批量上传按钮 + 行内编辑
feat(backend,D-2): ProposalService 状态流转时自动抽摘要
feat(frontend,D-2): ProposalDetail 摘要展示 + 重新提取按钮
```

## 自测

1. `mvn compile / test` 通过
2. `npm run build` 通过
3. 浏览器跑两个场景

## 交回物

1. ✅ 4 个 commit + push
2. ✅ 测试结果
3. ✅ 浏览器端到端截图

## 不在本 plan 范围

- ❌ RBAC 权限(Plan E)
- ❌ 字典 UI(Plan E)

## 风险/注意

- ⚠️ 批量上传**限制单次 <= 20 个文件**(内存考虑)
- ⚠️ 摘要抽取**走异步**,不阻塞状态流转
- ⚠️ 重新提取按钮**确认弹窗**("重新生成会覆盖现有摘要")
