package dev.antigravity.fluidengine.viewer

import android.net.Uri
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * R2 · Riprendi dallo stesso punto: una scrittura sul disco che fallisce una volta (disco pieno un attimo, una cartella
 * che non si crea) non perde le posizioni in memoria. Il negozio riprova da sé, e un `flush` dopo scrive davvero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidViewerResumeRetryTest {
  private val context get() = RuntimeEnvironment.getApplication()

  private fun pdf(name: String) = FluidViewerFile(Uri.parse("content://prova/$name"), name, "application/pdf", 4096, 77L)

  @Test
  fun `a failed write is retried by itself once the disk works again`() {
    val root = File(context.cacheDir, "resume-r2-${System.nanoTime()}").apply { mkdirs() }
    // La cartella del negozio è un file: la scrittura non può riuscire finché c'è.
    val blocker = File(root, "fluid-viewer").apply { writeText("non una cartella") }
    val file = File(blocker, "resume-v1.tsv")
    val store = FluidViewerResumeStore(file, saveDelayMillis = 20)
    try {
      store.slot(pdf("Manuale.pdf")).save(FluidViewerPosition(page = 12))
      store.flush()
      assertFalse(file.exists())
      blocker.delete()
      // Senza toccare il negozio: il tentativo dopo arriva da sé.
      val deadline = System.currentTimeMillis() + 10_000
      while (!file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
      assertTrue("la posizione non è mai stata scritta", file.exists())
      val again = FluidViewerResumeStore(file)
      assertEquals(12, again.read(FluidViewerResumeStore.keyOf(pdf("Manuale.pdf")))?.position?.page)
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun `a flush after a failed write writes what is in memory`() {
    val root = File(context.cacheDir, "resume-r2-${System.nanoTime()}").apply { mkdirs() }
    val blocker = File(root, "fluid-viewer").apply { writeText("non una cartella") }
    val file = File(blocker, "resume-v1.tsv")
    // Un'attesa lunga: il tentativo da sé non arriva durante la prova, lo fa il flush.
    val store = FluidViewerResumeStore(file, saveDelayMillis = 60_000)
    try {
      store.slot(pdf("Libro.pdf")).save(FluidViewerPosition(page = 30))
      store.flush()
      assertFalse(file.exists())
      blocker.delete()
      store.flush()
      assertTrue("il flush non ha scritto", file.exists())
      assertEquals(30, FluidViewerResumeStore(file).read(FluidViewerResumeStore.keyOf(pdf("Libro.pdf")))?.position?.page)
    } finally {
      root.deleteRecursively()
    }
  }
}
