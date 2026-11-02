package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il modello dell'aggiornamento, senza disegnarlo: i conti che otto app facevano ognuna a modo suo e che
 * si sbagliano in silenzio — un cento per cento che arriva un byte prima della fine, una dimensione di
 * "0,0 MB" per un file che il manifest non misura, un download senza peso che mostra "0%" per tutto il
 * tempo.
 */
class FluidUpdateModelTest {

  private val release = FluidUpdateRelease(version = "2.1.0", notes = "Novita'", sizeBytes = 12L * 1024 * 1024)
  private val it = Locale.ITALY

  // --- dimensione -----------------------------------------------------------------------------------

  @Test
  fun `an unknown size says nothing instead of zero`() {
    assertNull(FluidUpdate.sizeLabel(0L))
    assertNull(FluidUpdate.sizeLabel(-5L))
  }

  @Test
  fun `a size is in the unit a person reads`() {
    assertEquals("512 B", FluidUpdate.sizeLabel(512L, it))
    assertEquals("812 KB", FluidUpdate.sizeLabel(812L * 1024, it))
    assertEquals("12,4 MB", FluidUpdate.sizeLabel((12.4 * 1024 * 1024).toLong(), it))
    assertEquals("1,5 GB", FluidUpdate.sizeLabel((1.5 * 1024 * 1024 * 1024).toLong(), it))
  }

  @Test
  fun `a size at the border goes up a unit instead of showing a four digit number`() {
    assertEquals("1 KB", FluidUpdate.sizeLabel(1024L, it))
    assertEquals("1,0 MB", FluidUpdate.sizeLabel(1024L * 1024, it))
  }

  // --- percentuale ----------------------------------------------------------------------------------

  @Test
  fun `a percent is rounded down so it never says a hundred before it is done`() {
    assertEquals(99, FluidUpdate.percent(0.996f))
    assertEquals(100, FluidUpdate.percent(1f))
    assertEquals(0, FluidUpdate.percent(0.004f))
    assertEquals(50, FluidUpdate.percent(0.5f))
  }

  @Test
  fun `a percent is held between zero and a hundred`() {
    assertEquals(0, FluidUpdate.percent(-3f))
    assertEquals(100, FluidUpdate.percent(7f))
  }

  // --- note -----------------------------------------------------------------------------------------

  @Test
  fun `release notes lose the markdown that a short row would show as asterisks`() {
    val notes = "## Novita'\r\n**Nuovo**: il tema scuro\n- una riga\n* un'altra\n\n\n\nFine"
    val plain = FluidUpdate.plainNotes(notes)
    assertEquals("Novita'\nNuovo: il tema scuro\n• una riga\n• un'altra\n\nFine", plain)
    assertFalse(plain.contains("**"))
    assertFalse(plain.contains("#"))
  }

  @Test
  fun `the summary of notes is the first paragraph, cleaned`() {
    assertEquals("Tema scuro e correzioni", FluidUpdate.notesSummary("**Tema scuro** e correzioni\n\nAltro paragrafo"))
    assertEquals("", FluidUpdate.notesSummary("   \n\n  "))
    assertEquals("", FluidUpdate.notesSummary(""))
  }

  // --- ultimo controllo -----------------------------------------------------------------------------

  private val minute = 60_000L
  private val hour = 60 * minute
  private val day = 24 * hour
  private val now = 1_000_000_000_000L

  @Test
  fun `a check that never happened says so`() {
    assertEquals("Mai controllato", FluidUpdate.lastCheckLabel(null, now))
    assertEquals("Mai controllato", FluidUpdate.lastCheckLabel(0L, now))
  }

  @Test
  fun `a recent check is now, then minutes, hours and days`() {
    assertEquals("Controllato adesso", FluidUpdate.lastCheckLabel(now - 30_000L, now))
    assertEquals("Controllato 1 minuto fa", FluidUpdate.lastCheckLabel(now - minute, now))
    assertEquals("Controllato 5 minuti fa", FluidUpdate.lastCheckLabel(now - 5 * minute, now))
    assertEquals("Controllato 1 ora fa", FluidUpdate.lastCheckLabel(now - 90 * minute, now))
    assertEquals("Controllato 5 ore fa", FluidUpdate.lastCheckLabel(now - 5 * hour, now))
    assertEquals("Controllato ieri", FluidUpdate.lastCheckLabel(now - 30 * hour, now))
    assertEquals("Controllato 3 giorni fa", FluidUpdate.lastCheckLabel(now - 3 * day, now))
  }

  @Test
  fun `a check dated in the future is now, not minus some minutes`() {
    assertEquals("Controllato adesso", FluidUpdate.lastCheckLabel(now + 10 * minute, now))
  }

  @Test
  fun `a translated app passes its own words for the last check`() {
    val english = FluidUpdateStrings(lastCheckedMinutes = { "Checked $it min ago" })
    assertEquals("Checked 7 min ago", FluidUpdate.lastCheckLabel(now - 7 * minute, now, english))
  }

  // --- dai tipi di foundation -----------------------------------------------------------------------

  @Test
  fun `an available update becomes a release with its notes and its size`() {
    val update = AvailableAppUpdate("3.0.0", "Note", "v3.0.0", "app.apk", "https://x/app.apk", 4096L)
    assertEquals(FluidUpdateRelease("3.0.0", "Note", 4096L), update.toFluidUpdateRelease())
  }

  @Test
  fun `a download with a known size has a progress`() {
    val state = AppUpdateInstallState.Downloading(progress = 0.25f, downloadedBytes = 100L, totalBytes = 400L)
      .toFluidUpdateState(release) as FluidUpdateUiState.Downloading
    assertEquals(0.25f, state.progress!!, 0f)
    assertEquals(100L, state.downloadedBytes)
    assertEquals(400L, state.totalBytes)
    assertEquals(release, state.release)
  }

  @Test
  fun `a download of unknown size has no progress rather than a frozen zero`() {
    val state = AppUpdateInstallState.Downloading(progress = 0f, downloadedBytes = 900L, totalBytes = 0L)
      .toFluidUpdateState(release) as FluidUpdateUiState.Downloading
    assertNull(state.progress)
  }

  @Test
  fun `a progress outside zero and one is held inside`() {
    val state = AppUpdateInstallState.Downloading(progress = 1.4f, downloadedBytes = 5L, totalBytes = 4L)
      .toFluidUpdateState(release) as FluidUpdateUiState.Downloading
    assertEquals(1f, state.progress!!, 0f)
  }

  @Test
  fun `the three waiting steps keep their stage and the installer's message`() {
    val verifying = AppUpdateInstallState.Verifying("Controllo la firma").toFluidUpdateState(release)
    val installing = AppUpdateInstallState.Installing("Installo").toFluidUpdateState(release)
    val awaiting = AppUpdateInstallState.AwaitingUserAction("Conferma").toFluidUpdateState(release)
    assertEquals(FluidUpdateUiState.Working(release, FluidUpdateStage.Verifying, "Controllo la firma"), verifying)
    assertEquals(FluidUpdateUiState.Working(release, FluidUpdateStage.Installing, "Installo"), installing)
    assertEquals(FluidUpdateUiState.Working(release, FluidUpdateStage.AwaitingUser, "Conferma"), awaiting)
  }

  @Test
  fun `the two endings are installed and failed`() {
    assertEquals(FluidUpdateUiState.Installed(release), AppUpdateInstallState.Installed("/tmp/app.apk").toFluidUpdateState(release))
    assertEquals(FluidUpdateUiState.Failed(release, "Rete assente"), AppUpdateInstallState.Error("Rete assente").toFluidUpdateState(release))
  }

  // --- proprieta' dello stato -----------------------------------------------------------------------

  @Test
  fun `only states about a release have one`() {
    assertNull(FluidUpdateUiState.Idle.release)
    assertNull(FluidUpdateUiState.Checking.release)
    assertNull(FluidUpdateUiState.UpToDate.release)
    assertNull(FluidUpdateUiState.CheckFailed("rete").release)
    assertEquals(release, FluidUpdateUiState.Available(release).release)
    assertEquals(release, FluidUpdateUiState.NeedsPermission(release).release)
    assertNotNull(FluidUpdateUiState.Failed(release, "x").release)
  }

  @Test
  fun `a check, a download and an install are busy and waiting for the system is busy too`() {
    assertTrue(FluidUpdateUiState.Checking.isBusy)
    assertTrue(FluidUpdateUiState.Downloading(release, 0.5f).isBusy)
    assertTrue(FluidUpdateUiState.Working(release, FluidUpdateStage.Verifying).isBusy)
    // Due finestre di conferma sarebbero peggio di un tasto che non risponde.
    assertTrue(FluidUpdateUiState.Working(release, FluidUpdateStage.AwaitingUser).isBusy)
    assertFalse(FluidUpdateUiState.Available(release).isBusy)
    assertFalse(FluidUpdateUiState.Failed(release, "x").isBusy)
    assertFalse(FluidUpdateUiState.UpToDate.isBusy)
  }

  @Test
  fun `a release that can be started or restarted can be installed`() {
    assertTrue(FluidUpdateUiState.Available(release).canInstall)
    assertTrue(FluidUpdateUiState.Failed(release, "x").canInstall)
    assertTrue(FluidUpdateUiState.NeedsPermission(release).canInstall)
    assertFalse(FluidUpdateUiState.Downloading(release, 0.1f).canInstall)
    assertFalse(FluidUpdateUiState.Installed(release).canInstall)
    assertFalse(FluidUpdateUiState.UpToDate.canInstall)
  }

  // --- la frase di stato ----------------------------------------------------------------------------

  @Test
  fun `a download says how much and how far, and without a size only how far`() {
    val withSize = FluidUpdateUiState.Downloading(release, 0.5f, downloadedBytes = 6L * 1024 * 1024, totalBytes = 12L * 1024 * 1024)
    assertEquals("Scaricati 6,0 MB di 12,0 MB · 50%", FluidUpdate.progressText(withSize, locale = it))
    val unknown = FluidUpdateUiState.Downloading(release, progress = null)
    assertEquals("Download in corso…", FluidUpdate.progressText(unknown, locale = it))
  }

  @Test
  fun `a waiting step uses the installer's words or its own`() {
    assertEquals("Controllo la firma", FluidUpdate.progressText(FluidUpdateUiState.Working(release, FluidUpdateStage.Verifying, "Controllo la firma")))
    assertEquals("Verifica del file…", FluidUpdate.progressText(FluidUpdateUiState.Working(release, FluidUpdateStage.Verifying)))
    assertEquals("Installazione…", FluidUpdate.progressText(FluidUpdateUiState.Working(release, FluidUpdateStage.Installing)))
    assertEquals(
      "Conferma l'installazione nella finestra di Android.",
      FluidUpdate.progressText(FluidUpdateUiState.Working(release, FluidUpdateStage.AwaitingUser)),
    )
  }

  @Test
  fun `a failure with no message still says something`() {
    assertEquals("Disco pieno", FluidUpdate.progressText(FluidUpdateUiState.Failed(release, "Disco pieno")))
    assertEquals("Aggiornamento non riuscito", FluidUpdate.progressText(FluidUpdateUiState.Failed(release, "")))
  }

  @Test
  fun `states with nothing in progress have no progress line`() {
    assertNull(FluidUpdate.progressText(FluidUpdateUiState.Idle))
    assertNull(FluidUpdate.progressText(FluidUpdateUiState.UpToDate))
    assertNull(FluidUpdate.progressText(FluidUpdateUiState.Available(release)))
    assertNull(FluidUpdate.progressText(FluidUpdateUiState.CheckFailed()))
  }
}
