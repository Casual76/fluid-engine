package dev.antigravity.fluidengine.ai.tools

import dev.antigravity.fluidengine.ai.provider.ToolCallAssembler
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ToolContractsTest {
  private val schema = Schema.obj(mapOf("id" to Schema.str("id"), "page" to Schema.int("page", 0), "enabled" to Schema.bool("enabled")), listOf("id"))
  @Test fun malformedAndMissingArgumentsAreRejected() {
    listOf("{", "[]", "null", "{id:bad}", "{}").forEach {
      assertNotNull(it, ToolArgumentValidator.error(ToolCallAssembler.parseArguments(it), schema))
    }
  }
  @Test fun tolerantExistingArgsAndZeroBasedPagesRemainValid() {
    assertNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"id":123,"page":"0","enabled":"true","extension":1}""") as JsonObject, schema))
    assertNotNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"id":"a","page":-1}""") as JsonObject, schema))
    assertNotNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"id":"a","page":1.5}""") as JsonObject, schema))
  }
  @Test fun nestedRequiredAndNullableAlternatives() {
    val nested = Json.parseToJsonElement("""{"type":"object","required":["item"],"properties":{"item":{"anyOf":[{"type":"null"},{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}]}}}""") as JsonObject
    assertNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"item":null}""") as JsonObject, nested))
    assertNotNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"item":{}}""") as JsonObject, nested))
  }
  @Test fun localReferencesEnforceRequiredAndUnionTypesDoNotConflictWithCoercion() {
    val referenced = Json.parseToJsonElement("""{"type":"object","properties":{"item":{"REF":"#/definitions/item"}},"definitions":{"item":{"type":"object","required":["id"]}}}""".replace("REF", "\$ref")) as JsonObject
    assertNotNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"item":{}}""") as JsonObject, referenced))
    val union = Json.parseToJsonElement("""{"properties":{"id":{"oneOf":[{"type":"string"},{"type":"integer"}]}}}""") as JsonObject
    assertNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"id":42}""") as JsonObject, union))
    assertNull(ToolArgumentValidator.error(Json.parseToJsonElement("""{"id":"42"}""") as JsonObject, union))
  }
  @Test fun truncationRetainsLongFirstLineWithinEveryLimit() {
    for (limit in listOf(0, 1, 10, 100, 2400)) {
      val out = ToolText.limit("valuable:" + "x".repeat(5000), limit)
      assertTrue(out.length <= limit)
      if (limit >= 10) assertTrue(out.startsWith("valuable:"))
    }
  }
  @Test fun legacyOutputHasNoInventedOutcome() {
    assertNull(ToolOutput("done", emptyList(), true).result)
    assertEquals(ToolOutcome.ERROR, ToolOutput.error("failure").result?.outcome)
    assertFalse(ToolOutput.error("failure").result!!.error!!.retryable)
  }
}
