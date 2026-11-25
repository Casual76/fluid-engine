package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.Place
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyKind
import dev.antigravity.fluidengine.account.host.custody.CustodyVault
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

/** Cosa può fare un'app coi luoghi comuni: niente, leggerli, o anche scriverli (`b\chiavi.md` § 8). */
enum class PlaceAccess { NONE, READ, READ_WRITE }

/**
 * I luoghi comuni, custoditi dallo Store (`b\chiavi.md` § 8; scelta N7 e B8): «I miei luoghi» (casa, scuola, lavoro,
 * altro) e il permesso di ogni app. Ogni app li vede solo se l'utente glielo permette: la prima volta lo Store chiede
 * «Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?». MyCloud Gallery non li riceve mai
 * ([AppPolicy.Places.NEVER]). Funziona anche senza account; fra i dispositivi viaggia cifrato con `K_com(places)`
 * (F10.8).
 *
 * La casa dice dove abiti: il file è chiuso con la cassaforte, nella classe delle chiavi di lavoro, fuori dal backup.
 */
class PlacesStore(
  private val vault: CustodyVault,
  private val dir: File,
  private val policy: AppPolicy,
  private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE,
) {
  private val file get() = File(dir, "luoghi.bin")
  private val keyClass get() = KeyClassPolicy.classOf(CustodyKeyKind.WORK_KEYS, choice)

  private data class Data(val places: List<Place>, val access: Map<String, PlaceAccess>)

  @Synchronized
  private fun load(): Data {
    if (!file.exists()) return Data(emptyList(), emptyMap())
    val o = Json.parseToJsonElement(vault.open(keyClass, file.readBytes(), AAD).decodeToString()) as JsonObject
    val places = (o["places"] as JsonArray).map { p ->
      val x = p as JsonObject
      Place(x.s("id"), x.s("name"), x.s("kind"), (x["lat"] as JsonPrimitive).doubleOrNull ?: 0.0, (x["lon"] as JsonPrimitive).doubleOrNull ?: 0.0, (x["address"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content)
    }
    val access = (o["access"] as JsonObject).mapValues { PlaceAccess.valueOf((it.value as JsonPrimitive).content) }
    return Data(places, access)
  }

  @Synchronized
  private fun save(d: Data) {
    dir.mkdirs()
    val json = buildJsonObject {
      putJsonArray("places") {
        for (p in d.places) addJsonObject {
          put("id", p.id); put("name", p.name); put("kind", p.kind); put("lat", p.latitude); put("lon", p.longitude)
          if (p.address != null) put("address", p.address)
        }
      }
      putJsonObject("access") { for ((k, v) in d.access) put(k, v.name) }
    }
    file.writeBytes(vault.seal(keyClass, json.toString().toByteArray(), AAD))
  }

  /** Il permesso di [packageName]; un'app che la tabella esclude ha sempre [PlaceAccess.NONE]. */
  fun access(packageName: String): PlaceAccess {
    val entry = policy.entryFor(packageName) ?: return PlaceAccess.NONE
    if (entry.places == AppPolicy.Places.NEVER) return PlaceAccess.NONE
    return load().access[packageName] ?: PlaceAccess.NONE
  }

  /** La scelta dell'utente, dalla richiesta dello Store o dalla pagina dell'account. Rifiutata per chi non li può avere. */
  fun setAccess(packageName: String, access: PlaceAccess): Boolean {
    val entry = policy.entryFor(packageName) ?: return false
    if (entry.places == AppPolicy.Places.NEVER && access != PlaceAccess.NONE) return false
    val d = load()
    save(d.copy(access = d.access + (packageName to access)))
    return true
  }

  /** Tutti i luoghi: per lo Store. Alle app li dà [listFor]. */
  fun all(): List<Place> = load().places

  fun listFor(packageName: String): List<Place>? = if (access(packageName) == PlaceAccess.NONE) null else load().places

  fun put(packageName: String?, place: Place): Boolean {
    if (packageName != null && access(packageName) != PlaceAccess.READ_WRITE) return false
    val d = load()
    save(d.copy(places = d.places.filter { it.id != place.id } + place))
    return true
  }

  fun delete(packageName: String?, id: String): Boolean {
    if (packageName != null && access(packageName) != PlaceAccess.READ_WRITE) return false
    val d = load()
    save(d.copy(places = d.places.filter { it.id != id }))
    return true
  }

  private fun JsonObject.s(k: String) = (this[k] as JsonPrimitive).content

  private companion object {
    val AAD = "pampa/v1/custody|luoghi".toByteArray()
  }
}

/**
 * L'aspetto comune sul telefono (`b\architettura.md` § 2.5): il tema comune e le eccezioni delle app. Risponde
 * **anche senza account**. Scrive solo lo Store; un'app chiede la sua eccezione con `setAppearance`. Le chiavi sono
 * quelle del contratto (`prefs.json`): una chiave che non conosco non si scrive (critica M1, migrazioni).
 *
 * Non è un segreto: sta in chiaro in un file dello Store. La regola di chi vince e il collegamento a `FluidTheme` sono
 * di F10.8.
 */
class AppearanceStore(private val dir: File) {
  private val file get() = File(dir, "aspetto.json")

  /** La versione cresce a ogni scrittura: le app la confrontano con la loro copia. */
  data class Snapshot(val version: Long, val global: Map<String, String>, val overrides: Map<String, Map<String, String>>, val follow: Map<String, Boolean>)

  @Synchronized
  fun snapshot(): Snapshot {
    if (!file.exists()) return Snapshot(0, emptyMap(), emptyMap(), emptyMap())
    val o = Json.parseToJsonElement(file.readText()) as JsonObject
    fun map(x: JsonObject?) = x?.mapValues { (it.value as JsonPrimitive).content }.orEmpty()
    return Snapshot(
      (o["version"] as JsonPrimitive).longOrNull ?: 0,
      map(o["global"] as? JsonObject),
      (o["overrides"] as? JsonObject)?.mapValues { map(it.value as JsonObject) }.orEmpty(),
      (o["follow"] as? JsonObject)?.mapValues { (it.value as JsonPrimitive).booleanOrNull ?: true }.orEmpty(),
    )
  }

  @Synchronized
  private fun save(s: Snapshot) {
    dir.mkdirs()
    file.writeText(buildJsonObject {
      put("version", s.version)
      putJsonObject("global") { for ((k, v) in s.global) put(k, v) }
      putJsonObject("overrides") { for ((p, m) in s.overrides) putJsonObject(p) { for ((k, v) in m) put(k, v) } }
      putJsonObject("follow") { for ((p, f) in s.follow) put(p, f) }
    }.toString())
  }

  /** Lo Store cambia il tema comune. Le chiavi sconosciute si scartano. */
  fun setGlobal(values: Map<String, String>): Snapshot {
    val s = snapshot()
    return s.copy(version = s.version + 1, global = s.global + values.filterKeys { it in GLOBAL_KEYS }).also(::save)
  }

  /** L'eccezione di un'app: segue il comune, oppure tiene i suoi valori (solo le chiavi che valgono per un'app). */
  fun setException(packageName: String, followGlobal: Boolean, override: Map<String, String>?): Snapshot {
    val s = snapshot()
    val overrides = if (override == null) s.overrides - packageName else s.overrides + (packageName to override.filterKeys { it in APP_KEYS })
    return s.copy(version = s.version + 1, overrides = overrides, follow = s.follow + (packageName to followGlobal)).also(::save)
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

  companion object {
    /** Le chiavi del contratto v1 (`prefs.json`, `accepted`). */
    val GLOBAL_KEYS = setOf("appearance.mode", "appearance.accent", "appearance.glass", "appearance.textScale", "appearance.contrast", "icon.set", "motion.reduced")
    /** Quelle che un'app può tenere diverse (`scopes: global+app`). */
    val APP_KEYS = setOf("appearance.mode", "appearance.accent", "appearance.glass", "icon.set")
  }
}
