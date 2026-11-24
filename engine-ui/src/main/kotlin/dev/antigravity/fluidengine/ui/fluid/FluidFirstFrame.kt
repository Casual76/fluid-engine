package dev.antigravity.fluidengine.ui.fluid

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import dev.antigravity.fluidengine.ui.R

// F8.11 · Il primo fotogramma pronto (`apertura-dall-icona`, rivista da Alessio): «vorrei che le app avessero sempre un
// primo fotogramma pronto, in modo che l'avvio sia il più rapido possibile all'occhio, come mandatorio in iOS. All'avvio ci
// pensa già Android e non voglio toccarlo». Quindi niente schermata d'avvio dell'engine e niente uscita animata: la
// schermata d'avvio di Android resta com'è. L'engine fa in modo che la prima pagina vera sia pronta subito.

/**
 * Il primo fotogramma pronto, per un'Activity con Compose. Si chiama in `onCreate`, **prima** di `setContent`:
 *
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *   super.onCreate(savedInstanceState)
 *   enableEdgeToEdge()
 *   fluidFirstFrame()
 *   setContent { FluidTheme(settings) { … } }
 * }
 * ```
 *
 * Cosa fa:
 * - **Il fondo della finestra col colore della pagina, dal primo fotogramma.** Il tema `Theme.Fluid` dell'engine (nel
 *   manifest) dà già il chiaro o lo scuro del sistema; ma un'app col tema scelto dall'utente (scuro su un telefono chiaro,
 *   AMOLED, un accento) lo sa solo dopo aver letto le impostazioni. Qui si rilegge, da un file piccolo e sincrono, il
 *   fondo e il lato che [dev.antigravity.fluidengine.ui.theme.FluidTheme] ha usato l'ultima volta col sistema dalla stessa
 *   parte, e la finestra lo prende subito: niente lampo bianco su un'app scura, niente lampo nero su una chiara. Al primo
 *   avvio in assoluto prende il fondo di serie della pagina Fluid dal lato del sistema.
 * - **Le icone delle barre dalla parte giusta**, dallo stesso lato.
 * - **Tiene il segno**: da qui in poi `FluidTheme` tiene il fondo della finestra uguale a quello della pagina (anche quando
 *   l'utente cambia tema) e aggiorna il file per il prossimo avvio. Un'Activity che non chiama questa funzione non cambia
 *   niente (una finestra trasparente resta trasparente).
 *
 * **La schermata d'avvio di Android.** Con `Theme.Fluid` Android la colora col fondo della finestra, cioè della pagina (lo fa
 * per ogni app). Per lasciarla com'è di sistema si usa `Theme.Fluid.SystemSplash` (lo stesso tema senza il fondo della
 * finestra): l'avvio resta quello del tema Material di piattaforma, e il primo fotogramma dell'app ha comunque il colore
 * della pagina, perché lo mette questa funzione.
 *
 * La struttura della pagina al primo disegno (barra, titolo, scheletri) la dà `FluidScreen` con `FluidContentState`, e il
 * contenuto entra quando c'è; la misura dell'avvio (tempo al primo fotogramma, `reportFullyDrawn`) sta in F7.
 */
fun Activity.fluidFirstFrame() {
  val decor = window.decorView
  decor.setTag(R.id.fluid_first_frame, true)
  // Senza un avvio di prima: il fondo di serie della pagina Fluid dal lato del sistema (la risorsa ha la sua variante
  // `values-night`). Così anche col tema `Theme.Fluid.SystemSplash`, che non colora la finestra, il primo fotogramma ha il
  // fondo giusto.
  val hint = FluidFirstFrameHints.read(this) ?: FluidFirstFrameHint(
    background = getColor(R.color.fluid_page_background),
    dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
  )
  window.setBackgroundDrawable(ColorDrawable(hint.background))
  WindowCompat.getInsetsController(window, decor).apply {
    isAppearanceLightStatusBars = !hint.dark
    isAppearanceLightNavigationBars = !hint.dark
  }
}

/** Il fondo e il lato che il tema ha usato l'ultima volta, per un lato del sistema. */
internal data class FluidFirstFrameHint(val background: Int, val dark: Boolean)

/**
 * Il posto «leggibile subito»: un file di preferenze di poche decine di byte, letto nel `onCreate`. Non `engine-storage`
 * (DataStore è asincrono, e una lettura bloccante lì costa di più del fotogramma che vuole salvare). Una voce per lato del
 * sistema, perché un'app che segue il sistema ha due fondi e uno solo è quello giusto a questo avvio.
 */
internal object FluidFirstFrameHints {
  private const val File = "fluid_engine_first_frame"

  private fun side(context: Context): String =
    if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "night" else "day"

  fun read(context: Context): FluidFirstFrameHint? {
    val prefs = context.getSharedPreferences(File, Context.MODE_PRIVATE)
    val side = side(context)
    if (!prefs.contains("background.$side")) return null
    return FluidFirstFrameHint(prefs.getInt("background.$side", 0), prefs.getBoolean("dark.$side", false))
  }

  fun write(context: Context, hint: FluidFirstFrameHint) {
    val prefs = context.getSharedPreferences(File, Context.MODE_PRIVATE)
    val side = side(context)
    if (prefs.getInt("background.$side", 0) == hint.background && prefs.contains("background.$side") &&
      prefs.getBoolean("dark.$side", !hint.dark) == hint.dark
    ) return
    prefs.edit().putInt("background.$side", hint.background).putBoolean("dark.$side", hint.dark).apply()
  }
}

/**
 * Chiamata dal tema che governa la finestra: se l'Activity ha chiesto il primo fotogramma pronto ([fluidFirstFrame]), tiene
 * il fondo della finestra uguale alla pagina e scrive il suggerimento per il prossimo avvio. Altrimenti non fa niente.
 */
internal fun fluidFirstFrameFollowTheme(view: View, background: Color, dark: Boolean) {
  val activity = view.context.findFirstFrameActivity() ?: return
  val decor = activity.window.decorView
  if (decor.getTag(R.id.fluid_first_frame) != true) return
  val argb = background.copy(alpha = 1f).toArgb()
  val current = (decor.background as? ColorDrawable)?.color
  if (current != argb) activity.window.setBackgroundDrawable(ColorDrawable(argb))
  FluidFirstFrameHints.write(activity, FluidFirstFrameHint(argb, dark))
}

private tailrec fun Context.findFirstFrameActivity(): Activity? = when (this) {
  is Activity -> this
  is android.content.ContextWrapper -> baseContext.findFirstFrameActivity()
  else -> null
}
