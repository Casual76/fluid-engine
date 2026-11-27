/*
 * Il selettore di colore (4.0, F8.7, idea `selettore-colore`). Codice del Fluid Engine.
 *
 * Il riferimento e' `ColorPicker` di SwiftUI e `UIColorPickerViewController` di UIKit (iOS 14): un pozzetto tondo con
 * l'anello dell'arcobaleno che mostra il colore e, toccato, apre un foglio con tre modi (Griglia, Spettro, Cursori col
 * codice esadecimale), l'opacita', i colori salvati e il contagocce. Qui il foglio e' il foglio di vetro dell'engine
 * (`FluidGlassSheet`), con le sue misure e il suo viaggio: nasce dal pozzetto e ci torna. La versione corta, per una riga
 * delle impostazioni, e' una fila di campioni ([FluidColorSwatches]) coi pallini di `FluidAccentPicker` ([FluidColorDot])
 * e il pozzetto in fondo per un colore su misura.
 *
 * «Dito nitido, app sfocata» (Alessio, 24 novembre 2026): tutto quello che qui si muove lo muove il dito (la griglia, lo
 * spettro, i cursori, la lente del contagocce), quindi niente si sfoca. Il colore resta in sRGB anche sugli schermi a
 * colori estesi, come chiede la scheda dell'idea. Il contagocce vede solo la finestra dell'app (`PixelCopy`): per tutto lo
 * schermo servirebbe la registrazione dello schermo, e non vale la richiesta.
 */
package dev.antigravity.fluidengine.ui.fluid

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

// ================================================================================================================
// Il modello: modi, recenti, parole, misure
// ================================================================================================================

/** I tre modi del foglio, come i segmenti di `UIColorPickerViewController`: Griglia, Spettro, Cursori. */
enum class FluidColorPickerMode { Grid, Spectrum, Sliders }

/**
 * I colori recenti del selettore: l'ultimo scelto davanti, senza doppioni, al massimo [capacity]. Il foglio ci aggiunge
 * il colore scelto quando si chiude (se e' cambiato) e quello che il «+» salva, come i colori salvati di
 * `UIColorPickerViewController`.
 *
 * **Li conserva l'app**: [onChange] riceve la lista nuova a ogni cambio (per uno store, per `engine-storage`), e
 * [rememberFluidRecentColors] la riparte da `initial`. `engine-ui` non dipende da uno store.
 */
@Stable
class FluidRecentColors(
  initial: List<Color> = emptyList(),
  val capacity: Int = FluidColorPickerDefaults.RecentCapacity,
  onChange: (List<Color>) -> Unit = {},
) {
  internal var onChange: (List<Color>) -> Unit = onChange

  /** I colori, il piu' recente per primo. */
  var colors: List<Color> by mutableStateOf(normalize(initial))
    private set

  /** Mette [color] davanti (togliendolo da dov'era) e lascia cadere il piu' vecchio oltre [capacity]. */
  fun add(color: Color) {
    val next = normalize(listOf(color) + colors)
    if (next == colors) return
    colors = next
    onChange(next)
  }

  /** Dimentica tutto. */
  fun clear() {
    if (colors.isEmpty()) return
    colors = emptyList()
    onChange(emptyList())
  }

  private fun normalize(list: List<Color>): List<Color> =
    list.distinctBy { it.toArgb() }.take(capacity.coerceAtLeast(1))
}

/**
 * I colori recenti, conservati attraverso i cambi di configurazione. [initial] e' quello che l'app aveva salvato,
 * [onChange] dove salvarli di nuovo.
 */
@Composable
fun rememberFluidRecentColors(
  initial: List<Color> = emptyList(),
  capacity: Int = FluidColorPickerDefaults.RecentCapacity,
  onChange: (List<Color>) -> Unit = {},
): FluidRecentColors {
  val latest by rememberUpdatedState(onChange)
  val recents = rememberSaveable(
    saver = Saver<FluidRecentColors, List<Int>>(
      save = { r -> r.colors.map { it.toArgb() } },
      restore = { saved -> FluidRecentColors(saved.map { Color(it) }, capacity) },
    ),
  ) { FluidRecentColors(initial, capacity) }
  recents.onChange = { latest(it) }
  return recents
}

/**
 * Le parole del selettore, in italiano di serie. [colorName] dice un colore a parole a chi usa TalkBack: un colore da
 * solo non e' un'etichetta.
 */
data class FluidColorPickerStrings(
  val title: String = "Colori",
  val grid: String = "Griglia",
  val spectrum: String = "Spettro",
  val sliders: String = "Cursori",
  val red: String = "Rosso",
  val green: String = "Verde",
  val blue: String = "Blu",
  val hex: String = "Codice esadecimale",
  val opacity: String = "Opacità",
  val recents: String = "Recenti",
  val addToRecents: String = "Salva il colore",
  val eyedropper: String = "Contagocce",
  val eyedropperHint: String = "Trascina la lente sul colore e solleva il dito",
  val close: String = "Chiudi",
  val current: String = "Colore scelto",
  val custom: String = "Altro colore",
  val colorName: (Color) -> String = ::fluidColorName,
) {
  /** Il nome di un modo, per il segmento. */
  fun modeLabel(mode: FluidColorPickerMode): String = when (mode) {
    FluidColorPickerMode.Grid -> grid
    FluidColorPickerMode.Spectrum -> spectrum
    FluidColorPickerMode.Sliders -> sliders
  }
}

/**
 * Le misure del selettore. Dove non c'e' una misura di Apple a cui appoggiarsi il KDoc dice «a occhio» con la data: le
 * schermate di riferimento sono quelle di `UIColorPickerViewController` su iPhone (iOS 17–26), fiducia media sulla
 * struttura e bassa sui punti.
 */
object FluidColorPickerDefaults {
  /** Il diametro del pozzetto: **28 dp**, quanto `UIColorWell` in una riga di Impostazioni. A occhio, 24 novembre 2026. */
  val WellSize: Dp = 28.dp

  /** Lo spessore dell'anello dell'arcobaleno del pozzetto: **3 dp** (a occhio, 24 novembre 2026, su `UIColorWell`). */
  val WellRing: Dp = 3.dp

  /** Il vuoto fra l'anello e il colore: **2 dp** (a occhio, 24 novembre 2026). */
  val WellGap: Dp = 2.dp

  /** Il bersaglio del pozzetto: **44 dp**, il minimo di Apple per un tocco. */
  val WellTarget: Dp = 44.dp

  /**
   * La griglia: **12 colonne per 10 righe**, la prima di grigi dal bianco al nero e nove di tinte dallo scuro al chiaro,
   * come la griglia di `UIColorPickerViewController` (fiducia media: stessa struttura; i valori sono quelli della
   * griglia di iOS ripresi dalle schermate, fiducia bassa sul singolo colore).
   */
  const val GridColumns: Int = 12

  /** Le righe della griglia: vedi [GridColumns]. */
  const val GridRows: Int = 10

  /**
   * Il raggio degli angoli della griglia e dello spettro: **16 dp**. Misurato sulla schermata di iOS 26 a @3x (la
   * griglia del selettore su iPhone, `riferimentipple-colorpicker-griglia-iphone-ios26.webp` di F8.7): ~15 pt, con
   * le caselle da 29,7 pt. Fiducia media (una schermata), 24 novembre 2026.
   */
  val PanelCorner: Dp = 16.dp

  /** L'altezza della pista dei cursori: **30 dp**, una capsula col colore dentro (a occhio, 24 novembre 2026, su iOS). */
  val SliderHeight: Dp = 30.dp

  /** L'anello bianco del pomello dei cursori e del segno dello spettro: **3 dp** (a occhio, 24 novembre 2026). */
  val ThumbRing: Dp = 3.dp

  /** Il diametro del segno sullo spettro: **28 dp** (a occhio, 24 novembre 2026, sul segno di iOS). */
  val SpectrumMarker: Dp = 28.dp

  /**
   * Il quadrato del colore scelto in fondo al foglio: **74 dp**, angoli `FluidRadius.Card` (18). Misurato sulla stessa
   * schermata di iOS 26: 74 × 75 pt, angoli ~17 pt (fiducia media, 24 novembre 2026).
   */
  val CurrentSwatch: Dp = 74.dp

  /**
   * Il diametro dei colori recenti: **28 dp**, i pallini di [FluidColorDot] (bersaglio 48 dp). Misurato su iOS 26: 28 pt,
   * a 46 pt di passo, in righe che vanno a capo col «+» in fondo (fiducia media, 24 novembre 2026).
   */
  val RecentDot: Dp = 28.dp

  /**
   * La casella del valore accanto ai cursori («255», «100 %»): **64 × 34 dp**, a capsula, chiara sul foglio. iOS 26
   * mette il valore in una capsula bianca accanto alla pista (fiducia media sulla forma, a occhio sulla misura).
   */
  val ValueBoxWidth: Dp = 64.dp

  /** L'altezza della casella del valore: vedi [ValueBoxWidth]. */
  val ValueBoxHeight: Dp = 34.dp

  /** Quanti colori recenti tiene la lista di serie: **10** (a occhio, 24 novembre 2026: una fila che scorre poco). */
  const val RecentCapacity: Int = 10

  /** Il lato dei quadretti della scacchiera dietro i colori trasparenti: **6 dp** (a occhio, 24 novembre 2026). */
  val CheckerCell: Dp = 6.dp

  /**
   * L'altezza del foglio a riposo: **640 dp**, quanto serve ai tre modi con l'opacita' e i recenti; sotto quella altezza
   * il foglio e' vetro staccato dai bordi, sopra (il secondo detent, `Large`) diventa pagina come ogni foglio di vetro.
   * A occhio, 24 novembre 2026: il selettore di iOS sta quasi a tutta altezza su iPhone.
   */
  val SheetHeight: Dp = 640.dp

  /** La larghezza massima del contenuto del foglio, su un tablet: **420 dp** (a occhio, 24 novembre 2026). */
  val MaxContentWidth: Dp = 420.dp

  /**
   * Il diametro della lente del contagocce: **120 dp**. Sulla lente di iPadOS delle HIG («Color wells», l'immagine della
   * lente su un iPad da 12,9") ~122 pt (fiducia bassa: la scala dell'immagine e' stimata), 24 novembre 2026.
   */
  val LoupeSize: Dp = 120.dp

  /** Quanti pixel per lato mostra la lente: **11**, dispari perche' quello scelto stia nel mezzo. */
  const val LoupePixels: Int = 11

  /** Lo spessore dell'anello del colore attorno alla lente: **12 dp** (sulla stessa immagine ~14 pt; fiducia bassa). */
  val LoupeRing: Dp = 12.dp

  /**
   * Il velo sull'app mentre si sceglie: nero al **28 %**, con una finestra chiara attorno alla lente larga **3,4** raggi
   * e alta tre quarti, come la lente di iPadOS (264 × 200 px attorno a una lente di 156: a occhio sulla stessa immagine,
   * 24 novembre 2026).
   */
  const val LoupeVeilAlpha: Float = 0.28f

  /** Vedi [LoupeVeilAlpha]: la finestra chiara, in raggi della lente. */
  const val LoupeWindow: Float = 3.4f

  /**
   * Quanto aspetta il contagocce prima di fotografare la finestra: **420 ms**, il tempo in cui il foglio esce del tutto
   * (la molla d'uscita dell'host e' ferma verso 400 ms). Col movimento ridotto il foglio sparisce subito e bastano 60 ms.
   */
  const val EyedropperCaptureDelayMillis: Long = 420L

  /** Vedi [EyedropperCaptureDelayMillis]: col movimento ridotto. */
  const val EyedropperCaptureDelayReducedMillis: Long = 60L
}

/** I tag dei test e delle riprese. */
object FluidColorPickerTags {
  const val Well = "fluid-color:pozzetto"
  const val Picker = "fluid-color:selettore"
  const val Modes = "fluid-color:modi"
  const val Grid = "fluid-color:griglia"
  const val Spectrum = "fluid-color:spettro"
  const val Hex = "fluid-color:esadecimale"
  const val Opacity = "fluid-color:opacita"
  const val Current = "fluid-color:scelto"
  const val Recents = "fluid-color:recenti"
  const val AddRecent = "fluid-color:salva"
  const val Eyedropper = "fluid-color:contagocce"
  const val Loupe = "fluid-color:lente"
  const val Close = "fluid-color:chiudi"

  fun slider(channel: String): String = "fluid-color:cursore-$channel"
}

// ================================================================================================================
// Funzioni pure: esadecimale, nomi, HSV, griglia
// ================================================================================================================

/** Il codice esadecimale di [color] in sRGB, `#RRGGBB` (o `#RRGGBBAA` con [withAlpha]), in maiuscolo. */
fun fluidColorHex(color: Color, withAlpha: Boolean = false): String {
  val argb = color.toArgb()
  val rgb = String.format(Locale.ROOT, "#%02X%02X%02X", (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
  return if (withAlpha) rgb + String.format(Locale.ROOT, "%02X", (argb ushr 24) and 0xFF) else rgb
}

/**
 * Il colore di un codice esadecimale: `RGB`, `RRGGBB` o `RRGGBBAA`, col `#` o senza, maiuscole o minuscole, spazi ai
 * lati. Null se il testo non e' un colore.
 */
fun fluidParseHexColor(text: String): Color? {
  val digits = text.trim().removePrefix("#")
  if (digits.isEmpty() || digits.any { Character.digit(it, 16) < 0 }) return null
  val full = when (digits.length) {
    3 -> digits.map { "$it$it" }.joinToString("") + "FF"
    6 -> digits + "FF"
    8 -> digits
    else -> return null
  }
  val v = full.toLong(16)
  val r = ((v shr 24) and 0xFF).toInt()
  val g = ((v shr 16) and 0xFF).toInt()
  val b = ((v shr 8) and 0xFF).toInt()
  val a = (v and 0xFF).toInt()
  return Color(r, g, b, a)
}

/**
 * Un colore detto a parole, in italiano: «rosso», «blu scuro», «verde chiaro», «grigio», «bianco», con l'opacita' se
 * non e' piena. Per TalkBack: e' il nome che il selettore da' a ogni casella, al pozzetto e ai recenti.
 */
fun fluidColorName(color: Color): String {
  if (color.alpha < 0.05f) return "trasparente"
  val hsv = colorToHsv(color)
  val h = hsv[0]
  val s = hsv[1]
  val v = hsv[2]
  val base = when {
    v < 0.12f -> "nero"
    s < 0.12f && v > 0.94f -> "bianco"
    s < 0.12f -> when {
      v > 0.72f -> "grigio chiaro"
      v < 0.38f -> "grigio scuro"
      else -> "grigio"
    }
    else -> {
      val hue = when {
        h < 15f || h >= 345f -> "rosso"
        h < 40f -> if (v < 0.6f || (s < 0.5f && v < 0.8f)) "marrone" else "arancione"
        h < 68f -> if (v < 0.5f) "verde oliva" else "giallo"
        h < 160f -> "verde"
        h < 185f -> "verde acqua"
        h < 205f -> "azzurro"
        h < 255f -> "blu"
        h < 290f -> "viola"
        h < 330f -> "magenta"
        else -> "rosa"
      }
      when {
        hue == "marrone" || hue == "verde oliva" -> hue
        v < 0.45f -> "$hue scuro"
        s < 0.45f && v > 0.8f -> "$hue chiaro"
        else -> hue
      }
    }
  }
  return if (color.alpha < 0.995f) "$base, opacità ${(color.alpha * 100).roundToInt()} %" else base
}

/** Tinta (0–360), saturazione e valore (0–1) di un colore, come `android.graphics.Color.colorToHSV`, ma pura. */
internal fun colorToHsv(color: Color): FloatArray {
  val r = color.red
  val g = color.green
  val b = color.blue
  val maxC = max(r, max(g, b))
  val minC = min(r, min(g, b))
  val d = maxC - minC
  val h = when {
    d <= 0f -> 0f
    maxC == r -> 60f * (((g - b) / d).let { if (it < 0f) it + 6f else it })
    maxC == g -> 60f * ((b - r) / d + 2f)
    else -> 60f * ((r - g) / d + 4f)
  }
  return floatArrayOf(if (h >= 360f) h - 360f else h, if (maxC <= 0f) 0f else d / maxC, maxC)
}

/** Il colore di tinta [h] (0–360), saturazione [s] e valore [v], con l'alfa [alpha]. */
internal fun hsvColor(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
  val hh = ((h % 360f) + 360f) % 360f / 60f
  val c = v * s
  val x = c * (1f - abs(hh % 2f - 1f))
  val m = v - c
  val (r, g, b) = when (hh.toInt()) {
    0 -> Triple(c, x, 0f)
    1 -> Triple(x, c, 0f)
    2 -> Triple(0f, c, x)
    3 -> Triple(0f, x, c)
    4 -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
  }
  return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
}

/**
 * Il colore dello spettro nel punto ([x], [y]), entrambi fra 0 e 1: la tinta corre da sinistra a destra, dall'alto a
 * meta' il colore sale dal bianco al pieno, da meta' in giu' scende dal pieno al nero, come lo spettro di iOS.
 * E' anche quello che il gradiente disegna, pixel per pixel.
 */
internal fun spectrumColor(x: Float, y: Float): Color {
  val hue = x.coerceIn(0f, 1f) * 360f
  val yy = y.coerceIn(0f, 1f)
  return if (yy <= 0.5f) hsvColor(hue, yy / 0.5f, 1f) else hsvColor(hue, 1f, 1f - (yy - 0.5f) / 0.5f)
}

/** Dove sta [color] sullo spettro (x e y fra 0 e 1), con la tinta [hue] se il colore non ne ha una sua (un grigio). */
internal fun spectrumPosition(color: Color, hue: Float): Offset {
  val hsv = colorToHsv(color)
  val h = if (hsv[1] < 0.02f || hsv[2] < 0.02f) hue else hsv[0]
  val y = if (hsv[2] >= 0.999f) hsv[1] * 0.5f else 0.5f + (1f - hsv[2]) * 0.5f
  return Offset(h / 360f, y.coerceIn(0f, 1f))
}

private fun hex(v: Long): Color = Color(0xFF000000 or v)

private val GridCells = longArrayOf(
  0xFFFFFF, 0xEBEBEB, 0xD6D6D6, 0xC2C2C2, 0xADADAD, 0x999999, 0x858585, 0x707070, 0x5C5C5C, 0x474747, 0x333333, 0x000000,
  0x00374B, 0x011C57, 0x11063C, 0x2F063D, 0x3D071C, 0x5C0601, 0x591C01, 0x583300, 0x563E00, 0x666101, 0x4F5604, 0x273E10,
  0x004E65, 0x002F7B, 0x1A0A52, 0x450D59, 0x56102A, 0x821100, 0x7B2900, 0x7A4B00, 0x775801, 0x8E8602, 0x70760B, 0x39571B,
  0x026E90, 0x0142A9, 0x2D0977, 0x61187C, 0x781A3C, 0xB61A01, 0xAD3F00, 0xA96801, 0xA77B01, 0xC4BC00, 0x9AA50D, 0x4E7926,
  0x008CB3, 0x0056D6, 0x371A94, 0x79219E, 0x9A2350, 0xE22400, 0xDA5100, 0xD28301, 0xD19D02, 0xF5EC00, 0xC3D217, 0x659D33,
  0x00A1D8, 0x0062FE, 0x4D21B2, 0x992ABD, 0xB92D5C, 0xFF4016, 0xFF6A00, 0xFFAB01, 0xFDC600, 0xFEFA41, 0xDAEC36, 0x77BB40,
  0x00C7FC, 0x3B87FF, 0x5E30EA, 0xBD39F3, 0xE73B7A, 0xFF6251, 0xFF8548, 0xFEB440, 0xFECC3F, 0xFFF76B, 0xE4EF65, 0x97D25F,
  0x52D7FC, 0x74A7FF, 0x864EFE, 0xD258FE, 0xEF719E, 0xFF8D81, 0xFEA57D, 0xFFC879, 0xFFD977, 0xFFF995, 0xEBF18F, 0xB1DE8D,
  0x93E3FC, 0xA7C6FF, 0xB18CFF, 0xE392FD, 0xF4A4BF, 0xFFB5AE, 0xFFC6AC, 0xFED9A8, 0xFFE4A9, 0xFEFBB8, 0xF2F8B8, 0xCDE7B5,
  0xCBF0FF, 0xD2E2FF, 0xD9C9FE, 0xEECAFF, 0xF9D3E0, 0xFFDBD8, 0xFEE2D5, 0xFFECD4, 0xFFF2D4, 0xFEFCDD, 0xF7FADB, 0xDFEED4,
)

/**
 * I 120 colori della griglia, riga per riga: la prima di grigi dal bianco al nero, poi nove righe dallo scuro al chiaro,
 * come la griglia di `UIColorPickerViewController`. I valori sono quelli del selettore di sistema, campionati al centro
 * di ogni casella della schermata di iOS 26 su iPhone (F8.7, `riferimenti/apple-colorpicker-griglia-iphone-ios26.webp`;
 * fiducia media: un'immagine compressa, a meno di un livello o due per canale), 24 novembre 2026.
 */
val FluidColorGrid: List<Color> by lazy { GridCells.map { hex(it) } }

/** La casella della griglia con lo stesso colore (a meno di un livello per canale), o -1. */
internal fun gridIndexOf(color: Color): Int {
  val argb = color.copy(alpha = 1f).toArgb()
  return FluidColorGrid.indexOfFirst { cell ->
    val c = cell.toArgb()
    abs(((c shr 16) and 0xFF) - ((argb shr 16) and 0xFF)) <= 1 &&
      abs(((c shr 8) and 0xFF) - ((argb shr 8) and 0xFF)) <= 1 &&
      abs((c and 0xFF) - (argb and 0xFF)) <= 1
  }
}

// ================================================================================================================
// Il pozzetto e la fila corta
// ================================================================================================================

/**
 * Il pozzetto: un cerchio col colore scelto dentro l'anello dell'arcobaleno, come `ColorPicker` di SwiftUI e
 * `UIColorWell`. Toccato apre [FluidColorPickerSheet], che **nasce dal pozzetto** e ci rientra (il viaggio del foglio di
 * vetro dell'engine). Bersaglio di 44 dp; per TalkBack dice [label] e il nome del colore.
 *
 * @param color il colore di adesso, in sRGB, con la sua alfa.
 * @param onColorChange il colore nuovo, a ogni cambio (anche mentre il dito scorre sullo spettro).
 * @param label cosa colora («Sfondo», «Colore della materia»): il nome del pozzetto per chi non lo vede.
 * @param supportsOpacity offrire il cursore dell'opacita' (come `supportsOpacity` di SwiftUI).
 * @param recents i colori recenti, condivisi fra i pozzetti di una pagina se l'app passa lo stesso oggetto.
 */
@Composable
fun FluidColorWell(
  color: Color,
  onColorChange: (Color) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  supportsOpacity: Boolean = true,
  recents: FluidRecentColors = rememberFluidRecentColors(),
  strings: FluidColorPickerStrings = FluidColorPickerStrings(),
) {
  val namespace = rememberFluidGlassNamespace()
  val source = remember(namespace) { FluidTransitionSource(WellSourceId, namespace) }
  var open by rememberSaveable { mutableStateOf(false) }
  val description = "$label, ${strings.colorName(color)}"
  Box(
    modifier = modifier
      .size(FluidColorPickerDefaults.WellTarget)
      .testTag(FluidColorPickerTags.Well)
      .semantics { contentDescription = description }
      .fluidPressable(
        onClick = { open = true },
        enabled = enabled,
        role = Role.Button,
        feedback = FluidPressFeedback.Expand,
        haptic = FluidHapticEvent.Open,
        grip = FluidPressGrip.YieldsToScroll,
      ),
    contentAlignment = Alignment.Center,
  ) {
    ColorWellFace(
      color = color,
      modifier = Modifier
        .size(FluidColorPickerDefaults.WellSize)
        .fluidTransitionSource(WellSourceId, namespace, CircleShape),
    )
  }
  FluidColorPickerSheet(
    visible = open,
    color = color,
    onColorChange = onColorChange,
    onDismissRequest = { open = false },
    supportsOpacity = supportsOpacity,
    recents = recents,
    source = source,
    strings = strings,
  )
}

private const val WellSourceId = "fluid-color-well"

/**
 * Una riga delle impostazioni col pozzetto a destra, come `ColorPicker("Sfondo", selection: …)` dentro un `Form` di
 * SwiftUI: tutta la riga si tocca e apre il foglio, che nasce dal pozzetto. Per TalkBack e' un tasto che dice il titolo
 * e il nome del colore.
 */
@Composable
fun FluidColorRow(
  title: String,
  color: Color,
  onColorChange: (Color) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String = "",
  enabled: Boolean = true,
  supportsOpacity: Boolean = true,
  recents: FluidRecentColors = rememberFluidRecentColors(),
  strings: FluidColorPickerStrings = FluidColorPickerStrings(),
) {
  val namespace = rememberFluidGlassNamespace()
  val source = remember(namespace) { FluidTransitionSource(WellSourceId, namespace) }
  var open by rememberSaveable { mutableStateOf(false) }
  val name = strings.colorName(color)
  dev.antigravity.fluidengine.ui.theme.FluidListRow(
    title = title,
    subtitle = subtitle,
    modifier = modifier.semantics { stateDescription = name },
    onClick = if (enabled) ({ open = true }) else null,
    disclosure = false,
    badge = {
      ColorWellFace(
        color = color,
        modifier = Modifier
          .size(FluidColorPickerDefaults.WellSize)
          .testTag(FluidColorPickerTags.Well)
          .fluidTransitionSource(WellSourceId, namespace, CircleShape),
      )
    },
  )
  FluidColorPickerSheet(
    visible = open,
    color = color,
    onColorChange = onColorChange,
    onDismissRequest = { open = false },
    supportsOpacity = supportsOpacity,
    recents = recents,
    source = source,
    strings = strings,
  )
}

/** La faccia del pozzetto: l'anello dell'arcobaleno, il vuoto, il colore (sulla scacchiera se e' trasparente). */
@Composable
private fun ColorWellFace(color: Color, modifier: Modifier) {
  val checker = rememberCheckerBrush()
  // Un filo attorno al colore: un pozzetto bianco su una riga bianca (o nero su nero) si vede ancora.
  val edge = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
  Canvas(modifier) {
    val ring = FluidColorPickerDefaults.WellRing.toPx()
    val gap = FluidColorPickerDefaults.WellGap.toPx()
    val radius = size.minDimension / 2f
    drawCircle(brush = RainbowSweep, radius = radius - ring / 2f, style = Stroke(width = ring))
    val inner = radius - ring - gap
    if (color.alpha < 1f) drawCircle(brush = checker, radius = inner)
    drawCircle(color = color, radius = inner)
    drawCircle(color = edge, radius = inner, style = Stroke(width = 0.5.dp.toPx().coerceAtLeast(1f)))
  }
}

private val RainbowSweep = Brush.sweepGradient(
  listOf(
    Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF32ADE6),
    Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFFFF3B30),
  ),
)

/**
 * La versione corta, per una riga delle impostazioni: una fila di campioni ([FluidColorDot], gli stessi pallini di
 * `FluidAccentPicker`, bersaglio 48 dp, ruolo `RadioButton` e nome a parole) e, se [customLabel] non e' null, un pozzetto
 * in fondo per un colore su misura. La fila scorre di lato se non ci sta.
 *
 * @param selected il colore scelto; se non e' fra i campioni, lo mostra il pozzetto.
 */
@Composable
fun FluidColorSwatches(
  colors: List<Color>,
  selected: Color?,
  onSelect: (Color) -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidColorPickerStrings = FluidColorPickerStrings(),
  customLabel: String? = strings.custom,
  supportsOpacity: Boolean = false,
  recents: FluidRecentColors = rememberFluidRecentColors(),
) {
  val selectedArgb = selected?.toArgb()
  val inSwatches = colors.any { it.toArgb() == selectedArgb }
  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    colors.forEach { c ->
      FluidColorDot(
        color = c,
        selected = c.toArgb() == selectedArgb,
        onClick = { onSelect(c) },
        label = strings.colorName(c),
      )
    }
    if (customLabel != null) {
      FluidColorWell(
        color = if (selected != null && !inSwatches) selected else colors.lastOrNull() ?: Color.White,
        onColorChange = onSelect,
        label = customLabel,
        supportsOpacity = supportsOpacity,
        recents = recents,
        strings = strings,
      )
    }
  }
}

// ================================================================================================================
// Il foglio
// ================================================================================================================

/**
 * Il foglio del selettore: il foglio di vetro dell'engine (`FluidGlassSheet`) con dentro [FluidColorPicker], a
 * [FluidColorPickerDefaults.SheetHeight] (vetro) e a tutta altezza (pagina). Con [source] nasce da quella vista e ci
 * rientra (il pozzetto lo passa da se'). Il colore scelto entra nei [recents] quando il foglio si chiude, se e' cambiato.
 *
 * Il contagocce: il foglio esce, la finestra dell'app si fotografa e compare la lente; il dito la porta sul colore, e al
 * rilascio il colore e' scelto e il foglio torna.
 */
@Composable
fun FluidColorPickerSheet(
  visible: Boolean,
  color: Color,
  onColorChange: (Color) -> Unit,
  onDismissRequest: () -> Unit,
  supportsOpacity: Boolean = true,
  recents: FluidRecentColors = rememberFluidRecentColors(),
  source: FluidTransitionSource? = null,
  strings: FluidColorPickerStrings = FluidColorPickerStrings(),
) {
  var eyedropping by remember { mutableStateOf(false) }
  // Il modo resta quello di prima riaprendo il foglio e tornando dal contagocce, come iOS.
  var mode by rememberSaveable { mutableStateOf(FluidColorPickerMode.Grid) }
  val latestColor by rememberUpdatedState(color)
  // Il colore con cui il foglio si e' aperto: alla chiusura, se e' cambiato, il nuovo entra nei recenti.
  var opening by remember { mutableStateOf<Color?>(null) }
  LaunchedEffect(visible) {
    opening = if (visible) latestColor else null
    if (!visible) eyedropping = false
  }
  val close: () -> Unit = {
    val start = opening
    if (start != null && start.toArgb() != latestColor.toArgb()) recents.add(latestColor)
    onDismissRequest()
  }
  FluidGlassSheet(
    visible = visible && !eyedropping,
    onDismissRequest = close,
    detents = remember { setOf(FluidSheetDetent.Height(FluidColorPickerDefaults.SheetHeight), FluidSheetDetent.Large) },
    // Il selettore di iOS 26 non mostra la maniglia: il foglio si trascina lo stesso, da ogni punto.
    dragIndicator = false,
    paneTitle = strings.title,
    source = source,
  ) {
    FluidColorPicker(
      color = color,
      onColorChange = onColorChange,
      modifier = Modifier.align(Alignment.CenterHorizontally).weight(1f, fill = false),
      supportsOpacity = supportsOpacity,
      recents = recents,
      onEyedropper = { eyedropping = true },
      onClose = close,
      strings = strings,
      mode = mode,
      onModeChange = { mode = it },
    )
  }
  FluidColorEyedropper(
    active = visible && eyedropping,
    onPick = { picked ->
      eyedropping = false
      if (picked != null) onColorChange(picked.copy(alpha = latestColor.alpha))
    },
    strings = strings,
  )
}

/**
 * Il contenuto del selettore, anche fuori da un foglio (in un popover, in una pagina su un tablet): la testata col
 * contagocce, il titolo e Chiudi; i tre modi; l'opacita'; il colore scelto e i recenti.
 *
 * @param onEyedropper null: niente contagocce (per esempio quando il selettore non sta in una finestra da fotografare).
 * @param onClose null: niente Chiudi (il selettore sta in una pagina).
 * @param mode il modo, se lo tiene chi chiama (il foglio lo tiene, cosi' tornando dal contagocce o riaprendolo resta
 *   quello di prima, come iOS); null: lo tiene il selettore, partendo da [initialMode].
 * @param onModeChange il modo scelto col segmentato, quando [mode] e' di chi chiama.
 */
@Composable
fun FluidColorPicker(
  color: Color,
  onColorChange: (Color) -> Unit,
  modifier: Modifier = Modifier,
  supportsOpacity: Boolean = true,
  recents: FluidRecentColors? = null,
  initialMode: FluidColorPickerMode = FluidColorPickerMode.Grid,
  onEyedropper: (() -> Unit)? = null,
  onClose: (() -> Unit)? = null,
  strings: FluidColorPickerStrings = FluidColorPickerStrings(),
  mode: FluidColorPickerMode? = null,
  onModeChange: (FluidColorPickerMode) -> Unit = {},
) {
  var ownMode by rememberSaveable { mutableStateOf(initialMode) }
  val shownMode = mode ?: ownMode
  val emit by rememberUpdatedState(onColorChange)
  // La tinta ricordata: su un grigio, un bianco o un nero la tinta non c'e', e lo spettro non deve saltare al rosso.
  var hue by remember { mutableFloatStateOf(colorToHsv(color)[0]) }
  LaunchedEffect(color) {
    val hsv = colorToHsv(color)
    if (hsv[1] >= 0.02f && hsv[2] >= 0.02f) hue = hsv[0]
  }
  Column(
    modifier = modifier
      .widthIn(max = FluidColorPickerDefaults.MaxContentWidth)
      .fillMaxWidth()
      .testTag(FluidColorPickerTags.Picker)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    PickerHeader(strings, onEyedropper, onClose)
    FluidSegmentedControl(
      options = FluidColorPickerMode.entries,
      selected = shownMode,
      onSelect = {
        ownMode = it
        onModeChange(it)
      },
      modifier = Modifier.fillMaxWidth().testTag(FluidColorPickerTags.Modes),
      label = { strings.modeLabel(it) },
    )
    when (shownMode) {
      FluidColorPickerMode.Grid -> ColorGrid(color, strings) { emit(it.copy(alpha = color.alpha)) }
      FluidColorPickerMode.Spectrum -> ColorSpectrum(color, hue, strings) { h, c ->
        hue = h
        emit(c.copy(alpha = color.alpha))
      }
      FluidColorPickerMode.Sliders -> ColorSliders(color, strings, supportsOpacity) { emit(it) }
    }
    if (supportsOpacity) {
      Caption(strings.opacity)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorTrackSlider(
          value = color.alpha,
          range = 0f..1f,
          onValueChange = { emit(color.copy(alpha = it)) },
          track = { size -> Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 1f)), 0f, size.width) },
          checker = true,
          thumb = color,
          description = strings.opacity,
          state = "${(color.alpha * 100).roundToInt()}%",
          modifier = Modifier.weight(1f).testTag(FluidColorPickerTags.Opacity),
        )
        ValueText("${(color.alpha * 100).roundToInt()}%")
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    CurrentAndRecents(color, recents, strings) { emit(it) }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun PickerHeader(strings: FluidColorPickerStrings, onEyedropper: (() -> Unit)?, onClose: (() -> Unit)?) {
  Box(Modifier.fillMaxWidth().height(48.dp)) {
    if (onEyedropper != null) {
      FluidGlassIconButton(
        onClick = onEyedropper,
        modifier = Modifier.align(Alignment.CenterStart).testTag(FluidColorPickerTags.Eyedropper),
      ) { FluidIcon(EyedropperIcon, strings.eyedropper) }
    }
    Text(
      text = strings.title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.align(Alignment.Center).semantics { heading() },
    )
    if (onClose != null) {
      FluidGlassIconButton(
        onClick = onClose,
        modifier = Modifier.align(Alignment.CenterEnd).testTag(FluidColorPickerTags.Close),
      ) { FluidIcon(FluidIcons.Close, strings.close) }
    }
  }
}

@Composable
private fun Caption(text: String) {
  Text(
    text = text.uppercase(Locale.getDefault()),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp),
  )
}

/** Il valore accanto a un cursore, in una capsula chiara come iOS 26. */
@Composable
private fun ValueText(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier
      .size(FluidColorPickerDefaults.ValueBoxWidth, FluidColorPickerDefaults.ValueBoxHeight)
      .clip(FluidCapsuleShape)
      .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
  }
}

// --- La griglia -----------------------------------------------------------------------------------------------------

/**
 * La griglia: 120 caselle senza fughe in un rettangolo dagli angoli tondi; il dito sceglie la casella sotto di se' e,
 * scorrendo, quelle che attraversa (come su iOS). La casella scelta ha il bordo bianco dentro e scuro fuori. Ogni casella
 * e' un nodo di TalkBack col nome del colore.
 */
@Composable
private fun ColorGrid(color: Color, strings: FluidColorPickerStrings, onPick: (Color) -> Unit) {
  val cells = FluidColorGrid
  val cols = FluidColorPickerDefaults.GridColumns
  val rows = FluidColorPickerDefaults.GridRows
  val selected = remember(color) { gridIndexOf(color) }
  val pick by rememberUpdatedState(onPick)
  val shape = ContinuousCornerShape(FluidColorPickerDefaults.PanelCorner)
  val outline = MaterialTheme.colorScheme.onSurface
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(cols / rows.toFloat())
      .testTag(FluidColorPickerTags.Grid)
      .pointerInput(Unit) {
        fun cellAt(p: Offset): Int {
          val c = (p.x / size.width * cols).toInt().coerceIn(0, cols - 1)
          val r = (p.y / size.height * rows).toInt().coerceIn(0, rows - 1)
          return r * cols + c
        }
        awaitEachGesture {
          val down = awaitFirstDown()
          down.consume()
          var last = cellAt(down.position)
          pick(cells[last])
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            val now = cellAt(change.position)
            if (now != last) {
              last = now
              pick(cells[now])
            }
          }
        }
      }
      .drawWithCache {
        val cw = size.width / cols
        val ch = size.height / rows
        val ring = FluidColorPickerDefaults.ThumbRing.toPx()
        val clip = androidx.compose.ui.graphics.Path().apply {
          addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        onDrawWithContent {
          clipPath(clip) {
            for (i in cells.indices) {
              drawRect(cells[i], Offset((i % cols) * cw, (i / cols) * ch), Size(cw + 0.5f, ch + 0.5f))
            }
          }
          if (selected >= 0) {
            val topLeft = Offset((selected % cols) * cw, (selected / cols) * ch)
            drawRoundRect(
              color = outline.copy(alpha = 0.55f),
              topLeft = topLeft - Offset(ring / 2f, ring / 2f),
              size = Size(cw + ring, ch + ring),
              cornerRadius = CornerRadius(ring * 1.4f),
              style = Stroke(width = 1.dp.toPx()),
            )
            drawRoundRect(
              color = Color.White,
              topLeft = topLeft + Offset(ring / 2f, ring / 2f),
              size = Size(cw - ring, ch - ring),
              cornerRadius = CornerRadius(ring),
              style = Stroke(width = ring),
            )
          }
        }
      },
  ) {
    // I nodi di TalkBack: una riga per riga, una casella per casella, senza disegno (lo fa il rettangolo sopra).
    for (r in 0 until rows) {
      Row(Modifier.weight(1f).fillMaxWidth()) {
        for (c in 0 until cols) {
          val index = r * cols + c
          val cell = cells[index]
          Box(
            Modifier
              .weight(1f)
              .fillMaxSize()
              .semantics {
                contentDescription = strings.colorName(cell)
                this.selected = index == selected
                role = Role.RadioButton
                onClick { pick(cell); true }
              },
          )
        }
      }
    }
  }
}

// --- Lo spettro -----------------------------------------------------------------------------------------------------

/**
 * Lo spettro: la tinta da sinistra a destra, il bianco in alto, il pieno a meta', il nero in basso; il segno (un anello
 * bianco col colore dentro) sta dove il colore cade, e il dito lo porta. TalkBack lo regola con «aumenta» e «diminuisci»
 * sulla tinta.
 */
@Composable
private fun ColorSpectrum(
  color: Color,
  hue: Float,
  strings: FluidColorPickerStrings,
  onPick: (hue: Float, color: Color) -> Unit,
) {
  val pick by rememberUpdatedState(onPick)
  val shape = ContinuousCornerShape(FluidColorPickerDefaults.PanelCorner)
  val position = spectrumPosition(color, hue)
  val latestPosition by rememberUpdatedState(position)
  val latestColor by rememberUpdatedState(color)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(FluidColorPickerDefaults.GridColumns / FluidColorPickerDefaults.GridRows.toFloat())
      .testTag(FluidColorPickerTags.Spectrum)
      .semantics {
        contentDescription = strings.spectrum
        stateDescription = strings.colorName(color)
        progressBarRangeInfo = ProgressBarRangeInfo(position.x * 360f, 0f..360f)
        setProgress { target ->
          val x = (target / 360f).coerceIn(0f, 1f)
          pick(x * 360f, spectrumColor(x, latestPosition.y))
          true
        }
      }
      .pointerInput(Unit) {
        fun at(p: Offset) {
          val x = (p.x / size.width).coerceIn(0f, 1f)
          val y = (p.y / size.height).coerceIn(0f, 1f)
          pick(x * 360f, spectrumColor(x, y))
        }
        awaitEachGesture {
          val down = awaitFirstDown()
          down.consume()
          at(down.position)
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (change.positionChange() != Offset.Zero) at(change.position)
            change.consume()
          }
        }
      }
      .drawWithCache {
        val hues = Brush.horizontalGradient(
          listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
          0f, size.width,
        )
        val shade = Brush.verticalGradient(
          0f to Color.White, 0.5f to Color.White.copy(alpha = 0f), 0.5f to Color.Black.copy(alpha = 0f), 1f to Color.Black,
          startY = 0f, endY = size.height,
        )
        val outline = androidx.compose.ui.graphics.Path().apply {
          addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val marker = FluidColorPickerDefaults.SpectrumMarker.toPx() / 2f
        val ring = FluidColorPickerDefaults.ThumbRing.toPx()
        onDrawBehind {
          clipPath(outline) {
            drawRect(hues)
            drawRect(shade)
          }
          val p = latestPosition
          val center = Offset(p.x * size.width, p.y * size.height)
          drawCircle(Color.Black.copy(alpha = 0.22f), radius = marker + 1.dp.toPx(), center = center)
          drawCircle(Color.White, radius = marker, center = center)
          // Il segno ha il colore scelto, non quello dello spettro in quel punto: un colore smorzato e scuro (dai
          // cursori) non sta sullo spettro, e il segno mostrava un altro colore, saturo (R2).
          drawCircle(latestColor.copy(alpha = 1f), radius = marker - ring, center = center)
        }
      },
  )
}

// --- I cursori ------------------------------------------------------------------------------------------------------

@Composable
private fun ColorSliders(color: Color, strings: FluidColorPickerStrings, supportsOpacity: Boolean, onPick: (Color) -> Unit) {
  val channels = listOf(
    Triple(strings.red, "r", color.red),
    Triple(strings.green, "g", color.green),
    Triple(strings.blue, "b", color.blue),
  )
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    channels.forEach { (name, key, value) ->
      Caption(name)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        fun withChannel(v: Float) = when (key) {
          "r" -> color.copy(red = v)
          "g" -> color.copy(green = v)
          else -> color.copy(blue = v)
        }
        val level = (value * 255f).roundToInt()
        ColorTrackSlider(
          value = value * 255f,
          range = 0f..255f,
          onValueChange = { onPick(withChannel((it.roundToInt() / 255f).coerceIn(0f, 1f))) },
          track = { size ->
            Brush.horizontalGradient(listOf(withChannel(0f).copy(alpha = 1f), withChannel(1f).copy(alpha = 1f)), 0f, size.width)
          },
          checker = false,
          thumb = color.copy(alpha = 1f),
          description = name,
          state = level.toString(),
          modifier = Modifier.weight(1f).testTag(FluidColorPickerTags.slider(key)),
        )
        ValueText(level.toString())
      }
    }
    // Come iOS: l'etichetta a sinistra, allineata al campo, e il campo a destra.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "${strings.hex} #",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
      )
      HexField(color, strings, supportsOpacity, onPick)
    }
  }
}

@Composable
private fun HexField(color: Color, strings: FluidColorPickerStrings, supportsOpacity: Boolean, onPick: (Color) -> Unit) {
  val shown = fluidColorHex(color).removePrefix("#")
  var text by remember(shown) { mutableStateOf(shown) }
  FluidTextField(
    value = text,
    onValueChange = { typed ->
      // Sei cifre: il colore, con l'opacita' di adesso. Otto (`RRGGBBAA`, incollate da un'altra app): anche l'opacita',
      // se il selettore la sostiene (R2: prima le ultime due si perdevano e si applicava subito solo il colore).
      val clean = typed.removePrefix("#").filter { Character.digit(it, 16) >= 0 }.take(8).uppercase(Locale.ROOT)
      text = clean
      when (clean.length) {
        6 -> fluidParseHexColor(clean)?.let { onPick(it.copy(alpha = color.alpha)) }
        8 -> fluidParseHexColor(clean)?.let { onPick(if (supportsOpacity) it else it.copy(alpha = color.alpha)) }
      }
    },
    modifier = Modifier.width(128.dp).testTag(FluidColorPickerTags.Hex),
    placeholder = "RRGGBB",
    label = null,
    showClearButton = false,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Characters,
      keyboardType = KeyboardType.Ascii,
      imeAction = ImeAction.Done,
      autoCorrectEnabled = false,
    ),
  )
}

/**
 * Un cursore del selettore: una capsula alta [FluidColorPickerDefaults.SliderHeight] col suo gradiente ([track]) e il
 * pomello (anello bianco col colore dentro) dove sta il valore. Il dito lo porta dal primo contatto, il tocco lo sposta li'.
 * Per TalkBack e' un cursore con «aumenta» e «diminuisci».
 */
@Composable
private fun ColorTrackSlider(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  track: (Size) -> Brush,
  checker: Boolean,
  thumb: Color,
  description: String,
  state: String,
  modifier: Modifier = Modifier,
) {
  val change by rememberUpdatedState(onValueChange)
  val latestRange by rememberUpdatedState(range)
  val checkerBrush = if (checker) rememberCheckerBrush() else null
  val span = range.endInclusive - range.start
  val fraction = if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
  Box(
    modifier
      .height(FluidColorPickerDefaults.SliderHeight)
      .semantics {
        contentDescription = description
        stateDescription = state
        progressBarRangeInfo = ProgressBarRangeInfo(value, range)
        setProgress { target ->
          change(target.coerceIn(latestRange.start, latestRange.endInclusive))
          true
        }
      }
      .pointerInput(Unit) {
        fun at(x: Float) {
          val h = size.height.toFloat()
          val usable = (size.width - h).coerceAtLeast(1f)
          val f = ((x - h / 2f) / usable).coerceIn(0f, 1f)
          val r = latestRange
          change(r.start + f * (r.endInclusive - r.start))
        }
        awaitEachGesture {
          val down = awaitFirstDown()
          down.consume()
          at(down.position.x)
          while (true) {
            val event = awaitPointerEvent()
            val c = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            if (c.positionChange() != Offset.Zero) at(c.position.x)
            c.consume()
          }
        }
      }
      .drawWithCache {
        val radius = CornerRadius(size.height / 2f)
        val brush = track(size)
        val ring = FluidColorPickerDefaults.ThumbRing.toPx()
        onDrawBehind {
          if (checkerBrush != null) drawRoundRect(checkerBrush, cornerRadius = radius)
          drawRoundRect(brush, cornerRadius = radius)
          val r = size.height / 2f
          val cx = r + fraction * (size.width - size.height)
          val center = Offset(cx, r)
          drawCircle(Color.Black.copy(alpha = 0.22f), radius = r, center = center)
          drawCircle(Color.White, radius = r - 1.dp.toPx(), center = center)
          if (checkerBrush != null && thumb.alpha < 1f) drawCircle(checkerBrush, radius = r - 1.dp.toPx() - ring, center = center)
          drawCircle(thumb, radius = r - 1.dp.toPx() - ring, center = center)
        }
      },
  )
}

// --- Il colore scelto e i recenti ---------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentAndRecents(
  color: Color,
  recents: FluidRecentColors?,
  strings: FluidColorPickerStrings,
  onPick: (Color) -> Unit,
) {
  val checker = rememberCheckerBrush()
  Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    val swatchShape = ContinuousCornerShape(FluidRadius.Card)
    val name = strings.colorName(color)
    Box(
      Modifier
        .size(FluidColorPickerDefaults.CurrentSwatch)
        .testTag(FluidColorPickerTags.Current)
        .semantics { contentDescription = "${strings.current}: $name, ${fluidColorHex(color)}" }
        .clip(swatchShape)
        .drawBehind {
          if (color.alpha < 1f) drawRect(checker)
          drawRect(color)
        },
    )
    if (recents != null) {
      // Come iOS 26: i colori salvati vanno a capo, col «+» in fondo, allineati in alto al quadrato del colore scelto.
      FlowRow(Modifier.weight(1f).offset(y = (-7).dp).testTag(FluidColorPickerTags.Recents)) {
        val currentArgb = color.toArgb()
        recents.colors.forEach { c ->
          FluidColorDot(
            color = c,
            selected = c.toArgb() == currentArgb,
            onClick = { onPick(c) },
            label = strings.colorName(c),
            size = FluidColorPickerDefaults.RecentDot,
          )
        }
        AddRecentButton(strings.addToRecents) { recents.add(color) }
      }
    }
  }
}

/** Il «+» dei colori salvati: un cerchio grigio col segno, grande quanto un pallino. */
@Composable
private fun AddRecentButton(label: String, onClick: () -> Unit) {
  val fill = MaterialTheme.colorScheme.surfaceContainerHighest
  val ink = MaterialTheme.colorScheme.onSurfaceVariant
  Box(
    Modifier
      .size(48.dp)
      .testTag(FluidColorPickerTags.AddRecent)
      .semantics { contentDescription = label }
      .fluidPressable(
        onClick = onClick,
        role = Role.Button,
        feedback = FluidPressFeedback.Expand,
        grip = FluidPressGrip.YieldsToScroll,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(FluidColorPickerDefaults.RecentDot)) {
      drawCircle(fill)
      val arm = size.minDimension * 0.22f
      val c = center
      val w = 2.dp.toPx()
      drawLine(ink, c - Offset(arm, 0f), c + Offset(arm, 0f), w, StrokeCap.Round)
      drawLine(ink, c - Offset(0f, arm), c + Offset(0f, arm), w, StrokeCap.Round)
    }
  }
}

/** La scacchiera dietro i colori trasparenti: un riquadro di due quadretti ripetuto, fatto una volta per densita'. */
@Composable
private fun rememberCheckerBrush(): Brush {
  val density = LocalDensity.current
  // I quadretti di iOS: bianco e grigio chiaro nel tema chiaro, due grigi scuri nel tema scuro.
  val darkSide = GlassDefaults.isDarkSurface()
  val light = if (darkSide) Color(0xFF48484A) else Color.White
  val dark = if (darkSide) Color(0xFF2C2C2E) else Color(0xFFD9D9DE)
  return remember(density, light, dark) {
    val cell = with(density) { FluidColorPickerDefaults.CheckerCell.roundToPx() }.coerceAtLeast(1)
    val bitmap = ImageBitmap(cell * 2, cell * 2)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val paint = androidx.compose.ui.graphics.Paint()
    paint.color = light
    canvas.drawRect(0f, 0f, cell * 2f, cell * 2f, paint)
    paint.color = dark
    canvas.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), paint)
    canvas.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, paint)
    ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
  }
}

/** Il contagocce al tratto, nello stile delle icone dell'engine (Material Symbols, peso 500): un tubo e la pompetta. */
private val EyedropperIcon: ImageVector by lazy {
  ImageVector.Builder(name = "FluidEyedropper", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(
      pathData = PathParser().parsePathString(
        "M4.5 19.5 L5 17.5 L13.5 9 M10 6.5 L17.5 14 M15 11.5 L6.5 20 L4.5 19.5 " +
          "M13 7.5 L15.6 4.9 A2.1 2.1 0 0 1 18.6 4.9 L19.1 5.4 A2.1 2.1 0 0 1 19.1 8.4 L16.5 11",
      ).toNodes(),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    )
    .build()
}

// ================================================================================================================
// Il contagocce
// ================================================================================================================

/**
 * Il contagocce: quando [active], fotografa la finestra dell'app (`PixelCopy`, dopo che il foglio e' uscito) e mostra la
 * lente al centro; il dito la porta, la lente ingrandisce gli [FluidColorPickerDefaults.LoupePixels] pixel attorno al
 * suo centro, e al rilascio [onPick] riceve il colore del pixel nel mezzo. Indietro, o una finestra che non si lascia
 * fotografare (`FLAG_SECURE`), danno null.
 *
 * La lente sta in una finestra sua sopra l'app (un `Popup`), cosi' la fotografia non la contiene. Il pixel e' quello
 * della finestra, in sRGB: un colore e' scelto «come si vede».
 */
@Composable
fun FluidColorEyedropper(
  active: Boolean,
  onPick: (Color?) -> Unit,
  strings: FluidColorPickerStrings = FluidColorPickerStrings(),
) {
  if (!active) return
  val view = LocalView.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val haptics = LocalFluidHaptics.current
  val done by rememberUpdatedState(onPick)
  var shot by remember { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(Unit) {
    delay(if (reduced) FluidColorPickerDefaults.EyedropperCaptureDelayReducedMillis else FluidColorPickerDefaults.EyedropperCaptureDelayMillis)
    val window = view.context.findActivity()?.window
    val image = window?.let { captureWindow(it) }
    if (image == null) done(null) else shot = image
  }
  val image = shot ?: return
  // Il centro della lente, in pixel della finestra fotografata.
  var center by remember { mutableStateOf(Offset(image.width / 2f, image.height / 2f)) }
  // Dove sta la finestra della lente rispetto a quella dell'app (di solito nello stesso punto).
  var origin by remember { mutableStateOf(IntOffset.Zero) }
  Popup(
    popupPositionProvider = WindowOriginPosition,
    properties = PopupProperties(focusable = true, clippingEnabled = false),
    onDismissRequest = { done(null) },
  ) {
    val loupe = with(LocalDensity.current) { FluidColorPickerDefaults.LoupeSize.toPx() }
    val ring = with(LocalDensity.current) { FluidColorPickerDefaults.LoupeRing.toPx() }
    Box(
      Modifier
        .fillMaxSize()
        .onGloballyPositioned { coords ->
          val decor = IntArray(2).also { view.rootView.getLocationOnScreen(it) }
          val mine = coords.positionOnScreen()
          origin = IntOffset(mine.x.roundToInt() - decor[0], mine.y.roundToInt() - decor[1])
        }
        .testTag(FluidColorPickerTags.Loupe)
        .semantics { contentDescription = strings.eyedropperHint }
        .pointerInput(image) {
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            haptics.play(FluidHapticEvent.GestureStart)
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              val delta = change.positionChange()
              change.consume()
              if (delta != Offset.Zero) {
                center = Offset(
                  (center.x + delta.x).coerceIn(0f, image.width - 1f),
                  (center.y + delta.y).coerceIn(0f, image.height - 1f),
                )
              }
              if (!change.pressed) break
            }
            haptics.play(FluidHapticEvent.Confirm)
            done(eyedropperColorAt(image, center.x.toInt(), center.y.toInt()))
          }
        }
        .drawBehind {
          val c = Offset(center.x - origin.x, center.y - origin.y)
          drawLoupeVeil(c, loupe / 2f)
          drawLoupe(image, center, c, loupe / 2f, ring)
        },
    )
  }
}

/** Il colore del pixel ([x], [y]) di una fotografia della finestra, coi bordi trattenuti. */
internal fun eyedropperColorAt(image: Bitmap, x: Int, y: Int): Color =
  Color(image.getPixel(x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1))).copy(alpha = 1f)

/** La lente: i pixel attorno a [source] ingranditi in un cerchio centrato in [at], la casella di mezzo segnata, l'anello del colore. */
private fun DrawScope.drawLoupe(image: Bitmap, source: Offset, at: Offset, radius: Float, ring: Float) {
  val n = FluidColorPickerDefaults.LoupePixels
  val half = n / 2
  val inner = radius - ring
  val cell = inner * 2f / n
  val sx = source.x.toInt()
  val sy = source.y.toInt()
  val clip = androidx.compose.ui.graphics.Path().apply {
    addOval(androidx.compose.ui.geometry.Rect(at, inner))
  }
  drawCircle(Color.Black.copy(alpha = 0.25f), radius + 2.dp.toPx(), at)
  clipPath(clip) {
    for (j in -half..half) {
      for (i in -half..half) {
        val color = eyedropperColorAt(image, sx + i, sy + j)
        drawRect(color, Offset(at.x + (i - 0.5f) * cell, at.y + (j - 0.5f) * cell), Size(cell + 0.5f, cell + 0.5f))
      }
    }
    val line = Color.Black.copy(alpha = 0.12f)
    for (k in -half..half + 1) {
      val o = (k - 0.5f) * cell
      drawLine(line, Offset(at.x + o, at.y - inner), Offset(at.x + o, at.y + inner), 1f)
      drawLine(line, Offset(at.x - inner, at.y + o), Offset(at.x + inner, at.y + o), 1f)
    }
  }
  val mid = Offset(at.x - cell / 2f, at.y - cell / 2f)
  drawRect(Color.Black, mid - Offset(1f, 1f), Size(cell + 2f, cell + 2f), style = Stroke(width = 1.5f))
  drawRect(Color.White, mid, Size(cell, cell), style = Stroke(width = 1.5f))
  drawCircle(Color(0xFF8E8E93), inner + 1.dp.toPx(), at, style = Stroke(width = 2.dp.toPx()))
  drawCircle(eyedropperColorAt(image, sx, sy), radius - ring / 2f, at, style = Stroke(width = ring))
  drawCircle(Color.Black.copy(alpha = 0.18f), radius, at, style = Stroke(width = 1f))
}

/** Il velo sull'app, con la finestra chiara attorno alla lente in [at] (quattro rettangoli: niente tracciati per fotogramma). */
private fun DrawScope.drawLoupeVeil(at: Offset, radius: Float) {
  val veil = Color.Black.copy(alpha = FluidColorPickerDefaults.LoupeVeilAlpha)
  val half = radius * FluidColorPickerDefaults.LoupeWindow / 2f
  val left = (at.x - half).coerceIn(0f, size.width)
  val right = (at.x + half).coerceIn(0f, size.width)
  val top = (at.y - half * 0.76f).coerceIn(0f, size.height)
  val bottom = (at.y + half * 0.76f).coerceIn(0f, size.height)
  drawRect(veil, Offset.Zero, Size(size.width, top))
  drawRect(veil, Offset(0f, bottom), Size(size.width, size.height - bottom))
  drawRect(veil, Offset(0f, top), Size(left, bottom - top))
  drawRect(veil, Offset(right, top), Size(size.width - right, bottom - top))
}

/** La finestra della lente parte dall'angolo della finestra dell'app e la copre. */
private val WindowOriginPosition = object : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset = IntOffset.Zero
}

/** La fotografia della finestra, o null se il sistema la rifiuta. */
private suspend fun captureWindow(window: Window): Bitmap? {
  val decor: View = window.decorView
  if (decor.width <= 0 || decor.height <= 0) return null
  val image = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
  return suspendCancellableCoroutine { continuation ->
    try {
      PixelCopy.request(
        window,
        image,
        { result -> if (continuation.isActive) continuation.resume(if (result == PixelCopy.SUCCESS) image else null) },
        Handler(Looper.getMainLooper()),
      )
    } catch (_: IllegalArgumentException) {
      if (continuation.isActive) continuation.resume(null)
    }
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
