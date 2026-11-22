#!/usr/bin/env bash
# Builds SpreadsheetPro.jar (needs a JDK 11 or newer; the jar runs on Java 11+). Run it with: java -jar SpreadsheetPro.jar
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir out
javac --release 11 -encoding UTF-8 -d out *.java
VERSION="${1:-$(cat VERSION 2>/dev/null || echo dev)}"
printf 'Main-Class: SpreadsheetApp\nImplementation-Version: %s\n' "$VERSION" > manifest.txt
jar cfm SpreadsheetPro.jar manifest.txt -C out . -C resources .
echo "Built SpreadsheetPro.jar (version $VERSION)"
