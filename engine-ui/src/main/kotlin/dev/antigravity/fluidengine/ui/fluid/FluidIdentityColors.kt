package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Un colore d'identita' in due versioni: quella da usare su un tema chiaro e quella su un tema scuro.
 *
 * Sono una coppia, e non un colore solo, per la stessa ragione per cui ogni ruolo del tema e' una coppia:
 * un blu che si legge su carta bianca e' quasi invisibile su una superficie nera, e uno che si legge sul
 * nero e' un pastello sbiadito sul bianco. Il lato chiaro e' abbastanza **scuro** da reggere un
 * monogramma bianco (4,5:1) e da staccarsi dalla pagina (3:1); il lato scuro e' abbastanza **chiaro** da
 * reggere un monogramma scuro e da staccarsi dal nero. Lo verificano i test su tutta la [FluidIdentityColors.palette].
 */
@Immutable
data class FluidIdentityColor(val light: Color, val dark: Color) {
  /** Il lato che serve al tema: [dark] se [darkTheme], altrimenti [light]. */
  fun forTheme(darkTheme: Boolean): Color = if (darkTheme) dark else light
}

/**
 * La palette categoriale dell'engine: dodici tinte distinguibili fra loro, per tutto cio' che deve
 * **riconoscersi** senza dire un numero — il fondo di un monogramma, il colore di un account o di una
 * materia, un'etichetta, la serie di un grafico.
 *
 * **Perche' esiste.** Ogni app si inventava i suoi colori «a caso ma stabili» (un hash, una lista di sei
 * tinte scritte nel composable), e nessuno aveva controllato che il monogramma si leggesse sopra: tre
 * app su quattro avevano almeno un fondo giallo con la sigla bianca. Qui i colori sono scelti una volta,
 * a mano, e il contrasto e' una proprieta' provata da un test.
 *
 * **Stabilita': l'ordine e' un contratto.** [indexFor] sceglie la tinta con un hash definito qui sotto, e
 * lo stesso testo da' lo stesso indice **per sempre**, su ogni telefono e in ogni versione dell'engine.
 * Il motivo e' di prodotto, non tecnico: la persona ha imparato che «Anna» e' verde. Cambiare la palette
 * (l'ordine, il numero di tinte, anche una sola tonalita' vistosa) o l'hash **sposta i colori a tutti gli
 * utenti** al primo avvio dopo l'aggiornamento, ed e' un cambio visivo breaking: si fa solo in una
 * versione maggiore e va scritto nel changelog. Un colore nuovo si aggiunge **in coda** — e anche cosi'
 * cambia `indice % dimensione` per tutte le chiavi: nemmeno l'aggiunta e' innocua.
 *
 * **Normalizzazione: di chi chiama.** `"Anna@x.it"` e `"anna@x.it"` sono due chiavi diverse. Chi vuole che
 * siano la stessa persona passa la chiave gia' normalizzata (minuscole, spazi tolti): l'engine non sa se
 * una chiave e' un indirizzo, un nome o un identificatore, e normalizzare quello sbagliato spaccherebbe
 * un'app che distingue le maiuscole.
 *
 * **Usi.** Come fondo di un monogramma ([FluidAvatar]) il lato giusto del tema regge un inchiostro
 * scelto per contrasto; come segno grafico (un punto, una barra di grafico) regge 3:1 contro le
 * superfici del tema. Non e' fatta per **testo** colorato su una superficie: per quello serve il colore
 * del testo del tema.
 */
object FluidIdentityColors {

  /**
   * Le tinte, in ordine di cerchio cromatico (rosso → rosa): tre sono volutamente non sature (oro,
   * lime) perche' sono quelle che un colore «vivo» renderebbe illeggibili sotto una sigla.
   *
   * **L'ordine e' un contratto** (vedi la documentazione dell'oggetto): non riordinare, non inserire in
   * mezzo.
   */
  val palette: List<FluidIdentityColor> = listOf(
    FluidIdentityColor(light = Color(0xFFC62828), dark = Color(0xFFFF8A80)), // 0 rosso
    FluidIdentityColor(light = Color(0xFFB45309), dark = Color(0xFFFFAB66)), // 1 arancio
    FluidIdentityColor(light = Color(0xFF8F6B00), dark = Color(0xFFE6BE4A)), // 2 oro
    FluidIdentityColor(light = Color(0xFF5B7A00), dark = Color(0xFFB7D15A)), // 3 lime
    FluidIdentityColor(light = Color(0xFF2E7D32), dark = Color(0xFF7FCB82)), // 4 verde
    FluidIdentityColor(light = Color(0xFF00796B), dark = Color(0xFF4DB6A0)), // 5 verde acqua
    FluidIdentityColor(light = Color(0xFF00778B), dark = Color(0xFF40CFF0)), // 6 ciano
    FluidIdentityColor(light = Color(0xFF1565C0), dark = Color(0xFF6CB4F7)), // 7 blu
    FluidIdentityColor(light = Color(0xFF3949AB), dark = Color(0xFF8C9EFF)), // 8 indaco
    FluidIdentityColor(light = Color(0xFF6A3FB5), dark = Color(0xFFB794F4)), // 9 viola
    FluidIdentityColor(light = Color(0xFFA0359F), dark = Color(0xFFE08DE3)), // 10 magenta
    FluidIdentityColor(light = Color(0xFFC2185B), dark = Color(0xFFF48FB1)), // 11 rosa
  )

  /**
   * L'indice in [palette] di una chiave: **FNV-1a a 32 bit** sui byte UTF-8 del testo, preso senza
   * segno modulo la dimensione della palette. Definito qui, nel dettaglio, perche' e' il contratto di
   * stabilita': l'hash di `String.hashCode()` e' specificato dalla JVM ma non e' una promessa
   * dell'engine, e un hash «tuo» scelto domani sposterebbe i colori di tutti.
   *
   * La chiave vuota non e' un caso speciale: ha il suo indice, come ogni altra (lo stesso, sempre).
   */
  fun indexFor(key: String): Int =
    ((fluidIdentityHash(key).toLong() and 0xFFFFFFFFL) % palette.size).toInt()

  /** La coppia scelta da una chiave: `palette[indexFor(key)]`. */
  fun colorFor(key: String): FluidIdentityColor = palette[indexFor(key)]

  /**
   * La coppia all'[index] dato, **con giro**: l'indice scelto da una persona (il colore di un account che
   * ha toccato nelle impostazioni) resta valido anche se un domani la palette ha meno tinte, e un indice
   * negativo non esplode.
   */
  fun colorAt(index: Int): FluidIdentityColor = palette[Math.floorMod(index, palette.size)]
}

/** FNV-1a a 32 bit sui byte UTF-8: offset 0x811C9DC5, primo 0x01000193. Vedi [FluidIdentityColors.indexFor]. */
internal fun fluidIdentityHash(key: String): Int {
  var hash = -0x7ee3623b // 0x811C9DC5
  for (byte in key.toByteArray(Charsets.UTF_8)) {
    hash = hash xor (byte.toInt() and 0xFF)
    hash *= 0x01000193
  }
  return hash
}

/**
 * Il colore d'identita' di una [key] nel tema in scope: il lato chiaro o scuro, secondo la luminosita'
 * della superficie (non secondo `isSystemInDarkTheme`, che non sa se l'app ha il suo tema).
 *
 * @see FluidIdentityColors.indexFor per la stabilita' fra versioni.
 */
@Composable
fun fluidIdentityColor(key: String): Color =
  FluidIdentityColors.colorFor(key).forTheme(GlassDefaults.isDarkSurface())

/** Come [fluidIdentityColor] ma per un indice scelto dalla persona; vedi [FluidIdentityColors.colorAt]. */
@Composable
fun fluidIdentityColor(index: Int): Color =
  FluidIdentityColors.colorAt(index).forTheme(GlassDefaults.isDarkSurface())

private fun Color.isDarkInk(): Boolean = luminance() < 0.5f

/** Il contrasto minimo di un monogramma o di una cifra su un riempimento: testo normale, 4,5:1. */
internal const val FluidInkMinContrast = 4.5f

/** Un riempimento e l'inchiostro che ci si legge sopra: un'unita', come ogni coppia del tema. */
@Immutable
internal data class FluidReadableColors(val fill: Color, val ink: Color)

/**
 * L'inchiostro che si legge su [fill], e il riempimento portato — **del minimo che serve** — dove
 * l'inchiostro arriva a [minContrast].
 *
 * Per i colori della palette ([FluidIdentityColors]) il riempimento resta com'e': sono scelti perche'
 * bastino. Per un colore che porta l'app (la tinta d'un account scelta dalla persona, un blu di
 * mezzo tono) nessun inchiostro, nero o bianco, arriva a 4,5:1 sul grigio medio, e l'alternativa a un
 * monogramma illeggibile e' cambiare il fondo del poco che basta: come `legibleFill`, resta
 * riconoscibile come lo stesso colore. Il verso e' quello opposto all'inchiostro: inchiostro scuro →
 * fondo piu' chiaro, inchiostro chiaro → fondo piu' scuro.
 *
 * Pura e fuori dalla composizione: e' aritmetica di colori da provare su tutta la palette e su
 * centinaia di colori a caso, non su un telefono.
 */
internal fun fluidReadableOn(fill: Color, minContrast: Float = FluidInkMinContrast): FluidReadableColors {
  val opaque = fill.copy(alpha = 1f)
  val ink = FluidVividColors.from(opaque).content
  if (fluidContrastRatio(ink, opaque) >= minContrast) return FluidReadableColors(opaque, ink)

  val toward = if (ink.isDarkInk()) Color.White else Color.Black
  var low = 0f
  var high = 1f
  repeat(14) {
    val mid = (low + high) / 2f
    if (fluidContrastRatio(ink, lerp(opaque, toward, mid)) >= minContrast) high = mid else low = mid
  }
  return FluidReadableColors(lerp(opaque, toward, high), ink)
}
