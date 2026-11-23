package dev.antigravity.fluidengine.viewer.archive

import dev.antigravity.fluidengine.ui.fluid.FluidLicense

/** Add to `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` when the app includes the archive family. */
val FluidArchiveLicenses: List<FluidLicense> = buildList {
  add(FluidLicense("libarchive-android", "Google LLC e Zhang Hai", "Apache-2.0",
    "https://github.com/zhanghai/libarchive-android", "Collegamento Android per libarchive."))
  add(FluidLicense("libarchive", "Tim Kientzle e contributori", "BSD-2-Clause",
    "https://www.libarchive.org/", "Lettura di ZIP, 7z, RAR e TAR."))
  add(FluidLicense("zlib", "Jean-loup Gailly e Mark Adler", "Zlib",
    "https://zlib.net/", "Decodifica DEFLATE e gzip."))
  add(FluidLicense("bzip2", "Julian Seward e contributori", "bzip2-1.0.6",
    "https://sourceware.org/bzip2/", "Decodifica bzip2."))
  add(FluidLicense("XZ Utils (liblzma)", "Lasse Collin e contributori", "0BSD e pubblico dominio",
    "https://tukaani.org/xz/", "Decodifica XZ e LZMA."))
  add(FluidLicense("LZ4", "Yann Collet e contributori", "BSD-2-Clause",
    "https://github.com/lz4/lz4", "Codec incluso in libarchive."))
  add(FluidLicense("Zstandard", "Meta Platforms e contributori", "BSD-3-Clause",
    "https://github.com/facebook/zstd", "Decodifica Zstandard."))
  add(FluidLicense("Mbed TLS", "Arm e contributori", "Apache-2.0",
    "https://github.com/Mbed-TLS/mbedtls", "Supporto crittografico di libarchive."))
  if (SevenZipAes.available) {
    add(FluidLicense("Apache Commons Compress, IO, Codec e Lang", "Apache Software Foundation", "Apache-2.0",
      "https://commons.apache.org/", "Ripiego per i 7z cifrati e relative dipendenze."))
    add(FluidLicense("XZ for Java", "Tukaani Project", "0BSD",
      "https://tukaani.org/xz/java.html", "Decodifica LZMA nei 7z cifrati."))
  }
}
