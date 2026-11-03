package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.foundation.AppUpdateErrorKind
import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.foundation.UpdateChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FluidUpdateController] (3.0.1): la colla che tre app avevano scritto uguale, provata con un updater finto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidUpdateControllerTest {

  private val update = AvailableAppUpdate(
    version = "1.2.0",
    changelog = "Novita'",
    releaseTag = "v1.2.0",
    apkAsset = "app.apk",
    downloadUrl = "https://example.invalid/app.apk",
    sizeBytes = 1_000L,
  )

  private class FakeUpdater(
    var found: Result<AvailableAppUpdate?> = Result.success(null),
    var steps: List<AppUpdateInstallState> = emptyList(),
    var installFailure: Exception? = null,
    var gate: CompletableDeferred<Unit>? = null,
  ) : AppUpdater {
    var checks = 0
    var lastChannel: UpdateChannel? = null
    var lastIgnored: String? = null

    override suspend fun check(
      currentVersionName: String,
      channel: UpdateChannel,
      ignoredVersion: String,
    ): Result<AvailableAppUpdate?> {
      checks++
      lastChannel = channel
      lastIgnored = ignoredVersion
      gate?.await()
      return found
    }

    override fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState> = flow {
      installFailure?.let { throw it }
      steps.forEach { emit(it) }
    }
  }

  private fun TestScope.controller(updater: AppUpdater) =
    FluidUpdateController(
      updater = updater,
      scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
      currentVersion = "1.1.0",
      now = { 42L },
    )

  @Test
  fun `niente di nuovo e' aggiornata, non un errore`() = runTest {
    val c = controller(FakeUpdater(found = Result.success(null)))
    c.check()
    assertEquals(FluidUpdateUiState.UpToDate, c.state)
    assertEquals(42L, c.lastCheckedAtMillis)
    assertNull(c.available)
  }

  @Test
  fun `un controllo che non riesce e' CheckFailed, anche se l'updater lancia`() = runTest {
    val failing = FakeUpdater(found = Result.failure(IllegalStateException("rete")))
    val c = controller(failing)
    c.check()
    assertEquals(FluidUpdateUiState.CheckFailed("rete"), c.state)

    val throwing = object : AppUpdater by failing {
      override suspend fun check(
        currentVersionName: String,
        channel: UpdateChannel,
        ignoredVersion: String,
      ): Result<AvailableAppUpdate?> = throw IllegalStateException("manifest")
    }
    val d = controller(throwing)
    d.check()
    assertEquals(FluidUpdateUiState.CheckFailed("manifest"), d.state)
  }

  @Test
  fun `un secondo tocco mentre controlla non rilancia niente`() = runTest {
    val gate = CompletableDeferred<Unit>()
    val updater = FakeUpdater(found = Result.success(update), gate = gate)
    val c = controller(updater)
    c.check()
    c.check()
    assertEquals(FluidUpdateUiState.Checking, c.state)
    gate.complete(Unit)
    assertEquals(1, updater.checks)
    assertTrue(c.state is FluidUpdateUiState.Available)
  }

  @Test
  fun `il canale e la versione saltata arrivano all'updater`() = runTest {
    val updater = FakeUpdater()
    val c = controller(updater)
    c.channel = UpdateChannel.BETA
    c.ignoredVersion = "1.2.0"
    c.check()
    assertEquals(UpdateChannel.BETA, updater.lastChannel)
    assertEquals("1.2.0", updater.lastIgnored)
  }

  @Test
  fun `trovata, si installa fino in fondo`() = runTest {
    val updater = FakeUpdater(
      found = Result.success(update),
      steps = listOf(
        AppUpdateInstallState.Downloading(progress = 0.5f, downloadedBytes = 500L, totalBytes = 1_000L),
        AppUpdateInstallState.Installed(filePath = "app.apk"),
      ),
    )
    val c = controller(updater)
    c.install()
    assertEquals("senza una versione in mano non fa niente", FluidUpdateUiState.Idle, c.state)
    c.check()
    c.install()
    assertEquals(FluidUpdateUiState.Installed(update.toFluidUpdateRelease()), c.state)
  }

  @Test
  fun `il permesso d'installare e' NeedsPermission, e lo stesso tocco riprova`() = runTest {
    val updater = FakeUpdater(
      found = Result.success(update),
      steps = listOf(AppUpdateInstallState.Error("permesso", kind = AppUpdateErrorKind.InstallPermission)),
    )
    val c = controller(updater)
    c.check()
    c.install()
    assertEquals(FluidUpdateUiState.NeedsPermission(update.toFluidUpdateRelease()), c.state)

    updater.steps = listOf(AppUpdateInstallState.Installed(filePath = "app.apk"))
    c.install()
    assertTrue(c.state is FluidUpdateUiState.Installed)
  }

  @Test
  fun `un'eccezione dell'installatore e' Failed col suo messaggio`() = runTest {
    val updater = FakeUpdater(found = Result.success(update), installFailure = IllegalStateException("disco pieno"))
    val c = controller(updater)
    c.check()
    c.install()
    assertEquals(FluidUpdateUiState.Failed(update.toFluidUpdateRelease(), "disco pieno"), c.state)
  }
}
