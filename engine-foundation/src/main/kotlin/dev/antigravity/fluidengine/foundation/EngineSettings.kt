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
  /**
   * F10.8 · Il cursore del vetro, da 0 (trasparente) a 1 (colorato), 0,5 il vetro di serie: lo stesso valore di
   * `LocalFluidGlassPreference`. `null` (di serie): il tema non lo tocca, e vale quello che l'app fornisce da sé, come
   * prima. Lo riempie l'aspetto comune di Pampa Store («Vetro: trasparente o colorato»).
   */
  val glassPreference: Float? = null,
  /**
   * F10.8 · La grandezza del testo scelta nell'aspetto comune, **moltiplicata** per quella del sistema: 1 non cambia
   * niente. Il contratto dell'account va da 0,8 a 1,6 a passi di 0,05.
   */
  val textScale: Float = 1f,
  /** F10.8 · Il contrasto alto scelto nell'aspetto comune: vale come il contrasto alto del sistema (vince il più alto). */
  val highContrast: Boolean = false,
  /** F10.8 · «Riduci movimento» scelto nell'aspetto comune: vale come l'animazione spenta del sistema. */
  val reducedMotion: Boolean = false,
)
