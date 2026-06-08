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

### 3.2 PowerShell 5.x `curl` 是 `Invoke-WebRequest` 的别名 (M0)

**Commit**: `22ab893`

**症状**:
```
[1/2] GET /api/health
FAILED - backend may not be running
```

(其实后端正常,`/api/health` 返回了 UP,但 healthcheck 脚本解析错以为失败)

**根因**:
- `curl` 在 PowerShell 5.x 是 `Invoke-WebRequest` 的别名,返回**对象**(`StatusCode / Content / Headers` 等)
- 我用 `& curl` 调,返回对象不是字符串 → `$healthResp -match '...'` 失败 → catch 兜底报失败

**修复**: 用 `& curl.exe` 强制调**真实**的 curl(系统带的),返回字符串

**教训**:
- **PowerShell 里 `curl` 不等于 bash 的 `curl`**——PowerShell 5.x 是别名,7.x 才兼容
- 凡是要返回字符串的 HTTP 调用,用 `curl.exe` 或 `Invoke-RestMethod` 替代
- 写 healthcheck 类脚本,先在 PowerShell ISE 跑一次验证

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

---

## 8. 协议变更日志(每次新坑加入,记 commit)

| 坑号 | 描述 | 关联 commit |
|---|---|---|
| 1.1 | application.yml 自引用 | 3c67e67 |
| 1.2 | characterEncoding=utf8mb4 | af05494, 11f7ac1 |
| 1.3 | admin 密码 hash 错 | 5bb2439 |
| 2.1 | MaterialResponse 缺 import | 137e4cb |
| 2.2 | Tika parseToString(byte[]) API 错 | 165d430 |
| 3.1 | PowerShell 5.x 中文 UTF-8 脚本 | ceb2502 |
| 3.2 | PowerShell curl 别名 | 22ab893 |
| 3.3 | PowerShell 5.x RemoteException | a8fc056, 3ae81e4, cd8b59c |
| 3.4 | PowerShell -D 参数解析 | (M0-3 解决) |
| 4.1 | UTF-8 BOM | 1af3dc0, 2140440 |
| 4.2 | ConfigJsonLoader 路径 | cd8b59c |
| 5.1 | 沙箱 SSH key 丢失 | 7cdd290 |
| 5.2 | 沙箱没 JDK | (待修复) |
