package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.catalog.ChartsDemo
import dev.antigravity.fluidengine.sample.catalog.DateTimeDemo
import dev.antigravity.fluidengine.sample.catalog.DrawerAndTabsDemo
import dev.antigravity.fluidengine.sample.catalog.FileTileDemo
import dev.antigravity.fluidengine.sample.catalog.IdentityDemo
import dev.antigravity.fluidengine.sample.catalog.LongListsDemo
import dev.antigravity.fluidengine.sample.catalog.NotificationUndoDemo
import dev.antigravity.fluidengine.sample.catalog.RichTextDemo
import dev.antigravity.fluidengine.sample.catalog.RichTextDemoToolbars
import dev.antigravity.fluidengine.sample.catalog.rememberRichTextDemoStates
import dev.antigravity.fluidengine.sample.catalog.TokenFieldDemo
import dev.antigravity.fluidengine.sample.catalog.WebContentDemo
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBackIos
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons

/**
 * Il tag che ogni pagina dei componenti nuovi porta sulla sua radice: le prove (e il giro su emulatore) lo usano
 * per sapere che la pagina e' quella composta, senza conoscere la demo che ospita.
 */
internal const val NewComponentPageTag = "new-component-page"

/** Quello che una pagina dice di se': il sottotitolo, il fondale, e cosa fa — e non fa — con la matrice. */
private data class PageSpec(
  val subtitle: String,
  val tone: FluidHeroTone,
  val motif: FluidHeroMotif,
  /** Una frase, nel pannello della matrice: quali assi la demo segue da se', e quali no. */
  val note: String,
  /** La demo ha stati dei dati (vuoto, caricamento, errore) che la matrice puo' scegliere. */
  val supportsData: Boolean = false,
)

private fun GalleryPage.spec(): PageSpec = when (this) {
  GalleryPage.LongLists -> PageSpec(
    subtitle = "Righe che scorrono di lato, pillola delle nuove, vuoto, caricamento, errore.",
    tone = FluidHeroTone.Primary,
    motif = FluidHeroMotif.Ripples,
    note = "Tema, testo e movimento comandano anche gli interruttori della pagina, che partono da qui e restano " +
      "liberi; i dati scelgono se l'elenco è pieno, vuoto, in caricamento o in errore.",
    supportsData = true,
  )
  GalleryPage.Identity -> PageSpec(
    subtitle = "Avatar, palette, badge, selezione, barra laterale annidata, azione con avatar.",
    tone = FluidHeroTone.Tertiary,
    motif = FluidHeroMotif.Dots,
    note = "Tema, testo, movimento e lingua si applicano a tutte le sezioni; le «Varianti» in fondo mostrano " +
      "comunque le loro tre combinazioni fisse.",
  )
  GalleryPage.DrawerAndTabs -> PageSpec(
    subtitle = "Pillola con badge e «Scrivi», rail e pannello laterale.",
    tone = FluidHeroTone.Secondary,
    motif = FluidHeroMotif.Cards,
    note = "Tema, testo, movimento e lingua si applicano ai palchi; le «Varianti» in fondo mostrano comunque le " +
      "loro tre combinazioni fisse.",
  )
  GalleryPage.NotificationUndo -> PageSpec(
    subtitle = "Avvisi con azione in linea, sostituzione e scadenza.",
    tone = FluidHeroTone.Primary,
    motif = FluidHeroMotif.Ripples,
    note = "I tre interruttori della pagina (tema, testo, movimento) partono da qui e restano liberi.",
  )
  GalleryPage.TokenField -> PageSpec(
    subtitle = "Gettoni validi, non validi, lunghi, da destra a sinistra e cento insieme.",
    tone = FluidHeroTone.Secondary,
    motif = FluidHeroMotif.Dots,
    note = "I tre interruttori della pagina (tema, testo, movimento) partono da qui e restano liberi.",
  )
  GalleryPage.DateTime -> PageSpec(
    subtitle = "Scelte rapide, data e ora, limiti, in italiano e in inglese.",
    tone = FluidHeroTone.Tertiary,
    motif = FluidHeroMotif.Ripples,
    note = "I quattro interruttori della pagina (lingua, tema, testo, movimento) partono da qui e restano liberi.",
  )
  GalleryPage.RichText -> PageSpec(
    subtitle = "Campo, barra di formattazione, incolla di HTML sporco, tre uscite.",
    tone = FluidHeroTone.Primary,
    motif = FluidHeroMotif.Cards,
    note = "Tema, testo, movimento e lingua dell'editor si applicano ai primi riquadri; gli ultimi due («200% da " +
      "destra a sinistra» e «Scuro, movimento ridotto») hanno la loro combinazione fissa.",
  )
  GalleryPage.WebContent -> PageSpec(
    subtitle = "HTML locale in un contenitore chiuso alla rete.",
    tone = FluidHeroTone.Secondary,
    motif = FluidHeroMotif.Dots,
    note = "Tema, testo e movimento partono da qui e restano liberi con gli interruttori della pagina; la lingua " +
      "cambia le etichette. Il 200% della matrice e quello dell'interruttore non si sommano: lo zoom del testo " +
      "web resta 200%, mai 400%.",
  )
  GalleryPage.Charts -> PageSpec(
    subtitle = "Ciambella, barre, andamento, legenda e gli stati che li accompagnano.",
    tone = FluidHeroTone.TertiaryToPrimary,
    motif = FluidHeroMotif.Dots,
    note = "Tema, testo, movimento e lingua si applicano a tutti i grafici; vuoto, caricamento ed errore sono già " +
      "una sezione della pagina e le «Varianti» mostrano le loro combinazioni fisse.",
  )
  GalleryPage.FileTile -> PageSpec(
    subtitle = "Tipi, stati, nomi ostili e chip, in tutte le combinazioni di tema e testo.",
    tone = FluidHeroTone.Secondary,
    motif = FluidHeroMotif.Ripples,
    note = "La pagina mostra già tutte le combinazioni di tema e testo, una sotto l'altra: la matrice cambia la " +
      "lingua e il fondo intorno.",
  )
  // Le pagine di prima non hanno la matrice: questa funzione non si chiama per loro.
  else -> PageSpec(
    subtitle = "",
    tone = FluidHeroTone.Primary,
    motif = FluidHeroMotif.Dots,
    note = "",
  )
}

/** La riga che il catalogo di Chrome scrive sotto la pagina: la stessa che la pagina ha come sottotitolo. */
internal val GalleryPage.catalogSubtitle: String get() = spec().subtitle

/**
 * Una pagina dei componenti nuovi: la demo, il suo "Indietro" e il tasto della matrice degli stati.
 *
 * Le demo non hanno tutte la stessa forma, e la pagina le ospita nel modo in cui ognuna sa stare:
 *
 *  * **a sezioni** (avatar, pannello e pillole, editor, grafici): una colonna che sta dentro una `FluidScreen`
 *    della galleria, con il suo titolo grande, il suo "Indietro" e il tasto della matrice fra le azioni della barra;
 *  * **a schermo intero con la barra sua** (liste lunghe, avvisi, contenuto web): la demo e' gia' una
 *    `FluidScreen`, quindi sopra di lei galleggiano il tasto della matrice e, se la demo non ne ha uno, l'"Indietro";
 *  * **a colonna piena** (gettoni, data e ora, piastrelle): niente barra e niente margini, quindi la pagina
 *    mette una barra sottile in cima e lascia sotto lo spazio della barra delle schede.
 *
 * Il tema, la scala del testo, il movimento e la lingua non li applica questa funzione: li applica [MatrixScope]
 * attorno a tutta la pagina e agli host della radice, che la galleria compone una volta sola.
 *
 * @param backdrop il fondale che la pagina registra per il vetro della barra, quello su cui galleggiano i tasti.
 * @param baseDensity la densita' della radice, per tenere leggibile il pannello della matrice.
 */
@Composable
internal fun NewComponentPage(
  page: GalleryPage,
  matrix: GalleryMatrix,
  snapshot: MatrixSnapshot,
  onSettingsChange: (EngineSettings) -> Unit,
  bottomInset: Dp,
  onBack: () -> Unit,
  backdrop: GlassBackdropState,
  baseDensity: Density,
) {
  val spec = page.spec()
  var panelOpen by remember { mutableStateOf(false) }
  val openPanel = { panelOpen = true }

  // Il foglio sta nell'host dei modali della radice, come il resto: dichiarato qui, disegnato sopra la barra.
  FluidGlassModalPortal(
    visible = panelOpen,
    onDismissRequest = { panelOpen = false },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = "Matrice degli stati",
  ) {
    MatrixPanel(
      snapshot = snapshot,
      matrix = matrix,
      onSettingsChange = onSettingsChange,
      baseDensity = baseDensity,
      supportsData = spec.supportsData,
      note = spec.note,
    )
  }

  when (page) {
    GalleryPage.LongLists -> ScreenWithOverlay(snapshot, openPanel, backdrop, onBack, ownBack = true) {
      LongListsDemo(bottomInset = bottomInset, onBack = onBack)
    }
    GalleryPage.NotificationUndo -> ScreenWithOverlay(snapshot, openPanel, backdrop, onBack, ownBack = false) {
      // L'avviso della demo scende sotto la riga dei tasti: non la copre, e non e' coperto da lei.
      NotificationUndoDemo(avoidTop = PageBarHeight)
    }
    GalleryPage.WebContent -> ScreenWithOverlay(snapshot, openPanel, backdrop, onBack, ownBack = false) {
      WebContentDemo()
    }
    GalleryPage.Identity -> SectionsPage(page, spec, snapshot, openPanel, onBack, bottomInset) { IdentityDemo() }
    GalleryPage.DrawerAndTabs -> SectionsPage(page, spec, snapshot, openPanel, onBack, bottomInset) {
      DrawerAndTabsDemo()
    }
    GalleryPage.RichText -> {
      // Le barre di formattazione stanno nell'overlay della pagina, come in un'app vera (vedi RichTextDemo).
      // E come in un'app vera la pagina finisce sopra la tastiera (`avoidKeyboard`): e' la pagina che scorre
      // a portare la riga che si scrive sopra la tastiera, e l'editor sopra la barra.
      val states = rememberRichTextDemoStates()
      SectionsPage(
        page, spec, snapshot, openPanel, onBack, bottomInset,
        avoidKeyboard = true,
        overlay = { backdrop -> RichTextDemoToolbars(states, backdrop) },
      ) { RichTextDemo(states) }
    }
    GalleryPage.Charts -> SectionsPage(page, spec, snapshot, openPanel, onBack, bottomInset) { ChartsDemo() }
    GalleryPage.TokenField -> FullColumnPage(page, snapshot, openPanel, backdrop, onBack, bottomInset) {
      TokenFieldDemo()
    }
    GalleryPage.DateTime -> FullColumnPage(page, snapshot, openPanel, backdrop, onBack, bottomInset) {
      DateTimeDemo()
    }
    GalleryPage.FileTile -> FullColumnPage(page, snapshot, openPanel, backdrop, onBack, bottomInset) {
      FileTileDemo()
    }
    // Le pagine di prima non passano di qui: le compone la galleria.
    else -> Unit
  }
}

/** Una demo a sezioni dentro una `FluidScreen` della galleria, col tasto della matrice fra le azioni della barra. */
@Composable
private fun SectionsPage(
  page: GalleryPage,
  spec: PageSpec,
  snapshot: MatrixSnapshot,
  openPanel: () -> Unit,
  onBack: () -> Unit,
  bottomInset: Dp,
  avoidKeyboard: Boolean = false,
  overlay: @Composable BoxScope.(GlassBackdropState) -> Unit = {},
  demo: @Composable () -> Unit,
) {
  FluidScreen(
    title = page.title,
    subtitle = spec.subtitle,
    // Con `avoidKeyboard` la pagina (contenuto e overlay) finisce sopra la tastiera. Senza, una pagina a
    // tutto schermo resta sotto la tastiera e Android, per mostrare il campo, sposta l'intera finestra:
    // titolo fuori dallo schermo e barre dell'overlay a meta' pagina.
    modifier = Modifier.testTag(NewComponentPageTag).then(if (avoidKeyboard) Modifier.imePadding() else Modifier),
    onBack = onBack,
    actions = { MatrixBarAction(snapshot, openPanel) },
    extraBottomPadding = bottomInset,
    ambient = remember(spec.tone, spec.motif) { FluidAmbient(tone = spec.tone, motif = spec.motif) },
    overlay = overlay,
  ) {
    item(key = "new-component-${page.name}") { demo() }
  }
}

/**
 * Una demo che e' gia' una schermata: la pagina le sta sotto e le mette sopra i tasti. L'"Indietro" c'e' solo se
 * [ownBack] e' falso (le liste lunghe lo disegnano da se', con il loro titolo).
 *
 * I tasti stanno nella riga dei controlli di `FluidScreen` ([PageBarHeight] sotto la barra di stato), che e' vuota
 * in tutte le demo di questo involucro tranne una cosa: l'avviso di una demo che ha il suo `FluidNotificationHost`
 * (gli avvisi con azione) cade proprio li', e i tasti, disegnati dopo la demo, ne coprivano l'intestazione. Una
 * demo cosi' riceve [PageBarHeight] e tiene il suo avviso sotto la riga: i tasti restano sempre raggiungibili e
 * l'avviso si legge per intero. Le altre demo di questo involucro non hanno avvisi e non cambiano.
 */
@Composable
private fun ScreenWithOverlay(
  snapshot: MatrixSnapshot,
  openPanel: () -> Unit,
  backdrop: GlassBackdropState,
  onBack: () -> Unit,
  ownBack: Boolean,
  demo: @Composable () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize().testTag(NewComponentPageTag)) {
    demo()
    if (!ownBack) {
      BarSlot(modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp)) {
        BackButton(onBack, backdrop)
      }
    }
    BarSlot(modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp)) {
      MatrixOverlayButton(snapshot, openPanel, backdrop)
    }
  }
}

/**
 * Una demo a colonna piena: una barra sottile in cima (indietro, titolo, matrice) e, sotto, la demo con lo spazio
 * che la barra delle schede della galleria si prende in basso.
 *
 * Il fondo e il colore del testo sono quelli del tema della galleria: queste demo non dipingono il proprio, e un
 * testo senza colore sul nero e' nero su nero.
 */
@Composable
private fun FullColumnPage(
  page: GalleryPage,
  snapshot: MatrixSnapshot,
  openPanel: () -> Unit,
  backdrop: GlassBackdropState,
  onBack: () -> Unit,
  bottomInset: Dp,
  demo: @Composable () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(scheme.background)
      .testTag(NewComponentPageTag),
  ) {
    BarSlot(modifier = Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(start = 6.dp)) { BackButton(onBack, backdrop) }
        Text(
          text = page.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = scheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Box(modifier = Modifier.padding(end = 6.dp)) { MatrixOverlayButton(snapshot, openPanel, backdrop) }
      }
    }
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(bottom = bottomInset + FluidFloatingTabBarDefaults.ContentInset),
    ) {
      CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { demo() }
    }
  }
}

/** L'altezza della riga dei tasti di una barra, sotto la barra di stato: quella dei controlli di `FluidScreen`. */
internal val PageBarHeight = 44.dp

/** Il posto dei tasti di una barra: sotto la barra di stato, alto quanto la riga dei controlli di `FluidScreen`. */
@Composable
private fun BarSlot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier = modifier.statusBarsPadding().height(PageBarHeight),
    contentAlignment = Alignment.CenterStart,
  ) { content() }
}

/** L'"Indietro" delle pagine che non ne hanno uno: la stessa lente e la stessa freccia di `FluidScreen`. */
@Composable
private fun BackButton(onBack: () -> Unit, backdrop: GlassBackdropState) {
  val backLabel = LocalFluidLabels.current.back
  FluidGlassIconButton(onClick = onBack, backdrop = backdrop) {
    FluidIcon(
      imageVector = FluidIcons.ArrowBackIos,
      contentDescription = backLabel,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
  }
}
