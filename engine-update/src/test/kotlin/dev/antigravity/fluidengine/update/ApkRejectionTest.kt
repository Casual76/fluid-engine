package dev.antigravity.fluidengine.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Il controllo che sta fra un download e una richiesta di installazione.
 *
 * Vale la pena testarlo perche' il modo in cui puo' sbagliare non e' "non installa": e' installare
 * *un'altra app*. Un manifest sbagliato, un asset caricato nella release sbagliata, un redirect
 * verso il file di qualcun altro — e senza questi controlli l'utente si vede una richiesta di
 * installazione per un pacchetto che non ha mai chiesto, con la fiducia di un aggiornamento.
 */
class ApkRejectionTest {

  @Test
  fun `un apk di un altro pacchetto viene rifiutato`() {
    assertEquals(
      "L'APK scaricato non appartiene a questa app.",
      rejectApk(
        expectedPackageName = "com.example.app",
        expectedVersionName = "1.2.12",
        actualPackageName = "com.other.app",
        actualVersionName = "1.2.12",
      ),
    )
  }

  @Test
  fun `un apk con una versione diversa da quella annunciata viene rifiutato`() {
    assertEquals(
      "Versione APK inattesa: 1.2.10.",
      rejectApk(
        expectedPackageName = "com.example.app",
        expectedVersionName = "1.2.12",
        actualPackageName = "com.example.app",
        actualVersionName = "1.2.10",
      ),
    )
  }

  @Test
  fun `l'apk giusto passa`() {
    assertNull(
      rejectApk(
        expectedPackageName = "com.example.app",
        expectedVersionName = "1.2.12",
        actualPackageName = "com.example.app",
        actualVersionName = "1.2.12",
      ),
    )
  }

  @Test
  fun `una versione assente nell'apk non e' un motivo di rifiuto`() {
    assertNull(
      rejectApk(
        expectedPackageName = "com.example.app",
        expectedVersionName = "1.2.12",
        actualPackageName = "com.example.app",
        actualVersionName = null,
      ),
    )
  }

  /** Il caso in cui `getPackageArchiveInfo` legge un file che non e' un APK. */
  @Test
  fun `un pacchetto assente viene rifiutato`() {
    assertEquals(
      "L'APK scaricato non appartiene a questa app.",
      rejectApk(
        expectedPackageName = "com.example.app",
        expectedVersionName = "1.2.12",
        actualPackageName = null,
        actualVersionName = "1.2.12",
      ),
    )
  }
}
