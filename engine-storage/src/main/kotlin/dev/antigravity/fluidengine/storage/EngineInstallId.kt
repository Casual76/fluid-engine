package dev.antigravity.fluidengine.storage

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * L'identita' di questa installazione dell'app (F8.21): sedici caratteri casuali, nati al primo uso e mai cambiati finche'
 * l'app resta installata. La sincronizzazione (`engine-sync`) la usa come nome del dispositivo nel suo orologio logico.
 *
 * Sta in un file in `noBackupFilesDir`, **fuori dal backup automatico**: se il backup la copiasse su un telefono nuovo,
 * due dispositivi avrebbero lo stesso nome e i loro orologi si confonderebbero. Non e' un identificativo della persona e non
 * lascia mai il telefono in chiaro (sta dentro i documenti sigillati).
 */
object EngineInstallId {
  private const val FileName = "fluid_engine_install_id"
  private const val Alphabet = "abcdefghijklmnopqrstuvwxyz234567"
  private const val Length = 16

  /** L'identita' di questa installazione. Legge un file piccolo: meglio chiamarla fuori dal thread principale. */
  fun get(context: Context): String = readOrCreate(File(context.applicationContext.noBackupFilesDir, FileName))

  /**
   * L'identita' scritta in [file], creata se manca o se il contenuto non e' valido. La scrittura passa da un file
   * temporaneo rinominato, cosi' un processo che muore a meta' non lascia un'identita' troncata.
   */
  @Synchronized
  fun readOrCreate(file: File): String {
    val existing = runCatching { file.readText(Charsets.US_ASCII).trim() }.getOrNull()
    if (existing != null && isValid(existing)) return existing
    val random = SecureRandom()
    val id = String(CharArray(Length) { Alphabet[random.nextInt(Alphabet.length)] })
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeText(id, Charsets.US_ASCII)
    if (!temp.renameTo(file)) {
      file.delete()
      check(temp.renameTo(file)) { "non riesco a salvare l'identita' dell'installazione" }
    }
    return id
  }

  /** Vero se [id] e' un'identita' come le scrive [readOrCreate]. */
  fun isValid(id: String): Boolean = id.length == Length && id.all { it in Alphabet }
}
