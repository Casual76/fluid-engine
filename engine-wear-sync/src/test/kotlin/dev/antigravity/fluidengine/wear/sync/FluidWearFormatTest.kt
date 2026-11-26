package dev.antigravity.fluidengine.wear.sync

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.25 · Il formato versionato: andata e ritorno, i campi nuovi saltati, le versioni future riconosciute. */
class FluidWearFormatTest {

  private val status = FluidWearStatus(
    key = "lezione",
    title = "Matematica",
    value = "10:00",
    detail = "Aula 3 · fra 12 min",
    progress = 0.4f,
    updatedAtMillis = 1_764_000_000_000L,
    staleAfterMillis = 1_764_000_900_000L,
    openUri = "classeviva://agenda/oggi",
  )

  private val event = FluidWearEvent(
    id = "e-1",
    type = "preferito",
    payload = """{"brano":"42","titolo":"Così è"}""",
    createdAtMillis = 1_764_000_000_000L,
    expiresAtMillis = 1_764_000_030_000L,
  )

  @Test
  fun `stato, evento e ricevuta tornano uguali`() {
    assertEquals(FluidWearDecoded.Status(status), FluidWearFormat.decode(FluidWearFormat.encode(status)))
    val minimal = FluidWearStatus(key = "k", title = "t", value = "v", updatedAtMillis = 5)
    assertEquals(FluidWearDecoded.Status(minimal), FluidWearFormat.decode(FluidWearFormat.encode(minimal)))
    assertEquals(FluidWearDecoded.Event(event, attempt = 3), FluidWearFormat.decode(FluidWearFormat.encode(event, attempt = 3)))
    for (outcome in FluidWearReceiptOutcome.entries) {
      val receipt = FluidWearReceipt("e-1", outcome, 7)
      assertEquals(FluidWearDecoded.Receipt(receipt), FluidWearFormat.decode(FluidWearFormat.encode(receipt)))
    }
  }

  @Test
  fun `il numero della consegna cambia i byte, non l'evento`() {
    val first = FluidWearFormat.encode(event, attempt = 0)
    val second = FluidWearFormat.encode(event, attempt = 1)
    assertTrue(!first.contentEquals(second))
    assertEquals(event, (FluidWearFormat.decode(second) as FluidWearDecoded.Event).event)
  }

  @Test
  fun `un campo che questa versione non conosce si salta`() {
    val bytes = FluidWearFormat.encode(status) + field(tag = 99, value = "un campo di domani".toByteArray())
    assertEquals(FluidWearDecoded.Status(status), FluidWearFormat.decode(bytes))
  }

  @Test
  fun `una versione futura non si interpreta, ma l'id dell'evento si legge`() {
    val future = FluidWearFormat.encode(event).also { it[3] = 2 }
    assertEquals(FluidWearDecoded.Unsupported(version = 2, eventId = "e-1"), FluidWearFormat.decode(future))
    val futureStatus = FluidWearFormat.encode(status).also { it[3] = 9 }
    assertEquals(FluidWearDecoded.Unsupported(version = 9, eventId = null), FluidWearFormat.decode(futureStatus))
  }

  @Test
  fun `i byte rotti non lanciano`() {
    assertTrue(FluidWearFormat.decode(ByteArray(0)) is FluidWearDecoded.Unreadable)
    assertTrue(FluidWearFormat.decode("ciao, non sono FWS".toByteArray()) is FluidWearDecoded.Unreadable)
    val cut = FluidWearFormat.encode(status).let { it.copyOf(it.size - 3) }
    assertTrue(FluidWearFormat.decode(cut) is FluidWearDecoded.Unreadable)
    // Un evento senza il tipo (campo obbligatorio) e' illeggibile, non un evento a meta'.
    val header = FluidWearFormat.encode(event).copyOf(5)
    val noType = header + field(1, "e-1".toByteArray())
    assertTrue(FluidWearFormat.decode(noType) is FluidWearDecoded.Unreadable)
  }

  @Test
  fun `una ricevuta con un esito sconosciuto non diventa Registrato`() {
    val header = FluidWearFormat.encode(FluidWearReceipt("e-1", FluidWearReceiptOutcome.Registered, 1)).copyOf(5)
    val bytes = header + field(1, "e-1".toByteArray()) + field(2, int(42)) + field(3, long(1))
    val decoded = FluidWearFormat.decode(bytes) as FluidWearDecoded.Receipt
    assertEquals(FluidWearReceiptOutcome.Refused, decoded.receipt.outcome)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `un contenuto oltre il tetto non entra`() {
    FluidWearEvent("e", "t", "x".repeat(FluidWearEvent.MaxPayloadBytes + 1), 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `una chiave con la barra non entra (sarebbe un altro indirizzo)`() {
    FluidWearStatus(key = "a/b", title = "t", value = "v", updatedAtMillis = 0)
  }

  private fun field(tag: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer ->
    DataOutputStream(buffer).apply {
      writeByte(tag)
      writeInt(value.size)
      write(value)
    }
  }.toByteArray()

  private fun int(v: Int) = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(v) }.toByteArray()
  private fun long(v: Long) = ByteArrayOutputStream().also { DataOutputStream(it).writeLong(v) }.toByteArray()
}
