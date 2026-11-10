<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/vetro.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Vetro

_Generato sull'engine 3.1.3 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

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

## `engine-ui/ui/fluid/GlassMaterial.kt`

- `class GlassBackdropState internal constructor(internal val backdrop: Backdrop, internal val layerBackdrop: LayerBackdrop?, val blurRadius: Dp)` — Fluid Glass — a real refracting material, not a translucent rectangle.
  - `val supported: Boolean get()` — False below API 31: no `RenderEffect`, so surfaces fall back to a solid material.
  - `val refractionSupported: Boolean get()` — False below API 33: blur and vibrancy still work, the lens does not.
- `val DefaultGlassBlurRadius: Dp` — Reference depth of the frosting. **Two and a half dp.**
- `@Composable fun rememberGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState`
- `@Composable fun rememberCombinedGlassBackdrop(behind: GlassBackdropState, inFront: GlassBackdropState, blurRadius: Dp = inFront.blurRadius): GlassBackdropState` — Stacks two backdrops into one, back to front.
- `fun Modifier.glassBackdropSource(state: GlassBackdropState, frozen: () -> Boolean = { false }): Modifier` — Marks the content that glass surfaces sample.
- `val LocalGlassBackdrop` — The pane of glass the controls in this subtree should refract.
- `fun Modifier.glassContainer(material: GlassBackdropState): Modifier` — Il vetro dentro questo nodo sta su [material]: lo campiona sopra a quello che gli e' stato dato, come se questo nodo fosse figlio della superficie che lo disegna. Dalla 2.13.0.
- `fun Modifier.glassMaterialSource(state: GlassBackdropState): Modifier`
- `@Composable fun rememberEmptyGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState` — A backdrop with nothing in it.
- `@Composable fun currentGlassBackdrop(): GlassBackdropState` — The backdrop a control should use unless its caller names a different one.
- `val LocalFluidCanvasBackdrop` — The screen's *ambient* recording: the coloured wash and motif painted under the page, and nothing else.
- `val LocalFluidCanvasIsGlass` — Whether the canvas in scope is itself a pane of glass rather than a page's ambient wash.
- `val LocalFluidSurfaceSide` — Which side the app is painting on, when the palette cannot be asked.
- `@Composable fun currentCanvasBackdrop(): GlassBackdropState?` — The ambient recording in scope, or null when the screen has no canvas.
- `data class GlassTint(val overlay: Color, val fallback: Color, val hairline: Color)` — Colour treatment of a glass surface.
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
- `fun Modifier.drawBackdrop(backdrop: Backdrop, shape: () -> Shape, effects: BackdropEffectScope.() -> Unit, highlight: (() -> Highlight?)? = DefaultHighlight, shadow: (() -> Shadow?)? = DefaultShadow, innerShadow: (() -> InnerShadow?)? = null, layerBlock: (GraphicsLayerScope.() -> Unit)? = null, exportedBackdrop: LayerBackdrop? = null, onDrawBehind: (DrawScope.() -> Unit)? = null, onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop, onDrawSurface: (DrawScope.() -> Unit)? = null, onDrawFront: (DrawScope.() -> Unit)? = null, backdropScale: Float = 1f, sampleOnce: Boolean = false, resampleIntervalMillis: Long = 0L, backdropScaleFactor: () -> Float = OneScale, frozen: () -> Boolean = NeverFrozen, heldWhileMoving: () -> Boolean = NeverFrozen, nestInContainer: Boolean = true): Modifier`

## `engine-ui/ui/glass/backdrop/FluidGlassDiagnostics.kt`

- `object FluidGlassDiagnostics` — Counts how often glass re-captures what is behind it.
  - `var enabled: Boolean`
  - `val totalCaptures: Long get()` — Captures taken since [enabled] was first switched on.
  - `val totalExports: Long get()` — Materiali pubblicati (registrati) da quando [enabled] e' acceso: un pannello che registra cio' che disegna dietro al contenuto perche' del vetro dentro di lui lo campioni. Dalla 2.13.0 si registra solo se qualcuno lo guarda, quindi su una schermata senza vetro su vetro questo numero non si muove, e a schermo fermo non si muove mai.

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
