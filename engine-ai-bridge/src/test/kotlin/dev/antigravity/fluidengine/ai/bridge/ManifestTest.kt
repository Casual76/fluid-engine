package dev.antigravity.fluidengine.ai.bridge

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il manifest del modulo finisce fuso in ogni app che lo include: un `<permission>` qui e' un
 * permesso dichiarato da tutte, e due app con firme diverse non si installano insieme
 * (`INSTALL_FAILED_DUPLICATE_PERMISSION`, visto con ClasseViva e Fluid-transit). Dalla 3.0.0 il
 * cancello e' la firma controllata dal provider (`AiToolHostProvider.isCallerTrusted`), e il
 * manifest non dichiara ne' chiede niente.
 */
class ManifestTest {

  private val manifest: String = listOf("src/main/AndroidManifest.xml", "engine-ai-bridge/src/main/AndroidManifest.xml")
    .map(::File).first { it.exists() }.readText()

  /** Il manifest senza i commenti, che lo spiegano e nominano `AI_TOOLS`. */
  private val code: String = manifest.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

  @Test
  fun `il modulo non dichiara nessun permesso`() {
    assertFalse("<permission> fa fallire l'installazione di due app con firme diverse", code.contains("<permission"))
  }

  @Test
  fun `il modulo non chiede nessun permesso`() {
    assertFalse(code.contains("<uses-permission"))
    assertFalse(code.contains("AI_TOOLS"))
  }

  @Test
  fun `il manifest resta un manifest`() {
    assertTrue(code.contains("<manifest"))
  }

  @Suppress("DEPRECATION")
  @Test
  fun `la costante del vecchio permesso resta per chi la nomina, ma e' deprecata`() {
    // Source-compatible: un'app che la citava compila ancora (con un avviso). Non e' piu' usata da nessuno.
    assertTrue(BridgeProtocol.PERMISSION.endsWith("AI_TOOLS"))
  }
}
