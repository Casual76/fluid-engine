package dev.antigravity.fluidengine.media

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.antigravity.fluidengine.ui.fluid.FluidImagePhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Le miniature del selettore (F8.14): una cache in memoria **col tetto in byte** (un ottavo della memoria dell'app, al piu'
 * 32 MB; le meno usate escono per prime) e al piu' [Parallelism] letture insieme. La chiave e' l'elemento e il lato
 * arrotondato a [Step] px: due celle uguali fanno un lavoro solo, e tornando su una cella la miniatura e' gia' li' (non si
 * anima di nuovo).
 *
 * La griglia non tiene immagini sue: una cella che esce dalla vista lascia la miniatura alla cache, che la libera quando
 * serve posto.
 */
object FluidMediaThumbnails {
  /** Il passo del lato: 64 px. */
  const val Step: Int = 64

  /** Quante letture insieme: 4 (il MediaStore e i provider delle altre app non amano la folla). */
  const val Parallelism: Int = 4

  private val maxBytes: Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(32L * 1024 * 1024).toInt().coerceAtLeast(4 * 1024 * 1024)

  private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4).coerceAtLeast(1)
  }

  private val gate = Semaphore(Parallelism)

  private fun key(item: FluidMediaItem, sizePx: Int): String = "${item.key}@${bucket(sizePx)}"

  /** Il lato arrotondato in su al passo. */
  fun bucket(sizePx: Int): Int = ((sizePx.coerceAtLeast(1) + Step - 1) / Step) * Step

  /** La miniatura gia' in cache, senza lavorare; null se non c'e'. */
  fun cached(item: FluidMediaItem, sizePx: Int): ImageBitmap? = cache.get(key(item, sizePx))

  /** La miniatura di [item] da [source]: dalla cache, o letta (fuori dal filo principale, lo fa la sorgente). */
  suspend fun load(source: FluidMediaSource, item: FluidMediaItem, sizePx: Int): ImageBitmap? {
    val k = key(item, sizePx)
    cache.get(k)?.let { return it }
    return gate.withPermit {
      cache.get(k) ?: try {
        source.thumbnail(item, bucket(sizePx))?.asImageBitmap()?.also { cache.put(k, it) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }
    }
  }

  /** Toglie le miniature di un elemento (cambiato o tolto). */
  fun evict(item: FluidMediaItem) {
    val prefix = "${item.key}@"
    cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
  }

  /** Svuota la cache. */
  fun clear() {
    cache.evictAll()
  }
}

/**
 * La miniatura di [item] come fase di un'immagine che arriva (F8.1, [FluidImagePhase]): `Success` subito se e' gia' in
 * cache (niente arrivo animato), altrimenti `Loading` e poi `Success` o `Failure`. La lettura si annulla se la cella esce.
 */
@Composable
fun rememberFluidMediaThumbnail(source: FluidMediaSource, item: FluidMediaItem, sizePx: Int): FluidImagePhase {
  var phase by remember(source, item.key, FluidMediaThumbnails.bucket(sizePx)) {
    mutableStateOf<FluidImagePhase>(
      FluidMediaThumbnails.cached(item, sizePx)?.let { FluidImagePhase.Success(it) } ?: FluidImagePhase.Loading,
    )
  }
  if (phase is FluidImagePhase.Loading) {
    LaunchedEffect(source, item.key, FluidMediaThumbnails.bucket(sizePx)) {
      phase = FluidMediaThumbnails.load(source, item, sizePx)?.let { FluidImagePhase.Success(it) } ?: FluidImagePhase.Failure()
    }
  }
  return phase
}
