#!/usr/bin/env bash
# CycloneDX Maven Plugin 2.9.3：生成 JSON + XML。禁止只检查退出码。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}"
# shellcheck disable=SC1091
source "${ROOT}/scripts/ci/pins.env"

"${ROOT}/backend/mvnw" -f "${ROOT}/backend/pom.xml" \
  "org.cyclonedx:cyclonedx-maven-plugin:${CYCLONEDX_MAVEN_PLUGIN_VERSION}:makeAggregateBom" \
  -DoutputFormat=all \
  -DoutputName=aggregate-bom

python3 "${ROOT}/scripts/ci/validate-sbom.py" \
  "${ROOT}/backend/target/aggregate-bom.json" \
  "${ROOT}/backend/target/aggregate-bom.xml"
