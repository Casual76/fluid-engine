package dev.pampa.codex.crypto

/**
 * Come due telefoni vicini capiscono di conoscersi, senza dirlo a nessun altro.
 *
 * Il trasporto per vicinanze ha un problema che il cloud non ha: **chiunque sia nel raggio sente**.
 * Un annuncio Bluetooth lo raccoglie il bar intero, e se dentro ci fosse un nome o un Codex ID,
 * Codex diventerebbe un modo per sapere chi c'e' in una stanza -- che e' esattamente il contrario di
 * quello che deve essere.
 *
 * Quindi l'annuncio non dice chi si e'. Dice **un numero che cambia ogni giorno**, calcolato dal
 * seme del portachiavi: chi non ha quel seme non lo sa ricostruire, e nemmeno collegare quello di
 * oggi a quello di ieri. Serve solo a distinguere due dispositivi che si annunciano insieme, e a
 * dare a chi si connette qualcosa da confrontare dopo.
 *
 * **Chi si e' davvero si dice dopo, e si dimostra.** Appena il canale e' aperto, i due si mandano
 * una sfida a testa e la firmano con la chiave Ed25519 della loro identita'. Chi non ha quella
 * chiave non passa; chi passa e' esattamente la persona la cui scheda si ha in rubrica. Le due sfide
 * sono diverse e ognuna include il proprio identificatore, quindi non si puo' rigirare la sfida di
 * qualcun altro e farsi passare per lui.
 */
object NearbyHandshake {

  private val MAGIC = "CDXN".toByteArray(Charsets.US_ASCII)
  private const val VERSION = 1

  /** Quanto e' lungo il nome effimero che si annuncia. Corto: ci sta nell'annuncio di Nearby. */
  const val NAME_SIZE = 8

  /** Quanto e' lunga una sfida. Sedici byte a caso non si indovinano e non si ripetono. */
  const val CHALLENGE_SIZE = 16

  class Refused(message: String) : Exception(message)

  /**
   * Il nome con cui questo telefono si annuncia oggi.
   *
   * Cambia a mezzanotte e non dice niente di chi lo porta. Non e' un segreto -- lo sente chiunque --
   * ma e' inutile a chiunque: senza il seme non si sa da chi venga, e domani sara' un altro.
   */
  fun advertisedName(keyringSeed: ByteArray, dayEpoch: Long): String =
    Base32.encode(
      Hkdf.derive(keyringSeed, "codex-nearby-name-v1|$dayEpoch", NAME_SIZE),
    ).lowercase()

  /** Il giorno a cui appartiene un istante, in UTC. Due telefoni nella stessa stanza concordano. */
  fun dayOf(nowMillis: Long): Long = nowMillis / 86_400_000L

  /** Una sfida nuova: si manda, e si aspetta indietro firmata. */
  fun newChallenge(): ByteArray = Digests.randomBytes(CHALLENGE_SIZE)

  /**
   * La risposta a una sfida: la firma su **la sfida piu' chi sono**.
   *
   * Il proprio Codex ID sta dentro cio' che si firma, e non e' un dettaglio: senza, una firma
   * ottenuta in una conversazione varrebbe in tutte le altre, e basterebbe rigirare la sfida a un
   * terzo per farsi dare la risposta giusta da lui.
   */
  fun answer(challenge: ByteArray, myCodexId: String, signingPrivateKey: ByteArray): ByteArray {
    require(challenge.size == CHALLENGE_SIZE) { "sfida di misura sbagliata" }
    return IdentityKeys.sign(signingPrivateKey, transcript(challenge, myCodexId))
  }

  /**
   * Controlla la risposta di chi dice di essere [peerCodexId].
   *
   * `false` e basta: da fuori "la firma non torna" e "non e' chi dice" sono la stessa cosa, e in
   * tutti e due i casi la connessione si chiude.
   */
  fun verify(
    challenge: ByteArray,
    peerCodexId: String,
    peerEd25519Public: ByteArray,
    signature: ByteArray,
  ): Boolean = runCatching {
    // Il Codex ID nasce dalla chiave di firma: se non combaciano, quella scheda non e' di quella
    // persona, e non serve nemmeno guardare la firma.
    CodexId.fromSigningKey(peerEd25519Public) == peerCodexId &&
      IdentityKeys.verify(peerEd25519Public, transcript(challenge, peerCodexId), signature)
  }.getOrDefault(false)

  /**
   * Il saluto che apre il canale: chi sono e cosa ti chiedo di firmare.
   *
   * Il Codex ID viaggia **qui**, non nell'annuncio: a questo punto il canale e' gia' aperto fra due
   * dispositivi soli, invece che gridato a tutta la stanza. Chi lo riceve non deve credergli --
   * gli serve solo per cercare la scheda in rubrica e sapere quale chiave usare per il controllo.
   */
  fun hello(myCodexId: String, challenge: ByteArray): ByteArray = ByteWriter(64)
    .bytes(MAGIC)
    .u8(VERSION)
    .string(myCodexId)
    .blob(challenge)
    .build()

  class Hello(val codexId: String, val challenge: ByteArray)

  fun readHello(bytes: ByteArray): Hello {
    val reader = ByteReader(bytes)
    try {
      reader.expectMagic(MAGIC)
    } catch (error: ByteReader.Malformed) {
      throw Refused("non e' un saluto di Codex")
    }
    if (reader.u8() != VERSION) throw Refused("saluto di un'altra versione")
    val codexId = reader.string()
    val challenge = reader.blob()
    if (challenge.size != CHALLENGE_SIZE) throw Refused("sfida di misura sbagliata")
    return Hello(codexId, challenge)
  }

  private fun transcript(challenge: ByteArray, codexId: String): ByteArray =
    ByteWriter(64)
      .bytes(MAGIC)
      .u8(VERSION)
      .string("codex-nearby-auth-v1")
      .string(codexId)
      .blob(challenge)
      .build()
}
