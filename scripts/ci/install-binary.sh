#!/usr/bin/env bash
# 下载并校验官方 Release 二进制。禁止 curl|sh。
# 用法：install-binary.sh <gitleaks|trivy|actionlint> [install-dir]
set -euo pipefail

TOOL="${1:?usage: install-binary.sh <gitleaks|trivy|actionlint> [install-dir]}"
DEST="${2:-/tmp/gw-nexus-ci-tools}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck disable=SC1091
source "${ROOT}/scripts/ci/pins.env"

mkdir -p "${DEST}"
OS="$(uname -s)"
ARCH="$(uname -m)"

pick() {
  local linux_url="$1" linux_sha="$2" darwin_arm_url="$3" darwin_arm_sha="$4" darwin_x64_url="$5" darwin_x64_sha="$6"
  case "${OS}-${ARCH}" in
    Linux-x86_64|Linux-amd64)
      URL="${linux_url}"; SHA="${linux_sha}" ;;
    Darwin-arm64)
      URL="${darwin_arm_url}"; SHA="${darwin_arm_sha}" ;;
    Darwin-x86_64)
      URL="${darwin_x64_url}"; SHA="${darwin_x64_sha}" ;;
    *)
      echo "::error::不支持的平台 ${OS}-${ARCH}（${TOOL}）" >&2
      exit 2
      ;;
  esac
}

case "${TOOL}" in
  gitleaks)
    pick "${GITLEAKS_LINUX_X64_URL}" "${GITLEAKS_LINUX_X64_SHA256}" \
         "${GITLEAKS_DARWIN_ARM64_URL}" "${GITLEAKS_DARWIN_ARM64_SHA256}" \
         "${GITLEAKS_DARWIN_X64_URL}" "${GITLEAKS_DARWIN_X64_SHA256}"
    BIN_NAME="gitleaks"
    ;;
  trivy)
    pick "${TRIVY_LINUX_64_URL}" "${TRIVY_LINUX_64_SHA256}" \
         "${TRIVY_DARWIN_ARM64_URL}" "${TRIVY_DARWIN_ARM64_SHA256}" \
         "${TRIVY_DARWIN_X64_URL}" "${TRIVY_DARWIN_X64_SHA256}"
    BIN_NAME="trivy"
    ;;
  actionlint)
    pick "${ACTIONLINT_LINUX_AMD64_URL}" "${ACTIONLINT_LINUX_AMD64_SHA256}" \
         "${ACTIONLINT_DARWIN_ARM64_URL}" "${ACTIONLINT_DARWIN_ARM64_SHA256}" \
         "${ACTIONLINT_DARWIN_AMD64_URL}" "${ACTIONLINT_DARWIN_AMD64_SHA256}"
    BIN_NAME="actionlint"
    ;;
  *)
    echo "::error::未知工具 ${TOOL}" >&2
    exit 2
    ;;
esac

ARCHIVE="${DEST}/$(basename "${URL}")"
echo "下载 ${TOOL} ${URL}"
curl -fL --retry 3 --retry-delay 2 -o "${ARCHIVE}" "${URL}"

ACTUAL="$(shasum -a 256 "${ARCHIVE}" | awk '{print $1}')"
if [ "${ACTUAL}" != "${SHA}" ]; then
  echo "::error::SHA256 不匹配：${TOOL} expected=${SHA} actual=${ACTUAL}" >&2
  exit 2
fi
echo "SHA256 校验通过：${TOOL} ${SHA}"

tar -xzf "${ARCHIVE}" -C "${DEST}" "${BIN_NAME}"
chmod +x "${DEST}/${BIN_NAME}"
echo "安装完成：${DEST}/${BIN_NAME}"
echo "${DEST}/${BIN_NAME}"
