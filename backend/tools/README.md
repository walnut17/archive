# backend/tools — 沙箱 JDK + Maven 工具集

> **目的**: 解决"沙箱镜像无 JDK/Maven"问题。把 JDK 17 + Maven 3.9.9 解包到本目录,
> 配合 `mvn-tools.sh` 包装脚本, 让接手 agent 在任何沙箱都能直接跑 mvn 命令。

---

## 工具版本

| 工具 | 版本 | 来源 | 大小 |
|---|---|---|---|
| **JDK** | OpenJDK 17.0.19 | `openjdk-17-jdk-headless` + `openjdk-17-jdk` (阿里云镜像) | 262MB |
| **Maven** | 3.9.9 | `apache-maven-3.9.9-bin.tar.gz` (华为云镜像) | 11MB |
| **mvn-tools.sh** | 自带包装脚本 | 本目录手写 | 1KB |

---

## 沙箱环境限制 ⚠️

**本沙箱镜像** (Debian 12 / x86_64, 阿里云 csi 挂载, 2 CPU / 3.7GB RAM) 跑 mvn **会失败**:

```
Error: A JNI error has occurred, please check your installation and try again
Exception in thread "main" java.lang.InternalError: Error loading java.security file
    at java.base/java.security.Security.initialize(Security.java:106)
    at java.base/java.security.SecurityProperties.includedInExceptions(SecurityProperties.java:72)
```

**根因**: 沙箱 `/etc/ssl/certs/java/cacerts` 缺失, `ca-certificates-java` 包未安装,
`/etc/java-17-openjdk/security/` 系统目录不存在. JDK 17 的 `SecurityProperties.includedInExceptions`
类在 `<clinit>` 阶段读 cacerts 失败 → 任何 jar 工具都崩.

**影响**: `java -version` 能跑, `keytool` 不能跑, `mvn` 不能跑, 任何 java 工具都不行.

**解决**:
- ✅ 在**有完整 cacerts 的环境** (开发机 / 生产服务器), `mvn` 自动成功
- ✅ 本沙箱用静态扫描 + `npm run build` 替代 mvn 验证 (前端能跑)
- ✅ 接手 agent 在自己沙箱拉代码, 如沙箱同样无 cacerts, 装 `ca-certificates-java` 包

---

## 用法

### 在本沙箱（环境受限）

```bash
# 直接调 JDK
./jdk17/usr/lib/jvm/java-17-openjdk-amd64/bin/java -version
# ✅ 17.0.19 输出

# mvn-tools.sh (本沙箱会失败)
./mvn-tools.sh -version
# ❌ cacerts missing 错误
```

### 在接手 agent 的沙箱（有 cacerts）

```bash
# 把 backend/tools/mvn-tools.sh + README.md 已经在仓库里（.gitignore 精细规则）
# 大二进制 (jdk17/ + apache-maven-3.9.9/) 在 .gitignore 中，但接手 agent 可独立装
# 或拷贝 sandbox 工具目录

# 准备大二进制（接手 agent 第一次）
cd backend/tools
curl -fSL -o jdk17-headless.deb "https://mirrors.aliyun.com/debian/pool/main/o/openjdk-17/openjdk-17-jdk-headless_17.0.19%2B10-1~deb12u2_amd64.deb"
curl -fSL -o jdk17.deb "https://mirrors.aliyun.com/debian/pool/main/o/openjdk-17/openjdk-17-jdk_17.0.19%2B10-1~deb12u2_amd64.deb"
mkdir -p jdk17 && dpkg-deb -x jdk17-headless.deb jdk17/ && dpkg-deb -x jdk17.deb jdk17/ && rm *.deb

curl -fSL -o maven.tar.gz "https://repo.huaweicloud.com/apache/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
tar -xzf maven.tar.gz && rm maven.tar.gz

# 跑
cd ../.. && ./backend/tools/mvn-tools.sh -version
# ✅ 期望 Apache Maven 3.9.9 + Java 17.0.19

./backend/tools/mvn-tools.sh compile -DskipTests -B
# ✅ 期望 BUILD SUCCESS
```

### 在开发机 / 生产服务器（有 JDK/Maven）

直接用系统的 `mvn`, 不需要本目录的工具.

---

## 离线 / 在线使用对比

| 场景 | 命令 | 期望 |
|---|---|---|
| **在线开发机** | `cd backend && mvn compile -B` | ✅ BUILD SUCCESS |
| **沙箱（有 cacerts）** | `cd backend && ./tools/mvn-tools.sh compile -B` | ✅ BUILD SUCCESS |
| **沙箱（无 cacerts, 本机）** | `cd backend && ./tools/mvn-tools.sh compile -B` | ❌ 装 ca-certificates-java 或用静态扫描 |
| **CI/CD（标准 Docker）** | `mvn compile -B` (Dockerfile 装 JDK 17) | ✅ |

---

## 文件清单

```
backend/tools/
├── README.md (本文件)              # 进仓库
├── mvn-tools.sh                     # 进仓库 (1KB 包装脚本)
├── jdk17/                           # 沙箱本地 (gitignore, 262MB)
│   └── usr/lib/jvm/java-17-openjdk-amd64/
│       ├── bin/  (java, javac, jshell, jar, 等 28 个)
│       ├── lib/  (rt.jar, jrt-fs.jar, 等)
│       └── conf/ (security/, 等)
└── apache-maven-3.9.9/              # 沙箱本地 (gitignore, 11MB)
    ├── bin/mvn
    ├── boot/plexus-classworlds-2.8.0.jar
    ├── conf/
    └── lib/  (maven-core, maven-embedder, 等)
```

---

## mvn-tools.sh 实现原理

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
  echo "ERROR: 找不到 JDK 17。" >&2
  exit 1
fi

if [ -d "$SCRIPT_DIR/apache-maven-3.9.9" ]; then
  MAVEN_HOME="$SCRIPT_DIR/apache-maven-3.9.9"
else
  echo "ERROR: 找不到 Maven。" >&2
  exit 1
fi

exec "$JAVA_HOME/bin/java" \
  -classpath "$MAVEN_HOME/boot/plexus-classworlds-2.8.0.jar" \
  -Dclassworlds.conf="$MAVEN_HOME/bin/m2.conf" \
  -Dmaven.home="$MAVEN_HOME" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
```

---

## 维护

- **新增 JDK 版本**: 重新跑 dpkg-deb -x 流程, 改 `mvn-tools.sh` 的 `JAVA_HOME` 路径
- **新增 Maven 版本**: 解压新版 Maven, 改 `mvn-tools.sh` 的 `MAVEN_HOME` 路径
- **遇到问题**: 在本 README 末追加 "故障排查" 段

---

*编写人: 投委会档案项目 PM | 日期: 2026-06-11 | 工单: v1.1 离线构建*
