# engine-push-fcm

Il campanello di Pampa Store (F10.6, `artifacts/ecosistema/account/d/server-e-chiavi.md` § 6): il ricevitore di Firebase
Cloud Messaging. Pacchetto `dev.antigravity.fluidengine.push`. **Opt-in e solo per lo Store**: nessun'altra app lo
include, perché il campanello è uno solo e passa le cose alle app col ponte firmato.

Firebase Messaging non è una libreria libera: è coperta dal permesso della GPL-3.0 § 7 in
`LICENSES/Permesso-librerie-Google.md` (scelta Q6) e sta solo qui.

Catalogo completo delle firme: [`../api/push-fcm.md`](../api/push-fcm.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| ricevere il campanello | la `Application` dello Store implementa `PampaPushHost` (`onBell(data)`, `onPushToken(token)`) |
| accendere FCM all'accesso | `PampaPush.enable(context, PampaPush.options(appId, apiKey, projectId, senderId))` |
| il gettone da registrare sul server | `PampaPush.token(context)` → `PUT /v1/devices/{id}/push` (`null` se FCM è spento; lancia se il gettone non arriva: riprovare) |
| spegnerlo all'uscita | `PampaPush.disable(context)` |

## Come funziona

- **Niente rete prima dell'accesso** (R9; critica S7). Il manifest del modulo toglie `FirebaseInitProvider`, così
  Firebase non parte da solo, e spegne l'avvio automatico di FCM (`firebase_messaging_auto_init_enabled`), la raccolta
  di Analytics (`firebase_analytics_collection_enabled`) e la delega delle notifiche a Play services
  (`firebase_messaging_notification_delegation_enabled`). Firebase nasce solo con `PampaPush.enable`, all'accesso.
- **Solo messaggi data-only e vuoti.** Il server manda `k = posta` (le persone) o `k = account` (azioni dell'account):
  niente nome, niente tipo, niente anteprima. `PampaFcmService` sveglia lo Store e basta, entro 9 secondi.
- **Le notifiche non passano.** Un messaggio FCM con una notifica dentro (`gcm.n.*`) il pacchetto di Firebase lo
  mostrerebbe da solo: `PampaFcmService.handleIntent` lo butta. Chi rubasse la chiave di FCM non potrebbe mostrare
  «Il tuo account è bloccato, scrivi il codice».
- **Senza Play services** (Huawei, ROM libere): niente campanello; lo Store ritira quando si apre e con un controllo ogni
  15 minuti (F10.7).

## Le prove

`PampaPushTest` (Robolectric): il manifest unito con quelli di Firebase (i tre interruttori spenti, niente
`FirebaseInitProvider`, il servizio non esportato), nessun tentativo di rete prima dell'accesso (un selettore di proxy
registra ogni connessione e la manda nel vuoto), l'accensione con la configurazione dello Store, e il messaggio con una
notifica che non passa.
