package dev.antigravity.fluidengine.sample.material

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.sample.LoudCard
import dev.antigravity.fluidengine.sample.measure.MeasureClock
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBack
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.MoreVert
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidSyncAction
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Tutto ciò che una scena riceve dalla pagina. */
@Stable
internal class MaterialSession(
  val config: MaterialConfig,
  val clock: MeasureClock,
  val report: MaterialReport,
  /** Il fondo registrato: ciò che il vetro della scena rifrange. */
  val stage: GlassBackdropState,
  /** Il menu che si trasforma della pagina, con il suo host sopra la scena. */
  val menu: FluidMorphMenuState,
  /** Il dito finto; nullo dove non c'è una vista a cui consegnare i tocchi. */
  val finger: MaterialFinger?,
  /** Lo spazio che la barra delle schede della galleria copre in fondo. */
  val bottomInset: Dp,
  /** Lo spazio che il riquadro di stato copre in alto (zero con `material.hud off`). */
  val topInset: Dp,
)

/** La scena chiesta da [MaterialSession.config]. */
@Composable
internal fun MaterialSceneContent(session: MaterialSession) {
  when (session.config.scene) {
    MaterialScene.Components -> ModelSplit(session) { ComponentsCell(session) }
    MaterialScene.Edge -> ModelSplit(session) { EdgeCell(session) }
    MaterialScene.Corner -> ModelSplit(session) { CornerCell(session) }
    MaterialScene.Nested -> NestedScene(session)
    MaterialScene.Press -> PressScene(session)
    MaterialScene.Intensity -> ModelSplit(session) { IntensityCell(session) }
    MaterialScene.ListUnderBar -> ListScene(session)
    MaterialScene.Menu -> MenuScene(session)
    MaterialScene.Modal -> ModalScene(session)
    MaterialScene.Tints -> ModelSplit(session) { TintsCell(session) }
    MaterialScene.Apps -> ModelSplit(session) { AppsCell(session) }
    MaterialScene.Contrast -> ModelSplit(session) { ContrastCell(session) }
    MaterialScene.Rtl -> ModelSplit(session) { RtlCell(session) }
  }
}

// --- Le colonne dei due vetri -------------------------------------------------------------------------------

/** Da questa larghezza le due colonne stanno affiancate; sotto (formato telefono) una sopra l'altra. */
private val SplitMinWidth = 720.dp

/** Lo spazio dell'etichetta «Classic» / «Liquid» in cima a ogni colonna. */
private val LabelSpace = 36.dp

/**
 * Le lastre di [cell] una volta per ogni vetro di `material.model`, ognuna sotto il suo `LocalFluidGlassModel`:
 * affiancate sul tablet, una sopra l'altra in formato telefono. Lo stesso componente, due materiali, lo stesso fondo.
 */
@Composable
private fun ModelSplit(session: MaterialSession, cell: @Composable (FluidGlassModel) -> Unit) {
  val models = session.config.models.models
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(top = session.topInset, bottom = session.bottomInset),
  ) {
    if (models.size == 1 || maxWidth >= SplitMinWidth) {
      Row(modifier = Modifier.fillMaxSize()) {
        models.forEach { model -> ModelCell(model, Modifier.weight(1f).fillMaxHeight(), cell) }
      }
    } else {
      Column(modifier = Modifier.fillMaxSize()) {
        models.forEach { model -> ModelCell(model, Modifier.weight(1f).fillMaxWidth(), cell) }
      }
    }
  }
}

@Composable
private fun ModelCell(model: FluidGlassModel, modifier: Modifier, cell: @Composable (FluidGlassModel) -> Unit) {
  CompositionLocalProvider(LocalFluidGlassModel provides model) {
    Box(modifier = modifier.testTag("material-cell-${model.name.lowercase()}")) {
      cell(model)
      ModelLabel(model, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
    }
  }
}

/** L'etichetta della colonna, nera e bianca fissa: si legge su ogni fondo e non è vetro. */
@Composable
private fun ModelLabel(model: FluidGlassModel, modifier: Modifier = Modifier) {
  Text(
    text = model.name,
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = modifier
      .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(6.dp))
      .padding(horizontal = 10.dp, vertical = 3.dp),
  )
}

/** Una colonna che scorre, sotto l'etichetta: in formato telefono una scena può non starci. */
@Composable
private fun CellColumn(
  modifier: Modifier = Modifier,
  spacing: Dp = 16.dp,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(top = LabelSpace, bottom = 16.dp, start = 12.dp, end = 12.dp),
    verticalArrangement = Arrangement.spacedBy(spacing),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    content()
  }
}

/** Una didascalia sotto una lastra: piccola, nel colore del tema, perché si legga dove sta. */
@Composable
private fun Caption(text: String) {
  Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
}

// --- Componenti ---------------------------------------------------------------------------------------------

private val DemoTabs = listOf(
  FluidTabItem(route = "home", label = "Home", icon = FluidIcons.Home, selectedIcon = FluidIcons.Filled.Home),
  FluidTabItem(route = "search", label = "Cerca", icon = FluidIcons.Search, selectedIcon = FluidIcons.Filled.Search),
  FluidTabItem(route = "settings", label = "Altro", icon = FluidIcons.Settings, selectedIcon = FluidIcons.Filled.Settings),
)

private val DemoActions = listOf(
  FluidContextAction("Condividi", FluidIcons.Share) {},
  FluidContextAction("Aggiungi ai preferiti", FluidIcons.Filled.Favorite) {},
  FluidContextAction("Elimina", FluidIcons.Delete, destructive = true) {},
)

@Composable
private fun ComponentsCell(session: MaterialSession) {
  val config = session.config
  val report = session.report
  val backdrop = session.stage
  val tabScroll = rememberFluidFloatingTabBarScrollConnection()
  var tab by remember { mutableStateOf(DemoTabs.first().route) }
  CellColumn {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      FluidGlassIconButton(onClick = {}, modifier = Modifier.reported(report, config, "tondo")) {
        FluidIcon(FluidIcons.Filled.Star, contentDescription = "Preferito")
      }
      FluidGlassButton(text = "Scelto", onClick = {}, selected = true, modifier = Modifier.reported(report, config, "scelto"))
      FluidGlassButton(text = "Tasto", onClick = {}, modifier = Modifier.reported(report, config, "tasto"))
    }
    ThreeActionCapsule(session, onPhoto = false, name = "capsula")
    FluidFloatingTabBar(
      items = DemoTabs,
      selectedRoute = tab,
      onSelect = { tab = it.route },
      scrollConnection = tabScroll,
      backdrop = backdrop,
      modifier = Modifier.reported(report, config, "tabbar"),
    )
    FluidMorphMenuButton(
      state = session.menu,
      actions = { DemoActions },
      onClick = {},
      text = "Menu",
      icon = FluidIcons.MoreVert,
      backdrop = backdrop,
      opensOnTap = true,
      modifier = Modifier.reported(report, config, "menu"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      FluidCard(glass = true, modifier = Modifier.width(150.dp).reported(report, config, "card")) {
        Text("Card", style = MaterialTheme.typography.titleSmall)
        Text("Content, senza ombra", style = MaterialTheme.typography.bodySmall)
      }
      Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TintSlab(session, fixedAccentTint(), "Fixed", "fixed")
        TintSlab(session, coloredAccentTint(), "Colored", "colored")
      }
    }
    PanelSlab(session, "pannello", width = 320.dp, height = 132.dp) {
      Text("Pannello", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        "Ruolo Modal con l'ottica del pannello flottante, come il foglio dell'host dei modali.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** La capsula flottante vera con tre azioni, come la mette un'app quando qualcosa è selezionato. */
@Composable
private fun ThreeActionCapsule(session: MaterialSession, onPhoto: Boolean, name: String) {
  FluidFloatingCapsule(
    modifier = Modifier.reported(session.report, session.config, name),
    backdrop = session.stage,
    onPhoto = onPhoto,
  ) {
    FluidCapsuleAction(FluidIcons.Share, "Condividi", onClick = {})
    FluidCapsuleAction(FluidIcons.Filled.Favorite, "Preferito", onClick = {})
    FluidCapsuleAction(FluidIcons.Delete, "Elimina", onClick = {}, destructive = true)
  }
}

/** Una tinta scritta a mano, dipinta com'è (`GlassTintMode.Fixed`, il default del costruttore a tre argomenti). */
@Composable
private fun fixedAccentTint(): GlassTint {
  val accent = MaterialTheme.colorScheme.primary
  return remember(accent) {
    GlassTint(overlay = accent.copy(alpha = 0.36f), fallback = accent.copy(alpha = 0.92f), hairline = Color.Transparent)
  }
}

/** Una tinta alla Apple: l'accento sopra il velo del materiale, che segue ciò che il vetro ha dietro. */
@Composable
private fun coloredAccentTint(): GlassTint {
  val accent = MaterialTheme.colorScheme.primary
  return remember(accent) {
    GlassTint(
      overlay = accent.copy(alpha = 0.5f),
      fallback = accent.copy(alpha = 0.92f),
      hairline = Color.Transparent,
      mode = GlassTintMode.Colored,
    )
  }
}

/** Una capsula di vetro con una tinta data, e il nome scritto sopra. */
@Composable
private fun TintSlab(session: MaterialSession, tint: GlassTint, label: String, name: String, width: Dp = 112.dp) {
  Box(
    modifier = Modifier
      .size(width, 48.dp)
      .reported(session.report, session.config, name)
      .glassSurface(state = session.stage, tint = tint, shape = FluidCapsuleShape, role = GlassRole.Floating),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
  }
}

/** Un pannello di vetro (ruolo `Modal`, ottica del pannello flottante): quello che disegna il foglio dell'host. */
@Composable
private fun PanelSlab(
  session: MaterialSession,
  name: String,
  width: Dp,
  height: Dp,
  shape: Shape = ContinuousCornerShape(FluidRadius.Sheet),
  compact: Boolean = false,
  tint: GlassTint = GlassDefaults.panelTint(),
  content: @Composable () -> Unit = {},
) {
  Column(
    modifier = Modifier
      .size(width, height)
      .reported(session.report, session.config, name)
      .glassSurface(
        state = session.stage,
        tint = tint,
        shape = shape,
        role = GlassRole.Modal,
        optics = GlassDefaults.panelOptics(compact = compact),
      )
      .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    content()
  }
}

// --- Il bordo e l'angolo --------------------------------------------------------------------------------------

/**
 * I tre elementi del banco a taglia vera (scena `oggi-foto`): il tasto tondo vero da 44 dp, la capsula 156×56 e il
 * menu 170×192 con raggio 28. Si fotografano interi; i riquadri in logcat dicono dove ritagliarli a 6x.
 */
@Composable
private fun EdgeCell(session: MaterialSession) {
  val backdrop = session.stage
  CellColumn(spacing = 28.dp) {
    Spacer(Modifier.height(12.dp))
    FluidGlassIconButton(onClick = {}, modifier = Modifier.reported(session.report, session.config, "tondo")) {
      FluidIcon(FluidIcons.Add, contentDescription = "Aggiungi")
    }
    Box(
      modifier = Modifier
        .size(156.dp, 56.dp)
        .reported(session.report, session.config, "capsula")
        .glassSurface(state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape, role = GlassRole.Floating),
    )
    PanelSlab(session, "menu", width = 170.dp, height = 192.dp, shape = ContinuousCornerShape(28.dp), compact = true)
  }
}

/**
 * Due pannelli grandi con l'angolo continuo dell'engine: il campo lo raccorda, il ritaglio hardware sta al 98% del
 * raggio (decisione di F1A.1). A 6x: il filo ha lo stesso spessore sulla diagonale e sui lati, e non esce una
 * mezzaluna di corpo fuori dal filo.
 */
@Composable
private fun CornerCell(session: MaterialSession) {
  CellColumn(spacing = 24.dp) {
    PanelSlab(session, "menu-grande", width = 260.dp, height = 220.dp) {
      Text("Raggio 38 dp", style = MaterialTheme.typography.labelLarge)
    }
    PanelSlab(session, "foglio", width = 340.dp, height = 260.dp, shape = ContinuousCornerShape(FluidRadius.Sheet)) {
      Text("Foglio, raggio 38 dp", style = MaterialTheme.typography.labelLarge)
    }
  }
}

// --- Vetro annidato e pressione: il dito finto ------------------------------------------------------------

/**
 * Un foglio di vetro con dentro due tasti e un interruttore, che ricevono il fondo della pagina come li scrivono le
 * app (`backdrop = stage`): devono rifrangere il corpo del foglio, non il suo filo né il suo bagliore. Il primo tasto
 * si preme da solo; gli altri non devono sfarfallare.
 */
@Composable
private fun NestedScene(session: MaterialSession) {
  val targets = remember { LinkedHashMap<String, MaterialTarget>() }
  ModelSplit(session) { model ->
    val stage = session.stage
    var checked by remember { mutableStateOf(true) }
    CellColumn {
      Column(
        modifier = Modifier
          .widthIn(max = 360.dp)
          .fillMaxWidth()
          .reported(session.report, session.config, "foglio")
          .glassSurface(
            state = stage,
            tint = GlassDefaults.modalTint(),
            shape = ContinuousCornerShape(FluidRadius.Sheet),
            role = GlassRole.Modal,
          )
          .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("Foglio di vetro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          val target = targets.getOrPut("${model.name.lowercase()}/conferma") { MaterialTarget() }
          FluidGlassButton(
            text = "Conferma",
            onClick = {},
            backdrop = stage,
            modifier = Modifier.trackBounds(target).reported(session.report, session.config, "conferma"),
          )
          FluidGlassIconButton(onClick = {}, backdrop = stage, modifier = Modifier.reported(session.report, session.config, "tondo")) {
            FluidIcon(FluidIcons.Filled.Star, contentDescription = "Preferito")
          }
          FluidSwitch(checked = checked, onCheckedChange = { checked = it }, backdrop = stage)
        }
        Text(
          "Il primo tasto si preme da solo: guarda gli altri due.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
  FingerPresses(session, targets)
}

/**
 * Un tasto tondo vero da 76 dp e una capsula vera, premuti dal dito finto: giù a un quarto, scorre fino a tre quarti
 * (il bagliore segue il dito, la lente si approfondisce), resta, su. Una riga `PRESSIONE` in logcat a ogni giù e su,
 * per mettere i fotogrammi del video accanto all'istante.
 */
@Composable
private fun PressScene(session: MaterialSession) {
  val targets = remember { LinkedHashMap<String, MaterialTarget>() }
  ModelSplit(session) { model ->
    val key = model.name.lowercase()
    CellColumn(spacing = 36.dp) {
      Spacer(Modifier.height(24.dp))
      val round = targets.getOrPut("$key/tondo") { MaterialTarget() }
      FluidGlassIconButton(
        onClick = {},
        size = 76.dp,
        modifier = Modifier.trackBounds(round).reported(session.report, session.config, "tondo"),
      ) {
        FluidIcon(FluidIcons.Filled.PlayArrow, contentDescription = "Riproduci", modifier = Modifier.size(38.dp))
      }
      val capsule = targets.getOrPut("$key/capsula") { MaterialTarget() }
      FluidGlassButton(
        text = "Tieni premuto",
        onClick = {},
        size = FluidGlassSize.Large,
        modifier = Modifier.trackBounds(capsule).reported(session.report, session.config, "capsula"),
      )
    }
  }
  FingerPresses(session, targets)
}

private const val TwoPi = (2.0 * PI).toFloat()

/** Un'onda fra 0 e 1 che parte da 0: lo stesso valore allo stesso istante, a ogni lancio. */
private fun wave(seconds: Float, period: Float): Float = 0.5f - 0.5f * cos(TwoPi * seconds / period)

/**
 * Il dito finto sui bersagli di [targets], nell'ordine in cui sono nati (prima la colonna `Classic`, poi `Liquid`).
 * `cycle`: uno dopo l'altro, in ciclo. `hold`: giù sull'ultimo, e resta, scivolando piano. `off`: niente.
 */
@Composable
private fun FingerPresses(session: MaterialSession, targets: Map<String, MaterialTarget>) {
  val finger = session.finger ?: return
  val config = session.config
  if (config.cycle == MaterialCycle.Off) return
  DisposableEffect(finger) {
    onDispose { if (finger.isDown) finger.cancel(Offset.Zero) }
  }
  LaunchedEffect(finger, config) {
    // La scena si posa: le lastre hanno i loro limiti e il primo fotogramma è passato.
    delay(1_200L)
    val periodMs = (config.periodSeconds * 1000f).toLong().coerceAtLeast(400L)
    if (config.cycle == MaterialCycle.Hold) {
      val (name, target) = targets.entries.lastOrNull()?.toPair() ?: return@LaunchedEffect
      val r = target.bounds ?: return@LaunchedEffect
      finger.down(Offset(r.left + r.width * 0.5f, r.center.y))
      session.report.line("PRESSIONE ${config.key} nome=$name giu (tenuto)")
      var t = 0f
      while (true) {
        delay(FingerStepMillis)
        t += FingerStepMillis / 1000f
        finger.move(Offset(r.left + r.width * (0.5f + 0.18f * sin(TwoPi * t / 2.4f)), r.center.y))
      }
    }
    while (true) {
      var pressed = false
      for ((name, target) in targets.entries.toList()) {
        val r = target.bounds ?: continue
        pressed = true
        val from = Offset(r.left + r.width * 0.25f, r.center.y)
        val to = Offset(r.left + r.width * 0.75f, r.center.y)
        finger.down(from)
        session.report.line("PRESSIONE ${config.key} nome=$name giu")
        val steps = ((periodMs / 2) / FingerStepMillis).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
          delay(FingerStepMillis)
          val f = i / steps.toFloat()
          finger.move(Offset(from.x + (to.x - from.x) * f, from.y))
        }
        delay(periodMs / 4)
        finger.up(to)
        session.report.line("PRESSIONE ${config.key} nome=$name su")
        delay(periodMs / 4)
      }
      // Nessun bersaglio con i suoi limiti (non ancora disposti): si aspetta, mai un giro a vuoto sul filo principale.
      if (!pressed) delay(periodMs)
    }
  }
}

/** Un passo del dito finto: un fotogramma a 60 Hz. */
private const val FingerStepMillis = 16L

// --- Intensità --------------------------------------------------------------------------------------------

/**
 * Una capsula e un pannello con `intensity` da 0 a 1 in ciclo (o fissa con `material.intensity`): una lastra che
 * compare da zero non deve mostrare un rettangolo della pagina vecchia, e a metà strada il contenuto si dissolve
 * come oggi (decisione di F1A.6).
 */
@Composable
private fun IntensityCell(session: MaterialSession) {
  val config = session.config
  val clock = session.clock
  val backdrop = session.stage
  val amount: () -> Float = remember(config, clock) {
    {
      config.intensity ?: when (config.cycle) {
        MaterialCycle.Cycle -> wave(clock.seconds, config.periodSeconds)
        MaterialCycle.Hold, MaterialCycle.Off -> 1f
      }
    }
  }
  CellColumn(spacing = 24.dp) {
    Text(
      text = "intensity ${MaterialConfig.number((amount() * 100f).roundToInt() / 100f)}",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Box(
      modifier = Modifier
        .size(220.dp, 56.dp)
        .reported(session.report, config, "capsula")
        .glassSurface(
          state = backdrop,
          tint = GlassDefaults.floatingTint(),
          shape = FluidCapsuleShape,
          role = GlassRole.Floating,
          intensity = amount,
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text("Capsula", style = MaterialTheme.typography.labelLarge)
    }
    Box(
      modifier = Modifier
        .size(220.dp, 160.dp)
        .reported(session.report, config, "pannello")
        .glassSurface(
          state = backdrop,
          tint = GlassDefaults.panelTint(),
          shape = ContinuousCornerShape(28.dp),
          role = GlassRole.Modal,
          optics = GlassDefaults.panelOptics(compact = true),
          intensity = amount,
        ),
      contentAlignment = Alignment.Center,
    ) {
      Text("Pannello", style = MaterialTheme.typography.labelLarge)
    }
  }
}

// --- Lista sotto una barra ------------------------------------------------------------------------------------

/**
 * Una pagina vera (`FluidScreen`, con la barra in alto che resta `Classic` fino a F2.5) che scorre da sola, e sopra
 * due capsule flottanti vere, una per vetro: scorrendo devono aggiornarsi, senza salti del margine alla soglia di
 * qualità. Il fondo della scena è quello della pagina, non `material.backdrop`.
 */
@Composable
private fun ListScene(session: MaterialSession) {
  val config = session.config
  val listState = rememberLazyListState()
  val density = LocalDensity.current
  FluidScreen(
    title = "Lista sotto una barra",
    subtitle = "La pagina scorre da sola; le capsule e la barra delle schede devono seguirla.",
    listState = listState,
    extraBottomPadding = session.bottomInset,
    overlay = { pageBackdrop ->
      Row(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = session.bottomInset + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        config.models.models.forEach { model ->
          CompositionLocalProvider(LocalFluidGlassModel provides model) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              ModelLabel(model)
              FluidFloatingCapsule(
                modifier = Modifier.reported(session.report, config, "capsula"),
                backdrop = pageBackdrop,
              ) {
                FluidCapsuleAction(FluidIcons.Share, "Condividi", onClick = {})
                FluidCapsuleAction(FluidIcons.Filled.Favorite, "Preferito", onClick = {})
                FluidCapsuleAction(FluidIcons.Delete, "Elimina", onClick = {}, destructive = true)
              }
            }
          }
        }
      }
    },
  ) {
    items(40) { index -> LoudCard(index) }
  }
  if (config.cycle != MaterialCycle.Off) {
    LaunchedEffect(listState, config) {
      delay(1_200L)
      val distance = with(density) { 2400.dp.toPx() }
      val travel = (config.periodSeconds * 600f).toInt().coerceAtLeast(300)
      if (config.cycle == MaterialCycle.Hold) {
        listState.animateScrollBy(distance / 2f, tween(travel))
        session.report.line("LISTA ${config.key} ferma a meta'")
        return@LaunchedEffect
      }
      while (true) {
        session.report.line("LISTA ${config.key} giu")
        listState.animateScrollBy(distance, tween(travel))
        delay((config.periodSeconds * 400f).toLong())
        session.report.line("LISTA ${config.key} su")
        listState.animateScrollBy(-distance, tween(travel))
        delay((config.periodSeconds * 400f).toLong())
      }
    }
  }
}

// --- Menu e foglio -------------------------------------------------------------------------------------------

/**
 * Il tasto che diventa il proprio menu (`FluidMorphMenuButton`), aperto dal dito finto e chiuso, in ciclo, una
 * colonna dopo l'altra. Fino a F1B il viaggio è Fluid-physics (vetro di prima) mentre il tasto fermo è `Liquid`:
 * qui si vede quanto si vede il cambio di materiale.
 */
@Composable
private fun MenuScene(session: MaterialSession) {
  val targets = remember { LinkedHashMap<String, MaterialTarget>() }
  ModelSplit(session) { model ->
    val target = targets.getOrPut("${model.name.lowercase()}/menu") { MaterialTarget() }
    CellColumn {
      Spacer(Modifier.height(48.dp))
      FluidMorphMenuButton(
        state = session.menu,
        actions = { DemoActions },
        onClick = {},
        text = "Azione",
        icon = FluidIcons.MoreVert,
        backdrop = session.stage,
        opensOnTap = true,
        modifier = Modifier.trackBounds(target).reported(session.report, session.config, "menu"),
      )
    }
  }
  val finger = session.finger ?: return
  val config = session.config
  if (config.cycle == MaterialCycle.Off) return
  LaunchedEffect(finger, config) {
    delay(1_200L)
    val periodMs = (config.periodSeconds * 1000f).toLong().coerceAtLeast(800L)
    suspend fun tap(name: String, target: MaterialTarget): Boolean {
      val r = target.bounds ?: return false
      finger.down(r.center)
      delay(60L)
      finger.up(r.center)
      session.report.line("MENU ${config.key} nome=$name apre")
      return true
    }
    if (config.cycle == MaterialCycle.Hold) {
      targets.entries.lastOrNull()?.let { (name, target) -> tap(name, target) }
      return@LaunchedEffect
    }
    while (true) {
      var opened = false
      for ((name, target) in targets.entries.toList()) {
        if (!tap(name, target)) continue
        opened = true
        delay(periodMs * 6 / 10)
        session.menu.dismiss()
        session.report.line("MENU ${config.key} nome=$name chiude")
        delay(periodMs * 4 / 10)
      }
      // Come sopra: senza un tasto disposto si aspetta, non si gira a vuoto.
      if (!opened) delay(periodMs)
    }
  }
}

/**
 * Un foglio vero dell'host dei modali, con lo scrim dietro (lo scrim resta `Classic` per la regola del velo piatto
 * di F1A.6: deve essere identico a oggi), **in ogni colonna**: ogni colonna ha il suo host dei modali, sotto il suo
 * `LocalFluidGlassModel`, quindi il foglio della colonna `Liquid` è il materiale nuovo e quello della colonna
 * `Classic` il vetro di prima, aperti e chiusi insieme. Fino a F1A.9 il foglio si disegnava nell'host alla radice
 * della galleria e prendeva il vetro di `gallery.model`, non quello delle colonne.
 */
@Composable
private fun ModalScene(session: MaterialSession) {
  val config = session.config
  var open by remember { mutableStateOf(config.cycle == MaterialCycle.Hold) }
  ModelSplit(session) { _ -> ModalCell(session, open) { open = false } }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(top = session.topInset + LabelSpace, bottom = session.bottomInset),
    contentAlignment = Alignment.TopCenter,
  ) {
    FluidGlassButton(
      text = if (open) "Chiudi il foglio" else "Apri il foglio",
      onClick = { open = !open },
      modifier = Modifier.reported(session.report, config, "apri"),
    )
  }
  if (config.cycle == MaterialCycle.Cycle) {
    LaunchedEffect(config) {
      delay(1_200L)
      val periodMs = (config.periodSeconds * 1000f).toLong().coerceAtLeast(800L)
      while (true) {
        open = true
        session.report.line("FOGLIO ${config.key} apre")
        delay(periodMs)
        open = false
        session.report.line("FOGLIO ${config.key} chiude")
        delay(periodMs * 6 / 10)
      }
    }
  }
}

/**
 * Il foglio di una colonna: un host dei modali suo, che disegna dentro la colonna e sotto il suo vetro, e il foglio
 * che ci si apre. Lo scrim rifrange il palco della scena, come quello dell'host della radice rifrange la pagina.
 */
@Composable
private fun ModalCell(session: MaterialSession, open: Boolean, onDismiss: () -> Unit) {
  val host = rememberFluidGlassModalHostState()
  CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
    Box(modifier = Modifier.fillMaxSize()) {
      FluidGlassModalPortal(
        visible = open,
        onDismissRequest = onDismiss,
        presentation = FluidGlassModalPresentation.Sheet,
        paneTitle = "Foglio di prova",
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text("Foglio di prova", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FluidGlassButton(text = "Conferma", onClick = {})
            FluidGlassIconButton(onClick = {}) { FluidIcon(FluidIcons.Filled.Star, contentDescription = "Preferito") }
          }
          Text(
            "Vetro di questa colonna: ${LocalFluidGlassModel.current.name}. Lo scrim dietro resta il vetro di prima.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(120.dp))
        }
      }
      FluidGlassModalHost(state = host, backdrop = session.stage)
    }
  }
}

// --- Tinte -------------------------------------------------------------------------------------------------

private val SyncStatuses = listOf(
  SyncStatus(state = SyncState.SYNCING),
  SyncStatus(state = SyncState.ERROR, message = "Sessione scaduta."),
)

/**
 * Le tinte che il materiale nuovo deve vedere (F1A.6 voce 13, regola di F1A.8): l'indicatore di sincronizzazione
 * (atteso: senza tono, finché non si corregge), il tasto scelto con l'accento, la capsula su una foto (pellicola
 * `Fixed`), una tinta scritta a mano (`Fixed`), una alla Apple (`Colored`), e una tinta di serie a cui l'app ha
 * cambiato colore con `copy`, che deve leggersi `Fixed` e mostrare il suo arancio.
 */
@Composable
private fun TintsCell(session: MaterialSession) {
  val copied = GlassDefaults.floatingTint().copy(overlay = Color(0xFFFF9500).copy(alpha = 0.40f))
  CellColumn {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      SyncStatuses.forEach { status -> FluidSyncAction(status = status, onRetry = {}) }
      FluidGlassButton(text = "Scelto", onClick = {}, selected = true, modifier = Modifier.reported(session.report, session.config, "scelto"))
    }
    ThreeActionCapsule(session, onPhoto = true, name = "capsula-su-foto")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      TintSlab(session, fixedAccentTint(), "Fixed", "fixed")
      TintSlab(session, coloredAccentTint(), "Colored", "colored")
    }
    TintSlab(session, copied, "copy(overlay = arancio)", "copiata", width = 220.dp)
    PanelSlab(
      session,
      "pannello-colorato",
      width = 260.dp,
      height = 96.dp,
      tint = GlassDefaults.tintedModalTint(MaterialTheme.colorScheme.primary),
    ) {
      Text("tintedModalTint", style = MaterialTheme.typography.labelLarge)
    }
  }
}

// --- Lastre delle app ---------------------------------------------------------------------------------------

/** Una lastra di un'app: la tinta e l'ottica com'è scritta dove vive (F1A.8, `GlassCompatScenes`). */
private class AppSlab(
  val name: String,
  val title: String,
  val width: Dp,
  val height: Dp,
  val shape: Shape,
  val role: GlassRole,
  val optics: GlassOptics,
  val tint: GlassTint,
  val icon: Boolean,
)

private fun appSlabs(dark: Boolean): List<AppSlab> {
  val interactive = GlassDefaults.optics(GlassRole.Interactive)
  val modal = GlassDefaults.optics(GlassRole.Modal)
  val floating = GlassDefaults.optics(GlassRole.Floating)
  val panelShape = ContinuousCornerShape(38.dp)
  val cardShape = ContinuousCornerShape(18.dp)
  return if (!dark) {
    listOf(
      AppSlab(
        "fluidify-tasto-su-foto", "Fluidify: tasto, bianco 40%", 44.dp, 44.dp, FluidCapsuleShape, GlassRole.Interactive, interactive,
        GlassTint(Color.White.copy(alpha = 0.40f), Color(0xFFFDFDFF).copy(alpha = 0.96f), Color.Black.copy(alpha = 0.26f)), icon = true,
      ),
      AppSlab(
        "fluidify-scheda", "Fluidify: scheda, bianco 50%", 260.dp, 72.dp, cardShape, GlassRole.Modal, modal,
        GlassTint(Color.White.copy(alpha = 0.50f), Color(0xFFFDFDFF).copy(alpha = 0.96f), Color.Black.copy(alpha = 0.24f)), icon = false,
      ),
      AppSlab(
        "fluidify-pannello", "Fluidify: pannello, sfocatura 7, bianco 64%", 300.dp, 160.dp, panelShape, GlassRole.Modal,
        modal.copy(blurScale = 7f),
        GlassTint(Color.White.copy(alpha = 0.64f), Color(0xFFFDFDFF).copy(alpha = 0.96f), Color.Black.copy(alpha = 0.20f)), icon = false,
      ),
      AppSlab(
        "mycloud-capsula", "MyCloud Gallery: capsula, bianco 45%", 156.dp, 48.dp, FluidCapsuleShape, GlassRole.Floating, floating,
        GlassTint(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.94f), Color(0xFF141414).copy(alpha = 0.16f)), icon = false,
      ),
    )
  } else {
    listOf(
      AppSlab(
        "fluidify-tasto-su-foto", "Fluidify: tasto, nero 30%", 44.dp, 44.dp, FluidCapsuleShape, GlassRole.Interactive, interactive,
        GlassTint(Color.Black.copy(alpha = 0.30f), Color(0xFF141416).copy(alpha = 0.95f), Color.White.copy(alpha = 0.22f)), icon = true,
      ),
      AppSlab(
        "fluidify-scheda", "Fluidify: scheda, nero 38%", 260.dp, 72.dp, cardShape, GlassRole.Modal, modal,
        GlassTint(Color.Black.copy(alpha = 0.38f), Color(0xFF141416).copy(alpha = 0.95f), Color.White.copy(alpha = 0.20f)), icon = false,
      ),
      AppSlab(
        "fluidify-pannello", "Fluidify: pannello, sfocatura 7, nero 48%", 300.dp, 160.dp, panelShape, GlassRole.Modal,
        modal.copy(blurScale = 7f),
        GlassTint(Color.Black.copy(alpha = 0.48f), Color(0xFF141416).copy(alpha = 0.95f), Color.White.copy(alpha = 0.16f)), icon = false,
      ),
      AppSlab(
        "mycloud-capsula", "MyCloud Gallery: capsula, nero 60%", 156.dp, 48.dp, FluidCapsuleShape, GlassRole.Floating, floating,
        GlassTint(Color.Black.copy(alpha = 0.60f), Color(0xFF1B1B1E).copy(alpha = 0.96f), Color.White.copy(alpha = 0.20f)), icon = false,
      ),
    )
  }
}

/**
 * Le lastre scritte a mano nelle app (F1A.8): le pellicole su foto di Fluidify, il suo pannello del lettore con la
 * sfocatura 7 (che col materiale nuovo esce meno smerigliato di oggi: decisione aperta), la capsula di MyCloud
 * Gallery. Più i due tasti di Fluidify con la cattura al 40% e la sfocatura a zero. Le serie chiare col tema chiaro,
 * le scure col tema scuro.
 */
@Composable
private fun AppsCell(session: MaterialSession) {
  val dark = GlassDefaults.isDarkSurface()
  val slabs = remember(dark) { appSlabs(dark) }
  val backdrop = session.stage
  val ink = if (dark) Color(0xFFF2F2F5) else Color(0xFF121214)
  CellColumn(spacing = 14.dp) {
    slabs.forEach { slab ->
      Box(
        modifier = Modifier
          .size(slab.width, slab.height)
          .reported(session.report, session.config, slab.name)
          .glassSurface(state = backdrop, tint = slab.tint, shape = slab.shape, role = slab.role, optics = slab.optics),
        contentAlignment = Alignment.Center,
      ) {
        if (slab.icon) {
          FluidIcon(FluidIcons.Filled.PlayArrow, contentDescription = null, tint = ink)
        } else {
          Text(slab.title, style = MaterialTheme.typography.labelMedium, color = ink)
        }
      }
    }
    if (!dark) {
      val interactive = GlassDefaults.optics(GlassRole.Interactive)
      val control = GlassDefaults.controlTint()
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
          "fluidify-piatto" to interactive.copy(blurScale = 0f, backdropResolution = 0.4f),
          "fluidify-in-pagina" to interactive.copy(backdropResolution = 0.4f),
        ).forEach { (name, optics) ->
          Box(
            modifier = Modifier
              .size(44.dp)
              .reported(session.report, session.config, name)
              .glassSurface(state = backdrop, tint = control, shape = FluidCapsuleShape, role = GlassRole.Interactive, optics = optics),
            contentAlignment = Alignment.Center,
          ) {
            FluidIcon(FluidIcons.Add, contentDescription = null, tint = ink)
          }
        }
        Caption("Fluidify: piatto e in pagina (cattura al 40%)")
      }
    }
  }
}

// --- Contrasto di sistema -----------------------------------------------------------------------------------

private val ContrastLevels = listOf(0f to "standard", 0.5f to "medio", 1f to "alto")

/**
 * Gli stessi vetri al contrasto di sistema standard, medio e alto (`LocalFluidSystemContrast`, come lo fornisce il
 * tema da Android 14): sopra lo standard le tinte di serie diventano la pellicola densa di oggi (`Fixed`), e il testo
 * sopra deve restare leggibile. Il contrasto vero del sistema si prova a parte, dalle impostazioni (`LEGGIMI.md`).
 */
@Composable
private fun ContrastCell(session: MaterialSession) {
  val levels = session.config.contrast?.let { fixed -> listOf(fixed to MaterialConfig.number(fixed)) } ?: ContrastLevels
  Row(
    modifier = Modifier.fillMaxSize().padding(top = LabelSpace),
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    levels.forEach { (level, label) ->
      CompositionLocalProvider(LocalFluidSystemContrast provides level) {
        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Caption("contrasto $label")
          FluidGlassIconButton(onClick = {}, modifier = Modifier.reported(session.report, session.config, "tondo-$label")) {
            FluidIcon(FluidIcons.Filled.Star, contentDescription = "Preferito")
          }
          FluidGlassButton(text = "Testo", onClick = {}, modifier = Modifier.reported(session.report, session.config, "tasto-$label"))
          FluidCard(glass = true, modifier = Modifier.widthIn(max = 180.dp).reported(session.report, session.config, "card-$label")) {
            Text("Card", style = MaterialTheme.typography.titleSmall)
            Text("Testo sopra il velo", style = MaterialTheme.typography.bodySmall)
          }
          PanelSlab(session, "pannello-$label", width = 180.dp, height = 96.dp, compact = true) {
            Text("Pannello", style = MaterialTheme.typography.labelLarge)
            Text("Testo sopra il velo", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

// --- Destra-sinistra ------------------------------------------------------------------------------------------

/**
 * Gli stessi vetri da sinistra a destra e da destra a sinistra: un foglio con gli angoli diversi (il raccordo e il
 * ritaglio devono girare con la direzione), la capsula con le azioni, la tab bar con la prima scheda scelta.
 */
@Composable
private fun RtlCell(session: MaterialSession) {
  val backdrop = session.stage
  val sheetShape = ContinuousCornerShape(topStart = 38.dp, topEnd = 10.dp, bottomEnd = 38.dp, bottomStart = 10.dp)
  Row(modifier = Modifier.fillMaxSize().padding(top = LabelSpace)) {
    listOf(LayoutDirection.Ltr to "ltr", LayoutDirection.Rtl to "rtl").forEach { (direction, label) ->
      CompositionLocalProvider(LocalLayoutDirection provides direction) {
        val tabScroll = rememberFluidFloatingTabBarScrollConnection()
        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Caption(label)
          PanelSlab(session, "foglio-$label", width = 220.dp, height = 140.dp, shape = sheetShape) {
            Text("Angoli 38 e 10", style = MaterialTheme.typography.labelLarge)
          }
          FluidFloatingCapsule(modifier = Modifier.reported(session.report, session.config, "capsula-$label"), backdrop = backdrop) {
            FluidCapsuleAction(FluidIcons.ArrowBack, "Indietro", onClick = {})
            FluidCapsuleAction(FluidIcons.Share, "Condividi", onClick = {})
          }
          FluidGlassButton(
            text = "Avanti",
            onClick = {},
            leading = { FluidIcon(FluidIcons.Filled.PlayArrow, contentDescription = null) },
            modifier = Modifier.reported(session.report, session.config, "tasto-$label"),
          )
          FluidFloatingTabBar(
            items = DemoTabs,
            selectedRoute = DemoTabs.first().route,
            onSelect = {},
            scrollConnection = tabScroll,
            backdrop = backdrop,
            modifier = Modifier.reported(session.report, session.config, "tabbar-$label"),
          )
        }
      }
    }
  }
}
