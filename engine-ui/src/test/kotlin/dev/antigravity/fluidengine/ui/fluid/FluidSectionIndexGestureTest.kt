package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Chi prende il trascinamento dell'indice di sezione (F1B.3, giro di correzione; S2 parte 2.1: la pagina scorreva di
 * 103 px su 120 di dito).
 *
 * Il gesto ([fluidSectionIndexGesture]) gira qui su un puntatore finto ([FakePointer]): il tempo è quello virtuale di
 * `runTest` (la pressione lunga scade davvero), e ogni cambio resta in mano al test,
 * che guarda se il gesto l'ha consumato. Un cambio non consumato è un cambio che lo scorrevole della lista raccoglie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidSectionIndexGestureTest {

  private val longPress = 400L
  private val slop = 17f
  private val origin = Offset(20f, 300f)

  /**
   * Un `PointerInputScope` fatto come quello di Compose (`SuspendingPointerInputModifierNode`), in piccolo: il blocco di
   * `awaitPointerEventScope` è una coroutine a sospensione ristretta con contesto vuoto, che aspetta un evento alla
   * volta e riprende **subito**, dentro [dispatch]; la scadenza della pressione lunga è un timer sul tempo virtuale che
   * riprende l'attesa con un'eccezione; la coroutine che toglie il gesto riprende l'attesa con la sua cancellazione,
   * cosicché i `finally` del gesto girano come sul dispositivo. `currentEvent` è l'ultimo evento consegnato.
   */
  private class FakePointer(private val timers: CoroutineScope, private val longPressMillis: Long, private val slop: Float) :
    PointerInputScope, AwaitPointerEventScope {
    private val pending = ArrayDeque<PointerEvent>()
    private var awaiter: Continuation<PointerEvent>? = null

    /** Lo stesso oggetto visto come `PointerInputScope`: `AwaitPointerEventScope` limita le sospensioni a sé. */
    val input: PointerInputScope get() = this
    override var currentEvent: PointerEvent = PointerEvent(emptyList())
    override val size: IntSize = IntSize(40, 800)
    override val extendedTouchPadding: Size get() = Size.Zero
    override val density: Float = 2f
    override val fontScale: Float = 1f
    override val viewConfiguration: ViewConfiguration = object : ViewConfiguration {
      override val longPressTimeoutMillis: Long get() = longPressMillis
      override val doubleTapTimeoutMillis: Long get() = 300L
      override val doubleTapMinTimeMillis: Long get() = 40L
      override val touchSlop: Float get() = slop
    }

    /** Consegna un evento: il gesto lo legge e corre fino alla prossima attesa prima che questa ritorni. */
    fun dispatch(event: PointerEvent) {
      val waiting = awaiter
      if (waiting == null) {
        pending.addLast(event)
      } else {
        awaiter = null
        currentEvent = event
        waiting.resume(event)
      }
    }

    private fun interrupt(cause: Throwable) {
      val waiting = awaiter ?: return
      awaiter = null
      waiting.resumeWithException(cause)
    }

    override suspend fun <R> awaitPointerEventScope(block: suspend AwaitPointerEventScope.() -> R): R =
      suspendCancellableCoroutine { outer ->
        outer.invokeOnCancellation { interrupt(it ?: CancellationException("tolto")) }
        block.createCoroutine(this, Continuation(EmptyCoroutineContext) { outer.resumeWith(it) }).resume(Unit)
      }

    override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent {
      pending.removeFirstOrNull()?.let { currentEvent = it; return it }
      return suspendCoroutine { awaiter = it }
    }

    private class Expired : CancellationException("scaduto")

    override suspend fun <T> withTimeoutOrNull(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T? {
      val expired = Expired()
      val timer = timers.launch { delay(timeMillis); interrupt(expired) }
      return try {
        block()
      } catch (e: Expired) {
        if (e === expired) null else throw e
      } finally {
        timer.cancel()
      }
    }

    override suspend fun <T> withTimeout(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T =
      withTimeoutOrNull(timeMillis, block) ?: throw CancellationException("scaduto")
  }

  /** Ciò che l'indice ha sentito. */
  private class Heard {
    var held = 0
    var released = 0
    val fractions = ArrayList<Float>()
    val indices = ArrayList<Int>()
  }

  private fun host(heard: Heard) = FluidSectionIndexGestureHost(
    selectedIndex = { 2 },
    count = { 6 },
    usableExpandedPx = { 500f },
    onHeld = { heard.held++ },
    onFraction = { heard.fractions += it },
    onIndex = { heard.indices += it },
    onRelease = { heard.released++ },
  )

  /** Un dito: ogni cambio è nuovo, con la posizione di prima. */
  private class Finger(val id: Long, var at: Offset) {
    var pressed = false
  }

  private fun TestScope.change(finger: Finger, to: Offset, pressed: Boolean): PointerInputChange {
    val c = PointerInputChange(
      id = PointerId(finger.id),
      uptimeMillis = testScheduler.currentTime,
      position = to,
      pressed = pressed,
      previousUptimeMillis = testScheduler.currentTime,
      previousPosition = finger.at,
      previousPressed = finger.pressed,
      isInitiallyConsumed = false,
    )
    finger.at = to
    finger.pressed = pressed
    return c
  }

  /** Manda un evento con questi cambi e lascia girare il gesto. */
  private fun TestScope.send(pointer: FakePointer, vararg changes: PointerInputChange): List<PointerInputChange> {
    pointer.dispatch(PointerEvent(changes.toList()))
    runCurrent()
    return changes.toList()
  }

  @Test
  fun `before the hold nothing is consumed, after it every change is, up to the lift`() = runTest {
    val pointer = FakePointer(backgroundScope, longPress, slop)
    val heard = Heard()
    val job = launch { pointer.input.fluidSectionIndexGesture(slop, host(heard)) }
    runCurrent()
    val finger = Finger(1, origin)

    // Il dito scende e trema dentro la soglia: è ancora di tutti.
    val before = ArrayList<PointerInputChange>()
    before += send(pointer, change(finger, origin, pressed = true))
    for (step in 1..3) {
      advanceTimeBy(100)
      before += send(pointer, change(finger, origin + Offset(0f, step * 4f), pressed = true))
    }
    assertEquals(0, heard.held)
    assertTrue("prima della presa niente è consumato", before.none { it.isConsumed })

    // Fermo oltre la pressione lunga: l'indice prende il gesto.
    advanceTimeBy(longPress)
    runCurrent()
    assertEquals(1, heard.held)

    // Il dito trascina di 120 px, a passi di 10, e un secondo dito si appoggia: tutto è dell'indice.
    val after = ArrayList<PointerInputChange>()
    val second = Finger(2, Offset(30f, 500f))
    after += send(pointer, change(finger, finger.at + Offset(0f, 10f), pressed = true), change(second, second.at, pressed = true))
    for (step in 2..12) {
      advanceTimeBy(8)
      after += send(pointer, change(finger, finger.at + Offset(0f, 10f), pressed = true), change(second, second.at, pressed = true))
    }
    after += send(pointer, change(finger, finger.at, pressed = false), change(second, second.at, pressed = true))
    assertTrue("dopo la presa ogni cambio è consumato", after.all { it.isConsumed })
    assertEquals(1, heard.released)
    assertEquals(13, heard.fractions.size)
    // Dalla presa (il dito era sceso 12 px tremando) 132 px sul nastro di 500, da 2/5: 0,4 + 0,264 = 0,664, cioè la
    // sezione 3; una tacca sola attraversata.
    assertEquals(0.664f, heard.fractions.last(), 1e-4f)
    assertEquals(listOf(3), heard.indices)
    job.cancel()
  }

  @Test
  fun `a finger that moves before the long press never opens the index and is never consumed`() = runTest {
    val pointer = FakePointer(backgroundScope, longPress, slop)
    val heard = Heard()
    val job = launch { pointer.input.fluidSectionIndexGesture(slop, host(heard)) }
    runCurrent()
    val finger = Finger(1, origin)

    val all = ArrayList<PointerInputChange>()
    all += send(pointer, change(finger, origin, pressed = true))
    advanceTimeBy(60)
    all += send(pointer, change(finger, origin + Offset(0f, 12f), pressed = true))
    advanceTimeBy(16)
    all += send(pointer, change(finger, origin + Offset(0f, 30f), pressed = true))
    // Oltre la soglia: è uno scorrimento. Anche fermo a lungo dopo, l'indice non si apre.
    advanceTimeBy(longPress * 3)
    all += send(pointer, change(finger, finger.at, pressed = true))
    for (step in 1..9) {
      advanceTimeBy(8)
      all += send(pointer, change(finger, finger.at + Offset(0f, 10f), pressed = true))
    }
    all += send(pointer, change(finger, finger.at, pressed = false))
    assertEquals(0, heard.held)
    assertEquals(0, heard.released)
    assertTrue(heard.fractions.isEmpty())
    assertTrue("uno scorrimento passa intero alla lista", all.none { it.isConsumed })
    job.cancel()
  }

  @Test
  fun `a gesture taken away after the hold closes the index`() = runTest {
    val pointer = FakePointer(backgroundScope, longPress, slop)
    val heard = Heard()
    val job = launch { pointer.input.fluidSectionIndexGesture(slop, host(heard)) }
    runCurrent()
    val finger = Finger(1, origin)
    send(pointer, change(finger, origin, pressed = true))
    advanceTimeBy(longPress + 1)
    runCurrent()
    assertEquals(1, heard.held)
    send(pointer, change(finger, finger.at + Offset(0f, 20f), pressed = true))
    job.cancel()
    runCurrent()
    assertEquals("il nastro non resta aperto sotto un dito che non c'è più", 1, heard.released)
  }

  /**
   * Il difetto, a mano: il gesto tolto a metà trascinamento e rimesso (ciò che faceva ogni ricomposizione, quando il
   * blocco era parte dell'uguaglianza dell'elemento). Il gesto nuovo aspetta un dito che scende, e i movimenti del
   * dito già giù gli passano accanto senza essere consumati: lo scorrevole li prende e la pagina scorre.
   */
  @Test
  fun `a gesture restarted mid drag lets the moves through, which is why the element ignores the block`() = runTest {
    val pointer = FakePointer(backgroundScope, longPress, slop)
    val heard = Heard()
    val first = launch { pointer.input.fluidSectionIndexGesture(slop, host(heard)) }
    runCurrent()
    val finger = Finger(1, origin)
    send(pointer, change(finger, origin, pressed = true))
    advanceTimeBy(longPress + 1)
    runCurrent()
    val held = send(pointer, change(finger, finger.at + Offset(0f, 10f), pressed = true))
    assertTrue(held.all { it.isConsumed })

    first.cancel()
    val second = launch { pointer.input.fluidSectionIndexGesture(slop, host(heard)) }
    runCurrent()
    val lost = ArrayList<PointerInputChange>()
    for (step in 1..11) {
      advanceTimeBy(8)
      lost += send(pointer, change(finger, finger.at + Offset(0f, 10f), pressed = true))
    }
    assertTrue("il gesto rimesso non consuma i movimenti del dito già giù", lost.none { it.isConsumed })
    second.cancel()
  }

  @Test
  fun `the shared pointer element is the same element whatever the block, and a new one when a key changes`() {
    val blockA: suspend PointerInputScope.() -> Unit = {}
    val blockB: suspend PointerInputScope.() -> Unit = { awaitPointerEventScope { } }
    val host = host(Heard())
    val a = SharedPointerElement(slop, false, host, blockA)
    val b = SharedPointerElement(slop, false, host, blockB)
    assertEquals("stesse chiavi, blocco nuovo: il gesto in corso resta", a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertNotEquals("una chiave cambiata riparte", a, SharedPointerElement(slop, true, host, blockA))
    assertNotEquals(a, SharedPointerElement(slop + 1f, false, host, blockA))
    assertFalse(a == SharedPointerElement(slop, false, host(Heard()), blockA))
  }
}
