<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/net.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-net

_Generato sull'engine 3.0.7 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-net/net/EngineHttp.kt`

- `open class EngineHttp(private val userAgent: String = DefaultUserAgent, private val connectTimeoutMillis: Int = 15_000, private val readTimeoutMillis: Int = 30_000)` — The two HTTP calls the engine itself makes: read a small document, and download a large file.
  - `open suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String`
  - `open suspend fun download(url: String, target: File, expectedBytes: Long = 0L, headers: Map<String, String> = emptyMap(), onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit = { _, _, _ -> }): File` — Downloads to `<target>.part` and renames on success.
  - `companion object`
    - `const val DefaultUserAgent: String`
