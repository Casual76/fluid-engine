package dev.antigravity.fluidengine.sample.bilanci

import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import dev.antigravity.fluidengine.sample.novita.NewElementSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F7.28 · Il registro dei bilanci si legge, e ogni elemento disponibile ha il suo bilancio (la verifica di completezza).
 *
 * Disponibile vuol dire: una scena di «Misure», un componente del vetro che `GlassNodeCountTest` monta da solo, una scena
 * della pagina «Elementi nuovi»; il banco delle allocazioni ha una riga per scena. Un elemento che non c'è ancora resta «atteso» in
 * `attesi.bilancio`; quando arriva, la verifica vuole il suo frammento con le cinque fasi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlassBalanceRegistryTest {

  /** Gli elementi disponibili oggi, per proprietario del bilancio. */
  private fun catalog(): Map<String, List<String>> = buildMap {
    put("vetro.bilancio", MeasureScenario.entries.map { it.key } + GlassComponents)
    for (section in NewElementSections) put(section.pack, section.scenes.map { it.id })
  }

  private fun files(owner: String): BalanceFile? =
    GlassBalanceRegistry.load(if (owner.endsWith(".bilancio")) owner else GlassBalanceRegistry.fragmentName(owner))

  @Test
  fun ogniElementoDisponibileHaIlSuoBilancio() {
    val expected = GlassBalanceRegistry.require("attesi.bilancio").expected
    val problems = GlassBalanceRegistry.completeness(catalog(), ::files, expected)
    assertTrue("elementi senza bilancio:\n  ${problems.joinToString("\n  ")}", problems.isEmpty())
  }

  @Test
  fun ogniFileDelRegistroSiLegge() {
    val names = listOf("vetro.bilancio", "banco.bilancio", "attesi.bilancio") +
      NewElementSections.map { GlassBalanceRegistry.fragmentName(it.pack) }
    for (name in names) {
      val file = GlassBalanceRegistry.require(name)
      for (row in file.rows) {
        assertTrue("${row.where}: la prova «${row.proof}» non è una classe di prova", row.proof.endsWith("Test"))
      }
    }
    // Il banco non ha le cinque fasi (misura solo il moto), ma ogni sua scena ha la riga.
    val bench = GlassBalanceRegistry.require("banco.bilancio")
    for (scene in GlassHotPathAllocationTest.Scenes) {
      assertTrue("banco.bilancio: manca la riga $scene moto", bench.row(scene, "moto") != null)
    }
  }

  // ---- Rosso → verde della completezza --------------------------------------------------------------------------

  private val good = GlassBalanceRegistry.parse(
    "f8p99.bilancio",
    """
    a entrata 1 1 1 1 1 1 1 1 - - F7.28 F8BalanceTest | una scena di prova, entrata
    a moto    - - - - - - - - - - F7.28 F8BalanceTest | una scena di prova senza moto proprio
    a posa    1 1 0 0 1 1 0 0 0 - F7.28 F8BalanceTest | una scena di prova, posa
    a uscita  1 1 0 0 0 1 0 0 - - F7.28 F8BalanceTest | una scena di prova, uscita
    a rilascio 0 0 - - - 0 - - - - F7.28 F8BalanceTest | una scena di prova, rilascio
    """.trimIndent(),
  )

  @Test
  fun laCompletezzaCadeSuUnElementoDisponibileSenzaBilancio() {
    val attesi = GlassBalanceRegistry.parse(
      "attesi.bilancio",
      "F8.98 atteso F7.28 | un pacchetto di prova che non c'è ancora",
    ).expected
    // Verde: la scena ha le cinque fasi, il pacchetto atteso non c'è ancora.
    assertEquals(emptyList<String>(), GlassBalanceRegistry.completeness(mapOf("F8.99" to listOf("a")), { good }, attesi))
    // Rosso: una scena nuova senza righe.
    assertTrue(GlassBalanceRegistry.completeness(mapOf("F8.99" to listOf("a", "b")), { good }, attesi)
      .single().contains("b: scena disponibile senza bilancio"))
    // Rosso: un pacchetto disponibile senza frammento.
    assertTrue(GlassBalanceRegistry.completeness(mapOf("F8.97" to listOf("a")), { null }, attesi)
      .single().contains("senza bilancio"))
    // Rosso: il pacchetto atteso è arrivato, e la riga «atteso» è ancora lì.
    assertTrue(GlassBalanceRegistry.completeness(mapOf("F8.98" to listOf("a")), { good }, attesi)
      .any { "ancora «atteso»" in it })
    // Rosso: una fase che manca.
    val noRelease = GlassBalanceRegistry.parse("f8p99.bilancio", good.rows.filter { it.phase != "rilascio" }
      .joinToString("\n") { row -> "${row.id} ${row.phase} ${row.values.values.joinToString(" ")} ${row.version} ${row.proof} | ${row.reason}" })
    assertTrue(GlassBalanceRegistry.completeness(mapOf("F8.99" to listOf("a")), { noRelease }, attesi)
      .single().contains("mancano le fasi [rilascio]"))
    // Rosso: una riga per una scena che non c'è più.
    assertTrue(GlassBalanceRegistry.completeness(mapOf("F8.99" to emptyList()), { good }, attesi)
      .single().contains("scena che non c'è"))
  }

  @Test
  fun unaRigaSenzaMotivoOConUnNumeroSbagliatoNonSiLegge() {
    assertThrows(BalanceFormatException::class.java) {
      GlassBalanceRegistry.parse("x", "a entrata 1 1 1 1 1 1 1 1 - - F7.28 F8BalanceTest |")
    }
    assertThrows(BalanceFormatException::class.java) {
      GlassBalanceRegistry.parse("x", "a entrata 1 1 uno 1 1 1 1 1 - - F7.28 F8BalanceTest | un motivo qualunque")
    }
    assertThrows(BalanceFormatException::class.java) {
      GlassBalanceRegistry.parse("x", "a entrata NV 1 1 1 1 1 1 1 - - F7.28 F8BalanceTest | un motivo senza il posto")
    }
    assertThrows(BalanceFormatException::class.java) {
      GlassBalanceRegistry.parse("x", "a ferma 1 1 1 1 1 1 1 1 - - F7.28 F8BalanceTest | una fase che non esiste")
    }
    val row = GlassBalanceRegistry.parse("x", "a entrata 1 2 1 1 1 1 0 0 - - F7.28 F8BalanceTest | il tetto di prova").rows.single()
    assertEquals(listOf("layer 3 (tetto 2)"), row.exceeded(mapOf(BalanceColumn.Layers to 3L, BalanceColumn.Nodes to 1L)))
  }

  companion object {
    /** I componenti del vetro che `GlassNodeCountTest` monta da soli: li misura `GlassBalanceScenesTest`. */
    val GlassComponents = listOf("tasto", "gruppo", "popover", "tab-bar")
  }
}
