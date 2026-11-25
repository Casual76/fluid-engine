package dev.antigravity.fluidengine.account.backup

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.account.crypto.PampaLabels
import java.nio.ByteBuffer

/** Un file del backup di un'app: un nome scelto dall'app e i suoi byte. */
class BackupFile(val name: String, val bytes: ByteArray) {
  override fun equals(other: Any?): Boolean = other is BackupFile && other.name == name && other.bytes.contentEquals(bytes)
  override fun hashCode(): Int = name.hashCode() * 31 + bytes.contentHashCode()
  override fun toString(): String = "BackupFile($name, ${bytes.size} byte)"
}

/**
 * Il pacchetto di backup di un'app (`b\chiavi.md` § 7): uguale per tutte, lo decide l'engine.
 *
 * ```
 * pacchetto = { v: 1, app: "<pacchetto>", appVersion, schema, createdAt, files: [ { name, bytes } ] }
 * ```
 *
 * [schema] è il numero di versione **dell'app** per il contenuto: chi ripristina legge per sempre anche gli schemi
 * vecchi (critica M1, migrazioni). Cosa entra lo decide ogni app con la sua lista ([PampaBackupSource]): mai
 * credenziali di altri servizi, mai foto, audio o download (documento A § 6).
 */
data class BackupPackage(
  val app: String,
  val appVersion: String,
  val schema: Int,
  val createdAt: Long,
  val files: List<BackupFile>,
) {
  val size: Long get() = files.sumOf { it.bytes.size.toLong() }

  fun file(name: String): BackupFile? = files.firstOrNull { it.name == name }
}

/**
 * La lista di un'app: cosa entra nel suo backup e come lo rimette. Esempi del documento A: PampAI le preferenze;
 * ClasseViva obiettivi, eventi personali e orario aggiunto a mano; Fluid Weather località e segnalazioni; Fluidify le
 * preferenze audio, **mai** i token Spotify né le chiavi degli AirPods.
 */
interface PampaBackupSource {
  /** La versione del contenuto: cresce quando l'app cambia il formato dei suoi file. */
  val schema: Int

  /** I file da mettere nel backup adesso. */
  suspend fun collect(): List<BackupFile>

  /**
   * Rimette un backup che l'utente ha scelto di ripristinare («Ripristinare i dati di ieri?»). L'engine non lo chiama
   * mai da solo sopra dati esistenti (`b\chiavi.md` § 7).
   */
  suspend fun restore(backup: BackupPackage)
}

/**
 * Il formato del backup sul server (`b\chiavi.md` § 7 e § 9): il pacchetto in CBOR deterministico, chiuso con
 * AES-256-GCM sotto `K_bak(app, epoca)`, legato a `accountId|app|backup|n|epoca`. Il server non lo sa aprire.
 *
 * Sul server sta un blob binario (`PUT /v1/backups/{n}`, con l'epoca anche nell'intestazione `pampa-backup-epoch`):
 *
 * ```
 * "PBK1" (4 byte) || epoca (u32) || nonce (12 byte) || testo cifrato con tag
 * ```
 *
 * Il contratto del server non fissa questa testa (scrive solo l'epoca nell'intestazione): la scrivo qui, e il rapporto
 * di F10.4 chiede di metterla in `e2e.json`.
 */
object PampaBackupFormat {
  const val VERSION = 1L
  /** 5 MB a pacchetto (`routes.json`, `limits.backup.perPackageBytes`). */
  const val MAX_BYTES = 5L * 1024 * 1024
  private val MAGIC = byteArrayOf('P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
  private const val HEADER = 4 + 4 + PampaCrypto.NONCE_BYTES

  /** Il pacchetto in CBOR deterministico: stessi dati, stessi byte. */
  fun encode(p: BackupPackage): ByteArray = PampaCbor.encode(
    mapOf(
      "v" to VERSION,
      "app" to p.app,
      "appVersion" to p.appVersion,
      "schema" to p.schema.toLong(),
      "createdAt" to p.createdAt,
      "files" to p.files.map { mapOf("name" to it.name, "bytes" to it.bytes) },
    ),
  )

  /** Legge un pacchetto; [PampaCryptoException] se non è un pacchetto di backup di versione 1. */
  fun decode(bytes: ByteArray): BackupPackage {
    val m = runCatching { PampaCbor.decode(bytes) as Map<*, *> }.getOrElse { throw PampaCryptoException("pacchetto di backup illeggibile", it) }
    if (m["v"] != VERSION) throw PampaCryptoException("pacchetto di backup di una versione che non conosco")
    val files = (m["files"] as? List<*>)?.map { f ->
      val fm = f as? Map<*, *> ?: throw PampaCryptoException("file illeggibile")
      BackupFile(fm["name"] as? String ?: throw PampaCryptoException("file senza nome"), fm["bytes"] as? ByteArray ?: throw PampaCryptoException("file senza byte"))
    } ?: throw PampaCryptoException("pacchetto senza file")
    return BackupPackage(
      app = m["app"] as? String ?: throw PampaCryptoException("pacchetto senza app"),
      appVersion = m["appVersion"] as? String ?: "",
      schema = (m["schema"] as? Long)?.toInt() ?: throw PampaCryptoException("pacchetto senza schema"),
      createdAt = m["createdAt"] as? Long ?: 0,
      files = files,
    )
  }

  /** L'impronta del contenuto (senza la data): due backup con la stessa impronta sono uguali, e il secondo non parte. */
  fun fingerprint(p: BackupPackage): ByteArray = PampaCrypto.sha256(encode(p.copy(createdAt = 0)))

  /**
   * Chiude [p] per il server: il numero [n] del backup e l'[epoch] della chiave entrano nei dati associati, così il
   * server non può scambiare due backup né rimetterne uno di un'altra app. Oltre [MAX_BYTES] è un errore.
   */
  fun seal(p: BackupPackage, key: ByteArray, epoch: Int, accountId: String, n: Long): ByteArray {
    val plain = encode(p)
    val nonce = PampaCrypto.random(PampaCrypto.NONCE_BYTES)
    val ct = PampaCrypto.aesGcmSeal(key, nonce, plain, PampaLabels.backupAad(accountId, p.app, n, epoch))
    val out = ByteBuffer.allocate(HEADER + ct.size).put(MAGIC).putInt(epoch).put(nonce).put(ct).array()
    require(out.size <= MAX_BYTES) { "il backup è di ${out.size} byte, oltre i $MAX_BYTES" }
    return out
  }

  /** L'epoca scritta nella testa di un blob, per scegliere la chiave. */
  fun epochOf(blob: ByteArray): Int {
    if (blob.size < HEADER + PampaCrypto.TAG_BYTES || !blob.copyOfRange(0, 4).contentEquals(MAGIC)) throw PampaCryptoException("non è un backup Pampa")
    return ByteBuffer.wrap(blob, 4, 4).int
  }

  /**
   * Apre un blob del server: la chiave dell'epoca scritta nella testa ([keyFor]), e gli stessi dati associati. Un
   * backup spostato (altro numero, altra app, altro account) non si apre.
   */
  fun open(blob: ByteArray, keyFor: (Int) -> ByteArray?, accountId: String, app: String, n: Long): BackupPackage {
    val epoch = epochOf(blob)
    val key = keyFor(epoch) ?: throw PampaCryptoException("manca la chiave dell'epoca $epoch")
    val nonce = blob.copyOfRange(8, HEADER)
    val plain = try {
      PampaCrypto.aesGcmOpen(key, nonce, blob.copyOfRange(HEADER, blob.size), PampaLabels.backupAad(accountId, app, n, epoch))
    } catch (e: PampaCryptoException) {
      throw PampaCryptoException("il backup $n non è al suo posto (dati associati)", e)
    }
    val p = decode(plain)
    if (p.app != app) throw PampaCryptoException("il backup è di un'altra app")
    return p
  }
}
