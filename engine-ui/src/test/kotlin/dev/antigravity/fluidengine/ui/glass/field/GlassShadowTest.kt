package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ombra del materiale `Liquid` ([GlassShadowLayer], [GlassShadowProfile], [GlassTravelShadowBlock]).
 *
 * Tre cose si possono provare senza un dispositivo, e sono quelle da cui dipende il resto:
 *  1. **i numeri**: la legge di caduta che riposo e viaggio condividono, il contorno preso dal campo, la
 *     taglia e i raggi del layer con il tetto di 1024 px;
 *  2. **quando le maschere si rifanno**: per sagoma, raggi, scostamento e rapporto fra le due alfa; mai per
 *     colore o alfa, che in F2 una sonda cambierà più volte al secondo;
 *  3. **la lambda di `record` rieseguita** (`ref/regole.md`, «La lambda di `record` dipende solo dal proprio
 *     layer»): dopo che la piattaforma ha buttato la display list, il layer di una lastra ridisegna la sagoma
 *     di quella lastra e non quella dell'ultima che ha registrato. È il contorno fantasma della 3.x, e il
 *     banco è quello di `PhysicsShadowSlotsTest` e `HighlightRingReplayTest`: `GraphicsLayer` veri di Compose
 *     sopra un doppio della piattaforma.
 *
 * Cosa resta al banco e al dispositivo: che le maschere sfocate dalla piattaforma abbiano il profilo che la
 * formula promette, che il filtro di colore del layer arrivi davvero alla GPU, e l'aspetto.
 */
class GlassShadowTest {

  // ----------------------------------------------------------------------------------------------------------
  // La legge di caduta
  // ----------------------------------------------------------------------------------------------------------

  @Test
  fun theSigmaIsThePlatformsAndItsInverseUndoesIt() {
    assertEquals(0.5f, GlassShadowProfile.sigma(0f), 0f)
    assertEquals(0.57735f * 24f + 0.5f, GlassShadowProfile.sigma(24f), 1e-5f)
    assertEquals("un raggio negativo vale zero", 0.5f, GlassShadowProfile.sigma(-3f), 0f)
    for (radius in floatArrayOf(0f, 1.5f, 4.5f, 24f, 102f)) {
      assertEquals(radius, GlassShadowProfile.radiusForSigma(GlassShadowProfile.sigma(radius)), 1e-3f)
    }
    assertEquals("sotto il mezzo pixel non c'è raggio che tenga", 0f, GlassShadowProfile.radiusForSigma(0.2f), 0f)
  }

  @Test
  fun aScaledRadiusKeepsTheShadowAsWideOnScreen() {
    // Il layer a mezza risoluzione, riportato in scala, deve avere la stessa sigma di quello intero: non è
    // `raggio * scala`, perché il mezzo pixel della piattaforma non scala.
    for (radius in floatArrayOf(4.5f, 24f, 102f)) {
      for (scale in floatArrayOf(1f, 0.75f, 0.5f, 0.3f)) {
        val scaled = GlassShadowProfile.scaledRadius(radius, scale)
        assertEquals(
          "raggio $radius a scala $scale",
          GlassShadowProfile.sigma(radius) * scale,
          GlassShadowProfile.sigma(scaled),
          1e-3f,
        )
      }
    }
    assertEquals(24f, GlassShadowProfile.scaledRadius(24f, 1f), 1e-3f)
    // Un contatto sottile in un layer molto rimpicciolito scende sotto il mezzo pixel: niente sfocatura.
    assertEquals(0f, GlassShadowProfile.scaledRadius(1f, 0.2f), 0f)
  }

  @Test
  fun theFallIsABlurredEdge() {
    val sigma = 12f
    assertEquals("sul contorno resta metà", 0.5f, GlassShadowProfile.fall(0f, sigma), 1e-6f)
    var worst = 0.0
    var previous = 1f
    var x = -4f * sigma
    while (x <= 4f * sigma) {
      val got = GlassShadowProfile.fall(x, sigma)
      val want = blurredEdge(x.toDouble() / sigma)
      worst = maxOf(worst, abs(got - want))
      assertTrue("la caduta non scende a x = $x: $previous poi $got", got <= previous + 1e-6f)
      assertEquals(
        "dentro e fuori sono lo specchio l'uno dell'altro, x = $x",
        1f,
        got + GlassShadowProfile.fall(-x, sigma),
        1e-6f,
      )
      previous = got
      x += sigma / 16f
    }
    println("Caduta dell'ombra contro la gaussiana vera: scarto massimo $worst")
    // Abramowitz e Stegun 7.1.27 promette 5e-4 su erf, cioè 2,5e-4 qui; il margine è per i `Float`.
    assertTrue("la caduta dista dalla gaussiana vera di $worst", worst < 4e-4)
    assertTrue(
      "a tre sigma resta più dello 0,2%: il taglio del layer si vedrebbe",
      GlassShadowProfile.fall(GlassShadowReachSigmas * sigma, sigma) < 0.002f,
    )
  }

  @Test
  fun theTravelShadowIsTheTwoMasksAlongAStraightEdge() {
    val contactRadius = 4.5f
    val ambientRadius = 24f
    val slide = 12f
    val share = GlassShadowProfile.contactShare(contactRadius, ambientRadius)
    assertEquals(0.1875f, share, 1e-6f)
    val contactSigma = GlassShadowProfile.sigma(contactRadius)
    val ambientSigma = GlassShadowProfile.sigma(ambientRadius)
    // Sotto il bordo basso di una lastra larga: a `d` pixel dal bordo, il punto spostato indietro di tutto
    // lo scostamento sta a `d - slide`. Il contatto è la maschera stretta scostata della sua quota.
    var d = -8f
    while (d <= 40f) {
      val got = GlassShadowProfile.travel(d, d - slide, contactRadius, ambientRadius, 0.024f, 0.038f, slide)
      val want = 0.024f * GlassShadowProfile.fall(d - share * slide, contactSigma) +
        0.038f * GlassShadowProfile.fall(d - slide, ambientSigma)
      assertEquals("a $d px dal bordo", want, got, 1e-6f)
      d += 0.5f
    }
  }

  @Test
  fun theTravelShadowLeavesEarlyWhereItDoesNotMatter() {
    val reach = GlassShadowProfile.reach(4.5f, 24f, 0.024f, 0.038f, 12f)
    assertEquals(GlassShadowReachSigmas * GlassShadowProfile.sigma(24f) + 12f, reach, 1e-4f)
    // Oltre `reach` è zero, e subito prima è già quasi zero: l'uscita anticipata non fa uno scalino.
    assertEquals(0f, GlassShadowProfile.travel(reach + 0.01f, reach - 12f, 4.5f, 24f, 0.024f, 0.038f, 12f), 0f)
    val justInside = GlassShadowProfile.travel(reach - 0.01f, reach - 12f, 4.5f, 24f, 0.024f, 0.038f, 12f)
    assertTrue("a ridosso dell'uscita l'ombra vale ancora $justInside", justInside < 0.062f * 0.002f)
    // Ben dentro è l'ombra piena, senza valutare il campo; e la somma non supera mai uno.
    assertEquals(0.062f, GlassShadowProfile.travel(-reach - 1f, -reach - 13f, 4.5f, 24f, 0.024f, 0.038f, 12f), 1e-6f)
    assertEquals(1f, GlassShadowProfile.travel(-500f, -500f, 4.5f, 24f, 0.8f, 0.9f, 12f), 0f)
    // Senza alfa non c'è ombra, qualunque cosa dicano gli altri uniform (non impostati valgono zero).
    assertEquals(0f, GlassShadowProfile.travel(-10f, -10f, 4.5f, 24f, 0f, 0f, 12f), 0f)
    // Una componente spenta non allarga la fascia in cui lo shader lavora.
    assertEquals(
      GlassShadowReachSigmas * GlassShadowProfile.sigma(4.5f) + 0.1875f * 12f,
      GlassShadowProfile.reach(4.5f, 24f, 0.024f, 0f, 12f),
      1e-4f,
    )
    // Senza ambiente (raggio zero) il contatto prende tutto lo scostamento.
    assertEquals(1f, GlassShadowProfile.contactShare(4f, 0f), 0f)
    assertEquals("la quota non supera mai uno", 1f, GlassShadowProfile.contactShare(30f, 24f), 0f)
  }

  // ----------------------------------------------------------------------------------------------------------
  // Il blocco AGSL e la sua sonda
  // ----------------------------------------------------------------------------------------------------------

  @Test
  fun theShaderBlockSaysWhatTheMirrorSays() {
    // Da F1B.2 l'ombra in viaggio è in tre parti: la legge, la maschera del nucleo, la funzione che il corpo chiama.
    // Il contratto si legge sul loro insieme, nell'ordine in cui il corpo le include.
    val block = listOf(GlassTravelShadowBlock, GlassShadowMask8, GlassTravelShadowFunctionBlock).joinToString("\n")
    assertFalse("resta un `$` di un template Kotlin non risolto", '$' in block)
    assertFalse("un blocco condiviso non ha `main`", Regex("""\bmain\s*\(""").containsMatchIn(block))
    assertEquals("graffe spaiate", block.count { it == '{' }, block.count { it == '}' })
    assertEquals("parentesi spaiate", block.count { it == '(' }, block.count { it == ')' })
    assertFalse("un letterale con il suffisso `f` di Kotlin", Regex("""\d\.?\d*f\b""").containsMatchIn(block))
    // Il contratto di F1A.1, che il corpo include: gli uniform e la firma non cambiano.
    for (declaration in listOf(
      "uniform float2 shadowOffset;",
      "uniform float2 shadowRadii;",
      "uniform float2 shadowAlphas;",
      "layout(color) uniform half4 shadowColor;",
      "half4 glassTravelShadow(float2 p, float d) {",
    )) {
      assertTrue("manca `$declaration`", declaration in block)
    }
    // I numeri arrivano allo shader come letterali con la virgola (un `3` sarebbe un intero), e sono gli
    // stessi dello specchio.
    assertTrue("0.57735 * max(radius, 0.0) + 0.5" in block)
    assertTrue("3.0 * contactSigma + share * slide" in block)
    assertTrue("3.0 * ambientSigma + slide" in block)
    assertEquals(0.57735f, GlassShadowSigmaPerRadius, 0f)
    assertEquals(0.5f, GlassShadowSigmaFloor, 0f)
    assertEquals(3f, GlassShadowReachSigmas, 0f)
    // Nessun nome che sia una parola di GLSL o di SkSL: `flat` ha tenuto mezzo engine in ripiego.
    val code = block.lines().joinToString("\n") { it.substringBefore("//") }
    val forbidden = Regex("""\b(flat|smooth|sample|filter|input|output|noperspective|centroid|common|active)\b""")
    assertTrue("il blocco usa `${forbidden.find(code)?.value}`, una parola riservata", forbidden.find(code) == null)
  }

  @Test
  fun theBenchProbeCarriesTheBlockVerbatim() {
    // Il blocco è senza `main`: sul banco lo rende da solo una sonda che ne tiene una copia, con il nucleo a
    // otto. La sorgente è il Kotlin: se cambia, la copia va riscritta
    // (artifacts/liquid-glass/bench/f1a-4/genera_sonda.py), o le scene prova-ombra-viaggio-* mostrerebbero
    // un'ombra che l'engine non ha più.
    val file = File(benchFolder("shaders"), "prova-ombra-viaggio.agsl")
    assertTrue("Manca la sonda ${file.path}", file.isFile)
    val text = file.readText().replace("\r\n", "\n")
    val copies = listOf(
      "GlassFieldKernel8" to GlassFieldKernel8,
      "GlassTravelShadowBlock" to GlassTravelShadowBlock,
      "GlassShadowMask8" to GlassShadowMask8,
      "GlassTravelShadowFunctionBlock" to GlassTravelShadowFunctionBlock,
    )
    for ((name, source) in copies) {
      val open = "// >>> $name\n"
      val close = "\n// <<< $name"
      val from = text.indexOf(open)
      val to = text.indexOf(close)
      assertTrue("${file.name} non ha i due segni `>>> $name` e `<<< $name`", from >= 0 && to > from)
      assertEquals(
        "${file.name} non contiene più $name così com'è nel Kotlin: riscrivi la copia fra i due segni",
        source.replace("\r\n", "\n").trim('\n'),
        text.substring(from + open.length, to),
      )
    }
  }

  // ----------------------------------------------------------------------------------------------------------
  // La ricetta
  // ----------------------------------------------------------------------------------------------------------

  @Test
  fun scalingTheStrengthKeepsTheTwoMasksInProportion() {
    val spec = GlassShadowDefaults.Ios27
    assertSame("a forza piena è lo stesso oggetto: nessuna allocazione per fotogramma", spec, spec.scaled(1f))
    val half = spec.scaled(0.5f)
    assertEquals(spec.contactAlpha * 0.5f, half.contactAlpha, 1e-7f)
    assertEquals(spec.ambientAlpha * 0.5f, half.ambientAlpha, 1e-7f)
    assertEquals(spec.contactRadius, half.contactRadius)
    assertEquals(spec.ambientRadius, half.ambientRadius)
    assertEquals(spec.offset, half.offset)
    assertEquals(0f, spec.scaled(-1f).ambientAlpha, 0f)
    assertEquals(0f, spec.scaled(Float.NaN).contactAlpha, 0f)
  }

  @Test
  fun aLargerPaneGrowsItsShadowByTheSizeGain() {
    val spec = GlassShadowDefaults.Ios27
    assertSame("un tasto non cresce", spec, spec.grown(0f, GlassShadowDefaults.GrowthTwoSteps))
    assertSame("«come una barra» non cresce", spec, spec.grown(1f, GlassShadowDefaults.GrowthNone))
    val full = spec.grown(1f, GlassShadowDefaults.GrowthStep)
    assertEquals(spec.contactRadius.value * 2f, full.contactRadius.value, 1e-4f)
    assertEquals(spec.ambientRadius.value * 2f, full.ambientRadius.value, 1e-4f)
    assertEquals("lo scostamento cresce con i raggi", spec.offset.y.value * 2f, full.offset.y.value, 1e-4f)
    assertEquals(spec.contactAlpha * 1.5f, full.contactAlpha, 1e-6f)
    assertEquals(spec.ambientAlpha * 1.5f, full.ambientAlpha, 1e-6f)
    // A metà guadagno, metà della crescita; oltre uno non si va.
    val half = spec.grown(0.5f, GlassShadowDefaults.GrowthTwoSteps)
    assertEquals(spec.ambientRadius.value * 2f, half.ambientRadius.value, 1e-4f)
    assertEquals(spec.ambientAlpha * 1.5f, half.ambientAlpha, 1e-6f)
    assertEquals(full, spec.grown(7f, GlassShadowDefaults.GrowthStep))
    // La crescita non cambia il rapporto fra i due raggi: il contatto tiene la sua quota di scostamento.
    assertEquals(
      GlassShadowProfile.contactShare(spec.contactRadius.value, spec.ambientRadius.value),
      GlassShadowProfile.contactShare(full.contactRadius.value, full.ambientRadius.value),
      1e-6f,
    )
  }

  @Test
  fun theVariantsOfTheTasteBoardsAreTheOnesTheKDocSays() {
    val standard = GlassShadowDefaults.Ios27
    assertTrue("il contatto è più stretto dell'ambiente", standard.contactRadius < standard.ambientRadius)
    assertTrue("l'ambiente scende", standard.offset.y > 0.dp && standard.offset.x == 0.dp)
    assertEquals(Color.Black, standard.color)
    // Corta: a tre sigma più lo scostamento l'ombra di serie è finita entro un quarto di barra da 62 dp.
    val reachDp = GlassShadowReachSigmas * 0.57735f * standard.ambientRadius.value + standard.offset.y.value
    assertTrue("l'ombra di serie arriva a $reachDp dp: non è più corta", reachDp < 62f * 0.3f)

    val wide = GlassShadowDefaults.Ios26
    assertEquals("iOS 26 non ha una riga di contatto", 0f, wide.contactAlpha, 0f)
    assertTrue(wide.ambientRadius > standard.ambientRadius * 3f)

    val today = GlassShadowDefaults.today(20.dp, 0.7f, darkSide = false)
    assertEquals(0f, today.contactAlpha, 0f)
    assertEquals(20.dp, today.ambientRadius)
    assertEquals("`Shadow.offset`: un sesto del raggio", 20f / 6f, today.offset.y.value, 1e-5f)
    assertEquals(0.7f, today.ambientAlpha, 0f)
    // Un colore sRGB tiene l'alfa in 8 bit: 0,16 diventa 41/255 e 0,10 diventa 26/255.
    assertEquals("nero al 16% sul chiaro", 0.16f, today.color.alpha, 3e-3f)
    val dark = GlassShadowDefaults.today(20.dp, 0.7f, darkSide = true)
    assertEquals("nero al 10% sullo scuro", 0.10f, dark.color.alpha, 3e-3f)
  }

  @Test
  fun theBenchSceneStillCarriesTheDefaults() {
    // Le scene prova-ombra-* sono scritte a mano dai numeri di GlassShadowDefaults (non dalla ricetta, che
    // nasce in F1A.5): se i valori di serie cambiano e la scena no, le misure del rapporto parlano di
    // un'altra ombra.
    val file = File(benchFolder("scenes/f1a"), "prova-ombra-pagina-chiara.json")
    assertTrue("Manca la scena ${file.path}", file.isFile)
    val text = file.readText()
    val tasto = text.indexOf("\"name\": \"tasto\"")
    assertTrue("la scena non ha più il tasto", tasto >= 0)
    val shadow = Regex(""""shadow":\s*\[(.*?)\]\s*,\s*"capture"""", RegexOption.DOT_MATCHES_ALL).find(text, tasto)
      ?: throw AssertionError("il tasto della scena non ha più le maschere dell'ombra")
    val masks = shadow.groupValues[1]
    val radii = Regex(""""radiusDp":\s*([\d.]+)""").findAll(masks).map { it.groupValues[1].toFloat() }.toList()
    val drops = Regex(""""offsetDp":\s*\[\s*[\d.]+\s*,\s*([\d.]+)\s*\]""").findAll(masks)
      .map { it.groupValues[1].toFloat() }.toList()
    val alphas = Regex(""""alpha":\s*([\d.]+)""").findAll(masks).map { it.groupValues[1].toFloat() }.toList()
    val spec = GlassShadowDefaults.Ios27
    val share = GlassShadowProfile.contactShare(spec.contactRadius.value, spec.ambientRadius.value)
    assertEquals("due maschere: contatto e ambiente", 2, radii.size)
    assertEquals(spec.contactRadius.value, radii[0], 1e-4f)
    assertEquals(spec.ambientRadius.value, radii[1], 1e-4f)
    assertEquals("il contatto scende della sua quota", spec.offset.y.value * share, drops[0], 1e-4f)
    assertEquals(spec.offset.y.value, drops[1], 1e-4f)
    assertEquals(spec.contactAlpha, alphas[0], 1e-5f)
    assertEquals(spec.ambientAlpha, alphas[1], 1e-5f)
  }

  // ----------------------------------------------------------------------------------------------------------
  // Il contorno: la maschera parte dalla sagoma del campo
  // ----------------------------------------------------------------------------------------------------------

  @Test
  fun theContourSitsOnTheZeroOfTheField() {
    val sample = GlassFieldSample()
    val points = FloatArray(GlassShadowContour.MaxFloats)
    val shapes = listOf(
      // mezza larghezza, mezza altezza, raggi (alto-sx, alto-dx, basso-dx, basso-sx), raccordo
      Shape(360f, 360f, floatArrayOf(114f, 114f, 114f, 114f), 0.6f, "pannello 240 dp, raggio 38 dp, densità 3"),
      Shape(255f, 288f, floatArrayOf(84f, 84f, 84f, 84f), 0.6f, "menu 170 x 192 dp, raggio 28 dp"),
      Shape(360f, 264f, floatArrayOf(114f, 66f, 36f, 0f), 0.6f, "quattro raggi diversi, uno nullo"),
      Shape(234f, 84f, floatArrayOf(84f, 84f, 84f, 84f), 0.6f, "capsula: il raccordo non ha lato da spendere"),
      Shape(66f, 66f, floatArrayOf(66f, 66f, 66f, 66f), 0f, "tasto tondo"),
      Shape(300f, 200f, floatArrayOf(90f, 90f, 90f, 90f), 0.3f, "raccordo a metà"),
      Shape(700f, 480f, floatArrayOf(400f, 400f, 400f, 400f), 0.6f, "un raggio che il lato corto riduce"),
    )
    for (shape in shapes) {
      val count =
        GlassShadowContour.slab(500f, 400f, shape.halfWidth, shape.halfHeight, shape.radii, 0, shape.smoothing, points)
      assertTrue("${shape.name}: $count punti non stanno nell'appoggio", count * 2 <= GlassShadowContour.MaxFloats)
      var onContour = 0f
      var chord = 0f
      var area = 0.0
      for (i in 0 until count) {
        val x = points[i * 2]
        val y = points[i * 2 + 1]
        val next = (i + 1) % count
        val nextX = points[next * 2]
        val nextY = points[next * 2 + 1]
        area += x.toDouble() * nextY - nextX.toDouble() * y
        GlassFieldGeometry.slab(
          x - 500f,
          y - 400f,
          shape.halfWidth,
          shape.halfHeight,
          shape.radii,
          0,
          shape.smoothing,
          sample,
        )
        onContour = maxOf(onContour, abs(sample.distance))
        // A metà di ogni segmento la spezzata si è staccata dalla curva di quanto vale lì il campo.
        GlassFieldGeometry.slab(
          (x + nextX) / 2f - 500f,
          (y + nextY) / 2f - 400f,
          shape.halfWidth,
          shape.halfHeight,
          shape.radii,
          0,
          shape.smoothing,
          sample,
        )
        chord = maxOf(chord, abs(sample.distance))
      }
      println("Contorno, ${shape.name}: $count punti, sul contorno entro $onContour px, corde entro $chord px")
      // I vertici stanno sul contorno a meno dei `Float` (un `pow` a 32 bit su coordinate di centinaia di pixel).
      assertTrue("${shape.name}: un vertice dista $onContour px dal contorno del campo", onContour < 0.02f)
      // La regola dei segmenti tiene la freccia a 0,035-0,04 px (studio_ombra.py); la soglia ha margine.
      assertTrue("${shape.name}: la spezzata si stacca dalla curva di $chord px", chord < 0.07f)
      assertTrue("${shape.name}: il contorno non gira in senso orario", area > 0.0)
      // L'area: quella di un rettangolo meno gli angoli, quindi fra metà e tutto il rettangolo.
      val box = 4.0 * shape.halfWidth * shape.halfHeight
      val inside = area / 2.0
      assertTrue("${shape.name}: area $inside contro un riquadro di $box", inside > box * 0.5 && inside <= box)
    }
  }

  @Test
  fun circularPiecesStayRoundRects() {
    // Dove l'angolo è un arco di cerchio il pezzo resta un `RoundRect`, che la piattaforma sfoca senza una
    // maschera su CPU: sono tutti i tasti e tutte le capsule.
    val capsule = floatArrayOf(84f, 84f, 84f, 84f)
    assertTrue("una capsula", GlassShadowContour.isCircular(234f, 84f, capsule, 0, 0.6f))
    assertTrue("un tondo", GlassShadowContour.isCircular(66f, 66f, floatArrayOf(66f, 66f, 66f, 66f), 0, 0.6f))
    assertTrue("raccordo zero", GlassShadowContour.isCircular(360f, 360f, floatArrayOf(114f, 114f, 114f, 114f), 0, 0f))
    assertTrue("senza raggi", GlassShadowContour.isCircular(360f, 360f, FloatArray(4), 0, 0.6f))
    val panel = floatArrayOf(114f, 114f, 114f, 114f)
    assertFalse("un pannello raccordato", GlassShadowContour.isCircular(360f, 360f, panel, 0, 0.6f))
    val oneCorner = floatArrayOf(0f, 0f, 60f, 0f)
    assertFalse("un solo angolo raccordato basta", GlassShadowContour.isCircular(360f, 360f, oneCorner, 0, 0.6f))
    // La regola dei segmenti: cresce con la radice del raggio, fra un minimo e un tetto.
    assertEquals(GlassShadowContour.MinSegments, GlassShadowContour.segments(0f))
    assertEquals(GlassShadowContour.MinSegments, GlassShadowContour.segments(2f))
    assertEquals(36, GlassShadowContour.segments(136.4f))
    assertEquals(GlassShadowContour.MaxSegments, GlassShadowContour.segments(5000f))
  }

  // ----------------------------------------------------------------------------------------------------------
  // I numeri del layer
  // ----------------------------------------------------------------------------------------------------------

  @Test
  fun theLayerIsAsLargeAsTheTwoMasksAndSitsOnWholePixels() {
    // Un tasto da 44 dp a densità 3 con l'ombra di serie: contatto 4,5 px, ambiente 24 px, 12 px in giù.
    val pieces = GlassShadowPieces().setSlab(132f, 132f, 66f, 66f, 66f, 66f, 0f)
    val metrics = GlassShadowMetrics()
    metrics.resolve(
      pieces,
      contactRadius = 4.5f,
      ambientRadius = 24f,
      offsetX = 0f,
      offsetY = 12f,
      contact = true,
      ambient = true,
    )
    val ambientReach = GlassShadowReachSigmas * GlassShadowProfile.sigma(24f) // 43,07 px
    assertEquals(43.07f, ambientReach, 0.01f)
    // Ai lati comanda l'ambiente (tre sigma); sopra gli si toglie lo scostamento, sotto gli si aggiunge.
    assertEquals(-44f, metrics.originX, 0f)
    assertEquals(-32f, metrics.originY, 0f)
    assertEquals(220, metrics.layerWidth) // da -44 a ceil(132 + 43,07) = 176
    assertEquals(220, metrics.layerHeight) // da -32 a ceil(132 + 43,07 + 12) = 188
    assertEquals(1f, metrics.resolution, 0f)
    assertEquals(4.5f, metrics.contactBlur, 1e-3f)
    assertEquals(24f, metrics.ambientBlur, 1e-3f)
    assertEquals(0f, metrics.contactOffsetX, 0f)
    assertEquals("il contatto scende della sua quota: 12 px per 4,5 / 24", 2.25f, metrics.contactOffsetY, 1e-5f)
    assertEquals(12f, metrics.ambientOffsetY, 0f)

    // Una componente spenta non allarga il layer: senza ambiente restano i tre sigma del contatto.
    metrics.resolve(pieces, 4.5f, 24f, 0f, 12f, contact = true, ambient = false)
    val contactReach = GlassShadowReachSigmas * GlassShadowProfile.sigma(4.5f) // 9,29 px
    assertEquals(kotlin.math.floor(-contactReach), metrics.originX, 0f)
    assertEquals(kotlin.math.floor(-(contactReach - 2.25f)), metrics.originY, 0f)
    assertTrue(metrics.layerWidth < 160)

    // Uno scostamento più largo della maschera non taglia la sagoma dal layer: lo scavo la vuole tutta.
    metrics.resolve(pieces, 0f, 3f, 40f, 0f, contact = false, ambient = true)
    assertTrue("il layer comincia dopo il bordo sinistro della lastra", metrics.originX <= 0f)
  }

  @Test
  fun aLargePaneRecordsItsShadowSmaller() {
    // Un foglio di 2000 x 1200 px: il layer intero sarebbe 2087 px di lato lungo, il tetto è 1024.
    val pieces = GlassShadowPieces().setSlab(2000f, 1200f, 114f, 114f, 114f, 114f, 0.6f)
    val metrics = GlassShadowMetrics()
    metrics.resolve(pieces, 4.5f, 24f, 0f, 12f, contact = true, ambient = true)
    val fullWidth = 2000f + 44f + 44f
    assertEquals(1024f / fullWidth, metrics.resolution, 1e-5f)
    // 2088 per 1024 / 2088 in `Float` può uscire un'unghia sopra 1024, e il soffitto ne fa 1025.
    assertTrue("il layer è largo ${metrics.layerWidth} px", metrics.layerWidth in 1024..1025)
    assertTrue(metrics.layerHeight in 600..640)
    // Riportate in scala, le due maschere sono larghe come a piena risoluzione.
    val res = metrics.resolution
    assertEquals(GlassShadowProfile.sigma(24f) * res, GlassShadowProfile.sigma(metrics.ambientBlur), 1e-3f)
    assertEquals(GlassShadowProfile.sigma(4.5f) * res, GlassShadowProfile.sigma(metrics.contactBlur), 1e-3f)
    assertEquals(12f * metrics.resolution, metrics.ambientOffsetY, 1e-5f)
    // Con un altro tetto, un'altra risoluzione: il numero è un parametro, non una costante sepolta.
    metrics.resolve(pieces, 4.5f, 24f, 0f, 12f, contact = true, ambient = true, maxPx = 4096f)
    assertEquals(1f, metrics.resolution, 0f)
  }

  @Test
  fun aClusterIsTheUnionOfItsPieces() {
    val pieces = GlassShadowPieces()
      .add(100f, 50f, 50f, 50f, 50f, 50f, 50f, 50f, 0f)
      .add(300f, 80f, 120f, 40f, 40f, 40f, 40f, 40f, 0f)
    assertEquals(2, pieces.count)
    val metrics = GlassShadowMetrics()
    metrics.resolve(pieces, 0f, 0f, 0f, 0f, contact = false, ambient = false)
    assertEquals(50f, metrics.originX, 0f)
    assertEquals(0f, metrics.originY, 0f)
    assertEquals(370, metrics.layerWidth)
    assertEquals(120, metrics.layerHeight)

    val same = GlassShadowPieces().apply { copyFrom(pieces) }
    assertTrue(pieces.sameAs(same))
    same.clear()
    same.add(100f, 50f, 50f, 50f, 50f, 50f, 50f, 50f, 0f).add(300f, 80f, 120f, 40f, 40f, 40f, 40f, 40f, 0.6f)
    assertFalse("un raccordo diverso è un'altra sagoma", pieces.sameAs(same))
    assertFalse(GlassShadowPieces().setSlab(10f, 10f, 2f, 2f, 2f, 2f, 0f).isEmpty())
    assertTrue(GlassShadowPieces().setSlab(0f, 10f, 2f, 2f, 2f, 2f, 0f).isEmpty())
    assertTrue(GlassShadowPieces().isEmpty())
    // Oltre il tetto dei pezzi del campo non si aggiunge niente: lo shader non li leggerebbe.
    val full = GlassShadowPieces()
    repeat(GlassFieldMaxPieces + 3) { full.add(10f * it, 0f, 4f, 4f, 1f, 1f, 1f, 1f, 0f) }
    assertEquals(GlassFieldMaxPieces, full.count)
  }

  // ----------------------------------------------------------------------------------------------------------
  // Quando le maschere si rifanno
  // ----------------------------------------------------------------------------------------------------------

  @Test
  fun aStillPaneRecordsItsShadowOnce() {
    val bench = ShadowBench(hardware = true)
    val pane = bench.pane()
    val button = GlassShadowPieces().setSlab(132f, 132f, 66f, 66f, 66f, 66f, 0f)
    repeat(10) { bench.frame(pane, button, GlassShadowDefaults.Ios27) }
    assertEquals("dieci fotogrammi fermi, una registrazione", 1, pane.shadow.recordCount)
    assertEquals(1, pane.control.records)
    assertEquals("contatto, ambiente e scavo: tre disegni della sagoma, una volta", 3, bench.log.drawn.size)
    assertEquals(
      "le due maschere e lo scavo disegnano la stessa sagoma",
      1,
      bench.log.drawn.distinctBy { System.identityHashCode(it) }.size,
    )
    assertEquals(IntSizeOf(220, 220), IntSizeOf(pane.layer.size.width, pane.layer.size.height))
  }

  @Test
  fun alphaAndColourNeverRecordAgain() {
    val bench = ShadowBench(hardware = true)
    val pane = bench.pane()
    val button = GlassShadowPieces().setSlab(132f, 132f, 66f, 66f, 66f, 66f, 0f)
    val spec = GlassShadowDefaults.Ios27
    bench.frame(pane, button, spec)
    assertEquals(spec.contactAlpha + spec.ambientAlpha, pane.layer.alpha, 1e-6f)
    val black = pane.layer.colorFilter

    // La forza che cala e risale (comparsa, `intensity`, qualità, il rinforzo della sonda): solo l'alfa del layer.
    for (step in 1..40) {
      val amount = 0.05f + step * 0.045f
      bench.frame(pane, button, spec.scaled(amount))
      assertEquals(
        "forza $amount",
        ((spec.contactAlpha + spec.ambientAlpha) * amount).coerceAtMost(1f),
        pane.layer.alpha,
        1e-5f,
      )
    }
    assertSame("lo stesso colore tiene lo stesso filtro: niente oggetti nuovi a ogni fotogramma", black,
      pane.layer.colorFilter)

    // Il colore: un'altra tinta, un nero meno pieno. Cambia il filtro (o l'alfa), non le maschere.
    bench.frame(pane, button, spec.copy(color = Color(0xFF1A2B6C)))
    assertNotSame("un colore nuovo vuole un filtro nuovo", black, pane.layer.colorFilter)
    val faded = Color.Black.copy(alpha = 0.5f)
    bench.frame(pane, button, spec.copy(color = faded))
    assertEquals(
      "l'alfa del colore finisce nell'alfa del layer",
      (spec.contactAlpha + spec.ambientAlpha) * faded.alpha,
      pane.layer.alpha,
      1e-6f,
    )

    assertEquals("alfa e colore non rifanno le maschere", 1, pane.shadow.recordCount)
    assertEquals(1, pane.control.records)
    assertEquals(3, bench.log.drawn.size)
  }

  @Test
  fun sizeShapeAndRadiiRecordAgain() {
    val bench = ShadowBench(hardware = true)
    val pane = bench.pane()
    val spec = GlassShadowDefaults.Ios27
    val pieces = GlassShadowPieces().setSlab(720f, 720f, 114f, 114f, 114f, 114f, 0.6f)
    bench.frame(pane, pieces, spec)
    var expected = 1
    assertEquals(expected, pane.shadow.recordCount)

    fun changed(what: String, pieces: GlassShadowPieces, spec: GlassShadowSpec) {
      bench.frame(pane, pieces, spec)
      expected++
      assertEquals("$what deve rifare le maschere", expected, pane.shadow.recordCount)
      bench.frame(pane, pieces, spec)
      assertEquals("$what: il fotogramma dopo non rifà niente", expected, pane.shadow.recordCount)
    }

    changed("la taglia", pieces.setSlab(721f, 720f, 114f, 114f, 114f, 114f, 0.6f), spec)
    changed("un raggio della forma", pieces.setSlab(721f, 720f, 114f, 90f, 114f, 114f, 0.6f), spec)
    changed("il raccordo della forma", pieces.setSlab(721f, 720f, 114f, 90f, 114f, 114f, 0f), spec)
    changed("il raggio dell'ambiente", pieces, spec.copy(ambientRadius = 9.dp))
    changed("il raggio del contatto", pieces, spec.copy(ambientRadius = 9.dp, contactRadius = 2.dp))
    val moved = spec.copy(ambientRadius = 9.dp, contactRadius = 2.dp, offset = DpOffset(1.dp, 5.dp))
    changed("lo scostamento", pieces, moved)
    // Il rapporto fra le due alfa è nella registrazione: un layer ha un'alfa sola.
    changed("il rapporto fra le due alfa", pieces, moved.copy(contactAlpha = moved.contactAlpha * 2f))
    changed("il contatto spento", pieces, moved.copy(contactAlpha = 0f))
    // Tornare a una ricetta già vista è una ricetta nuova: non c'è una cache dietro, c'è un layer.
    changed("il ritorno alla ricetta di serie", pieces, spec)
    assertEquals(expected, pane.control.records)
  }

  @Test
  fun aShadowTooFaintToSeeIsNeitherRecordedNorDrawn() {
    val bench = ShadowBench(hardware = true)
    val pane = bench.pane()
    val button = GlassShadowPieces().setSlab(132f, 132f, 66f, 66f, 66f, 66f, 0f)
    bench.frame(pane, button, GlassShadowDefaults.Ios27.scaled(0f))
    bench.frame(pane, button, GlassShadowDefaults.Ios27.scaled(0.01f))
    bench.frame(pane, button, GlassShadowDefaults.Ios27.copy(color = Color.Transparent))
    bench.frame(pane, GlassShadowPieces(), GlassShadowDefaults.Ios27)
    bench.frame(pane, button, GlassShadowDefaults.Ios27.copy(contactAlpha = Float.NaN, ambientAlpha = Float.NaN))
    assertEquals(0, pane.shadow.recordCount)
    assertTrue(bench.log.drawn.isEmpty())
    // E quando compare, compare con le maschere giuste: la comparsa non le rifà a ogni fotogramma.
    for (step in 1..20) bench.frame(pane, button, GlassShadowDefaults.Ios27.scaled(step / 20f))
    assertEquals("la comparsa registra una volta", 1, pane.shadow.recordCount)
  }

  @Test
  fun onlyTheComponentsThatExistAreDrawn() {
    val bench = ShadowBench(hardware = true)
    val pane = bench.pane()
    val capsule = GlassShadowPieces().setSlab(468f, 168f, 84f, 84f, 84f, 84f, 0f)
    bench.frame(pane, capsule, GlassShadowDefaults.Ios26)
    assertEquals("una maschera e lo scavo", 2, bench.log.drawn.size)
    bench.log.drawn.clear()
    val today = GlassShadowDefaults.today(20.dp, 0.7f, darkSide = false)
    bench.frame(pane, capsule, today)
    assertEquals("l'ombra di oggi: una maschera e lo scavo", 2, bench.log.drawn.size)
    assertEquals("il nero del lato del tema per il peso del ruolo", 0.7f * today.color.alpha, pane.layer.alpha, 1e-6f)
  }

  // ----------------------------------------------------------------------------------------------------------
  // La lambda di `record` rieseguita: il contorno fantasma
  // ----------------------------------------------------------------------------------------------------------

  @Test
  fun aReplayedShadowRedrawsItsOwnSilhouetteNotTheLastOneRecorded() {
    // In modo software `GraphicsLayer.draw` riesegue la lambda registrata a ogni disegno: è la riesecuzione
    // tardiva che il dispositivo fa dopo aver buttato una display list, solo che qui avviene sempre.
    val bench = ShadowBench(hardware = false)
    val button = bench.pane()
    val panel = bench.pane()
    val round = GlassShadowPieces().setSlab(132f, 132f, 66f, 66f, 66f, 66f, 0f)
    val square = GlassShadowPieces().setSlab(720f, 720f, 114f, 114f, 114f, 114f, 0.6f)

    val firstButton = bench.frame(button, round, GlassShadowDefaults.Ios27)
    val onPanel = bench.frame(panel, square, GlassShadowDefaults.Ios27)
    // Il tasto non ha niente da registrare di nuovo: la sua lambda si riesegue e basta.
    val backOnButton = bench.frame(button, round, GlassShadowDefaults.Ios27)

    assertFalse("l'ombra del tasto non ha disegnato niente", firstButton.isEmpty())
    assertFalse("l'ombra del pannello non ha disegnato niente", onPanel.isEmpty())
    assertEquals("contatto, ambiente e scavo", 3, backOnButton.size)
    assertEquals("il tasto non ha registrato di nuovo", 1, button.shadow.recordCount)
    assertTrue(
      "rieseguita, l'ombra del tasto ridisegna il Path registrato dal pannello: è il contorno fantasma " +
        "(lambda di record che legge una sagoma condivisa fra le lastre)",
      backOnButton.none { drawn -> onPanel.any { it === drawn } },
    )
    assertTrue(
      "rieseguita, l'ombra del tasto deve ridisegnare la sagoma che aveva alla prima registrazione",
      backOnButton.all { drawn -> firstButton.any { it === drawn } },
    )
    // E lo stesso vale per i pennelli: il raggio di sfocatura del pannello non deve finire sul tasto.
    val buttonPaints = bench.log.paintsOf(firstButton)
    val panelPaints = bench.log.paintsOf(onPanel)
    assertTrue(
      "le due lastre disegnano con gli stessi pennelli: rieseguita, una prende la sfocatura dell'altra",
      buttonPaints.none { paint -> panelPaints.any { it === paint } },
    )
  }

  @Test
  fun afterThePlatformDropsTheDisplayListTheShadowComesBackOnceAndRight() {
    val bench = ShadowBench(hardware = true)
    val button = bench.pane()
    val panel = bench.pane()
    val round = GlassShadowPieces().setSlab(132f, 132f, 66f, 66f, 66f, 66f, 0f)
    val square = GlassShadowPieces().setSlab(720f, 720f, 114f, 114f, 114f, 114f, 0.6f)

    val firstButton = bench.frame(button, round, GlassShadowDefaults.Ios27)
    bench.frame(panel, square, GlassShadowDefaults.Ios27)
    val settled = bench.log.drawn.size
    assertEquals("una registrazione per lastra: tre disegni ciascuna", 6, settled)
    repeat(5) { bench.frame(button, round, GlassShadowDefaults.Ios27) }
    assertEquals("fermo, il tasto non esegue più la sua lambda", settled, bench.log.drawn.size)

    // Il tasto resta un fotogramma senza disegno, o l'app torna dallo sfondo: HWUI butta la display list.
    button.control.hasDisplayList = false
    val replayed = bench.frame(button, round, GlassShadowDefaults.Ios27)
    repeat(5) { bench.frame(button, round, GlassShadowDefaults.Ios27) }

    assertEquals("dopo uno scarto le maschere si ridisegnano una volta", 3, replayed.size)
    assertEquals("…e una volta sola", settled + 3, bench.log.drawn.size)
    assertEquals("la piattaforma ha registrato il layer due volte: prima e dopo lo scarto", 2, button.control.records)
    assertEquals("lo scarto non è una registrazione nuova del nodo", 1, button.shadow.recordCount)
    assertTrue(
      "dopo lo scarto il tasto ridisegna la propria sagoma, non quella del pannello",
      replayed.all { drawn -> firstButton.any { it === drawn } },
    )
  }

  // ----------------------------------------------------------------------------------------------------------
  // Attrezzi
  // ----------------------------------------------------------------------------------------------------------

  private class Shape(
    val halfWidth: Float,
    val halfHeight: Float,
    val radii: FloatArray,
    val smoothing: Float,
    val name: String,
  )

  private data class IntSizeOf(val width: Int, val height: Int)

  /** `0,5 * erfc(z / √2)`: la coda di una gaussiana di deviazione uno, integrata con Simpson in `Double`. */
  private fun blurredEdge(z: Double): Double {
    if (z < 0.0) return 1.0 - blurredEdge(-z)
    val steps = 4000
    val upper = z + 10.0
    val h = (upper - z) / steps
    var sum = 0.0
    for (i in 0..steps) {
      val t = z + i * h
      val weight = if (i == 0 || i == steps) 1.0 else if (i % 2 == 1) 4.0 else 2.0
      sum += weight * exp(-0.5 * t * t)
    }
    return sum * h / 3.0 / sqrt(2.0 * Math.PI)
  }

  /** Le cartelle del banco: Gradle lancia i test dalla cartella del modulo, un IDE a volte dalla radice. */
  private fun benchFolder(name: String): File =
    listOf(File("../tools/glassbench/$name"), File("tools/glassbench/$name")).firstOrNull { it.isDirectory }
      ?: throw AssertionError("Non trovo tools/glassbench/$name da ${File(".").absolutePath}")

  /** Una lastra sotto prova: il suo layer (un `GraphicsLayer` vero sopra un doppio) e la sua ombra. */
  private class Pane(val layer: GraphicsLayer, val control: LayerControl) {
    val shadow = GlassShadowLayer(layer)
  }

  private class LayerControl {
    var hasDisplayList = false

    /** Quante volte Compose ha chiesto alla piattaforma di registrare il layer. */
    var records = 0
  }

  /**
   * Il banco: un canvas di piattaforma che annota ogni sagoma disegnata, e lastre i cui layer sono
   * `GraphicsLayer` veri sopra un doppio. [hardware]: il canvas si dice accelerato, la registrazione esegue la
   * lambda una volta come la registrerebbe un `RenderNode`, e poi il layer si ridisegna senza rieseguirla
   * finché [LayerControl.hasDisplayList] resta vero. Senza [hardware] ogni disegno riesegue la lambda.
   */
  private class ShadowBench(private val hardware: Boolean) {
    val log = PathLog(hardware)
    private val canvas = Canvas(log)
    private val scope = CanvasDrawScope()

    fun pane(): Pane {
      val control = LayerControl()
      return Pane(fakeLayer(control), control)
    }

    /** Un fotogramma dell'ombra di [pane]: i `Path` di piattaforma che ha disegnato, in ordine. */
    fun frame(pane: Pane, pieces: GlassShadowPieces, spec: GlassShadowSpec): List<android.graphics.Path> {
      val before = log.drawn.size
      scope.draw(Density(3f), LayoutDirection.Ltr, canvas, Size(132f, 132f)) {
        pane.shadow.draw(this, pieces, spec)
      }
      return log.drawn.subList(before, log.drawn.size).toList()
    }

    /**
     * Un `GraphicsLayer` di Compose sopra un doppio della piattaforma (`GraphicsLayerImpl`, interno a
     * ui-graphics, costruito per riflessione), come in `PhysicsShadowSlotsTest` e `HighlightRingReplayTest`.
     * Se un aggiornamento di Compose rinomina queste interne, il test si rompe qui, ad alta voce: va riscritto
     * il doppio, non tolto il test.
     */
    private fun fakeLayer(control: LayerControl): GraphicsLayer {
      givePlatformAFingerprint()
      val implClass = Class.forName("androidx.compose.ui.graphics.layer.GraphicsLayerImpl")
      val properties = HashMap<String, Any?>()
      val impl = Proxy.newProxyInstance(implClass.classLoader, arrayOf(implClass)) { proxy, method, args ->
        val name = method.name.substringBefore('-')
        when {
          name == "equals" -> proxy === args?.get(0)
          name == "hashCode" -> System.identityHashCode(proxy)
          name == "toString" -> "FakeGraphicsLayerImpl"
          name == "getHasDisplayList" -> hardware && control.hasDisplayList
          name == "getSupportsSoftwareRendering" -> false
          name == "record" -> {
            control.records++
            if (hardware) {
              // Come un RenderNode: la lambda si esegue adesso, in una registrazione.
              val layer = args!![2] as GraphicsLayer
              @Suppress("UNCHECKED_CAST")
              val block = args[3] as (DrawScope) -> Unit
              CanvasDrawScope().draw(args[0] as Density, args[1] as LayoutDirection, canvas, layer.size.toSize()) {
                block(this)
              }
              control.hasDisplayList = true
            }
            null
          }
          name.startsWith("set") && args?.size == 1 -> {
            properties[name.removePrefix("set")] = args[0]
            null
          }
          name.startsWith("get") && args.isNullOrEmpty() ->
            properties[name.removePrefix("get")] ?: defaultOf(method)
          name.startsWith("is") && args.isNullOrEmpty() ->
            properties[name.removePrefix("is")] ?: defaultOf(method)
          else -> defaultOf(method)
        }
      }
      return GraphicsLayer::class.java.getConstructor(implClass).newInstance(impl)
    }
  }

  /** Il canvas di piattaforma: annota ogni `drawPath` con il suo pennello, e per il resto è uno stub. */
  private class PathLog(private val hardware: Boolean) : android.graphics.Canvas() {
    val drawn = mutableListOf<android.graphics.Path>()
    private val paints = mutableListOf<android.graphics.Paint>()

    override fun drawPath(path: android.graphics.Path, paint: android.graphics.Paint) {
      drawn += path
      paints += paint
    }

    /** I pennelli con cui sono state disegnate le sagome di [paths] (tutti i disegni di quei `Path`). */
    fun paintsOf(paths: List<android.graphics.Path>): List<android.graphics.Paint> =
      drawn.indices.filter { index -> paths.any { it === drawn[index] } }.map { paints[it] }

    override fun isHardwareAccelerated(): Boolean = hardware
  }

  private companion object {
    /**
     * Come in `PhysicsShadowSlotsTest`: `GraphicsLayer` nel suo inizializzatore statico legge
     * `Build.FINGERPRINT`, che negli stub è null. Un valore che non sia "robolectric", scritto con `Unsafe`
     * (è un `static final` di uno stub), solo nel processo dei test.
     */
    fun givePlatformAFingerprint() {
      val field = android.os.Build::class.java.getDeclaredField("FINGERPRINT")
      if (field.get(null) != null) return
      val unsafeClass = Class.forName("sun.misc.Unsafe")
      val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
      val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
      val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
        .invoke(unsafe, field) as Long
      unsafeClass.getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
        .invoke(unsafe, base, offset, "fluid-engine/jvm-unit-test")
    }

    fun defaultOf(method: Method): Any? = when (method.returnType) {
      java.lang.Boolean.TYPE -> false
      java.lang.Integer.TYPE -> 0
      java.lang.Long.TYPE -> 0L
      java.lang.Float.TYPE -> 0f
      java.lang.Double.TYPE -> 0.0
      java.lang.Short.TYPE -> 0.toShort()
      java.lang.Byte.TYPE -> 0.toByte()
      java.lang.Character.TYPE -> 0.toChar()
      else -> null
    }
  }
}
