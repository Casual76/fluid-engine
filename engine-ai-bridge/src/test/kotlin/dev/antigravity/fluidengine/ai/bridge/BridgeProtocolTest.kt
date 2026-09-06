package dev.antigravity.fluidengine.ai.bridge

import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolCategory
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private object BridgeCategory : AiToolCategory {
  override val id = "classeviva"
  override val label = "ClasseViva"
  override val hint = "il registro"
}

private enum class BridgeGroup(override val id: String, override val statusKey: String, override val hint: String, override val loadsWithCategory: Boolean = false) : AiToolGroup {
  VOTI("voti", "grades", "i voti", loadsWithCategory = true),
  APP("app", "app", "azioni nell'app");

  override val category: AiToolCategory get() = BridgeCategory
}

private class BridgeEcho(override val name: String, override val group: AiToolGroup, override val needsConfirmation: Boolean = false, override val longRunning: Boolean = false) : AiTool<Unit> {
  override val description = "eco di $name"
  override val parameters: JsonObject = Schema.obj(mapOf("x" to Schema.str("un parametro")))
  override val isAction: Boolean get() = needsConfirmation
  override suspend fun run(args: JsonObject, ctx: Unit): ToolOutput = ToolOutput("eco")
}

class BridgeProtocolTest {

  private val registry = ToolRegistry<Unit>(
    tools = listOf(BridgeEcho("voti_media", BridgeGroup.VOTI), BridgeEcho("bacheca_presa_visione", BridgeGroup.APP, needsConfirmation = true), BridgeEcho("installa", BridgeGroup.APP, longRunning = true)),
    groups = BridgeGroup.entries,
    actionGroup = BridgeGroup.APP,
  )

  @Test
  fun catalogRoundTripsThroughJson() {
    val json = BridgeProtocol.catalogJson(registry, "dev.antigravity.classevivaexpressive", "cv", "ClasseViva Expressive", "7.4.0", "il registro", listOf("Matematica", "Storia"))
    val parsed = BridgeProtocol.catalogOf(BridgeProtocol.parse(json.toString()), "dev.antigravity.classevivaexpressive.ai.tools", "dev.antigravity.classevivaexpressive")!!
    assertEquals(BridgeProtocol.PROTOCOL, parsed.protocol)
    assertEquals("cv", parsed.domain)
    assertEquals("ClasseViva Expressive", parsed.appLabel)
    assertEquals(listOf("Matematica", "Storia"), parsed.vocabulary)
    assertEquals(1, parsed.categories.size)
    assertEquals("classeviva", parsed.categories.single().id)
    assertEquals(2, parsed.groups.size)
    val voti = parsed.groups.first { it.id == "voti" }
    assertTrue(voti.loadsWithCategory)
    assertEquals("classeviva", voti.category)
    assertTrue(parsed.groups.first { it.id == "app" }.action)
    assertEquals(3, parsed.tools.size)
    val vision = parsed.tools.first { it.name == "bacheca_presa_visione" }
    assertTrue(vision.needsConfirmation)
    assertTrue(vision.action)
    assertTrue(parsed.tools.first { it.name == "installa" }.longRunning)
    assertEquals("object", (vision.parameters["type"] as kotlinx.serialization.json.JsonPrimitive).content)
  }

  @Test
  fun runResultsParseInEveryShape() {
    val done = BridgeProtocol.resultOf(BridgeProtocol.parse(BridgeProtocol.resultResponse("media: 7.5", false, listOf(RemotePart("image", "content://x/1", "image/jpeg", "a.jpg"))).toString()))
    assertTrue(done is RemoteResult.Done)
    assertEquals("media: 7.5", (done as RemoteResult.Done).text)
    assertEquals(1, done.parts.size)
    assertEquals("a.jpg", done.parts.single().name)

    val job = BridgeProtocol.resultOf(BridgeProtocol.parse(BridgeProtocol.jobResponse("j1").toString()))
    assertEquals("j1", (job as RemoteResult.Job).jobId)

    val status = BridgeProtocol.resultOf(BridgeProtocol.parse(BridgeProtocol.statusResponse(BridgeProtocol.STATE_RUNNING, "scarico", 0.4, null, false, emptyList()).toString()))
    assertEquals(BridgeProtocol.STATE_RUNNING, (status as RemoteResult.Status).state)
    assertEquals(0.4, status.progress!!, 0.001)

    val error = BridgeProtocol.resultOf(BridgeProtocol.parse(BridgeProtocol.errorResponse("boom").toString()))
    assertEquals("boom", (error as RemoteResult.Error).message)
    assertNull(BridgeProtocol.resultOf(BridgeProtocol.parse("{}")))
    assertNull(BridgeProtocol.resultOf(BridgeProtocol.parse("non json")))
  }

  @Test
  fun newerProtocolIsRejectedByCatalogOfOnlyWhenMissing() {
    val json = buildJsonObject { put("appId", "x") }
    assertNull(BridgeProtocol.catalogOf(json, "a", "p"))
    val ok = buildJsonObject { put("protocol", 1); put("appId", "x") }
    val parsed = BridgeProtocol.catalogOf(ok, "a", "p")!!
    assertEquals("x", parsed.domain)
    assertTrue(parsed.tools.isEmpty())
  }

  @Test
  fun remoteToolSetPrefixesNamesAndKeepsHierarchy() {
    val json = BridgeProtocol.catalogJson(registry, "dev.antigravity.classevivaexpressive", "cv", "ClasseViva", "7.4.0", null, emptyList())
    val catalog = BridgeProtocol.catalogOf(BridgeProtocol.parse(json.toString()), "auth", "pkg")!!
    val host = object : RemoteToolHost<Unit> {
      override suspend fun confirm(ctx: Unit, toolName: String, text: dev.antigravity.fluidengine.ai.tools.ConfirmationText): ToolOutput? = null
    }
    val set = RemoteToolSet.of(FakeCalls, catalog, host)
    assertEquals(listOf("cv_voti", "cv_app"), set.groups.map { it.id })
    assertEquals("classeviva", set.groups.first().category?.id)
    assertEquals(setOf("cv_voti_media", "cv_bacheca_presa_visione", "cv_installa"), set.tools.map { it.name }.toSet())
    val vision = set.tools.first { it.name == "cv_bacheca_presa_visione" }
    assertTrue(vision.needsConfirmation)
    assertTrue(vision.description.endsWith("(ClasseViva)"))
    assertFalse(set.tools.first { it.name == "cv_voti_media" }.isAction)
  }
}

/** Le chiamate finte: RemoteToolSet non le tocca finche' non gira un tool. */
private object FakeCalls : BridgeCalls {
  override suspend fun describe(authority: String, name: String, args: JsonObject, language: String): Pair<String?, String?>? = null
  override suspend fun run(authority: String, name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): RemoteResult = RemoteResult.Done("eco", false, emptyList())
  override suspend fun status(authority: String, jobId: String): RemoteResult = RemoteResult.Error("nessun lavoro")
  override suspend fun cancel(authority: String, jobId: String) = Unit
  override fun readPart(part: RemotePart): ByteArray? = null
}
