package dev.antigravity.fluidengine.push

import android.content.Context
import android.content.Intent
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lo Store che riceve il campanello: di solito la sua `Application`. [onBell] riceve il contenuto del messaggio FCM,
 * data-only e vuoto (`k = posta` per le persone, `k = account` per le azioni dell'account); [onPushToken] il gettone
 * FCM nuovo, da mandare al server (`PUT /v1/devices/{id}/push`).
 */
interface PampaPushHost {
  suspend fun onBell(data: Map<String, String>)
  suspend fun onPushToken(token: String)
}

/**
 * Il ricevitore di FCM dello Store (`d\server-e-chiavi.md` § 6). Fa una cosa sola: sveglia lo Store ([PampaPushHost]),
 * che ritira, apre e passa le cose alle app. Ha circa 10 secondi (il tempo che Android dà a `onMessageReceived`): il
 * ritiro che va oltre lo riprende il lavoro in sottofondo dello Store.
 *
 * **Le notifiche non passano** (critica S7): un messaggio FCM con una notifica dentro (`gcm.n.*`, `gcm.notification.*`)
 * il pacchetto di Firebase lo mostrerebbe da solo, senza passare da noi. Il server dello Store non ne manda mai: se ne
 * arriva una, viene da chi ha rubato la chiave di FCM, e qui si butta senza mostrarla.
 */
class PampaFcmService : FirebaseMessagingService() {
  override fun handleIntent(intent: Intent) {
    if (carriesNotification(intent)) return
    super.handleIntent(intent)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    val host = application as? PampaPushHost ?: return
    runBlocking { withTimeoutOrNull(BELL_MILLIS) { host.onBell(message.data) } }
  }

  override fun onNewToken(token: String) {
    val host = application as? PampaPushHost ?: return
    runBlocking { withTimeoutOrNull(BELL_MILLIS) { host.onPushToken(token) } }
  }

  companion object {
    /** Il tempo del campanello: meno dei 10 secondi di Android, con un margine. */
    const val BELL_MILLIS = 9_000L

    /** Il messaggio ha una notifica che Firebase mostrerebbe da sé? Allora non è del nostro server. */
    fun carriesNotification(intent: Intent): Boolean {
      val extras = intent.extras ?: return false
      return extras.keySet().any { it.startsWith("gcm.n.") || it.startsWith("gcm.notification.") }
    }
  }
}

/**
 * FCM acceso solo con l'account (R9; critica S7). Prima di [enable] Firebase non esiste nel processo: il manifest toglie
 * `FirebaseInitProvider` e spegne l'avvio automatico di FCM e la raccolta di Analytics, così l'app non fa nessuna
 * chiamata verso Google. All'accesso lo Store chiama [enable] con la configurazione del progetto «Account Pampa» (in
 * configurazione, R8: chi ricompila mette la sua), e all'uscita [disable].
 */
object PampaPush {
  /** Firebase è acceso in questo processo? */
  fun isEnabled(context: Context): Boolean = FirebaseApp.getApps(context.applicationContext).isNotEmpty()

  /** All'accesso: accende Firebase e FCM. Il gettone arriva con [token] (o con `onNewToken`). */
  fun enable(context: Context, options: FirebaseOptions) {
    val app = context.applicationContext
    if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app, options)
    FirebaseMessaging.getInstance().isAutoInitEnabled = true
  }

  /** Il gettone FCM di questo Store, da registrare sul server; `null` se FCM è spento. */
  suspend fun token(context: Context): String? {
    if (!isEnabled(context)) return null
    return FirebaseMessaging.getInstance().token.await()
  }

  /** All'uscita (o all'uscita da remoto): il gettone si cancella e FCM non riparte da solo. */
  suspend fun disable(context: Context) {
    if (!isEnabled(context)) return
    val fcm = FirebaseMessaging.getInstance()
    fcm.isAutoInitEnabled = false
    runCatching { fcm.deleteToken().await() }
  }

  /** La configurazione del progetto Google, senza `google-services.json`: i quattro valori in chiaro della console. */
  fun options(applicationId: String, apiKey: String, projectId: String, senderId: String): FirebaseOptions =
    FirebaseOptions.Builder().setApplicationId(applicationId).setApiKey(apiKey).setProjectId(projectId).setGcmSenderId(senderId).build()

  private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
  }
}
