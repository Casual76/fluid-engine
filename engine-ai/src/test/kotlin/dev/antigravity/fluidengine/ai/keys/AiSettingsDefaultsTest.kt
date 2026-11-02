package dev.antigravity.fluidengine.ai.keys

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.antigravity.fluidengine.ai.provider.OpenRouterDataPolicy
import dev.antigravity.fluidengine.ai.provider.ProviderId
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il Keystore non c'e' sul computer: qui la cifratura e' un involucro trasparente. */
private class TransparentCipher : SecretCipher {
  override fun encrypt(plain: String): String = "plain:$plain"
  override fun decrypt(blob: String): String? = blob.removePrefix("plain:").takeIf { blob.startsWith("plain:") }
}

/**
 * Le impostazioni dell'assistente con i valori di partenza dell'app, "mai scelto" distinto da
 * "spento", e il file corrotto che riparte (3.0.0): quello che Fluid-transit e FluidWeather
 * ottenevano riscrivendo il file con una migrazione e aprendolo da se'.
 */
class AiSettingsDefaultsTest {

  private fun directory(): File = Files.createTempDirectory("ai-settings").toFile()

  private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private fun store(defaults: AiSettingsDefaults = AiSettingsDefaults(), file: File = File(directory(), "ai.preferences_pb")): AiSettingsStore =
    AiSettingsStore.open(file, defaults, scope())

  // ------------------------------------------------------------------------- i valori di partenza

  @Test
  fun `i default di serie sono quelli di sempre`() = runBlocking {
    val settings = store().current()
    assertFalse(settings.enabled)
    assertFalse(settings.actionsEnabled)
    assertFalse(settings.speakReplies)
    assertEquals(ThinkingLevel.MEDIUM, settings.thinking)
    assertEquals(OpenRouterDataPolicy.ACCOUNT, settings.openRouterDataPolicy)
    assertEquals(ProviderId.defaultOrder, settings.chatOrder)
    assertEquals(ProviderId.defaultOrder, settings.sttOrder)
    assertFalse(settings.enabledChosen)
  }

  @Test
  fun `un valore assente vale il default dell'app`() = runBlocking {
    // Le due scelte che Fluid-transit scriveva con una migrazione: azioni accese, OpenRouter in `deny`.
    val store = store(AiSettingsDefaults(actionsEnabled = true, openRouterDataPolicy = OpenRouterDataPolicy.DENY, speakReplies = true, thinking = ThinkingLevel.LOW))
    val settings = store.current()
    assertTrue(settings.actionsEnabled)
    assertEquals(OpenRouterDataPolicy.DENY, settings.openRouterDataPolicy)
    assertTrue(settings.speakReplies)
    assertEquals(ThinkingLevel.LOW, settings.thinking)
    assertFalse("l'interruttore non e' fra i default accesi", settings.enabled)
  }

  @Test
  fun `una scelta dell'utente batte il default, anche quando e' il valore di serie`() = runBlocking {
    val store = store(AiSettingsDefaults(actionsEnabled = true, openRouterDataPolicy = OpenRouterDataPolicy.DENY, speakReplies = true, thinking = ThinkingLevel.LOW))
    store.setActionsEnabled(false)
    store.setOpenRouterDataPolicy(OpenRouterDataPolicy.ACCOUNT)
    store.setSpeakReplies(false)
    store.setThinking(ThinkingLevel.MEDIUM)
    val settings = store.current()
    assertFalse("l'ha spento lui: resta spento", settings.actionsEnabled)
    assertEquals(OpenRouterDataPolicy.ACCOUNT, settings.openRouterDataPolicy)
    assertFalse(settings.speakReplies)
    assertEquals(ThinkingLevel.MEDIUM, settings.thinking)
  }

  @Test
  fun `l'interruttore puo' partire acceso, e spegnerlo e' una scelta`() = runBlocking {
    val store = store(AiSettingsDefaults(enabled = true))
    assertTrue(store.current().enabled)
    assertFalse(store.current().enabledChosen)
    store.setEnabled(false)
    assertFalse(store.current().enabled)
    assertTrue(store.current().enabledChosen)
  }

  @Test
  fun `gli ordini di partenza dell'app valgono finche' non li cambia l'utente, e nominano sempre tutti i provider`() = runBlocking {
    val store = store(AiSettingsDefaults(chatOrder = listOf(ProviderId.GEMINI), sttOrder = listOf(ProviderId.OPENROUTER, ProviderId.OPENROUTER, ProviderId.GROQ)))
    val settings = store.current()
    assertEquals(listOf(ProviderId.GEMINI, ProviderId.GROQ, ProviderId.OPENROUTER), settings.chatOrder)
    assertEquals(listOf(ProviderId.OPENROUTER, ProviderId.GROQ, ProviderId.GEMINI), settings.sttOrder)
    store.setChatOrder(listOf(ProviderId.OPENROUTER, ProviderId.GROQ, ProviderId.GEMINI))
    assertEquals(listOf(ProviderId.OPENROUTER, ProviderId.GROQ, ProviderId.GEMINI), store.current().chatOrder)
    assertEquals("l'altro ordine non si muove", listOf(ProviderId.OPENROUTER, ProviderId.GROQ, ProviderId.GEMINI), store.current().sttOrder)
  }

  @Test
  fun `leggere i default non scrive niente nel file`() = runBlocking {
    val file = File(directory(), "ai.preferences_pb")
    val store = store(AiSettingsDefaults(actionsEnabled = true), file)
    store.current()
    store.settings.first()
    assertFalse("nessuna migrazione: il file non si tocca finche' l'utente non sceglie", file.exists() && file.length() > 0)
  }

  // --------------------------------------------------------------------- "mai scelto" e "spento"

  @Test
  fun `enableIfNeverChosen accende una volta sola`() = runBlocking {
    val store = store()
    assertFalse(store.current().enabledChosen)
    assertTrue("la prima volta accende", store.enableIfNeverChosen())
    assertTrue(store.current().enabled)
    assertTrue(store.current().enabledChosen)
    assertFalse("la seconda non fa niente", store.enableIfNeverChosen())
    assertTrue(store.current().enabled)
  }

  @Test
  fun `chi ha spento l'assistente lo ritrova spento dopo una chiave verificata`() = runBlocking {
    val store = store()
    store.setEnabled(true)
    store.setEnabled(false)
    assertFalse("l'ha spento: e' una scelta", store.enableIfNeverChosen())
    assertFalse(store.current().enabled)
    assertTrue(store.current().enabledChosen)
  }

  @Test
  fun `enableIfNeverChosen non conta come scelta quello che il default fa gia' valere`() = runBlocking {
    val store = store(AiSettingsDefaults(enabled = true))
    // Il default e' acceso ma nessuno ha scelto: la chiamata scrive la scelta, e torna vero.
    assertTrue(store.enableIfNeverChosen())
    assertTrue(store.current().enabled)
    assertTrue(store.current().enabledChosen)
  }

  // --------------------------------------------------------------------------- il file corrotto

  private fun corrupt(file: File) {
    file.parentFile?.mkdirs()
    file.writeBytes(ByteArray(64) { 0xFF.toByte() })
  }

  @Test
  fun `un file di impostazioni corrotto riparte dai default, e si puo' di nuovo scrivere`() = runBlocking {
    val file = File(directory(), "ai.preferences_pb")
    corrupt(file)
    val store = store(AiSettingsDefaults(actionsEnabled = true), file)
    val settings = store.current()
    assertTrue("ripartito dai default dell'app", settings.actionsEnabled)
    assertFalse(settings.enabled)
    store.setEnabled(true)
    assertTrue("e la scrittura che prima falliva per sempre ora passa", store.current().enabled)
  }

  @Test
  fun `un file di chiavi corrotto riparte vuoto, e una chiave si puo' salvare`() = runBlocking {
    val file = File(directory(), "ai_keys.preferences_pb")
    corrupt(file)
    val keys = AiKeyStore.open(file, TransparentCipher(), scope())
    assertTrue(keys.currentStates().values.none { it.present })
    keys.set(ProviderId.GROQ, "gsk_finta")
    assertEquals("gsk_finta", keys.key(ProviderId.GROQ))
    keys.markVerified(ProviderId.GROQ, 1_000L)
    assertTrue(keys.currentStates().getValue(ProviderId.GROQ).verified)
  }

  @Test
  fun `AiDataStores apre un file scelto dall'app e ne gestisce la corruzione`() = runBlocking {
    val file = File(directory(), "altro.preferences_pb")
    corrupt(file)
    val data: DataStore<Preferences> = AiDataStores.open(file, scope())
    val flag = booleanPreferencesKey("flag")
    assertNotNull(data.data.first())
    data.edit { it[flag] = true }
    assertEquals(true, data.data.first()[flag])
  }

  @Test
  fun `lo store si puo' costruire da un DataStore gia' aperto, come prima`() = runBlocking {
    val owner = scope()
    val data = AiDataStores.open(File(directory(), "ai.preferences_pb"), owner)
    val store = AiSettingsStore(data)
    store.setSpeakReplies(true)
    assertTrue(store.current().speakReplies)
    val withDefaults = AiSettingsStore(data, AiSettingsDefaults(actionsEnabled = true))
    assertTrue("stesso file, default dell'app, e la scelta di prima c'e' ancora", withDefaults.current().actionsEnabled && withDefaults.current().speakReplies)
    owner.cancel()
  }
}
