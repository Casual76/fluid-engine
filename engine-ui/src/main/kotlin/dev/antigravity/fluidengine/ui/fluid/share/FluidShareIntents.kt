package dev.antigravity.fluidengine.ui.fluid.share

import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.chooser.ChooserAction

/**
 * Un'azione dell'app dentro il foglio di Android («Altre app…»), come le azioni di `UIActivityViewController`: su Android
 * 14 e oltre entra nel foglio di sistema (`EXTRA_CHOOSER_CUSTOM_ACTIONS`), prima non c'e'. L'azione e' un
 * [PendingIntent] perche' il foglio di sistema vive in un altro processo.
 */
class FluidShareChooserAction(val label: String, val icon: Icon, val action: PendingIntent)

/**
 * Gli intent della condivisione, in un posto solo (4.0, F8.10). Tutto cio' che manda un file passa da qui, e qui stanno
 * le due regole che un'app dimentica: **il permesso di lettura** (`FLAG_GRANT_READ_URI_PERMISSION`, piu' il `ClipData`
 * con tutti gli Uri, senza il quale `SEND_MULTIPLE` concede solo il primo) e **mai la propria app** nel foglio di
 * Android.
 *
 * Mandano solo gli Uri dell'oggetto: un oggetto con file dell'app ancora da copiare ([FluidShareItem.needsCopy]) passa
 * prima da [FluidShareFiles.stage]. Il foglio di condivisione lo fa da se'.
 */
object FluidShareIntents {
  /** `SEND` o `SEND_MULTIPLE` con il tipo, il testo, l'oggetto e i file di [item], col permesso di lettura. */
  fun send(item: FluidShareItem): Intent {
    val intent = when {
      item.uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(item.uris))
      item.uris.size == 1 -> Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, item.uris.first())
      else -> Intent(Intent.ACTION_SEND)
    }
    intent.type = item.mimeType
    item.text?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
    item.subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
    intent.putExtra(Intent.EXTRA_TITLE, item.title)
    if (item.uris.isNotEmpty()) {
      // Il ClipData porta gli Uri al sistema anche per SEND_MULTIPLE: i flag concedono la lettura di cio' che sta nel
      // ClipData, non nell'extra.
      val clip = ClipData(item.title, arrayOf(item.mimeType), ClipData.Item(item.uris.first()))
      item.uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
      intent.clipData = clip
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return intent
  }

  /** [send] verso un'attivita' precisa (una app della fila del foglio). */
  fun sendTo(item: FluidShareItem, target: FluidShareTarget): Intent =
    send(item).setComponent(ComponentName(target.packageName, target.activityName))

  /**
   * Il foglio di Android per [item] («Altre app…»), con le azioni dell'app su Android 14 e oltre, e senza le attivita' di
   * questa app che ricevono una condivisione (mandarsi qualcosa da soli non e' mai la cosa voluta).
   */
  fun chooser(
    context: Context,
    item: FluidShareItem,
    title: String? = null,
    customActions: List<FluidShareChooserAction> = emptyList(),
  ): Intent {
    val send = send(item)
    val chooser = Intent.createChooser(send, title)
    val own = runCatching {
      @Suppress("DEPRECATION")
      context.packageManager.queryIntentActivities(Intent(send).setPackage(context.packageName), 0)
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    }.getOrDefault(emptyList())
    if (own.isNotEmpty()) chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, own.toTypedArray())
    if (Build.VERSION.SDK_INT >= 34 && customActions.isNotEmpty()) {
      chooser.putExtra(
        Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS,
        customActions.map { ChooserAction.Builder(it.icon, it.label, it.action).build() }.toTypedArray(),
      )
    }
    if (item.uris.isNotEmpty()) chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return chooser
  }

  /** Cio' che «Copia» mette negli appunti: il testo, o gli Uri (che un'app che incolla legge col permesso concesso). */
  fun clip(context: Context, item: FluidShareItem): ClipData = when {
    item.uris.isEmpty() -> ClipData.newPlainText(item.title, item.text.orEmpty())
    else -> ClipData.newUri(context.contentResolver, item.title, item.uris.first()).also { clip ->
      item.uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    }
  }

  /** «Salva in File» di un oggetto solo: il selettore di Android per scegliere dove e con che nome. */
  fun createDocument(item: FluidShareItem): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .setType(if (item.uris.isEmpty()) "text/plain" else item.mimeType)
    .putExtra(Intent.EXTRA_TITLE, fluidSaveName(item))
}

/** Il nome proposto per «Salva in File»: il nome del file, o il titolo con l'estensione giusta per un testo. */
internal fun fluidSaveName(item: FluidShareItem): String {
  val base = fluidSafeFileName(item.saveName)
  return if (item.uris.isEmpty() && !base.contains('.')) "$base.txt" else base
}
