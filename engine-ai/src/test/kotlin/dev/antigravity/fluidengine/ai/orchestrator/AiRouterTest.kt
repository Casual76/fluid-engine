package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.tools.AiToolCategory
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private enum class RG(override val id: String, override val statusKey: String, override val hint: String) : AiToolGroup {
  GRADES("voti", "grades", "voti e medie"),
  AGENDA("agenda", "agenda", "compiti e verifiche"),
  BOARD("bacheca", "board", "comunicazioni e allegati"),
  LESSONS("orario", "lessons", "orario e lezioni"),
  STATS("statistiche", "stats", "statistiche"),
  APP("app", "app", "azioni nell'app"),
}

private enum class Area(override val id: String, override val label: String, override val hint: String) : AiToolCategory {
  METEO("meteo", "Meteo", "previsioni e pioggia"),
  BUS("bus", "Bus", "autobus e fermate"),
}

private enum class RouterGroup(override val id: String, override val statusKey: String, override val hint: String, override val category: AiToolCategory) : AiToolGroup {
  ORARIO("meteo_orario", "hourly", "ora per ora", Area.METEO),
  RADAR("meteo_radar", "radar", "pioggia in arrivo", Area.METEO),
  ORARI("bus_orari", "schedule", "passaggi", Area.BUS),
}

class AiRouterTest {

  private val router = AiRouter(RG.entries, RG.APP, "il registro elettronico di uno studente", defaultGroups = listOf(RG.GRADES, RG.AGENDA))

  @Test
  fun `il JSON dei gruppi si legge, anche dentro un blocco di codice, con il livello profondo`() {
    assertEquals(RouterVerdict(setOf(RG.BOARD, RG.GRADES), deep = true), router.parse("""{"gruppi":["bacheca","voti"],"profondo":true}""", actionsEnabled = false))
    assertEquals(RouterVerdict(setOf(RG.LESSONS), deep = false), router.parse("```json\n{\"gruppi\":[\"orario\"]}\n```", actionsEnabled = false))
  }

  @Test
  fun `i gruppi ignoti si ignorano, le azioni cadono se spente, il massimo e' quattro`() {
    assertEquals(setOf(RG.AGENDA), router.parse("""{"gruppi":["agenda","xyz","app"],"profondo":false}""", actionsEnabled = false)!!.groups)
    assertEquals(setOf(RG.AGENDA, RG.APP), router.parse("""{"gruppi":["agenda","app"],"profondo":false}""", actionsEnabled = true)!!.groups)
    val five = router.parse("""{"gruppi":["voti","agenda","bacheca","orario","statistiche"],"profondo":false}""", actionsEnabled = true)!!
    assertEquals(4, five.groups.size)
  }

  @Test
  fun `JSON rotto o vuoto porta al ripiego, che tiene i gruppi gia' aperti`() {
    assertNull(router.parse("boh", actionsEnabled = false))
    assertNull(router.parse("""{"gruppi":[]}""", actionsEnabled = false))
    val fallback = router.fallback(setOf(RG.STATS))
    assertTrue(fallback.containsAll(setOf(RG.GRADES, RG.AGENDA, RG.STATS)))
  }

  private val hierarchical = AiRouter(RouterGroup.entries, null, "un assistente a piu' app", defaultGroups = listOf(RouterGroup.ORARIO), categories = Area.entries)

  @Test
  fun `col catalogo gerarchico lo schema chiede categoria e sottocategorie, e il prompt le elenca`() {
    assertTrue(hierarchical.hierarchical)
    val schema = hierarchical.schema.toString()
    assertTrue(schema.contains("\"categoria\""))
    assertTrue(schema.contains("\"nessuna\""))
    assertTrue(schema.contains("\"sottocategorie\""))
    assertFalse(schema.contains("\"gruppi\""))
    val prompt = hierarchical.prompt("it", actionsEnabled = true)
    assertTrue(prompt.contains("- meteo: previsioni e pioggia"))
    assertTrue(prompt.contains("meteo_radar = pioggia in arrivo"))
    assertTrue(prompt.contains("\"nessuna\""))
    assertFalse(prompt.contains("gia' aperte"))
    assertTrue(hierarchical.prompt("it", actionsEnabled = true, loadedCategories = setOf(Area.BUS)).contains("gia' aperte in questa conversazione, i cui strumenti restano disponibili: bus"))
  }

  @Test
  fun `il verdetto gerarchico tiene solo le sottocategorie della categoria scelta o gia' aperte`() {
    val meteo = hierarchical.parse("""{"categoria":"meteo","sottocategorie":["meteo_radar","bus_orari"],"profondo":false}""", actionsEnabled = true)!!
    assertEquals(Area.METEO, meteo.category)
    assertEquals(setOf(RouterGroup.RADAR), meteo.groups)
    assertFalse(meteo.none)
    val withBusOpen = hierarchical.parse("""{"categoria":"meteo","sottocategorie":["meteo_radar","bus_orari"],"profondo":true}""", actionsEnabled = true, loadedCategories = setOf(Area.BUS))!!
    assertEquals(setOf(RouterGroup.RADAR, RouterGroup.ORARI), withBusOpen.groups)
    assertTrue(withBusOpen.deep)
    // Solo la categoria: i gruppi li decide l'orchestratore (quelli di partenza).
    val onlyCategory = hierarchical.parse("""{"categoria":"bus","sottocategorie":[],"profondo":false}""", actionsEnabled = true)!!
    assertEquals(Area.BUS, onlyCategory.category)
    assertTrue(onlyCategory.groups.isEmpty())
    val none = hierarchical.parse("""{"categoria":"nessuna","sottocategorie":[],"profondo":false}""", actionsEnabled = true)!!
    assertTrue(none.none)
    assertTrue(none.groups.isEmpty())
    assertNull(none.category)
    // Categoria ignota e niente sottocategorie valide: si ripiega.
    assertNull(hierarchical.parse("""{"categoria":"cucina","sottocategorie":["pasta"],"profondo":false}""", actionsEnabled = true))
    assertNull(hierarchical.parse("boh", actionsEnabled = true))
  }

  @Test
  fun `lo schema elenca i gruppi e il prompt esclude le azioni spente e nomina l'app`() {
    assertTrue(router.schema.toString().contains("\"bacheca\""))
    assertTrue(router.schema.toString().contains("\"profondo\""))
    assertFalse(router.prompt("it", actionsEnabled = false).contains("- app:"))
    assertTrue(router.prompt("en", actionsEnabled = true).contains("- app:"))
    assertTrue(router.prompt("it", actionsEnabled = false).contains("registro elettronico"))
    assertFalse(router.prompt("it", actionsEnabled = false).contains("meteo"))
  }
}
