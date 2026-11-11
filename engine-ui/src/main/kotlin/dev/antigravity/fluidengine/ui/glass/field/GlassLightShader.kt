package dev.antigravity.fluidengine.ui.glass.field

import org.intellij.lang.annotations.Language

/**
 * La **luce** del materiale `Liquid` come blocco AGSL: filo scuro sul perimetro, riflessi chiari interni,
 * bagliore del dito. Qui c'è il **contratto** (uniform e firma) con un corpo neutro, che non disegna niente:
 * la luce vera la scrive F1A.3 dentro questa stessa funzione, senza cambiarne la firma.
 *
 * È un blocco e non uno shader perché lo includono due programmi. A riposo è il programma della luce
 * (`GlassLight1ShaderString`, `GlassLight8ShaderString`), senza ingressi, disegnato sopra il corpo: un
 * bagliore che segue il dito non deve rifare la sfocatura. In viaggio è il corpo stesso, dietro il suo
 * `lightInBody`: la sagoma cambia a ogni fotogramma, il corpo si riesegue comunque, e tenere la luce fuori
 * vorrebbe dire valutare il campo due volte. Lo stesso testo nei due posti è ciò che rende invisibile il
 * passaggio fra viaggio e riposo.
 *
 * ### Dove si include
 * Dopo il nucleo ([GlassFieldKernel1] o [GlassFieldKernel8]) e prima di `main`. Il blocco **dichiara i propri
 * uniform**: chi lo include non li ridichiara. Non dichiara `materialize` né `aa`, che sono del programma
 * che include (il corpo li ha fra i suoi; il programma della luce li dichiara da sé).
 *
 * ### uniform (piano 3.4). Le lunghezze sono pixel dello spazio dello shader, i punti sono nel sistema del campo
 * - `float rimWidth`: lo spessore del filo, **assoluto**: non cresce con la lastra;
 * - `layout(color) half4 rimDark`: colore del filo scuro, non premoltiplicato; l'alfa è la sua forza. Alfa
 *   zero sul lato scuro: lì il filo non c'è;
 * - `float2 reflexDir`: vettore unitario, l'asse dei due lobi di riflesso. Con y verso il basso, `(0, -1)` è
 *   il valore di serie: un lobo in alto e, opposto, uno in basso. Lo ruotano il sensore e la variante
 *   «iOS 26»;
 * - `float2 reflexWidths`: la mezza ampiezza dei due lobi, in **radianti**: `x` del lobo verso `reflexDir`,
 *   `y` di quello opposto. È l'angolo fra la normale e l'asse a cui il lobo si spegne;
 * - `float reflexStrength`: 0..1, l'alfa del riflesso al centro di un lobo, prima dell'alfa di `lightColor`;
 * - `float reflexDepth`: per quanti pixel il riflesso rientra dal filo verso l'interno prima di tornare al
 *   corpo;
 * - `float reflexFloor`: 0..1, la frazione di riflesso che resta fuori dai lobi;
 * - `float2 glowCenter`: dove sta il dito;
 * - `float glowRadius`: il raggio della campana del bagliore;
 * - `float glow`: 0..1, la forza del bagliore; a zero il bagliore non si calcola;
 * - `float glowFill`: 0..1, quanto il bagliore ha riempito l'elemento (0 = la campana, 1 = tutta la sagoma);
 * - `layout(color) half4 lightColor`: colore di riflessi e bagliore, non premoltiplicato; la sua alfa
 *   moltiplica tutti e due.
 *
 * ### `half4 glassLight(float2 p, float d, float2 normal)`
 * Riceve ciò che il chiamante ha già in mano, perché il campo si valuti una volta sola:
 * - `p`: il punto nel sistema del campo, cioè `glassFieldPoint(coord)`;
 * - `d`: la distanza con segno in `p`, cioè ciò che `glassField` ha restituito (negativa dentro, in pixel);
 * - `normal`: la normale che `glassField` ha scritto, **così com'è**: nella zona di fusione non è unitaria,
 *   e la normalizza questa funzione se le serve.
 *
 * Restituisce la luce in quel punto come colore **premoltiplicato**, da comporre sopra il corpo in `SrcOver`:
 * un filo scuro è nero con la sua alfa, un riflesso è `lightColor` con la sua. È la luce **piena** e **non
 * ritagliata**: non contiene né la copertura della sagoma né `materialize`. Li applica chi chiama:
 *
 *     // programma della luce, a riposo
 *     return glassLight(p, d, normal) * half(coverage * materialize);
 *     // corpo, in viaggio
 *     half4 light = glassLight(p, d, normal) * half(materialize);
 *     colour = light + colour * (1.0 - light.a);      // poi la copertura, una volta, su tutto
 *
 * Fuori dalla sagoma (`d > 0`) il valore non conta: chi chiama lo moltiplica per una copertura nulla.
 */
@Language("AGSL")
internal const val GlassLightBlock = """
uniform float rimWidth;
layout(color) uniform half4 rimDark;
uniform float2 reflexDir;
uniform float2 reflexWidths;
uniform float reflexStrength;
uniform float reflexDepth;
uniform float reflexFloor;
uniform float2 glowCenter;
uniform float glowRadius;
uniform float glow;
uniform float glowFill;
layout(color) uniform half4 lightColor;

half4 glassLight(float2 p, float d, float2 normal) {
    return half4(0.0);
}"""
