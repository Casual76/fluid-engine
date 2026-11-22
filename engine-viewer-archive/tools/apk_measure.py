"""Measure APK/native/Dex payloads and verify 16 KiB placement of 64-bit libarchive."""

from argparse import ArgumentParser
from hashlib import sha256
from pathlib import Path
from struct import unpack_from
from zipfile import ZIP_STORED, ZipFile


def measure(path):
    with path.open("rb") as file, ZipFile(path) as apk:
        digest = sha256(file.read()).hexdigest()
        print(f"{path.name} {path.stat().st_size:,} bytes, SHA-256 {digest}")
        dex = [entry for entry in apk.infolist() if entry.filename.startswith("classes") and entry.filename.endswith(".dex")]
        print(f"DEX {sum(entry.file_size for entry in dex):,} bytes, {sum(entry.compress_size for entry in dex):,} in APK")
        libs = [entry for entry in apk.infolist() if entry.filename.endswith("/libarchive-jni.so")]
        failures = []
        for entry in sorted(libs, key=lambda lib: lib.filename):
            abi = entry.filename.split("/")[1]
            file.seek(entry.header_offset)
            name_size, extra_size = unpack_from("<HH", file.read(30), 26)
            offset = entry.header_offset + 30 + name_size + extra_size
            elf = apk.read(entry)
            is64 = elf[4] == 2
            table_offset = unpack_from("<Q" if is64 else "<I", elf, 32 if is64 else 28)[0]
            stride, count = unpack_from("<HH", elf, 54 if is64 else 42)
            loads = [table_offset + i * stride for i in range(count) if unpack_from("<I", elf, table_offset + i * stride)[0] == 1]
            alignment = {unpack_from("<Q" if is64 else "<I", elf, item + (48 if is64 else 28))[0] for item in loads}
            print(f"{abi:<12} {entry.file_size:>9,} bytes, {entry.compress_size:>9,} in APK, "
                  f"LOAD={','.join(map(str, sorted(alignment)))}, offset%16384={offset % 16384}")
            if abi in ("arm64-v8a", "x86_64") and (
                not loads or min(alignment) < 16384 or entry.compress_type != ZIP_STORED or offset % 16384
            ):
                failures.append(abi)
        if len(libs) != 4 or failures:
            raise SystemExit(f"Expected four libarchive ABIs and 16 KiB alignment in both 64-bit ABIs. Failures: {failures}")
        return path.stat().st_size, sum(entry.compress_size for entry in dex), sum(entry.file_size for entry in dex)


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("with_aes", type=Path)
    parser.add_argument("without_aes", type=Path, nargs="?")
    args = parser.parse_args()
    full = measure(args.with_aes)
    if args.without_aes:
        stripped = measure(args.without_aes)
        total, packed, dex = (a - b for a, b in zip(full, stripped))
        print(f"Fallback delta: APK {total:+,} bytes, packed DEX {packed:+,} bytes, DEX {dex:+,} bytes")
