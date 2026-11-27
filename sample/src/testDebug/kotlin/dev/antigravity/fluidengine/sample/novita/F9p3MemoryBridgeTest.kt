package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistCaller
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsentState
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistExecutor
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistHost
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistHostProvider
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistPolicy
import dev.antigravity.fluidengine.ai.bridge.assist.InMemoryAiAssistConsentStore
import dev.antigravity.fluidengine.ai.memory.AiMemoryBook
import dev.antigravity.fluidengine.ai.memory.AiMemoryCaller
import dev.antigravity.fluidengine.ai.memory.AiMemoryConfig
import dev.antigravity.fluidengine.ai.memory.AiMemoryInbox
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposal
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult
import dev.antigravity.fluidengine.ai.memory.AiMemoryProtocol
import dev.antigravity.fluidengine.ai.memory.AiMemorySource
import dev.antigravity.fluidengine.ai.memory.AiMemoryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

/**
 * R3-S1b · D5 di Alessio: **i ricordi delle altre app passano dal ponte vero**, il provider dell'assistenza di F9.1, dopo
 * il suo cancello (la firma, il pacchetto detto dal sistema). Valgono il «no» di F9.1 (un'app rifiutata non propone) e il
 * blocco della memoria. Il PampAI di prova ha la tabella di serie di F9.1 e una memoria in memoria.
 *
 * Il fatto proposto si cerca con una parola intera («chimica», lettere e cifre escluse ai due lati).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class F9p3MemoryBridgeTest {

  /** Il provider dell'assistenza del PampAI di prova, con un cancello comandato dalla prova e la memoria agganciata. */
  class MemoryHost : AiAssistHostProvider() {
    var comparison: Int = PackageManager.SIGNATURE_MATCH
    var packages: Array<String>? = null

    override fun host(): AiAssistHost = assist

    override fun compareSignatures(callingUid: Int, ownUid: Int): Int = comparison

    override fun packagesForUid(uid: Int): Array<String>? = packages ?: super.packagesForUid(uid)

    // L'aggancio di R3-S1b, come in PampAI: la porta passa i metodi dei ricordi alla sua memoria.
    override suspend fun memory(method: String, json: String, caller: AiAssistCaller): String =
      AiMemoryInbox(book).handle(method, json, AiMemoryCaller(caller.packageName, caller.label))
  }

  companion object {
    lateinit var assist: AiAssistHost
    lateinit var book: AiMemoryBook
  }

  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val classeviva = "dev.antigravity.classevivaexpressive"
  private val chimica = Regex("(?<![\\p{L}\\p{N}])chimica(?![\\p{L}\\p{N}])")
  private val verifica = AiMemoryProposal("ha la verifica di chimica giovedì", "scuola.verifica.chimica", listOf("chimica", "verifica"))

  @Before fun setUp() = runBlocking {
    assist = AiAssistHost(AiAssistExecutor { _, _ -> }, InMemoryAiAssistConsentStore(), AiAssistPolicy())
    book = AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig())
  }

  @After fun stop() {
    assist.cancelAll()
    book.close()
    ShadowBinder.reset()
  }

  private fun provider(asPackage: String): MemoryHost {
    val info = ProviderInfo().apply {
      authority = "${context.packageName}.ai.assist.memoria"
      name = MemoryHost::class.java.name
      packageName = context.packageName
      exported = true
    }
    return Robolectric.buildContentProvider(MemoryHost::class.java).create(info).get().apply { packages = arrayOf(asPackage) }
  }

  private fun MemoryHost.send(method: String, json: String): AiMemoryProposalResult =
    AiMemoryProposalResult.fromJson(call(method, null, Bundle().apply { putString("json", json) }).getString("json")!!)

  /** Vero se la memoria di PampAI ha da qualche parte (domande o ricordi) il fatto con «chimica». */
  private fun inMemory(): Boolean =
    book.pending.value.any { chimica.containsMatchIn(it.text) } || book.memories.value.any { chimica.containsMatchIn(it.text) }

  @Test fun `ClasseViva propone col ponte vero - la domanda arriva a PampAI, e Ricorda dalla card dell'app`() {
    // ClasseViva non ha mai usato PampAI per le domande (il sì di F9.1 e' «da chiedere»): per i ricordi conta solo il «no».
    val cv = provider(classeviva)
    val asked = cv.send(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(verifica))
    assertEquals("il ponte vero non porta la proposta alla memoria", AiMemoryProposalResult.OutcomePending, asked.outcome)
    val ask = book.pending.value.single()
    assertTrue(chimica.containsMatchIn(ask.text))
    // Chi propone e il suo nome li dice il sistema e la tabella, non l'app.
    assertEquals(AiMemorySource.app(classeviva, "ClasseViva Expressive"), ask.source)
    val yes = cv.send(AiMemoryProtocol.MethodAnswer, AiMemoryProtocol.answerRequest(asked.noticeId!!, accept = true))
    assertEquals(AiMemoryProposalResult.OutcomeRemembered, yes.outcome)
    assertTrue(book.memories.value.any { chimica.containsMatchIn(it.text) })
  }

  @Test fun `il no di F9-1, il blocco della memoria e un'app fuori tabella fermano la proposta`() {
    val cv = provider(classeviva)
    // L'utente ha detto no a ClasseViva in «App che usano PampAI».
    assist.setAppConsent(classeviva, AiAssistConsentState.Denied)
    assertEquals(AiMemoryProposalResult.OutcomeNotAllowed, cv.send(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(verifica)).outcome)
    assertFalse("la proposta di un'app rifiutata e' arrivata alla memoria", inMemory())
    // Il sì di F9.1 c'e', ma la memoria l'ha bloccata.
    assist.setAppConsent(classeviva, AiAssistConsentState.Allowed)
    runBlocking { book.setAppBlocked(classeviva, true) }
    assertEquals(AiMemoryProposalResult.OutcomeNotAllowed, cv.send(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(verifica)).outcome)
    assertFalse("la proposta di un'app bloccata e' arrivata alla memoria", inMemory())
    // Pampa Notes e' nella tabella di F9.1 ma non in quella dei ricordi.
    val notes = provider("dev.pampa.pampanotes")
    assertEquals(AiMemoryProposalResult.OutcomeNotAllowed, notes.send(AiMemoryProtocol.MethodPropose, AiMemoryProtocol.proposeRequest(verifica.copy(key = "studio.chimica"))).outcome)
    assertFalse(inMemory())
  }

  @Test fun `una firma diversa riceve SecurityException anche sui metodi dei ricordi`() {
    val stranger = provider(classeviva).apply { comparison = PackageManager.SIGNATURE_NO_MATCH }
    ShadowBinder.setCallingUid(40_002)
    AiMemoryProtocol.Methods.forEach { method ->
      assertThrows("$method deve essere negato", SecurityException::class.java) { stranger.send(method, AiMemoryProtocol.proposeRequest(verifica)) }
    }
    assertFalse(inMemory())
  }
}
