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
 * Le pagine che la galleria apre sopra una scheda: la lista "Da provare" e le demo che non stanno in
 * nessuna scheda. Ognuna e' una schermata intera con il suo "Indietro".
 */
internal enum class GalleryPage(val title: String) {
  ToTry("Da provare"),
  RowsAndSheets("Righe e fogli"),
  CapsulesAndParts("Capsule e pezzi piccoli"),
  Gaps("I buchi delle app"),
  Menus("Menù liquidi"),
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
