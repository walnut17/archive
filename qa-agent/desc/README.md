# qa-agent 经验沉淀

> **业务背景**：不良资产投资投委会档案管理 — 用 LLM 辅助秘书/委员查项目、材料、议案与业务事实。  
> **服务定位**：Python FastAPI 微服务，与 Java 后端共用 `config.json`、MySQL、材料归档目录。

本目录记录 **提示词设计、工具链路由、API 变更、部署运维** 的可复用经验，供后续迭代与新人接手时查阅。

## 文档索引

| 文档 | 内容 |
|---|---|
| [01-工具链路由.md](./01-工具链路由.md) | 问句分类 → 工具选择、引擎升级/恢复、死循环规避 |
| [02-提示词设计.md](./02-提示词设计.md) | System prompt 结构、Few-shot、引擎提示、版本演进 |
| [03-API与端点.md](./03-API与端点.md) | HTTP 路由、健康检查、热更新、版本对账 |
| [04-部署与验收.md](./04-部署与验收.md) | 开发机→125 推送、重启陷阱、TUI 验收 |
| [05-案例复盘.md](./05-案例复盘.md) | lmz 材料数 / 利率等真实 Case 与根因 |
| [06-后台深度分析框架.md](./06-后台深度分析框架.md) | 异步分析队列、模板扩展、快照存储 |
| [07-安全机制.md](./07-安全机制.md) | 热更新路径沙箱、LLM 脱敏 |

## 核心代码入口

| 模块 | 路径 | 职责 |
|---|---|---|
| 场景路由表 + Few-shot | `app/agent/prompts.py` | LLM 读到的「该怎么选工具」 |
| 引擎升级/恢复 | `app/agent/react_helpers.py` | LLM 选错时强制改道、重复调用时兜底 |
| ReAct 主循环 | `app/agent/engine.py` | 每步调用 `maybe_upgrade_step`、注入 `append_step_hints` |
| 全文检索 | `app/agent/tools/search_fulltext.py` | 材料正文证据（利率/条款） |
| 项目汇总 | `app/agent/tools/get_project_business_data.py` | 汇总字段（材料数等，**无利率正文**） |
| 深度分析快照 | `app/analysis/` + `get_project_analysis` 工具 | 后台 LLM 提取的项目/资产关键信息 |

## 设计原则（一句话）

**Prompt 教 LLM 怎么走；引擎在关键分叉上兜底，不让错误工具链跑到死循环。**
