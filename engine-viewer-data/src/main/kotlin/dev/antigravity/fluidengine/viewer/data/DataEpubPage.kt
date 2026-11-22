package dev.antigravity.fluidengine.viewer.data

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonText
import dev.antigravity.fluidengine.ui.fluid.FluidWebContent
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import dev.antigravity.fluidengine.ui.fluid.FluidWebSizing
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronLeft
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronRight
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatListBulleted
import dev.antigravity.fluidengine.ui.fluid.rememberFluidWebContentState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Un libro aperto: lo ZIP resta aperto finché il documento vive, e serve capitoli, immagini, stili e caratteri. */
internal class DataEpubModel(val name: String, val zip: DataZip, val book: DataEpubBook) {
  /** Le risorse del capitolo, dallo ZIP: mai la rete. */
  val policy = DataWeb.Policy(local = { path ->
    if (zip.entry(path) == null) null
    else DataWeb.serve(path) {
      try {
        zip.read(path)?.let { ByteArrayInputStream(it) }
      } catch (_: Exception) {
        null
      }
    }
  })
}

/**
 * F6C.13 · Un EPUB a capitoli: il capitolo in `FluidWebContent` (le immagini, gli stili e i caratteri dallo ZIP), con
 * il foglio del lettore che porta il fondo e l'inchiostro del tema; sotto la barra «Capitolo 3 di 12 · 24%». In basso
 * l'indice (un foglio con le voci di `nav.xhtml` o `toc.ncx`) e i capitoli precedente e successivo. Un collegamento a un
 * altro capitolo ci porta; uno esterno va al gestore dell'app.
 */
@Composable
internal fun DataEpubPage(scope: FluidViewerCustomScope, model: DataEpubModel, settings: FluidDataSettings) {
  val strings = settings.strings
  val book = model.book
  var chapter by rememberSaveable { mutableIntStateOf(0) }
  var contents by rememberSaveable { mutableStateOf(false) }
  val count = book.chapters.size
  val index = chapter.coerceIn(0, count - 1)
  val open = rememberDataLinkOpener(settings)
  DataInner(
    scope,
    actions = {
      button("data.epub.toc", FluidIcons.FormatListBulleted, strings.contents, { contents = true }, placement = FluidToolbarPlacement.BottomBar)
      group("data.epub.nav", placement = FluidToolbarPlacement.BottomBar) {
        button("data.epub.prev", FluidIcons.ChevronLeft, strings.previousChapter, { if (index > 0) chapter = index - 1 }, enabled = index > 0)
        button("data.epub.next", FluidIcons.ChevronRight, strings.nextChapter, { if (index < count - 1) chapter = index + 1 }, enabled = index < count - 1)
      }
    },
  ) {
    val scheme = MaterialTheme.colorScheme
    val dark = dataDark()
    val density = LocalDensity.current
    val margin = with(density) { FluidScreenDefaults.HorizontalPadding.toPx() / density.density }.toInt()
    val css = remember(scheme, dark, margin) {
      DataWeb.readerCss(scheme.surface, scheme.onSurface, scheme.outlineVariant, scheme.primary, dark, margin)
    }
    val html by produceState<String?>(null, index, css) {
      value = null
      value = withContext(Dispatchers.IO) {
        try {
          DataEpub.chapterHtml(model.zip, book, index, css, if (dark) "dark" else "light")
        } catch (e: Exception) {
          "<!DOCTYPE html><html><body><p>${e.message ?: "Il capitolo non si legge"}</p></body></html>"
        }
      }
    }
    val top = contentPadding.calculateTopPadding()
    val bottom = contentPadding.calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val maxDp = with(density) { 4096.toDp() }
      val height = (maxHeight - top - bottom - 28.dp).coerceIn(160.dp, maxDp)
      Column(Modifier.fillMaxSize().padding(top = top)) {
        val title = book.chapters[index].title
        Text(
          listOfNotNull(strings.chapter(index + 1, count, book.percentAt(index)), title).joinToString(" · "),
          Modifier.fillMaxWidth().padding(horizontal = FluidScreenDefaults.HorizontalPadding).padding(bottom = 6.dp),
          style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val document = html
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.TopCenter) {
          if (document == null) {
            FluidSkeletonText(Modifier.padding(FluidScreenDefaults.HorizontalPadding), lines = 10)
          } else key(index) {
            val state = rememberFluidWebContentState()
            FluidWebContent(
              html = document,
              onLinkClick = { link ->
                val uri = Uri.parse(link.url)
                when (val target = DataWeb.target(uri)) {
                  is DataWebTarget.Local -> book.chapterOf(target.path).takeIf { it >= 0 }?.let { chapter = it }
                  else -> open(link.url)
                }
              },
              modifier = Modifier.fillMaxWidth().height(height),
              state = state,
              onRequest = model.policy::handle,
              sizing = FluidWebSizing.Viewport(height),
              // I colori li porta il foglio del lettore, che dichiara anche il color-scheme: niente scurimento automatico.
              darkening = dark,
              placeholder = { FluidSkeletonText(Modifier.padding(FluidScreenDefaults.HorizontalPadding), lines = 10) },
              failure = { DataNotice(strings.webFailed, it.toString(), Modifier.padding(FluidScreenDefaults.HorizontalPadding)) },
            )
          }
        }
      }
    }
    FluidGlassSheet(
      visible = contents,
      onDismissRequest = { contents = false },
      detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
      paneTitle = strings.contents,
    ) {
      Text(
        book.title ?: model.name, Modifier.padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      )
      val entries = book.toc.ifEmpty {
        book.chapters.mapIndexed { i, c -> DataEpubTocEntry(c.title ?: "Capitolo ${i + 1}", c.path, i, 0) }
      }
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FluidScreenDefaults.HorizontalPadding)) {
        run {
          FluidListGroup {
            entries.forEachIndexed { i, entry ->
              FluidListRow(
                title = entry.title,
                subtitle = if (entry.chapter >= 0) "Capitolo ${entry.chapter + 1} · ${book.percentAt(entry.chapter)}%" else "",
                modifier = Modifier.padding(start = (entry.depth * 16).dp),
                selected = entry.chapter == index,
                disclosure = false,
                onClick = if (entry.chapter >= 0) ({
                  chapter = entry.chapter
                  contents = false
                }) else null,
              )
              if (i < entries.lastIndex) FluidListDivider()
            }
          }
        }
      }
    }
  }
}

/** Il gestore delle risorse di un EPUB, per le prove: lo stesso della pagina. */
internal fun DataEpubModel.serve(path: String): FluidWebResponse? = policy.handle(
  dev.antigravity.fluidengine.ui.fluid.FluidWebRequest(Uri.parse("https://libro.invalid/$path"), "GET", false),
).takeIf { it is FluidWebResponse.Serve }
