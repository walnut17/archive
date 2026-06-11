# v1.1 并行任务表（RI v2 / TASKS.md v1.1 增量）

> 撰写人：投委会档案项目 PM | 日期：2026-06-11
> 上游输入：
> - `.mavis/plans/draft/architecture-v1.1-extended.md`（T1，架构师 da17c92）
> - `.mavis/plans/draft/refactor-and-fix-list.md`（T2）
> - `docs/requirements/ARCH-DECOMPOSITION.md`（RI-1~45 现有拆解）
> - `TASKS.md`（现有任务表 + 抢占 SOP，沿用）
> - `docs/AGENT-IMPL-PLAN.md §5`（任务表样例）
> 配套摘要：`.mavis/plans/draft/tasks-v1.1-summary.md`（PM 过目 1 页版）
> **基线**：`7aa7bae`（v1.0 末 Sisyphus 全面 review 后）
> **零回归**：v1.0 任何 .java / .vue / .sql / pom 行为不破坏

---

## A. RI v2 拆解（在 RI-46~69 续编号）

> 字段沿用 `docs/requirements/ARCH-DECOMPOSITION.md` 现有 6 字段格式
> 业务引用 §X.Y 取自 `docs/requirements/REQUIREMENTS.md`（v1.1）

### RI-46: 置信度 3 级体系（§13.1.1）

- **业务**：替换原 §5.8.3 "0.6 阈值"二元判定为 3 级（≥0.85 自动入库 / 0.60-0.84 AI 推测 / <0.60 待人工）
- **影响表**：`project_fact` ALTER `confidence_level` + `project_fact_event` 同 + 回填 SQL
- **角色**：admin / 业务部门 / 投委会委员
- **验收**：
  1. Given fact `confidence=0.90` When 重抽 Then `confidence_level='CONFIRMED'`
  2. Given fact `confidence=0.70` When 重抽 Then `confidence_level='AI_INFERRED'`，前端显示"AI 推测"徽章
  3. Given fact `confidence=0.50` When 重抽 Then `confidence_level='PENDING_REVIEW'`，标"待人工确认"
  4. **回填 SQL 1 次性跑过**：历史 500+ fact 全部带 `confidence_level` 不为 NULL
  5. **集成测例 ≥ 3 条**（mvn test）
- **依赖**：RI-50（ProjectFactEvent 触发器），RI-47（AgentSystemPrompt 追加）
- **估算**：BE 0.5d / FE 0.2d / 测试 0.2d
- **对应 §X.Y**：REQUIREMENTS §13.1.1
- **对应 T1 §X.Y**：T1 §2.1.1 + §3.3 改造 + §5.2 ALTER

### RI-47: Agent 隐式项目切换 5 级判定（§13.1.2）

- **业务**：替换原 §5.6.7.4 "0.95 阈值"为 5 级（同 projectCode 3 档 + 不同 projectCode 2 档）
- **影响表**：无 DB 改动
- **角色**：admin / 业务部门
- **验收**：
  1. Given locked=PRJ-001，用户问"新能源项目"（实际是 PRJ-001） conf=0.92 Then `SAME_PROBABLY` hint 注入
  2. Given locked=PRJ-001，用户问"江苏那个"（实际是 PRJ-002） conf=0.92 Then `DIFFERENT_PROBABLY` hint + 问用户
  3. Given conf=0.55 不同项目 Then `UNCLEAR` 保持锁定
  4. **5 步上限不变**（`MAX_ITERATIONS=5`）
  5. **集成测例 ≥ 5 条**（mvn test）覆盖 5 级判定
- **依赖**：RI-46（共享 prompt 改）
- **估算**：BE 0.5d / FE 0.2d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.2
- **对应 T1 §X.Y**：T1 §2.1.2 + §1.3 + §3.1 工具改造

### RI-48: 决议变更业务规则（§13.1.3）

- **业务**：草稿可改 / 已开投委会不可改（走"复议"= 新议案）/ 附条件通过增 `condition_status` 跟踪
- **影响表**：`proposal` ALTER +3 字段（condition_text / condition_status / condition_met_at）
- **角色**：admin / 项目经理 / 投委会委员
- **验收**：
  1. Given proposal status='OPEN'（已开投委会）When PATCH `/api/proposals/{id}/decision` Then 403 + message "已开投委会，需走复议"
  2. Given proposal status='DRAFT' When PATCH Then 200 + status 改 'MET'
  3. Given condition_status='MET' When TriggerEngine 扫 Then 自动创建 todo
  4. **回填 SQL**：历史 proposal `condition_status='NONE'`（非 PENDING）
  5. **集成测例 ≥ 4 条**
- **依赖**：RI-58（rollback），RI-49（编号预留）
- **估算**：BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.3
- **对应 T1 §X.Y**：T1 §2.1.3

### RI-49: 投委会编号预留/撤销/改系列（§13.1.4）

- **业务**：draft_reserved（24h 自动释放）/ revoked（编号加 `.revoked` 后缀）/ 改系列（仅 draft_reserved 允许）
- **影响表**：建 `proposal_series` 表 + `proposal` ALTER +2 字段（reserved_at / released_at）
- **角色**：admin / 项目经理
- **验收**：
  1. Given user 调 `POST /api/proposals/reserve` body=`{seriesCode, projectId}` Then 200 + `{proposalCode: 'tx26003', expiresAt: '...+24h'}`
  2. Given 24h 未确认的 draft_reserved When @Scheduled cron 扫 Then 释放
  3. Given proposal status='DRAFT_RESERVED' When `POST /api/proposals/{id}/change-series` Then 200 + 新编号
  4. Given proposal status='OPEN' When 调 change-series Then 403 "已开投委会不可改系列"
  5. **集成测例 ≥ 4 条**
- **依赖**：RI-48
- **估算**：BE 1d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.4
- **对应 T1 §X.Y**：T1 §2.1.4

### RI-50: 网络查 API 字典（§13.1.5）

- **业务**：4 候选（百度百科/维基/金融百科/互动百科）+ 优先级 + 可配 + 内网降级
- **影响表**：`dict_type` / `dict_item` 扩 2 行 + 无 ALTER
- **角色**：admin（改配置）/ 业务部门（用工具）
- **验收**：
  1. Given dict 配置百度百科 priority=1 When Agent 调 `network_dict_lookup("空债权")` Then 命中百度返回 definition
  2. Given 内网全失败 When Agent 调 Then 返回 `{found: false, reason: 'INTRANET_BLOCKED'}`（**不抛异常**）
  3. Given 4 候选全停用 When 调 Then 返回 reason='NO_SOURCE_ENABLED'
  4. Given user 改优先级 priority=2 Then 下次调用按新优先级
  5. **集成测例 ≥ 3 条**（含 mock 外部 API）
- **依赖**：—
- **估算**：BE 1d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.5
- **对应 T1 §X.Y**：T1 §2.1.5 + §1.3 工具 7

### RI-51: 跨项目批量工具安全白名单（§13.1.6）

- **业务**：5 类 filters 字段（region/industry/stage/fact_type/time_bucket）白名单 + 数值上限 + 行数截断 ≤1000
- **影响表**：无 DB 改动（仅 tool 配置 + dict 扩）
- **角色**：admin（配置白名单）/ Agent 工具
- **验收**：
  1. Given filters={region: '江苏', industry: '金融'} When 调 `query_mysql` Then 命中 + 不超 1000 行
  2. Given filters={region: '<script>'} When 调 Then reject + log error
  3. Given result rows=1500 When 调 Then 截断 + warning "请缩小范围"
  4. Given amount=1e10 When 调 Then reject + "数值超限"
  5. **集成测例 ≥ 8 条**（4 重加固已有 + 3 重新增 = 7 重加固全测）
- **依赖**：RI-46
- **估算**：BE 1d / FE 0 / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.6
- **对应 T1 §X.Y**：T1 §2.1.6 + §0 风险 C-8

### RI-52: 关键事实事件流字段细化（§13.1.7）

- **业务**：`project_fact_event` 增 4 字段（owner_id / due_date / resolved_at / resolution_note）
- **影响表**：`project_fact_event` ALTER +4 字段 + DB 触发器（4 字段白名单可改）
- **角色**：admin / 投委会委员
- **验收**：
  1. Given fact event When PATCH owner_id Then 200
  2. Given fact event When PATCH fact_value（非白名单字段） Then 403 + DB 触发器 SIGNAL
  3. Given fact event When DELETE Then 403 + DB 触发器 SIGNAL
  4. Given asOf=2026-06-11 When GET `/api/projects/{id}/fact-events/pending` Then 返回 owner_id 未 resolved 的
  5. **集成测例 ≥ 5 条**
- **依赖**：RI-46
- **估算**：BE 0.7d / FE 0.2d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.7
- **对应 T1 §X.Y**：T1 §2.1.7

### RI-53: 主页"双模"过渡动画（§13.1.8）

- **业务**：待办数 0→1 / N→0 触发 300ms CSS transition
- **影响表**：无 DB 改动
- **角色**：admin / 业务部门
- **验收**：
  1. Given 待办 0 When 新增 1 Then 300ms CSS transition 触发
  2. Given 待办 N When 减到 0 Then 300ms transition
  3. 顶部"问点什么"按钮常驻
- **依赖**：—
- **估算**：FE 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.8
- **对应 T1 §X.Y**：T1 §2.1.8

### RI-54: LLM 抽字段失败兜底（§13.1.9）

- **业务**：5 种失败类型差异化兜底（API 失败/非 JSON/字段缺失/字段值异常/parse 失败）
- **影响表**：无 DB 改动
- **角色**：admin
- **验收**：
  1. Given API 4xx When 抽字段 Then 响应 `failureType=API_ERROR, retryable=true`
  2. Given 返回非 JSON When 抽 Then `failureType=PARSE_ERROR, retryable=true`
  3. Given 字段缺失 Then `failureType=FIELD_MISSING, retryable=true`
  4. Given 字段值异常（如 amount=-1） Then `failureType=VALUE_INVALID, retryable=false`
  5. **集成测例 ≥ 5 条**（每种 failureType 一条）
- **依赖**：—
- **估算**：BE 0.5d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.1.9
- **对应 T1 §X.Y**：T1 §2.1.9

### RI-55: 软删 + 回收站（§13.1.10）

- **业务**：4 实体软删（Project/Proposal/Material/BusinessTerm） + 1 实体不可删（FactEvent） + 30 天回收站
- **影响表**：7 表 ALTER 加 `deleted_at/deleted_by` + `project_fact_event` 触发器
- **角色**：admin（恢复/物理删）
- **验收**：
  1. Given user 调 DELETE project When 软删 Then status='deleted' deleted_at=now
  2. Given 30 天后 @Scheduled cron When 扫 Then 物理删（保留 DB audit 字段）
  3. Given admin 调 `/api/recycle-bin/{id}/restore` Then 200 + status 清掉 deleted
  4. Given 调 DELETE project_fact_event Then 403 + DB 触发器
  5. **集成测例 ≥ 6 条**（含物理删 audit 验证）
- **依赖**：RI-58（rollback 共享 entity 字段）
- **估算**：BE 1.5d / FE 0.7d / 测试 0.5d
- **对应 §X.Y**：REQUIREMENTS §13.1.10
- **对应 T1 §X.Y**：T1 §2.1.10 + §0 风险 C-6

### RI-56: 撤销/回滚/反悔（§13.2.1）

- **业务**：项目 24h 整撤销 / 议案投委会前撤销 / 材料 24h 撤销 / 历史版本回滚
- **影响表**：3 实体 ALTER +`version`（与 RI-58 合并） + 新 `MaterialVersion` 沿用
- **角色**：admin / 项目经理
- **验收**：
  1. Given project 创建 <24h When DELETE Then 整撤销（status='revoked'）
  2. Given project >24h When DELETE Then 软删（走 RI-55）
  3. Given version=3 当前 version=5 When POST `/api/projects/{id}/rollback` body=`{targetVersion: 3}` Then 200 + version=3 + 加 fact_event UPDATE 记录
  4. **集成测例 ≥ 4 条**
- **依赖**：RI-58
- **估算**：BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.2.1
- **对应 T1 §X.Y**：T1 §2.2.1

### RI-57: 并发编辑乐观锁（§13.2.2）

- **业务**：`version` INT 默认 1，UPDATE +1，SQL `WHERE id=? AND version=?` 影响 0 行 → 提示刷新
- **影响表**：3 实体 ALTER +`version`（与 RI-56 合并）
- **角色**：全部
- **验收**：
  1. Given version=1 user A + user B 同时 PATCH When A 先提交 Then A 200 version=2
  2. When B 后提交（version 还是 1） Then 409 + message "数据已被他人修改"
  3. Given v1.1 期 `archive.optimistic-lock.strict=false` When B 冲突 Then 仅记日志，不强制失败
  4. **集成测例 ≥ 3 条**
- **依赖**：—
- **估算**：BE 0.5d / FE 0.2d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.2.2
- **对应 T1 §X.Y**：T1 §2.2.2

### RI-58: RBAC 5 角色（§13.2.3）

- **业务**：admin / 项目经理 / 业务部门 / 投委会委员 / 秘书 + `user_role` 多对多 + `project_member` 项目级
- **影响表**：建 `user_role` / `project_member` 表 + `role` 扩 4 行
- **角色**：admin（分配）
- **验收**：
  1. Given user.role_id=admin（兼容路径） When 登录 Then 200（零回归）
  2. Given user 加 user_role role='COMMITTEE' When 调委员端点 Then 200
  3. Given user 加 project_member role_in_project='OWNER' When 调项目级端点 Then 200
  4. Given 双轨（user.role_id + user_role） When 同时生效 Then 优先级 user_role > role_id
  5. **集成测例 ≥ 5 条**
- **依赖**：—
- **估算**：BE 1d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.2.3
- **对应 T1 §X.Y**：T1 §2.2.3 + §0 风险 C-4

### RI-59: 审计加强（§13.2.4）

- **业务**：5 类审计事件（WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM）
- **影响表**：`audit_log` ALTER +2 字段（type / entity_subtype）
- **角色**：admin（查）
- **验收**：
  1. Given 任意写操作 When 发生 Then `audit_log.type='WRITE'`
  2. Given 登录 When 发生 Then `type='LOGIN'`
  3. Given 脱敏视图申请 When 发生 Then `type='SENSITIVE_VIEW'`
  4. Given 导出 When 发生 Then `type='EXPORT'`
  5. Given LLM 调用 When 发生 Then `type='LLM'`
  6. GET `/api/audit-logs?type=SENSITIVE_VIEW` 返回 5 类筛选
  7. **回填 SQL**：历史 audit_log `type='WRITE'`
  8. **集成测例 ≥ 6 条**
- **依赖**：—
- **估算**：BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.2.4
- **对应 T1 §X.Y**：T1 §2.2.4

### RI-60: 数据生命周期（§13.2.5）

- **业务**：软删 30 天 → 物理删（DB 保留） → 归档 1 年 → 长期归档 5 年 → 不允许永久删
- **影响表**：`material` ALTER +`archived_at` + `application.yml` 增 `archive.retention.*`
- **角色**：admin
- **验收**：
  1. Given 软删 30 天 When @Scheduled cron 扫 Then 物理删文件 + parsed_text
  2. Given archived 1 年 When 扫 Then status='archived'
  3. Given 5 年 + When 扫 Then status='long_archived'
  4. **DB 记录永不删**（仅文件 + parsed_text 物理删）
  5. **集成测例 ≥ 3 条**
- **依赖**：RI-55
- **估算**：BE 0.5d / FE 0 / 测试 0.2d
- **对应 §X.Y**：REQUIREMENTS §13.2.5
- **对应 T1 §X.Y**：T1 §2.2.5

### RI-61: 失败兜底全景（§13.2.6）

- **业务**：10 路径 × N 失败类型 → 统一进 `failure_log` 表 + admin 可查
- **影响表**：建 `failure_log` 表
- **角色**：admin
- **验收**：
  1. Given 任意业务方法抛异常 When AOP 拦截 Then 写 failure_log
  2. GET `/api/failure-logs?resolved=false&path=project.create` 返回筛选
  3. Given admin 标 resolved When PATCH Then resolved_at=now
  4. **集成测例 ≥ 4 条**
- **依赖**：—
- **估算**：BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.2.6
- **对应 T1 §X.Y**：T1 §2.2.6

### RI-62: 项目看板（§13.3.1）

- **业务**：主页"🏠" → "项目看板" 子页，3 视图（表格/卡片/看板分组） + 7 筛选 + 4 排序 + 9 列
- **影响表**：无 DB 改动（聚合查询）
- **角色**：全部
- **验收**：
  1. GET `/api/projects/board?view=kanban&region=江苏&stage=POST_LOAN` 返回看板分组
  2. GET `/api/projects/board?view=table&sort=amount&order=desc&page=1&size=20` 返回表格分页
  3. 9 列含累计议案数 / 待办数 / 最后更新时间
  4. **集成测例 ≥ 3 条**
- **依赖**：—
- **估算**：BE 0.5d / FE 0.7d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.3.1
- **对应 T1 §X.Y**：T1 §2.3.1

### RI-63: 站内通知中心（§13.3.2）

- **业务**：顶栏"🔔" + 弹窗 + 4 类来源（待办/议案/事实/系统） + 已读/未读
- **影响表**：建 `notification` 表
- **角色**：全部
- **验收**：
  1. GET `/api/notifications?unread=true&page=1&size=20` 返回未读列表
  2. PATCH `/api/notifications/{id}/read` 标已读
  3. POST `/api/notifications/mark-all-read` 全标
  4. 30s 轮询触发（不引 SSE）
  5. 4 类来源（todo / proposal / fact / system）全测
  6. **集成测例 ≥ 5 条**
- **依赖**：—
- **估算**：BE 0.7d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.3.2
- **对应 T1 §X.Y**：T1 §2.3.2

### RI-64: 数据导出（§13.3.3）

- **业务**：项目详情页"导出"按钮 + PDF（单项目报告）/ Excel（4 类列表） + 审计
- **影响表**：无 DB 改动
- **角色**：全部
- **验收**：
  1. GET `/api/projects/{id}/export?format=pdf` 返回 application/pdf 流
  2. GET `/api/projects/export?format=xlsx&type=materials` 返回 Excel
  3. 导出时 audit_log.type='EXPORT'
  4. **集成测例 ≥ 4 条**（PDF + 4 类 Excel）
- **依赖**：—
- **估算**：BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.3.3
- **对应 T1 §X.Y**：T1 §2.3.3

### RI-65: 附件预览（§13.3.4）

- **业务**：材料列表点文件名 → 浏览器内嵌预览（PDF/Word/图片/文本）
- **影响表**：无 DB 改动
- **角色**：全部
- **验收**：
  1. GET `/api/materials/{id}/preview?version=3` 返回 application/pdf 流
  2. Word 文件 → 前端 mammoth 转 HTML 渲染
  3. PDF → 前端 pdfjs 渲染
  4. 图片 → 原生 `<img>`
  5. **集成测例 ≥ 4 条**
- **依赖**：—
- **估算**：BE 0.5d / FE 0.7d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.3.4
- **对应 T1 §X.Y**：T1 §2.3.4

### RI-66: 关键事实变更对比视图（§13.3.5）

- **业务**：事实时间线 → 单条 UPDATE → 弹窗"变更对比" + JSON tree diff
- **影响表**：无 DB 改动（派生计算）
- **角色**：全部
- **验收**：
  1. GET `/api/projects/{id}/fact-events/{eventId}/diff` 返回 before/after/evidenceSnippet
  2. 前端 DiffViewer JSON tree diff 渲染
  3. **集成测例 ≥ 3 条**
- **依赖**：RI-52
- **估算**：BE 0.3d / FE 0.5d / 测试 0.2d
- **对应 §X.Y**：REQUIREMENTS §13.3.5
- **对应 T1 §X.Y**：T1 §2.3.5

### RI-67: 业务术语中英对照（§13.3.6）

- **业务**：`business_term` 增 `english_name` 字段 + UI 显示 + Agent 英文查询
- **影响表**：`business_term` ALTER +1 字段
- **角色**：admin
- **验收**：
  1. Given english_name='vacant claim' When 调 `GET /api/dict/terms?q=vacant` Then 200 + 命中
  2. Given Agent 收到英文查询 When 调 network_dict_lookup Then 中文定义返回
  3. **集成测例 ≥ 2 条**
- **依赖**：RI-50（共享 network_dict_lookup）
- **估算**：BE 0.3d / FE 0.2d / 测试 0.2d
- **对应 §X.Y**：REQUIREMENTS §13.3.6
- **对应 T1 §X.Y**：T1 §2.3.6

### RI-68: 旧系统 Excel 导入接口（§13.3.7）

- **业务**：admin "数据导入"入口 + 4 类模板下载 + 字段校验 + 唯一索引冲突 + 导入审计
- **影响表**：建 `import_batch` / `import_error` 表
- **角色**：admin
- **验收**：
  1. POST `/api/admin/import/project` multipart/form-data 上传 .xlsx
  2. 字段校验失败 → 写 `import_error`（row/column/error_msg）
  3. 唯一索引冲突 → 整批 rollback + 提示
  4. GET `/api/admin/import/{batchId}/errors` 返回错误详情
  5. **集成测例 ≥ 5 条**（含 mock Excel）
- **依赖**：—
- **估算**：BE 1d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.3.7
- **对应 T1 §X.Y**：T1 §2.3.7

### RI-69: 数据脱敏视图（§13.3.8）

- **业务**：投委会委员看脱敏视图（`张**` / `***万` / 关联方脱敏） + 申请脱敏查看留痕
- **影响表**：`user` ALTER +1 字段（sensitive_view_enabled）
- **角色**：投委会委员 / admin
- **验收**：
  1. Given user 角色=COMMITTEE sensitive_view_enabled=false When 调 `/api/projects/{id}` Then 响应 `masked: true` + displayName='张**'
  2. Given 调 unmask 请求 When 发生 Then 写 audit_log.type='SENSITIVE_VIEW' + notification 通知 admin
  3. Given admin sensitive_view_enabled=true Then 响应 `masked: false` + 真实 name
  4. **集成测例 ≥ 4 条**
- **依赖**：RI-58（5 角色）/ RI-59（SENSITIVE_VIEW 审计）/ RI-63（admin 通知）
- **估算**：BE 0.5d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**：REQUIREMENTS §13.3.8
- **对应 T1 §X.Y**：T1 §2.3.8

---

## B. 并行任务表（TASKS.md v1.1 增量）

> **每条任务独占 1 节，1 commit = 1-3 小时**。
> **谁先 push 谁占**（沿用 TASKS.md 抢占 SOP）。
> **依赖**：依赖前驱任务先完工。

### T-v1.1-01: README 笔误修（即时）

- **状态**：未开发
- **占用者**：空
- **影响文件**：`README.md §3.2`（独占）
- **工作量**：0.1d（30 min）
- **依赖**：无
- **可并行**：✅
- **详细 spec**：T2 DOC-R-01
- **验收**：`grep -n "backend/agent/" README.md` 应为空，所有路径写 `backend/src/main/java/com/archive/agent/`
- **commit**：`docs(readme,fix): 修 backend/agent/ 路径笔误`

### T-v1.1-02: SQL 迁移 I-RI-22 + I-RI-31（含 7 表 ALTER）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `backend/src/main/resources/db/migration/I-RI-22-confidence-3level.sql`（新）
  - `backend/src/main/resources/db/migration/I-RI-31-soft-delete.sql`（新，含 7 表 ALTER）
  - `backend/src/main/resources/db/init.sql`（同步 append）
- **工作量**：0.5d
- **依赖**：无
- **可并行**：✅（与 T-v1.1-03/04/05 并行）
- **详细 spec**：T2 DB-R-02/06 + T1 §5.2
- **验收**：
  1. mysql 执行 2 个 SQL 无错
  2. 7 表全有 `deleted_at` / `deleted_by` / `version` 字段
  3. `project_fact.confidence_level` 字段存在 + 回填
  4. `project_fact_event` 触发器存在（`SHOW TRIGGERS`）
- **commit**：`feat(db,RI-22+31): 7 表 ALTER + 触发器 + confidence_level 回填`

### T-v1.1-03: SQL 迁移 I-RI-24 + I-RI-25 + I-RI-34

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `backend/src/main/resources/db/migration/I-RI-24-condition-status.sql`（新）
  - `backend/src/main/resources/db/migration/I-RI-25-proposal-series.sql`（新）
  - `backend/src/main/resources/db/migration/I-RI-34-rbac-5-roles.sql`（新）
  - `init.sql` 同步
- **工作量**：0.5d
- **依赖**：无
- **可并行**：✅
- **详细 spec**：T2 DB-R-03/04/08 + T1 §5.1
- **验收**：
  1. `proposal` 有 `condition_text/condition_status/condition_met_at/reserved_at/released_at` 5 字段
  2. `proposal_series` 表存在 + UNIQUE(code)
  3. `user_role` / `project_member` 表存在
  4. `role` 表有 6 行（admin / user / pm / legal / committee / secretary）
- **commit**：`feat(db,RI-24+25+34): proposal_series + RBAC 5 角色 + 条件字段`

### T-v1.1-04: SQL 迁移 I-RI-28 + I-RI-35 + I-RI-37 + I-RI-39 + I-RI-43 + I-RI-44 + I-RI-45（合批）

- **状态**：未开发
- **占用者**：空
- **影响文件**：7 个新 SQL + init.sql 同步
- **工作量**：0.7d
- **依赖**：无
- **可并行**：✅
- **详细 spec**：T2 DB-R-05/09/10/11/12/13/14 + T1 §5.1
- **验收**：11 个 SQL 全部执行无错
- **commit**：`feat(db,RI-28+35+37+39+43+44+45): 7 迁移批（fact_event/audit/failure_log/notification/english_name/import/masking）`

### T-v1.1-05: 实体 ALTER（Project/Proposal/Material + 7 字段）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `backend/src/main/java/com/archive/entity/Project.java`（独占）
  - `backend/src/main/java/com/archive/entity/Proposal.java`（独占）
  - `backend/src/main/java/com/archive/entity/Material.java`（独占）
  - `common/VersionedEntity.java`（新基类，封装 @Version 逻辑）
- **工作量**：0.5d
- **依赖**：T-v1.1-02（SQL 必先）
- **可并行**：✅（与 T-v1.1-06~14 并行）
- **详细 spec**：T2 BE-R-22/23/24
- **验收**：
  1. mvn compile 0 错
  2. mvn test -Dtest=EntityTest 3 测例过
  3. JPA 自动建表 + 字段 nullable 跟 SQL 一致
- **commit**：`feat(be,RI-22+31+33): Project/Proposal/Material 增 deleted_at/version/archive_status 字段`

### T-v1.1-06: 实体 ALTER（AuditLog + ProjectFactEvent + BusinessTerm + User）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `entity/AuditLog.java`（独占，RI-35）
  - `entity/ProjectFactEvent.java`（独占，RI-28 + 触发器白名单）
  - `entity/BusinessTerm.java`（独占，RI-43）
  - `entity/User.java`（独占，RI-45）
- **工作量**：0.5d
- **依赖**：T-v1.1-04（SQL 必先）
- **可并行**：✅
- **详细 spec**：T2 BE-R-25/26/27/28 + DB-R-05
- **验收**：mvn compile 0 错 + 4 实体字段映射正确
- **commit**：`feat(be,RI-28+35+43+45): 4 实体 ALTER + FactEvent 触发器白名单`

### T-v1.1-07: QueryMysqlTool 7 重加固

- **状态**：未开发
- **占用者**：空
- **影响文件**：`backend/src/main/java/com/archive/agent/tool/QueryMysqlTool.java`（独占）
- **工作量**：1d
- **依赖**：T-v1.1-06（实体含 version 字段）
- **可并行**：✅
- **详细 spec**：T2 BE-R-01 + T1 §1.3 #3 + §0 风险 C-8
- **验收**：
  1. mvn test -Dtest=QueryMysqlToolTest 8 测例过
  2. 7 重加固全测（group_by / is_not_null / MAX_IN_VALUES=50 / escapeLikePattern / **filters 白名单** / **行数截断** / **数值上限**）
  3. mvn compile 0 错
- **commit**：`feat(agent,RI-27): QueryMysqlTool 7 重加固（5 类 filters 白名单 + 行数截断 + 数值上限）`

### T-v1.1-08: FindProjectTool 5 级判定

- **状态**：未开发
- **占用者**：空
- **影响文件**：`backend/src/main/java/com/archive/agent/tool/FindProjectTool.java`（独占）
- **工作量**：0.5d
- **依赖**：无
- **可并行**：✅
- **详细 spec**：T2 BE-R-02 + T1 §1.3 #1 + §2.1.2
- **验收**：
  1. mvn test -Dtest=FindProjectToolTest 5 测例过（5 级判定全覆盖）
  2. 工具签名不变（`tool.execute(args) → ToolResult`）
  3. 5 步上限不变（unit test 中跑 1 步 ReAct 验证）
- **commit**：`feat(agent,RI-23): FindProjectTool 5 级隐式切换判定（in-tool，不算 ReAct 步数）`

### T-v1.1-09: AgentSystemPrompt + AgentFewShots 改写

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `agent/prompt/AgentSystemPrompt.java`（独占）
  - `agent/prompt/AgentFewShots.java`（独占）
- **工作量**：0.5d
- **依赖**：T-v1.1-08
- **可并行**：✅
- **详细 spec**：T2 BE-R-04/05 + T1 §6.2
- **验收**：
  1. mvn test -Dtest=AgentSystemPromptTest 渲染正确（2 段追加 + 1 few-shot）
  2. 现有 5 few-shot 保留 + 第 6 条英文术语
  3. mvn compile 0 错
- **commit**：`feat(agent,RI-22+23+43): AgentSystemPrompt 追加 3 级置信度+5 级切换说明 + Few-shot 加英文术语`

### T-v1.1-10: NetworkDictLookupTool 新增（工具 7）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `agent/tool/NetworkDictLookupTool.java`（新，独占）
  - `common/FailureType.java`（新，独占）
  - `agent/AgentContext.java`（改 1 字段）
- **工作量**：1d
- **依赖**：T-v1.1-04（dict 表扩）
- **可并行**：✅
- **详细 spec**：T1 §1.3 + §2.1.5 + §6.1
- **验收**：
  1. mvn test -Dtest=NetworkDictLookupToolTest 3 测例过（命中/降级/全失败）
  2. mvn test -Dtest=AgentEngineTest 7 工具自动注入
  3. 内网全失败时返回 `{found: false, reason: 'INTRANET_BLOCKED'}`（**不抛异常**）
- **commit**：`feat(agent,RI-26): NetworkDictLookupTool 新增（工具 7）+ 6 层降级`

### T-v1.1-11: 软删 + 回收站（RI-31 + RI-55）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `controller/ProjectController.java`（独占改 DELETE 路径）
  - `controller/MaterialController.java`（独占改 DELETE 路径）
  - `controller/RecycleBinController.java`（新）
  - `service/RecycleBinService.java`（新）
  - `service/ProjectService.java`（改）
- **工作量**：1.5d
- **依赖**：T-v1.1-05
- **可并行**：✅
- **详细 spec**：T2 BE-R-11/13 + T1 §2.1.10 + §3.3
- **验收**：
  1. mvn test -Dtest=RecycleBinServiceTest 6 测例过
  2. DELETE project 走软删（status='deleted'）
  3. 旧物理删路径移到 `/api/admin/projects/{id}/purge` (admin only)
  4. 30 天 @Scheduled cron 扫过期
- **commit**：`feat(be,RI-31+55): 软删+回收站（Project/Material/Proposal/BusinessTerm）`

### T-v1.1-12: 决议变更 + 编号预留（RI-48 + RI-49）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `controller/ProposalController.java`（独占改 PATCH + 新增 reserve/revoke/change-series 端点）
  - `service/ProposalService.java`（改）
  - `service/ProposalNumberGenerator.java`（重写）
  - `engine/TriggerEngine.java`（改 1 条规则）
- **工作量**：1.5d
- **依赖**：T-v1.1-05
- **可并行**：✅
- **详细 spec**：T2 BE-R-14 + T1 §2.1.3 + §2.1.4
- **验收**：
  1. mvn test -Dtest=ProposalServiceTest 8 测例过（4 决议 + 4 编号）
  2. 已开投委会不可改 → 403
  3. 24h 未确认 draft_reserved 自动释放
  4. TriggerEngine condition_status='MET' 自动建 todo
- **commit**：`feat(be,RI-48+49): 决议变更 + 编号预留/撤销/改系列 + Trigger 规则`

### T-v1.1-13: 乐观锁 RI-57 + 撤销回滚 RI-56

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `service/ProjectService.java`（改 UPDATE）
  - `service/ProposalService.java`（改 UPDATE）
  - `service/MaterialService.java`（改 UPDATE）
  - `controller/ProjectController.java`（增 rollback 端点）
  - `service/ProjectRollbackService.java`（新）
  - `common/GlobalExceptionHandler.java`（增 OptimisticLockException handler）
- **工作量**：1d
- **依赖**：T-v1.1-05
- **可并行**：✅
- **详细 spec**：T2 BE-R-32/33 + T1 §2.2.1 + §2.2.2
- **验收**：
  1. mvn test -Dtest=OptimisticLockTest 3 测例过
  2. 并发 2 user UPDATE 同 project → 1 成功 1 409
  3. `archive.optimistic-lock.strict=false` 时冲突仅记日志
  4. rollback 端点 version=3→5 不丢 fact_event 记录
- **commit**：`feat(be,RI-56+57): @Version 乐观锁 + rollback + 24h 整撤销`

### T-v1.1-14: RBAC 5 角色 RI-58

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `security/SecurityConfig.java`（改）
  - `security/JwtAuthFilter.java`（改 userRoles claim）
  - `service/RbacService.java`（新）
  - `controller/AdminUserController.java`（新，GET /api/admin/users/{id}/roles）
- **工作量**：1d
- **依赖**：T-v1.1-03
- **可并行**：✅
- **详细 spec**：T2 BE-R-34/35 + T1 §2.2.3
- **验收**：
  1. mvn test -Dtest=RbacServiceTest 5 测例过
  2. admin 登录路径不变（**零回归**）
  3. user_role 多对多 + project_member 项目级都生效
  4. JwtAuthFilter userRoles claim 正确
- **commit**：`feat(be,RI-58): RBAC 5 角色 + 双轨（user.role_id 兼容 + user_role 主用）`

### T-v1.1-15: 审计加强 RI-59

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `service/AuditLogService.java`（改 + 3 新方法）
  - `controller/AuditLogController.java`（改 + type 过滤）
- **工作量**：0.7d
- **依赖**：T-v1.1-06
- **可并行**：✅
- **详细 spec**：T2 BE-R-15/21 + T1 §2.2.4
- **验收**：
  1. mvn test -Dtest=AuditLogServiceTest 6 测例过
  2. 5 类事件（WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM）全写
  3. GET `/api/audit-logs?type=SENSITIVE_VIEW` 过滤
- **commit**：`feat(be,RI-59): 审计加强（5 类事件 + type 字段 + 6 测例）`

### T-v1.1-16: 关键事实事件 RI-46 + RI-52

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `controller/ProjectFactEventController.java`（新）
  - `service/ProjectFactEventService.java`（新）
  - `dto/ProjectFactEventResponse.java`（新）
  - `entity/ProjectFactEvent.java`（改 @PreUpdate/@PreDelete 拦截）
- **工作量**：1d
- **依赖**：T-v1.1-06
- **可并行**：✅
- **详细 spec**：T2 BE-R-26/29 + T1 §2.1.7
- **验收**：
  1. mvn test -Dtest=ProjectFactEventTest 5 测例过
  2. UPDATE 白名单字段 OK
  3. UPDATE 非白名单 / DELETE 抛异常（DB 触发器 + EntityListener 双保险）
  4. GET `/api/projects/{id}/fact-events/pending?asOf=...` 正确
- **commit**：`feat(be,RI-46+52): 关键事实事件 5 字段 + 触发器 + EntityListener 双保险`

### T-v1.1-17: LLM 抽字段失败兜底 RI-54

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `service/GlmService.java`（改 callWithLog + FailureType 分类）
  - `engine/ExtractionEngine.java`（改 onFailure 签名）
  - `dto/ExtractionFailureResponse.java`（新）
  - `common/FailureType.java`（新，5 枚举）
- **工作量**：0.5d
- **依赖**：无
- **可并行**：✅
- **详细 spec**：T2 BE-R-18/19 + T1 §2.1.9
- **验收**：
  1. mvn test -Dtest=GlmServiceTest 5 测例过（每种 FailureType 一条）
  2. 5 种 failureType 全测
  3. mvn compile 0 错
- **commit**：`feat(be,RI-54): GlmService 5 种 FailureType + ExtractionEngine 改回调`

### T-v1.1-18: 失败兜底 RI-61

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `entity/FailureLog.java`（新）
  - `repository/FailureLogRepository.java`（新）
  - `service/FailureLogService.java`（新）
  - `controller/FailureLogController.java`（新）
  - `common/BusinessAop.java`（新，AOP 拦截）
- **工作量**：0.7d
- **依赖**：T-v1.1-04
- **可并行**：✅
- **详细 spec**：T1 §2.2.6
- **验收**：
  1. mvn test -Dtest=FailureLogServiceTest 4 测例过
  2. AOP 拦截 @Service 方法抛异常 → 写 failure_log
  3. GET `/api/failure-logs?resolved=false&path=project.create` 过滤
- **commit**：`feat(be,RI-61): FailureLog + AOP 拦截 + admin 失败大盘`

### T-v1.1-19: 数据生命周期 RI-60

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `entity/Material.java`（改，加 archived_at 字段 — 跟 T-v1.1-05 合并到同一 commit）
  - `service/ArchiveService.java`（新）
  - `service/RecycleBinService.java`（扩 30 天扫描逻辑）
  - `application.yml`（增 `archive.retention.*`）
- **工作量**：0.5d
- **依赖**：T-v1.1-11
- **可并行**：✅
- **详细 spec**：T1 §2.2.5
- **验收**：
  1. mvn test -Dtest=ArchiveServiceTest 3 测例过
  2. 30 天 / 1 年 / 5 年 三阶段 @Scheduled cron
  3. DB 记录永不删
- **commit**：`feat(be,RI-60): 数据生命周期（30 天物理删 + 1 年归档 + 5 年长期）`

### T-v1.1-20: 项目看板 RI-62

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `controller/ProjectBoardController.java`（新）
  - `service/ProjectBoardService.java`（新）
  - `repository/ProjectRepository.java`（扩 findBoardView）
- **工作量**：0.5d
- **依赖**：—
- **可并行**：✅
- **详细 spec**：T1 §2.3.1
- **验收**：
  1. mvn test -Dtest=ProjectBoardServiceTest 3 测例过
  2. 3 视图（table/card/kanban）切换
  3. 7 筛选 + 4 排序
- **commit**：`feat(be,RI-62): 项目看板 3 视图 + 7 筛选 + 4 排序`

### T-v1.1-21: 通知中心 RI-63

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `entity/Notification.java`（新）
  - `repository/NotificationRepository.java`（新）
  - `service/NotificationService.java`（新）
  - `controller/NotificationController.java`（新）
  - `application.yml`（增 `archive.notification.polling-interval=30s`）
- **工作量**：0.7d
- **依赖**：T-v1.1-04
- **可并行**：✅
- **详细 spec**：T1 §2.3.2
- **验收**：
  1. mvn test -Dtest=NotificationServiceTest 5 测例过
  2. 4 类来源（todo/proposal/fact/system）全测
  3. 30s 轮询（不引 SSE）
- **commit**：`feat(be,RI-63): 通知中心（4 类来源 + 30s 轮询 + 全局）`

### T-v1.1-22: 数据导出 RI-64

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `controller/ProjectController.java`（增 export 端点）
  - `service/ExportService.java`（新，OpenPDF + POI）
  - `pom.xml`（加 OpenPDF 2.0.2 + POI 5.2.5）
- **工作量**：0.7d
- **依赖**：—
- **可并行**：✅
- **详细 spec**：T1 §2.3.3 + §3.5
- **验收**：
  1. mvn test -Dtest=ExportServiceTest 4 测例过
  2. PDF / Excel 4 类（project/material/proposal/fact）全测
  3. 审计走 `AuditLogService.logExport()`
  4. jar 增量 < 10MB
- **commit**：`feat(be,RI-64): 数据导出 PDF/Excel（OpenPDF + Apache POI）`

### T-v1.1-23: 附件预览 RI-65

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `controller/MaterialController.java`（增 preview 端点）
  - `service/PreviewService.java`（新）
- **工作量**：0.5d
- **依赖**：—
- **可并行**：✅
- **详细 spec**：T1 §2.3.4
- **验收**：
  1. mvn test -Dtest=PreviewServiceTest 4 测例过
  2. PDF / Word / 图片 / 文本 4 类全测
  3. Word 走前端 mammoth（**不引 LibreOffice**）
- **commit**：`feat(be,RI-65): 附件预览（PDF/Word/图片/文本流）`

### T-v1.1-24: 业务术语英文 RI-67

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `entity/BusinessTerm.java`（改，加 english_name — 跟 T-v1.1-06 合并）
  - `service/DictService.java`（改 query 方法）
- **工作量**：0.3d
- **依赖**：T-v1.1-06 + T-v1.1-10
- **可并行**：✅
- **详细 spec**：T1 §2.3.6
- **验收**：
  1. mvn test -Dtest=DictServiceTest 2 测例过
  2. GET `/api/dict/terms?q=vacant` 命中 english_name
- **commit**：`feat(be,RI-67): 业务术语英文查询（english_name 字段 + Agent 联动）`

### T-v1.1-25: 旧系统导入 RI-68

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `controller/ImportController.java`（新）
  - `service/ImportService.java`（新）
  - `entity/ImportBatch.java`（新）
  - `entity/ImportError.java`（新）
  - `repository/ImportBatchRepository.java`（新）
  - `repository/ImportErrorRepository.java`（新）
- **工作量**：1d
- **依赖**：T-v1.1-04
- **可并行**：✅
- **详细 spec**：T1 §2.3.7
- **验收**：
  1. mvn test -Dtest=ImportServiceTest 5 测例过
  2. 4 类模板（project/material/proposal/fact）全测
  3. 字段校验失败写 import_error
  4. 唯一索引冲突整批 rollback
- **commit**：`feat(be,RI-68): 旧系统 Excel 导入（4 类模板 + 字段校验 + 错误明细）`

### T-v1.1-26: 脱敏视图 RI-69

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `service/MaskingService.java`（新）
  - `dto/ProjectResponse.java`（增 masked/displayName/displayAmount 字段）
  - `entity/User.java`（改 — 跟 T-v1.1-06 合并）
- **工作量**：0.5d
- **依赖**：T-v1.1-06 + T-v1.1-14 + T-v1.1-15 + T-v1.1-21
- **可并行**：❌（等前驱）
- **详细 spec**：T1 §2.3.8
- **验收**：
  1. mvn test -Dtest=MaskingServiceTest 4 测例过
  2. 委员视图 `masked: true` + `displayName='张**'`
  3. unmask 申请写 audit_log.type='SENSITIVE_VIEW' + notification 通知 admin
- **commit**：`feat(be,RI-69): 数据脱敏视图（委员视图 + 申请查看留痕）`

### T-v1.1-27: 关键事实变更对比 RI-66

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `service/ProjectFactEventService.java`（扩 getDiff 方法）
- **工作量**：0.3d
- **依赖**：T-v1.1-16
- **可并行**：✅
- **详细 spec**：T1 §2.3.5
- **验收**：
  1. mvn test -Dtest=ProjectFactEventServiceTest 3 测例过
  2. GET `/api/projects/{id}/fact-events/{eventId}/diff` 返回 before/after
- **commit**：`feat(be,RI-66): 关键事实变更对比（JSON tree diff API）`

### T-v1.1-28: 主页双模动画 RI-53

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/views/Dashboard.vue`（独占）
  - `frontend/src/components/AnimatedModeSwitch.vue`（新）
- **工作量**：0.3d
- **依赖**：—
- **可并行**：✅
- **详细 spec**：T1 §2.1.8
- **验收**：
  1. npm run build 0 错
  2. 浏览器测 0→1 / N→0 300ms 过渡触发
  3. 顶部"问点什么"按钮常驻
- **commit**：`feat(fe,RI-53): 主页双模过渡动画（300ms CSS transition）`

### T-v1.1-29: 前端知识库增强（Knowledge.vue 改）

- **状态**：未开发
- **占用者**：空
- **影响文件**：`frontend/src/views/Knowledge.vue`（独占）
- **工作量**：0.5d
- **依赖**：—
- **可并行**：✅
- **详细 spec**：T1 §4.5
- **验收**：
  1. npm run build 0 错
  2. 置信度 3 级徽章显示
  3. 隐式切换 5 级 hint 文案
  4. 现有功能不破（折叠/展开/历史/导出 markdown）
- **commit**：`feat(fe,RI-22+23): Knowledge.vue 加置信度徽章 + 切换 hint`

### T-v1.1-30: 前端通知中心 RI-63

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/components/NotifBell.vue`（新）
  - `frontend/src/views/Notification.vue`（新）
  - `frontend/src/store/notification.ts`（新）
  - `frontend/src/api/notification.ts`（新）
  - `frontend/src/views/Layout.vue`（改）
  - `frontend/src/router/index.ts`（增 /notifications）
  - `frontend/package.json`（加 dayjs ^1.11）
- **工作量**：0.7d
- **依赖**：T-v1.1-21
- **可并行**：✅
- **详细 spec**：T1 §4.2 + §4.4
- **验收**：
  1. npm run build 0 错
  2. 顶栏铃铛 + 未读数 badge
  3. 通知中心全屏 + 4 类筛选 + 已读/未读
- **commit**：`feat(fe,RI-63): 前端通知中心（NotifBell + Notification.vue + Pinia store）`

### T-v1.1-31: 前端项目看板 RI-62

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/views/ProjectBoard.vue`（新）
  - `frontend/src/router/index.ts`（增 /projects/board）
  - `frontend/src/views/ProjectList.vue`（改入口按钮）
- **工作量**：0.7d
- **依赖**：T-v1.1-20
- **可并行**：✅
- **详细 spec**：T1 §2.3.1
- **验收**：
  1. npm run build 0 错
  2. 3 视图切换（table/card/kanban）
  3. 7 筛选 + 4 排序
- **commit**：`feat(fe,RI-62): 前端项目看板（3 视图 + 7 筛选 + 4 排序）`

### T-v1.1-32: 前端附件预览 RI-65

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/components/PreviewFrame.vue`（新，pdfjs + mammoth）
  - `frontend/src/views/ProjectDetail.vue`（改材料 tab）
  - `frontend/package.json`（加 pdfjs-dist ^4.0 + mammoth ^1.7）
- **工作量**：0.7d
- **依赖**：T-v1.1-23
- **可并行**：✅
- **详细 spec**：T1 §2.3.4
- **验收**：
  1. npm run build 0 错
  2. PDF / Word / 图片 / 文本 4 类内嵌预览
- **commit**：`feat(fe,RI-65): 前端附件预览（pdfjs + mammoth）`

### T-v1.1-33: 前端事实变更对比 RI-66

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/components/DiffViewer.vue`（新，jsondiffpatch）
  - `frontend/src/views/ProjectDetail.vue`（改事实 tab）
  - `frontend/package.json`（加 jsondiffpatch ^0.5）
- **工作量**：0.5d
- **依赖**：T-v1.1-27
- **可并行**：✅
- **详细 spec**：T1 §2.3.5
- **验收**：
  1. npm run build 0 错
  2. JSON tree diff 弹窗渲染
- **commit**：`feat(fe,RI-66): 前端事实变更对比（DiffViewer + JSON tree diff）`

### T-v1.1-34: 前端导入向导 RI-68

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/views/ImportWizard.vue`（新）
  - `frontend/src/router/index.ts`（增 /admin/import）
  - `frontend/src/api/archive.ts`（增 4 端点）
- **工作量**：0.5d
- **依赖**：T-v1.1-25
- **可并行**：✅
- **详细 spec**：T1 §2.3.7
- **验收**：
  1. npm run build 0 错
  2. 4 类模板下载
  3. 上传 .xlsx + 错误明细展示
- **commit**：`feat(fe,RI-68): 前端数据导入向导（ImportWizard.vue）`

### T-v1.1-35: 前端脱敏视图 RI-69

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/components/MaskedField.vue`（新）
  - `frontend/src/views/ProjectDetail.vue`（改显示）
  - `frontend/src/api/archive.ts`（增 unmask 端点）
- **工作量**：0.5d
- **依赖**：T-v1.1-26
- **可并行**：✅
- **详细 spec**：T1 §2.3.8
- **验收**：
  1. npm run build 0 错
  2. 委员视图脱敏 + 申请查看按钮
- **commit**：`feat(fe,RI-69): 前端脱敏视图（MaskedField + 申请查看）`

### T-v1.1-36: 前端回收站 RI-55

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `frontend/src/views/RecycleBin.vue`（新）
  - `frontend/src/router/index.ts`（增 /recycle-bin, admin only）
  - `frontend/src/components/RecycleBinList.vue`（新）
- **工作量**：0.7d
- **依赖**：T-v1.1-11
- **可并行**：✅
- **详细 spec**：T1 §2.1.10
- **验收**：
  1. npm run build 0 错
  2. 4 实体回收站列表 + 恢复按钮 + 物理删按钮（admin）
- **commit**：`feat(fe,RI-55): 前端回收站（RecycleBin.vue + 4 实体列表）`

### T-v1.1-37: application.yml + config.json 模板

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `backend/src/main/resources/application.yml`（改，5 段新增）
  - `config/config.example.json`（改，5 字段扩）
  - `docs/GLM-KEY-SETUP.md`（增 network-dict 段）
- **工作量**：0.3d
- **依赖**：无
- **可并行**：✅
- **详细 spec**：T1 §7.1
- **验收**：
  1. application.yml 含 `archive.network-dict.*` / `archive.query-mysql.*` / `archive.optimistic-lock.*` / `archive.retention.*` / `archive.audit.*` 5 段
  2. config.example.json 同步 5 字段
- **commit**：`feat(config,v1.1): application.yml + config.json 5 段新增`

### T-v1.1-38: 文档同步（ARCH-DECOMPOSITION + AGENT-DECISION + 6 份分章架构）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `docs/requirements/ARCH-DECOMPOSITION.md`（append RI-46~69）
  - `docs/AGENT-FRAMEWORK-DECISION.md §1.2 标题`（修）
  - `docs/AGENT-FRAMEWORK-DECISION.md §1.2.1.1`（增 v1.1 决策段）
  - `docs/architecture/01~06-arch-*.md`（6 份同步增量）
  - `docs/DB-SCHEMA-v2.md`（同步 7 ALTER + 7 新表）
  - `docs/ARCHITECTURE-v2.md`（同步 Agent 工具 6→7 + Controller 13→18）
- **工作量**：1d
- **依赖**：T-v1.1-30~36 完工
- **可并行**：❌（等业务完工）
- **详细 spec**：T2 DOC-R-01~10
- **验收**：6 份分章架构 + ARCHITECTURE-v2 + DB-SCHEMA-v2 + AGENT-DECISION 全部同步
- **commit**：`docs(arch,v1.1): 同步 24 RI 增量 + AGENT-DECISION 标题修 + 6 份分章同步`

### T-v1.1-39: TASKS.md v1.1 章节 publish

- **状态**：未开发
- **占用者**：空
- **影响文件**：`TASKS.md`（append v1.1 章节，引用 T3）
- **工作量**：0.2d
- **依赖**：T-v1.1-38
- **可并行**：❌
- **详细 spec**：沿用 TASKS.md 现有格式
- **验收**：`TASKS.md` 末段有 v1.1 章节，含 30+ 任务
- **commit**：`docs(tasks,v1.1): TASKS.md publish v1.1 章节（30+ 任务）`

### T-v1.1-40: 集成测试 v1.1（30+ 测例）

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `backend/src/test/java/com/archive/v11/V11IntegrationTest.java`（新）
  - `backend/src/test/resources/application-test.yml`（扩）
- **工作量**：1.5d
- **依赖**：T-v1.1-30~36 完工
- **可并行**：❌
- **详细 spec**：本表所有 RI 验收段的"集成测例 ≥ N 条"汇总
- **验收**：
  1. mvn test 30+ 测例全过
  2. 5 大场景（看板/通知/导出/预览/脱敏）端到端
  3. 降级测试（关 archive.enabled=false）
- **commit**：`test(v1.1): 30+ 集成测例（5 大场景 + 降级）`

### T-v1.1-41: 端到端验收 + LESSONS 复盘

- **状态**：未开发
- **占用者**：空
- **影响文件**：
  - `docs/ACCEPTANCE-GUIDE.md`（扩 v1.1 验收场景）
  - `docs/LESSONS-LEARNED.md`（追加 P0-24~）
  - `docs/reviews/2026-06-XX-v1.1-review.md`（新）
- **工作量**：0.5d
- **依赖**：T-v1.1-40
- **可并行**：❌
- **详细 spec**：沿用 v1.0 review 格式
- **验收**：11 步验收 SOP + LESSONS 追加 + review 文件
- **commit**：`docs(v1.1,review): 端到端验收 + LESSONS 复盘`

---

## C. 依赖图 + 抢占 SOP

### C.1 依赖图

```
T-v1.1-01 (README 笔误修,即开)
  ↓
T-v1.1-02/03/04 (SQL 迁移 3 批) ─┬─→ T-v1.1-05 (3 实体 ALTER)
                                  ├─→ T-v1.1-06 (4 实体 ALTER)  ─→ T-v1.1-16 (RI-46+52)
                                  │                              ├─→ T-v1.1-17 (RI-54)
                                  │                              ├─→ T-v1.1-15 (RI-59)
                                  │                              └─→ T-v1.1-24 (RI-67)
                                  ├─→ T-v1.1-10 (RI-26 NetworkDictTool)
                                  ├─→ T-v1.1-18 (RI-61 FailureLog)
                                  ├─→ T-v1.1-21 (RI-63 Notification) ─→ T-v1.1-30 (FE Notification)
                                  └─→ T-v1.1-25 (RI-68 Import) ─→ T-v1.1-34 (FE Import)

T-v1.1-05 (3 实体 ALTER) ─┬─→ T-v1.1-07 (QueryMysqlTool 7 重加固)
                          ├─→ T-v1.1-11 (RI-31+55 软删) ─→ T-v1.1-19 (RI-60 数据生命周期)
                          │                       └─→ T-v1.1-36 (FE 回收站)
                          ├─→ T-v1.1-12 (RI-48+49 决议+编号)
                          └─→ T-v1.1-13 (RI-56+57 乐观锁+回滚)

T-v1.1-08 (FindProject 5 级) ─→ T-v1.1-09 (AgentSystemPrompt 改写)

T-v1.1-03 (SQL RBAC) ─→ T-v1.1-14 (RBAC 5 角色)

T-v1.1-14 + T-v1.1-15 + T-v1.1-21 ─→ T-v1.1-26 (RI-69 脱敏) ─→ T-v1.1-35 (FE 脱敏)
T-v1.1-16 ─→ T-v1.1-27 (RI-66 diff API) ─→ T-v1.1-33 (FE DiffViewer)

T-v1.1-20 (RI-62 BE 看板) ─→ T-v1.1-31 (FE 看板)
T-v1.1-23 (RI-65 BE 预览) ─→ T-v1.1-32 (FE 预览)

T-v1.1-30~36 全部完工 ─→ T-v1.1-37 (config) ─→ T-v1.1-38 (文档同步) ─→ T-v1.1-39 (TASKS publish)
                                                                    └─→ T-v1.1-40 (集成测试) ─→ T-v1.1-41 (端到端验收)
```

### C.2 并行机会

**5 路同时**：
- T-v1.1-02/03/04（3 批 SQL 互不冲突）
- T-v1.1-37（application.yml）

**8 路同时**（SQL 完工后）：
- T-v1.1-05/06（实体 ALTER 2 批）
- T-v1.1-08（FindProject）
- T-v1.1-14（RBAC）— 需 T-v1.1-03
- T-v1.1-15（审计）— 需 T-v1.1-06
- T-v1.1-17（GlmService FailureType）
- T-v1.1-18/20/21/23/25（多种新 service）
- T-v1.1-28/29（前端）

### C.3 关键路径

```
T-v1.1-02 (SQL) → T-v1.1-05 (实体) → T-v1.1-11 (软删) → T-v1.1-40 (集成测试) → T-v1.1-41 (验收)
   ↓ 0.5d         ↓ 0.5d              ↓ 1.5d                ↓ 1.5d                ↓ 0.5d
合计：~4.5 天（关键路径，串行）
```

### C.4 抢占 SOP（沿用 TASKS.md）

1. 看 TASKS.md v1.1 章节，找 `可并行: ✅` + `未开发` + 匹配技术栈的任务
2. 改 `状态: 未开发` → `占用-<你的名字> (<当前时间>)`
3. **10 秒内** `git add TASKS.md && git commit && git push origin main`
4. **push 成功 = 占用成功**

### C.5 失联 30 分钟接管 SOP（沿用 TASKS.md）

1. `git log --author="<占用人名字>" -1` 看最后一次 commit 时间
2. > 30 分钟没动 = 失联
3. 接管：`占用-X` → `占用-<自己> (reclaim from X)`, push main
4. 项目方（PM）不追责

### C.6 严禁清单（沿用 TASKS.md）

- ❌ 改 `占用-A` 改回 `未开发`
- ❌ 一个 commit 改多个任务
- ❌ 占用了但**没 push** 超过 10 分钟
- ❌ 直推 `minimax` 分支
- ❌ 改 `REQUIREMENTS.md`（那是需求开发人员的活）
- ❌ 改 `ARCH-DECOMPOSITION.md`（除了 T-v1.1-38 任务）

---

## D. 任务统计

| 维度 | 数 |
|---|---|
| 总任务 | 41 |
| P0（必抢） | 14 项（含 1 个 README 笔误修即时） |
| P1（业务增量） | 24 项 |
| P2（文档/收尾） | 3 项 |
| 总工时 | ~30 天（6 程序员并行 ≈ 5 周） |
| 关键路径 | ~4.5 天（串行） |
| 5 路并行 | 8 组（见 C.2） |
| 集成测试 | 30+ 测例（mvn test） |
| SQL 迁移 | 11 个新文件 |
| 新增 Controller | 5 个 |
| 新增 Service | 12 个 |
| 新增 Entity | 7 个 |
| 新增前端 View | 5 个 |
| 新增前端 Component | 4 个 |
| ALTER 表 | 7 张 |

---

*本任务表由 PM 维护。接手 agent 看 §B 找任务，看 §C 找依赖，看 §A 找需求引用。*
*占任务时改 `状态: 未开发` → `占用-<你的名字>`，10 秒内 push main。*
