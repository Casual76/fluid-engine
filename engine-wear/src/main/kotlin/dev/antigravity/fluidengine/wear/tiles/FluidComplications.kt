package dev.antigravity.fluidengine.wear.tiles

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.wear.sync.FluidWearRole
import dev.antigravity.fluidengine.wear.sync.FluidWearStatus
import dev.antigravity.fluidengine.wear.sync.FluidWearSync

/**
 * Le complicazioni Fluid: i dati di uno stato ([FluidWearStatus]) nella forma che chiede il quadrante.
 *
 * Una complicazione **non si disegna**: la disegna il quadrante, col suo carattere e i suoi colori (come le complicazioni
 * di WidgetKit su watchOS, che il quadrante tinge). Lo stile Fluid arriva dove il formato lo lascia passare:
 * - il **testo breve** è il valore del disco della Tile (al massimo 7 caratteri, il limite dei quadranti), col titolo
 *   sopra;
 * - il **testo lungo** è «valore · riga sotto», col titolo;
 * - l'**intervallo** è l'anello: l'avanzamento dello stato da 0 a 1, con la **rampa dell'accento** (dall'accento
 *   spento all'accento pieno, `ColorRamp`), che i quadranti che la rispettano disegnano coi colori dell'app;
 * - uno stato vecchio dice «Non aggiornato» invece del valore, mai un dato falso.
 */
object FluidComplications {
  /** Il limite del testo breve sui quadranti (documentazione di `ShortTextComplicationData`: «up to 7 characters»). */
  const val ShortTextMaxChars: Int = 7

  /** I tipi che le complicazioni Fluid sanno dare. */
  val SupportedTypes: List<ComplicationType> = listOf(
    ComplicationType.SHORT_TEXT,
    ComplicationType.LONG_TEXT,
    ComplicationType.RANGED_VALUE,
  )

  /**
   * I dati di [status] per il tipo [type] a [nowMillis], con il tocco [tapAction]. `NoData` per un tipo che non c'è in
   * [SupportedTypes], o se il tipo è l'intervallo e lo stato non ha un avanzamento.
   */
  fun dataFor(
    type: ComplicationType,
    status: FluidWearStatus,
    nowMillis: Long,
    colors: FluidTileColors = fluidTileColors(),
    tapAction: PendingIntent? = null,
  ): ComplicationData {
    val stale = status.isStale(nowMillis)
    val value = if (stale) StaleShort else status.value
    val spoken = text(
      listOfNotNull(status.title, value, if (stale) FluidTileContent.StaleDetail else status.detail).joinToString(". "),
    )
    return when (type) {
      ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(value.take(ShortTextMaxChars)), spoken)
        .setTitle(text(status.title))
        .setTapAction(tapAction)
        .build()
      ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
        text(
          if (stale) FluidTileContent.StaleDetail else listOfNotNull(status.value, status.detail).joinToString(" · "),
        ),
        spoken,
      )
        .setTitle(text(status.title))
        .setTapAction(tapAction)
        .build()
      ComplicationType.RANGED_VALUE -> {
        val progress = status.progress ?: return NoDataComplicationData()
        RangedValueComplicationData.Builder(
          value = if (stale) 0f else progress,
          min = 0f,
          max = 1f,
          contentDescription = spoken,
        )
          .setText(text(value.take(ShortTextMaxChars)))
          .setTitle(text(status.title))
          .setColorRamp(accentRamp(colors))
          .setTapAction(tapAction)
          .build()
      }
      else -> NoDataComplicationData()
    }
  }

  /** La rampa dell'accento per un intervallo: dall'accento spento all'accento pieno, sfumata. */
  fun accentRamp(colors: FluidTileColors = fluidTileColors()): ColorRamp =
    ColorRamp(intArrayOf(colors.argb.accentDim, colors.argb.accent), interpolated = true)

  /** L'anteprima di un tipo, per il selettore delle complicazioni del quadrante. */
  fun preview(type: ComplicationType, colors: FluidTileColors = fluidTileColors()): ComplicationData =
    dataFor(type, PreviewStatus, nowMillis = 0, colors = colors)

  private fun text(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

  /** Il testo breve di uno stato vecchio: un trattino, che il quadrante mostra spento. */
  private const val StaleShort = "—"

  private val PreviewStatus = FluidWearStatus(
    key = "anteprima",
    title = "Lezione",
    value = "10:00",
    detail = "Aula 3",
    progress = 0.6f,
    updatedAtMillis = 0,
  )
}

/**
 * Il servizio di una complicazione che mostra lo stato [key] pubblicato dal telefono (`engine-wear-sync`): legge
 * l'archivio locale del ponte, quindi risponde subito e anche ad app chiusa. Si dichiara nel manifest come ogni sorgente
 * di complicazioni (`android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST`, i tipi in
 * `SUPPORTED_TYPES`: `SHORT_TEXT,LONG_TEXT,RANGED_VALUE`, `UPDATE_PERIOD_SECONDS` a 0: si aggiorna quando arriva lo stato,
 * con [FluidWearSurfaces.refresh]).
 */
abstract class FluidStatusComplicationService(
  private val key: String,
  private val brand: AccentPreset = FluidDefaultBrand,
) : SuspendingComplicationDataSourceService() {

  /** Il tocco: di serie nessuno (il quadrante apre il selettore). Un'app passa l'attività che apre lo stato. */
  protected open fun tapAction(): PendingIntent? = null

  override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
    val sync = FluidWearSync.get(this, FluidWearRole.Watch)
    val status = sync.statuses.get(key) ?: return NoDataComplicationData()
    return FluidComplications.dataFor(request.complicationType, status, sync.now(), fluidTileColors(brand), tapAction())
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? =
    if (type in FluidComplications.SupportedTypes) FluidComplications.preview(type, fluidTileColors(brand)) else null
}
