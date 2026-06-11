# Plan UP-0611-03 — 生产部署 SOP + 发布流程标准化

> **状态**：`DRAFT`（待 Implement 认领）
> **活跃目录**：`upgrade_to_settle/` · 完工后 → `done/`

---

## 0. Plan 元信息

| 字段 | 内容 |
|---|---|
| **Plan ID** | **UP-0611-03** |
| **标题** | 生产部署 SOP 标准化：迁移脚本治理 + 环境配置 + 发布流程 |
| **状态** | `DRAFT` |
| **优先级** | **P1** |
| **目标版本** | v1.1.x |
| **代码基线** | `main` ≥ `e592ce5` |
| **触发** | C-0611-02/03/04/05/09 |
| **架构师** | Sisyphus · 2026-06-11 |

### 完成条件

- [ ] `v1.1-DEPLOY-GUIDE.md` 编写完成
- [ ] `DEPLOYMENT.md` / `ENVIRONMENT-DEPENDENCIES.md` 同步更新
- [ ] `I-RI-39-notification.sql` 源文件 `read`→`is_read` 修复
- [ ] 生产 `application.yml` 模板配置 `ddl-auto: validate`

---

## 1. 需求追溯

| 字段 | 内容 |
|---|---|
| **Agent** | Sisyphus |
| **时间** | 2026-06-11 |
| **摘要** | 将本轮部署测试暴露的流程问题固化到 SOP 和配置 |

### 1.1 来源问题

| C-ID | 来源 | 问题 | 处理 |
|------|------|------|------|
| C-0611-02 | T-0611-07 | 生产升级强制 backup + 单一 migrate 脚本 | 写入 DEPLOY-GUIDE |
| C-0611-03 | A-2 | `ddl-auto: update` 与手工 migration 并存 | 生产改 `validate` |
| C-0611-04 | T-0611-06 | 验收环境 5173 vs build+Caddy | 两阶段 checklist |
| C-0611-05 | T-0611-03 | I-RI-39 `read`→`is_read` 源文件修正 | 修 I-RI-39 |
| C-0611-09 | T-0611-05 | healthcheck 不带 JWT vs 浏览器 | 文档 gap |

---

## 2. 架构追溯

### 2.1 改动范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `backend/src/main/resources/db/migration/I-RI-39-notification.sql` | 改 | `read` → `is_read`（与 Entity 对齐） |
| `backend/src/main/resources/application.yml` | 改 | 生产 profile 加 `ddl-auto: validate` |
| `deploy/sql/migrate_260611_01.sql` | 改 | 同步 `read`→`is_read` 改名 |
| `deploy/README.md` | 改 | 补充升级步骤 |
| `docs/ENVIRONMENT-DEPENDENCIES.md` | 改 | 生产配置说明 |

### 2.2 新增文件

| 文件 | 说明 |
|------|------|
| `docs/v1.1-DEPLOY-GUIDE.md` | v1.1 新增部署指南 |

### 2.3 关键设计

**生产 application.yml 模板**：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 生产禁止 auto DDL
    show-sql: false
```

**升级流程**：
```
1. backup DB: mysqldump archive_db > backup_{date}.sql
2. git pull origin main
3. source migrate_260611_01.sql
4. mvn clean package -DskipTests
5. 停旧服务 → 复制新 jar → 启新服务
6. 访问 health + 带 token 冒烟
```

---

## 3. PM 范围

| 字段 | 内容 |
|---|---|
| **Agent** | （待 PM 拍板） |
| **时间** | |

### 3.1 待 PM 拍板

- 升级流程是否要求**强制 backup** 还是允许跳过的宽松策略
- `v1.1-DEPLOY-GUIDE.md` 的维护归属

---

## 4. 开发说明

### 4.1 I-RI-39 源文件修复

```diff
--- a/I-RI-39-notification.sql
+++ b/I-RI-39-notification.sql
-  `read` TINYINT(1) NOT NULL DEFAULT 0,
+  `is_read` TINYINT(1) NOT NULL DEFAULT 0,
...
-  INDEX idx_user_read (user_id, `read`, created_at)
+  INDEX idx_user_read (user_id, is_read, created_at)
```

同时同步到 `init.sql` 和 `migrate_260611_01.sql`。

### 4.2 application.yml 生产 profile

在 `application.yml` 末尾或新增 `application-prod.yml`：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

### 4.3 验收

```bash
# startup.ps1 执行后验证
mysql -u root archive_db -e "DESC notification;"
# 期望: is_read TINYINT(1) (not `read`)

curl http://localhost:8080/api/health
# 期望: {"status":"UP"}
```

---

## 5. Implement

（Implementer 填写）

---

## 6. Review

| Agent | 时间 | 结论 |
|---|---|---|
| | | |

---

## 7. 验收

| Agent/Operator | 时间 | 结论 |
|---|---|---|
| | | |
