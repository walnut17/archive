# 投委会档案管理系统 - 后端

> Spring Boot 3.3 + Java 17 + JPA + Spring Security + JWT

**项目协作**：根 [`README.md`](../README.md) · 抢任务 [`TASKS.md`](../TASKS.md) · 集成测试案例 [`test_task/`](../test_task/README.md) · 架构说明 [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 1. 准备

| 工具 | 版本 | 验证命令 |
|---|---|---|
| JDK | 17 LTS | `java -version` |
| Maven | 3.8+ | `mvn -v` |
| MySQL | 8.0+ | `mysql --version` |

## 2. 准备数据库

```bash
# 把 init.sql 跑一遍（MySQL 客户端 source deploy/sql/init.sql）
mysql -u root -p archive_db < ../deploy/sql/init.sql

# 验证:能看到 archive_db 和 4 个角色 + 1 个 admin 账号
mysql -u root -p -e "USE archive_db; SELECT * FROM role; SELECT id, username, display_name, role_id FROM user;"
```

## 3. 准备 config.json(用户本地,**不进 Git**)

```bash
# 1. 从 ../config/config.example.json 复制
cp ../config/config.example.json ../config/config.json

# 2. 编辑填值(至少要改这几项)
notepad ../config/config.json
```

最少必填:

```json
{
  "glm": {
    "apiKey": "你的智谱 API key"
  },
  "database": {
    "host": "localhost",
    "port": 3306,
    "database": "archive_db",
    "username": "archive_app",
    "password": "archive_app 的密码"
  },
  "storage": {
    "fileRoot": "D:/archive/files",
    "logRoot": "D:/archive/logs"
  },
  "jwt": {
    "secret": "32 字节以上的随机字符串(用 openssl rand -base64 32 生成)"
  }
}
```

**MySQL 账号准备**:

```sql
-- 用 root 登录执行
CREATE USER 'archive_app'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON archive_db.* TO 'archive_app'@'localhost';
FLUSH PRIVILEGES;
```

## 4. 构建并启动

### 方式 A:开发期(IDE / 命令行)

```bash
# 在 backend 目录
mvn clean package -DskipTests

# 跑
java -jar -Xms512m -Xmx1536m -Dfile.encoding=UTF-8 target/archive.jar
```

### 方式 B:Windows 服务(推荐生产)

```bash
# 1. 装 WinSW(下载 WinSW-x64.exe,改名为 archive-backend-service.exe)
#    放到 D:\archive\apps\backend\

# 2. 拷贝 JAR 和配置
mkdir -p D:\archive\apps\backend
cp target/archive.jar D:\archive\apps\backend\
cp ..\deploy\winsw\backend.xml D:\archive\apps\backend\

# 3. 注册服务(管理员 PowerShell)
cd D:\archive\apps\backend
.\archive-backend-service.exe install
sc config archive-backend start= auto
net start archive-backend
```

## 5. 验证

```bash
# 健康检查
curl http://localhost:8080/api/health
# 预期:{"code":0,"message":"ok","data":{"status":"UP","time":"2026-..."}}

# 登录
curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'
# 预期:{"code":0,"message":"ok","data":{"token":"eyJ...","userId":1,"username":"admin","displayName":"系统管理员","role":"admin"}}

# 用 token 获取当前用户
curl http://localhost:8080/api/auth/me \
     -H "Authorization: Bearer 上面拿到的 token"
```

## 6. 常见问题

**Q: 启动报 "Could not create connection to database server"?**
A: 检查 `config.json` 里的 `database.host` / `port` / `username` / `password` 是否正确,确认 MySQL 在跑。

**Q: 启动报 "JWT secret 长度小于 32 字节"?**
A: config.json 的 `jwt.secret` 改成 32 字节以上(用 `openssl rand -base64 32` 生成)。

**Q: 登录提示 "用户名或密码错误"?**
A: 1) 确认 init.sql 跑过了;2) admin 账号的密码 hash 是 BCrypt("admin123"),其他密码会失败。

**Q: 端口 8080 被占用?**
A: 改 config.json 的 `server.port`,或者停掉占用 8080 的程序。

## 7. 目录结构

```
backend/
├── pom.xml                         # Maven 配置
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/com/archive/
    │   │   ├── ArchiveApplication.java    # 启动类
    │   │   ├── common/                    # ApiResponse, GlobalExceptionHandler
    │   │   ├── config/                    # ConfigJsonLoader, GlmProperties, ...
    │   │   ├── controller/                # AuthController, HealthController
    │   │   ├── entity/                    # User, Role
    │   │   ├── repository/                # UserRepository, RoleRepository
    │   │   ├── security/                  # SecurityConfig, JwtUtil, JwtAuthFilter
    │   │   └── service/                   # AuthService
    │   └── resources/
    │       ├── application.yml
    │       ├── logback-spring.xml
    │       └── META-INF/spring.factories  # 注册 ConfigJsonLoader
    └── test/
```

## 8. 已实现接口

| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/health` | 健康检查 | 公开 |
| POST | `/api/auth/login` | 登录,返回 JWT | 公开 |
| GET | `/api/auth/me` | 当前用户信息 | JWT |

## 9. 下一阶段(M1)

- Project / Proposal / Material / MaterialVersion 实体
- 上传下载接口
- Apache Tika 解析入库
- 章节切分
