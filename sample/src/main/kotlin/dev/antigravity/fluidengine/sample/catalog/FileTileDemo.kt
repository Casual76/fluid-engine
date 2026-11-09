package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.LocalGalleryMatrix
import dev.antigravity.fluidengine.sample.matrixDemoSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.FluidListGroup

/**
 * Catalogo non registrato: tipi, stati, nomi ostili, chip densi.
 *
 * Nella galleria la prima sezione e' **quella della matrice**: tema (nero compreso), testo e movimento sono quelli
 * che la matrice dice, applicati dalla radice. Sotto restano le quattro varianti interne, a confronto fisso
 * (chiaro/scuro × testo 1,0/2,0); le scure seguono il nero se la matrice e' nera.
 */
@Composable
fun FileTileDemo() {
  Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val matrix = LocalGalleryMatrix.current
    if (matrix != null) {
      Text("File · matrice: ${matrix.summary}")
      FileTileSamples()
    }
    for (dark in listOf(false, true)) {
      for (scale in listOf(1f, 2f)) {
        FluidTheme(settings = if (dark) matrixDemoSettings(dark = true) else EngineSettings(themeMode = ThemeMode.LIGHT), systemBars = false) {
          val density = LocalDensity.current
          CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalFluidMotionPolicy provides FluidMotionPolicy(if (scale == 2f) 0f else 1f)) {
            Text("Confronto fisso · scuro=$dark · testo=$scale")
            FileTileSamples()
          }
        }
      }
    }
  }
}

@Composable
private fun FileTileSamples() {
  FluidListGroup {
    FluidFileTile("miniatura.jpg", {}, thumbnail = { Icon(Icons.Outlined.Image, null) }, trailingMenu = {
      val menu = rememberFluidContextMenu(actions = { listOf(FluidContextAction("Condividi", onClick = {})) })
      FluidGlassIconButton(onClick = { menu.open() }, modifier = Modifier.fluidContextMenuAnchor(menu), grip = FluidPressGrip.YieldsToScroll) {
        Icon(Icons.Outlined.MoreVert, contentDescription = LocalFluidLabels.current.actions)
      }
    })
    listOf("foto.png", "fattura.pdf", "documento.docx", "tabella.xlsx", "presentazione.pptx", "archivio.zip", "audio.mp3", "video.mp4", "sorgente.kt", "fattura.pdf.apk", "sconosciuto").forEach {
      FluidFileTile(it, onClick = {}, sizeBytes = 1200000)
    }
    FluidFileTile("Nome molto lungo di un documento da scaricare con estensione conservata.pdf", {}, status = FluidFileStatus.Remote)
    FluidFileTile("download.zip", {}, status = remember { FluidFileStatus.Transferring { 0.42f } }, onCancel = {})
    FluidFileTile("in-corso.zip", {}, status = remember { FluidFileStatus.Transferring() }, onCancel = {})
    FluidFileTile("errore.pdf", {}, status = FluidFileStatus.Failed, onRetry = {})
    FluidFileTile("bozza.txt", {}, sizeBytes = 0, onRemove = {})
  }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    listOf("nome estremamente lungo di una fattura.pdf.apk", "foto.jpg", "appunti.txt").forEach { FluidFileChip(it, {}) }
  }
}
