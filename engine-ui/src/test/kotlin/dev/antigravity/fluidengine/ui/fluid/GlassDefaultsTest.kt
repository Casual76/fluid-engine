package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cio' che le ottiche e le tinte di serie valgono, e come il materiale nuovo le legge (F1A.8).
 *
 * Due promesse della 4.0.0 stanno qui. La prima: **nel vetro di prima niente cambia**. Le ottiche e le tinte di
 * [GlassDefaults] sono quelle della 3.1.3 al numero, perche' `Classic` (sotto Android 13, sull'orologio, dove un
 * antenato lo chiede) le dipinge come allora, e perche' il materiale nuovo le usa come attenuatori: un numero
 * spostato qui cambierebbe due vetri insieme. La seconda: il **modo** di ogni tinta di serie. Il velo
 * (`Neutral`) per la famiglia, la pellicola dipinta (`Fixed`) per le tinte che sono una pellicola per mestiere,
 * la tinta di Apple (`Colored`) per il pannello che porta un colore, e la pellicola densa per tutte quando il
 * contrasto di sistema sale.
 *
 * Le ottiche sono valori: si leggono direttamente. Le tinte sono `@Composable` (leggono la palette e il lato del
 * tema), e si leggono da una composizione senza nodi ([composed]): nessuna interfaccia, solo il runtime.
 */
class GlassDefaultsTest {

  // --- le ottiche ---------------------------------------------------------------------------------

  /** Un'ottica scritta per intero, con i nomi: i diciassette campi di sempre. */
  private fun optics(
    blurScale: Float,
    height: Float,
    amount: Float,
    dome: Boolean,
    dispersion: Boolean,
    highlightAlpha: Float,
    shadowRadius: Float,
    shadowAlpha: Float,
    pressed: Float,
    backdropResolution: Float = 1f,
  ) = GlassOptics(
    blurScale = blurScale,
    refractionHeight = height.dp,
    refractionAmount = amount.dp,
    depthEffect = dome,
    dispersion = dispersion,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = highlightAlpha,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = shadowRadius.dp,
    shadowAlpha = shadowAlpha,
    pressedDepthBoost = pressed,
    edgeFloor = 0f,
    backdropResolution = backdropResolution,
    sizeResponse = 1f,
  )

  @Test
  fun theRoleOpticsAreTheOnesOf313() {
    assertEquals(optics(5f, 20f, 16f, false, false, 0.5f, 0f, 0f, 0f), GlassDefaults.optics(GlassRole.Bar))
    assertEquals(optics(1.8f, 19f, 29f, false, false, 0.6f, 20f, 0.7f, 0.35f), GlassDefaults.optics(GlassRole.Floating))
    assertEquals(optics(1.4f, 10f, 14f, true, true, 0.55f, 12f, 0.5f, 0.6f), GlassDefaults.optics(GlassRole.Interactive))
    assertEquals(optics(3.5f, 20f, 28f, false, false, 0.6f, 32f, 0.8f, 0f), GlassDefaults.optics(GlassRole.Modal))
    assertEquals(
      optics(0.8f, 18f, 22f, false, false, 0.5f, 0f, 0f, 0f, backdropResolution = 0.5f),
      GlassDefaults.optics(GlassRole.Content),
    )
  }

  @Test
  fun theNamedOpticsAreTheOnesOf313() {
    // Il pulsante: la lente di LibrePods sotto il dito (da 14 a 24 dp di spostamento).
    assertEquals(
      optics(1.4f, 12f, 14f, true, true, 0.55f, 12f, 0.5f, 24f / 14f - 1f),
      GlassDefaults.buttonOptics(),
    )
    assertEquals(
      optics(1.8f, 14f, 18f, false, false, 0.6f, 0f, 0f, 0f, backdropResolution = 0.6f),
      GlassDefaults.stackedContentOptics(),
    )
    val panel = optics(2.5f, 14f, 18f, false, false, 0.45f, 18f, 0.36f, 0f)
    assertEquals(panel, GlassDefaults.panelOptics())
    assertEquals(panel.copy(refractionHeight = 7.dp, refractionAmount = 9.dp), GlassDefaults.panelOptics(compact = true))
    assertEquals(panel.copy(blurScale = 3.5f), GlassDefaults.panelOptics(overSharpPage = true))
    assertEquals(
      panel.copy(blurScale = 2.8f, refractionHeight = 7.dp, refractionAmount = 9.dp),
      GlassDefaults.panelOptics(compact = true, overSharpPage = true),
    )
  }

  @Test
  fun theDefaultOpticsAreAlreadyClean() {
    // `sanitized()` e' per le ottiche scritte a mano: una di serie esce identica, quindi `Classic` e la
    // traduzione del materiale nuovo leggono gli stessi numeri.
    val all = GlassRole.entries.map { GlassDefaults.optics(it) } + listOf(
      GlassDefaults.buttonOptics(),
      GlassDefaults.stackedContentOptics(),
      GlassDefaults.panelOptics(),
      GlassDefaults.panelOptics(compact = true),
      GlassDefaults.panelOptics(overSharpPage = true),
      GlassDefaults.panelOptics(compact = true, overSharpPage = true),
    )
    for (o in all) assertEquals(o, o.sanitized())
  }

  // --- le tinte -----------------------------------------------------------------------------------

  /** Le tinte di serie lette in una composizione, con il lato e il contrasto dati. */
  private class Tints(
    val film: Color,
    val primary: Color,
    val surface: Color,
    val low: Color,
    val onSurface: Color,
    val bar: GlassTint,
    val floating: GlassTint,
    val control: GlassTint,
    val content: GlassTint,
    val stacked: GlassTint,
    val modal: GlassTint,
    val panel: GlassTint,
    val tinted: GlassTint,
    val panelRed: GlassTint,
    val darkBar: GlassTint,
    val lightBar: GlassTint,
    val darkFloating: GlassTint,
    val lightFloating: GlassTint,
    val selection: GlassTint,
  ) {
    /** La famiglia: le tinte che di serie sono il velo del materiale. */
    val family get() = listOf(bar, floating, control, content, stacked, modal, panel)

    /** Le pellicole per mestiere. */
    val films get() = listOf(darkBar, lightBar, darkFloating, lightFloating, selection)
  }

  private fun tints(dark: Boolean, contrast: Float = FluidSystemContrast.Standard): Tints = composed {
    var out: Tints? = null
    CompositionLocalProvider(LocalFluidSurfaceSide provides dark, LocalFluidSystemContrast provides contrast) {
      val scheme = MaterialTheme.colorScheme
      out = Tints(
        film = GlassDefaults.glassFilm(),
        primary = scheme.primary,
        surface = scheme.surface,
        low = scheme.surfaceContainerLow,
        onSurface = scheme.onSurface,
        bar = GlassDefaults.barTint(),
        floating = GlassDefaults.floatingTint(),
        control = GlassDefaults.controlTint(),
        content = GlassDefaults.contentTint(),
        stacked = GlassDefaults.stackedContentTint(),
        modal = GlassDefaults.modalTint(),
        panel = GlassDefaults.panelTint(),
        tinted = GlassDefaults.tintedModalTint(Red),
        panelRed = GlassDefaults.panelTint(Red),
        darkBar = GlassDefaults.darkBarTint(),
        lightBar = GlassDefaults.lightBarTint(),
        darkFloating = GlassDefaults.darkFloatingTint(),
        lightFloating = GlassDefaults.lightFloatingTint(),
        selection = GlassDefaults.selectionTint(),
      )
    }
    checkNotNull(out)
  }

  private val Red = Color(0xFFE5484D)

  /** L'opacita' di un `Color` sRGB sta in un 255-esimo: un confronto esatto proverebbe l'arrotondamento. */
  private val Quantum = 0.006f

  private fun assertColour(what: String, expected: Color, actual: Color) {
    assertEquals("$what: rosso", expected.red, actual.red, Quantum)
    assertEquals("$what: verde", expected.green, actual.green, Quantum)
    assertEquals("$what: blu", expected.blue, actual.blue, Quantum)
    assertEquals("$what: alfa", expected.alpha, actual.alpha, Quantum)
  }

  @Test
  fun theFamilyIsTheVeilAndTheFilmsArePainted() {
    for (dark in listOf(false, true)) {
      val t = tints(dark)
      for (tint in t.family) {
        assertEquals("lato scuro $dark", GlassTintMode.Neutral, tint.mode)
        assertEquals("lato scuro $dark", GlassTintMode.Neutral, glassTintLiquidMode(tint))
      }
      for (tint in t.films) assertEquals("lato scuro $dark", GlassTintMode.Fixed, glassTintLiquidMode(tint))
      assertEquals(GlassTintMode.Colored, glassTintLiquidMode(t.tinted))
      assertEquals(t.tinted, t.panelRed)
      assertEquals(t.modal, t.panel)
    }
  }

  @Test
  fun theClassicFilmsAreTheOnesOf313() {
    // Il vetro di prima legge solo overlay, fallback e hairline: sono i numeri della 3.1.3, sui due lati.
    for (dark in listOf(false, true)) {
      val t = tints(dark)
      fun pick(light: Float, darkValue: Float) = if (dark) darkValue else light
      assertColour("barra", t.film.copy(alpha = pick(0.44f, 0.40f)), t.bar.overlay)
      assertColour("barra, ripiego", t.surface.copy(alpha = pick(0.96f, 0.95f)), t.bar.fallback)
      assertColour("barra, filetto", t.onSurface.copy(alpha = pick(0.10f, 0.16f)), t.bar.hairline)
      assertColour("flottante", t.film.copy(alpha = pick(0.52f, 0.48f)), t.floating.overlay)
      assertColour("flottante, filetto", t.onSurface.copy(alpha = pick(0.08f, 0.18f)), t.floating.hairline)
      assertColour("controllo", lerp(t.film, t.primary, 0.20f).copy(alpha = pick(0.38f, 0.34f)), t.control.overlay)
      assertEquals(Color.Transparent, t.control.hairline)
      assertColour("contenuto", lerp(t.low, t.film, 0.4f).copy(alpha = pick(0.56f, 0.52f)), t.content.overlay)
      assertColour("contenuto, ripiego", t.low, t.content.fallback)
      assertColour("impilato", t.film.copy(alpha = pick(0.34f, 0.30f)), t.stacked.overlay)
      assertColour("modale", t.film.copy(alpha = pick(0.54f, 0.50f)), t.modal.overlay)
      assertColour("modale, filetto", t.onSurface.copy(alpha = pick(0.08f, 0.14f)), t.modal.hairline)
      assertColour(
        "modale tinto",
        lerp(t.film.copy(alpha = pick(0.54f, 0.50f)), Red, 0.78f).copy(alpha = pick(0.76f, 0.72f)),
        t.tinted.overlay,
      )
      assertColour("barra su foto scura", Color.Black.copy(alpha = 0.42f), t.darkBar.overlay)
      assertColour("barra su foto chiara", Color.White.copy(alpha = 0.56f), t.lightBar.overlay)
      assertColour("flottante su foto scura", Color.Black.copy(alpha = 0.48f), t.darkFloating.overlay)
      assertColour("flottante su foto chiara", Color.White.copy(alpha = 0.64f), t.lightFloating.overlay)
      assertColour(
        "selezione",
        if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
        t.selection.overlay,
      )
    }
  }

  @Test
  fun aDefaultTintWithTheAppsColourShowsIt() {
    // Il caso delle app: una tinta di serie con un tono suo. Senza toccare una riga dell'app, il materiale
    // nuovo la dipinge come il vetro di prima.
    val t = tints(dark = false)
    val brand = Color(0xFF1F9E6E).copy(alpha = 0.30f)
    for (tint in t.family) {
      val toned = tint.copy(overlay = brand)
      assertEquals(GlassTintMode.Fixed, glassTintLiquidMode(toned))
      assertEquals(GlassTintMode.Neutral, glassTintLiquidMode(tint.copy(hairline = brand)))
    }
  }

  @Test
  fun aboveStandardContrastEveryDefaultIsTheDenseFilmOnce() {
    for (dark in listOf(false, true)) {
      val standard = tints(dark)
      for (level in listOf(FluidSystemContrast.Medium, FluidSystemContrast.High)) {
        val raised = tints(dark, level)
        val veil = FluidSystemContrast.veil(level)
        // La selezione no: e' una pellicola sotto un'altra (la tab scelta dentro una barra) e la 3.1.3 non le
        // applica il contrasto. Resta com'e', ed e' gia' dipinta.
        assertEquals(standard.selection, raised.selection)
        assertEquals(GlassTintMode.Fixed, glassTintLiquidMode(raised.selection))
        val pairs = standard.family.zip(raised.family) + (standard.films - standard.selection).zip(raised.films - raised.selection) +
          listOf(standard.tinted to raised.tinted)
        for ((before, after) in pairs) {
          val where = "lato scuro $dark, contrasto $level"
          assertEquals(where, GlassTintMode.Fixed, glassTintLiquidMode(after))
          // Una volta sola: a' = a + (1 - a) * velo, partendo dalla tinta a contrasto standard.
          val expected = before.overlay.alpha + (1f - before.overlay.alpha) * veil
          assertEquals(where, expected, after.overlay.alpha, Quantum)
          assertTrue(where, after.overlay.alpha < 1f)
        }
      }
    }
  }

  // --- una composizione senza interfaccia ---------------------------------------------------------

  /** Un albero senza nodi: le tinte non ne emettono, e qui serve solo che il runtime le chiami. */
  private class NoNodes : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
  }

  /**
   * Il valore che [block] restituisce alla prima composizione. `setContent` compone subito, sul filo che lo
   * chiama: nessun fotogramma, nessun `Recomposer` che giri.
   */
  private fun <T> composed(block: @Composable () -> T): T {
    val recomposer = Recomposer(EmptyCoroutineContext)
    val composition = Composition(NoNodes(), recomposer)
    var result: Result<T>? = null
    try {
      composition.setContent { result = Result.success(block()) }
    } finally {
      composition.dispose()
      recomposer.cancel()
    }
    return checkNotNull(result) { "la composizione non ha chiamato il blocco" }.getOrThrow()
  }
}
