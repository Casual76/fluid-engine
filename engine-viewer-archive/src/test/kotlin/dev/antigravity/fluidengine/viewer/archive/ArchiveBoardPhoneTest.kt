package dev.antigravity.fluidengine.viewer.archive

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h891dp-mdpi")
class ArchiveBoardPhoneTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun archiveAt412Dp() = checkArchive(412)

  @Config(sdk = [35], qualifiers = "w360dp-h780dp-mdpi")
  @Test fun archiveAt360Dp() = checkArchive(360)

  private fun checkArchive(expectedWidth: Int) {
    val context = compose.activity
    val spec = FluidViewerFamilyType("board", "Archivio ZIP", setOf("archivedboard"), kind = FluidFileKind.Archive)
    // Exercise the real page with an already-read index. The native libarchive reader belongs to device tests.
    val family = object : FluidViewerFamily {
      override val id = "archive-board-test"
      override val types = listOf(spec)

      override suspend fun open(request: FluidViewerRequest): FluidViewerOpened {
        val files = listOf(
          "app/settings.kt" to 6_200_000L, "docs/guida.md" to 3_100_000L,
          "immagini/paesaggio.png" to 2_900_000L, "README.md" to 4_000L, "logo.png" to 86_000L,
          "relazione.pdf" to 412_000L, "build.gradle.kts" to 3_000L, "chiavi.txt" to 1_000L,
        ).mapIndexed { index, (name, bytes) ->
          ArchiveItem(index, name, ArchiveKind.File, bytes, null, encrypted = name == "chiavi.txt")
        }
        val index = ArchiveIndex("ZIP", null, 4_100_000L, files, ArchiveProtection.Entries, null)
        val session = ArchiveSession(request, ArchiveFormat.Zip, NativeArchive(request.source, ArchiveFormat.Zip), index) {}
        return FluidViewerOpened(archivePageContent(session), onClose = session::close)
      }
    }
    FluidViewerRegistry.install(family)
    val file = File.createTempFile("archive-board", ".archivedboard", context.cacheDir).apply { writeText("layout fixture") }
    try {
      val opening = runBlocking {
        FluidViewerRegistry.open(context, FluidViewerSource.of(file, "Progetto.archivedboard"))
      }
      opening.use { document ->
        assertEquals(family.id, document.type.family)
        val metadata = FluidViewerInfo(document.file.info.copy(name = "Progetto.zip", kind = FluidFileKind.Archive))
        var dark by mutableStateOf(false)
        compose.setContent {
          FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
            FluidViewerInfoProvider({ metadata }) { FluidFileViewer(1, content = { document.content }) }
          }
        }
        assertEquals(expectedWidth.toFloat(), compose.onRoot().fetchSemanticsNode().boundsInRoot.width, 1f)
        if (expectedWidth == 412) capture("chiaro")
        assertLayout()
        compose.runOnIdle { dark = true }
        if (expectedWidth == 412) capture("scuro")
        assertLayout()
        compose.onNodeWithContentDescription("Trova").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("gradle")
        compose.onNodeWithText("build.gradle.kts").assertIsDisplayed()
        compose.onNodeWithText("README.md").assertDoesNotExist()
      }
    } finally {
      FluidViewerRegistry.uninstall(family.id)
      file.delete()
    }
  }

  private fun assertLayout() {
    val left = compose.onNodeWithContentDescription("Altro").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val center = compose.onNodeWithContentDescription("Estrai tutto").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val right = compose.onNodeWithContentDescription("Trova").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    assertTrue("Controlli archivio: $left, $center, $right", left.right <= center.left && center.right <= right.left)
    val folders = listOf("app", "docs", "immagini").map { compose.onNodeWithText(it, useUnmergedTree = true)
      .assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
    assertEquals(folders.first().top, folders.last().top, 1f)
    assertTrue(folders.zipWithNext().all { (a, b) -> a.right < b.left })
    val first = compose.onNodeWithText("build.gradle.kts", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val second = compose.onNodeWithText("chiavi.txt", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    assertEquals(58f, second.top - first.top, 6f)
  }

  private fun capture(theme: String) {
    val output = System.getenv("FLUID_F6C18_EVIDENCE") ?: return
    val folder = File(output, theme).apply { mkdirs() }
    File(folder, "archivio.tsv").writeText(compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 16))
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
      var status = PixelCopy.ERROR_UNKNOWN
      compose.runOnIdle {
        PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap,
          { status = it }, Handler(Looper.getMainLooper()))
      }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(PixelCopy.SUCCESS, status)
      File(folder, "archivio.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally { bitmap.recycle() }
  }
}
