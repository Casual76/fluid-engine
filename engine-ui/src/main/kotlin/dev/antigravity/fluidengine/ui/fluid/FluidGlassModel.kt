package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import dev.antigravity.fluidengine.ui.glass.backdrop.glassApiLevel
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.field.glassFieldFits
import dev.antigravity.fluidengine.ui.glass.field.glassFieldFitsAtAnySize
import dev.antigravity.fluidengine.ui.glass.field.glassFieldRenders
import dev.antigravity.fluidengine.ui.glass.field.glassModelChoice

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
   * chiede con [LocalFluidGlassModel]; e, qualunque cosa si chieda, sulla fascia in alto
   * ([GlassFalloff.FadeDown]), sullo scrim dei modali e sulle forme che il campo non sa rendere (vedi
   * [resolveFluidGlassModel]). Le altre strade del vetro scelgono con la stessa regola, e dove la risposta è
   * `Classic` sono quelle di prima **per intero**: Fluid-physics (che il materiale nuovo lo ha solo al livello
   * `Full`, `resolvePhysicsGlass`), la goccia dei controlli e delle voci di menù, la pillola della tab bar e il
   * vetro dipinto di fogli e avvisi di piattaforma. Resta il vetro di prima, qualunque cosa si chieda, chi chiama
   * `drawBackdrop` a mano senza passare il materiale.
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
 *     CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Classic) { content() }
 *
 * Il vetro dentro `content` è allora quello di prima, qualunque dispositivo lo disegni.
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
 *  4. la forma è una che il campo sa rendere ([genericShape] falso): un rettangolo o una forma ad angoli tondi
 *     (`RoundedCornerShape`, `CircleShape`, le forme continue dell'engine; vedi `glassFieldRenders`), **alla
 *     taglia a cui si disegna** (`glassFieldFits`: da F1B.1 il campo rende anche un angolo oltre mezzo lato
 *     corto, come lo disegna Compose, e la taglia non cambia più la risposta). Su un contorno generico il vetro
 *     di oggi spegne la lente e tiene il resto; il materiale nuovo ritaglia con lo shader, e una sagoma che lo
 *     shader non conosce uscirebbe col contorno sbagliato: una `CutCornerShape`, che pure ha quattro «angoli»,
 *     uscirebbe con gli angoli tondi;
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
 * tasto decide da qui se il bagliore del dito passa dalla luce del materiale o resta quello di prima. Lo
 * chiede anche l'ombra staccata di un pannello (`glassShadow`), perché resti l'ombra del vetro che le sta sopra.
 *
 * @param optics già passata da `sanitized()`.
 */
@Composable
internal fun rememberFluidGlassModel(
  shape: Shape,
  falloff: GlassFalloff,
  optics: GlassOptics,
  fit: GlassFieldFit? = null,
): FluidGlassModel {
  val requested = LocalFluidGlassModel.current
  // F1A.7: la finestra della superficie, a cui il riscaldamento chiede quando è disegnata e ferma.
  val view = LocalView.current
  // F1A, giro di correzione: la forma alla taglia misurata. Letto qui, in composizione: quando la misura cambia
  // risposta la superficie si ricompone e passa all'altro vetro.
  val fits = fit?.fits ?: true
  return remember(requested, shape, falloff, optics, fits) {
    // F1A.7: il livello di API su cui il vetro decide, che una build di debug può simulare (`bd/Platform.kt`).
    val sdk = glassApiLevel()
    val genericShape = !glassFieldRenders(shape) || !fits
    val flat = optics.isFlatVeil()
    val model = resolveFluidGlassModel(
      sdk = sdk,
      shadersHealthy = ::glassLiquidSlabShadersHealthy,
      requested = requested,
      falloff = falloff,
      genericShape = genericShape,
      flat = flat,
    )
    // F1A.7: la scelta resta contata nella diagnostica, col suo motivo, finché la superficie è in composizione; e
    // la prima che chiede `Liquid` dove può esistere fa partire il riscaldamento della lastra (`GlassWarmUp`).
    glassModelChoice(model, sdk, requested, falloff, genericShape, flat, view)
  }.model
}

/**
 * Se il campo sa rendere la forma di una superficie **alla taglia del suo nodo**, e il modificatore che la misura.
 *
 * Una forma con i quattro angoli uguali il campo la rende a ogni taglia (`glassFieldFitsAtAnySize`), e per lei non
 * c'è niente da misurare: [modifier] è vuoto e [fits] resta vero. Una con angoli diversi dipende dalla taglia (un
 * fumetto di chat `22/22/22/6` è `Liquid` a 44 dp di altezza, `Classic` a 40, dove Compose disegna l'angolo da 22
 * oltre mezzo lato): [modifier] la misura al layout e [fits] cambia quando cambia la risposta.
 *
 * Il vetro si sceglie in composizione, la taglia arriva dopo: fino alla prima misura [fits] è vero, e una forma che
 * a quella taglia il campo non sa rendere si disegna `Liquid` **per un fotogramma** (con gli angoli limitati a mezzo
 * lato, dentro la sagoma) prima di passare a `Classic`. Una forma che resta resa non cambia mai vetro. Un vetro che
 * cresce e attraversa la soglia (un fumetto che va a capo) cambia vetro quando la attraversa.
 *
 * [modifier] va **in testa** alla catena di chi disegna, nello stesso punto con i due vetri: così il nodo che misura
 * resta lo stesso quando la superficie passa dall'uno all'altro.
 */
@Stable
internal class GlassFieldFit(private val shape: Shape) {

  /** Vero se il campo rende la forma alla taglia misurata (o se non c'è ancora una misura). */
  var fits: Boolean by mutableStateOf(true)
    private set

  /** L'ultima risposta scritta, come campo semplice: il layout non legge lo stato che scrive. */
  private var written = true

  /** Il nodo che misura, o niente se la forma non dipende dalla taglia. */
  val modifier: Modifier = if (glassFieldFitsAtAnySize(shape)) {
    Modifier
  } else {
    Modifier.layout { measurable, constraints ->
      val placeable = measurable.measure(constraints)
      val now = glassFieldFits(shape, Size(placeable.width.toFloat(), placeable.height.toFloat()), this, layoutDirection)
      // Uno stato scritto al layout: la composizione lo legge al fotogramma dopo.
      if (now != written) {
        written = now
        fits = now
      }
      layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
  }
}

/** [GlassFieldFit] per [shape], ricordata finché la forma resta quella. */
@Composable
internal fun rememberGlassFieldFit(shape: Shape): GlassFieldFit = remember(shape) { GlassFieldFit(shape) }
