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

/** I nodi di una curva del velo nello shader ([GlassVeilCurve]): le curve più corte si riempiono col loro ultimo valore. */
internal const val GlassEdgeVeilKnots: Int = 10

/**
 * Mezzo livello (su 255) di grana sul velo: la rampa lenta della curva della foto, su una tinta unita a 8 bit, fa
 * scalini larghi qualche pixel; mezzo livello li scioglie senza vedersi (è lo stesso numero del corpo del vetro).
 */
internal const val GlassScrollEdgeGrain: Float = 0.5f

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
 *
 * Il velo (F2.5, giro sul velo, 2026-11-12) è una **curva per nodi** ([GlassVeilCurve], `veilKnots`: d, valore, tangente),
 * Hermite cubica monotona: niente spigoli, pendenza zero all'attacco e al tetto. Di serie è la curva dolce misurata su
 * una foto (`f17074`) su ogni contenuto; la manopola di debug `edgecurve=text` ci scrive quella ripida del testo (R4
 * B7). `veilKnob` = (scala del velo, tetto delle manopole; 1 = nessun tetto).
 *
 * La sfocatura è una curva dello stesso tipo (`blurKnots`, dal seguito di F2.5): la quota della sigma piena a `d`, per
 * poterla far crescere dietro i tasti senza toccarla sotto. `blurShare` a zero = sfuma senza sfocare.
 *
 * `split` (dal seguito di F2.5): fra due pagine che si muovono ognuna si porta la sua striscia. Fra `split.x` e `split.y`
 * (px) sta la pagina sopra e l'intensità è `split.w`, fuori quella sotto e vale `split.z`; con `split.x > split.y`
 * vale `amount` ovunque.
 *
 * `grain`: mezzo livello di grana fissa sul velo (la stessa formula del corpo del vetro, `GlassBodyShader`), contro gli
 * scalini a 8 bit di una rampa lenta su una tinta unita. Pesa zero dove il velo non c'è.
 */
internal const val GlassEdgeBlurShaderString = """
uniform shader content;

uniform float2 axis;
uniform float4 bounds;
uniform float centerWeight;
uniform float2 taps[$GlassEdgeBlurTapPairs];
uniform float3 edgeLine;
uniform float4 blurKnots[$GlassEdgeVeilKnots];
uniform float blurShare;
uniform float4 veilKnots[$GlassEdgeVeilKnots];
uniform float2 veilKnob;
layout(color) uniform half4 veilColor;
uniform float2 shade;
uniform float2 fadeRamp;
uniform float amount;
uniform float4 split;
uniform float grain;
uniform float finish;

float2 clampToBounds(float2 p) {
    float2 lower = min(bounds.xy + float2(0.5), (bounds.xy + bounds.zw) * 0.5);
    float2 upper = max(bounds.zw - float2(0.5), (bounds.xy + bounds.zw) * 0.5);
    return clamp(p, lower, upper);
}

float edgeDepth(float2 p) {
    return (p.y - edgeLine.x) * edgeLine.y / max(edgeLine.z, 0.0001);
}

float hermite(float4 a, float4 b, float d) {
    float h = b.x - a.x;
    float t = (d - a.x) / h;
    float t2 = t * t;
    float t3 = t2 * t;
    return (2.0 * t3 - 3.0 * t2 + 1.0) * a.y + (t3 - 2.0 * t2 + t) * h * a.z
        + (3.0 * t2 - 2.0 * t3) * b.y + (t3 - t2) * h * b.z;
}

float blurCurve(float d) {
    if (d <= blurKnots[0].x) return blurKnots[0].y;
    float v = blurKnots[$GlassEdgeVeilKnots - 1].y;
    for (int i = 0; i < $GlassEdgeVeilKnots - 1; i++) {
        float4 a = blurKnots[i];
        float4 b = blurKnots[i + 1];
        if (d >= a.x && d < b.x) v = hermite(a, b, d);
    }
    return v;
}

float blurWeight(float d, float a) {
    return max(blurCurve(d), 0.0) * blurShare * a;
}

float amountAt(float x) {
    if (split.x > split.y) return amount;
    return (x >= split.x && x <= split.y) ? split.w : split.z;
}

float veilCurve(float d) {
    if (d <= veilKnots[0].x) return veilKnots[0].y;
    float v = veilKnots[$GlassEdgeVeilKnots - 1].y;
    for (int i = 0; i < $GlassEdgeVeilKnots - 1; i++) {
        float4 a = veilKnots[i];
        float4 b = veilKnots[i + 1];
        if (d >= a.x && d < b.x) v = hermite(a, b, d);
    }
    return v;
}

float veilWeight(float d, float a) {
    float v = veilCurve(d);
    if (veilKnob.y < 0.999) {
        float knee = 0.12;
        float h = max(knee - abs(v - veilKnob.y), 0.0) / knee;
        v = max(min(v, veilKnob.y) - h * h * knee * 0.25, 0.0);
    }
    return max(v, 0.0) * veilKnob.x * a;
}

half4 main(float2 coord) {
    float d = edgeDepth(coord);
    float a = amountAt(coord.x);
    float s = blurWeight(d, a);
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
    float w = veilWeight(d, a);
    float v = min(w / max(cover, 0.0001), 1.0);
    half3 veiled = mix(c.rgb, veilColor.rgb * c.a, half(v * (1.0 - shade.x)));
    half3 rgb = veiled * half(1.0 - shade.y * v * shade.x);
    if (grain > 0.0 && w > 0.0) {
        float noise = fract(52.9829189 * fract(dot(coord, float2(0.06711056, 0.00583715)))) - 0.5;
        rgb = clamp(rgb + half(noise * grain * min(w * 50.0, 1.0) / 255.0) * c.a, half3(0.0), half3(c.a));
    }
    return half4(rgb, c.a) * half(cover * clamp(a * 4.0, 0.0, 1.0));
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
 * Una curva del velo, `v(d)`: monotona (decresce verso il contenuto), **liscia** e senza un inizio che si veda.
 *
 * Passa per i suoi punti con l'interpolazione cubica di Hermite **monotona** (tangenti di Fritsch–Carlson, la `pchip`
 * di MATLAB e SciPy): non oscilla fra i punti, non esce dall'intervallo dei valori, e la derivata non salta mai. Ai due
 * capi la pendenza è **zero**: il velo arriva al tetto senza un ginocchio e nasce dallo zero senza un attacco. Perché
 * conta: su una tinta unita uno spigolo della rampa (una derivata che salta) si legge come una riga, la banda di Mach;
 * con tratti dritti fra i punti ce ne sarebbe una a ogni punto. Prima di qui fuori dal primo punto vale il suo valore
 * (il tetto), dopo l'ultimo vale l'ultimo (zero).
 *
 * Lo shader la valuta con gli stessi nodi e le stesse tangenti ([writeKnots]): questa è la sua copia esatta, per i test
 * e per le misure. Lo specchio Python del banco (`tools/glassbench/scenes/f2/bordo-genera.py`) calcola le tangenti con
 * la stessa formula.
 *
 * @param points i punti (d in `H`, valore), con d crescente e valori che non crescono. Al più [GlassEdgeVeilKnots].
 */
@Immutable
internal class GlassVeilCurve(points: List<Pair<Float, Float>>) {
  val d: FloatArray = FloatArray(points.size) { points[it].first }
  val v: FloatArray = FloatArray(points.size) { points[it].second }

  /** La pendenza in ogni nodo (dv/dd, in velo per `H`): zero ai due capi, Fritsch–Carlson dentro. */
  val m: FloatArray = FloatArray(points.size)

  init {
    require(points.size in 2..GlassEdgeVeilKnots) { "una curva del velo ha da 2 a $GlassEdgeVeilKnots punti" }
    for (i in 1 until d.size) {
      require(d[i] > d[i - 1]) { "i punti del velo vanno per d crescente" }
      require(v[i] <= v[i - 1]) { "il velo non cresce verso il contenuto" }
    }
    for (k in 1 until d.size - 1) {
      val h0 = d[k] - d[k - 1]
      val h1 = d[k + 1] - d[k]
      val s0 = (v[k] - v[k - 1]) / h0
      val s1 = (v[k + 1] - v[k]) / h1
      m[k] = if (s0 == 0f || s1 == 0f || (s0 > 0f) != (s1 > 0f)) {
        0f
      } else {
        val w1 = 2f * h1 + h0
        val w2 = h1 + 2f * h0
        (w1 + w2) / (w1 / s0 + w2 / s1)
      }
    }
  }

  /** Il tetto: il valore prima del primo punto. */
  val top: Float get() = v.first()

  /** Dove la curva arriva al suo ultimo valore (lo zero): oltre, il velo non c'è. */
  val end: Float get() = d.last()

  /** Il velo a [x] (lo stesso conto dello shader). */
  fun at(x: Float): Float {
    if (x <= d.first()) return v.first()
    if (x >= d.last()) return v.last()
    var i = 0
    while (x >= d[i + 1]) i++
    return hermite(i, x)
  }

  /** La pendenza a [x], dv/dd: per i test (continua ovunque, zero ai capi). */
  fun slope(x: Float): Float {
    if (x <= d.first() || x >= d.last()) return 0f
    var i = 0
    while (x >= d[i + 1]) i++
    val h = d[i + 1] - d[i]
    val t = (x - d[i]) / h
    val t2 = t * t
    return ((6f * t2 - 6f * t) * v[i] + (3f * t2 - 4f * t + 1f) * h * m[i] +
      (6f * t - 6f * t2) * v[i + 1] + (3f * t2 - 2f * t) * h * m[i + 1]) / h
  }

  private fun hermite(i: Int, x: Float): Float {
    val h = d[i + 1] - d[i]
    val t = (x - d[i]) / h
    val t2 = t * t
    val t3 = t2 * t
    return (2f * t3 - 3f * t2 + 1f) * v[i] + (t3 - 2f * t2 + t) * h * m[i] +
      (3f * t2 - 2f * t3) * v[i + 1] + (t3 - t2) * h * m[i + 1]
  }

  /** La stessa curva stirata di [k] lungo d (più corta con `k` < 1): stessi valori, pendenze divise per [k]. */
  fun scaled(k: Float): GlassVeilCurve = GlassVeilCurve(List(d.size) { d[it] * k to v[it] })

  /**
   * I nodi per lo uniform `veilKnots` (d, valore, pendenza, 0 per nodo), in [out] da [GlassEdgeVeilKnots] × 4 float.
   * I posti in più ripetono l'ultimo valore a d crescenti, con pendenza zero: per lo shader sono tratti piatti.
   */
  fun writeKnots(out: FloatArray) {
    for (i in 0 until GlassEdgeVeilKnots) {
      val j = min(i, d.size - 1)
      out[4 * i] = if (i < d.size) d[i] else d.last() + (i - d.size + 1)
      out[4 * i + 1] = v[j]
      out[4 * i + 2] = if (i < d.size) m[i] else 0f
      out[4 * i + 3] = 0f
    }
  }

  override fun equals(other: Any?): Boolean =
    other is GlassVeilCurve && d.contentEquals(other.d) && v.contentEquals(other.v)

  override fun hashCode(): Int = 31 * d.contentHashCode() + v.contentHashCode()

  override fun toString(): String = d.indices.joinToString(", ", "GlassVeilCurve(", ")") { "${d[it]}→${v[it]}" }

  companion object {
    /**
     * La curva **dolce**, di serie su ogni contenuto (scelta di Alessio del 2026-11-12: «anche nel testo, se la fai più
     * dolce va bene»). Misurata su una foto in iOS 26 (WWDC25 219, `f17074`, la stessa foto seguita fra `f17040` e
     * `f17140`, lontano dal bordo della pagina bianca sopra): 2% a +1 H, 5% a +0,7, 10% a +0,4, 18% a +0,2, 26% al bordo
     * basso dei tasti, 32% a −0,4 H dietro i tasti, poi 37–39% più su, che il tetto di 0,36 (la colonna B della tavola,
     * scelta da Alessio) taglia: il tetto si raggiunge a −0,65 H. L'attacco è a +1,35 H, un poco sotto la misura di
     * +1 H, perché lo zero arrivi con pendenza zero.
     */
    val Photo: GlassVeilCurve = GlassVeilCurve(
      listOf(
        -0.65f to 0.36f,
        -0.4f to 0.32f,
        0f to 0.26f,
        0.2f to 0.18f,
        0.4f to 0.10f,
        0.7f to 0.05f,
        1f to 0.02f,
        1.35f to 0f,
      ),
    )

    /**
     * La curva **ripida** del testo, R4 B7 (Posta, `f17058`/`f17046`, fiducia alta): 36% a 0,17 H, 22% a 0,4, 13% a 0,6,
     * 6% a 0,85, zero a 1,1 H; sopra 0,17 H il tetto di 0,36. Non è più di serie: la tiene la manopola di debug
     * `edgecurve=text`, per riguardarla.
     */
    val Text: GlassVeilCurve = GlassVeilCurve(
      listOf(
        0.17f to 0.36f,
        0.4f to 0.22f,
        0.6f to 0.13f,
        0.85f to 0.06f,
        1.1f to 0f,
      ),
    )

    /** Il velo del bordo **duro**: pieno (0,97) fino al bordo dei tasti, zero a 0,15 H, con pendenza zero ai due capi. */
    val Hard: GlassVeilCurve = GlassVeilCurve(listOf(0f to 0.97f, 0.15f to 0f))
  }
}

/**
 * Dove la striscia sfoca e vela, in funzione di `d`: la distanza dal bordo dei tasti verso il contenuto, in altezze
 * dei tasti (`H`). Negativa sotto i tasti e fino al bordo dello schermo; positiva sul contenuto che sta per entrare.
 *
 * Perché in `H` e non in dp: R4 misura così (140 px del video = 1 H), e così una barra più alta (la tab bar, 62 dp)
 * si porta dietro un bordo proporzionato senza un secondo profilo.
 *
 * - **Sfocatura**: la curva [blur] ([GlassVeilCurve]) dà la quota della sigma piena ([blurSigma] `H`) a ogni `d`: 1
 *   verso il bordo dello schermo, zero dove il contenuto torna nitido. [blurShare] a zero = sfuma senza sfocare.
 * - **Velo** verso il colore del fondo: la curva [veil] ([GlassVeilCurve]); [veilText] è quella che la manopola di
 *   debug `edgecurve=text` mette al suo posto.
 * - **Scurimento** sul contenuto scuro ([darkDim]): quanto il contenuto scende al bordo dei tasti (`d` = 0) quando
 *   la striscia sta sul lato scuro; altrove segue il velo.
 * - **Dissolvenza** dell'alfa d'uscita fra [fadeFrom] e [fadeTo]: dove la striscia finisce si vede la pagina, e il
 *   passaggio da una all'altra non lascia righe. Il velo si divide per la copertura (nello shader), quindi la
 *   dissolvenza non lo accorcia; per questo sta oltre la fine del velo.
 */
@Immutable
internal data class GlassScrollEdgeProfile(
  val blur: GlassVeilCurve,
  val blurSigma: Float,
  val blurShare: Float,
  val veil: GlassVeilCurve,
  val veilText: GlassVeilCurve,
  val darkDim: Float,
  val fadeFrom: Float,
  val fadeTo: Float,
) {

  /** Dove finisce il velo, con l'una o l'altra curva. */
  val veilEnd: Float get() = max(veil.end, veilText.end)

  /** Quanto la striscia va oltre il bordo dei tasti, in `H`: dopo, la pagina è quella nuda. */
  val reach: Float get() = max(veilEnd, max(blur.end, fadeTo))

  /** Il velo a `d` (lo stesso conto dello shader, senza manopole); [text] = la curva di `edgecurve=text`. */
  fun veil(d: Float, text: Boolean = false): Float = (if (text) veilText else veil).at(d)

  /** La quota della sfocatura piena a `d` (lo stesso conto dello shader, senza l'intensità). */
  fun blur(d: Float): Float = blur.at(d) * blurShare

  /**
   * Lo stesso profilo stirato di [k] lungo `d`: più corto (`k` < 1) o più lungo (`k` > 1). Il velo a `k·d` vale quello
   * di serie a `d`, la sfocatura piena resta la stessa. Serve alla tavola «profilo di serie / più corto / più lungo».
   */
  fun scaled(k: Float): GlassScrollEdgeProfile = copy(
    blur = blur.scaled(k),
    veil = veil.scaled(k),
    veilText = veilText.scaled(k),
    fadeFrom = fadeFrom * k,
    fadeTo = fadeTo * k,
  )

  /**
   * Il profilo alla risoluzione di cattura [scale]. A metà risoluzione la cattura è già morbida (a densità 3 circa
   * 0,4 dp di sigma, che sul testo vale un quarto del contrasto): dove il testo deve tornare nitido la striscia si
   * dissolve sulla pagina per un tratto più lungo, da [HalfResolutionFadeFrom] (in proporzione alla fine del velo).
   */
  fun atResolution(scale: Float): GlassScrollEdgeProfile {
    if (scale >= 1f) return this
    val k = veilEnd / Soft.veilEnd
    return copy(fadeFrom = HalfResolutionFadeFrom * k, fadeTo = HalfResolutionFadeTo * k)
  }

  companion object {
    /**
     * La sigma piena del bordo morbido, in `H`: 0,16 H, 7 dp sotto tasti da 44 dp (dal seguito di F2.5; era 0,084 H). La
     * raggiunge solo in cima, sopra −1,5 H.
     */
    const val SoftBlurSigma: Float = 0.16f

    /**
     * La sfocatura del bordo morbido, in quote di [SoftBlurSigma] (dal seguito di F2.5, 2026-11-12): dietro i tasti
     * cresce fino a togliere al testo il contrasto che R4 B7 misura in Posta (`f16636`–`f16700`, fiducia alta) — 3–5%
     * sopra i tasti, 8% al loro centro — con la sola sfocatura, perché il velo resta la curva dolce scelta da Alessio
     * (tetto 0,36) e da solo non lo fa (col velo a 0,34 una riga si intuiva ancora dietro la metà bassa dei tasti: 13,9%
     * a −0,5 H sul banco). Sul banco: 3,4% a −1,5 H, 4,1% a −1 H, 7,4% a −0,5 H (scuro 3,8 / 4,5 / 8,4).
     *
     * Dal bordo dei tasti in giù è **la sfocatura di prima**, in valore assoluto (1,06 dp al bordo, 0,8 a +0,2 H, 0,51
     * a +0,43 H, zero a +1,05 H): lì il testo deve restare leggibile com'era (26,8% al bordo, 60,6% a 0,43 H, pieno da
     * 0,95 H). Per questo i nodi da 0 in giù sono la rampa di prima, `0,084 · ((1,05 − d)/2,55)^1,4`, divisa per la
     * sigma nuova.
     */
    val SoftBlur: GlassVeilCurve = GlassVeilCurve(
      listOf(
        -1.5f to 1f,
        -1f to 0.875f,
        -0.5f to 0.5f,
        -0.25f to 0.28125f,
        0f to 0.15125f,
        0.2f to 0.11275f,
        0.43f to 0.07256f,
        0.75f to 0.02578f,
        1.05f to 0f,
      ),
    )

    /** Vedi [atResolution]. Scelte sul banco il 2026-11-12 (F2.4): la coda torna nitida come a risoluzione piena. */
    const val HalfResolutionFadeFrom: Float = 0.45f
    const val HalfResolutionFadeTo: Float = 1.4f

    /**
     * Il bordo **morbido** di iOS 26 (`Automatic` in alto e `Soft`):
     *  - **sfocatura** da R4 B7 (Posta su telefono 9:07–9:41, `f16636`–`f16700`, fiducia alta): contrasto del testo
     *    pieno da 0,95 H sotto il bordo basso dei tasti, 50% a 0,43 H, 20% al bordo, 8% al centro dei tasti, 3–5%
     *    sopra ([SoftBlur], sigma piena [SoftBlurSigma]). Sul testo il contrasto lo toglie la sfocatura, non il velo:
     *    per questo il velo dolce regge anche lì;
     *  - **velo**: la curva dolce della foto ([GlassVeilCurve.Photo]) col tetto di 0,36 (decisione di Alessio del
     *    2026-11-12 sulla tavola del velo: «B», con il velo progressivo); fino a quel giorno era `0,4014 (1,1 − d)^1,5`
     *    di R4 col tetto di 0,7, che non veniva da una misura di iOS 26;
     *  - **scurimento** sul contenuto scuro: 0,2 al bordo dei tasti (R4 B7, «≈0,8 del valore», fiducia bassa); con la
     *    curva della foto diventa ×0,80 al bordo e ×0,72 dietro i tasti, e Apple (`f17140`, la foto che riempie la
     *    barra) fa ×0,8 e ×0,70–0,72;
     *  - **dissolvenza** dell'alfa fra 1,3 e 1,4 H, oltre la fine del velo (1,35 H).
     */
    val Soft: GlassScrollEdgeProfile = GlassScrollEdgeProfile(
      blur = SoftBlur,
      blurSigma = SoftBlurSigma,
      blurShare = 1f,
      veil = GlassVeilCurve.Photo,
      veilText = GlassVeilCurve.Text,
      darkDim = 0.2f,
      fadeFrom = 1.3f,
      fadeTo = 1.4f,
    )

    /**
     * Il bordo **duro** di iOS 26 (`Hard`): «fascia uniforme che nasconde tutto (contrasto 1%), taglio netto in meno
     * di 0,2 altezze di barra» (R4 B7, fiducia media, Finder 9:51–10:07, `f17800`–`f17990`). Stessa striscia, velo
     * quasi pieno fino al bordo dei tasti e una rampa di 0,15 H ([GlassVeilCurve.Hard]). La sfocatura sta **tutta
     * dentro la parte velata** (piena da −0,6 H, zero al bordo dei tasti): una sfocatura che andasse da piena a zero
     * nel taglio tirerebbe in verticale le aste delle lettere che entrano (la passata verticale legge righe che quella
     * orizzontale ha lasciato nitide: sul banco, righe verticali sopra il testo), e senza sfocatura il testo nitido
     * sotto un velo del 95% si legge ancora (F2.4, 2026-11-12).
     */
    val Hard: GlassScrollEdgeProfile = GlassScrollEdgeProfile(
      blur = GlassVeilCurve(listOf(-0.6f to 1f, -0.3f to 0.5f, 0f to 0f)),
      blurSigma = 0.084f,
      blurShare = 1f,
      veil = GlassVeilCurve.Hard,
      veilText = GlassVeilCurve.Hard,
      darkDim = 0.2f,
      fadeFrom = 0.1f,
      fadeTo = 0.15f,
    )

    /**
     * In basso, «sfuma senza sfocare»: in iOS 26 il bordo basso dissolve nel colore del fondo e non sfoca (R1 E1,
     * osservato, fonte secondaria); in iOS 27 nessuna conferma (R5 E1, bassa). Il velo del bordo morbido (la stessa
     * curva dolce, tetto 0,36), senza sfocatura. Scelta di Alessio del 2026-11-12 (tavola «in basso»: A).
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
  val blurKnots: FloatArray = FloatArray(GlassEdgeVeilKnots * 4)
  var blurShare: Float = 1f
  val veilKnots: FloatArray = FloatArray(GlassEdgeVeilKnots * 4)
  val veilKnob: FloatArray = FloatArray(2)
  var veilColor: Color = Color.Unspecified
  val shade: FloatArray = FloatArray(2)
  val fadeRamp: FloatArray = FloatArray(2)
  var amount: Float = 1f
  var grain: Float = GlassScrollEdgeGrain

  /** Il taglio fra due pagine ([GlassScrollEdgeSplit]): (da, a, intensità fuori, intensità dentro); spento di serie. */
  val split: FloatArray = floatArrayOf(1f, 0f, 0f, 0f)

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
    veilScale: Float = GlassScrollEdgeTuning.veilScale(top),
    veilCeiling: Float = GlassScrollEdgeTuning.veilCeiling,
    textCurve: Boolean = GlassScrollEdgeTuning.textCurve,
    split: GlassScrollEdgeSplit? = null,
    darkDim: Float = GlassScrollEdgeTuning.darkDim ?: profile.darkDim,
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
    profile.blur.writeKnots(blurKnots)
    blurShare = profile.blurShare
    // La curva del velo: quella del profilo, o quella del testo se la manopola `edgecurve=text` la chiede. Le altre
    // manopole ([GlassScrollEdgeTuning]) la scalano e la tagliano; a 1 i numeri sono quelli del profilo, al bit.
    val curve = if (textCurve) profile.veilText else profile.veil
    curve.writeKnots(veilKnots)
    veilKnob[0] = veilScale
    veilKnob[1] = veilCeiling
    grain = GlassScrollEdgeGrain
    this.veilColor = veilColor
    val atEdge = curve.at(0f)
    shade[0] = darkContent.coerceIn(0f, 1f)
    // Lo scurimento del profilo, o quello della manopola `edgedim` (S3): senza manopola è quello del profilo, al bit.
    shade[1] = if (atEdge > 0f) darkDim / atEdge else 0f
    fadeRamp[0] = profile.fadeFrom
    fadeRamp[1] = profile.fadeTo
    this.amount = amount.coerceIn(0f, 1f)
    if (split != null && split.to >= split.from) {
      this.split[0] = split.from
      this.split[1] = split.to
      this.split[2] = split.outside.coerceIn(0f, 1f)
      this.split[3] = split.inside.coerceIn(0f, 1f)
    } else {
      this.split[0] = 1f
      this.split[1] = 0f
      this.split[2] = 0f
      this.split[3] = 0f
    }
  }

  /** Scrive gli uniform nello shader per la passata [pass] (0 orizzontale, 1 verticale e finitura). */
  fun applyTo(shader: RuntimeShader, pass: Int) {
    shader.setFloatUniform("axis", if (pass == 0) 1f else 0f, if (pass == 0) 0f else 1f)
    shader.setFloatUniform("bounds", bounds)
    shader.setFloatUniform("centerWeight", centerWeight)
    shader.setFloatUniform("taps", taps)
    shader.setFloatUniform("edgeLine", edgeLine)
    shader.setFloatUniform("blurKnots", blurKnots)
    shader.setFloatUniform("blurShare", blurShare)
    shader.setFloatUniform("veilKnots", veilKnots)
    shader.setFloatUniform("veilKnob", veilKnob)
    shader.setFloatUniform("grain", grain)
    shader.setColorUniform("veilColor", veilColor)
    shader.setFloatUniform("shade", shade)
    shader.setFloatUniform("fadeRamp", fadeRamp)
    shader.setFloatUniform("amount", amount)
    shader.setFloatUniform("split", split)
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
 * Il taglio della striscia fra due pagine che si muovono (dal seguito di F2.5): fra [from] e [to] (px della striscia)
 * sta la pagina sopra, con la sua intensità [inside]; fuori quella sotto, con [outside]. Così la striscia di una pagina
 * che esce se ne va con lei, invece di spegnersi tutta insieme con la corsa (che è veloce all'inizio).
 */
internal class GlassScrollEdgeSplit(
  @JvmField var from: Float = 0f,
  @JvmField var to: Float = -1f,
  @JvmField var outside: Float = 0f,
  @JvmField var inside: Float = 0f,
)

/**
 * Le manopole di debug del **velo** della striscia (F2.5, giro sul velo del 2026-11-12): per far scegliere ad Alessio
 * quanto il contenuto si scioglie nel colore del fondo, sul tablet e senza ricompilare. Le installa
 * `FluidGlassDiagnostics.tuneLiquidProfile` insieme a quelle del profilo (`gallery.tune edgeveil=0.5,edgecap=0.36`), e
 * valgono solo in un'app `debuggable`. Si leggono quando la striscia riempie i suoi uniform, cioè al disegno.
 *
 * - `edgeveil` scala il velo della striscia **in alto** (curva e tetto insieme): 1 = il profilo, 0 = nessun velo
 *   (resta la sfocatura).
 * - `edgefade` scala la dissolvenza **in basso** allo stesso modo: 1 = il profilo, 0 = niente in basso.
 * - `edgecap` è un tetto in più, prima della scala, per tutte e due: 1 = nessuno (il tetto di serie, 0,36, è già nella
 *   curva: [GlassVeilCurve.Photo]). Col tetto della curva a 0,7, fino al primo giro sul velo, serviva per la colonna B.
 * - `edgecurve` sceglie la curva: `photo` (di serie: la curva dolce misurata su una foto, su ogni contenuto) o `text`
 *   (quella ripida di R4 B7, per riguardarla).
 *
 * Senza manopole (o con `null`) tutto torna a 1: i numeri della striscia sono quelli di serie, al bit.
 */
internal object GlassScrollEdgeTuning {

  const val VeilKnob: String = "edgeveil"
  const val FadeKnob: String = "edgefade"
  const val CapKnob: String = "edgecap"
  const val CurveKnob: String = "edgecurve"

  /**
   * S3: lo **scurimento** della striscia sul lato scuro ([GlassScrollEdgeProfile.darkDim]) come numero assoluto,
   * 0..1: quanto scende il contenuto al bordo dei tasti. Di serie 0,2 (×0,80 al bordo e ×0,72 dietro i tasti); più
   * grande, striscia più scura sopra una foto a metà luma (il titolo bianco di F2.3c a 2,7–3,0:1).
   */
  const val DimKnob: String = "edgedim"

  /** Le manopole e i valori che accettano, come le elenca `FluidGlassDiagnostics.liquidProfileKnobs`. */
  val Knobs: Map<String, List<String>> = mapOf(
    VeilKnob to listOf("0..1"),
    FadeKnob to listOf("0..1"),
    CapKnob to listOf("0..1"),
    CurveKnob to listOf("photo", "text"),
    DimKnob to listOf("0..1"),
  )

  @Volatile
  var topVeil: Float = 1f
    private set

  @Volatile
  var bottomFade: Float = 1f
    private set

  @Volatile
  var veilCeiling: Float = 1f
    private set

  /** Vero se la manopola `edgecurve=text` chiede la curva ripida del testo al posto di quella di serie. */
  @Volatile
  var textCurve: Boolean = false
    private set

  /** Lo scurimento chiesto da `edgedim`, o `null`: quello del profilo. */
  @Volatile
  var darkDim: Float? = null
    private set

  /** La scala del velo per il lato alto ([top]) o basso. */
  fun veilScale(top: Boolean): Float = if (top) topVeil else bottomFade

  /**
   * Prende le manopole da [knobs] (nomi senza distinzione di maiuscole, numeri fra 0 e 1, col punto o la virgola) e le
   * applica; quelle che mancano tornano a 1 (`edgedim` allo scurimento del profilo). Restituisce quelle prese, coi valori normalizzati; una manopola con un
   * valore che non si riconosce resta a 1 e non compare.
   */
  fun apply(knobs: Map<String, String>?): Map<String, String> {
    val taken = LinkedHashMap<String, String>()
    fun take(name: String): Float {
      val raw = knobs?.entries?.firstOrNull { it.key.trim().lowercase() == name }?.value ?: return 1f
      val value = glassScrollEdgeKnobValue(raw) ?: return 1f
      taken[name] = glassScrollEdgeKnobText(value)
      return value
    }
    topVeil = take(VeilKnob)
    bottomFade = take(FadeKnob)
    veilCeiling = take(CapKnob)
    val curve = knobs?.entries?.firstOrNull { it.key.trim().lowercase() == CurveKnob }?.value?.trim()?.lowercase()
    textCurve = curve == "text"
    if (curve == "text" || curve == "photo") taken[CurveKnob] = curve
    // Lo scurimento non è una scala: senza manopola resta quello del profilo (null), non 1.
    darkDim = knobs?.entries?.firstOrNull { it.key.trim().lowercase() == DimKnob }?.value
      ?.let(::glassScrollEdgeKnobValue)
      ?.also { taken[DimKnob] = glassScrollEdgeKnobText(it) }
    return taken
  }
}

/** Il numero di una manopola del velo: fra 0 e 1, col punto o la virgola; `null` se non lo è. Pura, per il test. */
internal fun glassScrollEdgeKnobValue(raw: String): Float? {
  val value = raw.trim().replace(',', '.').toFloatOrNull() ?: return null
  return if (value.isFinite() && value in 0f..1f) value else null
}

/** Come la riga `PROFILO` scrive un numero di manopola: senza zeri in coda (`0.5`, `0.36`, `1`). */
internal fun glassScrollEdgeKnobText(value: Float): String {
  val rounded = Math.round(value * 1000f) / 1000f
  return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
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
