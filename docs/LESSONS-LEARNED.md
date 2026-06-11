# 踩坑记录 — Lessons Learned

> 目的:把 M0 / M1 部署和编码过程中踩过的所有坑写下来,
> 让任何接手的 agent 或人**第一眼看到**哪些地方容易翻车。
>
> **每条都是真实发生过的,不是想象**。每条都标了:
> - 发生时间 / commit
> - 症状(报错原文)
> - 根本原因
> - 修复
> - 教训(下次怎么防)

---

## 分类

1. [配置类(代码里写死的默认值错)](#1-配置类)
2. [编码类(API 用错、import 漏)](#2-编码类)
3. [工具/环境类(Windows PowerShell 5.x 行为)](#3-工具环境类)
4. [数据库类(Schema / 凭据 / 字符集)](#4-数据库类)
5. [部署流程类(CI/CD / 推送 / 沙箱状态)](#5-部署流程类)

---

## 1. 配置类

### 1.1 `application.yml` 自引用循环 (M0)

**Commit**: `3c67e67`

**症状**:
```
Circular placeholder reference 'app.storage.log-root:D:/archive/logs' in property definitions
```

**根因**:
```yaml
app:
  storage:
    log-root: ${app.storage.log-root:D:/archive/logs}   # 写默认值时引用自己
```

Spring 启动时检测到 `X: ${X:default}` 这种**自引用**模式,直接拒绝。

**修复**:
```yaml
app:
  storage:
    log-root: D:/archive/logs   # 字面量
```

**教训**:
- 任何 `${app.X.Y:default}` 占位符,如果**默认值恰好又是自己**,就是自引用
- 正确做法是用 `@ConfigurationProperties`(M1 重构时再做)
- **写完 application.yml 一定** `mvn compile` 验证一次

---

### 1.2 `characterEncoding=utf8mb4` 是 MySQL 服务端 charset,不是 Java charset (M0)

**Commits**: `af05494` `11f7ac1`

**症状**:
```
java.io.UnsupportedEncodingException: utf8mb4
```

**根因**:
```yaml
# 错 ❌
url: jdbc:mysql://...?characterEncoding=utf8mb4

# 对 ✅
url: jdbc:mysql://...?characterEncoding=UTF-8
```

MySQL 服务端 collation 叫 `utf8mb4`(区分 utf8 和真正的 4 字节 UTF-8),但 **Java 客户端 charset** 用 `UTF-8`(Java 标准)。

**正确搭配**:
- 客户端 charset: `characterEncoding=UTF-8`
- 服务端 collation: `connectionCollation=utf8mb4_unicode_ci`
- (其他常用:`characterEncoding=UTF-8` + DB 创建时 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`)

**教训**:
- **charset 名在不同层是不一样**的(MySQL 服务端 vs JDBC 驱动 vs Java)
- 错一个,**所有连接都报** UnicodeEncodingException
- 修复要 **2 个文件同时改**:`application.yml` + `config.example.json`(后者会被 ConfigJsonLoader 覆盖)

---

### 1.3 admin 密码 hash 是网上抄的,实际不匹配 (M0)

**Commit**: `5bb2439`

**症状**: 登录报"密码错误"

**根因**:
- init.sql 里写 `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`
- 我**网上随手抄的**,没验证过
- Python bcrypt 验证返回 `False`(不匹配 `admin123`)

**修复**: 用 `bcrypt.hashpw("admin123".encode(), salt)` 现场生成 `wjN3...vg/Ve`

**教训**:
- **任何写死的密码 hash / token / 密钥,都要在 commit 前用脚本验证一次**——5 秒钟的事
- 推荐用 `bcrypt.hashpw()` 生成,**保留 $2a$ 前缀**(Spring Security 5.x 默认格式)
- 写 init.sql 模板时,加验证 SQL(用现成的 `SELECT * FROM user WHERE username=?` + 服务端校验)

---

## 2. 编码类

### 2.1 `MaterialVersionController` 缺 import (M1-3)

**Commit**: `137e4cb`

**症状**:
```
找不到符号: 类 MaterialResponse
位置: 类 com.archive.controller.MaterialVersionController
```

**根因**: 写 `MaterialVersionController.java` 时用了 `MaterialResponse`,但**没**在 import 段加 `import com.archive.dto.MaterialResponse;`

**修复**: 补 2 行 import。

**教训**:
- Lombok 简化了 entity 写法,但**省不了 import**——Controller / Service 用的 DTO 必须手写 import
- **沙箱无 JDK 没法 `mvn compile` 自测**——M1-3 写完到 deploy 之间漏了一轮编译

---

### 2.2 Tika 2.9 没有 `parseToString(byte[])` 重载 (M1-3)

**Commit**: `165d430`

**症状**:
```
对于 parseToString(byte[]), 找不到合适的方法
  方法 Tika.parseToString(InputStream)  (参数不匹配; byte[]无法转换为InputStream)
  方法 Tika.parseToString(Path)        (参数不匹配; byte[]无法转换为Path)
  方法 Tika.parseToString(File)        (参数不匹配; byte[]无法转换为File)
  方法 Tika.parseToString(URL)         (参数不匹配; byte[]无法转换为URL)
```

**根因**: Tika 1.x 早期有 `parseToString(byte[])`,**Tika 2.9 移除了**,只支持 InputStream/Path/File/URL。我**想当然**认为有 byte[] 重载。

**修复**:
```java
try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
    return tika.parseToString(bis);
}
```

**教训**:
- **任何第三方库的 API 用之前翻文档**——特别是版本升级后(1.x → 2.x)
- 写一个 Service 后,**真去跑一遍那个核心方法**——不要相信 IDE 自动补全的名字
- Tika 2.9 官方 API:`parseToString(InputStream / Path / File / URL)` 4 个,**`detect(byte[])` 还有**但 `parseToString` 没了

### 2.3 Tika 2.9 `detect(byte[])` 不抛 IOException (M1-3)

**Commit**: `0cb59ff`

**症状**:
```
在相应的 try 语句主体中不能抛出异常错误 java.io.IOException
```

**根因**:
- Tika 2.x 文档说 `detect(byte[])` 抛 `IOException`,但**实际编译时方法签名不抛**
- Java 编译器看到 `try { ... } catch (IOException e)` → 报"unreachable catch"
- 同样的问题在 `detect(byte[], String)` 也存在

**修复**:
```java
} catch (Exception e) {  // 不是 IOException
    log.warn("MimeType detect failed for filename={}", filename, e);
    return "application/octet-stream";
}
```

**教训**:
- **编译器权威,文档可能滞后**——Tika 2.9 的真实 API 签名跟 2.0/2.5 文档略有差异
- 对于**可能不抛 checked exception 的方法调用**,catch Exception 更安全
- 写完 Service **真编译一次**才是真理

---

## 3. 工具/环境类

### 3.1 PowerShell 5.x 中文 UTF-8 脚本解析失败 (M0)

**Commit**: `ceb2502`

**症状**:
```
所在位置 ... startup.ps1:21 字符: 1
+ }
+ ~
表达式或语句中包含意外的标记"}"。

所在位置 ... startup.ps1:61 字符: 56
+ java "-Dfile.encoding=UTF-8" -jar ".\target\archive.jar"
+                                                        ~
字符串缺少终止符: "。
```

**根因**:
- Windows Server 2012 R2 + PowerShell 5.x
- PowerShell 5.x **默认**用 `ISO-8859-1` 读 .ps1 文件
- 我写的 .ps1 是 UTF-8 + 中文注释 → 中文被当 ISO-8859-1 解析 → 注释里的引号/花括号乱码 → 语法错

**修复**: **.ps1 文件纯英文 + ASCII**(`file` 命令验证是 `ASCII text`)

**教训**:
- **任何 PowerShell 脚本都用纯英文**,中文改用注释/外部文档
- 写完后 `file xxx.ps1` 应该看到 `ASCII text`
- 跨平台(Win + Linux)的脚本更是纯英文

---

### 3.2 PowerShell 5.x `curl` / `Invoke-WebRequest` 都不可靠,用 .NET HttpClient (M0)

**Commits**: `22ab893`, `f3932d6`

**症状**:
```
& : 无法将"curl.exe"项识别为 cmdlet、函数、脚本文件或可运行程序的名称。
请检查名称的拼写,如果包括路径,请确保路径正确
```

**根因**:
- 第一次修:`curl` 在 PowerShell 5.x 是 `Invoke-WebRequest` 的别名,返回对象不是字符串
- 改成 `& curl.exe`,但 **Windows Server 2012 默认没装 curl.exe**(curl.exe 是 Win10 1803+ / Server 2019+ 自带)
- Server 2012 R2 上 `curl.exe` 找不到,`& curl.exe` 报 CommandNotFoundException

**修复**: 用 `Invoke-WebRequest -UseBasicParsing`(PowerShell 5.x 自带,不依赖 IE 渲染引擎)

```powershell
$resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
$json = $resp.Content
```

**教训**:
- **任何 healthcheck / API 测试脚本,用 `Invoke-WebRequest` + `Invoke-RestMethod`,不要用 curl**
- PowerShell 5.x / Server 2012 R2 都没 curl.exe
- 加 `-UseBasicParsing` 避免依赖 IE;加 `-TimeoutSec` 避免卡死

---

### 3.3 PowerShell 5.x stderr 触发 RemoteException (M0)

**Commits**: `a8fc056` `3ae81e4` `cd8b59c`

**症状**:
```
git.exe : From gitee.com:frisker/projects-online
所在位置 startup.ps1:20 字符: 11
+ $gitLog = & git pull origin minimax 2>&1 | Out-String
+           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : NotSpecified: (From gitee.com:frisker/projects-online:String) [], RemoteException
    + FullyQualifiedErrorId : NativeCommandError
```

**根因**:
- PowerShell 5.x 把 stderr 任何输出当 `RemoteException` 异常
- 即使 `$ErrorActionPreference = "Continue"` 也会触发
- 哪怕用 `cmd /c` 包装或 `2>&1` 重定向,git 的 `From gitee.com:...` 这种"信息行"也照抛

**修复**: `*>&1` 走 PowerShell 自己的输出流 + `Out-String` 收字符串(不让 stderr 走到 PowerShell 错误流)

```powershell
# 错 ❌
$gitLog = git pull origin minimax 2>&1

# 对 ✅
& git pull origin minimax *>&1 | Out-Null
$gitLog = & git pull origin minimax 2>&1 | Out-String
```

**教训**:
- PowerShell 5.x stderr 处理是公认的"老年病"——很多人栽过
- 写 .ps1 调外部命令,默认**就**用 `*>&1 | Out-String`,别管当前 PowerShell 版本
- 不要用 `$ErrorActionPreference = "Stop"`(太严格)
- 写完在 Windows Server 2012 R2 上**真跑一次**(我这沙箱没这环境,跑不了)

---

### 3.4 `java -jar -Dfile.encoding=UTF-8` PowerShell 解析错 (M0)

**症状**:
```
Error: Unable to access jarfile .encoding=UTF-8
```

**根因**: PowerShell 把 `-Dfile.encoding=UTF-8` 拆成 3 个参数(`-D`, `file.encoding=UTF-8` 被拆成 `file.encoding`, `=`, `UTF-8`)

**修复**:
```powershell
# 错 ❌
java -Dfile.encoding=UTF-8 -jar archive.jar

# 对 ✅
java "-Dfile.encoding=UTF-8" -jar archive.jar
```

**教训**:
- PowerShell 调用外部命令时,带 `=` / `,` / `&` 的参数**用双引号包起来**
- 或者用环境变量:`$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"`

---

## 4. 数据库类

### 4.1 UTF-8 BOM 让 Jackson 解析失败 (M0)
### 4.2 修复 ApiResponse 后,详情/编辑页漏改,导致页面空白 (M1-5)

**Commit**: `3b487ef`

**症状**: 浏览器点项目"详情" → 页面空白

**根因**:
- M1-5 写 ProjectDetail / ProposalDetail / ProjectForm 时,fetch 模式是:
  ```js
  const resp: any = await getProject(...)
  form.value = resp.data
  ```
- 后来 M1-5 修复 api/archive.ts 用 getData<T> 拆 ApiResponse,getProject 直接返回 T
- **但 ProjectDetail / ProposalDetail / ProjectForm 没改**,还在 `resp.data`
- 现在 `form.value = resp.data` = undefined → 模板崩 → 空白

**教训**:
- 任何 **改 fetch 解构** 的提交,必须**全局**扫所有 .vue 文件
- 改 API 返回类型时,所有调用方都得跟着改
- 写详情/编辑页的"加载数据"逻辑时,TypeScript 类型变化会标红,**但 as any 让它通过**,bug 漏到运行期

**Commits**: `1af3dc0` `2140440`

**症状**:
```
Unexpected character ('锘?' (code 65279 / 0xFEFF)): expected a valid value
```

**根因**:
- Windows Notepad 默认以 **UTF-8 + BOM** 保存 .json
- Jackson 解析器**不自动去 BOM**,看到 `0xFEFF` 当非法字符
- `ConfigJsonLoader` 用 Jackson,加载 `D:\archive\config\config.json` 直接挂

**修复**(2 处):
1. `ConfigJsonLoader` 读取后,首字符 `0xFEFF` 检测到就 `substring(1)`
2. `startup.ps1` 启动前**自动剥 BOM**(友好提示)

**教训**:
- **任何读 JSON 的代码都要考虑 BOM**——`Files.readString(path, UTF-8)` 不去 BOM
- 工具类代码(`ConfigJsonLoader`)**自身要容错**,不要依赖用户用"无 BOM"格式保存
- 写 test case 加一个带 BOM 的 JSON 文件

---

### 4.2 `ConfigJsonLoader` 找不到生产路径 (M0)

**Commit**: `cd8b59c`

**症状**:
```
Access denied for user 'archive_app'@'localhost' (using password: NO)
```

**根因**:
- `ConfigJsonLoader` 默认查找:
  1. `${CONFIG_JSON_PATH}` 环境变量
  2. `./config/config.json`(JAR 同级)
  3. `../config/config.json`(上一级)
- 生产部署路径是 `D:\archive\config\config.json`
- 3 个默认路径**都找不到** → 走 `application.yml` 默认值 → password = 空 → MySQL 拒登录

**修复**:
- `startup.ps1` 启动前设 `$env:CONFIG_JSON_PATH = "D:\archive\config\config.json"`
- ConfigJsonLoader 优先看这个环境变量

**教训**:
- 开发期路径 ≠ 部署期路径——**写 Loader 时要考虑生产部署场景**
- M1 重构时,把 `D:\archive\config\config.json` 加进查找优先级(第 0 优先级)
- 写完 Loader,**真去生产路径下放一个 config.json 跑一次**

---

## 5. 部署流程类

### 5.1 沙箱重启后 SSH key 丢失 (M0/M1)

**时间**: 2026-06-07 晚 → 2026-06-08 早

**症状**:
```
git@gitee.com: Permission denied (publickey).
```

**根因**:
- 最初 SSH key 放 `/tmp/archive_deploy`
- `/tmp` 在容器里**易失**,沙箱重启就清空
- 即使后面放 `/root/.ssh/archive_deploy`,容器层升级/重置还是会丢
- 沙箱 secret `GITEE_SSH_KEY` 是占位符 "-----BEGIN"(只有 11 字节,不是真 key)

**修复**(M1 阶段,Commit `7cdd290` + 新 deploy key `sandbox-deploy-2026-06-08`):
- **SSH key 放项目根 `.ssh/` 目录**——`git clone` 时跟着走,容器刷也不丢
- `.gitignore` 写 `.ssh/`,**保证 key 不会进 git 仓库**
- Gitee 仓库管理加 deploy key,标题 `sandbox-deploy-2026-06-08`

**教训**:
- **任何需要持久化的 secret(SSH key / API token / 数据库密码)在沙箱里:**
  1. **绝不放 `/tmp`**(易失)
  2. **绝不放 `~/.ssh/`**(容器层可能丢)
  3. **放项目目录 `.ssh/` 或 `.secrets/`**——跟着仓库走
  4. **`.gitignore` 兜底**——别让 key 进 git
- **任何 push 前,自己 `ssh -T git@gitee.com` 验证一次**
- 沙箱 secret 工具有"占位符"陷阱——`GITEE_SSH_KEY` 显示"-----BEGIN"但只有 11 字节,不是真 key,**别相信环境变量名**

---

### 5.2 沙箱没装 JDK,Java 代码没法自测 (M1)

**时间**: M1-3 提交后 → M1-3 部署失败

**症状**: M1-3 写完 2 个 bug 漏到生产
- `MaterialResponse` 缺 import
- `Tika.parseToString(byte[])` API 用错

**根因**:
- 沙箱里 `apt-get install openjdk-17` 网络不通(apt 仓库访问不到)
- 沙箱也没预设 JDK,只有 Python
- 我**没**装 JDK,直接 commit 推代码,赌"看起来对就行"

**教训**:
- **任何写 Java 代码的 agent,沙箱里必须有 JDK 17**——Mavis 团队**默认要装**:
  ```bash
  # setup-jdk.sh(每个 agent session 启动时跑一次)
  curl -L -o /tmp/jdk.tar.gz "https://..."
  tar xzf /tmp/jdk.tar.gz -C /opt/
  export JAVA_HOME=/opt/jdk-17
  export PATH=$JAVA_HOME/bin:$PATH
  ```
- 写完 Java 后**必跑** `mvn compile`,有错就修到过
- 不要让用户**当 IDE 调试员**——5 次重跑浪费时间

---

## 6. 部署脚本类

### 6.1 startup.ps1 编码要用 ASCII (M0)

见 [3.1](#31-powershell-5x-中文-utf-8-脚本解析失败-m0)

---

## 6.5 智能问答 Agent 类 (Plan I)

### 6.5.1 Spring AI 1.1 公开 API 没 `JdbcChatMemory` class

**Commit**: `52cbbb7` (Sisyphus 犯) + `ab57ef3` (Mavis 修)

**症状**:
- `mvn compile` 直接挂: `cannot find symbol: class JdbcChatMemory`
- 错误位置: `AgentConfig.java` + `ChatMemoryConfig.java` (同一个 bug 出现 2 次)

**根因**:
- Sisyphus 看 spec 写 "JdbcChatMemory" 就信了
- 实际 Spring AI 1.1 **公开 API 完全没有 `JdbcChatMemory` class**
- 真实 class 是 `JdbcChatMemoryRepository` (在 `org.springframework.ai.chat.memory.repository.jdbc` 包)
- 配套: `MessageWindowChatMemory` (滑动窗口) 代替 `JdbcChatMemory` 本身

**修复**:
```java
// 错 (Sisyphus 原版)
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
@Bean public JdbcChatMemory jdbcChatMemory(DataSource ds) { return new JdbcChatMemory(ds); }

// 对 (Mavis 修后)
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
@Bean public JdbcChatMemoryRepository repo(DataSource ds) {
    return JdbcChatMemoryRepository.builder().dataSource(ds).tableName("chat_memory").build();
}
@Bean public ChatMemory memory(JdbcChatMemoryRepository repo) {
    return MessageWindowChatMemory.builder().chatMemoryRepository(repo).maxMessages(20).build();
}
```

**教训**:
- **接 spec 时, API class 名必查** (Maven Central / Spring AI 官方文档)
- **不要 "spec 写了就 OK"** —— 1.1 vs 1.2 vs 阿里云 Spring AI Alibaba 概念很容易搞混
- **接手 AI 完工前必须 `mvn compile` 验证**, 0 错再 push

### 6.5.2 QueryMysqlTool 漏 4 重安全加固 (P0-1 ~ P0-4)

**Commit**: `ab57ef3` (Mavis 修)

**症状**:
- spec 明确写 "6 个 aggregate / 10 个 operator / 3 重安全加固"
- Sisyphus 实现: 5 个 aggregate / 9 个 operator / 1 重加固
- 漏 5 个, 4 个 P0 + 1 个 P1

**漏什么**:
| 项 | spec 要求 | Sisyphus 实现 | 业务影响 |
|---|---|---|---|
| aggregate | `count/sum/avg/max/min/group_by` (6 个) | 5 个, 漏 `group_by` | "还没结清有几个" 需 `group_by(status)` 才有意义 |
| operator | 10 个 | 9 个, 漏 `is_not_null` | 查"未结清" / "已结清" 都要 `is_not_null` |
| IN 长度上限 | ≤ 50 | 0 | LLM 可能生成 `IN (a, b, c, ..., 1000 个值)` 拖死 DB |
| LIKE 转义 | 转义 `%` `_` | 0 | LLM 输出 `100%` 走 LIKE 会变通配符 (SQL 注入风险) |

**修复**:
- `QueryMysqlTool.ALLOWED_AGGREGATES` 加 `group_by`
- `QueryMysqlTool.ALLOWED_OPERATORS` 加 `is_not_null`
- 加 `MAX_IN_VALUES = 50` 常量 + IN 长度校验
- 加 `escapeLikePattern()` 方法, 转义 `\ % _` (顺序避免双重转义)

**教训**:
- **接任务前先看 spec 验收清单的"数"** (10 个 operator / 6 个 aggregate / 3 重加固)
- 完工**逐项自检** "我真都做了吗", 没做就别 commit
- "快" 不等于 "好" —— 业务逻辑对, 安全细节漏, 等于没做

### 6.5.3 `@SpringBootTest` 启动要 GLM key, 缺 `application-test.yml` (P1-1)

**Commit**: `ab57ef3` (Mavis 修)

**症状**:
- Sisyphus 写了 10 测例 `AgentIntegrationTest`
- 但**没配套 `application-test.yml`**
- 跑 `mvn test` 会要真 `GLM_API_KEY` 环境变量
- 沙箱里没 key, 测例 100% 启动挂

**根因**:
- spec 写 "10 测例", 但没写 "测试要 application-test.yml"
- 接手 AI 想: "spec 没说要, 就不加"
- 结果: **测试跑挂**

**修复**:
加 `application-test.yml`:
```yaml
spring:
  ai:
    openai:
      api-key: test-mock-key-not-used  # 让 OpenAiChatModel Bean 实例化
      base-url: http://localhost:0
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL  # H2 代替 MySQL
```

**教训**:
- 写测试时, 先想"测试要哪些 Bean" → 反推需要哪些配置
- `@SpringBootTest` 必须有 `application-test.yml` (或 `application-test.properties`)
- 接手 AI 写完测试, **应该 `mvn test` 真的跑一次** (没 GLM key 也要想办法 mock)
- "spec 没写" ≠ "不用做" —— 推理该做啥

---

## 7. 经验教训总结(接手 agent 第一眼看)

**优先级:致命 → 烦人**

1. **任何 push 前,先 `mvn compile` + `mvn package`**——别赌
2. **PowerShell 5.x 上 .ps1 用纯英文 + ASCII**——别用中文
3. **第三方库 API 用之前翻文档**——Tika 2.x 移除了一些 1.x 的便捷重载
4. **charset 在不同层名字不同**——`UTF-8`(Java) ≠ `utf8mb4`(MySQL)
5. **写死的 hash/token/密码都要验证**——5 秒省 1 小时
6. **沙箱 SSH key 放项目根 .ssh/ + .gitignore 兜底**——别用 /tmp
7. **application.yml 避免自引用**——用字面量或 @ConfigurationProperties
8. **路径里的 BOM 自行处理**——不要依赖"用户用无 BOM 编辑器"
9. **ConfigJsonLoader 这类查找器要支持生产路径**——不只开发路径
10. **Curl 写脚本要分清 PowerShell 5.x 别名 vs curl.exe**——用 & curl.exe 强制
11. **Spring AI 1.1 API class 名必查** —— spec 写 `JdbcChatMemory` 是错的, 真实是 `JdbcChatMemoryRepository`
12. **接手 AI 完工前必 `mvn compile`** —— 5 P0 漏都是因为没真编译过
13. **验收清单要逐项自检"数"** —— 10 个 operator / 6 个 aggregate / 3 重加固, 不能"差不多"
14. **测例调真 LLM 时别用 @MockBean 关键依赖** —— Mockito 永远返 null 挡住真路径
15. **Few-shot 跟工具 signature 字段名必须一致** —— LLM 严格按 Few-shot 调
16. **H2 测例要禁 Flyway** —— `user` 是关键字冲突 + JPA auto DDL 更可控
17. **MySQL native FULLTEXT 测例要走 findByCode 不走 FULLTEXT** —— H2 没 MATCH 函数
18. **测例问题别用自然语言歧义词** —— "否决" LLM 会一直 ask_clarification
19. **测例断言用"业务结果" 不用"必走某工具"** —— LLM 智能选择应该被接受
20. **加新参数前 grep 旧代码** —— `body.put("temperature", 0.3)` 硬编码会覆盖参数

---

## 8. 协议变更日志(每次新坑加入,记 commit)

| 坑号 | 描述 | 关联 commit |
|---|---|---|
| 1.1 | application.yml 自引用 | 3c67e67 |
| 1.2 | characterEncoding=utf8mb4 | af05494, 11f7ac1 |
| 1.3 | admin 密码 hash 错 | 5bb2439 |
| 2.1 | MaterialResponse 缺 import | 137e4cb |
| 2.2 | Tika parseToString(byte[]) API 错 | 165d430 |
| 2.3 | Tika detect 不抛 IOException | 0cb59ff |
| 3.1 | PowerShell 5.x 中文 UTF-8 脚本 | ceb2502 |
| 3.2 | PS 5.x curl/Invoke-WebRequest 都不可靠 | 22ab893, f3932d6, 4d5c368 |
| 3.3 | PowerShell 5.x RemoteException | a8fc056, 3ae81e4, cd8b59c |
| 3.4 | PowerShell -D 参数解析 | (M0-3 解决) |
| 4.1 | UTF-8 BOM | 1af3dc0, 2140440 |
| 4.2 | ConfigJsonLoader 路径 | cd8b59c |
| 5.1 | 沙箱 SSH key 丢失 | 7cdd290 |
| 5.2 | 沙箱没 JDK | (待修复) |
| 6.5.1 | Spring AI 1.1 `JdbcChatMemory` 不存在 | `52cbbb7` (Sisyphus 犯) + `ab57ef3` (Mavis 修) |
| 6.5.2 | QueryMysqlTool 漏 5 P0 (aggregate / operator / IN / LIKE) | `ab57ef3` |
| 6.5.3 | `@SpringBootTest` 缺 `application-test.yml` | `ab57ef3` |
| 6.5.4 | 智谱 v4 跟 OpenAI v1 协议不兼容, 需自定义 ChatModel | `4ff8b98` |
| 6.5.5 | GlmService 硬编码 `temperature=0.3` 覆盖参数, LLM 调温不生效 | `c3ae805` |
| 6.5.6 | `@MockBean GlmService` 永远返 null, Agent 集成测必须真调 LLM | `c3ae805` |
| 6.5.7 | H2 测例 `user` 关键字冲突, 必须禁 Flyway + JPA auto DDL + `globally_quoted_identifiers` | `c3ae805` |
| 6.5.8 | 测例 setup 没种子数据, find_project 走 FULLTEXT 在 H2 崩 (MATCH 函数 H2 没有) | `c3ae805` |
| 6.5.9 | Few-shot 字段名跟工具不一致, LLM 按错的 Few-shot 调 | `c3ae805` |

### 5. Agent 写 Java 代码不编译就 push(C-2 三 Engine 错)

**Commit**: 8ca625e / 1e00136(原 28 个 plan commit 都有这毛病)

**症状**:
```
mvn compile
[ERROR] cannot find symbol: class LLMProviderFactory
  location: package com.archive.service
[ERROR] cannot find symbol: method chatJson(String, String)
  location: LLMProviderFactory
[ERROR] method log in class AuditLogService cannot be applied to given types
  required: 10 params / found: 5 params
```

**根因**:
- Agent 写 Engine 时**调错 LLMProviderFactory 方法**(方法签名是 chatJson(sys, user, Class),Agent 当成 chatJson(prompt, schema))
- Agent 假设了 `parseJsonResponse` / `parseJsonArrayResponse` 这俩**不存在**的方法
- Agent 写 auditLogService.log(...) 时**参错位**——当成 5 参调,实际 10 参

**修复**:
- LLMProviderFactory 改成 provider = factory.getProvider(); provider.chat(sys, user)
- 解析用 Jackson ObjectMapper + TypeReference,允许 markdown 围栏
- auditLogService.logSimple(...) 4 参版替代 5 参版
- createFromTrigger 改成解析 JSON + 组 7 参

**教训**:
- 任何 commit 含 .java,**必先 mvn compile 验证**,0 错再 push
- Agent 写代码**没有类型系统提示**(用了 lombok @Data 但 ObjectMapper.readValue 异常在 catch 里被吞)
- 写"调用 service"的代码前,先看 service 实际签名
- **验收环节必须有"编译通过"硬门槛**,不只是 commit 写完了

### 6. Vue import 风格不一致 — default vs named(G-5 LlmUsage 白屏)

**Commit**: 242560f(无效果) → 66379b3(本修复)

**症状**:
- 跳 /llm-usage 空白
- Console: `Uncaught SyntaxError: The requested module '/src/api/http.ts' does not provide an export named 'http' (at LlmUsage.vue? [sm]:11:10)`

**根因**:
- 旧代码统一用 `import http from '@/api/http'`(default import)
- 我写 LlmUsage.vue 时随手用 `import { http } from '@/api/http'`(named import)
- 但 http.ts 只有 `export default http` + `export function getData`,**没有 named `export { http }`**
- 整个文件 import 失败 → LlmUsage.vue 整个挂 → router-view 空 → 白屏

**修复**:
- LlmUsage.vue 改用 `import http, { getData } from '@/api/http'`
- http.ts 末尾加 `export { http }` 命名导出,两种写法都支持

**教训**:
- 写新 .vue 前**先看现有模块怎么 import**(`grep "import.*http" frontend/src/api/`)
- named vs default 不一致是 JS 模块系统最大坑之一
- TypeScript 在 Vue SFC `<script setup>` 里**不会**警告 import 错(运行时才崩)

---

## 9. 部署/网络类 — 2026-06-10 实战 (今天)

### 9.1 CORS 白名单遗漏内网 IP 段, 浏览器登录报"权限不足"

**Commit**: `013e0c9` (fix)

**症状**:
- 后端 API 单独用 curl/Postman 调 100% 正常
- 浏览器登录页输账号密码点登录 → 前端 axios 拦截器显示 `ElMessage.error('权限不足')`
- F12 Network 看 `/api/auth/login` 状态码 `403 Forbidden`,Body `Invalid CORS request`

**根因**:
- `SecurityConfig.corsConfigurationSource()` 的 `allowedOriginPatterns` 只写了:
  ```java
  "http://localhost:*", "http://127.0.0.1:*", "https://*"
  ```
- 实际部署: 前端在 `182.168.1.125:5173`, 浏览器发请求时 Origin 头是 `http://182.168.1.125:5173`
- **CORS 是浏览器独有的强校验**, curl/Postman 不发 Origin 头, 后端只看到浏览器请求 + 不在白名单的 Origin → 拒 → 403
- 前端 axios 拦截器看到 403 → 统一显示"权限不足", 误导排查方向 (以为是角色权限问题)

**修复** (CORS 修复 patch):
```java
cfg.setAllowedOriginPatterns(List.of(
    "http://localhost:*",
    "http://127.0.0.1:*",
    "http://192.168.*:*",   // 家/办公网
    "http://10.*:*",        // 公司内网
    "http://172.16.*:*",    // RFC1918 私网
    "http://182.*:*",       // 182.168.1.125 所在网段 (本项目)
    "https://*"
));
```

**教训**:
- **CORS 是浏览器独占, curl 测不出** — 任何前后端分离项目, 浏览器验证是不可省略的环节
- 前端 axios 拦截器的"权限不足"提示**信息量不够**, 应该把 403 的 Response body 也 `console.error` 出来 (本期改进项)
- 开发期 CORS 白名单**必须覆盖内网常见网段** (192.168/10/172.16), 不要只写 localhost
- `182.168.1.125` 不是 RFC1918 私网 (标准私网是 10/8, 172.16/12, 192.168/16) — 可能是公司特殊网段, 实际项目里遇到过

---

### 9.2 `mvn clean` 失败但被忽略, 旧 jar 继续被用

**Commit**: 隐式 (本次部署期间)

**症状**:
- 修了 CORS 改, `mvn clean package` 跑完
- 重启 java 进程, **新 jar 启动成功** (backend.log 显示 `Started ArchiveApplication in 22.9s`)
- **但** `/api/auth/login` 仍返回 403 CORS 错误 (跟没改 CORS 之前一样)

**根因**:
- 前一次 mvn 命令输出 `BUILD SUCCESS`, 但**实际** `maven-clean-plugin` 阶段就失败了:
  ```
  Failed to execute goal org.apache.maven.plugins:maven-clean-plugin:3.3.2:clean
  (default-clean) on project archive-backend: Failed to clean project:
  Failed to delete D:\projects-online\backend\target\archive.jar
  ```
- 旧 jar **被 java 进程占着** (Windows 文件句柄未释放), mvn 删不掉
- mvn 跳过 clean → 用**旧 target/ 目录里的旧 class** 继续 compile + package
- 结果: 新 jar **包含了旧 SecurityConfig.class** (没 192.168/10/172.16/182)
- 看着像"修了但没生效"

**修复**:
```powershell
# 1) 先杀 java + 等 5 秒 + 删整个 target
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 5
Remove-Item -Recurse -Force D:\projects-online\backend\target

# 2) 再打包
Set-Location D:\projects-online\backend
mvn -DskipTests clean package
```

**教训**:
- **"BUILD SUCCESS" ≠ "jar 是新的"** — 任何时候都要 `Get-Item target\archive.jar | Select LastWriteTime` 验证 jar 时间戳
- Windows 上 `Stop-Process -Force` 后**文件句柄要等几秒才完全释放** (3-5s), 删除 target 前必须 sleep
- mvn clean 失败时, **整个生命周期都不可信** — 应该先 `Remove-Item -Recurse target` 手动清, 再 `mvn package`
- 加 `2>&1 | Select-String "ERROR|FAILURE"` **过滤** mvn 输出, 关键错误才不会被 `BUILD SUCCESS` 掩盖

---

### 9.3 `git pull` 静默失败, 部署机 HEAD 落后于 origin

**Commit**: 隐式 (本次部署期间)

**症状**:
- 本地 `git log` 显示 `HEAD = 013e0c9` (含 CORS 修复)
- 推送成功后, 在 125 上跑 `git pull origin main`, 命令返回"成功"
- 但 125 上 `git log` HEAD 仍是 `9774b8e` (**少了 CORS commit**)
- 后续 mvn package 出来的 jar 不含 CORS 修复

**根因**:
- 125 上之前是 `minimax` 分支, 后来切到 `main` 但**没真正同步**最新 commit
- `git pull origin main` 可能在某些状态 (detached HEAD / 未跟踪文件冲突 / Gitee SSH 临时不可达) 静默失败
- 命令退出码 0 但**没拉到东西**

**修复**:
```powershell
# 显式分两步走 + 验证
git -C D:\projects-online fetch origin main          # 显式 fetch
git -C D:\projects-online merge --ff-only origin/main # fast-forward 合并 (不 ff 时会报错)
git -C D:\projects-online log --oneline -3            # 验证 HEAD
```

**教训**:
- **`git pull` 是"fetch + merge"两步的简写**, 失败模式不直观 — 拆成 `fetch` + `merge --ff-only`, 失败时报错更清晰
- 每次 `git pull` 后**必跑** `git log HEAD..origin/main --oneline` 验证 (空输出 = 已最新)
- 部署机 + 本地 + 远端**三方同步检查**是部署前的标准动作, 不要相信"我 pull 了"的口头确认
- 部署机上 `git remote -v` 确认远端 URL 是对的 (本项目 125 上是 `git@gitee.com:frisker/projects-online.git`)

---

### 9.4 Vite 5 dev server 缓存脏, 浏览器所有请求 pending

**Commit**: 隐式 (本次部署期间, 已修复)

**症状**:
- Vite dev server 启动 OK (日志 `VITE v5.4.21 ready in 2331ms`)
- 浏览器访问 `http://182.168.1.125:5173/login` → 页面空白
- F12 Network 看**所有请求**状态 `pending` (不返回, 不报错)
- F12 Console 空
- 后端 java 进程在跑, 8080 健康

**根因**:
- `node_modules/.vite/` 缓存目录的**依赖优化信息过期/损坏**
- Vite 5 启动时**预扫描** element-plus 等依赖做 deps optimization (用 esbuild 子进程)
- 缓存脏了之后, esbuild 在某个 element-plus 子模块的 AST 解析上**死循环或死锁**
- 表现: HTML 模板 `/` 能返 (静态), 但所有 `/src/*.ts` 编译请求**全部挂起**

**修复**:
```powershell
# 1) 停 Vite
Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 3

# 2) 删 .vite 缓存
Remove-Item -Recurse -Force D:\projects-online\frontend\node_modules\.vite

# 3) 重启 Vite (重新做 deps optimization)
Set-Location D:\projects-online\frontend
npm run dev
```

重启后日志:
```
VITE v5.4.21  ready in 2331 ms
[vite] new dependencies optimized: element-plus/es, element-plus/es/components/...
[vite] optimized dependencies changed. reloading
```

**教训**:
- **Vite 5 + 大型 UI 库 (element-plus) + 缓存脏** = dev server 死锁, 表现是 "页面空白 + 请求全 pending"
- **重启 Vite 前必删** `node_modules/.vite/` 缓存, 否则旧的脏缓存还会生效
- `npm run dev` 启动时**关注 "new dependencies optimized" 日志**, 长时间没出现 = 预扫描卡住
- 生产构建 (`npm run build`) 不受影响, 只 dev server 会卡 — 别因为 dev 挂了急着动 build 配置
- 排查顺序: **清缓存 → 重启 → 验证 Network**, 比反复 reload 页面高效 10 倍

---

### 9.5 Spring Boot Actuator health 503 DOWN: mail health 缩进错位

**Commit**: `9774b8e` (fix)

**症状**:
- `/actuator/health` 返回 `503 DOWN`, `groups: ["liveness", "readiness"]`
- `/actuator/health/liveness` 和 `/health/readiness` 单独都返回 `200 UP` ✅
- backend.log 有 `SocketTimeoutException: Connect timed out` 在 `org.eclipse.angus.mail.smtp.SMTPTransport`

**根因**:
- `5c14ab9` 之前尝试禁 mail health indicator, commit message 写的是 `management.health.mail.enabled: false`
- 但**实际 patch 把 mail.enabled 加错位置** (在 endpoint.health 下, 不是 management.health 下):
  ```yaml
  management:
    endpoint:
      health:
        show-details: when-authorized
        probes:
          enabled: true
        mail:              # ❌ 错 — 在 endpoint 块内
          enabled: false
  ```
- Spring 的 mail health indicator 关闭键是 `management.health.mail.enabled` (注意是 `management.health` 不是 `endpoint.health`)
- `endpoint.health.mail.enabled` 这个 key **Spring 根本不识别**, mail health 继续在跑
- mail health 尝试连 `smtp.internal.example.cn` (占位符) → 超时 → DOWN
- liveness/readiness group **默认不包含 mail indicator**, 所以单独看是 UP, 只有聚合 health 总览才暴露问题

**修复** (把 mail.enabled 移出 endpoint 块):
```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:                  # ✅ 对 — management.health 命名空间
    mail:
      enabled: false
```

**教训**:
- **Spring Boot Actuator 配置有 3 个独立命名空间**:
  1. `management.endpoints.web.exposure.include` — 暴露哪些端点
  2. `management.endpoint.<name>.*` — 单独端点的配置 (如 show-details, probes)
  3. `management.health.<indicator>.*` — 单个 health indicator 的配置 (如 mail.enabled, db.enabled)
- **commit message 写什么 ≠ 代码做了什么** — review diff 时要核对 YAML 实际缩进位置, 不能信 message
- `liveness/readiness` 不等于 `总览 health` — 总览包含**所有** indicator, liveness/readiness 包含**部分** (Spring 默认 livenessState / readinessState)
- 排查 health DOWN 时: **看 stderr 日志找具体超时 indicator** + **show-details=always 拿到 details** (本项目 show-details 配 `when-authorized`, 未认证拿不到 details, 排查时要临时改成 `always`)

---

## 10. 经验教训总结 v2 (2026-06-10 增补)

| # | 教训 |
|---|---|
| 21 | **CORS 是浏览器独占, curl 测不出** — 前后端分离必须浏览器验证, 内网网段 (192.168/10/172.16) 必须加白名单 |
| 22 | **"BUILD SUCCESS" ≠ "jar 是新的"** — 部署前必查 `archive.jar` LastWriteTime, mvn clean 失败要手动删 target |
| 23 | **`git pull` 静默失败是真实风险** — 拆成 fetch + merge --ff-only, 每次必跑 `git log HEAD..origin/main` 验证 |
| 24 | **Vite 5 + element-plus 缓存脏** = dev server 死锁 — 重启前必删 `node_modules/.vite/` |
| 25 | **Spring Actuator health 配置 3 个命名空间别搞混** — `management.health.<indicator>` 不是 `management.endpoint.health.<indicator>` |
| 26 | **liveness/readiness ≠ 总览 health** — 排查总览 DOWN 时单独看 liveness/readiness 是 200 没意义, 必看 stderr 找具体 indicator |
| 27 | **"权限不足"是 axios 拦截器对 403 的统一提示** — 前端拦截器错误信息太粗, 应该 console.error Response body 帮排查 |

---

## 11. 协议变更日志 v2 (2026-06-10 增补)

| 坑号 | 描述 | 关联 commit |
|---|---|---|
| 9.1 | CORS 白名单遗漏内网 IP 段 | `013e0c9` |
| 9.2 | mvn clean 失败旧 jar 继续被用 | (本次部署隐式) |
| 9.3 | git pull 静默失败 HEAD 落后 | (本次部署隐式) |
| 9.4 | Vite 5 + element-plus 缓存脏死锁 | (本次部署隐式) |
| 9.5 | Actuator health mail 配置缩进错 | `9774b8e` |

---

## 12. v1.1 关键教训 (P0-24+, MOD-06, 2026-06-11)

### P0-24.1 RBAC 双轨兼容（v1.1 RBAC 5 角色改造）
**现象**: v1.1 改 RBAC 时差点删除 `user.role_id` 字段
**教训**: v1.0 单用户路径千万不能断，新增 `user_role` 多对多是主路径，`user.role_id` 保留兼容
**预防**: 任何升级前先列"v1.0 用户操作清单"逐项验证

### P0-24.2 乐观锁严格度（v1.1 灰度）
**现象**: v1.1 乐观锁 `strict=true` 会导致单用户系统频繁 409
**教训**: v1.1 期 `strict=false`（冲突仅记日志），v2 多用户时切 true
**预防**: 配置项加详细注释 + README 标注 (D-3)

### P0-24.3 网络查字典降级（v1.1 RI-50）
**现象**: 内网全失败时网络查字典抛异常导致 AgentEngine 崩溃
**教训**: 工具级降级必须返回 `{found: false, reason: ...}` 而非抛异常
**预防**: 所有外部 API 调用都要有"全失败兜底"

### P0-25.1 ARCH-DECOMPOSITION 双编号（RI-22~45 vs RI-46~69）
**现象**: 需求底稿 (RI-22~45) 与实现跟踪 (RI-46~69) 编号并存易混淆
**教训**: MOD-06 收口时 append RI-46~69 带 MOD 映射，不动 RI-1~45
**预防**: TASKS.md v1.1 章节统一引用 RI-46~69

### P0-26.1 集成测试禁真实 GLM（V11IntegrationTest）
**现象**: CI 无 GLM_API_KEY 时 Agent 集成测试挂
**教训**: V11IntegrationTest 必须 @MockBean GlmService + H2，不测真实 API
**预防**: application-test.yml 文档化 + review checklist

### P0-27.1 OpenPDF jar 增量（RI-64）
**现象**: 初估 iText 增量 >15 MB，超出 D-4 预算
**教训**: 改用 OpenPDF 2.0.2，jar 增量 <3 MB，功能满足单项目 PDF 报告
**预防**: 新增 PDF 库先量 jar 再拍板

### P0-28.1 前端预览纯浏览器（D-5）
**现象**: 曾考虑 LibreOffice headless 转 Word，Windows 单机部署过重
**教训**: pdfjs + mammoth 纯前端预览，后端只返文件流
**预防**: 单机部署原则——能前端做的不引第 4 进程

