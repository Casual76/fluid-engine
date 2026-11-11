/*
 * Il nucleo del campo del materiale `Liquid`: la sagoma del vetro come funzione
 * `punto -> (distanza con segno, normale, centro)`, in AGSL.
 *
 * Tutto ciò che il materiale disegna (maschera, lente, filo, riflessi, ombra in viaggio) parte da questa
 * funzione, e per questo sta in un file solo: corpo e luce sono due programmi diversi che devono vedere la
 * stessa identica sagoma, e l'unico modo di garantirlo è che includano lo stesso testo.
 *
 * Il campo a pezzi fusi è quello di `SlabGroupRefractionShaderString` (`fluidphysics/FluidPhysicsShaders.kt`),
 * e il preludio SDF deriva dal renderer di Kyant (AndroidLiquidGlass, Apache-2.0: vedi
 * `LICENSES/AndroidLiquidGlass.md`). Di nuovo qui c'è il **raccordo continuo** dell'angolo (vedi
 * [GlassFieldSlabBlock]).
 *
 * Discipline, ereditate da `FluidPhysicsShaders.kt` e non negoziabili:
 *  1. **Sorgenti costanti.** La cache degli shader ha per chiave il testo: qui ci sono solo `const val`
 *     composte con i template `$Nome`, che il banco (`tools/glassbench/kotlin_sources.py`) sa leggere. Una
 *     sorgente costruita da una funzione il banco non la vede.
 *  2. **Un passaggio per pixel.** Distanza, normale e centro escono dallo stesso ciclo; niente differenze
 *     finite, niente derivate (`dFdx` e `fwidth` in AGSL non esistono).
 *  3. **Cicli a limite costante** con uscita su uniform, **indici** costanti o l'indice del ciclo.
 *  4. **Distanze e coordinate in `float`**, `half` solo per i colori: `half` su molte GPU ha 16 bit, e una
 *     coordinata di un tablet non ci sta.
 *  5. **Nessun nome che sia una parola di GLSL o di SkSL.** `flat` ha tenuto mezzo engine in ripiego dalla
 *     3.0.0 alla 3.1.2. Per lo stesso motivo qui non c'è una variabile `smooth` (qualificatore di GLSL ES 3)
 *     né `sample`, `filter`, `input`, `output`: SkSL le lascerebbe passare, il driver non è detto.
 *
 * Questi sono **blocchi senza `main`**: da soli non si compilano e la sonda di salute non li prova. Li prova
 * chi li include (il corpo e la luce, registrati in `GlassShaderSources`), e fino ad allora le due sonde del
 * banco `tools/glassbench/shaders/prova-campo-1.agsl` e `prova-campo-8.agsl`, che `GlassFieldGeometryTest`
 * tiene uguali a queste costanti.
 */
package dev.antigravity.fluidengine.ui.glass.field

import org.intellij.lang.annotations.Language

/**
 * Quanti pezzi fonde al più un campo: la variante grande del nucleo ([GlassFieldKernel8]).
 *
 * Otto perché la barra di Apple che cambia contesto ne ha cinque o sei in transito, e perché la misura di S1
 * (cancello C3) dice che il ciclo a otto con uscita su `pieceCount` costa come uno tagliato sul conteggio:
 * per questo le varianti sono due, uno e otto, e non quattro.
 */
internal const val GlassFieldMaxPieces: Int = 8

/**
 * Di quanto la **normale** allarga l'angolo rispetto alla distanza.
 *
 * È il numero di Kyant (`gradRadius = radius * 1.5` nella lente di oggi), e non è un vezzo: la normale vera di
 * un angolo converge nel centro dell'angolo, e una lente spessa quanto il raggio ci mostrerebbe un punto in
 * cui il fondale si strizza. Calcolando la normale su un angolo una volta e mezza più largo (mai oltre mezzo
 * lato corto) quel punto finisce più in dentro della lente, e la rifrazione gira l'angolo senza pieghe. Una
 * capsula non cambia: il suo angolo è già largo mezzo lato.
 */
internal const val GlassFieldNormalSpread: Float = 1.5f

/**
 * La legge del raccordo continuo: da `smoothing` (0..1) alla superellisse che ne prende il posto.
 *
 * L'engine disegna i suoi angoli con la costruzione di Figma (`fluid/FluidShape.kt`): una curva che sale, un
 * arco di cerchio accorciato, una curva che scende. Un campo di distanza non può portarsi dietro tre cubiche
 * per angolo, quindi usa la curva più semplice che le segue: `|x|^n + |y|^n = a^n`, con
 *
 *     a = r * (1 + s² * (ReachSquare + ReachCube * s))        il semiasse, quanto lato consuma l'angolo
 *     n = 2 + s² * (PowerSquare + PowerCube * s)              l'esponente; 2 è il cerchio
 *
 * I quattro numeri vengono da una ricerca fatta per F1A.1 (`artifacts/liquid-glass/bench/f1a-1/studio_legge.py`
 * e `tabella_legge.py`, 2026-11-11): per ogni `s` fra 0,05 e 1 la superellisse che rende minimo lo scarto massimo dalla curva
 * dell'engine, misurato come **distanza vera** fra i due contorni, e poi il polinomio che passa per gli ottimi
 * di `s = 0,3` e `s = 0,6`. Lo scarto che resta, in frazioni del raggio:
 *
 * | `s` | arco di cerchio | questa legge | la superellisse migliore |
 * |---|---|---|---|
 * | 0,2 | 0,14% | 0,11% | 0,10% |
 * | 0,4 | 0,57% | 0,33% | 0,31% |
 * | 0,6 (iOS, il solo valore che l'engine usa) | 1,25% | 0,44% | 0,44% |
 * | 0,8 | 2,16% | 0,52% | 0,37% |
 * | 1,0 | 3,26% | 0,68% | 0,26% |
 *
 * A `s = 0,6` esce `a = 1,197 r`, `n = 2,50`: il piano (3.7) partiva da 1,18 e 2,45, che con la distanza vera
 * dà 0,48%. Lo scarto residuo oscilla cinque volte lungo l'angolo con la stessa ampiezza: è il minimo che una
 * curva a due parametri può dare, e tre famiglie a tre parametri provate non scendono sotto. La legge è
 * tarata fino a 0,6 perché l'engine non chiede altro (`ContinuousCornerShape.IosSmoothing`, che il lato corto
 * può solo ridurre); oltre resta buona, non ottima. `ContinuousCornerFieldTest` misura lo scarto a ogni giro
 * di test.
 *
 * `a` non supera mai `(1 + s) * r`, cioè il lato che l'emettitore dell'engine concede all'angolo: il raccordo
 * del campo sta sempre dentro il mezzo lato corto.
 */
internal const val GlassCornerReachSquare: Float = 0.25f

/** Vedi [GlassCornerReachSquare]. */
internal const val GlassCornerReachCube: Float = 0.495f

/** Vedi [GlassCornerReachSquare]. */
internal const val GlassCornerPowerSquare: Float = 0.79f

/** Vedi [GlassCornerReachSquare]. */
internal const val GlassCornerPowerCube: Float = 1.0f

/**
 * Una lastra: distanza con segno e normale di un rettangolo arrotondato con il raccordo continuo. È il
 * preludio SDF del materiale, uno solo (oggi ce ne sono due copie, in `backdrop/internal/Shaders.kt` e in
 * `fluidphysics/FluidPhysicsShaders.kt`).
 *
 * ### `float glassRadiusAt(float2 rel, float4 radii)`
 * Il raggio dell'angolo del quadrante in cui cade `rel`, che è la coordinata **centrata** sul pezzo (x verso
 * destra, y verso il basso). `radii` = alto-sinistra, alto-destra, basso-destra, basso-sinistra, cioè
 * l'ordine di `RoundRect`. Fino alla 3.1.2 gli shader di Kyant gli passavano la coordinata del layer e
 * sceglievano sempre l'angolo in basso a destra: un foglio arrotondato solo in alto aveva la lente quadrata.
 *
 * ### `float3 glassSlab(float2 rel, float2 halfSize, float4 radii, float smoothing)`
 * - `rel`: il punto, centrato sul pezzo, in pixel dello spazio dello shader;
 * - `halfSize`: mezza larghezza e mezza altezza del pezzo, in pixel;
 * - `radii`: i quattro raggi nominali, in pixel; ognuno è limitato qui a mezzo lato corto;
 * - `smoothing`: 0 = angolo circolare, 0,6 = il raccordo di iOS. È lo `smoothing` *chiesto*: la riduzione che
 *   impone il lato corto la fa questa funzione, con la regola dell'emettitore dell'engine
 *   (`continuousCornerSmoothing`), quindi una capsula resta un semicerchio esatto qualunque numero arrivi.
 *
 * Restituisce `x` = la distanza con segno dal contorno, in pixel (negativa dentro); `yz` = la normale, un
 * vettore unitario che punta fuori dal pezzo, calcolata sull'angolo allargato di [GlassFieldNormalSpread].
 *
 * Quanto è una distanza, la distanza: sui lati e con l'angolo circolare è esatta. Nell'angolo raccordato è la
 * norma della superellisse divisa per la lunghezza del suo gradiente, quindi è esatta **al primo ordine sul
 * contorno** (il filo e l'antialias hanno lo spessore giusto lungo tutto l'angolo) e torna alla norma semplice
 * verso il centro dell'angolo, dove dividere farebbe un salto. Misurato a `smoothing` 0,6 contro la distanza
 * vera (`artifacts/liquid-glass/bench/f1a-1/studio_metrica.py`): dentro, entro un quarto di raggio dal
 * contorno, l'errore sta sotto l'1,4% della distanza, e più in dentro sotto il 4,5%; fuori sotto lo 0,5% fino
 * a 1,2 raggi (è dove cade l'ombra in viaggio). La norma semplice sbaglierebbe del 6,7% ovunque, filo compreso.
 *
 * Due cose da sapere, misurate. Il raccordo **esce dal cerchio nominale** fino allo 0,44% del raggio attorno
 * alla diagonale (mezzo pixel a 114 px di raggio): chi tiene acceso un ritaglio hardware al raggio nominale
 * lo taglia lì. E `pow` non è uguale ovunque: Skia su CPU (il banco) lo approssima, e la sagoma resa si
 * scosta dalla formula fino a 0,03 px; una GPU fa meglio.
 *
 * ### `float glassCircleMap(float x)`
 * Il profilo dello spessore di Kyant: 0 dove il vetro è piatto, 1 sul bordo, con la salita di un quarto di
 * cerchio. Lo usa la lente; sta qui perché lo usano tutti i corpi.
 */
@Language("AGSL")
internal const val GlassFieldSlabBlock = """
float glassRadiusAt(float2 rel, float4 radii) {
    if (rel.x >= 0.0) {
        if (rel.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (rel.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float3 glassSlab(float2 rel, float2 halfSize, float4 radii, float smoothing) {
    float minHalf = min(halfSize.x, halfSize.y);
    float radius = max(min(glassRadiusAt(rel, radii), minHalf), 0.0);
    // Lo smoothing che il lato corto lascia spendere: zero quando l'angolo e' gia' un semicerchio.
    float s = radius > 0.0001 ? clamp(min(smoothing, minHalf / radius - 1.0), 0.0, 1.0) : 0.0;
    float s2 = s * s;
    float reach = radius * (1.0 + s2 * ($GlassCornerReachSquare + $GlassCornerReachCube * s));
    float power = 2.0 + s2 * ($GlassCornerPowerSquare + $GlassCornerPowerCube * s);
    float2 a = abs(rel);
    float2 side = float2(rel.x < 0.0 ? -1.0 : 1.0, rel.y < 0.0 ? -1.0 : 1.0);

    float2 q = a - (halfSize - float2(reach));
    float sd;
    if (q.x <= 0.0 || q.y <= 0.0) {
        sd = max(q.x, q.y) - reach;
    } else if (s <= 0.0) {
        sd = length(q) - reach;
    } else {
        float2 qp = pow(q, float2(power - 1.0));
        float sum = max(dot(qp, q), 1.0e-20);
        float norm = pow(sum, 1.0 / power);
        float slope = length(qp) * norm / sum;
        float m = norm - reach;
        sd = m / mix(slope, 1.0, clamp(-m / reach, 0.0, 1.0));
    }

    // La normale: lo stesso pezzo con l'angolo allargato, mai oltre mezzo lato corto.
    float wide = min(reach * $GlassFieldNormalSpread, minHalf);
    float2 w = a - (halfSize - float2(wide));
    float2 n;
    if (w.x <= 0.0 || w.y <= 0.0) {
        float gx = step(w.y, w.x);
        n = float2(gx, 1.0 - gx);
    } else if (s <= 0.0) {
        n = w / length(w);
    } else {
        float2 wp = pow(w, float2(power - 1.0));
        float wl = length(wp);
        n = wl > 1.0e-20 ? wp / wl : float2(0.70710678);
    }
    return float3(sd, side * n);
}

float glassCircleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}"""

/**
 * Il punto interrogato: dalla coordinata del layer al sistema del campo, con la flessione.
 *
 * ### uniform
 * - `float2 offset`: porta `coord` nel sistema dell'elemento. La cattura è più grande della lastra (taglia +
 *   2 x padding), quindi vale `-padding`, come nella lente di oggi (`backdrop/effects/Lens.kt`); zero per la
 *   luce, che si disegna sul nodo;
 * - `float4 flex`, `float2 flexCenter`: la matrice **inversa** 2x2 della flessione, per righe
 *   (`m00, m01, m10, m11`), attorno a `flexCenter` (pixel, sistema dell'elemento). A riposo è l'identità,
 *   `(1, 0, 0, 1)`: **va impostata sempre**, perché uno uniform non impostato vale zero e una matrice a zero
 *   schiaccia tutta la lastra in un punto. Chi la anima è F6.1.
 *
 * ### `float2 glassFieldPoint(float2 coord)`
 * `coord` è l'argomento di `main`. Restituisce `p`, il punto nel **sistema del campo**: pixel dello spazio
 * dello shader, origine nell'angolo in alto a sinistra dell'elemento, y verso il basso. È lo spazio di
 * `pieceRect`, di `glowCenter` e di `shadowOffset`, e l'argomento di `glassField`, `glassLight` e
 * `glassTravelShadow`. Lo sfondo invece si campiona sempre in `coord`: `content.eval(coord + spostamento)`.
 *
 * «Pixel dello spazio dello shader» non sono pixel dello schermo quando la cattura è a risoluzione ridotta:
 * ogni lunghezza che entra negli uniform passa dal fattore di scala (`toScopeSpace`), come oggi.
 */
@Language("AGSL")
internal const val GlassFieldPointBlock = """
uniform float2 offset;
uniform float4 flex;
uniform float2 flexCenter;

float2 glassFieldPoint(float2 coord) {
    float2 rel = coord + offset - flexCenter;
    return flexCenter + float2(dot(flex.xy, rel), dot(flex.zw, rel));
}"""

/**
 * Il nucleo a **un pezzo**: un rettangolo arrotondato, senza ciclo. È la sagoma di ogni lastra ferma che non
 * fa parte di un gruppo, cioè della grande maggioranza dei vetri di una schermata.
 *
 * ### uniform (gli stessi nomi e gli stessi tipi di [GlassFieldKernel8], con una casella sola)
 * `offset`, `flex`, `flexCenter` ([GlassFieldPointBlock]); `pieceCount`; `pieceRect[1]`; `pieceRadii[1]`;
 * `pieceBlend[1]`; `pieceSmooth[1]`. `pieceCount` e `pieceBlend` qui non servono e ci sono lo stesso: chi
 * imposta gli uniform fa le stesse chiamate per le due varianti, e uno uniform dichiarato e non usato si può
 * impostare (il bagliore del tocco lo fa da sempre con `size`), uno non dichiarato lancia.
 *
 * ### funzioni
 * Le stesse di [GlassFieldKernel8]: `glassFieldPoint`, `glassField`, più `glassSlab`, `glassRadiusAt` e
 * `glassCircleMap` di [GlassFieldSlabBlock]. Qui `glassField` restituisce il pezzo zero così com'è: `normal`
 * è unitaria, `center` è il centro del pezzo.
 */
@Language("AGSL")
internal const val GlassFieldKernel1 = """
$GlassFieldPointBlock

uniform float pieceCount;
uniform float4 pieceRect[1];
uniform float4 pieceRadii[1];
uniform float pieceBlend[1];
uniform float pieceSmooth[1];
$GlassFieldSlabBlock

float glassField(float2 p, out float2 normal, out float2 center) {
    float3 slab = glassSlab(p - pieceRect[0].xy, pieceRect[0].zw, pieceRadii[0], pieceSmooth[0]);
    normal = slab.yz;
    center = pieceRect[0].xy;
    return slab.x;
}"""

/**
 * Il nucleo a **otto pezzi** fusi: gruppi, palco di una trasformazione, viaggi fra lastre.
 *
 * ### uniform
 * - `offset`, `flex`, `flexCenter`: vedi [GlassFieldPointBlock];
 * - `float pieceCount`: quanti pezzi sono vivi, da 0 a [GlassFieldMaxPieces]. Il ciclo esce al primo indice
 *   che lo raggiunge: le caselle oltre non si leggono, qualunque cosa contengano. Con zero il campo è vuoto
 *   (distanza un milione);
 * - `float4 pieceRect[8]`: per pezzo, `xy` = il centro e `zw` = mezza larghezza e mezza altezza, in pixel del
 *   sistema del campo;
 * - `float4 pieceRadii[8]`: per pezzo i raggi nominali in pixel: alto-sinistra, alto-destra, basso-destra,
 *   basso-sinistra;
 * - `float pieceBlend[8]`: il raggio di fusione in pixel con cui il pezzo `i` si unisce **a ciò che il ciclo
 *   ha già fuso** (i pezzi da 0 a `i - 1`); la casella 0 non si legge. Zero = unione netta, senza ponte: è il
 *   valore di riposo fra due pezzi distinti (scelta di Alessio, 2026-11-11: il collo esiste solo in viaggio).
 *   Lo smooth minimum gonfia l'unione di un quarto di questo raggio dove due pezzi coincidono: chi prepara
 *   gli uniform lo riduce con il varco, come fa oggi `effectiveBlendRadius`;
 * - `float pieceSmooth[8]`: per pezzo lo `smoothing` dell'angolo, 0..1 (vedi [GlassFieldSlabBlock]). Zero in
 *   viaggio e per i piccoli che l'engine disegna già circolari, lo `smoothing` della forma per i grandi a
 *   riposo; fra i due sale in modo continuo, e la sagoma cambia senza scatti.
 *
 * ### `float glassField(float2 p, out float2 normal, out float2 center)`
 * `p` è il punto nel sistema del campo ([GlassFieldPointBlock]). Restituisce la distanza con segno dal
 * contorno della sagoma fusa, in pixel dello spazio dello shader: negativa dentro, zero sul contorno. E
 * scrive:
 * - `normal`: la direzione che esce dalla sagoma (la normale allargata di [GlassFieldSlabBlock]), mescolata
 *   fra i pezzi con lo stesso peso `h` dello smooth minimum. Su un pezzo solo è unitaria; nella zona di
 *   fusione è più corta di 1 e **chi la usa la normalizza**, dopo averci sommato ciò che vuole (la cupola);
 * - `center`: il centro del pezzo a cui il punto appartiene, in pixel del sistema del campo, mescolato allo
 *   stesso modo: serve alla cupola, perché in un gruppo ogni pezzo sia una lente sua e non uno spicchio di
 *   una lente grande quanto il gruppo.
 *
 * La copertura (la maschera con il suo antialias) non la calcola il nucleo: è
 * `clamp(0.5 - d / aa, 0.0, 1.0)`, e la applica chi disegna.
 *
 * Il gradiente mescolato con `h` è la derivata esatta della distanza fusa dentro la zona di fusione (i
 * termini in `∇h` si annullano: R3 cap. 2), a patto che le normali dei pezzi siano gradienti veri: con la
 * normale allargata lo è lontano dagli angoli.
 */
@Language("AGSL")
internal const val GlassFieldKernel8 = """
$GlassFieldPointBlock

uniform float pieceCount;
uniform float4 pieceRect[$GlassFieldMaxPieces];
uniform float4 pieceRadii[$GlassFieldMaxPieces];
uniform float pieceBlend[$GlassFieldMaxPieces];
uniform float pieceSmooth[$GlassFieldMaxPieces];
$GlassFieldSlabBlock

float glassField(float2 p, out float2 normal, out float2 center) {
    float d = 1000000.0;
    normal = float2(0.0);
    center = float2(0.0);
    for (int i = 0; i < $GlassFieldMaxPieces; ++i) {
        if (float(i) >= pieceCount) break;
        float3 slab = glassSlab(p - pieceRect[i].xy, pieceRect[i].zw, pieceRadii[i], pieceSmooth[i]);
        if (i == 0) {
            d = slab.x;
            normal = slab.yz;
            center = pieceRect[i].xy;
        } else {
            // Il raggio di fusione e' del pezzo: lo unisce a tutto cio' che il ciclo ha gia' fuso.
            float k = max(pieceBlend[i], 0.0001);
            float h = clamp(0.5 + 0.5 * (slab.x - d) / k, 0.0, 1.0);
            d = slab.x + (d - slab.x) * h - k * h * (1.0 - h);
            normal = mix(slab.yz, normal, h);
            center = mix(pieceRect[i].xy, center, h);
        }
    }
    return d;
}"""
