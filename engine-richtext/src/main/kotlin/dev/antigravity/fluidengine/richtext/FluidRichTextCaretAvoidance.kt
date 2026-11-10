package dev.antigravity.fluidengine.richtext

/**
 * Cio' che l'editor misura per tenere la riga del cursore sopra la barra di formattazione: tutto in pixel,
 * le ordinate nelle coordinate della radice di Compose (le stesse per il campo e per la barra).
 *
 * @param caretBottom il fondo del rettangolo del cursore.
 * @param caretHeight l'altezza del rettangolo del cursore (una riga).
 * @param fieldBottom il fondo del campo, **compresa la coda** che si e' gia' tenuto ([CaretBarStep.Reserve]).
 * @param barTop il bordo alto della barra; `NaN` se nessuna barra si e' misurata.
 * @param visibleFieldTop la cima della parte del campo che si vede (ritagliata da chi lo contiene).
 * @param scrollBottom il fondo di chi scorre per primo attorno al campo: il bordo basso del primo
 *   contenitore che taglia il proprio contenuto (una pagina che scorre, un riquadro dentro la pagina). Se
 *   niente taglia niente, il fondo del campo.
 * @param cut vero se [scrollBottom] e' davvero un taglio; falso se niente taglia niente (tutto il contenuto
 *   sta nei suoi contenitori: una pagina corta, o scorsa esattamente fino in fondo) e il bordo di chi
 *   scorre non si vede.
 * @param windowHeight l'altezza della radice: un rettangolo piu' alto non si puo' portare in vista.
 * @param gap l'aria da lasciare fra la riga e la barra.
 */
internal class CaretBarMeasure(
  val caretBottom: Float,
  val caretHeight: Float,
  val fieldBottom: Float,
  val barTop: Float,
  val visibleFieldTop: Float,
  val scrollBottom: Float,
  val cut: Boolean,
  val windowHeight: Float,
  val gap: Float,
)

/** Il prossimo passo per tenere la riga del cursore sopra la barra: vedi [CaretBarAvoidance.next]. */
internal sealed interface CaretBarStep {
  /** Chiedere di portare in vista il cursore con [allowance] pixel in piu' sotto. */
  class Bring(val allowance: Float) : CaretBarStep

  /**
   * Il campo cambia di [space] pixel la coda che si tiene sotto l'ultima riga: di piu' prima di chiedere,
   * di meno (negativo) per restituire quella presa in eccesso per misurare.
   *
   * @param probe vero per la coda presa (e poi restituita) solo per misurare: se il giro si interrompe a
   *   meta', chi la tiene deve restituirla da se'.
   */
  class Reserve(val space: Float, val probe: Boolean = false) : CaretBarStep

  /** Niente da fare: la riga e' sopra la barra, la barra non c'e', o non si puo' fare di piu'. */
  data object Done : CaretBarStep
}

/**
 * Come portare la riga del cursore **sopra la barra di formattazione** e non solo sopra il fondo della
 * pagina, un passo alla volta ([next]). Si muove qualcosa **solo se la riga e' coperta**.
 *
 * **Il problema.** La barra galleggia nell'overlay e la pagina non le lascia uno spazio suo: il campo porta
 * il cursore sopra il fondo di chi scorre (la tastiera, se la pagina si ferma li'), ma fra quel fondo e la
 * riga c'e' la barra. Chi scorre non si conosce (una pagina, un riquadro dentro una pagina, tutti e due), e
 * l'unico modo di parlargli e' chiedere di portare in vista un rettangolo: se il rettangolo e' il cursore
 * **piu' lo spazio che la barra occupa sopra quel fondo**, il cursore finisce sopra la barra.
 *
 * **Quanto spazio** ([CaretBarStep.Bring]). La distanza fra il fondo di chi scorre per primo
 * ([CaretBarMeasure.scrollBottom]: il bordo del primo contenitore che taglia il suo contenuto) e la barra:
 * e' esatta, la riga si ferma **appena sopra** la barra e non a meta' pagina, e basta una richiesta. Se dopo
 * la richiesta la riga e' ancora coperta (un caso che non si e' saputo misurare), lo spazio cresce di quanto
 * e' ancora coperta; mai piu' di [MaxPasses] richieste.
 *
 * **La coda** ([CaretBarStep.Reserve]). Il rettangolo che si chiede deve stare **dentro il campo**. Sotto
 * l'ultima riga il campo finisce: un rettangolo che sporge oltre il contenuto di chi scorre non si puo'
 * portare in vista, e Compose lo passa al contenitore di fuori **come se quello di dentro ce l'avesse
 * fatta**, cosi' la pagina scorre di una misura sbagliata (troppo poco, o molto troppo). Quindi, quando la
 * riga coperta e' vicina alla fine, il campo si tiene prima sotto l'ultima riga una coda vuota alta quanto
 * serve al rettangolo: e' lo spazio che la pagina non ha lasciato alla barra, e da' a chi scorre la corsa
 * per portare l'ultima riga sopra di lei. Si tiene solo quando serve (riga coperta e vicina alla fine).
 *
 * **Quando niente taglia niente** (una pagina corta che non scorre ancora, o scorsa esattamente fino in
 * fondo) il bordo di chi scorre non si vede. Allora la coda si allunga per un momento fino al fondo della
 * finestra: il contenuto supera di sicuro il bordo, che cosi' si misura; fatta la richiesta, l'eccesso si
 * restituisce e resta solo la coda che serve.
 *
 * E' Kotlin puro: si prova sul computer con pagine finte (`FluidRichTextCaretAvoidanceTest`).
 */
internal class CaretBarAvoidance {
  private var pass = 0
  private var reserves = 0
  private var allowance = 0f
  private var probe = 0f
  private var trimmed = false

  /** Cosa fare adesso, date le misure di adesso. Dopo ogni passo l'editor rimisura e richiama. */
  fun next(measure: CaretBarMeasure): CaretBarStep {
    if (measure.barTop.isNaN()) return CaretBarStep.Done
    val line = measure.barTop - measure.gap
    val covered = measure.caretBottom - line
    if (covered <= Epsilon) return finish(measure)
    // Sopra la barra non si vede nessun pezzo del campo (una barra posata in alto, un campo tutto sotto di
    // lei): non c'e' un posto sopra la barra dove portare la riga.
    if (pass == 0 && measure.barTop <= measure.visibleFieldTop) return finish(measure)
    if (pass >= MaxPasses) return finish(measure)
    if (pass == 0 && !measure.cut && probe == 0f) {
      // Niente taglia niente: la coda fino al fondo della finestra, per vedere dove taglia chi scorre.
      val room = measure.windowHeight - measure.fieldBottom
      if (room > Epsilon) {
        probe = room
        return CaretBarStep.Reserve(room, probe = true)
      }
    }
    // Al primo giro la stima (rifatta se nel frattempo e' cresciuta la coda: il layout e' cambiato); dopo
    // una richiesta che non e' bastata, quanto manca ancora.
    var wanted = if (pass == 0) (measure.scrollBottom - line).coerceAtLeast(0f) else allowance + covered
    // Un rettangolo piu' alto della finestra non entra in nessuna pagina.
    wanted = wanted.coerceAtMost((measure.windowHeight - measure.caretHeight).coerceAtLeast(0f))
    val overflow = measure.caretBottom + wanted - measure.fieldBottom
    if (overflow > Epsilon && reserves < MaxReserves) {
      reserves++
      return CaretBarStep.Reserve(overflow)
    }
    allowance = wanted
    pass++
    return CaretBarStep.Bring(allowance)
  }

  /** Alla fine: la coda presa in eccesso per misurare torna indietro (resta quella che serve al rettangolo). */
  private fun finish(measure: CaretBarMeasure): CaretBarStep {
    if (probe > 0f && !trimmed) {
      trimmed = true
      val excess = if (pass > 0) measure.fieldBottom - (measure.caretBottom + allowance) else probe
      if (excess > Epsilon) return CaretBarStep.Reserve(-excess.coerceAtMost(probe), probe = true)
    }
    return CaretBarStep.Done
  }

  companion object {
    /** Quante richieste al massimo per uno spostamento del cursore. */
    const val MaxPasses: Int = 4

    /**
     * Quante volte al massimo si allunga la coda per uno spostamento: se il campo non puo' crescere (ha
     * un'altezza fissa) la coda lo accorcia invece di allungarlo, e non si insiste.
     */
    const val MaxReserves: Int = 4

    /** Sotto il pixel la riga non e' coperta: gli scorrimenti sono a pixel interi. */
    const val Epsilon: Float = 0.5f
  }
}

/**
 * Quale capo della selezione portare in vista: quello che si e' appena mosso (chi trascina la maniglia
 * d'inizio guarda l'inizio), altrimenti la fine.
 */
internal fun movedSelectionOffset(previousStart: Int?, previousEnd: Int?, start: Int, end: Int): Int =
  if (previousEnd != null && previousStart != null && end == previousEnd && start != previousStart) start else end
