#!/usr/bin/env python3
"""Update Package.swift checksums from a sha256sum output file.

Usage:
    python3 update-package-swift.py <Package.swift> <checksums.txt> <tag>

checksums.txt is expected to be the output of `sha256sum *.zip`, e.g.:
    abc123  KMPFaceLink.xcframework.zip
"""

import re
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 4:
        print(f"Usage: {sys.argv[0]} <Package.swift> <checksums.txt> <tag>")
        sys.exit(1)

    package_path = Path(sys.argv[1])
    checksums_path = Path(sys.argv[2])
    tag = sys.argv[3]

    # Parse checksums file
    checksums: dict[str, str] = {}
    for line in checksums_path.read_text().strip().splitlines():
        parts = line.split()
        if len(parts) == 2:
            checksum, filename = parts
            # Strip .xcframework.zip to get the framework name
            name = filename.replace(".xcframework.zip", "")
            checksums[name] = checksum

    content = package_path.read_text()

    # Update version
    version_match = re.search(r'let version = ".*?"', content)
    if version_match:
        # Strip 'v' prefix from tag
        version = tag.lstrip("v")
        content = content.replace(
            version_match.group(0), f'let version = "{version}"'
        )

    # Update each checksum
    for name, checksum in checksums.items():
        # Match the binaryTarget block for this framework
        pattern = (
            rf'(\.binaryTarget\(\s*name:\s*"{name}".*?checksum:\s*)"[^"]*?"'
        )
        content = re.sub(
            pattern, rf'\1"{checksum}"', content, flags=re.DOTALL
        )

    package_path.write_text(content)
    print(f"Updated Package.swift with {len(checksums)} checksums for {tag}")


if __name__ == "__main__":
    main()
