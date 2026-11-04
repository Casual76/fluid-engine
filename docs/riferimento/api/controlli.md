<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/controlli.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Controlli

_Generato sull'engine 3.0.4 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidControls.kt`

- `enum class FluidButtonStyle` — How much a control's colour says about it.
- `enum class FluidButtonSize`
- `object FluidButtonDefaults`
  - `fun height(size: FluidButtonSize): Dp`
  - `fun horizontalPadding(size: FluidButtonSize): Dp`
  - `fun shape(size: FluidButtonSize): Shape`
- `@Composable fun FluidButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: FluidButtonStyle = FluidButtonStyle.Filled, size: FluidButtonSize = FluidButtonSize.Medium, enabled: Boolean = true, loading: Boolean = false, fillWidth: Boolean = false, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, leading: (@Composable () -> Unit)? = null)` — A button.
- `@Composable fun <T> FluidSegmentedControl(options: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: (@Composable (option: T, selected: Boolean) -> Unit)? = null, label: (T) -> String)` — A segmented control.
- `@Composable fun FluidChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null)` — A filter pill.
- `@Composable fun FluidRowValue(text: String, modifier: Modifier = Modifier)` — A stepper-free numeric or textual value shown at the trailing edge of a settings row.
- `@Composable fun FluidHairline(modifier: Modifier = Modifier, startInset: Dp = 0.dp, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha…)` — A hairline. 0.5dp is a real width on every screen the app runs on, and it is what iOS draws.
- `@Composable fun FluidColorDot(color: Color, selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier, size: Dp = 30.dp)` — A circular dot, used to preview an accent colour in the picker.

## `engine-ui/ui/fluid/FluidGlassControls.kt`

- `enum class FluidGlassSize(val lens: Dp, val buttonHeight: Dp)` — La taglia di un tasto di vetro ([FluidGlassIconButton], [FluidGlassButton]).
  - `val touchTarget: Dp get()` — Il riquadro che il tasto tondo occupa nel layout e che il dito colpisce: mai meno di 48 dp.
  - `val iconSize: Dp get()` — Quanto sta bene un'icona dentro il vetro: la meta' del lato (20, 22, 27 dp).
- `@Composable fun Modifier.glassControlSurface(backdrop: GlassBackdropState, shape: Shape = FluidCapsuleShape, tint: GlassTint = GlassDefaults.controlTint(), optics: GlassOptics = GlassDefaults.buttonOptics(), selected: Boolean = false, interactive: Boolean = true, depth: () -> Float = { 1f }, frozen: () -> Boolean = { false }, heldWhileMoving: () -> Boolean = { false }, pressState: FluidPressState? = null): Modifier` — A pane of glass that behaves like a control.
- `@Composable fun Modifier.fluidStaticGlassSurface(shape: Shape = FluidCapsuleShape, selected: Boolean = false, pressure: () -> Float = { 0f }): Modifier` — Glass without a backdrop to refract.
- `@Composable fun FluidGlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), enabled: Boolean = true, selected: Boolean = false, shape: Shape = FluidCapsuleShape, onLongClick: (() -> Unit)? = null, size: FluidGlassSize = FluidGlassSize.Regular, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, content: @Composable BoxScope.() -> Unit)` — A glass lens for icon actions that live in chrome or another overlay.
- `@Composable fun FluidGlassIconButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), enabled: Boolean = true, selected: Boolean = false, shape: Shape = FluidCapsuleShape, onLongClick: (() -> Unit)? = null, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, content: @Composable BoxScope.() -> Unit)` — Un tasto tondo di vetro di una misura qualunque (3.0): i 62 e i 76 dp dei dischi del trasporto di un player, i 52 di un hero, i 54 di un pulsante di menu, che le tre taglie di [FluidGlassSize] non dicono.
- `@Composable fun FluidGlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…, enabled: Boolean = true, selected: Boolean = false, shape: Shape = FluidCapsuleShape, size: FluidGlassSize = FluidGlassSize.Regular, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, leading: (@Composable () -> Unit)? = null, maxLines: Int = Int.MAX_VALUE)` — A glass action button.

## `engine-ui/ui/fluid/FluidScrubber.kt`

- `typealias FluidScrubberFormat` — Come un [FluidScrubber] racconta una posizione a parole: la [position] e l'[range] in cui sta, nella stessa unita' del valore. Quella dell'app: millisecondi, secondi, l'indice di un fotogramma.
- `@Composable fun FluidScrubber(position: () -> Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, buffered: (() -> Float)? = null, step: Float = 0f, marks: List<Float> = emptyList(), onScrubStart: (() -> Unit)? = null, onScrub: ((Float) -> Unit)? = null, onScrubEnd: (() -> Unit)? = null, startLabel: FluidScrubberFormat? = null, endLabel: FluidScrubberFormat? = null, contentDescription: String = FluidScrubberDefaults.ContentDescription, valueDescription: FluidScrubberFormat = FluidScrubbing.Spoken, keyStep: Float = 0f, stepBackLabel: String = FluidScrubberDefaults.StepBackLabel, stepForwardLabel: String = FluidScrubberDefaults.StepForwardLabel, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…, onStartLabelClick: (() -> Unit)? = null, onEndLabelClick: (() -> Unit)? = null)` — Lo **scrubber**: la barra con cui si sceglie dove andare in qualcosa che dura — un brano, una registrazione, una cronologia di fotogrammi.
- `@Composable fun FluidScrubber(position: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, buffered: (() -> Float)? = null, step: Float = 0f, marks: List<Float> = emptyList(), onScrubStart: (() -> Unit)? = null, onScrub: ((Float) -> Unit)? = null, onScrubEnd: (() -> Unit)? = null, startLabel: FluidScrubberFormat? = null, endLabel: FluidScrubberFormat? = null, contentDescription: String = FluidScrubberDefaults.ContentDescription, valueDescription: FluidScrubberFormat = FluidScrubbing.Spoken, keyStep: Float = 0f, stepBackLabel: String = FluidScrubberDefaults.StepBackLabel, stepForwardLabel: String = FluidScrubberDefaults.StepForwardLabel, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…, onStartLabelClick: (() -> Unit)? = null, onEndLabelClick: (() -> Unit)? = null)` — [FluidScrubber] con la posizione data come valore: per chi la posizione la cambia di rado (una cronologia a passi).
- `object FluidScrubberDefaults` — Le misure, i testi e le soglie dello scrubber che un'app puo' voler conoscere.
  - `const val ContentDescription: String` — Il nome per TalkBack, quando l'app non ne da' uno piu' preciso.
  - `const val StepBackLabel: String` — Le due azioni di TalkBack per muoversi del `keyStep` di [FluidScrubber].
  - `const val StepForwardLabel: String`
  - `const val MaxStepDots: Int` — Oltre questo numero di passi i puntini non si disegnano: sarebbero un tratteggio, e sotto un polpastrello non si contano. Il passo c'e' lo stesso, e la maniglia ci si posa.
- `object FluidScrubbing` — I conti e i testi di [FluidScrubber]. Pubblico per i formati: un'app che mostra il tempo da se' accanto alla barra usa gli stessi.
  - `fun clock(millis: Long): String` — Un tempo come lo scrive un orologio: «3:20», «59:59», «1:02:03». Da [millis] in millisecondi, per difetto al secondo (un lettore a 3:19,9 e' ancora a 3:19) e mai sotto zero.
  - `val Elapsed: FluidScrubberFormat` — Il tempo trascorso, «1:05». Per i millisecondi: per un'altra unita' si passa un formato proprio.
  - `val Remaining: FluidScrubberFormat` — Quanto manca, «−2:15», con il meno tipografico.
  - `val Total: FluidScrubberFormat` — La durata intera, «3:20».
  - `val Spoken: FluidScrubberFormat` — Il valore a voce, «1:05 di 3:20»: il default di [FluidScrubber].

## `engine-ui/ui/fluid/FluidSlider.kt`

- `@Composable fun FluidSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, snapValues: List<Float> = emptyList(), snapThreshold: Float = 0.03f, contentDescription: String? = null, stateDescription: String? = null, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — Uno slider di vetro: una pista sottile riempita con l'accento, e una pillola che a riposo e' bianca e opaca e si fa lente mentre il dito la tiene.

## `engine-ui/ui/fluid/FluidSwitch.kt`

- `@Composable fun FluidSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — The switch.
- `@Composable fun FluidGlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — **DEPRECATO: FluidSwitch is this control now.** — The name this control had for one release, while it was a second control beside the old switch.
- `val FluidSwitchWidth: Dp` — Width helper so a row of controls can reserve the switch's footprint without instantiating one.

## `engine-ui/ui/fluid/FluidTextEdit.kt`

- `object FluidTextEdit` — Le operazioni di un editor di testo semplice, sul valore e non sul componente.
  - `fun insert(value: TextFieldValue, snippet: String): TextFieldValue` — Mette [snippet] al posto della selezione, o al cursore, e lascia il cursore dopo di esso.
  - `fun wrap(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue` — Avvolge la selezione fra [prefix] e [suffix] e la tiene selezionata, cosi' un secondo tocco puo' avvolgerla ancora. Senza selezione mette la coppia e lascia il cursore in mezzo, pronto a scrivere dentro.
  - `fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue` — Mette [prefix] all'inizio della riga del cursore (`# `, `- `, `> `), o lo toglie se c'e' gia'.

## `engine-ui/ui/fluid/FluidTextField.kt`

- `@Composable fun FluidTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null, label: String? = null, enabled: Boolean = true, readOnly: Boolean = false, singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, isError: Boolean = false, supportingText: String? = null, minHeight: androidx.compose.ui.unit.Dp = 44.dp, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, visualTransformation: VisualTransformation = VisualTransformation.None, showClearButton: Boolean = singleLine, secret: Boolean = false, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)` — A text field.
- `@Composable fun FluidTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null, label: String? = null, enabled: Boolean = true, readOnly: Boolean = false, singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, isError: Boolean = false, supportingText: String? = null, minHeight: androidx.compose.ui.unit.Dp = 44.dp, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, visualTransformation: VisualTransformation = VisualTransformation.None, showClearButton: Boolean = singleLine, secret: Boolean = false, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)` — The text field, with the caret.
