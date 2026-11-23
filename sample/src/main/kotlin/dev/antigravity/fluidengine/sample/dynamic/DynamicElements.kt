package dev.antigravity.fluidengine.sample.dynamic

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
import dev.antigravity.fluidengine.sample.catalog.NotificationSendDemo
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.sample.toolbar.ToolbarDemoPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

// «Elementi dinamici» (piano «iOS in Android», F6B.12 e F6B.13): la galleria degli elementi che sono parte dell'interfaccia
// e non della pagina e che si trasformano. Un file per elemento, in questa cartella; ogni scena è il caso di un'app di
// Alessio rifatto **solo con l'engine** (F6B.0, §12): Mail, Notes, Gallery, Fluidify, Weather, Transit, PampAI. Le app
// non migrano (scelta di Alessio del 21 novembre 2026): sono scene della galleria, e provano che i preset bastano senza
// codice di movimento.
//
// La scheda è `skill/fluid-engine/references/componenti/elementi-dinamici.md`.

/** Le parti della galleria: l'elemento e la scena. */
internal enum class DynamicElement(val title: String) {
  Selection("Selezione"),
  ActionButtons("Tasti con esito"),
  Undo("«Invia» e il contatore «Annulla»"),
  Notice("L'avviso dal tasto"),
  ElementToPage("Un elemento che diventa pagina"),
  Activity("La pillola di attività"),
  Split("Il tasto diviso"),
  TitleSelector("Il titolo che diventa selettore"),
  // F6B.13
  Field("Il campo e «trova nella pagina»"),
  Composer("Il composer dentro il campo"),
  Filters("Filtri e suggerimenti a pillole"),
  KeyboardBar("La barra sopra la tastiera"),
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
  ActivityPill(
    "attivita", DynamicElement.Activity, "La posta che sincronizza",
    "Mail · la pillola al posto del titolo, la card, l'esito, l'anellino a barra ritirata (F6B.10)",
  ),
  SplitButton(
    "tasto-diviso", DynamicElement.Split, "«Invia ▾» e «Programma invio»",
    "Mail «Scrivi» · un vetro solo con il filo fra azione e freccia; la freccia apre il menu dallo stesso vetro (F6B.11)",
  ),
  TitleMenu(
    "titolo-menu", DynamicElement.TitleSelector, "«Posta in arrivo ▾» con gli account",
    "Mail · il titolo grande e quello agganciato aprono gli account; con un'attività la pillola sta accanto (F6B.11)",
  ),
  TitlePanel(
    "titolo-pannello", DynamicElement.TitleSelector, "La località col pannello",
    "Weather · il titolo apre il pannello delle località; sul telefono un foglio dal titolo (F6B.11)",
  ),
  FieldPresets(
    "campo", DynamicElement.Field, "La ricerca della posta e i preset",
    "Mail · in alto il campo inghiotte «Scrivi» e l'account; Ricerca, Messaggio, Nota, Assistente (F6B.6)",
  ),
  FindInPage(
    "trova", DynamicElement.Field, "«Trova» in una trascrizione",
    "Notes · «3/12» sopra la tastiera, e la barra in alto resta com'è (F6B.6)",
  ),
  MessageComposer(
    "messaggio", DynamicElement.Composer, "«Messaggio» in una chat",
    "Messaggi, Mail · righe, allegati, il tasto a stati; la lista resta sopra il campo (F6B.7)",
  ),
  AssistantComposer(
    "assistente", DynamicElement.Composer, "L'assistente con l'alone",
    "PampAI · chip, pillole, voce, alone; mentre risponde, la pillola di attività (F6B.7)",
  ),
  Filters(
    "filtri", DynamicElement.Filters, "Le fermate coi filtri",
    "Transit · la fila sotto il campo, «Filtri · N» a testo grande, «Periodo», i recenti (F6B.8)",
  ),
  Tokens(
    "gettoni", DynamicElement.Filters, "«da: persona»",
    "Mail · la pillola scelta vola nel campo della ricerca e diventa gettone (F6B.8b)",
  ),
  KeyboardBar(
    "tastiera", DynamicElement.KeyboardBar, "«Scrivi» con una barra per campo",
    "Mail · suggerimenti, azioni rapide, formattazione e «Fatto»; «Invia ▾» (F6B.9)",
  ),
  RichTextKeyboard(
    "testo-ricco", DynamicElement.KeyboardBar, "La barra del testo ricco",
    "Notes · la formattazione come preset della barra sopra la tastiera (F6B.9)",
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
        DynamicScene.ActivityPill -> ActivityPillScene(onBack = close)
        DynamicScene.SplitButton -> SplitButtonScene(onBack = close)
        DynamicScene.TitleMenu -> TitleMenuScene(onBack = close)
        DynamicScene.TitlePanel -> TitlePanelScene(onBack = close)
        DynamicScene.FieldPresets -> FieldPresetsScene(onBack = close)
        DynamicScene.FindInPage -> FindInPageScene(onBack = close)
        DynamicScene.MessageComposer -> MessageComposerScene(onBack = close)
        DynamicScene.AssistantComposer -> AssistantComposerScene(onBack = close)
        DynamicScene.Filters -> FiltersScene(onBack = close)
        DynamicScene.Tokens -> TokensScene(onBack = close)
        DynamicScene.KeyboardBar -> KeyboardBarScene(onBack = close)
        DynamicScene.RichTextKeyboard -> RichTextKeyboardScene(onBack = close)
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
    subtitle = "F6B.12 · F6B.13 · gli elementi che si trasformano, coi casi delle app",
    onBack = onBack,
    modifier = Modifier.testTag(DynamicTags.Index),
    // Con venti scene l'indice tiene il titolo agganciato (il sottotitolo sta nella barra) e un elemento per voce della
    // lista, l'intestazione col suo gruppo: così sta in una pagina alta (la prova dell'indice).
    toolbar = { title(FluidToolbarTitleMode.Inline) },
  ) {
    DynamicElement.entries.forEach { element ->
      val scenes = DynamicScene.entries.filter { it.element == element }
      item(key = "${element.name}-scene") {
        Column {
          FluidSectionHeader(title = element.title)
          FluidListGroup {
            scenes.forEachIndexed { index, scene ->
              if (index > 0) FluidListDivider()
              // Il caso dell'app su una riga (TalkBack lo legge intero).
              FluidListRow(
                title = scene.title,
                subtitle = scene.detail,
                subtitleMaxLines = 1,
                meta = "dynamic.scene ${scene.key}",
                modifier = Modifier.testTag(DynamicTags.row(scene)),
                onClick = { onOpen(scene) },
              )
            }
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
