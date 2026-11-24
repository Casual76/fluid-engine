/*
 * Il colore preso dal contenuto (4.0, F8.7, idea `colore-dal-contenuto`): un'immagine (la copertina di un brano, la foto
 * aperta, un allegato) da cui ricavare un seme e due o tre colori sensati. Codice del Fluid Engine.
 *
 * Il riferimento e' Apple Music, che tinge la pagina del brano con i colori della copertina (fiducia media: il
 * comportamento si vede, l'API pubblica non c'e'). Qui c'e' solo la lettura dell'immagine; i ruoli col contrasto
 * verificato e il tema della sola pagina sono in `FluidContentTheme.kt`.
 *
 * L'estrattore viene da due codici di Alessio che c'erano gia', in sola lettura:
 *  * **Pampa Widgets** (`MediaWidgetUpdater.kt`, righe 1232–1312), l'estrattore automatico da bitmap a seme: campiona a
 *    96 x 96, un pixel ogni due, scarta l'alfa sotto 160, ricava la media (`dominant`) e il colore vivo (`vibrant`)
 *    pesato per saturazione al quadrato e luminosita'. E' portato qui con gli stessi numeri;
 *  * **Fluidify** (`ArtworkColor.kt`), la multipalette per chi deve muoversi fra piu' colori della stessa copertina e la
 *    cache per indirizzo (64 voci). Fluidify usa AndroidX Palette; qui la multipalette la fa l'estrattore stesso, per
 *    fasce di tinta, e nessuna libreria nuova entra nell'engine.
 */
package dev.antigravity.fluidengine.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * I colori letti da un'immagine: il seme per il tema della pagina e due o tre colori distinti per chi si muove fra piu'
 * tinte (un'aura, un fondo animato), tutti dalla stessa immagine.
 *
 * Si ottengono da [fluidContentColorsOf] (fuori dal thread principale) o da [rememberFluidContentColors] (che lo fa da
 * solo e tiene la cache). I ruoli leggibili in chiaro e in scuro si ricavano con `fluidContentRoles`, e il tema della
 * sola pagina con `FluidContentTheme`.
 *
 * @property seed il colore da cui il tema della pagina prende la tinta: [vibrant], o [dominant] per un'immagine
 *   [monochrome].
 * @property dominant la media dei pixel opachi: il colore «di massa» dell'immagine.
 * @property vibrant il colore vivo, pesato per saturazione e luminosita' (Pampa Widgets): quello che fa leggere un fondo
 *   come il colore della copertina invece che come un grigio fangoso.
 * @property swatches da uno a tre colori distinti per tinta, il primo e' [seed]: per un'animazione che ha bisogno di piu'
 *   note della stessa immagine (come l'aura di Fluidify).
 * @property monochrome vero per un'immagine senza un colore che conti (in bianco e nero, quasi grigia): i ruoli allora
 *   restano neutri invece di inventare una tinta.
 * @property vivid (4.0, F8.7b) il colore piu' vivo che pesa davvero: la fascia di tinta col peso per la croma al
 *   quadrato piu' alto ([FluidContentApple.SeedChromaPower]), se pesa almeno [FluidContentApple.VividMinShare] della
 *   massa colorata. Su una copertina di una tinta sola e' [vibrant]; su una a piu' tinte vicine (le foglie verdi, gialle
 *   e bruno-rosse del fiore) e' la piu' viva invece della loro media spenta. E' il seme del lato della copertina, come
 *   Apple Music. Di serie [vibrant].
 */
@Immutable
data class FluidContentColors(
  val seed: Color,
  val dominant: Color,
  val vibrant: Color,
  val swatches: List<Color>,
  val monochrome: Boolean,
  val vivid: Color = vibrant,
)

/** Le misure dell'estrattore e della transizione del tema della pagina. */
object FluidContentColorDefaults {
  /** Il lato massimo dell'immagine campionata: **96 px**, come Pampa Widgets e Fluidify (decodificano a 96). */
  const val SampleSize: Int = 96

  /** Un pixel ogni **2** su entrambi i lati, come Pampa Widgets: 48 x 48 campioni bastano per le proporzioni. */
  const val SampleStep: Int = 2

  /** Sotto quest'alfa (su 255) un pixel non conta: **160**, come Pampa Widgets (bordi sfumati, fondi trasparenti). */
  const val MinAlpha: Int = 160

  /**
   * La quota di pixel colorati sotto cui l'immagine e' «monocroma»: **4 %**. A occhio, 24 novembre 2026: un logo rosso
   * su una copertina bianca (piu' del 4 % dei pixel) tinge la pagina, un fondo grigio con qualche pixel di rumore no.
   */
  const val MonochromeShare: Float = 0.04f

  /** Quante voci tiene la cache per chiave: **64**, come la cache dei colori di Fluidify. */
  const val CacheSize: Int = 64

  /**
   * La durata del passaggio fra il tema normale e quello tinto, e fra due tinte: **450 ms**, sulla curva dei colori
   * dell'engine (`FluidMotion.color`). A occhio, 24 novembre 2026, sul cambio di brano di Apple Music (fiducia bassa: il
   * fondo sfuma in circa mezzo secondo). E' un cambio guidato dall'app, quindi sfuma; col movimento ridotto e' immediato.
   */
  const val TransitionMillis: Int = 450
}

/**
 * Legge i colori da un'immagine gia' in memoria. **Fuori dal thread principale**: ridimensiona a
 * [FluidContentColorDefaults.SampleSize] e campiona. Null se l'immagine non ha pixel abbastanza opachi (tutta
 * trasparente): li' non c'e' un colore da prendere, e il tema resta quello normale.
 *
 * Una bitmap `HARDWARE` si copia prima in memoria (i suoi pixel non si leggono).
 */
fun fluidContentColorsOf(bitmap: Bitmap): FluidContentColors? {
  val readable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
    bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
  } else {
    bitmap
  }
  val size = FluidContentColorDefaults.SampleSize
  val scale = min(1f, size.toFloat() / max(readable.width, readable.height).coerceAtLeast(1))
  val small = if (scale < 1f) {
    Bitmap.createScaledBitmap(
      readable,
      (readable.width * scale).roundToInt().coerceAtLeast(1),
      (readable.height * scale).roundToInt().coerceAtLeast(1),
      true,
    )
  } else {
    readable
  }
  val pixels = IntArray(small.width * small.height)
  small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
  if (small !== readable) small.recycle()
  if (readable !== bitmap) readable.recycle()
  return fluidContentColorsOf(pixels, small.width, small.height)
}

/** [fluidContentColorsOf] per un'immagine di Compose. */
fun fluidContentColorsOf(image: ImageBitmap): FluidContentColors? = fluidContentColorsOf(image.asAndroidBitmap())

/**
 * Il cuore dell'estrattore, sui pixel ARGB (`0xAARRGGBB`) di un'immagine gia' piccola ([width] x [height]): pura, cosi'
 * si prova sulla JVM. Null se nessun pixel e' abbastanza opaco.
 *
 * Come Pampa Widgets: un pixel ogni [FluidContentColorDefaults.SampleStep], alfa sotto
 * [FluidContentColorDefaults.MinAlpha] scartata; `dominant` e' la media; `vibrant` la media pesata per saturazione al
 * quadrato, luminosita' e vicinanza a una luminosita' di 0,62 dei pixel abbastanza saturi (≥ 0,16) e ne' neri ne'
 * bianchi (0,14–0,94), arricchita del 16 % (qui la media si fa sulla tinta piu' pesante e sulle vicine, vedi
 * [heaviestHue]); senza massa abbastanza il pixel migliore (arricchito del 12 %), e senza
 * nemmeno quello la media arricchita del 45 %. In piu', per fasce di tinta di 30°, i colori distinti per [swatches].
 */
fun fluidContentColorsOf(pixels: IntArray, width: Int, height: Int): FluidContentColors? {
  if (width <= 0 || height <= 0 || pixels.size < width * height) return null
  val step = FluidContentColorDefaults.SampleStep
  var rSum = 0L
  var gSum = 0L
  var bSum = 0L
  var count = 0L
  var colored = 0L

  var accentWeight = 0.0

  var bestScore = -1f
  var bestPixel = 0
  var bestSat = 0f
  var bestBrightness = 0f

  // Fasce di tinta per i colori distinti: peso e somme pesate di ogni fascia.
  val bandWeight = DoubleArray(HueBands)
  val bandR = DoubleArray(HueBands)
  val bandG = DoubleArray(HueBands)
  val bandB = DoubleArray(HueBands)

  var y = 0
  while (y < height) {
    var x = 0
    while (x < width) {
      val c = pixels[y * width + x]
      x += step
      if ((c ushr 24) < FluidContentColorDefaults.MinAlpha) continue
      val r = (c shr 16) and 0xFF
      val g = (c shr 8) and 0xFF
      val b = c and 0xFF
      rSum += r; gSum += g; bSum += b; count++
      val maxC = max(r, max(g, b))
      val minC = min(r, min(g, b))
      val brightness = maxC / 255f
      val sat = if (maxC == 0) 0f else (maxC - minC) / maxC.toFloat()
      val balance = 1f - (abs(brightness - 0.62f) / 0.62f).coerceIn(0f, 1f)
      val score = sat * (0.45f + 0.55f * brightness) * (0.65f + 0.35f * balance)
      if (score > bestScore) {
        bestScore = score
        bestPixel = c
        bestSat = sat
        bestBrightness = brightness
      }
      if (sat >= 0.16f && brightness in 0.14f..0.94f) {
        colored++
        val weight = (sat * sat) * (0.50f + 0.50f * brightness) * (0.70f + 0.30f * balance)
        accentWeight += weight
        val band = (hueOf(r, g, b, maxC, minC) / (360f / HueBands)).toInt().coerceIn(0, HueBands - 1)
        bandWeight[band] += weight.toDouble()
        bandR[band] += r * weight.toDouble()
        bandG[band] += g * weight.toDouble()
        bandB[band] += b * weight.toDouble()
      }
    }
    y += step
  }
  if (count == 0L) return null

  val dominant = rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
  val vibrant = when {
    accentWeight >= 1.0 -> heaviestHue(bandWeight, bandR, bandG, bandB).enrich(0.16f)
    bestSat >= 0.14f && bestBrightness >= 0.12f ->
      rgb((bestPixel shr 16) and 0xFF, (bestPixel shr 8) and 0xFF, bestPixel and 0xFF).enrich(0.12f)
    else -> dominant.enrich(0.45f)
  }
  val monochrome = colored.toFloat() / count < FluidContentColorDefaults.MonochromeShare
  val seed = if (monochrome) dominant else vibrant
  val vivid = when {
    monochrome -> seed
    accentWeight >= 1.0 -> vividHue(vibrant, bandWeight, bandR, bandG, bandB) ?: vibrant
    else -> vibrant
  }
  return FluidContentColors(
    seed = seed,
    dominant = dominant,
    vibrant = vibrant,
    swatches = if (monochrome) listOf(seed) else distinctSwatches(seed, bandWeight, bandR, bandG, bandB, accentWeight),
    monochrome = monochrome,
    vivid = vivid,
  )
}

/**
 * La fascia di tinta piu' viva che pesa davvero (F8.7b): quella col peso per la croma OKLCH della sua media alla
 * [FluidContentApple.SeedChromaPower] piu' alto, fra quelle che pesano almeno [FluidContentApple.VividMinShare]. Null
 * (cioe' [vibrant]) se vince la fascia al centro della finestra di [heaviestHue]: su una copertina di una tinta sola
 * il seme non cambia di un livello.
 */
private fun vividHue(vibrant: Color, weight: DoubleArray, r: DoubleArray, g: DoubleArray, b: DoubleArray): Color? {
  val n = weight.size
  val total = weight.sum()
  if (total <= 0.0) return null
  var center = 0
  var centerWeight = -1.0
  for (i in 0 until n) {
    val w = weight[(i + n - 1) % n] + weight[i] + weight[(i + 1) % n]
    if (w > centerWeight) {
      centerWeight = w
      center = i
    }
  }
  var best = -1
  var bestScore = -1.0
  for (i in 0 until n) {
    val w = weight[i]
    if (w <= 0.0 || w < total * FluidContentApple.VividMinShare) continue
    val c = Oklch.of(rgb((r[i] / w).toInt(), (g[i] / w).toInt(), (b[i] / w).toInt())).c
    val score = w * Math.pow(c.toDouble(), FluidContentApple.SeedChromaPower.toDouble())
    if (score > bestScore) {
      bestScore = score
      best = i
    }
  }
  if (best < 0 || best == center) return null
  val w = weight[best]
  val candidate = rgb((r[best] / w).toInt(), (g[best] / w).toInt(), (b[best] / w).toInt()).enrich(0.16f)
  // Prende il posto del colore vivo solo se e' davvero un altro colore, e piu' vivo: altrimenti il seme resta quello di
  // prima, al livello (una copertina di una tinta sola, la cui finestra piu' pesante e' spostata di una fascia).
  val was = Oklch.of(vibrant)
  val now = Oklch.of(candidate)
  val turn = Math.toDegrees(kotlin.math.abs(now.h - was.h).toDouble()).let { if (it > 180.0) 360.0 - it else it }
  if (turn < FluidContentApple.VividHueGap || now.c < was.c * FluidContentApple.VividChromaGain) return null
  return candidate
}

/**
 * La media pesata della fascia di tinta piu' pesante insieme alle sue due vicine (la finestra di 90° con piu' massa).
 * Pampa Widgets faceva la media su tutti i pixel colorati: con una copertina di un colore solo e' lo stesso, ma con
 * tre tinte diverse (blu, arancio, verde) la media e' un grigio fangoso che non e' nessuna delle tre.
 */
private fun heaviestHue(weight: DoubleArray, r: DoubleArray, g: DoubleArray, b: DoubleArray): Color {
  val n = weight.size
  var best = 0
  var bestWeight = -1.0
  for (i in 0 until n) {
    val w = weight[(i + n - 1) % n] + weight[i] + weight[(i + 1) % n]
    if (w > bestWeight) {
      bestWeight = w
      best = i
    }
  }
  var w = 0.0
  var rr = 0.0
  var gg = 0.0
  var bb = 0.0
  for (k in -1..1) {
    val i = (best + k + n) % n
    w += weight[i]
    rr += r[i]
    gg += g[i]
    bb += b[i]
  }
  return rgb((rr / w).toInt(), (gg / w).toInt(), (bb / w).toInt())
}

/** Le fasce di tinta dei colori distinti: **12**, da 30° l'una (la ruota dei colori in dodici, come la griglia di Apple). */
private const val HueBands = 12

/** Una fascia conta come colore a se' sopra questa quota della massa colorata: **8 %**. A occhio, 24 novembre 2026. */
private const val SwatchShare = 0.08

/** Due colori sono «lo stesso» sotto questa distanza di tinta: **40°**. A occhio, 24 novembre 2026. */
private const val SwatchHueGap = 40f

/**
 * Il seme e poi le fasce piu' pesanti lontane almeno [SwatchHueGap] dai colori gia' presi, fino a tre: la multipalette di
 * Fluidify («i vivi davanti, i tenui dietro»), fatta senza Palette.
 */
private fun distinctSwatches(
  seed: Color,
  weight: DoubleArray,
  r: DoubleArray,
  g: DoubleArray,
  b: DoubleArray,
  total: Double,
): List<Color> {
  val picked = mutableListOf(seed)
  if (total <= 0.0) return picked
  val order = weight.indices.sortedByDescending { weight[it] }
  for (band in order) {
    if (picked.size >= 3) break
    if (weight[band] < total * SwatchShare) break
    val w = weight[band]
    val candidate = rgb((r[band] / w).toInt(), (g[band] / w).toInt(), (b[band] / w).toInt()).enrich(0.12f)
    if (picked.all { hueDistance(it, candidate) >= SwatchHueGap }) picked += candidate
  }
  return picked
}

/** La tinta in gradi (0–360) di un colore, con i suoi canali gia' estremi. */
private fun hueOf(r: Int, g: Int, b: Int, maxC: Int, minC: Int): Float {
  val d = (maxC - minC).toFloat()
  if (d <= 0f) return 0f
  val h = when (maxC) {
    r -> ((g - b) / d).let { if (it < 0f) it + 6f else it }
    g -> (b - r) / d + 2f
    else -> (r - g) / d + 4f
  }
  return (h * 60f).let { if (it >= 360f) it - 360f else it }
}

private fun hueOf(color: Color): Float {
  val r = (color.red * 255).roundToInt()
  val g = (color.green * 255).roundToInt()
  val b = (color.blue * 255).roundToInt()
  return hueOf(r, g, b, max(r, max(g, b)), min(r, min(g, b)))
}

private fun hueDistance(a: Color, b: Color): Float {
  val d = abs(hueOf(a) - hueOf(b))
  return min(d, 360f - d)
}

private fun rgb(r: Int, g: Int, b: Int): Color = Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

/** Allontana i canali dal loro grigio di [amount]: la stessa spinta di Pampa Widgets (`enrich`). */
private fun Color.enrich(amount: Float): Color {
  val r = red * 255f
  val g = green * 255f
  val b = blue * 255f
  val grey = ((r + g + b) / 3f).toInt().toFloat()
  fun channel(c: Float) = (grey + (c - grey) * (1f + amount)).toInt().coerceIn(0, 255)
  return Color(channel(r), channel(g), channel(b))
}

/**
 * La cache dei colori letti, per chiave (l'indirizzo della copertina, l'id della foto): la stessa copertina la chiedono il
 * mini player, la pagina del brano e il widget insieme, e leggerla tre volte a ogni cambio di brano e' uno spreco
 * (Fluidify). Tiene [FluidContentColorDefaults.CacheSize] voci, l'ultima usata davanti.
 */
object FluidContentColorCache {
  private val entries = object : LinkedHashMap<Any, FluidContentColors>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, FluidContentColors>?): Boolean =
      size > FluidContentColorDefaults.CacheSize
  }

  /** I colori gia' letti per [key], o null. */
  @Synchronized
  operator fun get(key: Any): FluidContentColors? = entries[key]

  /** Conserva i colori letti per [key]. */
  @Synchronized
  operator fun set(key: Any, colors: FluidContentColors) {
    entries[key] = colors
  }

  /** Dimentica tutto (per esempio quando l'app svuota le sue copertine). */
  @Synchronized
  fun clear() = entries.clear()

  /** Quante voci tiene adesso. */
  val size: Int
    @Synchronized get() = entries.size
}

/**
 * I colori dell'immagine che [key] identifica, letti una volta sola: dalla cache se ci sono, altrimenti [load] e
 * [fluidContentColorsOf] su `Dispatchers.Default`, mai sul thread principale.
 *
 * Mentre i colori di una chiave nuova si leggono, restano quelli di prima (come Apple Music, che non torna al bianco fra
 * un brano e l'altro): il tema passa dall'una all'altra tinta con una sola transizione. Con [key] nullo, o se
 * l'immagine non c'e' o non ha colori, lo stato e' null e il tema torna quello normale.
 *
 * L'app decide quale immagine guardare ([load] la decodifica, piccola: 96 px bastano); l'engine la legge e la ricorda.
 */
@Composable
fun rememberFluidContentColors(key: Any?, load: suspend () -> Bitmap?): State<FluidContentColors?> {
  val state = remember { mutableStateOf(key?.let { FluidContentColorCache[it] }) }
  LaunchedEffect(key) {
    if (key == null) {
      state.value = null
      return@LaunchedEffect
    }
    FluidContentColorCache[key]?.let {
      state.value = it
      return@LaunchedEffect
    }
    val colors = try {
      withContext(Dispatchers.Default) { load()?.let { fluidContentColorsOf(it) } }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
    if (colors != null) FluidContentColorCache[key] = colors
    state.value = colors
  }
  return state
}

/** [rememberFluidContentColors] per un'immagine gia' in memoria; la chiave di serie e' l'immagine stessa. */
@Composable
fun rememberFluidContentColors(image: ImageBitmap?, key: Any? = image): State<FluidContentColors?> =
  rememberFluidContentColors(key = if (image == null) null else key) { image?.asAndroidBitmap() }
