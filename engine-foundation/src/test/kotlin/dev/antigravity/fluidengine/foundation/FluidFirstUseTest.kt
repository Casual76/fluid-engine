package dev.antigravity.fluidengine.foundation

import dev.antigravity.fluidengine.foundation.FluidPermissionStatus.Denied
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus.DeniedForever
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus.Granted
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus.NeverAsked
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus.Partial
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.11: le decisioni pure del benvenuto, delle novità e dei permessi. */
class FluidFirstUseTest {

  @Test fun benvenutoSoloAllaPrimaInstallazione() {
    assertEquals(FluidWelcomeKind.Welcome, fluidWelcomeKind("1.0.0", null, freshInstall = true, hasNews = true))
    assertNull(fluidWelcomeKind("1.0.0", null, freshInstall = true, hasNews = true, hasWelcome = false))
    // La stessa versione, già vista: niente, anche con le novità.
    assertNull(fluidWelcomeKind("1.0.0", "1.0.0", freshInstall = true, hasNews = true))
  }

  @Test fun novitaUnaVoltaPerVersione() {
    assertEquals(FluidWelcomeKind.WhatsNew, fluidWelcomeKind("1.1.0", "1.0.0", freshInstall = false, hasNews = true))
    assertNull(fluidWelcomeKind("1.1.0", "1.1.0", freshInstall = false, hasNews = true))
    // Senza novità non c'è foglio, ma la versione si segna lo stesso: una nota aggiunta dopo non compare.
    assertNull(fluidWelcomeKind("1.2.0", "1.1.0", freshInstall = false, hasNews = false))
    assertTrue(fluidWelcomeMarksSilently("1.2.0", "1.1.0"))
    assertFalse(fluidWelcomeMarksSilently("1.2.0", "1.2.0"))
    // Un ritorno indietro non mostra niente.
    assertNull(fluidWelcomeKind("1.0.0", "1.1.0", freshInstall = false, hasNews = true))
    // Dalla beta alla stabile: la stabile è più nuova.
    assertEquals(FluidWelcomeKind.WhatsNew, fluidWelcomeKind("1.2.0", "1.2.0-beta1", freshInstall = false, hasNews = true))
    // La prima versione col benvenuto dell'engine su un'app che c'era già: le novità, non il benvenuto.
    assertEquals(FluidWelcomeKind.WhatsNew, fluidWelcomeKind("4.0.0", null, freshInstall = false, hasNews = true))
  }

  @Test fun leNovitaDalManifestOppureDalChangelog() {
    val raw = """
      {"app": {
        "stable": {"version": "2.1.0", "changelog": "- Ricerca: trova le note mentre scrivi\n- Una correzione qualunque nel salvataggio delle note lunghe, finalmente\n# Note",
          "highlights": [{"title": "Note fissate", "text": "Le note importanti restano in cima.", "symbol": "star"}, {"text": "senza titolo"}]},
        "beta": {"version": "2.2.0-beta1", "changelog": "Cartelle condivise: invita chi vuoi"}
      }}
    """.trimIndent()
    val manifest = EngineManifestParser.parse(raw)
    assertEquals(
      listOf(FluidReleaseNote("Note fissate", "Le note importanti restano in cima.", "star")),
      manifest.releaseNotesFor("2.1.0"),
    )
    assertEquals(
      listOf(FluidReleaseNote("Cartelle condivise", "invita chi vuoi")),
      manifest.releaseNotesFor("2.2.0-beta1", beta = true),
    )
    // Un'altra versione: nessuna novità.
    assertTrue(manifest.releaseNotesFor("2.0.0").isEmpty())
    assertEquals(
      listOf(
        FluidReleaseNote("Ricerca", "trova le note mentre scrivi"),
        FluidReleaseNote("Una correzione qualunque nel salvataggio delle note lunghe, finalmente"),
      ),
      fluidReleaseNotesFromChangelog("- Ricerca: trova le note mentre scrivi\n- Una correzione qualunque nel salvataggio delle note lunghe, finalmente\n# Note"),
    )
    // Il manifest di oggi, senza highlights, si legge come prima.
    assertTrue(EngineManifestParser.parse("""{"app": {"stable": {"version": "1.0.0"}}}""").app!!.stable!!.highlights.isEmpty())
  }

  @Test fun loStatoDelPermesso() {
    assertEquals(Granted, fluidPermissionStatus(granted = true, partial = false, asked = false, rationale = false))
    assertEquals(Partial, fluidPermissionStatus(granted = false, partial = true, asked = true, rationale = false))
    assertEquals(NeverAsked, fluidPermissionStatus(granted = false, partial = false, asked = false, rationale = false))
    assertEquals(Denied, fluidPermissionStatus(granted = false, partial = false, asked = true, rationale = true))
    // Android dice lo stesso di «mai chiesto»: lo distingue la memoria dell'engine.
    assertEquals(DeniedForever, fluidPermissionStatus(granted = false, partial = false, asked = true, rationale = false))
    // Negato una volta prima dell'engine: la spiegazione accesa vince sulla memoria vuota.
    assertEquals(Denied, fluidPermissionStatus(granted = false, partial = false, asked = false, rationale = true))
  }

  @Test fun dopoIlDialogo() {
    assertEquals(Granted, fluidPermissionAfterRequest(NeverAsked, granted = true, partial = false, rationale = false))
    assertEquals(Partial, fluidPermissionAfterRequest(NeverAsked, granted = false, partial = true, rationale = false))
    assertEquals(Denied, fluidPermissionAfterRequest(NeverAsked, granted = false, partial = false, rationale = true))
    // Il primo dialogo chiuso senza scegliere non è un rifiuto.
    assertEquals(NeverAsked, fluidPermissionAfterRequest(NeverAsked, granted = false, partial = false, rationale = false))
    // Il secondo rifiuto (Android 11+) spegne la spiegazione: per sempre.
    assertEquals(DeniedForever, fluidPermissionAfterRequest(Denied, granted = false, partial = false, rationale = false))
  }

  @Test fun ilPassoDelTasto() {
    assertEquals(FluidPermissionStep.Proceed, fluidPermissionStep(Granted, explained = false))
    assertEquals(FluidPermissionStep.Proceed, fluidPermissionStep(Partial, explained = false))
    assertEquals(FluidPermissionStep.Explain, fluidPermissionStep(NeverAsked, explained = false))
    // Una volta sola: poi il dialogo diretto, anche dopo un rifiuto.
    assertEquals(FluidPermissionStep.AskSystem, fluidPermissionStep(NeverAsked, explained = true))
    assertEquals(FluidPermissionStep.AskSystem, fluidPermissionStep(Denied, explained = true))
    assertEquals(FluidPermissionStep.AskSystem, fluidPermissionStep(NeverAsked, explained = false, hasExplanation = false))
    assertEquals(FluidPermissionStep.OpenSettings, fluidPermissionStep(DeniedForever, explained = true))
    // Le notifiche prima di Android 13: non si chiedono, si accendono nelle impostazioni.
    assertEquals(FluidPermissionStep.OpenSettings, fluidPermissionStep(NeverAsked, explained = false, requestable = false))
  }

  @Test fun laMemoriaDiProva() = runTest {
    val memory = FluidFirstUseMemory.inMemory()
    assertNull(memory.read(FluidFirstUseKeys.welcomeSeen()))
    memory.write(FluidFirstUseKeys.welcomeSeen(), "1.0.0")
    assertEquals("1.0.0", memory.read("welcome.app.seen"))
    memory.write(FluidFirstUseKeys.welcomeSeen(), null)
    assertNull(memory.read(FluidFirstUseKeys.welcomeSeen()))
    assertEquals("permission.mic.asked", FluidFirstUseKeys.permissionAsked("mic"))
  }
}
