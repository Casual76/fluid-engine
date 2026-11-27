package dev.antigravity.fluidengine.aiui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.memory.AiMemoryNotice
import dev.antigravity.fluidengine.aiui.chat.FluidPillAction
import dev.antigravity.fluidengine.ui.fluid.FluidArrivingText
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationAction
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.History
import dev.antigravity.fluidengine.ui.fluid.icons.Psychology
import dev.antigravity.fluidengine.ui.fluid.icons.Undo
import dev.antigravity.fluidengine.ui.theme.FluidCard

/**
 * **«Vuoi che lo ricordi?» o «Ho ricordato…» con Annulla** (F9.3, F9.3b, `ricordi-dell-assistente`): la card sotto la
 * risposta che dice cosa l'assistente vuole ricordare o ha appena messo nella memoria, perche' niente si ricordi senza
 * che l'utente lo veda.
 *
 * - **La domanda** (di serie, scelta di Alessio del 26/11/2026): «Vuoi che lo ricordi?» col fatto e, sotto, Ricorda e
 *   No ([onAnswer]); da un'altra app «ClasseViva vuole che lo ricordi». Se il fatto ne sostituisce uno, sotto c'e' «al
 *   posto di: …». Dopo No la card dice «Va bene, non lo ricordo»; dopo Ricorda l'avviso diventa «Ho ricordato» con
 *   Annulla. Col salvataggio automatico acceso la domanda non c'e' e la card nasce «Ho ricordato».
 * - **Le tre forme**: un fatto nuovo («Ho ricordato: è in 4F al liceo Galilei»), uno che ne sostituisce un altro («Ho
 *   aggiornato: è in 5F», con sotto «prima: è in 4F»: la contraddizione detta), e quello che non entra perche' ce n'e'
 *   uno piu' recente («Tengo il più recente»). Da un'altra app: «Ho ricordato · da ClasseViva».
 * - **Annulla** ([onUndo]) c'e' solo finche' si puo' annullare ([AiMemoryNotice.canUndo]); dopo, la card dice «Non lo
 *   ricordo più». Un tocco sulla card ([onOpen]) apre «Cosa ricordo di te», dove si corregge.
 * - **Il movimento**: la parola in testa cambia identita' quando si annulla, quindi arriva sfocata e si mette a fuoco
 *   (`FluidArrivingText`, la regola R7: cio' che l'app cambia da sola si sfoca, cio' che muove il dito no); il fatto
 *   arriva cosi' la prima volta. Con «Riduci movimento» tutto e' fermo: il testo cambia di colpo e la card non anima la
 *   sua altezza.
 * - **Il vetro**: la card e' vetro di contenuto dell'engine ([glass], `FluidCard`), che campiona il fondale una volta e
 *   non ridisegna da ferma; in una lista lunga di chat costa come una card.
 * - **TalkBack**: un nodo solo che si annuncia quando compare («Ho ricordato: è in 4F al liceo Galilei»), con l'azione
 *   «Annulla» (o «Ricorda» e «No» per la domanda); i tasti restano tasti da 48 dp.
 */
@Composable
fun FluidMemoryNoticeCard(
  notice: AiMemoryNotice,
  onUndo: () -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidMemoryStrings = FluidMemoryStrings(),
  onOpen: (() -> Unit)? = null,
  glass: Boolean = true,
  onAnswer: (accept: Boolean) -> Unit = {},
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val headline = FluidMemoryTexts.headline(notice, strings)
  val detail = FluidMemoryTexts.detail(notice, strings)
  val label = FluidMemoryTexts.accessibilityLabel(notice, strings)
  val canUndo = notice.canUndo
  val asking = notice.asking
  val quiet = notice.undone || notice.declined
  val actions = remember(canUndo, asking, strings, onUndo, onAnswer) {
    when {
      asking -> listOf(
        CustomAccessibilityAction(strings.accept) { onAnswer(true); true },
        CustomAccessibilityAction(strings.decline) { onAnswer(false); true },
      )
      canUndo -> listOf(CustomAccessibilityAction(strings.undo) { onUndo(); true })
      else -> emptyList()
    }
  }
  FluidCard(
    modifier = modifier.semantics {
      contentDescription = label
      liveRegion = LiveRegionMode.Polite
      customActions = actions
    },
    onClick = onOpen,
    animateContent = !reduced,
    glass = glass,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FluidIcon(
        imageVector = if (notice.kind == AiMemoryNotice.KindUpdated) FluidIcons.History else FluidIcons.Psychology,
        contentDescription = null,
        tint = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FluidArrivingText(
          text = headline,
          style = MaterialTheme.typography.labelLarge,
          color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
          animateFirst = false,
        )
        FluidArrivingText(
          text = notice.text,
          style = MaterialTheme.typography.bodyMedium,
          color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
          animateFirst = !reduced,
        )
        if (detail != null) {
          Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (asking) {
          // Sotto il fatto e non a fianco: due tasti accanto a una frase lunga la stringerebbero a una parola per riga.
          Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidPillAction(icon = FluidIcons.Check, label = strings.accept, onClick = { onAnswer(true) })
            FluidPillAction(icon = FluidIcons.Close, label = strings.decline, onClick = { onAnswer(false) })
          }
        }
      }
      if (canUndo) {
        FluidPillAction(
          icon = FluidIcons.Undo,
          label = strings.undo,
          onClick = onUndo,
          modifier = Modifier.padding(start = 4.dp),
        )
      }
    }
  }
}

/** Gli avvisi della memoria come notifica di vetro dell'engine, per chi non ha una chat sotto gli occhi. */
object FluidMemoryNotices {
  /** Il gruppo delle notifiche della memoria: una nuova prende il posto della vecchia, senza fare la coda. */
  const val Group = "ai-memory"

  /** Quanto resta a schermo: **8 s**, il tempo di leggere un fatto e decidere se annullarlo (a occhio, 26/11/2026). */
  const val DurationMillis = 8_000L

  /**
   * La notifica di vetro (`FluidNotificationHost`) per [notice]: in un'altra app che ha proposto un ricordo
   * («ClasseViva vuole che lo ricordi», «Ho ricordato · da ClasseViva»), o sopra l'overlay dell'assistente. In linea,
   * subito toccabili, Ricorda e No per una domanda ([onAnswer]) o Annulla se si puo' annullare. Una domanda non
   * scade da sola: se la notifica sparisce, resta fra quelle «Da confermare» della pagina.
   */
  fun notification(
    notice: AiMemoryNotice,
    onUndo: () -> Unit,
    strings: FluidMemoryStrings = FluidMemoryStrings(),
    onAnswer: (accept: Boolean) -> Unit = {},
  ): FluidNotification {
    val detail = FluidMemoryTexts.detail(notice, strings)
    val inline = when {
      notice.asking -> listOf(FluidNotificationAction(strings.accept) { onAnswer(true) }, FluidNotificationAction(strings.decline) { onAnswer(false) })
      notice.canUndo -> listOf(FluidNotificationAction(strings.undo, onUndo))
      else -> emptyList()
    }
    return FluidNotification(
      id = notice.id,
      title = FluidMemoryTexts.headline(notice, strings),
      message = if (detail == null) notice.text else "${notice.text} · $detail",
      tone = FluidNotificationTone.Info,
      durationMillis = DurationMillis,
      inlineActions = inline,
      replaceGroup = Group,
    )
  }
}
