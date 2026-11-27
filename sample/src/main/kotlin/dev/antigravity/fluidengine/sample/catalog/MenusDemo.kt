package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.material.LocalGalleryGlassSwitches
import dev.antigravity.fluidengine.sample.measure.generateMeasurePhoto
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidMenuActions
import dev.antigravity.fluidengine.ui.fluid.FluidMenuElementSize
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOrder
import dev.antigravity.fluidengine.ui.fluid.FluidPickerOption
import dev.antigravity.fluidengine.ui.fluid.FluidPickerRow
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMenuPointOfLight
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreVert
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.LocalFluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow

/**
 * I menu liquidi come stanno dopo F5A, in quattro capitoli che si leggono in ordine: **il menu che nasce da un tasto o da
 * un gruppo** (il tasto che si trasforma, le azioni della barra, il gruppo della barra dichiarata), **la struttura** (fila di
 * simboli, sottomenu nello stesso vetro, voci che lasciano il menu aperto, `Priority` e `Fixed`), **il contestuale** (su una
 * riga, su una tessera con la foto e la sua anteprima, su un tondo e su una card) e, solo dove la galleria ha i suoi
 * interruttori, **la riga di debug dello sfondo** del contestuale. In fondo l'avviso, che non e' un menu ma vive nello
 * stesso host.
 *
 * La regola di tutti: **un menu si sceglie col dito che l'ha aperto, e un rilascio altrove lo lascia aperto.** Tieni
 * premuto: il menu si apre (un colpo, `Open`). Senza sollevare il dito, trascinalo sulle voci: una goccia di vetro lo segue
 * di voce in voce, un tick per voce, e al rilascio su una voce la scegli (`Tap`). Rilascia fermo, o fuori dalle voci: il
 * menu resta aperto e si sceglie con un tocco.
 *
 * Una colonna sola, cosi' va ovunque: la monta chi prepara la galleria. Il tasto che si trasforma ha il suo host DENTRO il
 * palco (il suo menu cresce li'); tutto il resto usa l'host dei modali della radice (`LocalFluidGlassModalHostState`), come
 * in un'app. I nomi delle sezioni e i `testTag` sono quelli che le riprese del tablet citano: cambiarli rompe le sessioni.
 */
@Composable
fun MenusDemo() {
  var lastPick by remember { mutableStateOf<String?>(null) }
  val pick: (String) -> Unit = { lastPick = it }

  Column(
    modifier = Modifier.fillMaxWidth().testTag("menus-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = lastPick?.let { "Scelta: $it" } ?: "Tieni premuto e trascina su una voce, oppure rilascia e tocca.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp),
    )
    // Il palco del tasto che si trasforma resta il primo palco della pagina: le riprese lo usano senza scorrere.
    Chapter(
      title = "Dal tasto e dal gruppo",
      detail = "Il vetro del tasto, o di tutto il gruppo, si raccoglie, sale come una goccia verso il centro del menu e si " +
        "apre da lì; le voci sono dentro dal primo fotogramma, sfocate e piegate dalla lente.",
    )
    MorphSection(pick)
    BarSection(pick)
    GroupMenuSection(pick)
    Chapter(
      title = "La struttura",
      detail = "Una fila di simboli in cima, un sottomenu che si apre nello stesso vetro, una voce che lascia il menu " +
        "aperto, e l'ordine che si capovolge aprendosi verso l'alto.",
    )
    StructureSection(pick)
    OrderSection(pick)
    Chapter(
      title = "Il menu contestuale",
      detail = "Tieni premuto: l'elemento si solleva, la pagina si scurisce con una sfocatura leggera e il menu nasce " +
        "accanto, da una goccia appesa al suo bordo.",
    )
    RowsSection(pick)
    PreviewSection(pick)
    ContextScrimPicker()
    Chapter(
      title = "Nello stesso host",
      detail = "L'avviso non è un menu, ma vive nello stesso host dei modali e si sceglie col dito allo stesso modo.",
    )
    AlertSection(pick)
  }
}

/**
 * Il titolo di un capitolo della pagina: piu' grande delle sezioni, perche' la pagina si legge come un racconto (dal
 * tasto, la struttura, il contestuale) e non come un elenco di palchi.
 */
@Composable
private fun Chapter(title: String, detail: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
    Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Le voci di prova: due sezioni, una voce spenta, una distruttiva. */
private fun demoActions(pick: (String) -> Unit): List<FluidContextAction> = listOf(
  FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { pick("Condividi") },
  FluidContextAction(label = "Aggiungi ai preferiti", icon = FluidIcons.Filled.Star) { pick("Aggiungi ai preferiti") },
  FluidContextAction(label = "Modifica (spenta)", icon = FluidIcons.Edit, enabled = false) { pick("Modifica") },
  FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {
    pick("Elimina")
  },
)

// --- Il tasto che si trasforma ---------------------------------------------------------------------

@Composable
private fun MorphSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Il tasto che si trasforma",
    detail = "Tieni premuto \"Azione\"; \"Ordina\" si apre al tocco, con la spunta sulla scelta di adesso. In alto il " +
      "punto di luce della goccia, acceso di serie.",
  )
  Stage(height = 360.dp) { backdrop ->
    val menu = rememberFluidMorphMenuState()
    var order by remember { mutableStateOf("Per data") }
    var light by remember { mutableStateOf(true) }
    // Lo stato del menu arriva ai tasti dal fornitore (3.0.1): nessuno dei due lo riceve come parametro.
    CompositionLocalProvider(LocalFluidMorphMenuState provides menu, LocalFluidMenuPointOfLight provides light) {
      FluidButton(
        text = if (light) "Punto di luce acceso" else "Punto di luce spento",
        onClick = { light = !light },
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
      )
      Row(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        FluidMorphMenuButton(
          actions = { demoActions(pick) },
          onClick = { pick("Azione (tocco)") },
          text = "Azione",
          backdrop = backdrop,
          modifier = Modifier.testTag("menus-morph"),
        )
        FluidMorphMenuButton(
          actions = {
            listOf("Per data", "Per nome", "Per dimensione").map { label ->
              FluidContextAction(label = label, selected = label == order) {
                order = label
                pick(label)
              }
            }
          },
          onClick = {},
          text = "Ordina",
          backdrop = backdrop,
          opensOnTap = true,
          modifier = Modifier.testTag("menus-morph-tap"),
        )
      }
      // L'host del palco: il menu cresce dentro il palco, sopra i tasti.
      FluidMorphMenuHost(state = menu, backdrop = backdrop)
    }
  }
}

@Composable
private fun GroupMenuSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Il gruppo che diventa menu",
    detail = "Tocca o tieni premuto «Menu del gruppo»: tutta la capsula si stringe, diventa goccia e si apre nel menu; " +
      "alla chiusura torna gruppo. Il tasto «Modifica foto» resta al suo posto.",
  )
  GroupMenuStage(pick)
}

/** L'id dell'azione «Menu del gruppo» del palco: la scena `menu-gruppo-barra` della pagina «Misure» la tocca da qui. */
internal const val GroupMenuStageActionId = "photo-menu"

/**
 * Il palco «Il gruppo che diventa menu»: una schermata annidata alta 320 dp con la barra in basso (due gruppi, il primo
 * apre il menu). Lo monta questa pagina e, uguale, la scena `menu-gruppo-barra` della pagina «Misure» (F7.27), perché le
 * corse automatiche sul tablet misurino proprio questa barra.
 */
@Composable
internal fun GroupMenuStage(pick: (String) -> Unit) {
  val photo = remember { generateMeasurePhoto() }
  FluidScreen(
    title = "Foto",
    modifier = Modifier.height(320.dp).clip(ContinuousCornerShape(FluidRadius.Group)).testTag("menus-group"),
    toolbar = {
      group(id = "photo-tools", placement = FluidToolbarPlacement.BottomBar) {
        button("share-photo", FluidIcons.Share, "Condividi foto", { pick("Condividi foto") })
        button("star-photo", FluidIcons.Filled.Star, "Preferiti foto", { pick("Preferiti foto") })
        action(GroupMenuStageActionId, FluidIcons.MoreVert, "Menu del gruppo") { demoActions(pick) }
        flexibleSpacer()
      }
      group(id = "photo-edit", placement = FluidToolbarPlacement.BottomBar) {
        button("edit-photo", FluidIcons.Edit, "Modifica foto", { pick("Modifica foto") })
      }
    },
  ) {
    item {
      Image(
        bitmap = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(228.dp),
      )
    }
  }
}

// --- Le azioni della barra -------------------------------------------------------------------------

@Composable
private fun BarSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Le azioni della barra",
    detail = "La stella si tiene premuta; i tre puntini si aprono al tocco. Il vetro del tasto diventa il menu, in tre tempi.",
  )
  Stage(height = 96.dp) { backdrop ->
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
      Row(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 20.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        FluidBarAction(
          icon = FluidIcons.Filled.Star,
          contentDescription = "Preferiti",
          onClick = { pick("Stella (tocco)") },
          actions = { demoActions(pick) },
          modifier = Modifier.testTag("menus-bar"),
        )
        FluidBarAction(
          icon = FluidIcons.MoreVert,
          contentDescription = "Altro",
          onClick = {},
          actions = { demoActions(pick) },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.testTag("menus-bar-tap"),
        )
      }
    }
  }
}

// --- Le righe --------------------------------------------------------------------------------------

@Composable
private fun RowsSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Righe con il menu contestuale",
    detail = "Tieni premuta una riga: si solleva dal punto in cui il dito l'ha gonfiata, il menu cresce accanto.",
  )
  FluidListGroup {
    FluidListRow(
      title = "Nota della riunione",
      subtitle = "Tieni premuto, trascina, rilascia",
      onClick = { pick("Nota (tocco)") },
      contextActions = { demoActions(pick) },
      modifier = Modifier.testTag("menus-row"),
    )
    FluidListDivider()
    FluidListRow(
      title = "Lista della spesa",
      subtitle = "Rilascia fermo, poi tocca",
      onClick = { pick("Lista (tocco)") },
      contextActions = { demoActions(pick) },
    )
  }
}

// --- Lo sfondo del contestuale, solo debug (correzione dopo S9) ---------------------------------------

/**
 * I tre livelli dello sfondo del contestuale (A, B, C di serie), per le catture a schermo intero
 * del tablet da cui Alessio sceglie: la stessa manopola di `--es gallery.tune ctxscrim=a`. Solo dove la galleria ha i
 * suoi interruttori (debug e bench, mai nella galleria dello store); vale dal contestuale successivo, senza far
 * rinascere la pagina, quindi le tre catture si fanno una dopo l'altra sulla stessa riga.
 */
@Composable
private fun ContextScrimPicker() {
  val switches = LocalGalleryGlassSwitches.current ?: return
  val context = LocalContext.current
  Chapter(
    title = "Solo nelle build di prova",
    detail = "Le manopole per le catture del tablet: nella galleria dello store questa parte non c'è.",
  )
  FluidSectionHeader(
    title = "Sfondo del contestuale (solo debug)",
    detail = "I tre candidati di F5A.4, per le catture a schermo intero. Da adb: --es gallery.tune ctxscrim=a|b|c.",
  )
  FluidListGroup {
    FluidPickerRow(
      title = "Livello",
      subtitle = "gallery.tune ctxscrim · vale dal prossimo menu",
      selectedKey = switches.tuning[ContextScrimKnob] ?: "c",
      options = listOf(
        FluidPickerOption("a", "A, il minimo", subtitle = "Nero 18% sul chiaro e 30% sullo scuro, sfocatura 3,75 dp"),
        FluidPickerOption("b", "B, l'intermedio", subtitle = "Nero 24% e 38%, sfocatura 5 dp"),
        FluidPickerOption("c", "C, il massimo (di serie)", subtitle = "Nero 32% e 48%, sfocatura 6,5 dp"),
      ),
      onSelected = { switches.tuneLive(context, ContextScrimKnob, it) },
      modifier = Modifier.testTag("menus-context-scrim"),
    )
  }
}

/** Il nome della manopola dell'engine (`FluidContextScrimTuning.Knob`, interna al modulo), come lo scrive `gallery.tune`. */
private const val ContextScrimKnob = "ctxscrim"

// --- Una card qualunque ------------------------------------------------------------------------------

/** Il caso piu' semplice del contestuale: senza anteprima ne' forma, e' l'elemento stesso a sollevarsi. */
@Composable
private fun CardSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Una card qualunque",
    detail = "Modifier.fluidContextMenu senza anteprima ne' forma, per chi non ha un click suo: si solleva lei.",
  )
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(96.dp)
      .clip(ContinuousCornerShape(FluidRadius.Card))
      .drawBehind { drawRect(Color(0xFF3A7BD5).copy(alpha = 0.18f)) }
      .fluidContextMenu(actions = {
        listOf(
          FluidContextAction(label = "Copia", icon = FluidIcons.Share) { pick("Copia") },
          FluidContextAction(label = "Mi piace", icon = FluidIcons.Filled.Favorite) { pick("Mi piace") },
        )
      })
      .testTag("menus-card"),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = "Tieni premuto qui", style = MaterialTheme.typography.titleMedium)
  }
}

// --- L'anteprima e la forma (F5A.4) ------------------------------------------------------------------

/**
 * I due parametri di `contextMenu(menuItems:preview:)` e `contentShape(.contextMenuPreview, _)`: una tessera con la foto
 * che, tenuta premuta, diventa un'anteprima piu' grande di se' (la card va dalla tessera all'anteprima, i due contenuti si
 * scambiano attraverso la sfocatura, il menu si apre accanto all'anteprima), e un tondo che si solleva tondo. Sotto, la
 * card qualunque: lo stesso modificatore senza anteprima ne' forma.
 *
 * Fino a F5A.5 c'era anche una tessera a gradiente con la stessa anteprima: la tessera con la foto (correzione dopo S9) fa
 * la stessa cosa su un'immagine con i suoi dettagli, che e' dove si giudicano la messa a fuoco e lo sfondo.
 */
@Composable
private fun PreviewSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Anteprima e forma",
    detail = "Modifier.fluidContextMenu(preview = …, previewShape = …): la foto diventa la sua anteprima, il tondo resta tondo.",
  )
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
    // La foto della galleria: su un gradiente non si giudica come l'anteprima si mette a fuoco, ne' come lo sfondo del
    // contestuale scurisce un'immagine con i suoi dettagli.
    val photo = remember { generateMeasurePhoto() }
    Box(
      modifier = Modifier
        .size(96.dp)
        .clip(ContinuousCornerShape(FluidRadius.Card))
        .fluidContextMenu(
          actions = {
            listOf(
              FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { pick("Condividi (foto)") },
              FluidContextAction(label = "Mi piace", icon = FluidIcons.Filled.Favorite) { pick("Mi piace (foto)") },
              FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {
                pick("Elimina (foto)")
              },
            )
          },
          previewShape = ContinuousCornerShape(FluidRadius.Group),
          preview = {
            Box(
              modifier = Modifier.width(280.dp).height(180.dp),
              contentAlignment = Alignment.BottomStart,
            ) {
              Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
              )
              Text(
                text = "Dune, 18 novembre",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.padding(16.dp),
              )
            }
          },
        )
        .testTag("menus-preview-photo"),
    ) {
      Image(
        bitmap = photo,
        contentDescription = "Foto",
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
    }
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .drawBehind { drawRect(Color(0xFF34C759).copy(alpha = 0.85f)) }
        .fluidContextMenu(
          actions = {
            listOf(
              FluidContextAction(label = "Scrivi", icon = FluidIcons.Edit) { pick("Scrivi (tondo)") },
              FluidContextAction(label = "Rimuovi", icon = FluidIcons.Delete, destructive = true) { pick("Rimuovi (tondo)") },
            )
          },
          previewShape = CircleShape,
        )
        .testTag("menus-preview-circle"),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = "AP", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
  }
  CardSection(pick)
}

// --- L'avviso --------------------------------------------------------------------------------------

@Composable
private fun AlertSection(pick: (String) -> Unit) {
  FluidSectionHeader(title = "L'avviso", detail = "Nell'host: vetro vero, e i pulsanti con la goccia.")
  var twoButtons by remember { mutableStateOf(false) }
  var threeButtons by remember { mutableStateOf(false) }
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    FluidButton(text = "Due pulsanti", onClick = { twoButtons = true }, style = FluidButtonStyle.Tinted)
    FluidButton(text = "Tre pulsanti", onClick = { threeButtons = true }, style = FluidButtonStyle.Tinted)
  }
  if (twoButtons) {
    FluidAlert(
      onDismissRequest = { twoButtons = false },
      title = "Eliminare la nota?",
      message = "Trascina il dito fra i due pulsanti: la goccia lo segue.",
      actions = listOf(
        FluidAlertAction(label = "Annulla", onClick = { twoButtons = false }),
        FluidAlertAction(
          label = "Elimina",
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            pick("Elimina (avviso)")
            twoButtons = false
          },
        ),
      ),
    )
  }
  if (threeButtons) {
    FluidAlert(
      onDismissRequest = { threeButtons = false },
      title = "Salvare le modifiche?",
      message = "Tre pulsanti stanno in pila.",
      actions = listOf(
        FluidAlertAction(
          label = "Salva",
          emphasis = FluidAlertAction.Emphasis.Preferred,
          onClick = {
            pick("Salva")
            threeButtons = false
          },
        ),
        FluidAlertAction(
          label = "Non salvare",
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            pick("Non salvare")
            threeButtons = false
          },
        ),
        FluidAlertAction(label = "Annulla", onClick = { threeButtons = false }),
      ),
    )
  }
}

// --- La struttura (F5A.3) -----------------------------------------------------------------------------

/**
 * La struttura dei menu della 4.0 su una riga: la fila di simboli in cima ([FluidMenuElementSize.Small]), un sottomenu che
 * si apre dentro il vetro («Ordina», col sottotitolo che dice la scelta di adesso) e un interruttore che lascia il menu
 * aperto («Mostra anteprime», letto dal vivo da [FluidMenuActions]).
 */
@Composable
private fun StructureSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Struttura: fila, sottomenu, ordine, chiusura",
    detail = "Tieni premuta la riga: la fila in cima, «Ordina» si apre nel vetro, «Mostra anteprime» lascia il menu aperto.",
  )
  var sort by remember { mutableStateOf("Per data") }
  var previews by remember { mutableStateOf(true) }
  val structured: () -> List<FluidContextAction> = {
    FluidMenuActions(elementSize = FluidMenuElementSize.Small) {
      listOf(
        FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { pick("Condividi (fila)") },
        FluidContextAction(label = "Preferiti", icon = FluidIcons.Filled.Star) { pick("Preferiti (fila)") },
        FluidContextAction(label = "Modifica", icon = FluidIcons.Edit) { pick("Modifica (fila)") },
        FluidContextAction(
          label = "Ordina",
          subtitle = sort,
          startsSection = true,
          children = listOf("Per data", "Per nome", "Per dimensione").map { label ->
            FluidContextAction(label = label, selected = label == sort) {
              sort = label
              pick("Ordina: $label")
            }
          },
        ) {},
        FluidContextAction(label = "Mostra anteprime", selected = previews, keepsMenuOpen = true) {
          previews = !previews
          pick(if (previews) "Anteprime accese" else "Anteprime spente")
        },
        FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {
          pick("Elimina (struttura)")
        },
      )
    }
  }
  FluidListGroup {
    FluidListRow(
      title = "Cartella Progetti",
      subtitle = "Ordine: $sort · anteprime ${if (previews) "accese" else "spente"}",
      onClick = { pick("Cartella (tocco)") },
      contextActions = structured,
      modifier = Modifier.testTag("menus-structure-row"),
    )
  }
}

/**
 * L'ordine e la fila `Medium`: due tasti in basso con le stesse voci (`Priority` si capovolge aprendosi verso l'alto,
 * `Fixed` no) e un tasto in alto con la fila [FluidMenuElementSize.Medium]. Il pannello di un tasto si apre verso l'alto
 * solo se sotto non ci sta: i due tasti vanno portati nella parte bassa dello schermo.
 */
@Composable
private fun OrderSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Priority e Fixed, verso l'alto",
    detail = "Scorri finché il palco sta in fondo allo schermo e tocca i due tasti: con Priority «Uno» resta vicino al dito. " +
      "La fila Medium si apre dal tasto in alto.",
  )
  val ordered: (FluidMenuOrder) -> List<FluidContextAction> = { order ->
    FluidMenuActions(
      actions = listOf(
        FluidContextAction(label = "Uno", icon = FluidIcons.Share) { pick("Uno ($order)") },
        FluidContextAction(label = "Due", icon = FluidIcons.Filled.Star) { pick("Due ($order)") },
        FluidContextAction(label = "Tre", icon = FluidIcons.Edit, startsSection = true) { pick("Tre ($order)") },
        FluidContextAction(label = "Quattro", icon = FluidIcons.Delete, destructive = true) { pick("Quattro ($order)") },
      ),
      order = order,
    )
  }
  Stage(height = 200.dp) { backdrop ->
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
      FluidBarAction(
        icon = FluidIcons.MoreVert,
        contentDescription = "Fila Medium",
        onClick = {},
        actions = {
          FluidMenuActions(
            actions = listOf(
              FluidContextAction(label = "Copia", icon = FluidIcons.Share) { pick("Copia (Medium)") },
              FluidContextAction(label = "Stella", icon = FluidIcons.Filled.Star) { pick("Stella (Medium)") },
              FluidContextAction(label = "Scrivi", icon = FluidIcons.Edit) { pick("Scrivi (Medium)") },
              FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {
                pick("Elimina (Medium)")
              },
            ),
            elementSize = FluidMenuElementSize.Medium,
          )
        },
        opensOn = FluidMenuOpensOn.Tap,
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).testTag("menus-medium"),
      )
      Row(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidBarAction(
          icon = FluidIcons.MoreVert,
          contentDescription = "Priority",
          onClick = {},
          actions = { ordered(FluidMenuOrder.Priority) },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.testTag("menus-priority"),
        )
        FluidBarAction(
          icon = FluidIcons.MoreVert,
          contentDescription = "Fixed",
          onClick = {},
          actions = { ordered(FluidMenuOrder.Fixed) },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.testTag("menus-fixed"),
        )
      }
    }
  }
}

// --- Il palco ---------------------------------------------------------------------------------------

/**
 * Un riquadro di colori e di testo nitido, registrato come fondale del vetro: una pagina piatta
 * rifrange una pagina piatta, e il vetro non si vedrebbe.
 */
@Composable
private fun Stage(
  height: Dp,
  content: @Composable BoxScope.(GlassBackdropState) -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  val wash = remember {
    Brush.linearGradient(
      colors = listOf(Color(0xFF3A7BD5), Color(0xFFE96443), Color(0xFFF5C451), Color(0xFF2E8B57)),
      start = Offset.Zero,
      end = Offset(900f, 900f),
    )
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .clip(ContinuousCornerShape(FluidRadius.Group)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(backdrop)
        .drawBehind { drawRect(wash) },
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(16) { index ->
          Text(
            text = "Testo nitido dietro il vetro, riga ${index + 1} — ABCDEFG 0123456789",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (index % 2 == 0) Color.Black else Color.White,
          )
        }
      }
    }
    content(backdrop)
  }
}
