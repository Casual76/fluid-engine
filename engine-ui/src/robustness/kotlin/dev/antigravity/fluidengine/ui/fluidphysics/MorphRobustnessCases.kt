package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.sin

/** Catalogo fisso F1B.8, condiviso dal banco JVM e dalla galleria. Coordinate in px (densità 1 nel banco). */
object MorphRobustnessCases {
  data class Case(val name: String, val from: FluidForm, val to: FluidForm)

  private fun slab(w: Float, h: Float, r: Float = 12f, x: Float = 40f, y: Float = 40f) =
    FluidForm.Slab(Rect(x, y, x + w, y + h), FluidCornerRadii.all(r))
  private val panel = slab(240f, 180f)
  private val pill = slab(180f, 44f, 22f)
  private val circle = FluidForm.circle(Offset(120f, 120f), 40f)
  private fun poly(vararg xy: Float) = FluidForm.Poly(Rect(40f, 40f, 280f, 280f), xy.toList().chunked(2).map { Offset(it[0], it[1]) })
  private fun star(n: Int) = FluidForm.Poly(Rect(40f, 40f, 280f, 280f), List(n * 2) {
    val a = it * Math.PI / n - Math.PI / 2
    val r = if (it % 2 == 0) .5 else .21
    Offset((.5 + cos(a) * r).toFloat(), (.5 + sin(a) * r).toFloat())
  })
  private val triangle = poly(.5f, 0f, 1f, 1f, 0f, 1f)
  private val concave = poly(0f, 0f, 1f, 0f, 1f, .3f, .3f, .3f, .3f, 1f, 0f, 1f)
  private val collinear = poly(0f, 0f, .25f, .00001f, .5f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
  private fun group(n: Int, spacing: Float = 62f, varied: Boolean = false) = FluidForm.Group(List(n) {
    slab(if (varied) 12f + it * 13 else 44f, if (varied) 20f + it * 9 else 44f,
      9f, 20f + it * spacing, 100f)
  })
  val third: FluidForm = slab(110f, 160f, 20f, 170f, 10f)
  /** Ogni coppia non identica viene percorsa in entrambi i versi, senza estrazioni casuali. */
  val all: List<Case> = buildList {
    fun pair(name: String, a: FluidForm, b: FluidForm) {
      add(Case(name, a, b))
      if (a != b) add(Case("$name-inverso", b, a))
    }
    pair("lastra-capsula", panel, pill)
    pair("capsula-cerchio", pill, circle)
    pair("cerchio-lastra", circle, panel)
    pair("taglia-24-600", slab(24f, 24f), slab(600f, 600f))
    pair("proporzioni-1-12", slab(24f, 288f), slab(288f, 24f))
    pair("raggi-asimmetrici", panel.copy(cornerRadii = FluidCornerRadii(2f, 30f, 70f, 0f)), pill)
    pair("raggio-oltre-meta", slab(120f, 40f, 35f), panel)
    pair("larghezza-zero", slab(0f, 80f, 0f), panel)
    pair("altezza-zero", slab(80f, 0f, 0f), panel)
    pair("un-pixel", slab(1f, 1f, .5f), panel)
    pair("identica", panel, panel)
    pair("triangolo-stella5", triangle, star(5))
    pair("stella5-stella12", star(5), star(12))
    pair("concavo-collineare", concave, collinear)
    pair("poly-lastra", star(12), panel)
    pair("uno-due", circle, group(2))
    pair("uno-otto", circle, group(8))
    pair("tre-cinque", group(3), group(5))
    pair("taglie-diverse", group(8, varied = true), panel)
    pair("sovrapposti", group(5, 8f), panel)
    pair("lontanissimi", group(8, 10000f), panel)
    pair("gruppo-poly", group(3), concave)
  }
}
