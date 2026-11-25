package dev.antigravity.fluidengine.media

import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F8.14 · Lo stato del selettore senza interfaccia: le pagine che arrivano, la scelta in ordine col tetto, la consegna da
 * due sorgenti nell'ordine dei tocchi, il rifiuto che non lascia uscire niente e toglie il colpevole, i saltati.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FluidMediaPickerStateTest {

  private fun TestScope.picker(limit: Int, vararg sources: FluidMediaSource): FluidMediaPickerState {
    val request = FluidMediaRequest.attachments(limit)
    return FluidMediaPickerState(sources.toList(), request, FluidSelectionState(limit = limit), this, pageSize = 20)
  }

  @Test fun lePagineArrivanoEUnaSorgenteFintaRicarica() = runTest {
    val cloud = FluidFakeMediaSource.sample(count = 45)
    val state = picker(10, cloud)
    val feed = state.feed(cloud, state.request.query())
    assertEquals(0, feed.version)
    feed.loadMore()
    advanceUntilIdle()
    assertEquals(20, feed.items.size)
    assertEquals(45, feed.total)
    feed.loadMore()
    feed.loadMore() // gia' in corso: non ne parte una seconda
    advanceUntilIdle()
    assertEquals(40, feed.items.size)
    feed.loadMore()
    advanceUntilIdle()
    assertEquals(45, feed.items.size)
    assertTrue(feed.finished)
    // La stessa domanda da' la stessa lista.
    assertTrue(feed === state.feed(cloud, state.request.query()))
    // Le cose si ritrovano per chiave.
    assertEquals(feed.items[3], state.item(feed.items[3].key))
  }

  @Test fun laSceltaEInOrdineEColTetto() = runTest {
    val cloud = FluidFakeMediaSource.sample(count = 30)
    val state = picker(3, cloud)
    val feed = state.feed(cloud, state.request.query())
    feed.loadMore()
    advanceUntilIdle()
    val keys = feed.items.map { it.key }
    state.selection.toggle(keys[5])
    state.selection.toggle(keys[1])
    state.selection.toggle(keys[9])
    assertTrue(state.selection.atLimit)
    state.selection.toggle(keys[0]) // oltre il tetto: non entra
    assertEquals(listOf(keys[5], keys[1], keys[9]), state.selectedItems.map { it.key })
    state.selection.toggle(keys[1])
    assertFalse(state.selection.atLimit)
    state.selection.toggle(keys[0])
    assertEquals(listOf(keys[5], keys[9], keys[0]), state.selectedItems.map { it.key })
  }

  @Test fun laConsegnaDaDueSorgentiTieneLOrdineDeiTocchi() = runTest {
    val phone = FluidFakeMediaSource.sample(id = "device", label = "Telefono", count = 10)
    val cloud = FluidFakeMediaSource.sample(id = "mycloud", count = 10)
    val state = picker(10, phone, cloud)
    val a = state.feed(phone, state.request.query()).also { it.loadMore() }
    val b = state.feed(cloud, state.request.query(album = FluidFakeMediaSource.AlbumAll)).also { it.loadMore() }
    advanceUntilIdle()
    val order = listOf(b.items[0], a.items[2], b.items[4], a.items[0]).filter { !it.backupOnly }
    order.forEach { state.selection.toggle(it.key) }
    var result: List<FluidPickedMedia>? = null
    launchDeliver(state) { result = it }
    advanceUntilIdle()
    assertEquals(order.map { it.key }, result!!.map { it.item!!.key })
    assertNull(state.notice)
    assertFalse(state.delivering)
  }

  @Test fun unRifiutoNonLasciaUscireNiente() = runTest {
    val phone = FluidFakeMediaSource.sample(id = "device", label = "Telefono", count = 10)
    val cloud = FluidFakeMediaSource.sample(id = "mycloud", count = 12)
    cloud.backupReachable = false
    val state = picker(10, phone, cloud)
    val a = state.feed(phone, state.request.query()).also { it.loadMore() }
    val b = state.feed(cloud, state.request.query()).also { it.loadMore() }
    advanceUntilIdle()
    val backup = b.items.first { it.backupOnly }
    state.selection.toggle(a.items[0].key)
    state.selection.toggle(backup.key)
    var result: List<FluidPickedMedia>? = listOf()
    launchDeliver(state) { result = it }
    advanceUntilIdle()
    assertNull("niente esce", result)
    assertEquals(FluidMediaRefusal.Unreachable, state.notice?.refusal)
    // La scelta resta, perche' la persona la corregga.
    assertEquals(2, state.selection.countIn(0))
  }

  @Test fun gliSparitiContanoFraISaltati() = runTest {
    val cloud = FluidFakeMediaSource.sample(count = 6)
    val state = picker(10, cloud)
    val feed = state.feed(cloud, state.request.query()).also { it.loadMore() }
    advanceUntilIdle()
    val gone = feed.items[0].copy(id = "sparito")
    var result: List<FluidPickedMedia>? = null
    launchDeliver(state, listOf(gone, feed.items[1])) { result = it }
    advanceUntilIdle()
    assertEquals(listOf(feed.items[1].id), result!!.map { it.item!!.id })
    assertEquals(1, state.notice?.skipped)
    assertNull(state.notice?.refusal)
  }

  @Test fun bloccataChiedeLoSblocco() = runTest {
    val cloud = FluidFakeMediaSource.sample(count = 6)
    val state = picker(10, cloud)
    val feed = state.feed(cloud, state.request.query()).also { it.loadMore() }
    advanceUntilIdle()
    state.selection.toggle(feed.items[0].key)
    cloud.lock()
    launchDeliver(state) {}
    advanceUntilIdle()
    assertTrue(state.unlockNeeded is FluidMediaAccess.Locked)
  }

  private fun kotlinx.coroutines.CoroutineScope.launchDeliver(
    state: FluidMediaPickerState,
    items: List<FluidMediaItem>? = null,
    onDone: (List<FluidPickedMedia>?) -> Unit,
  ) {
    this.launch { onDone(if (items == null) state.deliver() else state.deliver(items)) }
  }
}
