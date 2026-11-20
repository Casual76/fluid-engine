@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.field.trace.TodayLiquidDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fotogrammi a 120 Hz di tempo virtuale (in ms interi: 8, 17, 25, ...). */
private class GroupFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    val now = scheduler.currentTime
    val next = frameAfter(now)
    delay(next - now)
    return onFrame(next * 1_000_000L)
  }

  companion object {
    /** Il primo fotogramma dopo [ms]: i fotogrammi stanno a `ceil(k · 25 / 3)` ms. */
    fun frameAfter(ms: Long): Long {
      var k = ms * 3L / 25L
      while ((k * 25L + 2L) / 3L <= ms) k++
      return (k * 25L + 2L) / 3L
    }
  }
}

/**
 * S4f sul Tab S9 (NO 3): al ritorno del gruppo, in scuro a f0236, la stella e' gia' leggibile, il glifo a sinistra a meta' e
 * quello a destra ridotto al bordo; la capsula intera arriva nei fotogrammi dopo. Nell'annullo a meta' nascita (f0193–f0195) i
 * simboli della capsula stanno sopra le righe del menu. Le tre icone devono tornare insieme, con la capsula.
 *
 * Il ritorno vero ([FluidMenuJourney.run], la goccia del banco come `runLiquid`) da menu fermo e da una nascita interrotta a
 * ~150 ms: a ogni fotogramma la sagoma disegnata e la fase delle icone ([FluidMenuAnchor.groupProgress], spente da
 * [FluidMotion.MenuGroupFaceProgressSpan]). Quando le icone si vedono, il vetro deve contenerle tutte e tre: prima (la sola
 * regola del progresso) e dopo (con la copertura della capsula, [fluidMenuGroupFaceCover]).
 */
class GruppoRitornoIconeTest {
  private val d = 2.125f
  // Il gruppo della foto: tre tasti da 44 dp, il menu sul terzo, e il pannello sotto.
  private val capsule = Rect(64f, 600f, 64f + 132f * d, 600f + 44f * d)
  private val trigger = Rect(capsule.right - 44f * d, capsule.top, capsule.right, capsule.bottom)
  private val panel = Rect(64f, capsule.bottom + 8f * d, 64f + 250f * d, capsule.bottom + 8f * d + 196f * d)

  /** Le icone: 24 dp al centro di ogni tasto, quindi 10 dp dentro la capsula a sinistra, a destra, sopra e sotto. */
  private val icons = Rect(capsule.left + 10f * d, capsule.top + 10f * d, capsule.right - 10f * d, capsule.bottom - 10f * d)

  private class Frame(
    val t: Long,
    val drawn: Rect,
    val progress: Float,
    val phase: Float,
    val phaseBefore: Float,
    /** L'inchiostro del congedo ([FluidMenuJourney.contentVisibility]) e le voci come le disegna l'host (`lensInkAlpha`). */
    val visibility: Float,
    val ink: Float,
    /**
     * La fase che la barra mostra in questo fotogramma: quella scritta dal vetro del fotogramma prima (lo strato delle icone si
     * aggiorna dopo il disegno, S4g-quater). Le voci leggono [phase], scritta dal vetro di questo.
     */
    val phaseShown: Float = phase,
  )

  private fun TestScope.ritorno(dismissMs: Long): List<Frame> {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + GroupFrames(testScheduler))
    val anchor = FluidMenuAnchor().apply {
      restBounds = trigger
      pressedBounds = trigger
      groupBounds = capsule
    }
    val journey = FluidMenuJourney().apply {
      prepare(anchor, Offset.Zero, panel, 22f * d, FluidMenuInflation.None, liquidMenu = true, appleBirth = true)
      retarget(1f)
    }
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
      liquidBody = true
    }
    journey.install(physics)
    // Come l'host (`FluidMenuLayer`): la copertura della capsula dalla sagoma disegnata.
    journey.groupCover = { fluidMenuGroupFaceCover(physics.ensurePlan().bounds, capsule) }
    val driver = TodayLiquidDriver(physics, d)
    val open = scope.launch { journey.run(reducedMotion = false) }
    runCurrent()
    val out = ArrayList<Frame>()
    var at = 0L
    var closed = false
    var t = GroupFrames.frameAfter(0L)
    while (t < dismissMs + 900L) {
      advanceTimeBy(t - at)
      runCurrent()
      at = t
      if (!closed && t >= dismissMs) {
        // Il congedo nell'ordine dell'host: il verso cambia nella composizione, poi riparte l'effetto col ritorno.
        closed = true
        journey.retarget(0f)
        open.cancel()
        scope.launch { journey.run(reducedMotion = false) }
        runCurrent()
      }
      driver.frame(t * 1_000_000L)
      if (closed) {
        // L'ordine del Tab S9: la barra ha la fase del fotogramma prima, poi la superficie disegna il vetro e la scrive
        // ([FluidMenuJourney.noteGroupGlass]), poi l'host disegna le icone e le voci.
        val shown = anchor.groupProgress()
        journey.noteGroupGlass()
        out += Frame(
          t, physics.ensurePlan().bounds, journey.progress.value, anchor.groupProgress(), fluidMenuGroupFaceReturn(journey.progress.value),
          journey.contentVisibility.value, journey.lensInkAlpha(), phaseShown = shown,
        )
      }
      t = GroupFrames.frameAfter(t)
    }
    job.cancel()
    return out
  }

  private fun holds(drawn: Rect) =
    drawn.left <= icons.left + 1f && drawn.right >= icons.right - 1f && drawn.top <= icons.top + 1f && drawn.bottom >= icons.bottom - 1f

  private fun check(name: String, frames: List<Frame>) {
    val span = FluidMotion.MenuGroupFaceProgressSpan
    var before = 0
    var after = 0
    for (f in frames) {
      val cover = fluidMenuGroupFaceCover(f.drawn, capsule)
      println(
        "TAVOLA $name t=${f.t} p=${"%.3f".format(f.progress)} vetro=${"%.0f..%.0f x %.0f..%.0f".format(f.drawn.left, f.drawn.right, f.drawn.top, f.drawn.bottom)} " +
          "copertura=${"%.2f".format(cover)} icone-prima=${"%.2f".format(f.phaseBefore)} icone-dopo=${"%.2f".format(f.phase)}",
      )
      if (f.phaseBefore < span && !holds(f.drawn)) before++
      if (f.phase < span && !holds(f.drawn)) after++
    }
    println("TAVOLA $name fotogrammi con le icone fuori dal vetro: prima $before, dopo $after")
    assertEquals("$name: icone che si vedono fuori dal vetro", 0, after)
    assertTrue("$name: le icone tornano", frames.any { it.phase < span * 0.5f })
    // Mai la capsula vuota: appena il vetro tiene le icone, e le righe del menu se ne sono andate, si accendono; al piu' un
    // fotogramma a 120 Hz. Con la prima regola (la capsula a 0,8 di intersezione su unione) era 1 da fermo e 9 nell'annullo.
    val empty = frames.count { holds(it.drawn) && it.progress <= 0.02f && it.phase >= span }
    println("TAVOLA $name fotogrammi col vetro che tiene le icone e le icone spente: $empty")
    assertTrue("$name: capsula vuota per $empty fotogrammi", empty <= 1)
    assertEquals("$name: piene alla consegna", 0f, frames.last().phase, 0.02f)
    // S4g (NO 3): mai la pillola vuota piu' stretta delle icone vicino alla capsula (scuro f0131, chiaro f0058), da cui la
    // capsula larga coi glifi arrivava in un passo. Col tondo del ritorno erano i fotogrammi in cui la bolla si allargava.
    val narrow = frames.count {
      it.phase >= span && it.drawn.height <= capsule.height * 1.6f && it.drawn.width < icons.width && it.progress < 0.5f
    }
    val step = frames.zipWithNext().maxOf { (a, b) -> a.phase - b.phase }
    println("TAVOLA $name pillola vuota piu' stretta delle icone: $narrow fotogrammi; passo piu' grande delle icone ${"%.2f".format(step)}")
    assertEquals("$name: pillola vuota stretta", 0, narrow)
    // S4g-bis (d): in chiaro a f0361 e a f0391–f0392 voci sfocate dietro le tre icone piene. Le icone si vedono con la curva
    // d'uscita della barra; con la regola di S4g (il quadrato dell'inchiostro uscito) erano gia' quasi piene sopra le voci.
    var latch = 1f
    var overlapBefore = 0
    var overlapAfter = 0
    for (f in frames) {
      val cover = fluidMenuGroupFaceCover(f.drawn, capsule)
      val gone = 1f - f.visibility
      latch = minOf(latch, fluidMenuGroupFaceReturn(f.progress, cover * gone * gone))
      val iconsBefore = iconAlpha(latch)
      val iconsAfter = iconAlpha(f.phase)
      if (iconsBefore > 0.01f && f.visibility > 0.01f) overlapBefore++
      if (iconsAfter > 0.01f && f.ink > 0.01f) overlapAfter++
      println(
        "TAVOLA $name t=${f.t} icone ${"%.2f".format(iconsAfter)} voci ${"%.2f".format(f.ink)} " +
          "(S4g: icone ${"%.2f".format(iconsBefore)} voci ${"%.2f".format(f.visibility)})",
      )
    }
    println("TAVOLA $name fotogrammi con icone e voci insieme: S4g $overlapBefore, ora $overlapAfter")
    assertEquals("$name: icone e voci insieme", 0, overlapAfter)
    // S4g-quater (d): sul Tab S9 restava un fotogramma con le voci sotto le tre icone piene (chiaro f0129, scuro f0119). Con le
    // icone della barra un fotogramma indietro (la fase del vetro di prima) e le voci di adesso, mai insieme; la capsula vuota
    // al piu' un fotogramma; e le voci sfumano mentre il vetro avvolge la capsula, prima delle icone (la via di mezzo).
    var overlapShown = 0
    var fading = 0
    var emptyShown = 0
    for (f in frames) {
      val icons = iconAlpha(f.phaseShown)
      if (icons > 0.01f && f.ink > 0.01f) overlapShown++
      if (f.ink > 0.01f && f.ink < f.visibility * 0.95f) fading++
      if (holds(f.drawn) && f.progress <= 0.02f && f.phaseShown >= span) emptyShown++
      println(
        "TAVOLA $name t=${f.t} barra ${"%.2f".format(icons)} (fase ${"%.2f".format(f.phaseShown)}) voci ${"%.2f".format(f.ink)} " +
          "di ${"%.2f".format(f.visibility)}",
      )
    }
    println("TAVOLA $name con la barra un fotogramma indietro: insieme $overlapShown, voci che sfumano $fading, capsula vuota $emptyShown")
    assertEquals("$name: icone della barra e voci insieme", 0, overlapShown)
    assertTrue("$name: capsula vuota con la barra un fotogramma indietro per $emptyShown fotogrammi", emptyShown <= 1)
    // Le voci sfumano prima delle icone solo se un fotogramma cade mentre il vetro comincia ad avvolgere la capsula: col vetro che
    // la avvolge in fretta (S4i, copertura piena a 0,6) lo scambio e' di un fotogramma. Qui si stampa e basta.
    // S4i (d): mai la capsula quasi vuota. Col vetro gia' attorno alla capsula (copertura da 0,5) e le voci spente, le icone sono
    // almeno a 0,3: sul Tab S9 da fermo la capsula restava quasi vuota per 2-3 fotogrammi (voto S4h, ~22-23 ms).
    val nearEmpty = frames.count {
      fluidMenuGroupFaceCover(it.drawn, capsule) >= FluidMenuGroupCoverReady && it.ink <= 0.01f && iconAlpha(it.phase) < 0.3f
    }
    println("TAVOLA $name capsula quasi vuota (vetro capsula, voci spente, icone sotto 0,3): $nearEmpty")
    assertEquals("$name: capsula quasi vuota", 0, nearEmpty)
  }

  /** Un fotogramma del Tab S9: i ms dal congedo, il progresso, l'inchiostro delle voci e il vetro disegnato. */
  private class DeviceFrame(val ms: Float, val progress: Float, val ink: Float, val drawn: Rect)

  /**
   * Il congedo da fermo del gruppo sul Tab S9 (sessione s4q2, chiaro), letto dai marcatori `menu.birth` (progresso e vetro) e
   * `menu.group` (inchiostro): il vetro avvolge la capsula gia' a 155 ms, con le voci al 7,7%.
   */
  private val tabletRest = listOf(
    DeviceFrame(61.9f, 0.785f, 1.000f, Rect(73f, 1833f, 579f, 2258f)),
    DeviceFrame(76.1f, 0.714f, 1.000f, Rect(81f, 1829f, 563f, 2235f)),
    DeviceFrame(82.0f, 0.678f, 0.982f, Rect(80f, 1829f, 559f, 2228f)),
    DeviceFrame(92.3f, 0.642f, 0.929f, Rect(78f, 1830f, 547f, 2209f)),
    DeviceFrame(107.0f, 0.571f, 0.745f, Rect(71f, 1832f, 508f, 2148f)),
    DeviceFrame(118.9f, 0.535f, 0.629f, Rect(68f, 1833f, 485f, 2111f)),
    DeviceFrame(139.4f, 0.428f, 0.264f, Rect(57f, 1837f, 422f, 2011f)),
    DeviceFrame(155.3f, 0.356f, 0.077f, Rect(54f, 1837f, 406f, 1985f)),
    DeviceFrame(170.8f, 0.320f, 0.021f, Rect(55f, 1838f, 405f, 1984f)),
    DeviceFrame(179.2f, 0.285f, 0.000f, Rect(56f, 1838f, 404f, 1981f)),
    DeviceFrame(187.7f, 0.213f, 0.000f, Rect(62f, 1838f, 398f, 1969f)),
    DeviceFrame(196.0f, 0.177f, 0.000f, Rect(66f, 1838f, 394f, 1963f)),
    DeviceFrame(204.9f, 0.142f, 0.000f, Rect(69f, 1838f, 391f, 1956f)),
    DeviceFrame(215.1f, 0.106f, 0.000f, Rect(72f, 1838f, 388f, 1950f)),
    DeviceFrame(226.0f, 0.070f, 0.000f, Rect(75f, 1838f, 385f, 1945f)),
    DeviceFrame(239.6f, 0.000f, 0.000f, Rect(77f, 1838f, 383f, 1940f)),
  )

  /** La capsula del gruppo come la disegna il vetro a riposo nello stesso posto (s4q2). */
  private val tabletCapsule = Rect(77f, 1838f, 383f, 1940f)

  /**
   * S4i (d), voto S4h: nel congedo da fermo, in chiaro f0090–f0091 e in scuro f0077–f0078, la capsula restava quasi vuota per due
   * campioni (~22-23 ms) prima che le icone salissero. I fotogrammi veri del Tab S9, rigiocati con la regola di S4g-quater
   * ([fluidMenuGroupFaceReturn]) e con quella di adesso ([fluidMenuGroupFacePhase]): col vetro che avvolge la capsula, mai voci e
   * icone entrambe sotto 0,3, e mai tutte e due sopra 0,01.
   */
  @Test fun onTheTabletTheRestingReturnNeverLeavesTheCapsuleAlmostEmpty() {
    fun replay(name: String, rule: (DeviceFrame, Float) -> Float): Pair<Int, Int> {
      var latch = 1f
      var empty = 0
      var both = 0
      for (f in tabletRest) {
        val cover = fluidMenuGroupFaceCover(f.drawn, tabletCapsule)
        latch = minOf(latch, rule(f, cover))
        val items = fluidMenuGroupReturnInk(f.ink, latch)
        val faces = iconAlpha(latch)
        val dim = cover >= FluidMenuGroupCoverReady && items < 0.3f && faces < 0.3f
        if (dim) empty++
        if (items > 0.01f && faces > 0.01f) both++
        println(
          "TAVOLA tablet $name ${"%.1f".format(f.ms)} ms p=${"%.3f".format(f.progress)} copertura=${"%.2f".format(cover)} " +
            "fase=${"%.3f".format(latch)} voci=${"%.3f".format(items)} icone=${"%.3f".format(faces)}${if (dim) " QUASI VUOTA" else ""}",
        )
      }
      println("TAVOLA tablet $name: quasi vuota $empty, insieme $both")
      return empty to both
    }
    // La regola di S4g-quater: il progresso, o `1 - copertura`.
    val before = replay("S4g-quater") { f, cover ->
      maxOf((f.progress / FluidMenuGroupFaceReturnShare).coerceIn(0f, 1f), 1f - cover.coerceIn(0f, 1f))
    }
    val now = replay("S4i") { f, cover -> fluidMenuGroupFacePhase(f.progress, cover, f.ink) }
    // La regola di prima rifa' il difetto del tablet: piu' fotogrammi di fila con la capsula quasi vuota.
    assertTrue("S4g-quater: ${before.first} fotogrammi quasi vuoti", before.first >= 2)
    assertEquals("S4i: fotogrammi quasi vuoti", 0, now.first)
    assertEquals("S4i: voci e icone insieme", 0, now.second)
    // Le icone arrivano subito dopo le voci a 0,26 (139 ms): gia' a 155 ms sopra 0,5.
    val at155 = fluidMenuGroupFacePhase(0.356f, fluidMenuGroupFaceCover(tabletRest[7].drawn, tabletCapsule), 0.077f)
    assertTrue("a 155 ms le icone a ${iconAlpha(at155)}", iconAlpha(at155) > 0.5f)
  }

  /** La regola: con le voci quasi spente e il vetro gia' capsula conta il vetro; altrimenti quella di prima. */
  @Test fun theFacesFollowTheGlassOnceTheItemsAreGone() {
    // Voci ancora piene (l'annullo): la regola di sempre.
    assertEquals(fluidMenuGroupFaceReturn(0.3f, 0.8f), fluidMenuGroupFacePhase(0.3f, 0.8f, 0.6f), 0f)
    // Vetro non ancora capsula: la regola di sempre.
    assertEquals(fluidMenuGroupFaceReturn(0.3f, 0.3f), fluidMenuGroupFacePhase(0.3f, 0.3f, 0f), 0f)
    // Voci spente e vetro capsula: il vetro, e mai piu' in alto della regola di prima.
    assertEquals(1f - 0.48f / FluidMenuGroupCoverFull, fluidMenuGroupFacePhase(0.3f, 0.48f, 0.05f), 1e-5f)
    assertEquals(0f, fluidMenuGroupFacePhase(0.3f, 0.8f, 0.05f), 0f)
    for (p in listOf(0f, 0.2f, 0.5f, 1f)) for (c in listOf(0f, 0.5f, 0.9f, 1f)) for (ink in listOf(0f, 0.1f, 0.5f, 1f)) {
      assertTrue(fluidMenuGroupFacePhase(p, c, ink) <= fluidMenuGroupFaceReturn(p, c) + 1e-6f)
    }
  }

  /** L'opacita' delle icone a questa fase, come le disegna l'host al ritorno ([fluidMenuGroupFacesAlpha]). */
  private fun iconAlpha(phase: Float): Float = fluidMenuGroupFacesAlpha(phase, blurAvailable = true)

  /**
   * S4g-quater (d), coi marcatori sul Tab S9: durante il ritorno lo strato delle icone della barra non si ricalcolava, e l'host
   * le disegnava piene appena la fase scendeva sotto lo span, con le voci vive fino a 0,647. Ora l'opacita' delle facce e quella
   * delle voci vengono dalla stessa fase: a nessuna fase sono sopra zero insieme, con e senza sfocatura.
   */
  @Test fun theFacesAndTheItemsNeverShareAPhase() {
    var both = 0
    for (k in 0..1000) {
      val phase = k / 1000f
      for (blur in listOf(true, false)) {
        val faces = fluidMenuGroupFacesAlpha(phase, blur)
        val items = fluidMenuGroupItemsGate(phase)
        if (faces > 0.01f && items > 0.01f) both++
      }
    }
    assertEquals("fasi con facce e voci insieme", 0, both)
    // Piene alla consegna, spente finche' il vetro non avvolge la capsula, e le voci piene a fase piena.
    assertEquals(1f, fluidMenuGroupFacesAlpha(0f), 1e-4f)
    assertEquals(0f, fluidMenuGroupFacesAlpha(FluidMotion.MenuGroupFaceProgressSpan), 0f)
    assertEquals(1f, fluidMenuGroupItemsGate(1f), 0f)
  }

  @Test fun theIconsComeBackTogetherWithTheCapsuleFromARestingMenu() = runTest { check("fermo", ritorno(dismissMs = 1_200L)) }

  @Test fun theIconsStayOffTheEggWhenTheBirthIsCancelled() = runTest {
    // L'annullo di S4f (f0192–f0195): il congedo a ~150 ms dalla nascita, con l'uovo e le righe.
    check("annullo", ritorno(dismissMs = 150L))
  }

  @Test fun theCoverIsTheCapsuleOnlyWhenTheGlassIsTheCapsule() {
    assertEquals(1f, fluidMenuGroupFaceCover(capsule, capsule), 1e-4f)
    // Il pannello copre tutta la capsula ma non e' lei; la bolla tonda sul tasto e' piu' stretta.
    assertTrue(fluidMenuGroupFaceCover(panel.copy(top = capsule.top), capsule) < 0.5f)
    val bubble = Rect(trigger.center.x - 32f * d, trigger.center.y - 32f * d, trigger.center.x + 32f * d, trigger.center.y + 32f * d)
    assertTrue(fluidMenuGroupFaceCover(bubble, capsule) < 0.5f)
    // Il pannello che si stringe sopra la capsula le contiene, ma e' molto piu' grande: spente.
    assertEquals(0f, fluidMenuGroupFaceCover(Rect(capsule.left - 40f, capsule.top - 300f, capsule.right + 40f, capsule.bottom), capsule), 0f)
    // Il vetro che le ha appena raggiunte: si accendono; con 5 dp attorno sono piene.
    assertEquals(0f, fluidMenuGroupFaceCover(icons, capsule), 1e-4f)
    val wrapped = Rect(icons.left - 5f * d, icons.top - 5f * d, icons.right + 5f * d, icons.bottom + 5f * d)
    assertEquals(1f, fluidMenuGroupFaceCover(wrapped, capsule), 0.01f)
    // Spente finche' il vetro non le tiene, piene quando le avvolge, anche a progresso zero.
    assertTrue(fluidMenuGroupFaceReturn(0f, 0f) >= FluidMotion.MenuGroupFaceProgressSpan)
    assertEquals(0f, fluidMenuGroupFaceReturn(0f, 1f), 0f)
    // Senza l'host conta il solo progresso, come prima.
    assertEquals(0.5f, fluidMenuGroupFaceReturn(FluidMenuGroupFaceReturnShare / 2f), 1e-4f)
  }
}
