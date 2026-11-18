package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole
import java.io.Closeable
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Il primo evento attraversa il riconoscitore vero: il banco sostituisce soltanto il puntatore. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class FluidPageInputInterruptTest(
  private val transition: FluidPageTransition,
  private val fraction: Float,
  private val direction: Int,
  private val edge: Int,
) {
  @Test fun inAppThresholdKeepsThePushFrameAtTheFirstDragTo() = atPush { rig ->
    rig.state.backSwipeOf = { FluidBackSwipe.Anywhere }
    val finger = rig.finger()
    finger.send(rig.touchX)
    val before = rig.frame()
    finger.send(rig.touchX + 24f * direction)
    assertEquals(FluidPageHostState.Phase.Dragging, rig.state.phase)
    rig.assertFrame(before)
    rig.assertNextFingerStep(finger)
  }

  @Test fun nestedHandoffKeepsThePushFrameAtTheFirstDragTo() = atPush { rig ->
    rig.state.backSwipeOf = { FluidBackSwipe.Anywhere }
    // Fuori dal bordo il Main lascia decidere il figlio, che poi offre il resto del dito.
    val finger = rig.finger()
    val start = if (edge > 0) 100f else 900f
    finger.send(start)
    finger.send(start + 24f * direction, consumed = true)
    assertTrue(rig.state.handoffWatcher)
    assertTrue(rig.state.offerHandoff(12f))
    val before = rig.frame()
    finger.send(start + 36f * direction)
    assertEquals(FluidPageHostState.Phase.Dragging, rig.state.phase)
    rig.assertFrame(before)
    rig.assertNextFingerStep(finger)
  }

  @Test fun systemStartKeepsThePushFrameAndItsDirectionEvenFromTheOtherEdge() = atPush { rig ->
    val system = rig.system()
    val before = rig.frame()
    system.start(rig.event(rig.touchX)) // chiama gia' follow -> dragTo
    rig.assertFrame(before)
    assertEquals(direction, rig.state.direction)
    val travel = rig.state.travelPx
    system.progress(rig.event(rig.touchX + 8f * direction, time = 108L))
    assertEquals(travel + 8f, rig.state.travelPx, PositionTolerance)
    assertEquals(direction, rig.state.direction)
  }

  @Test fun edgeOnlyStillRejectsTheCentreDuringPush() = atPush { rig ->
    val finger = rig.finger()
    finger.send(500f)
    finger.send(500f + 40f * direction)
    assertEquals(FluidPageHostState.Phase.Pushing, rig.state.phase)
    assertFalse(rig.state.isInteractive)
    assertFalse(rig.state.handoffWatcher)
  }

  @Test fun edgeOnlyKeepsThePushFrameWhenItsEntranceTakesTheFinger() = atPush { rig ->
    val finger = rig.finger()
    val entrance = if (direction > 0) 10f else 990f
    finger.send(entrance)
    val before = rig.frame()
    finger.send(entrance + 24f * direction)
    assertEquals(FluidPageHostState.Phase.Dragging, rig.state.phase)
    rig.assertFrame(before)
    rig.assertNextFingerStep(finger)
  }

  @Test fun edgeOnlyStillRejectsTheOppositeEdgeDuringPush() = atPush { rig ->
    val finger = rig.finger()
    val opposite = if (direction > 0) 990f else 10f
    finger.send(opposite)
    finger.send(opposite + 40f * direction)
    assertEquals(FluidPageHostState.Phase.Pushing, rig.state.phase)
  }

  @Test fun offAndTheBarStillRejectThePush() = atPush { rig ->
    rig.state.backSwipeOf = { FluidBackSwipe.Off }
    val finger = rig.finger()
    finger.send(rig.touchX)
    finger.send(rig.touchX + 40f * direction)
    assertEquals(FluidPageHostState.Phase.Pushing, rig.state.phase)
    finger.send(finger.x, pressed = false)
    rig.state.backSwipeOf = { FluidBackSwipe.Anywhere }
    rig.state.barBottomPx = 400f // il puntatore e' a y=300
    finger.send(rig.touchX)
    finger.send(rig.touchX + 40f * direction)
    assertEquals(FluidPageHostState.Phase.Pushing, rig.state.phase)
  }

  @Test fun aFreshInAppGestureStillIncludesTheThresholdDistance() = atRest { rig ->
    val finger = rig.finger()
    val start = if (direction > 0) 10f else 990f
    finger.send(start)
    finger.send(start + 24f * direction)
    assertEquals(24f, rig.state.travelPx, PositionTolerance)
  }

  @Test fun aFreshSystemGestureStillUsesTheWindowEdge() = atRest { rig ->
    rig.system().start(rig.event(rig.touchX))
    assertEquals(10f, rig.state.travelPx, PositionTolerance)
    assertEquals(edge, rig.state.direction)
  }

  @Test fun expandKeepsItsDeclaredFallbackInBothRecognizers() {
    for (system in listOf(false, true)) {
      Rig(FluidPageTransition.Expand, direction, edge).use { rig ->
        rig.pushTo(fraction)
        if (system) {
          rig.system().start(rig.event(rig.touchX))
          assertEquals(10f, rig.state.travelPx, PositionTolerance)
          assertEquals(edge, rig.state.direction)
        } else {
          rig.state.backSwipeOf = { FluidBackSwipe.Anywhere }
          val finger = rig.finger()
          finger.send(rig.touchX)
          finger.send(rig.touchX + 24f * direction)
          assertEquals(24f, rig.state.travelPx, PositionTolerance)
        }
        assertFalse(rig.state.opening)
      }
    }
  }

  private fun atPush(check: (Rig) -> Unit) {
    Rig(transition, direction, edge).use { rig -> rig.pushTo(fraction); check(rig) }
  }

  private fun atRest(check: (Rig) -> Unit) {
    Rig(transition, direction, edge).use { rig ->
      rig.state.reducedMotion = true
      rig.state.onStack(listOf("root", "detail"))
      rig.settleReducedPush()
      check(rig)
    }
  }

  private data class Frame(val upperX: Float, val lowerX: Float, val scale: Float)

  private class Rig(transition: FluidPageTransition, private val direction: Int, private val edge: Int) : Closeable {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    private val systemInputs = mutableListOf<SystemInput>()
    private var nanos = 0L
    val touchX = if (edge > 0) 10f else 990f
    val state = FluidPageHostState().apply {
      this.scope = this@Rig.scope
      width = 1000f
      height = 800f
      hostRightInWindow = width
      windowWidth = width
      layoutDirectionSign = this@Rig.direction
      transitionOf = { transition }
    }

    init { state.onStack(listOf("root")) }

    fun settleReducedPush() {
      state.pagePlaced("detail")
      repeat(32) { nanos += 8_333_333L; clock.sendFrame(nanos) }
      assertFalse(state.isMoving)
    }

    fun pushTo(fraction: Float) {
      state.onStack(listOf("root", "detail"))
      state.pagePlaced("detail")
      repeat(2000) {
        nanos += 1_000_000L
        clock.sendFrame(nanos)
        if (state.upperExitFraction() <= fraction) {
          assertEquals(fraction, state.upperExitFraction(), 0.01f)
          assertEquals(FluidPageHostState.Phase.Pushing, state.phase)
          return
        }
      }
      throw AssertionError("la molla non ha raggiunto $fraction")
    }

    fun finger(): Finger {
      // Si esegue il blocco installato dal Modifier di produzione, senza ricopiare il riconoscitore.
      // Le coordinate sono locali a un host che puo' essere inset nella finestra: il dito preso
      // puo' anche superarne il bordo senza uscire dallo schermo.
      val modifier = Modifier.fluidPageBackSwipe(state)
      val element = modifier.foldIn<ModifierNodeElement<*>?>(null) { found, item ->
        found ?: item as? ModifierNodeElement<*>
      }!!
      val node = element.create() as SuspendingPointerInputModifierNode
      val pointer = FakePointer()
      scope.launch { with(node.pointerInputEventHandler) { pointer.input.invoke() } }
      return Finger(pointer)
    }

    fun event(x: Float, time: Long = 100L) = NavigationEvent(
      swipeEdge = if (edge > 0) NavigationEvent.EDGE_LEFT else NavigationEvent.EDGE_RIGHT,
      touchX = x, touchY = 300f, progress = 0.1f, frameTimeMillis = time,
    )

    fun system() = SystemInput(state).also { systemInputs += it }

    fun frame() = Frame(state.pageOffsetX(PageRole.Upper), state.pageOffsetX(PageRole.Lower), state.pageScale(PageRole.Upper))

    fun assertFrame(before: Frame) {
      val after = frame()
      assertEquals("traslazione sopra al primo evento", before.upperX, after.upperX, PositionTolerance)
      assertEquals("traslazione sotto al primo evento", before.lowerX, after.lowerX, PositionTolerance)
      assertEquals("scala sopra al primo evento", before.scale, after.scale, ScaleTolerance)
      assertEquals("verso conservato", direction, state.direction)
    }

    fun assertNextFingerStep(finger: Finger) {
      val travel = state.travelPx
      finger.send(finger.x + 8f * direction)
      assertEquals("dopo la presa segue il dito 1:1", travel + 8f, state.travelPx, PositionTolerance)
      // Il job del push non deve piu' muovere il fotogramma tenuto dal dito.
      val held = frame()
      repeat(12) { nanos += 8_333_333L; clock.sendFrame(nanos) }
      assertFrame(held)
    }

    override fun close() { systemInputs.forEach { it.close() }; scope.cancel() }
  }

  private class SystemInput(state: FluidPageHostState) : NavigationEventInput(), Closeable {
    private val dispatcher = NavigationEventDispatcher().apply {
      addInput(this@SystemInput)
      addHandler(FluidPageBackHandler(state).apply { isBackEnabled = true })
    }

    fun start(event: NavigationEvent) { dispatchOnBackStarted(event) }
    fun progress(event: NavigationEvent) { dispatchOnBackProgressed(event) }
    override fun close() { dispatcher.dispose() }
  }

  private class Finger(private val pointer: FakePointer) {
    var x = 0f
      private set
    private var pressed = false
    private var time = 0L

    fun send(nextX: Float, pressed: Boolean = true, consumed: Boolean = false) {
      time += 8L
      val change = PointerInputChange(
        id = PointerId(1L), uptimeMillis = time, position = Offset(nextX, 300f), pressed = pressed,
        previousUptimeMillis = time - 8L, previousPosition = Offset(x, 300f), previousPressed = this.pressed,
        isInitiallyConsumed = consumed,
      )
      x = nextX
      this.pressed = pressed
      pointer.dispatch(PointerEvent(listOf(change)))
    }
  }

  /** Eventi Compose nei tre passaggi; il test controlla lo stato prima che arrivi un altro frame. */
  private class FakePointer : PointerInputScope, AwaitPointerEventScope {
    val input: PointerInputScope get() = this
    private var awaiter: Continuation<PointerEvent>? = null
    private var awaitedPass = PointerEventPass.Main
    override var currentEvent = PointerEvent(emptyList())
    override val size = IntSize(1000, 800)
    override val extendedTouchPadding get() = Size.Zero
    override val density = 1f
    override val fontScale = 1f
    override val viewConfiguration = object : ViewConfiguration {
      override val longPressTimeoutMillis = 400L
      override val doubleTapTimeoutMillis = 300L
      override val doubleTapMinTimeMillis = 40L
      override val touchSlop = 16f
    }

    fun dispatch(event: PointerEvent) {
      currentEvent = event
      for (pass in listOf(PointerEventPass.Initial, PointerEventPass.Main, PointerEventPass.Final)) {
        if (awaitedPass != pass) continue
        val waiting = awaiter ?: continue
        awaiter = null
        waiting.resume(event)
      }
    }

    override suspend fun <R> awaitPointerEventScope(block: suspend AwaitPointerEventScope.() -> R): R =
      suspendCancellableCoroutine { outer ->
        outer.invokeOnCancellation { cause ->
          val waiting = awaiter
          awaiter = null
          waiting?.resumeWithException(cause ?: CancellationException("tolto"))
        }
        block.createCoroutine(this, Continuation(EmptyCoroutineContext) { outer.resumeWith(it) }).resume(Unit)
      }

    override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent = suspendCoroutine {
      awaitedPass = pass
      check(awaiter == null)
      awaiter = it
    }

    override suspend fun <T> withTimeoutOrNull(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T? =
      error("il riconoscitore non usa timer")

    override suspend fun <T> withTimeout(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T =
      error("il riconoscitore non usa timer")
  }

  companion object {
    // Nessun tempo fra prima/dopo: solo arrotondamento Float, non un margine percettivo.
    private const val PositionTolerance = 0.001f // px
    private const val ScaleTolerance = 0.000001f

    @JvmStatic @Parameterized.Parameters(name = "{0}, uscita={1}, verso={2}, bordo={3}")
    fun cases(): List<Array<Any>> = listOf(FluidPageTransition.Slide, FluidPageTransition.ShrinkSlide).flatMap { transition ->
      listOf(0.2f, 0.5f, 0.8f).flatMap { fraction ->
        listOf(1, -1).flatMap { direction ->
          listOf(1, -1).map { edge -> arrayOf<Any>(transition, fraction, direction, edge) }
        }
      }
    }
  }
}
