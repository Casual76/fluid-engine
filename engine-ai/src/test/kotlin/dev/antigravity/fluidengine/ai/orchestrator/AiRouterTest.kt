package dev.antigravity.fluidengine.ai.orchestrator

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
