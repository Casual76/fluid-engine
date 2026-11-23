package dev.antigravity.fluidengine.sample.viewer

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.archive.FluidArchiveExtraction
import dev.antigravity.fluidengine.viewer.archive.FluidArchiveFamily
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The sample owns the destination. It never replaces documents in the folder the user selects. */
@Composable
internal fun VisoriArchiveExport() {
  val resolver = LocalContext.current.contentResolver
  val chooser = remember { ArchiveDestination() }
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    chooser.pending?.complete(uri)
  }
  DisposableEffect(resolver, picker) {
    val previous = FluidViewerRegistry.families.firstOrNull { it.id == "archive" }
    val family = FluidArchiveFamily(onExtractAll = { extraction ->
      if (chooser.pending == null) {
        val pending = CompletableDeferred<Uri?>()
        chooser.pending = pending
        try {
          picker.launch(null)
          pending.await()?.let { extractIntoNewFolder(resolver, it, extraction) }
        } finally {
          chooser.pending = null
        }
      }
    })
    if (previous is FluidArchiveFamily) FluidViewerRegistry.install(family)
    onDispose {
      chooser.pending?.cancel()
      if (previous is FluidArchiveFamily && FluidViewerRegistry.families.any { it === family }) {
        FluidViewerRegistry.install(previous)
      }
    }
  }
}

private class ArchiveDestination {
  var pending: CompletableDeferred<Uri?>? = null
}

private suspend fun extractIntoNewFolder(resolver: ContentResolver, tree: Uri, extraction: FluidArchiveExtraction) =
  withContext(Dispatchers.IO) {
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val output = DocumentsContract.createDocument(resolver, parent, MIME_TYPE_DIR, "Archivio estratto")
      ?: throw IOException("Impossibile creare la cartella di estrazione")
    var completed = false
    try {
      val directories = mutableMapOf("" to output)
      extraction.writeFiles { entry ->
        val parentPath = entry.path.substringBeforeLast('/', "")
        var prefix = ""
        var folder = output
        for (part in parentPath.split('/').filter(String::isNotEmpty)) {
          prefix = if (prefix.isEmpty()) part else "$prefix/$part"
          folder = directories.getOrPut(prefix) {
            DocumentsContract.createDocument(resolver, folder, MIME_TYPE_DIR, part)
              ?: throw IOException("Impossibile creare $part")
          }
        }
        val name = entry.path.substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        val file = DocumentsContract.createDocument(resolver, folder, mime, name)
          ?: throw IOException("Impossibile creare $name")
        resolver.openOutputStream(file, "w") ?: throw IOException("Impossibile scrivere $name")
      }
      completed = true
    } finally {
      if (!completed) withContext(NonCancellable + Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(resolver, output) }
      }
    }
  }
