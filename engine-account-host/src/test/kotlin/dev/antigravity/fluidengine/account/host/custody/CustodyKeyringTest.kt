package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaRecordCipher
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * La custodia sul telefono nelle due classi (`b\chiavi.md` § 3.3), con la cassaforte finta che sa fare il telefono
 * bloccato e la chiave persa. Voci 7 e 13 del banco di § 11, e la domanda Q7 provata nelle sue tre risposte.
 */
class CustodyKeyringTest {
  @get:Rule val tmp = TemporaryFolder()

  private val pampai = WorkKeyGrant("dev.pampa.pampai", setOf(AppKeyPurpose.APP, AppKeyPurpose.BACKUP))
  private val weather = WorkKeyGrant("dev.pampa.fluidweather", setOf(AppKeyPurpose.BACKUP))

  private fun started(): Triple<FakeKeysServer, KeyCustody, RecoveryCode> {
    val server = FakeKeysServer()
    val code = RecoveryCode.generate()
    val start = KeyCustody.start(server.accountId, listOf(DoorSecret.recovery(code)))
    server.postRecovery(code.auth, start.upload.doors.single())
    server.postEpoch(start.upload.epoch)
    return Triple(server, start.custody, code)
  }

  @Test
  fun `la custodia si chiude e si riapre uguale`() {
    val (server, custody, _) = started()
    val vault = MemoryCustodyVault()
    val ring = CustodyKeyring(vault, tmp.root)
    ring.saveCustody(custody, listOf(pampai))
    val back = ring.loadCustody()!!
    assertEquals(custody.epochs, back.epochs)
    assertArrayEquals(custody.cd(1), back.cd(1))
    assertEquals(custody.knownDoors, back.knownDoors)
    back.verify(server.view())
  }

  @Test
  fun `13 - con Q7 A il sottofondo lavora a telefono bloccato e non apre mai la madre`() {
    val (_, custody, _) = started()
    val vault = MemoryCustodyVault()
    val ring = CustodyKeyring(vault, tmp.root, KeyClassPolicy.Choice.A)
    ring.saveCustody(custody, listOf(pampai, weather))
    val motherBefore = vault.opened[KeyClass.MOTHER] ?: 0
    vault.locked = true
    // Il lavoro in sottofondo: le chiavi di un'app (appKey), quelle del backup, la rubrica per aprire una busta.
    val app = ring.workKeys("dev.pampa.pampai", AppKeyPurpose.APP)!!
    val backup = ring.workKeys("dev.pampa.fluidweather", AppKeyPurpose.BACKUP)!!
    assertNotNull(ring.sharedKeys("rubrica"))
    assertArrayEquals(custody.appKeys("dev.pampa.pampai", AppKeyPurpose.APP).keys.getValue(1), app.keys.getValue(1))
    assertArrayEquals(custody.appKeys("dev.pampa.fluidweather", AppKeyPurpose.BACKUP).keys.getValue(1), backup.keys.getValue(1))
    // Una riga scritta in tasca si apre dopo.
    val field = PampaRecordCipher(custody.accountId, "dev.pampa.pampai").seal(app.keys.getValue(app.current), app.current, "c", "r", 1, "in tasca".toByteArray())
    assertEquals(motherBefore, vault.opened[KeyClass.MOTHER] ?: 0)
    // K3: niente chiavi per chi non è nella tabella, né per uno scopo che non ha.
    assertNull(ring.workKeys("com.p2r3.convert", AppKeyPurpose.APP))
    assertNull(ring.workKeys("dev.pampa.fluidweather", AppKeyPurpose.APP))
    // La madre resta chiusa: le operazioni rare aspettano lo sblocco.
    assertTrue(runCatching { ring.loadCustody() }.exceptionOrNull() is CustodyLockedException)
    vault.locked = false
    val keys = ring.loadCustody()!!.appKeys("dev.pampa.pampai", AppKeyPurpose.APP).keys
    assertEquals("in tasca", PampaRecordCipher(custody.accountId, "dev.pampa.pampai").open({ keys[it] }, "c", "r", 1, field).decodeToString())
  }

  @Test
  fun `13 - con Q7 B o C le chiavi di lavoro a telefono bloccato restano chiuse`() {
    for (choice in listOf(KeyClassPolicy.Choice.B, KeyClassPolicy.Choice.C)) {
      val (_, custody, _) = started()
      val vault = MemoryCustodyVault()
      val ring = CustodyKeyring(vault, tmp.newFolder(), choice)
      ring.saveCustody(custody, listOf(pampai))
      vault.locked = true
      assertTrue(choice.name, runCatching { ring.workKeys("dev.pampa.pampai", AppKeyPurpose.APP) }.exceptionOrNull() is CustodyLockedException)
      assertFalse(KeyClassPolicy.workKeysInBackground(choice))
    }
    assertTrue(KeyClassPolicy.workKeysInBackground(KeyClassPolicy.Choice.A))
    // La chiave del dispositivo (il rinnovo) resta in consegna con A e B; con C anche lei è madre.
    assertEquals(KeyClass.DELIVERY, KeyClassPolicy.classOf(CustodyKeyKind.DEVICE_KEY, KeyClassPolicy.Choice.B))
    assertEquals(KeyClass.MOTHER, KeyClassPolicy.classOf(CustodyKeyKind.DEVICE_KEY, KeyClassPolicy.Choice.C))
    // La raccomandazione A è quella di serie.
    assertEquals(KeyClassPolicy.Choice.A, KeyClassPolicy.CHOICE)
  }

  @Test
  fun `prima del primo sblocco non si apre niente`() {
    val (_, custody, _) = started()
    val vault = MemoryCustodyVault()
    val ring = CustodyKeyring(vault, tmp.root)
    ring.saveCustody(custody, listOf(pampai))
    vault.beforeFirstUnlock = true
    assertTrue(runCatching { ring.workKeys("dev.pampa.pampai", AppKeyPurpose.APP) }.exceptionOrNull() is CustodyLockedException)
  }

  @Test
  fun `7 - la chiave del Keystore invalidata - lo Store se ne accorge e riapre da una porta`() {
    val (server, custody, code) = started()
    val vault = MemoryCustodyVault()
    val ring = CustodyKeyring(vault, tmp.root)
    ring.saveCustody(custody, listOf(pampai))
    vault.invalidate()
    assertTrue(runCatching { ring.loadCustody() }.exceptionOrNull() is CustodyKeyLostException)
    assertTrue(runCatching { ring.workKeys("dev.pampa.pampai", AppKeyPurpose.APP) }.exceptionOrNull() is CustodyKeyLostException)
    // Riapre dalla porta del codice e richiude con chiavi nuove del Keystore.
    val reopened = KeyCustody.openWithDoor(server.accountId, server.view(), DoorSecret.recovery(code))
    ring.saveCustody(reopened, listOf(pampai))
    assertArrayEquals(custody.cd(1), ring.loadCustody()!!.cd(1))
    assertNotNull(ring.workKeys("dev.pampa.pampai", AppKeyPurpose.APP))
  }

  @Test
  fun `5 - l'uscita cancella file e chiavi`() {
    val (_, custody, _) = started()
    val vault = MemoryCustodyVault()
    val ring = CustodyKeyring(vault, tmp.root)
    ring.saveCustody(custody, listOf(pampai))
    assertTrue(ring.hasCustody)
    ring.wipe()
    assertFalse(ring.hasCustody)
    assertNull(ring.loadCustody())
    assertNull(ring.workKeys("dev.pampa.pampai", AppKeyPurpose.APP))
    assertTrue(tmp.root.listFiles()!!.isEmpty())
  }

  @Test
  fun `il codice di recupero - formato, lettura con gli errori di chi scrive a mano, e mai stampato`() {
    val code = RecoveryCode.generate()
    assertTrue(code.display.matches(Regex("[A-Z2-7]{5}(-[A-Z2-7]{5}){4}-[A-Z2-7]")))
    assertEquals(code, RecoveryCode.parse(code.display))
    assertEquals(code, RecoveryCode.parse(code.display.lowercase().replace("-", "  ")))
    val withLookalikes = code.display.replace('O', '0').replace('I', '1').replace('B', '8')
    assertEquals(code, RecoveryCode.parse(withLookalikes))
    assertNull(RecoveryCode.parse(code.display.dropLast(1)))
    assertNull(RecoveryCode.parse(code.display + "A"))
    // L'ultimo carattere porta 3 bit: uno con gli ultimi due bit accesi non è un codice.
    assertNull(RecoveryCode.parse(code.display.dropLast(1) + "7"))
    assertFalse(code.toString().contains(code.display.take(5)))
    assertEquals(43, code.auth.length)
    // Lo stesso formato del telefono finto del server: 16 byte, base32 maiuscolo, gruppi da 5.
    val known = RecoveryCode.fromBytes(ByteArray(16) { it.toByte() })
    assertEquals("AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB-4", known.display)
  }
}
