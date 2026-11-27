package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.memory.AiMemory
import dev.antigravity.fluidengine.ai.memory.AiMemoryAppPolicy
import dev.antigravity.fluidengine.ai.memory.AiMemoryBook
import dev.antigravity.fluidengine.ai.memory.AiMemoryCaller
import dev.antigravity.fluidengine.ai.memory.AiMemoryConfig
import dev.antigravity.fluidengine.ai.memory.AiMemoryEvidence
import dev.antigravity.fluidengine.ai.memory.AiMemoryExclusion
import dev.antigravity.fluidengine.ai.memory.AiMemoryExtractor
import dev.antigravity.fluidengine.ai.memory.AiMemoryInbox
import dev.antigravity.fluidengine.ai.memory.AiMemoryLegacyRow
import dev.antigravity.fluidengine.ai.memory.AiMemoryNotice
import dev.antigravity.fluidengine.ai.memory.AiMemoryPolicy
import dev.antigravity.fluidengine.ai.memory.AiMemoryProfileRow
import dev.antigravity.fluidengine.ai.memory.AiMemoryPrompt
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposal
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult
import dev.antigravity.fluidengine.ai.memory.AiMemoryProtocol
import dev.antigravity.fluidengine.ai.memory.AiMemorySignals
import dev.antigravity.fluidengine.ai.memory.AiMemorySource
import dev.antigravity.fluidengine.ai.memory.AiMemoryStore
import dev.antigravity.fluidengine.ai.memory.AiMemoryTurn
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.orchestrator.HistoryCompactor
import dev.antigravity.fluidengine.ai.provider.ChatDelta
import dev.antigravity.fluidengine.ai.provider.ChatProvider
import dev.antigravity.fluidengine.ai.provider.ChatRequest
import dev.antigravity.fluidengine.ai.provider.ChatTurn
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.Message
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import dev.antigravity.fluidengine.ai.provider.Transcript
import dev.antigravity.fluidengine.ai.provider.Usage
import dev.antigravity.fluidengine.aiui.chat.FluidAssistantBubble
import dev.antigravity.fluidengine.aiui.chat.FluidUserBubble
import dev.antigravity.fluidengine.aiui.memory.FluidMemoryActions
import dev.antigravity.fluidengine.aiui.memory.FluidMemoryNoticeCard
import dev.antigravity.fluidengine.aiui.memory.FluidMemoryNotices
import dev.antigravity.fluidengine.aiui.memory.FluidMemoryPage
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.VaultAccess
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// F9.3 e F9.3b · Una memoria che funziona: la memoria di PampAI fatta con l'engine (engine-ai-memory), la card «Vuoi che
// lo ricordi?» (di serie) o «Ho ricordato…» (con «Ricorda da solo») e la pagina «Cosa ricordo di te» (engine-ai-ui). Una
// conversazione finta fa nascere ricordi, li corregge e li revoca; ClasseViva finta propone un ricordo col formato del
// ponte, Pampa Notes no (puo' solo il modello della chat, con lo strumento). Il modello dell'estrattore e' finto (nessuna chiamata vera,
// nessuna chiave): legge le frasi del copione e risponde come risponderebbe un modello piccolo. La memoria delle scene sta
// in memoria e ricomincia da capo a ogni apertura: sul telefono non resta niente. La cassaforte vera la misura «Le misure»,
// che poi butta la sua sezione. Il log `F9p3` scrive le misure per la sessione sul tablet.

/** Le tre scene di F9.3. */
internal val F9p3Section = NewElementSection(
  pack = "F9.3",
  title = "La memoria di PampAI",
  detail = "I ricordi nascono dopo i turni, si vedono, si correggono, si revocano; solo quelli che c'entrano entrano nella domanda",
  scenes = listOf(
    NewElementScene(
      "ricordi-chat", "Una conversazione che ricorda",
      "PampAI · «Vuoi che lo ricordi?» con Ricorda e No, o «Ho ricordato…» con Annulla; l'aggiornamento che dice cosa c'era " +
        "prima, le app che propongono, la chat temporanea",
    ) { F9p3ChatScene(it) },
    NewElementScene(
      "ricordi-pagina", "Cosa ricordo di te",
      "La pagina: Ricorda da solo, da confermare, da te, dalle app, dedotto dalle foto, dalla memoria di prima; correggi, " +
        "dimentica, non imparare più",
    ) { F9p3PageScene(it) },
    NewElementScene(
      "ricordi-misure", "Le misure della memoria",
      "Quante volte si chiama il modello piccolo, quanto pesa, il blocco nel prompt contro quello di oggi, la cassaforte",
    ) { F9p3MeasureScene(it) },
  ),
)

/** Le voci da provare a mano (in coda a `ToTry.kt`). Una frase sola per voce, come vuole la galleria. */
internal val F9p3TryItems: List<TryItem> = listOf(
  TryItem(
    "f9-3-chat", TryArea.AppParts, "Una conversazione che ricorda",
    "Tocca «Scrivi» e rispondi «Ricorda» o «No» alle card «Vuoi che lo ricordi?», poi accendi «Ricorda da solo» prima della " +
      "5F e guarda «Ho aggiornato» con sotto «prima: è in 4F» e Annulla, e «Ricordati che mi chiamo Giulia» che entra subito.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Una conversazione che ricorda"),
  ),
  TryItem(
    "f9-3-app", TryArea.AppParts, "Un ricordo dalle app",
    "Nella conversazione tocca «ClasseViva propone la verifica» e rispondi nella notifica di vetro, poi «Pampa Notes propone» " +
      "che non può farlo da sola e «Il modello ricorda coi dati di Notes» che invece chiede, e accendi «Chat temporanea» per " +
      "vedere che il messaggio dopo non lascia ricordi.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Una conversazione che ricorda"),
  ),
  TryItem(
    "f9-3-pagina", TryArea.AppParts, "Cosa ricordo di te",
    "Apri la pagina, conferma il ricordo «Da confermare», leggi il fatto dedotto da MyCloud Gallery coi suoi limiti, usa " +
      "«Dimentica e non imparare più» con la pressione lunga e poi «Impara di nuovo», in chiaro e in scuro.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Cosa ricordo di te"),
  ),
)

/** I tag delle prove e delle riprese. */
internal object F9p3Tags {
  const val Chat = "f9p3:chat"
  const val Send = "f9p3:scrivi"
  const val Propose = "f9p3:classeviva"
  const val Temporary = "f9p3:temporanea"
  const val AutoSave = "f9p3:ricorda-da-solo"
  const val Notes = "f9p3:notes"
  const val Tool = "f9p3:strumento"
  const val ToolCard = "f9p3:card:strumento"
  const val OpenPage = "f9p3:pagina"
  const val Restart = "f9p3:ricomincia"
  const val Prompt = "f9p3:prompt"
  const val Measures = "f9p3:misure"

  fun card(turn: Int): String = "f9p3:card:$turn"
  fun backdrop(b: F9p3Backdrop): String = "f9p3:fondo:${b.name}"
}

private const val Tag = "F9p3"

/** Il fondo delle scene: la pagina, l'alone, il motivo a carte (il vetro delle card e dei gruppi li campiona). */
internal enum class F9p3Backdrop(val label: String, val ambient: FluidAmbient?) {
  Page("Pagina", null),
  Glow("Alone", FluidAmbient(tone = FluidHeroTone.PrimaryToSecondary, motif = FluidHeroMotif.Glow)),
  Cards("Motivo", FluidAmbient(tone = FluidHeroTone.TertiaryToPrimary, motif = FluidHeroMotif.Cards, intensity = 1.2f)),
}

// ================================================================================================================
// La memoria della galleria e il modello finto
// ================================================================================================================

/** Il fuso della galleria: le date dei ricordi e le scadenze. */
private val F9p3Zone: ZoneId get() = ZoneId.systemDefault()

/**
 * Come la galleria apre la memoria di una scena ([name] e' il nome della scena): **in memoria**, che sparisce con la
 * scena. La galleria gira su un telefono che non e' suo, e la conversazione finta non deve lasciare file: la cassaforte
 * vera la prova la scena «Le misure», che apre una sua sezione, misura e poi la butta (`VaultSection.reset`: chiavi,
 * scheda e file). Le prove della galleria la cambiano con la loro.
 */
internal val LocalF9p3Books = staticCompositionLocalOf<suspend (Context, String) -> AiMemoryBook> {
  { _, _ -> AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig(zone = F9p3Zone)) }
}

/**
 * Il modello finto dell'estrattore: **nessuna chiamata vera**. Legge la frase dell'utente nella richiesta e risponde col
 * JSON che darebbe un modello piccolo; l'uso dei gettoni e' quello stimato (3,5 caratteri l'uno), come lo direbbe il
 * fornitore. Per una frase che non conosce risponde «niente».
 */
internal object F9p3FakeModel : ChatProvider {
  override val id: ProviderId = ProviderId.GROQ

  override suspend fun complete(request: ChatRequest): ChatTurn {
    val user = (request.messages.last() as Message.User).text
    val said = user.substringAfter("«").substringBefore("»").lowercase()
    val text = when {
      "4f" in said && "galilei" in said -> facts(fact("è in 4F al liceo Galilei", "scuola.classe", "scuola", "classe", "compiti", "verifiche"))
      "5f" in said -> facts(fact("è in 5F al liceo Galilei", "scuola.classe", "scuola", "classe", "compiti", "verifiche"))
      "vegetarian" in said -> facts(fact("è vegetariana", "cibo.dieta", "cibo", "cena", "ricette", "ristorante"))
      "password" in said -> facts(fact("la password del wifi è girasole42", "casa.wifi", "wifi"))
      "firenze" in said -> facts(fact("abita a Firenze", "casa.citta", "casa", "città", "meteo"))
      "giulia" in said -> facts(fact("si chiama Giulia", "persona.nome", "nome"))
      else -> facts()
    }
    val prompt = HistoryCompactor.estimateTokens(request.messages)
    val completion = HistoryCompactor.estimateTokens(text)
    return ChatTurn(Message.Assistant(text), FinishReason.STOP, Usage(prompt, completion, prompt + completion), RateLimitInfo.EMPTY)
  }

  private fun fact(testo: String, chiave: String, vararg argomenti: String) =
    "{\"testo\":\"$testo\",\"chiave\":\"$chiave\",\"argomenti\":[${argomenti.joinToString(",") { "\"$it\"" }}],\"scade\":null,\"sempre\":false}"

  private fun facts(vararg f: String) = "{\"fatti\":[${f.joinToString(",")}]}"

  override fun stream(request: ChatRequest): Flow<ChatDelta> = emptyFlow()
  override suspend fun listModels(): ModelCatalogue = ModelCatalogue(emptyList(), emptyList())
  override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = error("non serve")
}

/** Un passo del copione: cosa scrive l'utente e cosa risponde l'assistente (finto anche lui). */
internal class F9p3Step(val question: String, val answer: String)

/**
 * Il copione della conversazione: propone due ricordi (che di serie chiedono), ne salta uno (il meteo), ne aggiorna uno,
 * ne salva subito uno chiesto con «ricordati», rifiuta un segreto.
 */
internal val F9p3Script = listOf(
  F9p3Step("Ciao! Sono in 4F al Galilei, che compiti ho per domani?", "Per domani hai gli esercizi 3 e 4 a pagina 112 di matematica e il capitolo 6 di storia."),
  F9p3Step("Sono vegetariana: mi consigli una cena veloce?", "Una frittata di zucchine col pane tostato, o una pasta con ceci e rosmarino: venti minuti."),
  F9p3Step("Che tempo fa domani?", "Domani a Firenze sole e 14 gradi; la sera si scende a 6."),
  F9p3Step("Da lunedì sono in 5F, ho cambiato sezione", "D'accordo: da lunedì guardo i compiti della 5F."),
  F9p3Step("Ricordati che mi chiamo Giulia", "Piacere, Giulia: me lo ricordo."),
  F9p3Step("Ricordati che la password del wifi è girasole42", "Le password non le ricordo: finirebbero nelle domande che mando al modello. Tienila nel gestore delle password."),
)

/** Un turno mostrato: la domanda, la risposta, gli avvisi nati qui, e la riga piccola sul costo della memoria. */
internal class F9p3Shown(val index: Int, val question: String, val answer: String, val notices: List<String>, val note: String)

/** La riga piccola sotto una risposta: cosa ha fatto la memoria, e quanto e' costato. */
internal fun f9p3Note(skipped: String?, tokens: Int, remembered: Int, discarded: Int, asked: Int = 0): String = when (skipped) {
  AiMemoryPolicy.SkipTemporary -> "Memoria: chat temporanea, non ricordo niente e il modello non si chiama."
  AiMemoryPolicy.SkipNoSignal -> "Memoria: niente di te in questo messaggio, nessuna chiamata."
  null -> "Memoria: modello piccolo, ~$tokens gettoni" + when {
    asked > 0 -> "; prima di ricordare ti chiedo."
    remembered > 0 -> "."
    discarded > 0 -> "; scartato $discarded (sembra un segreto)."
    else -> "; niente da ricordare."
  }
  else -> "Memoria: nessuna chiamata ($skipped)."
}

// ================================================================================================================
// La conversazione
// ================================================================================================================

@Composable
private fun F9p3ChatScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val open = LocalF9p3Books.current
  val book by produceState<AiMemoryBook?>(null) {
    val b = open(context, "f9p3-memoria")
    // La scena ricomincia da capo: la memoria della scena e' sua, e la conversazione parte dal primo messaggio.
    b.forgetAll()
    value = b
  }
  val b = book
  if (b == null) {
    FluidScreen(title = "La memoria", subtitle = "F9.3 · apro la memoria…", onBack = onBack) {}
    return
  }
  var page by remember { mutableStateOf(false) }
  if (page) {
    F9p3MemoryPage(b, onBack = { page = false })
    return
  }
  F9p3Chat(b, onBack, onOpenPage = { page = true })
}

@Composable
internal fun F9p3Chat(book: AiMemoryBook, onBack: () -> Unit, onOpenPage: () -> Unit) {
  val scope = rememberCoroutineScope()
  val notices by book.notices.collectAsState()
  val memories by book.memories.collectAsState()
  val autoSave by book.autoSave.collectAsState()
  val host = LocalFluidNotificationHostState.current
  val extractor = remember { AiMemoryExtractor(F9p3FakeModel, "modello-piccolo-finto", zone = F9p3Zone) }
  // La tabella di serie (risposta 3 di Alessio): Pampa Mail, MyCloud Gallery, ClasseViva.
  val inbox = remember(book) { AiMemoryInbox(book, AiMemoryAppPolicy()) }
  var shown by remember { mutableStateOf<List<F9p3Shown>>(emptyList()) }
  var toolNotice by remember { mutableStateOf<String?>(null) }
  var temporary by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var prompt by remember { mutableStateOf(AiMemoryPrompt.Empty) }
  var appLine by remember { mutableStateOf<String?>(null) }
  var backdrop by remember { mutableStateOf(F9p3Backdrop.Glow) }
  val next = F9p3Script.getOrNull(shown.size)
  val listState = rememberLazyListState()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion

  FluidScreen(
    title = "PampAI",
    subtitle = "F9.3b · una conversazione finta: la memoria vera, il modello finto",
    onBack = onBack,
    modifier = Modifier.testTag(F9p3Tags.Chat),
    listState = listState,
    ambient = backdrop.ambient,
  ) {
    item(key = "fondi") {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        F9p3Backdrop.entries.forEach { b ->
          FluidChip(label = b.label, selected = b == backdrop, onClick = { backdrop = b }, modifier = Modifier.testTag(F9p3Tags.backdrop(b)))
        }
      }
    }
    shown.forEach { turn ->
      item(key = "turno-${turn.index}") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidUserBubble(text = turn.question)
          FluidAssistantBubble(text = turn.answer, contentKey = "f9p3-${turn.index}")
          turn.notices.mapNotNull { id -> notices.firstOrNull { it.id == id } }.forEach { notice ->
            FluidMemoryNoticeCard(
              notice = notice,
              onUndo = { scope.launch { book.undo(notice.id) } },
              onOpen = onOpenPage,
              onAnswer = { accept -> scope.launch { book.answer(notice.id, accept) } },
              modifier = Modifier.testTag(F9p3Tags.card(turn.index)),
            )
          }
          FluidSectionFootnote(text = turn.note)
        }
      }
    }
    val toolCard = toolNotice?.let { id -> notices.firstOrNull { it.id == id } }
    if (appLine != null || toolCard != null) {
      item(key = "dalle-app") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          appLine?.let { FluidSectionFootnote(text = it) }
          if (toolCard != null) {
            FluidMemoryNoticeCard(
              notice = toolCard,
              onUndo = { scope.launch { book.undo(toolCard.id) } },
              onOpen = onOpenPage,
              onAnswer = { accept -> scope.launch { book.answer(toolCard.id, accept) } },
              modifier = Modifier.testTag(F9p3Tags.ToolCard),
            )
          }
        }
      }
    }
    item(key = "comandi") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FluidSectionHeader(
          title = if (next != null) "Il prossimo messaggio" else "Fine del copione",
          detail = next?.let { "«${it.question}»" } ?: "Ricomincia, o apri la pagina dei ricordi.",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidButton(
            text = "Scrivi",
            enabled = next != null && !busy,
            onClick = {
              // Il passo si legge adesso, dallo stato: non quello della composizione in cui e' nato il tocco.
              val index = shown.size
              val step = F9p3Script.getOrNull(index)
              if (step == null || busy) return@FluidButton
              busy = true
              scope.launch {
                val at = System.currentTimeMillis()
                val turn = AiMemoryTurn(conversationId = 1L, userText = step.question, previousAnswer = shown.lastOrNull()?.answer,
                  atMillis = at, temporary = temporary)
                val after = book.afterTurn(turn, extractor)
                val ex = after.extraction
                val tokens = ex.usage?.totalTokens ?: ex.estimatedPromptTokens
                val asked = after.notices.count { it.kind == AiMemoryNotice.KindAsk }
                val note = f9p3Note(ex.skipped, tokens, after.notices.size - asked, ex.discarded, asked)
                Log.i(Tag, "turno $index: ${ex.skipped ?: "chiamata"} gettoni=$tokens ricordi=${after.notices.size - asked} domande=$asked scartati=${ex.discarded} ms=${ex.millis}")
                // Il blocco del prompt prima della risposta, come in una chat vera: quando il turno compare, il lavoro della
                // memoria per lui e' finito (niente resta in volo se la scena si chiude subito dopo).
                val nextPrompt = book.prompt(step.question)
                shown = shown + F9p3Shown(index, step.question, step.answer, after.notices.map { it.id }, note)
                prompt = nextPrompt
                busy = false
                // Come una chat: il turno nuovo sale in vista (l'elemento 0 sono i fondi). Lo muove l'app, ma e' uno
                // scorrimento di pagina, non un contenuto che cambia identita': niente sfocatura; fermo con «Riduci movimento».
                if (reduced) listState.scrollToItem(1 + index) else listState.animateScrollToItem(1 + index)
              }
            },
            modifier = Modifier.testTag(F9p3Tags.Send),
          )
          FluidButton(
            text = "ClasseViva propone la verifica",
            style = FluidButtonStyle.Tinted,
            enabled = !busy,
            onClick = {
              scope.launch {
                // Col formato del ponte, come arriverebbe dal provider dell'assistenza di PampAI (F9.1): qui nello stesso
                // processo, con chi chiama gia' «verificato».
                val friday = LocalDate.now(F9p3Zone).let { d -> d.plusDays(((5 - d.dayOfWeek.value + 7) % 7).toLong().coerceAtLeast(1)) }
                val proposal = AiMemoryProposal(
                  text = "ha la verifica di matematica venerdì ${friday.dayOfMonth}",
                  key = "scuola.verifica.matematica",
                  topics = listOf("verifica", "matematica", "studio"),
                  expiresAtMillis = friday.plusDays(1).atStartOfDay(F9p3Zone).toInstant().toEpochMilli(),
                )
                val caller = AiMemoryCaller("dev.antigravity.classevivaexpressive", "ClasseViva")
                val json = inbox.handle(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(proposal), caller)
                val result = AiMemoryProposalResult.fromJson(json)
                appLine = "Dalle app · ClasseViva ha proposto un ricordo: ${result.outcome}."
                Log.i(Tag, "classeviva: ${result.outcome}")
                val notice = result.notice(AiMemorySource.app(caller.packageName, caller.label), System.currentTimeMillis())
                if (notice != null && host != null) {
                  launch {
                    // Come la mostrerebbe ClasseViva: la domanda (o, con «Ricorda da solo», Annulla) torna col ponte.
                    host.show(FluidMemoryNotices.notification(
                      notice,
                      onUndo = {
                        scope.launch {
                          inbox.handle(AiMemoryProtocol.MethodUndo, AiMemoryProtocol.undoRequest(notice.id), caller)
                          appLine = "Dalle app · ClasseViva: annullato."
                        }
                      },
                      onAnswer = { accept ->
                        scope.launch {
                          val answer = inbox.handle(AiMemoryProtocol.MethodAnswer, AiMemoryProtocol.answerRequest(notice.id, accept), caller)
                          appLine = "Dalle app · ClasseViva, la risposta: ${AiMemoryProposalResult.fromJson(answer).outcome}."
                        }
                      },
                    ))
                  }
                }
              }
            },
            modifier = Modifier.testTag(F9p3Tags.Propose),
          )
          FluidButton(
            text = "Pampa Notes propone",
            style = FluidButtonStyle.Tinted,
            enabled = !busy,
            onClick = {
              scope.launch {
                // Pampa Notes non e' nella tabella: il ponte rifiuta, qualunque cosa proponga.
                val caller = AiMemoryCaller("dev.pampa.pampanotes", "Pampa Notes")
                val proposal = AiMemoryProposal("studia chimica con gli appunti di Pampa Notes", "studio.chimica", listOf("chimica", "studio"))
                val json = inbox.handle(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(proposal), caller)
                val outcome = AiMemoryProposalResult.fromJson(json).outcome
                appLine = "Dalle app · Pampa Notes non può proporre ricordi da sola: $outcome."
                Log.i(Tag, "notes: $outcome")
              }
            },
            modifier = Modifier.testTag(F9p3Tags.Notes),
          )
          FluidButton(
            text = "Il modello ricorda coi dati di Notes",
            style = FluidButtonStyle.Tinted,
            enabled = !busy,
            onClick = {
              scope.launch {
                // Il modello della chat ha letto gli appunti di Notes e chiama lo strumento della memoria: e' l'unica
                // strada per le app fuori dalla tabella. Non l'ha chiesto l'utente, quindi di serie chiede.
                val outcome = book.remember(
                  AiMemoryProposal("studia chimica con gli appunti di Pampa Notes", "studio.chimica", listOf("chimica", "studio", "appunti")),
                  AiMemorySource.tool(1L, "dev.pampa.pampanotes", "Pampa Notes"),
                )
                toolNotice = outcome.notice?.id
                appLine = "Lo strumento della memoria, coi dati di Pampa Notes: ${outcome.result}."
                Log.i(Tag, "strumento: ${outcome.result}")
              }
            },
            modifier = Modifier.testTag(F9p3Tags.Tool),
          )
          FluidChip(
            label = "Ricorda da solo",
            selected = autoSave,
            onClick = { scope.launch { book.setAutoSave(!autoSave) } },
            modifier = Modifier.testTag(F9p3Tags.AutoSave),
          )
          FluidChip(
            label = "Chat temporanea",
            selected = temporary,
            onClick = { temporary = !temporary },
            modifier = Modifier.testTag(F9p3Tags.Temporary),
          )
          FluidButton(
            text = "Cosa ricordo di te (${memories.size})",
            style = FluidButtonStyle.Plain,
            onClick = onOpenPage,
            modifier = Modifier.testTag(F9p3Tags.OpenPage),
          )
          FluidButton(
            text = "Ricomincia",
            style = FluidButtonStyle.Plain,
            enabled = !busy,
            onClick = {
              scope.launch {
                book.forgetAll()
                book.setAutoSave(false)
                shown = emptyList()
                prompt = AiMemoryPrompt.Empty
                appLine = null
                toolNotice = null
                temporary = false
              }
            },
            modifier = Modifier.testTag(F9p3Tags.Restart),
          )
        }
      }
    }
    item(key = "prompt") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(F9p3Tags.Prompt)) {
        FluidSectionHeader(
          title = "Nel prompt dell'ultima domanda",
          detail = if (prompt.isEmpty) "Nessun ricordo c'entra: zero gettoni (oggi PampAI ne mette sempre fino a ~430)."
            else "${prompt.memories.size} ricordi, ${prompt.tokens} gettoni su 250 (oggi PampAI ne mette sempre fino a ~430).",
        )
        if (!prompt.isEmpty) {
          FluidListGroup(glass = true) {
            prompt.memories.forEachIndexed { i, m ->
              if (i > 0) FluidListDivider()
              // La riga com'e' nel prompt, senza il fatto: la data, l'app, la scadenza.
              val tail = AiMemoryPrompt.line(m, zone = F9p3Zone).removePrefix("- ${m.text} (").removeSuffix(")")
              FluidListRow(title = m.text, subtitle = tail, disclosure = false)
            }
          }
        }
      }
    }
  }
}

// ================================================================================================================
// La pagina «Cosa ricordo di te»
// ================================================================================================================

@Composable
private fun F9p3PageScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val open = LocalF9p3Books.current
  val book by produceState<AiMemoryBook?>(null) {
    val b = open(context, "f9p3-pagina")
    b.forgetAll()
    f9p3Seed(b)
    value = b
  }
  val b = book
  if (b == null) {
    FluidScreen(title = "Cosa ricordo di te", subtitle = "F9.3b · apro la memoria…", onBack = onBack) {}
    return
  }
  F9p3MemoryPage(b, onBack)
}

/** La pagina sopra una memoria: le azioni vanno alla memoria, che le scrive cifrate. */
@Composable
internal fun F9p3MemoryPage(book: AiMemoryBook, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val memories by book.memories.collectAsState()
  val blocked by book.blockedApps.collectAsState()
  val pending by book.pending.collectAsState()
  val autoSave by book.autoSave.collectAsState()
  val exclusions by book.exclusions.collectAsState()
  val actions = remember(book) {
    FluidMemoryActions(
      add = { t -> scope.launch { book.remember(AiMemoryProposal(t), AiMemorySource.Manual) } },
      correct = { id, t -> scope.launch { book.correct(id, t) } },
      setPinned = { id, p -> scope.launch { book.setPinned(id, p) } },
      forget = { id -> scope.launch { book.forget(id) } },
      forgetAll = { scope.launch { book.forgetAll() } },
      forgetFromApp = { app -> scope.launch { book.forgetFromApp(app) } },
      setAppBlocked = { app, b -> scope.launch { book.setAppBlocked(app, b) } },
      answer = { id, accept -> scope.launch { book.answer(id, accept) } },
      setAutoSave = { on -> scope.launch { book.setAutoSave(on) } },
      forgetAndExclude = { id -> scope.launch { book.forget(id, exclude = true) } },
      include = { e -> scope.launch { book.include(e) } },
    )
  }
  FluidMemoryPage(
    memories = memories,
    actions = actions,
    onBack = onBack,
    blockedApps = blocked,
    zone = F9p3Zone,
    secretLike = { AiMemoryPolicy.looksSecret(it.text) },
    pending = pending,
    autoSave = autoSave,
    exclusions = exclusions,
    appLabel = { F9p3AppLabels[it] },
  )
}

/** I nomi delle app finte, per le esclusioni della pagina. */
internal val F9p3AppLabels = mapOf(
  "dev.antigravity.classevivaexpressive" to "ClasseViva",
  "dev.pampa.mail" to "Pampa Mail",
  "dev.pampa.mycloudgallery" to "MyCloud Gallery",
  "dev.pampa.pampanotes" to "Pampa Notes",
)

/**
 * I ricordi della pagina di prova: detti in chat (uno aggiornato), scritti a mano, proposti da ClasseViva (uno che
 * scade) e da Pampa Mail, la memoria di prima (con un fissato e uno che sembra un codice), un fatto dedotto dalle foto
 * di MyCloud Gallery (il profilo della ricerca fuso nella memoria), una domanda di Pampa Mail che aspetta e
 * un'esclusione. Quelli gia' in memoria entrano come se l'utente avesse detto Ricorda ([AiMemoryBook.remember] con
 * `confirmed`).
 */
internal suspend fun f9p3Seed(book: AiMemoryBook) {
  val now = System.currentTimeMillis()
  val day = 86_400_000L
  val you = AiMemorySource.chat(1L)
  book.importLegacy(
    listOf(
      AiMemoryLegacyRow(1, "Sono allergica alle noci", now - 90 * day, 3L, pinned = true),
      AiMemoryLegacyRow(2, "La mia fermata è Dalmazia, prendo il 23", now - 60 * day, null, pinned = false),
      AiMemoryLegacyRow(3, "il codice del portone è 4521 9087 3321", now - 40 * day, null, pinned = false),
    ),
  )
  book.remember(AiMemoryProposal("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola", "classe"), observedAtMillis = now - 30 * day), you, confirmed = true)
  book.remember(AiMemoryProposal("è in 5F al liceo Galilei", "scuola.classe", listOf("scuola", "classe"), observedAtMillis = now - 2 * day), you, confirmed = true)
  book.remember(AiMemoryProposal("è vegetariana", "cibo.dieta", listOf("cibo", "cena")), you, confirmed = true)
  book.remember(AiMemoryProposal("preferisce risposte brevi", "stile.risposte", listOf("risposte"), always = true), AiMemorySource.Manual)
  val cv = AiMemorySource.app("dev.antigravity.classevivaexpressive", "ClasseViva")
  val friday = LocalDate.now(F9p3Zone).let { d -> d.plusDays(((5 - d.dayOfWeek.value + 7) % 7).toLong().coerceAtLeast(1)) }
  book.remember(AiMemoryProposal("ha la verifica di matematica venerdì ${friday.dayOfMonth}", "scuola.verifica.matematica",
    listOf("verifica", "matematica"), expiresAtMillis = friday.plusDays(1).atStartOfDay(F9p3Zone).toInstant().toEpochMilli()), cv, confirmed = true)
  book.remember(AiMemoryProposal("la prof di inglese è la Bianchi", "scuola.docenti", listOf("inglese", "professori")), cv, confirmed = true)
  val mail = AiMemorySource.app("dev.pampa.mail", "Pampa Mail")
  book.remember(AiMemoryProposal("firma le mail con «Giulia»", "scrittura.firma", listOf("mail", "firma")), mail, confirmed = true)
  // Il profilo della ricerca, fuso nella memoria: un fatto dedotto dalle foto, coi suoi limiti e la sua fonte.
  val gallery = "dev.pampa.mycloudgallery"
  book.importProfile(
    listOf(
      AiMemoryProfileRow(
        id = "f9p3-mare", field = "luoghi.vacanze", value = "passa le vacanze d'estate al mare, a Cecina", kind = "INFERENCE",
        status = "ACTIVE", limitations = "solo dalle foto degli ultimi due anni", derivedBy = "finto/modello-piccolo",
        evidence = listOf(AiMemoryEvidence("f9p3-foto", grant = "f9p3-galleria", app = gallery, field = "foto.luoghi", observedAtMillis = now - 100 * day)),
        app = gallery, appLabel = "MyCloud Gallery",
      ),
    ),
    exclusions = listOf(AiMemoryExclusion(key = "salute")),
  ) { "In vacanza" }
  // Una domanda che aspetta: Pampa Mail propone, e di serie la memoria chiede.
  book.remember(AiMemoryProposal("ai professori scrive dando del lei", "scrittura.registro", listOf("mail", "professori")), mail)
  // La pagina parte senza avvisi aperti: quelli sono della conversazione (le domande restano «Da confermare»).
  book.notices.value.forEach { book.dismissNotice(it.id) }
}

// ================================================================================================================
// Le misure
// ================================================================================================================

/** Il corpus del filtro locale: 20 messaggi che dicono qualcosa di se', 60 che no (le stesse frasi di `AiMemoryExtractorTest`). */
internal val F9p3WithFacts = listOf(
  "sono in 4F al Galilei, che compiti ho?", "sono vegetariana, consigliami una cena", "mi chiamo Giulia",
  "abito a Firenze, che tempo fa?", "venerdì ho la verifica di matematica", "ricordati che non mangio la carne",
  "il mio autobus è il 23", "la mia fermata è Santa Maria Novella", "da lunedì sono in 5F", "dammi del tu",
  "preferisco risposte brevi", "sono allergico alle noci", "lavoro in un bar il sabato", "gioco a pallavolo il martedì",
  "mia sorella si chiama Marta", "ho un cane che si chiama Pepe", "studio al conservatorio", "tifo Fiorentina",
  "vado a scuola in bici", "remember that I'm lactose intolerant",
)

internal val F9p3WithoutFacts = listOf(
  "che tempo fa domani?", "metti una sveglia alle 7", "quando passa il 23?", "che voti ho preso in storia?",
  "quanto fa il 15% di 340?", "cosa c'è scritto sullo schermo?", "ricordami alle 18 di chiamare la nonna", "accendi la torcia",
  "riassumi questa pagina", "traduci in inglese: buongiorno", "chi ha vinto la partita ieri?", "dimmi una barzelletta",
  "che ore sono a Tokyo?", "converti 5 miglia in km", "apri Fluidify", "metti un po' di musica", "alza il volume",
  "quanti giorni mancano a Natale?", "che film danno stasera?", "come si fa la carbonara?", "spiegami la fotosintesi",
  "chi era Garibaldi?", "scrivi una mail al prof per giustificare l'assenza", "leggi le notifiche", "che impegni ho domani?",
  "c'è sciopero dei treni venerdì?", "quanto costa un biglietto per Roma?", "fammi un riassunto della lezione di filosofia",
  "cos'è un integrale?", "trova un ristorante vicino", "imposta un timer di 10 minuti", "che giorno è oggi?",
  "qual è la capitale dell'Australia?", "aiutami con l'esercizio 4 di pagina 120", "traduci questo testo",
  "che significa serendipity?", "manda un messaggio a Luca: arrivo tra 5 minuti", "quanto è alto l'Everest?",
  "come sta andando la borsa?", "consigliami un libro", "dammi tre idee per un tema sull'ambiente", "spegni il wifi",
  "a che ora tramonta il sole?", "qual è il prossimo treno per Pisa?", "quanto pesa un litro d'olio?",
  "fai il conto della spesa: 3,20 + 4,50 + 12", "cosa vuol dire questa parola?", "metti in pausa", "salta la canzone",
  "che canzone è questa?", "c'è traffico in tangenziale?", "prenota un tavolo per due", "chi ha scritto i Promessi Sposi?",
  "come si coniuga il verbo essere in francese?", "fammi un quiz di storia", "ripetimi l'ultima risposta", "grazie mille",
  "ok perfetto", "buongiorno!", "puoi essere più breve?",
)

/** Una misura: il nome e il valore, come riga e come riga del log. */
internal class F9p3Measure(val name: String, val value: String)

/** Le misure della memoria, calcolate dove gira la galleria (sul telefono: la cassaforte e il Keystore veri). */
internal suspend fun f9p3Measures(context: Context?): List<F9p3Measure> = withContext(Dispatchers.Default) {
  val out = ArrayList<F9p3Measure>()
  val calls = F9p3WithFacts.count { AiMemorySignals.mayCarryFacts(it) } + F9p3WithoutFacts.count { AiMemorySignals.mayCarryFacts(it) }
  out += F9p3Measure("Chiamate al modello piccolo", "$calls su 80 messaggi (${calls * 100 / 80}%): le altre costano zero")
  val extractor = AiMemoryExtractor(F9p3FakeModel, "modello-piccolo-finto", zone = F9p3Zone)
  val tokens = F9p3WithFacts.map { HistoryCompactor.estimateTokens(extractor.request(AiMemoryTurn(1L, it, atMillis = 0L)).messages) }
  out += F9p3Measure("Una chiamata dell'estrattore", "~${tokens.average().toInt()} gettoni di richiesta (al più ${tokens.max()}), risposta al più 200")
  // 300 ricordi in una memoria in memoria: 12 della persona, il resto di riempimento.
  val book = AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig(zone = F9p3Zone))
  val started = SystemClock.elapsedRealtime()
  val now = System.currentTimeMillis()
  repeat(288) { i ->
    book.remember(AiMemoryProposal("ha visto il film numero $i al cinema Odeon", "film.visti$i", listOf("film", "cinema"), observedAtMillis = now - (300 - i) * 3_600_000L), AiMemorySource.chat(i.toLong()), confirmed = true)
  }
  f9p3Seed(book)
  out += F9p3Measure("Riempire la memoria", "${book.memories.value.size} ricordi in ${SystemClock.elapsedRealtime() - started} ms (con l'indice)")
  val questions = listOf("che compiti ho per domani?", "consigliami una ricetta per cena", "quanto fa 15 per 340?")
  val t0 = SystemClock.elapsedRealtime()
  val prompts = questions.map { book.prompt(it) }
  val perQuestion = (SystemClock.elapsedRealtime() - t0) / questions.size
  out += F9p3Measure("Il blocco nel prompt", questions.zip(prompts).joinToString(" · ") { (q, p) -> "«${q.take(18)}…» ${p.tokens} gettoni" })
  out += F9p3Measure("Il blocco di oggi", "~${legacyTokens(book.memories.value)} gettoni in ogni domanda, che c'entrino o no")
  out += F9p3Measure("Una ricerca per pertinenza", "$perQuestion ms a domanda su ${book.memories.value.size} ricordi")
  book.close()
  if (context != null) {
    // La cassaforte vera (il Keystore del telefono): una sezione sua, buttata alla fine anche se qualcosa va storto,
    // cosi' sul telefono non resta niente (ne' il file, ne' la scheda, ne' le chiavi).
    val section = withContext(Dispatchers.IO) { FluidVault.get(context).section("f9p3-misure", VaultAccess.Device()) }
    try {
      val store = AiMemoryStore.vault(section, "misure.v1")
      val vb = AiMemoryBook.open(store, AiMemoryConfig(zone = F9p3Zone))
      val w0 = SystemClock.elapsedRealtime()
      repeat(60) { i -> vb.remember(AiMemoryProposal("ricordo di prova numero $i", "prova.$i"), AiMemorySource.Manual) }
      val write = (SystemClock.elapsedRealtime() - w0) / 60
      vb.close()
      val o0 = SystemClock.elapsedRealtime()
      val reopened = AiMemoryBook.open(store, AiMemoryConfig(zone = F9p3Zone))
      val openMs = SystemClock.elapsedRealtime() - o0
      val size = section.file("misure.v1").file.length()
      val leggibile = String(section.file("misure.v1").file.readBytes(), Charsets.ISO_8859_1).contains("ricordo di prova")
      reopened.close()
      out += F9p3Measure("La cassaforte", "una scrittura $write ms, riaprire 60 ricordi $openMs ms, file cifrato ${size / 1024} KiB" +
        if (leggibile) " (IN CHIARO!)" else ", nessuna parola leggibile")
    } finally {
      runCatching { section.reset() }
      out += F9p3Measure("Dopo la misura", "sezione buttata: niente resta sul telefono")
    }
  }
  out.forEach { Log.i(Tag, "misura ${it.name}: ${it.value}") }
  out
}

/** Il blocco di oggi di PampAI (`MemoryRepository.promptBlock`): i fissati, poi i piu' recenti, fino a 40 righe e 1 500 caratteri. */
internal fun legacyTokens(memories: List<AiMemory>): Int {
  val ordered = memories.sortedWith(compareByDescending<AiMemory> { it.pinned }.thenByDescending { it.createdAtMillis })
  var chars = 0
  var lines = 0
  for (m in ordered) {
    val line = "- ${m.text}"
    if (lines >= 40 || chars + line.length > 1_500) break
    lines++
    chars += line.length + 1
  }
  return (chars / HistoryCompactor.CHARS_PER_TOKEN).toInt()
}

@Composable
private fun F9p3MeasureScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val measures by produceState<List<F9p3Measure>?>(null) { value = runCatching { f9p3Measures(context) }.getOrElse { e ->
    listOf(F9p3Measure("Errore", e.message ?: e::class.java.simpleName))
  } }
  FluidScreen(title = "Le misure della memoria", subtitle = "F9.3 · sul telefono, col modello finto", onBack = onBack,
    modifier = Modifier.testTag(F9p3Tags.Measures)) {
    item(key = "misure") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Costo e peso", detail = if (measures == null) "Misuro…" else "Le stesse righe vanno nel log F9p3.")
        val list = measures
        if (list != null) {
          FluidListGroup(glass = true) {
            list.forEachIndexed { i, m ->
              if (i > 0) FluidListDivider()
              FluidListRow(title = m.name, subtitle = m.value, disclosure = false)
            }
          }
        }
      }
    }
  }
}

/** Per le prove della galleria: la pagina con la memoria data, senza la cassaforte. */
@Composable
internal fun F9p3WithBooks(open: suspend (Context, String) -> AiMemoryBook, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalF9p3Books provides open, content = content)
}
