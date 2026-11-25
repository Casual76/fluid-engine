# API · engine-push-fcm

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-push-fcm/push/PampaPush.kt`

- `interface PampaPushHost` — Lo Store che riceve il campanello: di solito la sua `Application`. [onBell] riceve il contenuto del messaggio FCM, data-only e vuoto (`k = posta` per le persone, `k = account` per le azioni dell'account); [onPushToken] il gettone FCM nuovo, da mandare al server (`PUT /v1/devices/{id}/push`).
  - `suspend fun onBell(data: Map<String, String>)`
  - `suspend fun onPushToken(token: String)`
- `class PampaFcmService : FirebaseMessagingService()` — Il ricevitore di FCM dello Store (`d\server-e-chiavi.md` § 6). Fa una cosa sola: sveglia lo Store ([PampaPushHost]), che ritira, apre e passa le cose alle app. Ha circa 10 secondi (il tempo che Android dà a `onMessageReceived`): il ritiro che va oltre lo riprende il lavoro in sottofondo dello Store.
  - `companion object`
    - `const val BELL_MILLIS` — Il tempo del campanello: meno dei 10 secondi di Android, con un margine.
    - `fun carriesNotification(intent: Intent): Boolean` — Il messaggio ha una notifica che Firebase mostrerebbe da sé? Allora non è del nostro server.
- `object PampaPush` — FCM acceso solo con l'account (R9; critica S7). Prima di [enable] Firebase non esiste nel processo: il manifest toglie `FirebaseInitProvider` e spegne l'avvio automatico di FCM e la raccolta di Analytics, così l'app non fa nessuna chiamata verso Google. All'accesso lo Store chiama [enable] con la configurazione del progetto «Account Pampa» (in configurazione, R8: chi ricompila mette la sua), e all'uscita [disable].
  - `fun isEnabled(context: Context): Boolean` — Firebase è acceso in questo processo?
  - `fun enable(context: Context, options: FirebaseOptions)` — All'accesso: accende Firebase e FCM. Il gettone arriva con [token] (o con `onNewToken`).
  - `suspend fun token(context: Context): String?` — Il gettone FCM di questo Store, da registrare sul server; `null` se FCM è spento.
  - `suspend fun disable(context: Context)` — All'uscita (o all'uscita da remoto): il gettone si cancella e FCM non riparte da solo.
  - `fun options(applicationId: String, apiKey: String, projectId: String, senderId: String): FirebaseOptions` — La configurazione del progetto Google, senza `google-services.json`: i quattro valori in chiaro della console.
