package dev.antigravity.fluidengine.ai.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F9.3 · La memoria di prima di PampAI passa senza perdere niente: un database finto con lo schema di oggi (Room v7,
 * `PampaiDatabase/7.json`: la tabella `memories` com'e', `room_master_table` con l'identita' dello schema,
 * `user_version` 7), letto in sola lettura e portato nella memoria nuova. Si puo' rifare senza doppioni.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiMemoryLegacyImportTest {
  @get:Rule val tmp = TemporaryFolder()

  private val long = "Preferisce che le risposte sui compiti di matematica partano dal metodo e non dal risultato, " +
    "con un esempio svolto passo per passo, perché la professoressa Bianchi chiede sempre di mostrare il procedimento " +
    "completo; se l'esercizio è di geometria vuole anche il disegno a parole."

  @Test fun `il ricordo lungo della prova e' lungo come i piu' lunghi di oggi`() {
    assertTrue(long.length in 270..300)
  }

  /** Il database di PampAI alla versione 7, con le righe di [rows] (testo, ora, conversazione, fissato). */
  private fun pampaiV7(rows: List<Triple<String, Long?, Boolean>>, schema: String = MemoriesV7): File {
    val file = File(tmp.root, "pampai.db")
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
      db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bc8b4520aa70d6dde0bd58e521cbff87')")
      db.execSQL(Conversations)
      db.execSQL(schema)
      rows.forEachIndexed { i, (text, conversation, pinned) ->
        db.insert("memories", null, ContentValues().apply {
          put("text", text)
          put("createdAtMillis", MemoryTestTime.Now - (rows.size - i) * MemoryTestTime.Day)
          if (conversation != null) put("sourceConversationId", conversation) else putNull("sourceConversationId")
          if ("pinned" in schema) put("pinned", if (pinned) 1 else 0)
        })
      }
      db.version = 7
    }
    return file
  }

  private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

  @Test fun `la memoria di Room v7 passa intera, e il file di prima non cambia`() = runBlocking {
    val file = pampaiV7(
      listOf(
        Triple("Sono allergico alle noci", 3L, true),
        Triple("La mia fermata è Santa Maria Novella", null, false),
        Triple(long, 12L, false),
        Triple("la mia fermata è santa maria novella", 15L, false),
        Triple("   ", null, false),
        Triple("il codice del portone è 4521 9087 3321", null, false),
        Triple("è in 4F", 20L, false),
        Triple("è in 5F", 31L, false),
      ),
    )
    val before = sha(file)
    val rows = AiMemoryLegacyImport.readPampaiRoom(file)
    assertEquals(8, rows.size)
    assertEquals(before, sha(file))

    val book = AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true), TestClock())
    val first = book.importLegacy(rows)
    assertEquals(AiMemoryImport(imported = 6, alreadyImported = 0, duplicates = 1, empty = 1, secretLike = 1), first)
    val byText = book.memories.value.associateBy { it.text }
    // Il testo intero (300 caratteri, il tetto di oggi), la data, la conversazione, la fissatura.
    assertEquals(long, byText.getValue(long).text)
    assertEquals(rows[2].createdAtMillis, byText.getValue(long).createdAtMillis)
    assertEquals(AiMemorySource.imported(12L), byText.getValue(long).source)
    assertTrue(byText.getValue("Sono allergico alle noci").pinned)
    // Due ricordi di prima che si contraddicono restano tutti e due (non hanno una chiave): la pagina li mostra insieme.
    assertTrue("è in 4F" in byText && "è in 5F" in byText)
    // Il ricordo che sembra un codice entra, ma non va mai nel prompt.
    assertTrue(book.prompt("qual è il codice del portone?").memories.none { it.text.contains("4521") })

    // Rifatto (PampAI riparte a meta' del passaggio): nessun doppione.
    val second = book.importLegacy(AiMemoryLegacyImport.readPampaiRoom(file))
    assertEquals(AiMemoryImport(imported = 0, alreadyImported = 8, duplicates = 0, empty = 0, secretLike = 0), second)
    assertEquals(6, book.memories.value.size)
    assertEquals(before, sha(file))
  }

  @Test fun `una tabella che non e' quella della versione 7 non si indovina, una che manca non c'e'`() {
    val wrong = pampaiV7(listOf(Triple("x", null, false)),
      schema = "CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, " +
        "`createdAtMillis` INTEGER NOT NULL, `sourceConversationId` INTEGER)")
    val error = runCatching { AiMemoryLegacyImport.readPampaiRoom(wrong) }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
    assertTrue(error!!.message!!.contains("pinned"))

    val empty = File(tmp.root, "vuoto.db")
    SQLiteDatabase.openOrCreateDatabase(empty, null).use { it.execSQL(Conversations) }
    assertTrue(AiMemoryLegacyImport.readPampaiRoom(empty).isEmpty())
  }

  private companion object {
    /** Dallo schema esportato di PampAI (`7.json`, `createSql` di `memories`). */
    const val MemoriesV7 = "CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, " +
      "`createdAtMillis` INTEGER NOT NULL, `sourceConversationId` INTEGER, `pinned` INTEGER NOT NULL)"

    const val Conversations = "CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
      "`createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, `lastProvider` TEXT, `source` TEXT NOT NULL, " +
      "`pinned` INTEGER NOT NULL, `loadedGroupsJson` TEXT, `autoTitled` INTEGER NOT NULL, `plugin` TEXT, `temporary` INTEGER NOT NULL, " +
      "`activeLeafId` INTEGER)"
  }
}
