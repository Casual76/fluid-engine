package dev.antigravity.fluidengine.ink

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

// F8.16 · Il modello del disegno, in Kotlin puro: gli strumenti, i punti di un tratto con pressione e inclinazione, il
// tratto. Niente di androidx.ink qui dentro: il modello si salva, si annulla, si cancella e si prova sul computer, e la
// resa (androidx.ink sul telefono, il ripiego in `Path` altrove) lo legge senza cambiarlo.
//
// Le coordinate sono quelle del **foglio** («mondo»): per un foglio bianco i dp della pagina a zoom 1, per una foto i suoi
// pixel, per un PDF i punti tipografici della pagina. Lo spessore di un tratto e' nella stessa unita': uno zoom non lo
// cambia, come in PencilKit, dove la larghezza di `PKInkingTool` e' nelle coordinate del disegno.

/**
 * Uno strumento della tavolozza. Una classe di valori invece di un `enum`: gli strumenti di PencilKit sono cresciuti
 * negli anni (il pennarello, il monolinea, la penna stilografica, l'acquerello, il pastello), e uno strumento nuovo qui
 * non deve rompere i `when` delle app.
 *
 * Quelli che disegnano ([isInking]) lasciano un [FluidInkStroke]; [Eraser] e [Lasso] lavorano sui tratti che ci sono. Il
 * righello non e' uno strumento ma un interruttore ([FluidInkState.rulerVisible]), come in `PKToolPicker`.
 */
@Immutable
@JvmInline
value class FluidInkTool internal constructor(val id: Int) {
  /** Vero per la penna, l'evidenziatore e la matita: gli strumenti che lasciano un tratto. */
  val isInking: Boolean get() = id in 1..99

  override fun toString(): String = when (id) {
    1 -> "Pen"
    2 -> "Highlighter"
    3 -> "Pencil"
    4 -> "Monoline"
    101 -> "Eraser"
    102 -> "Lasso"
    else -> "FluidInkTool($id)"
  }

  companion object {
    /** La penna: tratto pieno, piu' largo con la pressione e un filo con la velocita' (`PKInkingTool.InkType.pen`). */
    val Pen = FluidInkTool(1)

    /** L'evidenziatore: punta a scalpello, colore trasparente che non si somma con se stesso (`.marker`). */
    val Highlighter = FluidInkTool(2)

    /** La matita: tratto sottile e morbido, piu' chiaro se si preme poco e piu' largo se la penna e' inclinata (`.pencil`). */
    val Pencil = FluidInkTool(3)

    /** Il monolinea: lo stesso spessore dall'inizio alla fine, senza pressione ne' velocita' (`.monoline`, iOS 17). */
    val Monoline = FluidInkTool(4)

    /** La gomma: a oggetti (toglie il tratto toccato) o a pixel (toglie solo la parte toccata), [FluidInkEraserMode]. */
    val Eraser = FluidInkTool(101)

    /** Il lazo: si gira attorno ai tratti per sceglierli, poi si spostano, si duplicano, si tolgono o si ricolorano. */
    val Lasso = FluidInkTool(102)

    /** Gli strumenti che disegnano. */
    val inking: List<FluidInkTool> = listOf(Pen, Monoline, Highlighter, Pencil)

    /**
     * Tutti gli strumenti, nell'ordine della tavolozza di Markup di iOS 26 e 27 da sinistra (la guida dell'iPhone, «Draw
     * and handwrite with Markup», fiducia alta): penna, monolinea, pennarello (qui l'evidenziatore), gomma, lazo, (il
     * righello), matita.
     */
    val all: List<FluidInkTool> = listOf(Pen, Monoline, Highlighter, Eraser, Lasso, Pencil)

    /** Lo strumento con questo [id], o null (un salvataggio di una versione piu' nuova). */
    fun fromId(id: Int): FluidInkTool? = all.firstOrNull { it.id == id }
  }
}

/**
 * Da dove arriva un tratto: il dito, la penna (lo stilo), il mouse. I valori sono quelli di `MotionEvent.TOOL_TYPE_*` e
 * di `androidx.ink.brush.InputToolType`, cosi' la conversione e' un numero.
 */
@Immutable
@JvmInline
value class FluidInkInput internal constructor(val id: Int) {
  override fun toString(): String = when (id) {
    1 -> "Mouse"
    2 -> "Touch"
    3 -> "Stylus"
    4 -> "Synthetic"
    else -> "Unknown"
  }

  companion object {
    val Unknown = FluidInkInput(0)
    val Mouse = FluidInkInput(1)
    val Touch = FluidInkInput(2)
    val Stylus = FluidInkInput(3)

    /** Un tratto fatto dall'engine: una forma riconosciuta, una copia, un tratto scritto dall'app. */
    val Synthetic = FluidInkInput(4)

    fun fromId(id: Int): FluidInkInput = when (id) {
      1 -> Mouse
      2 -> Touch
      3 -> Stylus
      4 -> Synthetic
      else -> Unknown
    }
  }
}

/** Come cancella la gomma: il tratto intero toccato ([Object], la gomma «vettoriale» di PencilKit) o solo la parte toccata ([Area]). */
@Immutable
@JvmInline
value class FluidInkEraserMode internal constructor(val id: Int) {
  override fun toString(): String = if (id == 2) "Area" else "Object"

  companion object {
    /** `PKEraserTool(.vector)`: toglie ogni tratto che la gomma tocca («Gomma per oggetti», da scegliere). */
    val Object = FluidInkEraserMode(1)

    /**
     * `PKEraserTool(.bitmap)`: toglie solo la parte toccata, e un tratto tagliato a meta' diventa due. Quella di serie di
     * Markup in iOS 26 e 27 («Erase pixels: scrub over…»; la gomma per oggetti si sceglie toccando di nuovo la gomma).
     */
    val Area = FluidInkEraserMode(2)
  }
}

/**
 * Chi disegna. Come `PKCanvasViewDrawingPolicy`: con [Auto] il dito disegna finche' non si usa la penna; dalla prima
 * volta che la penna tocca il foglio disegna solo la penna, e il dito scorre e ingrandisce la pagina («Disegna solo con
 * Apple Pencil», acceso da solo). Con [AnyInput] disegnano tutti; con [StylusOnly] solo la penna, dall'inizio.
 */
@Immutable
@JvmInline
value class FluidInkDrawingPolicy internal constructor(val id: Int) {
  override fun toString(): String = when (id) {
    2 -> "AnyInput"
    3 -> "StylusOnly"
    else -> "Auto"
  }

  companion object {
    val Auto = FluidInkDrawingPolicy(1)
    val AnyInput = FluidInkDrawingPolicy(2)
    val StylusOnly = FluidInkDrawingPolicy(3)
  }
}

/** La forma in cui si e' trasformato un tratto tenuto fermo alla fine (null: un tratto a mano libera). */
@Immutable
@JvmInline
value class FluidInkShapeKind internal constructor(val id: Int) {
  override fun toString(): String = when (id) {
    1 -> "Line"
    2 -> "Ellipse"
    3 -> "Circle"
    4 -> "Triangle"
    5 -> "Rectangle"
    6 -> "Square"
    7 -> "Polygon"
    else -> "FluidInkShapeKind($id)"
  }

  companion object {
    val Line = FluidInkShapeKind(1)
    val Ellipse = FluidInkShapeKind(2)
    val Circle = FluidInkShapeKind(3)
    val Triangle = FluidInkShapeKind(4)
    val Rectangle = FluidInkShapeKind(5)
    val Square = FluidInkShapeKind(6)

    /** Un quadrilatero storto, un pentagono, un esagono: i lati diventano dritti e gli angoli restano dove sono. */
    val Polygon = FluidInkShapeKind(7)

    fun fromId(id: Int): FluidInkShapeKind? = if (id in 1..7) FluidInkShapeKind(id) else null
  }
}

/**
 * I punti di un tratto, impacchettati: per ogni punto la posizione, il tempo dall'inizio del tratto (ms), la pressione
 * (0..1), l'inclinazione della penna (radianti, 0 = dritta sul foglio) e l'orientamento (radianti). Un valore che il
 * dispositivo non da' vale [None] in tutto il tratto ([hasPressure], [hasTilt], [hasOrientation]): androidx.ink vuole
 * le stesse proprieta' su tutti i punti di un tratto.
 *
 * Immutabile: una modifica (spostare, tagliare) ne fa una copia. I tratti di un disegno condividono i loro punti fra le
 * versioni della cronologia, quindi annullare non costa una copia del disegno.
 */
@Immutable
class FluidInkPoints internal constructor(
  internal val data: FloatArray,
  /** Quanti punti. */
  val size: Int,
  val hasPressure: Boolean,
  val hasTilt: Boolean,
  val hasOrientation: Boolean,
) {
  fun x(index: Int): Float = data[index * Stride]
  fun y(index: Int): Float = data[index * Stride + 1]

  /** Il tempo del punto, in ms dall'inizio del tratto. */
  fun time(index: Int): Float = data[index * Stride + 2]
  fun pressure(index: Int): Float = data[index * Stride + 3]
  fun tilt(index: Int): Float = data[index * Stride + 4]
  fun orientation(index: Int): Float = data[index * Stride + 5]

  /** Il riquadro dei centri dei punti (senza lo spessore). */
  val bounds: Rect by lazy(LazyThreadSafetyMode.NONE) {
    if (size == 0) return@lazy Rect.Zero
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (i in 0 until size) {
      val x = x(i)
      val y = y(i)
      left = min(left, x); right = max(right, x)
      top = min(top, y); bottom = max(bottom, y)
    }
    Rect(left, top, right, bottom)
  }

  /** La lunghezza del percorso. */
  val length: Float by lazy(LazyThreadSafetyMode.NONE) {
    var total = 0f
    for (i in 1 until size) total += InkGeometry.distance(x(i - 1), y(i - 1), x(i), y(i))
    total
  }

  /** Gli stessi punti spostati di ([dx], [dy]). */
  fun translated(dx: Float, dy: Float): FluidInkPoints {
    val copy = data.copyOf(size * Stride)
    for (i in 0 until size) {
      copy[i * Stride] += dx
      copy[i * Stride + 1] += dy
    }
    return FluidInkPoints(copy, size, hasPressure, hasTilt, hasOrientation)
  }

  /** I punti da [from] (compreso) a [to] (escluso), coi tempi che ripartono da zero. */
  fun slice(from: Int, to: Int): FluidInkPoints {
    val count = (to - from).coerceAtLeast(0)
    val copy = FloatArray(count * Stride)
    System.arraycopy(data, from * Stride, copy, 0, count * Stride)
    val t0 = if (count > 0) copy[2] else 0f
    for (i in 0 until count) copy[i * Stride + 2] -= t0
    return FluidInkPoints(copy, count, hasPressure, hasTilt, hasOrientation)
  }

  /** La pressione media, o [None] se il tratto non ne ha. */
  fun averagePressure(): Float {
    if (!hasPressure || size == 0) return None
    var sum = 0f
    for (i in 0 until size) sum += pressure(i)
    return sum / size
  }

  companion object {
    /** I valori per punto: x, y, tempo, pressione, inclinazione, orientamento. */
    const val Stride: Int = 6

    /** Il valore di una proprieta' che il dispositivo non da' (lo stesso di `StrokeInput.NO_PRESSURE` di androidx.ink). */
    const val None: Float = -1f

    val Empty: FluidInkPoints = FluidInkPoints(FloatArray(0), 0, hasPressure = false, hasTilt = false, hasOrientation = false)

    /**
     * Punti da coppie x, y, a 8 ms l'uno dall'altro (un tratto scritto dall'app o una prova). Senza pressione ne'
     * inclinazione.
     */
    fun of(vararg xy: Float): FluidInkPoints {
      require(xy.size % 2 == 0) { "Le coordinate vanno a coppie" }
      val builder = FluidInkPointsBuilder(xy.size / 2)
      for (i in 0 until xy.size / 2) builder.add(xy[i * 2], xy[i * 2 + 1], i * 8f)
      return builder.build()
    }
  }
}

/**
 * Costruisce i [FluidInkPoints] di un tratto che arriva: un punto alla volta, senza allocare per punto (la memoria
 * raddoppia quando serve). Pulisce l'ingresso come vuole androidx.ink: un punto nello stesso posto e nello stesso
 * istante del precedente si scarta, un tempo che torna indietro si allinea al precedente, e le proprieta' facoltative
 * valgono per tutto il tratto come il primo punto le ha date.
 */
class FluidInkPointsBuilder(initialCapacity: Int = 64) {
  private var data = FloatArray(initialCapacity.coerceAtLeast(4) * FluidInkPoints.Stride)
  var size: Int = 0
    private set
  private var pressure: Boolean? = null
  private var tilt: Boolean? = null
  private var orientation: Boolean? = null

  val lastX: Float get() = if (size == 0) Float.NaN else data[(size - 1) * FluidInkPoints.Stride]
  val lastY: Float get() = if (size == 0) Float.NaN else data[(size - 1) * FluidInkPoints.Stride + 1]
  val lastTime: Float get() = if (size == 0) 0f else data[(size - 1) * FluidInkPoints.Stride + 2]

  /** Aggiunge un punto; falso se e' stato scartato (doppione). */
  fun add(
    x: Float,
    y: Float,
    timeMillis: Float,
    pressure: Float = FluidInkPoints.None,
    tilt: Float = FluidInkPoints.None,
    orientation: Float = FluidInkPoints.None,
  ): Boolean {
    if (x.isNaN() || y.isNaN()) return false
    val hasP = this.pressure ?: (pressure >= 0f).also { this.pressure = it }
    val hasT = this.tilt ?: (tilt >= 0f).also { this.tilt = it }
    val hasO = this.orientation ?: (orientation >= 0f).also { this.orientation = it }
    val time = if (size == 0) timeMillis.coerceAtLeast(0f) else max(timeMillis, lastTime)
    if (size > 0 && x == lastX && y == lastY && time == lastTime) return false
    ensure(size + 1)
    val o = size * FluidInkPoints.Stride
    data[o] = x
    data[o + 1] = y
    data[o + 2] = time
    data[o + 3] = if (hasP) pressure.coerceIn(0f, 1f) else FluidInkPoints.None
    data[o + 4] = if (hasT) tilt.coerceIn(0f, (Math.PI / 2).toFloat()) else FluidInkPoints.None
    data[o + 5] = if (hasO) orientation.coerceIn(0f, (Math.PI * 2).toFloat()) else FluidInkPoints.None
    size++
    return true
  }

  fun x(index: Int): Float = data[index * FluidInkPoints.Stride]
  fun y(index: Int): Float = data[index * FluidInkPoints.Stride + 1]
  fun time(index: Int): Float = data[index * FluidInkPoints.Stride + 2]
  fun pressure(index: Int): Float = data[index * FluidInkPoints.Stride + 3]
  fun tilt(index: Int): Float = data[index * FluidInkPoints.Stride + 4]
  fun orientation(index: Int): Float = data[index * FluidInkPoints.Stride + 5]

  /** Toglie tutto, tenendo la memoria (il prossimo tratto non alloca). */
  fun clear() {
    size = 0
    pressure = null
    tilt = null
    orientation = null
  }

  /** I dati impacchettati come in [FluidInkPoints] (per disegnare il tratto mentre arriva, senza copiarlo). */
  internal val packed: FloatArray get() = data
  internal val knowsPressure: Boolean get() = pressure == true

  fun build(): FluidInkPoints = FluidInkPoints(
    data.copyOf(size * FluidInkPoints.Stride), size,
    hasPressure = pressure == true, hasTilt = tilt == true, hasOrientation = orientation == true,
  )

  private fun ensure(points: Int) {
    val need = points * FluidInkPoints.Stride
    if (need <= data.size) return
    data = data.copyOf(max(need, data.size * 2))
  }
}

/**
 * Un tratto finito: lo strumento, il colore (con la sua opacita'), lo spessore nelle unita' del foglio, i punti e da
 * dove sono arrivati. [shape] dice se il tratto e' una forma riconosciuta. L'identita' ([id]) resta uguale quando il
 * tratto si sposta col lazo; una gomma a pixel che lo taglia ne fa dei tratti nuovi, con identita' nuove.
 */
@Immutable
class FluidInkStroke(
  val id: Long,
  val tool: FluidInkTool,
  val color: Color,
  val size: Float,
  val points: FluidInkPoints,
  val input: FluidInkInput = FluidInkInput.Unknown,
  val shape: FluidInkShapeKind? = null,
) {
  init {
    require(tool.isInking) { "Solo uno strumento che disegna lascia un tratto: $tool" }
    require(size > 0f) { "Lo spessore di un tratto e' positivo: $size" }
  }

  /** Il riquadro che il tratto copre, spessore compreso. */
  val bounds: Rect by lazy(LazyThreadSafetyMode.NONE) {
    val b = points.bounds
    val half = size / 2f * InkGeometry.MaxSizeGain
    Rect(b.left - half, b.top - half, b.right + half, b.bottom + half)
  }

  fun copy(
    id: Long = this.id,
    tool: FluidInkTool = this.tool,
    color: Color = this.color,
    size: Float = this.size,
    points: FluidInkPoints = this.points,
    input: FluidInkInput = this.input,
    shape: FluidInkShapeKind? = this.shape,
  ): FluidInkStroke = FluidInkStroke(id, tool, color, size, points, input, shape)

  override fun toString(): String = "FluidInkStroke(id=$id, $tool, ${points.size} punti, size=$size, shape=$shape)"
}
