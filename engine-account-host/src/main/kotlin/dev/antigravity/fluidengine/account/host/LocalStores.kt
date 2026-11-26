package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.Place
import dev.antigravity.fluidengine.account.PlacesAccess
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyKind
import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.custody.CustodyVault
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import dev.antigravity.fluidengine.sync.BlobSyncStore
import dev.antigravity.fluidengine.sync.FluidSync
import dev.antigravity.fluidengine.sync.SyncConfig
import dev.antigravity.fluidengine.sync.SyncDocument
import dev.antigravity.fluidengine.sync.SyncFailure
import dev.antigravity.fluidengine.sync.SyncPullResult
import dev.antigravity.fluidengine.sync.SyncPushBatch
import dev.antigravity.fluidengine.sync.SyncPushResult
import dev.antigravity.fluidengine.sync.SyncRelay
import dev.antigravity.fluidengine.sync.SyncRelayException
import dev.antigravity.fluidengine.sync.SyncSealContext
import dev.antigravity.fluidengine.sync.SyncSealException
import dev.antigravity.fluidengine.sync.SyncSealedPayload
import dev.antigravity.fluidengine.sync.SyncSealer
import dev.antigravity.fluidengine.sync.SyncSpace
import dev.antigravity.fluidengine.sync.SyncSpaceId
import java.io.File
import java.security.SecureRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Cosa può fare un'app coi luoghi comuni: niente, leggerli, o anche scriverli (`b\chiavi.md` § 8). */
enum class PlaceAccess { NONE, READ, READ_WRITE }

/**
 * I luoghi comuni, custoditi dallo Store (`b\chiavi.md` § 8; scelta N7 e B8): «I miei luoghi» (casa, scuola, lavoro,
 * altro) e il permesso di ogni app. Ogni app li vede solo se l'utente glielo permette: la prima volta lo Store chiede
 * «Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?» ([PlacesConsent]). MyCloud Gallery non li riceve
 * mai ([AppPolicy.Places.NEVER]). Funziona anche senza account.
 *
 * **F10.8 · Dove stanno.** I luoghi sono uno spazio di `engine-sync` (`places`), uno per luogo, con i campi `id`, `name`,
 * `kind`, `lat`, `lon`, `address` e per ognuno l'orologio logico di chi l'ha scritto: fra i dispositivi vince, campo
 * per campo, la scrittura più recente ([PlacesSync], cifrata con `K_com(places)`). Gli identificativi delle righe sono
 * casuali: l'`id` del luogo («casa») sta dentro, cifrato. Il registro è un blob chiuso con la cassaforte, come il file
 * dei permessi: la casa dice dove abiti, quindi niente in chiaro, e niente nel backup di Android.
 *
 * Un permesso **mancante** vuol dire «mai chiesto»; [PlaceAccess.NONE] scritto vuol dire «negato».
 *
 * **La cassaforte va data a parte** (F10.8): [vault] dev'essere una cassaforte **sua**, con un prefisso d'alias diverso
 * da quella della custodia (`KeystoreCustodyVault.Config(aliasPrefix = "dev.pampa.account.places")`). L'uscita
 * dall'account cancella le chiavi della custodia (`CustodyKeyring.wipe`), e i luoghi devono restare (R1: chi esce per
 * scelta tiene i suoi dati; senza account i luoghi restano comuni sul telefono). Solo un'uscita **da remoto** li
 * cancella, con [wipe].
 */
class PlacesStore(
  private val vault: CustodyVault,
  private val dir: File,
  private val policy: AppPolicy,
  private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE,
  /** Chiamato dopo ogni cambio, coi motivi dell'avviso (`places`): lo Store lo gira alle app ([AccountChangedBroadcaster]). */
  private val onChange: (List<String>) -> Unit = {},
) {
  private val file get() = File(dir, "luoghi.bin")
  private val registryFile get() = File(dir, "luoghi-registro.bin")
  private val keyClass get() = KeyClassPolicy.classOf(CustodyKeyKind.WORK_KEYS, choice)

  private val relay = SwitchRelay()
  private val sealer = SwitchSealer()

  @Volatile private var syncOrNull: FluidSync? = null

  /**
   * L'ultimo file dei luoghi che non si apriva più (i permessi o il registro), messo da parte accanto
   * (`<nome>.rotto-<ora>`), o `null`. Succede con una chiave persa o con un file rimasto a metà: i permessi ripartono
   * «mai chiesti» e il registro vuoto (con l'account i luoghi tornano dal server), invece di far cadere ogni chiamata.
   * Lo Store lo dice (R2). Con la classe chiusa (prima del primo sblocco) non si tocca niente: si riprova dopo.
   */
  @Volatile var setAside: File? = null
    private set

  /** Lo spazio di `engine-sync` dei luoghi. [PlacesSync] gli dà relay e sigillo quando c'è l'account. */
  internal val sync: FluidSync
    get() = syncOrNull ?: synchronized(this) { syncOrNull ?: newSync().also { syncOrNull = it } }

  private fun newSync(): FluidSync {
    dir.mkdirs()
    return FluidSync(
      store = BlobSyncStore(
        load = { if (registryFile.exists()) openOrSetAside(registryFile) { vault.open(keyClass, it, REGISTRY_AAD) } else null },
        save = { writeReplacing(registryFile, vault.seal(keyClass, it, REGISTRY_AAD)) },
      ),
      relay = relay,
      sealer = sealer,
      node = HostNode.of(dir),
      // Il server accetta al massimo 40 righe per invio (`changesPerRequest`, F10.3).
      config = SyncConfig(pushBatchSize = 40),
    )
  }

  internal val space: SyncSpace get() = sync.space(SPACE)

  /** Collega lo spazio al server (`PlacesSync`), o lo stacca con `null`. */
  internal fun attach(relay: SyncRelay?, sealer: SyncSealer?) {
    this.relay.target = relay
    this.sealer.target = sealer
  }

  /** Il file dei permessi (e dei luoghi di F10.4), o `null` se non c'è o se non si apriva più (allora è da parte). */
  @Synchronized
  private fun readFile(): JsonObject? {
    if (!file.exists()) return null
    return openOrSetAside(file) { Json.parseToJsonElement(vault.open(keyClass, it, AAD).decodeToString()) as JsonObject }
  }

  /** Apre [f] con [open]; se non si apre più (non per la classe chiusa) lo mette da parte e rende `null`. */
  private fun <T : Any> openOrSetAside(f: File, open: (ByteArray) -> T): T? = try {
    open(f.readBytes())
  } catch (e: CustodyLockedException) {
    throw e
  } catch (e: Exception) {
    setAside = setAsideBroken(f)
    null
  }

  @Synchronized
  private fun readAccess(): Map<String, PlaceAccess> {
    val o = readFile() ?: return emptyMap()
    return (o["access"] as? JsonObject)?.mapNotNull { (k, v) ->
      runCatching { PlaceAccess.valueOf((v as JsonPrimitive).content) }.getOrNull()?.let { k to it }
    }?.toMap().orEmpty()
  }

  @Synchronized
  private fun writeAccess(access: Map<String, PlaceAccess>) {
    val json = buildJsonObject { putJsonObject("access") { for ((k, v) in access) put(k, v.name) } }
    writeReplacing(file, vault.seal(keyClass, json.toString().toByteArray(), AAD))
  }

  /** La scelta dell'utente per [packageName], o `null` se non gliel'hanno mai chiesta. */
  fun decision(packageName: String): PlaceAccess? {
    val entry = policy.entryFor(packageName) ?: return null
    if (entry.places == AppPolicy.Places.NEVER) return PlaceAccess.NONE
    return readAccess()[packageName]
  }

  /** Il permesso di [packageName]; un'app che la tabella esclude ha sempre [PlaceAccess.NONE]. */
  fun access(packageName: String): PlaceAccess = decision(packageName) ?: PlaceAccess.NONE

  /** Il permesso come lo vede l'app ([PlacesAccess]): mai chiesto, negato, dato, o mai. */
  fun accessFor(packageName: String): PlacesAccess {
    val entry = policy.entryFor(packageName) ?: return PlacesAccess.NEVER
    if (entry.places == AppPolicy.Places.NEVER) return PlacesAccess.NEVER
    return when (readAccess()[packageName]) {
      null -> PlacesAccess.NOT_ASKED
      PlaceAccess.NONE -> PlacesAccess.DENIED
      PlaceAccess.READ -> PlacesAccess.READ
      PlaceAccess.READ_WRITE -> PlacesAccess.READ_WRITE
    }
  }

  /** Tutte le scelte fatte, per la pagina dell'account nello Store («Chi usa i tuoi luoghi»). */
  fun decisions(): Map<String, PlaceAccess> = readAccess()

  /**
   * La scelta dell'utente, dalla richiesta dello Store o dalla pagina dell'account. Rifiutata per chi non li può avere.
   * [PlaceAccess.NONE] è «negato» (o «revocato»): l'app non richiede più da sola.
   */
  fun setAccess(packageName: String, access: PlaceAccess): Boolean {
    val entry = policy.entryFor(packageName) ?: return false
    if (entry.places == AppPolicy.Places.NEVER && access != PlaceAccess.NONE) return false
    // Leggere e riscrivere sotto lo stesso lucchetto: due scelte insieme non rimettono la mappa di prima (R1).
    synchronized(this) { writeAccess(readAccess() + (packageName to access)) }
    onChange(listOf(WHAT))
    return true
  }

  /** Dimentica la scelta: l'app tornerà a chiedere (la pagina dell'account, «Chiedi di nuovo»). */
  fun forget(packageName: String) {
    synchronized(this) { writeAccess(readAccess() - packageName) }
    onChange(listOf(WHAT))
  }

  /** Tutti i luoghi: per lo Store. Alle app li dà [listFor]. */
  suspend fun all(): List<Place> = livePlaces().map { it.second }

  suspend fun listFor(packageName: String): List<Place>? = if (access(packageName) == PlaceAccess.NONE) null else all()

  /** Aggiunge o cambia un luogo. [packageName] `null` è lo Store stesso. */
  suspend fun put(packageName: String?, place: Place): Boolean {
    if (packageName != null && access(packageName) != PlaceAccess.READ_WRITE) return false
    val existing = livePlaces().filter { it.second.id == place.id }
    val write: dev.antigravity.fluidengine.sync.SyncEditor.() -> Unit = {
      text(F_ID, place.id)
      text(F_NAME, place.name)
      text(F_KIND, place.kind)
      real(F_LAT, place.latitude)
      real(F_LON, place.longitude)
      text(F_ADDRESS, place.address)
    }
    if (existing.isEmpty()) space.create(write) else space.edit(existing.first().first, write)
    onChange(listOf(WHAT))
    return true
  }

  suspend fun delete(packageName: String?, id: String): Boolean {
    if (packageName != null && access(packageName) != PlaceAccess.READ_WRITE) return false
    for ((recordId, _) in livePlaces(dedupe = false).filter { it.second.id == id }) space.delete(recordId)
    onChange(listOf(WHAT))
    return true
  }

  /**
   * I luoghi vivi, con l'identificativo della loro riga. Due dispositivi che creano «casa» senza vedersi fanno due
   * righe: si legge quella scritta per ultima, e [PlacesSync] cancella l'altra ([duplicates]).
   */
  internal suspend fun livePlaces(dedupe: Boolean = true): List<Pair<String, Place>> {
    space.load()
    val all = space.state.value.records.mapNotNull { (rid, doc) -> placeOf(doc)?.let { Triple(rid, it, newest(doc)) } }
    val kept = if (!dedupe) all else all.groupBy { it.second.id }.values.map { same -> same.maxWith(compareBy({ it.third }, { it.first })) }
    return kept.sortedBy { it.second.name.lowercase() }.map { it.first to it.second }
  }

  /**
   * Dopo un'uscita **da remoto** (telefono perso o rubato, `b\chiavi.md` § 4.4): via i luoghi, i permessi e le chiavi
   * della loro cassaforte. La casa dice dove abiti, e chi ha il telefono non deve trovarla. Un'uscita volontaria non la
   * chiama. Dopo, i luoghi ripartono vuoti.
   */
  fun wipe() {
    for (f in listOf(file, registryFile)) {
      if (f.exists()) {
        runCatching { f.writeBytes(ByteArray(f.length().toInt())) }
        f.delete()
      }
      removeSetAside(f)
    }
    vault.destroy()
    syncOrNull = null
    onChange(listOf(WHAT))
  }

  /** La sincronizzazione ha cambiato i luoghi: si avvisano le app. */
  internal fun changedFromSync() = onChange(listOf(WHAT))

  /** Le righe doppie dello stesso luogo, tranne quella che vince. */
  internal suspend fun duplicates(): List<String> {
    val live = livePlaces().map { it.first }.toSet()
    return livePlaces(dedupe = false).map { it.first }.filter { it !in live }
  }

  /**
   * I luoghi del file di F10.4, se ci sono: entrano nello spazio una volta, poi il file tiene solo i permessi. F10.4
   * non è mai uscito; c'è perché un telefono di prova non perda i suoi luoghi.
   */
  suspend fun migrateLegacy() {
    val o = readFile() ?: return
    val legacy = (o["places"] as? JsonArray) ?: return
    for (p in legacy) {
      val x = p as JsonObject
      fun s(k: String) = (x[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
      put(null, Place(s("id") ?: continue, s("name") ?: "", s("kind") ?: Place.OTHER, (x["lat"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, (x["lon"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, s("address")))
    }
    synchronized(this) { writeAccess(readAccess()) }
  }

  private fun placeOf(doc: SyncDocument): Place? {
    if (doc.isDeleted) return null
    val id = doc.text(F_ID) ?: return null
    return Place(id, doc.text(F_NAME) ?: "", doc.text(F_KIND) ?: Place.OTHER, doc.real(F_LAT) ?: 0.0, doc.real(F_LON) ?: 0.0, doc.text(F_ADDRESS))
  }

  private fun newest(doc: SyncDocument) = doc.fields.values.maxOf { it.stamp }

  /** Il relay dello spazio: quello del server quando c'è l'account, altrimenti «offline». */
  private class SwitchRelay : SyncRelay {
    @Volatile var target: SyncRelay? = null
    override suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult =
      (target ?: throw SyncRelayException(SyncFailure.Offline)).pull(space, since, limit)
    override suspend fun push(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult =
      (target ?: throw SyncRelayException(SyncFailure.Offline)).push(space, batch)
  }

  private class SwitchSealer : SyncSealer {
    @Volatile var target: SyncSealer? = null
    override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload =
      (target ?: throw SyncSealException("nessuna chiave dei luoghi")).seal(context, plaintext)
    override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray =
      (target ?: throw SyncSealException("nessuna chiave dei luoghi")).open(context, payload)
  }

  companion object {
    /** Lo spazio e la raccolta sul server: `places`, che il server riserva allo Store (`sync.places`, F10.3). */
    const val COLLECTION = "places"
    val SPACE = SyncSpaceId(COLLECTION)
    const val WHAT = "places"

    const val F_ID = "id"
    const val F_NAME = "name"
    const val F_KIND = "kind"
    const val F_LAT = "lat"
    const val F_LON = "lon"
    const val F_ADDRESS = "address"

    private val AAD = "pampa/v1/custody|luoghi".toByteArray()
    private val REGISTRY_AAD = "pampa/v1/custody|luoghi-registro".toByteArray()
  }
}

/** Il nome di questo dispositivo nell'orologio logico: 12 lettere e cifre minuscole, nate qui una volta, in [dir]. */
internal object HostNode {
  private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

  fun of(dir: File): String {
    val f = File(dir, "nodo.txt")
    f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.matches(Regex("^[a-z0-9]{12}$")) }?.let { return it }
    val random = SecureRandom()
    val node = String(CharArray(12) { ALPHABET[random.nextInt(ALPHABET.length)] })
    dir.mkdirs()
    f.writeText(node)
    return node
  }
}
