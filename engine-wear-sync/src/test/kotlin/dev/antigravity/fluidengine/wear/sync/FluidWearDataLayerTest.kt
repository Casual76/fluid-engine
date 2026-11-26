package dev.antigravity.fluidengine.wear.sync

import android.net.Uri
import android.os.Looper
import com.google.android.gms.common.api.Releasable
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataItemAsset
import com.google.android.gms.wearable.PutDataRequest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F8.25 · L'adattatore del Data Layer: le richieste sono urgenti, all'indirizzo giusto e coi byte del formato nel campo
 * `fws`; gli eventi di `onDataChanged` si smistano col ruolo del lato. Senza Play services vero (nessun telefono e nessun
 * orologio al PC): le classi del Data Layer si costruiscono davvero, il trasporto no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidWearDataLayerTest {

  private val status = FluidWearStatus("posta", "Posta", "3", detail = "non lette", updatedAtMillis = 42)

  @Test
  fun `la richiesta e' urgente, all'indirizzo dello stato, coi byte del formato`() {
    val request = FluidWearDataLayer.requestFor(FluidWearPaths.status("posta"), FluidWearFormat.encode(status))
    assertEquals("/fluid-wear/status/posta", request.uri.path)
    assertTrue(request.isUrgent)
    val bytes = FluidWearDataLayer.bytesOf(FakeItem(request.uri, request.data))
    assertArrayEquals(FluidWearFormat.encode(status), bytes)
  }

  @Test
  fun `l'indirizzo per tutti i dispositivi non ha nodo`() {
    val uri = FluidWearDataLayer.wildcard(FluidWearPaths.StatusPrefix)
    assertEquals(PutDataRequest.WEAR_URI_SCHEME, uri.scheme)
    assertTrue(uri.host.isNullOrEmpty())
    assertEquals("/fluid-wear/status/", uri.path)
  }

  @Test
  fun `l'orologio tiene lo stato arrivato e lo toglie quando viene cancellato`() {
    val sync = FluidWearSync(FluidWearRole.Watch, FluidWearMemoryStore())
    val request = FluidWearDataLayer.requestFor(FluidWearPaths.status("posta"), FluidWearFormat.encode(status))
    val routed = handle(sync, listOf(FakeEvent(DataEvent.TYPE_CHANGED, FakeItem(request.uri, request.data))))
    assertEquals(listOf(FluidWearRouted.StatusChanged(status)), routed)
    val gone = handle(sync, listOf(FakeEvent(DataEvent.TYPE_DELETED, FakeItem(request.uri, null))))
    assertEquals(listOf(FluidWearRouted.StatusRemoved("posta")), gone)
    assertNull(sync.statuses.get("posta"))
  }

  @Test
  fun `gli elementi fuori dal ponte non si toccano`() {
    val sync = FluidWearSync(FluidWearRole.Watch, FluidWearMemoryStore())
    val other = Uri.parse("wear://nodo/altra-app/stato")
    assertTrue(handle(sync, listOf(FakeEvent(DataEvent.TYPE_CHANGED, FakeItem(other, byteArrayOf(1, 2, 3))))).isEmpty())
  }

  @Test
  fun `il telefono riceve l'evento del polso dall'elemento di dati`() {
    val event = FluidWearEvent("ev-1", "preferito", "42", createdAtMillis = 1)
    val outgoing = FluidWearOutgoing(event, attempt = 0)
    val request = FluidWearDataLayer.requestFor(outgoing.path, outgoing.bytes())
    val sync = FluidWearSync(FluidWearRole.Phone, FluidWearMemoryStore(), clock = { 2 })
    val routed = handle(sync, listOf(FakeEvent(DataEvent.TYPE_CHANGED, FakeItem(request.uri, request.data))))
    assertEquals(listOf(FluidWearRouted.EventArrived(event)), routed)
  }

  /**
   * R1 (Sol, maggiore): la schermata che chiama `pullStatuses` esce mentre Play services legge. Il `Task` completa dopo
   * l'annullamento con un buffer: il `finally` a valle non lo riceve, quindi lo deve rilasciare l'attesa stessa.
   */
  @Test
  fun `un buffer arrivato dopo l'annullamento si rilascia`() = runBlocking {
    val source = TaskCompletionSource<FakeBuffer>()
    val waiting = launch(start = CoroutineStart.UNDISPATCHED) { source.task.await() }
    waiting.cancel()
    val buffer = FakeBuffer()
    source.setResult(buffer)
    shadowOf(Looper.getMainLooper()).idle()
    waiting.join()
    assertTrue("il buffer arrivato tardi resta aperto", buffer.released)
    // Un risultato arrivato in tempo resta a chi aspetta, che lo rilascia lui.
    val inTime = FakeBuffer()
    val ok = TaskCompletionSource<FakeBuffer>()
    val got = async(start = CoroutineStart.UNDISPATCHED) { ok.task.await() }
    ok.setResult(inTime)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(inTime, got.await())
    assertFalse(inTime.released)
  }

  private class FakeBuffer : Releasable {
    var released = false
    override fun release() {
      released = true
    }
  }

  // Lo smistamento non usa il client: e' la stessa funzione che il servizio chiama dentro onDataChanged.
  private fun handle(sync: FluidWearSync, events: List<DataEvent>): List<FluidWearRouted> =
    FluidWearDataLayer.route(sync, events)

  private class FakeItem(private val uri: Uri, private var bytes: ByteArray?) : DataItem {
    override fun getUri(): Uri = uri
    override fun getData(): ByteArray? = bytes
    override fun getAssets(): Map<String, DataItemAsset> = emptyMap()
    override fun setData(data: ByteArray?): DataItem = apply { bytes = data }
    override fun freeze(): DataItem = this
    override fun isDataValid(): Boolean = true
  }

  private class FakeEvent(private val type: Int, private val item: DataItem) : DataEvent {
    override fun getType(): Int = type
    override fun getDataItem(): DataItem = item
    override fun freeze(): DataEvent = this
    override fun isDataValid(): Boolean = true
  }
}
