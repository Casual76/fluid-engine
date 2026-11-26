package dev.antigravity.fluidengine.wear.tiles

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.background
import androidx.wear.protolayout.modifiers.border
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.clip
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import dev.antigravity.fluidengine.wear.sync.FluidWearStatus

/**
 * Che cosa mostra una Tile Fluid: un titolo piccolo in alto, un **valore** dentro il disco di vetro al centro, una riga
 * sotto, l'**anello sul bordo** per l'avanzamento e un tasto sul bordo in basso.
 *
 * Il riferimento è la Smart Stack di watchOS 10 (i widget di WidgetKit sul polso: un'informazione sola, grande, un
 * tocco per aprire) e la Tile di Wear OS con Material 3 Expressive (titolo, slot principale, tasto sul bordo: la
 * disposizione è `primaryLayout` di ProtoLayout, quindi le misure e i margini per 192 e 225 dp sono quelli del sistema).
 * Fluid aggiunge l'accento, il disco di vetro e l'anello sul bordo, come le schermate di `engine-wear`.
 *
 * @property title la riga in alto, corta (la materia, «Posta»).
 * @property value il valore nel disco: un orario, un numero, al massimo [ValueMaxChars] caratteri per il carattere grande.
 * @property detail la riga sotto il disco, facoltativa.
 * @property progress l'anello sul bordo, da 0 a 1; `null` lo toglie.
 * @property action il tasto sul bordo in basso, facoltativo.
 * @property onClick il tocco sul resto della Tile (apre l'app): di serie ricarica la Tile.
 * @property stale vero se il valore non è più attuale: il disco resta, il testo si spegne e la riga sotto dice
 * [StaleDetail], come la Smart Stack che non mente su un dato vecchio.
 * @property contentDescription la frase per TalkBack; di serie titolo, valore e riga sotto.
 */
data class FluidTileContent(
  val title: String,
  val value: String,
  val detail: String? = null,
  val progress: Float? = null,
  val action: FluidTileAction? = null,
  val onClick: ActionBuilders.Action? = null,
  val stale: Boolean = false,
  val contentDescription: String? = null,
) {
  init {
    require(progress == null || progress in 0f..1f) { "progress fuori da 0..1: $progress" }
  }

  /** La frase per TalkBack. */
  val spoken: String
    get() = contentDescription ?: listOfNotNull(title, value, if (stale) StaleDetail else detail).joinToString(". ")

  companion object {
    /** Oltre questi caratteri il valore scende dal carattere numerale a quello del titolo, per stare nel disco. */
    const val ValueMaxChars: Int = 5

    /** La riga di uno stato vecchio. */
    const val StaleDetail: String = "Non aggiornato"

    /**
     * La Tile di uno stato pubblicato dal telefono ([FluidWearStatus]): titolo, valore, riga sotto, anello. A [nowMillis]
     * oltre [FluidWearStatus.staleAfterMillis] la Tile si spegne.
     */
    fun of(status: FluidWearStatus, nowMillis: Long, action: FluidTileAction? = null, onClick: ActionBuilders.Action? = null) =
      FluidTileContent(
        title = status.title,
        value = status.value,
        detail = status.detail,
        progress = status.progress,
        action = action,
        onClick = onClick,
        stale = status.isStale(nowMillis),
      )
  }
}

/**
 * Il tasto sul bordo in basso: [label] corta («Apri», «Chiedi», «Pausa»). [launch] è l'attività che apre (dell'app
 * dell'orologio); `null` ricarica la Tile con l'id [id] (lo legge `requestParams.currentState.lastClickableId`).
 */
data class FluidTileAction(
  val label: String,
  val launch: ComponentName? = null,
  val id: String = "fluid-tile-action",
) {
  /** L'azione di ProtoLayout. */
  fun toAction(): ActionBuilders.Action = launch?.let { fluidTileLaunch(it) } ?: loadAction()
}

/** L'azione che apre l'attività [activity] dalla Tile. */
fun fluidTileLaunch(activity: ComponentName): ActionBuilders.Action =
  ActionBuilders.LaunchAction.Builder()
    .setAndroidActivity(
      ActionBuilders.AndroidActivity.Builder()
        .setPackageName(activity.packageName)
        .setClassName(activity.className)
        .build(),
    )
    .build()

/** Le misure della Tile Fluid, in dp e in frazioni dello schermo. */
object FluidTileDimens {
  /** Il diametro del disco, in frazione della larghezza dello schermo: 0,42 senza riga sotto, 0,36 con. A occhio, 26/11/2026 (sta nello slot principale di `primaryLayout` a 192 dp col tasto sul bordo). */
  const val DiscFraction: Float = 0.42f
  const val DiscFractionWithDetail: Float = 0.36f

  /** Il tratto dell'anello sul bordo: `FluidWearDimens.RingStroke`, 4 dp. */
  const val RingStroke: Float = 4f

  /** Il filetto del disco: 0,8 dp, quello del vetro dell'orologio (`FluidWearGlass.Optics.highlightWidth`). */
  const val DiscRim: Float = 0.8f
}

/**
 * La Tile Fluid di [content], dentro un `MaterialScope` (quello che `Material3TileService` passa a `tileResponse`, o
 * `materialScope(...)` in una prova). I colori sono [colors]; di serie quelli di [fluidTileColors] col marchio di serie.
 */
fun MaterialScope.fluidTile(content: FluidTileContent, colors: FluidTileColors = fluidTileColors()): LayoutElement {
  val width = deviceConfiguration.screenWidthDp.toFloat()
  val layout = primaryLayout(
    titleSlot = { text(content.title.layoutString, typography = Typography.TITLE_SMALL, color = colors.secondary, maxLines = 1) },
    mainSlot = { fluidTileDisc(content, colors, width) },
    bottomSlot = content.action?.let { action ->
      {
        textEdgeButton(
          onClick = ModifiersBuilders.Clickable.Builder().setId(action.id).setOnClick(action.toAction()).build(),
          colors = ButtonColors(
            containerColor = colors.accent,
            iconColor = colors.scheme.onPrimary,
            labelColor = colors.scheme.onPrimary,
            secondaryLabelColor = colors.scheme.onPrimary,
          ),
          labelContent = { text(action.label.layoutString) },
        )
      }
    },
    onClick = ModifiersBuilders.Clickable.Builder()
      .setId("fluid-tile")
      .setOnClick(content.onClick ?: loadAction())
      .build(),
  )
  val ring = content.progress?.let { fluidTileEdgeRing(it, colors) }
  return box(
    *listOfNotNull(ring, layout).toTypedArray(),
    width = expand(),
    height = expand(),
    modifier = LayoutModifier.contentDescription(content.spoken),
  )
}

/** Il disco di vetro col valore e, sotto, la riga. */
private fun MaterialScope.fluidTileDisc(content: FluidTileContent, colors: FluidTileColors, screenWidth: Float): LayoutElement {
  val showDetail = content.stale || content.detail != null
  val diameter = screenWidth * if (showDetail) FluidTileDimens.DiscFractionWithDetail else FluidTileDimens.DiscFraction
  val valueColor = if (content.stale) colors.stale else colors.onDisc
  val disc = box(
    text(
      content.value.layoutString,
      typography = if (content.value.length <= FluidTileContent.ValueMaxChars) Typography.NUMERAL_SMALL else Typography.TITLE_MEDIUM,
      color = valueColor,
      maxLines = 1,
      alignment = TEXT_ALIGN_CENTER,
      overflow = TEXT_OVERFLOW_ELLIPSIZE,
    ),
    width = dp(diameter),
    height = dp(diameter),
    modifier = LayoutModifier
      .background(colors.discFill)
      .clip(diameter / 2f)
      .border(FluidTileDimens.DiscRim, colors.discRim),
    horizontalAlignment = HORIZONTAL_ALIGN_CENTER,
    verticalAlignment = VERTICAL_ALIGN_CENTER,
  )
  if (!showDetail) return disc
  val detail = if (content.stale) FluidTileContent.StaleDetail else content.detail.orEmpty()
  return column(
    disc,
    androidx.wear.protolayout.layout.spacer(height = dp(DetailGap)),
    text(
      detail.layoutString,
      typography = Typography.BODY_SMALL,
      color = if (content.stale) colors.stale else colors.secondary,
      maxLines = 1,
      alignment = TEXT_ALIGN_CENTER,
      overflow = TEXT_OVERFLOW_ELLIPSIZE,
    ),
    horizontalAlignment = HORIZONTAL_ALIGN_CENTER,
  )
}

/**
 * L'anello sul bordo: la pista intera e l'avanzamento dalle ore dodici in senso orario, a filo del bordo dello schermo
 * (come `FluidEdgeGlowRing`, senza l'alone: ProtoLayout non sfuma). Lo spessore è [FluidTileDimens.RingStroke].
 */
fun fluidTileEdgeRing(progress: Float, colors: FluidTileColors = fluidTileColors()): LayoutElement {
  val sweep = 360f * progress.coerceIn(0f, 1f)
  val arc = LayoutElementBuilders.Arc.Builder()
    .setAnchorAngle(degrees(0f))
    .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
    .addContent(
      LayoutElementBuilders.ArcLine.Builder()
        .setLength(degrees(sweep))
        .setThickness(dp(FluidTileDimens.RingStroke))
        .setColor(colors.ring.prop)
        .setStrokeCap(if (sweep in 1f..359f) LayoutElementBuilders.STROKE_CAP_ROUND else LayoutElementBuilders.STROKE_CAP_BUTT)
        .build(),
    )
    .build()
  val track = LayoutElementBuilders.Arc.Builder()
    .setAnchorAngle(degrees(0f))
    .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
    .addContent(
      LayoutElementBuilders.ArcLine.Builder()
        .setLength(degrees(360f))
        .setThickness(dp(FluidTileDimens.RingStroke))
        .setColor(colors.ringTrack.prop)
        .build(),
    )
    .build()
  return box(track, arc, width = expand(), height = expand())
}

/** Lo spazio fra il disco e la riga sotto. A occhio, 26/11/2026. */
private const val DetailGap = 4f
