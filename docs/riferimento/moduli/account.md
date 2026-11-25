<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/account.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-account

L'Account Pampa visto da ogni app (F10.4, `artifacts/ecosistema/account/b/architettura.md` § 9.4): chi sei, un token
per il servizio dell'app, l'aspetto comune, i luoghi comuni, le chiavi dell'app per la sincronizzazione e per il backup,
e i backup da ripristinare. Pacchetto `dev.antigravity.fluidengine.account`. **Opt-in**, senza interfaccia (la riga
«Account Pampa» e' [account-ui](account-ui.md); il motore dentro Pampa Store e' [account-host](account-host.md)).

**Non usa mai la rete** (regola R9 dell'account): parla solo con Pampa Store sul telefono, con il provider firmato. La
rete la fanno lo Store (verso `account.pampa-apps.com`) e l'app verso il suo servizio, col token che lo Store le da'.

Catalogo completo delle firme: [`../api/account.md`](../api/account.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| sapere se c'e' l'account e chi e' | `PampaAccount.state(context)` (`StateFlow<AccountState>`: `NoStore`, `StoreTooOld`, `SignedOut`, `SignedIn`) |
| la riga «Account Pampa» nelle impostazioni | [account-ui](account-ui.md), `PampaAccountRow(onDownloadStore = …)` |
| un token per il mio servizio (Notes, `sync`) | `PampaAccount.token(context, "notes", setOf("notes.sync"))` → `TokenResult` (15 minuti, solo in memoria) |
| le chiavi per `engine-sync` o per il backup | `PampaAccount.appKey(context, KeyPurpose.APP)` / `KeyPurpose.BACKUP` → `AppKeyResult.Ok(current, keys)` |
| cifrare una riga sincronizzata | `PampaRecordCipher(accountId, pacchetto).seal(key, epoca, raccolta, id, versione, byte)` e `open(…)` |
| il backup dei dati piccoli dell'app | un `PampaBackupSource` (la lista dell'app) e `PampaBackupFormat.seal/open` |
| proporre il ripristino al primo avvio | `PampaAccount.availableBackups(context)` |
| il tema comune dello Store nel mio `FluidTheme` | niente: con `engine-account` fra le dipendenze `EngineSettingsStore.settings` lo mette da sé (F10.8) |
| la sezione Aspetto dell'app con lo Store | `EngineSettingsStore.setSharedException(followShared, EngineAppearanceLayer(…))` |
| l'aspetto comune grezzo (anche senza account) | `PampaAccount.appearance(context)`; tradotto per il tema `PampaAppearance.layerOf` |
| i luoghi comuni (Casa, Scuola) | `PampaAccount.places(context)` (si rilegge da sé agli avvisi), `putPlace` — solo col permesso dell'utente |
| chiedere i luoghi la prima volta | `PampaAccount.placesAccess(context)` (`NOT_ASKED`?) e `placesRequestIntent(context)` con `startActivityForResult` |
| l'icona che segue la famiglia d'icone comune | un `<activity-alias>` per famiglia col meta-data `dev.pampa.account.ICON_SET` (sotto) |
| aprire lo Store sulla pagina giusta | `PampaAccount.storeIntent(context, AccountSections.SIGN_IN)` |
| cancellare le copie dopo un'uscita da remoto | `PampaAccount.onWiped(context) { … }` |
| le primitive (HKDF, HMAC, AES-256-GCM) | `PampaCrypto`, con le etichette del contratto in `PampaLabels` |
| la fila delle persone (F10.6) | `PampaPeople.row(context)` → `List<PeopleRowEntry>`: «I tuoi dispositivi», poi persone e gruppi; vuota senza Store o senza contatti (P1) |
| mandare a una persona, a un gruppo o ai miei dispositivi | `PampaPeople.send(context, id, ShareKind.COPY, titolo, json)` → `ShareResult.Queued`; `PampaPeople.sendToMyDevices(context, indirizzo)` |
| «Condivisi con te» e la notifica dell'app | `PampaPeople.received(context)`; `PampaPeople.onReceived(context) { items -> … }` in `Application.onCreate` |
| «Rispondi», «Togli dai condivisi», «Non mostrare più da X» | `PampaPeople.reply(context, item, testo)`, `PampaPeople.hide(context, item, muteSender)` |

## Come funziona

**Trovare lo Store e fidarsi** (`StoreLocator`). Tre controlli, nell'ordine:
1. **La precedenza** (critica M9). Una build vera dell'app cerca solo `com.pampa.store`; una build di sviluppo (il flag
   `debuggable`, o `AccountClientConfig.devBuild`) cerca prima `com.pampa.store.debug`, poi quello vero. Un'app non
   finisce mai con due account.
2. **Il riconoscimento**: il provider `<pacchetto>.account` col meta-data `dev.pampa.account.HOST = 1`.
3. **Il controllo inverso della firma** (minacce M1): lo Store deve essere firmato come l'app (`pampa.jks`). Un
   `com.pampa.store` firmato da altri, per l'app, non c'e': `NoStore`, e non riceve nemmeno una domanda.

Il manifest del modulo porta i `<queries>` dei due Store (da Android 11 un'app non vede le altre se non lo dice) e il
ricevitore dell'avviso.

**Il protocollo** (`AccountProtocol`, `b/api.md` § 5). `ContentResolver.call(metodo, null, extras)` con un JSON in
`extras["json"]`, come il ponte dei tool. `negotiate` dice le capacita': uno Store senza `state` e' `StoreTooOld`. Lo
Store tiene il protocollo `1` per sempre; le novita' arrivano come capacita' (`people.*` e `share.*` con F10.6).

**Gli avvisi.** Lo Store manda a ogni app Pampa un broadcast esplicito `dev.pampa.account.CHANGED` con solo `what`
(`state`, `appearance`, `grants`, `places`, `keys`, `wiped`). L'app **non si fida del contenuto**: rilegge dal provider.
`wiped` (uscita da remoto) chiama i lavori di `onWiped` solo se lo Store conferma: nessuno dentro e un'uscita da remoto
piu' nuova dell'ultima gestita. Un broadcast falso costa una lettura in piu'.

**Il tema comune (F10.8).** `engine-storage` cerca con `ServiceLoader` una `EngineSharedAppearance`, e questo modulo
la dichiara (`PampaSharedAppearance`, in `META-INF/services`): un'app con `engine-account` che usa `EngineSettingsStore`
ha il tema di Pampa Store **senza scrivere niente**. La regola di chi vince, campo per campo: **l'eccezione dell'app**
(se l'utente gliel'ha data e l'app non segue il comune), **poi il tema comune**, **poi i valori dell'app**. I primi due
gradini sono `PampaAppearance.layerOf`, il terzo `EngineAppearanceLayer.applyTo`. Le chiavi sono quelle del contratto
(`prefs.json`): `appearance.mode` (chiaro, scuro, automatico; il nero vero resta dell'app), `appearance.accent` (un
colore `#rrggbb`, che il tema porta sul preset con quel colore o sul più vicino), `appearance.glass` (`clear` = il vetro
di oggi 0,5, `tinted` = 0,8, `reduced` = 1 sul cursore `LocalFluidGlassPreference`), `appearance.textScale`
(moltiplica la grandezza del sistema), `appearance.contrast` (`high` vale come il contrasto alto di Android),
`motion.reduced`, `icon.set`. Un valore che non si capisce vale «nessuna scelta» su quel campo. Senza Store
(`NoStore`) lo strato è `null` e l'app ha il suo tema (R1). Il primo valore arriva dalla copia locale, così il tema non
lampeggia.

**Le icone (F10.8, critica C6).** L'app dichiara un `<activity-alias>` per famiglia, col meta-data
`dev.pampa.account.ICON_SET` (`default`, `tinted`, `dark`, `clear`) e uno solo acceso nel manifest (l'icona di serie).
`PampaAppIcons` fa il resto: quando arriva l'avviso, un'app **non** in primo piano cambia subito l'alias acceso (prima
accende il nuovo, poi spegne il vecchio, con `DONT_KILL_APP`); un'app **aperta** lo cambia appena passa in sottofondo
(`TRIM_MEMORY_UI_HIDDEN`). Una famiglia che l'app non ha vale l'icona di serie; senza Store l'icona resta com'è.

```xml
<activity-alias android:name=".IconaDiSerie" android:targetActivity=".MainActivity" android:icon="@mipmap/ic_launcher" android:exported="true">
  <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
  <meta-data android:name="dev.pampa.account.ICON_SET" android:value="default" />
</activity-alias>
<activity-alias android:name=".IconaTinta" android:enabled="false" android:targetActivity=".MainActivity" android:icon="@mipmap/ic_launcher_tinta" android:exported="true">
  <!-- lo stesso intent-filter -->
  <meta-data android:name="dev.pampa.account.ICON_SET" android:value="tinted" />
</activity-alias>
```

**I luoghi col permesso (F10.8, scelta B8).** `placesAccess` dice `NOT_ASKED`, `DENIED`, `READ`, `READ_WRITE`,
`NEVER` (MyCloud Gallery, o un'app fuori dalla tabella) o `NO_STORE`. La prima volta l'app apre la richiesta dello
Store con `placesRequestIntent` **e `startActivityForResult`** (`registerForActivityResult(StartActivityForResult())`):
la domanda «Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?» è dello Store, che sa dal sistema chi
chiede. Dopo un «no» l'app non richiede da sola: l'utente cambia idea dalla pagina dell'account. `places(context)` si
rilegge da sé agli avvisi `places`, `state` e `wiped`: un permesso tolto si vede subito.

**Offline.** Stato e aspetto stanno in una copia locale (DataStore `pampa_account`), cosi' l'app parte senza aspettare.
Le scadenze dei token si contano con l'orologio monotono (`expiresAtElapsed`).

**Le chiavi dell'app** (K3). `appKey` da' le chiavi **di quest'app** per tutte le epoche, mai quelle di un'altra.
`Locked` vuol dire che il telefono non e' stato sbloccato dall'accensione (o, con la risposta B o C a Q7, che e'
bloccato): si riprova dopo. `NoE2e`: la cifratura non e' partita, l'app lavora in locale.

**La busta** (`PampaEnvelope`, contratto `e2e.json`). `{ v: 1, e: <epoca>, alg: "A256GCM", n, c }`, in base64url
nel campo `ciphertext`; i dati associati `accountId|pacchetto|raccolta|idRiga|versione` li ricostruisce chi apre (K4).
`PampaRecordCipher` ricorda l'ultima versione vista di ogni riga: un server che ne rimette una vecchia e' scoperto (M12).

**Il backup** (`PampaBackupFormat`, `b/chiavi.md` § 7). Il pacchetto `{ v, app, appVersion, schema, createdAt, files }`
in CBOR deterministico, chiuso con `K_bak(app, epoca)`, legato a `accountId|app|backup|n|epoca`, con la testa
`"PBK1" || epoca || nonce`. Al massimo 5 MB. `fingerprint` ignora la data: un backup uguale non riparte.

## Le persone (F10.6)

`PampaPeople` passa dai metodi `people.row`, `share.send`, `share.received`, `share.reply`, `share.hide` e
`devices.send` del provider (`bpi.md` § 5.3), che lo Store annuncia con le capacità `people`, `share`, `devices`
(`AccountProtocol.PEOPLE_CAPABILITIES`). L'app non vede codici, caselle o chiavi: gli id della fila valgono solo per
lei. Quando arriva qualcosa lo Store manda l'avviso esplicito `CHANGED` con `what = share`; `PampaPeople` rilegge
`share.received` dal provider (non si fida del broadcast) e chiama i lavori registrati con `onReceived`, che mostrano
la notifica **dell'app** («Alessio ti ha condiviso una lista»). Uno Store senza le persone risponde `NoPeople` e la fila
non c'è. Il gestore nello Store è `AccountPeopleMethods`, dato da `engine-people`.

## Usarlo in un'app

```kotlin
// Le impostazioni
PampaAccountRow(onDownloadStore = { openUri(STORE_RELEASE_PAGE) })

// Un token per Notes
when (val t = PampaAccount.token(context, "notes", setOf("notes.sync"))) {
  is TokenResult.Ok -> api.call(bearer = t.token)
  TokenResult.Offline -> showOfflineCapsule()
  else -> Unit // senza account l'app funziona come oggi (R1)
}

// Le chiavi per la sincronizzazione (F10.9 le collega a engine-sync)
val keys = PampaAccount.appKey(context, KeyPurpose.APP) as? AppKeyResult.Ok
```

## I limiti, scritti

- **Le concessioni Google** (`google`, `grants`) rispondono «non concesso» finche' non si accendono al primo uso vero
  (scelta N6). L'API c'e' gia', lo Store la riempira'.
- **Gli adattatori delle app** che tengono il tema nelle loro preferenze (ClasseViva, Fluidify, Gallery, Converter, lo
  Store) sono di F10.9: chi non usa `EngineSettingsStore` mappa `PampaAccount.appearance` con `PampaAppearance.layerOf`
  e `EngineAppearanceLayer.applyTo`.
- **L'accento comune** è un colore del contratto. Il telefono capisce già anche `app` («il colore di ogni app», cioè il
  marchio) e `system` («i colori del sistema»), ma il server del contratto v1 accetta solo `#rrggbb`, quindi lo Store non
  li scrive ancora: oggi una scelta comune d'accento resta finché l'utente non ne sceglie un'altra.
- **Il cursore del vetro** viaggia fra i dispositivi come uno dei tre livelli del contratto (`clear`, `tinted`,
  `reduced`); sul telefono un numero fra 0 e 1 passa già così com'è.
- **Le persone** (`people.row`, `share.*`, «Da X») sono di F10.6, in `engine-people` e nei componenti di
  [account-ui](account-ui.md).
- **Bouncy Castle** entra nell'app con le regole di R8 di Codex (`consumer-rules.pro`: tutto `org.bouncycastle`).
- **La facciata `AccountManager`** (l'account in Impostazioni → Account) non c'e': arriva dopo l'uscita (critica V2).

## Le prove

- `PampaCryptoTest`: HKDF coi casi 1–3 di RFC 5869, AES-256-GCM col caso 16 della specifica GCM, la busta, le versioni
  vecchie (M12), le etichette confrontate col contratto `e2e.json`.
- `PampaBackupFormatTest`: CBOR deterministico, il backup legato al suo posto, il tetto di 5 MB.
- `StoreLocatorTest` (Robolectric): la precedenza M9, la firma M1, lo Store vecchio, nessuno Store.
- `PampaAppearanceTest` (F10.8): le chiavi del contratto, la regola di chi vince, i valori che non si capiscono.
- `PampaAppIconsTest` (F10.8, Robolectric): l'app chiusa cambia subito, quella aperta al sottofondo; il `PackageManager`
  vero con gli alias trovati dal meta-data.
- Due app finte che cambiano tema insieme col `FluidTheme` vero: `sample`, `F10p8SharedThemeTest`.
- Il provider da capo a fondo col cliente vero sta nelle prove di [account-host](account-host.md).

## File

`engine-account/src/main/kotlin/dev/antigravity/fluidengine/account/`: `PampaAccount.kt` (la facciata, il ricevitore,
la copia locale), `PampaAccountClient.kt`, `StoreLocator.kt`, `AccountProtocol.kt` (protocollo e tipi),
`PampaAppearance.kt` (il tema comune e la sorgente di `EngineSettingsStore`), `PampaAppIcons.kt` (le icone), `crypto/`
(`PampaCrypto`, `PampaLabels`, `PampaEnvelope`), `backup/` (`PampaBackup`, `PampaCbor`);
`src/main/resources/META-INF/services/` (la sorgente per `ServiceLoader`).
