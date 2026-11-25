package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.WorkKeyGrant
import dev.antigravity.fluidengine.account.host.custody.resource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tabella delle app sul telefono è quella del contratto (`apps.json`), e le varianti di sviluppo contano solo nello Store di sviluppo. */
class AppPolicyTest {

  @Test
  fun `la tabella e' quella del contratto v1`() {
    val contract = Json.parseToJsonElement(resource("contract/apps.json")).jsonObject
    val apps = contract["apps"]!!.jsonObject
    assertEquals(apps.keys, AppPolicy.DEFAULT.keys)
    for ((pkg, raw) in apps) {
      val o = raw.jsonObject
      val mine = AppPolicy.DEFAULT.getValue(pkg)
      assertEquals(pkg, o["name"]!!.jsonPrimitive.content, mine.name)
      val audiences = o["audiences"]!!.jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content }.toSet() }
      assertEquals(pkg, audiences, mine.audiences)
      assertEquals(pkg, o["googleServices"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), mine.googleServices)
    }
    assertEquals(listOf(AppPolicy.STORE, AppPolicy.STORE_DEBUG), contract["storePackages"]!!.jsonArray.map { it.jsonPrimitive.content })
    assertEquals(AppPolicy.DEV_SUFFIXES, contract["devSuffixes"]!!.jsonArray.map { it.jsonPrimitive.content })
  }

  @Test
  fun `le varianti di sviluppo solo nello Store di sviluppo, e MyCloud Gallery mai i luoghi`() {
    assertNull(AppPolicy(devBuild = false).entryFor("dev.pampa.fluidify.debug"))
    assertEquals("dev.pampa.fluidify.debug", AppPolicy(devBuild = true).entryFor("dev.pampa.fluidify.debug")!!.packageName)
    assertNull(AppPolicy(devBuild = true).entryFor("com.estranea.debug"))
    assertEquals(AppPolicy.Places.NEVER, AppPolicy().entryFor("dev.pampa.mycloudgallery")!!.places)
    assertEquals(AppPolicy.Places.ASK, AppPolicy().entryFor("dev.antigravity.fluidtransit")!!.places)
    assertTrue(AppPolicy().allows("dev.pampa.pampanotes", "notes", setOf("notes.sync", "notes.share")))
    assertFalse(AppPolicy().allows("dev.pampa.pampanotes", "notes", setOf("notes.admin")))
  }

  @Test
  fun `le chiavi di lavoro solo per chi sincronizza o fa il backup`() {
    val grants = AppPolicy().workKeyGrants().associateBy { it.packageName }
    assertEquals(WorkKeyGrant("dev.pampa.pampai", setOf(AppKeyPurpose.APP, AppKeyPurpose.BACKUP)), grants["dev.pampa.pampai"])
    assertEquals(setOf(AppKeyPurpose.BACKUP), grants["dev.pampa.fluidweather"]!!.purposes)
    assertNull(grants["com.p2r3.convert"])
    assertNull(grants["dev.pampa.pampanotes"])
    assertEquals(listOf("dev.pampa.pampai"), AppPolicy().workKeyGrants(installed = listOf("dev.pampa.pampai", "com.p2r3.convert")).map { it.packageName })
  }
}
