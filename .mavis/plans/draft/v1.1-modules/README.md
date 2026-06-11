# v1.1 增量 — 6 大独立模块划分（PM 总览）

> 撰写人：投委会档案项目 PM | 日期：2026-06-11
> 上游：`.mavis/plans/draft/architecture-v1.1-extended.md` + `refactor-and-fix-list.md` + `tasks-v1.1.md`
> 配套：每模块一份独立 spec（`v1.1-modules/MOD-NN-*.md`）
> **原则**：每个模块 = 1 个接手 agent 能独立 hold 的工作包，**只看自己那一份 spec + 模块内涉及文件 + 已有代码**，不需要回到总表看上下文

---

## 决策（PM 拍板 5 项）

| # | 决策 | 拍板 |
|---|---|---|
| D-1 | 5 角色命名 | `admin / pm / legal / committee / secretary`（v1.0 `admin / user` 双轨兼容，详见 MOD-02 §3.1） |
| D-2 | 网络查字典候选范围 | v1.1 实施只配 2 候选（百度百科 + 维基百科），金融百科/互动百科留"已停用"占位（详见 MOD-03 §3.4） |
| D-3 | 乐观锁 v1.1 严格度 | `archive.optimistic-lock.strict=false`（冲突仅记日志，不强制 409，v2 多用户时切 true，详见 MOD-02 §3.5） |
| D-4 | 导出库选型 | OpenPDF 2.0.2 + Apache POI 5.2.5（jar 增量 < 10MB，详见 MOD-04 §3.3） |
| D-5 | 附件预览前端库 | pdfjs-dist ^4.0 + mammoth ^1.7（**纯前端，不引 LibreOffice**，详见 MOD-04 §3.4） |

---

## 6 大模块一览

| 模块 | 名称 | 涉及 RI | 涉及任务 | 工时 | 接手 agent 角色 |
|---|---|---|---|---|---|
| **MOD-01** | 数据库迁移 + 7 表 ALTER | RI-22~45 全部（DB 部分） | T-v1.1-02/03/04 | 1.7d | DBA / 后端 |
| **MOD-02** | 核心域改造（软删/RBAC/乐观锁/审计） | RI-31/55/56/57/58/59/33 | T-v1.1-05/06/11/12/13/14/15/19/18 | ~7d | 后端（业务域） |
| **MOD-03** | Agent 工具改造（5 级 / 7 加固 / 网络字典） | RI-22/23/26/27/46/52 | T-v1.1-07/08/09/10/16/17 | ~5d | Agent 后端 |
| **MOD-04** | 业务功能新增（看板/通知/导出/预览/脱敏/导入） | RI-62/63/64/65/66/68/69 | T-v1.1-20/21/22/23/25/26/27 + FE 30~36 | ~10d | 全栈 |
| **MOD-05** | 前端集成（Knowledge/Dashboard/路由/store） | RI-22/23/29/53/54 | T-v1.1-28/29/37 | ~1.5d | 前端 |
| **MOD-06** | 文档同步 + 集成测试 + 端到端验收 | 全部 RI | T-v1.1-38/39/40/41 | ~3.5d | 测试 + PM |

**总工时**：~28.7 天（沿用 T3 总览）
**并行机会**：MOD-01（1.7d）完工后，**MOD-02/03/04/05 四路同时开工**，每路独立 hold

---

## 模块依赖图

```
MOD-01 (DB 迁移)
    ↓ (1.7d)
    ├─→ MOD-02 (核心域)     ─┐
    ├─→ MOD-03 (Agent 工具) ─┼─→ MOD-06 (文档+集成测试+验收)
    ├─→ MOD-04 (业务新增)   ─┤
    └─→ MOD-05 (前端集成)   ─┘
```

**关键路径**：MOD-01 → MOD-02 → MOD-06 = 1.7 + 7 + 3.5 = **~12.2d**
**最快路径**：MOD-01 → MOD-04 → MOD-06 = 1.7 + 10 + 3.5 = **~15.2d**
**并行路径**：MOD-02/03/04/05 同时 ≈ 10d（最长那条）

---

## 模块文件位置

```
.mavis/plans/draft/v1.1-modules/
├── README.md                       # 本文件
├── MOD-01-db-migrations.md         # 数据库迁移 + 7 表 ALTER
├── MOD-02-core-domain.md           # 核心域改造（软删/RBAC/乐观锁/审计）
├── MOD-03-agent-tools.md           # Agent 工具改造
├── MOD-04-business-features.md     # 业务功能新增（看板/通知/导出/预览/脱敏/导入）
├── MOD-05-frontend-integration.md  # 前端集成
└── MOD-06-docs-test-acceptance.md  # 文档同步 + 集成测试 + 验收
```

---

## 接手 agent 上手指南

1. PM 跟你说"去干 MOD-XX"
2. 你 `cat .mavis/plans/draft/v1.1-modules/MOD-XX-*.md`
3. 看 §0 模块目标 → §1 涉及 RI → §2 涉及文件（独占清单） → §3 设计要点 → §4 验收 → §5 踩坑预警 → §6 接口契约
4. 改 `TASKS.md` v1.1 章节对应节 `状态: 未开发` → `占用-<你的名字>`，10 秒内 push main
5. 按 §4 验收写代码 + 单测
6. 完工：改 `状态` → `已完成` + commit + push

**严禁**：
- ❌ 改 MOD-XX 之外的文件（除非接口契约 §6 明确写明）
- ❌ 改 `REQUIREMENTS.md`（那是需求开发人员的活）
- ❌ 改 `ARCH-DECOMPOSITION.md` RI 拆解（那是架构师 + MOD-06）
- ❌ 直推 `minimax` 分支
- ❌ 一个 commit 改多个任务

---

*本表由 PM 维护。6 份模块 spec 各 1 个接手 agent。*