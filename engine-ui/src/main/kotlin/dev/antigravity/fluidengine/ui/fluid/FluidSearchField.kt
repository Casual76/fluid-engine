package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.roundToInt
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidSearchReadingSurface
import dev.antigravity.fluidengine.ui.fluid.toolbar.ToolbarSearchTiming
import dev.antigravity.fluidengine.ui.fluid.toolbar.ToolbarSearchAnimation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowDown
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowUp
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import androidx.compose.material3.LocalContentColor

/** La tab bar fornisce già il pezzo del tondo, il materiale e il viaggio al suo slot di ricerca. */
internal val LocalFluidSearchTabSurface = compositionLocalOf { false }

/** Le misure del [FluidSearchField]. */
object FluidSearchFieldDefaults {
  /** L'altezza: 52 dp, la stessa che il campo di ricerca di Fluid-transit si dava a mano. */
  val Height: Dp = 52.dp

  /** La lente d'ingrandimento. */
  val IconSize: Dp = 22.dp

  /**
   * Il margine a sinistra della lente: quello che la mette **al centro** del cerchio quando il campo e'
   * chiuso — `(Height - IconSize) / 2` — e che resta come margine di testo quando si apre. Il cerchio
   * che diventa campo non deve spostare la lente di un pixel.
   */
  val IconInset: Dp = (Height - IconSize) / 2

  /** Il lato dei comandi dentro il campo (precedente, successivo, chiudi): quattro in 332 dp non ci stanno a 48. */
  val ActionSize: Dp = 40.dp

  /** La larghezza di apertura quando il genitore non ne da' una (dentro uno scorrimento orizzontale). */
  val ExpandedFallbackWidth: Dp = 320.dp
}

/**
 * Le parole del campo, tutte insieme: un'app in un'altra lingua ne passa una e non cinque parametri.
 * Il default e' l'italiano dell'engine ("Chiudi", "Indietro", ...).
 *
 * @property counterSpoken cosa legge TalkBack al posto di «3/12»: «3 di 12». Riceve il numero della
 *   corrente da 1 e il totale.
 * @property send (F6B.6) il nome del tasto «Invia» del campo con [FluidSearchTrailing.Send].
 * @property message (F6B.6) il segnaposto di serie di [FluidSearchSpec.message].
 * @property note (F6B.6) il segnaposto di serie di [FluidSearchSpec.note].
 * @property assistant (F6B.6) il segnaposto di serie di [FluidSearchSpec.assistant].
 * @property findInPage (F6B.6) il segnaposto di serie di [FluidSearchSpec.findInPage].
 * @property dictate (F6B.7) il nome del microfono del composer ([FluidSearchTrailing.voice]).
 * @property stop (F6B.7) il nome dello stop del composer ([FluidSearchTrailing.stop]), se l'app non ne dà uno.
 */
@Immutable
data class FluidSearchLabels(
  val search: String = "Cerca",
  val clear: String = "Cancella",
  val close: String = "Chiudi la ricerca",
  val previous: String = "Risultato precedente",
  val next: String = "Risultato successivo",
  val noResults: String = "Nessun risultato",
  val counterSpoken: (current: Int, total: Int) -> String = { current, total -> "$current di $total" },
  val send: String = "Invia",
  val message: String = "Messaggio",
  val note: String = "Nota",
  val assistant: String = "Chiedi all'assistente",
  val findInPage: String = "Trova nella pagina",
  val dictate: String = "Dettatura",
  val stop: String = "Interrompi",
)

/**
 * La navigazione fra i risultati di una ricerca dentro la pagina: «3/12» e i due tasti che vanno
 * avanti e indietro.
 *
 * Il campo non conosce cosa si cerca (una trascrizione, un documento, una pagina web): sa solo
 * mostrare dov'e' e passare il tocco. I due tasti restano accesi con almeno un risultato; che alla fine
 * si torni all'inizio lo decide l'app.
 *
 * @property current l'indice della risultato corrente, **da zero**: il campo scrive `current + 1`.
 * @property total quanti sono; zero spegne i tasti e scrive «Nessun risultato» se c'e' una parola.
 */
@Immutable
class FluidSearchNavigation(
  val current: Int,
  val total: Int,
  val onPrevious: () -> Unit,
  val onNext: () -> Unit,
)

/**
 * La scritta del contatore: «3/12» (da [current], da zero, su [total]); [noResults] se c'e' una parola
 * e nessun risultato; `null` con la parola vuota, quando non c'e' niente da contare.
 *
 * Un indice fuori dai limiti — la lista dei risultati si e' accorciata prima che l'app aggiornasse la
 * posizione — si ferma all'ultimo invece di scrivere «13/12».
 */
internal fun fluidSearchCounter(query: String, current: Int, total: Int, noResults: String): String? = when {
  query.isBlank() -> null
  total <= 0 -> noResults
  else -> "${current.coerceIn(0, total - 1) + 1}/$total"
}

/**
 * La larghezza del campo durante l'apertura: dal cerchio ([collapsedPx]) a tutto lo spazio
 * ([expandedPx]) col progresso della molla. Mai meno del cerchio, mai oltre lo spazio.
 */
internal fun fluidSearchWidth(collapsedPx: Int, expandedPx: Int, progress: Float): Int =
  lerp(collapsedPx, maxOf(collapsedPx, expandedPx), progress.coerceIn(0f, 1f))

/** La dissolvenza del contenuto del campo: compare dal 20% dell'apertura, e va via per primo chiudendo. */
internal fun fluidSearchContentAlpha(progress: Float): Float = ((progress - 0.2f) / 0.6f).coerceIn(0f, 1f)

/**
 * Un campo di ricerca in vetro: la capsula con la lente a sinistra, il testo, la crocetta che
 * svuota e — se serve — «3/12» con i due tasti per scorrere i risultati dentro la pagina.
 *
 * Tre app se lo erano scritto (Fluid-transit, Pampa notes, Fluidify), ognuna con qualcosa di diverso e
 * qualcosa di sbagliato: quella senza il fuoco automatico, quella con il campo di testo di una pagina
 * dentro un vetro (un pozzetto opaco dentro una lastra), quella con la X a destra che era due X. Qui il
 * campo e' **la lastra stessa**: lo stesso materiale della capsula di azioni ([FluidFloatingCapsule]) e
 * della barra di navigazione, quindi due pillole nella stessa fila non sono due grigi.
 *
 * **Il testo e' quello del motore** (`bodyLarge`, il cursore dell'accento): lo stesso del
 * `FluidTextField`, che qui non si puo' usare perche' porta il suo pozzetto — un incavo dentro un
 * vetro e' un rettangolo appiccicato sopra la lastra.
 *
 * **Si apre da un'icona.** Con [onExpandedChange] il campo e' richiudibile: [expanded] falso e' un
 * cerchio di vetro con la lente, che toccato si allarga fino a tutto lo spazio — la larghezza e' la
 * molla della casa ([FluidMotion.standard]), lo stesso vetro per tutta l'apertura, senza un cambio di
 * superficie a meta' — e prende il fuoco con la tastiera. Aperto ha una X a destra per richiuderlo, e
 * «indietro» fa lo stesso. Dove il cerchio sta (a destra di una barra, al centro) lo decide chi lo
 * contiene: il campo cresce dal lato opposto a quello a cui e' allineato. Senza [onExpandedChange] e'
 * sempre aperto e [expanded] non conta.
 *
 * **Il fuoco e la tastiera.** Con [autoFocus] il campo prende il fuoco quando si apre (o compare) e la
 * tastiera sale; chiudendosi il fuoco e la tastiera vanno via, non restano a coprire. «Cerca» sulla
 * tastiera chiama [onSearch]; senza, nasconde la tastiera (la ricerca era gia' live: scrivere e'
 * cercare) e lascia il testo.
 *
 * **Con la navigazione** ([navigation]) e' una ricerca dentro la pagina: il contatore e' un'area viva
 * per TalkBack («3 di 12») e «Invio» di solito e' «avanti», quindi [onSearch] non nasconde la tastiera.
 *
 * Si posa con [fluidCapsuleInsets] se prende il posto della barra di navigazione, e sale con la
 * tastiera con `avoidKeyboard = true`.
 *
 * @param backdrop cio' che il vetro del campo rifrange. Di serie (3.0.1) il canvas della pagina se c'e',
 *   come i chip, il segmentato e lo slider, altrimenti la chrome in scope: prima era sempre la chrome, e su
 *   una pagina con un canvas ambientale il campo rifrangeva il corpo della pagina invece della lavata
 *   sotto di lui. In una barra il canvas non c'e' (la barra lo toglie), e resta il vetro della barra.
 * @param placeholder cosa dice a campo vuoto; di serie [FluidSearchLabels.search].
 * @param labels le parole per TalkBack e per il «nessun risultato».
 * @param onSearch il tasto «cerca» della tastiera. Null: nasconde la tastiera.
 * @param expanded vero: aperto. Conta solo se c'e' [onExpandedChange].
 * @param onExpandedChange chi lo passa rende il campo richiudibile e riceve l'apertura (dal cerchio) e
 *   la chiusura (dalla X e da «indietro»). Non svuota la ricerca: lo decide l'app.
 * @param navigation il contatore e i tasti dei risultati; null: niente.
 * @param autoFocus il fuoco alla comparsa. Falso per un campo sempre in pagina dove una tastiera che
 *   sale da sola coprirebbe i risultati.
 * @param onPhoto la pellicola di `floatingTintOnPhoto`, sopra una foto a tutta pagina.
 */
@Composable
fun FluidSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
  placeholder: String? = null,
  labels: FluidSearchLabels = FluidSearchLabels(),
  onSearch: (() -> Unit)? = null,
  expanded: Boolean = true,
  onExpandedChange: ((Boolean) -> Unit)? = null,
  navigation: FluidSearchNavigation? = null,
  autoFocus: Boolean = true,
  onPhoto: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
) {
  val scheme = MaterialTheme.colorScheme
  val collapsible = onExpandedChange != null
  val tabSurface = LocalFluidSearchTabSurface.current
  val open = tabSurface || expanded || !collapsible
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val progress = animateFloatAsState(
    targetValue = if (open) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else ToolbarSearchTiming,
    label = "fluid search expansion",
  )
  val focus = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current

  // Il fuoco si toglie solo quando il campo **si chiude**, non quando compare gia' chiuso: un cerchio
  // che entra in una pagina con un altro campo in primo piano non deve strappargli il cursore.
  var wasOpen by remember { mutableStateOf(open) }
  LaunchedEffect(open, autoFocus) {
    if (open) {
      // Il campo e' in composizione (showContent e' vero con open), ma il nodo del fuoco puo' non
      // essere ancora collegato al primo istante: nessuna ragione per far cadere la schermata.
      if (autoFocus) runCatching { focus.requestFocus() }
    } else if (wasOpen) {
      focusManager.clearFocus()
      keyboard?.hide()
    }
    wasOpen = open
  }
  BackHandler(enabled = collapsible && open && !tabSurface) { onExpandedChange?.invoke(false) }
  // F2.3b: il campo possiede la sua lente e il suo testo e gira come i tasti di vetro.
  val searchInk = rememberGlassInk()

  SearchFieldSurface(collapsible, open, modifier, backdrop, onPhoto, searchInk, { progress.value }) { surface, planeInk ->
    Row(
      modifier = surface
        .pointerInput(Unit) { detectTapGestures { } }
        // Chiuso, tutto il cerchio e' un tasto. La crescita sta *dentro* il vetro (la lente che cresce
        // si legge, il vetro che cresce da un antenato rifrange un fondale che non c'e' piu'), e l'aptica
        // e' quella di un tasto che apre qualcosa: Open al posto del click.
        .then(
          if (!open) {
            Modifier.fluidPressable(
              onClick = { onExpandedChange?.invoke(true) },
              role = Role.Button,
              focusShape = FluidCapsuleShape,
              feedback = FluidPressFeedback.Expand,
              haptic = FluidHapticEvent.Open,
            )
          } else {
            Modifier
          },
        )
        .clip(FluidCapsuleShape),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GlassInkContent(searchInk, planeInk) {
        FluidIcon(
          imageVector = FluidIcons.Search,
          // La lente e' il nome del tasto solo da chiuso: aperto e' decorazione del campo.
          contentDescription = if (open) null else labels.search,
          // 4.0 (F1A.11): da chiuso e' un tasto di barra, quindi inchiostro del testo come le altre icone di barra e non
          // l'accento (R6: 15-17:1 contro 3,5-6,7:1); da aperto e' decorazione del campo e resta nel tono secondario.
          tint = if (collapsible) LocalContentColor.current else scheme.onSurfaceVariant,
          modifier = Modifier
            .padding(start = if (collapsible) (FluidMetrics.GlassButtonLens - FluidSearchFieldDefaults.IconSize) / 2 else FluidSearchFieldDefaults.IconInset)
            .size(FluidSearchFieldDefaults.IconSize),
        )
        Row(
          modifier = Modifier
            .weight(1f)
            .padding(start = 10.dp, end = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (collapsible) FluidGlassContentTransition(
              if (open && query.isEmpty()) placeholder ?: labels.search else null,
              contentAlignment = Alignment.CenterStart,
            ) { hint ->
              if (hint != null) Text(hint, style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (open) SearchTextField(
              query = query,
              onQueryChange = onQueryChange,
              placeholder = if (collapsible) "" else placeholder ?: labels.search,
              keyboardOptions = keyboardOptions,
              onSearch = {
                if (onSearch != null) {
                  onSearch()
                } else {
                  keyboard?.hide()
                  focusManager.clearFocus()
                }
              },
              focus = focus,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          AnimatedVisibility(
            visible = query.isNotEmpty() && open,
            enter = fadeIn(FluidMotion.fadeIn(140)) + scaleIn(FluidMotion.snappy(), initialScale = 0.6f),
            exit = fadeOut(FluidMotion.fadeOut(120)) + scaleOut(FluidMotion.fadeOut(120), targetScale = 0.6f),
          ) {
            SearchClearButton(label = labels.clear, onClick = { onQueryChange("") })
          }
          if (navigation != null && open) {
            SearchCounter(query = query, navigation = navigation, labels = labels)
            FluidCapsuleAction(
              icon = FluidIcons.KeyboardArrowUp,
              contentDescription = labels.previous,
              onClick = navigation.onPrevious,
              enabled = navigation.total > 0,
              tint = scheme.onSurface,
              size = FluidSearchFieldDefaults.ActionSize,
            )
            FluidCapsuleAction(
              icon = FluidIcons.KeyboardArrowDown,
              contentDescription = labels.next,
              onClick = navigation.onNext,
              enabled = navigation.total > 0,
              tint = scheme.onSurface,
              size = FluidSearchFieldDefaults.ActionSize,
            )
          }
          if (collapsible) FluidGlassContentTransition(if (open) true else null) { visible ->
            if (visible == true && open) FluidCapsuleAction(
              icon = FluidIcons.Close,
              contentDescription = labels.close,
              onClick = { onExpandedChange?.invoke(false) },
              tint = scheme.onSurface,
              size = FluidSearchFieldDefaults.ActionSize,
            ) else if (visible == true) Box(Modifier.size(FluidSearchFieldDefaults.ActionSize),
              contentAlignment = Alignment.Center) { FluidIcon(FluidIcons.Close, null) }
          }
        }
      }
    }
  }
}

/** Il layout del contenuto assume subito la misura finale. Il genitore che centra il campo vede la sagoma in volo. */
@Composable
private fun SearchFieldSurface(
  collapsible: Boolean, open: Boolean, modifier: Modifier, backdrop: GlassBackdropState, onPhoto: Boolean,
  ink: GlassInk, progress: () -> Float, content: @Composable (Modifier, Color) -> Unit,
) {
  if (LocalFluidSearchTabSurface.current) {
    content(modifier.fluidSearchReadingSurface(LocalContentColor.current) { 1f }, LocalContentColor.current)
    return
  }
  if (!collapsible) {
    content(modifier.fillMaxWidth().heightIn(min = FluidSearchFieldDefaults.Height)
      .fluidFloatingGlass(backdrop = backdrop, onPhoto = onPhoto, ink = ink), LocalContentColor.current)
    return
  }
  val namespace = rememberFluidGlassNamespace()
  val id = remember(namespace) { FluidGlassQualifiedId(namespace, "ricerca") }
  val tint = if (onPhoto) GlassDefaults.floatingTintOnPhoto() else GlassDefaults.floatingTint()
  CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    FluidGlassContainer(modifier, animation = ToolbarSearchAnimation, propagateMinConstraints = true) {
      val plane = currentFluidGlassContainer(FluidCapsuleShape)
      val lens = FluidMetrics.GlassButtonLens
      val field = FluidTextFieldDefaults.MinHeight
      val surface = if (plane != null) {
        Modifier.layout { measurable, constraints ->
          val row = measurable.measure(constraints)
          val visible = plane.flightOf(id)?.bounds?.width ?: plane.restFields.singleOrNull()?.node?.width
          layout(constraints.constrainWidth(visible?.roundToInt() ?: row.width), row.height) { row.place(0, 0) }
        }.fluidSearchWidth({ if (open) 1f else 0f }, lens)
          .heightIn(min = if (open) field else lens)
          .fluidGlassId(id.id, namespace).fluidGlassContentAnchor(FluidGlassContentAnchor.Start)
          .fluidGlassPanel(backdrop, tint, FluidCapsuleShape)
      } else {
        Modifier.fluidSearchWidth(progress, lens).heightIn(min = androidx.compose.ui.unit.lerp(lens, field, progress().coerceIn(0f, 1f)))
          .fluidFloatingGlass(backdrop = backdrop, onPhoto = onPhoto, ink = ink)
      }
      content(surface.fluidSearchReadingSurface(LocalContentColor.current, progress), LocalContentColor.current)
    }
  }
}

/** Il campo di testo con il segnaposto, senza nessuna decorazione: la lastra e' il suo sfondo. */
@Composable
private fun SearchTextField(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  keyboardOptions: KeyboardOptions,
  onSearch: () -> Unit,
  focus: FocusRequester,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  BasicTextField(
    value = query,
    onValueChange = onQueryChange,
    singleLine = true,
    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
    cursorBrush = SolidColor(scheme.primary),
    keyboardOptions = keyboardOptions,
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    modifier = modifier.focusRequester(focus),
    decorationBox = { inner ->
      Box(contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty() && placeholder.isNotEmpty()) {
          Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        inner()
      }
    },
  )
}

/**
 * Cosa scrive e cosa dice il contatore (F6B.6 lo porta anche nel campo della barra): la scritta di [fluidSearchCounter],
 * la frase per TalkBack e l'area viva, che fa leggere ogni cambio senza spostare il fuoco. Null: niente da contare.
 */
internal class FluidSearchCounterSpeech(val text: String, val spoken: String, val liveRegion: LiveRegionMode)

internal fun fluidSearchCounterSpeech(query: String, navigation: FluidSearchNavigation, labels: FluidSearchLabels): FluidSearchCounterSpeech? {
  val text = fluidSearchCounter(query, navigation.current, navigation.total, labels.noResults) ?: return null
  val spoken = if (navigation.total > 0) {
    labels.counterSpoken(navigation.current.coerceIn(0, navigation.total - 1) + 1, navigation.total)
  } else {
    text
  }
  return FluidSearchCounterSpeech(text, spoken, LiveRegionMode.Polite)
}

/** «3/12», area viva: chi non vede la lista scorrere sente «3 di 12». */
@Composable
internal fun SearchCounter(
  query: String,
  navigation: FluidSearchNavigation,
  labels: FluidSearchLabels,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  val speech = fluidSearchCounterSpeech(query, navigation, labels) ?: return
  Text(
    text = speech.text,
    // Le cifre tabulari: «9/12» e «10/12» non fanno ballare i tasti accanto.
    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
    color = color,
    maxLines = 1,
    modifier = Modifier
      .padding(horizontal = 4.dp)
      .semantics {
        contentDescription = speech.spoken
        liveRegion = speech.liveRegion
      },
  )
}

/**
 * Il cerchio con la croce che svuota, disegnato e non spedito come asset: lo stesso di
 * `FluidTextField`, che lo tiene privato. Prende il dito come un tasto (cresce) e dice il suo nome.
 */
@Composable
internal fun SearchClearButton(label: String, onClick: () -> Unit) {
  val disc = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
  val glyph = MaterialTheme.colorScheme.surface
  Box(
    modifier = Modifier
      .size(28.dp)
      .fluidPressable(onClick = onClick, role = Role.Button, feedback = FluidPressFeedback.Expand)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(17.dp)) {
      val radius = size.minDimension / 2f
      drawCircle(color = disc, radius = radius)
      val arm = radius * 0.44f
      val stroke = radius * 0.2f
      drawLine(glyph, center + Offset(-arm, -arm), center + Offset(arm, arm), strokeWidth = stroke, cap = StrokeCap.Round)
      drawLine(glyph, center + Offset(-arm, arm), center + Offset(arm, -arm), strokeWidth = stroke, cap = StrokeCap.Round)
    }
  }
}

/**
 * La larghezza che segue l'apertura, letta nel layout: durante la molla il campo si rimisura e basta,
 * senza ricomporre. Il cerchio e' `collapsed` per `collapsed` (con l'altezza minima, un cerchio vero).
 */
private fun Modifier.fluidSearchWidth(progress: () -> Float, collapsed: Dp): Modifier =
  layout { measurable, constraints ->
    val collapsedPx = collapsed.roundToPx()
    val expandedPx = if (constraints.hasBoundedWidth) {
      constraints.maxWidth
    } else {
      FluidSearchFieldDefaults.ExpandedFallbackWidth.roundToPx()
    }
    val width = fluidSearchWidth(collapsedPx, expandedPx, progress())
    val placeable = measurable.measure(
      Constraints(
        minWidth = width,
        maxWidth = width,
        minHeight = constraints.minHeight,
        maxHeight = constraints.maxHeight,
      ),
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
  }
