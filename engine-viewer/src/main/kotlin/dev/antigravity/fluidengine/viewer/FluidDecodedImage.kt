package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import kotlinx.coroutines.currentCoroutineContext
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidAnimatedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Un'immagine decodificata dal modulo, ferma o animata, pronta per il preset immagine.
 *
 * Le GIF e le WebP animate (e le HEIF animate dove la piattaforma le anima) diventano un `AnimatedImageDrawable`
 * dietro un [Painter]: il preset le riceve come un `FluidViewerContent.Image` qualunque ([content]) e il telaio dà
 * loro zoom, sfoglio e chiusura come a una foto. L'animazione **parte quando l'immagine entra in composizione e si
 * ferma quando esce** (lo sfoglio la toglie), e col **movimento ridotto** resta ferma sul primo fotogramma.
 * L'animazione sta su Android 9 (API 28) e oltre: sotto, `BitmapFactory` dà il primo fotogramma.
 */
@Stable
class FluidDecodedImage private constructor(
  /** La misura decodificata in pixel (già ridotta, se l'originale superava il tetto). */
  val width: Int,
  val height: Int,
  /** La misura dell'originale, come la dichiara il file. */
  val sourceWidth: Int,
  val sourceHeight: Int,
  private val bitmap: ImageBitmap?,
  /**
   * Il drawable che si riproduce (un `AnimatedImageDrawable`, che è `Animatable`), `null` se l'immagine è ferma. È
   * quello da dare a `fluidAnimatedImage` del preset immagine di engine-ui (F6C.2), che lo fa partire solo sulla
   * pagina ferma e gli dà l'azione TalkBack «Riproduci»; [content] lo disegna da sé quando quel preset non c'è.
   */
  val animation: Drawable?,
) {
  /** Vero se è un contenuto che si riproduce (una GIF o una WebP animata, decodificata su Android 9+). */
  val animated: Boolean get() = animation != null

  /** La bitmap di un'immagine ferma, `null` se è animata. */
  val still: ImageBitmap? get() = bitmap

  /** Il rapporto d'aspetto, per il telaio (limiti del pan, elemento condiviso). */
  val aspectRatio: Float get() = width.toFloat() / height.coerceAtLeast(1)

  /**
   * Il contenuto per il visore: un `FluidViewerContent.Image` col rapporto d'aspetto. Animata, passa dal preset
   * immagine di engine-ui (`fluidAnimatedImage`: parte solo sulla pagina ferma, l'azione TalkBack «Riproduci»); ferma,
   * il disegno di [Body].
   */
  fun content(contentDescription: String? = null): FluidViewerContent.Image {
    val drawable = animation ?: return FluidViewerContent.Image(aspectRatio) { Body(contentDescription, Modifier.fillMaxSize()) }
    return fluidAnimatedImage(drawable, contentDescription, aspectRatio = aspectRatio)
  }

  /** Disegna l'immagine adattata al posto che ha; se è animata la fa partire (salvo il movimento ridotto). */
  @Composable
  fun Body(contentDescription: String?, modifier: Modifier = Modifier) {
    val still = bitmap
    if (still != null) {
      Image(still, contentDescription, modifier, contentScale = ContentScale.Fit)
      return
    }
    val reduced = LocalFluidMotionPolicy.current.reducedMotion
    val drawable = animation ?: return
    val painter = remember(drawable, reduced) { DrawablePainter(drawable, play = !reduced) }
    Image(painter, contentDescription, modifier, contentScale = ContentScale.Fit)
  }

  companion object {
    /**
     * Il tetto di serie: 12 MP. Una foto di telefono normale si decodifica intera; una da 50 o 200 MP si riduce
     * (per quelle i tasselli sono dell'app: il suo composable va come contenuto `Image`). A occhio, 22 novembre 2026.
     */
    const val DefaultMaxPixels: Long = 12_000_000

    /** Decodifica da un `Uri` (`content://`, `file://`, `android.resource://`). */
    suspend fun decode(
      context: Context,
      uri: Uri,
      maxPixels: Long = DefaultMaxPixels,
      animate: Boolean = true,
    ): FluidDecodedImage {
      ViewerMemory.get(context)
      val resolver = context.applicationContext.contentResolver
      return decode(maxPixels, animate,
        source = { if (Build.VERSION.SDK_INT >= 28) ImageDecoder.createSource(resolver, uri) else null },
        stream = { resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString()) },
      )
    }

    /** Decodifica da un file dell'app. */
    suspend fun decode(file: File, maxPixels: Long = DefaultMaxPixels, animate: Boolean = true): FluidDecodedImage =
      decode(maxPixels, animate,
        source = { if (Build.VERSION.SDK_INT >= 28) ImageDecoder.createSource(file) else null },
        stream = { file.inputStream() },
      )

    private suspend fun decode(
      maxPixels: Long,
      animate: Boolean,
      source: () -> Any?,
      stream: () -> InputStream,
    ): FluidDecodedImage = withContext(Dispatchers.IO) {
      require(maxPixels > 0) { "maxPixels deve essere positivo" }
      try {
        val memory = ViewerMemory.get()
        val optional = currentCoroutineContext()[OptionalViewerWork] != null
        memory.whileAllowed(optional) {
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          if (currentCoroutineContext()[ThumbnailAllocation] != null) {
            stream().use { BitmapFactory.decodeStream(it, null, bounds) }
          } else {
            val header = memory.reserve(FluidMemoryCategory.VIEWER, 65536, optional) ?: noViewerMemory()
            header.use { stream().use { BitmapFactory.decodeStream(it, null, bounds) } }
          }
          val fallback = sqrt(maxPixels.toDouble()).toInt().coerceAtLeast(1)
          val width = bounds.outWidth.takeIf { it > 0 } ?: fallback
          val height = bounds.outHeight.takeIf { it > 0 } ?: fallback
          val target = viewerPixelSize(width, height, maxPixels)
          val multiFrame = animate && bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/bmp")
          fun platformDecode(admitted: IntSize): FluidDecodedImage {
            val limit = minOf(maxPixels, admitted.width.toLong() * admitted.height)
            val platform = source()
            return if (Build.VERSION.SDK_INT >= 28 && platform != null)
              decodeWithImageDecoder(platform as ImageDecoder.Source, limit, animate)
            else decodeWithBitmapFactory(stream, limit)
          }
          if (currentCoroutineContext()[ThumbnailAllocation] != null) return@whileAllowed platformDecode(target)
          memory.decode(FluidMemoryCategory.VIEWER, target, optional,
            { pixelPeak(it, if (multiFrame) 32 else 16) }, { platformDecode(it) }, { it.still?.pixelBytes ?: pixelPeak(IntSize(it.width, it.height), 24) },
            { it.still?.physical ?: checkNotNull(it.animation) },
            { it.still?.asAndroidBitmap()?.recycle() }) { pixels -> pixels.use { it.read(null) } } ?: noViewerMemory()
        }
      } catch (e: FluidViewerException) {
        throw e
      } catch (e: FileNotFoundException) {
        throw FluidViewerException(FluidViewerFailure.Unreadable, "Immagine non leggibile", e)
      } catch (e: SecurityException) {
        throw FluidViewerException(FluidViewerFailure.Unreadable, "Permesso negato", e)
      } catch (e: IOException) {
        // `ImageDecoder.DecodeException` è un IOException: byte rotti o un formato che la piattaforma non conosce.
        throw FluidViewerException(FluidViewerFailure.Corrupt, "Immagine non decodificabile", e)
      }
    }

    @RequiresApi(28)
    private fun decodeWithImageDecoder(source: ImageDecoder.Source, maxPixels: Long, animate: Boolean): FluidDecodedImage {
      var sourceWidth = 0
      var sourceHeight = 0
      val listener = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
        sourceWidth = info.size.width
        sourceHeight = info.size.height
        val target = viewerPixelSize(sourceWidth, sourceHeight, maxPixels)
        if (target.width != sourceWidth || target.height != sourceHeight) decoder.setTargetSize(target.width, target.height)
        // Un file troncato (un download a metà) mostra ciò che c'è invece di un errore.
        decoder.setOnPartialImageListener { true }
      }
      if (!animate) {
        val bitmap = ImageDecoder.decodeBitmap(source, listener)
        return FluidDecodedImage(bitmap.width, bitmap.height, sourceWidth, sourceHeight, bitmap.asImageBitmap(), null)
      }
      return when (val drawable = ImageDecoder.decodeDrawable(source, listener)) {
        is AnimatedImageDrawable -> FluidDecodedImage(
          drawable.intrinsicWidth, drawable.intrinsicHeight, sourceWidth, sourceHeight, null, drawable,
        )
        is BitmapDrawable -> FluidDecodedImage(
          drawable.bitmap.width, drawable.bitmap.height, sourceWidth, sourceHeight, drawable.bitmap.asImageBitmap(), null,
        )
        else -> {
          val width = drawable.intrinsicWidth.coerceAtLeast(1)
          val height = drawable.intrinsicHeight.coerceAtLeast(1)
          val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          drawable.setBounds(0, 0, width, height)
          drawable.draw(android.graphics.Canvas(bitmap))
          FluidDecodedImage(width, height, sourceWidth, sourceHeight, bitmap.asImageBitmap(), null)
        }
      }
    }

    private fun decodeWithBitmapFactory(stream: () -> InputStream, maxPixels: Long): FluidDecodedImage {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      stream().use { BitmapFactory.decodeStream(it, null, bounds) }
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw FluidViewerException(FluidViewerFailure.Unsupported, "Formato d'immagine non decodificabile qui")
      }
      var sample = 1
      while ((bounds.outWidth + sample.toLong() - 1) / sample *
        ((bounds.outHeight + sample.toLong() - 1) / sample) > maxPixels) {
        if (sample == 1 shl 30) noViewerMemory()
        sample *= 2
      }
      val options = BitmapFactory.Options().apply { inSampleSize = sample }
      val bitmap = stream().use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw FluidViewerException(FluidViewerFailure.Corrupt, "Immagine non decodificabile")
      return FluidDecodedImage(bitmap.width, bitmap.height, bounds.outWidth, bounds.outHeight, bitmap.asImageBitmap(), null)
    }
  }
}

private val MainHandler by lazy { Handler(Looper.getMainLooper()) }

/**
 * Un `Drawable` in Compose. Su un canvas accelerato `AnimatedImageDrawable` avanza da solo sul RenderThread; altrove
 * chiede un nuovo disegno con `invalidateSelf`, che qui diventa uno stato letto dal disegno. Parte quando è ricordato
 * e si ferma quando è dimenticato: è il punto in cui il pager toglie la pagina.
 */
private class DrawablePainter(private val drawable: Drawable, private val play: Boolean) : Painter(), RememberObserver {
  private var invalidations by mutableIntStateOf(0)

  private val callback = object : Drawable.Callback {
    override fun invalidateDrawable(who: Drawable) {
      invalidations++
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
      MainHandler.postAtTime(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
      MainHandler.removeCallbacks(what)
    }
  }

  override val intrinsicSize: Size =
    Size(drawable.intrinsicWidth.toFloat().coerceAtLeast(1f), drawable.intrinsicHeight.toFloat().coerceAtLeast(1f))

  override fun onRemembered() {
    drawable.callback = callback
    if (Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) {
      if (play) drawable.start() else drawable.stop()
    }
  }

  override fun onForgotten() {
    if (Build.VERSION.SDK_INT >= 28 && drawable is AnimatedImageDrawable) drawable.stop()
    if (drawable.callback === callback) drawable.callback = null
  }

  override fun onAbandoned() = onForgotten()

  override fun DrawScope.onDraw() {
    // Letto per iscriversi: ogni invalidateSelf ridisegna.
    invalidations
    drawIntoCanvas { canvas ->
      drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
      drawable.draw(canvas.nativeCanvas)
    }
  }
}
