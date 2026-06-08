# 投委会档案管理系统 — 团队开发档案

> 文档说明: 项目环境/部署/账户/踩坑/约定的**全量档案**,避免分头开发时走弯路
> 版本: v1 / 2026-06-08
> 阅读对象: 任何启动本项目开发的人 / Agent
> 维护: 任何新发现加一行,标日期和原因

---

## 1. 项目基本信息

| 项 | 值 |
|---|---|
| 项目名 | 投委会档案管理系统 |
| 业务定位 | 投委会专属档案管理与智能分析 Web 应用 |
| 服务对象 | 投委会委员、投委会秘书、业务部门、风控合规、法务 |
| 部署规模 | 单机(Windows Server 2012 R2),内网访问 |
| 文档规模预期 | <= 50 GB 原始档案 |
| 用户规模 | 内网同事,数量 < 50 |

---

## 2. 仓库与代码托管

### 2.1 Gitee 仓库

```
仓库:    git@gitee.com:frisker/projects-online.git
组织:    frisker
项目名:  projects-online
本仓库路径: investment-committee-archive-system/
```

### 2.2 分支

| 分支 | 用途 | 推入方式 |
|---|---|---|
| `main` | 生产分支(只读) | **只走 PR,不直推** |
| `minimax` | 集成/开发分支(活跃) | dev 直接 push |

**沙箱 deploy key**(只读权限,可在 Gitee 后台看到):
- 标题: `sandbox-deploy-2026-06-08`
- 私钥位置: `/workspace/projects-online-clone/.ssh/archive_deploy`
- 公钥已加到 Gitee

**本机 user account key**(读写):
- 文件名: `id_ed25519_gitee`(或类似)
- 位置: `C:\Users\<用户>\.ssh\`
- 公钥已加到 Gitee 账号 `frisker`

### 2.3 SSH / Git 配置

**本机**(`~/.ssh/config` 或 `C:\Users\<用户>\.ssh\config`):
```
Host gitee.com
    IdentityFile ~/.ssh/id_ed25519_gitee
    IdentitiesOnly yes
```

**沙箱**:
```bash
export GIT_SSH_COMMAND="ssh -i /workspace/projects-online-clone/.ssh/archive_deploy -o IdentitiesOnly=yes"
```

> ⚠️ **沙箱易失**:`/root/.ssh/` 和 `/opt/` `/tmp/` 容器层 reset 会丢。
> 持久化的只有 `/workspace/`,所以 SSH key 必须放在项目目录的 `.ssh/`,并加 `.gitignore`。
> 项目 `.ssh/` 已在 `.gitignore` 中。

---

## 3. 部署环境

### 3.1 主机

| 项 | 值 |
|---|---|
| 操作系统 | Windows Server 2012 R2 |
| CPU | 单机(具体配置不强) |
| 内存 | 32 GB |
| 硬盘 | >= 500 GB 可用 |
| 网络 | 纯内网隔离,无外网(LLM 调用除外) |
| PowerShell | 5.x(自带,不能升) |
| .NET Framework | 4.x(自带) |

### 3.2 已装软件

| 软件 | 路径 | 备注 |
|---|---|---|
| JDK 17.0.2 | `C:\Program Files\Java\jdk-17.0.2\` | 配 `JAVA_HOME` |
| Maven 3.9.6 | `C:\Program Files\apache-maven-3.9.6\` | 配 `M2_HOME` |
| Node.js 20.x | `C:\Program Files\nodejs\` | 自带 npm |
| MySQL 8.0.16 | `C:\Program Files\MySQL\MySQL Server 8.0\` | 已运行 |
| Git | `C:\Program Files\Git\` | 含 `mingw64/bin/curl.exe`(可靠) |
| Caddy 2.x | `D:\archive\apps\caddy\` | 部署用 |
| WinSW 2.x | `D:\archive\apps\winsw\` | 部署用 |
| Tesseract OCR | **未装**(不做扫描件 OCR,改用 GLM-4V) |

### 3.3 部署目录约定(WinSW + Caddy + MySQL)

```
D:\archive\
├── config\
│   ├── config.json               # 应用配置(含 glm.apiKey)
│   └── config.example.json       # 模板(在仓库内)
├── files\                        # 原始档案(用户上传)
│   └── <project_id>\
│       └── <material_id>\
│           └── <version_id>__<filename>
├── parsed\                       # Tika 解析后的 .txt
│   └── <project_id>\
│       └── <material_id>\
│           └── <version_id>.txt
├── logs\
│   ├── backend.log               # Spring Boot 日志
│   ├── caddy.log
│   └── wsl-archive.log
└── apps\
    ├── backend\
    │   ├── archive.jar           # Spring Boot fat jar
    │   ├── archive.exe           # WinSW 注册的服务
    │   ├── startup.ps1           # 启动脚本
    │   ├── healthcheck.ps1       # 健康检查
    │   └── winsw.xml             # WinSW 配置
    ├── frontend\
    │   └── dist\                 # Vite build 产物
    └── caddy\
        ├── caddy.exe
        └── Caddyfile

数据库:
- host: localhost:3306
- db:   archive_db
- user: root
- pwd:  <在 D:\archive\config\config.json 的 storage 段>
```

### 3.4 启动命令(本机)

```powershell
# 一次性
cd D:\projects-online\investment-committee-archive-system\backend
mvn clean package -DskipTests
# 产物在 target\archive.jar,copy 到 D:\archive\apps\backend\

# 后端(开发模式)
cd D:\projects-online\investment-committee-archive-system\backend
.\startup.ps1

# 前端(开发模式)
cd D:\projects-online\investment-committee-archive-system\frontend
npm run dev
# 浏览器开 http://localhost:5173

# 前端(生产 build)
npm run build
# 产物在 dist\,copy 到 D:\archive\apps\frontend\dist\

# Caddy
cd D:\archive\apps\caddy
.\caddy.exe run
# 或 WinSW 启动
Start-Service archive-caddy
```

### 3.5 PowerShell 5.x + Server 2012 R2 兼容性(已踩坑)

| 坑 | 解决 |
|---|---|
| 中文 UTF-8 脚本解析错 | **用纯 ASCII** 写 .ps1,所有注释/字符串不出现中文 |
| `curl` 是 `Invoke-WebRequest` 别名 | 用 `curl.exe`(git 自带)或 .NET `HttpClient` |
| `Invoke-WebRequest` IndexOutOfRangeException | 不用 |
| `curl.exe` 默认没装 | git 自带 `C:\Program Files\Git\mingw64\bin\curl.exe` |
| body 写 inline 中文乱码 | 写文件 + `--data-binary @file` |
| stderr 抛 RemoteException | 2>&1 合并 |
| `-D` 参数解析错 | `--data-binary "@file"` 加双引号 |

✅ **已修复方案**(见 `backend/startup.ps1` / `healthcheck.ps1`):用 git 自带 curl + body 写到文件。

---

## 4. 沙箱环境(开发 Agent 用)

### 4.1 沙箱限制

- ❌ `/opt` `/tmp` `/root/.ssh` 容器层,容器 reset 会丢
- ✅ `/workspace/` 持久化(绑主机卷)
- ✅ 沙箱内 JDK 17 + Maven 3.9.6 装在 `/workspace/.tools/`

### 4.2 沙箱工具安装

```bash
# JDK
mkdir -p /workspace/.tools
cd /workspace/.tools
wget https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip
# (本项目是 Windows,但沙箱内编译用 Linux JDK 也行,mvn compile 不需要打包 Windows)
# 用 Linux JDK 17.0.2 tarball:
wget https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-x64_bin.tar.gz
tar xzf openjdk-17.0.2_linux-x64_bin.tar.gz
mv jdk-17.0.2 jdk-17.0.2

# Maven
wget https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
tar xzf apache-maven-3.9.6-bin.tar.gz

# 环境变量
export JAVA_HOME=/workspace/.tools/jdk-17.0.2
export M2_HOME=/workspace/.tools/apache-maven-3.9.6
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH

# 验证
java -version
mvn -version

# Maven 依赖缓存位置: /root/.m2/ (沙箱内)
```

### 4.3 沙箱编译命令

```bash
cd /workspace/projects-online-clone/investment-committee-archive-system/backend
mvn compile -DskipTests -B        # 完整编译
mvn compile -DskipTests -B -o     # 离线模式(已缓存依赖)
mvn clean compile -DskipTests -B  # 强制全量
```

> **首次** 编译需要联网拉依赖(慢),后续 `-o` 离线模式即可。

### 4.4 沙箱 SSH key 重建(若丢)

```bash
# 如果 /root/.ssh/ 没了
mkdir -p /root/.ssh
chmod 700 /root/.ssh
# 项目 .ssh/ 还在,直接用
cat /workspace/projects-online-clone/.ssh/config
# 复制到 /root/.ssh/config
cp /workspace/projects-online-clone/.ssh/config /root/.ssh/config
chmod 600 /root/.ssh/config

# 验证
ssh -T git@gitee.com
# 期望: Hi frisker! You've successfully authenticated...
```

---

## 5. 数据库

### 5.1 连接信息

```
host: localhost:3306
db:   archive_db
user: root
pwd:  <D:\archive\config\config.json>
```

### 5.2 已建表(M0~M2)

- `user` / `role` / `user_role`
- `project`
- `proposal`
- `material`
- `material_version`(M2 加了 `parsed_text` + FULLTEXT 索引)

### 5.3 待建表(v2 阶段)

详见 `docs/DB-SCHEMA-v2.md`,包含:
- `chapter_summary`
- `timepoint`
- `todo`
- `trigger_rule` / `trigger_action`
- `extraction_method`
- `comparison_method`
- `dict_type` / `dict_item`
- `audit_log`

迁移脚本: `backend/src/main/resources/db/migration/v2-schema.sql`

### 5.4 MySQL 关键配置

```ini
# my.ini
[mysqld]
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
default-time-zone = '+08:00'
max_connections = 100
innodb_buffer_pool_size = 4G        # 32GB 内存可大方
```

---

## 6. LLM 集成

### 6.1 智谱 GLM-4-Flash(默认)

- 申请地址: https://open.bigmodel.cn/
- 文档: https://open.bigmodel.cn/dev/api
- 限速: 免费版 60 req/min,够用
- API key 存到 `config.json` 的 `llm.glm.apiKey`

### 6.2 切换 Provider(本期新增能力)

`config.json`:
```json
"llm": {
  "provider": "glm",   // 改这个切换
  ...
}
```

实现详见 `provider/` 包:
- `LLMProvider` 接口
- `GLMProvider`(用 Java 11+ `HttpClient`)
- `OpenAIProvider`(OpenAI 兼容)
- `MockProvider`(测试用,返回固定字符串)

### 6.3 Prompt 模板位置

- 字段抽取: `extraction_method` 表(DB)
- 对比方法: `comparison_method` 表(DB)
- 触发规则 prompt: `trigger_rule` 表的 `action_json` 内嵌
- 时点抽取: 硬编码在 `engine/TimepointExtractor.java`(后续可抽到 DB)

---

## 7. 测试账号

| 用户名 | 密码 | 角色 | 用途 |
|---|---|---|---|
| admin | admin123 | admin | 主管账号 |
| user1 | user123 | user | 普通用户测试 |

> **生产前必须改**!目前 dev 密码硬编码在 init.sql。

---

## 8. 已知问题 / TODO

### 8.1 已修(详见 `docs/LESSONS-LEARNED.md`)

- ✅ M0: 5 个 bug
- ✅ M1: 4 个 bug
- ✅ M2: 1 个 bug(GlmService 双引号)
- ✅ M1-5 修复: 4 处 `resp.data` 解构错
- ✅ MaterialVersionService: parseVersion 没 setParsedText 写 DB

### 8.2 未做(留给后续 milestone)

- 移动端响应式
- 数据导出(Excel/PDF)
- 与 OA 系统的 API 对接
- 邮件/钉钉通知
- 真实环境下的 LLM Provider 切换测试
- 字典管理页面前端
- 审计日志查看页
- 异步解析的进度条 / 推送

---

## 9. 联系人 / 责任分工

| 角色 | 负责人 | 备注 |
|---|---|---|
| 产品方 | **frisker**(你) | 业务决策 + 过目 PR |
| 开发主 | Mavis(我) | 编码 + 写文档 + 审 PR |
| 部署 | Mavis(我) | 沙箱编译 + 你本机部署 |
| 运维 | (无人) | 单机,自己维护 |

---

## 10. 关键时间节点

| 日期 | 事件 |
|---|---|
| 2026-06-07 | 项目启动,方案 v1/v2/v3 推到 Gitee |
| 2026-06-08 上午 | M0 端到端跑通(浏览器看到"后端健康:UP") |
| 2026-06-08 下午 | M1 档案 CRUD + M2 知识库问答框架推到 Gitee |
| 2026-06-08 傍晚 | 业务需求 v1 文档 + 5 份配套文档完成 |
| TBD | v2 阶段开工(看完文档你拍板) |

---

## 11. 紧急情况处理

### 11.1 后端崩了

```powershell
# 看日志
Get-Content D:\archive\logs\backend.log -Tail 200

# 重启
Stop-Service archive-backend
Start-Service archive-backend
```

### 11.2 前端崩了

```powershell
# 看 Vite 输出
# 浏览器 F12 Console / Network
# 重启
npm run dev
```

### 11.3 数据库连不上

```powershell
# MySQL 状态
Get-Service MySQL80

# 重启
Restart-Service MySQL80

# 测连接
mysql -u root -p archive_db -e "SELECT 1"
```

### 11.4 LLM API 限速

- 切到 mock 模式:`llm.provider=mock`
- 智谱限速: 免费 60 req/min,程序上加队列/退避

### 11.5 仓库推不上去

```bash
# 看下远程状态
git remote -v

# 看冲突
git fetch origin
git log --oneline origin/minimax -5

# rebase
git rebase origin/minimax
git push origin minimax
```

---

## 12. 任何新发现加这里

```markdown
### [YYYY-MM-DD] <短标题>

**发现**: <现象>
**根因**: <为什么>
**解决**: <怎么修的>
**教训**: <下次怎么避免>
```

每加一条,提交 commit:`docs(repo): TEAM-ARCHIVE 加 <短标题>`

---

*本档案是项目的"活档案",任何团队成员发现新信息都该更新它。*
