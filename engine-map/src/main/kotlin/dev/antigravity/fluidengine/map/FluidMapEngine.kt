/*
 * engine-map (4.0, F8.26): gli stili, l'attribuzione, il motore che disegna la mappa e le parole. Codice del Fluid
 * Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext

/** Un credito della mappa: la riga da leggere e il collegamento che la spiega. */
@Immutable
data class FluidMapCredit(val text: String, val url: String)

/**
 * L'attribuzione di una mappa: la scritta corta sempre visibile nell'angolo ([short], «© OpenStreetMap») e i crediti
 * interi che compaiono toccandola ([credits]). I dati di OpenStreetMap sono sotto ODbL, che la chiede: la mappa la
 * disegna sempre, anche sulla cartolina, e non si spegne (le linee guida dell'attribuzione della OSMF: «© OpenStreetMap»
 * visibile, con il collegamento a openstreetmap.org/copyright a un tocco).
 */
@Immutable
data class FluidMapAttribution(val short: String, val credits: List<FluidMapCredit>)

/**
 * Uno stile della mappa: l'indirizzo dello stile di MapLibre ([uri]) e quello per il tema scuro ([darkUri], null se lo
 * stile non ne ha uno: allora resta chiaro anche di notte), il nome nel pannello dei livelli e i due colori del suo
 * campione. Lo stile dice anche fin dove arriva lo zoom ([maxZoom]) e chi va citato ([attribution]).
 */
@Immutable
data class FluidMapStyle(
  val id: String,
  val label: String,
  val uri: String,
  val darkUri: String? = null,
  val attribution: FluidMapAttribution = FluidMapStyles.OpenFreeMapAttribution,
  val maxZoom: Double = 19.0,
  val swatch: Color = Color(0xFFE9E4DA),
  val darkSwatch: Color = Color(0xFF1D2330),
) {
  /** L'indirizzo per il tema: [darkUri] di notte, se c'e'. */
  fun uriFor(dark: Boolean): String = if (dark) darkUri ?: uri else uri
}

/**
 * Gli stili di serie: le mappe di OpenStreetMap servite da **OpenFreeMap** (tiles.openfreemap.org), gratis, senza
 * chiave, senza account, senza limiti di visite (openfreemap.org, letto il 26/11/2026). Le regole del servizio, che
 * l'app accetta usandolo:
 *
 * - l'attribuzione (la disegna la mappa: [OpenFreeMapAttribution]);
 * - i download per l'uso senza rete al massimo di 3 000 tessere e 50 MB l'uno, con al massimo 4 richieste insieme e uno
 *   User-Agent che dice l'app (MapLibre lo manda gia');
 * - il servizio e' «cosi' com'e'» e puo' cambiare o fermarsi; passa da Cloudflare; non tiene gli indirizzi IP nei log.
 *
 * Un'app che vuole il suo server (un file PMTiles sul proprio cloud, l'istanza di OpenFreeMap fatta girare da se') passa
 * i suoi [FluidMapStyle].
 */
object FluidMapStyles {
  /** I crediti delle mappe di OpenFreeMap, come li scrive il servizio (TileJSON di tiles.openfreemap.org). */
  val OpenFreeMapAttribution: FluidMapAttribution = FluidMapAttribution(
    short = "© OpenStreetMap",
    credits = listOf(
      FluidMapCredit("Dati © contributori di OpenStreetMap, licenza ODbL", "https://www.openstreetmap.org/copyright"),
      FluidMapCredit("Tessere di OpenFreeMap", "https://openfreemap.org"),
      FluidMapCredit("Stile © OpenMapTiles", "https://www.openmaptiles.org/"),
    ),
  )

  private const val Base = "https://tiles.openfreemap.org/styles/"

  /** «Esplora»: lo stile Liberty, coi parchi, l'acqua e gli edifici; di notte Dark. Il piu' vicino a Mappe. */
  val Explore: FluidMapStyle = FluidMapStyle(
    id = "esplora", label = "Esplora", uri = Base + "liberty", darkUri = Base + "dark",
    swatch = Color(0xFFF8F4F0), darkSwatch = Color(0xFF1F2430),
  )

  /** «Essenziale»: Positron, grigio chiaro, per i segnaposto che devono spiccare; di notte Dark. */
  val Essential: FluidMapStyle = FluidMapStyle(
    id = "essenziale", label = "Essenziale", uri = Base + "positron", darkUri = Base + "dark",
    swatch = Color(0xFFF2F3F0), darkSwatch = Color(0xFF16181D),
  )

  /** «Vivace»: Bright, coi colori pieni delle strade; di notte Fiord, il blu notte. */
  val Vivid: FluidMapStyle = FluidMapStyle(
    id = "vivace", label = "Vivace", uri = Base + "bright", darkUri = Base + "fiord",
    swatch = Color(0xFFF6EFD9), darkSwatch = Color(0xFF2D3E50),
  )

  /** I tre stili di OpenFreeMap, nell'ordine del pannello dei livelli. */
  val OpenFreeMap: List<FluidMapStyle> = listOf(Explore, Essential, Vivid)
}

/** Che cosa chiede la cartolina al motore: un'immagine ferma di [camera], di [widthPx] × [heightPx] pixel. */
@Immutable
data class FluidMapSnapshotRequest(
  val camera: FluidMapCamera,
  val widthPx: Int,
  val heightPx: Int,
  val density: Float,
  val style: FluidMapStyle,
  val dark: Boolean,
)

/**
 * Chi muove la camera della mappa vera: il motore lo da' a [FluidMapState.attach] quando la sua superficie e' pronta, e
 * da li' lo stato gli passa i comandi (la posizione, la bussola, il tocco su un grappolo).
 */
interface FluidMapCameraDriver {
  /** Porta la camera a [camera] subito, senza animazione. */
  fun jump(camera: FluidMapCamera)

  /** Porta la camera a [camera] in [durationMillis] (una curva morbida del motore). Torna quando arriva o si ferma. */
  suspend fun fly(camera: FluidMapCamera, durationMillis: Int)
}

/**
 * Il motore che disegna la mappa: la superficie viva (che segue il dito e dice la camera allo stato) e l'immagine ferma
 * della cartolina. Ce ne sono due:
 *
 * - [FluidMapLibreEngine], la mappa vera con MapLibre e le tessere di rete;
 * - [FluidSketchMapEngine], il **disegno** dell'engine (terra, acqua, parchi e strade inventati, ma coerenti col luogo):
 *   per le prove sul computer (MapLibre non si disegna in Robolectric), per la galleria senza rete e per un'anteprima.
 *
 * Un'app con un altro fornitore implementa questa interfaccia.
 */
interface FluidMapEngine {
  /**
   * Vero se il vetro sopra la mappa la rifrange: la superficie si disegna dentro Compose (una `TextureView`, un
   * `Canvas`). Falso per una `SurfaceView`, che sta in una finestra sua: il vetro dei comandi allora si vede chiaro,
   * senza sfocare la mappa sotto.
   */
  val glassSamplesMap: Boolean

  /**
   * La mappa viva. Legge la camera da [state] alla prima composizione, poi la dice lei a ogni fotogramma con
   * [FluidMapState.report] e prende i comandi con [FluidMapState.attach]. Il dito la muove (trascinare, pizzicare,
   * ruotare con due dita, doppio tocco): il motore non la sfoca mai, neanche nel lancio («dito nitido»).
   */
  @Composable
  fun Surface(state: FluidMapState, style: FluidMapStyle, dark: Boolean, modifier: Modifier)

  /** L'immagine ferma per [request], o null se non si puo' fare (niente rete, stile che non arriva). */
  suspend fun snapshot(request: FluidMapSnapshotRequest): ImageBitmap?
}

/**
 * Il motore della mappa di serie per questo sottoalbero. Null (di serie): [rememberFluidMapEngine] fa MapLibre. Le prove
 * e la galleria senza rete forniscono qui il disegno ([FluidSketchMapEngine]).
 */
val LocalFluidMapEngine: ProvidableCompositionLocal<FluidMapEngine?> = staticCompositionLocalOf { null }

/** Il motore di [LocalFluidMapEngine], o la mappa vera di MapLibre ([FluidMapLibreEngine], in `TextureView`). */
@Composable
fun rememberFluidMapEngine(): FluidMapEngine {
  val provided = LocalFluidMapEngine.current
  if (provided != null) return provided
  val context = LocalContext.current.applicationContext
  return remember(context) { FluidMapLibreEngine(context) }
}

/**
 * Le parole della mappa, per TalkBack e per gli scritti. Italiano di serie; [English] per le app in inglese.
 */
@Immutable
data class FluidMapLabels(
  val map: String = "Mappa",
  val location: String = "Posizione",
  val locationFollowing: String = "Posizione, la mappa ti segue",
  val locationUnknown: String = "Posizione non disponibile",
  val compass: (degrees: Int) -> String = { "Bussola, $it gradi. Tocca per tornare al nord" },
  val layers: String = "Livelli della mappa",
  val layersTitle: String = "Mappa",
  val credits: String = "Crediti della mappa",
  val zoomIn: String = "Ingrandisci",
  val zoomOut: String = "Riduci",
  val showAll: String = "Mostra tutti i luoghi",
  val cluster: (count: Int) -> String = { if (it == 1) "1 luogo" else "$it luoghi" },
  val clusterAction: String = "Avvicina",
  val selected: String = "Scelto",
  val userLocation: String = "La tua posizione",
  val directions: String = "Indicazioni",
  val share: String = "Condividi",
  val copyCoordinates: String = "Copia coordinate",
  val copied: String = "Coordinate copiate",
  val openMap: String = "Apri la mappa",
  val postcard: (place: String) -> String = { "Mappa di $it" },
  val loading: String = "Carico la mappa",
  val unavailable: String = "Mappa non disponibile",
  val visiblePlaces: (count: Int) -> String = { if (it == 1) "Mappa, un luogo in vista" else "Mappa, $it luoghi in vista" },
  /** La scritta corta del tasto della copia nella scheda del luogo (TalkBack dice [copyCoordinates]). */
  val copyShort: String = "Copia",
) {
  companion object {
    /** Le stesse parole in inglese. */
    val English: FluidMapLabels = FluidMapLabels(
      map = "Map", location = "Location", locationFollowing = "Location, the map follows you",
      locationUnknown = "Location unavailable", compass = { "Compass, $it degrees. Tap to face north" },
      layers = "Map layers", layersTitle = "Map", credits = "Map credits", zoomIn = "Zoom in", zoomOut = "Zoom out",
      showAll = "Show all places", cluster = { if (it == 1) "1 place" else "$it places" }, clusterAction = "Zoom in",
      selected = "Selected", userLocation = "Your location", directions = "Directions", share = "Share",
      copyCoordinates = "Copy coordinates", copied = "Coordinates copied", openMap = "Open map",
      postcard = { "Map of $it" }, loading = "Loading the map", unavailable = "Map unavailable",
      visiblePlaces = { if (it == 1) "Map, one place in view" else "Map, $it places in view" }, copyShort = "Copy",
    )
  }
}
