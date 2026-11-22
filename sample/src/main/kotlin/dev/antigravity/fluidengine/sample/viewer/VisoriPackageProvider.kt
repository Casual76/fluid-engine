package dev.antigravity.fluidengine.sample.viewer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Exposes only the gallery's installed base APK, read-only, to the selected share/open target. */
class VisoriPackageProvider : ContentProvider() {
  companion object {
    fun uri(context: Context): Uri = Uri.Builder().scheme("content").authority(context.packageName + ".visori")
      .appendPath("Aggiornamento.apk").build()
  }

  override fun onCreate() = true
  private fun source(request: Uri): File {
    val app = requireNotNull(context)
    if (request != uri(app)) throw FileNotFoundException(request.toString())
    return File(app.applicationInfo.sourceDir)
  }
  override fun getType(uri: Uri): String? = if (uri == uri(requireNotNull(context)))
    "application/vnd.android.package-archive" else null

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (mode != "r") throw FileNotFoundException("Sola lettura")
    return ParcelFileDescriptor.open(source(uri), ParcelFileDescriptor.MODE_READ_ONLY)
  }
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
    sortOrder: String?): MatrixCursor {
    val file = source(uri)
    val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    return MatrixCursor(columns).apply { addRow(columns.map {
      when (it) {
        OpenableColumns.DISPLAY_NAME -> uri.lastPathSegment
        OpenableColumns.SIZE -> file.length()
        else -> null
      }
    }) }
  }
  override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
    throw UnsupportedOperationException()
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
