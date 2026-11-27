package dev.antigravity.fluidengine.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * R1-C · L'apertura della foto (review R1, Muse 10): un'apertura annullata (l'editor esce mentre la foto si legge) non
 * diventa un errore di apertura: l'annullo risale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidImageEditorLoadTest {
  private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun `an interrupted load is not a load error`() = runBlocking {
    val file = File(context.cacheDir, "annullo-${System.nanoTime()}.png")
    file.outputStream().use {
      Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF808080.toInt()) }
        .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    val state = FluidImageEditorState(FluidEditorSource.uri(Uri.fromFile(file)), FluidImageEdit.Original, FluidImageEdit.Original, context)
    val job = launch(start = CoroutineStart.UNDISPATCHED) { state.load(256) }
    job.cancel()
    job.join()
    assertNull("un annullo non e' un errore: ${state.loadError}", state.loadError)
  }
}
