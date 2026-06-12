# 投委会档案管理系统 — 运维手册

> 版本: v2.0 | 更新: 2026-06-09 | 受众: 开发/运维人员

---

## 1. 服务管理

### 1.1 后端服务

| 操作 | 命令 |
|------|------|
| 启动 | `net start archive-backend` |
| 停止 | `net stop archive-backend` |
| 重启 | `net stop archive-backend && net start archive-backend` |
| 状态 | `sc query archive-backend` |
| 日志实时 | `Get-Content D:\archive\logs\backend.log -Tail 100 -Wait` |

**服务配置**: `D:\archive\apps\backend\backend.xml` (WinSW)

### 1.2 QA 微服务 (qa-agent)

Python FastAPI 微服务，专司智能问答 + 字段抽取。

| 项目 | 内容 |
|------|------|
| **服务名** | `qa-agent` |
| **可执行文件** | `D:\projects-online\qa-agent\.venv\Scripts\python.exe -m uvicorn app.main:app` |
| **配置文件** | `D:\projects-online\qa-agent\.env`（或环境变量） |
| **端口** | `127.0.0.1:8001`（仅 localhost，不对外暴露） |
| **日志** | WinSW 滚动日志，10MB×10 文件 |
| **启停** | `net start/stop qa-agent` |
| **健康检查** | `curl http://127.0.0.1:8001/health` 或 Spring Boot `/actuator/health` |

**环境变量**：

```
GLM_API_KEY=your_key
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=archive_db
MYSQL_USER=archive_app
MYSQL_PASSWORD=your_password
FILE_ROOT=D:/archive/files
PARSED_ROOT=D:/archive/parsed
```

**手动启动（调试）**：
```powershell
cd D:\projects-online\qa-agent
.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

### 1.3 前端服务

由 Caddy 托管静态文件，无独立进程。

| 操作 | 命令 |
|------|------|
| 重载 Caddy | `caddy reload --config D:\archive\apps\caddy\Caddyfile` |
| 查看 Caddy 日志 | `Get-Content D:\archive\logs\caddy.log -Tail 50` |

### 1.3 数据库

| 操作 | 命令 |
|------|------|
| 连接 | `mysql -u root -p archive_db` |
| 备份 | `mysqldump -u root -p archive_db > E:\backup\mysql\archive_$(date +%F).sql` |
| 恢复 | `mysql -u root -p archive_db < E:\backup\mysql\archive_20260608.sql` |

---

## 2. 常见运维场景

### 2.1 后端部署新版本

```powershell
# 1. 拉最新代码
cd D:\projects_new\projects-online
git pull origin minimax

# 2. 构建
cd backend
mvn clean package -DskipTests

# 3. 备份旧版
Copy-Item D:\archive\apps\backend\archive.jar D:\archive\apps\backend\archive.jar.prev

# 4. 部署新版
Copy-Item target\archive.jar D:\archive\apps\backend\

# 5. 重启
net stop archive-backend && net start archive-backend

# 6. 验证
curl http://localhost:8080/api/health
```

### 2.2 前端部署新版本

```powershell
cd D:\projects_new\projects-online\frontend
npm install
npm run build
Copy-Item dist\* D:\archive\apps\frontend\dist\ -Recurse -Force
# Caddy 自动托管新文件，无需重启
```

### 2.3 修改配置热加载

```powershell
# 修改 config.json
notepad D:\archive\config\config.json

# 部分配置需重启后端生效(JWT secret, DB 连接等)
net stop archive-backend && net start archive-backend

# 日志级别可动态调整(无需重启)
curl -X POST http://localhost:8080/actuator/loggers/com.archive \
     -H "Content-Type: application/json" \
     -d '{"configuredLevel":"DEBUG"}'
```

### 2.4 数据库 Schema 变更

```powershell
# 1. 备份
mysqldump -u root -p archive_db > E:\backup\mysql\pre_migration_$(Get-Date -Format yyyyMMdd_HHmm).sql

# 2. 执行迁移 SQL
mysql -u root -p archive_db < D:\projects_new\projects-online\backend\src\main\resources\db\migration\v2-schema.sql

# 3. 验证
mysql -u root -p -e "USE archive_db; SHOW TABLES;"
```

### 2.5 证书续期(Caddy 自动)

Caddy 自动管理 Let's Encrypt 证书，无需人工干预。
如需强制续期：
```powershell
caddy reload --config D:\archive\apps\caddy\Caddyfile
```

---

## 3. 监控与告警

### 3.1 关键指标

| 指标 | 端点 | 阈值建议 |
|------|------|----------|
| JVM 内存使用率 | `/actuator/metrics/jvm.memory.used` | > 80% 告警 |
| HTTP 请求延迟 P95 | `/actuator/metrics/http.server.requests` | > 2s 告警 |
| DB 连接池使用率 | `/actuator/metrics/hikaricp.connections.active` | > 80% 告警 |
| 磁盘剩余 | `Get-PSDrive D` | < 10GB 告警 |
| 错误率 | `/actuator/httptrace` 统计 5xx | > 1% 告警 |

### 3.2 健康检查自动化

```powershell
# 简单版:每 5 分钟跑一次
while ($true) {
    try {
        $health = curl -s http://localhost:8080/api/health | ConvertFrom-Json
        if ($health.data.status -ne 'UP') {
            # 发送告警(邮件/钉钉/企微)
            Write-Warning "Backend health DOWN"
        }
    } catch {
        Write-Error "Health check failed: $_"
    }
    Start-Sleep 300
}
```

---

## 4. 故障处理 SOP

### 4.1 后端无响应

1. `sc query archive-backend` 确认服务状态
2. `Get-Content D:\archive\logs\backend.log -Tail 200` 看报错
3. 常见原因:
   - `config.json` 缺失/格式错 → 检查 BOM、JSON 语法
   - DB 连不上 → 检查密码、MySQL 服务、防火墙 3306
   - 端口占用 → `netstat -ano | findstr 8080`
   - OOM → 看日志 `java.lang.OutOfMemoryError`，加 `-Xmx`

### 4.2 登录失败/Token 无效

1. 确认 `config.json` 的 `jwt.secret` 与启动时一致
2. 确认 token 未过期(默认 8h)
3. 重新登录拿新 token

### 4.3 文件上传失败

| 错误 | 原因 | 处理 |
|------|------|------|
| 413 Request Entity Too Large | 超过 `max-file-size` | 改 `application.yml` + 重启 |
| 500 Tika parse failed | 格式不支持/损坏 | 看 `parseError` 字段，M5 用 GLM-4V |
| 500 SHA-256 重复 | 同文件已上传 | 业务正常，前端提示"已存在" |

### 4.4 前端白屏/报错

1. F12 Console 看 JS 报错
2. Network 看 API 请求状态(401/500/CORS)
3. 常见:
   - `Failed to fetch` → 后端挂了/代理错
   - `resp.data is undefined` → API 返回结构变了(见 Lessons 4.2)

### 4.5 数据库连接数耗尽

```sql
SHOW PROCESSLIST;
-- 看 Sleep 连接多不多

-- 临时加连接数
SET GLOBAL max_connections = 200;

-- 根治:检查代码是否正确关闭连接(@Transactional 回滚、连接池配置)
```

---

## 5. 备份与恢复演练

### 5.1 备份清单(每日自动)

| 对象 | 频率 | 保留 | 位置 |
|------|------|------|------|
| MySQL 全量 | 每日 02:00 | 30 天 | `E:\backup\mysql\` |
| 上传文件 | 每日 03:00 | 30 天 | `E:\backup\files\` |
| 配置文件 | 每次改动 | 永久 | `D:\archive\config\config.json` |

### 5.2 恢复演练(季度 1 次)

```powershell
# 1. 还原 DB 到测试库
mysql -u root -p archive_db_test < E:\backup\mysql\archive_20260601.sql

# 2. 还原文件到测试目录
robocopy E:\backup\files D:\archive_test\files /MIR

# 3. 修改测试环境 config.json 指向测试库/测试目录

# 4. 启动测试实例(端口 8081)，跑冒烟测试
```

---

## 6. 关键文件清单

| 文件 | 用途 | 修改频率 |
|------|------|----------|
| `D:\archive\config\config.json` | 生产密钥/配置 | 低 |
| `D:\archive\apps\backend\backend.xml` | WinSW 服务配置 | 低 |
| `D:\archive\apps\caddy\Caddyfile` | 反代/SSL/静态托管 | 低 |
| `D:\archive\logs\backend.log` | 后端日志 | 持续 |
| `backend/src/main/resources/application.yml` | 后端默认配置 | 中 |
| `frontend/vite.config.ts` | 前端构建/代理配置 | 低 |

---

## 7. 联系人

| 角色 | 联系方式 | 职责 |
|------|----------|------|
| 业务方 | frisker(投委会秘书) | 业务决策、验收 |
| 开发主 | Mavis(agent) | 代码、文档、排查 |
| 运维 | 无专人(单机自维护) | 部署、监控、备份 |

---

## 8. 版本记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v2.0.0 | 2026-06-09 | M0-M4 全部完工，生产就绪 |