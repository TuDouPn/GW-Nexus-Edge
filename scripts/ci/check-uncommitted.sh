#!/usr/bin/env bash
# 工具运行后工作树必须干净。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}"

if [ -n "$(git status --porcelain)" ]; then
  echo "::error::工作树存在未提交变更（工具运行后不应产生）"
  git status --porcelain
  git diff
  exit 1
fi
echo "工作树干净"
