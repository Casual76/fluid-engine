package dev.antigravity.fluidengine.sample.adaptive

import android.util.Log
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import kotlinx.coroutines.delay

/**
 * Ciò che la pagina cambia **senza** rifare la scena: il fondo, la quota di scuro, il cursore, i riquadri della
 * scacchiera, e se la pagina si muove da sola. Sono stati letti in disegno (`drawBehind`) o in effetti: cambiarli ridisegna
 * il fondo e non ricompone un solo vetro, perché la prova è un vetro fermo sopra un fondo che cambia.
 */
@Stable
internal class AdaptiveLive(start: AdaptiveStart) {
  var tone by mutableStateOf(start.tone)
  var share by mutableFloatStateOf(start.share)
  var cursor by mutableFloatStateOf(start.cursor)
  var dark by mutableStateOf(start.dark)
  var cell by mutableIntStateOf(start.cell)
  var auto by mutableStateOf(start.auto)

  /** Il grande aperto alla partenza nella scena dei grandi (`adaptive.large`). */
  val largeAtStart: AdaptiveLarge = start.large

  /** La scacchiera sposta il disegno di una frazione di casella quando è 1 (solo con `auto`). */
  var phase by mutableIntStateOf(0)
}

/**
 * Dove sta ogni vetro che la pagina nomina, in pixel della finestra, e quanta parte del vetro sta sopra la fascia scura:
 * le righe `RIQUADRO` e `COPERTURA` di logcat. Una mappa semplice: si legge solo per scriverla.
 */
@Stable
internal class AdaptiveReport {
  private val boxes = linkedMapOf<String, Rect>()
  var density: Float = 1f

  /** Un nome per un riquadro: lo scrive quando si posa. */
  fun place(name: String): Modifier = Modifier.onGloballyPositioned { boxes[name] = it.boundsInWindow() }

  /** Le righe `RIQUADRO`: posizione e lato di ogni vetro nominato, in pixel e in dp. */
  fun logBoxes() {
    for ((name, r) in boxes) {
      if (name == StageName) continue
      Log.i(
        AdaptiveLogTag,
        "RIQUADRO $name x=${r.left.toInt()} y=${r.top.toInt()} w=${r.width.toInt()} h=${r.height.toInt()} px · " +
          "${"%.1f".format(r.width / density)}×${"%.1f".format(r.height / density)} dp · lato corto ${"%.1f".format(minOf(r.width, r.height) / density)} dp",
      )
    }
  }

  /**
   * Le righe `COPERTURA`: quanto di ogni vetro sta sopra la fascia scura che sale dal fondo dello stage con quota
   * [share] (0..1 dell'altezza). È il numero da confrontare con la quota a cui il vetro gira.
   */
  fun logCoverage(share: Float) {
    val stage = boxes[StageName] ?: return
    val edge = stage.top + adaptiveEdge(share, stage.height)
    val parts = boxes.entries.filter { it.key != StageName }.joinToString(" ") { (name, r) ->
      "$name=${"%.2f".format(adaptiveCoverage(r.top, r.bottom, edge))}"
    }
    Log.i(AdaptiveLogTag, "COPERTURA quota=${"%.2f".format(share)} $parts")
  }

  companion object {
    const val StageName = "scena"
  }
}

/**
 * Il palco: il fondo registrato due volte, come ciò che il vetro della scena rifrange ([stage], la sorgente di sempre) e
 * come sorgente di una sonda in più ([probeSource]) che solo la pagina legge, per scrivere in logcat le sue letture. Il
 * fondo è anche quello che `LocalGlassBackdrop` e `LocalFluidCanvasBackdrop` danno ai componenti dentro.
 *
 * La seconda registrazione costa un layer in più sul fondo: è uno strumento di misura, e la pagina non è un esempio di
 * come si monta il vetro in un'app.
 */
@Composable
internal fun AdaptiveStage(
  stage: GlassBackdropState,
  probeSource: LayerBackdrop,
  report: AdaptiveReport,
  draw: DrawScope.() -> Unit,
  content: @Composable BoxScope.() -> Unit,
) {
  report.density = LocalDensity.current.density
  Box(modifier = Modifier.fillMaxSize().then(report.place(AdaptiveReport.StageName))) {
    // F2.3b, visto sul tablet: la sorgente di misura sta in un fratello **sotto** il palco, con lo stesso fondo. Annidata
    // nel palco (`glassBackdropSource(stage).layerBackdrop(probeSource)`), quando il fondo cambiava si registrava di nuovo
    // solo lei: il palco teneva il suo layer per riferimento e non avvisava, quindi le sonde dei vetri non rileggevano e
    // nessun vetro girava, mentre le righe LETTURA (che leggono la sorgente di misura) giravano.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .layerBackdrop(probeSource)
        .drawBehind(draw),
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(stage)
        .drawBehind(draw),
    )
    CompositionLocalProvider(LocalGlassBackdrop provides stage, LocalFluidCanvasBackdrop provides stage) {
      content()
    }
  }
}

/**
 * Una sonda in più sul riquadro, con lo stesso fondo e lo stesso filtro di quella del vetro: scrive in logcat ogni
 * lettura (luminanza, deviazione, gradiente, copertura, il lato che il filtro ha deciso, se è una lettura nuova o il giro
 * del filtro alla sua scadenza, e quando). Il lato è quello che deciderebbe il vetro **a quel riquadro**: lo stato
 * interno del vetro lo dice [AppearanceLog], da dentro. Da posare dopo la taglia, perché legga l'area giusta.
 */
@Composable
internal fun Modifier.adaptiveProbe(source: LayerBackdrop, name: String): Modifier {
  val probe = remember(source, name) {
    FluidGlassProbeDiagnostics.probe(source) { r ->
      Log.i(
        AdaptiveLogTag,
        "LETTURA $name luminanza=${"%.3f".format(r.luminance)} deviazione=${"%.3f".format(r.deviation)} " +
          "gradiente=${"%.3f".format(r.gradient)} copertura=${"%.2f".format(r.coverage)} " +
          "lato=${if (r.dark) "scuro" else "chiaro"} ${if (r.fresh) "lettura" else "scadenza"} t=${r.uptimeMillis}",
      )
    }
  }
  return this.then(probe)
}

/**
 * Il lato del vetro dentro cui sta, visto da dentro: legge `LocalFluidGlassAppearance` e scrive in logcat una riga a ogni
 * cambio (cambia una volta per giro). Fuori da un vetro adattivo dice che non c'è. Non disegna niente e non scrive
 * stato: si posa nel contenuto del vetro che si vuole osservare.
 */
@Composable
internal fun AppearanceLog(name: String) {
  val appearance = LocalFluidGlassAppearance.current
  LaunchedEffect(name, appearance) {
    // Una riga per cambio: un contenuto composto in piu' copie (le icone della tab bar: la riga vera e quella nascosta
    // che la pillola guarda) la direbbe una volta per copia.
    val line = appearance?.let { "${it.isDark} ${it.darkness} ${it.backdropLuminance} ${it.isMeasured}" } ?: "-"
    val now = android.os.SystemClock.uptimeMillis()
    val last = AppearanceLogLast[name]
    if (last != null && last.first == line && now - last.second < 500L) return@LaunchedEffect
    AppearanceLogLast[name] = line to now
    if (appearance == null) {
      Log.i(AdaptiveLogTag, "ASPETTO $name nessun vetro adattivo qui (vetro di prima, annidato o sotto Android 13)")
    } else {
      Log.i(
        AdaptiveLogTag,
        "ASPETTO $name lato=${if (appearance.isDark) "scuro" else "chiaro"} scurimento=${"%.2f".format(appearance.darkness)} " +
          "luminanza=${if (appearance.backdropLuminance.isNaN()) "n/d" else "%.3f".format(appearance.backdropLuminance)} " +
          "misurato=${appearance.isMeasured}",
      )
    }
  }
  Spacer(Modifier.size(0.dp))
}

/** L'ultima riga `ASPETTO` di ogni nome, con l'istante: per non ripeterla per ogni copia dello stesso contenuto. */
private val AppearanceLogLast = HashMap<String, Pair<String, Long>>()

/**
 * I contatori della sonda a pagina viva: una riga `SONDA` quando cambiano (richieste al lettore, letture, fallimenti,
 * caselle al massimo e perché le sonde si sono svegliate), controllati due volte al secondo. A pagina ferma non ne esce
 * nessuna. Solo log, nessuno stato.
 */
@Composable
internal fun ProbeCountersLog(label: String) {
  LaunchedEffect(label) {
    var last = ""
    while (true) {
      val now = "richieste=${FluidGlassProbeDiagnostics.totalRequests} letture=${FluidGlassProbeDiagnostics.totalReadings} " +
        "fallite=${FluidGlassProbeDiagnostics.totalFailures} caselle=${FluidGlassProbeDiagnostics.peakSlots} " +
        "rifiutate=${FluidGlassProbeDiagnostics.totalSlotsRefused} risvegli=${FluidGlassProbeDiagnostics.wakes()}"
      if (now != last) {
        Log.i(AdaptiveLogTag, "SONDA $label $now")
        last = now
      }
      delay(500L)
    }
  }
}

/** Il bianco, il nero e un colore da un valore a 32 bit. */
internal fun argb(value: Long): Color = Color(value)

/** Una scacchiera di caselle quadrate di [cellPx], [light] e [dark], con il disegno spostato di [shiftPx] in orizzontale. */
internal fun DrawScope.drawChecker(cellPx: Float, light: Color, dark: Color, shiftPx: Float) {
  drawRect(light)
  if (!(cellPx >= 2f)) return
  val columns = (size.width / cellPx).toInt() + 2
  val rows = (size.height / cellPx).toInt() + 1
  for (row in 0 until rows) {
    for (column in -1 until columns) {
      if ((row + column + 100) % 2 == 0) continue
      drawRect(dark, topLeft = Offset(column * cellPx + shiftPx, row * cellPx), size = Size(cellPx, cellPx))
    }
  }
}
