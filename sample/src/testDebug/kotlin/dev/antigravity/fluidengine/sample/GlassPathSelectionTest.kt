package dev.antigravity.fluidengine.sample

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassModelReason
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassWarmUpState
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il percorso del vetro a ogni livello di API (piano «iOS in Android», 4.4, F1A.7): **quale** materiale prendono un
 * tasto, una capsula, un menu e una superficie di Fluid-physics, letto dalla diagnostica dell'engine, e che sotto
 * Android 13 tutto si disegni senza cadere.
 *
 * Il piano (4.4, da R2 §9) dava per scontato che Robolectric non esegua AGSL. Sul livello 35 il test non si affida
 * comunque a ciò che la sonda di salute fa lì: forza i verdetti con `FluidGlassDiagnostics.forceShaderVerdictsForTesting`.
 * Sani, le tre lastre risultano `Liquid` (e non si disegnano); rifiutati, il caso «un driver che rifiuta», tutto
 * risulta `Classic` e si disegna. Un test a parte, senza ganci, scopre che cosa prendono **davvero** i test della
 * galleria sul livello 35, e lo scrive nell'uscita del test: in modo grafico `NATIVE` la sonda compila corpo e luce
 * (F1A.7, prima corsa), quindi la galleria prende `Liquid`, e Robolectric dichiara la finestra accelerata.
 *
 * Il disegno sotto Android 13 è quello di Robolectric (Skia sul processore): prova che i rami senza AGSL non
 * lanciano, non che disegnino come su un Android 12 vero (NV, piano 4.4).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 31, 32, 35], qualifiers = "w400dp-h900dp-xhdpi")
class GlassPathSelectionTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
  }

  /** Una pagina di righe colorate, sotto: il vetro ha qualcosa da rifrangere, e il disegno qualcosa da mostrare. */
  private val stripes = Brush.verticalGradient(
    0f to Color(0xFF1E88E5),
    0.33f to Color(0xFFFDD835),
    0.66f to Color(0xFFE53935),
    1f to Color(0xFF43A047),
  )

  /** Il tasto, la capsula, il menu e la superficie fisica, sopra la pagina. */
  @Composable
  private fun Scene(withPhysics: Boolean = true) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val backdrop = rememberGlassBackdrop()
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(stripes))
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
          Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            FluidGlassIconButton(onClick = {}) {
              Box(Modifier.size(22.dp).background(Color.Black))
            }
            Box(
              Modifier
                .size(156.dp, 56.dp)
                .glassSurface(state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape),
            )
            Box(
              Modifier
                .size(250.dp, 220.dp)
                .glassSurface(
                  state = backdrop,
                  tint = GlassDefaults.panelTint(),
                  shape = ContinuousCornerShape(28.dp),
                  role = GlassRole.Modal,
                  optics = GlassDefaults.panelOptics(compact = true),
                ),
            )
            if (withPhysics) {
              // 120 dp a densità 2: un cerchio di 120 px di raggio, la forma di riposo del Playground.
              val physics = rememberFluidPhysicsState(FluidForm.circle(Offset(120f, 120f), 120f))
              Box(
                Modifier
                  .size(120.dp)
                  .fluidPhysicsSurface(state = physics, backdrop = backdrop, tint = GlassDefaults.floatingTint()),
              )
            }
          }
        }
      }
    }
  }

  private val sdk get() = Build.VERSION.SDK_INT

  private fun liquidSurfaces() = FluidGlassDiagnostics.liveSurfaces.getValue(FluidGlassModel.Liquid)

  private fun classicSurfaces() = FluidGlassDiagnostics.liveSurfaces.getValue(FluidGlassModel.Classic)

  private fun choices(reason: FluidGlassModelReason) = FluidGlassDiagnostics.liveModelChoices.getValue(reason)

  private fun slabWarmUp() = FluidGlassDiagnostics.warmUp.first { it.family == "Slab" }

  private fun report(what: String) {
    println(
      "GlassPathSelection [API $sdk] $what: superfici ${FluidGlassDiagnostics.liveSurfaces}, " +
        "scelte ${FluidGlassDiagnostics.liveModelChoices.filterValues { it != 0 }}, " +
        "nodi ${FluidGlassDiagnostics.liveNodes}, layer ${FluidGlassDiagnostics.liveLayers}, " +
        "verdetti ${FluidGlassDiagnostics.shaderVerdicts.mapValues { it.value?.javaClass?.simpleName ?: "compila" }}, " +
        "riscaldamento ${FluidGlassDiagnostics.warmUp}",
    )
  }

  /** Disegna la finestra in un bitmap e restituisce quanti colori diversi ci sono: zero vuol dire niente disegnato. */
  private fun drawnColours(): Int {
    var colours = 0
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      val pixels = IntArray(bitmap.width * bitmap.height)
      bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      colours = pixels.filter { android.graphics.Color.alpha(it) > 0 }.toSet().size
      bitmap.recycle()
    }
    return colours
  }

  @Test
  fun `sotto Android 13 ogni vetro e' quello di prima, e si disegna`() {
    assumeTrue("Il ramo senza AGSL", sdk < Build.VERSION_CODES.TIRAMISU)
    assertFalse(isRuntimeShaderSupported())
    assertEquals(sdk >= Build.VERSION_CODES.S, isRenderEffectSupported())
    compose.setContent { Scene() }
    compose.waitForIdle()
    report("senza ganci")

    assertEquals(0, liquidSurfaces())
    assertEquals(0, choices(FluidGlassModelReason.Liquid))
    // Chi chiede il modello (il tasto sempre, capsula e menu da Android 12) si sente dire «API».
    val asked = FluidGlassDiagnostics.liveModelChoices.filterValues { it != 0 }
    assertTrue("Qualcuno deve aver chiesto il modello: $asked", asked.isNotEmpty())
    assertEquals(setOf(FluidGlassModelReason.ApiLevel), asked.keys)
    if (sdk >= Build.VERSION_CODES.S) {
      // Tasto, capsula e menu passano da `drawBackdrop` con il vetro di prima; la superficie fisica pure.
      assertTrue("superfici Classic: ${classicSurfaces()}", classicSurfaces() >= 3)
    }
    // Sotto Android 13 il riscaldamento non parte nemmeno.
    assertEquals(FluidGlassWarmUpState.Idle, slabWarmUp().state)

    val colours = drawnColours()
    assertTrue("Il fotogramma deve contenere qualcosa: $colours colori", colours > 1)
  }

  @Test
  fun `su Android 13 con gli shader sani tasto, capsula e menu sono Liquid`() {
    assumeTrue("Il materiale nuovo", sdk >= Build.VERSION_CODES.TIRAMISU)
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.setContent { Scene() }
    compose.waitForIdle()
    report("verdetti sani")

    // Una scelta per superficie: il tasto la fa una volta sola, perché da `Liquid` disegna da sé.
    assertEquals(3, choices(FluidGlassModelReason.Liquid))
    assertEquals(3, FluidGlassDiagnostics.liveModelChoices.values.sum())
    // Da F1B.1 anche la superficie di Fluid-physics disegna col materiale nuovo (a `Full`, con i due corpi sani):
    // quattro superfici `Liquid`, nessuna di prima. La sua scelta passa da `resolvePhysicsGlass`, che non si conta
    // fra quelle del modello: le scelte restano tre.
    assertEquals(4, liquidSurfaces())
    assertEquals(0, classicSurfaces())
    // Il riscaldamento è stato chiesto (Robolectric dichiara la finestra accelerata: aspetta il momento fermo).
    val warm = slabWarmUp()
    assertTrue("Il riscaldamento della lastra va chiesto: $warm", warm.state != FluidGlassWarmUpState.Idle)
    println("GlassPathSelection [API $sdk] finestra accelerata: ${compose.activity.window.decorView.isHardwareAccelerated}")
  }

  /**
   * Il riscaldamento vero, dalla richiesta alla fine, con l'orologio di Robolectric che avanza: dopo il primo
   * fotogramma, a finestra ferma, i due programmi della lastra disegnati fuori schermo; poi una ricomposizione non
   * lo fa ripartire. Senza ganci: in modo `NATIVE` Robolectric compila AGSL (lo dice il test qui sopra), quindi il
   * disegno fuori schermo usa gli shader veri.
   *
   * Prova il **quando** e che niente lanci; non prova la GPU (Robolectric disegna sul processore): quella è del
   * gemello sul dispositivo. Se il `HardwareRenderer` di Robolectric non regge, la famiglia finisce `Failed` con il
   * motivo, e il test lo scrive invece di fallire: è proprio il ripiego che il riscaldamento deve avere.
   */
  @Test
  fun `il riscaldamento parte a finestra ferma, finisce, e non riparte`() {
    assumeTrue(sdk >= Build.VERSION_CODES.TIRAMISU)
    val generation = mutableIntStateOf(0)
    compose.setContent { key(generation.intValue) { Scene(withPhysics = false) } }
    compose.waitForIdle()
    val first = slabWarmUp()
    println("GlassPathSelection [API $sdk] riscaldamento dopo la composizione: $first")
    assumeTrue("Finestra non accelerata: niente da scaldare ($first)", first.state != FluidGlassWarmUpState.Skipped)
    assertTrue(first.toString(), first.state != FluidGlassWarmUpState.Idle)

    val looper = shadowOf(Looper.getMainLooper())
    repeat(12) { looper.idleFor(Duration.ofMillis(500)) }
    val after = slabWarmUp()
    println("GlassPathSelection [API $sdk] riscaldamento dopo 6 s fermi: $after")
    assertTrue(
      "Il riscaldamento deve arrivare in fondo: $after",
      after.state == FluidGlassWarmUpState.Done || after.state == FluidGlassWarmUpState.Failed,
    )
    if (after.state == FluidGlassWarmUpState.Done) assertEquals(after.programs, after.warmed)
    // Gli altri non l'ha chiesti nessuno.
    assertEquals(FluidGlassWarmUpState.Idle, FluidGlassDiagnostics.warmUp.first { it.family == "Group" }.state)

    // Una ricomposizione da capo (le superfici rinascono e richiedono) non lo fa ripartire.
    compose.runOnIdle { generation.intValue++ }
    compose.waitForIdle()
    repeat(4) { looper.idleFor(Duration.ofMillis(500)) }
    assertEquals(after.toString(), slabWarmUp().toString())
  }

  @Test
  fun `un compilatore che rifiuta lascia tutto il vetro di prima, e si disegna`() {
    assumeTrue("Il materiale nuovo", sdk >= Build.VERSION_CODES.TIRAMISU)
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.setContent { Scene() }
    compose.waitForIdle()
    report("verdetti rifiutati")

    assertEquals(0, liquidSurfaces())
    assertEquals(0, choices(FluidGlassModelReason.Liquid))
    // Quattro scelte, tutte «rifiutate»: il tasto chiede due volte (per sé, e `glassSurface` dentro di lui quando
    // resta `Classic`), capsula e menu una ciascuno.
    assertEquals(4, choices(FluidGlassModelReason.ShadersRefused))
    assertEquals(4, FluidGlassDiagnostics.liveModelChoices.values.sum())
    assertEquals(4, classicSurfaces())
    // Una lastra rifiutata non si scalda.
    assertEquals(FluidGlassWarmUpState.Idle, slabWarmUp().state)

    val colours = drawnColours()
    assertTrue("Il fotogramma deve contenere qualcosa: $colours colori", colours > 1)
  }

  /**
   * Che cosa prendono **oggi** i test Robolectric della galleria sul livello 35, senza ganci: la sonda vera prova a
   * costruire i `RuntimeShader` del corpo e della luce sotto Robolectric, e il modello segue il suo verdetto. Il
   * test non decide quale dei due sia giusto: controlla che modello e verdetti vadano d'accordo, e lo scrive.
   */
  @Test
  fun `che cosa prende oggi la galleria sotto Robolectric`() {
    assumeTrue(sdk >= Build.VERSION_CODES.TIRAMISU)
    compose.setContent { Scene(withPhysics = false) }
    compose.waitForIdle()
    val verdicts = FluidGlassDiagnostics.shaderVerdicts
    val body = verdicts["GlassBody1ShaderString"]
    val light = verdicts["GlassLight1ShaderString"]
    val healthy = verdicts.containsKey("GlassBody1ShaderString") && body == null && light == null
    println(
      "GlassPathSelection [API $sdk] SENZA GANCI: la galleria prende " +
        (if (healthy) "Liquid" else "Classic") + "; GlassBody1 = ${body ?: "compila"}, GlassLight1 = " +
        "${if (verdicts.containsKey("GlassLight1ShaderString")) light ?: "compila" else "non provata"}; " +
        "finestra accelerata: ${compose.activity.window.decorView.isHardwareAccelerated}",
    )
    report("senza ganci")
    if (healthy) {
      assertEquals(3, choices(FluidGlassModelReason.Liquid))
      assertEquals(3, liquidSurfaces())
    } else {
      assertEquals(0, liquidSurfaces())
      assertTrue(choices(FluidGlassModelReason.ShadersRefused) > 0)
    }
  }

  @Test
  fun `il livello simulato fa rispondere l'engine come Android 12 e 11, e solo in debug`() {
    assumeTrue("Si simula solo sotto il livello vero", sdk >= Build.VERSION_CODES.TIRAMISU)
    val context = compose.activity
    val info = context.applicationInfo
    val flags = info.flags
    try {
      info.flags = flags or ApplicationInfo.FLAG_DEBUGGABLE
      // Anche con gli shader sani: è il livello di API a decidere.
      FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)

      assertTrue(FluidGlassDiagnostics.simulateApiLevel(context, 32))
      assertEquals(32, FluidGlassDiagnostics.simulatedApiLevel)
      assertFalse(isRuntimeShaderSupported())
      assertTrue(isRenderEffectSupported())
      val generation = mutableIntStateOf(0)
      compose.setContent { key(generation.intValue) { Scene() } }
      compose.waitForIdle()
      report("simulato 32")
      assertEquals(0, liquidSurfaces())
      // Come su Android 12: tasto (due volte), capsula e menu si sentono dire «API».
      assertEquals(4, choices(FluidGlassModelReason.ApiLevel))
      assertEquals(4, FluidGlassDiagnostics.liveModelChoices.values.sum())
      assertTrue(drawnColours() > 1)

      // Vale per ciò che entra in composizione dopo: si ricompone tutto, come farebbe `recreate()`.
      assertTrue(FluidGlassDiagnostics.simulateApiLevel(context, 30))
      assertFalse(isRenderEffectSupported())
      compose.runOnIdle { generation.intValue++ }
      compose.waitForIdle()
      report("simulato 30")
      assertEquals(0, liquidSurfaces())
      // Come su Android 11: capsula e menu non chiedono più (il vetro è la tinta piena), il tasto sì.
      assertEquals(1, choices(FluidGlassModelReason.ApiLevel))
      assertTrue(drawnColours() > 1)

      // Mai sopra il vero, mai il vero.
      assertFalse(FluidGlassDiagnostics.simulateApiLevel(context, sdk))
      assertFalse(FluidGlassDiagnostics.simulateApiLevel(context, sdk + 1))
      assertEquals(30, FluidGlassDiagnostics.simulatedApiLevel)

      assertTrue(FluidGlassDiagnostics.simulateApiLevel(context, null))
      assertNull(FluidGlassDiagnostics.simulatedApiLevel)
      assertTrue(isRuntimeShaderSupported())
      compose.runOnIdle { generation.intValue++ }
      compose.waitForIdle()
      report("simulazione tolta")
      // Tasto, capsula, menu e, da F1B.1, la superficie di Fluid-physics.
      assertEquals(4, liquidSurfaces())

      // Un'app che non è `debuggable` non simula niente.
      info.flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
      assertFalse(FluidGlassDiagnostics.simulateApiLevel(context, 32))
      assertNull(FluidGlassDiagnostics.simulatedApiLevel)
      assertTrue(isRuntimeShaderSupported())
    } finally {
      info.flags = flags
    }
  }
}
