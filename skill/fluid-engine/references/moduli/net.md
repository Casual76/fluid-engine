# engine-net

**Le due chiamate HTTP che l'engine fa da solo**: leggere un documento piccolo (`EngineHttp.readText`, il manifest) e scaricare un file grande
(`EngineHttp.download`, l'APK dell'aggiornamento). Una sola classe, `EngineHttp`, nel pacchetto `dev.antigravity.fluidengine.net`.

E' costruita su `HttpURLConnection` e **non** su una libreria client, di proposito: queste chiamate girano prima che lo stack di rete dell'app sia
necessariamente pronto (il controllo aggiornamenti parte a freddo, il fetch della config deve funzionare in un'app che non parla con nient'altro), e
un engine che impone un client HTTP al suo ospite e' un engine che perde la discussione con quello dell'ospite. Modello mentale: **un tubo con due
rubinetti, non un client HTTP generico**. Chi ha bisogno di POST, JSON tipizzato, autenticazione, retry o cache usa il proprio stack.

Usato da [config](config.md) (`readText`) e [update](update.md) (`readText` per il manifest, `download` per l'APK). Catalogo: [`../api/net.md`](../api/net.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| scaricare il testo di un manifest/JSON piccolo | `EngineHttp.readText(url)` |
| scaricare un file grande con avanzamento e **senza lasciare mezzi file** | `EngineHttp.download(url, target, expectedBytes, onProgress = ...)` |
| provare in un test la logica sopra la rete (manifest, «e' piu' nuova?») | una sottoclasse di `EngineHttp` che ridefinisce `readText` — la classe e' `open` per questo |
| dare all'engine il nome della mia app nello `User-Agent` | `EngineHttp(userAgent = "MiaApp/1.2.3")` |
| chiamate dell'app (API, POST, auth, retry, cache, certificati) | il **tuo** client (OkHttp, Ktor…): `EngineHttp` non e' fatto per questo |
| un solo `EngineHttp` per config e aggiornamenti | lo stesso oggetto passato a `EngineRemoteConfig` e a `EngineAppUpdater`/`AndroidAppUpdateInstaller` |

## `EngineHttp`

**Cos'e'.** Un wrapper sottile su `HttpURLConnection` con due metodi `suspend` (eseguiti su `Dispatchers.IO`) e i timeout giusti. `open` perche' tutto cio' che
sta **sopra** la rete — decidere se una release e' piu' nuova, leggere un manifest, risolvere un URL di download — e' logica ordinaria che merita test ordinari, e
una classe `final` costringerebbe ogni app ospite a toccare la rete per provarla (CHANGELOG 1.1.0).

**Firma essenziale.**

```kotlin
open class EngineHttp(
  private val userAgent: String = DefaultUserAgent,          // "FluidEngine/1.0"
  private val connectTimeoutMillis: Int = 15_000,
  private val readTimeoutMillis: Int = 30_000,
) {
  open suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String
  open suspend fun download(
    url: String, target: File, expectedBytes: Long = 0L,
    headers: Map<String, String> = emptyMap(),
    onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit = { _, _, _ -> },
  ): File
  companion object { const val DefaultUserAgent: String }
}
```

**Uso.**

```kotlin
val http = EngineHttp(userAgent = "MiaApp/${BuildConfig.VERSION_NAME}")
val raw = http.readText("https://raw.githubusercontent.com/OWNER/REPO/main/manifest.json")

// Un finto per i test: niente rete, la logica sopra resta vera.
class FakeHttp(private val body: String) : EngineHttp() {
  override suspend fun readText(url: String, headers: Map<String, String>): String = body
}
```

**Comportamento — `readText`.**
- Aggiunge `Accept: application/json` **dopo** i tuoi header: se ne passi uno `Accept` tuo, vince quello dell'engine. Imposta anche `User-Agent`.
- Legge **tutto il corpo in memoria** come `String` (UTF-8). Per un manifest va bene; non e' fatto per file grandi.
- **Qualunque codice fuori da 200–299 lancia** `IllegalStateException("Richiesta non riuscita (<codice>).")`; il corpo dell'errore viene letto e **scartato**.
  Gli errori di rete (`UnknownHostException`, `SocketTimeoutException`, …) risalgono cosi' come sono. Nessun retry, nessuna cache HTTP, nessun ETag.
  I chiamanti dell'engine lo mettono in un `Result` (`runCatching`).
- Segue i redirect con il meccanismo di `HttpURLConnection` (`instanceFollowRedirects = true`), che **rifiuta quelli che cambiano protocollo**
  (`http` → `https`): un URL `http://` che reindirizza a `https://` finisce in «Richiesta non riuscita (301)». Usa direttamente `https://`.

**Comportamento — `download`.**
- **Scrive in `<nome>.part` e rinomina alla fine.** Un file mezzo scritto col nome definitivo e' indistinguibile da uno completo, e il giro dopo qualcuno proverebbe a
  installarlo. Il `.part` precedente viene cancellato prima di cominciare, e rimosso nel `finally` se qualcosa va storto. La cartella di destinazione viene creata
  (`mkdirs`); un `target` gia' esistente viene sostituito solo a download completato.
- **Redirect a mano, fino a 5.** `HttpURLConnection` non segue un redirect che cambia protocollo, ed e' esattamente cio' che fa un host di release quando passa
  alla sua CDN (GitHub Releases → storage): seguirli a mano non e' opzionale. Alla sesta redirezione, o con un `3xx` senza `Location`, lancia «Download non riuscito (<codice>).».
  Gli stessi `headers` sono mandati a **ogni** salto (anche a un host diverso: non mettere un token qui).
- **Verifica la dimensione.** Se `expectedBytes > 0` e i byte scaricati sono diversi lancia «File incompleto: N byte invece di M.». Un download troncato **non solleva
  niente da solo** (la connessione si chiude e il file resta a meta'): senza questo controllo l'APK dimezzato arriverebbe all'installer, che lo rifiuta con un errore che non spiega niente.
  Il confronto e' con `expectedBytes` (il `sizeBytes` del manifest), **non** con il `Content-Length` della risposta.
- `total` per `onProgress` e' `Content-Length` se positivo, altrimenti `expectedBytes`; se nessuno dei due e' noto il `progress` resta `0f`.
  Il callback e' chiamato **a ogni lettura da 8 KiB**, **sul thread IO** (non sul principale) e con `progress` limitato a `0f..1f`: un APK di 3,3 MB sono circa 400 chiamate.
- Timeout: connessione `connectTimeoutMillis` (15 s); lettura **fissa a 60 s** nel download (non usa `readTimeoutMillis`, che vale solo per `readText`).

**Trappole.**
- **`expectedBytes` sbagliato blocca il download.** Con un `sizeBytes` del manifest diverso dal peso vero (un APK ricostruito senza aggiornare il manifest) il file viene scaricato per intero
  e poi rifiutato come incompleto. E' una cosa buona (meglio che installare l'APK sbagliato), ma e' anche l'errore piu' tipico di una pubblicazione fatta a mano.
- **Annullare il coroutine non interrompe un download in corso.** Nel ciclo di lettura non c'e' nessun controllo di cancellazione (`ensureActive`): il blocco e' I/O bloccante dentro `withContext`,
  quindi la lettura prosegue fino alla fine e l'eccezione di cancellazione arriva solo dopo. Per fermarlo davvero serve un `EngineHttp` tuo (sottoclasse) che controlli l'attivita'.
- **Senza `expectedBytes` un troncamento passa inosservato.** Passa sempre il peso quando lo conosci (`AvailableAppUpdate.sizeBytes`).
- I messaggi d'errore sono **in italiano** e fissi: un'app in un'altra lingua li vedra' cosi' se li mostra tali e quali.
- Non e' uno stack di rete: niente POST, niente corpo di richiesta, niente cookie, niente configurazione TLS, niente cache.

**Non usarlo quando.** Servono richieste dell'app (usa il tuo client) o un download che deve poter essere annullato/ripreso (scrivine uno tuo, o usa `DownloadManager`).

**Versione.** Dalla 1.0; la verifica della dimensione attesa e `open` dalla 1.1.0.

## Token, costanti, Defaults

| nome | valore |
|---|---|
| `EngineHttp.DefaultUserAgent` | `"FluidEngine/1.0"` (non e' la versione dell'engine: e' una stringa fissa) |
| timeout di connessione | 15 000 ms (parametro) |
| timeout di lettura di `readText` | 30 000 ms (parametro) |
| timeout di lettura di `download` | 60 000 ms (**fisso**) |
| redirect massimi in `download` | 5 (privato) |

## Cosa e' verificato dai test

**Nessun test in `engine-net`** (la cartella `src/test` non esiste): `EngineHttp` non e' coperto, ne' dai test di `engine-update` (che provano `rejectApk` e il checksum senza rete).
La classe e' `open` proprio per poter fingere la rete altrove. Cio' che e' scritto sopra viene dal sorgente.

## Dove guardare

- `engine-net/src/main/kotlin/dev/antigravity/fluidengine/net/EngineHttp.kt` — `readText`, `download`, i redirect a mano (`openFollowingRedirects`), `DefaultUserAgent`.
- `engine-net/src/main/AndroidManifest.xml` — i permessi che il modulo porta nell'app.

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api project(':engine-foundation')`. Nessuna libreria di rete. Il manifest del modulo dichiara `INTERNET` e `ACCESS_NETWORK_STATE`, che si fondono
nel manifest dell'app (`ACCESS_NETWORK_STATE` non e' usato da `EngineHttp`, che non controlla lo stato della rete: un telefono offline fallisce con la sua eccezione).
Cosi' un'app che usa solo `engine-config` ha gia' `INTERNET` senza dichiararlo.

**Aggancio.** `tools/engine-install.ps1 -AppRoot . -Modules engine-net` (porta `engine-foundation`; `engine-config` e `engine-update` lo portano da soli). Nel `build.gradle`:

```groovy
implementation project(':engine-net')    // quasi sempre arriva gia' con engine-config o engine-update
```

**Configurazione minima.** Un'istanza condivisa con lo `User-Agent` dell'app (e' in `templates/EngineModule.kt.txt`):

```kotlin
@Provides @Singleton
fun engineHttp(): EngineHttp = EngineHttp(userAgent = "MiaApp/${BuildConfig.VERSION_NAME}")
```
