package dev.antigravity.fluidengine.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.diagnostics.FluidLog
import dev.antigravity.fluidengine.foundation.lab.FluidExperiment
import dev.antigravity.fluidengine.foundation.lab.FluidLab
import dev.antigravity.fluidengine.foundation.lab.FluidLabStore
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F8.24 · «Segnala un problema», la parte senza interfaccia: cosa si raccoglie, i dati personali tolti, il file (il
 * file ZIP), l'indirizzo della issue precompilata e il suo tetto.
 */
@RunWith(RobolectricTestRunner::class)
class FluidFeedbackTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val github = FluidFeedbackGitHub("Casual76", "fluid-engine", template = "segnalazione.md")
  private val config = FluidFeedbackConfig("Pampa Notes", github)

  @After
  fun tearDown() = FluidLog.clear()

  private fun info(log: List<String> = listOf("14:03:01 I sync: Sincronizzate 3 note"), crashes: List<String> = emptyList()) = FluidFeedbackInfo(
    appName = "Pampa Notes", appId = "dev.pampa.notes", appVersion = "2.0.0", versionCode = 12, buildType = "release",
    engineVersion = "3.1.2", device = "Samsung SM-X710", android = "16 (API 36)", language = "it-IT",
    screen = "1080 × 2340 px · 420 dpi", appearance = "scuro · testo 100%", experiments = listOf("Nuovo editor"),
    crashes = crashes, log = log, createdAtMillis = 1_795_702_980_000L,
  )

  private fun query(url: String): Map<String, String> = URI(url).rawQuery.split("&").associate { part ->
    val (k, v) = part.split("=", limit = 2)
    k to URLDecoder.decode(v, "UTF-8")
  }

  @Test
  fun collectTakesTheAppThePhoneTheLabAndTheScrubbedLog() {
    FluidLog.clear()
    FluidLog.event("sync", "Sincronizzate 3 note")
    FluidLog.warn("posta", "Invio a marta.rossi@example.com non riuscito")
    val editor = FluidExperiment("nuovo-editor", "Nuovo editor", "L'editor nuovo.")
    val lab = FluidLab(listOf(editor), FluidLabStore.inMemory()).apply { setEnabled(editor, true) }
    val collected = FluidFeedbackInfo.collect(context, config, lab = lab, diagnostics = null, locale = Locale.ITALY)
    assertEquals("Pampa Notes", collected.appName)
    assertEquals(context.packageName, collected.appId)
    assertEquals(listOf("Nuovo editor"), collected.experiments)
    assertEquals(2, collected.log.size)
    assertTrue(collected.log.last(), collected.log.last().contains("«[email]»"))
    assertFalse(collected.log.joinToString().contains("marta"))
    assertTrue(collected.android.contains("API"))
    assertEquals("it-IT", collected.language)
  }

  @Test
  fun theIssueIsPrefilledAndCreditsTheReporter() {
    val shot = File(context.cacheDir, "segnata.png").apply { writeBytes(byteArrayOf(1)) }
    val draft = FluidFeedbackDraft(
      description = "Il titolo è tagliato nella pagina Note\nSuccede sempre con i titoli lunghi + emoji 🙂",
      screenshot = shot,
      credit = "@mario",
    )
    val url = FluidFeedbackText.issueUrl(draft, info(), github, screenshotName = "Segnalazione.png")
    assertTrue(url.startsWith("https://github.com/Casual76/fluid-engine/issues/new?"))
    assertFalse("gli spazi sono %20, mai +", url.contains("+"))
    val q = query(url)
    assertEquals("[Pampa Notes 2.0.0] Il titolo è tagliato nella pagina Note", q["title"])
    assertEquals("segnalazione.md", q["template"])
    val body = q.getValue("body")
    assertTrue(body.contains("### Cosa non va?"))
    assertTrue(body.contains("titoli lunghi + emoji 🙂"))
    assertTrue(body.contains("| Telefono | Samsung SM-X710 |"))
    assertTrue(body.contains("- Nuovo editor"))
    assertTrue(body.contains("<details><summary>Ultimi eventi (1)</summary>"))
    assertTrue(body.contains("`Segnalazione.png`"))
    assertTrue(body.endsWith("Segnalato da @mario con «Segnala un problema» di Pampa Notes. Grazie!"))
  }

  @Test
  fun whatIsSwitchedOffDoesNotLeave() {
    val draft = FluidFeedbackDraft(
      description = "Scrivimi a luca@example.com", includeDevice = false, includeExperiments = false, includeLog = false,
      includeCrashes = false,
    )
    val body = FluidFeedbackText.issueBody(draft, info(crashes = listOf("26 nov · IllegalStateException")), screenshotName = "x.png")
    assertFalse(body.contains("Telefono"))
    assertFalse(body.contains("Nuovo editor"))
    assertFalse(body.contains("Ultimi eventi"))
    assertFalse(body.contains("IllegalStateException"))
    // Nessuno screenshot nella bozza: niente sezione, anche col nome.
    assertFalse(body.contains("Screenshot"))
    // La frase dell'utente passa dal filtro: la issue è pubblica.
    assertTrue(body.contains("Scrivimi a «[email]»"))
    assertTrue(body.endsWith("Segnalato con «Segnala un problema» di Pampa Notes. Grazie!"))
  }

  @Test
  fun aLongReportFitsTheUrlByDroppingTheOldestEventsFirst() {
    val log = List(400) { i -> "14:%02d:%02d I evento: passo numero $i della prova lunga".format(i / 60, i % 60) }
    val url = FluidFeedbackText.issueUrl(FluidFeedbackDraft(description = "Lento"), info(log = log), github)
    assertTrue(url.length <= FluidFeedbackText.MaxIssueUrlLength)
    val body = query(url).getValue("body")
    assertTrue("restano gli ultimi", body.contains("passo numero 399"))
    assertFalse("i primi vanno via", body.contains("passo numero 0 "))
    // Anche una frase enorme sta nell'indirizzo.
    val huge = FluidFeedbackText.issueUrl(FluidFeedbackDraft(description = "à".repeat(20_000)), info(log = log), github)
    assertTrue(huge.length <= FluidFeedbackText.MaxIssueUrlLength)
  }

  /** Legge uno ZIP: i nomi nell'ordine in cui sono scritti, coi loro byte. */
  private fun unzip(file: File): List<Pair<String, ByteArray>> = ZipInputStream(file.inputStream()).use { zip ->
    generateSequence { zip.nextEntry }.map { it.name to zip.readBytes() }.toList()
  }

  @Test
  fun theZipHasTheReadmeFirstAndEverythingThatIsOn() = runBlocking<Unit> {
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    val shot = File(context.cacheDir, "segnata.png").apply { writeBytes(png) }
    val draft = FluidFeedbackDraft(description = "Non si apre la nota", screenshot = shot, credit = "Mario")
    val file = FluidFeedbackFile.writeToFile(
      context, draft, info(crashes = listOf("26 nov · IllegalStateException")), locale = Locale.ITALY,
    )
    // Un nome chiaro: l'app e la data.
    assertTrue(file.name, file.name.matches(Regex("Segnalazione-pampa-notes-\\d{8}-\\d{4}\\.zip")))
    // Nella cartella del foglio Condividi, che lo manda come documento.
    assertTrue(file.path.contains("fluid-share"))
    assertEquals("application/zip", FluidFeedbackFile.MimeType)
    val entries = unzip(file)
    assertEquals(listOf("LEGGIMI.txt", "segnalazione.json", "screenshot.png", "registro.txt"), entries.map { it.first })
    val readme = String(entries[0].second, Charsets.UTF_8)
    assertTrue(readme, readme.startsWith("Segnalazione · Pampa Notes 2.0.0\r\n"))
    assertTrue(readme.contains("COSA NON VA?\r\nNon si apre la nota\r\n"))
    assertTrue(readme.contains("Telefono: Samsung SM-X710"))
    assertTrue(readme.contains("Android: 16 (API 36)"))
    assertTrue(readme.contains("- Nuovo editor"))
    assertTrue(readme.contains("- 26 nov · IllegalStateException"))
    assertTrue(readme.contains("Segnalato da Mario"))
    assertTrue(readme.contains("- screenshot.png: lo screenshot segnato"))
    assertTrue(readme.contains("- registro.txt: l'ultimo evento dell'app"))
    // Il JSON ha gli stessi dati.
    val json = Json.parseToJsonElement(String(entries[1].second, Charsets.UTF_8)).jsonObject
    assertEquals(1, json["schema"]!!.jsonPrimitive.int)
    assertEquals("Non si apre la nota", json["description"]!!.jsonPrimitive.content)
    assertEquals("Samsung SM-X710", json["device"]!!.jsonObject["phone"]!!.jsonPrimitive.content)
    assertEquals("screenshot.png", json["screenshot"]!!.jsonPrimitive.content)
    assertEquals("Mario", json["credit"]!!.jsonPrimitive.content)
    // Lo screenshot è quello segnato, al byte; il registro è quello pulito.
    assertTrue(png.contentEquals(entries[2].second))
    assertEquals("14:03:01 I sync: Sincronizzate 3 note\r\n", String(entries[3].second, Charsets.UTF_8))
    // Lo stesso ZIP due volte ha gli stessi byte (le voci hanno l'ora della segnalazione).
    val again = FluidFeedbackFile.writeToFile(context, draft, info(crashes = listOf("26 nov · IllegalStateException")), locale = Locale.ITALY)
    assertTrue(file.readBytes().contentEquals(again.readBytes()))
    shot.delete()
  }

  @Test
  fun whatIsOffIsInNoFileOfTheZip() {
    val shot = File(context.cacheDir, "segnata.png").apply { writeBytes(byteArrayOf(1)) }
    val draft = FluidFeedbackDraft(
      description = "Lento", screenshot = shot, includeScreenshot = false, includeDevice = false, includeExperiments = false,
      includeCrashes = false, includeLog = false,
    )
    val entries = FluidFeedbackFile.entries(draft, info(crashes = listOf("26 nov · IllegalStateException")), screenshot = byteArrayOf(1))
    assertEquals(listOf("LEGGIMI.txt", "segnalazione.json"), entries.map { it.first })
    val all = entries.joinToString("\n") { String(it.second, Charsets.UTF_8) }
    for (gone in listOf("Samsung", "SM-X710", "Nuovo editor", "IllegalStateException", "Sincronizzate", "screenshot.png", "registro.txt")) {
      assertFalse("$gone non dovrebbe esserci", all.contains(gone))
    }
    val json = Json.parseToJsonElement(String(entries[1].second, Charsets.UTF_8)).jsonObject
    assertFalse(json.containsKey("device"))
    assertFalse(json.containsKey("log"))
    assertEquals(JsonNull, json["screenshot"])
    shot.delete()
  }

  @Test
  fun personalDataIsRemovedInsideTheZipToo() = runBlocking<Unit> {
    FluidLog.clear()
    FluidLog.warn("posta", "Invio a marta.rossi@example.com non riuscito")
    FluidLog.event("rete", "GET https://api.classeviva.it/rest/v1/students/1234567/grades token=abc123segretissimo")
    val collected = FluidFeedbackInfo.collect(context, config, diagnostics = null, locale = Locale.ITALY)
    val draft = FluidFeedbackDraft(
      description = "Scrivetemi a luca@example.com o al +39 333 123 4567",
      credit = "luca@example.com",
    )
    val file = FluidFeedbackFile.writeToFile(context, draft, collected, locale = Locale.ITALY)
    val all = unzip(file).joinToString("\n") { String(it.second, Charsets.UTF_8) }
    for (secret in listOf("marta", "luca@example.com", "333 123 4567", "1234567", "abc123segretissimo")) {
      assertFalse("$secret è uscito", all.contains(secret))
    }
    assertTrue(all.contains("«[email]»"))
    assertTrue(all.contains("«[telefono]»"))
    assertTrue(all.contains("https://api.classeviva.it/«…»"))
  }

  @Test
  fun namesAndRules() {
    assertEquals("Segnalazione-pampa-notes-", FluidFeedbackText.fileName(info(), "pdf").substringBeforeLast("2026"))
    assertTrue(FluidFeedbackText.fileName(info(), "pdf").matches(Regex("Segnalazione-pampa-notes-\\d{8}-\\d{4}\\.pdf")))
    assertTrue(runCatching { FluidFeedbackGitHub("nome con spazi", "x") }.isFailure)
    assertFalse(FluidFeedbackDraft().canSend)
    assertTrue(FluidFeedbackDraft(description = "x").canSend)
    assertEquals("a%20b%2Bc", FluidFeedbackText.encode("a b+c"))
  }
}
