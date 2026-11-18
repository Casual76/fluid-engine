package dev.antigravity.fluidengine.sample.toolbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import androidx.compose.runtime.saveable.rememberSaveable
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchExpansion
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidButtonRole
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarIds
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.page.FluidPageBarDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPeerMove
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.delay

/**
 * Le pagine della prova «Barre fra pagine» (piano «iOS in Android», F4A.3): un host con due schede pari (Note, Cartelle)
 * e una catena di quattro pagine, ognuna con la sua barra **dichiarata**. Ogni passaggio è uno degli esempi di Alessio
 * (2026-11-11): i tasti **restano al loro posto e si trasformano** in quelli della pagina d'arrivo, sul tempo del piano.
 * Le pagine non scrivono animazioni: dicono solo chi sono i loro tasti.
 */
internal enum class ToolbarHostPage(val key: String, val title: String, val voice: String) {
  Notes(
    "note", "Note",
    "Un tasto solo, i tre puntini, e nessun indietro. Apri «Nota»: i tre puntini restano fermi e nitidi, il secondo " +
      "tasto nasce dal loro fianco col collo (esempio 1); l'indietro compare per lente sul posto (R7 21).",
  ),
  Folders(
    "cartelle", "Cartelle",
    "La scheda accanto a «Note», con altri tasti in alto: il cambio di scheda è un cambio di stato, sul posto e senza " +
      "verso (D4). I tre puntini di «Note» diventano il «più» nello stesso vetro.",
  ),
  Note(
    "nota", "Nota",
    "Due tasti separati. Torna indietro col gesto: finché il dito tiene la pagina la barra resta questa, al rilascio " +
      "che conferma si trasforma, all'annullo non succede niente (esempio 3, D1).",
  ),
  Attachment(
    "allegato", "Allegato",
    "Gli stessi due tasti (stessi id) con icone nuove: i vetri non si muovono, le icone si incrociano sfocandosi " +
      "(esempio 2). L'indietro, comune, resta fermo al pixel.",
  ),
  Group(
    "gruppo", "Gruppo",
    "I tasti collegati: i due vetri si fondono in uno solo con un terzo tasto in mezzo (esempio 5); le icone che " +
      "restano non si sfocano.",
  ),
  Edit(
    "modifica", "Modifica",
    "Le combinazioni (esempio 4): Chiudi al posto dell'indietro nello stesso vetro (stesso id), un tasto di testo, la " +
      "conferma tinta, sola a destra, e il trabocco «Altro» con le azioni nascoste.",
  ),
  Legacy(
    "vecchia", "Pagina di prima",
    "Una pagina con lo slot `actions` di sempre accanto a una dichiarata: il suo indietro è nel modello (compare per " +
      "lente e resta fermo), la sua azione entra ed esce in dissolvenza con la corsa, come prima di F4A.3.",
  ),
  Search(
    "ricerca", "Ricerca",
    "La barra dichiarata con la ricerca principale (giro di correzione di F4A, B1). Scorri: titolo, tasti e indietro si " +
      "ritirano insieme, il campo resta sotto la barra di stato e si tocca.",
  ),
  LegacySearch(
    "ricerca-vecchia", "Ricerca di prima",
    "Lo slot `actions` di sempre con la ricerca principale (C8). Scorri: l'indietro, l'azione e il titolo si ritirano " +
      "insieme, il campo resta e si tocca.",
  ),
  LegacyRead(
    "lettura", "Lettura di prima",
    "Lo slot `actions` di sempre che si ritira scorrendo verso il fondo, senza campo (C8, `OnScrollDown`): l'indietro e " +
      "l'azione si ritirano insieme e tornano risalendo.",
  ),
  ;

  /** La pagina che si apre da questa, o null alla fine della catena. */
  val next: ToolbarHostPage? get() = when (this) {
    Notes -> Note
    Folders -> Legacy
    Note -> Attachment
    Attachment -> Group
    Group -> Edit
    Legacy -> LegacyRead
    Edit, Search, LegacySearch, LegacyRead -> null
  }

  /** La pagina con la ricerca che si apre da una scheda: quella dichiarata da «Note», quella di prima da «Cartelle». */
  val search: ToolbarHostPage? get() = when (this) {
    Notes -> Search
    Folders -> LegacySearch
    else -> null
  }

  /** Se la pagina dichiara la ricerca principale (il campo resta sotto la barra di stato quando la barra si ritira). */
  val hasSearch: Boolean get() = this == Search || this == LegacySearch

  /** Il ritiro della barra: quello di serie (che con la ricerca principale ritira), o verso il fondo per la lettura. */
  val minimize: FluidBarMinimizeBehavior get() =
    if (this == LegacyRead) FluidBarMinimizeBehavior.OnScrollDown else FluidBarMinimizeBehavior.Automatic

  /** Le due schede pari: un passo fra loro è un cambio di scheda, non un push. */
  val isTab: Boolean get() = this == Notes || this == Folders

  /**
   * La barra dichiarata della pagina, o null per la pagina di prima (che ha lo slot `actions`). [close] chiude la pagina
   * (Chiudi e la conferma nella pagina di modifica).
   */
  fun toolbar(close: () -> Unit): (FluidToolbarScope.() -> Unit)? = when (this) {
    Legacy, LegacySearch, LegacyRead -> null
    Search -> {
      { button("trail", FluidIcons.MoreHoriz, "Opzioni", onClick = {}, placement = FluidToolbarPlacement.PinnedTrailing) }
    }
    Notes -> {
      { button("trail", FluidIcons.MoreHoriz, "Opzioni", onClick = {}) }
    }
    Folders -> {
      { button("trail", FluidIcons.Add, "Nuova cartella", onClick = {}) }
    }
    Note -> {
      {
        button("lead", FluidIcons.IosShare, "Condividi", onClick = {})
        spacer()
        button("trail", FluidIcons.MoreHoriz, "Opzioni", onClick = {})
      }
    }
    Attachment -> {
      {
        button("lead", FluidIcons.Edit, "Modifica", onClick = {})
        spacer()
        button("trail", FluidIcons.Favorite, "Preferito", onClick = {})
      }
    }
    Group -> {
      {
        button("lead", FluidIcons.Edit, "Modifica", onClick = {})
        button("mid", FluidIcons.IosShare, "Condividi", onClick = {})
        button("trail", FluidIcons.Favorite, "Preferito", onClick = {})
      }
    }
    Edit -> {
      {
        // Chiudi con l'identità dell'indietro: lo stesso vetro, la X che si incrocia col chevron (matrice di F4A.1).
        close(onClick = close, id = FluidToolbarIds.Back)
        text("draft", "Bozza", onClick = {})
        confirm(onClick = close)
        overflow {
          button("archive", FluidIcons.Archive, "Archivia", onClick = {})
          button("delete", FluidIcons.Delete, "Elimina", onClick = {}, role = FluidButtonRole.Destructive)
        }
      }
    }
  }
}

/**
 * Il copione dell'automatico (`pages.auto`): la catena in avanti fino a «Modifica», di nuovo indietro con i tasti, poi
 * la scheda accanto e ritorno. Un passo per intervallo, così ogni passaggio si riprende da fermo.
 */
internal val ToolbarPagesScript: List<List<ToolbarHostPage>> = listOf(
  listOf(ToolbarHostPage.Notes),
  listOf(ToolbarHostPage.Notes, ToolbarHostPage.Note),
  listOf(ToolbarHostPage.Notes, ToolbarHostPage.Note, ToolbarHostPage.Attachment),
  listOf(ToolbarHostPage.Notes, ToolbarHostPage.Note, ToolbarHostPage.Attachment, ToolbarHostPage.Group),
  listOf(ToolbarHostPage.Notes, ToolbarHostPage.Note, ToolbarHostPage.Attachment, ToolbarHostPage.Group, ToolbarHostPage.Edit),
  listOf(ToolbarHostPage.Notes, ToolbarHostPage.Note, ToolbarHostPage.Attachment, ToolbarHostPage.Group),
  listOf(ToolbarHostPage.Notes, ToolbarHostPage.Note, ToolbarHostPage.Attachment),
  listOf(ToolbarHostPage.Notes, ToolbarHostPage.Note),
  listOf(ToolbarHostPage.Notes),
  listOf(ToolbarHostPage.Folders),
  listOf(ToolbarHostPage.Folders, ToolbarHostPage.Legacy),
  listOf(ToolbarHostPage.Folders),
)

/** Gli extra di lancio della prova (solo nella build di debug). */
internal object ToolbarPagesLaunch {
  /** L'automatico: `true` (l'intervallo di serie) o i millisecondi fra due passi. */
  const val ExtraAuto = "pages.auto"

  /** `true`: la variante del gesto indietro che segue il dito ([FluidPageBarDiagnostics.gestureFollowsFinger]). */
  const val ExtraFollow = "pages.follow"

  /**
   * La pagina da cui partire (la catena fino a lei è già aperta): `nota`, `allegato`, `gruppo`, `modifica`, `cartelle`,
   * `vecchia`, `ricerca`, `ricerca-vecchia`, `lettura`; oppure `sola`, un host di sole pagine di prima (mai una barra
   * dichiarata: la barra dell'host resta quella di sempre) con «Ricerca di prima» sopra «Lettura di prima».
   */
  const val ExtraStart = "pages.start"

  /** Il valore di [ExtraStart] per l'host di sole pagine di prima. */
  const val StartLegacyOnly = "sola"

  /** Il viaggio più lungo del piano (~900 ms) e un attimo fermo, più la molla della pagina. */
  const val DefaultAutoMs = 1_800L

  private val truthy = setOf("true", "1", "on", "yes", "si", "sì", "acceso")

  data class Request(val start: ToolbarHostPage, val autoMs: Long, val follow: Boolean, val legacyOnly: Boolean = false, val uniform: Boolean = false)

  fun parse(extra: (String) -> String?): Request {
    fun value(key: String): String? = extra(key)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val startName = value(ExtraStart)
    val legacyOnly = startName == StartLegacyOnly
    val start = if (legacyOnly) {
      ToolbarHostPage.LegacySearch
    } else {
      startName?.let { name -> ToolbarHostPage.entries.firstOrNull { it.key == name || it.name.lowercase() == name } }
        ?: ToolbarHostPage.Notes
    }
    val auto = value(ExtraAuto)?.let { raw -> raw.toLongOrNull()?.takeIf { it > 0L } ?: if (raw in truthy) DefaultAutoMs else 0L } ?: 0L
    val follow = value(ExtraFollow)?.let { it in truthy } ?: false
    return Request(start, auto, follow, legacyOnly, value("pages.uniform") in truthy)
  }

  /** La pila che porta a [page]: la scheda, poi la catena fino a lei. */
  fun stackTo(page: ToolbarHostPage, legacyOnly: Boolean = false): List<ToolbarHostPage> {
    if (legacyOnly) return listOf(ToolbarHostPage.LegacyRead, ToolbarHostPage.LegacySearch)
    if (page.isTab) return listOf(page)
    if (page == ToolbarHostPage.Legacy) return listOf(ToolbarHostPage.Folders, page)
    if (page == ToolbarHostPage.LegacyRead) return listOf(ToolbarHostPage.Folders, ToolbarHostPage.Legacy, page)
    if (page == ToolbarHostPage.Search) return listOf(ToolbarHostPage.Notes, page)
    if (page == ToolbarHostPage.LegacySearch) return listOf(ToolbarHostPage.Folders, page)
    val chain = generateSequence(ToolbarHostPage.Note) { it.next }.takeWhile { it != page }.toList()
    return listOf(ToolbarHostPage.Notes) + chain + page
  }
}

/**
 * La pagina «Barre fra pagine» (F4A.3): `--es gallery.page toolbarpages`, con `pages.auto`, `pages.follow` e
 * `pages.start`. In logcat (`adb logcat -s FluidBarre`) scrive `PILA` a ogni cambio, con l'istante `uptimeMillis`.
 */
@Composable
internal fun ToolbarPagesPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val launched = remember(context) {
    val activity = context.findHostActivity()
    if (context.isDebuggableApp() && activity != null) {
      ToolbarPagesLaunch.parse { activity.intent?.getStringExtra(it) }
    } else {
      ToolbarPagesLaunch.Request(ToolbarHostPage.Notes, 0L, false)
    }
  }
  ToolbarDemoPolicy { ToolbarPagesContent(launched, bottomInset, onBack) }
}

/** La prova senza la lettura degli extra: è ciò che apre il test. */
@Composable
internal fun ToolbarPagesContent(
  launched: ToolbarPagesLaunch.Request,
  bottomInset: Dp,
  onBack: () -> Unit,
  hostState: FluidPageHostState = rememberFluidPageHostState(),
) {
  if (launched.uniform) {
    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(MaterialTheme.colorScheme.background))
      ToolbarPagesHost(launched, bottomInset, onBack, hostState, backdrop)
    }
  } else {
    ToolbarPagesHost(launched, bottomInset, onBack, hostState, null)
  }
}

@Composable
private fun ToolbarPagesHost(
  launched: ToolbarPagesLaunch.Request,
  bottomInset: Dp,
  onBack: () -> Unit,
  hostState: FluidPageHostState,
  uniform: GlassBackdropState?,
) {
  var stack by remember { mutableStateOf(ToolbarPagesLaunch.stackTo(launched.start, launched.legacyOnly)) }
  var queries by rememberSaveable { mutableStateOf(mapOf<ToolbarHostPage, String>()) }
  var auto by remember { mutableLongStateOf(launched.autoMs) }
  // L'interruttore è dell'engine (le prove della barra dell'host): lo si accende qui e lo si rimette a posto uscendo.
  DisposableEffect(Unit) {
    val before = FluidPageBarDiagnostics.gestureFollowsFinger
    if (launched.follow) FluidPageBarDiagnostics.gestureFollowsFinger = true
    onDispose { FluidPageBarDiagnostics.gestureFollowsFinger = before }
  }
  val scale = LocalFluidMotionPolicy.current.durationScale
  LaunchedEffect(stack) {
    Log.i(ToolbarLogTag, "PILA ${stack.joinToString("/") { it.key }} t=${android.os.SystemClock.uptimeMillis()}")
  }
  LaunchedEffect(auto, scale) {
    if (auto <= 0L) return@LaunchedEffect
    val wait = (auto * scale.coerceAtLeast(1f)).toLong()
    var step = ToolbarPagesScript.indexOf(stack).coerceAtLeast(0)
    while (true) {
      delay(wait)
      step = (step + 1) % ToolbarPagesScript.size
      stack = ToolbarPagesScript[step]
    }
  }
  val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
  FluidPageHost(
    stack = stack,
    onBack = back,
    state = hostState,
    modifier = Modifier
      .fillMaxSize()
      .testTag("toolbar-pages"),
    peer = remember {
      FluidPeerMove<ToolbarHostPage> { from, to ->
        if (from.isTab && to.isTab) to.ordinal.compareTo(from.ordinal) else 0
      }
    },
  ) { page ->
    // La radice della pila non ha l'indietro (le schede, e la prima pagina dell'host di sole pagine di prima).
    val root = page.isTab || stack.firstOrNull() == page
    if (uniform != null) {
      // Il materiale legge la stessa superficie uniforme sui due lati anche mentre passa l'ombra della carta.
      Box(Modifier.fillMaxSize().clickable {
        if (page == ToolbarHostPage.Notes) stack = stack + ToolbarHostPage.Note else back()
      })
      FluidPageChrome(title = page.title, onBack = back.takeUnless { root }, backdrop = uniform, toolbar = page.toolbar(close = back))
      return@FluidPageHost
    }
    val query = queries[page].orEmpty()
    var searchMode by rememberSaveable { mutableStateOf(0) }
    var scopeAll by rememberSaveable { mutableStateOf(true) }
    FluidScreen(
      title = page.title,
      subtitle = "F4A.3 · fra pagine",
      modifier = Modifier.testTag("toolbar-pages-${page.key}"),
      onBack = if (root) null else back,
      extraBottomPadding = bottomInset,
      toolbar = page.toolbar(close = back),
      actions = {
        when (page) {
          ToolbarHostPage.Legacy, ToolbarHostPage.LegacyRead ->
            FluidBarAction(FluidIcons.IosShare, "Condividi (di prima)", onClick = {})
          ToolbarHostPage.LegacySearch -> FluidBarAction(FluidIcons.Add, "Nuova (di prima)", onClick = {})
          else -> Unit
        }
      },
      search = if (page.hasSearch) FluidSearchSpec(
        query = query,
        onQueryChange = { queries = queries + (page to it) },
        placement = if (searchMode == 0) FluidSearchPlacement.Principal else FluidSearchPlacement.TopBar,
        behavior = if (searchMode == 0) FluidSearchBehavior.Automatic else FluidSearchBehavior.CollapseToButton,
        expansion = if (searchMode == 2) FluidSearchExpansion.AboveKeyboard else FluidSearchExpansion.StayAtTop,
        scopes = { Text(if (scopeAll) "Tutte" else "Recenti", modifier = Modifier.clickable { scopeAll = !scopeAll }) },
        prompt = "Cerca in ${page.title}",
      ) else null,
      barMinimize = page.minimize,
    ) {
      item(key = "voice") {
        Text(
          text = page.voice,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 4.dp),
        )
      }
      if (page == ToolbarHostPage.Search) item(key = "search-mode") {
        val next = listOf("Prova la ricerca in alto", "Prova il viaggio alla tastiera", "Torna al campo fissato")
        val current = listOf("Campo fissato sotto il titolo", "Campo attivo in alto", "Campo attivo sopra la tastiera")
        FluidListRow(title = next[searchMode], subtitle = current[searchMode], onClick = { searchMode = (searchMode + 1) % next.size })
      }
      item(key = "commands") { FluidSectionHeader(title = "Comandi", detail = "Le pagine dicono solo chi sono i tasti.") }
      page.next?.let { next ->
        item(key = "open") {
          FluidListRow(
            title = "Apri «${next.title}»",
            subtitle = "Un push: i tasti partono col tocco, col tempo del piano",
            modifier = Modifier.testTag("toolbar-pages-open"),
            onClick = { stack = stack + next },
          )
        }
      }
      page.search?.let { search ->
        item(key = "search") {
          FluidListRow(
            title = "Apri «${search.title}»",
            subtitle = "La ricerca principale: la barra si ritira scorrendo e il campo resta",
            modifier = Modifier.testTag("toolbar-pages-search"),
            onClick = { stack = stack + search },
          )
        }
      }
      if (page.isTab) {
        val other = if (page == ToolbarHostPage.Notes) ToolbarHostPage.Folders else ToolbarHostPage.Notes
        item(key = "tab") {
          FluidListRow(
            title = "Passa alla scheda «${other.title}»",
            subtitle = "Un cambio di scheda: come un cambio di stato, senza verso",
            modifier = Modifier.testTag("toolbar-pages-tab"),
            onClick = { stack = listOf(other) },
          )
        }
        item(key = "exit") {
          FluidListRow(title = "Esci dalla prova", subtitle = "Torna alla galleria", onClick = onBack)
        }
      } else if (!root) {
        item(key = "back") {
          FluidListRow(
            title = "Torna indietro",
            subtitle = "Un pop col tasto: come il push, al contrario",
            modifier = Modifier.testTag("toolbar-pages-back"),
            onClick = back,
          )
        }
      }
      item(key = "follow") {
        FluidListRow(
          title = "Gesto indietro: ${if (FluidPageBarDiagnostics.gestureFollowsFinger) "segue il dito (prova)" else "aspetta la conferma"}",
          subtitle = "La variante da confrontare con quella di serie (`pages.follow`)",
          modifier = Modifier.testTag("toolbar-pages-follow"),
          onClick = { FluidPageBarDiagnostics.gestureFollowsFinger = !FluidPageBarDiagnostics.gestureFollowsFinger },
        )
      }
      item(key = "auto") {
        FluidListRow(
          title = "Automatico: ${if (auto > 0L) "$auto ms" else "spento"}",
          subtitle = "Avanti fino a «Modifica», indietro, la scheda accanto (`pages.auto`)",
          modifier = Modifier.testTag("toolbar-pages-auto"),
          onClick = { auto = if (auto > 0L) 0L else ToolbarPagesLaunch.DefaultAutoMs },
        )
      }
      // Abbastanza righe da far scorrere la pagina sotto la barra: il titolo si aggancia e i tasti restano fermi.
      val rows = (1..20).filter { query.isBlank() || "Riga $it".contains(query.trim(), ignoreCase = true) }
      items(count = rows.size, key = { "filler-${rows[it]}" }) { index ->
        FluidListRow(title = "Riga ${rows[index]}", subtitle = "Per far scorrere la pagina sotto la barra")
      }
    }
  }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findHostActivity()
  else -> null
}
