#!/usr/bin/env bash
# MCJEBooster deterministic build script (no Maven required).
#
# Usage:
#   ./scripts/build.sh [--skip-tests]
#
# Steps:
#   1. Ensure dependency jars in lib/ (downloaded from Maven Central if missing)
#   2. Clean compile all sources with --release 17
#   3. Run JUnit 5 tests if the standalone console launcher is available
#   4. Package a self-contained fat jar with the agent manifest
#   5. Emit checksums.txt (SHA-256) beside the artifact
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

SKIP_TESTS=0
if [[ "${1:-}" == "--skip-tests" ]]; then SKIP_TESTS=1; fi

VERSION="$(grep -o 'VERSION = "[^"]*"' src/main/java/com/mcjebooster/util/BoosterVersion.java | sed 's/VERSION = "\(.*\)"/\1/')"
if [[ -z "$VERSION" ]]; then
  echo "ERROR: could not read version from BoosterVersion.java" >&2
  exit 1
fi
JAR_NAME="MCJEBooster-${VERSION}.jar"
echo "==> Building MCJEBooster ${VERSION}"

ASM=9.6
JSON=20231013
JUNIT_CONSOLE=1.10.0

mkdir -p lib target/classes

fetch() { # fetch <url> <dest>
  local url="$1" dest="$2"
  if [[ ! -f "$dest" ]]; then
    echo "  downloading $(basename "$dest")"
    curl -fsSL --retry 3 -o "$dest.part" "$url"
    mv "$dest.part" "$dest"
  fi
}

echo "==> [1/5] Dependencies"
fetch "https://repo1.maven.org/maven2/org/ow2/asm/asm/${ASM}/asm-${ASM}.jar"                 "lib/asm-${ASM}.jar"
fetch "https://repo1.maven.org/maven2/org/ow2/asm/asm-tree/${ASM}/asm-tree-${ASM}.jar"        "lib/asm-tree-${ASM}.jar"
fetch "https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/${ASM}/asm-commons-${ASM}.jar"  "lib/asm-commons-${ASM}.jar"
fetch "https://repo1.maven.org/maven2/org/json/json/${JSON}/json-${JSON}.jar"                 "lib/json-${JSON}.jar"

CP="lib/asm-${ASM}.jar;lib/asm-tree-${ASM}.jar;lib/asm-commons-${ASM}.jar;lib/json-${JSON}.jar"
if [[ "$OSTYPE" != "msys" && "$OSTYPE" != "cygwin" ]]; then
  CP="$(echo "$CP" | tr ';' ':')"
fi

echo "==> [2/5] Compile (--release 17)"
rm -rf target/classes
mkdir -p target/classes
find src/main/java -name '*.java' > target/sources.txt
javac -d target/classes -cp "$CP" -encoding UTF-8 --release 17 @target/sources.txt
SRC_COUNT=$(wc -l < target/sources.txt)
CLASS_COUNT=$(find target/classes -name '*.class' | wc -l)
echo "  compiled ${SRC_COUNT} sources -> ${CLASS_COUNT} classes"

TEST_COUNT=0
if [[ "$SKIP_TESTS" -eq 0 ]]; then
  echo "==> [3/5] Tests"
  JUNIT_JAR="lib/junit-platform-console-standalone-${JUNIT_CONSOLE}.jar"
  if fetch "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_CONSOLE}/junit-platform-console-standalone-${JUNIT_CONSOLE}.jar" "$JUNIT_JAR" 2>/dev/null && [[ -f "$JUNIT_JAR" ]] && [[ -d src/test/java ]]; then
    rm -rf target/test-classes
    mkdir -p target/test-classes
    find src/test/java -name '*.java' > target/test-sources.txt
    javac -d target/test-classes -cp "$CP;target/classes;$JUNIT_JAR" -encoding UTF-8 --release 17 @target/test-sources.txt
    java -jar "$JUNIT_JAR" execute \
      --class-path "target/classes;target/test-classes;$CP" \
      --scan-class-path=target/test-classes \
      --fail-if-no-tests --disable-banner --details=summary > target/test-report.txt 2>&1 || {
        cat target/test-report.txt; echo "ERROR: tests failed" >&2; exit 1; }
    grep -E "tests (successful|failed)" target/test-report.txt || true
    TEST_COUNT=$(sed -n 's/^[[:space:]\[]*\([0-9][0-9]*\) tests successful.*/\1/p' target/test-report.txt | head -1)
    TEST_COUNT=${TEST_COUNT:-0}
  else
    echo "  WARN: JUnit console launcher unavailable; tests skipped"
  fi
else
  echo "==> [3/5] Tests skipped"
fi

echo "==> [4/5] Package ${JAR_NAME}"
rm -rf target/stage
mkdir -p target/stage
cp -r target/classes/. target/stage/
for j in "lib/asm-${ASM}.jar" "lib/asm-tree-${ASM}.jar" "lib/asm-commons-${ASM}.jar" "lib/json-${JSON}.jar"; do
  (cd target/stage && jar xf "$ROOT/$j")
done
rm -rf target/stage/META-INF/MANIFEST.MF target/stage/META-INF/*.SF target/stage/META-INF/*.DSA target/stage/META-INF/*.RSA target/stage/module-info.class 2>/dev/null || true

cat > target/MANIFEST.MF <<EOF
Manifest-Version: 1.0
Agent-Class: com.mcjebooster.agent.MCJEBoosterAgent
Premain-Class: com.mcjebooster.agent.MCJEBoosterAgent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Can-Set-Native-Method-Prefix: true
Main-Class: com.mcjebooster.injector.InjectorMain
Automatic-Module-Name: mcjebooster.agent
Implementation-Title: MCJEBooster
Implementation-Version: ${VERSION}
Implementation-Vendor: StarsailsClover
EOF

jar cfm "target/${JAR_NAME}" target/MANIFEST.MF -C target/stage .
ls -la "target/${JAR_NAME}"

echo "==> [5/5] Checksums"
(cd target && sha256sum "${JAR_NAME}" > checksums.txt)
cat target/checksums.txt

echo ""
echo "==> Build complete: target/${JAR_NAME}"
echo "    version=${VERSION} sources=${SRC_COUNT} classes=${CLASS_COUNT} tests=${TEST_COUNT}"
