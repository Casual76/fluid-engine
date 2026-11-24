package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidCelebration
import dev.antigravity.fluidengine.ui.fluid.FluidCelebrationState
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSymbol
import dev.antigravity.fluidengine.ui.fluid.FluidSymbolEffect
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.CheckCircle
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.MicOff
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.NotificationsActive
import dev.antigravity.fluidengine.ui.fluid.icons.NotificationsOff
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.Person
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.School
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import dev.antigravity.fluidengine.ui.fluid.icons.VolumeUp
import dev.antigravity.fluidengine.ui.fluid.icons.Wifi
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCelebrationState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.delay

// F8.5 · Festa e simboli: i coriandoli di un successo (`coriandoli`) e gli effetti dei simboli (`effetti-dei-simboli`), nei
// casi delle app di Alessio. Ogni scena ha il suo fondo (pagina, foto chiara, foto scura); sulle foto gli elementi stanno in
// un pannello di vetro. «Movimento ridotto (prova)» simula l'impostazione di sistema dentro la scena, per le riprese.

/** Le scene di F8.5, nell'ordine delle idee. */
internal val F8p5Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "festa-voto", "Un bel voto",
    "ClasseViva · la pioggia di coriandoli di iMessage quando arriva un 10, col doppio colpetto",
  ) { onBack -> GradeCelebrationScene(onBack) },
  NewElementScene(
    "festa-installa", "Installazione riuscita",
    "Pampa Store · i coriandoli scoppiano dal tasto quando l'app e' installata",
  ) { onBack -> InstallCelebrationScene(onBack) },
  NewElementScene(
    "simboli-tavola", "Gli effetti, uno per uno",
    "SF Symbols · rimbalzo, pulsazione, dondolio, rotazione, respiro, colore variabile, sostituzione, Draw On, Magic Replace",
  ) { onBack -> SymbolTableScene(onBack) },
  NewElementScene(
    "simboli-app", "Gli effetti nelle app",
    "La scheda che rimbalza, la campanella, la sincronizzazione che gira, il microfono, il Wi-Fi, play e pausa",
  ) { onBack -> SymbolAppsScene(onBack) },
)

/** I tag delle prove. */
internal object F8p5Tags {
  const val Celebrate = "f8p5:festeggia"
  const val Install = "f8p5:installa"
  const val Status = "f8p5:stato"
  const val Reduced = "f8p5:ridotto"
  const val Repeat = "f8p5:ripeti"
  const val Auto = "f8p5:automatico"
  const val Background = "f8p5:fondo"

  fun cell(id: String): String = "f8p5:simbolo:$id"
}

/** Il fondo di una scena. */
internal enum class F8p5Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

@Composable
private fun F8p5Screen(
  id: String,
  title: String,
  onBack: () -> Unit,
  celebration: FluidCelebrationState? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  var ground by rememberSaveable { mutableStateOf(F8p5Ground.Page) }
  var reduced by rememberSaveable { mutableStateOf(false) }
  val policy = if (reduced) FluidMotionPolicy(durationScale = 0f) else LocalFluidMotionPolicy.current
  CompositionLocalProvider(LocalFluidMotionPolicy provides policy) {
    Box(Modifier.fillMaxSize()) {
      FluidScreen(title = title, subtitle = "F8.5 · $id", onBack = onBack) {
        item(key = "fondo") {
          FluidSegmentedControl(
            options = F8p5Ground.entries,
            selected = ground,
            onSelect = { ground = it },
            modifier = Modifier.fillMaxWidth().testTag(F8p5Tags.Background),
            label = { it.label },
          )
        }
        item(key = "scena") { F8p5Stage(ground, content) }
        item(key = "ridotto") {
          FluidListGroup(Modifier.padding(top = 12.dp)) {
            FluidToggleRow(
              title = "Movimento ridotto (prova)",
              subtitle = "Come «Riduci movimento» del sistema, solo in questa scena",
              checked = reduced,
              onCheckedChange = { reduced = it },
              modifier = Modifier.testTag(F8p5Tags.Reduced),
            )
          }
        }
      }
      // La festa sta sopra tutto, ultima nel Box: uno strato solo, che non prende i tocchi.
      if (celebration != null) FluidCelebration(celebration)
    }
  }
}

/** La pagina, o una foto con sopra un pannello di vetro che porta gli elementi. */
@Composable
private fun F8p5Stage(ground: F8p5Ground, content: @Composable ColumnScope.() -> Unit) {
  if (ground == F8p5Ground.Page) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    return
  }
  val photo = rememberGlassBackdrop()
  Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Group))) {
    Image(
      painter = painterResource(if (ground == F8p5Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.matchParentSize().glassBackdropSource(photo),
    )
    Column(
      Modifier
        .padding(horizontal = 12.dp, vertical = 28.dp)
        .fillMaxWidth()
        .fluidGlassPanel(photo, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Group), GlassRole.Modal)
        .padding(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
private fun Caption(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  )
}

@Composable
private fun Status(state: FluidCelebrationState) {
  Text(
    text = if (state.isCelebrating) "Festa in corso" else "Ferma: nessun disegno",
    style = FluidTextStyles.numeric,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(F8p5Tags.Status),
  )
}

/** «Ripeti ogni 5 s»: per misurare il costo della festa sul tablet, una festa dopo l'altra senza toccare. */
@Composable
private fun RepeatRow(repeat: Boolean, onChange: (Boolean) -> Unit) {
  FluidListGroup {
    FluidToggleRow(
      title = "Ripeti ogni 5 s",
      subtitle = "Per misurare i fotogrammi della festa",
      checked = repeat,
      onCheckedChange = onChange,
      modifier = Modifier.testTag(F8p5Tags.Repeat),
    )
  }
}

// --- La festa --------------------------------------------------------------------------------------------------

private val Grades = listOf("7½ · Storia", "8 · Inglese", "6½ · Fisica")

@Composable
private fun GradeCelebrationScene(onBack: () -> Unit) {
  val celebration = rememberFluidCelebrationState()
  val grades = remember { mutableStateListOf<String>().apply { addAll(Grades) } }
  var repeat by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(repeat) {
    while (repeat) {
      celebration.launch()
      delay(5_000)
    }
  }
  F8p5Screen("festa-voto", "Un bel voto", onBack, celebration) {
    FluidSectionHeader(title = "Ultimi voti")
    FluidListGroup {
      grades.forEachIndexed { index, grade ->
        if (index > 0) FluidListDivider()
        FluidListRow(title = grade, subtitle = if (index == 0 && grades.size > Grades.size) "Nuovo" else "")
      }
    }
    FluidButton(
      text = "Arriva un 10 in Matematica",
      onClick = {
        grades.add(0, "10 · Matematica")
        celebration.launch()
      },
      modifier = Modifier.padding(horizontal = 16.dp).testTag(F8p5Tags.Celebrate),
      fillWidth = true,
    )
    Status(celebration)
    RepeatRow(repeat) { repeat = it }
    Caption(
      "La pioggia di iMessage: i coriandoli entrano dall'alto, girano su se stessi e scendono adagio. Con il movimento " +
        "ridotto restano posati e fermi per un attimo, e si sente solo il doppio colpetto.",
    )
  }
}

@Composable
private fun InstallCelebrationScene(onBack: () -> Unit) {
  val celebration = rememberFluidCelebrationState()
  var phase by rememberSaveable { mutableIntStateOf(0) }
  var center by remember { mutableStateOf(Offset.Zero) }
  var repeat by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(phase) {
    if (phase == 1) {
      delay(1_200)
      phase = 2
      celebration.launch(center)
    }
  }
  LaunchedEffect(repeat) {
    while (repeat) {
      celebration.launch(center)
      delay(5_000)
    }
  }
  F8p5Screen("festa-installa", "Installazione riuscita", onBack, celebration) {
    FluidSectionHeader(title = "Pampa Store")
    FluidListGroup {
      FluidListRow(title = "Fluid Weather", subtitle = "Il meteo col vetro liquido · 12 MB")
    }
    FluidButton(
      text = when (phase) {
        0 -> "Installa"
        1 -> "Installazione…"
        else -> "Apri"
      },
      onClick = { if (phase == 0) phase = 1 else if (phase == 2) phase = 0 },
      loading = phase == 1,
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .testTag(F8p5Tags.Install)
        .onGloballyPositioned { center = it.boundsInRoot().center },
      fillWidth = true,
    )
    Status(celebration)
    RepeatRow(repeat) { repeat = it }
    Caption("Quando l'installazione riesce i coriandoli partono dal tasto, salgono a ventaglio e ricadono. «Apri» rimette il tasto a «Installa».")
  }
}

// --- Gli effetti dei simboli -----------------------------------------------------------------------------------

/** Una casella della tavola: il nome, l'icona, l'effetto. */
private class SymbolCell(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val effect: FluidSymbolEffect?,
  val alternate: ImageVector? = null,
  val draw: Boolean = false,
)

private val Cells = listOf(
  SymbolCell("rimbalzo", "Rimbalzo", FluidIcons.Star, FluidSymbolEffect.Bounce),
  SymbolCell("pulsazione", "Pulsazione", FluidIcons.Mic, FluidSymbolEffect.Pulse),
  SymbolCell("dondolio", "Dondolio", FluidIcons.Notifications, FluidSymbolEffect.wiggleRotation(pivotY = 0.12f)),
  SymbolCell("rotazione", "Rotazione", FluidIcons.Sync, FluidSymbolEffect.Rotate),
  SymbolCell("respiro", "Respiro", FluidIcons.Favorite, FluidSymbolEffect.Breathe),
  SymbolCell("variabile", "Colore variabile", FluidIcons.Wifi, FluidSymbolEffect.VariableColor),
  SymbolCell("riempire", "A riempire", FluidIcons.VolumeUp, FluidSymbolEffect.VariableColorCumulative),
  SymbolCell("sostituzione", "Sostituzione", FluidIcons.PlayArrow, null, alternate = FluidIcons.Pause),
  SymbolCell("disegno", "Draw On e Off", FluidIcons.Check, null, draw = true),
  // F8.5b · la Magic Replace di SF Symbols 6: fra icone parenti cambiano solo i pezzi diversi.
  SymbolCell("magia", "Magic Replace", FluidIcons.Notifications, null, alternate = FluidIcons.NotificationsOff),
  SymbolCell("suona", "Campana che suona", FluidIcons.Notifications, null, alternate = FluidIcons.NotificationsActive),
  SymbolCell("muto", "Microfono muto", FluidIcons.Mic, null, alternate = FluidIcons.MicOff),
)

@Composable
private fun SymbolTableScene(onBack: () -> Unit) {
  var auto by rememberSaveable { mutableStateOf(true) }
  F8p5Screen("simboli-tavola", "Gli effetti, uno per uno", onBack) {
    FluidListGroup {
      FluidToggleRow(
        title = "Automatico",
        subtitle = "Ogni effetto suona da solo, uno dopo l'altro; tocca una casella per suonarlo",
        checked = auto,
        onCheckedChange = { auto = it },
        modifier = Modifier.testTag(F8p5Tags.Auto),
      )
    }
    Cells.chunked(3).forEach { row ->
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        row.forEach { cell -> SymbolTableCell(cell, auto, Cells.indexOf(cell)) }
      }
    }
    Caption(
      "Tempi e ampiezze di SF Symbols, misurati sulle sessioni WWDC23 10197, WWDC24 10188 e WWDC25 337. Draw On e Off " +
        "sono fedeli dove l'icona e' fatta di tratti (la spunta, il Wi-Fi, le onde).",
    )
  }
}

@Composable
private fun SymbolTableCell(cell: SymbolCell, auto: Boolean, index: Int) {
  var count by remember { mutableIntStateOf(0) }
  var alternate by remember { mutableStateOf(false) }
  var drawn by remember { mutableStateOf(true) }
  fun fire() {
    count++
    if (cell.alternate != null) alternate = !alternate
    if (cell.draw) drawn = !drawn
  }
  LaunchedEffect(auto) {
    if (!auto) return@LaunchedEffect
    // Uno dopo l'altro: ogni casella al suo turno, un giro ogni 12 × 1,4 s.
    delay(400L + index * 1_400L)
    while (true) {
      fire()
      if (cell.draw) { delay(1_200); fire() }
      delay(Cells.size * 1_400L - if (cell.draw) 1_200L else 0L)
    }
  }
  Column(
    Modifier
      .width(104.dp)
      .padding(vertical = 8.dp)
      .testTag(F8p5Tags.cell(cell.id)),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    SymbolTap(onClick = { fire() }, size = 64) {
      FluidSymbol(
        icon = if (alternate && cell.alternate != null) cell.alternate else cell.icon,
        contentDescription = cell.label,
        size = 44.dp,
        effect = cell.effect,
        trigger = count,
        drawn = drawn,
        tint = MaterialTheme.colorScheme.primary,
      )
    }
    Text(
      text = cell.label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
  }
}

private val Tabs = listOf("Home" to FluidIcons.Home, "Voti" to FluidIcons.School, "Cerca" to FluidIcons.Search, "Profilo" to FluidIcons.Person)

@Composable
private fun SymbolAppsScene(onBack: () -> Unit) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var taps by remember { mutableIntStateOf(0) }
  var bell by remember { mutableIntStateOf(0) }
  var syncing by rememberSaveable { mutableStateOf(false) }
  var listening by rememberSaveable { mutableStateOf(false) }
  var searching by rememberSaveable { mutableStateOf(false) }
  var playing by rememberSaveable { mutableStateOf(false) }
  var muted by rememberSaveable { mutableStateOf(false) }
  var silenced by rememberSaveable { mutableStateOf(false) }
  var saved by rememberSaveable { mutableStateOf(true) }
  LaunchedEffect(syncing) {
    if (syncing) {
      delay(4_000)
      syncing = false
    }
  }
  F8p5Screen("simboli-app", "Gli effetti nelle app", onBack) {
    FluidSectionHeader(title = "La scheda scelta rimbalza")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
      Tabs.forEachIndexed { index, (label, icon) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          SymbolTap(onClick = { tab = index; taps++ }, size = 48) {
            FluidSymbol(
              icon = icon,
              contentDescription = label,
              size = 28.dp,
              effect = FluidSymbolEffect.Bounce,
              trigger = if (tab == index) taps else -1,
              tint = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    FluidSectionHeader(title = "Stati")
    FluidListGroup {
      AppRow("Arriva una notifica", "Mail · la campanella dondola, appesa al manico", "Prova", onClick = { bell++ }) {
        FluidSymbol(FluidIcons.Notifications, null, effect = FluidSymbolEffect.wiggleRotation(pivotY = 0.12f), trigger = bell)
      }
      FluidListDivider()
      AppRow("Sincronizza", "MyCloud Gallery · gira finche' sincronizza, poi finisce il giro", if (syncing) "In corso" else "Avvia",
        onClick = { syncing = true }) {
        FluidSymbol(FluidIcons.Sync, null, effect = FluidSymbolEffect.Rotate, active = syncing)
      }
      FluidListDivider()
      AppRow("Ascolta", "PampAI · il microfono respira mentre ascolta", if (listening) "Ferma" else "Parla",
        onClick = { listening = !listening }) {
        FluidSymbol(FluidIcons.Mic, null, effect = FluidSymbolEffect.Breathe, active = listening)
      }
      FluidListDivider()
      AppRow("Cerca reti", "Le tacche del Wi-Fi in fila, finche' cerca", if (searching) "Ferma" else "Cerca",
        onClick = { searching = !searching }) {
        FluidSymbol(FluidIcons.Wifi, null, effect = FluidSymbolEffect.VariableColor, active = searching)
      }
      FluidListDivider()
      AppRow("Riproduci", "Fluidify · play diventa pausa, sfocata", if (playing) "Pausa" else "Play",
        onClick = { playing = !playing }) {
        FluidSymbol(if (playing) FluidIcons.Pause else FluidIcons.PlayArrow, if (playing) "Pausa" else "Riproduci")
      }
      FluidListDivider()
      AppRow("Silenzia", "Mail · la Magic Replace: la barra si disegna sulla campana e la taglia", if (silenced) "Riattiva" else "Silenzia",
        onClick = { silenced = !silenced }) {
        FluidSymbol(if (silenced) FluidIcons.NotificationsOff else FluidIcons.Notifications,
          if (silenced) "Notifiche silenziate" else "Notifiche attive")
      }
      FluidListDivider()
      AppRow("Microfono", "PampAI · la Magic Replace del microfono muto", if (muted) "Riattiva" else "Muto",
        onClick = { muted = !muted }) {
        FluidSymbol(if (muted) FluidIcons.MicOff else FluidIcons.Mic, if (muted) "Microfono spento" else "Microfono acceso")
      }
      FluidListDivider()
      AppRow("Salvato", "Pampa Notes · la spunta si disegna (Draw On) e si cancella (Draw Off)",
        if (saved) "Cancella" else "Salva", onClick = { saved = !saved }) {
        FluidSymbol(FluidIcons.CheckCircle, null, drawn = saved)
      }
    }
    Caption("Ogni icona ha un solo orologio, che si ferma quando l'effetto torna a riposo: ferma, non ridisegna.")
  }
}

/** Un'icona che si tocca: un tasto tondo che cresce, da 44 dp in su. */
@Composable
private fun SymbolTap(onClick: () -> Unit, size: Int, content: @Composable () -> Unit) {
  Box(
    Modifier
      .size(size.dp)
      .fluidPressable(onClick = onClick, role = Role.Button, haptic = null, feedback = FluidPressFeedback.ExpandRound),
    contentAlignment = Alignment.Center,
  ) { content() }
}

@Composable
private fun AppRow(title: String, subtitle: String, action: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
  FluidListRow(
    title = title,
    subtitle = subtitle,
    subtitleMaxLines = 2,
    leading = { Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() } },
    meta = action,
    onClick = onClick,
  )
}

/** Le voci di «Da provare» di F8.5. */
internal val F8p5TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-5-festa", TryArea.ContentData, "La festa di un bel voto",
    "Apri «Un bel voto» e tocca «Arriva un 10»: giudica se i coriandoli somigliano a quelli di iMessage, se il doppio " +
      "colpetto arriva con loro e se lo scorrimento della pagina resta liscio mentre cadono.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Un bel voto"),
  ),
  TryItem(
    "f8-5-installa", TryArea.ContentData, "L'installazione riuscita",
    "Apri «Installazione riuscita» e tocca «Installa»: giudica se lo scoppio parte dal tasto e se non copre troppo.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Installazione riuscita"),
  ),
  TryItem(
    "f8-5-simboli", TryArea.ContentData, "Gli effetti dei simboli",
    "Apri «Gli effetti, uno per uno» e guarda il giro automatico: giudica se rimbalzo, dondolio, respiro e il Wi-Fi " +
      "hanno il ritmo di iOS e se Draw On sembra disegnato a mano.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Gli effetti, uno per uno"),
  ),
  TryItem(
    "f8-5-app", TryArea.ContentData, "Gli effetti nelle app",
    "Apri «Gli effetti nelle app»: cambia scheda, fai suonare la campanella, avvia la sincronizzazione e play: giudica " +
      "se gli effetti aiutano o stancano.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Gli effetti nelle app"),
  ),
)
