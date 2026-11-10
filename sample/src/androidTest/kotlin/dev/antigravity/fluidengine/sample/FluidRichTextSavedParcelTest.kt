package dev.antigravity.fluidengine.sample

import android.os.Parcel
import android.util.Log
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.richtext.FluidRichTextBlock
import dev.antigravity.fluidengine.richtext.FluidRichTextDocument
import dev.antigravity.fluidengine.richtext.FluidRichTextInlineFormat
import dev.antigravity.fluidengine.richtext.FluidRichTextLinks
import dev.antigravity.fluidengine.richtext.FluidRichTextParagraphStyle
import dev.antigravity.fluidengine.richtext.FluidRichTextState
import dev.antigravity.fluidengine.richtext.buildFluidRichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il tetto dello stato salvato dell'editor contro un `Parcel` **vero**.
 *
 * Il contratto di `FluidRichTextState.Saver`: il valore che restituisce, scritto in un `Parcel` con
 * `writeValue`, non supera mai 256 KB. Lo garantisce una stima (`savedSizeEstimate`) che deve essere un
 * limite superiore della dimensione vera, e non molto piu' larga. Sulla JVM un `Parcel` non c'e' (quello
 * di Robolectric non codifica come Android): per questo la prova sta qui, sul dispositivo.
 *
 * Per ogni documento si confrontano tre numeri: la stima dell'engine, la dimensione nel `Parcel` del
 * valore salvato, e la decisione del `Saver`. Dei documenti che il `Saver` rifiuta si misura il valore
 * che avrebbe salvato, ricostruito qui con la stessa forma (la dimensione non dipende dai numeri dentro
 * gli array); sui documenti accettati la prova controlla che la ricostruzione pesi quanto l'originale.
 *
 * La stima e' `internal` e il tetto anche: la prova chiama la prima per riflessione (il nome compilato
 * porta il nome del modulo) e scrive il secondo come lo dichiara la scheda, 256 KB.
 */
@RunWith(AndroidJUnit4::class)
class FluidRichTextSavedParcelTest {
  private class Row(
    val name: String,
    val document: FluidRichTextDocument,
    val estimate: Long,
    val parcel: Int,
    val saved: Any?,
  )

  private fun save(document: FluidRichTextDocument): Any? =
    with(FluidRichTextState.Saver) { SaverScope { true }.save(FluidRichTextState(document)) }

  private fun parcelSize(value: Any): Int {
    val parcel = Parcel.obtain()
    return try {
      parcel.writeValue(value)
      parcel.dataSize()
    } finally {
      parcel.recycle()
    }
  }

  /** Il valore attraversa un `Parcel` (scritto, riletto dai byte) e torna uno stato. */
  private fun throughParcel(value: Any): FluidRichTextDocument? {
    val out = Parcel.obtain()
    val back = Parcel.obtain()
    return try {
      out.writeValue(value)
      val bytes = out.marshall()
      back.unmarshall(bytes, 0, bytes.size)
      back.setDataPosition(0)
      val read = back.readValue(javaClass.classLoader)
      read?.let { FluidRichTextState.Saver.restore(it)?.document }
    } finally {
      out.recycle()
      back.recycle()
    }
  }

  private fun linksOf(document: FluidRichTextDocument): List<String> = document.spans.mapNotNull { it.format.link }.distinct()

  /** Quanti tratti ha il documento: quelli con un formato, piu' i pezzi di testo semplice fra loro. */
  private fun runCount(document: FluidRichTextDocument): Int {
    var count = 0
    var position = 0
    for (span in document.spans) {
      if (span.start > position) count++
      count++
      position = span.end
    }
    if (position < document.text.length) count++
    return count
  }

  /** Lo stesso valore che il `Saver` restituisce, per forma e dimensione: serve per i documenti rifiutati. */
  private fun sameShapeValue(document: FluidRichTextDocument): Any = arrayListOf(
    document.text,
    IntArray(runCount(document) * 2),
    ArrayList(linksOf(document)),
    IntArray(document.paragraphCount),
    0,
    0,
    false,
    2,
  )

  private fun estimate(document: FluidRichTextDocument): Long =
    estimateMethod.invoke(FluidRichTextState.Companion, document, linksOf(document)) as Long

  private fun measure(name: String, document: FluidRichTextDocument): Row {
    val saved = save(document)
    val shaped = parcelSize(sameShapeValue(document))
    if (saved != null) assertEquals("$name: la ricostruzione pesa quanto il valore salvato", parcelSize(saved), shaped)
    val row = Row(name, document, estimate(document), shaped, saved)
    Log.i(
      Tag,
      "documento=\"$name\" caratteri=${document.text.length} tratti=${runCount(document)} paragrafi=${document.paragraphCount} " +
        "link=${linksOf(document).size} stima=${row.estimate} parcel=${row.parcel} scarto=${row.estimate - row.parcel} " +
        "salvato=${saved != null}",
    )
    return row
  }

  // --- i documenti ---------------------------------------------------------------------------------

  /** Un link breve per riga: il documento di C4 (4.001 link stavano sotto la stima e sopra il tetto). */
  private fun shortLinks(count: Int) = buildFluidRichText {
    repeat(count) { index -> paragraph { link("x", "https://e.co/$index") } }
  }

  private fun longLinks(count: Int) = buildFluidRichText {
    repeat(count) { index -> paragraph { link("l$index", "https://example.org/" + "q".repeat(1900) + index) } }
  }

  /** Un indirizzo lungo esattamente [length] caratteri, diverso per ogni [index]. */
  private fun url(length: Int, index: Int): String {
    val head = "https://e.co/"
    val tail = "/$index"
    return head + "u".repeat(length - head.length - tail.length) + tail
  }

  /** Link lunghi quanto l'editor ne accetta (`FluidRichTextLinks.MaxLength`). */
  private fun longestLinks(count: Int) = buildFluidRichText {
    repeat(count) { index -> paragraph { link("x", url(FluidRichTextLinks.MaxLength, index)) } }
  }

  /** 63 link lunghissimi e uno di [length] caratteri: con [length] la dimensione si regola a pochi byte dal tetto. */
  private fun tuned(length: Int) = buildFluidRichText {
    repeat(63) { index -> paragraph { link("x", url(FluidRichTextLinks.MaxLength, index)) } }
    paragraph { link("x", url(length, 63)) }
  }

  private fun repeatedUrl(count: Int) = buildFluidRichText {
    paragraph {
      repeat(count) {
        link("x", "https://e.co/x")
        append(" ")
      }
    }
  }

  /** Un tratto per carattere: grassetto e corsivo alternati. */
  private fun manyRuns(count: Int) = buildFluidRichText {
    paragraph { repeat(count) { index -> if (index % 2 == 0) bold("a") else italic("b") } }
  }

  /** Paragrafi vuoti, annidati da zero al massimo dei livelli, con i tre contenitori mescolati. */
  private fun nestedParagraphs(count: Int) = buildFluidRichText {
    val kinds = FluidRichTextBlock.entries
    repeat(count) { index ->
      val depth = index % (FluidRichTextParagraphStyle.MaxDepth + 1)
      paragraph(FluidRichTextParagraphStyle(List(depth) { level -> kinds[(index + level) % kinds.size] }))
    }
  }

  /** Emoji e caratteri fuori dal BMP (due unita' UTF-16 l'uno) nel testo e negli indirizzi. */
  private fun astral(count: Int) = buildFluidRichText {
    repeat(count) { index ->
      paragraph {
        append("😀𝄞 ")
        link("👩‍💻", "https://e.co/𝄞/😀/$index")
        bold(" 𠮷")
      }
    }
  }

  private fun mixed() = buildFluidRichText {
    val kinds = FluidRichTextBlock.entries
    repeat(900) { index ->
      paragraph(FluidRichTextParagraphStyle(List(index % 4) { level -> kinds[(index + level) % kinds.size] })) {
        append("Testo $index ")
        bold("grassetto")
        append(" e ")
        append("link", FluidRichTextInlineFormat(italic = true, underline = true, link = url(40 + index % 200, index)))
        italic(" 😀")
      }
    }
  }

  /** Il piu' grande `n` in `low..high` il cui documento viene salvato (la decisione cresce con `n`). */
  private fun largestSaved(low: Int, high: Int, document: (Int) -> FluidRichTextDocument): Int {
    var accepted = low
    var rejected = high
    assertNotNull("il documento piu' piccolo della famiglia si salva", save(document(low)))
    assertNull("il documento piu' grande della famiglia non si salva", save(document(high)))
    while (rejected - accepted > 1) {
      val middle = (accepted + rejected) / 2
      if (save(document(middle)) != null) accepted = middle else rejected = middle
    }
    return accepted
  }

  private fun rows(): List<Row> = measured ?: measureAll().also { measured = it }

  private fun measureAll(): List<Row> {
    val shortEdge = largestSaved(1, 6000, ::shortLinks)
    val tunedEdge = largestSaved(20, FluidRichTextLinks.MaxLength, ::tuned)
    return listOf(
      measure("vuoto", FluidRichTextDocument.Empty),
      measure("una riga semplice", FluidRichTextDocument.fromPlainText("Ciao, mondo")),
      measure("testo semplice di 50.000 caratteri", FluidRichTextDocument.fromPlainText("x".repeat(FluidRichTextState.MaxSavedLength))),
      measure("4.001 link brevi (C4)", shortLinks(4001)),
      measure("67 link lunghi (C4)", longLinks(67)),
      measure("68 link lunghi", longLinks(68)),
      measure("lo stesso indirizzo 4.000 volte (C4)", repeatedUrl(4000)),
      measure("5 link da 2.048 caratteri", longestLinks(5)),
      measure("63 link da 2.048 caratteri", longestLinks(63)),
      measure("64 link da 2.048 caratteri", longestLinks(64)),
      measure("26.000 tratti di un carattere", manyRuns(26_000)),
      measure("27.000 tratti di un carattere", manyRuns(27_000)),
      measure("43.000 paragrafi annidati", nestedParagraphs(43_000)),
      measure("44.000 paragrafi annidati", nestedParagraphs(44_000)),
      measure("2.000 righe di emoji e caratteri fuori dal BMP", astral(2000)),
      measure("misto: 900 paragrafi con stili, link e rientri", mixed()),
      measure("link brevi, l'ultimo salvato ($shortEdge)", shortLinks(shortEdge)),
      measure("link brevi, il primo non salvato (${shortEdge + 1})", shortLinks(shortEdge + 1)),
      measure("63 link lunghissimi + uno di $tunedEdge caratteri, l'ultimo salvato", tuned(tunedEdge)),
      measure("63 link lunghissimi + uno di ${tunedEdge + 1} caratteri, il primo non salvato", tuned(tunedEdge + 1)),
    )
  }

  private fun failures(rows: List<Row>, check: (Row) -> String?): String =
    rows.mapNotNull { row -> check(row)?.let { "${row.name}: $it" } }.joinToString("\n")

  // --- le prove ------------------------------------------------------------------------------------

  @Test fun theEstimateIsNeverBelowTheRealParcel() {
    val failed = failures(rows()) { row ->
      if (row.estimate >= row.parcel) null else "stima ${row.estimate} B sotto il Parcel vero ${row.parcel} B"
    }
    assertTrue("la stima non e' un limite superiore:\n$failed", failed.isEmpty())
  }

  @Test fun theEstimateIsNotMuchAboveTheRealParcel() {
    val failed = failures(rows()) { row ->
      if (row.estimate - row.parcel <= Slack) null else "stima ${row.estimate} B, Parcel vero ${row.parcel} B: oltre $Slack B di scarto"
    }
    assertTrue("la stima esagera:\n$failed", failed.isEmpty())
  }

  @Test fun whatIsSavedFitsTheCapAndComesBackIdentical() {
    val rows = rows()
    assertTrue("servono documenti salvati e documenti rifiutati", rows.any { it.saved != null } && rows.any { it.saved == null })
    val failed = failures(rows) { row ->
      val saved = row.saved
      when {
        saved == null -> null
        row.parcel > Cap -> "salvato, ma nel Parcel pesa ${row.parcel} B: oltre $Cap"
        throughParcel(saved) != row.document -> "salvato, ma dal Parcel non torna identico"
        else -> null
      }
    }
    assertTrue("il valore salvato non rispetta il tetto:\n$failed", failed.isEmpty())
  }

  @Test fun aboveTheCapNothingIsSavedAndOnlyThen() {
    val failed = failures(rows()) { row ->
      when {
        row.saved == null && row.estimate <= Cap -> "non salvato con una stima di ${row.estimate} B, sotto il tetto"
        row.saved != null && row.estimate > Cap -> "salvato con una stima di ${row.estimate} B, sopra il tetto"
        // Il rifiuto non e' prudenza in eccesso: un documento rifiutato pesa davvero quasi quanto il tetto.
        row.saved == null && row.parcel <= Cap - Slack -> "non salvato, ma nel Parcel peserebbe solo ${row.parcel} B"
        else -> null
      }
    }
    assertTrue("la decisione non segue il tetto:\n$failed", failed.isEmpty())
  }

  /** I numeri che C4 ha misurato sulla 3.1.2: la codifica del `Parcel` e' quella, byte per byte. */
  @Test fun theParcelMeasuresWhatC4Measured() {
    assertEquals(271_740, parcelSize(sameShapeValue(shortLinks(4001))))
    assertEquals(260_240, parcelSize(sameShapeValue(longLinks(67))))
    assertEquals(80_128, parcelSize(sameShapeValue(repeatedUrl(4000))))
  }

  private companion object {
    const val Tag = "FluidSavedParcel"

    /** Il tetto dichiarato dalla scheda: 256 KB. */
    const val Cap = 256 * 1024

    /**
     * Di quanto la stima puo' superare il `Parcel` vero: i 64 byte di margine che la stima dichiara, piu'
     * gli 8 delle due lunghezze di lista che Android scrive solo dalla 13 (API 33) in poi.
     */
    const val Slack = 64 + 8

    /** Le misure si fanno una volta per processo: le quattro prove leggono la stessa tabella. */
    var measured: List<Row>? = null

    val estimateMethod = FluidRichTextState.Companion::class.java.declaredMethods
      .first { it.name.startsWith("savedSizeEstimate") }
      .apply { isAccessible = true }
  }
}
