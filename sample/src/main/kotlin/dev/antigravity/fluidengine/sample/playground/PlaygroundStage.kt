package dev.antigravity.fluidengine.sample.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface

/**
 * Il palco dell'editor: un fondale volutamente rumoroso, registrato come sorgente, e sopra il
 * pezzo di vetro che si trasforma.
 *
 * Il fondale è rumoroso per la stessa ragione dei riquadri della prima scheda: il vetro sopra il
 * grigio è invisibile per costruzione, e un motore di trasformazione dimostrato su un fondo piatto
 * dimostrerebbe soltanto una sagoma che cambia. Qui le righe si *piegano* dentro il bordo mentre il
 * bordo si sposta — che è l'intera tesi di Fluid-physics.
 */
@Composable
internal fun PlaygroundStage(
  state: FluidPhysicsState,
  tier: FluidPhysicsTier?,
  drawing: Boolean,
  aspectRatio: Float = 1f,
  onSized: (IntSize) -> Unit = {},
  onStrokeFinished: (List<Offset>) -> Unit = {},
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = {},
) {
  val stageBackdrop = rememberGlassBackdrop()
  val stroke = remember { mutableStateListOf<Offset>() }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(aspectRatio)
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .onSizeChanged(onSized),
  ) {
    StageBackdropContent(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(stageBackdrop),
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .fluidPhysicsSurface(
          state = state,
          backdrop = stageBackdrop,
          tint = GlassDefaults.floatingTint(),
          role = GlassRole.Floating,
          // Più lente del Floating di serie, apposta: questo palco esiste per far *vedere* il
          // materiale, e un pezzo da dimostrazione è l'unico posto dove la cupola intera
          // (depthEffect) è giusta anche in grande — il pezzo È la lente.
          optics = GlassDefaults.optics(GlassRole.Floating).copy(
            refractionHeight = 24.dp,
            refractionAmount = 40.dp,
            depthEffect = true,
            highlightWidth = 1.2.dp,
            highlightAlpha = 0.75f,
          ),
          tier = tier,
        ),
    )

    content()

    if (drawing) {
      val accent = MaterialTheme.colorScheme.primary
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(Unit) {
            detectDragGestures(
              onDragStart = { start ->
                stroke.clear()
                stroke += start
              },
              onDrag = { change, _ ->
                change.consume()
                stroke += change.position
              },
              onDragEnd = {
                if (stroke.size > 2) onStrokeFinished(stroke.toList())
                stroke.clear()
              },
              onDragCancel = { stroke.clear() },
            )
          },
      ) {
        if (stroke.size > 1) {
          val path = Path()
          path.moveTo(stroke[0].x, stroke[0].y)
          for (i in 1 until stroke.size) path.lineTo(stroke[i].x, stroke[i].y)
          drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }
      }
    }
  }
}

/** Il fondale del palco: colore saturo con righe dure, così la dislocazione si legge come tale. */
@Composable
private fun StageBackdropContent(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .background(
        Brush.linearGradient(
          listOf(Color(0xFF007AFF), Color(0xFF5856D6), Color(0xFFAF52DE)),
        ),
      )
      .background(
        // Ripetuto, non un colpo solo: le righe devono coprire l'intero palco, perché sono loro a
        // rendere la dislocazione leggibile come dislocazione ovunque il pezzo si trovi.
        Brush.linearGradient(
          colors = List(18) { i -> if (i % 2 == 0) Color.White.copy(alpha = 0.22f) else Color.Transparent },
          start = Offset.Zero,
          end = Offset(260f, 260f),
          tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
        ),
      ),
  )
}
