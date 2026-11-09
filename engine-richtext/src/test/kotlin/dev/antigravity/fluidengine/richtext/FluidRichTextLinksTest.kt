package dev.antigravity.fluidengine.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FluidRichTextLinksTest {
  @Test fun onlySafeSchemesPass() {
    assertEquals("https://esempio.it/a?b=1", FluidRichTextLinks.sanitize("https://esempio.it/a?b=1"))
    assertEquals("http://x.it", FluidRichTextLinks.sanitize("HTTP://x.it"))
    assertEquals("mailto:a@b.it", FluidRichTextLinks.sanitize("mailto:a@b.it"))
    assertEquals("tel:+39055", FluidRichTextLinks.sanitize("tel:+39055"))
    listOf(
      "javascript:alert(1)", "JavaScript:alert(1)", " java\nscript:alert(1)", "java\u0000script:x", "vbscript:x",
      "data:text/html,<script>", "file:///etc/passwd", "/relativo", "esempio.it", "https:", "https://", "https:///x",
      "mailto:", "intent://x#Intent;end", "https://x.it/\"onmouseover=\"x", "", "  ",
      "https://" + "a".repeat(FluidRichTextLinks.MaxLength),
    ).forEach { assertNull(it, FluidRichTextLinks.sanitize(it)) }
  }

  @Test fun userInputIsCompleted() {
    assertEquals("https://esempio.it", FluidRichTextLinks.fromUserInput(" esempio.it "))
    assertEquals("https://esempio.it/x", FluidRichTextLinks.fromUserInput("https://esempio.it/x"))
    assertEquals("mailto:nome@esempio.it", FluidRichTextLinks.fromUserInput("nome@esempio.it"))
    assertEquals("tel:+39055123456", FluidRichTextLinks.fromUserInput("+39 055 123 456"))
    assertNull(FluidRichTextLinks.fromUserInput("javascript:alert(1)"))
    assertNull(FluidRichTextLinks.fromUserInput("non un indirizzo"))
    assertNull(FluidRichTextLinks.fromUserInput(""))
  }

  @Test fun displayTextDropsTechnicalPrefixes() {
    assertEquals("a@b.it", FluidRichTextLinks.displayText("mailto:a@b.it"))
    assertEquals("+39", FluidRichTextLinks.displayText("tel:+39"))
    assertEquals("https://a.it", FluidRichTextLinks.displayText("https://a.it/"))
  }
}
