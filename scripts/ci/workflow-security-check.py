#!/usr/bin/env python3
"""Workflow 安全静态检查：可变 Action 引用、pull_request_target、PR 写权限、curl|sh、secrets 读取。"""
from __future__ import annotations

import glob
import re
import sys
from pathlib import Path

SHA_RE = re.compile(
    r"^\s+uses:\s+(\S+?)@([0-9a-f]{40})\s*(?:#\s*(\S+))?\s*$",
    re.M,
)
BAD_REF_RE = re.compile(
    r"^\s+uses:\s+\S+?@(main|master|v?\d+(?:\.\d+)*|[\da-f]{1,39})\s*$",
    re.M,
)
CURL_SH_RE = re.compile(r"curl\s+[^|\n]+\|\s*(?:ba)?sh")
SECRETS_RE = re.compile(r"secrets\.[A-Z0-9_]+")
FORBIDDEN_TRIGGER = "pull_request" + "_target"


def main() -> int:
    fail = False
    files = sorted(glob.glob(".github/workflows/*.yml"))
    if not files:
        print("::error::未找到 workflow 文件")
        return 1

    for f in files:
        text = Path(f).read_text(encoding="utf-8")

        if CURL_SH_RE.search(text):
            print(f"::error::{f} 禁止 curl|sh")
            fail = True

        for m in SECRETS_RE.finditer(text):
            name = m.group(0)
            if name != "secrets.GITHUB_TOKEN":
                print(f"::error::{f} 读取 Secret {name}（Fork 模型禁止）")
                fail = True
            else:
                print(f"::error::{f} 显式读取 secrets.GITHUB_TOKEN（扫描 Job 不得读取任何 Secret）")
                fail = True

        if re.search(rf"^[ \t]*{re.escape(FORBIDDEN_TRIGGER)}[ \t]*:", text, re.M):
            print(f"::error::{f} 禁止 {FORBIDDEN_TRIGGER} 触发事件执行 PR 代码")
            fail = True

        uses = list(SHA_RE.finditer(text))
        raw_uses = re.findall(r"^\s+uses:\s+(\S+)", text, re.M)
        if len(uses) != len(raw_uses):
            print(f"::error::{f} 存在未固定 40 位 commit SHA 的 Action 引用")
            fail = True
            for line in text.splitlines():
                if re.match(r"^\s+uses:", line) and not SHA_RE.search(line):
                    print(f"  {line.strip()}")
        for m in uses:
            action, sha, ver = m.group(1), m.group(2), m.group(3)
            if not ver:
                print(f"::error::{f} {action}@{sha} 缺少版本注释")
                fail = True

        if BAD_REF_RE.search(text):
            print(f"::error::{f} 发现可变 Action 引用（必须固定 commit SHA）")
            fail = True

        perm = re.search(r"^permissions:\n((?:[ \t]+[^ \t].*\n)+)", text, re.M)
        if perm:
            block = perm.group(1)
            if "write-all" in block:
                print(f"::error::{f} 声明 write-all（禁止）")
                fail = True
            pr_triggered = bool(re.search(r"^[ \t]+pull_request\s*:", text, re.M))
            if pr_triggered and re.search(r"^[ \t]+\S+:[ \t]*write\s*$", block, re.M):
                print(f"::error::{f} 由 PR 触发却声明工作流级写权限（应只读 contents: read）")
                fail = True

        # 高权限必须限制在独立 Job + 明确事件条件
        if "security-events: write" in text:
            if "if: github.event_name == 'push' && github.ref == 'refs/heads/main'" not in text:
                print(f"::error::{f} security-events: write 必须限制在 push main 的独立 Job")
                fail = True

    if fail:
        return 1
    print("Workflow 安全检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
