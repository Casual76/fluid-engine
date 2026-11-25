package dev.antigravity.fluidengine.account.ui

import dev.antigravity.fluidengine.account.AccountProfile
import dev.antigravity.fluidengine.account.AccountState
import org.junit.Assert.assertEquals
import org.junit.Test

/** La riga «Account Pampa» nei quattro stati (`b\architettura.md` § 2.4), con le parole del progetto. */
class PampaAccountRowModelTest {

  @Test
  fun `i quattro stati, con le frasi di architettura 2_4 e l'azione giusta`() {
    val noStore = pampaAccountRowModel(AccountState.NoStore)
    assertEquals("Account Pampa" to "L'Account Pampa si usa con Pampa Store", noStore.title to noStore.subtitle)
    assertEquals(PampaAccountAction.DOWNLOAD_STORE, noStore.action)

    val old = pampaAccountRowModel(AccountState.StoreTooOld("state"))
    assertEquals("Aggiorna Pampa Store per usare l'account", old.subtitle)
    assertEquals(PampaAccountAction.UPDATE_STORE, old.action)

    val out = pampaAccountRowModel(AccountState.SignedOut)
    assertEquals("Accedi con Pampa Store", out.subtitle)
    assertEquals(PampaAccountAction.SIGN_IN, out.action)
    assertEquals("senza account l'avatar è il glifo di una persona", "", out.avatarName)

    val inside = pampaAccountRowModel(AccountState.SignedIn(AccountProfile("acc_1", "Alessio Pampaloni", "AP", recovery = true)))
    assertEquals("Alessio Pampaloni" to "Account Pampa", inside.title to inside.subtitle)
    assertEquals("Alessio Pampaloni", inside.avatarName)
    assertEquals("il colore dell'avatar viene dall'id, uguale in ogni app", "acc_1", inside.avatarKey)
    assertEquals(PampaAccountAction.OPEN_ACCOUNT, inside.action)
  }

  @Test
  fun `una sessione di recupero lo dice, e un'app in un'altra lingua passa le sue parole`() {
    val rec = pampaAccountRowModel(AccountState.SignedIn(AccountProfile("acc_1", "Alessio", "A", recovery = false, recoverySession = true)))
    assertEquals("Entrato col codice di recupero", rec.subtitle)
    val en = pampaAccountRowModel(AccountState.SignedOut, PampaAccountLabels(signedOut = "Sign in with Pampa Store"))
    assertEquals("Sign in with Pampa Store", en.subtitle)
    val noName = pampaAccountRowModel(AccountState.SignedIn(AccountProfile("acc_1", " ", "", recovery = false)))
    assertEquals("Account Pampa", noName.title)
  }
}
