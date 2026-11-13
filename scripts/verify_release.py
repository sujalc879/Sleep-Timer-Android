#!/usr/bin/env python3
"""Inspect locally built release artifacts; does not install or publish anything."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
from zipfile import ZIP_STORED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
PAGE = 16_384
ALLOWED_PERMISSIONS = {
    "POST_PROMOTED_NOTIFICATIONS", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE",
    "POST_NOTIFICATIONS", "SCHEDULE_EXACT_ALARM", "RECEIVE_BOOT_COMPLETED", "WAKE_LOCK",
    "VIBRATE", "MODIFY_AUDIO_SETTINGS", "ACCESS_NETWORK_STATE",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def elf_alignment(data):
    """Check ELF program headers, including congruence and bounds, without an NDK."""
    require(len(data) >= 16 and data[:4] == b"\x7fELF", "Invalid ELF header")
    bits, endian = data[4], data[5]
    require(bits in (1, 2) and endian == 1, "Unsupported ELF class or byte order")
    wide = bits == 2
    header_size, entry_size = (64, 56) if wide else (52, 32)
    require(len(data) >= header_size, "Truncated ELF header")
    offset = struct.unpack_from("<Q" if wide else "<I", data, 32 if wide else 28)[0]
    stride, count = struct.unpack_from("<HH", data, 54 if wide else 42)
    require(count > 0 and stride >= entry_size, "Invalid ELF program header table")
    require(offset >= header_size and offset + stride * count <= len(data), "Truncated ELF program headers")
    alignments = []
    for index in range(count):
        base = offset + stride * index
        if struct.unpack_from("<I", data, base)[0] != 1:  # PT_LOAD
            continue
        if wide:
            file_offset, address = struct.unpack_from("<QQ", data, base + 8)
            file_size = struct.unpack_from("<Q", data, base + 32)[0]
            alignment = struct.unpack_from("<Q", data, base + 48)[0]
        else:
            file_offset, address = struct.unpack_from("<II", data, base + 4)
            file_size = struct.unpack_from("<I", data, base + 16)[0]
            alignment = struct.unpack_from("<I", data, base + 28)[0]
        require(alignment > 0 and alignment & (alignment - 1) == 0, "Invalid ELF segment alignment")
        require(file_offset + file_size <= len(data), "ELF segment exceeds file")
        require((address - file_offset) % alignment == 0, "ELF segment offset/address are incongruent")
        if wide:
            require(alignment >= PAGE, "64-bit ELF PT_LOAD alignment is below 16 KB")
        alignments.append(alignment)
    require(alignments, "ELF has no loadable segments")
    return {"bits": 64 if wide else 32, "load_alignments": alignments}


def zip_data_offset(archive, entry):
    with open(archive, "rb") as stream:
        stream.seek(entry.header_offset)
        header = stream.read(30)
    require(len(header) == 30 and header[:4] == b"PK\x03\x04", "Invalid ZIP local header")
    name_size, extra_size = struct.unpack_from("<HH", header, 26)
    return entry.header_offset + 30 + name_size + extra_size


def inspect_archive(path, apk):
    native = {}
    with ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate ZIP entries")
        for entry in archive.infolist():
            if entry.filename.endswith(".so") and "/lib/" in "/" + entry.filename:
                data = archive.read(entry)
                record = elf_alignment(data)
                record["sha256"] = hashlib.sha256(data).hexdigest()
                record["stored_uncompressed"] = entry.compress_type == ZIP_STORED
                if apk and record["stored_uncompressed"]:
                    offset = zip_data_offset(path, entry)
                    require(offset % PAGE == 0, f"Native APK entry is not 16 KB ZIP aligned: {entry.filename}")
                    record["zip_data_offset"] = offset
                native[entry.filename.removeprefix("base/")] = record
            if re.fullmatch(r"(?:base/dex/)?classes\d*\.dex", entry.filename):
                dex = archive.read(entry)
                for package in (b"Lleakcanary/", b"Lcom/squareup/leakcanary/", b"Landroidx/benchmark/"):
                    require(package not in dex, f"Debug/benchmark code in {entry.filename}")
        signed = any(re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC)", name) for name in names)
        profile = any(name.endswith("/baseline.prof") for name in names)
    return {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "native": native, "jar_signature_present": signed, "binary_profile_present": profile}


def sdk_path(explicit):
    if explicit:
        return Path(explicit).expanduser()
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            return Path(os.environ[name]).expanduser()
    for line in (ROOT / "local.properties").read_text().splitlines():
        if line.startswith("sdk.dir="):
            return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    raise ValueError("Provide --sdk or an SDK path in local.properties")


def tool_output(command):
    return subprocess.run(command, check=True, text=True, capture_output=True).stdout


def audit(args):
    metadata_path = ROOT / "app/build/outputs/apk/release/output-metadata.json"
    metadata = json.loads(metadata_path.read_text())
    require(metadata["variantName"] == "release" and len(metadata["elements"]) == 1, "Expected one release APK")
    apk = metadata_path.parent / metadata["elements"][0]["outputFile"]
    bundle = ROOT / "app/build/outputs/bundle/release/app-release.aab"
    sdk = sdk_path(args.sdk)
    tools = sorted((p for p in (sdk / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", p.name)),
                   key=lambda p: tuple(int(x) for x in p.name.split(".")))
    require(tools, "No installed stable Android Build Tools found")
    aapt = str(tools[-1] / "aapt2")
    permissions = re.findall(r"uses-permission: name='([^']+)'", tool_output([aapt, "dump", "permissions", str(apk)]))
    allowed = {"android.permission." + name for name in ALLOWED_PERMISSIONS}
    allowed.add(metadata["applicationId"] + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    require(set(permissions) <= allowed, "Unexpected release permission: " + ", ".join(sorted(set(permissions) - allowed)))
    badging = tool_output([aapt, "dump", "badging", str(apk)])
    require("application-debuggable" not in badging, "Release APK is debuggable")
    require("minSdkVersion:'26'" in badging and "targetSdkVersion:'37'" in badging, "Unexpected release SDK levels")
    manifest = tool_output([aapt, "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"])
    require("E: profileable" not in manifest, "Production APK must not be shell-profileable")
    mapping_dir = ROOT / "app/build/outputs/mapping/release"
    require((mapping_dir / "mapping.txt").is_file(), "Missing R8 mapping")
    configuration = (mapping_dir / "configuration.txt").read_text()
    require(not re.search(r"^-(dontoptimize|dontshrink|dontobfuscate)\s*$", configuration, re.MULTILINE),
            "Release R8 optimization/shrinking/obfuscation was disabled")
    apk_info, bundle_info = inspect_archive(apk, True), inspect_archive(bundle, False)
    require({k: v["sha256"] for k, v in apk_info["native"].items()} ==
            {k: v["sha256"] for k, v in bundle_info["native"].items()}, "APK/AAB native libraries differ")
    sources = list((ROOT / "app/src").glob("**/generated/baselineProfiles/baseline-prof.txt"))
    app_profiles = [str(p.relative_to(ROOT)) for p in sources if "Lcom/sujal/sleeptimer/" in p.read_text()]
    if args.require_app_profile:
        require(app_profiles, "Generate an app baseline profile on a connected device before distribution")
        require(apk_info["binary_profile_present"] and bundle_info["binary_profile_present"], "Missing packaged profile")
    if args.require_signed:
        require(bundle_info["jar_signature_present"], "Configure the external upload keystore before distribution")
        certificate = tool_output([str(tools[-1] / "apksigner"), "verify", "--print-certs", str(apk)])
        require("CN=Android Debug" not in certificate, "The standard debug certificate is not a release upload key")
        jdk = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else Path("/usr/bin")
        result = tool_output([str(jdk / "jarsigner"), "-J-Duser.language=en", "-verify", str(bundle)])
        require("jar verified" in result, "AAB signature verification did not succeed")
    return {"application_id": metadata["applicationId"], "version": metadata["elements"][0]["versionName"],
            "permissions": permissions, "r8_mapping_present": True, "app_profile_sources": app_profiles,
            "apk": apk_info, "aab": bundle_info,
            "limits": "Binary inspection only. Device smoke, 16 KB runtime, performance and Play split/download size remain separate gates."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--require-app-profile", action="store_true")
    parser.add_argument("--require-signed", action="store_true")
    args = parser.parse_args()
    try:
        report = audit(args)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Release artifact audit passed: APK {report['apk']['bytes']:,} bytes; AAB {report['aab']['bytes']:,} bytes")
        print(f"Native ELF/ZIP entries: {len(report['apk']['native'])}; app profile sources: {len(report['app_profile_sources'])}")
        print(report["limits"])
    except (ValueError, OSError, KeyError, struct.error, subprocess.CalledProcessError) as error:
        print(f"Release artifact audit failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
