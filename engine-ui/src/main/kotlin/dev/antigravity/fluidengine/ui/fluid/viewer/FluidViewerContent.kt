package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind

/** Metadata supplied by the owner of the file. A null size is unknown, zero means empty. */
data class FluidFileInfo(
  val name: String,
  val mimeType: String? = null,
  val sizeBytes: Long? = null,
  val kind: FluidFileKind = FluidFileKind.from(mimeType, name),
)

/** A player owned by the app. Publish changing properties as Compose state. Times are milliseconds. */
interface FluidMediaSession {
  val playing: Boolean
  val positionMillis: Long
  val durationMillis: Long?
  val canSeek: Boolean
  val error: String? get() = null
  fun play()
  fun pause()
  fun seekTo(positionMillis: Long)
}

/** Zero-based pages. Both suspending operations must be safe to call on the main dispatcher. */
interface FluidPageSource {
  val count: Int
  suspend fun size(index: Int): IntSize
  suspend fun render(index: Int, target: IntSize): ImageBitmap
}

/** Read at most [count] lines from a zero-based offset. A short chunk marks the end of the file. */
interface FluidTextSource {
  val language: String? get() = null
  val lineCount: Int? get() = null
  suspend fun readLines(start: Int, count: Int): List<String>
}

/**
 * An open contract so applications and future presets may supply another representation.
 * The frame owns geometry and gestures. A renderer can read the sources without owning their lifecycle.
 */
interface FluidViewerContent {
  class Image(
    val aspectRatio: Float? = null,
    val draw: @Composable () -> Unit,
  ) : FluidViewerContent {
    constructor(painter: Painter, description: String? = null, aspectRatio: Float? = painter.intrinsicSize
      .takeIf { it.isSpecified && it.width > 0f && it.height > 0f }?.let { it.width / it.height }) :
      this(aspectRatio, { Image(painter, description, Modifier.fillMaxSize()) })
  }

  /** A non-null video surface identifies a video, even while it is buffering. */
  class Media(
    val session: FluidMediaSession,
    val videoSurface: (@Composable () -> Unit)? = null,
    val artwork: (@Composable () -> Unit)? = null,
  ) : FluidViewerContent

  class Pages(val source: FluidPageSource) : FluidViewerContent
  class Text(val source: FluidTextSource) : FluidViewerContent
  class Unknown(
    val info: FluidFileInfo,
    val openWith: (() -> Unit)? = null,
    val share: (() -> Unit)? = null,
  ) : FluidViewerContent
}

internal val FluidViewerContent.blackBackground: Boolean
  get() = this is FluidViewerContent.Image || this is FluidViewerContent.Media && videoSurface != null
