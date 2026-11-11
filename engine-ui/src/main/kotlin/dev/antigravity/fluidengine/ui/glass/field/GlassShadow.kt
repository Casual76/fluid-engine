package dev.antigravity.fluidengine.ui.glass.field

import org.intellij.lang.annotations.Language

/**
 * L'**ombra in viaggio** del materiale `Liquid` come blocco AGSL. Qui c'è il **contratto** (uniform e firma)
 * con un corpo neutro, che non disegna niente: l'ombra vera la scrive F1A.4 dentro questa stessa funzione,
 * senza cambiarne la firma. Il nodo dell'ombra a riposo (due maschere in sola alfa) nasce in questo stesso
 * file con F1A.4.
 *
 * A riposo l'ombra sono due maschere sfumate in cache. In viaggio la sagoma cambia a ogni fotogramma (un
 * ponte fra due pezzi non ha un path) e rifare due maschere sfocate su CPU a quella cadenza non sta nel
 * fotogramma: l'ombra esce allora dal campo, dentro lo shader del corpo, che in viaggio si riesegue comunque.
 *
 * ### Dove si include
 * Nel solo corpo, dopo il nucleo ([GlassFieldKernel1] o [GlassFieldKernel8]) e prima di `main`: il blocco può
 * chiamare `glassField` per valutare il campo nel punto spostato. **Dichiara i propri uniform**: chi lo
 * include non li ridichiara. Non dichiara `materialize` né `aa`, che sono del corpo.
 *
 * ### uniform (piano 3.4)
 * - `float2 shadowOffset`: di quanto l'ombra è scostata dalla sagoma, in pixel dello spazio dello shader nel
 *   sistema del campo (y positiva = verso il basso). L'ombra in `p` è la sagoma vista da `p - shadowOffset`;
 * - `float2 shadowRadii`: i raggi delle due componenti, in pixel dello spazio dello shader: `x` del contatto,
 *   `y` dell'ambiente. Sono **gli stessi numeri** che riceve il nodo delle due maschere a riposo (il raggio
 *   di sfocatura della maschera), perché riposo e viaggio abbiano una ricetta sola; il profilo di caduta che
 *   fa somigliare il campo a una maschera sfumata è di questa funzione;
 * - `float2 shadowAlphas`: 0..1, l'alfa delle due componenti dove sono più forti: `x` contatto, `y` ambiente;
 * - `layout(color) half4 shadowColor`: il colore dell'ombra, non premoltiplicato; la sua alfa moltiplica le
 *   due componenti.
 *
 * ### `half4 glassTravelShadow(float2 p, float d)`
 * - `p`: il punto nel sistema del campo, cioè `glassFieldPoint(coord)`;
 * - `d`: la distanza con segno in `p` che il corpo ha già calcolato (negativa dentro, in pixel). Il campo nel
 *   punto spostato lo valuta questa funzione, se e dove le serve.
 *
 * Restituisce l'ombra in quel punto come colore **premoltiplicato**, e **non scavata**: vale anche sotto la
 * sagoma, e non contiene né la copertura né `materialize`. La scava e la compone chi chiama, sotto il corpo:
 *
 *     half4 shade = glassTravelShadow(p, d) * half(materialize);
 *     return colour * half(coverage) + shade * half(1.0 - coverage);
 *
 * Perché l'ombra si veda il corpo deve disegnare anche **fuori** dalla sagoma: è il caso delle lastre che
 * viaggiano, che hanno il margine del campo e il ritaglio hardware spento. A riposo il corpo non la chiama
 * (`lightInBody` a zero) ed esce subito dove la copertura è nulla, come oggi.
 */
@Language("AGSL")
internal const val GlassTravelShadowBlock = """
uniform float2 shadowOffset;
uniform float2 shadowRadii;
uniform float2 shadowAlphas;
layout(color) uniform half4 shadowColor;

half4 glassTravelShadow(float2 p, float d) {
    return half4(0.0);
}"""
