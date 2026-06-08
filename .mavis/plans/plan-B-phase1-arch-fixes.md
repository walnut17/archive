# Plan B: Phase 1 — 架构缺陷修复

> **状态**: 准备启动(等 Plan A 完成)
> **优先级**: 🟡 P1(架构性,影响后续扩展)
> **工作量**: 2-3 个 commit,1-2 小时
> **依赖**: Plan A 完成(尤其是 P0-4 AuditorAware)
> **互斥**: 不与 A 并行,可与 C/D 并行做不同模块

## 必读文档(启动前)

1. `docs/REQUIREMENTS-v1.md`
2. `docs/ARCHITECTURE-v2.md` § Provider 层 / Engine 层
3. `docs/DB-SCHEMA-v2.md` § 新增表
4. `docs/DEV-STANDARDS.md`
5. `docs/TEAM-ARCHIVE.md`
6. `investment-committee-archive-system/SUPPLEMENTARY-REQUIREMENTS.md` § Phase 1

## 范围(4 个子项)

### B-1. P1-1 GlobalExceptionHandler 加 NoSuchElementException 处理器

**目标**: `findById().orElseThrow(...)` 抛 `NoSuchElementException` 时返回 404,不是 500

**改动**:
- `backend/src/main/java/com/archive/common/GlobalExceptionHandler.java`:
  ```java
  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException e, HttpServletRequest req) {
      log.info("资源不存在 @ {}: {}", req.getRequestURI(), e.getMessage());
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(40400, e.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Void>> handleState(IllegalStateException e, HttpServletRequest req) {
      log.info("状态不允许 @ {}: {}", req.getRequestURI(), e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(40001, e.getMessage()));
  }
  ```

**验收**: `curl http://localhost:8080/api/projects/99999` 返回 404,不是 500。

### B-2. P1-2 Service 层异常类型统一

**目标**: 全 Service 改用统一异常类型
- "资源不存在" → `NoSuchElementException`(会映射 404)
- "状态不允许" → `IllegalStateException`(会映射 400)
- "参数无效" → `IllegalArgumentException`(已映射 400)

**改动**:
- 扫所有 `*Service.java`,找 `.orElseThrow(() -> new XxxException(...))`
- 替换为对应类型
- 涉及文件:`AuthService` / `ProjectService` / `ProposalService` / `MaterialService` / `MaterialVersionService` / `TodoService` / `DictService` / `TriggerService`

**验收**: `grep -rn "throw new " backend/src/main/java/com/archive/service/ | grep -v NoSuchElement | grep -v IllegalArgument | grep -v IllegalState` → 空。

### B-3. P1-3 Caddyfile 增加 HTTP 限流

**目标**: 80 端口也加 rate limit(不只是 443)

**改动**:
- `deploy/caddy/Caddyfile`:
  - `:80` 块加 `rate_limit @bottlenot 600r/m`(同 443)
  - 同步加 `@bottlenot` 匹配规则(可能本来就有)

**验收**: `curl http://localhost` 频繁请求(> 600/分)返回 429。

### B-4. P1-4 config.example.json 补充 jwt.secret

**目标**: 用户照模板配置时知道要配 jwt.secret

**改动**:
- `config/config.example.json`:
  - 加 `jwt` 段,含 `secret` 和 `expirationSeconds`
  - 注释说明生成方法 `openssl rand -base64 32`

**验收**: 新人拿到 config.example.json,知道要填 `jwt.secret` 才能用。

## 提交规范

每个子项一个 commit:
```
feat(backend,B-1): GlobalExceptionHandler 加 NoSuchElement + IllegalState 映射
refactor(backend,B-2): 统一 Service 异常类型
chore(deploy,B-3): Caddyfile :80 加 rate limit
docs(config,B-4): config.example.json 补充 jwt 配置说明
```

## 自测

每个子项完工后:
1. `mvn compile -DskipTests -B -o`
2. (B-1/B-2) `curl` 测 404 场景
3. (B-3) `caddy validate` 检查配置
4. (B-4) 人工 review

## 交回物

完工后向 owner 交:
1. ✅ 4 个 commit + push 链接
2. ✅ `mvn compile` 通过
3. ✅ `curl` 测试输出
4. ✅ Caddyfile 校验通过

## 不在本 plan 范围

- ❌ 任何业务功能(P2+ 都在 C/D)
- ❌ 任何 UI 改动

## 风险/注意

- ⚠️ B-1 改 ExceptionHandler:**测所有 Service** 的 orElseThrow 路径,避免漏改
- ⚠️ B-3 Caddyfile: 生产 Caddy 跑前必须 `caddy validate` 过
