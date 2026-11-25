package dev.antigravity.fluidengine.notify.testing

import dev.antigravity.fluidengine.notify.FluidNotify
import dev.antigravity.fluidengine.notify.FluidNotifyTestHooks

/**
 * Per le prove delle app (e della galleria) con engine-notify: un processo nuovo a ogni prova, l'app spostata dentro e
 * fuori dal primo piano a mano, il conto delle pubblicazioni. Non serve in un'app vera.
 */
object FluidNotifyTesting {
  /** Dimentica l'istanza installata: la prova dopo parte come un processo nuovo. */
  fun reset() = FluidNotifyTestHooks.reset()

  /** Sposta l'app fuori ([outside] vero) o dentro il primo piano, senza aspettare le attività. */
  fun setOutside(notify: FluidNotify, outside: Boolean) = FluidNotifyTestHooks.setOutside(notify, outside)

  /** Quante notifiche ha pubblicato l'engine dall'installazione (aggiornamenti compresi). */
  fun postCount(notify: FluidNotify): Int = FluidNotifyTestHooks.postCount(notify)
}
