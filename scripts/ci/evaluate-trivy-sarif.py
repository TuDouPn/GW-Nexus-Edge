#!/usr/bin/env python3
"""评估 Trivy filesystem SARIF：HIGH/CRITICAL 默认阻断；仅接受未过期的版本化例外。"""
from __future__ import annotations

import json
import re
import sys
from datetime import date
from pathlib import Path


def fail(msg: str, code: int = 1) -> None:
    print(f"::error::{msg}")
    sys.exit(code)


def parse_pkg(message: str) -> tuple[str, str]:
    pkg = ""
    ver = ""
    m = re.search(r"Package:\s*(\S+)", message)
    if m:
        pkg = m.group(1).strip()
    m = re.search(r"Installed Version:\s*(\S+)", message)
    if m:
        ver = m.group(1).strip()
    return pkg, ver


def evaluate(data: dict, exceptions: list, trivy_code: int, today: str) -> tuple[int, str, list, list]:
    """返回 (exit_code, message, excepted, blocking)。exit 2 = TOOL_BLOCKED/INFRASTRUCTURE_FAILURE。"""
    runs = data.get("runs")
    if not isinstance(runs, list):
        return 2, "TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：SARIF 缺少 runs，不得视为无漏洞", [], []

    results = []
    for run in runs:
        results.extend(run.get("results") or [])

    if trivy_code not in (0, 1):
        return 2, f"TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：Trivy 退出码 {trivy_code}", [], []

    if trivy_code == 1 and len(results) == 0:
        return (
            2,
            "TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：Trivy exit=1 但 SARIF results=0，不得视为无漏洞",
            [],
            [],
        )

    blocking = []
    excepted = []
    for res in results:
        cve = res.get("ruleId") or ""
        msg = (res.get("message") or {}).get("text") or ""
        pkg, ver = parse_pkg(msg)
        matched = None
        for exc in exceptions:
            if (
                exc.get("cve") == cve
                and exc.get("component") == pkg
                and exc.get("version") == ver
            ):
                matched = exc
                break
        if matched:
            expires = str(matched.get("expires") or "")
            if not expires or expires < today:
                blocking.append((cve, pkg, ver, f"例外已过期 expires={expires or 'missing'}"))
            else:
                excepted.append((cve, pkg, ver, matched.get("reason", "")))
        else:
            blocking.append((cve, pkg, ver, "无有效例外"))

    if blocking:
        return 1, "HIGH/CRITICAL 漏洞阻断（filesystem/dependency scan，非 Container Image Scan）", excepted, blocking

    if trivy_code == 1:
        if not results or len(excepted) != len(results):
            return (
                2,
                "TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：exit=1 未满足「全部结果均为精确未过期例外」",
                excepted,
                blocking,
            )

    return 0, "Trivy filesystem/dependency scan：无未例外的 HIGH/CRITICAL 阻断项", excepted, blocking


def main() -> None:
    if len(sys.argv) != 4:
        fail("usage: evaluate-trivy-sarif.py <sarif> <exceptions.json> <trivy-exit-code>")
    sarif_path = Path(sys.argv[1])
    exc_path = Path(sys.argv[2])
    trivy_code = int(sys.argv[3])

    try:
        data = json.loads(sarif_path.read_text(encoding="utf-8"))
    except Exception as e:
        fail(f"TOOL_BLOCKED/INFRASTRUCTURE_FAILURE：SARIF 不可解析：{e}", 2)

    exceptions = []
    if exc_path.is_file():
        payload = json.loads(exc_path.read_text(encoding="utf-8"))
        exceptions = payload.get("exceptions") or []

    today = date.today().isoformat()
    results_count = 0
    for run in data.get("runs") or []:
        results_count += len(run.get("results") or [])
    print(f"Trivy filesystem/dependency scan SARIF results={results_count} exit={trivy_code}")

    code, message, excepted, blocking = evaluate(data, exceptions, trivy_code, today)
    for item in excepted:
        print(f"EXCEPTION {item[0]} {item[1]}:{item[2]} until documented expiry — {item[3][:180]}")
    for item in blocking:
        print(f"::error::HIGH/CRITICAL 阻断 {item[0]} {item[1]}:{item[2]} ({item[3]})")
    if code != 0:
        fail(message, code)
    print(message)


if __name__ == "__main__":
    main()
