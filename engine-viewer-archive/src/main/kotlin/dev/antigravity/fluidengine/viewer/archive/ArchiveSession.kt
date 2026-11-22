package dev.antigravity.fluidengine.viewer.archive

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

internal sealed interface ArchiveAction {
  data object UnlockHeaders : ArchiveAction
  data object Export : ArchiveAction
  data class Open(val item: ArchiveItem) : ArchiveAction
}

internal class ArchiveSession(
  val request: FluidViewerRequest,
  val format: ArchiveFormat,
  private val reader: NativeArchive,
  initialIndex: ArchiveIndex,
  private val onExtractAll: (suspend (FluidArchiveExtraction) -> Unit)?,
) {
  var index by mutableStateOf(initialIndex)
    private set
  var encoding by mutableStateOf(ArchiveEncoding.Cp437)
    private set
  var preview by mutableStateOf<FluidViewerDocument?>(null)
    private set
  var progress by mutableStateOf<FluidArchiveProgress?>(null)
    private set
  var busy by mutableStateOf(false)
    private set
  var pending by mutableStateOf<ArchiveAction?>(null)
    private set
  var problem by mutableStateOf<ArchiveProblem?>(null)
    private set
  private val operations = Mutex()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var password: CharArray? = null
  private var sharedArchive: File? = null
  @Volatile private var closed = false

  val details get() = archiveDetails()
  var unlocked by mutableStateOf(false)
    private set
  val canExport: Boolean get() = onExtractAll != null && canRead && index.tree.extractable.isNotEmpty() && index.problem == null
  val canUnlockHeaders: Boolean get() = index.protection == ArchiveProtection.SevenZipHeaders && SevenZipAes.available
  private val canRead: Boolean get() = !index.streamedZip && index.protection != ArchiveProtection.Rar &&
    (!index.sevenZipFallback || SevenZipAes.available)

  fun canOpen(item: ArchiveItem): Boolean = canRead && item.kind == ArchiveKind.File && (item.bytes ?: 0) <= ArchiveEntryLimit

  fun changeEncoding(value: ArchiveEncoding) {
    if (encoding == value) return
    run {
      val updated = withContext(Dispatchers.IO) { operations.withLock { reader.index(value) } }
      encoding = value
      index = updated
      replacePassword(null)
    }
  }

  fun requestOpen(item: ArchiveItem) {
    if (!canOpen(item)) return
    requestAction(ArchiveAction.Open(item), item.encrypted || index.sevenZipFallback)
  }

  fun requestExport() {
    if (!canExport) return
    requestAction(ArchiveAction.Export, index.tree.extractable.any { it.encrypted } || index.sevenZipFallback)
  }

  fun requestUnlock() { if (!busy && canUnlockHeaders) pending = ArchiveAction.UnlockHeaders }

  fun requestOpenOriginal() {
    request.openWith?.let { open -> open(); return }
    run {
      val file = sharedArchive ?: stageOriginal()
      sharedArchive = file
      openPreviewWith(file, request.source.name, request.source.mimeType ?: archiveMime())
    }
  }

  private fun requestAction(action: ArchiveAction, protected: Boolean) {
    if (busy || pending != null) return
    if (protected && !unlocked) pending = action
    else run {
      try {
        perform(action, password)
      } catch (failure: ArchiveReadFailure) {
        if (protected && failure.problem == ArchiveProblem.WrongPassword) {
          replacePassword(null)
          pending = action
        }
        throw failure
      }
    }
  }

  fun dismissPassword() { pending = null; problem = null }

  fun submitPassword(value: CharArray) {
    val action = pending
    if (action == null || busy || closed) {
      value.fill('\u0000')
      return
    }
    pending = null
    run {
      try {
        perform(action, value)
        replacePassword(value.copyOf())
      } catch (failure: ArchiveReadFailure) {
        if (failure.problem == ArchiveProblem.WrongPassword || failure.problem == ArchiveProblem.Locked) pending = action
        throw failure
      } finally {
        value.fill('\u0000')
      }
    }
  }

  private suspend fun perform(action: ArchiveAction, key: CharArray?) {
    when (action) {
      ArchiveAction.UnlockHeaders -> {
        val unlockedIndex = withContext(Dispatchers.IO) {
          operations.withLock { SevenZipAes.unlock(request.source, index, checkNotNull(key)) }
        }
        index = unlockedIndex
      }
      ArchiveAction.Export -> export(key)
      is ArchiveAction.Open -> open(action.item, key)
    }
  }

  private suspend fun open(item: ArchiveItem, key: CharArray?) {
    var staged: File? = null
    var opened: FluidViewerDocument? = null
    try {
      withContext(Dispatchers.IO) {
        val output = createPreviewFile("entry-")
        staged = output
        copy(listOf(item), key) { _, write -> output.outputStream().use { write(it) } }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.name.substringAfterLast('.', "").lowercase(Locale.ROOT))
        val source = FluidViewerSource.of(output, name = item.name, mimeType = mime, deleteOnClose = true)
        opened = FluidViewerRegistry.open(request.context, source, request.options,
          openWith = { openPreviewWith(output, item.name, mime) }, media = request.media)
      }
      if (!closed) {
        dismissPreview()
        preview = opened
        opened = null
        staged = null
      }
    } finally {
      opened?.close()
      staged?.delete()
    }
  }

  private suspend fun stageOriginal(): File {
    val limit = request.options.sourceMaxCopyBytes.coerceIn(0, ArchiveTotalLimit)
    var temp: File? = null
    try {
      val file = withContext(Dispatchers.IO) {
        if ((request.source.sizeBytes ?: 0) > limit) throw ArchiveProblem.Limit.error()
        val staged = createPreviewFile("archive-").also { temp = it }
        request.source.open().use { input ->
          staged.outputStream().use { output ->
            val buffer = ByteArray(ArchiveBufferSize)
            var size = 0L
            while (true) {
              currentCoroutineContext().ensureActive()
              val count = input.read(buffer)
              if (count < 0) break
              size += count
              if (size > limit) throw ArchiveProblem.Limit.error()
              output.write(buffer, 0, count)
            }
          }
        }
        staged
      }
      temp = null
      return file
    } finally { temp?.delete() }
  }

  private fun createPreviewFile(prefix: String): File {
    val directory = File(request.context.cacheDir, "fluid-archive-preview")
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw ArchiveProblem.Unreadable.error()
    return File.createTempFile(prefix, ".preview", directory)
  }

  private fun archiveMime(): String = when (format) {
    ArchiveFormat.Zip -> "application/zip"
    ArchiveFormat.SevenZip -> "application/x-7z-compressed"
    ArchiveFormat.Rar -> "application/vnd.rar"
    ArchiveFormat.Tar -> "application/x-tar"
    ArchiveFormat.Compressed -> "application/octet-stream"
  }

  private fun openPreviewWith(file: File, name: String, mime: String?) {
    val context = request.context
    try {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fluidviewer.archive", file, name)
      val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime ?: "application/octet-stream")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      context.startActivity(Intent.createChooser(intent, "Apri con…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
      problem = ArchiveProblem.NoApp
    } catch (_: SecurityException) {
      problem = ArchiveProblem.Unreadable
    }
  }

  private suspend fun export(key: CharArray?) {
    val callback = onExtractAll ?: return
    val items = index.tree.extractable
    val files = items.map { FluidArchiveEntry(it.path, it.bytes) }
    // Check a password on a bounded entry before calling the app. Wrong credentials never open an output.
    val sample = items.firstOrNull { it.encrypted && (it.bytes ?: 0L) > 0L }
    if (key != null && sample != null && !unlocked) {
      withContext(Dispatchers.IO) { copy(listOf(sample), key) { _, write -> write(DiscardOutput) } }
    }
    val transfer = FluidArchiveExtraction(files) { output ->
      var completed = 0
      var bytes = 0L
      progress = FluidArchiveProgress(0, files.size, 0)
      withContext(Dispatchers.IO) {
        copy(items, key) { item, write ->
          val sink = output(FluidArchiveEntry(item.path, item.bytes))
          sink.use { write(it) }
          bytes += item.bytes ?: 0
          completed++
          withContext(Dispatchers.Main.immediate) { progress = FluidArchiveProgress(completed, files.size, bytes) }
        }
      }
    }
    try {
      callback(transfer)
    } finally {
      transfer.finish()
      progress = null
    }
  }

  private suspend fun copy(
    items: List<ArchiveItem>,
    key: CharArray?,
    write: suspend (ArchiveItem, suspend (OutputStream) -> Unit) -> Unit,
  ) = operations.withLock {
    currentCoroutineContext().ensureActive()
    if (closed) throw ArchiveProblem.Unreadable.error()
    if (index.sevenZipFallback) {
      SevenZipAes.extract(request.source, items, key ?: throw ArchiveProblem.Locked.error(), write)
    } else reader.extract(items, encoding, key, write)
  }

  private fun run(block: suspend () -> Unit) {
    if (busy || closed) return
    busy = true
    problem = null
    scope.launch {
      try {
        block()
      } catch (failure: ArchiveReadFailure) {
        problem = failure.problem
        if (pending == null && failure.problem == ArchiveProblem.WrongPassword) replacePassword(null)
      } catch (_: IOException) {
        problem = ArchiveProblem.Unreadable
      } catch (_: SecurityException) {
        problem = ArchiveProblem.Unreadable
      } finally {
        busy = false
      }
    }
  }

  private fun replacePassword(value: CharArray?) {
    password?.fill('\u0000')
    password = value
    unlocked = value != null
  }

  fun dismissPreview() { preview?.close(); preview = null }

  fun close() {
    closed = true
    scope.cancel()
    dismissPreview()
    sharedArchive?.delete()
    sharedArchive = null
    replacePassword(null)
  }
}

private object DiscardOutput : OutputStream() {
  override fun write(b: Int) = Unit
  override fun write(b: ByteArray, off: Int, len: Int) = Unit
}
