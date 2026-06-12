# plan-2026-06-11-deploy-pipeline — 生产部署 SOP + 发布流程标准化

> **Case 状态**：`CLOSED` · 归档于 `upgrade_to_settle/done/`

---

## 0. Plan 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | **plan-2026-06-11-deploy-pipeline** |
| **Case 状态** | **`CLOSED`** |
| **标题** | 生产部署 SOP 标准化：迁移脚本治理 + 环境配置 + 发布流程 |
| **优先级** | **P1** |
| **目标版本** | v1.1.x |
| **代码基线** | `main` ≥ `e592ce5` |
| **触发** | C-0611-02/03/04/05/09（已出站 → 本 plan） |
| **架构师** | Sisyphus · 2026-06-11 |

### 完成条件

- [x] `v1.1-DEPLOY-GUIDE.md` 编写完成
- [x] `application-prod.yml` 生产 `ddl-auto: validate`
- [x] `I-RI-39-notification.sql` 源文件 `read`→`is_read` 修复
- [x] §6 Review **APPROVED** + 审查员 **Reviewer(CLOSED)**

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

### 1.2 需求锚点

| 文档 | 章节 | 要点 |
|---|---|---|
| [`DEPLOYMENT.md`](../docs/operations/DEPLOYMENT.md) | §2 | 部署流程标准化 |
| [`ENVIRONMENT-DEPENDENCIES.md`](../docs/operations/ENVIRONMENT-DEPENDENCIES.md) | §3 | 生产配置要求 |

### 1.3 验收标准（产品）

- [ ] 生产 `ddl-auto: validate` 配置生效
- [ ] `I-RI-39-notification.sql` 源文件 `read`→`is_read` 改名完成
- [ ] 升级流程文档可指导新手完成一次完整升级

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

## 3. PM 范围与决策

| 字段 | 内容 |
|---|---|
| **Agent** | （待 PM 拍板） |
| **时间** | |
| **摘要** | |

| 项 | 决策 |
|---|---|
| **做** | ① 修 I-RI-39 源文件；② application.yml 加 `ddl-auto: validate`；③ 写 `v1.1-DEPLOY-GUIDE.md` |
| **不做** | Flyway 迁移工具本次不引入（留 v2） |
| **风险** | `ddl-auto` 从 `update` 改 `validate` 后，启动时如果 schema 不一致会报错退出 |
| **估时** | BE 0.5d · 文档 0.5d · 测试 0.3d |

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

| **Agent** | Sisyphus |
|---|---|
| **时间** | 2026-06-11 |
| **摘要** | 完成全部 4 项：I-RI-39 + init.sql is_read 修复；application-prod.yml 创建；v1.1-DEPLOY-GUIDE.md 编写 |

| 项 | Commit | 说明 | 状态 |
|---|---|---|---|
| I-RI-39 `read`→`is_read` | (当前) | 源文件 + init.sql 同步修复 | `DONE` |
| application-prod.yml | (当前) | `ddl-auto: validate` + 注释 | `DONE` |
| v1.1-DEPLOY-GUIDE.md | (当前) | 升级步骤 + 验收 checklist + 回滚方案 | `DONE` |

---

## 6. 评审（Reviewer Agent）

| Agent | 时间 | 结论 |
|---|---|---|
| 投委会档案项目PM | 2026-06-11 23:59 | `APPROVED` |

### 6.1 意见清单

无问题。

### 6.2 总评

- I-RI-39 `read`→`is_read` 源文件 + `init.sql` 同步修，避免双轨
- `application-prod.yml` `ddl-auto: validate` 是生产标准做法
- `docs/v1.1-DEPLOY-GUIDE.md`（root）覆盖 v1.0/v2 → v1.1 升级路径完整

### 6.3 建议（非阻塞）

- `docs/v1.1-DEPLOY-GUIDE.md`（root，190 行） 与 `docs/handoff/v1.1-DEPLOY-GUIDE.md`（PM 之前写的） 同名不同内容；建议 v1.2 收一收，按场景分文件名（如 `v1.1-upgrade-guide.md` vs `v1.1-deploy-guide.md`）

---

## 7. 验收

| Agent/Operator | 时间 | 结论 |
|---|---|---|
| 投委会档案项目PM | 2026-06-12 | 文档/SOP 交付验收通过（125 联测非本 case 阻塞项） |

---

## Agent Blocks

> 旧 §5～§7 表只读；关单留痕见下。

----- agent-block begin -----
role: Reviewer
agent: 投委会档案项目PM
time: 2026-06-11 23:59
ref: plan-2026-06-11-deploy-pipeline
verdict: APPROVED
summary: I-RI-39 + application-prod.yml + v1.1-DEPLOY-GUIDE 交付完整

- 源文件与 init.sql `is_read` 同步
- 生产 `ddl-auto: validate` 配置到位
- 升级指南覆盖 v1.0/v2 → v1.1 路径

----- agent-block end -----

----- agent-block begin -----
role: Reviewer
agent: Auto（代审查员；**后续关单由审查员按 CODE-REVIEWER.md 执行**）
time: 2026-06-12 12:00
ref: case
verdict: CLOSED
archive: upgrade_to_settle/done/plan-2026-06-11-deploy-pipeline.md
summary: plan-2026-06-11-deploy-pipeline 目的达成，case 关闭

----- agent-block end -----
