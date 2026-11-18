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
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
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
 * I menu liquidi della 3.0 (fase 7), tutti e quattro gli ingressi piu' l'avviso, per provarli con le
 * dita: **un menu si sceglie col dito che l'ha aperto, e un rilascio altrove lo lascia aperto.**
 *
 * Tieni premuto: il menu si apre (un colpo, `Open`). Senza sollevare il dito, trascinalo sulle voci:
 * una goccia di vetro lo segue di voce in voce, un tick per voce, e al rilascio su una voce la scegli
 * (`Tap`). Rilascia fermo, o fuori dalle voci: il menu resta aperto e si sceglie con un tocco. Le voci
 * spente non fanno tick e non si scelgono; le sezioni hanno una banda fra loro.
 *
 * Una colonna sola, cosi' va ovunque: la monta chi prepara la galleria. Il tasto che si trasforma ha il
 * suo host DENTRO il palco (il suo menu cresce li'); le azioni della barra, le righe, la card e
 * l'avviso usano l'host dei modali della radice (`LocalFluidGlassModalHostState`), come in un'app.
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
    MorphSection(pick)
    GroupMenuSection(pick)
    BarSection(pick)
    ContextScrimPicker()
    RowsSection(pick)
    CardSection(pick)
    PreviewSection(pick)
    AlertSection(pick)
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
    detail = "Tieni premuto \"Azione\"; \"Ordina\" si apre al tocco, con la spunta sulla scelta di adesso.",
  )
  Stage(height = 360.dp) { backdrop ->
    val menu = rememberFluidMorphMenuState()
    var order by remember { mutableStateOf("Per data") }
    var light by remember { mutableStateOf(false) }
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
    detail = "Tocca o tieni premuto «Menu del gruppo». Il tasto «Modifica foto» resta al suo posto.",
  )
  val photo = remember { generateMeasurePhoto() }
  FluidScreen(
    title = "Foto",
    modifier = Modifier.height(320.dp).clip(ContinuousCornerShape(FluidRadius.Group)).testTag("menus-group"),
    toolbar = {
      group(id = "photo-tools", placement = FluidToolbarPlacement.BottomBar) {
        button("share-photo", FluidIcons.Share, "Condividi foto", { pick("Condividi foto") })
        button("star-photo", FluidIcons.Filled.Star, "Preferiti foto", { pick("Preferiti foto") })
        action("photo-menu", FluidIcons.MoreVert, "Menu del gruppo") { demoActions(pick) }
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
  FluidSectionHeader(title = "Le azioni della barra", detail = "La stella si tiene premuta; i tre puntini si aprono al tocco.")
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
  FluidSectionHeader(title = "Righe con il menu contestuale", detail = "Tieni premuta una riga: si solleva, il menu cresce accanto.")
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
 * La scelta dello sfondo del contestuale fra i tre candidati di F5A.4 (A, B di serie, C), per le catture a schermo intero
 * del tablet da cui Alessio sceglie: la stessa manopola di `--es gallery.tune ctxscrim=a`. Solo dove la galleria ha i
 * suoi interruttori (debug e bench, mai nella galleria dello store); vale dal contestuale successivo, senza far
 * rinascere la pagina, quindi le tre catture si fanno una dopo l'altra sulla stessa riga.
 */
@Composable
private fun ContextScrimPicker() {
  val switches = LocalGalleryGlassSwitches.current ?: return
  val context = LocalContext.current
  FluidSectionHeader(
    title = "Sfondo del contestuale (solo debug)",
    detail = "I tre candidati di F5A.4, per le catture a schermo intero. Da adb: --es gallery.tune ctxscrim=a|b|c.",
  )
  FluidListGroup {
    FluidPickerRow(
      title = "Livello",
      subtitle = "gallery.tune ctxscrim · vale dal prossimo menu",
      selectedKey = switches.tuning[ContextScrimKnob] ?: "b",
      options = listOf(
        FluidPickerOption("a", "A, il minimo", subtitle = "Nero 18% sul chiaro e 30% sullo scuro, sfocatura 3,75 dp"),
        FluidPickerOption("b", "B, la proposta (di serie)", subtitle = "Nero 24% e 38%, sfocatura 5 dp"),
        FluidPickerOption("c", "C, il massimo", subtitle = "Nero 32% e 48%, sfocatura 6,5 dp"),
      ),
      onSelected = { switches.tuneLive(context, ContextScrimKnob, it) },
      modifier = Modifier.testTag("menus-context-scrim"),
    )
  }
}

/** Il nome della manopola dell'engine (`FluidContextScrimTuning.Knob`, interna al modulo), come lo scrive `gallery.tune`. */
private const val ContextScrimKnob = "ctxscrim"

// --- Una card qualunque ------------------------------------------------------------------------------

@Composable
private fun CardSection(pick: (String) -> Unit) {
  FluidSectionHeader(title = "Una card qualunque", detail = "Modifier.fluidContextMenu, per chi non ha un click suo.")
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
 * I due parametri di `contextMenu(menuItems:preview:)` e `contentShape(.contextMenuPreview, _)`: una tessera che,
 * tenuta premuta, diventa un'anteprima piu' grande di se' (la card va dalla tessera all'anteprima, i due contenuti si
 * scambiano attraverso la sfocatura, il menu si apre accanto all'anteprima), e un tondo che si solleva tondo.
 */
@Composable
private fun PreviewSection(pick: (String) -> Unit) {
  FluidSectionHeader(
    title = "Anteprima e forma",
    detail = "Modifier.fluidContextMenu(preview = …, previewShape = …): la tessera diventa la sua anteprima, il tondo resta tondo.",
  )
  val sky = Brush.linearGradient(listOf(Color(0xFF3A7BD5), Color(0xFF8E54E9), Color(0xFFF2994A)))
  Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(96.dp)
        .clip(ContinuousCornerShape(FluidRadius.Card))
        .drawBehind { drawRect(sky) }
        .fluidContextMenu(
          actions = {
            listOf(
              FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { pick("Condividi (anteprima)") },
              FluidContextAction(label = "Mi piace", icon = FluidIcons.Filled.Favorite) { pick("Mi piace (anteprima)") },
            )
          },
          previewShape = ContinuousCornerShape(FluidRadius.Group),
          preview = {
            Box(
              modifier = Modifier
                .width(280.dp)
                .height(180.dp)
                .drawBehind { drawRect(sky) },
              contentAlignment = Alignment.BottomStart,
            ) {
              Text(
                text = "Tramonto, 18 novembre",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.padding(16.dp),
              )
            }
          },
        )
        .testTag("menus-preview-tile"),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = "Foto", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
    // La stessa tessera con la foto della galleria (correzione dopo S9): su un gradiente non si giudica come
    // l'anteprima si mette a fuoco, ne' come lo sfondo del contestuale scurisce un'immagine con i suoi dettagli.
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
