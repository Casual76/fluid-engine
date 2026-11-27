# engine-security

**Il blocco con l'impronta** (4.0, F8.23, idea `blocco-biometrico`): l'app che si blocca quando ci torni dopo il tempo
scelto e si apre con l'impronta, il volto o il codice del telefono, con il PIN dell'app di riserva; il contenuto protetto
(una cartella, un album, le note) che resta chiuso finché non lo apri; l'anteprima oscurata nelle app recenti; la riga
pronta per le impostazioni. È «Richiedi Face ID» di iOS 18, le note bloccate e l'album Nascosti. Pacchetto
`dev.antigravity.fluidengine.security`. **Opt-in**; porta `engine-ui` e [`engine-vault`](vault.md).

Catalogo completo delle firme: [`../api/security.md`](../api/security.md).

**Il blocco è una porta, non una cassaforte**: chi ha i file dell'app li legge comunque. I dati che contano vanno in una
sezione all'impronta della cassaforte, che il blocco richiude insieme all'app.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| bloccare tutta l'app al rientro (Pampa Mail) | un `FluidAppLock(context)` nell'`Application`, e `FluidAppLockHost(lock) { App() }` alla radice |
| la riga nelle impostazioni (interruttore, «Blocca dopo», PIN dell'app) | `FluidAppLockSettingsGroup(lock)` |
| condividere o scegliere un file senza che l'app si blocchi al ritorno | `rememberFluidLockSafeLauncher(contract) { … }`, `lock.startExternal(context, intent)`, o `lock.allowLeaving()` prima |
| una cartella o un album che si apre con l'impronta (Notes, MyCloud) | `FluidVaultContent(section, "Privata") { … }` con una sezione `VaultAccess.Biometric()` |
| lo stesso segno «è bloccato · Visualizza» senza cassaforte | `FluidLockedContent(unlocked, title, onView) { … }` |
| un valore riservato che si scopre solo con l'impronta (PampAI) | `LocalFluidPrivateGate provides rememberFluidBiometricGate("Mostra la chiave")` attorno a `Modifier.fluidPrivate` |
| il dialogo di sistema per la cassaforte o per me | `rememberFluidSystemAuthenticator()` (`FluidSystemAuthenticator`) |
| sapere se il telefono ha impronta, volto, codice | `FluidBiometrics.capability(context)` |
| le parole in inglese | `LocalFluidSecurityLabels provides FluidSecurityLabels.English` (e `VaultLabels.English` per la cassaforte) |
| il dialogo di vetro | **no**: il dialogo dell'impronta è di sistema e non si cambia; di vetro è la schermata di blocco |

## `FluidAppLock`: quando si blocca

Uno per app, creato all'avvio: conta da sé le attività aperte (come `ProcessLifecycleOwner`, senza la libreria).

- **Al rientro** dopo almeno `timeout` fuori (di serie «Subito», come «Richiedi Face ID»: l'app «si blocca di nuovo da
  sola quando la chiudi»; poi 1, 5, 15 minuti, un'ora). Allo **schermo spento** l'app esce, quindi con «Subito» si blocca.
- **All'avvio** a freddo, se acceso.
- **Mai durante una condivisione o una scelta di file**: `allowLeaving()` prima di aprirle (lo fanno
  `rememberFluidLockSafeLauncher` e `startExternal`), e il rientro da lì non blocca. Il permesso vale per un rientro solo,
  per 10 minuti al più, e si toglie se l'app torna in primo piano senza essere uscita (un pannello che la copre solo in
  parte). Anche il dialogo di sistema non la blocca.
- **Mai per una rotazione** o un cambio di configurazione.
- Bloccandosi richiude le sezioni della cassaforte (`lockVaultOnLock`).

Le impostazioni e il PIN stanno in una sezione «solo telefono» della cassaforte, fuori dal backup: su un telefono nuovo il
blocco riparte spento. Il PIN (4–8 cifre, 6 di serie come iPhone) si tiene come PBKDF2 con un sale, mai in chiaro, e il
PBKDF2 (210 000 giri) gira su `Dispatchers.Default`, mai sul thread di chi chiama; dal quinto errore di fila si aspetta
come iPhone: 1 minuto, 5, 15, 15, poi un'ora (`FluidAppLockDefaults.pinDelay`). Un riavvio del telefono (o l'ora
spostata) non accorcia l'attesa: dopo il riavvio l'attesa riparte una volta da capo, poi il PIN giusto apre (R2: prima
ripartiva a ogni tentativo, per sempre).

**Le impostazioni che non si leggono all'avvio** (R2). Se la chiave della sezione non c'è più (Keystore azzerato) o la
scheda è rovinata, il blocco riparte spento: sono perse, come su un telefono nuovo (e chi ha i file dell'app potrebbe
comunque cancellarle). Un errore passeggero (il Keystore che non risponde, il disco) non apre l'app: resta coperta
(`FluidAppLockState.Loading`) e si riprova da sola, da mezzo secondo a 30 s.

## `FluidAppLockHost`: la schermata di blocco

- **Vetro dell'engine**: un pannello di vetro (`GlassRole.Modal`, la tinta e l'ottica dei pannelli) sopra uno sfondo
  fermo, di serie `FluidLockBackdrop` nei colori del tema, oppure il `backdrop` dell'app (una foto). L'icona e il nome
  dell'app, «Pampa Mail è bloccata», la frase su come aprirla, il tasto «Sblocca» (vetro tinto) e «Usa il PIN dell'app».
- **Il dialogo parte da solo**, una volta per blocco, quando l'attività è davanti (come Face ID all'apertura); annullato,
  il tasto lo riapre. Il tasto in basso del dialogo è «Usa il PIN dell'app» quando c'è il PIN.
- **Il PIN** è il campo a codice di F8.3 (`FluidCodeField`, nascosto): sbagliato trema e lo dice, troppi tentativi fanno
  aspettare con un solo risveglio (nessun conto alla rovescia che ridisegna).
- **Il contenuto bloccato resta composto** (navigazione e stato restano), ma non si disegna, non prende il dito e TalkBack
  non lo legge (`clearAndSetSemantics`); il fuoco e la tastiera si chiudono. Con una tastiera, un D-pad o Switch Access il
  fuoco non ci entra (il contenuto è un gruppo del fuoco che rifiuta l'ingresso: Tab passa ai tasti della schermata) e i
  tasti non gli arrivano (R2: prima Invio premeva un tasto invisibile).
- **Le capacità del telefono si rileggono a ogni ritorno in primo piano** (`rememberFluidAuthCapability`): un blocco
  schermo o un'impronta messi nelle impostazioni del telefono mentre l'app è bloccata si vedono al rientro.
- **L'anteprima nelle recenti è oscurata** finché il blocco è acceso: `setRecentsScreenshotEnabled(false)` da Android 13,
  `FLAG_SECURE` prima (che toglie anche gli screenshot). È `FluidSecureWindowHold` di `engine-ui`, lo stesso segno dei
  valori riservati, contato.
- **All'apertura** la schermata si dissolve (320 ms, cresce del 6%) e l'app entra dalla sfocatura (18 dp a zero): è un
  cambio che fa l'app, non il dito. Con «Riduci movimento» si apre e basta. Il moto si legge nel livello grafico: niente
  ricomposizioni per fotogramma.
- **Ferma non ridisegna**: lo sfondo si disegna una volta e il vetro lo campiona.
- TalkBack: la schermata è un pannello («Schermata di blocco»), il titolo un'intestazione, la frase una regione viva.

## Il dialogo di sistema: `FluidSystemAuthenticator`

**Perché non `androidx.biometric`.** La sua API per una `ComponentActivity` (`registerForAuthenticationResult`, dalla
1.4.0-alpha06) è ancora candidata: 1.4.0-rc01 del 18 novembre 2026 (developer.android.com, letto il 24/11/2026), compilata
contro Android 16 QPR2 (`minCompileSdk` 36.1, più dell'engine e delle app); la 1.1.0 stabile vuole una
`FragmentActivity`. Il `BiometricPrompt` **di sistema** (Android 9+) lavora con qualunque attività, e lo usiamo
direttamente:

| Android | blocco dell'app | sezione all'impronta | riserva «codice del telefono» |
|---|---|---|---|
| 11+ | impronta o volto (anche «debole») **o** codice, nello stesso dialogo | impronta forte col `CryptoObject` | `BiometricPrompt` col solo codice |
| 10 | impronta o volto, con «Usa il codice» (`setDeviceCredentialAllowed`) | impronta forte | la conferma del `KeyguardManager` |
| 9 | impronta, con «Usa il PIN dell'app» o «Annulla» | impronta | la conferma del `KeyguardManager` |
| 8 | il codice del telefono (`KeyguardManager`) o il PIN | — (riserva) | la conferma del `KeyguardManager` |

Quando la 1.4.0 sarà stabile e l'engine compilerà contro la 36.1 si potrà passare a lei senza cambiare l'API
(`VaultAuthenticator` e `FluidLockAuthenticator` restano).

**Il sensore bloccato** (troppe impronte sbagliate, `BIOMETRIC_ERROR_LOCKOUT`, anche per sempre) per una sezione della
cassaforte con la riserva vale come il tasto della riserva: `VaultAuthResult.Fallback`, e la sezione passa al codice del
telefono o al PIN dell'app (R2: prima restava chiusa). Senza riserva resta `Failed` con la frase del sistema.

## La riga delle impostazioni

`FluidAppLockSettingsGroup` (R2):
- **Spegnere il blocco, cambiare o togliere il PIN chiedono prima chi sei**, come iOS per «Richiedi Face ID» e per
  «Cambia codice»: il dialogo di sistema (il parametro in coda `authenticator`, di serie quello dell'attività), oppure, se il telefono non
  ha né impronta né codice, il PIN di adesso (`FluidSecurityLabels.pinCurrentTitle`, con gli stessi errori e le stesse
  attese della schermata di blocco). Accenderlo e scegliere il primo PIN non chiedono niente.
- **Un salvataggio rifiutato dalla cassaforte lo dice** (`FluidSecurityLabels.settingSaveFailed`, una riga in fondo al
  gruppo), e la scelta del PIN resta aperta: prima sembrava accettato.
- **Il blocco schermo si rilegge al ritorno**: messo nelle impostazioni del telefono, l'interruttore si accende senza
  ricreare la schermata.

## Il contenuto protetto

`FluidLockedContent` è il segno di iOS: il lucchetto, «L'album Nascosto è bloccato», «Usa l'impronta per vederlo» e il
tasto «Visualizza» (le parole di Foto e Note di iOS 27: «View Album», «View Note»). Chiuso, il contenuto non si compone.
`FluidVaultContent` lo lega a una sezione della cassaforte: «Visualizza» apre la sezione (`unlock` col dialogo, poi la
riserva con la frase pronta se l'impronta è cambiata), e il contenuto si richiude da solo quando la sezione si richiude
(dopo qualche minuto senza usarla, come le note di iOS, allo schermo spento, al blocco dell'app).

## Trappole

- **`FluidPickerRow` della riga delle impostazioni vuole un `FluidGlassModalHost`** alla radice, come ogni portale.
- **Un blocco creato in una schermata** (non nell'`Application`) va chiuso con `close()` quando la schermata se ne va,
  come fa la galleria con un blocco per scena.
- **Il permesso `USE_BIOMETRIC` serve.** Lo dichiara il modulo e arriva da solo nel manifesto dell'app: un'app che lo
  togliesse (`tools:node="remove"`) avrebbe il dialogo che non parte e nessuna impronta.
- **Una `Activity` che non è `ComponentActivity`** non ha il codice del telefono su Android 8–10 (serve il registro dei
  risultati): resta il PIN dell'app.
- **Il dialogo non si cambia**: titolo e sottotitolo sì (`FluidSecurityLabels.promptTitle`), l'aspetto no.

## Prove

- `engine-security/src/test` (JVM): `FluidAppLockTest` (quando si blocca, «dopo 5 minuti», l'uscita permessa che vale
  una volta, quella non usata, quella scaduta, la cassaforte richiusa, il PIN come impronta con l'attesa di iPhone, le
  parole dei tempi); `FluidAppLockR2Test` (il PIN dopo un riavvio e con l'ora spostata, il PBKDF2 fuori dal filo di chi
  chiama, le impostazioni illeggibili per un momento e col Keystore azzerato).
- `sample/src/testDebug/.../novita/F8p23LockR2Test.kt` (Robolectric): Tab e D-pad sotto il blocco, il blocco schermo
  messo mentre l'app è bloccata, il sensore bloccato, la riga che dice i salvataggi rifiutati, rilegge il blocco schermo
  e chiede chi sei prima di spegnere o di cambiare il PIN, il dialogo della cassaforte sul thread principale.
- `sample/src/testDebug/.../novita/F8p23LockUiTest.kt` (Robolectric): il vetro che copre e TalkBack che non legge il
  contenuto, il dialogo che parte da solo, il tasto che lo riapre, il rientro (sì), la condivisione e la rotazione (no),
  `FLAG_SECURE` su Android 12, il PIN con l'errore, il PIN subito senza impronta né codice, «Riduci movimento», la
  dissolvenza, nessun ridisegno da ferma, il contenuto protetto che si richiude da solo, le cinque scene.

## Dove guardare

`engine-security/src/main/kotlin/dev/antigravity/fluidengine/security/`: `FluidAppLock.kt` (il blocco e il conto delle
attività), `FluidAppLockRules.kt` (i tempi e i ritardi del PIN), `FluidAppLockHost.kt` (la schermata, i lanciatori),
`FluidAppLockSettings.kt` (la riga), `FluidLockedContent.kt`, `FluidSystemAuthenticator.kt` (il dialogo di sistema, le
capacità, il PIN), `FluidSecurityLabels.kt`.

Installazione: `engine-install.ps1 -AppRoot . -Modules engine-security` (opt-in; porta `engine-ui`, `engine-vault` e
`engine-foundation`). Nel `build.gradle` dell'app: `implementation project(':engine-security')`. Il modulo porta nel
manifesto dell'app `USE_BIOMETRIC` (e `USE_FINGERPRINT` fino ad Android 9): permessi normali, concessi
all'installazione senza dialogo, che `BiometricPrompt` e `BiometricManager.canAuthenticate` vogliono. Senza, il sistema
lancia una `SecurityException` alla prima domanda (visto sul Tab S9 il 25/11/2026: Robolectric non lo controlla, e lo
prova `F8p23VaultAndroidTest` leggendo il manifesto unito); `FluidBiometrics.capability` la legge come «niente
impronta».
