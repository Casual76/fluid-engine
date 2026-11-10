package dev.antigravity.fluidengine.ui.glass.backdrop

import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts how often glass re-captures what is behind it.
 *
 * A capture is the expensive half of the material: a replay of the backdrop's whole recording plus
 * the effect chain over it. The design is that a pane captures only when something it shows has
 * changed — its backdrop redrew, or it moved relative to it — and holds still otherwise. This is how
 * that claim is checked on a real device instead of being believed: switch [enabled] on in a debug
 * build, leave a screen alone, and [totalCaptures] must stop moving.
 *
 * Off by default, and then it costs one volatile read per capture.
 *
 * Dalla 4.0.0 dice anche se gli shader del vetro **compilano** su questo dispositivo ([shaderVerdicts],
 * [compileAllShaders]): un driver che rifiuta una sorgente fa ripiegare il vetro su un materiale più povero,
 * e quel ripiego deve vedersi. Queste letture non dipendono da [enabled].
 */
object FluidGlassDiagnostics {

  @Volatile
  var enabled: Boolean = false

  private val captures = AtomicLong()
  private val exports = AtomicLong()

  /** Captures taken since [enabled] was first switched on. */
  val totalCaptures: Long get() = captures.get()

  /**
   * Materiali pubblicati (registrati) da quando [enabled] e' acceso: un pannello che registra cio'
   * che disegna dietro al contenuto perche' del vetro dentro di lui lo campioni. Dalla 2.13.0 si
   * registra solo se qualcuno lo guarda, quindi su una schermata senza vetro su vetro questo numero
   * non si muove, e a schermo fermo non si muove mai.
   */
  val totalExports: Long get() = exports.get()

  internal fun onCapture() {
    if (enabled) captures.incrementAndGet()
  }

  internal fun onExport() {
    if (enabled) exports.incrementAndGet()
  }

  /** I nomi di tutte le sorgenti AGSL dell'engine, nell'ordine in cui [compileAllShaders] le prova. */
  val shaderSourceNames: List<String> get() = GlassShaderSources.all.map { it.name }

  /**
   * I verdetti che il vetro ha già chiesto in questo processo: nome della sorgente → `null` se il compilatore
   * del dispositivo l'ha accettata, o il suo errore (il messaggio è quello del compilatore, con riga e
   * motivo). Una sorgente che nessuno ha ancora usato non compare: la sonda compila solo quando serve.
   *
   * Esiste perché dalla 3.0.0 alla 3.1.2 un rifiuto è passato inosservato per una settimana, con una riga
   * di log a livello avviso e nessun altro segno: un ripiego si deve poter leggere da fuori, e chi lo
   * mostra (la galleria, un test) lo legge da qui.
   */
  val shaderVerdicts: Map<String, Throwable?> get() = GlassFieldHealth.verdicts()

  /**
   * Compila adesso ogni sorgente AGSL dell'engine che non sia già stata provata, e restituisce tutti i
   * verdetti: nome della sorgente → `null` o l'errore del compilatore. Ogni rifiuto scrive anche una riga
   * di log a livello errore (tag `FluidGlass`).
   *
   * Solo diagnostica: lo chiama il test strumentato della galleria a ogni sessione sul dispositivo, perché
   * le sorgenti sono interne all'engine e il test sta in un altro modulo. Costa una compilazione per
   * sorgente la prima volta, niente le successive. Sotto Android 13 non c'è un compilatore AGSL e il
   * risultato è vuoto.
   */
  fun compileAllShaders(): Map<String, Throwable?> = GlassFieldHealth.compileAll()
}
