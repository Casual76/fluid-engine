package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFileProblem
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareItem
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareStaged
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * F8.10 · Un file qualsiasi dell'app si condivide senza crash: [FluidShareItem.file] non lancia mai, e
 * [FluidShareFiles.stage] copia nella cartella dell'engine, o dice perche' non si puo'.
 *
 * Sul JVM di Windows il `FileProvider` vero non trova le sue radici: l'Uri lo da' un provider finto
 * ([FluidShareFiles.overrideUrisForTesting]). La logica del percorso e la copia sono provate al JVM in engine-ui
 * (`FluidShareStagingTest`); il caso vero col FileProvider si guarda sul tablet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class F8p10StagingTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @After fun stop() {
    FluidShareFiles.overrideUrisForTesting(null)
    File(context.filesDir, "f8-10-prove").deleteRecursively()
    FluidShareFiles.root(context).deleteRecursively()
  }

  private fun write(file: File, size: Int): File {
    file.parentFile?.mkdirs()
    file.writeBytes(ByteArray(size) { (it % 251).toByte() })
    return file
  }

  private fun fakeUris() = FluidShareFiles.overrideUrisForTesting { file ->
    Uri.parse("content://prova.fluid.share/${file.parentFile!!.name}/${Uri.encode(file.name)}")
  }

  @Test fun anAppFileOutsideTheFolderBecomesALocalFileWithoutThrowing() {
    val photo = write(File(context.filesDir, "f8-10-prove/Fiori con posizione.jpg"), 5_000)
    val item = FluidShareItem.file(context, photo, "image/jpeg", title = "1 foto")
    assertTrue(item.needsCopy)
    assertTrue(item.hasFiles)
    assertEquals(1, item.fileCount)
    assertTrue(item.uris.isEmpty())
    assertEquals(5_000L, item.sizeBytes)
    assertFalse(item.isLink)
    assertFalse(FluidShareFiles.isShareable(context, photo))
  }

  @Test fun aFileInTheFolderGetsItsUriAtOnce() {
    fakeUris()
    val image = write(FluidShareFiles.newFile(context, "Voto.png"), 1_000)
    assertTrue(FluidShareFiles.isShareable(context, image))
    val item = FluidShareItem.file(context, image, "image/png")
    assertFalse(item.needsCopy)
    assertEquals(1, item.uris.size)
  }

  @Test fun withoutTheFakeProviderAFileInTheFolderStillDoesNotThrow() {
    // Sul JVM di Windows il provider vero lancia: l'oggetto ripiega sulla copia, e il tocco non va in crash.
    val image = write(FluidShareFiles.newFile(context, "Voto.png"), 1_000)
    val item = FluidShareItem.file(context, image, "image/png")
    assertTrue(item.hasFiles)
  }

  @Test fun stagingCopiesAndHandsBackContentUris() = runBlocking {
    fakeUris()
    val photo = write(File(context.filesDir, "f8-10-prove/Fiori.jpg"), 3_000)
    val staged = FluidShareFiles.stage(context, FluidShareItem.file(context, photo, "image/jpeg"))
    staged as FluidShareStaged.Ready
    assertFalse(staged.item.needsCopy)
    val uri = staged.item.uris.single()
    assertEquals("content", uri.scheme)
    assertEquals("Fiori.jpg", uri.lastPathSegment)
    val copy = File(File(FluidShareFiles.root(context), uri.pathSegments[0]), "Fiori.jpg")
    assertArrayEquals(photo.readBytes(), copy.readBytes())
    assertTrue("l'originale resta dov'era", photo.exists())
  }

  @Test fun stagingSeveralFilesKeepsTheOrder() = runBlocking {
    fakeUris()
    val inside = write(FluidShareFiles.newFile(context, "Pagella.pdf"), 1_000)
    val outside = write(File(context.filesDir, "f8-10-prove/Foto.jpg"), 2_000)
    val item = FluidShareItem.files(context, listOf(inside, outside), "2 elementi", "*/*")
    assertEquals(2, item.fileCount)
    val staged = FluidShareFiles.stage(context, item) as FluidShareStaged.Ready
    assertEquals(listOf("Pagella.pdf", "Foto.jpg"), staged.item.uris.map { it.lastPathSegment })
    assertEquals(3_000L, staged.item.sizeBytes)
  }

  @Test fun stagingAMissingFileFailsWithTheReason() = runBlocking {
    fakeUris()
    val item = FluidShareItem.file(context, File(context.filesDir, "f8-10-prove/non-c-e.jpg"), "image/jpeg")
    val staged = FluidShareFiles.stage(context, item)
    staged as FluidShareStaged.Failed
    assertEquals(FluidShareFileProblem.Missing, staged.problem)
    assertEquals("non-c-e.jpg", staged.fileName)
  }

  @Test fun stagingOverTheLimitSaysHowMuch() = runBlocking {
    fakeUris()
    val video = write(File(context.filesDir, "f8-10-prove/Video.mp4"), 4_096)
    val staged = FluidShareFiles.stage(context, FluidShareItem.file(context, video, "video/mp4"), limitBytes = 1_024)
    staged as FluidShareStaged.Failed
    assertEquals(FluidShareFileProblem.TooLarge, staged.problem)
    assertEquals(4_096L, staged.sizeBytes)
    assertEquals(1_024L, staged.limitBytes)
  }

  @Test fun aFileInTheFolderThatIsGoneSaysSo() = runBlocking {
    // R2: un'uscita cancellata (Android ha svuotato la cache) dentro la cartella dell'engine. Prima aveva subito il suo
    // Uri, e il foglio mandava un Uri morto senza dire perche'.
    fakeUris()
    val gone = File(FluidShareFiles.newFile(context, "Sparito.png").path)
    val item = FluidShareItem.file(context, gone, "image/png")
    assertTrue("si controlla come un file da copiare", item.needsCopy)
    val staged = FluidShareFiles.stage(context, item)
    assertEquals(FluidShareFileProblem.Missing, (staged as FluidShareStaged.Failed).problem)
  }

  @Test fun anItemWithoutLocalFilesIsReadyAsItIs() = runBlocking {
    val link = FluidShareItem.link("https://example.org")
    val staged = FluidShareFiles.stage(context, link)
    assertTrue(staged is FluidShareStaged.Ready && staged.item === link)
  }
}
