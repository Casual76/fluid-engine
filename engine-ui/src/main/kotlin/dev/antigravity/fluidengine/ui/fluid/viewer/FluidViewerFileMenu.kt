package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.icons.ContentCopy
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.DriveFileMove
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Info
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.OpenInNew

/**
 * F6C.19 · **Il menu del file.** In cima al visore il titolo è il nome del file senza estensione con «⌄» (il titolo
 * selettore di F6B.11), e apre questo menu, come in File e in Quick Look di iOS 26 (il fermo `ql-pdf-menu.png`, la
 * tavola «D · Mix»). Una voce c'è solo se la sua azione non è null.
 *
 * - **Quelle che l'engine sa fare**: [share], [openWith], [copy] (il file negli appunti) e [print]. Con `engine-viewer`
 *   le dà `FluidViewerFiles.fileActions(index)`. «Stampa» di un documento a pagine (PDF, Word, slide) la fa il visore
 *   da sé quando [print] è null. «Informazioni», che apre il pannello info, la aggiunge sempre il visore.
 * - **Quelle che solo l'app sa fare**: [duplicate], [move], [rename], le voci in più dell'app ([more], per esempio
 *   «Salva in Download») e [delete], in rosso, in fondo.
 *
 * Le azioni del file stanno solo qui: «•••» in basso tiene le azioni del tipo (in un archivio Ordina, Filtra e
 * Codifica dei nomi), e c'è solo se il tipo ne ha.
 */
data class FluidViewerFileActions(
  val share: (() -> Unit)? = null,
  val openWith: (() -> Unit)? = null,
  val copy: (() -> Unit)? = null,
  val print: (() -> Unit)? = null,
  val rename: (() -> Unit)? = null,
  val move: (() -> Unit)? = null,
  val duplicate: (() -> Unit)? = null,
  val delete: (() -> Unit)? = null,
  /** Le voci dell'app che non hanno un posto qui sopra («Salva in Download», «Converti in…»), dopo [rename]. */
  val more: List<FluidViewerInfoAction> = emptyList(),
)

/** Che cosa fa una voce del menu del file: l'ordine dei gruppi e le prove lo leggono da qui. */
internal enum class ViewerFileItem { Duplicate, Move, Rename, More, Share, OpenWith, Copy, Print, Extra, Info, Delete }

internal class ViewerFileEntry(val item: ViewerFileItem, val label: String, val run: () -> Unit)

/**
 * Le voci del menu del file, nell'ordine e nei gruppi del menu del titolo di un documento in iOS (UIDocument, iOS 17:
 * Duplica, Sposta, Rinomina | Esporta, Stampa): l'app, poi l'engine (Condividi, Apri con…, Copia, Stampa), poi
 * Informazioni, poi Elimina.
 *
 * [legacy] sono le azioni che un'app dava al pannello info prima di F6C.19 ([FluidViewerInfo.actions]): «Apri con…» e
 * «Condividi» valgono come le voci dell'engine quando [actions] non le dà; le altre entrano fra le voci dell'engine,
 * tranne quelle che la barra in basso ha già ([dockLabels], come «Estrai tutto» di un archivio). [print] è la stampa
 * del visore, che vale se [actions] non ne dà una. Pura.
 */
internal fun viewerFileMenuEntries(
  actions: FluidViewerFileActions?,
  legacy: List<FluidViewerInfoAction>,
  print: (() -> Unit)?,
  onInfo: (() -> Unit)?,
  dockLabels: Set<String>,
  strings: FluidViewerStrings,
): List<ViewerFileEntry> = buildList {
  fun put(item: ViewerFileItem, label: String, run: (() -> Unit)?) {
    if (run != null && none { it.label == label }) add(ViewerFileEntry(item, label, run))
  }
  val app = actions ?: FluidViewerFileActions()
  put(ViewerFileItem.Duplicate, strings.duplicate, app.duplicate)
  put(ViewerFileItem.Move, strings.move, app.move)
  put(ViewerFileItem.Rename, strings.rename, app.rename)
  app.more.forEach { put(ViewerFileItem.More, it.label, it.onClick) }
  put(ViewerFileItem.Share, strings.share, app.share ?: legacy.firstOrNull { it.label == strings.share }?.onClick)
  put(ViewerFileItem.OpenWith, strings.openWith, app.openWith ?: legacy.firstOrNull { it.label == strings.openWith }?.onClick)
  put(ViewerFileItem.Copy, strings.copyFile, app.copy)
  put(ViewerFileItem.Print, strings.print, app.print ?: print)
  legacy.filter { it.label != strings.share && it.label != strings.openWith && it.label !in dockLabels }
    .forEach { put(ViewerFileItem.Extra, it.label, it.onClick) }
  put(ViewerFileItem.Info, strings.info, onInfo)
  put(ViewerFileItem.Delete, strings.delete, app.delete)
}

/** Il gruppo di una voce: fra due gruppi il menu mette la banda (`startsSection`). */
private fun ViewerFileItem.group(): Int = when (this) {
  ViewerFileItem.Duplicate, ViewerFileItem.Move, ViewerFileItem.Rename, ViewerFileItem.More -> 0
  ViewerFileItem.Share, ViewerFileItem.OpenWith, ViewerFileItem.Copy, ViewerFileItem.Print, ViewerFileItem.Extra -> 1
  ViewerFileItem.Info -> 2
  ViewerFileItem.Delete -> 3
}

private fun ViewerFileItem.icon(): ImageVector? = when (this) {
  ViewerFileItem.Duplicate -> ViewerFileIcons.Duplicate
  ViewerFileItem.Move -> FluidIcons.DriveFileMove
  ViewerFileItem.Rename -> FluidIcons.Edit
  ViewerFileItem.Share -> FluidIcons.IosShare
  ViewerFileItem.OpenWith -> FluidIcons.OpenInNew
  ViewerFileItem.Copy -> FluidIcons.ContentCopy
  ViewerFileItem.Print -> ViewerFileIcons.Print
  ViewerFileItem.Info -> FluidIcons.Info
  ViewerFileItem.Delete -> FluidIcons.Delete
  ViewerFileItem.More, ViewerFileItem.Extra -> null
}

/** Le voci come voci di menu: le icone, la banda fra i gruppi, Elimina in rosso. [run] esegue l'azione di una voce. */
internal fun List<ViewerFileEntry>.toContextActions(run: (() -> Unit) -> Unit): List<FluidContextAction> =
  mapIndexed { index, entry ->
    val section = index > 0 && this[index - 1].item.group() != entry.item.group()
    FluidContextAction(entry.label, icon = entry.item.icon(), destructive = entry.item == ViewerFileItem.Delete,
      startsSection = section) { run(entry.run) }
  }

/**
 * Il nome del file senza l'estensione, come il titolo di Quick Look («opm71» per `opm71.pdf`). Resta intero un nome che
 * senza estensione sarebbe vuoto (`.gitignore`) o che finisce con un punto, e un'estensione che non sembra tale (spazi,
 * più di dieci caratteri). Pura.
 */
internal fun fluidViewerTitleName(name: String): String {
  val dot = name.lastIndexOf('.')
  if (dot <= 0 || dot == name.length - 1) return name
  val extension = name.substring(dot + 1)
  if (extension.length > 10 || extension.any { it.isWhitespace() }) return name
  return name.substring(0, dot).ifBlank { name }
}

/**
 * Le etichette del menu del file di ogni pagina (F6C.19): un «•••» della pagina (il lettore audio, una famiglia) toglie
 * dalle sue voci quelle che il titolo ha già, così nessuna voce è doppia.
 */
internal val LocalViewerFileMenuLabels = staticCompositionLocalOf<Set<String>> { emptySet() }

/**
 * Le due icone che il set dell'engine non ha: `print` e `library_add` di Material Symbols Rounded, peso 500, al tratto
 * (google/material-design-icons@10ecc14790, Apache-2.0, `LICENSES/MaterialSymbols.md`), generate con
 * `tools/icons/gen_fluid_icons.py --app` come quelle del set. Restano qui finché il set non le prende.
 */
internal object ViewerFileIcons {
  val Print: ImageVector by lazy {
    icon("ViewerFileIcons.Print", "M8.07 21.31Q7.13 21.31 6.46 20.64Q5.8 19.97 5.8 19.03V17.17H4.07Q3.13 17.17 2.46 16.5Q1.8 15.84 1.8 14.89V11Q1.8 9.65 2.73 8.72Q3.65 7.8 4.98 7.8H19.02Q20.37 7.8 21.29 8.72Q22.2 9.65 22.2 11V14.89Q22.2 15.84 21.54 16.5Q20.87 17.17 19.93 17.17H18.2V19.03Q18.2 19.97 17.54 20.64Q16.87 21.31 15.93 21.31H8.07ZM4.07 14.89H6.15Q6.23 14.21 6.78 13.73Q7.34 13.26 8.07 13.26H15.93Q16.66 13.26 17.22 13.73Q17.77 14.21 17.85 14.89H19.93V10.99Q19.93 10.6 19.66 10.34Q19.4 10.07 19.01 10.07H4.98Q4.6 10.07 4.33 10.34Q4.07 10.61 4.07 11V14.89ZM15.93 7.8V5.07H8.07V7.8H5.8V5.07Q5.8 4.13 6.46 3.46Q7.13 2.8 8.07 2.8H15.93Q16.87 2.8 17.54 3.46Q18.2 4.13 18.2 5.07V7.8H15.93ZM17.87 12.76Q18.33 12.76 18.64 12.44Q18.96 12.13 18.96 11.67Q18.96 11.21 18.64 10.89Q18.33 10.57 17.87 10.57Q17.41 10.57 17.09 10.89Q16.77 11.21 16.77 11.67Q16.77 12.13 17.09 12.44Q17.41 12.76 17.87 12.76ZM15.93 19.07V15.53H8.07V19.07H15.93ZM4.07 10.07Q4.07 10.07 4.33 10.07Q4.6 10.07 4.98 10.07H19.01Q19.4 10.07 19.66 10.07Q19.93 10.07 19.93 10.07H17.85Q17.77 10.07 17.22 10.07Q16.66 10.07 15.93 10.07H8.07Q7.34 10.07 6.78 10.07Q6.23 10.07 6.15 10.07H4.07Z")
  }

  val Duplicate: ImageVector by lazy {
    icon("ViewerFileIcons.Duplicate", "M14.14 13.86Q14.56 13.86 14.85 13.58Q15.14 13.29 15.14 12.86V10.86H17.14Q17.56 10.86 17.85 10.58Q18.14 10.29 18.14 9.86Q18.14 9.44 17.85 9.15Q17.56 8.86 17.14 8.86H15.14V6.86Q15.14 6.44 14.85 6.15Q14.56 5.86 14.14 5.86Q13.71 5.86 13.42 6.15Q13.14 6.44 13.14 6.86V8.86H11.14Q10.71 8.86 10.42 9.15Q10.14 9.44 10.14 9.86Q10.14 10.29 10.42 10.58Q10.71 10.86 11.14 10.86H13.14V12.86Q13.14 13.29 13.42 13.58Q13.71 13.86 14.14 13.86ZM8.21 18.07Q7.26 18.07 6.6 17.4Q5.93 16.74 5.93 15.79V3.93Q5.93 2.99 6.6 2.32Q7.26 1.66 8.21 1.66H20.07Q21.01 1.66 21.68 2.32Q22.34 2.99 22.34 3.93V15.79Q22.34 16.74 21.68 17.4Q21.01 18.07 20.07 18.07H8.21ZM8.21 15.79H20.07V3.93H8.21V15.79ZM3.93 22.34Q2.99 22.34 2.32 21.68Q1.66 21.01 1.66 20.07V7.07Q1.66 6.59 1.99 6.26Q2.32 5.93 2.8 5.93Q3.28 5.93 3.6 6.26Q3.93 6.59 3.93 7.07V20.07H16.93Q17.41 20.07 17.74 20.4Q18.07 20.72 18.07 21.2Q18.07 21.68 17.74 22.01Q17.41 22.34 16.93 22.34H3.93ZM8.21 3.93V15.79V3.93Z")
  }

  private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
  ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
