<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/security.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-security

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-security/security/FluidAppLock.kt`

- `sealed interface FluidAppLockState` — Lo stato del blocco dell'app.
  - `data object Loading : FluidAppLockState` — Le impostazioni non sono ancora lette: l'app non si mostra (un attimo, all'avvio; o finché un errore passeggero della cassaforte non passa, e intanto si riprova).
  - `data object Unlocked : FluidAppLockState` — Aperta.
  - `data object Locked : FluidAppLockState` — Bloccata: si vede la schermata di blocco, il contenuto non si disegna e TalkBack non lo legge.
- `data class FluidAppLockSettings(val enabled: Boolean = false, val timeout: Duration = Duration.ZERO, val hasPin: Boolean = false, val pinLength: Int = FluidAppLockDefaults.PinLength)` — Le impostazioni del blocco, quelle della riga pronta ([FluidAppLockSettingsGroup]).
- `sealed interface FluidPinCheck` — Com'è andato un PIN dell'app.
  - `data object Correct : FluidPinCheck` — Giusto: l'app si apre.
  - `data class Wrong(val failures: Int, val waitUntil: Long?) : FluidPinCheck` — Sbagliato, per la [failures]-esima volta di fila; [waitUntil] non nullo: prima di riprovare si aspetta.
  - `data class Wait(val until: Long) : FluidPinCheck` — Troppi tentativi: si riprova da [until] (in `SystemClock.elapsedRealtime`).
  - `data object NoPin : FluidPinCheck` — Non c'è un PIN dell'app.
- `class FluidAppLock internal constructor(private val section: VaultSection, private val vault: FluidVault?, private val clock: () -> Long, private val scope: CoroutineScope, private val pinIterations: Int, private val lockVaultOnLock: Boolean, private val wallClock: () -> Long = System::currentTimeMillis)` — **Il blocco dell'app** (F8.23, `blocco-biometrico`): l'app si blocca quando ci torni dopo il tempo scelto, e si sblocca con l'impronta, il volto o il codice del telefono, o con il PIN dell'app come riserva. È «Richiedi Face ID» di iOS 18: l'interfaccia è [FluidAppLockHost] (la schermata di vetro, il dialogo di sistema, l'anteprima oscurata nelle recenti), le impostazioni sono la riga pronta [FluidAppLockSettingsGroup…
  - `val state: StateFlow<FluidAppLockState>` — Bloccata, aperta, o ancora da leggere.
  - `val settings: StateFlow<FluidAppLockSettings>` — Le impostazioni di adesso.
  - `var lockCount: Int` — Quante volte l'app si è bloccata (per i test e per far ripartire il dialogo automatico).
  - `fun canEnable(context: Context): Boolean` — Vero se il blocco si può accendere: c'è un blocco schermo sicuro o un PIN dell'app.
  - `suspend fun setEnabled(enabled: Boolean)` — Accende o spegne il blocco. Acceso, l'app resta aperta adesso: si blocca al prossimo rientro.
  - `suspend fun setTimeout(timeout: Duration)` — Dopo quanto fuori, al rientro, l'app si blocca.
  - `suspend fun setPin(pin: CharArray?)` — Imposta il PIN dell'app ([pin], solo cifre, 4–8), o lo toglie (null). Si tiene solo la sua impronta (PBKDF2 con un sale, calcolato su `Dispatchers.Default`), mai il PIN. Chi lo passa lo azzera dopo.
  - `suspend fun checkPin(pin: CharArray): FluidPinCheck` — Controlla il PIN dell'app. Giusto: l'app si apre. Sbagliato: conta, e dal quinto errore di fila fa aspettare come iPhone ([FluidAppLockDefaults.pinDelay]), anche dopo un riavvio. Il PBKDF2 gira su `Dispatchers.Default`. Chi passa [pin] lo azzera dopo.
  - `fun lock()` — Blocca subito (un tasto «Blocca ora», o lo schermo che si spegne con il tempo «Subito»).
  - `fun unlock()` — Apre l'app: dopo un'autenticazione riuscita (il dialogo di sistema, il PIN).
  - `fun allowLeaving(within: Duration = FluidAppLockDefaults.LeaveAllowance)` — La prossima uscita dall'app non la blocca: si chiama prima di aprire una condivisione, una scelta di file, la fotocamera di sistema. Il permesso vale per un rientro solo e al più per [within].
  - `fun cancelLeaving()` — Toglie il permesso di [allowLeaving] prima del tempo (l'uscita non c'è stata).
  - `fun close()` — Stacca il blocco dal conto delle attività: per un blocco che vive meno dell'app (una scena della galleria).

## `engine-security/security/FluidAppLockHost.kt`

- `fun interface FluidLockAuthenticator` — Chi chiede l'impronta o il codice per aprire l'app: di serie il dialogo di sistema ([FluidSystemAuthenticator]); un test ne passa uno finto.
  - `suspend fun authenticateUser(title: String, subtitle: String?, fallback: String?): VaultAuthResult` — Mostra il dialogo e aspetta l'esito. [fallback]: il tasto in basso della riserva («Usa il PIN dell'app»).
- `val LocalFluidAppLock: ProvidableCompositionLocal<FluidAppLock?>` — Il blocco dell'app di questo albero, per i lanciatori che non devono bloccare ([rememberFluidLockSafeLauncher]).
- `object FluidAppLockTags` — I tag dei test.
  - `const val Screen`
  - `const val Unlock`
  - `const val UsePin`
  - `const val PinField`
  - `const val Message`
  - `const val Content`
- `object FluidAppLockMotion` — I numeri di movimento della schermata di blocco. **A occhio**, 24/11/2026: Apple non pubblica come si apre un'app bloccata; questo è quello che si vede in iOS 18–27, la schermata che si dissolve e l'app che entra a fuoco.
  - `const val RevealMs: Int` — Quanto dura l'uscita della schermata di blocco: 320 ms.
  - `const val RevealBlurDp: Float` — La sfocatura dell'app che entra, al primo fotogramma: 18 dp, a zero alla fine.
  - `const val AutoPromptDelayMs: Long` — Il dialogo automatico parte quando la schermata è già lì: 250 ms dopo.
- `@Composable fun rememberFluidSystemAuthenticator(labels: FluidSecurityLabels = LocalFluidSecurityLabels.current): FluidSystemAuthenticator?` — Il dialogo di sistema dell'attività di questo albero, o null se non c'è un'attività.
- `fun FluidSystemAuthenticator.asLockAuthenticator(): FluidLockAuthenticator` — Il dialogo di sistema come [FluidLockAuthenticator].
- `@Composable fun FluidAppLockHost(lock: FluidAppLock, modifier: Modifier = Modifier, appName: String = rememberAppName(), authenticator: FluidLockAuthenticator? = rememberFluidSystemAuthenticator()?.asLockAuthe…, capability: FluidAuthCapability = rememberFluidAuthCapability(lock), hidePreview: Boolean = true, labels: FluidSecurityLabels = LocalFluidSecurityLabels.current, backdrop: (@Composable BoxScope.() -> Unit)? = null, content: @Composable () -> Unit)` — **L'app con il blocco** (F8.23): [content] è l'app, e quando [lock] è bloccato sopra c'è la schermata di blocco di vetro, col nome e l'icona dell'app, il tasto «Sblocca» (il dialogo di sistema parte anche da solo, come Face ID) e «Usa il PIN dell'app» se c'è il PIN.
- `@Composable fun rememberFluidAuthCapability(lock: FluidAppLock): FluidAuthCapability` — Le capacità del telefono, rilette a ogni blocco e a ogni ritorno in primo piano: un'impronta o un blocco schermo messi nelle impostazioni del telefono mentre l'app è bloccata contano al rientro (R2).
- `@Composable fun rememberAppName(): String` — Il nome dell'app, dal suo manifest.
- `@Composable fun FluidLockBackdrop(modifier: Modifier = Modifier)` — Lo sfondo di serie della schermata di blocco: una sfumatura ferma nei colori del tema, con due macchie di luce che il vetro rifrange. Si disegna una volta: niente che si muova da solo.
- `class FluidLockSafeLauncher<I> internal constructor(private val launcher: ManagedActivityResultLauncher<I, *>, private val lock: () -> FluidAppLock?)` — Un lanciatore che, prima di aprire l'altra app, dice al blocco di non bloccare al ritorno.
  - `fun launch(input: I)` — Apre l'altra app (la scelta di file, la fotocamera): il ritorno non blocca.
- `@Composable fun <I, O> rememberFluidLockSafeLauncher(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): FluidLockSafeLauncher<I>` — `rememberLauncherForActivityResult` che non fa bloccare l'app al ritorno: per la scelta di un file, di una foto, la fotocamera di sistema. Il blocco è quello di [LocalFluidAppLock] (lo dà [FluidAppLockHost]).
- `fun FluidAppLock?.startExternal(context: Context, intent: Intent)` — Apre [intent] (una condivisione: `Intent.createChooser`) senza far bloccare l'app al ritorno.

## `engine-security/security/FluidAppLockRules.kt`

- `object FluidAppLockDefaults` — I numeri del blocco dell'app (F8.23).
  - `val Timeouts: List<Duration>` — Le scelte di «Blocca»: subito, dopo 1, 5 o 15 minuti, dopo un'ora. «Subito» è il comportamento di «Richiedi Face ID» di iOS 18+ (l'app «si blocca di nuovo da sola quando la chiudi», support.apple.com, «Lock or hide an app on iPhone», iOS 27, letto il 24/11/2026, fiducia alta); gli altri sono quelli di «Richiedi la password» di macOS e del codice di iPhone senza Face ID (a memoria, fiducia media).
  - `const val PinLength: Int` — Le cifre del PIN dell'app: 6, il codice di serie di iPhone dal 2015 (iOS 9).
  - `val PinLengths: List<Int>` — Le lunghezze del PIN che si possono scegliere: 4 e 6, come «Opzioni codice» di iOS.
  - `val LeaveAllowance: Duration` — Per quanto vale il permesso di uscire senza blocco ([FluidAppLock.allowLeaving]): 10 minuti, il tempo di scegliere un file o una persona a cui mandare qualcosa. **A occhio**, 24/11/2026.
  - `const val PinIterations: Int` — I giri di PBKDF2-HMAC-SHA256 del PIN dell'app: come la cassaforte (`VaultDefaults.SecretIterations`).
  - `fun pinDelay(failures: Int): Duration` — Quanto aspettare dopo [failures] PIN sbagliati di fila: niente fino a 4, poi 1 minuto, 5, 15 (al settimo e all'ottavo) e un'ora. È la tabella dei ritardi del codice di iPhone (Apple Platform Security, «Passcodes and passwords», a memoria, fiducia media).

## `engine-security/security/FluidAppLockSettings.kt`

- `object FluidAppLockSettingsTags` — I tag dei test delle impostazioni del blocco.
  - `const val Toggle`
  - `const val Timeout`
  - `const val Pin`
  - `const val PinSetup`
  - `const val PinCheck` — Il PIN di adesso, chiesto prima di spegnere il blocco o di cambiare il PIN (R2).
  - `const val Message` — La riga che dice un salvataggio rifiutato dalla cassaforte (R2).
- `@Composable fun FluidAppLockSettingsGroup(lock: FluidAppLock, modifier: Modifier = Modifier, appName: String = rememberAppName(), timeouts: List<Duration> = FluidAppLockDefaults.Timeouts, pinLength: Int = FluidAppLockDefaults.PinLength, labels: FluidSecurityLabels = LocalFluidSecurityLabels.current, authenticator: FluidLockAuthenticator? = rememberFluidSystemAuthenticator(labels)?.asLoc…)` — **La riga pronta delle impostazioni** del blocco (F8.23): un gruppo con l'interruttore «Richiedi l'impronta», la scelta «Blocca» (subito, dopo 1, 5, 15 minuti, dopo un'ora) e il «PIN dell'app» (sceglierlo due volte, cambiarlo, toglierlo), scritto lì nel gruppo con il campo a codice di F8.3. Senza blocco schermo né PIN l'interruttore è spento e dice perché.

## `engine-security/security/FluidLockedContent.kt`

- `object FluidLockedContentTags` — I tag dei test del contenuto protetto.
  - `const val Placeholder`
  - `const val View`
  - `const val Message`
- `@Composable fun FluidLockedContent(unlocked: Boolean, title: String, onView: () -> Unit, modifier: Modifier = Modifier, message: String? = null, labels: FluidSecurityLabels = LocalFluidSecurityLabels.current, content: @Composable () -> Unit)` — **Il contenuto protetto** (F8.23): una cartella, un album, le note, che restano chiusi finché non li apri. Chiuso, al posto di [content] c'è il segno di iOS (il lucchetto, «Album nascosto è bloccato», «Visualizza»), e il contenuto non si compone: non si disegna, non lo legge TalkBack, non passa in uno screenshot.
- `@Composable fun FluidVaultContent(section: VaultSection, title: String, modifier: Modifier = Modifier, prompt: VaultPrompt = VaultPrompt(title = LocalFluidSecurityLabels.cu…, authenticator: VaultAuthenticator? = rememberFluidSystemAuthenticator(), onNeedsSecret: (() -> Unit)? = null, labels: FluidSecurityLabels = LocalFluidSecurityLabels.current, content: @Composable () -> Unit)` — Il contenuto di una **sezione all'impronta** della cassaforte: chiuso finché la sezione è chiusa, aperto dopo «Visualizza» (il dialogo dell'impronta, poi la riserva), e di nuovo chiuso quando la sezione si richiude da sola (dopo qualche minuto senza usarla, allo schermo spento, al blocco dell'app). Un'impronta aggiunta dà la frase pronta della cassaforte e passa alla riserva;
- `@Composable fun rememberFluidBiometricGate(title: String, authenticator: FluidLockAuthenticator? = rememberFluidSystemAuthenticator()?.asLockAuthe…): FluidPrivateGate?` — Il cancello dei valori riservati (`Modifier.fluidPrivate` di `engine-ui`) con il dialogo di sistema: il valore si scopre solo dopo l'impronta, il volto o il codice del telefono. Da mettere in `LocalFluidPrivateGate`.

## `engine-security/security/FluidSecurityLabels.kt`

- `data class FluidSecurityLabels(val lockedTitle: String = "%1\$s è bloccata", val lockedSubtitleBiometric: String = "Usa l'impronta, il volto o il codice del telefono per aprirla.", val lockedSubtitleCredential: String = "Usa il codice del telefono per aprirla.", val lockedSubtitlePin: String = "Scrivi il PIN dell'app per aprirla.", val unlock: String = "Sblocca", val usePin: String = "Usa il PIN dell'app", val promptTitle: String = "Sblocca %1\$s", val pinTitle: String = "Scrivi il PIN dell'app", val pinWrong: String = "PIN sbagliato. Riprova.", val pinWait: String = "Troppi tentativi. Riprova fra %1\$s.", val waitOneMinute: String = "1 minuto", val waitMinutes: String = "%1\$d minuti", val waitOneHour: String = "1 ora", val waitHours: String = "%1\$d ore", val back: String = "Indietro", val cancel: String = "Annulla", val authenticationFailed: String = "Non è stato possibile verificare che sei tu. R…, val nothingToUnlockWith: String = "Imposta un blocco schermo nelle impostazioni d…, o un PIN dell'app.", val lockScreenDescription: String = "Schermata di blocco", val settingTitle: String = "Richiedi l'impronta", val settingSubtitle: String = "Quando torni in %1\$s chiede l'impronta, il volto o il codice del telefono.", val settingUnavailable: String = "Per usarlo imposta un blocco schermo nelle imp…, o un PIN dell'app.", val settingTimeout: String = "Blocca", val timeoutImmediately: String = "Subito", val timeoutAfterMinutes: String = "Dopo %1\$d min", val timeoutAfterHours: String = "Dopo %1\$d h", val settingPin: String = "PIN dell'app", val settingPinSubtitle: String = "La riserva quando l'impronta e il codice del t…, val pinOn: String = "Attivo", val pinOff: String = "Spento", val pinChooseTitle: String = "Scegli un PIN dell'app", val pinConfirmTitle: String = "Scrivilo di nuovo", val pinMismatch: String = "I due PIN non sono uguali. Ricomincia.", val pinRemove: String = "Togli il PIN", val contentLockedTitle: String = "%1\$s è bloccato", val contentLockedSubtitle: String = "Usa l'impronta per vederlo.", val contentView: String = "Visualizza", val contentLock: String = "Blocca", val settingSaveFailed: String = "Non è stato possibile salvare. Riprova.", val pinCurrentTitle: String = "Scrivi il PIN attuale", val confirmTitle: String = "Conferma che sei tu")` — Le parole del blocco: la schermata di vetro, il dialogo, il PIN, la riga delle impostazioni, il contenuto protetto. Italiano di serie, [English] per un'app in inglese. `%1$s` è il nome dell'app o della cosa protetta.
  - `fun timeout(duration: Duration): String` — Il nome di un tempo di blocco: «Subito», «Dopo 5 min», «Dopo 1 h».
  - `fun pinWaitText(millis: Long): String` — «Riprova fra 5 minuti», per un'attesa di [millis].
  - `companion object`
    - `val English: FluidSecurityLabels` — Le parole in inglese.
- `val LocalFluidSecurityLabels: ProvidableCompositionLocal<FluidSecurityLabels>` — Le parole del blocco per questo albero.

## `engine-security/security/FluidSystemAuthenticator.kt`

- `data class FluidAuthCapability(val strongBiometrics: Boolean, val anyBiometrics: Boolean, val deviceCredential: Boolean)` — Che cosa sa fare il telefono per riconoscere la persona.
- `object FluidBiometrics` — Che cosa sa fare il telefono, letto dal sistema: `BiometricManager` da Android 10, l'impronta di `FingerprintManager` su Android 9, niente prima (su Android 8 il dialogo dell'impronta non esiste: restano il codice del telefono e il PIN dell'app).
  - `fun capability(context: Context): FluidAuthCapability` — Le capacità di adesso (cambiano se la persona registra un'impronta). Senza il permesso `USE_BIOMETRIC` nel manifesto (lo dichiara questo modulo; un'app che lo togliesse) il sistema lancia una `SecurityException`: qui vuol dire «niente impronta», mai un'app che si chiude.
- `class FluidSystemAuthenticator(private val activity: Activity, private val labels: FluidSecurityLabels = FluidSecurityLabels()) : VaultAuthenticator` — Il dialogo di sistema per l'impronta, il volto e il codice del telefono, per la cassaforte ([VaultAuthenticator]) e per il blocco dell'app ([authenticateUser]). Il dialogo resta di sistema: non è vetro e non si cambia (scheda dell'idea, «rischi»).
  - `suspend fun authenticateUser(title: String, subtitle: String? = null, fallback: String? = null): VaultAuthResult` — Il dialogo per il blocco dell'app: l'impronta o il volto, anche «deboli», e il codice del telefono, insieme quando il sistema lo permette. [fallback] è il tasto in basso quando il codice del telefono non sta nel dialogo («Usa il PIN dell'app»): toccarlo dà [VaultAuthResult.Fallback].
