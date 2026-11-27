<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/prima-volta.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Prima volta: benvenuto e novità, permessi, sfondi vivi, primo fotogramma

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidFirstFrame.kt`

- `fun Activity.fluidFirstFrame()` — Il primo fotogramma pronto, per un'Activity con Compose. Si chiama in `onCreate`, **prima** di `setContent`:

## `engine-ui/ui/fluid/FluidFirstUseLabels.kt`

- `data class FluidFirstUseLabels(val welcomeTitle: (appName: String) -> String = { "Benvenuto in $it" }, val whatsNewTitle: (appName: String) -> String = { "Novità di $it" }, val continueLabel: String = "Continua", val privacyLink: String = "Scopri come vengono gestiti i tuoi dati…", val stepPosition: (page: Int, count: Int) -> String = { page, count -> "Passo $page di $count" }, val permissionContinue: String = "Continua", val permissionNotNow: String = "Non ora", val permissionOpenSettings: String = "Apri Impostazioni", val permissionOffTitle: (FluidPermission) -> String = { "Accesso ${it.name} non consentito" }, val permissionOffHint: String = "Puoi consentirlo nelle Impostazioni dell'app.", val permissionTitle: (FluidPermission) -> String = { "Consentire l'accesso ${it.name}?" })` — Le parole della prima volta (F8.11): il foglio di benvenuto e delle novità, la spiegazione di un permesso e il suo «Apri Impostazioni». Di serie in italiano; [english] è la versione inglese completa. Un'app in un'altra lingua ne dà una sua con [LocalFluidFirstUseLabels].
  - `companion object`
    - `fun english(): FluidFirstUseLabels` — Le stesse parole in inglese.
- `val LocalFluidFirstUseLabels` — Le parole della prima volta per il sottoalbero.
- `val LocalFluidFirstUseMemory` — La memoria della prima volta per il sottoalbero: cosa ha già visto il benvenuto, quali permessi sono già stati chiesti. L'app la fornisce alla radice con `EngineFirstUseStore(context)` di `engine-storage`; di serie è una memoria che dura quanto il processo (il benvenuto tornerebbe a ogni avvio a freddo, e un permesso negato per sempre sembrerebbe mai chiesto): va bene per le prove, non per un'app.

## `engine-ui/ui/fluid/FluidLiveBackground.kt`

- `class FluidLiveBackground private constructor(internal val kind: Int, private val label: String)` — Uno sfondo vivo: [Aurora] (aloni di colore che vagano piano in alto), [Waves] (onde morbide in basso) o [Stars] (puntini che respirano). Una classe con le sue costanti e non un enum, così uno sfondo nuovo si aggiunge senza rompere chi li usa.
  - `companion object`
    - `val Aurora: FluidLiveBackground` — L'aurora: tre aloni larghi coi colori del tema che vagano piano nella metà alta, e sotto la pagina pulita.
    - `val Waves: FluidLiveBackground` — Le onde: tre onde morbide nella metà bassa, che scorrono di lato in decine di secondi.
    - `val Stars: FluidLiveBackground` — Le stelle: puntini radi che respirano, ognuno col suo ritmo; sul tema chiaro diventano granelli di colore.
- `object FluidLiveBackgroundDefaults` — I numeri degli sfondi vivi.
  - `const val FrameRateHz: Int` — Il ritmo dello sfondo: **30 Hz**. Lo sfondo è lentissimo (le onde più svelte fanno un giro in una quarantina di secondi), e a 30 Hz il passo di un fotogramma è sotto il pixel: non si vede, e il vetro sopra ricampiona la metà delle volte che a 60 Hz e un quarto che a 120. Scelto il 24 novembre 2026, misura nel rapporto di F8.11.
  - `const val RestSeconds: Float` — L'istante disegnato quando lo sfondo è fermo (movimento ridotto, risparmio energetico, batteria scarica): **12 s**, una posa in cui gli aloni e le onde sono ben distribuiti. Fermo vuol dire un fotogramma solo, leggibile.
  - `const val WrapSeconds: Float` — Ogni quanto il tempo ricomincia: **200π s** (10 min e 28 s). Tiene i seni precisi in `float`, e il ritorno a zero non si vede: tutte le frequenze del disegno sono multipli di 0,01 rad/s, quindi in 200π secondi ogni onda fa un numero intero di giri (R2: con 30 minuti gli aloni saltavano fino a un decimo della pagina). Una frequenza nuova deve restare un multiplo di 0,01 rad/s (`F8p11LiveBackgroundWrapTest`).
- `class FluidLiveBackgroundState internal constructor()` — Lo stato di uno sfondo vivo: se sta andando ([isRunning]) e quanti fotogrammi nuovi ha disegnato ([framesDrawn]), per le prove e per le misure. Non serve tenerlo: [FluidLiveBackgroundCanvas] ne crea uno suo.
  - `var isRunning: Boolean by mutableStateOf(false)` — Lo sfondo si muove: falso col movimento ridotto, col risparmio energetico, con la batteria scarica o a pagina nascosta.
  - `var framesDrawn: Long` — Quanti istanti nuovi lo sfondo ha chiesto da quando c'è (non è uno stato osservato: leggerlo non ricompone).
- `@Composable fun rememberFluidLiveBackgroundState(): FluidLiveBackgroundState` — Lo stato di uno sfondo vivo, da passare a [FluidLiveBackgroundCanvas] quando lo si vuole leggere.
- `@Composable fun FluidLiveBackgroundCanvas(background: FluidLiveBackground, modifier: Modifier = Modifier, tone: FluidHeroTone = FluidHeroTone.Primary, intensity: Float = 1f, state: FluidLiveBackgroundState = rememberFluidLiveBackgroundState(), frameRateHz: Int = FluidLiveBackgroundDefaults.FrameRateHz)` — Uno sfondo vivo a tutta la misura del suo contenitore, opaco, coi colori del tema ([tone], di serie il primario; vedi [FluidHeroTone]). Va sotto il contenuto di una pagina speciale, **non** sotto le pagine di tutti i giorni: un benvenuto, la pagina vuota dell'assistente, un traguardo. In una [FluidScreen] si passa con `ambient = FluidAmbient(…, live = …)`, così la barra e il vetro della pagina lo rifrangono.

## `engine-ui/ui/fluid/FluidPermission.kt`

- `class FluidPermission private constructor(val id: String, val name: String, private val required: (sdk: Int) -> List<String>, private val partialOnly: (sdk: Int) -> List<String>, private val notifications: Boolean = false)` — Un permesso di Android come lo chiede un tasto: uno o più permessi del manifest, a seconda della versione di Android, e quelli che da soli valgono «in parte» (le foto «solo alcune» di Android 14).
  - `fun permissions(sdk: Int = Build.VERSION.SDK_INT): List<String>` — I permessi da chiedere su questa versione di Android: vuoto se non ce n'è uno da chiedere (notifiche prima del 13).
  - `companion object`
    - `val Microphone: FluidPermission` — Il microfono (`RECORD_AUDIO`): dettare, registrare una nota, parlare con l'assistente.
    - `val Camera: FluidPermission` — La fotocamera (`CAMERA`): scansionare, il visore AR.
    - `val Notifications: FluidPermission` — Le notifiche: `POST_NOTIFICATIONS` da Android 13; prima non si chiedono, e se l'utente le ha spente si accendono nelle impostazioni delle notifiche dell'app.
    - `val Photos: FluidPermission` — Le foto: `READ_MEDIA_IMAGES` da Android 13 (`READ_EXTERNAL_STORAGE` prima) e, da Android 14, `READ_MEDIA_VISUAL_USER_SELECTED`, che da solo vuol dire **solo alcune foto** ([FluidPermissionStatus.Partial]).
    - `val PhotosAndVideos: FluidPermission` — Come [Photos], anche coi video (`READ_MEDIA_VIDEO`).
    - `val ApproximateLocation: FluidPermission` — La posizione approssimativa (`ACCESS_COARSE_LOCATION`): il meteo, una fermata vicina.
    - `fun of(id: String, name: String, vararg permissions: String): FluidPermission` — Uno o più permessi qualsiasi, tutti necessari, su ogni versione.
- `class FluidPermissionState internal constructor(val permission: FluidPermission, private val memory: FluidFirstUseMemory, private val scope: CoroutineScope, promptState: MutableState<PermissionPrompt?> = mutableStateOf(null), unansweredState: MutableIntState = mutableIntStateOf(0), actionDueState: MutableState<Boolean> = mutableStateOf(false))` — Lo stato di un permesso per un tasto: [status], [request] dal tasto, [requestMore] per le foto «solo alcune», [openSettings]. Si crea con [rememberFluidPermission]; il tasto porta [Modifier.fluidPermissionSource] in testa ai suoi modificatori, così la spiegazione nasce da lui e ci rientra.
  - `var status: FluidPermissionStatus by mutableStateOf(FluidPermissionStatus.NeverAsked)` — Lo stato di adesso: si aggiorna dopo il dialogo e, da solo, al ritorno dalle impostazioni.
  - `var isLoaded: Boolean by mutableStateOf(false)` — La memoria è letta: prima, [status] non distingue ancora «mai chiesto» da «negato per sempre».
  - `fun request(onGranted: () -> Unit = NoAction)` — Il tocco del tasto: se il permesso c'è (anche in parte) l'azione parte subito; altrimenti la prima volta nasce la spiegazione dal tasto, poi il dialogo di Android, e se l'utente consente l'azione parte da sola. Da negato si chiede direttamente; da negato per sempre la spiegazione offre «Apri Impostazioni».
  - `fun requestMore()` — Concesso in parte (le foto «solo alcune»): mostra di nuovo la scelta di Android per aggiungerne altre.
  - `fun openSettings()` — Apre le impostazioni giuste dell'app (quelle delle notifiche, per le notifiche). Al ritorno lo stato si rilegge.
  - `fun refresh()` — Rilegge lo stato da Android (lo fa da solo alla ripresa dell'Activity).
- `@Composable fun rememberFluidPermission(permission: FluidPermission, reason: String, title: String? = null, explain: Boolean = true, memory: FluidFirstUseMemory = LocalFluidFirstUseMemory.current, onResult: (FluidPermissionStatus) -> Unit = {}, onGranted: (() -> Unit)? = null): FluidPermissionState` — Un permesso chiesto nel punto giusto (F8.11): dal tasto che ne ha bisogno.
- `fun Modifier.fluidPermissionSource(state: FluidPermissionState, shape: Shape = FluidButtonDefaults.shape(FluidButtonSize.Mediu…): Modifier` — Il tasto da cui nasce la spiegazione del permesso [state]: in testa ai modificatori del tasto (prima di fondo e contenuto), così mentre la spiegazione è in scena il tasto non si disegna e lei ci rientra. [shape] è il contorno del tasto: di serie quello di un `FluidButton` medio.

## `engine-ui/ui/fluid/FluidWelcome.kt`

- `object FluidWelcomeDefaults` — Le misure e i tempi del foglio di benvenuto.
  - `val TopPadding: Dp` — Dal bordo alto del foglio alla prima cosa (l'icona o il titolo): **65 dp** (WhatsNewKit `contentPadding.top`, fiducia media).
  - `val PageIconSize: Dp` — L'icona del passo sopra il titolo, nell'accento: **56 dp** (Password di iOS 18: 55 pt d'altezza, fiducia alta).
  - `val IconToTitle: Dp` — Fra l'icona e il titolo: **33 dp** (Password di iOS 18: 41 pt fino al tratto del titolo, 33 fino alla sua riga).
  - `val TitleToFeatures: Dp` — Fra il titolo e la prima riga: **40 dp** (Password di iOS 18: 40,5 pt fra le righe di testo, fiducia alta; WhatsNewKit ne mette 60).
  - `val TitleToMessage: Dp` — Fra il titolo e la frase di un passo senza righe: **24 dp** (Password di iOS 18, «Turn On Notifications»).
  - `val FeatureSpacing: Dp` — Fra una riga e la successiva: **25 dp** (WhatsNewKit `featureListSpacing`; Password di iOS 18 ≈ 25 pt).
  - `val FeatureIconWidth: Dp` — La colonna dell'icona: **40 dp** (WhatsNewKit `featureImageWidth`; in Password il centro sta a 60 pt dal bordo).
  - `val FeatureIconSize: Dp` — Il glifo nella colonna: **32 dp**, a occhio il 24 novembre 2026 (in iOS è un SF Symbol grande, nell'accento).
  - `val FeatureGap: Dp` — Fra la colonna dell'icona e il testo: **18 dp** (Password di iOS 18: il testo parte a 98 pt, fiducia alta).
  - `val FeatureLineGap: Dp` — Fra il titolo di una riga e la sua frase: **2 dp** (WhatsNewKit `featureVerticalSpacing`, fiducia media).
  - `val HorizontalPadding: Dp` — Il margine dei lati: **16 dp**, più **24 dp** ai due lati delle righe: la colonna delle icone parte a 40 dal bordo (Password di iOS 18: icone centrate a 60 pt, testo che va a capo prima di 353 pt; WhatsNewKit ne mette 15 a sinistra).
  - `val FeaturesLeadingPadding: Dp`
  - `val FeaturesTrailingPadding: Dp`
  - `val FooterHorizontalPadding: Dp` — I lati del tasto e della nota in basso: **20 dp** (WhatsNewKit, telefono in verticale, fiducia media).
  - `val FooterBottomPadding: Dp` — Sotto il tasto: **24 dp**, a occhio il 24 novembre 2026 (il foglio è già scostato dalla barra di navigazione).
  - `val FooterGap: Dp` — Sopra la nota sulla privacy e fra la nota e il tasto: **16 dp**, a occhio il 24 novembre 2026.
  - `val MaxWidth: Dp` — Oltre questa larghezza il contenuto resta centrato (come [FluidSheetScaffoldDefaults.MaxWidth]).
  - `const val LargeTextScale: Float` — Da questa scala del testo la nota sulla privacy passa nel corpo che scorre: in basso restano solo i pallini e il tasto, che non devono coprire mezzo schermo (130%, a occhio il 24 novembre 2026).
  - `const val FirstFeatureDelayMillis: Int` — Quando entra la prima riga, dall'apertura del foglio: **250 ms**, quando il foglio ha quasi finito di salire. A occhio il 24 novembre 2026: Apple mostra le righe ferme, l'entrata una dopo l'altra è la scelta del piano (F8.11).
  - `const val FeatureStaggerMillis: Int` — Il passo fra una riga e la successiva: **90 ms**, a occhio il 24 novembre 2026.
  - `const val FeatureArrivalMillis: Int` — Quanto ci mette una riga a posarsi e a mettersi a fuoco: **380 ms**, come una riga di testo che arriva (F8.1).
  - `val FeatureRise: Dp` — Da quanto sotto sale una riga che entra: **14 dp**, a occhio il 24 novembre 2026.
  - `val FeatureBlur: Dp` — La sfocatura di una riga che entra, al massimo: **6 dp** di σ, quella dell'immagine che arriva (F8.1). È l'app che fa entrare le righe, non il dito: per la regola di Alessio del 24 novembre si sfocano.
- `data class FluidWelcomeFeature(val icon: ImageVector, val title: String, val text: String = "")` — Una riga del foglio: l'icona (nell'accento), il titolo e la frase.
- `class FluidWelcomePage(val title: String, val features: List<FluidWelcomeFeature> = emptyList(), val actionLabel: String? = null, val content: (@Composable ColumnScope.() -> Unit)? = null, val icon: ImageVector? = null, val message: String? = null)` — Un passo del benvenuto: il titolo grande, le righe e, se serve, un contenuto dell'app sotto le righe (per esempio la scelta di un tema o il tasto di un permesso). [actionLabel] è il testo del tasto per questo passo (di serie «Continua»); [icon] è il segno sopra il titolo, nell'accento (l'icona dell'app, la campanella di un passo sulle notifiche);
- `fun FluidReleaseNote.toWelcomeFeature(icon: ImageVector = fluidReleaseNoteIcon(symbol)): FluidWelcomeFeature` — Una novità del manifest come riga del foglio, con l'icona del suo simbolo ([fluidReleaseNoteIcon]).
- `fun fluidReleaseNoteIcon(symbol: String?): ImageVector` — L'icona di un simbolo scritto nel manifest (`"symbol": "star"`): un vocabolario piccolo e stabile, in inglese come i nomi di SF Symbols. Un nome che non c'è, o nessun nome: le scintille delle novità.
- `object FluidLaunchOrigin` — Da dove arriva un'apertura: un link, una condivisione o una notifica dell'app ([ExtraFromNotification]) non sono un'apertura dall'icona, e il benvenuto non ci si mette davanti.
  - `const val ExtraFromNotification: String` — L'extra che l'app mette sull'intent delle sue notifiche: `intent.putExtra(ExtraFromNotification, true)`.
  - `fun isDirect(intent: Intent?): Boolean` — Vero per un'apertura da link, condivisione o notifica; falso dall'icona, da un collegamento o da un `am start`.
- `class FluidWelcomeState internal constructor(private val memory: FluidFirstUseMemory, private val key: String, val versionName: String, private val scope: CoroutineScope)` — Lo stato del benvenuto: se all'apertura va mostrato qualcosa ([kind]), e quando ([isShowing]).
  - `var kind: FluidWelcomeKind? by mutableStateOf(null)` — Cosa mostrare: il benvenuto, le novità, o niente. Resta valido mentre il foglio esce.
  - `var isDecided: Boolean by mutableStateOf(false)` — La decisione è presa (la memoria è letta e, se servivano, le novità sono arrivate).
  - `var isShowing: Boolean by mutableStateOf(false)` — Il foglio è in scena: da passare come `visible` a [FluidWelcomeSheet].
  - `fun dismiss()` — Chiude il foglio (il «Continua» dell'ultimo passo, o il foglio tirato giù). La versione è già segnata come vista.
  - `fun forget()` — Dimentica la versione vista e decide di nuovo: per le prove e per una voce «Mostra di nuovo le novità».
- `@Composable fun rememberFluidWelcomeState(versionName: String, hasNews: Boolean? = false, hasWelcome: Boolean = true, suppressed: Boolean = false, busy: Boolean = false, freshInstall: Boolean? = null, memory: FluidFirstUseMemory = LocalFluidFirstUseMemory.current, scope: String = "app"): FluidWelcomeState` — Lo stato del benvenuto per questa apertura.
- `@Composable fun FluidWelcomeSheet(visible: Boolean, onFinish: () -> Unit, pages: List<FluidWelcomePage>, modifier: Modifier = Modifier, privacyNote: String? = null, onPrivacyClick: (() -> Unit)? = null)` — Il foglio di benvenuto o delle novità, a tutta altezza: un [FluidGlassSheet] al detent `Large` (opaco e attaccato ai bordi, come il foglio di pagina di iOS), senza maniglia, con dentro [FluidWelcomeContent].
- `@Composable fun FluidWelcomeSheet(visible: Boolean, onFinish: () -> Unit, title: String, features: List<FluidWelcomeFeature>, modifier: Modifier = Modifier, privacyNote: String? = null, onPrivacyClick: (() -> Unit)? = null)` — Il foglio con un passo solo: il caso di «Novità di…».
- `@Composable fun FluidWelcomeContent(pages: List<FluidWelcomePage>, onFinish: () -> Unit, modifier: Modifier = Modifier, privacyNote: String? = null, onPrivacyClick: (() -> Unit)? = null, background: (@Composable BoxScope.() -> Unit)? = null)` — Il contenuto del benvenuto, da mettere in un foglio ([FluidWelcomeSheet]) o in una pagina intera del primo avvio.
