# GLM API Key 配置手册 (Agent 跑通指南)

> **目的**: 让接手 AI / 沙箱能在拿到 GLM API key 后, 1 分钟配好环境, 跑通 Plan I 13 任务.
>
> **沙箱里跑不通的原因**: 没 `GLM_API_KEY` 环境变量, `OpenAiChatModel` 启动失败, `AgentIntegrationTest` 报错
> **生产环境能跑通的原因**: 项目方 `frisker` 已有智谱 GLM API key

---

## 0. 拿 GLM API Key

**没 key?** 项目方账号在 `https://open.bigmodel.cn/usercenter/apikeys`, 联系项目方 `frisker` 拿

**自己有 key?** 直接跳 §1

---

## 1. 配环境变量

### Windows (项目方生产环境, 主用)

**方式 A** (PowerShell 永久):
```powershell
# 管理员 PowerShell
[System.Environment]::SetEnvironmentVariable("GLM_API_KEY", "your-key-here", "User")

# 验证
$env:GLM_API_KEY
# 应输出: your-key-here
```

**方式 B** (临时, 当前 session):
```powershell
$env:GLM_API_KEY = "your-key-here"
```

**方式 C** (项目方标准做法, `config.json`):
```json
{
  "app": {
    "glm": {
      "api-key": "your-key-here",
      "chat-url": "https://open.bigmodel.cn/api/paas/v4/chat/completions",
      "vision-url": "https://open.bigmodel.cn/api/paas/v4/vision",
      "chat-model": "glm-4-flash",
      "vision-model": "glm-4v"
    }
  }
}
```
文件位置: `D:\archive\apps\config\config.json` (生产)
或: `<项目根>/config.json` (开发, 由 ConfigJsonLoader 加载)

### Linux / 沙箱 (Mavis 沙箱)
```bash
export GLM_API_KEY="your-key-here"
# 持久化 (写到 ~/.bashrc)
echo 'export GLM_API_KEY="your-key-here"' >> ~/.bashrc
```

---

## 2. 验证 key 配好

### 后端
```bash
cd /workspace/projects-online-clone
source tools/env.sh  # 加载 JDK 17 + Maven

# 跑后端
cd backend
mvn spring-boot:run
# 应启动到 8080 端口
```

启动日志里看到 `Started ArchiveApplication in X.XXX seconds` = OK

### 测试 (不依赖 GLM)
```bash
mvn test -Dtest='!AgentIntegrationTest'
# 应过 19/19 测例
```

### 集成测试 (依赖 GLM)
```bash
mvn test -Dtest=AgentIntegrationTest
# 应过 10/10 测例 (search_fulltext / find_project / query_mysql 等)
```

---

## 3. 跑 Agent 端到端

### 启动后端
```bash
cd backend && mvn spring-boot:run
# 等 30s 启动
```

### 启动前端
```bash
cd frontend && npm run dev
# 跑 http://localhost:5173
```

### 浏览器测试
1. 打开 http://localhost:5173
2. 登录 (admin / admin123)
3. 进 "智能问答" 页面
4. 输入 "新能源项目风险点"
5. 期望: 看到 Agent ReAct 步骤 (find_project → search_fulltext → FINAL_ANSWER)

### curl 测试
```bash
curl -X POST http://localhost:8080/api/qa/ask \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt>" \
  -d '{"question": "新能源项目风险点", "topN": 10, "rerank": true}'
```

期望响应包含:
- `answer`: LLM 生成的回答
- `agentMode`: true
- `steps`: 5 步 ReAct 循环
- `toolCalls`: ≥ 1

---

## 4. 故障排查

### 问题 1: `Failed to load ApplicationContext`
- **原因**: `GLM_API_KEY` 没配
- **修法**: 检查 `echo $GLM_API_KEY` (Linux) 或 `$env:GLM_API_KEY` (PowerShell)
- **项目方**: 检查 `D:\archive\apps\config\config.json` 里 `app.glm.api-key` 字段

### 问题 2: `Connection timeout` 调智谱
- **原因**: 沙箱不能访问外网
- **修法**: 沙箱不能真跑 Agent 端到端, 只能跑 unit tests + compile

### 问题 3: `AgentIntegrationTest` 全 error
- **原因**: 跟问题 1 一样, 单元测试需要 Spring 上下文, ChatClient 需要真 key
- **修法**: 配好 key 再跑, 或排除该测试 `mvn test -Dtest='!AgentIntegrationTest'`

### 问题 4: 前端 "智能问答" 页面 401
- **原因**: JWT 过期 / 没带
- **修法**: 重新登录拿新 token, 检查 `frontend/src/api/http.ts` 的 `Authorization` header

---

## 5. 跑通后的验证清单

- [ ] `mvn compile` 0 错
- [ ] `mvn test` (排除 AgentIntegrationTest) 19/19 过
- [ ] `mvn test -Dtest=AgentIntegrationTest` 10/10 过
- [ ] `npm run build` 0 错
- [ ] 后端 8080 启动 OK (`Started ArchiveApplication`)
- [ ] 前端 5173 启动 OK (`vite ready in XXX ms`)
- [ ] 浏览器能登录 + 进 "智能问答" 页面
- [ ] 智能问答返回 Agent 答案 (不是降级路径)
- [ ] llm_call_log 表有 token 用量记录
- [ ] spring_ai_chat_memory 表有多轮对话记录

---

## v1.1 网络查字典 API Key 配置（D-2）

D-2 拍板：v1.1 实施只配 2 候选（百度百科 + 维基百科），金融百科/互动百科留占位。

### 百度百科 API（v1.1 启用）

1. 申请：https://baike.baidu.com/api
2. 配置：`config.json` 的 `archive.networkDict.cacheTtl` 设值
3. 启用：application.yml `archive.network-dict.enabled-sources` 加 `baidu_baike`

### 维基百科 API（v1.1 启用）

1. 申请：https://www.mediawiki.org/wiki/API:Main_page
2. 配置：同上

### 金融百科 / 互动百科（v1.1 停用占位）

D-2 决策：金融百科 / 互动百科留 "已停用" 占位 entry，业务方后续确认出网策略再启用。

---

## 6. 相关文档

- [V2-TEST-TASKS.md](V2-TEST-TASKS.md) — 端到端验收清单
- [DEPLOYMENT-LOG.md](DEPLOYMENT-LOG.md) — 部署日志
- [ENVIRONMENT-DEPENDENCIES.md](ENVIRONMENT-DEPENDENCIES.md) — 环境依赖
- [RUNBOOK.md](RUNBOOK.md) — 运行手册

---

**最后更新**: 2026-06-09 23:40 (Mavis 沙箱)
**维护者**: Mavis
