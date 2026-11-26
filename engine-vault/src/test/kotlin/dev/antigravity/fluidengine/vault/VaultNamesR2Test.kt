package dev.antigravity.fluidengine.vault

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * R2 · Una sezione (o una cassaforte) chiamata «..» stava nella cartella di sopra, e `reset()` l'avrebbe cancellata
 * ricorsiva: le altre cassaforti, o i dati dell'app. I nomi li sceglie chi scrive l'app: è una trappola dell'API, e
 * adesso i nomi di soli punti si rifiutano come già si rifiutano per i file.
 */
class VaultNamesR2Test {
  @get:Rule val tmp = TemporaryFolder()

  @Test
  fun `una sezione o una cassaforte di soli punti si rifiuta`() = runTest {
    val vault = FluidVault.create(
      dir = tmp.newFolder("vault"),
      keystore = MemoryVaultKeystore { testScheduler.currentTime },
      clock = { testScheduler.currentTime },
      scope = backgroundScope,
      io = StandardTestDispatcher(testScheduler),
      secretIterations = 1_000,
    )
    for (name in listOf(".", "..", "...")) {
      assertTrue("la sezione «$name»", runCatching { vault.section(name) }.exceptionOrNull() is IllegalArgumentException)
      assertTrue("la cassaforte «$name»", runCatching { FluidVault.create(tmp.newFolder(), MemoryVaultKeystore { 0L }, name) }.exceptionOrNull() is IllegalArgumentException)
    }
    // I nomi col punto in mezzo restano validi.
    assertEquals("note.v2", vault.section("note.v2").name)
  }
}
