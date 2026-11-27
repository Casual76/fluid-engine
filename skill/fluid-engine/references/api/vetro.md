# API · Vetro

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidGlass.kt`

- `class FluidGlass private constructor(internal val variant: Variant, val tint: Color, val isInteractive: Boolean)` — Quale vetro: la configurazione del materiale nuovo, come `Glass` in SwiftUI.
  - `val isClear: Boolean get()` — Vero per [Clear].
  - `val isIdentity: Boolean get()` — Vero per [Identity]: il vetro non c'è.
  - `fun tint(color: Color?): FluidGlass` — Lo stesso vetro tinto di [color]; `null` o [Color.Unspecified] toglie la tinta. Su [Identity] non cambia niente.
  - `fun interactive(enabled: Boolean = true): FluidGlass` — Lo stesso vetro che reagisce al dito (o no, con [enabled] falso). Su [Identity] non cambia niente.
  - `companion object`
    - `val Regular: FluidGlass` — Il vetro di serie: smerigliato e adattivo.
    - `val Clear: FluidGlass` — Il vetro limpido: nessuna smerigliatura né adattività, scurimento del 35% sotto. Vedi [FluidGlass].
    - `val Identity: FluidGlass` — Nessun vetro: il contenuto com'è, nella stessa struttura.
- `@Composable fun Modifier.fluidGlass(glass: FluidGlass = FluidGlass.Regular, shape: Shape = FluidCapsuleShape, backdrop: GlassBackdropState = currentGlassBackdrop()): Modifier` — Posa un vetro dietro a questo elemento: `glassEffect(_:in:)` di SwiftUI.
- `@Composable fun Modifier.fluidGlassPanel(backdrop: GlassBackdropState, tint: GlassTint, shape: Shape, role: GlassRole = GlassRole.Floating, optics: GlassOptics = GlassDefaults.optics(role), large: Boolean? = null, exports: GlassBackdropState? = null, resampleIntervalMillis: Long = 0L): Modifier` — Un **pannello** di vetro che può essere un pezzo del piano (F3.2e): lo stesso vetro di [glassSurface], con il suo ruolo, la sua tinta e la sua ottica, per un vetro che porta del contenuto (una card, un dock, una barra di scrittura, la capsula di un campo).

## `engine-ui/ui/fluid/FluidGlassAdaptive.kt`

- `object FluidGlassPreference` — I punti notevoli del cursore del vetro: in iOS 27 è in Impostazioni › Aspetto › Liquid Glass, e ha sostituito la scelta a due della 26.1 (R5 M2). Il valore va da [Transparent] a [Default], il vetro dell'engine così com'è, al bit.
  - `const val Transparent: Float` — Trasparente. Dalla F10.8b (scelta di Alessio del 25/11/2026) i **tasti e i pezzi piccoli** non sfocano affatto, e le **superfici grandi** (menu, fogli) restano sfocate per la leggibilità, come i menu e i fogli di iOS 26.1 con «Trasparente»; in mezzo vale la regola della taglia dell'engine (64–320 dp di lato corto). Il velo scende come prima.
  - `const val Default: Float` — Di serie: il vetro del profilo, senza ritocchi. È anche la cima del cursore.
  - `const val Colored: Float` — **DEPRECATO: F10.8c: il vetro colorato non c'è più, e questo valore si legge Default** — L'estremo colorato di prima. **Dalla F10.8c non c'è più** e si legge [Default] (vedi [effective]): resta, con lo stesso valore, per le app che lo nominano o l'hanno salvato.
  - `fun fromClarity(clarity: Float): Float` — F10.8b · Il cursore della **chiarezza** del vetro dell'aspetto comune (Pampa Store, `appearance.glass`): da 0 «perfettamente trasparente» a 1 «più sfocato, come ora», **di serie 1**, come il cursore Liquid Glass di iOS 27 (Impostazioni › Aspetto, R5 M2). È questo stesso cursore misurato da 0 a 1: 0 → [Transparent], 1 → [Default], in linea.
  - `fun clarityOf(preference: Float): Float` — Il contrario di [fromClarity]: la chiarezza di un valore del cursore. 1 per [Default] e per un valore salvato oltre (la metà colorata di prima, vedi [effective]).
  - `fun effective(preference: Float): Float` — F10.8c · Il valore che il vetro usa davvero per [preference]: fra [Transparent] e [Default]. Sotto lo zero si legge trasparente; **oltre [Default] si legge [Default]**, cioè un valore salvato nella metà colorata di prima (fino a [Colored], e oltre) torna al vetro di serie, al bit; un valore non finito è [Default].
- `val LocalFluidGlassPreference: ProvidableCompositionLocal<Float>` — Il cursore del vetro per questo sottoalbero, da [FluidGlassPreference.Transparent] (0) a [FluidGlassPreference.Default] (0,5), che è anche il valore di serie. Dalla F10.8c non va oltre: un valore più alto (la metà colorata di prima) si legge di serie ([FluidGlassPreference.effective]).
- `class FluidGlassAppearance internal constructor(val isDark: Boolean, val darkness: Float, val backdropLuminance: Float, val isMeasured: Boolean)` — Ciò che un vetro del materiale nuovo sa di sé e dello sfondo che ha dietro, per chi disegna **sopra** di lui: da quale lato sta (chiaro o scuro), quanto è scurito, e quanto è luminoso lo sfondo.
- `val LocalFluidGlassAppearance: ProvidableCompositionLocal<FluidGlassAppearance?>` — L'aspetto del vetro su cui sta questo contenuto, o `null` fuori da un vetro adattivo. Lo fornisce il vetro (F2.3). `GlassDefaults.isDarkSurface()` lo legge per primo: dentro un vetro misurato il lato è quello del vetro.

## `engine-ui/ui/fluid/FluidGlassContainer.kt`

- `sealed class FluidGlassAnimation` — Come il contenitore anima i cambi dei suoi vetri. In SwiftUI è l'animazione della transazione (`withAnimation`); qui non si chiama niente: **il contenitore anima da solo** ogni cambio, e questo dice come.
  - `data object Default : FluidGlassAnimation()` — Di serie: la molla [FluidMotion.standard] stirata nei bilanci del piano, [FluidMotion.PlaneContextMillis] per un cambio di contesto, [FluidMotion.PlaneAppearMillis] per una comparsa e [FluidMotion.PlaneDisappearMillis] per una scomparsa. Un cambio a metà viaggio riparte da dove si trova, con la velocità che ha.
  - `data object None : FluidGlassAnimation()` — Nessun viaggio: i vetri vanno al posto nuovo nel fotogramma dopo il cambio, e il contenuto che si è spostato o cresciuto con loro (lo stesso fotogramma per vetro e contenuto). Anche con il movimento ridotto. Se arriva mentre un vetro viaggia, il viaggio finisce lì, al posto nuovo.
  - `class Custom(val spec: FiniteAnimationSpec<Float>) : FluidGlassAnimation()` — La spec dell'app per ogni lato e raggio di ogni vetro, col suo tempo (i bilanci di serie non contano). Una molla conserva la velocità di un viaggio interrotto; una curva a durata no, e alla ripartenza riparte da ferma.
- `object FluidGlassContainerDefaults` — I valori di serie di [FluidGlassContainer].
  - `val Spacing: Dp` — Fin dove due vetri dello stesso contenitore sono **un grappolo** (una cattura sola, un campo fuso in viaggio), fin dove un vetro nuovo **nasce dal vicino** invece di comparire per lente, e il raggio di fusione del loro collo.
- `class FluidGlassNamespace internal constructor()` — Lo spazio dei nomi delle identità dei vetri: `@Namespace` di SwiftUI. Due `id` uguali in due spazi diversi sono due vetri diversi; lo stesso `id` nello stesso spazio, prima e dopo un cambio, è **lo stesso vetro** che si sposta.
- `@Composable fun rememberFluidGlassNamespace(): FluidGlassNamespace` — Uno spazio dei nomi che vive quanto il punto della composizione che lo chiede.
- `enum class FluidGlassTransition` — Come un vetro che arriva o se ne va entra nel cambio: `glassEffectTransition` di SwiftUI.
- `fun Modifier.fluidGlassId(id: Any, namespace: FluidGlassNamespace): Modifier` — L'identità del vetro che **segue** questo modificatore nella stessa catena (`glassEffectID` di SwiftUI).
- `fun Modifier.fluidGlassUnion(id: Any, namespace: FluidGlassNamespace): Modifier` — Unisce il vetro che segue in **una sagoma sola** con gli altri della stessa unione (`glassEffectUnion` di SwiftUI): fusi a riposo in un campo, anche oltre lo `spacing` del contenitore (mai oltre otto pezzi). Stesso ordine di [fluidGlassId]: vale per il [fluidGlass] alla sua destra.
- `fun Modifier.fluidGlassTransition(transition: FluidGlassTransition): Modifier` — Come il vetro che segue compare e scompare (`glassEffectTransition` di SwiftUI). Stesso ordine di [fluidGlassId]: vale per il [fluidGlass] alla sua destra.
- `enum class FluidGlassContentAnchor` — Il punto del pezzo che porta il contenuto mentre il vetro cambia larghezza. Il centro conserva il volo del piano; un bordo tiene ferme le icone di una capsula che cresce attorno a loro, senza stirarle (R7: chi resta resta nitido). I bordi seguono la direzione del layout; in verticale resta il centro. Il rimbalzo del bordo resta nel contenuto.
- `fun Modifier.fluidGlassContentAnchor(anchor: FluidGlassContentAnchor): Modifier` — Ancora il contenuto al bordo del vetro che segue, invece che al suo centro. Stesso ordine di [fluidGlassId]; fuori da un [FluidGlassContainer] non cambia il disegno. Di serie [FluidGlassContentAnchor.Center].
- `@Composable fun FluidGlassContainer(modifier: Modifier = Modifier, spacing: Dp = FluidGlassContainerDefaults.Spacing, animation: FluidGlassAnimation = FluidGlassAnimation.Default, propagateMinConstraints: Boolean = false, content: @Composable () -> Unit)` — Il contenitore del piano flottante: `GlassEffectContainer(spacing:)` di SwiftUI.
- `@Composable fun FluidGlassNested(content: @Composable () -> Unit)` — Il contenuto di un pezzo che **ospita vetri annidati** (F3.2e), di solito un [fluidGlassPanel]:
- `object FluidGlassContainerDiagnostics` — Il contenitore visto da fuori, per le prove (il test del piano nel modulo della galleria, che non vede gli `internal` dell'engine) e per le sessioni sul dispositivo. Non è un'API per le app.
  - `val totalJourneys: Long get()` — I viaggi partiti (un cambio a riposo), in tutto il processo.
  - `val totalRetargets: Long get()` — I viaggi ripartiti a metà (uno stato cambiato mentre il vetro viaggiava).
  - `val totalArrivals: Long get()` — I viaggi arrivati: il conducente ha finito e il palco ha lasciato il vetro al riposo (f3-corpo). È la fine vera di un viaggio; [lastRest] non lo è più, perché il riposo d'arrivo si pubblica al secondo fotogramma del palco (giro di correzione di F3), molto prima dell'arrivo.
  - `val totalOrderWarnings: Long get()` — Quante volte un `fluidGlassId`, `fluidGlassUnion` o `fluidGlassTransition` è stato scritto **dopo** il suo vetro (o senza vetro alla sua destra): non ha effetto, e in debug il log lo dice.
  - `val liveContainers: Int get()` — I contenitori in scena adesso.
  - `val lastJourney: List<FluidGlassFlightRecord> get()` — Chi è andato dove nell'ultimo viaggio partito o ripartito: un record per vetro in volo.
  - `val lastRest: List<FluidGlassPieceRecord> get()` — I pezzi dell'ultimo riposo stabilito, con i raggi già risolti (le forme concentriche comprese).
  - `val lastContents: List<FluidGlassContentRecord> get()` — Che parte fa il contenuto di ogni vetro nell'ultimo viaggio partito o ripartito (F3.8a): chi nasce col suo vetro, chi si condensa, chi se ne va, chi resta.
  - `val lastRestLarge: Boolean get()` — Se, nell'ultimo riposo stabilito, il contenitore è un **grande** che scurisce in continuo invece di girare (F3.2e): lo dice il pannello che l'ha dichiarato ([fluidGlassPanel], `large` o il suo ruolo), altrimenti la taglia del grappolo più grande.
  - `val lastFinger: Float get()` — La **luce del dito** dell'ultimo viaggio partito o ripartito (F3.10): il bagliore da cui comincia il campo del vetro toccato, 0..1 (nelle unità della pressione). Zero se il viaggio non l'ha fatto partire un tocco su un vetro del contenitore: allora parte dal livello del carattere.
  - `val lastStart: FluidGlassStartRecord? get()` — Com'è partito l'ultimo viaggio da fermo (giro di correzione di F3): quanto ha aspettato dal momento in cui il contenitore ha saputo del cambio, e quanto lavoro ha fatto il fotogramma della partenza sul filo dell'interfaccia. Null finché nessun viaggio è partito. Per i log della galleria e le sessioni sul tablet.
  - `enum class FusionPreview(internal val fusion: PhysicsPlaneFusion)` — Le tre distanze di fusione della tavola di F3.3, da riprendere sul dispositivo (S3): quella scelta da Alessio è la media, di serie; le altre due servono a confrontarle dal vivo.
  - `var fusionPreview: FusionPreview? by mutableStateOf(null)` — La manopola delle prove: con un valore, **ogni** contenitore usa quella distanza di fusione al posto del suo `spacing` (e la «larga» fonde anche a riposo). Null, di serie: lo `spacing` di ogni contenitore. Uno stato: i contenitori in scena si ricompongono quando cambia. Non è un'API per le app (la galleria la legge dall'extra `plane.fusion`).
  - `fun resetForTesting()` — Azzera i contatori e i record, per i test.
- `class FluidGlassFlightRecord internal constructor(val fromId: Any?, val toId: Any?, val change: String, val bornFromId: Any?, val mergesIntoId: Any?)` — Un vetro in volo nell'ultimo viaggio: da quale `id` a quale (null per un vetro senza `id`), che cambio è (`Matched`, `BornFromNeighbour`, `MergeIntoNeighbour`, `Materialize`, `Dematerialize`, `Identity`) e, per chi nasce o confluisce, l'`id` del vicino.
- `class FluidGlassStartRecord internal constructor(val waitMs: Double, val workMs: Double)` — La partenza di un viaggio da fermo (giro di correzione di F3, A 8 e C R5): [waitMs] dal momento in cui il contenitore a riposo ha saputo del cambio (il pezzo nuovo si iscrive o si posa: la composizione e il layout del fotogramma del cambio) all'istante del fotogramma in cui il palco parte, che è sempre il fotogramma dopo (il pezzo nuovo è misurato lì);
- `class FluidGlassContentRecord internal constructor(val id: Any?, val role: String, val departed: Boolean)` — Il contenuto di un vetro nell'ultimo viaggio partito o ripartito: l'`id` del vetro (o null), la parte che il contenuto fa (`Rest`: resta nitido e viaggia col vetro; `Born`: nasce col vetro; `Lens`: si condensa nella comparsa per lente; `Recover`: torna nitido da dove si trovava; `Exit`: se ne va; `Hidden`: non si vede) e se è il contenuto di un vetro che non c'è più, disegnato dal contenitore finché non è sparito.
- `class FluidGlassPieceRecord internal constructor(val id: Any?, val bounds: Rect, val cornerRadii: FluidCornerRadii, val clusterSize: Int, val unionId: Any? = null, val exports: Boolean = false, val drawnTint: GlassTint? = null)` — Un vetro a riposo: il suo `id` (o null), il riquadro nel contenitore in pixel e i quattro raggi risolti.

## `engine-ui/ui/fluid/FluidGlassContentTransition.kt`

- `@Composable fun <S> FluidGlassContentTransition(targetState: S, modifier: Modifier = Modifier, key: (S) -> Any? = { it }, blurReference: Dp = GlassContentDefaults.BlurReference, contentColor: ((S) -> Color)? = null, contentAlignment: Alignment = Alignment.Center, content: @Composable (S) -> Unit)` — Il contenuto di un vetro che cambia, alla maniera di Apple (`blurReplace` dentro un vetro, R7 A.2): chi esce si sfoca, rimpicciolisce appena e svanisce; chi entra arriva sfocato e si fa nitido; i due si incrociano al massimo della sfocatura, e la tinta cambia lì dentro. Il contenuto con la **stessa [key]** nei due stati non si tocca: resta nitido anche se il vetro intorno corre (R7 A3, D2 di Alessio).

## `engine-ui/ui/fluid/FluidGlassModel.kt`

- `enum class FluidGlassModel` — Con quale materiale l'engine disegna il vetro.
- `val LocalFluidGlassModel` — Il materiale chiesto per il vetro di questo sottoalbero. Di serie [FluidGlassModel.Liquid].

## `engine-ui/ui/fluid/FluidGlassQuality.kt`

- `class FluidGlassQuality internal constructor(private val ceiling: () -> Float = { 1f })` — Quanta ottica il vetro riceve **adesso**, fra 0 e 1.
  - `val level: Float get()` — 1 = materiale pieno, [ScrollingFloor] = quanto ne resta durante uno scorrimento veloce.
  - `companion object`
    - `const val FullQualityVelocity: Float` — Sotto questo spostamento per fotogramma il materiale resta intero. Circa mezzo centimetro a 60 Hz: uno scorrimento lento, quello in cui si legge mentre si scorre.
    - `const val MinQualityVelocity: Float` — Sopra questo, il materiale è al minimo. Un lancio vero.
    - `const val ScrollingFloor: Float` — Quanto resta al minimo. Non zero: a zero il vetro *sparisce*, e una superficie che appare e scompare col gesto è molto più evidente di una che si assottiglia.
- `val LocalFluidGlassQuality` — Il livello di qualità in scope. Vale 1 fisso quando nessuno lo sta guidando, quindi una schermata che non aggancia niente si comporta esattamente come prima.
- `@Composable fun rememberFluidGlassQuality(ceiling: () -> Float = { 1f }): FluidGlassQuality`
- `@Composable fun currentGlassQuality(): Float` — Il valore che una superficie deve usare adesso.
- `fun fluidGlassQualityScrollConnection(quality: FluidGlassQuality): NestedScrollConnection` — Collega un contenitore scorrevole al livello di qualità.
- `object : NestedScrollConnection`

## `engine-ui/ui/fluid/GlassContrast.kt`

- `val LocalFluidSystemContrast: ProvidableCompositionLocal<Float>` — Il contrasto che il sistema chiede, da 0 (standard) a 1 (alto): Android 14+, Impostazioni > Accessibilita' > Contrasto del colore, `UiModeManager.getContrast()` (3.0).
- `object FluidSystemContrast` — I livelli del contrasto di sistema e cosa ne fa il vetro. Pura e senza Compose attorno: una pellicola troppo timida non leggibile, o troppo piena e opaca, si vede solo in mano, quindi ogni numero qui sotto e' fissato in `FluidSystemContrastTest`.
  - `const val Standard: Float` — Il contrasto di serie del sistema.
  - `const val Medium: Float` — "Medio": la voce di mezzo delle impostazioni di Android 14+.
  - `const val High: Float` — "Alto".
  - `fun level(raw: Float): Float` — Il valore del sistema, ripulito: Android lo da' come float e le impostazioni offrono standard, medio e alto (0; 0,5; 1). Un valore sotto lo zero non e' un'offerta e qui vale "standard", uno sopra l'uno vale "alto", e uno non finito non e' un livello.
  - `fun veil(contrast: Float): Float` — Quanta della strada verso il pieno percorre una pellicola a questo [contrast]: 0 a standard, [MediumVeil] a medio, [HighVeil] ad alto, lineare fra uno e l'altro.
- `fun GlassTint.forContrast(contrast: Float): GlassTint` — La stessa pellicola, resa piu' densa per il [contrast] del sistema (vedi [LocalFluidSystemContrast]).
- `@Composable fun GlassTint.forSystemContrast(): GlassTint` — [forContrast] col contrasto del sistema che il tema ha messo in [LocalFluidSystemContrast].
- `@Composable fun rememberFluidSystemContrast(): Float` — Il contrasto del sistema adesso, e **si aggiorna quando l'utente lo cambia** mentre l'app e' aperta. 0 prima di Android 14, dove le impostazioni non esistono (e `UiModeManager.getContrast()` neppure), e dove il servizio manca.
- `val LocalFluidReduceTransparency: ProvidableCompositionLocal<Boolean>` — Consente a un'app di ridurre la trasparenza del materiale Liquid anche quando il dispositivo non offre un controllo equivalente. Il tema aggiunge la preferenza di sistema al valore fornito dall'app. Di serie l'app non aggiunge alcuna preferenza.

## `engine-ui/ui/fluid/GlassMaterial.kt`

- `class GlassBackdropState internal constructor(internal val backdrop: Backdrop, internal val layerBackdrop: LayerBackdrop?, val blurRadius: Dp)` — Fluid Glass — a real refracting material, not a translucent rectangle.
  - `val supported: Boolean get()` — False below API 31: no `RenderEffect`, so surfaces fall back to a solid material.
  - `val refractionSupported: Boolean get()` — False below API 33: blur and vibrancy still work, the lens does not.
  - `fun notifyChanged()` — Tells the glass sampling this source that what it shows has changed although the source did not redraw.
- `val DefaultGlassBlurRadius: Dp` — Reference depth of the frosting. **Two and a half dp.**
- `@Composable fun rememberGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState`
- `@Composable fun rememberCombinedGlassBackdrop(behind: GlassBackdropState, inFront: GlassBackdropState, blurRadius: Dp = inFront.blurRadius): GlassBackdropState` — Stacks two backdrops into one, back to front.
- `fun Modifier.glassBackdropSource(state: GlassBackdropState, frozen: () -> Boolean = { false }): Modifier` — Marks the content that glass surfaces sample.
- `val LocalGlassBackdrop` — The pane of glass the controls in this subtree should refract.
- `fun Modifier.glassContainer(material: GlassBackdropState): Modifier` — Il vetro dentro questo nodo sta su [material]: lo campiona sopra a quello che gli e' stato dato, come se questo nodo fosse figlio della superficie che lo disegna. Dalla 2.13.0.
- `fun Modifier.glassMaterialSource(state: GlassBackdropState): Modifier` — Registra in [state] quello che disegnano i modificatori che vengono dopo questo — il materiale di un pannello che l'app disegna da se' — cosi' il vetro dell'engine che ci sta sopra lo puo' campionare (vedi [glassContainer]). Dalla 2.13.0.
- `@Composable fun rememberEmptyGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState` — A backdrop with nothing in it.
- `@Composable fun currentGlassBackdrop(): GlassBackdropState` — The backdrop a control should use unless its caller names a different one.
- `val LocalFluidCanvasBackdrop` — The screen's *ambient* recording: the coloured wash and motif painted under the page, and nothing else.
- `val LocalFluidCanvasIsGlass` — Whether the canvas in scope is itself a pane of glass rather than a page's ambient wash.
- `val LocalFluidSurfaceSide` — Which side the app is painting on, when the palette cannot be asked.
- `@Composable fun currentCanvasBackdrop(): GlassBackdropState?` — The ambient recording in scope, or null when the screen has no canvas.
- `enum class GlassTintMode` — Cosa vuol dire [GlassTint.overlay] per il materiale nuovo ([FluidGlassModel.Liquid]). Il vetro di prima ([FluidGlassModel.Classic]) non lo legge: li' `overlay` e' sempre la pellicola dipinta sopra la lente.
- `data class GlassTint(val overlay: Color, val fallback: Color, val hairline: Color, val mode: GlassTintMode = GlassTintMode.Fixed, internal val modeOverlay: Color = overlay, internal val darkCeiling: Float = Float.NaN)` — Colour treatment of a glass surface.
- `enum class GlassRole` — The optical job a pane performs; colour remains the separate concern of [GlassTint].
- `data class GlassOptics(val blurScale: Float, val refractionHeight: Dp, val refractionAmount: Dp, val depthEffect: Boolean, val dispersion: Boolean, val vibrancy: Float, val highlightWidth: Dp, val highlightAlpha: Float, val highlightAngle: Float, val innerShadowRadius: Dp, val innerShadowAlpha: Float, val shadowRadius: Dp, val shadowAlpha: Float, val pressedDepthBoost: Float, val edgeFloor: Float = 0f, val backdropResolution: Float = 1f, val sizeResponse: Float = 1f)` — How thick the glass is, physically.
- `object GlassDefaults`
  - `fun buttonOptics(): GlassOptics` — L'ottica di un pulsante di vetro: vedi [ButtonOptics].
  - `fun stackedContentOptics(): GlassOptics` — The optics a content surface takes when its canvas is another pane. See [LocalFluidCanvasIsGlass].
  - `fun panelOptics(compact: Boolean = false, overSharpPage: Boolean = false): GlassOptics` — L'ottica del pannello flottante: vedi [PanelOptics].
  - `@Composable fun panelTint(colour: Color? = null): GlassTint` — La pellicola del pannello flottante: quella di un modale, o quella tinta col [colour] della cosa da cui il pannello e' nato (vedi [tintedModalTint]). Una sola, per tutti i pannelli di [panelOptics].
  - `@Composable fun stackedContentTint(): GlassTint` — The tint for the same case: denser than a page card's, because it is competing with a pane that already carries a film of its own, and text has to stay readable on top of both.
  - `fun optics(role: GlassRole): GlassOptics` — Stable, allocation-free presets. Custom callers can use `copy` and are sanitized at use.
  - `@Composable fun barTint(): GlassTint` — Bars.
  - `@Composable fun darkBarTint(): GlassTint` — The bar, when the page beneath it is a picture rather than a colour.
  - `@Composable fun darkFloatingTint(): GlassTint` — Floating navigation, when what it floats over is a picture.
  - `@Composable fun barTintOnPhoto(): GlassTint` — The bar over a picture, on the side the page is actually on.
  - `@Composable fun floatingTintOnPhoto(): GlassTint` — [barTintOnPhoto] for the floating family. Use the pair together or neither.
  - `@Composable fun lightBarTint(): GlassTint` — The light twin of [darkBarTint]: a white scrim, at the weight a photograph asks for.
  - `@Composable fun lightFloatingTint(): GlassTint` — The light twin of [darkFloatingTint]. One step denser, for that one's reason.
  - `@Composable fun floatingTint(): GlassTint` — Floating navigation: a little denser, because it travels over arbitrary content.
  - `@Composable fun controlTint(): GlassTint` — Controls.
  - `@Composable fun selectionTint(): GlassTint` — The capsule that marks a selection inside a bar.
  - `@Composable fun contentTint(): GlassTint` — Cards and grouped lists.
  - `@Composable fun modalTint(): GlassTint` — Sheets and alerts.
  - `@Composable fun tintedModalTint(colour: Color): GlassTint` — Il materiale di una finestra che porta il colore della cosa da cui e' nata.
  - `@Composable fun isDarkSurface(): Boolean` — Whether the app is painting a dark surface.
- `enum class GlassEdge` — Which edge, if any, carries the hairline that separates glass from content.
- `enum class GlassFalloff` — How the material ends.
- `@Composable fun Modifier.glassSurface(state: GlassBackdropState, tint: GlassTint, shape: Shape = RectangleShape, edge: GlassEdge = GlassEdge.None, falloff: GlassFalloff = GlassFalloff.Uniform, intensity: () -> Float = { 1f }, role: GlassRole = if (falloff == GlassFalloff.FadeDown) GlassRole…, optics: GlassOptics = GlassDefaults.optics(role), opticalDepth: () -> Float = { 1f }, pressed: () -> Float = { 0f }, exports: GlassBackdropState? = null, layerBlock: (GraphicsLayerScope.() -> Unit)? = null, sampleOnce: Boolean = role == GlassRole.Content, resampleIntervalMillis: Long = 0L, frozen: () -> Boolean = { false }, heldWhileMoving: () -> Boolean = { false }, nestInContainer: Boolean = true): Modifier` — Turns a composable into a pane of glass over [state]'s content.

## `engine-ui/ui/glass/backdrop/Backdrop.kt`

- `interface Backdrop`
  - `val isCoordinatesDependent: Boolean`
  - `val layerSources: List<Any> get()` — Fluid Engine addition: the recordings this backdrop replays, if it is made of any.
  - `fun isReadyToSample(): Boolean` — Fluid Engine addition: whether this backdrop, **right now**, has something to draw.
  - `val stackedBackdrops: List<Backdrop> get()` — Fluid Engine addition (2.13.0): the backdrops this one draws, back to front, when it is a stack of them — or itself, when it is not.
  - `fun forEachPart(block: (Backdrop) -> Unit)` — Fluid Engine addition (2.13.0): calls [block] on this backdrop and on every backdrop it is built from, at any depth.
  - `fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)? = null)`

## `engine-ui/ui/glass/backdrop/BackdropEffectScope.kt`

- `sealed interface BackdropEffectScope : Density, RuntimeShaderCache`
  - `val size: Size`
  - `val layoutDirection: LayoutDirection`
  - `val shape: Shape`
  - `var padding: Float`
  - `var renderEffect: RenderEffect?`

## `engine-ui/ui/glass/backdrop/DrawBackdropModifier.kt`

- `fun Modifier.drawPlainBackdrop(backdrop: Backdrop, shape: () -> Shape, effects: BackdropEffectScope.() -> Unit, layerBlock: (GraphicsLayerScope.() -> Unit)? = null, exportedBackdrop: LayerBackdrop? = null, onDrawBehind: (DrawScope.() -> Unit)? = null, onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop, onDrawSurface: (DrawScope.() -> Unit)? = null, onDrawFront: (DrawScope.() -> Unit)? = null, backdropScale: Float = 1f, nestInContainer: Boolean = true): Modifier`
- `fun Modifier.drawBackdrop(backdrop: Backdrop, shape: () -> Shape, effects: BackdropEffectScope.() -> Unit, highlight: (() -> Highlight?)? = DefaultHighlight, shadow: (() -> Shadow?)? = DefaultShadow, innerShadow: (() -> InnerShadow?)? = null, layerBlock: (GraphicsLayerScope.() -> Unit)? = null, exportedBackdrop: LayerBackdrop? = null, onDrawBehind: (DrawScope.() -> Unit)? = null, onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop, onDrawSurface: (DrawScope.() -> Unit)? = null, onDrawFront: (DrawScope.() -> Unit)? = null, backdropScale: Float = 1f, sampleOnce: Boolean = false, resampleIntervalMillis: Long = 0L, backdropScaleFactor: () -> Float = OneScale, frozen: () -> Boolean = NeverFrozen, heldWhileMoving: () -> Boolean = NeverFrozen, nestInContainer: Boolean = true, onDrawLight: (DrawScope.() -> Unit)? = null, underlay: Modifier = Modifier): Modifier`

## `engine-ui/ui/glass/backdrop/FluidGlassDiagnostics.kt`

- `object FluidGlassDiagnostics` — Counts how often glass re-captures what is behind it.
  - `var enabled: Boolean`
  - `val totalCaptures: Long get()` — Captures taken since [enabled] was first switched on.
  - `val totalExports: Long get()` — Materiali pubblicati (registrati) da quando [enabled] e' acceso: un pannello che registra cio' che disegna dietro al contenuto perche' del vetro dentro di lui lo campioni. Dalla 2.13.0 si registra solo se qualcuno lo guarda, quindi su una schermata senza vetro su vetro questo numero non si muove, e a schermo fermo non si muove mai.
  - `val shaderSourceNames: List<String> get()` — I nomi di tutte le sorgenti AGSL dell'engine, nell'ordine in cui [compileAllShaders] le prova. Non dipende dal dispositivo: anche sotto Android 13 l'elenco è intero, vuoti sono solo i verdetti.
  - `val shaderVerdicts: Map<String, Throwable?> get()` — I verdetti che il vetro ha già chiesto in questo processo: nome della sorgente → `null` se il compilatore del dispositivo l'ha accettata, o il suo errore (il messaggio è quello del compilatore, con riga e motivo). Una sorgente che nessuno ha ancora usato non compare: la sonda compila solo quando serve. Sotto Android 13 la sonda non compila niente e la mappa resta vuota.
  - `fun compileAllShaders(): Map<String, Throwable?>` — Compila adesso ogni sorgente AGSL dell'engine che non sia già stata provata, e restituisce tutti i verdetti: nome della sorgente → `null` o l'errore del compilatore. Ogni rifiuto scrive anche una riga di log a livello errore (tag `FluidGlass`).
  - `val liveSurfaces: Map<FluidGlassModel, Int>` — Le superfici di vetro vive, per il materiale che disegnano: una per nodo di `drawBackdrop` attaccato. `Liquid` è una superficie che disegna la luce del materiale nuovo, qualunque strada l'abbia scelta (anche Fluid-physics, la goccia e la pillola della tab bar, quando sono sul materiale nuovo); tutte le altre sono il vetro di prima, comprese quelle di chi chiama `drawBackdrop` a mano.
  - `val liveScrollEdges: Int get()` — Le strisce del bordo dello scorrimento vive (`FluidScrollEdge`, F2.5): una per striscia attaccata, contata con la regola dei nodi (se la diagnostica è accesa quando si attacca). Non sono vetro (nessuna lastra, nessuna luce): per questo stanno qui e non in [liveSurfaces]. Il loro nodo e i suoi layer restano in [liveNodes] e [liveLayers].
  - `val liveModelChoices: Map<FluidGlassModelReason, Int> get()` — Le risposte vive della risoluzione del modello (`rememberFluidGlassModel`), per motivo: una per chiamata in composizione, finché la chiamata resta in composizione.
  - `val liveNodes: Map<FluidGlassNodeKind, Int> get()` — I nodi del vetro vivi che tengono un layer, per tipo. Contati dove nascono e dove si staccano.
  - `val liveLayers: Map<FluidGlassLayerKind, Int> get()` — I layer del vetro vivi, per tipo: creati e rilasciati dai nodi di [liveNodes]. Stesso limite.
  - `var warmUpEnabled: Boolean` — Il riscaldamento automatico dei programmi del materiale nuovo: dopo il primo fotogramma, a finestra ferma, un disegno fuori schermo per programma (vedi `GlassWarmUp`). Acceso di serie.
  - `val warmUp: List<FluidGlassWarmUpStatus> get()` — A che punto è il riscaldamento, famiglia per famiglia. Si legge sempre, anche con [enabled] spento.
  - `fun warmUpNow(): List<FluidGlassWarmUpStatus>` — Scalda **adesso**, sul filo principale e aspettando ogni fotogramma fuori schermo, tutte le famiglie che il dispositivo può avere, anche con [warmUpEnabled] spento; restituisce lo stato finale.
  - `val simulatedApiLevel: Int? get()` — Il livello di API che il vetro sta simulando, o `null`. Fuori da un'app `debuggable` è sempre `null`.
  - `fun simulateApiLevel(context: Context, level: Int?): Boolean` — Fa rispondere il vetro dell'engine come su un Android più vecchio: `32` (Android 12: `RenderEffect` sì, AGSL no), `30` (Android 11: né l'uno né l'altro), `null` per tornare al vero.
  - `val liquidProfileTuning: Map<String, String> get()` — Le manopole del materiale nuovo in vigore, coi nomi e i valori normalizzati: vuota vuol dire il profilo di serie. Fuori da un'app `debuggable` è sempre vuota.
  - `val liquidProfileKnobs: Map<String, List<String>>` — Le manopole che [tuneLiquidProfile] riconosce, ognuna con i valori che accetta: le varianti delle tavole di gusto per nome (`edge` → `ios27`, `ios26`, `today`…), i numeri come intervallo (`blur` → `0..64`).
  - `fun tuneLiquidProfile(context: Context, knobs: Map<String, String>?): Boolean` — Cambia i numeri del materiale nuovo per le lastre che nascono **da adesso**: le varianti delle tavole di gusto (bordo, velo, ombra, smerigliatura, lente, grandi, tema scuro) e i numeri in mezzo, per fotografarle sul pannello vero senza ricompilare (piano «iOS in Android», F1A.9). Ogni chiamata sostituisce tutte le manopole di prima; `null` o vuota torna al profilo di serie.
  - `fun forceShaderVerdictsForTesting(accept: Boolean)` — Solo per i test della galleria (Robolectric): ogni sorgente AGSL dell'engine risulta compilata ([accept] vero) o rifiutata, senza chiedere niente al compilatore. Senza questo gancio, sul livello 35 il modello dipenderebbe da ciò che Robolectric fa di un `RuntimeShader`: in modo grafico `NATIVE` lo compila (F1A.7 l'ha visto), negli altri modi no.
  - `fun resetForTesting()` — Solo per i test: la sonda vera senza verdetti, nessun livello simulato, il riscaldamento acceso e mai partito, il profilo di serie. I conteggi vivi non si azzerano: scendono da soli quando le superfici se ne vanno.
  - `fun enableStateDumpForTesting()` — Solo per i test (4.0, F8.10): accende la fotografia a riposo dei vetri come in un'app debuggable sul dispositivo, senza il ricevitore del broadcast. Va chiamata prima di comporre: le sonde si iscrivono in composizione e in disegno. Spenta da [resetForTesting].
  - `fun stateDumpForTesting(action: String? = null): List<String>` — Solo per i test (4.0, F8.10): una prova sullo stato fermo ([action]: `vetri`, `vetri-cattura`, `tasto`, `foglio`) o, con null, la fotografia dello stato dei vetri vivi. Restituisce le righe che scrive nel log `FluidGlassState`. Serve [enableStateDumpForTesting].
- `enum class FluidGlassModelReason(val model: FluidGlassModel)` — Perché una superficie ha il materiale che ha: i motivi della risoluzione del modello (`resolveFluidGlassModel` in `ui/fluid/FluidGlassModel.kt`), nell'ordine in cui si chiedono. Il primo che vale decide.
- `enum class FluidGlassNodeKind` — I nodi del vetro che tengono un layer.
- `enum class FluidGlassLayerKind` — I layer del vetro.
- `enum class FluidGlassWarmUpState` — Lo stato del riscaldamento di una famiglia di programmi.
- `class FluidGlassWarmUpStatus internal constructor(val family: String, val programs: List<String>, val state: FluidGlassWarmUpState, val warmed: List<String>, val detail: String?, val mainThreadMillis: Float)` — Il riscaldamento di una famiglia di programmi, come lo legge chi lo mostra.

## `engine-ui/ui/glass/backdrop/Platform.kt`

- `fun isRenderEffectSupported(): Boolean`
- `fun isRuntimeShaderSupported(): Boolean`

## `engine-ui/ui/glass/backdrop/RuntimeShader.kt`

- `fun RuntimeShader(@Language("AGSL") shaderString: String): RuntimeShader`
- `fun RuntimeShader.asComposeShader(): Shader`
- `fun RuntimeShader.asAndroidRuntimeShader(): android.graphics.RuntimeShader`
- `interface RuntimeShader` — The subset of a platform runtime shader the effects need.
  - `fun setFloatUniform(name: String, value: Float)`
  - `fun setFloatUniform(name: String, value1: Float, value2: Float)`
  - `fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)`
  - `fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)`
  - `fun setFloatUniform(name: String, values: FloatArray)`
  - `fun setIntUniform(name: String, value: Int)`
  - `fun setIntUniform(name: String, value1: Int, value2: Int)`
  - `fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)`
  - `fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)`
  - `fun setIntUniform(name: String, values: IntArray)`
  - `fun setColorUniform(name: String, color: Color)`

## `engine-ui/ui/glass/backdrop/RuntimeShaderCache.kt`

- `sealed interface RuntimeShaderCache`
  - `fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader`

## `engine-ui/ui/glass/backdrop/backdrops/Backdrop.kt`

- `@Composable fun rememberBackdrop(backdrop: Backdrop, onDraw: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit): Backdrop`

## `engine-ui/ui/glass/backdrop/backdrops/CanvasBackdrop.kt`

- `@Composable fun rememberCanvasBackdrop(onDraw: DrawScope.() -> Unit): Backdrop`

## `engine-ui/ui/glass/backdrop/backdrops/CombinedBackdrop.kt`

- `@Composable fun rememberCombinedBackdrop(backdrop1: Backdrop, backdrop2: Backdrop): Backdrop`
- `@Composable fun rememberCombinedBackdrop(backdrop1: Backdrop, backdrop2: Backdrop, backdrop3: Backdrop): Backdrop`
- `@Composable fun rememberCombinedBackdrop(vararg backdrops: Backdrop): Backdrop`

## `engine-ui/ui/glass/backdrop/backdrops/EmptyBackdrop.kt`

- `fun emptyBackdrop(): Backdrop`

## `engine-ui/ui/glass/backdrop/backdrops/LayerBackdrop.kt`

- `@Composable fun rememberLayerBackdrop(graphicsLayer: GraphicsLayer = rememberGraphicsLayer(), onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw): LayerBackdrop`
- `class LayerBackdrop internal constructor(val graphicsLayer: GraphicsLayer, internal val onDraw: ContentDrawScope.() -> Unit) : Backdrop`

## `engine-ui/ui/glass/backdrop/backdrops/LayerBackdropModifier.kt`

- `fun Modifier.layerBackdrop(backdrop: LayerBackdrop, frozen: () -> Boolean = { false }): Modifier`

## `engine-ui/ui/glass/backdrop/effects/Blur.kt`

- `fun BackdropEffectScope.blur(@FloatRange(from = 0.0) radius: Float, edgeTreatment: TileMode = TileMode.Clamp)`

## `engine-ui/ui/glass/backdrop/effects/ColorFilter.kt`

- `fun BackdropEffectScope.colorFilter(colorFilter: ColorFilter)`
- `fun BackdropEffectScope.opacity(@FloatRange(from = 0.0, to = 1.0) alpha: Float)`
- `fun BackdropEffectScope.colorControls(brightness: Float = 0f, contrast: Float = 1f, saturation: Float = 1f)`
- `fun BackdropEffectScope.vibrancy()`

## `engine-ui/ui/glass/backdrop/effects/Lens.kt`

- `fun BackdropEffectScope.lens(@FloatRange(from = 0.0) refractionHeight: Float, @FloatRange(from = 0.0) refractionAmount: Float, depthEffect: Boolean = false, chromaticAberration: Boolean = false)`

## `engine-ui/ui/glass/backdrop/effects/RenderEffect.kt`

- `fun BackdropEffectScope.effect(effect: RenderEffect)`
- `fun BackdropEffectScope.runtimeShaderEffect(key: String, @Language("AGSL") shaderString: String, uniformShaderName: String, block: RuntimeShader.() -> Unit)`

## `engine-ui/ui/glass/backdrop/highlight/Highlight.kt`

- `data class Highlight(val width: Dp = 0.5f.dp, val blurRadius: Dp = width / 2f, @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f, val style: HighlightStyle = HighlightStyle.Default)`
  - `companion object`
    - `val Default: Highlight`
    - `val Ambient: Highlight`
    - `val Plain: Highlight`

## `engine-ui/ui/glass/backdrop/highlight/HighlightStyle.kt`

- `interface HighlightStyle`
  - `val color: Color`
  - `val blendMode: BlendMode`
  - `fun DrawScope.createShader(shape: Shape, size: Size, density: Density, runtimeShaderCache: RuntimeShaderCache): RuntimeShader?`
  - `data class Plain(override val color: Color = Color.White.copy(alpha = 0.38f), override val blendMode: BlendMode = BlendMode.Plus) : HighlightStyle`
  - `data class Default(override val color: Color = Color.White.copy(alpha = 0.5f), override val blendMode: BlendMode = BlendMode.Plus, val angle: Float = 45f, @param:FloatRange(from = 0.0) val falloff: Float = 1f) : HighlightStyle`
  - `data class Ambient(@param:FloatRange(from = 0.0, to = 1.0) val intensity: Float = 0.38f) : HighlightStyle`
  - `companion object`
    - `val Default: Default`
    - `val Ambient: Ambient`
    - `val Plain: Plain`

## `engine-ui/ui/glass/backdrop/shadow/InnerShadow.kt`

- `data class InnerShadow(val radius: Dp = 24f.dp, val offset: DpOffset = DpOffset(0f.dp, radius), val color: Color = Color.Black.copy(alpha = 0.15f), @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f, val blendMode: BlendMode = DrawScope.DefaultBlendMode)`
  - `companion object`
    - `val Default: InnerShadow`
- `fun lerp(start: InnerShadow, stop: InnerShadow, fraction: Float): InnerShadow`

## `engine-ui/ui/glass/backdrop/shadow/Shadow.kt`

- `data class Shadow(val radius: Dp = 24f.dp, val offset: DpOffset = DpOffset(0f.dp, radius / 6f), val color: Color = Color.Black.copy(alpha = 0.1f), @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f, val blendMode: BlendMode = DrawScope.DefaultBlendMode)`
  - `companion object`
    - `val Default: Shadow`

## `engine-ui/ui/glass/field/GlassLightEnvironment.kt`

- `val LocalFluidGlassMotionLight: ProvidableCompositionLocal<Boolean>` — Consente al riflesso del vetro Liquid di seguire l'inclinazione. Di serie è spento, così il tema non apre i servizi dei sensori. L'app può fornirlo attorno a `FluidTheme` e conservare la scelta.

## `engine-ui/ui/glass/field/GlassProbe.kt`

- `class FluidGlassProbeReading internal constructor(val luminance: Float, val deviation: Float, val gradient: Float, val red: Float, val green: Float, val blue: Float, val coverage: Float, val dark: Boolean, val fresh: Boolean, val uptimeMillis: Long)` — Una lettura della sonda, come la vede una prova sul dispositivo. Non è un'API per le app.
- `object FluidGlassProbeDiagnostics` — La sonda dello sfondo vista da fuori, per le prove strumentate (`GlassProbeInstrumentedTest` nel modulo della galleria, che non vede gli `internal` dell'engine). Non è un'API per le app: nessun componente la usa, e la sonda vera entra nei vetri con F2.2.
  - `val totalSourceRedraws: Long get()` — Quante volte il contenuto di una sorgente di vetro si è ridisegnato e l'ha detto alle sonde (F3.10, il nodo di `glassSourceRedraws` dentro il layer di `glassBackdropSource`): un fondo che cambia solo in disegno.
  - `var readerForTesting: (() -> Color)?` — Un **lettore finto**, per i test Robolectric (F3.10), dove il lettore vero non consegna mai l'immagine: con un valore, ogni lettura che una sonda chiede torna subito (al messaggio dopo, sul filo principale) col colore medio che questa funzione dice di vedere sotto il vetro in quel momento, uniforme. Le letture si contano come quelle vere ([totalRequests], [totalReadings]). Null (di serie): il lettore vero.
  - `val liveProbes: Int get()` — Le sonde attaccate adesso, in tutto il processo (F3.2): un nodo di sonda in scena, letto o no. Un `FluidGlassContainer` ne ha **una** per tutti i suoi pezzi; ogni vetro radice fuori da un contenitore la sua.
  - `fun wakes(): Map<String, Long>` — Perché le sonde si sono svegliate, per motivo (F2.3): una revisione di una sorgente, un riposizionamento, una lettura osservata cambiata, il timer (cadenza o scadenza del filtro), e le letture partite (con o senza miniatura nuova). Su una pagina ferma, dopo la prima misura, non deve crescere niente.
  - `val totalSlotsRefused: Long get()` — Le sonde rimaste senza casella (o spinte fuori da una più importante).
  - `val peakSlots: Int get()` — Il massimo di caselle occupate insieme in un lettore di finestra, da [resetPeakSlots] (F2.3): quante sonde radice ha la pagina più carica che si è aperta intanto. Il tetto è 64.
  - `fun resetPeakSlots()` — Riparte il conto di [peakSlots].
  - `fun resetForTesting()` — Azzera i contatori, per i test.
  - `val totalRequests: Long get()` — I fotogrammi chiesti ai lettori in tutto il processo (una richiesta può leggere più sonde).
  - `val totalReadings: Long get()` — Le letture consegnate alle sonde.
  - `val totalFailures: Long get()` — Le letture fallite (fotogramma rifiutato, lettore che non risponde).
  - `val liveReaders: Int` — I lettori di finestra aperti adesso.
  - `val isAvailable: Boolean get()` — Se la sonda può esistere su questo dispositivo (Android 13 e oltre).
  - `fun probe(backdrop: Backdrop, onReading: (FluidGlassProbeReading) -> Unit): Modifier` — Una sonda su [backdrop], con il proprio filtro (lato iniziale chiaro): [onReading] riceve, sul filo principale, ogni lettura (`fresh` vero) e ogni giro che il filtro fa alla sua scadenza (`fresh` falso). Sotto Android 13 non aggiunge niente.

## `engine-ui/ui/glass/interaction/GlassInteraction.kt`

- `suspend fun PointerInputScope.inspectDragGestures(onDragStart: (down: PointerInputChange) -> Unit = {}, onDragEnd: (change: PointerInputChange) -> Unit = {}, onDragCancel: () -> Unit = {}, onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit)` — Drag recognition without a slop threshold.
- `class GlassDragAnimation(private val animationScope: CoroutineScope, val initialValue: Float, val valueRange: ClosedRange<Float>, val visibilityThreshold: Float, val initialScale: Float, val pressedScale: Float, val onDragStarted: GlassDragAnimation.(position: Offset) -> Unit = {}, val onDragStopped: GlassDragAnimation.() -> Unit = {}, val onDrag: GlassDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit = { _, _ -> }, val velocityDampingRatio: Float = 0.5f)` — The physics behind a control that can be pushed around.
  - `val value: Float get()`
  - `val progress: Float`
  - `val targetValue: Float get()`
  - `val pressProgress: Float get()`
  - `val scaleX: Float get()`
  - `val scaleY: Float get()`
  - `val velocity: Float get()`
  - `val isAnimating: Boolean` — True while any of the springs is moving: the thumb following a drag, the squash, the press. The window in which the glass this animation drives is genuinely live. Since 2.10.0.
  - `val modifier: Modifier`
  - `fun press()`
  - `fun release()`
  - `fun updateValue(value: Float)`
  - `fun animateToValue(value: Float)`
  - `fun snapToValue(value: Float)`
- `class GlassTouchHighlight(val animationScope: CoroutineScope, val strength: () -> Float = { 1f }, val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }, val onDarkSurface: Boolean = true, val onGlass: Boolean = false, val button: Boolean = false, val progress: (() -> Float)? = null, val touch: (() -> Offset)? = null)` — The bright spot that follows a finger across a pane of glass.
  - `val pressProgress: Float get()`
  - `val offset: Offset get()`
  - `val isAnimating: Boolean` — True while the highlight is on screen or moving: pressed, following the finger, or settling back after a release. The window in which the glass under it is genuinely live. Since 2.10.0.
  - `val modifier: Modifier`
  - `val gestureModifier: Modifier`

## `engine-ui/ui/glass/quality/android/FluidGlassResources.kt`

- `class FluidGlassResources private constructor(private val app: Application)` — Limiti condivisi dai moduli Fluid nello stesso processo. Accessibile anche prima del primo Composable.
  - `val uiVisible: StateFlow<Boolean>` — Il trim UI_HIDDEN sospende i lettori fino al prossimo ingresso in primo piano.
  - `val thumbnailParallelism: Int @Synchronized get()` — Posti condivisi dalle miniature secondo il profilo effettivo del processo.
  - `val viewerHardwarePages: Boolean get()` — F8.18b · Se le pagine dei documenti vanno nella memoria grafica, secondo la fascia del processo adesso: la regola è [viewerHardwarePagesFor], con la riga di memoria, il livello ottico più basso fra le finestre in vista e (F7.26) la classe della GPU. Cambia con la fascia (una pressione, una termica severa la accendono); il PDF la legge a ogni pagina resa, senza lock.
  - `val budget` — Un solo registro per le catture del vetro e i decoder, comprese finestre simultanee.
  - `val prefetchAllowed: StateFlow<Boolean>` — Controllare il valore prima di accodare. I produttori in vita possono osservare anche lo stop.
  - `fun observeTrim(onTrim: (Int) -> Unit): Closeable` — Registrarsi mentre il modulo possiede cache o lavoro. Chiudere sul main thread alla disattivazione.
  - `fun refreshBeforeAllocation()` — Ricontrolla disponibilità e heap prima di una richiesta grande se l'ultima lettura è vecchia. Fuori dal draw.
  - `fun visibleBudgetDenied(window: Window?, denied: Boolean)` — Segnale del produttore visibile. Un rifiuto restringe le quote prima del tentativo successivo.
  - `companion object`
    - `fun current(): FluidGlassResources` — Registro inizializzato da Startup o da get(context), anche per gli adattatori che ricevono solo un File.
    - `fun get(context: Context): FluidGlassResources` — Inizializzazione sincrona, idempotente anche nei processi secondari senza AndroidX Startup.
