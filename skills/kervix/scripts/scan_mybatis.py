#!/usr/bin/env python3
"""Create an offline inventory of MyBatis migration signals."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


TEXT_SUFFIXES = {
    ".gradle",
    ".java",
    ".kts",
    ".properties",
    ".xml",
    ".yaml",
    ".yml",
}
BUILD_NAMES = {"pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}
EXCLUDED_PARTS = {
    ".git",
    ".idea",
    ".ai",
    ".cursor",
    ".github",
    "target",
    "build",
    "node_modules",
    "docs",
}
MAX_FILE_BYTES = 2_000_000

PATTERNS = {
    "MyBatis imports": re.compile(
        r"\bimport\s+org\.apache\.ibatis(?:\.|\s*;)|"
        r"\bimport\s+org\.mybatis\.(?:logging(?:\.|\s*;)|"
        r"spring(?:\.(?:annotation|batch|config|mapper|support|transaction)|\.[A-Z]|\s*;))"
    ),
    "Mapper annotations": re.compile(
        r"@(Mapper|Select|Insert|Update|Delete|SelectProvider|InsertProvider|"
        r"UpdateProvider|DeleteProvider|Results?|Options|ResultMap)"
    ),
    "Mapper XML declarations": re.compile(
        r"<(?:mapper|select|insert|update|delete|resultMap|sql|include|cache|plugins?)\b"
    ),
    "Dynamic SQL": re.compile(
        r"<(?:if|choose|when|otherwise|trim|where|set|foreach|bind)\b"
    ),
    "SQL parameters": re.compile(r"#\{[^}]+\}"),
    "Text substitution": re.compile(r"\$\{[^}]+\}"),
    "Nested or graph mapping": re.compile(
        r"<(?:association|collection|discriminator)\b|"
        r"\b(?:select|resultMap)=['\"][^'\"]+['\"]"
    ),
    "Sessions and runtime configuration": re.compile(
        r"\b(?:SqlSession|SqlSessionFactory|Configuration|"
        r"MapperScannerConfigurer|SqlSessionFactoryBean)\b"
    ),
    "Plugins and caches": re.compile(
        r"\b(?:Interceptor|@Intercepts|Cache|@CacheNamespace)\b|<cache\b|<plugins\b"
    ),
    "Spring integration": re.compile(
        r"\bimport\s+org\.mybatis\.spring(?:\.(?:annotation|batch|config|mapper|support|transaction)|\.[A-Z]|\s*;)|"
        r"@(?:Transactional|MapperScan)|SqlSession"
    ),
    "Build dependencies": re.compile(
        r"<groupId>\s*org\.mybatis\s*</groupId>|"
        r"<artifactId>\s*(?:mybatis|mybatis-spring|mybatis-spring-boot-starter)\s*</artifactId>|"
        r"(?:org\.mybatis:(?:mybatis|mybatis-spring|mybatis-spring-boot-starter)\b)"
    ),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def source_files(project: Path) -> list[Path]:
    files: list[Path] = []
    for path in project.rglob("*"):
        if not path.is_file() or path.stat().st_size > MAX_FILE_BYTES:
            continue
        if path.name in BUILD_NAMES or path.suffix.lower() in TEXT_SUFFIXES:
            if not any(part in EXCLUDED_PARTS for part in path.parts):
                files.append(path)
    return sorted(files)


def matching_categories(path: Path, line: str) -> list[str]:
    suffix = path.suffix.lower()
    is_java = suffix == ".java"
    is_xml = suffix == ".xml"
    is_build = path.name in BUILD_NAMES
    is_build_config = is_build or path.name == "settings.xml" or ".mvn" in path.parts
    stripped = line.lstrip()
    if stripped.startswith(("//", "/*", "*", "<!--", "-->")):
        return []
    categories: list[str] = []
    for category, pattern in PATTERNS.items():
        if category == "MyBatis imports" and not is_java:
            continue
        if category == "Mapper annotations" and not is_java:
            continue
        if category in {"Mapper XML declarations", "Nested or graph mapping"} and not is_xml:
            continue
        if category == "Build dependencies" and not is_build:
            continue
        if category == "Text substitution" and (not (is_java or is_xml) or is_build_config):
            continue
        if is_build and category in {
            "Mapper XML declarations",
            "Dynamic SQL",
            "Nested or graph mapping",
            "Plugins and caches",
        }:
            continue
        if pattern.search(line):
            categories.append(category)
    return categories


def render(project: Path, files: list[Path], findings: list[tuple[Path, int, str]]) -> str:
    counts: dict[str, int] = {}
    for _, _, category in findings:
        counts[category] = counts.get(category, 0) + 1

    lines = [
        "# MyBatis Migration Inventory",
        "",
        "This inventory was generated offline. It reports source signals only; "
        "it does not classify compatibility or modify the project.",
        "",
        f"- Project: `{project}`",
        f"- Scanned text files: `{len(files)}`",
        f"- Findings: `{len(findings)}`",
        "",
        "## Signal Summary",
        "",
        "| Signal | Count |",
        "| --- | ---: |",
    ]
    for category in sorted(counts):
        lines.append(f"| {category} | {counts[category]} |")
    if not counts:
        lines.append("| None | 0 |")

    lines.extend(["", "## Evidence", ""])
    if not findings:
        lines.append("None.")
    else:
        for path, line_number, category in findings:
            relative = path.relative_to(project)
            lines.append(f"- `{relative}:{line_number}` **{category}**: `{path_line(path, line_number)}`")

    lines.extend(
        [
            "",
            "## Next Review",
            "",
            "Inspect each referenced declaration and classify it against the pinned "
            "Kervix compatibility matrix. A signal is not proof of an unsupported "
            "construct and must not be converted without semantic review.",
            "",
        ]
    )
    return "\n".join(lines)


def path_line(path: Path, line_number: int) -> str:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    return lines[line_number - 1].strip()[:240]


def main() -> int:
    args = parse_args()
    project = args.project.resolve()
    if not project.is_dir():
        raise SystemExit(f"project is not a directory: {project}")

    files = source_files(project)
    findings: list[tuple[Path, int, str]] = []
    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        for line_number, line in enumerate(text.splitlines(), start=1):
            for category in matching_categories(path, line):
                findings.append((path, line_number, category))

    report = render(project, files, findings)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
