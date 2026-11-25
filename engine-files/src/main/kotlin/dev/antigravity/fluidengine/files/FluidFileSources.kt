package dev.antigravity.fluidengine.files

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/** Le due sorgenti di serie dello sfoglia-file (F8.18). */
object FluidFileSources {
  /**
   * La cartella [root] dell'app (per esempio `filesDir/Documenti`, o i risultati di una conversione): una sorgente
   * sul disco, senza permessi. I file nascosti (col punto davanti) non si mostrano; una cartella dice quanti elementi
   * contiene. L'[id] dev'essere stabile fra un avvio e l'altro.
   */
  fun appFolder(
    root: File,
    id: String,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
  ): FluidFileSource = FluidAppFolderSource(root, id, title, subtitle, icon)

  /**
   * Una cartella concessa dall'utente con lo Storage Access Framework ([treeUri], da `ACTION_OPEN_DOCUMENT_TREE`: il
   * telefono, una scheda, o un'app con un `DocumentsProvider`). Serve il permesso, persistente se la cartella deve
   * restare fra un avvio e l'altro ([FluidFileLocations] lo prende da sé). La cartella si legge con una richiesta sola
   * (il provider la dà intera) e si consegna a pagine; un provider che carica ancora (un cloud) avvisa con [changes].
   */
  fun documentTree(
    context: Context,
    treeUri: Uri,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    id: String = treeSourceId(treeUri),
  ): FluidFileSource = FluidDocumentTreeSource(context.applicationContext.contentResolver, treeUri, id, title, subtitle, icon)

  /** L'id di serie di una cartella concessa: stabile, senza «/», dall'indirizzo dell'albero. */
  fun treeSourceId(treeUri: Uri): String = "tree-" + Integer.toHexString(treeUri.toString().hashCode()) +
    "-" + treeUri.toString().length
}

/** Taglia una lista già ordinata a pagine, col segno numerico della prossima. */
internal fun List<FluidFileItem>.page(query: FluidFileQuery): FluidFilePage {
  val start = (query.cursor?.toIntOrNull() ?: 0).coerceIn(0, size)
  val end = (start + query.limit.coerceAtLeast(1)).coerceAtMost(size)
  return FluidFilePage(subList(start, end).toList(), if (end < size) end.toString() else null)
}

internal class FluidAppFolderSource(
  private val root: File,
  override val id: String,
  override val title: String,
  override val subtitle: String?,
  override val icon: ImageVector?,
) : FluidFileSource {
  override val sorts: Boolean get() = true

  override suspend fun list(query: FluidFileQuery): FluidFilePage = withContext(Dispatchers.IO) {
    val base = root.canonicalFile
    val folder = if (query.folder.isNullOrEmpty()) base else File(base, query.folder).canonicalFile
    // Una cartella fuori dalla radice («..») non si legge: la sorgente è la radice e ciò che sta sotto.
    if (folder.path != base.path && !folder.path.startsWith(base.path + File.separator)) {
      throw IOException("Cartella fuori dalla sorgente: ${query.folder}")
    }
    val children = folder.listFiles() ?: throw IOException("La cartella non si legge: ${folder.name}")
    children.asSequence()
      .filter { !it.name.startsWith(".") }
      .map { file -> file.item(base) }
      .sortedWith(query.sort.comparator())
      .toList()
      .page(query)
  }

  private fun File.item(base: File): FluidFileItem {
    val relative = path.removePrefix(base.path).trimStart(File.separatorChar).replace(File.separatorChar, '/')
    return if (isDirectory) {
      FluidFileItem(relative, name, isFolder = true, modifiedAtMillis = lastModified().takeIf { it > 0 },
        childCount = list()?.count { !it.startsWith(".") })
    } else {
      val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
      FluidFileItem(relative, name, mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension),
        sizeBytes = length(), modifiedAtMillis = lastModified().takeIf { it > 0 }, uri = Uri.fromFile(this))
    }
  }
}

internal class FluidDocumentTreeSource(
  private val resolver: ContentResolver,
  val treeUri: Uri,
  override val id: String,
  override val title: String,
  override val subtitle: String?,
  override val icon: ImageVector?,
) : FluidFileSource {
  override val sorts: Boolean get() = true

  private val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)
  // La cartella letta e ordinata, per consegnarla a pagine senza rileggerla: l'ultima, con l'ordine.
  @Volatile private var cached: Triple<String, FluidFileSort, List<FluidFileItem>>? = null

  override suspend fun list(query: FluidFileQuery): FluidFilePage = withContext(Dispatchers.IO) {
    val folder = query.folder ?: rootId
    val hit = cached?.takeIf { query.cursor != null && it.first == folder && it.second == query.sort }
    val items = hit?.third ?: read(folder).sortedWith(query.sort.comparator()).also { cached = Triple(folder, query.sort, it) }
    items.page(query)
  }

  override fun changes(folder: String?): Flow<Unit> = callbackFlow {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder ?: rootId)
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) {
        cached = null
        trySend(Unit)
      }
    }
    resolver.registerContentObserver(children, false, observer)
    awaitClose { resolver.unregisterContentObserver(observer) }
  }

  private fun read(folder: String): List<FluidFileItem> {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder)
    val cursor = try {
      resolver.query(children, Projection, null, null, null)
    } catch (e: SecurityException) {
      throw IOException("Permesso tolto per la cartella", e)
    } ?: throw IOException("La cartella non si legge")
    return cursor.use { c ->
      val id = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
      val name = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
      val mime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
      val size = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
      val modified = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
      buildList(c.count.coerceAtLeast(0)) {
        while (c.moveToNext()) {
          val documentId = if (id >= 0) c.getString(id) else null
          if (documentId.isNullOrEmpty()) continue
          val display = if (name >= 0) c.getString(name) else null
          if (display.isNullOrEmpty() || display.startsWith(".")) continue
          val type = if (mime >= 0) c.getString(mime) else null
          val folderRow = type == DocumentsContract.Document.MIME_TYPE_DIR
          add(FluidFileItem(
            id = documentId,
            name = display,
            isFolder = folderRow,
            mimeType = type.takeUnless { folderRow },
            sizeBytes = if (!folderRow && size >= 0 && !c.isNull(size)) c.getLong(size) else null,
            modifiedAtMillis = if (modified >= 0 && !c.isNull(modified)) c.getLong(modified).takeIf { it > 0 } else null,
            uri = if (folderRow) null else DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
          ))
        }
      }
    }
  }

  private companion object {
    val Projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )
  }
}
