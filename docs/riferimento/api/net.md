<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/net.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-net

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-net/net/EngineHttp.kt`

- `open class EngineHttp(private val userAgent: String = DefaultUserAgent, private val connectTimeoutMillis: Int = 15_000, private val readTimeoutMillis: Int = 30_000)` — The two HTTP calls the engine itself makes: read a small document, and download a large file.
  - `open suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String`
  - `open suspend fun download(url: String, target: File, expectedBytes: Long = 0L, headers: Map<String, String> = emptyMap(), onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit = { _, _, _ -> }): File` — Downloads to `<target>.part` and renames on success.
  - `companion object`
    - `const val DefaultUserAgent: String`

## `engine-net/net/EngineNetwork.kt`

- `data class EngineNetworkState(val connected: Boolean, val validated: Boolean, val metered: Boolean)` — Lo stato della rete come serve a un'app (F8.1, `stati-pronti`; lo riusa `stato-rete-e-offline`, F8.20).
  - `val online: Boolean get()` — Si può usare la rete: c'è, e porta a internet.
  - `companion object`
    - `val Offline: EngineNetworkState` — Nessuna rete.
    - `fun of(hasInternet: Boolean, validated: Boolean, notMetered: Boolean): EngineNetworkState` — Dalle capacità della rete predefinita: la regola, pura, che le prove provano sul computer.
- `object EngineNetwork` — L'osservatore della rete dell'engine, **senza interfaccia**: lo stato di adesso ([current]) e quello che cambia ([states]). Guarda la rete predefinita del telefono (`registerDefaultNetworkCallback`), quella che le app usano davvero. Serve il permesso `ACCESS_NETWORK_STATE`, che il manifesto di `engine-net` dichiara già (è innocuo: non lo vede nessuno).
  - `fun current(context: Context): EngineNetworkState` — Lo stato della rete predefinita adesso.
  - `fun states(context: Context): Flow<EngineNetworkState>` — Lo stato della rete predefinita, ogni volta che cambia: parte dallo stato di adesso, non ripete uno stato uguale e si stacca da sola quando chi raccoglie smette.
