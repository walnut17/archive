# v1.1 工单 — 后端 mvn 验证（PM 派单）

> 派单人：投委会档案项目 PM | 日期：2026-06-11
> **目的**: 接手 agent 沙箱（有完整 cacerts + JDK 17）跑 `mvn compile + mvn test V11IntegrationTest`，
> 出实测报告，**PM 沙箱跑不动**（无 cacerts + 无 JDK），必须委托。

---

## §0 任务背景

### 0.1 为什么 PM 跑不动
- PM 沙箱（`alinas-ap-0p4fnw0spb.6139061222-ogc85.cn-shanghai.tls.127.0.1.1`）镜像 strip 掉了 Java 工具链
- apt 仓库找不到 `openjdk-17-jdk-headless`（源被 strip）
- 没有 `/etc/ssl/certs/java/cacerts`（ca-certificates-java 包未安装）
- 装在 `backend/tools/jdk17/` 的 JDK 17.0.19 因 cacerts 缺失导致 `SecurityProperties.includedInExceptions` 在 `<clinit>` 阶段崩
- 任何 jar 工具（mvn / keytool）都跑不起来

### 0.2 PM 已经做的
- ✅ 装好 OpenJDK 17.0.19 到 `backend/tools/jdk17/`（262MB）
- ✅ 装好 Maven 3.9.9 到 `backend/tools/apache-maven-3.9.9/`（11MB）
- ✅ 写好 `backend/tools/mvn-tools.sh` 包装脚本（1KB，**进仓库**）
- ✅ 写好 `backend/tools/README.md` 沙箱使用说明（**进仓库**）
- ✅ 精细化 `.gitignore`：大二进制忽略，小文档进仓库
- ✅ 前端 `npm run build` 0 错（17.97s，独立验证）
- ✅ 静态交叉引用检查 113 个 Spring Bean + 5 类 AuditLog + QaResponse 字段 + 前端 5 库
- ❌ `mvn compile` 跑不动（环境限制）

### 0.3 必须接手 agent 验证
接手 agent 在自己沙箱（有完整 cacerts）跑：
1. `mvn compile -DskipTests -B` → 期望 BUILD SUCCESS
2. `mvn test -Dtest=V11IntegrationTest -B` → 期望 45 测例全过
3. 出实测报告

---

## §1 接手 agent 上手步骤

### 1.1 拉仓库（baseline `0257e13`）

```bash
cd /workspace
git clone -b main git@gitee.com:frisker/projects-online.git projects-online-verify
cd projects-online-verify

# 验证基线
git rev-parse HEAD
# 期望: 0257e13 (PM 已 push 沙箱工具集)
```

### 1.2 装 JDK 17 + Maven 3.9.9（如果沙箱没有）

```bash
cd backend/tools

# JDK 17 (阿里云镜像，沙箱网络可达)
curl -fSL -o jdk17-headless.deb \
  "https://mirrors.aliyun.com/debian/pool/main/o/openjdk-17/openjdk-17-jdk-headless_17.0.19%2B10-1~deb12u2_amd64.deb"
curl -fSL -o jdk17.deb \
  "https://mirrors.aliyun.com/debian/pool/main/o/openjdk-17/openjdk-17-jdk_17.0.19%2B10-1~deb12u2_amd64.deb"
mkdir -p jdk17 && dpkg-deb -x jdk17-headless.deb jdk17/ && dpkg-deb -x jdk17.deb jdk17/
rm jdk17-headless.deb jdk17.deb

# Maven 3.9.9 (华为云镜像)
curl -fSL -o maven.tar.gz \
  "https://repo.huaweicloud.com/apache/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
tar -xzf maven.tar.gz && rm maven.tar.gz

# 验证
./jdk17/usr/lib/jvm/java-17-openjdk-amd64/bin/java -version
# 期望: openjdk version "17.0.19"
```

### 1.3 跑 mvn-tools.sh

```bash
cd ../..  # 回到仓库根
./backend/tools/mvn-tools.sh -version
# 期望: Apache Maven 3.9.9 + Java version: 17.0.19
```

### 1.4 跑 mvn compile

```bash
cd backend
../backend/tools/mvn-tools.sh compile -DskipTests -B 2>&1 | tee /tmp/mvn-compile.log
# 期望: BUILD SUCCESS, 0 ERROR, 0 FAILURE
```

### 1.5 跑 V11IntegrationTest

```bash
cd backend
../backend/tools/mvn-tools.sh test -Dtest=V11IntegrationTest -B 2>&1 | tee /tmp/mvn-test.log
# 期望: Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
```

### 1.6 出实测报告

写 `VERIFICATION-REPORT.md` 到仓库根，包含：

```markdown
# v1.1 后端 mvn 验证报告（接手 agent 沙箱）

> 执行：<接手 agent 名字>
> 日期：2026-06-XX
> 沙箱：<沙箱信息>
> 基线：0257e13 (PM 已 push 沙箱工具集)

## 1. 环境

- 操作系统：<uname -a>
- JDK 17.0.19：<java -version>
- Maven 3.9.9：<mvn -version>
- cacerts 状态：<file /etc/ssl/certs/java/cacerts> (OK/缺失)

## 2. mvn compile 结果

- 命令：`./backend/tools/mvn-tools.sh compile -DskipTests -B`
- 输出：<贴关键行>
- BUILD SUCCESS / FAILURE
- 编译产物路径：`backend/target/classes/`
- 编译耗时：<X 秒>

## 3. mvn test (V11IntegrationTest) 结果

- 命令：`./backend/tools/mvn-tools.sh test -Dtest=V11IntegrationTest -B`
- 测例数：45
- 通过：X / 失败：Y
- 失败测例（如果有）：
  - <测例名>: <失败原因>
- 测试耗时：<X 秒>

## 4. 已知问题（如果有）

- <列任何 compile/test 报错>

## 5. 结论

- ✅ / ❌ v1.1 后端代码可生产
```

---

## §2 占任务 SOP

1. **看** 本文件（5 分钟）
2. **改** `TASKS.md` v1.1 阶段加一段:
   ```
   #### T-v1.1-42: 后端 mvn 验证（PM 委托）
   - 状态: 未开发
   - 占用者: 空
   - 详细 spec: .mavis/plans/draft/task-mvn-verification.md
   - 工作量: 0.5d
   - 依赖: 无
   - 验收: VERIFICATION-REPORT.md 包含 mvn compile SUCCESS + 45 测例全过
   - commit: docs(verify): mvn 验证报告 VERIFICATION-REPORT.md
   ```
3. **10 秒内** `git add TASKS.md && git commit && git push origin main`
4. **干完** 按 §1.6 出报告 + commit + push
5. **改状态** → `已完成`

---

## §3 严禁清单

- ❌ 改 PM 已 push 的代码（除非有 P0 编译错误需要修）
- ❌ 直推 `minimax` 分支
- ❌ 改 MOD-XX 独占清单之外的文件

---

## §4 预期产出

接手 agent 完成后，PM 拿到：
1. `VERIFICATION-REPORT.md` 在 main 分支
2. TASKS.md v1.1 章节新增 T-v1.1-42 状态 `已完成`
3. 接手 agent 的 commit hash

PM 接下来：
1. 读 `VERIFICATION-REPORT.md`
2. 如果全过 → 通知业务方部署到 182.168.1.125
3. 如果有 P0 错 → 派单修

---

*PM 委托: 2026-06-11 | 沙箱环境受限必须委托 | 接手 agent 见任务详情*
