package dev.antigravity.fluidengine.wear.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole di movimento della 3.0 sull'orologio: i componenti portati dalla 2.11 (`FluidWearToast`,
 * `FluidWearAccent`) usano i nomi di `FluidMotion`, non molle e tween scritti a mano, e il movimento
 * decorativo sparisce con la scala di animazione a zero.
 */
class FluidWearMotionTest {

  @Test
  fun `un avviso con il movimento ridotto non cambia scala, solo sfuma`() {
    assertEquals(1f, fluidWearToastScale(reducedMotion = true), 0f)
    assertTrue(fluidWearToastScale(reducedMotion = false) < 1f)
  }

  @Test
  fun `nessun file di engine-wear scrive molle o tween a mano`() {
    // I test girano nella cartella del modulo: i sorgenti stanno a src/main/kotlin.
    val root = File("src/main/kotlin")
    assertTrue("cartella dei sorgenti non trovata: ${root.absolutePath}", root.isDirectory)
    val raw = Regex("""\b(tween|spring|keyframes|infiniteRepeatable)\s*[(<{]""")
    val offenders = root.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .flatMap { file ->
        file.readLines().mapIndexedNotNull { index, line ->
          val code = line.substringBefore("//")
          if (!code.trimStart().startsWith("*") && raw.containsMatchIn(code)) "${file.name}:${index + 1}: ${line.trim()}" else null
        }
      }
      .toList()
    assertTrue("movimento scritto a mano fuori da FluidMotion:\n${offenders.joinToString("\n")}", offenders.isEmpty())
  }
}
