"""F8.17 · Le librerie native di un'APK (o di un jar/aar) sono pronte per le pagine da 16 kB?

Per ogni .so di arm64-v8a e x86_64: l'allineamento dei segmenti LOAD (p_align, deve essere almeno 16384) e, nelle APK,
se il file è salvato senza compressione con l'inizio dei dati allineato a 16 kB nello zip (quello che controlla anche
`zipalign -c -P 16`). Legge soltanto: non esegue niente.

Uso: python -I allineamento-16kb.py <file.apk|.jar|.aar> [...]
"""
import struct
import sys
import zipfile


def load_aligns(data):
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return None
    little = data[5] == 1
    e = "<" if little else ">"
    phoff = struct.unpack_from(e + "Q", data, 0x20)[0]
    phentsize, phnum = struct.unpack_from(e + "HH", data, 0x36)
    out = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type = struct.unpack_from(e + "I", data, off)[0]
        if p_type == 1:  # PT_LOAD
            p_align = struct.unpack_from(e + "Q", data, off + 0x30)[0]
            out.append(p_align)
    return out


def main():
    for path in sys.argv[1:]:
        print(f"== {path.replace(chr(92), '/').split('/')[-1]}")
        with zipfile.ZipFile(path) as z:
            for info in z.infolist():
                n = info.filename
                if not n.endswith(".so") or not ("arm64-v8a" in n or "x86_64" in n):
                    continue
                aligns = load_aligns(z.read(n))
                if aligns is None:
                    continue
                elf_ok = min(aligns) >= 16384
                zip_note = ""
                if path.endswith(".apk"):
                    if info.compress_type == zipfile.ZIP_STORED:
                        # Inizio dei dati: intestazione locale (30 byte) + nome + extra.
                        with open(path, "rb") as f:
                            f.seek(info.header_offset)
                            h = f.read(30)
                            nlen, xlen = struct.unpack_from("<HH", h, 26)
                        start = info.header_offset + 30 + nlen + xlen
                        zip_note = " zip:allineato" if start % 16384 == 0 else f" zip:NON allineato ({start % 16384})"
                    else:
                        zip_note = " zip:compresso"
                mark = "ok " if elf_ok else "NO "
                print(f"  {mark} {n}  LOAD={'/'.join(str(a) for a in sorted(set(aligns)))}  {info.file_size} B{zip_note}")


if __name__ == "__main__":
    main()
