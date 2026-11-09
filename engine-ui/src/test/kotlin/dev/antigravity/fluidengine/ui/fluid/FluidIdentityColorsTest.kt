package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.resolveFluidColorScheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La palette d'identita': stabile (e' un contratto di prodotto), distinguibile e leggibile.
 *
 * I numeri «d'oro» in alto sono il test che conta di piu': se cadono, qualcuno ha cambiato la
 * palette, l'ordine o l'hash, e **tutte le persone di tutte le app hanno cambiato colore**.
 * Si aggiornano solo in una versione maggiore, e col changelog.
 */
class FluidIdentityColorsTest {

  private val lightFilm = Color(0xFFFAFAFA)
  private val darkFilm = Color(0xFF4A4A4E)

  private data class Case(val name: String, val dark: Boolean, val scheme: ColorScheme, val film: Color)

  private fun cases(): List<Case> =
    listOf("fluid", "ember", "ocean", "jade").flatMap { name ->
      listOf(false, true).map { dark ->
        val scheme = resolveFluidColorScheme(
          settings = EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = name),
          isDark = dark,
        )
        Case("$name/dark=$dark", dark, scheme, if (dark) darkFilm else lightFilm)
      }
    }

  private fun surfaces(case: Case) =
    listOf(case.scheme.surface, case.scheme.surfaceContainerLow, case.scheme.surfaceContainerHigh, case.film)

  // --- Stabilita' ------------------------------------------------------------------------------------

  @Test
  fun `the hash is FNV-1a over the UTF-8 bytes - the published test vectors`() {
    // Vettori di prova pubblicati di FNV-1a a 32 bit.
    assertEquals(0x811C9DC5.toInt(), fluidIdentityHash(""))
    assertEquals(0xE40C292C.toInt(), fluidIdentityHash("a"))
    assertEquals(0xBF9CF968.toInt(), fluidIdentityHash("foobar"))
  }

  @Test
  fun `golden indexes - changing them moves every user's colours`() {
    assertEquals(12, FluidIdentityColors.palette.size)
    assertEquals(1, FluidIdentityColors.indexFor(""))
    assertEquals(4, FluidIdentityColors.indexFor("a"))
    assertEquals(2, FluidIdentityColors.indexFor("anna@example.com"))
    assertEquals(11, FluidIdentityColors.indexFor("Anna Rossi"))
    assertEquals(0, FluidIdentityColors.indexFor("mario.rossi@gmail.com"))
    assertEquals(5, FluidIdentityColors.indexFor("alessio@pampa.dev"))
  }

  @Test
  fun `golden palette - the order is a contract`() {
    val p = FluidIdentityColors.palette
    assertEquals(Color(0xFFC62828), p[0].light)
    assertEquals(Color(0xFF00796B), p[5].light)
    assertEquals(Color(0xFF1565C0), p[7].light)
    assertEquals(Color(0xFFC2185B), p[11].light)
    assertEquals(Color(0xFFFF8A80), p[0].dark)
    assertEquals(Color(0xFFF48FB1), p[11].dark)
    assertEquals(Color(0xFF4DB6A0), p[5].dark)
    assertEquals(Color(0xFF40CFF0), p[6].dark)
  }

  @Test
  fun `the same key always gives the same colour and a different case is a different key`() {
    repeat(3) { assertEquals(2, FluidIdentityColors.indexFor("anna@example.com")) }
    assertEquals(FluidIdentityColors.colorFor("anna@example.com"), FluidIdentityColors.colorFor("anna@example.com"))
    // La normalizzazione e' di chi chiama: questo test dice che l'engine non la fa.
    assertEquals(6, FluidIdentityColors.indexFor("Anna@example.com"))
    assertNotEquals(FluidIdentityColors.indexFor("Anna@example.com"), FluidIdentityColors.indexFor("anna@example.com"))
  }

  @Test
  fun `indexes stay inside the palette for any key including emoji and very long text`() {
    val keys = listOf("", " ", "\u0000", "🙂", "日本語", "x".repeat(100_000), "Ünïcödé", "مرحبا")
    keys.forEach { key ->
      val i = FluidIdentityColors.indexFor(key)
      assertTrue("$key -> $i", i in 0 until FluidIdentityColors.palette.size)
    }
  }

  @Test
  fun `keys spread over the whole palette`() {
    val hit = BooleanArray(12)
    for (n in 0 until 500) hit[FluidIdentityColors.indexFor("persona$n@example.com")] = true
    assertTrue("tinte mai scelte: ${hit.indices.filter { !hit[it] }}", hit.all { it })
  }

  @Test
  fun `colorAt wraps and survives negatives`() {
    assertEquals(FluidIdentityColors.palette[0], FluidIdentityColors.colorAt(12))
    assertEquals(FluidIdentityColors.palette[11], FluidIdentityColors.colorAt(-1))
    assertEquals(FluidIdentityColors.palette[3], FluidIdentityColors.colorAt(15))
  }

  // --- Contrasto -----------------------------------------------------------------------------------

  @Test
  fun `the palette needs no correction - the monogram reads on every pair as it is`() {
    FluidIdentityColors.palette.forEachIndexed { index, pair ->
      listOf("light" to pair.light, "dark" to pair.dark).forEach { (side, fill) ->
        val readable = fluidReadableOn(fill)
        assertEquals("$index/$side: il fondo non doveva spostarsi", fill, readable.fill)
        val ratio = fluidContrastRatio(readable.ink, fill)
        assertTrue("$index/$side: $ratio:1 sotto 4,5", ratio >= FluidInkMinContrast)
      }
    }
  }

  @Test
  fun `each side stands out from the surfaces it sits on at 3 to 1 as a graphic mark`() {
    cases().forEach { case ->
      FluidIdentityColors.palette.forEachIndexed { index, pair ->
        val fill = pair.forTheme(case.dark)
        surfaces(case).forEach { behind ->
          val ratio = fluidContrastRatio(fill, behind)
          assertTrue("${case.name} #$index su $behind: $ratio:1 sotto 3", ratio >= 3f)
        }
      }
    }
  }

  @Test
  fun `the twelve hues are distinct - neighbours at least 14 degrees apart and no pair under 10`() {
    listOf("light", "dark").forEach { side ->
      val hues = FluidIdentityColors.palette.map { hue(if (side == "light") it.light else it.dark) }
      for (i in hues.indices) {
        val gap = hueGap(hues[i], hues[(i + 1) % hues.size])
        assertTrue("$side: tinta $i e ${(i + 1) % hues.size} a $gap gradi", gap >= 14f)
      }
      for (i in hues.indices) for (j in i + 1 until hues.size) {
        assertTrue("$side: $i e $j a ${hueGap(hues[i], hues[j])} gradi", hueGap(hues[i], hues[j]) >= 10f)
      }
    }
  }

  @Test
  fun `a mid grey has no ink that reaches 4,5 and is moved by the least that works`() {
    val grey = Color(0xFF777777)
    val ink = FluidVividColors.from(grey).content
    assertTrue("il grigio medio doveva essere il caso difficile", fluidContrastRatio(ink, grey) < FluidInkMinContrast)
    val fixed = fluidReadableOn(grey)
    assertTrue(fluidContrastRatio(fixed.ink, fixed.fill) >= FluidInkMinContrast)
    // Del minimo: non e' diventato nero ne' bianco.
    assertNotEquals(Color.Black, fixed.fill)
    assertNotEquals(Color.White, fixed.fill)
    assertTrue("si e' spostato di poco", abs(fixed.fill.red - grey.red) < 0.25f)
  }

  @Test
  fun `any colour an app passes ends up readable - five hundred random colours and the extremes`() {
    val random = Random(1234)
    val colours = List(500) { Color(random.nextInt(0x1000000) or (0xFF shl 24)) } +
      listOf(Color.White, Color.Black, Color(0xFFFFFF00), Color(0xFF7F7F7F), Color(0xFF808080), Color(0xFFFF00FF))
    colours.forEach { c ->
      val r = fluidReadableOn(c)
      val ratio = fluidContrastRatio(r.ink, r.fill)
      assertTrue("$c -> ${r.fill} con ${r.ink}: $ratio:1", ratio >= FluidInkMinContrast - 0.01f)
    }
  }

  @Test
  fun `a colour that already reads is not touched`() {
    val readable = Color(0xFF1B5E20)
    val result = fluidReadableOn(readable)
    assertEquals(readable, result.fill)
    // Un verde scuro: l'inchiostro e' chiaro.
    assertTrue(result.ink.luminance() > 0.5f)
  }

  private fun hue(c: Color): Float {
    val r = c.red
    val g = c.green
    val b = c.blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    if (d == 0f) return 0f
    val h = when (mx) {
      r -> ((g - b) / d) % 6f
      g -> (b - r) / d + 2f
      else -> (r - g) / d + 4f
    }
    return ((h * 60f) + 360f) % 360f
  }

  private fun hueGap(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return min(d, 360f - d)
  }
}
