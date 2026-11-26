package dev.antigravity.fluidengine.editor

import java.util.Locale

/**
 * La **ricetta** di una modifica: la geometria e le regolazioni, non i pixel. Il file di partenza non si tocca mai; la
 * foto modificata e' sempre un file nuovo, fatto da questa ricetta ([FluidImageExport]). Salvata accanto al file
 * ([encode]), riapre la modifica dov'era, e «Ripristina originale» e' semplicemente [Original] — come in Foto, dove una
 * foto modificata si riporta all'originale anche dopo mesi.
 *
 * @property autoAdjustments la parte di [adjustments] che ha messo «Auto», o null se «Auto» e' spento. Il righello mostra
 *   sempre il totale; spegnendo «Auto» si toglie solo la sua parte, e restano le modifiche fatte a mano.
 */
data class FluidImageEdit(
  val geometry: FluidCropGeometry = FluidCropGeometry(),
  val adjustments: FluidAdjustments = FluidAdjustments.Neutral,
  val autoAdjustments: FluidAdjustments? = null,
) {
  /** Vero se la ricetta lascia la foto com'e'. */
  val isOriginal: Boolean get() = geometry.isIdentity && adjustments.isNeutral

  /** Vero se «Auto» e' acceso. */
  val isAuto: Boolean get() = autoAdjustments != null

  /** La stessa ricetta con [adjustment] a [value] (il totale che mostra il righello). */
  fun with(adjustment: FluidAdjustment, value: Float): FluidImageEdit =
    copy(adjustments = adjustments.with(adjustment, value))

  /** «Auto» acceso con i valori [suggestion] sommati a quelli fatti a mano, o spento togliendo solo la sua parte. */
  fun withAuto(suggestion: FluidAdjustments?): FluidImageEdit {
    var manual = adjustments
    autoAdjustments?.let { old -> FluidAdjustment.All.forEach { manual = manual.with(it, manual[it] - old[it]) } }
    if (suggestion == null) return copy(adjustments = manual, autoAdjustments = null)
    var total = manual
    FluidAdjustment.All.forEach { total = total.with(it, total[it] + suggestion[it]) }
    return copy(adjustments = total, autoAdjustments = suggestion)
  }

  /**
   * La ricetta in una riga di testo, da salvare accanto al file (un campo del database dell'app, un file `.fluidedit`).
   * Una versione futura dell'engine la rilegge; una regolazione che questa versione non conosce si ignora.
   */
  fun encode(): String = buildString {
    append(Header)
    val g = geometry
    append(";q=").append(g.quarterTurns)
    append(";f=").append(if (g.flipped) 1 else 0)
    append(";a=").append(num(g.angle))
    append(";c=").append(num(g.centerX)).append(',').append(num(g.centerY)).append(',')
      .append(num(g.width)).append(',').append(num(g.height))
    append(";r=").append(g.aspect.id)
    if (!adjustments.isNeutral) append(";adj=").append(values(adjustments))
    autoAdjustments?.let { append(";auto=").append(values(it)) }
  }

  companion object {
    /** La foto com'e': nessuna modifica. */
    val Original = FluidImageEdit()

    private const val Header = "fluid-edit/1"

    /** Rilegge una ricetta scritta da [encode]; null se il testo non e' una ricetta. */
    fun decode(text: String): FluidImageEdit? {
      val parts = text.trim().split(';')
      if (parts.firstOrNull()?.startsWith("fluid-edit/") != true) return null
      val fields = parts.drop(1).mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }
        .toMap()
      return runCatching {
        val c = fields["c"]?.split(',')?.map { it.toFloat() } ?: listOf(0.5f, 0.5f, 1f, 1f)
        val geometry = FluidCropGeometry(
          quarterTurns = Math.floorMod(fields["q"]?.toInt() ?: 0, 4),
          flipped = fields["f"] == "1",
          angle = (fields["a"]?.toFloat() ?: 0f).coerceIn(-45f, 45f),
          centerX = c[0].coerceIn(0f, 1f),
          centerY = c[1].coerceIn(0f, 1f),
          width = c[2].coerceIn(0.001f, 1f),
          height = c[3].coerceIn(0.001f, 1f),
          aspect = fields["r"]?.let { FluidCropAspect.parse(it) } ?: FluidCropAspect.Freeform,
        )
        FluidImageEdit(geometry, parseValues(fields["adj"]) ?: FluidAdjustments.Neutral, parseValues(fields["auto"]))
      }.getOrNull()
    }

    private fun num(v: Float): String = String.format(Locale.ROOT, "%.5f", v).trimEnd('0').trimEnd('.')

    private fun values(a: FluidAdjustments): String =
      a.changed.joinToString(",") { "${it.id}:${num(a[it])}" }

    private fun parseValues(text: String?): FluidAdjustments? {
      if (text == null) return null
      var out = FluidAdjustments.Neutral
      text.split(',').forEach { pair ->
        val i = pair.indexOf(':')
        if (i <= 0) return@forEach
        val adjustment = FluidAdjustment.byId(pair.substring(0, i)) ?: return@forEach
        pair.substring(i + 1).toFloatOrNull()?.let { out = out.with(adjustment, it) }
      }
      return out
    }
  }
}

/**
 * Annulla e ripristina: la cronologia delle ricette, un passo per gesto finito (un righello lasciato, una maniglia
 * lasciata, un tasto), non un passo per fotogramma. Come in Foto, «Ripristina originale» e' un passo come gli altri: si
 * annulla.
 */
class FluidEditHistory(initial: FluidImageEdit = FluidImageEdit.Original, private val limit: Int = 100) {
  private val undo = ArrayDeque<FluidImageEdit>()
  private val redo = ArrayDeque<FluidImageEdit>()

  /** La ricetta di adesso. */
  var current: FluidImageEdit = initial
    private set

  val canUndo: Boolean get() = undo.isNotEmpty()
  val canRedo: Boolean get() = redo.isNotEmpty()

  /** Un passo nuovo; se e' uguale a quello di adesso non succede niente. Toglie i passi da ripristinare. */
  fun commit(edit: FluidImageEdit): Boolean {
    if (edit == current) return false
    undo.addLast(current)
    if (undo.size > limit) undo.removeFirst()
    redo.clear()
    current = edit
    return true
  }

  fun undo(): FluidImageEdit? {
    val previous = undo.removeLastOrNull() ?: return null
    redo.addLast(current)
    current = previous
    return previous
  }

  fun redo(): FluidImageEdit? {
    val next = redo.removeLastOrNull() ?: return null
    undo.addLast(current)
    current = next
    return next
  }
}
