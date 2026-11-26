package dev.antigravity.fluidengine.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * I dati sensibili nell'indice (F8.22): con la cassaforte di `engine-vault` il file dell'indice non contiene il testo
 * conservato, ne' le parole dei campi riservati, ne' le domande recenti; l'app li trova lo stesso, le altre app no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidSearchCipherTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private val schema = FluidSearchSchema(
    name = "note-cifrate",
    version = 1,
    fields = listOf(
      FluidSearchField("title", weight = 3f),
      FluidSearchField("body"),
      FluidSearchField("secret", sensitive = true),
    ),
  )

  private fun vaultCipher(dir: File, keystore: MemoryVaultKeystore = MemoryVaultKeystore()): FluidSearchCipher =
    FluidSearchCipher.vault(FluidVault.create(dir, keystore).section())

  private fun bytesOf(name: String): String {
    val f = FluidSearchIndex.fileOf(context, name)
    val all = listOf(f, File(f.path + "-wal")).filter { it.exists() }.joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }
    return all
  }

  @Test
  fun ilFileNonDiceNienteDelTestoEDelleParoleRiservate() = runBlocking {
    val keystore = MemoryVaultKeystore()
    val dir = File(context.cacheDir, "vault-a").apply { deleteRecursively() }
    val index = FluidSearchIndex.open(context, schema, vaultCipher(dir, keystore))
    index.put(
      FluidSearchDocument(
        "wifi",
        mapOf("title" to "Rete di casa", "body" to "Il router sta in corridoio, vicino a Matera.", "secret" to "girasole autunnale quarantadue"),
        kind = "nota",
      ),
    )
    index.addRecent("girasole")
    // Dentro l'app si trova, anche per inizio di parola (da tre lettere), e il pezzo riservato si vede.
    assertEquals(listOf("wifi"), index.search("girasole ").hits.map { it.id })
    assertEquals(listOf("wifi"), index.search("autunn").hits.map { it.id })
    assertEquals("girasole autunnale quarantadue", index.search("girasole ").hits.single().fields["secret"])
    // Per un'altra app il campo riservato non trova e non si vede.
    val outside = index.search(FluidSearchQuery("girasole ", includeSensitive = false))
    assertTrue(outside.hits.isEmpty())
    val other = index.search(FluidSearchQuery("router ", includeSensitive = false)).hits.single()
    assertFalse("secret" in other.fields)
    index.close()
    val raw = bytesOf("note-cifrate")
    assertFalse("la parola riservata e' nel file", raw.contains("girasol"))
    assertFalse("la parola riservata e' nel file", raw.contains("autunnal"))
    assertFalse("il testo conservato e' nel file", raw.contains("corridoio, vicino"))
    assertFalse("le recenti sono nel file", raw.contains("\ngirasole") || raw.endsWith("girasole"))
    // Le radici dei campi non riservati si', per scelta (l'indice delle parole e' fatto di loro).
    assertTrue(raw.contains("router"))
    // Riaperto con la stessa cassaforte, tutto torna.
    val again = FluidSearchIndex.open(context, schema, vaultCipher(dir, keystore))
    assertFalse(again.rebuilt)
    assertEquals(listOf("wifi"), again.search("girasole ").hits.map { it.id })
    assertEquals(listOf("girasole"), again.recents())
    again.destroy()
  }

  @Test
  fun unaChiavePersaRifaLIndiceVuoto() = runBlocking {
    val dir = File(context.cacheDir, "vault-b").apply { deleteRecursively() }
    val first = FluidSearchIndex.open(context, schema, vaultCipher(dir), name = "persa")
    first.put(FluidSearchDocument("a", mapOf("title" to "Uno", "secret" to "segreto"), kind = "nota"))
    first.close()
    // Un'altra cassaforte (un telefono nuovo, il Keystore azzerato): la chiave dell'indice non si apre piu'.
    val dir2 = File(context.cacheDir, "vault-c").apply { deleteRecursively() }
    val second = FluidSearchIndex.open(context, schema, vaultCipher(dir2), name = "persa")
    assertTrue(second.rebuilt)
    assertEquals(0, second.count())
    second.destroy()
  }

  @Test
  fun unCampoRiservatoVuoleLaCassaforte() = runBlocking {
    val failed = runCatching { FluidSearchIndex.inMemory(schema) }
    assertTrue(failed.exceptionOrNull() is IllegalArgumentException)
  }
}
