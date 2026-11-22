"""Generate original F6C.12 fixtures. Optional packages: Pillow, pyzipper, py7zr and zstandard."""

from io import BytesIO
from pathlib import Path
import gzip
import hashlib
import lzma
import stat
import struct
import tarfile
import zipfile
import zlib

from PIL import Image, ImageDraw
from Cryptodome.Cipher import AES
import py7zr
import pyzipper
import zstandard


FIXTURES = Path(__file__).resolve().parents[1] / "src" / "androidTest" / "assets"
PASSWORD = "archivio-prova"
PAYLOAD = b"Nota di prova F6C.12\n"


def pdf():
    stream = b"BT /F1 28 Tf 60 700 Td (Archivio - foglio di prova) Tj ET"
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length %d >>\nstream\n%s\nendstream" % (len(stream), stream),
    ]
    data = b"%PDF-1.4\n"
    offsets = []
    for i, obj in enumerate(objects, 1):
        offsets.append(len(data))
        data += b"%d 0 obj\n%s\nendobj\n" % (i, obj)
    return (data + b"xref\n0 6\n0000000000 65535 f \n" + b"".join(b"%010d 00000 n \n" % o for o in offsets)
            + b"trailer\n<< /Root 1 0 R /Size 6 >>\nstartxref\n%d\n%%%%EOF\n" % len(data))


def png():
    image = Image.new("RGB", (560, 360), "#e8f4ee")
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 230, 560, 360), fill="#126b66")
    draw.ellipse((325, 45, 405, 125), fill="#f1bd63")
    draw.polygon(((20, 244), (188, 90), (300, 242)), fill="#4c8f85")
    draw.polygon(((165, 257), (348, 145), (510, 260)), fill="#268077")
    data = BytesIO()
    image.save(data, format="PNG")
    return data.getvalue()


def docx():
    data = BytesIO()
    with zipfile.ZipFile(data, "w") as z:
        z.writestr("[Content_Types].xml", '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                   '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
                   '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        z.writestr("_rels/.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        z.writestr("word/document.xml", '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
                   '<w:body><w:p><w:r><w:t>Relazione di prova</w:t></w:r></w:p><w:sectPr/></w:body></w:document>')
    return data.getvalue()


def original_zip():
    with zipfile.ZipFile(FIXTURES / "progetto.zip", "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("README.md", b"# Progetto\nQuesta raccolta contiene le prove del visore archivio.\n")
        z.writestr("app/config.txt", b"build=42\n")
        z.writestr("docs/relazione.pdf", pdf())
        z.writestr("docs/relazione.docx", docx())
        z.writestr("docs/2026/appunti.txt", PAYLOAD)
        z.writestr("immagini/paesaggio.png", png())
        z.writestr("logo.png", png())
        z.writestr("relazione.pdf", pdf())
        z.writestr("chiavi.txt", PAYLOAD)
        z.writestr("pubblico/", b"")


def scrolling_zip():
    with zipfile.ZipFile(FIXTURES / "scorrimento.zip", "w", compression=zipfile.ZIP_DEFLATED) as z:
        for i in range(1, 33):
            z.writestr(f"documenti/appunto-{i:02}.txt", PAYLOAD)
        z.writestr("documenti/relazione-finale.pdf", pdf())


def tar_payload():
    data = BytesIO()
    with tarfile.open(fileobj=data, mode="w", format=tarfile.USTAR_FORMAT) as tar:
        for path in ("cartella/nota.txt", "seconda.txt"):
            item = tarfile.TarInfo(path)
            item.size = len(PAYLOAD)
            tar.addfile(item, BytesIO(PAYLOAD))
        link = tarfile.TarInfo("collegamento")
        link.type = tarfile.SYMTYPE
        link.linkname = "cartella/nota.txt"
        tar.addfile(link)
        link = tarfile.TarInfo("hardlink")
        link.type = tarfile.LNKTYPE
        link.linkname = "cartella/nota.txt"
        tar.addfile(link)
    return data.getvalue()


def legacy_zip(filename, raw_name):
    data = BytesIO()
    marker = b"Q" * len(raw_name)
    with zipfile.ZipFile(data, "w") as z:
        z.writestr(marker.decode(), PAYLOAD)
    (FIXTURES / filename).write_bytes(data.getvalue().replace(marker, raw_name))


def vint(number):
    result = bytearray()
    while number >= 128:
        result.append(128 | (number & 127))
        number >>= 7
    result.append(number)
    return bytes(result)


def crc(data):
    return struct.pack("<I", zlib.crc32(data))


def rar4_header(kind, flags, body=b""):
    header = struct.pack("<BHH", kind, flags, 7 + len(body)) + body
    return crc(header)[:2] + header


def rar5_header(kind, flags, body=b""):
    header = vint(kind) + vint(flags) + body
    header = vint(len(header)) + header
    return crc(header) + header


def rar_fixtures():
    name = b"nota.txt"
    signature4 = b"Rar!\x1a\x07\x00"
    file_header = (struct.pack("<IIB", len(PAYLOAD), len(PAYLOAD), 3) + crc(PAYLOAD)
                   + struct.pack("<IBBHI", 0x5d610000, 20, 0x30, len(name), 0o100644) + name)
    (FIXTURES / "rar4.rar").write_bytes(signature4 + rar4_header(0x73, 0, b"\0" * 6)
                                       + rar4_header(0x74, 0x8000, file_header) + PAYLOAD + rar4_header(0x7b, 0))
    signature5 = b"Rar!\x1a\x07\x01\x00"
    body = vint(len(PAYLOAD)) + vint(4) + vint(len(PAYLOAD)) + vint(0o100644) + crc(PAYLOAD)
    body += vint(0) + vint(1) + vint(len(name)) + name
    (FIXTURES / "rar5.rar").write_bytes(signature5 + rar5_header(1, 0, vint(0)) + rar5_header(2, 2, body)
                                       + PAYLOAD + rar5_header(5, 0, vint(0)))
    broken = rar5_header(2, 2, body)
    broken = bytes([broken[0] ^ 1]) + broken[1:]
    (FIXTURES / "rar5-malformato.rar").write_bytes(signature5 + rar5_header(1, 0, vint(0)) + broken + PAYLOAD)
    salt = bytes(range(16))
    key = hashlib.pbkdf2_hmac("sha256", PASSWORD.encode(), salt, 1 << 15, 32)

    def encrypted_header(header, number):
        iv = number.to_bytes(16, "little")
        return iv + AES.new(key, AES.MODE_CBC, iv).encrypt(header + bytes(-len(header) % 16))

    protection = rar5_header(4, 0, bytes([0, 0, 15]) + salt)
    (FIXTURES / "rar5-protetto.rar").write_bytes(signature5 + protection
        + encrypted_header(rar5_header(1, 0, vint(0)), 1)
        + encrypted_header(rar5_header(2, 2, body), 2) + PAYLOAD
        + encrypted_header(rar5_header(5, 0, vint(0)), 3))


def main():
    FIXTURES.mkdir(parents=True, exist_ok=True)
    original_zip()
    scrolling_zip()
    tar = tar_payload()
    (FIXTURES / "cartelle.tar").write_bytes(tar)
    (FIXTURES / "cartelle.tgz").write_bytes(gzip.compress(tar, mtime=0))
    (FIXTURES / "cartelle.tar.xz").write_bytes(lzma.compress(tar))
    (FIXTURES / "cartelle.tar.zst").write_bytes(zstandard.ZstdCompressor().compress(tar))
    # APK asset packaging treats a final .gz as packaging syntax. Preserve the compressed bytes with a suffix.
    (FIXTURES / "nota.txt.gz.fixture").write_bytes(gzip.compress(PAYLOAD, mtime=0))
    # Preserve a complete first TAR member followed by part of the second file's data.
    (FIXTURES / "danneggiato.tar").write_bytes(tar[:512 * 3 + 2])
    complete_zip = (FIXTURES / "progetto.zip").read_bytes()
    (FIXTURES / "danneggiato.zip").write_bytes(complete_zip[:complete_zip.index(b"PK\x01\x02") - 12])
    with zipfile.ZipFile(FIXTURES / "percorsi.zip", "w") as z:
        for name in ("../fuori.txt", "C:/fuori.txt", "cartella/../../fuori.txt", "sicuro/nota.txt"):
            z.writestr(name, PAYLOAD)
        link = zipfile.ZipInfo("sicuro/link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        z.writestr(link, "../fuori.txt")
    with zipfile.ZipFile(FIXTURES / "rapporto.zip", "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("nota.txt", PAYLOAD)
        z.writestr("ripetizioni.txt", b"a" * (1024 * 1024 + 8192))
    legacy_zip("cp437.zip", "caffè.txt".encode("cp437"))
    legacy_zip("shift-jis.zip", "日本語.txt".encode("shift_jis"))
    with pyzipper.AESZipFile(FIXTURES / "zip-aes.zip", "w", compression=pyzipper.ZIP_DEFLATED,
                            encryption=pyzipper.WZ_AES) as z:
        z.setpassword(PASSWORD.encode())
        z.writestr("nota.txt", PAYLOAD)
    for label, password, hide_headers in (("cartelle.7z", None, False), ("7z-aes.7z", PASSWORD, False),
                                          ("7z-intestazioni.7z", PASSWORD, True)):
        with py7zr.SevenZipFile(FIXTURES / label, "w", password=password, header_encryption=hide_headers) as z:
            z.writestr(PAYLOAD, "cartella/nota.txt")
    (FIXTURES / "7z-troncato.7z").write_bytes((FIXTURES / "cartelle.7z").read_bytes()[:24])
    rar_fixtures()


if __name__ == "__main__":
    main()
