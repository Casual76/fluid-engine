package dev.antigravity.fluidengine.widget

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidArrivingText
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidRadius

// F8.25b · Il riquadro delle Impostazioni rapide: un pezzo per la pagina delle impostazioni dell'app, separato dalla
// vetrina dei widget (FluidWidgetShowcase), dove non va mai.

/**
 * Un riquadro delle Impostazioni rapide dell'app, da proporre **dalla pagina delle impostazioni dell'app**
 * (`StatusBarManager.requestAddTileService`, da Android 13), mai dalla vetrina dei widget (scelta di Alessio del
 * 26/11/2026: la vetrina mostra solo i widget). [service] è il `TileService` dell'app (quello di Android, non le Tile
 * dell'orologio).
 */
@Immutable
data class FluidQuickTileOffer(
  val service: ComponentName,
  val label: String,
  val description: String,
  @DrawableRes val icon: Int,
)

/** L'esito della proposta del riquadro rapido. */
sealed interface FluidQuickTileState {
  data object Idle : FluidQuickTileState
  data object Asking : FluidQuickTileState
  data object Added : FluidQuickTileState
  data object AlreadyAdded : FluidQuickTileState
  data object NotAdded : FluidQuickTileState

  /** Android prima del 13, o il sistema ha rifiutato la richiesta. */
  data object Unsupported : FluidQuickTileState
}

/** Le parole del riquadro rapido, in italiano semplice. Un'app in un'altra lingua passa le sue. */
data class FluidQuickTileStrings(
  val add: String = "Aggiungi alle Impostazioni rapide",
  val added: String = "Nelle Impostazioni rapide.",
  val alreadyAdded: String = "È già nelle Impostazioni rapide.",
  val notAdded: String = "Non aggiunto.",
  val unsupported: String = "Si può proporre da Android 13: aprilo dalle Impostazioni rapide, tocca la matita e trascinalo su.",
)

/** La frase per l'esito del riquadro rapido, o `null` se non c'è niente da dire. */
fun FluidQuickTileStrings.messageFor(state: FluidQuickTileState): String? = when (state) {
  FluidQuickTileState.Idle, FluidQuickTileState.Asking -> null
  FluidQuickTileState.Added -> added
  FluidQuickTileState.AlreadyAdded -> alreadyAdded
  FluidQuickTileState.NotAdded -> notAdded
  FluidQuickTileState.Unsupported -> unsupported
}

/** I tag delle prove del riquadro rapido. */
object FluidQuickTileTags {
  const val Card: String = "fluid-quick-tile:scheda"
  const val Add: String = "fluid-quick-tile:aggiungi"
  const val Message: String = "fluid-quick-tile:esito"
}

/**
 * La richiesta del riquadro rapido, senza interfaccia. Qui, a differenza del widget, la risposta torna sempre: il
 * sistema la manda sul filo principale.
 */
@Stable
class FluidQuickTileRequest internal constructor(private val context: Context, val offer: FluidQuickTileOffer) {
  var state: FluidQuickTileState by mutableStateOf(FluidQuickTileState.Idle)
    private set

  /** Vero da Android 13. */
  val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

  fun request() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      state = FluidQuickTileState.Unsupported
      return
    }
    val manager = context.getSystemService(StatusBarManager::class.java)
    if (manager == null) {
      state = FluidQuickTileState.Unsupported
      return
    }
    state = FluidQuickTileState.Asking
    manager.requestAddTileService(
      offer.service,
      offer.label,
      AndroidIcon.createWithResource(context, offer.icon),
      ContextCompat.getMainExecutor(context),
    ) { result -> state = quickTileStateOf(result) }
  }

  companion object {
    /** Il codice di `StatusBarManager` come esito: 2 aggiunto, 1 già c'era, 0 non aggiunto, negativo un errore. */
    fun quickTileStateOf(result: Int): FluidQuickTileState = when (result) {
      2 -> FluidQuickTileState.Added
      1 -> FluidQuickTileState.AlreadyAdded
      0 -> FluidQuickTileState.NotAdded
      else -> FluidQuickTileState.Unsupported
    }
  }
}

/**
 * La scheda della proposta del riquadro rapido, per la pagina delle impostazioni dell'app: l'icona del riquadro, il nome,
 * una frase e il tasto «Aggiungi alle Impostazioni rapide»; sotto, l'esito (che qui torna sempre) o, prima di Android 13,
 * la strada a mano. La frase arriva sfocata come ogni testo che cambia l'app, ferma con «Riduci movimento».
 */
@Composable
fun FluidQuickTileCard(
  offer: FluidQuickTileOffer,
  modifier: Modifier = Modifier,
  strings: FluidQuickTileStrings = FluidQuickTileStrings(),
) {
  val context = LocalContext.current
  val request = remember(context, offer) { FluidQuickTileRequest(context, offer) }
  val colors = MaterialTheme.colorScheme
  Column(
    modifier
      .fillMaxWidth()
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .background(colors.surfaceContainerLow)
      .padding(FluidWidgetShowcaseDimens.Padding)
      .testTag(FluidQuickTileTags.Card),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier
          .size(QuickTileIconBox)
          .clip(ContinuousCornerShape(FluidRadius.Control))
          .background(colors.primary),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painterResource(offer.icon),
          contentDescription = null,
          colorFilter = ColorFilter.tint(colors.onPrimary),
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(offer.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Text(offer.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
      }
    }
    Spacer(Modifier.height(12.dp))
    FluidButton(
      text = strings.add,
      onClick = { request.request() },
      style = FluidButtonStyle.Tinted,
      fillWidth = true,
      modifier = Modifier.testTag(FluidQuickTileTags.Add),
    )
    val message = strings.messageFor(request.state)
      ?: strings.unsupported.takeIf { !request.isSupported }
    if (message != null) {
      FluidArrivingText(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().testTag(FluidQuickTileTags.Message),
      )
    }
  }
}

/** La tessera dell'icona del riquadro: 44 dp, il bersaglio minimo di Apple. */
private val QuickTileIconBox = 44.dp
