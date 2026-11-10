package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.fluid.ClosedProfilePool.Stage
import org.junit.Assert.*
import org.junit.Test

/** La chiusura di una vista ([RendererClosing]) su una piattaforma finta: attese, tempi limite e ogni esito;
 * in fondo, chiusura e profili insieme, come nel contenitore.
 */
class FluidWebClosingTest {
  private val main = FakeMainThread()
  private val view = FakeClosingView(main)
  private val returned = RecordingReturn()

  private fun closing(profile: String? = "p", alreadyGone: Boolean = false) =
    RendererClosing(view, profile, alreadyGone, returned)

  // ---- terminazione accolta ----

  @Test fun confirmedTerminationDestroysTheViewThenRecyclesTheProfile() {
    view.answers += true
    val closing = closing()
    closing.start()
    // Client sostituito prima di chiedere la terminazione; la vista resta finche' manca la conferma.
    assertEquals(listOf("acquire", "takeOver", "terminate"), view.log)
    assertEquals(0, view.destroys)
    assertTrue(returned.log.isEmpty())
    main.advanceBy(40)
    closing.rendererGone(crashed = false)
    assertEquals(40L, view.destroyedAt)
    assertEquals(listOf("recycle p"), returned.log)
    // Il tempo limite della conferma non resta in coda.
    assertEquals(0, main.pending)
    main.advanceBy(60_000)
    assertEquals(1, view.destroys)
    assertEquals(1, view.terminateTimes.size)
    assertEquals(listOf("recycle p"), returned.log)
  }

  @Test fun confirmationThatNeverComesRetiresTheProfileAtTheTimeLimit() {
    view.answers += true
    val closing = closing()
    closing.start()
    main.advanceBy(RendererClosing.ConfirmationMillis - 1)
    assertEquals(0, view.destroys)
    main.advanceBy(1)
    assertEquals(RendererClosing.ConfirmationMillis, view.destroyedAt)
    assertEquals(listOf("retire p"), returned.log)
    // Una conferma arrivata dopo non cambia nulla: niente seconda distruzione, niente riciclo.
    closing.rendererGone(crashed = false)
    main.advanceBy(60_000)
    assertEquals(1, view.destroys)
    assertEquals(listOf("retire p"), returned.log)
    assertEquals(1, view.terminateTimes.size)
  }

  @Test fun crashWhileWaitingForTheConfirmationRetiresTheProfile() {
    view.answers += true
    val closing = closing()
    closing.start()
    main.advanceBy(10)
    closing.rendererGone(crashed = true)
    assertEquals(10L, view.destroyedAt)
    assertEquals(listOf("retire p"), returned.log)
    assertEquals(0, main.pending)
  }

  @Test fun deathReportedInsideTerminateCountsAsTheOneAskedFor() {
    lateinit var closing: RendererClosing
    view.answers += true
    view.duringTerminate = { closing.rendererGone(crashed = false) }
    closing = closing()
    closing.start()
    assertEquals(0L, view.destroyedAt)
    assertEquals(listOf("recycle p"), returned.log)
    // Gia' finita: nessun tempo limite messo in coda.
    assertEquals(0, main.pending)
  }

  @Test fun crashReportedInsideTerminateRetiresTheProfile() {
    lateinit var closing: RendererClosing
    view.answers += true
    view.duringTerminate = { closing.rendererGone(crashed = true) }
    closing = closing()
    closing.start()
    assertEquals(listOf("retire p"), returned.log)
    assertEquals(1, view.destroys)
    assertEquals(0, main.pending)
  }

  // ---- niente da terminare ----

  @Test fun withoutADedicatedProfileTheRendererIsNotEvenTaken() {
    closing(profile = null).start()
    assertEquals(listOf("takeOver", "destroy"), view.log)
    assertTrue(returned.log.isEmpty())
    assertEquals(0, main.pending)
  }

  @Test fun rendererAlreadyGoneIsNotTerminatedAndTheProfileIsRetired() {
    closing(alreadyGone = true).start()
    assertEquals(listOf("takeOver", "destroy"), view.log)
    assertEquals(listOf("retire p"), returned.log)
    assertEquals(0, main.pending)
  }

  @Test fun providerThatDoesNotGiveTheRendererDestroysAtOnceAndRetires() {
    view.rendererAvailable = false
    closing().start()
    assertEquals(listOf("acquire", "takeOver", "destroy"), view.log)
    assertEquals(listOf("retire p"), returned.log)
    assertEquals(0, main.pending)
  }

  @Test fun providerThatCannotTerminateDestroysAtOnceAndRetires() {
    view.answers += null
    closing().start()
    assertEquals(listOf("acquire", "takeOver", "terminate", "destroy"), view.log)
    assertEquals(listOf("retire p"), returned.log)
    assertEquals(0, main.pending)
  }

  // ---- il processo sta ancora partendo: terminate() risponde false ----

  @Test fun startingRendererIsRetriedThenTheViewIsLetGoAndTheRetriesGoOn() {
    view.afterAnswers = false
    closing().start()
    main.advanceBy(4_099)
    // Cinque tentativi oltre il primo, a 50, 200, 600, 1600 e (non ancora) 4100 ms: la vista e' ancora tenuta.
    assertEquals(listOf(0L, 50L, 200L, 600L, 1_600L), view.terminateTimes)
    assertEquals(0, view.destroys)
    assertTrue(returned.log.isEmpty())
    main.advanceBy(1)
    assertEquals(4_100L, view.destroyedAt)
    assertEquals(listOf("retire p"), returned.log)
    // I tentativi continuano sul solo riferimento al processo, poi finiscono: meno di un minuto in tutto.
    main.runAll()
    assertEquals(listOf(0L, 50L, 200L, 600L, 1_600L, 4_100L, 10_100L, 25_100L, 55_100L), view.terminateTimes)
    assertEquals(RendererClosing.RetryDelays.size + 1, view.terminateTimes.size)
    assertTrue(view.terminateTimes.last() < 60_000)
    main.advanceBy(600_000)
    assertEquals(9, view.terminateTimes.size)
    assertEquals(1, view.destroys)
    assertEquals(listOf("retire p"), returned.log)
  }

  @Test fun rendererTerminatedOnARetryAndConfirmedIsRecycled() {
    view.answers += listOf(false, false, true)
    val closing = closing()
    closing.start()
    main.advanceBy(200)
    assertEquals(listOf(0L, 50L, 200L), view.terminateTimes)
    assertEquals(0, view.destroys)
    main.advanceBy(30)
    closing.rendererGone(crashed = false)
    assertEquals(230L, view.destroyedAt)
    assertEquals(listOf("recycle p"), returned.log)
    main.advanceBy(600_000)
    assertEquals(3, view.terminateTimes.size)
  }

  @Test fun rendererTerminatedOnARetryButNeverConfirmedIsRetiredAtTheTimeLimit() {
    view.answers += listOf(false, true)
    closing().start()
    main.advanceBy(50 + RendererClosing.ConfirmationMillis - 1)
    assertEquals(0, view.destroys)
    main.advanceBy(1)
    assertEquals(listOf("retire p"), returned.log)
    main.advanceBy(600_000)
    assertEquals(2, view.terminateTimes.size)
  }

  @Test fun rendererThatDiesWhileStartingIsNotAConfirmation() {
    // Muore fra un tentativo e l'altro, senza che una terminazione sia mai stata accolta: non si sa di che
    // cosa e' morto, e il profilo non si riusa nemmeno se il provider non parla di crash.
    view.afterAnswers = false
    val closing = closing()
    closing.start()
    main.advanceBy(300)
    closing.rendererGone(crashed = false)
    assertEquals(300L, view.destroyedAt)
    assertEquals(listOf("retire p"), returned.log)
    // Nessun tentativo resta in coda.
    assertEquals(0, main.pending)
    main.advanceBy(600_000)
    assertEquals(listOf(0L, 50L, 200L), view.terminateTimes)
  }

  @Test fun terminationAcceptedAfterTheViewWasLetGoChangesNothingMore() {
    // Sei risposte false (la vista viene lasciata al sesto tentativo), poi il processo finisce di partire e
    // la terminazione viene accolta: niente tempo limite, niente seconda riconsegna, niente altri tentativi.
    view.answers += listOf(false, false, false, false, false, false, true)
    closing().start()
    main.runAll()
    assertEquals(listOf(0L, 50L, 200L, 600L, 1_600L, 4_100L, 10_100L), view.terminateTimes)
    assertEquals(4_100L, view.destroyedAt)
    assertEquals(1, view.destroys)
    assertEquals(listOf("retire p"), returned.log)
    assertEquals(0, main.pending)
  }

  @Test fun providerThatStopsTerminatingDuringTheRetriesEndsThem() {
    view.answers += listOf(false, false, null)
    closing().start()
    main.advanceBy(200)
    assertEquals(200L, view.destroyedAt)
    assertEquals(listOf("retire p"), returned.log)
    main.advanceBy(600_000)
    assertEquals(3, view.terminateTimes.size)
  }

  // ---- il thread principale ----

  @Test fun startAndEveryQueuedStepCheckTheMainThread() {
    main.onMain = false
    assertThrows(IllegalStateException::class.java) { closing().start() }
    assertTrue(view.log.isEmpty())
    main.onMain = true
    view.afterAnswers = false
    val closing = closing()
    closing.start()
    main.onMain = false
    assertThrows(IllegalStateException::class.java) { main.advanceBy(50) } // un tentativo rimesso in coda
    assertThrows(IllegalStateException::class.java) { closing.rendererGone(crashed = false) }
    assertEquals(0, view.destroys)
    assertTrue(returned.log.isEmpty())
  }

  // ---- chiusura e profili insieme ----

  private val provider = FakeProfileProvider(main)
  private val pool = ClosedProfilePool(provider)
  private var opened = 0

  /** Apre un documento: una vista nuova con il suo profilo. */
  private fun open(): Pair<String, FakeClosingView> = pool.isolate("vista-${opened++}") to FakeClosingView(main)

  /** Chiude il documento come `ClosedWebView.release`. */
  private fun close(document: Pair<String, FakeClosingView>, rendererGone: Boolean = false): RendererClosing =
    RendererClosing(document.second, document.first, rendererGone, pool).also { it.start() }

  @Test fun closedDocumentHandsItsProfileToALaterOneOnlyAfterDeathAndCleaning() {
    val first = open().also { it.second.answers += true }
    val closing = close(first)
    val second = open()
    assertNotEquals(first.first, second.first) // renderer non ancora confermato morto
    closing.rendererGone(crashed = false)
    assertEquals(Stage.Cleaning, pool.stage(first.first))
    val third = open()
    assertNotEquals(first.first, third.first) // pulizia non ancora risposta
    provider.answerClear(first.first)
    val fourth = open()
    assertEquals(first.first, fourth.first)
    assertEquals(setOf(first.first, second.first, third.first), provider.store)
  }

  @Test fun manyDocumentsOneAfterTheOtherUseOneProfile() {
    val used = HashSet<String>()
    repeat(60) {
      val document = open().also { it.second.answers += true }
      used += document.first
      val closing = close(document)
      main.advanceBy(30)
      closing.rendererGone(crashed = false)
      provider.answerClear(document.first)
      assertEquals(1, document.second.destroys)
    }
    assertEquals(1, used.size)
    assertEquals(0, main.pending)
  }

  @Test fun withoutRendererTerminationNoProfileIsEverReused() {
    val used = (1..12).map {
      val document = open().also { it.second.answers += null }
      close(document)
      assertEquals(1, document.second.destroys)
      assertEquals(Stage.Retired, pool.stage(document.first))
      document.first
    }
    assertEquals(12, used.toSet().size)
    assertTrue(provider.clearing.isEmpty())
  }

  @Test fun withoutTheRendererHandleNoProfileIsEverReused() {
    val used = (1..12).map {
      val document = open().also { it.second.rendererAvailable = false }
      close(document)
      assertEquals(Stage.Retired, pool.stage(document.first))
      document.first
    }
    assertEquals(12, used.toSet().size)
  }

  @Test fun withoutDeleteBrowsingDataConfirmedDeathsStillReuseNothing() {
    provider.canClear = false
    val used = (1..12).map {
      val document = open().also { it.second.answers += true }
      close(document).rendererGone(crashed = false)
      assertEquals(Stage.Retired, pool.stage(document.first))
      document.first
    }
    assertEquals(12, used.toSet().size)
  }

  @Test fun profileOfARendererThatCrashedIsNeverReused() {
    // Morto da se' con il documento aperto: ClosedWebView chiude con rendererGone = true.
    val crashedOpen = open()
    close(crashedOpen, rendererGone = true)
    // Morto per un crash dopo la richiesta di terminazione.
    val crashedClosing = open().also { it.second.answers += true }
    close(crashedClosing).rendererGone(crashed = true)
    assertEquals(Stage.Retired, pool.stage(crashedOpen.first))
    assertEquals(Stage.Retired, pool.stage(crashedClosing.first))
    assertTrue(provider.clearing.isEmpty())
    val later = (1..10).map {
      val document = open().also { it.second.answers += true }
      close(document).rendererGone(crashed = false)
      provider.answerClear(document.first)
      document.first
    }
    assertFalse(crashedOpen.first in later)
    assertFalse(crashedClosing.first in later)
    assertEquals(1, later.toSet().size)
  }

  @Test fun documentsClosedWhileTheirRendererStartsAreAllRetiredAndNeverShared() {
    // Sfogliando in fretta: ogni documento si chiude mentre il renderer sta partendo, e quello dopo si apre subito.
    val documents = (1..24).map {
      val document = open()
      close(document)
      main.advanceBy(20)
      document
    }
    assertEquals(24, documents.map { it.first }.toSet().size)
    main.runAll()
    assertTrue(documents.all { it.second.destroys == 1 })
    assertTrue(documents.all { pool.stage(it.first) == Stage.Retired })
    assertTrue(documents.all { it.second.terminateTimes.size == RendererClosing.RetryDelays.size + 1 })
    // Il renderer che parte in tempo, invece, si termina e il suo profilo torna.
    val calm = open().also { it.second.answers += listOf(false, true) }
    val closing = close(calm)
    main.advanceBy(60)
    closing.rendererGone(crashed = false)
    provider.answerClear(calm.first)
    assertEquals(calm.first, open().first)
  }
}
