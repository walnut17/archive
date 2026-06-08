# M1 测试任务清单 — 明早执行

> **目标**:验证 M1 全部代码在本地能跑通,从后端到前端到端到端.
> **预计时间**:30-60 分钟(首次可能要 90 分钟遇到 bug 排查)
> **谁**:用户
> **何时**:明天上班(2026-06-08 起)

---

## 0. 前置条件(M0 部署已经跑过)

- [x] JDK 17.0.19 / Maven 3.8.8 / Node 18.20.4 / MySQL 8.0.16
- [x] 后端 8080 端口可用
- [x] MySQL 已有 `archive_db` 库 + `role` / `user` 表(admin/admin123)
- [x] 沙箱 deploy key + 本机 Gitee 账号 key 都配好

---

## 1. 拉最新代码(2 分钟)

```powershell
cd D:\projects-online
git pull origin minimax
```

**预期看到**(Gitee 上 4 个新 M1 commit):

```
a507532 feat(backend,M1-3): Tika 解析 + 章节切分 + 版本管理 + 上传接口
f828ee9 feat(frontend,M1-5): 项目/议案/材料/版本管理 UI
2816e64 feat(backend,M1-2): Project/Proposal/Material API 端点
f2d8c2f feat(backend,M1-1): Project/Proposal/Material/MaterialVersion 实体 + Repository
```

---

## 2. 数据库建表(2 分钟)

**新加 4 张表**(M1-1 引入,不会清掉老数据):

```sql
mysql -u root -p archive_db
```

**依次执行** 4 张表的 `CREATE TABLE`,从 `backend/src/main/resources/db/init.sql` 第 60-160 行附近复制。

或者用 init.sql 里整段执行:

```powershell
mysql -u root -p archive_db < D:\projects-online\investment-committee-archive-system\backend\src\main\resources\db\init.sql
```

⚠️ **注意**:`init.sql` 头部有 `DROP TABLE IF EXISTS ... + CREATE TABLE` 4 张表(如果数据很重要,**别**整段重跑)。

**更安全的做法**:只跑 4 张新表(从 init.sql 里复制 project / proposal / material / material_version 的 CREATE 段)。

**验证 6 张表都存在**:

```sql
SHOW TABLES;
-- 预期:role, user, project, proposal, material, material_version
```

---

## 3. 后端启动 + 健康检查(3 分钟)

```powershell
cd D:\projects-online\investment-committee-archive-system\backend
.\startup.ps1
```

**预期看到**:
```
[1/4] Pulling latest code from Gitee...
OK

[2/4] Building JAR ...
OK

[3/4] Preparing log directory + verifying config.json...
config.json found: D:\archive\config\config.json

[4/4] Starting backend...
Tomcat started on port 8080 (http)
Started ArchiveApplication in X.XXX seconds
```

**没看到最后两行 = 启动失败,把错误贴给 Mavis**。

**新开 PowerShell 跑 healthcheck**:

```powershell
cd D:\projects-online\investment-committee-archive-system\backend
.\healthcheck.ps1
```

**预期看到**:
```
[1/2] GET /api/health
{"code":0,"message":"ok","data":{"status":"UP",...}}

[2/2] POST /api/auth/login
{"code":0,"message":"ok","data":{"token":"eyJ...","userInfo":{...}}}

================================
M0 Backend PASSED!
================================
```

---

## 4. API 后端测试(15 分钟)

**新开 PowerShell,挨个跑下面 curl 测:**

### 4.1 登录拿 token

```powershell
$loginBody = '{"username":"admin","password":"admin123"}'
$loginResp = curl.exe -s -X POST -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $loginBody
$loginResp
```

**预期**:`token:eyJ...`

**保存 token 到变量**:

```powershell
$token = ($loginResp | Select-String -Pattern '"token":"([^"]+)"').Matches.Groups[1].Value
Write-Host "Token: $token"
```

### 4.2 测项目 API

**创建项目**:

```powershell
$projBody = '{"code":"PRJ-001","name":"测试项目A","category":"股权类","amountWan":10000,"summary":"测试项目A","status":"草稿"}'
curl.exe -s -X POST -Uri http://localhost:8080/api/projects -ContentType "application/json" -Body $projBody -Headers @{"Authorization"="Bearer $token"}
```

**预期**:`{"code":0,"message":"ok","data":{...id:1, code:"PRJ-001"...}}`

**列出项目**:

```powershell
curl.exe -s -X GET -Uri "http://localhost:8080/api/projects?page=0&size=10" -Headers @{"Authorization"="Bearer $token"}
```

**预期**:`content` 数组里有刚创建的项目。

**测不通过鉴权(故意不带 token)**:

```powershell
curl.exe -s -X GET -Uri "http://localhost:8080/api/projects"
```

**预期**:`401 Unauthorized` 或 `{"code":40101,...}`

### 4.3 测议案 + 材料 + 上传 + 解析 + 章节

**创建议案**(用项目 id=1):

```powershell
$propBody = '{"code":"PROP-001-A","title":"主体议案","projectId":1,"type":"主体","status":"草稿","summary":"测试"}'
curl.exe -s -X POST -Uri http://localhost:8080/api/proposals -ContentType "application/json" -Body $propBody -Headers @{"Authorization"="Bearer $token"}
```

**预期**:`{"code":0,"data":{...id:1...}}`

**创建材料**:

```powershell
$matBody = '{"proposalId":1,"title":"尽调报告","category":"尽调报告","status":"草稿","description":"v1"}'
curl.exe -s -X POST -Uri http://localhost:8080/api/materials -ContentType "application/json" -Body $matBody -Headers @{"Authorization"="Bearer $token"}
```

**预期**:`{"code":0,"data":{...id:1...}}`

**上传文件**(用 PowerShell 的 `Invoke-WebRequest` 或者 `curl.exe` 都行):

```powershell
# 准备一个测试文件(任意 docx/pdf/txt)
# 如果没现成文件,创建一个简单的 txt:
"第一章 项目概述`n本项目是一个测试项目`n`n第二章 财务数据`n收入 1000 万,利润 200 万" | Out-File -FilePath D:\test.txt -Encoding UTF8

# 上传
curl.exe -s -X POST -Uri "http://localhost:8080/api/materials/1/versions" `
    -Headers @{"Authorization"="Bearer $token"} `
    -Form "file=@D:\test.txt" `
    -Form "changeNote=测试上传"
```

**预期**:`{"code":0,"data":{...versionNo:1, parseStatus:"success", fileSize:..., sha256:...}}`

**等 2-3 秒,看解析状态**:

```powershell
curl.exe -s -X GET -Uri "http://localhost:8080/api/materials/1/versions" -Headers @{"Authorization"="Bearer $token"}
```

**预期**:第 1 个 version 的 `parseStatus = "success"`(说明 Tika 解析成功)。

**看章节切分**:

```powershell
curl.exe -s -X GET -Uri "http://localhost:8080/api/materials/1/versions/1/sections" -Headers @{"Authorization"="Bearer $token"}
```

**预期**:`[{index:0, title:"第一章 项目概述", content:"..."}, {index:1, title:"第二章 财务数据", content:"..."}]`

**测下载**:

```powershell
curl.exe -s -X GET -Uri "http://localhost:8080/api/materials/1/versions/1/download" -Headers @{"Authorization"="Bearer $token"} -o D:\downloaded.txt
Get-Content D:\downloaded.txt
```

**预期**:`downloaded.txt` 内容 = 原始 `test.txt` 内容。

### 4.4 测异常路径

**重复 code 创建项目**(应该失败):

```powershell
$dupBody = '{"code":"PRJ-001","name":"重复","category":"股权类"}'
curl.exe -s -X POST -Uri http://localhost:8080/api/projects -ContentType "application/json" -Body $dupBody -Headers @{"Authorization"="Bearer $token"}
```

**预期**:`{"code":40001,"message":"项目编号已存在: PRJ-001"}` (或类似的错误)

**修改编号**(应该失败):

```powershell
$body = '{"code":"PRJ-999","name":"改编号","category":"股权类"}'
curl.exe -s -X PUT -Uri "http://localhost:8080/api/projects/1" -ContentType "application/json" -Body $body -Headers @{"Authorization"="Bearer $token"}
```

**预期**:`{"code":40001,"message":"项目编号不可修改"}`

---

## 5. 前端测试(10 分钟)

**前端默认在跑**(你 M0 启动后没关),如果不在跑:

```powershell
cd D:\projects-online\investment-committee-archive-system\frontend
npm run dev
```

**浏览器开 `http://localhost:5173`**,登录 `admin / admin123`。

**测试路径**:

| 步骤 | 期望 |
|---|---|
| 左侧菜单有"项目管理"(可点) | ✅ |
| 点"项目管理" | 跳到 /projects,看到项目列表页(初次可能空) |
| 点"+ 新建项目" | 跳到 /projects/new,看到表单 |
| 填 code=`PRJ-UI-001`,name=`前端测试`,点保存 | 跳回 /projects,列表里看到 PRJ-UI-001 |
| 点"详情"按钮 | 跳到项目详情页,看到描述表 + 议案子列表(空) |
| 点"+ 新建议案" | 弹窗,填 code=`PROP-UI-001`,title=`UI 测试`,点保存 | 弹窗关闭,议案列表新增一行 |
| 点议案行"详情 / 材料" | 跳到议案详情,看到材料子列表(空) |
| 点"+ 新建材料" | 弹窗,填 title=`尽调报告`,保存 | 材料列表新增 |
| 点"上传版本" | 弹文件选择器,选一个 .docx / .pdf / .txt | 上传成功,弹"v1 解析状态:success"(或 running) |
| 点"版本管理" | 看到版本列表 |
| 点"章节" | 看到章节折叠列表(每个章节有标题和字数) |
| 点"下载" | 浏览器下载原文件 |

---

## 6. 故障排查(出问题用)

### 6.1 启动失败:`Failed to bind properties under 'spring.servlet.multipart.max-file-size'`

- 上一轮 M0 修过,可能又出。打开 `application.yml` 找 `max-file-size: 100MB`,确认是字面量不是 `${...}` 自引用。
- 修法:跑 sed(同 M0 的那次),或者 `git reset --hard origin/minimax` 拉回正确版本

### 6.2 API 报 401 / 403

- Token 没带 / 过期 / 错:重新 `curl POST /api/auth/login` 拿新 token
- `Authorization` 头格式错:必须 `Bearer xxx` 空格分隔

### 6.3 上传文件 500

- 看 backend.log 找错
- 常见:Tika 不认的格式(扫描件 PDF),M5 用 GLM-4V 处理
- 文件大小超过 100MB(改 `application.yml` 里的 `max-file-size` 和 `max-request-size`)

### 6.4 章节切分空数组

- Tika 解析失败 → 看 version 的 `parseStatus` 和 `parseError`
- 文件太小,正则没匹配到 → 用 `console.log` 看 `SectionService.split` 输入
- 文件是图片 / 扫描件 → 不支持,Tika 只解析文本

### 6.5 前端 `Failed to fetch` / CORS

- 后端没跑 / 8080 没起
- 看浏览器 DevTools Network 标签,点请求看 Response
- vite proxy 配置:`vite.config.ts` 应该有 `server.proxy['/api']`

---

## 7. 验证清单(过完勾)

### 后端
- [ ] `mvn clean package` BUILD SUCCESS
- [ ] `Tomcat started on port 8080`
- [ ] `Started ArchiveApplication`
- [ ] `/api/health` 返回 UP
- [ ] `/api/auth/login` 返回 token
- [ ] 4 张新表 `project` / `proposal` / `material` / `material_version` 存在

### API
- [ ] 创建项目 → 列表能看到
- [ ] 创建议案 → 详情能看到
- [ ] 创建材料 → 详情能看到
- [ ] 上传文件 → version 记录,parseStatus=success
- [ ] 章节切分 → 数组(2+ 章节)
- [ ] 下载文件 → 内容正确
- [ ] 重复 code → 报错
- [ ] 不带 token → 401

### 前端
- [ ] 菜单"项目管理"可点
- [ ] 列表/创建/编辑/详情/删除 都能跑
- [ ] 议案子表单 工作
- [ ] 材料 上传/版本/章节 弹窗 工作
- [ ] 浏览器 F12 控制台 无 JS 错误

---

## 8. 完成后告诉 Mavis

跑完上面 7 节所有勾,贴一句:

> **"M1 验证全过"**

或者有 bug:

> **"M1 验证 X 项失败,报错:[贴报错]"**

---

## 9. M2 / M3 / M4 / M5 预告(你下次说"开始"我才动)

- **M2** 知识库问答:MySQL FULLTEXT 索引 + 智谱 GLM-4-Flash 重排 + 引用溯源
- **M3** 时点提取:手工 + 自动抽取 + 邮件提醒
- **M4** 规则引擎:Aviator 表达式 + 事件订阅
- **M5** 打磨:扫描件 GLM-4V OCR + 审计 + 监控 + 备份演练 + 文档

---

**预期时间**:首次 60-90 分钟(遇到 bug 排查),熟练 30 分钟。
