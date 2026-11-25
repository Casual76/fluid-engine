package dev.antigravity.fluidengine.account.host.custody

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaLabels

/**
 * Le formule della custodia, una per riga, come le fissa il contratto del server (`Pampa-account\contract\v1\e2e.json`,
 * `inputs`) e come le usa il suo telefono finto (`test\fake\phone.ts`). Tutto il resto della custodia è costruito
 * sopra queste; le prove le confrontano coi valori usciti dal codice del server.
 *
 * ```
 * seme porta  = HKDF(segreto, sale della porta, "pampa/v1/door")                     → X25519
 * sigillo     = HPKE base verso la pubblica della porta, info "pampa/v1/door-seal|<doorId>|<epoca>", aad vuoto
 * tag (K8)    = HMAC(HKDF(CD_r, "pampa/v1/doors"), utf8("<doorId>|<tipo>|<pub>|<sale>|<r>"))   pub e sale in base64url
 * check (K9)  = HMAC(HKDF(segreto, "pampa/v1/door-check"), SHA-256(CD_radice))
 * link (K9)   = HMAC(HKDF(CD_{e-1}, "pampa/v1/epoch"), utf8("<e>|<tipo>|") || SHA-256(CD_e))
 * K_app       = HKDF(CD_e, sale = utf8(accountId), "pampa/v1/app/<pacchetto>"); K_bak e K_com uguali
 * auth        = HKDF(codice, sale vuoto, "pampa/v1/recovery-auth")                 → l'unica cosa che va al server
 * passaggio   = HPKE base verso N, info "pampa/v1/transfer|<transferId>";
 * mac         = HMAC(HKDF(s, "pampa/v1/transfer-mac"), utf8(transferId) || N || sigillo)
 * ```
 */
object CustodyMath {

  /** Il seme della porta: resta sempre sul telefono (K6). */
  fun doorSeed(secret: ByteArray, salt: ByteArray): ByteArray = PampaCrypto.hkdf(secret, salt, PampaLabels.DOOR_SEED)

  /** La coppia X25519 di una porta, dal suo segreto (codice o uscita del PRF) e dal suo sale. */
  fun doorKeys(secret: ByteArray, salt: ByteArray): X25519KeyPair = X25519KeyPair.fromSeed(doorSeed(secret, salt))

  fun sealInfo(doorId: String, epoch: Int): ByteArray =
    PampaLabels.fill(PampaLabels.DOOR_SEAL, "doorId" to doorId, "epoch" to epoch).toByteArray(Charsets.UTF_8)

  /** Sigilla la chiave dei dati [cd] di un'epoca per la porta con la pubblica [doorPub]. */
  fun sealForDoor(doorPub: ByteArray, doorId: String, epoch: Int, cd: ByteArray): ByteArray =
    PampaHpke.seal(doorPub, sealInfo(doorId, epoch), PampaCrypto.EMPTY, cd)

  fun openDoorSeal(door: X25519KeyPair, doorId: String, epoch: Int, sealed: ByteArray): ByteArray =
    PampaHpke.open(door, sealInfo(doorId, epoch), PampaCrypto.EMPTY, sealed)

  /** Il sigillo d'autenticità di una porta (K8), fatto con la chiave dei dati dell'epoca di riferimento [r]. */
  fun tag(cdR: ByteArray, doorId: String, kind: String, pub: String, salt: String, r: Int): ByteArray =
    PampaCrypto.hmacSha256(PampaCrypto.hkdf(cdR, info = PampaLabels.DOOR_TAG), "$doorId|$kind|$pub|$salt|$r".toByteArray(Charsets.UTF_8))

  /** Il controllo della prima epoca (K9): solo chi conosce il segreto della porta lo sa fare. */
  fun check(secret: ByteArray, cdRoot: ByteArray): ByteArray =
    PampaCrypto.hmacSha256(PampaCrypto.hkdf(secret, info = PampaLabels.DOOR_CHECK), PampaCrypto.sha256(cdRoot))

  /** Il collegamento dell'epoca [epoch] alla precedente (K9). Il tipo entra nella catena (F10.3, S11). */
  fun link(cdPrev: ByteArray, epoch: Int, kind: String, cd: ByteArray): ByteArray =
    PampaCrypto.hmacSha256(PampaCrypto.hkdf(cdPrev, info = PampaLabels.EPOCH_LINK), "$epoch|$kind|".toByteArray(Charsets.UTF_8) + PampaCrypto.sha256(cd))

  fun appKey(cd: ByteArray, accountId: String, packageName: String): ByteArray =
    PampaCrypto.hkdf(cd, accountId.toByteArray(Charsets.UTF_8), PampaLabels.fill(PampaLabels.APP_KEY, "package" to packageName))

  fun backupKey(cd: ByteArray, accountId: String, packageName: String): ByteArray =
    PampaCrypto.hkdf(cd, accountId.toByteArray(Charsets.UTF_8), PampaLabels.fill(PampaLabels.BACKUP_KEY, "package" to packageName))

  fun sharedKey(cd: ByteArray, accountId: String, name: String): ByteArray =
    PampaCrypto.hkdf(cd, accountId.toByteArray(Charsets.UTF_8), PampaLabels.fill(PampaLabels.SHARED_KEY, "name" to name))

  /** `auth` del codice di recupero: l'unico valore del codice che va al server (critica S1). */
  fun recoveryAuth(code: ByteArray): ByteArray = PampaCrypto.hkdf(code, info = PampaLabels.RECOVERY_AUTH)

  fun transferInfo(transferId: String): ByteArray =
    PampaLabels.fill(PampaLabels.TRANSFER_SEAL, "transferId" to transferId).toByteArray(Charsets.UTF_8)

  fun transferMac(s: ByteArray, transferId: String, n: ByteArray, sealed: ByteArray): ByteArray =
    PampaCrypto.hmacSha256(PampaCrypto.hkdf(s, info = PampaLabels.TRANSFER_MAC), transferId.toByteArray(Charsets.UTF_8) + n + sealed)

  internal fun b64(bytes: ByteArray): String = PampaBase64.encode(bytes)
}
