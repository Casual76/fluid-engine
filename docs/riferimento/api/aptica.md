<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/aptica.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Aptica

_Generato sull'engine 3.0.5 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/haptics/AndroidFluidHaptics.kt`

- `interface HapticPort` — Il dito del device, astratto: cosi' i cancelli (impostazione, sistema, risparmio energetico, anti-raffica) si provano sul computer con un vibratore finto.
  - `val hasVibrator: Boolean`
  - `fun supportedPrimitives(ids: IntArray): Set<Int>` — Quali delle primitive chieste il device sa fare; vuoto sotto API 30.
  - `fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean): Boolean` — Vero se la vibrazione e' partita davvero: falso lascia la parola alla costante di piattaforma.
  - `fun platform(type: androidx.compose.ui.hapticfeedback.HapticFeedbackType)`
  - `val systemHapticsEnabled: Boolean`
  - `val powerSave: Boolean`
- `class FluidHapticsImpl(private val port: HapticPort, private val enabled: () -> Boolean, private val clock: () -> Long = System::currentTimeMillis) : FluidHaptics` — L'aptica di un tema: composizioni di primitive dove il device le ha (l'S25 si'), le costanti di piattaforma altrove; sempre dietro all'interruttore dell'app, all'impostazione tattile di sistema e, per i tick continui, al risparmio energetico. Un tick continuo non si ripete piu' spesso di ogni 40 ms: sotto, il motore lo perde comunque e la batteria no.
  - `fun usesComposition(event: FluidHapticEvent): Boolean` — Vero se questo evento andra' a composizione (le sue primitive ci sono tutte).
  - `fun info(): FluidHapticsInfo` — Cosa sa fare questo device, per una pagina di prova: motore, primitive, cancelli di sistema.
  - `companion object`
    - `const val CONTINUOUS_MIN_GAP_MILLIS`
    - `const val GRAIN_MIN_GAP_MILLIS` — Il tetto della grana: un granello ogni 8 ms al massimo (sotto, il motore non li separa).
- `data class FluidHapticsInfo(val engine: FluidHapticEngine, val hasVibrator: Boolean, val supportedPrimitives: Set<Int>, val systemHapticsEnabled: Boolean, val powerSave: Boolean)` — La fotografia del vibratore per una pagina "Prova i feedback".
- `class AndroidHapticPort(private val context: Context, private val platformHaptics: HapticFeedback? = null) : HapticPort` — Il vibratore vero, e le costanti di Compose per il ripiego.
- `fun fluidHapticsFor(context: Context, enabled: () -> Boolean = { true }): FluidHaptics` — L'aptica dell'engine per il codice che non e' Compose: una tastiera (`InputMethodService`), un `View`, un servizio, un'attivita' a viste.
- `@Composable fun rememberAndroidFluidHaptics(enabled: Boolean): FluidHaptics` — L'istanza per un tema: una per composizione del tema, che legge l'interruttore a ogni evento.

## `engine-ui/ui/haptics/FluidHapticGrain.kt`

- `enum class FluidHapticGrainStyle` — Come si decide e come suona la grana di un dito che trascina un tasto (3.0).
- `data class FluidHapticGrainConfig(val style: FluidHapticGrainStyle = FluidHapticGrainStyle.LibrePods, val librePodsSquaredPx: Float = 350f, val onsetDpPerSec: Float = 520f, val releaseDpPerSec: Float = 380f, val fullDpPerSec: Float = 2400f, val spacingDp: Float = 8f, val spacingJitter: Float = 0.3f, val minGapMillis: Long = 9L, val minIntensity: Float = 0.14f, val maxIntensity: Float = 0.5f, val intensityJitter: Float = 0.2f, val smoothingMillis: Float = 24f)` — I numeri della grana (3.0). Di serie vanno bene per tutte le app: esistono come classe perche' la galleria li tara dal vivo, sul telefono, che e' l'unico posto dove la grana si giudica.
  - `companion object`
    - `val Default`
- `val LocalFluidHapticGrainConfig: ProvidableCompositionLocal<FluidHapticGrainConfig>` — La grana in uso. Le app non lo toccano: e' il gancio della pagina di taratura della galleria.

## `engine-ui/ui/haptics/FluidHapticTrace.kt`

- `class FluidHapticTrace(val capacity: Int = DefaultCapacity, private val clock: () -> Long = ::monotonicMillis)` — Il registro aptico (3.0): cosa e' stato chiesto al vibratore, e quando.
  - `var recording: Boolean by mutableStateOf(true)` — Falso: il registro smette di scrivere (e solo quello, il motore vero suona lo stesso).
  - `val entries: List<FluidHapticTraceEntry> get()` — Le voci, dalla piu' vecchia alla piu' nuova. Una lettura in composizione si aggiorna da sola.
  - `fun recordEvent(event: FluidHapticEvent)` — Un evento del vocabolario e' stato chiesto adesso.
  - `fun recordGrain(intensity: Float, style: FluidHapticGrainStyle)` — Un granello della grana e' stato chiesto adesso.
  - `fun clear()` — Svuota la lista. Le reti stimate no: il motore vero non si e' azzerato.
  - `fun rows(maxRows: Int = DefaultRows): List<FluidHapticTraceRow>` — Le righe da mostrare, le piu' nuove per prime: vedi [fluidHapticTraceRows].
  - `fun wrap(delegate: FluidHaptics): FluidHaptics` — [delegate] guardato da questo registro. Se e' gia' guardato da questo stesso registro, e' lui.
  - `companion object`
    - `const val DefaultCapacity`
    - `const val DefaultRows`
- `enum class FluidHapticTraceOutcome` — Quanto il motore ne ha fatto, per quanto se ne sa da fuori.
- `data class FluidHapticTraceEntry(val atMillis: Long, val event: FluidHapticEvent?, val grainIntensity: Float = 0f, val grainStyle: FluidHapticGrainStyle? = null, val outcome: FluidHapticTraceOutcome = FluidHapticTraceOutcome.Played)` — Una voce del registro: un evento del vocabolario ([event]) o un granello di grana ([event] nullo, con [grainIntensity] e [grainStyle]).
  - `val isGrain: Boolean get()`
- `data class FluidHapticTraceRow(val label: String, val detail: String?, val count: Int, val deltaMillis: Long?, val outcome: FluidHapticTraceOutcome, val atMillis: Long)` — Una riga di registro leggibile: un evento, o una **fila** di granelli consecutivi dello stesso stile (la grana ne fa decine al secondo: una riga ciascuno coprirebbe il resto).
  - `val text: String get()` — Il nome con il conteggio, per una riga sola: `grana LibrePods ×14`.
  - `val startsMoment: Boolean` — Vero se la riga comincia un momento nuovo (almeno la finestra della rete dei 70 ms dopo la precedente).
- `const val FluidHapticTraceGrainRunGapMillis` — Due granelli piu' lontani di cosi' non sono la stessa fila.
- `fun fluidHapticTraceRows(entries: List<FluidHapticTraceEntry>, maxRows: Int = 20): List<FluidHapticTraceRow>` — Le righe di [entries] (dalla piu' vecchia alla piu' nuova), **le piu' nuove per prime** e al piu' [maxRows]. Pura: la prova sta sul computer.
- `class TracingFluidHaptics internal constructor(val delegate: FluidHaptics, val trace: FluidHapticTrace) : FluidHaptics` — Il decoratore: inoltra tutto a [delegate] e, prima, ne scrive una copia in [trace].
- `val LocalFluidHapticTrace: ProvidableCompositionLocal<FluidHapticTrace?>` — Il registro installato sopra questo punto dell'albero, se c'e'. Nullo di serie: niente registro, niente costo.
- `@Composable fun ProvideFluidHapticTrace(trace: FluidHapticTrace? = LocalFluidHapticTrace.current, content: @Composable () -> Unit)` — Mette [trace] attorno all'aptica del tema corrente per tutto [content], e lo rende leggibile con [LocalFluidHapticTrace].

## `engine-ui/ui/haptics/FluidHaptics.kt`

- `enum class FluidHapticEvent(val continuous: Boolean = false)` — Il vocabolario aptico del design system (1.19.0): ogni evento e' una cosa che l'utente sente sotto il dito e che vuol dire sempre la stessa cosa in ogni app Pampa. I componenti dell'engine lo usano da soli (un tasto che cresce fa [PressDown], [PressRelease] e [PressClick] dalla 3.0, una superficie che cede [Tap], uno switch [ToggleOn]/[ToggleOff], un menu che si apre [Open]);
- `enum class FluidHapticEngine` — Con cosa si vibra: le composizioni di primitive (ricche), le costanti di piattaforma, o niente.
- `interface FluidHaptics`
  - `val engine: FluidHapticEngine`
  - `fun play(event: FluidHapticEvent)`
  - `fun grain(intensity: Float, style: FluidHapticGrainStyle = FluidHapticGrainStyle.Fluid)` — Un granello della grana di un trascinamento veloce (3.0). Non e' un evento del vocabolario perche' non ha un pattern fisso: dipende dallo [style] e, per [FluidHapticGrainStyle.Fluid], da quanto corre il dito ([intensity], 0..1, che per [FluidHapticGrainStyle.LibrePods] non conta: il tick e' sempre la costante di sistema). Lo suona [FluidHapticGrain] attraverso i tasti che si trascinano;
- `object NoOpFluidHaptics : FluidHaptics` — Fuori da un [dev.antigravity.fluidengine.ui.theme.FluidTheme] (anteprime, test) non si vibra.
- `val LocalFluidHaptics: ProvidableCompositionLocal<FluidHaptics>`
- `@Composable fun rememberFluidHaptics(): FluidHaptics` — L'aptica del tema corrente. Statico: cambia solo con le impostazioni, non ricompone i figli.
- `object HapticPrimitive` — Le primitive di `VibrationEffect.Composition`, con gli stessi valori numerici di Android (API 30+): tenute qui come costanti proprie perche' i pattern devono essere puri e provati sul computer, dove `android.os.VibrationEffect` non esiste.
  - `const val CLICK`
  - `const val THUD`
  - `const val SPIN`
  - `const val QUICK_RISE`
  - `const val SLOW_RISE`
  - `const val QUICK_FALL`
  - `const val TICK`
  - `const val LOW_TICK`
  - `val all: List<Int>` — Tutte le primitive, nell'ordine di Android: per elencarle in una pagina di prova.
  - `fun label(id: Int): String` — Il nome della primitiva come lo scrive Android (`PRIMITIVE_CLICK`...), senza il prefisso.
  - `fun nominalMillis(id: Int): Int` — Durate nominali in millisecondi, per il budget dei pattern (Google le documenta cosi').
- `data class HapticPrimitiveStep(val id: Int, val scale: Float, val delayMillis: Int = 0)` — Una primitiva in una composizione: quale, quanto forte (0..1), dopo quanto dalla precedente.
- `object FluidHapticPatterns` — I pattern, evento per evento: la composizione (secca e breve, stile iOS — tick e click per i controlli, rampe solo per l'assistente e i gesti, il tonfo solo per errori e allerta; nessun pattern oltre i 400 ms, nessun controllo oltre i 120) e il ripiego di piattaforma per i device senza primitive. Da tarare sul telefono con la pagina "Prova i feedback" delle app.
  - `fun composition(event: FluidHapticEvent): List<HapticPrimitiveStep>`
  - `fun fallback(event: FluidHapticEvent): HapticFeedbackType` — Le costanti di Compose per i device senza primitive; sotto API 30 Compose degrada da solo.
  - `val platformOnly: Set<FluidHapticEvent>` — Gli eventi che suonano **solo** la costante di piattaforma di [fallback], mai una composizione (3.0): i tre colpi di un tasto che cresce. Il motore li manda dritti a `performHapticFeedback`, dopo l'interruttore dell'app e il controllo del vibratore ma prima di qualunque rete: non contano per [coalescible] e non la armano, perche' rilascio e click cadono nello stesso istante e suonare entrambi e' il carattere di Libre…
  - `fun durationMillis(event: FluidHapticEvent): Int` — Quanto dura un pattern, secondo le durate nominali: il test tiene i controlli sotto i 120 ms.
  - `val controlEvents: Set<FluidHapticEvent>` — Gli eventi dei controlli: quelli che devono restare secchi.
  - `val coalescible: Set<FluidHapticEvent>` — Gli eventi di un gesto su un controllo che non devono mai sovrapporsi (3.0): se ne arriva uno entro [CoalesceWindowMillis] dal precedente, il secondo non suona. Due eventi a pochi millisecondi l'uno dall'altro non sono due momenti, sono lo stesso momento detto da due componenti (il tasto e il pannello che apre), e il vibratore tronca il primo per suonare il secondo.
  - `const val CoalesceWindowMillis` — La finestra entro cui due eventi di [coalescible] sono lo stesso momento.
  - `val primitivesUsed: IntArray` — Tutte le primitive che i pattern usano: il motore le chiede al device una volta sola.
