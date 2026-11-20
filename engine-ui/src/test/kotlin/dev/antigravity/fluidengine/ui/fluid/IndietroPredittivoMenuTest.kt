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
}
