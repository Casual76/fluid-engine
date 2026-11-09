package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.MatrixLanguage
import dev.antigravity.fluidengine.sample.MatrixTextScale
import dev.antigravity.fluidengine.sample.matrixDemoSettings
import dev.antigravity.fluidengine.sample.rememberMatrixFlag
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Standalone, unregistered demo: locale, theme, large type, reduced motion and future bounds. */
@Composable
fun DateTimeDemo() {
  // Nella galleria gli interruttori partono dalla matrice degli stati (e restano liberi); fuori, da spenti.
  var english by rememberMatrixFlag { it.language == MatrixLanguage.English }
  var dark by rememberMatrixFlag { it.theme.isDark }
  var large by rememberMatrixFlag { it.textScale == MatrixTextScale.Largest }
  var reduced by rememberMatrixFlag { it.reducedMotion }
  var bounded by remember { mutableStateOf(false) }
  var empty by remember { mutableStateOf(false) }
  var open by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<FluidDateTimeSelection?>(null) }
  val zone = ZoneId.systemDefault()
  val locale = if (english) Locale.US else Locale.ITALY
  val labels = if (english) FluidLabels.english() else FluidLabels()
  val options = remember(open, english, zone) {
    val now = ZonedDateTime.now(zone)
    val names = if (english) listOf("Later today", "This evening", "Tomorrow morning", "Weekend", "Next week")
      else listOf("Più tardi oggi", "Stasera", "Domani mattina", "Weekend", "Settimana prossima")
    val times = listOf(now.plusHours(3).withMinute(0), now.withHour(18).withMinute(0),
      now.plusDays(1).withHour(8).withMinute(0),
      now.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).withHour(8).withMinute(0),
      now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(8).withMinute(0))
    names.zip(times).map { (name, time) -> FluidQuickTimeOption(name, time.withSecond(0).withNano(0).toInstant()) }
  }
  val min = remember(open, bounded) { if (bounded) Instant.now().plusSeconds(86400) else null }
  FluidTheme(matrixDemoSettings(dark), systemBars = false) {
    val density = LocalDensity.current
    val host = rememberFluidGlassModalHostState()
    val backdrop = rememberGlassBackdrop()
    CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else density.fontScale),
      LocalFluidLabels provides labels, LocalFluidMotionPolicy provides FluidMotionPolicy(if (reduced) 0f else 1f),
      LocalFluidGlassModalHostState provides host) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(MaterialTheme.colorScheme.background).fluidGlassModalObscured().verticalScroll(rememberScrollState())) {
          FluidButton("Italiano / English", { english = !english }, grip = FluidPressGrip.YieldsToScroll)
          FluidButton("Light / Dark", { dark = !dark }, grip = FluidPressGrip.YieldsToScroll)
          FluidButton("100% / 200%", { large = !large }, grip = FluidPressGrip.YieldsToScroll)
          FluidButton("Motion / Reduced", { reduced = !reduced }, grip = FluidPressGrip.YieldsToScroll)
          FluidButton("Min +24h", { bounded = !bounded }, grip = FluidPressGrip.YieldsToScroll)
          FluidButton("Quick / Custom only", { empty = !empty }, grip = FluidPressGrip.YieldsToScroll)
          FluidButton(labels.dateTime.title, { open = true }, grip = FluidPressGrip.YieldsToScroll)
          result?.let { Text("${it.instant} / ${it.zone}") }
        }
        FluidDateTimeSheet(open, { open = false }, { result = it; open = false },
          quickOptions = if (empty) emptyList() else options, minimumInstant = min, zone = zone, locale = locale)
        FluidGlassModalHost(state = host, backdrop = backdrop)
      }
    }
  }
}
