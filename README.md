# projects-online

> 在线项目集合 / Gitee `frisker/projects-online` 的本地工作仓库
> 当前活跃项目:**投委会档案管理系统**(v1.0 → v2.0 阶段)

---

## ⚡ 接手必读(给新来的开发者 / Agent)

**先读这 3 份**(按顺序):

1. **`docs/REQUIREMENTS-v1.md`** — 业务需求,搞清楚系统做什么 / 不做什么
2. **`docs/ARCHITECTURE-v2.md`** — 架构方案,在 M0~M2 基础上怎么增补
3. **`docs/TEAM-ARCHIVE.md`** — 环境 / 部署 / 沙箱 / 数据库 / 账户 / 紧急情况

**再读这 2 份**(开发规范):

4. **`docs/DEV-STANDARDS.md`** — 命名 / 注释 / 安全 / Git / 测试 / 交付规范
5. **`docs/LESSONS-LEARNED.md`** — 踩坑列表(必读,避免重蹈覆辙)

**想开工?读这 1 份**:

6. **`.mavis/plans/plan-A-phase0-fixes.md`** — 第一个要做的 plan(6 个 P0 修复)

---

## 📂 仓库结构

```
projects-online/
├── README.md                            # 本文件(接手入口)
├── .gitignore                           # 含 .ssh/ 排除
├── .ssh/                                # [沙箱] 持久化 SSH deploy key
│
├── investment-committee-archive-system/ # ⭐ 投委会档案系统
│   ├── backend/                         # Spring Boot 3.3 后端
│   ├── frontend/                        # Vue 3 + TypeScript 前端
│   ├── deploy/caddy/                    # Caddy 反向代理
│   ├── docs/                            # 8 份核心文档(见下)
│   ├── architecture-v3-final.md         # 已废止的 v3 方案(保留作历史)
│   ├── SUPPLEMENTARY-REQUIREMENTS.md    # 597 行 P0~P4 缺陷清单
│   └── README* 等
│
└── .mavis/plans/                        # ⭐ 施工 plan(6 个,见下)
    ├── plan-A-phase0-fixes.md           # P0 阻塞修复(第一步)
    ├── plan-B-phase1-arch-fixes.md      # P1 架构修复
    ├── plan-C-phase2-business-core.md   # P2 业务核心
    ├── plan-D-phase2-5-ux.md            # P2.5 UX 增强
    ├── plan-E-phase3-rbac-dict-ui.md    # P3 权限/字典/UI
    └── plan-F-phase4-test-polish.md     # P4 测试/收尾
```

## 📚 完整文档清单(8 份)

| 文档 | 行数 | 用途 |
|---|---|---|
| [`docs/REQUIREMENTS-v1.md`](./investment-committee-archive-system/docs/REQUIREMENTS-v1.md) | 872 | 业务需求,12 章节 |
| [`docs/ARCHITECTURE-v2.md`](./investment-committee-archive-system/docs/ARCHITECTURE-v2.md) | 685 | 架构方案,Provider/Engine 层 |
| [`docs/DB-SCHEMA-v2.md`](./investment-committee-archive-system/docs/DB-SCHEMA-v2.md) | 1060 | 数据库 v2(可执行 SQL) |
| [`docs/SIMILAR-PRODUCTS.md`](./investment-committee-archive-system/docs/SIMILAR-PRODUCTS.md) | 256 | 6 类 22 个产品调研 |
| [`docs/ARCH-REUSE-AUDIT.md`](./investment-committee-archive-system/docs/ARCH-REUSE-AUDIT.md) | 219 | M0~M2 沿用评估 |
| [`docs/DEV-STANDARDS.md`](./investment-committee-archive-system/docs/DEV-STANDARDS.md) | 466 | 开发标准 + 交付规范 |
| [`docs/TEAM-ARCHIVE.md`](./investment-committee-archive-system/docs/TEAM-ARCHIVE.md) | 458 | 团队档案 |
| [`docs/LESSONS-LEARNED.md`](./investment-committee-archive-system/docs/LESSONS-LEARNED.md) | 持续更新 | 踩坑记录 |

## 🚀 施工 plan(6 个,按序)

| Plan | 内容 | 工作量 | 互斥 |
|---|---|---|---|
| **A** | P0 阻塞修复(6 项) | 30-45 分钟 | 必须先做 |
| **B** | P1 架构修复(4 项) | 1-2 小时 | A 完后 |
| **C** | P2 业务核心(LLM+Engine+实体) | 1-2 周 | B 完后 |
| **D** | P2.5 UX(批量上传+智能摘要) | 半天-1 天 | C 完后 |
| **E** | P3 权限/字典/管理 UI | 1-2 天 | C/D 完后 |
| **F** | P4 测试/治理/收尾 | 1-2 天 | 全部完后 |

每个 plan 都**自包含**:含必读清单 + 范围 + 验收 + 提交规范 + 交回物。

---

## 📋 项目当前状态(2026-06-08)

| 阶段 | 状态 | 备注 |
|---|---|---|
| M0 基建(登录) | ✅ 跑通 | 浏览器端到端验证过 |
| M1 档案 CRUD | ✅ 跑通 | 项目/议案/材料/版本 |
| M2 知识库问答 | ✅ 跑通 | MySQL FULLTEXT + 智谱 GLM |
| v2.0 业务扩展 | 🟡 准备中 | 8 份文档已交付,等开工 |
| v2.0 测试/收尾 | ⚪ 未启动 | 依赖前序完成 |

**已落代码**:commits `5bb2439`(M0 末尾)→ `1528ed1`(本批次文档),**全部在 `minimax` 分支**。

## 🌿 Git 分支约定

| 分支 | 用途 | 谁能推 |
|---|---|---|
| `main` | 生产分支(只读) | **只走 PR** |
| `minimax` | 集成/开发(活跃) | dev 直接 push |
| `feature/*` | 单功能分支 | dev |

**任何 push 都到 `minimax`,main 走 PR。**

## 🔑 账户 / 凭证(本地需要)

不在仓库里,本地开发者自己申请 / 配置:
- **Gitee 账号** `frisker`(已有 user account key)
- **智谱 GLM-4-Flash API key**(免费,https://open.bigmodel.cn/)
- **MySQL root 密码**(本机已有,写在 `D:\archive\config\config.json`)
- **JWT secret**(用 `openssl rand -base64 32` 生成)

详细配置见 `docs/TEAM-ARCHIVE.md` § 3.3 + `docs/DEV-STANDARDS.md` § 3.3。

---

## 🛠 快速开始(本机 5 步)

```powershell
# 1. 拉仓库(假设已有 Gitee 账号 key)
git clone git@gitee.com:frisker/projects-online.git
cd projects-online
git checkout minimax

# 2. 后端跑起来
cd investment-committee-archive-system\backend
mvn clean package -DskipTests
# 复制 target\archive.jar 到 D:\archive\apps\backend\
.\startup.ps1

# 3. 前端跑起来
cd ..\frontend
npm install
npm run dev
# 浏览器开 http://localhost:5173

# 4. 数据库(已有 archive_db,跑 v2-schema 升级)
mysql -u root -p archive_db < investment-committee-archive-system\backend\src\main\resources\db\migration\v2-schema.sql

# 5. 配置
# 复制 config.example.json 到 D:\archive\config\config.json
# 填 glm.apiKey + jwt.secret
```

## 📞 找谁

- **业务方**:frisker(投委会秘书 / 项目经理)
- **开发主**:Mavis(主 agent,接管所有开发 + 文档)
- **运维**:无人(单机,Mavis 代)

---

## 🆘 紧急情况

- 后端崩了 → `docs/TEAM-ARCHIVE.md` § 11
- 推不上去 → `docs/TEAM-ARCHIVE.md` § 11.5
- 数据库连不上 → `docs/TEAM-ARCHIVE.md` § 11.3
- LLM 限速 → 切 `llm.provider=mock`

---

*最后更新:2026-06-08 / commit `1528ed1` / 阶段:v2.0 启动准备*
