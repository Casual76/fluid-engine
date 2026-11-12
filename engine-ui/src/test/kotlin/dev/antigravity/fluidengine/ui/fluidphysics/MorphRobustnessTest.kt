package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.glass.field.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.*

/** Orologio senza attese reali: ammette anche due fotogrammi allo stesso istante. */
private class MorphRobustnessClock : MonotonicFrameClock {
  val frames = Channel<Long>(Channel.UNLIMITED)
  override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class MorphRobustnessTest(private val case: MorphRobustnessCases.Case, private val variant: String, private val hz: Int, private val dark: Boolean) {
  companion object {
    @JvmStatic @Parameterized.Parameters(name = "{index}/{1}/{2}Hz/scuro={3}")
    fun cases() = MorphRobustnessCases.all.flatMap { c ->
      val base = listOf("diretto", "inverti25", "inverti50", "inverti75", "terza25", "terza50", "terza75", "doppio", "irregolare", "zero")
      (base + "goccia" + base.drop(1).map { "goccia-$it" } + listOf("goccia-interrompi25","goccia-interrompi50","goccia-interrompi75")).flatMap { v ->
        listOf(60,120).flatMap { hz -> listOf(false,true).map { arrayOf<Any>(c,v,hz,it) } }
      }
    }
  }
  @Test fun viaggio() {
    val key = "${case.name}/$variant/${hz}/${if (dark) "scuro" else "chiaro"}"
    val result = MorphRobustnessRun.execute(case,variant,hz,dark)
    val issues = result.issues
    println("ORACOLO|$key|raffinati=${result.refinedAway}")
    issues.forEach { (kind, detail) -> println("RILIEVO|$key|$kind|$detail") }
    val unknown = issues.keys - MorphRobustnessKnown.issues[key].orEmpty()
    assertTrue("NON_NOTI|$key|${unknown.sorted().joinToString(",")}", unknown.isEmpty())
    println("ESITO|$key|${if (issues.isEmpty()) "PASS" else "NOTO"}|${MorphRobustnessKnown.classification(key,issues.keys)}")
    org.junit.Assume.assumeTrue("Noti dichiarati: ${issues.keys}", issues.isEmpty())
  }

  /** Visita anche array e oggetti degli uniform, inclusi gli slot inattivi; esclude path e cache Compose. */
  internal fun finite(root: Any) {
    val seen = java.util.IdentityHashMap<Any, Boolean>()
    fun visit(value: Any?) {
      if (value == null || seen.put(value, true) != null) return
      when (value) {
        is Float -> assertTrue("non finito: $value", value.isFinite())
        is Double -> assertTrue(value.isFinite())
        is androidx.compose.ui.geometry.Rect -> listOf(value.left,value.top,value.right,value.bottom).forEach { assertTrue(it.isFinite()) }
        is androidx.compose.ui.geometry.RoundRect -> {
          visit(androidx.compose.ui.geometry.Rect(value.left,value.top,value.right,value.bottom))
          listOf(value.topLeftCornerRadius,value.topRightCornerRadius,value.bottomRightCornerRadius,value.bottomLeftCornerRadius).forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite())
          }
        }
        is FloatArray -> value.forEach { assertTrue("array non finito", it.isFinite()) }
        else -> if (value.javaClass.name.startsWith("dev.antigravity.fluidengine.ui.glass.field.") || value is PhysicsRenderPlan) {
          value.javaClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach {
            it.isAccessible = true
            visit(it.get(value))
          }
        }
      }
    }
    visit(root)
  }

  internal fun metrics(p: PhysicsRenderPlan, validateContour: Boolean = true, subdivisions: Int = 24): FloatArray {
    if (p.mode == PlanModePoly) {
      val pts = List(p.vertCount) { Offset(p.verts[it * 2], p.verts[it * 2 + 1]) }
      val area = signedArea(pts)
      if (validateContour) assertTrue("contorno rovesciato", area >= -0.001f)
      fun cross(a: Offset, b: Offset, c: Offset) = (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
      if (validateContour) for (i in pts.indices) for (j in i + 2 until pts.size) {
        if (i == 0 && j == pts.lastIndex) continue
        val a = pts[i]; val b = pts[(i+1)%pts.size]; val c = pts[j]; val d = pts[(j+1)%pts.size]
        assertFalse("contorno autointersecato: $i/$j", cross(a,b,c)*cross(a,b,d) < -0.001f && cross(c,d,a)*cross(c,d,b) < -0.001f)
      }
      var x = 0f; var y = 0f
      for (i in pts.indices) {
        val a = pts[i]; val b = pts[(i+1)%pts.size]
        val k = a.x*b.y-b.x*a.y
        x += (a.x+b.x)*k; y += (a.y+b.y)*k
      }
      return floatArrayOf(abs(area), if (abs(area) > .001f) x/(6*area) else p.bounds.center.x,
        if (abs(area) > .001f) y/(6*area) else p.bounds.center.y)
    }
    // Unione esatta dei riquadri, senza contare due volte i pezzi duplicati alle scissioni.
    val xs = mutableListOf<Float>(); val ys = mutableListOf<Float>()
    for (i in 0 until p.pieceCount) {
      val k=i*4; val w=p.pieceRects[k+2]; val h=p.pieceRects[k+3]
      assertTrue(w >= 0 && h >= 0)
      for (r in 0..3) assertTrue(p.pieceRadii[k+r] in 0f..min(w,h))
      assertTrue(p.fieldRadii[k]+p.fieldRadii[k+1] <= 2*w+.001f)
      assertTrue(p.fieldRadii[k]+p.fieldRadii[k+3] <= 2*h+.001f)
      assertTrue(p.fieldRadii[k+2]+p.fieldRadii[k+3] <= 2*w+.001f)
      assertTrue(p.fieldRadii[k+1]+p.fieldRadii[k+2] <= 2*h+.001f)
      xs += p.pieceRects[k]-w; xs += p.pieceRects[k]+w
      ys += p.pieceRects[k+1]-h; ys += p.pieceRects[k+1]+h
    }
    // Integrazione deterministica del campo vero, inclusi angoli e colli: errore di quadratura,
    // non l'area dei riquadri (che salterebbe del 21% passando da cerchio a anello).
    val field = GlassFieldPlan(GlassFieldMaxPieces)
    physicsFieldPlan(p, 1f, field)
    val box = p.bounds
    // Il collo nuovo resta vivo anche su pezzi compenetrati: il campo pu? uscire dai
    // riquadri delle lastre. La quadratura deve coprire anche questa fascia, non troncarla.
    val margin = field.blend.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    xs += box.left-margin; xs += box.right+margin
    ys += box.top-margin; ys += box.bottom+margin
    var area=0f; var mx=0f; var my=0f
    // Celle delimitate dai pezzi: un tasto di 44 px a 70000 px dagli altri non sparisce
    // fra due campioni di una griglia uniforme sul riquadro intero.
    val xx=xs.distinct().sorted(); val yy=ys.distinct().sorted()
    val n=subdivisions
    for (u in 0 until xx.size-1) for (v in 0 until yy.size-1) {
      val w=xx[u+1]-xx[u]; val h=yy[v+1]-yy[v]
      val cell=w*h/(n*n)
      for (i in 0 until n) for (j in 0 until n) {
        val x=xx[u]+(i+.5f)*w/n; val y=yy[v]+(j+.5f)*h/n
        if (GlassFieldGeometry.fieldDistance(field.count,field.rects,field.radii,field.blend,field.smooth,x,y) <= 0f) {
          area+=cell; mx+=cell*x; my+=cell*y
        }
      }
    }
    return floatArrayOf(area, if(area>0) mx/area else box.center.x, if(area>0) my/area else box.center.y)
  }
}
