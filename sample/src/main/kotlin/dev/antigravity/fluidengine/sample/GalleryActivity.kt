package dev.antigravity.fluidengine.sample

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.catalog.AllGlassDemo
import dev.antigravity.fluidengine.sample.catalog.CapsulesAndPartsDemo
import dev.antigravity.fluidengine.sample.catalog.MenusDemo
import dev.antigravity.fluidengine.sample.catalog.MoreComponentsDemo
import dev.antigravity.fluidengine.sample.catalog.RowsAndSheetsDemo
import dev.antigravity.fluidengine.sample.material.GalleryGlassLaunch
import dev.antigravity.fluidengine.sample.material.GalleryGlassSwitches
import dev.antigravity.fluidengine.sample.material.LocalGalleryGlassSwitches
import dev.antigravity.fluidengine.sample.material.LocalGalleryPageOpener
import dev.antigravity.fluidengine.sample.icons.IconsPage
import dev.antigravity.fluidengine.sample.material.MaterialPage
import dev.antigravity.fluidengine.sample.material.rememberGalleryGlassSwitches
import dev.antigravity.fluidengine.sample.measure.MeasurePage
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.sample.playground.PlaygroundTab
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.icons.Animation
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import dev.antigravity.fluidengine.ui.fluid.icons.Home
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.delay

/**
 * Fluid Glass.
 *
 * Nata come galleria interna dell'engine, e ancora quello — ogni componente si guarda qui prima di
 * finire in un'app — ma adesso anche un'app del Pampa Store, perché la quinta scheda è diventata
 * una cosa che non esiste da nessun'altra parte: il Playground di **Fluid-physics**, il motore che
 * trasforma qualsiasi forma di vetro in qualsiasi altra.
 *
 *  | Materiale    | i quattro `GlassRole` sopra contenuto volutamente rumoroso                  |
 *  | Playground   | Fluid-physics: preset, disegno a mano, molle, livelli, tasti→menù→pop-up   |
 *  | Controlli    | tocco, registro aptico e grana (3.0), cursori, interruttori, segmenti, campi |
 *  | Chrome       | modale in-root, menù contestuale, notifiche, pagine e il catalogo della 3.0 |
 *  | Impostazioni | aspetto (tema, accento) che cambia davvero, versione e crediti              |
 *
 * Dalla beta della 3.0 la prima cosa che si vede è la pagina **Da provare** ([ToTryPage]): la lista di
 * ciò che va giudicato in mano, con il collegamento alla demo di ciascuna voce. Le demo che non stanno
 * in nessuna scheda ([GalleryPage]) si aprono sopra la scheda da cui si parte, con il loro "Indietro".
 */
class GalleryActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Android, da 10 in su, sotto la barra di navigazione a tre tasti stende un velo di contrasto (un nero al 40%
    // circa, su tutta la larghezza): sopra un foglio di vetro e' una fascia scura in fondo. La galleria disegna da
    // se' fino al bordo, quindi lo spegne (F1B.5: misurato sul Tab S9 in tema scuro).
    if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    // Gli extra di lancio (pagina e matrice di partenza) li legge solo la build di debug: servono al giro di
    // verifica su emulatore, e la galleria pubblicata non ha scorciatoie nascoste.
    val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val launch = if (debuggable) GalleryLaunch.parse { intent?.getStringExtra(it) } else GalleryLaunch.None
    // Gli interruttori del vetro per tutta la galleria (F1A.9: `gallery.model`, `gallery.api`, `gallery.warmup`,
    // `gallery.tune`), solo nella build di debug. Livello simulato, riscaldamento e manopole valgono per cio' che entra
    // in composizione dopo: si applicano qui, prima del contenuto, e una volta sola (una rotazione non rimette quelli
    // degli extra sopra quelli cambiati a mano). La diagnostica si accende qui per la stessa ragione: conta solo cio'
    // che nasce dopo, e una pagina aperta con `gallery.page` nasce nella prima composizione.
    val glassLaunch = if (debuggable) GalleryGlassLaunch.parse { intent?.getStringExtra(it) } else GalleryGlassLaunch.None
    if (debuggable) {
      FluidGlassDiagnostics.enabled = true
      if (savedInstanceState == null) GalleryGlassSwitches.applyAtLaunch(this, glassLaunch)
    }
    setContent {
      // Le impostazioni stanno sopra il tema, non dentro una scheda: "Aspetto" le cambia davvero, e
      // il tema chiaro serve per giudicare il bordo del vetro che si trasforma.
      var settings by remember { mutableStateOf(launch.settings()) }
      val nav = remember { GalleryNav(launch.stack()) }
      val matrix = remember { launch.matrix() }
      val glass = rememberGalleryGlassSwitches(glassLaunch)
      CompositionLocalProvider(
        LocalGalleryGlassSwitches provides glass.takeIf { debuggable },
        LocalFluidGlassModel provides glass.model,
      ) {
        // Un cambio di livello simulato o di manopole fa rinascere tutta la galleria: una superficie sceglie vetro e
        // profilo quando nasce. Pila, impostazioni e interruttori stanno sopra la chiave, e restano.
        key(glass.epoch) {
          FluidTheme(settings = settings) {
            // La policy del movimento si installa in radice, come la skill raccomanda alle app: `FluidTheme` non lo
            // fa, e senza questo con le animazioni di sistema a zero la pillola si ripiegava ancora animata. Sotto,
            // la matrice «movimento ridotto» (`MatrixScope`) la forza a zero, altrimenti riparte da questa.
            FluidMotionPolicyProvider {
              Gallery(settings = settings, onSettingsChange = { settings = it }, nav = nav, matrix = matrix)
            }
          }
        }
      }
    }
  }
}

private val Tabs = listOf(
  FluidTabItem(route = RouteMaterial, label = "Materiale", icon = FluidIcons.Home, filledIcon = FluidIcons.Filled.Home),
  FluidTabItem(route = RoutePlayground, label = "Fisica", icon = FluidIcons.Animation, filledIcon = FluidIcons.Filled.Animation),
  FluidTabItem(route = RouteControls, label = "Controlli", icon = FluidIcons.GridView, filledIcon = FluidIcons.Filled.GridView),
  FluidTabItem(route = RouteChrome, label = "Chrome", icon = FluidIcons.AutoAwesome, filledIcon = FluidIcons.Filled.AutoAwesome),
  FluidTabItem(route = RouteSettings, label = "Impostazioni", icon = FluidIcons.Settings, filledIcon = FluidIcons.Filled.Settings),
)

/**
 * The shell, in the shape a real app has one.
 *
 * The tab bar, the notification host and the modal host are all siblings of the content and all read
 * `chromeController.activeBackdrop` — which is exactly how ClasseViva and Pampa Store are wired. A
 * gallery that special-cased its own chrome would be testing an arrangement no app uses.
 */
@Composable
internal fun Gallery(
  settings: EngineSettings,
  onSettingsChange: (EngineSettings) -> Unit,
  nav: GalleryNav = remember { GalleryNav(GalleryNav.Launch) },
  tools: GalleryTools = SharedGalleryTools,
  matrix: GalleryMatrix = remember { GalleryMatrix() },
) {
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  // La matrice degli stati: vale solo sulle pagine dei componenti nuovi (e sugli host della radice mentre una
  // di quelle e' aperta). La densita' della radice si legge qui, prima che la matrice la cambi: il pannello della
  // matrice si legge sempre alla stessa scala, qualunque cosa stia comandando.
  val baseDensity = LocalDensity.current
  val matrixSnapshot = rememberMatrixSnapshot(matrix, settings)
  val matrixActive = nav.page?.hasMatrix == true
  val chromeController = rememberFluidChromeController()
  val scrollToTop = remember { FluidScrollToTopBus() }
  val modalHost = rememberFluidGlassModalHostState()
  val notificationHost = rememberFluidNotificationHostState()
  val fallbackBackdrop = rememberGlassBackdrop()
  val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop
  val morphMenu = rememberFluidMorphMenuState()
  val toTryList = rememberLazyListState()

  // L'"Indietro" di sistema e' della galleria finche' c'e' una pagina (o una scheda aperta da "Da
  // provare") sopra quella da cui si e' partiti; poi torna al sistema, che chiude l'app.
  BackHandler(enabled = nav.canGoBack) { nav.back() }

  // Dove porta una voce di "Da provare": una scheda o una pagina della galleria, o la schermata a parte
  // delle pagine (un'attivita': il suo "Indietro" e' quello di sistema).
  val context = LocalContext.current
  val openTarget: (TryTarget) -> Unit = { target ->
    when (target) {
      is TryTarget.Tab -> nav.openTab(target.route)
      is TryTarget.Page -> nav.open(target.page)
      is TryTarget.Screen -> context.startActivity(Intent(context, PagesDemoActivity::class.java))
    }
  }

  // Solo nella build di debug: quante volte il vetro ricattura e quante registra il materiale che
  // pubblica per il vetro su vetro (`adb logcat -s FluidGlassDiag`). Una riga quando i numeri
  // cambiano, controllati una volta al secondo: a schermo fermo non deve uscirne nessuna.
  val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
  if (debuggable) {
    LaunchedEffect(Unit) {
      FluidGlassDiagnostics.enabled = true
      var last = ""
      while (true) {
        delay(1_000L)
        val now = "captures=${FluidGlassDiagnostics.totalCaptures} " +
          "exports=${FluidGlassDiagnostics.totalExports}"
        if (now != last) {
          Log.d("FluidGlassDiag", now)
          last = now
        }
      }
    }
  }

  // The bar folds itself now: the connection is what the page scrolls through, and the bar
  // reads it. What the floating controls below want is a number between the two states, so it is
  // animated here rather than held by the bar — the bar's own transition is a shape change, not a
  // value anybody else can borrow.
  val tabBarScroll = rememberFluidFloatingTabBarScrollConnection()
  val barFold by animateFloatAsState(
    targetValue = if (tabBarScroll.isInline) 1f else 0f,
    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
    label = "barFold",
  )

  CompositionLocalProvider(
    LocalFluidGlassModalHostState provides modalHost,
    LocalFluidNotificationHostState provides notificationHost,
    // Le Impostazioni portano alla pagina «Materiale» dagli interruttori del vetro.
    LocalGalleryPageOpener provides { page -> nav.open(page) },
  ) {
    // Il registro aptico e la grana scelta sono della radice: sentono anche i menù e le pagine.
    ProvideGalleryTools(tools) {
      Box(modifier = Modifier.fillMaxSize()) {
       // La matrice avvolge il contenuto e (sotto) gli host, mai la barra delle schede: quella e' chrome
       // dell'app, non il contenuto che si sta giudicando.
       MatrixScope(matrixSnapshot, matrixActive) {
        ProvideFluidChrome(
          controller = chromeController,
          bottomInset = FluidFloatingTabBarDefaults.ContentInset,
          scrollToTop = scrollToTop,
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .nestedScroll(tabBarScroll)
              .fluidGlassModalObscured(),
          ) {
            // Una pagina sta sopra la scheda e la sostituisce (la scheda sotto non si compone: due
            // schermate registrerebbero entrambe il fondale della barra).
            when (nav.page) {
              GalleryPage.ToTry -> ToTryPage(
                bottomInset = bottomInset,
                onBack = { nav.back() },
                onOpen = openTarget,
                listState = toTryList,
              )
              GalleryPage.RowsAndSheets -> RowsAndSheetsDemo(bottomInset, onBack = { nav.back() })
              GalleryPage.CapsulesAndParts -> CapsulesAndPartsPage(bottomInset) { nav.back() }
              GalleryPage.Gaps -> GapsPage(bottomInset) { nav.back() }
              GalleryPage.Menus -> MenusPage(bottomInset) { nav.back() }
              GalleryPage.AllGlass -> AllGlassPage(bottomInset) { nav.back() }
              GalleryPage.MoreComponents -> MoreComponentsPage(bottomInset) { nav.back() }
              // Le misure della prima sessione sul dispositivo: scene fisse, scelte con `measure.scene`.
              GalleryPage.Measure -> MeasurePage(bottomInset) { nav.back() }
              // Il materiale nuovo accanto a quello di prima, per le sessioni sul dispositivo: `material.scene`.
              // Il palco della scena nel fondo che la barra delle schede campiona quando nessuna `FluidScreen` ne
              // registra uno (F1A, giro di correzione): sopra la scena la barra sfoca come sulle altre pagine.
              GalleryPage.Material -> MaterialPage(bottomInset, stage = fallbackBackdrop) { nav.back() }
              // Le icone del set e le taglie nuove (F1A.11): `icons.section`.
              GalleryPage.Icons -> IconsPage(bottomInset) { nav.back() }
              GalleryPage.Robustness -> dev.antigravity.fluidengine.sample.robustness.RobustnessPage(bottomInset) { nav.back() }
              // La parità delle strade del materiale (F1B.6): `parity.scene`, `parity.theme`, `parity.breathe`, `parity.model`.
              GalleryPage.Parity -> dev.antigravity.fluidengine.sample.parity.ParityPage(bottomInset, stage = fallbackBackdrop) { nav.back() }
              // I componenti nuovi della fase 1: una pagina per demo, tutte dentro la matrice degli stati.
              GalleryPage.LongLists,
              GalleryPage.Identity,
              GalleryPage.DrawerAndTabs,
              GalleryPage.NotificationUndo,
              GalleryPage.TokenField,
              GalleryPage.DateTime,
              GalleryPage.RichText,
              GalleryPage.WebContent,
              GalleryPage.Charts,
              GalleryPage.FileTile,
              -> {
                // Una chiave per pagina: passando da una pagina nuova a un'altra il posto nella composizione e'
                // lo stesso, e senza la chiave il pannello aperto e gli interruttori della demo di prima
                // resterebbero a quella dopo.
                val current = requireNotNull(nav.page)
                key(current) {
                  NewComponentPage(
                    page = current,
                    matrix = matrix,
                    snapshot = matrixSnapshot,
                    onSettingsChange = onSettingsChange,
                    bottomInset = bottomInset,
                    onBack = { nav.back() },
                    backdrop = backdrop,
                    baseDensity = baseDensity,
                  )
                }
              }
              null -> when (nav.route) {
                RoutePlayground -> PlaygroundTab(bottomInset)
                RouteControls -> ControlsTab(bottomInset)
                RouteChrome -> ChromeTab(bottomInset, onOpen = { nav.open(it) })
                RouteSettings -> SettingsTab(bottomInset, settings, onSettingsChange)
                else -> MaterialTab(bottomInset, onOpenToTry = { nav.open(GalleryPage.ToTry) })
              }
            }
          }
        }
       }

        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
              horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
              vertical = FluidFloatingTabBarDefaults.BottomMargin,
            ),
        ) {
          FluidFloatingTabBar(
            items = Tabs,
            selectedRoute = nav.route,
            onSelect = { nav.selectTab(it.route) },
            // Un secondo tocco sulla scheda gia' scelta: sotto una pagina la chiude, altrimenti la
            // scheda torna in cima.
            onReselect = { if (nav.canGoBack) nav.popToRoot() else scrollToTop.request() },
            scrollConnection = tabBarScroll,
            backdrop = backdrop,
          )
        }

        if (nav.page == null && nav.route == RouteMaterial) {
          GalleryFloatingControls(
            backdrop = backdrop,
            morphMenu = morphMenu,
            bottomInset = bottomInset,
            fold = { barFold },
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }

        // Above the tab bar, and that placement is the whole reason the modal is in-root: a pop-up
        // that a floating navigation capsule can sit on top of is not a modal.
        // Gli host stanno dentro la matrice come il contenuto: un menu aperto da una demo al 200% si compone
        // qui, e se qui non fosse al 200% la prova non proverebbe niente.
        MatrixScope(matrixSnapshot, matrixActive) {
          FluidGlassModalHost(state = modalHost, backdrop = backdrop)
        // Il tasto che diventa il proprio menù: la superficie che si trasforma vive qui, alla
        // radice, per la stessa ragione dei modali.
          FluidMorphMenuHost(state = morphMenu, backdrop = backdrop)
          FluidNotificationHost(
            state = notificationHost,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
          )
        }
        // Il registro aptico in piccolo, sopra a tutto e senza prendere il tocco (si accende da Controlli).
        GalleryToolsOverlay(tools)
      }
    }
  }
}

/** La demo delle capsule e dei pezzi piccoli: una colonna sola, qui in una schermata con il suo "Indietro". */
@Composable
private fun CapsulesAndPartsPage(bottomInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
  FluidScreen(
    title = "Capsule e pezzi piccoli",
    subtitle = "Capsula, ricerca, pallino dal vivo, scheletri, selezione, punti del pager, passi.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Tertiary, motif = FluidHeroMotif.Dots) },
  ) {
    item(key = "capsules-and-parts") { CapsulesAndPartsDemo() }
  }
}

/**
 * I menu liquidi (fase 7): una colonna sola, qui in una schermata con il suo "Indietro".
 *
 * La demo ha bisogno dell'host dei modali della radice (`LocalFluidGlassModalHostState`): le azioni
 * della barra, le righe, la card e l'avviso vivono li', e `Gallery` lo fornisce a tutto il contenuto.
 * Il tasto che si trasforma ha invece il suo host dentro il palco della demo.
 */
@Composable
private fun MenusPage(bottomInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
  FluidScreen(
    title = "Menù liquidi",
    subtitle = "Tieni premuto, trascina su una voce, rilascia: si sceglie col dito che ha aperto il menu.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Ripples) },
  ) {
    item(key = "menus-demo") { MenusDemo() }
  }
}

/**
 * Tutto in vetro vero (fase 9): chip, segmentato, rail, indice di sezione, barra con la banda,
 * suggerimento, sincronizzazione e fogli, ognuno su un riquadro che il vetro puo' rifrangere.
 *
 * Le sezioni che aprono un foglio usano l'host dei modali della radice, che `Gallery` fornisce a tutto
 * il contenuto: per questo la demo sta qui e non in una schermata a parte.
 */
@Composable
private fun AllGlassPage(bottomInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
  FluidScreen(
    title = "Tutto in vetro",
    subtitle = "Chip, segmentato, rail, indice, barra con la banda, suggerimento, sincronizzazione, fogli.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Dots) },
  ) {
    item(key = "all-glass-demo") { AllGlassDemo() }
  }
}

/**
 * I pezzi generici della fase 12c: l'aggiornamento in-app, il riordino, lo scorrimento rapido e il
 * visualizzatore. La demo si porta il suo host dei modali (il visore e il foglio dell'aggiornamento
 * stanno li'), quindi qui basta una colonna.
 */
@Composable
private fun MoreComponentsPage(bottomInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
  FluidScreen(
    title = "Riordino, aggiornamento e visore",
    subtitle = "Aggiornamento in-app, riordino trascinando, scorrimento rapido con la bolla, visualizzatore.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Ripples) },
  ) {
    item(key = "more-components-demo") { MoreComponentsDemo() }
  }
}

/** I buchi delle app (fase 12a): le stesse sezioni che stanno in fondo a Controlli, qui da sole. */
@Composable
private fun GapsPage(bottomInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
  FluidScreen(
    title = "I buchi delle app",
    subtitle = "Taglie, messaggio, campo segreto, barre, piastrella, stato vuoto, gruppi, aptica.",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Secondary, motif = FluidHeroMotif.Dots) },
  ) {
    gapsDemoItems()
    glassGrowthContrastItems()
  }
}

/**
 * Controls parked over the page rather than inside it.
 *
 * A glass button that lives in a list has an opaque row behind it and cannot show what it does; the
 * only honest place to look at one is floating over moving content, which is also the only place a
 * real app should use one.
 */
@Composable
private fun GalleryFloatingControls(
  backdrop: GlassBackdropState,
  morphMenu: FluidMorphMenuState,
  bottomInset: androidx.compose.ui.unit.Dp,
  fold: () -> Float,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val openPx = with(density) {
    (FluidFloatingTabBarDefaults.OpenHeight + FluidFloatingTabBarDefaults.BottomMargin + 18.dp)
      .toPx()
  }
  val foldedPx = with(density) {
    (FluidFloatingTabBarDefaults.FoldedHeight + FluidFloatingTabBarDefaults.BottomMargin + 18.dp)
      .toPx()
  }
  CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    Row(
      modifier = modifier
        // Riding the fold rather than parked at a fixed height. Left alone, these sat exactly where
        // the open bar used to be — so folding the bar left them hanging in the middle of nothing,
        // which is worse than either state on its own.
        .graphicsLayer {
          translationY = -androidx.compose.ui.util.lerp(openPx, foldedPx, fold().coerceIn(0f, 1f))
        }
        .padding(bottom = bottomInset)
        .padding(horizontal = 18.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidGlassButton(text = "Attiva", onClick = {}, backdrop = backdrop, selected = true)
      // Tenuto premuto, il tasto SI ESPANDE nel proprio menù: Fluid-physics al lavoro in un
      // componente vero — la capsula viaggia fino a essere il pannello, e torna.
      FluidMorphMenuButton(
        state = morphMenu,
        text = "Azione",
        onClick = {},
        backdrop = backdrop,
        actions = {
          listOf(
            FluidContextAction("Condividi", FluidIcons.Share) {},
            FluidContextAction("Aggiungi ai preferiti", FluidIcons.Filled.Star) {},
            FluidContextAction("Elimina", FluidIcons.Delete, destructive = true) {},
          )
        },
      )
      FluidGlassIconButton(onClick = {}, backdrop = backdrop) {
        FluidIcon(
          imageVector = FluidIcons.AutoAwesome,
          contentDescription = "Effetto",
        )
      }
    }
  }
}
