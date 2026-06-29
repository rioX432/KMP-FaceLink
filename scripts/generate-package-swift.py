#!/usr/bin/env python3
"""Generate Package.swift from a GitHub Release's XCFramework assets.

KMP-FaceLink is a PRIVATE repository, so SwiftPM cannot resolve binary targets
from public `releases/download/...` URLs. Instead we point each binaryTarget at
the GitHub REST API asset URL (`.../releases/assets/<id>.zip`), which SwiftPM can
fetch with an authenticated token (matches the persona-domain-kmm convention).

Usage:
    generate-package-swift.py <out_path> <version> <release_json> <zips_dir>

- release_json: output of `gh api /repos/<owner>/<repo>/releases/tags/<tag>`
  (must contain `.assets[]` with `name` and `url`).
- zips_dir: directory holding the `<Framework>.xcframework.zip` files used to
  compute SHA-256 checksums.
"""

import hashlib
import json
import sys
from pathlib import Path

# Canonical ordering kept stable for clean diffs; any extra frameworks present in
# the release but missing here are appended alphabetically.
CANONICAL_ORDER = [
    "KMPFaceLink",
    "KMPFaceLinkAvatar",
    "KMPFaceLinkActions",
    "KMPFaceLinkVoice",
    "KMPFaceLinkLlm",
    "KMPFaceLinkPipeline",
    "KMPFaceLinkNetworking",
    "KMPFaceLinkVMC",
    "KMPFaceLinkIFM",
    "KMPFaceLinkInteract",
]

SUFFIX = ".xcframework.zip"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 5:
        print(f"Usage: {sys.argv[0]} <out_path> <version> <release_json> <zips_dir>")
        sys.exit(1)

    out_path = Path(sys.argv[1])
    version = sys.argv[2].lstrip("v")
    release = json.loads(Path(sys.argv[3]).read_text())
    zips_dir = Path(sys.argv[4])

    # Map framework name -> API asset URL.
    asset_urls: dict[str, str] = {}
    for asset in release.get("assets", []):
        name = asset.get("name", "")
        if name.endswith(SUFFIX):
            asset_urls[name[: -len(SUFFIX)]] = asset["url"]

    # Frameworks actually built (zips on disk), ordered canonically.
    present = {p.name[: -len(SUFFIX)] for p in zips_dir.glob(f"*{SUFFIX}")}
    ordered = [fw for fw in CANONICAL_ORDER if fw in present]
    ordered += sorted(present - set(CANONICAL_ORDER))

    missing_assets = [fw for fw in ordered if fw not in asset_urls]
    if missing_assets:
        print(f"ERROR: no release asset for: {', '.join(missing_assets)}")
        sys.exit(1)

    products, targets = [], []
    for fw in ordered:
        checksum = sha256(zips_dir / f"{fw}{SUFFIX}")
        # SwiftPM requires the URL to end in .zip; the asset API URL has no
        # extension, so append it (GitHub serves the binary regardless).
        url = f"{asset_urls[fw]}.zip"
        products.append(f'        .library(name: "{fw}", targets: ["{fw}"]),')
        targets.append(
            "        .binaryTarget(\n"
            f'            name: "{fw}",\n'
            f'            url: "{url}",\n'
            f'            checksum: "{checksum}"\n'
            "        ),"
        )

    content = (
        "// swift-tools-version: 5.9\n"
        "import PackageDescription\n"
        "\n"
        f'let version = "{version}"\n'
        "\n"
        "let package = Package(\n"
        '    name: "KMPFaceLink",\n'
        "    platforms: [\n"
        "        .iOS(.v17),\n"
        "    ],\n"
        "    products: [\n"
        + "\n".join(products)
        + "\n    ],\n"
        "    targets: [\n"
        + "\n".join(targets)
        + "\n    ]\n"
        ")\n"
    )
    out_path.write_text(content)
    print(f"Generated {out_path} with {len(ordered)} frameworks for {version}")


if __name__ == "__main__":
    main()
