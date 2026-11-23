package dev.antigravity.fluidengine.sample.measure

import java.util.Locale

/**
 * Un tratto di un ciclo: un nome ([name], la riga `fase=` in logcat), quanto dura il viaggio dal segnale `INIZIO` al
 * segnale `FINE` ([journeyMillis]) e la quiete che segue prima del tratto dopo ([pauseMillis]).
 *
 * La quiete esiste per lo script di cattura (`tools/qa/perf-glass.sh`): legge i framestats dopo ogni `FINE`, e
 * `dumpsys gfxinfo` lavora sul RenderThread dell'app. Fatto in un viaggio, sporcherebbe proprio i fotogrammi che misura;
 * fatto nella quiete, non tocca niente. Per lo stesso motivo un viaggio sta sotto i 120 fotogrammi che il dump tiene
 * (un secondo a 120 Hz), salvo quelli che durano di più per natura: per loro c'è [probeMillis], un segnale `SONDA` a
 * metà viaggio che chiede un dump in più (come il `primi-frame.gfx` di S9).
 */
internal data class ScenarioPhase(
  val name: String,
  val journeyMillis: Long,
  val pauseMillis: Long,
  val probeMillis: Long? = null,
)

/**
 * Gli scenari fissi della pagina «Misure» (piano «iOS in Android», F7.1): quelli dei bilanci del piano (sezione F7) e
 * le tre prove di F1B di cui il piano chiede di tornare ai numeri della partenza.
 *
 * Ogni scenario monta componenti veri dell'engine e li guida da solo, con lo stato o con un dito finto dentro l'app
 * ([dev.antigravity.fluidengine.sample.material.MaterialFinger]): niente tocchi da fuori, quindi lo stesso moto a ogni
 * lancio. Il nome ([key]) è quello che si passa con `--es measure.scenario`.
 *
 * - [phases]: i tratti di un ciclo, in ordine;
 * - [cycles]: quanti cicli di serie;
 * - [probes]: falso per le prove di F1B. Là il confronto è col `gfxinfo` dell'intera prova presa a mano in F1B (reset,
 *   gesti, un dump alla fine): niente dump nel mezzo, e la cadenza è quella degli script di allora.
 */
internal enum class MeasureScenario(
  val key: String,
  val title: String,
  val detail: String,
  val phases: List<ScenarioPhase>,
  val cycles: Int,
  val probes: Boolean = true,
) {
  ScrollPhotos(
    "scorrimento-foto", "Scorrimento sulle foto",
    "Una lista di foto sotto la barra e la tab bar, trascinata dal dito finto in su e in giù (col lancio).",
    listOf(ScenarioPhase("su", 1_500, 1_200, probeMillis = 700), ScenarioPhase("giu", 1_500, 1_200, probeMillis = 700)),
    cycles = 3,
  ),
  ScrollText(
    "scorrimento-testo", "Scorrimento sul testo",
    "La stessa lista, di solo testo.",
    listOf(ScenarioPhase("su", 1_500, 1_200, probeMillis = 700), ScenarioPhase("giu", 1_500, 1_200, probeMillis = 700)),
    cycles = 3,
  ),
  MenuButton(
    "menu-tasto", "Menu dal tasto",
    "«Ordina», il tasto che diventa il suo menu: si apre al tocco, si chiude con dismiss().",
    listOf(ScenarioPhase("apre", 900, 1_200), ScenarioPhase("chiude", 900, 1_200)),
    cycles = 3,
  ),
  MenuGroup(
    "menu-gruppo", "Menu dal gruppo",
    "Il gruppo della barra che diventa menu: tocco su «Menu del gruppo», chiusura dall'host dei modali.",
    listOf(ScenarioPhase("apre", 900, 1_200), ScenarioPhase("chiude", 900, 1_200)),
    cycles = 3,
  ),
  ContextMenu(
    "contestuale", "Menu contestuale",
    "Una riga tenuta premuta 600 ms: si solleva e apre il menu; chiusura dall'host dei modali.",
    listOf(ScenarioPhase("apre", 1_400, 1_200, probeMillis = 800), ScenarioPhase("chiude", 900, 1_200)),
    cycles = 3,
  ),
  BarContext(
    "barra-contesto", "Cambio di contesto della barra",
    "Sfoglia → seleziona: «Seleziona» e ⋯ diventano condividi, elimina e la spunta, e tornano.",
    listOf(ScenarioPhase("cambia", 900, 1_200), ScenarioPhase("torna", 900, 1_200)),
    cycles = 3,
  ),
  CapsulePress(
    "capsula-premuta", "Pressione continua su una capsula",
    "Il dito finto giù sulla capsula, scivola avanti e indietro per 1,6 s, poi su.",
    listOf(ScenarioPhase("tieni", 2_200, 1_200, probeMillis = 800)),
    cycles = 3,
  ),
  SheetRise(
    "foglio-sale", "Foglio che sale",
    "Il foglio di vetro con due altezze sale alla media e scende, guidato dallo stato.",
    listOf(ScenarioPhase("sale", 1_000, 1_200), ScenarioPhase("scende", 900, 1_200)),
    cycles = 3,
  ),
  SheetDrag(
    "foglio-trascinato", "Foglio trascinato",
    "Il foglio aperto a metà, trascinato dal dito finto fino in alto e riportato giù.",
    listOf(ScenarioPhase("su", 1_200, 1_200, probeMillis = 700), ScenarioPhase("giu", 1_200, 1_200, probeMillis = 700)),
    cycles = 3,
  ),
  EightSlabs(
    "otto-lastre", "Pagina con otto lastre",
    "Otto pannelli di vetro fermi sopra una lista di foto trascinata in su e in giù.",
    listOf(ScenarioPhase("su", 1_500, 1_200, probeMillis = 700), ScenarioPhase("giu", 1_500, 1_200, probeMillis = 700)),
    cycles = 3,
  ),
  PushPop(
    "push-pop", "Push e pop",
    "Una pagina con la barra dichiarata entra sopra l'elenco ed esce, cambiando la pila dell'host delle pagine.",
    listOf(ScenarioPhase("push", 900, 1_200), ScenarioPhase("pop", 900, 1_200)),
    cycles = 3,
  ),
  ZoomCard(
    "zoom-card", "Zoom dalla card",
    "Una card si apre a pagina intera con lo zoom dalla sorgente e ci torna.",
    listOf(ScenarioPhase("apre", 1_000, 1_200), ScenarioPhase("chiude", 1_000, 1_200)),
    cycles = 3,
  ),

  /**
   * F1B (c), «un gruppo di tasti che diventa un menu»: il palco «Tasti → Menù → Pop-up» del Playground, con la cadenza
   * di `moto/f1b/partenza/strumenti/lotto-gfx-cfh.sh` (Menù, Pop-up, Menù, Tasti ogni 1,6 s, due giri).
   */
  F1bGroupMenu(
    "f1b-c", "F1B (c): un gruppo di tasti che diventa un menu",
    "Il palco del Playground che passa da due tasti al menu e al pop-up, ogni 1,6 s: la prova (c) del piano.",
    listOf(
      ScenarioPhase("menu", 1_200, 400), ScenarioPhase("popup", 1_200, 400),
      ScenarioPhase("menu-2", 1_200, 400), ScenarioPhase("tasti", 1_200, 400),
    ),
    cycles = 2,
    probes = false,
  ),

  /** F1B (f), «il menu contestuale»: tenuto 800 ms, 1,4 s di menu, chiusura e 1,8 s, tre volte (`f-riga`). */
  F1bContext(
    "f1b-f", "F1B (f): il menu contestuale",
    "Una riga tenuta 800 ms, il menu aperto 1,4 s, chiuso e 1,8 s di quiete, tre volte: la prova (f) del piano.",
    listOf(ScenarioPhase("apre", 2_200, 0), ScenarioPhase("chiude", 1_800, 0)),
    cycles = 3,
    probes = false,
  ),

  /** F1B (h), «la card che diventa foglio»: la riga «Card che si espande» di Chrome, aperta e chiusa ogni 1,9 s. */
  F1bCard(
    "f1b-h", "F1B (h): la card che diventa foglio",
    "La riga «Card che si espande» toccata, il pop-up aperto 1,9 s e chiuso, tre volte: la prova (h) del piano.",
    listOf(ScenarioPhase("apre", 1_900, 0), ScenarioPhase("chiude", 1_900, 0)),
    cycles = 3,
    probes = false,
  ),

  // F6B.13: gli elementi dinamici che si ripetono spesso, perché F7 misuri anche loro.

  /** F6B.1 e F6B.2: la selezione di una lista con la tab bar della galleria che diventa la capsula delle azioni. */
  Selection(
    "selezione", "Selezione con la tab bar",
    "«Seleziona» e tre righe scelte: la tab bar diventa le azioni e «Fine» la riporta com'era.",
    listOf(ScenarioPhase("entra", 900, 1_200), ScenarioPhase("esce", 900, 1_200)),
    cycles = 3,
  ),

  /** F6B.7: il campo «Messaggio» col composer che cresce di riga in riga e apre il vassoio, senza tastiera. */
  ComposerField(
    "campo-composer", "Campo col composer",
    "Il campo «Messaggio» cresce da una a cinque righe, apre il vassoio di un allegato e torna a una riga.",
    listOf(
      ScenarioPhase("cresce", 1_200, 1_200, probeMillis = 600),
      ScenarioPhase("allegato", 900, 1_200),
      ScenarioPhase("torna", 900, 1_200),
    ),
    cycles = 3,
  ),

  /** F6B.9: la barra sopra la tastiera che cambia contesto, in vista senza tastiera (`alwaysVisible`). */
  KeyboardBar(
    "tastiera-contesto", "Barra sopra la tastiera",
    "La barra sopra la tastiera passa dai suggerimenti alla formattazione e torna, trasformandosi.",
    listOf(ScenarioPhase("cambia", 900, 1_200), ScenarioPhase("torna", 900, 1_200)),
    cycles = 3,
  ),
}

/** Un tratto con il suo posto nella corsa: il ciclo, quanti sono, e i suoi tempi. */
internal data class ScenarioStep(val cycle: Int, val cycles: Int, val phase: ScenarioPhase) {
  /** `2/3`: il ciclo nella riga di logcat. */
  val cycleLabel: String get() = "$cycle/$cycles"
}

/**
 * Tutto ciò che decide una corsa di uno scenario: lo scenario, quanti cicli, l'attesa prima del segnale `PRONTO` e il
 * margine fra `PRONTO` e il primo viaggio (lo script azzera gfxinfo lì dentro).
 */
internal data class ScenarioConfig(
  val scenario: MeasureScenario,
  val cycles: Int = scenario.cycles,
  /** Dal primo fotogramma al segnale `PRONTO`: la scena si compone, i programmi si compilano, lo schermo si ferma. */
  val warmupMillis: Long = DefaultWarmupMillis,
  /** Da `PRONTO` al primo viaggio: il tempo che lo script ha per leggere la riga e azzerare gfxinfo. */
  val leadMillis: Long = DefaultLeadMillis,
  val hud: Boolean = true,
) {
  /** La chiave della corsa, come le righe `RIS` delle scene di F0.6: due righe con la stessa chiave sono la stessa misura. */
  val key: String get() = "scenario=${scenario.key} cycles=$cycles"

  /** La corsa intera, tratto per tratto: ogni ciclo ripete i tratti dello scenario nello stesso ordine. */
  fun timeline(): List<ScenarioStep> =
    (1..cycles).flatMap { cycle -> scenario.phases.map { ScenarioStep(cycle, cycles, it) } }

  /** Quanto dura la corsa dal segnale `PRONTO` all'ultimo `FINE` più la sua quiete, in millisecondi. */
  fun durationMillis(): Long = leadMillis + timeline().sumOf { it.phase.journeyMillis + it.phase.pauseMillis }

  companion object {
    const val ExtraScenario = "measure.scenario"
    private const val Prefix = "measure."
    const val DefaultWarmupMillis = 3_000L
    const val DefaultLeadMillis = 1_500L
    const val MaxCycles = 20

    /**
     * Legge gli extra con [extra]. Nulla se `measure.scenario` manca o non è uno scenario. Come per le scene di F0.6, un
     * valore che non si riconosce resta quello di serie: la riga `PRONTO` dice con che cosa la corsa è partita.
     */
    fun parse(extra: (String) -> String?): ScenarioConfig? {
      fun value(name: String): String? =
        extra(Prefix + name)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

      val scenario = value("scenario")?.let { name -> MeasureScenario.entries.firstOrNull { it.key == name } }
        ?: return null
      val base = ScenarioConfig(scenario)
      return base.copy(
        cycles = value("cycles")?.toIntOrNull()?.coerceIn(1, MaxCycles) ?: base.cycles,
        warmupMillis = value("warmup")?.let(::seconds)?.coerceIn(0L, 60_000L) ?: base.warmupMillis,
        leadMillis = value("lead")?.let(::seconds)?.coerceIn(200L, 10_000L) ?: base.leadMillis,
        hud = when (value("hud")) {
          "off", "false", "0", "no" -> false
          "on", "true", "1", "yes", "si" -> true
          else -> base.hud
        },
      )
    }

    private fun seconds(text: String): Long? =
      text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.let { (it * 1000f).toLong() }
  }
}

/**
 * Le righe che lo script legge, tutte col tag `FluidMisure` e un orologio solo: `t_ns` è `CLOCK_MONOTONIC`, lo stesso
 * di `IntendedVsync` nei framestats. Il formato è quello che `tools/qa/perf-summary.py` e `perf-glass.sh` cercano.
 */
internal object ScenarioLines {
  fun ready(config: ScenarioConfig, nanos: Long): String =
    "SCENARIO PRONTO id=${config.scenario.key} cicli=${config.cycles} " +
      "fasi=${config.scenario.phases.joinToString(",") { it.name }} " +
      "sondaggi=${if (config.scenario.probes) "si" else "no"} t_ns=$nanos"

  fun start(config: ScenarioConfig, step: ScenarioStep, nanos: Long): String = event("INIZIO", config, step, nanos)

  fun probe(config: ScenarioConfig, step: ScenarioStep, nanos: Long): String = event("SONDA", config, step, nanos)

  fun end(config: ScenarioConfig, step: ScenarioStep, nanos: Long, note: String? = null): String =
    event("FINE", config, step, nanos) + (note?.let { " nota=$it" } ?: "")

  fun closed(config: ScenarioConfig, nanos: Long, millis: Long): String =
    "SCENARIO CHIUSO id=${config.scenario.key} cicli=${config.cycles} durata_ms=$millis t_ns=$nanos"

  fun error(config: ScenarioConfig, reason: String): String =
    "SCENARIO ERRORE id=${config.scenario.key} motivo=${reason.replace(' ', '_')}"

  private fun event(kind: String, config: ScenarioConfig, step: ScenarioStep, nanos: Long): String =
    "SCENARIO $kind id=${config.scenario.key} ciclo=${step.cycleLabel} fase=${step.phase.name} t_ns=$nanos"
}
