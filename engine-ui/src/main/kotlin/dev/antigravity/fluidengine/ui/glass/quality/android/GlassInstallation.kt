package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.startup.Initializer
import java.io.Closeable
import java.util.IdentityHashMap

/** AndroidX Startup inizializza prima dell'Activity, anche se il primo elemento è un visore senza tema. */
internal class GlassStartup : Initializer<FluidGlassResources> {
  override fun create(context: Context) = FluidGlassResources.get(context)
  override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

internal class GlassActivities(private val process: FluidGlassResources) : Application.ActivityLifecycleCallbacks {
  private val started = IdentityHashMap<Activity, Closeable>()

  override fun onActivityStarted(activity: Activity) {
    process.foreground()
    if (!started.containsKey(activity)) started[activity] = process.acquire(activity.window)
  }
  override fun onActivityStopped(activity: Activity) { started.remove(activity)?.close() }
  override fun onActivityDestroyed(activity: Activity) { started.remove(activity)?.close() }
  override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
  override fun onActivityResumed(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}

/** Le Dialog Compose hanno un decor proprio ma ereditano i CompositionLocal dell'Activity.
 * Risolvere la Window dalla View della composizione evita di addebitare i frame a quella sottostante. */
internal val LocalGlassHostView = staticCompositionLocalOf<View?> { null }

@Composable
internal fun ObserveGlassWindow(activityToo: Boolean = false) {
  val view = LocalView.current
  if (LocalInspectionMode.current || LocalGlassHostView.current === view) return
  val window = remember(view, activityToo) { glassWindowFor(view, activityToo) } ?: return
  val process = remember(view) { FluidGlassResources.get(view.context) }
  DisposableEffect(process, window) {
    val lease = process.acquire(window)
    onDispose { lease.close() }
  }
}

/** Segnale diretto dello scroll Compose, inclusa l'inerzia. Un solo handle per gesto, condiviso fra host annidati. */
@Composable
internal fun ObserveGlassScroll(list: LazyListState) {
  if (LocalInspectionMode.current || !list.isScrollInProgress) return
  val view = LocalView.current
  DisposableEffect(view, list) {
    val lease = glassWindowFor(view, true)?.let { FluidGlassResources.get(view.context).scrollStarted(it) }
    onDispose { lease?.close() }
  }
}

internal fun glassWindowFor(view: View, activityToo: Boolean): Window? {
  var parent: View? = view
  while (parent != null) {
    if (parent is DialogWindowProvider) return parent.window
    parent = parent.parent as? View
  }
  if (activityToo) {
    var context = view.context
    while (context is android.content.ContextWrapper) {
      if (context is Activity) return context.window
      context = context.baseContext
    }
  }
  return null
}
