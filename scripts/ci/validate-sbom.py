#!/usr/bin/env python3
"""校验 CycloneDX SBOM：文件存在、可解析、包含实际组件。禁止只检查命令退出码。"""
from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def fail(msg: str) -> None:
    print(f"::error::{msg}")
    sys.exit(1)


def validate_json(path: Path) -> int:
    if not path.is_file():
        fail(f"SBOM JSON 不存在：{path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        fail(f"SBOM JSON 不可解析：{e}")
    components = data.get("components")
    if not isinstance(components, list) or len(components) == 0:
        fail("SBOM JSON 未包含实际组件")
    print(f"SBOM JSON 组件数: {len(components)}")
    return len(components)


def validate_xml(path: Path) -> int:
    if not path.is_file():
        fail(f"SBOM XML 不存在：{path}")
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        fail(f"SBOM XML 不可解析：{e}")
    components = [el for el in root.iter() if el.tag.endswith("component") and "components" not in (el.tag,)]
    # CycloneDX 使用 {namespace}component；过滤 components 容器。
    real = [
        el
        for el in root.iter()
        if el.tag.endswith("component") and not el.tag.endswith("components")
    ]
    if not real:
        fail("SBOM XML 未包含实际组件")
    print(f"SBOM XML 组件数: {len(real)}")
    return len(real)


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: validate-sbom.py <aggregate-bom.json> <aggregate-bom.xml>")
    n_json = validate_json(Path(sys.argv[1]))
    n_xml = validate_xml(Path(sys.argv[2]))
    if n_json <= 0 or n_xml <= 0:
        fail("SBOM 组件数为 0")
    print("SBOM JSON/XML 存在、可解析且包含实际组件")


if __name__ == "__main__":
    main()
