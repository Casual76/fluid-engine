package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.field.glassFieldRenders

/**
 * Con quale materiale l'engine disegna il vetro.
 *
 * Sono due perché il materiale nuovo non può esistere ovunque: chiede AGSL (Android 13) e un compilatore che
 * accetti le sue sorgenti. Dove non può, il vetro è quello di prima, **com'era**: non una copia impoverita del
 * nuovo, ma il codice di sempre, lasciato dov'era.
 */
enum class FluidGlassModel {
  /**
   * Il materiale nuovo: corpo, luce e ombra escono da un campo di distanza (`ui/glass/field`), con il velo,
   * il filo e i riflessi del profilo dell'engine. Di serie, da Android 13.
   */
  Liquid,

  /**
   * Il vetro della 3.x: lente di Kyant, pellicola dipinta, anello speculare, un'ombra. È ciò che si vede
   * sotto Android 13, su un dispositivo il cui compilatore rifiuta le sorgenti nuove, e dove un antenato lo
   * chiede con [LocalFluidGlassModel].
   */
  Classic,
}

/**
 * Il materiale chiesto per il vetro di questo sottoalbero. Di serie [FluidGlassModel.Liquid].
 *
 * È una **richiesta**: [FluidGlassModel.Classic] si ottiene sempre, [FluidGlassModel.Liquid] solo dove può
 * esistere (vedi [resolveFluidGlassModel]). Chi lo fornisce: un modulo che non è ancora passato al materiale
 * nuovo (l'orologio), una pagina che mette i due vetri uno accanto all'altro, un'app che vuole restare
 * all'aspetto di prima finché non ha rivisto le proprie schermate.
 *
 *     CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Classic) { /* vetro di prima */ }
 */
val LocalFluidGlassModel = compositionLocalOf { FluidGlassModel.Liquid }

/** Il primo livello di API con AGSL: sotto, il materiale nuovo non c'è. */
internal const val FluidGlassLiquidMinSdk: Int = Build.VERSION_CODES.TIRAMISU

/**
 * Il materiale con cui una superficie si disegna davvero. Pura, come `resolvePhysicsTier`: è la tabella di
 * verità che `GlassModelSelectionTest` legge riga per riga.
 *
 * `Liquid` solo se tutte queste sono vere, nell'ordine in cui si chiedono:
 *  1. qualcuno lo ha chiesto ([requested]);
 *  2. il dispositivo ha AGSL ([sdk] da [FluidGlassLiquidMinSdk] in su);
 *  3. la superficie non è la fascia in alto ([GlassFalloff.FadeDown]): resta quella di oggi finché non la
 *     sostituisce il bordo dello scorrimento (piano, F2.5);
 *  4. la forma è una che il campo sa rendere ([genericShape] falso): un rettangolo o una `CornerBasedShape`.
 *     Su un contorno generico il vetro di oggi spegne la lente e tiene il resto; il materiale nuovo ritaglia
 *     con lo shader, e una sagoma che lo shader non conosce uscirebbe col contorno sbagliato;
 *  5. la superficie ha uno spessore ([flat] falso). Un'ottica senza lente, senza bordo e senza ombra non
 *     descrive una lastra: è un velo che sfoca appena e scurisce, cioè lo scrim di un modale. Il materiale
 *     nuovo gli darebbe la smerigliatura e l'ombreggiatura di una lastra grande quanto lo schermo;
 *  6. il compilatore del dispositivo accetta le sorgenti che servono ([shadersHealthy]). Si chiede per
 *     ultima, e come funzione, perché la prima domanda compila: una superficie che resta `Classic` per una
 *     delle ragioni sopra non deve far compilare i programmi nuovi.
 *
 * @param shadersHealthy vero se corpo e luce compilano su questo dispositivo.
 */
internal fun resolveFluidGlassModel(
  sdk: Int,
  shadersHealthy: () -> Boolean,
  requested: FluidGlassModel,
  falloff: GlassFalloff,
  genericShape: Boolean,
  flat: Boolean = false,
): FluidGlassModel = when {
  requested != FluidGlassModel.Liquid -> FluidGlassModel.Classic
  sdk < FluidGlassLiquidMinSdk -> FluidGlassModel.Classic
  falloff == GlassFalloff.FadeDown -> FluidGlassModel.Classic
  genericShape -> FluidGlassModel.Classic
  flat -> FluidGlassModel.Classic
  !shadersHealthy() -> FluidGlassModel.Classic
  else -> FluidGlassModel.Liquid
}

/**
 * Vero se questa ottica non descrive una lastra: né lente, né bordo, né ombra. Vedi il punto 5 di
 * [resolveFluidGlassModel].
 *
 * @receiver un'ottica già passata da `sanitized()`.
 */
internal fun GlassOptics.isFlatVeil(): Boolean =
  (refractionHeight.value <= 0f || refractionAmount.value <= 0f) && highlightAlpha <= 0f && shadowAlpha <= 0f

/**
 * Le sorgenti che una lastra ferma `Liquid` disegna: il corpo e la luce a un pezzo. Se il compilatore ne
 * rifiuta una, la superficie resta `Classic` per intero (mai un corpo nuovo con il bordo di prima).
 */
internal fun glassLiquidSlabShadersHealthy(): Boolean =
  GlassFieldHealth.usable(GlassShaderSources.Body1) && GlassFieldHealth.usable(GlassShaderSources.Light1)

/**
 * Il materiale di una `glassSurface` con questa forma, questa caduta e questa ottica, in questo punto
 * dell'albero. Lo chiedono `glassSurface` e `glassControlSurface`, che devono rispondere allo stesso modo: il
 * tasto decide da qui se il bagliore del dito passa dalla luce del materiale o resta quello di prima.
 *
 * @param optics già passata da `sanitized()`.
 */
@Composable
internal fun rememberFluidGlassModel(shape: Shape, falloff: GlassFalloff, optics: GlassOptics): FluidGlassModel {
  val requested = LocalFluidGlassModel.current
  return remember(requested, shape, falloff, optics) {
    resolveFluidGlassModel(
      sdk = Build.VERSION.SDK_INT,
      shadersHealthy = ::glassLiquidSlabShadersHealthy,
      requested = requested,
      falloff = falloff,
      genericShape = !glassFieldRenders(shape),
      flat = optics.isFlatVeil(),
    )
  }
}
