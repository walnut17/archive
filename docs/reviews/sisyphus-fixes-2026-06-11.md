# Sisyphus Review 采纳与修复记录

> 执行：阿根廷 | 日期：2026-06-11 | 基线：`48abc9d` → fix：`37e5d7a` → 理由扩写：`a29eef9+`

**说明**：每条发现项的 **已改 / 未改 / 部分改** 及 **完整理由**，见各 MOD review 文末 **「阿根廷回应 → 逐条理由」**（署名阿根廷）。本文档为汇总索引。

---

## 已改（附理由摘要）

| 来源 | 问题 | commit | 理由摘要 |
|------|------|--------|----------|
| MOD-01 | `project_fact_event` 缺 `version` | `37e5d7a` | JPA `@Version` 依赖该列；补 CREATE、去 I-RI-31 重复 ADD |
| MOD-01 | 缺 `committee` 角色 | `37e5d7a` | 增量库无此角色 → COMMITTEE 授权永久 403 |
| MOD-02 | `ProposalController.delete` 无权限 | `37e5d7a` | 任意登录用户可软删议案；对齐 reserve/revoke 角色 |
| MOD-02 | `User` 无软删过滤 | `37e5d7a` | 与其它实体 `@SQLRestriction` 策略一致 |
| MOD-02 | JWT `uid` NPE | `37e5d7a` | malformed token 不应 500，应回落 401 |
| MOD-03 | `GlmService` 重复方法 | `8fafce3` | 编译 BLOCKER |
| MOD-04 | Diff/Preview XSS | `37e5d7a` | 存储型 XSS；DOMPurify 消毒 |
| MOD-04 | 通知 open redirect | `37e5d7a` | 限制 SPA 内部路径 |
| MOD-04 | `NotificationController` 授权 | `37e5d7a` | 显式 `isAuthenticated()` |
| MOD-04 | PDF iframe sandbox | `37e5d7a` | 限制 iframe 脚本面 |
| MOD-06 | EXPORT 审计断言 | `48abc9d` | 补强 scenario3 审计链路 |

---

## 未改 / 部分改（附理由摘要）

| 来源 | 问题 | 阿根廷 | 理由摘要 |
|------|------|--------|----------|
| MOD-01 | I-RI-28 NULL 比较 | **未改** | 当前 SQL 已含 NULL 安全写法，审查误报 |
| MOD-01 | 文档计数偏差 | **未改** | spec 口径差异，非 bug |
| MOD-01 | Flyway / SQL CI | **未改** | 认同建议，v2 实施 |
| MOD-02 | BusinessAop 覆盖面 | **未改** | FailureLog 排除为防递归，有意设计 |
| MOD-02 | MaterialService.delete null userId | **未改** | HTTP 路径已传 userId；内部方法无调用 |
| MOD-02 | rollback 手动 version | **未改** | 无 snapshot 表，v2 一并重构 |
| MOD-02 | User vs BaseEntity | **未改** | User 未继承 BaseEntity，审查不符 |
| MOD-03 | UNCLEAR 静默锁定 | **未改** | 产品语义取舍，需单独 RI |
| MOD-03 | URL 拼接 / rate limit | **未改** | 现网白名单可控；P2 网关限流 |
| MOD-03 | 前端弱类型 / 异步 onFailure | **未改** | 同步路径已 extract-preview+重试 |
| MOD-04 | MaskingService unmaskUrl | **未改** | 仅 shouldMask 分支设置，非 bug |
| MOD-04 | PreviewService 材料权限 | **未改** | IDOR 需 RBAC 大改，v2 |
| MOD-04 | markAllRead N+1 | **未改** | 性能优化，量小可接受 |
| MOD-04 | 上传大小显式限制 | **未改** | 默认限制仍生效 |
| MOD-05 | Knowledge 强类型 | **未改** | 非阻塞，可单开 FE 任务 |
| MOD-06 | JDBC 手写建表 | **未改** | Flyway test profile 工作量大 |
| MOD-06 | MockMvc 全量 E2E | **部分改** | 仅 scenario3 审计断言 |
| MOD-06 | Agent 专项单测 | **未改** | 已有冒烟，7-fold 单测 debt |
| MOD-06 | ProjectForm AI 预填 | **部分改** | `8fafce3` 补 extract-preview |

---

## Review 原文（含逐条理由）

- `sisyphus-review-MOD-01-2026-06-11.md` §8
- `sisyphus-review-MOD-02-2026-06-11.md` §5
- `sisyphus-review-MOD-03-2026-06-11.md` §6
- `sisyphus-review-MOD-04-2026-06-11.md` §6
- `sisyphus-review-MOD-05-06-2026-06-11.md` 文末
