/*
 * Parti di questo file (il nucleo `GlassEdgeBlurKernel` e la forma della passata in `GlassEdgeBlurShaderString`) sono
 * portate da Haze:
 *   Copyright 2024-2026, Christopher Banes and the Haze project contributors.
 *   https://github.com/chrisbanes/haze, commit 95c0c48a915d3593e7f8d34039404f54f4e538e7:
 *   haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/SemanticBlurKernel.kt,
 *   haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassShaders.kt (buildBlur),
 *   haze-utils/src/commonMain/kotlin/dev/chrisbanes/haze/ProgressiveBlurRenderEffect.kt (clampToCrop).
 *
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use those parts except in
 *   compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is
 *   distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and limitations under the License.
 *
 * Cambiato dal Fluid Engine (Antigravity), vedi LICENSES/Haze.md: nomi e commenti; le coppie in due array; lo shader
 * riscritto con un array di uniform e un ciclo, la quota per pixel da una rampa invece che da una maschera, e dopo la
 * sfocatura velo, scurimento e dissolvenza dell'engine. Il resto del file è dell'engine (GPL-3.0).
 *
 * ---
 *
 * La STRISCIA del bordo dello scorrimento (F2.4, piano «iOS in Android» 3.10): il contenuto che scorre sotto i tasti
 * di una barra si scioglie nel colore del fondo, con una sfocatura che cresce verso il bordo. Nessuna lastra: niente
 * sagoma, niente lente, niente filo. È l'aspetto di iOS 26 per decisione di Alessio (2026-11-10, piano sezione 2): la
 * fascia uniforme a taglio netto di iOS 27 (R5 E1) non si fa.
 *
 * Qui ci sono il programma (una sorgente, due passate: la direzione è uno uniform), il nucleo della sfocatura che la
 * CPU calcola una volta per raggio, e il **profilo**: dove la sfocatura e il velo crescono, in altezze dei tasti
 * (`H`), con le fonti. Il nodo e l'API stanno in `ui/fluid/FluidScrollEdge.kt`.
 *
 * ### Perché due passate a raggio variabile
 * La fascia di oggi (`GlassFalloff.FadeDown`) è una sfocatura uniforme dissolta da una maschera d'alfa: a metà
 * dissolvenza si leggono insieme il testo nitido e la sua copia sfocata (R3 cap. 4, `sonda-bordo-maschera`). Una
 * passata sola a raggio variabile con pochi campioni a spirale non ha l'immagine doppia ma mostra il disegno dei
 * campioni sul testo (`sonda-bordo-scorrimento`). Due passate separabili, orizzontale e verticale, con il raggio che
 * cambia per pixel, sono una gaussiana vera a raggio variabile: un'immagine sola, sempre, che si fa nitida dove il
 * raggio va a zero. Il cancello C4 (S1) ha confermato le due passate.
 *
 * ### Il nucleo viene da Haze
 * I pesi e le posizioni dei campioni li calcola [GlassEdgeBlurKernel], portato da `SemanticBlurKernel` di Haze
 * (Chris Banes e i contributori di Haze, Apache-2.0), commit `95c0c48a915d3593e7f8d34039404f54f4e538e7`, file
 * `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/SemanticBlurKernel.kt`: al più venti coppie di
 * campioni più il centro per passata, ogni coppia al baricentro gaussiano di un gruppo di pixel; per i raggi piccoli
 * le posizioni si riscalano perché la varianza sia quella giusta. Come Haze applica una maschera di progressività
 * (`GlassShaders.buildBlur(progressive = true)`), qui il raggio di ogni pixel **scala le posizioni** del nucleo
 * calcolato per la sfocatura piena: la forma resta gaussiana a ogni raggio. Cosa abbiamo cambiato e perché sta in
 * `LICENSES/Haze.md`; il codice di Haze qui sotto è sotto Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0), il resto del file è dell'engine (GPL-3.0).
 */
package dev.antigravity.fluidengine.ui.glass.field

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCacheImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RuntimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.obtainEngineShader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------------------
// Il programma
// ---------------------------------------------------------------------------------------------------------

/**
 * Le coppie di campioni per passata, oltre al centro: 41 letture per pixel e per passata al più. È il tetto di Haze
 * (`SemanticBlurKernel.MAX_TAP_PAIRS`), e con due passate dà una gaussiana fino a [GlassEdgeBlurMaxRadiusPx].
 */
internal const val GlassEdgeBlurTapPairs: Int = 20

/**
 * Il raggio più grande che il nucleo copre senza che si veda il disegno dei campioni, in pixel **della cattura**.
 *
 * È il limite che Haze tiene per la sfocatura progressiva (`MAX_PROGRESSIVE_RADIUS_PX`): con venti coppie e sigma
 * 22,7 px ogni coppia sta per un gruppo di quattro pixel, e oltre i gruppi si allargano e su un testo nitido si
 * leggono le lettere ripetute. Una sfocatura più larga di così chiede la cattura a metà risoluzione (piano 3.10).
 */
internal const val GlassEdgeBlurMaxRadiusPx: Float = 38.5f

/**
 * Lo shader della striscia, **una sorgente per le due passate** (piano 3.4: una variante, direzione come uniform).
 *
 * Coordinate: quelle della cattura, che è la striscia stessa (nessun margine: sopra il bordo dello schermo non c'è
 * niente da campionare, e dove la striscia finisce il raggio è già zero). I campioni restano dentro `bounds`, come
 * fa Haze (`clampSample`): fuori la cattura è trasparente, e una lettura trasparente scurirebbe il bordo.
 *
 * Il profilo si legge in `d`, la distanza dal bordo dei tasti verso il contenuto, in altezze dei tasti (`H`):
 * negativa sotto i tasti e fino al bordo dello schermo, positiva sul contenuto che sta per entrare. `edgeLine` =
 * (dove sta il bordo dei tasti, in px; +1 se il contenuto sta sotto, −1 se sta sopra; `H` in px).
 *
 * Prima passata (`finish` = 0): sola sfocatura orizzontale. Seconda (`finish` = 1): sfocatura verticale, poi il
 * **velo** verso il colore del fondo (`veilColor`), lo **scurimento** del contenuto scuro e l'alfa d'uscita che si
 * dissolve dove la striscia finisce: lì sotto c'è la pagina nitida, e il passaggio non deve lasciare una riga. Dove
 * l'alfa scende il velo si divide per l'alfa, così il velo che si vede (striscia sopra pagina) resta quello del
 * profilo: senza, la dissolvenza lo accorcerebbe.
 *
 * Velo o scurimento: sul contenuto chiaro la striscia si scioglie nel colore del fondo, su quello scuro scurisce (R4
 * B7: «scurisce invece di schiarire», fiducia bassa). Nei fotogrammi di Apple (`f17058` contro `f17132`) la scelta è
 * di tutta la barra, insieme al giro dei tasti, e non pixel per pixel: una foto scura che entra sotto una pagina
 * chiara si vela, la stessa foto che riempie la barra si scurisce. Per questo il lato è uno uniform (`shade.x`, da 0
 * chiaro a 1 scuro), che dà chi conosce lo sfondo (la sonda di F2.1); `shade.y` è lo scurimento per unità di velo.
 * Una scelta per pixel lascerebbe senza velo il nero delle lettere dove la sfocatura è piccola.
 */
internal const val GlassEdgeBlurShaderString = """
uniform shader content;

uniform float2 axis;
uniform float4 bounds;
uniform float centerWeight;
uniform float2 taps[$GlassEdgeBlurTapPairs];
uniform float3 edgeLine;
uniform float4 blurRamp;
uniform float4 veilRamp;
layout(color) uniform half4 veilColor;
uniform float2 shade;
uniform float2 fadeRamp;
uniform float amount;
uniform float finish;

float2 clampToBounds(float2 p) {
    float2 lower = min(bounds.xy + float2(0.5), (bounds.xy + bounds.zw) * 0.5);
    float2 upper = max(bounds.zw - float2(0.5), (bounds.xy + bounds.zw) * 0.5);
    return clamp(p, lower, upper);
}

float edgeDepth(float2 p) {
    return (p.y - edgeLine.x) * edgeLine.y / max(edgeLine.z, 0.0001);
}

float blurWeight(float d) {
    float t = clamp((blurRamp.y - d) / max(blurRamp.y - blurRamp.x, 0.0001), 0.0, 1.0);
    float shaped = t > 0.0 ? pow(t, blurRamp.z) : 0.0;
    return shaped * blurRamp.w * amount;
}

float veilWeight(float d) {
    float x = max(veilRamp.y - d, 0.0);
    float raw = x > 0.0 ? veilRamp.x * pow(x, veilRamp.z) : 0.0;
    float knee = 0.12;
    float h = max(knee - abs(raw - veilRamp.w), 0.0) / knee;
    return max(min(raw, veilRamp.w) - h * h * knee * 0.25, 0.0) * amount;
}

half4 main(float2 coord) {
    float d = edgeDepth(coord);
    float s = blurWeight(d);
    float4 sum = float4(content.eval(clampToBounds(coord))) * centerWeight;
    if (s > 0.0001) {
        for (int i = 0; i < $GlassEdgeBlurTapPairs; i++) {
            float2 tap = taps[i];
            if (tap.y > 0.0) {
                float2 o = axis * (tap.x * s);
                sum += float4(content.eval(clampToBounds(coord - o))) * tap.y;
                sum += float4(content.eval(clampToBounds(coord + o))) * tap.y;
            }
        }
    } else {
        sum = float4(content.eval(clampToBounds(coord)));
    }
    if (finish < 0.5) {
        return half4(sum);
    }
    half4 c = half4(sum);
    float cover = 1.0;
    if (fadeRamp.y > fadeRamp.x) {
        cover = 1.0 - smoothstep(fadeRamp.x, fadeRamp.y, d);
    }
    float v = min(veilWeight(d) / max(cover, 0.0001), 1.0);
    half3 veiled = mix(c.rgb, veilColor.rgb * c.a, half(v * (1.0 - shade.x)));
    half3 rgb = veiled * half(1.0 - shade.y * v * shade.x);
    return half4(rgb, c.a) * half(cover * clamp(amount * 4.0, 0.0, 1.0));
}
"""

// ---------------------------------------------------------------------------------------------------------
// Il nucleo (portato da Haze)
// ---------------------------------------------------------------------------------------------------------

/**
 * Mezzo nucleo gaussiano limitato: il peso del centro e fino a [GlassEdgeBlurTapPairs] coppie (posizione, peso),
 * ognuna letta ai due segni. Sono i valori di `taps` e `centerWeight` del programma, calcolati **per la sfocatura
 * piena**: lo shader scala le posizioni per pixel.
 *
 * Portato da `SemanticBlurKernel` di Haze (Apache-2.0, commit `95c0c48`, vedi l'intestazione del file e
 * `LICENSES/Haze.md`). Cambiato: i nomi; le coppie in due array invece di una lista di oggetti (gli uniform si
 * scrivono così, e il calcolo non alloca liste); niente raggio di Compose (qui arriva la sigma).
 */
internal class GlassEdgeBlurKernel private constructor(
  val centerWeight: Float,
  /** Posizione di ogni coppia, in px della cattura; zero dove la coppia non c'è. */
  val offsets: FloatArray,
  /** Peso di ogni coppia (per **ciascuno** dei due campioni); zero dove la coppia non c'è. */
  val weights: FloatArray,
) {

  /** Varianza del nucleo: `2 Σ w x²`. Dice la sigma che il nucleo realizza davvero. */
  val variance: Float
    get() {
      var sum = 0.0
      for (i in offsets.indices) sum += 2.0 * weights[i] * offsets[i] * offsets[i]
      return sum.toFloat()
    }

  /** Le coppie come le vuole lo uniform `taps` (posizione, peso per coppia, in fila). */
  fun packedTaps(): FloatArray {
    val out = FloatArray(GlassEdgeBlurTapPairs * 2)
    for (i in 0 until GlassEdgeBlurTapPairs) {
      out[2 * i] = offsets[i]
      out[2 * i + 1] = weights[i]
    }
    return out
  }

  companion object {
    private const val SigmaSupport = 3f
    private const val MaxIntegrationSamplesPerTap = 8

    /** La sigma di HWUI e di Haze per un raggio: `0,57735 r + 0,5` (sotto un pixel, in proporzione). */
    fun radiusToSigma(radiusPx: Float): Float = radiusPx * 0.57735f + 0.5f * radiusPx.coerceIn(0f, 1f)

    /** Il raggio di cui [sigmaPx] è la sigma (l'inverso di [radiusToSigma] sopra il pixel). */
    fun sigmaToRadius(sigmaPx: Float): Float =
      if (sigmaPx <= 1.07735f) sigmaPx / 1.07735f else (sigmaPx - 0.5f) / 0.57735f

    /** Il nucleo dell'identità: il solo centro. */
    val Identity: GlassEdgeBlurKernel =
      GlassEdgeBlurKernel(1f, FloatArray(GlassEdgeBlurTapPairs), FloatArray(GlassEdgeBlurTapPairs))

    /** `SemanticBlurKernel.createForSigma` di Haze, riga per riga. */
    fun forSigma(sigmaPx: Float): GlassEdgeBlurKernel {
      if (!sigmaPx.isFinite() || sigmaPx <= 0f) return Identity
      val offsets = FloatArray(GlassEdgeBlurTapPairs)
      val weights = FloatArray(GlassEdgeBlurTapPairs)
      if (sigmaPx <= 0.5f) {
        val variance = sigmaPx * sigmaPx
        offsets[0] = 1f
        weights[0] = variance * 0.5f
        return GlassEdgeBlurKernel(1f - variance, offsets, weights)
      }
      val support = max(1, ceil((sigmaPx * SigmaSupport).toDouble()).toInt())
      // Ogni gruppo è sostituito dal suo baricentro gaussiano. I gruppi di due sono esatti con la lettura
      // bilineare; quelli più larghi sono un'approssimazione limitata (Haze).
      val samplesPerTap = max(2, ceil(support / GlassEdgeBlurTapPairs.toDouble()).toInt())
      val center = gaussianWeight(0.0, sigmaPx)
      var count = 0
      var start = 1
      var total = 0.0
      while (start <= support && count < GlassEdgeBlurTapPairs) {
        val end = min(start + samplesPerTap - 1, support)
        val sampleCount = min(end - start + 1, MaxIntegrationSamplesPerTap)
        val sampleWidth = (end - start + 1).toDouble() / sampleCount
        var weight = 0.0
        var weightedOffset = 0.0
        for (index in 0 until sampleCount) {
          val offset = start - 0.5 + (index + 0.5) * sampleWidth
          val sampleWeight = gaussianWeight(offset, sigmaPx) * sampleWidth
          weight += sampleWeight
          weightedOffset += offset * sampleWeight
        }
        offsets[count] = (weightedOffset / weight).toFloat()
        weights[count] = weight.toFloat()
        total += weight
        count++
        start = end + 1
      }
      val normalization = center + 2.0 * total
      for (i in 0 until count) weights[i] = (weights[i] / normalization).toFloat()
      val kernel = GlassEdgeBlurKernel((center / normalization).toFloat(), offsets, weights)
      if (sigmaPx <= 2f) {
        val offsetScale = sigmaPx / sqrt(kernel.variance)
        for (i in 0 until count) offsets[i] *= offsetScale
      }
      return kernel
    }

    private fun gaussianWeight(offset: Double, sigma: Float): Double {
      val normalized = offset / sigma
      return exp(-0.5 * normalized * normalized)
    }
  }
}

/**
 * La risoluzione della cattura della striscia: 1 = piena, 0,5 = metà. Il cancello C4 (S1) ha detto che sul tablet stanno
 * nel fotogramma tutte e due; la scelta è a occhio (piano 3.10, tavola «piena / metà» di F2.4, decisione di Alessio
 * aperta). La piena è il profilo di R4 senza aggiustamenti; la metà costa circa un settimo (un quarto dei pixel, e
 * metà dei campioni per pixel perché la sigma in px si dimezza) e per tornare nitida dove il testo deve esserlo si
 * dissolve sulla pagina da 0,45 H ([GlassScrollEdgeProfile.atResolution]).
 */
internal const val GlassScrollEdgeCaptureScale: Float = 1f

/** Il programma della striscia per la sonda di salute e per `obtainEngineShader`: un'istanza sola nel processo. */
internal val GlassEdgeBlurSource: GlassShaderSource =
  GlassShaderSource("GlassEdgeBlurShaderString", GlassEdgeBlurShaderString)

// ---------------------------------------------------------------------------------------------------------
// Il profilo, in altezze dei tasti
// ---------------------------------------------------------------------------------------------------------

/**
 * Dove la striscia sfoca e vela, in funzione di `d`: la distanza dal bordo dei tasti verso il contenuto, in altezze
 * dei tasti (`H`). Negativa sotto i tasti e fino al bordo dello schermo; positiva sul contenuto che sta per entrare.
 *
 * Perché in `H` e non in dp: R4 misura così (140 px del video = 1 H), e così una barra più alta (la tab bar, 62 dp)
 * si porta dietro un bordo proporzionato senza un secondo profilo.
 *
 * - **Sfocatura**: piena (sigma [blurSigma] `H`) da [blurFullAt] verso il bordo, zero da [blurZeroAt] verso il
 *   contenuto, in mezzo `t^`[blurShape] con `t` lineare. Lo spigolo in [blurFullAt] non si vede: è un raggio che
 *   smette di crescere, non una luminanza. [blurShare] a zero = sfuma senza sfocare.
 * - **Velo** verso il colore del fondo: [veilScale] `· (`[veilReach]` − d)^`[veilShape], con un tetto morbido a
 *   [veilCap] (un minimo liscio, così la rampa non ha spigoli e non disegna righe). Zero, con derivata zero, a
 *   [veilReach].
 * - **Scurimento** sul contenuto scuro ([darkDim]): quanto il contenuto scende al bordo dei tasti (`d` = 0) quando
 *   la striscia sta sul lato scuro; altrove segue il velo.
 * - **Dissolvenza** dell'alfa d'uscita fra [fadeFrom] e [fadeTo]: dove la striscia finisce si vede la pagina, e il
 *   passaggio da una all'altra non lascia righe. Il velo si divide per la copertura (nello shader), quindi la
 *   dissolvenza non lo accorcia.
 */
@Immutable
internal data class GlassScrollEdgeProfile(
  val blurFullAt: Float,
  val blurZeroAt: Float,
  val blurShape: Float,
  val blurSigma: Float,
  val blurShare: Float,
  val veilScale: Float,
  val veilReach: Float,
  val veilShape: Float,
  val veilCap: Float,
  val darkDim: Float,
  val fadeFrom: Float,
  val fadeTo: Float,
) {

  /** Quanto la striscia va oltre il bordo dei tasti, in `H`: dopo, la pagina è quella nuda. */
  val reach: Float get() = max(veilReach, max(blurZeroAt, fadeTo))

  /** Il velo senza tetto (lo stesso conto dello shader). */
  fun veilRaw(d: Float): Float {
    val x = max(veilReach - d, 0f)
    return if (x > 0f) veilScale * x.toDouble().pow(veilShape.toDouble()).toFloat() else 0f
  }

  /** Il velo a `d`, col tetto morbido dello shader. */
  fun veil(d: Float): Float {
    val raw = veilRaw(d)
    val h = max(VeilKnee - abs(raw - veilCap), 0f) / VeilKnee
    return max(min(raw, veilCap) - h * h * VeilKnee * 0.25f, 0f)
  }

  /** La quota della sfocatura piena a `d` (lo stesso conto dello shader, senza l'intensità). */
  fun blur(d: Float): Float {
    val t = ((blurZeroAt - d) / max(blurZeroAt - blurFullAt, 1e-4f)).coerceIn(0f, 1f)
    return if (t > 0f) t.toDouble().pow(blurShape.toDouble()).toFloat() * blurShare else 0f
  }

  /**
   * Lo stesso profilo stirato di [k] lungo `d`: più corto (`k` < 1) o più lungo (`k` > 1). Il velo a `k·d` vale quello
   * di serie a `d`, la sfocatura piena resta la stessa. Serve alla tavola «profilo di R4 / più corto / più lungo».
   */
  fun scaled(k: Float): GlassScrollEdgeProfile = copy(
    blurFullAt = blurFullAt * k,
    blurZeroAt = blurZeroAt * k,
    veilReach = veilReach * k,
    veilScale = veilScale / k.toDouble().pow(veilShape.toDouble()).toFloat(),
    fadeFrom = fadeFrom * k,
    fadeTo = fadeTo * k,
  )

  /**
   * Il profilo alla risoluzione di cattura [scale]. A metà risoluzione la cattura è già morbida (a densità 3 circa
   * 0,4 dp di sigma, che sul testo vale un quarto del contrasto): dove il testo deve tornare nitido la striscia si
   * dissolve sulla pagina per un tratto più lungo, da [HalfResolutionFadeFrom] (in proporzione a [veilReach]).
   */
  fun atResolution(scale: Float): GlassScrollEdgeProfile {
    if (scale >= 1f) return this
    val k = veilReach / Soft.veilReach
    return copy(fadeFrom = HalfResolutionFadeFrom * k, fadeTo = HalfResolutionFadeTo * k)
  }

  companion object {
    /** Il ginocchio del tetto del velo: sotto questa distanza dal tetto la rampa si piega in modo liscio. */
    const val VeilKnee: Float = 0.12f

    /** Vedi [atResolution]. Scelte sul banco il 2026-11-12 (F2.4): la coda torna nitida come a risoluzione piena. */
    const val HalfResolutionFadeFrom: Float = 0.45f
    const val HalfResolutionFadeTo: Float = 1.1f

    /**
     * Il bordo **morbido** di iOS 26 (`Automatic` in alto e `Soft`): R4 B7, fiducia alta, Posta su telefono
     * 9:07–9:41 (`f16636`–`f16700`, `f17058`), letto col metodo di R4 (inviluppo della deviazione di riga, massimo
     * mentre il testo scorre; velo come spostamento della luminanza media verso il fondo):
     *  - contrasto del testo pieno da 0,95 H sotto il bordo basso dei tasti, 50% a 0,43 H, 20% al bordo, 8% al
     *    centro dei tasti, 3–5% sopra: la sfocatura (sigma piena 0,084 H = 3,7 dp sotto tasti da 44 dp; 1,1 dp al
     *    bordo dei tasti, 0,5 dp a 0,43 H) più il velo;
     *  - velo verso il colore del fondo 36% a 0,17 H, 22% a 0,4 H, 13% a 0,6 H, 6% a 0,85 H, 0 a 1,1 H: la legge di
     *    potenza `0,4014 (1,1 − d)^1,5` passa per i cinque punti entro 1,5 punti percentuali;
     *  - su contenuto scuro scurisce, «≈0,8 del valore al bordo basso dei tasti» (R4 B7, fiducia **bassa**:
     *    stima indiretta da `f17132`);
     *  - il tetto del velo (0,7) e la sfocatura sopra i tasti R4 non li misura: 0,7 viene da `f16636` (il titolo
     *    al centro dei tasti, sfocato, sta a 190 fra il nero e la carta a 225), a occhio sul banco.
     * Le misure che ne escono, contro R4, sono nel rapporto di F2.4 (`artifacts/liquid-glass/bench/f2-4/`).
     */
    val Soft: GlassScrollEdgeProfile = GlassScrollEdgeProfile(
      blurFullAt = -1.5f,
      blurZeroAt = 1.05f,
      blurShape = 1.4f,
      blurSigma = 0.084f,
      blurShare = 1f,
      veilScale = 0.4014f,
      veilReach = 1.1f,
      veilShape = 1.5f,
      veilCap = 0.7f,
      darkDim = 0.2f,
      fadeFrom = 0.95f,
      fadeTo = 1.1f,
    )

    /**
     * Il bordo **duro** di iOS 26 (`Hard`): «fascia uniforme che nasconde tutto (contrasto 1%), taglio netto in meno
     * di 0,2 altezze di barra» (R4 B7, fiducia media, Finder 9:51–10:07, `f17800`–`f17990`). Stessa striscia, velo
     * quasi pieno fino al bordo dei tasti e una rampa di 0,15 H. La sfocatura sta **tutta dentro la parte velata**
     * (piena da −0,6 H, zero al bordo dei tasti): una sfocatura che andasse da piena a zero nel taglio tirerebbe in
     * verticale le aste delle lettere che entrano (la passata verticale legge righe che quella orizzontale ha lasciato
     * nitide: sul banco, righe verticali sopra il testo), e senza sfocatura il testo nitido sotto un velo del 95% si
     * legge ancora (F2.4, 2026-11-12).
     */
    val Hard: GlassScrollEdgeProfile = GlassScrollEdgeProfile(
      blurFullAt = -0.6f,
      blurZeroAt = 0f,
      blurShape = 1f,
      blurSigma = 0.084f,
      blurShare = 1f,
      veilScale = 16.7f,
      veilReach = 0.15f,
      veilShape = 1.5f,
      veilCap = 0.97f,
      darkDim = 0.2f,
      fadeFrom = 0.1f,
      fadeTo = 0.15f,
    )

    /**
     * In basso, «sfuma senza sfocare»: in iOS 26 il bordo basso dissolve nel colore del fondo e non sfoca (R1 E1,
     * osservato, fonte secondaria); in iOS 27 nessuna conferma (R5 E1, bassa). Il velo del bordo morbido, senza
     * sfocatura. È la variante A della tavola «in basso»; la B è nessun effetto (`Hidden`).
     */
    val FadeOnly: GlassScrollEdgeProfile = Soft.copy(blurShare = 0f)
  }
}

// ---------------------------------------------------------------------------------------------------------
// Gli uniform
// ---------------------------------------------------------------------------------------------------------

/**
 * Gli uniform della striscia, per le due passate: cambia solo `axis` (e `finish`). Un contenitore riusabile perché
 * il nodo li riscrive a ogni cambio d'intensità senza allocare; [glassScrollEdgeUniforms] ne fa uno nuovo per i test.
 */
internal class GlassScrollEdgeUniforms {
  var centerWeight: Float = 1f
  val taps: FloatArray = FloatArray(GlassEdgeBlurTapPairs * 2)
  val bounds: FloatArray = FloatArray(4)
  val edgeLine: FloatArray = FloatArray(3)
  val blurRamp: FloatArray = FloatArray(4)
  val veilRamp: FloatArray = FloatArray(4)
  var veilColor: Color = Color.Unspecified
  val shade: FloatArray = FloatArray(2)
  val fadeRamp: FloatArray = FloatArray(2)
  var amount: Float = 1f

  /**
   * Riempie gli uniform per una striscia larga [widthPx] e alta [heightPx] (px della cattura), con il bordo dei
   * tasti a [controlsPx] dal bordo dello schermo e tasti alti [unitPx]. [kernel] è il nucleo per
   * [sigmaFor]`(profile, unitPx)`: il nodo lo tiene da parte e lo rifà solo quando la sigma cambia.
   */
  fun fill(
    profile: GlassScrollEdgeProfile,
    top: Boolean,
    widthPx: Float,
    heightPx: Float,
    controlsPx: Float,
    unitPx: Float,
    veilColor: Color,
    darkContent: Float,
    amount: Float,
    kernel: GlassEdgeBlurKernel,
  ) {
    centerWeight = kernel.centerWeight
    for (i in 0 until GlassEdgeBlurTapPairs) {
      taps[2 * i] = kernel.offsets[i]
      taps[2 * i + 1] = kernel.weights[i]
    }
    // I campioni restano dentro il layer della cattura, che ha la taglia troncata al pixel (`refreshCapture`).
    bounds[0] = 0f
    bounds[1] = 0f
    bounds[2] = floor(widthPx)
    bounds[3] = floor(heightPx)
    edgeLine[0] = if (top) controlsPx else heightPx - controlsPx
    edgeLine[1] = if (top) 1f else -1f
    edgeLine[2] = unitPx
    blurRamp[0] = profile.blurFullAt
    blurRamp[1] = profile.blurZeroAt
    blurRamp[2] = profile.blurShape
    blurRamp[3] = profile.blurShare
    veilRamp[0] = profile.veilScale
    veilRamp[1] = profile.veilReach
    veilRamp[2] = profile.veilShape
    veilRamp[3] = profile.veilCap
    this.veilColor = veilColor
    val atEdge = profile.veil(0f)
    shade[0] = darkContent.coerceIn(0f, 1f)
    shade[1] = if (atEdge > 0f) profile.darkDim / atEdge else 0f
    fadeRamp[0] = profile.fadeFrom
    fadeRamp[1] = profile.fadeTo
    this.amount = amount.coerceIn(0f, 1f)
  }

  /** Scrive gli uniform nello shader per la passata [pass] (0 orizzontale, 1 verticale e finitura). */
  fun applyTo(shader: RuntimeShader, pass: Int) {
    shader.setFloatUniform("axis", if (pass == 0) 1f else 0f, if (pass == 0) 0f else 1f)
    shader.setFloatUniform("bounds", bounds)
    shader.setFloatUniform("centerWeight", centerWeight)
    shader.setFloatUniform("taps", taps)
    shader.setFloatUniform("edgeLine", edgeLine)
    shader.setFloatUniform("blurRamp", blurRamp)
    shader.setFloatUniform("veilRamp", veilRamp)
    shader.setColorUniform("veilColor", veilColor)
    shader.setFloatUniform("shade", shade)
    shader.setFloatUniform("fadeRamp", fadeRamp)
    shader.setFloatUniform("amount", amount)
    shader.setFloatUniform("finish", if (pass == 0) 0f else 1f)
  }

  companion object {
    /** La sigma piena in px della cattura per tasti alti [unitPx], col tetto del nucleo; zero se non sfoca. */
    fun sigmaFor(profile: GlassScrollEdgeProfile, unitPx: Float): Float {
      if (profile.blurShare <= 0f || unitPx <= 0f) return 0f
      val sigma = profile.blurSigma * unitPx
      return if (GlassEdgeBlurKernel.sigmaToRadius(sigma) > GlassEdgeBlurMaxRadiusPx) {
        GlassEdgeBlurKernel.radiusToSigma(GlassEdgeBlurMaxRadiusPx)
      } else {
        sigma
      }
    }
  }
}

/**
 * La catena della striscia per il riscaldamento (piano 3.10, cancello C4: il primo fotogramma lento della striscia in S1
 * era la compilazione a freddo del suo programma): le due passate del bordo morbido su un riquadro di [sidePx] px a
 * densità [density], come le costruisce il nodo. `null` se il compilatore rifiuta il programma.
 *
 * Esiste perché `GlassWarmUp` (fuori dal perimetro di F2.4) la registri con una riga, in un passo suo:
 * `frame.recordBody(glassScrollEdgeWarmEffect(d, side) ?: error(…), 0f, 1f)`.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun glassScrollEdgeWarmEffect(density: Float, sidePx: Int): android.graphics.RenderEffect? {
  val shader = RuntimeShaderCacheImpl().obtainEngineShader("GlassEdgeBlur", GlassEdgeBlurSource) ?: return null
  val side = sidePx.toFloat()
  val uniforms = glassScrollEdgeUniforms(
    profile = GlassScrollEdgeProfile.Soft,
    top = true,
    widthPx = side,
    heightPx = side,
    controlsPx = side * 0.5f,
    unitPx = 44f * density,
    veilColor = Color.White,
  )
  uniforms.applyTo(shader, pass = 0)
  val horizontal = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
  uniforms.applyTo(shader, pass = 1)
  val vertical = RuntimeShaderEffect(shader, "content").asAndroidRenderEffect()
  return android.graphics.RenderEffect.createChainEffect(vertical, horizontal)
}

/** Gli uniform di una striscia, nuovi: per i test e per le scene del banco. Il nodo usa [GlassScrollEdgeUniforms.fill]. */
internal fun glassScrollEdgeUniforms(
  profile: GlassScrollEdgeProfile,
  top: Boolean,
  widthPx: Float,
  heightPx: Float,
  controlsPx: Float,
  unitPx: Float,
  veilColor: Color,
  darkContent: Float = 0f,
  amount: Float = 1f,
): GlassScrollEdgeUniforms = GlassScrollEdgeUniforms().apply {
  val kernel = GlassEdgeBlurKernel.forSigma(GlassScrollEdgeUniforms.sigmaFor(profile, unitPx))
  fill(profile, top, widthPx, heightPx, controlsPx, unitPx, veilColor, darkContent, amount, kernel)
}
