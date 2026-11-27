package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.backup.BackupFile
import dev.antigravity.fluidengine.account.backup.BackupPackage
import dev.antigravity.fluidengine.account.backup.PampaBackupFormat
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I formati del contratto `e2e.json` dopo le porte dei dispositivi (Pampa-account `05500e5`): i tipi di porta, la porta
 * del dispositivo, la testa `PBK1` del backup e il QR del passaggio sono quelli del codice del telefono.
 */
class ContractFormatsTest {
  private val contract = Json.parseToJsonElement(resource("contract/e2e.json")).jsonObject

  @Test
  fun `i tipi di porta e l'id della porta del dispositivo`() {
    assertEquals(contract["doorKinds"]!!.jsonArray.map { it.jsonPrimitive.content }, DoorKind.entries.map { it.wire })
    assertEquals("device:<deviceId>", contract["deviceDoor"]!!.jsonObject["doorId"]!!.jsonPrimitive.content)
    assertEquals("device:dev_x", DoorSecret.deviceDoorId("dev_x"))
  }

  @Test
  fun `la testa del backup e' PBK1, epoca big-endian, nonce, testo cifrato, al massimo 5 MB`() {
    val backup = contract["formats"]!!.jsonObject["backup"]!!.jsonObject
    assertEquals("PBK1", backup["magic"]!!.jsonPrimitive.content)
    assertEquals(PampaBackupFormat.MAX_BYTES, backup["maxBytes"]!!.jsonPrimitive.long)
    val blob = PampaBackupFormat.seal(BackupPackage("a", "1", 1, 0, listOf(BackupFile("f", byteArrayOf(1)))), PampaCrypto.random(32), 0x01020304, "acc", 1)
    assertEquals("PBK1", blob.copyOfRange(0, 4).decodeToString())
    assertEquals(listOf<Byte>(1, 2, 3, 4), blob.copyOfRange(4, 8).toList())
    assertEquals(0x01020304, PampaBackupFormat.epochOf(blob))
  }

  @Test
  fun `il QR del passaggio - v, transferId, N di 32 byte, s di 16`() {
    val qr = contract["formats"]!!.jsonObject["transferQr"]!!.jsonObject
    assertEquals(setOf("v", "transferId", "N", "s"), qr["json"]!!.jsonObject.keys)
    assertEquals(32, qr["nBytes"]!!.jsonPrimitive.int)
    assertEquals(16, qr["sBytes"]!!.jsonPrimitive.int)
    val mine = Json.parseToJsonElement(TransferOffer.create("xfr_1").qr).jsonObject
    assertEquals(setOf("v", "transferId", "N", "s"), mine.keys)
    assertEquals(32, PampaBase64.decode(mine["N"]!!.jsonPrimitive.content).size)
    assertEquals(16, PampaBase64.decode(mine["s"]!!.jsonPrimitive.content).size)
    // F10.7b · Il ritiro si ripete finché il telefono nuovo non conferma: la rotta della conferma è quella di CustodyService.
    val pickup = qr["pickup"]!!.jsonPrimitive.content
    assertTrue(pickup, "POST /v1/keys/transfers/{id}/received" in pickup)
    assertTrue(pickup, "state sealed" in pickup)
  }
}
