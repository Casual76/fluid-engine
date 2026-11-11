package dev.antigravity.fluidengine.sample.icons

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.Gallery
import dev.antigravity.fluidengine.sample.GalleryEntry
import dev.antigravity.fluidengine.sample.GalleryLaunch
import dev.antigravity.fluidengine.sample.GalleryNav
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.RouteChrome
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarInlineBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.lang.reflect.Modifier as JavaModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pagina «Icone» (F1A.11): si apre dalla galleria con il suo extra, ogni sezione si compone nei due temi, e il
 * catalogo che mostra e' tutto il set. Nessuno l'ha vista sul tablet: questa e' la prova che ci arriva intera.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-mdpi")
class IconsPageTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun `l'extra della galleria apre la pagina, e l'extra della sezione sceglie la sezione`() {
    assertEquals(GalleryPage.Icons, GalleryLaunch.parse { if (it == "gallery.page") "icons" else null }.page)
    assertEquals(IconsSection.Sizes, IconsSection.parse("sizes"))
    assertEquals(IconsSection.Chrome, IconsSection.parse(" CHROME "))
    assertEquals(IconsSection.Rtl, IconsSection.parse("rtl"))
    assertEquals(IconsSection.Set, IconsSection.parse("set"))
    // Un valore che non c'e' (o nessuno) mostra tutto.
    assertNull(IconsSection.parse("tutto"))
    assertNull(IconsSection.parse(null))
  }

  @Test
  fun `la pagina si apre nella galleria e Indietro torna a chi l'ha aperta`() {
    val nav = GalleryNav(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(GalleryPage.Icons)))
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Gallery(settings = EngineSettings(), onSettingsChange = {}, nav = nav)
      }
    }
    compose.waitForIdle()
    assertTrue(compose.onAllNodesWithText("--es icons.section", substring = true).fetchSemanticsNodes().isNotEmpty())
    compose.onNodeWithTag("icons-page").assertExists()
    compose.runOnIdle { assertTrue(nav.back()) }
    compose.waitForIdle()
    assertNull(nav.page)
  }

  private fun everySectionComposes(dark: Boolean) {
    val section = mutableStateOf<IconsSection?>(null)
    compose.setContent {
      val settings = remember(dark) { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
      FluidTheme(settings = settings) {
        val current = section.value
        key(current) {
          FluidScreen(title = "Icone") { iconsPageContent(current) }
        }
      }
    }
    val expected = mapOf(
      IconsSection.Sizes to listOf("icons-sizes-light", "icons-sizes-dark"),
      IconsSection.Chrome to listOf(
        "icons-bar-light", "icons-bar-tinted-light", "icons-capsule-light", "icons-tabbar-light",
        "icons-bar-dark", "icons-bar-tinted-dark", "icons-capsule-dark", "icons-tabbar-dark",
      ),
      IconsSection.Rtl to listOf("icons-rtl-bar-ltr", "icons-rtl-bar-rtl", "icons-rtl-row-ltr", "icons-rtl-row-rtl"),
      IconsSection.Set to listOf("icons-set-0", "icons-set-${(IconsCatalog.size - 1) / 3}"),
    )
    for ((s, tags) in expected) {
      compose.runOnIdle { section.value = s }
      compose.waitForIdle()
      for (tag in tags) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
        compose.onNodeWithTag(tag).assertExists()
      }
    }
  }

  @Test
  fun `ogni sezione si compone sul tema chiaro`() = everySectionComposes(dark = false)

  @Test
  fun `ogni sezione si compone sul tema scuro`() = everySectionComposes(dark = true)

  @Test
  fun `il catalogo della pagina e' tutto il set, niente di piu'`() {
    val outline = publicGetters("dev.antigravity.fluidengine.ui.fluid.icons.FluidIconsOutlineKt", FluidIcons::class.java)
    val filled = publicGetters("dev.antigravity.fluidengine.ui.fluid.icons.FluidIconsFilledKt", FluidIcons.Filled::class.java)
    assertEquals(
      "la pagina «Icone» non mostra il set: aggiungi o togli le righe in IconsCatalog.kt",
      outline.toSortedSet(),
      IconsCatalog.map { it.name }.toSortedSet(),
    )
    assertEquals(
      "le gemelle piene della pagina non sono quelle del set",
      filled.toSortedSet(),
      IconsCatalog.filter { it.filled != null }.map { it.name }.toSortedSet(),
    )
    // Ogni voce porta l'icona che dice di portare.
    IconsCatalog.forEach { entry ->
      assertEquals(entry.name, "FluidIcons.${entry.name}", entry.outline().name)
      entry.filled?.let { assertEquals(entry.name, "FluidIcons.Filled.${entry.name}", it().name) }
    }
  }

  private fun publicGetters(className: String, receiver: Class<*>): List<String> =
    Class.forName(className).declaredMethods
      .filter { JavaModifier.isPublic(it.modifiers) && JavaModifier.isStatic(it.modifiers) }
      .filter { it.name.startsWith("get") && it.parameterTypes.singleOrNull() == receiver }
      .map { it.name.removePrefix("get") }
}

/**
 * Le taglie e i margini che i componenti dell'engine danno alle icone dalla 4.0 (F1A.11), misurati su un albero vero.
 *
 * Perche' qui. I test di `engine-ui` sono sul JVM senza Compose; la taglia d'ambiente ([FluidIcon] senza `size`
 * dentro un tasto, una capsula, una tab bar) e i margini della barra si vedono solo componendo. Sono le stesse misure
 * che la pagina «Icone» mette davanti al dispositivo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class IconSizesInComponentsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.LIGHT) }, content = content)
  }

  private fun widthOf(description: String): Dp =
    compose.onNodeWithContentDescription(description, useUnmergedTree = true).getUnclippedBoundsInRoot().width

  private fun centerXOf(description: String): Dp {
    val bounds = compose.onNodeWithContentDescription(description, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (bounds.left + bounds.right) / 2
  }

  private fun assertDp(message: String, expected: Dp, actual: Dp) =
    assertEquals(message, expected.value, actual.value, 0.51f)

  @Test
  fun `l'icona prende la taglia dal tasto, e una taglia scritta resta`() {
    compose.setContent {
      Themed {
        Column {
          FluidIcon(FluidIcons.Search, "fuori")
          FluidGlassIconButton(onClick = {}) { FluidIcon(FluidIcons.Search, "tasto") }
          FluidGlassIconButton(onClick = {}, size = FluidGlassSize.Compact) { FluidIcon(FluidIcons.Search, "compatto") }
          FluidGlassIconButton(onClick = {}, size = FluidGlassSize.Large) { FluidIcon(FluidIcons.Search, "grande") }
          FluidGlassIconButton(onClick = {}) { FluidIcon(FluidIcons.Search, "scritta", Modifier.size(20.dp)) }
          FluidGlassIconButton(onClick = {}) { FluidIcon(FluidIcons.Search, "parametro", size = 22.dp) }
          // Un `Icon` di Material senza taglia: quella del vettore, come prima.
          FluidGlassIconButton(onClick = {}) { Icon(Icons.Rounded.Share, "material") }
        }
      }
    }
    compose.waitForIdle()
    assertDp("fuori da tutto", 24.dp, widthOf("fuori"))
    assertDp("nel tasto da 44", 26.dp, widthOf("tasto"))
    assertDp("nel tasto da 40", 24.dp, widthOf("compatto"))
    assertDp("nel tasto da 54", 32.dp, widthOf("grande"))
    assertDp("con il modificatore", 20.dp, widthOf("scritta"))
    assertDp("con il parametro", 22.dp, widthOf("parametro"))
    assertDp("Icon di Material", 24.dp, widthOf("material"))
  }

  @Test
  fun `la barra - freccia a 24, azioni a 26, vetri a 16 dp dal bordo e a 8 fra loro`() {
    compose.setContent {
      Themed {
        Box(Modifier.fillMaxSize().testTag("schermo")) {
          FluidPageChrome(title = "Note", onBack = {}) {
            FluidBarAction(icon = FluidIcons.Search, contentDescription = "Cerca", onClick = {})
            FluidBarAction(icon = FluidIcons.MoreHoriz, contentDescription = "Altro", onClick = {})
          }
        }
      }
    }
    compose.waitForIdle()
    val width = compose.onNodeWithTag("schermo").getUnclippedBoundsInRoot().width
    val halfLens = FluidMetrics.GlassButtonLens / 2
    assertDp("freccia", FluidMetrics.BackIconSize, widthOf("Indietro"))
    assertDp("azione", FluidMetrics.BarIconSize, widthOf("Cerca"))
    // L'icona sta al centro del vetro: il vetro del tasto indietro comincia a 16 dp dal bordo sinistro...
    assertDp("vetro indietro dal bordo", 16.dp, centerXOf("Indietro") - halfLens)
    // ... quello dell'ultima azione finisce a 16 dp dal bordo destro, e fra i due vetri delle azioni ci sono 8 dp.
    assertDp("ultimo vetro dal bordo", 16.dp, width - (centerXOf("Altro") + halfLens))
    assertDp("fra due vetri", 8.dp, centerXOf("Altro") - centerXOf("Cerca") - FluidMetrics.GlassButtonLens)
  }

  @Test
  fun `la capsula da' 26 dp alle sue azioni, la tab bar 28 alle schede`() {
    compose.setContent {
      Themed {
        Column {
          FluidFloatingCapsule {
            FluidCapsuleAction(FluidIcons.Search, "Cerca nella capsula", onClick = {})
            FluidIcon(FluidIcons.Home, "icona nella capsula")
          }
          // Fuori dalla capsula un'azione resta a 24, come prima (il campo di ricerca la usa cosi').
          FluidCapsuleAction(FluidIcons.Search, "azione sciolta", onClick = {})
          FluidFloatingTabBar(
            items = listOf(
              FluidTabItem("home", "Home", FluidIcons.Home, filledIcon = FluidIcons.Filled.Home),
              FluidTabItem("search", "Cerca", FluidIcons.Search),
            ),
            selectedRoute = "home",
            onSelect = {},
            scrollConnection = rememberFluidFloatingTabBarScrollConnection(inlineBehavior = FluidFloatingTabBarInlineBehavior.Never),
            modifier = Modifier.testTag("barra"),
          )
          // Una scheda scritta dall'app con FluidIcon senza taglia: la barra le da' 28 dp.
          FluidFloatingTabBar(
            selectedTabKey = "a",
            scrollConnection = rememberFluidFloatingTabBarScrollConnection(inlineBehavior = FluidFloatingTabBarInlineBehavior.Never),
          ) {
            tab(key = "a", title = { Text("A") }, icon = { FluidIcon(FluidIcons.Home, "scheda A") }, onClick = {})
            tab(key = "b", title = { Text("B") }, icon = { FluidIcon(FluidIcons.Search, "scheda B") }, onClick = {})
          }
        }
      }
    }
    compose.waitForIdle()
    assertDp("azione della capsula", FluidMetrics.CapsuleIconSize, widthOf("Cerca nella capsula"))
    assertDp("icona nella capsula", FluidMetrics.CapsuleIconSize, widthOf("icona nella capsula"))
    assertDp("azione sciolta", 24.dp, widthOf("azione sciolta"))
    val bar = compose.onNodeWithTag("barra").getUnclippedBoundsInRoot()
    assertTrue("la barra e' alta ${bar.height}", bar.height >= FluidMetrics.TabBarHeight - 0.5.dp)
    // La scheda non scelta: 28 dp anche lei (la barra puo' comporre piu' copie della riga: si guarda la prima).
    val tab = compose.onAllNodesWithContentDescription("scheda B", useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue("la scheda B non c'e'", tab.isNotEmpty())
    val tabWidth = with(compose.density) { tab.first().boundsInRoot.width.toDp() }
    assertDp("icona della scheda", FluidMetrics.TabBarIconSize, tabWidth)
  }
}
