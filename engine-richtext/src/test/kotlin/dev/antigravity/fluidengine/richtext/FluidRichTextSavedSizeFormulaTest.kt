package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La formula del tetto dello stato salvato, voce per voce.
 *
 * Qui un `Parcel` non c'e': che la formula sia un limite superiore della dimensione vera lo prova
 * `FluidRichTextSavedParcelTest` sul dispositivo (nella galleria). Questa prova tiene ferma la formula
 * sui numeri misurati con un `Parcel` vero, cosi' un ritocco che la sposta si vede anche senza un
 * emulatore.
 */
class FluidRichTextSavedSizeFormulaTest {
  private fun estimate(doc: FluidRichTextDocument): Long =
    FluidRichTextState.savedSizeEstimate(doc, doc.spans.mapNotNull { it.format.link }.distinct())

  private fun save(doc: FluidRichTextDocument): Any? = with(FluidRichTextState.Saver) { SaverScope { true }.save(FluidRichTextState(doc)) }

  /** Un indirizzo lungo esattamente [length] caratteri, diverso per ogni [index]. */
  private fun url(length: Int, index: Int): String {
    val head = "https://e.co/"
    val tail = "/$index"
    return head + "u".repeat(length - head.length - tail.length) + tail
  }

  @Test fun theEmptyDocumentCostsOnlyTheFixedPart() {
    // 72 fissi, la stringa vuota (8 + il terminatore allineato a 4), nessun tratto, un paragrafo.
    assertEquals(Margin + 72 + 12 + 4, estimate(FluidRichTextDocument.Empty))
  }

  @Test fun aStringIsTwoBytesPerCharWithTerminatorPaddedToFour() {
    // Un tratto (8) e un paragrafo (4) oltre ai 72 fissi: cambia solo la stringa del testo.
    val base = Margin + 72 + 8 + 4
    assertEquals(base + 8 + 4, estimate(FluidRichTextDocument.fromPlainText("a")))
    assertEquals(base + 8 + 8, estimate(FluidRichTextDocument.fromPlainText("ab")))
    assertEquals(base + 8 + 8, estimate(FluidRichTextDocument.fromPlainText("abc")))
    assertEquals(base + 8 + 12, estimate(FluidRichTextDocument.fromPlainText("abcd")))
  }

  @Test fun aCharacterOutsideTheBmpCountsAsTwo() {
    val emoji = FluidRichTextDocument.fromPlainText("😀")
    assertEquals(2, emoji.text.length)
    assertEquals(estimate(FluidRichTextDocument.fromPlainText("ab")), estimate(emoji))
  }

  @Test fun runsAndParagraphsAreIntsAndALinkIsAStringCountedOnce() {
    val plain = buildFluidRichText { paragraph { append("abcd") } }
    val twoRuns = buildFluidRichText {
      paragraph {
        append("ab")
        bold("cd")
      }
    }
    val twoParagraphs = buildFluidRichText {
      paragraph { append("a") }
      paragraph { append("bc") }
    }
    assertEquals(estimate(plain) + 8, estimate(twoRuns))
    // Il testo dei due paragrafi (a, a capo, bc) e' lungo quanto "abcd": cambia solo un paragrafo in piu'.
    assertEquals(estimate(plain) + 4, estimate(twoParagraphs))

    val address = "https://e.co/x" // 14 caratteri: 8 + pad4(30) = 40 byte
    val once = buildFluidRichText {
      paragraph {
        link("ab", address)
        append("cd")
      }
    }
    val twice = buildFluidRichText {
      paragraph {
        link("a", address)
        append("b")
        link("c", address)
        append("d")
      }
    }
    assertEquals(estimate(twoRuns) + 40, estimate(once))
    assertEquals(estimate(once) + 2 * 8, estimate(twice))
  }

  /** Le dimensioni misurate con un `Parcel` vero (API 37) per i documenti di C4: la stima e' quelle, piu' il margine. */
  @Test fun theFormulaMatchesTheMeasuredParcels() {
    val shortLinks = buildFluidRichText { repeat(4001) { index -> paragraph { link("x", "https://e.co/$index") } } }
    val longLinks = buildFluidRichText {
      repeat(67) { index -> paragraph { link("l$index", "https://example.org/" + "q".repeat(1900) + index) } }
    }
    val repeated = buildFluidRichText {
      paragraph {
        repeat(4000) {
          link("x", "https://e.co/x")
          append(" ")
        }
      }
    }
    assertEquals(Margin + 271_740, estimate(shortLinks))
    assertEquals(Margin + 260_240, estimate(longLinks))
    assertEquals(Margin + 80_128, estimate(repeated))
    // La 3.1.2 stimava il primo 262.092 byte e lo salvava: in un Parcel ne pesa 271.740, sopra il tetto.
    assertNull(save(shortLinks))
    assertNotNull(save(longLinks))
  }

  @Test fun theSaverRefusesExactlyAboveTheCap() {
    fun tuned(length: Int) = buildFluidRichText {
      repeat(63) { index -> paragraph { link("x", url(FluidRichTextLinks.MaxLength, index)) } }
      paragraph { link("x", url(length, 63)) }
    }
    // Con 829 caratteri nell'ultimo indirizzo la stima e' esattamente il tetto: si salva. Un carattere
    // in piu' sono 4 byte (l'allineamento), e non si salva piu'.
    assertEquals(FluidRichTextState.MaxSavedBytes.toLong(), estimate(tuned(829)))
    assertNotNull(save(tuned(829)))
    assertEquals(FluidRichTextState.MaxSavedBytes + 4L, estimate(tuned(830)))
    assertNull(save(tuned(830)))
  }

  private companion object {
    /** Il margine dichiarato dalla stima. */
    const val Margin = 64L
  }
}
