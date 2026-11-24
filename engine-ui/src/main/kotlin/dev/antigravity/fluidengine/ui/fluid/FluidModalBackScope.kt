package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import dev.antigravity.fluidengine.ui.page.LocalFluidPageTransition

// Una pagina e i suoi editor condividono un ramo del dispatcher. I layer del modale
// usano quello esterno: conservano il gesto predittivo, i sottomenu e i gestori del loro contenuto.
private class ModalBackScope(
  val host: FluidGlassModalHostState,
  val parent: NavigationEventDispatcherOwner?,
  val inPage: Boolean,
)

private val LocalModalBackScope = staticCompositionLocalOf<ModalBackScope?> { null }

@Composable
internal fun FluidPageModalBackScope(content: @Composable () -> Unit) {
  val host = LocalFluidGlassModalHostState.current
  if (host == null || LocalModalBackScope.current?.host === host) {
    content()
    return
  }
  val parent = LocalNavigationEventDispatcherOwner.current
  val scope = remember(host, parent) { ModalBackScope(host, parent, inPage = true) }
  CompositionLocalProvider(LocalModalBackScope provides scope) {
    if (parent == null) content() else {
      val pages = rememberNavigationEventDispatcherOwner(enabled = !host.isPresenting, parent = parent)
      CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides pages, content = content)
    }
  }
}

@Composable
internal fun FluidLayerModalBackScope(host: FluidGlassModalHostState, content: @Composable () -> Unit) {
  val enclosing = LocalModalBackScope.current
  val parent = if (enclosing?.host === host) enclosing.parent else LocalNavigationEventDispatcherOwner.current
  val scope = remember(host, parent) { ModalBackScope(host, parent, inPage = false) }
  CompositionLocalProvider(LocalModalBackScope provides scope) {
    if (parent == null) content()
    else CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides parent, content = content)
  }
}

@Composable
internal fun rememberFluidGlassModalEntry(key: Any? = null): FluidGlassModalEntry {
  val scope = LocalModalBackScope.current
  val inPage = scope != null && scope.inPage && scope.host === LocalFluidGlassModalHostState.current &&
    LocalFluidPageTransition.current != null
  return remember(key, inPage) { FluidGlassModalEntry(atRoot = !inPage) }
}
