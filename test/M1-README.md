# M1 档案 CRUD — 完成报告

> **状态**:代码完成,等待用户明早验证
> **范围**:Project/Proposal/Material/MaterialVersion 四级档案 + Tika 解析 + 章节切分
> **预计交付**:本周末

## 完成的功能

### 后端
- 4 个 JPA 实体(Project/Proposal/Material/MaterialVersion) + BaseEntity(审计字段)
- 4 个 Repository(Spring Data JPA,分页 + 搜索)
- 3 个 Service(Project/Proposal/Material,业务逻辑 + 状态校验)
- 1 个 MaterialVersionService(上传/解析/版本切换/删除)
- 3 个 REST Controller(15 个 API 端点)
- TikaService(文档解析)
- SectionService(章节切分)
- StorageService(文件存储,带路径遍历防护)
- DTO 8 个(Request/Response/Page)

### 前端
- API 封装(src/api/archive.ts)
- 4 个页面:ProjectList/Form/Detail/ProposalDetail
- 版本管理弹窗 + 章节查看弹窗
- 路由 5 个新页面
- Layout 导航"项目管理"启用

## API 端点(15 个)

### Project
- `GET  /api/projects?page&size&status&keyword`
- `GET  /api/projects/{id}`
- `POST /api/projects`
- `PUT  /api/projects/{id}`
- `DELETE /api/projects/{id}`

### Proposal
- `GET  /api/proposals?page&size&projectId&status&keyword`
- `GET  /api/proposals/{id}`
- `POST /api/proposals`
- `PUT  /api/proposals/{id}`
- `DELETE /api/proposals/{id}`

### Material
- `GET  /api/materials?page&size&proposalId&category&status&keyword`
- `GET  /api/materials/{id}`
- `POST /api/materials`
- `PUT  /api/materials/{id}`
- `DELETE /api/materials/{id}`

### Material Version
- `GET  /api/materials/{mid}/versions`
- `GET  /api/materials/{mid}/versions/{vid}`
- `POST /api/materials/{mid}/versions`(MultipartFile 上传)
- `PUT  /api/materials/{mid}/versions/{vid}/current`(切换当前版本)
- `DELETE /api/materials/{mid}/versions/{vid}`
- `GET  /api/materials/{mid}/versions/{vid}/download`
- `POST /api/materials/{mid}/versions/{vid}/reparse`
- `GET  /api/materials/{mid}/versions/{vid}/sections`(章节切分)

## 数据库表

新建 4 张表,见 `backend/src/main/resources/db/init.sql`:
- `project` (id, code, name, category, owner_id, amount_wan, summary, status, scheduled_meeting_at, remark, 审计字段)
- `proposal` (id, code, title, project_id, type, summary, status, reviewed_at, decision, remark, 审计字段)
- `material` (id, proposal_id, title, category, current_version_id, status, description, tags, 审计字段)
- `material_version` (id, material_id, version_no, original_filename, storage_path, parsed_text_path, file_size, mime_type, sha256, parse_status, parsed_at, parse_error, uploaded_by, change_note, 审计字段)

每张表都建了合适索引,`material_version (material_id, version_no)` 唯一约束。

## 文件存储路径

原始文件: `D:/archive/files/material-{id}/v{n}/{filename}`
解析文本: `D:/archive/parsed/material-{id}/v{n}/{filename}.txt`

路径遍历防护:StorageService.resolveUnderRoot 拒绝 `..` 跳出 root。

## 下一步(M2)

- MySQL FULLTEXT 索引(在 `material_version.parsed_text_path` 对应的文本上加)
- 智谱 GLM-4-Flash 集成(问答重排 + 引用溯源)
- 前端:知识库问答页面

## 测试任务

看 `docs/M1-TEST-TASKS.md` —— 明早按步骤验证。

