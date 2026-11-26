package dev.antigravity.fluidengine.account.host

import java.io.File

// R1 e R2 · I file dello Store che non sono della custodia (i luoghi e l'aspetto): si scrivono come `CustodyKeyring`,
// `SessionStore` e `PeopleStateStore`, e un file che non si apre più si mette da parte invece di far cadere ogni
// chiamata.

/**
 * Scrive [bytes] in [target] passando da un file accanto e poi scambiandoli: un processo che muore a metà lascia il file
 * di prima, intero. Sul telefono lo scambio è uno solo (`rename` sopra il vecchio); dove non riesce (Windows, nelle
 * prove) si toglie il vecchio prima.
 */
internal fun writeReplacing(target: File, bytes: ByteArray) {
  target.parentFile?.mkdirs()
  val tmp = File(target.parentFile, target.name + ".nuovo")
  tmp.writeBytes(bytes)
  if (!tmp.renameTo(target)) {
    target.delete()
    check(tmp.renameTo(target)) { "non riesco a scrivere ${target.name}" }
  }
}

/**
 * Mette da parte [file], che non si apre più (una chiave persa, un file a metà): accanto, come `<nome>.rotto-<ora>`.
 * Non si butta, si può guardare dopo; lo cancella solo un'uscita da remoto ([removeSetAside]).
 */
internal fun setAsideBroken(file: File, now: Long = System.currentTimeMillis()): File {
  val aside = File(file.parentFile, "${file.name}.rotto-$now")
  if (!file.renameTo(aside)) {
    file.copyTo(aside, overwrite = true)
    file.delete()
  }
  return aside
}

/** Via i file messi da parte di [file] ([setAsideBroken]): per l'uscita da remoto, che cancella tutto. */
internal fun removeSetAside(file: File) {
  file.parentFile?.listFiles { f -> f.name.startsWith(file.name + ".rotto-") }?.forEach { f ->
    runCatching { f.writeBytes(ByteArray(f.length().toInt())) }
    f.delete()
  }
}
