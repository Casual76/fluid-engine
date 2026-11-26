package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1 (Muse 8): il file dello stato delle persone che non si apre più. Una chiave del Keystore persa o invalidata, o un
 * file troncato, non devono far cadere ogni chiamata per sempre: il file si mette da parte (mai buttato), lo stato
 * riparte vuoto e chi chiama lo sa. La classe chiusa prima del primo sblocco invece è di passaggio: si riprova dopo.
 */
class PeopleStateStoreTest {
  private val dir: File = Files.createTempDirectory("stato-persone").toFile()
  private val file = File(dir, "persone.bin")

  private fun written(vault: MemoryPersonVault): ByteArray {
    val st = PeopleState().apply { cursor = 42; blocked += "casella-di-qualcuno" }
    PeopleStateStore(file, vault).save(st)
    return file.readBytes()
  }

  @Test
  fun `R1 - una chiave persa mette da parte il file e riparte vuoto`() {
    val bytes = written(MemoryPersonVault())
    val store = PeopleStateStore(file, MemoryPersonVault()) // un'altra chiave: quella di prima non vale più
    val st = store.load()
    assertEquals(0, st.cursor)
    assertTrue(st.blocked.isEmpty())
    val aside = requireNotNull(store.setAside) { "chi chiama deve sapere che lo stato è ripartito" }
    assertTrue(aside.exists())
    assertArrayEquals("il file rotto resta com'era", bytes, aside.readBytes())
    assertFalse(file.exists())
    // Lo stato nuovo si salva e si rilegge.
    st.cursor = 7
    store.save(st)
    assertEquals(7, store.reload().cursor)
    // L'uscita toglie anche il file messo da parte.
    store.wipe()
    assertFalse(aside.exists())
  }

  @Test
  fun `R1 - un file troncato mette da parte il file e riparte vuoto`() {
    val vault = MemoryPersonVault()
    val bytes = written(vault)
    file.writeBytes(bytes.copyOf(bytes.size / 2))
    val store = PeopleStateStore(file, vault)
    assertEquals(0, store.load().cursor)
    assertNotNull(store.setAside)
  }

  @Test
  fun `R1 - prima del primo sblocco non si tocca niente`() {
    val vault = MemoryPersonVault()
    val bytes = written(vault)
    vault.allLocked = true
    val store = PeopleStateStore(file, vault)
    assertTrue(runCatching { store.load() }.exceptionOrNull() is PersonLockedException)
    assertNull(store.setAside)
    assertArrayEquals(bytes, file.readBytes())
    vault.allLocked = false
    assertEquals(42, store.load().cursor)
  }
}
