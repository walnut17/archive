# 投委会档案管理系统

> 投委会专属档案管理与智能分析 Web 应用
> **当前阶段**:v2.0 开工准备(M0/M1/M2 已落,v2.0 业务核心待开发)
> **维护**:Mavis(主开发 agent)+ 用户(业务方)
> **最后更新**:2026-06-08

---

## ⚡ 接手必读(给新来的人 / Agent)

**按顺序读 3 份**(20 分钟掌握全貌):

1. 📋 **[`docs/REQUIREMENTS-v1.md`](docs/REQUIREMENTS-v1.md)** — 业务需求(12 章节,872 行,1 万多字)。先看这个,知道系统做什么/不做什么。
2. 🏗️ **[`docs/ARCHITECTURE-v2.md`](docs/ARCHITECTURE-v2.md)** — 架构方案(685 行,33KB)。看 M0~M2 沿用 + v2.0 增补。
3. 🛠️ **[`docs/TEAM-ARCHIVE.md`](docs/TEAM-ARCHIVE.md)** — 团队档案(458 行)。环境/部署/沙箱/数据库/账户/紧急情况。

**再读 2 份**(开发规范,1 小时掌握工程标准):

4. 📏 **[`docs/DEV-STANDARDS.md`](docs/DEV-STANDARDS.md)** — 开发标准 + 交付规范(466 行)。命名/注释/安全/Git/测试/完工自查。
5. 🩹 **[`docs/LESSONS-LEARNED.md`](docs/LESSONS-LEARNED.md)** — 踩坑记录。13+ 条真实坑,避免重蹈覆辙。

**想开工?读 1 份**:

6. 🚀 **[`.mavis/plans/plan-A-phase0-fixes.md`](.mavis/plans/plan-A-phase0-fixes.md)** — 第一个施工 plan(P0 阻塞修复,30-45 分钟)。

---

## 📂 仓库结构(2026-06-08 扁平化)

```
projects-online/
├── README.md                                # 本文件
├── .gitignore                               # 含 .ssh/ + .mavis/plans/yaml 屏蔽
├── .gitignore.example                       # 子项目 .gitignore 模板
│
├── backend/                                 # Spring Boot 3.3 + JPA
│   ├── pom.xml
│   ├── startup.ps1
│   ├── healthcheck.ps1
│   ├── src/main/java/com/archive/...        # 实体/服务/控制器
│   ├── src/main/resources/                  # application.yml + SQL
│   └── README.md
│
├── frontend/                                # Vue 3 + TypeScript + Element Plus
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── src/                                 # views / api / router / store
│   └── README.md
│
├── deploy/                                  # 部署配置
│   ├── caddy/                               # Caddyfile
│   ├── winsw/                               # WinSW 服务配置
│   ├── sql/                                 # 数据库迁移 SQL
│   └── scripts/                             # 启动/重启脚本
│
├── config/                                  # 配置模板
│   ├── config.example.json                  # ⭐ 用户复制这个填真实值
│   └── README.md
│
├── docs/                                    # ⭐ 8 份核心文档
│   ├── REQUIREMENTS-v1.md                   # 业务需求
│   ├── ARCHITECTURE-v2.md                   # 架构方案
│   ├── DB-SCHEMA-v2.md                      # 数据库 v2(可执行 SQL)
│   ├── SIMILAR-PRODUCTS.md                  # 6 类 22 个产品调研
│   ├── ARCH-REUSE-AUDIT.md                  # M0~M2 沿用评估
│   ├── DEV-STANDARDS.md                     # 开发标准
│   ├── TEAM-ARCHIVE.md                      # 团队档案
│   ├── LESSONS-LEARNED.md                   # 踩坑记录
│   ├── DEPLOYMENT-LOG.md                    # 部署日志
│   ├── M1-README.md                         # M1 完工报告
│   └── M1-TEST-TASKS.md                     # M1 测试任务
│
├── architecture-v1-full.md                  # 已废止的 v1 方案(保留历史)
├── architecture-v2-lite.md                  # 过渡版(保留历史)
├── architecture-v3-final.md                 # v3 终稿(M0/M1/M2 基线)
├── SUPPLEMENTARY-REQUIREMENTS.md            # 597 行 P0~P4 缺陷清单
│
├── scripts/                                 # 仓库级脚本
│   └── sync.sh                              # minimax 从 main 拉新并 push
│
└── .mavis/plans/                            # ⭐ 施工 plan(6 个,见下)
    ├── plan-A-phase0-fixes.md               # P0 阻塞修复
    ├── plan-B-phase1-arch-fixes.md          # P1 架构修复
    ├── plan-C-phase2-business-core.md       # P2 业务核心
    ├── plan-D-phase2-5-ux.md                # P2.5 UX 增强
    ├── plan-E-phase3-rbac-dict-ui.md        # P3 权限/字典/UI
    └── plan-F-phase4-test-polish.md         # P4 测试/收尾
```

> **2026-06-08 扁平化重构**(`39b18ed`):之前所有源码在 `investment-committee-archive-system/` 下,现在 `backend/` / `frontend/` / `docs/` 都在仓库根,部署路径更直。`scripts/sync.sh` 帮 minimax 保持跟 main 同步。

---

## 📚 核心文档(11 份,都已在 `docs/`)

| 类别 | 文档 | 行数 | 用途 |
|---|---|---|---|
| **业务** | `REQUIREMENTS-v1.md` | 872 | 12 章节,基于 4 轮业务访谈 |
| **架构** | `ARCHITECTURE-v2.md` | 685 | v3 基础 + 增补,Provider/Engine 层 |
| **架构** | `DB-SCHEMA-v2.md` | 1060 | 10 新表 + ALTER 沿用表,含完整 SQL |
| **调研** | `SIMILAR-PRODUCTS.md` | 256 | 6 类 22 个同类产品对比 |
| **调研** | `ARCH-REUSE-AUDIT.md` | 219 | M0~M2 逐项 ✅/✏️/❌ |
| **治理** | `DEV-STANDARDS.md` | 466 | 开发标准 + 完工交回清单 |
| **治理** | `TEAM-ARCHIVE.md` | 458 | 环境/部署/沙箱/账户/紧急 |
| **治理** | `LESSONS-LEARNED.md` | 持续 | 13+ 真实踩坑 |
| **沿用** | `M1-README.md` | 80 | M1 完工报告 |
| **沿用** | `M1-TEST-TASKS.md` | 270 | M1 测试任务清单 |
| **沿用** | `DEPLOYMENT-LOG.md` | 320 | 部署日志 |

---

## 🚀 施工 plan(6 个,按序执行)

| Plan | 内容 | 工作量 | 依赖 | 互斥 |
|---|---|---|---|---|
| **A** | P0 阻塞修复(6 项) | 30-45 分钟 | 无 | 必须先做 |
| **B** | P1 架构修复(4 项) | 1-2 小时 | A | 跟 C/D/E 可分模块并行 |
| **C** | P2 业务核心(LLM + 4 Engine + 8 实体 + 6 Controller) | 1-2 周 | B | 跟 D 不可并行 |
| **D** | P2.5 UX(批量上传 + 智能摘要) | 半天-1 天 | C | 跟 C 不可并行 |
| **E** | P3 权限 + 字典 UI + 抽取/对比/触发规则 UI | 1-2 天 | C/D | 跟 F 不可并行 |
| **F** | P4 集成测试 + 类型安全 + 性能基线 + 文档收尾 | 1-2 天 | 全部 | 收尾 |

**每个 plan 都自包含**:必读清单 + 范围 + 验收 + 提交规范 + 交回物。打开看即可独立开工。

---

## 📋 项目当前状态(2026-06-08)

| 阶段 | 内容 | 状态 | 证据 |
|---|---|---|---|
| **架构** | v1 → v2 → v3 演进 | ✅ 落定 | `architecture-v3-final.md` |
| **调研** | 同类产品 + 沿用评估 | ✅ 完成 | `SIMILAR-PRODUCTS.md` / `ARCH-REUSE-AUDIT.md` |
| **需求** | 业务需求 v1 | ✅ 完成 | `REQUIREMENTS-v1.md` |
| **治理** | 开发标准 + 团队档案 | ✅ 完成 | `DEV-STANDARDS.md` / `TEAM-ARCHIVE.md` |
| **M0** | 脚手架 + 登录 + 部署 | ✅ 跑通 | 浏览器端到端验证 |
| **M1** | 项目-议案-材料 CRUD | ✅ 跑通 | M1-README.md |
| **M2** | 知识库问答(MySQL FULLTEXT + GLM) | ✅ 跑通 | d5ae194 修复 + M2 框架 |
| **Phase 0** | P0 阻塞修复 | ⏳ 准备 | plan-A |
| **Phase 1-4** | v2.0 业务核心 | ⚪ 未启动 | plan-B~F |

**已落代码**(commits `5bb2439` → `1528ed1` → `39b18ed` 扁平化),**全部在 `minimax` 分支**。

---

## 🌿 Git 分支约定

| 分支 | 用途 | 谁能推 |
|---|---|---|
| `main` | 生产分支(只读) | **只走 PR** |
| `minimax` | 集成/开发(活跃) | dev 直接 push |
| `feature/*` | 单功能分支 | dev |

**任何 push 都到 `minimax`,main 走 PR。**

**单向同步**:`minimax` 始终从 `origin/main` 拉新(fast-forward merge),push **只**走 `origin/minimax`,**不**直推 main。

**推荐**:`./scripts/sync.sh "commit message"`(自动 fetch → 切 minimax → merge main → add → commit → push)

---

## 🔑 账户 / 凭证(本地自备,不在仓库)

| 项 | 来源 |
|---|---|
| Gitee 账号 `frisker` 的 user account key | 已有,在 `~/.ssh/` |
| 智谱 GLM-4-Flash API key | 免费,https://open.bigmodel.cn/ 申请 |
| MySQL root 密码 | 本机已有,填到 `config/config.json` |
| JWT secret(>= 32 byte) | `openssl rand -base64 32` 生成 |

详细见 `docs/TEAM-ARCHIVE.md` § 3.3 + `docs/DEV-STANDARDS.md` § 3.3。

---

## 🛠 快速开始(本机 5 步)

```powershell
# 1. 拉仓库(假设 Gitee 账号 key 已配)
git clone git@gitee.com:frisker/projects-online.git
cd projects-online
git checkout minimax

# 2. 后端
cd backend
mvn clean package -DskipTests
# 复制 target\archive.jar 到 D:\archive\apps\backend\
cd ..
cd deploy\scripts
.\start-backend.ps1   # 或 .\startup.ps1

# 3. 前端(dev 模式)
cd ..\..\frontend
npm install
npm run dev
# 浏览器开 http://localhost:5173

# 4. 数据库(已有 archive_db,跑 v2 迁移)
mysql -u root -p archive_db < backend\src\main\resources\db\migration\v2-schema.sql

# 5. 配置
# 复制 config\config.example.json 到 D:\archive\config\config.json
# 填 glm.apiKey + jwt.secret + database.password
```

---

## 📞 找谁

- **业务方**:frisker(投委会秘书 / 项目经理)—— 业务决策 + 过目 PR
- **开发主**:Mavis(主 agent)—— 编码 + 写文档 + 审 PR
- **部署**:Mavis 代(沙箱编译,你本机部署)
- **运维**:无人(单机,自己维护)

---

## 🆘 紧急情况

- 后端崩了 → `docs/TEAM-ARCHIVE.md` § 11
- 数据库连不上 → § 11.3
- LLM 限速 → 切 `llm.provider=mock`
- 推不上去 → § 11.5(SSH key 检查)

---

## 📜 文档演进历史

| 时间 | 事件 |
|---|---|
| 2026-06-05 | v1 架构(双机主备,RAG 纠结) |
| 2026-06-06 | v2 架构(定位单机轻量) |
| 2026-06-07 | v3 架构定稿(智谱 + 不向量化) |
| 2026-06-07 | M0 端到端跑通(浏览器 UP) |
| 2026-06-08 上午 | M1 档案 CRUD 完工 |
| 2026-06-08 下午 | M2 知识库问答框架 + 修复 |
| 2026-06-08 傍晚 | 业务需求 v1 + 8 份核心文档 + 6 个 plan |
| 2026-06-08 晚 | **仓库扁平化重构**(`39b18ed`)+ 本 README 重写 |

---

*任何新发现的踩坑、配置、流程,加到对应的 `docs/` 文档,标日期。*
