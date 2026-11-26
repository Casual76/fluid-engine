package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.health.FluidHealthAvailability
import dev.antigravity.fluidengine.health.FluidHealthCard
import dev.antigravity.fluidengine.health.FluidHealthConnectGateway
import dev.antigravity.fluidengine.health.FluidHealthDayCard
import dev.antigravity.fluidengine.health.FluidHealthDeviceKind
import dev.antigravity.fluidengine.health.FluidHealthFormat
import dev.antigravity.fluidengine.health.FluidHealthInterval
import dev.antigravity.fluidengine.health.FluidHealthLabels
import dev.antigravity.fluidengine.health.FluidHealthReadings
import dev.antigravity.fluidengine.health.FluidHeartSummary
import dev.antigravity.fluidengine.health.day
import dev.antigravity.fluidengine.health.night
import dev.antigravity.fluidengine.health.week
import dev.antigravity.fluidengine.health.FluidHealthMetric
import dev.antigravity.fluidengine.health.FluidHealthPermission
import dev.antigravity.fluidengine.health.FluidHealthPermissionRows
import dev.antigravity.fluidengine.health.FluidHealthPoint
import dev.antigravity.fluidengine.health.FluidHealthReading
import dev.antigravity.fluidengine.health.FluidHealthRing
import dev.antigravity.fluidengine.health.FluidHealthSession
import dev.antigravity.fluidengine.health.FluidHealthSample
import dev.antigravity.fluidengine.health.FluidHealthSource
import dev.antigravity.fluidengine.health.FluidHealthWeek
import dev.antigravity.fluidengine.health.FluidMemoryHealthGateway
import dev.antigravity.fluidengine.health.FluidSleepStage
import dev.antigravity.fluidengine.map.FluidGeoFormat
import dev.antigravity.fluidengine.map.FluidGeoPoint
import dev.antigravity.fluidengine.map.FluidMap
import dev.antigravity.fluidengine.map.FluidMapCamera
import dev.antigravity.fluidengine.map.FluidMapEngine
import dev.antigravity.fluidengine.map.FluidMapGlassHost
import dev.antigravity.fluidengine.map.FluidMapLibreEngine
import dev.antigravity.fluidengine.map.FluidMapMarker
import dev.antigravity.fluidengine.map.FluidMapPlace
import dev.antigravity.fluidengine.map.FluidMapPlaceCard
import dev.antigravity.fluidengine.map.FluidMapPostcard
import dev.antigravity.fluidengine.map.FluidMapRenderMode
import dev.antigravity.fluidengine.map.FluidMapSheet
import dev.antigravity.fluidengine.map.FluidMapUserLocation
import dev.antigravity.fluidengine.map.FluidSketchMapEngine
import dev.antigravity.fluidengine.map.LocalFluidMapEngine
import dev.antigravity.fluidengine.map.fluidViewerLocation
import dev.antigravity.fluidengine.map.rememberFluidMapState
import dev.antigravity.fluidengine.sample.BuildConfig
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmSheet
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronLeft
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// F8.26 · Luoghi e salute: la mappa (engine-map, MapLibre con le tessere di OpenStreetMap da OpenFreeMap) e la salute
// (engine-health, Health Connect). La galleria usa **solo punti finti e una posizione simulata** (mai quella vera del
// telefono: la galleria non chiede il permesso della posizione) e **solo dati di salute finti** (FluidMemoryHealthGateway):
// i dati veri di Health Connect non si leggono. Dalla Health Connect vera la scena dei permessi legge solo se c'e'.
// Le tessere vere arrivano nella build di debug e nella bench; nella release pubblicata la mappa e' il disegno dell'engine.
// Per le misure: l'extra di lancio `novita.map` = `texture` (di serie), `surface` o `disegno`.

internal val F8p26Section = NewElementSection(
  pack = "F8.26",
  title = "Luoghi e salute",
  scenes = listOf(
    NewElementScene(
      "mappa", "La mappa dei luoghi",
      "MyCloud Gallery, «Luoghi» · I segnaposto a grappolo, i comandi di vetro, il foglio sopra la mappa e la scheda del " +
        "luogo, con le tessere di OpenStreetMap",
    ) { onBack -> F8p26MapScene(onBack) },
    NewElementScene(
      "mappa-disegno", "La mappa senza rete",
      "La stessa mappa sul disegno dell'engine: le prove al computer e una galleria senza tessere",
    ) { onBack -> F8p26MapScene(onBack, forceSketch = true) },
    NewElementScene(
      "cartolina", "La cartolina in una riga",
      "Pampa Notes e Pampa Mail · Dove è stata presa una nota, il luogo di un invito: una mappa piccola e ferma che toccata si apre",
    ) { onBack -> F8p26PostcardScene(onBack) },
    NewElementScene(
      "cartolina-visore", "Il luogo nel visore",
      "MyCloud Gallery · Il pannello info di una foto con la cartolina al posto della mappa dell'app, e «Apri la mappa»",
    ) { onBack -> F8p26ViewerScene(onBack) },
    NewElementScene(
      "salute", "Il giorno in salute",
      "Un'app di movimento · Gli anelli del giorno, le card con la fonte di ogni dato, la settimana e il sonno di Sounds",
    ) { onBack -> F8p26HealthScene(onBack) },
    NewElementScene(
      "salute-fonti", "Buchi e doppioni",
      "Telefono e orologio insieme contati una volta, il pomeriggio senza dati detto com'è, «Nessun dato» e non «0»",
    ) { onBack -> F8p26SourcesScene(onBack) },
    NewElementScene(
      "salute-permessi", "I permessi misura per misura",
      "Negati, parziali, consentiti: ogni riga chiede solo la sua misura (Health Connect finto, mai i dati veri)",
    ) { onBack -> F8p26PermissionsScene(onBack) },
  ),
  detail = "La mappa di vetro con MapLibre e OpenStreetMap, la cartolina, e Health Connect con gli anelli e le fonti",
)

internal val F8p26TryItems = listOf(
  TryItem(
    "f8-26-mappa", TryArea.Pages, "La mappa dei luoghi",
    "Trascina e pizzica la mappa (resta nitida sotto il dito), tocca un grappolo per avvicinarti finché si divide, tocca " +
      "un segnaposto e guarda la scheda nel foglio, poi ruota con due dita e tocca la bussola per tornare al nord.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La mappa dei luoghi"),
  ),
  TryItem(
    "f8-26-posizione", TryArea.Pages, "La posizione simulata",
    "Nella mappa tocca il tasto della freccia: arriva una posizione finta in piazza della Repubblica, la mappa ci vola e " +
      "la segue, e appena la trascini smette di seguirla.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La mappa dei luoghi"),
  ),
  TryItem(
    "f8-26-foglio", TryArea.Pages, "Il foglio sopra la mappa",
    "Porta il foglio a metà e in cima e poi tiralo giù oltre il fondo: torna al suo posto più basso, e finché sta a metà " +
      "la mappa sopra si trascina ancora.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La mappa dei luoghi"),
  ),
  TryItem(
    "f8-26-cartolina", TryArea.Pages, "La cartolina",
    "Scorri la nota e l'invito: le cartoline restano ferme e non scattano, entrano a fuoco la prima volta, e toccate " +
      "aprono la mappa sul luogo.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La cartolina in una riga"),
  ),
  TryItem(
    "f8-26-visore", TryArea.Pages, "Il luogo nel visore",
    "Apri il pannello info della foto: sotto le coordinate c'è la cartolina del Giardino di Boboli, e «Apri la mappa» porta " +
      "alla mappa col segnaposto scelto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il luogo nel visore"),
  ),
  TryItem(
    "f8-26-salute", TryArea.Pages, "Il giorno in salute",
    "Guarda gli anelli crescere e tocca «Arriva un dato nuovo»: i numeri scorrono sfocati e gli anelli avanzano, e ogni " +
      "card dice da quale app e da quale apparecchio arriva il dato.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il giorno in salute"),
  ),
  TryItem(
    "f8-26-fonti", TryArea.Pages, "Buchi e doppioni",
    "Leggi le quattro card: i passi di telefono e orologio non si sommano, il pomeriggio senza dati è detto come fatto, " +
      "il giorno vuoto è «Nessun dato» e la notte vista da due app è una sola.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Buchi e doppioni"),
  ),
  TryItem(
    "f8-26-permessi", TryArea.Pages, "I permessi misura per misura",
    "Tocca la riga del battito e rispondi «Non consentire» due volte, poi ritocca: la riga ora porta a Health Connect, " +
      "mentre le misure già consentite non chiedono niente.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › I permessi misura per misura"),
  ),
)

/** I tag delle prove. */
internal object F8p26Tags {
  const val Back = "f8p26:indietro"
  const val Arrive = "f8p26:arriva"
  const val Preparing = "f8p26:preparo"
}

/** Il centro di Firenze e i luoghi finti della galleria: foto inventate attorno ai luoghi veri della citta'. */
internal object F8p26Places {
  val Florence = FluidGeoPoint(43.77130, 11.25540)

  /** La posizione simulata: piazza della Repubblica (mai la posizione vera del telefono). */
  val SimulatedHere = FluidMapUserLocation(FluidGeoPoint(43.77142, 11.25389), accuracyMeters = 35f, headingDegrees = 40f)

  private val landmarks = listOf(
    Triple("duomo", "Duomo", FluidGeoPoint(43.77310, 11.25600)),
    Triple("signoria", "Piazza della Signoria", FluidGeoPoint(43.76956, 11.25581)),
    Triple("ponte-vecchio", "Ponte Vecchio", FluidGeoPoint(43.76797, 11.25314)),
    Triple("pitti", "Palazzo Pitti", FluidGeoPoint(43.76510, 11.25000)),
    Triple("boboli", "Giardino di Boboli", FluidGeoPoint(43.76250, 11.24840)),
    Triple("santa-croce", "Santa Croce", FluidGeoPoint(43.76870, 11.26220)),
    Triple("michelangelo", "Piazzale Michelangelo", FluidGeoPoint(43.76290, 11.26500)),
    Triple("san-lorenzo", "San Lorenzo", FluidGeoPoint(43.77490, 11.25390)),
    Triple("novella", "Santa Maria Novella", FluidGeoPoint(43.77450, 11.24930)),
    Triple("cascine", "Parco delle Cascine", FluidGeoPoint(43.78090, 11.22700)),
    Triple("fiesole", "Fiesole", FluidGeoPoint(43.80700, 11.29200)),
    Triple("san-miniato", "San Miniato al Monte", FluidGeoPoint(43.75960, 11.26480)),
  )

  /** Una manciata di foto per luogo, sparse di qualche decina di metri (sempre le stesse). */
  val Markers: List<FluidMapMarker> = landmarks.flatMapIndexed { i, (id, title, at) ->
    val photos = 2 + (i * 7) % 5
    (0 until photos).map { k ->
      val dLat = ((k * 37 + i * 11) % 9 - 4) * 0.00011
      val dLon = ((k * 53 + i * 17) % 9 - 4) * 0.00014
      FluidMapMarker("$id-$k", FluidGeoPoint(at.latitude + dLat, at.longitude + dLon), title, subtitle = "${3 + (k * 13 + i) % 40} foto")
    }
  }

  fun place(marker: FluidMapMarker): FluidMapPlace =
    FluidMapPlace(marker.title, marker.point, subtitle = marker.subtitle?.let { "Firenze · $it" } ?: "Firenze")
}

/** Il motore della mappa della galleria: le tessere vere in debug e bench, il disegno nella release e a richiesta. */
@Composable
private fun rememberF8p26Engine(forceSketch: Boolean): FluidMapEngine {
  val context = LocalContext.current
  val provided = LocalFluidMapEngine.current
  val wanted = remember(context) {
    context.findActivityF8p26()?.takeIf { context.isDebuggableApp() }?.intent?.getStringExtra("novita.map")?.lowercase()
  }
  return remember(forceSketch, wanted, provided) {
    when {
      provided != null -> provided
      forceSketch || wanted == "disegno" || BuildConfig.BUILD_TYPE == "release" -> FluidSketchMapEngine()
      wanted == "surface" -> FluidMapLibreEngine(context, FluidMapRenderMode.Surface)
      else -> FluidMapLibreEngine(context, FluidMapRenderMode.Texture)
    }
  }
}

private tailrec fun Context.findActivityF8p26(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivityF8p26()
  else -> null
}

/**
 * La mappa dei luoghi come «Luoghi» di MyCloud Gallery: i segnaposto a grappolo, i comandi di vetro, il foglio coi luoghi
 * e la scheda del luogo scelto. [initialSelection] apre la mappa su un luogo (dalla cartolina).
 */
@Composable
private fun F8p26MapScene(onBack: () -> Unit, forceSketch: Boolean = false, initialSelection: String? = null) {
  val engine = rememberF8p26Engine(forceSketch)
  val start = F8p26Places.Markers.firstOrNull { it.id == initialSelection }
  val state = rememberFluidMapState(FluidMapCamera(start?.point ?: F8p26Places.Florence, if (start != null) 16.0 else 13.2))
  var selected by rememberSaveable { mutableStateOf(initialSelection) }
  var detent by remember { mutableStateOf<FluidSheetDetent?>(if (initialSelection != null) FluidSheetDetent.Medium else null) }
  val scope = rememberCoroutineScope()
  val backdrop = rememberGlassBackdrop()
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val chosen = F8p26Places.Markers.firstOrNull { it.id == selected }
  FluidMapGlassHost(backdrop) {
    FluidMap(
      state = state,
      modifier = Modifier.fillMaxSize(),
      markers = F8p26Places.Markers,
      selectedMarkerId = selected,
      onMarkerClick = {
        selected = it.id
        detent = FluidSheetDetent.Medium
      },
      onMapClick = { selected = null },
      onLocationRequest = { state.userLocation = F8p26Places.SimulatedHere },
      engine = engine,
      contentPadding = PaddingValues(top = top),
      backdrop = backdrop,
    )
    FluidGlassIconButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(top = top + 8.dp, start = 10.dp)
        .testTag(F8p26Tags.Back),
      backdrop = backdrop,
    ) {
      FluidIcon(FluidIcons.ChevronLeft, contentDescription = "Indietro")
    }
    FluidMapSheet(
      mapState = state,
      selectedDetent = detent,
      onSelectedDetentChange = { detent = it },
      paneTitle = "Luoghi",
    ) {
      if (chosen != null) {
        FluidMapPlaceCard(
          F8p26Places.place(chosen),
          // La galleria non apre un'altra app di mappe: sul tablet potrebbe mostrare la posizione vera.
          onDirections = { },
        )
      } else {
        Text(
          "Luoghi", style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
        )
        Text(
          "${F8p26Places.Markers.size} foto in 12 luoghi · Firenze", style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
        val byPlace = remember { F8p26Places.Markers.groupBy { it.title } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
          FluidListGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
            byPlace.entries.forEachIndexed { i, (title, list) ->
              if (i > 0) FluidListDivider()
              FluidListRow(
                title = title,
                subtitle = "${list.size} foto",
                onClick = {
                  selected = list.first().id
                  detent = FluidSheetDetent.Medium
                  scope.launch { state.animateTo(state.camera.copy(center = list.first().point, zoom = maxOf(state.camera.zoom, 16.0))) }
                },
              )
            }
          }
        }
      }
    }
  }
}

/** La cartolina in una riga: una nota di Pampa Notes e un invito di Pampa Mail; toccata apre la mappa sul luogo. */
@Composable
private fun F8p26PostcardScene(onBack: () -> Unit) {
  var open by rememberSaveable { mutableStateOf<String?>(null) }
  val target = open
  if (target != null) {
    F8p26MapScene(onBack = { open = null }, initialSelection = target)
    return
  }
  val engine = rememberF8p26Engine(forceSketch = false)
  val signoria = F8p26Places.Markers.first { it.id.startsWith("signoria") }
  val boboli = F8p26Places.Markers.first { it.id.startsWith("boboli") }
  FluidScreen(title = "La cartolina", subtitle = "F8.26 · una mappa ferma in una riga", onBack = onBack) {
    item(key = "nota") {
      Column {
        FluidSectionHeader(title = "Pampa Notes · Dove è stata presa")
        FluidListGroup {
          FluidListRow(title = "Appunti sugli Uffizi", subtitle = "Martedì, 10:42 · Piazza della Signoria", disclosure = false)
          FluidListDivider()
          Box(Modifier.padding(12.dp)) {
            FluidMapPostcard(
              signoria.point, Modifier.fillMaxWidth().height(150.dp), title = "Piazza della Signoria", engine = engine,
              onClick = { open = signoria.id },
            )
          }
        }
      }
    }
    item(key = "invito") {
      Column {
        FluidSectionHeader(title = "Pampa Mail · Il luogo di un invito")
        FluidListGroup {
          FluidListRow(title = "Picnic a Boboli", subtitle = "Sabato, 12:30 · Giardino di Boboli", disclosure = false)
          FluidListDivider()
          Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FluidMapPostcard(boboli.point, Modifier.height(88.dp).fillMaxWidth(0.38f), title = "Giardino di Boboli", engine = engine, zoom = 15.0,
              onClick = { open = boboli.id })
            Column(Modifier.weight(1f)) {
              Text("Giardino di Boboli", style = MaterialTheme.typography.titleSmall)
              Text(FluidGeoFormat.display(boboli.point), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        FluidSectionFootnote("Le cartoline si fanno una volta e restano in memoria e su disco: scorrendo non si rifanno, e nessuna mappa viva gira.")
      }
    }
  }
}

/** Una foto nel visore col luogo nel pannello info: la cartolina al posto della mappa che prima disegnava l'app. */
@Composable
private fun F8p26ViewerScene(onBack: () -> Unit) {
  var map by rememberSaveable { mutableStateOf(false) }
  val boboli = F8p26Places.Markers.first { it.id.startsWith("boboli") }
  if (map) {
    F8p26MapScene(onBack = { map = false }, initialSelection = boboli.id)
    return
  }
  val context = LocalContext.current
  val file by produceState<FluidViewerFile?>(null) {
    value = withContext(Dispatchers.IO) {
      val dir = File(context.cacheDir, "f8p26").apply { mkdirs() }
      val out = File(dir, "boboli.jpg")
      if (!out.exists()) context.resources.openRawResource(R.drawable.viewer_flower).use { input -> out.outputStream().use { input.copyTo(it) } }
      FluidViewerFile(Uri.fromFile(out), "IMG_2026_boboli.jpg", "image/jpeg", out.length(), out.lastModified())
    }
  }
  val f = file
  if (f == null) {
    Box(Modifier.fillMaxSize().testTag(F8p26Tags.Preparing), contentAlignment = Alignment.Center) { FluidSpinner() }
    return
  }
  val list = remember(f) { listOf(f) }
  val opened = rememberFluidViewerFiles(list)
  val location = remember { fluidViewerLocation(boboli.point, "Giardino di Boboli") { map = true } }
  FluidViewerInfoProvider(info = { _ ->
    FluidViewerInfo(file = FluidFileInfo(f.name, f.mimeType, f.sizeBytes), subtitle = "Sabato 21 novembre, 12:41", location = location)
  }) {
    // La cartolina del pannello usa lo stesso motore della galleria (il disegno nella release).
    CompositionLocalProvider(LocalFluidMapEngine provides rememberF8p26Engine(false)) {
      FluidFileViewer(count = opened.count, content = opened::content, onBack = onBack)
    }
  }
}

/** Le fonti finte della galleria (mai i dati veri di Health Connect). */
internal object F8p26Health {
  val Watch = FluidHealthSource("com.example.orologio", "Orologio", FluidHealthDeviceKind.Watch, "Orologio finto")
  val Phone = FluidHealthSource("dev.antigravity.fluidglass", "Telefono", FluidHealthDeviceKind.Phone)
  val Sounds = FluidHealthSource("dev.pampa.sounds", "Pampa Sounds", FluidHealthDeviceKind.Phone)
  val Scale = FluidHealthSource("com.example.bilancia", "Bilancia", FluidHealthDeviceKind.Scale)

  /** L'ordine delle app come se l'utente l'avesse scelto nelle impostazioni di Health Connect (il finto lo segue). */
  val SettingsOrder: List<String> = listOf(Watch.packageName, Phone.packageName, Sounds.packageName, Scale.packageName)

  /** Una giornata finta, relativa a oggi: passi di orologio e telefono (sovrapposti), sonno, battito, peso, acqua. */
  fun day(now: Instant, zone: ZoneId): List<FluidHealthSample> {
    val today = LocalDate.ofInstant(now, zone)
    fun at(h: Int, m: Int = 0, d: LocalDate = today) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant()
    val out = ArrayList<FluidHealthSample>()
    var n = 0
    // I passi della settimana: un giorno senza dati (l'orologio sul comodino), e oggi fino ad adesso.
    for (back in 6 downTo 1) {
      if (back == 4) continue
      val d = today.minusDays(back.toLong())
      for (h in 8..20 step 2) out.add(FluidHealthInterval("p${n++}", FluidHealthMetric.Steps, at(h, 0, d), at(h + 1, 0, d), (600 + (h * 37 + back * 91) % 900).toDouble(), Watch))
    }
    for (h in 7..17) {
      val end = at(h + 1)
      if (end.isAfter(now)) break
      out.add(FluidHealthInterval("p${n++}", FluidHealthMetric.Steps, at(h), end, (380 + (h * 53) % 500).toDouble(), Watch))
      // Il telefono in tasca conta gli stessi passi: si sovrappone, e non si somma.
      out.add(FluidHealthInterval("p${n++}", FluidHealthMetric.Steps, at(h), end, (340 + (h * 41) % 450).toDouble(), Phone))
    }
    // Il sonno di stanotte: Sounds col timer della notte, e l'orologio con le fasi.
    out.add(FluidHealthSession("s1", FluidHealthMetric.Sleep, at(23, 10, today.minusDays(1)), at(6, 50), Sounds, "Timer della notte"))
    out.add(
      FluidHealthSession("s2", FluidHealthMetric.Sleep, at(23, 25, today.minusDays(1)), at(6, 40), Watch, stages = listOf(
        FluidSleepStage(at(23, 25, today.minusDays(1)), at(2, 10), 4), FluidSleepStage(at(2, 10), at(2, 30), 1),
        FluidSleepStage(at(2, 30), at(5, 0), 5), FluidSleepStage(at(5, 0), at(6, 40), 6),
      )),
    )
    for (k in 0 until 24) out.add(FluidHealthPoint("h$k", FluidHealthMetric.HeartRate, now.minus(Duration.ofMinutes(20L * k + 5)), (58 + (k * 17) % 34).toDouble(), Watch))
    out.add(FluidHealthPoint("w1", FluidHealthMetric.Weight, at(7, 30, today.minusDays(3)), 72.4, Scale))
    for (k in 0 until 5) {
      val t = at(8 + k * 2)
      if (t.isBefore(now)) out.add(FluidHealthInterval("a$k", FluidHealthMetric.Hydration, t, t, 250.0, Phone))
    }
    out.add(FluidHealthSession("e1", FluidHealthMetric.Exercise, at(7, 5), at(7, 37), Watch, "Corsa", exerciseType = 56))
    return out
  }
}

/** Quello che mostra il giorno in salute, letto dai totali del Health Connect finto, come lo leggerebbe un'app. */
private class F8p26Day(
  val steps: FluidHealthReading,
  val water: FluidHealthReading,
  val active: FluidHealthReading,
  val night: FluidHealthReading,
  val heart: FluidHeartSummary?,
  val weight: FluidHealthPoint?,
  val week: List<FluidHealthReading>,
  val lastWorkout: FluidHealthSession?,
)

/**
 * Il giorno in salute: gli anelli, le card con le fonti, la settimana; «Arriva un dato nuovo» fa scorrere i numeri. I
 * totali vengono dalle aggregazioni del provider (qui il Health Connect finto, con l'ordine delle app «delle impostazioni»:
 * orologio, telefono, Sounds), mai da una somma fatta dalla scena.
 */
@Composable
private fun F8p26HealthScene(onBack: () -> Unit) {
  val zone = remember { ZoneId.systemDefault() }
  // Un «adesso» simulato, le 18:30 di oggi: la giornata finta e' la stessa a qualunque ora si apra la scena (anche di notte).
  val now = remember { LocalDate.now(zone).atTime(18, 30).atZone(zone).toInstant() }
  val today = remember { LocalDate.ofInstant(now, zone) }
  val gateway = remember {
    FluidMemoryHealthGateway(sourcePriority = F8p26Health.SettingsOrder).apply { grantAll(); put(*F8p26Health.day(now, zone).toTypedArray()) }
  }
  var extra by rememberSaveable { mutableStateOf(0) }
  val day by produceState<F8p26Day?>(null, extra) {
    // Il valore si prende qui, sul thread principale: letto dentro withContext, da un altro filo, sarebbe quello di prima.
    val at = now.plus(Duration.ofMinutes(extra * 30L))
    value = withContext(Dispatchers.Default) {
      F8p26Day(
        steps = gateway.day(FluidHealthMetric.Steps, today, zone, at),
        water = gateway.day(FluidHealthMetric.Hydration, today, zone, at, gapRule = null),
        active = gateway.day(FluidHealthMetric.Exercise, today, zone, at, gapRule = null),
        night = gateway.night(today, zone),
        heart = FluidHealthReadings.heart(
          gateway.aggregate(FluidHealthMetric.HeartRate, at.minus(Duration.ofDays(1)), at.plusSeconds(60)),
          gateway.latest(FluidHealthMetric.HeartRate, at.minus(Duration.ofDays(1)), at.plusSeconds(60)) as? FluidHealthPoint,
        ),
        weight = gateway.latest(FluidHealthMetric.Weight, at.minus(Duration.ofDays(30)), at) as? FluidHealthPoint,
        week = gateway.week(FluidHealthMetric.Steps, today.minusDays(6), 7, zone),
        // L'elenco degli allenamenti sono i record, tutti, come nell'app di Health Connect: nessuna unione a mano.
        lastWorkout = gateway.read(FluidHealthMetric.Exercise, today.atStartOfDay(zone).toInstant(), at)
          .filterIsInstance<FluidHealthSession>().maxByOrNull { it.start },
      )
    }
  }
  val labels = remember { FluidHealthLabels() }
  val d = day
  val stepsValue = (d?.steps as? FluidHealthReading.Value)?.value ?: 0.0
  val waterValue = (d?.water as? FluidHealthReading.Value)?.value ?: 0.0
  val activeMinutes = (d?.active as? FluidHealthReading.Value)?.value ?: 0.0
  val scope = rememberCoroutineScope()
  FluidScreen(title = "Salute", subtitle = "F8.26 · dati finti, mai quelli di Health Connect", onBack = onBack) {
    item(key = "giorno") {
      FluidHealthDayCard(
        rings = listOf(
          FluidHealthRing("Passi", stepsValue, 10_000.0, FluidHealthFormat.steps(stepsValue), "10.000"),
          FluidHealthRing("Minuti attivi", activeMinutes, 45.0, FluidHealthFormat.steps(activeMinutes), "45"),
          FluidHealthRing("Acqua", waterValue / 1000.0, 2.0, "${FluidHealthFormat.water(waterValue)} l", "2 l"),
        ),
        title = "Oggi",
        sources = (d?.steps as? FluidHealthReading.Value)?.sources.orEmpty(),
      )
    }
    item(key = "arriva") {
      FluidButton(
        text = "Arriva un dato nuovo",
        onClick = {
          scope.launch {
            val k = extra + 1
            val end = now.plus(Duration.ofMinutes(k * 30L))
            gateway.put(
              FluidHealthInterval("nuovo-p$k", FluidHealthMetric.Steps, end.minus(Duration.ofMinutes(30)), end, 640.0, F8p26Health.Watch),
              FluidHealthInterval("nuovo-a$k", FluidHealthMetric.Hydration, end, end, 200.0, F8p26Health.Phone),
            )
            extra = k
          }
        },
        style = FluidButtonStyle.Tinted,
        modifier = Modifier.testTag(F8p26Tags.Arrive),
      )
    }
    item(key = "passi") {
      val steps = d?.steps as? FluidHealthReading.Value
      val week = d?.week
      FluidHealthCard(
        FluidHealthMetric.Steps,
        value = steps?.let { FluidHealthFormat.steps(it.value) },
        numeric = stepsValue, unit = "passi", time = "Oggi",
        sources = steps?.sources.orEmpty(),
        note = steps?.gaps?.let { labels.gapLine(it, now.plus(Duration.ofMinutes(extra * 30L)), zone) },
        labels = labels,
        chart = if (week != null) ({ FluidHealthWeek(FluidHealthFormat.week(week, today.minusDays(6)), FluidHealthMetric.Steps, goal = 10_000.0, labels = labels) }) else null,
      )
    }
    item(key = "sonno") {
      val night = d?.night as? FluidHealthReading.Value
      FluidHealthCard(
        FluidHealthMetric.Sleep,
        value = night?.let { FluidHealthFormat.sleep(Duration.ofMinutes(it.value.toLong())) },
        numeric = night?.value ?: 0.0,
        time = "Stanotte",
        sources = night?.sources.orEmpty(),
        note = night?.sources?.takeIf { it.size > 1 }?.let { "Due app, una notte sola: i doppioni li toglie Health Connect" },
        labels = labels,
      )
    }
    item(key = "battito") {
      val heart = d?.heart
      FluidHealthCard(
        FluidHealthMetric.HeartRate,
        value = heart?.let { "${FluidHealthFormat.heart(it.min)}–${FluidHealthFormat.heart(it.max)}" },
        numeric = heart?.max ?: 0.0, unit = "bpm", time = "Ultime 24 ore",
        sources = heart?.sources.orEmpty(), labels = labels,
      )
    }
    item(key = "peso") {
      val weight = d?.weight
      FluidHealthCard(
        FluidHealthMetric.Weight, value = weight?.let { FluidHealthFormat.weight(it.value) }, numeric = weight?.value ?: 0.0, unit = "kg",
        time = "3 giorni fa", sources = listOfNotNull(weight?.source), labels = labels,
      )
    }
    item(key = "allenamenti") {
      val last = d?.lastWorkout
      FluidHealthCard(
        FluidHealthMetric.Exercise, value = last?.let { "${it.duration.toMinutes()}" }, numeric = last?.duration?.toMinutes()?.toDouble() ?: 0.0,
        unit = "min", time = last?.title, sources = listOfNotNull(last?.source), labels = labels,
      )
    }
  }
}

/** Le quattro letture di «Buchi e doppioni», dal Health Connect finto. */
private class F8p26Sources(
  val watchFirst: FluidHealthReading,
  val phoneFirst: FluidHealthReading,
  val gap: FluidHealthReading,
  val empty: FluidHealthReading,
  val night: FluidHealthReading,
)

/**
 * Buchi e doppioni: i doppioni li toglie Health Connect con l'ordine delle app che sceglie l'utente (lo stesso giorno con
 * l'orologio primo e col telefono primo), il buco detto come fatto, il giorno vuoto senza zero, la notte di due app.
 */
@Composable
private fun F8p26SourcesScene(onBack: () -> Unit) {
  val zone = remember { ZoneId.systemDefault() }
  val today = remember { LocalDate.now(zone) }
  val labels = remember { FluidHealthLabels() }
  val evening = remember { today.atTime(18, 30).atZone(zone).toInstant() }
  val readings by produceState<F8p26Sources?>(null) {
    value = withContext(Dispatchers.Default) {
      fun at(h: Int, m: Int = 0, d: LocalDate = today) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant()
      val both = arrayOf<FluidHealthSample>(
        FluidHealthInterval("t1", FluidHealthMetric.Steps, at(9), at(12), 3100.0, F8p26Health.Phone),
        FluidHealthInterval("o1", FluidHealthMetric.Steps, at(9), at(12), 3400.0, F8p26Health.Watch),
        FluidHealthInterval("t2", FluidHealthMetric.Steps, at(12), at(13), 1100.0, F8p26Health.Phone),
      )
      val watchFirst = FluidMemoryHealthGateway(sourcePriority = listOf(F8p26Health.Watch.packageName, F8p26Health.Phone.packageName)).apply { grantAll(); put(*both) }
      val phoneFirst = FluidMemoryHealthGateway(sourcePriority = listOf(F8p26Health.Phone.packageName, F8p26Health.Watch.packageName)).apply { grantAll(); put(*both) }
      val gap = FluidMemoryHealthGateway().apply {
        grantAll()
        put(FluidHealthInterval("o2", FluidHealthMetric.Steps, at(8), at(14, 10), 5200.0, F8p26Health.Watch))
      }
      val night = FluidMemoryHealthGateway(sourcePriority = listOf(F8p26Health.Watch.packageName, F8p26Health.Sounds.packageName)).apply {
        grantAll()
        put(
          FluidHealthSession("n1", FluidHealthMetric.Sleep, at(23, 0, today.minusDays(1)), at(7), F8p26Health.Sounds, "Timer della notte"),
          FluidHealthSession("n2", FluidHealthMetric.Sleep, at(23, 20, today.minusDays(1)), at(6, 50), F8p26Health.Watch),
        )
      }
      F8p26Sources(
        watchFirst = watchFirst.day(FluidHealthMetric.Steps, today, zone, evening, gapRule = null),
        phoneFirst = phoneFirst.day(FluidHealthMetric.Steps, today, zone, evening, gapRule = null),
        gap = gap.day(FluidHealthMetric.Steps, today, zone, evening),
        empty = gap.day(FluidHealthMetric.Steps, today.minusDays(1), zone, evening),
        night = night.night(today, zone),
      )
    }
  }
  val r = readings
  FluidScreen(title = "Buchi e doppioni", subtitle = "F8.26 · i doppioni li toglie Health Connect, noi sommiamo", onBack = onBack) {
    if (r == null) return@FluidScreen
    item(key = "insieme") {
      Column {
        FluidSectionHeader(title = "Telefono e orologio insieme")
        StepsCard(r.watchFirst, labels, note = "Con l'orologio primo nell'ordine delle app di Health Connect: dalle 9 alle 12 conta l'orologio")
        StepsCard(r.phoneFirst, labels, note = "Lo stesso giorno col telefono primo: dalle 9 alle 12 conta il telefono")
        FluidSectionFootnote("L'ordine lo sceglie la persona nelle impostazioni di Health Connect; l'app prende il totale che le dà.")
      }
    }
    item(key = "buco") {
      Column {
        FluidSectionHeader(title = "Il pomeriggio senza dati")
        val v = r.gap as? FluidHealthReading.Value
        StepsCard(r.gap, labels, note = v?.gaps?.let { labels.gapLine(it, evening, zone) })
        FluidSectionFootnote("Un fatto, non una causa: l'orologio può essere scarico o sul comodino, e la card non lo inventa.")
      }
    }
    item(key = "vuoto") {
      Column {
        FluidSectionHeader(title = "Ieri, nessuna fonte")
        StepsCard(r.empty, labels, note = null)
      }
    }
    item(key = "notte") {
      Column {
        FluidSectionHeader(title = "Una notte vista da due app")
        val n = r.night as? FluidHealthReading.Value
        FluidHealthCard(FluidHealthMetric.Sleep, n?.let { FluidHealthFormat.sleep(Duration.ofMinutes(it.value.toLong())) }, numeric = n?.value ?: 0.0,
          sources = n?.sources.orEmpty(), note = "Sounds e l'orologio, una notte sola: Health Connect conta il tempo una volta", labels = labels)
      }
    }
  }
}

@Composable
private fun StepsCard(reading: FluidHealthReading, labels: FluidHealthLabels, note: String?) {
  val v = reading as? FluidHealthReading.Value
  FluidHealthCard(
    FluidHealthMetric.Steps, value = v?.let { FluidHealthFormat.steps(it.value) }, numeric = v?.value ?: 0.0, unit = if (v != null) "passi" else null,
    sources = v?.sources.orEmpty(), note = note, labels = labels, modifier = Modifier.padding(bottom = 8.dp),
  )
}

/** I permessi misura per misura, con un Health Connect finto (il foglio di conferma e' della galleria). */
@Composable
private fun F8p26PermissionsScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val asked = remember {
    listOf(
      FluidHealthPermission.read(FluidHealthMetric.Steps),
      FluidHealthPermission.read(FluidHealthMetric.Sleep), FluidHealthPermission.write(FluidHealthMetric.Sleep),
      FluidHealthPermission.read(FluidHealthMetric.HeartRate),
      FluidHealthPermission.read(FluidHealthMetric.Weight),
      FluidHealthPermission.read(FluidHealthMetric.Hydration), FluidHealthPermission.write(FluidHealthMetric.Hydration),
    )
  }
  var granted by remember {
    mutableStateOf(setOf(FluidHealthPermission.read(FluidHealthMetric.Steps), FluidHealthPermission.read(FluidHealthMetric.Sleep)))
  }
  var asking by remember { mutableStateOf<Set<FluidHealthPermission>?>(null) }
  val denials = remember { mutableStateOf(mapOf<FluidHealthMetric, Int>()) }
  // Health Connect vero: solo se c'e', mai i permessi ne' i dati.
  val real by produceState<FluidHealthAvailability?>(null) {
    value = withContext(Dispatchers.IO) { runCatching { FluidHealthConnectGateway(context).availability() }.getOrNull() }
  }
  FluidScreen(title = "Permessi", subtitle = "F8.26 · Health Connect finto", onBack = onBack) {
    item(key = "righe") {
      FluidHealthPermissionRows(
        asked = asked, granted = granted,
        onRequest = { missing ->
          val metric = missing.first().metric
          if ((denials.value[metric] ?: 0) >= 2) asking = null else asking = missing
        },
        needsSettings = { (denials.value[it] ?: 0) >= 2 },
      )
    }
    item(key = "vero") {
      FluidSectionFootnote(
        when (real) {
          null -> "Health Connect su questo telefono: controllo."
          FluidHealthAvailability.Available -> "Health Connect su questo telefono: c'è (la galleria non chiede i permessi e non legge niente)."
          FluidHealthAvailability.NeedsUpdate -> "Health Connect su questo telefono: va aggiornato."
          FluidHealthAvailability.Unavailable -> "Health Connect su questo telefono: non c'è."
        },
      )
    }
  }
  val request = asking
  FluidConfirmSheet(
    visible = request != null,
    onDismissRequest = {
      request?.let { r -> denials.value = denials.value + (r.first().metric to ((denials.value[r.first().metric] ?: 0) + 1)) }
      asking = null
    },
    title = "Consentire «${request?.first()?.metric?.let { FluidHealthLabels().metricName(it) } ?: ""}»?",
    message = "Il foglio finto della galleria al posto di quello di Health Connect: «Annulla» vale come «Non consentire».",
    confirmLabel = "Consenti",
    cancelLabel = "Non consentire",
    onConfirm = {
      request?.let { granted = granted + it }
      asking = null
    },
  )
}
