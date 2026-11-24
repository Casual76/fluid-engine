package dev.antigravity.fluidengine.ui.fluid

import android.content.ClipDescription
import androidx.compose.ui.text.AnnotatedString
import dev.antigravity.fluidengine.foundation.FluidPasswordRules
import dev.antigravity.fluidengine.foundation.FluidUnitFormat
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.3 · la logica pura dei campi speciali: la scrittura dal vivo del campo con unità (il testo grezzo e le migliaia a
 * vista), il codice che entra da tastiera, incolla e proposta, il tasto incolla che decide solo dalla descrizione, i nomi
 * delle regole e le parole inglesi.
 */
class FluidSpecialFieldsPureTest {
  private val it = Locale.ITALY
  private val en = Locale.US
  private val itSep = FluidUnitFormat.Separators(',', '.')
  private val enSep = FluidUnitFormat.Separators('.', ',')

  private fun edit(previous: String, incoming: String, sep: FluidUnitFormat.Separators = itSep, locale: Locale = it,
    fraction: Int = 2, negative: Boolean = false) = fluidUnitEdit(previous, incoming, sep, locale, fraction, negative)

  // --- Il campo con unità ---------------------------------------------------------------------------------------

  @Test fun `si scrive tasto per tasto e «1,» resta scrivibile`() {
    assertEquals("1", edit("", "1"))
    assertEquals("1,", edit("1", "1,"))
    assertEquals("1,", edit("1", "1.")) // sulla tastiera italiana il punto diventa virgola
    assertEquals("1,2", edit("1,", "1,2"))
    assertEquals("1,25", edit("1,2", "1,25"))
    assertEquals("0,", edit("", ","))
    assertEquals("5", edit("0", "05"))
    assertEquals("0", edit("0", "00"))
    assertEquals("1.", edit("1", "1.", enSep, en))
  }

  @Test fun `oltre le cifre dell'unità il tasto non entra`() {
    assertEquals("1,25", edit("1,25", "1,257"))
    assertEquals("12", edit("12", "12,", fraction = 0))
    assertEquals("1,2", edit("1,2", "1,2,"))
    assertEquals("999999999999999", edit("999999999999999", "9999999999999999"))
  }

  @Test fun `il meno solo in testa e solo se l'unità lo vuole`() {
    assertEquals("", edit("", "-"))
    assertEquals("-", edit("", "-", negative = true))
    assertEquals("-3", edit("-", "-3", negative = true))
    assertEquals("3", edit("3", "3-", negative = true))
  }

  @Test fun `l'incolla si legge in più formati e si arrotonda alle cifre dell'unità`() {
    assertEquals("1234,56", edit("", "1,234.56"))
    assertEquals("1234,5", edit("", "1.234,5"))
    assertEquals("12,5", edit("", "12,5 km"))
    // Al pari, come le FormatStyle: 12,345 → 12,34 (il 4 è pari), 12,355 → 12,36.
    assertEquals("12,34", edit("", "12,345"))
    assertEquals("12,36", edit("", "12,355"))
    assertEquals("1234.56", edit("", "1.234,56", enSep, en))
  }

  @Test fun `il testo grezzo e il numero`() {
    assertEquals("12,50", fluidUnitRaw(BigDecimal("12.5"), itSep, 2, 2))
    assertEquals("1234,5", fluidUnitRaw(BigDecimal("1234.5"), itSep, 2))
    assertEquals("", fluidUnitRaw(null, itSep, 2))
    assertEquals(0, fluidUnitRawValue("1,", ',')!!.compareTo(BigDecimal.ONE))
    assertEquals(0, fluidUnitRawValue("-3,5", ',')!!.compareTo(BigDecimal("-3.5")))
    assertNull(fluidUnitRawValue("-", ','))
    assertNull(fluidUnitRawValue("", ','))
  }

  @Test fun `le migliaia sono solo a vista, e il cursore resta al suo posto`() {
    val t = FluidGroupingTransformation(itSep).filter(AnnotatedString("1234567,5"))
    assertEquals("1.234.567,5", t.text.text)
    // Il cursore dopo il «4» (indice 4 nel grezzo) sta davanti al «5», cioè dopo il punto delle migliaia (indice 6).
    assertEquals(6, t.offsetMapping.originalToTransformed(4))
    assertEquals(9, t.offsetMapping.originalToTransformed(7))
    assertEquals(t.text.length, t.offsetMapping.originalToTransformed(9))
    assertEquals(4, t.offsetMapping.transformedToOriginal(5))
    for (o in 0..9) assertEquals(o, t.offsetMapping.transformedToOriginal(t.offsetMapping.originalToTransformed(o)))
    val short = FluidGroupingTransformation(itSep).filter(AnnotatedString("999,5"))
    assertEquals("999,5", short.text.text)
    val negative = FluidGroupingTransformation(enSep).filter(AnnotatedString("-1234.5"))
    assertEquals("-1,234.5", negative.text.text)
  }

  // --- Il campo a codice ----------------------------------------------------------------------------------------

  @Test fun `il codice entra cifra per cifra, e da un incolla in un colpo`() {
    assertEquals("4", fluidCodeInput("", "4", 6, true))
    assertEquals("48", fluidCodeInput("4", "48", 6, true))
    assertEquals("4", fluidCodeInput("48", "4", 6, true))
    assertEquals("48", fluidCodeInput("48", "48a", 6, true))
    assertEquals("482913", fluidCodeInput("", "482913", 6, true))
    assertEquals("482913", fluidCodeInput("", "Il tuo codice MyCloud è 482913", 6, true))
    assertEquals("482913", fluidCodeInput("", "482-913", 6, true))
    assertEquals("482913", fluidCodeInput("", "482 913", 6, true))
    // Una frase con due numeri: vince quello lungo quanto il codice.
    assertEquals("482913", fluidCodeInput("", "Codice 12: 482913, valido 10 minuti", 6, true))
    assertEquals("482913", fluidCodeInput("482913", "4829137", 6, true))
    assertEquals("AB12CD34", fluidCodeInput("", "ab12-cd34", 8, false))
  }

  @Test fun `i gruppi e la scossa`() {
    assertEquals(listOf(4), fluidCodeGroups(4))
    assertEquals(listOf(3, 3), fluidCodeGroups(6))
    assertEquals(listOf(3, 4), fluidCodeGroups(7))
    assertEquals(listOf(4, 4), fluidCodeGroups(8))
    assertEquals(0f, fluidCodeShake(0f), 1e-6f)
    assertEquals(0f, fluidCodeShake(1f), 1e-4f)
    assertTrue((0..100).maxOf { kotlin.math.abs(fluidCodeShake(it / 100f)) } > 0.6f)
  }

  // --- Il tasto incolla -----------------------------------------------------------------------------------------

  private fun facts(
    mimes: List<String> = listOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
    classified: Boolean = false,
    otp: Float = 0f,
    url: Float = 0f,
    text: String? = null,
  ) = FluidClipFacts(mimes, classified, otp, url, text)

  @Test fun `il tasto compare solo per il dato concordato`() {
    val code = FluidPasteKind.code(6..6)
    // Da Android 12: solo la classificazione, mai il contenuto.
    assertTrue(fluidPasteMatches(code, facts(classified = true, otp = 1f)))
    assertFalse(fluidPasteMatches(code, facts(classified = true, otp = 0f)))
    assertFalse(fluidPasteMatches(code, facts(classified = false)))
    assertTrue(fluidPasteMatches(FluidPasteKind.Link, facts(classified = true, url = 1f)))
    assertFalse(fluidPasteMatches(FluidPasteKind.Link, facts(classified = true, url = 0.2f)))
    assertTrue(fluidPasteMatches(FluidPasteKind.Link, facts(mimes = listOf(ClipDescription.MIMETYPE_TEXT_URILIST))))
    assertTrue(fluidPasteMatches(FluidPasteKind.Image, facts(mimes = listOf("image/png"))))
    assertFalse(fluidPasteMatches(FluidPasteKind.Image, facts()))
    // Gli appunti normali: niente tasto.
    assertFalse(fluidPasteMatches(code, facts(classified = true)))
    assertFalse(fluidPasteMatches(FluidPasteKind.Link, facts(classified = true)))
    assertFalse(fluidPasteMatches(code, null))
  }

  @Test fun `sotto Android 12 il testo si legge, e deve essere il dato intero`() {
    val code = FluidPasteKind.code(6..6)
    assertTrue(fluidPasteMatches(code, facts(text = "482 913")))
    assertFalse(fluidPasteMatches(code, facts(text = "Chiamami al 3331234567")))
    assertFalse(fluidPasteMatches(code, facts(text = "Ci vediamo alle 20")))
    assertTrue(fluidPasteMatches(FluidPasteKind.Link, facts(text = "https://pampa.example/a")))
    assertFalse(fluidPasteMatches(FluidPasteKind.Link, facts(text = "Guarda qui https://pampa.example/a e poi dimmi")))
  }

  @Test fun `il codice e il link dentro un testo, dopo il tocco`() {
    assertEquals("482913", fluidCodeFromText("Il tuo codice è 482913.", 6..6, true, strict = false))
    assertNull(fluidCodeFromText("Il tuo codice è 482913.", 6..6, true, strict = true))
    assertEquals("https://pampa.example/album", fluidLinkFromText("Apri https://pampa.example/album."))
    assertNull(fluidLinkFromText("niente link qui"))
  }

  // --- Le parole ------------------------------------------------------------------------------------------------

  @Test fun `i nomi delle regole`() {
    val labels = FluidSpecialFieldLabels()
    val texts = FluidPasswordRules.parse("minlength: 10; required: upper, digit; required: [!#]; max-consecutive: 2")
      .requirements.map { fluidPasswordRuleText(it, labels) }
    assertEquals(listOf("Almeno 10 caratteri", "Una lettera maiuscola o un numero", "Uno di questi: ! #",
      "Non più di 2 caratteri uguali di fila"), texts)
  }

  @Test fun `l'inglese traduce ogni parola`() {
    val italian = FluidSpecialFieldLabels()
    val english = FluidSpecialFieldLabels.English
    val fields = FluidSpecialFieldLabels::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
    for (field in fields) {
      field.isAccessible = true
      val a = field.get(italian)
      val b = field.get(english)
      val sample: (Any?) -> Any? = { value ->
        when (value) {
          is Function1<*, *> -> runCatching { (value as (Any?) -> Any?).invoke(3) }.getOrElse { (value as (Any?) -> Any?).invoke("Buona") }
          is Function2<*, *, *> -> (value as (Any?, Any?) -> Any?).invoke(3, 6)
          else -> value
        }
      }
      if (field.name == "converted") continue // «≈ …» è lo stesso segno nelle due lingue
      assertNotEquals("${field.name} non è tradotto", sample(a), sample(b))
    }
  }
}
