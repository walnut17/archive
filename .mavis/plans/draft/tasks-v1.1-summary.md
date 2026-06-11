# v1.1 并行任务表 1 页摘要（PM 过目）

> 配套详细：`.mavis/plans/draft/tasks-v1.1.md`（41 任务 / ~30 天 / 6 程序员并行 ≈ 5 周）
> 上游：T1 架构扩展方案 da17c92 + T2 重构维修清单

---

## 一、总览

| 项 | 数 |
|---|---|
| **总任务数** | **41**（P0: 14 / P1: 24 / P2: 3） |
| **总工时** | **~30 天**（6 程序员并行） |
| **关键路径** | **~4.5 天**（SQL → 实体 → 软删 → 集成测试 → 验收，串行） |
| **并行机会** | **8 组** 5 路同时（详见 T3 §C.2） |
| **基线** | `7aa7bae`（v1.0 末 Sisyphus review 后） |
| **零回归** | v1.0 任何 .java / .vue / .sql / pom 行为不破坏 |

## 二、5 大并行机会（最先可抢的 5 个任务）

1. **T-v1.1-02/03/04** — 3 批 SQL 迁移（11 个新 I-RI-N.sql + init.sql append）— **0.5/0.5/0.7d**
2. **T-v1.1-05/06** — 7 实体 ALTER（Project/Proposal/Material/AuditLog/ProjectFactEvent/BusinessTerm/User）— **0.5/0.5d**
3. **T-v1.1-08** — FindProjectTool 5 级判定（in-tool 不算 ReAct 步数）— **0.5d**
4. **T-v1.1-14** — RBAC 5 角色（user_role 多对多 + project_member 项目级 + 双轨）— **1d**
5. **T-v1.1-21** — 通知中心后端（notification 表 + 4 类来源 + 30s 轮询）— **0.7d**

## 三、关键路径（不能并行的 4 步）

```
T-v1.1-02 (SQL) → T-v1.1-05 (实体) → T-v1.1-11 (软删) → T-v1.1-40 (集成测试) → T-v1.1-41 (验收)
   0.5d              0.5d                 1.5d                  1.5d                  0.5d
合计 ~4.5 天
```

## 四、P0 任务清单（不能漏的 14 项）

| 编号 | 任务 | 工时 | 阻塞 |
|---|---|---|---|
| T-v1.1-01 | README 笔误修（即时） | 0.1d | — |
| T-v1.1-02 | SQL I-RI-22+31（7 表 ALTER） | 0.5d | RI-22/31/33 |
| T-v1.1-03 | SQL I-RI-24+25+34 | 0.5d | RI-24/25/34 |
| T-v1.1-04 | SQL I-RI-28+35+37+39+43+44+45（7 批） | 0.7d | 7 RI |
| T-v1.1-05 | 3 实体 ALTER | 0.5d | RI-31/32/33 |
| T-v1.1-06 | 4 实体 ALTER | 0.5d | RI-28/35/43/45 |
| T-v1.1-07 | QueryMysqlTool 7 重加固 | 1d | RI-27 |
| T-v1.1-08 | FindProjectTool 5 级判定 | 0.5d | RI-23 |
| T-v1.1-09 | AgentSystemPrompt + FewShots 改写 | 0.5d | RI-22/23/43 |
| T-v1.1-10 | NetworkDictLookupTool 新增（工具 7） | 1d | RI-26 |
| T-v1.1-11 | 软删 + 回收站 | 1.5d | RI-31/55 |
| T-v1.1-12 | 决议变更 + 编号预留 | 1.5d | RI-48/49 |
| T-v1.1-13 | 乐观锁 + 撤销回滚 | 1d | RI-56/57 |
| T-v1.1-14 | RBAC 5 角色 | 1d | RI-58 |

**P0 总工时**：~10 天（4 路并行 ≈ 2.5 周）

## 五、跟现有 RI-1~45 的差异点

- **新增 RI-46~69**（24 条 v1.1 增量），文档同步到 `docs/requirements/ARCH-DECOMPOSITION.md`（T-v1.1-38）
- **现有 RI-1~21** 沿用（不改字段、不改验收）
- **现有 RI-22~45** 是 v1.0 阶段在 v1.0 Plan A~G 拆的，v1.1 阶段以 RI-46~69 为主
- **冲突**：`AGENT-FRAMEWORK-DECISION.md §1.2` 标题修（T1 §0 风险 C-1）— 单独立工单，**不**在 T-v1.1-38 内做

## 六、PM 需要拍板的 5 个决策点

1. **5 角色命名**（RI-58）：`admin / pm / legal / committee / secretary`（v1.0 原 `admin / user` 沿用为双轨）
   - 拍板：是/否？如果业务方想用别的名（比如 `manager` 替 `pm`），T-v1.1-03/14 都要改
2. **网络查字典降级范围**（RI-26）：v1.1 实施只配 2 候选（百度+维基），金融/互动留占位
   - 拍板：是/否？业务方后面会补 2 候选 URL，但 v1.1 期不阻塞
3. **乐观锁 v1.1 期严格度**（RI-57）：`archive.optimistic-lock.strict=false`（v1.1 期冲突仅记日志，不强制失败）
   - 拍板：是/否？v2 多用户时切 true
4. **导出格式库选型**（RI-64）：OpenPDF 2.0.2 + Apache POI 5.2.5（**jar 增量 < 10MB**）
   - 拍板：是/否？如要换（如 iText）请提前说，pom 改动会涉及 BE-R-36
5. **附件预览前端库**（RI-65）：pdfjs-dist ^4.0 + mammoth ^1.7（**纯前端**，不引 LibreOffice）
   - 拍板：是/否？Word 预览效果由 mammoth 转 HTML，质量中等

## 七、给接手 agent 的"上手 4 件事"

1. 拉 `TASKS.md` v1.1 章节 + 本摘要 + T1 架构扩展 + T2 重构清单
2. 找 `可并行: ✅` + `未开发` + 匹配技术栈的任务
3. 改 `状态: 未开发` → `占用-<你的名字>`，**10 秒内** `git add TASKS.md && git commit && git push origin main`
4. 按 T3 §B 任务详细 spec 干活，完工后改 `状态` + commit + push

## 八、文件位置

- 详细任务表：`.mavis/plans/draft/tasks-v1.1.md`（41 任务详）
- 1 页摘要（本文件）：`.mavis/plans/draft/tasks-v1.1-summary.md`
- T1 架构扩展：`.mavis/plans/draft/architecture-v1.1-extended.md`（da17c92）
- T2 重构清单：`.mavis/plans/draft/refactor-and-fix-list.md`
- 现有 RI-1~45：`docs/requirements/ARCH-DECOMPOSITION.md`
- 现有任务表：`TASKS.md`（v1.0 阶段，沿用抢占 SOP）

---

*本摘要由 PM 维护。给项目方过目用。详细任务表 T3 §B 已被接手 agent 直接消费。*
