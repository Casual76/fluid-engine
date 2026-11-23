package dev.antigravity.fluidengine.sample.viewer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Exposes, read-only, to the selected share/open target: the gallery's installed base APK, and (F6C.15) the gallery's
 * own copies of its sample files, `filesDir/visori-v2/<scene>/<name>`. Nothing outside that folder resolves.
 */
class VisoriPackageProvider : ContentProvider() {
  companion object {
    private fun authority(context: Context) = context.packageName + ".visori"

    fun uri(context: Context): Uri = Uri.Builder().scheme("content").authority(authority(context))
      .appendPath("Aggiornamento.apk").build()

    /** A sample file the gallery copied for [scene] (`galleria`, `converter`, a family scene, `senza`). */
    fun fileUri(context: Context, scene: String, name: String): Uri = Uri.Builder().scheme("content")
      .authority(authority(context)).appendPath("file").appendPath(scene).appendPath(name).build()
  }

  override fun onCreate() = true

  private fun source(request: Uri): File {
    val app = requireNotNull(context)
    if (request == uri(app)) return File(app.applicationInfo.sourceDir)
    val segments = request.pathSegments
    if (request.authority != authority(app) || segments.size != 3 || segments[0] != "file") {
      throw FileNotFoundException(request.toString())
    }
    val root = VisoriFiles.root(app).canonicalFile
    val file = File(File(root, segments[1]), segments[2]).canonicalFile
    if (file.parentFile?.parentFile != root || !file.isFile) throw FileNotFoundException(request.toString())
    return file
  }

  override fun getType(uri: Uri): String? {
    val app = requireNotNull(context)
    if (uri == uri(app)) return "application/vnd.android.package-archive"
    val name = uri.lastPathSegment ?: return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
  }

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
