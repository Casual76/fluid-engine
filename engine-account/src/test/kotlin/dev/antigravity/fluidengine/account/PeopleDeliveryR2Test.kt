package dev.antigravity.fluidengine.account

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R2 · «È arrivato qualcosa»: notifiche ripetute a ogni riavvio, e qualcuna si perde (`review\R2\muse-account.md`).
 * Il punto di consegna stava solo in memoria e confrontava solo l'ora; si segnava prima dei gestori.
 */
class PeopleDeliveryR2Test {
  /** Lo Store finto: la posta in arrivo di quest'app, con le ore che dà lo Store quando smista (spesso uguali). */
  private class FakeStore {
    val inbox = CopyOnWriteArrayList<Pair<String, Long>>()

    val client = PampaAccountClient({ StoreHost("com.pampa.store", "com.pampa.store.account", "1.10.0") }, { _, method, request ->
      when (method) {
        AccountProtocol.METHOD_NEGOTIATE -> CallResult.Ok(buildJsonObject { put("protocol", 1); put("capabilities", JsonArray(listOf("state", "people").map(::JsonPrimitive))) })
        AccountProtocol.METHOD_STATE -> CallResult.Ok(buildJsonObject { put("signedIn", true); put("accountId", "acc_1"); put("displayName", "Mamma") })
        AccountProtocol.METHOD_SHARE_RECEIVED -> {
          val since = (request["since"] as? JsonPrimitive)?.longOrNull ?: 0
          CallResult.Ok(buildJsonObject {
            put("ok", true)
            putJsonArray("items") {
              for ((id, at) in inbox) if (at > since) addJsonObject {
                put("id", id)
                putJsonObject("from") { put("id", "p1"); put("kind", "person"); put("name", "Alessio") }
                put("kind", "copy"); put("title", id); put("payload", "{}"); put("at", at)
              }
            }
          })
        }
        else -> CallResult.Ok(JsonObject(emptyMap()))
      }
    })
  }

  private class MemoryCache : AccountCache {
    val map = ConcurrentHashMap<String, String>()
    override suspend fun read(key: String): String? = map[key]
    override suspend fun write(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  @Test
  fun `un'app fatta ripartire non riceve di nuovo la posta di sempre`() = runBlocking {
    val store = FakeStore()
    val cache = MemoryCache()
    store.inbox += "lista" to 1_000L
    val got = CopyOnWriteArrayList<String>()
    val handler: suspend (List<SharedItem>) -> Unit = { items -> got += items.map { it.id } }
    PeopleDelivery(listOf(handler)).deliver(store.client, cache)
    assertEquals(listOf("lista"), got.toList())
    // Android uccide l'app; l'avviso dopo la fa ripartire (consegna nuova, stessa copia locale).
    store.inbox += "voto" to 2_000L
    PeopleDelivery(listOf(handler)).deliver(store.client, cache)
    assertEquals(listOf("lista", "voto"), got.toList())
  }

  @Test
  fun `due cose arrivate nello stesso millisecondo arrivano tutte e due`() = runBlocking {
    val store = FakeStore()
    val cache = MemoryCache()
    val got = CopyOnWriteArrayList<String>()
    val delivery = PeopleDelivery(listOf<suspend (List<SharedItem>) -> Unit>({ items -> got += items.map { it.id } }))
    store.inbox += "prima" to 5_000L
    delivery.deliver(store.client, cache)
    // Lo Store ha smistato in blocco: la seconda ha la stessa ora della prima.
    store.inbox += "seconda" to 5_000L
    delivery.deliver(store.client, cache)
    assertEquals(listOf("prima", "seconda"), got.toList())
    delivery.deliver(store.client, cache)
    assertEquals("niente doppioni", 2, got.size)
  }

  @Test
  fun `un gestore che cade non perde la cosa - torna all'avviso dopo`() = runBlocking {
    val store = FakeStore()
    val cache = MemoryCache()
    store.inbox += "invito" to 7_000L
    var fail = true
    val got = CopyOnWriteArrayList<String>()
    val delivery = PeopleDelivery(listOf<suspend (List<SharedItem>) -> Unit>({ items ->
      check(!fail) { "la notifica non parte" }
      got += items.map { it.id }
    }))
    delivery.deliver(store.client, cache)
    assertEquals(0, got.size)
    fail = false
    delivery.deliver(store.client, cache)
    assertEquals(listOf("invito"), got.toList())
  }
}
