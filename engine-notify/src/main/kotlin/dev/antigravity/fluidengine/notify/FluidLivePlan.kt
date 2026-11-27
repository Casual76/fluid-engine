package dev.antigravity.fluidengine.notify

import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLivePoint
import dev.antigravity.fluidengine.foundation.live.FluidLiveStage
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import kotlin.math.roundToInt

// F8.19 · `attivita-fuori-dall-app`: cosa diventa un'attività viva in una notifica, deciso da una funzione pura per ogni
// generazione di Android. Il builder di Android ([FluidLiveUpdates]) si limita ad applicare il piano; le prove JVM
// controllano il piano per le tre generazioni, quelle Robolectric la notifica vera.

/**
 * Le tre generazioni di Android per le notifiche di un'attività, come le chiama il piano. Non è un `enum` (regola 3.11).
 *
 * - [Classic], Android 8–12 (API 26–32): la notifica con l'avanzamento, senza permesso da chiedere;
 * - [Permission], Android 13–15 (API 33–35): la stessa, ma serve il permesso `POST_NOTIFICATIONS`, e da Android 14
 *   l'utente può togliere anche una notifica «in corso»;
 * - [Live], Android 16 (API 36) e oltre: il Live Update promosso, con la barra a tappe (`Notification.ProgressStyle`) e il
 *   gettone nella barra di stato.
 */
class FluidNotifyGeneration private constructor(val name: String, val firstSdk: Int) {
  override fun equals(other: Any?): Boolean = other is FluidNotifyGeneration && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidNotifyGeneration($name)"

  companion object {
    /** Android 8–12. */
    val Classic: FluidNotifyGeneration = FluidNotifyGeneration("classic", 26)

    /** Android 13–15. */
    val Permission: FluidNotifyGeneration = FluidNotifyGeneration("permission", 33)

    /** Android 16 e oltre. */
    val Live: FluidNotifyGeneration = FluidNotifyGeneration("live", 36)

    /** La generazione di [sdk]. */
    fun of(sdk: Int): FluidNotifyGeneration = when {
      sdk >= Live.firstSdk -> Live
      sdk >= Permission.firstSdk -> Permission
      else -> Classic
    }
  }
}

/** La barra di una notifica: [max] unità, [progress] fatte; le tappe e i punti valgono solo con `ProgressStyle`. */
internal data class FluidLiveBar(
  val max: Int,
  val progress: Int,
  val indeterminate: Boolean,
  val stages: List<FluidLiveStage> = emptyList(),
  val points: List<FluidLivePoint> = emptyList(),
)

/** Il cronometro della notifica: il sistema lo fa scorrere da solo verso (o da) [whenWallMillis]. */
internal data class FluidLiveChronometer(val whenWallMillis: Long, val countDown: Boolean)

/** Un tasto della notifica di un'attività. [unfollow] è «Smetti di seguire», che aggiunge l'engine. */
internal data class FluidLivePlanAction(val label: String, val key: String, val destructive: Boolean, val unfollow: Boolean = false)

/**
 * Cosa contiene la notifica di un'attività viva. Due piani uguali danno la stessa notifica: l'engine non ripubblica.
 *
 * @property icon il nome dell'icona piccola: il genere ([FluidLiveKind.name]), oppure `done` e `failed` per l'esito.
 */
internal data class FluidLivePlan(
  val generation: FluidNotifyGeneration,
  val title: String,
  val text: String?,
  val ongoing: Boolean,
  val requestPromotion: Boolean,
  val bar: FluidLiveBar?,
  val chip: String?,
  val chronometer: FluidLiveChronometer?,
  val category: String,
  val actions: List<FluidLivePlanAction>,
  val icon: String,
  val timeoutMillis: Long?,
  val finished: Boolean,
) {
  /** Vero se il piano differisce da [other] solo nell'avanzamento (e nel gettone, che lo ripete). */
  fun onlyProgressDiffers(other: FluidLivePlan?): Boolean {
    if (other == null || other.bar == null || bar == null) return false
    return copy(bar = bar.copy(progress = 0), chip = null) == other.copy(bar = other.bar.copy(progress = 0), chip = null)
  }
}

/** La chiave del tasto «Smetti di seguire». */
internal const val FluidLiveUnfollowKey: String = "fluid:unfollow"

/**
 * Il piano della notifica di [update] su Android [sdk], a [nowElapsedMillis] (`elapsedRealtime`) e [nowWallMillis]. Con
 * [outcome] è la notifica della fine; un esito silenzioso non ha notifica (null).
 *
 * Le regole dei Live Update (developer.android.com, «Live Updates», fiducia alta): in corso (`ongoing`), col titolo,
 * senza colori pieni, senza viste su misura, mai il riassunto di un gruppo, uno stile fra standard e `ProgressStyle`, un
 * canale sopra `IMPORTANCE_MIN`. L'engine le rispetta tutte per costruzione; chiede la promozione solo per
 * un'attività che la vuole ([FluidLiveUpdate.wantsPromotion]) e solo finché è in corso.
 */
internal fun fluidLivePlan(
  update: FluidLiveUpdate,
  sdk: Int,
  nowElapsedMillis: Long,
  nowWallMillis: Long,
  labels: FluidNotifyLabels,
  outcome: FluidLiveOutcome? = null,
): FluidLivePlan? {
  if (outcome != null && outcome.silent) return null
  val generation = FluidNotifyGeneration.of(sdk)
  val category = fluidLiveCategory(update.kind, generation, finished = outcome != null)
  if (outcome != null) {
    return FluidLivePlan(
      generation = generation,
      title = update.title,
      text = outcome.message ?: if (outcome.failed) labels.failed else labels.done,
      ongoing = false,
      requestPromotion = false,
      bar = null,
      chip = null,
      chronometer = null,
      category = category,
      actions = emptyList(),
      icon = if (outcome.failed) "failed" else "done",
      timeoutMillis = FluidNotifyProfile.EndedLingerMs,
      finished = true,
    )
  }
  val time = update.time
  val bar: FluidLiveBar? = when {
    update.stages.isNotEmpty() -> {
      val max = update.stages.sumOf { it.length }
      // Senza avanzamento e senza tempo la barra gira, anche con le tappe (il KDoc di `FluidLiveUpdate.progress`; R2).
      val spinning = update.progress == null && time == null && !update.paused
      FluidLiveBar(
        max = max,
        progress = ((update.progress ?: 0f).coerceIn(0f, 1f) * max).roundToInt(),
        indeterminate = spinning,
        stages = update.stages,
        points = update.points.filter { it.position <= max },
      )
    }
    update.progress != null -> FluidLiveBar(max = 100, progress = update.percent ?: 0, indeterminate = false)
    update.indeterminate && !update.paused -> FluidLiveBar(max = 0, progress = 0, indeterminate = true)
    else -> null
  }
  val chronometer = if (time != null && time.running && !update.paused) {
    time.wallAnchorMillis(nowElapsedMillis, nowWallMillis)?.let { FluidLiveChronometer(it, time.countsDown) }
  } else {
    null
  }
  val text = if (update.paused) {
    // In pausa il tempo è fermo: anche un tempo che corre si mostra fermo, al valore di [nowElapsedMillis] (chi pubblica
    // passa l'istante della pausa: `FluidLiveUpdates`). Prima spariva dal testo (R2).
    val still = time?.let { fluidLiveClock(it.shownMillis(nowElapsedMillis)) }
    listOfNotNull(labels.paused, still, update.text).joinToString(" · ")
  } else {
    update.text
  }
  val promote = generation == FluidNotifyGeneration.Live && update.wantsPromotion
  val chip = when {
    !promote -> null
    update.chip != null -> update.chip
    chronometer != null -> null // il gettone mostra il cronometro da solo
    update.percent != null -> labels.percent(update.percent!!)
    else -> null
  }
  val own = update.actions.map { FluidLivePlanAction(it.label, it.key, it.destructive) }
  val actions = if (update.origin.followed) {
    own.take(MaxActions - 1) + FluidLivePlanAction(labels.unfollow, FluidLiveUnfollowKey, destructive = false, unfollow = true)
  } else {
    own.take(MaxActions)
  }
  return FluidLivePlan(
    generation = generation,
    title = update.title,
    text = text,
    ongoing = true,
    requestPromotion = promote,
    bar = bar,
    chip = chip,
    chronometer = chronometer,
    category = category,
    actions = actions,
    icon = update.kind.name,
    timeoutMillis = null,
    finished = false,
  )
}

/** Android mostra al massimo tre tasti in una notifica. */
private const val MaxActions = 3

/**
 * La categoria di Android: il viaggio è `navigation`, il timer `stopwatch` da Android 16 (prima `progress`), il resto
 * `progress`; l'esito `status`. Le stringhe sono quelle di `Notification.CATEGORY_*`: `stopwatch` c'è solo da API 36.
 */
internal fun fluidLiveCategory(kind: FluidLiveKind, generation: FluidNotifyGeneration, finished: Boolean): String = when {
  finished -> "status"
  kind == FluidLiveKind.Navigation -> "navigation"
  kind == FluidLiveKind.Timer && generation == FluidNotifyGeneration.Live -> "stopwatch"
  else -> "progress"
}

/** Un tempo fermo come lo scrive un orologio: «4:05», «1:02:03». */
internal fun fluidLiveClock(millis: Long): String {
  val total = (millis / 1000L).coerceAtLeast(0L)
  val hours = total / 3600
  val minutes = (total % 3600) / 60
  val seconds = total % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
