package dev.antigravity.fluidengine.notify

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.os.Handler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// F8.19 · Dentro o fuori: l'app è in primo piano se almeno una sua attività è avviata (fra `onStart` e `onStop`). L'uscita
// si dichiara dopo [FluidNotifyProfile.OutsideDelayMs], come `ProcessLifecycleOwner`: un'attività che si ricrea non fa
// lampeggiare le notifiche. L'entrata è subito: al rientro la pillola riprende il posto della notifica senza aspettare.
// Niente libreria nuova (lifecycle-process): bastano le richiamate dell'Application.

/** Se l'app è fuori dal primo piano, letto dalle attività avviate. */
internal class FluidAppForeground(private val handler: Handler) : Application.ActivityLifecycleCallbacks {
  private val state = MutableStateFlow(initialOutside())
  private var started = 0
  private val goOutside = Runnable { if (started == 0) set(true) }

  /** Vero quando nessuna attività dell'app è in vista. */
  val outside: StateFlow<Boolean> = state.asStateFlow()

  /** Chiamata a ogni cambio, sul thread principale. */
  var onChange: ((Boolean) -> Unit)? = null

  private fun set(outside: Boolean) {
    if (state.value == outside) return
    state.value = outside
    onChange?.invoke(outside)
  }

  /** Ascolta le attività di [application]. */
  fun attach(application: Application) {
    application.unregisterActivityLifecycleCallbacks(this)
    application.registerActivityLifecycleCallbacks(this)
  }

  override fun onActivityStarted(activity: Activity) {
    started++
    handler.removeCallbacks(goOutside)
    set(false)
  }

  override fun onActivityStopped(activity: Activity) {
    started = (started - 1).coerceAtLeast(0)
    if (started == 0) {
      handler.removeCallbacks(goOutside)
      handler.postDelayed(goOutside, FluidNotifyProfile.OutsideDelayMs)
    }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
  override fun onActivityResumed(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
  override fun onActivityDestroyed(activity: Activity) = Unit

  /** Le prove lo spostano a mano. */
  internal fun force(outside: Boolean) {
    handler.removeCallbacks(goOutside)
    set(outside)
  }

  private companion object {
    /**
     * Prima della prima richiamata (l'engine installato tardi, con un'attività già in vista) si chiede al sistema
     * l'importanza del processo: in vista se è «foreground» o «visible».
     */
    fun initialOutside(): Boolean = runCatching {
      val info = ActivityManager.RunningAppProcessInfo()
      ActivityManager.getMyMemoryState(info)
      info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }.getOrDefault(true)
  }
}
