@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.chat.FluidAssistantBubble
import dev.antigravity.fluidengine.foundation.FluidByteStyle
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.FluidFormatWidth
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidArrivingText
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidAsyncImage
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidCountdownBadge
import dev.antigravity.fluidengine.ui.fluid.FluidExpandableText
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidImagePhase
import dev.antigravity.fluidengine.ui.fluid.FluidLiveClockProvider
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidNumericText
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidRelativeText
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTextArrival
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidThumbHash
import dev.antigravity.fluidengine.ui.fluid.FluidTimerText
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidContentState
import dev.antigravity.fluidengine.ui.theme.FluidEmptyPreset
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidLoadState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// F8.1 · Testo e numeri in movimento. Le scene della pagina «Elementi nuovi», una per elemento, coi nomi brevi delle
// riprese: `numeri`, `testo-arriva`, `espandibile`, `conto`, `tempi`, `immagine`, `stati`. Ogni scena ha un tasto
// «Automatico» che rifà da sola la sequenza delle riprese, così sul tablet non servono tocchi in serie.

internal val F8p1Section = NewElementSection(
  pack = "F8.1",
  title = "Testo e numeri in movimento",
  scenes = listOf(
    NewElementScene(
      "numeri", "Numeri che scorrono",
      "Il saldo come Wallet, il cronometro come Orologio, i contatori: scorrono solo le cifre cambiate, sfocate",
    ) { NumbersScene(it) },
    NewElementScene(
      "testo-arriva", "Testo che arriva",
      "La risposta di PampAI parola per parola, un testo che compare riga per riga: sfocati, si mettono a fuoco",
    ) { ArrivingTextScene(it) },
    NewElementScene(
      "espandibile", "Testo che si espande",
      "La descrizione come nell'App Store, «altro» nell'ultima riga; aperta, le righe arrivano una dopo l'altra",
    ) { ExpandableScene(it) },
    NewElementScene(
      "conto", "Conto alla rovescia",
      "La capsula del timer della notte e «Annulla invio»: le cifre scendono, una volta al secondo",
    ) { CountdownScene(it) },
    NewElementScene(
      "tempi", "Formattatori e tempi vivi",
      "«ora», «12 min fa», «ieri, 09:04», pesi, elenchi, durate e numeri brevi, in italiano e in inglese",
    ) { TimesScene(it) },
    NewElementScene(
      "immagine", "Immagine che arriva",
      "L'impronta ThumbHash coi colori veri, poi la foto sfocata che si mette a fuoco, oppure «Riprova»",
    ) { ImageScene(it) },
    NewElementScene(
      "stati", "Stati pronti",
      "Nessun risultato, offline, errore con «Riprova», permesso, ancora niente; e il passaggio dallo scheletro",
    ) { StatesScene(it) },
  ),
)

/** I tag delle prove. */
internal object F8p1Tags {
  const val Auto = "f8p1:automatico"
  const val Balance = "f8p1:saldo"
  const val BalanceUp = "f8p1:saldo-su"
  const val BalanceDown = "f8p1:saldo-giu"
  const val Unread = "f8p1:non-lette"
  const val Badge = "f8p1:badge"
  const val Replay = "f8p1:ripeti"
  const val Expandable = "f8p1:descrizione"
  const val Countdown = "f8p1:capsula"
  const val Restart = "f8p1:ricomincia"
  const val Reload = "f8p1:ricarica"
  const val Next = "f8p1:avanti"
}

// ---------------------------------------------------------------------------------------------------------------------
// Pezzi comuni
// ---------------------------------------------------------------------------------------------------------------------

/** Un fondo della scena: la pagina, una foto chiara, una foto scura. */
private enum class SceneGround(val label: String, @param:DrawableRes val photo: Int?) {
  Page("Pagina", null),
  LightPhoto("Foto chiara", R.drawable.viewer_flower),
  DarkPhoto("Foto scura", R.drawable.viewer_astronaut),
}

/**
 * L'elemento sui tre fondi: sulla pagina così com'è, sulle foto dentro una capsula di vetro dell'engine (un elemento che
 * galleggia sul contenuto è vetro).
 */
@Composable
private fun OnGrounds(height: androidx.compose.ui.unit.Dp = 112.dp, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SceneGround.entries.forEach { ground ->
      val shape = ContinuousCornerShape(FluidRadius.Card)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(height)
          .clip(shape)
          .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
      ) {
        val photo = ground.photo
        if (photo == null) {
          content()
        } else {
          val stage = rememberGlassBackdrop()
          Image(
            painter = painterResource(photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().glassBackdropSource(stage),
          )
          FluidGlassContainer {
            Box(
              modifier = Modifier.fluidGlass(backdrop = stage).padding(horizontal = 18.dp, vertical = 10.dp),
              contentAlignment = Alignment.Center,
            ) { content() }
          }
        }
        Text(
          text = ground.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (photo == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
          modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        )
      }
    }
  }
}

/** Il tasto «Automatico» delle riprese. */
@Composable
private fun AutoChip(auto: Boolean, onChange: (Boolean) -> Unit) {
  FluidChip(
    label = if (auto) "Automatico · acceso" else "Automatico",
    selected = auto,
    onClick = { onChange(!auto) },
    modifier = Modifier.testTag(F8p1Tags.Auto),
  )
}

@Composable
private fun SceneCard(content: @Composable () -> Unit) {
  FluidListGroup {
    Box(Modifier.fillMaxWidth().padding(16.dp)) { content() }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// numeri · I numeri che scorrono
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun NumbersScene(onBack: () -> Unit) {
  var auto by rememberSaveable { mutableStateOf(false) }
  var balance by rememberSaveable { mutableDoubleStateOf(1_284.50) }
  var unread by rememberSaveable { mutableIntStateOf(3) }
  var percent by rememberSaveable { mutableIntStateOf(42) }
  var seconds by rememberSaveable { mutableLongStateOf(57L) }
  var running by rememberSaveable { mutableStateOf(true) }
  val locale = Locale.ITALY
  LaunchedEffect(running) {
    while (running) {
      delay(1_000)
      seconds++
    }
  }
  LaunchedEffect(auto) {
    if (!auto) return@LaunchedEffect
    // La sequenza delle riprese: su, giù, attraverso il migliaio, i contatori.
    val steps = listOf(12.40, -7.25, 990.00, -1_000.00, 0.35, 45.10)
    var i = 0
    while (true) {
      delay(1_600)
      balance = (balance + steps[i % steps.size]).coerceAtLeast(0.0)
      unread = if (i % 3 == 2) (unread - 2).coerceAtLeast(0) else unread + 1
      percent = (percent + 7).let { if (it > 100) 7 else it }
      i++
    }
  }
  FluidScreen(title = "Numeri che scorrono", subtitle = "F8.1 · numeri-che-scorrono", onBack = onBack) {
    item("auto") { Row(Modifier.padding(horizontal = 16.dp)) { AutoChip(auto) { auto = it } } }
    item("saldo-titolo") { FluidSectionHeader("Il saldo", detail = "Come Wallet: cresce, le cifre salgono; cala, scendono.") }
    item("saldo") {
      OnGrounds(height = 120.dp) {
        FluidNumericText(
          text = "€ " + FluidFormat.number(balance, locale, fractionDigits = 2),
          value = balance,
          style = FluidTextStyles.largeNumeric,
          modifier = Modifier.testTag(F8p1Tags.Balance),
        )
      }
    }
    item("saldo-tasti") {
      Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton("+ 12,40", { balance += 12.40 }, Modifier.testTag(F8p1Tags.BalanceUp), style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
        FluidButton("− 7,25", { balance = (balance - 7.25).coerceAtLeast(0.0) }, Modifier.testTag(F8p1Tags.BalanceDown), style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
        FluidButton("+ 990", { balance += 990.0 }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
      }
    }
    item("orologio-titolo") { FluidSectionHeader("Il cronometro", detail = "Come Orologio: ogni secondo scorre solo la cifra che cambia.") }
    item("orologio") {
      OnGrounds(height = 104.dp) {
        FluidNumericText(
          text = FluidFormat.timer(Duration.ofSeconds(seconds), locale),
          value = seconds.toDouble(),
          style = FluidTextStyles.largeNumeric,
        )
      }
    }
    item("orologio-tasti") {
      Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(if (running) "Ferma" else "Avvia", { running = !running }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
        FluidButton("Azzera", { seconds = 0 }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
      }
    }
    item("contatori-titolo") { FluidSectionHeader("I contatori", detail = "«3 non lette» e la percentuale di uno scaricamento.") }
    item("contatori") {
      FluidListGroup {
        FluidListRow(
          title = "Posta in arrivo",
          subtitle = "Il numero cambia sul posto, la parola resta ferma",
          badge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              FluidNumericText(unread, Modifier.testTag(F8p1Tags.Unread), color = MaterialTheme.colorScheme.primary)
              Text(" non lette", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          },
          onClick = { unread++ },
        )
        FluidListDivider()
        // F8.1b: anche il badge scorre, col blur durante lo scorrimento.
        FluidListRow(
          title = "Il badge",
          subtitle = "FluidBadge: la capsula segue la larghezza nuova",
          badge = { FluidBadge(count = unread, modifier = Modifier.testTag(F8p1Tags.Badge)) },
          onClick = { unread++ },
        )
        FluidListDivider()
        FluidListRow(
          title = "Scaricamento",
          subtitle = "Tocca per avanzare",
          badge = {
            FluidNumericText(
              text = FluidFormat.percent(percent / 100.0, locale),
              value = percent.toDouble(),
              color = MaterialTheme.colorScheme.primary,
            )
          },
          onClick = { percent = (percent + 7).let { if (it > 100) 7 else it } },
        )
      }
    }
    item("nota") {
      FluidSectionFootnote(
        "Virgola, segno e unità non scorrono. Il verso viene dal valore. Col movimento ridotto il numero cambia a dissolvenza.",
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// testo-arriva · Il testo che arriva
// ---------------------------------------------------------------------------------------------------------------------

internal const val F8p1Answer =
  "Certo! Ecco come funziona il **vetro liquido** dell'engine: ogni superficie rifrange ciò che sta sotto, " +
    "si piega ai bordi e prende la luce dal dito.\n\n" +
    "- I tasti crescono sotto il dito e lo seguono.\n" +
    "- Il contenuto che cambia entra sfocato e si mette a fuoco.\n" +
    "- Da fermo non ridisegna niente.\n\n" +
    "Vuoi che ti mostri una scena della galleria?"

private val ArrivingParagraphs = listOf(
  "Il testo che arriva non compare tutto insieme: le righe entrano una dopo l'altra, sfocate, e si mettono a fuoco " +
    "mentre la cornice si apre. È lo stesso arrivo delle risposte di PampAI.",
  "Una descrizione caricata dalla rete, una nota riaperta, una risposta che cambia: ciò che resta uguale non si muove, " +
    "il resto arriva riga per riga dal primo carattere diverso.",
  "Col movimento ridotto il testo compare intero, senza sfocatura e senza passo: il risultato è fermo e leggibile.",
)

@Composable
internal fun ArrivingTextScene(onBack: () -> Unit) {
  var auto by rememberSaveable { mutableStateOf(false) }
  var shown by remember { mutableStateOf("") }
  var streaming by remember { mutableStateOf(false) }
  var run by remember { mutableIntStateOf(0) }
  var paragraph by rememberSaveable { mutableIntStateOf(0) }
  var fadeOnly by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(run) {
    if (run == 0) return@LaunchedEffect
    // Un modello finto: pezzi da 2 a 6 parole ogni 70–110 ms, come uno streaming vero.
    streaming = true
    shown = ""
    val words = F8p1Answer.split(' ')
    var i = 0
    var chunk = 0
    while (i < words.size) {
      val take = 2 + (chunk * 7 % 5)
      shown = words.take((i + take).coerceAtMost(words.size)).joinToString(" ")
      i += take
      chunk++
      delay(70L + (chunk * 13 % 40))
    }
    shown = F8p1Answer
    streaming = false
  }
  LaunchedEffect(auto) {
    if (!auto) return@LaunchedEffect
    while (true) {
      run++
      delay(5_200)
      paragraph = (paragraph + 1) % ArrivingParagraphs.size
      delay(2_400)
    }
  }
  FluidScreen(title = "Testo che arriva", subtitle = "F8.1 · testo che arriva, parola per parola e riga per riga", onBack = onBack) {
    item("auto") {
      FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoChip(auto) { auto = it }
        FluidChip("Ripeti la risposta", selected = false, onClick = { run++ }, modifier = Modifier.testTag(F8p1Tags.Replay))
        FluidChip("Solo dissolvenza (prima)", selected = fadeOnly, onClick = { fadeOnly = !fadeOnly })
      }
    }
    item("bolla-titolo") {
      FluidSectionHeader("La risposta di PampAI", detail = "La bolla vera dell'assistente (engine-ai-ui): le parole arrivano sfocate.")
    }
    item("bolla") {
      Box(Modifier.padding(horizontal = 16.dp)) {
        if (fadeOnly) {
          SceneCard { FluidMarkdownFade(shown, streaming) }
        } else {
          FluidAssistantBubble(text = shown, streaming = streaming, contentKey = run)
        }
      }
    }
    item("righe-titolo") {
      FluidSectionHeader("Un testo che compare", detail = "Riga per riga, dal primo carattere diverso. Tocca «Cambia».")
    }
    item("righe") {
      SceneCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          FluidArrivingText(
            text = ArrivingParagraphs[paragraph],
            arrival = if (fadeOnly) FluidTextArrival.Fade else FluidTextArrival.Lines,
          )
          FluidButton("Cambia", { paragraph = (paragraph + 1) % ArrivingParagraphs.size }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
        }
      }
    }
  }
}

/** La stessa risposta con la sola dissolvenza di prima della 4.0, per il confronto. */
@Composable
private fun FluidMarkdownFade(text: String, streaming: Boolean) {
  dev.antigravity.fluidengine.ui.fluid.FluidRevealingText(
    text = androidx.compose.ui.text.AnnotatedString(text.replace("**", "")),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurface,
    animateFirst = true,
    reveal = streaming,
    revealMillis = 180,
    arrival = FluidTextArrival.Fade,
  )
}

// ---------------------------------------------------------------------------------------------------------------------
// espandibile · Il testo che si espande
// ---------------------------------------------------------------------------------------------------------------------

internal const val F8p1Description =
  "Fluid Glass è la galleria del Fluid Engine: ogni pagina mostra un pezzo dell'interfaccia di vetro, dai tasti che " +
    "crescono sotto il dito ai menu che nascono dal tasto che li apre. Le scene rifanno i casi delle app di Pampa con il " +
    "solo engine, senza una riga di movimento scritta a mano. Puoi cambiare il tema, la grandezza del testo e il " +
    "movimento, e vedere come ogni elemento si adatta. Le novità di questa versione: i numeri che scorrono, il testo che " +
    "arriva, il conto alla rovescia, i tempi vivi, l'immagine che arriva e gli stati pronti delle pagine vuote."

@Composable
internal fun ExpandableScene(onBack: () -> Unit) {
  var tapped by remember { mutableStateOf(false) }
  val linkColor = MaterialTheme.colorScheme.primary
  val withLink = remember(linkColor) {
    buildAnnotatedString {
      append("Con la versione 4.0 arriva il vetro nuovo. Le note per gli sviluppatori sono sul ")
      withLink(LinkAnnotation.Clickable("note", TextLinkStyles(SpanStyle(color = linkColor))) { tapped = true }) {
        append("sito dell'engine")
      }
      append(": un tocco sul link apre il link e non il paragrafo. Il resto delle novità: i formattatori in italiano e " +
        "nella lingua del sistema, il tempo relativo che si aggiorna da solo una volta al minuto, l'impronta delle foto " +
        "che si caricano, e gli stati della pagina senza contenuto con «Riprova» come tasto con esito.")
    }
  }
  FluidScreen(title = "Testo che si espande", subtitle = "F8.1 · testo-espandibile", onBack = onBack) {
    item("descrizione-titolo") { FluidSectionHeader("Descrizione", detail = "Come l'App Store: «altro» in fondo all'ultima riga.") }
    item("descrizione") {
      SceneCard { FluidExpandableText(F8p1Description, Modifier.testTag(F8p1Tags.Expandable), collapsedMaxLines = 3) }
    }
    item("novita-titolo") { FluidSectionHeader("Novità della versione 4.0", detail = if (tapped) "Link toccato: il paragrafo è rimasto com'era." else "Con un link nel testo.") }
    item("novita") { SceneCard { FluidExpandableText(withLink, collapsedMaxLines = 2, collapsible = true) } }
    item("nota") {
      FluidSectionFootnote(
        "Aperto, l'altezza cresce con una molla senza rimbalzo e le righe nuove arrivano una dopo l'altra, sfocate. " +
          "Il secondo paragrafo si richiude con un altro tocco.",
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// conto · Il conto alla rovescia
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun CountdownScene(onBack: () -> Unit) {
  var night by remember { mutableStateOf(Instant.now().plusSeconds(5 * 60L)) }
  var undo by remember { mutableStateOf(Instant.now().plusSeconds(10L)) }
  var sent by remember { mutableStateOf(false) }
  FluidScreen(title = "Conto alla rovescia", subtitle = "F8.1 · conto-alla-rovescia", onBack = onBack) {
    item("notte-titolo") { FluidSectionHeader("Il timer della notte", detail = "Sounds · l'anello si svuota, le cifre scendono.") }
    item("notte") {
      OnGrounds(height = 96.dp) {
        FluidCountdownBadge(endsAt = night, total = Duration.ofMinutes(5), modifier = Modifier.testTag(F8p1Tags.Countdown))
      }
    }
    item("grande") {
      SceneCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          FluidTimerText(endsAt = night, style = FluidTextStyles.largeNumeric)
          Text("Text(timerInterval:)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    item("annulla-titolo") { FluidSectionHeader("«Annulla invio»", detail = if (sent) "Inviata." else "Mail · dieci secondi per ripensarci.") }
    item("annulla") {
      FluidListGroup {
        FluidListRow(
          title = "Riunione di domani",
          subtitle = "A: Anna, Luca",
          badge = {
            FluidCountdownBadge(
              endsAt = undo,
              total = Duration.ofSeconds(10),
              tone = MaterialTheme.colorScheme.tertiary,
              onExpire = { sent = true },
            )
          },
        )
      }
    }
    item("tasti") {
      Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          "Ricomincia",
          {
            night = Instant.now().plusSeconds(5 * 60L)
            undo = Instant.now().plusSeconds(10L)
            sent = false
          },
          Modifier.testTag(F8p1Tags.Restart),
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
        )
        FluidButton("Ultimi 5 s", { night = Instant.now().plusSeconds(5L) }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
      }
    }
    item("nota") {
      FluidSectionFootnote("A riposo la capsula si ricompone una volta al secondo e non ridisegna fra un secondo e l'altro.")
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// tempi · Formattatori e tempi vivi
// ---------------------------------------------------------------------------------------------------------------------

@Composable
internal fun TimesScene(onBack: () -> Unit) {
  val opened = remember { Instant.now() }
  val zone = ZoneId.systemDefault()
  val yesterday = remember { opened.atZone(zone).minusDays(1).withHour(9).withMinute(4).toInstant() }
  val lives = remember {
    listOf(
      "Aggiornato" to opened.minusSeconds(20),
      "Ultimo voto" to opened.minusSeconds(2 * 60 + 5),
      "Sincronizzato" to opened.minusSeconds(58 * 60),
      "Messaggio" to opened.minusSeconds(3 * 3_600),
      "Foto" to opened.minusSeconds(2 * 86_400),
      "Consegna" to opened.plusSeconds(5 * 60 + 30),
      "Verifica" to opened.plusSeconds(3 * 86_400),
    )
  }
  FluidScreen(title = "Formattatori e tempi vivi", subtitle = "F8.1 · formattatori-e-tempi-vivi", onBack = onBack) {
    item("vivi-titolo") {
      FluidSectionHeader("Tempi vivi", detail = "Un orologio solo per la pagina: batte quando un testo cambia, non a ogni fotogramma.")
    }
    item("vivi") {
      FluidLiveClockProvider {
        FluidListGroup {
          lives.forEachIndexed { index, (label, at) ->
            if (index > 0) FluidListDivider()
            FluidListRow(title = label, subtitle = FluidFormat.dayAndTime(at, opened), badge = { FluidRelativeText(at, color = MaterialTheme.colorScheme.onSurfaceVariant) })
          }
        }
      }
    }
    listOf(Locale.ITALY to "Italiano", Locale.US to "English").forEach { (locale, name) ->
      item("formati-$name") {
        Column {
          FluidSectionHeader(name, detail = "FluidFormat, funzioni pure in engine-foundation")
          FluidListGroup {
            val rows = listOf(
              "Giorno e ora" to FluidFormat.dayAndTime(yesterday, opened, locale),
              "Data in lista" to FluidFormat.listDate(yesterday.minusSeconds(3 * 86_400), opened, locale),
              "Tempo relativo" to FluidFormat.relative(opened.minusSeconds(12 * 60), opened, locale),
              "Per TalkBack" to FluidFormat.relative(opened.minusSeconds(12 * 60), opened, locale, FluidFormatWidth.Wide),
              "Peso di un file" to FluidFormat.bytes(2_420_000, FluidByteStyle.File, locale),
              "Memoria" to FluidFormat.bytes(3L * 1024 * 1024 * 1024, FluidByteStyle.Memory, locale),
              "Destinatari" to FluidFormat.list(listOf("Anna", "Luca", "Marco", "Sara", "Piero"), locale, limit = 2),
              "Durata" to FluidFormat.duration(Duration.ofMinutes(85), locale),
              "Cronometro" to FluidFormat.timer(Duration.ofSeconds(3_727), locale),
              "Numero breve" to FluidFormat.compact(1_234_567.0, locale),
              "Percentuale" to FluidFormat.percent(0.425, locale),
            )
            rows.forEachIndexed { index, (label, value) ->
              if (index > 0) FluidListDivider()
              FluidListRow(title = label, subtitle = value, subtitleMaxLines = 2)
            }
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// immagine · L'immagine che arriva
// ---------------------------------------------------------------------------------------------------------------------

private val ScenePhotos = listOf(R.drawable.viewer_flower, R.drawable.viewer_astronaut, R.drawable.viewer_rocket, R.drawable.viewer_notes_cover)

/** Una foto delle risorse e la sua impronta, calcolata una volta come la calcolerebbe l'app al caricamento. */
private class ScenePhoto(val image: ImageBitmap, val hash: ByteArray)

private fun loadScenePhoto(context: android.content.Context, @DrawableRes id: Int): ScenePhoto {
  val bitmap = BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inSampleSize = 4 })
  val side = 96
  val scale = side.toFloat() / maxOf(bitmap.width, bitmap.height)
  val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
  val pixels = IntArray(small.width * small.height)
  small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
  val rgba = ByteArray(pixels.size * 4)
  pixels.forEachIndexed { i, c ->
    rgba[i * 4] = (c shr 16 and 255).toByte()
    rgba[i * 4 + 1] = (c shr 8 and 255).toByte()
    rgba[i * 4 + 2] = (c and 255).toByte()
    rgba[i * 4 + 3] = (c ushr 24).toByte()
  }
  return ScenePhoto(bitmap.asImageBitmap(), FluidThumbHash.encode(small.width, small.height, rgba))
}

@Composable
internal fun ImageScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val photos = remember { ScenePhotos.map { loadScenePhoto(context, it) } }
  val tiles = 6
  val phases = remember { mutableStateListOf<FluidImagePhase>().apply { repeat(tiles) { add(FluidImagePhase.Loading) } } }
  var round by remember { mutableIntStateOf(1) }
  var auto by rememberSaveable { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(round) {
    for (i in 0 until tiles) phases[i] = FluidImagePhase.Loading
    // Le foto arrivano una dopo l'altra, come da un NAS; la quinta non arriva al primo giro.
    for (i in 0 until tiles) {
      delay(if (i == 0) 900L else 380L)
      phases[i] = if (i == 4 && round % 2 == 1) FluidImagePhase.Failure() else FluidImagePhase.Success(photos[i % photos.size].image)
    }
  }
  LaunchedEffect(auto) {
    if (!auto) return@LaunchedEffect
    while (true) {
      delay(5_000)
      round++
    }
  }
  FluidScreen(title = "Immagine che arriva", subtitle = "F8.1 · immagine-che-arriva", onBack = onBack) {
    item("auto") {
      Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoChip(auto) { auto = it }
        FluidChip("Ricarica", selected = false, onClick = { round++ }, modifier = Modifier.testTag(F8p1Tags.Reload))
      }
    }
    item("griglia-titolo") {
      FluidSectionHeader("La libreria dal NAS", detail = "MyCloud Gallery · l'impronta coi colori veri, poi la foto che si mette a fuoco.")
    }
    item("griglia") {
      Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until tiles / 3) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (col in 0 until 3) {
              val i = row * 3 + col
              FluidAsyncImage(
                phase = phases[i],
                contentDescription = "Foto ${i + 1}",
                thumbHash = photos[i % photos.size].hash,
                shape = ContinuousCornerShape(FluidRadius.Card),
                onRetry = { scope.launch { phases[i] = FluidImagePhase.Loading; delay(700); phases[i] = FluidImagePhase.Success(photos[i % photos.size].image) } },
                modifier = Modifier.weight(1f).aspectRatio(1f),
              )
            }
          }
        }
      }
    }
    item("impronte-titolo") { FluidSectionHeader("Le impronte da sole", detail = "Poche decine di byte per foto, decodificate una volta e tenute.") }
    item("impronte") {
      Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.forEach { photo ->
          Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
              bitmap = FluidThumbHash.imageBitmap(photo.hash),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(ContinuousCornerShape(FluidRadius.Card)),
            )
            Text("${photo.hash.size} byte", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// stati · Gli stati pronti
// ---------------------------------------------------------------------------------------------------------------------

private val StatePresets = listOf(
  "Ricerca" to FluidEmptyPreset.NoResults("cena di classe"),
  "Offline" to FluidEmptyPreset.Offline,
  "Errore" to FluidEmptyPreset.Error(),
  "Permesso" to FluidEmptyPreset.Permission("Per le foto del NAS serve l'accesso alla rete locale."),
  "Vuoto" to FluidEmptyPreset.Empty,
)

@Composable
internal fun StatesScene(onBack: () -> Unit) {
  var chosen by rememberSaveable { mutableIntStateOf(2) }
  var online by rememberSaveable { mutableStateOf(false) }
  var attempts by remember { mutableIntStateOf(0) }
  var step by rememberSaveable { mutableIntStateOf(0) }
  val flow = listOf<FluidLoadState<List<String>>>(
    FluidLoadState.Loading,
    FluidLoadState.Ready(listOf("Compito di matematica", "Uscita didattica", "Colloqui")),
    FluidLoadState.Unavailable(FluidEmptyPreset.Error("Il registro non risponde.")),
    FluidLoadState.Unavailable(FluidEmptyPreset.Empty),
  )
  FluidScreen(title = "Stati pronti", subtitle = "F8.1 · stati-pronti", onBack = onBack) {
    item("scelta") {
      FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatePresets.forEachIndexed { index, (label, _) -> FluidChip(label, selected = chosen == index, onClick = { chosen = index }) }
      }
    }
    item("stato") {
      SceneCard {
        FluidEmptyState(
          preset = StatePresets[chosen].second,
          onRetry = {
            // Il primo tentativo non riesce, il secondo sì: il tasto mostra i due esiti.
            delay(900)
            attempts++
            if (attempts % 2 == 1) error("ancora niente")
          },
          online = online,
          onAction = if (StatePresets[chosen].second is FluidEmptyPreset.Permission) ({ }) else null,
        )
      }
    }
    item("rete") {
      FluidListGroup {
        FluidListRow(
          title = "Rete",
          subtitle = if (online) "Tornata: lo stato offline riprova da solo" else "Spenta (finta, per la scena)",
          badge = { FluidSwitch(checked = online, onCheckedChange = { online = it }) },
        )
      }
    }
    item("passaggio-titolo") {
      FluidSectionHeader("Senza salti", detail = "Scheletro, contenuto, errore, vuoto: il nuovo entra sfocato, l'altezza segue.")
    }
    item("passaggio") {
      SceneCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          FluidContentState(flow[step]) { items ->
            Column {
              items.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp)) }
            }
          }
          FluidButton("Avanti", { step = (step + 1) % flow.size }, Modifier.testTag(F8p1Tags.Next), style = FluidButtonStyle.Tinted, size = FluidButtonSize.Small)
        }
      }
    }
  }
}
