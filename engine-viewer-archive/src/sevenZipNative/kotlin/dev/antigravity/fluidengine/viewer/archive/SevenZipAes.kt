package dev.antigravity.fluidengine.viewer.archive

import dev.antigravity.fluidengine.viewer.FluidViewerSource
import java.io.OutputStream

/** Used by the comparable APK containing only libarchive. */
@Suppress("UNUSED_PARAMETER")
internal object SevenZipAes {
  const val available = false

  suspend fun unlock(source: FluidViewerSource, index: ArchiveIndex, password: CharArray): ArchiveIndex =
    throw ArchiveProblem.Locked.error()

  suspend fun extract(
    source: FluidViewerSource,
    items: List<ArchiveItem>,
    password: CharArray,
    write: suspend (ArchiveItem, suspend (OutputStream) -> Unit) -> Unit,
  ): Unit = throw ArchiveProblem.Locked.error()
}
