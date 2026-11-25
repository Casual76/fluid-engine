package dev.antigravity.fluidengine.ui.fluid.share

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.UUID

/**
 * F8.10 · Un file dell'app fuori dalla cartella dell'engine si copia quando si condivide, invece di mandare in crash il
 * tocco su Condividi (il crash del 24 novembre sul tablet: una foto di `filesDir`).
 *
 * Il FileProvider vero sul JVM di Windows non trova le sue radici, quindi qui si prova la logica del percorso con la
 * funzione pura ([fluidIsUnder]) e la copia vera su file veri ([fluidStageFiles]), al JVM. L'oggetto, l'Uri (da un
 * provider finto) e il foglio si provano nel sample (`F8p10StagingTest`, `F8p10ShareUiTest`); il caso vero, col
 * FileProvider, si guarda sul tablet.
 */
class FluidShareStagingTest {
  private lateinit var tmp: File
  private lateinit var root: File

  @Before fun start() {
    tmp = Files.createTempDirectory("fluid-share-staging").toFile()
    root = File(tmp, "cache/fluid-share").apply { mkdirs() }
  }

  @After fun stop() {
    tmp.deleteRecursively()
  }

  private fun write(file: File, size: Int): File {
    file.parentFile?.mkdirs()
    file.writeBytes(ByteArray(size) { (it % 251).toByte() })
    return file
  }

  private fun newFileUnder(base: File): (String) -> File = { name ->
    File(File(base, UUID.randomUUID().toString().take(12)).apply { mkdirs() }, name)
  }

  private val roomy = Long.MAX_VALUE / 4

  // --- Dentro o fuori la radice ----------------------------------------------------------------------------------

  @Test fun insideAndOutsideTheRootAreToldApartFolderByFolder() {
    assertTrue(fluidIsUnder(root, File(root, "a1b2/Voto.png")))
    assertTrue(fluidIsUnder(root, File(root, "a1b2/../c3d4/Voto.png")))
    assertFalse("la radice stessa non e' un file da mandare", fluidIsUnder(root, root))
    assertFalse("un nome che comincia come la radice non e' dentro", fluidIsUnder(root, File(tmp, "cache/fluid-share2/x.png")))
    assertFalse(fluidIsUnder(root, File(tmp, "files/f8-10/Fiori con posizione.jpg")))
    assertFalse("«..» non fa uscire e rientrare", fluidIsUnder(root, File(root, "../Fiori.jpg")))
  }

  // --- La copia ---------------------------------------------------------------------------------------------------

  @Test fun aFileOutsideIsCopiedUnderTheRootWithItsNameAndBytes() = runBlocking {
    val photo = write(File(tmp, "files/f8-10/Fiori con posizione.jpg"), 70_000)
    val result = fluidStageFiles(root, listOf(photo), limitBytes = 1_000_000, freeBytes = roomy, newFile = newFileUnder(root))
    result as FluidStageResult.Done
    val copy = result.files.single()
    assertNotEquals(photo, copy)
    assertTrue(fluidIsUnder(root, copy))
    assertEquals("Fiori con posizione.jpg", copy.name)
    assertArrayEquals(photo.readBytes(), copy.readBytes())
    assertTrue("l'originale resta", photo.exists())
    assertEquals(70_000L, result.totalBytes)
  }

  @Test fun aFileAlreadyInsideIsNotCopiedAndTheOrderStays() = runBlocking {
    val inside = write(File(root, "e5f6/Pagella.pdf"), 1_000)
    val outside = write(File(tmp, "files/Foto.jpg"), 2_000)
    val before = root.walkTopDown().count()
    val result = fluidStageFiles(root, listOf(inside, outside), 1_000_000, roomy, newFileUnder(root)) as FluidStageResult.Done
    assertEquals(inside, result.files[0])
    assertTrue(fluidIsUnder(root, result.files[1]))
    assertEquals("una cartella e un file nuovi, per la sola copia", before + 2, root.walkTopDown().count())
    assertEquals(3_000L, result.totalBytes)
  }

  @Test fun aMissingFileIsAProblemNotAnException() = runBlocking {
    val result = fluidStageFiles(root, listOf(File(tmp, "files/non-c-e.jpg")), 1_000, roomy, newFileUnder(root))
    assertEquals(FluidShareFileProblem.Missing, (result as FluidStageResult.Problem).problem)
  }

  @Test fun aFolderIsUnreadable() = runBlocking {
    val folder = File(tmp, "files/cartella").apply { mkdirs() }
    val result = fluidStageFiles(root, listOf(folder), 1_000, roomy, newFileUnder(root))
    assertEquals(FluidShareFileProblem.Unreadable, (result as FluidStageResult.Problem).problem)
  }

  @Test fun overTheLimitNothingIsCopied() = runBlocking {
    val big = write(File(tmp, "files/Video.mp4"), 4_096)
    val result = fluidStageFiles(root, listOf(big), limitBytes = 1_024, freeBytes = roomy, newFile = newFileUnder(root))
    result as FluidStageResult.Problem
    assertEquals(FluidShareFileProblem.TooLarge, result.problem)
    assertEquals(4_096L, result.sizeBytes)
    assertEquals("niente nella cartella dell'engine", 1, root.walkTopDown().count())
  }

  @Test fun aFileInsideDoesNotCountAgainstTheLimit() = runBlocking {
    val inside = write(File(root, "a1/Grande.pdf"), 8_192)
    val result = fluidStageFiles(root, listOf(inside), limitBytes = 1_024, freeBytes = 0, newFile = newFileUnder(root))
    assertTrue("non si copia, quindi niente tetto e niente spazio", result is FluidStageResult.Done)
  }

  @Test fun withoutFreeSpaceNothingIsCopied() = runBlocking {
    val photo = write(File(tmp, "files/Foto.jpg"), 2_048)
    val result = fluidStageFiles(root, listOf(photo), 1_000_000, freeBytes = FluidStageSpareBytes, newFile = newFileUnder(root))
    assertEquals(FluidShareFileProblem.NoSpace, (result as FluidStageResult.Problem).problem)
    assertEquals(1, root.walkTopDown().count())
  }

  @Test fun theMessagesAreHuman() {
    val labels = FluidShareLabels()
    fun failed(problem: FluidShareFileProblem) = FluidShareStaged.Failed(problem, "Foto.jpg", 2_048, 1_024)
    assertEquals(labels.fileMissing, fluidShareProblemText(failed(FluidShareFileProblem.Missing), labels, Locale.ITALY))
    assertEquals(labels.fileUnreadable, fluidShareProblemText(failed(FluidShareFileProblem.Unreadable), labels, Locale.ITALY))
    val space = fluidShareProblemText(failed(FluidShareFileProblem.NoSpace), labels, Locale.ITALY)
    assertTrue(space, space.startsWith("Non c'è abbastanza spazio libero") && !space.contains("%"))
    val english = fluidShareProblemText(failed(FluidShareFileProblem.TooLarge), FluidShareLabels.english(), Locale.US)
    assertTrue(english, english.startsWith("The file is too large") && !english.contains("%"))
  }
}
