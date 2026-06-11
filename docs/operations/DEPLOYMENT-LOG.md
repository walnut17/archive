# 投委会档案管理系统 — 部署进度记录

> **最后更新**:2026-06-07
> **服务器**:Windows Server 2012 R2,D 盘根目录工作
> **维护人**:Mavis(沙箱)+ 用户(本机)
> **目的**:记录真实部署过程的所有步骤、坑、修复,方便后续开新 session 1 秒对齐进度

---

## 0. 当前状态(1 秒对齐)

| 项 | 状态 | 备注 |
|---|---|---|
| **后端代码** | ✅ 已构建 | `target\archive.jar` 存在 |
| **后端启动** | ⏳ **待你重启** | 修复了 `application.yml` 4 处循环引用,等重启验证 |
| **数据库** | ✅ 已初始化 | 4 role + 1 user,archive_app 账号已建 |
| **前端代码** | ⏳ 未跑 | npm install + npm run dev 未做 |
| **后端 curl 验证** | ⏳ 未做 | 等后端跑通后跑 /api/health 和 /api/auth/login |
| **前端浏览器验证** | ⏳ 未做 | 等 npm run dev 后开 http://localhost:5173 |
| **M0 端到端** | ⏳ **未跑通** | 还差"后端启动 + curl + 前端启动 + 浏览器"4 步 |
| **M1 档案 CRUD** | 🚫 未开始 | 等 M0 跑通才能开 |
| **M2-M5** | 🚫 未开始 | 依序推进 |

**距离 M0 跑通:还差 1 步后端重启 + 2 步 curl 验证 + 2 步前端。**

---

## 1. 仓库与分支

| 项 | 值 |
|---|---|
| 仓库 | `git@gitee.com:frisker/projects-online.git` |
| 本地路径 | `D:\projects-online`(用户本机) |
| 工作分支 | `minimax` |
| 沙箱路径 | `/workspace/projects-online-clone/` |
| 沙箱最新 commit | `1f6c7d1`(M0-8 docs) |
| 待发 PR | minimax → main(用户自己发) |
| commit 粒度 | 每个独立模块一次 commit + push(用户定) |

---

## 2. 工具环境(已装)

| 工具 | 版本 | 验证命令 | 备注 |
|---|---|---|---|
| **Git** | 2.54.0 | `git --version` |  |
| **JDK** | 17.0.19(Temurin) | `java -version` | 装在 `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\` |
| **Maven** | 3.8.8 | `mvn -v` | 装在 `C:\Program Files\apache-maven-3.8.8\`,Maven 3.9+ 不支持 Server 2012 |
| **Node** | 18.20.4 LTS | `node -v` | **不能装 Node 20**(Server 2012 装不了),18 LTS 兼容 |
| **MySQL** | 8.0.16 | 用户已有 | 装在默认路径 `C:\Program Files\MySQL\MySQL Server 8.0\` |
| **OS** | Windows Server 2012 R2 6.3 amd64 | `systeminfo` | R2 兼容性比 Server 2012 原始版好 |

### 镜像源记录(下次装工具用)

- **Git**:`https://registry.npmmirror.com/-/binary/git-for-windows/v2.43.0/windows.x64.exe`
- **JDK 17**:Temurin `https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.10%2B7`,下 `OpenJDK17U-jdk_x64_windows_hotspot_17.0.10_7.msi`(注意是 `-jdk-` 不是 `-debugimage-`/`-jre-`/`-sbom-`)
- **Maven 3.8.8**:Apache archive `https://archive.apache.org/dist/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.zip`
- **Node 18.20.4**:npmmirror `https://registry.npmmirror.com/-/binary/node/v18.20.4/node-v18.20.4-x64.msi`

---

## 3. SSH Key 配置(已完成)

| 端 | Key 类型 | 状态 |
|---|---|---|
| **Gitee 账号** `https://gitee.com/profile/sshkeys` | 个人 SSH key `~/.ssh/id_ed25519_gitee`(ed25519) | ✅ 已加,无密码 |
| **Gitee 仓库 deploy key** | `archive-deploy-20260607`(之前我加的) | ⚠️ **建议清理**(现在账号 key 已能 clone) |
| **沙箱 deploy key 私钥** | `/tmp/archive_deploy`(ed25519) | ✅ **保留,不动**(用户明确要求别删) |
| **沙箱 SSH config** | `~/.ssh/config` | ✅ 已配 Gitee host 用 deploy key |
| **Gitee 指纹** | `SHA256:+ULzij2u99B9eWYFTw1Q4ErYG/aepHLbu96PAUCoV88` | 首次 clone 已 accept |

### 用户本机 SSH 配置文件 `C:\Users\Administrator\.ssh\config`

```
Host gitee.com
    HostName gitee.com
    User git
    IdentityFile C:\Users\Administrator\.ssh\id_ed25519_gitee
    IdentitiesOnly yes
```

### ⚠️ 沙箱 push 仍用 deploy key(虽然本机用账号 key 也能 push)

```bash
export GIT_SSH_COMMAND="ssh -i /tmp/archive_deploy -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"
git push origin minimax
```

**理由:** 沙箱和本机用不同 key 互不干扰,以后 Gitee 清理 deploy key 时,沙箱还能继续 push。

---

## 4. MySQL 数据库(已完成)

| 操作 | 命令 | 结果 |
|---|---|---|
| 初始化 | `mysql -u root -p < ...\backend\src\main\resources\db\init.sql` | ✅ archive_db 库 + 5 张表(role/user/project/proposal/material) |
| Role 数据 | `SELECT * FROM role;` | 4 行:ADMIN / COMMITTEE / MANAGER / STAFF |
| User 数据 | `SELECT * FROM user;` | 1 行:admin / 管理员 / ACTIVE |
| 应用账号 | `CREATE USER 'archive_app'@'localhost' IDENTIFIED BY 'Archive@2026';` | ✅ 已建,密码用户自定 |
| 应用授权 | `GRANT ALL PRIVILEGES ON archive_db.* TO 'archive_app'@'localhost';` | ✅ 已授权 |

### 验证 SQL(随时跑)

```sql
USE archive_db;
SHOW TABLES;             -- 预期:role, user, project, proposal, material
SELECT code, name FROM role;
SELECT id, username, display_name, status FROM user;
```

---

## 5. config.json(已完成)

**位置**:`D:\archive\config\config.json`(从 `config\config.example.json` 复制)

### 用户改过的项

```json
{
  "glm": {
    "apiKey": ""                    // 留空,M0 用不上智谱
  },
  "database": {
    "username": "archive_app",
    "password": "Archive@2026"      // 用户自定
  }
  // jwt 段未改(模板里没有这字段)
  // 其他保持默认
}
```

### ⚠️ 已知问题:`config.json` 没有 jwt 段

**原因:** M0-4 写模板时把 jwt.secret 默认值硬编码到 `application.yml`,config.json 没暴露。**M0 阶段用默认 secret 先跑通就行,production 上线前必须改。**

**待办(M0 跑通后):**
1. 在 `ConfigJsonLoader` 加 jwt 段解析
2. `application.yml` 加启动校验:用默认 secret 启动失败
3. 更新 `config.example.json` 加 jwt 段
4. 提 PR 到 Gitee

---

## 6. 后端构建(已完成)

| 步骤 | 命令 | 结果 |
|---|---|---|
| 构建 | `mvn clean package -DskipTests` | ✅ **BUILD SUCCESS** |
| 产物 | `D:\projects_new\projects-online\backend\target\archive.jar` | ✅ 存在 |
| 首次依赖下载 | `C:\Users\Administrator\.m2\repository\` | 已缓存,后续构建快 |

---

## 7. 后端启动(⏳ 待重启验证)

### 第一次启动失败 + 修复

**失败原因:`application.yml` 有 4 处循环引用**

| 行 | 问题 | 修复 |
|---|---|---|
| `app.storage.log-root: ${app.storage.log-root:D:/archive/logs}` | 自引用 | 改为 `log-root: D:/archive/logs` |
| `logging.file.name: ${app.storage.log-root:D:/archive/logs}/backend.log` | 间接自引用 | 改为 `name: D:/archive/logs/backend.log` |
| `app.storage.upload-max-size-mb: ${app.storage.upload-max-size-mb:100}` | 自引用 | 改为 `upload-max-size-mb: 100` |
| `spring.servlet.multipart.max-file-size: ${app.storage.upload-max-size-mb:100}MB` | 间接自引用 | 改为 `max-file-size: 100MB` |
| `spring.servlet.multipart.max-request-size: ${app.storage.upload-max-size-mb:100}MB` | 间接自引用 | 改为 `max-request-size: 100MB` |

**修复用的 sed 命令(已发给用户):**

```powershell
$file = "D:\projects_new\projects-online\backend\src\main\resources\application.yml"
$content = Get-Content $file -Raw
$content = $content -replace 'log-root: \$\{app\.storage\.log-root:D:/archive/logs\}', 'log-root: D:/archive/logs'
$content = $content -replace 'name: \$\{app\.storage\.log-root:D:/archive/logs\}/backend\.log', 'name: D:/archive/logs/backend.log'
$content = $content -replace 'upload-max-size-mb: \$\{app\.storage\.upload-max-size-mb:100\}', 'upload-max-size-mb: 100'
$content = $content -replace 'max-file-size: \$\{app\.storage\.upload-max-size-mb:100\}MB', 'max-file-size: 100MB'
$content = $content -replace 'max-request-size: \$\{app\.storage\.upload-max-size-mb:100\}MB', 'max-request-size: 100MB'
Set-Content -Path $file -Value $content -Encoding UTF8
```

**✅ 用户已跑完 sed**(`Get-Content` 验证后看到修复后的字面量,无自引用)。

### 第二次启动待验证

```powershell
cd D:\projects_new\projects-online\backend
mvn clean package -DskipTests
java "-Dfile.encoding=UTF-8" -jar .\target\archive.jar
```

**预期最后 3 行:**

```
Tomcat started on port 8080 (http)
Started ArchiveApplication in X.XXX seconds
```

> 注意 PowerShell 参数解析坑:`-Dfile.encoding=UTF-8` 必须用双引号包起来,否则会变成 3 个参数

---

## 8. 验证清单(后端跑通后必跑)

### curl 1:健康检查

```powershell
curl http://localhost:8080/api/health
```

**预期:**
```json
{"code":0,"message":"OK","data":{"status":"UP",...}}
```

### curl 2:登录

```powershell
$body = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
curl -Method POST -Uri http://localhost:8080/api/auth/login -ContentType "application/json" -Body $body
```

**预期:**
```json
{"code":0,"message":"OK","data":{"token":"eyJ...","userInfo":{...}}}
```

---

## 9. 前端(⏳ 未启动)

```powershell
cd D:\projects_new\projects-online\frontend
npm install              # 首次 3-5 分钟
npm run dev
```

**预期:**
```
VITE v5.4.x  ready in xxx ms
➜  Local:   http://localhost:5173/
```

**浏览器开 `http://localhost:5173`,admin/admin123 登录,工作台显示"后端健康: UP" = M0 端到端跑通。**

---

## 10. 踩过的坑(防止下次再踩)

| 坑 | 教训 | 修复 |
|---|---|---|
| PowerShell `ssh-keygen` 找不到 | Git 自带 ssh 工具不在 PATH | 加 `$env:Path += ";C:\Program Files\Git\usr\bin"` |
| `git clone` 报 `Permission denied (publickey)` | 本机用的是 Gitee 账号 key,不是 deploy key | 用账号 key 走 SSH config 配,或用 HTTPS + token |
| `mvn -v` 报 JAVA_HOME not set | JDK 装完没自动加环境变量 | 手动加 `JAVA_HOME` + `Path` |
| `mvn` 想装 3.9.x 但 Server 2012 不支持 | Maven 3.9+ 要 Server 2016+ | 用 3.8.8(最后一个支持 Server 2012) |
| `node -v` 报 Node 20 装不了 | Node 20 要 Windows Server 2019+ | 用 Node 18 LTS(20.x 之后只支持 Server 2019+) |
| `java -jar -Dfile.encoding=UTF-8` 报 "Unable to access jarfile" | PowerShell 把 `-D` 后内容当成 jar 名 | 用 `java "-Dfile.encoding=UTF-8" -jar ...` 加引号 |
| 启动报 `Circular placeholder reference` | `application.yml` 4 处自引用 | 改字面量(详见第 7 节) |
| `git clone` 在 `D:\projects-online` 之后又 `cd projects-online` clone | 路径里多了一层 `projects-online` | 直接在 `D:\` clone,真路径是 `D:\projects-online\...` 一层 |

---

## 11. M0 跑通后的待办

### 必修 bug(影响启动)

- [ ] 修 `application.yml` 4 处循环引用(M0 跑通后提 PR)
- [ ] 修 `ConfigJsonLoader` 防止类似自引用
- [ ] 修 `config.example.json` 加 jwt 段
- [ ] 加启动校验:jwt.secret 是默认值就启动失败

### 沙箱侧 PR(可并行,先写后端代码)

- [ ] 修 `application.yml`(修后让用户 pull)
- [ ] 修 `ConfigJsonLoader.java`
- [ ] 修 `config.example.json`
- [ ] 提 PR 到 minimax

### M1 启动条件

- [ ] 后端启动 + curl /api/health + curl /api/auth/login 三者都 OK
- [ ] 前端 npm run dev + 浏览器登录 + 工作台"后端健康:UP"
- [ ] 用户在 Gitee 发 PR:minimax → main(可选,等生产前再合)

---

## 12. 用户偏好(下次开新 session 适用)

- **沟通**:中文,直接务实,不要客服腔
- **进度**:每次改完一块就 push 一次,不打包文件
- **架构**:极致轻量,反对过度设计,接受"不向量化"作为底线
- **命名**:沙箱里 `Mavis` agent,`minimax` 是分支/agent 名
- **联系方式**:沙箱留 `Mavis` deploy key 私钥 `/tmp/archive_deploy`,**别删**
- **代码 review 风格**:快速定位问题直接给修复命令,不绕弯
- **commit 信息**:用 `M0-X: ...` 风格(M0-1 ~ M0-8 已确立)

---

## 13. 1 秒钟对齐当前 session

如果下次开新 session,**只发这一段话:**

> "M0 部署进度到 DEPLOYMENT-LOG.md 第 8 节(curl 验证)前,后端 jar 已构建,application.yml 4 处循环引用已修,待重启 + curl 验证 + 前端启动。当前卡在后端重启验证。继续 M0 部署。"

我立刻能接上,不用再问你一遍环境/路径/版本。

---

## 14. 仓库 / 数据库 / 服务端 一览

| 项 | 值 |
|---|---|
| Gitee 仓库 | https://gitee.com/frisker/projects-online |
| Gitee 分支 | minimax(领先 main 1 个 commit) |
| Gitee 最新 commit | `1f6c7d1` |
| MySQL 库名 | `archive_db` |
| MySQL 应用账号 | `archive_app` / `Archive@2026`(用户自定) |
| 后端端口 | 8080 |
| 前端端口 | 5173(开发),Caddy 反代后 80/443(生产) |
| 日志路径 | `D:\archive\logs\backend.log`(Spring Boot 写) |
| 文件路径 | `D:\archive\files\`(原始) + `D:\archive\parsed\`(Tika 解析缓存) |
| 备份路径 | `E:\backup\mysql\` + `E:\backup\files\`(M5 启用) |

---

**下次开 session,贴"第 13 节那段话"给我,5 秒对齐进度。**
