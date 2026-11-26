package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.sync.SyncClock
import dev.antigravity.fluidengine.sync.SyncHlc
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Le chiavi delle preferenze come le accetta il server (`Pampa-account\src\prefs.ts`, copia in `contract\v1\
 * prefs.json`: una prova le confronta). Il telefono controlla come il server, così una scelta che il server
 * rifiuterebbe non nasce nemmeno.
 */
object PrefsContract {
  sealed interface Type {
    data class Enum(val values: List<String>) : Type
    /** Un colore `#rrggbb`, o una delle [keywords] (F10.8b: `app` e `system` per l'accento). */
    data class Color(val keywords: List<String> = emptyList()) : Type
    data object Bool : Type
    data class Number(val min: Double, val max: Double, val step: Double) : Type
  }

  /** Dove vale una chiave: `global`, `app` (solo come eccezione di un'app) o tutte e due. */
  data class Spec(val scopes: String, val type: Type)

  val PREFS: Map<String, Spec> = mapOf(
    "appearance.mode" to Spec("global+app", Type.Enum(listOf("system", "light", "dark"))),
    "appearance.accent" to Spec("global+app", Type.Color(listOf("app", "system"))),
    // F10.8b: la chiarezza del vetro, da 0 «perfettamente trasparente» a 1 «più sfocato, come ora».
    "appearance.glass" to Spec("global+app", Type.Number(0.0, 1.0, 0.01)),
    "appearance.textScale" to Spec("global", Type.Number(0.8, 1.6, 0.05)),
    "appearance.contrast" to Spec("global", Type.Enum(listOf("standard", "high"))),
    "icon.set" to Spec("global+app", Type.Enum(listOf("default", "tinted", "dark", "clear"))),
    "motion.reduced" to Spec("global", Type.Bool),
    "app.followGlobal" to Spec("app", Type.Bool),
  )

  const val FOLLOW_GLOBAL = "app.followGlobal"

  private val HEX = Regex("^#[0-9a-fA-F]{6}$")

  /** Il motivo per cui il server rifiuterebbe, o `null` se va bene (la stessa logica di `checkPref`). */
  fun check(key: String, scope: String, value: JsonElement?): String? {
    val spec = PREFS[key] ?: return "unknown_key"
    val global = scope == "global"
    if (global && spec.scopes == "app") return "scope_not_allowed"
    if (!global && spec.scopes == "global") return "scope_not_allowed"
    if (value == null || value is JsonNull) return if (global) "bad_value" else null
    val p = value as? JsonPrimitive ?: return "bad_value"
    return when (val t = spec.type) {
      is Type.Enum -> if (p.isString && p.content in t.values) null else "bad_value"
      is Type.Color -> if (p.isString && (HEX.matches(p.content) || p.content in t.keywords)) null else "bad_value"
      Type.Bool -> if (!p.isString && p.booleanOrNull != null) null else "bad_value"
      is Type.Number -> {
        val v = if (p.isString) null else p.doubleOrNull
        if (v == null || !v.isFinite() || v < t.min || v > t.max) "bad_value"
        else if (kotlin.math.abs(Math.round((v - t.min) / t.step) * t.step + t.min - v) < 1e-9) null else "bad_value"
      }
    }
  }

  /** Il valore col suo tipo JSON, da un testo (le scelte dello Store e delle app viaggiano come testo nel provider). */
  fun typed(key: String, text: String): JsonPrimitive? = when (PREFS[key]?.type) {
    is Type.Number -> text.toDoubleOrNull()?.let(::JsonPrimitive)
    Type.Bool -> text.toBooleanStrictOrNull()?.let(::JsonPrimitive)
    null -> null
    else -> JsonPrimitive(text)
  }
}

/**
 * L'orologio logico ibrido nel formato del server (`Pampa-account\src\hlc.ts`): `<ms, 13 cifre>-<contatore, 5 cifre>-
 * <nodo [a-z0-9]{1,16}>`. A larghezza fissa, così il confronto fra testi è quello vero; qui si confronta comunque
 * per campi, con l'ordine di [SyncHlc].
 */
object PrefsHlc {
  private val RE = Regex("^(\\d{13})-(\\d{5})-([a-z0-9]{1,16})$")

  fun format(h: SyncHlc): String = "${h.millis.toString().padStart(13, '0')}-${h.counter.coerceAtMost(99_999).toString().padStart(5, '0')}-${h.node}"

  fun parse(text: String?): SyncHlc? {
    val m = text?.let(RE::matchEntire) ?: return null
    return SyncHlc(m.groupValues[1].toLong(), m.groupValues[2].toInt(), m.groupValues[3])
  }
}

/** Una riga delle preferenze come viaggia col server: dove vale, la chiave, il valore (`null` toglie un'eccezione) e l'HLC. */
data class PrefRow(val scope: String, val key: String, val value: JsonElement, val hlc: String)

/**
 * L'aspetto comune sul telefono (`b\architettura.md` § 2.5 e § 5.2): il tema comune e le eccezioni delle app. Risponde
 * **anche senza account**. Scrive solo lo Store; un'app chiede la sua eccezione con `setAppearance`. Le chiavi sono
 * quelle del contratto ([PrefsContract]): una chiave o un valore che il server non accetterebbe non si scrive.
 *
 * **F10.8 · Fra i dispositivi** ogni campo porta l'orologio logico di chi l'ha scritto (HLC, `SyncClock` di
 * `engine-sync`) e vince il più recente, campo per campo ([merge]), come sul server. Un'eccezione tolta è una lapide
 * (`null`) col suo HLC: vince anche lei solo se è la scelta più recente. Le scelte da mandare restano segnate
 * ([pending]) finché il server non le ha prese ([AppearanceSync]).
 *
 * Non è un segreto: sta in chiaro in un file dello Store.
 */
class AppearanceStore(
  private val dir: File,
  /** L'ora del telefono, per l'orologio logico. */
  private val wall: () -> Long = System::currentTimeMillis,
  /** Chiamato dopo ogni cambio che si vede (`appearance`): lo Store lo gira alle app. */
  private val onChange: (List<String>) -> Unit = {},
) {
  private val file get() = File(dir, "aspetto.json")

  /** La versione cresce a ogni cambio: le app la confrontano con la loro copia. */
  data class Snapshot(val version: Long, val global: Map<String, String>, val overrides: Map<String, Map<String, String>>, val follow: Map<String, Boolean>)

  private class Entry(val scope: String, val key: String, val value: JsonElement, val hlc: String, val dirty: Boolean)

  private class State(
    var version: Long = 0,
    var seq: Long = 0,
    var accepted: Set<String>? = null,
    val entries: LinkedHashMap<String, Entry> = LinkedHashMap(),
  )

  private var cached: State? = null

  /**
   * Il file dell'aspetto che non si leggeva più (rimasto a metà, cambiato a mano), messo da parte accanto
   * (`aspetto.json.rotto-<ora>`), o `null`. L'aspetto riparte da «di serie» invece di far cadere il provider e la pagina
   * Aspetto; con l'account le scelte tornano dal server. Lo Store lo dice (R2).
   */
  @Volatile var setAside: File? = null
    private set

  private var clockOrNull: SyncClock? = null
  private val clock: SyncClock
    get() = clockOrNull ?: SyncClock(HostNode.of(dir), wall).also { clockOrNull = it }

  @Synchronized
  private fun load(): State {
    cached?.let { return it }
    val st = if (!file.exists()) State() else try {
      read()
    } catch (e: Exception) {
      setAside = setAsideBroken(file)
      State()
    }
    cached = st
    return st
  }

  private fun read(): State {
    val st = State()
    val o = Json.parseToJsonElement(file.readText()) as JsonObject
    st.version = (o["version"] as? JsonPrimitive)?.longOrNull ?: 0
    st.seq = (o["seq"] as? JsonPrimitive)?.longOrNull ?: 0
    st.accepted = (o["accepted"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet()
    val entries = o["entries"] as? JsonArray
    if (entries != null) {
      for (e in entries) {
        val x = e as JsonObject
        val entry = Entry((x["s"] as JsonPrimitive).content, (x["k"] as JsonPrimitive).content, x["v"] ?: JsonNull, (x["h"] as JsonPrimitive).content, (x["d"] as? JsonPrimitive)?.booleanOrNull == true)
        st.entries[id(entry.scope, entry.key)] = entry
        PrefsHlc.parse(entry.hlc)?.let(clock::observe)
      }
    } else {
      // Il file di F10.4 (mai uscito): global, overrides, follow, senza orologio. Entra con l'HLC zero e da mandare.
      val zero = PrefsHlc.format(SyncHlc(0, 0, clock.node))
      (o["global"] as? JsonObject)?.forEach { (k, v) -> PrefsContract.typed(k, (v as JsonPrimitive).content)?.let { st.entries[id(GLOBAL, k)] = Entry(GLOBAL, k, it, zero, true) } }
      (o["overrides"] as? JsonObject)?.forEach { (p, m) -> (m as JsonObject).forEach { (k, v) -> PrefsContract.typed(k, (v as JsonPrimitive).content)?.let { st.entries[id(p, k)] = Entry(p, k, it, zero, true) } } }
      (o["follow"] as? JsonObject)?.forEach { (p, f) -> st.entries[id(p, PrefsContract.FOLLOW_GLOBAL)] = Entry(p, PrefsContract.FOLLOW_GLOBAL, f, zero, true) }
    }
    return st
  }

  @Synchronized
  private fun save(st: State) {
    writeReplacing(file, buildJsonObject {
      put("v", 2)
      put("version", st.version)
      put("seq", st.seq)
      st.accepted?.let { a -> putJsonArray("accepted") { a.sorted().forEach { add(JsonPrimitive(it)) } } }
      putJsonArray("entries") {
        for (e in st.entries.values) addJsonObject {
          put("s", e.scope); put("k", e.key); put("v", e.value); put("h", e.hlc); put("d", e.dirty)
        }
      }
    }.toString().toByteArray())
  }

  @Synchronized
  fun snapshot(): Snapshot {
    val st = load()
    val global = HashMap<String, String>()
    val overrides = HashMap<String, HashMap<String, String>>()
    val follow = HashMap<String, Boolean>()
    for (e in st.entries.values) {
      val p = e.value as? JsonPrimitive ?: continue
      if (p is JsonNull) continue
      when {
        e.scope == GLOBAL -> global[e.key] = p.content
        e.key == PrefsContract.FOLLOW_GLOBAL -> p.booleanOrNull?.let { follow[e.scope] = it }
        else -> overrides.getOrPut(e.scope) { HashMap() }[e.key] = p.content
      }
    }
    return Snapshot(st.version, global, overrides, follow)
  }

  /**
   * Scrive [values] in [scope] con un HLC nuovo; risponde se qualcosa è cambiato. Le righe uguali non si riscrivono:
   * un salvataggio della pagina Aspetto che rimanda anche i campi non toccati non deve scavalcare le scelte più recenti
   * fatte su quei campi da un altro dispositivo. Fanno eccezione le chiavi di [always], che sono una scelta anche quando
   * il valore è lo stesso (scegliere di nuovo «Personalizza» dopo un «Segui» arrivato da un altro dispositivo).
   */
  private fun write(st: State, scope: String, values: Map<String, JsonElement>, always: Set<String> = emptySet()): Boolean {
    var changed = false
    for ((k, v) in values) {
      if (PrefsContract.check(k, scope, v) != null) continue
      val current = st.entries[id(scope, k)]
      if (k !in always && (current?.value == v || (current == null && v is JsonNull))) continue
      st.entries[id(scope, k)] = Entry(scope, k, v, PrefsHlc.format(clock.now()), dirty = true)
      changed = true
    }
    if (changed) st.version++
    return changed
  }

  /** Lo Store cambia il tema comune. Le chiavi e i valori che il contratto non accetta si scartano. */
  @Synchronized
  fun setGlobal(values: Map<String, String>): Snapshot {
    val st = load()
    val typed = values.mapNotNull { (k, v) -> PrefsContract.typed(k, v)?.let { k to it } }.toMap()
    if (write(st, GLOBAL, typed)) {
      save(st)
      onChange(listOf(WHAT_APPEARANCE))
    }
    return snapshot()
  }

  /** L'eccezione di un'app: segue il comune, oppure tiene i suoi valori (solo le chiavi che valgono per un'app). */
  @Synchronized
  fun setException(packageName: String, followGlobal: Boolean, override: Map<String, String>?): Snapshot {
    val st = load()
    val values = LinkedHashMap<String, JsonElement>()
    values[PrefsContract.FOLLOW_GLOBAL] = JsonPrimitive(followGlobal)
    for (k in APP_KEYS) {
      val wanted = override?.get(k)?.let { PrefsContract.typed(k, it) }
      values[k] = wanted ?: JsonNull
    }
    if (write(st, packageName, values, always = setOf(PrefsContract.FOLLOW_GLOBAL))) {
      save(st)
      onChange(listOf(WHAT_APPEARANCE))
    }
    return snapshot()
  }

  /** La risposta di `appearance` per [packageName]: `{ version, global, override?, followGlobal }`. */
  fun responseFor(packageName: String): JsonObject {
    val s = snapshot()
    return buildJsonObject {
      put("version", s.version)
      putJsonObject("global") { for ((k, v) in s.global) put(k, v) }
      s.overrides[packageName]?.let { o -> putJsonObject("override") { for ((k, v) in o) put(k, v) } }
      put("followGlobal", s.follow[packageName] ?: true)
    }
  }

  /** Le righe da mandare al server, solo delle chiavi che il server annuncia (critica M1), se le conosce. */
  @Synchronized
  fun pending(): List<PrefRow> {
    val st = load()
    val accepted = st.accepted
    return st.entries.values.filter { it.dirty && (accepted == null || it.key in accepted) }.map { PrefRow(it.scope, it.key, it.value, it.hlc) }
  }

  /** Il server ha preso [rows] (o le ha rifiutate per sempre): non si rimandano. Una riga riscritta nel frattempo resta. */
  @Synchronized
  fun markSent(rows: Collection<PrefRow>) {
    val st = load()
    var changed = false
    for (r in rows) {
      val e = st.entries[id(r.scope, r.key)] ?: continue
      if (e.hlc == r.hlc && e.dirty) {
        st.entries[id(r.scope, r.key)] = Entry(e.scope, e.key, e.value, e.hlc, dirty = false)
        changed = true
      }
    }
    if (changed) save(st)
  }

  /**
   * Le righe arrivate dal server (o da un altro dispositivo): per ogni campo vince l'HLC più grande. Risponde se è
   * cambiato qualcosa che si vede; in quel caso avvisa le app. L'orologio di qui le osserva, così una scelta fatta dopo
   * averle viste ha sempre un HLC più grande.
   */
  @Synchronized
  fun merge(rows: Collection<PrefRow>): Boolean {
    val st = load()
    var changed = false
    for (r in rows) {
      val remote = PrefsHlc.parse(r.hlc) ?: continue
      if (PrefsContract.PREFS[r.key] == null) continue
      clock.observe(remote)
      val local = st.entries[id(r.scope, r.key)]
      val localHlc = PrefsHlc.parse(local?.hlc)
      if (localHlc != null && localHlc >= remote) continue
      st.entries[id(r.scope, r.key)] = Entry(r.scope, r.key, r.value, r.hlc, dirty = false)
      if (local?.value != r.value) changed = true
    }
    if (changed) st.version++
    save(st)
    if (changed) onChange(listOf(WHAT_APPEARANCE))
    return changed
  }

  /** Il numero d'ordine del server già letto (`GET /v1/prefs?since=`). */
  @Synchronized
  fun serverSeq(): Long = load().seq

  @Synchronized
  fun setServerSeq(seq: Long) {
    val st = load()
    if (seq > st.seq) {
      st.seq = seq
      save(st)
    }
  }

  /** Le chiavi annunciate dal server (`accepted`): lo Store manda solo quelle (critica M1). */
  @Synchronized
  fun setAccepted(keys: Set<String>) {
    val st = load()
    st.accepted = keys
    save(st)
  }

  /**
   * L'ora del server appena letta ([serverNowMillis]): l'orologio logico si corregge di quanto il telefono è avanti o
   * indietro, così le scelte nuove non nascono nel futuro del server (che le rifiuterebbe) né nel passato.
   */
  fun syncClockTo(serverNowMillis: Long) {
    clock.offsetMillis = serverNowMillis - wall()
  }

  /**
   * Le righe che il server ha rifiutato per l'orologio (`bad_hlc`: un telefono con l'ora avanti di più di un giorno).
   * Non le ha viste nessuno, quindi rinascono con un orologio nuovo, all'ora corretta ([syncClockTo]) e dopo tutte le
   * righe valide: l'orologio di prima, rimasto nel futuro, non si usa più. Restano da mandare.
   */
  @Synchronized
  fun restamp(rows: Collection<PrefRow>, serverNowMillis: Long) {
    val st = load()
    val limit = serverNowMillis + MAX_FUTURE_MILLIS
    val fresh = SyncClock(clock.node, wall).also { it.offsetMillis = serverNowMillis - wall() }
    for (e in st.entries.values) PrefsHlc.parse(e.hlc)?.takeIf { it.millis <= limit }?.let(fresh::observe)
    clockOrNull = fresh
    for (r in rows) {
      val e = st.entries[id(r.scope, r.key)] ?: continue
      if (e.hlc != r.hlc) continue
      st.entries[id(r.scope, r.key)] = Entry(e.scope, e.key, e.value, PrefsHlc.format(fresh.now()), dirty = true)
    }
    save(st)
  }

  /** L'ultimo HLC scritto o visto, per le prove. */
  val lastHlc: SyncHlc get() = clock.current

  private fun id(scope: String, key: String) = "$scope|$key"

  companion object {
    const val GLOBAL = "global"

    /** Le chiavi del contratto v1 (`prefs.json`) che valgono per tutte le app. */
    val GLOBAL_KEYS: Set<String> = PrefsContract.PREFS.filterValues { it.scopes != "app" }.keys

    /** Quelle che un'app può tenere diverse (`scopes: global+app`). */
    val APP_KEYS: Set<String> = PrefsContract.PREFS.filterValues { it.scopes == "global+app" }.keys

    /** Il campo `what` dell'avviso alle app. */
    const val WHAT_APPEARANCE = "appearance"

    /** Quanto avanti sull'ora del server il server accetta un HLC: un giorno (`HLC_MAX_FUTURE_MS`, F10.3). */
    const val MAX_FUTURE_MILLIS = 86_400_000L
  }
}
