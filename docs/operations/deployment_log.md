# 部署日志 — deployment_log

> 按**真实操作时间线**记录，方便下次 session 快速对齐。  
> 老文档 [`DEPLOYMENT-LOG.md`](DEPLOYMENT-LOG.md) 保留 2026-06-07 以前 M0 阶段记录；**本文件从 v1.1 (0611) 起记**。

---

## 索引

| 版本 | 日期 | 服务器 | 指挥/执行 | 状态 |
|---|---|---|---|---|
| **v1.1 / 0611 续** | 2026-06-11 | `182.168.1.125` | Auto (Guide) + 用户 (Operator) | ✅ VERIFY 已记录（§9）；round-0611 已 CLOSED |
| **v1.1 / 0612 新轮** | 2026-06-12 | `182.168.1.125` | Auto (Co-test Guide) + 用户 (Operator) | 🟡 Step 2e 新建项目流程 🔴 与 RI-16 不符 |

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
- mvn 验证报告（历史）：[`test-to-settle/old/VERIFICATION-REPORT.md`](../../test-to-settle/old/VERIFICATION-REPORT.md) · 新 PASS 写 [`test_task/`](../../test_task/README.md)
- 老部署记录：[`DEPLOYMENT-LOG.md`](DEPLOYMENT-LOG.md)（2026-06-07 前）
- **部署测试问题汇总**：`test-to-settle/round-2026-06-11-v1.1-deploy.md`（交架构 review，**测试轮次先记问题、不急着改代码**）

---

## 1. Git 时间线（与本部署相关）

| Commit | 说明 |
|---|---|
| `2ae2b62` | 阿根廷：mvn 验证报告 + H2 测试兼容修复 |
| `1ca9099` | Mavis：`v1.1-DEPLOY-GUIDE.md` |
| `59f25f7` | 阿根廷：首版 `migrate_260611_01.sql`（14 个 I-RI 合并） |
| `5551b74` | 阿根廷：**完整版** migrate — 含 v2/Plan I 旧表 ALTER + 幂等列检测 |
| `fca37d1` | docs(tasks)：TASKS 瘦身 + Plan I 历史归档 |
| `96abc32` | fix(agent)：`AgentEngine.java` 补类闭合 `}`（125 mvn 阻塞 T-0612-01） |
| `16eec7a` | fix(build)：`ArchiveMaterialPathResolver` + `AuditLogService.truncate`（T-0612-02） |
| `5408bee` | fix(test)：`ClientErrorControllerTest` MockBean import（T-0612-03） |

用户 125 上首次 pull 到约 `90b8616`；迁移脚本以 **`5551b74` 及之后** 为准。  
**0612 Co-test 当前基线**：`5408bee`（125 已 pull，`mvn` 成功，后端已重启）。

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

### Step 8 — 「AI 用量」500：前端路径双 `/api` ✅ 已修

**现象：** 控制台请求 `api/api/llm/my-usage` → 500。

**根因：** `http.ts` 的 `baseURL` 已是 `/api`，`LlmUsage.vue` 又写了 `/api/llm/...`，拼成 `/api/api/llm/...`。

**修复：** `frontend/src/views/LlmUsage.vue` 改为 `/llm/my-usage`、`/llm/stats`（commit 待 push）。

**125 上：**

```powershell
cd D:\projects-online
git pull origin main
# npm run dev 若未自动热更新，Ctrl+C 后重新 npm run dev
```

浏览器再点「AI 用量」，Network 里应是 **`/api/llm/my-usage`**（只有一个 api）。

---

### Step 7 附录 — 迁移脚本踩坑（已解决）

**PowerShell 整段粘贴一行报错：** 不要用一行 `$files=...foreach`；在 MySQL 客户端 `source deploy/sql/migrate_260611_01.sql`。

---

## 4. 尚未完成（下次继续）

- [x] 确认 `migrate_260611_01.sql` 在 125 执行成功（**112 queries OK**）
- [x] 0612 新轮：125 对齐 `5408bee`，`mvn -DskipTests clean package` **SUCCESS**，后端 jar 已重启
- [ ] **Step 2 冒烟**（healthcheck、5173 登录、Console 无 500、AI 用量 / 知识库 / 侧栏回归）
- [ ] 浏览器登录无 500，通知/待办/health 正常（0612 待 Operator 确认）
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
| AI 用量 500，URL 为 `/api/api/llm/...` | `LlmUsage.vue` 路径重复 `/api` | 改为 `/llm/my-usage`；`git pull` 刷新前端 |
| `5173` 打不开 | dev 未起 / 防火墙 | `npm run dev`；放行 5173 |
| `http://125/` 无 Vue 页 | Caddy `:80` 仅反代 8080，未托管 dist | 验收用 `:5173`；正式再 build+Caddy |
| PowerShell 迁移脚本语法错误 | 多行粘成一行 | 用 `source xxx.sql` |
| `Duplicate column` 跑迁移 | Hibernate `ddl-auto:update` 已加过列 | 完整版脚本应跳过；可忽略 |
| `mvn compile`：`AgentEngine.java` 文件结尾 | 缺类闭合 `}` | `git pull` ≥ `96abc32` |
| `mvn compile`：`getMaterial()` 找不到 | `ArchiveMaterialPathResolver` 误用 JPA 关联 | `git pull` ≥ `16eec7a` |
| `mvn compile`：`truncate` 找不到 | `AuditLogService.logClientError` 缺 helper | 同上 |
| `mvn testCompile`：`mock.bean.MockBean` 不存在 | 测试 import 包名写错 | `git pull` ≥ `5408bee`；正确包为 `mock.mockito.MockBean` |

---

## 6. 关键路径备忘

```text
D:\projects-online\              # 代码（git）；含 qa-agent/ 源码 + .venv（本地 pip，不进 Git）
D:\archive\config\config.json    # 生产密钥（不进 Git）；Java + qa-agent 共用
D:\archive\apps\backend\         # archive.jar（WinSW 后端）
D:\archive\logs\backend.log      # 后端日志
D:\archive\logs\qa-agent\        # qa-agent WinSW 日志
D:\archive\files\                # 上传文件
backend\target\archive.jar       # 后端编译产物（copy 到 archive\apps\backend）
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

## 9. Co-test 联调 — 2026-06-11 续（VERIFY 回归）

| 字段 | 内容 |
|---|---|
| **Guide** | Auto |
| **Operator** | 用户 |
| **环境** | `182.168.1.125`，`D:\projects-online` |
| **目标** | 拉最新 `main`，VERIFY T-0611-08 / 12 / 13 / 14 / 16 / 17 |
| **代码目标** | ≥ `dd7caae`（含 Knowledge fix `e592ce5`、LlmUsage `a6eabf1`） |

### 步骤记录

| Step | 操作 | 结果 | 时间 |
|---|---|---|---|
| 1 | `git log -5` 确认基线 | ✅ `HEAD=dd7caae=origin/main`；含 fix `e592ce5` | 2026-06-11 |
| 2 | 重新 `mvn package`，新 jar，后端已重启 | ✅ 后端已用新 jar 跑起；前端 dev **未重启**（一直在跑旧进程） | 2026-06-11 |
| 3 | 重启 `npm run dev` + 强刷 | （Operator 未单独确认；继续测 4a） | 2026-06-11 |
| 4a | 侧栏「AI 用量」 | ✅ **T-0611-18 已确认**：admin 免登正常；`GET /api/llm/stats?recentLimit=1` **403**；`GET /api/llm/my-usage?recentLimit=50` **200**（个人用量页可用，无「全员」） | 2026-06-11 |
| 4b | 知识库问「今天天气怎么样？」 | ✅ **T-0611-15 已确认**：**正常回答气泡**（非红条），8475ms；文案「抱歉, 多次尝试未找到匹配结果…」；思考过程 `network_dict_lookup(weather)` → 死循环警告 → 强制 `FINAL_ANSWER`；**未做离题拒答** | 2026-06-11 |
| 4c | 4b 后**不刷新**，侧栏切换（项目列表/看板/回收站等） | ✅ Console **无报错**；菜单切换 **正常** → T-0611-16/17 连锁导航 **本轮未复现** | 2026-06-11 |
| 4d | 知识库 UX：对话布局 vs DeepSeek/豆包 | 🔴 **T-0611-19 OPEN**：顶部输入 + 单次结果覆盖；无滚动多轮对话区、输入未固定底部 | 2026-06-11 |
| 5 | 知识库问「lmz项目下有几份材料？」 | 🟡 **T-0611-20 FIX 已提交本地**：`find_project` 多 variant MySQL 检索；125 **待 pull + mvn package 重启** 后复测 | 2026-06-11 |
| 6 | Operator 查 `D:\archive` 无项目目录 `.md` | 📝 **设计澄清**（非必现 bug）：v1.1 需求 **不**维护静态项目 MD；`D:\archive` 应有 `files/`、`parsed/`、`logs/`、`config/`；lmz 失败属 T-0611-20 运行时链路 | 2026-06-11 |

---

## 10. Co-test 联调 — 2026-06-12 新轮（Step 0～1）

| 字段 | 内容 |
|---|---|
| **Guide** | Auto（Co-test Guide） |
| **Operator** | 用户 |
| **环境** | `182.168.1.125`，`D:\projects-online` |
| **开场 SOP** | 每轮**先问 125 当前 git 版本**，对齐后再操作 |
| **目标** | 新轮验收：`main` 拉齐 → 重建 jar → 重启后端 →（下一步）冒烟 + 7 大场景 |
| **起始基线** | `fca37d1`（与阿根廷 `origin/main` 一致） |
| **最终基线** | `5408bee`（含 3 次 compile 热修） |

### 步骤记录

| Step | 操作 | 结果 | 时间 |
|---|---|---|---|
| 0 | Co-test 开场：确认 125 git 版本 | ✅ Operator 确认与 Guide 一致（`fca37d1`） | 2026-06-12 |
| 1a | Operator 杀掉旧后端进程 | ✅ 已执行 | 2026-06-12 |
| 1b | `mvn -DskipTests clean package`（@ `fca37d1`） | ❌ **T-0612-01**：`AgentEngine.java:464` 语法错误「到达文件结尾」— 缺类闭合 `}` | 2026-06-12 ~11:12 |
| 1c | Guide push `96abc32`；125 `git pull` 后重编 | ❌ **T-0612-02**：12 errors — `ArchiveMaterialPathResolver.getMaterial()` 不存在；`AuditLogService.truncate()` 缺失 | 2026-06-12 ~11:24 |
| 1d | Guide push `16eec7a`；125 pull 后重编 | ❌ **T-0612-03**：testCompile — `ClientErrorControllerTest` 误 import `mock.bean.MockBean` | 2026-06-12 ~11:31 |
| 1e | Guide push `5408bee`；125 pull 后重编 | ✅ **`BUILD SUCCESS`**；产物 `backend\target\archive.jar` | 2026-06-12 ~11:33+ |
| 1f | 重启 `archive.jar` | ✅ Operator 确认后端日志正常（Tomcat / Started ArchiveApplication） | 2026-06-12 |
| 2c | 知识库问「今天天气怎么样？」 | ✅ **离题拒答 UX 通过**（相对 0611 T-0611-15 改进）：答案「我是投委会档案助手，只回答项目档案相关问题。请问您想查询哪个项目？」；Console **无报错**；思考过程：`💭 无法解析 LLM 输出,直接返回原文` → `🔧 FINAL_ANSWER`（Agent 未走结构化 JSON，fallback 原文即拒答文案，与 `AgentSystemPrompt` 拒绝示例一致） | 2026-06-12 |
| 2d | 同会话第 2 问「lmz项目下有多少份材料？」 | 🔴 **T-0612-04 OPEN**：UI「问答失败: Request failed with status code 500」；Network `POST /api/qa/turn/{sessionId}` **500**；**前置**：第 1 问已成功（走 `/api/qa/ask`） | 2026-06-12 |
| 2d+ | Operator：同会话**后续任意再问** | 🔴 **同 T-0612-04**：第 2 问起 `turnCount≥1`，**每条**都走 `POST /api/qa/turn/...` → 全部 500；**非 lmz 独有**，会话内多轮整体不可用 | 2026-06-12 |
| 2e | 零回归：**新建项目** | 🔴 **T-0612-05 OPEN**（需求/产品）：侧栏「项目管理」→「+ 新建项目」→ **直接进入结构化表单**（编号/名称/类别…）；Operator：**应先上传材料再 AI 预填**，当前业务逻辑错误 | 2026-06-12 |

#### T-0612-05 对照（Guide 读需求 + 代码）

| 项 | 内容 |
|---|---|
| **需求** | RI-16 §5.11.4：**上传材料 → LLM 抽取 → 申请表预填 → 用户改 → 提交**（[`ARCH-DECOMPOSITION.md`](../../requirements/ARCH-DECOMPOSITION.md)） |
| **现状** | `ProjectList.goCreate()` → `/projects/new`，**无** `?materialVersionId=`；`ProjectForm` 默认空表单手工填；AI 预填**仅**在 URL 带 `materialVersionId` 时触发 |
| **后端** | `POST /api/projects/extract-preview`、`create` 已支持 `materialVersionId`（部分 MOD-06） |
| **缺口** | **缺「上传优先」入口页/向导**；主路径仍是 v1.0 式手工建项 |
| **归类** | 非 125 部署环境问题；**需求未闭环**（→ **C-0612-01** · [`plan-2026-06-12-project-create-upload-first`](../../upgrade_to_settle/plan-2026-06-12-project-create-upload-first.md)） |

#### T-0612-04 根因（Guide 对照代码）

| 层 | 实际行为 |
|---|---|
| **前端** `Knowledge.vue` | `turnCount > 0` 时 **`POST /api/qa/turn/{sessionId}`** + JSON `{ question }`（见 plan chat-ui §4.2） |
| **后端** `MultiTurnController` | 仅 **`GET /api/qa/turn/{sessionId}?question=...`**（`@RequestParam`，无 `@PostMapping`） |
| **结论** | 聊天 UI 多轮接口 **前后端契约不一致** → **第 1 问后本会话内所有后续提问均 500**；与具体问句 / find_project 无关 |
| **临时绕过** | **Ctrl+F5 刷新** → 新 session，`turnCount=0`，**仅下一条**可走 `/api/qa/ask`；再发第 2 条仍会 500 |

### 编译阻塞摘要（已修，待收入 round §1 若需正式路由）

| ID | 严重度 | 现象 | 修复 commit |
|---|---|---|---|
| **T-0612-01** | P0 | `AgentEngine.java` 缺 `}`，`mvn compile` 失败 | `96abc32` |
| **T-0612-02** | P0 | `ArchiveMaterialPathResolver` 调用不存在的 `getMaterial()`；`AuditLogService` 缺 `truncate` | `16eec7a` |
| **T-0612-03** | P1 | `ClientErrorControllerTest` MockBean 包名错误（`-DskipTests` 仍 testCompile） | `5408bee` |

### 下一步（未做）

- [ ] Step 2a：`healthcheck.ps1`
- [ ] Step 2b：确认 / 重启 `npm run dev`（`:5173`）
- [x] Step 2c（部分）：知识库离题问「今天天气怎么样？」→ ✅ 拒答文案 + 无 Console 报错
- [ ] Step 2c（续）：2c 后**不刷新**侧栏切换（T-0611-16/17 回归）— **待 Operator 反馈**
- [ ] Step 2e：新建项目 — 🔴 T-0612-05（RI-16 上传优先未实现主路径）
- [ ] Step 2d：lmz 材料数 — 🔴 多轮路径 500（T-0612-04）；**可刷新后单轮重测**
- [ ] Step 3：`v1.1-DEPLOY-GUIDE.md` 7 大场景 + 零回归 4 项

### 观察项（非阻塞，暂不记 round）

| 项 | 说明 |
|---|---|
| 思考过程「无法解析 LLM 输出」 | LLM 未返回可解析的 ReAct JSON，`AgentEngine` fallback 直接展示原文；**用户可见答案正确**，仅思考链展示不够「干净」 |

---

## 8. 变更记录

| 日期 | 记录人 | 变更 |
|---|---|---|
| 2026-06-11 | 阿根廷 | 创建本文件，记录 0611 v1.1 引导部署全过程 |
| 2026-06-11 | Auto | 开启 Co-test 续：§9 VERIFY 回归，框架补 §7.5 Co-test 角色 |
| 2026-06-12 | Auto (Co-test Guide) | §10 0612 新轮：Step 0 git 对齐 + mvn 三轮编译修复 + 后端重启；索引/Git 时间线/速查表同步 |
| 2026-06-12 | Auto (Co-test Guide) | §10 Step 2c：离题问「今天天气怎么样？」— 拒答 UX ✅，Console 无报错 |
| 2026-06-12 | Auto (Co-test Guide) | §10 Step 2d：多轮第 2 问 lmz → 500（T-0612-04）；后续同会话全 500 |
| 2026-06-12 | Auto (Co-test Guide) | §10 Step 2e：新建项目仍手工表单入口 — T-0612-05 vs RI-16 上传优先 |
| 2026-06-12 | Auto (Co-test Guide) | 任务分流：DEBUG [`round-2026-06-12-qa-regression`](../../test-to-settle/round-2026-06-12-qa-regression.md) + UPGRADE plan + complexity C-0612-01 |
| 2026-06-12 | Sisyphus | **§11 qa-agent 交付**：Python 8 工具 + 二期全量 · Java BFF QaAgentClient/health · WinSW qa-agent.xml · Java Agent @Deprecated |

---

## 11. qa-agent 微服务交付（2026-06-12）

### 交付物

| 模块 | 文件 | 说明 |
|------|------|------|
| Python 服务 | `qa-agent/` | FastAPI 8 工具 + ReAct + pytest 20 passed |
| Java BFF | `QaAgentClient.java` | HTTP 调用 Python，带 agentSources 映射 |
| Java BFF | `QaAgentHealthIndicator.java` | Actuator 健康检查 |
| Java BFF | `MultiTurnController.java` | 3 降级路径（Python→Java→503） |
| 部署 | `deploy/winsw/qa-agent.xml` | WinSW 第 5 进程 |
| 部署 | `deploy/scripts/register-services.bat` | 注册脚本（已含 qa-agent） |
| 运维 | `docs/operations/RUNBOOK.md` §1.2 | qa-agent 启停/日志/环境变量 |
| 架构 | `docs/architecture/08-qa-agent-python-service.md` | 架构说明 |

### 状态

- ✅ Python 二期 4 工具（archive_fs/network/get/ask）已实现
- ✅ v1.1 prompts 含 8 工具 + 置信度 + 5 级切换
- ✅ LLM 调用写入 `llm_call_log`（AGENT_STEP）
- ✅ Java Agent `@Deprecated`，默认关闭
- ✅ pytest 20 passed · `mvn compile` 待 125 验证

### 125 部署路径（2026-06-12 拍板）

| 项 | 路径 |
|---|---|
| 源码 | `D:\projects-online\qa-agent` |
| venv | `D:\projects-online\qa-agent\.venv`（125 本地 `pip install`，不进 Git） |
| 配置 | `D:\archive\config\config.json` |
| **当前启停** | 手工 `deploy/scripts/start-qa-agent.ps1` |
| WinSW 日志（后续） | `D:\archive\logs\qa-agent\` |

### 待 125 验证

- §1.4 验收 8 条
- 手工启动 qa-agent + `/health` 返回 `config_json`
- 7 大端到端场景

---

*下次部署或排错：先看 §0 状态表 + §5 速查表，再 pull 最新 `main`。*
