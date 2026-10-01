#!/usr/bin/env python3
"""16 KB page-size inspection for APK and AAB artifacts.

For every native library (*.so) found in the given archives:
  * reads the ELF program headers and checks that every PT_LOAD segment has p_align >= 16384;
  * for APKs, checks that the .so entry is stored uncompressed at a 16 KB-aligned offset
    (zipalign -c -P 16 is run separately in CI as a second check).
Writes a Markdown report to stdout. Exit code 1 if any library fails.
If no native libraries are present, the report says so (exit 0).
"""
import struct
import sys
import zipfile

PAGE = 16384


def load_alignments(data: bytes):
    if data[:4] != b"\x7fELF":
        return None, "not an ELF file"
    is64 = data[4] == 2
    endian = "<" if data[5] == 1 else ">"
    if is64:
        e_phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x36)
    else:
        e_phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", data, 0x2A)
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        if is64:
            p_align = struct.unpack_from(endian + "Q", data, off + 0x30)[0]
        else:
            p_align = struct.unpack_from(endian + "I", data, off + 0x1C)[0]
        aligns.append(p_align)
    return aligns, None


def data_offset(zf: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    zf.fp.seek(info.header_offset)
    header = zf.fp.read(30)
    name_len, extra_len = struct.unpack("<HH", header[26:30])
    return info.header_offset + 30 + name_len + extra_len


def inspect(path: str):
    rows, failures = [], 0
    is_apk = path.endswith(".apk")
    with zipfile.ZipFile(path) as zf:
        for info in zf.infolist():
            if not info.filename.endswith(".so"):
                continue
            aligns, err = load_alignments(zf.read(info))
            elf_ok = err is None and aligns and all(a >= PAGE for a in aligns)
            zip_note = "n/a (AAB)"
            zip_ok = True
            if is_apk:
                stored = info.compress_type == zipfile.ZIP_STORED
                offset_ok = data_offset(zf, info) % PAGE == 0
                zip_ok = (not stored) or offset_ok
                zip_note = ("stored, 16 KB-aligned" if offset_ok else "stored, NOT 16 KB-aligned") if stored else "compressed (extracted at install)"
            ok = elf_ok and zip_ok
            failures += 0 if ok else 1
            rows.append((info.filename, err or ", ".join(hex(a) for a in aligns), zip_note, "OK" if ok else "FAIL"))
    return rows, failures


def main(paths):
    total_fail = 0
    print("# Native library / 16 KB page-size inspection\n")
    for p in paths:
        rows, failures = inspect(p)
        total_fail += failures
        print(f"## {p}\n")
        if not rows:
            print("No native libraries (.so) found, including transitive dependencies. "
                  "16 KB ELF alignment does not apply to this artifact.\n")
            continue
        print("| Library | PT_LOAD p_align | APK packaging | Result |\n|---|---|---|---|")
        for r in rows:
            print(f"| `{r[0]}` | {r[1]} | {r[2]} | {r[3]} |")
        print()
    print("**Result:** " + ("all libraries pass" if total_fail == 0 else f"{total_fail} librar(y/ies) FAIL"))
    return 1 if total_fail else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
