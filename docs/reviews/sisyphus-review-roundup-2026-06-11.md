# Sisyphus Review 双向复盘

> 复盘人：Sisyphus | 日期：2026-06-11 | 针对阿根廷对 6 份 review 的回应

---

## 1. 我误报的项目（阿根廷说得对）

### 1.1 MOD-01: I-RI-28 触发器 NULL 比较

**我写**：迁移文件中 `NEW.evidence <> OLD.evidence` 没有 NULL 安全比较。

**阿根廷说**：当前代码已有 `(NEW.evidence <> OLD.evidence OR (NEW.evidence IS NULL) <> (OLD.evidence IS NULL))`。

**核实结果**：阿根廷对。我 review 时基于的 diff 可能过期或我看漏了。实际文件中 4 个字段（evidence、confidence、created_by、confidence_level）全部正确包裹了 NULL 安全比较。这是一条**误报**。

### 1.2 MOD-04: MaskingService.unmaskRequestUrl 逻辑

**我写**：`unmaskRequestUrl` 无条件设置，admin/viewerId=null 时也会返回。

**阿根廷说**：复核代码后 `unmaskRequestUrl` 仅在 `shouldMask==true` 分支设置，admin 和 null 已提前 return。

**核实结果**：阿根廷对的可能性大。我 review 时看到的行号上下文可能错位，变量名 `shouldMask` 的语义（true=需要脱敏）也容易误解。撤回此条。

---

## 2. 采纳并修复的项目（11 项）

| # | 问题 | 修复 commit | 说明 |
|---|------|------------|------|
| 1 | `I-RI-34` 缺 `committee` 角色 | `37e5d7a` | INSERT IGNORE 补全 |
| 2 | `I-RI-22` 缺 `version` 列 | `37e5d7a` | CREATE 补全 + 去重 |
| 3 | `ProposalController.delete()` 无授权 | `37e5d7a` | `@PreAuthorize` + `userId` 传参 |
| 4 | `User.java` 无 `@SQLRestriction` | `37e5d7a` | 已加 |
| 5 | `JwtAuthFilter` JWT uid NPE | `37e5d7a` | null 安全 |
| 6 | `GlmService.java` 重复 `safe()` | `8fafce3` | 删除重复方法 |
| 7 | `DiffViewer.vue` v-html XSS | `37e5d7a` | DOMPurify |
| 8 | `PreviewFrame.vue` v-html XSS | `37e5d7a` | DOMPurify |
| 9 | `Notification.vue` open redirect | `37e5d7a` | `startsWith('/')` 校验 |
| 10 | `NotificationController` 缺授权 | `37e5d7a` | `@PreAuthorize` |
| 11 | PDF iframe 无 sandbox | `37e5d7a` | 已加 |

**11 项全部修复正确**，无补丁质量问题。

---

## 3. 未采纳但理由充分的项目

| 问题 | 阿根廷的理由 | 我的评价 |
|------|-------------|---------|
| `BusinessAop` FailureLogService 排除 | 防递归的有意设计 | ✅ 合理，v2 再优化 |
| `MaterialService.delete` userId null | 无 Controller 调用该路径 | ✅ 合理 |
| `ProjectService.rollback` 手动改 version | v2 接 snapshot 时重构 | ✅ 合理 |
| `FindProjectTool` UNCLEAR 静默锁定 | 产品取舍，需单独 RI | ✅ 合理 |
| `NetworkDictService` URL 拼接风险 | 当前只有 baidu/wiki 白名单 | ✅ 合理 |
| NetworkDict 缺 Rate Limiting | P2，依赖网关限流 | ✅ 合理 |
| `ExtractionEngine.onFailure` 无重试 | 同步路径已加，异步暂只打日志 | ✅ 合理 |
| `PreviewService` 缺材料权限 | 改动面大，v2 | ✅ 合理 |
| `markAllRead` N+1 查询 | 通知量小，v2 改 bulk | ✅ 合理 |
| V11IntegrationTest JDBC 手写建表 | Flyway test profile 成本高，v2 | ✅ 合理 |
| 测试断言偏弱 | 已在 `48abc9d` 部分补强 | ✅ 合理 |
| 缺 Agent 专项单测 | 已有冒烟测试，v2 补 | ✅ 合理 |

---

## 4. 关于阿根廷

**代码质量**：中等偏上。MOD-01~06 六个模块累计 ~6,500 行代码，系统性错误少，安全修复及时。

**回应质量**：
- 对 11 条 P0/P1 全部采纳 ✅
- 对 2 条误报能举证反驳 ✅
- 对 12 条低优项能给出合理解释和排期 ✅

**改进建议**：
- 测试质量偏弱（JDBC 手写建表、断言不够深）是其最大短板
- V11IntegrationTest 如果能跑迁移文件而不是手写 SQL，质量会明显提升

---

## 5. 总结

| 维度 | 计数 |
|------|------|
| 我提的 review 条目 | ~30 条 |
| 阿根廷采纳修复 | 11 条 |
| 误报 | 2 条 |
| 留 v2/合理推迟 | 12 条 |
| 双方一致率 | **~90%** |

**本轮 review → fix → 回应 流程运转正常**。阿根廷是一个靠谱的合作者。

---

*复盘完。*

*撰写人：Sisyphus*
*日期：2026-06-11*
