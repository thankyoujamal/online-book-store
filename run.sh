#!/usr/bin/env bash
# Builds and runs the store without Maven. Needs JDK 17+ and curl.
set -euo pipefail
cd "$(dirname "$0")"

CONNECTOR_VERSION=9.1.0
JAR="lib/mysql-connector-j-${CONNECTOR_VERSION}.jar"

if [ ! -f "$JAR" ]; then
  echo "Downloading MySQL Connector/J ${CONNECTOR_VERSION}..."
  mkdir -p lib
  curl -fsSL -o "$JAR" \
    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${CONNECTOR_VERSION}/mysql-connector-j-${CONNECTOR_VERSION}.jar"
fi

rm -rf out
javac -d out --release 17 $(find src/main/java -name '*.java')
exec java -cp "out:${JAR}" com.obs.App
