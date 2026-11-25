package dev.antigravity.fluidengine.account

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Come un'app trova Pampa Store e decide se fidarsi: il controllo inverso della firma (minacce M1) e la precedenza fra
 * lo Store vero e quello di sviluppo (critica M9). Il `PackageManager` è finto ([FakeProbe]) perché quello di
 * Robolectric risponde sempre «firme uguali».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreLocatorTest {

  private class FakeProbe(
    val installed: Map<String, Bundle?> = emptyMap(),
    val signedByUs: Set<String> = emptySet(),
  ) : StoreLocator.Probe {
    val asked = mutableListOf<String>()
    override fun provider(packageName: String, authority: String): Bundle? {
      asked += packageName
      if (authority != packageName + ".account") return null
      return installed[packageName]
    }
    override fun sameSignature(packageName: String) = packageName in signedByUs
    override fun versionName(packageName: String) = "1.10.0"
  }

  private fun host() = Bundle().apply { putInt(AccountProtocol.META_HOST, 1) }
  private val config = AccountClientConfig()

  @Test
  fun `M9 - una build vera usa solo lo Store vero, anche se c'e' quello di sviluppo`() {
    val probe = FakeProbe(mapOf("com.pampa.store" to host(), "com.pampa.store.debug" to host()), setOf("com.pampa.store", "com.pampa.store.debug"))
    val found = StoreLocator(config, devBuild = false, probe).locate()!!
    assertEquals("com.pampa.store", found.packageName)
    assertEquals("com.pampa.store.account", found.authority)
    assertEquals(listOf("com.pampa.store"), probe.asked)
    // Con solo lo Store di sviluppo, una build vera non ha Store.
    assertNull(StoreLocator(config, false, FakeProbe(mapOf("com.pampa.store.debug" to host()), setOf("com.pampa.store.debug"))).locate())
  }

  @Test
  fun `M9 - una build di sviluppo prova prima lo Store di sviluppo, poi quello vero`() {
    val both = FakeProbe(mapOf("com.pampa.store" to host(), "com.pampa.store.debug" to host()), setOf("com.pampa.store", "com.pampa.store.debug"))
    assertEquals("com.pampa.store.debug", StoreLocator(config, true, both).locate()!!.packageName)
    val onlyReal = FakeProbe(mapOf("com.pampa.store" to host()), setOf("com.pampa.store"))
    assertEquals("com.pampa.store", StoreLocator(config, true, onlyReal).locate()!!.packageName)
  }

  @Test
  fun `M1 - un com_pampa_store firmato da altri per l'app non c'e'`() {
    val fake = FakeProbe(mapOf("com.pampa.store" to host()), signedByUs = emptySet())
    assertNull(StoreLocator(config, false, fake).locate())
    // E con lo Store di sviluppo vero e quello vero falso, una build di sviluppo prende quello di sviluppo.
    val mixed = FakeProbe(mapOf("com.pampa.store" to host(), "com.pampa.store.debug" to host()), setOf("com.pampa.store.debug"))
    assertEquals("com.pampa.store.debug", StoreLocator(config, true, mixed).locate()!!.packageName)
  }

  @Test
  fun `senza il meta-data dell'ospite non e' lo Store`() {
    val noMeta = FakeProbe(mapOf("com.pampa.store" to Bundle()), setOf("com.pampa.store"))
    assertNull(StoreLocator(config, false, noMeta).locate())
  }

  @Test
  fun `il cliente con uno Store che rifiuta la firma vede NoStore, senza Store anche, e non va mai in rete`() = runBlocking {
    val denying = PampaAccountClient({ StoreHost("com.pampa.store", "com.pampa.store.account", "1.0") }, { _, _, _ -> CallResult.Denied })
    assertEquals(AccountState.NoStore, denying.refresh())
    assertEquals(AppKeyResult.NoStore, denying.appKey(KeyPurpose.APP))
    val none = PampaAccountClient({ null }, { _, _, _ -> error("non deve chiamare") })
    assertEquals(AccountState.NoStore, none.refresh())
    assertTrue(none.token("notes", setOf("notes.sync")) is TokenResult.Unavailable)
    assertEquals(PlacesResult.NoStore, none.places())
    assertNull(none.storeIntent(AccountSections.ACCOUNT))
  }

  @Test
  fun `uno Store vecchio senza la capacita' dello stato`() = runBlocking {
    val old = PampaAccountClient({ StoreHost("com.pampa.store", "com.pampa.store.account", "0.9") }, { _, method, _ ->
      if (method == AccountProtocol.METHOD_NEGOTIATE) CallResult.Ok(buildJsonObject { put("protocol", 1); put("capabilities", JsonArray(listOf(JsonPrimitive("open")))) })
      else CallResult.Ok(JsonObject(emptyMap()))
    })
    assertEquals(AccountState.StoreTooOld("state"), old.refresh())
  }

  @Test
  fun `le iniziali del nome`() {
    assertEquals("AP", PampaAccountClient.initialsOf("Alessio Pampaloni"))
    assertEquals("A", PampaAccountClient.initialsOf("  alessio "))
    assertEquals("", PampaAccountClient.initialsOf(""))
  }
}
