package dev.antigravity.fluidengine.push

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Critica S7 e regola R9: **nessuna chiamata verso Google prima dell'accesso**. Si guarda il manifest unito (con quelli di
 * Firebase), e la rete: un selettore di proxy registra ogni connessione che il processo prova ad aprire, e la manda nel
 * vuoto, così la prova non va mai online.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PampaPushTest {
  private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
  private val attempts = CopyOnWriteArrayList<URI>()
  private var previous: ProxySelector? = null

  @Before fun trap() {
    previous = ProxySelector.getDefault()
    ProxySelector.setDefault(object : ProxySelector() {
      override fun select(uri: URI): List<Proxy> {
        attempts += uri
        // Un proxy che non esiste: la connessione cade subito, e non esce niente dal PC.
        return listOf(Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress.createUnresolved("rete-spenta.invalid", 9)))
      }

      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    })
  }

  @After fun release() {
    ProxySelector.setDefault(previous)
    FirebaseApp.getApps(context).forEach { it.delete() }
  }

  @Test
  fun `il manifest spegne l'avvio automatico di FCM, Analytics e la delega, e toglie FirebaseInitProvider`() {
    val meta = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
    assertFalse(meta.getBoolean("firebase_messaging_auto_init_enabled", true))
    assertFalse(meta.getBoolean("firebase_analytics_collection_enabled", true))
    assertFalse(meta.getBoolean("firebase_messaging_notification_delegation_enabled", true))
    val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
    assertTrue("FirebaseInitProvider non c'è", providers.none { it.name == "com.google.firebase.provider.FirebaseInitProvider" })
    val service = context.packageManager.getServiceInfo(ComponentName(context, PampaFcmService::class.java), 0)
    assertFalse("il campanello non è esportato", service.exported)
  }

  @Test
  fun `prima dell'accesso Firebase non esiste e nessuno prova ad andare in rete`() {
    ShadowLooper.idleMainLooper()
    assertTrue(FirebaseApp.getApps(context).isEmpty())
    assertFalse(PampaPush.isEnabled(context))
    assertNull(runBlocking { PampaPush.token(context) })
    runBlocking { PampaPush.disable(context) }
    // Il campanello finto prima dell'accesso: lo Store lo riceve, ma Firebase resta spento.
    val bell = Intent("com.google.firebase.MESSAGING_EVENT").putExtra("k", "posta")
    assertFalse(PampaFcmService.carriesNotification(bell))
    ShadowLooper.idleMainLooper()
    assertEquals("nessuna connessione verso Google", emptyList<URI>(), attempts.toList())
  }

  @Test
  fun `all'accesso Firebase si accende con la configurazione dello Store`() {
    PampaPush.enable(context, PampaPush.options("1:1234567890:android:0123456789abcdef", "AIzaFinta", "account-pampa-prova", "1234567890"))
    assertTrue(PampaPush.isEnabled(context))
    assertEquals("account-pampa-prova", FirebaseApp.getInstance().options.projectId)
  }

  @Test
  fun `un messaggio con una notifica dentro non passa, il server non ne manda mai`() {
    val phishing = Intent("com.google.firebase.MESSAGING_EVENT")
      .putExtra("gcm.n.e", "1")
      .putExtra("gcm.n.title", "Il tuo account è bloccato")
      .putExtra("gcm.n.body", "Scrivi qui il codice di recupero")
    assertTrue(PampaFcmService.carriesNotification(phishing))
    assertTrue(PampaFcmService.carriesNotification(Intent().putExtra("gcm.notification.title", "x")))
    assertFalse(PampaFcmService.carriesNotification(Intent().putExtra("k", "posta")))
    Shadows.shadowOf(context).clearStartedServices()
  }
}
