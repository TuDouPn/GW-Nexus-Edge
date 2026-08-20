#!/usr/bin/env bash
# Trivy filesystem/dependency/SBOM 向的漏洞扫描。不是 Container Image Scan。
# HIGH/CRITICAL 阻断。数据库无法获取 = TOOL_BLOCKED/INFRASTRUCTURE_FAILURE，不得显示为无漏洞。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}"
OUT="${1:-/tmp/gw-nexus-scan}"
BIN="${2:-}"
mkdir -p "${OUT}"

if [ -z "${BIN}" ]; then
  "${ROOT}/scripts/ci/install-binary.sh" trivy /tmp/gw-nexus-ci-tools >/dev/null
  BIN="/tmp/gw-nexus-ci-tools/trivy"
fi

SARIF="${OUT}/trivy-fs.sarif"
INPUT="${3:-}"
set +e
if [ -n "${INPUT}" ]; then
  # 对已解析 CycloneDX 扫描：使用 Maven 解析后的真实版本，避免 Trivy 再打 Maven Central。
  "${BIN}" sbom --scanners vuln --severity HIGH,CRITICAL --exit-code 1 \
    --format sarif --output "${SARIF}" "${INPUT}"
else
  "${BIN}" fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 \
    --format sarif --output "${SARIF}" "${ROOT}"
fi
code=$?
set -e

if [ ! -s "${SARIF}" ]; then
  echo "::error::TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：Trivy 未产生 SARIF（漏洞库不可用或扫描未执行）。不得视为无漏洞。"
  exit 2
fi

python3 "${ROOT}/scripts/ci/evaluate-trivy-sarif.py" \
  "${SARIF}" \
  "${ROOT}/docs/dev-0005/scan-exceptions.json" \
  "${code}"
