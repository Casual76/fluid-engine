package dev.antigravity.fluidengine.sharein

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.10 · Le regole pure degli arrivi: i tipi accettati, i link nel testo, i nomi delle copie. Sulla JVM. */
class FluidShareInPolicyTest {
  @Test fun typesFollowThePatterns() {
    val photos = FluidShareInPolicy(acceptedTypes = listOf("image/*", "application/pdf"))
    assertTrue(photos.accepts("image/jpeg"))
    assertTrue(photos.accepts("IMAGE/PNG; charset=binary"))
    assertTrue(photos.accepts("application/pdf"))
    assertFalse(photos.accepts("video/mp4"))
    assertFalse("un tipo ignoto passa solo con */*", photos.accepts(null))
    assertTrue(FluidShareInPolicy().accepts(null))
    assertTrue(FluidShareInPolicy().accepts("application/x-what"))
  }

  @Test fun aLinkIsALinkEvenWithTheTitleInFront() {
    assertEquals("https://example.org/a", fluidLinkOf("https://example.org/a"))
    assertEquals("https://example.org/a", fluidLinkOf("Una pagina https://example.org/a"))
    assertNull("un testo lungo con un link in fondo resta testo", fluidLinkOf("Riga uno\nRiga due con dentro un link https://example.org/a"))
    assertNull(fluidLinkOf("ciao"))
    assertNull(fluidLinkOf("ftp://example.org/x"))
  }

  @Test fun copiesNeverOverwriteEachOther() {
    val used = HashSet<String>()
    assertEquals("foto.jpg", fluidUniqueName("foto.jpg", used))
    assertEquals("foto (2).jpg", fluidUniqueName("foto.jpg", used))
    assertEquals("FOTO (3).jpg", fluidUniqueName("FOTO.jpg", used))
    assertEquals("nota", fluidUniqueName("nota", used))
    assertEquals("nota (2)", fluidUniqueName("nota", used))
  }

  @Test(expected = IllegalArgumentException::class)
  fun aPolicyNeedsRealLimits() {
    FluidShareInPolicy(maxFiles = 0)
  }
}
