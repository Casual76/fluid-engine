package dev.antigravity.fluidengine.editor

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowNativeBitmap
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector

/**
 * R3 · Il disco pieno a meta' di una codifica, come lo vede `Bitmap.compress` sul telefono: il codificatore scrive una
 * parte dei byte, lo scrittore fallisce (`ENOSPC`), e `compress` torna **falso** invece di lanciare (il nativo di Android
 * prende l'eccezione dello scrittore e la tace). Solo per i JPEG alla qualita' [failJpegQuality]; tutto il resto passa
 * al codificatore vero. Estende l'ombra nativa (grafica nativa di Robolectric, sdk 35), come le ombre dei bilanci.
 */
@Implements(value = Bitmap::class, minSdk = 26, isInAndroidSdk = false, callNativeMethodsByDefault = true)
class FailingCompressBitmap : ShadowNativeBitmap() {
  @RealObject private lateinit var real: Bitmap

  @Implementation
  protected fun compress(format: Bitmap.CompressFormat, quality: Int, stream: OutputStream): Boolean {
    val encoder = reflector(BitmapReflector::class.java, real)
    if (format != Bitmap.CompressFormat.JPEG || quality != failJpegQuality) return encoder.compress(format, quality, stream)
    val whole = ByteArrayOutputStream()
    encoder.compress(format, quality, whole)
    val bytes = whole.toByteArray()
    stream.write(bytes, 0, bytes.size / 3)
    failures++
    return false
  }

  @ForType(Bitmap::class)
  interface BitmapReflector {
    @Direct fun compress(format: Bitmap.CompressFormat, quality: Int, stream: OutputStream): Boolean
  }

  companion object {
    /** La qualita' JPEG che fallisce (-1: nessuna). */
    @Volatile @JvmStatic var failJpegQuality: Int = -1
    /** Quante codifiche sono fallite. */
    @Volatile @JvmStatic var failures: Int = 0
  }
}
