package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// «Elementi nuovi» (piano dell'ecosistema, F8): la pagina della galleria con gli elementi nuovi scelti da Alessio il 23
// novembre 2026, che fra qualche settimana diventera' parte della vetrina dell'engine (F11). Le regole comuni di F8 la
// vogliono cosi', per non litigare fra pacchetti paralleli: ogni pacchetto aggiunge **un** file di scene,
// `F8p<n>Scenes.kt`, e **una** riga a [NewElementSections]. All'unione le versioni si fondono.

/**
 * Una scena: [id] e' il nome breve, per le riprese e per i log (l'extra di lancio `novita.scene`, solo nella build di
 * debug: `adb shell am start -n dev.antigravity.fluidglass/dev.antigravity.fluidengine.sample.GalleryActivity
 * --es gallery.page NewElements --es novita.scene ruota-timer`).
 */
internal class NewElementScene(
  val id: String,
  val title: String,
  val detail: String,
  val content: @Composable (onBack: () -> Unit) -> Unit,
)

/** Le scene di un pacchetto di F8: la sigla ([pack], «F8.2»), il titolo, le scene e una riga facoltativa ([detail]). */
internal class NewElementSection(
  val pack: String,
  val title: String,
  val scenes: List<NewElementScene>,
  val detail: String? = null,
)

/** Un pacchetto per riga, nell'ordine del piano. */
internal val NewElementSections: List<NewElementSection> = listOf(
  F8p1Section,
  F8p2Section,
  F8p3Section,
  F8p4Section,
  NewElementSection(pack = "F8.5", title = "Festa e simboli", scenes = F8p5Scenes, detail = "I coriandoli di un successo e gli effetti di SF Symbols sulle icone"),
  F8p6Section,
  NewElementSection(pack = "F8.7", title = "Colore", scenes = F8p7Scenes, detail = "Il selettore di colore di iOS e la pagina tinta dalla copertina, come Apple Music"),
  NewElementSection(pack = "F8.12", title = "Indirizzi delle pagine", scenes = F8p12Scenes, detail = "Un link apre la nota nel suo quaderno."),
  NewElementSection(pack = "F8.8", title = "Date", scenes = F8p8Scenes, detail = "Il calendario in pagina, intervalli e giorni sparsi con la capsula della data, e il pannello «Ripeti»"),
  NewElementSection(pack = "F8.9", title = "Dati nel testo", scenes = F8p9Scenes, detail = "Date, orari, indirizzi, telefoni, mail e link dentro un avviso"),
  NewElementSection(pack = "F8.11", title = "Prima volta, permessi e avvio", scenes = F8p11Scenes, detail = "Il benvenuto e le novità una volta per versione, il permesso chiesto dal tasto, gli sfondi vivi e il primo fotogramma pronto"),
  NewElementSection(pack = "F8.10", title = "Condivisione", scenes = F8p10Scenes, detail = "Il foglio di vetro, la copia preparata, immagini, PDF e stampa, e gli arrivi da altre app"),
  F8p19Section,
  F8p21Section,
  F8p23Section,
  F8p20Section,
  F8p18Section,
  F8p14Section,
  F8p13Section,
  F8p16Section,
  F8p22Section,
  F8p15Section,
  F8p17Section,
  F8p25Section,
  F8p26Section,
  F8p24Section,
  F9p1Section,
  F9p2Section,
)

/** Gli extra di lancio della pagina (solo nella build di debug). */
internal object NewElementsLaunch {
  const val ExtraScene = "novita.scene"
}

/** I tag dei test. */
internal object NewElementsTags {
  const val Index = "novita:indice"

  fun row(id: String): String = "novita:$id"
}

/** La scena con l'[id] dato, in tutti i pacchetti, o nulla. */
internal fun newElementScene(id: String?): NewElementScene? {
  val wanted = id?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
  return NewElementSections.firstNotNullOfOrNull { section -> section.scenes.firstOrNull { it.id == wanted } }
}

/**
 * La pagina «Elementi nuovi»: l'indice delle scene, un gruppo per pacchetto, e la scena aperta. Il back della scena torna
 * all'indice; quello dell'indice chiude la pagina ([onBack]). La scena chiesta con `novita.scene` si apre da sola.
 */
@Composable
internal fun NewElementsPage(onBack: () -> Unit, initial: String? = null) {
  val context = LocalContext.current
  val launched = remember(context) {
    initial ?: context.findActivity()?.takeIf { context.isDebuggableApp() }?.intent
      ?.getStringExtra(NewElementsLaunch.ExtraScene)
  }
  NewElementsPageAt(onBack = onBack, initial = launched)
}

/** La pagina con la scena [initial] gia' aperta (per i test e per l'extra di lancio). */
@Composable
internal fun NewElementsPageAt(onBack: () -> Unit, initial: String?) {
  var open by rememberSaveable { mutableStateOf(newElementScene(initial)?.id) }
  val scene = newElementScene(open)
  BackHandler(enabled = scene != null) { open = null }
  // Una chiave per scena: lo stato di una non resta alla dopo.
  key(scene?.id) {
    if (scene == null) {
      NewElementsIndex(onBack = onBack, onOpen = { open = it.id })
    } else {
      scene.content { open = null }
    }
  }
}

@Composable
private fun NewElementsIndex(onBack: () -> Unit, onOpen: (NewElementScene) -> Unit) {
  FluidScreen(
    title = "Elementi nuovi",
    // Il segno della pagina per le prove della galleria (`FluidBetaTest`): la sigla della fase.
    subtitle = "F8 · gli elementi nuovi scelti il 23 novembre, con i casi delle app",
    onBack = onBack,
    modifier = Modifier.testTag(NewElementsTags.Index),
    toolbar = { title(FluidToolbarTitleMode.Inline) },
  ) {
    NewElementSections.forEach { section ->
      item(key = "sezione-${section.pack}") {
        Column {
          FluidSectionHeader(title = "${section.pack} · ${section.title}", detail = section.detail)
          FluidListGroup {
            section.scenes.forEachIndexed { index, scene ->
              if (index > 0) FluidListDivider()
              FluidListRow(
                title = scene.title,
                subtitle = scene.detail,
                subtitleMaxLines = 2,
                meta = "novita.scene ${scene.id}",
                modifier = Modifier.testTag(NewElementsTags.row(scene.id)),
                onClick = { onOpen(scene) },
              )
            }
          }
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Ogni scena e' un caso di un'app fatto con il solo engine, su fondi diversi (pagina, foto chiara, foto " +
          "scura). La scheda di ogni elemento e' nella skill del Fluid Engine.",
      )
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
