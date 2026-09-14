#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXAMPLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE="${NATIVE_IMAGE_IMAGE:-ghcr.io/graalvm/native-image-community:21}"

HTTP_PROXY="${HTTP_PROXY:-http://127.0.0.1:7890}"
HTTPS_PROXY="${HTTPS_PROXY:-http://127.0.0.1:7890}"
ALL_PROXY="${ALL_PROXY:-socks5://127.0.0.1:7890}"

mvn -q -pl lynxus-processor -am -DskipTests install
mvn -q -f "$EXAMPLE_DIR/pom.xml" clean package dependency:copy-dependencies \
  -DskipTests -DoutputDirectory=target/native-libs

docker run --rm \
  --platform linux/arm64 \
  -e HTTP_PROXY="$HTTP_PROXY" \
  -e HTTPS_PROXY="$HTTPS_PROXY" \
  -e ALL_PROXY="$ALL_PROXY" \
  -v "$EXAMPLE_DIR:/workspace" \
  "$IMAGE" \
  --no-fallback \
  -cp '/workspace/target/classes:/workspace/target/native-libs/*' \
  io.github.lynxus.example.nativeimage.NativeImageSmoke \
  -H:Path=/workspace/target/lynxus-native-smoke

docker run --rm \
  --platform linux/arm64 \
  --entrypoint /workspace/target/lynxus-native-smoke/io.github.lynxus.example.nativeimage.nativeimagesmoke \
  -v "$EXAMPLE_DIR/target:/workspace/target" \
  "$IMAGE"
