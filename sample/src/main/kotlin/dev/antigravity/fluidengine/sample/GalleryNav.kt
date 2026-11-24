package dev.antigravity.fluidengine.sample

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val RouteMaterial = "material"
internal const val RoutePlayground = "playground"
internal const val RouteControls = "controls"
internal const val RouteChrome = "chrome"
internal const val RouteSettings = "settings"

/**
 * I gruppi in cui si raccolgono le pagine dei componenti nuovi della fase 1, nell'ordine in cui compaiono
 * nel catalogo di Chrome e in "Da provare". Sono solo un raggruppamento: ogni pagina e' una demo, e tutte
 * hanno la stessa matrice degli stati ([GalleryMatrix]).
 */
internal enum class GalleryGroup(val title: String, val detail: String) {
  Lists("Liste e righe", "Liste lunghe, scorrimento di lato, pillola delle nuove, vuoto, caricamento, errore."),
  Identity("Identità e selezione", "Avatar, palette, badge, selezione, barra laterale annidata, azione con avatar."),
  Navigation("Navigazione e avvisi", "Pannello laterale, pillola con badge, rail con «Scrivi» e avvisi con azione."),
  Fields("Campi ed editor", "Campo a gettoni, data e ora, editor con formattazione."),
  Content("Contenuti e dati", "Contenuto web, grafici, piastrelle di file."),
}

/**
 * Le pagine che la galleria apre sopra una scheda: la lista "Da provare" e le demo che non stanno in
 * nessuna scheda. Ognuna e' una schermata intera con il suo "Indietro".
 *
 * Quelle con un [group] sono i componenti nuovi della fase 1: si aprono dentro la matrice degli stati
 * (tema, scala del testo, movimento, lingua), che le altre non hanno.
 */
internal enum class GalleryPage(val title: String, val group: GalleryGroup? = null) {
  ToTry("Da provare"),
  RowsAndSheets("Righe e fogli"),
  CapsulesAndParts("Capsule e pezzi piccoli"),
  Gaps("I buchi delle app"),
  Menus("Menù liquidi"),
  AllGlass("Tutto in vetro"),
  MoreComponents("Riordino, aggiornamento e visore"),
  Visori("Visori"),

  /** Le scene di misura della prima sessione sul dispositivo (piano «iOS in Android», F0.6): vedi `measure/`. */
  Measure("Misure"),

  /**
   * Il materiale nuovo accanto a quello di prima, sui sei fondi, per le sessioni sul dispositivo (piano «iOS in
   * Android», F1A.9): vedi `material/`. Come «Misure», è uno strumento e non una demo di «Da provare».
   */
  Material("Materiale"),

  /**
   * Le icone del set dell'engine e le taglie nuove di barre, capsula, tab bar e menu, nei due temi e da destra a
   * sinistra (piano «iOS in Android», F1A.11): vedi `icons/`. Uno strumento per le sessioni sul dispositivo, come
   * «Materiale»: si apre con `--es gallery.page icons`.
   */
  Icons("Icone"),
  Robustness("Robustezza"),

  /**
   * La stessa forma resa dalle strade diverse del materiale (`glassSurface`, Fluid-physics ferma, vetro dipinto, pillola,
   * goccia) fianco a fianco sullo stesso fondo (piano «iOS in Android», F1B.6): vedi `parity/`. Uno strumento per le
   * sessioni sul dispositivo: si apre con `--es gallery.page parity`.
   */
  Parity("Parità"),

  /**
   * Il vetro **dipinto** dove si vede davvero (F1B, giro di correzione): la galleria ha l'host dei modali alla radice,
   * e con l'host foglio e avviso sono di vetro vero. Qui si aprono fuori dall'host (`ModalBottomSheet`, `Dialog`), con
   * un tasto e un pannello dipinti sulla pagina: vedi `painted/`. Si apre con `--es gallery.page painted`.
   */
  Painted("Vetro dipinto"),

  /**
   * Dove si prova ciò che F2 ha dato al vetro e alle schermate (piano «iOS in Android», F2.6): il vetro che si adatta a ciò
   * che ha dietro (giro chiaro/scuro, inchiostro, tasto tinto, cursore dell'utente), i grandi che scuriscono e il bordo dello
   * scorrimento, in sette scene con un parametro di lancio ciascuna: vedi `adaptive/`. Si apre con
   * `--es gallery.page adaptive`. Uno strumento per le sessioni sul dispositivo, come «Parità».
   */
  Adaptive("Adattività"),

  /**
   * Il piano flottante (piano «iOS in Android», F3.6): un `FluidGlassContainer` per scena, e la scena che cambia solo lo
   * stato (un tasto che diventa due, capsule che si fondono, comparsa per lente, unione, contesto che si sfoca, giro
   * uniforme, `Clear`, forme concentriche, interruzione): vedi `plane/`. Si apre con `--es gallery.page plane`. Uno
   * strumento per le sessioni sul dispositivo, come «Adattività».
   */
  Plane("Piano flottante"),

  /**
   * Le superfici dell'assistente (`engine-ai-ui`, piano «iOS in Android», F3.2b) sopra un fondo che cambia (chiaro, scuro,
   * foto, anche da solo): i pezzi che girano da soli nel contenitore e quelli che per ora restano del lato del tema. Vedi
   * `assistant/`. Si apre con `--es gallery.page assistant`. Uno strumento per le sessioni sul dispositivo, come
   * «Adattività».
   */
  Assistant("Assistente"),

  /**
   * La barra dichiarata sul piano flottante (piano «iOS in Android», F4A.2): poche barre che cambiano stato a un tocco
   * (sfoglia → seleziona, un tasto che diventa principale, il trabocco, la selezione per tasto, le icone che cambiano),
   * col cambio automatico per le riprese. Prova minima: la galleria «Barre» vera è F4A.5. Vedi `toolbar/`. Si apre con
   * `--es gallery.page toolbar`. Uno strumento per le sessioni sul dispositivo, come «Piano flottante».
   */
  Toolbar("Barre (prova)"),

  /**
   * La barra dichiarata **fra pagine** (piano «iOS in Android», F4A.3): un host con due schede e una catena di quattro
   * pagine, una per esempio di Alessio (un tasto che diventa due, due tasti che cambiano icona, il gesto indietro, le
   * combinazioni, i tasti collegati), con l'automatico per le riprese e l'interruttore della variante del gesto che segue
   * il dito. Vedi `toolbar/ToolbarPagesPage.kt`. Si apre con `--es gallery.page toolbarpages`.
   */
  ToolbarPages("Barre fra pagine"),

  /**
   * Tutto F4B in una pagina (piano «iOS in Android», F4B.5), in quattro capitoli: la barra in basso e la capsula; la
   * ricerca nelle sue sedi (in basso con gli ambiti, automatica, minimizzata, in alto, fino alla tastiera, il campo di
   * inserimento); la tab bar con la piega, il tondo della ricerca, il lettore e la prominente; il passaggio alla
   * navigazione adattiva. Vedi `bottom/`. Si apre con `--es gallery.page BottomBar`. Uno strumento per le sessioni sul
   * dispositivo, come «Barre fra pagine».
   */
  BottomBar("Barre in basso, ricerca e schede"),

  /**
   * La stessa dichiarazione di schede su tre larghezze (F4B.4): sotto 600 dp la tab bar, fino a 999 dp il rail, da
   * 1000 dp la tab bar in alto che si apre in barra laterale e torna. Sta da sola perché vuole la finestra intera (la
   * galleria qui non disegna la sua tab bar); ci si arriva dal quarto capitolo di [BottomBar] o con
   * `--es gallery.page TabletNavigation`.
   */
  TabletNavigation("Navigazione adattiva"),

  /**
   * Liste, campi e stepper (piano «iOS in Android», F6.6): le azioni a scorrimento con l'aspetto di iOS 27 (e il
   * confronto con quello di prima), lo stepper di vetro, i campi a capsula, i separatori sotto il testo. Vedi
   * `listsforms/`. Si apre con `--es gallery.page ListsForms`. Uno strumento per le sessioni sul dispositivo, come
   * «Barre fra pagine».
   */
  ListsForms("Liste, campi e stepper"),

  /**
   * Gli stili dei tasti di vetro (piano «iOS in Android», F6.4): `Glass`, `Clear` e `Prominent` per le tre taglie, la
   * larghezza adattata e flessibile, e un foglio aperto da una barra `Compact` i cui tasti tornano alla taglia di serie.
   * Vedi `buttons/`. Si apre con `--es gallery.page ButtonStyles` (`--es buttons.sheet open` col foglio aperto). Uno
   * strumento per le sessioni sul dispositivo, come «Liste, campi e stepper».
   */
  ButtonStyles("Stili dei tasti"),

  /**
   * Gli elementi dinamici (piano «iOS in Android», F6B.12): la selezione con la tab bar che diventa le azioni, i tasti con
   * esito, «Invia» col contatore «Annulla», l'avviso dal tasto e l'elemento che diventa pagina, ognuno nei casi delle app
   * rifatti con il solo engine. Vuole la finestra intera (le scene hanno la loro tab bar): la galleria qui non disegna la
   * sua. Vedi `dynamic/`. Si apre con `--es gallery.page DynamicElements`, una scena con `--es dynamic.scene …`.
   */
  DynamicElements("Elementi dinamici"),

  /**
   * Gli elementi nuovi (piano dell'ecosistema, F8): una sezione per pacchetto, una scena per caso d'app. Come gli
   * elementi dinamici vuole la finestra intera. Vedi `novita/`. Si apre con `--es gallery.page NewElements`, una scena con
   * `--es novita.scene …`.
   */
  NewElements("Elementi nuovi"),

  // --- Componenti nuovi della fase 1 (F1.2 – F1.5), una pagina per demo, raggruppate da [group] ---
  LongLists("Liste lunghe e righe", GalleryGroup.Lists),
  Identity("Identità e selezione", GalleryGroup.Identity),
  DrawerAndTabs("Pannello laterale e schede", GalleryGroup.Navigation),
  NotificationUndo("Avvisi con azione", GalleryGroup.Navigation),
  TokenField("Campo a gettoni", GalleryGroup.Fields),
  DateTime("Data e ora", GalleryGroup.Fields),
  RichText("Testo ricco", GalleryGroup.Fields),
  WebContent("Contenuto web", GalleryGroup.Content),
  Charts("Grafici", GalleryGroup.Content),
  FileTile("Piastrelle di file", GalleryGroup.Content),
  ;

  /** Una pagina dei componenti nuovi: ha la matrice degli stati. */
  val hasMatrix: Boolean get() = group != null

  companion object {
    /** Le pagine dei componenti nuovi, nell'ordine dei gruppi e poi in quello di dichiarazione. */
    val newComponents: List<GalleryPage> =
      GalleryGroup.entries.flatMap { group -> entries.filter { it.group == group } }
  }
}

/** Un posto della pila: una scheda della barra o una [GalleryPage] sopra di lei. */
internal sealed interface GalleryEntry {
  data class Tab(val route: String) : GalleryEntry
  data class Page(val page: GalleryPage) : GalleryEntry
}

/**
 * Dove sta la galleria: una pila di posti, con una scheda in fondo.
 *
 * Le schede della barra non si impilano: toccarne una **riparte** da lei (come in iOS, e come ha
 * sempre fatto la galleria). Si impila quello che si apre da dentro: una demo sopra la scheda da cui
 * parte, o una scheda sopra la lista "Da provare" che ci ha mandato — cosi' il "Indietro" di sistema
 * torna alla lista, e dopo aver provato una voce si passa alla successiva senza cercarla.
 *
 * Pura e senza Android attorno, perche' la regola e' tutta qui e si prova sul computer.
 */
@Stable
internal class GalleryNav(
  initial: List<GalleryEntry> = listOf(GalleryEntry.Tab(RouteMaterial)),
) {
  var stack: List<GalleryEntry> by mutableStateOf(normalized(initial))
    private set

  val current: GalleryEntry get() = stack.last()

  /** La pagina in cima, o nulla se in cima c'e' una scheda. */
  val page: GalleryPage? get() = (current as? GalleryEntry.Page)?.page

  /** La scheda che la barra evidenzia: l'ultima della pila (sotto una pagina c'e' sempre la sua). */
  val route: String get() = stack.filterIsInstance<GalleryEntry.Tab>().last().route

  /** Si puo' tornare indietro dentro la galleria, e quindi l'"Indietro" di sistema e' nostro. */
  val canGoBack: Boolean get() = stack.size > 1

  /** Un tocco sulla barra: riparte da quella scheda. */
  fun selectTab(route: String) {
    stack = listOf(GalleryEntry.Tab(route))
  }

  /** Torna alla scheda in fondo, senza cambiarla (un secondo tocco sulla scheda gia' scelta). */
  fun popToRoot() {
    stack = listOf(stack.first())
  }

  /** Apre una pagina sopra quello che c'e'. Se e' gia' quella in cima non ne impila una seconda. */
  fun open(page: GalleryPage) {
    if (this.page == page) return
    stack = stack + GalleryEntry.Page(page)
  }

  /** Apre una scheda sopra quello che c'e' (da una voce di "Da provare"): "Indietro" torna a chi l'ha aperta. */
  fun openTab(route: String) {
    if (current == GalleryEntry.Tab(route)) return
    stack = stack + GalleryEntry.Tab(route)
  }

  /** Un passo indietro. Falso se non c'e' dove andare: tocca al sistema. */
  fun back(): Boolean {
    if (!canGoBack) return false
    stack = stack.dropLast(1)
    return true
  }

  /** Rimette la pila a [entries] (le prove ripartono da un posto noto). */
  fun reset(entries: List<GalleryEntry>) {
    stack = normalized(entries)
  }

  companion object {
    /** Dove parte la beta: la scheda Materiale con "Da provare" aperta sopra. */
    val Launch: List<GalleryEntry> = listOf(
      GalleryEntry.Tab(RouteMaterial),
      GalleryEntry.Page(GalleryPage.ToTry),
    )
  }
}

/** Una pila ha sempre una scheda in fondo: senza, la barra non saprebbe cosa evidenziare. */
private fun normalized(entries: List<GalleryEntry>): List<GalleryEntry> =
  if (entries.firstOrNull() is GalleryEntry.Tab) entries else listOf<GalleryEntry>(GalleryEntry.Tab(RouteMaterial)) + entries
