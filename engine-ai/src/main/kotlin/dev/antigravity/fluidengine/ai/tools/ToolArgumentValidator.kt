package dev.antigravity.fluidengine.ai.tools

import kotlinx.serialization.json.*

/** Validates the structural JSON Schema subset used by tools. Unknown annotations are ignored;
 * numeric/boolean strings accepted by Args remain accepted. Never substitutes missing arguments. */
object ToolArgumentValidator {
  fun error(args: JsonObject, schema: JsonObject): String? {
    if (args.keys == setOf("_raw")) return "malformed JSON arguments"
    return check(args, schema, "args", schema)
  }

  private fun check(value: JsonElement, schema: JsonObject, path: String, root: JsonObject, depth: Int = 0, tolerant: Boolean = true): String? {
    if (depth > 64) return "$path: schema nesting limit"
    (schema["\$ref"] as? JsonPrimitive)?.content?.let { ref ->
      if (!ref.startsWith("#/")) return "$path: unsupported schema reference"
      var target: JsonElement? = root
      for (part in ref.removePrefix("#/").split('/')) target = (target as? JsonObject)?.get(part.replace("~1", "/").replace("~0", "~"))
      val resolved = target as? JsonObject ?: return "$path: unresolved schema reference"
      check(value, resolved, path, root, depth + 1, tolerant)?.let { return it }
    }
    for (key in listOf("allOf", "anyOf", "oneOf")) {
      val branches = (schema[key] as? JsonArray)?.filterIsInstance<JsonObject>() ?: continue
      // Prefer strict JSON types for oneOf: tolerant string/number coercion must not make
      // an otherwise valid union match two branches.
      val strictMatched = if (key == "oneOf") branches.count { check(value, it, path, root, depth + 1, false) == null } else 0
      val matched = if (strictMatched > 0) strictMatched else branches.count { check(value, it, path, root, depth + 1, tolerant) == null }
      if ((key == "allOf" && matched != branches.size) || (key == "anyOf" && matched == 0) || (key == "oneOf" && matched != 1)) return "$path: $key mismatch"
    }
    val types = when (val type = schema["type"]) {
      is JsonPrimitive -> listOf(type.content)
      is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.content }
      else -> emptyList()
    }
    val p = value as? JsonPrimitive
    fun matches(type: String): Boolean = when (type) {
      "null" -> value == JsonNull
      "object" -> value is JsonObject
      "array" -> value is JsonArray
      "string" -> p != null && p != JsonNull && (p.isString || tolerant && (p.booleanOrNull != null || p.doubleOrNull != null))
      "number" -> p != null && p != JsonNull && (tolerant || !p.isString) && p.doubleOrNull?.isFinite() == true
      "integer" -> p != null && p != JsonNull && (tolerant || !p.isString) && p.doubleOrNull?.let { it.isFinite() && it % 1.0 == 0.0 } == true
      "boolean" -> p != null && p != JsonNull && (tolerant || !p.isString) && p.booleanOrNull != null
      else -> true
    }
    if (types.isNotEmpty() && types.none(::matches)) return "$path: expected ${types.joinToString("/")}"
    val allowed = schema["enum"] as? JsonArray
    if (allowed != null && allowed.none { it == value || (it is JsonPrimitive && p != null && it.content == p.content) }) return "$path: value outside enum"
    schema["const"]?.let { if (it != value) return "$path: const mismatch" }
    if (value is JsonObject) {
      val required = (schema["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
      required.firstOrNull { it !in value }?.let { return "$path.$it: required" }
      val props = schema["properties"] as? JsonObject
      for ((key, item) in value) {
        val child = props?.get(key) as? JsonObject
        if (child != null) check(item, child, "$path.$key", root, depth + 1, tolerant)?.let { return it }
        else if ((schema["patternProperties"] as? JsonObject)?.keys?.any { pattern -> runCatching { Regex(pattern).containsMatchIn(key) }.getOrDefault(false) } == true) {
          for ((pattern, patternSchema) in schema["patternProperties"] as JsonObject) {
            if (runCatching { Regex(pattern).containsMatchIn(key) }.getOrDefault(false) && patternSchema is JsonObject)
              check(item, patternSchema, "$path.$key", root, depth + 1, tolerant)?.let { return it }
          }
        }
        else if (schema["additionalProperties"] == JsonPrimitive(false)) return "$path.$key: unknown property"
        else (schema["additionalProperties"] as? JsonObject)?.let { check(item, it, "$path.$key", root, depth + 1, tolerant) }?.let { return it }
      }
    }
    if (value is JsonArray) {
      val min = (schema["minItems"] as? JsonPrimitive)?.intOrNull
      val max = (schema["maxItems"] as? JsonPrimitive)?.intOrNull
      if (min != null && value.size < min || max != null && value.size > max) return "$path: array size"
      (schema["items"] as? JsonObject)?.let { child -> value.forEachIndexed { i, item -> check(item, child, "$path[$i]", root, depth + 1, tolerant)?.let { return it } } }
    }
    p?.doubleOrNull?.let { n ->
      (schema["minimum"] as? JsonPrimitive)?.doubleOrNull?.let { if (n < it) return "$path: below minimum" }
      (schema["maximum"] as? JsonPrimitive)?.doubleOrNull?.let { if (n > it) return "$path: above maximum" }
    }
    return null
  }
}
