package dev.antigravity.fluidengine.vault

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Un **DataStore intero cifrato** in una sezione «solo telefono»: lo stesso `DataStore<T>` di Jetpack, con lo stesso
 * `Serializer<T>` dell'app, ma il file sul disco è cifrato tutto con la chiave della sezione (AES-256-GCM, una chiave
 * derivata per file). Il file sta fuori dal backup, nella cartella della sezione.
 *
 * Uno per file e per processo, come vuole DataStore: chiamarla due volte con lo stesso nome dà lo stesso.
 *
 * **Solo per [VaultAccess.Device].** Un DataStore tiene in memoria i dati letti finché vive, e una sezione all'impronta
 * deve dimenticarli quando si richiude: lì servono [VaultSection.values] o [VaultFile].
 *
 * Un file che non si decifra (toccato, o di un'altra chiave) diventa `CorruptionException`: con [corruptionHandler] si
 * riparte dal valore che dà lui.
 */
fun <T> VaultSection.dataStore(
  fileName: String,
  serializer: Serializer<T>,
  corruptionHandler: ReplaceFileCorruptionHandler<T>? = null,
  migrations: List<DataMigration<T>> = emptyList(),
  scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<T> {
  requireDeviceSection()
  val target = dataStoreFile(fileName)
  return sharedDataStore(target) {
    DataStoreFactory.create(
      serializer = EncryptedSerializer(this, serializer, fileName),
      corruptionHandler = corruptionHandler,
      migrations = migrations,
      scope = scope,
      produceFile = { target },
    )
  }
}

/**
 * Le **Preferences** di DataStore, cifrate in una sezione «solo telefono»: le stesse chiavi (`stringPreferencesKey`…)
 * e lo stesso `edit { }`, con il file cifrato. Vedi [dataStore].
 */
fun VaultSection.preferencesDataStore(
  fileName: String,
  corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
  migrations: List<DataMigration<Preferences>> = emptyList(),
  scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> {
  requireDeviceSection()
  val target = dataStoreFile("$fileName.preferences_pb")
  return sharedDataStore(target) {
    PreferenceDataStoreFactory.create(
      storage = OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = EncryptedOkioSerializer(this, PreferencesSerializer, "$fileName.preferences_pb"),
        producePath = { target.toOkioPath() },
      ),
      corruptionHandler = corruptionHandler,
      migrations = migrations,
      scope = scope,
    )
  }
}

private fun VaultSection.requireDeviceSection() {
  require(access is VaultAccess.Device) {
    "un DataStore tiene i dati in memoria: in una sezione all'impronta usa values o file (sezione «$name»)"
  }
}

private fun VaultSection.dataStoreFile(fileName: String): File {
  require(fileName.isNotEmpty() && fileName.all { it.isLetterOrDigit() || it in "._-" } && !fileName.startsWith(".")) {
    "nome di file non valido: «$fileName»"
  }
  return File(File(dir, "datastore"), "$fileName.fv")
}

@Suppress("UNCHECKED_CAST")
private fun <T> VaultSection.sharedDataStore(target: File, create: () -> DataStore<T>): DataStore<T> {
  val registry = vaultDataStores()
  synchronized(registry) {
    return registry.getOrPut(target.absolutePath) { create() as Any } as DataStore<T>
  }
}

/** Cifra e decifra tutto il contenuto: il `Serializer` dell'app vede solo il chiaro. */
private class EncryptedSerializer<T>(
  private val section: VaultSection,
  private val inner: Serializer<T>,
  fileName: String,
) : Serializer<T> {
  private val codec = DataStoreCodec(section, fileName)
  override val defaultValue: T get() = inner.defaultValue

  override suspend fun readFrom(input: InputStream): T {
    val plain = codec.decrypt(input.readBytes())
    return try {
      inner.readFrom(plain.inputStream())
    } finally {
      plain.fill(0)
    }
  }

  override suspend fun writeTo(t: T, output: OutputStream) {
    val buffer = java.io.ByteArrayOutputStream()
    inner.writeTo(t, buffer)
    val plain = buffer.toByteArray()
    try {
      output.write(codec.encrypt(plain))
    } finally {
      plain.fill(0)
    }
  }
}

/** Come [EncryptedSerializer], per i serializzatori Okio (quello delle Preferences). */
private class EncryptedOkioSerializer<T>(
  private val section: VaultSection,
  private val inner: OkioSerializer<T>,
  fileName: String,
) : OkioSerializer<T> {
  private val codec = DataStoreCodec(section, fileName)
  override val defaultValue: T get() = inner.defaultValue

  override suspend fun readFrom(source: BufferedSource): T {
    val plain = codec.decrypt(source.readByteArray())
    return try {
      inner.readFrom(Buffer().write(plain))
    } finally {
      plain.fill(0)
    }
  }

  override suspend fun writeTo(t: T, sink: BufferedSink) {
    val buffer = Buffer()
    inner.writeTo(t, buffer)
    val plain = buffer.readByteArray()
    try {
      sink.write(codec.encrypt(plain))
    } finally {
      plain.fill(0)
    }
  }
}

private class DataStoreCodec(private val section: VaultSection, fileName: String) {
  private val info = "fluid-vault/v1/datastore/$fileName".toByteArray(Charsets.UTF_8)
  private val associated = "fluid-vault/v1/${section.vaultName}/${section.name}/datastore/$fileName".toByteArray(Charsets.UTF_8)

  suspend fun encrypt(plain: ByteArray): ByteArray = section.withKey { key ->
    val fileKey = VaultCrypto.hkdf(key, ByteArray(0), info)
    try {
      Header + VaultCrypto.seal(fileKey, plain, Header + associated)
    } finally {
      fileKey.fill(0)
    }
  }

  suspend fun decrypt(stored: ByteArray): ByteArray {
    if (stored.size < Header.size || !stored.copyOfRange(0, Header.size).contentEquals(Header)) {
      throw CorruptionException("non è un DataStore della cassaforte")
    }
    return try {
      section.withKey { key ->
        val fileKey = VaultCrypto.hkdf(key, ByteArray(0), info)
        try {
          VaultCrypto.open(fileKey, stored.copyOfRange(Header.size, stored.size), Header + associated)
        } finally {
          fileKey.fill(0)
        }
      }
    } catch (e: VaultException.Corrupt) {
      throw CorruptionException("DataStore cifrato non valido", e)
    } catch (e: VaultException.Invalidated) {
      throw CorruptionException("la chiave del DataStore non vale più: ${e.reason}", e)
    }
  }

  private companion object {
    val Header = byteArrayOf(0x46, 0x56, 0x01, 0x02) // "FV", versione 1, DataStore
  }
}
