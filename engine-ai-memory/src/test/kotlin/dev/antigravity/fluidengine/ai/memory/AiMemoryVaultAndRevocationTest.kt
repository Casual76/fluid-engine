package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F9.3 · La cifratura e la revoca. Sul disco c'e' un file solo, chiuso dalla cassaforte di `engine-vault` (AES-256-GCM a
 * pezzi, la chiave della sezione nel Keystore): niente dei ricordi si legge, nemmeno le radici delle parole (l'indice
 * vive solo in memoria). Dimenticare toglie davvero: dal file, dall'indice, dagli annulla, dalle card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiMemoryVaultAndRevocationTest {
  @get:Rule val tmp = TemporaryFolder()

  private val io = Executors.newSingleThreadExecutor { Thread(it, "vault-io") }.asCoroutineDispatcher()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val clock = TestClock()
  private val classeviva = "dev.antigravity.classevivaexpressive"

  @After fun stop() {
    scope.cancel()
    io.close()
  }

  private fun vault(dir: File, keystore: MemoryVaultKeystore) =
    FluidVault.create(dir = dir, keystore = keystore, clock = { System.nanoTime() / 1_000_000 }, scope = scope, io = io)

  private fun bookIn(dir: File, keystore: MemoryVaultKeystore) = runBlocking {
    AiMemoryBook.open(AiMemoryStore.vault(vault(dir, keystore).section(AiMemoryBook.VaultSectionName)), AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true), clock)
  }

  /** Tutti i byte della cartella della cassaforte, come li vedrebbe chi copia i file del telefono. */
  private fun diskBytes(dir: File): String =
    dir.walkTopDown().filter { it.isFile }.joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }

  @Test fun `sul disco niente dei ricordi si legge, e si riaprono con la stessa chiave`() = runBlocking {
    val dir = tmp.newFolder()
    val keystore = MemoryVaultKeystore()
    val b = bookIn(dir, keystore)
    b.remember(AiMemoryProposal("è in 4F al liceo Galilei", "scuola.classe", listOf("scuola", "compiti")), AiMemorySource.chat(1))
    b.remember(AiMemoryProposal("è vegetariana", "cibo.dieta", listOf("cena")), AiMemorySource.chat(2))
    b.close()
    val disk = diskBytes(dir)
    assertTrue(disk.isNotEmpty())
    // Parole di almeno 4 lettere: due byte a caso in un file cifrato possono comporre «4F», quattro quasi mai.
    for (secret in listOf("Galilei", "liceo", "vegetariana", "scuola.classe", "compiti", "cena", "memories")) {
      assertFalse("sul disco c'e' «$secret»", disk.contains(secret))
    }
    // Nient'altro sul disco: nessun indice FTS, nessun -wal.
    assertTrue(dir.walkTopDown().none { it.name.endsWith(".db") || it.name.endsWith("-wal") })

    val again = bookIn(dir, keystore)
    assertEquals(setOf("è in 4F al liceo Galilei", "è vegetariana"), again.memories.value.map { it.text }.toSet())
    assertEquals(1, again.relevant("che compiti ho?").size)
  }

  @Test fun `con un'altra chiave (un altro telefono, il Keystore azzerato) non si apre e non si azzera da sola`() {
    val dir = tmp.newFolder()
    runBlocking { bookIn(dir, MemoryVaultKeystore()).remember(AiMemoryProposal("abita a Firenze"), AiMemorySource.chat(1)) }
    val before = diskBytes(dir)
    val error = runCatching { bookIn(dir, MemoryVaultKeystore()) }.exceptionOrNull()
    assertNotNull(error)
    assertEquals(before, diskBytes(dir))
  }

  @Test fun `dimenticare un ricordo, tutti, o quelli nati da un'app`() = runBlocking {
    val dir = tmp.newFolder()
    val keystore = MemoryVaultKeystore()
    val b = bookIn(dir, keystore)
    val cv = AiMemorySource.app(classeviva, "ClasseViva")
    val fromCvChat = AiMemorySource.chat(7, classeviva, "ClasseViva")
    val a = b.remember(AiMemoryProposal("è in 4F", "scuola.classe"), cv)
    b.remember(AiMemoryProposal("ha la verifica di storia giovedì", "scuola.verifica.storia", listOf("verifica", "storia")), cv)
    b.remember(AiMemoryProposal("vuole ripassare la Rivoluzione francese", "studio.storia", listOf("storia")), fromCvChat)
    val mine = b.remember(AiMemoryProposal("è vegetariana", "cibo.dieta", listOf("cena")), AiMemorySource.chat(1))
    b.remember(AiMemoryProposal("abita a Firenze", "casa.citta", listOf("casa")), AiMemorySource.Manual)
    assertEquals(5, b.memories.value.size)

    // Un ricordo: sparisce dalla pagina, dalla ricerca e dal file; la sua card dice «Non lo ricordo più».
    assertTrue(b.forget(mine.memory!!.id))
    assertTrue(b.relevant("cosa mangio a cena?").isEmpty())
    assertTrue(b.notices.value.first { it.memoryId == mine.memory!!.id }.undone)
    assertFalse(b.undo(mine.notice!!.id))

    // Quelli di un'app, chiesti dall'app stessa col ponte: solo quelli proposti da lei.
    assertEquals(2, b.forgetFromApp(classeviva, onlyProposed = true))
    assertEquals(listOf("vuole ripassare la Rivoluzione francese"), b.memories.value.filter { it.source.app == classeviva }.map { it.text })
    assertFalse(b.undo(a.notice!!.id))
    // Quelli di un'app, dalla pagina: anche quelli detti nella chat «Dalle app» partita da lei.
    assertEquals(1, b.forgetFromApp(classeviva))
    assertEquals(listOf("abita a Firenze"), b.memories.value.map { it.text })

    // E nel file non c'e' piu' niente di loro (riaperto e letto da capo).
    b.close()
    val reopened = bookIn(dir, keystore)
    assertEquals(listOf("abita a Firenze"), reopened.memories.value.map { it.text })
    assertEquals(1, reopened.forgetAll())
    assertTrue(reopened.memories.value.isEmpty())
    assertTrue(bookIn(dir, keystore).memories.value.isEmpty())
  }

  @Test fun `un'app bloccata non propone piu', e lo resta dopo un riavvio`() = runBlocking {
    val dir = tmp.newFolder()
    val keystore = MemoryVaultKeystore()
    val b = bookIn(dir, keystore)
    b.setAppBlocked(classeviva, true)
    val cv = AiMemorySource.app(classeviva, "ClasseViva")
    assertEquals(AiMemoryProposalResult.OutcomeNotAllowed, b.remember(AiMemoryProposal("è in 4F", "scuola.classe"), cv).result)
    b.close()
    val again = bookIn(dir, keystore)
    assertEquals(setOf(classeviva), again.blockedApps.value)
    assertEquals(AiMemoryProposalResult.OutcomeNotAllowed, again.remember(AiMemoryProposal("è in 4F", "scuola.classe"), cv).result)
    again.setAppBlocked(classeviva, false)
    assertEquals(AiMemoryProposalResult.OutcomeRemembered, again.remember(AiMemoryProposal("è in 4F", "scuola.classe"), cv).result)
  }
}
