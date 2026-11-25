package dev.antigravity.fluidengine.vault

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultFilesAndStoresTest {
  @get:Rule val tmp = TemporaryFolder()

  private val io = Executors.newSingleThreadExecutor { Thread(it, "vault-io") }.asCoroutineDispatcher()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val keystore = MemoryVaultKeystore()

  @After fun stop() {
    scope.cancel()
    io.close()
  }

  private fun vault(dir: File) = FluidVault.create(dir = dir, keystore = keystore, clock = { System.nanoTime() / 1_000_000 }, scope = scope, io = io)

  @Test fun `un file grande si scrive e si legge a pezzi, fuori dal thread di chi chiama`() = runBlocking {
    val dir = tmp.newFolder()
    val file = vault(dir).section().file("referto.pdf")
    val plain = Random(7).nextBytes(200_000) // tre pezzi da 64 KiB e un avanzo
    var writerThread = ""
    file.write { out ->
      writerThread = Thread.currentThread().name
      out.write(plain, 0, 100_000)
      out.write(plain, 100_000, 100_000)
    }
    assertTrue("scrive sul thread della cassaforte, non su quello di chi chiama: $writerThread", writerThread.startsWith("vault-io"))
    assertTrue(file.exists)
    assertEquals(31 + 200_000 + 4 * 16, file.file.length().toInt())
    assertArrayEquals(plain, vault(dir).section().file("referto.pdf").read())
    // A pezzi: si legge col flusso, senza tutto in memoria.
    val firstBytes = file.read { input -> ByteArray(10).also { input.read(it) } }
    assertArrayEquals(plain.copyOf(10), firstBytes)
  }

  @Test fun `un file vuoto e uno lungo esattamente un pezzo tornano uguali`() = runBlocking {
    val section = vault(tmp.newFolder()).section()
    section.file("vuoto").write(ByteArray(0))
    assertEquals(0, section.file("vuoto").read().size)
    val exact = Random(3).nextBytes(VaultFile.SegmentSize)
    section.file("esatto").write(exact)
    assertArrayEquals(exact, section.file("esatto").read())
  }

  @Test fun `un pezzo toccato, tolto, spostato o un file rinominato non si aprono`() {
    val key = VaultCrypto.randomBytes(32)
    val plain = Random(1).nextBytes(300)
    val stored = vaultFileBytesForTests(key, "nota", plain, segmentSize = 64)
    assertArrayEquals(plain, vaultFileReadForTests(key, "nota", stored))
    val header = VaultFile.HeaderBytes
    val segment = 64 + 16
    // Toccato.
    expectCorruptStream { vaultFileReadForTests(key, "nota", stored.copyOf().also { it[header + 5] = (it[header + 5].toInt() xor 1).toByte() }) }
    // L'ultimo pezzo tolto: il file finisce senza il segno «ultimo».
    expectCorruptStream { vaultFileReadForTests(key, "nota", stored.copyOf(stored.size - (300 - 4 * 64 + 16))) }
    // Due pezzi scambiati.
    val swapped = stored.copyOf()
    System.arraycopy(stored, header + segment, swapped, header, segment)
    System.arraycopy(stored, header, swapped, header + segment, segment)
    expectCorruptStream { vaultFileReadForTests(key, "nota", swapped) }
    // Un altro nome (rinominato a mano sul disco), o un'altra chiave.
    expectCorruptStream { vaultFileReadForTests(key, "altra", stored) }
    expectCorruptStream { vaultFileReadForTests(VaultCrypto.randomBytes(32), "nota", stored) }
  }

  @Test fun `un file della cassaforte non si legge in chiaro sul disco`() = runBlocking {
    val section = vault(tmp.newFolder()).section()
    section.file("diario").write("Caro diario, oggi".toByteArray())
    val raw = String(section.file("diario").file.readBytes(), Charsets.ISO_8859_1)
    assertFalse(raw.contains("Caro diario"))
  }

  @Test fun `le Preferences di DataStore si cifrano intere e si rileggono`() = runBlocking {
    val dir = tmp.newFolder()
    val section = vault(dir).section("impostazioni")
    val store = section.preferencesDataStore("conti")
    val iban = stringPreferencesKey("iban")
    store.edit { it[iban] = "IT60X0542811101000000123456" }
    assertEquals("IT60X0542811101000000123456", store.data.first()[iban])
    // Lo stesso file, lo stesso DataStore (DataStore ne vuole uno per file).
    assertTrue(store === section.preferencesDataStore("conti"))
    val onDisk = File(dir, "impostazioni/datastore/conti.preferences_pb.fv")
    assertTrue(onDisk.exists())
    assertFalse(String(onDisk.readBytes(), Charsets.ISO_8859_1).contains("IT60X"))
    assertFalse(String(onDisk.readBytes(), Charsets.ISO_8859_1).contains("iban"))
  }

  @Test fun `un DataStore tipizzato si cifra col Serializer dell'app, e un file toccato è una CorruptionException`() = runBlocking {
    val dir = tmp.newFolder()
    val section = vault(dir).section("diario")
    val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store = section.dataStore("pagine", TextSerializer, scope = storeScope)
    store.updateData { "Oggi ho visto il mare" }
    assertEquals("Oggi ho visto il mare", store.data.first())
    val file = File(dir, "diario/datastore/pagine.fv")
    assertFalse(String(file.readBytes(), Charsets.UTF_8).contains("mare"))
    // DataStore ne vuole uno per file: il primo «processo» finisce prima di riaprire.
    storeScope.coroutineContext[Job]!!.cancelAndJoin()

    // Un'altra cassaforte (un altro processo) sullo stesso file toccato.
    val bytes = file.readBytes()
    bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 1).toByte()
    file.writeBytes(bytes)
    val reopened = FluidVault.create(dir = dir, keystore = keystore, clock = { 0L }, scope = scope, io = io).section("diario")
      .dataStore("pagine", TextSerializer)
    try {
      reopened.data.first()
      fail("il file è toccato")
    } catch (e: CorruptionException) {
      // giusto
    }
  }

  @Test fun `un DataStore in una sezione all'impronta è un errore, perché terrebbe i dati in memoria`() {
    val section = vault(tmp.newFolder()).section("note", VaultAccess.Biometric())
    try {
      section.preferencesDataStore("note")
      fail("una sezione all'impronta non tiene un DataStore")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("values o file"))
    }
  }

  private fun expectCorruptStream(block: () -> Unit) {
    try {
      block()
      fail("il file doveva essere non valido")
    } catch (e: VaultCorruptStreamException) {
      // giusto
    }
  }

  private object TextSerializer : Serializer<String> {
    override val defaultValue: String = ""
    override suspend fun readFrom(input: InputStream): String = input.readBytes().toString(Charsets.UTF_8)
    override suspend fun writeTo(t: String, output: OutputStream) = output.write(t.toByteArray(Charsets.UTF_8))
  }
}
