package dev.antigravity.fluidengine.account.host.server

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyKind
import dev.antigravity.fluidengine.account.host.custody.KeyClass
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * La chiave del dispositivo (`b\architettura.md` § 3.2; `b\api.md` § 3.3): EC P-256, solo per firmare la prova del
 * rinnovo, non esportabile. Senza di lei il token di rinnovo, anche rubato da una copia, non rinnova niente.
 * Con la raccomandazione A di Q7 è in classe «consegna»: il rinnovo funziona anche col telefono in tasca.
 */
interface DeviceKey {
  /** La pubblica come JWK (`{ kty: "EC", crv: "P-256", x, y }`), da mandare all'accesso nel campo `device.deviceKey`. */
  val publicJwk: JsonObject

  /** Firma [data] con ES256; la firma è `r || s`, 64 byte, come vuole JWS (RFC 7518 § 3.4). */
  fun sign(data: ByteArray): ByteArray

  /** Cancella la chiave (uscita). */
  fun destroy()

  companion object {
    /** La JWK di una pubblica P-256. */
    fun jwkOf(key: ECPublicKey): JsonObject = buildJsonObject {
      put("kty", "EC")
      put("crv", "P-256")
      put("x", PampaBase64.encode(unsigned(key.w.affineX, 32)))
      put("y", PampaBase64.encode(unsigned(key.w.affineY, 32)))
    }

    /** Da una firma DER di `SHA256withECDSA` a `r || s` di 64 byte. */
    fun derToJose(der: ByteArray): ByteArray {
      require(der.size >= 8 && der[0] == 0x30.toByte()) { "firma DER non valida" }
      var i = if (der[1].toInt() and 0x80 != 0) 2 + (der[1].toInt() and 0x7f) else 2
      fun readInt(): BigInteger {
        require(der[i] == 0x02.toByte()) { "firma DER non valida" }
        val len = der[i + 1].toInt() and 0xff
        val v = BigInteger(1, der.copyOfRange(i + 2, i + 2 + len))
        i += 2 + len
        return v
      }
      val r = readInt()
      val s = readInt()
      return unsigned(r, 32) + unsigned(s, 32)
    }

    private fun unsigned(v: BigInteger, size: Int): ByteArray {
      val raw = v.toByteArray()
      return when {
        raw.size == size -> raw
        raw.size > size -> raw.copyOfRange(raw.size - size, raw.size)
        else -> ByteArray(size - raw.size) + raw
      }
    }
  }
}

/**
 * La chiave del dispositivo nel Keystore di Android: P-256, `SHA256withECDSA`, StrongBox se c'è e se chiesto, e il
 * vincolo «solo a telefono sbloccato» se la classe di [CustodyKeyKind.DEVICE_KEY] è madre (Q7 C) e da Android 15.
 * Nessuna richiesta d'impronta: il rinnovo gira anche in sottofondo (architettura § 3.2).
 */
class AndroidKeystoreDeviceKey(
  private val context: Context,
  private val alias: String = "dev.pampa.account.device",
  private val strongBox: Boolean = false,
  private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE,
  private val attestationChallenge: ByteArray? = null,
) : DeviceKey {
  private val keyStore: KeyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

  /** La chiave è finita davvero in StrongBox? Per la prova sul tablet. */
  var strongBoxBacked: Boolean = false
    private set

  /**
   * Cerca la chiave, o la crea. R2 · Sotto un lucchetto, come `KeystoreCustodyVault.seal`: due fili insieme (la
   * pagina d'accesso che chiede [publicJwk] e un rinnovo che chiede [sign]) farebbero nascere due chiavi con lo stesso
   * alias, e il Keystore terrebbe la seconda mentre il server registra la prima. E dopo averla creata la si **rilegge**
   * dal Keystore: si usa sempre quella che c'è davvero, mai quella appena tenuta in mano.
   */
  private fun keyPair(): Pair<PrivateKey, ECPublicKey> = synchronized(lock) {
    stored()?.let { return it }
    generate(strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
    stored() ?: throw IllegalStateException("la chiave del dispositivo non si rilegge dal Keystore")
  }

  private fun stored(): Pair<PrivateKey, ECPublicKey>? {
    val entry = runCatching { keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry }.getOrNull() ?: return null
    return entry.privateKey to (entry.certificate.publicKey as ECPublicKey)
  }

  private val lock = Any()

  private fun generate(useStrongBox: Boolean): KeyPair {
    val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
      .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
      .setDigests(KeyProperties.DIGEST_SHA256)
    if (KeyClassPolicy.classOf(CustodyKeyKind.DEVICE_KEY, choice) == KeyClass.MOTHER && Build.VERSION.SDK_INT >= 35) builder.setUnlockedDeviceRequired(true)
    attestationChallenge?.let(builder::setAttestationChallenge)
    if (useStrongBox) {
      try {
        builder.setIsStrongBoxBacked(true)
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
          initialize(builder.build())
          generateKeyPair()
        }.also { strongBoxBacked = true }
      } catch (_: StrongBoxUnavailableException) {
        builder.setIsStrongBoxBacked(false)
      }
    }
    return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
      initialize(builder.build())
      generateKeyPair()
    }
  }

  override val publicJwk: JsonObject get() = DeviceKey.jwkOf(keyPair().second)

  override fun sign(data: ByteArray): ByteArray {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(keyPair().first)
    signer.update(data)
    return DeviceKey.derToJose(signer.sign())
  }

  override fun destroy() {
    synchronized(lock) { runCatching { keyStore.deleteEntry(alias) } }
  }
}

/**
 * La prova del dispositivo per il rinnovo (`b\api.md` § 3.3), un JWS compatto ES256:
 * `{ typ: "pampa-proof+jwt", alg: "ES256" }` · `{ htm, htu, iat, jti, rth }`, con `rth = base64url(SHA-256(token di
 * rinnovo))`. Il token di rinnovo non entra mai nella prova: solo il suo hash. `iat` è l'ora del server (± 5 minuti).
 */
object DeviceProof {
  fun create(key: DeviceKey, method: String, url: String, refreshToken: String, serverNowMillis: Long): String {
    val header = """{"typ":"pampa-proof+jwt","alg":"ES256"}"""
    val payload = buildJsonObject {
      put("htm", method)
      put("htu", url)
      put("iat", serverNowMillis / 1000)
      put("jti", PampaBase64.encode(PampaCrypto.random(16)))
      put("rth", PampaBase64.encode(PampaCrypto.sha256(refreshToken.toByteArray())))
    }.toString()
    val input = PampaBase64.encode(header.toByteArray()) + "." + PampaBase64.encode(payload.toByteArray())
    return input + "." + PampaBase64.encode(key.sign(input.toByteArray()))
  }
}
