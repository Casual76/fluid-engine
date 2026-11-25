# API · engine-net

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-net/net/EngineConditions.kt`

- `object EngineConditions` — Le condizioni del telefono lette da Android (F8.20, `pochi-dati-poca-batteria`): la rete ([EngineNetwork], con la sua tenuta), il risparmio dati, il risparmio energetico, il calore e la corrente, in un [FluidConditions] solo. La regola che le usa sta in `engine-foundation` ([FluidConditions.allows]).
  - `fun current(context: Context): FluidConditions` — Le condizioni adesso (una lettura, senza la tenuta della rete).
  - `fun states(context: Context, graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis): Flow<FluidConditions>` — Le condizioni ogni volta che cambiano. La rete ha la tenuta di [EngineNetwork.states] ([graceMillis]): una micro-caduta non ferma niente.
  - `fun install(context: Context)` — Tiene aggiornato [FluidDeviceConditions] per tutta la vita del processo. Si chiama una volta (nell'`Application`, o da chi ne ha bisogno: `FluidTransfers.open` lo fa da se'); le chiamate dopo la prima non fanno niente. Costa un callback di rete e un ricevitore: niente lavoro finche' non cambia qualcosa.
  - `val isInstalled: Boolean get()` — Vero se [install] sta aggiornando [FluidDeviceConditions].
  - `fun uninstall()` — Smette di aggiornare [FluidDeviceConditions] (per le prove).

## `engine-net/net/EngineHttp.kt`

- `open class EngineHttp(private val userAgent: String = DefaultUserAgent, private val connectTimeoutMillis: Int = 15_000, private val readTimeoutMillis: Int = 30_000)` — The two HTTP calls the engine itself makes: read a small document, and download a large file.
  - `open suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String`
  - `open suspend fun download(url: String, target: File, expectedBytes: Long = 0L, headers: Map<String, String> = emptyMap(), onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit = { _, _, _ -> }): File` — Downloads to `<target>.part` and renames on success.
  - `companion object`
    - `const val DefaultUserAgent: String`

## `engine-net/net/EngineNetwork.kt`

- `data class EngineNetworkState(val connected: Boolean, val validated: Boolean, val metered: Boolean, val vpn: Boolean = false, val roaming: Boolean = false)` — Lo stato della rete come serve a un'app (F8.1, `stati-pronti`; F8.20, `stato-rete-e-offline`).
  - `val online: Boolean get()` — Si può usare la rete: c'è, e porta a internet.
  - `companion object`
    - `val Offline: EngineNetworkState` — Nessuna rete.
    - `fun of(hasInternet: Boolean, validated: Boolean, notMetered: Boolean): EngineNetworkState` — Dalle capacità della rete predefinita: la regola, pura, che le prove provano sul computer.
- `object EngineNetworkDefaults` — I tempi dell'osservatore.
  - `const val OfflineGraceMillis: Long` — Quanto deve durare una caduta prima di diventare «offline»: **2,5 s**, a occhio il 25 novembre 2026 (F8.20).
- `object EngineNetwork` — L'osservatore della rete dell'engine, **senza interfaccia**: lo stato di adesso ([current]) e quello che cambia ([states]). Guarda la rete predefinita del telefono (`registerDefaultNetworkCallback`), quella che le app usano davvero, e le reti sotto una VPN. Serve il permesso `ACCESS_NETWORK_STATE`, che il manifesto di `engine-net` dichiara già (è innocuo: non lo vede nessuno).
  - `fun current(context: Context): EngineNetworkState` — Lo stato della rete predefinita adesso (senza tenuta: è una lettura).
  - `fun states(context: Context, graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis): Flow<EngineNetworkState>` — Lo stato della rete predefinita, ogni volta che cambia: parte dallo stato di adesso, non ripete uno stato uguale e si stacca da sola quando chi raccoglie smette.
  - `fun steady(raw: Flow<EngineNetworkState>, graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis, now: () -> Long = { System.nanoTime() / 1_000_000L }): Flow<EngineNetworkState>` — La tenuta di [states] su un flusso qualsiasi di stati ([raw]): passa subito tutto, tranne il passaggio da online a non online, che aspetta [graceMillis] e si annulla se intanto la rete torna. [now] è l'orologio in millisecondi (quello delle prove, nelle prove).

## `engine-net/net/testing/FakeNetworks.kt`

- `class FakeNetworks(private val graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis, private val now: () -> Long = { System.nanoTime() / 1_000_000L })` — Reti finte per le prove e per la simulazione della galleria (F8.20): un Wi-Fi, i dati mobili, una VPN sopra, un portale di login, una micro-caduta, il cambio da una rete all'altra. **Non tocca il telefono**: la regola e la tenuta sono quelle vere di [EngineNetwork] (Wi-Fi senza internet, VPN, cambio di rete e micro-cadute passano dallo stesso codice dell'osservatore di Android).
  - `val raw: StateFlow<EngineNetworkState>` — Lo stato senza tenuta: ogni cambio com'e'.
  - `val states: Flow<EngineNetworkState> get()` — Lo stato come lo da' [EngineNetwork.states]: con la tenuta contro le micro-cadute.
  - `val current: String? get()` — La rete predefinita adesso: «wifi», «cellular», «vpn», o null.
  - `fun wifi(validated: Boolean = true, metered: Boolean = false, captivePortal: Boolean = false)` — Un Wi-Fi diventa la rete predefinita (con una VPN accesa, va sotto la VPN).
  - `fun cellular(validated: Boolean = true, roaming: Boolean = false, suspended: Boolean = false)` — I dati mobili diventano la rete predefinita (con una VPN accesa, vanno sotto la VPN).
  - `fun vpn(claimsValidated: Boolean = true)` — Una VPN sopra le reti che ci sono. [claimsValidated] e' quello che la VPN dice di se' (Android spesso la segna validata senza provarla): conta la rete sotto.
  - `fun vpnOff()` — Spegne la VPN: torna predefinita la rete sotto (il Wi-Fi se c'e').
  - `fun lose(name: String)` — Perde la rete [name] («wifi», «cellular», «vpn»); se era la predefinita, nessuna lo e' (come `onLost`).
  - `fun disconnect()` — Nessuna rete.
  - `suspend fun microCut(millis: Long = 600L)` — Una micro-caduta: la rete predefinita sparisce per [millis] e torna com'era. Con la tenuta di [states] una caduta piu' corta di [graceMillis] non si vede.
  - `suspend fun handover(gapMillis: Long = 400L, validationMillis: Long = 700L)` — Il passaggio dal Wi-Fi ai dati mobili com'e' su un telefono vero: il Wi-Fi si perde, i dati diventano predefiniti dopo [gapMillis] e sono validati dopo altri [validationMillis].
  - `companion object`
    - `const val Wifi: String`
    - `const val Cellular: String`
    - `const val Vpn: String`
