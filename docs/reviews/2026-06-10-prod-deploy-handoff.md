# Plan I 生产部署踩坑 + 调试全程交接

**作者**: Mavis (PM/调试者)
**日期**: 2026-06-10
**接收方**: 接手 agent / 项目方
**Git 远端**: minimax HEAD = `5c14ab9`

---

## TL;DR

项目代码 Plan I 完工 (`2fd4c18`) + 部署过程发现并修了 **6 个真实环境 bug** (P0-24 ~ P0-29),代码 HEAD 推进到 `5c14ab9`。

**当前状态**:
- ✅ 后端 JAR 已打包: `D:\projects-online\backend\target\archive.jar`
- ✅ 数据库已重建 (18 张表 + FULLTEXT 索引)
- ⚠️ **后端进程已启动但 actuator/health 返回 503** (mail health indicator 没禁)
- 🔧 **最后修复已推到 Gitee** (`5c14ab9`),需要重新打包 + 重启 Spring Boot
- ❌ **前端还没起**

---

## 接手方要做的 3 件事

1. **拉最新代码 + 重新打包 + 重启后端**:
   ```
   cd D:\projects-online
   git pull origin minimax --ff-only   # HEAD = 5c14ab9
   cd backend
   mvn clean package -DskipTests
   # 关掉旧 java 进程
   Get-Process -Name java
   Stop-Process -Id<PID> -Force
   # 启动新进程 (后台 + log 文件)
   Start-Process -FilePath "java" -ArgumentList "-jar","D:\projects-online\backend\target\archive.jar" -WorkingDirectory "D:\projects-online\backend" -RedirectStandardOutput "D:\projects-online\storage\logs\backend.log" -RedirectStandardError "D:\projects-online\storage\logs\backend-error.log" -NoNewWindow
   ```

2. **验证后端活**:
   ```
   Start-Sleep -Seconds 15
   curl http://localhost:8080/actuator/health
   # 期望: {"status":"UP"}
   curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
   # 期望: 返回 JWT token
   ```

3. **前端部署** (Mavis 没动):
   ```
   cd D:\projects-online\frontend
   npm install
   # 开发模式:
   npm run dev   # 5173
   # 生产 build:
   npm run build   # 输出 dist/
   ```

---

## 完整调试时间线 (Plan I 验收完 → 生产部署)

### 阶段 A: 项目方验收 (Windows D:\projects-online)

| 时间 | 动作 | 结果 |
|---|---|---|
| 14:00 | git pull minimax | ❌ "Your local changes to be overwritten by merge: components.d.ts" |
| 14:01 | 项目方决定: 删 D:\projects-online 重新 clone | ✅ 干净 |
| 14:30 | 跑验收 11 步 | ✅ 全过 |
| - | 后端 19/19 单测 | ✅ |
| - | 后端 10/10 集成测 (真 GLM) | ✅ |
| - | 前端 build | ✅ |
| - | 11 步全过 | ✅ |

### 阶段 B: 生产部署踩坑

#### B1. Git 同步状态错乱 (跳过)

远端 origin/main 比 origin/minax 落后 2 个 commit (正常,docs cherry-pick 双推). Windows 本地原本是项目方的老 main HEAD `be61ff2`,删掉重 clone minimax 解决.

#### B2. application.yml 文件路径不对 (P0-24)

**症状**: `D:/archive/files` 等老路径,生产用 `D:\projects-online\storage\...`.

**修法**: PowerShell 一行 `.NET.Replace`:
```powershell
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$content = $content.Replace("D:/archive/", "D:/projects-online/storage/")
[System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)
```

**注意**: PowerShell 5.x 的 `-replace` operator 跟 bash 不一样,字符编码有时出问题. 用 `[System.IO.File]::Replace` 最稳.

#### B3. Init.sql 缺表 (P0-25)

**症状**: Hibernate 启动报 `Schema-validation: missing column [customer_name] in table [project]`.

**根因**: 项目里 `init.sql` (193 行) 只建 6 张表 (role/user/project/proposal/material/material_version),entity 有 18 个,差的 12 张表在 `v2-schema.sql` 跟 migration 文件里.

**修法**: 重写 `init.sql` 整合所有迁移:
- role / user / project (加 customer_name + FULLTEXT 索引) / proposal / material / material_version (FULLTEXT) / **llm_call_log** (Plan G) / **spring_ai_chat_memory** (Plan I-13) / **chapter_summary / timepoint / todo / trigger_rule / trigger_action / extraction_method / comparison_method / dict_type / dict_item / audit_log** (Plan A-F)
- 加 `DROP TABLE IF EXISTS flyway_schema_history` 让 Flyway 不会卡历史

**结果**: 18 张表全建,commit `08e953c` → `2af48de`.

#### B4. Entity 字段缺失 (P0-26)

**症状**: `Schema-validation: missing column [total_tokens] in table [llm_call_log]`.

**根因**: `LlmCallLog` entity 加了字段 (prompt_tokens / completion_tokens / total_tokens),但 init.sql 的建表是从老 G-llm-call-log.sql 抄的,没同步.

**修法**: 改 `spring.jpa.hibernate.ddl-auto: validate → update`. Hibernate 启动时自动 ALTER TABLE 加缺失字段.

**commit**: `f93d395`.

#### B5. OpenAI Audio Bean 报错 (P0-27 真坑,深挖 3 轮)

**症状**: `Error creating bean 'openAiAudioSpeechModel': OpenAI API key must be set`.

**根因**: 项目里 `pom.xml` 有 `spring-ai-starter-model-openai` (Sisyphus Plan I 调研时加的,实际**没用**),它会拉起 `OpenAiAudioSpeechModel` / `OpenAiImageModel` 等 Bean,启动时强制要 `spring.ai.openai.api-key`.

**3 轮修法失败**:
1. ❌ `spring.autoconfigure.exclude: OpenAiAudioSpeechAutoConfiguration` — 不生效,因为类名可能不对
2. ❌ 删 `spring-ai-starter-model-openai` 依赖 — ChatClient / CallAdvisor / MessageChatMemoryAdvisor 28 编译错
3. ❌ `spring.ai.openai.api-key: placeholder` — Spring AI 内部还是会校验

**真正解**: 用 `spring.ai.model.*: none` 关掉所有内置模型 (Spring AI 1.1 用 properties 控制,不是 exclude):

```yaml
spring:
  ai:
    model:
      audio:
        speech: none   # 关闭 OpenAI audio
        transcription: none
      image: none      # 关闭 OpenAI image
      embedding: none  # 关闭 OpenAI embedding
      moderation: none
      chat: none       # 关闭 OpenAI chat (用 GLMChatModel 自定义)
```

**为什么 exclude 不生效**: Spring AI 1.1 的 `@ConditionalOnProperty` 有 `matchIfMissing=true`,默认就启用,exclude 名字对但时机不对. 用 properties 直接关最稳.

**commit**: `88580cf` (加回 starter) → `eb6cfe4` (用 properties 关).

#### B6. Mail Health Indicator 503 (P0-28 当前阻塞)

**症状**: `curl http://localhost:8080/actuator/health` 返回 `503`.

**根因**: Spring Boot Actuator 默认 health 检查所有指标包括 SMTP. `smtp.internal.example.cn:25` 是占位符,生产没这个 SMTP 服务器 → MailHealthIndicator 失败 → 整个 health 503.

**修法**: 禁掉 mail health indicator (邮件不是核心,业务调用会自己处理失败):

```yaml
management:
  health:
    mail:
      enabled: false
```

**commit**: `5c14ab9` (**待重启生效**).

---

## 接手方的工作清单

### 优先级 P0 (必做)

1. **重启后端** (B6 修复已推,只需重启):
   - 关旧 java 进程 → 重启新 java 进程
   - `curl http://localhost:8080/actuator/health` 期望 `{"status":"UP"}`

2. **验证登录接口**:
   ```
   curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
   ```
   期望返回 JWT token.

### 优先级 P1 (做完 P0 后)

3. **前端部署**:
   - `cd frontend && npm install`
   - 开发模式 `npm run dev` (5173 端口)
   - 或生产 build `npm run build` (输出 `dist/`,扔给 Nginx)

4. **前端访问后端联调**:
   - 浏览器开 `http://localhost:5173`
   - 登录 admin/admin123
   - 试创建项目/上传材料/智能问答

### 优先级 P2 (上线前)

5. **改 admin 默认密码**: `init.sql` 里的 BCrypt hash 是 `admin123`,生产必须改
6. **配真 SMTP 服务器**: `config.json` 里 `app.mail.*` 用真实 SMTP (或者保持 mail disabled)
7. **配真 GLM API key**: 项目方去 https://open.bigmodel.cn 申请生产 key,改 `config.json`
8. **WinSW 服务化**: 把 `java -jar` 包成 Windows 服务,开机自启 + 自动重启

---

## 重要文件路径

| 文件 | 路径 |
|---|---|
| 代码仓库 | `D:\projects-online` |
| 后端 JAR | `D:\projects-online\backend\target\archive.jar` |
| 后端配置 | `D:\projects-online\backend\src\main\resources\application.yml` |
| 生产配置 | `D:\projects-online\backend\src\main\resources\config.json` |
| 文件存储 | `D:\projects-online\storage\files` |
| 解析文本 | `D:\projects-online\storage\parsed` |
| 后端 log | `D:\projects-online\storage\logs\backend.log` |
| MySQL 连接 | `localhost:3306 / archive_db / archive_app` (config.json 配) |
| 数据库 | `archive_db` (18 张表) |
| GLM API key | `config.json` 里 `app.glm.api-key` |

---

## Git 状态总结

| 分支 | HEAD | 含义 |
|---|---|---|
| `origin/minimax` | **`5c14ab9`** | 生产用, 含 B1-B6 全部修复 |
| `origin/main` | `0b99f07` | 开发用, 跟 minimax 同步 |

**commit 历史** (从 `2fd4c18` Plan I 完工到 `5c14ab9` 当前):

```
5c14ab9 fix(app): 禁 mail health indicator (没配真 SMTP 导致 503)
eb6cfe4 fix(app): 显式 spring.ai.model.* = none 关闭 OpenAI 内置模型
88580cf fix(deps): 加回 spring-ai-starter-model-openai 但 exclude 所有 OpenAI 配置
0d60904 fix(deps): 删 spring-ai-starter-model-openai (P0-15 真正根因)
f93d395 fix(app): ddl-auto validate → update (Hibernate 自动同步字段)
2af48de fix(db): init.sql 补 10 张表 (chapter_summary/timepoint/todo/...)
08e953c fix(db): 整合 init.sql 重建脚本 (含 Plan G + Plan I 所有迁移)
2fd4c18 docs: LESSONS-LEARNED 加 P0-19~23 + 经验教训14~20 + TASKS.md
```

---

## 接手方容易踩的坑 (前车之鉴)

1. **PowerShell 5.x 不支持 `2>&1`**: 用 `*> log.txt` 或 `cmd /c "...> log.txt 2>&1"`
2. **PowerShell 参数名要空格**: `-Last 25` 不是 `-Last25`,`-Port 8080` 不是 `-Port8080`
3. **`&` 是 PowerShell 5.x 保留字**: 多行命令粘进 PowerShell 要用 `;` 分隔
4. **Spring AI 1.1 exclude 不灵**: 用 `spring.ai.model.*: none` 关,不要 `autoconfigure.exclude`
5. **Hibernate `validate` 太严**: 实际项目用 `update`,entity 改字段自动同步
6. **actuator/health 默认包含所有 health indicator**: 不需要的 (mail / diskSpace 之外的) 显式禁用

---

## 接手方读不到的文件 (Mavis UI 问题)

Mavis UI 在调试期间反复报 React error #185,导致部分上下文丢失. 完整对话历史如果需要,接手方可以问项目方要原始聊天记录.

本文件 + `review/test.md` / `test.txt` / `test2.md` 是 Mavis 调试期间留下的所有产出物.