# v2.0 端到端测试任务清单

> **目标**:验证 v2.0 全部功能在本机能跑通,从后端到前端到端到端.
> **预计时间**:2-4 小时(视环境)
> **谁**:用户 或 接手 AI

---

## 0. 前置条件

- [x] JDK 17 / Maven 3.8+ / Node 18+ / MySQL 8.0+
- [x] MySQL 已有 `archive_db` + 全量表(role/user/project/proposal/material/material_version 等)
- [x] `config.json` 已配置(GL API key / DB 密码 / JWT secret)
- [x] 后端 8080 端口可用
- [x] `D:\archive\` 目录结构完整(apps/config/logs/files/parsed)

---

## 1. 拉最新代码 & 构建

```powershell
cd D:\projects_new\projects-online
git pull origin minimax

# 后端构建
cd backend
mvn clean package -DskipTests

# 前端构建
cd ..\frontend
npm install
npm run build
```

**预期**:
- `mvn clean package` → BUILD SUCCESS
- `npm run build` → 0 错误

---

## 2. 后端启动 + 健康检查

```powershell
cd D:\projects_new\projects-online\backend
.\startup.ps1
# 或: java "-Dfile.encoding=UTF-8" -jar .\target\archive.jar
```

**预期看到**:
```
Tomcat started on port 8080 (http)
Started ArchiveApplication in X.XXX seconds
```

**健康检查**:
```powershell
curl http://localhost:8080/api/health
```
→ `{"code":0,"message":"ok","data":{"status":"UP",...}}`

**登录获取 token**:
```powershell
$loginResp = curl.exe -s -X POST -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$token = ($loginResp | Select-String -Pattern '"token":"([^"]+)"').Matches.Groups[1].Value
Write-Host "Token: $token"
```

---

## 3. 核心业务 API 测试

### 3.1 项目 CRUD

```powershell
# 创建项目
curl.exe -s -X POST -Uri http://localhost:8080/api/projects -ContentType "application/json" -Body '{"code":"PRJ-TEST-001","name":"端到端测试项目","category":"股权类","amountWan":50000,"summary":"v2 端到端测试","status":"草稿"}' -Headers @{"Authorization"="Bearer $token"}

# 列表
curl.exe -s -X GET -Uri "http://localhost:8080/api/projects?page=0&size=10" -Headers @{"Authorization"="Bearer $token"}

# 详情
curl.exe -s -X GET -Uri "http://localhost:8080/api/projects/1" -Headers @{"Authorization"="Bearer $token"}

# 修改
curl.exe -s -X PUT -Uri "http://localhost:8080/api/projects/1" -ContentType "application/json" -Body '{"name":"端到端测试项目-改","category":"债权类"}' -Headers @{"Authorization"="Bearer $token"}

# 删除
curl.exe -s -X DELETE -Uri "http://localhost:8080/api/projects/1" -Headers @{"Authorization"="Bearer $token"}
```

### 3.2 议案 CRUD

```powershell
# 先创建项目(如果 3.1 删了)
curl.exe -s -X POST -Uri http://localhost:8080/api/projects -ContentType "application/json" -Body '{"code":"PRJ-TEST-002","name":"议案测试项目","category":"股权类","amountWan":10000,"summary":"测试"}' -Headers @{"Authorization"="Bearer $token"}

# 创建议案
curl.exe -s -X POST -Uri http://localhost:8080/api/proposals -ContentType "application/json" -Body '{"code":"PROP-TEST-001","title":"端到端测试主体议案","projectId":2,"type":"主体","status":"草稿","summary":"测试"}' -Headers @{"Authorization"="Bearer $token"}

# 列表 & 详情
curl.exe -s -X GET -Uri "http://localhost:8080/api/proposals?projectId=2" -Headers @{"Authorization"="Bearer $token"}
```

### 3.3 材料 CRUD + 上传 + 解析

```powershell
# 创建材料
curl.exe -s -X POST -Uri http://localhost:8080/api/materials -ContentType "application/json" -Body '{"proposalId":1,"title":"尽调报告","category":"尽调报告","status":"草稿"}' -Headers @{"Authorization"="Bearer $token"}

# 创建测试文件
"第一章 项目概况`n本项目为端到端测试项目`n`n第二章 财务数据`n收入 5000 万,利润 1000 万" | Out-File -FilePath D:\test-e2e.txt -Encoding UTF8

# 上传版本
curl.exe -s -X POST -Uri "http://localhost:8080/api/materials/1/versions" -Headers @{"Authorization"="Bearer $token"} -Form "file=@D:\test-e2e.txt" -Form "changeNote=v2 测试上传"

# 查看版本
curl.exe -s -X GET -Uri "http://localhost:8080/api/materials/1/versions" -Headers @{"Authorization"="Bearer $token"}

# 查看章节
curl.exe -s -X GET -Uri "http://localhost:8080/api/materials/1/versions/1/sections" -Headers @{"Authorization"="Bearer $token"}
```

### 3.4 知识库问答

```powershell
curl.exe -s -X POST -Uri http://localhost:8080/api/qa -ContentType "application/json" -Body '{"question":"端到端测试项目的财务数据是什么?","projectId":2}' -Headers @{"Authorization"="Bearer $token"}
```

**预期**:返回包含"收入 5000 万,利润 1000 万"的答案(基于上传材料的解析内容)。

---

## 4. 字典管理

```powershell
# 创建字典类型
curl.exe -s -X POST -Uri http://localhost:8080/api/dict-types -ContentType "application/json" -Body '{"typeCode":"TEST_TYPE","typeName":"测试类型","description":"端到端测试"}' -Headers @{"Authorization"="Bearer $token"}

# 创建字典项
curl.exe -s -X POST -Uri http://localhost:8080/api/dict-items -ContentType "application/json" -Body '{"typeCode":"TEST_TYPE","itemCode":"ITEM_001","itemName":"测试项1","sortOrder":1}' -Headers @{"Authorization"="Bearer $token"}

# 列表
curl.exe -s -X GET -Uri "http://localhost:8080/api/dict-types" -Headers @{"Authorization"="Bearer $token"}
curl.exe -s -X GET -Uri "http://localhost:8080/api/dict-items/TEST_TYPE" -Headers @{"Authorization"="Bearer $token"}
```

---

## 5. 触发规则

```powershell
# 创建触发规则
curl.exe -s -X POST -Uri http://localhost:8080/api/trigger-rules -ContentType "application/json" -Body '{"name":"端到端测试触发","eventType":"UPLOAD","conditionExpr":"true","actionType":"CREATE_TODO","actionConfig":"{\"title\":\"端到端测试待办\"}"}' -Headers @{"Authorization"="Bearer $token"}

# 上传文件触发
curl.exe -s -X POST -Uri "http://localhost:8080/api/materials/1/versions" -Headers @{"Authorization"="Bearer $token"} -Form "file=@D:\test-e2e.txt" -Form "changeNote=触发测试"

# 查看待办
curl.exe -s -X GET -Uri "http://localhost:8080/api/todos" -Headers @{"Authorization"="Bearer $token"}
```

---

## 6. 对比 + 抽取 Engine

```powershell
# 创建对比方法
curl.exe -s -X POST -Uri http://localhost:8080/api/comparison-methods -ContentType "application/json" -Body '{"name":"端到端对比","description":"测试对比","category":"立项-申请","dimensions":"[\"投资金额\",\"期限\"]"}' -Headers @{"Authorization"="Bearer $token"}

# 创建抽取方法
curl.exe -s -X POST -Uri http://localhost:8080/api/extraction-methods -ContentType "application/json" -Body '{"name":"端到端抽取","description":"测试抽取","category":"财务数据","fields":"[\"收入\",\"利润\"]"}' -Headers @{"Authorization"="Bearer $token"}
```

---

## 7. 审计日志

```powershell
curl.exe -s -X GET -Uri "http://localhost:8080/api/audit-logs?page=0&size=20" -Headers @{"Authorization"="Bearer $token"}
```

**预期**:看到之前操作产生的审计日志(创建项目/议案/材料等)。

---

## 8. RBAC 权限测试

```powershell
# 无 token 访问 → 401
curl.exe -s -X GET -Uri "http://localhost:8080/api/projects"
```

**预期**:`401 Unauthorized`

---

## 9. 前端 UI 测试

**浏览器开 `http://localhost:5173`**,登录 `admin / admin123`。

| 步骤 | 期望 |
|---|---|
| 左侧菜单:项目管理/知识库/参数管理/审计日志 | ✅ 全部可见 |
| 点"项目管理" → 列表页 | ✅ 正常渲染 |
| 新建/编辑/详情/删除 项目 | ✅ 全部可用 |
| 议案子表:增删改 | ✅ |
| 材料:上传/版本/章节 | ✅ |
| 点"知识库" → 问答页 | ✅ 正常渲染 |
| 输入问题 → 获取答案 | ✅ |
| 点"参数管理" → 字典/抽取/对比/触发 | ✅ admin 可见 |
| 管理员能看到全部菜单 | ✅ |
| 非 admin 看不到参数管理 | ✅ |

---

## 10. 性能验证

| 指标 | 目标 | 验证方式 |
|---|---|---|
| 首页加载 | < 2s | 浏览器 DevTools Network |
| 列表 API P95 | < 500ms | `curl -w '%{time_total}'` |
| 知识库问答 | < 3s | 浏览器实测 |
| 登录限流 | 5 次失败后 429 | 连续 6 次错误密码 |

---

## 11. 验证清单

### 后端
- [ ] `mvn clean package` BUILD SUCCESS
- [ ] `Tomcat started on port 8080`
- [ ] `Started ArchiveApplication`
- [ ] `/api/health` → UP
- [ ] `/api/auth/login` → token

### API
- [ ] 项目:创建/列表/详情/修改/删除
- [ ] 议案:CRUD(关联项目)
- [ ] 材料:CRUD + 上传 + 版本 + 章节 + 下载
- [ ] 知识库问答:10 个问题能答
- [ ] 字典:类型/项 CRUD
- [ ] 触发规则:创建 + 文件上传触发待办
- [ ] 对比方法 CRUD
- [ ] 抽取方法 CRUD
- [ ] 审计日志列表
- [ ] 重复 code → 报错
- [ ] 不带 token → 401
- [ ] 登录限流:5 次失败后 429
- [ ] actuator 端点可达(/actuator/health, /actuator/metrics)

### 前端
- [ ] 菜单完整:项目管理/知识库/参数管理/审计日志
- [ ] 项目 CRUD 页面工作
- [ ] 议案/材料子表单工作
- [ ] 上传/版本/章节弹窗工作
- [ ] 知识库问答页面工作
- [ ] 参数管理(admin)工作
- [ ] 浏览器 F12 控制台无 JS 错误
- [ ] `npm run build` 0 错误

---

## 12. 完成后

全过 → 告知开发者:
> **"v2.0 端到端验证全过"**

有 bug → 贴具体报错:
> **"v2.0 验证 X 项失败,报错:[报错信息]"**
