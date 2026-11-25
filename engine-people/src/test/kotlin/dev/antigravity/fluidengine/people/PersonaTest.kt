package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.people.crypto.PersonCard
import dev.antigravity.fluidengine.people.crypto.PersonCode
import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** La persona divisa in radice (classe madre) e chiavi di lavoro (classe consegna), `b\chiavi.md` § 12. */
class PersonaTest {
  private fun dir(): File = Files.createTempDirectory("persona").toFile()

  @Test
  fun `la persona nasce con la radice, il codice che non cambia e le chiavi di lavoro firmate`() {
    val vault = MemoryPersonVault()
    val store = PersonaStore(dir(), vault)
    assertFalse(store.exists)
    val me = store.create("Alessio")
    assertTrue(store.exists)
    assertEquals(PersonCode.of(me.card.root), me.code)
    assertEquals(1, me.card.work.epoch)
    assertTrue(me.work.public.signedBy(me.card.root))
    assertEquals(me.card, PersonCard.verify(me.card.raw, me.card.sig))
  }

  @Test
  fun `a telefono bloccato le chiavi di lavoro si leggono, la radice no, e il sottofondo non la tocca`() {
    val vault = MemoryPersonVault()
    val d = dir()
    PersonaStore(d, vault).create("Alessio")
    vault.motherLocked = true
    val opensBefore = vault.rootOpens
    // Un altro processo (il campanello) rilegge da disco: solo la classe consegna.
    val again = PersonaStore(d, vault).identity()!!
    assertEquals(1, again.work.epoch)
    assertEquals(opensBefore, vault.rootOpens)
    try {
      PersonaStore(d, vault).rotateWork()
      fail("la rotazione vuole la radice")
    } catch (_: PersonLockedException) {
    }
    vault.allLocked = true
    try {
      PersonaStore(d, vault).identity()
      fail("prima del primo sblocco non si apre niente")
    } catch (_: PersonLockedException) {
    }
  }

  @Test
  fun `il rinnovo cambia le chiavi di lavoro e lascia radice, codice e casella`() {
    val store = PersonaStore(dir(), MemoryPersonVault())
    val before = store.create("Alessio")
    val after = store.rotateWork()
    assertEquals(2, after.card.work.epoch)
    assertEquals(before.code, after.code)
    assertEquals(before.mailbox, after.mailbox)
    assertArrayEquals(before.card.root, after.card.root)
    assertNotEquals(before.card.work, after.card.work)
    assertEquals(setOf(1, 2), after.works.keys)
    assertTrue(after.card.at > before.card.at)
    val renamed = store.rename("Ale")
    assertEquals("Ale", renamed.card.name)
    assertEquals(2, renamed.card.work.epoch)
  }

  @Test
  fun `la copia sotto K_com(persona) riporta la persona su un telefono nuovo, e solo con la chiave giusta`() {
    val accountId = "acc_prova"
    val k1 = PampaCrypto.random(32)
    val k2 = PampaCrypto.random(32)
    val phone = PersonaStore(dir(), MemoryPersonVault())
    val me = phone.create("Alessio")
    val field = phone.exportSealed(accountId, 2, k2, version = 1)
    val tablet = PersonaStore(dir(), MemoryPersonVault())
    val back = tablet.importSealed(accountId, field, 1) { mapOf(1 to k1, 2 to k2)[it] }
    assertEquals(me.card, back.card)
    assertArrayEquals(me.work.sign.publicKey, back.work.sign.publicKey)
    assertArrayEquals(phone.root().publicKey, tablet.root().publicKey)
    // Spostata a un'altra versione, o aperta con la chiave di un'altra epoca: non si apre.
    assertTrue(runCatching { PersonaStore(dir(), MemoryPersonVault()).importSealed(accountId, field, 2) { k2 } }.exceptionOrNull() is PampaCryptoException)
    assertTrue(runCatching { PersonaStore(dir(), MemoryPersonVault()).importSealed("acc_altro", field, 1) { k2 } }.exceptionOrNull() is PampaCryptoException)
    assertTrue(runCatching { PersonaStore(dir(), MemoryPersonVault()).importSealed(accountId, field, 1) { k1 } }.exceptionOrNull() is PampaCryptoException)
    // Una copia di un'altra persona non sostituisce quella del telefono.
    val other = PersonaStore(dir(), MemoryPersonVault()).also { it.create("Marco") }.exportSealed(accountId, 2, k2, 1)
    assertTrue(runCatching { tablet.importSealed(accountId, other, 1) { k2 } }.exceptionOrNull() is PampaCryptoException)
  }

  @Test
  fun `l'uscita cancella la persona dal telefono`() {
    val store = PersonaStore(dir(), MemoryPersonVault())
    store.create("Alessio")
    store.wipe()
    assertFalse(store.exists)
    assertEquals(null, store.identity())
  }
}
