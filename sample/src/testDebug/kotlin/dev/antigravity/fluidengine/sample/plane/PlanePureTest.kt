package dev.antigravity.fluidengine.sample.plane

import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.GalleryEntry
import dev.antigravity.fluidengine.sample.GalleryLaunch
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole pure della pagina «Piano flottante» (F3.6), senza Android attorno: gli extra di lancio, il giro dello
 * spacing e chi legge i contatori del contenitore per scrivere `VIAGGIO`, `FINE` e `RIPOSO`.
 */
class PlanePureTest {

  @Test
  fun `la pagina si apre da riga di comando con la scena, il tema, lo spacing e l'automatico chiesti`() {
    val page = GalleryLaunch.parse(mapOf("gallery.page" to "plane")::get).stack().last() as GalleryEntry.Page
    assertEquals(GalleryPage.Plane, page.page)
    val (config, start) = PlaneLaunch.parse(inheritedDark = false, inherited = FluidGlassModel.Liquid, extra = mapOf(
      "plane.scene" to " Capsule ",
      "plane.theme" to "scuro",
      "plane.spacing" to "larga",
      "plane.model" to "classic",
      "plane.auto" to "1500",
      "plane.interrupt" to "true",
    )::get)
    assertEquals(PlaneScene.Capsules, config.scene)
    assertTrue(config.dark)
    assertEquals(PlaneSpacing.Wide, config.spacing)
    assertEquals(FluidGlassModel.Classic, config.model)
    assertEquals(1_500L, start.autoMs)
    assertEquals(PlaneLaunch.DefaultInterruptMs, start.interruptMs)
  }

  @Test
  fun `senza extra parte la prima scena, e un refuso non fa fallire il lancio`() {
    val (plain, start) = PlaneLaunch.parse(true, FluidGlassModel.Liquid, mapOf("plane.scene" to "boh", "plane.auto" to "x")::get)
    assertEquals(PlaneScene.OneTwo, plain.scene)
    assertTrue(plain.dark)
    assertEquals(PlaneSpacing.Medium, plain.spacing)
    assertEquals(0L, start.autoMs)
    assertEquals(0L, start.interruptMs)
  }

  @Test
  fun `l'automatico acceso usa l'intervallo di serie, mai sotto 300 ms, e lo spacing accetta i dp`() {
    val (config, start) = PlaneLaunch.parse(false, FluidGlassModel.Liquid, mapOf("plane.auto" to "acceso", "plane.spacing" to "24dp")::get)
    assertEquals(PlaneLaunch.DefaultAutoMs, start.autoMs)
    assertEquals(24.dp, config.spacing)
    assertEquals("24 dp", PlaneSpacing.label(config.spacing))
    assertEquals(300L, PlaneLaunch.parse(false, FluidGlassModel.Liquid, mapOf("plane.auto" to "10")::get).second.autoMs)
    assertEquals(0L, PlaneLaunch.parse(false, FluidGlassModel.Liquid, mapOf("plane.auto" to "spento")::get).second.autoMs)
  }

  @Test
  fun `la scena dell'interruzione interrompe di serie, le altre solo se lo si chiede`() {
    val interrupt = PlaneLaunch.parse(false, FluidGlassModel.Liquid, mapOf("plane.scene" to "interruzione")::get).second
    assertEquals(PlaneLaunch.DefaultInterruptMs, interrupt.interruptMs)
    val off = PlaneLaunch.parse(false, FluidGlassModel.Liquid, mapOf("plane.scene" to "interruzione", "plane.interrupt" to "no")::get)
    assertEquals(0L, off.second.interruptMs)
    val other = PlaneLaunch.parse(false, FluidGlassModel.Liquid, mapOf("plane.scene" to "uno-due")::get).second
    assertEquals(0L, other.interruptMs)
  }

  @Test
  fun `lo spacing gira stretta, media, larga, e ogni scena ha una chiave sua`() {
    assertEquals(PlaneSpacing.Medium, PlaneSpacing.next(PlaneSpacing.Narrow))
    assertEquals(PlaneSpacing.Wide, PlaneSpacing.next(PlaneSpacing.Medium))
    assertEquals(PlaneSpacing.Narrow, PlaneSpacing.next(PlaneSpacing.Wide))
    assertEquals(PlaneScene.entries.size, PlaneScene.entries.map { it.key }.toSet().size)
    assertEquals(PlaneScene.OneTwo, PlaneScene.entries.last().next())
  }

  @Test
  fun `le scene del giro di correzione si aprono dagli extra, ognuna con i suoi due stati in parole`() {
    val added = mapOf(
      "due-nati" to PlaneScene.TwoBorn,
      "bordo" to PlaneScene.Edge,
      "tre-vicini" to PlaneScene.ThreeNear,
      "lista" to PlaneScene.Scrolling,
      "testo" to PlaneScene.Label,
      "piu-contenitori" to PlaneScene.Several,
      "ridimensiona" to PlaneScene.Resize,
    )
    added.forEach { (key, scene) ->
      val (config, _) = PlaneLaunch.parse(false, FluidGlassModel.Liquid, mapOf("plane.scene" to key)::get)
      assertEquals(key, scene, config.scene)
      assertEquals(key, scene.key)
      assertNotEquals("$key: i due stati hanno nomi diversi", describe(scene, false), describe(scene, true))
    }
    assertEquals(17, PlaneScene.entries.size)
    // Le prime dieci restano nel loro ordine: il tasto «Scena» e le riprese di prima non cambiano.
    assertEquals(PlaneScene.Many, PlaneScene.entries[9])
    assertEquals(PlaneScene.TwoBorn, PlaneScene.Many.next())
  }

  @Test
  fun `lo stato e' la parita' del passo, e il cambio lo scrive`() {
    val live = PlaneLive()
    val logged = ArrayList<Int>()
    assertFalse(live.on)
    live.change { logged += it }
    assertTrue(live.on)
    live.change { logged += it }
    assertFalse(live.on)
    assertEquals(listOf(1, 2), logged)
    live.reset()
    assertEquals(0, live.step)
  }

  @Test
  fun `chi guarda il contenitore distingue partenza, ripartenza, fine e riposo`() {
    val first = listOf("riposo-0")
    val watch = PlaneJourneyWatch(journeys = 0, retargets = 0, rest = first)
    assertEquals(PlaneJourneyWatch.Event.None, watch.observe(0, 0, first))
    // Il primo riposo (o un aspetto cambiato) senza viaggio: RIPOSO.
    val second = listOf("riposo-1")
    assertEquals(PlaneJourneyWatch.Event.Rest, watch.observe(0, 0, second))
    // Un viaggio parte e pubblica il riposo d'arrivo nello stesso fotogramma: è la partenza, non la fine.
    val target = listOf("arrivo")
    assertEquals(PlaneJourneyWatch.Event.Started, watch.observe(1, 0, target))
    assertTrue(watch.inFlight)
    assertEquals(PlaneJourneyWatch.Event.None, watch.observe(1, 0, target))
    // Ripartito a metà: un altro arrivo, ancora in volo.
    val retarget = listOf("arrivo-2")
    assertEquals(PlaneJourneyWatch.Event.Retargeted, watch.observe(1, 1, retarget))
    assertTrue(watch.inFlight)
    // L'arrivo: un riposo nuovo a contatori fermi.
    assertEquals(PlaneJourneyWatch.Event.Finished, watch.observe(1, 1, listOf("arrivo-2")))
    assertFalse(watch.inFlight)
  }
}
