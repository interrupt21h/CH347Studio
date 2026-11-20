#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -d "$out" app/src/main/java/io/interrupt21h/ch347studio/protocol/*.java tests/ProtocolSelfTest.java
java -cp "$out" ProtocolSelfTest
