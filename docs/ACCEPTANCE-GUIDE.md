# 项目方验收 SOP — Plan I 完工

**目的**: 一步一步交互式验收,过一步标一步
**作者**: Mavis
**日期**: 2026-06-10
**目标分支**: `minimax` (成品分支, 生产用)

---

## 验收前准备(由项目方/Mavis 跑一次)

### 0. 装 SSH key(沙箱专属,项目方本机不需要)

```bash
mkdir -p /root/.ssh && chmod 700 /root/.ssh
cp /workspace/projects-online-clone/.ssh/config /root/.ssh/config
cp /workspace/projects-online-clone/.ssh/archive_deploy /root/.ssh/
cp /workspace/projects-online-clone/.ssh/archive_deploy.pub /root/.ssh/
chmod 600 /root/.ssh/config /root/.ssh/archive_deploy
chmod 644 /root/.ssh/archive_deploy.pub
```

### 0.1 克隆仓库

```bash
cd /workspace
git clone -b minimax git@gitee.com:frisker/projects-online.git projects-online-accept
cd projects-online-accept
```

---

## 验收步骤(11 步,逐步推进)

### ✅ 步骤 1: 环境加载

**命令**:
```bash
source tools/env.sh
java -version
mvn -version
```

**期望**:
- `java version "17.0.19"` (Temurin JDK 17)
- `Apache Maven 3.9.16`
- aliyun mirror 已配 (看 mvn 输出 `aliyun-central` 字样)

**通过标准**: 3 项都对得上

---

### ✅ 步骤 2: 后端编译

**命令**:
```bash
cd backend
mvn clean compile -B 2>&1 | tail -20
```

**期望**:
- `BUILD SUCCESS`
- 0 编译错误
- 0 警告(理想,可接受少量 deprecation 警告)

**通过标准**: `BUILD SUCCESS`

---

### ✅ 步骤 3: 后端单测(不需要 GLM key,跑得快)

**命令**:
```bash
mvn test -Dtest='*ToolTest' -B 2>&1 | tail -30
```

**期望**:
- `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0` (19 = 6 工具测试,不含 AgentIntegrationTest)
- 各工具测试分别过:
  - AskClarificationToolTest 2/2
  - FindProjectToolTest 3/3
  - GetProjectBusinessDataToolTest 1/1
  - LlmSummarizeToolTest 2/2
  - QueryMysqlToolTest 8/8
  - SearchFulltextToolTest 3/3

**通过标准**: 19/19 全过,0 fail 0 error

---

### ✅ 步骤 4: 后端集成测(需要 GLM key,跑 2 分钟)

**命令**:
```bash
export GLM_API_KEY="<key>"  # Mavis 给你
mvn test -Dtest=AgentIntegrationTest -B 2>&1 | tail -10
```

**期望**:
- `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`
- 总耗时 ~120s (10 测例 × ~10s/测例 = 100s)

**通过标准**: 10/10 全过

**如果 key 失效**: 报错 `智谱 API key 未配置` → 联系 Mavis 重发 key

---

### ✅ 步骤 5: 前端 build

**命令**:
```bash
cd ../frontend
npm install  # 第一次需要,大约 1-2 分钟
npm run build 2>&1 | tail -20
```

**期望**:
- `✓ built in <X>s`
- `dist/` 目录生成
- vue-tsc 0 类型错误
- 警告 (chunk size > 500KB) 可接受,不影响功能

**通过标准**: `built in` 字样 + `dist/` 存在

---

### ✅ 步骤 6: Plan I 13 任务完工核对

**命令**:
```bash
cd ..
cat TASKS.md | grep -A 12 "完工清单"
```

**核对清单**(13 项全 ✅):
- T-I-1: 加 Spring AI 1.1 依赖
- T-I-2: application.yml + OpenAI 协议
- T-I-3: agent 包骨架
- T-I-4: search_fulltext 工具
- T-I-5: find_project 工具
- T-I-6: query_mysql 工具
- T-I-7: get_project_business_data 工具
- T-I-8: ask_clarification + llm_summarize 工具
- T-I-9: AgentEngine 5 步 ReAct
- T-I-10: QaController 改造 + 降级
- T-I-11: 集成测试 10 测例
- T-I-12: 前端 Knowledge.vue + AgentStepsPanel
- T-I-13: 多轮对话

**通过标准**: 13 项全 `✅`

---

### ✅ 步骤 7: 文档完整性核对

**命令**:
```bash
ls -la docs/
ls -la docs/reviews/
wc -l docs/LESSONS-LEARNED.md docs/reviews/*.md docs/GLM-KEY-SETUP.md
```

**期望文档**:
- `docs/AGENT-FRAMEWORK-DECISION.md` (框架决策)
- `docs/ENVIRONMENT-DEPENDENCIES.md` (环境档案)
- `docs/GLM-KEY-SETUP.md` (智谱 key 配置手册)
- `docs/LESSONS-LEARNED.md` (踩坑大全)
- `docs/reviews/README.md` (reviews 索引)
- `docs/reviews/2026-06-09-plan-i-p0-review.md` (静态 review 详情)
- `docs/reviews/2026-06-10-plan-i-10of10-achievement.md` (10/10 复盘)
- `docs/ACCEPTANCE-GUIDE.md` (本文档)
- `README.md` (项目入口)
- `TASKS.md` (任务分块清单)

**通过标准**: 10 份文档全在

---

### ✅ 步骤 8: Git 同步状态核对

**命令**:
```bash
git log --oneline -10
git status
```

**期望**:
- HEAD 在 `minimax` 分支
- 最近 2 个 commit:
  1. `docs: Plan I 10/10 完工复盘 + 5 P0 lessons (P0-19~23)` (e6e0a7b)
  2. `Plan I: 10/10 AgentIntegrationTest 全过 + 5 P0 修 (P0-19~23)` (fd4ff62)
- `git status` 显示 `nothing to commit, working tree clean`

**通过标准**: 分支对、commit 对、working tree clean

---

### ✅ 步骤 9: 数据库 / Flyway 迁移核对

**命令**:
```bash
ls backend/src/main/resources/db/migration/
cat backend/src/main/resources/db/migration/I-find-project-fulltext.sql
cat backend/src/main/resources/db/migration/I-chat-memory.sql
```

**期望**:
- 6 个 migration 文件:G-llm-call-log / I-chat-memory / I-find-project-fulltext / M2-fulltext-index / v2-schema / (其他)
- I-find-project-fulltext.sql: FULLTEXT INDEX on project(name, customer_name)
- I-chat-memory.sql: CREATE TABLE spring_ai_chat_memory

**通过标准**: 2 个 I- 前缀的 migration 都在

---

### ✅ 步骤 10: 6 工具代码核对

**命令**:
```bash
ls backend/src/main/java/com/archive/agent/tool/
```

**期望 6 个工具**:
- `AskClarificationTool.java`
- `FindProjectTool.java`
- `GetProjectBusinessDataTool.java`
- `LlmSummarizeTool.java`
- `QueryMysqlTool.java` (白名单 6 表 + 6 aggregate + 10 operator + 3 重安全加固)
- `SearchFulltextTool.java`

**通过标准**: 6 个 class 都在

---

### ✅ 步骤 11: 业务回归核对(可选,慢)

**命令**:
```bash
cd backend
mvn test -B 2>&1 | tail -20
```

**期望**:
- **总测例 29 全过** (19 单测 + 10 集成测)
- Plan A~G + M0~M2 旧测例无回归

**通过标准**: 29/29 全过,无新失败

---

## 验收完成标准

✅ **11 步全过 + 文档齐全 = 项目方验收通过**

如果任何一步挂:
1. 截图 + log 贴给 Mavis
2. Mavis 跟接手 AI 排查修
3. 修完重跑该步

---

## 验收人签字

- 项目方: _____________ 日期: _______
- Mavis 沙箱: _____________ 日期: _______
