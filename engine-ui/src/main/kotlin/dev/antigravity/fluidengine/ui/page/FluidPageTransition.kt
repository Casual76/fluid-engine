package dev.antigravity.fluidengine.ui.page

/**
 * Come una pagina entra e, soprattutto, come se ne va col dito.
 *
 * Tutte e tre sono opache dal primo all'ultimo fotogramma: nessuna sfuma, nessuna sfoca la pagina
 * sotto. Due pagine leggibili una attraverso l'altra sono la cosa che fa sembrare l'app un prototipo
 * (vedi le regole), e una sfocatura a tutto schermo e' il fotogramma piu' caro che l'app possa
 * disegnare proprio quando ne ha meno.
 */
enum class FluidPageTransition {
  /**
   * Di serie. Entra da destra (da sinistra in RTL) mentre quella sotto arretra di un terzo; il back
   * e' quello di iOS: la pagina sta sotto il dito, pixel per pixel, finche' il dito non la lascia.
   */
  Slide,

  /**
   * Prima rimpicciolisce sul posto (fino al 90%, nel primo 30% della corsa), poi scivola via; la
   * pagina sotto sta ferma e piena, perche' attorno a una pagina piu' piccola dello schermo si vede lei.
   * Per le pagine che vogliono dire "sono una cosa sola che si chiude", non "torno indietro di un livello".
   */
  ShrinkSlide,

  /**
   * Si apre dal punto toccato (scala 0,91 → 1, opaca, la pagina sotto ferma) e torna indietro come
   * [ShrinkSlide]: la carta che si espande, per chi la vuole tenere.
   */
  Expand,
}

/**
 * Da dove si puo' tirare indietro una pagina col dito, dentro l'app.
 *
 * Il back di sistema non c'entra: con la navigazione a gesti i tocchi sul bordo non arrivano
 * nemmeno all'app, e il gesto di sistema segue comunque il dito.
 *
 * **Un `BackHandler` non e' piu' un motivo per spegnerlo** (3.0). Il dito che supera la soglia manda
 * "indietro" al dispatcher come il sistema, quindi una pagina con una conferma di uscita (un editor
 * con modifiche, un'importazione a meta') la chiede anche al dito: la pagina segue il dito, torna a
 * posto e il suo gestore decide. [Off] resta per chi non vuole che la pagina si muova affatto.
 */
enum class FluidBackSwipe {
  /**
   * Da qualunque punto della pagina, purche' nessun figlio si sia preso il dito per primo (un
   * cursore, un interruttore, una riga che scorre di lato). Uno scorrimento orizzontale arrivato al
   * suo inizio passa la mano alla pagina. **Non e' piu' di serie** (3.0.3): va chiesto, per le
   * pagine che lo vogliono davvero.
   */
  Anywhere,

  /**
   * Di serie (3.0.3), come il gesto di sistema: solo dai primi 20 dp del bordo d'ingresso. Righe,
   * carte e scorrimenti orizzontali in mezzo alla pagina non c'entrano.
   */
  EdgeOnly,

  /** Mai col dito dentro l'app (il back di sistema e il tasto restano). */
  Off,
}

/**
 * L'ordine fra pari: le schede.
 *
 * Due destinazioni che sono schede non sono una dentro l'altra, quindi non si aprono come pagine e
 * non si chiudono col dito: fanno il passo laterale di sempre (13% della larghezza, opaca sopra,
 * quella che esce scivola e sfuma in 150 ms). Chi le ordina e' l'app, che ha gia' la sua funzione di
 * ordinamento delle schede.
 *
 * [direction] torna un numero positivo se [to] sta dopo [from] nell'ordine (arriva dal lato di
 * fine), negativo se sta prima, **zero se le due non sono pari**: allora valgono le regole delle
 * pagine.
 */
fun interface FluidPeerMove<in K> {
  fun direction(from: K, to: K): Int
}

/** Che movimento sta facendo l'host, adesso. */
enum class FluidPageMove {
  /** A riposo: una pagina sola si vede. */
  None,

  /** Una pagina nuova sta arrivando. */
  Push,

  /** Una pagina se ne sta andando da sola (tasto indietro, 3 tasti, Android 13 e prima). */
  Pop,

  /** Il dito tiene la pagina, o l'ha appena lasciata e la molla sta finendo il suo lavoro. */
  Back,

  /** Un passo fra schede. */
  Peer,
}
