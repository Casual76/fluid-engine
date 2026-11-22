package dev.antigravity.fluidengine.sample.dynamic

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import dev.antigravity.fluidengine.sample.catalog.NotificationSendDemo
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.sample.toolbar.ToolbarDemoPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// «Elementi dinamici» (piano «iOS in Android», F6B.12): la galleria degli elementi che sono parte dell'interfaccia e non
// della pagina e che si trasformano. Un file per elemento, in questa cartella; ogni scena è il caso di un'app di Alessio
// rifatto **solo con l'engine** (F6B.0, §12): Mail, Notes, Gallery, Fluidify, Weather. Le app non migrano (scelta di
// Alessio del 21 novembre 2026): sono scene della galleria, e provano che i preset bastano senza codice di movimento.
//
// La scheda è `skill/fluid-engine/references/componenti/elementi-dinamici.md`.

/** Le parti della galleria: l'elemento e la scena. */
internal enum class DynamicElement(val title: String) {
  Selection("Selezione"),
  ActionButtons("Tasti con esito"),
  Undo("«Invia» e il contatore «Annulla»"),
  Notice("L'avviso dal tasto"),
  ElementToPage("Un elemento che diventa pagina"),
}

/**
 * Una scena della galleria. [key] è il valore dell'extra di lancio `dynamic.scene` (solo nella build di debug):
 * `adb shell am start -n dev.antigravity.fluidglass/dev.antigravity.fluidengine.sample.GalleryActivity
 * --es gallery.page DynamicElements --es dynamic.scene selezione-posta`.
 */
internal enum class DynamicScene(
  val key: String,
  val element: DynamicElement,
  val title: String,
  val detail: String,
) {
  SelectionMail(
    "selezione-posta", DynamicElement.Selection, "Posta in arrivo",
    "Mail · la tab bar diventa le azioni; su tablet la barra sta nel pannello della lista, accanto a rail e barra laterale",
  ),
  SelectionNotes(
    "selezione-note", DynamicElement.Selection, "Una cartella di note",
    "Notes · «Seleziona tutto» su una lista che si carica a pagine, togli una nota, le azioni in alto",
  ),
  SelectionPhotos(
    "selezione-foto", DynamicElement.Selection, "La libreria di foto",
    "Gallery · la capsula delle azioni galleggia sulla griglia, che le passa sotto",
  ),
  SelectionFiles(
    "selezione-file", DynamicElement.Selection, "I file, senza tab bar",
    "File · la barra in basso della pagina cambia azioni nello stesso vetro",
  ),
  ActionButtons(
    "tasti", DynamicElement.ActionButtons, "I sei preset e uno su misura",
    "Mail, Notes, Fluidify · riuscita, fallimento, lavoro breve, pausa e ripresa",
  ),
  SendUndo(
    "invia", DynamicElement.Undo, "«Invia» e «Nuova mail»",
    "Mail · dopo l'invio il compositore si chiude e «Nuova mail» diventa «Annulla» e si riempie",
  ),
  Notice(
    "avviso", DynamicElement.Notice, "L'avviso che nasce dal tasto",
    "Mail · «Archivia» e «Segnala» pubblicano un avviso con testo che nasce dal loro vetro (non è di serie)",
  ),
  ElementToPage(
    "elemento", DynamicElement.ElementToPage, "Mini lettore, griglia, pillola, «Scrivi»",
    "Fluidify, Gallery, Weather, Mail · Sale, Zoom, Cresce e Dalla barra",
  ),
  ;

  companion object {
    /** La scena chiesta con l'extra `dynamic.scene`, o nulla. Un valore sconosciuto apre l'indice. */
    fun of(key: String?): DynamicScene? {
      val wanted = key?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
      return entries.firstOrNull { it.key == wanted || it.name.lowercase() == wanted }
    }
  }
}

/** Gli extra di lancio della pagina (solo nella build di debug), come quelli di «Barre (prova)». */
internal object DynamicLaunch {
  const val ExtraScene = "dynamic.scene"
}

/** I tag dei test. */
internal object DynamicTags {
  const val Index = "dinamici:indice"

  fun row(scene: DynamicScene): String = "dinamici:${scene.key}"
}

/**
 * La pagina «Elementi dinamici» della galleria: l'indice delle scene, raggruppate per elemento, e la scena aperta. Il
 * back della scena torna all'indice; quello dell'indice chiude la pagina ([onBack]).
 *
 * [initial] serve ai test: la galleria apre di serie la scena chiesta con `dynamic.scene`, o l'indice.
 */
@Composable
internal fun DynamicElementsPage(onBack: () -> Unit, initial: DynamicScene? = null) {
  val context = LocalContext.current
  val launched = remember(context) {
    initial ?: context.findActivity()?.takeIf { context.isDebuggableApp() }
      ?.intent?.getStringExtra(DynamicLaunch.ExtraScene)?.let(DynamicScene::of)
  }
  var open by rememberSaveable { mutableStateOf(launched?.key) }
  val scene = DynamicScene.of(open)
  // Prima della scena: i gestori della scena (la selezione, l'host delle pagine) si registrano dopo e vincono.
  BackHandler(enabled = scene != null) { open = null }
  val close: () -> Unit = { open = null }
  ToolbarDemoPolicy {
    // Una chiave per scena: lo stato di una (la selezione, la pagina aperta) non resta a quella dopo.
    key(scene) {
      when (scene) {
        null -> DynamicElementsIndex(onBack = onBack, onOpen = { open = it.key })
        DynamicScene.SelectionMail -> SelectionMailScene(onBack = close)
        DynamicScene.SelectionNotes -> SelectionNotesScene(onBack = close)
        DynamicScene.SelectionPhotos -> SelectionPhotosScene(onBack = close)
        DynamicScene.SelectionFiles -> SelectionFilesScene(onBack = close)
        DynamicScene.ActionButtons -> ActionButtonsScene(onBack = close)
        // La scena c'è già (F6B.4): la voce «Avvisi con azione» la apre dal suo tasto, qui si apre da sola.
        DynamicScene.SendUndo -> NotificationSendDemo(onBack = close)
        DynamicScene.Notice -> NoticeFromButtonScene(onBack = close)
        // Le scene di F6B.5c, con il loro indice e le loro rotte.
        DynamicScene.ElementToPage -> ElementToPagePage(onClose = close)
      }
    }
  }
}

/** L'indice: per ogni elemento le sue scene, con il caso dell'app e cosa provare. */
@Composable
private fun DynamicElementsIndex(onBack: () -> Unit, onOpen: (DynamicScene) -> Unit) {
  FluidScreen(
    title = "Elementi dinamici",
    // Il segno della pagina per le prove della galleria (`FluidBetaTest`): la sigla del pacchetto.
    subtitle = "F6B.12 · selezione, tasti con esito, Annulla, avviso dal tasto, elemento che diventa pagina",
    onBack = onBack,
    modifier = Modifier.testTag(DynamicTags.Index),
  ) {
    DynamicElement.entries.forEach { element ->
      val scenes = DynamicScene.entries.filter { it.element == element }
      item(key = "${element.name}-intestazione") { FluidSectionHeader(title = element.title) }
      item(key = "${element.name}-scene") {
        FluidListGroup {
          scenes.forEachIndexed { index, scene ->
            if (index > 0) FluidListDivider()
            FluidListRow(
              title = scene.title,
              subtitle = scene.detail,
              meta = "dynamic.scene ${scene.key}",
              modifier = Modifier.testTag(DynamicTags.row(scene)),
              onClick = { onOpen(scene) },
            )
          }
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Ogni scena è un caso di un'app rifatto con il solo engine: nessuna riga di movimento nella scena. " +
          "La scheda è «Elementi dinamici» nella skill del Fluid Engine.",
      )
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
