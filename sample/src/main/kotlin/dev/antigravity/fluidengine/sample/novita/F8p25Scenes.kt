package dev.antigravity.fluidengine.sample.novita

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Row as GlanceRow
import androidx.glance.layout.Spacer as GlanceSpacer
import androidx.glance.layout.fillMaxWidth as glanceFillMaxWidth
import androidx.glance.layout.height as glanceHeight
import androidx.glance.layout.width as glanceWidth
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.renderer.TileRenderer
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.GalleryActivity
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeThreshold
import dev.antigravity.fluidengine.wear.sync.FluidWearDelivery
import dev.antigravity.fluidengine.wear.sync.FluidWearDeliveryState
import dev.antigravity.fluidengine.wear.sync.FluidWearEvent
import dev.antigravity.fluidengine.wear.sync.FluidWearFormat
import dev.antigravity.fluidengine.wear.sync.FluidWearMemoryStore
import dev.antigravity.fluidengine.wear.sync.FluidWearPaths
import dev.antigravity.fluidengine.wear.sync.FluidWearRole
import dev.antigravity.fluidengine.wear.sync.FluidWearRouted
import dev.antigravity.fluidengine.wear.sync.FluidWearStatus
import dev.antigravity.fluidengine.wear.sync.FluidWearSync
import dev.antigravity.fluidengine.wear.tiles.FluidComplications
import dev.antigravity.fluidengine.wear.tiles.FluidTileAction
import dev.antigravity.fluidengine.wear.tiles.FluidTileColors
import dev.antigravity.fluidengine.wear.tiles.FluidTileContent
import dev.antigravity.fluidengine.wear.tiles.fluidTile
import dev.antigravity.fluidengine.wear.tiles.fluidTileColors
import dev.antigravity.fluidengine.widget.EngineWidgetGauge
import dev.antigravity.fluidengine.widget.EngineWidgetGroup
import dev.antigravity.fluidengine.widget.EngineWidgetHairline
import dev.antigravity.fluidengine.widget.EngineWidgetHeader
import dev.antigravity.fluidengine.widget.EngineWidgetMetrics
import dev.antigravity.fluidengine.widget.EngineWidgetPill
import dev.antigravity.fluidengine.widget.EngineWidgetRow
import dev.antigravity.fluidengine.widget.EngineWidgetSurface
import dev.antigravity.fluidengine.widget.FluidQuickTileCard
import dev.antigravity.fluidengine.widget.FluidQuickTileOffer
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.widget.FluidWidgetEntry
import dev.antigravity.fluidengine.widget.FluidWidgetPreview
import dev.antigravity.fluidengine.widget.FluidWidgetShowcase
import dev.antigravity.fluidengine.widget.engineWidgetPalette
import dev.antigravity.fluidengine.widget.fluidWidgetPreviewScale
import dev.antigravity.fluidengine.widget.resolveEngineWidgetLayout
import java.time.Instant
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay

// F8.25 · Widget e Wear (le schede `vetrina-dei-widget`, `tile-e-complicazioni`, `wear-con-ricevuta`): la vetrina dei
// widget di Pampa Widgets con due widget veri della galleria, le Tile e le complicazioni di ClasseViva, Pampa Mail, Pampa
// Sounds e PampAI disegnate dal renderer di Wear OS, lo stato pubblicato dal telefono che l'orologio mostra ad app
// chiusa, e il preferito di Fluidify salvato al polso col telefono lontano. Non c'e' un orologio vero: il ponte gira fra
// due lati in memoria, con gli stessi byte che andrebbero sul Data Layer.

/** Le scene di F8.25. */
internal val F8p25Section = NewElementSection(
  pack = "F8.25",
  title = "Widget e Wear",
  scenes = listOf(
    NewElementScene(
      "widget-vetrina", "La vetrina dei widget",
      "Pampa Widgets · l'anteprima vera per taglia come la galleria di iPhone e «Aggiungi alla Home»",
    ) { onBack -> WidgetShowcaseScene(onBack) },
    NewElementScene(
      "impostazioni-app", "Le impostazioni dell'app",
      "Pampa Sounds · il riquadro delle Impostazioni rapide si propone da qui, mai dalla vetrina dei widget",
    ) { onBack -> AppSettingsScene(onBack) },
    NewElementScene(
      "wear-tile", "Le Tile sull'orologio",
      "ClasseViva, Pampa Mail, Sounds, PampAI · disco di vetro, anello sul bordo, disegnate dal renderer di Wear OS",
    ) { onBack -> WearTilesScene(onBack) },
    NewElementScene(
      "wear-stato", "Lo stato dal telefono",
      "ClasseViva · il telefono pubblica la prossima lezione, la Tile e le complicazioni la mostrano ad app chiusa",
    ) { onBack -> WearStatusScene(onBack) },
    NewElementScene(
      "wear-ricevuta", "Dal polso con ricevuta",
      "Fluidify · un preferito salvato col telefono lontano: In coda, poi Registrato, una volta sola; il comando che scade",
    ) { onBack -> WearReceiptScene(onBack) },
  ),
  detail = "La galleria dei widget di iPhone dentro l'app, la Smart Stack di watchOS nello stile Fluid, Watch Connectivity con la ricevuta",
)

/** Le voci da provare a mano di F8.25, in coda a quelle di «Da provare». */
internal val F8p25TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-25-vetrina", TryArea.AppParts, "La vetrina dei widget",
    "Apri «La vetrina dei widget», sfoglia le taglie col dito e tocca «Aggiungi alla Home», poi chiudi la richiesta e " +
      "riprova confermando: giudica se l'anteprima e' il widget vero alla sua misura e se la riga sotto il tasto dice " +
      "il giusto in tutti e due i casi.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La vetrina dei widget"),
  ),
  TryItem(
    "f8-25-tile", TryArea.AppParts, "Le Tile sull'orologio",
    "Apri «Le Tile sull'orologio» e cambia accento fra Fluid, Sole e Notte: giudica se il disco di vetro, l'anello sul " +
      "bordo e il tasto si leggono sul nero anche con l'accento piu' scuro.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Le Tile sull'orologio"),
  ),
  TryItem(
    "f8-25-ricevuta", TryArea.AppParts, "Dal polso con ricevuta",
    "Apri «Dal polso con ricevuta», spegni «Telefono vicino», salva un preferito e un «Riproduci», riavvia l'orologio e " +
      "poi riaccendi il telefono: giudica se il preferito passa da «In coda» a «Registrato» una volta sola e se " +
      "«Riproduci» resta «Scaduto».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Dal polso con ricevuta"),
  ),
)

/** I tag delle prove. */
internal object F8p25Tags {
  const val Ground = "f8p25:fondo"
  const val Accent = "f8p25:accento"
  const val Publish = "f8p25:pubblica"
  const val Finish = "f8p25:finita"
  const val Withdraw = "f8p25:togli"
  const val Near = "f8p25:vicino"
  const val Favorite = "f8p25:preferito"
  const val Play = "f8p25:riproduci"
  const val Restart = "f8p25:riavvia"
  const val Resend = "f8p25:riconsegna"
  const val WatchQueue = "f8p25:coda"
  const val PhoneSaved = "f8p25:salvati"

  fun tile(id: String) = "f8p25:tile:$id"
}

// --- I due widget veri della galleria ------------------------------------------------------------------------------

private class Lesson(val subject: String, val room: String, val time: String)

private val TodayLessons = listOf(
  Lesson("Matematica", "Aula 3", "8:00"),
  Lesson("Italiano", "Aula 3", "9:00"),
  Lesson("Inglese", "Laboratorio", "10:00"),
  Lesson("Storia", "Aula 3", "11:00"),
)

/** L'agenda di prova: le lezioni di oggi, coi componenti di engine-widget (F8.25, la vetrina la mostra per taglia). */
@Composable
internal fun F8p25AgendaContent(context: Context) {
  val size = LocalSize.current
  val palette = engineWidgetPalette(context, EngineSettings())
  // Le pillole in fondo solo dove ci stanno intere: a 2×2 (158 dp) si troncherebbero.
  val layout = resolveEngineWidgetLayout(size, hasFooter = size.width >= 240.dp)
  EngineWidgetSurface(palette, layout, onClick = actionStartActivity<GalleryActivity>()) {
    EngineWidgetHeader(title = "Oggi", subtitle = "4 lezioni", palette = palette, layout = layout)
    GlanceSpacer(GlanceModifier.glanceHeight(EngineWidgetMetrics.Gap))
    EngineWidgetGroup(palette) {
      val shown = TodayLessons.take(layout.rowLimit)
      for (i in shown.indices) {
        if (i > 0) EngineWidgetHairline(palette, layout)
        val lesson = shown[i]
        EngineWidgetRow(
          title = lesson.subject,
          subtitle = lesson.room,
          trailing = lesson.time,
          tone = if (i == 0) palette.primaryTone else palette.neutralTone,
          palette = palette,
          layout = layout,
        )
      }
    }
    if (layout.showFooter) {
      GlanceSpacer(GlanceModifier.glanceHeight(EngineWidgetMetrics.Gap))
      GlanceRow(GlanceModifier.glanceFillMaxWidth()) {
        EngineWidgetPill("2", "Compiti", palette.primaryTone, GlanceModifier.defaultWeight(), compact = layout.compact)
        GlanceSpacer(GlanceModifier.glanceWidth(8.dp))
        EngineWidgetPill("1", "Verifica", palette.warningTone, GlanceModifier.defaultWeight(), compact = layout.compact)
      }
    }
  }
}

/** Le assenze di prova: il quadrante di F8.6 sul widget. */
@Composable
internal fun F8p25AbsencesContent(context: Context) {
  val size = LocalSize.current
  val palette = engineWidgetPalette(context, EngineSettings())
  val layout = resolveEngineWidgetLayout(size)
  EngineWidgetSurface(palette, layout, onClick = actionStartActivity<GalleryActivity>()) {
    EngineWidgetHeader(title = "Assenze", subtitle = "Sul massimo dell'anno", palette = palette, layout = layout)
    GlanceSpacer(GlanceModifier.glanceHeight(EngineWidgetMetrics.Gap))
    EngineWidgetGauge(
      value = 18f,
      range = 0f..100f,
      palette = palette,
      layout = layout,
      valueText = "18 %",
      label = "Assenze",
      minText = "0",
      maxText = "100 %",
      thresholds = listOf(FluidGaugeThreshold(25f, "Limite")),
      thresholdTexts = listOf("25 %"),
    )
  }
}

/** Il widget dell'agenda di prova. */
internal class F8p25AgendaWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent { F8p25AgendaContent(context) }
  }
}

/** Il ricevitore dell'agenda di prova (nel manifest della galleria). */
class F8p25AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = F8p25AgendaWidget()
}

/** Il widget delle assenze di prova. */
internal class F8p25AbsencesWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent { F8p25AbsencesContent(context) }
  }
}

/** Il ricevitore delle assenze di prova. */
class F8p25AbsencesWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = F8p25AbsencesWidget()
}

/** Il riquadro rapido di prova: si accende e si spegne, non fa altro. */
class F8p25QuickTileService : TileService() {
  override fun onStartListening() {
    qsTile?.apply { updateTile() }
  }

  override fun onClick() {
    qsTile?.apply {
      state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
      updateTile()
    }
  }
}

// --- La vetrina ----------------------------------------------------------------------------------------------------

/** Il fondo della scena. */
internal enum class F8p25Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

@Composable
private fun WidgetShowcaseScene(onBack: () -> Unit) {
  val context = LocalContext.current
  var ground by rememberSaveable { mutableStateOf(F8p25Ground.Page) }
  val entries = remember(context) {
    listOf(
      FluidWidgetEntry(
        provider = ComponentName(context, F8p25AgendaWidgetReceiver::class.java),
        title = "Agenda",
        description = "Le lezioni di oggi, una per riga, e i compiti in fondo.",
        preview = FluidWidgetPreview.Glance { F8p25AgendaContent(context) },
      ),
      FluidWidgetEntry(
        provider = ComponentName(context, F8p25AbsencesWidgetReceiver::class.java),
        title = "Assenze",
        description = "Le assenze sul massimo dell'anno, col limite.",
        preview = FluidWidgetPreview.Glance { F8p25AbsencesContent(context) },
      ),
    )
  }
  FluidScreen(title = "La vetrina dei widget", subtitle = "F8.25 · widget-vetrina", onBack = onBack) {
    item(key = "fondo") { GroundControl(ground) { ground = it } }
    item(key = "vetrina") {
      FluidWidgetShowcase(
        entries = entries,
        appName = "Fluid Glass",
        home = when (ground) {
          F8p25Ground.Page -> null
          F8p25Ground.LightPhoto -> { { HomePhoto(R.drawable.viewer_flower) } }
          F8p25Ground.DarkPhoto -> { { HomePhoto(R.drawable.viewer_astronaut) } }
        },
        modifier = Modifier.padding(top = 12.dp),
      )
    }
    item(key = "nota") {
      Caption(
        "Su Android la taglia non si chiede al launcher: il widget arriva nella sua (2×2) e si allarga sulla Home. " +
          "Il riquadro delle Impostazioni rapide non sta qui: si propone dalle impostazioni dell'app.",
      )
    }
  }
}

/**
 * Le impostazioni di un'app (qui Pampa Sounds, con la galleria a fare da app): il riquadro delle Impostazioni rapide si
 * propone da qui, in una sezione sua, e mai dalla vetrina dei widget (scelta di Alessio del 26/11/2026). Le righe sopra
 * sono di contorno: fanno vedere la scheda dove starà davvero.
 */
@Composable
private fun AppSettingsScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val quickTile = remember(context) {
    FluidQuickTileOffer(
      service = ComponentName(context, F8p25QuickTileService::class.java),
      label = "Fluid Glass",
      description = "Il riquadro della galleria, a un tocco dalle Impostazioni rapide (come farà il timer di Sounds).",
      icon = R.drawable.f8p25_quick_tile,
    )
  }
  FluidScreen(title = "Impostazioni", subtitle = "F8.25 · impostazioni-app", onBack = onBack) {
    item(key = "aspetto") {
      Column {
        FluidSectionHeader(title = "Aspetto")
        FluidListGroup {
          FluidListRow(title = "Tema", subtitle = "Automatico")
          FluidListDivider()
          FluidListRow(title = "Accento", subtitle = "Verde Fluid")
        }
      }
    }
    item(key = "riquadro") {
      Column(Modifier.padding(top = 8.dp)) {
        FluidSectionHeader(title = "Impostazioni rapide")
        FluidQuickTileCard(quickTile)
        FluidSectionFootnote(
          "Un riquadro per far partire il timer senza aprire l'app. Si propone qui, nelle impostazioni, mai dalla vetrina " +
            "dei widget.",
        )
      }
    }
  }
}

@Composable
private fun HomePhoto(drawable: Int) {
  Image(
    painter = painterResource(drawable),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize(),
  )
}

@Composable
private fun GroundControl(ground: F8p25Ground, onSelect: (F8p25Ground) -> Unit) {
  FluidSegmentedControl(
    options = F8p25Ground.entries,
    selected = ground,
    onSelect = onSelect,
    modifier = Modifier.fillMaxWidth().testTag(F8p25Tags.Ground),
    label = { it.label },
  )
}

@Composable
private fun Caption(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
  )
}

// --- Le Tile ---------------------------------------------------------------------------------------------------------

/** Gli accenti della scena: quello di serie, uno chiaro e uno scuro (che sul nero va alzato). */
internal enum class F8p25Accent(val label: String, val brand: AccentPreset?) {
  Fluid("Fluid", null),
  Sun("Sole", AccentPreset("sole", "Sole", Color(0xFFF2B705), Color(0xFFFFD34D))),
  Night("Notte", AccentPreset("notte", "Notte", Color(0xFF1A2A6C), Color(0xFF3550C0))),
}

private fun colorsOf(accent: F8p25Accent): FluidTileColors =
  accent.brand?.let { fluidTileColors(it) } ?: fluidTileColors()

private val WatchTiles: List<Pair<String, FluidTileContent>> = listOf(
  "lezione" to FluidTileContent(
    title = "Matematica",
    value = "10:00",
    detail = "Aula 3 · fra 12 min",
    progress = 0.4f,
    action = FluidTileAction("Apri"),
  ),
  "posta" to FluidTileContent(title = "Pampa Mail", value = "3", detail = "non lette", action = FluidTileAction("Leggi")),
  "timer" to FluidTileContent(title = "Pioggia", value = "18:20", detail = "timer di Sounds", progress = 0.7f, action = FluidTileAction("Pausa")),
  "chiedi" to FluidTileContent(title = "PampAI", value = "Chiedi", action = FluidTileAction("Parla")),
)

@Composable
private fun WearTilesScene(onBack: () -> Unit) {
  var accent by rememberSaveable { mutableStateOf(F8p25Accent.Fluid) }
  val colors = remember(accent) { colorsOf(accent) }
  FluidScreen(title = "Le Tile sull'orologio", subtitle = "F8.25 · wear-tile", onBack = onBack) {
    item(key = "accento") {
      FluidSegmentedControl(
        options = F8p25Accent.entries,
        selected = accent,
        onSelect = { accent = it },
        modifier = Modifier.fillMaxWidth().testTag(F8p25Tags.Accent),
        label = { it.label },
      )
    }
    item(key = "tile") {
      Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (pair in WatchTiles.chunked(2)) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((id, content) in pair) {
              key(id) { WatchTile(content, colors, Modifier.weight(1f).testTag(F8p25Tags.tile(id))) }
            }
          }
        }
      }
    }
    item(key = "complicazioni") {
      val status = FluidWearStatus("lezione", "Matematica", "10:00", detail = "Aula 3", progress = 0.4f, updatedAtMillis = 0)
      Column(Modifier.padding(top = 16.dp)) {
        Text("Le complicazioni", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        ComplicationFace(status, nowMillis = 0, colors = colors)
      }
    }
    item(key = "nota") {
      Caption(
        "Le Tile sono quelle vere: ProtoLayout disegnato dal renderer di Wear OS, a 225 dp. Il disco e' il ripiego del " +
          "vetro dell'orologio, perche' una Tile non sfoca. Le complicazioni le disegna il quadrante: qui un'anteprima " +
          "indicativa dei dati che ricevono.",
      )
    }
  }
}

/** Una Tile disegnata dal renderer di Wear OS (lo stesso del sistema dell'orologio), disposta a [widthDp] e rimpicciolita. */
@Composable
private fun WatchTile(content: FluidTileContent, colors: FluidTileColors, modifier: Modifier = Modifier, widthDp: Int = 225) {
  val context = LocalContext.current
  val density = LocalDensity.current.density
  // La frase per TalkBack sul nodo esterno, quello che porta il tag della scena: la Tile e' un'immagine sola.
  BoxWithConstraints(modifier.semantics { contentDescription = content.spoken }, contentAlignment = Alignment.Center) {
    val scale = fluidWidgetPreviewScale(DpSize(widthDp.dp, widthDp.dp), maxWidth)
    Box(
      Modifier
        .size(widthDp.dp * scale)
        .clip(CircleShape)
        .background(Color.Black),
      contentAlignment = Alignment.Center,
    ) {
      AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
          val key = content to colors
          if (frame.tag != key) {
            frame.tag = key
            frame.removeAllViews()
            val device = DeviceParametersBuilders.DeviceParameters.Builder()
              .setScreenWidthDp(widthDp)
              .setScreenHeightDp(widthDp)
              .setScreenDensity(density)
              .setFontScale(1f)
              .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
              .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
              .setRendererSchemaVersion(VersionBuilders.VersionInfo.Builder().setMajor(1).setMinor(500).build())
              .build()
            val element = materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = colors.scheme) {
              fluidTile(content, colors)
            }
            TileRenderer(context, ContextCompat.getMainExecutor(context)) { }.inflateAsync(
              LayoutElementBuilders.Layout.fromLayoutElement(element),
              ResourceBuilders.Resources.Builder().setVersion("1").build(),
              frame,
            )
          }
        },
        modifier = Modifier
          .requiredSize(widthDp.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          },
      )
    }
  }
}

/** Un quadrante finto con le tre complicazioni Fluid: un'anteprima dei dati, non il disegno del quadrante. */
@Composable
private fun ComplicationFace(status: FluidWearStatus, nowMillis: Long, colors: FluidTileColors) {
  val resources = LocalContext.current.resources
  fun ComplicationText.read(): String = getTextAt(resources, Instant.EPOCH).toString()
  val ranged = FluidComplications.dataFor(ComplicationType.RANGED_VALUE, status, nowMillis, colors) as? RangedValueComplicationData
  val short = FluidComplications.dataFor(ComplicationType.SHORT_TEXT, status, nowMillis, colors) as ShortTextComplicationData
  val long = FluidComplications.dataFor(ComplicationType.LONG_TEXT, status, nowMillis, colors) as LongTextComplicationData
  val accent = Color(colors.argb.accent)
  val secondary = Color(colors.argb.secondary)
  Box(
    Modifier.fillMaxWidth().height(220.dp).clip(ContinuousCornerShape(FluidRadius.Card)).background(Color(0xFF101012)),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(200.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
      Text("10:09", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 22.dp))
      // In alto il testo lungo, in basso a sinistra l'intervallo e a destra il testo breve: niente sopra l'ora.
      Text(
        "${long.title?.read().orEmpty()}  ${long.text.read()}",
        color = secondary,
        fontSize = 11.sp,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
      )
      if (ranged != null) {
        val ramp = ranged.colorRamp?.colors?.map { Color(it) } ?: listOf(accent, accent)
        Box(Modifier.align(Alignment.BottomStart).padding(start = 42.dp, bottom = 34.dp).size(46.dp), contentAlignment = Alignment.Center) {
          Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(colors.argb.ringTrack), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
              Brush.sweepGradient(ramp),
              -90f,
              360f * (ranged.value - ranged.min) / (ranged.max - ranged.min),
              false,
              Offset(inset, inset),
              arcSize,
              style = Stroke(stroke, cap = StrokeCap.Round),
            )
          }
          Text(ranged.text?.read().orEmpty(), color = Color.White, fontSize = 10.sp)
        }
      }
      Column(
        Modifier.align(Alignment.BottomEnd).padding(end = 42.dp, bottom = 34.dp).size(46.dp).clip(CircleShape).background(Color(0xFF1C1C1E)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(short.title?.read().orEmpty().take(5), color = secondary, fontSize = 8.sp)
        Text(short.text.read(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

// --- Lo stato dal telefono -------------------------------------------------------------------------------------------

private val NextLessons = listOf(
  Triple("Matematica", "10:00", "Aula 3"),
  Triple("Italiano", "11:00", "Aula 3"),
  Triple("Inglese", "12:00", "Laboratorio"),
)

@Composable
private fun WearStatusScene(onBack: () -> Unit) {
  // L'orologio: un ponte in memoria. Il telefono gli passa gli stessi byte che metterebbe sul Data Layer.
  val watch = remember { FluidWearSync(FluidWearRole.Watch, FluidWearMemoryStore()) }
  val statuses by watch.statuses.statuses.collectAsState()
  var next by rememberSaveable { mutableIntStateOf(0) }
  var lastBytes by remember { mutableIntStateOf(0) }
  val colors = remember { fluidTileColors() }
  val status = statuses["lezione"]
  val now = System.currentTimeMillis()

  fun publish(stale: Boolean) {
    val (subject, time, room) = NextLessons[next % NextLessons.size]
    val at = System.currentTimeMillis()
    val update = FluidWearStatus(
      key = "lezione",
      title = subject,
      value = time,
      detail = "$room · fra 12 min",
      progress = 0.25f + 0.25f * (next % 3),
      updatedAtMillis = at,
      staleAfterMillis = if (stale) at - 1 else at + 3_600_000,
    )
    val bytes = FluidWearFormat.encode(update)
    lastBytes = bytes.size
    watch.route(FluidWearPaths.status("lezione"), bytes)
  }

  FluidScreen(title = "Lo stato dal telefono", subtitle = "F8.25 · wear-stato", onBack = onBack) {
    item(key = "telefono") {
      Panel("Telefono · ClasseViva") {
        Text(
          "Pubblica la prossima lezione: l'orologio la tiene su disco e la Tile la legge da li', anche ad app chiusa.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        FluidButton(
          text = "Pubblica: ${NextLessons[next % NextLessons.size].first}",
          onClick = { publish(stale = false); next++ },
          fillWidth = true,
          modifier = Modifier.testTag(F8p25Tags.Publish),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidButton(
            "Lezione finita",
            onClick = { publish(stale = true) },
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
            modifier = Modifier.testTag(F8p25Tags.Finish),
          )
          FluidButton(
            "Togli",
            onClick = { watch.routeDeleted(FluidWearPaths.status("lezione")) },
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
            modifier = Modifier.testTag(F8p25Tags.Withdraw),
          )
        }
        if (lastBytes > 0) {
          Text(
            "Ultimo messaggio: $lastBytes byte, formato FWS ${FluidWearFormat.Version}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
    }
    item(key = "orologio") {
      Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val content = status?.let { FluidTileContent.of(it, now, action = FluidTileAction("Apri")) }
          ?: FluidTileContent(title = "In attesa", value = "—", detail = "Apri l'app sul telefono")
        WatchTile(content, colors, Modifier.fillMaxWidth().testTag(F8p25Tags.tile("stato")))
        if (status != null) {
          Spacer(Modifier.height(12.dp))
          ComplicationFace(status, now, colors)
        }
      }
    }
  }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(
    Modifier
      .fillMaxWidth()
      .padding(top = 12.dp)
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(16.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    content()
  }
}

// --- Dal polso con ricevuta ----------------------------------------------------------------------------------------

/**
 * Il collegamento finto fra i due lati, come il Data Layer: tiene quello che si mette col telefono lontano e lo consegna
 * quando si ritrovano; un elemento rimesso identico non arriva di nuovo.
 */
private class FakeLink {
  var connected = true
  private val toPhone = LinkedHashMap<String, ByteArray>()
  private val toWatch = LinkedHashMap<String, ByteArray>()
  private val delivered = HashMap<String, ByteArray>()

  fun put(fromWatch: Boolean, path: String, bytes: ByteArray) {
    if (delivered[path]?.contentEquals(bytes) == true) return
    (if (fromWatch) toPhone else toWatch)[path] = bytes
  }

  fun drain(fromWatch: Boolean): List<Pair<String, ByteArray>> {
    if (!connected) return emptyList()
    val queue = if (fromWatch) toPhone else toWatch
    val out = queue.entries.map { it.key to it.value }
    out.forEach { (p, b) -> delivered[p] = b }
    queue.clear()
    return out
  }
}

/**
 * L'ora della scena: l'ora di sistema all'apertura più il tempo dei fotogrammi da allora. Sul telefono è l'ora vera; nelle
 * prove segue l'orologio virtuale di Compose, così «Riproduci» scade davvero dopo dieci secondi di prova.
 */
private class SceneClock {
  private val start = System.currentTimeMillis()
  private var firstFrame = -1L
  var now: Long = start
    private set

  fun onFrame(frameMillis: Long) {
    if (firstFrame < 0) firstFrame = frameMillis
    now = start + (frameMillis - firstFrame)
  }
}

@Composable
private fun WearReceiptScene(onBack: () -> Unit) {
  val clock = remember { SceneClock() }
  val watchStore = remember { FluidWearMemoryStore() }
  val phone = remember { FluidWearSync(FluidWearRole.Phone, FluidWearMemoryStore(), clock = { clock.now }) }
  val link = remember { FakeLink() }
  var boots by remember { mutableIntStateOf(0) }
  // «Riavvia l'orologio»: un ponte nuovo sullo stesso archivio, come un processo che riparte.
  val watch = remember(boots) { FluidWearSync(FluidWearRole.Watch, watchStore, clock = { clock.now }) }
  val deliveries by watch.outbox.deliveries.collectAsState()
  var near by remember { mutableStateOf(true) }
  var saved by remember { mutableStateOf(listOf<FluidWearEvent>()) }
  var repeats by remember { mutableIntStateOf(0) }
  var songs by remember { mutableIntStateOf(0) }

  fun pump(resendAll: Boolean = false) {
    link.connected = near
    for (outgoing in watch.outbox.due(resendAll)) {
      link.put(true, outgoing.path, outgoing.bytes())
      watch.outbox.markSent(outgoing.event.id)
    }
    for ((path, bytes) in link.drain(fromWatch = true)) {
      when (val routed = phone.route(path, bytes)) {
        is FluidWearRouted.EventArrived -> {
          // Il telefono salva (l'app di Fluidify: il preferito) e solo dopo registra e rimanda la ricevuta.
          saved = saved + routed.event
          val receipt = phone.inbox.confirm(routed.event.id)
          link.put(false, FluidWearPaths.receipt(receipt.eventId), FluidWearFormat.encode(receipt))
        }
        is FluidWearRouted.ReceiptToSend -> {
          if (routed.repeated) repeats++
          link.put(false, FluidWearPaths.receipt(routed.receipt.eventId), FluidWearFormat.encode(routed.receipt))
        }
        else -> Unit
      }
    }
    for ((path, bytes) in link.drain(fromWatch = false)) watch.route(path, bytes)
  }

  LaunchedEffect(watch) {
    while (true) {
      clock.onFrame(withFrameMillis { it })
      pump()
      delay(PumpMillis)
    }
  }

  FluidScreen(title = "Dal polso con ricevuta", subtitle = "F8.25 · wear-ricevuta", onBack = onBack) {
    item(key = "comandi") {
      Panel("Il polso") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Telefono vicino", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
          FluidSwitch(checked = near, onCheckedChange = { near = it; pump() }, modifier = Modifier.testTag(F8p25Tags.Near))
        }
        Spacer(Modifier.height(10.dp))
        FluidButton(
          "Salva un preferito",
          onClick = {
            songs++
            watch.outbox.enqueue("preferito", """{"brano":"$songs"}""")
            pump()
          },
          fillWidth = true,
          modifier = Modifier.testTag(F8p25Tags.Favorite),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidButton(
            "Riproduci (10 s)",
            onClick = { watch.outbox.enqueue("riproduci", ttlMillis = PlayTtlMillis); pump() },
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
            modifier = Modifier.testTag(F8p25Tags.Play),
          )
          FluidButton(
            "Riavvia l'orologio",
            onClick = { boots++ },
            style = FluidButtonStyle.Tinted,
            size = FluidButtonSize.Small,
            modifier = Modifier.testTag(F8p25Tags.Restart),
          )
        }
        Spacer(Modifier.height(8.dp))
        FluidButton(
          "Riconsegna tutto",
          onClick = { pump(resendAll = true) },
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Small,
          modifier = Modifier.testTag(F8p25Tags.Resend),
        )
      }
    }
    item(key = "orologio") {
      WatchQueue(deliveries, Modifier.padding(top = 12.dp).testTag(F8p25Tags.WatchQueue))
    }
    item(key = "telefono") {
      Panel("Il telefono") {
        Text(
          if (saved.isEmpty()) "Niente di salvato." else saved.joinToString("\n") { "${it.type} · ${it.payload.ifEmpty { "—" }}" },
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.testTag(F8p25Tags.PhoneSaved),
        )
        Text(
          "Consegne ripetute riconosciute: $repeats · avvii dell'orologio: ${boots + 1}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
    }
  }
}

/** La coda del polso su un quadrante rotondo: l'evento e il suo stato, «In coda», «Registrato», «Scaduto». */
@Composable
private fun WatchQueue(deliveries: List<FluidWearDelivery>, modifier: Modifier = Modifier) {
  val accent = MaterialTheme.colorScheme.primary
  Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(
      Modifier.size(240.dp).clip(CircleShape).background(Color.Black).padding(horizontal = 34.dp, vertical = 40.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("Fluidify", color = Color(0xFFAAAAB2), fontSize = 12.sp)
      if (deliveries.isEmpty()) {
        Text("Niente in coda", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
      }
      for (d in deliveries.takeLast(5)) {
        key(d.event.id) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
              if (d.event.type == "preferito") "♥ ${d.event.payload.filter { it.isDigit() }}" else "▶ ${d.event.type}",
              color = Color.White,
              fontSize = 13.sp,
              modifier = Modifier.weight(1f),
            )
            Text(
              d.state.label,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = when (d.state) {
                FluidWearDeliveryState.Registered -> accent
                FluidWearDeliveryState.Queued -> Color(0xFFAAAAB2)
                FluidWearDeliveryState.Expired -> Color(0xFFFFB74D)
                FluidWearDeliveryState.Refused -> Color(0xFFFF8A80)
              },
            )
          }
        }
      }
    }
  }
}

/** Ogni quanto il collegamento finto consegna: il Data Layer non e' istantaneo. A occhio, 26/11/2026. */
private const val PumpMillis = 400L

/** La vita del comando «Riproduci» nella scena: 10 s, abbastanza per vederlo scadere col telefono lontano. */
private const val PlayTtlMillis = 10_000L
