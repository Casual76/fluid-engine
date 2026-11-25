# engine-vault

**La cassaforte** dell'engine (4.0, F8.23, idea `cassaforte-cifrata`): valori, file e DataStore cifrati con chiavi del
Keystore di Android (nel chip separato, StrongBox, quando il telefono lo ha), divisi in **sezioni** che si aprono col
solo telefono o con l'impronta e si richiudono da sole. Pacchetto `dev.antigravity.fluidengine.vault`. **Opt-in**, senza
interfaccia e senza librerie nuove: il Keystore e `javax.crypto` del telefono, e DataStore che l'engine ha già.

È il mattone della custodia delle chiavi dell'Account Pampa (F10.4): lo Store vi chiude la chiave dei dati, le app le
loro `K_app` e `K_bak` (`artifacts/ecosistema/account/b/chiavi.md` § 3.3).

Catalogo completo delle firme: [`../api/vault.md`](../api/vault.md). L'interfaccia del blocco con l'impronta è in
[`security.md`](security.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| una password, un token, poche chiavi di un'app | `FluidVault.get(context).section().values.putString(…)` |
| un documento, una foto, un referto cifrato | `section.file("referto.pdf").write { out -> … }` / `read { input -> … }` |
| un DataStore intero cifrato (Preferences o tipizzato) | `section.preferencesDataStore("conti")`, `section.dataStore("nome", serializer)` |
| sigillare byte miei (una chiave derivata, una radice privata) legandoli al telefono | `section.seal(bytes, associated)` / `open(box, associated)` |
| …e anche all'impronta | la stessa cosa in una sezione `VaultAccess.Biometric()`, aperta con `unlock(authenticator)` |
| una cartella che si apre con l'impronta e si richiude da sola | `vault.section("note", VaultAccess.Biometric(relockAfter = 5.minutes))` |
| la chiave diretta del Keystore (pochi byte, il formato di `engine-ai`) | `FluidKeystoreKey.android(alias)` |
| la frase per «chiave non più valida» e per gli altri casi | `vault.labels.message(unlock, reserve)` (`VaultLabels`, anche `VaultLabels.English`) |
| provare il mio codice sul computer | `FluidVault.create(dir, MemoryVaultKeystore(), …)` |
| il dialogo dell'impronta | non qui: `FluidSystemAuthenticator` in `engine-security`, o un `VaultAuthenticator` mio |
| `androidx.security-crypto` (`EncryptedSharedPreferences`) | **no**: deprecata da Google nel 2025; questa ne è il posto |

## Le sezioni

Ogni sezione ha **la sua chiave dei dati** (256 bit casuali), chiusa da una chiave del Keystore: è la «busta» del
portachiavi di Tink e delle chiavi di Signal. Aperta, la chiave dei dati sta in memoria; ogni dato ha una chiave derivata
sua (HKDF-SHA256) e porta i suoi dati associati, così una scatola non si apre in un'altra sezione, un file non si apre
con un altro nome e un DataStore non si scambia con un altro.

| accesso | come si apre | quando si chiude | Apple |
|---|---|---|---|
| `VaultAccess.Device()` | da sola, al primo uso | mai (finché l'app vive) | Data Protection «fino al primo sblocco», la classe di serie |
| `VaultAccess.Device(whileUnlocked = true)` | da sola, solo a telefono sbloccato (`setUnlockedDeviceRequired`) | allo schermo spento | `NSFileProtectionComplete` |
| `VaultAccess.Biometric()` | `unlock(authenticator)`: l'impronta o il volto forte, poi la riserva | dopo `relockAfter` senza usarla (5 minuti di serie), allo schermo spento, con `lock()`, al blocco dell'app | Portachiavi con `kSecAccessControlBiometryCurrentSet` |

Una sezione all'impronta è chiusa da una **coppia RSA-2048** del Keystore: la privata chiede l'impronta forte a ogni uso
(`setUserAuthenticationParameters(0, BIOMETRIC_STRONG)`, invalidata da un'impronta nuova), la pubblica chiude senza
chiedere niente. Così la sezione **nasce senza dialogo** (la prima `unlock` la crea vuota e aperta) e dopo un'impronta
aggiunta **la chiave si rifà con un solo dialogo**, quello della riserva.

### La riserva e la chiave che non vale più

La riserva (`VaultReserve`) è la seconda porta, quella che resta quando l'impronta non va:

| riserva | cosa la apre | resta se si aggiunge un'impronta | resta se si toglie il blocco schermo |
|---|---|---|---|
| `DeviceCredential` (di serie) | il codice del telefono: una coppia RSA che lavora 30 s dopo un'autenticazione | sì | **no** |
| `AppSecret` | il PIN dell'app (o una frase), dato con `setReserveSecret` a sezione aperta: PBKDF2, poi chiuso anche da una chiave del Keystore | sì | sì |
| `None` | niente: i dati si rifanno (una password da reinserire) | **no** | no |

Cosa succede, e cosa vede la persona:

- **Un'impronta aggiunta.** `unlock` non mostra il dialogo dell'impronta (la chiave non vale più) e passa subito alla
  riserva, con la frase pronta (`VaultLabels.biometricsChanged`: «Hai cambiato le impronte o il volto del telefono: per
  sicurezza questa sezione si è chiusa. Aprila con il codice del telefono…») nel sottotitolo del dialogo. Aperta, la
  chiave dell'impronta si rifà da sola: `VaultUnlock.Opened(recovered = BiometricsChanged)`, e la volta dopo basta
  l'impronta.
- **Il blocco schermo tolto.** Android butta tutte le chiavi con autenticazione: con `DeviceCredential` la sezione è
  persa (`VaultUnlock.Lost(ScreenLockRemoved)`, `VaultLabels.screenLockRemoved`), con `AppSecret` si apre col PIN.
- **Il Keystore azzerato, o i file di un altro telefono.** `VaultException.Invalidated(KeyLost)`.
- **Persa.** L'app mostra la frase e chiama `reset()`: si riparte da una sezione vuota.

Il motivo si legge dal blocco schermo (`KeyguardManager.isDeviceSecure`): Android non dice perché una chiave è stata
invalidata.

## I byte di F10.4: `seal` e `open`

```kotlin
val custody = FluidVault.get(context).section("custodia", VaultAccess.Device(whileUnlocked = true))
val box = custody.seal(dataKey, associated = "pampa/v1/$accountId/cd/e=$epoch".toByteArray())
// …
val dataKey = custody.open(box, associated = "pampa/v1/$accountId/cd/e=$epoch".toByteArray())
try { … } finally { dataKey.fill(0) }
```

- La scatola è `"FV" 1 1 ‖ nonce(12) ‖ testo cifrato ‖ tag(16)`, AES-256-GCM: si apre **solo su questo telefono**, con la
  sezione aperta, e solo con gli stessi dati associati (regola K4 dell'account: di chi è e dove sta).
- Una sezione `Biometric` lega i byte anche all'impronta.
- La copia di recupero separata (la passkey col PRF, il codice di recupero) **resta all'account**: la cassaforte non
  esce dal telefono.

## La chiave diretta: `FluidKeystoreKey`

Una chiave AES-256-GCM del Keystore usata a ogni operazione, senza busta: la chiave non è mai nella memoria dell'app, ma
ogni cifratura costa un viaggio al Keystore (millisecondi nel TEE, decine con StrongBox). Il formato è quello di
`KeystoreCipher` di `engine-ai`: **nonce(12) ‖ testo cifrato ‖ tag(16)**.

**`engine-ai` passa da qui dalla 4.0, con lo stesso alias** (`fluidengine.ai`, o quello dell'app come `fluidweather.ai`):
le chiavi dei provider già salvate si riaprono, e una versione di prima rilegge quelle nuove (provato con il
`KeystoreCipher` della 3.x copiato nei test). Una chiave che manca nasce ora in StrongBox quando c'è.

## Le regole che non si vedono

- **Niente va in backup.** Tutto sta in `noBackupFilesDir/fluid-vault/<nome>`, che Android non copia né nel cloud né nel
  passaggio fra telefoni. Le chiavi del Keystore non si copiano comunque: un backup che porta via dei dati li decifra e
  li ricifra con la sua chiave (quella dell'account, `K_bak`).
- **Fuori dal thread principale.** Ogni lettura e scrittura è `suspend` e lavora su `Dispatchers.IO` (`FluidVault.create`
  ne accetta un altro); `FluidKeystoreKey` è bloccante e va chiamata fuori dal thread principale.
- **Tutto o niente.** La scheda della sezione, i valori, i file si scrivono accanto e poi si scambiano.
- **I file a pezzi.** `VaultFile` cifra a pezzi da 64 KiB (lo «streaming AEAD» di Tink, schema STREAM): un pezzo
  toccato, tolto, spostato o un file rinominato a mano danno `VaultCorruptStreamException` alla lettura.
- **Un DataStore solo nelle sezioni «solo telefono».** Un DataStore tiene i dati letti in memoria finché vive: in una
  sezione all'impronta usa `values` o i file, che dimenticano tutto quando la sezione si richiude.
- **StrongBox quando c'è.** Si chiede sempre (`VaultKeySpec.strongBox`); senza chip la chiave nasce nel TEE.

## Il dialogo: `VaultAuthenticator`

La cassaforte non mostra niente: chiede a un `VaultAuthenticator` con un `VaultAuthRequest` (l'impronta col `Cipher` da
dare a `BiometricPrompt.CryptoObject`, o il codice del telefono) e riceve un `VaultAuthResult`. `engine-security` ne ha uno
pronto, `FluidSystemAuthenticator`, col `BiometricPrompt` di sistema. Un'app può scriverne uno suo.

## Trappole

- **Un `VaultAuthenticator` che dice «sì» senza il dialogo vero non apre niente**: il Keystore vuole il `CryptoObject`
  passato a `BiometricPrompt` e sbloccato da lui. Il risultato è `VaultUnlock.Failed`.
- **Una sezione nasce con un accesso e non lo cambia**: chiederla con un altro è un `IllegalStateException`.
- **Una sezione `Biometric` vuole un blocco schermo**: senza, `VaultUnlock.Unavailable(NoScreenLock)` con la sua frase.
- **Il segreto dell'app (`AppSecret`) non ha un limite di tentativi suo**: lo mette chi lo chiede (il PIN di
  `engine-security` fa aspettare come iPhone).
- **`MemoryVaultKeystore` è per i test**: tiene le chiavi in chiaro nella memoria.

## Prove

- `engine-vault/src/test` (JVM): `VaultCryptoTest` (i vettori di HKDF della RFC 5869, AES-GCM, PBKDF2, i record),
  `FluidVaultTest` (valori che restano, scatole e dati associati, la richiusura a tempo che si sposta con l'uso, lo
  schermo spento, il dialogo finto che non basta, l'impronta aggiunta con la riserva e la chiave rifatta, nessuna
  riserva, il blocco schermo tolto, il PIN dell'app, nessuna impronta registrata, la chiave diretta),
  `VaultFilesAndStoresTest` (file a pezzi fuori dal thread di chi chiama, pezzi toccati, tolti, scambiati, rinominati,
  Preferences e DataStore tipizzato cifrati).
- `sample/src/testDebug/.../novita/F8p23VaultAndroidTest.kt` (Robolectric, con un fornitore «AndroidKeyStore» finto che
  legge il `KeyGenParameterSpec` vero): le chiavi della 3.x riaperte con lo stesso alias, `AiKeyStore` che le rilegge dal
  suo DataStore, StrongBox, i parametri delle chiavi, la sezione all'impronta col Keystore di Android.

## Dove guardare

`engine-vault/src/main/kotlin/dev/antigravity/fluidengine/vault/`: `FluidVault.kt` (la cassaforte, gli accessi, il
dialogo), `VaultSection.kt` (la busta, l'apertura, la riserva, la richiusura), `VaultValues.kt`, `VaultFile.kt`,
`VaultDataStore.kt`, `FluidKeystoreKey.kt`, `VaultKeystore.kt` (il contratto col Keystore), `AndroidVaultKeystore.kt`,
`MemoryVaultKeystore.kt`, `VaultCrypto.kt`, `VaultLabels.kt`.

Installazione: `engine-install.ps1 -AppRoot . -Modules engine-vault` (opt-in; porta `engine-foundation`; entra comunque
con `engine-ai`). Nel `build.gradle` dell'app: `implementation project(':engine-vault')`. Nessun permesso.
