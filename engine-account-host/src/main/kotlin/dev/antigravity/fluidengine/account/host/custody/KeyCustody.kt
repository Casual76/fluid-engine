package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.auth.FluidPasskeyResult
import java.util.SortedMap
import java.util.TreeMap

/**
 * Il server ha mentito, o qualcosa non torna: il telefono **non usa** quello che ha ricevuto e lo segnala
 * (`b\chiavi.md` § 11, punti 2 e 10). Il messaggio dice cosa, mai un segreto.
 */
class CustodyTamperedException(message: String) : Exception(message)

/**
 * Un altro dispositivo ha creato un'epoca nuova ([serverEpoch]) che questa custodia non ha. La si apre con la porta di
 * questo dispositivo ([KeyCustody.catchUp] senza porta), in silenzio; se la porta del dispositivo manca, con una porta
 * vera (codice, passkey col PRF) o col passaggio. Fino ad allora si scrive con l'epoca che si ha (`b\chiavi.md` § 3.3).
 */
class CustodyBehindException(val serverEpoch: Int, val mine: Int) : Exception("c'è un'epoca nuova ($serverEpoch) che questo telefono non ha ancora ($mine)")

/**
 * La regola delle due porte (K2): niente parte cifrato finché non c'è una porta **fuori da questo telefono**, cioè il
 * codice di recupero o una passkey col PRF sincronizzata. Lo Store chiede il codice (`RecoveryCodePolicy`). Le porte
 * dei dispositivi non contano.
 */
class NeedsDoorException : Exception("Per proteggere i tuoi dati, crea un codice di recupero. Ti servirà se perdi questo telefono.")

/**
 * Il segreto di una porta, solo sul telefono: i 16 byte del codice di recupero o i 32 byte del PRF di una passkey.
 * [external] dice se la porta si apre **senza** questo telefono (K2): il codice sì; una passkey solo se è
 * sincronizzata (flag BS della passkey, che lo Store legge da `engine-auth`).
 */
class DoorSecret private constructor(val doorId: String, val kind: DoorKind, internal val secret: ByteArray, val external: Boolean) {
  override fun toString(): String = "DoorSecret($doorId)"

  companion object {
    fun recovery(code: RecoveryCode): DoorSecret = DoorSecret(RECOVERY_DOOR, DoorKind.RECOVERY, code.secret(), external = true)

    /** La porta di una passkey col PRF; [synced] è il flag BS. L'uscita del PRF non lascia mai il telefono (K6). */
    fun prf(credentialId: String, prfOutput: ByteArray, synced: Boolean): DoorSecret {
      require(prfOutput.size == 32) { "l'uscita del PRF è di 32 byte" }
      return DoorSecret("prf:$credentialId", DoorKind.PRF, prfOutput.copyOf(), external = synced)
    }

    /**
     * La porta di una passkey appena usata, letta da `engine-auth` (`FluidWebAuthnJson.readAssertion` o `readCreation`
     * con l'ingresso di [StorePrf]): l'id della credenziale, l'uscita del PRF e il flag BS (`backedUp`) dai dati
     * dell'autenticatore. [IllegalArgumentException] se il gestore non ha dato l'uscita del PRF: quella passkey non fa
     * da porta.
     */
    fun fromPasskey(result: FluidPasskeyResult): DoorSecret {
      val output = requireNotNull(result.prfOutput()) { "la passkey non ha dato l'uscita del PRF: non può fare da porta" }
      try {
        return prf(result.credentialId, output, synced = result.authenticatorData.backedUp)
      } finally {
        output.fill(0)
      }
    }

    /**
     * La porta di un dispositivo già dentro (`e2e.json`, `deviceDoor`): 32 byte casuali nati sul dispositivo, che restano
     * nella sua custodia (mai al server, mai in un backup, mai nel passaggio). Non conta per K2.
     */
    fun device(deviceId: String, secret: ByteArray): DoorSecret {
      require(secret.size == 32) { "il segreto della porta del dispositivo è di 32 byte" }
      return DoorSecret(deviceDoorId(deviceId), DoorKind.DEVICE, secret.copyOf(), external = false)
    }

    /** Rimette la porta del dispositivo salvata nella custodia (`CustodyKeyring`). */
    internal fun deviceFromStore(doorId: String, secret: ByteArray): DoorSecret = DoorSecret(doorId, DoorKind.DEVICE, secret.copyOf(), external = false)

    const val RECOVERY_DOOR = "recovery"

    /** L'id della porta di un dispositivo: `device:<deviceId>`. */
    fun deviceDoorId(deviceId: String): String = "device:$deviceId"
  }
}

/** Le chiavi di un'app per tutte le epoche, e l'epoca con cui scrive oggi. Quello che risponde `appKey` (`b\api.md` § 5.3). */
class AppKeySet(val current: Int, val keys: SortedMap<Int, ByteArray>) {
  override fun toString(): String = "AppKeySet(current=$current, epochs=${keys.keys})"
}

/** Per cosa servono le chiavi di un'app: la sincronizzazione dei suoi dati o il suo backup. */
enum class AppKeyPurpose(val wire: String) { APP("app"), BACKUP("backup") }

/**
 * La custodia della chiave dei dati dell'account (`b\chiavi.md`): tutte le epoche di `CD`, le porte che questo
 * telefono conosce, e le operazioni che le cambiano. **Logica pura**: non parla col server e non tocca il Keystore.
 * Riceve lo stato del server ([KeysView]), lo verifica, e restituisce i corpi da mandare; chi la usa li spedisce e,
 * se il server dice sì, tiene la custodia nuova (`CustodyKeyring` la chiude nella classe madre).
 *
 * Le verifiche, sempre prima di usare qualcosa che viene dal server:
 * - **K8**, i sigilli d'autenticità delle porte ([verifyTags]): una pubblica sostituita dal server non riceve mai la
 *   chiave; dopo un «perso o rubato» vale solo un tag dell'epoca nuova (critica S11);
 * - **K9**, la chiave ricevuta ([openWithDoor], [receiveTransfer], [verifyChain]): la radice col `check` del segreto
 *   della porta o col `mac` del QR, le epoche dopo con la catena dei `link`;
 * - una porta che questo telefono conosceva e che sparisce, o un'epoca che torna indietro, sono scoperte.
 */
class KeyCustody private constructor(
  val accountId: String,
  epochs: Map<Int, ByteArray>,
  knownDoors: Set<String>,
  private var device: DoorSecret? = null,
) {
  private val cds: TreeMap<Int, ByteArray> = TreeMap(epochs.mapValues { it.value.copyOf() })
  private val known: MutableSet<String> = knownDoors.toMutableSet()

  /** La porta di questo dispositivo (`device:<deviceId>`), se l'ha già creata. */
  val deviceDoorId: String? get() = device?.doorId

  internal fun deviceSecret(): ByteArray? = device?.secret?.copyOf()

  /** Le epoche che questa custodia tiene. */
  val epochs: Set<Int> get() = cds.keys

  /** L'epoca con cui si scrive oggi. */
  val current: Int get() = cds.lastKey()

  /** Le porte che questo telefono ha creato o aperto: se il server ne toglie una, se ne accorge. */
  val knownDoors: Set<String> get() = known.toSet()

  internal fun cd(epoch: Int): ByteArray? = cds[epoch]?.copyOf()

  override fun toString(): String = "KeyCustody(epochs=${cds.keys}, doors=$known)"

  // --- Le chiavi di lavoro (`b\chiavi.md` § 3) -----------------------------------------------------------------

  /** Le chiavi di [packageName] per [purpose], una per epoca della catena di oggi (K3: mai quelle di un'altra app). */
  fun appKeys(packageName: String, purpose: AppKeyPurpose, from: Int = rootOfChain()): AppKeySet {
    val out = TreeMap<Int, ByteArray>()
    for ((e, cd) in cds.tailMap(from, true)) {
      out[e] = when (purpose) {
        AppKeyPurpose.APP -> CustodyMath.appKey(cd, accountId, packageName)
        AppKeyPurpose.BACKUP -> CustodyMath.backupKey(cd, accountId, packageName)
      }
    }
    return AppKeySet(current, out)
  }

  /** Le chiavi comuni `K_com(name)`: «places», «rubrica», «persona». Solo lo Store (e Pampa Mail per la rubrica). */
  fun sharedKeys(name: String, from: Int = rootOfChain()): AppKeySet =
    AppKeySet(current, TreeMap(cds.tailMap(from, true).mapValues { CustodyMath.sharedKey(it.value, accountId, name) }))

  private var root: Int = cds.firstKey()

  /** La radice della catena di oggi: 1, o l'epoca dell'ultimo «Ricomincia». */
  fun rootOfChain(): Int = root

  // --- Le porte (`b\chiavi.md` § 3.1 e § 4.3) -------------------------------------------------------------------

  /**
   * La porta di [door] per tutte le epoche della catena di oggi, col tag dell'epoca di riferimento [reference] e il
   * `check` della radice. Prima di costruirla verifica lo stato del server ([verify]), se c'è.
   */
  fun buildDoor(door: DoorSecret, view: KeysView?): DoorInfo {
    val reference = if (view != null && view.started) verify(view) else rootOfChain()
    val salt = PampaCrypto.random(16)
    val keys = CustodyMath.doorKeys(door.secret, salt)
    val pub = PampaBase64.encode(keys.publicKey)
    val saltText = PampaBase64.encode(salt)
    val seals = cds.tailMap(rootOfChain(), true).map { (e, cd) ->
      SealInfo(e, PampaBase64.encode(CustodyMath.sealForDoor(keys.publicKey, door.doorId, e, cd)))
    }
    known += door.doorId
    return DoorInfo(
      doorId = door.doorId,
      kind = door.kind,
      pub = pub,
      salt = saltText,
      tag = PampaBase64.encode(CustodyMath.tag(cds.getValue(reference), door.doorId, door.kind.wire, pub, saltText, reference)),
      tagEpoch = reference,
      check = PampaBase64.encode(CustodyMath.check(door.secret, cds.getValue(rootOfChain()))),
      seals = seals,
    )
  }

  /** Una porta tolta da questo telefono (col sì del server): non la si cerca più. */
  fun forgetDoor(doorId: String) {
    known -= doorId
  }

  // --- Le verifiche (K8, K9) -------------------------------------------------------------------------------------

  /**
   * Verifica tutto lo stato del server prima di usarlo: catena, sigilli d'autenticità, porte conosciute, epoca che non
   * torna indietro. Restituisce l'epoca di riferimento dei tag, ricalcolata dalla catena (non presa dal server).
   */
  fun verify(view: KeysView): Int {
    if (view.epoch < current) throw CustodyTamperedException("l'epoca del server (${view.epoch}) è più vecchia della mia ($current)")
    if (view.epoch > current) throw CustodyBehindException(view.epoch, current)
    verifyChain(view)
    return verifyTags(view)
  }

  /** K9: ogni epoca dopo la radice è legata alla precedente, col suo tipo. Restituisce la radice. */
  fun verifyChain(view: KeysView): Int {
    val list = view.epochs.sortedBy { it.epoch }
    if (list.isEmpty()) throw CustodyTamperedException("catena delle epoche vuota")
    val first = list.first()
    if (first.kind != EpochKind.FIRST && first.kind != EpochKind.RESET) throw CustodyTamperedException("la catena non parte da una radice")
    if (first.link != null) throw CustodyTamperedException("la radice non ha collegamento")
    if (list.last().epoch != view.epoch) throw CustodyTamperedException("catena delle epoche incompleta")
    for (i in 1 until list.size) {
      val e = list[i]
      if (e.epoch != list[i - 1].epoch + 1) throw CustodyTamperedException("catena delle epoche con un buco")
      if (e.kind != EpochKind.NORMAL && e.kind != EpochKind.LOST) throw CustodyTamperedException("epoca ${e.epoch} di un tipo che non sta in mezzo alla catena")
      val prev = cds[e.epoch - 1]
      val cur = cds[e.epoch]
      val link = e.link?.let { runCatching { PampaBase64.decode(it) }.getOrNull() }
      if (prev == null || cur == null || link == null || !PampaCrypto.equal(link, CustodyMath.link(prev, e.epoch, e.kind.wire, cur))) {
        throw CustodyTamperedException("epoca ${e.epoch} non legata alla catena (K9)")
      }
    }
    if (cds[first.epoch] == null) throw CustodyTamperedException("non ho la radice della catena")
    root = first.epoch
    return first.epoch
  }

  /**
   * K8: il sigillo d'autenticità di ogni porta è fatto con la chiave dei dati, all'epoca di riferimento giusta: la
   * radice, o l'ultimo «perso o rubato» (S11). Una porta che conoscevo e che manca è scoperta. Restituisce il riferimento.
   */
  fun verifyTags(view: KeysView): Int {
    val chain = view.epochs.filter { it.epoch >= root }
    val reference = (chain.filter { it.kind != EpochKind.NORMAL }.maxOfOrNull { it.epoch } ?: root).coerceAtLeast(root)
    for (d in view.doors) {
      if (d.tagEpoch < reference) throw CustodyTamperedException("porta ${d.doorId}: sigillo d'autenticità di un'epoca superata (S11)")
      val cd = cds[d.tagEpoch] ?: throw CustodyTamperedException("porta ${d.doorId}: sigillo d'autenticità di un'epoca che non conosco")
      val tag = runCatching { PampaBase64.decode(d.tag) }.getOrNull()
      if (tag == null || !PampaCrypto.equal(tag, CustodyMath.tag(cd, d.doorId, d.kind.wire, d.pub, d.salt, d.tagEpoch))) {
        throw CustodyTamperedException("porta ${d.doorId}: sigillo d'autenticità falso (K8)")
      }
    }
    // Le porte degli altri dispositivi se ne vanno da sole quando quel dispositivo esce: non sono un segnale. Il codice,
    // le passkey e la porta di questo dispositivo, se spariscono, sì.
    for (id in known) {
      if (id.startsWith(DEVICE_PREFIX) && id != device?.doorId) continue
      if (view.doors.none { it.doorId == id }) throw CustodyTamperedException("la porta $id è sparita")
    }
    return reference
  }

  // --- Le epoche (`b\chiavi.md` § 6) ----------------------------------------------------------------------------

  /**
   * Un'epoca nuova, sigillata per **tutte** le porte del server (K5), dopo averne verificato i tag. Con [kind] =
   * [EpochKind.LOST] rifà anche i sigilli d'autenticità con la chiave nuova (S11). Restituisce il corpo da mandare
   * e la custodia che vale **se il server dice sì**: fino ad allora questa non cambia.
   */
  fun rotate(view: KeysView, kind: EpochKind = EpochKind.NORMAL): Pending<EpochUpload> {
    require(kind == EpochKind.NORMAL || kind == EpochKind.LOST) { "una rotazione è normal o lost" }
    verify(view)
    val next = current + 1
    val cd = PampaCrypto.random(PampaCrypto.KEY_BYTES)
    val seals = view.doors.map { d ->
      val pub = runCatching { PampaBase64.decode(d.pub) }.getOrElse { throw CustodyTamperedException("pubblica illeggibile") }
      d.doorId to PampaBase64.encode(CustodyMath.sealForDoor(pub, d.doorId, next, cd))
    }
    val tags = if (kind == EpochKind.LOST) {
      view.doors.map { d -> d.doorId to PampaBase64.encode(CustodyMath.tag(cd, d.doorId, d.kind.wire, d.pub, d.salt, next)) }
    } else emptyList()
    val upload = EpochUpload(next, kind, PampaBase64.encode(CustodyMath.link(cds.getValue(current), next, kind.wire, cd)), seals, tags)
    val after = KeyCustody(accountId, cds + (next to cd), known, device).also { it.root = root }
    return Pending(upload, after)
  }

  /**
   * «Ricomincia» (`b\chiavi.md` § 5): una chiave nuova, un'epoca nuova di tipo `reset` che fa da radice, e le porte
   * nuove. Le epoche vecchie se ne vanno: i dati chiusi con quelle sono persi. Serve almeno una porta esterna (K2).
   */
  fun reset(doors: List<DoorSecret>): Pending<ResetUpload> = startChain(accountId, current + 1, EpochKind.RESET, doors).let { (custody, doorsInfo) ->
    Pending(ResetUpload(current + 1, doorsInfo), custody)
  }

  // --- Il passaggio fra telefoni (`b\chiavi.md` § 4.2) ------------------------------------------------------------

  /**
   * Il telefono **vecchio** ha inquadrato il QR del nuovo e l'utente ha confermato con l'impronta: sigilla tutte le
   * epoche verso `N` e firma il sigillo col segreto `s` del QR. Né `N` né `s` passano dal server.
   */
  fun sealTransfer(qr: TransferQr): TransferSeal {
    val sealed = PampaHpke.seal(qr.n, CustodyMath.transferInfo(qr.transferId), PampaCrypto.EMPTY, TransferPayload.encode(cds.tailMap(rootOfChain(), true)))
    return TransferSeal(PampaBase64.encode(sealed), PampaBase64.encode(CustodyMath.transferMac(qr.s, qr.transferId, qr.n, sealed)))
  }

  /**
   * Il telefono è rimasto indietro di un'epoca ([CustodyBehindException]) e apre una porta per averla. La custodia
   * nuova deve **contenere** questa: ogni epoca che avevo deve essere la stessa, altrimenti il server ha cambiato le
   * chiavi alle mie spalle.
   */
  fun catchUp(view: KeysView, door: DoorSecret): KeyCustody {
    val opened = openWithDoor(accountId, view, door)
    for ((e, cd) in cds) {
      if (e < opened.rootOfChain()) continue
      val theirs = opened.cds[e] ?: throw CustodyTamperedException("dopo la porta manca l'epoca $e")
      if (!PampaCrypto.equal(cd, theirs)) throw CustodyTamperedException("l'epoca $e non è più quella che avevo")
    }
    // Una radice nuova è un «Ricomincia» fatto da un altro dispositivo: il server non lo può inventare, perché il
    // `check` della radice lo sa fare solo chi ha il segreto della porta (K9). Le epoche di prima sono perse.
    opened.known += known
    opened.device = device
    return opened
  }

  /**
   * Rimettersi in pari **in silenzio** con la porta di questo dispositivo (`e2e.json`, `deviceDoor.catchUp`): chi ha
   * ruotato ha sigillato l'epoca nuova anche per lei. Gli stessi controlli di quando si apre una porta (la pubblica,
   * K9 sulla radice, la catena, i tag K8), e le epoche che avevo devono restare le stesse. Senza porta del dispositivo,
   * o se il server l'ha tolta, [CustodyBehindException]: serve una porta vera o il passaggio.
   */
  fun catchUp(view: KeysView): KeyCustody {
    val mine = device ?: throw CustodyBehindException(view.epoch, current)
    if (view.door(mine.doorId) == null) throw CustodyBehindException(view.epoch, current)
    return catchUp(view, mine)
  }

  /**
   * La porta di questo dispositivo, appena la custodia ha la chiave (dopo il PRF, il codice o il QR, e una volta per i
   * dispositivi già dentro). Restituisce il corpo di `PUT /v1/keys/doors/device:<deviceId>` e la custodia che la ricorda.
   */
  fun createDeviceDoor(deviceId: String, view: KeysView): Pending<DoorInfo> {
    val secret = DoorSecret.device(deviceId, PampaCrypto.random(32))
    val after = KeyCustody(accountId, cds, known, secret).also { it.root = root }
    return Pending(after.buildDoor(secret, view), after)
  }

  internal fun copyEpochs(): Map<Int, ByteArray> = cds.mapValues { it.value.copyOf() }

  /** Quello che cambia se il server dice sì: il corpo da mandare e la custodia di dopo. */
  class Pending<T>(val upload: T, val custody: KeyCustody)

  companion object {

    /**
     * La prima volta che serve la cifratura (`b\chiavi.md` § 4.1): `CD_1` nasce qui e le porte date diventano le prime.
     * **K2**: senza almeno una porta esterna, [NeedsDoorException], e niente parte.
     */
    fun start(accountId: String, doors: List<DoorSecret>): Pending<FirstUpload> {
      val (custody, infos) = startChain(accountId, 1, EpochKind.FIRST, doors)
      return Pending(FirstUpload(infos, EpochUpload(1, EpochKind.FIRST, link = null, seals = emptyList())), custody)
    }

    private fun startChain(accountId: String, epoch: Int, kind: EpochKind, doors: List<DoorSecret>): Pair<KeyCustody, List<DoorInfo>> {
      if (doors.none { it.external }) throw NeedsDoorException()
      val custody = KeyCustody(accountId, mapOf(epoch to PampaCrypto.random(PampaCrypto.KEY_BYTES)), emptySet())
      custody.root = epoch
      val infos = doors.map { custody.buildDoor(it, view = null) }
      return custody to infos
    }

    /**
     * Un telefono nuovo apre una porta (`b\chiavi.md` § 4.2): ricava la sua privata dal segreto, apre i sigilli,
     * verifica la radice col `check` (K9), la catena dei `link` e i sigilli d'autenticità (K8).
     */
    fun openWithDoor(accountId: String, view: KeysView, door: DoorSecret): KeyCustody {
      val info = view.door(door.doorId) ?: throw CustodyTamperedException("la porta ${door.doorId} non c'è")
      if (info.kind != door.kind) throw CustodyTamperedException("la porta ${door.doorId} è di un altro tipo")
      val salt = runCatching { PampaBase64.decode(info.salt) }.getOrElse { throw CustodyTamperedException("sale illeggibile") }
      val keys = CustodyMath.doorKeys(door.secret, salt)
      if (PampaBase64.encode(keys.publicKey) != info.pub) throw CustodyTamperedException("la pubblica della porta non è quella del mio segreto (K8)")
      val epochs = TreeMap<Int, ByteArray>()
      for (s in info.seals) {
        val sealed = runCatching { PampaBase64.decode(s.sealed) }.getOrElse { throw CustodyTamperedException("sigillo illeggibile") }
        epochs[s.epoch] = try {
          CustodyMath.openDoorSeal(keys, door.doorId, s.epoch, sealed)
        } catch (e: PampaCryptoException) {
          throw CustodyTamperedException("sigillo dell'epoca ${s.epoch} non apribile")
        }
      }
      val custody = KeyCustody(accountId, epochs, emptySet())
      val root = custody.verifyChain(view)
      val check = runCatching { PampaBase64.decode(info.check) }.getOrNull()
      val rootCd = epochs[root] ?: throw CustodyTamperedException("manca il sigillo della radice")
      if (check == null || !PampaCrypto.equal(check, CustodyMath.check(door.secret, rootCd))) {
        throw CustodyTamperedException("la radice non è quella della porta (K9)")
      }
      for (e in view.epochs) if (e.epoch >= root && epochs[e.epoch] == null) throw CustodyTamperedException("manca il sigillo dell'epoca ${e.epoch}")
      custody.known += door.doorId
      custody.verifyTags(view)
      custody.known += view.doors.map { it.doorId }
      return custody.trimmedTo(root)
    }

    /**
     * Il telefono **nuovo** ritira il sigillo del passaggio: controlla il `mac` col segreto del QR prima di tutto,
     * apre con la sua effimera, e verifica la catena e i sigilli d'autenticità (K9). Poi chi chiama dimentica
     * l'offerta ([TransferOffer.forget]).
     */
    fun receiveTransfer(accountId: String, offer: TransferOffer, seal: TransferSeal, view: KeysView): KeyCustody {
      val sealed = runCatching { PampaBase64.decode(seal.sealed) }.getOrElse { throw CustodyTamperedException("sigillo del passaggio illeggibile") }
      val mac = runCatching { PampaBase64.decode(seal.mac) }.getOrNull()
      if (mac == null || !PampaCrypto.equal(mac, CustodyMath.transferMac(offer.secretS(), offer.transferId, offer.publicKey, sealed))) {
        throw CustodyTamperedException("sigillo del passaggio non fatto col segreto del QR")
      }
      val plain = try {
        PampaHpke.open(offer.keyPair(), CustodyMath.transferInfo(offer.transferId), PampaCrypto.EMPTY, sealed)
      } catch (e: PampaCryptoException) {
        throw CustodyTamperedException("sigillo del passaggio non apribile")
      }
      val epochs = try {
        TransferPayload.decode(plain)
      } catch (e: PampaCryptoException) {
        throw CustodyTamperedException("passaggio illeggibile")
      }
      val custody = KeyCustody(accountId, epochs, emptySet())
      val root = custody.verifyChain(view)
      for (e in view.epochs) if (e.epoch >= root && epochs[e.epoch] == null) throw CustodyTamperedException("al passaggio manca l'epoca ${e.epoch}")
      custody.verifyTags(view)
      custody.known += view.doors.map { it.doorId }
      return custody.trimmedTo(root)
    }

    /** Rimette una custodia salvata (`CustodyKeyring`). */
    internal fun restore(accountId: String, epochs: Map<Int, ByteArray>, root: Int, knownDoors: Set<String>, device: DoorSecret? = null): KeyCustody =
      KeyCustody(accountId, epochs, knownDoors, device).also { it.root = root }

    private const val DEVICE_PREFIX = "device:"
  }

  private fun trimmedTo(root: Int): KeyCustody = KeyCustody(accountId, cds.tailMap(root, true), known, device).also { it.root = root }
}

/** Il primo avvio della cifratura: le porte da creare e la prima epoca (`POST /v1/recovery` o `PUT …/doors`, poi `POST /v1/keys/epochs`). */
class FirstUpload(val doors: List<DoorInfo>, val epoch: EpochUpload)
