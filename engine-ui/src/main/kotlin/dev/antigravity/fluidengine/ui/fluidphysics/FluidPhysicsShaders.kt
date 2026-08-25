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
uniform float blendRadius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float aa;
layout(color) uniform half4 tintColor;

$PhysicsSdfPrelude

half4 main(float2 coord) {
    float2 p = coord + offset;

    float d = 1000000.0;
    float2 grad = float2(0.0);
    float k = max(blendRadius, 0.0001);
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
        sampled = content.eval(coord);
    } else {
        float x = 1.0 - min(-d, refractionHeight) / refractionHeight;
        float disp = physicsCircleMap(x) * refractionAmount;
        float len = length(grad);
        float2 normal = len > 0.0001 ? grad / len : float2(0.0);
        sampled = content.eval(coord + disp * normal);
    }

    half4 tint = half4(tintColor.rgb * tintColor.a, tintColor.a);
    return (tint + sampled * (1.0 - tint.a)) * coverage;
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
uniform float2 verts[$PhysicsMaxVertices];
uniform float soften;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float aa;
layout(color) uniform half4 tintColor;

$PhysicsSdfPrelude

half4 main(float2 coord) {
    float2 p = coord + offset;

    int count = int(vertCount);
    float best = 1000000000.0;
    float2 nearest = float2(0.0);
    float winding = 1.0;
    int j = count - 1;
    for (int i = 0; i < $PhysicsMaxVertices; ++i) {
        if (i >= count) break;
        float2 vi = verts[i];
        float2 vj = verts[j];
        float2 e = vj - vi;
        float2 w = p - vi;
        float t = clamp(dot(w, e) / max(dot(e, e), 0.000001), 0.0, 1.0);
        float2 closest = vi + e * t;
        float2 delta = p - closest;
        float dd = dot(delta, delta);
        if (dd < best) {
            best = dd;
            nearest = closest;
        }
        bool c1 = p.y >= vi.y;
        bool c2 = p.y < vj.y;
        bool c3 = e.x * w.y > e.y * w.x;
        if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) winding = -winding;
        j = i;
    }
    float d = winding * sqrt(best) - soften;

    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    half4 sampled;
    if (-d >= refractionHeight) {
        sampled = content.eval(coord);
    } else {
        float2 away = p - nearest;
        float len = length(away);
        float2 normal = len > 0.0001 ? (away / len) * winding : float2(0.0);
        float x = 1.0 - min(-d, refractionHeight) / refractionHeight;
        float disp = physicsCircleMap(x) * refractionAmount;
        sampled = content.eval(coord + disp * normal);
    }

    half4 tint = half4(tintColor.rgb * tintColor.a, tintColor.a);
    return (tint + sampled * (1.0 - tint.a)) * coverage;
}"""
