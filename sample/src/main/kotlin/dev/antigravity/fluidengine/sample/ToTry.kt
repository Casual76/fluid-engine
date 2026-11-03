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
  AppParts("I pezzi delle app"),
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
private val ControlsRound = TryTarget.Tab(RouteControls, "Controlli › Tasti tondi: crescono, ma con un tetto")
private val ControlsContrast = TryTarget.Tab(RouteControls, "Controlli › Contrasto: il vetro si fa più denso")
private val MaterialAction = TryTarget.Tab(RouteMaterial, "Materiale › tasto Azione")
private val PhysicsSpring = TryTarget.Tab(RoutePlayground, "Fisica › Molla e materiale")
private val ChromeExpand = TryTarget.Tab(RouteChrome, "Chrome › Card che si espande")
private val SettingsRows = TryTarget.Tab(RouteSettings, "Impostazioni › Righe di impostazioni")
private val ControlsScroll = TryTarget.Tab(RouteControls, "Controlli, scorrendo fino in fondo")
private val PagesScreen = TryTarget.Screen("Chrome › Navigazione › Pagine (schermata a parte)")
private val RowsPage = TryTarget.Page(GalleryPage.RowsAndSheets, "Righe e fogli")
private val CapsulesPage = TryTarget.Page(GalleryPage.CapsulesAndParts, "Capsule e pezzi piccoli")
private val GapsPage = TryTarget.Page(GalleryPage.Gaps, "I buchi delle app")
private val MenusAll = TryTarget.Page(GalleryPage.Menus, "Menù liquidi")
private val MenusMorph = TryTarget.Page(GalleryPage.Menus, "Menù liquidi › Il tasto che si trasforma")
private val MenusBar = TryTarget.Page(GalleryPage.Menus, "Menù liquidi › Le azioni della barra")
private val MenusRows = TryTarget.Page(GalleryPage.Menus, "Menù liquidi › Righe con il menu contestuale")
private val MenusAlert = TryTarget.Page(GalleryPage.Menus, "Menù liquidi › L'avviso")
private val MenusLog = TryTarget.Page(GalleryPage.Menus, "Menù liquidi, con il registro aptico acceso in Controlli")
private val GlassChips = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Chip")
private val GlassSegmented = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Segmentato")
private val GlassRail = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Rail")
private val GlassIndex = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Indice di sezione")
private val GlassBar = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Barra flottante")
private val GlassCoach = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Suggerimento")
private val GlassSync = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Sincronizzazione")
private val GlassSheets = TryTarget.Page(GalleryPage.AllGlass, "Tutto in vetro › Fogli e pagine")
private val MoreUpdate = TryTarget.Page(GalleryPage.MoreComponents, "Riordino, aggiornamento e visore › Aggiornamento in-app")
private val MoreReorder = TryTarget.Page(GalleryPage.MoreComponents, "Riordino, aggiornamento e visore › Riordino")
private val MoreFastScroll = TryTarget.Page(GalleryPage.MoreComponents, "Riordino, aggiornamento e visore › Scorrimento rapido")
private val MoreViewer = TryTarget.Page(GalleryPage.MoreComponents, "Riordino, aggiornamento e visore › Visualizzatore")
private val MoreScrubber = TryTarget.Page(GalleryPage.MoreComponents, "Riordino, aggiornamento e visore › Scrubber")

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

/**
 * La beta 2: i menu liquidi (fase 7), tutto in vetro vero (fase 9) e la barra ancorata (fase 8). Un menu
 * si sceglie col dito che l'ha aperto, e un rilascio altrove lo lascia aperto; l'avviso sta nell'host, in
 * vetro vero. I menu si provano dalla demo [GalleryPage.Menus], il vetro da [GalleryPage.AllGlass], la
 * barra dalla schermata delle pagine.
 */
private val Beta2 = TryRound(
  id = "beta2",
  title = "Beta 2",
  intro = "Seconda prova della 3.0: i menu liquidi, tutto in vetro vero e la barra ancorata. Un menu si sceglie " +
    "col dito che l'ha aperto — tieni premuto, trascina su una voce, rilascia — e un rilascio altrove lo lascia " +
    "aperto. Tocca una voce per aprire la sua demo.",
  items = listOf(
    TryItem(
      "menu-scelta-dito", TryArea.Menus, "Scelta col dito",
      "Tieni premuto «Azione» nel palco e, senza sollevare il dito, trascinalo su una voce e rilascia: la scelta " +
        "compare in cima alla demo; giudica se il gesto è naturale e se la goccia ti segue di voce in voce senza " +
        "ritardo.",
      MenusMorph,
    ),
    TryItem(
      "menu-rilascio-fermo", TryArea.Menus, "Rilascio fermo",
      "Tieni premuto «Azione» e solleva il dito senza muoverti: il menu resta aperto e si sceglie con un tocco, " +
        "e lo stesso se rilasci fuori dalle voci o su «Modifica (spenta)»; giudica se il passaggio dal tenere al " +
        "toccare è chiaro.",
      MenusMorph,
    ),
    TryItem(
      "menu-riga-cresce", TryArea.Menus, "Riga che si solleva",
      "Tieni premuta una riga sotto «Righe con il menu contestuale»: la riga si solleva e il menu cresce accanto " +
        "a lei, mai sopra, e trascinando sulle voci la lista resta ferma; giudica se il menu sembra nato dalla riga.",
      MenusRows,
    ),
    TryItem(
      "menu-apre-al-tocco", TryArea.Menus, "Apertura al tocco",
      "Tocca «Ordina» nel palco e i tre puntini nella barra: il menu si apre al tocco, senza fare l'azione del " +
        "tasto; giudica che si apra subito e che il tasto lasci il posto al menu senza un attimo vuoto.",
      MenusAll,
    ),
    TryItem(
      "menu-barra-tenuto", TryArea.Menus, "Azione della barra",
      "Tieni premuta la stella nella barra, trascina su una voce e rilascia: il menu nasce dal tasto gonfiato dal " +
        "dito e il tasto lascia il posto alla finestra; giudica se nel passaggio si vede un doppione o un " +
        "fotogramma vuoto.",
      MenusBar,
    ),
    TryItem(
      "menu-sezioni", TryArea.Menus, "Sezioni e separatori",
      "Apri un menu e guarda «Elimina»: sta in una sezione sua, con una banda al posto della riga sottile, rosso " +
        "perché distruttivo, mentre «Modifica (spenta)» non fa tick e non si sceglie; giudica se la banda si " +
        "legge e se il dito ci cade sopra per sbaglio.",
      MenusMorph,
    ),
    TryItem(
      "menu-goccia-dito", TryArea.Menus, "La goccia sul dito",
      "Trascina il dito su e giù per le voci, prima piano e poi di scatto: la goccia segue il dito, si schiaccia " +
        "quando va veloce e si posa bianca sulla voce scelta; giudica se lo schiacciamento è bello o troppo, e " +
        "se resta incollata al dito.",
      MenusMorph,
    ),
    TryItem(
      "menu-un-open", TryArea.Menus, "Un Open per apertura",
      "Accendi il registro aptico in Controlli e apri un menu da ogni ingresso: all'apertura un Open solo, poi un " +
        "Tick per voce attraversata e un Tap alla scelta, niente al congedo; giudica se si sente come un colpo " +
        "unico e conta le righe nel registro.",
      MenusLog,
    ),
    TryItem(
      "avviso-vetro", TryArea.Panels, "Avviso di vetro",
      "Apri «Due pulsanti» e «Tre pulsanti» nell'avviso: è vetro vero con la pagina rifratta dietro, arriva " +
        "posandosi e se ne va con un'uscita che si vede; trascina il dito fra i pulsanti per vedere la goccia, e " +
        "giudica se si legge sopra la pagina.",
      MenusAlert,
    ),
    TryItem(
      "avviso-tastiera", TryArea.Panels, "Avviso con la tastiera",
      "Apri «Rinomina» in Righe e fogli: l'avviso con il campo sta nell'host, il campo prende il fuoco con il " +
        "testo selezionato e la tastiera sale senza coprirlo; giudica se l'avviso si sposta bene e se esce pulito " +
        "quando si chiude.",
      RowsPage,
    ),
    TryItem(
      "vetro-segmentato", TryArea.Panels, "Segmentato a goccia",
      "Trascina la pillola da un segmento all'altro, sul chiaro e sullo scuro: a riposo è bianca (grigia sullo " +
        "scuro), sotto il dito diventa lente e vibra un tick per segmento; giudica la lente sulle etichette e se " +
        "la pista di vetro si legge.",
      GlassSegmented,
    ),
    TryItem(
      "vetro-indice", TryArea.Panels, "Indice di sezione",
      "Tieni premuta la barretta dell'indice e trascina: la lente è una goccia e la bolla ha il materiale dei " +
        "pannelli; giudica se la bolla si legge sopra la pagina e se all'inizio parte un colpo solo, senza tick.",
      GlassIndex,
    ),
    TryItem(
      "vetro-suggerimento", TryArea.Panels, "Suggerimento senza scala",
      "Mostra il suggerimento sull'elemento evidenziato: entra e esce scivolando e sfumando, senza scala e senza " +
        "la punta dipinta, e «Ok» è un tasto di vetro; giudica se l'alone ti basta per capire a cosa si riferisce.",
      GlassCoach,
    ),
    TryItem(
      "vetro-contenitore", TryArea.Panels, "Contenitore e pagina intera",
      "Apri «Contenitore» e «Pagina intera» da Fogli e pagine: sul fondale ambientale il gruppo è una lastra di " +
        "contenuto come una lista di vetro e la chiusura è un tasto di vetro; giudica se il testo resta leggibile.",
      GlassSheets,
    ),
    TryItem(
      "vetro-foglio", TryArea.Panels, "Foglio di vetro",
      "Apri «Foglio» da Fogli e pagine e chiudilo: il vecchio foglio è quello di vetro dell'host e chi lo ha " +
        "aperto sa di essere chiuso solo a uscita finita; giudica l'uscita e se la pagina dietro ricompare senza " +
        "lampi.",
      GlassSheets,
    ),
    TryItem(
      "vetro-chip", TryArea.Buttons, "Chip di vetro",
      "Tocca i chip sul riquadro colorato e poi quelli sulla pagina piatta: il chip scelto ha l'accento dentro il " +
        "vetro e premuto cresce e segue il dito; giudica se il vetro si legge e se senza fondale torna la " +
        "pastiglia di sempre.",
      GlassChips,
    ),
    TryItem(
      "vetro-rail", TryArea.Bar, "Rail a goccia",
      "Tocca le schede del rail: la goccia viaggia come la pillola della barra e diventa lente mentre si muove; " +
        "giudica se resta centrata sulla scheda e se l'etichetta si legge dentro la lente.",
      GlassRail,
    ),
    TryItem(
      "vetro-banda", TryArea.Bar, "Banda della barra",
      "Tocca una scheda, il cerchio e la scheda ripiegata della barra con la banda sopra: la banda è il vetro " +
        "della barra, senza l'increspatura di Material e con un Tap per tocco; giudica se banda e barra sembrano " +
        "un vetro solo.",
      GlassBar,
    ),
    TryItem(
      "vetro-sincronizzazione", TryArea.Bar, "Capsula di sincronizzazione",
      "Guarda l'indicatore di sincronizzazione nella barra e toccalo: è una capsula di vetro con il tono dentro " +
        "il materiale e la pressione dei tasti; giudica se il colore si legge senza coprire il vetro.",
      GlassSync,
    ),
    TryItem(
      "pagine-barra-ancorata", TryArea.Pages, "Barra ancorata",
      "Apri la demo Pagine e vai avanti e indietro con una, due e tre azioni: il tasto indietro resta fermo al " +
        "pixel, le azioni si trasformano, il titolo grande viaggia con la pagina e un gesto partito sulla barra " +
        "è della barra; giudica se la barra sembra ferma mentre le pagine passano.",
      PagesScreen,
    ),
    TryItem(
      "pezzi-aggiornamento", TryArea.AppParts, "Aggiornamento in-app",
      "Scorri le righe dell'aggiornamento e apri l'avviso come foglio: è il disegno che sostituirà quello di " +
        "otto app; giudica se stati, avanzamento e pulsanti si leggono al primo colpo.",
      MoreUpdate,
    ),
    TryItem(
      "pezzi-riordino", TryArea.AppParts, "Riordino",
      "Tieni premuto un elemento della colonna o della griglia e spostalo: si solleva come una goccia di vetro, " +
        "i vicini scalano e c'è un tick per posto; giudica se il dito lo porta senza ritardo e se si posa bene.",
      MoreReorder,
    ),
    TryItem(
      "pezzi-scorrimento", TryArea.AppParts, "Scorrimento rapido",
      "Scorri la lista e prendi la maniglia che compare sul bordo: segue il dito, la bolla dice dove sei e c'è un " +
        "tick per etichetta; giudica se è facile da prendere e se sparisce al momento giusto.",
      MoreFastScroll,
    ),
    TryItem(
      "pezzi-visore", TryArea.AppParts, "Visualizzatore",
      "Apri il visualizzatore (si apre nel palco sotto il pulsante): pizzica, doppio tocco, sposta l'immagine " +
        "ingrandita, scorri fra le immagini e trascina giù per chiudere; giudica se lo zoom sta sotto le dita.",
      MoreViewer,
    ),
  ),
)

/** La beta 3: quello che e' cambiato dopo le prove della beta 2. */
private val Beta3 = TryRound(
  id = "beta3",
  title = "Beta 3",
  intro = "Terza prova della 3.0: cosa è cambiato dopo la beta 2. Tocca una voce per aprire la sua demo.",
  items = listOf(
    TryItem(
      "liquido-apertura", TryArea.Menus, "Vetro liquido",
      "Tieni premuto «Azione» e guarda il pannello salire dal tasto con una goccia che si stacca e oscillare un " +
        "attimo prima di posarsi; giudica se si legge come un liquido e non come un rettangolo che cresce.",
      MenusMorph,
    ),
    TryItem(
      "liquido-chiusura", TryArea.Menus, "Il testo in chiusura",
      "Chiudi lo stesso menu toccando fuori, anche in alto sopra il palco: le voci se ne vanno per prime, mai fuori " +
        "dal vetro né insieme ad «Azione»; dimmi se l'uscita ti torna.",
      MenusMorph,
    ),
    TryItem(
      "liquido-contestuale", TryArea.Menus, "Contestuale liquido",
      "Tieni premuta una riga e guarda il menu uscire dal bordo della riga sotto il dito e rientrarci; giudica se la " +
        "riga sollevata sembra ancora un ritaglio.",
      MenusRows,
    ),
    TryItem(
      "liquido-confronto", TryArea.Panels, "Liquido o geometrico",
      "In Fisica › Molla e materiale metti l'interruttore su «Geometrico (beta 2)» e poi su «Vetro liquido», e rifai i " +
        "tre gesti sopra: dimmi quale preferisci a velocità vera.",
      PhysicsSpring,
    ),
    TryItem(
      "tasti-tengono-dito", TryArea.Buttons, "Il tasto tiene il dito",
      "Tieni premuto un tasto in una pagina che scorre e muovi il dito in ogni verso: il tasto ti segue e la " +
        "pagina resta ferma, e se lo lasci sopra il tasto (anche dopo averlo trascinato) vale come un tocco, mentre " +
        "nella fila dei chip la fila scorre e il chip si scioglie; giudica se ti torna in tutti e due i casi.",
      ControlsTouch,
    ),
    TryItem(
      "segmentato-come-ios", TryArea.Panels, "Segmentato come iOS",
      "Appoggia il dito su un segmento che non è scelto e scivola di lato: la goccia ti viene sotto, ti segue con " +
        "un tick per segmento e sceglie dove la lasci (un tocco fermo sceglie come prima); giudica se la goccia " +
        "arriva senza strappo e se il dito in verticale scorre ancora la pagina.",
      GlassSegmented,
    ),
    TryItem(
      "pezzi-scrubber", TryArea.AppParts, "Scrubber",
      "Trascina la maniglia del brano mentre suona e lasciala, poi prova la cronologia a passi: la maniglia sta " +
        "sotto il dito, al rilascio non torna indietro mentre il lettore la raggiunge e a passi c'è un tick per " +
        "fotogramma; giudica se i tre secondi di attesa sono giusti e se il continuo senza vibrazione ti sembra morto.",
      MoreScrubber,
    ),
  ),
)

/** La beta 4: la goccia al posto del vetro geometrico, e i buchi trovati guardando le app. */
private val Beta4 = TryRound(
  id = "beta4",
  title = "Beta 4",
  intro = "Quarta prova della 3.0: la goccia e i pezzi che servivano alle app. Tocca una voce per aprire la sua demo.",
  items = listOf(
    TryItem(
      "goccia-tasto", TryArea.Menus, "La goccia del tasto",
      "Tieni premuto «Azione», lascia e poi tocca fuori: il pannello esce dal tasto come una goccia (l'alto arriva " +
        "per ultimo) e ci rientra più piano, con le voci che se ne vanno per prime e sempre dentro il vetro; " +
        "giudica se è liquido senza essere molle e se alla posa c'è uno scatto.",
      MenusMorph,
    ),
    TryItem(
      "goccia-contestuale", TryArea.Menus, "Il contestuale dal dito",
      "Tieni premuta «Nota della riunione» a sinistra, al centro e a destra: il menu si stacca dal punto del dito " +
        "e la coda lo tiene attaccato alla riga; giudica se riga e menu sembrano un vetro solo.",
      MenusRows,
    ),
    TryItem(
      "goccia-card", TryArea.Panels, "La card che rientra",
      "Apri e chiudi la card: il vetro vuoto rientra nella riga come un liquido e il testo resta sempre dentro il " +
        "vetro; giudica l'uscita del testo, che prima se ne andava in un modo strano.",
      ChromeExpand,
    ),
    TryItem(
      "goccia-tensione", TryArea.Panels, "Goccia e tensione",
      "Confronta «Goccia» con «Geometrico (beta 2)» rifacendo i gesti sopra, poi muovi «Tensione del bordo» (0,2 " +
        "di serie, fino a 2): dimmi se gli angoli sono giusti o se la vuoi più tesa.",
      PhysicsSpring,
    ),
    TryItem(
      "back-chiede", TryArea.Pages, "Il back che chiede",
      "Apri «Apri una bozza» e torna indietro col dito oltre metà: la pagina torna sotto il dito e compare " +
        "l'avviso, «Esci» la porta via e con «Modifiche non salvate» spento esce come le altre; giudica se il " +
        "ritorno sotto il dito si legge come un no e non come un errore.",
      PagesScreen,
    ),
    TryItem(
      "tondi-tetto", TryArea.Buttons, "Tondi con un tetto",
      "Tieni premuti i quattro dischi da 44 a 76 dp: fino a 48 dp crescono di metà come prima, oltre di 24 dp " +
        "in tutto, così il 76 non copre il vicino; giudica se i grandi crescono abbastanza o se preferisci un " +
        "tetto più basso (12 per lato ora, 8 l'alternativa).",
      ControlsRound,
    ),
    TryItem(
      "contrasto-sistema", TryArea.Panels, "Contrasto di sistema",
      "Guarda le tre righe Standard, Medio e Alto, poi metti «Contrasto del colore» a medio e ad alto nelle " +
        "impostazioni di Android con la galleria aperta: barra, tasti e pannelli si fanno più densi e il bordo " +
        "più netto; giudica se ad alto il vetro si legge ancora come vetro.",
      ControlsContrast,
    ),
  ),
)

/** La beta 5: la 3.0.1, cioe' quello che le app hanno trovato migrando alla 3.0.0. */
private val Beta5 = TryRound(
  id = "beta5",
  title = "Beta 5",
  intro = "Quinta prova: le correzioni trovate portando le app sulla 3.0. Tocca una voce per aprire la sua demo.",
  items = listOf(
    TryItem(
      "segmentato-dal-scelto", TryArea.Pages, "Segmentato e back",
      "Apri «Apri C», metti il tema scuro e trascina di lato partendo dal segmento già scelto e poi da un altro: " +
        "la pagina resta ferma e la goccia ti segue; giudica se la goccia prende il dito senza ritardo.",
      PagesScreen,
    ),
    TryItem(
      "riga-selettore", TryArea.Rows, "La riga che sceglie",
      "Tocca «Qualità»: la riga diventa il pop-up; scegli una voce e poi muovi «Volume», anche col tema scuro; " +
        "giudica il viaggio dalla riga al pop-up e se il testo si legge sullo scuro.",
      SettingsRows,
    ),
    TryItem(
      "ordina-spunta", TryArea.Menus, "Ordina con la spunta",
      "Tocca «Ordina»: il menu si apre al tocco con la spunta sull'ordine scelto; scegline un altro e riaprilo; " +
        "giudica se la spunta si legge e se le voci stanno intere nel menu.",
      MenusMorph,
    ),
    TryItem(
      "barra-si-riapre", TryArea.Bar, "La barra si riapre",
      "Scorri una scheda finché la barra in basso si ripiega, poi cambia scheda: la barra torna aperta, e su una " +
        "pagina che non scorre non si ripiega più; giudica se il ritorno è al momento giusto.",
      ControlsScroll,
    ),
  ),
)

/** La 2.0.0 stabile: gli ultimi ritocchi dopo la beta 5 (engine 3.0.4). */
private val Stable = TryRound(
  id = "stabile",
  title = "Stabile",
  intro = "La versione stabile: gli ultimi ritocchi dopo la beta 5. Tocca una voce per aprire la sua demo.",
  items = listOf(
    TryItem(
      "interruttore-trascinabile", TryArea.Rows, "Interruttori da trascinare",
      "Trascina il pallino di «Nero assoluto» o di «Aptica» invece di toccare la riga: si prende e si posa dove lo " +
        "lasci, e un tocco sulla riga commuta come prima; giudica se il pallino segue il dito.",
      TryTarget.Tab(RouteSettings, "Impostazioni › Aspetto"),
    ),
    TryItem(
      "menu-apertura-veloce", TryArea.Menus, "Menu più svelti",
      "Tieni premuto «Azione» e poi tocca fuori: il menu si apre veloce quanto si chiude e le voci arrivano prima; " +
        "giudica se apertura e chiusura hanno lo stesso passo.",
      MenusMorph,
    ),
    TryItem(
      "back-dito-fermo", TryArea.Pages, "Back senza scatti",
      "Apri B e torna indietro dal bordo fermando il dito a metà per un attimo, poi riprendi e torna verso il bordo: " +
        "la pagina resta sotto il dito; giudica se non salta più.",
      PagesScreen,
    ),
  ),
)

/** Tutti i giri, dal piu' vecchio al piu' nuovo. Una beta nuova aggiunge qui il suo e basta. */
internal val TryRounds: List<TryRound> = listOf(Beta1, Beta2, Beta3, Beta4, Beta5, Stable)

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
