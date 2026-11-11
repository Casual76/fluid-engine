package dev.antigravity.fluidengine.ui.compat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluidphysics.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Le firme pubbliche del vetro come le scrivono oggi le app e l'orologio, per esteso (piano «iOS in Android»,
 * 3.11 e 4.4; F1A.8).
 *
 * Perche' esiste. La 4.0.0 mette un materiale nuovo **sotto** `glassSurface` e `glassControlSurface` senza
 * cambiare una firma: e' la condizione perche' un'app aggiorni l'engine senza toccare una riga. Le app compilano
 * l'engine dai sorgenti, quindi la prova e' la compilazione: se una di queste chiamate smette di compilare,
 * questo file non compila, e il test fallisce prima ancora di girare.
 *
 * Cosa c'e':
 *  - `glassSurface` (17 parametri) e `glassControlSurface` (10), per nome, per posizione e nelle forme che le
 *    app usano davvero (lette nei repository delle app di Alessio a novembre 2026);
 *  - `fluidPhysicsSurface` (14), le funzioni del fondale, i `CompositionLocal` del vetro;
 *  - `GlassOptics` a 14 argomenti nominati (com'e' scritto in `engine-wear`), a 17 nominati e a 17 posizionali;
 *  - `GlassTint` a tre argomenti, nominati e posizionali, la sua destrutturazione, e i costruttori della 4.0;
 *  - ogni funzione di `GlassDefaults`, con i suoi argomenti;
 *  - le chiamate di `engine-wear` ricopiate com'erano (questo modulo non vede l'orologio: le sue forme sono qui);
 *  - la `FluidContextAction` posizionale a **sette** argomenti, che F5A dovra' tenere viva con un costruttore
 *    secondario quando aggiungera' campi prima di `onClick` (piano 3.11).
 *
 * Regole per chi lo tocca, come in `Compat307SourceTest`: gli import sono quelli di un'app (a stella sui pacchetti
 * pubblici); le funzioni @Composable non si eseguono, contano perche' compilano; i test qui sotto provano a quale
 * parametro arriva cio' che conta. Quando la 4.x aggiunge un parametro, la sua forma va **accanto** a queste,
 * non al posto.
 */

// --- glassSurface ------------------------------------------------------------------------------------

@Composable
internal fun GlassSurfaceCallsAsInThe3x(
  state: GlassBackdropState,
  exported: GlassBackdropState,
  tint: GlassTint,
  optics: GlassOptics,
  layer: (GraphicsLayerScope.() -> Unit)?,
): List<Modifier> = listOf(
  // Tutti e diciassette, per nome.
  Modifier.glassSurface(
    state = state,
    tint = tint,
    shape = RectangleShape,
    edge = GlassEdge.Bottom,
    falloff = GlassFalloff.Uniform,
    intensity = { 1f },
    role = GlassRole.Floating,
    optics = optics,
    opticalDepth = { 1f },
    pressed = { 0f },
    exports = exported,
    layerBlock = layer,
    sampleOnce = false,
    resampleIntervalMillis = 0L,
    frozen = { false },
    heldWhileMoving = { false },
    nestInContainer = true,
  ),
  // Tutti e diciassette, per posizione.
  Modifier.glassSurface(
    state, tint, RectangleShape, GlassEdge.None, GlassFalloff.Uniform, { 1f }, GlassRole.Modal, optics, { 1f }, { 0f },
    null, null, false, 33L, { false }, { false }, false,
  ),
  // Le forme minime.
  Modifier.glassSurface(state, tint),
  Modifier.glassSurface(state, tint, FluidCapsuleShape),
  Modifier.glassSurface(state = state, tint = tint),
  // Come le scrivono le app: ruolo e forma (Fluid-transit, PampAI), la fascia in alto con l'intensita'
  // (Fluidify, PampAI), il contenuto con il ricampionamento (FluidWeather), un'ottica di serie ritoccata
  // (Fluidify), il pannello che esporta il proprio materiale.
  Modifier.glassSurface(state = state, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape),
  Modifier.glassSurface(
    state = state,
    tint = GlassDefaults.floatingTint(),
    shape = ContinuousCornerShape(FluidRadius.Card),
    role = GlassRole.Modal,
    sampleOnce = true,
  ),
  Modifier.glassSurface(
    state = state,
    tint = GlassDefaults.barTint(),
    shape = RectangleShape,
    falloff = GlassFalloff.FadeDown,
    role = GlassRole.Bar,
    intensity = { 0.5f },
    resampleIntervalMillis = 100L,
  ),
  Modifier.glassSurface(
    state = state,
    tint = GlassDefaults.contentTint(),
    shape = ContinuousCornerShape(FluidRadius.Group),
    role = GlassRole.Content,
    sampleOnce = false,
    resampleIntervalMillis = 33L,
  ),
  Modifier.glassSurface(
    state = state,
    resampleIntervalMillis = 100L,
    tint = GlassDefaults.lightFloatingTint(),
    shape = ContinuousCornerShape(FluidRadius.Sheet),
    role = GlassRole.Modal,
    optics = GlassDefaults.optics(GlassRole.Modal).copy(blurScale = 7f),
    exports = exported,
  ),
  Modifier.glassSurface(
    state = state,
    tint = GlassDefaults.panelTint(),
    shape = FluidCapsuleShape,
    role = GlassRole.Floating,
    optics = GlassDefaults.panelOptics(overSharpPage = true),
  ),
)

// --- glassControlSurface -----------------------------------------------------------------------------

@Composable
internal fun GlassControlSurfaceCallsAsInThe3x(
  backdrop: GlassBackdropState,
  tint: GlassTint,
  optics: GlassOptics,
  press: FluidPressState,
): List<Modifier> = listOf(
  // Tutti e dieci, per nome.
  Modifier.glassControlSurface(
    backdrop = backdrop,
    shape = FluidCapsuleShape,
    tint = tint,
    optics = optics,
    selected = true,
    interactive = true,
    depth = { 1f },
    frozen = { false },
    heldWhileMoving = { false },
    pressState = press,
  ),
  // Tutti e dieci, per posizione.
  Modifier.glassControlSurface(backdrop, FluidCapsuleShape, tint, optics, false, true, { 1f }, { false }, { false }, null),
  // Le forme minime e quelle delle app (FluidWeather, MyCloud Gallery, Fluidify).
  Modifier.glassControlSurface(backdrop),
  Modifier.glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape),
  Modifier.glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape, interactive = false),
  Modifier.glassControlSurface(
    tint = GlassDefaults.controlTint(),
    backdrop = backdrop,
    shape = FluidCapsuleShape,
    optics = GlassDefaults.optics(GlassRole.Interactive).copy(blurScale = 0f, backdropResolution = 0.4f),
    selected = false,
    interactive = true,
  ),
)

// --- fluidPhysicsSurface, il fondale, i CompositionLocal ----------------------------------------------

@Composable
internal fun FluidPhysicsSurfaceCallsAsInThe3x(
  state: FluidPhysicsState,
  backdrop: GlassBackdropState,
  exported: GlassBackdropState,
  press: FluidPressState,
): List<Modifier> = listOf(
  Modifier.fluidPhysicsSurface(
    state = state,
    backdrop = backdrop,
    tint = GlassDefaults.floatingTint(),
    role = GlassRole.Floating,
    optics = GlassDefaults.optics(GlassRole.Floating),
    tier = FluidPhysicsTier.Full,
    intensity = { 1f },
    tintFrom = GlassDefaults.controlTint(),
    tintBlend = { 1f },
    opticsFrom = GlassDefaults.buttonOptics(),
    opticsBlend = { 1f },
    sampleOnce = false,
    exports = exported,
    pressState = press,
  ),
  Modifier.fluidPhysicsSurface(state, backdrop, GlassDefaults.floatingTint()),
  // Il palco del Playground: un'ottica di serie con la lente piu' forte.
  Modifier.fluidPhysicsSurface(
    state = state,
    backdrop = backdrop,
    tint = GlassDefaults.floatingTint(),
    role = GlassRole.Floating,
    optics = GlassDefaults.optics(GlassRole.Floating).copy(
      refractionHeight = 24.dp,
      refractionAmount = 40.dp,
      depthEffect = true,
      highlightWidth = 1.2.dp,
      highlightAlpha = 0.75f,
    ),
    tier = null,
    pressState = null,
  ),
)

@Composable
internal fun BackdropCallsAsInThe3x(material: GlassBackdropState): List<Any> {
  val page = rememberGlassBackdrop()
  val wide = rememberGlassBackdrop(blurRadius = 4.dp)
  val empty = rememberEmptyGlassBackdrop()
  val current = currentGlassBackdrop()
  val canvas: GlassBackdropState? = currentCanvasBackdrop() ?: LocalFluidCanvasBackdrop.current
  val combined = rememberCombinedGlassBackdrop(behind = page, inFront = material)
  val combinedPositional = rememberCombinedGlassBackdrop(page, material, 2.dp)
  val side: Boolean? = LocalFluidSurfaceSide.current
  val contrast: Float = LocalFluidSystemContrast.current
  val supported: Boolean = page.supported && page.refractionSupported
  return listOfNotNull(
    page, wide, empty, current, canvas, combined, combinedPositional, side, contrast, supported,
    Modifier.glassBackdropSource(page),
    Modifier.glassBackdropSource(page) { false },
    Modifier.glassBackdropSource(state = page, frozen = { true }),
    Modifier.glassContainer(material),
  )
}

// --- GlassOptics, GlassTint ----------------------------------------------------------------------------

/** L'ottica dell'orologio com'e' scritta in `FluidWearGlass`: quattordici argomenti nominati, gli altri di serie. */
internal val WearOpticsAsWritten: GlassOptics = GlassOptics(
  blurScale = 1.6f,
  refractionHeight = 12.dp,
  refractionAmount = 16.dp,
  depthEffect = true,
  dispersion = false,
  vibrancy = 1.5f,
  highlightWidth = 0.8.dp,
  highlightAlpha = 0.6f,
  highlightAngle = 90f,
  innerShadowRadius = 0.dp,
  innerShadowAlpha = 0f,
  shadowRadius = 14.dp,
  shadowAlpha = 0.55f,
  pressedDepthBoost = 0.5f,
)

/** Tutti e diciassette, per posizione: l'ordine dei campi e' parte dell'API. */
internal val OpticsPositional: GlassOptics =
  GlassOptics(1.6f, 12.dp, 16.dp, true, false, 1.5f, 0.8.dp, 0.6f, 90f, 0.dp, 0f, 14.dp, 0.55f, 0.5f, 0f, 1f, 1f)

/** Tutti e diciassette, per nome. */
internal val OpticsNamed: GlassOptics = GlassOptics(
  blurScale = 1.6f,
  refractionHeight = 12.dp,
  refractionAmount = 16.dp,
  depthEffect = true,
  dispersion = false,
  vibrancy = 1.5f,
  highlightWidth = 0.8.dp,
  highlightAlpha = 0.6f,
  highlightAngle = 90f,
  innerShadowRadius = 0.dp,
  innerShadowAlpha = 0f,
  shadowRadius = 14.dp,
  shadowAlpha = 0.55f,
  pressedDepthBoost = 0.5f,
  edgeFloor = 0f,
  backdropResolution = 1f,
  sizeResponse = 1f,
)

/** Le tre tinte dell'orologio, com'e' scritto in `FluidWearGlass`: tre argomenti nominati. */
internal fun wearTintsAsWritten(accent: Color): List<GlassTint> = listOf(
  GlassTint(
    overlay = androidx.compose.ui.graphics.lerp(Color.Black, accent, 0.16f).copy(alpha = 0.42f),
    fallback = androidx.compose.ui.graphics.lerp(Color(0xFF141416), accent, 0.16f).copy(alpha = 0.95f),
    hairline = Color.White.copy(alpha = 0.18f),
  ),
  GlassTint(
    overlay = androidx.compose.ui.graphics.lerp(Color(0xFF4A4A4E), accent, 0.16f).copy(alpha = 0.36f),
    fallback = androidx.compose.ui.graphics.lerp(Color(0xFF141416), accent, 0.16f).copy(alpha = 0.95f),
    hairline = Color.White.copy(alpha = 0.18f),
  ),
  GlassTint(
    overlay = androidx.compose.ui.graphics.lerp(Color(0xFF141416), accent, 0.16f).copy(alpha = 0.88f),
    fallback = androidx.compose.ui.graphics.lerp(Color(0xFF141416), accent, 0.16f).copy(alpha = 0.95f),
    hairline = Color.White.copy(alpha = 0.18f),
  ),
)

/** Le forme di `GlassTint` che compilano dalla 3.x, e quelle che la 4.0 aggiunge accanto. */
internal fun tintFormsAsInThe3x(): List<GlassTint> {
  val positional = GlassTint(Color.White.copy(alpha = 0.4f), Color.White, Color.Black.copy(alpha = 0.2f))
  val named = GlassTint(overlay = Color.Black.copy(alpha = 0.3f), fallback = Color.Black, hairline = Color.Transparent)
  val (overlay, fallback, hairline) = positional
  val copied = named.copy(overlay = overlay, fallback = fallback, hairline = hairline)
  // 4.0: il modo, per posizione e per nome.
  val withMode = GlassTint(overlay, fallback, hairline, GlassTintMode.Neutral)
  val namedMode = GlassTint(overlay = overlay, fallback = fallback, hairline = hairline, mode = GlassTintMode.Colored)
  return listOf(positional, named, copied, withMode, namedMode, named.copy(mode = GlassTintMode.Fixed))
}

// --- GlassDefaults --------------------------------------------------------------------------------------

internal val opticsReference: (GlassRole) -> GlassOptics = GlassDefaults::optics

@Composable
internal fun GlassDefaultsCallsAsInThe3x(colour: Color): List<Any> = listOf(
  GlassDefaults.optics(GlassRole.Bar),
  GlassDefaults.optics(role = GlassRole.Content),
  GlassDefaults.buttonOptics(),
  GlassDefaults.stackedContentOptics(),
  GlassDefaults.panelOptics(),
  GlassDefaults.panelOptics(compact = true),
  GlassDefaults.panelOptics(overSharpPage = true),
  GlassDefaults.panelOptics(true, true),
  GlassDefaults.barTint(),
  GlassDefaults.floatingTint(),
  GlassDefaults.controlTint(),
  GlassDefaults.selectionTint(),
  GlassDefaults.contentTint(),
  GlassDefaults.stackedContentTint(),
  GlassDefaults.modalTint(),
  GlassDefaults.tintedModalTint(colour),
  GlassDefaults.panelTint(),
  GlassDefaults.panelTint(colour),
  GlassDefaults.panelTint(colour = null),
  GlassDefaults.darkBarTint(),
  GlassDefaults.darkFloatingTint(),
  GlassDefaults.lightBarTint(),
  GlassDefaults.lightFloatingTint(),
  GlassDefaults.barTintOnPhoto(),
  GlassDefaults.floatingTintOnPhoto(),
  GlassDefaults.isDarkSurface(),
  // Il contrasto di sistema: una volta, sulla tinta che l'app si costruisce da se'.
  GlassTint(Color.White.copy(alpha = 0.5f), Color.White, Color.Transparent).forSystemContrast(),
  GlassDefaults.barTint().forContrast(FluidSystemContrast.High),
  // Una tinta di serie col tono dell'app: la forma che la regola dei modi deve reggere senza toccarla.
  GlassDefaults.barTint().copy(overlay = colour.copy(alpha = 0.3f)),
  GlassDefaults.controlTint().copy(overlay = colour.copy(alpha = 0.5f), fallback = colour, hairline = Color.Transparent),
)

// --- engine-wear ------------------------------------------------------------------------------------------

/**
 * Le chiamate al vetro di `engine-wear`, com'erano: il modulo dell'orologio non e' visibile da qui, quindi le sue
 * forme sono ricopiate. Il tema dell'orologio chiede il vetro di prima (`FluidWearTheme`, F1A.8), e la forma di
 * quella richiesta e' l'ultima.
 */
@Composable
internal fun WearCallsAsWritten(backdrop: GlassBackdropState, press: FluidPressState, accent: Color): List<Modifier> {
  val (smoke, page, notice) = wearTintsAsWritten(accent)
  val wide = WearOpticsAsWritten.copy(depthEffect = false)
  val modifiers = mutableListOf(
    // FluidWearRows: la pillola di una riga.
    Modifier.glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape, tint = page, optics = wide, pressState = press),
    // FluidWearToast: un avviso senza fondale.
    Modifier.glassSurface(
      state = rememberEmptyGlassBackdrop(),
      tint = notice,
      shape = FluidCapsuleShape,
      role = GlassRole.Floating,
      optics = wide,
    ),
    // FluidGlassBadge, FluidGlassCapsule, FluidGlassTimePill.
    Modifier.glassSurface(state = backdrop, tint = smoke, shape = FluidCapsuleShape, role = GlassRole.Interactive, optics = WearOpticsAsWritten),
    Modifier.glassSurface(state = backdrop, tint = smoke, shape = FluidCapsuleShape, role = GlassRole.Floating, optics = wide),
    // FluidGlassDisc: il disco del trasporto.
    Modifier.glassControlSurface(
      backdrop = backdrop,
      shape = FluidCapsuleShape,
      tint = smoke,
      optics = WearOpticsAsWritten,
      selected = false,
      interactive = true,
      pressState = press,
    ),
  )
  CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Classic) {
    modifiers += Modifier.glassSurface(state = backdrop, tint = smoke)
  }
  return modifiers
}

// --- FluidContextAction ------------------------------------------------------------------------------------

/** Le forme della 3.x: per nome, per posizione a sette, con la lambda in coda. */
internal fun contextActionsAsInThe3x(log: MutableList<String>): List<FluidContextAction> = listOf(
  // Sette, per posizione: label, icon, destructive, enabled, startsSection, selected, onClick.
  FluidContextAction("Sette", null, false, true, false, false, { log += "sette" }),
  FluidContextAction("Sette e icona", Icons.Rounded.Delete, true, false, true, true, { log += "icona" }),
  // Sette, per nome.
  FluidContextAction(
    label = "Nomi",
    icon = null,
    destructive = false,
    enabled = true,
    startsSection = false,
    selected = false,
    onClick = { log += "nomi" },
  ),
  // La lambda in coda.
  FluidContextAction("Coda") { log += "coda" },
  FluidContextAction("Coda", Icons.Rounded.Delete) { log += "coda e icona" },
  FluidContextAction("Coda", destructive = true) { log += "coda distruttiva" },
  FluidContextAction("Sei e coda", null, false, true, true, false) { log += "sei e coda" },
)

class GlassCompatSignaturesTest {

  @Test
  fun theWatchOpticsKeepTheirDefaultsForTheThreeFieldsTheyDoNotWrite() {
    // Quattordici nominati: i tre dopo restano ai default di sempre, quindi coincide con i diciassette scritti.
    assertEquals(OpticsNamed, WearOpticsAsWritten)
    assertEquals(OpticsNamed, OpticsPositional)
    assertEquals(0f, WearOpticsAsWritten.edgeFloor, 0f)
    assertEquals(1f, WearOpticsAsWritten.backdropResolution, 0f)
    assertEquals(1f, WearOpticsAsWritten.sizeResponse, 0f)
  }

  @Test
  fun aThreeArgumentTintIsStillThePaintedFilm() {
    // Il default del quarto campo e' il comportamento di sempre: la pellicola dipinta com'e', anche nel
    // materiale nuovo. Le tinte dell'orologio e quelle scritte a mano dalle app sono di questa forma.
    val accent = Color(0xFF2ED8A0)
    for (tint in wearTintsAsWritten(accent) + tintFormsAsInThe3x().take(3)) {
      assertEquals(GlassTintMode.Fixed, tint.mode)
    }
    val (positional, named, copied) = tintFormsAsInThe3x()
    assertEquals(positional.overlay, copied.overlay)
    assertEquals(positional.fallback, copied.fallback)
    assertEquals(positional.hairline, copied.hairline)
    assertFalse(named == positional)
  }

  @Test
  fun theSevenArgumentContextActionStillPutsTheLambdaInOnClick() {
    val log = mutableListOf<String>()
    val actions = contextActionsAsInThe3x(log)
    actions.forEach { it.onClick() }
    assertEquals(
      listOf("sette", "icona", "nomi", "coda", "coda e icona", "coda distruttiva", "sei e coda"),
      log,
    )
    val seven = actions[1]
    assertEquals("Sette e icona", seven.label)
    assertTrue(seven.destructive)
    assertFalse(seven.enabled)
    assertTrue(seven.startsSection)
    assertTrue(seven.selected)
    assertNull(actions[0].icon)
    // `copy` per nome, la forma garantita dal piano.
    assertEquals("Altro", actions[0].copy(label = "Altro").label)
  }

  @Test
  fun theRolesFalloffsAndEdgesAreTheOnesOfThe3x() {
    assertEquals(listOf("Bar", "Floating", "Interactive", "Modal", "Content"), GlassRole.entries.map { it.name })
    assertEquals(listOf("Uniform", "FadeDown"), GlassFalloff.entries.map { it.name })
    assertEquals(listOf("None", "Bottom", "Top"), GlassEdge.entries.map { it.name })
    assertEquals(GlassDefaults.optics(GlassRole.Floating), opticsReference(GlassRole.Floating))
  }
}
