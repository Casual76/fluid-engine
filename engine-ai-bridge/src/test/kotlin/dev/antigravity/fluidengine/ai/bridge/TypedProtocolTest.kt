package dev.antigravity.fluidengine.ai.bridge

import dev.antigravity.fluidengine.ai.tools.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TypedProtocolTest {
  private val details = ToolResultDetails(ToolOutcome.PARTIAL,
    data = buildJsonObject { put("count", 3) },
    entities = listOf(ToolEntityRef("source", "item", "42", "content://source/42")),
    operation = ToolOperationRef("stable", "source"), sources = listOf(ToolSourceRef("source", account = "account")),
    coverage = ToolCoverage(false, "start", "end"), freshness = ToolFreshness(10, 5, true),
    pagination = ToolPagination("next", true, 20), error = ToolError("incomplete", "More pages", true))
  @Test fun oldClientNewHostKeepsLegacyCatalogAndEnvelope() {
    val group = object : AiToolGroup { override val id = "g"; override val statusKey = "g"; override val hint = "g" }
    val registry = ToolRegistry<Unit>(emptyList(), listOf(group))
    val catalog = BridgeProtocol.catalogJson(registry, "app", "app", "App", "1", null, emptyList())
    assertEquals(JsonPrimitive(1), catalog["protocol"])
    val legacy = BridgeProtocol.resultResponse("partial", false, emptyList())
    assertEquals(legacy, BridgeProtocol.withResult(legacy, ToolOutput("partial", result = details), null))
    assertNull((BridgeProtocol.resultOf(legacy) as RemoteResult.Done).result)
  }
  @Test fun maxCommonVersionAndCapabilitiesAreNegotiated() {
    val agreement = BridgeProtocol.negotiate(BridgeProtocol.negotiationRequest())
    assertEquals(JsonPrimitive(2), agreement["protocol"])
    assertTrue(BridgeProtocol.typedRequested(agreement))
    val v1 = BridgeProtocol.negotiate(Json.parseToJsonElement("""{"versions":[1],"capabilities":["typed_results"]}""") as JsonObject)
    assertEquals(JsonPrimitive(1), v1["protocol"])
    assertFalse(BridgeProtocol.typedRequested(v1))
    assertNotNull(BridgeProtocol.negotiate(Json.parseToJsonElement("""{"versions":[99]}""") as JsonObject)["error"])
  }
  @Test fun newClientNewHostRetainsEvidenceOnRunAndStatus() {
    val agreement = BridgeProtocol.negotiate(BridgeProtocol.negotiationRequest())
    val output = ToolOutput("partial", result = details)
    val done = BridgeProtocol.withResult(BridgeProtocol.resultResponse(output.text, false, emptyList()), output, agreement)
    assertEquals(details, (BridgeProtocol.resultOf(done) as RemoteResult.Done).result)
    val status = BridgeProtocol.withResult(BridgeProtocol.statusResponse("done", null, 1.0, output.text, false, emptyList()), output, agreement)
    assertEquals(details, (BridgeProtocol.resultOf(status) as RemoteResult.Status).result)
  }
  @Test fun oldHostNegotiationFallbackHasNoExtendedCapabilities() {
    assertEquals(BridgeNegotiation(), BridgeProtocol.agreementOf(BridgeProtocol.errorResponse("unknown method")))
    assertEquals(BridgeNegotiation(), BridgeProtocol.agreementOf(null))
    val agreed = BridgeProtocol.agreementOf(BridgeProtocol.negotiate(BridgeProtocol.negotiationRequest()))
    assertEquals(2, agreed.protocol)
    assertEquals(BridgeProtocol.CAPABILITIES, agreed.capabilities)
  }
  @Test fun malformedEnvelopeIsNeverRepairedIntoExecutableArguments() {
    listOf("{", "{name:write,args:{}}", "[]", "null").forEach { assertNull(BridgeProtocol.parse(it)) }
  }
  @Test fun unknownFutureOutcomeCannotBecomeCompleted() {
    val future = Json.parseToJsonElement("""{"text":"done","result":{"outcome":"future"}}""") as JsonObject
    assertEquals(ToolOutcome.UNCERTAIN, (BridgeProtocol.resultOf(future) as RemoteResult.Done).result?.outcome)
  }
}
