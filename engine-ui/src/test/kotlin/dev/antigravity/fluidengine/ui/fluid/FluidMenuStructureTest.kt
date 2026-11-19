package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * La struttura dei menu (4.0, F5A.3): l'ordine che si capovolge, la fila di simboli, il sottomenu, le righe col
 * sottotitolo, il cambio di livello. Tutto puro: le celle che il dito sceglie e quelle che il pannello disegna
 * vengono da qui, e un menu di sempre deve avere le stesse celle al bit.
 */
class FluidMenuStructureTest {

  private fun action(label: String, starts: Boolean = false, enabled: Boolean = true) =
    FluidContextAction(label = label, startsSection = starts, enabled = enabled) {}

  private val metrics = FluidMenuStructureMetrics(
    rowHeightPx = 100f,
    subtitleRowHeightPx = 140f,
    tileHeightPx = 120f,
    paddingPx = 10f,
    sectionGapPx = 20f,
  )

  private fun labels(entries: List<FluidMenuEntry>) = entries.map { it.action.label }
  private fun starts(entries: List<FluidMenuEntry>) = entries.map { it.startsSection }

  // --- Un menu di sempre --------------------------------------------------------------------------

  @Test
  fun aPlainMenuHasTheSameCellsAsBefore() {
    val actions = listOf(action("A"), action("B"), action("C", starts = true), action("D"))
    val entries = fluidMenuEntries(actions, FluidMenuElementSize.Large, reversed = false)
    assertEquals(listOf("A", "B", "C", "D"), labels(entries))
    assertEquals(listOf(false, false, true, false), starts(entries))
    assertEquals(listOf(0, 1, 2, 3), entries.map { it.source })

    val structure = fluidMenuStructure(entries, metrics)
    val layout = fluidMenuPanelLayout(actions.map { it.startsSection }, rowHeightPx = 100f, paddingPx = 10f, sectionGapPx = 20f)
    assertArrayEquals(layout.tops, structure.tops, 0f)
    assertArrayEquals(layout.sectionBands, structure.sectionBands, 0f)
    assertEquals(layout.height, structure.height, 0f)

    // Le celle del dito al bit, anche su un pannello posato a coordinate non intere.
    val panel = Rect(13.37f, 401.25f, 265.9f, 401.25f + layout.height)
    val enabled = actions.map { it.enabled }
    assertEquals(fluidMenuGeometryOf(layout, enabled, panel), structure.geometry(panel))
    assertEquals(structure.tops.toList(), fluidMenuStructureOfLayout(actions, layout).tops.toList())
    assertTrue(fluidMenuCellsUniform(structure))
  }

  @Test
  fun aPlainListIsAutomaticAndLarge() {
    val actions = listOf(action("A"))
    assertEquals(FluidMenuOrder.Automatic, fluidMenuOrderOf(actions))
    assertEquals(FluidMenuElementSize.Large, fluidMenuElementSizeOf(actions))
    assertEquals(actions, fluidMenuSnapshot(actions))
  }

  // --- L'ordine -----------------------------------------------------------------------------------

  @Test
  fun onlyPriorityFlipsAndOnlyUpward() {
    assertTrue(fluidMenuReverses(FluidMenuOrder.Priority, opensUpward = true))
    assertFalse(fluidMenuReverses(FluidMenuOrder.Priority, opensUpward = false))
    // Di serie l'ordine e' fisso: un menu gia' scritto non cambia ordine aggiornando l'engine.
    assertFalse(fluidMenuReverses(FluidMenuOrder.Automatic, opensUpward = true))
    assertFalse(fluidMenuReverses(FluidMenuOrder.Fixed, opensUpward = true))
  }

  @Test
  fun aMenuFromAButtonAtTheBottomOpensUpward() {
    val host = Size(1080f, 2400f)
    val low = Rect(500f, 2200f, 580f, 2280f)
    val high = Rect(900f, 100f, 980f, 180f)
    val up = solveFluidMenuPanel(low, host, 600f, 800f, 30f, 40f, FluidMenuPlacement.OverAnchor)
    val down = solveFluidMenuPanel(high, host, 600f, 800f, 30f, 40f, FluidMenuPlacement.OverAnchor)
    assertTrue(fluidMenuOpensUpward(low, up))
    assertFalse(fluidMenuOpensUpward(high, down))
    // La riga vicina al fondo: il contestuale si apre sopra di lei.
    val row = Rect(0f, 2100f, 1080f, 2250f)
    assertTrue(fluidMenuOpensUpward(row, solveFluidMenuPanel(row, host, 600f, 800f, 30f, 40f, FluidMenuPlacement.BesideAnchor)))
  }

  @Test
  fun priorityReversesSectionsAndTheActionsInsideThem() {
    val actions = listOf(action("A"), action("B"), action("C", starts = true), action("D"), action("E", starts = true))
    val entries = fluidMenuEntries(actions, FluidMenuElementSize.Large, reversed = true)
    // La prima voce scritta e' l'ultima che si vede, accanto al dito; le sezioni seguono.
    assertEquals(listOf("E", "D", "C", "B", "A"), labels(entries))
    assertEquals(listOf(false, true, false, true, false), starts(entries))
    assertEquals(listOf(4, 3, 2, 1, 0), entries.map { it.source })
    // Stessa altezza: il pannello risolto con l'ordine scritto vale anche capovolto.
    val written = fluidMenuStructure(fluidMenuEntries(actions, FluidMenuElementSize.Large, false), metrics)
    assertEquals(written.height, fluidMenuStructure(entries, metrics).height, 0f)
  }

  // --- La fila di simboli -------------------------------------------------------------------------

  @Test
  fun smallPutsUpToFourOfTheFirstSectionOnARow() {
    val actions = listOf(
      action("Taglia"), action("Copia"), action("Incolla"), action("Cerca"), action("Traduci"),
      action("Elimina", starts = true),
    )
    val entries = fluidMenuEntries(actions, FluidMenuElementSize.Small, reversed = false)
    assertEquals(
      listOf(FluidMenuCellKind.Tile, FluidMenuCellKind.Tile, FluidMenuCellKind.Tile, FluidMenuCellKind.Tile, FluidMenuCellKind.Row, FluidMenuCellKind.Row),
      entries.map { it.kind },
    )
    // La voce della prima sezione che non ci sta continua nella lista, staccata dalla fila da una banda.
    assertEquals(listOf(false, false, false, false, true, true), starts(entries))

    val s = fluidMenuStructure(entries, metrics)
    assertEquals(10f, s.tops[0], 0f)
    assertEquals(listOf(0, 1, 2, 3), (0..3).map { s.columns[it] })
    assertTrue((0..3).all { s.columnCounts[it] == 4 && s.heights[it] == 120f })
    // fila (120) + banda + riga + banda + riga + padding
    assertEquals(10f + 120f + 20f + 100f + 20f + 100f + 10f, s.height, 0.001f)
    assertEquals(Rect(100f, 10f, 200f, 130f), s.cell(1, 400f))
    assertTrue(s.sameLine(1, 2))
    assertFalse(s.sameLine(3, 4))
    assertFalse(fluidMenuCellsUniform(s))

    val geometry = s.geometry(Rect(50f, 500f, 450f, 500f + s.height))
    assertEquals(Rect(150f, 510f, 250f, 630f), geometry.cells[1])
    assertEquals(1, fluidMenuCellAt(geometry, geometry.cells[1].center))
  }

  @Test
  fun mediumTakesThreeAndLargeNone() {
    val actions = listOf(action("A"), action("B"), action("C"), action("D"))
    assertEquals(3, fluidMenuEntries(actions, FluidMenuElementSize.Medium, false).count { it.kind == FluidMenuCellKind.Tile })
    assertEquals(0, fluidMenuEntries(actions, FluidMenuElementSize.Large, false).count { it.kind == FluidMenuCellKind.Tile })
    // Una prima sezione corta: la fila ha solo quelle voci.
    val short = listOf(action("A"), action("B", starts = true), action("C"))
    assertEquals(1, fluidMenuEntries(short, FluidMenuElementSize.Small, false).count { it.kind == FluidMenuCellKind.Tile })
  }

  @Test
  fun aRowAloneIsTheWholeMenu() {
    val actions = listOf(action("A"), action("B"))
    val s = fluidMenuStructure(fluidMenuEntries(actions, FluidMenuElementSize.Small, false), metrics)
    assertEquals(10f + 120f + 10f, s.height, 0.001f)
    assertEquals(0, s.sectionBands.size)
  }

  @Test
  fun aFlippedMenuCarriesItsRowToTheBottom() {
    val actions = listOf(action("Taglia"), action("Copia"), action("Elimina", starts = true), action("Info"))
    val entries = fluidMenuEntries(actions, FluidMenuElementSize.Small, reversed = true)
    // La lista capovolta in cima, la fila in fondo (vicino al dito) con le tessere nell'ordine scritto.
    assertEquals(listOf("Info", "Elimina", "Taglia", "Copia"), labels(entries))
    assertEquals(listOf(false, false, true, false), starts(entries))
    val s = fluidMenuStructure(entries, metrics)
    assertTrue(s.sameLine(2, 3))
    assertEquals(s.height - 10f - 120f, s.tops[2], 0.001f)
  }

  @Test
  fun theDropTakesTheSizeOfTheCellItIsOn() {
    val actions = listOf(action("A"), action("B"), action("C", starts = true))
    val s = fluidMenuStructure(fluidMenuEntries(actions, FluidMenuElementSize.Small, false), metrics)
    assertEquals(Size(200f, 120f), fluidMenuDropCellSize(s, 400f, 0f))
    assertEquals(Size(400f, 100f), fluidMenuDropCellSize(s, 400f, 2f))
    // A meta' strada fra una tessera e una riga: a meta' misura, senza scatti.
    assertEquals(Size(300f, 110f), fluidMenuDropCellSize(s, 400f, 1.5f))
    assertEquals(Size(400f, 100f), fluidMenuDropCellSize(s, 400f, 9f))
  }

  // --- Il sottotitolo -----------------------------------------------------------------------------

  @Test
  fun aSubtitleMakesItsRowTaller() {
    val actions = listOf(action("A"), FluidContextAction("Ordina", subtitle = "Per data") {}, action("C"))
    val s = fluidMenuStructure(fluidMenuEntries(actions, FluidMenuElementSize.Large, false), metrics)
    assertEquals(listOf(100f, 140f, 100f), s.heights.toList())
    assertEquals(listOf(10f, 110f, 250f), s.tops.toList())
    assertFalse(fluidMenuCellsUniform(s))
    // 23 + 21 + 16 = 60 dp; mai meno di una riga di sempre.
    assertEquals(60f, fluidMenuSubtitleRowHeight(23.dp, 21.dp).value, 0.001f)
    assertEquals(48f, fluidMenuSubtitleRowHeight(10.dp, 10.dp).value, 0.001f)
    assertEquals(68f, fluidMenuMediumTileHeight(18.dp).value, 0.001f)
    assertEquals(64f, fluidMenuMediumTileHeight(10.dp).value, 0.001f)
  }

  // --- Il sottomenu -------------------------------------------------------------------------------

  @Test
  fun aSubmenuHasItsParentOnTopAndOneLevelOnly() {
    val grandchild = action("Nipote")
    val parent = FluidContextAction(
      label = "Ordina",
      children = listOf(action("Per data"), FluidContextAction("Per nome", children = listOf(grandchild)) {}, action("Per taglia", starts = true)),
    ) {}
    val root = fluidMenuEntries(listOf(parent, action("Elimina", starts = true)), FluidMenuElementSize.Large, false)
    assertTrue(root[0].opensSubmenu)
    assertFalse(root[1].opensSubmenu)

    val entries = fluidMenuSubmenuEntries(parent, reversed = false)
    assertEquals(listOf("Ordina", "Per data", "Per nome", "Per taglia"), labels(entries))
    assertEquals(FluidMenuCellKind.Header, entries[0].kind)
    assertEquals(-1, entries[0].source)
    assertFalse("l'intestazione torna indietro, non riapre", entries[0].opensSubmenu)
    assertTrue("l'intestazione si sceglie sempre", entries[0].enabled)
    // Un livello solo: i figli dei figli non si aprono.
    assertFalse(entries[2].opensSubmenu)
    assertTrue(entries[2].action.children.isEmpty())
    assertEquals(listOf(false, false, false, true), starts(entries))

    val flipped = fluidMenuSubmenuEntries(parent, reversed = true)
    assertEquals(listOf("Per taglia", "Per nome", "Per data", "Ordina"), labels(flipped))
    assertEquals(FluidMenuCellKind.Header, flipped.last().kind)
  }

  @Test
  fun aSubmenuGrowsInsideItsParentFromTheSameEdge() {
    val host = Size(1080f, 2400f)
    val parent = Rect(300f, 400f, 900f, 1000f)
    val down = fluidMenuSubmenuPanel(parent, 600f, 900f, host, 40f, upward = false)
    assertEquals(Rect(300f, 400f, 900f, 1300f), down)
    val up = fluidMenuSubmenuPanel(parent, 600f, 400f, host, 40f, upward = true)
    assertEquals(Rect(300f, 600f, 900f, 1000f), up)
    // Piu' alto dello spazio che resta: resta dentro i margini.
    val clamped = fluidMenuSubmenuPanel(Rect(300f, 1800f, 900f, 2300f), 700f, 900f, host, 40f, upward = false)
    assertEquals(2360f, clamped.bottom, 0.001f)
    assertEquals(parent.center.x, down.center.x, 0.001f)
  }

  // --- Il cambio di livello -----------------------------------------------------------------------

  @Test
  fun aLevelChangeCrossesTheTwoLevelsAndNeverLeavesTheGlassEmpty() {
    // Chi esce e' pieno e nitido all'inizio, sparito a 0,66 T; chi entra comincia a 0,36 T ed e' pieno a 1,15 T.
    assertEquals(1f, fluidMenuLevelExitAlpha(0f, blur = true), 0f)
    assertEquals(0f, fluidMenuLevelExitAlpha(GlassContentDefaults.ExitEnd, blur = true), 0f)
    assertEquals(0f, fluidMenuLevelEnterAlpha(0.3f, blur = true), 0f)
    assertEquals(1f, fluidMenuLevelEnterAlpha(FluidMenuLevelEnd, blur = true), 0f)
    // Con la sfocatura si incrociano: la somma non scende mai sotto 0,5 (R7: ≈0,7 all'incrocio).
    var u = 0f
    while (u <= FluidMenuLevelEnd) {
      val sum = fluidMenuLevelExitAlpha(u, true) + fluidMenuLevelEnterAlpha(u, true)
      assertTrue("a u = $u la somma e' $sum", sum >= 0.5f)
      u += 0.01f
    }
    // Senza sfocatura si passano il posto: mai due testi nitidi e leggibili uno sull'altro.
    u = 0f
    while (u <= FluidMenuLevelEnd) {
      assertFalse("a u = $u", fluidMenuLevelExitAlpha(u, false) > 0.5f && fluidMenuLevelEnterAlpha(u, false) > 0.5f)
      u += 0.01f
    }
    // La sfocatura: chi entra e' nitido ai capi e al massimo a meta'; chi esce resta sfocato finche' svanisce.
    assertEquals(0f, fluidMenuLevelEnterBlur(0f), 0f)
    assertEquals(1f, fluidMenuLevelEnterBlur(0.5f), 0.001f)
    assertEquals(0f, fluidMenuLevelEnterBlur(1f), 0.001f)
    assertEquals(1f, fluidMenuLevelExitBlur(0.6f), 0.001f)
    assertEquals(1f, fluidMenuLevelExitScale(0f), 0f)
    assertEquals(1f, fluidMenuLevelEnterScale(1f), 0.001f)
    assertTrue(fluidMenuLevelEnterScale(0.5f) < 1f)
    assertTrue(FluidMenuLevelPeakBlurDp in 2f..4f)
  }

  @Test
  fun levelsSnapUnderReducedMotion() {
    val levels = FluidMenuLevels()
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    levels.change(2, Rect(0f, 0f, 10f, 10f), scope, reducedMotion = true)
    assertEquals(2, levels.openParent)
    assertEquals(-1, levels.previousParent)
    assertFalse(levels.swapping)
    assertFalse(levels.morphing)
    levels.change(-1, Rect(0f, 0f, 10f, 10f), scope, reducedMotion = true)
    assertEquals(-1, levels.openParent)
    assertEquals(2, levels.previousParent)
    levels.reset()
    assertEquals(-1, levels.openParent)
    assertEquals(-1, levels.previousParent)
    assertNull(levels.fromPanel)
  }

  // --- FluidMenuActions ---------------------------------------------------------------------------

  @Test
  fun menuActionsAreAListAndCarryOrderAndSize() {
    val actions = listOf(action("A"), action("B"))
    val menu = FluidMenuActions(actions, order = FluidMenuOrder.Priority, elementSize = FluidMenuElementSize.Small)
    assertEquals(actions, menu)
    assertEquals(menu, actions)
    assertEquals(actions.hashCode(), menu.hashCode())
    assertEquals(2, menu.size)
    assertEquals("B", menu[1].label)
    assertEquals(listOf("A", "B"), menu.map { it.label })
    assertEquals(FluidMenuOrder.Priority, fluidMenuOrderOf(menu))
    assertEquals(FluidMenuElementSize.Small, fluidMenuElementSizeOf(menu))
    // Una lista fissa e' fotografata: cambiarla dopo non cambia il menu.
    val mutable = mutableListOf(action("X"))
    val fixed = FluidMenuActions(mutable)
    mutable += action("Y")
    assertEquals(1, fixed.size)
    assertEquals(FluidMenuOrder.Automatic, fixed.order)
    assertEquals(FluidMenuElementSize.Large, fixed.elementSize)
  }

  @Test
  fun menuActionsReadTheirContentLive() {
    val grid = mutableStateOf(false)
    val menu = FluidMenuActions {
      listOf(FluidContextAction("Griglia", selected = grid.value, keepsMenuOpen = true) { grid.value = !grid.value })
    }
    assertFalse(fluidMenuSnapshot(menu)[0].selected)
    menu[0].onClick()
    // La voce che lascia il menu aperto mostra lo stato nuovo alla lettura dopo.
    assertTrue(fluidMenuSnapshot(menu)[0].selected)
    assertTrue(menu.current()[0].keepsMenuOpen)
    assertNotEquals(listOf(action("Griglia")), menu)
  }

  @Test
  fun theLevelWidthIgnoresTheRowAndCountsSubtitlesAndChevrons() {
    val panel = FluidMenuPanelMetrics(252f, 48f, 6f, 8f, 22f, 12f, 16f, 6f, 240f)
    val measure: (String) -> Float = { it.length * 10f }
    val small = listOf(action("Una etichetta lunghissima che non deve allargare"), action("B"))
    val tiles = fluidMenuEntries(small, FluidMenuElementSize.Small, false)
    assertEquals(252f, fluidMenuLevelWidth(tiles, panel, measure, measure, 1f, 1080f), 0f)

    // Il sottotitolo piu' largo dell'etichetta decide la larghezza.
    val withSubtitle = fluidMenuEntries(listOf(FluidContextAction("A", subtitle = "x".repeat(25)) {}), FluidMenuElementSize.Large, false)
    assertEquals(250f + 32f + 12f, fluidMenuLevelWidth(withSubtitle, panel, measure, measure, 1f, 1080f), 0.001f)

    val parent = FluidContextAction("x".repeat(25), children = listOf(action("Figlio"))) {}
    val withChevron = fluidMenuEntries(listOf(parent), FluidMenuElementSize.Large, false)
    // Il gallone prende la colonna della spunta: 22 dp + 12 dp.
    assertEquals(250f + 32f + 34f + 12f, fluidMenuLevelWidth(withChevron, panel, measure, measure, 1f, 1080f), 0.001f)
  }
}
