# MOD-02 代码审查报告 — 核心域改造

> 审查人：Sisyphus | 日期：2026-06-11 | 审查范围：MOD-02（29 文件，1442 行新增）
> 执行人：阿根廷 | 基线 commit：`a849a30`

---

## 0. 总体评价

**质量尚可，但有几个严重安全遗漏。** RBAC 框架和软删逻辑写得不错，但 ProposalController 的 delete 接口忘了加权限控制、User 实体忘了加 `@SQLRestriction`，这两个是 P0。

---

## 1. 🔴 严重问题（必须修）

### 1.1 `ProposalController.delete()` — 无任何授权检查

**文件**：`ProposalController.java:57-61`

```java
@DeleteMapping("/{id}")
public ApiResponse<Void> delete(@PathVariable Long id) {
    proposalService.delete(id);   // ← 任何已认证用户可删除任意议案
    return ApiResponse.ok();
}
```

- `MaterialController.delete()` → `@PreAuthorize("hasAnyRole('ADMIN','PM','LEGAL')")` ✅
- `ProjectController.delete()` → `@PreAuthorize("hasAnyRole('ADMIN','PM')")` ✅
- `ProposalController.delete()` → **无任何限制** ❌

虽然走的是软删（标记 `deleted`），但任何登录用户都能删，这是明显的权限遗漏。

---

### 1.2 `User.java` — 缺少 `@SQLRestriction("deleted_at IS NULL")`

Project、Proposal、Material、BusinessTerm 四个实体都有：

```java
@SQLRestriction("deleted_at IS NULL")
```

但 User 没有。这意味着：
- 默认查询会包含已软删的用户
- 登录接口可能允许已软删用户登录
- 审计日志查 user 可能查到软删用户

---

### 1.3 `I-RI-34-rbac-5-roles.sql` — committee 角色迁移遗漏（MOD-01 遗留，MOD-02 受害）

RBAC 逻辑正确定义了 5 个角色常量（`Role.CODE_COMMITTEE`），`RbacExpressionRoot.isCommittee()` 等便捷方法都写了。但 I-RI-34 迁移只插入了 `legal` 和 `secretary`，漏了 `committee`。

**影响**：`@PreAuthorize("hasAnyRole('ADMIN','COMMITTEE')")` 在增量迁移的库上永远 403。

---

## 2. 🟡 中等问题

### 2.1 `JwtAuthFilter.java:59` — JWT 解析可能 NPE

```java
Long uid = ((Number) claims.get("uid")).longValue();  // claims.get("uid") 为 null 时 NPE
```

如果 JWT 中没有 `uid` 字段，会抛出 NPE，请求直接 500。

---

### 2.2 `BusinessAop.java:31-33` — FailureLogService 被排除在 AOP 外

```java
if (invocation.getThis() instanceof FailureLogService) {
    return invocation.proceed();  // 自身异常不会被拦截
}
```

FailureLogService 的异常不会被 BusinessAop 记录，虽然这是为了避免递归，但其他服务（如 NotificationService、RbacService）的 business 异常同样不会被拦截，覆盖面不够。

---

### 2.3 `MaterialService.delete()` — userId 传 null

```java
recycleBinService.softDeleteMaterial(id, null);  // userId 为什么是 null？
```

审计信息会丢失（`deleted_by` 为 NULL）。对比 `ProjectService.delete()` 正确地传了 `userId`。

---

## 3. 🟢 轻微问题

### 3.1 `ProjectService.rollback()` — 手动修改版本号

```java
p.setVersion(p.getVersion() + 1);
```

JPA `@Version` 由 EntityManager 自动管理，手动修改会导致版本号跳跃。

### 3.2 `User.java` 的 BaseEntity 审计字段可能冲突

User 同时继承了 `BaseEntity`（含 `createdBy/updatedBy`）和有自己的 `createdAt` 字段，`@CreatedBy` / `@LastModifiedBy` 可能覆盖 user 自己的设置。

---

## 4. ✅ 正确的部分

| 模块 | 状态 | 说明 |
|------|------|------|
| 软删 `@SQLRestriction` | ✅ | Project、Proposal、Material、BusinessTerm 正确覆盖 |
| `RecycleBinService` 软删逻辑 | ✅ | 正确设置 `deleted_at`/`deleted_by`/`status='deleted'` |
| 乐观锁 `@Version` | ✅ | 7 个实体正确定义 + `saveWithVersionCheck` 统一处理 |
| `GlobalExceptionHandler` 处理 `OptimisticLockException` | ✅ | 有专门 handler |
| RBAC 5 角色常量 | ✅ | `Role.CODE_*` 常量完整 |
| `RbacExpressionRoot` 便捷方法 | ✅ | `isAdmin/isPm/isLegal/isCommittee/isSecretary` |
| `RbacService` 双轨兼容 | ✅ | `user_role` 优先，`role_id` 兜底 |
| 软删回滚 | ✅ | `rollback()` 正确恢复 `deleted_at=NULL` + 原状态 |
| `ArchiveService` 归档查询 | ✅ | 正确的 `deleted_at IS NOT NULL` 过滤 |
| 回收站恢复 | ✅ | 恢复时清除 `deleted_at` 并恢复原状态 |

---

*审查完。*

*审查人：Sisyphus*
*3 个 P0（ProposalController 权限、User @SQLRestriction、committee 角色迁移），1 个 P1（JWT NPE），其余 OK。*
