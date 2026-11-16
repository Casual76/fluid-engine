package dev.antigravity.fluidengine.sample.plane

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel

/** Il tag di logcat della pagina: `adb logcat -s FluidPiano`. */
internal const val PlaneLogTag = "FluidPiano"

/**
 * Le nove scene della pagina «Piano flottante» (piano «iOS in Android», F3.6). Ognuna mette davanti un risultato
 * osservabile di F3: [key] è il valore di `plane.scene`, [command] il nome del cambio di stato che la scena sa fare,
 * [voice] cosa si guarda. Ogni scena cambia **solo lo stato** (un `if`, una distanza, un fondo): nessuna animazione è
 * scritta qui, le fa il contenitore.
 */
internal enum class PlaneScene(val key: String, val title: String, val command: String, val voice: String) {
  OneTwo(
    "uno-due", "Un tasto diventa due", "Modifica / Fatto",
    "«Fatto» nasce da «Modifica» (entro lo spacing) e se ne stacca col collo; al ritorno vi confluisce",
  ),
  Capsules(
    "capsule", "Due capsule si fondono e si dividono", "Unisci / Dividi",
    "due capsule con il loro id: accostate si fondono in una sagoma, allontanate si dividono passando per un collo",
  ),
  Appear(
    "comparsa", "Un tasto compare per lente", "Mostra / Nascondi",
    "un tasto lontano dagli altri compare per lente al suo posto (contorno da più largo, rifrazione che sale) e scompare",
  ),
  Union(
    "unione", "Tre viste in una sagoma", "Unisci / Separa",
    "tre vetri a distanza maggiore dello spacing: con `fluidGlassUnion` diventano un grappolo solo",
  ),
  Context(
    "contesto", "Una barra cambia contesto", "Contesto A / B",
    "le icone che cambiano si sfocano e si incrociano, quella comune resta nitida; «Fatto» nasce dalla barra",
  ),
  Uniform(
    "adattamento", "Adattamento uniforme", "Fondo chiaro / scuro",
    "tre vetri fermi sopra una foto che passa da chiara a scura: girano tutti nello stesso fotogramma, con una sonda sola",
  ),
  Clear(
    "clear", "Clear accanto a Regular", "Fondo chiaro / scuro",
    "il limpido accanto al vetro di serie sopra una foto, dentro e fuori dal contenitore",
  ),
  Concentric(
    "concentriche", "Forme concentriche", "Margini stretti / larghi",
    "un pannello con `fluidContainerShape` e dentro tre vetri concentrici a margini diversi: i raggi seguono il margine",
  ),
  Interrupt(
    "interruzione", "Interruzione", "Cambia tre volte",
    "il tasto che diventa due, con due cambi a metà viaggio: riparte da dove si trova, senza scatti",
  ),
  ;

  /** La scena dopo questa, in giro. */
  fun next(): PlaneScene = entries[(ordinal + 1) % entries.size]
}

/** Le tre distanze di fusione della scelta di gusto di F3 (stretta, media, larga), più una misura qualsiasi. */
internal object PlaneSpacing {
  /** Stretta: i vetri sono un grappolo solo quando si toccano. */
  val Narrow: Dp = 0.dp

  /** Media: quella di serie del contenitore. */
  val Medium: Dp = FluidGlassContainerDefaults.Spacing

  /** Larga: l'esempio più largo di Apple (40 pt): fusi già a riposo quando sono vicini. */
  val Wide: Dp = 40.dp

  /** Il nome della distanza, o i dp se non è una delle tre. */
  fun label(spacing: Dp): String = when (spacing) {
    Narrow -> "stretta"
    Medium -> "media"
    Wide -> "larga"
    else -> "${spacing.value.toInt()} dp"
  }

  /** La distanza dopo questa nel giro stretta → media → larga. */
  fun next(spacing: Dp): Dp = when (spacing) {
    Narrow -> Medium
    Medium -> Wide
    else -> Narrow
  }
}

/**
 * Ciò che fa **rinascere** la scena: la scena, il tema, lo spacing del contenitore e il vetro chiesto. Lo stato della
 * scena e l'automatico vivono in [PlaneLive]: cambiarli è proprio ciò che si guarda.
 */
internal data class PlaneConfig(
  val scene: PlaneScene = PlaneScene.OneTwo,
  val dark: Boolean = false,
  val spacing: Dp = PlaneSpacing.Medium,
  val model: FluidGlassModel = FluidGlassModel.Liquid,
) {
  /** Una riga leggibile, per logcat e per il sottotitolo. */
  val key: String
    get() = "${scene.key} · ${if (dark) "scuro" else "chiaro"} · spacing ${PlaneSpacing.label(spacing)} · ${model.name}"
}

/**
 * Lo stato della scena e il suo automatico. [step] è **lo stato** che la scena legge: pari è la partenza, dispari l'altro
 * stato (la scena lo traduce in un `if`, in una distanza o in un fondo). Il cambio lo fa solo [change], che scrive la riga
 * `STATO`.
 */
@Stable
internal class PlaneLive(
  /** Ogni quanto l'automatico cambia lo stato, in ms; 0 = spento. */
  autoMs: Long = 0L,
  /** Fra un cambio e l'altro di un comando che interrompe, in ms; 0 = un cambio solo per comando. */
  interruptMs: Long = 0L,
) {
  var step by mutableIntStateOf(0)
    private set

  var autoMs by mutableLongStateOf(autoMs)

  var interruptMs by mutableLongStateOf(interruptMs)

  /** Lo stato «acceso» della scena (il secondo dei due). */
  val on: Boolean get() = step % 2 == 1

  /** Un cambio di stato; [log] riceve la riga da scrivere, con lo stato nuovo. */
  fun change(log: (Int) -> Unit = {}) {
    step += 1
    log(step)
  }

  /** Torna allo stato di partenza: lo fa la pagina quando la scena rinasce, senza scrivere `STATO`. */
  fun reset() {
    step = 0
  }
}

/** Le richieste di lancio della pagina, lette da `PlaneLaunch.parse`. */
internal data class PlaneStart(
  val autoMs: Long = 0L,
  val interruptMs: Long = 0L,
)

internal object PlaneLaunch {
  const val ExtraScene = "plane.scene"
  const val ExtraTheme = "plane.theme"
  const val ExtraSpacing = "plane.spacing"
  const val ExtraModel = "plane.model"
  const val ExtraAuto = "plane.auto"
  const val ExtraInterrupt = "plane.interrupt"

  /** L'intervallo dell'automatico quando si chiede solo «acceso»: lascia arrivare il viaggio più lungo (470 ms) e guardarlo fermo. */
  const val DefaultAutoMs = 2_000L

  /** Il passo di un comando che interrompe: metà del viaggio di contesto (`FluidMotion.PlaneContextMillis`, 300 ms). */
  const val DefaultInterruptMs = 150L

  private val truthy = setOf("true", "1", "on", "yes", "si", "sì", "acceso")
  private val falsy = setOf("false", "0", "off", "no", "spento")

  /**
   * Gli extra di lancio (solo nella build di debug). Un valore sconosciuto lascia quello di serie: una pagina che non
   * parte per un refuso in un comando di prova è peggio di una che parte di serie. [inheritedDark] è il tema della
   * galleria, [inherited] il vetro che chiede (`gallery.model`).
   */
  fun parse(
    inheritedDark: Boolean,
    inherited: FluidGlassModel,
    extra: (String) -> String?,
  ): Pair<PlaneConfig, PlaneStart> {
    fun value(key: String): String? = extra(key)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val scene = value(ExtraScene)?.let { name ->
      PlaneScene.entries.firstOrNull { it.key == name || it.name.lowercase() == name }
    } ?: PlaneScene.OneTwo
    val dark = when (value(ExtraTheme)) {
      "scuro", "dark" -> true
      "chiaro", "light" -> false
      else -> inheritedDark
    }
    val spacing = when (val name = value(ExtraSpacing)) {
      null -> PlaneSpacing.Medium
      "stretta", "narrow" -> PlaneSpacing.Narrow
      "media", "medium" -> PlaneSpacing.Medium
      "larga", "wide" -> PlaneSpacing.Wide
      else -> name.removeSuffix("dp").trim().replace(',', '.').toFloatOrNull()
        ?.takeIf { it.isFinite() && it >= 0f }?.coerceAtMost(200f)?.dp ?: PlaneSpacing.Medium
    }
    val model = when (value(ExtraModel)) {
      "classic" -> FluidGlassModel.Classic
      "liquid" -> FluidGlassModel.Liquid
      else -> inherited
    }
    val auto = millisOf(value(ExtraAuto), DefaultAutoMs, min = 300L)
    // La scena «interruzione» interrompe di serie; le altre solo se lo si chiede (`plane.interrupt true` o i ms).
    val interrupt = millisOf(value(ExtraInterrupt), DefaultInterruptMs, min = 16L)
      ?: if (scene == PlaneScene.Interrupt) DefaultInterruptMs else 0L
    return PlaneConfig(scene, dark, spacing, model) to PlaneStart(autoMs = auto ?: 0L, interruptMs = interrupt)
  }

  /** «acceso» → [default]; un numero → quei ms (mai sotto [min]); «spento» → 0; altro o niente → null (di serie). */
  private fun millisOf(text: String?, default: Long, min: Long): Long? = when {
    text == null -> null
    text in truthy -> default
    text in falsy -> 0L
    else -> text.removeSuffix("ms").trim().toLongOrNull()?.let { if (it <= 0L) 0L else it.coerceIn(min, 60_000L) }
  }
}

/**
 * Chi guarda il contenitore da fuori per scrivere le righe `VIAGGIO`, `FINE` e `RIPOSO`: logica pura, che riceve i
 * contatori di `FluidGlassContainerDiagnostics` a ogni controllo e dice che cosa è successo dall'ultimo.
 *
 * Perché così: la diagnostica del contenitore non avvisa, conta. Un viaggio partito o ripartito fa salire i contatori (e
 * pubblica il riposo d'arrivo nello stesso fotogramma); la fine del viaggio pubblica solo un riposo nuovo, a contatori
 * fermi. Quindi un riposo nuovo con un viaggio in volo è la fine, senza viaggio in volo è il primo riposo (o un aspetto
 * cambiato). L'istante della fine è quello del controllo: preciso al passo del controllo, non al fotogramma.
 */
internal class PlaneJourneyWatch(journeys: Long, retargets: Long, rest: Any?) {
  enum class Event { None, Started, Retargeted, Finished, Rest }

  private var journeys = journeys
  private var retargets = retargets
  private var rest = rest

  /** Un viaggio partito e non ancora arrivato. */
  var inFlight: Boolean = false
    private set

  /** Che cosa è cambiato da quanto visto l'ultima volta; [rest] si confronta per identità (un riposo nuovo è una lista nuova). */
  fun observe(journeys: Long, retargets: Long, rest: Any?): Event {
    val event = when {
      retargets > this.retargets -> Event.Retargeted
      journeys > this.journeys -> Event.Started
      rest !== this.rest -> if (inFlight) Event.Finished else Event.Rest
      else -> Event.None
    }
    when (event) {
      Event.Started, Event.Retargeted -> inFlight = true
      Event.Finished -> inFlight = false
      else -> Unit
    }
    this.journeys = journeys
    this.retargets = retargets
    this.rest = rest
    return event
  }
}
