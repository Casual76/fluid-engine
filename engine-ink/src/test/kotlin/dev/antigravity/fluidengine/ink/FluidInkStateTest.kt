package dev.antigravity.fluidengine.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.16 · Il modello: annulla e ripristina, la gomma a oggetti e a pixel, il lazo, un gesto = un passo. */
class FluidInkStateTest {
  private fun line(state: FluidInkState, x0: Float, y0: Float, x1: Float, y1: Float, tool: FluidInkTool = FluidInkTool.Pen): FluidInkStroke {
    val b = FluidInkPointsBuilder()
    val steps = 20
    for (i in 0..steps) b.add(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, i * 8f)
    return FluidInkStroke(state.newStrokeId(), tool, Color.Black, 4f, b.build(), FluidInkInput.Touch)
  }

  @Test fun undoAndRedoWalkTheHistory() {
    val s = FluidInkState()
    assertFalse(s.canUndo)
    s.add(line(s, 0f, 0f, 100f, 0f))
    s.add(line(s, 0f, 50f, 100f, 50f))
    assertEquals(2, s.strokes.size)
    assertTrue(s.canUndo)
    s.undo()
    assertEquals(1, s.strokes.size)
    assertTrue(s.canRedo)
    s.redo()
    assertEquals(2, s.strokes.size)
    s.undo(); s.undo()
    assertTrue(s.isEmpty)
    assertFalse(s.canUndo)
    // Un tratto nuovo dopo un annulla toglie il futuro, come in PencilKit.
    s.add(line(s, 0f, 0f, 10f, 10f))
    assertFalse(s.canRedo)
  }

  @Test fun clearIsOneStepThatCanBeUndone() {
    val s = FluidInkState()
    repeat(3) { s.add(line(s, 0f, it * 20f, 100f, it * 20f)) }
    s.clear()
    assertTrue(s.isEmpty)
    s.undo()
    assertEquals(3, s.strokes.size)
  }

  @Test fun theObjectEraserRemovesWholeStrokesInOneStep() {
    val s = FluidInkState()
    s.add(line(s, 0f, 0f, 100f, 0f))
    s.add(line(s, 0f, 100f, 100f, 100f))
    val revision = s.revision
    s.beginEdit()
    // La gomma passa in verticale sul primo tratto soltanto, in tre pezzi (tre fotogrammi del gesto).
    s.eraseObjects(Offset(50f, -30f), Offset(50f, -10f), 6f)
    s.eraseObjects(Offset(50f, -10f), Offset(50f, 10f), 6f)
    s.eraseObjects(Offset(50f, 10f), Offset(50f, 30f), 6f)
    s.endEdit()
    assertEquals(1, s.strokes.size)
    assertEquals(100f, s.strokes.single().points.y(0), 0.01f)
    assertNotEquals(revision, s.revision)
    // Un gesto, un passo.
    s.undo()
    assertEquals(2, s.strokes.size)
  }

  @Test fun theAreaEraserCutsAStrokeInTwo() {
    val s = FluidInkState()
    val original = line(s, 0f, 0f, 200f, 0f)
    s.add(original)
    s.beginEdit()
    assertTrue(s.eraseArea(Offset(100f, -20f), Offset(100f, 20f), 10f))
    s.endEdit()
    assertEquals(2, s.strokes.size)
    val (a, b) = s.strokes
    assertTrue(a.id != original.id && b.id != original.id && a.id != b.id)
    // Il taglio cade attorno alla gomma: un pezzo finisce prima di x = 100 - 10, l'altro comincia dopo x = 100 + 10.
    assertTrue(a.points.bounds.right < 95f)
    assertTrue(b.points.bounds.left > 105f)
    assertEquals(0f, a.points.bounds.left, 0.01f)
    assertEquals(200f, b.points.bounds.right, 0.01f)
    s.undo()
    assertEquals(original.id, s.strokes.single().id)
  }

  @Test fun anEraserThatMissesChangesNothing() {
    val s = FluidInkState()
    val stroke = line(s, 0f, 0f, 100f, 0f)
    s.add(stroke)
    val revision = s.revision
    assertFalse(s.eraseArea(Offset(50f, 60f), Offset(60f, 60f), 5f))
    assertFalse(s.eraseObjects(Offset(50f, 60f), Offset(60f, 60f), 5f))
    assertEquals(revision, s.revision)
    assertTrue(s.strokes.single() === stroke)
  }

  @Test fun theLassoTakesWhatItSurrounds() {
    val s = FluidInkState()
    val inside = line(s, 20f, 20f, 60f, 20f)
    val outside = line(s, 200f, 200f, 260f, 200f)
    val half = line(s, 70f, 50f, 190f, 50f) // poco dentro: non lo prende
    s.add(inside); s.add(outside); s.add(half)
    val lasso = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
    assertTrue(s.selectInLasso(lasso))
    assertEquals(setOf(inside.id), s.selection)
    // Spostare la selezione e' un passo solo, anche se arriva in tanti pezzi.
    s.beginEdit()
    repeat(10) { s.moveSelection(1f, 2f) }
    s.endEdit()
    val moved = s.strokes.first { it.id == inside.id }
    assertEquals(30f, moved.points.x(0), 0.01f)
    assertEquals(40f, moved.points.y(0), 0.01f)
    s.undo()
    assertEquals(20f, s.strokes.first { it.id == inside.id }.points.x(0), 0.01f)
  }

  @Test fun duplicateAndDeleteTheSelection() {
    val s = FluidInkState()
    val a = line(s, 10f, 10f, 50f, 10f)
    s.add(a)
    s.select(setOf(a.id))
    s.duplicateSelection()
    assertEquals(2, s.strokes.size)
    val copy = s.strokes.last()
    assertNotEquals(a.id, copy.id)
    assertEquals(setOf(copy.id), s.selection)
    assertEquals(26f, copy.points.x(0), 0.01f)
    s.deleteSelection()
    assertEquals(listOf(a.id), s.strokes.map { it.id })
    assertTrue(s.selection.isEmpty())
  }

  @Test fun recolorKeepsTheHighlighterTransparent() {
    val s = FluidInkState()
    val marker = line(s, 0f, 0f, 50f, 0f, FluidInkTool.Highlighter).copy(color = Color(0xFFFFCC00).copy(alpha = 0.5f))
    val pen = line(s, 0f, 20f, 50f, 20f)
    s.add(marker); s.add(pen)
    s.select(setOf(marker.id, pen.id))
    s.recolorSelection(Color.Red)
    assertEquals(0.5f, s.strokes[0].color.alpha, 0.01f)
    assertEquals(Color.Red.red, s.strokes[0].color.red, 0.01f)
    assertEquals(Color.Red, s.strokes[1].color)
  }

  @Test fun theHistoryHasACeiling() {
    val s = FluidInkState()
    repeat(FluidInkDefaults.HistoryLimit + 30) { s.add(line(s, 0f, it.toFloat(), 10f, it.toFloat())) }
    var undos = 0
    while (s.canUndo) { s.undo(); undos++ }
    assertEquals(FluidInkDefaults.HistoryLimit, undos)
    assertEquals(30, s.strokes.size)
  }

  @Test fun cancelEditPutsEverythingBack() {
    val s = FluidInkState()
    s.add(line(s, 0f, 0f, 100f, 0f))
    s.beginEdit()
    s.eraseObjects(Offset(50f, -10f), Offset(50f, 10f), 5f)
    assertTrue(s.isEmpty)
    s.cancelEdit()
    assertEquals(1, s.strokes.size)
    s.undo()
    assertTrue(s.isEmpty)
  }

  @Test fun loadStartsANewHistory() {
    val s = FluidInkState()
    s.add(line(s, 0f, 0f, 1f, 1f))
    val other = FluidInkState()
    val strokes = List(3) { line(other, 0f, it * 5f, 10f, it * 5f) }
    s.load(strokes)
    assertFalse(s.canUndo)
    assertEquals(3, s.strokes.size)
    // Le identita' nuove non si scontrano con quelle caricate.
    assertTrue(s.newStrokeId() > strokes.maxOf { it.id })
  }

  @Test fun policyDecidesWhoDraws() {
    val s = FluidInkState()
    assertTrue(s.fingerDraws)
    s.stylusSeen = true
    assertFalse(s.fingerDraws)
    s.drawingPolicy = FluidInkDrawingPolicy.AnyInput
    assertTrue(s.fingerDraws)
    s.drawingPolicy = FluidInkDrawingPolicy.StylusOnly
    s.stylusSeen = false
    assertFalse(s.fingerDraws)
  }

  @Test fun theBuilderCleansTheInput() {
    val b = FluidInkPointsBuilder(2)
    assertTrue(b.add(0f, 0f, 0f, pressure = 0.5f))
    assertFalse(b.add(0f, 0f, 0f, pressure = 0.6f)) // doppione
    assertTrue(b.add(1f, 1f, 5f, pressure = 2f)) // pressione oltre 1: si ferma a 1
    assertTrue(b.add(2f, 2f, 3f)) // tempo all'indietro: si allinea, la pressione mancante resta del tratto
    val p = b.build()
    assertEquals(3, p.size)
    assertTrue(p.hasPressure)
    assertEquals(1f, p.pressure(1), 0f)
    assertEquals(5f, p.time(2), 0f)
    assertEquals(0f, p.pressure(2), 0f)
  }
}
