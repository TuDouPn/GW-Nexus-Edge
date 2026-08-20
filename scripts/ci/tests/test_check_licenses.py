#!/usr/bin/env python3
"""check-licenses.py 回归：版本漂移、许可证漂移、新组件、已登记 REVIEW_REQUIRED。"""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MOD_PATH = ROOT / "scripts" / "ci" / "check-licenses.py"


def load_mod():
    spec = importlib.util.spec_from_file_location("check_licenses", MOD_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


CL = load_mod()


def row(key, version, licenses, evidence="sbom", status="POLICY_ACCEPT", reason="permissive-identified"):
    return {
        "key": key,
        "version": version,
        "licenses": licenses,
        "evidence": evidence,
        "status": status,
        "reason": reason,
    }


BASELINE = {
    "identified": {
        "org.example:ok": {
            "version": "1.0.0",
            "licenses": ["Apache-2.0"],
            "evidence": "sbom",
            "status": "POLICY_ACCEPT",
            "reason": "permissive-identified",
        },
        "org.example:review": {
            "version": "2.0.0",
            "licenses": ["EPL-2.0", "LGPL-2.1-only"],
            "evidence": "sbom",
            "status": "REVIEW_REQUIRED",
            "reason": "multi-license",
        },
    }
}


class CheckLicensesTests(unittest.TestCase):
    def test_registered_review_required_allowed(self):
        findings = CL.check_rows(
            [
                row("org.example:ok", "1.0.0", ["Apache-2.0"]),
                row(
                    "org.example:review",
                    "2.0.0",
                    ["EPL-2.0", "LGPL-2.1-only"],
                    status="REVIEW_REQUIRED",
                    reason="multi-license",
                ),
            ],
            BASELINE,
        )
        self.assertEqual(findings, [])

    def test_version_drift_blocked(self):
        findings = CL.check_rows(
            [
                row("org.example:ok", "1.0.1", ["Apache-2.0"]),
                row(
                    "org.example:review",
                    "2.0.0",
                    ["EPL-2.0", "LGPL-2.1-only"],
                    status="REVIEW_REQUIRED",
                    reason="multi-license",
                ),
            ],
            BASELINE,
        )
        self.assertTrue(any("版本漂移" in f and "org.example:ok" in f for f in findings), findings)

    def test_license_drift_blocked(self):
        findings = CL.check_rows(
            [
                row("org.example:ok", "1.0.0", ["MIT"]),
                row(
                    "org.example:review",
                    "2.0.0",
                    ["EPL-2.0", "LGPL-2.1-only"],
                    status="REVIEW_REQUIRED",
                    reason="multi-license",
                ),
            ],
            BASELINE,
        )
        self.assertTrue(any("许可证漂移" in f and "org.example:ok" in f for f in findings), findings)

    def test_new_component_blocked(self):
        findings = CL.check_rows(
            [
                row("org.example:ok", "1.0.0", ["Apache-2.0"]),
                row(
                    "org.example:review",
                    "2.0.0",
                    ["EPL-2.0", "LGPL-2.1-only"],
                    status="REVIEW_REQUIRED",
                    reason="multi-license",
                ),
                row("org.example:new", "0.1.0", ["Apache-2.0"]),
            ],
            BASELINE,
        )
        self.assertTrue(any("新增未进入 baseline" in f and "org.example:new" in f for f in findings), findings)

    def test_policy_accept_to_review_required_blocked(self):
        findings = CL.check_rows(
            [
                row("org.example:ok", "1.0.0", ["GPL-2.0"], status="REVIEW_REQUIRED", reason="ambiguous-legal"),
                row(
                    "org.example:review",
                    "2.0.0",
                    ["EPL-2.0", "LGPL-2.1-only"],
                    status="REVIEW_REQUIRED",
                    reason="multi-license",
                ),
            ],
            BASELINE,
        )
        self.assertTrue(any("许可证状态变化" in f and "org.example:ok" in f for f in findings), findings)

    def test_missing_license_not_in_baseline_blocked(self):
        findings = CL.check_rows(
            [
                row("org.example:ok", "1.0.0", ["Apache-2.0"]),
                row(
                    "org.example:review",
                    "2.0.0",
                    ["EPL-2.0", "LGPL-2.1-only"],
                    status="REVIEW_REQUIRED",
                    reason="multi-license",
                ),
                row("org.example:unknown", "3.0.0", [], status="REVIEW_REQUIRED", reason="missing"),
            ],
            BASELINE,
        )
        self.assertTrue(any("org.example:unknown" in f for f in findings), findings)


if __name__ == "__main__":
    unittest.main()
