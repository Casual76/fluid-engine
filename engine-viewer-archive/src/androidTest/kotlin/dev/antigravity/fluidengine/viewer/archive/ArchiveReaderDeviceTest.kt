package dev.antigravity.fluidengine.viewer.archive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArchiveReaderDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val password = "archivio-prova"
  private val payload = "Nota di prova F6C.12\n"

  @Test fun scans_and_extracts_with_the_native_library() = runBlocking(Dispatchers.IO) {
    val formats = listOf(
      Triple("progetto.zip", ArchiveFormat.Zip, "ZIP"), Triple("cartelle.7z", ArchiveFormat.SevenZip, "7z"),
      Triple("rar4.rar", ArchiveFormat.Rar, "RAR 4"), Triple("rar5.rar", ArchiveFormat.Rar, "RAR 5"),
      Triple("cartelle.tar", ArchiveFormat.Tar, "TAR"), Triple("cartelle.tgz", ArchiveFormat.Compressed, "TAR"),
      Triple("cartelle.tar.xz", ArchiveFormat.Compressed, "TAR"), Triple("cartelle.tar.zst", ArchiveFormat.Compressed, "TAR"),
      Triple("nota.txt.gz", ArchiveFormat.Compressed, "gzip"),
    )
    for ((name, hint, format) in formats) fixture(name, hint) { reader, _ ->
      val index = reader.index(ArchiveEncoding.Cp437)
      assertEquals(name, null, index.problem)
      assertEquals(name, format, index.format)
      val expected = index.files.first { it.name in setOf("nota.txt", "appunti.txt") }
      assertEquals(name, payload, extract(reader, expected, ArchiveEncoding.Cp437))
    }
  }

  @Test fun corrupt_archives_keep_the_readable_prefix() = runBlocking(Dispatchers.IO) {
    for ((name, hint) in listOf("danneggiato.tar" to ArchiveFormat.Tar, "danneggiato.zip" to ArchiveFormat.Zip)) {
      fixture(name, hint) { reader, _ ->
        val index = reader.index(ArchiveEncoding.Cp437)
        assertEquals(ArchiveProblem.Broken, index.problem)
        assertTrue(name, index.items.isNotEmpty())
      }
    }
  }

  @Test fun truncated_7z_and_bad_rar5_header_are_reported_as_incomplete() = runBlocking(Dispatchers.IO) {
    for ((name, hint) in listOf("7z-troncato.7z" to ArchiveFormat.SevenZip, "rar5-malformato.rar" to ArchiveFormat.Rar)) {
      fixture(name, hint) { reader, _ ->
        val index = reader.index(ArchiveEncoding.Cp437)
        assertEquals(name, ArchiveProblem.Broken, index.problem)
        assertTrue(name, index.tree.extractable.isEmpty())
      }
    }
  }

  @Test fun rejects_zip_slip_and_expansion_but_displays_links_and_decodes_legacy_names() = runBlocking(Dispatchers.IO) {
    fixture("percorsi.zip", ArchiveFormat.Zip) { reader, _ ->
      val index = reader.index(ArchiveEncoding.Cp437)
      assertEquals(3, index.hiddenPaths)
      assertEquals(ArchiveKind.Link, index.items.first { it.name == "link" }.kind)
      assertEquals(listOf("sicuro/nota.txt"), index.tree.extractable.map { it.path })
    }
    fixture("cartelle.tar", ArchiveFormat.Tar) { reader, _ ->
      val index = reader.index(ArchiveEncoding.Cp437)
      assertEquals(2, index.items.count { it.kind == ArchiveKind.Link })
    }
    fixture("rapporto.zip", ArchiveFormat.Zip) { reader, _ ->
      val index = reader.index(ArchiveEncoding.Cp437)
      assertEquals(ArchiveProblem.Limit, index.problem)
      problem(ArchiveProblem.Limit) { extract(reader, index.files.last(), ArchiveEncoding.Cp437) }
    }
    fixture("cp437.zip", ArchiveFormat.Zip) { reader, _ ->
      assertEquals("caffè.txt", reader.index(ArchiveEncoding.Cp437).files.single().name)
    }
    fixture("shift-jis.zip", ArchiveFormat.Zip) { reader, _ ->
      val index = reader.index(ArchiveEncoding.ShiftJis)
      assertEquals("日本語.txt", index.files.single().name)
      assertEquals(payload, extract(reader, index.files.single(), ArchiveEncoding.ShiftJis))
    }
  }

  @Test fun zip_aes_and_rar_protection_are_identified() = runBlocking(Dispatchers.IO) {
    fixture("zip-aes.zip", ArchiveFormat.Zip) { reader, _ ->
      val index = reader.index(ArchiveEncoding.Cp437)
      assertEquals(ArchiveProtection.Entries, index.protection)
      val entry = index.files.single()
      problem(ArchiveProblem.Locked) { extract(reader, entry, ArchiveEncoding.Cp437) }
      problem(ArchiveProblem.WrongPassword) { extract(reader, entry, ArchiveEncoding.Cp437, "sbagliata") }
      assertEquals(payload, extract(reader, entry, ArchiveEncoding.Cp437, password))
    }
    fixture("rar5-protetto.rar", ArchiveFormat.Rar) { reader, _ ->
      assertEquals(ArchiveProtection.Rar, reader.index(ArchiveEncoding.Cp437).protection)
    }
  }

  @Test fun only_encrypted_7z_uses_the_aes_reader() = runBlocking(Dispatchers.IO) {
    for (fixture in listOf("7z-aes.7z", "7z-intestazioni.7z")) fixture(fixture, ArchiveFormat.SevenZip) { reader, source ->
      val index = reader.index(ArchiveEncoding.Cp437)
      assertTrue(fixture, index.sevenZipFallback)
      val decoded = if (fixture == "7z-intestazioni.7z") {
        assertEquals(ArchiveProtection.SevenZipHeaders, index.protection)
        problem(ArchiveProblem.WrongPassword) { SevenZipAes.unlock(source, index, "sbagliata".toCharArray()) }
        SevenZipAes.unlock(source, index, password.toCharArray())
      } else index
      val files = decoded.files
      val result = ByteArrayOutputStream()
      problem(ArchiveProblem.WrongPassword) {
        SevenZipAes.extract(source, files, "sbagliata".toCharArray()) { _, write -> write(result) }
      }
      result.reset()
      SevenZipAes.extract(source, files, password.toCharArray()) { _, write -> write(result) }
      assertEquals(payload, result.toString(Charsets.UTF_8.name()))
    }
  }

  @Test fun registry_opens_nested_pdf_image_and_office_fallback() = runBlocking(Dispatchers.IO) {
    val context = instrumentation.targetContext
    fixture("progetto.zip", ArchiveFormat.Zip) { reader, source ->
      FluidViewerRegistry.open(context, source).use { archive ->
        assertTrue(archive.content is FluidViewerContent.Custom)
        assertNotNull(archive.details.find { it.label == "Dimensione estratta" })
        val index = reader.index(ArchiveEncoding.Cp437)
        for (name in listOf("docs/relazione.pdf", "immagini/paesaggio.png", "docs/relazione.docx")) {
          val item = index.files.first { it.path == name }
          val cache = File.createTempFile("item-", ".asset", context.cacheDir)
          try {
            reader.extract(listOf(item), ArchiveEncoding.Cp437, null) { _, write -> cache.outputStream().use { write(it) } }
            FluidViewerRegistry.open(context, FluidViewerSource.of(cache, item.name, deleteOnClose = true), openWith = {}).use { nested ->
              when (item.name.substringAfterLast('.')) {
                "pdf" -> assertTrue(nested.content is FluidViewerContent.Pages)
                "png" -> assertTrue(nested.content is FluidViewerContent.Image)
                "docx" -> assertNotNull((nested.content as FluidViewerContent.Unknown).openWith)
              }
            }
            assertTrue(!cache.exists())
          } finally { cache.delete() }
        }
      }
    }
  }

  private suspend fun fixture(name: String, format: ArchiveFormat, action: suspend (NativeArchive, FluidViewerSource) -> Unit) {
    val file = File.createTempFile("archive-", ".fixture", instrumentation.targetContext.cacheDir)
    try {
      val asset = if (name.endsWith(".gz")) "$name.fixture" else name
      instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
      FluidViewerSource.of(file, name).use { source -> action(NativeArchive(source, format), source) }
    } finally { file.delete() }
  }

  private suspend fun extract(reader: NativeArchive, item: ArchiveItem, encoding: ArchiveEncoding, key: String? = null): String {
    val sink = ByteArrayOutputStream()
    reader.extract(listOf(item), encoding, key?.toCharArray()) { _, write -> write(sink) }
    return sink.toString(Charsets.UTF_8.name())
  }

  private suspend fun problem(expected: ArchiveProblem, action: suspend () -> Unit) {
    try {
      action()
      fail("Atteso: $expected")
    } catch (e: ArchiveReadFailure) {
      assertEquals(expected, e.problem)
    }
  }
}
