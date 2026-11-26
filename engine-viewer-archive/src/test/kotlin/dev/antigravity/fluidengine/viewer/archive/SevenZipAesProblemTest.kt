package dev.antigravity.fluidengine.viewer.archive

import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * R2 · Il ripiego dei 7z cifrati (Commons Compress) distingue un archivio rotto da una password sbagliata. Commons
 * Compress non dice mai «password»: con una password sbagliata dà «Compressed data is corrupt» o «no Header», con un
 * file tronco «nextHeaderOffset is out of bounds». Un archivio che non si legge nemmeno senza password è rotto; il resto
 * resta «password sbagliata», come chiede 7-Zip («Wrong password?»), perché i due casi non si separano.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SevenZipAesProblemTest {
  private val password = "archivio-prova".toCharArray()
  private val assets = File("src/androidTest/assets")
  private lateinit var dir: File

  @Before fun setUp() {
    assumeTrue(SevenZipAes.available)
    dir = File(RuntimeEnvironment.getApplication().cacheDir, "prove-7z").apply { mkdirs() }
  }

  @After fun tearDown() {
    if (::dir.isInitialized) dir.deleteRecursively()
  }

  @Test fun a_broken_encrypted_7z_is_broken_and_a_wrong_password_stays_wrong() = runBlocking {
    val entries = File(assets, "7z-aes.7z").readBytes()
    val headers = File(assets, "7z-intestazioni.7z").readBytes()
    val index = ArchiveIndex("7z", null, null, emptyList(), ArchiveProtection.SevenZipHeaders, null)

    // Integri: la password sbagliata resta sbagliata, quella giusta apre.
    problem(ArchiveProblem.WrongPassword) { SevenZipAes.unlock(source("intestazioni.7z", headers), index, "sbagliata".toCharArray()) }
    val files = SevenZipAes.unlock(source("voci.7z", entries), index, password).files
    problem(ArchiveProblem.WrongPassword) { extract(source("voci.7z", entries), files, "sbagliata".toCharArray()) }
    problem(null) { extract(source("voci.7z", entries), files, password) }

    // Rotti: tagliato a metà, e con un byte cambiato nell'intestazione in coda (il suo CRC non torna).
    for ((name, bytes) in listOf("voci" to entries, "intestazioni" to headers)) {
      val half = bytes.copyOf(bytes.size / 2)
      val tail = bytes.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 0x55).toByte() }
      for ((label, broken) in listOf("tagliato" to half, "coda" to tail)) {
        val source = source("$name-$label.7z", broken)
        if (name == "voci") problem(ArchiveProblem.Broken, "$name $label") { extract(source, files, password) }
        else problem(ArchiveProblem.Broken, "$name $label") { SevenZipAes.unlock(source, index, password) }
      }
    }
  }

  private suspend fun extract(source: FluidViewerSource, files: List<ArchiveItem>, key: CharArray) {
    SevenZipAes.extract(source, files, key.copyOf()) { _, write -> write(ByteArrayOutputStream()) }
  }

  private fun source(name: String, bytes: ByteArray): FluidViewerSource =
    FluidViewerSource.of(File(dir, name).apply { writeBytes(bytes) })

  private suspend fun problem(expected: ArchiveProblem?, label: String = "", block: suspend () -> Unit) {
    val failure = try {
      block()
      null
    } catch (e: ArchiveReadFailure) {
      e
    }
    assertEquals(label, expected, failure?.problem)
  }
}
