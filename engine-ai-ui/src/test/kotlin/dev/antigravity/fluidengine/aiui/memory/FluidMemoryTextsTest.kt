package dev.antigravity.fluidengine.aiui.memory

import dev.antigravity.fluidengine.ai.memory.AiMemory
import dev.antigravity.fluidengine.ai.memory.AiMemoryExclusion
import dev.antigravity.fluidengine.ai.memory.AiMemoryNotice
import dev.antigravity.fluidengine.ai.memory.AiMemorySource
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F9.3 · Le frasi della card «Ho ricordato…» e della pagina «Cosa ricordo di te», come funzioni pure. */
class FluidMemoryTextsTest {
  private val zone = ZoneId.of("Europe/Rome")
  private val now = LocalDateTime.of(2026, 11, 26, 10, 0).atZone(zone).toInstant().toEpochMilli()
  private val day = 86_400_000L
  private val it = FluidMemoryStrings()
  private val cv = AiMemorySource.app("dev.antigravity.classevivaexpressive", "ClasseViva")

  private fun notice(kind: String, source: AiMemorySource = AiMemorySource.chat(1), previous: String? = null) =
    AiMemoryNotice("n", kind, "m", "è in 5F al liceo Galilei", previous, source, now)

  @Test fun `la card dice cosa e' successo, da dove, e cosa c'era prima`() {
    assertEquals("Ho ricordato", FluidMemoryTexts.headline(notice(AiMemoryNotice.KindRemembered), it))
    assertEquals("Ho aggiornato · da ClasseViva", FluidMemoryTexts.headline(notice(AiMemoryNotice.KindUpdated, cv), it))
    assertEquals("prima: è in 4F", FluidMemoryTexts.detail(notice(AiMemoryNotice.KindUpdated, previous = "è in 4F"), it))
    assertEquals("non entra: è in 4F", FluidMemoryTexts.detail(notice(AiMemoryNotice.KindKept, previous = "è in 4F"), it))
    assertNull(FluidMemoryTexts.detail(notice(AiMemoryNotice.KindRemembered), it))
    val undone = notice(AiMemoryNotice.KindUpdated, previous = "è in 4F").copy(undone = true)
    assertEquals("Non lo ricordo più", FluidMemoryTexts.headline(undone, it))
    assertNull(FluidMemoryTexts.detail(undone, it))
    assertEquals(
      "Ho aggiornato: è in 5F al liceo Galilei. prima: è in 4F",
      FluidMemoryTexts.accessibilityLabel(notice(AiMemoryNotice.KindUpdated, previous = "è in 4F"), it),
    )
  }

  @Test fun `la riga sotto un ricordo, coi mesi in minuscolo`() {
    val m = AiMemory("a", "ha la verifica di matematica venerdì", "scuola.verifica", source = cv, createdAtMillis = now - 2 * day,
      observedAtMillis = now - 2 * day, expiresAtMillis = LocalDateTime.of(2026, 11, 28, 0, 0).atZone(zone).toInstant().toEpochMilli())
    assertEquals("Da ClasseViva · 24 novembre · scade il 27 novembre", FluidMemoryTexts.subtitle(m, now, zone, it))
    val expired = FluidMemoryTexts.subtitle(m, now + 3 * day, zone, it)
    assertEquals("Da ClasseViva · 24 novembre · scaduto il 27 novembre", expired)
    val pinned = AiMemory("b", "è vegetariana", source = AiMemorySource.Manual, createdAtMillis = now - 400 * day,
      observedAtMillis = now - 400 * day, pinned = true, edited = true, previousText = "è vegana")
    assertEquals("fissato · Scritto da te · 22 ottobre 2025 · corretto · prima: è vegana", FluidMemoryTexts.subtitle(pinned, now, zone, it))
    assertEquals("Detto in ClasseViva", FluidMemoryTexts.source(AiMemorySource.chat(3, cv.app, "ClasseViva"), it))
    assertEquals("Said in chat", FluidMemoryTexts.source(AiMemorySource.chat(3), FluidMemoryStrings.english()))
  }

  @Test fun `la domanda di serie, il no, le fonti nuove e le esclusioni`() {
    val ask = notice(AiMemoryNotice.KindAsk, previous = "è in 4F")
    assertEquals("Vuoi che lo ricordi?", FluidMemoryTexts.headline(ask, it))
    assertEquals("ClasseViva vuole che lo ricordi", FluidMemoryTexts.headline(notice(AiMemoryNotice.KindAsk, cv), it))
    assertEquals("al posto di: è in 4F", FluidMemoryTexts.detail(ask, it))
    assertEquals("Vuoi che lo ricordi? è in 5F al liceo Galilei. al posto di: è in 4F", FluidMemoryTexts.accessibilityLabel(ask, it))
    val no = ask.copy(declined = true)
    assertEquals("Va bene, non lo ricordo", FluidMemoryTexts.headline(no, it))
    assertNull(FluidMemoryTexts.detail(no, it))
    assertEquals("Remember this?", FluidMemoryTexts.headline(ask, FluidMemoryStrings.english()))
    assertEquals("Chiesto in chat, coi dati di Pampa Notes",
      FluidMemoryTexts.source(AiMemorySource.tool(1, "dev.pampa.pampanotes", "Pampa Notes"), it))
    assertEquals("Chiesto in chat", FluidMemoryTexts.source(AiMemorySource.tool(1), it))
    val inferred = AiMemory("p", "musica artista preferito: Coldplay", "musica.artista_preferito",
      source = AiMemorySource.research("dev.pampa.fluidify", "Fluidify"), createdAtMillis = now, observedAtMillis = now - 2 * day,
      origin = AiMemory.OriginInference, limitations = "solo dagli ultimi 30 giorni")
    assertEquals("Imparato dai dati di Fluidify · 24 novembre · dedotto: solo dagli ultimi 30 giorni",
      FluidMemoryTexts.subtitle(inferred, now, zone, it))
    assertEquals("L'argomento «scuola › classe»", FluidMemoryTexts.exclusion(AiMemoryExclusion(key = "scuola.classe"), it))
    assertEquals("Da MyCloud Gallery",
      FluidMemoryTexts.exclusion(AiMemoryExclusion(app = "dev.pampa.mycloudgallery"), it) { "MyCloud Gallery" })
    assertEquals("Un elemento da ClasseViva",
      FluidMemoryTexts.exclusion(AiMemoryExclusion(app = cv.app, grant = "g", record = "r"), it) { "ClasseViva" })
  }

  @Test fun `i gruppi della pagina`() {
    fun m(id: String, source: AiMemorySource) = AiMemory(id, id, source = source, createdAtMillis = now)
    val mail = AiMemorySource.app("dev.pampa.mail", "Pampa Mail")
    val groups = FluidMemoryTexts.groups(
      listOf(m("a", AiMemorySource.chat(1)), m("b", cv), m("c", AiMemorySource.imported(4)), m("d", mail), m("e", AiMemorySource.Manual),
        m("f", AiMemorySource.chat(2, cv.app, "ClasseViva"))),
      it,
    )
    assertEquals(listOf("Da te", "Da ClasseViva", "Da Pampa Mail", "Dalla memoria di prima"), groups.map { it.title })
    assertEquals(listOf("a", "e"), groups[0].memories.map { it.id })
    assertEquals(listOf("b", "f"), groups[1].memories.map { it.id })
    assertEquals("dev.antigravity.classevivaexpressive", groups[1].app)
  }
}
