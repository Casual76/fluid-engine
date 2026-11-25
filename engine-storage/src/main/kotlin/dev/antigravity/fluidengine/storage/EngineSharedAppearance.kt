package dev.antigravity.fluidengine.storage

import android.content.Context
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlinx.coroutines.flow.Flow

/**
 * F10.8 · Le scelte d'aspetto che arrivano **da fuori dall'app** (l'aspetto comune di Pampa Store), già risolte per
 * quest'app: per ogni campo prima la sua eccezione, se l'utente l'ha scelta, poi il tema comune. Un campo `null` vuol
 * dire «nessuna scelta comune»: vale quello dell'app ([applyTo]). È l'ultimo gradino della regola di chi vince
 * (`b\architettura.md` § 2.5): **eccezione dell'app, poi tema comune, poi valori di serie dell'app**.
 */
data class EngineAppearanceLayer(
  /** Chiaro, scuro o automatico. Il nero vero resta una scelta dell'app (`amoledEnabled`). */
  val themeMode: ThemeMode? = null,
  /**
   * L'accento: un colore `#rrggbb` (il tema lo porta sul preset con quel colore, o sul più vicino), oppure [ACCENT_APP]
   * (il colore di ogni app, cioè il suo marchio) o [ACCENT_SYSTEM] (i colori del sistema).
   */
  val accent: String? = null,
  /** Il cursore del vetro, 0–1 (`EngineSettings.glassPreference`). */
  val glassPreference: Float? = null,
  /** La grandezza del testo, 0,8–1,6. */
  val textScale: Float? = null,
  val highContrast: Boolean? = null,
  val reducedMotion: Boolean? = null,
  /** La famiglia d'icone (`default`, `tinted`, `dark`, `clear`): la usa l'icona dell'app, non il tema. */
  val iconSet: String? = null,
  /** `true` se l'app segue il tema comune, `false` se ha una sua eccezione (che sta già nei campi). */
  val followShared: Boolean = true,
) {
  /**
   * Le impostazioni dell'app ([own]) con sopra le scelte comuni. Uno scuro comune su un'app che aveva il nero vero
   * (`ThemeMode.AMOLED`) resta nero: il nero si piega in `amoledEnabled`, come fa la sezione Aspetto dell'engine.
   */
  fun applyTo(own: EngineSettings): EngineSettings {
    var s = own
    themeMode?.let { mode ->
      s = s.copy(themeMode = mode, amoledEnabled = s.amoledEnabled || s.themeMode == ThemeMode.AMOLED || mode == ThemeMode.AMOLED)
      if (mode == ThemeMode.AMOLED) s = s.copy(themeMode = ThemeMode.DARK)
    }
    accent?.let {
      s = when (it) {
        ACCENT_APP -> s.copy(accentMode = AccentMode.BRAND)
        // Scegliere «Colori di sistema» li riaccende, come fa la sezione Aspetto dell'engine.
        ACCENT_SYSTEM -> s.copy(accentMode = AccentMode.DYNAMIC, dynamicColorEnabled = true)
        else -> s.copy(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = it)
      }
    }
    glassPreference?.let { s = s.copy(glassPreference = it) }
    textScale?.let { s = s.copy(textScale = it) }
    highContrast?.let { s = s.copy(highContrast = it) }
    reducedMotion?.let { s = s.copy(reducedMotion = it) }
    return s
  }

  /** Nessuna scelta: [applyTo] restituisce le impostazioni dell'app così come sono. */
  val isEmpty: Boolean
    get() = themeMode == null && accent == null && glassPreference == null && textScale == null &&
      highContrast == null && reducedMotion == null && iconSet == null

  companion object {
    val EMPTY = EngineAppearanceLayer()

    /** L'accento comune «il colore di ogni app»: ogni app torna al suo marchio. */
    const val ACCENT_APP = "app"

    /** L'accento comune «i colori del sistema» (Android 12+; sotto, il marchio dell'app). */
    const val ACCENT_SYSTEM = "system"
  }
}

/**
 * F10.8 · Una sorgente dell'aspetto comune. La dà `engine-account` (l'aspetto di Pampa Store, che funziona anche senza
 * account): basta averlo fra le dipendenze, e [EngineSettingsStore] la trova da sé con `ServiceLoader`, come i moduli dei
 * visori. Niente codice nell'`Application`. Senza `engine-account`, o senza Store, l'app ha il suo tema di sempre (R1).
 */
interface EngineSharedAppearance {
  /**
   * Le scelte comuni per quest'app, già con la sua eccezione sopra; `null` quando la sorgente non c'è (lo Store manca,
   * o è firmato da altri): allora vale solo l'app. Il primo valore arriva presto (dalla copia locale), poi a ogni cambio.
   */
  fun layer(context: Context): Flow<EngineAppearanceLayer?>

  /**
   * L'eccezione di quest'app: [followShared] `true` torna al tema comune; `false` tiene i campi non nulli di
   * [exception] (gli altri seguono il comune). Scrive la sorgente, non l'app (sul telefono scrive solo lo Store).
   */
  suspend fun setException(context: Context, followShared: Boolean, exception: EngineAppearanceLayer?)

  companion object {
    @Volatile private var manual: EngineSharedAppearance? = null
    @Volatile private var manualSet = false
    @Volatile private var discovered: Array<EngineSharedAppearance?>? = null

    /** La sorgente di questo processo: quella installata a mano ([install]), o quella trovata, o `null`. */
    fun current(): EngineSharedAppearance? = if (manualSet) manual else discover()

    /**
     * Mette a mano la sorgente (le prove, o un'app che vuole la sua). `null` toglie l'aspetto comune; [reset] torna a
     * quella trovata.
     */
    fun install(source: EngineSharedAppearance?) {
      manual = source
      manualSet = true
    }

    /** Torna alla sorgente trovata con `ServiceLoader`. */
    fun reset() {
      manual = null
      manualSet = false
    }

    private fun discover(): EngineSharedAppearance? {
      discovered?.let { return it[0] }
      synchronized(this) {
        discovered?.let { return it[0] }
        // La forma che R8 riconosce: `ServiceLoader.load(X::class.java, X::class.java.classLoader)`.
        val found = try {
          ServiceLoader.load(EngineSharedAppearance::class.java, EngineSharedAppearance::class.java.classLoader).iterator().let { if (it.hasNext()) it.next() else null }
        } catch (_: ServiceConfigurationError) {
          null
        }
        discovered = arrayOf(found)
        return found
      }
    }
  }
}
