package dev.antigravity.fluidengine.ui.fluid.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il set [FluidIcons] contro il suo elenco (`tools/icons/fluid-icons.txt`) e la sua tabella di corrispondenza.
 *
 * Perché esiste. Il set nasce da un generatore, e `gen_fluid_icons.py --check` confronta il testo dei file; qui
 * si prova quello che il testo non dice: che ogni icona si costruisca davvero sul JVM (il `PathParser` di Compose
 * legge i tracciati scritti dal generatore e li mette nel riquadro da 24), che la si costruisca una volta sola,
 * che nessuna icona sia stata aggiunta a mano fuori dall'elenco, che le frecce si girino da destra a sinistra e
 * che la tabella che le app useranno per migrare non nomini un'icona che non c'è.
 */
class FluidIconsTest {

  @Test
  fun `ogni icona dell'elenco esiste, nel riquadro da 24, ed e' costruita una volta sola`() {
    val entries = listEntries()
    assertTrue("l'elenco e' vuoto", entries.isNotEmpty())
    for (entry in entries) {
      checkIcon(outline(entry.name), "FluidIcons.${entry.name}")
      assertSame("FluidIcons.${entry.name} si ricostruisce a ogni lettura", outline(entry.name), outline(entry.name))
      if (entry.filled) {
        checkIcon(filled(entry.name), "FluidIcons.Filled.${entry.name}")
        assertSame(filled(entry.name), filled(entry.name))
      }
    }
  }

  @Test
  fun `il set e' esattamente l'elenco, niente aggiunto a mano`() {
    val entries = listEntries()
    assertEquals(
      "le icone al tratto non sono quelle dell'elenco",
      entries.map { it.name }.toSortedSet(),
      publicGetters(OUTLINE_CLASS, FluidIcons::class.java).toSortedSet(),
    )
    assertEquals(
      "le icone piene non sono quelle segnate `pieno`",
      entries.filter { it.filled }.map { it.name }.toSortedSet(),
      publicGetters(FILLED_CLASS, FluidIcons.Filled::class.java).toSortedSet(),
    )
    val files = sourceDir().listFiles()!!.map { it.name }.toSortedSet()
    assertEquals(
      "nella cartella del set ci sono solo i file del generatore",
      sortedSetOf("FluidIcons.kt", "FluidIconsFilled.kt", "FluidIconsOutline.kt"),
      files,
    )
  }

  @Test
  fun `i nomi sono unici`() {
    val entries = listEntries()
    val symbols = entries.map { it.symbol }
    assertEquals("un nome di Material Symbols ripetuto nell'elenco", symbols.size, symbols.toSet().size)
    val kotlin = entries.map { it.name }
    assertEquals("due nomi di Material Symbols danno lo stesso nome Kotlin", kotlin.size, kotlin.toSet().size)
    val vectors = entries.map { outline(it.name).name } + entries.filter { it.filled }.map { filled(it.name).name }
    assertEquals("due ImageVector con lo stesso nome", vectors.size, vectors.toSet().size)
  }

  @Test
  fun `le frecce si girano da destra a sinistra, le altre icone no`() {
    // Il segno viene dalle sorgenti di Google (`android:autoMirrored`): qui si controlla che arrivi fino al vettore.
    for (name in listOf("ArrowBack", "ArrowForward", "ArrowBackIos", "ArrowBackIosNew", "ChevronLeft", "ChevronRight", "Send", "Undo", "Redo")) {
      assertTrue("FluidIcons.$name deve girarsi da destra a sinistra", outline(name).autoMirror)
    }
    for (name in listOf("Add", "Close", "Check", "Search", "Home", "ArrowUpward", "ArrowDownward", "Settings")) {
      assertTrue("FluidIcons.$name non deve girarsi", !outline(name).autoMirror)
    }
  }

  @Test
  fun `la tabella di corrispondenza copre il censimento e nomina solo icone che esistono`() {
    val entries = listEntries().associateBy { it.symbol }
    val census = File(toolsDir(), "censimento-app.txt").readLines()
      .filter { it.isNotBlank() }
      .map { it.substringBefore(';') }
    val rows = File(toolsDir(), "corrispondenza.csv").readLines()
      .drop(1)
      .filter { it.isNotBlank() }
      .map { line ->
        // Le prime quattro colonne non hanno mai `;` ne' virgolette; la nota, l'ultima, puo' averle.
        val cells = line.split(";", limit = 5)
        assertEquals("riga fuori formato: $line", 5, cells.size)
        Row(cells[0], cells[1], cells[2], cells[3])
      }
    val material = rows.filter { it.origin == "material" }
    assertEquals(
      "la tabella non ha una riga per ogni icona del censimento",
      census.toSortedSet(),
      material.map { it.name }.toSortedSet(),
    )
    assertEquals("un'icona del censimento ha due righe", material.size, material.map { it.name }.toSet().size)
    assertTrue("mancano le icone Phosphor di Fluidify", rows.any { it.origin == "phosphor" })
    for (row in rows) {
      assertTrue("origine sconosciuta: $row", row.origin == "material" || row.origin == "phosphor")
      val entry = entries[row.symbol]
      if (row.fluid.isEmpty()) {
        assertTrue("${row.name}: `${row.symbol}` e' nel set ma la tabella non lo dice", entry == null)
        continue
      }
      assertNotNull("${row.name}: `${row.symbol}` non e' nell'elenco ma la tabella da' ${row.fluid}", entry)
      when {
        row.fluid == "FluidIcons.${entry!!.name}" -> checkIcon(outline(entry.name), row.fluid)
        row.fluid == "FluidIcons.Filled.${entry.name}" -> {
          assertTrue("${row.name}: ${row.fluid} non e' segnata `pieno`", entry.filled)
          checkIcon(filled(entry.name), row.fluid)
        }
        else -> throw AssertionError("${row.name}: ${row.fluid} non e' l'icona di `${row.symbol}`")
      }
    }
  }

  // ------------------------------------------------------------------------------------------

  private data class Entry(val symbol: String, val filled: Boolean) {
    /** Come `pascal()` del generatore: `arrow_back_ios_new` -> `ArrowBackIosNew`, `10k` -> `_10k`. */
    val name: String = symbol.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
      .let { if (it.first().isDigit()) "_$it" else it }
  }

  private data class Row(val origin: String, val name: String, val symbol: String, val fluid: String)

  private fun checkIcon(icon: ImageVector, name: String) {
    assertEquals(name, icon.name)
    assertEquals("$name: larghezza", 24.dp, icon.defaultWidth)
    assertEquals("$name: altezza", 24.dp, icon.defaultHeight)
    assertEquals("$name: riquadro", 24f, icon.viewportWidth, 0f)
    assertEquals("$name: riquadro", 24f, icon.viewportHeight, 0f)
    val paths = paths(icon.root)
    assertTrue("$name: nessun tracciato", paths.isNotEmpty())
    for (path in paths) {
      assertNotNull("$name: tracciato senza riempimento", path.fill)
      assertTrue("$name: tracciato vuoto", path.pathData.size > 2)
      // Un numero letto male dal PathParser finisce lontano dal riquadro: le sagome di Material Symbols stanno
      // fra -0,1 e 24,1 (`groups` sfiora il bordo).
      for (value in path.pathData.flatMap { coordinates(name, it) }) {
        assertTrue("$name: coordinata $value fuori dal riquadro", value in -0.5f..24.5f)
      }
    }
  }

  private fun paths(group: VectorGroup): List<VectorPath> = group.flatMap { node ->
    when (node) {
      is VectorPath -> listOf(node)
      is VectorGroup -> paths(node)
      else -> emptyList()
    }
  }

  /** Il generatore scrive solo comandi assoluti: uno relativo vorrebbe dire che il formato e' cambiato. */
  private fun coordinates(name: String, node: PathNode): List<Float> = when (node) {
    is PathNode.MoveTo -> listOf(node.x, node.y)
    is PathNode.LineTo -> listOf(node.x, node.y)
    is PathNode.HorizontalTo -> listOf(node.x)
    is PathNode.VerticalTo -> listOf(node.y)
    is PathNode.QuadTo -> listOf(node.x1, node.y1, node.x2, node.y2)
    is PathNode.CurveTo -> listOf(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
    is PathNode.ArcTo -> listOf(node.arcStartX, node.arcStartY)
    is PathNode.Close -> emptyList()
    else -> throw AssertionError("$name: comando inatteso $node")
  }

  private fun outline(name: String): ImageVector = getter(OUTLINE_CLASS, name, FluidIcons::class.java).invoke(null, FluidIcons) as ImageVector

  private fun filled(name: String): ImageVector =
    getter(FILLED_CLASS, name, FluidIcons.Filled::class.java).invoke(null, FluidIcons.Filled) as ImageVector

  private fun getter(className: String, name: String, receiver: Class<*>): Method =
    try {
      Class.forName(className).getMethod("get$name", receiver)
    } catch (error: NoSuchMethodException) {
      throw AssertionError("manca l'icona $name in $className: l'elenco e il codice generato non coincidono", error)
    }

  private fun publicGetters(className: String, receiver: Class<*>): List<String> =
    Class.forName(className).declaredMethods
      .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) }
      .filter { it.name.startsWith("get") && it.parameterTypes.contentEquals(arrayOf(receiver)) }
      .map { it.name.removePrefix("get") }

  private fun listEntries(): List<Entry> = File(toolsDir(), "fluid-icons.txt").readLines()
    .map { it.substringBefore('#').trim() }
    .filter { it.isNotEmpty() }
    .map { line ->
      val tokens = line.split(Regex("\\s+"))
      Entry(tokens[0], "pieno" in tokens.drop(1))
    }

  /** Gradle lancia i test dalla cartella del modulo, un IDE a volte dalla radice. */
  private fun toolsDir(): File = listOf(File("../tools/icons"), File("tools/icons"))
    .firstOrNull { File(it, "fluid-icons.txt").isFile }
    ?: throw AssertionError("Non trovo tools/icons/fluid-icons.txt da ${File(".").absolutePath}: il test lo deve leggere.")

  private fun sourceDir(): File = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin"))
    .map { File(it, PACKAGE.replace('.', '/')) }
    .firstOrNull { it.isDirectory }
    ?: throw AssertionError("Non trovo i sorgenti di FluidIcons da ${File(".").absolutePath}.")

  private companion object {
    const val PACKAGE = "dev.antigravity.fluidengine.ui.fluid.icons"
    const val OUTLINE_CLASS = "$PACKAGE.FluidIconsOutlineKt"
    const val FILLED_CLASS = "$PACKAGE.FluidIconsFilledKt"
  }
}
