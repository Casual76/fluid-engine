package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlassPlaneTest {
  private val canvas = Rect(0f, 0f, 400f, 180f)
  private fun piece(key: String, x: Float, id: Any? = key, y: Float = 40f, width: Float = 44f) =
    GlassPlanePiece(key, Rect(x, y, x + width, y + 44f), FluidCornerRadii.all(22f), id = id)
  private fun state(vararg pieces: GlassPlanePiece, spacing: Float = 12f, preferred: Int = 4) =
    GlassPlaneState(pieces.toList(), spacing, canvas, preferred)
  private fun GlassPlaneFrame.target(key: Any) = pieces.single { it.flightId.targetKey == key }
  private fun assertRect(expected: Rect, actual: Rect, epsilon: Float = 0.001f) {
    assertEquals(expected.left, actual.left, epsilon)
    assertEquals(expected.top, actual.top, epsilon)
    assertEquals(expected.right, actual.right, epsilon)
    assertEquals(expected.bottom, actual.bottom, epsilon)
  }
  /** [this] più piccolo di [scale] attorno al suo centro: il bocciolo di chi nasce, il nucleo di chi confluisce (F3.12c). */
  private fun Rect.scaledAbout(scale: Float) = Rect(
    center.x - width * scale / 2f, center.y - height * scale / 2f, center.x + width * scale / 2f, center.y + height * scale / 2f,
  )
  private fun assertContains(outer: Rect, inner: Rect) {
    assertTrue("$outer non contiene $inner", outer.left <= inner.left + 0.001f && outer.top <= inner.top + 0.001f &&
      outer.right + 0.001f >= inner.right && outer.bottom + 0.001f >= inner.bottom)
  }

  @Test fun registryUpdatesByNodeKeyAndSnapshotsDoNotChangeAfterRemoval() {
    val registry = GlassPlaneRegistry()
    registry.put(piece("a", 20f))
    val before = registry.snapshot(12f, canvas)
    registry.put(piece("a", 40f))
    registry.put(piece("b", 88f))
    val after = registry.snapshot(12f, canvas)
    assertEquals(20f, before.pieces.single().bounds.left, 0f)
    assertEquals(2, after.pieces.size)
    assertTrue(registry.remove("a"))
    assertFalse(registry.remove("a"))
    assertEquals(2, after.pieces.size)
    assertEquals(1, registry.snapshot(12f, canvas).pieces.size)
    assertNull(after.clusterOf("missing"))
  }

  @Test fun clustersAreTransitiveAndRespectContainerAndGlassVariant() {
    val a = piece("a", 0f)
    val b = piece("b", 56f)
    val c = piece("c", 112f)
    val foreign = piece("foreign", 56f).copy(containerId = "other")
    val clear = piece("clear", 56f).copy(variant = GlassLiquidVariant.Clear)
    val snapshot = state(a, b, c, foreign, clear)
    assertEquals(3, snapshot.clusters.size)
    val cluster = snapshot.clusterOf("b")!!
    assertEquals(setOf("a", "b", "c"), cluster.pieces.map { it.key }.toSet())
    assertRect(Rect(0f, 40f, 156f, 84f), cluster.bounds)
    assertEquals(cluster.id, state(c, b, a).clusterOf("a")!!.id)
    assertNotEquals(cluster.id, snapshot.clusterOf("foreign")!!.id)
  }

  @Test fun spacingIsEdgeDistanceIncludingDiagonalAndExactThreshold() {
    assertEquals(1, state(piece("a", 0f), piece("b", 56f)).clusters.size)
    assertEquals(2, state(piece("a", 0f), piece("b", 56.01f)).clusters.size)
    // Varco diagonale 3,4: distanza euclidea 5, non il massimo dei due assi.
    val a = piece("a", 0f, y = 0f)
    val b = piece("b", 47f, y = 48f)
    assertEquals(1, state(a, b, spacing = 5f).clusters.size)
    assertEquals(2, state(a, b, spacing = 4.99f).clusters.size)
  }

  @Test fun preferredFourSplitsOnlyAtARealGapAndExplicitUnionStaysTogether() {
    val separated = (0..4).map { piece("p$it", it * 48f) }
    assertEquals(2, GlassPlaneState(separated, 12f, canvas).clusters.size)
    val dense = (0..4).map { piece("p$it", it * 4f) }
    assertEquals(1, GlassPlaneState(dense, 12f, canvas).clusters.size)
    val united = separated.map { it.copy(unionId = "one") }
    assertEquals(1, GlassPlaneState(united, 0f, canvas).clusters.size)
  }

  @Test fun overEightSplitsAtTheWidestGapEvenForAnExplicitUnion() {
    val pieces = (0..8).map { index -> piece("p$index", if (index < 4) index * 2f else 90f + index * 2f)
      .copy(unionId = "all") }
    val snapshot = GlassPlaneState(pieces, 100f, canvas)
    assertEquals(listOf(4, 5), snapshot.clusters.map { it.pieces.size })
    assertEquals(9, snapshot.clusters.sumOf { it.pieces.size })
    assertTrue(snapshot.clusters.all { it.pieces.size <= GlassFieldMaxPieces })
    val vertical = pieces.map { it.copy(bounds = Rect(0f, it.bounds.left, 44f, it.bounds.right)) }
    assertEquals(listOf(4, 5), GlassPlaneState(vertical, 100f, canvas).clusters.map { it.pieces.size })
  }

  @Test fun identityPrecedesRoleSymbolAndNodeKeyAndInterpolatesFourDifferentRadii() {
    val a = piece("old-node", 20f, id = "stable").copy(role = "back", symbol = "arrow")
    val b = piece("new-node", 65f, id = "stable", width = 72f)
      .copy(role = "other", symbol = "plus", radii = FluidCornerRadii(1f, 8f, 13f, 21f))
    val journey = GlassPlaneJourney(state(a), state(b))
    assertEquals(GlassPlaneMatch.Id, journey.frame(80.0).target("new-node").matchedBy)
    assertRect(a.bounds, journey.frame(0.0).pieces.single().bounds)
    val middle = journey.frame(80.0).pieces.single()
    assertTrue(middle.bounds.left > a.bounds.left && middle.bounds.left < b.bounds.left)
    assertTrue(middle.radii.topLeft < middle.radii.topRight && middle.radii.topRight < middle.radii.bottomRight)
    assertRect(b.bounds, middle.contentBounds)
    assertEquals(b.radii, journey.frame(1000.0).pieces.single().radii)
  }

  @Test fun anonymousMatchingUsesRoleThenSymbolThenOutsideOrder() {
    val outer = piece("a", 320f, id = null).copy(role = "main", symbol = "plus")
    val inner = piece("b", 250f, id = null).copy(symbol = "star")
    val toOuter = piece("c", 310f, id = null).copy(role = "main", symbol = "changed")
    val toInner = piece("d", 240f, id = null).copy(symbol = "star")
    val frame = planeJourneyFrame(70.0, state(inner, outer), state(toInner, toOuter))
    assertEquals(GlassPlaneMatch.Role, frame.target("c").matchedBy)
    assertEquals("a", frame.target("c").flightId.sourceKey)
    assertEquals(GlassPlaneMatch.Symbol, frame.target("d").matchedBy)
    val ordered = planeJourneyFrame(0.0, state(inner.copy(symbol = null), outer.copy(role = null, symbol = null)),
      state(toInner.copy(symbol = null), toOuter.copy(role = null, symbol = null)))
    assertEquals("a", ordered.target("c").flightId.sourceKey)
    assertTrue(ordered.pieces.all { it.matchedBy == GlassPlaneMatch.OutsideOrder })
  }

  @Test fun anonymousPiecesStayOnTheirSideOfTheContainer() {
    val frame = planeJourneyFrame(60.0, state(piece("left", 10f, id = null)), state(piece("right", 330f, id = null)))
    assertNull(frame.target("right").flightId.sourceKey)
    assertEquals(GlassPlaneChange.Materialize, frame.target("right").change)
  }

  @Test fun crossingRoleMatchesAreDeclinedAndFallbackKeepsOutsideOrder() {
    val from = state(piece("a", 230f, id = null).copy(role = "one"), piece("b", 310f, id = null).copy(role = "two"))
    val to = state(piece("c", 230f, id = null).copy(role = "two"), piece("d", 310f, id = null).copy(role = "one"))
    val journey = GlassPlaneJourney(from, to)
    for (ms in 0..330 step 3) {
      val frame = journey.frame(ms.toDouble())
      assertTrue(frame.target("c").bounds.center.x <= frame.target("d").bounds.center.x)
    }
  }

  @Test fun explicitIdentitySwapReportsConflictAndMaterializesLocally() {
    val from = state(piece("a", 230f), piece("b", 310f))
    val to = state(piece("a", 310f), piece("b", 230f))
    val journey = GlassPlaneJourney(from, to)
    assertEquals(setOf("a", "b"), journey.crossingIds)
    assertTrue(journey.frame(100.0).pieces.all {
      it.change == GlassPlaneChange.Materialize || it.change == GlassPlaneChange.Dematerialize
    })
    assertEquals(2, journey.frame(100.0).pieces.count { it.materialize > 0f && it.piece.id != null })
    // Anche l'interruzione di questo ripiego ha chiavi uniche e nessun id duplicato.
    journey.retarget(90.0, from).frame(0.0)
  }

  @Test fun newIdentityIsBornFromClosestCompatibleNeighbourWithinSpacing() {
    val a = piece("a", 20f)
    val b = piece("b", 100f)
    val newcomer = piece("new", 147f)
    val frame = planeJourneyFrame(0.0, state(a, b), state(a, b, newcomer))
    val born = frame.target("new")
    assertEquals(GlassPlaneChange.BornFromNeighbour, born.change)
    assertEquals("b", born.bornFrom)
    // F3.12c: chi nasce parte dentro il donatore, più piccolo di lui (il bocciolo che cresce mentre esce).
    assertRect(b.bounds.scaledAbout(FluidMotion.PlaneReleaseBud), born.bounds)
    assertEquals(1f, born.materialize, 0f)
    assertRect(newcomer.bounds, born.contentBounds)
  }

  @Test fun distantOrExplicitAppearanceUsesLensAndStartsNinePercentWider() {
    val a = piece("a", 20f)
    val b = piece("b", 300f)
    val journey = GlassPlaneJourney(state(a), state(a, b))
    val first = journey.frame(0.0).target("b")
    assertEquals(GlassPlaneChange.Materialize, first.change)
    assertNull(first.bornFrom)
    assertEquals(44f * 1.09f, first.bounds.width, 0.001f)
    assertEquals(0f, first.materialize, 0f)
    // F3.2c: a 430 ms la comparsa è ferma a vista (entro lo 0,2% di `materialize`), e alla fine del viaggio è piena.
    assertEquals(430.0, journey.settleMs, 0.5)
    assertEquals(1f, journey.frame(430.0).target("b").materialize, FluidMotion.PlaneRestUnit + 0.0001f)
    assertEquals(1f, journey.frame(journey.durationMs + 0.01).target("b").materialize, 0f)
    val explicit = b.copy(bounds = Rect(68f, 40f, 112f, 84f), transition = GlassPlaneTransition.Materialize)
    assertEquals(GlassPlaneChange.Materialize, planeJourneyFrame(0.0, state(a), state(a, explicit)).target("b").change)
  }

  @Test fun compatibleDisappearingNeighbourMergesAndDistantPieceDematerializes() {
    val a = piece("a", 20f)
    val b = piece("b", 68f)
    val merging = GlassPlaneJourney(state(a, b), state(a))
    val exit = merging.frame(1000.0).pieces.single { it.flightId.sourceKey == "b" }
    assertEquals(GlassPlaneChange.MergeIntoNeighbour, exit.change)
    assertEquals("a", exit.mergesInto)
    // F3.12c: chi confluisce si rimpicciolisce di traverso mentre entra, e quando è dentro diventa il vicino: finisce
    // della sua misura, così le due sagome coincidono e l'unione non fa orecchie sul fianco di chi accoglie.
    assertRect(a.bounds, exit.bounds)
    assertRect(b.bounds, exit.contentBounds)
    val disappearing = GlassPlaneJourney(state(piece("far", 300f)), state())
    assertEquals(470.0, disappearing.settleMs, 0.5)
    assertTrue(disappearing.durationMs >= disappearing.settleMs)
    assertEquals(0f, disappearing.frame(470.0).pieces.single().materialize, FluidMotion.PlaneRestUnit + 0.0001f)
    assertEquals(0f, disappearing.frame(disappearing.durationMs + 0.01).pieces.single().materialize, 0f)
  }

  @Test fun identityTransitionOptsOutOfTheClock() {
    val target = piece("b", 20f).copy(transition = GlassPlaneTransition.Identity)
    val journey = GlassPlaneJourney(state(), state(target))
    assertEquals(0.0, journey.durationMs, 0.0)
    assertTrue(journey.frame(0.0).finished)
    assertRect(target.bounds, journey.frame(0.0).target("b").bounds)
  }

  @Test fun fieldCapacityAndStageStayFixedThroughTheWholeJourney() {
    val a = piece("a", 90f)
    val b = piece("a", 50f)
    val newborn = piece("b", 138f)
    val journey = GlassPlaneJourney(state(a), state(b, newborn))
    assertEquals(1, journey.fields.size)
    val field = journey.fields.single()
    assertEquals(GlassBodyKernel.Eight, field.kernel)
    assertContains(field.bounds, a.bounds)
    assertContains(field.bounds, b.bounds)
    assertContains(field.bounds, newborn.bounds)
    for (ms in 0..(journey.durationMs.toInt() + 4) step 2) {
      val frame = journey.frame(ms.toDouble())
      assertEquals(journey.fields, frame.fields)
      frame.pieces.forEach { assertContains(field.bounds, it.bounds) }
    }
  }

  @Test fun moreThanEightTransientPiecesArePartitionedWithoutDroppingAny() {
    // Sedici vetri che viaggiano tutti vicini (uno si sposta, uno nasce dal vicino): un componente solo, diviso.
    val a = (0..7).map { piece("a$it", it * 46f, y = 20f) }
    val b = (0..7).map { piece("a$it", it * 46f + 6f, y = 20f) } + (0..7).map { piece("n$it", it * 46f, y = 70f) }
    val journey = GlassPlaneJourney(GlassPlaneState(a, 12f, canvas), GlassPlaneState(b, 12f, canvas))
    assertEquals(16, journey.frame(70.0).pieces.size)
    assertEquals(16, journey.fields.sumOf { it.flights.size })
    assertTrue(journey.fields.size >= 2)
    assertTrue(journey.fields.all { it.flights.size <= 8 })
  }

  @Test fun appearingAndDisappearingGlassIsAlwaysAFieldOfItsOwn() {
    // F3.2c: un vetro che compare per lente accanto a uno che si sposta (entro lo spacing) non entra nel suo campo, così
    // la sua comparsa (un `materialize` per campo) è esatta.
    val a = piece("a", 20f)
    val moved = piece("a", 40f)
    val forced = piece("m", 92f).copy(transition = GlassPlaneTransition.Materialize)
    val journey = GlassPlaneJourney(state(a), state(moved, forced))
    assertEquals(2, journey.fields.size)
    val lensField = journey.fields.single { field -> field.flights.any { it.targetKey == "m" } }
    assertEquals(1, lensField.flights.size)
    assertEquals(GlassBodyKernel.One, lensField.kernel)
    // Più vetri che compaiono insieme, vicini: uno per campo.
    val many = GlassPlaneJourney(state(), state(piece("x", 20f), piece("y", 70f), piece("z", 120f)))
    assertEquals(3, many.fields.size)
    assertTrue(many.fields.all { it.flights.size == 1 && it.kernel == GlassBodyKernel.One })
  }

  @Test fun retargetPreservesEverySideAndItsVelocityIncludingOrthogonalMotion() {
    val a = state(piece("a", 20f))
    val b = state(piece("a", 140f, y = 70f, width = 70f).copy(radii = FluidCornerRadii(8f, 12f, 16f, 20f)))
    val c = state(piece("a", 60f, y = 110f, width = 90f))
    val journey = GlassPlaneJourney(a, b)
    val live = journey.frame(117.0).pieces.single()
    val next = journey.retarget(117.0, c)
    val restart = next.frame(0.0).pieces.single()
    assertRect(live.bounds, restart.bounds, 0f)
    assertEquals(live.radii, restart.radii)
    assertEquals(live.velocity, restart.velocity)
    assertTrue(abs(live.velocity.bounds.left) > 1f)
    // La spec Spring di Compose campiona a millisecondi: sotto quel passo una differenza finita è zero.
    // La velocità sopra è quella analitica; il primo passo visibile deve continuare nel verso precedente
    // anche se il nuovo bersaglio è indietro (una ripartenza da ferma lo sposterebbe subito all'indietro).
    val following = next.frame(1.0).pieces.single()
    assertTrue(following.bounds.left > restart.bounds.left)
    for (ms in 0..600 step 3) next.frame(ms.toDouble()).pieces.forEach { flying ->
      assertContains(next.fields.single().bounds, flying.bounds)
    }
    assertRect(c.pieces.single().bounds, next.frame(1000.0).pieces.single().bounds)
  }

  @Test fun interruptionEntryPointUsesTheLiveFrameAndKeepsAppearanceVelocity() {
    val a = state()
    val b = state(piece("b", 130f).copy(transition = GlassPlaneTransition.Materialize))
    val at = 150.0
    val old = planeJourneyFrame(at, a, b).target("b")
    val interruption = GlassPlaneInterruption(at, b)
    val next = planeJourneyFrame(at, a, b, interruption).target("b")
    assertRect(old.bounds, next.bounds, 0f)
    assertEquals(old.materialize, next.materialize, 0f)
    assertEquals(old.velocity, next.velocity)
    assertEquals(planeJourneyFrame(80.0, a, b), planeJourneyFrame(80.0, a, b, interruption))
  }

  @Test fun contextLastsThreeHundredMsAndSmallOvershootStaysInsideTheStage() {
    val journey = GlassPlaneJourney(state(piece("a", 20f)), state(piece("a", 80f, width = 100f)))
    // F3.2c: il bilancio è il riposo a vista; la coda sotto il mezzo pixel viene dopo e non si vede.
    assertEquals(300.0, journey.settleMs, 0.5)
    assertTrue(journey.durationMs >= journey.settleMs)
    assertFalse(journey.frame(299.0).finished)
    assertTrue(journey.frame(journey.durationMs + 0.01).finished)
    val maximum = (0..journey.durationMs.toInt()).maxOf { journey.frame(it.toDouble()).pieces.single().bounds.width }
    assertTrue("oltrepasso $maximum", maximum <= 101.3f)
    assertEquals(430.0, GlassPlaneJourney(state(), state(piece("a", 20f))).settleMs, 0.5)
  }

  @Test fun aClosingCapsuleRestsOnApplesRealTimeClockAndTheStartIsNotFrontLoaded() {
    // Il rilievo di F3.3 (`contesto-capsule-curva`): la capsula da 200 a 88 dp a densità 3, col bordo destro fermo, si
    // posava a vista a 133 ms e consumava il 39% della corsa nei primi 33; F3.2c l'aveva portata a 270–330 (R4 B2 (a)).
    // È un **cambio di larghezza** (F4A.5b): sulle due clip a tempo vero di Apple il bordo che si chiude fa il 2, 19, 61,
    // 84% della corsa a 33, 100, 200, 300 ms (tolto il trascinamento) e si posa a ~450 (1,5%). Il riposo a vista qui è lo
    // 0,5% della larghezza, la tolleranza di `glassmotion.py curve`: la larghezza non sente il trascinamento.
    val d = 3f
    val wide = GlassPlaneState(
      listOf(GlassPlanePiece("c", Rect(120f * d, 58f * d, 320f * d, 102f * d), FluidCornerRadii.all(22f * d), id = "c")),
      12f * d, Rect(0f, 0f, 360f * d, 160f * d),
    )
    val narrow = GlassPlaneState(
      listOf(GlassPlanePiece("c", Rect(232f * d, 58f * d, 320f * d, 102f * d), FluidCornerRadii.all(22f * d), id = "c")),
      12f * d, Rect(0f, 0f, 360f * d, 160f * d),
    )
    val journey = GlassPlaneJourney(wide, narrow)
    assertEquals(FluidMotion.PlaneResizeMillis.toDouble(), journey.settleMs, 25.0)
    val start = 200f * d
    val end = 88f * d
    fun width(ms: Double) = journey.frame(ms).pieces.single().bounds.width
    fun progress(ms: Double) = (start - width(ms)) / (start - end)
    val tolerance = 0.005f * abs(end - start)
    var rest = 0.0
    var ms = 0.0
    while (ms <= journey.durationMs + 1.0) {
      if (abs(width(ms) - end) > tolerance) rest = ms + 1.0
      ms += 1.0
    }
    assertTrue("riposo a vista (0,5%) a $rest ms", rest in 470.0..600.0)
    assertTrue("nei primi 33 ms il ${progress(33.0) * 100}% della corsa", progress(33.0) < 0.04f)
    assertTrue("a 100 ms il ${progress(100.0) * 100}%", progress(100.0) in 0.12f..0.30f)
    assertTrue("a 200 ms il ${progress(200.0) * 100}%", progress(200.0) in 0.52f..0.70f)
    assertTrue("a 300 ms il ${progress(300.0) * 100}%", progress(300.0) in 0.78f..0.92f)
    val overshoot = (end - (0..journey.durationMs.toInt()).minOf { width(it.toDouble()) }) / abs(end - start)
    assertTrue("superamento ${overshoot * 100}%", overshoot <= 0.013f)
  }

  @Test fun repeatedInterruptionsRetainTheCurrentJourneyRatherThanTheOriginalEndpoints() {
    val a = state(piece("a", 20f))
    val b = state(piece("a", 140f))
    val initial = GlassPlaneJourney(a, b)
    val reverse = initial.retarget(100.0, a)
    val live = reverse.frame(70.0).pieces.single()
    val again = reverse.retarget(70.0, b).frame(0.0).pieces.single()
    assertRect(live.bounds, again.bounds, 0f)
    assertEquals(live.velocity, again.velocity)
  }

  @Test fun interruptedMaterializeOnAReplacementNodeDoesNotDuplicateItsExplicitIdentity() {
    val from = state(piece("old-node", 100f, id = "same"))
    val to = state(piece("new-node", 150f, id = "same").copy(transition = GlassPlaneTransition.Materialize))
    val journey = GlassPlaneJourney(from, to)
    val live = journey.frame(130.0)
    assertEquals(2, live.pieces.size)
    assertEquals(1, live.pieces.count { it.piece.id == "same" })
    val restarted = journey.retarget(130.0, to).frame(0.0)
    live.pieces.forEach { previous ->
      val next = restarted.pieces.single { it.piece.key == previous.piece.key }
      assertRect(previous.bounds, next.bounds, 0f)
      assertEquals(previous.velocity, next.velocity)
      assertEquals(previous.materialize, next.materialize, 0f)
    }
  }

  // ---- F3.8a ---------------------------------------------------------------------------------------------------

  @Test fun appearanceAndDisappearanceFollowTheR4ClockWithoutAPause() {
    // S3: la comparsa finiva a vista a ~320 ms con una pausa a metà, la scomparsa a 240–290. `materialize` segue una
    // curva nel tempo: il contorno (0,3) a 67 ms, la lente piena (0,97) a 230, la fine a 430; via la lente da 130 a 400.
    val far = piece("far", 300f)
    val appear = GlassPlaneJourney(state(), state(far))
    fun up(ms: Double) = appear.frame(ms).target("far").materialize
    assertEquals(0f, up(0.0), 0f)
    assertEquals(0.3f, up(67.0), 0.01f)
    assertEquals(0.97f, up(230.0), 0.01f)
    assertEquals(1f, up(430.0), 0f)
    var last = 0f
    for (ms in 4..428 step 4) {
      val now = up(ms.toDouble())
      assertTrue("la comparsa non si ferma né torna indietro a $ms ms: $last -> $now", now > last)
      last = now
    }
    val disappear = GlassPlaneJourney(state(far), state())
    fun down(ms: Double) = disappear.frame(ms).pieces.single().materialize
    assertEquals(1f, down(0.0), 0f)
    assertEquals(0.97f, down(130.0), 0.01f)
    assertEquals(0.27f, down(400.0), 0.01f)
    assertEquals(0f, down(470.0), 0f)
    // La fase di chi compare e di chi scompare conta sul suo bilancio.
    assertEquals(0.5f, appear.frame(215.0).target("far").phase, 0.001f)
    assertEquals(0.5f, disappear.frame(235.0).pieces.single().phase, 0.001f)
  }

  @Test fun anInterruptedAppearanceDisappearsFromWhereItIs() {
    val far = piece("far", 300f)
    val appear = GlassPlaneJourney(state(), state(far))
    val live = appear.frame(150.0).target("far")
    assertTrue(live.materialize > 0.3f && live.materialize < 0.97f)
    val leaving = appear.retarget(150.0, state())
    val first = leaving.frame(0.0).pieces.single()
    assertEquals(GlassPlaneChange.Dematerialize, first.change)
    assertEquals(live.materialize, first.materialize, 0f)
    assertRect(live.bounds, first.bounds, 0f)
    val next = leaving.frame(8.0).pieces.single()
    assertTrue("riparte in continuo: ${first.materialize} -> ${next.materialize}",
      next.materialize < first.materialize && first.materialize - next.materialize < 0.05f)
    // Parte da metà della sua curva: dura meno dei 470 ms di una scomparsa da pieno.
    assertTrue(leaving.durationMs < 470.0)
    assertEquals(0f, leaving.frame(leaving.durationMs + 0.01).pieces.single().materialize, 0f)
  }

  @Test fun theTailLandsOnTheTargetWithoutAStep() {
    // S3: ~120 ms dopo il riposo a vista ogni lato arrivava a mezzo pixel dall'arrivo e scattava. Ora lo scarto si
    // spegne in continuo: fra un millisecondo e l'altro nessun lato si sposta di più di qualche centesimo di pixel.
    val journey = GlassPlaneJourney(state(piece("a", 20f)), state(piece("a", 140f, width = 90f)))
    val target = Rect(140f, 40f, 230f, 84f)
    var previous = journey.frame(journey.settleMs).pieces.single().bounds
    var worst = 0f
    var landing = 0f
    var ms = journey.settleMs + 1.0
    while (ms <= journey.durationMs + 3.0) {
      val now = journey.frame(ms).pieces.single().bounds
      val step = maxOf(abs(now.left - previous.left), abs(now.right - previous.right))
      worst = maxOf(worst, step)
      if (ms >= journey.durationMs - 20.0) landing = maxOf(landing, step)
      previous = now
      ms += 1.0
    }
    assertTrue("il passo più lungo della coda è $worst px al millisecondo", worst < 0.1f)
    assertTrue("negli ultimi 20 ms il vetro si posa, non scatta: $landing px al millisecondo", landing < 0.01f)
    assertRect(target, journey.frame(journey.durationMs + 0.01).pieces.single().bounds, 0f)
  }

  @Test fun stillPiecesKeepToThemselvesAndAreReported() {
    // «cerca» e «preferiti» fermi a 8 px mentre «play» compare lontano (S3): nessun campo li unisce, quindi nessun collo
    // nasce fra loro, e il contenitore sa che non cambiano.
    val a = piece("a", 20f)
    val b = piece("b", 72f)
    val far = piece("far", 300f)
    val journey = GlassPlaneJourney(state(a, b), state(a, b, far))
    assertEquals(setOf<Any>("a", "b"), journey.stillKeys)
    assertEquals(3, journey.fields.size)
    assertTrue(journey.fields.all { it.flights.size == 1 })
    assertTrue(journey.fields.filter { field -> field.flights.none { it.targetKey == "far" } }.all { it.still })
    assertTrue(journey.frame(100.0).target("a").still)
    assertFalse(journey.frame(100.0).target("far").still)
    // Un pezzo fermo entra nel campo di uno che cambia: «b» sta con chi nasce da lui, «a» no.
    val born = piece("c", 124f)
    val birth = GlassPlaneJourney(state(a, b), state(a, b, born))
    assertEquals(GlassPlaneChange.BornFromNeighbour, birth.frame(0.0).target("c").change)
    val fields = birth.fields.map { field -> field.flights.map { it.targetKey }.toSet() }
    assertTrue("campi: $fields", fields.contains(setOf<Any?>("b", "c")) && fields.contains(setOf<Any?>("a")))
    // F3.12a: il donatore «b» rincula e rimbalza, quindi non è fermo; «a», dall'altra parte, lo è e resta nel suo campo.
    assertEquals(setOf<Any>("a"), birth.stillKeys)
    assertFalse(birth.frame(200.0).target("b").still)
    assertTrue(birth.frame(200.0).target("a").still)
    // La distanza «larga» della tavola tiene fusi i vicini anche a riposo: lì i fermi stanno insieme, come prima.
    val wide = GlassPlaneJourney(state(a, b), state(a, b, far), GlassPlaneMotion(joinStill = true))
    assertTrue(wide.fields.any { field -> field.flights.map { it.targetKey }.toSet() == setOf<Any?>("a", "b") })
    // Chi si sposta, anche di poco, non è fermo.
    val nudged = GlassPlaneJourney(state(a, b), state(a, piece("b", 76f), far))
    assertEquals(setOf<Any>("a"), nudged.stillKeys)
  }

  @Test fun aUnionThatFormsOrDissolvesIsAJourneyEvenIfNothingMoves() {
    // «Unisci» e «Separa» passavano in un fotogramma (S3): nessun pezzo si muove, quindi il viaggio durava zero.
    val union = Any()
    val a = piece("a", 20f)
    val b = piece("b", 72f)
    val forming = GlassPlaneJourney(state(a, b), state(a.copy(unionId = union), b.copy(unionId = union)))
    assertEquals(300.0, forming.settleMs, 0.5)
    assertTrue(forming.durationMs >= 300.0)
    assertFalse(forming.frame(299.0).finished)
    assertTrue(forming.frame(forming.durationMs + 0.01).finished)
    val field = forming.fields.single()
    assertEquals(GlassPlaneUnionChange.Forming, field.union)
    assertEquals(2, field.flights.size)
    assertFalse(field.still)
    assertTrue(forming.stillKeys.isEmpty())
    val flying = forming.frame(150.0).target("a")
    assertNull(flying.unionFrom)
    assertEquals(union, flying.unionTo)
    assertEquals(0.5f, flying.phase, 0.001f)
    assertRect(a.bounds, flying.bounds, 0f)
    val dissolving = GlassPlaneJourney(state(a.copy(unionId = union), b.copy(unionId = union)), state(a, b))
    assertEquals(GlassPlaneUnionChange.Dissolving, dissolving.fields.single().union)
    assertEquals(300.0, dissolving.settleMs, 0.5)
    // Un'unione che resta non è un cambio, e i suoi pezzi fermi stanno nello stesso campo.
    val kept = GlassPlaneJourney(
      state(a.copy(unionId = union), b.copy(unionId = union)),
      state(a.copy(unionId = union), b.copy(unionId = union), piece("far", 300f)),
    )
    val together = kept.fields.single { it.flights.size == 2 }
    assertEquals(GlassPlaneUnionChange.None, together.union)
    assertTrue(together.still)
    // Chi nasce dentro un'unione ha il collo pieno, come prima: non è un'unione che nasce.
    val newcomer = piece("c", 124f).copy(unionId = union)
    val growing = GlassPlaneJourney(
      state(a.copy(unionId = union), b.copy(unionId = union)),
      state(a.copy(unionId = union), b.copy(unionId = union), newcomer),
    )
    assertTrue(growing.fields.all { it.union == GlassPlaneUnionChange.None })
  }

  @Test fun theFlightPhaseRunsOnTheVisibleRest() {
    val a = piece("a", 20f)
    val born = piece("b", 72f)
    val journey = GlassPlaneJourney(state(a), state(a, born))
    assertEquals(0f, journey.frame(0.0).target("b").phase, 0f)
    assertEquals(0.5f, journey.frame(journey.settleMs / 2.0).target("b").phase, 0.001f)
    assertEquals(1f, journey.frame(journey.settleMs).target("b").phase, 0.001f)
    // Chi non si muove conta sulla stessa fase del viaggio, perché il contenuto del vicino entri insieme al suo vetro.
    assertEquals(0.5f, journey.frame(journey.settleMs / 2.0).target("a").phase, 0.001f)
    // F3.12a: il contenuto di chi nasce conta dal primo rilascio. Un donatore che resta dov'è rilascia a 0,17, 0,12
    // prima di uno che si sposta (0,29, il caso su cui le fasi del contenuto sono tarate): la fase del contenuto di chi
    // nasce è avanti di tanto, quella degli altri è la fase del volo.
    val lead = FluidMotion.PlaneReleaseLead
    assertEquals(0.5f + lead, journey.frame(journey.settleMs / 2.0).target("b").contentPhase, 0.001f)
    assertEquals(lead, journey.frame(0.0).target("b").contentPhase, 0.001f)
    assertEquals(0.5f, journey.frame(journey.settleMs / 2.0).target("a").contentPhase, 0.001f)
    // Con un donatore che si sposta di più della sua altezza le due fasi coincidono.
    val travelling = GlassPlaneJourney(state(select), state(circle, triangle, done))
    for (key in listOf("circle", "triangle", "done")) {
      val flying = travelling.frame(200.0).target(key)
      assertEquals(key, flying.phase, flying.contentPhase, 0.001f)
    }
    // Un viaggio senza tempo è già arrivato.
    val identity = GlassPlaneJourney(state(), state(a.copy(transition = GlassPlaneTransition.Identity)))
    assertEquals(1f, identity.frame(0.0).target("a").phase, 0f)
  }

  // ---- F3.11: la coreografia della nascita e della confluenza ------------------------------------------------------

  private fun GlassPlaneFrame.source(key: Any) = pieces.single { it.flightId.sourceKey == key && it.flightId.targetKey == null }

  /** «Seleziona» diventa due tondi e «Fatto» (R4 B2 (d)), con quattro identità diverse, in pixel. */
  private val select = piece("select", 150f, width = 96f)
  private val circle = piece("circle", 97f)
  private val triangle = piece("triangle", 154f)
  private val done = piece("done", 211f, width = 88f)

  @Test fun aGlassThatLeavesIntoTheGlassBornFromItIsOneGlassChangingIdentity() {
    // Il centro del tondo di mezzo è più vicino a «Seleziona» di quello di «Fatto» (22 px contro 57), ma è «Fatto» ad
    // avere la sua misura: è lui a continuarlo. I due voli della staffetta coincidono dal primo all'ultimo fotogramma.
    val journey = GlassPlaneJourney(state(select), state(circle, triangle, done))
    var ms = 0.0
    while (ms <= journey.durationMs + 5.0) {
      val frame = journey.frame(ms)
      val leaving = frame.source("select")
      val heir = frame.target("done")
      assertEquals(GlassPlaneChange.MergeIntoNeighbour, leaving.change)
      assertEquals("done", leaving.mergesInto)
      assertEquals(GlassPlaneChange.BornFromNeighbour, heir.change)
      assertEquals("select", heir.bornFrom)
      assertTrue(leaving.relay && heir.relay)
      assertRect(leaving.bounds, heir.bounds, 0f)
      assertEquals(leaving.radii, heir.radii)
      assertFalse(frame.target("circle").relay)
      assertEquals("select", frame.target("circle").bornFrom)
      assertEquals("select", frame.target("triangle").bornFrom)
      ms += 7.0
    }
    assertRect(select.bounds, journey.frame(0.0).target("done").bounds, 0f)
    assertRect(done.bounds, journey.frame(journey.durationMs + 0.01).target("done").bounds, 0f)
    // La staffetta non è un rilascio: non ha un ritardo suo. È il donatore: va al suo posto con la molla del piano
    // (superamento entro l'1,3%), e in più rimbalza un poco dopo il rinculo dai due nati (F3.12a: nel render di Apple il
    // donatore di «Select → Done» supera il suo posto del 9% della corsa).
    assertFalse(journey.releaseDelays.containsKey("done"))
    val widest = (0..journey.durationMs.toInt()).maxOf { journey.frame(it.toDouble()).target("done").bounds.right }
    val rebound = widest - done.bounds.right
    assertTrue("il donatore supera l'arrivo di $rebound px",
      rebound <= 0.013f * 53f + 2f * FluidMotion.PlaneDonorRebound * 44f + 0.05f)
    assertTrue("il donatore rimbalza: $rebound px", rebound > 1.5f)
    // Il verso opposto è la stessa staffetta: «Fatto» torna «Seleziona», e i due tondi gli confluiscono dentro.
    val back = GlassPlaneJourney(state(circle, triangle, done), state(select)).frame(40.0)
    assertTrue(back.source("done").relay && back.target("select").relay)
    assertEquals("done", back.target("select").bornFrom)
    assertEquals("select", back.source("circle").mergesInto)
    assertEquals("select", back.source("triangle").mergesInto)
    assertFalse(back.source("circle").relay)
    // A misure uguali vince il centro più vicino, com'era: fra due tondi uguali chi se ne va confluisce nel più vicino.
    val near = GlassPlaneJourney(state(piece("a", 20f), piece("b", 72f), piece("c", 124f)), state(piece("a", 20f), piece("c", 120f)))
    assertEquals("c", near.frame(10.0).source("b").mergesInto)
  }

  @Test fun theDonorGoesFirstAndTheBornLeaveItLaterTheFarthestFirst() {
    val journey = GlassPlaneJourney(state(select), state(circle, triangle, done))
    // F3.12c: il riposo a vista è il ritorno del superamento di Apple sull'orologio corretto (540 ms); la coda viene dopo.
    val budget = FluidMotion.PlaneReleaseMillis.toDouble()
    assertEquals(540.0, journey.settleMs, 0.5)
    assertTrue("la coda tiene il palco oltre il riposo a vista: ${journey.durationMs}", journey.durationMs > budget + 150.0)
    // Il donatore si sposta di più della sua altezza: dopo l'attesa (0,23) va per primo, e il più lontano è rilasciato
    // quando ha fatto un pezzo di strada (+0,08: 0,31 della fase, R4 B2 (d) sull'orologio corretto); il più vicino dopo
    // (+0,09: 0,40).
    val first = journey.releaseDelays.getValue("circle")
    val second = journey.releaseDelays.getValue("triangle")
    assertEquals(0.31 * budget, first, 0.5)
    assertEquals(0.40 * budget, second, 0.5)
    val travel = done.bounds.center.x - select.bounds.center.x
    // L'attesa: per i primi 0,23 il donatore non viaggia.
    val wait = FluidMotion.PlaneReleaseDelay * budget
    assertRect(select.bounds, journey.frame(wait * 0.7).target("done").bounds, 0.05f)
    assertEquals(select.bounds.center.x, journey.frame(wait).target("done").bounds.center.x, 0.6f)
    assertTrue("dopo l'attesa il donatore parte", journey.frame(wait + 40.0).target("done").bounds.center.x > select.bounds.center.x + 3f)
    // Finché non è rilasciato, chi nasce sta nel donatore in volo, **più piccolo** (F3.12c, il bocciolo): al più se ne
    // sporge di [FluidMotion.PlaneReleaseCreep] della strada che gli resta.
    val bud = FluidMotion.PlaneReleaseBud
    var ms = 0.0
    while (ms <= first) {
      val frame = journey.frame(ms)
      val donor = frame.target("done").bounds
      val born = frame.target("circle").bounds
      val lean = FluidMotion.PlaneReleaseCreep * (donor.left - circle.bounds.left)
      assertTrue("t=$ms: il tondo ($born) sta nel donatore ($donor)", born.left >= donor.left - lean - 0.01f && born.right <= donor.right)
      assertTrue("t=$ms: ed è più piccolo di lui (${born.height})", born.height < circle.bounds.height * (bud + FluidMotion.PlaneReleaseCreep * (1f - bud)) + 0.05f)
      ms += 3.0
    }
    // Al rilascio il donatore ha già fatto un quarto della strada, e il tondo parte da lì, non dal riquadro di partenza.
    val release = journey.frame(first)
    val donorAtRelease = release.target("done").bounds
    assertTrue("al rilascio il donatore è al ${(donorAtRelease.center.x - select.bounds.center.x) / travel * 100}% del viaggio",
      donorAtRelease.center.x - select.bounds.center.x > 0.15f * travel)
    val from = release.target("circle").bounds
    assertTrue("il tondo parte da ${from.left}, il donatore era partito da ${select.bounds.left}", from.left > select.bounds.left + 5f)
    val budLeft = donorAtRelease.left + (1f - bud) / 2f * circle.bounds.width
    assertEquals(budLeft - FluidMotion.PlaneReleaseCreep * (budLeft - circle.bounds.left), from.left, 0.05f)
    // Prima del rilascio il blob è andato verso il posto del donatore: il suo bordo sinistro è rientrato (di poco: il
    // donatore è partito solo dopo l'attesa, e chi nasce si sporge dall'altra parte).
    assertTrue("il tondo parte da ${from.left}", from.left > journey.frame(0.0).target("circle").bounds.left + 0.5f)
    // Il triangolo è ancora nel donatore quando il tondo è già partito.
    val between = journey.frame(second - 1.0)
    assertTrue(between.target("circle").bounds.left < from.left - 1f)
    val host = between.target("done").bounds
    assertTrue("il triangolo (${between.target("triangle").bounds}) è ancora nel donatore ($host)",
      between.target("triangle").bounds.left >= host.left - FluidMotion.PlaneReleaseCreep * (host.left - triangle.bounds.left) - 0.01f)
    // Nessun incrocio, dal primo all'ultimo fotogramma: tondo, triangolo, donatore restano in quest'ordine.
    val field = journey.fields.single()
    ms = 0.0
    while (ms <= journey.durationMs + 5.0) {
      val frame = journey.frame(ms)
      val c = frame.target("circle").bounds
      val t = frame.target("triangle").bounds
      val d = frame.target("done").bounds
      assertTrue("t=$ms: ordine dei centri ${c.center.x}, ${t.center.x}, ${d.center.x}",
        c.center.x <= t.center.x + 0.001f && t.center.x <= d.center.x + 0.001f)
      // Il palco contiene tutto, superamento compreso.
      frame.pieces.forEach { assertContains(field.bounds, it.bounds) }
      assertEquals(journey.fields, frame.fields)
      ms += 1.0
    }
    for (target in listOf(circle, triangle, done)) {
      assertRect(target.bounds, journey.frame(journey.durationMs + 0.01).target(target.key).bounds, 0f)
    }
  }

  @Test fun aBornGlassIsReleasedWithASpringThatOvershootsAndReturns() {
    // Il caso tipo: un tasto fermo ne fa nascere uno più largo alla sua destra (R4 B2 (b), 4:49).
    val a = piece("a", 100f)
    val b = piece("b", 152f, width = 88f)
    val journey = GlassPlaneJourney(state(a), state(a, b))
    val delay = journey.releaseDelays.getValue("b")
    val budget = FluidMotion.PlaneReleaseMillis.toDouble()
    assertEquals("il ritorno di Apple a tempo vero, sull'orologio corretto", 540.0, budget, 0.0)
    assertEquals("un donatore fermo rilascia alla fine dell'attesa, a 0,23 del bilancio", 0.23 * budget, delay, 0.5)
    // F3.12c: il riposo a vista è il ritorno; la coda di Apple viene dopo e il viaggio finisce quando lo scarto è sotto
    // il mezzo pixel (qui, coi pixel di un tasto da 44, ~700 ms; il donatore torna fino a ~860).
    assertEquals(budget, journey.settleMs, 0.5)
    assertTrue("la coda viene dopo il ritorno: ${journey.durationMs}", journey.durationMs in budget + 120.0..budget + 400.0)
    fun born(ms: Double) = journey.frame(ms).target("b")
    fun donor(ms: Double) = journey.frame(ms).target("a")
    // Parte dentro il donatore, più piccolo di lui (F3.12c, il bocciolo).
    assertRect(a.bounds.scaledAbout(FluidMotion.PlaneReleaseBud), born(0.0).bounds)
    // Prima del rilascio si sporge appena dal suo posto nel donatore (che intanto comincia a rinculare verso di lui), con
    // una rampa liscia: parte da fermo, è a metà a metà dell'attesa, e al rilascio è fermo rispetto al donatore.
    assertEquals(0f, born(0.0).velocity.bounds.right, 0f)
    fun budRight(ms: Double) = donor(ms).bounds.center.x + FluidMotion.PlaneReleaseBud * donor(ms).bounds.width / 2f
    fun lean(ms: Double) = (born(ms).bounds.right - budRight(ms)) / (b.bounds.right - budRight(ms))
    assertEquals(FluidMotion.PlaneReleaseCreep, lean(delay), 0.001f)
    assertEquals(FluidMotion.PlaneReleaseCreep / 2f, lean(delay / 2.0), 0.001f)
    // Al rilascio lo sporgersi è fermo: del donatore, che in quell'istante rincula verso di lui, il vetro ha la velocità
    // meno la parte di strada che gli resta (si sporge di una frazione di una distanza che si accorcia).
    val following = donor(delay).velocity.bounds.right
    val apart = born(delay).velocity.bounds.right - following
    assertEquals("al rilascio lo sporgersi è fermo", -FluidMotion.PlaneReleaseCreep * following, apart, 0.05f)
    // Il rilascio continua da lì, senza scalini di posizione né di velocità.
    val before = born(delay - 0.01)
    val after = born(delay + 0.01)
    assertEquals(before.bounds.right, after.bounds.right, 0.05f)
    assertEquals(before.velocity.bounds.right, after.velocity.bounds.right, 0.05f * abs(after.velocity.bounds.right) + 1f)
    // I tempi di Apple a tempo vero sull'orologio corretto (R4 B2 (b): arrivo a ~283–296 ms, colmo a ~367, ritorno a
    // ~537–559): il lato di testa arriva al suo posto a ~0,55 del bilancio, lo supera del 9,5% della sua corsa a ~0,68 (il
    // resto del 13% di Apple è il rigonfiamento del pezzo nato, che non sta in questa geometria: vedi `swell`), ripassa
    // per il suo posto al ritorno, poi la coda: un secondo passaggio dall'altra parte, piccolo, e fermo.
    val run = b.bounds.right - born(delay).bounds.right
    var peak = 0f
    var peakMs = 0.0
    var trail = 0f
    var centre = 0f
    var arrival = Double.NaN
    var back = Double.NaN
    var under = 0f
    var underMs = 0.0
    var ms = 0.0
    while (ms <= journey.durationMs) {
      val bounds = born(ms).bounds
      if (bounds.right > peak) {
        peak = bounds.right
        peakMs = ms
      }
      if (arrival.isNaN() && bounds.right >= b.bounds.right) arrival = ms
      if (!arrival.isNaN() && ms > peakMs && back.isNaN() && bounds.right < b.bounds.right) back = ms
      if (!back.isNaN() && b.bounds.right - bounds.right > under) {
        under = b.bounds.right - bounds.right
        underMs = ms
      }
      trail = maxOf(trail, bounds.left - b.bounds.left)
      centre = maxOf(centre, bounds.center.x - b.bounds.center.x)
      ms += 1.0
    }
    val overshoot = (peak - b.bounds.right) / run
    println("F3.12c rilascio: arrivo a $arrival ms, colmo a $peakMs ms (+${overshoot * 100}%), ritorno a $back ms, " +
      "coda ${under / run * 100}% a $underMs ms, fine a ${journey.durationMs} ms; centro oltre di $centre px")
    assertTrue("superamento del ${overshoot * 100}% a $peakMs ms", overshoot in 0.085f..0.115f)
    assertTrue("il colmo cade a $peakMs ms", peakMs in 350.0..385.0)
    assertTrue("arriva al suo posto a $arrival ms", arrival in 280.0..305.0)
    assertTrue("ripassa per il suo posto a $back ms", back in 525.0..560.0)
    assertTrue("la coda va dall'altra parte di ${under / run * 100}% a $underMs ms", under / run in 0.004f..0.013f && underMs in 580.0..650.0)
    assertTrue("dopo il colmo torna", born(450.0).velocity.bounds.right < 0f)
    // Il vetro viaggia rigido: anche il lato di coda supera il suo arrivo, e il centro (dove sta il contenuto) con lui.
    // Fino a F3.11 il lato di coda si fermava all'arrivo e il contenuto restava fermo dentro un vetro che si allungava.
    val trailRun = b.bounds.left - a.bounds.left
    assertTrue("il lato di coda supera di ${trail / trailRun * 100}%", trail / trailRun in 0.06f..0.13f)
    assertTrue("il centro supera il suo posto di $centre px", centre > 0.07f * (b.bounds.center.x - a.bounds.center.x))
    assertRect(b.bounds, born(journey.durationMs + 0.01).bounds, 0f)
    assertEquals(0f, born(journey.durationMs + 0.01).velocity.bounds.right, 0f)
    // La coda arriva al suo posto in continuo: negli ultimi fotogrammi lo scarto è sotto il mezzo pixel e non scatta.
    var previous = born(budget).bounds.right
    ms = budget + 1.0
    while (ms <= journey.durationMs + 2.0) {
      val now = born(ms).bounds.right
      assertTrue("t=$ms: la coda non scatta ($previous -> $now)", abs(now - previous) < 0.2f)
      if (ms >= journey.durationMs - 40.0) assertEquals("t=$ms: sotto il mezzo pixel", b.bounds.right, now, 0.5f)
      previous = now
      ms += 1.0
    }
    // Il palco contiene il superamento e la coda.
    val field = journey.fields.single()
    assertTrue(field.bounds.right >= peak)
    ms = 0.0
    while (ms <= journey.durationMs + 5.0) {
      journey.frame(ms).pieces.forEach { assertContains(field.bounds, it.bounds) }
      ms += 1.0
    }
    // Il bilancio del rilascio segue quello del contesto.
    val slow = GlassPlaneJourney(state(a), state(a, b), GlassPlaneMotion(contextMs = 600.0))
    assertEquals(0.23 * 1080.0, slow.releaseDelays.getValue("b"), 0.5)
    assertEquals(1080.0, slow.settleMs, 0.5)
  }

  // ---- F3.12a e F3.12c: i tempi di Apple, il donatore che rincula, il pezzo che arriva gonfio ----------------------

  @Test fun theDonorRecoilsTowardTheBornGlassAndReboundsAsAWhole() {
    // R4 B2 (b), le due nascite a tempo vero sull'orologio corretto (F3.12c): il tondo che resta va verso chi esce di
    // ~0,10 D (al colmo a ~260 ms), poi rimbalza di ~0,07 D dall'altra parte (~480), torna con un ultimo piccolo passaggio
    // verso chi è nato (~710) e si ferma (~860). Si sposta tutto il vetro: la sua misura cambia solo nel rigonfiamento.
    val a = piece("a", 100f)
    val b = piece("b", 152f, width = 88f)
    val journey = GlassPlaneJourney(state(a), state(a, b))
    var toward = 0f
    var towardMs = 0.0
    var away = 0f
    var awayMs = 0.0
    var settle = 0f
    var settleMs = 0.0
    var previous = a.bounds.left
    var ms = 0.0
    while (ms <= journey.durationMs) {
      val donor = journey.frame(ms).target("a")
      assertEquals("t=$ms: il donatore non cambia misura", a.bounds.width, donor.bounds.width, 0.001f)
      assertEquals(a.bounds.top, donor.bounds.top, 0f)
      val shift = donor.bounds.left - a.bounds.left
      if (ms < 400.0 && shift > toward) {
        toward = shift
        towardMs = ms
      }
      if (-shift > away) {
        away = -shift
        awayMs = ms
      }
      if (ms > 600.0 && shift > settle) {
        settle = shift
        settleMs = ms
      }
      assertTrue("t=$ms: nessuno scatto (${previous} -> ${donor.bounds.left})", abs(donor.bounds.left - previous) < 0.1f)
      // Non è fermo: il suo contenuto lo segue (il palco lo porta, e `fluidGlassContentFlightOf` lo sposta).
      assertFalse(donor.still)
      previous = donor.bounds.left
      ms += 1.0
    }
    println("F3.12c donatore: rincula di $toward px a $towardMs ms, rimbalza di $away px a $awayMs ms, " +
      "ultimo passaggio $settle px a $settleMs ms, fermo a ${journey.durationMs} ms")
    assertEquals("rincula verso chi esce di 0,10 del suo lato corto", FluidMotion.PlaneDonorRecoil * 44f, toward, 0.02f)
    assertTrue("il colmo del rinculo cade a $towardMs ms", towardMs in 240.0..280.0)
    assertEquals("rimbalza oltre di 0,07", FluidMotion.PlaneDonorRebound * 44f, away, 0.02f)
    assertTrue("il rimbalzo viene dopo il colmo di chi è nato ($awayMs ms)", awayMs in 455.0..505.0)
    assertEquals("e torna con un ultimo piccolo passaggio", FluidMotion.PlaneDonorSettle * 44f, settle, 0.02f)
    assertTrue("a $settleMs ms", settleMs in 680.0..740.0)
    assertRect(a.bounds, journey.frame(journey.durationMs).target("a").bounds, 0f)
    assertEquals(0f, journey.frame(journey.durationMs).target("a").velocity.bounds.left, 0f)
    // Parte da fermo.
    assertEquals(0f, journey.frame(0.0).target("a").velocity.bounds.left, 0f)
    // Con una spec dell'app il donatore resta dov'è.
    val plain = GlassPlaneJourney(state(a), state(a, b), GlassPlaneMotion(spec = androidx.compose.animation.core.tween(200)))
    assertRect(a.bounds, plain.frame(100.0).target("a").bounds, 0f)
    assertTrue(plain.frame(100.0).target("a").still)
    // Un donatore contro il bordo del contenitore non ne esce rimbalzando.
    val edge = piece("a", 0.5f)
    val next = piece("b", 52.5f, width = 88f)
    val cornered = GlassPlaneJourney(state(edge), state(edge, next))
    val leftmost = (0..cornered.durationMs.toInt()).minOf { cornered.frame(it.toDouble()).target("a").bounds.left }
    assertTrue("arriva a $leftmost", leftmost >= canvas.left - 0.001f)
  }

  @Test fun everyoneInABirthHasItsOwnSwellInsteadOfTheCommonOne() {
    // F3.12c, R4 B2 (b), il contorno alto delle due nascite a tempo vero: il pezzo nato passa per la sua misura a ~286
    // ms, è alto +19% a ~385, va sotto del 3% a ~680 e si ferma; il donatore si gonfia poco (+4% a ~260, sotto del 2,5%
    // a ~550). Né l'uno né l'altro prendono il rigonfiamento comune del carattere (`swellShare`), che ha il colmo a 0,45
    // del viaggio per tutti; un vetro che non fa parte della nascita lo prende tutto.
    val a = piece("a", 100f)
    val b = piece("b", 152f, width = 88f)
    val c = piece("c", 300f)
    val journey = GlassPlaneJourney(state(a, c), state(a, b, piece("c", 330f)))
    var peak = 0f
    var peakMs = 0.0
    var low = 0f
    var lowMs = 0.0
    var first = Double.NaN
    var donorPeak = 0f
    var donorPeakMs = 0.0
    var donorLow = 0f
    var previous = 0f
    var ms = 0.0
    while (ms <= journey.durationMs) {
      val frame = journey.frame(ms)
      assertEquals(0f, frame.target("a").swellShare, 0f)
      assertEquals(0f, frame.target("b").swellShare, 0f)
      assertEquals(1f, frame.target("c").swellShare, 0f)
      assertEquals(0f, frame.target("c").swell, 0f)
      val swell = frame.target("b").swell
      // Sale in ~95 ms dal suo posto al colmo: al più 0,035 al millisecondo (0,3 a 120 Hz, poco più di mezzo dp).
      assertTrue("t=$ms: in continuo ($previous -> $swell)", abs(swell - previous) < 0.04f)
      previous = swell
      if (first.isNaN() && swell > 0f) first = ms
      if (swell > peak) {
        peak = swell
        peakMs = ms
      }
      if (swell < low) {
        low = swell
        lowMs = ms
      }
      val breath = frame.target("a").swell
      if (breath > donorPeak) {
        donorPeak = breath
        donorPeakMs = ms
      }
      donorLow = minOf(donorLow, breath)
      ms += 1.0
    }
    println("F3.12c rigonfiamento: il nato da $first ms, colmo $peak a $peakMs ms, sotto $low a $lowMs ms; " +
      "il donatore $donorPeak a $donorPeakMs ms, sotto $donorLow")
    assertEquals(FluidMotion.PlaneReleaseSwell, peak, 0.01f)
    assertTrue("comincia quando il pezzo arriva al suo posto ($first ms)", first in 265.0..300.0)
    assertTrue("il colmo cade dopo quello del superamento ($peakMs ms)", peakMs in 360.0..400.0)
    // Poi va sotto la sua misura (in Apple −3% a ~680 ms): qui, coi pixel di un tasto da 44, quello scarto è sotto il
    // pixel e la coda comincia già a spegnerlo, quindi è un poco meno profondo e cade prima.
    assertTrue("poi va sotto la sua misura ($low a $lowMs ms)", low in -0.25f..-0.08f && lowMs in 590.0..700.0)
    assertEquals(FluidMotion.PlaneDonorBreath, donorPeak, 0.005f)
    assertTrue("il donatore al colmo a $donorPeakMs ms", donorPeakMs in 240.0..280.0)
    assertEquals(-FluidMotion.PlaneDonorBreathUnder, donorLow, 0.005f)
    assertEquals(0f, journey.frame(journey.durationMs).target("b").swell, 0f)
    assertEquals(0f, journey.frame(journey.durationMs).target("a").swell, 0f)
    assertEquals(0f, journey.frame(journey.releaseDelays.getValue("b")).target("b").swell, 0f)
    // Un viaggio che riparte al colmo lo porta con sé e lo spegne, senza scalini.
    val live = journey.frame(peakMs).target("b")
    val back = journey.retarget(peakMs, state(a, piece("c", 330f)))
    assertEquals(live.swell, back.frame(0.0).source("b").swell, 0f)
    // «b» ora confluisce in «a»: quando è dentro diventa «a», rigonfiamento compreso (F3.12c), così le due sagome
    // coincidono. Prima si spegne soltanto; sempre senza scalini.
    val regrow = FluidMotion.PlaneMergeCoreRegrowFrom * FluidMotion.PlaneReleaseMillis
    val inside = FluidMotion.PlaneMergeCoreRegrowTo * FluidMotion.PlaneReleaseMillis
    var falling = live.swell
    ms = 1.0
    while (ms <= back.durationMs) {
      val frame = back.frame(ms)
      val now = frame.source("b").swell
      assertTrue("t=$ms: senza scalini ($falling -> $now)", abs(falling - now) < 0.03f)
      if (ms < regrow) assertTrue("t=$ms: si spegne ($falling -> $now)", now <= falling + 1e-6f)
      if (ms >= inside) assertEquals("t=$ms: è il vicino", frame.target("a").swell, now, 1e-5f)
      falling = now
      ms += 1.0
    }
    assertEquals(0f, falling, 0f)
    // Chi riparte prendendo una parte del comune diversa da prima si porta la differenza: la somma col comune (che riparte
    // da dov'era) non salta.
    val common = 0.6f
    val again = journey.retarget(peakMs, state(a, b, piece("c", 330f)), commonSwell = common)
    for (key in listOf("a", "b", "c")) {
      val was = journey.frame(peakMs).target(key)
      val now = again.frame(0.0).target(key)
      assertEquals(key, was.swell + was.swellShare * common, now.swell + now.swellShare * common, 1e-5f)
    }
    // Con una spec dell'app niente rigonfiamento proprio.
    val plain = GlassPlaneJourney(state(a), state(a, b), GlassPlaneMotion(spec = androidx.compose.animation.core.tween(200)))
    assertTrue((0..200).all { plain.frame(it.toDouble()).target("b").swell == 0f && plain.frame(it.toDouble()).target("b").swellShare == 1f })
  }

  @Test fun aLaterReleaseHasAFasterSpringSoThePeaksFallTogether() {
    // R4 B2 (d), sull'orologio corretto: il tondo è rilasciato a 0,31 e il triangolo a 0,40, e i due colmi cadono
    // insieme (0,72–0,78): la molla sta fra il rilascio e il ritorno.
    val journey = GlassPlaneJourney(state(select), state(circle, triangle, done))
    fun peakOf(key: String, place: Rect): Double =
      (0..journey.durationMs.toInt()).minByOrNull { journey.frame(it.toDouble()).target(key).bounds.left - place.left }!!.toDouble()
    val far = peakOf("circle", circle.bounds)
    val near = peakOf("triangle", triangle.bounds)
    val budget = FluidMotion.PlaneReleaseMillis.toDouble()
    println("F3.12c colmi: tondo a $far ms, triangolo a $near ms")
    assertTrue("il colmo del tondo a ${far / budget}", far / budget in 0.66..0.78)
    assertTrue("il colmo del triangolo a ${near / budget}", near / budget in 0.68..0.82)
    assertTrue("cadono insieme: $far e $near ms", abs(far - near) < 45.0)
    // Tutti e due superano il loro posto, e alla fine ci sono.
    assertTrue(journey.frame(far).target("circle").bounds.left < circle.bounds.left - 2f)
    assertTrue(journey.frame(near).target("triangle").bounds.left < triangle.bounds.left - 0.3f)
    // Un viaggio che riparte a metà non aspetta: i vetri continuano da dove sono, con la velocità che hanno.
    val at = 200.0
    val live = journey.frame(at)
    val again = journey.retarget(at, state(circle, triangle, done))
    for (key in listOf("circle", "triangle", "done")) {
      assertRect(live.target(key).bounds, again.frame(0.0).target(key).bounds, 0f)
      assertEquals(live.target(key).velocity, again.frame(0.0).target(key).velocity)
    }
    val moving = live.target("done").velocity.bounds.right
    assertTrue(moving > 50f)
    assertTrue("il donatore continua subito", again.frame(8.0).target("done").bounds.right > live.target("done").bounds.right + 0.3f)
  }

  @Test fun theOvershootOfAReleaseStaysInsideTheStageRoomNotTheContainer() {
    // Giro di correzione di F3 (R3 di C, rilievo 2 di A). Fino a F3.12c il tetto era il riquadro del **contenitore**, e un
    // contenitore che fascia la sua riga (la galleria, il KDoc, le barre di F4A) tagliava il superamento al 105% e la
    // spinta a 1–2 dp. Ora il tetto è il **posto del palco** (`GlassPlaneState.room`, la finestra nel contenitore): un
    // vetro che nasce a due pixel dal bordo della finestra non lo oltrepassa, uno che nasce a filo del contenitore sì.
    val a = piece("a", 258f)
    val b = piece("b", 310f, width = 88f)
    val journey = GlassPlaneJourney(state(a), state(a, b))
    val furthest = (0..journey.durationMs.toInt()).maxOf { journey.frame(it.toDouble()).target("b").bounds.right }
    assertTrue("arriva a $furthest, la finestra finisce a ${canvas.right}", furthest <= canvas.right + 0.01f)
    assertTrue(furthest > b.bounds.right)
    assertRect(b.bounds, journey.frame(journey.durationMs + 0.01).target("b").bounds, 0f)
    // Lo stesso vetro in un contenitore che finisce col suo bordo, dentro una finestra larga: esce dal contenitore quanto
    // in una scena larga, fino al 9,5% della molla.
    val wide = Rect(0f, 0f, 800f, 180f)
    fun wrapped(vararg p: GlassPlanePiece) = GlassPlaneState(p.toList(), 12f, Rect(256f, 38f, 398f, 86f), 4, room = wide)
    val free = GlassPlaneJourney(wrapped(a), wrapped(a, b))
    val beyond = (0..free.durationMs.toInt()).maxOf { free.frame(it.toDouble()).target("b").bounds.right }
    val run = b.bounds.right - (a.bounds.center.x + a.bounds.width * FluidMotion.PlaneReleaseBud / 2f)
    assertTrue("esce dal contenitore di ${beyond - 398f} px (corsa $run)", beyond - 398f > 0.06f * run)
  }

  @Test fun aWrappedContainerKeepsTheChoreographyOfAWideOne() {
    // La prova differenziale (R3 di C): gli stessi pezzi in un contenitore che fascia la riga e in uno largo, con la stessa
    // finestra, fanno lo **stesso viaggio**, fotogramma per fotogramma: il riquadro del contenitore non conta più per il
    // superamento, la spinta e il rimbalzo del donatore. Col tetto di F3.12c (il posto del palco uguale al contenitore
    // stretto) il superamento del nato era lo 0,5% della corsa e la spinta di chi accoglie quasi niente.
    val window = Rect(0f, 0f, 800f, 300f)
    val donor = piece("a", 100f)
    val born = piece("b", 152f, width = 88f)
    fun tight(capped: Boolean, vararg p: GlassPlanePiece): GlassPlaneState {
      val box = Rect(p.minOf { it.bounds.left } - 4f, 36f, p.maxOf { it.bounds.right }, 88f)
      return GlassPlaneState(p.toList(), 12f, box, 4, room = if (capped) box else window)
    }
    for ((before, after) in listOf(arrayOf(donor) to arrayOf(donor, born), arrayOf(donor, born) to arrayOf(donor))) {
      val large = GlassPlaneJourney(
        GlassPlaneState(before.toList(), 12f, window, 4), GlassPlaneState(after.toList(), 12f, window, 4),
      )
      val narrow = GlassPlaneJourney(tight(false, *before), tight(false, *after))
      val capped = GlassPlaneJourney(tight(true, *before), tight(true, *after))
      assertEquals(large.durationMs, narrow.durationMs, 0.0)
      assertEquals(large.settleMs, narrow.settleMs, 0.0)
      var ms = 0.0
      while (ms <= large.durationMs + 1.0) {
        val l = large.frame(ms).pieces
        val n = narrow.frame(ms).pieces
        assertEquals(l.size, n.size)
        for (i in l.indices) {
          assertEquals("t=$ms", l[i].bounds, n[i].bounds)
          assertEquals("t=$ms", l[i].radii, n[i].radii)
          assertEquals("t=$ms", l[i].swell, n[i].swell, 0f)
        }
        ms += 1.0
      }
      // E il tetto di prima tagliava davvero: il lato di testa del nato, o la spinta di chi accoglie.
      if (after.size == 2) {
        val free = (0..large.durationMs.toInt()).maxOf { large.frame(it.toDouble()).target("b").bounds.right }
        val cut = (0..capped.durationMs.toInt()).maxOf { capped.frame(it.toDouble()).target("b").bounds.right }
        val run = born.bounds.right - donor.bounds.center.x
        assertTrue("superamento libero ${free - born.bounds.right} px", (free - born.bounds.right) / run > 0.05f)
        assertTrue("col tetto di F3.12c ${cut - born.bounds.right} px", (cut - born.bounds.right) / run < 0.01f)
      } else {
        val push = (0..large.durationMs.toInt()).minOf { large.frame(it.toDouble()).target("a").bounds.left }
        val pinned = (0..capped.durationMs.toInt()).minOf { capped.frame(it.toDouble()).target("a").bounds.left }
        assertTrue("spinta libera ${donor.bounds.left - push} px", donor.bounds.left - push > 6f)
        assertTrue("col tetto di F3.12c ${donor.bounds.left - pinned} px", donor.bounds.left - pinned <= 4.01f)
      }
    }
  }

  @Test fun theBirthWaistNarrowsMonotonicallyUntilTheBreakAndNeverReopens() {
    // Giro di correzione di F3, punto 10 (scelta di Alessio: «C corretta»). La vita della goccia si stringe da ~200 ms
    // (0,31 del mezzo periodo dopo il rilascio) fino a ~350 (0,93) e poi resta stretta fino alla rottura: il prototipo C
    // tornava a uno a 367 ms e la vita si riallargava. Chi non nasce non ha vita, e una spec dell'app nemmeno.
    val a = piece("a", 100f)
    val b = piece("b", 152f, width = 88f)
    val journey = GlassPlaneJourney(state(a), state(a, b))
    val delay = journey.releaseDelays.getValue("b")
    val half = FluidMotion.PlaneReleaseSwing * (FluidMotion.PlaneReleaseMillis - delay)
    val from = delay + FluidMotion.PlaneReleaseWaistFrom * half
    val to = delay + FluidMotion.PlaneReleaseWaistTo * half
    assertEquals("comincia a ~200 ms", 200.0, from, 3.0)
    assertEquals("ed è al più stretto a ~350 ms", 350.0, to, 3.0)
    var previous = 1f
    var ms = 0.0
    while (ms <= journey.durationMs) {
      val frame = journey.frame(ms)
      val waist = frame.target("b").birthWaist
      assertTrue("t=$ms: la vita non si riapre ($previous -> $waist)", waist <= previous + 1e-6f)
      if (ms <= from) assertEquals("t=$ms", 1f, waist, 0f)
      if (ms >= to) assertEquals("t=$ms", FluidMotion.PlaneReleaseWaist, waist, 1e-6f)
      assertEquals("il donatore non ha vita sua", 1f, frame.target("a").birthWaist, 0f)
      previous = waist
      ms += 1.0
    }
    val custom = GlassPlaneJourney(state(a), state(a, b), GlassPlaneMotion(spec = androidx.compose.animation.core.tween(300)))
    assertTrue((0..300).all { custom.frame(it.toDouble()).target("b").birthWaist == 1f })
    // Un viaggio che riparte porta la vita che c'era e la riapre piano: nessun salto al primo fotogramma.
    for (at in listOf(250.0, 330.0, 400.0)) {
      val live = journey.frame(at).target("b").birthWaist
      for (next in listOf(state(a), state(a, b))) {
        val again = journey.retarget(at, next)
        val first = again.frame(0.0).pieces.single { it.piece.key == "b" }
        assertEquals("ripartito a $at", live, first.birthWaist, 0f)
        val later = again.frame(40.0).pieces.single { it.piece.key == "b" }.birthWaist
        assertTrue("si riapre piano: $live -> $later", later >= live && later - live < 0.2f)
      }
    }
  }

  @Test fun aReleaseInterruptedHalfwayRestartsFromEverySideAndItsVelocity() {
    val a = piece("a", 100f)
    val b = piece("b", 152f, width = 88f)
    val journey = GlassPlaneJourney(state(a), state(a, b))
    val delay = journey.releaseDelays.getValue("b")
    // A metà rilascio «b» se ne va: confluisce in «a» da dove si trova, con la velocità che ha.
    val at = delay + 45.0
    val live = journey.frame(at).target("b")
    assertTrue(live.velocity.bounds.right > 100f)
    val leaving = journey.retarget(at, state(a))
    val restart = leaving.frame(0.0).source("b")
    assertEquals(GlassPlaneChange.MergeIntoNeighbour, restart.change)
    assertRect(live.bounds, restart.bounds, 0f)
    assertEquals(live.radii, restart.radii)
    assertEquals(live.velocity, restart.velocity)
    // Il primo passo continua nel verso di prima, anche se l'arrivo adesso è dall'altra parte.
    assertTrue(leaving.frame(4.0).source("b").bounds.right > restart.bounds.right)
    var ms = 0.0
    var previous = restart.bounds
    while (ms <= leaving.durationMs + 5.0) {
      val frame = leaving.frame(ms)
      frame.pieces.forEach { assertContains(leaving.fields.single().bounds, it.bounds) }
      val now = frame.source("b").bounds
      assertTrue("t=$ms: nessun salto (${previous.right} -> ${now.right})", abs(now.right - previous.right) < 6f)
      previous = now
      ms += 1.0
    }
    // F3.12c: finisce dentro «a», e quando è dentro diventa «a» (anche se ripartiva dal bocciolo, più basso di lui): due
    // sagome quasi uguali farebbero orecchie nell'unione, due uguali no.
    val core = leaving.frame(leaving.durationMs + 0.01).source("b").bounds
    assertRect(a.bounds, core, 0.01f)
    // Lo stesso stato ridato a metà rilascio: «b» continua verso il suo posto, da dove si trova.
    val same = journey.retarget(at, state(a, b)).frame(0.0).target("b")
    assertRect(live.bounds, same.bounds, 0f)
    assertEquals(live.velocity, same.velocity)
    // Prima del rilascio: riparte dal posto che aveva dentro il donatore.
    val early = journey.frame(delay / 2.0).target("b")
    val resumed = journey.retarget(delay / 2.0, state(a, b)).frame(0.0).target("b")
    assertRect(early.bounds, resumed.bounds, 0f)
    assertEquals(early.velocity, resumed.velocity)
  }

  @Test fun aMergingGlassEntersItsNeighbourWhereverItIsGoingAndPushesThrough() {
    // La confluenza (R4 B2 (b), 4:51 e 4:59): nessuna attesa, e il superamento dell'8,4% è la **spinta** che sposta chi
    // accoglie, tutto intero (F3.12a: in Apple il tondo che resta va a +0,18 D e torna; in F3.11 gli usciva un lato). Da
    // F3.12c la spinta ha la coda di Apple: dall'altra parte a ~590 ms, ferma a ~900.
    val a = piece("a", 100f)
    val b = piece("b", 152f, width = 88f)
    val merging = GlassPlaneJourney(state(a, b), state(a))
    assertTrue(merging.releaseDelays.isEmpty())
    val budget = FluidMotion.PlaneReleaseMillis.toDouble()
    assertEquals(budget, merging.settleMs, 0.5)
    assertTrue("la coda di chi accoglie: ${merging.durationMs}", merging.durationMs in 880.0..920.0)
    fun leaving(ms: Double) = merging.frame(ms).source("b")
    fun host(ms: Double) = merging.frame(ms).target("a")
    assertRect(b.bounds, leaving(0.0).bounds, 0f)
    assertTrue("parte subito", leaving(20.0).bounds.left < b.bounds.left - 0.5f)
    // La corsa del lato lontano di chi confluisce (il destro) fino al lato destro di chi lo accoglie.
    val run = b.bounds.right - a.bounds.right
    val pushAt = (0..merging.durationMs.toInt()).minByOrNull { host(it.toDouble()).bounds.left }!!.toDouble()
    val push = a.bounds.left - host(pushAt).bounds.left
    val backAt = (0..merging.durationMs.toInt()).maxByOrNull { host(it.toDouble()).bounds.left }!!.toDouble()
    val pushBack = host(backAt).bounds.left - a.bounds.left
    println("F3.12c confluenza: la spinta e' $push px (${push / run * 100}% della corsa) a $pushAt ms, " +
      "poi $pushBack px dall'altra parte a $backAt ms, ferma a ${merging.durationMs} ms")
    assertTrue("la spinta è del ${push / run * 100}% della corsa", push / run in 0.07f..0.095f)
    assertTrue("al colmo a $pushAt ms", pushAt in 285.0..320.0)
    assertEquals("poi dall'altra parte", FluidMotion.PlaneMergeRebound * push, pushBack, 0.05f)
    assertTrue("a $backAt ms", backAt in 570.0..610.0)
    assertEquals("si sposta tutto il vetro", a.bounds.width, host(pushAt).bounds.width, 0.001f)
    assertFalse(host(pushAt).still)
    assertRect(a.bounds, host(0.0).bounds, 0f)
    assertRect(a.bounds, host(100.0).bounds, 0f)
    assertRect(a.bounds, host(merging.durationMs).bounds, 0f)
    // Chi confluisce non esce dall'altra parte: da quando è entrato sta dentro il vicino in volo.
    for (ms in 300..merging.durationMs.toInt()) {
      val inside = leaving(ms.toDouble()).bounds
      val around = host(ms.toDouble()).bounds
      assertTrue("t=$ms: $inside dentro $around", inside.left >= around.left - 0.02f * run && inside.right <= around.right + 0.02f * run)
    }
    // Al colmo della spinta chi confluisce è oltre il centro di chi lo accoglie. Uno stato che lo richiama in
    // quell'istante non è uno scambio di posto: i due vetri sono fusi, e «b» riparte da dove si trova.
    val peakAt = (0..merging.durationMs.toInt()).minByOrNull { leaving(it.toDouble()).bounds.left }!!.toDouble()
    val pushed = leaving(peakAt)
    assertTrue(pushed.bounds.center.x < a.bounds.center.x)
    val recalled = merging.retarget(peakAt, state(a, b))
    assertTrue(recalled.crossingIds.isEmpty())
    val again = recalled.frame(0.0).target("b")
    assertEquals(GlassPlaneChange.Matched, again.change)
    assertRect(pushed.bounds, again.bounds, 0f)
    assertEquals(pushed.velocity, again.velocity)
    assertRect(a.bounds, leaving(merging.durationMs + 0.01).bounds, 0f)
    // E mentre entra si rimpicciolisce (F3.12c): a metà strada è già più basso di chi lo accoglie. Quando è dentro
    // (da 0,37 a 0,6 del riposo a vista) torna della sua misura, e con la sua velocità: nessun salto.
    assertTrue("a 120 ms è alto ${leaving(120.0).bounds.height}", leaving(120.0).bounds.height < 0.9f * a.bounds.height)
    for (ms in 1..merging.durationMs.toInt()) {
      val before = leaving(ms - 1.0).bounds
      val now = leaving(ms.toDouble()).bounds
      assertTrue("t=$ms: l'altezza non salta (${before.height} -> ${now.height})", abs(now.height - before.height) < 0.6f)
    }
    val inside = leaving(0.6 * FluidMotion.PlaneReleaseMillis + 1.0).bounds
    assertTrue("dentro è della misura del vicino: $inside", abs(inside.height - a.bounds.height) < 0.06f * a.bounds.height)
    val field = merging.fields.single()
    for (ms in 0..(merging.durationMs.toInt() + 5)) merging.frame(ms.toDouble()).pieces.forEach { assertContains(field.bounds, it.bounds) }
    // Il vicino che intanto si sposta: chi confluisce lo segue, e finisce dove finisce lui.
    val moved = piece("a", 130f)
    val following = GlassPlaneJourney(state(a, b), state(moved))
    assertRect(moved.bounds, following.frame(following.durationMs + 0.01).source("b").bounds, 0f)
    val stage = following.fields.single()
    for (ms in 0..(following.durationMs.toInt() + 5)) {
      val frame = following.frame(ms.toDouble())
      frame.pieces.forEach { assertContains(stage.bounds, it.bounds) }
      // Mai più largo di quand'è partito: entra nel vicino, non lo scavalca.
      assertTrue(frame.source("b").bounds.right <= b.bounds.right + 0.01f)
    }
  }

  @Test fun anAppSpecKeepsItsOwnCurveWithoutTheChoreography() {
    val a = piece("a", 100f)
    val b = piece("b", 152f, width = 88f)
    val journey = GlassPlaneJourney(state(a), state(a, b), GlassPlaneMotion(spec = androidx.compose.animation.core.tween(200)))
    assertTrue(journey.releaseDelays.isEmpty())
    assertEquals(200.0, journey.durationMs, 1.0)
    // Nessun ritardo e nessun superamento: la spec porta il vetro dal donatore al suo posto.
    assertTrue(journey.frame(60.0).target("b").bounds.right > a.bounds.right + 5f)
    val furthest = (0..200).maxOf { journey.frame(it.toDouble()).target("b").bounds.right }
    assertEquals(b.bounds.right, furthest, 0.01f)
  }

  @Test fun invalidGeometryDuplicateIdsAndNonFiniteTimesFailBeforePlanning() {
    fun rejects(block: () -> Unit) {
      try { block(); throw AssertionError("input non rifiutato") } catch (_: IllegalArgumentException) { }
    }
    rejects { state(piece("a", 0f), piece("b", 60f, id = "a")) }
    rejects { state(piece("a", 0f), spacing = -1f) }
    rejects { piece("a", Float.NaN) }
    rejects { piece("a", 0f).copy(radii = FluidCornerRadii.all(23f)) }
    rejects { GlassPlaneJourney(state(), state()).frame(Double.NaN) }
    assertTrue(GlassPlaneJourney(state(), state()).frame(0.0).finished)
  }
}
