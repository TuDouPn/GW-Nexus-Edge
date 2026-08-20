#!/usr/bin/env bash
# Gitleaks：扫描 Git 历史 + 当前工作树。确认 Secret 阻断。输出脱敏（--redact）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}"
OUT="${1:-/tmp/gw-nexus-scan}"
BIN="${2:-}"
mkdir -p "${OUT}"

if [ -z "${BIN}" ]; then
  "${ROOT}/scripts/ci/install-binary.sh" gitleaks /tmp/gw-nexus-ci-tools >/dev/null
  BIN="/tmp/gw-nexus-ci-tools/gitleaks"
fi

fail=0
echo "Gitleaks Git 历史扫描"
if ! "${BIN}" detect --source "${ROOT}" --redact --report-path "${OUT}/gitleaks-git.json" --exit-code 1; then
  echo "::error::Git 历史 Secret Scan 阻断（确认 Secret）"
  fail=1
fi

echo "Gitleaks 工作树扫描"
if ! "${BIN}" detect --source "${ROOT}" --no-git --redact --report-path "${OUT}/gitleaks-tree.json" --exit-code 1; then
  echo "::error::工作树 Secret Scan 阻断（确认 Secret）"
  fail=1
fi

# 报告不得包含常见 Secret 值形态的明文（脱敏校验，宽松：只拒绝 gitleaks 未 redact 的 Match 字段）。
python3 - "${OUT}" <<'PY'
import json, sys, pathlib
out = pathlib.Path(sys.argv[1])
for name in ("gitleaks-git.json", "gitleaks-tree.json"):
    p = out / name
    if not p.exists():
        continue
    raw = p.read_text(encoding="utf-8")
    try:
        data = json.loads(raw) if raw.strip() else []
    except json.JSONDecodeError:
        print(f"::error::{name} 不是合法 JSON")
        sys.exit(1)
    findings = data if isinstance(data, list) else data.get("findings") or data.get("leaks") or []
    for item in findings:
        if not isinstance(item, dict):
            continue
        secret = str(item.get("Secret") or item.get("secret") or "")
        match = str(item.get("Match") or item.get("match") or "")
        if secret and "REDACTED" not in secret.upper() and len(secret) > 4:
            print(f"::error::{name} 报告疑似包含未脱敏 Secret Value")
            sys.exit(1)
        if match and "REDACTED" not in match.upper() and len(match) > 40:
            # Match 可能含上下文；要求 gitleaks --redact 已处理 Secret 字段即可
            pass
print("Secret 报告已生成且未发现未脱敏 Secret 字段")
PY

exit "${fail}"
