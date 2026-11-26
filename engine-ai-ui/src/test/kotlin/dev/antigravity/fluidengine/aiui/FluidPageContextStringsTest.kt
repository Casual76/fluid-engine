package dev.antigravity.fluidengine.aiui

import dev.antigravity.fluidengine.aiui.chat.FluidPageContextStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F9.1 · Le parole del chip «Questa pagina»: tradotte tutte, col segnaposto del titolo in tutte e due le lingue. */
class FluidPageContextStringsTest {
  private fun fields(s: FluidPageContextStrings): Map<String, String> =
    s.javaClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.associate { f ->
      f.isAccessible = true
      f.name to f.get(s) as String
    }

  @Test fun `ogni parola ha la sua traduzione`() {
    val it = fields(FluidPageContextStrings())
    val en = fields(FluidPageContextStrings.english())
    assertEquals(it.keys, en.keys)
    it.forEach { (name, value) -> assertNotEquals("$name non e' tradotto", value, en[name]) }
  }

  @Test fun `la descrizione per TalkBack porta il titolo`() {
    assertTrue(FluidPageContextStrings().description.contains("%1\$s"))
    assertTrue(FluidPageContextStrings.english().description.contains("%1\$s"))
  }
}
