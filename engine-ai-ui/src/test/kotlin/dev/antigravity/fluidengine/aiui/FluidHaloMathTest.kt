package dev.antigravity.fluidengine.aiui

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.halo.FluidHaloColours
import dev.antigravity.fluidengine.aiui.halo.FluidHaloMood
import dev.antigravity.fluidengine.aiui.halo.HaloBaseSpeed
import dev.antigravity.fluidengine.aiui.halo.amplitudeTarget
import dev.antigravity.fluidengine.aiui.halo.blobAlpha
import dev.antigravity.fluidengine.aiui.halo.blobBreath
import dev.antigravity.fluidengine.aiui.halo.blobFraction
import dev.antigravity.fluidengine.aiui.halo.blobRadiusFactor
import dev.antigravity.fluidengine.aiui.halo.haloApproach
import dev.antigravity.fluidengine.aiui.halo.haloMood
import dev.antigravity.fluidengine.aiui.halo.perimeterSpeed
import dev.antigravity.fluidengine.aiui.halo.sampleCycle
import dev.antigravity.fluidengine.aiui.halo.tempo
import dev.antigravity.fluidengine.aiui.halo.topHaloColours
import dev.antigravity.fluidengine.aiui.halo.wrap01
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La matematica dell'alone: cio' che si puo' dire di un campo di luce senza guardarlo. */
class FluidHaloMathTest {

  private val colours = FluidHaloColours(
    accent = Color(0xFF0000FF),
    secondary = Color(0xFF00FF00),
    tertiary = Color(0xFFFF0000),
    error = Color(0xFFFF00FF),
  )

  @Test
  fun `l'umore dell'alone viene dallo stato dell'assistente`() {
    assertEquals(FluidHaloMood.LISTENING, AssistantState.Listening(0).haloMood())
    assertEquals(FluidHaloMood.WORKING, AssistantState.Transcribing.haloMood())
    assertEquals(FluidHaloMood.WORKING, AssistantState.Classifying("q", ProviderId.GROQ).haloMood())
    assertEquals(FluidHaloMood.WRITING, AssistantState.Answering("q", "x", ProviderId.GROQ).haloMood())
    assertEquals(FluidHaloMood.DONE, AssistantState.Done("q", "a", emptyList(), ProviderId.GROQ, AskMode.TEXT, null, emptyList(), 1).haloMood())
    assertEquals(FluidHaloMood.ERROR, AssistantState.Failed("q", FailureKind.NETWORK, null, null, null).haloMood())
    assertEquals(FluidHaloMood.IDLE, AssistantState.Idle.haloMood())
    assertEquals(FluidHaloMood.IDLE, AssistantState.HeardNothing.haloMood())
  }

  @Test
  fun `scrivendo scorre piu' in fretta che ascoltando, e a riposo quasi non si muove`() {
    assertTrue(FluidHaloMood.WRITING.perimeterSpeed() > FluidHaloMood.LISTENING.perimeterSpeed())
    assertTrue(FluidHaloMood.LISTENING.perimeterSpeed() > FluidHaloMood.WORKING.perimeterSpeed())
    assertTrue(FluidHaloMood.WORKING.perimeterSpeed() > FluidHaloMood.IDLE.perimeterSpeed())
    // Ma si muove: e' quel poco di moto che distingue un alone da una cornice.
    assertTrue(FluidHaloMood.IDLE.perimeterSpeed() > 0f)
    // Da nascosto tiene il passo di riposo, cosi' nella dissolvenza in uscita le macchie non si inchiodano.
    assertEquals(FluidHaloMood.IDLE.perimeterSpeed(), FluidHaloMood.HIDDEN.perimeterSpeed(), 0f)
  }

  @Test
  fun `il tempo dell'orologio, moltiplicato per la velocita' base, da' la velocita' dell'umore`() {
    for (mood in FluidHaloMood.entries) {
      assertEquals("$mood", mood.perimeterSpeed(), mood.tempo() * HaloBaseSpeed, 1e-6f)
    }
    // E in ascolto il tempo e' uno: e' la sua definizione.
    assertEquals(1f, FluidHaloMood.LISTENING.tempo(), 1e-5f)
  }

  @Test
  fun `in ascolto l'ampiezza segue la voce ma non parte mai da zero`() {
    assertEquals(0.30f, FluidHaloMood.LISTENING.amplitudeTarget(0f), 1e-6f)
    assertEquals(1.0f, FluidHaloMood.LISTENING.amplitudeTarget(1f), 1e-6f)
    assertEquals(0.65f, FluidHaloMood.LISTENING.amplitudeTarget(0.5f), 1e-6f)
    // Un livello fuori scala non fa un alone oltre il pieno.
    assertEquals(1.0f, FluidHaloMood.LISTENING.amplitudeTarget(9f), 1e-6f)
    assertEquals(0.30f, FluidHaloMood.LISTENING.amplitudeTarget(-1f), 1e-6f)
    assertEquals(0f, FluidHaloMood.HIDDEN.amplitudeTarget(1f), 0f)
    // Al lavoro l'alone e' piu' acceso che a riposo.
    assertTrue(FluidHaloMood.WORKING.amplitudeTarget(0f) > FluidHaloMood.IDLE.amplitudeTarget(0f))
  }

  @Test
  fun `l'ampiezza sale piu' in fretta di quanto scende`() {
    val dt = 1f / 60f
    val rise = haloApproach(0f, 1f, dt)
    val fall = 1f - haloApproach(1f, 0f, dt)
    assertTrue("attacco $rise, rilascio $fall", rise > fall)
    // A 60 Hz un fotogramma e' esattamente la costante di riferimento.
    assertEquals(0.35f, rise, 1e-5f)
    assertEquals(0.08f, fall, 1e-5f)
  }

  @Test
  fun `il passo e' lo stesso a qualunque frequenza di aggiornamento`() {
    // Due mezzi passi fanno un passo intero: a 120 Hz non si accelera.
    val once = haloApproach(0f, 1f, 1f / 60f)
    val half = haloApproach(0f, 1f, 1f / 120f)
    val twiceHalf = haloApproach(half, 1f, 1f / 120f)
    assertEquals(once, twiceHalf, 1e-5f)
  }

  @Test
  fun `l'ampiezza raggiunge il bersaglio senza oltrepassarlo`() {
    var a = 0f
    repeat(600) { a = haloApproach(a, 0.7f, 1f / 60f) }
    assertEquals(0.7f, a, 1e-3f)
    assertTrue(a <= 0.7f)
  }

  @Test
  fun `le macchie sono equispaziate e restano sul perimetro`() {
    val n = 14
    // Al tempo zero, senza ondeggiare di molto: le frazioni sono i/n piu' un'inezia.
    val fractions = (0 until n).map { blobFraction(it, n, 0f) }
    for (f in fractions) assertTrue("$f fuori dal perimetro", f in 0f..1f)
    for (i in 1 until n) {
      val gap = fractions[i] - fractions[i - 1]
      assertTrue("distanza $gap", gap > 0.04f && gap < 0.10f)
    }
    // Nel tempo restano dentro [0, 1): il perimetro si chiude.
    for (t in listOf(0f, 3.3f, 17f, 250f, 10_000f)) {
      for (i in 0 until n) assertTrue(blobFraction(i, n, t) in 0f..1f)
    }
  }

  @Test
  fun `il perimetro si chiude, anche tornando indietro`() {
    assertEquals(0.2f, wrap01(1.2f), 1e-5f)
    assertEquals(0.8f, wrap01(-0.2f), 1e-5f)
    assertEquals(0f, wrap01(3f), 1e-5f)
    assertEquals(0.5f, wrap01(0.5f), 0f)
  }

  @Test
  fun `il respiro e il raggio stanno nei loro limiti`() {
    for (i in 0 until 14) for (t in listOf(0f, 1.1f, 5f, 42f)) {
      val breath = blobBreath(i, 14, t)
      assertTrue("respiro $breath", breath in 0.69f..1.01f)
    }
    // Ampiezza zero: tre quarti del raggio; piena: uno e un quinto; il respiro scala il risultato.
    assertEquals(0.75f, blobRadiusFactor(0f, 1f), 1e-6f)
    assertEquals(1.2f, blobRadiusFactor(1f, 1f), 1e-6f)
    assertEquals(0.6f, blobRadiusFactor(0f, 0.8f), 1e-6f)
  }

  @Test
  fun `l'alpha di una macchia sale con l'ampiezza e sparisce con la presenza`() {
    assertEquals(0.18f, blobAlpha(0f, 1f, 0.45f), 1e-6f)
    assertEquals(0.45f, blobAlpha(1f, 1f, 0.45f), 1e-6f)
    assertEquals(0f, blobAlpha(1f, 0f, 0.45f), 0f)
    assertEquals(0.225f, blobAlpha(1f, 0.5f, 0.45f), 1e-6f)
  }

  @Test
  fun `il colore del ciclo gira e interpola`() {
    val cycle = listOf(Color.Red, Color.Green, Color.Blue)
    assertEquals(Color.Red, sampleCycle(cycle, 0f))
    // A un terzo del ciclo e' il secondo colore (a meno dell'ultimo bit della moltiplicazione).
    val second = sampleCycle(cycle, 1f / 3f)
    assertEquals(0f, second.red, 1e-2f)
    assertEquals(1f, second.green, 1e-2f)
    assertEquals(0f, second.blue, 1e-2f)
    // 1.0 e' come 0.0: il ciclo si chiude.
    assertEquals(sampleCycle(cycle, 0f), sampleCycle(cycle, 1f))
    assertEquals(sampleCycle(cycle, 0.25f), sampleCycle(cycle, 1.25f))
    // A meta' fra due colori sta in mezzo (la mescolanza e' percettiva, non lineare: non e' 0,5 giusto).
    val mid = sampleCycle(listOf(Color.Black, Color.White), 0.25f)
    assertTrue("${mid.red}", mid.red > 0.2f && mid.red < 0.7f)
    // Un solo colore e' quel colore.
    assertEquals(Color.Red, sampleCycle(listOf(Color.Red), 0.7f))
  }

  @Test
  fun `il ciclo dei colori per umore, l'errore e' rosso e il fatto torna sull'accento`() {
    val error = colours.cycle(FluidHaloMood.ERROR)
    assertEquals(6, error.size)
    assertEquals(colours.error, error[0])
    // Ma un accenno di accento resta: un rosso solo sembra un allarme, non un errore.
    assertTrue(error.any { it.red == colours.accent.red && it.alpha < 1f })
    val done = colours.cycle(FluidHaloMood.DONE)
    assertEquals(colours.accent, done[0])
    val working = colours.cycle(FluidHaloMood.WORKING)
    assertEquals(listOf(colours.accent, colours.secondary, colours.tertiary), working.take(3))
    assertEquals(6, working.size)
  }

  @Test
  fun `l'aureola in cima ha quattro macchie, e l'errore e' l'unico che prende il rosso`() {
    for (mood in listOf(FluidHaloMood.LISTENING, FluidHaloMood.WORKING, FluidHaloMood.WRITING, FluidHaloMood.DONE, FluidHaloMood.IDLE, FluidHaloMood.ERROR)) {
      assertEquals("$mood", 4, topHaloColours(colours, mood).size)
    }
    val error = topHaloColours(colours, FluidHaloMood.ERROR)
    assertTrue(error.all { it.red == colours.error.red && it.blue == colours.error.blue })
    val working = topHaloColours(colours, FluidHaloMood.WORKING)
    assertTrue(working.none { it.red == colours.error.red && it.green == colours.error.green && it.blue == colours.error.blue })
    // A riposo l'aureola e' quella del "fatto": non ha un umore suo.
    assertEquals(topHaloColours(colours, FluidHaloMood.DONE), topHaloColours(colours, FluidHaloMood.IDLE))
  }
}
