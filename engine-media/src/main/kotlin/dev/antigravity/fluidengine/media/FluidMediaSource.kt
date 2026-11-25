package dev.antigravity.fluidengine.media

import android.graphics.Bitmap
import androidx.compose.runtime.Stable

/**
 * Una sorgente di foto e video per il selettore (F8.14, la nota di Alessio su `scelta-foto-e-video`: «la lettura
 * direttamente da MyCloud Gallery, con la ricerca semantica e le cose solo sul backup, e in futuro un archivio con
 * indicizzazione e ricerca»). Un **archivio con indice e ricerca**: album, pagine di elementi dal piu' recente, una
 * ricerca per testo (anche per significato), le miniature, la consegna degli indirizzi.
 *
 * Le sorgenti di serie:
 * - [FluidDeviceMediaSource]: il telefono, dal MediaStore, coi tre regimi dei permessi delle foto (tutto, «solo alcune»,
 *   niente);
 * - [FluidRemoteMediaSource]: una sorgente di un'altra app che espone lo stesso contratto con [FluidMediaSourceProvider]
 *   (in F12 MyCloud Gallery: la Libreria, la ricerca semantica, le foto solo nel backup), trovata con
 *   [FluidMediaSources.discover];
 * - [FluidFakeMediaSource]: una sorgente finta, per le prove e la galleria.
 *
 * Un'app ne scrive una sua (un archivio futuro) implementando questa interfaccia; per offrirla alle altre app la mette
 * dietro un [FluidMediaSourceProvider].
 *
 * **Le regole di chi implementa** (le stesse del selettore di MyCloud Gallery, `PickerDelivery.kt`):
 * - **la vista di adesso decide**: si elencano e si consegnano solo gli elementi che la persona potrebbe vedere adesso
 *   nella sorgente (mai i nascosti, mai un album chiuso, mai cio' che il permesso non concede); un elemento sparito o
 *   nascosto dopo che la griglia l'aveva mostrato non si consegna e conta in `skipped`;
 * - **niente dati per sbaglio**: gli id sono opachi (mai percorsi o nomi di file privati), [FluidMediaItem.description]
 *   e [FluidMediaItem.name] solo se la persona li vedrebbe;
 * - **mai attese infinite**: ogni chiamata ha una scadenza sua, e un backup che non risponde si dice subito
 *   ([FluidMediaRefusal.Unreachable]);
 * - **la consegna rispetta l'ordine chiesto** (l'ordine della scelta) e si annulla: una consegna annullata non lascia
 *   niente di consegnato.
 *
 * Le funzioni sospese girano fuori dal filo principale (lo decide chi implementa: il MediaStore e i provider vogliono
 * `Dispatchers.IO`) e sono annullabili.
 */
@Stable
interface FluidMediaSource {
  /** L'id della sorgente, stabile («device», «dev.pampa.mycloudgallery»): entra nelle chiavi della scelta. */
  val id: String

  /** Il nome per la persona («Su questo telefono», «MyCloud Gallery»). */
  val label: String

  /** Cosa sa fare. */
  val capabilities: FluidMediaSourceCapabilities

  /**
   * L'accesso di adesso, come stato di Compose (chi implementa lo tiene in un `mutableStateOf`): il selettore lo legge e
   * mostra la cosa giusta. [refresh] lo rilegge.
   */
  val access: FluidMediaAccess

  /** Rilegge l'accesso: dopo il dialogo del permesso, al ritorno dalle impostazioni o dallo sblocco. */
  suspend fun refresh() {}

  /** Gli album, nell'ordine in cui mostrarli (prima quelli intelligenti, poi gli altri). Vuoto senza accesso. */
  suspend fun albums(): List<FluidMediaAlbum>

  /**
   * Una pagina di elementi per [query], dalla pagina [cursor] (null: la prima), al piu' [limit]. Vuota senza accesso.
   */
  suspend fun page(query: FluidMediaQuery, cursor: String?, limit: Int): FluidMediaPage

  /**
   * La miniatura di [item], col lato lungo vicino a [sizePx] (mai molto piu' piccola: si ingrandirebbe sgranata), o null
   * se non c'e'. La griglia la tiene in una cache sua ([FluidMediaThumbnails]); la sorgente non deve tenerne.
   */
  suspend fun thumbnail(item: FluidMediaItem, sizePx: Int): Bitmap?

  /**
   * Gli indirizzi di [items], nell'ordine dato (l'ordine della scelta), per la [request] dell'app. [onProgress] dice a che
   * punto e' una consegna lunga (un video solo nel backup che arriva dal NAS).
   */
  suspend fun deliver(
    items: List<FluidMediaItem>,
    request: FluidMediaRequest,
    onProgress: (FluidMediaProgress) -> Unit = {},
  ): FluidMediaDelivery
}
