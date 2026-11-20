package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4f sul Tab S9 (NO 5): in «Altro» scuro il gesto dal bordo chiudeva il menu, ma durante il trascinamento il vetro restava
 * fermo (f0084–f0156) e partiva solo al rilascio (f0162). In Apple, e nelle pagine di F4A, il gesto anima il ritorno col
 * dito. Qui il viaggio del menu col gesto ([FluidMenuJourney.backPull]): la sagoma segue il dito verso la sorgente, il
 * congedo parte dalla sagoma tirata, il gesto annullato torna al pannello.
 */
class IndietroPredittivoMenuTest {
  private val d = 2.125f
  private val button = Rect(1446f, 1846f, 1540f, 1940f)
  private val panel = Rect(1009f, 1957f, 1540f, 2373f)
  private val radius = 22f * d

  private fun physics() = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply { density = d }

  /** «Altro» fermo: la nascita finita, il pannello posato. */
  private fun altro(physics: FluidPhysicsState): FluidMenuJourney = FluidMenuJourney().apply {
    prepare(
      FluidMenuAnchor().apply {
        restBounds = button
        pressedBounds = button
      },
      Offset.Zero, panel, radius, FluidMenuInflation.None, liquidMenu = true, appleBirth = true,
    )
    retarget(1f)
    install(physics)
    runBlocking {
      birthClock.snapTo(10_000f)
      progress.snapTo(1f)
      focus.snapTo(1f)
    }
  }

  private fun shown(physics: FluidPhysicsState): Rect = physics.dropRestSlabNow()?.frame ?: panel

  @Test
  fun theMenuFollowsTheFingerTowardsItsSource() {
    val physics = physics()
    val journey = altro(physics)
    assertEquals("senza gesto il pannello", panel, shown(physics))
    var last = panel
    for (p in listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f)) {
      runBlocking { journey.backPull.snapTo(p) }
      val now = shown(physics)
      println("TAVOLA altro gesto=$p sagoma=${now.left.toInt()},${now.top.toInt()},${now.right.toInt()},${now.bottom.toInt()}")
      // Si accorcia sempre, verso il tasto (sopra il pannello): il bordo lontano sale.
      assertTrue("al $p il bordo lontano ${now.bottom} sale da ${last.bottom}", now.bottom < last.bottom)
      assertTrue("al $p il bordo vicino resta sul tasto", now.top <= panel.top + 1f)
      last = now
    }
    // Alla fine del gesto il menu si e' accorciato del bordo lontano del primo tratto del congedo, e si legge ancora.
    assertTrue("accorciato: ${last.height} su ${panel.height}", last.height < panel.height * 0.92f)
    assertTrue("ancora un menu: ${last.height} su ${panel.height}", last.height > panel.height * 0.6f)
  }

  @Test
  fun releasingClosesFromThePulledShape() {
    val physics = physics()
    val journey = altro(physics)
    runBlocking { journey.backPull.snapTo(0.8f) }
    val pulled = requireNotNull(physics.dropRestSlabNow())
    // L'ordine dell'host (FluidMenuLayer): la sagoma tirata diventa la partenza del ritorno, poi il verso cambia.
    journey.noteBackHome(physics.dropRestSlabNow())
    journey.retarget(0f)
    assertTrue(journey.controlledReturn)
    assertEquals("il primo fotogramma del ritorno e' la sagoma tirata", pulled, physics.dropRestSlabNow())
    var last = pulled.frame
    for (p in listOf(0.9f, 0.7f, 0.5f, 0.3f, 0.1f)) {
      runBlocking { journey.progress.snapTo(p) }
      val now = requireNotNull(physics.dropRestSlabNow()).frame
      assertTrue("al ritorno $p non ricresce: $last -> $now", now.height <= last.height + 0.5f)
      last = now
    }
  }

  @Test
  fun aCancelledGestureGoesBackToThePanel() {
    val physics = physics()
    val journey = altro(physics)
    runBlocking {
      journey.backPull.snapTo(0.6f)
      assertTrue(shown(physics).height < panel.height)
      // Nell'host la molla del menu; qui basta il punto d'arrivo.
      journey.backPull.snapTo(0f)
    }
    assertNull("di nuovo fermo sul pannello", physics.dropRestSlabNow()?.takeIf { it.frame != panel })
    // Un'apertura nuova dimentica il gesto.
    journey.prepare(journey.anchor, Offset.Zero, panel, radius, FluidMenuInflation.None, liquidMenu = true, appleBirth = true)
    assertEquals(0f, journey.backPull.value, 0f)
    assertNull(journey.backHome)
  }

  @Test
  fun theContextMenuFollowsTheFingerTowardsTheRow() {
    val physics = physics()
    val seed = Rect(1200f, 1880f, 1260f, 1940f)
    val journey = FluidMenuJourney().apply {
      prepare(null, Offset.Zero, panel, radius, FluidMenuInflation.None, seed = seed, liquidMenu = true)
      retarget(1f)
    }
    val birth = FluidContextBirth().apply {
      this.radius = this@IndietroPredittivoMenuTest.radius
      prepare(fresh = true)
    }
    runBlocking {
      birth.clock.snapTo(FluidContextBirthProfile.EndMillis)
      journey.progress.snapTo(1f)
    }
    // `installMenuTravel` del contestuale.
    physics.driveExternally(
      from = FluidForm.Slab(seed, FluidCornerRadii.all(seed.minDimension / 2f)),
      to = FluidForm.Slab(panel, FluidCornerRadii.all(radius)),
      progress = { journey.progress.value },
      startFrame = null,
      travelSlab = { journey.backPulled(birth.travel(journey)) },
      travelWarp = { birth.warp(journey) },
    )
    runBlocking { journey.backPull.snapTo(1f) }
    val pulled = requireNotNull(physics.dropRestSlabNow())
    assertTrue(pulled.frame.height < panel.height * 0.92f)
    journey.noteBackHome(pulled)
    journey.retarget(0f, onContour = true)
    assertEquals("il ritorno del contestuale parte dalla sagoma tirata", pulled, physics.dropRestSlabNow())
  }

  /** Un menu del Tab S9 a riposo (S4g-ter, prova 34, scuro): la sorgente da cui e' nato e il pannello. */
  private class Menu(val name: String, val source: Rect, val panel: Rect)

  /** Le sorgenti misurate sul Tab S9: il tasto di «Altro», e la goccia del contestuale sotto il dito sulla riga, sul tondo e sulla foto. */
  private fun tabletMenus(): List<Menu> {
    val seed = FluidMenuDropSeedDiameter.value * d
    val overlap = FluidMenuDropSeedOverlap.value * d
    fun drop(row: Rect, panel: Rect, x: Float) = fluidMenuDropSeed(row, panel, x, seed, overlap)
    val rowPanel = Rect(532f, 1340f, 1068f, 1788f)
    val roundPanel = Rect(80f, 993f, 616f, 1222f)
    val photoPanel = Rect(64f, 1120f, 600f, 1462f)
    return listOf(
      Menu("altro", button, panel),
      Menu("riga", drop(Rect(20f, 1160f, 1580f, 1308f), rowPanel, 700f), rowPanel),
      Menu("tondo", drop(Rect(280f, 828f, 420f, 964f), roundPanel, 348f), roundPanel),
      Menu("foto", drop(Rect(32f, 708f, 628f, 1088f), photoPanel, 145f), photoPanel),
    )
  }

  /** Di quanto il bordo lontano (quello opposto alla sorgente) e' venuto verso di lei. */
  private fun farGive(m: Menu, now: Rect): Float =
    if (m.source.center.y >= m.panel.center.y) now.top - m.panel.top else m.panel.bottom - now.bottom

  /** Il tiro fino a S4g-ter: il ritorno dei menu fino a 0,32 del cammino. */
  private fun before(m: Menu, pull: Float): Rect {
    val eased = 1f - (1f - pull) * (1f - pull)
    val source = FluidForm.Slab(m.source, FluidCornerRadii.all(m.source.minDimension / 2f))
    return fluidMenuReturnSlab(source, FluidForm.Slab(m.panel, FluidCornerRadii.all(radius)), 1f - 0.32f * eased,
      wrapSource = m.name == "altro").frame
  }

  /**
   * S4g-ter sul Tab S9 (prova 34): col gesto completo i cinque menu si chiudono, ma durante il trascinamento il tondo e la foto
   * restavano quasi fermi (scuro tondo f0533–f0563, foto f0493–f0526), mentre la riga, il gruppo e «Altro» seguivano il dito.
   * Il gesto del tablet (397 px dal bordo, ~0,4): nelle riprese il bordo lontano saliva di ~10 px nel tondo, ~16 nella foto, ~22
   * nella riga e ~27 in «Altro». Il tiro era proporzionale all'altezza del pannello e partiva col quadrato del gesto: sui
   * pannelli corti si vedeva appena. Ora tutti seguono il dito di una misura simile.
   */
  @Test
  fun theRoundAndThePhotoFollowTheFingerLikeTheOthers() {
    val minPx = FluidMenuBackPullMinFarDp * d
    val tablet = 0.4f
    val gives = HashMap<String, Float>()
    for (m in tabletMenus()) {
      val slab = FluidForm.Slab(m.panel, FluidCornerRadii.all(radius))
      var last = 0f
      for (p in listOf(0.1f, 0.25f, tablet, 0.6f, 1f)) {
        val now = fluidMenuBackPullSlab(m.source, slab, p, minPx).frame
        val give = farGive(m, now)
        println(
          "TAVOLA tiro ${m.name} gesto=$p bordo lontano ${"%.1f".format(give)} px (prima ${"%.1f".format(farGive(m, before(m, p)))}) " +
            "sagoma=${now.left.toInt()},${now.top.toInt()},${now.right.toInt()},${now.bottom.toInt()}",
        )
        assertTrue("${m.name} al $p il bordo lontano segue il dito: $give dopo $last", give > last)
        // Il bordo vicino resta sulla sorgente, e il pannello resta un menu.
        val near = if (m.source.center.y >= m.panel.center.y) now.bottom - m.panel.bottom else m.panel.top - now.top
        assertTrue("${m.name} al $p il bordo vicino: $near", near >= 0f && near <= m.panel.height * 0.03f)
        assertTrue("${m.name} al $p ancora un menu: ${now.height}", now.height > m.panel.height * 0.65f)
        if (p == tablet) gives[m.name] = give
        last = give
      }
      // Dal primo tratto del gesto: col quadrato del ritorno, a 0,1 il bordo si muoveva del 4% della sua corsa.
      val first = farGive(m, fluidMenuBackPullSlab(m.source, slab, 0.1f, minPx).frame)
      assertTrue("${m.name} segue il dito da subito: $first su $last", first >= last * 0.15f)
    }
    println("TAVOLA tiro al gesto del tablet: $gives")
    val riga = requireNotNull(gives["riga"])
    for (name in listOf("tondo", "foto")) {
      val give = requireNotNull(gives[name])
      assertTrue("$name: $give px al gesto del tablet", give >= 30f)
      assertTrue("$name come la riga: $give contro $riga", give >= riga * 0.8f)
    }
  }
}
