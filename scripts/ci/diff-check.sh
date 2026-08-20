#!/usr/bin/env bash
# PR：merge-base(base SHA, head SHA) → head SHA
# push main：before → sha（提交范围）
# 禁止无范围的 git diff --check 作为 CI 门禁。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT}"

EVENT_NAME="${GITHUB_EVENT_NAME:-${1:-local}}"

if [ "${EVENT_NAME}" = "pull_request" ]; then
  BASE_SHA="${PR_BASE_SHA:?PR_BASE_SHA required}"
  HEAD_SHA="${PR_HEAD_SHA:?PR_HEAD_SHA required}"
  git cat-file -e "${BASE_SHA}^{commit}" 2>/dev/null || git fetch --no-tags origin "${BASE_SHA}"
  git cat-file -e "${HEAD_SHA}^{commit}" 2>/dev/null || git fetch --no-tags origin "${HEAD_SHA}"
  MERGE_BASE="$(git merge-base "${BASE_SHA}" "${HEAD_SHA}")"
  echo "git diff --check ${MERGE_BASE}..${HEAD_SHA} (PR base=${BASE_SHA})"
  git diff --check "${MERGE_BASE}..${HEAD_SHA}"
elif [ "${EVENT_NAME}" = "push" ]; then
  BEFORE="${GITHUB_EVENT_BEFORE:-}"
  AFTER="${GITHUB_SHA:-HEAD}"
  ZERO="0000000000000000000000000000000000000000"
  if [ -z "${BEFORE}" ] || [ "${BEFORE}" = "${ZERO}" ]; then
    echo "git diff --check ${AFTER}^..${AFTER} (push 无 before)"
    git diff --check "${AFTER}^..${AFTER}"
  else
    echo "git diff --check ${BEFORE}..${AFTER} (push 提交范围)"
    git diff --check "${BEFORE}..${AFTER}"
  fi
else
  echo "git diff --check（本地工作树；CI 必须传入 pull_request 或 push）"
  git diff --check
fi
