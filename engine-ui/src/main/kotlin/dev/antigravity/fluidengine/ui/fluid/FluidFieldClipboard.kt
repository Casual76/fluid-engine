package dev.antigravity.fluidengine.ui.fluid

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Image
import dev.antigravity.fluidengine.ui.fluid.icons.Key
import dev.antigravity.fluidengine.ui.fluid.icons.Link
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Gli appunti dei campi speciali (F8.3): il **tasto incolla** (`tasto-incolla`) e la **copia delicata**
 * (`copia-delicata`). Solo API di Android, nessuna libreria.
 *
 * La regola comune è quella della privacy di Android 12: **leggere il contenuto degli appunti** (`getPrimaryClip`) di un
 * clip arrivato da un'altra app fa comparire l'avviso «<app> ha incollato dagli appunti»; leggere la **descrizione**
 * (`getPrimaryClipDescription`: i tipi, l'etichetta, gli extra, la classificazione del testo) no. Qui il contenuto si
 * legge solo dopo un tocco, come `PasteButton` di SwiftUI, che legge «quando la persona lo preme».
 */

// ================================================================================================================
// Il tasto incolla
// ================================================================================================================

/**
 * Il tipo di dato che un campo aspetta: il **dato concordato** della nota di Alessio («se è richiesto un OTP, o un link,
 * o uno screen, allora si appaiono in automatico; appunti normali no»). Il tasto compare **solo** se negli appunti c'è
 * questo tipo di dato.
 */
@Immutable
class FluidPasteKind private constructor(
  internal val id: String,
  /** Per un codice, quante cifre (o caratteri) può avere. */
  val codeLengths: IntRange,
  /** Per un codice, solo cifre (vero) o anche lettere. */
  val digitsOnly: Boolean,
) {
  override fun equals(other: Any?): Boolean =
    other is FluidPasteKind && other.id == id && other.codeLengths == codeLengths && other.digitsOnly == digitsOnly

  override fun hashCode(): Int = (id.hashCode() * 31 + codeLengths.hashCode()) * 31 + digitsOnly.hashCode()
  override fun toString(): String = "FluidPasteKind($id)"

  internal val isCode: Boolean get() = id == CodeId
  internal val isLink: Boolean get() = id == LinkId
  internal val isImage: Boolean get() = id == ImageId

  companion object {
    private const val CodeId = "code"
    private const val LinkId = "link"
    private const val ImageId = "image"

    /** Un codice di [lengths] cifre (di serie 4–8: verifica in due passaggi, PIN, abbinamento). */
    fun code(lengths: IntRange = 4..8, digitsOnly: Boolean = true): FluidPasteKind {
      require(!lengths.isEmpty() && lengths.first >= 1) { "lunghezze del codice non valide: $lengths" }
      return FluidPasteKind(CodeId, lengths, digitsOnly)
    }

    /** Un link web (`http`, `https`). */
    val Link: FluidPasteKind = FluidPasteKind(LinkId, 0..0, false)

    /** Un'immagine, per esempio uno screenshot appena fatto. */
    val Image: FluidPasteKind = FluidPasteKind(ImageId, 0..0, false)
  }
}

/** Quello che il tasto incolla consegna, già del tipo concordato. */
sealed class FluidPasted {
  /** Il codice, solo le sue cifre («123-456» diventa «123456»). */
  data class Code(val code: String) : FluidPasted()

  /** Il link, intero. */
  data class Link(val url: String) : FluidPasted()

  /** L'immagine: l'indirizzo e il tipo. Leggerla (con `contentResolver`) è dell'app. */
  data class Image(val uri: Uri, val mimeType: String) : FluidPasted()
}

/**
 * Quello che si sa degli appunti **senza leggerne il contenuto**: i tipi e, da Android 12, la classificazione del testo
 * che il sistema fa da sé (`ClipDescription.getConfidenceScore`). [text] c'è solo sotto Android 12, dove leggere il testo
 * non mostra nessun avviso.
 */
internal class FluidClipFacts(
  val mimeTypes: List<String>,
  val classified: Boolean,
  val otpScore: Float,
  val urlScore: Float,
  val text: String?,
)

/** Le soglie della classificazione. */
internal object FluidClipRules {
  /**
   * La fiducia minima perché un testo classificato sia un codice o un link: 0,5. A occhio, 23 novembre 2026: il
   * classificatore di Android dà 1,0 ai link e ai codici che riconosce, e la soglia lascia fuori i dubbi.
   */
  const val MinScore: Float = 0.5f

  /** Sotto Android 12 si legge il testo per decidere, ma non oltre questi caratteri: un appunto lungo non è un codice. */
  const val MaxSilentText: Int = 2_048

  /** L'entità «codice monouso» del classificatore (`TextClassifier.TYPE_OTP`, Android 16): prima non esiste. */
  const val OtpEntity: String = "otp"
  const val UrlEntity: String = "url"

  /** Le attese della classificazione, che il sistema finisce dopo aver avvisato del clip nuovo. */
  val ClassificationRetriesMs: LongArray = longArrayOf(150L, 400L, 900L, 2_000L)
}

private val LinkPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** Il link dentro [text] (il primo), senza la punteggiatura che lo chiude; null se non ce n'è uno. */
internal fun fluidLinkFromText(text: String): String? =
  LinkPattern.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '»')

/**
 * Il codice dentro [text]. Con [strict] il testo **è** il codice (tolti gli spazi e i trattini fra le cifre: «123 456»,
 * «123-456»): è la regola per decidere senza la classificazione, dove un numero di telefono non deve sembrare un codice.
 * Senza [strict] (dopo il tocco) si prende la prima sequenza della lunghezza giusta, anche dentro una frase («Il tuo
 * codice è 482913»), e in mancanza i primi caratteri validi.
 */
internal fun fluidCodeFromText(text: String, lengths: IntRange, digitsOnly: Boolean, strict: Boolean): String? {
  fun valid(c: Char) = if (digitsOnly) c in '0'..'9' else c.isLetterOrDigit() && c.code < 128
  val compact = text.trim().replace(Regex("""(?<=\w)[\s\-·.](?=\w)"""), "")
  if (strict) return compact.takeIf { it.length in lengths && it.all(::valid) }?.uppercase()
  val runs = Regex(if (digitsOnly) "[0-9]+" else "[A-Za-z0-9]+").findAll(compact).map { it.value }.toList()
  runs.firstOrNull { it.length in lengths }?.let { return it.uppercase() }
  val all = compact.filter(::valid)
  return all.take(lengths.last).takeIf { it.length >= lengths.first }?.uppercase()
}

/** Vero se negli appunti descritti da [facts] c'è il dato di [kind]. Senza certezza, no (la nota di Alessio). */
internal fun fluidPasteMatches(kind: FluidPasteKind, facts: FluidClipFacts?): Boolean {
  if (facts == null) return false
  return when {
    kind.isImage -> facts.mimeTypes.any { it.startsWith("image/") }
    kind.isLink -> facts.mimeTypes.any { it == ClipDescription.MIMETYPE_TEXT_URILIST } ||
      (facts.classified && facts.urlScore >= FluidClipRules.MinScore) ||
      (facts.text != null && fluidLinkFromText(facts.text)?.let { it.length >= facts.text.trim().length - 1 } == true)
    kind.isCode -> (facts.classified && facts.otpScore >= FluidClipRules.MinScore) ||
      (facts.text != null && fluidCodeFromText(facts.text, kind.codeLengths, kind.digitsOnly, strict = true) != null)
    else -> false
  }
}

/**
 * I fatti degli appunti per [kind], letti da [manager] **senza il contenuto** da Android 12 in poi ([sdk] ≥ 31): i tipi
 * e la classificazione. Sotto Android 12, dove non c'è nessun avviso, il testo corto si legge.
 */
internal fun fluidClipFacts(manager: ClipboardManager, kind: FluidPasteKind, sdk: Int = Build.VERSION.SDK_INT): FluidClipFacts? {
  val description = runCatching { manager.primaryClipDescription }.getOrNull() ?: return null
  val mimes = (0 until description.mimeTypeCount).map { description.getMimeType(it) }
  var classified = false
  var otp = 0f
  var url = 0f
  if (sdk >= 31 && runCatching { description.classificationStatus }.getOrNull() == ClipDescription.CLASSIFICATION_COMPLETE) {
    classified = true
    otp = runCatching { description.getConfidenceScore(FluidClipRules.OtpEntity) }.getOrDefault(0f)
    url = runCatching { description.getConfidenceScore(FluidClipRules.UrlEntity) }.getOrDefault(0f)
  }
  val text = if (sdk < 31 && !kind.isImage && mimes.any { it.startsWith("text/") }) {
    runCatching { manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() }.getOrNull()
      ?.takeIf { it.length <= FluidClipRules.MaxSilentText }
  } else null
  return FluidClipFacts(mimes, classified, otp, url, text)
}

/** Il clip da classificare ancora: il sistema lo classifica dopo averlo annunciato, e qui si riprova un poco. */
private fun ClipDescription.awaitingClassification(sdk: Int = Build.VERSION.SDK_INT): Boolean =
  sdk >= 31 && hasMimeType("text/*") &&
    runCatching { classificationStatus }.getOrNull() == ClipDescription.CLASSIFICATION_NOT_COMPLETE

/**
 * L'offerta del tasto incolla: se negli appunti c'è il dato di [kind] ([available]) e, al tocco, il dato ([take]).
 * Si aggiorna quando gli appunti cambiano, quando la finestra torna in primo piano (la persona ha copiato in un'altra
 * app) e quando la pagina riprende. Non legge mai il contenuto prima di [take] (da Android 12).
 */
@Stable
class FluidPasteOffer internal constructor(
  val kind: FluidPasteKind,
  private val context: Context,
  private val manager: ClipboardManager?,
) {
  /** Vero se il tasto deve comparire. */
  var available: Boolean by mutableStateOf(false)
    private set

  internal fun refresh(): Boolean {
    val m = manager ?: return false.also { available = false }
    available = fluidPasteMatches(kind, fluidClipFacts(m, kind))
    return runCatching { m.primaryClipDescription?.awaitingClassification() }.getOrNull() == true
  }

  /**
   * Il dato, letto ora (è il tocco della persona: qui Android può mostrare il suo avviso). Null se nel frattempo gli
   * appunti sono cambiati e il dato non c'è più.
   */
  fun take(): FluidPasted? {
    val m = manager ?: return null
    val clip = runCatching { m.primaryClip }.getOrNull()?.takeIf { it.itemCount > 0 } ?: return null
    val item = clip.getItemAt(0)
    return when {
      kind.isImage -> {
        val uri = item.uri ?: return null
        val mime = clip.description.filterMimeTypes("image/*")?.firstOrNull()
          ?: context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: return null
        FluidPasted.Image(uri, mime)
      }
      kind.isLink -> {
        val text = item.uri?.toString() ?: item.coerceToText(context)?.toString() ?: return null
        fluidLinkFromText(text)?.let { FluidPasted.Link(it) }
      }
      kind.isCode -> {
        val text = item.coerceToText(context)?.toString()?.take(FluidClipRules.MaxSilentText) ?: return null
        fluidCodeFromText(text, kind.codeLengths, kind.digitsOnly, strict = false)?.let { FluidPasted.Code(it) }
      }
      else -> null
    }
  }
}

/** L'offerta del tasto incolla per [kind], che si aggiorna da sola. Per un tasto suo; quello pronto è [FluidPasteButton]. */
@Composable
fun rememberFluidPasteOffer(kind: FluidPasteKind): FluidPasteOffer {
  val context = LocalContext.current
  val manager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
  val offer = remember(kind, manager) { FluidPasteOffer(kind, context.applicationContext ?: context, manager) }
  var generation by remember { mutableStateOf(0) }
  DisposableEffect(manager, offer) {
    val listener = ClipboardManager.OnPrimaryClipChangedListener { generation++ }
    manager?.addPrimaryClipChangedListener(listener)
    onDispose { manager?.removePrimaryClipChangedListener(listener) }
  }
  // La pagina che riprende, la finestra che torna al fuoco: chi ha copiato altrove ritrova il tasto.
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) generation++ }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  val window = LocalWindowInfo.current
  LaunchedEffect(window) { snapshotFlow { window.isWindowFocused }.collect { if (it) generation++ } }
  LaunchedEffect(offer, generation) {
    var waiting = offer.refresh()
    // La classificazione arriva dopo l'avviso del clip: si riprova poche volte, poi basta (nessun ciclo a vuoto).
    for (wait in FluidClipRules.ClassificationRetriesMs) {
      if (!waiting) break
      kotlinx.coroutines.delay(wait)
      waiting = offer.refresh()
    }
  }
  return offer
}

/**
 * Il tasto incolla per i dati concordati (F8.3, idea `tasto-incolla` rivista da Alessio): un tasto di vetro dell'engine
 * che **compare da solo solo se negli appunti c'è il dato che il campo aspetta** — un codice per il campo del codice, un
 * link per un campo link, un'immagine per un campo che accetta uno screenshot — e altrimenti non c'è. Gli appunti
 * normali non lo fanno comparire.
 *
 * È `PasteButton` di SwiftUI per come legge (solo al tocco), con una differenza voluta: Apple lo mostra sempre e lo
 * spegne, qui compare solo quando serve (la nota di Alessio). Nasce e se ne va nel piano flottante
 * ([FluidGlassContainer]), col viaggio di un vetro che compare da solo.
 *
 * **Come sa cosa c'è senza leggere.** Da Android 12 legge solo la descrizione del clip: il tipo (un'immagine, una lista
 * di indirizzi) e la classificazione che Android fa da sé del testo copiato (il link; il codice monouso da Android 16,
 * `TextClassifier.TYPE_OTP`). Così Android non mostra l'avviso «ha incollato dagli appunti» finché la persona non tocca.
 * Fra Android 12 e 15 Android non dice se un testo è un codice: lì il tasto del codice non compare (nel dubbio, no), e
 * restano l'incolla col dito lungo e il codice proposto dalla tastiera. Sotto Android 12 l'avviso non esiste e il testo
 * corto si legge per decidere.
 *
 * @param onPaste riceve il dato, già del tipo concordato ([FluidPasted]). Il campo del codice lo usa da sé.
 */
@Composable
fun FluidPasteButton(
  kind: FluidPasteKind,
  onPaste: (FluidPasted) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val offer = rememberFluidPasteOffer(kind)
  val haptics = LocalFluidHaptics.current
  val paste by rememberUpdatedState(onPaste)
  val text = label ?: when {
    kind.isImage -> labels.pasteImage
    kind.isLink -> labels.pasteLink
    else -> labels.pasteCode
  }
  val icon: ImageVector = when {
    kind.isImage -> FluidIcons.Image
    kind.isLink -> FluidIcons.Link
    else -> FluidIcons.Key
  }
  FluidGlassContainer(modifier) {
    if (offer.available) {
      FluidGlassButton(
        text = text,
        onClick = {
          val data = offer.take()
          if (data != null) paste(data) else haptics.play(FluidHapticEvent.Reject)
        },
        backdrop = backdrop,
        size = FluidGlassSize.Compact,
        maxLines = 1,
        leading = { FluidIcon(icon, contentDescription = null) },
      )
    }
  }
}

// ================================================================================================================
// La copia delicata
// ================================================================================================================

/**
 * Una copia delicata in corso (F8.3, `copia-delicata`): il suo tempo e la sua fine. [isPending] è vero finché gli appunti
 * non sono stati cancellati o sostituiti.
 */
@Stable
class FluidSensitiveCopy internal constructor(
  internal val token: String,
  /** Quando scade, sull'orologio monotono (`SystemClock.elapsedRealtime`). */
  val expiresAtElapsedRealtime: Long,
  /** Quanto dura in tutto. */
  val duration: Duration,
) {
  /** Vero finché la copia aspetta la sua cancellazione. */
  var isPending: Boolean by mutableStateOf(true)
    internal set

  /** Vero se alla fine gli appunti sono stati cancellati dall'engine (falso: sostituiti da altro, o lasciati nel dubbio). */
  var wasCleared: Boolean by mutableStateOf(false)
    internal set

  /** I millisecondi che restano, mai sotto zero. */
  fun remainingMillis(now: Long = SystemClock.elapsedRealtime()): Long = (expiresAtElapsedRealtime - now).coerceAtLeast(0L)
}

/**
 * La copia delicata: un dato che si copia **segnato come riservato** e che **si cancella dopo un tempo** (di serie 60 s).
 * È `UIPasteboard.setItems(_:options:)` con `.expirationDate` di iOS, come copiano le app di password.
 *
 * - **Riservato.** Il clip porta `ClipDescription.EXTRA_IS_SENSITIVE`: da Android 13 il sistema non lo mostra
 *   nell'anteprima degli appunti né nei suggerimenti della tastiera. Sotto Android 13 il segno non esiste e resta la
 *   scadenza.
 * - **Si cancella solo se è ancora il nostro.** Il clip porta anche un segno dell'engine (un codice a caso negli extra
 *   della descrizione, che si legge senza leggere il contenuto). Alla scadenza gli appunti si cancellano solo se c'è
 *   ancora quel segno: se la persona ha copiato altro, non si tocca niente (scheda dell'idea, «rischi»).
 * - **In secondo piano** Android non lascia leggere nemmeno la descrizione: nel dubbio non si cancella, e la cancellazione
 *   aspetta il ritorno dell'app in primo piano, dove il segno si rilegge.
 */
object FluidSensitiveClipboard {
  /** Il tempo di serie: 60 s, quello della scheda dell'idea (e delle app di password su iOS, che di solito danno 60–90 s). */
  val DefaultExpiry: Duration = 60.seconds

  /** Il segno dell'engine negli extra della descrizione. */
  internal const val TokenExtra: String = "dev.antigravity.fluidengine.clip.token"

  /** `ClipDescription.EXTRA_IS_SENSITIVE` (Android 13); la stessa chiave scritta per esteso vale anche prima, e lì si ignora. */
  internal const val SensitiveExtra: String = "android.content.extra.IS_SENSITIVE"

  private val pending = mutableListOf<Pending>()
  private var resumed = 0
  private var installedOn: Application? = null
  private val callbacksInstalled: Boolean get() = installedOn != null
  private var listening: ClipboardManager? = null

  private class Pending(
    val copy: FluidSensitiveCopy,
    val manager: ClipboardManager,
    val handler: Handler,
    val tick: Runnable,
  ) {
    var due = false
  }

  private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
    // Un clip nuovo (letto in primo piano): se non è uno dei nostri, le copie in corso sono state sostituite.
    val token = listening?.let { m -> runCatching { m.primaryClipDescription?.extras?.getString(TokenExtra) }.getOrNull() }
    pending.toList().forEach { p -> if (p.copy.token != token && listening != null) settle(p, cleared = false) }
  }

  /**
   * Copia [text] come dato riservato, che si cancella dopo [expireAfter] se è ancora negli appunti. [label] è l'etichetta
   * del clip (non si vede da nessuna parte, ma i gestori degli appunti la leggono): di serie vuota, così non racconta cosa
   * è stato copiato.
   */
  fun copy(
    context: Context,
    text: CharSequence,
    label: CharSequence = "",
    expireAfter: Duration = DefaultExpiry,
  ): FluidSensitiveCopy {
    require(expireAfter.isPositive()) { "la scadenza deve essere positiva" }
    val manager = context.getSystemService(ClipboardManager::class.java)
      ?: error("il servizio degli appunti non c'è")
    installCallbacks(context)
    val token = UUID.randomUUID().toString()
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply {
      putBoolean(SensitiveExtra, true)
      putString(TokenExtra, token)
    }
    // Le copie di prima sono sostituite da questa: non si cancellano più da sole (sarebbe cancellare questa).
    pending.toList().forEach { settle(it, cleared = false) }
    manager.setPrimaryClip(clip)
    val millis = expireAfter.inWholeMilliseconds
    val copy = FluidSensitiveCopy(token, SystemClock.elapsedRealtime() + millis, expireAfter)
    lateinit var entry: Pending
    val tick = Runnable { expire(entry) }
    val handler = Handler(Looper.getMainLooper())
    entry = Pending(copy, manager, handler, tick)
    pending += entry
    if (listening !== manager) {
      listening?.removePrimaryClipChangedListener(clipListener)
      manager.addPrimaryClipChangedListener(clipListener)
      listening = manager
    }
    handler.postDelayed(tick, millis)
    return copy
  }

  /** Cancella subito gli appunti di [copy], se sono ancora i suoi. */
  fun clearNow(copy: FluidSensitiveCopy) {
    pending.firstOrNull { it.copy === copy }?.let { expire(it) }
  }

  private fun expire(entry: Pending) {
    if (!pending.contains(entry)) return
    entry.handler.removeCallbacks(entry.tick)
    val description = runCatching { entry.manager.primaryClipDescription }.getOrNull()
    when {
      description == null && resumed == 0 && callbacksInstalled -> entry.due = true // in secondo piano: si aspetta
      description == null -> settle(entry, cleared = false) // gli appunti sono già vuoti
      description.extras?.getString(TokenExtra) == entry.copy.token -> {
        clear(entry.manager)
        settle(entry, cleared = true)
      }
      else -> settle(entry, cleared = false) // c'è altro: non si tocca
    }
  }

  private fun settle(entry: Pending, cleared: Boolean) {
    entry.handler.removeCallbacks(entry.tick)
    pending.remove(entry)
    entry.copy.wasCleared = cleared
    entry.copy.isPending = false
    if (pending.isEmpty()) {
      listening?.removePrimaryClipChangedListener(clipListener)
      listening = null
    }
  }

  private fun clear(manager: ClipboardManager) {
    val done = Build.VERSION.SDK_INT >= 28 && runCatching { manager.clearPrimaryClip() }.isSuccess
    if (!done) runCatching { manager.setPrimaryClip(ClipData.newPlainText("", "")) }
  }

  private fun installCallbacks(context: Context) {
    val app = context.applicationContext as? Application ?: return
    if (installedOn === app) return
    app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) {
        resumed++
        // Di ritorno in primo piano: le cancellazioni rimaste in sospeso si rileggono ora.
        Handler(Looper.getMainLooper()).post {
          pending.filter { it.due || it.copy.remainingMillis() == 0L }.forEach { expire(it) }
        }
      }
      override fun onActivityPaused(activity: Activity) { resumed = (resumed - 1).coerceAtLeast(0) }
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    })
    // Chi copia lo fa da un'attività in primo piano.
    resumed = 1
    installedOn = app
  }

  /** Per i test: dimentica le copie in corso e lo stato del primo piano. */
  internal fun resetForTesting() {
    pending.toList().forEach { settle(it, cleared = false) }
  }
}

/** Copia [text] come dato riservato che si cancella dopo [expireAfter] (di serie 60 s): [FluidSensitiveClipboard.copy]. */
fun Context.fluidCopySensitive(
  text: CharSequence,
  label: CharSequence = "",
  expireAfter: Duration = FluidSensitiveClipboard.DefaultExpiry,
): FluidSensitiveCopy = FluidSensitiveClipboard.copy(this, text, label, expireAfter)

/**
 * Il tasto della copia delicata (F8.3): il tasto con esito di Copia ([FluidActionPreset.Copy]) che copia [text] come dato
 * riservato, poi **dice «Copiato» e fa vedere il tempo che resta** nello stesso vetro, col pieno che avanza del contatore
 * «Annulla» ([FluidUndoCountdown]); TalkBack legge «Copiato, si cancella fra 58 s». Alla fine gli appunti si cancellano
 * (se sono ancora quelli) e il tasto torna Copia. Toccato mentre conta, copia di nuovo e il tempo riparte.
 *
 * @param text il dato, letto al tocco (non resta in un parametro della composizione più del necessario).
 * @param expireAfter il tempo; null: una copia normale (non riservata e senza scadenza), con la sola spunta.
 * @param label la scritta accanto all'icona; null: un tondo di vetro.
 */
@Composable
fun FluidCopyButton(
  text: () -> String,
  modifier: Modifier = Modifier,
  label: String? = null,
  expireAfter: Duration? = FluidSensitiveClipboard.DefaultExpiry,
  labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
) {
  val context = LocalContext.current
  var copy by remember { mutableStateOf<FluidSensitiveCopy?>(null) }
  var outcome by remember { mutableStateOf<FluidActionState>(FluidActionState.Idle) }
  val current = copy?.takeIf { it.isPending }
  val countdown = current?.let { c ->
    val timing = remember(c) {
      FluidNotificationCountdown(c.duration.inWholeMilliseconds, c.expiresAtElapsedRealtime - c.duration.inWholeMilliseconds)
    }
    val state = rememberFluidUndoCountdownState(timing)
    FluidUndoCountdown(
      state = state,
      onUndo = { copy = context.fluidCopySensitive(text(), expireAfter = expireAfter ?: FluidSensitiveClipboard.DefaultExpiry) },
      label = labels.copied,
      accessibilityLabel = labels.copied,
      remainingLabel = { seconds -> labels.clearsIn(seconds.toInt()) },
    )
  }
  LaunchedEffect(outcome) {
    if (outcome != FluidActionState.Idle) {
      kotlinx.coroutines.delay(1_000L)
      outcome = FluidActionState.Idle
    }
  }
  FluidActionButton(
    preset = FluidActionPreset.Copy,
    state = outcome,
    onClick = {
      if (expireAfter != null) {
        copy = context.fluidCopySensitive(text(), expireAfter = expireAfter)
      } else {
        val manager = context.getSystemService(ClipboardManager::class.java)
        outcome = if (manager != null && runCatching { manager.setPrimaryClip(ClipData.newPlainText("", text())) }.isSuccess) {
          FluidActionState.Succeeded
        } else FluidActionState.Failed
      }
    },
    modifier = modifier,
    label = label,
    backdrop = backdrop,
    undo = countdown,
  )
}
