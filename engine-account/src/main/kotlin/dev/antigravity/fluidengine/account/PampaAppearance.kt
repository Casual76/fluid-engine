package dev.antigravity.fluidengine.account

import android.content.Context
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.storage.EngineAppearanceLayer
import dev.antigravity.fluidengine.storage.EngineSharedAppearance
import kotlinx.coroutines.flow.Flow

/**
 * F10.8 · L'aspetto comune di Pampa Store tradotto per il tema dell'engine (`b\architettura.md` § 2.5 e § 5.2;
 * `progetto.md` § 3.13). Le chiavi e i valori sono quelli del contratto del server, `Pampa-account\contract\v1\
 * prefs.json`: una prova li confronta con la copia del contratto.
 *
 * **La regola di chi vince**, campo per campo: prima l'eccezione dell'app (se l'utente gliel'ha data e l'app non segue
 * il comune), poi il tema comune, poi i valori di serie dell'app. I primi due gradini li fa [layerOf]; il terzo
 * `EngineAppearanceLayer.applyTo`, dentro `EngineSettingsStore`.
 *
 * Un valore che non si capisce (una chiave nuova, un valore di uno Store più nuovo) vale «nessuna scelta»: l'app resta
 * com'era su quel campo, non si rompe.
 */
object PampaAppearance {
  /** Le chiavi del contratto v1. */
  object Keys {
    const val MODE = "appearance.mode"
    const val ACCENT = "appearance.accent"
    const val GLASS = "appearance.glass"
    const val TEXT_SCALE = "appearance.textScale"
    const val CONTRAST = "appearance.contrast"
    const val ICON_SET = "icon.set"
    const val MOTION_REDUCED = "motion.reduced"
    const val FOLLOW_GLOBAL = "app.followGlobal"
  }

  /** I valori di `appearance.mode`. */
  val MODES = mapOf("system" to ThemeMode.SYSTEM, "light" to ThemeMode.LIGHT, "dark" to ThemeMode.DARK)

  /**
   * F10.8b · Il vetro dell'aspetto comune è la sua **chiarezza** (scelta di Alessio del 25/11/2026): un numero da 0
   * «perfettamente trasparente» a 1 «più sfocato, come ora», di serie 1, come il cursore Liquid Glass di iOS 27. Sul
   * cursore dell'engine (`LocalFluidGlassPreference`) è lo stesso cursore misurato da 0 a 0,5: 0 → 0, 1 →
   * [GLASS_DEFAULT], in linea (`FluidGlassPreference.fromClarity` in `engine-ui`, che questo modulo non porta: la prova
   * `F10p8cGlassCursorTest` del `sample` controlla che i numeri siano gli stessi). Dalla F10.8c il cursore è uno solo,
   * uguale nello Store e nelle app: la metà colorata di prima non c'è più.
   */
  const val GLASS_DEFAULT = 0.5f

  /** Le famiglie d'icone. */
  val ICON_SETS = listOf("default", "tinted", "dark", "clear")

  /** Le chiavi che un'app può tenere diverse dal comune (`scopes: global+app`). */
  val APP_KEYS = setOf(Keys.MODE, Keys.ACCENT, Keys.GLASS, Keys.ICON_SET)

  /** I limiti di `appearance.textScale` nel contratto. */
  const val TEXT_SCALE_MIN = 0.8f
  const val TEXT_SCALE_MAX = 1.6f

  private val HEX = Regex("^#[0-9a-fA-F]{6}$")

  /**
   * I due accenti che non sono un colore: «il colore di ogni app» e «i colori del sistema» (F10.8b, scelta di Alessio
   * del 25/11/2026; nel contratto sono le `keywords` del tipo `color`).
   */
  val SPECIAL_ACCENTS = setOf(EngineAppearanceLayer.ACCENT_APP, EngineAppearanceLayer.ACCENT_SYSTEM)

  private fun accentOf(value: String): String? = when {
    value in SPECIAL_ACCENTS -> value
    HEX.matches(value) -> value.lowercase()
    else -> null
  }

  /** Le scelte che valgono per quest'app: per ogni chiave l'eccezione (se l'app non segue il comune), poi il comune. */
  fun layerOf(shared: SharedAppearance): EngineAppearanceLayer {
    fun v(key: String) = shared.effective(key)
    return EngineAppearanceLayer(
      themeMode = v(Keys.MODE)?.let(MODES::get),
      accent = v(Keys.ACCENT)?.let(::accentOf),
      glassPreference = v(Keys.GLASS)?.let(::glassPreferenceOf),
      textScale = v(Keys.TEXT_SCALE)?.toFloatOrNull()?.takeIf { it.isFinite() && it in TEXT_SCALE_MIN..TEXT_SCALE_MAX },
      highContrast = when (v(Keys.CONTRAST)) { "high" -> true; "standard" -> false; else -> null },
      reducedMotion = v(Keys.MOTION_REDUCED)?.toBooleanStrictOrNull(),
      iconSet = v(Keys.ICON_SET)?.takeIf { it in ICON_SETS },
      followShared = shared.followGlobal,
    )
  }

  /** Il cursore del vetro dell'engine per un valore di `appearance.glass` (la chiarezza, da 0 a 1), o `null`. */
  fun glassPreferenceOf(value: String): Float? =
    value.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..1f }?.let { it * GLASS_DEFAULT }

  /**
   * La chiarezza per il contratto di un valore del cursore dell'engine. Un valore salvato oltre [GLASS_DEFAULT] (la metà
   * colorata di prima, F10.8c) è 1, «come ora», come lo legge il vetro.
   */
  fun clarityOf(glassPreference: Float): Float =
    if (glassPreference.isFinite()) (glassPreference / GLASS_DEFAULT).coerceIn(0f, 1f) else 1f

  /**
   * I campi di un'eccezione dell'app scritti nelle chiavi del contratto, per `setAppearance`. Solo le chiavi che un'app
   * può tenere diverse ([APP_KEYS]); il vetro diventa la sua chiarezza, a passi di 0,01 come nel contratto.
   */
  fun exceptionValues(layer: EngineAppearanceLayer): Map<String, String> = buildMap {
    layer.themeMode?.let { mode ->
      // Il nero vero non è nel contratto: è «scuro», e il nero resta una scelta dell'app.
      val wire = if (mode == ThemeMode.AMOLED) "dark" else MODES.entries.firstOrNull { it.value == mode }?.key
      if (wire != null) put(Keys.MODE, wire)
    }
    layer.accent?.let(::accentOf)?.let { put(Keys.ACCENT, it) }
    layer.glassPreference?.let { g ->
      put(Keys.GLASS, java.math.BigDecimal(clarityOf(g).toDouble()).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
    }
    layer.iconSet?.takeIf { it in ICON_SETS }?.let { put(Keys.ICON_SET, it) }
  }
}

/**
 * F10.8 · La sorgente dell'aspetto comune per `EngineSettingsStore`: la trova `ServiceLoader` (`META-INF/services` di
 * questo modulo), quindi un'app con `engine-account` ha il tema dello Store senza scrivere niente. Legge e scrive dallo
 * Store con [PampaAccount]: nessuna rete (R9).
 */
class PampaSharedAppearance : EngineSharedAppearance {
  override fun layer(context: Context): Flow<EngineAppearanceLayer?> = PampaAccount.client(context).sharedLayer

  override suspend fun setException(context: Context, followShared: Boolean, exception: EngineAppearanceLayer?) {
    PampaAccount.setAppearanceException(context, followShared, exception?.let(PampaAppearance::exceptionValues))
  }
}
