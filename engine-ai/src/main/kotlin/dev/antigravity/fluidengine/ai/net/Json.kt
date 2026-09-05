package dev.antigravity.fluidengine.ai.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Navigazione JSON senza modelli: ogni campo mancante e' una decisione
 * esplicita, non una eccezione.
 *
 * I provider IA cambiano forma spesso e in modo non annunciato — un campo in
 * piu', un campo che diventa nullo, un dialetto diverso fra Groq e Gemini.
 * Con dei modelli tipizzati ogni scostamento sarebbe un crash; cosi' e' un
 * `null` che il chiamante decide come trattare.
 */
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

fun JsonElement?.at(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()

fun JsonElement?.double(): Double? = (this as? JsonPrimitive)?.doubleOrNull

fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull
