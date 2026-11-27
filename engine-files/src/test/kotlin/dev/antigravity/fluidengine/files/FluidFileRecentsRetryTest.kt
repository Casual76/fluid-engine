package dev.antigravity.fluidengine.files

import android.net.Uri
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
 * R1-C (da R2, Muse visori 9) · I recenti: una scrittura sul disco che fallisce una volta (disco pieno un attimo, una
 * cartella che non si crea) non perde la lista in memoria. Si riprova da sé, anche se non arriva un'altra modifica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidFileRecentsRetryTest {
  private val context get() = RuntimeEnvironment.getApplication()

  @Test
  fun `a failed write of the recents is retried by itself once the disk works again`() {
    val root = File(context.cacheDir, "recenti-r2-${System.nanoTime()}").apply { mkdirs() }
    // La cartella dei recenti è un file: la scrittura non può riuscire finché c'è.
    val blocker = File(root, "fluid-files").apply { writeText("non una cartella") }
    val file = File(blocker, "recents-v1.tsv")
    val recents = FluidFileRecents(file)
    try {
      recents.record("app", FluidFileItem("1", "Contratto.pdf", mimeType = "application/pdf", uri = Uri.parse("content://p/1")))
      recents.awaitWrites()
      assertFalse(file.exists())
      blocker.delete()
      // Senza toccare i recenti: il tentativo dopo arriva da sé.
      val deadline = System.currentTimeMillis() + 10_000
      while (!file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
      assertTrue("i recenti non sono mai stati scritti", file.exists())
      recents.awaitWrites()
      assertEquals(listOf("Contratto.pdf"), FluidFileRecents(file).entries.map { it.item.name })
    } finally {
      root.deleteRecursively()
    }
  }
}
