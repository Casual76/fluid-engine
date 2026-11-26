package dev.antigravity.fluidengine.scan

import android.content.res.Configuration
import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * R1 · L'attività dello scanner del contratto spegne il velo di contrasto della barra a tre tasti (la regola delle app
 * dell'engine), anche dopo una rotazione, che gestisce da sé. E lo scanner stacca solo le sue sorgenti di CameraX.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanActivityR1Test {
  @Test fun ilVeloDellaBarraDiNavigazioneResteSpento() {
    val controller = Robolectric.buildActivity(FluidScanActivity::class.java).create()
    val activity = controller.get()
    assertFalse("dopo enableEdgeToEdge", activity.window.isNavigationBarContrastEnforced)
    val turned = Configuration(activity.resources.configuration).apply {
      orientation = Configuration.ORIENTATION_LANDSCAPE
      screenWidthDp = 891
      screenHeightDp = 411
    }
    controller.configurationChange(turned)
    shadowOf(Looper.getMainLooper()).idle()
    assertFalse("dopo la rotazione", activity.window.isNavigationBarContrastEnforced)
  }

  /**
   * `ProcessCameraProvider` è uno per processo: `unbindAll()` staccherebbe anche l'anteprima dell'app ospite. Una prova
   * col CameraX vero chiederebbe `camera-testing` (una dipendenza nuova); qui si guarda il sorgente, come le guardie.
   */
  @Test fun loScannerStaccaSoloLeSueSorgentiDiCameraX() {
    val source = File("src/main/kotlin/dev/antigravity/fluidengine/scan/FluidCameraXScanCamera.kt").readText()
    assertFalse("unbindAll() stacca anche le fotocamere dell'app ospite", source.contains("unbindAll("))
    assertTrue("lo scanner stacca le sue tre sorgenti", source.contains("unbind(preview, analysis, still)"))
  }
}
