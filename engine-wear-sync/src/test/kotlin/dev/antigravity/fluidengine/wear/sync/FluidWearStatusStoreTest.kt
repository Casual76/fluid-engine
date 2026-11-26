package dev.antigravity.fluidengine.wear.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F8.25 · Lo stato pubblicato dal telefono e tenuto dall'orologio (`tile-e-complicazioni`): l'ultimo per chiave, su
 * disco (c'è ad app chiusa e dopo un riavvio), e uno più vecchio arrivato dopo non torna indietro.
 */
class FluidWearStatusStoreTest {

  @get:Rule val folder = TemporaryFolder()

  private fun lesson(at: Long, value: String) = FluidWearStatus(
    key = "lezione",
    title = "Matematica",
    value = value,
    progress = 0.5f,
    updatedAtMillis = at,
    staleAfterMillis = at + 3_600_000,
  )

  @Test
  fun `il piu' nuovo vince anche se arriva prima`() {
    val store = FluidWearStatusStore(FluidWearFileStore(folder.root))
    assertTrue(store.put(lesson(200, "11:00")))
    assertFalse("uno piu' vecchio arrivato dopo si scarta", store.put(lesson(100, "10:00")))
    assertFalse("lo stesso, rimesso, non cambia niente", store.put(lesson(200, "11:00")))
    assertEquals("11:00", store.get("lezione")?.value)
  }

  @Test
  fun `gli stati restano dopo un riavvio, e si tolgono`() {
    val first = FluidWearStatusStore(FluidWearFileStore(folder.root))
    first.put(lesson(100, "10:00"))
    first.put(FluidWearStatus("posta", "Posta", "3", detail = "non lette", updatedAtMillis = 100))
    val second = FluidWearStatusStore(FluidWearFileStore(folder.root))
    assertEquals(setOf("lezione", "posta"), second.statuses.value.keys)
    assertEquals(lesson(100, "10:00"), second.get("lezione"))
    assertTrue(second.remove("posta"))
    assertNull(FluidWearStatusStore(FluidWearFileStore(folder.root)).get("posta"))
  }

  @Test
  fun `il ponte dell'orologio tiene lo stato e lo toglie quando il telefono lo toglie`() {
    val watch = FluidWearSync(FluidWearRole.Watch, FluidWearFileStore(folder.root))
    val status = lesson(100, "10:00")
    val routed = watch.route(FluidWearPaths.status("lezione"), FluidWearFormat.encode(status))
    assertEquals(FluidWearRouted.StatusChanged(status), routed)
    assertEquals(FluidWearRouted.StatusRemoved("lezione"), watch.routeDeleted(FluidWearPaths.status("lezione")))
    assertNull(watch.statuses.get("lezione"))
  }

  @Test
  fun `uno stato scaduto si riconosce`() {
    val status = lesson(100, "10:00")
    assertFalse(status.isStale(100 + 3_599_999))
    assertTrue(status.isStale(100 + 3_600_000))
  }
}
