package dev.antigravity.fluidengine.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.fluidengine.foundation.EngineSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

/**
 * F8.7 · Il colore preso dal contenuto, puro: l'estrattore su immagini di prova (monocroma, trasparente, molto scura,
 * molto chiara, colorata) e i ruoli col contrasto verificato in chiaro e in scuro, su tutta la ruota dei colori.
 */
class F8p7ContentColorTest {

  /** Un'immagine [w] x [h] riempita da [paint] (ARGB). */
  private fun image(w: Int = 96, h: Int = 96, paint: (x: Int, y: Int) -> Int): IntArray =
    IntArray(w * h) { i -> paint(i % w, i / w) }

  private fun solid(color: Color, w: Int = 96, h: Int = 96) = image(w, h) { _, _ -> color.toArgb() }

  private fun hueOf(c: Color): Float {
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d <= 0f) return 0f
    val h = when (max) {
      r -> ((g - b) / d).let { if (it < 0) it + 6 else it }
      g -> (b - r) / d + 2
      else -> (r - g) / d + 4
    } * 60f
    return h
  }

  private fun hueDistance(a: Float, b: Float): Float = min(abs(a - b), 360f - abs(a - b))

  private fun saturation(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    return if (max <= 0f) 0f else (max - min) / max
  }

  // --- L'estrattore ------------------------------------------------------------------------------------------------

  @Test
  fun `un'immagine tutta trasparente non ha colori`() {
    assertNull(fluidContentColorsOf(image { _, _ -> 0x00FF0000 }, 96, 96))
    // Sotto l'alfa 160 non conta: un rosso quasi trasparente e' ancora niente.
    assertNull(fluidContentColorsOf(image { _, _ -> 0x9FFF0000.toInt() }, 96, 96))
    assertNull(fluidContentColorsOf(IntArray(0), 0, 0))
  }

  @Test
  fun `un'immagine monocroma da' un seme grigio e niente tinta inventata`() {
    // Grigi con una sfumatura, e un pugno di pixel colorati (sotto il 4 %).
    val pixels = image { x, y ->
      if (x < 6 && y < 6) Color(0xFFE53935).toArgb() else Color(0.30f + x / 400f, 0.30f + x / 400f, 0.30f + x / 400f).toArgb()
    }
    val colors = fluidContentColorsOf(pixels, 96, 96)!!
    assertTrue("monocroma", colors.monochrome)
    assertTrue("seme grigio: saturazione ${saturation(colors.seed)}", saturation(colors.seed) < 0.05f)
    assertEquals(listOf(colors.seed), colors.swatches)
  }

  @Test
  fun `un'immagine colorata da' il suo colore vivo e due o tre colori distinti`() {
    // Meta' blu notte, un terzo arancio, il resto verde: tre tinte vere.
    val pixels = image { x, _ ->
      when {
        x < 48 -> Color(0xFF1E4FD8).toArgb()
        x < 80 -> Color(0xFFF07A1A).toArgb()
        else -> Color(0xFF2EAA4A).toArgb()
      }
    }
    val colors = fluidContentColorsOf(pixels, 96, 96)!!
    assertFalse(colors.monochrome)
    assertEquals(colors.seed, colors.vibrant)
    assertTrue("seme saturo: ${saturation(colors.seed)}", saturation(colors.seed) > 0.4f)
    assertTrue("almeno due colori distinti: ${colors.swatches.size}", colors.swatches.size >= 2)
    assertTrue("al massimo tre", colors.swatches.size <= 3)
    // I colori distinti sono lontani di tinta fra loro.
    val hues = colors.swatches.map { hueOf(it) }
    for (i in hues.indices) for (j in i + 1 until hues.size) {
      assertTrue("tinte ${hues[i]} e ${hues[j]}", hueDistance(hues[i], hues[j]) >= 40f)
    }
    // Ogni colore della copertina che pesa e' rappresentato: il blu (la massa) c'e'.
    assertTrue("il blu c'e': ${hues.joinToString()}", hues.any { hueDistance(it, hueOf(Color(0xFF1E4FD8))) < 25f })
  }

  @Test
  fun `una copertina di un solo colore da' quel colore`() {
    val red = Color(0xFFD32F2F)
    val colors = fluidContentColorsOf(solid(red), 96, 96)!!
    assertFalse(colors.monochrome)
    assertTrue("tinta rossa: ${hueOf(colors.seed)}", hueDistance(hueOf(colors.seed), hueOf(red)) < 6f)
    assertEquals(1, colors.swatches.size)
    // La media e' il colore stesso.
    assertEquals(red.toArgb(), colors.dominant.toArgb())
  }

  @Test
  fun `un'immagine molto scura tiene la sua tinta`() {
    // Quasi nera con un blu notte profondo (luminosita' 0,25): niente viene scartato come nero, il seme e' blu.
    val navy = Color(0xFF101A40)
    val pixels = image { x, _ -> if (x % 3 == 0) Color(0xFF050505).toArgb() else navy.toArgb() }
    val colors = fluidContentColorsOf(pixels, 96, 96)!!
    assertFalse(colors.monochrome)
    assertTrue("tinta blu: ${hueOf(colors.seed)}", hueDistance(hueOf(colors.seed), hueOf(navy)) < 15f)
  }

  @Test
  fun `un'immagine molto chiara non diventa un neon`() {
    // Bianco con un rosa pallido: il seme resta rosa, e i ruoli lo tengono un fondo.
    val pink = Color(0xFFF7D9E0)
    val pixels = image { x, y -> if ((x + y) % 2 == 0) Color.White.toArgb() else pink.toArgb() }
    val colors = fluidContentColorsOf(pixels, 96, 96)!!
    val roles = fluidContentRoles(colors, isDark = false)
    assertTrue("pagina chiara: ${roles.pageTint}", contrastRatio(roles.pageTint, Color.White) < 1.25f)
  }

  @Test
  fun `il passo di campionamento e il bordo sono quelli di Pampa Widgets`() {
    assertEquals(96, FluidContentColorDefaults.SampleSize)
    assertEquals(2, FluidContentColorDefaults.SampleStep)
    assertEquals(160, FluidContentColorDefaults.MinAlpha)
    // Un'immagine piccola (non ridimensionata): un pixel solo vale.
    val one = fluidContentColorsOf(intArrayOf(Color(0xFF34C759).toArgb()), 1, 1)!!
    assertEquals(Color(0xFF34C759).toArgb(), one.dominant.toArgb())
  }

  // --- I ruoli -----------------------------------------------------------------------------------------------------

  /** Una tavolozza di prova: la ruota a passi di 15°, a tre saturazioni e tre luminosita', i grigi, i neon. */
  private fun seeds(): List<FluidContentColors> {
    val out = mutableListOf<Color>()
    for (h in 0 until 360 step 15) {
      for (s in listOf(0.25f, 0.6f, 1f)) for (v in listOf(0.2f, 0.6f, 1f)) out += hsv(h.toFloat(), s, v)
    }
    for (g in 0..10) out += Color(g / 10f, g / 10f, g / 10f)
    out += listOf(Color(0xFF39FF14), Color(0xFFFF00FF), Color(0xFF00FFFF), Color(0xFFFFFF00), Color(0xFF0000FF))
    return out.flatMap { c ->
      listOf(
        FluidContentColors(c, c, c, listOf(c), monochrome = false),
        FluidContentColors(c, c, c, listOf(c), monochrome = true),
      )
    }
  }

  private fun hsv(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt()) {
      0 -> Triple(c, x, 0f)
      1 -> Triple(x, c, 0f)
      2 -> Triple(0f, c, x)
      3 -> Triple(0f, x, c)
      4 -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
  }

  @Test
  fun `i ruoli fanno almeno 4,5 a 1 sul testo, in chiaro e in scuro, su tutta la ruota`() {
    var checked = 0
    for (colors in seeds()) for (dark in listOf(false, true)) {
      val r = fluidContentRoles(colors, dark)
      val name = "seme ${colors.seed} monocromo=${colors.monochrome} scuro=$dark"
      assertTrue("testo $name: ${contrastRatio(r.onPage, r.pageTint)}", contrastRatio(r.onPage, r.pageTint) >= 4.5f)
      assertTrue("secondario $name", contrastRatio(r.onPageVariant, r.pageTint) >= 4.5f)
      assertTrue("accento $name: ${contrastRatio(r.accent, r.pageTint)}", contrastRatio(r.accent, r.pageTint) >= 4.5f)
      assertTrue("sul tasto $name", contrastRatio(r.onAccent, r.accent) >= 4.5f)
      assertTrue("barra $name", contrastRatio(r.onBar, r.barGlass) >= 4.5f)
      assertTrue("barra sulla pagina $name", contrastRatio(r.onBar, r.pageTint) >= 4.5f)
      checked++
    }
    assertTrue(checked > 500)
  }

  @Test
  fun `dalla parte della copertina, come Apple Music, ogni coppia fa 4,5 a 1`() {
    var dark = 0
    var light = 0
    for (colors in seeds()) {
      val r = fluidContentRoles(colors, isDark = false, side = FluidContentSide.Content)
      // Non dipende dal tema.
      assertEquals(r, fluidContentRoles(colors, isDark = true, side = FluidContentSide.Content))
      val name = "seme ${colors.seed} monocromo=${colors.monochrome}"
      assertTrue("testo $name: ${contrastRatio(r.onPage, r.pageTint)}", contrastRatio(r.onPage, r.pageTint) >= 4.5f)
      assertTrue("secondario $name", contrastRatio(r.onPageVariant, r.pageTint) >= 4.5f)
      assertTrue("accento $name", contrastRatio(r.accent, r.pageTint) >= 4.5f)
      assertTrue("sul tasto $name", contrastRatio(r.onAccent, r.accent) >= 4.5f)
      assertTrue("barra $name", contrastRatio(r.onBar, r.barGlass) >= 4.5f)
      if (r.isDark) dark++ else light++
    }
    assertTrue("quasi tutte scure, come Apple: $dark scure, $light chiare", dark > light * 4)
  }

  @Test
  fun `l'azzurro chiaro diventa azzurro medio col testo bianco, il giallo chiarissimo resta chiaro col testo nero`() {
    // La copertina di «The Chase» nel Newsroom di iOS 26: #B7E3F6 diventa #5D90A5, stessa tinta, testo bianco.
    val sky = Color(0xFFB7E3F6)
    val r = fluidContentRoles(FluidContentColors(sky, sky, sky, listOf(sky), false), isDark = false, side = FluidContentSide.Content)
    assertTrue(r.isDark)
    assertTrue("stessa tinta: ${hueOf(r.pageTint)}", hueDistance(hueOf(r.pageTint), hueOf(sky)) < 15f)
    assertTrue("testo bianco", r.onPage.luminance() > 0.85f)
    assertTrue("pagina media, non nera: ${r.pageTint.luminance()}", r.pageTint.luminance() in 0.06f..0.20f)
    // La playlist gialla della 26.4: pagina gialla, testo e tasto neri.
    val yellow = Color(0xFFFFE135)
    val y = fluidContentRoles(FluidContentColors(yellow, yellow, yellow, listOf(yellow), false), isDark = true, side = FluidContentSide.Content)
    assertFalse(y.isDark)
    assertTrue("testo nero", y.onPage.luminance() < 0.05f)
    assertTrue("pagina gialla: ${hueOf(y.pageTint)}", hueDistance(hueOf(y.pageTint), hueOf(yellow)) < 15f)
  }

  @Test
  fun `il lato del tema non si ribalta e la pagina tiene la tinta del seme`() {
    val blue = Color(0xFF1E4FD8)
    val colors = FluidContentColors(blue, blue, blue, listOf(blue), monochrome = false)
    val light = fluidContentRoles(colors, isDark = false)
    val dark = fluidContentRoles(colors, isDark = true)
    assertTrue("pagina chiara nel tema chiaro", light.pageTint.luminance() > 0.7f)
    assertTrue("pagina scura nel tema scuro", dark.pageTint.luminance() < 0.08f)
    assertTrue("tinta blu chiara: ${hueOf(light.pageTint)}", hueDistance(hueOf(light.pageTint), hueOf(blue)) < 25f)
    assertTrue("tinta blu scura: ${hueOf(dark.pageTint)}", hueDistance(hueOf(dark.pageTint), hueOf(blue)) < 25f)
    assertTrue("si vede la tinta", saturation(light.pageTint) > 0.04f)
    // Monocromo: pagina neutra.
    val grey = fluidContentRoles(colors.copy(monochrome = true), isDark = false)
    assertTrue("pagina neutra: ${saturation(grey.pageTint)}", saturation(grey.pageTint) < 0.02f)
  }

  @Test
  fun `il tema della pagina tinge sfondo, superfici, testo e accento e lascia il resto`() {
    val base = fluidColorScheme(EngineSettings(), isDark = false)
    val seed = Color(0xFFF07A1A)
    val roles = fluidContentRoles(FluidContentColors(seed, seed, seed, listOf(seed), false), isDark = false)
    val tinted = tintedScheme(base, roles, page = true, accent = true)
    assertEquals(roles.pageTint, tinted.background)
    assertEquals(roles.pageTint, tinted.surface)
    assertEquals(roles.onPage, tinted.onSurface)
    assertEquals(roles.onPageVariant, tinted.onSurfaceVariant)
    assertEquals(roles.accent, tinted.primary)
    assertEquals(roles.onAccent, tinted.onPrimary)
    assertEquals(base.error, tinted.error)
    assertEquals(base.secondary, tinted.secondary)
    // Solo la pagina: l'accento resta quello dell'app.
    val pageOnly = tintedScheme(base, roles, page = true, accent = false)
    assertEquals(base.primary, pageOnly.primary)
    // Il testo sulle card della pagina tinta si legge.
    for (surface in listOf(tinted.surfaceContainerLow, tinted.surfaceContainer, tinted.surfaceContainerHigh)) {
      assertTrue("card $surface", contrastRatio(tinted.onSurface, surface) >= 4.5f)
    }
    val dark = fluidContentRoles(FluidContentColors(seed, seed, seed, listOf(seed), false), isDark = true)
    val tintedDark = tintedScheme(fluidColorScheme(EngineSettings(), isDark = true), dark, page = true, accent = true)
    for (surface in listOf(tintedDark.surfaceContainerLow, tintedDark.surfaceContainer, tintedDark.surfaceContainerHigh)) {
      assertTrue("card scura $surface", contrastRatio(tintedDark.onSurface, surface) >= 4.5f)
    }
  }

  @Test
  fun `OKLCH va e torna`() {
    for (c in listOf(Color(0xFF1E4FD8), Color(0xFFF07A1A), Color(0xFF808080), Color.White, Color.Black, Color(0xFF39FF14))) {
      val back = Oklch.of(c).toColor()
      assertTrue("$c -> $back", sameColor(c, back) || abs(c.red - back.red) < 0.004f && abs(c.green - back.green) < 0.004f && abs(c.blue - back.blue) < 0.004f)
    }
    // Fuori gamma: la croma scende finche' il colore ci sta.
    val wild = Oklch(0.9f, 0.4f, 4f).toColor()
    assertTrue(wild.red in 0f..1f && wild.green in 0f..1f && wild.blue in 0f..1f)
  }

  // --- F8.7b: il colore vivo per il lato della copertina ---------------------------------------------------------

  /** Una copertina di 96 x 96 fatta di blocchi: ogni colore per la sua quota dei pixel, nell'ordine. */
  private fun cover(vararg parts: Pair<Long, Float>): IntArray {
    val px = IntArray(96 * 96)
    var i = 0
    for ((argb, share) in parts) repeat((share * px.size).toInt()) { if (i < px.size) px[i++] = Color(argb).toArgb() }
    while (i < px.size) px[i++] = Color(parts.last().first).toArgb()
    return px
  }

  private fun degrees(c: Color): Float = ((Math.toDegrees(Oklch.of(c).h.toDouble()) + 360.0) % 360.0).toFloat()

  private fun assertLegible(r: FluidContentRoles, name: String) {
    assertTrue("testo $name", contrastRatio(r.onPage, r.pageTint) >= 4.5f)
    assertTrue("secondario $name", contrastRatio(r.onPageVariant, r.pageTint) >= 4.5f)
    assertTrue("accento $name", contrastRatio(r.accent, r.pageTint) >= 4.5f)
    assertTrue("sul tasto $name", contrastRatio(r.onAccent, r.accent) >= 4.5f)
    assertTrue("barra $name", contrastRatio(r.onBar, r.barGlass) >= 4.5f)
  }

  @Test
  fun `una copertina colorata da' una pagina viva, come Apple Music`() {
    // Il fiore della galleria: foglie oliva, verdi e bruno-rosse, un poco di rosa. La media delle tre tinte vicine era un
    // oliva spento (croma 0,068); ora vince il verde delle foglie, piu' vivo.
    val flower = fluidContentColorsOf(
      cover(0xFF6E5D26 to 0.43f, 0xFF667428 to 0.32f, 0xFF84412D to 0.20f, 0xFFAB678F to 0.05f), 96, 96,
    )!!
    assertTrue("il colore vivo non e' la media: ${flower.vivid} / ${flower.vibrant}", flower.vivid != flower.vibrant)
    val green = fluidContentRoles(flower, isDark = false, side = FluidContentSide.Content)
    val greenHue = degrees(green.pageTint)
    assertTrue("pagina verde: $greenHue°", abs(greenHue - 119f) < 20f)
    assertTrue("pagina viva: croma ${Oklch.of(green.pageTint).c}", Oklch.of(green.pageTint).c >= 0.08f)
    assertTrue("non quasi nera: ${Oklch.of(green.pageTint).l}", Oklch.of(green.pageTint).l >= 0.40f)
    assertLegible(green, "fiore")

    // Una copertina rosa con un fondo scuro: la pagina e' rosa, piena.
    val pink = fluidContentColorsOf(cover(0xFFE0457B to 0.6f, 0xFF3A2E35 to 0.4f), 96, 96)!!
    val rose = fluidContentRoles(pink, isDark = false, side = FluidContentSide.Content)
    val roseHue = degrees(rose.pageTint)
    assertTrue("pagina rosa: $roseHue°", roseHue < 20f || roseHue > 345f)
    assertTrue("pagina viva: croma ${Oklch.of(rose.pageTint).c}", Oklch.of(rose.pageTint).c >= 0.10f)
    assertLegible(rose, "rosa")

    // La tuta arancione dell'astronauta: la pagina e' arancio bruciato.
    val suit = fluidContentColorsOf(
      cover(0xFFBE5534 to 0.55f, 0xFF73111B to 0.12f, 0xFF604A22 to 0.10f, 0xFF1A1A1E to 0.23f), 96, 96,
    )!!
    val orange = fluidContentRoles(suit, isDark = true, side = FluidContentSide.Content)
    val orangeHue = degrees(orange.pageTint)
    assertTrue("pagina arancio: $orangeHue°", orangeHue in 20f..55f)
    assertTrue("pagina viva: croma ${Oklch.of(orange.pageTint).c}", Oklch.of(orange.pageTint).c >= 0.10f)
    assertLegible(orange, "tuta")
  }

  @Test
  fun `le copertine scure e chiare non cambiano`() {
    // Il razzo di notte (blu notte con le luci) e la copertina chiara color pesca: il colore vivo e' quello di prima.
    val night = fluidContentColorsOf(cover(0xFF273553 to 0.95f, 0xFFAB8451 to 0.05f), 96, 96)!!
    assertEquals(night.vibrant, night.vivid)
    val peach = fluidContentColorsOf(cover(0xFFAE7854 to 0.93f, 0xFFD7AFB9 to 0.04f, 0xFFCDA6C1 to 0.03f), 96, 96)!!
    assertEquals(peach.vibrant, peach.vivid)
    // E i ruoli sono quelli del seme di prima.
    val before = FluidContentColors(peach.seed, peach.dominant, peach.vibrant, peach.swatches, peach.monochrome)
    assertEquals(
      fluidContentRoles(before, isDark = false, side = FluidContentSide.Content),
      fluidContentRoles(peach, isDark = false, side = FluidContentSide.Content),
    )
    // Una macchia viva piccola (sotto il 15 %) non prende il posto della massa.
    val spot = fluidContentColorsOf(cover(0xFF6E5D26 to 0.94f, 0xFF2EAA4A to 0.06f), 96, 96)!!
    assertEquals(spot.vibrant, spot.vivid)
  }

  @Test
  fun `la cache tiene le ultime 64 chiavi`() {
    FluidContentColorCache.clear()
    val c = FluidContentColors(Color.Red, Color.Red, Color.Red, listOf(Color.Red), false)
    for (i in 0 until 70) FluidContentColorCache[i] = c
    assertEquals(64, FluidContentColorCache.size)
    assertNull(FluidContentColorCache[0])
    assertNotNull(FluidContentColorCache[69])
    FluidContentColorCache.clear()
    assertEquals(0, FluidContentColorCache.size)
  }

  @Test
  fun `il passaggio lascia gli schemi chiari e scuri distinti`() {
    // Smoke: gli schemi di base esistono in chiaro e in scuro (la base del passaggio).
    assertTrue(lightColorScheme().background != darkColorScheme().background)
  }
}
