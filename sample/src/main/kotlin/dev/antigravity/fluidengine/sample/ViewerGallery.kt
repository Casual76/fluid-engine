package dev.antigravity.fluidengine.sample

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.viewer.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

private enum class Example(val label: String, val cover: Int?, val credit: String) {
  Launch("Lancio", R.drawable.viewer_rocket, "Fotografia di SpaceX, via scikit-image"),
  Astronaut("Eileen Collins", R.drawable.viewer_astronaut, "Fotografia della NASA"),
  Flower("Fiori al vento", R.drawable.viewer_flower, "Video CC0 della raccolta MDN"),
  Notes("Quaderno di campo", null, "PDF · 2 pagine"),
}

@Composable
internal fun ViewerGallery(onBack: () -> Unit) {
  val nav = rememberFluidNavController()
  val namespace = rememberFluidGlassNamespace()
  fun thumbnail(index: Int) = FluidViewerThumbnail(Example.entries[index], namespace)
  FluidNavHost(nav, "collection") {
    composable("collection") {
      FluidScreen(title = "Visori", onBack = onBack) {
        items(2) { row ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { column ->
              val index = row * 2 + column
              val file = Example.entries[index]
              Column(Modifier.weight(1f).clickable { nav.navigate("viewer/$index") }) {
                val shape = ContinuousCornerShape(FluidRadius.Card)
                Box(Modifier.fillMaxWidth().aspectRatio(1.5f).fluidViewerThumbnail(thumbnail(index), shape)
                  .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                  if (file.cover != null) Image(painterResource(file.cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                  else Text("PDF", style = MaterialTheme.typography.headlineMedium)
                }
                Text(file.label, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.titleSmall)
              }
            }
          }
        }
      }
    }
    fluidFileViewerRoute("viewer/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType }),
      initial = { it.arguments!!.getInt("index") }, thumbnail = { _, index -> thumbnail(index) }) { entry ->
      ExampleViewer(entry.arguments!!.getInt("index")) { nav.popBackStack() }
    }
  }
}

@Composable
private fun ExampleViewer(initial: Int, onBack: () -> Unit) {
  val context = LocalContext.current.applicationContext
  val player = remember(context) {
    ExoPlayer.Builder(context).build().apply {
      setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.viewer_flower}")))
      repeatMode = Player.REPEAT_MODE_ONE
    }
  }
  val session = rememberFluidMedia3Session(player)
  DisposableEffect(player) { onDispose { player.release() } }
  var selected by rememberSaveable { mutableIntStateOf(initial) }
  if (selected == Example.Flower.ordinal) LifecycleResumeEffect(player) {
    if (player.playbackState == Player.STATE_IDLE) player.prepare()
    player.play()
    onPauseOrDispose { player.pause() }
  }

  var pdf by remember { mutableStateOf<FluidPdfPageSource?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(context) {
    val copy = File.createTempFile("viewer-notes-", ".pdf", context.cacheDir)
    try {
      withContext(Dispatchers.IO) {
        context.resources.openRawResource(R.raw.viewer_notes).use { input -> copy.outputStream().use(input::copyTo) }
      }
      FluidPdfPageSource.open(copy).use { source ->
        pdf = source
        awaitCancellation()
      }
    } catch (e: IOException) {
      error = e.localizedMessage
    } finally {
      copy.delete()
    }
  }
  val photos = listOf(Example.Launch, Example.Astronaut).map {
    FluidViewerContent.Image(painterResource(requireNotNull(it.cover)), it.label)
  }
  val movie = remember(session) { fluidMedia3Content(session) }
  FluidFileViewer(Example.entries.size, onBack = onBack, onPageChange = { selected = it },
    info = { i ->
      Column(Modifier.padding(16.dp)) {
        Text(Example.entries[i].label, style = MaterialTheme.typography.titleLarge)
        Text(Example.entries[i].credit)
      }
    }, content = { index ->
      when (Example.entries[index]) {
        Example.Launch, Example.Astronaut -> photos[index]
        Example.Flower -> movie
        Example.Notes -> pdf?.let(FluidViewerContent::Pages)
          ?: FluidViewerContent.Unknown(FluidFileInfo(error ?: "Quaderno di campo.pdf"))
      }
    })
}
