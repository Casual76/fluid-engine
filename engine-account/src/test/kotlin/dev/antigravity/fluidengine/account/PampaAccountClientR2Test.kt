package dev.antigravity.fluidengine.account

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2 (review `muse-account`): l'uscita da remoto segnata «già fatta» prima di cancellare, e le risposte storte dello
 * Store che facevano cadere l'app. Lo Store qui è un trasporto finto che risponde quello che gli si dice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PampaAccountClientR2Test {

  private class MemoryCache : AccountCache {
    val map = HashMap<String, String>()
    override suspend fun read(key: String) = map[key]
    override suspend fun write(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  private fun client(state: () -> JsonObject, cache: AccountCache?, answers: Map<String, JsonObject> = emptyMap()) = PampaAccountClient(
    { StoreHost("com.pampa.store", "com.pampa.store.account", "1.10.0") },
    { _, method, _ ->
      when (method) {
        AccountProtocol.METHOD_NEGOTIATE -> CallResult.Ok(buildJsonObject {
          putJsonArray("capabilities") { for (c in listOf("state", "appKey")) add(JsonPrimitive(c)) }
        })
        AccountProtocol.METHOD_STATE -> CallResult.Ok(state())
        else -> answers[method]?.let { CallResult.Ok(it) } ?: CallResult.Failed("nessuna risposta")
      }
    },
    cache,
    elapsed = { 0L },
  )

  private val exited = buildJsonObject {
    put("signedIn", false)
    put("remoteExitAt", 1_764_000_000_000)
  }

  @Test
  fun `un lavoro di wiped che cade - l'uscita non risulta gestita, e al riavvio si rifa'`() = runBlocking {
    val cache = MemoryCache()
    val first = client({ exited }, cache)
    first.refresh()
    var tries = 0
    first.onWiped { tries++; throw IOException("il disco è pieno, o il processo muore a metà") }
    first.onChanged(listOf("state", "wiped"))
    assertEquals(1, tries)
    // L'app riparte (un processo nuovo con la stessa copia locale): alla prima lettura dallo Store si cancella.
    val second = client({ exited }, cache)
    var wiped = 0
    second.onWiped { wiped++ }
    second.refresh()
    assertEquals("le copie si cancellano al primo contatto", 1, wiped)
    // E una volta sola: finito, è segnato.
    second.onChanged(listOf("wiped"))
    second.refresh()
    assertEquals(1, wiped)
  }

  @Test
  fun `risposte storte dello Store - un esito, mai un crash`() = runBlocking {
    val signedIn = buildJsonObject {
      put("signedIn", true)
      put("accountId", "acc_1")
      put("displayName", "Alessio")
    }
    val c = client({ signedIn }, null, mapOf(
      AccountProtocol.METHOD_APP_KEY to buildJsonObject { put("ok", true); put("current", 1); put("keys", "strano") },
      AccountProtocol.METHOD_TOKEN to buildJsonObject { put("ok", true); put("expiresIn", 900) },
      AccountProtocol.METHOD_PLACES_LIST to buildJsonObject { put("ok", true); putJsonArray("places") { add(buildJsonObject { put("id", 1) }) } },
      AccountProtocol.METHOD_GOOGLE to buildJsonObject { put("ok", true) },
      AccountProtocol.METHOD_BACKUP_AVAILABLE to buildJsonObject { putJsonArray("items") { add(JsonPrimitive("x")) } },
      AccountProtocol.METHOD_GRANTS to buildJsonObject { putJsonArray("grants") { add(buildJsonObject { put("state", "granted") }) } },
    ))
    c.refresh()
    assertEquals(AppKeyResult.NoStore, c.appKey(KeyPurpose.APP))
    assertTrue(c.token("sync", setOf("sync.data")) is TokenResult.Unavailable)
    assertEquals(PlacesResult.NoStore, c.places())
    assertEquals(GoogleTokenResult.NoStore, c.google("drive"))
    assertEquals(emptyList<BackupInfo>(), c.availableBackups())
    assertEquals(emptySet<String>(), c.refreshGrants())
  }

  @Test
  fun `chiavi dell'app con un'epoca corrente che non c'e' - non le si usa`() = runBlocking {
    val signedIn = buildJsonObject { put("signedIn", true) }
    val c = client({ signedIn }, null, mapOf(
      AccountProtocol.METHOD_APP_KEY to buildJsonObject {
        put("ok", true)
        put("current", 2)
        putJsonArray("keys") { add(buildJsonObject { put("epoch", 1); put("key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") }) }
      },
    ))
    c.refresh()
    assertEquals(AppKeyResult.NoStore, c.appKey(KeyPurpose.APP))
  }
}
