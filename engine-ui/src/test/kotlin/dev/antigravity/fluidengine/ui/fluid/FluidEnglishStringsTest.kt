package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.ui.theme.lastSyncLabel
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le versioni inglesi delle parole dell'engine (3.0.4): nessun campo dell'inglese resta uguale
 * all'italiano, salvo quelli che sono la stessa parola in tutte e due le lingue (`Beta`).
 *
 * Il test legge i campi per riflessione, quindi un campo aggiunto domani senza la sua traduzione lo fa
 * cadere da solo; un campo funzione senza un campionatore qui sotto lo fa cadere lo stesso, perche'
 * altrimenti nessuno guarderebbe cosa dice.
 */
class FluidEnglishStringsTest {

  private fun fields(set: Any): Map<String, Any?> =
    set.javaClass.declaredFields
      .filter { !Modifier.isStatic(it.modifiers) }
      .associate { field ->
        field.isAccessible = true
        field.name to field.get(set)
      }

  @Suppress("UNCHECKED_CAST")
  private fun flatten(
    prefix: String,
    set: Any,
    samplers: Map<String, (Any) -> String>,
  ): Map<String, String> = buildMap {
    for ((name, value) in fields(set)) {
      when {
        value is String -> put("$prefix$name", value)
        value is FluidSyncStrings -> putAll(flatten("$prefix$name.", value, syncSamplers))
        value is Function<*> -> {
          val sampler = samplers[name] ?: error("$prefix$name e' una funzione senza campionatore nel test")
          put("$prefix$name", (sampler as (Any) -> String)(value))
        }
        else -> error("$prefix$name: tipo non previsto dal test (${value?.javaClass})")
      }
    }
  }

  private fun assertTranslated(label: String, italian: Map<String, String>, english: Map<String, String>, neutral: Set<String>) {
    assertEquals("$label: gli stessi campi nelle due lingue", italian.keys, english.keys)
    val same = italian.filter { (name, it) -> english.getValue(name) == it && name !in neutral }.keys
    assertTrue("$label: campi inglesi rimasti uguali all'italiano: $same", same.isEmpty())
    val gone = neutral.filter { it !in italian }
    assertTrue("$label: eccezioni che non esistono piu': $gone", gone.isEmpty())
    val blank = english.filterValues { it.isBlank() }.keys
    assertTrue("$label: campi inglesi vuoti: $blank", blank.isEmpty())
  }

  private val syncSamplers: Map<String, (Any) -> String> = mapOf(
    "notUpdated" to { f -> (f as (String) -> String)("Voti, Agenda") },
    "minutesAgo" to { f -> (f as (Int) -> String)(5) },
    "hoursAgo" to { f -> (f as (Int) -> String)(3) },
    "updatedOn" to { f -> (f as (String) -> String)("03/11 10:00") },
  )

  private val labelSamplers: Map<String, (Any) -> String> = mapOf(
    "sectionPosition" to { f -> (f as (String, Int, Int) -> String)("Marzo", 3, 12) },
  )

  private val updateSamplers: Map<String, (Any) -> String> = mapOf(
    "promptTitle" to { f -> (f as (String) -> String)("3.1") },
    "availableMessage" to { f ->
      val g = f as (String?) -> String
      g("12 MB") + "|" + g(null)
    },
    "downloading" to { f ->
      val g = f as (Int?, String?, String?) -> String
      g(50, "1 MB", "2 MB") + "|" + g(50, null, null) + "|" + g(null, null, null)
    },
    "available" to { f -> (f as (String) -> String)("3.1") },
    "installVersion" to { f -> (f as (String) -> String)("3.1") },
    "lastCheckedMinutes" to { f ->
      val g = f as (Int) -> String
      g(1) + "|" + g(7)
    },
    "lastCheckedHours" to { f ->
      val g = f as (Int) -> String
      g(1) + "|" + g(7)
    },
    "lastCheckedDays" to { f ->
      val g = f as (Int) -> String
      g(1) + "|" + g(7)
    },
  )

  @Test
  fun `FluidLabels in inglese non lascia campi italiani`() {
    assertTranslated(
      "FluidLabels",
      flatten("", FluidLabels(), labelSamplers),
      flatten("", FluidLabels.english(), labelSamplers),
      neutral = emptySet(),
    )
  }

  @Test
  fun `FluidUpdateStrings in inglese non lascia campi italiani`() {
    assertTranslated(
      "FluidUpdateStrings",
      flatten("", FluidUpdateStrings(), updateSamplers),
      flatten("", FluidUpdateStrings.english(), updateSamplers),
      neutral = setOf("channelBeta"),
    )
  }

  @Test
  fun `l'italiano resta il default`() {
    assertEquals("Chiudi", FluidLabels().close)
    assertEquals("Aggiorna", FluidUpdateStrings().install)
    assertEquals("Riprova", FluidLabels().sync.retry)
  }

  @Test
  fun `lastSyncLabel dice le frasi che gli si danno e in italiano di serie`() {
    val now = 1_700_000_000_000L
    fun status(agoMillis: Long?) = SyncStatus(
      state = SyncState.IDLE,
      lastSuccessfulSyncEpochMillis = agoMillis?.let { now - it },
    )
    val en = FluidSyncStrings.english()
    assertEquals("Mai aggiornato", status(null).lastSyncLabel(now))
    assertEquals("Never updated", status(null).lastSyncLabel(now, en))
    assertEquals("Aggiornato adesso", status(1_000).lastSyncLabel(now))
    assertEquals("Updated just now", status(1_000).lastSyncLabel(now, en))
    assertEquals("Aggiornato 5 min fa", status(5 * 60_000L).lastSyncLabel(now))
    assertEquals("Updated 5 min ago", status(5 * 60_000L).lastSyncLabel(now, en))
    assertEquals("Aggiornato 3 h fa", status(3 * 3_600_000L).lastSyncLabel(now))
    assertEquals("Updated 3 h ago", status(3 * 3_600_000L).lastSyncLabel(now, en))
    assertTrue(status(3 * 86_400_000L).lastSyncLabel(now).startsWith("Aggiornato il "))
    assertTrue(status(3 * 86_400_000L).lastSyncLabel(now, en).startsWith("Updated on "))
    assertEquals("Aggiornamento in corso", SyncStatus(state = SyncState.SYNCING).lastSyncLabel(now))
    assertEquals("Updating", SyncStatus(state = SyncState.SYNCING).lastSyncLabel(now, en))
  }
}
