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
| l'aspetto comune (anche senza account) | `PampaAccount.appearance(context)`; l'eccezione dell'app con `setAppearanceException` |
| i luoghi comuni (Casa, Scuola) | `PampaAccount.places(context)`, `putPlace` — solo col permesso dell'utente |
| aprire lo Store sulla pagina giusta | `PampaAccount.storeIntent(context, AccountSections.SIGN_IN)` |
| cancellare le copie dopo un'uscita da remoto | `PampaAccount.onWiped(context) { … }` |
| le primitive (HKDF, HMAC, AES-256-GCM) | `PampaCrypto`, con le etichette del contratto in `PampaLabels` |

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
- **Il collegamento a `FluidTheme`** (l'aspetto comune che cambia il tema da solo) e i luoghi con la richiesta di
  permesso sono di F10.8; qui c'e' il canale.
- **Le persone** (`people.row`, `share.*`, «Da X») sono di F10.6, in `engine-people` e nei componenti di
  [account-ui](account-ui.md).
- **Bouncy Castle** entra nell'app con le regole di R8 di Codex (`consumer-rules.pro`: tutto `org.bouncycastle`).
- **La facciata `AccountManager`** (l'account in Impostazioni → Account) non c'e': arriva dopo l'uscita (critica V2).

## Le prove

- `PampaCryptoTest`: HKDF coi casi 1–3 di RFC 5869, AES-256-GCM col caso 16 della specifica GCM, la busta, le versioni
  vecchie (M12), le etichette confrontate col contratto `e2e.json`.
- `PampaBackupFormatTest`: CBOR deterministico, il backup legato al suo posto, il tetto di 5 MB.
- `StoreLocatorTest` (Robolectric): la precedenza M9, la firma M1, lo Store vecchio, nessuno Store.
- Il provider da capo a fondo col cliente vero sta nelle prove di [account-host](account-host.md).

## File

`engine-account/src/main/kotlin/dev/antigravity/fluidengine/account/`: `PampaAccount.kt` (la facciata, il ricevitore,
la copia locale), `PampaAccountClient.kt`, `StoreLocator.kt`, `AccountProtocol.kt` (protocollo e tipi), `crypto/`
(`PampaCrypto`, `PampaLabels`, `PampaEnvelope`), `backup/` (`PampaBackup`, `PampaCbor`).
