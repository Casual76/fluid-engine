package dev.antigravity.fluidengine.vault

import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Una sezione della cassaforte: una chiave dei dati sua (256 bit casuali), chiusa da una chiave del Keystore, e tutto
 * quello che ci sta dentro: [values], i file ([file]), i DataStore ([dataStore], [preferencesDataStore]) e i byte
 * sigillati a mano ([seal], [open]).
 *
 * **Come sta chiusa** (la «busta», come il portachiavi di Tink e le chiavi di Signal):
 * - [VaultAccess.Device]: la chiave dei dati è chiusa da una chiave AES del Keystore senza autenticazione, StrongBox
 *   quando c'è. Si apre da sola al primo uso e resta in memoria finché l'app vive (o fino allo schermo spento, con
 *   `whileUnlocked`).
 * - [VaultAccess.Biometric]: la chiave dei dati è chiusa da una coppia RSA del Keystore la cui privata chiede l'impronta
 *   forte a ogni uso (e un'impronta aggiunta la invalida), e una seconda volta per la riserva: una coppia che lavora per
 *   30 s dopo il codice del telefono ([VaultReserve.DeviceCredential]), o il segreto dell'app chiuso da una chiave del
 *   Keystore ([VaultReserve.AppSecret]). Si chiude con la pubblica: la sezione nasce, e dopo un'impronta aggiunta si
 *   rifà la chiave, senza un secondo dialogo.
 *
 * Aperta, la chiave dei dati sta in memoria e si richiude da sola dopo [VaultAccess.Biometric.relockAfter] senza usarla,
 * allo schermo spento e con [lock]. Ogni dato ha una chiave derivata sua (HKDF) e porta i suoi dati associati.
 */
class VaultSection internal constructor(
  private val vault: FluidVault,
  /** Il nome della sezione. */
  val name: String,
  /** Chi la apre. */
  val access: VaultAccess,
) {
  private val mutex = Mutex()

  @Volatile private var dataKey: ByteArray? = null

  @Volatile private var deadline: Long = Long.MAX_VALUE
  private var timer: Job? = null
  private val _state = MutableStateFlow<VaultSectionState>(VaultSectionState.Closed)

  /** La cartella dei dati della sezione (fuori dal backup, come tutta la cassaforte). */
  internal val dir: File = File(vault.dir, name)
  private val recordFile = File(vault.dir, "$name.key")
  /** Le frasi pronte della sua cassaforte. */
  val labels: VaultLabels get() = vault.labels
  internal val vaultName: String get() = vault.name

  /** I DataStore cifrati della cassaforte, uno per file. */
  internal fun vaultDataStores(): HashMap<String, Any> = vault.dataStores

  /** Lo stato della sezione: chiusa, aperta (fino a quando), persa. */
  val state: StateFlow<VaultSectionState> = _state.asStateFlow()

  /** Vero se la chiave dei dati è in memoria adesso. */
  val isOpen: Boolean get() = dataKey != null && vault.clock() < deadline

  /** I valori della sezione: testi e byte per nome, cifrati in un file solo. */
  val values: VaultValues by lazy { VaultValues(this) }

  private val files = HashMap<String, VaultFile>()

  /** Il file cifrato [fileName] della sezione (lettere, cifre, `.`, `-`, `_`). */
  fun file(fileName: String): VaultFile = synchronized(files) {
    files.getOrPut(fileName) { VaultFile(this, fileName) }
  }

  // --- aprire e chiudere ------------------------------------------------------------------------------------------

  /**
   * Apre la sezione. Per [VaultAccess.Device] non chiede niente (e non serve chiamarla: il primo uso la apre). Per
   * [VaultAccess.Biometric] mostra il dialogo dell'impronta con [authenticator]; se l'impronta non va, o la persona
   * sceglie la riserva, passa alla riserva:
   * - il codice del telefono: un secondo dialogo, e se l'impronta era cambiata la chiave si rifà da sola;
   * - il segreto dell'app: [VaultUnlock.NeedsSecret], e l'app lo chiede e chiama [unlockWithSecret].
   *
   * La prima volta la sezione nasce, vuota e aperta, senza dialogo.
   */
  suspend fun unlock(
    authenticator: VaultAuthenticator,
    prompt: VaultPrompt = VaultPrompt(vault.labels.unlockTitle),
  ): VaultUnlock = try {
    unlockSafely(authenticator, prompt)
  } catch (e: VaultException) {
    e.toUnlock()
  } catch (e: GeneralSecurityException) {
    // Un errore del Keystore che non ha un nome nostro: mai un'app che si chiude.
    VaultUnlock.Unavailable(VaultUnavailability.KeystoreError)
  } catch (e: ProviderException) {
    VaultUnlock.Unavailable(VaultUnavailability.KeystoreError)
  } catch (e: IOException) {
    // R2 · La scheda della sezione non si legge (un errore del disco): un esito, «riprova fra poco», non un'eccezione
    // che fa cadere chi chiama (`FluidVaultContent` la lanciava in uno scope di composizione).
    VaultUnlock.Unavailable(VaultUnavailability.KeystoreError)
  }

  private suspend fun unlockSafely(authenticator: VaultAuthenticator, prompt: VaultPrompt): VaultUnlock {
    val biometric = access as? VaultAccess.Biometric ?: return try {
      withKey { }
      VaultUnlock.Opened()
    } catch (e: VaultException) {
      e.toUnlock()
    }
    if (isOpen) {
      touch()
      return VaultUnlock.Opened()
    }
    return mutex.withLock {
      if (isOpen) return@withLock VaultUnlock.Opened()
      val record = withContext(vault.io) { readRecord() } ?: return@withLock create(biometric)
      unlockBiometric(record, biometric, authenticator, prompt)
    }
  }

  /**
   * Apre la sezione col segreto dell'app ([VaultReserve.AppSecret]): il PIN dell'app, una frase. Se l'impronta era
   * cambiata, la sua chiave si rifà. Il segreto non si tiene: chi lo passa lo azzera dopo.
   */
  suspend fun unlockWithSecret(secret: CharArray): VaultUnlock = mutex.withLock {
    if (isOpen) return@withLock VaultUnlock.Opened()
    try {
      withContext(vault.io) {
        val record = readRecord() ?: return@withContext VaultUnlock.Failed(vault.labels.noSecret)
        val wrapped = record.reserveWrapped
        val reserveAlias = record.reserveAlias
        if (record.reserve != VaultReserve.AppSecret || wrapped == null || reserveAlias == null) {
          return@withContext VaultUnlock.Failed(vault.labels.noSecret)
        }
        val inner = openWithKeystore(reserveAlias, wrapped, record.associated(ReservePurpose))
        val reader = VaultRecordReader(inner)
        val salt = reader.bytes() ?: throw VaultException.Corrupt()
        val iterations = reader.int()
        val sealedKey = reader.bytes() ?: throw VaultException.Corrupt()
        val derived = VaultCrypto.pbkdf2(secret, salt, iterations)
        val key = try {
          VaultCrypto.open(derived, sealedKey, record.associated(SecretPurpose))
        } catch (e: VaultException.Corrupt) {
          return@withContext VaultUnlock.WrongSecret
        } finally {
          derived.fill(0)
        }
        val invalid = record.biometricAlias?.let { probe(it) }
        if (invalid != null) rekeyBiometric(record, key)
        openWith(key)
        VaultUnlock.Opened(recovered = invalid)
      }
    } catch (e: VaultException) {
      e.toUnlock()
    } catch (e: IOException) {
      VaultUnlock.Unavailable(VaultUnavailability.KeystoreError)
    }
  }

  /**
   * Dà alla sezione (aperta) il segreto dell'app come riserva ([VaultReserve.AppSecret]), o lo cambia. Non tiene
   * [secret]: chi lo passa lo azzera dopo.
   *
   * @throws VaultException.Locked se la sezione è chiusa.
   */
  suspend fun setReserveSecret(secret: CharArray) {
    check(access is VaultAccess.Biometric && access.reserve == VaultReserve.AppSecret) {
      "la sezione «$name» non ha il segreto dell'app come riserva"
    }
    val key = currentKey()
    try {
      mutex.withLock {
        withContext(vault.io) {
          val record = readRecord() ?: throw VaultException.Locked(name)
          val alias = record.reserveAlias ?: alias("s", record.generation)
          if (!vault.keystore.contains(alias)) vault.keystore.createSecretKey(alias, VaultKeySpec())
          val salt = VaultCrypto.randomBytes(16)
          val derived = VaultCrypto.pbkdf2(secret, salt, vault.secretIterations)
          val sealedKey = try {
            VaultCrypto.seal(derived, key, record.associated(SecretPurpose))
          } finally {
            derived.fill(0)
          }
          val inner = VaultRecordWriter().bytes(salt).int(vault.secretIterations).bytes(sealedKey).toByteArray()
          val updated = record.copy(reserveAlias = alias, reserveWrapped = sealWithKeystore(alias, inner, record.associated(ReservePurpose)))
          writeRecord(updated)
        }
      }
    } finally {
      key.fill(0)
    }
  }

  /** Chiude subito la sezione: la chiave dei dati esce dalla memoria. Una sezione «solo telefono» si riaprirà da sola. */
  fun lock() {
    val key = dataKey
    dataKey = null
    deadline = Long.MAX_VALUE
    timer?.cancel()
    timer = null
    key?.fill(0)
    values.dropCache()
    if (_state.value !is VaultSectionState.Lost) _state.value = VaultSectionState.Closed
  }

  /**
   * Butta la sezione: le sue chiavi nel Keystore, la sua scheda e tutti i suoi dati. È la strada dopo
   * [VaultUnlock.Lost]: si ricomincia da una sezione vuota.
   */
  suspend fun reset() {
    lock()
    mutex.withLock {
      withContext(vault.io) {
        readRecordOrNull()?.let { record ->
          listOfNotNull(record.biometricAlias, record.keyAlias, record.reserveAlias).forEach { vault.keystore.delete(it) }
        }
        recordFile.delete()
        dir.deleteRecursively()
      }
    }
    _state.value = VaultSectionState.Closed
  }

  // --- i byte di chi chiama (F10.4) -------------------------------------------------------------------------------

  /**
   * Sigilla [plain] con la chiave della sezione: la scatola che torna si apre solo su questo telefono, con questa
   * sezione aperta (e, se è all'impronta, dopo l'impronta). Per il materiale che l'app genera e deve rileggere: una
   * chiave simmetrica derivata, una radice privata.
   *
   * La scatola è `"FV" 1 1 ‖ nonce(12) ‖ testo cifrato ‖ tag(16)`, AES-256-GCM con una chiave derivata dalla chiave
   * della sezione.
   *
   * @param associated i dati associati: di chi è e dove sta il dato (l'account, l'epoca). Non cifrati, ma la scatola
   *   si apre solo con gli stessi.
   * @throws VaultException.Locked se la sezione è chiusa.
   */
  suspend fun seal(plain: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray = withKey { key ->
    val boxKey = VaultCrypto.hkdf(key, ByteArray(0), BoxInfo)
    try {
      BoxHeader + VaultCrypto.seal(boxKey, plain, BoxHeader + associated)
    } finally {
      boxKey.fill(0)
    }
  }

  /**
   * Apre una scatola di [seal]. Chi riceve i byte li azzera quando ha finito.
   *
   * @throws VaultException.Corrupt se la scatola non torna (toccata, di un'altra sezione, altri dati associati).
   * @throws VaultException.Locked se la sezione è chiusa.
   */
  suspend fun open(sealed: ByteArray, associated: ByteArray = ByteArray(0)): ByteArray {
    if (sealed.size < BoxHeader.size || !sealed.copyOfRange(0, BoxHeader.size).contentEquals(BoxHeader)) {
      throw VaultException.Corrupt("non è una scatola della cassaforte")
    }
    return withKey { key ->
      val boxKey = VaultCrypto.hkdf(key, ByteArray(0), BoxInfo)
      try {
        VaultCrypto.open(boxKey, sealed.copyOfRange(BoxHeader.size, sealed.size), BoxHeader + associated)
      } finally {
        boxKey.fill(0)
      }
    }
  }

  // --- dentro: la chiave dei dati ----------------------------------------------------------------------------------

  /**
   * Esegue [block] con una copia della chiave dei dati, su [FluidVault.io]. Una sezione «solo telefono» si apre qui se
   * serve; una all'impronta chiusa dà [VaultException.Locked]. Ogni uso sposta in avanti la richiusura.
   */
  internal suspend fun <T> withKey(block: suspend (ByteArray) -> T): T {
    val key = currentKey()
    touch()
    return try {
      withContext(vault.io) { block(key) }
    } finally {
      key.fill(0)
    }
  }

  private suspend fun currentKey(): ByteArray = try {
    currentKeyOrThrow()
  } catch (e: GeneralSecurityException) {
    throw VaultException.Unavailable(VaultUnavailability.KeystoreError, e)
  } catch (e: ProviderException) {
    throw VaultException.Unavailable(VaultUnavailability.KeystoreError, e)
  }

  private suspend fun currentKeyOrThrow(): ByteArray {
    dataKey?.takeIf { vault.clock() < deadline }?.let { return it.copyOf() }
    if (dataKey != null) lock()
    if (access is VaultAccess.Biometric) throw VaultException.Locked(name)
    return mutex.withLock {
      dataKey?.let { return@withLock it.copyOf() }
      withContext(vault.io) {
        val record = readRecord()
        if (record == null) {
          createDevice()
        } else {
          val keyAlias = record.keyAlias ?: throw VaultException.Corrupt()
          val keyWrapped = record.keyWrapped ?: throw VaultException.Corrupt()
          val key = try {
            openWithKeystore(keyAlias, keyWrapped, record.associated(KeyPurpose))
          } catch (e: VaultException.NeedsAuthentication) {
            // Una sezione «solo a telefono sbloccato», a telefono bloccato.
            throw VaultException.Locked(name)
          }
          openWith(key)
          key
        }
      }
    }
  }

  private fun createDevice(): ByteArray {
    val whileUnlocked = (access as VaultAccess.Device).whileUnlocked
    val record = newRecord(AccessDevice)
    val keyAlias = alias("k", 1)
    vault.keystore.delete(keyAlias)
    vault.keystore.createSecretKey(keyAlias, VaultKeySpec(unlockedDeviceRequired = whileUnlocked))
    val key = VaultCrypto.randomBytes(VaultCrypto.KeyBytes)
    writeRecord(record.copy(keyAlias = keyAlias, keyWrapped = sealWithKeystore(keyAlias, key, record.associated(KeyPurpose))))
    openWith(key)
    return key
  }

  private suspend fun create(biometric: VaultAccess.Biometric): VaultUnlock = try {
    withContext(vault.io) {
      if (!vault.keystore.isDeviceSecure) throw VaultException.Unavailable(VaultUnavailability.NoScreenLock)
      val base = newRecord(AccessBiometric, biometric.reserve)
      val key = VaultCrypto.randomBytes(VaultCrypto.KeyBytes)
      var record = base
      record = withBiometricKey(record, key, required = biometric.reserve != VaultReserve.DeviceCredential)
      if (biometric.reserve == VaultReserve.DeviceCredential) {
        val reserveAlias = alias("r", 1)
        vault.keystore.delete(reserveAlias)
        vault.keystore.createKeyPair(reserveAlias, VaultKeySpec(auth = VaultKeyAuth.Recent(VaultDefaults.ReserveWindowSeconds)))
        val public = vault.keystore.publicKey(reserveAlias) ?: throw VaultException.Invalidated(VaultInvalidation.KeyLost)
        record = record.copy(reserveAlias = reserveAlias, reserveWrapped = VaultCrypto.rsaSeal(public, key))
      }
      writeRecord(record)
      openWith(key)
      VaultUnlock.Opened()
    }
  } catch (e: VaultException) {
    e.toUnlock()
  }

  /** Mette nella scheda la coppia dell'impronta per [key]. Senza impronte, se non è [required], la lascia vuota. */
  private fun withBiometricKey(record: SectionRecord, key: ByteArray, required: Boolean): SectionRecord {
    val biometricAlias = alias("b", record.generation)
    vault.keystore.delete(biometricAlias)
    return try {
      vault.keystore.createKeyPair(biometricAlias, VaultKeySpec(auth = VaultKeyAuth.BiometricEachUse))
      val public = vault.keystore.publicKey(biometricAlias) ?: throw VaultException.Invalidated(VaultInvalidation.KeyLost)
      record.copy(biometricAlias = biometricAlias, biometricWrapped = VaultCrypto.rsaSeal(public, key))
    } catch (e: VaultException.Unavailable) {
      if (required || e.reason != VaultUnavailability.NoBiometrics) throw e
      record.copy(biometricAlias = null, biometricWrapped = null)
    }
  }

  private suspend fun unlockBiometric(
    record: SectionRecord,
    biometric: VaultAccess.Biometric,
    authenticator: VaultAuthenticator,
    prompt: VaultPrompt,
  ): VaultUnlock {
    var invalid: VaultInvalidation? = null
    val biometricAlias = record.biometricAlias
    val biometricWrapped = record.biometricWrapped
    if (biometricAlias != null && biometricWrapped != null) {
      val op = try {
        withContext(vault.io) { vault.keystore.rsaDecrypt(biometricAlias) }
      } catch (e: VaultException.Invalidated) {
        invalid = e.reason
        null
      } catch (e: VaultException) {
        return e.toUnlock()
      }
      if (op != null) {
        val request = VaultAuthRequest(
          method = VaultAuthMethod.Biometric,
          cipher = op.cipher,
          title = prompt.title,
          subtitle = prompt.subtitle,
          fallback = when (record.reserve) {
            VaultReserve.DeviceCredential -> vault.labels.useDeviceCredential
            VaultReserve.AppSecret -> if (record.reserveWrapped != null) vault.labels.useAppSecret else null
            VaultReserve.None -> null
          },
        )
        when (val result = ask(authenticator, request)) {
          VaultAuthResult.Success -> {
            try {
              val key = withContext(vault.io) { op.finish(biometricWrapped) }
              openWith(key)
              return VaultUnlock.Opened()
            } catch (e: VaultException.Invalidated) {
              invalid = e.reason
            } catch (e: VaultException) {
              return e.toUnlock()
            }
          }
          VaultAuthResult.Fallback -> Unit
          VaultAuthResult.Cancelled -> return VaultUnlock.Cancelled
          is VaultAuthResult.Failed -> return VaultUnlock.Failed(result.message)
          is VaultAuthResult.Unavailable ->
            if (result.reason != VaultUnavailability.NoBiometrics) return VaultUnlock.Unavailable(result.reason)
        }
      }
    }
    if (invalid != null) _state.value = VaultSectionState.Closed
    return when (record.reserve) {
      VaultReserve.DeviceCredential -> unlockWithDeviceCredential(record, authenticator, prompt, invalid)
      VaultReserve.AppSecret ->
        if (record.reserveWrapped != null) VaultUnlock.NeedsSecret(invalid) else lostOrCancelled(invalid)
      VaultReserve.None -> lostOrCancelled(invalid)
    }.also { if (it is VaultUnlock.Lost) _state.value = VaultSectionState.Lost(it.reason) }
  }

  /**
   * Il dialogo, sempre sul thread principale come promette [VaultAuthenticator] (R2: chiamata da un lavoro in
   * sottofondo, `unlock` lo chiedeva su quel filo, e il `BiometricPrompt` nasceva lì).
   */
  private suspend fun ask(authenticator: VaultAuthenticator, request: VaultAuthRequest): VaultAuthResult =
    withContext(vault.main) { authenticator.authenticate(request) }

  private fun lostOrCancelled(invalid: VaultInvalidation?): VaultUnlock =
    if (invalid != null) VaultUnlock.Lost(invalid) else VaultUnlock.Cancelled

  private suspend fun unlockWithDeviceCredential(
    record: SectionRecord,
    authenticator: VaultAuthenticator,
    prompt: VaultPrompt,
    invalid: VaultInvalidation?,
  ): VaultUnlock {
    val reserveAlias = record.reserveAlias ?: return lostOrCancelled(invalid)
    val reserveWrapped = record.reserveWrapped ?: return lostOrCancelled(invalid)
    // La chiave di riserva c'è ancora? Se il blocco schermo è stato tolto, no: inutile chiedere il codice.
    if (!vault.keystore.isDeviceSecure) return VaultUnlock.Lost(VaultInvalidation.ScreenLockRemoved)
    val request = VaultAuthRequest(
      method = VaultAuthMethod.DeviceCredential,
      cipher = null,
      title = prompt.title,
      subtitle = invalid?.let { vault.labels.reason(it, VaultReserve.DeviceCredential) } ?: prompt.subtitle,
    )
    when (val result = ask(authenticator, request)) {
      VaultAuthResult.Success -> Unit
      VaultAuthResult.Fallback, VaultAuthResult.Cancelled -> return VaultUnlock.Cancelled
      is VaultAuthResult.Failed -> return VaultUnlock.Failed(result.message)
      is VaultAuthResult.Unavailable -> return VaultUnlock.Unavailable(result.reason)
    }
    return try {
      withContext(vault.io) {
        val key = vault.keystore.rsaDecrypt(reserveAlias).finish(reserveWrapped)
        val rekey = invalid != null || record.biometricAlias == null
        if (rekey) rekeyBiometric(record, key)
        openWith(key)
        VaultUnlock.Opened(recovered = invalid)
      }
    } catch (e: VaultException) {
      e.toUnlock()
    }
  }

  /** Una chiave dell'impronta nuova per [key], dopo un'impronta aggiunta (o la prima, se prima non c'erano impronte). */
  private fun rekeyBiometric(record: SectionRecord, key: ByteArray) {
    val next = record.copy(generation = record.generation + 1)
    val updated = try {
      withBiometricKey(next, key, required = false)
    } catch (e: VaultException) {
      return
    }
    writeRecord(updated)
    record.biometricAlias?.takeIf { it != updated.biometricAlias }?.let { vault.keystore.delete(it) }
  }

  /** Il motivo per cui la chiave [alias] non vale più, o null se vale. Non chiede niente: `init` di un'operazione. */
  private fun probe(alias: String): VaultInvalidation? = try {
    vault.keystore.rsaDecrypt(alias)
    null
  } catch (e: VaultException.Invalidated) {
    e.reason
  } catch (e: VaultException) {
    null
  }

  private fun openWith(key: ByteArray) {
    dataKey = key.copyOf()
    when (val a = access) {
      is VaultAccess.Device -> {
        deadline = Long.MAX_VALUE
        _state.value = VaultSectionState.Open(relocksAt = null)
      }
      is VaultAccess.Biometric -> {
        deadline = vault.clock() + a.relockAfter.inWholeMilliseconds
        _state.value = VaultSectionState.Open(relocksAt = deadline)
        armTimer()
      }
    }
  }

  private fun touch() {
    val a = access as? VaultAccess.Biometric ?: return
    if (dataKey == null) return
    deadline = vault.clock() + a.relockAfter.inWholeMilliseconds
    _state.value = VaultSectionState.Open(relocksAt = deadline)
  }

  private fun armTimer() {
    timer?.cancel()
    timer = vault.scope.launch {
      while (dataKey != null) {
        val wait = deadline - vault.clock()
        if (wait <= 0) {
          lock()
          break
        }
        delay(wait)
      }
    }
  }

  private fun VaultException.toUnlock(): VaultUnlock = when (this) {
    is VaultException.Invalidated -> VaultUnlock.Lost(reason).also { _state.value = VaultSectionState.Lost(reason) }
    is VaultException.Unavailable -> VaultUnlock.Unavailable(reason)
    is VaultException.NeedsAuthentication -> VaultUnlock.Failed(vault.labels.authenticationFailed)
    is VaultException.Locked -> VaultUnlock.Failed(vault.labels.locked)
    is VaultException.Corrupt -> VaultUnlock.Failed(vault.labels.corrupt)
  }

  // --- la scheda della sezione ------------------------------------------------------------------------------------

  private fun alias(kind: String, generation: Int): String = "fluidvault.${vault.name}.$name.$kind$generation"

  private fun sealWithKeystore(alias: String, plain: ByteArray, associated: ByteArray): ByteArray {
    val op = vault.keystore.aesEncrypt(alias)
    op.associate(associated)
    val body = op.finish(plain)
    return op.iv + body
  }

  private fun openWithKeystore(alias: String, sealed: ByteArray, associated: ByteArray): ByteArray {
    if (sealed.size <= VaultCrypto.NonceBytes) throw VaultException.Corrupt()
    val op = vault.keystore.aesDecrypt(alias, sealed.copyOfRange(0, VaultCrypto.NonceBytes))
    op.associate(associated)
    return op.finish(sealed.copyOfRange(VaultCrypto.NonceBytes, sealed.size))
  }

  /** La scheda com'è, senza confrontarla con l'accesso chiesto: per [reset], che deve poter buttare anche una sezione nata diversa. */
  private fun readRecordOrNull(): SectionRecord? = try {
    if (recordFile.exists()) SectionRecord.decode(recordFile.readBytes(), vault.name, name) else null
  } catch (e: VaultException) {
    null
  } catch (e: IOException) {
    null
  }

  /**
   * La scheda, che deve essere nata con l'accesso chiesto: lo stesso tipo, la stessa riserva e lo stesso
   * «solo a telefono sbloccato» (R2: prima riserva e `whileUnlocked` cambiati da un'app aggiornata si accettavano in
   * silenzio, con la chiave di prima, più debole). Una scheda di prima di R2, senza `whileUnlocked`, prende quello chiesto.
   */
  private fun readRecord(): SectionRecord? {
    if (!recordFile.exists()) return null
    val record = SectionRecord.decode(recordFile.readBytes(), vault.name, name)
    val expected = if (access is VaultAccess.Biometric) AccessBiometric else AccessDevice
    if (record.access != expected) throw VaultException.Corrupt("la sezione «$name» è nata con un altro accesso")
    when (val a = access) {
      is VaultAccess.Biometric -> if (record.reserve != a.reserve) {
        throw VaultException.Corrupt("la sezione «$name» è nata con un altro accesso (la riserva ${record.reserve}, non ${a.reserve})")
      }
      is VaultAccess.Device -> {
        val stored = record.whileUnlocked
        if (stored == null) {
          val updated = record.copy(whileUnlocked = a.whileUnlocked)
          writeRecord(updated)
          return updated
        }
        if (stored != a.whileUnlocked) {
          throw VaultException.Corrupt("la sezione «$name» è nata con un altro accesso (whileUnlocked = $stored, non ${a.whileUnlocked})")
        }
      }
    }
    return record
  }

  private fun writeRecord(record: SectionRecord) {
    atomicWrite(recordFile, record.encode())
  }

  internal data class SectionRecord(
    val access: Int,
    val generation: Int,
    val keyAlias: String? = null,
    val keyWrapped: ByteArray? = null,
    val biometricAlias: String? = null,
    val biometricWrapped: ByteArray? = null,
    val reserve: VaultReserve = VaultReserve.None,
    val reserveAlias: String? = null,
    val reserveWrapped: ByteArray? = null,
    val vaultName: String = "",
    val sectionName: String = "",
    /** «Solo a telefono sbloccato» (R2, in coda alla scheda); null in una scheda scritta prima, che non lo diceva. */
    val whileUnlocked: Boolean? = null,
  ) {
    fun associated(purpose: String): ByteArray = "fluid-vault/v1/$vaultName/$sectionName/$purpose".toByteArray(Charsets.UTF_8)

    // Il segno in più sta in fondo: una versione di prima lo ignora, e una scheda di prima non ce l'ha (`atEnd`).
    fun encode(): ByteArray = VaultRecordWriter()
      .int(RecordMagic).int(RecordVersion).int(access).int(generation)
      .text(keyAlias).bytes(keyWrapped)
      .text(biometricAlias).bytes(biometricWrapped)
      .int(reserve.ordinal).text(reserveAlias).bytes(reserveWrapped)
      .int(if (whileUnlocked == true) FlagWhileUnlocked else 0)
      .toByteArray()

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
      fun decode(bytes: ByteArray, vaultName: String, sectionName: String): SectionRecord {
        val r = VaultRecordReader(bytes)
        if (r.int() != RecordMagic) throw VaultException.Corrupt("non è la scheda di una sezione")
        val version = r.int()
        if (version != RecordVersion) throw VaultException.Corrupt("scheda di versione $version")
        return SectionRecord(
          access = r.int(),
          generation = r.int(),
          keyAlias = r.text(),
          keyWrapped = r.bytes(),
          biometricAlias = r.text(),
          biometricWrapped = r.bytes(),
          reserve = VaultReserve.entries.getOrNull(r.int()) ?: throw VaultException.Corrupt("riserva sconosciuta"),
          reserveAlias = r.text(),
          reserveWrapped = r.bytes(),
          vaultName = vaultName,
          sectionName = sectionName,
          whileUnlocked = if (r.atEnd()) null else (r.int() and FlagWhileUnlocked) != 0,
        )
      }
    }
  }

  private fun newRecord(access: Int, reserve: VaultReserve = VaultReserve.None): SectionRecord =
    SectionRecord(
      access = access,
      generation = 1,
      reserve = reserve,
      vaultName = vault.name,
      sectionName = name,
      whileUnlocked = (this.access as? VaultAccess.Device)?.whileUnlocked ?: false,
    )

  internal companion object {
    const val AccessDevice = 0
    const val AccessBiometric = 1
    const val RecordMagic = 0x46564B31 // "FVK1"
    const val RecordVersion = 1
    /** Il segno «solo a telefono sbloccato» nell'ultimo campo della scheda (R2). */
    const val FlagWhileUnlocked = 1
    const val KeyPurpose = "key"
    const val ReservePurpose = "reserve"
    const val SecretPurpose = "secret"
    val BoxHeader = byteArrayOf(0x46, 0x56, 0x01, 0x01) // "FV", versione 1, scatola
    val BoxInfo = "fluid-vault/v1/box".toByteArray(Charsets.UTF_8)

    /** Scrive [bytes] in [target] tutto o niente: un file accanto, sul disco (`sync`), poi lo scambio. */
    fun atomicWrite(target: File, bytes: ByteArray) {
      target.parentFile?.mkdirs()
      val temp = File(target.parentFile, ".${target.name}.tmp")
      temp.outputStream().use { out ->
        out.write(bytes)
        out.fd.sync()
      }
      moveIntoPlace(temp, target)
    }

    /** Mette [temp] (già sul disco) al posto di [target] con un solo scambio; dove `ATOMIC_MOVE` non c'è, il ripiego. */
    fun moveIntoPlace(temp: File, target: File) {
      try {
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } catch (e: IOException) {
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }
}
