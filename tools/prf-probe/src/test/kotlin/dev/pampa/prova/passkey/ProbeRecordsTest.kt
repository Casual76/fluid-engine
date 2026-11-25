package dev.pampa.prova.passkey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il registro della prova: il confronto fra i gestori, e nessun segreto nel riassunto. */
class ProbeRecordsTest {

  private val env = ProbeEnvironment(
    rpId = "prova.pampa-apps.com",
    app = "dev.pampa.prova.passkey 1.0",
    signingSha256 = "AA:BB",
    expectedSha256 = "aa:bb",
    origin = "android:apk-key-hash:qrs",
    device = "samsung SM-X710",
    android = "Android 16 (API 36)",
    unsupported = null,
  )

  private fun creation(id: String, manager: String, prfAtCreation: String?, synced: Boolean = true) = CreationRecord(
    credentialId = id, manager = manager, aaguid = null, prfEnabled = true, askedValue = true,
    prfAtCreation = prfAtCreation, backupEligible = synced, backedUp = synced, userVerified = true,
    userName = "Prova PRF", at = 0,
  )

  private fun access(id: String, prf: String?, synced: Boolean = true) =
    AccessRecord(id, prf, synced, synced, userVerified = true, at = 0)

  @Test
  fun iGestoriDallAaguid() {
    assertEquals("Gestore password di Google", KnownManagers.name("ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4", null))
    assertEquals("Samsung Pass", KnownManagers.name("53414d53-554e-4700-0000-000000000000", "Gestore password di Google"))
    assertEquals("Samsung Pass (AAGUID non dichiarato)", KnownManagers.name("00000000-0000-0000-0000-000000000000", "Samsung Pass"))
    assertTrue(KnownManagers.name("12345678-0000-0000-0000-000000000000", null).startsWith("Sconosciuto"))
  }

  @Test
  fun lImprontaNonEIlValore() {
    val prf = ByteArray(32) { it.toByte() }
    val print = prfFingerprint(prf)
    assertEquals(8, print.length)
    assertEquals(print, prfFingerprint(prf.copyOf()))
    assertFalse(print == prfFingerprint(ByteArray(32)))
  }

  @Test
  fun googleStabileESamsungSoloAllAccesso() {
    // Il caso del test di terzi (chiavi.md § 3.2): Samsung Pass dà il PRF solo all'accesso.
    val log = ProbeLog()
    log.creations += creation("g1", KnownManagers.GOOGLE, prfAtCreation = "11111111")
    log.accesses += access("g1", "11111111")
    log.accesses += access("g1", "11111111")
    log.creations += creation("s1", KnownManagers.SAMSUNG, prfAtCreation = null)
    log.accesses += access("s1", "22222222")
    log.accesses += access("s1", "22222222")

    val google = log.verdict(KnownManagers.GOOGLE)
    assertEquals(true, google.prfAtCreation)
    assertEquals(true, google.stableAcrossAccesses)
    assertEquals(true, google.sameAsCreation)
    assertEquals(true, google.doorPossible)
    val samsung = log.verdict(KnownManagers.SAMSUNG)
    assertEquals(false, samsung.prfAtCreation)
    assertEquals(true, samsung.prfAtAccess)
    assertNull(samsung.sameAsCreation)
    assertEquals(true, samsung.doorPossible)
    assertEquals(listOf("valore alla creazione: Google sì, Samsung Pass no", "uguale fra creazione e accesso: Google sì, Samsung Pass non provato"), google.differences(samsung))

    val text = log.summary(env)
    assertTrue(text.contains("== Differenze fra Google e Samsung Pass"))
    assertTrue(text.contains("PRF presente e stabile con: Gestore password di Google, Samsung Pass"))
    assertTrue(text.contains("= quella di assetlinks.json di prova"))
  }

  @Test
  fun unPrfCheCambiaChiudeLaPorta() {
    val log = ProbeLog()
    log.creations += creation("s1", KnownManagers.SAMSUNG, prfAtCreation = null)
    log.accesses += access("s1", "22222222")
    log.accesses += access("s1", "33333333")
    assertEquals(false, log.verdict(KnownManagers.SAMSUNG).stableAcrossAccesses)
    assertEquals(false, log.verdict(KnownManagers.SAMSUNG).doorPossible)
    assertTrue(log.summary(env).contains("PRF assente o instabile con: Samsung Pass"))
  }

  @Test
  fun senzaSincronizzazioneNonEUnaPorta() {
    // K2: la porta fuori dal telefono vuole una passkey sincronizzata.
    val log = ProbeLog()
    log.creations += creation("g1", KnownManagers.GOOGLE, prfAtCreation = "11111111", synced = false)
    log.accesses += access("g1", "11111111", synced = false)
    log.accesses += access("g1", "11111111", synced = false)
    assertEquals(false, log.verdict(KnownManagers.GOOGLE).doorPossible)
  }

  @Test
  fun unSoloAccessoNonBasta() {
    val log = ProbeLog()
    log.creations += creation("g1", KnownManagers.GOOGLE, prfAtCreation = "11111111")
    log.accesses += access("g1", "11111111")
    assertNull(log.verdict(KnownManagers.GOOGLE).stableAcrossAccesses)
    assertNull(log.verdict(KnownManagers.GOOGLE).doorPossible)
    assertTrue(log.summary(env).contains("stesso valore fra due accessi: serve un secondo accesso"))
  }

  @Test
  fun ilRegistroSiRilegge() {
    val log = ProbeLog(declaredManager = KnownManagers.SAMSUNG)
    log.creations += creation("s1", KnownManagers.SAMSUNG, prfAtCreation = null)
    log.accesses += access("s1", "22222222")
    log.failures += FailureRecord("accesso", "Operazione annullata.", "Cancelled", 0)
    val back = ProbeLog.fromJson(log.toJson())
    assertEquals(log.creations, back.creations)
    assertEquals(log.accesses, back.accesses)
    assertEquals(log.failures, back.failures)
    assertEquals(KnownManagers.SAMSUNG, back.declaredManager)
    assertEquals(0, ProbeLog.fromJson("rotto").creations.size)
  }

  @Test
  fun unaFirmaSbagliataSiVede() {
    val text = ProbeLog().summary(env.copy(signingSha256 = "CC:DD"))
    assertTrue(text.contains("ATTENZIONE: diversa da quella di assetlinks.json"))
  }
}
