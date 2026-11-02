package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidLiveDot
import dev.antigravity.fluidengine.ui.fluid.FluidPagerDots
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonList
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonText
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonTile
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidStepProgress
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListGroup

/**
 * Le capsule flottanti e i pezzi piccoli della fase 12b, tutti insieme.
 *
 * La capsula di azioni, il campo di ricerca (chiuso in un cerchio e aperto, e con la navigazione dei
 * risultati), il pallino dal vivo, gli scheletri, il segno di selezione, i punti del pager e
 * l'intestazione a passi: i pezzi che piu' app si erano riscritte da sole. Non e' ancora nella
 * galleria: la monta chi la prepara, e qui sta come una colonna sola cosi' va ovunque.
 *
 * Il vetro ha bisogno di qualcosa di nitido dietro (una pagina piatta rifrange una pagina piatta), e
 * per questo ogni capsula sta su un palco di colori e di testo, registrato come fa una schermata vera.
 */
@Composable
fun CapsulesAndPartsDemo() {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("capsules-and-parts-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    CapsuleSection()
    SearchSection()
    LivenessSection()
    SelectionSection()
    PagerSection()
    StepSection()
  }
}

// --- Il palco -------------------------------------------------------------------------------------

/**
 * Un riquadro di colori e di testo nitido, registrato come fondale del vetro, e il vetro sopra.
 * [content] riceve il fondale: e' quello che i pezzi di vetro devono rifrangere.
 */
@Composable
private fun Stage(
  height: androidx.compose.ui.unit.Dp,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.(GlassBackdropState) -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(ContinuousCornerShape(FluidRadius.Group)),
  ) {
    StageBackdrop(Modifier.fillMaxSize().glassBackdropSource(backdrop))
    content(backdrop)
  }
}

/** Una pagina finta: due colori, righe di testo. Quello che una foto o una lista darebbero al vetro. */
@Composable
private fun StageBackdrop(modifier: Modifier = Modifier) {
  val wash = remember {
    Brush.linearGradient(
      colors = listOf(Color(0xFF3A7BD5), Color(0xFFE96443), Color(0xFFF5C451), Color(0xFF2E8B57)),
      start = Offset.Zero,
      end = Offset(900f, 700f),
    )
  }
  Box(modifier = modifier.drawBehind { drawRect(wash) }) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      repeat(12) { index ->
        Text(
          text = "Testo nitido dietro il vetro, riga ${index + 1} — ABCDEFG 0123456789",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = if (index % 2 == 0) Color.Black else Color.White,
        )
      }
    }
  }
}

// --- Capsule ---------------------------------------------------------------------------------------

@Composable
private fun CapsuleSection() {
  var selecting by remember { mutableStateOf(true) }
  var playing by remember { mutableStateOf(false) }

  FluidSectionHeader(
    title = "Capsula flottante",
    detail = "Una lastra di vetro con le azioni dentro: selezione, lettore, comandi di una foto.",
  )
  Stage(height = 156.dp) { stage ->
    FluidFloatingCapsule(
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
      backdrop = stage,
      visible = selecting,
      label = "3 selezionate",
      leading = {
        FluidCapsuleAction(
          icon = Icons.Rounded.Close,
          contentDescription = "Annulla la selezione",
          onClick = { selecting = false },
          tint = MaterialTheme.colorScheme.onSurface,
        )
      },
    ) {
      Text(
        text = "3 selezionate",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 8.dp),
      )
      FluidCapsuleAction(Icons.Rounded.Share, "Condividi", onClick = {})
      FluidCapsuleAction(Icons.Rounded.Favorite, "Preferito", onClick = {})
      FluidCapsuleAction(Icons.Rounded.Delete, "Elimina", onClick = {}, destructive = true)
    }
  }
  FluidButton(
    text = if (selecting) "Nascondi la barra" else "Mostra la barra",
    onClick = { selecting = !selecting },
    style = FluidButtonStyle.Tinted,
  )

  Stage(height = 132.dp) { stage ->
    // A tutta larghezza (fino a 560 dp), con un tasto di vetro vero nella coda: dentro la capsula
    // campiona il suo materiale, e il play e' l'unica cosa che si stacca dal resto.
    FluidFloatingCapsule(
      modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp),
      backdrop = stage,
      fillWidth = true,
      onPhoto = true,
      label = "Lettore",
      leading = {
        Box(
          modifier = Modifier
            .padding(start = 4.dp)
            .size(44.dp)
            .clip(ContinuousCornerShape(FluidRadius.Small))
            .background(Brush.linearGradient(listOf(Color(0xFFFF8A65), Color(0xFF6A5ACD)))),
        )
      },
      trailing = {
        FluidGlassIconButton(
          onClick = { playing = !playing },
          size = FluidGlassSize.Compact,
        ) {
          Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pausa" else "Riproduci",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      },
    ) {
      Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
        Text(
          text = "Lezione di storia",
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
        )
        Text(
          text = "12:40 di 52:10",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
    }
  }
}

// --- Ricerca ---------------------------------------------------------------------------------------

@Composable
private fun SearchSection() {
  var expanded by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var pageQuery by remember { mutableStateOf("storia") }
  var current by remember { mutableIntStateOf(2) }
  val total = if (pageQuery.isBlank()) 0 else 12

  FluidSectionHeader(
    title = "Campo di ricerca",
    detail = "Un cerchio che si apre in campo, e una ricerca dentro la pagina con «3/12».",
  )
  Stage(height = 120.dp) { stage ->
    Box(
      modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 12.dp),
      contentAlignment = Alignment.CenterEnd,
    ) {
      FluidSearchField(
        query = query,
        onQueryChange = { query = it },
        backdrop = stage,
        placeholder = "Cerca nelle foto",
        expanded = expanded,
        onExpandedChange = { expanded = it },
        // Qui la tastiera non sale da sola: e' una demo dentro una pagina che scorre.
        autoFocus = false,
      )
    }
  }
  Stage(height = 120.dp) { stage ->
    Box(
      modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 12.dp),
    ) {
      FluidSearchField(
        query = pageQuery,
        onQueryChange = {
          pageQuery = it
          current = 0
        },
        backdrop = stage,
        placeholder = "Cerca nella pagina",
        autoFocus = false,
        navigation = FluidSearchNavigation(
          current = current,
          total = total,
          onPrevious = { if (total > 0) current = (current - 1 + total) % total },
          onNext = { if (total > 0) current = (current + 1) % total },
        ),
      )
    }
  }
}

// --- Pallino, scheletri ----------------------------------------------------------------------------

@Composable
private fun LivenessSection() {
  var loading by remember { mutableStateOf(true) }

  FluidSectionHeader(
    title = "Dal vivo e in arrivo",
    detail = "Il pallino che respira (tutti insieme) e le sagome di cio' che sta per comparire.",
  )
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidLiveDot(contentDescription = "In diretta")
    Text("In diretta", style = MaterialTheme.typography.bodyMedium)
    FluidLiveDot(color = MaterialTheme.colorScheme.error)
    Text("In ritardo", style = MaterialTheme.typography.bodyMedium)
    FluidLiveDot(color = MaterialTheme.colorScheme.onSurfaceVariant, pulsing = false)
    Text("Da tabella", style = MaterialTheme.typography.bodyMedium)
  }
  FluidButton(
    text = if (loading) "Arrivano i dati" else "Torna alle sagome",
    onClick = { loading = !loading },
    style = FluidButtonStyle.Tinted,
  )
  if (loading) {
    FluidListGroup {
      FluidSkeletonList(rows = 3, trailing = true, label = "Caricamento delle partenze")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FluidSkeletonTile(modifier = Modifier.weight(1f))
      FluidSkeletonTile(modifier = Modifier.weight(1f), aspectRatio = 1.4f)
    }
    FluidSkeletonText(lines = 3)
  } else {
    Text(
      text = "Qui, al posto delle sagome, ci sarebbero i dati: occupavano gia' questo spazio, e non salta niente.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// --- Selezione -------------------------------------------------------------------------------------

@Composable
private fun SelectionSection() {
  val picked = remember { mutableStateOf(setOf(1)) }
  var rowSelected by remember { mutableStateOf(false) }

  FluidSectionHeader(
    title = "Segno di selezione",
    detail = "Sopra una foto (anello bianco), in una riga, e sopra una tessera colorata.",
  )
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    val colors = listOf(Color(0xFFE96443), Color(0xFF3A7BD5), Color(0xFF2E8B57), Color(0xFF8E54E9))
    colors.forEachIndexed { index, color ->
      val selected = index in picked.value
      Box(
        modifier = Modifier
          .weight(1f)
          .height(76.dp)
          .clip(ContinuousCornerShape(FluidRadius.Small))
          .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))))
          .clickable {
            picked.value = if (selected) picked.value - index else picked.value + index
          }
          .testTag("demo-photo-$index"),
      ) {
        FluidSelectionMark(
          selected = selected,
          onPhoto = true,
          modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
      }
    }
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { rowSelected = !rowSelected }
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidSelectionMark(selected = rowSelected)
    Text("Una riga in selezione multipla", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.weight(1f))
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(ContinuousCornerShape(FluidRadius.Small))
        .background(Color(0xFF1E88E5)),
      contentAlignment = Alignment.Center,
    ) {
      // Sopra una tessera colorata l'accento non si leggerebbe: il segno prende il colore passato.
      FluidSelectionMark(selected = rowSelected, size = 20.dp, tint = Color.White)
    }
  }
}

// --- Punti del pager -------------------------------------------------------------------------------

@Composable
private fun PagerSection() {
  val pager = rememberPagerState(pageCount = { 3 })
  var position by remember { mutableFloatStateOf(0f) }

  FluidSectionHeader(
    title = "Punti del pager",
    detail = "Seguono il dito, non scattano a pagina assestata; oltre sette punti la fila scorre.",
  )
  HorizontalPager(
    state = pager,
    modifier = Modifier.fillMaxWidth().height(96.dp).clip(ContinuousCornerShape(FluidRadius.Card)),
  ) { page ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.linearGradient(
            listOf(
              Color(0xFF3A7BD5).copy(alpha = 0.55f + page * 0.15f),
              Color(0xFFE96443).copy(alpha = 0.55f),
            ),
          ),
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text("Pagina ${page + 1}", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
  }
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FluidPagerDots(state = pager) }
  FluidSlider(
    value = position,
    onValueChange = { position = it },
    valueRange = 0f..19f,
    contentDescription = "Posizione fra venti pagine",
  )
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    FluidPagerDots(pageCount = 20, position = { position })
  }
}

// --- Passi -----------------------------------------------------------------------------------------

@Composable
private fun StepSection() {
  var step by remember { mutableIntStateOf(2) }

  FluidSectionHeader(
    title = "Intestazione a passi",
    detail = "«Passo 2 di 5» con la barra, o a segmenti col passo in corso che respira.",
  )
  FluidStepProgress(step = step, steps = 5)
  FluidStepProgress(
    step = step,
    steps = 5,
    segmented = true,
    pulseCurrent = true,
    detail = "Converto il file",
  )
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    FluidButton(
      text = "Indietro",
      onClick = { step = (step - 1).coerceAtLeast(1) },
      style = FluidButtonStyle.Tinted,
      modifier = Modifier.weight(1f),
    )
    FluidButton(
      text = "Avanti",
      onClick = { step = (step + 1).coerceAtMost(5) },
      modifier = Modifier.weight(1f),
    )
  }
}
