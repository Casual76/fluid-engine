package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidDurationUnits
import dev.antigravity.fluidengine.ui.fluid.FluidDurationWheel
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidRangeSlider
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.FluidValueText
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidTimeWheel
import dev.antigravity.fluidengine.ui.fluid.FluidWheelColumn
import dev.antigravity.fluidengine.ui.fluid.FluidWheelPicker
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// F8.2 · Gesti e ruote: il selettore a ruota, la ghiera a righello e il cursore a intervallo, nei casi delle app di
// Alessio (le schede `selettore-a-ruota`, `ghiera-a-righello`, `cursore-a-intervallo`). Ogni scena ha il suo fondo
// (pagina, foto chiara, foto scura): sulle foto l'elemento sta in un pannello di vetro, come in un foglio.

/** Le scene di F8.2, nell'ordine delle idee. */
internal val F8p2Section = NewElementSection(
  pack = "F8.2",
  title = "Gesti e ruote",
  scenes = listOf(
    NewElementScene(
      "ruota-timer", "Il timer di Orologio",
      "Fluidify, Pampa Sounds · ore, minuti e secondi collegati: zero non si sceglie",
    ) { onBack -> TimerWheelScene(onBack) },
    NewElementScene(
      "ruota-ora", "L'ora di una lezione",
      "ClasseViva, F8.8 · ore e minuti, in 12 ore AM e PM girano passando da 11 a 12",
    ) { onBack -> TimeWheelScene(onBack) },
    NewElementScene(
      "ruota-data", "Una data e «ogni quanto»",
      "Pampa Widgets · giorno, mese e anno (giorni spenti oltre la fine del mese, anni lunghi), e una colonna di parole",
    ) { onBack -> DateWheelScene(onBack) },
    NewElementScene(
      "ghiera-raddrizza", "Raddrizza una foto",
      "MyCloud Gallery · ±45°, una principale ogni 10°, lo zero che attira, doppio tocco per tornare a zero",
    ) { onBack -> StraightenScene(onBack) },
    NewElementScene(
      "ghiera-valori", "Velocita' e decibel",
      "Fluidify, Pampa Sounds · da 0,5× a 2× col neutro a 1×, e ±12 dB a passi di 0,1",
    ) { onBack -> DialValuesScene(onBack) },
    NewElementScene(
      "intervallo", "Durata e peso",
      "Fluidify, MyCloud Gallery · due maniglie che non si scavalcano, la distanza minima, le tacche",
    ) { onBack -> RangeScene(onBack) },
  ),
)

/** I tag delle prove. */
internal object F8p2Tags {
  const val Readout = "f8p2:valore"
  const val Wheel = "f8p2:ruota"
  const val Dial = "f8p2:ghiera"
  const val Range = "f8p2:intervallo"
  const val Background = "f8p2:fondo"
}

/** Il fondo di una scena. */
internal enum class F8p2Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

@Composable
private fun F8p2Screen(id: String, title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  var ground by rememberSaveable { mutableStateOf(F8p2Ground.Page) }
  FluidScreen(title = title, subtitle = "F8.2 · $id", onBack = onBack) {
    item(key = "fondo") {
      FluidSegmentedControl(
        options = F8p2Ground.entries,
        selected = ground,
        onSelect = { ground = it },
        modifier = Modifier.fillMaxWidth().testTag(F8p2Tags.Background),
        label = { it.label },
      )
    }
    item(key = "scena") { F8p2Stage(ground, content) }
  }
}

/** La pagina, o una foto con sopra un pannello di vetro che porta l'elemento. */
@Composable
private fun F8p2Stage(ground: F8p2Ground, content: @Composable ColumnScope.() -> Unit) {
  if (ground == F8p2Ground.Page) {
    // Da bordo a bordo, come il timer di Orologio (F8.2b): la ruota e' larga quanto lo schermo e la sua fascia rientra di
    // 45 dp dal bordo, dove rientra in iOS 26. Ogni elemento della scena ha gia' il suo margine.
    Column(
      Modifier.fillMaxWidth().edgeToEdge().padding(top = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
    return
  }
  val photo = rememberGlassBackdrop()
  Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Group))) {
    Image(
      painter = painterResource(if (ground == F8p2Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
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

/** Esce dai margini di [FluidScreen] (`FluidScreenDefaults.HorizontalPadding` per lato) e va da bordo a bordo. */
private fun Modifier.edgeToEdge(): Modifier = layout { measurable, constraints ->
  val extra = FluidScreenDefaults.HorizontalPadding.roundToPx() * 2
  val width = constraints.maxWidth + extra
  val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
  layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * Il valore scritto sotto il controllo. F7.24: un [FluidValueText], che si legge nel disegno: mentre il dito gira la
 * ruota o il righello non si ricompone e non si rimisura, e TalkBack lo legge quando il gesto finisce.
 */
@Composable
private fun Readout(text: () -> String) {
  FluidValueText(
    text = text,
    style = FluidTextStyles.numeric,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(F8p2Tags.Readout),
  )
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

// --- Le ruote ------------------------------------------------------------------------------------------------------

@Composable
private fun TimerWheelScene(onBack: () -> Unit) {
  var seconds by rememberSaveable { mutableLongStateOf(30L * 60L) }
  var withSeconds by rememberSaveable { mutableStateOf(true) }
  F8p2Screen("ruota-timer", "Il timer di Orologio", onBack) {
    FluidSegmentedControl(
      options = listOf(true, false),
      selected = withSeconds,
      onSelect = { withSeconds = it },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      label = { if (it) "Ore, min, s" else "Ore e min" },
    )
    FluidDurationWheel(
      seconds = seconds,
      onSecondsChange = { seconds = it },
      showSeconds = withSeconds,
      modifier = Modifier.testTag(F8p2Tags.Wheel),
    )
    Readout { "Durata: ${seconds / 3600}:${(seconds % 3600 / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}" }
    Caption("Ferma la ruota su 0 ore 0 min 0 s: la colonna piu' piccola sale da sola al minimo, come in iOS.")
  }
}

@Composable
private fun TimeWheelScene(onBack: () -> Unit) {
  var minutes by rememberSaveable { mutableIntStateOf(8 * 60) }
  var use24 by rememberSaveable { mutableStateOf(true) }
  val time = LocalTime.of(minutes / 60, minutes % 60)
  F8p2Screen("ruota-ora", "L'ora di una lezione", onBack) {
    FluidSegmentedControl(
      options = listOf(true, false),
      selected = use24,
      onSelect = { use24 = it },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      label = { if (it) "24 ore" else "12 ore" },
    )
    FluidTimeWheel(
      time = time,
      onTimeChange = { minutes = it.hour * 60 + it.minute },
      use24Hour = use24,
      locale = Locale.ITALY,
      minuteInterval = 5,
      modifier = Modifier.testTag(F8p2Tags.Wheel),
    )
    Readout { "Lezione alle ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}" }
    Caption("Minuti a passi di 5 (`minuteInterval`). In 12 ore porta le ore da 11 a 12: AM e PM girano da soli.")
  }
}

private val Frequencies = listOf("Ogni 15 minuti", "Ogni 30 minuti", "Ogni ora", "Ogni 2 ore", "Ogni 6 ore", "Una volta al giorno")

@Composable
private fun DateWheelScene(onBack: () -> Unit) {
  var day by rememberSaveable { mutableIntStateOf(23) }
  var month by rememberSaveable { mutableIntStateOf(11) }
  var year by rememberSaveable { mutableIntStateOf(2026) }
  var frequency by rememberSaveable { mutableIntStateOf(2) }
  val firstYear = 1900
  val lastYear = 2100
  val length = YearMonth.of(year, month).lengthOfMonth()
  F8p2Screen("ruota-data", "Una data e «ogni quanto»", onBack) {
    FluidSectionHeader(title = "Data, come UIDatePicker")
    FluidWheelPicker(
      columns = listOf(
        FluidWheelColumn(
          count = 31,
          selected = day - 1,
          onSelectedChange = { day = it + 1 },
          label = remember { { i: Int -> (i + 1).toString() } },
          cyclic = true,
          contentDescription = "Giorno",
          weight = 0.7f,
          // I giorni oltre la fine del mese sono spenti: la ruota che ci si ferma torna all'ultimo, come in iOS.
          isEnabled = { it < length },
        ),
        FluidWheelColumn(
          count = 12,
          selected = month - 1,
          onSelectedChange = { m ->
            month = m + 1
            day = day.coerceAtMost(YearMonth.of(year, m + 1).lengthOfMonth())
          },
          label = remember {
            { i: Int -> java.time.Month.of(i + 1).getDisplayName(TextStyle.FULL, Locale.ITALY) }
          },
          cyclic = true,
          contentDescription = "Mese",
          weight = 1.4f,
        ),
        FluidWheelColumn(
          count = lastYear - firstYear + 1,
          selected = year - firstYear,
          onSelectedChange = { y ->
            year = firstYear + y
            day = day.coerceAtMost(YearMonth.of(firstYear + y, month).lengthOfMonth())
          },
          label = remember { { i: Int -> (firstYear + i).toString() } },
          contentDescription = "Anno",
          // Un minimo e un massimo, come `minimumDate` e `maximumDate`.
          isEnabled = { firstYear + it in 1950..2050 },
        ),
      ),
      modifier = Modifier.testTag(F8p2Tags.Wheel),
    )
    Readout { "$day ${java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ITALY)} $year" }
    FluidSectionHeader(title = "Ogni quanto aggiornare")
    FluidWheelPicker(
      count = Frequencies.size,
      selected = frequency,
      onSelectedChange = { frequency = it },
      label = { Frequencies[it] },
      contentDescription = "Aggiornamento",
    )
    Caption("Gli anni vanno dal 1900 al 2100, ma si scelgono solo dal 1950 al 2050: gli altri sono spenti.")
  }
}

// --- La ghiera -----------------------------------------------------------------------------------------------------

@Composable
private fun StraightenScene(onBack: () -> Unit) {
  var degrees by rememberSaveable { mutableFloatStateOf(0f) }
  F8p2Screen("ghiera-raddrizza", "Raddrizza una foto", onBack) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f).clip(ContinuousCornerShape(FluidRadius.Card))) {
      Image(
        painter = painterResource(R.drawable.viewer_flower),
        contentDescription = "La foto da raddrizzare",
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().graphicsLayer {
          rotationZ = degrees
          // Raddrizzare taglia i bordi: la foto cresce quanto serve a coprire il riquadro.
          val s = abs(sin(Math.toRadians(degrees.toDouble()))).toFloat()
          scaleX = 1f + s * 0.9f
          scaleY = 1f + s * 0.9f
        },
      )
    }
    FluidRulerDial(
      value = { degrees },
      onValueChange = { degrees = it },
      contentDescription = "Raddrizza",
      valueText = { "${fluidSceneDegrees(it)}°" },
      modifier = Modifier.padding(horizontal = 16.dp).testTag(F8p2Tags.Dial),
    )
    Readout { "Rotazione: ${fluidSceneDegrees(degrees)}°" }
    Caption("Trascina il righello o lancialo: si ferma su un grado, sulle principali ogni 10°, e lo zero attira.")
  }
}

private fun fluidSceneDegrees(value: Float): String {
  val r = value.roundToInt()
  return if (r < 0) "−${-r}" else r.toString()
}

@Composable
private fun DialValuesScene(onBack: () -> Unit) {
  var speed by rememberSaveable { mutableFloatStateOf(1f) }
  var decibel by rememberSaveable { mutableFloatStateOf(0f) }
  F8p2Screen("ghiera-valori", "Velocita' e decibel", onBack) {
    FluidSectionHeader(title = "Velocita' (Fluidify)")
    FluidRulerDial(
      value = { speed },
      onValueChange = { speed = it },
      valueRange = 0.5f..2f,
      step = 0.05f,
      majorEvery = 10,
      neutralValue = 1f,
      contentDescription = "Velocita'",
      valueText = { "${"%.2f".format(Locale.ITALY, it)}×" },
      modifier = Modifier.padding(horizontal = 16.dp).testTag(F8p2Tags.Dial),
    )
    FluidSectionHeader(title = "Calibrazione (Pampa Sounds)")
    FluidRulerDial(
      value = { decibel },
      onValueChange = { decibel = it },
      valueRange = -12f..12f,
      step = 0.1f,
      majorEvery = 10,
      neutralValue = 0f,
      contentDescription = "Calibrazione",
      valueText = { "${"%+.1f".format(Locale.ITALY, it).replace('-', '−')} dB" },
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    Readout { "${"%.2f".format(Locale.ITALY, speed)}× · ${"%+.1f".format(Locale.ITALY, decibel)} dB" }
  }
}

// --- Il cursore a intervallo ---------------------------------------------------------------------------------------

@Composable
private fun RangeScene(onBack: () -> Unit) {
  var from by rememberSaveable { mutableFloatStateOf(60f) }
  var to by rememberSaveable { mutableFloatStateOf(270f) }
  var lightest by rememberSaveable { mutableFloatStateOf(1f) }
  var heaviest by rememberSaveable { mutableFloatStateOf(25f) }
  F8p2Screen("intervallo", "Durata e peso", onBack) {
    FluidSectionHeader(title = "Durata dei brani (Fluidify)")
    FluidRangeSlider(
      value = from..to,
      onValueChange = { from = it.start; to = it.endInclusive },
      valueRange = 0f..600f,
      minDistance = 30f,
      startContentDescription = "Durata minima",
      endContentDescription = "Durata massima",
      startStateDescription = clock(from),
      endStateDescription = clock(to),
      modifier = Modifier.padding(horizontal = 16.dp).testTag(F8p2Tags.Range),
    )
    Readout { "Da ${clock(from)} a ${clock(to)}" }
    FluidSectionHeader(title = "Peso delle foto (MyCloud Gallery)")
    FluidRangeSlider(
      value = lightest..heaviest,
      onValueChange = { lightest = it.start; heaviest = it.endInclusive },
      valueRange = 0f..50f,
      snapValues = listOf(0f, 1f, 5f, 10f, 25f, 50f),
      snapThreshold = 1f,
      startContentDescription = "Peso minimo",
      endContentDescription = "Peso massimo",
      startStateDescription = "${lightest.roundToInt()} MB",
      endStateDescription = "${heaviest.roundToInt()} MB",
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    Readout { "Da ${lightest.roundToInt()} MB a ${heaviest.roundToInt()} MB" }
    FluidSectionFootnote(text = "Le maniglie non si scavalcano: quella che trascini si ferma sull'altra, con un colpo.")
  }
}

private fun clock(seconds: Float): String {
  val s = seconds.roundToInt()
  return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

