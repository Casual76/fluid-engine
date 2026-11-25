package dev.antigravity.fluidengine.account.host.sync

import dev.antigravity.fluidengine.account.host.AppearanceStore
import dev.antigravity.fluidengine.account.host.PrefRow
import dev.antigravity.fluidengine.account.host.PrefsContract
import dev.antigravity.fluidengine.account.host.PrefsHlc
import dev.antigravity.fluidengine.account.host.custody.resource
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F10.8 · Il tema fra i dispositivi (`b\architettura.md` § 5.2): per ogni campo vince l'orologio logico ibrido più
 * grande, anche con gli orologi dei telefoni sfasati. Due dispositivi finti e il server finto con le regole di
 * `choices.ts`.
 */
class AppearanceSyncTest {
  @get:Rule val tmp = TemporaryFolder()

  private fun pair(server: FakeSharedServer, skewA: Long = 0, skewB: Long = 0) =
    server.Device("a", "acc_1", null, tmp.newFolder(), skewA) to server.Device("b", "acc_1", null, tmp.newFolder(), skewB)

  @Test
  fun `le chiavi del telefono sono quelle del contratto del server`() {
    val contract = Json.parseToJsonElement(resource("contract/prefs.json")).jsonObject
    val keys = (contract["accepted"] as JsonArray).map { it.jsonObject }
    assertEquals(keys.map { it["key"]!!.jsonPrimitive.content }.toSet(), PrefsContract.PREFS.keys)
    for (k in keys) {
      val name = k["key"]!!.jsonPrimitive.content
      val spec = PrefsContract.PREFS.getValue(name)
      assertEquals(name, k["scopes"]!!.jsonPrimitive.content, spec.scopes)
      val value = k["value"]!!.jsonObject
      when (val t = spec.type) {
        is PrefsContract.Type.Enum -> assertEquals(name, (value["values"] as JsonArray).map { it.jsonPrimitive.content }, t.values)
        is PrefsContract.Type.Number -> {
          assertEquals(value["min"]!!.jsonPrimitive.doubleOrNull, t.min)
          assertEquals(value["max"]!!.jsonPrimitive.doubleOrNull, t.max)
          assertEquals(value["step"]!!.jsonPrimitive.doubleOrNull, t.step)
        }
        is PrefsContract.Type.Color -> {
          assertEquals("color", value["type"]!!.jsonPrimitive.content)
          assertEquals(name, (value["keywords"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(), t.keywords)
        }
        PrefsContract.Type.Bool -> assertEquals("bool", value["type"]!!.jsonPrimitive.content)
      }
    }
    // Lo stesso giudizio del server su qualche caso al limite.
    assertNull(PrefsContract.check("appearance.textScale", "global", JsonPrimitive(1.35)))
    assertEquals("bad_value", PrefsContract.check("appearance.textScale", "global", JsonPrimitive(1.33)))
    assertEquals("bad_value", PrefsContract.check("appearance.mode", "global", JsonPrimitive("sepia")))
    assertEquals("scope_not_allowed", PrefsContract.check("appearance.textScale", "dev.pampa.pampai", JsonPrimitive(1.2)))
    assertNull(PrefsContract.check("appearance.mode", "dev.pampa.pampai", null))
    assertEquals("bad_value", PrefsContract.check("appearance.mode", "global", null))
    // F10.8b: l'accento «app» e «system», e il vetro come numero a passi di 0,01.
    assertNull(PrefsContract.check("appearance.accent", "global", JsonPrimitive("app")))
    assertNull(PrefsContract.check("appearance.accent", "dev.pampa.fluidify", JsonPrimitive("system")))
    assertEquals("bad_value", PrefsContract.check("appearance.accent", "global", JsonPrimitive("arcobaleno")))
    assertNull(PrefsContract.check("appearance.glass", "global", JsonPrimitive(0.35)))
    assertEquals("bad_value", PrefsContract.check("appearance.glass", "global", JsonPrimitive(0.355)))
    assertEquals("bad_value", PrefsContract.check("appearance.glass", "global", JsonPrimitive("tinted")))
  }

  @Test
  fun `l'orologio logico nel formato del server, e ritorno`() {
    val h = dev.antigravity.fluidengine.sync.SyncHlc(1_764_000_000_123L, 7, "ab12cd34ef56")
    val text = PrefsHlc.format(h)
    assertEquals("1764000000123-00007-ab12cd34ef56", text)
    assertEquals(h, PrefsHlc.parse(text))
    assertNull(PrefsHlc.parse("1764000000123-7-ab12"))
  }

  @Test
  fun `un cambio fatto su un dispositivo arriva sull'altro, e le app lo sanno`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    a.appearance.setGlobal(mapOf("appearance.mode" to "dark", "appearance.glass" to "0.35", "appearance.textScale" to "1.25", "appearance.accent" to "app"))
    assertTrue(a.appearanceSync.sync() is StoreSyncResult.Ok)
    assertEquals("dark", server.pref("global", "appearance.mode"))
    b.notified.clear()
    val r = b.appearanceSync.sync() as StoreSyncResult.Ok
    assertTrue(r.changed)
    assertEquals("dark", b.appearance.snapshot().global["appearance.mode"])
    assertEquals(0.35, b.appearance.snapshot().global["appearance.glass"]!!.toDouble(), 1e-9)
    assertEquals("app", b.appearance.snapshot().global["appearance.accent"])
    assertEquals(1.25, b.appearance.snapshot().global["appearance.textScale"]!!.toDouble(), 1e-9)
    assertEquals(listOf(listOf("appearance")), b.notified)
    // Rileggere non cambia niente e non avvisa di nuovo.
    b.notified.clear()
    assertFalse((b.appearanceSync.sync() as StoreSyncResult.Ok).changed)
    assertTrue(b.notified.isEmpty())
  }

  @Test
  fun `conflitto - due dispositivi offline cambiano lo stesso campo, vince la modifica piu recente, su tutti e due`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    server.offline = true
    a.appearance.setGlobal(mapOf("appearance.mode" to "dark", "icon.set" to "tinted"))
    server.now += 60_000
    b.appearance.setGlobal(mapOf("appearance.mode" to "light"))
    assertEquals(StoreSyncResult.Offline, a.appearanceSync.sync())
    server.offline = false
    // Torna prima la rete al più recente: il vecchio, arrivato dopo, non lo scavalca.
    b.appearanceSync.sync()
    a.appearanceSync.sync()
    b.appearanceSync.sync()
    for (d in listOf(a, b)) {
      assertEquals(d.name, "light", d.appearance.snapshot().global["appearance.mode"])
      // Campi diversi non si toccano: le icone di A restano.
      assertEquals(d.name, "tinted", d.appearance.snapshot().global["icon.set"])
    }
    assertEquals("light", server.pref("global", "appearance.mode"))
  }

  @Test
  fun `orologi sfasati - chi sceglie dopo aver visto vince, anche col telefono indietro di tre ore`() = runBlocking {
    val server = FakeSharedServer()
    val threeHours = 3 * 3_600_000L
    // A è avanti di tre ore, B indietro di tre: senza l'orologio logico, A vincerebbe sempre.
    val (a, b) = pair(server, skewA = threeHours, skewB = -threeHours)
    a.appearance.setGlobal(mapOf("appearance.mode" to "dark"))
    a.appearanceSync.sync()
    server.now += 1_000
    b.appearanceSync.sync()
    assertEquals("dark", b.appearance.snapshot().global["appearance.mode"])
    b.appearance.setGlobal(mapOf("appearance.mode" to "light"))
    assertTrue(b.appearance.lastHlc > PrefsHlc.parse(server.prefHlc("global", "appearance.mode"))!!)
    b.appearanceSync.sync()
    a.appearanceSync.sync()
    assertEquals("light", a.appearance.snapshot().global["appearance.mode"])
    assertEquals("light", server.pref("global", "appearance.mode"))
  }

  @Test
  fun `orologio avanti di due giorni - il server rifiuta, il telefono si corregge e rimanda`() = runBlocking {
    val server = FakeSharedServer()
    val (a, _) = pair(server, skewA = 2 * 86_400_000L)
    a.appearance.setGlobal(mapOf("appearance.mode" to "dark"))
    val future = a.appearance.lastHlc
    val first = a.appearanceSync.sync() as StoreSyncResult.Ok
    // Il primo invio torna `bad_hlc`; la riga rinasce all'ora del server e il secondo giro passa.
    assertTrue(first.rejected.isEmpty())
    assertEquals(1, first.pushed)
    assertEquals("dark", server.pref("global", "appearance.mode"))
    assertTrue(a.appearance.pending().isEmpty())
    val stamped = PrefsHlc.parse(server.prefHlc("global", "appearance.mode"))!!
    assertTrue(stamped < future)
    assertTrue(stamped.millis <= server.now + 1_000)
    // E le scelte dopo nascono all'ora del server, non più nel futuro.
    a.appearance.setGlobal(mapOf("appearance.mode" to "light"))
    assertTrue(a.appearance.lastHlc.millis <= server.now + 1_000)
    assertTrue((a.appearanceSync.sync() as StoreSyncResult.Ok).rejected.isEmpty())
    assertEquals("light", server.pref("global", "appearance.mode"))
  }

  @Test
  fun `l'eccezione di un'app - tolta su un dispositivo, vince solo se e la scelta piu recente`() = runBlocking {
    val server = FakeSharedServer()
    val (a, b) = pair(server)
    val weather = "dev.pampa.fluidweather"
    a.appearance.setException(weather, followGlobal = false, override = mapOf("appearance.mode" to "light"))
    a.appearanceSync.sync()
    b.appearanceSync.sync()
    assertEquals(mapOf("appearance.mode" to "light"), b.appearance.snapshot().overrides[weather])
    assertEquals(false, b.appearance.snapshot().follow[weather])
    // B toglie l'eccezione (lapide), poi A, che non l'ha vista, ne mette una nuova più tardi: vince quella di A.
    server.now += 1_000
    b.appearance.setException(weather, followGlobal = true, override = null)
    server.now += 1_000
    a.appearance.setException(weather, followGlobal = false, override = mapOf("appearance.mode" to "dark"))
    b.appearanceSync.sync()
    a.appearanceSync.sync()
    b.appearanceSync.sync()
    for (d in listOf(a, b)) {
      assertEquals(d.name, mapOf("appearance.mode" to "dark"), d.appearance.snapshot().overrides[weather])
      assertEquals(d.name, false, d.appearance.snapshot().follow[weather])
    }
    // Ora la tolgono di nuovo, ed è la più recente: la lapide vince e non resta niente.
    server.now += 1_000
    b.appearance.setException(weather, followGlobal = true, override = null)
    b.appearanceSync.sync()
    a.appearanceSync.sync()
    assertNull(a.appearance.snapshot().overrides[weather])
    assertEquals(true, a.appearance.snapshot().follow[weather])
  }

  @Test
  fun `una chiave sconosciuta o un valore sbagliato non nascono nemmeno, e niente va senza account`() = runBlocking {
    val server = FakeSharedServer()
    val store = AppearanceStore(tmp.newFolder())
    store.setGlobal(mapOf("chiave.nuova" to "x", "appearance.mode" to "sepia", "appearance.textScale" to "1.33", "appearance.accent" to "#00AAFF"))
    assertEquals(mapOf("appearance.accent" to "#00AAFF"), store.snapshot().global)
    assertEquals(listOf("appearance.accent"), store.pending().map { it.key })
    // Il server annuncia solo alcune chiavi: lo Store manda solo quelle (critica M1).
    store.setAccepted(setOf("appearance.mode"))
    assertTrue(store.pending().isEmpty())
    val (a, _) = pair(server)
    a.session.signOut()
    assertEquals(StoreSyncResult.SignedOut, a.appearanceSync.sync())
  }

  @Test
  fun `il file di F10_4 si legge ancora, coi valori da mandare`() {
    val dir = tmp.newFolder()
    File(dir, "aspetto.json").writeText("""{"version":3,"global":{"appearance.mode":"dark","appearance.textScale":"1.2"},"overrides":{"dev.pampa.pampai":{"appearance.mode":"light"}},"follow":{"dev.pampa.pampai":false}}""")
    val store = AppearanceStore(dir)
    val s = store.snapshot()
    assertEquals("dark", s.global["appearance.mode"])
    assertEquals(mapOf("appearance.mode" to "light"), s.overrides["dev.pampa.pampai"])
    assertEquals(false, s.follow["dev.pampa.pampai"])
    assertEquals(4, store.pending().size)
    // Una riga arrivata dal server con qualunque orologio vince su quelle senza orologio.
    assertTrue(store.merge(listOf(PrefRow("global", "appearance.mode", JsonPrimitive("light"), "0000000000001-00000-zz"))))
    assertEquals("light", store.snapshot().global["appearance.mode"])
    assertNotNull(store.responseFor("dev.pampa.pampai")["override"] as? JsonObject)
  }
}
