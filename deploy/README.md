# 投委会档案管理系统 - 部署

> Windows Server 2019/2022 单机部署

**项目协作**：部署指南 [`docs/operations/DEPLOYMENT.md`](../docs/operations/DEPLOYMENT.md) · 操作日志 [`docs/operations/deployment_log.md`](../docs/operations/deployment_log.md) · 联调 bug [`test-to-settle/`](../test-to-settle/README.md) · 架构说明 [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 1. 准备

| 工具 | 来源 | 路径 |
|---|---|---|
| JDK 17 LTS | https://adoptium.net | `D:\jdk-17\`(记下完整路径) |
| MySQL 8.0 | https://dev.mysql.com/downloads/installer/ | `C:\Program Files\MySQL\MySQL Server 8.0\`(默认) |
| Caddy 2 | https://caddy.server | `D:\archive\apps\caddy\caddy.exe` |
| WinSW x64 | https://github.com/winsw/winsw/releases | 改名 `archive-backend-service.exe` |
| Node 20 LTS(开发用) | https://nodejs.org | 装到默认路径 |

## 2. 目录约定

```
D:\archive\
├── apps\
│   ├── backend\
│   │   ├── archive.jar
│   │   ├── archive-backend-service.exe   (WinSW)
│   │   └── backend.xml                    (WinSW 配置)
│   ├── caddy\
│   │   ├── caddy.exe
│   │   └── Caddyfile
│   └── frontend\
│       └── dist\                          (npm run build 产物)
├── config\
│   └── config.json                        (从 ../config/config.example.json 复制)
├── files\                                 (原始档案)
├── parsed\                                (Tika 解析缓存)
└── logs\                                  (应用日志)
```

## 3. 部署步骤(首次)

### 3.1 准备 MySQL

详见 [`sql/README.md`](sql/README.md)。

```powershell
# 安装 MySQL 8.0,记住 root 密码
# 初始化：MySQL 客户端 source deploy\sql\init.sql（新库）
# 已有库升级：source deploy\sql\migrate_260611_01.sql
# 用 root 登录 MySQL,创建应用账号
mysql -u root -p
> CREATE USER 'archive_app'@'localhost' IDENTIFIED BY '强密码';
> GRANT ALL PRIVILEGES ON archive_db.* TO 'archive_app'@'localhost';
> FLUSH PRIVILEGES;
> EXIT;
```

### 3.2 准备 config.json
```powershell
# 从 config/config.example.json 复制
copy config\config.example.json D:\archive\config\config.json
notepad D:\archive\config\config.json
# 填好 glm.apiKey / database.password / jwt.secret 等
```

### 3.3 构建后端 JAR
```powershell
# 在 backend 目录
cd backend
mvn clean package -DskipTests
# 或用 deploy\scripts\build-backend.bat
```

### 3.4 部署后端
```powershell
# 拷贝产物
mkdir D:\archive\apps\backend
copy target\archive.jar D:\archive\apps\backend\
copy ..\deploy\winsw\backend.xml D:\archive\apps\backend\

# 下载 WinSW:https://github.com/winsw/winsw/releases
# 把 WinSW-x64.exe 改名为 archive-backend-service.exe,放 D:\archive\apps\backend\

# 注册为 Windows 服务(管理员 PowerShell)
cd D:\archive\apps\backend
.\archive-backend-service.exe install
sc config archive-backend start= auto
net start archive-backend

# 看日志确认启动
type D:\archive\logs\backend.log
```

### 3.5 部署前端
```powershell
# 在 frontend 目录
cd frontend
npm install
npm run build
# 拷贝 dist/ 到 D:\archive\apps\frontend\dist
mkdir D:\archive\apps\frontend\dist
xcopy /E /I dist\* D:\archive\apps\frontend\dist\
```

### 3.6 部署 Caddy
```powershell
# 下载 caddy_windows_amd64.zip,解压到 D:\archive\apps\caddy\
# 把 Caddyfile 放过去
copy ..\deploy\caddy\Caddyfile D:\archive\apps\caddy\

# 开发期(内网 HTTP,无 HTTPS)
cd D:\archive\apps\caddy
caddy.exe run --config Caddyfile

# 注册为服务
caddy.exe service install --config Caddyfile
sc config caddy start= auto
net start caddy
```

## 4. 验证清单

部署完,按这个清单逐项勾:

- [ ] `curl http://localhost:8080/api/health` 返回 `{"code":0,"data":{"status":"UP",...}}`
- [ ] `curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"` 返回 token
- [ ] `http://localhost:5173`(dev)或 `http://archive.internal.example.cn`(prod)能打开登录页
- [ ] 浏览器登录 admin/admin123,跳到工作台,显示 "后端健康: UP"
- [ ] `sc query archive-backend` 状态 RUNNING
- [ ] `sc query caddy` 状态 RUNNING(如果启用了)
- [ ] 重启服务器后,服务能自动拉起(测一次)

## 5. 运维常用命令

```powershell
# 看后端日志
type D:\archive\logs\backend.log

# 重启后端
net stop archive-backend && net start archive-backend

# 看后端进程
tasklist | findstr java

# 备份数据库(每日 02:00 跑)
mysqldump -u backup_operator -p archive_db | gzip > D:\archive\backup\db-%date:~0,4%%date:~5,2%%date:~8,2%.sql.gz

# 同步文件到 E 盘(每日 03:00 跑)
robocopy D:\archive\files D:\archive\backup\files /MIR /Z /R:3 /W:10
```

## 6. 故障排查

**前端 502 Bad Gateway**
- 检查后端服务:`sc query archive-backend` 必须 RUNNING
- 看后端日志最后 50 行,看有没有启动错误

**登录提示"用户名或密码错误"**
- 确认 init.sql 跑过了
- 用 root 登录 MySQL,检查 user 表是否有 admin

**上传文件失败(413 Request Entity Too Large)**
- Spring Boot 默认 100MB,改 application.yml 调大

**服务启动后立即退出**
- 看 `D:\archive\logs\backend.log` 最后错误
- 99% 是 config.json 缺失或格式错
