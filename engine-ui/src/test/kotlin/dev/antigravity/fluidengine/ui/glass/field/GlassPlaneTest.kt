package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Rect
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
    assertRect(b.bounds, born.bounds)
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
    assertEquals(1f, journey.frame(430.01).target("b").materialize, 0f)
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
    assertRect(a.bounds, exit.bounds)
    assertRect(b.bounds, exit.contentBounds)
    val disappearing = GlassPlaneJourney(state(piece("far", 300f)), state())
    assertEquals(470.0, disappearing.durationMs, 0.05)
    assertEquals(0f, disappearing.frame(470.01).pieces.single().materialize, 0f)
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
    for (ms in 0..600 step 2) {
      val frame = journey.frame(ms.toDouble())
      assertEquals(journey.fields, frame.fields)
      frame.pieces.forEach { assertContains(field.bounds, it.bounds) }
    }
  }

  @Test fun moreThanEightTransientPiecesArePartitionedWithoutDroppingAny() {
    val a = (0..7).map { piece("a$it", it * 3f).copy(transition = GlassPlaneTransition.Materialize) }
    val b = (0..7).map { piece("b$it", it * 3f).copy(transition = GlassPlaneTransition.Materialize) }
    val journey = GlassPlaneJourney(GlassPlaneState(a, 12f, canvas), GlassPlaneState(b, 12f, canvas))
    assertEquals(16, journey.frame(70.0).pieces.size)
    assertEquals(16, journey.fields.sumOf { it.flights.size })
    assertTrue(journey.fields.all { it.flights.size <= 8 && it.kernel == GlassBodyKernel.Eight })
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
    assertEquals(300.0, journey.durationMs, 0.05)
    assertFalse(journey.frame(299.0).finished)
    assertTrue(journey.frame(300.01).finished)
    val maximum = (0..300).maxOf { journey.frame(it.toDouble()).pieces.single().bounds.width }
    assertTrue("oltrepasso $maximum", maximum <= 101.3f)
    assertEquals(430.0, GlassPlaneJourney(state(), state(piece("a", 20f))).durationMs, 0.05)
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
