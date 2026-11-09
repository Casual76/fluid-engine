package dev.antigravity.fluidengine.ai.bridge

import dev.antigravity.fluidengine.ai.tools.*
import kotlinx.serialization.json.*

/** Wire codec kept separate from the legacy text/parts envelope. Unknown outcomes fail closed. */
internal object TypedResultCodec {
  fun encode(r: ToolResultDetails): JsonObject = buildJsonObject {
    put("outcome", r.outcome.name.lowercase())
    r.data?.let { put("data", it) }
    put("entities", buildJsonArray { r.entities.forEach { e -> add(buildJsonObject { put("source", e.source); put("type", e.type); put("id", e.id); e.uri?.let { put("uri", it) } }) } })
    r.operation?.let { o -> put("operation", buildJsonObject { put("id", o.id); o.source?.let { put("source", it) } }) }
    put("sources", buildJsonArray { r.sources.forEach { s -> add(buildJsonObject { put("id", s.id); s.uri?.let { put("uri", it) }; s.account?.let { put("account", it) } }) } })
    r.coverage?.let { c -> put("coverage", buildJsonObject { c.complete?.let { put("complete", it) }; c.from?.let { put("from", it) }; c.to?.let { put("to", it) } }) }
    r.freshness?.let { f -> put("freshness", buildJsonObject { f.observedAtMillis?.let { put("observedAtMillis", it) }; f.syncedAtMillis?.let { put("syncedAtMillis", it) }; f.stale?.let { put("stale", it) } }) }
    r.pagination?.let { p -> put("pagination", buildJsonObject { p.nextCursor?.let { put("nextCursor", it) }; p.hasMore?.let { put("hasMore", it) }; p.total?.let { put("total", it) } }) }
    r.error?.let { e -> put("error", buildJsonObject { put("code", e.code); put("message", e.message); put("retryable", e.retryable) }) }
  }
  fun decode(j: JsonObject?): ToolResultDetails? {
    j ?: return null
    val outcome = ToolOutcome.entries.firstOrNull { it.name.equals(j.s("outcome"), true) } ?: ToolOutcome.UNCERTAIN
    return ToolResultDetails(outcome, j["data"] as? JsonObject,
      (j["entities"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { e -> ToolEntityRef(e.s("source") ?: return@mapNotNull null, e.s("type") ?: return@mapNotNull null, e.s("id") ?: return@mapNotNull null, e.s("uri")) } },
      (j["operation"] as? JsonObject)?.let { ToolOperationRef(it.s("id") ?: return@let null, it.s("source")) },
      (j["sources"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { s -> ToolSourceRef(s.s("id") ?: return@mapNotNull null, s.s("uri"), s.s("account")) } },
      (j["coverage"] as? JsonObject)?.let { ToolCoverage(it.b("complete"), it.s("from"), it.s("to")) },
      (j["freshness"] as? JsonObject)?.let { ToolFreshness(it.l("observedAtMillis"), it.l("syncedAtMillis"), it.b("stale")) },
      (j["pagination"] as? JsonObject)?.let { ToolPagination(it.s("nextCursor"), it.b("hasMore"), it.l("total")) },
      (j["error"] as? JsonObject)?.let { ToolError(it.s("code") ?: "unknown", it.s("message") ?: "", it.b("retryable") ?: false) })
  }
  private fun JsonObject.s(k: String) = (get(k) as? JsonPrimitive)?.contentOrNull
  private fun JsonObject.b(k: String) = (get(k) as? JsonPrimitive)?.booleanOrNull
  private fun JsonObject.l(k: String) = (get(k) as? JsonPrimitive)?.longOrNull
}
