package dev.antigravity.fluidengine.sample.novita

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.bridge.ReadyState
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistCall
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistClient
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistClientConfig
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsent
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistError
import dev.antigravity.fluidengine.ai.bridge.assist.InMemoryAiAssistHistory
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistHistoryEntry
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistTexts
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistEntry
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistException
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistExecutor
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistHost
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistHostProvider
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistPage
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsentState
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistPolicy
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistProgress
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistReceipt
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistRequest
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistSink
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistStatus
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistTask
import dev.antigravity.fluidengine.ai.bridge.assist.InMemoryAiAssistConsentStore
import dev.antigravity.fluidengine.ai.bridge.assist.SharedPreferencesAiAssistConsentStore
import dev.antigravity.fluidengine.aiui.chat.FluidComposer
import dev.antigravity.fluidengine.aiui.chat.FluidComposerPagePill
import dev.antigravity.fluidengine.aiui.chat.fluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.rememberFluidComposerState
import dev.antigravity.fluidengine.aiui.overlay.FluidChipPill
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidRevealingText
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.CheckCircle
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.School
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// F9.1 · Il contratto inverso: un'app chiede a PampAI e riceve la risposta. La galleria fa la parte dell'app (una pagina
// di ClasseViva finta) e, nello stesso APK, la parte di PampAI: un ospite dell'assistenza finto ([F9p1FakePampaiHost])
// con un modello finto ([F9p1FakeModel], frasi preparate, nessuna rete, nessuna chiave). L'ospite sta in un processo a
// parte (`:pampai`, nel manifest di debug e della bench): la domanda attraversa un vero Binder fra due processi, come
// fra ClasseViva e PampAI, e la scena «Le misure» lo misura. Il log `F9p1` scrive le misure per la sessione sul tablet.

internal const val F9p1LogTag = "F9p1"

/** Le scene di F9.1. */
internal val F9p1Section = NewElementSection(
  pack = "F9.1",
  title = "PampAI per le app",
  scenes = listOf(
    NewElementScene(
      "pampai-chiedi", "Chiedi da una pagina",
      "ClasseViva finta · la domanda a PampAI con «Questa pagina», il sì la prima volta, la risposta a pezzi e la ricevuta",
    ) { onBack -> F9p1AskScene(onBack) },
    NewElementScene(
      "pampai-assente", "Senza PampAI",
      "La stessa pagina quando PampAI non c'è: il tasto si vede spento, con «Installa PampAI»",
    ) { onBack -> F9p1AbsentScene(onBack) },
    NewElementScene(
      "pampai-errori", "Gli errori",
      "PampAI che non c'è, l'app non autorizzata, il sì che manca, il limite, l'annullamento, il tempo scaduto, PampAI che si ferma",
    ) { onBack -> F9p1ErrorsScene(onBack) },
    NewElementScene(
      "pampai-misure", "Le misure del ponte",
      "Un giro di call fra due processi, i pezzi col canale di ritorno e con assist_poll, la memoria",
    ) { onBack -> F9p1MeasureScene(onBack) },
  ),
  detail = "Il contratto inverso: un'app chiede a PampAI e riceve la risposta, con un PampAI finto e un modello finto",
)

/** Le voci da provare a mano. */
internal val F9p1TryItems: List<TryItem> = listOf(
  TryItem(
    "f9-1-chiedi", TryArea.Pages, "Chiedi da una pagina",
    "Nella pagina dei voti tocca «Perché questo voto abbassa la media?» col chip «Questa pagina» acceso: la prima volta " +
      "PampAI finto chiede il permesso, poi la risposta arriva a pezzi con la riga «Controllo i voti» e l'icona della scuola; " +
      "tocca il chip per vedere cosa passa, la X per toglierlo, e chiedi di nuovo senza pagina.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Chiedi da una pagina"),
  ),
  TryItem(
    "f9-1b-primo-uso", TryArea.Pages, "La prima volta con PampAI",
    "Da «Apri PampAI finto» tocca «Dimentica le scelte», poi chiedi dalla pagina dei voti col chip acceso: PampAI finto " +
      "chiede in un foglio solo se la galleria può usarla e mandare la pagina, con «Consenti», «Senza la pagina» e «Non " +
      "consentire»; prova anche il no, che si ricorda, e rimettilo da «App che usano PampAI».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Chiedi da una pagina"),
  ),
  TryItem(
    "f9-1b-assente", TryArea.Pages, "Senza PampAI",
    "Nella scena «Senza PampAI» il tasto «Chiedi a PampAI» si vede spento con «PampAI non c'è»; tocca «Installa PampAI»: " +
      "senza Pampa Store compare la spiegazione breve.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Senza PampAI"),
  ),
  TryItem(
    "f9-1b-dalle-app", TryArea.Pages, "La chat «Dalle app»",
    "Dopo qualche domanda apri PampAI finto: in «Dalle app» ci sono l'app, la domanda (per «Riassumi la pagina» il testo " +
      "intero), il titolo della pagina e la risposta, ma non le righe della pagina; togli una domanda con «Togli» e le " +
      "altre restano.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Chiedi da una pagina"),
  ),
  TryItem(
    "f9-1-errori", TryArea.Pages, "Gli errori di PampAI",
    "Prova le righe una per una: «PampAI non c'è», «Non autorizzata» (riscrivere non è concesso alla galleria), «Serve il sì» " +
      "(nega la pagina da «Apri PampAI finto»), «Limite raggiunto», «Annullata», «Tempo scaduto» e «PampAI si è fermata», " +
      "e controlla che ogni messaggio sia chiaro e senza nomi di modelli o chiavi.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Gli errori"),
  ),
  TryItem(
    "f9-1-misure", TryArea.Pages, "Le misure del ponte",
    "Tocca «Misura» e aspetta la fine, circa un minuto e mezzo perché le domande stanno nel limite di PampAI: un giro di " +
      "call fra i due processi sotto il millisecondo, i pezzi col canale più svelti di quelli con assist_poll, e la memoria " +
      "del PampAI finto che non cresce dopo quarantotto domande.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Le misure del ponte"),
  ),
)

/** Vero mentre il testo della risposta arriva sfocato (falso con «Riduci movimento»): per le prove. */
internal val F9p1Arriving = SemanticsPropertyKey<Boolean>("F9p1Arriving")

/** I tag delle prove. */
internal object F9p1Tags {
  const val Answer = "f9p1:risposta"
  const val AnswerText = "f9p1:risposta-testo"
  const val Status = "f9p1:stato"
  const val Error = "f9p1:errore"
  const val Receipt = "f9p1:ricevuta"
  const val PagePill = "f9p1:questa-pagina"
  const val PageSheet = "f9p1:cosa-passa"
  const val Composer = "f9p1:composer"
  const val OpenPampai = "f9p1:apri-pampai"
  const val Measure = "f9p1:misura"
  const val MeasureResult = "f9p1:misura-esito"
  const val ConsentSheet = "f9p1:foglio-del-si"
  const val History = "f9p1:dalle-app"
  const val InstallSheet = "f9p1:installa"
  const val DisabledEntry = "f9p1:tasto-spento"
  const val EntryAction = "f9p1:tasto-spento-azione"
  const val Retry = "f9p1:riprova"
  const val PampaiSection = "f9p1:sezione-pampai"
  const val BottomSpace = "f9p1:spazio-in-fondo"

  fun suggestion(index: Int) = "f9p1:suggerimento-$index"

  fun errorRow(id: String) = "f9p1:errore-$id"

  fun historyRemove(index: Int) = "f9p1:dalle-app-togli-$index"
}

// ── Il PampAI finto ───────────────────────────────────────────────────────────────────────────────────────────

/**
 * Il PampAI finto della galleria: l'ospite dell'assistenza del processo `:pampai`. La tabella ha solo la galleria, con
 * gli ambiti che avrebbe ClasseViva **tranne riscrivere** (cosi' la scena degli errori mostra un compito non concesso).
 * Il registro del consenso e' nelle `SharedPreferences` del processo dell'ospite.
 */
internal object F9p1FakePampai {
  const val ConsentFile = "f9p1-pampai-finto"
  const val ProcessSuffix = ":pampai"

  /** Le domande magiche del modello finto, per gli errori e le misure. */
  const val AskLimit = "#limite"
  const val AskSlow = "#lento"
  const val AskLong = "#lungo"
  const val AskMeasure = "#misura"
  const val AskMemory = "#memoria"

  @Volatile private var made: Pair<Context, AiAssistHost>? = null

  /** La chat «Dalle app» del PampAI finto: in memoria, nel processo `:pampai` (PampAI vera la scrive nel suo database). */
  val history = InMemoryAiAssistHistory(capacity = 100)

  fun host(context: Context): AiAssistHost {
    val app = context.applicationContext
    made?.takeIf { it.first === app }?.let { return it.second }
    return synchronized(this) {
      made?.takeIf { it.first === app }?.second ?: create(app).also { made = app to it }
    }
  }

  private fun create(context: Context): AiAssistHost {
    val pkg = context.packageName
    val scopes = setOf(AiAssistPolicy.ScopeAnswer, AiAssistPolicy.ScopeSummarize, AiAssistPolicy.ScopeTools, AiAssistPolicy.ScopePage)
    return AiAssistHost(
      executor = F9p1FakeModel(pkg),
      // Nella debug il registro sta in un file (si toglie a fine sessione con run-as); nella bench, che non si apre con
      // run-as, resta in memoria: il tablet torna com'era senza file in piu'.
      consents = if (context.isDebuggableApp(extrasWithoutDebuggable = false)) SharedPreferencesAiAssistConsentStore(context, ConsentFile) else InMemoryAiAssistConsentStore(),
      policy = AiAssistPolicy(mapOf(pkg to AiAssistPolicy.Entry(pkg, "Fluid Glass", scopes))),
      ready = { ReadyState(true) },
      history = history,
    )
  }

  /** Il cliente della galleria: PampAI e' la galleria stessa (il suo provider `<pacchetto>.ai.assist`). */
  fun client(context: Context, pollMillis: Long = 0L): AiAssistClient =
    AiAssistClient(context, AiAssistClientConfig(hostPackage = context.packageName, devHostPackage = null, pollIntervalMillis = pollMillis))

  /**
   * Un cliente che cerca un PampAI che non c'e'. Anche lo Store e' inventato: la galleria non guarda nemmeno il Pampa
   * Store vero del telefono, quindi «Installa PampAI» mostra la spiegazione breve.
   */
  fun absentClient(context: Context): AiAssistClient = AiAssistClient(
    context,
    AiAssistClientConfig(hostPackage = "dev.pampa.pampai.finta.assente", devHostPackage = null, storePackage = "dev.pampa.store.finto.assente", devStorePackage = null),
  )

  /** Il processo dell'ospite finto, se e' vivo (solo sul telefono: nelle prove c'e' un processo solo). */
  fun processId(context: Context): Int? {
    val am = context.getSystemService(ActivityManager::class.java) ?: return null
    val name = context.packageName + ProcessSuffix
    return am.runningAppProcesses?.firstOrNull { it.processName == name }?.pid?.takeIf { it != android.os.Process.myPid() }
  }
}

/** Il provider dell'ospite finto (nel manifest di debug e della bench, processo `:pampai`, non esportato). */
class F9p1FakePampaiHost : AiAssistHostProvider() {
  override fun host(): AiAssistHost = F9p1FakePampai.host(context!!)
}

/**
 * Il modello finto: frasi preparate, a parole, con i tempi di un modello vero (35 ms a parola, a occhio). Usa la pagina
 * quando c'e', dice «Controllo i voti» coi tool dell'app, lascia una ricevuta quando aggiunge al calendario, e risponde
 * alle domande magiche di [F9p1FakePampai] per gli errori e le misure. Nessuna rete, nessuna chiave.
 */
internal class F9p1FakeModel(private val appPackage: String) : AiAssistExecutor {
  override suspend fun execute(call: AiAssistCall, sink: AiAssistSink) {
    val q = call.task.text.trim()
    when {
      q.startsWith(F9p1FakePampai.AskLimit) -> {
        sink.status("Penso…")
        delay(400)
        throw AiAssistException(AiAssistError(AiAssistError.LimitReached, retryAfterMillis = 120_000))
      }
      q.startsWith(F9p1FakePampai.AskSlow) -> {
        sink.status("Penso…")
        delay(40_000)
        words(sink, "Eccomi, finalmente.")
      }
      q.startsWith(F9p1FakePampai.AskLong) -> {
        sink.status("Scrivo una risposta lunga")
        repeat(40) { words(sink, LongParagraph) }
      }
      q.startsWith(F9p1FakePampai.AskMeasure) -> {
        val (count, step) = q.removePrefix(F9p1FakePampai.AskMeasure).trim().split(' ').mapNotNull { it.toIntOrNull() }.let { (it.getOrNull(0) ?: 200) to (it.getOrNull(1) ?: 5) }
        repeat(count) {
          delay(step.toLong())
          sink.text("${SystemClock.elapsedRealtimeNanos()} ")
        }
      }
      q.startsWith(F9p1FakePampai.AskMemory) -> {
        val heap = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1024 }
        sink.text("pss=${Debug.getPss()} heap=$heap ")
      }
      call.task.kind == AiAssistTask.KindSummarize -> {
        sink.status("Riassumo")
        delay(300)
        val points = call.page?.lines?.takeIf { it.isNotEmpty() } ?: call.task.text.split(". ").filter { it.isNotBlank() }.take(4)
        words(sink, points.joinToString("\n") { "• $it" } + "\n")
      }
      else -> answer(call, sink, q.lowercase())
    }
  }

  private suspend fun answer(call: AiAssistCall, sink: AiAssistSink, q: String) {
    val page = call.page
    if (page != null) {
      sink.status("Leggo la pagina")
      delay(350)
    }
    val tools = call.tools != AiAssistRequest.ToolsNone
    when {
      "calendario" in q || "aggiungi" in q -> {
        if (tools) { sink.status("Apro il calendario", appPackage); delay(700) }
        sink.receipt(AiAssistReceipt("Verifica di recupero aggiunta al calendario: mercoledì 3 dicembre", appPackage))
        words(sink, "Fatto: ho messo la verifica di recupero di matematica nel calendario, mercoledì 3 dicembre alle 9. Te la ricordo il giorno prima.")
      }
      "media" in q || "voto" in q -> {
        if (tools) { sink.status("Controllo i voti", appPackage); delay(800) }
        if (page != null) {
          words(sink, "Nella pagina «${page.title}» c'è un 5½ con peso pieno. Gli altri voti del quadrimestre fanno 7,1 di media: " +
            "un voto sotto la media con peso 100% la tira giù di circa tre decimi, e infatti ora è 6,8. Con un 7 alla verifica di " +
            "recupero torneresti sopra il 7.")
        } else {
          words(sink, "Non vedo la pagina che hai davanti, quindi non so di che voto parli. Dimmi la materia, il voto e il suo peso, " +
            "e ti dico quanto sposta la media.")
        }
      }
      else -> words(sink, "Sono il PampAI finto della galleria: rispondo con frasi preparate, senza rete e senza chiavi. Prova a " +
        "chiedermi della media o di aggiungere la verifica al calendario.")
    }
  }

  private suspend fun words(sink: AiAssistSink, text: String) {
    Regex("\\S+\\s*").findAll(text).forEach { delay(35); sink.text(it.value) }
    if (!text.last().isWhitespace()) sink.text(" ")
  }

  private companion object {
    const val LongParagraph = "Una risposta lunga arriva a pezzi, parola dopo parola, e si può fermare in ogni momento: PampAI " +
      "smette di lavorare e non spende altro. "
  }
}

/**
 * La pagina del PampAI finto, nel suo processo: il foglio del sì (aperto da un'app con `startActivityForResult`, come
 * farebbe PampAI: la prima volta per usare PampAI, e per la pagina, **in un foglio solo** se servono tutte e due),
 * oppure le sue impostazioni, «App che usano PampAI» e la chat «Dalle app».
 */
class F9p1FakePampaiActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val host = F9p1FakePampai.host(this)
    val consent = AiAssistConsent(host)
    val step = if (AiAssistConsent.isRequest(intent)) consent.begin(AiAssistConsent.callerOf(this), AiAssistConsent.wantsPage(intent)) else null
    when (step) {
      is AiAssistConsent.Step.Decided -> { setResult(RESULT_OK, AiAssistConsent.resultIntent(step)); finish(); return }
      AiAssistConsent.Step.Refused -> { setResult(RESULT_CANCELED); finish(); return }
      else -> Unit
    }
    setContent {
      FluidMotionPolicyProvider {
        FluidTheme(settings = remember { EngineSettings() }) {
          if (step is AiAssistConsent.Step.Ask) {
            F9p1ConsentPage(step, onAnswer = { choice ->
              val decided = consent.answer(step, choice)
              Log.i(F9p1LogTag, "CONSENSO app=${decided.app} pagina=${decided.page} scelta=«${choice.label}»")
              setResult(RESULT_OK, AiAssistConsent.resultIntent(decided))
              finish()
            }, onBack = { setResult(RESULT_CANCELED); finish() })
          } else {
            F9p1FakeSettingsPage(host, onBack = ::finish)
          }
        }
      }
    }
  }
}

@Composable
private fun F9p1ConsentPage(ask: AiAssistConsent.Step.Ask, onAnswer: (AiAssistConsent.Choice) -> Unit, onBack: () -> Unit) {
  FluidScreen(title = "PampAI", subtitle = "PampAI finto della galleria", onBack = onBack) {
    item(key = "domanda") {
      Column(Modifier.padding(top = 24.dp).testTag(F9p1Tags.ConsentSheet), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(ask.question, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        FluidSectionFootnote(
          (if (ask.askPage) "La pagina passa solo quando chiedi qualcosa, e il chip «Questa pagina» mostra cosa. " else "") +
            "Le domande restano nella chat «Dalle app» di PampAI. Puoi cambiare idea da «App che usano PampAI».",
        )
        ask.choices.forEachIndexed { index, choice ->
          FluidButton(
            choice.label,
            onClick = { onAnswer(choice) },
            style = if (index == 0) FluidButtonStyle.Filled else if (choice.allowApp) FluidButtonStyle.Tinted else FluidButtonStyle.Plain,
            fillWidth = true,
          )
        }
      }
    }
  }
}

private fun consentLine(state: AiAssistConsentState, allowed: String, denied: String, notAsked: String): String = when (state) {
  AiAssistConsentState.Allowed -> allowed
  AiAssistConsentState.Denied -> denied
  AiAssistConsentState.NotAsked -> notAsked
}

@Composable
private fun F9p1FakeSettingsPage(host: AiAssistHost, onBack: () -> Unit) {
  val context = LocalContext.current
  val pkg = context.packageName
  var app by remember { mutableStateOf(host.consents.appConsent(pkg)) }
  var page by remember { mutableStateOf(host.consents.pageConsent(pkg)) }
  var entries by remember { mutableStateOf(F9p1FakePampai.history.entries()) }
  FluidScreen(title = "App che usano PampAI", subtitle = "PampAI finto della galleria", onBack = onBack) {
    item(key = "app") {
      Column {
        FluidSectionHeader(title = "Fluid Glass")
        FluidListGroup {
          FluidToggleRow(
            title = "Può chiedere a PampAI",
            subtitle = consentLine(app, "Rispondere, riassumere e usare i suoi tool", "No: riceve «non autorizzata»", "Non ancora chiesto: alla prima domanda PampAI chiede"),
            checked = app == AiAssistConsentState.Allowed,
            onCheckedChange = { allow ->
              val value = if (allow) AiAssistConsentState.Allowed else AiAssistConsentState.Denied
              host.setAppConsent(pkg, value); app = value
            },
          )
          FluidListDivider()
          FluidToggleRow(
            title = "Può mandare la pagina",
            subtitle = consentLine(page, "Consentita: passa quando chiedi", "Negata: le domande con la pagina ricevono «serve il sì»", "Non ancora chiesto"),
            checked = page == AiAssistConsentState.Allowed,
            onCheckedChange = { allow ->
              val value = if (allow) AiAssistConsentState.Allowed else AiAssistConsentState.Denied
              host.setPageConsent(pkg, value); page = value
            },
          )
          FluidListDivider()
          FluidListRow(title = "Dimentica le scelte", subtitle = "Alla prossima domanda PampAI chiede di nuovo, in un foglio solo", onClick = {
            host.forget(pkg); app = AiAssistConsentState.NotAsked; page = AiAssistConsentState.NotAsked
          })
        }
        FluidSectionFootnote("Le scelte stanno in PampAI, non nell'app: un'app non può rispondersi da sola. Dire no o revocare ferma anche le domande in corso.")
      }
    }
    item(key = "dalle-app") {
      Column(Modifier.testTag(F9p1Tags.History)) {
        FluidSectionHeader(title = "Dalle app", detail = if (entries.isEmpty()) "Nessuna domanda" else "${entries.size} domande, la più nuova in cima")
        if (entries.isNotEmpty()) {
          FluidListGroup {
            entries.forEachIndexed { index, e ->
              if (index > 0) FluidListDivider()
              val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(e.atMillis))
              FluidListRow(
                eyebrow = "${e.appName} · $time" + (e.pageTitle?.let { " · «$it»" } ?: ""),
                title = e.question.ifBlank { "Domanda" },
                titleMaxLines = 2,
                subtitle = if (e.outcome == AiAssistHistoryEntry.OutcomeDone) e.answer.trim().ifBlank { "Fatto" } else AiAssistError(e.outcome).message(),
                subtitleMaxLines = 3,
                // F9.1c: le domande restano finché non le togli, una per una o tutte.
                badge = {
                  FluidButton("Togli", onClick = {
                    F9p1FakePampai.history.remove(e.requestId)
                    entries = F9p1FakePampai.history.entries()
                  }, style = FluidButtonStyle.Plain, size = FluidButtonSize.Small, modifier = Modifier.testTag(F9p1Tags.historyRemove(index)))
                },
              )
            }
            FluidListDivider()
            FluidListRow(title = "Svuota «Dalle app»", subtitle = "Le domande di tutte le app", onClick = { F9p1FakePampai.history.forget(); entries = emptyList() })
          }
        }
        FluidSectionFootnote(
          "Si salvano l'app, la domanda o il testo da riscrivere e riassumere (intero), il titolo della pagina, la risposta " +
            "e le ricevute, e restano finché non le togli. Della pagina non si salvano le righe né la selezione.",
        )
      }
    }
  }
}

// ── La pagina dell'app: chiedi da una pagina ──────────────────────────────────────────────────────────────────

/** La pagina di ClasseViva finta, come la descrive l'app: corta, solo ciò che si vede. */
internal val F9p1Page = AiAssistPage(
  title = "Matematica · verifica del 12 novembre",
  lines = listOf("Voto 5½, peso 100%", "Media del quadrimestre 6,8 (prima 7,1)", "Nota: ripassare le equazioni di secondo grado"),
  kind = "voto",
)

internal val F9p1Suggestions = listOf(
  "Perché questo voto abbassa la media?",
  "Aggiungi la verifica di recupero al calendario",
  "Riassumi la pagina",
)

@Composable
private fun F9p1AskScene(onBack: () -> Unit) = F9p1PageScene(onBack, absent = false)

@Composable
private fun F9p1AbsentScene(onBack: () -> Unit) = F9p1PageScene(onBack, absent = true)

/**
 * La pagina dei voti con «Chiedi a PampAI». Con [absent] il cliente cerca un PampAI che non c'e': il tasto si vede
 * spento con «Installa PampAI» (F9.1b, scelta di Alessio), che apre Pampa Store o, senza lo Store, una spiegazione.
 */
@Composable
private fun F9p1PageScene(onBack: () -> Unit, absent: Boolean) {
  val context = LocalContext.current
  val client = remember { if (absent) F9p1FakePampai.absentClient(context) else F9p1FakePampai.client(context) }
  val scope = rememberCoroutineScope()
  val composer = rememberFluidComposerState()
  var status by remember { mutableStateOf<AiAssistStatus?>(null) }
  var withPage by rememberSaveable { mutableStateOf(true) }
  var showPage by remember { mutableStateOf(false) }
  var showInstall by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf<AiAssistProgress?>(null) }
  var asked by remember { mutableStateOf<String?>(null) }
  var job by remember { mutableStateOf<Job?>(null) }
  var waiting by remember { mutableStateOf<String?>(null) }
  var refresh by remember { mutableIntStateOf(0) }
  LaunchedEffect(refresh) { status = client.check() }
  val entry = AiAssistEntry.of(status)

  // La domanda parte da [start]; il foglio di PampAI la rimette in moto ([startAgain]) quando l'utente ha scelto.
  // Un riferimento semplice, non uno stato: si aggiorna a ogni composizione senza farne partire un'altra.
  val startAgain = remember { java.util.concurrent.atomic.AtomicReference<((String, AiAssistPage?) -> Unit)?>(null) }
  val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val answer = AiAssistConsent.resultOf(result.resultCode, result.data)
    refresh++
    val question = waiting ?: return@rememberLauncherForActivityResult
    waiting = null
    when {
      // Uscita senza scegliere: niente domanda.
      answer == null -> Unit
      // «Non consentire» la prima volta: l'app non e' autorizzata, e lo dice.
      answer.app != AiAssistConsentState.Allowed -> { asked = question; progress = AiAssistProgress(error = AiAssistError(AiAssistError.NotAuthorized)) }
      // Senza la pagina la domanda parte lo stesso (come Siri quando non si manda lo schermo).
      else -> {
        if (answer.page != AiAssistConsentState.Allowed) withPage = false
        startAgain.get()?.invoke(question, if (withPage && answer.page == AiAssistConsentState.Allowed) F9p1Page else null)
      }
    }
  }

  fun start(question: String, page: AiAssistPage?, retried: Boolean = false) {
    job?.cancel()
    asked = question
    composer.sent()
    val task = if (question == F9p1Suggestions[2]) AiAssistTask.summarize(page?.preview() ?: question, AiAssistTask.FormKeyPoints) else AiAssistTask.answer(question)
    job = scope.launch {
      client.ask(AiAssistRequest(task, page = page)).collect { p ->
        val error = p.error
        // PampAI chiede un sì che la scena non sapeva (le scelte dimenticate dalle sue impostazioni): il foglio, una volta.
        val intent = if (error?.code == AiAssistError.ConsentRequired && !retried) client.consentIntent(page = AiAssistError.NeedsPage in error.needs) else null
        if (intent != null) {
          waiting = question
          progress = null
          consentLauncher.launch(intent)
        } else {
          progress = p
        }
        if (p.finished) Log.i(F9p1LogTag, "RISPOSTA domanda=«$question» pagina=${page != null} esito=${error?.code ?: "done"} caratteri=${p.text.length}")
      }
    }
  }
  startAgain.set { q, page -> start(q, page, retried = true) }

  fun send(question: String) {
    if (question.isBlank()) return
    // Lo stato di PampAI non e' ancora arrivato: lo si chiede, poi si riprova.
    val s = status ?: run { scope.launch { status = client.check(); if (status != null) send(question) }; return }
    if (!AiAssistEntry.of(s).enabled) return
    val needApp = s.appConsent == AiAssistConsentState.NotAsked
    val needPage = withPage && s.pageConsent == AiAssistConsentState.NotAsked
    val intent = client.consentIntent(page = withPage)
    if ((needApp || needPage) && intent != null) {
      // Un foglio solo, anche quando mancano tutte e due le risposte.
      waiting = question
      consentLauncher.launch(intent)
      return
    }
    start(question, if (withPage && s.pageConsent == AiAssistConsentState.Allowed) F9p1Page else null)
  }

  val busy = progress?.finished == false
  // F9.1d: lo spazio in fondo alla pagina segue l'altezza di quello che le sta sopra (la risposta aperta, le pillole, il
  // composer), cosi' scorrendo fino in fondo l'ultima riga esce da sotto. Si scrive quando l'overlay cambia misura e si
  // legge nella misura dell'ultimo elemento della lista: la risposta che cresce riga per riga non ricompone la pagina.
  val overlayHeight = remember { mutableIntStateOf(0) }
  FluidScreen(
    title = "Matematica",
    subtitle = if (absent) "F9.1 · pampai-assente · ClasseViva finta, senza PampAI" else "F9.1 · pampai-chiedi · ClasseViva finta",
    onBack = onBack,
    overlay = { pageGlass ->
      Column(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          // Dentro i margini del sistema: quelli la lista li ha gia' in fondo (FluidScreenDefaults.bottomContentPadding).
          .onSizeChanged { if (overlayHeight.intValue != it.height) overlayHeight.intValue = it.height }
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        progress?.let { p ->
          F9p1AnswerCard(asked, p, onRetry = { asked?.let(::send) }, modifier = Modifier.fluidGlassPanel(pageGlass, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Group), GlassRole.Modal))
        }
        if (status != null && !entry.enabled) {
          F9p1DisabledEntry(entry, onAction = {
            when (entry.action) {
              AiAssistEntry.ActionInstall, AiAssistEntry.ActionUpdate -> {
                val store = client.installIntent()
                if (store != null) runCatching { context.startActivity(store) } else showInstall = true
              }
              AiAssistEntry.ActionOpen -> client.openIntent()?.let { runCatching { context.startActivity(it) } }
            }
          }, modifier = Modifier.fluidGlassPanel(pageGlass, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Group), GlassRole.Modal))
        } else {
          FluidComposer(
            value = composer.text,
            onValueChange = { composer.text = it },
            backdrop = pageGlass,
            action = fluidComposerAction(hasInput = composer.text.isNotBlank(), busy = busy, listening = false, speaking = false, micAvailable = false),
            onSend = { send(composer.text.trim()) },
            onStop = { job?.cancel(); progress = progress?.copy(error = AiAssistError(AiAssistError.Cancelled)) },
            placeholder = "Chiedi a PampAI…",
            requestMicPermission = false,
            modifier = Modifier.testTag(F9p1Tags.Composer),
            pills = {
              FluidComposerPagePill(
                title = F9p1Page.title,
                included = withPage,
                onIncludedChange = { withPage = it },
                onShowContents = { showPage = true },
                modifier = Modifier.testTag(F9p1Tags.PagePill),
              )
              // F9.1d: i suggerimenti sono capsule di vetro (il chip dell'assistente, che rifrange la pagina): sopra il
              // testo della pagina una pillola senza fondo si confondeva con le righe dietro.
              F9p1Suggestions.forEachIndexed { index, s ->
                FluidChipPill(s, backdrop = pageGlass, onClick = { send(s) }, modifier = Modifier.testTag(F9p1Tags.suggestion(index)))
              }
            },
          )
        }
      }
    },
  ) {
    item(key = "voti") {
      FluidListGroup {
        FluidListRow(title = "Verifica del 12 novembre", subtitle = "Equazioni di secondo grado", meta = "5½")
        FluidListDivider()
        FluidListRow(title = "Peso", subtitle = "Scritto", meta = "100%")
        FluidListDivider()
        FluidListRow(title = "Media del quadrimestre", subtitle = "Prima della verifica 7,1", meta = "6,8")
        FluidListDivider()
        FluidListRow(title = "Nota del docente", subtitle = "Ripassare le equazioni di secondo grado")
      }
    }
    item(key = "pampai") {
      Column(Modifier.testTag(F9p1Tags.PampaiSection)) {
        FluidSectionHeader(title = if (absent) "Senza PampAI" else "PampAI finto", detail = status?.let { pampaiLine(it) } ?: "Lo cerco…")
        if (!absent) {
          FluidListGroup {
            FluidListRow(
              title = "Apri PampAI finto",
              subtitle = "Le sue impostazioni: dire no alla galleria, revocare il sì per la pagina, la chat «Dalle app»",
              onClick = { context.startActivity(android.content.Intent(context, F9p1FakePampaiActivity::class.java)) },
              modifier = Modifier.testTag(F9p1Tags.OpenPampai),
            )
          }
        }
        FluidSectionFootnote(
          if (absent) "Senza PampAI il tasto si vede spento, con «Installa PampAI». Se PampAI c'è ma non ha autorizzato l'app, il tasto dice solo «non autorizzata»."
          else "La prima volta PampAI chiede se la galleria può usarla e, con la pagina, anche quella: un foglio solo. Le chiavi e i modelli restano in PampAI.",
        )
      }
    }
    item(key = "sotto-pampai") {
      // Alto quanto l'overlay, letto qui nella misura (F9.1d).
      Spacer(
        Modifier
          .fillMaxWidth()
          .testTag(F9p1Tags.BottomSpace)
          .layout { measurable, constraints ->
            val height = overlayHeight.intValue
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
            layout(placeable.width, height) { placeable.place(0, 0) }
          },
      )
    }
  }

  FluidGlassSheet(visible = showPage, onDismissRequest = { showPage = false }, detents = setOf(FluidSheetDetent.Medium), paneTitle = "Cosa vede PampAI") {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag(F9p1Tags.PageSheet), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Cosa vede PampAI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      Text(F9p1Page.preview(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
      FluidSectionFootnote("${F9p1Page.length} caratteri su ${AiAssistPage.MaxTotalChars}. Passa solo con la domanda.")
      FluidButton("Togli la pagina", onClick = { withPage = false; showPage = false }, style = FluidButtonStyle.Tinted, fillWidth = true)
    }
  }
  FluidGlassSheet(visible = showInstall, onDismissRequest = { showInstall = false }, detents = setOf(FluidSheetDetent.Medium), paneTitle = "Installa PampAI") {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag(F9p1Tags.InstallSheet), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Installa PampAI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      Text(AiAssistTexts.installHint(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
      FluidButton("Ho capito", onClick = { showInstall = false }, style = FluidButtonStyle.Tinted, fillWidth = true)
    }
  }
}

/**
 * Il tasto «Chiedi a PampAI» spento (F9.1b): la riga di [AiAssistEntry.note] e, se c'e', l'azione («Installa PampAI»).
 * Per TalkBack il tasto e' un nodo disattivato con la sua ragione.
 */
@Composable
private fun F9p1DisabledEntry(entry: AiAssistEntry, onAction: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag(F9p1Tags.DisabledEntry), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.clearAndSetSemantics {
        contentDescription = "Chiedi a PampAI" + (entry.note?.let { ": $it" } ?: "")
        role = Role.Button
        disabled()
      },
    ) {
      FluidIcon(FluidIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Column {
        Text("Chiedi a PampAI", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        entry.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
    }
    entry.actionLabel?.let { label ->
      FluidButton(label, onClick = onAction, style = FluidButtonStyle.Tinted, fillWidth = true, modifier = Modifier.testTag(F9p1Tags.EntryAction))
    }
  }
}

private fun pampaiLine(status: AiAssistStatus): String = when {
  status.error != null -> status.error!!.message()
  else -> "Pronta · la galleria: " + consentLine(status.appConsent, "consentita", "no", "da chiedere") +
    " · la pagina: " + consentLine(status.pageConsent, "consentita", "negata", "da chiedere")
}

/** La risposta di PampAI sopra il composer: la domanda, la riga di stato con l'icona dell'app, il testo che arriva sfocato, le ricevute, l'errore. */
@Composable
private fun F9p1AnswerCard(question: String?, progress: AiAssistProgress, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier
      .fillMaxWidth()
      .heightIn(max = 260.dp)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag(F9p1Tags.Answer),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    question?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    progress.status?.let { s ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(F9p1Tags.Status).semantics { liveRegion = LiveRegionMode.Polite }) {
        FluidIcon(if (s.app != null) FluidIcons.School else FluidIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(s.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (progress.text.isNotEmpty()) {
      // Il testo che arriva dall'app entra sfocato, parola per parola (F8); con «Riduci movimento» compare fermo.
      val arriving = !progress.finished && !LocalFluidMotionPolicy.current.reducedMotion
      FluidRevealingText(
        text = AnnotatedString(progress.text.trimEnd()),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        reveal = arriving,
        animateFirst = true,
        modifier = Modifier.testTag(F9p1Tags.AnswerText).semantics { this[F9p1Arriving] = arriving },
      )
    }
    progress.receipts.forEach { r ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(F9p1Tags.Receipt)) {
        FluidIcon(FluidIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(r.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
      }
    }
    progress.error?.let { e ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(F9p1Tags.Error).semantics { liveRegion = LiveRegionMode.Polite }) {
        FluidIcon(FluidIcons.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(e.message(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        // F9.1d: «Riprova» solo dove la stessa domanda puo' andare; mai per «non autorizzata» (il tasto e' gia' spento).
        if (e.retryable) FluidButton("Riprova", onClick = onRetry, style = FluidButtonStyle.Plain, modifier = Modifier.testTag(F9p1Tags.Retry))
      }
    }
  }
}

// ── Gli errori ────────────────────────────────────────────────────────────────────────────────────────────────

private class F9p1ErrorCase(val id: String, val title: String, val detail: String, val attempt: suspend (Context) -> AiAssistProgress)

private val F9p1ErrorCases: List<F9p1ErrorCase> = listOf(
  F9p1ErrorCase("assente", "PampAI non c'è", "Un'app cerca un PampAI che non è installato") { c ->
    F9p1FakePampai.absentClient(c).run(AiAssistRequest(AiAssistTask.answer("Ciao")))
  },
  F9p1ErrorCase("ambito", "Non autorizzata", "Riscrivere non è nell'ambito della galleria, nella tabella di PampAI finto") { c ->
    F9p1FakePampai.client(c).run(AiAssistRequest(AiAssistTask.rewrite("ciao prof, domani non ci sono", AiAssistTask.StyleProfessional)))
  },
  F9p1ErrorCase("consenso", "Serve il sì", "Una domanda con la pagina quando il sì non c'è (negalo da «Apri PampAI finto»)") { c ->
    F9p1FakePampai.client(c).run(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), page = F9p1Page))
  },
  F9p1ErrorCase("limite", "Limite raggiunto", "Il modello finto risponde come un fornitore al limite (429)") { c ->
    F9p1FakePampai.client(c).run(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskLimit)))
  },
  F9p1ErrorCase("tempo", "Tempo scaduto", "Il modello finto ci mette 40 secondi, la domanda ne concede 4") { c ->
    F9p1FakePampai.client(c).run(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskSlow), timeoutMillis = 4_000))
  },
)

@Composable
private fun F9p1ErrorsScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ensure = rememberF9p1Consent()
  val results = remember { mutableStateOf(mapOf<String, String>()) }
  fun put(id: String, text: String) { results.value = results.value + (id to text) }
  FluidScreen(title = "Gli errori", subtitle = "F9.1 · pampai-errori", onBack = onBack) {
    item(key = "casi") {
      FluidListGroup {
        F9p1ErrorCases.forEachIndexed { index, case ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = case.title,
            subtitle = results.value[case.id] ?: case.detail,
            subtitleMaxLines = 3,
            modifier = Modifier.testTag(F9p1Tags.errorRow(case.id)),
            onClick = {
              ensure {
                put(case.id, "In corso…")
                scope.launch {
                  val p = case.attempt(context)
                  put(case.id, p.error?.let { "${it.message()} · ${it.code}" } ?: "Risposta: ${p.text.take(60)}")
                  Log.i(F9p1LogTag, "ERRORE caso=${case.id} codice=${p.error?.code}")
                }
              }
            },
          )
        }
        FluidListDivider()
        FluidListRow(
          title = "Annullata",
          subtitle = results.value["annullata"] ?: "Una risposta lunga, fermata dopo un secondo: PampAI smette di lavorare",
          subtitleMaxLines = 3,
          modifier = Modifier.testTag(F9p1Tags.errorRow("annullata")),
          onClick = { ensure {
            put("annullata", "In corso…")
            scope.launch {
              var last: AiAssistProgress? = null
              val run = launch { F9p1FakePampai.client(context).ask(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskLong))).collect { last = it } }
              delay(1_200)
              run.cancel()
              put("annullata", "${AiAssistError(AiAssistError.Cancelled).message()} dopo ${last?.text?.length ?: 0} caratteri · cancelled")
            }
          } },
        )
        FluidListDivider()
        FluidListRow(
          title = "PampAI si è fermata",
          subtitle = results.value["fermata"] ?: "Il processo del PampAI finto muore a metà risposta (solo sul telefono)",
          subtitleMaxLines = 3,
          modifier = Modifier.testTag(F9p1Tags.errorRow("fermata")),
          onClick = { ensure {
            put("fermata", "In corso…")
            scope.launch {
              val run = launch {
                val p = F9p1FakePampai.client(context).run(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskLong)))
                put("fermata", p.error?.let { "${it.message()} · ${it.code}" } ?: "Risposta intera")
              }
              delay(1_200)
              val pid = F9p1FakePampai.processId(context)
              if (pid == null) { run.cancel(); put("fermata", "Qui l'ospite sta nello stesso processo: si prova sul telefono") } else Process.killProcess(pid)
            }
          } },
        )
      }
    }
    item(key = "nota") {
      FluidSectionFootnote("I codici sono fissi e le parole sono dell'engine: nessun testo di un fornitore arriva all'app, quindi nessuna chiave.")
    }
  }
}

// ── Le misure ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Le misure del ponte, per il rapporto: un giro di `call`, il ritardo dei pezzi, la memoria. */
internal class F9p1Measures(
  val roundTripMicros: LongArray,
  val crossProcess: Boolean,
  val pushedLagMillis: DoubleArray,
  val pushedUpdates: Int,
  val polledLagMillis: DoubleArray,
  val polledUpdates: Int,
  val hostPssBeforeKb: Long?,
  val hostPssAfterKb: Long?,
  val appPssKb: Long,
  val asks: Int = F9p1MemoryAsks,
  val asksMillis: Long? = null,
) {
  fun summary(): String = buildString {
    append("Un giro di call (${if (crossProcess) "fra due processi" else "nello stesso processo"}, ${roundTripMicros.size} volte): ")
    append("p50 ${pct(roundTripMicros, 50)} µs, p95 ${pct(roundTripMicros, 95)} µs, p99 ${pct(roundTripMicros, 99)} µs, max ${roundTripMicros.maxOrNull() ?: 0} µs.\n")
    append("Pezzi col canale: ritardo p50 ${fmt(pctD(pushedLagMillis, 50))} ms, p95 ${fmt(pctD(pushedLagMillis, 95))} ms, $pushedUpdates aggiornamenti.\n")
    append("Pezzi con assist_poll ogni 50 ms: p50 ${fmt(pctD(polledLagMillis, 50))} ms, p95 ${fmt(pctD(polledLagMillis, 95))} ms, $polledUpdates aggiornamenti.\n")
    val took = asksMillis?.let { " (in ${(it + 500) / 1000} s, col limite di 30 al minuto)" }.orEmpty()
    append("Memoria del PampAI finto: ${hostPssBeforeKb ?: "?"} kB, dopo $asks domande$took ${hostPssAfterKb ?: "?"} kB. La galleria: $appPssKb kB.")
  }

  companion object {
    fun pct(values: LongArray, p: Int): Long = values.sorted().let { if (it.isEmpty()) 0 else it[((it.size - 1) * p / 100.0).toInt()] }
    fun pctD(values: DoubleArray, p: Int): Double = values.sorted().let { if (it.isEmpty()) 0.0 else it[((it.size - 1) * p / 100.0).toInt()] }
    private fun fmt(v: Double) = String.format(java.util.Locale.ITALY, "%.1f", v)
  }
}

/** Un giro di `negotiate` col provider del PampAI finto, [count] volte dopo 30 di riscaldamento, in microsecondi. */
internal fun f9p1RoundTrips(context: Context, count: Int): LongArray {
  val client = context.contentResolver.acquireUnstableContentProviderClient("${context.packageName}.ai.assist") ?: return LongArray(0)
  try {
    val extras = Bundle().apply { putString("json", """{"versions":[3],"capabilities":["assist","assist_stream"]}""") }
    repeat(30) { client.call("negotiate", null, Bundle(extras)) }
    return LongArray(count) {
      val t = System.nanoTime()
      client.call("negotiate", null, Bundle(extras))
      (System.nanoTime() - t) / 1_000
    }
  } finally {
    client.close()
  }
}

/** Il ritardo di ogni parola dal PampAI finto a chi raccoglie (che qui e' il filo principale), in millisecondi. */
internal suspend fun f9p1StreamLags(client: AiAssistClient, count: Int, stepMillis: Int): Pair<DoubleArray, Int> {
  val lags = ArrayList<Double>(count)
  var updates = 0
  var seen = 0
  client.ask(AiAssistRequest(AiAssistTask.answer("${F9p1FakePampai.AskMeasure} $count $stepMillis"), tools = AiAssistRequest.ToolsNone)).collect { p ->
    updates++
    val now = SystemClock.elapsedRealtimeNanos()
    val words = p.text.split(' ').filter { it.isNotEmpty() }
    for (i in seen until words.size) words[i].toLongOrNull()?.let { lags += (now - it) / 1_000_000.0 }
    seen = words.size
  }
  return lags.toDoubleArray() to updates
}

private suspend fun f9p1HostPss(client: AiAssistClient): Long? =
  Regex("pss=(\\d+)").find(f9p1WithinLimit { client.run(AiAssistRequest(AiAssistTask.answer(F9p1FakePampai.AskMemory), tools = AiAssistRequest.ToolsNone)) }.text)?.groupValues?.get(1)?.toLongOrNull()

/** Le domande fra le due letture della memoria del PampAI finto. */
internal const val F9p1MemoryAsks = 48

/**
 * Una domanda della misura **nel limite di PampAI** (30 al minuto per app), come deve fare un'app vera (F9.1d): se torna
 * «limite raggiunto» aspetta quanto dice PampAI ([AiAssistError.retryAfterMillis]) e la rifà. Prima la misura le faceva
 * tutte di fila: dalla trentesima PampAI le rifiutava, compresa quella che legge la memoria («dopo 50 domande ? kB»).
 * Adesso le domande dopo la trentesima aspettano che il minuto passi: la misura dura un minuto in piu'.
 */
internal suspend fun f9p1WithinLimit(ask: suspend () -> AiAssistProgress): AiAssistProgress {
  repeat(3) {
    val progress = ask()
    val error = progress.error ?: return progress
    if (error.code != AiAssistError.LimitReached) return progress
    delay((error.retryAfterMillis ?: 1_000L).coerceIn(100L, 61_000L))
  }
  return ask()
}

@Composable
private fun F9p1MeasureScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ensure = rememberF9p1Consent()
  var result by remember { mutableStateOf<String?>(null) }
  var running by remember { mutableStateOf(false) }
  FluidScreen(title = "Le misure del ponte", subtitle = "F9.1 · pampai-misure", onBack = onBack) {
    item(key = "misura") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidSectionFootnote(
          "Il PampAI finto sta nel processo «:pampai»: ogni chiamata è un vero Binder fra due processi, come fra un'app e PampAI. " +
            "La misura fa 500 giri di call, due risposte di 200 parole (una ogni 5 ms) col canale di ritorno e con assist_poll, " +
            "e legge la memoria del PampAI finto prima e dopo $F9p1MemoryAsks domande. Le domande stanno nel limite di PampAI " +
            "(30 al minuto): quando lo raggiungono aspettano, e la misura dura circa un minuto e mezzo.",
        )
        FluidButton(if (running) "Misuro…" else "Misura", onClick = {
          if (running) return@FluidButton
          ensure { running = true; scope.launch {
            val text = withContext(Dispatchers.IO) {
              val client = F9p1FakePampai.client(context)
              val trips = f9p1RoundTrips(context, 500)
              val cross = F9p1FakePampai.processId(context) != null
              val before = f9p1HostPss(client)
              val (pushed, pushedUpdates) = withContext(Dispatchers.Main) { f9p1StreamLags(client, 200, 5) }
              val (polled, polledUpdates) = withContext(Dispatchers.Main) { f9p1StreamLags(F9p1FakePampai.client(context, pollMillis = 50), 200, 5) }
              val askStart = SystemClock.elapsedRealtime()
              repeat(F9p1MemoryAsks) {
                f9p1WithinLimit { client.run(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), tools = AiAssistRequest.ToolsNone, timeoutMillis = 30_000)) }
              }
              val askMillis = SystemClock.elapsedRealtime() - askStart
              val after = f9p1HostPss(client)
              F9p1Measures(trips, cross, pushed, pushedUpdates, polled, polledUpdates, before, after, Debug.getPss(), F9p1MemoryAsks, askMillis).summary()
            }
            Log.i(F9p1LogTag, "MISURE ${text.replace('\n', ' ')}")
            result = text
            running = false
          } }
        }, loading = running, fillWidth = true, modifier = Modifier.testTag(F9p1Tags.Measure))
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag(F9p1Tags.MeasureResult)) }
      }
    }
  }
}

/**
 * Prima di provare (gli errori, le misure): se PampAI finto non ha ancora il sì per la galleria, apre il suo foglio (una
 * volta), poi fa [then]. Le scene non scrivono mai il registro di PampAI: come un'app vera, chiedono a lei.
 */
@Composable
private fun rememberF9p1Consent(): (then: () -> Unit) -> Unit {
  val context = LocalContext.current
  val client = remember { F9p1FakePampai.client(context) }
  val scope = rememberCoroutineScope()
  val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val next = pending.value
    pending.value = null
    if (AiAssistConsent.resultOf(result.resultCode, result.data) != null) next?.invoke()
  }
  return remember(launcher) {
    { then ->
      scope.launch {
        val status = client.check()
        val intent = client.consentIntent()
        if (status.appConsent == AiAssistConsentState.NotAsked && intent != null) {
          pending.value = then
          launcher.launch(intent)
        } else {
          then()
        }
      }
    }
  }
}
