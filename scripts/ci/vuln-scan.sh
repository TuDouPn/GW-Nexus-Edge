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
set +e
"${BIN}" fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 \
  --format sarif --output "${SARIF}" "${ROOT}"
code=$?
set -e

if [ ! -s "${SARIF}" ]; then
  echo "::error::TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：Trivy 未产生 SARIF（漏洞库不可用或扫描未执行）。不得视为无漏洞。"
  exit 2
fi

python3 - "${SARIF}" "${code}" <<'PY'
import json, sys
path, code = sys.argv[1], int(sys.argv[2])
try:
    data = json.load(open(path, encoding="utf-8"))
except Exception as e:
    print(f"::error::TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：SARIF 不可解析：{e}")
    sys.exit(2)
runs = data.get("runs")
if not isinstance(runs, list):
    print("::error::TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：SARIF 缺少 runs，不得视为无漏洞")
    sys.exit(2)
results = []
for run in runs:
    results.extend(run.get("results") or [])
print(f"Trivy filesystem scan SARIF results={len(results)} exit={code}")
if code == 1:
    print("::error::HIGH/CRITICAL 漏洞阻断（filesystem/dependency scan，非 Container Image Scan）")
    sys.exit(1)
if code != 0:
    print(f"::error::TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：Trivy 退出码 {code}")
    sys.exit(2)
print("Trivy filesystem/dependency scan：无 HIGH/CRITICAL 阻断项")
PY
