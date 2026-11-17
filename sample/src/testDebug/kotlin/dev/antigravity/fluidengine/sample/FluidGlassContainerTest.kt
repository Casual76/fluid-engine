package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidConcentricRadiiState
import dev.antigravity.fluidengine.ui.fluid.FluidConcentricShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance
import dev.antigravity.fluidengine.ui.fluid.fluidConcentricRadii
import dev.antigravity.fluidengine.ui.fluid.fluidContainerShape
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidConcentricRadii
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il piano flottante nell'albero (4.0, piano «iOS in Android» F3.2): un `FluidGlassContainer` vero, con i suoi pezzi
 * composti, misurati e guidati dall'orologio dei fotogrammi.
 *
 * Che cosa prova, e che cosa no. Prova che i pezzi si iscrivono e si accoppiano **come dichiarato** quando l'app cambia
 * lo stato (lo stesso `id` si sposta, un `id` nuovo vicino nasce dal vicino, lontano compare per lente, chi se ne va
 * vicino confluisce), che l'**ordine dei modificatori** è quello del contratto (un `fluidGlassId` scritto dopo il vetro
 * non conta e avvisa), che a riposo **ogni grappolo ha una cattura** e il contenitore **una sonda sola**, che un tasto di
 * vetro dell'engine dentro il contenitore è un pezzo come gli altri, e che una forma concentrica si risolve contro il
 * contenitore di forma **nell'albero** (F3.5 l'aveva provata solo come aritmetica). Non prova come si vede: Robolectric
 * non disegna, e il passaggio di consegne riposo↔palco si guarda in S3.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidGlassContainerTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  /** Fa passare [ms] di tempo finto a fotogrammi da 16 ms, col filo principale al passo. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  /**
   * Scrive lo stato dal filo del test e lo consegna subito alla composizione (F3.2c). Senza `sendApplyNotifications` la
   * scrittura arriva alla composizione solo quando il gestore globale degli snapshot gira sul filo principale, e in
   * certe posizioni della suite (dopo altre classi Robolectric) non arrivava: il test passava da solo e cadeva in
   * compagnia (F3.6, F3.2b).
   */
  private fun change(block: () -> Unit) {
    block()
    Snapshot.sendApplyNotifications()
  }

  /** Una pagina con una sorgente da rifrangere e, sopra, [content]. */
  private fun scene(content: @Composable (GlassBackdropState) -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
          Box(Modifier.padding(24.dp)) { content(backdrop) }
        }
      }
    }
    compose.waitForIdle()
    advance(200)
  }

  private val backdropNodes: Int get() = FluidGlassDiagnostics.liveNodes.getValue(FluidGlassNodeKind.Backdrop)

  @Composable
  private fun Pieces(backdrop: GlassBackdropState, ns: FluidGlassNamespace, count: Int, spacing: Dp) {
    FluidGlassContainer(spacing = spacing) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
          Box(Modifier.size(44.dp).fluidGlassId(index, ns).fluidGlass(backdrop = backdrop))
        }
      }
    }
  }

  @Test
  fun `tre vetri vicini sono un grappolo con una cattura, e il contenitore ha una sonda sola`() {
    scene { backdrop -> Pieces(backdrop, rememberFluidGlassNamespace(), count = 3, spacing = 16.dp) }
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertEquals(rest.toString(), 3, rest.size)
    assertTrue(rest.toString(), rest.all { it.clusterSize == 3 })
    assertEquals("una cattura per il grappolo (${FluidGlassDiagnostics.liveNodes})", 1, backdropNodes)
    assertEquals("i pezzi non sondano: la sonda è del contenitore", 1, FluidGlassProbeDiagnostics.liveProbes)
  }

  @Test
  fun `con lo spacing stretto ogni vetro e' un grappolo suo, e la sonda resta una`() {
    scene { backdrop -> Pieces(backdrop, rememberFluidGlassNamespace(), count = 3, spacing = 0.dp) }
    val rest = FluidGlassContainerDiagnostics.lastRest
    assertTrue(rest.toString(), rest.size == 3 && rest.all { it.clusterSize == 1 })
    assertEquals("una cattura per vetro (${FluidGlassDiagnostics.liveNodes})", 3, backdropNodes)
    assertEquals(1, FluidGlassProbeDiagnostics.liveProbes)
  }

  @Test
  fun `l'app scrive solo lo stato, e i vetri si accoppiano come dichiarato`() {
    var two by mutableStateOf(false)
    var far by mutableStateOf(false)
    scene { backdrop ->
      val ns = rememberFluidGlassNamespace()
      FluidGlassContainer {
        Box {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).fluidGlassId("uno", ns).fluidGlass(backdrop = backdrop))
            if (two) Box(Modifier.size(44.dp).fluidGlassId("due", ns).fluidGlass(backdrop = backdrop))
          }
          if (far) {
            Box(Modifier.offset(x = 260.dp).size(44.dp).fluidGlassId("lontano", ns).fluidGlass(backdrop = backdrop))
          }
        }
      }
    }
    assertEquals("la prima misura non viaggia", 0L, FluidGlassContainerDiagnostics.totalJourneys)

    // Un tasto diventa due: «uno» resta lui, «due» nasce da «uno».
    change { two = true }
    advance(64)
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    val split = FluidGlassContainerDiagnostics.lastJourney
    assertTrue(split.toString(), split.any { it.fromId == "uno" && it.toId == "uno" && it.change == "Matched" })
    assertTrue(
      split.toString(),
      split.any { it.toId == "due" && it.change == "BornFromNeighbour" && it.bornFromId == "uno" },
    )
    // F3.8a: il contenuto di chi nasce entra col suo vetro, quello di chi resta non si tocca.
    val born = FluidGlassContainerDiagnostics.lastContents
    assertTrue(born.toString(), born.any { it.id == "due" && it.role == "Born" && !it.departed })
    assertTrue(born.toString(), born.any { it.id == "uno" && it.role == "Rest" && !it.departed })
    advance(800)
    assertEquals(2, FluidGlassContainerDiagnostics.lastRest.size)

    // Lontano: compare per lente, al suo posto.
    change { far = true }
    advance(64)
    assertTrue(
      FluidGlassContainerDiagnostics.lastJourney.toString(),
      FluidGlassContainerDiagnostics.lastJourney.any { it.toId == "lontano" && it.change == "Materialize" },
    )
    // Chi compare per lente porta un simbolo che si condensa; i due fermi restano com'erano.
    val lens = FluidGlassContainerDiagnostics.lastContents
    assertTrue(lens.toString(), lens.any { it.id == "lontano" && it.role == "Lens" })
    assertTrue(lens.toString(), lens.filter { it.id != "lontano" }.all { it.role == "Rest" })
    advance(800)

    // «due» se ne va accanto a «uno»: vi confluisce.
    change { two = false }
    advance(64)
    assertTrue(
      FluidGlassContainerDiagnostics.lastJourney.toString(),
      FluidGlassContainerDiagnostics.lastJourney.any {
        it.fromId == "due" && it.change == "MergeIntoNeighbour" && it.mergesIntoId == "uno"
      },
    )
    // Il contenuto di chi se ne va esce da dov'è: lo porta il contenitore, perché il nodo non c'è più.
    val gone = FluidGlassContainerDiagnostics.lastContents
    assertTrue(gone.toString(), gone.any { it.id == "due" && it.role == "Exit" && it.departed })
    advance(800)
    assertEquals(
      setOf("uno", "lontano"),
      FluidGlassContainerDiagnostics.lastRest.map { it.id }.toSet(),
    )
  }

  @Test
  fun `un cambio a meta' viaggio fa ripartire il viaggio, non ne apre un altro dopo`() {
    var two by mutableStateOf(false)
    scene { backdrop ->
      val ns = rememberFluidGlassNamespace()
      FluidGlassContainer {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(Modifier.size(44.dp).fluidGlassId("uno", ns).fluidGlass(backdrop = backdrop))
          if (two) Box(Modifier.size(44.dp).fluidGlassId("due", ns).fluidGlass(backdrop = backdrop))
        }
      }
    }
    change { two = true }
    advance(96)
    change { two = false }
    advance(64)
    assertEquals(1L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(1L, FluidGlassContainerDiagnostics.totalRetargets)
    advance(800)
    assertEquals(listOf("uno"), FluidGlassContainerDiagnostics.lastRest.map { it.id })
  }

  @Test
  fun `l'ordine dei modificatori e' contratto, l'id scritto dopo il vetro non conta e avvisa`() {
    scene { backdrop ->
      val ns = rememberFluidGlassNamespace()
      FluidGlassContainer(spacing = 0.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
          Box(Modifier.size(44.dp).fluidGlassId("prima", ns).fluidGlass(backdrop = backdrop))
          Box(Modifier.size(44.dp).fluidGlass(backdrop = backdrop).fluidGlassId("dopo", ns))
        }
      }
    }
    val ids = FluidGlassContainerDiagnostics.lastRest.map { it.id }
    assertTrue(ids.toString(), "prima" in ids)
    assertTrue("«dopo» non è l'id di nessun vetro: $ids", "dopo" !in ids && null in ids)
    assertEquals(1L, FluidGlassContainerDiagnostics.totalOrderWarnings)
  }

  @Test
  fun `una forma concentrica si risolve contro il contenitore di forma nell'albero`() {
    scene { backdrop ->
      FluidGlassContainer {
        Box(Modifier.size(200.dp, 120.dp).fluidContainerShape(RoundedCornerShape(30.dp)).padding(8.dp)) {
          Box(Modifier.size(80.dp, 60.dp).fluidGlass(shape = FluidConcentricShape(minimum = 4.dp), backdrop = backdrop))
        }
      }
    }
    // xhdpi: 30 dp = 60 px di raggio, il pezzo a 8 dp (16 px) dal bordo in alto a sinistra: 60 - 16 = 44 px. Gli altri
    // tre angoli sono lontani dai lati del contenitore e stanno al minimo, 4 dp = 8 px.
    val radii = FluidGlassContainerDiagnostics.lastRest.single().cornerRadii
    assertEquals(radii.toString(), 44f, radii.topLeft, 0.5f)
    assertEquals(radii.toString(), 8f, radii.topRight, 0.5f)
    assertEquals(radii.toString(), 8f, radii.bottomRight, 0.5f)
    assertEquals(radii.toString(), 8f, radii.bottomLeft, 0.5f)
  }

  @Test
  fun `la via pubblica di F3_5 risolve nell'albero gli stessi raggi`() {
    // Il controllo dell'aggancio: `rememberFluidConcentricRadii` e `fluidConcentricRadii` sullo stesso albero, senza
    // il piano. Se questo regge e il pezzo del contenitore no, il difetto è nella delega del contenitore.
    var state: FluidConcentricRadiiState? = null
    scene { _ ->
      Box(Modifier.size(200.dp, 120.dp).fluidContainerShape(RoundedCornerShape(30.dp)).padding(8.dp)) {
        val radii = rememberFluidConcentricRadii(minimum = 4.dp)
        state = radii
        Box(Modifier.size(80.dp, 60.dp).fluidConcentricRadii(radii))
      }
    }
    val radii = state!!.radii
    assertEquals(radii.toString(), 44f, radii.topLeft, 0.5f)
    assertEquals(radii.toString(), 8f, radii.bottomRight, 0.5f)
  }

  @Test
  fun `un tasto di vetro dell'engine dentro il contenitore e' un pezzo, non sonda, e il contenuto ha il patto`() {
    var appearance: FluidGlassAppearance? = null
    var outside: FluidGlassAppearance? = null
    scene { _ ->
      outside = LocalFluidGlassAppearance.current
      val ns = rememberFluidGlassNamespace()
      FluidGlassContainer {
        appearance = LocalFluidGlassAppearance.current
        // L'id sta sul `modifier` del tasto, che lo posa sul riquadro del tocco: il tasto lo inoltra al suo vetro.
        FluidGlassIconButton(onClick = {}, modifier = Modifier.fluidGlassId("tasto", ns)) {}
      }
    }
    assertEquals(FluidGlassContainerDiagnostics.lastRest.toString(), 1, FluidGlassContainerDiagnostics.lastRest.size)
    assertEquals("tasto", FluidGlassContainerDiagnostics.lastRest.single().id)
    assertEquals("nessun avviso d'ordine: l'id è del vetro del tasto", 0L, FluidGlassContainerDiagnostics.totalOrderWarnings)
    assertEquals("il tasto non sonda: la sonda è del contenitore", 1, FluidGlassProbeDiagnostics.liveProbes)
    assertNull("fuori dal contenitore nessun vetro dà l'aspetto", outside)
    assertNotNull("dentro il contenitore il contenuto riceve l'aspetto del vetro", appearance)
  }

  @Test
  fun `un contenitore che cambia misura con gli stessi pezzi non viaggia, e i vetri lo seguono`() {
    // Giro di correzione di F3 (D R5): una rotazione senza ricreazione, lo schermo diviso, una riga che si allarga. Il
    // genitore dà al contenitore un posto nuovo, i pezzi si spostano col layout: nessun viaggio, nessuna luce, il riposo
    // alla misura nuova.
    var wide by mutableStateOf(false)
    scene { backdrop ->
      val ns = rememberFluidGlassNamespace()
      Box(Modifier.width(if (wide) 340.dp else 220.dp)) {
        FluidGlassContainer(Modifier.fillMaxWidth()) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(44.dp).fluidGlassId("sinistra", ns).fluidGlass(backdrop = backdrop))
            Box(Modifier.size(44.dp).fluidGlassId("destra", ns).fluidGlass(backdrop = backdrop))
          }
        }
      }
    }
    val before = FluidGlassContainerDiagnostics.lastRest.single { it.id == "destra" }.bounds
    change { wide = true }
    advance(64)
    assertEquals("nessun viaggio", 0L, FluidGlassContainerDiagnostics.totalJourneys)
    val after = FluidGlassContainerDiagnostics.lastRest.single { it.id == "destra" }.bounds
    // xhdpi: 120 dp in più sono 240 px.
    assertEquals("il vetro di destra è al posto nuovo: $before -> $after", before.left + 240f, after.left, 1f)
    change { wide = false }
    advance(64)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals(before.left, FluidGlassContainerDiagnostics.lastRest.single { it.id == "destra" }.bounds.left, 1f)
  }

  @Test
  fun `col vetro di prima il contenitore non viaggia e ogni pezzo e' il vetro di sempre`() {
    // Giro di correzione di F3 (B 9): `Classic` dentro un contenitore. Il contenitore c'è ma non disegna: nessun riposo
    // pubblicato, nessun viaggio, e un tasto che arriva si tocca subito (non è un pezzo del piano).
    var two by mutableStateOf(false)
    var clicks = 0
    scene { backdrop ->
      CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Classic) {
        val ns = rememberFluidGlassNamespace()
        FluidGlassContainer {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).fluidGlassId("uno", ns).fluidGlass(backdrop = backdrop))
            if (two) {
              FluidGlassIconButton(onClick = { clicks++ }, modifier = Modifier.fluidGlassId("due", ns).testTag("due")) {}
            }
          }
        }
      }
    }
    assertTrue("nessun riposo del piano: ${FluidGlassContainerDiagnostics.lastRest}", FluidGlassContainerDiagnostics.lastRest.isEmpty())
    change { two = true }
    advance(48)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalJourneys)
    compose.onNodeWithTag("due").performTouchInput { click(center) }
    advance(16)
    assertEquals("il tasto di sempre si tocca subito", 1, clicks)
    assertEquals(0L, FluidGlassContainerDiagnostics.totalOrderWarnings)
  }
}
