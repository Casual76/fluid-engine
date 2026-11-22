package dev.antigravity.fluidengine.viewer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/**
 * Dà i file della cache del test come pipe, come fanno i fornitori che trasmettono un flusso (un client di posta,
 * un archivio in rete): il descrittore non è ricercabile, e `PdfRenderer` da solo lo rifiuterebbe.
 */
class PipeProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    val name = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
    val file = File(requireNotNull(context).cacheDir, name)
    if (!file.isFile) throw FileNotFoundException(uri.toString())
    val (read, write) = ParcelFileDescriptor.createPipe()
    thread(name = "PipeProvider") {
      try {
        ParcelFileDescriptor.AutoCloseOutputStream(write).use { out -> file.inputStream().use { it.copyTo(out) } }
      } catch (_: java.io.IOException) {
        // Chi legge ha chiuso prima della fine: va bene.
      }
    }
    return read
  }

  override fun getType(uri: Uri): String? = null
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

  companion object {
    const val Authority = "dev.antigravity.fluidengine.viewer.test.pipe"
    fun uri(name: String): Uri = Uri.parse("content://$Authority/$name")
  }
}
