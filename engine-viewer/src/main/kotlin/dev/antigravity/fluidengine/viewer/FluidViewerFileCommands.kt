package dev.antigravity.fluidengine.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerFileActions
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoAction

/**
 * F6C.19 · Le azioni sul file che solo l'app sa fare, per il menu del nome in cima al visore ([FluidViewerFileActions]).
 * Ognuna compare solo se l'app la dà; l'engine le chiama col file della pagina.
 *
 * ```kotlin
 * val files = rememberFluidViewerFiles(list, openWith = ::apri, share = ::condividi)
 * val commands = FluidViewerFileCommands(rename = { file -> rinomina(file) }, delete = { file -> chiediElimina(file) },
 *   shareableUri = { file -> FileProvider.getUriForFile(context, autorita, File(file.uri.path!!)) })
 * FluidFileViewer(count = files.count, content = files::content, fileActions = { i -> files.fileActions(i, commands) })
 * ```
 */
data class FluidViewerFileCommands(
  val rename: ((FluidViewerFile) -> Unit)? = null,
  val move: ((FluidViewerFile) -> Unit)? = null,
  val duplicate: ((FluidViewerFile) -> Unit)? = null,
  /** Elimina, in rosso in fondo al menu. La conferma la chiede l'app. */
  val delete: ((FluidViewerFile) -> Unit)? = null,
  /** Le voci dell'app che non hanno un posto fisso («Salva in Download», «Converti in…»). */
  val more: ((FluidViewerFile) -> List<FluidViewerInfoAction>)? = null,
  /**
   * L'indirizzo che un'altra app può leggere (un `content://`, per esempio di un `FileProvider`). Serve a «Copia» e,
   * se l'app non dà `share` e `openWith` a [rememberFluidViewerFiles], a «Condividi» e «Apri con…». Senza, vale
   * l'indirizzo del file quando non è un `file://`, che Android non lascia uscire dall'app.
   */
  val shareableUri: ((FluidViewerFile) -> Uri?)? = null,
)

/**
 * F6C.19 · Le voci del menu del file per la pagina [index]:
 * - **Condividi** e **Apri con…**: le callback di [rememberFluidViewerFiles] se l'app le dà, altrimenti l'engine col
 *   selettore di sistema (`ACTION_SEND` e `ACTION_VIEW`) sull'indirizzo che si può dare alle altre app;
 * - **Copia**: il file negli appunti (`ClipData.newUri`), con lo stesso indirizzo;
 * - **Stampa** la fa il visore per un documento a pagine; **Informazioni** la aggiunge il visore;
 * - le voci dell'app ([commands]): Duplica, Sposta, Rinomina, le sue in più, Elimina.
 *
 * Null se la pagina non c'è. Si chiama dentro `fileActions` di `FluidFileViewer`.
 */
@Composable
fun FluidViewerFiles.fileActions(index: Int, commands: FluidViewerFileCommands = FluidViewerFileCommands()): FluidViewerFileActions? {
  val file = files.getOrNull(index) ?: return null
  val context = LocalContext.current
  val address = commands.shareableUri?.invoke(file) ?: file.uri.takeIf { fluidViewerUriLeavesApp(it) }
  val appShare = share
  val appOpenWith = openWith
  return FluidViewerFileActions(
    share = appShare?.let { { it(file) } } ?: address?.let { { fluidShareViewerFile(context, file, it) } },
    openWith = appOpenWith?.let { { it(file) } } ?: address?.let { { fluidOpenViewerFileWith(context, file, it) } },
    copy = address?.let { { fluidCopyViewerFile(context, file, it) } },
    rename = commands.rename?.let { { it(file) } },
    move = commands.move?.let { { it(file) } },
    duplicate = commands.duplicate?.let { { it(file) } },
    delete = commands.delete?.let { { it(file) } },
    more = commands.more?.invoke(file).orEmpty(),
  )
}

/** Se un'altra app può leggere [uri]: un `content://` o una risorsa dell'app, mai un `file://`. Pura. */
internal fun fluidViewerUriLeavesApp(uri: Uri): Boolean =
  uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE

private fun fluidShareViewerFile(context: Context, file: FluidViewerFile, uri: Uri) {
  val send = Intent(Intent.ACTION_SEND).setType(file.mimeType ?: "application/octet-stream")
    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  // Il permesso di lettura arriva all'app scelta nel selettore solo se l'indirizzo sta anche nella ClipData.
  send.clipData = ClipData.newRawUri(file.name, uri)
  context.startActivity(Intent.createChooser(send, file.name))
}

private fun fluidOpenViewerFileWith(context: Context, file: FluidViewerFile, uri: Uri) {
  val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mimeType ?: "*/*")
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  context.startActivity(Intent.createChooser(view, file.name))
}

/** Il file negli appunti, come «Copia» in File di iOS: un'altra app lo incolla. Android 13+ mostra la sua conferma. */
private fun fluidCopyViewerFile(context: Context, file: FluidViewerFile, uri: Uri) {
  val clipboard = context.getSystemService(ClipboardManager::class.java)
  clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, file.name, uri))
}
