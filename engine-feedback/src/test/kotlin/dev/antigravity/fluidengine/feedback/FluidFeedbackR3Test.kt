package dev.antigravity.fluidengine.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.diagnostics.FluidLog
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R3 (review `sol-ai`, n. 5, 12 e 13) · Gli IPv6 compressi non entrano nello ZIP; lo screenshot di lavoro se ne va con
 * la bozza; con «L'app e il telefono» spento i dati dell'app (versione, pacchetto, build, engine) non sono in nessun file.
 */
@RunWith(RobolectricTestRunner::class)
class FluidFeedbackR3Test {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val config = FluidFeedbackConfig("Pampa Notes")

  @After
  fun tearDown() {
    FluidLog.clear()
    FluidFeedbackFile.workDir(context).deleteRecursively()
  }

  private fun info() = FluidFeedbackInfo(
    appName = "Pampa Notes", appId = "dev.pampa.notes", appVersion = "2.0.0", versionCode = 12, buildType = "beta",
    engineVersion = "3.1.7", device = "Samsung SM-X710", android = "16 (API 36)", language = "it-IT",
    screen = "1080 × 2340 px · 420 dpi", appearance = "scuro · testo 100%", experiments = emptyList(),
    crashes = emptyList(), log = emptyList(), createdAtMillis = 1_795_702_980_000L,
  )

  private fun unzip(file: File): List<Pair<String, ByteArray>> = ZipInputStream(file.inputStream()).use { zip ->
    generateSequence { zip.nextEntry }.map { it.name to zip.readBytes() }.toList()
  }

  /** [text] scritto per intero, non dentro un'altra parola, un numero o un indirizzo. */
  private fun isolated(text: String) = Regex("(?i)(?<![0-9a-z:.])" + Regex.escape(text) + "(?![0-9a-z]|\\.[0-9a-z])")

  // --- n. 5 · gli IPv6 compressi -------------------------------------------------------------------------------

  @Test
  fun compressedIpv6IsNotInTheZip() = runBlocking<Unit> {
    FluidLog.clear()
    FluidLog.warn("rete", "Timeout verso 2001:db8::1 dopo 15 s")
    FluidLog.event("rete", "GET http://[2001:db8::42]:8080/v1/students/1234567?x=1")
    val collected = FluidFeedbackInfo.collect(context, config, diagnostics = null, locale = Locale.ITALY)
    val draft = FluidFeedbackDraft(description = "Il router fe80::1%wlan0 non risponde, né ::ffff:192.0.2.10")
    val file = FluidFeedbackFile.writeToFile(context, draft, collected, locale = Locale.ITALY)
    val all = unzip(file).joinToString("\n") { String(it.second, Charsets.UTF_8) }
    for (address in listOf("2001:db8::1", "2001:db8::42", "fe80::1", "::ffff:192.0.2.10", "192.0.2.10")) {
      assertFalse("$address è nello ZIP", isolated(address).containsMatchIn(all))
    }
    assertFalse("il percorso è nello ZIP", Regex("(?<!\\d)1234567(?!\\d)").containsMatchIn(all))
    assertTrue(all.contains("«[ip]»"))
    file.delete()
  }

  // --- n. 12 · lo screenshot di lavoro ------------------------------------------------------------------------

  @Test
  fun forgettingTheDraftDeletesTheWorkingScreenshot() {
    val state = FluidFeedbackState(config, lab = null, diagnostics = null)
    val shot = File(FluidFeedbackFile.workDir(context), "screenshot-1.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    // Lo screenshot è nella bozza ma spento: la segnalazione parte senza, poi la bozza si dimentica.
    state.update { it.copy(description = "Lento", screenshot = shot, includeScreenshot = false) }
    state.discard()
    assertFalse("lo screenshot di lavoro è rimasto nella cache", shot.exists())
    assertEquals(FluidFeedbackDraft(), state.draft)
  }

  @Test
  fun forgettingTheDraftLeavesTheAppsOwnFiles() {
    // Un file dell'app messo nella bozza da lei, fuori dalla cartella di lavoro: non è nostro, resta.
    val own = File(context.cacheDir, "mia-immagine.png").apply { writeBytes(byteArrayOf(1)) }
    val state = FluidFeedbackState(config, lab = null, diagnostics = null)
    state.update { it.copy(screenshot = own) }
    state.discard()
    assertTrue(own.exists())
    own.delete()
  }

  // --- n. 13 · «L'app e il telefono» spento -------------------------------------------------------------------

  @Test
  fun appAndPhoneOffLeavesNoAppDataInTheZip() {
    val draft = FluidFeedbackDraft(description = "Lento", includeDevice = false)
    val entries = FluidFeedbackFile.entries(draft, info(), screenshot = null, locale = Locale.ITALY)
    val readme = String(entries[0].second, Charsets.UTF_8)
    val json = Json.parseToJsonElement(String(entries[1].second, Charsets.UTF_8)).jsonObject
    for ((name, bytes) in entries) {
      val text = String(bytes, Charsets.UTF_8)
      for (gone in listOf("2.0.0", "dev.pampa.notes", "3.1.7", "beta", "Samsung", "SM-X710")) {
        assertFalse("$gone è in $name", isolated(gone).containsMatchIn(text))
      }
    }
    // Resta il nome dell'app: dice di che app è la segnalazione (è anche nel nome dello ZIP).
    assertTrue(readme, readme.startsWith("Segnalazione · Pampa Notes\r\n"))
    assertEquals(setOf("name"), json["app"]!!.jsonObject.keys)
    assertFalse(json.containsKey("device"))
    // E così anche il titolo della issue.
    assertEquals("[Pampa Notes] Lento", FluidFeedbackText.title(draft, info()))
  }

  @Test
  fun appAndPhoneOnKeepsEverything() {
    val draft = FluidFeedbackDraft(description = "Lento")
    val entries = FluidFeedbackFile.entries(draft, info(), screenshot = null, locale = Locale.ITALY)
    val readme = String(entries[0].second, Charsets.UTF_8)
    val json = Json.parseToJsonElement(String(entries[1].second, Charsets.UTF_8)).jsonObject
    assertTrue(readme, readme.startsWith("Segnalazione · Pampa Notes 2.0.0\r\n"))
    assertEquals(setOf("name", "id", "version", "code", "build", "engine"), json["app"]!!.jsonObject.keys)
    assertEquals("[Pampa Notes 2.0.0] Lento", FluidFeedbackText.title(draft, info()))
  }
}
