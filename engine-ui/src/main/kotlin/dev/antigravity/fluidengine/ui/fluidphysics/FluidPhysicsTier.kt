package dev.antigravity.fluidengine.ui.fluidphysics

import dev.antigravity.fluidengine.ui.fluid.FluidGlassLiquidMinSdk
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderProbe
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSource
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources

/**
 * Quanto materiale un morph può portare su questo dispositivo, adesso.
 *
 * La versione perfetta del motore è pesante per costruzione — un campo di distanza per pixel — e
 * la risposta giusta su un dispositivo che non se la può permettere non è un morph che scatta, è
 * un materiale più povero che si muove uguale. La *geometria* del viaggio è identica su tutti e
 * tre i livelli: quello che cambia è solo quanta ottica la veste.
 */
enum class FluidPhysicsTier {
  /**
   * SDK 33+: rifrazione SDF per ogni famiglia, ponti liquidi fra i pezzi. Dalla 4.0 a questo livello la
   * superficie si disegna col materiale nuovo (`FluidGlassModel.Liquid`) dove può esistere, e col vetro di prima
   * dove no: vedi `resolvePhysicsGlass`.
   */
  Full,

  /** SDK 31–32, o uno shader che il driver ha rifiutato: sfocatura, vividezza, tinta e bordo. */
  Balanced,

  /** Sotto SDK 31: la tinta solida di ripiego, ritagliata sul path che si trasforma. */
  Lite,
}

/**
 * Il livello effettivo, dati i fatti del dispositivo e l'eventuale richiesta del chiamante.
 *
 * La richiesta può solo *scendere*: chiedere [FluidPhysicsTier.Full] a un dispositivo senza
 * RuntimeShader non è un'opinione, è un crash — quindi il tetto del dispositivo vince sempre.
 * Estratta e pura perché è una tabella di verità, e una tabella di verità si testa.
 */
internal fun resolvePhysicsTier(
  renderEffectSupported: Boolean,
  runtimeShaderSupported: Boolean,
  shadersHealthy: Boolean,
  requested: FluidPhysicsTier?,
): FluidPhysicsTier {
  val ceiling = when {
    !renderEffectSupported -> FluidPhysicsTier.Lite
    !runtimeShaderSupported || !shadersHealthy -> FluidPhysicsTier.Balanced
    else -> FluidPhysicsTier.Full
  }
  if (requested == null) return ceiling
  return if (requested.ordinal > ceiling.ordinal) requested else ceiling
}

/**
 * Con cosa si disegna una superficie di Fluid-physics: il livello, e a [FluidPhysicsTier.Full] quale dei due
 * materiali.
 */
internal class PhysicsGlass(val tier: FluidPhysicsTier, val model: FluidGlassModel) {
  /** Vero se la superficie si disegna col materiale nuovo: corpo, luce e maschera dal campo di `glass/field`. */
  val liquid: Boolean get() = tier == FluidPhysicsTier.Full && model == FluidGlassModel.Liquid

  override fun equals(other: Any?): Boolean = other is PhysicsGlass && other.tier == tier && other.model == model
  override fun hashCode(): Int = tier.hashCode() * 31 + model.hashCode()
  override fun toString(): String = "PhysicsGlass($tier, $model)"
}

/**
 * Il livello e il materiale di una superficie di Fluid-physics (4.0, F1B.1). Pura: è la tabella di verità che
 * `FluidPhysicsTierTest` legge riga per riga.
 *
 * Il materiale nuovo si ha a [FluidPhysicsTier.Full] e solo se: qualcuno lo ha chiesto ([requestedModel], cioè
 * `LocalFluidGlassModel`), il dispositivo ha AGSL ([sdk] da Android 13), e il suo compilatore accetta le tre
 * sorgenti che servono ([liquidHealthy]: corpo a otto pezzi, corpo ad anello, luce a otto pezzi). Ogni «no»
 * scende di un gradino, come sempre, e mai con un'eccezione:
 *
 *  - materiale nuovo rifiutato o non chiesto, sorgenti di prima sane ([classicHealthy]) → `Full` col vetro di
 *    prima, al pixel com'era;
 *  - sorgenti di prima rifiutate ma materiale nuovo sano e chiesto → `Full` col materiale nuovo (un driver che
 *    inciampa nelle vecchie non deve togliere la lente alle nuove);
 *  - né le une né le altre, o niente AGSL → `Balanced`; senza `RenderEffect` → `Lite`.
 *
 * Una richiesta di livello ([requestedTier]) può solo scendere, e sotto `Full` il materiale è sempre quello di
 * prima: `Balanced` e `Lite` non hanno uno shader che disegni il campo.
 *
 * Le due salute sono funzioni perché la prima domanda compila: chi resta sotto `Full` per un'altra ragione non
 * deve far compilare niente.
 */
internal fun resolvePhysicsGlass(
  sdk: Int,
  renderEffectSupported: Boolean,
  runtimeShaderSupported: Boolean,
  classicHealthy: () -> Boolean,
  liquidHealthy: () -> Boolean,
  requestedTier: FluidPhysicsTier?,
  requestedModel: FluidGlassModel,
): PhysicsGlass {
  val liquidPossible = renderEffectSupported && runtimeShaderSupported &&
    requestedModel == FluidGlassModel.Liquid && sdk >= FluidGlassLiquidMinSdk &&
    (requestedTier == null || requestedTier == FluidPhysicsTier.Full)
  // Prima il materiale nuovo: se c'è, le sorgenti di prima non servono e non si compilano nemmeno.
  if (liquidPossible && liquidHealthy()) return PhysicsGlass(FluidPhysicsTier.Full, FluidGlassModel.Liquid)
  val tier = resolvePhysicsTier(
    renderEffectSupported = renderEffectSupported,
    runtimeShaderSupported = runtimeShaderSupported,
    shadersHealthy = runtimeShaderSupported && classicHealthy(),
    requested = requestedTier,
  )
  return PhysicsGlass(tier, FluidGlassModel.Classic)
}

/**
 * Le due sorgenti del materiale nuovo che servono a Fluid-physics, provate tutte: [GlassShaderSources.travel].
 * Un rifiuto è una riga d'errore della sonda col nome della sorgente (`GlassFieldHealth`), e la superficie resta
 * al vetro di prima.
 */
internal fun physicsLiquidShadersHealthy(): Boolean = GlassShaderSources.travel.all(GlassFieldHealth::usable)

/** Il livello per questo processo, senza richieste: il tetto del dispositivo. */
internal fun currentPhysicsTierCeiling(): FluidPhysicsTier = resolvePhysicsTier(
  renderEffectSupported = isRenderEffectSupported(),
  runtimeShaderSupported = isRuntimeShaderSupported(),
  shadersHealthy = FluidPhysicsShaderHealth.healthy(),
  requested = null,
)

/** Le sorgenti senza le quali Fluid-physics non sale a [FluidPhysicsTier.Full]: lente, maschera e ponti. */
internal val PhysicsBaseShaders: List<GlassShaderSource> =
  listOf(GlassShaderSources.SlabGroupRefraction, GlassShaderSources.PolyMorphRefraction)

/** Le due varianti con la frangia cromatica (3.0): senza, si spegne solo la frangia. */
internal val PhysicsDispersionShaders: List<GlassShaderSource> =
  listOf(GlassShaderSources.SlabGroupDispersion, GlassShaderSources.PolyMorphDispersion)

/**
 * Il verdetto di Fluid-physics, dati i verdetti per sorgente di [probe]: le due sorgenti di base, nell'ordine
 * di sempre. Pura rispetto alla sonda, così la tabella «quale sorgente rifiutata porta a quale livello» si
 * prova sul JVM con un compilatore finto.
 */
internal fun physicsShadersHealthy(probe: GlassShaderProbe): Boolean = probe.healthy(PhysicsBaseShaders)

/** La frangia: si prova solo se la base passa, e il suo rifiuto non tocca la base. */
internal fun physicsDispersionHealthy(probe: GlassShaderProbe): Boolean =
  physicsShadersHealthy(probe) && probe.healthy(PhysicsDispersionShaders)

/**
 * Una compilazione di prova per sorgente, una volta per processo, attraverso [GlassFieldHealth].
 *
 * Un loop a 64 lati con array uniform è AGSL legittimo, ma "legittimo" e "accettato da ogni
 * driver del parco Android" non sono la stessa affermazione. Se il driver rifiuta, il verdetto
 * resta e il livello si ferma a [FluidPhysicsTier.Balanced] — un materiale più povero, mai
 * un'eccezione a metà gesto. Le sorgenti sono costanti, quindi il verdetto vale per sempre.
 *
 * Il ripiego però non è più silenzioso (F0.1): dalla 3.0.0 alla 3.1.2 un nome riservato in uno shader ha
 * tenuto tutto il motore a `Balanced` con un solo `Log.w`. Ora ogni sorgente rifiutata è una riga
 * d'errore di [GlassFieldHealth] con il suo nome, la conseguenza qui sotto è un'altra riga d'errore, e i
 * verdetti si leggono in `FluidGlassDiagnostics`.
 */
internal object FluidPhysicsShaderHealth {

  @Volatile
  private var verdict: Boolean? = null

  fun healthy(): Boolean {
    if (!isRuntimeShaderSupported()) return false
    verdict?.let { return it }
    val result = physicsShadersHealthy(GlassFieldHealth.probe)
    if (!result) {
      android.util.Log.e(
        "FluidPhysics",
        "Fluid-physics fermo a Balanced, senza lente ne' ponti: rifiutati ${refused(PhysicsBaseShaders)}.",
      )
    }
    verdict = result
    return result
  }

  @Volatile
  private var dispersionVerdict: Boolean? = null

  /**
   * Le due varianti con la frangia cromatica (3.0), provate a parte e solo se le altre passano: un
   * driver che le rifiutasse toglie la frangia e basta — la lente, la maschera e i ponti restano,
   * perche' il loro verdetto e' quello di [healthy]. Chiesto solo da chi ha una frangia da disegnare.
   */
  fun dispersionHealthy(): Boolean {
    if (!isRuntimeShaderSupported() || !healthy()) return false
    dispersionVerdict?.let { return it }
    val result = physicsDispersionHealthy(GlassFieldHealth.probe)
    if (!result) {
      android.util.Log.e(
        "FluidPhysics",
        "Frangia cromatica di Fluid-physics rifiutata, lente senza frangia: ${refused(PhysicsDispersionShaders)}.",
      )
    }
    dispersionVerdict = result
    return result
  }

  /** I nomi delle sorgenti di [family] che il compilatore ha rifiutato: la riga di log dice quali. */
  private fun refused(family: List<GlassShaderSource>): List<String> {
    val verdicts = GlassFieldHealth.verdicts()
    return family.map { it.name }.filter { verdicts[it] != null }
  }

  /** Solo per i test dell'engine. */
  internal fun resetForTesting() {
    verdict = null
    dispersionVerdict = null
    GlassFieldHealth.resetForTesting()
  }
}
