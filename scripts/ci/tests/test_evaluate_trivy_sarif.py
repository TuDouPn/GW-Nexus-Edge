#!/usr/bin/env python3
"""evaluate-trivy-sarif.py 回归：无漏洞、空结果异常、未例外、有效例外、过期例外。"""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MOD_PATH = ROOT / "scripts" / "ci" / "evaluate-trivy-sarif.py"


def load_mod():
    spec = importlib.util.spec_from_file_location("evaluate_trivy_sarif", MOD_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


EV = load_mod()
TODAY = "2026-08-20"


def sarif(results):
    return {"runs": [{"results": results}]}


def finding(cve, pkg, ver):
    return {
        "ruleId": cve,
        "message": {"text": f"Package: {pkg}\nInstalled Version: {ver}\nVulnerability {cve}"},
    }


EXC = [
    {
        "cve": "CVE-2026-35568",
        "component": "io.modelcontextprotocol.sdk:mcp-core",
        "version": "0.17.0",
        "expires": "2026-11-20",
        "reason": "client-only",
    }
]


class EvaluateTrivyTests(unittest.TestCase):
    def test_no_vulnerabilities(self):
        code, msg, excepted, blocking = EV.evaluate(sarif([]), EXC, 0, TODAY)
        self.assertEqual(code, 0, msg)
        self.assertEqual(blocking, [])
        self.assertEqual(excepted, [])

    def test_exit_1_empty_results_is_infrastructure_failure(self):
        code, msg, excepted, blocking = EV.evaluate(sarif([]), EXC, 1, TODAY)
        self.assertEqual(code, 2, msg)
        self.assertIn("TOOL_BLOCKED/INFRASTRUCTURE_FAILURE", msg)
        self.assertIn("results=0", msg)

    def test_unexcepted_vulnerability_blocked(self):
        data = sarif([finding("CVE-2026-59901", "io.netty:netty-codec-compression", "4.2.15.Final")])
        code, msg, excepted, blocking = EV.evaluate(data, EXC, 1, TODAY)
        self.assertEqual(code, 1, msg)
        self.assertTrue(any(b[0] == "CVE-2026-59901" for b in blocking), blocking)

    def test_valid_exception_allows_exit_1(self):
        data = sarif([finding("CVE-2026-35568", "io.modelcontextprotocol.sdk:mcp-core", "0.17.0")])
        code, msg, excepted, blocking = EV.evaluate(data, EXC, 1, TODAY)
        self.assertEqual(code, 0, msg)
        self.assertEqual(blocking, [])
        self.assertEqual(len(excepted), 1)

    def test_expired_exception_blocked(self):
        data = sarif([finding("CVE-2026-35568", "io.modelcontextprotocol.sdk:mcp-core", "0.17.0")])
        code, msg, excepted, blocking = EV.evaluate(data, EXC, 1, "2026-11-21")
        self.assertEqual(code, 1, msg)
        self.assertTrue(any("例外已过期" in b[3] for b in blocking), blocking)


if __name__ == "__main__":
    unittest.main()
