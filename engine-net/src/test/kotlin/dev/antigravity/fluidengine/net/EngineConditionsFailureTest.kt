package dev.antigravity.fluidengine.net

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowConnectivityManager

/**
 * Android che rifiuta il callback della rete predefinita, come fa con `TooManyRequestsException` quando l'app ne ha
 * aperti troppi: le prime [failures] volte lancia, poi lavora come l'ombra di serie.
 */
@Implements(ConnectivityManager::class)
class RefusingConnectivityManager : ShadowConnectivityManager() {
  @Implementation
  override fun registerDefaultNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback) {
    if (failures.getAndDecrement() > 0) throw RuntimeException("Too many NetworkRequests filed")
    super.registerDefaultNetworkCallback(networkCallback)
  }

  companion object {
    val failures = AtomicInteger(0)
  }
}

/** R2 · `EngineConditions.install` quando Android rifiuta un callback: niente app che cade, e si riprova. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [RefusingConnectivityManager::class])
class EngineConditionsFailureTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @After fun reset() {
    EngineConditions.uninstall()
    FluidDeviceConditions.reset()
    RefusingConnectivityManager.failures.set(0)
  }

  @Test fun `R2 un callback di rete rifiutato non fa cadere l'app e si riprova`() {
    val uncaught = CopyOnWriteArrayList<Throwable>()
    val before = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    try {
      RefusingConnectivityManager.failures.set(1)
      EngineConditions.install(context)
      val deadline = System.currentTimeMillis() + 10_000L
      while (System.currentTimeMillis() < deadline && !FluidDeviceConditions.current.known && uncaught.isEmpty()) Thread.sleep(20)
      // Una prima lettura puo' arrivare prima dell'errore: si lascia il tempo all'errore (e al nuovo tentativo).
      Thread.sleep(2_500L)
      assertEquals("eccezioni arrivate al gestore del processo (sul telefono: l'app cade)", emptyList<Throwable>(), uncaught.toList())
      assertTrue("la lettura non si e' ripresa", FluidDeviceConditions.current.known)
      assertTrue(EngineConditions.isInstalled)
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(before)
    }
  }
}
