# 投委会档案管理系统 — 部署与环境

> 撰写人：Sisyphus | 日期：2026-06-10 | 版本：v1.0

---

## 1. 硬件规格

### 最低配置（实测可运行）

| 资源 | 规格 |
|------|------|
| CPU | 4 核 |
| RAM | 8 GB（可用 6 GB） |
| 磁盘 | 200 GB SSD |
| OS | Windows Server 2012 R2 |

### 推荐配置

| 资源 | 规格 |
|------|------|
| CPU | 4 核 |
| RAM | 16 GB（Spring Boot + MySQL 各占 ~1.5 GB） |
| 磁盘 | 500 GB SSD（含档案文件存储） |
| OS | Windows Server 2019+ |

### 内存分布（典型负载）

| 进程 | 分配 | 实际峰值 |
|------|------|---------|
| Spring Boot JVM | -Xmx1536m | ~1.2 GB |
| MySQL 8.0 | 系统分配 | ~500 MB (innodb_buffer_pool=256M) |
| Caddy | 极低 | ~50 MB |
| **合计** | | **~1.8 GB** |

---

## 2. 软件依赖

### 运行时

| 软件 | 版本 | 验证命令 | 说明 |
|------|------|---------|------|
| JDK | 17 LTS | `java -version` | 生产用 OpenJDK 或 Oracle JDK |
| Maven | 3.8+ | `mvn -v` | 仅构建时需要，生产可移除 |
| MySQL | 8.0.16+ | `mysql --version` | 必须 8.0（FULLTEXT ngram） |
| Node.js | 20 LTS | `node -v` | 仅构建时需要，生产可移除 |
| Caddy | 1.x | `caddy version` | 反代 + TLS |
| WinSW | latest | | Windows 服务包装器 |

### 构建工具

| 工具 | 用途 | 说明 |
|------|------|------|
| Maven 3.8+ | 后端构建 | 需配置阿里云镜像加速 |
| npm 10+ | 前端构建 | 需配置阿里源或能访问 npmjs.org |
| Python 3.x | 诊断脚本 | scripts/diag-125.py 和 test-qa-lmz.py |

---

## 3. 网络拓扑

```
┌─────────────────┐
│   内网用户       │
│  (浏览器)        │
└────────┬────────┘
         │ HTTPS :443 或 HTTP :80
         ▼
┌─────────────────────────────────────────────┐
│  Windows Server (部署主机)                    │
│                                             │
│  Caddy (80/443)                             │
│    ↓                                        │
│  Spring Boot (8080, 仅 127.0.0.1)           │
│    ↓                                        │
│  MySQL (3306, 仅 127.0.0.1)                 │
│                                             │
│  出站:                                       │
│    open.bigmodel.cn:443 (智谱 GLM API)       │
│    git@gitee.com:22 (代码仓库, 开发期)         │
│    maven.aliyun.com:443 (构建, 首次/更新)      │
│    registry.npmjs.org:443 (构建, 首次/更新)    │
└─────────────────────────────────────────────┘
```

### 端口分配

| 端口 | 服务 | 绑定地址 | 说明 |
|------|------|---------|------|
| 80 | Caddy HTTP | 0.0.0.0 | 开发环境/内网HTTP |
| 443 | Caddy HTTPS | 0.0.0.0 | 生产环境 |
| 3306 | MySQL | 127.0.0.1 | 仅本机访问 |
| 5173 | Vite 开发服务器 | 0.0.0.0 | 开发期 |
| 8080 | Spring Boot | 127.0.0.1 | 仅 Caddy 反代访问 |

---

## 4. 目录结构

```
D:\archive\
├── apps\
│   ├── backend\
│   │   ├── archive.jar                 # Spring Boot JAR
│   │   ├── archive-backend-service.exe # WinSW 包装器
│   │   └── backend.xml                 # WinSW 配置
│   ├── caddy\
│   │   ├── caddy.exe                   # Caddy 可执行文件
│   │   └── Caddyfile                   # Caddy 配置
│   └── frontend\dist\                  # Vue 构建产物
│       ├── index.html                  # SPA 入口
│       ├── assets/                     # JS/CSS 静态资源
│       └── favicon.ico
│
├── config\
│   └── config.json                     # 敏感配置（不进 Git）
│
├── files\                              # 原始档案（只读）
│   └── {proposal_id}\                  # 按议案分目录
│       ├── {material_id}\              # 按材料分目录
│       │   └── v{version_no}_{orig_filename}  # 具体文件
│       └── ...
│
├── parsed\                             # Tika 解析缓存
│   └── {material_id}\
│       └── v{version_no}_parsed.txt    # 解析后纯文本
│
├── logs\
│   ├── backend.log                     # Spring Boot 日志（30天滚动）
│   ├── backend-error.log               # 错误日志
│   ├── backend-{date}.log              # 归档日志
│   └── access.log                      # Caddy 访问日志
│
└── db_data\MySQL\                      # MySQL 数据目录
```

---

## 5. 进程管理（WinSW）

### 后端服务

```xml
<!-- backend.xml 关键配置 -->
<executable>java</executable>
<arguments>
  -Xms512m -Xmx1536m
  -Dfile.encoding=UTF-8
  -jar D:\archive\apps\backend\archive.jar
</arguments>
<onfailure action="restart" delay="30 sec"/>
<onfailure action="restart" delay="1 min"/>
<resetsfailure after="1 hour"/>
<log mode="roll">
  <sizeThreshold>10240</sizeThreshold>
  <keepFiles>10</keepFiles>
</log>
```

### 服务注册（管理员 PowerShell）

```powershell
# 注册后端服务
D:\archive\apps\backend\archive-backend-service.exe install
sc config archive-backend start= auto
net start archive-backend

# 注册 Caddy 服务
new-service -name caddy -binaryPathName "D:\archive\apps\caddy\caddy.exe run --config D:\archive\apps\caddy\Caddyfile"
sc config caddy start= auto
net start caddy
```

### 运维命令

```powershell
# 重启后端
net stop archive-backend
net start archive-backend

# 查看日志
Get-Content D:\archive\logs\backend.log -Tail 50 -Wait

# 构建后端
cd D:\projects_new\projects-online\backend
mvn clean package -DskipTests
```

---

## 6. 反向代理（Caddy）

```caddy
# Caddyfile 关键配置
{
  admin off
}

:80 {
  # 限速: 600 请求/分钟
  rate_limit {
    zone dynamic {
      key {remote_host}
      events 600
      window 1m
    }
  }

  # SPA fallback
  root * D:\archive\apps\frontend\dist
  try_files {path} /index.html

  # API 反代到后端
  reverse_proxy /api/* localhost:8080

  # 静态文件服务
  file_server
}
```

生产环境启用 HTTPS（443），Caddy 自动申请 Let's Encrypt 证书。

---

## 7. 配置管理

### 配置文件加载机制

`ConfigJsonLoader`（EnvironmentPostProcessor）按优先级从 4 个位置查找 `config.json`：

```
1. CONFIG_JSON_PATH 环境变量（最高优先级）
2. JAR 同级目录 config/config.json
3. JAR 上一级目录 config/config.json
4. application.yml 默认值（最低优先级）
```

### config.json 字段结构

```json
{
  "glm": {
    "apiKey": "智谱 API Key (必填)"
  },
  "jwt": {
    "secret": "32 字节以上随机字符串 (必填)",
    "expiration": 28800
  },
  "database": {
    "host": "localhost",
    "port": 3306,
    "database": "archive_db",
    "username": "archive_app",
    "password": "密码"
  },
  "storage": {
    "fileRoot": "D:/archive/files",
    "parsedRoot": "D:/archive/parsed",
    "logRoot": "D:/archive/logs"
  },
  "server": {
    "port": 8080
  }
}
```

**安全原则**：config.json 不进 Git，.gitignore 已排除。每个部署者根据 `config.example.json` 创建自己的配置。

---

## 8. 外部依赖清单

| 依赖 | 协议 | 端口 | 用途 | 必要性 |
|------|------|------|------|--------|
| open.bigmodel.cn | HTTPS | 443 | 智谱 GLM-4-Flash API | ✅ 必须（LLM 功能） |
| 无开源 LLM | - | - | 不支持本地部署 | 网络必须可达智谱 |
| Gitee | SSH/HTTPS | 22/443 | 代码仓库 | 🟡 仅开发/构建期 |
| maven.aliyun.com | HTTPS | 443 | Maven 依赖 | 🟡 仅首次构建/更新依赖 |
| npmjs.org | HTTPS | 443 | npm 依赖 | 🟡 仅首次构建/更新依赖 |

**离线要求**：如果需要完全离线部署，必须提前缓存 Maven/npm 依赖，并解决 LLM 依赖问题（当前不支持本地 LLM）。

---

## 9. 启动流程（startup.ps1）

```powershell
# Step 1: 拉取最新代码
git pull origin minimax

# Step 2: Maven 构建
mvn clean package -DskipTests -B

# Step 3: 准备运行环境
#   - 检查/创建 D:\archive\logs 目录
#   - 验证 config.json 存在
#   - 自动移除 UTF-8 BOM（避免 Windows 兼容问题）
#   - 设置 CONFIG_JSON_PATH 环境变量

# Step 4: 启动后端
java -Xms512m -Xmx1536m -Dfile.encoding=UTF-8 -jar archive.jar
```

**幂等性**：脚本设计为可重复执行，不会因为重复运行而出错。

---

## 10. 备份策略

| 项目 | 方式 | 频率 | 保留 |
|------|------|------|------|
| MySQL 数据库 | mysqldump | 每日凌晨 | 7 天 |
| config.json | 手动备份 | 变更时 | 长期 |
| 文件存储 | 文件系统复制 | 手动 | 按需 |

**备份脚本位置**：`config.example.json` 中有 `backup` 段的路径配置模板。

---

## 11. 性能参考

| 场景 | 耗时 | 说明 |
|------|------|------|
| 首页加载（后端健康） | < 500ms | |
| 项目列表 | < 200ms | 分页查询 |
| 全文检索（无 LLM） | < 1s | FULLTEXT 索引 |
| 全文检索 + LLM 重排 | 2-3s | 含 1 次 LLM 调用 |
| Agent 模式（5 步 ReAct） | 10-25s | 含 2-5 次 LLM 调用 |
| 文件上传 + Tika 解析 | 1-5s | 取决于文件大小 |
| LLM 字段抽取（异步） | 3-8s | @Async，不阻塞 |

---

## 12. 容量约束

| 项目 | 上限 | 说明 |
|------|------|------|
| 项目数 | ≤ 300 | Agent FindProjectTool LLM 兜底在该量级表现稳定 |
| 材料版本数 | 单材料 ≤ 50 | 版本控制设计 |
| 聊天记忆 | 单 session ≤ 20 条 | MessageWindowChatMemory 硬限制 |
| 并发用户 | 5-10 人 | 单机 + 智谱 60 req/min 限速 |
| 文件大小 | ≤ 50 MB | Tika 解析超时风险 |
| TOTAL 支持文件类型 | docx/xlsx/pdf/txt | Tika 支持格式 |
