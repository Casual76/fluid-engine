package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.MatrixTextScale
import dev.antigravity.fluidengine.sample.matrixDemoSettings
import dev.antigravity.fluidengine.sample.rememberMatrixFlag
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pagina «Avvisi con azione» della galleria: aggregazione generica, sostituzione e scadenza applicativa.
 *
 * @param avoidTop quanto spazio lasciare libero in cima prima dell'avviso, sotto la barra di stato: una pagina che
 *   mette i suoi tasti nella riga in alto lo passa, cosi' l'avviso non finisce sotto di loro. Di serie zero.
 */
@Composable
fun NotificationUndoDemo(avoidTop: Dp = 0.dp) {
  // Nella galleria gli interruttori partono dalla matrice degli stati (e restano liberi); fuori, da spenti.
  var dark by rememberMatrixFlag { it.theme.isDark }
  var large by rememberMatrixFlag { it.textScale == MatrixTextScale.Largest }
  var reduced by rememberMatrixFlag { it.reducedMotion }
  var status by remember { mutableStateOf("Nessuna operazione") }
  var serial by remember { mutableIntStateOf(0) }
  var protectedUntil by remember { mutableLongStateOf(0L) }
  val host = rememberFluidNotificationHostState()
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  fun publish(protect: Boolean, ordinary: Boolean = true) {
    val id = "demo-${++serial}"
    if (protect) protectedUntil = android.os.SystemClock.elapsedRealtime() + 10_000L
    val actions = buildList {
      if (protectedUntil > android.os.SystemClock.elapsedRealtime()) {
        add(FluidNotificationAction("Gestisci operazioni in attesa") {
          status = "Operazioni in attesa aperte" // L'app apre qui una destinazione durevole.
        })
      }
      if (ordinary) add(FluidNotificationAction("Ripristina ultima modifica") {
        status = "Ultima modifica ripristinata"
        // La nuova presentazione conserva la destinazione protetta dopo il click dell'altra azione.
        if (protectedUntil > android.os.SystemClock.elapsedRealtime()) publish(false, ordinary = false)
      })
    }
    val n = FluidNotification(id, "Operazione aggiornata", "Ultima modifica disponibile",
      durationMillis = 15_000, inlineActions = actions, replaceGroup = "operazioni")
    scope.launch { status = host.showForResult(n).toString() }
    scope.launch {
      val wait = protectedUntil - android.os.SystemClock.elapsedRealtime()
      if (wait > 0) {
        delay(wait)
        if (android.os.SystemClock.elapsedRealtime() >= protectedUntil) {
          host.updateCurrent(n.copy(message = "Attesa terminata", inlineActions = if (ordinary) actions.takeLast(1) else emptyList()))
        }
      }
    }
  }
  FluidTheme(matrixDemoSettings(dark)) {
    CompositionLocalProvider(
      // Spento, il testo e' quello che arriva da fuori (la matrice puo' dire 1,3): non piu' un 1 fisso.
      LocalDensity provides Density(density.density, if (large) 2f else density.fontScale),
      LocalFluidMotionPolicy provides FluidMotionPolicy(if (reduced) 0f else 1f),
    ) {
      Box(Modifier.fillMaxSize()) {
        // Un pulsante per riga in una pagina che scorre: cede il dito alla lista (con il testo al 200% la pagina e' piu' alta dello schermo).
        FluidScreen(title = "Azioni immediate") {
          item { Text(status) }
          item { FluidButton("Modifica ordinaria", onClick = { publish(false) }, grip = FluidPressGrip.YieldsToScroll) }
          item { FluidButton("Operazione protetta (10 s)", onClick = { publish(true) }, grip = FluidPressGrip.YieldsToScroll) }
          item { FluidButton("Errore", onClick = {
            scope.launch { host.show(FluidNotification("errore-${++serial}", "Operazione non riuscita",
              "Riprova quando disponibile", tone = FluidNotificationTone.Error)) }
          }, grip = FluidPressGrip.YieldsToScroll) }
          item { FluidButton("Chiaro / scuro", onClick = { dark = !dark }, grip = FluidPressGrip.YieldsToScroll) }
          item { FluidButton("Testo 100% / 200%", onClick = { large = !large }, grip = FluidPressGrip.YieldsToScroll) }
          item { FluidButton("Movimento ridotto", onClick = { reduced = !reduced }, grip = FluidPressGrip.YieldsToScroll) }
        }
        FluidNotificationHost(host, modifier = Modifier.padding(top = avoidTop))
      }
    }
  }
}
