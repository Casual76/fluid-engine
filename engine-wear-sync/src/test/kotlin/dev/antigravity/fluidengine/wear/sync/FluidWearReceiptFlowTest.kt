package dev.antigravity.fluidengine.wear.sync

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F8.25 · Dal polso con ricevuta (`wear-con-ricevuta`): il polso salva col telefono lontano, l'evento resta «In coda»
 * anche dopo un riavvio, arriva quando i due si ritrovano, il telefono lo salva una volta sola anche se arriva due volte,
 * e il polso passa a «Registrato» solo con la ricevuta. Gli eventi a vita corta scadono. Il trasporto è finto (un
 * collegamento che si accende e si spegne), il resto è vero, coi file su disco.
 */
class FluidWearReceiptFlowTest {

  @get:Rule val folder = TemporaryFolder()

  private var now = 1_000_000L
  private val clock = { now }
  private var ids = 0

  private fun watch(dir: File) = FluidWearSync(FluidWearRole.Watch, FluidWearFileStore(dir), clock) { "ev-${++ids}" }
  private fun phone(dir: File) = FluidWearSync(FluidWearRole.Phone, FluidWearFileStore(dir), clock)

  /**
   * Il trasporto finto: come il Data Layer, tiene gli elementi messi col collegamento spento e li consegna quando si
   * riaccende. Un elemento rimesso identico non si riconsegna.
   */
  private class Link {
    var connected = false
    private val toPhone = LinkedHashMap<String, ByteArray>()
    private val toWatch = LinkedHashMap<String, ByteArray>()
    private val delivered = HashMap<String, ByteArray>()

    fun put(fromWatch: Boolean, path: String, bytes: ByteArray) {
      if (delivered[path]?.contentEquals(bytes) == true) return
      (if (fromWatch) toPhone else toWatch)[path] = bytes
    }

    fun drain(fromWatch: Boolean): List<Pair<String, ByteArray>> {
      if (!connected) return emptyList()
      val queue = if (fromWatch) toPhone else toWatch
      val out = queue.entries.map { it.key to it.value }
      out.forEach { (p, b) -> delivered[p] = b }
      queue.clear()
      return out
    }
  }

  /** Il telefono che salva: un elenco, e il registro del ponte. */
  private class PhoneApp(val sync: FluidWearSync, val link: Link) {
    val saved = ArrayList<FluidWearEvent>()

    fun pump() {
      for ((path, bytes) in link.drain(fromWatch = true)) {
        when (val r = sync.route(path, bytes)) {
          is FluidWearRouted.EventArrived -> {
            saved += r.event
            val receipt = sync.inbox.confirm(r.event.id)
            link.put(false, FluidWearPaths.receipt(receipt.eventId), FluidWearFormat.encode(receipt))
          }
          is FluidWearRouted.ReceiptToSend -> link.put(false, FluidWearPaths.receipt(r.receipt.eventId), FluidWearFormat.encode(r.receipt))
          else -> Unit
        }
      }
    }
  }

  private fun flush(watch: FluidWearSync, link: Link, resendAll: Boolean = false) {
    for (o in watch.outbox.due(resendAll)) {
      link.put(true, o.path, o.bytes())
      watch.outbox.markSent(o.event.id)
    }
  }

  private fun deliverToWatch(watch: FluidWearSync, link: Link): List<FluidWearRouted> =
    link.drain(fromWatch = false).map { (p, b) -> watch.route(p, b) }

  @Test
  fun `col telefono lontano resta In coda, anche dopo un riavvio, poi diventa Registrato`() {
    val watchDir = folder.newFolder("orologio")
    val link = Link()
    val phone = PhoneApp(phone(folder.newFolder("telefono")), link)

    var watch = watch(watchDir)
    val event = watch.outbox.enqueue("preferito", """{"brano":"42"}""")
    flush(watch, link)
    assertEquals(FluidWearDeliveryState.Queued, watch.outbox.get(event.id)?.state)
    phone.pump()
    assertTrue("col collegamento spento il telefono non riceve niente", phone.saved.isEmpty())

    // L'orologio si riavvia: la coda viene dal disco.
    now += 60_000
    watch = watch(watchDir)
    assertEquals(FluidWearDeliveryState.Queued, watch.outbox.get(event.id)?.state)
    assertEquals(1, watch.outbox.get(event.id)?.attempts)

    link.connected = true
    phone.pump()
    assertEquals(listOf(event), phone.saved)
    val routed = deliverToWatch(watch, link)
    assertEquals(FluidWearDeliveryState.Registered, (routed.single() as FluidWearRouted.DeliverySettled).delivery.state)
    assertEquals("Registrato", watch.outbox.get(event.id)?.state?.label)
    // E resta Registrato dopo un altro riavvio.
    assertEquals(FluidWearDeliveryState.Registered, watch(watchDir).outbox.get(event.id)?.state)
  }

  @Test
  fun `una consegna ripetuta non si salva due volte e riceve la stessa ricevuta`() {
    val link = Link().apply { connected = true }
    val phoneSync = phone(folder.newFolder("telefono"))
    val phone = PhoneApp(phoneSync, link)
    val watch = watch(folder.newFolder("orologio"))

    val event = watch.outbox.enqueue("giro", "5 km")
    flush(watch, link)
    phone.pump()
    val first = phoneSync.inbox.receiptFor(event.id)
    // Il polso non ha visto la ricevuta e, passato il tempo, riconsegna (attempt nuovo: byte nuovi).
    now += FluidWearOutbox.DefaultResendAfterMillis
    assertEquals(1, watch.outbox.due().size)
    flush(watch, link)
    phone.pump()
    assertEquals("salvato una volta sola", 1, phone.saved.size)
    assertEquals(first, phoneSync.inbox.receiptFor(event.id))

    // Anche un telefono riavviato riconosce la ripetizione: il registro e' su disco.
    val restarted = phone(File(folder.root, "telefono"))
    assertTrue(restarted.route(FluidWearPaths.event(event.id), FluidWearFormat.encode(event, 7)) is FluidWearRouted.ReceiptToSend)

    // Due ricevute uguali al polso: la seconda non cambia niente.
    val receipts = deliverToWatch(watch, link)
    assertEquals(1, receipts.count { it is FluidWearRouted.DeliverySettled })
    val again = watch.route(FluidWearPaths.receipt(event.id), FluidWearFormat.encode(first!!))
    assertTrue(again is FluidWearRouted.Ignored)
  }

  @Test
  fun `un evento a vita corta scade al polso e non si consegna piu'`() {
    val link = Link()
    val watch = watch(folder.newFolder("orologio"))
    val play = watch.outbox.enqueue("riproduci", ttlMillis = 30_000)
    flush(watch, link)
    now += 31_000
    assertTrue(watch.outbox.due(resendAll = true).isEmpty())
    assertEquals(FluidWearDeliveryState.Expired, watch.outbox.get(play.id)?.state)
    assertEquals("Scaduto", watch.outbox.get(play.id)?.state?.label)
  }

  @Test
  fun `un evento scaduto che arriva al telefono non si applica e torna Scaduto`() {
    val link = Link()
    val phoneSync = phone(folder.newFolder("telefono"))
    val phone = PhoneApp(phoneSync, link)
    val watch = watch(folder.newFolder("orologio"))
    val play = watch.outbox.enqueue("riproduci", ttlMillis = 30_000)
    flush(watch, link)
    // Il telefono torna vicino dopo 40 s: l'elemento era gia' sul Data Layer.
    now += 40_000
    link.connected = true
    phone.pump()
    assertTrue(phone.saved.isEmpty())
    assertEquals(FluidWearReceiptOutcome.Expired, phoneSync.inbox.receiptFor(play.id)?.outcome)
    deliverToWatch(watch, link)
    assertEquals(FluidWearDeliveryState.Expired, watch.outbox.get(play.id)?.state)
    assertTrue(watch.outbox.get(play.id)!!.confirmedByPhone)
  }

  @Test
  fun `se il telefono l'aveva salvato in tempo, la sua ricevuta vince sulla scadenza del polso`() {
    val link = Link().apply { connected = true }
    val phoneSync = phone(folder.newFolder("telefono"))
    val phone = PhoneApp(phoneSync, link)
    val watch = watch(folder.newFolder("orologio"))
    val event = watch.outbox.enqueue("salta", ttlMillis = 30_000)
    flush(watch, link)
    phone.pump()
    // La ricevuta tarda: intanto il polso decide da se' che e' scaduto.
    now += 31_000
    watch.outbox.expire()
    assertEquals(FluidWearDeliveryState.Expired, watch.outbox.get(event.id)?.state)
    deliverToWatch(watch, link)
    assertEquals(FluidWearDeliveryState.Registered, watch.outbox.get(event.id)?.state)
  }

  @Test
  fun `un evento di una versione futura riceve Non registrato col suo id`() {
    val phoneSync = phone(folder.newFolder("telefono"))
    val future = FluidWearFormat.encode(FluidWearEvent("ev-9", "nuovo", "", now)).also { it[3] = 2 }
    val routed = phoneSync.route(FluidWearPaths.event("ev-9"), future) as FluidWearRouted.ReceiptToSend
    assertEquals(FluidWearReceiptOutcome.Refused, routed.receipt.outcome)
    assertEquals("ev-9", routed.receipt.eventId)
  }

  @Test
  fun `ogni lato tratta solo i suoi messaggi`() {
    val watch = watch(folder.newFolder("orologio"))
    val phone = phone(folder.newFolder("telefono"))
    val event = watch.outbox.enqueue("preferito")
    // L'orologio che si vede tornare il suo evento non lo «riceve».
    assertTrue(watch.route(FluidWearPaths.event(event.id), FluidWearFormat.encode(event)) is FluidWearRouted.Ignored)
    val status = FluidWearStatus("posta", "Posta", "3", updatedAtMillis = now)
    assertTrue(phone.route(FluidWearPaths.status("posta"), FluidWearFormat.encode(status)) is FluidWearRouted.Ignored)
    // Un messaggio all'indirizzo di un altro non passa.
    assertTrue(watch.route(FluidWearPaths.status("altro"), FluidWearFormat.encode(status)) is FluidWearRouted.Ignored)
  }

  @Test
  fun `gli eventi chiusi si tolgono dopo un giorno, quelli in coda restano`() {
    val link = Link().apply { connected = true }
    val phone = PhoneApp(phone(folder.newFolder("telefono")), link)
    val watch = watch(folder.newFolder("orologio"))
    val done = watch.outbox.enqueue("a")
    flush(watch, link)
    phone.pump()
    deliverToWatch(watch, link)
    link.connected = false
    val waiting = watch.outbox.enqueue("b")
    now += FluidWearOutbox.DefaultKeepSettledMillis
    watch.outbox.prune()
    assertNull(watch.outbox.get(done.id))
    assertEquals(FluidWearDeliveryState.Queued, watch.outbox.get(waiting.id)?.state)
  }

  @Test
  fun `una coda illeggibile su disco riparte vuota senza fermare l'orologio`() = runTest {
    val dir = folder.newFolder("orologio")
    File(dir, "coda.fws").writeBytes(byteArrayOf(0, 0, 0, 1, 0x7F, 0x7F, 0x7F, 0x7F))
    val watch = watch(dir)
    assertTrue(watch.outbox.deliveries.value.isEmpty())
    watch.outbox.enqueue("dopo")
    assertEquals(1, watch(dir).outbox.deliveries.value.size)
  }

  @Test
  fun `il registro del telefono con handle salva prima e conferma dopo`() = runTest {
    val phoneSync = phone(folder.newFolder("telefono"))
    val event = FluidWearEvent("ev-1", "preferito", "", now)
    var saves = 0
    val receipt = phoneSync.inbox.handle(event) { saves++; true }
    assertEquals(FluidWearReceiptOutcome.Registered, receipt.outcome)
    assertEquals(receipt, phoneSync.inbox.handle(event) { saves++; true })
    assertEquals(1, saves)
    // Un salvataggio che lancia non lascia ricevuta: il polso riconsegnera'.
    val broken = FluidWearEvent("ev-2", "preferito", "", now)
    runCatching { phoneSync.inbox.handle(broken) { error("disco pieno") } }
    assertNull(phoneSync.inbox.receiptFor("ev-2"))
    // Un tipo che l'app non conosce: «Non registrato».
    assertEquals(FluidWearReceiptOutcome.Refused, phoneSync.inbox.handle(FluidWearEvent("ev-3", "boh", "", now)) { false }.outcome)
  }
}
