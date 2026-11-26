package dev.antigravity.fluidengine.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidArrivingText
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidPagerDots
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlinx.coroutines.launch

/** I tag delle prove della vetrina. */
object FluidWidgetShowcaseTags {
  const val Add: String = "fluid-widget:aggiungi"
  const val Message: String = "fluid-widget:esito"
  const val Pager: String = "fluid-widget:taglie"

  fun card(title: String): String = "fluid-widget:scheda:$title"
  fun preview(size: String): String = "fluid-widget:anteprima:$size"
}

/** Le misure della vetrina. */
object FluidWidgetShowcaseDimens {
  /** Il margine dentro la scheda e attorno al widget sulla «Home»: 16, il margine dei widget di Apple (HIG «Widgets»: «16 points for most widgets»). */
  val Padding: Dp = 16.dp

  /** Lo spazio fra le schede. */
  val Gap: Dp = 16.dp
}

/**
 * La vetrina dei widget dell'app: una scheda per widget, con **l'anteprima vera** a ogni taglia, le taglie da sfogliare
 * col dito e il tasto «Aggiungi alla Home» che apre la richiesta del launcher. Solo i widget: il riquadro delle
 * Impostazioni rapide non sta mai qui (scelta di Alessio, 26/11/2026), si propone dalle impostazioni dell'app con
 * [FluidQuickTileCard].
 *
 * Il riferimento è la galleria dei widget di iPhone (Apple non ce l'ha dentro le app: su iOS i widget si aggiungono solo
 * da lì): il nome e la frase in alto, il widget al centro come sarà sulla Home, le taglie da scorrere coi punti sotto, e
 * «Aggiungi widget» in basso (guida di iPhone, «Add, edit, and remove widgets»: «swipe through the size and style
 * options… tap Add Widget»). Su Android la richiesta esiste davvero, `requestPinAppWidget`: quasi nessuno sa che un'app
 * ha dei widget, e così si scoprono da dentro l'app.
 *
 * Le taglie seguono il dito e restano nitide (regola di F8, «dito nitido»); l'esito sotto il tasto arriva sfocato, perché
 * è l'app a cambiarlo. Con «Riduci movimento» l'esito compare fermo e le azioni di TalkBack cambiano taglia senza
 * scorrere.
 *
 * @param appName il nome dell'app, per la strada a mano quando il launcher non permette la richiesta.
 * @param home il fondo «Home» dietro le anteprime ([FluidWidgetShowcaseCard]).
 */
@Composable
fun FluidWidgetShowcase(
  entries: List<FluidWidgetEntry>,
  appName: String,
  modifier: Modifier = Modifier,
  strings: FluidWidgetShowcaseStrings = FluidWidgetShowcaseStrings(),
  home: (@Composable () -> Unit)? = null,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(FluidWidgetShowcaseDimens.Gap)) {
    for (entry in entries) {
      key(entry.provider) {
        FluidWidgetShowcaseCard(entry, appName, strings = strings, home = home)
      }
    }
  }
}

/**
 * La scheda di un widget: nome e frase, l'anteprima per taglia sulla «Home», i punti delle taglie, il tasto e l'esito.
 *
 * @param home il fondo su cui sta l'anteprima, perché si legga come un widget sulla Home e non come un riquadro della
 * pagina (lo sfondo della Home, una foto); riempie la «Home». Di serie una sfumatura dell'accento e del terziario.
 */
@Composable
fun FluidWidgetShowcaseCard(
  entry: FluidWidgetEntry,
  appName: String,
  modifier: Modifier = Modifier,
  strings: FluidWidgetShowcaseStrings = FluidWidgetShowcaseStrings(),
  home: (@Composable () -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val pager = rememberPagerState { entry.sizes.size }
  val scope = rememberCoroutineScope()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  // La pagina corrente cambia a meta' di uno scorrimento, non a ogni fotogramma: leggerla qui non ricompone il moto.
  val size = entry.sizes[pager.currentPage]
  val pin = rememberFluidWidgetPin(size.provider ?: entry.provider)

  fun goTo(page: Int) {
    val target = page.coerceIn(0, entry.sizes.lastIndex)
    scope.launch { if (reduced) pager.scrollToPage(target) else pager.animateScrollToPage(target) }
  }

  val homeBrush = remember(colors.primary, colors.tertiary, colors.surface) {
    Brush.linearGradient(
      listOf(
        colors.primary.copy(alpha = HomeTint).compositeOver(colors.surface),
        colors.tertiary.copy(alpha = HomeTint).compositeOver(colors.surface),
      ),
    )
  }

  Column(
    modifier
      .fillMaxWidth()
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .background(colors.surfaceContainerLow)
      .padding(FluidWidgetShowcaseDimens.Padding)
      .testTag(FluidWidgetShowcaseTags.card(entry.title))
      .semantics {
        customActions = listOfNotNull(
          CustomAccessibilityAction(strings.nextSize) { goTo(pager.currentPage + 1); true }
            .takeIf { entry.sizes.size > 1 },
          CustomAccessibilityAction(strings.previousSize) { goTo(pager.currentPage - 1); true }
            .takeIf { entry.sizes.size > 1 },
        )
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      entry.title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = colors.onSurface,
      textAlign = TextAlign.Center,
    )
    Text(
      entry.description,
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 2.dp),
    )
    Spacer(Modifier.height(12.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val inner = maxWidth - FluidWidgetShowcaseDimens.Padding * 2
      // L'altezza della «Home» e' quella della taglia piu' alta, rimpicciolita come la mostrera' l'anteprima: fissa,
      // cosi' sfogliare le taglie non fa saltare la pagina.
      val tallest = entry.sizes.maxOf { s -> s.height * fluidWidgetPreviewScale(s.dpSize, inner) }
      Box(
        Modifier
          .fillMaxWidth()
          .height(tallest + FluidWidgetShowcaseDimens.Padding * 2)
          .clip(ContinuousCornerShape(FluidRadius.Card))
          .let { if (home == null) it.background(homeBrush) else it },
      ) {
        if (home != null) Box(Modifier.matchParentSize()) { home() }
        HorizontalPager(
          state = pager,
          modifier = Modifier.fillMaxSize().testTag(FluidWidgetShowcaseTags.Pager),
        ) { page ->
          val pageSize = entry.sizes[page]
          FluidWidgetPreviewBox(
            entry = entry,
            size = pageSize,
            contentDescription = strings.preview(entry.title, pageSize.label),
            modifier = Modifier.fillMaxSize().padding(FluidWidgetShowcaseDimens.Padding),
          )
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    FluidPagerDots(
      pager,
      pageLabel = { page, _ ->
        val s = entry.sizes[(page - 1).coerceIn(0, entry.sizes.lastIndex)]
        strings.sizeOf(s.label, s.columns, s.rows)
      },
    )
    Text(
      strings.sizeOf(size.label, size.columns, size.rows),
      style = MaterialTheme.typography.labelMedium,
      color = colors.onSurfaceVariant,
      modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(12.dp))
    FluidButton(
      text = strings.add,
      onClick = { pin.request() },
      fillWidth = true,
      modifier = Modifier.testTag(FluidWidgetShowcaseTags.Add),
      leading = { Icon(Icons.Rounded.Add, contentDescription = null) },
    )
    val message = strings.messageFor(pin.state, appName)
      ?: strings.resizeHint.takeIf { entry.sizes.size > 1 && size.provider == null }
    if (message != null) {
      FluidArrivingText(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (pin.state is FluidWidgetPinState.Added) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().testTag(FluidWidgetShowcaseTags.Message),
      )
    }
  }
}

/** Quanto l'accento tinge la «Home» dietro l'anteprima. A occhio, 26/11/2026: si vede che è un fondo, non compete col widget. */
private const val HomeTint = 0.22f
