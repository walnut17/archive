# 部署日志 — deployment_log

> 按**真实操作时间线**记录，方便下次 session 快速对齐。  
> 老文档 `docs/DEPLOYMENT-LOG.md` 保留 2026-06-07 以前 M0 阶段记录；**本文件从 v1.1 (0611) 起记**。

---

## 索引

| 版本 | 日期 | 服务器 | 指挥/执行 | 状态 |
|---|---|---|---|---|
| **v1.1 / 0611** | 2026-06-11 | `182.168.1.125` | 阿根廷 (AI 指挥) + 用户 (125 上操作) | 🟡 DB 迁移 ✅ — 待浏览器验证 + 7 场景验收 |

---

# v1.1 部署 — 2026-06-11 (0611)

## 0. 背景与目标

| 项 | 说明 |
|---|---|
| **版本** | v1.1 业务增量（MOD-01~06 已完工，mvn 验证 T-v1.1-42 已通过） |
| **生产机** | `182.168.1.125`，Windows，代码目录 `D:\projects-online` |
| **部署方式** | 用户按阿根廷一步步操作；**验收期暂不用 Caddy 正式托管**，用 `npm run dev` + `:5173` |
| **数据库** | 已有 v1.0/v2 库 `archive_db`，需 **增量迁移**（不能跑 `init.sql`） |
| **相关 commit** | 见下文「Git 时间线」 |

### 相关文档

- 官方指引：`docs/handoff/v1.1-DEPLOY-GUIDE.md`
- 一次性 SQL：`deploy/sql/migrate_260611_01.sql`（**推荐**）
- mvn 验证报告：根目录 `VERIFICATION-REPORT.md`
- 老部署记录：`docs/DEPLOYMENT-LOG.md`（2026-06-07 前）

---

## 1. Git 时间线（与本部署相关）

| Commit | 说明 |
|---|---|
| `2ae2b62` | 阿根廷：mvn 验证报告 + H2 测试兼容修复 |
| `1ca9099` | Mavis：`v1.1-DEPLOY-GUIDE.md` |
| `59f25f7` | 阿根廷：首版 `migrate_260611_01.sql`（14 个 I-RI 合并） |
| `5551b74` | 阿根廷：**完整版** migrate — 含 v2/Plan I 旧表 ALTER + 幂等列检测 |

用户 125 上首次 pull 到约 `90b8616`；迁移脚本以 **`5551b74` 及之后** 为准。

---

## 2. 环境确认（对话中已对齐）

| 工具 | 125 生产机 | 阿根廷本机（验证用） |
|---|---|---|
| JDK | Temurin 17（PATH 需刷新） | 17.0.19 |
| Maven | 3.8.8 | 3.8.8 |
| MySQL | 8.0，`archive_db` + `archive_app` | — |
| Node | 有（验收 `npm run dev`） | — |
| Caddy | 文档说有，**本次验收未启用** | — |

**PATH 刷新（PowerShell，125 上 mvn 找不到时）：**

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User')
```

---

## 3. 操作时间线（按步骤）

### Step 1 — 拉代码 ✅

```powershell
cd D:\projects-online
git pull origin main
```

**问题：** `frontend/package-lock.json` 本地有改动，pull 被拒绝。

**处理：**

```powershell
git checkout -- frontend/package-lock.json
git pull origin main
```

**说明：** 生产机上的 `package-lock.json` 本地漂移可丢弃，以远程 `main` 为准。

---

### Step 2 — 编译后端 ✅（用户已完成）

```powershell
cd D:\projects-online\backend
mvn clean package -DskipTests -B
```

**产物：** `backend\target\archive.jar`

---

### Step 3 — 启动后端 jar ✅（用户已完成）

用户手动启动 jar，日志正常：

```text
Tomcat started on port 8080
Started ArchiveApplication in ~35s
```

**配置：** 生产用 `D:\archive\config\config.json`（`CONFIG_JSON_PATH`）；日志 `D:\archive\logs\backend.log`。

---

### Step 4 — healthcheck ✅

```powershell
cd D:\projects-online\backend
.\healthcheck.ps1
```

**结果：** `M0 Backend PASSED!` — `/api/health` UP + `admin/admin123` 登录返回 token。

**注意：** healthcheck **不带 JWT**；与浏览器登录后行为不同（见 Step 6）。

---

### Step 5 — 前端验收模式（进行中）

**决策（用户 + 阿根廷）：** 先验收，**暂不** `npm run build` + Caddy 托管 `dist`。

```powershell
cd D:\projects-online\frontend
npm ci
npm run dev
```

**访问：** `http://182.168.1.125:5173`（或 RDP 本机 `http://localhost:5173`）  
**登录：** `admin` / `admin123`

#### 架构备忘（对话中解释过）

| 组件 | 作用 | 本次是否用 |
|---|---|---|
| **npm run dev** | Vite 开发服务器 `:5173`，`/api` 代理到 `:8080` | ✅ 验收用 |
| **npm run build → dist/** | 静态网页文件（HTML/JS/CSS，非 exe） | ⏸ 正式对外再用 |
| **Caddy** | Web 服务器/反代，`:80` 统一入口，托管 dist + 反代 API | ⏸ 验收不用 |
| **archive.jar** | Spring Boot 后端 `:8080` | ✅ 已跑 |

---

### Step 6 — 浏览器 500 报错 🔴 → 根因已定位

**现象：** 登录后控制台大量 500：

- `/api/health`
- `/api/notifications?unread=true`
- `/api/todos?...`

**矛盾：** healthcheck 直连 `:8080` 通过，浏览器经 `:5173` 代理失败。

**根因分析：**

1. 浏览器登录后，`http.ts` 给**所有请求**（含 `/api/health`）带上 `Authorization: Bearer <token>`。
2. `JwtAuthFilter` → `RbacService.getRoleCodes()` 查 **`user_role`** 表；表不存在或缺列 → 异常。
3. v1.1 `BusinessAop` 写 **`failure_log`**；表不存在 → 二次异常 → **500**。
4. `notification` 表缺或列名 `` `read` `` vs Java 实体 **`is_read`** 不一致 → 通知接口 500。

**backend.log 关键行：**

```text
Caused by: java.sql.SQLSyntaxErrorException: Table 'archive_db.failure_log' doesn't exist
```

---

### Step 7 — 数据库 v1.1 迁移 ✅（2026-06-11 用户确认）

**执行结果：** MySQL 客户端显示 **112 queries 全部成功**。

**文件：** `deploy/sql/migrate_260611_01.sql`（commit `5551b74` 完整版）

**125 上已执行：**

```powershell
cd D:\projects-online
git pull origin main
mysql -u archive_app -p archive_db < D:\projects-online\deploy\sql\migrate_260611_01.sql
```

（或 mysql 内 `source D:/projects-online/deploy/sql/migrate_260611_01.sql`）

**脚本覆盖：** 见下表（完整版含 v2 旧表补丁 + v1.1 旧表 ALTER + 新表 + 触发器，幂等可重跑）。

| 类别 | 内容 |
|---|---|
| **A. v1/v2 旧表补丁** | `material_version.parsed_text`+FULLTEXT；`project.customer_name`/金额/archive_status；`user` 登录限流；`llm_call_log`；`spring_ai_chat_memory` |
| **B. v1.1 旧表 ALTER** | `project/proposal/material/user/audit_log` 软删+version；`proposal` 附条件/编号；`audit_log.type`；`user.sensitive_view_enabled` |
| **C. v1.1 新表** | `failure_log`、`user_role`、`notification`(is_read)、`import_*`、`project_fact*`、`business_term` 等 + RBAC 种子 + 触发器 |

**跑完后：** 无需重启 jar → 浏览器 **Ctrl+F5** 验证控制台无 500。

---

### Step 7 附录 — 迁移脚本踩坑（已解决）

**PowerShell 整段粘贴一行报错：** 不要用一行 `$files=...foreach`；改用 `source migrate_260611_01.sql` 或 `run-v11-migrations.ps1`。

---

## 4. 尚未完成（下次继续）

- [x] 确认 `migrate_260611_01.sql` 在 125 执行成功（**112 queries OK**）
- [ ] 浏览器登录无 500，通知/待办/health 正常
- [ ] 跑 `v1.1-DEPLOY-GUIDE.md` **7 大场景** + v1.0 零回归 4 项
- [ ] 改 `admin` 默认密码、配真实 GLM API key
- [ ] （可选）正式对外：`npm run build` + Caddy/IIS 托管 `dist`，统一 `:80` 入口

---

## 5. 问题速查表

| 现象 | 可能原因 | 处理 |
|---|---|---|
| `git pull` 报 package-lock 冲突 | 本地改过 lock 文件 | `git checkout -- frontend/package-lock.json` 再 pull |
| healthcheck 过，浏览器 `/api/*` 500 | 缺 v1.1 表；JWT 路径查 `user_role`/`failure_log` 失败 | 跑 `migrate_260611_01.sql` |
| log: `failure_log doesn't exist` | 未跑 I-RI-37 / 合并迁移 | 同上 |
| log: `user_role doesn't exist` | 未跑 I-RI-34 | 同上 |
| notification 500 | 列 ``read`` vs `is_read` | 迁移脚本末尾自动 rename |
| `5173` 打不开 | dev 未起 / 防火墙 | `npm run dev`；放行 5173 |
| `http://125/` 无 Vue 页 | Caddy `:80` 仅反代 8080，未托管 dist | 验收用 `:5173`；正式再 build+Caddy |
| PowerShell 迁移脚本语法错误 | 多行粘成一行 | 用 `source xxx.sql` |
| `Duplicate column` 跑迁移 | Hibernate `ddl-auto:update` 已加过列 | 完整版脚本应跳过；可忽略 |

---

## 6. 关键路径备忘

```text
D:\projects-online\              # 代码（git）
D:\archive\config\config.json    # 生产密钥（不进 Git）
D:\archive\logs\backend.log      # 后端日志
D:\archive\files\                # 上传文件
backend\target\archive.jar       # 后端产物
frontend\dist\                   # 前端 build 产物（正式部署用）
deploy\sql\migrate_260611_01.sql # v1.1 一次性 DB 迁移
```

---

## 7. 对话中的架构 Q&A（备查）

**Q：Caddy 和 npm 啥关系？**  
A：npm 负责开发/打包前端；Caddy 负责正式环境把 `dist` 和 `/api` 一起对外（`:80`）。验收期只用 npm dev + jar，不必开 Caddy。

**Q：dist 里是 exe 吗？**  
A：不是，是 HTML/JS/CSS 静态网页；jar 是 Java 程序包，也不是 exe。

**Q：Caddy 必须装吗？**  
A：验收不必；给同事统一 `http://125` 无端口时再装/启 Caddy（或 IIS/Nginx 替代）。

**Q：healthcheck 和浏览器 health 为何不一致？**  
A：healthcheck 无 token；浏览器带 token 会走 RBAC，DB 未迁移时在 filter 层就 500。

---

## 8. 变更记录

| 日期 | 记录人 | 变更 |
|---|---|---|
| 2026-06-11 | 阿根廷 | 创建本文件，记录 0611 v1.1 引导部署全过程 |

---

*下次部署或排错：先看 §0 状态表 + §5 速查表，再 pull 最新 `migrate_260611_01.sql`。*
