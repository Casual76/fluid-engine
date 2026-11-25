package dev.antigravity.fluidengine.account.host.custody

/**
 * Le due classi di chiavi sul telefono (`b\chiavi.md` § 3.3, K10; critica S3).
 *
 * - [DELIVERY], «consegna»: si usa dopo il primo sblocco dall'accensione, anche a telefono bloccato. Serve al
 *   sottofondo: il rinnovo, le buste delle persone, il backup notturno, la sincronizzazione.
 * - [MOTHER], «madre»: si usa solo a telefono sbloccato (`setUnlockedDeviceRequired`), per le operazioni rare: porta
 *   nuova, passaggio, rotazione, «Ricomincia», esportazione, il calcolo delle chiavi di lavoro.
 *
 * Prima del primo sblocco nessuna delle due si apre: i file stanno nella memoria cifrata con le credenziali di Android
 * (Direct Boot).
 */
enum class KeyClass { DELIVERY, MOTHER }

/** Cosa custodisce lo Store, per tipo: ogni tipo ha la sua classe secondo [KeyClassPolicy]. */
enum class CustodyKeyKind {
  /** La chiave P-256 del dispositivo, che firma la prova del rinnovo (`b\api.md` § 3.3). */
  DEVICE_KEY,
  /** Il file della sessione, col token di rinnovo. */
  SESSION,
  /** La chiave dei dati, tutte le epoche. */
  DATA_KEY,
  /** La radice Ed25519 della persona (F10.6). */
  PERSON_ROOT,
  /** Le chiavi di lavoro: `K_app` e `K_bak` delle app, `K_com(places)`, `K_com(rubrica)`, quelle della persona. */
  WORK_KEYS,
}

/**
 * **Domanda Q7 di Alessio, in attesa** (`progetto.md` § 8.2). Qui c'è la raccomandazione A, ed è l'unico posto da
 * cambiare: [CHOICE].
 *
 * | Scelta | Consegna (anche a telefono bloccato) | Madre (solo sbloccato) | Cosa succede in tasca |
 * |---|---|---|---|
 * | **A** (oggi) | chiave del dispositivo, sessione, chiavi di lavoro | chiave dei dati, radice della persona | rinnovo, buste aperte e backup notturno funzionano |
 * | B | chiave del dispositivo, sessione | tutto il resto | rinnovo sì; le buste restano chiuse fino allo sblocco («Hai ricevuto qualcosa da un contatto»), backup solo da sbloccato |
 * | C | niente | tutto | niente rinnovo, niente notifiche complete, backup solo a schermo acceso |
 */
object KeyClassPolicy {
  enum class Choice { A, B, C }

  /**
   * La risposta a Q7. Di serie la raccomandazione A. Chi la usa la riceve come parametro (`AccountHostConfig`,
   * `CustodyKeyring`), così le prove provano tutte e tre le risposte senza toccare questa costante.
   */
  val CHOICE: Choice = Choice.A

  /** La classe di [kind] con la scelta [choice] (di serie quella di oggi). */
  fun classOf(kind: CustodyKeyKind, choice: Choice = CHOICE): KeyClass = when (choice) {
    Choice.A -> when (kind) {
      CustodyKeyKind.DEVICE_KEY, CustodyKeyKind.SESSION, CustodyKeyKind.WORK_KEYS -> KeyClass.DELIVERY
      CustodyKeyKind.DATA_KEY, CustodyKeyKind.PERSON_ROOT -> KeyClass.MOTHER
    }
    Choice.B -> when (kind) {
      CustodyKeyKind.DEVICE_KEY, CustodyKeyKind.SESSION -> KeyClass.DELIVERY
      else -> KeyClass.MOTHER
    }
    Choice.C -> KeyClass.MOTHER
  }

  /** Il lavoro in sottofondo può usare le chiavi di lavoro col telefono bloccato? Sì solo con la A. */
  fun workKeysInBackground(choice: Choice = CHOICE): Boolean = classOf(CustodyKeyKind.WORK_KEYS, choice) == KeyClass.DELIVERY
}

/**
 * **Domanda Q11 di Alessio, in attesa**: quando si crea il codice di recupero. Qui c'è la raccomandazione A
 * ([AT_FIRST_ENCRYPTED]): alla prima cosa cifrata (il primo backup, la prima persona aggiunta), con «Più tardi»; fino
 * ad allora non parte niente di cifrato. Con la B ([AT_CREATION]) lo Store lo chiede già alla creazione dell'account.
 * La regola delle due porte (K2) resta uguale con tutte e due.
 */
object RecoveryCodePolicy {
  enum class When { AT_FIRST_ENCRYPTED, AT_CREATION }

  /** La risposta a Q11. Di serie la raccomandazione A. */
  val WHEN: When = When.AT_FIRST_ENCRYPTED
}
