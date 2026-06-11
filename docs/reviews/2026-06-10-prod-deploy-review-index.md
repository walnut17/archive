# Review 目录 — 调试 / 验收 / 部署踩坑记录

**目的**: 接手方从这里了解项目的历史调试、验收、部署过程,以及当前状态.

---

## 当前测试调试进程 (2026-06-10)

### 代码状态
- **远端 minimax HEAD**: `5c14ab9`
- **远端 main HEAD**: `0b99f07`
- **Plan I 完工 commit**: `2fd4c18` (基础)
- **Plan I 部署阶段修复**: `08e953c` → `f93d395` → `0d60904` → `88580cf` → `eb6cfe4` → `5c14ab9` (6 个 bug)

### 当前阻塞
- **后端已修但需重启** (commit `5c14ab9` 禁 mail health indicator)
- **前端未部署**

### 接手方下一步 (优先级排序)
1. **P0**: 重启后端进程 → 验证 `/actuator/health` 返回 `{"status":"UP"}`
2. **P0**: 验证登录接口 `POST /api/auth/login` 返回 JWT token
3. **P1**: 前端 `cd frontend && npm install && npm run dev`
4. **P1**: 浏览器访问 `http://localhost:5173`,登录 admin/admin123
5. **P2**: 改 admin 默认密码 (生产安全)
6. **P2**: 配真 GLM API key (项目方自己的智谱 key)

**详细调试过程和踩坑分析** → [2026-06-10-prod-deploy-handoff.md](./2026-06-10-prod-deploy-handoff.md)

---

## 文件索引

### 调试记录
- **`2026-06-10-prod-deploy-handoff.md`** — **当前重点** Plan I 部署踩坑全过程 + 6 个 bug 修复 + 接手方工作清单
- **`test.md` / `test.txt` / `test2.md`** — Mavis 调试期间临时记录 (不重要,历史)

### 验收 / Review
- **`../docs/reviews/2026-06-09-plan-i-p0-review.md`** — Plan I 静态 review (5 P0 + 1 P1)
- **`../docs/reviews/2026-06-10-plan-i-10of10-achievement.md`** — Plan I 10/10 测例全过复盘
- **`../docs/ACCEPTANCE-GUIDE.md`** — 项目方 11 步验收 SOP (Mavis 跟项目方已交互式走完)
- **`../docs/LESSONS-LEARNED.md`** — 踩坑大全 (P0-19~23 已加)

---

## 接手方从哪里读

**如果你是接手方,先看这些**:

1. **本 README.md** — 了解当前状态和优先级
2. **`2026-06-10-prod-deploy-handoff.md`** — 看 Mavis 怎么一步步从 Plan I 完工走到生产部署
3. **`../README.md`** — 项目入口
4. **`../TASKS.md`** — 任务分块清单
5. **`../docs/AGENT-FRAMEWORK-DECISION.md`** — 框架决策记录
6. **`../docs/GLM-KEY-SETUP.md`** — 智谱 API key 配置手册

**调试 / 部署期间的原始对话**: 见项目方本地的 Mavis 聊天记录 (Mavis UI 有 React error #185,导致部分上下文丢失,本文件 + 上面列的文档是最权威的记录).