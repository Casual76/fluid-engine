package dev.antigravity.fluidengine.account.host.testing

import dev.antigravity.fluidengine.account.host.server.DeviceKey
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlinx.serialization.json.JsonObject

/** La chiave del dispositivo delle prove, in memoria: P-256 del JDK al posto del Keystore. Mai in un'app. */
class SoftwareDeviceKey : DeviceKey {
  private var pair: KeyPair? = newPair()

  private fun newPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
    initialize(ECGenParameterSpec("secp256r1"))
    generateKeyPair()
  }

  private fun current(): KeyPair = pair ?: newPair().also { pair = it }

  val publicKey: ECPublicKey get() = current().public as ECPublicKey

  override val publicJwk: JsonObject get() = DeviceKey.jwkOf(publicKey)

  override fun sign(data: ByteArray): ByteArray {
    val s = Signature.getInstance("SHA256withECDSA")
    s.initSign(current().private)
    s.update(data)
    return DeviceKey.derToJose(s.sign())
  }

  override fun destroy() {
    pair = null
  }
}
