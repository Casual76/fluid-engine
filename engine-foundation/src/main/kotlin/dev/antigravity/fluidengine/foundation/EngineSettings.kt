package dev.antigravity.fluidengine.foundation

/**
 * How the app decides between its light and dark palettes.
 *
 * [AMOLED] is a third state rather than a flag on [DARK] because it changes what the palette *is*,
 * not how it is chosen: pure black backgrounds need their own surface ladder to stay legible.
 */
enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
  AMOLED,
}

/** Where the accent colour comes from. */
enum class AccentMode {
  /** The app's own colour — whatever brand preset the theme was given. */
  BRAND,

  /** The wallpaper's, on Android 12 and above. Falls back to [BRAND] below that. */
  DYNAMIC,

  /** One of the presets the picker offers, stored by name. */
  CUSTOM_PRESET,
}

/**
 * Everything the engine needs to know to paint an app.
 *
 * Deliberately small, and deliberately not the app's own settings model: an app has dozens of
 * preferences and none of the others belong in a design system. Map your own settings onto this at
 * the edge — one `map { }` on the flow you already have — and the engine stays independent of what
 * else your app happens to store.
 */
data class EngineSettings(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val accentMode: AccentMode = AccentMode.BRAND,
  /** The preset name for [AccentMode.CUSTOM_PRESET]; unknown names fall back to the first preset. */
  val customAccentName: String = "fluid",
  val dynamicColorEnabled: Boolean = true,
  /**
   * Pure black surfaces while in a dark theme.
   *
   * Separate from [ThemeMode.AMOLED] so an app can offer it as a switch under "Dark" instead of as
   * a fourth radio button; either route produces the same palette.
   */
  val amoledEnabled: Boolean = false,
  /**
   * The design system's haptic vocabulary (1.19.0), on or off. Default on: apps vibrated on long
   * presses before this switch existed, and a setting that appears must not change what a phone
   * already did.
   */
  val hapticsEnabled: Boolean = true,
)
