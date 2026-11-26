package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 (Muse, «Luoghi e aspetto»): un file dei luoghi o dell'aspetto rimasto a metà (il processo morto mentre scriveva)
 * faceva cadere ogni lettura per sempre. Adesso si scrive accanto e poi si scambia, e un file che non si apre più si mette
 * da parte: i permessi ripartono «mai chiesti», l'aspetto «di serie», e lo Store lo sa (`setAside`).
 */
class LocalStoresRecoveryTest {
  private val weather = "dev.pampa.fluidweather"
  private val dir: File = Files.createTempDirectory("negozi").toFile()

  @Test
  fun `R2 - i permessi dei luoghi a metà si mettono da parte e ripartono`() {
    val vault = MemoryCustodyVault()
    val places = PlacesStore(vault, dir, AppPolicy())
    assertTrue(places.setAccess(weather, PlaceAccess.READ))
    val file = File(dir, "luoghi.bin")
    val bytes = file.readBytes()
    file.writeBytes(bytes.copyOf(bytes.size / 2)) // il processo è morto a metà della scrittura

    val again = PlacesStore(vault, dir, AppPolicy())
    assertTrue("«Chi usa i tuoi luoghi» non cade", again.decisions().isEmpty())
    assertNull(again.decision(weather))
    val aside = requireNotNull(again.setAside) { "lo Store deve sapere che i permessi sono ripartiti" }
    assertArrayEquals(bytes.copyOf(bytes.size / 2), aside.readBytes())
    // Si richiede e si riscrive, intero.
    assertTrue(again.setAccess(weather, PlaceAccess.READ_WRITE))
    assertEquals(PlaceAccess.READ_WRITE, PlacesStore(vault, dir, AppPolicy()).decision(weather))
    assertFalse("niente file accanto lasciato in giro", File(dir, "luoghi.bin.nuovo").exists())
    // L'uscita da remoto cancella anche quello messo da parte: la casa dice dove abiti.
    again.wipe()
    assertFalse(aside.exists())
  }

  @Test
  fun `R2 - con la classe chiusa i permessi non si toccano`() {
    val vault = MemoryCustodyVault()
    PlacesStore(vault, dir, AppPolicy()).setAccess(weather, PlaceAccess.READ)
    val bytes = File(dir, "luoghi.bin").readBytes()
    vault.beforeFirstUnlock = true
    val locked = PlacesStore(vault, dir, AppPolicy())
    assertTrue(runCatching { locked.decisions() }.exceptionOrNull() is CustodyLockedException)
    assertNull(locked.setAside)
    assertArrayEquals(bytes, File(dir, "luoghi.bin").readBytes())
    vault.beforeFirstUnlock = false
    assertEquals(PlaceAccess.READ, locked.decision(weather))
  }

  @Test
  fun `R2 - l'aspetto a metà si mette da parte e riparte di serie`() {
    val store = AppearanceStore(dir)
    val before = store.snapshot()
    val file = File(dir, "aspetto.json")
    file.writeText("""{"v":2,"version":3,"entries":[{"s":"global","k":"appearance.mode","v":"da""") // a metà
    val again = AppearanceStore(dir)
    assertEquals(before.global, again.snapshot().global)
    assertTrue(requireNotNull(again.setAside) { "lo Store deve saperlo" }.exists())
    assertFalse(file.exists())
  }
}
