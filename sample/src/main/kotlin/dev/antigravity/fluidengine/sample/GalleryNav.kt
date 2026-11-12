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
