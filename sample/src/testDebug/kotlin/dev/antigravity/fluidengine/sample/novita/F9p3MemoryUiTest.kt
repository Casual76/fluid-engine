package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.ai.memory.AiMemoryBook
import dev.antigravity.fluidengine.ai.memory.AiMemoryCaller
import dev.antigravity.fluidengine.ai.memory.AiMemoryConfig
import dev.antigravity.fluidengine.ai.memory.AiMemoryExclusion
import dev.antigravity.fluidengine.ai.memory.AiMemoryInbox
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult
import dev.antigravity.fluidengine.ai.memory.AiMemoryProtocol
import dev.antigravity.fluidengine.ai.memory.AiMemorySource
import dev.antigravity.fluidengine.ai.memory.AiMemoryStore
import dev.antigravity.fluidengine.aiui.memory.FluidMemoryPageTags
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F9.3 e F9.3b · La memoria nella galleria, con l'interfaccia vera di engine-ai-ui e la memoria vera di engine-ai-memory
 * (in un deposito in memoria al posto della cassaforte, che ha le sue prove): la conversazione che di serie chiede
 * «Vuoi che lo ricordi?», Ricorda e No, «Ricorda da solo», la card e Annulla, l'aggiornamento che dice cosa c'era prima, il
 * «ricordati» che entra subito, il segreto che non entra, la chat temporanea, ClasseViva che propone col ponte e Pampa
 * Notes che non puo', lo strumento del modello, la pagina che conferma, corregge, revoca ed esclude, TalkBack e «Riduci
 * movimento».
 *
 * I tocchi passano dall'azione OnClick dei nodi, dopo aver portato in vista l'elemento della lista per chiave: con
 * l'orologio fermo `performScrollTo` scorre con un'animazione che non finisce mai e riempie la memoria della JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F9p3MemoryUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val books = ArrayList<AiMemoryBook>()
  private val inMemory: suspend (Context, String) -> AiMemoryBook = { _, _ -> AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig()).also { books += it } }

  @Before fun start() {
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = books.forEach { it.close() }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  /**
   * Aspetta [condition], al piu' 120 giri (4 s di orologio di Compose). Corta apposta: col vetro vero ogni fotogramma
   * costa, e un'attesa lunga che non arriva mai riempie la memoria della JVM prima di fallire.
   */
  private fun waitFor(what: String, condition: () -> Boolean) {
    repeat(120) {
      if (condition()) return
      advance(32)
      Thread.sleep(3)
    }
    throw AssertionError("non è successo: $what")
  }

  /** Porta in vista l'elemento [key] della lista della pagina (gli elementi fuori vista non esistono). Falso se non c'e'. */
  private fun scrollToKey(key: String): Boolean = runCatching {
    compose.onNode(hasScrollToKeyAction()).performScrollToKey(key)
    advance(64)
  }.isSuccess

  /** Il tocco di un nodo col tag [tag]: la sua azione OnClick, come la fa TalkBack. */
  private fun click(tag: String) {
    compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    advance(64)
  }

  private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  private fun has(text: String, substring: Boolean = true) =
    compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

  private fun label(tag: String): String = node(tag).config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")

  /** Le azioni per TalkBack del nodo [tag] (o dei suoi figli, se le tiene uno di loro). */
  private fun actionsOf(tag: String): List<androidx.compose.ui.semantics.CustomAccessibilityAction> {
    fun walk(n: SemanticsNode): List<androidx.compose.ui.semantics.CustomAccessibilityAction> =
      n.config.getOrNull(SemanticsActions.CustomActions)?.takeIf { it.isNotEmpty() }
        ?: n.children.firstNotNullOfOrNull { c -> walk(c).takeIf { it.isNotEmpty() } }.orEmpty()
    return walk(node(tag)).ifEmpty { walk(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()) }
  }

  /** Fa l'azione [label] del nodo [tag], come la fa TalkBack. */
  private fun act(tag: String, label: String) {
    val action = actionsOf(tag).first { it.label == label }
    compose.runOnIdle { action.action() }
    advance(64)
  }

  private fun show(scene: String, reduced: Boolean = false) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default,
          LocalFluidGlassModalHostState provides modals,
        ) {
          F9p3WithBooks(inMemory) {
            Box(Modifier.fillMaxSize()) {
              NewElementsPageAt(onBack = {}, initial = scene)
              FluidGlassModalHost(modals, backdrop = null)
            }
          }
        }
      }
    }
    advance(200)
  }

  /** Un tocco su «Scrivi», poi aspetta che il turno [index] compaia (la scena lo porta in vista da sola). */
  private fun send(index: Int) {
    // «Scrivi» si riaccende quando il turno di prima ha finito anche il prompt (in sottofondo, a tempo vero): prima un
    // tocco non fa niente, ed e' giusto cosi'.
    waitFor("Scrivi di nuovo pronto") {
      scrollToKey("comandi") && node(F9p3Tags.Send).config.getOrNull(SemanticsProperties.Disabled) == null
    }
    click(F9p3Tags.Send)
    waitFor("il turno $index") { has(F9p3Script[index].answer) || (scrollToKey("turno-$index") && has(F9p3Script[index].answer)) }
    advance(400)
  }

  /** La card del turno [index], portata in vista. */
  private fun card(index: Int): SemanticsNode {
    scrollToKey("turno-$index")
    return node(F9p3Tags.card(index))
  }

  @Test fun `le scene sono nell'indice`() {
    assertTrue(NewElementSections.contains(F9p3Section))
    assertEquals(listOf("ricordi-chat", "ricordi-pagina", "ricordi-misure"), F9p3Section.scenes.map { it.id })
    assertEquals(3, F9p3TryItems.size)
  }

  @Test fun `di serie chiede, Ricorda e No rispondono, l'aggiornamento dice cosa c'era prima, Annulla toglie`() {
    show("ricordi-chat")
    waitFor("la memoria aperta") { exists(F9p3Tags.Send) }
    val book = books.single()
    send(0)
    waitFor("la domanda del primo turno") { exists(F9p3Tags.card(0)) }
    assertEquals("Vuoi che lo ricordi? è in 4F al liceo Galilei", label(F9p3Tags.card(0)))
    assertEquals(listOf("Ricorda", "No"), actionsOf(F9p3Tags.card(0)).map { it.label })
    assertTrue("prima del si' non c'e' niente", book.memories.value.isEmpty())
    assertTrue(has("prima di ricordare ti chiedo"))
    act(F9p3Tags.card(0), "Ricorda")
    waitFor("il si'") { book.memories.value.any { it.text == "è in 4F al liceo Galilei" } }
    advance(300)
    assertEquals("Ho ricordato: è in 4F al liceo Galilei", label(F9p3Tags.card(0)))
    assertEquals(listOf("Annulla"), actionsOf(F9p3Tags.card(0)).map { it.label })
    // No: il fatto non entra, e la card lo dice.
    send(1)
    waitFor("la domanda del secondo turno") { exists(F9p3Tags.card(1)) }
    act(F9p3Tags.card(1), "No")
    waitFor("il no") { label(F9p3Tags.card(1)).startsWith("Va bene, non lo ricordo") }
    assertTrue(book.memories.value.none { it.text == "è vegetariana" } && book.pending.value.isEmpty())
    // Il meteo: niente di se', nessuna chiamata, nessuna card.
    send(2)
    assertFalse(exists(F9p3Tags.card(2)))
    assertTrue(has("niente di te in questo messaggio, nessuna chiamata"))
    // L'aggiornamento chiede anche lui, e dice cosa sostituirebbe; col si' vince il piu' recente.
    send(3)
    waitFor("la domanda dell'aggiornamento") { exists(F9p3Tags.card(3)) }
    assertEquals("Vuoi che lo ricordi? è in 5F al liceo Galilei. al posto di: è in 4F al liceo Galilei", label(F9p3Tags.card(3)))
    act(F9p3Tags.card(3), "Ricorda")
    waitFor("l'aggiornamento") { book.memories.value.any { it.text == "è in 5F al liceo Galilei" } }
    advance(300)
    assertEquals("Ho aggiornato: è in 5F al liceo Galilei. prima: è in 4F al liceo Galilei", label(F9p3Tags.card(3)))
    // La card di prima non offre piu' Annulla: il fatto e' cambiato dopo di lei.
    assertTrue(card(0).config.getOrNull(SemanticsActions.CustomActions).orEmpty().isEmpty())
    // «Ricordati che…»: l'ha chiesto l'utente, entra subito.
    send(4)
    waitFor("il ricordati") { exists(F9p3Tags.card(4)) }
    assertEquals("Ho ricordato: si chiama Giulia", label(F9p3Tags.card(4)))
    assertEquals(setOf("è in 5F al liceo Galilei", "si chiama Giulia"), book.memories.value.map { it.text }.toSet())
    // Il segreto non entra, nemmeno chiesto, e lo dice.
    send(5)
    assertFalse(exists(F9p3Tags.card(5)))
    assertTrue(has("scartato 1 (sembra un segreto)"))
    assertEquals(2, book.memories.value.size)
    // Annulla sull'aggiornamento: torna la 4F, e la card lo dice.
    act(F9p3Tags.card(3), "Annulla")
    waitFor("l'annulla") { book.memories.value.any { it.text == "è in 4F al liceo Galilei" } }
    advance(400)
    assertTrue(label(F9p3Tags.card(3)).startsWith("Non lo ricordo più"))
    // Nel prompt dell'ultima domanda: il blocco nuovo, coi gettoni contati.
    assertTrue(scrollToKey("prompt") && exists(F9p3Tags.Prompt))
  }

  @Test fun `Ricorda da solo - salva senza chiedere, con Annulla, e resta acceso`() {
    show("ricordi-chat")
    waitFor("la memoria aperta") { exists(F9p3Tags.Send) }
    val book = books.single()
    scrollToKey("comandi")
    click(F9p3Tags.AutoSave)
    waitFor("l'interruttore") { book.autoSave.value }
    send(0)
    waitFor("la card") { exists(F9p3Tags.card(0)) }
    assertEquals("Ho ricordato: è in 4F al liceo Galilei", label(F9p3Tags.card(0)))
    assertEquals(listOf("Annulla"), actionsOf(F9p3Tags.card(0)).map { it.label })
    assertEquals(1, book.memories.value.size)
    assertTrue(book.pending.value.isEmpty())
  }

  @Test fun `TalkBack - la card e' un nodo solo che si annuncia, e Ricorda e Annulla sono tasti`() {
    show("ricordi-chat")
    waitFor("la memoria aperta") { exists(F9p3Tags.Send) }
    send(0)
    waitFor("la card") { exists(F9p3Tags.card(0)) }
    val card = node(F9p3Tags.card(0))
    assertEquals(LiveRegionMode.Polite, card.config.getOrNull(SemanticsProperties.LiveRegion))
    assertEquals(listOf("Ricorda", "No"), card.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label })
    // I tasti, con la loro parola, sono nodi che si toccano (l'azione OnClick, come la fa TalkBack).
    compose.onNode(hasText("Ricorda") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    waitFor("il si' col tasto") { books.single().memories.value.size == 1 }
    advance(400)
    compose.onNode(hasText("Annulla") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    waitFor("l'annulla col tasto") { books.single().memories.value.isEmpty() }
    advance(400)
    assertTrue(label(F9p3Tags.card(0)).startsWith("Non lo ricordo più"))
    assertTrue(node(F9p3Tags.card(0)).config.getOrNull(SemanticsActions.CustomActions).orEmpty().isEmpty())
  }

  @Test fun `la chat temporanea non lascia ricordi e non chiama il modello`() {
    show("ricordi-chat")
    waitFor("la memoria aperta") { exists(F9p3Tags.Send) }
    scrollToKey("comandi")
    click(F9p3Tags.Temporary)
    send(0)
    assertFalse(exists(F9p3Tags.card(0)))
    assertTrue(has("chat temporanea, non ricordo niente"))
    assertTrue(books.single().memories.value.isEmpty())
  }

  @Test fun `ClasseViva propone col formato del ponte, la risposta torna col ponte, e il ricordo e' suo`() {
    show("ricordi-chat")
    waitFor("la memoria aperta") { exists(F9p3Tags.Propose) }
    val book = books.single()
    scrollToKey("comandi")
    click(F9p3Tags.Propose)
    waitFor("la domanda di ClasseViva") { book.pending.value.isNotEmpty() }
    assertTrue(book.memories.value.isEmpty())
    advance(100)
    assertTrue(scrollToKey("dalle-app") && has("ClasseViva ha proposto un ricordo: pending"))
    // La risposta dall'app, col ponte (come dal tasto della sua notifica).
    val caller = AiMemoryCaller("dev.antigravity.classevivaexpressive", "ClasseViva")
    val ask = book.pending.value.single()
    val json = runBlocking { AiMemoryInbox(book).handle(AiMemoryProtocol.MethodAnswer, AiMemoryProtocol.answerRequest(ask.id, true), caller) }
    assertEquals(AiMemoryProposalResult.OutcomeRemembered, AiMemoryProposalResult.fromJson(json).outcome)
    val m = book.memories.value.single()
    assertEquals(AiMemorySource.app("dev.antigravity.classevivaexpressive", "ClasseViva"), m.source)
    assertTrue(m.text.startsWith("ha la verifica di matematica venerdì"))
    assertTrue(m.expiresAtMillis != null)
  }

  @Test fun `Pampa Notes non puo' proporre da sola, ma il modello con lo strumento chiede`() {
    show("ricordi-chat")
    waitFor("la memoria aperta") { exists(F9p3Tags.Notes) }
    val book = books.single()
    scrollToKey("comandi")
    click(F9p3Tags.Notes)
    waitFor("il rifiuto") { scrollToKey("dalle-app") && has("Pampa Notes non può proporre ricordi da sola: not_allowed") }
    assertTrue(book.memories.value.isEmpty() && book.pending.value.isEmpty())
    scrollToKey("comandi")
    click(F9p3Tags.Tool)
    waitFor("la domanda dello strumento") { book.pending.value.isNotEmpty() }
    assertEquals(AiMemorySource.KindTool, book.pending.value.single().source.kind)
    waitFor("la card dello strumento") { scrollToKey("dalle-app") && exists(F9p3Tags.ToolCard) }
    assertEquals("Vuoi che lo ricordi? studia chimica con gli appunti di Pampa Notes", label(F9p3Tags.ToolCard))
  }

  /** L'altezza della card [index] adesso. */
  private fun height(index: Int) = node(F9p3Tags.card(index)).boundsInRoot.height

  @Test fun `Riduci movimento - la card cambia di colpo, ferma e leggibile`() {
    show("ricordi-chat", reduced = true)
    waitFor("la memoria aperta") { exists(F9p3Tags.Send) }
    // Col salvataggio automatico: la card nasce «Ho ricordato», e l'aggiornamento ha la riga «prima: …».
    scrollToKey("comandi")
    click(F9p3Tags.AutoSave)
    waitFor("l'interruttore") { books.single().autoSave.value }
    send(0)
    send(1)
    send(2)
    send(3)
    waitFor("la card dell'aggiornamento") { exists(F9p3Tags.card(3)) }
    advance(600)
    val before = height(3)
    val undo = card(3).config.getOrNull(SemanticsActions.CustomActions).orEmpty().single { it.label == "Annulla" }
    compose.runOnIdle { undo.action() }
    waitFor("l'annulla") { label(F9p3Tags.card(3)).startsWith("Non lo ricordo più") }
    advance(16)
    val right = height(3)
    advance(800)
    val settled = height(3)
    // Senza la riga «prima: …» e senza il tasto la card e' piu' bassa, e lo e' gia' al primo fotogramma.
    assertTrue("la card non cambia altezza ($before → $settled)", settled < before)
    assertEquals(settled, right, 0.5f)
    assertEquals("Non lo ricordo più: è in 5F al liceo Galilei", label(F9p3Tags.card(3)))
  }

  @Test fun `la pagina - i gruppi, la domanda da confermare, il fatto dedotto, le esclusioni, dimentica e blocca un'app`() {
    show("ricordi-pagina")
    waitFor("la pagina coi ricordi") { books.isNotEmpty() && books.single().memories.value.size >= 10 && exists(FluidMemoryPageTags.Page) }
    advance(300)
    val book = books.single()
    val groups = listOf(
      "gruppo-Da te" to "DA TE",
      "gruppo-dev.antigravity.classevivaexpressive" to "DA CLASSEVIVA",
      "gruppo-dev.pampa.mail" to "DA PAMPA MAIL",
      "gruppo-dev.pampa.mycloudgallery" to "DA MYCLOUD GALLERY",
      "gruppo-Dalla memoria di prima" to "DALLA MEMORIA DI PRIMA",
      "da-confermare" to "DA CONFERMARE",
      "esclusioni" to "NON IMPARO PIÙ",
    )
    for ((key, header) in groups) {
      assertTrue("manca il gruppo $header", scrollToKey(key) && has(header, substring = false))
    }
    assertTrue(scrollToKey("ricorda-da-solo") && exists(FluidMemoryPageTags.AutoSave))
    assertTrue(scrollToKey("gruppo-Dalla memoria di prima") && has("sembra un codice: non entra nelle domande"))
    assertTrue(scrollToKey("gruppo-dev.pampa.mycloudgallery") && has("dedotto: solo dalle foto degli ultimi due anni"))
    assertTrue(has("Imparato dai dati di MyCloud Gallery"))
    assertTrue(scrollToKey("gruppo-Da te") && has("prima: è in 4F al liceo Galilei"))
    // La domanda di Pampa Mail: Ricorda dalla pagina, e il ricordo entra.
    val ask = book.pending.value.single()
    scrollToKey("da-confermare")
    act(FluidMemoryPageTags.pending(ask.id), "Ricorda")
    waitFor("la domanda confermata") { book.memories.value.any { it.text == "ai professori scrive dando del lei" } && book.pending.value.isEmpty() }
    // Dimentica e non imparare piu': il ricordo va via e il suo argomento passa fra le esclusioni.
    val veg = book.memories.value.single { it.text == "è vegetariana" }
    scrollToKey("gruppo-Da te")
    act(FluidMemoryPageTags.row(veg.id), "Dimentica e non imparare più")
    waitFor("l'esclusione") { book.exclusions.value.contains(AiMemoryExclusion(key = "cibo.dieta")) && book.memories.value.none { it.id == veg.id } }
    advance(200)
    // Impara di nuovo: tolta l'esclusione «salute».
    assertTrue(scrollToKey("esclusioni") && has("L'argomento «salute»"))
    click(FluidMemoryPageTags.exclusion(0))
    waitFor("l'esclusione tolta") { AiMemoryExclusion(key = "salute") !in book.exclusions.value }
    // Dimentica quelli di ClasseViva, con la conferma.
    val cv = "dev.antigravity.classevivaexpressive"
    scrollToKey("gruppo-$cv")
    click(FluidMemoryPageTags.forgetApp(cv))
    advance(400)
    compose.onNode(hasText("Dimentica", substring = false) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    waitFor("i ricordi di ClasseViva dimenticati") { book.memories.value.none { it.source.app == cv } }
    advance(300)
    assertFalse(has("DA CLASSEVIVA", substring = false))
    // Pampa Mail non propone piu'.
    scrollToKey("gruppo-dev.pampa.mail")
    click(FluidMemoryPageTags.blockApp("dev.pampa.mail"))
    waitFor("Pampa Mail bloccata") { "dev.pampa.mail" in book.blockedApps.value }
    advance(200)
    assertTrue(has("Ricorda di nuovo da Pampa Mail"))
    runBlocking { book.forgetAll() }
    waitFor("la pagina vuota") { scrollToKey("vuota") && has("Niente per ora") }
  }

  @Test fun `le misure si calcolano anche sul computer`() = runBlocking {
    val measures = f9p3Measures(null)
    assertTrue(measures.first().value, measures.first().value.startsWith("22 su 80"))
    assertTrue(measures.any { it.name == "Il blocco di oggi" })
  }
}
