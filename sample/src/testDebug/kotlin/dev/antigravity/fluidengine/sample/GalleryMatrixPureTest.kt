package dev.antigravity.fluidengine.sample

import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.foundation.AccentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole pure della matrice degli stati (F1.6), senza Android attorno: come il tema si legge dalle
 * impostazioni e vi si scrive, com'e' fatta la fotografia che le demo leggono, da dove parte la galleria quando la
 * si lancia con degli extra, e come sono messe le pagine dei componenti nuovi.
 */
class GalleryMatrixPureTest {

  // --- Il tema ------------------------------------------------------------------------------------------

  @Test
  fun `ogni tema scritto nelle impostazioni si rilegge uguale`() {
    MatrixTheme.entries.forEach { theme ->
      val settings = theme.applyTo(EngineSettings())
      assertEquals("$theme", theme, MatrixTheme.of(settings, systemDark = false))
      assertEquals("$theme, col sistema scuro", theme, MatrixTheme.of(settings, systemDark = true))
    }
  }

  @Test
  fun `il tema di sistema segue il sistema, e il nero conta solo da scuro`() {
    val system = EngineSettings(themeMode = ThemeMode.SYSTEM)
    assertEquals(MatrixTheme.Light, MatrixTheme.of(system, systemDark = false))
    assertEquals(MatrixTheme.Dark, MatrixTheme.of(system, systemDark = true))

    // Il nero acceso sotto il chiaro non si vede: il tema e' chiaro, e tornando allo scuro il nero c'e' ancora.
    val lightButBlack = MatrixTheme.Black.applyTo(EngineSettings()).copy(themeMode = ThemeMode.LIGHT)
    assertEquals(MatrixTheme.Light, MatrixTheme.of(lightButBlack, systemDark = true))
    assertEquals(MatrixTheme.Black, MatrixTheme.of(lightButBlack.copy(themeMode = ThemeMode.DARK), systemDark = false))

    // AMOLED e' "scuro, e nero".
    assertEquals(MatrixTheme.Black, MatrixTheme.of(EngineSettings(themeMode = ThemeMode.AMOLED), systemDark = false))
  }

  @Test
  fun `cambiare il tema non tocca il resto delle impostazioni`() {
    val base = EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ocean", hapticsEnabled = false)
    MatrixTheme.entries.forEach { theme ->
      val applied = theme.applyTo(base)
      assertEquals(AccentMode.CUSTOM_PRESET, applied.accentMode)
      assertEquals("ocean", applied.customAccentName)
      assertFalse(applied.hapticsEnabled)
    }
    assertTrue(MatrixTheme.Dark.isDark)
    assertTrue(MatrixTheme.Black.isDark)
    assertFalse(MatrixTheme.Light.isDark)
  }

  // --- La fotografia ------------------------------------------------------------------------------------

  @Test
  fun `la matrice di serie non e' mossa, e la riga dice solo il tema`() {
    val snapshot = GalleryMatrix().snapshot(EngineSettings(themeMode = ThemeMode.LIGHT), systemDark = false)

    assertTrue(snapshot.isDefault)
    assertEquals("Chiaro", snapshot.summary)
    assertEquals("Matrice degli stati: Chiaro", matrixButtonDescription(snapshot))
  }

  @Test
  fun `ogni asse mosso si legge nella riga e accende il tasto`() {
    val matrix = GalleryMatrix()
    matrix.textScale = MatrixTextScale.Largest
    matrix.reducedMotion = true
    matrix.language = MatrixLanguage.English
    matrix.data = MatrixData.Empty
    val snapshot = matrix.snapshot(MatrixTheme.Black.applyTo(EngineSettings()), systemDark = false)

    assertFalse(snapshot.isDefault)
    assertEquals("Nero · testo 2,0× · movimento ridotto · English · vuoto", snapshot.summary)
  }

  @Test
  fun `ripristinare rimette di serie gli assi della matrice`() {
    val matrix = GalleryMatrix(MatrixTextScale.Medium, reducedMotion = true, MatrixLanguage.English, MatrixData.Error)

    matrix.reset()

    assertTrue(matrix.snapshot(EngineSettings(), systemDark = false).isDefault)
  }

  @Test
  fun `le tre scale sono quelle del brief, in ordine`() {
    assertEquals(listOf(1f, 1.3f, 2f), MatrixTextScale.entries.map { it.scale })
  }

  // --- Il lancio con degli extra ------------------------------------------------------------------------

  private fun launch(vararg extras: Pair<String, String>): GalleryLaunch = GalleryLaunch.parse(mapOf(*extras)::get)

  @Test
  fun `senza extra la galleria parte come sempre`() {
    val none = launch()

    assertTrue(none.isEmpty)
    assertEquals(GalleryNav.Launch, none.stack())
    assertEquals(EngineSettings(), none.settings())
    assertTrue(none.matrix().snapshot(EngineSettings(), systemDark = false).isDefault)
  }

  @Test
  fun `una pagina chiesta si apre sopra Chrome, e la matrice parte come richiesto`() {
    val request = launch(
      GalleryLaunch.ExtraPage to "LongLists",
      GalleryLaunch.ExtraTheme to "BLACK",
      GalleryLaunch.ExtraText to "2.0",
      GalleryLaunch.ExtraReduced to "true",
      GalleryLaunch.ExtraLanguage to "en",
      GalleryLaunch.ExtraData to "empty",
    )

    assertEquals(
      listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(GalleryPage.LongLists)),
      request.stack(),
    )
    assertEquals(MatrixTheme.Black, MatrixTheme.of(request.settings(), systemDark = false))
    val snapshot = request.matrix().snapshot(request.settings(), systemDark = false)
    assertEquals(MatrixTextScale.Largest, snapshot.textScale)
    assertTrue(snapshot.reducedMotion)
    assertEquals(MatrixLanguage.English, snapshot.language)
    assertEquals(MatrixData.Empty, snapshot.data)
  }

  @Test
  fun `un valore che non si riconosce si ignora e non fa partire la galleria storta`() {
    val request = launch(
      GalleryLaunch.ExtraPage to "NonEsiste",
      GalleryLaunch.ExtraTheme to "viola",
      GalleryLaunch.ExtraText to "3.7",
      GalleryLaunch.ExtraLanguage to "klingon",
      GalleryLaunch.ExtraData to "boh",
    )

    assertTrue(request.isEmpty)
    assertNull(request.page)
  }

  @Test
  fun `ogni scala del testo si chiede in piu' modi, e totry riapre la lista`() {
    assertEquals(MatrixTextScale.Normal, launch(GalleryLaunch.ExtraText to "100").textScale)
    assertEquals(MatrixTextScale.Medium, launch(GalleryLaunch.ExtraText to "1,3").textScale)
    assertEquals(MatrixTextScale.Largest, launch(GalleryLaunch.ExtraText to "200").textScale)
    assertEquals(GalleryNav.Launch, launch(GalleryLaunch.ExtraPage to "totry").stack())
  }

  // --- Le pagine dei componenti nuovi -------------------------------------------------------------------

  @Test
  fun `le pagine nuove sono dieci, ognuna in un gruppo, e le altre non hanno la matrice`() {
    val withMatrix = GalleryPage.entries.filter { it.hasMatrix }

    assertEquals(10, withMatrix.size)
    assertEquals(withMatrix.toSet(), GalleryPage.newComponents.toSet())
    assertEquals(withMatrix.size, GalleryPage.newComponents.size)
    withMatrix.forEach { assertNotEquals("${it.name}: senza gruppo", null, it.group) }
    GalleryPage.entries.filter { !it.hasMatrix }.forEach { assertNull("${it.name} ha un gruppo", it.group) }
  }

  @Test
  fun `ogni gruppo ha almeno una pagina, e il catalogo le raggruppa nell'ordine dei gruppi`() {
    GalleryGroup.entries.forEach { group ->
      assertTrue("${group.title}: vuoto", GalleryPage.newComponents.any { it.group == group })
    }
    val order = GalleryPage.newComponents.map { it.group!!.ordinal }
    assertEquals("le pagine non seguono l'ordine dei gruppi", order.sorted(), order)
  }

  @Test
  fun `ogni pagina nuova ha un sottotitolo per il catalogo`() {
    GalleryPage.newComponents.forEach { page ->
      assertTrue("${page.name}: sottotitolo vuoto", page.catalogSubtitle.isNotBlank())
    }
  }

  @Test
  fun `ogni pagina nuova ha una voce in Da provare, e la sua area`() {
    val items = TryRounds.flatMap { it.items }
    GalleryPage.newComponents.forEach { page ->
      assertTrue(
        "${page.name}: nessuna voce porta qui",
        items.any { (it.target as? TryTarget.Page)?.page == page },
      )
    }
    // Una voce spiega la matrice stessa.
    assertTrue(items.any { it.area == TryArea.StateMatrix })
  }
}
