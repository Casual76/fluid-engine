package dev.antigravity.fluidengine.wear.tiles

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.wear.sync.FluidWearRole
import dev.antigravity.fluidengine.wear.sync.FluidWearStatus
import dev.antigravity.fluidengine.wear.sync.FluidWearSync

/**
 * Il servizio di una Tile Fluid: l'app scrive solo [content], cioè che cosa mostrare; il disegno (disco di vetro,
 * anello sul bordo, tasto sul bordo, colori dal suo marchio) lo fa [fluidTile].
 *
 * Sopra `Material3TileService` di Tiles 1.6, con il colore dinamico di sistema **spento**: la Tile ha l'accento
 * dell'app, come il resto di `engine-wear`. Si dichiara nel manifest dell'app dell'orologio come ogni Tile
 * (`androidx.wear.tiles.action.BIND_TILE_PROVIDER`, permesso `com.google.android.wearable.permission.BIND_TILE_PROVIDER`,
 * l'anteprima in `androidx.wear.tiles.PREVIEW`).
 *
 * Le Tile si aggiornano poco, per scelta del sistema: [freshnessMillis] è un desiderio, non una promessa (al più una
 * volta al minuto, documentazione di `Tile.freshness`). Per un dato che cambia sul telefono si chiede un aggiornamento con
 * [FluidWearSurfaces.refresh] quando arriva.
 */
abstract class FluidTileService(
  /** Il marchio dell'app: l'accento della Tile. */
  private val brand: AccentPreset = FluidDefaultBrand,
) : Material3TileService(allowDynamicTheme = false, defaultColorScheme = fluidTileColors(brand).scheme) {

  /** Che cosa mostrare adesso. */
  protected abstract suspend fun content(request: RequestBuilders.TileRequest): FluidTileContent

  /** Ogni quanto il sistema può chiedere di nuovo la Tile da solo; 0 = solo su richiesta. Di serie 15 minuti. */
  protected open val freshnessMillis: Long = DefaultFreshnessMillis

  final override suspend fun MaterialScope.tileResponse(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
    val layout = fluidTile(content(requestParams), fluidTileColors(brand))
    return TileBuilders.Tile.Builder()
      .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
      .setFreshnessIntervalMillis(freshnessMillis)
      .build()
  }

  companion object {
    /** 15 minuti. A occhio, 26/11/2026: il sistema raggruppa comunque gli aggiornamenti. */
    const val DefaultFreshnessMillis: Long = 15 * 60 * 1000L
  }
}

/**
 * La Tile di uno stato pubblicato dal telefono con `engine-wear-sync` ([FluidWearStatus], chiave [key]): la lezione dopo,
 * le mail non lette, il timer. Legge l'archivio locale del ponte, quindi c'è anche ad app chiusa e col telefono lontano;
 * finché il telefono non ha pubblicato niente dice [waiting].
 *
 * ```kotlin
 * class LezioneTile : FluidStatusTileService(key = "lezione", brand = ClasseVivaBrand)
 * ```
 */
abstract class FluidStatusTileService(
  private val key: String,
  brand: AccentPreset = FluidDefaultBrand,
  /** Il tasto sul bordo, facoltativo. */
  private val action: FluidTileAction? = null,
  /** Che cosa dire prima del primo stato. */
  private val waiting: FluidTileContent = FluidTileContent(title = "In attesa", value = "—", detail = "Apri l'app sul telefono"),
) : FluidTileService(brand) {

  override suspend fun content(request: RequestBuilders.TileRequest): FluidTileContent {
    val sync = FluidWearSync.get(this, FluidWearRole.Watch)
    val status = sync.statuses.get(key) ?: return waiting.copy(action = action)
    return FluidTileContent.of(status, sync.now(), action)
  }
}

/** Le richieste di ridisegno delle superfici dell'orologio, quando un dato cambia. */
object FluidWearSurfaces {
  /**
   * Chiede di ridisegnare le Tile [tiles] e tutte le complicazioni dei servizi [complications]. Si chiama da
   * `onStatusChanged` di `FluidWearListenerService`, sull'orologio. Il sistema decide quando (le Tile visibili subito,
   * le altre al prossimo passaggio).
   */
  fun refresh(
    context: Context,
    tiles: List<Class<out TileService>> = emptyList(),
    complications: List<Class<*>> = emptyList(),
  ) {
    val updater = TileService.getUpdater(context)
    tiles.forEach { updater.requestUpdate(it) }
    complications.forEach { service ->
      ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, service)).requestUpdateAll()
    }
  }
}
