package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile

/**
 * Le misure del contenuto di un foglio di vetro.
 *
 * In un oggetto e non in costanti private perche' sono i numeri che le app si erano scritte da sole
 * (560 dp, 20 dp di margine, una piastrella di 44) e che vanno ritoccati insieme se cambiano.
 */
object FluidSheetScaffoldDefaults {

  /**
   * La larghezza oltre la quale il contenuto si ferma e sta in mezzo.
   *
   * Un foglio su un tablet in orizzontale e' largo quanto la finestra fino ai 620 dp del foglio: dentro,
   * una riga di scelta lunga mezzo metro e' una riga che si legge con gli occhi, non con il pollice. Il
   * pannello resta largo — e' lui il fondo — e a fermarsi e' quello che ci sta scritto.
   */
  val MaxWidth: Dp = 560.dp

  /**
   * L'aria intorno al contenuto. In alto 16 dp: sotto il grabber di un foglio ce n'e' gia' un po', ma
   * un pop-up non ha niente sopra e il suo angolo e' da 22 dp, e il testo non deve toccarlo.
   */
  val ContentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp)

  /** Il lato della piastrella dell'icona: piu' grande di quella di una riga (32), perche' qui e' l'unico segno. */
  val IconTileSize: Dp = 44.dp

  /** Fra la piastrella e il titolo. */
  val HeaderGap: Dp = 14.dp

  /** Fra le cose del corpo (la frase, un gruppo di righe, una nota). */
  val BodyGap: Dp = 12.dp

  /** Fra la testata e il corpo. */
  val HeaderToBody: Dp = 12.dp

  /** Fra il corpo e i tasti. */
  val BodyToActions: Dp = 16.dp

  /** Fra un tasto e il successivo. */
  val ActionGap: Dp = 8.dp
}

/**
 * Il contenuto di un foglio o di un pop-up di vetro: una testata (con la piastrella dell'icona, se c'e'),
 * un corpo che scorre e dei tasti che **non** scorrono.
 *
 * `FluidGlassModalPortal` consegna il pannello e basta: i margini, la larghezza e lo scorrimento li mette
 * chi ci scrive dentro. Tre app ne avevano scritto uno ciascuna (`SheetBody`/`SheetScaffold`,
 * `CodexSheetColumn`, `GallerySheetScaffold`) e a ciascuna mancava un pezzo che l'altra aveva: chi la
 * larghezza massima, chi lo scorrimento, chi i tasti fermi.
 *
 * **I tasti non scorrono, il corpo si.** Un foglio e' alto al piu' una misura (640 dp) e non scorre da
 * se': con un testo ingrandito o una lista lunga, i tasti in fondo sarebbero tagliati e la decisione non
 * si potrebbe prendere. «Condividi» sotto il bordo, dietro una lista che sembra finire, e' un tasto che
 * meta' delle persone non trova. Per questo la testata e i tasti si misurano **per primi** e il corpo
 * prende quello che avanza (`fluidSheetBodyBudget`): non un `weight`, perche' un `weight` in una colonna
 * senza altezza massima — un foglio dentro uno scorrimento, un test — da' al corpo altezza zero, e il
 * foglio sembra vuoto.
 *
 * **Il testo sul vetro e' `onSurface`**, non il secondario: sopra una foto qualunque il grigio del
 * secondario non regge il contrasto. Anche per `LocalContentColor`, che fuori da una `Surface` vale nero
 * e in un pop-up scuro farebbe un `Text` nudo illeggibile: qui vale `onSurface`, e un `Text` nel corpo
 * e' leggibile senza che l'app lo sappia.
 *
 * Il corpo e' una [ColumnScope], quindi `FluidListGroup`, `FluidCard` e `FluidTextField` ci stanno come
 * in una schermata; sul vetro, dentro un pannello, sono vetro su vetro da soli (vedi la regola 2.13.0).
 *
 * @param icon l'icona della testata, nella piastrella del [tone] (`FluidToneTile`): il tono sta sulla
 *   piastrella e mai sul foglio. Nulla: solo il titolo.
 * @param message una frase sotto il titolo, dentro il corpo (scorre con lui).
 * @param actions i tasti, impilati a tutta larghezza: normalmente `FluidButton(size = Large, fillWidth =
 *   true)`, quello principale in alto — come un foglio di azioni — e l'annulla in fondo. Nulla: niente.
 * @param maxWidth vedi [FluidSheetScaffoldDefaults.MaxWidth].
 * @param scrollable falso per un corpo che scorre da solo (una `LazyColumn`) o che non puo' crescere.
 * @param insetBottom lascia sotto lo spazio della barra di navigazione: vero in un foglio, che sta
 *   attaccato al bordo; **falso in un pop-up**, che sta a mezz'aria, dove sarebbe aria a vuoto.
 */
@Composable
fun FluidSheetScaffold(
  title: String,
  modifier: Modifier = Modifier,
  message: String? = null,
  icon: (@Composable () -> Unit)? = null,
  tone: FluidTone = FluidTone.Primary,
  maxWidth: Dp = FluidSheetScaffoldDefaults.MaxWidth,
  contentPadding: PaddingValues = FluidSheetScaffoldDefaults.ContentPadding,
  scrollable: Boolean = true,
  insetBottom: Boolean = true,
  actions: (@Composable ColumnScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit = {},
) {
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val headerGap = with(density) { FluidSheetScaffoldDefaults.HeaderToBody.roundToPx() }
  val actionsGap = with(density) { FluidSheetScaffoldDefaults.BodyToActions.roundToPx() }

  CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
    // Prima il tetto, poi il riempimento: al contrario `fillMaxWidth` fissa la larghezza minima a
    // quella disponibile e il tetto non ha piu' niente da limitare.
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
      Layout(
        modifier = Modifier
          .then(if (insetBottom) Modifier.navigationBarsPadding() else Modifier)
          .widthIn(max = maxWidth)
          .fillMaxWidth()
          .padding(contentPadding),
        content = {
          Row(
            modifier = Modifier.layoutId(SheetHeaderId).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FluidSheetScaffoldDefaults.HeaderGap),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (icon != null) {
              FluidToneTile(tone = tone, size = FluidSheetScaffoldDefaults.IconTileSize, content = icon)
            }
            Text(
              text = title,
              modifier = Modifier.weight(1f).semantics { heading() },
              style = MaterialTheme.typography.titleLarge,
              color = scheme.onSurface,
            )
          }
          Column(
            modifier = Modifier
              .layoutId(SheetBodyId)
              .fillMaxWidth()
              .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(FluidSheetScaffoldDefaults.BodyGap),
          ) {
            if (!message.isNullOrBlank()) {
              Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
              )
            }
            content()
          }
          if (actions != null) {
            Column(
              modifier = Modifier.layoutId(SheetActionsId).fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(FluidSheetScaffoldDefaults.ActionGap),
              content = actions,
            )
          }
        },
      ) { measurables, constraints ->
        // I figli prendono la larghezza intera e l'altezza che vogliono; e' il corpo, ultimo, a
        // ricevere il tetto: quello che resta dopo la testata e i tasti.
        val loose = constraints.copy(minHeight = 0)
        val header = measurables.first { it.layoutId == SheetHeaderId }.measure(loose)
        val actionsPlaceable = measurables.firstOrNull { it.layoutId == SheetActionsId }?.measure(loose)
        val budget = fluidSheetBodyBudget(
          maxHeight = constraints.maxHeight,
          header = header.height,
          actions = actionsPlaceable?.height ?: 0,
          headerGap = headerGap,
          actionsGap = actionsGap,
        )
        val body = measurables.first { it.layoutId == SheetBodyId }.measure(loose.copy(maxHeight = budget))
        val stack = fluidSheetStack(
          header = header.height,
          body = body.height,
          actions = actionsPlaceable?.height ?: 0,
          headerGap = headerGap,
          actionsGap = actionsGap,
        )
        layout(
          width = constraints.maxWidth.takeIf { it != Constraints.Infinity }
            ?: maxOf(header.width, body.width, actionsPlaceable?.width ?: 0),
          height = stack.height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
          header.placeRelative(0, 0)
          body.placeRelative(0, stack.bodyTop)
          actionsPlaceable?.placeRelative(0, stack.actionsTop)
        }
      }
    }
  }
}

private const val SheetHeaderId = "fluidSheetHeader"
private const val SheetBodyId = "fluidSheetBody"
private const val SheetActionsId = "fluidSheetActions"

/**
 * Il tetto che si da' al corpo quando il foglio non ne ha uno (sta in uno scorrimento, in un test): 32.768
 * pixel, cioe' «nessuno» per ogni contenuto che esista.
 *
 * Non `Constraints.Infinity`: un `verticalScroll` misurato senza altezza massima lancia
 * un'eccezione, ed e' esattamente il corpo del foglio. Con questo tetto il corpo e' alto quanto il suo
 * contenuto, e scorrere non serve.
 */
internal const val FluidSheetUnboundedBodyCap = 32_768

/**
 * Quanta altezza resta al corpo: il tetto meno la testata, i tasti e i due vuoti.
 *
 * Senza un tetto (`Constraints.Infinity`: il foglio sta in uno scorrimento) vale
 * [FluidSheetUnboundedBodyCap]. Il vuoto fra il corpo e i tasti conta solo se i tasti ci sono, e quello
 * sotto la testata si conta sempre: un corpo vuoto riserva poche decine di pixel di troppo, ed e'
 * l'errore giusto da fare — il contrario taglia l'ultima riga.
 */
internal fun fluidSheetBodyBudget(
  maxHeight: Int,
  header: Int,
  actions: Int,
  headerGap: Int,
  actionsGap: Int,
): Int {
  if (maxHeight == Constraints.Infinity) return FluidSheetUnboundedBodyCap
  val reserved = header + headerGap + if (actions > 0) actions + actionsGap else 0
  return (maxHeight - reserved).coerceAtLeast(0)
}

/** Dove stanno il corpo e i tasti, e quanto e' alto il tutto. */
internal data class FluidSheetStack(val bodyTop: Int, val actionsTop: Int, val height: Int)

/**
 * La pila verticale: testata, corpo, tasti, con un vuoto fra due cose che ci sono davvero.
 *
 * Un corpo vuoto non lascia un vuoto sotto la testata, e dei tasti assenti non lasciano un vuoto in
 * fondo: un foglio con solo un titolo e due tasti non ha un buco in mezzo.
 */
internal fun fluidSheetStack(
  header: Int,
  body: Int,
  actions: Int,
  headerGap: Int,
  actionsGap: Int,
): FluidSheetStack {
  val bodyTop = header + if (body > 0) headerGap else 0
  val actionsTop = bodyTop + body + if (actions > 0) actionsGap else 0
  return FluidSheetStack(bodyTop = bodyTop, actionsTop = actionsTop, height = actionsTop + actions)
}

/**
 * Un foglio di conferma: un titolo, una frase, e due tasti — **conferma** e **annulla** —, in un pop-up
 * di vetro che sale dal basso.
 *
 * E' `FluidGlassModalPortal` + [FluidSheetScaffold], e basta: le quattro conferme che le app si erano
 * scritte da sole (eliminare, condividere, usare i dati mobili, approvare) erano tutte questo, piu' un
 * corpo. Il corpo ([content]) e' il posto per quello che la decisione ha di suo — un elenco di effetti,
 * una stima — e va **sul foglio** con le sue righe e le sue schede, non in una frase piu' lunga.
 *
 * **Il tasto di conferma e' uno solo e dice cosa fa** ("Elimina dal telefono", non "OK"). Con
 * [destructive] e' `FluidButtonStyle.Destructive` e la piastrella dell'icona prende il tono `Danger`:
 * l'engine non ha un secondo registro per le azioni che non si disfano, ha il rosso, e vale al piu' una
 * volta per schermata. L'annulla e' `Tinted`, sotto, in fondo come in un foglio di azioni.
 *
 * **La conferma non chiude il foglio, la chiude chi lo possiede.** [visible] e' dell'app, come in ogni
 * portale: `onConfirm = { fai(); aperto = false }`. Chiudere da qui avrebbe voluto dire che [onDismissRequest]
 * vale due cose, "annulla" e "ho finito", e chi deve distinguerle non potrebbe.
 *
 * **Le parole restano mentre esce.** Titolo, frase ed etichette si congelano all'ultimo valore visibile:
 * un'app che calcola la frase dall'oggetto da confermare (`"Eliminare ${scelto?.nome}?"`) azzera
 * l'oggetto nello stesso istante in cui chiude, e il foglio che sta uscendo diventava «Eliminare ?». Non
 * si congelano [icon] e [content], che sono lambda composable e si rileggono vive: se dipendono
 * dall'oggetto, apri il portale con la sua forma a `item` e usa [FluidSheetScaffold] direttamente.
 *
 * Senza un `FluidGlassModalHost` in scope non si apre e non va in crash (e' la regola del portale).
 *
 * @param tone il tono della piastrella; di serie `Danger` se [destructive], altrimenti `Primary`.
 * @param presentation `Sheet` di serie; un `Popover` con [origin] nasce dal tasto che l'ha aperto (e
 *   allora il foglio non lascia lo spazio della barra di navigazione, che a mezz'aria sarebbe vuoto).
 */
@Composable
fun FluidConfirmSheet(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  title: String,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
  message: String? = null,
  icon: (@Composable () -> Unit)? = null,
  tone: FluidTone? = null,
  confirmLabel: String = "Conferma",
  cancelLabel: String = "Annulla",
  destructive: Boolean = false,
  confirmEnabled: Boolean = true,
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Sheet,
  origin: () -> Rect? = { null },
  content: @Composable ColumnScope.() -> Unit = {},
) {
  val current = FluidConfirmWords(title, message, confirmLabel, cancelLabel, destructive, tone, confirmEnabled)
  // Un contenitore semplice e non uno stato: lo si scrive e lo si rilegge nella stessa composizione, e
  // scrivere uno stato mentre lo si e' gia' letto e' proprio cio' che il portale a `item` evita.
  val kept = remember { FluidKeptWords(current) }
  if (visible) kept.words = current
  val words = kept.words

  FluidGlassModalPortal(
    visible = visible,
    onDismissRequest = onDismissRequest,
    origin = origin,
    presentation = presentation,
    paneTitle = words.title,
  ) {
    FluidSheetScaffold(
      title = words.title,
      modifier = modifier,
      message = words.message,
      icon = icon,
      tone = fluidConfirmTone(words.destructive, words.tone),
      insetBottom = fluidModalSlidesUp(presentation),
      actions = {
        FluidButton(
          text = words.confirmLabel,
          onClick = onConfirm,
          style = if (words.destructive) FluidButtonStyle.Destructive else FluidButtonStyle.Filled,
          size = FluidButtonSize.Large,
          enabled = words.confirmEnabled,
          fillWidth = true,
        )
        FluidButton(
          text = words.cancelLabel,
          onClick = onDismissRequest,
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Large,
          fillWidth = true,
        )
      },
      content = content,
    )
  }
}

/** Il tono della piastrella di una conferma: quello chiesto, altrimenti `Danger` se distruttiva. */
internal fun fluidConfirmTone(destructive: Boolean, requested: FluidTone?): FluidTone =
  requested ?: if (destructive) FluidTone.Danger else FluidTone.Primary

/** Le parole di un foglio di conferma, quelle che si congelano mentre il foglio esce. */
@Immutable
internal data class FluidConfirmWords(
  val title: String,
  val message: String?,
  val confirmLabel: String,
  val cancelLabel: String,
  val destructive: Boolean,
  val tone: FluidTone?,
  val confirmEnabled: Boolean,
)

internal class FluidKeptWords(var words: FluidConfirmWords)
