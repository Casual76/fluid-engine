package dev.antigravity.fluidengine.viewer.archive

import android.os.ParcelFileDescriptor
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import dev.antigravity.fluidengine.viewer.archive.sevenz.SevenZArchiveEntry
import dev.antigravity.fluidengine.viewer.archive.sevenz.SevenZFile
import java.io.IOException
import java.io.OutputStream

internal object SevenZipAes {
  const val available = true
  private const val MaxDecoderMemoryKiB = 64 * 1024

  suspend fun unlock(source: FluidViewerSource, index: ArchiveIndex, password: CharArray): ArchiveIndex =
    read(source, password) { archive, compressedSize ->
      val budget = ArchiveBudget(compressedSize)
      val items = mutableListOf<ArchiveItem>()
      var hidden = 0
      for ((ordinal, entry) in archive.entries.withIndex()) {
        currentCoroutineContext().ensureActive()
        if (ordinal >= ArchiveMaxEntries) throw ArchiveProblem.Limit.error()
        if (entry.kind == ArchiveKind.File) budget.addEntry(entry.size)
        val item = entry.item(ordinal)
        if (item == null) hidden++ else items += item
      }
      index.copy(items = items, hiddenPaths = hidden, compressedSize = compressedSize,
        protection = ArchiveProtection.Entries, problem = null, sevenZipFallback = true)
    }

  suspend fun extract(
    source: FluidViewerSource,
    items: List<ArchiveItem>,
    password: CharArray,
    write: suspend (ArchiveItem, suspend (OutputStream) -> Unit) -> Unit,
  ) {
    if (items.isEmpty()) return
    read(source, password) { archive, compressedSize ->
      val budget = ArchiveBudget(compressedSize)
      val metadata = archive.entries.toList()
      // Validate the cumulative size before traversing solid blocks, including entries not being extracted.
      for (entry in metadata) {
        if (entry.kind == ArchiveKind.File) budget.addEntry(entry.size)
      }
      val targets = items.associateBy { it.ordinal }
      val buffer = ByteArray(ArchiveBufferSize)
      for (ordinal in 0..items.maxOf { it.ordinal }) {
        currentCoroutineContext().ensureActive()
        val entry = archive.nextEntry ?: throw ArchiveProblem.Changed.error()
        val item = targets[ordinal] ?: continue
        if (entry.kind != ArchiveKind.File || entry.item(ordinal)?.path != item.path || entry.size != item.bytes) {
          throw ArchiveProblem.Changed.error()
        }
        write(item) { output ->
          var written = 0L
          while (true) {
            currentCoroutineContext().ensureActive()
            val count = archive.read(buffer)
            if (count < 0) break
            written += count
            if (written > entry.size) throw ArchiveProblem.Broken.error()
            output.write(buffer, 0, count)
          }
          if (written != entry.size) throw ArchiveProblem.Broken.error()
        }
      }
    }
  }

  private suspend fun <T> read(
    source: FluidViewerSource,
    password: CharArray,
    use: suspend (SevenZFile, Long) -> T,
  ): T {
    return try {
      ParcelFileDescriptor.AutoCloseInputStream(source.seekable()).use { input ->
        val size = input.channel.size()
        SevenZFile.builder().setSeekableByteChannel(input.channel).setMaxMemoryLimitKiB(MaxDecoderMemoryKiB)
          .setPassword(password).get().use { use(it, size) }
      }
    } catch (e: ArchiveReadFailure) {
      throw e
    } catch (e: MemoryLimitException) {
      throw ArchiveProblem.Limit.error(e)
    } catch (e: PasswordRequiredException) {
      throw ArchiveProblem.Locked.error(e)
    } catch (e: IOException) {
      throw when {
        e.message == "7z AES work factor exceeds supported limit" -> ArchiveProblem.Limit.error(e)
        // R2 · Commons Compress non dice mai «password sbagliata»: con la password sbagliata dà «Compressed data is
        // corrupt» o «no Header», con un file tronco «nextHeaderOffset is out of bounds». Se l'archivio non si legge
        // nemmeno senza password è rotto, e lo si dice invece di richiedere la password all'infinito. Un errore dentro
        // i dati cifrati resta «password sbagliata», come per 7-Zip («Wrong password?»): i due casi non si separano.
        brokenWithoutPassword(source) -> ArchiveProblem.Broken.error(e)
        else -> ArchiveProblem.WrongPassword.error(e)
      }
    }
  }

  /**
   * R2 · Vero se l'archivio non si apre nemmeno senza password: firma, intestazione in coda o suo CRC rotti, file tronco.
   * Un'intestazione cifrata che chiede la password vuol dire che fin lì l'archivio è sano.
   */
  private suspend fun brokenWithoutPassword(source: FluidViewerSource): Boolean = try {
    ParcelFileDescriptor.AutoCloseInputStream(source.seekable()).use { input ->
      SevenZFile.builder().setSeekableByteChannel(input.channel).setMaxMemoryLimitKiB(MaxDecoderMemoryKiB).get().close()
    }
    false
  } catch (e: CancellationException) {
    throw e
  } catch (_: PasswordRequiredException) {
    false
  } catch (_: MemoryLimitException) {
    false
  } catch (_: IOException) {
    true
  } catch (_: Exception) {
    false
  }

  private val SevenZArchiveEntry.kind: ArchiveKind
    get() {
      // 7-Zip stores the Unix mode in the high word of Windows attributes, including symlinks.
      val mode = if (hasWindowsAttributes) (windowsAttributes ushr 16) and 0xf000 else 0
      return when {
        isAntiItem -> ArchiveKind.Special
        mode == 0xa000 || hasWindowsAttributes && windowsAttributes and 0x400 != 0 -> ArchiveKind.Link
        isDirectory -> ArchiveKind.Folder
        mode == 0 || mode == 0x8000 -> ArchiveKind.File
        else -> ArchiveKind.Special
      }
    }

  private fun SevenZArchiveEntry.item(ordinal: Int): ArchiveItem? {
    val path = archiveSafePath(name.orEmpty()) ?: return null
    val modified = if (hasLastModifiedDate) lastModifiedDate.time / 1000 else null
    return ArchiveItem(ordinal, path, kind, size, modified, encrypted = kind == ArchiveKind.File && hasStream())
  }
}
