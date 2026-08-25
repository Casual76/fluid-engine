package dev.antigravity.fluidengine.ui.fluidphysics

import android.os.Build
import androidx.annotation.RequiresApi
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported

/**
 * Quanto materiale un morph può portare su questo dispositivo, adesso.
 *
 * La versione perfetta del motore è pesante per costruzione — un campo di distanza per pixel — e
 * la risposta giusta su un dispositivo che non se la può permettere non è un morph che scatta, è
 * un materiale più povero che si muove uguale. La *geometria* del viaggio è identica su tutti e
 * tre i livelli: quello che cambia è solo quanta ottica la veste.
 */
enum class FluidPhysicsTier {
  /** SDK 33+: rifrazione SDF per ogni famiglia, ponti liquidi fra i pezzi. */
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

/** Il livello per questo processo, senza richieste: il tetto del dispositivo. */
internal fun currentPhysicsTierCeiling(): FluidPhysicsTier = resolvePhysicsTier(
  renderEffectSupported = isRenderEffectSupported(),
  runtimeShaderSupported = isRuntimeShaderSupported(),
  shadersHealthy = FluidPhysicsShaderHealth.healthy(),
  requested = null,
)

/**
 * Una compilazione di prova, una volta per processo.
 *
 * Un loop a 64 lati con array uniform è AGSL legittimo, ma "legittimo" e "accettato da ogni
 * driver del parco Android" non sono la stessa affermazione. Se il driver rifiuta, il verdetto
 * resta e il livello si ferma a [FluidPhysicsTier.Balanced] — un materiale più povero, mai
 * un'eccezione a metà gesto. Le sorgenti sono costanti, quindi il verdetto vale per sempre.
 */
internal object FluidPhysicsShaderHealth {

  @Volatile
  private var verdict: Boolean? = null

  fun healthy(): Boolean {
    if (!isRuntimeShaderSupported()) return false
    verdict?.let { return it }
    val result = probe()
    verdict = result
    return result
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun probe(): Boolean = runCatching {
    android.graphics.RuntimeShader(SlabGroupRefractionShaderString)
    android.graphics.RuntimeShader(PolyMorphRefractionShaderString)
  }.isSuccess

  /** Solo per i test dell'engine. */
  internal fun resetForTesting() {
    verdict = null
  }
}
