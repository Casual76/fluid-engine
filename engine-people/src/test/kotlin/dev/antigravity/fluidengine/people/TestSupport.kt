package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Le risorse delle prove: i vettori e la copia del contratto. */
object TestResources {
  fun text(path: String): String =
    requireNotNull(TestResources::class.java.classLoader!!.getResourceAsStream(path)) { "manca la risorsa $path" }.use { it.readBytes().decodeToString() }

  fun json(path: String): JsonObject = Json.parseToJsonElement(text(path)).jsonObject
}

fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun b64(s: String): ByteArray = PampaBase64.decode(s)
fun JsonObject.str(k: String): String = (this[k] as JsonPrimitive).content
fun JsonObject.obj(k: String): JsonObject = this[k]!!.jsonObject
