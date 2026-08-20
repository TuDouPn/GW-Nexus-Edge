#!/usr/bin/env python3
"""从 CycloneDX SBOM + Maven POM 取证许可证。禁止凭组件名称猜测。

POLICY_ACCEPT：单一、有证据的宽松许可证（Apache-2.0/MIT/BSD-2/BSD-3/ISC/0BSD/CC0-1.0/EDL-1.0）。
REVIEW_REQUIRED：缺失、未识别、多许可证表达式，或法律结论不明确（GPL/LGPL/EPL/CDDL/FOSS Exception/Public Domain 等）。
CI --check：比较 baseline 的 key、精确 version、licenses、evidence、status、reason。
版本漂移、许可证证据变化、POLICY_ACCEPT→REVIEW_REQUIRED、缺失/未知许可证、新组件均阻断。
已登记且字段完全一致的 REVIEW_REQUIRED 进入人工复核清单，不把 G-07 写成 PASS。
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

ACCEPT_ALIASES = {
    "apache-2.0",
    "apache license 2.0",
    "apache license, version 2.0",
    "the apache license, version 2.0",
    "apache 2.0",
    "asl 2.0",
    "mit",
    "the mit license",
    "mit license",
    "bsd-2-clause",
    "bsd 2-clause",
    "bsd 2-clause license",
    "bsd-2-clause license",
    "simplified bsd",
    "bsd-3-clause",
    "bsd 3-clause",
    "bsd 3-clause license",
    "new bsd",
    "revised bsd",
    "isc",
    "isc license",
    "0bsd",
    "mit-0",
    "mit no attribution",
    "mit-0 license",
    "cc0-1.0",
    "cc0",
    "creative commons zero v1.0 universal",
    "edl-1.0",
    "eclipse distribution license - v 1.0",
    "eclipse distribution license v1.0",
}

AMBIGUOUS_HINTS = (
    "gpl",
    "lgpl",
    "agpl",
    "epl",
    "eclipse public",
    "cddl",
    "mpl",
    "mozilla",
    "foss exception",
    "classpath exception",
    "public domain",
    "proprietary",
    "commercial",
    "unknown",
)


def fail(msg: str, code: int = 1) -> None:
    print(f"::error::{msg}")
    sys.exit(code)


def norm(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip().lower())


def pom_path(m2: Path, group: str, artifact: str, version: str) -> Path:
    return m2 / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.pom"


def read_pom_licenses(path: Path, m2: Path, depth: int = 0) -> list[str]:
    if not path.is_file() or depth > 3:
        return []
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return []
    names: list[str] = []

    def license_nodes() -> list:
        found = list(root.findall("m:licenses/m:license", NS))
        if found:
            return found
        return list(root.findall("licenses/license"))

    def child_text(node, tagged: str, plain: str) -> str:
        return (
            (node.findtext(tagged, default="", namespaces=NS) or node.findtext(plain) or "")
        ).strip()

    for lic in license_nodes():
        name = child_text(lic, "m:name", "name")
        url = child_text(lic, "m:url", "url")
        if name:
            names.append(name)
        elif url:
            names.append(url)
    if names:
        return names
    parent = root.find("m:parent", NS)
    if parent is None:
        return []
    g = (parent.findtext("m:groupId", default="", namespaces=NS) or "").strip()
    a = (parent.findtext("m:artifactId", default="", namespaces=NS) or "").strip()
    v = (parent.findtext("m:version", default="", namespaces=NS) or "").strip()
    if not (g and a and v):
        return []
    return read_pom_licenses(pom_path(m2, g, a, v), m2, depth + 1)


def sbom_licenses(comp: dict) -> list[str]:
    out: list[str] = []
    for entry in comp.get("licenses") or []:
        lic = entry.get("license") or {}
        expr = entry.get("expression")
        if expr:
            out.append(str(expr).strip())
        name = (lic.get("name") or "").strip()
        url = (lic.get("url") or "").strip()
        spdx = (lic.get("id") or "").strip()
        if spdx:
            out.append(spdx)
        elif name:
            out.append(name)
        elif url:
            out.append(url)
    return [x for x in out if x]


def classify(licenses: list[str]) -> tuple[str, str]:
    unique = []
    for item in licenses:
        n = norm(item)
        if n and n not in unique:
            unique.append(n)
    if not unique:
        return "REVIEW_REQUIRED", "missing"
    if len(unique) > 1 or any(" or " in u or " and " in u or "(" in u for u in unique):
        if len(unique) > 1 or any(re.search(r"\bor\b|\band\b|\(", u) for u in unique):
            return "REVIEW_REQUIRED", "multi-license"
    text = unique[0]
    if any(h in text for h in AMBIGUOUS_HINTS):
        return "REVIEW_REQUIRED", "ambiguous-legal"
    if text in ACCEPT_ALIASES:
        return "POLICY_ACCEPT", "permissive-identified"
    return "REVIEW_REQUIRED", "unrecognized"


def component_key(comp: dict) -> str:
    group = (comp.get("group") or "").strip()
    name = (comp.get("name") or "").strip()
    return f"{group}:{name}" if group else name


def snapshot(row: dict) -> dict:
    return {
        "version": row.get("version") or "",
        "licenses": list(row.get("licenses") or []),
        "evidence": row.get("evidence") or "",
        "status": row.get("status") or "",
        "reason": row.get("reason") or "",
    }


def check_rows(rows: list[dict], baseline: dict) -> list[str]:
    """对比当前 SBOM 行与 baseline.identified。返回阻断原因；空列表表示检查通过。"""
    identified = baseline.get("identified") or {}
    findings: list[str] = []
    current_keys = {row["key"] for row in rows}
    for key in identified:
        if key not in current_keys:
            findings.append(f"baseline 组件从 SBOM 消失（阻断）: {key}")
    for row in rows:
        key = row["key"]
        cur = snapshot(row)
        prev = identified.get(key)
        missing_or_unknown = (not cur["licenses"]) or cur["reason"] in ("missing", "unrecognized")
        if prev is None:
            findings.append(
                f"新增未进入 baseline 的组件（阻断）: {key} version={cur['version']} licenses={cur['licenses']}"
            )
            continue
        prev_s = snapshot(prev)
        if prev_s["version"] != cur["version"]:
            findings.append(f"版本漂移（阻断）: {key} {prev_s['version']} → {cur['version']}")
        if prev_s["licenses"] != cur["licenses"]:
            findings.append(f"许可证漂移（阻断）: {key} {prev_s['licenses']} → {cur['licenses']}")
        if prev_s["evidence"] != cur["evidence"]:
            findings.append(f"许可证证据变化（阻断）: {key} {prev_s['evidence']} → {cur['evidence']}")
        if prev_s["status"] != cur["status"]:
            findings.append(f"许可证状态变化（阻断）: {key} {prev_s['status']} → {cur['status']}")
        if prev_s["reason"] != cur["reason"]:
            findings.append(f"许可证原因变化（阻断）: {key} {prev_s['reason']} → {cur['reason']}")
        if missing_or_unknown and prev_s != cur:
            findings.append(
                f"缺失或未知许可证未按已登记 REVIEW_REQUIRED 精确匹配（阻断）: {key}"
            )
    return findings


def collect(bom: dict, m2: Path) -> list[dict]:
    rows = []
    for comp in bom.get("components") or []:
        key = component_key(comp)
        version = (comp.get("version") or "").strip()
        group = (comp.get("group") or "").strip()
        name = (comp.get("name") or "").strip()
        licenses = sbom_licenses(comp)
        evidence = "sbom" if licenses else ""
        if not licenses and group and name and version:
            pom = pom_path(m2, group, name, version)
            licenses = read_pom_licenses(pom, m2)
            if licenses:
                evidence = "pom"
        status, reason = classify(licenses)
        rows.append(
            {
                "key": key,
                "version": version,
                "licenses": licenses,
                "evidence": evidence or "none",
                "status": status,
                "reason": reason,
            }
        )
    return rows


def render_notices(rows: list[dict]) -> str:
    lines = [
        "# THIRD-PARTY-NOTICES.md",
        "",
        "> 本清单由当前 Maven 依赖闭包（CycloneDX SBOM，`cyclonedx-maven-plugin:2.9.3:makeAggregateBom`）",
        "> 与对应 POM `<licenses>` 取证生成。禁止凭组件名称或二手表格猜测许可证。",
        "> `POLICY_ACCEPT`：有 POM/SBOM 证据的单一宽松许可证。",
        "> `REVIEW_REQUIRED`：缺失、未识别、多许可证，或法律结论不明确；不由 AI 解释为兼容。",
        "> G-07 仅在当前解析依赖全部完成逐项核验（REVIEW_REQUIRED 为空）后才能 PASS。",
        "",
        "## 组件清单",
        "",
        "| 组件 | 版本 | 许可证证据 | 状态 |",
        "|---|---|---|---|",
    ]
    for row in sorted(rows, key=lambda r: r["key"]):
        lic = "; ".join(row["licenses"]) if row["licenses"] else "（缺失）"
        lines.append(
            f"| {row['key']} | {row['version']} | {lic} ({row['evidence']}) | {row['status']} ({row['reason']}) |"
        )
    accept = sum(1 for r in rows if r["status"] == "POLICY_ACCEPT")
    review = sum(1 for r in rows if r["status"] == "REVIEW_REQUIRED")
    lines += [
        "",
        f"合计：{len(rows)} 个组件；POLICY_ACCEPT {accept}；REVIEW_REQUIRED {review}。",
        "",
        "## CI 工具（非 Maven 依赖，见 docs/dev-0005/supply-chain.md）",
        "",
        "GitHub Action 固定 40 位 commit SHA；Gitleaks / Trivy / actionlint 以官方 Release 二进制 + SHA256 校验安装。",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bom", required=True)
    parser.add_argument("--baseline", default="docs/dev-0005/license-baseline.json")
    parser.add_argument("--m2", default=str(Path.home() / ".m2" / "repository"))
    parser.add_argument("--write-baseline", action="store_true")
    parser.add_argument("--write-notices", action="store_true")
    parser.add_argument("--notices", default="THIRD-PARTY-NOTICES.md")
    parser.add_argument("--review-out", default="/tmp/license-review.json")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    bom_path = Path(args.bom)
    if not bom_path.is_file():
        fail(f"SBOM 不存在：{bom_path}")
    bom = json.loads(bom_path.read_text(encoding="utf-8"))
    rows = collect(bom, Path(args.m2))
    if not rows:
        fail("SBOM 无组件，License 检查不能通过")

    baseline_path = Path(args.baseline)
    baseline = {"version": 1, "identified": {}, "policy_accept": [], "review_required": []}
    if baseline_path.is_file():
        baseline = json.loads(baseline_path.read_text(encoding="utf-8"))

    review = [row for row in rows if row["status"] == "REVIEW_REQUIRED"]
    accept = [row for row in rows if row["status"] == "POLICY_ACCEPT"]
    findings = check_rows(rows, baseline)

    print(f"License 组件={len(rows)} POLICY_ACCEPT={len(accept)} REVIEW_REQUIRED={len(review)}")

    if args.write_baseline:
        payload = {
            "version": 2,
            "updated": "2026-08-20",
            "note": "identified 仅记录 POM/SBOM 证据；POLICY_ACCEPT 仅含明确单一宽松许可证；其余 REVIEW_REQUIRED，不由 AI 解释法律兼容性。",
            "identified": {
                row["key"]: {
                    "version": row["version"],
                    "licenses": row["licenses"],
                    "evidence": row["evidence"],
                    "status": row["status"],
                    "reason": row["reason"],
                }
                for row in sorted(rows, key=lambda r: r["key"])
            },
            "policy_accept": [r["key"] for r in sorted(accept, key=lambda r: r["key"])],
            "review_required": [
                {"key": r["key"], "reason": r["reason"], "licenses": r["licenses"]}
                for r in sorted(review, key=lambda r: r["key"])
            ],
        }
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"已写入 {baseline_path}")

    if args.write_notices:
        Path(args.notices).write_text(render_notices(rows), encoding="utf-8")
        print(f"已写入 {args.notices}")

    review_path = Path(args.review_out)
    review_path.parent.mkdir(parents=True, exist_ok=True)
    review_path.write_text(
        json.dumps(
            {
                "policy_accept": len(accept),
                "review_required": [{"key": r["key"], "reason": r["reason"], "licenses": r["licenses"]} for r in review],
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    if args.check:
        if findings:
            for item in findings:
                print(item)
            fail("License baseline 比对失败（版本/许可证/证据/状态变化或新组件不得默认通过；不构成 G-07 PASS）")
        print(
            "License 检查通过：当前闭包与 baseline 的 key/version/licenses/evidence/status/reason 一致；"
            "REVIEW_REQUIRED 已进入人工复核清单（不构成 G-07 PASS）"
        )


if __name__ == "__main__":
    main()
