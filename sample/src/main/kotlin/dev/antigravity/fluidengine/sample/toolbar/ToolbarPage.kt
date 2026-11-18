package dev.antigravity.fluidengine.sample.toolbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.sample.GalleryLaunch
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.FormatUnderlined
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.FormatListBulleted
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidButtonRole
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPriority
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.delay

/** Il tag di logcat della pagina: `adb logcat -s FluidBarre`. */
internal const val ToolbarLogTag = "FluidBarre"

/**
 * Le scene della pagina «Barre (prova)» (piano «iOS in Android», F4A.2): una barra dichiarata per scena, che cambia
 * **solo lo stato** ([ToolbarScene.toolbar] con `alt` falso o vero). Gruppi, vetri, nascite, confluenze, icone che si
 * incrociano e trabocco li decide la barra; la pagina non scrive animazioni. La galleria «Barre» vera è F4A.5: questa è
 * la prova minima da riprendere sul tablet (`--es gallery.page toolbar --es toolbar.scene … --es toolbar.auto true`).
 */
internal enum class ToolbarScene(val key: String, val title: String, val voice: String) {
  BrowseSelect(
    "sfoglia-seleziona", "Sfoglia → Seleziona",
    "«Seleziona» e i tre puntini diventano condividi ed elimina in un vetro e la spunta tinta, sola a destra; l'indietro resta",
  ),
  Primary(
    "principale", "Un tasto diventa principale",
    "tre icone in un vetro; «Preferito» diventa l'azione principale: il gruppo si divide e lui prende la tinta",
  ),
  Overflow(
    "trabocco", "Tasti nel trabocco",
    "arrivano due gruppi in più: i tasti a priorità bassa escono nel menu «Altro» (i tre puntini) e ritornano; il " +
      "preferito sta sempre nel menu",
  ),
  Tint(
    "tinta", "Selezione per tasto e tinta accanto a un gruppo",
    "un gruppo di formato con un tasto selezionato (la pellicola si sposta da Corsivo a Grassetto, gli altri restano " +
      "neutri) e la conferma tinta a 12 dp: due tinte nello stesso grappolo",
  ),
  Icons(
    "icone", "Due tasti cambiano icona",
    "stessi id, icone nuove: il vetro non si muove, le icone si incrociano sfocandosi",
  ),
  Minimize(
    "ritiro", "Barra che si ritira",
    "Scorri le note e inverti il verso. Il campo resta sotto la barra di stato.",
  ),
  ApplePhone(
    "telefono-apple", "Telefono Apple",
    "Elenco diventa Cerca. Il gruppo con cuore, Condividi e più si chiude sul più.",
  ),
  TravellingTarget(
    "bersaglio-in-viaggio", "Bersaglio in viaggio",
    "Cambia stato e tocca Corsivo mentre si sposta. Il log FluidBarre indica quale comando riceve il tocco.",
  ),
  ;

  fun next(): ToolbarScene = entries[(ordinal + 1) % entries.size]

  /** La barra della scena nello stato [alt]; [toggle] è il comando, per chi lo vuole su un tasto della barra. */
  fun toolbar(
    alt: Boolean,
    toggle: () -> Unit,
    command: (String) -> Unit = { Log.i(ToolbarLogTag, "COMANDO $it") },
  ): FluidToolbarScope.() -> Unit = when (this) {
    BrowseSelect -> {
      {
        if (alt) {
          button("share", FluidIcons.IosShare, "Condividi", onClick = {})
          button("delete", FluidIcons.Delete, "Elimina", onClick = {}, role = FluidButtonRole.Destructive)
          confirm(onClick = toggle)
        } else {
          text("select", "Seleziona", onClick = toggle)
          button("more", FluidIcons.MoreHoriz, "Opzioni", onClick = {})
        }
      }
    }
    Primary -> {
      {
        button("edit", FluidIcons.Edit, "Modifica", onClick = {})
        button("share", FluidIcons.IosShare, "Condividi", onClick = {})
        if (alt) primary("star", FluidIcons.Star, "Preferito", onClick = toggle) else button("star", FluidIcons.Star, "Preferito", onClick = toggle)
      }
    }
    Overflow -> {
      {
        // Il cambio viene dalla riga «Cambia stato»: un tasto della barra che finisse nel menu non si toccherebbe più.
        if (alt) {
          button("edit", FluidIcons.Edit, "Modifica", onClick = {}, priority = FluidToolbarPriority.Low)
          spacer()
          button("archive", FluidIcons.Archive, "Archivia", onClick = {}, priority = FluidToolbarPriority.Low)
          spacer()
        }
        button("share", FluidIcons.IosShare, "Condividi", onClick = {}, priority = FluidToolbarPriority.High)
        button("search", FluidIcons.Search, "Cerca", onClick = {})
        overflow { button("fav", FluidIcons.Favorite, "Aggiungi ai preferiti", onClick = {}) }
      }
    }
    Tint -> {
      {
        group("format") {
          button("bold", FluidIcons.FormatBold, "Grassetto", onClick = toggle, selected = alt)
          button("italic", FluidIcons.FormatItalic, "Corsivo", onClick = toggle, selected = !alt)
          button("underline", FluidIcons.FormatUnderlined, "Sottolineato", onClick = {})
        }
        confirm(onClick = {})
      }
    }
    Icons -> {
      {
        button("one", if (alt) FluidIcons.Star else FluidIcons.Favorite, if (alt) "Stella" else "Cuore", onClick = toggle)
        button("two", if (alt) FluidIcons.Archive else FluidIcons.Edit, if (alt) "Archivia" else "Modifica", onClick = {})
      }
    }
    TravellingTarget -> {
      {
        button("a", FluidIcons.FormatBold, "Grassetto", onClick = { command("a") })
        if (alt) spacer()
        button("b", FluidIcons.FormatItalic, "Corsivo", onClick = { command("b") })
        if (alt) button("c", FluidIcons.IosShare, "Condividi", onClick = { command("c") })
      }
    }
    ApplePhone -> {
      {
        button("leading", if (alt) FluidIcons.Search else FluidIcons.FormatListBulleted,
          if (alt) "Cerca" else "Elenco", onClick = toggle, placement = FluidToolbarPlacement.Leading)
        if (!alt) {
          button("heart", FluidIcons.Favorite, "Preferito", onClick = {})
          button("share", FluidIcons.IosShare, "Condividi", onClick = {})
        }
        button("add", FluidIcons.Add, "Aggiungi", onClick = toggle)
      }
    }
    Minimize -> {
      { button("more", FluidIcons.MoreHoriz, "Opzioni", onClick = {}) }
    }
  }
}

/** Gli extra di lancio della pagina (solo nella build di debug), come quelli del piano flottante. */
internal object ToolbarLaunch {
  const val ExtraScene = "toolbar.scene"
  const val ExtraAuto = "toolbar.auto"

  /** L'intervallo dell'automatico quando si chiede solo «acceso»: il viaggio più lungo (~900 ms) e un attimo fermo. */
  const val DefaultAutoMs = 2_000L

  private val truthy = setOf("true", "1", "on", "yes", "si", "sì", "acceso")

  /** La scena e l'automatico chiesti; un valore sconosciuto lascia quello di serie. */
  fun parse(extra: (String) -> String?): Pair<ToolbarScene, Long> {
    fun value(key: String): String? = extra(key)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val scene = value(ExtraScene)?.let { name ->
      ToolbarScene.entries.firstOrNull { it.key == name || it.name.lowercase() == name }
    } ?: ToolbarScene.BrowseSelect
    val auto = value(ExtraAuto)?.let { raw -> raw.toLongOrNull()?.takeIf { it > 0L } ?: if (raw in truthy) DefaultAutoMs else 0L } ?: 0L
    return scene to auto
  }
}

/**
 * La pagina «Barre (prova)» (F4A.2): una `FluidScreen` con la barra dichiarata della scena, e sotto i comandi (cambia
 * stato, scena dopo, automatico). In logcat (`adb logcat -s FluidBarre`) scrive `SCENA` e `STATO` a ogni cambio.
 * Ferma non chiede fotogrammi: lo stato cambia solo dal comando o dall'automatico.
 */
@Composable
internal fun ToolbarPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val launched = remember(context) {
    val activity = context.findActivity()
    if (context.isDebuggableApp() && activity != null) {
      ToolbarLaunch.parse { activity.intent?.getStringExtra(it) }
    } else {
      ToolbarScene.BrowseSelect to 0L
    }
  }
  ToolbarDemoPolicy {
    ToolbarContent(launched.first, launched.second, bottomInset, onBack)
  }
}

/** Le due prove leggono gli assi di lancio senza entrare nell'elenco dei componenti nuovi della matrice. */
@Composable
internal fun ToolbarDemoPolicy(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val launch = remember(context) {
    val activity = context.findActivity()
    if (context.isDebuggableApp() && activity != null) {
      GalleryLaunch.parse { activity.intent?.getStringExtra(it) }
    } else {
      GalleryLaunch.None
    }
  }
  val density = LocalDensity.current
  val policy = LocalFluidMotionPolicy.current
  if (launch.textScale == null && launch.reducedMotion != true) {
    content()
    return
  }
  CompositionLocalProvider(
    LocalDensity provides (launch.textScale?.let { Density(density.density, it.scale) } ?: density),
    LocalFluidMotionPolicy provides (if (launch.reducedMotion == true) FluidMotionPolicy(0f) else policy),
    content = content,
  )
}

/** La pagina senza la lettura degli extra: è ciò che apre il test. */
@Composable
internal fun ToolbarContent(initial: ToolbarScene, autoMs: Long, bottomInset: Dp, onBack: () -> Unit) {
  var scene by remember { mutableStateOf(initial) }
  var alt by remember { mutableStateOf(false) }
  var auto by remember { mutableLongStateOf(autoMs) }
  var query by remember { mutableStateOf("") }
  val toggle: () -> Unit = remember { { alt = !alt } }
  val scale = LocalFluidMotionPolicy.current.durationScale
  LaunchedEffect(scene) { Log.i(ToolbarLogTag, "SCENA ${scene.key}") }
  LaunchedEffect(scene, alt) { Log.i(ToolbarLogTag, "STATO ${scene.key} alt=$alt t=${android.os.SystemClock.uptimeMillis()}") }
  LaunchedEffect(scene, auto, scale) {
    if (auto <= 0L) return@LaunchedEffect
    val wait = (auto * scale.coerceAtLeast(1f)).toLong()
    while (true) {
      delay(wait)
      alt = !alt
    }
  }
  FluidScreen(
    title = if (scene == ToolbarScene.Minimize) "Note" else "Barre",
    subtitle = if (scene == ToolbarScene.Minimize) null else "F4A.2 · ${scene.title}",
    modifier = Modifier.testTag("toolbar-page"),
    onBack = onBack.takeUnless { scene == ToolbarScene.ApplePhone },
    extraBottomPadding = bottomInset,
    toolbar = scene.toolbar(alt, toggle),
    search = if (scene == ToolbarScene.Minimize) FluidSearchSpec(
      query = query,
      onQueryChange = { query = it },
      placement = FluidSearchPlacement.Principal,
      prompt = "Cerca nelle note",
    ) else null,
  ) {
    item(key = "voice") {
      Text(
        text = scene.voice,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
      )
    }
    item(key = "commands") { FluidSectionHeader(title = "Comandi", detail = "La pagina cambia solo lo stato: il resto è della barra.") }
    item(key = "toggle") {
      FluidListRow(
        title = "Cambia stato",
        subtitle = if (alt) "Stato B" else "Stato A",
        modifier = Modifier.testTag("toolbar-toggle"),
        onClick = toggle,
      )
    }
    item(key = "scene") {
      FluidListRow(
        title = "Scena: ${scene.title}",
        subtitle = "Tocca per la prossima (${scene.next().title})",
        modifier = Modifier.testTag("toolbar-scene"),
        onClick = {
          scene = scene.next()
          alt = false
        },
      )
    }
    item(key = "auto") {
      FluidListRow(
        title = "Automatico: ${if (auto > 0L) "$auto ms" else "spento"}",
        subtitle = "Cambia stato da solo, per le riprese (`toolbar.auto`)",
        modifier = Modifier.testTag("toolbar-auto"),
        onClick = { auto = if (auto > 0L) 0L else ToolbarLaunch.DefaultAutoMs },
      )
    }
    // Abbastanza righe da far scorrere la pagina sotto la barra: il titolo si aggancia e i gruppi restano fermi.
    val notes = (1..24).filter { query.isBlank() || "Nota $it".contains(query.trim(), ignoreCase = true) }
    items(count = notes.size, key = { "filler-${notes[it]}" }) { index ->
      FluidListRow(title = "Nota ${notes[index]}", subtitle = "Una riga per far scorrere la pagina sotto la barra")
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
