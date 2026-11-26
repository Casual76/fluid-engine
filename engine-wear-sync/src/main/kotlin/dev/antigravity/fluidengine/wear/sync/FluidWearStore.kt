package dev.antigravity.fluidengine.wear.sync

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Dove il ponte tiene la coda, il registro e gli stati: pochi file piccoli, riscritti interi.
 *
 * Interi perché sono piccoli (qualche decina di eventi, qualche stato) e perché un file riscritto per intero e
 * sostituito in un colpo solo non resta mai a metà: dopo un riavvio a sorpresa si trova la versione di prima o quella di
 * dopo, mai un pezzo di tutte e due.
 */
interface FluidWearStore {
  /** I byte del file [name], o `null` se non c'è. */
  fun read(name: String): ByteArray?

  /** Sostituisce il file [name] con [bytes], tutto o niente. */
  fun write(name: String, bytes: ByteArray)
}

/**
 * L'archivio su disco: un file per nome in [directory], scritto in un file accanto e poi spostato sopra quello vecchio
 * con uno spostamento atomico (lo stesso schema di `AtomicFile` di Android), dopo `fsync`.
 *
 * Sull'orologio e sul telefono la cartella giusta è `noBackupFilesDir/fluid-wear-sync` ([FluidWearSync.get] la sceglie
 * da sé): fuori dal backup, perché una coda ripristinata su un altro telefono rimanderebbe eventi già registrati.
 */
class FluidWearFileStore(private val directory: File) : FluidWearStore {

  override fun read(name: String): ByteArray? {
    val file = File(directory, checked(name))
    return if (file.isFile) file.readBytes() else null
  }

  override fun write(name: String, bytes: ByteArray) {
    if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
      throw IOException("non riesco a creare ${directory.path}")
    }
    val target = File(directory, checked(name))
    val temp = File(directory, "$name.nuovo")
    FileOutputStream(temp).use { out ->
      out.write(bytes)
      out.flush()
      out.fd.sync()
    }
    try {
      Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: AtomicMoveNotSupportedException) {
      Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun checked(name: String): String {
    require(FluidWearPaths.isSafeSegment(name)) { "nome di file non valido: «$name»" }
    return name
  }
}

/** L'archivio in memoria: per le prove e per la galleria. Una copia dei byte entra e una esce. */
class FluidWearMemoryStore : FluidWearStore {
  private val files = HashMap<String, ByteArray>()

  @Synchronized
  override fun read(name: String): ByteArray? = files[name]?.copyOf()

  @Synchronized
  override fun write(name: String, bytes: ByteArray) {
    files[name] = bytes.copyOf()
  }
}
