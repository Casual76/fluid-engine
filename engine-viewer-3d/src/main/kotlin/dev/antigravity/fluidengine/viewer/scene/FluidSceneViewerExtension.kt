package dev.antigravity.fluidengine.viewer.scene

import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import java.util.ServiceLoader

/** Optional companion mode for a 3D file, discovered once through [ServiceLoader]. */
interface FluidSceneViewerExtension {
  /** A separate set of controls for each opened model, with no UI side effects until composed. */
  fun create(model: FluidSceneModel): FluidSceneViewerControls
}

/** Connects a companion mode to the existing viewer toolbar and the page lifecycle. */
interface FluidSceneViewerControls {
  val overlayVisible: Boolean
  fun actions(toolbar: FluidToolbarScope)

  /**
   * F6C.20 · Vero se [actions] mette ora un tasto nella barra (pubblicalo come stato di Compose). La barra del visore
   * tiene tre vetri (R5 B11: «•••», il centro, condividi): quando c'è il tasto dell'estensione il centro è suo, e i
   * comandi del modello stanno in «•••». Di serie vero, il caso prudente.
   */
  val showsAction: Boolean get() = true

  /** [active] is false for a page that is off screen or being dismissed. */
  @Composable
  fun Overlay(active: Boolean)
}

internal val sceneViewerExtension: FluidSceneViewerExtension? by lazy {
  val service = FluidSceneViewerExtension::class.java
  ServiceLoader.load(service, service.classLoader).firstOrNull()
}
