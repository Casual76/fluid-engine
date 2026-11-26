package dev.antigravity.fluidengine.sample.novita

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.diagnostics.FluidDiagnostics
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsConfig
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsConsentPrompt
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsDataPage
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsPage
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsSettingsGroup
import dev.antigravity.fluidengine.diagnostics.FluidLog
import dev.antigravity.fluidengine.diagnostics.FluidPageFrames
import dev.antigravity.fluidengine.diagnostics.FluidTrace
import dev.antigravity.fluidengine.diagnostics.FluidTraceName
import dev.antigravity.fluidengine.diagnostics.FluidTraceNames
import dev.antigravity.fluidengine.feedback.FluidFeedbackConfig
import dev.antigravity.fluidengine.feedback.FluidFeedbackGitHub
import dev.antigravity.fluidengine.feedback.FluidFeedbackHost
import dev.antigravity.fluidengine.feedback.FluidFeedbackRow
import dev.antigravity.fluidengine.feedback.rememberFluidFeedbackState
import dev.antigravity.fluidengine.foundation.lab.FluidExperiment
import dev.antigravity.fluidengine.foundation.lab.FluidExperimentApplies
import dev.antigravity.fluidengine.foundation.lab.FluidLab
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidNumericText
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.lab.FluidLabPage
import dev.antigravity.fluidengine.ui.fluid.lab.FluidLabRow
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// F8.24 · Diagnostica e Laboratorio, nella pagina «Elementi nuovi». Le scene sono le pagine di Fluid Glass che un'app
// Pampa avrebbe: le impostazioni col Laboratorio, «Informazioni» con «Segnala un problema», la privacy con la
// diagnostica, la pagina degli scatti dal vivo, le tracce. La diagnostica si accende solo quando si apre una sua scena
// (il primo fotogramma della galleria non cambia), e manda solo al PC (`adb reverse tcp:8787 tcp:8787` e `wrangler dev`
// del Worker di engine-diagnostics), solo col sì. Nessun numero di movimento qui.

/** I tag dei test. */
internal object F8p24Tags {
  const val OpenLab = "f8p24:apri-laboratorio"
  const val Preview = "f8p24:anteprima"
  const val PreviewTitle = "f8p24:anteprima:titolo"
  const val IndexLine = "f8p24:indice"
  const val PhotoPage = "f8p24:pagina-foto"
  const val OpenPhoto = "f8p24:apri-foto"
  const val Hang = "f8p24:blocco"
  const val Crash = "f8p24:arresto"
  const val AskAgain = "f8p24:chiedi-di-nuovo"
  const val SlowToggle = "f8p24:rallenta"
  const val Frames = "f8p24:fotogrammi"
  const val Slow = "f8p24:lenti"
  const val Hitch = "f8p24:ritardo"
  const val Verdict = "f8p24:giudizio"
  const val RunTrace = "f8p24:traccia"
  const val TraceResult = "f8p24:traccia:esito"
  const val TraceAvailable = "f8p24:traccia:disponibile"
  const val LogEvent = "f8p24:registro"

  fun backdrop(b: F8p24Backdrop): String = "f8p24:fondo:${b.name}"
}

/** Il fondo dell'anteprima e della pagina da catturare. */
internal enum class F8p24Backdrop(val label: String, val photo: Int?) {
  LightPhoto("Foto chiara", R.drawable.viewer_astronaut),
  DarkPhoto("Foto scura", R.drawable.viewer_rocket),
  Page("Pagina", null),
}

/** Le funzioni in prova di Fluid Glass, per il Laboratorio della scena. */
internal object F8p24Experiments {
  val BigPreviews = FluidExperiment(
    "anteprime-grandi", "Anteprime grandi", "Le card con la foto si fanno più alte, e la foto si vede intera.",
    group = "Interfaccia",
  )
  val TintedTitles = FluidExperiment(
    "titoli-tinti", "Titoli nell'accento", "I titoli delle card prendono il colore dell'accento dell'app.",
    group = "Interfaccia",
  )
  val NewIndex = FluidExperiment(
    "indice-nuovo", "Indice di ricerca nuovo", "Un indice più veloce per la ricerca. Si decide all'avvio dell'app.",
    group = "Prestazioni", applies = FluidExperimentApplies.NextLaunch,
  )
  val All: List<FluidExperiment> = listOf(BigPreviews, TintedTitles, NewIndex)

  /** Il nome del file del Laboratorio della galleria (a parte da quello di serie, per le prove). */
  const val FileName = "fluid-lab-galleria"
}

@Volatile
private var galleryLab: FluidLab? = null

/** Il Laboratorio della galleria: uno per processo, come in un'app. */
internal fun galleryLab(context: Context): FluidLab =
  galleryLab ?: synchronized(F8p24Experiments) {
    galleryLab ?: FluidLab.create(context, F8p24Experiments.All, F8p24Experiments.FileName).also { galleryLab = it }
  }

/** Solo per le prove: dimentica il Laboratorio del processo (come un riavvio). */
internal fun resetGalleryLabForTesting() {
  galleryLab = null
}

/** L'indirizzo del Worker sul PC (`adb reverse tcp:8787 tcp:8787` e `wrangler dev --local --port 8787`). */
internal const val F8p24Endpoint = "http://127.0.0.1:8787/v1/reports"

/** La diagnostica della galleria: si accende la prima volta che si apre una scena di F8.24. */
internal fun galleryDiagnostics(context: Context): FluidDiagnostics =
  FluidDiagnostics.install(context.applicationContext as Application, FluidDiagnosticsConfig(appName = "Fluid Glass", endpoint = F8p24Endpoint))

/** «Segnala un problema» della galleria: la issue va nella repo dell'engine. */
internal val F8p24FeedbackConfig = FluidFeedbackConfig(
  appName = "Fluid Glass",
  github = FluidFeedbackGitHub(owner = "Casual76", repo = "fluid-engine"),
)

/** Le cinque scene di F8.24. */
internal val F8p24Section: NewElementSection = NewElementSection(
  pack = "F8.24",
  title = "Diagnostica e Laboratorio",
  detail = "Le funzioni in prova, «Segnala un problema», i crash e gli scatti col consenso, le tracce di debug",
  scenes = listOf(
    NewElementScene("laboratorio", "Il Laboratorio", "Le funzioni in prova da accendere nelle impostazioni, come in Safari") { LabScene(it) },
    NewElementScene("segnala-problema", "Segnala un problema", "Da «Informazioni»: lo screenshot segnato, lo ZIP per WhatsApp o la issue") { FeedbackScene(it) },
    NewElementScene("diagnostica", "Crash e lentezze", "Il consenso chiesto una volta, i rapporti sul telefono e cosa parte") { DiagnosticsScene(it) },
    NewElementScene("scatti-per-pagina", "Scatti per pagina", "I fotogrammi lenti di questa pagina dal vivo, come li conta JankStats") { FramesScene(it) },
    NewElementScene("tracce", "Tracce dei tempi", "Due passaggi segnati per Perfetto, solo nella build di debug") { TracesScene(it) },
  ),
)

/** Le voci da provare a mano (in coda a `ToTry.kt`). */
internal val F8p24TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-24-laboratorio", TryArea.AppParts, "Il Laboratorio",
    "Apri «Il Laboratorio» e poi «Laboratorio», accendi «Anteprime grandi» e guarda la card; accendi «Indice di " +
      "ricerca nuovo» e leggi «Al prossimo avvio»; chiudi la galleria dalle recenti, riaprila e guarda se le scelte ci sono.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il Laboratorio"),
  ),
  TryItem(
    "f8-24-segnala", TryArea.AppParts, "Segnala un problema",
    "Tocca la riga, scrivi due parole e «Cattura una pagina»: vai alla foto, tocca «Cattura», segna col dito e " +
      "conferma, poi «Manda un file» e scegli WhatsApp senza inviare; giudica se il foglio disturba.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Segnala un problema"),
  ),
  TryItem(
    "f8-24-github", TryArea.AppParts, "La issue su GitHub",
    "Nella stessa scena tocca «Apri una issue su GitHub» e leggi la pagina che si apre nel browser senza mandarla: " +
      "il titolo, la tabella del telefono e i ringraziamenti.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Segnala un problema"),
  ),
  TryItem(
    "f8-24-consenso", TryArea.AppParts, "Il consenso della diagnostica",
    "Apri «Crash e lentezze»: il foglio chiede una volta sola; leggi cosa parte e cosa no, scegli, poi apri «Dati di " +
      "diagnostica» e tocca un rapporto per vedere il JSON vero.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Crash e lentezze"),
  ),
  TryItem(
    "f8-24-scatti", TryArea.AppParts, "Gli scatti per pagina",
    "Apri «Scatti per pagina», accendi «Rallenta i fotogrammi» per qualche secondo e guarda i numeri e il giudizio di Apple.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Scatti per pagina"),
  ),
)

// ================================================================================================================
// I pezzi comuni
// ================================================================================================================

@Composable
private fun BackdropChips(selected: F8p24Backdrop, onSelect: (F8p24Backdrop) -> Unit) {
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    F8p24Backdrop.entries.forEach { b ->
      FluidChip(label = b.label, selected = b == selected, onClick = { onSelect(b) }, modifier = Modifier.testTag(F8p24Tags.backdrop(b)))
    }
  }
}

// ================================================================================================================
// Il Laboratorio
// ================================================================================================================

@Composable
private fun LabScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val lab = remember { galleryLab(context) }
  val feedback = rememberFluidFeedbackState(F8p24FeedbackConfig, lab = lab, diagnostics = FluidDiagnostics.get())
  val nav = rememberFluidNavController()
  FluidFeedbackHost(feedback) {
    FluidNavHost(nav, "impostazioni") {
      composable("impostazioni") { LabSettings(lab, onBack, onOpenLab = { nav.navigate("laboratorio") }) }
      composable("laboratorio") {
        FluidLabPage(lab, onBack = { nav.popBackStack() }, onReportProblem = { feedback.open() })
      }
    }
  }
}

@Composable
private fun LabSettings(lab: FluidLab, onBack: () -> Unit, onOpenLab: () -> Unit) {
  val state by lab.state.collectAsStateWithLifecycle()
  var backdrop by rememberSaveable { mutableStateOf(F8p24Backdrop.LightPhoto) }
  val big = state.isChosen(F8p24Experiments.BigPreviews.key)
  val tinted = state.isChosen(F8p24Experiments.TintedTitles.key)
  FluidScreen(title = "Impostazioni", subtitle = "F8.24 · Il Laboratorio di Fluid Glass", onBack = onBack) {
    item(key = "anteprima") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader("Anteprima", detail = "La card cambia con le funzioni accese nel Laboratorio")
        BackdropChips(backdrop) { backdrop = it }
        PreviewCard(backdrop, big, tinted)
        Text(
          text = "Indice di ricerca: " + if (lab.isEnabled(F8p24Experiments.NewIndex)) "nuovo" else "quello di sempre",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(F8p24Tags.IndexLine),
        )
      }
    }
    item(key = "riga") {
      Column {
        FluidSectionHeader("Generali")
        FluidListGroup {
          FluidListRow(title = "Aspetto", subtitle = "Tema, accento, vetro", onClick = {})
          FluidListDivider()
          FluidLabRow(lab, onClick = onOpenLab, modifier = Modifier.testTag(F8p24Tags.OpenLab))
        }
        FluidSectionFootnote("Il Laboratorio sta dove l'app tiene le sue impostazioni, come «Feature Flags» di Safari. Niente rete: la scelta resta sul telefono.")
      }
    }
  }
}

@Composable
private fun PreviewCard(backdrop: F8p24Backdrop, big: Boolean, tinted: Boolean) {
  val shape = ContinuousCornerShape(FluidRadius.Card)
  Column(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .testTag(F8p24Tags.Preview),
  ) {
    val photo = backdrop.photo
    Box(Modifier.fillMaxWidth().height(if (big) 240.dp else 120.dp)) {
      if (photo != null) {
        Image(
          painterResource(photo), contentDescription = null,
          contentScale = if (big) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
      } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
      }
    }
    Text(
      text = "Le foto di novembre",
      style = MaterialTheme.typography.titleMedium,
      color = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(16.dp).testTag(F8p24Tags.PreviewTitle),
    )
  }
}

// ================================================================================================================
// Segnala un problema
// ================================================================================================================

@Composable
private fun FeedbackScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val lab = remember { galleryLab(context) }
  val feedback = rememberFluidFeedbackState(F8p24FeedbackConfig, lab = lab, diagnostics = FluidDiagnostics.get())
  val nav = rememberFluidNavController()
  LaunchedEffect(Unit) {
    // Due eventi nel registro, come li scriverebbe un'app: il secondo ha una mail, che la segnalazione toglie.
    FluidLog.event("galleria", "Aperta la scena «Segnala un problema»")
    FluidLog.warn("posta", "Invio a marta.rossi@example.com non riuscito, riprovo fra 30 s")
  }
  FluidFeedbackHost(feedback) {
    FluidNavHost(nav, "informazioni") {
      composable("informazioni") {
        FluidScreen(title = "Informazioni", subtitle = "F8.24 · Fluid Glass 2.0.0", onBack = onBack) {
          item(key = "app") {
            Column {
              FluidSectionHeader("Fluid Glass")
              FluidListGroup {
                FluidListRow(title = "Versione", subtitle = "", meta = "2.0.0 (12)", disclosure = false)
                FluidListDivider()
                FluidListRow(title = "Fluid Engine", subtitle = "", meta = dev.antigravity.fluidengine.foundation.EngineBuild.VERSION, disclosure = false)
              }
            }
          }
          item(key = "aiuto") {
            Column {
              FluidSectionHeader("Aiuto")
              FluidListGroup {
                FluidFeedbackRow()
                FluidListDivider()
                FluidListRow(
                  title = "Una pagina con una foto",
                  subtitle = "Da catturare con «Cattura una pagina»",
                  modifier = Modifier.testTag(F8p24Tags.OpenPhoto),
                  onClick = { nav.navigate("foto") },
                )
              }
              FluidSectionFootnote("«Segnala un problema» si apre solo da qui o dal Laboratorio: non chiede niente dopo uno screenshot.")
            }
          }
        }
      }
      composable("foto") { PhotoPage(onBack = { nav.popBackStack() }) }
    }
  }
}

@Composable
private fun PhotoPage(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p24Backdrop.DarkPhoto) }
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag(F8p24Tags.PhotoPage)) {
    backdrop.photo?.let {
      Image(painterResource(it), contentDescription = "Una foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      FluidGlassButton(text = "Indietro", onClick = onBack)
      BackdropChips(backdrop) { backdrop = it }
      Text(
        "Qui c'è «il problema»: il titolo è tagliato a metà perché è troppo lungo per lo schermo del telefono",
        style = MaterialTheme.typography.headlineSmall,
        color = if (backdrop.photo != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}

// ================================================================================================================
// Crash e lentezze
// ================================================================================================================

@Composable
private fun DiagnosticsScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val diagnostics = remember { galleryDiagnostics(context) }
  val nav = rememberFluidNavController()
  var confirmCrash by remember { mutableStateOf(false) }
  FluidNavHost(nav, "privacy") {
    composable("privacy") {
      FluidDiagnosticsPage("privacy")
      FluidScreen(title = "Privacy", subtitle = "F8.24 · La diagnostica di Fluid Glass", onBack = onBack) {
        item(key = "diagnostica") {
          Column {
            FluidSectionHeader("Analisi e miglioramenti")
            FluidDiagnosticsSettingsGroup(diagnostics, onOpenData = { nav.navigate("dati") })
          }
        }
        item(key = "prove") {
          Column {
            FluidSectionHeader("Prove", detail = "Per vedere un rapporto nascere")
            FluidListGroup {
              FluidListRow(
                title = "Blocca l'app per mezzo secondo",
                subtitle = "Un blocco da 500 ms: lo conta la pagina «privacy»",
                modifier = Modifier.testTag(F8p24Tags.Hang),
                tone = FluidTone.Warning,
                disclosure = false,
                onClick = { Thread.sleep(500) },
              )
              FluidListDivider()
              FluidListRow(
                title = "Fai chiudere l'app",
                subtitle = "Un crash vero: alla prossima apertura il rapporto è in «Dati di diagnostica»",
                modifier = Modifier.testTag(F8p24Tags.Crash),
                tone = FluidTone.Danger,
                disclosure = false,
                onClick = { confirmCrash = true },
              )
              FluidListDivider()
              FluidListRow(
                title = "Chiedi di nuovo il consenso",
                subtitle = "Il foglio si riapre, come quando cambia cosa si raccoglie",
                modifier = Modifier.testTag(F8p24Tags.AskAgain),
                disclosure = false,
                onClick = { diagnostics.askAgain() },
              )
            }
            FluidSectionFootnote(
              "Col sì i rapporti partono verso il PC: `adb reverse tcp:8787 tcp:8787` e `wrangler dev` del Worker di " +
                "engine-diagnostics. Senza, restano sul telefono.",
            )
          }
        }
      }
      FluidDiagnosticsConsentPrompt(diagnostics)
    }
    composable("dati") { FluidDiagnosticsDataPage(diagnostics, onBack = { nav.popBackStack() }) }
  }
  FluidConfirmationDialog(
    visible = confirmCrash,
    onDismissRequest = { confirmCrash = false },
    title = "Far chiudere Fluid Glass adesso?",
    actions = listOf(
      FluidAlertAction("Chiudi l'app", { throw IllegalStateException("Prova di F8.24: un crash voluto") }, FluidAlertAction.Emphasis.Destructive),
    ),
  )
}

// ================================================================================================================
// Scatti per pagina
// ================================================================================================================

/** Il giudizio di Apple sul ritardo per secondo: sotto 5 bene, da 5 a 10 si vede, da 10 disturba (WWDC20 10077). */
internal fun f8p24Verdict(ratio: Double): String = when {
  ratio < 5.0 -> "Bene"
  ratio < 10.0 -> "Si vede"
  else -> "Disturba"
}

@Composable
private fun FramesScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val diagnostics = remember { galleryDiagnostics(context) }
  var slow by remember { mutableStateOf(false) }
  // Si rilegge due volte al secondo, non a ogni fotogramma.
  val frames by produceState<FluidPageFrames?>(null, diagnostics) {
    while (true) {
      value = diagnostics.pendingFrames().firstOrNull { it.page == "scatti-per-pagina" }
      delay(500)
    }
  }
  FluidDiagnosticsPage("scatti-per-pagina")
  FluidScreen(title = "Scatti per pagina", subtitle = "F8.24 · I fotogrammi di questa pagina", onBack = onBack) {
    item(key = "numeri") {
      Column {
        FluidSectionHeader("Questa pagina, oggi")
        FluidListGroup {
          FluidListRow(
            title = "Fotogrammi", subtitle = "", disclosure = false, modifier = Modifier.testTag(F8p24Tags.Frames),
            badge = { FluidNumericText(value = (frames?.frames ?: 0L).toInt(), style = MaterialTheme.typography.bodyLarge) },
          )
          FluidListDivider()
          FluidListRow(
            title = "Lenti", subtitle = "Più del doppio del tempo di un fotogramma", disclosure = false,
            modifier = Modifier.testTag(F8p24Tags.Slow),
            badge = { FluidNumericText(value = (frames?.slow ?: 0L).toInt(), style = MaterialTheme.typography.bodyLarge) },
          )
          FluidListDivider()
          FluidListRow(
            title = "Blocchi", subtitle = "Da 250 ms in su", disclosure = false,
            badge = { FluidNumericText(value = (frames?.hangs ?: 0L).toInt(), style = MaterialTheme.typography.bodyLarge) },
          )
          FluidListDivider()
          val ratio = frames?.hitchRatio ?: 0.0
          FluidListRow(
            title = "Ritardo, ms per secondo", subtitle = "Il giudizio di Apple: " + f8p24Verdict(ratio),
            disclosure = false, modifier = Modifier.testTag(F8p24Tags.Hitch),
            badge = { FluidNumericText(value = ratio.toInt(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(F8p24Tags.Verdict)) },
          )
        }
      }
    }
    item(key = "rallenta") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidListGroup {
          FluidToggleRow(
            title = "Rallenta i fotogrammi",
            subtitle = "Il pallino gira e ogni suo fotogramma perde 24 ms",
            checked = slow,
            onCheckedChange = { slow = it },
            modifier = Modifier.testTag(F8p24Tags.SlowToggle),
          )
        }
        Spinner(slow)
        FluidSectionFootnote(
          "JankStats conta i fotogrammi di ogni pagina segnata con FluidDiagnosticsPage. Quando l'app esce dallo " +
            "schermo i conti vanno nella coda, sommati per pagina e per giorno.",
        )
      }
    }
  }
}

/** Il pallino che gira: con [slow] ogni suo disegno dorme 24 ms. Con «Riduci movimento» è fermo. */
@Composable
private fun Spinner(slow: Boolean) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val turn = if (reduced) null else rememberInfiniteTransition(label = "f8p24-giro").animateFloat(
    0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "f8p24-giro",
  )
  Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .size(48.dp)
        .graphicsLayer { rotationZ = turn?.value ?: 0f }
        .drawBehind {
          if (slow) SystemClock.sleep(24)
          drawCircle(color = androidx.compose.ui.graphics.Color(0xFF0A84FF), radius = size.minDimension / 6, center = center.copy(y = size.minDimension / 6))
        },
    )
  }
}

// ================================================================================================================
// Tracce dei tempi
// ================================================================================================================

private val OpenAlbumTrace = FluidTraceName("galleria:apri-album")

@Composable
private fun TracesScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var result by remember { mutableStateOf<String?>(null) }
  FluidScreen(title = "Tracce dei tempi", subtitle = "F8.24 · AndroidX Tracing e Perfetto", onBack = onBack) {
    item(key = "build") {
      Column {
        FluidSectionHeader("Questa build")
        FluidListGroup {
          FluidListRow(
            title = if (FluidTrace.isAvailable) "Le tracce ci sono" else "Niente tracce",
            subtitle = if (FluidTrace.isAvailable) "Build di debug: ogni intervallo si vede in Perfetto" else "Release o bench: ogni chiamata è vuota",
            modifier = Modifier.testTag(F8p24Tags.TraceAvailable),
            tone = if (FluidTrace.isAvailable) FluidTone.Success else FluidTone.Neutral,
            disclosure = false,
          )
        }
      }
    }
    item(key = "prova") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidSectionHeader("Apri un album", detail = "Come MyCloud Gallery: dal tocco alla prima miniatura, attraverso il disco")
        FluidButton(
          text = "Apri l'album di prova",
          onClick = {
            scope.launch {
              val start = SystemClock.elapsedRealtime()
              var readMs = 0L
              var decodeMs = 0L
              FluidTrace.async(OpenAlbumTrace) {
                val bytes = withContext(Dispatchers.IO) {
                  val t = SystemClock.elapsedRealtime()
                  FluidTrace.section(FluidTraceNames.ReadFile) {
                    context.resources.openRawResource(R.drawable.viewer_flower).use { it.readBytes() }
                  }.also { readMs = SystemClock.elapsedRealtime() - t }
                }
                withContext(Dispatchers.Default) {
                  val t = SystemClock.elapsedRealtime()
                  FluidTrace.section(FluidTraceNames.Decode) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
                  }
                  decodeMs = SystemClock.elapsedRealtime() - t
                }
              }
              result = "Letta in $readMs ms, decodificata in $decodeMs ms; in tutto ${SystemClock.elapsedRealtime() - start} ms"
            }
          },
          modifier = Modifier.testTag(F8p24Tags.RunTrace),
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
        )
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(F8p24Tags.TraceResult)) }
        FluidSectionFootnote(
          "In Perfetto (ui.perfetto.dev) cerca «galleria:apri-album», «pampa:leggi-file» e «pampa:decodifica». La traccia " +
            "si prende con il file engine-diagnostics/tracce/fluid-glass.pbtx.",
        )
      }
    }
  }
}
