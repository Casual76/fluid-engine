package dev.antigravity.fluidengine.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** F8.21 · I pezzi puri: l'orologio logico, l'unione dei campi e dei testi, la codifica. */
class SyncPiecesTest {

  // --- L'orologio --------------------------------------------------------------------------------------------------

  @Test fun `l'orologio va sempre avanti anche se il telefono torna indietro`() {
    var wall = 10_000L
    val clock = SyncClock("a", wall = { wall })
    val first = clock.now()
    wall = 5_000L // l'ora del telefono torna indietro
    val second = clock.now()
    assertTrue(second > first)
    assertEquals(first.millis, second.millis)
    assertEquals(first.counter + 1, second.counter)
  }

  @Test fun `dopo aver visto uno stamp del futuro il prossimo lo supera`() {
    val clock = SyncClock("a", wall = { 1_000L })
    val remote = SyncHlc(9_000_000L, 4, "b")
    clock.observe(remote)
    assertTrue(clock.now() > remote)
  }

  @Test fun `la forma testuale si ordina come gli stamp e torna indietro`() {
    val stamps = listOf(
      SyncHlc(1, 0, "b"), SyncHlc(1, 0, "c"), SyncHlc(1, 1, "a"), SyncHlc(2, 0, "a"), SyncHlc(0xFFFF_FFFF_FFL, 3, "z"),
    )
    assertEquals(stamps.sorted().map { it.encode() }, stamps.map { it.encode() }.sorted())
    for (s in stamps) assertEquals(s, SyncHlc.parse(s.encode()))
    assertNull(SyncHlc.parse("non e' uno stamp"))
  }

  // --- La codifica -------------------------------------------------------------------------------------------------

  @Test fun `un documento torna uguale e gli stessi dati danno gli stessi byte`() {
    val stamp = SyncHlc(1_700_000_000_000L, 2, "tel")
    val doc = SyncDocument(
      linkedMapOf(
        "z" to SyncField(SyncValue.Bytes(byteArrayOf(1, 2, 3)), stamp),
        "a" to SyncField(SyncValue.LongText("riga 1\nriga 2 è più lunga"), stamp, listOf(SyncAlternative(SyncHlc(5, 0, "b"), SyncValue.LongText("x")))),
        "n" to SyncField(SyncValue.Integer(-42), stamp),
        "r" to SyncField(SyncValue.Real(3.25), stamp),
        "b" to SyncField(SyncValue.Bool(true), stamp),
        "t" to SyncField(SyncValue.Text("ciao"), stamp),
        "vuoto" to SyncField(null, stamp),
      ),
    )
    val bytes = SyncDocumentCodec.encode(doc)
    assertEquals(doc, SyncDocumentCodec.decode(bytes))
    val reordered = SyncDocument(doc.fields.entries.reversed().associate { it.key to it.value })
    assertTrue(bytes.contentEquals(SyncDocumentCodec.encode(reordered)))
    val tomb = SyncDocument.tombstone(stamp)
    assertEquals(tomb, SyncDocumentCodec.decode(SyncDocumentCodec.encode(tomb)))
  }

  @Test fun `un formato piu' nuovo o rovinato non si legge e non rompe niente`() {
    val bytes = SyncDocumentCodec.encode(SyncDocument(mapOf("a" to SyncField(SyncValue.Text("x"), SyncHlc(1, 0, "a")))))
    val newer = bytes.copyOf().also { it[1] = 9 }
    try {
      SyncDocumentCodec.decode(newer)
      fail("un formato piu' nuovo non si legge")
    } catch (e: SyncFormatException) {
      assertTrue(e.message!!.contains("formato 9"))
    }
    try {
      SyncDocumentCodec.decode(bytes.copyOf(bytes.size - 3))
      fail("dati troncati")
    } catch (_: SyncFormatException) {
    }
  }

  // --- L'unione dei campi --------------------------------------------------------------------------------------------

  private var tick = 1_000L
  private fun stamp(node: String = "x") = SyncHlc(tick++, 0, node)
  private fun now(): SyncHlc = stamp("fuso")

  private fun doc(vararg fields: Pair<String, SyncField>) = SyncDocument(mapOf(*fields))

  @Test fun `per un campo corto vince lo stamp piu' grande, per tutti uguale`() {
    val base = doc("t" to SyncField(SyncValue.Text("a"), stamp()))
    val older = doc("t" to SyncField(SyncValue.Text("b"), stamp("p")))
    val newer = doc("t" to SyncField(SyncValue.Text("c"), stamp("q")))
    assertEquals("c", SyncMerge.merge(base, older, newer, ::now).text("t"))
    assertEquals("c", SyncMerge.merge(base, newer, older, ::now).text("t"))
  }

  @Test fun `l'unione e' idempotente`() {
    val base = doc("t" to SyncField(SyncValue.LongText("uno\n"), stamp()))
    val mine = doc("t" to SyncField(SyncValue.LongText("uno\ndue\n"), stamp()))
    assertEquals(mine, SyncMerge.merge(base, mine, mine, ::now))
    assertEquals(mine, SyncMerge.merge(base, base, mine, ::now))
    assertEquals(mine, SyncMerge.merge(base, mine, base, ::now))
  }

  @Test fun `la cancellazione vince su una modifica`() {
    val base = doc("t" to SyncField(SyncValue.Text("a"), stamp()))
    val edited = doc("t" to SyncField(SyncValue.Text("b"), stamp()))
    val gone = SyncDocument.tombstone(stamp())
    assertTrue(SyncMerge.merge(base, edited, gone, ::now).isDeleted)
    assertTrue(SyncMerge.merge(base, gone, edited, ::now).isDeleted)
  }

  @Test fun `i testi lunghi - righe diverse, parole diverse della stessa riga, aggiunte nello stesso punto`() {
    fun merge(base: String, a: String, b: String) = SyncTextMerge.merge(base, a, b, aFirst = true)
    assertEquals("A\nb\nC\n", merge("a\nb\nc\n", "A\nb\nc\n", "a\nb\nC\n"))
    assertEquals("il cane bianco\n", merge("il gatto nero\n", "il cane nero\n", "il gatto bianco\n"))
    assertEquals("latte\npane\nuova\n", merge("latte\n", "latte\npane\n", "latte\nuova\n"))
    assertEquals("latte\nuova\npane\n", SyncTextMerge.merge("latte\n", "latte\npane\n", "latte\nuova\n", aFirst = false))
    // La stessa riga aggiunta da tutte e due, e un'aggiunta che contiene l'altra: una volta sola.
    assertEquals("latte\npane\n", merge("latte\n", "latte\npane\n", "latte\npane\n"))
    assertEquals("latte\npane\nuova\n", merge("latte\n", "latte\npane\nuova\n", "latte\npane\n"))
    // Lo stesso passaggio cambiato in due modi: non si fonde.
    assertNull(merge("Ritrovo alle 8.\n", "Ritrovo alle 7.\n", "Ritrovo alle 9.\n"))
    // Due testi nati senza base: si sceglie.
    assertNull(merge("", "uno", "due"))
  }

  @Test fun `un testo che non si fonde tiene le due versioni e la scelta le toglie dappertutto`() {
    val base = doc("t" to SyncField(SyncValue.LongText("alle 8\n"), stamp()))
    val a = doc("t" to SyncField(SyncValue.LongText("alle 7\n"), stamp("a")))
    val b = doc("t" to SyncField(SyncValue.LongText("alle 9\n"), stamp("b")))
    val merged = SyncMerge.merge(base, a, b, ::now)
    val field = merged.fields.getValue("t")
    assertEquals("alle 9\n", (field.value as SyncValue.LongText).text)
    assertEquals(listOf(a.fields.getValue("t").stamp), field.alternatives.map { it.stamp })
    // Un altro dispositivo che ha ancora il conflitto e uno che l'ha risolto: la scelta vince.
    val resolved = merged.with("t", SyncField(SyncValue.LongText("alle 7\n"), stamp("r")))
    val again = SyncMerge.merge(merged, merged, resolved, ::now)
    assertTrue(again.fields.getValue("t").alternatives.isEmpty())
    assertNotEquals(merged, again)
  }

  @Test fun `due note lunghe cambiate in punti diversi si fondono in poco tempo`() {
    // Una nota di 2 000 righe (circa 90 KB): un dispositivo cambia l'inizio e aggiunge in fondo, l'altro cambia il mezzo.
    val base = (1..2_000).joinToString("") { "Riga $it della nota lunga, con qualche parola in piu' per pesare.\n" }
    val a = base.replaceFirst("Riga 10 della", "Riga DIECI della") + "In fondo, dal telefono.\n"
    val b = base.replaceFirst("Riga 1000 della", "Riga MILLE della")
    SyncTextMerge.merge(base, a, b, aFirst = true) // il primo giro scalda la JVM
    val start = System.nanoTime()
    val merged = SyncTextMerge.merge(base, a, b, aFirst = true)!!
    val millis = (System.nanoTime() - start) / 1_000_000
    println("F8.21: unione di due note da ${base.length / 1024} KB in $millis ms")
    assertTrue("DIECI" in merged && "MILLE" in merged && merged.endsWith("In fondo, dal telefono.\n"))
    assertEquals(a.length + (b.length - base.length), merged.length)
    assertTrue("troppo lenta: $millis ms", millis < 500)
  }
}
