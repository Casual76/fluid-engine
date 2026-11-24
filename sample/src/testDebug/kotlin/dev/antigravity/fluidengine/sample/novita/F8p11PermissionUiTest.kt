package dev.antigravity.fluidengine.sample.novita

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFirstUseKeys
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidPermission
import dev.antigravity.fluidengine.ui.fluid.FluidPermissionState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidPermissionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPermission
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.11 · La macchina dei permessi col dito vero: la spiegazione che nasce dal tasto una volta sola, il dialogo di Android
 * (un registro dei risultati finto che risponde come Android), negato, negato per sempre con «Apri Impostazioni», il
 * ritorno dalle impostazioni che rilegge lo stato da solo, la memoria che distingue «mai chiesto» da «negato per sempre», e
 * le foto «solo alcune» di Android 14.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class F8p11PermissionUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Il dialogo di Android finto: ricorda cosa gli si chiede e risponde quando la prova lo dice. */
  private inner class FakeRegistry : ActivityResultRegistry() {
    val asked = mutableListOf<List<String>>()
    private var code = -1

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
      @Suppress("UNCHECKED_CAST")
      asked += (input as Array<String>).toList()
      code = requestCode
    }

    /** Android ha risposto: [granted] concessi, gli altri negati, con la spiegazione accesa se [rationale]. */
    fun answer(permissions: List<String>, granted: Set<String>, rationale: Boolean) {
      val app = shadowOf(compose.activity.application)
      val pm = shadowOf(compose.activity.packageManager)
      permissions.forEach { permission ->
        if (permission in granted) app.grantPermissions(permission) else app.denyPermissions(permission)
        pm.setShouldShowRequestPermissionRationale(permission, rationale && permission !in granted)
      }
      compose.runOnIdle { dispatchResult(code, permissions.associateWith { it in granted }) }
      settle()
    }
  }

  private val registry = FakeRegistry()
  private val owner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = registry
  }

  private fun settle() {
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
  }

  @Composable
  private fun Hosted(memory: FluidFirstUseMemory, content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(
        LocalFluidGlassModalHostState provides host,
        LocalFluidFirstUseMemory provides memory,
        LocalActivityResultRegistryOwner provides owner,
      ) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  private val reason = "Fluid Transit usa la posizione approssimativa per mostrarti le fermate qui vicino."

  @Test fun laMacchinaATuttiGliStati() {
    val memory = FluidFirstUseMemory.inMemory()
    val location = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    var launch by mutableIntStateOf(0)
    var ran = 0
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(memory) {
        key(launch) {
          state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason)
          FluidButton(
            text = "Fermate qui vicino",
            onClick = { state.request { ran++ } },
            modifier = Modifier.fluidPermissionSource(state),
          )
        }
      }
    }
    settle()
    assertEquals(FluidPermissionStatus.NeverAsked, state.status)

    // Il primo tocco: la spiegazione nasce dal tasto (l'avviso col perché), il dialogo di Android non ancora.
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    compose.onNodeWithText(reason).assertExists()
    compose.onNodeWithText("Consentire l'accesso alla posizione?").assertExists()
    assertTrue(registry.asked.isEmpty())
    assertEquals("1", runBlocking { memory.read(FluidFirstUseKeys.permissionExplained("location-coarse")) })

    // «Continua»: il dialogo di Android. Prima risposta: no (Android accende la spiegazione).
    compose.onNodeWithText("Continua").performClick()
    settle()
    assertEquals(listOf(location), registry.asked)
    registry.answer(location, granted = emptySet(), rationale = true)
    assertEquals(FluidPermissionStatus.Denied, state.status)
    assertEquals(0, ran)
    assertEquals("1", runBlocking { memory.read(FluidFirstUseKeys.permissionAsked("location-coarse")) })

    // Il secondo tocco: niente muro, il dialogo diretto. Secondo no: Android non chiederà più.
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    compose.onNodeWithText(reason).assertDoesNotExist()
    assertEquals(2, registry.asked.size)
    registry.answer(location, granted = emptySet(), rationale = false)
    assertEquals(FluidPermissionStatus.DeniedForever, state.status)

    // Un'apertura nuova dell'app: Android dice «non concesso, niente spiegazione» come per «mai chiesto»; la memoria no.
    launch = 1
    settle()
    assertEquals(FluidPermissionStatus.DeniedForever, state.status)

    // Il tocco porta alla spiegazione con «Apri Impostazioni», e da lì alla pagina dell'app.
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    compose.onNodeWithText("Accesso alla posizione non consentito").assertExists()
    compose.onNodeWithText("Apri Impostazioni").performClick()
    settle()
    val started = shadowOf(compose.activity).nextStartedActivity
    assertNotNull(started)
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
    assertEquals("package:${compose.activity.packageName}", started.dataString)
    assertEquals(2, registry.asked.size)

    // L'utente consente nelle impostazioni e torna: lo stato si rilegge da solo alla ripresa.
    shadowOf(compose.activity.application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    settle()
    assertEquals(FluidPermissionStatus.Granted, state.status)
    // E ora il tocco fa l'azione, subito.
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    assertEquals(1, ran)
  }

  @Test fun ilDialogoChiusoSenzaSceltaNonEUnNo() {
    val memory = FluidFirstUseMemory.inMemory()
    val location = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason, explain = false)
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    // Senza spiegazione: il dialogo subito.
    assertEquals(1, registry.asked.size)
    registry.answer(location, granted = emptySet(), rationale = false)
    assertEquals(FluidPermissionStatus.NeverAsked, state.status)
    assertEquals(null, runBlocking { memory.read(FluidFirstUseKeys.permissionAsked("location-coarse")) })
  }

  @Config(sdk = [34])
  @Test fun leFotoSoloAlcune() {
    val memory = FluidFirstUseMemory.inMemory()
    var ran = 0
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.Photos, reason = "Carica solo le foto che scegli.", explain = false)
        FluidButton("Scegli le foto", onClick = { state.request { ran++ } }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.onNodeWithText("Scegli le foto").performClick()
    settle()
    val photos = listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    assertEquals(listOf(photos), registry.asked)
    // «Solo alcune»: Android dà il permesso della scelta e non quello di tutte le foto.
    registry.answer(photos, granted = setOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED), rationale = false)
    assertEquals(FluidPermissionStatus.Partial, state.status)
    assertEquals(1, ran)
    // Il tocco dopo fa l'azione, e «scegline altre» riapre la scelta di Android.
    compose.onNodeWithText("Scegli le foto").performClick()
    settle()
    assertEquals(2, ran)
    compose.runOnIdle { state.requestMore() }
    assertEquals(2, registry.asked.size)
  }

  @Test fun ilTastoEUnTastoPerTalkBack() {
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(FluidFirstUseMemory.inMemory()) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason)
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button).and(hasText("Fermate qui vicino"))).assertExists()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    // La spiegazione è un avviso con titolo e due tasti raggiungibili.
    compose.onNodeWithText("Non ora").assertExists()
    compose.onNodeWithText("Continua").assertExists()
    compose.onNodeWithText("Non ora").performClick()
    settle()
    compose.onNodeWithText(reason).assertDoesNotExist()
    assertEquals(FluidPermissionStatus.NeverAsked, state.status)
  }
}
