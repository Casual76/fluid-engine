package dev.antigravity.fluidengine.viewer.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** F6C.13 · I lettori puri della famiglia «data», sulla JVM: CSV, JSON, MIME, iCalendar, vCard, Markdown. */
class DataReadersTest {
  private fun resource(name: String): ByteArray =
    requireNotNull(javaClass.classLoader!!.getResourceAsStream("data/$name")) { "manca data/$name" }.use { it.readBytes() }

  private fun text(name: String) = DataText.decode(DataBytes(resource(name), resource(name).size, false)).text

  // --- CSV ----------------------------------------------------------------------------------------------------------

  @Test fun `csv recognises the semicolon, the quotes and the header of an Italian Excel file`() {
    val bytes = resource("spese.csv")
    val decoded = DataText.decode(DataBytes(bytes, bytes.size, false))
    assertEquals("windows-1252", decoded.charset.charset.name())
    val dialect = DataCsv.sniff(decoded.text, "csv")
    assertEquals(';', dialect.delimiter)
    val rows = DataCsv.records(decoded.text, dialect)
    assertEquals(6, rows.size)
    assertEquals(listOf("Voce", "Data", "Importo", "Note"), rows[0])
    assertEquals("pieno, autostrada", rows[1][3])
    assertEquals("con il \"capo\"", rows[2][3])
    assertEquals("due notti\r\ncolazione inclusa", rows[3][3])
    assertEquals("", rows[4][3])
    assertTrue(DataCsv.hasHeader(rows))
    assertTrue(DataCsv.looksNumeric("1.120,00"))
    assertTrue(DataCsv.looksNumeric("45,20"))
    assertFalse(DataCsv.looksNumeric("03/11/2026"))
  }

  @Test fun `csv recognises the comma, the tab and a file without header`() {
    val comma = text("virgole.csv")
    assertEquals(',', DataCsv.sniff(comma, "csv").delimiter)
    val rows = DataCsv.records(comma, DataCsv.sniff(comma, "csv"))
    assertEquals("Rossi, Mario", rows[1][1])
    assertEquals("Verdi \"Peppe\"", rows[3][1])
    assertTrue(DataCsv.hasHeader(rows))
    val tsv = text("misure.tsv")
    assertEquals('\t', DataCsv.sniff(tsv, "tsv").delimiter)
    assertTrue(DataCsv.hasHeader(DataCsv.records(tsv, DataCsv.sniff(tsv, "tsv"))))
    val pipes = "a|b|c\n1|2|3\n4|5|6\n"
    assertEquals('|', DataCsv.sniff(pipes).delimiter)
    val numbers = "1,2,3\n4,5,6\n7,8,9\n"
    assertFalse(DataCsv.hasHeader(DataCsv.records(numbers, CsvDialect(','))))
  }

  @Test fun `csv reads 100000 rows as a stream, and the first block arrives before the end`() = runBlocking {
    val file = File.createTempFile("grande", ".csv")
    try {
      file.bufferedWriter().use { w ->
        w.write("id;nome;importo;nota\n")
        for (i in 0 until 100_000) w.write("$i;riga $i;${i % 997},${i % 100};\"nota con ; e \"\"virgolette\"\"\"\n")
      }
      val firstBatch = CompletableDeferred<Int>()
      val resume = CompletableDeferred<Unit>()
      val grid = DataCsvGrid(
        "grande.csv", RandomAccessFile(file, "r").channel, { FileInputStream(file) }, 0L, CsvDialect(';'), Charsets.UTF_8,
        header = true, widths = emptyMap(), initialColumns = 4,
        pace = { rows -> if (!firstBatch.isCompleted) { firstBatch.complete(rows); resume.await() } },
      )
      // Il conto si ferma dopo il primo pezzo: la griglia ha già le prime righe, il resto non è contato.
      val counted = withTimeout(10_000) { firstBatch.await() }
      assertTrue(counted in DataProfile.CsvPublishRows until 100_001)
      assertFalse(grid.sheets[0].countFinal)
      val first = grid.cells(0, 0..63, 0..3)
      assertEquals("id", first.first { it.row == 0 && it.column == 0 }.text)
      assertEquals("nota con ; e \"virgolette\"", first.first { it.row == 5 && it.column == 3 }.text)
      assertTrue(first.first { it.row == 5 && it.column == 2 }.numeric)
      resume.complete(Unit)
      withTimeout(30_000) { grid.awaitIndex() }
      assertTrue(grid.finished)
      assertEquals(100_001, grid.rowCount)
      assertEquals(4, grid.columnCount)
      assertTrue(grid.sheets[0].countFinal)
      assertEquals(1, grid.sheets[0].frozenRows)
      val last = grid.cells(0, 99_990..100_000, 0..1)
      assertEquals("99999", last.first { it.row == 100_000 && it.column == 0 }.text)
      assertEquals("riga 99999", last.first { it.row == 100_000 && it.column == 1 }.text)
      grid.close()
    } finally {
      file.delete()
    }
  }

  @Test fun `csv counts quoted newlines, CRLF and a last row without newline like the parser`() = runBlocking {
    val file = File.createTempFile("quoted", ".csv")
    try {
      file.writeBytes("a,b\r\n\"x\r\ny\",2\r\n\"\"\"q\"\"\",3\r\nfine,4".toByteArray())
      val grid = DataCsvGrid("q.csv", RandomAccessFile(file, "r").channel, { FileInputStream(file) }, 0L, CsvDialect(','),
        Charsets.UTF_8, header = true, widths = emptyMap(), initialColumns = 2)
      grid.awaitIndex()
      assertEquals(4, grid.rowCount)
      val cells = grid.cells(0, 0..3, 0..1)
      assertEquals("x\r\ny", cells.first { it.row == 1 && it.column == 0 }.text)
      assertEquals("\"q\"", cells.first { it.row == 2 && it.column == 0 }.text)
      assertEquals("fine", cells.first { it.row == 3 && it.column == 0 }.text)
      grid.close()
    } finally {
      file.delete()
    }
  }

  // --- JSON ---------------------------------------------------------------------------------------------------------

  @Test fun `json nested tree with types and stats`() {
    val tree = DataJsonParser.parse(text("config.json"))
    assertNull(tree.error)
    assertEquals("JSON", tree.format)
    val root = tree.root!!
    assertEquals(DataKind.Object, root.kind)
    assertEquals(7, root.childCount)
    val byName = root.children!!.associateBy { it.name }
    assertEquals(DataKind.String, byName["nome"]!!.kind)
    assertEquals("Fluid Glass", byName["nome"]!!.value)
    assertEquals(DataKind.Number, byName["build"]!!.kind)
    assertEquals(DataKind.Bool, byName["beta"]!!.kind)
    assertEquals(DataKind.Null, byName["icona"]!!.kind)
    val moduli = byName["moduli"]!!
    assertEquals(DataKind.Array, moduli.kind)
    assertEquals("4 elementi", moduli.summary())
    assertEquals("engine-viewer-office", moduli.children!![0].preview())
    assertEquals(4, tree.stats.maxDepth)
    assertEquals(6, moduli.line)
  }

  @Test fun `jsonc comments and trailing commas are accepted and counted`() {
    val tree = DataJsonParser.parse(text("impostazioni.jsonc"))
    assertNull(tree.error)
    assertEquals("JSONC", tree.format)
    assertEquals(3, tree.stats.comments)
    assertEquals(3, tree.stats.trailingCommas)
    assertEquals(14.toString(), tree.root!!.children!!.first { it.name == "editor.fontSize" }.value)
  }

  @Test fun `an invalid json says line and column, and keeps what was read`() {
    val tree = DataJsonParser.parse(text("rotto.json"))
    val error = tree.error!!
    assertEquals(4, error.line)
    assertEquals(3, error.column)
    assertTrue(error.message, error.message.contains("atteso «,» o «}»"))
    assertEquals(listOf("nome", "elenco"), tree.root!!.children!!.map { it.name })
    assertFalse(tree.root!!.complete)
    // Altri errori, con la loro posizione.
    assertEquals(DataTreeError(1, 9, "").line, DataJsonParser.parse("{\"a\": tru}").error!!.line)
    assertEquals(7, DataJsonParser.parse("{\"a\": tru}").error!!.column)
    val unclosed = DataJsonParser.parse("[\n  \"aperta\n]").error!!
    assertEquals(2, unclosed.line)
    assertTrue(unclosed.message.contains("stringa non chiusa"))
    assertTrue(DataJsonParser.parse("").error!!.message.contains("vuoto"))
    assertTrue(DataJsonParser.parse("{} {}").error!!.message.contains("NDJSON"))
  }

  @Test fun `ndjson is a list of lines, and a broken line does not stop the others`() {
    val tree = DataJsonParser.parseLines(text("eventi.ndjson").reader())
    assertEquals("NDJSON", tree.format)
    val lines = tree.root!!.children!!
    assertEquals(4, lines.size)
    assertEquals(4, tree.error!!.line)
    assertEquals("1", tree.prolog["invalidLines"])
    assertEquals("chiusura", lines[3].children!!.first { it.name == "evento" }.value)
    assertFalse(lines[2].complete)
  }

  @Test fun `a huge json stops at the cap and the tree pages its children`() {
    val big = buildString {
      append('[')
      for (i in 0 until 5_000) { if (i > 0) append(','); append("{\"i\":").append(i).append(",\"s\":\"v").append(i).append("\"}") }
      append(']')
    }
    val capped = DataJsonParser.parse(big, maxNodes = 1_000)
    assertTrue(capped.truncated)
    assertNull(capped.error)
    assertEquals(1_000, capped.stats.nodes)
    assertFalse(capped.root!!.complete)
    val full = DataJsonParser.parse(big)
    assertEquals(5_000, full.root!!.childCount)
    val root = full.root!!
    val rows = dataTreeRows(root, setOf(root.id), emptyMap())
    assertEquals(1 + DataProfile.TreePage + 1, rows.size)
    assertTrue(rows.last() is DataTreeRow.More)
    val more = dataTreeRows(root, setOf(root.id), mapOf(root.id to 200))
    assertEquals(1 + 200 + 1, more.size)
    // Un annidamento folle è un errore, non uno stack esaurito.
    val deep = "[".repeat(100_000)
    assertTrue(DataJsonParser.parse(deep).error!!.message.contains("profondità"))
  }

  // --- MIME ---------------------------------------------------------------------------------------------------------

  @Test fun `eml with html, cid image, attachments and encoded headers`() {
    val bytes = resource("messaggio.eml")
    val mail = DataMime.parse(bytes)
    assertEquals("Preventivo e riepilogo delle spese — novembre", mail.subject)
    assertEquals("Marta Rossi 🌉", mail.from.single().name)
    assertEquals("marta@example.com", mail.from.single().address)
    assertEquals(listOf("Alessio", "Bianchi, Luca"), mail.to.map { it.name })
    assertEquals("ufficio@example.com", mail.cc.single().address)
    assertNotNull(mail.html)
    assertTrue(mail.html!!.contains("cid:logo@fluid"))
    assertTrue(mail.text!!.contains("Perché no? Città e caffè."))
    assertNotNull(mail.part("logo@fluid"))
    assertEquals("image/png", mail.part("logo@fluid")!!.type)
    assertEquals(listOf("preventivo.pdf", "riepilogo spese €.csv"), mail.attachments.map { it.filename })
    assertTrue(String(mail.attachments[0].body, Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
    assertEquals(2026, mail.date!!.year)
    assertEquals(setOf("iso-8859-1", "utf-8"), mail.charsets)
  }

  @Test fun `eml in KOI8-R with a Shift_JIS attachment`() {
    val mail = DataMime.parse(resource("charset.eml"))
    assertEquals("Привет из Москвы", mail.subject)
    assertTrue(mail.text!!.contains("Привет! Это письмо в кодировке KOI8-R."))
    val attachment = mail.attachments.single()
    assertEquals("giapponese.txt", attachment.filename)
    assertTrue(attachment.text().contains("シフトJIS"))
  }

  @Test fun `mime words, rfc 2231 parameters and quoted printable`() {
    assertEquals("Caffè e cornetto", DataMime.decodeWords("=?ISO-8859-1?Q?Caff=E8_e_?= =?UTF-8?B?Y29ybmV0dG8=?="))
    val v = DataMime.parameters("attachment; filename*0*=UTF-8''r%C3%A9; filename*1*=sum%C3%A9.pdf; size=12")
    assertEquals("résumé.pdf", v.params["filename"])
    assertEquals("12", v.params["size"])
    assertEquals("a=b\nc", String(DataMime.quotedPrintable("a=3Db=\r\n\nc".toByteArray())))
  }

  // --- iCalendar ----------------------------------------------------------------------------------------------------

  @Test fun `ics events with time zones, all day, repetitions and alarms`() {
    val calendar = DataIcs.parse(text("riunioni.ics"))
    assertEquals("Lavoro", calendar.name)
    assertEquals(7, calendar.events.size)
    val review = calendar.events.first { it.summary == "Revisione dei visori" }
    assertEquals(ZoneId.of("Europe/Rome"), review.start!!.zone)
    assertEquals(LocalTime.of(10, 0), review.start.time)
    assertEquals("Sala riunioni, primo piano", review.location)
    assertEquals("Ordine del giorno:\n1. famiglia data\n2. archivi", review.description)
    assertEquals("Ogni settimana il martedì e il giovedì, fino al 30 giugno 2027", review.repeat)
    assertEquals(1, review.exceptions)
    assertEquals(listOf("Avviso 15 minuti prima"), review.alarms)
    assertEquals("Alessio <alessio@example.com>", review.organizer)
    assertEquals(2, review.attendees.size)

    val holiday = calendar.events.first { it.summary == "Ferie al mare" }
    assertTrue(holiday.start!!.allDay)
    assertEquals("Tutto il giorno · dal 3 dicembre al 5 dicembre", DataWhen.time(holiday, ZoneId.of("Europe/Rome"), "Tutto il giorno"))

    val ny = calendar.events.first { it.summary == "Chiamata con New York" }
    assertEquals("Ogni mese l'ultimo venerdì, per 6 volte", ny.repeat)
    assertEquals("15:00 – 16:00", DataWhen.time(ny, ZoneId.of("Europe/Rome"), ""))
    assertEquals("09:00 ora di New York", DataWhen.zone(ny, ZoneId.of("Europe/Rome")))

    val utc = calendar.events.first { it.summary == "Rilascio in UTC" }
    assertEquals("15:00 – 15:45", DataWhen.time(utc, ZoneId.of("Europe/Rome"), ""))
    assertEquals("14:00 UTC", DataWhen.zone(utc, ZoneId.of("Europe/Rome")))

    val outlook = calendar.events.first { it.summary == "Invito da Outlook" }
    assertEquals(ZoneId.of("Europe/Berlin"), outlook.start!!.zone)
    assertEquals("Ogni anno il 25 novembre", outlook.repeat)

    val todo = calendar.events.first { it.todo }
    assertEquals("Da fare", todo.status)
    assertEquals(40, todo.percent)

    assertEquals("Ogni anno il 25 dicembre", calendar.events.first { it.summary == "Natale" }.repeat)
    assertTrue(calendar.unknownZones.isEmpty())
  }

  @Test fun `repetition rules in Italian`() {
    val monday = IcsTime(LocalDate.of(2026, 11, 23), LocalTime.NOON, null, null)
    assertEquals(DayOfWeek.MONDAY, monday.date.dayOfWeek)
    assertEquals("Ogni giorno", DataIcs.describeRule("FREQ=DAILY", monday))
    assertEquals("Ogni 3 giorni, per 10 volte", DataIcs.describeRule("FREQ=DAILY;INTERVAL=3;COUNT=10", monday))
    assertEquals("Ogni giorno feriale", DataIcs.describeRule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", monday))
    assertEquals("Ogni 2 settimane il lunedì", DataIcs.describeRule("FREQ=WEEKLY;INTERVAL=2", monday))
    assertEquals("Ogni mese il giorno 23", DataIcs.describeRule("FREQ=MONTHLY", monday))
    assertEquals("Ogni mese il primo lunedì", DataIcs.describeRule("FREQ=MONTHLY;BYDAY=1MO", monday))
    assertEquals("Ogni mese la prima domenica", DataIcs.describeRule("FREQ=MONTHLY;BYDAY=SU;BYSETPOS=1", monday))
    assertEquals("Ogni mese l'ultimo giorno", DataIcs.describeRule("FREQ=MONTHLY;BYMONTHDAY=-1", monday))
    assertEquals("Ogni settimana la domenica", DataIcs.describeRule("FREQ=WEEKLY;BYDAY=SU", monday))
  }

  // --- vCard --------------------------------------------------------------------------------------------------------

  @Test fun `vcf with three contacts in 2_1, 3_0 and 4_0, labels and photos`() {
    val contacts = DataVcard.parse(text("contatti.vcf"))
    assertEquals(3, contacts.size)
    val marta = contacts[0]
    assertEquals("Marta Rossi", marta.name)
    assertEquals("Studio Dune · Rilievi", marta.organization)
    assertEquals("Geologa", marta.title)
    assertEquals(listOf("cellulare", "lavoro"), marta.phones.map { it.label })
    assertEquals("Via delle Dune 12\n04016 Sabaudia (LT)\nItalia", marta.addresses.single().value)
    assertEquals("15 marzo 1990", marta.birthday)
    assertEquals("Conosciuta al convegno, ottobre 2026.", marta.note)
    assertEquals("portfolio", marta.urls.single().label)
    assertNotNull(marta.photo)
    assertTrue(marta.photo!!.size > 100)
    assertEquals(0x89.toByte(), marta.photo!![0])

    val luca = contacts[1]
    assertEquals("2.1", luca.version)
    assertEquals("Luca Bianchi", luca.name)
    assertEquals(listOf("cellulare", "casa"), luca.phones.map { it.label })
    assertTrue(luca.addresses.single().value, luca.addresses.single().value.contains("Città di Prova"))
    assertTrue(luca.addresses.single().value.endsWith("Italia"))

    val officina = contacts[2]
    assertEquals("4.0", officina.version)
    assertNotNull(officina.photo)
  }

  // --- Markdown -----------------------------------------------------------------------------------------------------

  @Test fun `markdown chunks keep fences and lists together, and the stats count what is there`() {
    val md = text("nota.md")
    val chunks = DataMarkdown.chunks(md)
    assertTrue(chunks.size >= 3)
    assertTrue(chunks.any { it.contains("```kotlin") && it.contains("fun spostamento") && Regex("```").findAll(it).count() == 2 })
    assertEquals(md.lines().count { it.isNotBlank() }, chunks.sumOf { c -> c.lines().count { it.isNotBlank() } })
    val stats = DataMarkdown.stats(md)
    assertEquals("Quaderno di campo", stats.title)
    assertEquals(4, stats.headings)
    assertEquals(1, stats.tables)
    assertEquals(1, stats.codeBlocks)
    assertEquals(3, stats.tasks)
    assertEquals(1, stats.tasksDone)
    assertEquals(1, stats.images)
    assertEquals(1, stats.links)
  }

  @Test fun `text helpers`() {
    assertEquals("CRLF", DataText.newline("a\r\nb\r\n"))
    assertEquals("misti", DataText.newline("a\nb\r\n"))
    assertEquals("2,3 KB", DataText.size(2_300))
    assertEquals("100.000", DataText.count(100_000))
    assertEquals("iso-8859-1", DataText.htmlCharset("<meta charset=\"ISO-8859-1\">".toByteArray(), 26))
  }
}
