package dev.antigravity.fluidengine.ui.fluid

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidFirstUseKeys
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.foundation.FluidReleaseNote
import dev.antigravity.fluidengine.foundation.FluidWelcomeKind
import dev.antigravity.fluidengine.foundation.fluidWelcomeKind
import dev.antigravity.fluidengine.foundation.fluidWelcomeMarksSilently
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.Bolt
import dev.antigravity.fluidengine.ui.fluid.icons.CalendarMonth
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.Group
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.ui.fluid.icons.Map
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.Person
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoLibrary
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Shield
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// F8.11 · `benvenuto-e-novita`: il foglio con cui le app di Apple si presentano («Benvenuto in…», «Novità di…» di Foto,
// Note, Mappe). Titolo grande, tre o quattro righe con icona e frase che entrano una dopo l'altra, una nota sulla privacy e
// «Continua» fermo in basso; dopo un aggiornamento lo stesso foglio mostra le novità di quella versione una volta sola.

/**
 * Le misure e i tempi del foglio di benvenuto.
 *
 * Apple non dà il foglio come API pubblica e non lo misura in nessuna sessione. Due fonti, il 24 novembre 2026: lo
 * schermo «Welcome to the Passwords App» di iOS 18 (9to5mac, 10 giugno 2024, `passwords-startup.jpg`: larghezza intera
 * dell'iPhone a 2,545 px per punto, quindi misure in punti) e WhatsNewKit (SvenTiigi, `WhatsNew.Layout` di serie), la
 * ricostruzione più usata del foglio di Apple, per quello che lo schermo non mostra. I punti di iOS sono i dp di qui.
 */
object FluidWelcomeDefaults {
  /** Dal bordo alto del foglio alla prima cosa (l'icona o il titolo): **65 dp** (WhatsNewKit `contentPadding.top`, fiducia media). */
  val TopPadding: Dp = 65.dp

  /** L'icona del passo sopra il titolo, nell'accento: **56 dp** (Password di iOS 18: 55 pt d'altezza, fiducia alta). */
  val PageIconSize: Dp = 56.dp

  /** Fra l'icona e il titolo: **33 dp** (Password di iOS 18: 41 pt fino al tratto del titolo, 33 fino alla sua riga). */
  val IconToTitle: Dp = 33.dp

  /**
   * Fra il titolo e la prima riga: **40 dp** (Password di iOS 18: 40,5 pt fra le righe di testo, fiducia alta; WhatsNewKit
   * ne mette 60).
   */
  val TitleToFeatures: Dp = 40.dp

  /** Fra il titolo e la frase di un passo senza righe: **24 dp** (Password di iOS 18, «Turn On Notifications»). */
  val TitleToMessage: Dp = 24.dp

  /** Fra una riga e la successiva: **25 dp** (WhatsNewKit `featureListSpacing`; Password di iOS 18 ≈ 25 pt). */
  val FeatureSpacing: Dp = 25.dp

  /** La colonna dell'icona: **40 dp** (WhatsNewKit `featureImageWidth`; in Password il centro sta a 60 pt dal bordo). */
  val FeatureIconWidth: Dp = 40.dp

  /** Il glifo nella colonna: **32 dp**, a occhio il 24 novembre 2026 (in iOS è un SF Symbol grande, nell'accento). */
  val FeatureIconSize: Dp = 32.dp

  /** Fra la colonna dell'icona e il testo: **18 dp** (Password di iOS 18: il testo parte a 98 pt, fiducia alta). */
  val FeatureGap: Dp = 18.dp

  /** Fra il titolo di una riga e la sua frase: **2 dp** (WhatsNewKit `featureVerticalSpacing`, fiducia media). */
  val FeatureLineGap: Dp = 2.dp

  /**
   * Il margine dei lati: **16 dp**, più **24 dp** ai due lati delle righe: la colonna delle icone parte a 40 dal bordo
   * (Password di iOS 18: icone centrate a 60 pt, testo che va a capo prima di 353 pt; WhatsNewKit ne mette 15 a sinistra).
   */
  val HorizontalPadding: Dp = 16.dp
  val FeaturesLeadingPadding: Dp = 24.dp
  val FeaturesTrailingPadding: Dp = 24.dp

  /** I lati del tasto e della nota in basso: **20 dp** (WhatsNewKit, telefono in verticale, fiducia media). */
  val FooterHorizontalPadding: Dp = 20.dp

  /** Sotto il tasto: **24 dp**, a occhio il 24 novembre 2026 (il foglio è già scostato dalla barra di navigazione). */
  val FooterBottomPadding: Dp = 24.dp

  /** Sopra la nota sulla privacy e fra la nota e il tasto: **16 dp**, a occhio il 24 novembre 2026. */
  val FooterGap: Dp = 16.dp

  /** Oltre questa larghezza il contenuto resta centrato (come [FluidSheetScaffoldDefaults.MaxWidth]). */
  val MaxWidth: Dp = 560.dp

  /**
   * Da questa scala del testo la nota sulla privacy passa nel corpo che scorre: in basso restano solo i pallini e il
   * tasto, che non devono coprire mezzo schermo (130%, a occhio il 24 novembre 2026).
   */
  const val LargeTextScale: Float = 1.3f

  /**
   * Quando entra la prima riga, dall'apertura del foglio: **250 ms**, quando il foglio ha quasi finito di salire. A occhio
   * il 24 novembre 2026: Apple mostra le righe ferme, l'entrata una dopo l'altra è la scelta del piano (F8.11).
   */
  const val FirstFeatureDelayMillis: Int = 250

  /** Il passo fra una riga e la successiva: **90 ms**, a occhio il 24 novembre 2026. */
  const val FeatureStaggerMillis: Int = 90

  /** Quanto ci mette una riga a posarsi e a mettersi a fuoco: **380 ms**, come una riga di testo che arriva (F8.1). */
  const val FeatureArrivalMillis: Int = FluidTextMotion.LineArrivalMillis

  /** Da quanto sotto sale una riga che entra: **14 dp**, a occhio il 24 novembre 2026. */
  val FeatureRise: Dp = 14.dp

  /**
   * La sfocatura di una riga che entra, al massimo: **6 dp** di σ, quella dell'immagine che arriva (F8.1). È l'app che
   * fa entrare le righe, non il dito: per la regola di Alessio del 24 novembre si sfocano.
   */
  val FeatureBlur: Dp = FluidTextMotion.ImageArrivalBlurDp.dp
}

/** Una riga del foglio: l'icona (nell'accento), il titolo e la frase. */
@Immutable
data class FluidWelcomeFeature(
  val icon: ImageVector,
  val title: String,
  val text: String = "",
)

/**
 * Un passo del benvenuto: il titolo grande, le righe e, se serve, un contenuto dell'app sotto le righe (per esempio la
 * scelta di un tema o il tasto di un permesso). [actionLabel] è il testo del tasto per questo passo (di serie
 * «Continua»); [icon] è il segno sopra il titolo, nell'accento (l'icona dell'app, la campanella di un passo sulle
 * notifiche); [message] è una frase centrata sotto il titolo, per un passo senza righe (come «Turn On Notifications» di
 * Password).
 */
@Immutable
class FluidWelcomePage(
  val title: String,
  val features: List<FluidWelcomeFeature> = emptyList(),
  val actionLabel: String? = null,
  val content: (@Composable ColumnScope.() -> Unit)? = null,
  val icon: ImageVector? = null,
  val message: String? = null,
)

/** Una novità del manifest come riga del foglio, con l'icona del suo simbolo ([fluidReleaseNoteIcon]). */
fun FluidReleaseNote.toWelcomeFeature(icon: ImageVector = fluidReleaseNoteIcon(symbol)): FluidWelcomeFeature =
  FluidWelcomeFeature(icon = icon, title = title, text = text)

/**
 * L'icona di un simbolo scritto nel manifest (`"symbol": "star"`): un vocabolario piccolo e stabile, in inglese come i
 * nomi di SF Symbols. Un nome che non c'è, o nessun nome: le scintille delle novità.
 */
fun fluidReleaseNoteIcon(symbol: String?): ImageVector = when (symbol?.trim()?.lowercase()) {
  "star" -> FluidIcons.Star
  "bolt", "speed", "fast" -> FluidIcons.Bolt
  "lock", "privacy" -> FluidIcons.Lock
  "shield", "security" -> FluidIcons.Shield
  "photo", "photos", "image" -> FluidIcons.PhotoLibrary
  "bell", "notification", "notifications" -> FluidIcons.Notifications
  "search" -> FluidIcons.Search
  "sync", "cloud" -> FluidIcons.Sync
  "share" -> FluidIcons.Share
  "settings", "gear" -> FluidIcons.Settings
  "mic", "microphone", "voice" -> FluidIcons.Mic
  "map", "location" -> FluidIcons.Map
  "person", "account" -> FluidIcons.Person
  "people", "group" -> FluidIcons.Group
  "calendar", "date" -> FluidIcons.CalendarMonth
  "folder", "files" -> FluidIcons.Folder
  "heart", "favorite" -> FluidIcons.Favorite
  else -> FluidIcons.AutoAwesome
}

/**
 * Da dove arriva un'apertura: un link, una condivisione o una notifica dell'app ([ExtraFromNotification]) non sono
 * un'apertura dall'icona, e il benvenuto non ci si mette davanti.
 *
 * Con gli indirizzi delle pagine (F8.12) l'intent di lancio è già consumato da `takeInitialFluidAddress()`: lì basta
 * `suppressed = initialAddress != null || FluidLaunchOrigin.isDirect(intentDiPrima)`.
 */
object FluidLaunchOrigin {
  /** L'extra che l'app mette sull'intent delle sue notifiche: `intent.putExtra(ExtraFromNotification, true)`. */
  const val ExtraFromNotification: String = "dev.antigravity.fluidengine.extra.FROM_NOTIFICATION"

  /** Vero per un'apertura da link, condivisione o notifica; falso dall'icona, da un collegamento o da un `am start`. */
  fun isDirect(intent: Intent?): Boolean {
    if (intent == null) return false
    if (intent.getBooleanExtra(ExtraFromNotification, false)) return true
    val action = intent.action
    return intent.data != null || action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND ||
      action == Intent.ACTION_SEND_MULTIPLE
  }
}

/**
 * Lo stato del benvenuto: se all'apertura va mostrato qualcosa ([kind]), e quando ([isShowing]).
 *
 * Decide da solo, con [fluidWelcomeKind]: il benvenuto a un'installazione nuova, le novità a un aggiornamento con delle
 * novità, niente alla stessa versione. Mostra il foglio solo quando può: mai a un'apertura da link o da notifica
 * (`suppressed`), mai sopra un'azione in corso (`busy`, o un altro modale già aperto nell'host), e mai nel primo
 * fotogramma (la decisione legge la memoria, quindi arriva qualche fotogramma dopo la pagina). La versione si segna come
 * vista **nel momento in cui il foglio compare**: chi chiude l'app a foglio aperto non lo rivede.
 */
@Stable
class FluidWelcomeState internal constructor(
  private val memory: FluidFirstUseMemory,
  private val key: String,
  /** La versione dell'app che questo stato guarda (di solito `BuildConfig.VERSION_NAME`). */
  val versionName: String,
  private val scope: CoroutineScope,
) {
  /** Cosa mostrare: il benvenuto, le novità, o niente. Resta valido mentre il foglio esce. */
  var kind: FluidWelcomeKind? by mutableStateOf(null)
    internal set

  /** La decisione è presa (la memoria è letta e, se servivano, le novità sono arrivate). */
  var isDecided: Boolean by mutableStateOf(false)
    internal set

  /** Il foglio è in scena: da passare come `visible` a [FluidWelcomeSheet]. */
  var isShowing: Boolean by mutableStateOf(false)
    internal set

  internal var seen: String? by mutableStateOf(null)
  internal var seenLoaded by mutableStateOf(false)
  internal var finished by mutableStateOf(false)
  internal var epoch by mutableIntStateOf(0)

  /** Chiude il foglio (il «Continua» dell'ultimo passo, o il foglio tirato giù). La versione è già segnata come vista. */
  fun dismiss() {
    isShowing = false
    finished = true
  }

  /** Dimentica la versione vista e decide di nuovo: per le prove e per una voce «Mostra di nuovo le novità». */
  fun forget() {
    scope.launch {
      memory.write(key, null)
      isShowing = false
      finished = false
      kind = null
      isDecided = false
      seen = null
      seenLoaded = false
      epoch++
    }
  }

  internal suspend fun markSeen() = memory.write(key, versionName)

  internal suspend fun load() {
    seen = memory.read(key)
    seenLoaded = true
  }
}

/**
 * Lo stato del benvenuto per questa apertura.
 *
 * @param versionName la versione dell'app (`BuildConfig.VERSION_NAME`).
 * @param hasNews se questa versione ha delle novità da mostrare: di solito `notes.isNotEmpty()` dopo
 *   `EngineRemoteConfig.releaseNotes(versionName)` di `engine-config`. Null: non si sa ancora (la decisione aspetta).
 * @param hasWelcome se l'app ha un benvenuto per il primo avvio.
 * @param suppressed l'app è stata aperta da un link o da una notifica ([FluidLaunchOrigin.isDirect], o l'indirizzo di
 *   F8.12): per questa apertura niente foglio, e la versione resta da vedere.
 * @param busy un'azione dell'app in corso (un caricamento, un passaggio di dati): il foglio aspetta che finisca.
 * @param freshInstall se è un'installazione nuova; null: lo dice il `PackageManager` (prima installazione e ultimo
 *   aggiornamento alla stessa ora).
 * @param memory dove ricordare la versione vista; di serie quella del sottoalbero ([LocalFluidFirstUseMemory]).
 * @param scope più fogli indipendenti nella stessa app (l'app e una sua sezione) hanno nomi diversi.
 */
@Composable
fun rememberFluidWelcomeState(
  versionName: String,
  hasNews: Boolean? = false,
  hasWelcome: Boolean = true,
  suppressed: Boolean = false,
  busy: Boolean = false,
  freshInstall: Boolean? = null,
  memory: FluidFirstUseMemory = LocalFluidFirstUseMemory.current,
  scope: String = "app",
): FluidWelcomeState {
  val coroutines = rememberCoroutineScope()
  val state = remember(memory, scope, versionName) {
    FluidWelcomeState(memory, FluidFirstUseKeys.welcomeSeen(scope), versionName, coroutines)
  }
  val context = LocalContext.current
  val fresh = freshInstall ?: remember(context) { context.isFreshInstall() }
  LaunchedEffect(state, state.epoch) { state.load() }
  LaunchedEffect(state, state.epoch, state.seenLoaded, hasNews, fresh, hasWelcome) {
    if (!state.seenLoaded || state.isDecided) return@LaunchedEffect
    val seen = state.seen
    val firstRun = seen == null && fresh
    if (!firstRun && hasNews == null) return@LaunchedEffect
    val kind = fluidWelcomeKind(versionName, seen, fresh, hasNews == true, hasWelcome)
    state.kind = kind
    state.isDecided = true
    if (kind == null && fluidWelcomeMarksSilently(versionName, seen)) state.markSeen()
  }
  val hostBusy = LocalFluidGlassModalHostState.current?.isPresenting == true
  LaunchedEffect(state, state.kind, state.finished, suppressed, busy, hostBusy) {
    if (state.kind == null || state.finished || state.isShowing || suppressed || busy || hostBusy) return@LaunchedEffect
    state.isShowing = true
    state.markSeen()
  }
  return state
}

private fun Context.isFreshInstall(): Boolean = runCatching {
  @Suppress("DEPRECATION")
  val info = packageManager.getPackageInfo(packageName, 0)
  info.firstInstallTime == info.lastUpdateTime
}.getOrDefault(true)

/**
 * Il foglio di benvenuto o delle novità, a tutta altezza: un [FluidGlassSheet] al detent `Large` (opaco e attaccato ai
 * bordi, come il foglio di pagina di iOS), senza maniglia, con dentro [FluidWelcomeContent].
 *
 * Si chiude col «Continua» dell'ultimo passo o tirandolo giù: tutti e due chiamano [onFinish] (di solito
 * [FluidWelcomeState.dismiss]). Serve un `FluidGlassModalHost` alla radice. Le [pages] devono restare le stesse mentre il
 * foglio esce.
 */
@Composable
fun FluidWelcomeSheet(
  visible: Boolean,
  onFinish: () -> Unit,
  pages: List<FluidWelcomePage>,
  modifier: Modifier = Modifier,
  privacyNote: String? = null,
  onPrivacyClick: (() -> Unit)? = null,
) {
  if (pages.isEmpty()) return
  FluidGlassSheet(
    visible = visible,
    onDismissRequest = onFinish,
    detents = setOf(FluidSheetDetent.Large),
    dragIndicator = false,
    paneTitle = pages.first().title,
  ) {
    FluidWelcomeContent(
      pages = pages,
      onFinish = onFinish,
      modifier = modifier.fillMaxWidth().weight(1f),
      privacyNote = privacyNote,
      onPrivacyClick = onPrivacyClick,
    )
  }
}

/** Il foglio con un passo solo: il caso di «Novità di…». */
@Composable
fun FluidWelcomeSheet(
  visible: Boolean,
  onFinish: () -> Unit,
  title: String,
  features: List<FluidWelcomeFeature>,
  modifier: Modifier = Modifier,
  privacyNote: String? = null,
  onPrivacyClick: (() -> Unit)? = null,
) {
  val pages = remember(title, features) { listOf(FluidWelcomePage(title, features)) }
  FluidWelcomeSheet(visible, onFinish, pages, modifier, privacyNote, onPrivacyClick)
}

/**
 * Il contenuto del benvenuto, da mettere in un foglio ([FluidWelcomeSheet]) o in una pagina intera del primo avvio.
 *
 * - **Il tasto resta fermo in basso, le righe scorrono.** Il corpo prende l'altezza che il tasto lascia e scorre; col
 *   testo grande (da [FluidWelcomeDefaults.LargeTextScale]) anche la nota sulla privacy passa nel corpo.
 * - **Le righe entrano una dopo l'altra** (sale, si mette a fuoco), solo nel primo passo e solo alla prima composizione:
 *   le muove l'app, quindi si sfocano. Col movimento ridotto sono già al loro posto.
 * - **Più passi** ([pages] più d'uno): scorrono col dito (nitidi), i pallini ([FluidPagerDots]) stanno sopra il tasto e il
 *   tasto porta al passo dopo; l'ultimo chiama [onFinish].
 *
 * @param background disegnato sotto tutto, per esempio uno sfondo vivo ([FluidLiveBackgroundCanvas]) in una pagina di
 *   benvenuto. Nullo: niente, si vede il fondo di chi lo contiene.
 */
@Composable
fun FluidWelcomeContent(
  pages: List<FluidWelcomePage>,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
  privacyNote: String? = null,
  onPrivacyClick: (() -> Unit)? = null,
  background: (@Composable BoxScope.() -> Unit)? = null,
) {
  if (pages.isEmpty()) return
  val labels = LocalFluidFirstUseLabels.current
  val coroutines = rememberCoroutineScope()
  val pager = rememberPagerState { pages.size }
  val largeText = LocalDensity.current.fontScale >= FluidWelcomeDefaults.LargeTextScale
  val privacyInBody = largeText && privacyNote != null
  val fadeColor = if (background == null) MaterialTheme.colorScheme.background else Color.Unspecified
  Box(modifier) {
    background?.invoke(this)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
      Column(Modifier.fillMaxSize()) {
        if (pages.size == 1) {
          WelcomeBody(
            page = pages[0], animate = true, fadeColor = fadeColor,
            privacy = if (privacyInBody) privacyNote else null, onPrivacyClick = onPrivacyClick,
            modifier = Modifier.weight(1f).fillMaxWidth(),
          )
        } else {
          HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            WelcomeBody(
              page = pages[index], animate = index == 0, fadeColor = fadeColor,
              privacy = if (privacyInBody && index == pages.lastIndex) privacyNote else null,
              onPrivacyClick = onPrivacyClick,
              modifier = Modifier.fillMaxSize(),
            )
          }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
          Column(
            modifier = Modifier
              .widthIn(max = FluidWelcomeDefaults.MaxWidth)
              .fillMaxWidth()
              .padding(
                start = FluidWelcomeDefaults.FooterHorizontalPadding,
                end = FluidWelcomeDefaults.FooterHorizontalPadding,
                top = FluidWelcomeDefaults.FooterGap,
                bottom = FluidWelcomeDefaults.FooterBottomPadding,
              ),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            if (privacyNote != null && !privacyInBody) {
              WelcomePrivacy(privacyNote, onPrivacyClick)
              Spacer(Modifier.height(FluidWelcomeDefaults.FooterGap))
            }
            if (pages.size > 1) {
              FluidPagerDots(state = pager, pageLabel = labels.stepPosition)
              Spacer(Modifier.height(FluidWelcomeDefaults.FooterGap))
            }
            val current = pager.currentPage.coerceIn(0, pages.lastIndex)
            FluidButton(
              text = pages[current].actionLabel ?: labels.continueLabel,
              onClick = {
                if (current >= pages.lastIndex) onFinish() else coroutines.launch { pager.animateScrollToPage(current + 1) }
              },
              size = FluidButtonSize.Large,
              fillWidth = true,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WelcomeBody(
  page: FluidWelcomePage,
  animate: Boolean,
  fadeColor: Color,
  privacy: String?,
  onPrivacyClick: (() -> Unit)?,
  modifier: Modifier,
) {
  val scroll = rememberScrollState()
  Box(modifier.scrollFade(scroll, fadeColor), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier
        .widthIn(max = FluidWelcomeDefaults.MaxWidth)
        .fillMaxWidth()
        .verticalScroll(scroll)
        .padding(horizontal = FluidWelcomeDefaults.HorizontalPadding)
        .padding(top = FluidWelcomeDefaults.TopPadding, bottom = FluidWelcomeDefaults.FooterGap),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      page.icon?.let { icon ->
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(FluidWelcomeDefaults.PageIconSize),
        )
        Spacer(Modifier.height(FluidWelcomeDefaults.IconToTitle))
      }
      Text(
        text = page.title,
        style = MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
      )
      page.message?.let { message ->
        Spacer(Modifier.height(FluidWelcomeDefaults.TitleToMessage))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(horizontal = FluidWelcomeDefaults.FeaturesLeadingPadding / 2),
        )
      }
      if (page.features.isNotEmpty()) {
        Spacer(Modifier.height(FluidWelcomeDefaults.TitleToFeatures))
        Column(
          modifier = Modifier.fillMaxWidth().padding(
            start = FluidWelcomeDefaults.FeaturesLeadingPadding,
            end = FluidWelcomeDefaults.FeaturesTrailingPadding,
          ),
          verticalArrangement = Arrangement.spacedBy(FluidWelcomeDefaults.FeatureSpacing),
        ) {
          page.features.forEachIndexed { index, feature -> WelcomeFeatureRow(feature, index, animate) }
        }
      }
      page.content?.let { content ->
        Spacer(Modifier.height(FluidWelcomeDefaults.FeatureSpacing))
        Column(Modifier.fillMaxWidth(), content = content)
      }
      if (privacy != null) {
        Spacer(Modifier.height(FluidWelcomeDefaults.TitleToFeatures / 2))
        WelcomePrivacy(privacy, onPrivacyClick)
      }
    }
  }
}

/** Una riga: entra salendo e mettendosi a fuoco, una dopo l'altra (vedi [FluidWelcomeDefaults.FeatureStaggerMillis]). */
@Composable
private fun WelcomeFeatureRow(feature: FluidWelcomeFeature, index: Int, animate: Boolean) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val arrival = remember { Animatable(if (animate && !reduced) 0f else 1f) }
  LaunchedEffect(arrival) {
    if (arrival.value >= 1f) return@LaunchedEffect
    delay((FluidWelcomeDefaults.FirstFeatureDelayMillis + index * FluidWelcomeDefaults.FeatureStaggerMillis).toLong())
    arrival.animateTo(1f, tween(FluidWelcomeDefaults.FeatureArrivalMillis, easing = FluidMotion.EaseOut))
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        val p = arrival.value
        if (p >= 1f) return@graphicsLayer
        // L'opacità arriva prima della messa a fuoco (come il testo che arriva di F8.1): a metà strada la riga si vede già,
        // ma è ancora morbida.
        alpha = (p * 1.6f).coerceIn(0f, 1f)
        translationY = (1f - p) * FluidWelcomeDefaults.FeatureRise.toPx()
        val sigma = (1f - p) * FluidWelcomeDefaults.FeatureBlur.toPx()
        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sigma > 0.25f) {
          BlurEffect(sigma, sigma, TileMode.Decal)
        } else {
          null
        }
      }
      .semantics(mergeDescendants = true) {},
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.width(FluidWelcomeDefaults.FeatureIconWidth), contentAlignment = Alignment.Center) {
      Icon(
        imageVector = feature.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(FluidWelcomeDefaults.FeatureIconSize),
      )
    }
    Spacer(Modifier.width(FluidWelcomeDefaults.FeatureGap))
    Column(Modifier.weight(1f)) {
      Text(text = feature.title, style = MaterialTheme.typography.titleSmall)
      if (feature.text.isNotEmpty()) {
        Spacer(Modifier.height(FluidWelcomeDefaults.FeatureLineGap))
        Text(
          text = feature.text,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** La nota sulla privacy: il segno delle persone nell'accento, la frase, e il collegamento se l'app ne dà uno. */
@Composable
private fun WelcomePrivacy(note: String, onClick: (() -> Unit)?) {
  val labels = LocalFluidFirstUseLabels.current
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
    Icon(
      imageVector = FluidIcons.Group,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(22.dp),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = note,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (onClick != null) {
      FluidButton(
        text = labels.privacyLink,
        onClick = onClick,
        style = FluidButtonStyle.Plain,
        size = FluidButtonSize.Small,
      )
    }
  }
}

/**
 * Una sfumatura del colore del fondo in fondo al corpo, solo mentre c'è ancora testo da scorrere: dice che il corpo
 * continua sotto il tasto. Letto nel disegno, non ricompone; nessuno strato in più.
 */
private fun Modifier.scrollFade(scroll: ScrollState, color: Color): Modifier =
  if (color == Color.Unspecified) this else drawWithCache {
    val height = 28.dp.toPx().coerceAtMost(size.height)
    val top = Offset(0f, size.height - height)
    val band = Size(size.width, height)
    val brush = Brush.verticalGradient(listOf(color.copy(alpha = 0f), color), startY = top.y, endY = size.height)
    onDrawWithContent {
      drawContent()
      if (scroll.canScrollForward) drawRect(brush = brush, topLeft = top, size = band)
    }
  }
