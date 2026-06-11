# backend/tools — 沙箱构建工具

> **目的**: 提供 `mvn-tools.sh` 包装脚本 + 使用说明, 让接手 agent 在自己沙箱
> 用 4 步快速装好 JDK 17 + Maven 3.9.9, 直接跑 `mvn compile`。
>
> **本目录不预先放 JDK/Maven 大二进制**——PM 沙箱装过导致 plexus classworlds
> 启动崩循环, 副作用大于价值。接手 agent 各自装各自沙箱即可。

---

## 接手 agent 上手 4 步

### Step 1: 拉仓库

```bash
cd /workspace
git clone -b main git@gitee.com:frisker/projects-online.git projects-online-verify
cd projects-online-verify
git rev-parse HEAD
# 期望: 802788f (v1.1 完工交付)
```

### Step 2: 装 JDK 17（阿里云镜像，国内可达）

```bash
mkdir -p backend/tools/jdk17 && cd backend/tools

curl -fSL -o jdk17-headless.deb \
  "https://mirrors.aliyun.com/debian/pool/main/o/openjdk-17/openjdk-17-jdk-headless_17.0.19%2B10-1~deb12u2_amd64.deb"
curl -fSL -o jdk17.deb \
  "https://mirrors.aliyun.com/debian/pool/main/o/openjdk-17/openjdk-17-jdk_17.0.19%2B10-1~deb12u2_amd64.deb"

# 用 dpkg-deb 解包（不需 sudo, 不污染系统）
dpkg-deb -x jdk17-headless.deb jdk17/
dpkg-deb -x jdk17.deb jdk17/
rm jdk17-headless.deb jdk17.deb

# 验证
./jdk17/usr/lib/jvm/java-17-openjdk-amd64/bin/java -version
# 期望: openjdk version "17.0.19"
```

> **沙箱跑不动 mvn 怎么办?** 如果你的沙箱没装 `ca-certificates-java`, JDK 启动会卡
> `SecurityProperties.includedInExceptions` 阶段. **先装**:
> ```bash
> apt-get install -y ca-certificates-java   # 标准 Debian
> # 或 沙箱无 apt: 找接手 agent 沙箱或开发机跑 mvn
> ```

### Step 3: 装 Maven 3.9.9（华为云镜像）

```bash
curl -fSL -o maven.tar.gz \
  "https://repo.huaweicloud.com/apache/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
tar -xzf maven.tar.gz && rm maven.tar.gz

# 验证
apache-maven-3.9.9/bin/mvn -version
# 期望: Apache Maven 3.9.9 + Java version: 17.0.19
```

### Step 4: 跑 mvn 验证

```bash
cd ../..  # 回到仓库根
./backend/tools/mvn-tools.sh compile -DskipTests -B
# 期望: BUILD SUCCESS

./backend/tools/mvn-tools.sh test -Dtest=V11IntegrationTest -B
# 期望: Tests run: 45, Failures: 0, Errors: 0
```

---

## mvn-tools.sh 工作原理

```sh
#!/bin/sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 优先用项目自带的 JDK + Maven
if [ -d "$SCRIPT_DIR/jdk17/usr/lib/jvm/java-17-openjdk-amd64" ]; then
  export JAVA_HOME="$SCRIPT_DIR/jdk17/usr/lib/jvm/java-17-openjdk-amd64"
elif [ -n "$JAVA_HOME" ]; then
  : # 用环境变量
else
  echo "ERROR: 找不到 JDK 17。请先按 README §Step 2 装。" >&2
  exit 1
fi

if [ -d "$SCRIPT_DIR/apache-maven-3.9.9" ]; then
  MAVEN_HOME="$SCRIPT_DIR/apache-maven-3.9.9"
else
  echo "ERROR: 找不到 Maven。请先按 README §Step 3 装。" >&2
  exit 1
fi

exec "$JAVA_HOME/bin/java" \
  -classpath "$MAVEN_HOME/boot/plexus-classworlds-2.8.0.jar" \
  -Dclassworlds.conf="$MAVEN_HOME/bin/m2.conf" \
  -Dmaven.home="$MAVEN_HOME" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
```

---

## 沙箱环境 vs 开发机对比

| 场景 | 命令 | 期望 |
|---|---|---|
| **在线开发机** | `cd backend && mvn compile -B` | ✅ BUILD SUCCESS |
| **沙箱（已装 JDK/Maven, 有 cacerts）** | `cd backend && ./tools/mvn-tools.sh compile -B` | ✅ BUILD SUCCESS |
| **沙箱（无 cacerts）** | 装 `ca-certificates-java` 或换接手 agent 沙箱 | ✅ |
| **CI/CD（标准 Docker）** | `mvn compile -B` (Dockerfile 装 JDK 17) | ✅ |

---

## 文件清单

```
backend/tools/
├── README.md (本文件)              # 4 步上手 + 故障排查
└── mvn-tools.sh                     # 包装脚本（找 JAVA_HOME / 找 Maven）
```

> **大二进制 (jdk17/ + apache-maven-3.9.9/) 由接手 agent 在自己沙箱装**,
> 走 §Step 2 / §Step 3 流程。`.gitignore` 精细规则:
> ```
> backend/tools/jdk17/                 # gitignored
> backend/tools/apache-maven-3.9.9/    # gitignored
> !backend/tools/mvn-tools.sh         # 进仓库
> !backend/tools/README.md            # 进仓库
> ```

---

## 故障排查

| 报错 | 原因 | 解决 |
|---|---|---|
| `ERROR: 找不到 JDK 17` | 没装 jdk17/ | 按 §Step 2 装 |
| `ERROR: 找不到 Maven` | 没装 apache-maven-3.9.9/ | 按 §Step 3 装 |
| `Error: A JNI error has occurred, please check your installation` + `Error loading java.security file` | 沙箱无 `ca-certificates-java` | 装 `ca-certificates-java` 包或换沙箱 |
| `Maven 3.9.9 not found at backend/tools/apache-maven-3.9.9` | tar 解压路径错 | 看 README 文件清单核对 |
| 接手 agent 沙箱网络不通 gitee.com | 防火墙 / 沙箱白名单 | 让项目方给沙箱白名单 |

---

*编写人: 投委会档案项目 PM | 日期: 2026-06-11 | 工单: v1.1 后端 mvn 验证*
