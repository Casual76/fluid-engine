# API · engine-widget

_Generato sull'engine 3.1.3 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-widget/widget/EngineWidgetComponents.kt`

- `object EngineWidgetShape` — The radii the design system uses, in the sizes a home screen needs.
  - `val Container`
  - `val Group`
  - `val Tile`
  - `val Pill`
- `@Composable fun EngineWidgetSurface(palette: EngineWidgetPalette, layout: EngineWidgetLayout, onClick: Action? = null, content: @Composable ColumnScope.() -> Unit)` — The widget's outer surface: the app's background, rounded, with one tap target behind everything.
- `@Composable fun EngineWidgetHeader(title: String, palette: EngineWidgetPalette, layout: EngineWidgetLayout, subtitle: String? = null, subtitleIsWarning: Boolean = false, trailing: (@Composable () -> Unit)? = null)` — Title, an optional status line, and one trailing action.
- `@Composable fun EngineWidgetActionButton(@DrawableRes icon: Int, contentDescription: String, action: Action, palette: EngineWidgetPalette, layout: EngineWidgetLayout)` — A round, tinted glyph button — the widget's equivalent of a bar button item.
- `@Composable fun EngineWidgetGroup(palette: EngineWidgetPalette, content: @Composable ColumnScope.() -> Unit)` — Rows sharing one rounded card, the way an inset grouped list does.
- `@Composable fun EngineWidgetRow(title: String, palette: EngineWidgetPalette, layout: EngineWidgetLayout, @DrawableRes icon: Int? = null, tone: EngineWidgetTone = palette.neutralTone, subtitle: String? = null, trailing: String? = null, onClick: Action? = null)` — One row of a group: a tinted icon tile, one or two lines of text, and a trailing value.
- `@Composable fun EngineWidgetHairline(palette: EngineWidgetPalette, layout: EngineWidgetLayout)` — The separator between two rows, inset to where the row's text starts.
- `@Composable fun EngineWidgetIconTile(@DrawableRes icon: Int, tone: EngineWidgetTone, compact: Boolean = false)`
- `@Composable fun EngineWidgetPill(value: String, label: String, tone: EngineWidgetTone, modifier: GlanceModifier = GlanceModifier, compact: Boolean = false)` — A count and what it counts.
- `@Composable fun EngineWidgetMessage(message: String, palette: EngineWidgetPalette, layout: EngineWidgetLayout, @DrawableRes icon: Int? = null, tone: EngineWidgetTone = palette.neutralTone, detail: String? = null)` — The card a widget shows when it has nothing to list.
- `fun engineWidgetTextStyle(color: ColorProvider, size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle` — Glance text has no typography scale to inherit, so the sizes are stated at each call site.

## `engine-widget/widget/EngineWidgetLayout.kt`

- `data class EngineWidgetLayout(val padding: Dp, val compact: Boolean, val rowLimit: Int, val showSubtitle: Boolean, val showFooter: Boolean)` — How much of a list fits in the cell the launcher handed over.
- `object EngineWidgetMetrics`
  - `val RowHeight: Dp` — A two-line row: a 15sp title over a 12sp subtitle, plus the padding around them.
  - `val CompactRowHeight: Dp` — One line and a trailing value, for a cell too short to give a row two of them.
  - `val HeaderHeight: Dp`
  - `val CompactHeaderHeight: Dp`
  - `val FooterHeight: Dp`
  - `val Gap: Dp`
  - `val Padding: Dp`
  - `val CompactPadding: Dp`
  - `const val MaxRows: Int` — Beyond four rows a home-screen widget stops being glanceable and becomes a bad list.
- `fun resolveEngineWidgetLayout(size: DpSize, hasFooter: Boolean = false, maxRows: Int = EngineWidgetMetrics.MaxRows): EngineWidgetLayout` — [hasFooter] is asked for rather than assumed: a footer that has nothing to say should not be drawn, and the row budget should get its space back instead of leaving a gap.

## `engine-widget/widget/EngineWidgetPalette.kt`

- `data class EngineWidgetTone(val container: ColorProvider, val content: ColorProvider)` — A background paired with the content colour that stays readable on it.
- `data class EngineWidgetPalette(val background: ColorProvider, val card: ColorProvider, val hairline: ColorProvider, val onSurface: ColorProvider, val onSurfaceVariant: ColorProvider, val accent: ColorProvider, val accentContainer: ColorProvider, val onAccentContainer: ColorProvider, val attention: ColorProvider, val primaryTone: EngineWidgetTone, val secondaryTone: EngineWidgetTone, val warningTone: EngineWidgetTone, val dangerTone: EngineWidgetTone, val successTone: EngineWidgetTone, val neutralTone: EngineWidgetTone)` — Every colour a widget draws, resolved for both launcher themes in advance.
- `fun engineWidgetPalette(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand): EngineWidgetPalette` — Builds a widget palette from the app's own appearance settings.
- `fun engineWidgetIsDark(settings: EngineSettings, launcherIsDark: Boolean): Boolean` — Whether a widget is dark when the launcher is [launcherIsDark]: **the rule**, public since 3.0.1.
- `fun engineWidgetColorScheme(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand, launcherIsDark: Boolean): ColorScheme` — The full Material scheme a widget uses for one launcher theme (public since 3.0.1, it was the private `widgetScheme`): the app's own palette — accent, dynamic colour, AMOLED — resolved with [engineWidgetIsDark]. For a widget drawn with something other than Glance that wants a role the palette does not carry; most want [engineWidgetArgb].
- `data class EngineWidgetArgb(val background: Int, val card: Int, val hairline: Int, val onSurface: Int, val onSurfaceVariant: Int, val accent: Int, val accentContainer: Int, val onAccentContainer: Int, val attention: Int, val warning: Int, val success: Int, val isDark: Boolean)` — The palette of a widget as plain ARGB ints, for one launcher theme (3.0.1): what a `RemoteViews` widget passes to `setTextColor`, `setInt(id, "setColorFilter", …)` or, from Android 12, to `setColorInt(id, method, day, night)` with the two sets ([launcherIsDark] false and true).
- `fun engineWidgetArgb(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand, launcherIsDark: Boolean): EngineWidgetArgb` — [EngineWidgetArgb] for the launcher theme [launcherIsDark]: see there.
