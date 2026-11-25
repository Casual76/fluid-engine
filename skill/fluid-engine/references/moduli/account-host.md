# engine-account-host

Il motore dell'Account Pampa dentro **Pampa Store** (F10.4, `artifacts/ecosistema/account/b/architettura.md` § 9.5 e
`b/chiavi.md`). Lo usa solo lo Store: le altre app hanno [account](account.md), che chiede a lui. Pacchetto
`dev.antigravity.fluidengine.account.host`. **Opt-in**.

Dentro: il provider firmato, la sessione con la chiave del dispositivo, i token in memoria, la **custodia** della
chiave dei dati (epoche, porte, passaggio col QR, verifiche K8 e K9, due classi di chiavi), il codice di recupero, i
luoghi, l'aspetto comune e il backup dello Store. Tutta la crittografia e' Bouncy Castle (progetto, T9): HPKE «base»
con X25519, HKDF-SHA256 e AES-256-GCM, provata coi vettori dell'RFC 9180 e coi valori usciti dal codice del server.

Catalogo completo delle firme: [`../api/account-host.md`](../api/account-host.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| esporre l'account alle app | una sottoclasse di `AccountHostProvider` che da' un `AccountProtocolHandler` |
| entrare, rinnovare, uscire | `AccountSession`: `adopt(risposta d'ingresso)`, `storeToken()`, `appToken(…)`, `signOut()`, `onRevoked(motivo)` |
| la prima cifratura, il codice di recupero, una porta | `CustodyService.startEncryption(codice)`, `createRecoveryCode()`, `addPrfDoor(…)` |
| ruotare la chiave dopo un «perso o rubato» | `CustodyService.rotate(EpochKind.LOST)` |
| ricevere in silenzio l'epoca creata da un altro dispositivo | `CustodyService.catchUp()`, con la porta del dispositivo (`ensureDeviceDoor`) |
| il telefono nuovo | `openWithDoor(DoorSecret.recovery(codice))` o `DoorSecret.fromPasskey(risultato)`; col QR `beginTransfer()` → `completeTransfer(offer)` |
| le opzioni e la risposta della passkey | `StorePrf.assertionRequest(opzioni)` / `creationRequest(…)`, poi `FluidWebAuthnJson.readAssertion` (`engine-auth`) → `DoorSecret.fromPasskey` |
| il telefono vecchio nel passaggio | `TransferQr.parse(testo)` → `transferInfo(qr)` (la conferma) → `confirmTransfer(qr)` |
| le chiavi di un'app (`appKey`) | `CustodyService.appKey(pacchetto, scopo)` → `AppKeyOutcome` |
| la logica pura delle chiavi | `KeyCustody` (verifica, porte, epoche, passaggio) e `CustodyMath` (le formule, riga per riga) |
| dove si chiudono le chiavi sul telefono | `CustodyKeyring` sopra un `CustodyVault`: `AndroidKeystoreVault`, cioe' `KeystoreCustodyVault` sopra il `VaultKeystore` di `engine-vault` |
| la classe di ogni chiave (Q7) | `KeyClassPolicy.CHOICE` |
| il backup dello Store | `StoreBackupJob`, `BackupScheduler.schedule(context)`, l'`Application` che implementa `AccountHostOwner` |
| il tema comune (Store → Aspetto) | `AppearanceStore.setGlobal(mapOf("appearance.mode" to "dark", …))`; fra i dispositivi `AppearanceSync.sync()` |
| avvisare le app di un cambio | `AccountChangedBroadcaster(context).send(listOf("appearance"))` (dal callback dei depositi) |
| la richiesta dei luoghi di un'app | `PlacesConsent.begin(PlacesConsent.callerOf(activity), wantsWrite(intent))` → `Ask.question` → `answer(ask, allow)` |
| «Chi usa i tuoi luoghi» nella pagina dell'account | `PlacesStore.decisions()`, `setAccess(pacchetto, PlaceAccess.NONE)` (revoca), `forget` (chiedi di nuovo) |
| i luoghi fra i dispositivi | `PlacesSync.sync()` (spazio `places` di `engine-sync`, cifrato con `K_com(places)`) |
| provare senza Keystore | `testing.MemoryCustodyVault` (bloccato, prima dello sblocco, chiave persa), o `KeystoreCustodyVault` sopra `MemoryVaultKeystore` di `engine-vault`; `testing.SoftwareDeviceKey` |

## Come funziona

**Il provider** (`AccountHostProvider`). `exported="true"` **senza** `android:permission`, autorita'
`<applicationId>.account`, meta-data `dev.pampa.account.HOST = 1`. A ogni chiamata: la firma di chi chiama
(`checkSignatures(callingUid, myUid)`, altrimenti `SecurityException`, che l'app legge come «Store assente»), il
pacchetto da `getPackagesForUid` (dal sistema; un UID condiviso e' rifiutato), poi la tabella delle app (`AppPolicy`,
uguale a `contract/v1/apps.json`). `negotiate`, `state` e `appearance` rispondono a ogni app con la firma dello Store;
token, chiavi e luoghi solo a chi e' nella tabella, e solo per cio' che e' suo (K3).

**La sessione.** Il token di rinnovo sta solo nel file della sessione (`SessionStore`), chiuso con la cassaforte e
dentro `noBackupFilesDir`: escluso dal backup di Android. Ogni rinnovo porta la **prova del dispositivo**
(`DeviceProof`, JWS ES256 con la chiave P-256 del Keystore, `rth` = hash del token di rinnovo, `iat` dall'ora del
server). I token stanno in memoria (`TokenCache`), con l'orologio monotono. `session_revoked` con motivo `remote`,
`lost` o `all` cancella chiavi **e** copie e avvisa le app con `wiped`; l'uscita volontaria cancella solo le chiavi.

**La custodia** (`KeyCustody`, logica pura). La chiave dei dati `CD` a epoche, nata sul telefono. Le porte (il codice
di recupero, ogni passkey col PRF) sono coppie X25519 ricavate dal segreto; sul server vanno solo pubblica, sale,
sigilli HPKE, il tag (K8) e il `check` della radice (K9). Prima di usare qualunque cosa del server, la custodia
verifica: la catena dei `link` col tipo dentro, i tag all'epoca di riferimento giusta (dopo un «perso o rubato» solo
quelli nuovi, S11), le porte che conosceva, l'epoca che non torna indietro. Dal server ostile si accorge sempre.

**Le porte dei dispositivi** (`device:<deviceId>`, contratto `e2e.json`, `deviceDoor`). Ogni dispositivo dentro crea la
sua porta appena ha la chiave (dopo il PRF, il codice o il QR): 32 byte casuali che restano nella sua custodia, in classe
madre. Chi ruota sigilla l'epoca nuova per tutte le porte, quindi anche per gli altri dispositivi, che la aprono in
silenzio (`catchUp`) con gli stessi controlli di una porta; il dispositivo fatto uscire perde la sua porta sul server
nello stesso istante. Non contano per K2. Le porte degli altri dispositivi possono sparire (escono): non e' un segnale;
il codice, le passkey e la porta di questo dispositivo, se spariscono, si'. Senza porta del dispositivo resta
`CustodyBehindException`, e ci si rimette in pari con una porta vera o col passaggio.

**Il codice di recupero** (`RecoveryCode`). Lo genera il telefono: 128 bit, base32 in gruppi da cinque. Ne nascono due
valori indipendenti: `auth` (l'unico che va al server) e il seme della porta, che resta qui (critica S1). `toString`
lo copre; `parse` perdona minuscole, spazi e `0/1/8` al posto di `O/I/B`.

**Il passaggio col QR** (critica S2). Il nuovo mostra `{ transferId, N, s }`; il vecchio lo inquadra, conferma, sigilla
tutte le epoche verso `N` (HPKE base) e aggiunge `mac` col segreto `s`. Il server non vede ne' `N` ne' `s`.

**Le due classi** (K10, **Q7 in attesa**: `KeyClassPolicy`). Con la raccomandazione A: «consegna» (dopo il primo
sblocco, anche a telefono bloccato) per la chiave del dispositivo, la sessione e le chiavi di lavoro gia' ricavate;
«madre» (solo sbloccato) per la chiave dei dati e la radice della persona. `CustodyKeyring` ricava le chiavi di lavoro a
telefono sbloccato e le legge in sottofondo senza mai aprire la madre.

**L'aspetto comune (F10.8)** (`AppearanceStore`). Risponde anche senza account. Scrive solo lo Store: il tema comune
(`setGlobal`, dalla pagina Aspetto) e le eccezioni delle app (`setException`, dal metodo `setAppearance`). Le chiavi e i
valori sono quelli del contratto (`PrefsContract`, confrontato con `prefs.json`): una scelta che il server
rifiuterebbe non nasce. Ogni campo porta l'**orologio logico ibrido** di chi l'ha scritto (`SyncClock` di
`engine-sync`, nel formato del server `ms-contatore-nodo`): fra i dispositivi vince, campo per campo, il più recente,
anche con gli orologi sfasati; un'eccezione tolta è una lapide col suo orologio. `AppearanceSync` legge `GET
/v1/prefs?since=`, fonde, poi manda solo le chiavi che il server annuncia (critica M1); una riga rifiutata per
l'orologio (`bad_hlc`, telefono avanti di oltre un giorno) rinasce all'ora del server e riparte.

**I luoghi (F10.8)** (`PlacesStore`, `PlacesConsent`, `PlacesSync`). I luoghi sono uno spazio di `engine-sync`, uno per
riga con id casuale, e il registro è un blob chiuso con la cassaforte (`BlobSyncStore`). **La cassaforte dei luoghi è
sua**, con un prefisso d'alias diverso dalla custodia: l'uscita volontaria cancella le chiavi della custodia, non i
luoghi (R1); solo un'uscita da remoto li cancella (`PlacesStore.wipe()`). Il permesso: mancante = mai chiesto, `NONE`
scritto = negato; la domanda è dello Store (`PlacesConsent`), dopo un «no» non si ripete. Fra i dispositivi viaggiano
sopra `/v1/sync/places` (ambito `sync.places`, solo dello Store) con `K_com(places)` e i dati associati del contratto
(`AccountSyncRelay`, `PlacesSealer`); due creazioni dello stesso luogo senza vedersi lasciano la più recente.

**Gli avvisi alle app (F10.8)** (`AccountChangedBroadcaster`). Un broadcast esplicito per app, solo a quelle col
ricevitore di `engine-account` **e la firma dello Store**. Il manifest del modulo porta la `<queries>` per vederle.

**Il Keystore** (`KeystoreCustodyVault`, e `AndroidKeystoreVault` per lo Store). Passa dalla cassaforte dell'engine
(`engine-vault`, F8.23): una chiave AES-256-GCM per classe, creata con `VaultKeySpec` (`VaultKeyAuth.None`) e usata con
`aesEncrypt`/`aesDecrypt`; il file e' `nonce(12) ‖ testo cifrato ‖ tag`. La madre ha `unlockedDeviceRequired` solo da
Android 15: la pagina di `KeyGenParameterSpec.Builder` dice che su Android 9–14 queste chiavi si cancellavano togliendo
il blocco schermo e non si creavano senza. Sotto, il controllo «bloccato» lo fa il codice
(`KeyguardManager.isDeviceLocked`). StrongBox di serie solo per la madre (il ripiego sul TEE lo fa `engine-vault`). Gli
errori della cassaforte diventano quelli della custodia: `Invalidated` → `CustodyKeyLostException` (lo Store riapre da
una porta), `NeedsAuthentication` → `CustodyLockedException`, `Corrupt` → `PampaCryptoException`.
Sul Tab S9 (Android 16, 25/11): TEE ~8 ms a chiusura o apertura, StrongBox ~120 ms; la madre si usa di rado.

## I limiti, scritti

- **La rimessa in pari vuole la madre**: si fa a telefono sbloccato, al primo sblocco dopo una rotazione.
- **Il PRF** lo fa `engine-auth` (`FluidWebAuthnJson` con `FluidPrfInput.PampaV1`, uguale a `PampaLabels.prfInput()`):
  `StorePrf` e' la porta dello Store, e `AccountServerClient.call` rifiuta un corpo che porta ancora un'uscita del PRF.
- **Le persone** (radice e chiavi di lavoro, `K_com(persona)`) hanno le chiavi pronte nel portachiavi; il resto e' F10.6.
- **Le schermate** sono dello Store (F10.7, F10.11): anche la pagina della richiesta dei luoghi, che qui ha la logica.
- **Quando sincronizzare** tema e luoghi lo decide lo Store (F10.7): dopo un cambio, all'apertura, al rinnovo.

## Le prove

- `HpkeVectorsTest`: RFC 9180 A.1.1 e la suite di Pampa (AES-256-GCM) coi vettori CFRG, 257 cifrature e gli export.
- `ServerInteropTest`: auth, semi, pubbliche, tag, check, link, chiavi delle app, sigilli, buste e passaggio fatti dal
  codice del server Pampa-account.
- `KeyCustodyBenchTest`: il banco di `b/chiavi.md` § 11 voce per voce (telefono perso in ogni riga, server ostile, tre
  rotazioni, due porte, PRF diverso, niente segreti, passaggio ostile e il promemoria delle tre parole, PRF dello Store,
  tag dopo un furto).
- `DeviceDoorBenchTest`: la voce 14, le porte dei dispositivi (il furto, il tablet che si rimette in pari, K2, il server
  che inventa, cambia o toglie una porta, il segreto che non parte).
- `ContractFormatsTest`: i tipi di porta, la testa `PBK1` e il QR del passaggio contro `e2e.json`.
- `CustodyKeyringTest`: le due classi nelle tre risposte a Q7, il Keystore perso, prima del primo sblocco, l'uscita.
- `KeystoreCustodyVaultTest`: l'adattatore sopra `MemoryVaultKeystore` (le ricette delle classi, bloccato, chiave
  persa, file toccato, `NeedsAuthentication`, la custodia intera).
- `AccountSessionTest` (su HTTP con MockWebServer): la prova del dispositivo verificata, i token, le uscite, il
  passaggio fra due telefoni, il server ostile, niente segreti in intestazioni e corpi.
- `AccountHostProviderTest` (Robolectric): firme diverse a ogni metodo, UID condiviso, K3, luoghi, `wiped`.
- `StoreBackupTest`: il backup una volta, solo se cambiato, in tasca con la A, chiuso con la C, WorkManager.
- `AppearanceSyncTest` (F10.8): due dispositivi e il server finto con le regole di `choices.ts`; il conflitto sullo
  stesso campo, gli orologi sfasati di tre ore, l'orologio avanti di due giorni, le lapidi delle eccezioni, il contratto.
- `PlacesSyncTest` (F10.8): i luoghi cifrati fra due dispositivi, il conflitto per campo, la cancellazione che vince, il
  doppione, il server che scambia due righe, l'uscita che non li toglie.
- `PlacesConsentTest` (F10.8, Robolectric): negato, concesso, revocato, «chiedi di nuovo», la scrittura in più, Gallery
  mai; l'avviso solo alle app con la firma dello Store.

## File

`engine-account-host/src/main/kotlin/dev/antigravity/fluidengine/account/host/`: `AccountHostProvider.kt` (provider e
gestore del protocollo), `AccountSession.kt`, `CustodyService.kt`, `AppPolicy.kt`, `LocalStores.kt` (luoghi),
`AppearanceStore.kt` (aspetto, `PrefsContract`, `PrefsHlc`), `PlacesConsent.kt`, `AccountChangedBroadcaster.kt`,
`sync/` (`AccountSyncRelay`, `PlacesSealer`, `PlacesSync`, `AppearanceSync`), `custody/` (la custodia), `server/`
(client, chiave del dispositivo, sessione, token), `backup/`, `testing/`.
