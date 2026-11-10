/*
 * La famiglia di shader di Fluid-physics.
 *
 * Il preludio SDF (radiusAt / sdRoundedRect / gradSdRoundedRect) deriva dalle stesse funzioni del
 * renderer vendored di Kyant (AndroidLiquidGlass, Apache-2.0 — vedi LICENSES/AndroidLiquidGlass.md):
 * lì sono `private` dentro Shaders.kt, quindi vengono ri-dichiarate qui invece di forzare una
 * visibilità che a monte non esiste. La struttura della rifrazione (circleMap, la banda di
 * displacement al bordo) è la stessa, perché il materiale deve restare *lo stesso vetro* anche
 * mentre cambia forma.
 *
 * Le tre discipline non negoziabili, ereditate dal contratto di DrawBackdropModifier:
 *  1. **Sorgenti fisse.** La cache degli shader è processo-wide e ha come chiave il testo sorgente:
 *     queste stringhe sono costanti, e ogni cosa che cambia per fotogramma passa dagli uniform.
 *  2. **Un solo passaggio per pixel.** Il gradiente del campo si accumula nello stesso loop della
 *     distanza (mix pesato dallo stesso h dello smooth minimum), mai per differenze finite: tre
 *     valutazioni extra del campo per pixel sono esattamente il tipo di conto che su un tablet si
 *     vede.
 *  3. **La maschera è dello shader.** Il clip hardware del nodo resta un rettangolo fisso per tutto
 *     il viaggio (vedi GlassClipGeometry); è l'alpha calcolato qui a ritagliare la silhouette, ed è
 *     per questo che la sagoma può cambiare a ogni fotogramma senza mai costringere il layer a
 *     re-registrare.
 */
package dev.antigravity.fluidengine.ui.fluidphysics

import org.intellij.lang.annotations.Language

/**
 * Quanti pezzi porta lo shader di gruppo. È il `MaxPieces` di [FluidForm.Group], e i due numeri
 * devono restare uguali: l'array uniform è dimensionato su questo.
 */
internal const val PhysicsMaxPieces: Int = 6

/**
 * Quanti vertici porta lo shader poligonale. È il `MaxVertices` di [FluidFormPresets]: stesso
 * vincolo di cui sopra.
 */
internal const val PhysicsMaxVertices: Int = 64

/**
 * Quanti vertici porta, al massimo, l'anello che lo shader poligonale disegna (3.0): la goccia del
 * vetro liquido si disegna con [DropRenderPoints] punti, piu' dei [PhysicsMaxVertices] di una sagoma
 * libera, perche' gli angoli del pannello restino definiti mentre viaggia.
 */
internal const val PhysicsMaxRingVertices: Int = 96

@Language("AGSL")
private const val PhysicsSdfPrelude = """
float physicsRadiusAt(float2 rel, float4 radii) {
    if (rel.x >= 0.0) {
        if (rel.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (rel.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float physicsSdRoundedRect(float2 rel, float2 halfSize, float radius) {
    float2 cornerCoord = abs(rel) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 physicsGradSdRoundedRect(float2 rel, float2 halfSize, float radius) {
    float2 cornerCoord = abs(rel) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        float2 direction = max(cornerCoord, 0.0);
        float len = length(direction);
        if (len <= 0.0001) return float2(0.0);
        return sign(rel) * (direction / len);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(rel) * float2(gradX, 1.0 - gradX);
    }
}

float physicsCircleMap(float x) {
    return 1.0 - sqrt(max(1.0 - x * x, 0.0));
}"""

/**
 * Fino a sei rettangoli arrotondati fusi in un solo campo con uno smooth minimum: la lente, la
 * maschera e il ponte liquido di un morph a pezzi, in un passaggio.
 *
 * Il gradiente del campo fuso è il mix dei gradienti analitici dei pezzi, pesato dallo stesso `h`
 * dello smin: è l'approssimazione standard dei metaball, indistinguibile dal vero nel ponte e
 * esatta lontano da esso.
 *
 * Il raggio di fusione e' per pezzo (`pieceBlend`, 3.0): un gruppo li ha tutti uguali, il viaggio
 * liquido ne da' uno all'ancora che si svuota nel corpo (vedi FluidPhysicsLiquid.kt). `magnify` e'
 * l'ingrandimento della goccia in moto, zero a riposo.
 *
 * La tinta sta *dentro* lo shader (uniform `tintColor`) perché il ponte fra due pezzi è materiale
 * che nessun path di geometria descrive: una tinta disegnata come path lascerebbe il ponte
 * trasparente — rifrazione senza pellicola — che si legge come un buco. Con alpha 0 il blend è un
 * passaggio neutro e la tinta torna al chiamante.
 */
@Language("AGSL")
internal const val SlabGroupRefractionShaderString = """
uniform shader content;

uniform float2 offset;
uniform float pieceCount;
uniform float4 pieceRect[$PhysicsMaxPieces];
uniform float4 pieceRadii[$PhysicsMaxPieces];
uniform float pieceBlend[$PhysicsMaxPieces];
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float magnify;
uniform float2 fieldCenter;
uniform float aa;
layout(color) uniform half4 tintColor;

$PhysicsSdfPrelude

half4 main(float2 coord) {
    float2 p = coord + offset;
    // L'ingrandimento della goccia in moto (3.0): zero a riposo, quindi la lastra di sempre.
    float2 lensShift = (fieldCenter - p) * magnify;

    float d = 1000000.0;
    float2 grad = float2(0.0);
    for (int i = 0; i < $PhysicsMaxPieces; ++i) {
        if (float(i) >= pieceCount) break;
        float2 rel = p - pieceRect[i].xy;
        float2 halfSize = pieceRect[i].zw;
        float radius = min(physicsRadiusAt(rel, pieceRadii[i]), min(halfSize.x, halfSize.y));
        float sd = physicsSdRoundedRect(rel, halfSize, radius);
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 sdGrad = physicsGradSdRoundedRect(rel, halfSize, gradRadius);
        if (i == 0) {
            d = sd;
            grad = sdGrad;
        } else {
            // Il raggio di fusione e' del PEZZO (3.0): un gruppo li ha tutti uguali, il liquido di un
            // viaggio ne ha uno per l'ancora che si svuota.
            float k = max(pieceBlend[i], 0.0001);
            float h = clamp(0.5 + 0.5 * (sd - d) / k, 0.0, 1.0);
            d = sd + (d - sd) * h - k * h * (1.0 - h);
            grad = mix(sdGrad, grad, h);
        }
    }

    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    half4 sampled;
    if (-d >= refractionHeight) {
        sampled = content.eval(coord + lensShift);
    } else {
        float x = 1.0 - min(-d, refractionHeight) / refractionHeight;
        float disp = physicsCircleMap(x) * refractionAmount;
        // Il termine a cupola: aggiunge alla normale del bordo una componente diretta al centro
        // del campo, e trasforma un bordo smussato in una lente intera. È quello che dà a un
        // pezzo piccolo la lettura "palla di vetro" invece di "adesivo lucido".
        float2 toCenter = p - fieldCenter;
        float toCenterLen = length(toCenter);
        float2 depthDir = toCenterLen > 0.0001 ? toCenter / toCenterLen : float2(0.0);
        float2 normal = grad + depthEffect * depthDir;
        float len = length(normal);
        normal = len > 0.0001 ? normal / len : float2(0.0);
        sampled = content.eval(coord + lensShift + disp * normal);
    }

    half4 tint = half4(tintColor.rgb * tintColor.a, tintColor.a);
    return (tint + sampled * (1.0 - tint.a)) * coverage;
}"""

/**
 * La fascia della goccia (3.0), come blocco AGSL condiviso dalle due varianti poligonali: il contorno
 * della goccia non si allontana dalla sua sagoma a riposo (un rettangolo arrotondato) piu' di
 * `dropReach` pixel, quindi fuori da quella fascia il loop sui lati non serve — oltre e' vuoto, piu'
 * dentro della lente e' il vetro piatto, esattamente come lo darebbe il loop. Il loop gira solo dove
 * il bordo puo' essere: a goccia quasi posata una striscia sottile, non tutto il pannello.
 * `dropReach` negativo: non e' una goccia, nessuna scorciatoia.
 *
 * Il campione piatto si chiama `flatSample` e non `flat`: `flat` e' una parola riservata di SkSL, e
 * quindi di AGSL. Come nome di variabile faceva rifiutare i due shader poligonali, e la sonda di
 * [FluidPhysicsShaderHealth] portava **tutto** il motore al livello Balanced, senza lente ne' ponti:
 * dalla 3.0.0 alla 3.1.2, su ogni dispositivo (confermato su un Tab S9 il 2026-11-10, in logcat:
 * «expected ';', but found 'flat'»). Un nome nuovo in uno shader va provato contro il compilatore,
 * non contro l'occhio: `tools/glassbench` lo fa sul PC.
 */
private const val PhysicsDropBandBlock = """
    if (dropReach >= 0.0) {
        float2 restRel = p - restCenter;
        float restD = physicsSdRoundedRect(restRel, restHalfSize, physicsRadiusAt(restRel, restRadii));
        if (restD - dropReach > aa) return half4(0.0);
        if (restD + dropReach < -(refractionHeight + aa)) {
            half4 flatSample = content.eval(coord + lensShift);
            half4 flatTint = half4(tintColor.rgb * tintColor.a, tintColor.a);
            return flatTint + flatSample * (1.0 - flatTint.a);
        }
    }"""

/**
 * Una sagoma chiusa qualsiasi, come anello di vertici: distanza, segno per winding e direzione del
 * punto più vicino, tutto in un loop solo.
 *
 * `soften` non è il raccordo della forma — quello è già cotto dentro l'anello dal campionamento
 * delle cubiche — ma un'erosione di un pixel o due che scioglie le faccette del campionamento
 * prima che la lente le renda visibili.
 */
@Language("AGSL")
internal const val PolyMorphRefractionShaderString = """
uniform shader content;

uniform float2 offset;
uniform float vertCount;
uniform float2 verts[$PhysicsMaxRingVertices];
uniform float4 bbox;
uniform float normalSmooth;
uniform float2 restCenter;
uniform float2 restHalfSize;
uniform float4 restRadii;
uniform float dropReach;
uniform float soften;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float magnify;
uniform float2 fieldCenter;
uniform float aa;
layout(color) uniform half4 tintColor;

$PhysicsSdfPrelude

half4 main(float2 coord) {
    float2 p = coord + offset;
    // L'ingrandimento della goccia in moto (3.0): zero a riposo, quindi la lastra di sempre.
    float2 lensShift = (fieldCenter - p) * magnify;
    // Fuori dal riquadro della sagoma non c'e' niente da disegnare: si esce prima del loop (3.0), che
    // con l'anello della goccia e' il conto piu' caro del sottosistema.
    if (p.x < bbox.x || p.y < bbox.y || p.x > bbox.z || p.y > bbox.w) return half4(0.0);
$PhysicsDropBandBlock

    // AGSL accetta un indice di array uniform solo se costante — e la variabile d'induzione di un
    // loop a limiti costanti lo è, perché il compilatore srotola. Qualsiasi altro indice (il
    // classico `j` del vertice precedente) viene rifiutato alla compilazione. Quindi il vertice
    // precedente viaggia in una variabile, e il lato di chiusura si fa dopo il loop con verts[0].
    int count = int(vertCount);
    float best = 1000000000.0;
    float2 nearest = float2(0.0);
    float2 nsum = float2(0.0);
    float winding = 1.0;
    float2 first = verts[0];
    float2 prev = first;
    for (int i = 1; i < $PhysicsMaxRingVertices; ++i) {
        if (i >= count) break;
        float2 vi = verts[i];
        float2 e = prev - vi;
        float2 w = p - vi;
        float t = clamp(dot(w, e) / max(dot(e, e), 0.000001), 0.0, 1.0);
        float2 closest = vi + e * t;
        float2 delta = p - closest;
        float dd = dot(delta, delta);
        if (dd < best) {
            best = dd;
            nearest = closest;
        }
        // La normale smussata (3.0): la media delle direzioni dai lati vicini, pesata col quadrato
        // inverso della distanza — niente faccette sulla lente dove due lati s'incontrano.
        float wn = 1.0 / ((dd + normalSmooth) * (dd + normalSmooth));
        nsum += delta * (wn * inversesqrt(dd + 0.000001));
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
        float t = clamp(dot(w, e) / max(dot(e, e), 0.000001), 0.0, 1.0);
        float2 closest = vi + e * t;
        float2 delta = p - closest;
        float dd = dot(delta, delta);
        if (dd < best) {
            best = dd;
            nearest = closest;
        }
        // La normale smussata (3.0): la media delle direzioni dai lati vicini, pesata col quadrato
        // inverso della distanza — niente faccette sulla lente dove due lati s'incontrano.
        float wn = 1.0 / ((dd + normalSmooth) * (dd + normalSmooth));
        nsum += delta * (wn * inversesqrt(dd + 0.000001));
        bool c1 = p.y >= vi.y;
        bool c2 = p.y < prev.y;
        bool c3 = e.x * w.y > e.y * w.x;
        if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) winding = -winding;
    }
    float d = winding * sqrt(best) - soften;

    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    half4 sampled;
    if (-d >= refractionHeight) {
        sampled = content.eval(coord + lensShift);
    } else {
        float2 away = normalSmooth > 0.0 ? nsum : p - nearest;
        float len = length(away);
        float2 grad = len > 0.0001 ? (away / len) * winding : float2(0.0);
        float2 toCenter = p - fieldCenter;
        float toCenterLen = length(toCenter);
        float2 depthDir = toCenterLen > 0.0001 ? toCenter / toCenterLen : float2(0.0);
        float2 normal = grad + depthEffect * depthDir;
        float nl = length(normal);
        normal = nl > 0.0001 ? normal / nl : float2(0.0);
        float x = 1.0 - min(-d, refractionHeight) / refractionHeight;
        float disp = physicsCircleMap(x) * refractionAmount;
        sampled = content.eval(coord + lensShift + disp * normal);
    }

    half4 tint = half4(tintColor.rgb * tintColor.a, tintColor.a);
    return (tint + sampled * (1.0 - tint.a)) * coverage;
}"""

/**
 * La frangia cromatica di Kyant, come blocco AGSL condiviso dalle due varianti "con dispersione".
 *
 * E' il ramo `RoundedRectRefractionWithDispersionShaderString` del renderer vendored (Apache-2.0,
 * `glass/backdrop/internal/Shaders.kt`), con due differenze soltanto: il centro e la mezza taglia
 * sono quelli del campo (per un pezzo solo sono quelli del suo rettangolo, cioe' i numeri di Kyant
 * alla lettera), e l'intensita' e' un PESO fra 0 e 1 invece di un interruttore — cosi' un tasto con
 * la frangia che diventa un pannello senza la perde lungo il viaggio, invece che a meta' strada.
 *
 * Si aspetta nello scope: `coord`, `lensShift` (l'ingrandimento in moto), `disp` (lo spostamento),
 * `normal`, `toCenter` (= p - fieldCenter), e scrive `sampled`. Sette campioni per pixel, ma solo
 * nella banda della lente: piu' dentro, dove la lente non arriva, il ramo non si esegue.
 */
private const val PhysicsDispersionBlock = """
        float2 refracted = coord + lensShift + disp * normal;
        float2 halfField = max(fieldHalfSize, float2(0.0001));
        float dispersionIntensity = chromaticAberration * ((toCenter.x * toCenter.y) / (halfField.x * halfField.y));
        float2 dispersed = disp * normal * dispersionIntensity;

        half4 color = half4(0.0);

        half4 red = content.eval(refracted + dispersed);
        color.r += red.r / 3.5;
        color.a += red.a / 7.0;

        half4 orange = content.eval(refracted + dispersed * (2.0 / 3.0));
        color.r += orange.r / 3.5;
        color.g += orange.g / 7.0;
        color.a += orange.a / 7.0;

        half4 yellow = content.eval(refracted + dispersed * (1.0 / 3.0));
        color.r += yellow.r / 3.5;
        color.g += yellow.g / 3.5;
        color.a += yellow.a / 7.0;

        half4 green = content.eval(refracted);
        color.g += green.g / 3.5;
        color.a += green.a / 7.0;

        half4 cyan = content.eval(refracted - dispersed * (1.0 / 3.0));
        color.g += cyan.g / 3.5;
        color.b += cyan.b / 3.0;
        color.a += cyan.a / 7.0;

        half4 blue = content.eval(refracted - dispersed * (2.0 / 3.0));
        color.b += blue.b / 3.0;
        color.a += blue.a / 7.0;

        half4 purple = content.eval(refracted - dispersed);
        color.r += purple.r / 7.0;
        color.b += purple.b / 3.0;
        color.a += purple.a / 7.0;

        sampled = color;"""

/**
 * [SlabGroupRefractionShaderString] con la frangia cromatica: la lente di `glassSurface` per i ruoli
 * che la chiedono (`GlassOptics.dispersion`, i tasti), quindi la stessa lastra ferma o in viaggio.
 *
 * Una sorgente a parte, non un ramo dentro quella di sempre: chi non ha frangia non paga nemmeno il
 * test, e un driver che rifiutasse questa variante toglie solo la frangia (vedi
 * [FluidPhysicsShaderHealth.dispersionHealthy]), non la lente.
 */
@Language("AGSL")
internal const val SlabGroupDispersionShaderString = """
uniform shader content;

uniform float2 offset;
uniform float pieceCount;
uniform float4 pieceRect[$PhysicsMaxPieces];
uniform float4 pieceRadii[$PhysicsMaxPieces];
uniform float pieceBlend[$PhysicsMaxPieces];
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float magnify;
uniform float2 fieldCenter;
uniform float2 fieldHalfSize;
uniform float chromaticAberration;
uniform float aa;
layout(color) uniform half4 tintColor;

$PhysicsSdfPrelude

half4 main(float2 coord) {
    float2 p = coord + offset;
    // L'ingrandimento della goccia in moto (3.0): zero a riposo, quindi la lastra di sempre.
    float2 lensShift = (fieldCenter - p) * magnify;

    float d = 1000000.0;
    float2 grad = float2(0.0);
    for (int i = 0; i < $PhysicsMaxPieces; ++i) {
        if (float(i) >= pieceCount) break;
        float2 rel = p - pieceRect[i].xy;
        float2 halfSize = pieceRect[i].zw;
        float radius = min(physicsRadiusAt(rel, pieceRadii[i]), min(halfSize.x, halfSize.y));
        float sd = physicsSdRoundedRect(rel, halfSize, radius);
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 sdGrad = physicsGradSdRoundedRect(rel, halfSize, gradRadius);
        if (i == 0) {
            d = sd;
            grad = sdGrad;
        } else {
            // Il raggio di fusione e' del PEZZO (3.0): un gruppo li ha tutti uguali, il liquido di un
            // viaggio ne ha uno per l'ancora che si svuota.
            float k = max(pieceBlend[i], 0.0001);
            float h = clamp(0.5 + 0.5 * (sd - d) / k, 0.0, 1.0);
            d = sd + (d - sd) * h - k * h * (1.0 - h);
            grad = mix(sdGrad, grad, h);
        }
    }

    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    half4 sampled;
    if (-d >= refractionHeight) {
        sampled = content.eval(coord + lensShift);
    } else {
        float x = 1.0 - min(-d, refractionHeight) / refractionHeight;
        float disp = physicsCircleMap(x) * refractionAmount;
        float2 toCenter = p - fieldCenter;
        float toCenterLen = length(toCenter);
        float2 depthDir = toCenterLen > 0.0001 ? toCenter / toCenterLen : float2(0.0);
        float2 normal = grad + depthEffect * depthDir;
        float len = length(normal);
        normal = len > 0.0001 ? normal / len : float2(0.0);
$PhysicsDispersionBlock
    }

    half4 tint = half4(tintColor.rgb * tintColor.a, tintColor.a);
    return (tint + sampled * (1.0 - tint.a)) * coverage;
}"""

/** [PolyMorphRefractionShaderString] con la frangia cromatica: vedi [SlabGroupDispersionShaderString]. */
@Language("AGSL")
internal const val PolyMorphDispersionShaderString = """
uniform shader content;

uniform float2 offset;
uniform float vertCount;
uniform float2 verts[$PhysicsMaxRingVertices];
uniform float4 bbox;
uniform float normalSmooth;
uniform float2 restCenter;
uniform float2 restHalfSize;
uniform float4 restRadii;
uniform float dropReach;
uniform float soften;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float magnify;
uniform float2 fieldCenter;
uniform float2 fieldHalfSize;
uniform float chromaticAberration;
uniform float aa;
layout(color) uniform half4 tintColor;

$PhysicsSdfPrelude

half4 main(float2 coord) {
    float2 p = coord + offset;
    // L'ingrandimento della goccia in moto (3.0): zero a riposo, quindi la lastra di sempre.
    float2 lensShift = (fieldCenter - p) * magnify;
    if (p.x < bbox.x || p.y < bbox.y || p.x > bbox.z || p.y > bbox.w) return half4(0.0);
$PhysicsDropBandBlock

    int count = int(vertCount);
    float best = 1000000000.0;
    float2 nearest = float2(0.0);
    float2 nsum = float2(0.0);
    float winding = 1.0;
    float2 first = verts[0];
    float2 prev = first;
    for (int i = 1; i < $PhysicsMaxRingVertices; ++i) {
        if (i >= count) break;
        float2 vi = verts[i];
        float2 e = prev - vi;
        float2 w = p - vi;
        float t = clamp(dot(w, e) / max(dot(e, e), 0.000001), 0.0, 1.0);
        float2 closest = vi + e * t;
        float2 delta = p - closest;
        float dd = dot(delta, delta);
        if (dd < best) {
            best = dd;
            nearest = closest;
        }
        // La normale smussata (3.0): la media delle direzioni dai lati vicini, pesata col quadrato
        // inverso della distanza — niente faccette sulla lente dove due lati s'incontrano.
        float wn = 1.0 / ((dd + normalSmooth) * (dd + normalSmooth));
        nsum += delta * (wn * inversesqrt(dd + 0.000001));
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
        float t = clamp(dot(w, e) / max(dot(e, e), 0.000001), 0.0, 1.0);
        float2 closest = vi + e * t;
        float2 delta = p - closest;
        float dd = dot(delta, delta);
        if (dd < best) {
            best = dd;
            nearest = closest;
        }
        // La normale smussata (3.0): la media delle direzioni dai lati vicini, pesata col quadrato
        // inverso della distanza — niente faccette sulla lente dove due lati s'incontrano.
        float wn = 1.0 / ((dd + normalSmooth) * (dd + normalSmooth));
        nsum += delta * (wn * inversesqrt(dd + 0.000001));
        bool c1 = p.y >= vi.y;
        bool c2 = p.y < prev.y;
        bool c3 = e.x * w.y > e.y * w.x;
        if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) winding = -winding;
    }
    float d = winding * sqrt(best) - soften;

    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    half4 sampled;
    if (-d >= refractionHeight) {
        sampled = content.eval(coord + lensShift);
    } else {
        float2 away = normalSmooth > 0.0 ? nsum : p - nearest;
        float awayLen = length(away);
        float2 grad = awayLen > 0.0001 ? (away / awayLen) * winding : float2(0.0);
        float2 toCenter = p - fieldCenter;
        float toCenterLen = length(toCenter);
        float2 depthDir = toCenterLen > 0.0001 ? toCenter / toCenterLen : float2(0.0);
        float2 normal = grad + depthEffect * depthDir;
        float nl = length(normal);
        normal = nl > 0.0001 ? normal / nl : float2(0.0);
        float x = 1.0 - min(-d, refractionHeight) / refractionHeight;
        float disp = physicsCircleMap(x) * refractionAmount;
$PhysicsDispersionBlock
    }

    half4 tint = half4(tintColor.rgb * tintColor.a, tintColor.a);
    return (tint + sampled * (1.0 - tint.a)) * coverage;
}"""
