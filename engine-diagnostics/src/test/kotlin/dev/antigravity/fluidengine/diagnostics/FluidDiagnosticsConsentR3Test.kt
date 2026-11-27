package dev.antigravity.fluidengine.diagnostics

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R3 (sospetto n. 4 della review, confermato da R3-M4) · Il consenso alla diagnostica si salva con un nome stabile, non
 * col posto dell'enum: un riordino di `FluidDiagnosticsConsent` non può far diventare un «no» un «sì». Il numero salvato
 * prima si migra senza perderlo; un valore che non si legge conta come «no», mai come «sì».
 */
@RunWith(RobolectricTestRunner::class)
class FluidDiagnosticsConsentR3Test {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var directory: File

  /** Le `SharedPreferences` della diagnostica, come le scrive il telefono. */
  private val preferences: SharedPreferences
    get() = context.getSharedPreferences(FluidDiagnostics.PreferencesName, Context.MODE_PRIVATE)

  /** Il valore salvato prima di R3: il posto dell'enum come numero. */
  private val oldKey = "consenso"

  /** Il valore salvato da R3: un nome fisso. */
  private val newKey = "consenso-scelta"

  private fun diagnostics() = FluidDiagnostics.create(
    context,
    FluidDiagnosticsConfig(appName = "Prova", endpoint = "https://diagnostica.example/v1/reports", crashes = false, systemExits = false, frames = false),
    transport = { _, _ -> 200 },
    clock = { 1_795_702_800_000L },
    io = Dispatchers.Unconfined,
    directory = directory,
  )

  @Before
  fun setUp() {
    directory = File(context.cacheDir, "diagnostica-consenso-r3").apply { deleteRecursively() }
    preferences.edit().clear().commit()
  }

  @After
  fun tearDown() {
    FluidDiagnostics.resetForTesting()
    directory.deleteRecursively()
    preferences.edit().clear().commit()
  }

  @Test
  fun theChoiceIsSavedWithAStableName() {
    diagnostics().setConsent(false)
    assertEquals("denied", preferences.getString(newKey, null))
    assertFalse("niente più il posto dell'enum", preferences.contains(oldKey))
    diagnostics().setConsent(true)
    assertEquals("granted", preferences.getString(newKey, null))
    assertEquals(FluidDiagnosticsConsent.Granted, diagnostics().consent.value)
    // «Chiedi di nuovo» toglie la scelta.
    diagnostics().askAgain()
    assertFalse(preferences.contains(newKey))
    assertEquals(FluidDiagnosticsConsent.NotAsked, diagnostics().consent.value)
  }

  @Test
  fun anOldNumberIsMigratedWithoutLosingIt() {
    // Prima di R3: 1 era il sì, 2 il no (il posto in `NotAsked, Granted, Denied`).
    for ((old, expected) in listOf(2 to FluidDiagnosticsConsent.Denied, 1 to FluidDiagnosticsConsent.Granted, 0 to FluidDiagnosticsConsent.NotAsked)) {
      preferences.edit().clear().putInt(oldKey, old).commit()
      assertEquals("il numero $old", expected, diagnostics().consent.value)
      assertFalse("il numero $old resta salvato", preferences.contains(oldKey))
      val wire = when (expected) {
        FluidDiagnosticsConsent.Granted -> "granted"
        FluidDiagnosticsConsent.Denied -> "denied"
        FluidDiagnosticsConsent.NotAsked -> null
      }
      assertEquals("il numero $old", wire, preferences.getString(newKey, null))
      // E al prossimo avvio è lo stesso.
      assertEquals("il numero $old dopo un riavvio", expected, diagnostics().consent.value)
    }
  }

  @Test
  fun anUnreadableValueCountsAsNo() {
    // Un numero che non è un posto dell'enum.
    preferences.edit().clear().putInt(oldKey, 7).commit()
    assertEquals(FluidDiagnosticsConsent.Denied, diagnostics().consent.value)
    assertEquals("denied", preferences.getString(newKey, null))
    // Un valore vecchio d'un altro tipo (prima faceva cadere l'app all'avvio con un ClassCastException).
    preferences.edit().clear().putString(oldKey, "si").commit()
    assertEquals(FluidDiagnosticsConsent.Denied, runCatching { diagnostics().consent.value }.getOrElse { it })
    // Un nome che non si conosce, o d'un altro tipo.
    preferences.edit().clear().putString(newKey, "granted-forse").commit()
    assertEquals(FluidDiagnosticsConsent.Denied, diagnostics().consent.value)
    preferences.edit().clear().putInt(newKey, 1).commit()
    assertEquals(FluidDiagnosticsConsent.Denied, runCatching { diagnostics().consent.value }.getOrElse { it })
    // Letto come no, non raccoglie.
    assertFalse(diagnostics().isCollecting)
  }
}
