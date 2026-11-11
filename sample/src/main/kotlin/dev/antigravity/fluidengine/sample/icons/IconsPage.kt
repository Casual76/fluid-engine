package dev.antigravity.fluidengine.sample.icons

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarInlineBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/** L'extra che mostra una sezione sola: `--es icons.section sizes|chrome|rtl|set`. Solo nella build di debug. */
internal const val IconsSectionExtra = "icons.section"

/** Le sezioni della pagina «Icone», ognuna una voce del backlog del dispositivo di F1A.11. */
internal enum class IconsSection(val key: String, val title: String, val detail: String) {
  Sizes(
    "sizes",
    "Taglie sul vetro",
    "Lo stesso tasto da 44 dp con l'icona a 20, 22, 24, 26 e 28 dp, nei due temi. 26 e' la taglia di serie dalla 4.0 " +
      "(0,6 del vetro); prima era 22, nell'accento.",
  ),
  Chrome(
    "chrome",
    "Barra, capsula, tab bar, menu",
    "I componenti veri con le misure nuove: tasti a 16 dp dal bordo e 8 fra due vetri, icone a 26 (freccia 24) " +
      "nell'inchiostro del testo, tab bar alta 62 con icone da 28 piene di inchiostro. «Altro» apre il menu (icone a 22).",
  ),
  Rtl(
    "rtl",
    "Da destra a sinistra",
    "La stessa barra specchiata: la freccia si gira da sola, i margini passano dall'altra parte. Sotto, le icone del " +
      "set che si girano (↔) accanto alla loro forma da sinistra a destra.",
  ),
  Set(
    "set",
    "Il set",
    "FluidIcons: Material Symbols arrotondate, peso 500. A sinistra al tratto, a destra la gemella piena se c'e' " +
      "(per la scheda scelta della tab bar); ↔ si gira da destra a sinistra.",
  ),
  ;

  companion object {
    /** La sezione chiesta dall'extra, o null per tutte (anche per un valore che non c'e'). */
    fun parse(value: String?): IconsSection? = entries.firstOrNull { it.key == value?.trim()?.lowercase() }
  }
}

/** Le taglie della prima sezione: quella di prima (22), quella di serie (26) e i vicini. */
internal val IconsSizeSamples: List<Dp> = listOf(20.dp, 22.dp, 24.dp, 26.dp, 28.dp)

/** Le icone del set che si girano da destra a sinistra, per la sezione della specchiatura. */
private val MirroredSamples get() = listOf(
  FluidIcons.ArrowBackIosNew, FluidIcons.ArrowForward, FluidIcons.Send, FluidIcons.Undo, FluidIcons.OpenInNew,
)

/**
 * La pagina «Icone» (piano «iOS in Android», pacchetto F1A.11).
 *
 * Esiste per giudicare in mano le icone nuove: Alessio ha chiesto icone «un po' piu' grassettate, come Apple» e tasti
 * forse piu' grandi, e la risposta dell'engine (R6) e' un set al tratto a peso 500, icone piu' grandi e
 * nell'inchiostro del testo, tasti piu' lontani dal bordo. Qui ci sono le taglie una accanto all'altra sul vetro vero,
 * i componenti veri con le misure nuove nei due temi, la specchiatura e tutto il set.
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page icons --es icons.section chrome
 *
 * Senza `icons.section` (o con un valore che non c'e') mostra tutte le sezioni.
 */
@Composable
internal fun IconsPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val only = remember(context) {
    if (context.isDebuggableApp()) IconsSection.parse(context.findActivity()?.intent?.getStringExtra(IconsSectionExtra)) else null
  }
  FluidScreen(
    title = "Icone",
    subtitle = "Il set dell'engine e le taglie nuove (R6). Una sezione sola: --es icons.section sizes|chrome|rtl|set.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    modifier = Modifier.testTag("icons-page"),
  ) {
    iconsPageContent(only)
  }
}

/** Il contenuto della pagina, sezione per sezione: [only] null le mette tutte. */
internal fun LazyListScope.iconsPageContent(only: IconsSection?) {
  fun shows(section: IconsSection) = only == null || only == section
  fun header(section: IconsSection) {
    item(key = "icons-header-${section.key}") { FluidSectionHeader(title = section.title, detail = section.detail) }
  }
  if (shows(IconsSection.Sizes)) {
    header(IconsSection.Sizes)
    for (dark in listOf(false, true)) {
      item(key = "icons-sizes-${side(dark)}") { ThemedPatch(dark) { SizesStage(dark) } }
    }
  }
  if (shows(IconsSection.Chrome)) {
    header(IconsSection.Chrome)
    for (dark in listOf(false, true)) {
      item(key = "icons-chrome-${side(dark)}") { ThemedPatch(dark) { ChromeStages(dark) } }
    }
  }
  if (shows(IconsSection.Rtl)) {
    header(IconsSection.Rtl)
    item(key = "icons-rtl") { RtlStages() }
  }
  if (shows(IconsSection.Set)) {
    header(IconsSection.Set)
    itemsIndexed(IconsCatalog.chunked(SetColumns), key = { index, _ -> "icons-set-$index" }) { index, row ->
      SetRow(row, Modifier.testTag("icons-set-$index"))
    }
  }
}

private const val SetColumns = 3

private fun side(dark: Boolean) = if (dark) "dark" else "light"

/** Un pezzo della pagina nel tema chiesto, qualunque sia quello della galleria. */
@Composable
private fun ThemedPatch(dark: Boolean, content: @Composable () -> Unit) {
  val settings = remember(dark) { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
  FluidTheme(settings = settings, systemBars = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = if (dark) "Tema scuro" else "Tema chiaro",
        style = MaterialTheme.typography.labelLarge,
      )
      content()
    }
  }
}

/**
 * Il palco: la pagina del tema (il fondo vero su cui stanno le barre), registrata come cio' che il vetro rifrange, e
 * sopra i componenti. Niente foto ne' righe di testo: si giudica il peso delle icone, sul fondo dove lo si vede di piu'.
 */
@Composable
private fun IconStage(
  height: Dp,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.(GlassBackdropState) -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  val page = MaterialTheme.colorScheme.background
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(ContinuousCornerShape(FluidRadius.Group)),
  ) {
    Box(Modifier.matchParentSize().glassBackdropSource(backdrop).background(page))
    CompositionLocalProvider(
      LocalGlassBackdrop provides backdrop,
      LocalFluidCanvasBackdrop provides backdrop,
      LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
      content(backdrop)
    }
  }
}

// --- Taglie ---------------------------------------------------------------------------------------------------

@Composable
private fun SizesStage(dark: Boolean) {
  IconStage(height = 214.dp, modifier = Modifier.testTag("icons-sizes-${side(dark)}")) { backdrop ->
    Column(
      modifier = Modifier.align(Alignment.Center),
      verticalArrangement = Arrangement.spacedBy(2.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      for (icon in listOf(FluidIcons.Share, FluidIcons.Search, FluidIcons.MoreHoriz)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SizeColumnGap)) {
          IconsSizeSamples.forEach { size ->
            FluidGlassIconButton(onClick = {}, backdrop = backdrop) {
              FluidIcon(icon, contentDescription = null, size = size)
            }
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(SizeColumnGap)) {
        IconsSizeSamples.forEach { size ->
          Text(
            text = if (size == FluidMetrics.BarIconSize) "${size.value.toInt()} ●" else "${size.value.toInt()}",
            modifier = Modifier.width(FluidMetrics.TouchTarget),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

/** Fra un tasto e l'altro della tavola delle taglie: gli 8 dp fra due vetri, meno l'aria dei riquadri. */
private val SizeColumnGap = 4.dp

// --- Barra, capsula, tab bar, menu ---------------------------------------------------------------------------------

/** Le voci del menu di «Altro»: icone del set a 22 dp nelle righe, una distruttiva in una sezione sua. */
private val MenuActions: List<FluidContextAction>
  get() = listOf(
    FluidContextAction("Condividi", FluidIcons.Share) {},
    FluidContextAction("Duplica", FluidIcons.ContentCopy) {},
    FluidContextAction("Sposta", FluidIcons.DriveFileMove) {},
    FluidContextAction("Elimina", FluidIcons.Delete, destructive = true, startsSection = true) {},
  )

@Composable
private fun ChromeStages(dark: Boolean) {
  val tag = side(dark)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // La barra di serie: indietro e due azioni, la seconda apre il menu.
    BarStage(Modifier.testTag("icons-bar-$tag")) { backdrop ->
      FluidPageChrome(title = "Note", onBack = {}, backdrop = backdrop) {
        FluidBarAction(icon = FluidIcons.Share, contentDescription = "Condividi", onClick = {})
        FluidBarAction(
          icon = FluidIcons.MoreHoriz,
          contentDescription = "Altro",
          onClick = {},
          actions = { MenuActions },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.testTag("icons-bar-more-$tag"),
        )
      }
    }
    Caption("Un tasto colorato resta possibile, e non e' di serie: l'accento sull'azione principale.")
    BarStage(Modifier.testTag("icons-bar-tinted-$tag")) { backdrop ->
      FluidPageChrome(title = "Bozza", onBack = {}, backdrop = backdrop) {
        FluidBarAction(icon = FluidIcons.Edit, contentDescription = "Modifica", onClick = {})
        FluidBarAction(
          icon = FluidIcons.Send,
          contentDescription = "Invia",
          onClick = {},
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
    // La capsula flottante: tre azioni di serie, una accesa (accento e disco) e una distruttiva.
    IconStage(height = FluidMetrics.TabBarHeight + 32.dp, modifier = Modifier.testTag("icons-capsule-$tag")) { backdrop ->
      FluidFloatingCapsule(modifier = Modifier.align(Alignment.Center), backdrop = backdrop) {
        FluidCapsuleAction(FluidIcons.Share, "Condividi", onClick = {})
        FluidCapsuleAction(FluidIcons.Favorite, "Preferito", onClick = {})
        FluidCapsuleAction(FluidIcons.FormatBold, "Grassetto", onClick = {}, selected = true)
        FluidCapsuleAction(FluidIcons.Delete, "Elimina", onClick = {}, destructive = true)
      }
    }
    // La tab bar: la scheda scelta con la gemella piena, le altre al tratto e a inchiostro pieno.
    IconStage(height = FluidMetrics.TabBarHeight + 40.dp, modifier = Modifier.testTag("icons-tabbar-$tag")) { backdrop ->
      var route by remember { mutableStateOf("home") }
      FluidFloatingTabBar(
        items = TabItems,
        selectedRoute = route,
        onSelect = { route = it.route },
        scrollConnection = rememberFluidFloatingTabBarScrollConnection(inlineBehavior = FluidFloatingTabBarInlineBehavior.Never),
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
      )
    }
  }
}

/** Tre schede con la gemella piena: Home, Cerca, Impostazioni. */
private val TabItems: List<FluidTabItem>
  get() = listOf(
    FluidTabItem("home", "Home", FluidIcons.Home, selectedIcon = FluidIcons.Filled.Home),
    FluidTabItem("search", "Cerca", FluidIcons.Search, selectedIcon = FluidIcons.Filled.Search),
    FluidTabItem("settings", "Impostazioni", FluidIcons.Settings, selectedIcon = FluidIcons.Filled.Settings),
  )

/** Un palco alto quanto la barra in alto di uno schermo: la barra di stato, la riga da 44 dp e la coda del vetro. */
@Composable
private fun BarStage(modifier: Modifier = Modifier, content: @Composable BoxScope.(GlassBackdropState) -> Unit) {
  IconStage(
    height = FluidScreenDefaults.topBarHeight() + FluidScreenDefaults.GlassFadeTail,
    modifier = modifier,
    content = content,
  )
}

@Composable
private fun Caption(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

// --- Da destra a sinistra ---------------------------------------------------------------------------------------

@Composable
private fun RtlStages() {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
      val tag = if (direction == LayoutDirection.Rtl) "rtl" else "ltr"
      Caption(if (direction == LayoutDirection.Rtl) "Da destra a sinistra" else "Da sinistra a destra")
      CompositionLocalProvider(LocalLayoutDirection provides direction) {
        BarStage(Modifier.testTag("icons-rtl-bar-$tag")) { backdrop ->
          FluidPageChrome(title = "Note", onBack = {}, backdrop = backdrop) {
            FluidBarAction(icon = FluidIcons.Share, contentDescription = "Condividi", onClick = {})
            FluidBarAction(icon = FluidIcons.Send, contentDescription = "Invia", onClick = {})
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth().testTag("icons-rtl-row-$tag"),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          MirroredSamples.forEach { FluidIcon(it, contentDescription = null, size = FluidMetrics.BarIconSize) }
        }
      }
    }
  }
}

// --- Il set ---------------------------------------------------------------------------------------------------------

@Composable
private fun SetRow(row: List<IconsCatalogEntry>, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    row.forEach { entry -> SetCell(entry, Modifier.weight(1f)) }
    repeat(SetColumns - row.size) { Box(Modifier.weight(1f)) }
  }
}

@Composable
private fun SetCell(entry: IconsCatalogEntry, modifier: Modifier = Modifier) {
  val outline = entry.outline()
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      FluidIcon(outline, contentDescription = null, size = FluidMetrics.BarIconSize)
      entry.filled?.let { filled -> FluidIcon(filled(), contentDescription = null, size = FluidMetrics.BarIconSize) }
    }
    Text(
      text = if (outline.autoMirror) "${entry.name} ↔" else entry.name,
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = entry.symbol,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
