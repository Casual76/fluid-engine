package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
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
    BarSection(pick)
    RowsSection(pick)
    CardSection(pick)
    AlertSection(pick)
  }
}

/** Le voci di prova: due sezioni, una voce spenta, una distruttiva. */
private fun demoActions(pick: (String) -> Unit): List<FluidContextAction> = listOf(
  FluidContextAction(label = "Condividi", icon = Icons.Rounded.Share) { pick("Condividi") },
  FluidContextAction(label = "Aggiungi ai preferiti", icon = Icons.Rounded.Star) { pick("Aggiungi ai preferiti") },
  FluidContextAction(label = "Modifica (spenta)", icon = Icons.Rounded.Edit, enabled = false) { pick("Modifica") },
  FluidContextAction(label = "Elimina", icon = Icons.Rounded.Delete, destructive = true, startsSection = true) {
    pick("Elimina")
  },
)

// --- Il tasto che si trasforma ---------------------------------------------------------------------

@Composable
private fun MorphSection(pick: (String) -> Unit) {
  FluidSectionHeader(title = "Il tasto che si trasforma", detail = "Tieni premuto \"Azione\"; \"Ordina\" si apre al tocco.")
  Stage(height = 360.dp) { backdrop ->
    val menu = rememberFluidMorphMenuState()
    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      FluidMorphMenuButton(
        state = menu,
        actions = { demoActions(pick) },
        onClick = { pick("Azione (tocco)") },
        text = "Azione",
        backdrop = backdrop,
        modifier = Modifier.testTag("menus-morph"),
      )
      FluidMorphMenuButton(
        state = menu,
        actions = {
          listOf(
            FluidContextAction(label = "Per data") { pick("Per data") },
            FluidContextAction(label = "Per nome") { pick("Per nome") },
            FluidContextAction(label = "Per dimensione") { pick("Per dimensione") },
          )
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
          icon = Icons.Rounded.Star,
          contentDescription = "Preferiti",
          onClick = { pick("Stella (tocco)") },
          actions = { demoActions(pick) },
          modifier = Modifier.testTag("menus-bar"),
        )
        FluidBarAction(
          icon = Icons.Rounded.MoreVert,
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
          FluidContextAction(label = "Copia", icon = Icons.Rounded.Share) { pick("Copia") },
          FluidContextAction(label = "Mi piace", icon = Icons.Rounded.Favorite) { pick("Mi piace") },
        )
      })
      .testTag("menus-card"),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = "Tieni premuto qui", style = MaterialTheme.typography.titleMedium)
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
