#!/bin/sh
# mvnw: 项目自带 Maven 包装脚本
# 用 backend/tools/jdk17 + backend/tools/apache-maven-3.9.9
# 自动检测环境，绕开系统 mvn
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOLS_DIR="$SCRIPT_DIR"

# 优先用项目自带的 JDK + Maven
if [ -d "$TOOLS_DIR/jdk17/usr/lib/jvm/java-17-openjdk-amd64" ]; then
  export JAVA_HOME="$TOOLS_DIR/jdk17/usr/lib/jvm/java-17-openjdk-amd64"
elif [ -n "$JAVA_HOME" ]; then
  : # 用环境变量
else
  echo "ERROR: 找不到 JDK 17。请设置 JAVA_HOME 或把 JDK 解压到 backend/tools/jdk17/" >&2
  exit 1
fi

if [ -d "$TOOLS_DIR/apache-maven-3.9.9" ]; then
  MAVEN_CMD="$TOOLS_DIR/apache-maven-3.9.9/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
  MAVEN_CMD="mvn"
else
  echo "ERROR: 找不到 Maven。请安装或解压到 backend/tools/apache-maven-3.9.9/" >&2
  exit 1
fi

exec "$JAVA_HOME/bin/java" $MAVEN_OPTS -classpath "$TOOLS_DIR/apache-maven-3.9.9/boot/plexus-classworlds-2.8.0.jar" \
  -Dclassworlds.conf="$TOOLS_DIR/apache-maven-3.9.9/bin/m2.conf" \
  -Dmaven.home="$TOOLS_DIR/apache-maven-3.9.9" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
