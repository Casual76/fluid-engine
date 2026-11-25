package dev.antigravity.fluidengine.net

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.FluidConditionReason
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.FluidThermal
import dev.antigravity.fluidengine.foundation.FluidWork
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** F8.20 · La lettura delle condizioni da Android: risparmio dati ed energetico, calore, corrente, e il posto solo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineConditionsTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @After fun reset() {
    EngineConditions.uninstall()
    FluidDeviceConditions.reset()
  }

  @Test fun `il risparmio dati conta solo su una rete che costa`() {
    val wifi = EngineNetworkState(connected = true, validated = true, metered = false)
    val cell = wifi.copy(metered = true)
    val saver = EngineDeviceSignals(restrictBackground = true)
    assertFalse(EngineConditions.conditionsOf(wifi, saver).dataSaver)
    assertTrue(EngineConditions.conditionsOf(cell, saver).dataSaver)
    assertEquals(
      listOf(FluidConditionReason.Metered, FluidConditionReason.DataSaver),
      EngineConditions.conditionsOf(cell, saver).allows(FluidWork.LargeDownload).reasons,
    )
  }

  @Test fun `il calore sui gradini di Apple`() {
    assertEquals(FluidThermal.Nominal, EngineConditions.thermalOf(PowerManager.THERMAL_STATUS_NONE))
    assertEquals(FluidThermal.Nominal, EngineConditions.thermalOf(PowerManager.THERMAL_STATUS_LIGHT))
    assertEquals(FluidThermal.Fair, EngineConditions.thermalOf(PowerManager.THERMAL_STATUS_MODERATE))
    assertEquals(FluidThermal.Serious, EngineConditions.thermalOf(PowerManager.THERMAL_STATUS_SEVERE))
    assertEquals(FluidThermal.Critical, EngineConditions.thermalOf(PowerManager.THERMAL_STATUS_CRITICAL))
    assertEquals(FluidThermal.Critical, EngineConditions.thermalOf(PowerManager.THERMAL_STATUS_SHUTDOWN))
  }

  @Test fun `i segnali letti da Android`() {
    val power = context.getSystemService(PowerManager::class.java)
    shadowOf(power).setIsPowerSaveMode(true)
    shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    shadowOf(connectivity).setRestrictBackgroundStatus(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
    @Suppress("DEPRECATION")
    context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC))
    val signals = EngineConditions.readSignals(context)
    assertTrue(signals.powerSave)
    assertTrue(signals.restrictBackground)
    assertTrue(signals.plugged)
    assertEquals(PowerManager.THERMAL_STATUS_SEVERE, signals.thermalStatus)
    val conditions = EngineConditions.current(context)
    assertTrue(conditions.known)
    assertTrue(conditions.powerSave)
    assertTrue(conditions.charging)
    assertTrue(conditions.hot)
    // In carica il risparmio energetico non ferma un download, il calore si'.
    assertEquals(FluidConditionReason.Hot, conditions.allows(FluidWork.LargeDownload).reasons.last())
  }

  @Test fun `install tiene aggiornato il posto solo`() {
    assertFalse(FluidDeviceConditions.current.known)
    EngineConditions.install(context)
    EngineConditions.install(context) // la seconda non fa niente
    assertTrue(EngineConditions.isInstalled)
    val deadline = System.currentTimeMillis() + 5_000
    while (!FluidDeviceConditions.current.known && System.currentTimeMillis() < deadline) {
      shadowOf(android.os.Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    assertTrue(FluidDeviceConditions.current.known)
    EngineConditions.uninstall()
    assertFalse(EngineConditions.isInstalled)
  }
}
