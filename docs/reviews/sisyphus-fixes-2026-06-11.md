# Sisyphus Review 采纳与修复记录

> 执行：阿根廷 | 日期：2026-06-11 | 基线：`48abc9d`

## 采纳并修复

| 来源 | 问题 | 处理 |
|------|------|------|
| MOD-01 | `I-RI-34` 缺 `committee` 角色 | ✅ INSERT IGNORE 补全 |
| MOD-01 | `project_fact_event` 缺 `version` | ✅ `I-RI-22` CREATE 加 version；`I-RI-31` 去重 |
| MOD-02 | `ProposalController.delete` 无权限 | ✅ `@PreAuthorize` + `softDelete(userId)` |
| MOD-02 | `User` 无 `@SQLRestriction` | ✅ 已加 |
| MOD-02 | JWT `uid` NPE | ✅ null 安全校验 |
| MOD-03 | `GlmService` 重复 `safe()` | ✅ 已在 `8fafce3` 修复 |
| MOD-04 | `DiffViewer` / `PreviewFrame` v-html XSS | ✅ DOMPurify.sanitize |
| MOD-04 | `Notification.vue` open redirect | ✅ 仅允许 `/` 开头内部路径 |
| MOD-04 | `NotificationController` 缺授权 | ✅ `@PreAuthorize("isAuthenticated()")` |
| MOD-04 | PDF iframe 无 sandbox | ✅ `sandbox=""` |

## 未采纳 / 已有对策

| 来源 | 问题 | 说明 |
|------|------|------|
| MOD-01 | `I-RI-28` 触发器 NULL 比较 | 当前迁移文件已含 NULL 安全比较 |
| MOD-04 | `MaskingService.unmaskRequestUrl` | 仅在 `shouldMask` 分支设置，非无条件 |
| MOD-05/06 | 测试用手写 JDBC 建表 | 改动面大，留 v2 引入 Flyway 测试 profile |
| MOD-03 | NetworkDict rate limit | P2，生产需 API key + 网关限流 |

## Review 原文

- `sisyphus-review-MOD-01-2026-06-11.md` … MOD-05-06
