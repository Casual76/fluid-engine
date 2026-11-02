package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * Dove porta una voce di "Da provare".
 *
 * [where] e' la frase sotto la voce, "Controlli › Tocco": dice dove si va prima di toccare, perche'
 * un collegamento che non dice dove porta si tocca solo per scoprirlo.
 */
internal sealed interface TryTarget {
  val where: String

  /** Una scheda della barra, aperta sopra la lista (l'"Indietro" di sistema torna alla lista). */
  data class Tab(val route: String, override val where: String) : TryTarget

  /** Una pagina della galleria ([GalleryPage]): una demo che non sta in nessuna scheda. */
  data class Page(val page: GalleryPage, override val where: String) : TryTarget

  /** La schermata a parte delle pagine ([PagesDemoActivity]): il suo "Indietro" e' quello di sistema. */
  data class Screen(override val where: String) : TryTarget
}

/** Le aree in cui si raggruppano le voci, nell'ordine in cui compaiono nella pagina. */
internal enum class TryArea(val title: String) {
  Buttons("Tasti come LibrePods"),
  Haptics("Aptica e grana"),
  Menus("Menù"),
  Panels("Pannelli e materiale"),
  Bar("Barra in basso"),
  Pages("Pagine"),
  Rows("Righe, aspetto e fogli"),
  Capsules("Capsule e pezzi piccoli"),
  Gaps("I buchi delle app"),
}

/**
 * Una voce: [title] in due parole, [text] che e' una frase sola ("cosa provare, cosa giudicare"), e
 * [target] che e' il collegamento diretto alla demo. L'[id] e' stabile fra una beta e l'altra: serve
 * a nominare la voce in chat e ai test.
 */
internal data class TryItem(
  val id: String,
  val area: TryArea,
  val title: String,
  val text: String,
  val target: TryTarget,
)

/**
 * Un giro di prove: una beta. Le prossime aggiungono un [TryRound] a [TryRounds] e non toccano la
 * pagina; la pagina mostra il giro piu' nuovo per primo e tiene sotto i precedenti.
 */
internal data class TryRound(
  val id: String,
  val title: String,
  val intro: String,
  val items: List<TryItem>,
) {
  /** Le voci raggruppate per area, nell'ordine in cui le aree compaiono (e quello delle voci dentro). */
  fun byArea(): List<Pair<TryArea, List<TryItem>>> {
    val order = items.map { it.area }.distinct()
    return order.map { area -> area to items.filter { it.area == area } }
  }
}

private val ControlsTouch = TryTarget.Tab(RouteControls, "Controlli › Tocco")
private val ControlsLog = TryTarget.Tab(RouteControls, "Controlli › Registro aptico")
private val ControlsGrain = TryTarget.Tab(RouteControls, "Controlli › Grana")
private val MaterialAction = TryTarget.Tab(RouteMaterial, "Materiale › tasto Azione")
private val PagesScreen = TryTarget.Screen("Chrome › Navigazione › Pagine (schermata a parte)")
private val RowsPage = TryTarget.Page(GalleryPage.RowsAndSheets, "Righe e fogli")
private val CapsulesPage = TryTarget.Page(GalleryPage.CapsulesAndParts, "Capsule e pezzi piccoli")
private val GapsPage = TryTarget.Page(GalleryPage.Gaps, "I buchi delle app")

/** La beta 1: tasti e aptica come LibrePods, menù e pannelli a un materiale, pagine, e i pezzi nuovi. */
private val Beta1 = TryRound(
  id = "beta1",
  title = "Beta 1",
  intro = "Prima prova della 3.0: i tasti, la grana, i menù, i pannelli, le pagine e i componenti nuovi. " +
    "Tocca una voce per aprire la sua demo.",
  items = listOf(
    TryItem(
      "tasti-contatto", TryArea.Buttons, "Pressione al contatto",
      "Appoggia il dito su un tasto: deve crescere subito, non al rilascio; giudica se anche un tap veloce si vede.",
      ControlsTouch,
    ),
    TryItem(
      "tasti-tondi", TryArea.Buttons, "Tasti tondi",
      "Tieni premuto un tasto tondo con l'icona: cresce di una volta e mezza; giudica se la lente resta " +
        "intera, senza essere tagliata da chi la contiene.",
      ControlsTouch,
    ),
    TryItem(
      "tasti-luce", TryArea.Buttons, "Luce del dito",
      "Striscia il dito su un tasto di vetro, anche sul tema chiaro: la luce sotto il dito è grande e morbida; " +
        "giudica che non sbianchi la faccia del tasto.",
      ControlsTouch,
    ),
    TryItem(
      "tasti-scorrimento", TryArea.Buttons, "La pagina scorre",
      "Trascina un tasto in verticale: la pagina deve scorrere comunque, il tasto non tiene il gesto; " +
        "giudica se ti sembra naturale.",
      ControlsTouch,
    ),
    TryItem(
      "aptica-tre-colpi", TryArea.Haptics, "Tre colpi per tocco",
      "Tocca un tasto: contatto, rilascio e click, tre colpi in un centinaio di millisecondi; giudica se si " +
        "sentono distinti e controlla le tre voci nel registro aptico.",
      ControlsLog,
    ),
    TryItem(
      "aptica-grana", TryArea.Haptics, "La grana",
      "Trascina un tasto prima piano e poi veloce: da piano niente, da veloce una ruvidità; giudica se è una " +
        "superficie e non un ronzio.",
      ControlsGrain,
    ),
    TryItem(
      "aptica-confronto", TryArea.Haptics, "LibrePods o Fluid",
      "Cambia lo stile della grana e ripeti lo stesso gesto; giudica quale preferisci e, per Fluid, tara " +
        "partenza, spegnimento, passo e forza con i cursori.",
      ControlsGrain,
    ),
    TryItem(
      "menu-azione", TryArea.Menus, "Menù che si trasforma",
      "Tieni premuto il tasto Azione in basso: il tasto diventa il menù con una vibrazione sola (Open, 70 ms " +
        "netti); giudica che il registro mostri un colpo solo.",
      MaterialAction,
    ),
    TryItem(
      "menu-riga", TryArea.Menus, "Menù di una riga",
      "Tieni premuta una riga di «Menù contestuale»: una sola vibrazione all'apertura; giudica se il menù " +
        "nasce bene dalla riga.",
      TryTarget.Tab(RouteChrome, "Chrome › Menù contestuale"),
    ),
    TryItem(
      "pannelli-finestra", TryArea.Panels, "Card che si espande",
      "Apri la card che si espande: ora ha un materiale solo e un'ombra molto più leggera di prima; giudica " +
        "il peso, se è troppo poca o troppo piatta.",
      TryTarget.Tab(RouteChrome, "Chrome › Card che si espande"),
    ),
    TryItem(
      "pannelli-notifica", TryArea.Panels, "Notifica e callout",
      "Mostra una notifica in-app: la lente è più leggera, bordo e ombra più sottili; giudica se si legge " +
        "ancora sopra una pagina rumorosa.",
      TryTarget.Tab(RouteChrome, "Chrome › Notifica in-app"),
    ),
    TryItem(
      "pannelli-menu-compatto", TryArea.Panels, "Menù compatto",
      "Tieni premuto Azione e guarda il pannello: il menù che si trasforma è più compatto di prima; giudica " +
        "la misura e l'aria fra le voci.",
      MaterialAction,
    ),
    TryItem(
      "pannelli-bordo-chiaro", TryArea.Panels, "Bordo sul tema chiaro",
      "Passa al tema chiaro e guarda il bordo del vetro che si trasforma: ora è scuro (sullo scuro è la metà " +
        "più tenue); giudica se si vede senza pesare.",
      TryTarget.Tab(RouteSettings, "Impostazioni › Aspetto"),
    ),
    TryItem(
      "barra-ombra", TryArea.Bar, "Ombra della barra",
      "Scorri una pagina sotto la barra in basso: ha un'ombra di vetro; giudica se la stacca dalla pagina sia " +
        "sul chiaro sia sullo scuro.",
      TryTarget.Tab(RouteMaterial, "Materiale"),
    ),
    TryItem(
      "pagine-opache", TryArea.Pages, "Pagine opache da destra",
      "Apri una pagina: entra da destra ed è opaca, quella sotto non traspare; giudica velocità e fluidità.",
      PagesScreen,
    ),
    TryItem(
      "pagine-indietro", TryArea.Pages, "Indietro sotto il dito",
      "Trascina verso destra da un punto qualunque: la pagina sta sotto il dito a ogni fotogramma, anche con " +
        "il gesto di sistema; giudica se è mai più veloce o più lenta del dito.",
      PagesScreen,
    ),
    TryItem(
      "pagine-rimpicciolisce", TryArea.Pages, "Rimpicciolisce e scivola",
      "Apri «Rimpicciolisce e scivola» e torna indietro: prima si fa piccola, poi esce; giudica se il " +
        "movimento è piacevole.",
      PagesScreen,
    ),
    TryItem(
      "pagine-da-tasto", TryArea.Pages, "Indietro da un tasto",
      "Parti con un trascinamento orizzontale proprio sopra un tasto o una riga: parte l'indietro, come in " +
        "LibrePods; giudica se ti dà fastidio.",
      PagesScreen,
    ),
    TryItem(
      "righe-bersaglio", TryArea.Rows, "Righe intere",
      "Tocca ovunque su un interruttore o su una scelta: la riga è il bersaglio e vibra una volta sola; " +
        "spegni «Notifiche» e giudica che la riga sotto sfumi e non risponda.",
      RowsPage,
    ),
    TryItem(
      "righe-aspetto", TryArea.Rows, "Aspetto",
      "Scegli Scuro o un altro accento nella sezione Aspetto: cambia davvero il tema di quella pagina; " +
        "giudica se i colori si rimettono senza lampi.",
      RowsPage,
    ),
    TryItem(
      "fogli-avvisi", TryArea.Rows, "Fogli e avvisi",
      "Apri le conferme dal basso, poi Rinomina e Calibra: tasti fermi sotto un corpo che scorre, testo " +
        "selezionato, un valore fuori misura lo dice senza chiudere; giudica peso e tastiera.",
      RowsPage,
    ),
    TryItem(
      "capsule-azioni", TryArea.Capsules, "Capsula flottante",
      "Nascondi e mostra la capsula di azioni, poi prova il lettore a tutta larghezza; giudica forma, peso " +
        "del vetro e se i tasti piatti si leggono sul palco.",
      CapsulesPage,
    ),
    TryItem(
      "capsule-ricerca", TryArea.Capsules, "Ricerca che si apre",
      "Apri la ricerca dal cerchio e prova quella con «3/12»; giudica apertura, chiusura e tasti dei risultati.",
      CapsulesPage,
    ),
    TryItem(
      "capsule-pezzi", TryArea.Capsules, "Sagome, pallino, segni",
      "Guarda il pallino dal vivo, gli scheletri che respirano, il segno di selezione, i punti del pager e i " +
        "passi; giudica se il respiro è calmo e se tutto si legge.",
      CapsulesPage,
    ),
    TryItem(
      "buchi-taglie", TryArea.Gaps, "Tre taglie di vetro",
      "Tieni premuti uno dopo l'altro i tasti di vetro piccolo, medio e grande, e il pulsante grande: la " +
        "pressione è la stessa in proporzione; giudica il tocco sul piccolo.",
      GapsPage,
    ),
    TryItem(
      "buchi-messaggio", TryArea.Gaps, "Messaggio, campo, barre",
      "Tocca il messaggio con la freccia, prova il campo segreto e guarda le barre giallo pallido e grigio " +
        "chiaro; giudica che si capisca cosa è toccabile e che il riempimento si legga.",
      GapsPage,
    ),
    TryItem(
      "buchi-pezzi", TryArea.Gaps, "Piastrella, vuoto, gruppi",
      "Scorri la lista lunga in pannelli di vetro: il taglio fra due pannelli non deve vedersi; giudica " +
        "anche piastrella e stato vuoto con l'illustrazione.",
      GapsPage,
    ),
  ),
)

/** Tutti i giri, dal piu' vecchio al piu' nuovo. Una beta nuova aggiunge qui il suo e basta. */
internal val TryRounds: List<TryRound> = listOf(Beta1)

/**
 * La pagina "Da provare": quello che Alessio vede per prima, e la lista di cio' che va giudicato in mano.
 *
 * Guidata dai dati ([TryRounds]): non sa niente di nessuna voce. Ogni voce e' una riga della lista
 * raggruppata, e toccarla porta alla demo ([onOpen]). La lista ricorda dov'era ([listState]) anche
 * quando si va a provare una voce e si torna.
 */
@Composable
internal fun ToTryPage(
  bottomInset: Dp,
  onBack: (() -> Unit)?,
  onOpen: (TryTarget) -> Unit,
  modifier: Modifier = Modifier,
  listState: LazyListState = rememberLazyListState(),
  rounds: List<TryRound> = TryRounds,
) {
  FluidScreen(
    title = "Da provare",
    modifier = modifier.testTag("to-try-page"),
    subtitle = rounds.lastOrNull()?.let { "${it.title} · versione ${BuildConfig.VERSION_NAME}" },
    onBack = onBack,
    listState = listState,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Ripples) },
  ) {
    item(key = "to-try-how") {
      FluidCard(glass = true) {
        Text(
          text = "Come si risponde",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Non c'è un modulo: prova una voce e scrivimi in chat il suo nome e cosa hai visto o sentito. " +
            "Una riga basta («il menù vibra due volte», «il bordo sul chiaro pesa»). Per le vibrazioni " +
            "accendi il registro aptico (Controlli › Registro aptico) e dimmi cosa scrive: mostra cosa è " +
            "stato chiesto al vibratore, non quello che senti, ma segna quali colpi la rete dei 70 ms fonde.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    rounds.asReversed().forEach { round ->
      item(key = "${round.id}-header") {
        FluidSectionHeader(title = round.title, detail = round.intro)
      }
      round.byArea().forEach { (area, items) ->
        item(key = "${round.id}-${area.name}-header") { FluidSectionHeader(title = area.title) }
        item(key = "${round.id}-${area.name}") {
          FluidListGroup(glass = true) {
            items.forEachIndexed { index, item ->
              if (index > 0) FluidListDivider()
              FluidListRow(
                title = item.title,
                subtitle = item.text,
                meta = "Apri: ${item.target.where}",
                tone = FluidTone.Primary,
                onClick = { onOpen(item.target) },
                modifier = Modifier.testTag("to-try-${item.id}"),
              )
            }
          }
        }
      }
    }
  }
}

/** Il segno della voce "Da provare" nelle schede: la lista di controllo. */
@Composable
internal fun ToTryIcon() {
  Icon(Icons.Rounded.Checklist, contentDescription = null)
}

/** Quante voci ha il giro piu' nuovo: per la riga che apre la pagina, in cima a Materiale. */
internal fun latestRoundSize(rounds: List<TryRound> = TryRounds): Int = rounds.lastOrNull()?.items?.size ?: 0
