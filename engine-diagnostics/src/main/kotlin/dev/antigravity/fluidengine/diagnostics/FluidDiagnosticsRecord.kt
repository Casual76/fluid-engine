package dev.antigravity.fluidengine.diagnostics

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

// F8.24 · Che cosa c'è in un rapporto. Le regole della scheda `crash-e-lentezze` («senza contenuti né identità») sono
// scritte qui, nel tipo, e non affidate a chi lo riempie:
// - nessun messaggio d'eccezione (può contenere quello che l'utente ha scritto: «Nota "Diario di Marta" non trovata»),
//   solo i tipi e le righe del codice;
// - nessun nome di filo (un filo può chiamarsi come un file o un account), solo «principale» o «in sottofondo»;
// - nessun identificativo del telefono o dell'installazione: ogni rapporto ha un id a caso, solo per non contarlo due
//   volte, e due rapporti dello stesso telefono non si possono legare fra loro;
// - l'ora arrotondata all'ora intera, e i fotogrammi sommati per giorno.
// Il server (engine-diagnostics/worker) rifiuta ogni campo che non conosce.

/** Il tipo di un [FluidDiagnosticsRecord]. */
enum class FluidDiagnosticsKind(val wire: String) {
  /** Un'eccezione che nessuno ha preso: l'app si è chiusa. */
  Crash("crash"),

  /** Una chiusura decisa dal sistema, letta al riavvio (ANR, memoria, crash nativo): Android 11+. */
  Exit("exit"),

  /** I fotogrammi di una pagina in un giorno: quanti, quanti lenti, i blocchi. */
  Frames("frames"),
  ;

  companion object {
    fun fromWire(value: String): FluidDiagnosticsKind? = entries.firstOrNull { it.wire == value }
  }
}

/**
 * L'app e il telefono di un rapporto: niente che identifichi la persona. [buildType] è «debug» o «release» (le build
 * dev contano come debug), così il cruscotto tiene da parte le prove.
 */
class FluidDiagnosticsSource(
  val appId: String,
  val appVersion: String,
  val versionCode: Long,
  val engineVersion: String,
  val buildType: String,
  val manufacturer: String,
  val model: String,
  val sdk: Int,
  val refreshRateHz: Int,
) {
  internal fun toJson(): JsonObject = buildJsonObject {
    put("app", appId)
    put("version", appVersion)
    put("code", versionCode)
    put("engine", engineVersion)
    put("build", buildType)
    put("maker", manufacturer)
    put("model", model)
    put("sdk", sdk)
    put("hz", refreshRateHz)
  }

  internal companion object {
    fun fromJson(o: JsonObject): FluidDiagnosticsSource = FluidDiagnosticsSource(
      appId = o.str("app"), appVersion = o.str("version"), versionCode = o["code"]?.jsonPrimitive?.longOrNull ?: 0,
      engineVersion = o.str("engine"), buildType = o.str("build"), manufacturer = o.str("maker"), model = o.str("model"),
      sdk = o["sdk"]?.jsonPrimitive?.intOrNull ?: 0, refreshRateHz = o["hz"]?.jsonPrimitive?.intOrNull ?: 0,
    )
  }
}

/**
 * Un crash: il tipo dell'eccezione e quelli delle sue cause, le righe del codice (al massimo [FluidCrash.MaxFrames]),
 * il filo (principale o no) e una firma per raggruppare i crash uguali. Mai il messaggio.
 */
class FluidCrashInfo(
  val type: String,
  val causes: List<String>,
  val frames: List<String>,
  val mainThread: Boolean,
  val signature: String,
) {
  internal fun toJson(): JsonObject = buildJsonObject {
    put("type", type)
    put("causes", JsonArray(causes.map { JsonPrimitive(it) }))
    put("frames", JsonArray(frames.map { JsonPrimitive(it) }))
    put("thread", if (mainThread) "main" else "background")
    put("signature", signature)
  }

  internal companion object {
    fun fromJson(o: JsonObject) = FluidCrashInfo(
      type = o.str("type"), causes = o.strings("causes"), frames = o.strings("frames"),
      mainThread = o.str("thread") == "main", signature = o.str("signature"),
    )
  }
}

/**
 * Una chiusura decisa dal sistema ([reason]: «anr», «memoria», «crash-nativo», …), con l'importanza dell'app in quel
 * momento («primo-piano» o «sottofondo»), la memoria usata in kB e, per un ANR, le righe del filo principale.
 */
class FluidExitInfo(
  val reason: String,
  val importance: String,
  val pssKb: Long,
  val rssKb: Long,
  val frames: List<String>,
) {
  internal fun toJson(): JsonObject = buildJsonObject {
    put("reason", reason)
    put("importance", importance)
    put("pssKb", pssKb)
    put("rssKb", rssKb)
    put("frames", JsonArray(frames.map { JsonPrimitive(it) }))
  }

  internal companion object {
    fun fromJson(o: JsonObject) = FluidExitInfo(
      reason = o.str("reason"), importance = o.str("importance"), pssKb = o["pssKb"]?.jsonPrimitive?.longOrNull ?: 0,
      rssKb = o["rssKb"]?.jsonPrimitive?.longOrNull ?: 0, frames = o.strings("frames"),
    )
  }
}

/**
 * I fotogrammi di una pagina in un giorno ([day], `AAAA-MM-GG` in UTC). [slow] sono i fotogrammi lenti di JankStats
 * (più del doppio del tempo di un fotogramma), [hangs] quelli da 250 ms in su (un blocco, come lo conta l'Organizer di
 * Xcode), [frozen] quelli da 700 ms in su (i «fotogrammi bloccati» di Android vitals). [hitchMillis] è il ritardo
 * sommato oltre la scadenza, da cui il cruscotto fa il rapporto di Apple (ms di ritardo per secondo, [hitchRatio]).
 * [buckets] è l'istogramma delle durate con i confini di [FluidFrameBuckets.UpperMillis].
 */
class FluidPageFrames(
  val page: String,
  val day: String,
  val frames: Long,
  val slow: Long,
  val hangs: Long,
  val frozen: Long,
  val hitchMillis: Long,
  val durationMillis: Long,
  val worstMillis: Long,
  val buckets: List<Long>,
) {
  /**
   * Il rapporto dei ritardi di Apple, in millisecondi di ritardo per secondo: sotto 5 va bene, da 5 a 10 si vede, da 10
   * in su disturba (WWDC20 10077, «Eliminate animation hitches with XCTest»; fiducia alta).
   */
  val hitchRatio: Double get() = if (durationMillis <= 0) 0.0 else hitchMillis * 1000.0 / durationMillis

  /** Questi fotogrammi più quelli di [other] (stessa pagina e stesso giorno). */
  internal fun plus(other: FluidPageFrames): FluidPageFrames = FluidPageFrames(
    page, day, frames + other.frames, slow + other.slow, hangs + other.hangs, frozen + other.frozen,
    hitchMillis + other.hitchMillis, durationMillis + other.durationMillis, maxOf(worstMillis, other.worstMillis),
    List(FluidFrameBuckets.Count) { (buckets.getOrNull(it) ?: 0) + (other.buckets.getOrNull(it) ?: 0) },
  )

  internal fun toJson(): JsonObject = buildJsonObject {
    put("page", page)
    put("day", day)
    put("frames", frames)
    put("slow", slow)
    put("hangs", hangs)
    put("frozen", frozen)
    put("hitchMs", hitchMillis)
    put("durationMs", durationMillis)
    put("worstMs", worstMillis)
    put("buckets", JsonArray(buckets.map { JsonPrimitive(it) }))
  }

  internal companion object {
    fun fromJson(o: JsonObject) = FluidPageFrames(
      page = o.str("page"), day = o.str("day"), frames = o.lng("frames"), slow = o.lng("slow"), hangs = o.lng("hangs"),
      frozen = o.lng("frozen"), hitchMillis = o.lng("hitchMs"), durationMillis = o.lng("durationMs"),
      worstMillis = o.lng("worstMs"), buckets = o["buckets"]?.jsonArray?.map { it.jsonPrimitive.long } ?: emptyList(),
    )
  }
}

/** I confini dell'istogramma dei fotogrammi, in millisecondi: l'ultimo secchio prende tutto il resto. */
object FluidFrameBuckets {
  /** Il limite alto di ogni secchio (escluso). 8,3 e 16,7 ms sono un fotogramma a 120 e a 60 Hz. */
  val UpperMillis: List<Double> = listOf(4.2, 8.4, 12.5, 16.7, 25.0, 33.4, 50.0, 100.0, 250.0, 700.0, Double.MAX_VALUE)

  /** Quanti secchi. */
  val Count: Int = UpperMillis.size

  /** Il secchio di un fotogramma di [nanos] nanosecondi. */
  fun indexOf(nanos: Long): Int {
    val millis = nanos / 1_000_000.0
    for (i in UpperMillis.indices) if (millis < UpperMillis[i]) return i
    return Count - 1
  }
}

/**
 * Un rapporto della diagnostica: uno di [crash], [exit] o [frames], secondo [kind]. [id] è casuale e serve solo al
 * server per non contare due volte lo stesso invio; [hourMillis] è l'ora arrotondata in giù all'ora intera.
 */
class FluidDiagnosticsRecord internal constructor(
  val id: String,
  val kind: FluidDiagnosticsKind,
  val hourMillis: Long,
  val source: FluidDiagnosticsSource,
  val crash: FluidCrashInfo? = null,
  val exit: FluidExitInfo? = null,
  val frames: FluidPageFrames? = null,
) {
  /** Il rapporto in JSON, come parte verso il server e come si vede nella pagina «Dati di diagnostica». */
  fun toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("kind", kind.wire)
    put("hour", hourMillis)
    put("source", source.toJson())
    crash?.let { put("crash", it.toJson()) }
    exit?.let { put("exit", it.toJson()) }
    frames?.let { put("frames", it.toJson()) }
  }

  /** Lo stesso rapporto come testo JSON su una riga. */
  fun toJsonString(): String = toJson().toString()

  /** Lo stesso rapporto con i fotogrammi sommati a [more] (per la coda, che tiene un rapporto per pagina e giorno). */
  internal fun withFrames(more: FluidPageFrames): FluidDiagnosticsRecord =
    FluidDiagnosticsRecord(id, kind, hourMillis, source, crash, exit, frames?.plus(more) ?: more)

  /** La chiave di raggruppamento dei fotogrammi: stessa pagina, stesso giorno, stessa versione. */
  internal val framesKey: String?
    get() = frames?.let { "${it.page}|${it.day}|${source.appVersion}|${source.versionCode}" }

  companion object {
    /** Un id nuovo, a caso. */
    internal fun newId(): String = UUID.randomUUID().toString()

    /** L'ora intera che contiene [millis]. */
    fun hourOf(millis: Long): Long = millis - Math.floorMod(millis, 3_600_000L)

    /** Il giorno `AAAA-MM-GG` in UTC che contiene [millis]. */
    fun dayOf(millis: Long): String = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate().toString()

    internal fun crash(source: FluidDiagnosticsSource, now: Long, info: FluidCrashInfo) =
      FluidDiagnosticsRecord(newId(), FluidDiagnosticsKind.Crash, hourOf(now), source, crash = info)

    internal fun exit(source: FluidDiagnosticsSource, at: Long, info: FluidExitInfo) =
      FluidDiagnosticsRecord(newId(), FluidDiagnosticsKind.Exit, hourOf(at), source, exit = info)

    internal fun frames(source: FluidDiagnosticsSource, now: Long, info: FluidPageFrames) =
      FluidDiagnosticsRecord(newId(), FluidDiagnosticsKind.Frames, hourOf(now), source, frames = info)

    /** Un rapporto da una riga JSON di [toJsonString], o nulla se la riga è rotta. */
    fun fromJsonString(line: String): FluidDiagnosticsRecord? = runCatching {
      val o = Json.parseToJsonElement(line).jsonObject
      val kind = FluidDiagnosticsKind.fromWire(o.str("kind")) ?: return null
      FluidDiagnosticsRecord(
        id = o.str("id"),
        kind = kind,
        hourMillis = o["hour"]?.jsonPrimitive?.long ?: 0,
        source = FluidDiagnosticsSource.fromJson(o["source"]!!.jsonObject),
        crash = o["crash"]?.jsonObject?.let(FluidCrashInfo::fromJson),
        exit = o["exit"]?.jsonObject?.let(FluidExitInfo::fromJson),
        frames = o["frames"]?.jsonObject?.let(FluidPageFrames::fromJson),
      )
    }.getOrNull()
  }
}

/** Le righe del codice di un'eccezione, senza i messaggi: la parte pura del gestore dei crash. */
object FluidCrash {
  /** Quante righe del codice si tengono dell'eccezione in cima. */
  const val MaxFrames: Int = 40

  /** Quante cause si contano (i tipi, senza righe). */
  const val MaxCauses: Int = 8

  /** Una riga del codice: `pacchetto.Classe.metodo(File.kt:12)`, senza il nome del modulo né del caricatore di classi. */
  fun frame(element: StackTraceElement): String {
    val file = element.fileName
    val where = when {
      element.isNativeMethod -> "Native Method"
      file != null && element.lineNumber >= 0 -> "$file:${element.lineNumber}"
      file != null -> file
      else -> "Unknown Source"
    }
    return "${element.className}.${element.methodName}($where)"
  }

  /** Il crash di [error], sul filo principale o no. Il messaggio non entra mai. */
  fun describe(error: Throwable, mainThread: Boolean): FluidCrashInfo {
    val frames = error.stackTrace.take(MaxFrames).map(::frame)
    val causes = ArrayList<String>()
    var cause = error.cause
    val seen = HashSet<Throwable>().apply { add(error) }
    while (cause != null && causes.size < MaxCauses && seen.add(cause)) {
      causes += cause.javaClass.name
      cause = cause.cause
    }
    return FluidCrashInfo(error.javaClass.name, causes, frames, mainThread, signature(error.javaClass.name, frames))
  }

  /**
   * La firma di un crash: i primi otto passaggi del codice dell'app (senza quelli di Java, Kotlin e Android) col tipo.
   * Due crash con la stessa firma sono lo stesso difetto, anche con messaggi diversi.
   */
  fun signature(type: String, frames: List<String>): String {
    val own = frames.filterNot { f -> PlatformPrefixes.any { f.startsWith(it) } }.ifEmpty { frames }.take(8)
    val digest = MessageDigest.getInstance("SHA-256").digest((type + "\n" + own.joinToString("\n")).toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
  }

  private val PlatformPrefixes = listOf("java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "com.android.", "dalvik.", "libcore.", "sun.")
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""
private fun JsonObject.lng(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0
private fun JsonObject.strings(key: String): List<String> =
  (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
