package dev.antigravity.fluidengine.diagnostics

import android.os.Looper
import android.os.Process
import kotlin.system.exitProcess

/**
 * Il gestore delle eccezioni non prese (F8.24). Scrive il crash nella coda e **lo passa sempre** al gestore che c'era
 * prima ([previous]): quello di Android, che chiude l'app e mostra «L'app si è arrestata», o quello di un'altra
 * libreria. Non mangia mai un crash: un'app che resta viva dopo un'eccezione non presa è in uno stato che nessuno ha
 * pensato (rischio scritto nella scheda `crash-e-lentezze`).
 *
 * L'ordine conta: prima la riga sul disco (breve e sincrona: dopo il processo non c'è più), poi il gestore di prima, in
 * un `finally`, anche se la scrittura fallisce. Un crash dentro la scrittura non ne nasconde un altro.
 *
 * @param record scrive il crash; può lanciare, e non cambia niente.
 * @param previous il gestore di prima. Nullo solo dove nessuno l'ha messo (le prove): allora il processo si chiude
 *   come farebbe Android.
 */
internal class FluidCrashHandler(
  private val previous: Thread.UncaughtExceptionHandler?,
  private val record: (thread: Thread, error: Throwable, mainThread: Boolean) -> Unit,
) : Thread.UncaughtExceptionHandler {
  @Volatile
  private var handling = false

  override fun uncaughtException(thread: Thread, error: Throwable) {
    try {
      // Un secondo crash mentre si scrive il primo (dentro la scrittura stessa) non si riscrive: si passa e basta.
      if (!handling) {
        handling = true
        record(thread, error, isMainThread(thread))
      }
    } catch (_: Throwable) {
      // La diagnostica non deve mai cambiare cosa succede a un crash.
    } finally {
      val next = previous
      if (next != null) {
        next.uncaughtException(thread, error)
      } else {
        Process.killProcess(Process.myPid())
        exitProcess(10)
      }
    }
  }

  private fun isMainThread(thread: Thread): Boolean =
    runCatching { thread == Looper.getMainLooper()?.thread }.getOrDefault(thread.name == "main")

  internal companion object {
    /**
     * Mette [handler] al posto del gestore di serie e lo ritorna. Se il gestore attuale è già uno dei nostri (una seconda
     * `install`), lo lascia: due copie scriverebbero lo stesso crash due volte.
     */
    fun install(record: (Thread, Throwable, Boolean) -> Unit): FluidCrashHandler {
      val current = Thread.getDefaultUncaughtExceptionHandler()
      if (current is FluidCrashHandler) return current
      val handler = FluidCrashHandler(current, record)
      Thread.setDefaultUncaughtExceptionHandler(handler)
      return handler
    }
  }
}
