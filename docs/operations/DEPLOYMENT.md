# 投委会档案管理系统 — 部署指南

> 版本: v2.0 | 更新: 2026-06-09 | 环境: Windows Server 2012 R2 / 单机部署

---

## 1. 架构概览

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Caddy     │────▶│  Frontend   │     │  Backend    │
│  (反代/SSL) │     │  (Vue 3)    │────▶│ (Spring Boot)│
│  :80/:443   │     │  :5173(dev) │     │  :8080      │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                                    ┌──────────┴──────────┐
                                    │      MySQL 8.0      │
                                    │     archive_db      │
                                    └─────────────────────┘
```

---

## 2. 前置条件

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 17.0.19+ (Temurin) | Server 2012 R2 兼容 |
| Maven | 3.8.8 | 3.9+ 不支持 Server 2012 |
| Node.js | 18.20.4 LTS | 20.x 需 Server 2019+ |
| MySQL | 8.0.16+ | 已有实例 |
| Git | 2.43+ | 配置 SSH key |

---

## 3. 目录结构约定(生产)

```
D:\archive\
├── apps\
│   ├── backend\          # archive.jar 放这里
│   └── frontend\dist\    # 前端构建产物
├── config\
│   └── config.json       # ⚠️ 生产密钥,不进 Git
├── logs\
│   └── backend.log
├── files\                # 原始上传文件
├── parsed\               # Tika 解析缓存
└── .ssh\                 # 沙箱 SSH key(不进 Git)
```

---

## 4. 部署步骤

### 4.1 克隆代码 & 切分支

```powershell
cd D:\
git clone git@gitee.com:frisker/projects-online.git
cd projects-online
git checkout minimax
```

### 4.2 数据库初始化

```sql
-- 1. 创建库 + 应用账号
CREATE DATABASE archive_db DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE USER 'archive_app'@'localhost' IDENTIFIED BY 'Archive@2026';
GRANT ALL PRIVILEGES ON archive_db.* TO 'archive_app'@'localhost';
FLUSH PRIVILEGES;

-- 2. 跑 init.sql（新库全量建表；已有库升级见 deploy/sql/migrate_260611_01.sql）
mysql -u root -p archive_db < D:\projects_new\projects-online\deploy\sql\init.sql

-- 3. 验证
USE archive_db;
SHOW TABLES;
-- 预期: role, user, project, proposal, material, material_version, chapter_summary, ...
```

### 4.3 配置文件

```powershell
# 复制模板
Copy-Item D:\projects_new\projects-online\config\config.example.json D:\archive\config\config.json

# 编辑(至少填这几项)
notepad D:\archive\config\config.json
```

```json
{
  "glm": { "apiKey": "你的智谱API Key" },
  "database": {
    "host": "localhost",
    "port": 3306,
    "database": "archive_db",
    "username": "archive_app",
    "password": "Archive@2026"
  },
  "storage": {
    "fileRoot": "D:/archive/files",
    "logRoot": "D:/archive/logs"
  },
  "jwt": {
    "secret": "openssl rand -base64 32 生成的字符串"
  }
}
```

### 4.4 后端构建 & 部署

```powershell
cd D:\projects_new\projects-online\backend
mvn clean package -DskipTests

# 部署到生产目录
mkdir -p D:\archive\apps\backend
Copy-Item target\archive.jar D:\archive\apps\backend\
Copy-Item ..\deploy\winsw\backend.xml D:\archive\apps\backend\

# 注册 Windows 服务(管理员 PowerShell)
cd D:\archive\apps\backend
.\archive-backend-service.exe install
sc config archive-backend start= auto
net start archive-backend
```

### 4.5 前端构建 & 部署

```powershell
cd D:\projects_new\projects-online\frontend
npm install
npm run build

# 部署到 Caddy 静态目录
mkdir -p D:\archive\apps\frontend\dist
Copy-Item dist\* D:\archive\apps\frontend\dist\ -Recurse
```

### 4.6 Caddy 配置 & 启动

```powershell
# Caddyfile 已在 deploy/caddy/Caddyfile
# 关键配置:
# - 反代 /api/* → localhost:8080
# - 静态托管 D:\archive\apps\frontend\dist
# - SPA 路由 try_files {path} /index.html
```

---

## 5. 验证清单

| 检查项 | 命令 | 预期 |
|--------|------|------|
| 后端健康 | `curl http://localhost:8080/api/health` | `{"status":"UP"}` |
| 后端登录 | `curl -X POST .../api/auth/login` | 返回 JWT token |
| 前端访问 | 浏览器开 `https://archive.internal.example.cn` | 登录页正常 |
| 端到端 | 登录 admin/admin123 → 工作台显示"后端健康: UP" | ✅ |

---

## 6. 常用运维命令

```powershell
# 后端重启
net stop archive-backend && net start archive-backend

# 查看日志
Get-Content D:\archive\logs\backend.log -Tail 100 -Wait

# 后端重新构建部署
cd D:\projects_new\projects-online\backend
mvn clean package -DskipTests
Copy-Item target\archive.jar D:\archive\apps\backend\
net stop archive-backend && net start archive-backend

# 前端重新部署
cd D:\projects_new\projects-online\frontend
npm run build
Copy-Item dist\* D:\archive\apps\frontend\dist\ -Recurse

# 数据库备份
mysqldump -u root -p archive_db > E:\backup\mysql\archive_$(Get-Date -Format yyyyMMdd).sql

# 文件备份
robocopy D:\archive\files E:\backup\files /MIR /R:1 /W:1
```

---

## 7. 回滚流程

```powershell
# 1. 停止服务
net stop archive-backend

# 2. 回滚 JAR(保留上一个版本)
Move-Item D:\archive\apps\backend\archive.jar D:\archive\apps\backend\archive.jar.bak
Move-Item D:\archive\apps\backend\archive.jar.prev D:\archive\apps\backend\archive.jar

# 3. 如需回滚数据库
mysql -u root -p archive_db < E:\backup\mysql\archive_20260608.sql

# 4. 启动
net start archive-backend
```

---

## 8. 监控端点

| 端点 | 用途 |
|------|------|
| `/actuator/health` | 健康检查(含 DB/磁盘) |
| `/actuator/metrics` | JVM/HTTP/DB 指标 |
| `/actuator/prometheus` | Prometheus 抓取格式 |
| `/actuator/loggers` | 动态调整日志级别 |
| `/actuator/httptrace` | 最近 HTTP 请求追踪 |

---

## 9. 故障排查速查

| 现象 | 可能原因 | 排查 |
|------|----------|------|
| 后端起不来 | config.json 缺失/密码错 | 检查 `D:\archive\config\config.json` |
| 登录 401 | JWT secret 不匹配 | 对比 config.json 与 application.yml |
| 上传 500 | Tika 不支持格式/文件过大 | 检查 `max-file-size`、backend.log |
| 前端白屏 | API 代理失效/构建缓存 | 清 `node_modules/.vite` 重 build |
| DB 连不上 | 端口/防火墙/账号 | `telnet localhost 3306` + 检查 grant |

---

## 10. 版本记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v2.0.0 | 2026-06-09 | M0-M4 全部完工,生产就绪 |
| v1.0.0 | 2026-06-07 | M0 端到端跑通 |