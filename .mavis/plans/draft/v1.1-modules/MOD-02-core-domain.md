# MOD-02 — 核心域改造（软删 / RBAC / 乐观锁 / 审计）

> **接手 agent 只需读本文 + `MOD-01` 完工后的 SQL + 现有 `entity/` `controller/` `service/` 即可开工**

---

## §0 模块目标

v1.1 核心业务域改造：4 实体软删 + 回收站 + RBAC 5 角色 + 乐观锁 + 审计加强 + 数据生命周期 + 失败兜底 + 决议变更 + 编号预留/撤销 + 24h 整撤销/历史回滚。

**这是 MOD-01 完工后第一个能开工的核心模块**，其他模块（MOD-03/04）都依赖本模块的实体 ALTER 字段。

---

## §1 涉及 RI

| RI | 改造 | 阻塞关系 |
|---|---|---|
| RI-31（软删 + 回收站） | 4 实体 + 7 表 ALTER + 30 天扫描 | MOD-01 SQL |
| RI-33（乐观锁） | 3 实体 ALTER `version` + GlobalExceptionHandler | MOD-01 SQL |
| RI-34（RBAC 5 角色） | user_role/project_member + 5 角色 @PreAuthorize | MOD-01 SQL |
| RI-35（审计加强） | 5 类事件 + type 字段 + 6 测例 | MOD-01 SQL |
| RI-36（数据生命周期） | 30 天物理删 + 1 年归档 + 5 年长期 | 依赖 RI-31 |
| RI-37（失败兜底） | failure_log + AOP | MOD-01 SQL |
| RI-48（决议变更） | proposal.condition_status 跟踪 | MOD-01 SQL |
| RI-49（编号预留） | proposal_series + reserve/revoke/change-series | MOD-01 SQL |
| RI-55（软删 + 回收站） | 同 RI-31（含 30 天扫描 + 物理删） | RI-31 |
| RI-56（撤销/回滚） | version 回滚 + 24h 整撤销 | RI-33 |
| RI-57（并发编辑） | @Version 乐观锁 | RI-33 |
| RI-58（5 角色） | 同 RI-34 | RI-34 |
| RI-59（审计加强） | 同 RI-35 | RI-35 |
| RI-60（数据生命周期） | 同 RI-36 | RI-36 |
| RI-61（失败兜底） | 同 RI-37 | RI-37 |

---

## §2 涉及文件（独占清单）

**接手 agent 只允许改以下文件**（其他文件由对应模块独占）：

### 2.1 新建（8 个文件）

```
backend/src/main/java/com/archive/
├── common/
│   ├── OptimisticLockException.java       (新, 业务异常封装)
│   └── BusinessAop.java                   (新, AOP 拦截 @Service 抛异常 → 写 failure_log)
├── security/
│   └── RbacExpressionRoot.java            (新, @PreAuthorize 5 角色权限矩阵)
├── service/
│   ├── RecycleBinService.java             (新, RI-31/55)
│   ├── ArchiveService.java                (新, RI-36/60)
│   ├── FailureLogService.java             (新, RI-37/61)
│   ├── ProposalNumberGenerator.java       (重写, RI-49)
│   └── RbacService.java                   (新, RI-34/58)
└── controller/
    ├── RecycleBinController.java          (新, RI-31/55)
    ├── FailureLogController.java          (新, RI-37/61)
    └── AdminUserController.java           (新, RI-34/58)
```

### 2.2 修改（13 个文件，独占）

```
backend/src/main/java/com/archive/
├── entity/
│   ├── Project.java                       (改, RI-31/55 + RI-33/57 + RI-56)
│   ├── Proposal.java                      (改, RI-24/48 + RI-25/49 + RI-31/55 + RI-33/57)
│   ├── Material.java                      (改, RI-31/55 + RI-33/57 + RI-36/60)
│   ├── AuditLog.java                      (改, RI-35/59)
│   ├── ProjectFactEvent.java              (改, RI-28 + RI-22 + @PreUpdate/@PreDelete 拦截)
│   ├── BusinessTerm.java                  (改, RI-43)
│   ├── User.java                          (改, RI-45)
│   └── Role.java                          (改, RI-34/58, 保持兼容)
├── controller/
│   ├── ProjectController.java             (改 DELETE + rollback, RI-31/55 + RI-56)
│   ├── MaterialController.java            (改 DELETE, RI-31/55)
│   └── ProposalController.java            (改 + reserve/revoke/change-series, RI-24/48/49)
├── service/
│   ├── ProjectService.java                (改 UPDATE @Version, RI-33/57)
│   ├── ProposalService.java               (改 @Version + condition/revoke, RI-24/33/48/49)
│   └── MaterialService.java               (改 UPDATE @Version, RI-33/57)
├── security/
│   ├── SecurityConfig.java                (改 @PreAuthorize)
│   └── JwtAuthFilter.java                 (改 userRoles claim)
└── common/
    └── GlobalExceptionHandler.java        (改, 加 OptimisticLockException handler)
```

**总计**：8 新 + 13 改 = 21 个文件

---

## §3 设计要点

### 3.1 RBAC 5 角色双轨（RI-34 关键）

```sql
-- MOD-01 已执行：role 表 6 行
-- 1. admin (既有, 不动)
-- 2. user (既有, 不动) — v1.0 单用户兼容
-- 3. pm (新增) — 项目经理
-- 4. legal (新增) — 业务部门/法务
-- 5. committee (新增) — 投委会委员
-- 6. secretary (新增) — 秘书
```

**双轨策略**：
- v1.0 路径：`user.role_id` 直接指向 `role.id`（admin/user 二选一）— **零回归**
- v1.1 路径：`user_role` 多对多（user_id + role_id）— **主用**
- 优先级：`user_role` > `user.role_id`

```java
// RbacService.hasRole(userId, roleName)
public boolean hasRole(Long userId, String roleName) {
    // 1. 查 user_role 多对多（v1.1 主路径）
    Optional<UserRole> ur = userRoleRepository.findByUserIdAndRoleName(userId, roleName);
    if (ur.isPresent()) return true;
    
    // 2. 兜底查 user.role_id 兼容路径（v1.0）
    Optional<User> user = userRepository.findById(userId);
    if (user.isPresent() && user.get().getRoleId() != null) {
        Optional<Role> role = roleRepository.findById(user.get().getRoleId());
        return role.isPresent() && role.get().getName().equals(roleName);
    }
    return false;
}
```

### 3.2 软删 + 回收站（RI-31/55 关键）

**4 实体**：`Project / Proposal / Material / BusinessTerm`
**1 实体不可删**：`ProjectFactEvent`（DB 触发器 + EntityListener 双保险）

```java
// ProjectService.softDelete(projectId, userId)
@Transactional
public void softDelete(Long projectId, Long userId) {
    Project p = projectRepository.findById(projectId)
        .orElseThrow(() -> new NotFoundException("project not found"));
    p.setStatus("deleted");
    p.setDeletedAt(LocalDateTime.now());
    p.setDeletedBy(userId);
    projectRepository.save(p);
    // audit_log 自动写（SIS-AOP 在 save 后触发）
}

// MaterialController.delete() 改造
@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN','PM','LEGAL')")
public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
    Long userId = getUserId(auth);
    materialService.softDelete(id, userId);
    return ResponseEntity.ok(Map.of("status", "deleted"));
}
```

**回收站扫描**（30 天物理删）：
```java
// RecycleBinService.scanExpired() - @Scheduled(cron = "0 2 * * *")
@Scheduled(cron = "0 2 * * *")
public void scanExpired() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
    List<Project> expired = projectRepository.findByStatusAndDeletedAtBefore("deleted", cutoff);
    for (Project p : expired) {
        // 物理删文件 + parsed_text, 但保留 DB 行（审计）
        fileService.purgeFiles(p.getId());
        p.setStatus("purged");
        projectRepository.save(p);
    }
}
```

### 3.3 乐观锁（RI-33/57 关键）

```java
// entity/Project.java
@Entity
public class Project {
    @Version
    private Integer version;  // 默认 1, UPDATE 自动 +1
    // ... 其他字段
}

// ProjectService.update()
@Transactional
public Project update(Project p) {
    return projectRepository.save(p);
    // JPA 自动: UPDATE project SET ..., version=version+1 WHERE id=? AND version=?
    // 影响 0 行 → 抛 OptimisticLockException
}

// GlobalExceptionHandler.handleOptimisticLock()
@ExceptionHandler(OptimisticLockException.class)
public ResponseEntity<?> handleOptimisticLock(OptimisticLockException e) {
    // D-3 拍板：v1.1 strict=false, 仅记日志, 不强制 409
    if (!optimisticLockStrict) {
        log.warn("Optimistic lock conflict (v1.1 灰度, 仅记日志): {}", e.getMessage());
        // 返回 200 + 提示前端刷新（但实际可能脏读）
        return ResponseEntity.ok(Map.of(
            "warning", "数据已被他人修改，请刷新后重试",
            "version", -1
        ));
    }
    return ResponseEntity.status(409).body(Map.of(
        "error", "数据已被他人修改，请刷新后重试",
        "code", "OPTIMISTIC_LOCK"
    ));
}
```

### 3.4 审计加强（RI-35/59）

```java
// AuditLogService 新增 5 类写入方法
@Service
public class AuditLogService {
    public void logWrite(Long userId, String entityType, String action, Long entityId) { ... }
    public void logLogin(Long userId, String action) { ... }  // LOGIN/LOGOUT
    public void logSensitiveView(Long userId, String entityType, Long entityId, String reason) { ... }
    public void logExport(Long userId, String exportType, Long entityId) { ... }
    public void logToolCall(String toolName, String args, String result, long durationMs) { ... }
    // type 字段自动填：WRITE / LOGIN / SENSITIVE_VIEW / EXPORT / LLM
}
```

**MOD-04** 调用 `logExport / logSensitiveView / logToolCall`，本模块只暴露方法。

### 3.5 决议变更 + 编号预留（RI-24/48/49）

```java
// ProposalController.updateDecision()
@PatchMapping("/{id}/decision")
@PreAuthorize("hasAnyRole('ADMIN','COMMITTEE')")
public ResponseEntity<?> updateDecision(@PathVariable Long id, @RequestBody DecisionDTO dto) {
    Proposal p = proposalRepository.findById(id).orElseThrow();
    
    // RI-24：已开投委会不可改
    if ("OPEN".equals(p.getStatus()) || "CLOSED".equals(p.getStatus())) {
        return ResponseEntity.status(403).body(Map.of(
            "error", "已开投委会，需走复议（新建议案）",
            "code", "PROPOSAL_IMMUTABLE_AFTER_OPEN"
        ));
    }
    
    // RI-24：附条件通过跟踪
    if ("CONDITIONAL_PASS".equals(dto.getMeetingResult())) {
        p.setConditionText(dto.getConditionText());
        p.setConditionStatus("PENDING");
    }
    
    p.setMeetingResult(dto.getMeetingResult());
    proposalRepository.save(p);
    return ResponseEntity.ok(p);
}

// ProposalController.reserve() - 新
@PostMapping("/reserve")
public ResponseEntity<?> reserve(@RequestBody ReserveDTO dto) {
    // RI-49：预留编号 24h 过期
    Proposal p = proposalService.reserve(dto.getSeriesCode(), dto.getProjectId());
    return ResponseEntity.ok(Map.of(
        "proposalCode", p.getCode(),
        "expiresAt", p.getReservedAt().plusHours(24)
    ));
}

// ProposalNumberGenerator 重写
@Service
public class ProposalNumberGenerator {
    // v1.0: 直接生成 code
    // v1.1: reserve/release/change-series/legacyGenerate
    
    public Proposal reserve(String seriesCode, Long projectId) {
        ProposalSeries series = seriesRepo.findByCodeWithLock(seriesCode)
            .orElseThrow(() -> new NotFoundException("series not found"));
        String code = series.getPrefix() + String.format("%03d", series.getCurrentSeq() + 1);
        series.setCurrentSeq(series.getCurrentSeq() + 1);
        seriesRepo.save(series);
        
        Proposal p = new Proposal();
        p.setCode(code);
        p.setProjectId(projectId);
        p.setStatus("DRAFT_RESERVED");
        p.setReservedAt(LocalDateTime.now());
        return proposalRepo.save(p);
    }
    
    public void release(Long proposalId) {
        // 编号加 .revoked 后缀，保留 UNIQUE 约束
        Proposal p = proposalRepo.findById(proposalId).orElseThrow();
        p.setCode(p.getCode() + ".revoked");
        p.setStatus("REVOKED");
        p.setReleasedAt(LocalDateTime.now());
        proposalRepo.save(p);
    }
}
```

### 3.6 24h 整撤销 + 历史回滚（RI-56）

```java
// ProjectController.rollback()
@PostMapping("/{id}/rollback")
public ResponseEntity<?> rollback(@PathVariable Long id, @RequestBody RollbackDTO dto) {
    Project p = projectService.rollback(id, dto.getTargetVersion());
    // 1. 复制 version=3 的快照到当前
    // 2. version+1
    // 3. 写 project_fact_event UPDATE 记录（**不删**历史）
    return ResponseEntity.ok(p);
}

// ProjectService.rollback()
@Transactional
public Project rollback(Long projectId, int targetVersion) {
    Project p = projectRepository.findById(projectId).orElseThrow();
    ProjectSnapshot snap = snapshotRepo.findByProjectIdAndVersion(projectId, targetVersion)
        .orElseThrow(() -> new NotFoundException("version " + targetVersion + " not found"));
    
    // 复制快照到当前
    p.setName(snap.getName());
    p.setAmount(snap.getAmount());
    p.setDescription(snap.getDescription());
    p.setVersion(p.getVersion() + 1);
    
    Project saved = projectRepository.save(p);
    
    // 写 fact_event UPDATE 记录（**INSERT**，不破坏 INSERT-only 约束）
    ProjectFactEvent evt = new ProjectFactEvent();
    evt.setProjectId(projectId);
    evt.setEventType("ROLLBACK");
    evt.setFactValue("回滚到 version=" + targetVersion);
    evt.setCreatedBy(getCurrentUserId());
    evt.setCreatedAt(LocalDateTime.now());
    factEventRepo.save(evt);
    
    return saved;
}
```

### 3.7 失败兜底 + AOP（RI-37/61）

```java
// FailureLogService
@Service
public class FailureLogService {
    public void log(String path, String failureType, String errorMsg, String stackTrace) {
        FailureLog log = new FailureLog();
        log.setPath(path);
        log.setFailureType(failureType);
        log.setErrorMsg(errorMsg);
        log.setStackTrace(stackTrace);
        log.setOccurredAt(LocalDateTime.now());
        log.setResolved(false);
        failureLogRepo.save(log);
    }
}

// BusinessAop - @Around 拦截
@Aspect
@Component
public class BusinessAop {
    @Around("@within(org.springframework.stereotype.Service)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            String path = pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName();
            failureLogService.log(path, t.getClass().getSimpleName(), t.getMessage(), stackTraceToString(t));
            throw t;  // 异常继续抛，让前端能收到
        }
    }
}
```

### 3.8 数据生命周期（RI-36/60）

```java
// ArchiveService - @Scheduled 1 年 / 5 年扫描
@Scheduled(cron = "0 3 1 * *")  // 每月 1 号凌晨 3 点
public void scanLongTerm() {
    LocalDateTime oneYear = LocalDateTime.now().minusYears(1);
    LocalDateTime fiveYear = LocalDateTime.now().minusYears(5);
    
    // 1 年归档：status=archived
    List<Material> yearOld = materialRepo.findByDeletedAtBeforeAndStatus(oneYear, "purged");
    for (Material m : yearOld) m.setStatus("archived");
    
    // 5 年长期：status=long_archived（DB 仍可查）
    List<Material> fiveOld = materialRepo.findByDeletedAtBeforeAndStatus(fiveYear, "archived");
    for (Material m : fiveOld) m.setStatus("long_archived");
    
    materialRepo.saveAll(yearOld);
    materialRepo.saveAll(fiveOld);
}
```

---

## §4 验收

### 4.1 编译验证

```bash
cd /workspace/projects-online
mvn compile -DskipTests -B
# 期望：BUILD SUCCESS, 0 ERROR
```

### 4.2 单元测试（每 RI 至少 N 条）

```bash
mvn test -B \
  -Dtest='RecycleBinServiceTest,RbacServiceTest,AuditLogServiceTest,ProposalServiceTest,OptimisticLockTest,FailureLogServiceTest,ArchiveServiceTest'
# 期望：≥ 30 测例全过
```

### 4.3 关键场景验证（手动 + integration test）

| RI | 场景 | 期望 |
|---|---|---|
| RI-31 | DELETE project | 200 + status='deleted'，DB 仍有行 |
| RI-31 | DELETE project_fact_event | 403 + 触发器 SIGNAL |
| RI-33 | 2 user 同时 PATCH project | 1 成功 1 409（strict=true）或 200 + warning（strict=false） |
| RI-34 | admin 登录（v1.0 路径） | 200（**零回归**） |
| RI-34 | user 加 user_role role='COMMITTEE' | 调委员端点 200 |
| RI-35 | 任意写操作 | audit_log.type='WRITE' |
| RI-37 | @Service 方法抛 RuntimeException | failure_log 写一条 path=xxx |
| RI-48 | PATCH 已开投委会决议 | 403 + "需走复议" |
| RI-49 | reserve 24h 未确认 | @Scheduled 释放（手动跑 trigger） |
| RI-49 | 已 OPEN 议案改系列 | 403 |
| RI-56 | rollback version=3→5 | 200 + project_fact_event 新增 UPDATE/ROLLBACK 记录 |

### 4.4 RBAC 集成测例（≥ 5 条）

```java
@Test
void adminCanDeleteProject() { ... }      // v1.0 兼容
@Test
void committeeCannotDeleteProject() { ... }
@Test
void userRoleMultiAssignment() { ... }    // v1.1 主路径
@Test
void projectMemberOwnerOnly() { ... }     // 项目级
@Test
void dualTrackPriority() { ... }          // user_role > user.role_id
```

### 4.5 完工 checklist

- [ ] 8 新文件全部 commit
- [ ] 13 改文件全部 commit（独占，不冲突）
- [ ] `mvn compile` 0 错
- [ ] `mvn test` ≥ 30 测例过
- [ ] §4.3 关键场景全部通过
- [ ] 改 `TASKS.md` 状态 → `已完成`

---

## §5 踩坑预警

### 5.1 v1.0 `user.role_id` 兼容路径千万不能断

D-1 双轨是项目方 v1.0 拍板的"零回归"。**不要**在 v1.1 删除 `user.role_id` 字段，**不要**把 admin 改成只能走 `user_role`。

### 5.2 乐观锁 strict 开关默认 false

D-3 拍板 v1.1 `strict=false`。如果接任务的人手贱改成 true，会导致 v1.0 单用户系统频繁 409。**写 application.yml 时注释明确标"v1.1 灰度, v2 切 true"**。

### 5.3 @Version 字段必须是 Integer 不能是 int

JPA `@Version` 要求可空对象类型（默认 0/null → 1），用 `int` 会有 NPE 风险。

### 5.4 `condition_status` 默认 `'NONE'` 而不是 `'PENDING'`

历史 proposal 都是非附条件，默认 NONE 不会误导。MOD-01 SQL 已经按此拍板。

### 5.5 软删后现有查询必须兼容

`status` 字段新增 `'deleted'` 枚举，但**现有 query 默认不写 `WHERE status IS NULL OR status != 'deleted'`**。建议**改用 Hibernate `@SQLRestriction`**（v1.1 改造）：entity 加 `@SQLRestriction("status IS NULL OR status != 'deleted'")`，**所有现有 query 自动过滤软删数据**，**零回归**。

```java
@Entity
@Table(name = "project")
@SQLRestriction("status IS NULL OR status != 'deleted'")
public class Project { ... }
```

**注意**：要测所有现有 query（如 `findAll` / `findById` / `findByProjectCode`）在软删后是否正确过滤。

### 5.6 ProposalNumberGenerator 必须支持 `.revoked` 后缀

v1.0 `proposal.code` 字段有 UNIQUE 约束。撤销时加 `.revoked` 后缀可保持 UNIQUE，**不删** UNIQUE 索引。

### 5.7 AOP 拦截 @Service 必须 try-catch Throwable

不能只 catch Exception，否则 Error（如 OutOfMemoryError）会绕过兜底记录。

### 5.8 @Scheduled cron 表达式

`0 2 * * *` = 每天凌晨 2 点（软删 30 天扫描）
`0 3 1 * *` = 每月 1 号凌晨 3 点（归档 1 年 / 5 年）
**别跟** RI-39 通知的 `archive.notification.polling-interval=30s` 混了。

---

## §6 接口契约

### 6.1 给 MOD-03（Agent 工具）

- `project_fact_event` 触发器已生效，EntityListener 可加 `@PreUpdate`/`@PreDelete` 双保险
- `project_fact.confidence_level` 字段可读可写

### 6.2 给 MOD-04（业务功能）

- `AuditLogService.logExport / logSensitiveView / logToolCall` 已暴露，**直接调用**
- `notification` 表 + `NotificationService` 暂未建（MOD-04 自己建），但**业务域抛异常会被 AOP 拦截写 failure_log**
- `failure_log` 表已建，`FailureLogService.log()` 已暴露，**直接调用**
- `RecycleBinService.restore()` 已暴露，MOD-04 可直接调（如果需要 UI 触发恢复）

### 6.3 给 MOD-05（前端集成）

- `Knowledge.vue` 调 `/api/qa/ask` 响应体新增可空 `projectSwitchHint` + `confidenceBadge`（MOD-03 加，本模块不提供）
- `ProjectForm.vue` 提交失败响应体新增 `failureType`（RI-54，MOD-03 提供，本模块不提供）

### 6.4 给 MOD-06（文档/测试）

- 所有 `entity/*` ALTER 字段 + `@SQLRestriction` 注解完整
- 所有 `controller/*` `@PreAuthorize` 注解完整
- 所有 `service/*` `@Transactional` 注解完整
- `application.yml` 新增 `archive.optimistic-lock.strict` + `archive.retention.*` + `archive.audit.*` 配置

---

*本模块由后端业务域 agent 接手。MOD-01 完工后才能开工。*