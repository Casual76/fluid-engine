package dev.antigravity.fluidengine.sample.bilanci

/**
 * F7.28 · Il registro dei bilanci: una riga per elemento, scena e fase, con i tetti di quello che l'elemento puo' costare.
 *
 * Le righe stanno in file di testo sotto `sample/src/testDebug/resources/bilanci/`, uno per proprietario:
 * `vetro.bilancio` (i componenti del vetro e le scene di «Misure», F7), `banco.bilancio` (il banco delle allocazioni sul
 * percorso caldo), `f8p<n>.bilancio` (un frammento per pacchetto di F8: lo possiede il pacchetto) e `attesi.bilancio`
 * (gli elementi che non ci sono ancora). Il formato e il significato delle colonne sono scritti in testa a ogni file e
 * nella scheda `vetro.md` della skill («Bilanci e guardie»).
 *
 * Una riga:
 * ```
 * <id> <fase> <nodi> <layer> <cat/f> <catture> <pixel> <KiB> <fab> <fx> <rid> <alloc/f> <ver> <prova> | <motivo>
 * <id> atteso <ver> | <motivo>
 * @margine <id> <px>
 * @sottofondo <id> | <motivo>
 * ```
 * Un numero e' un tetto (si misura al piu' quello), `-` una colonna che in quella fase non si misura, `NV` una colonna che
 * al PC non si puo' verificare (serve il motivo, e la prova vera altrove). [ver] e' la sigla del pacchetto che ha scritto
 * o cambiato la riga: chi alza un tetto cambia la sigla e scrive nel motivo il costo e la ragione.
 */
internal enum class BalanceColumn(val label: String) {
  /** I nodi del vetro vivi (cattura e ombra), al massimo nella fase; nel rilascio, quelli rimasti rispetto a prima. */
  Nodes("nodi"),

  /** I layer del vetro vivi (cattura, posa, materiale, ombra), come sopra. */
  Layers("layer"),

  /** Le catture in un fotogramma, al massimo. */
  CapturesPerFrame("cat/f"),

  /** Le catture in tutta la fase; nella posa, quelle fra i due campioni separati da 2 s. */
  Captures("catture"),

  /** La cattura o la finitura più grande, in pixel (larghezza × altezza). */
  Pixels("pixel"),

  /** I byte del vetro residenti e in volo (categoria `GLASS`), al massimo, in KiB, rispetto a prima dell'elemento. */
  KiB("KiB"),

  /**
   * Le fabbriche pesanti: layer (RenderNode), bitmap e programmi AGSL nuovi. In entrata e in uscita il totale della fase,
   * in moto il massimo per fotogramma del tratto stabile (deve essere zero), nella posa il totale fra i due campioni.
   */
  Factories("fab"),

  /** I filtri (`RenderEffect`) nuovi, con le stesse finestre di [Factories]. */
  Effects("fx"),

  /** Le registrazioni di layer nella posa, fra i due campioni (un elemento fermo non ridisegna: zero, o la cadenza). */
  Redraws("rid"),

  /** I byte allocati per fotogramma di moto stabile sul filo principale, meno il banco vuoto (solo `banco.bilancio`). */
  AllocPerFrame("alloc/f"),
}

/** Il valore di una colonna: un tetto, «non si misura qui», o «non verificabile al PC». */
internal sealed interface BalanceValue {
  data class Ceiling(val value: Long) : BalanceValue {
    override fun toString() = value.toString()
  }

  data object NotMeasured : BalanceValue {
    override fun toString() = "-"
  }

  data object NotVerifiable : BalanceValue {
    override fun toString() = "NV"
  }
}

internal data class BalanceRow(
  val file: String,
  val line: Int,
  val id: String,
  val phase: String,
  val values: Map<BalanceColumn, BalanceValue>,
  val version: String,
  val proof: String,
  val reason: String,
) {
  val where: String get() = "$file:$line"

  /** Le colonne misurate che superano il tetto, come frasi pronte per il messaggio della prova. */
  fun exceeded(measured: Map<BalanceColumn, Long>): List<String> = measured.mapNotNull { (column, value) ->
    when (val ceiling = values.getValue(column)) {
      is BalanceValue.Ceiling -> if (value > ceiling.value) "${column.label} $value (tetto ${ceiling.value})" else null
      else -> null
    }
  }

  /** Le colonne dove la misura sta sotto il tetto: il tetto si può abbassare (solo un avviso). */
  fun slack(measured: Map<BalanceColumn, Long>): List<String> = measured.mapNotNull { (column, value) ->
    val ceiling = values.getValue(column) as? BalanceValue.Ceiling ?: return@mapNotNull null
    if (value < ceiling.value) "${column.label} $value < ${ceiling.value}" else null
  }
}

/**
 * Un difetto vero trovato da una invariante (che non ha un tetto da alzare): la violazione di [id] nella fase [phase] il
 * cui messaggio contiene [key] si stampa invece di cadere, finché il difetto non si corregge. Il motivo dice quale.
 */
internal data class KnownDefect(val file: String, val line: Int, val id: String, val phase: String, val key: String, val reason: String) {
  val where: String get() = "$file:$line"
}

/** Un elemento che non c'è ancora: un pacchetto (`F8.24`) o una scena (`gruppo-menu`). */
internal data class ExpectedEntry(val file: String, val line: Int, val id: String, val version: String, val reason: String)

internal class BalanceFile(
  val name: String,
  val rows: List<BalanceRow>,
  val expected: List<ExpectedEntry>,
  val margins: Map<String, Int>,
  val defects: List<KnownDefect> = emptyList(),
  /** F7.32 · Le scene che il banco misura col lavoro in sottofondo sull'orologio della prova (`@sottofondo`, [BackgroundWork]). */
  val background: Set<String> = emptySet(),
) {
  fun rowsOf(id: String): List<BalanceRow> = rows.filter { it.id == id }

  fun row(id: String, phase: String): BalanceRow? = rows.firstOrNull { it.id == id && it.phase == phase }
}

internal class BalanceFormatException(message: String) : IllegalArgumentException(message)

internal object GlassBalanceRegistry {
  /** Le fasi che ogni elemento misurato deve dichiarare. `moto` vale per ogni fase che comincia con `moto`. */
  val RequiredPhases = listOf("entrata", "moto", "posa", "uscita", "rilascio")

  private val Phase = Regex("""(entrata|posa|uscita|rilascio)|(moto|posa)(:[a-z0-9-]+)?""")
  private val Version = Regex("""F\d+\.\d+[a-z0-9]*""")

  /** F7.32d · Il nome di una prova nel motivo di una riga NV («…Test»). */
  private val ProofName = Regex("""\b[A-Z]\w*Test\b""")

  /** F7.32d · Le colonne che a riposo devono essere zero, salvo una cadenza dichiarata. */
  private val QuietColumns = listOf(BalanceColumn.Captures, BalanceColumn.Factories, BalanceColumn.Effects, BalanceColumn.Redraws)

  /**
   * F7.32d · Le fasi in più che vogliono le scene di F8 e F9: la posa dopo il gesto ([BalanceRuns.measureElement]).
   */
  val ElementPhases = listOf("posa:dopo")

  fun parse(name: String, text: String): BalanceFile {
    val rows = ArrayList<BalanceRow>()
    val expected = ArrayList<ExpectedEntry>()
    val margins = LinkedHashMap<String, Int>()
    val defects = ArrayList<KnownDefect>()
    val background = LinkedHashSet<String>()
    text.lines().forEachIndexed { index, raw ->
      val line = index + 1
      // Un commento è una riga intera che comincia con «#»: il motivo può contenere qualunque carattere.
      val trimmed = raw.trim()
      if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
      fun fail(why: String): Nothing = throw BalanceFormatException("$name:$line: $why\n  $raw")
      if (trimmed.startsWith("@difetto ")) {
        val bar = trimmed.indexOf('|')
        if (bar < 0) fail("manca «| motivo»")
        val parts = trimmed.substring(0, bar).trim().split(Regex("\\s+"))
        val reason = trimmed.substring(bar + 1).trim()
        if (parts.size != 4) fail("«@difetto <id> <fase> <chiave> | motivo»")
        if (reason.length < 20) fail("un difetto noto vuole il motivo: che cosa, quanto, chi lo corregge")
        defects += KnownDefect(name, line, parts[1], parts[2], parts[3], reason)
        return@forEachIndexed
      }
      if (trimmed.startsWith("@sottofondo ")) {
        val bar = trimmed.indexOf('|')
        if (bar < 0) fail("manca «| motivo»")
        val parts = trimmed.substring(0, bar).trim().split(Regex("\\s+"))
        if (parts.size != 2) fail("«@sottofondo <id> | motivo»")
        if (trimmed.substring(bar + 1).trim().length < 20) fail("@sottofondo vuole il motivo: quale lavoro in sottofondo, su che filo")
        background += parts[1]
        return@forEachIndexed
      }
      if (trimmed.startsWith("@")) {
        val parts = trimmed.split(Regex("\\s+"))
        if (parts[0] != "@margine" || parts.size != 3) fail("direttiva sconosciuta («@margine <id> <px>», «@difetto <id> <fase> <chiave> | motivo», «@sottofondo <id> | motivo»)")
        margins[parts[1]] = parts[2].toIntOrNull() ?: fail("margine non intero")
        return@forEachIndexed
      }
      val bar = trimmed.indexOf('|')
      if (bar < 0) fail("manca «| motivo»")
      val reason = trimmed.substring(bar + 1).trim()
      if (reason.length < 8) fail("il motivo è obbligatorio (costo e ragione, almeno una frase)")
      val fields = trimmed.substring(0, bar).trim().split(Regex("\\s+"))
      if (fields.size == 3 && fields[1] == "atteso") {
        if (!Version.matches(fields[2])) fail("versione «${fields[2]}» non valida (per esempio F7.28)")
        expected += ExpectedEntry(name, line, fields[0], fields[2], reason)
        return@forEachIndexed
      }
      val columns = BalanceColumn.entries
      if (fields.size != 2 + columns.size + 2) {
        fail("servono ${2 + columns.size + 2} campi prima di «|» (id, fase, ${columns.joinToString(" ") { it.label }}, ver, prova), ce ne sono ${fields.size}")
      }
      val (id, phase) = fields
      if (!Phase.matches(phase)) fail("fase «$phase» sconosciuta (entrata, moto[:nome], posa[:nome], uscita, rilascio)")
      val values = LinkedHashMap<BalanceColumn, BalanceValue>()
      for ((i, column) in columns.withIndex()) {
        val cell = fields[2 + i]
        values[column] = when (cell) {
          "-" -> BalanceValue.NotMeasured
          "NV" -> BalanceValue.NotVerifiable
          else -> BalanceValue.Ceiling(cell.toLongOrNull()?.takeIf { it >= 0 } ?: fail("${column.label}: «$cell» non è un intero, «-» o «NV»"))
        }
      }
      val version = fields[2 + columns.size]
      if (!Version.matches(version)) fail("versione «$version» non valida (per esempio F7.28)")
      val proof = fields[3 + columns.size]
      // F7.32d · Il posto dove si verifica davvero: «tablet», o il nome di una prova («…Test»). Fino a F7.32c bastava che il
      // motivo contenesse «NV».
      if (values.values.any { it == BalanceValue.NotVerifiable } && !reason.contains("tablet", ignoreCase = true) &&
        !ProofName.containsMatchIn(reason)) {
        fail("una colonna NV vuole nel motivo dove si verifica (per esempio «sul tablet», o il nome della prova)")
      }
      // F7.32d · A riposo zero vuol dire zero (le pose di F8 e F9): un tetto sopra zero di catture, fabbriche, filtri o
      // ridisegni dice nel motivo la cadenza della scena. Fino a F7.32c le righe «ferma» avevano 2, 3, 3 e 3.
      if ((name.startsWith("f8p") || name.startsWith("f9p")) && phase.startsWith("posa") &&
        QuietColumns.any { ((values[it] as? BalanceValue.Ceiling)?.value ?: 0L) > 0L } && !reason.contains("cadenza")
      ) {
        fail("a riposo zero vuol dire zero: un tetto sopra zero nella posa vuole nel motivo la «cadenza» (che cosa lavora, quanto)")
      }
      if (rows.any { it.id == id && it.phase == phase }) fail("riga doppia per $id $phase")
      rows += BalanceRow(name, line, id, phase, values, version, proof, reason)
    }
    return BalanceFile(name, rows, expected, margins, defects, background)
  }

  /** Il file [name] dalle risorse di prova (`bilanci/<name>`), o null se non c'è. */
  fun load(name: String): BalanceFile? {
    val stream = GlassBalanceRegistry::class.java.classLoader!!.getResourceAsStream("bilanci/$name") ?: return null
    return parse(name, stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
  }

  fun require(name: String): BalanceFile = checkNotNull(load(name)) { "manca il file del registro bilanci/$name" }

  /** Il frammento di un pacchetto di F8 («F8.12» → `f8p12.bilancio`, «F8.17d» → `f8p17d.bilancio`). */
  fun fragmentName(pack: String): String = pack.lowercase().replace(".", "p") + ".bilancio"

  /**
   * La verifica di completezza. [catalog] sono gli elementi disponibili, per proprietario (un pacchetto, o «vetro»), con
   * le loro scene; [files] il registro di ciascuno (null se manca); [expected] gli elementi attesi. Rosso se un elemento
   * disponibile non ha il file, o una sua scena non ha le fasi richieste, o è ancora «atteso»; un elemento atteso che non
   * c'è ancora resta verde.
   */
  fun completeness(
    catalog: Map<String, List<String>>,
    files: (String) -> BalanceFile?,
    expected: List<ExpectedEntry>,
  ): List<String> {
    val problems = ArrayList<String>()
    val expectedIds = expected.map { it.id }.toSet()
    for ((owner, scenes) in catalog) {
      val file = files(owner)
      if (file == null) {
        problems += "$owner: elemento disponibile senza bilancio (manca il file ${if (owner.startsWith("F")) fragmentName(owner) else owner})"
        continue
      }
      if (owner in expectedIds) problems += "$owner: è disponibile, ma è ancora «atteso» in attesi.bilancio: misurarlo e togliere la riga"
      for (scene in scenes) {
        if (scene in expectedIds) problems += "$owner/$scene: è disponibile, ma è ancora «atteso»"
        val rows = file.rowsOf(scene)
        if (rows.isEmpty()) {
          problems += "$owner/$scene: scena disponibile senza bilancio in ${file.name}"
          continue
        }
        val required = RequiredPhases + if (owner.startsWith("F")) ElementPhases else emptyList()
        val missing = required.filter { phase -> rows.none { it.phase == phase || it.phase.startsWith("$phase:") } }
        if (missing.isNotEmpty()) problems += "$owner/$scene: mancano le fasi $missing in ${file.name}"
      }
      val known = scenes.toSet()
      file.rows.map { it.id }.distinct().filter { it !in known }.forEach {
        problems += "$owner/$it: riga per una scena che non c'è (rinominata o tolta?) in ${file.name}"
      }
    }
    return problems
  }
}
