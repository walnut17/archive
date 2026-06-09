# 投委会档案管理系统 — 环境管理与外部依赖档案

> **文档说明**: 专门记录"项目运行/部署/开发**对外部世界的依赖**":硬件、网络、操作系统、外部接口、凭证、文件路径、端口、密钥、第三方 SaaS 等。
> **版本**: v1 / 2026-06-09
> **阅读对象**: 接手 AI、运维、审计、合规
> **维护**: 环境变更时**必**更新本档案,标日期和原因
> **互补文档**:
> - `docs/TEAM-ARCHIVE.md` — 项目基本信息 + 团队约定 + 踩坑
> - `docs/ARCHITECTURE-v2.md` — 架构基线
> - `docs/DEPLOYMENT-LOG.md` — 部署日志(每次部署的版本/时间/操作)
> - `docs/DB-SCHEMA-v2.md` — 数据库 schema(不重复,只列连接信息)

---

## 0. 一句话总结(给非技术方看)

本系统运行在**一台 Windows Server 2012 R2 单机**上,**仅依赖 1 个智谱 GLM API Key**和**1 个 MySQL 数据库**。**不依赖**任何其他外部 SaaS、云服务、向量库、RAG 平台、消息队列、对象存储。**所有数据/计算都在本机完成**。

---

## 1. 硬件依赖

### 1.1 部署主机(必需,**单机**)

| 项 | 规格 | 备注 |
|---|---|---|
| 角色 | 生产 + 开发 + 沙箱(同一台) | **不**双机主备,**不**集群 |
| OS | Windows Server 2012 R2 64-bit | PowerShell 5.x(.ps1 必须纯 ASCII + .NET HttpClient) |
| CPU | ≥ 4 核 | 实测 2 核可用,4 核舒适 |
| **内存** | **≥ 16 GB(推荐 32 GB)** | 现状 32GB;后端 ~1GB + Spring AI < 100MB + Node 20 ~ 500MB + MySQL ~ 1GB + 系统 1GB,**总 < 4GB**,32GB 极大富余 |
| 磁盘 | ≥ 200 GB(档案 + 解析后文本) | 档案 ≤ 50GB 原始 + 解析后 FULLTEXT 索引约 1.5x = ~75GB,留 buffer |
| 网络 | **内网隔离**(无公网) | 见 §2.1 |

### 1.2 客户端(必需)

| 项 | 规格 |
|---|---|
| 浏览器 | Chrome / Edge 现代版本(Element Plus + Vue 3 兼容) |
| 操作系统 | Windows / macOS / Linux(只要能跑 Chrome) |
| 屏幕分辨率 | 1366x768 及以上 |

### 1.3 沙箱环境(Mavis 专用,可选)

| 项 | 规格 | 用途 |
|---|---|---|
| 沙箱 | Linux container(本仓库的 `/workspace`) | PM 方案设计 + 沙箱编译验证,**不**部署生产 |
| JDK | 17.0.2(`/workspace/.tools/jdk-17.0.2`) | 沙箱内 `mvn compile` |
| Maven | 3.9.6(`/workspace/.tools/maven-3.9.6`) | 离线模式 `-o` |
| Node | 20.x(沙箱不跑前端构建,只校验语法) | 实际构建在用户本机 |

---

## 2. 网络依赖

### 2.1 出站连接(本机 → 外部)

| 目的地址 | 协议 | 端口 | 用途 | 必需? |
|---|---|---|---|---|
| `open.bigmodel.cn` | HTTPS | 443 | **智谱 GLM-4-Flash API**(LLM 调用) | **必需** |
| `git@gitee.com` | SSH | 22 | Git 推送/拉取代码 | **必需**(开发时) |
| `maven.aliyun.com` | HTTPS | 443 | Maven 依赖下载(沙箱/生产均可) | **必需**(首次构建) |
| `registry.npmjs.org` | HTTPS | 443 | npm 依赖下载(前端首次) | **必需**(首次构建) |

> **其他外部 SaaS / 公网地址一律不得加** —— 任何新加的外部接口必须先在 `AGENT-FRAMEWORK-DECISION.md` / 本档案登记。

### 2.2 入站连接(外部 → 本机)

| 来源 | 协议 | 端口 | 用途 |
|---|---|---|---|
| 内网浏览器 | HTTP | 80(Caddy 反代) | 前端访问 |
| 内网浏览器 | HTTP | 5173(开发) | 前端 Vite dev server |
| 内网浏览器 | HTTPS | 8080(后端) | Spring Boot |
| 内网浏览器 | HTTPS | 3306 | MySQL(**不**暴露外网,内网连接) |

**对外暴露面**: **0 个公网端口**。所有入站都走内网或反代。

### 2.3 内网通信矩阵

| 源 → 目标 | 协议 | 用途 |
|---|---|---|
| 浏览器 → Caddy :80 | HTTP | 用户访问入口 |
| Caddy → Spring Boot :8080 | HTTP | 反代 |
| Caddy → MySQL :3306 | TCP | 持久化 |
| Spring Boot → MySQL :3306 | JDBC | 业务查询 |
| Spring Boot → `open.bigmodel.cn:443` | HTTPS | LLM 调用 |
| Vite dev → Spring Boot :8080 | HTTP proxy | 前端开发模式 |

---

## 3. 外部服务依赖(SaaS / API / 云)

### 3.1 智谱 GLM(LLM 唯一依赖)

| 项 | 值 |
|---|---|
| 服务 | 智谱开放平台 GLM-4-Flash |
| 协议 | OpenAI 兼容 RESTful |
| Base URL | `https://open.bigmodel.cn/api/paas/v4` |
| 模型 | `glm-4-flash`(免费) |
| API Key | 环境变量 `GLM_API_KEY`(32 字符) |
| 限流 | 60 req/min(企业认证可放宽) |
| 费用 | 0 元(GLM-4-Flash 免费) |
| 备胎 | 切到 `glm-4-plus`(付费) 只需改 `application.yml` 一行 |
| 文档 | <https://open.bigmodel.cn/dev/api/openai-sdk> |
| 申请 | <https://open.bigmodel.cn/usercenter/apikeys> |
| 密钥保管 | 仅运维持有,**不**入库,**不**进 .yml,**不**进 .env 入仓 |

**SLA 假设**:
- 服务可用性:智谱承诺 99.9%,实测更稳
- 单次响应延迟:2-8 秒
- **降级路径**(关键):LLM 不可达时,后端 fallback 到 FULLTEXT 检索(无 LLM 答案,但来源/命中数仍可用),**用户无白屏**

### 3.2 Gitee(代码托管,开发期依赖)

| 项 | 值 |
|---|---|
| 服务 | Gitee 私有仓库 |
| URL | `git@gitee.com:frisker/projects-online.git` |
| 分支 | `minimax`(开发) / `main`(生产) |
| 凭证 | SSH deploy key(项目方持有,**不**入仓) |
| 沙箱内路径 | `/workspace/projects-online-clone/.ssh/archive_deploy` |
| 文档 | <https://gitee.com/frisker/projects-online> |

### 3.3 Maven / npm 镜像(构建期依赖)

| 项 | 值 |
|---|---|
| Maven mirror | `https://maven.aliyun.com/repository/public`(阿里云镜像,首次下载) |
| npm mirror | `https://registry.npmjs.org`(原始,内网已通) |
| 离线模式 | 沙箱可 `mvn -o` 离线构建(已下载 jar 缓存到 `/workspace/.tools/maven-repo`) |

---

## 4. 内部依赖(本机服务)

### 4.1 必需服务

| 服务 | 版本 | 启动 | 端口 | 状态 |
|---|---|---|---|---|
| MySQL | 8.0.16 | Windows Service,开机自启 | 3306 | 已部署 |
| JDK | 17.0.2 | PATH 环境变量 | - | 已部署 |
| Maven | 3.9.6 | PATH 环境变量 | - | 已部署 |
| Node | 20.x | PATH 环境变量 | - | 已部署 |
| Caddy | 1.x | Windows Service | 80 | 已部署 |
| Spring Boot | 3.3.x | `startup.ps1` 拉起 | 8080 | 已部署 |

### 4.2 关键路径

| 用途 | 路径 |
|---|---|
| 应用代码 | `D:\projects-online\`(后端 `backend/`,前端 `frontend/`) |
| 启动脚本 | `D:\projects-online\backend\startup.ps1` |
| 应用配置 | `D:\archive\config\config.json` |
| **原始档案** | `D:\archive\files\`(只读,**不**入仓) |
| **解析后文本** | `D:\archive\parsed\`(FULLTEXT 索引来源,**不**入仓) |
| **日志** | `D:\archive\logs\`(Spring Boot + MySQL + Caddy) |
| **应用 jar** | `D:\archive\apps\`(git pull 后 mvn 打包输出) |
| **数据库** | `D:\archive\db_data\MySQL\`(MySQL 数据目录) |
| **沙箱持久化** | `/workspace/.tools/`(JDK/Maven) + `/workspace/projects-online-clone/.ssh/`(deploy key) |

### 4.3 数据库(本机内嵌)

| 项 | 值 |
|---|---|
| 类型 | MySQL 8.0.16 |
| 数据库名 | `archive_db` |
| 用户 | `archive_app`(主应用) / `root`(运维) |
| 凭证 | `D:\archive\config\config.json`(`spring.datasource.password` 字段) |
| 备份 | 每周一凌晨手动 `mysqldump` 到 `D:\archive\db_backup\` |
| Schema 演进 | `backend/src/main/resources/db/migration/` 下的 `v2-schema.sql` / `G-llm-call-log.sql` 等,**不**用 Flyway/Liquibase(本期) |

---

## 5. 凭证 / 密钥管理

| 凭证 | 位置 | 入仓? | 备份 |
|---|---|---|---|
| 智谱 GLM API Key | `D:\archive\config\config.json` + 沙箱 `secret` 工具 | ❌ 绝不 | 加密 USB,1 份 |
| MySQL `archive_app` 密码 | `D:\archive\config\config.json` | ❌ 绝不 | 加密 USB,1 份 |
| MySQL `root` 密码 | 运维人员脑 + 加密 USB | ❌ 绝不 | 加密 USB,2 份 |
| Gitee deploy key | `D:\archive\config\.ssh\archive_deploy` + 沙箱 `/root/.ssh/archive_deploy` | ❌ 绝不 | Gitee 仓库 Settings 可重新生成 |
| Windows 管理员密码 | 运维人员脑 | ❌ 绝不 | - |

**硬规则**:
- **不**在源码、文档、`.env`、`.yml`、`.properties`、commit message 里出现**任何**明文凭证
- **不**通过 IM / 邮件传凭证
- **不**用 Git 提交 `.ssh/`、`config.json`、`*.env`、`.git-credentials`
- 项目方有 secret 工具(GITEE_SSH_KEY)管理沙箱侧凭证

---

## 6. 文件系统 / 端口 / 资源 限额

### 6.1 文件系统

| 类型 | 路径 | 权限 | 大小限制 |
|---|---|---|---|
| 应用代码 | `D:\projects-online\` | 读写 | - |
| 应用配置 | `D:\archive\config\` | 读写 | - |
| 原始档案 | `D:\archive\files\` | **只读**(防误删) | 50GB 总量 |
| 解析后文本 | `D:\archive\parsed\` | 读写 | 75GB(估算 1.5x) |
| 数据库数据 | `D:\archive\db_data\` | 读写 | - |
| 日志 | `D:\archive\logs\` | 读写 | **30 天滚动**(Spring Boot logback) |
| 数据库备份 | `D:\archive\db_backup\` | 读写 | **12 周滚动** |
| 临时上传 | `D:\archive\uploads\` | 读写 | 单文件 ≤ 200MB |

### 6.2 端口分配

| 端口 | 服务 | 备注 |
|---|---|---|
| 80 | Caddy 反代 | **唯一对外** |
| 3306 | MySQL | **仅内网** |
| 5173 | Vite dev server | **仅开发模式**,生产不用 |
| 8080 | Spring Boot | **仅内网**,经 Caddy 反代 |

### 6.3 资源限额(应用层)

| 项 | 限额 |
|---|---|
| 单文件上传 | 200MB(可在 `application.yml` 调) |
| FULLTEXT 检索 topN | 默认 10,硬上限 100 |
| LLM 调用超时 | 30 秒(GlmService.chat) |
| Agent 步骤上限 | 5(`spring.ai.agent.max-iterations`) |
| LLM 工具结果观察字符上限 | 2000(`observation-truncate-chars`) |
| SQL 查询 limit | 100(`QueryMysqlTool` 硬上限) |
| 文件夹扫描并发 | 4 线程(M2 文件监听) |

---

## 7. 网络安全 / 防火墙

| 项 | 现状 |
|---|---|
| 防火墙 | Windows Firewall 默认开启 |
| 入站规则 | 仅 80 / 8080 / 3306 / 22(SSH,仅运维) |
| 出站规则 | 全开(GLM / Gitee / Maven / npm 都需) |
| DDoS 防护 | 不需要(内网,无公网) |
| WAF | 不部署(内网) |
| TLS | 本期**不**启用(内网 HTTP);若需对外,加 Caddy 自签证书 |

---

## 8. 容灾 / 备份

| 项 | 策略 |
|---|---|
| 数据库 | 每周一凌晨 `mysqldump` → `D:\archive\db_backup\archive_db_<date>.sql`,保留 12 周 |
| 原始档案 | 一次性写入,只读,无备份(若丢,需业务方重传) |
| 解析后文本 | **可重生成**(原始档案 + 解析脚本),无备份 |
| 应用代码 | Gitee `main` 分支为唯一可信源,本地 `D:\projects-online\` 为镜像 |
| 启动脚本 | `startup.ps1` 自动 `git pull` + `mvn package`,无需手动同步 |
| 灾备演练 | 本期不强制(单机) |

**RTO/RPO 假设**(本期,单机无异地):
- RTO(恢复时间目标): ≤ 30 分钟(数据库还原 + 启动)
- RPO(数据恢复点目标): ≤ 1 周(周备份)

---

## 9. 监控 / 日志

| 项 | 工具 | 路径 |
|---|---|---|
| 应用日志 | Spring Boot logback(滚动 30 天) | `D:\archive\logs\backend.log` |
| 数据库日志 | MySQL error log | `D:\archive\logs\mysql.err` |
| 反代日志 | Caddy access log | `D:\archive\logs\caddy.log` |
| 审计 | `audit_log` 表(操作人/时间/资源) | MySQL |
| LLM 用量 | `llm_call_log` 表(场景/时间/结果) | MySQL |
| 监控告警 | 本期**不**部署(单机 + 1 人) | - |

**关键日志规则**:
- ❌ **不**记 API Key / 密码 / 凭证
- ❌ **不**记用户上传的**敏感业务数据原文**(只记 ID / 标题)
- ✅ 记所有 agent 工具调用(steps[])
- ✅ 记所有 LLM 调用(scenario / user / elapsedMs)
- ✅ 记所有写操作(项目/档案/时点/待办/触发器)

---

## 10. 升级 / 变更管理

| 项 | 流程 |
|---|---|
| Spring AI 升级 | 等官方 GA(目前 1.1),小版本升级先在沙箱编译验证 |
| MySQL 升级 | 本期不升(8.0.16 满足);如需 8.4,先备份再 `mysql_upgrade` |
| JDK 升级 | 当前 17.0.2,LTS;升 21 需先验证 Spring Boot 3.3 兼容 |
| Node 升级 | 跟随 LTS,18 → 20 已在测试 |
| LLM 模型切换 | 改 `application.yml` 1 行(`model: glm-4-flash` → `glm-4-plus` 等) |
| 智谱 API Key 轮换 | 1. 申请新 key;2. 改 `config.json`;3. 重启后端;4. 旧 key 在智谱后台撤销 |
| 数据库 schema 变更 | 写新 `v3-schema.sql` / `H-xxx.sql`,生产手动跑 |

---

## 11. 合规 / 审计

| 项 | 现状 |
|---|---|
| 等保 | 本期不强制(内网,无敏感个人信息) |
| 个人信息 | 业务档案**可能**含客户身份证号/手机号/银行账号,**必须**加密存储(本期 AES,**待办**) |
| 数据出境 | **0 跨境** —— 所有数据在本机,LLM 调用传文本摘要,不含完整档案 |
| 审计 | `audit_log` 表记录所有关键操作,保留 5 年(本期 12 周滚动) |
| 日志访问 | 仅 `admin` 角色可看完整日志,`user` 看自己的 |

---

## 12. 已知风险 / 限制

| 风险 | 影响 | 缓解 |
|---|---|---|
| **单机部署**,无主备 | 主机故障 = 全停 | 每周 db 备份 + 启动脚本自动恢复 |
| **MySQL 与应用同机** | 数据库压力与应用争 CPU | 32GB 内存富余,实际未触发 |
| **GLM 限流 60 req/min** | 高峰期 LLM 调用排队 | 5 步 max_iterations + 队列缓冲(下期) |
| **沙箱 vs 生产环境** | 沙箱 Linux + 生产 Windows | Spring Boot 跨平台 + 沙箱编译验证 + 启动脚本幂等 |
| **PowerShell 5.x** | .ps1 不支持 `??` 等现代语法 | 已固化:ASCII + .NET HttpClient(踩坑 #1) |
| **Spring AI 1.1 较新** | 可能有 bug | 走官方 GA + 5 步上限 + 降级到 GlmService |
| **智谱依赖** | 智谱故障 = LLM 不可用 | 降级到 FULLTEXT-only + 未来可换 Qwen(只需改 base-url) |
| **MySQL FULLTEXT 中文** | ngram parser 必需 | 已加 `WITH PARSER ngram`,见 `v2-schema.sql` |
| **MySQL 8.0.16 较老** | 8.0.16 是 2019 年,新特性缺 | 本期不动,升 8.4 待评估 |

---

## 13. 变更日志

| 日期 | 变更 | 作者 |
|---|---|---|
| 2026-06-09 | 初版 v1,Plan H 实施前 | Mavis 沙箱 PM |

---

*本档案与 `TEAM-ARCHIVE.md` 互补。本档案侧重"对外依赖"和"环境约束",`TEAM-ARCHIVE.md` 侧重"团队约定"和"踩坑"。*
