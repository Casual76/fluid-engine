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
 * Da quanto oltre mezzo lato corto un raggio manda la lastra al **ramo largo** di `glassSlab`
 * ([GlassFieldSlabBlock]): un decimillesimo, lo stesso margine con cui la regola forma → piano chiama
 * «a mezzo lato» un raggio che ci arriva con l'errore di un `float` (`GlassFieldPlan.kt`). Sotto, il ramo di
 * sempre limita il raggio a mezzo lato: una capsula non cambia strada per un milionesimo.
 */
internal const val GlassFieldWideSlack: Float = 1.0001f

/**
 * Quanti vertici porta al più l'anello del nucleo [GlassFieldKernelRing]: quelli con cui Fluid-physics disegna
 * la goccia (`DropRenderPoints`, `PhysicsMaxRingVertices`), più dei 64 di una sagoma libera perché gli angoli
 * di un pannello restino definiti mentre viaggia.
 */
internal const val GlassFieldRingVertices: Int = 96

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
 * ### Il ramo largo: un angolo oltre mezzo lato corto (F1B.1)
 * Compose disegna un angolo più largo di mezzo lato corto quando l'angolo accanto gli lascia il posto (un
 * fumetto di chat `22/22/22/6` alto 40 dp ha l'angolo in alto a sinistra a 22). Lì il quadrante in cui cade il
 * punto non dice più di quale angolo è: l'arco da 22 scende sotto la metà dell'altezza. Fino a F1B.1 il campo
 * limitava il raggio e la forma restava `Classic`: due vetri nella stessa chat.
 *
 * Se almeno un raggio supera mezzo lato corto (di [GlassFieldWideSlack]) **e i raggi stanno nei lati** (due
 * angoli vicini non superano il lato che dividono: è come Compose li consegna) la lastra prende
 * `glassWideSlab`. Un raggio chiesto semplicemente troppo grande (il 999 di una capsula scritta a mano negli
 * uniform) non sta nei lati e resta limitato a mezzo lato, come prima di F1B.1. `glassWideSlab` è la
 * sagoma come intersezione dei quattro quadranti arrotondati, cioè `max(scatola, arco_k)` sui quattro angoli,
 * dove un arco conta solo nella sua **zona** (oltre il proprio centro su tutti e due gli assi). Dentro la
 * sagoma è la distanza vera; fuori lo è fin dove serve (accanto al contorno). Le zone di due angoli vicini
 * non si toccano perché i raggi arrivano già ridotti come li riduce Compose (`glassSlabGeometry`); quelle di
 * due angoli opposti possono sovrapporsi (una «foglia»), e il `max` le regge. Solo archi di cerchio: il
 * raccordo continuo l'engine lo ferma da sé a mezzo lato, quindi qui `smoothing` non si legge. La normale è
 * la stessa costruzione su raggi allargati di [GlassFieldNormalSpread], ognuno tenuto dentro mezzo lato corto e
 * dentro il lato che il vicino gli lascia (mai sotto il raggio vero). È un ramo su uno uniform: una lastra lo
 * prende tutta o per niente, e chi non lo prende esegue le righe di prima, al bit (le immagini di riferimento
 * `golden/liquid` non si muovono). Costa due giri di quattro confronti e al più due radici per giro.
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

float3 glassWideBox(float2 rel, float2 halfSize) {
    float2 b = abs(rel) - halfSize;
    float2 side = float2(rel.x < 0.0 ? -1.0 : 1.0, rel.y < 0.0 ? -1.0 : 1.0);
    if (b.x > 0.0 && b.y > 0.0) return float3(length(b), side * (b / length(b)));
    float gx = step(b.y, b.x);
    return float3(max(b.x, b.y), side * float2(gx, 1.0 - gx));
}

float3 glassWideCorner(float3 held, float2 rel, float2 halfSize, float2 side, float radius) {
    // `q`: il punto visto dal centro dell'arco, con gli assi che puntano al vertice dell'angolo.
    float2 q = rel * side - (halfSize - float2(radius));
    if (q.x <= 0.0 || q.y <= 0.0) return held;
    float len = length(q);
    if (len - radius <= held.x) return held;
    return float3(len - radius, side * (q / len));
}

float glassWideSpread(float radius, float minHalf, float roomX, float roomY) {
    return max(radius, min(min(radius * $GlassFieldNormalSpread, minHalf), min(roomX, roomY)));
}

float3 glassWideSlab(float2 rel, float2 halfSize, float4 radii) {
    float4 r = max(radii, float4(0.0));
    float3 box = glassWideBox(rel, halfSize);
    float3 held = glassWideCorner(box, rel, halfSize, float2(-1.0, -1.0), r.x);
    held = glassWideCorner(held, rel, halfSize, float2(1.0, -1.0), r.y);
    held = glassWideCorner(held, rel, halfSize, float2(1.0, 1.0), r.z);
    held = glassWideCorner(held, rel, halfSize, float2(-1.0, 1.0), r.w);
    float sd = held.x;

    // La normale: gli stessi quattro archi allargati, ognuno dentro il lato che il vicino gli lascia.
    float minHalf = min(halfSize.x, halfSize.y);
    float2 whole = halfSize * 2.0;
    held = glassWideCorner(box, rel, halfSize, float2(-1.0, -1.0),
        glassWideSpread(r.x, minHalf, whole.x - r.y, whole.y - r.w));
    held = glassWideCorner(held, rel, halfSize, float2(1.0, -1.0),
        glassWideSpread(r.y, minHalf, whole.x - r.x, whole.y - r.z));
    held = glassWideCorner(held, rel, halfSize, float2(1.0, 1.0),
        glassWideSpread(r.z, minHalf, whole.x - r.w, whole.y - r.y));
    held = glassWideCorner(held, rel, halfSize, float2(-1.0, 1.0),
        glassWideSpread(r.w, minHalf, whole.x - r.z, whole.y - r.x));
    return float3(sd, held.yz);
}

float3 glassSlab(float2 rel, float2 halfSize, float4 radii, float smoothing) {
    float minHalf = min(halfSize.x, halfSize.y);
    // Un angolo oltre mezzo lato corto: il quadrante non basta a dire di quale angolo e' un punto. Solo se i
    // raggi stanno nei lati (due vicini non superano il lato che dividono): un raggio chiesto troppo grande,
    // il 999 di una capsula, resta limitato a mezzo lato come sempre.
    if (max(max(radii.x, radii.y), max(radii.z, radii.w)) > minHalf * $GlassFieldWideSlack) {
        float2 room = halfSize * (2.0 * $GlassFieldWideSlack);
        if (max(radii.x + radii.y, radii.w + radii.z) <= room.x
            && max(radii.x + radii.w, radii.y + radii.z) <= room.y) {
            return glassWideSlab(rel, halfSize, radii);
        }
    }
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
 * `pieceRect`, di `glowCenter` e di `shadowOffset`, e l'argomento di `glassField`, `glassLightParts` e
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
 * - `float pieceSoft[8]`: per pezzo il raggio, in pixel, su cui **normale e centro** (non la distanza) si pesano
 *   con il già fuso, quando supera `pieceBlend` (F1B.2). Zero = come la sagoma: a riposo, e fra pezzi che non si
 *   toccano. Lo alza chi prepara gli uniform dove due pezzi si sovrappongono (`physicsFieldPlan`): lì, con il
 *   collo spento, i due pezzi si contendevano il punto a scatto e la lente mostrava una riga (le cuciture di
 *   `gruppo-diventa-menu`). Con zero il campo è quello di prima, al bit. Il nucleo a un pezzo non lo dichiara:
 *   lì non c'è niente da pesare.
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
uniform float pieceSoft[$GlassFieldMaxPieces];
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
            // Normale e centro si pesano su un raggio che non e' mai sotto quello della sagoma, e dove i pezzi si
            // sovrappongono sale a `pieceSoft` (F1B.2): due pezzi sovrapposti con centri diversi non si contendono
            // piu' il punto a scatto. La sagoma non cambia: `d` resta quella di sempre.
            float ka = max(k, pieceSoft[i]);
            float ha = clamp(0.5 + 0.5 * (slab.x - d) / ka, 0.0, 1.0);
            d = slab.x + (d - slab.x) * h - k * h * (1.0 - h);
            normal = mix(slab.yz, normal, ha);
            center = mix(pieceRect[i].xy, center, ha);
        }
    }
    return d;
}"""

/**
 * Il nucleo ad **anello**: una sagoma chiusa qualsiasi come poligono di al più [GlassFieldRingVertices]
 * vertici. È la goccia in viaggio e la sagoma libera di Fluid-physics (`FluidForm.Poly`), cioè tutto ciò che
 * non è un'unione di rettangoli arrotondati (piano 3.7, 3.8).
 *
 * Il ciclo è quello di `PolyMorphRefractionShaderString` (`fluidphysics/FluidPhysicsShaders.kt`), portato
 * nella firma del nucleo: distanza dal lato più vicino, segno per avvolgimento e normale mediata, tutto in un
 * passaggio. Ha la stessa `glassField` dei nuclei a lastre, quindi sopra ci sta lo stesso corpo, riga per riga.
 *
 * ### uniform (oltre a `offset`, `flex`, `flexCenter` di [GlassFieldPointBlock])
 * - `float vertCount`, `float2 verts[96]`: l'anello, in senso orario sullo schermo, in pixel del sistema del
 *   campo. Le caselle oltre `vertCount` non si leggono;
 * - `float4 bbox`: il riquadro dell'anello **allargato di `ringBand.x`** (sinistra, alto, destra, basso). Fuori
 *   il ciclo non gira;
 * - `float normalSmooth`: il quadrato della distanza, in pixel dello spazio dello shader, su cui la normale si
 *   media fra i lati vicini (scioglie lo
 *   spigolo fra due lati); zero = la direzione dal punto più vicino;
 * - `float soften`: di quanti pixel il campo si erode per sciogliere le faccette del campionamento. Zero per
 *   la goccia, che è già fitta agli angoli;
 * - `float2 fieldCenter`: il centro del riquadro della sagoma: è il `center` che il campo restituisce (cupola e
 *   ingrandimento della lente girano attorno a lui);
 * - `float2 restCenter`, `float2 restHalfSize`, `float4 restRadii`, `float restSmooth`, `float dropReach`: la
 *   **fascia della goccia**. Il contorno di una goccia non si allontana dalla sua sagoma a riposo (la lastra che
 *   la sostituirà, con i raggi e il raccordo che il campo le dà: la regola forma → piano) più di `dropReach`
 *   pixel: fuori da quella fascia il ciclo non serve. `dropReach` negativo: non è una goccia, nessuna
 *   scorciatoia. `restSmooth` a zero (una scena che non lo scrive) è l'angolo circolare di prima;
 * - `float restBlend`: quanto il campo della goccia è già quello della sua lastra a riposo, 0..1 (F1B.2). Zero in
 *   volo: il campo è l'anello, al bit. Mentre la goccia si posa sale fino a uno, e all'ultimo fotogramma ad
 *   anello distanza, normale e centro sono quelli che il nucleo a lastre darà al fotogramma dopo. Senza, la
 *   normale mediata dell'anello e quella allargata della lastra differivano negli angoli, e con loro il riflesso:
 *   fino a 74 livelli su 255 alla posa del menu (`bench/f1b-2/posa`). Lo calcola chi prepara gli uniform
 *   (`physicsDropSettle`);
 * - `float2 ringBand`: fin dove la distanza deve essere **vera**: `x` fuori dalla sagoma, `y` dentro. Oltre,
 *   il nucleo restituisce una distanza approssimata ma dalla parte giusta e non più vicina al contorno di così
 *   (fuori almeno `x`, dentro più profonda di `y`), con normale nulla. `y` deve coprire tutto ciò che il corpo
 *   legge accanto al contorno (la banda della lente, l'ombreggiatura, il filo e il rientro del riflesso) e `x`
 *   l'antialias; chi accende l'ombra in viaggio (F1B.2) allarga `x` e `bbox` di quanto l'ombra arriva.
 *
 * ### La normale mediata
 * Con `normalSmooth` sopra zero la normale è la media delle **normali dei lati** (la perpendicolare di ogni lato,
 * che esce da un anello in senso orario; se l'anello arriva nell'altro senso la rigira il lato più vicino), col
 * peso `(normalSmooth / (dd + normalSmooth))²`: 1 sul lato a qualunque densità di schermo, verso zero lontano.
 * Due differenze dallo shader di oggi (`PolyMorphRefractionShaderString`), tutte e due viste sul banco con la
 * scena `f1b/poly`:
 * - oggi il peso è `1 / (dd + normalSmooth)²` e le somme sotto 10⁻⁴ si scartano: sopra `normalSmooth` = 100 px²
 *   (4 dp a densità 2,5 con la cattura a scala 1) la normale si annulla ovunque, e con lei filo e lente;
 * - oggi si mediano le direzioni **dai punti più vicini**, che accanto a un vertice tirano di lato: la normale
 *   oscilla lungo ogni lato. La lente morbida di oggi non lo mostra; il riflesso del materiale nuovo, che legge
 *   la normale, usciva tratteggiato.
 * Si controlla guardando quella scena ingrandita: filo e riga chiara devono girare tutta la sagoma, continui.
 *
 * ### `float glassField(float2 p, out float2 normal, out float2 center)`
 * Come nei nuclei a lastre. `normal` è unitaria (nulla nelle scorciatoie, dove il vetro è piatto); `center` è
 * `fieldCenter`. Su un lato dritto la distanza è esatta; attorno a un vertice è quella del poligono.
 *
 * AGSL accetta come indice di un array uniform solo una costante o la variabile di un ciclo a limite
 * costante: per questo il vertice precedente viaggia in una variabile e il lato di chiusura si fa dopo il
 * ciclo, con `verts[0]`.
 */
@Language("AGSL")
internal const val GlassFieldKernelRing = """
$GlassFieldPointBlock

uniform float vertCount;
uniform float2 verts[$GlassFieldRingVertices];
uniform float4 bbox;
uniform float normalSmooth;
uniform float soften;
uniform float2 fieldCenter;
uniform float2 restCenter;
uniform float2 restHalfSize;
uniform float4 restRadii;
uniform float restSmooth;
uniform float restBlend;
uniform float dropReach;
uniform float2 ringBand;
$GlassFieldSlabBlock

float glassRingField(float2 p, out float2 normal, out float2 center) {
    normal = float2(0.0);
    center = fieldCenter;
    // Fuori dal riquadro (gia' allargato di ringBand.x) non c'e' niente: si esce prima del ciclo.
    float2 beyond = max(max(bbox.xy - p, p - bbox.zw), float2(0.0));
    if (beyond.x > 0.0 || beyond.y > 0.0) return ringBand.x + length(beyond);
    // La fascia della goccia: lontano dalla sagoma a riposo il contorno non puo' esserci.
    if (dropReach >= 0.0) {
        float restD = glassSlab(p - restCenter, restHalfSize, restRadii, restSmooth).x;
        if (restD - dropReach > ringBand.x) return restD - dropReach;
        if (restD + dropReach < -ringBand.y) return restD + dropReach;
    }

    int count = int(vertCount);
    float best = 1000000000.0;
    float2 nearest = float2(0.0);
    float2 nearestSide = float2(0.0);
    float2 nsum = float2(0.0);
    float winding = 1.0;
    float2 first = verts[0];
    float2 prev = first;
    for (int i = 1; i < $GlassFieldRingVertices; ++i) {
        if (i >= count) break;
        float2 vi = verts[i];
        float2 e = prev - vi;
        float2 w = p - vi;
        float ee = dot(e, e);
        float t = clamp(dot(w, e) / max(ee, 0.000001), 0.0, 1.0);
        float2 closest = vi + e * t;
        float2 delta = p - closest;
        float dd = dot(delta, delta);
        // La normale del lato: la perpendicolare che esce da un anello in senso orario. Un lato nullo non pesa.
        float2 side = float2(-e.y, e.x) * inversesqrt(max(ee, 0.000001));
        if (dd < best) {
            best = dd;
            nearest = closest;
            nearestSide = side;
        }
        // La normale mediata: le normali dei lati vicini, col peso 1 sul lato e verso zero lontano.
        float wn = normalSmooth / (dd + normalSmooth);
        nsum += side * (wn * wn);
        bool c1 = p.y >= vi.y;
        bool c2 = p.y < prev.y;
        bool c3 = e.x * w.y > e.y * w.x;
        if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) winding = -winding;
        prev = vi;
    }
    {
        float2 vi = first;
        float2 e = prev - vi;
        float2 w = p - vi;
        float ee = dot(e, e);
        float t = clamp(dot(w, e) / max(ee, 0.000001), 0.0, 1.0);
        float2 closest = vi + e * t;
        float2 delta = p - closest;
        float dd = dot(delta, delta);
        float2 side = float2(-e.y, e.x) * inversesqrt(max(ee, 0.000001));
        if (dd < best) {
            best = dd;
            nearest = closest;
            nearestSide = side;
        }
        float wn = normalSmooth / (dd + normalSmooth);
        nsum += side * (wn * wn);
        bool c1 = p.y >= vi.y;
        bool c2 = p.y < prev.y;
        bool c3 = e.x * w.y > e.y * w.x;
        if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) winding = -winding;
    }
    // Dal punto piu' vicino verso `p`, girata in fuori dal segno dell'avvolgimento: la direzione esatta.
    float2 away = (p - nearest) * winding;
    float2 outward = away;
    if (normalSmooth > 0.0) {
        // Se l'anello arriva in senso antiorario le normali dei lati guardano dentro: le rigira il lato piu' vicino.
        outward = dot(nearestSide, away) < 0.0 ? -nsum : nsum;
    }
    float len = length(outward);
    normal = len > 0.0000001 ? outward / len : float2(0.0);
    return winding * sqrt(best) - soften;
}

float glassField(float2 p, out float2 normal, out float2 center) {
    float d = glassRingField(p, normal, center);
    // La goccia che si posa (F1B.2): verso il campo della lastra che la sostituira'. Un ramo su uno uniform: in volo
    // non si esegue, e il campo e' l'anello di sempre.
    if (restBlend > 0.0) {
        float3 rest = glassSlab(p - restCenter, restHalfSize, restRadii, restSmooth);
        float w = clamp(restBlend, 0.0, 1.0);
        d = mix(d, rest.x, w);
        normal = mix(normal, rest.yz, w);
        center = mix(center, restCenter, w);
    }
    return d;
}"""
