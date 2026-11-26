package dev.antigravity.fluidengine.net

import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * R2 · Un server https finto, solo in locale: un certificato fatto al volo col `keytool` del JDK delle prove (per
 * `localhost` e `127.0.0.1`), che il server usa e il client delle prove riconosce. Niente librerie in piu', niente rete.
 */
internal object LocalTls {
  private const val Password = "prova-r2"

  private val contexts: Pair<SSLContext, SSLContext> by lazy {
    val folder = Files.createTempDirectory("tls").toFile().apply { deleteOnExit() }
    val keys = File(folder, "prova.p12")
    val bin = File(System.getProperty("java.home"), "bin")
    val keytool = listOf("keytool.exe", "keytool").map { File(bin, it) }.first { it.exists() }
    val process = ProcessBuilder(
      keytool.path, "-genkeypair", "-alias", "prova", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost",
      "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12", "-keystore", keys.path,
      "-storepass", Password, "-keypass", Password,
    ).redirectErrorStream(true).start()
    val output = process.inputStream.readBytes().decodeToString()
    check(process.waitFor() == 0) { "keytool: $output" }
    val store = KeyStore.getInstance("PKCS12").apply { keys.inputStream().use { load(it, Password.toCharArray()) } }
    val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
      load(null, null)
      setCertificateEntry("prova", store.getCertificate("prova"))
    }
    keys.delete()
    val server = SSLContext.getInstance("TLS").apply {
      init(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, Password.toCharArray()) }.keyManagers, null, null)
    }
    val client = SSLContext.getInstance("TLS").apply {
      init(null, TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }.trustManagers, null)
    }
    server to client
  }

  /** Per `MockWebServer.useHttps`. */
  val serverSockets: SSLSocketFactory get() = contexts.first.socketFactory

  /** Fa [block] con le connessioni https della piattaforma che riconoscono il certificato delle prove. */
  fun <T> trusted(block: () -> T): T {
    val before = HttpsURLConnection.getDefaultSSLSocketFactory()
    HttpsURLConnection.setDefaultSSLSocketFactory(contexts.second.socketFactory)
    try {
      return block()
    } finally {
      HttpsURLConnection.setDefaultSSLSocketFactory(before)
    }
  }
}
