/*
 * engine-viewer-ocr (4.0, F8.17): i comandi di «Leggi testo» nello spazio dello schermo (l'accessorio della pagina del
 * visore): il tasto di Testo attivo, «Copia tutto», «Testo» e il foglio con l'elenco delle righe e la ricerca. Codice del
 * Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidDetectedText
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.rememberEmptyGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.icons.DocumentScanner
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons

/**
 * I comandi di «Leggi testo» per [state], da mettere nell'accessorio della pagina del visore
 * (`FluidViewerPageAccessory`) o in un posto fermo dello schermo di un visore dell'app. Compaiono quando la lettura ha
 * trovato parole (come il tasto di Testo attivo, che c'è solo se c'è testo), con la sostituzione sfocata di chi arriva
 * da solo; col movimento ridotto subito.
 *
 * La riga sta sempre nella larghezza che riceve (F8.17b: su un telefono a 360 dp, e col testo grande, usciva dallo
 * schermo e schiacciava il tasto): vedi [LiveTextRow].
 */
@Composable
fun FluidLiveTextControls(
  state: FluidLiveTextState,
  modifier: Modifier = Modifier,
  strings: FluidLiveTextStrings = FluidLiveText.strings,
) {
  val motion = LocalFluidMotionPolicy.current
  val shown by animateFloatAsState(if (state.hasText) 1f else 0f,
    if (motion.reducedMotion) tween(0) else tween(AppearMillis), label = "LiveTextControls")
  if (shown <= 0f) return
  LiveTextRow(state, strings, modifier.graphicsLayer {
    // Arriva da solo (l'app ha finito di leggere): sfocato e più piccolo, poi nitido, come le comparse di R7.
    alpha = shown
    val s = 0.85f + 0.15f * shown
    scaleX = s; scaleY = s
    if (shown < 1f && !motion.reducedMotion) renderEffect = androidx.compose.ui.graphics.BlurEffect(12f * (1f - shown), 12f * (1f - shown))
  })
  LiveTextSheet(state, strings)
}

/**
 * La riga dei comandi, dentro la larghezza che riceve. Da destra, in ordine di importanza:
 * - il tasto di Testo attivo, sempre e sempre intero (è l'interruttore: non si stringe mai);
 * - «Testo», se ci sta: apre l'elenco delle righe, che ha anche «Copia tutto» e i dati toccabili;
 * - «Copia tutto», solo se dopo di lui resta posto per un'azione rapida di [QuickRoom] (o se l'azione rapida non c'è);
 * - l'azione rapida (il numero, la mail, il link), a sinistra, nel posto che resta, su una riga coi puntini se è lunga;
 *   sotto [QuickMinWidth] non compare (è anche fra le righe del foglio «Testo»).
 *
 * Le larghezze di «Testo» e «Copia tutto» si misurano su tasti di prova mai posati e senza semantica (come le pillole di
 * `FluidFilterChips`): un comando che non ci sta non viene composto, e TalkBack non lo trova.
 */
@Composable
private fun LiveTextRow(state: FluidLiveTextState, strings: FluidLiveTextStrings, modifier: Modifier) {
  val context = LocalContext.current
  // L'azione rapida di Testo attivo (WWDC22 10026): il primo numero, mail o link trovato, da toccare.
  val quick = if (state.active) androidx.compose.runtime.remember(state.text) { state.text?.let(::quickAction) } else null
  val probeBackdrop = rememberEmptyGlassBackdrop()
  SubcomposeLayout(modifier) { constraints ->
    val gap = LiveTextRowGap.roundToPx()
    val max = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
    val loose = Constraints(maxWidth = max, maxHeight = constraints.maxHeight)
    val toggle = subcompose(LiveTextSlot.Toggle) { LiveTextToggle(state, strings) }.map { it.measure(loose) }
    var used = toggle.sumOf { it.width }
    var withText = false
    var withCopy = false
    if (state.active) {
      val probes = subcompose(LiveTextSlot.Probes) {
        FluidGlassButton(strings.showText, {}, Modifier.clearAndSetSemantics {}, backdrop = probeBackdrop, maxLines = 1)
        FluidGlassButton(strings.copyAll, {}, Modifier.clearAndSetSemantics {}, backdrop = probeBackdrop, maxLines = 1)
      }.map { it.measure(loose) }
      val textWidth = probes.getOrNull(0)?.width ?: 0
      val copyWidth = probes.getOrNull(1)?.width ?: 0
      if (used.toLong() + gap + textWidth <= max) { withText = true; used += gap + textWidth }
      val room = if (quick != null) gap + QuickRoom.roundToPx() else 0
      if (used.toLong() + gap + copyWidth + room <= max) { withCopy = true; used += gap + copyWidth }
    }
    var quickButton: List<Placeable> = emptyList()
    if (quick != null) {
      val remaining = (max.toLong() - used - gap).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
      if (remaining >= QuickMinWidth.roundToPx()) {
        quickButton = subcompose(LiveTextSlot.Quick) {
          FluidGlassButton(quick.label, {
            runCatching { context.startActivity(quick.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
          }, modifier = Modifier.testTag(FluidLiveTextTags.QuickAction), maxLines = 1)
        }.map { it.measure(loose.copy(maxWidth = remaining)) }
      }
    }
    val right = ArrayList<Placeable>(3)
    if (withText) right += subcompose(LiveTextSlot.ShowText) {
      FluidGlassButton(strings.showText, { state.sheet = true }, modifier = Modifier.testTag(FluidLiveTextTags.ShowText), maxLines = 1)
    }.map { it.measure(loose) }
    if (withCopy) right += subcompose(LiveTextSlot.CopyAll) {
      FluidGlassButton(strings.copyAll, { state.text?.let { copy(context, it.text) } },
        modifier = Modifier.testTag(FluidLiveTextTags.CopyAll), maxLines = 1)
    }.map { it.measure(loose) }
    right += toggle
    val all = quickButton + right
    val height = all.maxOf { it.height }
    val rightWidth = right.sumOf { it.width } + gap * (right.size - 1)
    val quickWidth = quickButton.sumOf { it.width }
    // Come VisionKit (WWDC22 10026, `riferimenti/wwdc22-livetext-tasto-attivo.png`): l'azione rapida nell'angolo in
    // basso a sinistra, il tasto in quello a destra. Senza larghezza data, la riga è stretta quanto i comandi.
    val natural = rightWidth + if (quickButton.isEmpty()) 0 else quickWidth + gap
    val width = if (quickButton.isNotEmpty() && constraints.hasBoundedWidth) constraints.maxWidth else natural
    layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
      for (p in quickButton) p.placeRelative(0, (height - p.height) / 2)
      var x = width - rightWidth
      for (p in right) {
        p.placeRelative(x, (height - p.height) / 2)
        x += p.width + gap
      }
    }
  }
}

/** Il tasto di Testo attivo: un interruttore di vetro con l'icona dello scanner. */
@Composable
private fun LiveTextToggle(state: FluidLiveTextState, strings: FluidLiveTextStrings) {
  FluidGlassIconButton(
    onClick = {
      state.active = !state.active
      if (!state.active) { state.selection = null; state.query = "" }
    },
    selected = state.active,
    modifier = Modifier.testTag(FluidLiveTextTags.Button).semantics {
      contentDescription = strings.liveText
      stateDescription = if (state.active) strings.on else strings.off
      role = Role.Switch
    },
  ) {
    FluidIcon(FluidIcons.DocumentScanner, null)
  }
}

/** I posti della riga (le chiavi di `subcompose`). */
private object LiveTextSlot {
  const val Toggle = "toggle"
  const val Probes = "probes"
  const val Quick = "quick"
  const val ShowText = "show-text"
  const val CopyAll = "copy-all"
}

/** Lo spazio fra i comandi: 10 dp, quello della fila di prima (a occhio, come le capsule della barra in basso). */
private val LiveTextRowGap = 10.dp

/**
 * Quanto posto deve restare all'azione rapida perché «Copia tutto» stia nella riga: 160 dp, una mail o un numero
 * interi su un telefono. A occhio: con meno, la mail di una circolare («segreteria@…») si leggeva solo a metà.
 */
private val QuickRoom = 160.dp

/** Sotto questa larghezza l'azione rapida non compare: 80 dp, cinque o sei caratteri coi puntini. A occhio. */
private val QuickMinWidth = 80.dp

/**
 * «Testo trovato»: il campo per cercare nelle parole trovate (con «3/12» e le frecce) e l'elenco delle righe, con numeri,
 * indirizzi, mail e link toccabili. Toccare una riga la seleziona sull'immagine. È anche l'elenco per TalkBack.
 */
@Composable
private fun LiveTextSheet(state: FluidLiveTextState, strings: FluidLiveTextStrings) {
  val text = state.text ?: return
  val context = LocalContext.current
  FluidGlassSheet(visible = state.sheet, onDismissRequest = { state.sheet = false },
    detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large), paneTitle = strings.sheetTitle) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(FluidLiveTextTags.Sheet),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(strings.sheetTitle, Modifier.weight(1f).semantics { heading() },
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        FluidGlassButton(strings.copyAll, { copy(context, text.text) })
      }
      val matches = state.matches
      FluidSearchField(
        query = state.query,
        onQueryChange = { state.query = it; state.current = 0 },
        placeholder = strings.search,
        autoFocus = false,
        navigation = if (state.query.isNotBlank()) FluidSearchNavigation(
          state.current.coerceIn(0, maxOf(0, matches.lastIndex)), matches.size,
          { if (matches.isNotEmpty()) state.current = (state.current + matches.size - 1) % matches.size },
          { if (matches.isNotEmpty()) state.current = (state.current + 1) % matches.size },
        ) else null,
      )
      val hitLines = matches.map { text.lineOfWord[it.first] }.toSet()
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(text.lines) { i, line ->
          val hit = i in hitLines
          Box(Modifier.fillMaxWidth().clip(ContinuousCornerShape(10.dp))
            .background(if (hit) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f))
            .fluidPressable(onClick = {
              val first = text.firstWordOfLine[i]
              state.selection = FluidTextMatch(first, first + line.words.size - 1)
              state.active = true
              state.sheet = false
            }, role = Role.Button)
            .padding(horizontal = 10.dp, vertical = 8.dp)) {
            FluidDetectedText(line.text, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    }
  }
}

/** La comparsa dei comandi quando la lettura finisce: 300 ms. A occhio (le comparse sfocate di R7 stanno fra 250 e 430). */
private const val AppearMillis = 300

/** L'azione rapida: cosa dice la capsula e l'intent che apre. */
internal class LiveTextQuickAction(val label: String, val intent: android.content.Intent)

/**
 * La prima azione rapida del testo, come le azioni rapide di VisionKit: un numero di telefono (Chiama), una mail o un
 * link. Le regole sono strette di proposito (un numero ha almeno 8 cifre): meglio nessuna capsula che una sbagliata.
 */
internal fun quickAction(text: FluidRecognizedText): LiveTextQuickAction? {
  for (line in text.lines) {
    val s = line.text
    Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""").find(s)?.let { m ->
      return LiveTextQuickAction(m.value, android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${m.value}")))
    }
    Regex("""(https?://\S+|www\.[\w-]+(\.[\w-]+)+\S*)""").find(s)?.let { m ->
      val url = m.value.trimEnd('.', ',', ';')
      return LiveTextQuickAction(url, android.content.Intent(android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(if (url.startsWith("http")) url else "https://$url")))
    }
    Regex("""\+?\d[\d ./-]{6,}\d""").find(s)?.let { m ->
      val digits = m.value.filter { it.isDigit() || it == '+' }
      if (digits.count { it.isDigit() } >= 8) {
        return LiveTextQuickAction(m.value, android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$digits")))
      }
    }
  }
  return null
}
