<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/menu-modali.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Menu, pop-up, fogli, avvisi, notifiche

_Generato sull'engine 3.0.6 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidGlassMenuButton.kt`

- `@Composable fun FluidGlassMenuButton(actions: () -> List<FluidContextAction>, onClick: () -> Unit, modifier: Modifier = Modifier, text: String? = null, icon: ImageVector? = null, contentDescription: String? = null, enabled: Boolean = true, backdrop: GlassBackdropState = currentGlassBackdrop(), shape: Shape = FluidCapsuleShape)` — **DEPRECATO: Il menù compare sopra il tasto: due superfici. FluidMorphMenuButton (con ** — A button that unfolds into its own menu, and folds back once something is chosen.

## `engine-ui/ui/fluid/FluidGlassModal.kt`

- `class FluidGlassModalHostState internal constructor()` — A modal that lives *inside* the app's composition.
  - `val isPresenting: Boolean get()` — Whether anything is currently open. An app can use it to obscure the page behind.
  - `val isOnScreen: Boolean get()` — Whether anything still has pixels on screen, the exit animation included.
  - `fun dismissTop()` — Asks whatever is innermost to close. This is what back and a tap on the scrim call.
- `@Composable fun rememberFluidGlassModalHostState(): FluidGlassModalHostState`
- `val LocalFluidGlassModalHostState: ProvidableCompositionLocal<FluidGlassModalHostState?>` — Lets any feature declare a modal without owning, or duplicating, the single root host.
- `enum class FluidGlassModalPresentation` — How a modal arranges itself once it has finished arriving.
- `data class FluidContextAction(val label: String, val icon: ImageVector? = null, val destructive: Boolean = false, val enabled: Boolean = true, val startsSection: Boolean = false, val selected: Boolean = false, val onClick: () -> Unit)` — One action in a context menu.
- `@Composable fun FluidGlassModalPortal(visible: Boolean, onDismissRequest: () -> Unit, origin: () -> Rect? = { null }, presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover, paneTitle: String? = null, paneTint: Color? = null, footer: (@Composable ColumnScope.() -> Unit)? = null, pageColor: Color? = null, content: @Composable ColumnScope.() -> Unit)` — Declares a modal here and draws it at the root.
- `@Composable fun <T : Any> FluidGlassModalPortal(item: T?, onDismissRequest: () -> Unit, origin: () -> Rect? = { null }, presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover, paneTitle: String? = null, paneTint: Color? = null, footer: (@Composable ColumnScope.(T) -> Unit)? = null, pageColor: Color? = null, content: @Composable ColumnScope.(T) -> Unit)` — Declares a modal that presents **an item**, and stays whole while it leaves.
- `@Composable fun Modifier.fluidExpandOrigin(open: () -> Boolean = { false }, onMeasured: (Rect) -> Unit): Modifier` — Reports this element's bounds in root coordinates, for a modal to expand out of.
- `@Composable fun Modifier.fluidGlassModalObscured(): Modifier` — Hides everything under it from accessibility while a modal is open.
- `@Composable fun FluidGlassModalHost(state: FluidGlassModalHostState, backdrop: GlassBackdropState?, modifier: Modifier = Modifier)` — The host. One per app, at the root, next to [FluidNotificationHost].
- `class FluidContextMenuController internal constructor(private val host: FluidGlassModalHostState?, internal val entry: FluidGlassModalEntry, internal val layer: GraphicsLayer)` — A context menu that something else decides when to open.
  - `val isOpen: Boolean get()`
  - `fun open(): Boolean` — Raises the menu. Returns false when there is nothing to show, so the caller can fall through.
  - `fun dismiss()`
- `@Composable fun rememberFluidContextMenu(actions: () -> List<FluidContextAction>, tint: Color? = null): FluidContextMenuController` — The state behind a context menu, so a component that owns its own gestures can raise one.
- `@Composable fun Modifier.fluidContextMenuAnchor(controller: FluidContextMenuController): Modifier` — Marks the element a [FluidContextMenuController] belongs to.
- `@Composable fun Modifier.fluidContextMenu(actions: () -> List<FluidContextAction>, enabled: Boolean = true): Modifier` — Long-press this element to raise a menu of actions anchored to it.

## `engine-ui/ui/fluid/FluidMenu.kt`

- `enum class FluidMenuOpensOn` — Quale gesto apre il menu di un tasto (3.0, fase 7).

## `engine-ui/ui/fluid/FluidNotification.kt`

- `enum class FluidNotificationTone`
- `data class FluidNotificationAction(val label: String, val onClick: () -> Unit)` — Un'azione della notifica (2.12.0): compare solo quando la card e' stata aperta tirandola in giu'.
- `data class FluidNotification(val id: String, val title: String, val message: String, val tone: FluidNotificationTone = FluidNotificationTone.Info, val durationMillis: Long = 6_000L, val actions: List<FluidNotificationAction> = emptyList())` — Una notifica in-app.
- `enum class FluidNotificationDelivery` — A durable producer acknowledges only after the card has been genuinely presented.
- `class FluidNotificationHostState internal constructor()` — A small FIFO for transient, app-wide messages.
  - `suspend fun show(notification: FluidNotification): FluidNotificationDelivery` — Suspends until this ID is actually laid out by an active host. Concurrent calls for the same ID await the same delivery instead of duplicating the banner. A host lifecycle interruption transparently re-enqueues an unpresented request.
  - `fun dismissCurrent()`
- `@Composable fun rememberFluidNotificationHostState(): FluidNotificationHostState`
- `val LocalFluidNotificationHostState: ProvidableCompositionLocal<FluidNotificationHostState?>` — Lets any feature publish into the single root host without owning or duplicating its UI.
- `@Composable fun FluidNotificationHost(state: FluidNotificationHostState, modifier: Modifier = Modifier, backdrop: GlassBackdropState? = null)` — A top-of-app notice inspired by iOS banners, hosted outside navigation content.

## `engine-ui/ui/fluid/FluidPromptAlert.kt`

- `sealed interface FluidPromptValidation` — Cosa pensa un avviso con un campo di quello che e' stato scritto.
  - `data object Valid : FluidPromptValidation` — Si puo' confermare.
  - `data object Incomplete : FluidPromptValidation` — Non ancora: il tasto resta spento e il campo tace. Il caso del campo vuoto.
  - `data class Invalid(val message: String) : FluidPromptValidation` — Scritto, ma sbagliato: il tasto resta spento e il campo mostra [message] sotto di se', in errore.
- `fun fluidPromptNotBlank(text: String): FluidPromptValidation` — La validazione di serie di [FluidPromptAlert]: basta che ci sia qualcosa che non siano spazi.
- `@Composable fun FluidPromptAlert(title: String, onDismissRequest: () -> Unit, onConfirm: (String) -> Unit, modifier: Modifier = Modifier, message: String? = null, initialValue: String = "", placeholder: String? = null, confirmLabel: String = "Salva", cancelLabel: String = "Annulla", validate: (String) -> FluidPromptValidation = ::fluidPromptNotBlank, keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapita…, trim: Boolean = true, selectAll: Boolean = true, singleLine: Boolean = true)` — Un avviso che chiede una riga di testo: rinominare qualcosa, scrivere un valore di taratura.

## `engine-ui/ui/fluid/FluidSheet.kt`

- `@Composable fun FluidSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpa…, title: String? = null, showGrabber: Boolean = true, content: @Composable ColumnScope.() -> Unit)` — **DEPRECATO: Un pop-up in vetro deve stare dentro la composizione dell'app: usa ** — A modal sheet.
- `@Composable fun FluidGrabber(modifier: Modifier = Modifier)` — The grabber.
- `data class FluidAlertAction(val label: String, val onClick: () -> Unit, val emphasis: Emphasis = Emphasis.Normal, val enabled: Boolean = true)` — One button in a [FluidAlert].
  - `enum class Emphasis`
- `@Composable fun FluidAlert(onDismissRequest: () -> Unit, title: String, modifier: Modifier = Modifier, message: String? = null, actions: List<FluidAlertAction>, inHost: Boolean = true, content: (@Composable ColumnScope.() -> Unit)? = null)` — An alert.

## `engine-ui/ui/fluid/FluidSheetScaffold.kt`

- `object FluidSheetScaffoldDefaults` — Le misure del contenuto di un foglio di vetro.
  - `val MaxWidth: Dp` — La larghezza oltre la quale il contenuto si ferma e sta in mezzo.
  - `val ContentPadding: PaddingValues` — L'aria intorno al contenuto. In alto 16 dp: sotto il grabber di un foglio ce n'e' gia' un po', ma un pop-up non ha niente sopra e il suo angolo e' da 22 dp, e il testo non deve toccarlo.
  - `val IconTileSize: Dp` — Il lato della piastrella dell'icona: piu' grande di quella di una riga (32), perche' qui e' l'unico segno.
  - `val HeaderGap: Dp` — Fra la piastrella e il titolo.
  - `val BodyGap: Dp` — Fra le cose del corpo (la frase, un gruppo di righe, una nota).
  - `val HeaderToBody: Dp` — Fra la testata e il corpo.
  - `val BodyToActions: Dp` — Fra il corpo e i tasti.
  - `val ActionGap: Dp` — Fra un tasto e il successivo.
- `@Composable fun FluidSheetScaffold(title: String, modifier: Modifier = Modifier, message: String? = null, icon: (@Composable () -> Unit)? = null, tone: FluidTone = FluidTone.Primary, maxWidth: Dp = FluidSheetScaffoldDefaults.MaxWidth, contentPadding: PaddingValues = FluidSheetScaffoldDefaults.ContentPadding, scrollable: Boolean = true, insetBottom: Boolean = true, actions: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {})` — Il contenuto di un foglio o di un pop-up di vetro: una testata (con la piastrella dell'icona, se c'e'), un corpo che scorre e dei tasti che **non** scorrono.
- `@Composable fun FluidConfirmSheet(visible: Boolean, onDismissRequest: () -> Unit, title: String, onConfirm: () -> Unit, modifier: Modifier = Modifier, message: String? = null, icon: (@Composable () -> Unit)? = null, tone: FluidTone? = null, confirmLabel: String = "Conferma", cancelLabel: String = "Annulla", destructive: Boolean = false, confirmEnabled: Boolean = true, presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Sheet, origin: () -> Rect? = { null }, content: @Composable ColumnScope.() -> Unit = {})` — Un foglio di conferma: un titolo, una frase, e due tasti — **conferma** e **annulla** —, in un pop-up di vetro che sale dal basso.
