package dev.antigravity.fluidengine.wear.tiles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.argb
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme
import dev.antigravity.fluidengine.wear.theme.FluidWearDefaults
import dev.antigravity.fluidengine.wear.theme.fluidWearColorScheme
import dev.antigravity.fluidengine.wear.theme.liftForBlack

/**
 * I colori di una Tile e di una complicazione Fluid, già risolti: ProtoLayout non ha un tema da leggere al momento del
 * disegno (come Glance per i widget del telefono, il layout va al sistema come dati), quindi tutto si decide qui, una
 * volta, dagli stessi token di [dev.antigravity.fluidengine.wear.theme.FluidWearTheme].
 *
 * Una Tile sta **sempre sul nero**: il carosello delle Tile di Wear OS è nero, come il quadrante, e ogni pixel acceso costa
 * batteria. Per questo i colori vengono sempre dallo schema scuro ([FluidWearDefaults.settings], nero assoluto), qualunque
 * tema abbia scelto l'app del telefono: un tema chiaro passato qui dà lo stesso accento, mai un fondo bianco al polso.
 *
 * @property scheme lo schema di Material 3 per le Tile (ProtoLayout), mappato ruolo per ruolo da quello dell'orologio.
 * @property accent l'accento, alzato finché si legge sul nero ([liftForBlack]).
 * @property discFill il **disco di vetro**: il materiale di ripiego di `FluidWearGlass.tint()` (il fumé con un sesto
 * di accento, quasi opaco), composto sul nero. ProtoLayout disegna nel processo del sistema e non ha né sfocatura né
 * rifrazione: il vetro vero dell'orologio non si può portare in una Tile, e il suo ripiego è la stessa lastra che
 * l'engine mostra sotto Android 12 (scheda `moduli/wear.md`, «Il vetro dell'orologio»).
 * @property discRim il filetto del disco: bianco al 18%, come il vetro dell'orologio.
 * @property ringTrack la pista dell'anello sul bordo: `onSurface` al 10%, come `FluidEdgeGlowRing`.
 * @property ring l'anello pieno: l'accento.
 * @property onDisc il testo sul disco.
 * @property secondary il testo piccolo (titolo, riga sotto).
 * @property stale il testo di uno stato non più aggiornato: lo stesso grigio, più spento.
 */
class FluidTileColors internal constructor(
  val scheme: ColorScheme,
  val accent: LayoutColor,
  val discFill: LayoutColor,
  val discRim: LayoutColor,
  val ringTrack: LayoutColor,
  val ring: LayoutColor,
  val onDisc: LayoutColor,
  val secondary: LayoutColor,
  val stale: LayoutColor,
  /** Gli stessi colori come ARGB, per le prove e per chi disegna altro (le rampe delle complicazioni). */
  val argb: FluidTileArgb,
)

/** I colori di [FluidTileColors] come interi ARGB. */
data class FluidTileArgb(
  val background: Int,
  val accent: Int,
  /** L'accento a meta' strada verso il nero: l'inizio della rampa di un intervallo. */
  val accentDim: Int,
  val onAccent: Int,
  val discFill: Int,
  val discRim: Int,
  val ringTrack: Int,
  val onDisc: Int,
  val secondary: Int,
  val stale: Int,
)

/**
 * I colori Fluid per le Tile e le complicazioni, dal marchio [brand] dell'app. [settings] serve solo per l'accento
 * (marchio o preimpostato): il fondo resta nero (vedi [FluidTileColors]).
 */
fun fluidTileColors(
  brand: AccentPreset = FluidDefaultBrand,
  settings: EngineSettings = FluidWearDefaults.settings,
): FluidTileColors {
  val watchSettings = settings.copy(
    themeMode = FluidWearDefaults.settings.themeMode,
    amoledEnabled = true,
    // Il colore dinamico del telefono non arriva in una Tile: la Tile la disegna il sistema dell'orologio.
    dynamicColorEnabled = false,
  )
  val wear = fluidWearColorScheme(fluidColorScheme(settings = watchSettings, isDark = true, brand = brand))
  val accent = liftForBlack(wear.primary)
  val background = Color.Black
  val discFill = lerp(GlassBase, accent, GlassAccentShare).copy(alpha = GlassAlpha).compositeOver(background)
  val argb = FluidTileArgb(
    background = background.toArgb(),
    accent = accent.toArgb(),
    accentDim = lerp(accent, background, RampDim).toArgb(),
    onAccent = wear.onPrimary.toArgb(),
    discFill = discFill.toArgb(),
    discRim = Color.White.copy(alpha = RimAlpha).toArgb(),
    ringTrack = wear.onSurface.copy(alpha = TrackAlpha).toArgb(),
    onDisc = Color.White.toArgb(),
    secondary = wear.onSurfaceVariant.toArgb(),
    stale = wear.onSurfaceVariant.copy(alpha = StaleAlpha).compositeOver(background).toArgb(),
  )
  fun c(color: Color): LayoutColor = color.toArgb().argb
  val scheme = ColorScheme(
    primary = c(accent),
    primaryDim = c(wear.primaryDim),
    primaryContainer = c(wear.primaryContainer),
    onPrimary = c(wear.onPrimary),
    onPrimaryContainer = c(wear.onPrimaryContainer),
    secondary = c(wear.secondary),
    secondaryDim = c(wear.secondaryDim),
    secondaryContainer = c(wear.secondaryContainer),
    onSecondary = c(wear.onSecondary),
    onSecondaryContainer = c(wear.onSecondaryContainer),
    tertiary = c(wear.tertiary),
    tertiaryDim = c(wear.tertiaryDim),
    tertiaryContainer = c(wear.tertiaryContainer),
    onTertiary = c(wear.onTertiary),
    onTertiaryContainer = c(wear.onTertiaryContainer),
    surfaceContainerLow = c(wear.surfaceContainerLow),
    surfaceContainer = c(wear.surfaceContainer),
    surfaceContainerHigh = c(wear.surfaceContainerHigh),
    onSurface = c(wear.onSurface),
    onSurfaceVariant = c(wear.onSurfaceVariant),
    outline = c(wear.outline),
    outlineVariant = c(wear.outlineVariant),
    background = c(background),
    onBackground = c(wear.onBackground),
    error = c(wear.error),
    errorDim = c(wear.errorDim),
    errorContainer = c(wear.errorContainer),
    onError = c(wear.onError),
    onErrorContainer = c(wear.onErrorContainer),
  )
  return FluidTileColors(
    scheme = scheme,
    accent = argb.accent.argb,
    discFill = argb.discFill.argb,
    discRim = argb.discRim.argb,
    ringTrack = argb.ringTrack.argb,
    ring = argb.accent.argb,
    onDisc = argb.onDisc.argb,
    secondary = argb.secondary.argb,
    stale = argb.stale.argb,
    argb = argb,
  )
}

// Il ripiego di FluidWearGlass.tint(): la base fume', un sesto di accento, al 95%. Gli stessi numeri di FluidWearGlass.
private val GlassBase = Color(0xFF141416)
private const val GlassAccentShare = 0.16f
private const val GlassAlpha = 0.95f
private const val RimAlpha = 0.18f
// La pista di FluidEdgeGlowRing (EdgeTrackAlpha).
private const val TrackAlpha = 0.10f
// Uno stato vecchio: il grigio secondario a meta', ancora leggibile ma chiaramente spento. A occhio, 26/11/2026.
private const val StaleAlpha = 0.6f
// L'inizio della rampa di un intervallo: l'accento verso il nero di quanto basta a vedere la sfumatura e non a perderlo.
// A occhio, 26/11/2026.
private const val RampDim = 0.45f
