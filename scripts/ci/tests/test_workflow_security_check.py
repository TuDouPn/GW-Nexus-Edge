#!/usr/bin/env python3
"""workflow-security-check：防止 maven-dependency-submission 的 backend/backend/mvnw。"""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MOD_PATH = ROOT / "scripts" / "ci" / "workflow-security-check.py"


def load_mod():
    spec = importlib.util.spec_from_file_location("workflow_security_check", MOD_PATH)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


WS = load_mod()

GOOD = """
name: Dependency Submission
on:
  push:
    branches: [main]
permissions:
  contents: write
jobs:
  submit:
    steps:
      - uses: advanced-security/maven-dependency-submission-action@b275d12641ac2d2108b2cbb7598b154ad2f2cee8 # v5.0.0
        with:
          directory: backend
          ignore-maven-wrapper: true
"""

MISSING_IGNORE = """
      - uses: advanced-security/maven-dependency-submission-action@b275d12641ac2d2108b2cbb7598b154ad2f2cee8 # v5.0.0
        with:
          directory: backend
"""

DOUBLED_PATH = """
      - run: backend/backend/mvnw -v
"""

CONTINUE = """
      - uses: advanced-security/maven-dependency-submission-action@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa # v5.0.0
        continue-on-error: true
        with:
          directory: backend
          ignore-maven-wrapper: true
"""


class MavenSubmissionPathTests(unittest.TestCase):
    def test_good_backend_with_ignore_wrapper(self):
        self.assertEqual(WS.check_maven_dependency_submission("dep.yml", GOOD), [])

    def test_directory_backend_without_ignore_wrapper_blocked(self):
        errors = WS.check_maven_dependency_submission("dep.yml", MISSING_IGNORE)
        self.assertTrue(any("ignore-maven-wrapper" in e for e in errors), errors)

    def test_doubled_backend_mvnw_blocked(self):
        errors = WS.check_maven_dependency_submission("dep.yml", DOUBLED_PATH)
        self.assertTrue(any("backend/backend" in e for e in errors), errors)

    def test_continue_on_error_blocked(self):
        errors = WS.check_maven_dependency_submission("dep.yml", CONTINUE)
        self.assertTrue(any("continue-on-error" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
