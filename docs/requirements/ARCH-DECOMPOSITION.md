# 投委会档案管理系统 — 需求拆解工作底稿 (v1.1)

> **文档说明**: 本文是**为架构师 / 后端 / 前端 / DBA / 测试** 服务. 把 `REQUIREMENTS.md` v1.1 的业务需求拆成一个一个可落地的工作项.
> **来源**: REQUIREMENTS.md v1.1 (7 条业务碎片 + 3 批 Mavis 拓展整合, 2026-06-10)
> **目的**: 架构师可按本表排期, 每个人领一个或多个 RI (Requirement Item), 独立完成

---

## ⚠️ 重要说明 (2026-06-10 22:50 更新)

**本次会话 Mavis 重建此文档的过程**:

1. **第一阶段 (22:40)**: 本文件 RI-1~21 内容**因未 commit 永久丢失**. Mavis 误用 Windows 大小写不敏感的 `Remove-Item`, 把 ARCH-DECOMPOSITION.md 文件删了. 重建时只含 RI-22~45 (本轮新增) + "诚实声明"section.

2. **第二阶段 (22:50)**: **业务方拍方案 B** —— 从上一轮对话 summary + 本轮 REQUIREMENTS.md v1.1 章节, Mavis 重新生成 RI-1~21 骨架 (按章节 §5.6 / §5.6.7 / §5.6.8 / §5.6.9 / §5.7 / §5.8 / §5.9 / §5.10 / §5.11 / §9.1 / §10.3 / §12 拆 21 个 RI).

3. **本文件 RI-22~45 (本轮完整)** + **RI-1~21 (方案 B 恢复)** 全部到位, 共 45 个 RI.

4. **教训**: Mavis 以后**所有写入磁盘的非临时文档, 必须立刻 commit** 或放到 git-tracked 路径, 避免 working copy 误删时无 recovery 路径.

---



### RI-1 智能问答入口与 Agent 模式 (§5.6)

- **业务**: §5.6 智能问答 4 级兜底 (精确 / FULLTEXT / LIKE / LLM) + 死循环保护
- **影响**: `QaController.ask` + `AgentEngine.run` (ReAct 循环) + `FindProjectTool` 4 级 fallback
- **验收**:
  - 4 级 fallback 顺序: 精确 projectCode → FULLTEXT → LIKE → LLM 语义
  - 死循环 1 次 hint / 2 次 force FINAL_ANSWER
  - LLM 兜底开关: `llm-fallback-max-total` (默认 300, 超过禁用 LLM)
- **依赖**: `glossary.md` 项目索引 (若有) / LLM 配 (GLM API key)
- **估算**: BE 3d / FE 1d / 测试 1d
- **配置项**: `agent.find-project.llm-fallback-max-total`, `agent.find-project.loop-trigger-threshold`

### RI-2 多轮对话与上下文窗口 (§5.6.7)

- **业务**: 登录态下 30 天会话保留, 滑动 3 轮原文 + 关键事实表
- **影响表**: `conversation` (id / user_id / locked_project_code / created_at) + `conversation_message` (conversation_id / role / content / created_at) + `conversation_fact` (key / value)
- **验收**:
  - 用户连续 3 轮原文送 LLM
  - 关键事实表 (locked project, user role) 永远在 LLM 上下文
  - 关 tab 清空 / 显式清空按钮
- **依赖**: RI-1 (智能问答)
- **估算**: BE 2d / FE 1.5d / 测试 1d

### RI-3 智能问答首页入口 (§5.6.8)

- **业务**: 主页"双模" (无待办 = 问答 / 有待办 = 待办列表, 顶部问答入口按钮始终可见)
- **影响**: `Home.vue` 状态机 + 待办数变化触发视图切换
- **验收**:
  - 待办数 = 0 → 主页显示问答
  - 待办数 > 0 → 主页显示待办, 顶部"问点什么"按钮折叠
  - 平滑过渡 300ms (Mavis 拓展 §13.1.8)
- **依赖**: RI-1, RI-2
- **估算**: FE 1.5d / 测试 0.5d

### RI-4 意图识别与跳转 (§5.6.9)

- **业务**: 用户问"我要新增项目" / "我有个投委会要上" → 关键词路由 + LLM 1 次分类兜底
- **影响**: `QaController.intentClassify` + 3 类意图 (a: 新项目 / b: 新议案 / c: 上传材料)
- **验收**:
  - 关键词命中: "新增项目/立项/申请" → 跳新建项目页
  - 关键词命中: "新增议案/投委会要上" → 反问弹窗 (新增/老项目/取消)
  - LLM 兜底 1 次分类
  - 意图 c 走"先定位项目"流程
- **依赖**: RI-1
- **估算**: BE 1d / FE 1d / 测试 0.5d

### RI-5 智能问答 UI/交互 (DeepSeek 风格) (§5.7)

- **业务**: 流式输出 (SSE) + 推理过程显示 (思考流 + 工具调用轨迹) + 停止按钮 + 停止后反馈
- **影响**: 前端 `Knowledge.vue` 重写 + 后端 SSE endpoint
- **验收**:
  - 流式打字机效果
  - 思考流 + 工具调用轨迹同屏显示
  - 停止按钮 < 1s 内中断
  - 停止后保留已有内容 + 灰字"已停止" + "重新生成"按钮
- **依赖**: RI-1
- **估算**: BE 2d / FE 3d / 测试 1d

### RI-6 单项目分析 - 数据模型 (§5.8 P0 部分)

- **业务**: 关键事实档案 - P0 维度 (抵押物 / 担保人 / 处置和解 等 8-12 项 P0)
- **影响表**: `project_fact` (id / project_id / fact_type / fact_value / confidence / evidence_material_id / evidence_snippet / status)
- **验收**:
  - LLM 抽取单项目关键事实入库
  - 置信度 0.6+ 入库 (Mavis 拓展 §13.1.1 改为 3 级)
  - 证据链: 关联材料 + 原文片段
- **依赖**: LLM 配
- **估算**: BE 3d / FE 2d / 测试 1.5d

### RI-7 单项目分析 - 事件流模型 (§5.8 P0+事件)

- **业务**: 关键事实事件流 (32 项 P0/P1/P2), 不可变审计
- **影响表**: `project_fact_event` (id / project_id / fact_type / event_type [INSERT/UPDATE/DELETE] / before / after / actor_id / created_at + Mavis 拓展 §13.1.7 4 字段 owner_id / due_date / resolved_at / resolution_note)
- **验收**:
  - 每次关键事实 INSERT / UPDATE / DELETE 触发事件
  - 事件流不可变 (仅 INSERT, 不可 UPDATE/DELETE)
  - UI 时间线展示
- **依赖**: RI-6
- **估算**: BE 3d / FE 2d / 测试 1.5d

### RI-8 单项目分析 - 32 项维度落地 (§5.8 完整列表)

- **业务**: 32 项单项目分析维度 (P0 + P1 + P2) 全部接入 LLM 抽取
- **影响**: `ExtractionMethod` (LLM prompt 模板) + RI-6 抽取循环
- **验收**:
  - P0 (8-12 项) 必抽
  - P1 (8-10 项) 业务可配启用
  - P2 (10-12 项) 业务可手动触发
  - LLM 失败重试 1 次 (不同 prompt) + 业务降级
- **依赖**: RI-6, RI-7
- **估算**: BE 3d / 测试 1d (LLM prompt 调优)

### RI-9 跨项目聚合 - 数据模型 (§5.9.1)

- **业务**: 5 个批量工具 (按地区 / 行业 / 阶段 / 事实类型 / 时间段 聚合)
- **影响表**: `cross_project_query_log` (id / user_id / filters / result_count / created_at) - 审计 + 性能分析
- **验收**:
  - 5 个工具可调
  - 业务可配启用/停用
  - result 缓存 (5min)
- **依赖**: RI-6, RI-7
- **估算**: BE 2d / FE 1.5d / 测试 1d

### RI-10 跨项目聚合 - 业务口径定义 (§5.9.2)

- **业务**: 业务术语口径 (空债权 / 回收率 / 经验值 等)
- **影响**: `QaController` 批量工具前置校验 + 业务术语字典联动 (RI-11)
- **验收**:
  - "空债权" = 当前没抵押物 (用户已定)
  - "回收率" = 累计收回 / 累计付出
  - "经验上" = 历史平均值
  - "江苏" = project.region
  - Mavis 拓展 §13.1.6 安全白名单 (region/industry/stage/fact_type/time_bucket)
- **依赖**: RI-11
- **估算**: BE 1d / 测试 0.5d

### RI-11 业务术语管理 - 数据模型 (§5.10.1)

- **业务**: 业务术语字典, 7 字段 (name / aliases / category / definition / standard_definition / source_url / data_mapping)
- **影响表**: `business_term` (id / name / aliases / category / definition / standard_definition / source_url / data_mapping / status / created_at / updated_at / english_name (Mavis §13.3.6))
- **验收**:
  - 7 字段 + english_name 共 8 字段
  - CRUD (新增 / 编辑 / 弃用 / 合并 / 搜索)
  - 软弃用 (status=deprecated)
- **依赖**: 无
- **估算**: BE 1.5d / FE 1d / 测试 0.5d

### RI-12 业务术语管理 - 网络查 (§5.10.2)

- **业务**: 用户新增术语时, 调网络 API 候选填充 + 人工采纳
- **影响**: 4 个 API 字典 (Mavis §13.1.5: 百度百科 / 维基百科 / 金融百科 / 互动百科) + 优先级 1→2→3→4
- **影响表**: `web_search_source` (id / name / type / priority / enabled / api_url / api_key) - 见 RI-26
- **验收**:
  - 候选填充 4-5 条
  - 人工采纳入库
  - 都失败 → 提示"网络查无结果, 请手工填"
- **依赖**: RI-11, RI-26
- **估算**: BE 2d / FE 1d / 测试 1d

### RI-13 业务术语管理 - 维护入口 (§5.10.4)

- **业务**: 谁可新建术语 = admin + 普通用户 (默认草稿, 需 admin 确认)
- **影响**: `business_term` 增 status 枚举 (draft / pending_review / active / deprecated)
- **验收**:
  - 普通用户新增 → 草稿
  - admin 审核 → 激活
  - admin 可合并相似术语
- **依赖**: RI-11
- **估算**: BE 0.5d / FE 0.5d / 测试 0.5d

### RI-14 项目创建流程 - 数据模型 (§5.11.1)

- **业务**: 4 阶段 → 3 阶段 (立项+申请合并为"创建"), 投委会编号可配置
- **影响表**: `project` 增 `stage` 枚举 (CREATE / POST_LOAN / SETTLED) + `touweihui_series` (id / code / name / year / current_seq / status) + `proposal` (id / project_id / code / stage [CREATE/POST_LOAN] / decision / 详见 RI-15)
- **验收**:
  - 立项 + 申请合并 = 1 个 stage
  - 投委会系列可配 (默认 tx / xc, 可加)
  - 编号格式: `{series}{2位年号}{3位自增}` (e.g. tx26003)
- **依赖**: §2 业务对象
- **估算**: BE 2d / FE 1.5d / 测试 1d

### RI-15 议案实体 + 决议状态 (§5.11.5)

- **业务**: Proposal 独立于 Material, 1:N 关联 Material
- **影响表**: `proposal` (id / project_id / code / type [立项/申请/其他议案] / decision [通过/暂缓/否决/撤回/附条件通过] / decision_at / decision_actor_id / condition_text / condition_status [pending/met/unmet] / condition_met_at - 详见 Mavis §13.1.3) + `proposal_material` (proposal_id / material_id)
- **验收**:
  - 议案独立 CRUD
  - 决议 5 选 1
  - 已开投委会的议案不能改决议
  - 复议 = 新建一个 proposal
- **依赖**: RI-14
- **估算**: BE 2d / FE 1.5d / 测试 1d

### RI-16 项目创建流程 - 上传优先 + LLM 预填 (§5.11.4)

- **业务**: 用户上传材料 → LLM 抽取关键信息 → 弹出申请表预填 → 用户补充/修改 → 提交
- **影响**: `ProjectController.create` + LLM 抽取 prompt 模板 + 表单预填逻辑
- **验收**:
  - 上传材料后自动触发 LLM 抽 (失败兜底见 Mavis §13.1.9)
  - 表单预填率 80%+ (取决于材料质量)
  - 用户可修改 AI 填错的内容
  - 提交时校验必填字段
- **依赖**: RI-14, RI-15, RI-6
- **估算**: BE 3d / FE 2d / 测试 1.5d

### RI-17 投委会编号生成器 (§5.11.3)

- **业务**: 系统按 series / year / current_seq 自动生成, 申请/议案环节
- **影响**: `TouweihuiSeriesService.next()` (DB 锁 + 事务)
- **验收**:
  - 申请 / 议案: 系统按 series+year+seq 自动生成 (用户选 series)
  - 立项: 系统生成一个立项编号 (非投委会编号)
  - 维护: 议案有编号, 纯维护无议案无编号
  - Mavis §13.1.4 预留 / 撤销 / 改系列 规则
- **依赖**: RI-14
- **估算**: BE 1.5d / FE 0.5d / 测试 1d

### RI-18 维护流程 - 3 类入口 (§5.11.6)

- **业务**: 维护分 3 类 ([上传材料] / [登记议案] / [议案+材料]), 不绑在一起
- **影响**: 项目详情页 3 个独立按钮
- **验收**:
  - [上传材料] → 单纯传材料
  - [登记议案] → 仅记备忘录, 无材料
  - [议案+材料] → 议案必传材料 (有投委会编号)
  - 维护决定系统是否生成投委会编号
- **依赖**: RI-15, RI-17
- **估算**: BE 1d / FE 1.5d / 测试 0.5d

### RI-19 性能修订 (§9.1 + §10.3)

- **业务**: 单项目分析 < 8s, 跨项目聚合 < 30s, 主页加载 < 2s
- **影响**: 后端查询优化 + 缓存 + LLM 异步
- **验收**:
  - 单项目查询 P95 < 8s (含 LLM 1 次往返)
  - 跨项目批量 P95 < 30s
  - 主页 P95 < 2s
  - 慢查询日志 (查询 > 3s 记录)
- **依赖**: RI-6, RI-9
- **估算**: BE 1d / 测试 0.5d

### RI-20 验收标准修订 (§10.3 + §19)

- **业务**: 32 项单项目分析必须可被 LLM 抽到, 跨项目聚合结果可解释
- **影响**: 测试用例 (T-1 ~ T-32)
- **验收**:
  - T-1~T-32 32 个测试用例 (单项目)
  - T-33~T-37 5 个跨项目测试用例
  - T-38~T-40 3 个业务术语测试用例
  - T-41~T-45 5 个项目创建流程测试用例
- **依赖**: RI-6 ~ RI-18
- **估算**: 测试 3d

### RI-21 版本规划 (§12)

- **业务**: v1.1 业务规则细化 / 隐含 / 拓展; v1.2 Plan-and-Execute; v2 多用户多项目
- **影响**: 文档 (本文档就是)
- **验收**:
  - v1.1: 30 项 P0 (含 RI-22~45)
  - v1.2: 5 项 (跨项目 Plan-and-Execute, 通知系统接入邮件/钉钉, 移动端, 多租户, 实时协作)
  - v2: 多用户多项目, 业务方 v2 时再定
- **依赖**: 无
- **估算**: 0d (规划文档)

---\n
## 二、v1.1 增量需求拆解 (Mavis 拓展, RI-22 ~ RI-45)

> 本节是 `REQUIREMENTS.md` §13 v1.1 业务规则细化与补充 的工作底稿.
> 来源: 批 1 (业务规则细化 10 处) + 批 2 (隐含业务规则 6 大类) + 批 3 (业务方没说但应做的功能 8 项).

### RI-22 置信度 3 级体系 (§13.1.1)

- **业务**: §5.8 关键事实抽取时, 阈值细化 0.6 → 3 级 (>=0.85 / 0.6-0.84 / <0.6)
- **影响表**: `project_fact` (status 增 3 枚举 + ui_badge 字段)
- **角色**: admin / 项目经理
- **验收**:
  - 置信度 0.85+ 入库, 无徽章
  - 0.6-0.84 入库, 灰底"AI 推测"徽章
  - <0.6 不入库, 标 pending_review, 红底"待确认"
- **依赖**: 无 (LLM 已返回 confidence)
- **估算**: BE 1d / FE 0.5d / 测试 0.5d

### RI-23 隐式项目切换 5 级判定 (§13.1.2)

- **业务**: §5.6.7 多轮对话中, 用户问题涉及项目时, 自动判别是否切换锁定项目
- **影响**: `QaController` 逻辑 + `conversation.session.locked_project_code` 字段
- **验收**: 5 级判定表完全实现, 反问弹窗 UI
- **依赖**: RI-22 (置信度体系)
- **估算**: BE 1d / FE 1d / 测试 0.5d

### RI-24 决议变更 + 附条件追踪 (§13.1.3)

- **业务**: 投委会开完后决议不可改, 附条件通过的条件状态追踪
- **影响表**: `proposal` 增 `condition_text` / `condition_status` / `condition_met_at` 字段
- **验收**:
  - 已开投委会的议案 resolution API 拒绝 (403)
  - 附条件议案 tab 显示 condition_status (红/黄/绿)
  - 复议流程 = 新建一个 proposal
- **依赖**: 提案实体 (§5.11 已建)
- **估算**: BE 1d / FE 0.5d / 测试 0.5d

### RI-25 投委会编号预留/撤销/改系列 (§13.1.4)

- **业务**: 编号生成有 3 个状态 (draft_reserved / confirmed / revoked)
- **影响表**: `proposal` 增 status 枚举; `touweihui_series` 增 `current_seq` 不回退规则
- **验收**:
  - 草稿保留 24h 超时自动释放
  - 撤销后编号 + .revoked 后缀
  - draft_reserved 可改系列, confirmed 不可
- **依赖**: §5.11.3 编号生成器
- **估算**: BE 1.5d / FE 0.5d / 测试 1d

### RI-26 网络查 API 字典化 (§13.1.5)

- **业务**: 4 个网络查 API 可配 (启用/停用/优先级), admin 配置
- **影响表**: 新增 `web_search_source` (id / name / type / priority / enabled / api_url / api_key)
- **验收**:
  - 优先级 1→2→3→4 依次调
  - admin 参数管理页可配置
  - 都失败 → 提示"网络查无结果, 请手工填"
- **依赖**: §5.10 业务术语 (已有)
- **估算**: BE 2d / FE 0.5d / 测试 1d (含 4 个 API 接入 + key 管理)

### RI-27 批量工具安全白名单 (§13.1.6)

- **业务**: 跨项目批量工具的 filters 字段必须白名单, 防 LLM 注入
- **影响**: `QaController` 跨项目聚合工具前置校验
- **验收**:
  - filters 字段不在白名单 → 400 拒绝
  - 数值字段超限 → 400 拒绝
  - result 行数 > 1000 → 截断 + 提示
- **依赖**: §5.9 跨项目聚合 (已有)
- **估算**: BE 0.5d / 测试 0.5d

### RI-28 关键事实事件流字段扩展 (§13.1.7)

- **业务**: project_fact_event 增 owner / due_date / resolved_at / resolution_note
- **影响表**: `project_fact_event` 增 4 字段 + UI"待处置"视图
- **验收**:
  - 待处置列表 (status != resolved AND due_date < today)
  - 责任人认领
  - 处置完成填 resolution_note
- **依赖**: §5.8 已有
- **估算**: BE 1d / FE 1d / 测试 0.5d

### RI-29 主页双模过渡动画 (§13.1.8)

- **业务**: 待办数 0↔N 时, 主页从"问答"平滑过渡到"待办"列表
- **影响**: `Home.vue` CSS transition + 状态机
- **验收**:
  - 过渡时长 300ms
  - 顶部"问点什么"按钮始终可见
- **依赖**: §5.6.8 主页双模 (已有)
- **估算**: FE 0.5d / 测试 0.5d

### RI-30 LLM 抽失败兜底细化 (§13.1.9)

- **业务**: 5 种失败类型 (API 失败 / 非 JSON / 字段缺失 / 值异常 / 解析失败) 各自兜底
- **影响**: `ProjectController` / `QaController` 错误处理
- **验收**: 5 种失败类型 UI 各异 (banner / 黄字段 / 红字段 / 阻断弹窗)
- **依赖**: §5.11 已有
- **估算**: BE 1d / FE 1d / 测试 1d

### RI-31 软删 + 回收站 (§13.1.10 + §13.3)

- **业务**: Project / Proposal / Material 软删, Fact Event 不可删, BusinessTerm 软弃用
- **影响表**: 4 实体增 `status` / `deleted_at` / `deleted_by` 字段 + `recycle_bin` 视图
- **验收**:
  - 删除 30 天内可恢复
  - 30 天后自动物理删 (Material) / 归档 (其他)
  - 回收站 UI 列已删项目
- **依赖**: 4 实体已建
- **估算**: BE 2d / FE 2d / 测试 1d

### RI-32 撤销/回滚业务规则 (§13.2.1)

- **业务**: 项目 / 议案 / 材料 各自有撤销规则 + 版本历史 + 回滚
- **影响表**: 3 实体增 `version` 字段 + `version_history` 视图
- **验收**:
  - 整项目撤销级联软删所有子项
  - 议案撤销加 .revoked 后缀
  - 每次 UPDATE 留 version_history
  - UI 提供"回滚到此版"
- **依赖**: RI-31 软删
- **估算**: BE 2d / FE 2d / 测试 1d

### RI-33 乐观锁 (§13.2.2)

- **业务**: project / proposal / material 增 version 字段, 防止覆盖写
- **影响表**: 3 实体 `version` INT DEFAULT 1
- **验收**:
  - UPDATE WHERE id=? AND version=?, 影响 0 行 → 提示
  - v1.1 单用户系统可豁免 (配置开关), v2 多用户必启用
- **依赖**: 无
- **估算**: BE 1d / 测试 0.5d

### RI-34 RBAC 5 角色 (§13.2.3)

- **业务**: admin / 项目经理 / 业务部门 / 投委会委员 / 秘书 5 角色
- **影响表**:
  - `role` 字典扩展
  - `user_role` 多对多
  - `project_member` 项目级角色
- **验收**:
  - 5 角色登录后看到不同 UI
  - 委员脱敏视图
  - 决议字段只有委员可改
- **依赖**: 现有 user/role (需扩展)
- **估算**: BE 2d / FE 2d / 测试 1.5d

### RI-35 审计加强 (§13.2.4)

- **业务**: audit_log 增 SENSITIVE_VIEW / EXPORT 类型
- **影响表**: `audit_log` 增 `type` 枚举 + `data_sensitivity` 字段
- **验收**:
  - 委员脱敏查看留痕
  - 数据导出留痕
  - 审计查询 UI (admin)
- **依赖**: 现有 audit_log
- **估算**: BE 1.5d / FE 1d / 测试 0.5d

### RI-36 数据生命周期 (§13.2.5)

- **业务**: 材料 30 天回收站 → 物理删; 项目 1 年归档 → 冷存储; 5 年长期归档
- **影响**: 定时任务 (Spring Scheduled) + `cold_storage_path` 字段
- **验收**:
  - 物理删执行成功 (DB 留痕)
  - 归档文件移到 `archive/` 目录
  - 5 年定时清理规则 (业务方可配)
- **依赖**: RI-31 软删
- **估算**: BE 2d / 测试 1d

### RI-37 失败兜底全景 (§13.2.6)

- **业务**: 10 个关键路径失败兜底集中
- **影响**: `failure_log` 表 (新) + 各 Controller 错误处理
- **验收**: 10 种失败各有日志 + UI 兜底
- **依赖**: RI-30 部分
- **估算**: BE 2d / FE 0.5d / 测试 1d

### RI-38 项目看板 (§13.3.1)

- **业务**: 主页"项目看板"子页面, 表格/卡片/看板 3 视图
- **影响**: 新页面 `Dashboard.vue` + 后端聚合 API
- **验收**:
  - 3 视图切换
  - 7 维度筛选 + 5 维度排序
  - 9 列字段展示
- **依赖**: project / proposal / fact_event / todo 已有
- **估算**: BE 2d / FE 3d / 测试 1d

### RI-39 站内通知中心 (§13.3.2)

- **业务**: 顶部铃铛 + 通知列表, 4 类 (待办/议案/事实/系统)
- **影响表**: `notification` (id / user_id / type / title / content / link / read / created_at)
- **验收**:
  - 未读红点
  - 4 类通知来源
  - 弹窗 / 已读 / 全部已读
- **依赖**: 现有 todo / proposal / fact_event
- **估算**: BE 2d / FE 1.5d / 测试 1d

### RI-40 数据导出 PDF/Excel (§13.3.3)

- **业务**: 项目详情页"导出"按钮, 2 格式
- **影响**: 新依赖 iText / OpenPDF (PDF) + Apache POI (Excel, 已有)
- **验收**:
  - PDF 报告 (项目信息 + 事实 + 议案 + 风险 + 待办)
  - Excel 列表 (材料/事实/议案 3 类)
  - 导出留痕 (RI-35)
- **依赖**: RI-35
- **估算**: BE 3d / 测试 1d

### RI-41 附件预览 (§13.3.4)

- **业务**: 材料详情页内嵌预览 PDF/Word/图片/文本
- **影响**: 前端 pdf.js + mammoth.js (新依赖)
- **验收**:
  - 4 种格式内嵌预览
  - 不支持格式显示下载按钮
  - 章节高亮 (与 LLM 抽取联动, 需 LLM 抽取时记录坐标)
- **依赖**: LLM 抽取需记录 PDF 坐标 (新功能)
- **估算**: FE 2d / 测试 0.5d

### RI-42 关键事实变更对比视图 (§13.3.5)

- **业务**: 事件流 UPDATE 事件弹窗显示 before/after diff
- **影响**: 前端组件 `FactDiffViewer.vue` + JSON diff 库
- **验收**:
  - 左右对比
  - JSON tree 折叠
  - evidence_snippet 原文引用 + 链接
- **依赖**: §5.8 fact_event (已有)
- **估算**: BE 1d (查询 event 详情 API) / FE 2d / 测试 0.5d

### RI-43 业务术语中英对照字段 (§13.3.6)

- **业务**: `business_term` 增 `english_name` 字段
- **影响表**: `business_term` 增字段
- **验收**: 术语详情页显示中/英/别名/定义
- **依赖**: §5.10 业务术语 (已有)
- **估算**: BE 0.5d / FE 0.5d / 测试 0.5d

### RI-44 旧系统 Excel 导入接口 (§13.3.7)

- **业务**: admin 页"数据导入"入口, 4 类 Excel 模板
- **影响**: 新表 `import_template` (id / name / entity_type / file_url) + 导入 API
- **验收**:
  - 4 类 Excel 模板下载
  - 字段必填/类型校验
  - 导入留痕 (RI-35)
- **依赖**: RI-35
- **估算**: BE 2d / FE 1d / 测试 1d

### RI-45 数据脱敏视图 (§13.3.8 + 配合 §13.2.3)

- **业务**: 投委会委员登录后看脱敏视图, 申请脱敏查看留痕
- **影响**: 前端 `*Desensitized.vue` 组件 + 后端脱敏注解
- **验收**:
  - 客户名/金额脱敏
  - 委员申请脱敏查看 (留痕 + 通知 admin)
  - admin 收到通知 (RI-39)
- **依赖**: RI-34 RBAC, RI-39 通知
- **估算**: BE 1.5d / FE 2d / 测试 1d

---

## 三、v1.1 增量优先级 (Mavis 建议)

| 优先级 | RI 编号 | 估时总计 (BE / FE / Test, 人天) |
|---|---|---|
| **P0 必做** | RI-22, 23, 24, 25, 27, 28, 29, 30, 31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 42, 45 | 30d / 28d / 18d |
| **P1 应做** | RI-26, 36, 43, 44 | 6d / 2d / 3d |
| **P2 可选** | (无) | - |

**合计**: BE 36d / FE 30d / Test 21d (约 7-8 人 1 个月)

**关键路径**:
1. RI-22 ~ RI-30 (业务规则细化) → RI-31/32 (软删/回滚) → RI-34 (RBAC) → RI-35 (审计) → RI-38~45 (业务方没说但应做的)
2. RBAC + 审计是 P0 的"卡口", 没做的话其他 P0 验收打折

**风险**:
- RI-26 网络查 4 个 API key 业务方需提前准备
- RI-40 PDF 生成需业务方提供"项目报告模板"
- RI-45 脱敏规则细节需法务确认

---

## 四、v1.1 实现拆解 (RI-46 ~ RI-69, MOD-01~06 收口)

> **编号说明**: RI-22~45 是 v1.0 阶段需求底稿; RI-46~69 是 v1.1 **实现跟踪**续编号 (见 `tasks-v1.1.md`).
> **唯一真相源**: 本段 + `.mavis/plans/draft/v1.1-modules/MOD-*.md` §4 验收.
> **与 RI-22~45 关系**: 业务内容对应, RI-46~69 增加 Given/When/Then 验收 + MOD 映射, **不修改**上文 RI-1~45.

### RI-46: 置信度 3 级体系 (§13.1.1)

- **业务**: 替换 §5.8.3 "0.6 阈值"为 3 级 (≥0.85 CONFIRMED / 0.60–0.84 AI_INFERRED / <0.60 PENDING_REVIEW)
- **影响表**: `project_fact` + `project_fact_event` ALTER `confidence_level` + 回填 SQL
- **角色**: admin / 业务部门 / 投委会委员
- **验收**: confidence=0.90→CONFIRMED; 0.70→AI_INFERRED 徽章; 0.50→PENDING_REVIEW; 历史 fact 回填非 NULL; 集成测例 ≥3
- **依赖**: RI-50, RI-47
- **估算**: BE 0.5d / FE 0.2d / 测试 0.2d
- **对应 §X.Y**: REQUIREMENTS §13.1.1
- **对应 MOD**: MOD-01 (SQL) + MOD-02 (实体) + MOD-03 (prompt)

### RI-47: Agent 隐式项目切换 5 级判定 (§13.1.2)

- **业务**: 替换 §5.6.7.4 "0.95 阈值"为 5 级 (同/不同 projectCode 各 3/2 档)
- **影响表**: 无 DB 改动
- **角色**: admin / 业务部门
- **验收**: SAME_PROBABLY hint; DIFFERENT_PROBABLY 反问; UNCLEAR 保持锁定; MAX_ITERATIONS=5; 集成测例 ≥5
- **依赖**: RI-46
- **估算**: BE 0.5d / FE 0.2d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.2
- **对应 MOD**: MOD-03 (FindProjectTool) + MOD-05 (Knowledge.vue hint)

### RI-48: 决议变更业务规则 (§13.1.3)

- **业务**: 草稿可改 / 已开投委会不可改 (走复议=新议案) / 附条件 `condition_status` 跟踪
- **影响表**: `proposal` ALTER +3 字段
- **角色**: admin / 项目经理 / 投委会委员
- **验收**: OPEN 议案 PATCH decision→403; DRAFT→200; condition MET 自动 todo; 集成测例 ≥4
- **依赖**: RI-58, RI-49
- **估算**: BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.3
- **对应 MOD**: MOD-01 + MOD-02

### RI-49: 投委会编号预留/撤销/改系列 (§13.1.4)

- **业务**: draft_reserved (24h 释放) / revoked (`.revoked` 后缀) / 改系列 (仅 draft_reserved)
- **影响表**: `proposal_series` 新表 + `proposal` ALTER
- **角色**: admin / 项目经理
- **验收**: reserve→200+expiresAt; 24h cron 释放; change-series OPEN→403; 集成测例 ≥4
- **依赖**: RI-48
- **估算**: BE 1d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.4
- **对应 MOD**: MOD-01 + MOD-02

### RI-50: 网络查 API 字典 (§13.1.5)

- **业务**: 4 候选 + 优先级 + 可配; v1.1 只启用百度百科 + 维基 (D-2)
- **影响表**: `dict_type` / `dict_item` 扩行
- **角色**: admin / 业务部门
- **验收**: 命中返回 definition; 内网全失败→`{found:false, reason:INTRANET_BLOCKED}` 不抛异常; 集成测例 ≥3
- **依赖**: —
- **估算**: BE 1d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.5
- **对应 MOD**: MOD-03 (NetworkDictLookupTool)

### RI-51: 跨项目批量工具安全白名单 (§13.1.6)

- **业务**: filters 白名单 (region/industry/stage/fact_type/time_bucket) + 数值上限 + 行数 ≤1000
- **影响表**: 无 (tool 配置)
- **角色**: admin / Agent
- **验收**: 合法 filters 命中; `<script>` reject; 1500 行截断; 集成测例 ≥8
- **依赖**: RI-46
- **估算**: BE 1d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.6
- **对应 MOD**: MOD-03 (QueryMysqlTool 7 重加固)

### RI-52: 关键事实事件流字段细化 (§13.1.7)

- **业务**: `project_fact_event` +4 字段 + DB 触发器白名单
- **影响表**: `project_fact_event` ALTER + 触发器
- **角色**: admin / 投委会委员
- **验收**: PATCH owner_id→200; PATCH fact_value→403; DELETE→403; pending 列表; 集成测例 ≥5
- **依赖**: RI-46
- **估算**: BE 0.7d / FE 0.2d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.7
- **对应 MOD**: MOD-01 + MOD-03 (EntityListener)

### RI-53: 主页双模过渡动画 (§13.1.8)

- **业务**: 待办 0↔N 时 300ms CSS transition
- **影响表**: 无
- **角色**: 全部
- **验收**: 0→1 / N→0 触发 transition; "问点什么"按钮常驻
- **依赖**: —
- **估算**: FE 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.8
- **对应 MOD**: MOD-05 (Home.vue)

### RI-54: LLM 抽字段失败兜底 (§13.1.9)

- **业务**: 5 种 failureType 差异化兜底
- **影响表**: 无
- **角色**: admin
- **验收**: API_ERROR / PARSE_ERROR / FIELD_MISSING / VALUE_INVALID / parse 失败各一条; 集成测例 ≥5
- **依赖**: —
- **估算**: BE 0.5d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.1.9
- **对应 MOD**: MOD-03 + MOD-05 (ProjectForm banner)

### RI-55: 软删 + 回收站 (§13.1.10)

- **业务**: 4 实体软删 + FactEvent 不可删 + 30 天回收站
- **影响表**: 7 表 ALTER `deleted_at/deleted_by`
- **角色**: admin
- **验收**: DELETE→软删; 30 天 cron 物理删; restore→200; DELETE fact_event→403; 集成测例 ≥6
- **依赖**: RI-58
- **估算**: BE 1.5d / FE 0.7d / 测试 0.5d
- **对应 §X.Y**: REQUIREMENTS §13.1.10
- **对应 MOD**: MOD-01 + MOD-02

### RI-56: 撤销/回滚/反悔 (§13.2.1)

- **业务**: 24h 整撤销 / 历史 version 回滚
- **影响表**: 3 实体 `version` (与 RI-57 合并)
- **角色**: admin / 项目经理
- **验收**: <24h DELETE→revoked; rollback version→fact_event UPDATE; 集成测例 ≥4
- **依赖**: RI-58
- **估算**: BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.2.1
- **对应 MOD**: MOD-02

### RI-57: 并发编辑乐观锁 (§13.2.2)

- **业务**: `version` INT + WHERE id=? AND version=?; v1.1 strict=false
- **影响表**: project / proposal / material ALTER
- **角色**: 全部
- **验收**: 并发 PATCH 后 version+1; strict=true→409; strict=false→仅日志; 集成测例 ≥3
- **依赖**: —
- **估算**: BE 0.5d / FE 0.2d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.2.2
- **对应 MOD**: MOD-02 (D-3)

### RI-58: RBAC 5 角色 (§13.2.3)

- **业务**: admin/pm/legal/committee/secretary + `user_role` 多对多 + `project_member`
- **影响表**: `user_role` / `project_member` 新表 + role 扩 4 行
- **角色**: admin
- **验收**: user.role_id 零回归; user_role 优先于 role_id; 集成测例 ≥5
- **依赖**: —
- **估算**: BE 1d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.2.3
- **对应 MOD**: MOD-02 (D-1)

### RI-59: 审计加强 (§13.2.4)

- **业务**: 5 类 audit_log.type (WRITE/LOGIN/SENSITIVE_VIEW/EXPORT/LLM)
- **影响表**: `audit_log` ALTER +2 字段
- **角色**: admin
- **验收**: 5 类事件各留痕; GET 按 type 筛选; 历史回填 WRITE; 集成测例 ≥6
- **依赖**: —
- **估算**: BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.2.4
- **对应 MOD**: MOD-02

### RI-60: 数据生命周期 (§13.2.5)

- **业务**: 软删 30 天→物理删文件; 1 年归档; 5 年长期归档
- **影响表**: `material.archived_at` + `archive.retention.*` 配置
- **角色**: admin
- **验收**: cron 物理删文件保留 DB 行; 集成测例 ≥3
- **依赖**: RI-55
- **估算**: BE 0.5d / 测试 0.2d
- **对应 §X.Y**: REQUIREMENTS §13.2.5
- **对应 MOD**: MOD-02

### RI-61: 失败兜底全景 (§13.2.6)

- **业务**: 10 路径失败→`failure_log` + admin 可查
- **影响表**: `failure_log` 新表
- **角色**: admin
- **验收**: AOP 写 failure_log; GET 筛选; PATCH resolved; 集成测例 ≥4
- **依赖**: —
- **估算**: BE 0.7d / FE 0.3d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.2.6
- **对应 MOD**: MOD-02

### RI-62: 项目看板 (§13.3.1)

- **业务**: 3 视图 + 7 筛选 + 4 排序 + 9 列
- **影响表**: 无 (聚合查询)
- **角色**: 全部
- **验收**: GET board kanban/table; 9 列含议案数/待办数; 集成测例 ≥3
- **依赖**: —
- **估算**: BE 0.5d / FE 0.7d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.3.1
- **对应 MOD**: MOD-04 + MOD-05 (ProjectBoard.vue)

### RI-63: 站内通知中心 (§13.3.2)

- **业务**: 顶栏铃铛 + 4 类来源 + 已读/未读 + 30s 轮询
- **影响表**: `notification` 新表
- **角色**: 全部
- **验收**: 未读列表; mark read; mark-all-read; 4 类来源; 集成测例 ≥5
- **依赖**: —
- **估算**: BE 0.7d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.3.2
- **对应 MOD**: MOD-04 + MOD-05

### RI-64: 数据导出 (§13.3.3)

- **业务**: PDF (OpenPDF) + Excel (POI) 4 类列表 + EXPORT 审计
- **影响表**: 无
- **角色**: 全部
- **验收**: PDF 流; Excel 4 类; audit_log.type=EXPORT; 集成测例 ≥4
- **依赖**: RI-59
- **估算**: BE 0.7d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.3.3
- **对应 MOD**: MOD-04 (D-4)

### RI-65: 附件预览 (§13.3.4)

- **业务**: PDF/Word/图片/文本 浏览器内嵌预览
- **影响表**: 无
- **角色**: 全部
- **验收**: preview API; pdfjs/mammoth/img; 集成测例 ≥4
- **依赖**: —
- **估算**: BE 0.5d / FE 0.7d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.3.4
- **对应 MOD**: MOD-04 + MOD-05 (D-5)

### RI-66: 关键事实变更对比 (§13.3.5)

- **业务**: UPDATE 事件 before/after JSON diff
- **影响表**: 无 (派生)
- **角色**: 全部
- **验收**: GET diff API; DiffViewer 渲染; 集成测例 ≥3
- **依赖**: RI-52
- **估算**: BE 0.3d / FE 0.5d / 测试 0.2d
- **对应 §X.Y**: REQUIREMENTS §13.3.5
- **对应 MOD**: MOD-04

### RI-67: 业务术语中英对照 (§13.3.6)

- **业务**: `business_term.english_name` + Agent 英文查询
- **影响表**: `business_term` ALTER
- **角色**: admin
- **验收**: GET terms?q=vacant 命中; network_dict_lookup 英文; 集成测例 ≥2
- **依赖**: RI-50
- **估算**: BE 0.3d / FE 0.2d / 测试 0.2d
- **对应 §X.Y**: REQUIREMENTS §13.3.6
- **对应 MOD**: MOD-01 + MOD-04

### RI-68: 旧系统 Excel 导入 (§13.3.7)

- **业务**: admin 导入入口 + 4 类模板 + 字段校验 + import_error
- **影响表**: `import_batch` / `import_error` 新表
- **角色**: admin
- **验收**: multipart 上传; 校验失败写 error; 唯一冲突 rollback; 集成测例 ≥5
- **依赖**: RI-59
- **估算**: BE 1d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.3.7
- **对应 MOD**: MOD-04

### RI-69: 数据脱敏视图 (§13.3.8)

- **业务**: 委员脱敏 (`张**` / `***万`) + 申请查看留痕 + admin 通知
- **影响表**: `user.sensitive_view_enabled`
- **角色**: 投委会委员 / admin
- **验收**: COMMITTEE masked=true; unmask→SENSITIVE_VIEW 审计; admin 全量; 集成测例 ≥4
- **依赖**: RI-58, RI-59, RI-63
- **估算**: BE 0.5d / FE 0.5d / 测试 0.3d
- **对应 §X.Y**: REQUIREMENTS §13.3.8
- **对应 MOD**: MOD-04 + MOD-02

### RI-70: Archive 本地只读文件 Agent 工具 (§5.6.7 · UP-0611-01)

- **业务**: Agent 在 `D:/archive/files` 与 `D:/archive/parsed` 内只读 list/grep/read；路径 DB 绑定 + PathGuard
- **影响**: `agent/tool/archive/*`、`ArchiveFsTool`、`AgentSystemPrompt`
- **Plan**: [`upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md`](../../upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md)
- **架构**: [`docs/architecture/07-archive-fs-agent-tools.md`](../architecture/07-archive-fs-agent-tools.md)
- **验收**: traversal 拒绝；grep 项目材料成功；audit_log；单测
- **依赖**: RI-1 (Agent 基座)、StorageService 路径约定
- **估算**: BE 2.5d / 测试 0.5d
- **状态**: `未开发`（plan `DRAFT`）

### RI-71: Archive 多模态读取 (扩展 · 占位)

- **业务**: PDF 单页 / 图像 → vision 或 OCR 管道（`ArchiveFsAction` 扩展）
- **影响**: 新 `ReadPdfPageAction` 等；可能依赖外部 API
- **验收**: TBD（另开 plan）
- **依赖**: RI-70
- **估算**: TBD
- **状态**: `未规划`（仅 RI 占位）