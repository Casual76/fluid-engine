package dev.antigravity.fluidengine.ui.fluid.share

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * La lettura a campioni di un'immagine (4.0, F8.10): **l'unico posto di engine-ui che legge un'immagine dai suoi byte**.
 *
 * Due usi, tutti e due ridotti di una potenza di due e mai l'immagine intera in memoria: la miniatura in cima al foglio di
 * condivisione ([atLeast]) e l'immagine da stampare ([atMost]). Le immagini vere da guardare (animate, HDR, i formati
 * della piattaforma, i visori) le decodifica `engine-viewer`: engine-ui resta senza decodificatori, e
 * `EngineUiDecoderFreeTest` ammette `BitmapFactory` solo qui.
 *
 * Le misure si leggono con `inJustDecodeBounds`, che torna **sempre** null: stanno nelle opzioni, non nel risultato (un
 * `?: return null` sulla lettura delle misure faceva uscire ogni lettura, la miniatura e la stampa: il tablet il 25
 * novembre 2026). Mai sul thread principale.
 */
internal object FluidImageSample {

  /** Le misure dell'immagine di [uri] senza leggerne i pixel, o null se non si apre o non e' un'immagine. */
  fun bounds(resolver: ContentResolver, uri: Uri): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val stream = resolver.openInputStream(uri) ?: return null
    stream.use { BitmapFactory.decodeStream(it, null, options) }
    return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
  }

  /** [uri] letta a campioni col lato corto di almeno [minShortSide] pixel: una miniatura che riempie il suo riquadro. */
  fun atLeast(resolver: ContentResolver, uri: Uri, minShortSide: Int): Bitmap? {
    val (width, height) = bounds(resolver, uri) ?: return null
    return read(resolver, uri, fluidSampleAtLeast(width, height, minShortSide))
  }

  /** [uri] letta a campioni col lato lungo di non piu' di [maxLongSide] pixel: la stampa (3508, un A4 a 300 dpi). */
  fun atMost(resolver: ContentResolver, uri: Uri, maxLongSide: Int): Bitmap? {
    val (width, height) = bounds(resolver, uri) ?: return null
    return read(resolver, uri, fluidSampleAtMost(width, height, maxLongSide))
  }

  private fun read(resolver: ContentResolver, uri: Uri, sample: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
  }
}

/**
 * Il campionamento piu' forte (una potenza di due) che lascia il lato corto di [width] × [height] ad almeno
 * [minShortSide] pixel. Uno se l'immagine e' gia' piccola.
 */
internal fun fluidSampleAtLeast(width: Int, height: Int, minShortSide: Int): Int {
  val short = minOf(width, height)
  var sample = 1
  while (minShortSide > 0 && short / (sample * 2) >= minShortSide) sample *= 2
  return sample
}

/** Il campionamento piu' debole (una potenza di due) che porta il lato lungo a non piu' di [maxLongSide] pixel. */
internal fun fluidSampleAtMost(width: Int, height: Int, maxLongSide: Int): Int {
  val long = maxOf(width, height)
  var sample = 1
  while (maxLongSide > 0 && long / sample > maxLongSide) sample *= 2
  return sample
}
