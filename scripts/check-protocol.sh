#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/io/interrupt21h/ch347studio/protocol/*.java app/src/main/java/io/interrupt21h/ch347studio/devices/*.java tests/ProtocolSelfTest.java tests/DeviceScriptsSelfTest.java
java -cp "$out" ProtocolSelfTest
java -cp "$out" DeviceScriptsSelfTest
