package dev.antigravity.fluidengine.files

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Un `DocumentsProvider` finto, come lo vede chi lo interroga col `ContentResolver`: le cartelle sono documenti, i figli
 * si chiedono con gli indirizzi dell'albero (`…/tree/<albero>/document/<cartella>/children`). È un `ContentProvider`
 * semplice perché Robolectric non dà a un `DocumentsProvider` vero il permesso `MANAGE_DOCUMENTS` che il sistema
 * gli chiede. [denied] simula il permesso tolto, [queries] conta le richieste.
 */
class FakeTreeProvider : ContentProvider() {
  data class Doc(val id: String, val name: String, val mime: String, val size: Long? = null, val modified: Long? = null)

  companion object {
    const val Authority = "dev.prova.documenti"
    val children = HashMap<String, List<Doc>>()
    val names = HashMap<String, String>()
    @Volatile var denied = false
    @Volatile var queries = 0

    fun reset() {
      children.clear()
      names.clear()
      denied = false
      queries = 0
    }

    fun tree(root: String): Uri = DocumentsContract.buildTreeDocumentUri(Authority, root)
  }

  override fun onCreate() = true

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
    sortOrder: String?): Cursor {
    queries++
    if (denied) throw SecurityException("Permesso tolto")
    val segments = uri.pathSegments
    val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    val cursor = MatrixCursor(columns)
    // …/tree/<albero>/document/<id>/children oppure …/tree/<albero>/document/<id>
    val id = segments.getOrNull(3) ?: return cursor
    if (segments.lastOrNull() == "children") {
      for (doc in children[id].orEmpty()) cursor.addRow(columns.map { column -> value(doc, column) })
    } else {
      val doc = Doc(id, names[id] ?: id, DocumentsContract.Document.MIME_TYPE_DIR)
      cursor.addRow(columns.map { column -> value(doc, column) })
    }
    return cursor
  }

  private fun value(doc: Doc, column: String): Any? = when (column) {
    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> doc.id
    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> doc.name
    DocumentsContract.Document.COLUMN_MIME_TYPE -> doc.mime
    DocumentsContract.Document.COLUMN_SIZE -> doc.size
    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> doc.modified
    else -> null
  }

  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
