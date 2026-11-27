package dev.antigravity.fluidengine.ui.fluid.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 · La pillola specchiata fuori dall'app (F8.19): la pillola «Offline» di F8.20 non è un'attività avviata
 * dall'utente, quindi fuori non chiede di diventare un Live Update (le linee guida di Android: solo ciò che l'utente ha
 * avviato). Sulla JVM.
 */
class FluidActivityOutsideKindTest {
  @Test
  fun r2m_laPillolaOffline_fuoriNonChiedeLaPromozione() {
    val offline = FluidActivity(id = "offline", kind = FluidActivityKind.Offline, title = "Offline · 3 modifiche in attesa")
    assertFalse(offline.toLiveUpdate().wantsPromotion)
  }

  @Test
  fun unTrasferimento_fuoriLaChiedeAncora() {
    val transfer = FluidActivity(id = "video", kind = FluidActivityKind.Transfer, title = "Converto il video", progress = { 0.4f })
    assertTrue(transfer.toLiveUpdate().wantsPromotion)
  }
}
