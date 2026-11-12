package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.forContrast
import dev.antigravity.fluidengine.ui.fluid.glassTintLiquidMode
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.legibleInk
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassScene
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassSceneOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Il profilo della pillola della tab bar (F1B.4, `GlassPuckProfile.kt`): che sia **derivato** dal profilo di serie
 * e non ricopiato, che la pillola premuta sopra la riga nascosta sia la pillola a riposo, che la regola del vetro
 * annidato ne riduca l'ombra una volta sola.
 *
 * In fondo, **solo se** c'è la variabile d'ambiente `GLASS_PUCK_SCENE_DIR` (di solito `tools/glassbench/scenes/f1b`),
 * scrive le scene del banco `puck-*`: la barra e la sua pillola, scritte da `glassRecipe` con questi profili.
 */
class GlassPuckProfileTest {

  private val base = GlassLiquidProfile.Shipped
  private val bar = 62f
  private val optics = GlassDefaults.optics(GlassRole.Floating).sanitized()

  /** Un velo posato su un fondo grigio [backdrop] (0..1), senza ispessimento: `fondo · (1 − a) + colore · a`. */
  private fun over(backdrop: Float, veil: Color): Float = backdrop * (1f - veil.alpha) + veil.red * veil.alpha

  /**
   * Un colore sRGB di Compose tiene 8 bit per canale: la composizione è esatta, il velo che la porta allo shader
   * arriva al più vicino dei 256 livelli. Mezzo livello per canale e alfa, sommati: un livello e mezzo.
   */
  private val eightBit = 1.5f / 255f

  private fun recipe(profile: GlassLiquidProfile, w: Float, h: Float, dark: Boolean): GlassRecipe =
    glassRecipe(role = GlassRole.Floating, widthDp = w, heightDp = h, darkSide = dark, optics = optics, profile = profile)

  @Test
  fun thePuckFollowsTheStockVeilInsteadOfCopyingIt() {
    val other = base.copy(lightVeil = base.lightVeil.copy(color = Color(0x80E0E0E8)))
    val stock = glassPuckProfile(bar, base)
    val moved = glassPuckProfile(bar, other)
    assertNotEquals(stock.lightVeil.color, moved.lightVeil.color)
    assertEquals(glassPuckSelectedVeil(other.lightVeil, GlassPuckSelections.Ios27.light), moved.lightVeil)
    // E senza selezione la pillola è la barra.
    assertEquals(base.lightVeil, glassPuckProfile(bar, base, GlassPuckSelections.None).lightVeil)
  }

  @Test
  fun theSelectionIsABlackFilmOverTheVeilWrittenAsOneVeil() {
    for (veil in listOf(base.lightVeil, base.darkVeil, base.darkLargeVeil)) {
      for (s in listOf(0.05f, 0.1f, 0.24f, 0.5f)) {
        val one = glassPuckSelectedVeil(veil.copy(adapt = 0f), s)
        for (b in listOf(0f, 0.05f, 0.3f, 0.6f, 0.93f, 1f)) {
          val twoLayers = over(over(b, veil.color), Color.Black.copy(alpha = s))
          assertEquals("velo $veil, selezione $s, fondo $b", twoLayers, over(b, one.color), eightBit)
        }
      }
    }
  }

  @Test
  fun thePuckIsDarkerThanTheBarOnBothSides() {
    for (dark in listOf(false, true)) {
      val b = if (dark) 0.05f else 0.93f
      val barBody = over(b, glassPuckBarVeil(base, dark, bar).color)
      val puckBody = over(b, glassPuckProfile(bar, base).veil(dark).color)
      assertTrue("lato ${if (dark) "scuro" else "chiaro"}: $puckBody contro $barBody", puckBody < barBody)
    }
  }

  @Test
  fun thePressedPuckOverTheHiddenRowIsTheRestingPuck() {
    // La riga nascosta porta il materiale della barra; la pillola premuta ci posa la sola selezione. Senza
    // ispessimento (che la composizione non può riscrivere esatta) le due strade danno lo stesso grigio.
    val flat = base.copy(
      lightVeil = base.lightVeil.copy(adapt = 0f),
      darkVeil = base.darkVeil.copy(adapt = 0f),
      darkLargeVeil = base.darkLargeVeil.copy(adapt = 0f),
    )
    for (dark in listOf(false, true)) {
      val row = glassPuckRowProfile(bar, flat).veil(dark).color
      val pressed = glassPuckOverRowProfile(bar, flat).veil(dark).color
      val rest = glassPuckProfile(bar, flat).veil(dark).color
      for (b in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
        assertEquals("fondo $b", over(b, rest), over(over(b, row), pressed), 2 * eightBit)
      }
    }
  }

  @Test
  fun thePressedPuckNeitherBlursNorSaturatesAgainAndKeepsTheCapture() {
    for (dark in listOf(false, true)) {
      val rest = recipe(glassPuckProfile(bar, base), 88f, 54f, dark)
      val pressed = recipe(glassPuckOverRowProfile(bar, base), 88f, 54f, dark)
      assertEquals(1f, pressed.mix, 0f)
      assertEquals(1f, pressed.saturation, 0f)
      // Il raggio resta: il margine della cattura non cambia fra riposo e pressione.
      assertEquals(rest.paddingDp, pressed.paddingDp, 0f)
      assertEquals(rest.blurDp, pressed.blurDp, 0f)
      // La sagoma è la stessa: lente, bordo, ombra.
      assertEquals(rest.thicknessDp, pressed.thicknessDp, 0f)
      assertEquals(rest.refractionDp, pressed.refractionDp, 0f)
      assertEquals(rest.rimDark, pressed.rimDark)
      assertEquals(rest.reflexStrength, pressed.reflexStrength, 0f)
      assertEquals(rest.shadowAmbientAlpha, pressed.shadowAmbientAlpha, 0f)
    }
  }

  @Test
  fun theRestingPuckFrostsExactlyLikeTheBarItCovers() {
    for (dark in listOf(false, true)) {
      val barRecipe = recipe(base, 360f, bar, dark)
      val puck = recipe(glassPuckProfile(bar, base), 88f, 54f, dark)
      assertEquals(barRecipe.blurDp, puck.blurDp, 0f)
      assertEquals(barRecipe.mix, puck.mix, 0f)
      assertEquals(barRecipe.saturation, puck.saturation, 0f)
      assertEquals(barRecipe.rimWidthDp, puck.rimWidthDp, 0f)
      assertEquals(barRecipe.reflexStrength, puck.reflexStrength, 0f)
    }
  }

  @Test
  fun theHiddenRowAndThePuckTakeTheVeilTheBarHasAtItsOwnSize() {
    for (dark in listOf(false, true)) {
      val barVeil = recipe(base, 360f, bar, dark).veilColor
      val row = recipe(glassPuckRowProfile(bar, base), 352f, 54f, dark).veilColor
      assertEquals(barVeil.red, row.red, 1e-5f)
      assertEquals(barVeil.alpha, row.alpha, 1e-5f)
      val puck = recipe(glassPuckProfile(bar, base), 88f, 54f, dark).veilColor
      val expected = glassPuckSelectedVeil(glassPuckBarVeil(base, dark, bar), GlassPuckSelections.Ios27.of(dark)).color
      assertEquals(expected.red, puck.red, 1e-5f)
      assertEquals(expected.alpha, puck.alpha, 1e-5f)
    }
  }

  @Test
  fun theNestingRuleReducesTheShadowOnceAndTheEdgeStays() {
    val puck = glassPuckProfile(bar, base)
    assertEquals(base.lightShadow.ambientAlpha * base.lightNesting.shadow, puck.lightShadow.ambientAlpha, 1e-6f)
    assertEquals(base.darkShadow.contactAlpha * base.darkNesting.shadow, puck.darkShadow.contactAlpha, 1e-6f)
    assertEquals(base.lightShadow.ambientRadius, puck.lightShadow.ambientRadius)
    assertEquals(GlassLiquidNestings.Unchanged, puck.lightNesting)
    assertEquals(GlassLiquidNestings.Unchanged, puck.darkNesting)
    // Se il nodo un giorno la trovasse dentro un vetro, l'ombra non scenderebbe una seconda volta.
    val nested = glassRecipe(GlassRole.Floating, 88f, 54f, false, optics = optics, nested = true, profile = puck)
    val alone = glassRecipe(GlassRole.Floating, 88f, 54f, false, optics = optics, nested = false, profile = puck)
    assertEquals(alone.shadowAmbientAlpha, nested.shadowAmbientAlpha, 0f)
    // Il bordo è quello di serie; del filo resta la frazione della selezione (sul chiaro metà), il riflesso intero.
    val sel = GlassPuckSelections.Ios27
    assertEquals(base.lightEdge.rim.alpha * sel.lightRim, puck.lightEdge.rim.alpha, 1f / 255f)
    assertEquals(base.lightEdge.copy(rim = puck.lightEdge.rim), puck.lightEdge)
    assertEquals(base.darkEdge.rim.alpha * sel.darkRim, puck.darkEdge.rim.alpha, 1f / 255f)
    assertEquals(base.darkEdge.copy(rim = puck.darkEdge.rim), puck.darkEdge)
    assertEquals(base.frost, puck.frost)
    assertEquals(base.lens, puck.lens)
  }

  /**
   * Sotto contrasto di sistema (F1B.5, giro di correzione) la tinta della barra si fa densa e `Fixed`, e col materiale
   * nuovo prende il posto del velo: la pillola rifà **quella** pellicola con sopra la selezione, non il velo del profilo.
   */
  @Test
  fun underSystemContrastThePuckFollowsTheBarFilm() {
    // La tinta della barra com'è in `GlassDefaults.floatingTint()` (il grigio di `glassFilm` senza la quota di palette).
    for (dark in listOf(false, true)) {
      val film = if (dark) Color(0xFF4A4A4E).copy(alpha = 0.48f) else Color(0xFFFAFAFA).copy(alpha = 0.52f)
      val standard = GlassTint(overlay = film, fallback = Color.Gray, hairline = Color.Transparent, mode = GlassTintMode.Neutral)
      // A contrasto standard la barra ha il velo del profilo: niente pellicola, la pillola resta quella di sempre.
      assertEquals(GlassTintMode.Neutral, glassTintLiquidMode(standard.forContrast(FluidSystemContrast.Standard)))
      assertEquals(Color.Unspecified, glassPuckFilm(Color.Unspecified, dark))
      for (contrast in listOf(FluidSystemContrast.Medium, FluidSystemContrast.High)) {
        val bar = standard.forContrast(contrast)
        assertEquals("contrasto $contrast: la barra è una pellicola", GlassTintMode.Fixed, glassTintLiquidMode(bar))
        val puck = glassPuckFilm(bar.overlay, dark)
        val s = GlassPuckSelections.Ios27.of(dark)
        for (page in listOf(0.05f, 0.5f, 0.95f)) {
          // Sopra la pagina: la barra è la sua pellicola; la pillola è la barra con sopra un nero della selezione.
          val barOver = page * (1f - bar.overlay.alpha) + bar.overlay.red * bar.overlay.alpha
          val expected = barOver * (1f - s)
          val puckOver = page * (1f - puck.alpha) + puck.red * puck.alpha
          assertEquals("contrasto $contrast, pagina $page, dark=$dark", expected, puckOver, eightBit)
          assertTrue("la pillola resta più scura della barra", puckOver < barOver)
        }
      }
    }
  }

  @Test
  fun theScenesAreConsistentAndWrittenOnlyWhenAsked() {
    val scenes = PuckScenes.all()
    assertEquals(12, scenes.size)
    for (scene in scenes) {
      @Suppress("UNCHECKED_CAST")
      val elements = scene.json["elements"] as List<Map<String, Any?>>
      assertTrue(scene.path, elements.all { it["kind"] == "material" })
    }
    val dir = GlassSceneOutput.requestedDir(System.getenv(PuckScenes.Variable)) ?: return
    GlassSceneOutput.write(dir, scenes)
  }
}

/**
 * Le scene del banco della pillola: la tab bar (360 × 62 dp, quattro schede da 88) con la pillola sulla prima,
 * su fondo piatto, testo e foto, nei due temi. `puck-<fondo>-<tema>` è la pillola a riposo (un vetro accanto alla
 * barra, che campiona la pagina); `puck-premuta-<fondo>-<tema>` la pillola premuta, figlia della barra: campiona
 * pagina e corpo della barra, che è ciò che fa con la riga nascosta.
 */
internal object PuckScenes {

  const val Variable = "GLASS_PUCK_SCENE_DIR"

  private const val BarX = 20
  private const val BarY = 24
  private const val BarW = 360
  private const val BarH = 62
  private const val Inset = 4
  private const val TabW = 88
  private const val PuckH = BarH - 2 * Inset

  private val Labels = listOf("Home", "Catalogo", "Novità", "Altro")
  private val Icons = listOf("fluid:home.fill|home", "fluid:apps.fill|grid4", "fluid:download.fill|plus", "fluid:favorite.fill|circle")

  private fun ink(dark: Boolean) = if (dark) "#F2F2F5" else "#121214"
  /**
   * L'inchiostro della scheda scelta: `legibleInk` dell'accento di serie del banco contro la pellicola del pannello
   * (`GlassDefaults.floatingTint`, il grigio di `glassFilm` senza la quota di palette), come lo calcola la barra.
   */
  private fun selectedInk(dark: Boolean): String {
    val accent = if (dark) Color(0xFF2ED8A0) else Color(0xFF1F9E6E)
    val film = if (dark) Color(0xFF4A4A4E).copy(alpha = 0.48f) else Color(0xFFFAFAFA).copy(alpha = 0.52f)
    val ink = legibleInk(accent, film, dark)
    fun c(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return String.format(Locale.ROOT, "#%02X%02X%02X", c(ink.red), c(ink.green), c(ink.blue))
  }

  /** Icona ed etichetta della scheda [i], centrate in x = [cx] dell'elemento che le porta (la cima del vetro a [top]). */
  private fun tab(i: Int, cx: Int, top: Int, color: String): List<Map<String, Any?>> = listOf(
    linkedMapOf("kind" to "icon", "icon" to Icons[i], "x" to cx, "y" to top + 6 + 14, "sizeDp" to 28, "color" to color),
    linkedMapOf(
      "kind" to "text", "text" to Labels[i], "x" to cx, "y" to top + 6 + 28 + 12, "sizeDp" to 11, "weight" to 500,
      "align" to "center", "color" to color,
    ),
  )

  private fun background(kind: String, dark: Boolean): Map<String, Any?> = when (kind) {
    "piatto" -> linkedMapOf("kind" to "color", "color" to if (dark) "#0D0D0F" else "#F2F2F7")
    "testo" -> linkedMapOf(
      "kind" to "text",
      "lines" to listOf(
        "Muoversi con i mezzi in Toscana: i bus dove",
        "passano, gli orari della sera e le fermate",
        "che conviene conoscere prima di partire.",
        "Le linee extraurbane cambiano d'estate.",
        "Il biglietto si compra anche a bordo.",
        "Muoversi con i mezzi in Toscana: i bus dove",
      ),
      "fontSizeDp" to 17, "lineHeightDp" to 22, "paddingDp" to 12,
      "color" to if (dark) "#E6E6EA" else "#1A1A1C",
      "background" to if (dark) "#0D0D0F" else "#F2F2F7",
    )
    "foto" -> linkedMapOf(
      "kind" to "synthetic", "scaleDp" to 46, "detail" to 1.0, "contrast" to 1.25,
      "palette" to listOf("#111114", "#F4D21C", "#F06AA8", "#1A1A22", "#35C2D6", "#F4D21C", "#E8651C", "#111114"),
    )
    else -> error(kind)
  }

  private fun recipe(profile: GlassLiquidProfile, w: Int, h: Int, dark: Boolean): GlassRecipe = glassRecipe(
    role = GlassRole.Floating, widthDp = w.toFloat(), heightDp = h.toFloat(), darkSide = dark,
    optics = GlassDefaults.optics(GlassRole.Floating).sanitized(), profile = profile,
  )

  fun scene(kind: String, dark: Boolean, pressed: Boolean): GlassScene {
    val theme = if (dark) "dark" else "light"
    val side = if (dark) "scuro" else "chiaro"
    val barShape = linkedMapOf<String, Any?>(
      "name" to "barra", "x" to BarX, "y" to BarY, "w" to BarW, "h" to BarH, "radius" to "capsule",
      "items" to (1 until 4).flatMap { tab(it, Inset + TabW * it + TabW / 2, Inset, ink(dark)) },
    )
    val barEl = liquidElement(
      barShape, recipe(GlassLiquidProfile.Default, BarW, BarH, dark), null,
      "glassRecipe: ruolo floating, $BarW x $BarH dp, lato $side, profilo di serie (il pannello della barra)",
    )
    val puckItems = tab(0, TabW / 2, 0, selectedInk(dark))
    val elements: List<Map<String, Any?>> = if (pressed) {
      val child = linkedMapOf<String, Any?>(
        "name" to "pillola", "x" to Inset, "y" to Inset, "w" to TabW, "h" to PuckH, "radius" to "capsule",
        "items" to puckItems,
      )
      barEl["children"] = listOf(
        liquidElement(
          child, recipe(glassPuckOverRowProfile(BarH.toFloat()), TabW, PuckH, dark), null,
          "glassRecipe: ruolo floating, $TabW x $PuckH dp, lato $side, glassPuckOverRowProfile($BarH) " +
            "(la pillola premuta sopra la riga nascosta: qui sopra il corpo della barra)",
        ),
      )
      listOf(barEl)
    } else {
      val puck = linkedMapOf<String, Any?>(
        "name" to "pillola", "x" to BarX + Inset, "y" to BarY + Inset, "w" to TabW, "h" to PuckH, "radius" to "capsule",
        "items" to puckItems,
      )
      listOf(
        barEl,
        liquidElement(
          puck, recipe(glassPuckProfile(BarH.toFloat()), TabW, PuckH, dark), null,
          "glassRecipe: ruolo floating, $TabW x $PuckH dp, lato $side, glassPuckProfile($BarH) (la pillola a riposo)",
        ),
      )
    }
    val json = linkedMapOf<String, Any?>(
      "_" to "Scritta dalla ricetta (GlassPuckProfileTest, F1B.4): la tab bar e la sua pillola " +
        (if (pressed) "PREMUTA (figlia della barra: campiona pagina e corpo della barra, come la riga nascosta)"
        else "A RIPOSO (campiona la pagina cruda)") +
        ", fondo $kind, lato $side. Non si modifica a mano: si rigenera.",
      "width" to 400, "height" to 110, "density" to 3.0, "theme" to theme, "palette" to "engine", "corner" to "engine",
      "background" to background(kind, dark),
      "elements" to elements,
      "measure" to listOf(
        linkedMapOf("kind" to "body", "element" to "barra", "label" to "barra fra due schede",
          "window" to listOf(BarX + Inset + TabW * 2 - 6, BarY + 12, 12, 16), "rows" to listOf("corpo", "fondo", "corpo-fondo")),
        linkedMapOf("kind" to "body", "element" to "pillola", "label" to "pillola accanto all'icona",
          "window" to listOf(BarX + Inset + 8, BarY + Inset + 10, 14, 14), "rows" to listOf("corpo", "fondo", "corpo-fondo")),
      ),
    )
    val name = if (pressed) "puck-premuta-$kind-$side.json" else "puck-$kind-$side.json"
    return GlassScene(name, json)
  }

  fun all(): List<GlassScene> = listOf("piatto", "testo", "foto").flatMap { kind ->
    listOf(false, true).flatMap { dark -> listOf(scene(kind, dark, pressed = false), scene(kind, dark, pressed = true)) }
  }
}
