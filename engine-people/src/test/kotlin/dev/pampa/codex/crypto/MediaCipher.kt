package dev.pampa.codex.crypto

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Foto e note vocali, cifrate **a blocchi**.
 *
 * La busta di un messaggio (`CDX3`) cifra tutto in un colpo, e va benissimo per del testo. Una foto
 * da dodici megapixel sono venti megabyte: caricarli interi in memoria per cifrarli e' il modo piu'
 * rapido di far chiudere l'app dal sistema su un telefono che ha altro da fare. Qui si legge un
 * pezzo per volta, si cifra, si scrive, e la memoria occupata resta quella di **un blocco**.
 *
 * **Cosa protegge, oltre al contenuto.** Un file cifrato a blocchi indipendenti si potrebbe
 * rimescolare: togliere un pezzo, ripeterne uno, metterli in un altro ordine. Ogni blocco porta
 * quindi nei dati autenticati il proprio **numero** e l'intera **intestazione**, e l'intestazione
 * dice quanto e' lungo l'originale. Il risultato e' che un file rimescolato, troncato o allungato
 * non si apre: non produce un'immagine strana, produce un errore.
 *
 * Il formato:
 *
 * ```
 * CDXM | versione(1) | dimensione blocco(8) | lunghezza originale(8) | nonce di base(12)
 * poi, per ogni blocco: testo cifrato + tag(16)
 * ```
 *
 * Il nonce di ogni blocco nasce dal nonce di base e dal numero del blocco, quindi non si ripete mai
 * con la stessa chiave -- che con GCM non e' una raccomandazione, e' la condizione perche' funzioni.
 */
object MediaCipher {

  private val MAGIC = "CDXM".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1

  /** Sessantaquattro kibibyte: un compromesso fra chiamate di sistema e memoria occupata. */
  const val CHUNK = 64 * 1024

  private const val HEADER_SIZE = 4 + 1 + 8 + 8 + Aead.NONCE_SIZE

  class Malformed(message: String) : Exception(message)

  /**
   * La chiave di un media.
   *
   * Nasce dalla chiave della conversazione e dall'identificatore del file: due foto della stessa
   * chat non condividono mai una chiave, quindi chi riuscisse a rompere l'una non avrebbe fatto un
   * passo verso l'altra.
   */
  fun key(chatKey: ByteArray, mediaId: String): ByteArray =
    Hkdf.derive(chatKey, "codex-media-v1:$mediaId", Aead.KEY_SIZE)

  /** Quanto occupera' un file una volta cifrato: serve a dire "non ci sta" prima di cominciare. */
  fun encryptedSize(plainLength: Long): Long {
    val chunks = (plainLength + CHUNK - 1) / CHUNK
    return HEADER_SIZE + plainLength + chunks * Aead.TAG_SIZE
  }

  fun encrypt(key: ByteArray, input: InputStream, output: OutputStream, plainLength: Long) {
    require(plainLength >= 0) { "lunghezza negativa" }
    val nonceBase = Digests.randomBytes(Aead.NONCE_SIZE)
    val header = ByteWriter(HEADER_SIZE)
      .bytes(MAGIC)
      .u8(VERSION)
      .i64(CHUNK.toLong())
      .i64(plainLength)
      .bytes(nonceBase)
      .build()
    output.write(header)

    val buffer = ByteArray(CHUNK)
    var index = 0L
    var written = 0L
    while (written < plainLength) {
      val wanted = minOf(CHUNK.toLong(), plainLength - written).toInt()
      val read = input.readFully(buffer, wanted)
      if (read < wanted) throw Malformed("il file e' finito prima di quanto dichiarato")
      output.write(
        Aead.encrypt(
          key = key,
          nonce = nonceFor(nonceBase, index),
          plaintext = buffer.copyOf(read),
          associatedData = associated(header, index),
        ),
      )
      written += read
      index++
    }
    output.flush()
  }

  fun decrypt(key: ByteArray, input: InputStream, output: OutputStream) {
    val header = ByteArray(HEADER_SIZE)
    if (input.readFully(header, HEADER_SIZE) < HEADER_SIZE) throw Malformed("intestazione assente")

    val reader = ByteReader(header)
    try {
      reader.expectMagic(MAGIC)
      if (reader.u8() != VERSION) throw Malformed("versione sconosciuta")
    } catch (error: ByteReader.Malformed) {
      throw Malformed("non e' un media di Codex")
    }
    val chunk = reader.i64().toInt()
    val plainLength = reader.i64()
    if (chunk !in 1..MAX_CHUNK || plainLength < 0) throw Malformed("intestazione fuori scala")
    val nonceBase = reader.bytes(Aead.NONCE_SIZE)

    val buffer = ByteArray(chunk + Aead.TAG_SIZE)
    var index = 0L
    var produced = 0L
    while (produced < plainLength) {
      val expected = minOf(chunk.toLong(), plainLength - produced).toInt() + Aead.TAG_SIZE
      val read = input.readFully(buffer, expected)
      if (read < expected) throw Malformed("il file e' stato troncato")
      val plain = try {
        Aead.decrypt(
          key = key,
          nonce = nonceFor(nonceBase, index),
          ciphertext = buffer.copyOf(read),
          associatedData = associated(header, index),
        )
      } catch (error: Aead.DecryptionFailed) {
        // Non si distingue "chiave sbagliata" da "file manomesso", ed e' giusto: sopra questo
        // livello la differenza non cambia cosa si puo' fare, cioe' niente.
        throw Malformed("il media non si apre")
      }
      output.write(plain)
      produced += plain.size
      index++
    }
    output.flush()
  }

  /**
   * I dati autenticati di un blocco: **tutta** l'intestazione, piu' il numero del blocco.
   *
   * L'intestazione ci sta dentro per intero perche' non e' cifrata: senza, si potrebbe cambiare la
   * lunghezza dichiarata e far accettare un file tagliato. Il numero del blocco impedisce di
   * rimescolarli, ripeterne uno o toglierne uno di mezzo.
   */
  private fun associated(header: ByteArray, index: Long): ByteArray =
    ByteWriter(header.size + 8).bytes(header).i64(index).build()

  /**
   * Il nonce di un blocco.
   *
   * Gli ultimi otto byte del nonce di base sono messi in XOR con il numero del blocco: dentro un
   * file i nonce sono tutti diversi per costruzione, e fra file diversi lo sono perche' la base e'
   * casuale e la chiave e' diversa.
   */
  private fun nonceFor(base: ByteArray, index: Long): ByteArray {
    val nonce = base.copyOf()
    for (byte in 0 until 8) {
      nonce[nonce.size - 1 - byte] =
        (nonce[nonce.size - 1 - byte].toLong() xor (index ushr (byte * 8))).toByte()
    }
    return nonce
  }

  /** Un blocco piu' grande di questo non e' un blocco: e' un'intestazione che mente. */
  private const val MAX_CHUNK = 4 * 1024 * 1024

  /** `InputStream.read` puo' restituire meno del richiesto senza che il file sia finito. */
  private fun InputStream.readFully(into: ByteArray, count: Int): Int {
    var done = 0
    while (done < count) {
      val read = read(into, done, count - done)
      if (read < 0) return done
      done += read
    }
    return done
  }
}
