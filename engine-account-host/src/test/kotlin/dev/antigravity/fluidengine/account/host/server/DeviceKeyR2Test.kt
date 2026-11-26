package dev.antigravity.fluidengine.account.host.server

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.test.core.app.ApplicationProvider
import java.io.InputStream
import java.math.BigInteger
import java.io.OutputStream
import java.security.Key
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyPairGeneratorSpi
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2 · La chiave del dispositivo nata due volte. Due fili al primo uso: la pagina d'accesso chiede [DeviceKey.publicJwk]
 * (che va al server) e un rinnovo chiede [DeviceKey.sign]. Sul codice di prima tutti e due non trovavano la chiave e
 * la creavano con lo stesso alias: il Keystore teneva la seconda, il server registrava la prima, e ogni rinnovo dopo
 * veniva rifiutato.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceKeyR2Test {

  @Before fun install() = FakeEcKeyStore.install()

  @After fun uninstall() = FakeEcKeyStore.uninstall()

  @Test
  fun `due fili al primo uso - la pubblica mandata al server e' quella che firma dopo`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val key = AndroidKeystoreDeviceKey(context, "prova.r2.dispositivo")
    var jwk: JsonObject? = null
    val first = thread { jwk = key.publicJwk }
    assertTrue(FakeEcKeyStore.firstInside.await(10, TimeUnit.SECONDS))
    val second = thread { key.sign("rinnovo".toByteArray()) }
    first.join(15_000)
    second.join(15_000)
    val stored = FakeEcKeyStore.entries.getValue("prova.r2.dispositivo").cert.publicKey as ECPublicKey
    assertEquals("la pubblica mandata al server è quella che sta nel Keystore", DeviceKey.jwkOf(stored), jwk)
    val data = "la prova del dispositivo".toByteArray()
    assertTrue("la firma dopo torna con la pubblica registrata", FakeEcKeyStore.verify(stored, data, key.sign(data)))
    assertEquals("una chiave sola", 1, FakeEcKeyStore.generated.get())
  }

  @Test
  fun `dopo l'uscita nasce una chiave nuova, e la pubblica e' quella del Keystore`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val key = AndroidKeystoreDeviceKey(context, "prova.r2.uscita")
    FakeEcKeyStore.noWait = true
    val before = key.publicJwk
    key.destroy()
    val after = key.publicJwk
    assertTrue(before != after)
    val stored = FakeEcKeyStore.entries.getValue("prova.r2.uscita").cert.publicKey as ECPublicKey
    assertEquals(DeviceKey.jwkOf(stored), after)
    val data = "x".toByteArray()
    assertTrue(FakeEcKeyStore.verify(stored, data, key.sign(data)))
  }
}

/**
 * Un «AndroidKeyStore» finto con solo quello che serve alla chiave del dispositivo (EC P-256): le chiavi stanno qui per
 * alias, e la **prima** generazione aspetta un po' che ne arrivi un'altra, così due fili senza lucchetto si
 * incontrano davvero. La seconda scrive dopo la prima: è quella che resta, come nel Keystore di Android.
 */
internal object FakeEcKeyStore {
  class Entry(val private: PrivateKey, val cert: Certificate)

  val entries = ConcurrentHashMap<String, Entry>()
  val generated = AtomicInteger()
  @Volatile var firstInside = CountDownLatch(1)
  @Volatile var secondArrived = CountDownLatch(1)
  @Volatile var noWait = false

  /** Bouncy Castle per le chiavi EC vere: nella sandbox di Robolectric `SunEC` non si costruisce. */
  val bc = BouncyCastleProvider()

  fun install() {
    entries.clear()
    generated.set(0)
    firstInside = CountDownLatch(1)
    secondArrived = CountDownLatch(1)
    noWait = false
    Security.removeProvider("AndroidKeyStore")
    Security.addProvider(FakeEcProvider())
    // La firma di `AndroidKeystoreDeviceKey.sign` sceglie il fornitore da sé: Bouncy Castle deve esserci.
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(BouncyCastleProvider())
  }

  /** Verifica una firma `r || s` (64 byte, come JWS) con Bouncy Castle. */
  fun verify(key: PublicKey, data: ByteArray, jose: ByteArray): Boolean {
    fun int(b: ByteArray): ByteArray {
      val v = BigInteger(1, b).toByteArray()
      return byteArrayOf(0x02, v.size.toByte()) + v
    }
    val body = int(jose.copyOfRange(0, 32)) + int(jose.copyOfRange(32, 64))
    val der = byteArrayOf(0x30, body.size.toByte()) + body
    return Signature.getInstance("SHA256withECDSA", bc).run {
      initVerify(key)
      update(data)
      verify(der)
    }
  }

  fun uninstall() {
    Security.removeProvider("AndroidKeyStore")
    entries.clear()
  }
}

private class FakeEcProvider : Provider("AndroidKeyStore", 1.0, "Keystore finto della prova R2") {
  init {
    put("KeyStore.AndroidKeyStore", FakeEcKeyStoreSpi::class.java.name)
    put("KeyPairGenerator.EC", FakeEcKeyPairGeneratorSpi::class.java.name)
  }
}

private class FakeEcCert(private val key: PublicKey) : Certificate("X.509") {
  override fun getEncoded(): ByteArray = key.encoded
  override fun verify(key: PublicKey?) = Unit
  override fun verify(key: PublicKey?, sigProvider: String?) = Unit
  override fun toString(): String = "FakeEcCert"
  override fun getPublicKey(): PublicKey = key
}

class FakeEcKeyStoreSpi : KeyStoreSpi() {
  override fun engineGetKey(alias: String, password: CharArray?): Key? = FakeEcKeyStore.entries[alias]?.private
  override fun engineGetCertificateChain(alias: String): Array<Certificate>? = FakeEcKeyStore.entries[alias]?.let { arrayOf(it.cert) }
  override fun engineGetCertificate(alias: String): Certificate? = FakeEcKeyStore.entries[alias]?.cert
  override fun engineGetCreationDate(alias: String): Date = Date(0)

  /** Come `AndroidKeyStore`: `getEntry(alias, null)` dà la chiave senza password (quello di serie del JDK la vuole). */
  override fun engineGetEntry(alias: String, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? =
    FakeEcKeyStore.entries[alias]?.let { KeyStore.PrivateKeyEntry(it.private, arrayOf(it.cert)) }
  override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) = throw UnsupportedOperationException()
  override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) = throw UnsupportedOperationException()
  override fun engineSetCertificateEntry(alias: String, cert: Certificate) = throw UnsupportedOperationException()
  override fun engineDeleteEntry(alias: String) {
    FakeEcKeyStore.entries.remove(alias)
  }
  override fun engineAliases(): Enumeration<String> = Collections.enumeration(FakeEcKeyStore.entries.keys.toList())
  override fun engineContainsAlias(alias: String): Boolean = FakeEcKeyStore.entries.containsKey(alias)
  override fun engineSize(): Int = FakeEcKeyStore.entries.size
  override fun engineIsKeyEntry(alias: String): Boolean = FakeEcKeyStore.entries.containsKey(alias)
  override fun engineIsCertificateEntry(alias: String): Boolean = false
  override fun engineGetCertificateAlias(cert: Certificate): String? = null
  override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
  override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}

class FakeEcKeyPairGeneratorSpi : KeyPairGeneratorSpi() {
  private var alias: String? = null

  override fun initialize(keysize: Int, random: SecureRandom?) = throw UnsupportedOperationException()

  override fun initialize(params: AlgorithmParameterSpec, random: SecureRandom?) {
    alias = (params as KeyGenParameterSpec).keystoreAlias
  }

  override fun generateKeyPair(): KeyPair {
    val n = FakeEcKeyStore.generated.incrementAndGet()
    if (!FakeEcKeyStore.noWait) {
      if (n == 1) {
        FakeEcKeyStore.firstInside.countDown()
        FakeEcKeyStore.secondArrived.await(1500, TimeUnit.MILLISECONDS)
      } else {
        FakeEcKeyStore.secondArrived.countDown()
        Thread.sleep(150)
      }
    }
    val pair = KeyPairGenerator.getInstance("EC", FakeEcKeyStore.bc).run {
      initialize(ECGenParameterSpec("secp256r1"))
      generateKeyPair()
    }
    FakeEcKeyStore.entries[alias!!] = FakeEcKeyStore.Entry(pair.private, FakeEcCert(pair.public))
    return pair
  }
}
