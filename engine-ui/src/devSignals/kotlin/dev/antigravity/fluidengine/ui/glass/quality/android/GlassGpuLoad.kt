package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi

/**
 * F7.26 · **Il carico finto della GPU** del profilo simulato ([GlassSimulation.gpuLoad]), solo nelle build di prova.
 *
 * Una View a tutta finestra sopra il contenuto, nel decor, che disegna uno shader invisibile: un ciclo di seni e coseni
 * per pixel, moltiplicato per zero alla fine (un uniform, quindi il compilatore non lo può togliere). Esce trasparente e
 * non cambia un pixel, ma ogni fotogramma che ridisegna quella zona lo paga sulla GPU: così un tablet veloce fa la parte
 * di un telefono lento, e la politica scende per i ritardi misurati dalle metriche vere. Il costo cresce con le
 * [iterazioni][GlassSimulation.gpuLoad] e con l'area ridisegnata (in moto, quasi tutto lo schermo).
 *
 * Non riceve tocchi (non è cliccabile), non c'è per TalkBack, non entra nelle catture del vetro (sta sopra il
 * contenuto, fuori da ogni sorgente). Sotto Android 13 non c'è AGSL e non fa niente.
 */
internal object GlassGpuLoad {
  const val MaxIterations = 1024

  private const val Source = """
    uniform float iterations;
    uniform float zero;
    half4 main(float2 p) {
      float acc = 0.0;
      for (int i = 0; i < 1024; i++) {
        if (float(i) >= iterations) { break; }
        float f = float(i);
        acc += sin(p.x * 0.0131 + f * 0.7) * cos(p.y * 0.0173 - f * 0.3);
      }
      return half4(acc * zero);
    }
  """

  /** La vista del carico della prima Activity, da ridisegnare ai confini delle fasi. */
  @Volatile var view: View? = null
    private set

  fun attach(activity: Activity, simulation: GlassSimulation) {
    if (Build.VERSION.SDK_INT < 33) return
    val decor = activity.window.decorView as? ViewGroup ?: return
    val load = LoadView(activity, simulation)
    decor.addView(load, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    view = load
  }

  @RequiresApi(33)
  private class LoadView(context: Context, private val simulation: GlassSimulation) : View(context) {
    private val shader = RuntimeShader(Source)
    private val paint = Paint().also { it.shader = shader }

    init {
      isClickable = false
      isFocusable = false
      importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
      setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
      val iterations = simulation.gpuLoadNow()
      if (iterations <= 0) return
      shader.setFloatUniform("iterations", iterations.toFloat())
      shader.setFloatUniform("zero", 0f)
      canvas.drawPaint(paint)
    }
  }
}
