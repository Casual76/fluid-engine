/*
 * Il CORPO del materiale `Liquid`: ciò che il vetro fa allo sfondo che ha dietro (lente, dispersione, velo,
 * tinta, ombreggiatura) e la maschera che lo ritaglia. È un programma con un ingresso solo, in fondo alla
 * catena di `RenderEffect` della cattura (piano 3.3, 3.6, via A):
 *
 *     ColorFilter(saturazione) -> Blur(Clamp) -> BlendMode(Plus, w * nitido, (1 - w) * sfocato) -> QUESTO
 *
 * Quindi qui la smerigliatura non c'è: l'ha già fatta la catena, con il raggio di sfocatura, e la mescola `w`
 * fra nitido e sfocato la decide chi monta la catena. Lo shader riceve uno sfondo solo e non sa quale dei due
 * sta guardando.
 *
 * Le discipline sono quelle del nucleo (`GlassFieldKernel.kt`, in testa): sorgenti `const val` composte con i
 * template `$Nome`, un passaggio del campo per pixel, cicli a limite costante, distanze in `float` e colori in
 * `half`, niente derivate, nessun nome che sia una parola di GLSL o di SkSL.
 *
 * I numeri che decidono l'aspetto non stanno nelle sorgenti: sono uniform, e i loro valori di serie stanno
 * tutti in [GlassBodyDefaults], in fondo a questo file. La ricetta e il profilo (F1A.5) li raccolgono da lì.
 */
package dev.antigravity.fluidengine.ui.glass.field

import org.intellij.lang.annotations.Language

/**
 * Gli uniform del corpo, oltre a quelli della geometria (che dichiara il nucleo: `offset`, `flex`,
 * `flexCenter`, `pieceCount`, `pieceRect`, `pieceRadii`, `pieceBlend`, `pieceSmooth`).
 *
 * È un blocco a parte, e sta **prima** dei blocchi di luce e d'ombra, perché quei due usano `materialize` e
 * `aa` senza dichiararli (vedi [GlassLightBlock] e [GlassTravelShadowBlock]): li dichiara il programma che li
 * include, e in AGSL un nome va dichiarato prima di chi lo legge.
 *
 * Le lunghezze sono in pixel dello spazio dello shader (con la cattura a risoluzione ridotta non sono pixel
 * dello schermo: passano da `toScopeSpace`, come oggi). I colori `layout(color)` non sono premoltiplicati.
 *
 * ### La lente
 * - `float thickness`: fin dove agisce il bordo. Dentro questa banda lo sfondo si sposta lungo la normale;
 *   più in dentro il vetro è piatto. Non deve superare mezzo lato corto del pezzo più piccolo: oltre, le
 *   bande di due lati opposti si incontrano sull'asse del pezzo e lì la normale salta. Lo garantisce chi
 *   prepara gli uniform, come oggi per `refractionHeight`;
 * - `float refraction`: lo spostamento massimo, sul contorno, **negato** come in `effects/Lens.kt` (un valore
 *   negativo campiona verso l'interno: il vetro è una lente convessa, e al bordo si vede ciò che sta più in
 *   dentro). Il profilo fra il contorno e `thickness` è `glassCircleMap`, quello di Kyant;
 * - `float dome`: 0..1, la **cupola**. È il peso di una seconda direzione sommata alla normale, quella che
 *   esce dal centro del pezzo: a zero la lente piega solo di traverso al bordo (un bordo smussato), a uno un
 *   pezzo piccolo si legge come una goccia. È un peso e non un interruttore perché un tasto che diventa un
 *   pannello la perde lungo il viaggio, non a metà strada;
 * - `float dispersion`: 0..1, la frangia cromatica: di quale frazione dello spostamento il blu piega più del
 *   verde e il rosso meno. Tre campioni invece dei sette di Kyant (R3 cap. 9), e solo dove la lente piega;
 *   a zero il ramo non si esegue e il campione è uno;
 * - `float magnify`: l'ingrandimento della goccia in moto, attorno al centro del pezzo: zero a riposo. È
 *   quello di `SlabGroupRefractionShaderString`, ma il centro è quello che il campo restituisce (in un gruppo
 *   ogni pezzo ingrandisce attorno a sé), non uno uniform in più.
 *
 * ### Il materiale
 * - `float materialize`: 0 = l'ingresso com'è (resta la sola maschera), 1 = materiale pieno. Scala insieme
 *   lente, ingrandimento, frangia, scurimento, velo, tinta, ombreggiatura, grana, e la luce e l'ombra in
 *   corpo. Ogni termine ne dipende in modo continuo: fra 0 e 1 non c'è un salto. È ciò in cui diventano
 *   `intensity` e la comparsa (piano 3.3), al posto del raggio di sfocatura, che non si può animare senza
 *   rifare la cattura;
 * - `layout(color) half4 veilColor`: il **velo**, il colore proprio del vetro posato sopra ciò che trasmette.
 *   L'alfa è la sua forza. È ciò che fa staccare il tasto da una pagina piatta: su bianco puro lo porta
 *   *sotto* il bianco, su una pagina grigio chiaro sopra;
 * - `float veilAdapt`: 0 = il velo è quello di `veilColor`, uguale ovunque. Sopra zero il velo **si
 *   ispessisce dove lo sfondo gli somiglia**: la sua alfa diventa `a * (1 + veilAdapt * v²)`, con `v` = 1 meno
 *   la distanza fra la luminanza dello sfondo e quella del velo. È la legge che i bersagli di R5 chiedono e
 *   che un velo costante non dà (su bianco e su pagina chiara il corpo di iOS 27 è quasi opaco, su un colore
 *   saturo lascia passare metà): vale per i due lati senza cambiare formula, perché un velo scuro somiglia
 *   agli sfondi scuri. **In F1A resta a zero**: lato e velo vengono dal tema, e accenderla è lavoro di F2
 *   (piano 3.9), che la tara. La forma della legge è «a occhio», 2026-11-11;
 * - `layout(color) half4 tintColor`, `float tintMode`, `float tintFollow`: la **tinta**, sopra il velo.
 *   `tintMode` 0 = pellicola fissa: il colore con la sua alfa, com'è, cioè la pellicola che oggi
 *   `glassSurface` disegna sopra la lente (compatibilità). `tintMode` 1 = la tinta di Apple: la tonalità
 *   resta e la luminosità segue lo sfondo, scalata di `1 - tintFollow * (1 - luma dello sfondo)`. I valori
 *   fra 0 e 1 sono la via di mezzo, per un viaggio fra un tasto tinto e uno no;
 * - `layout(color) half4 bodyShade`, `float bodyShadeDepth`, `float2 bodyShadeLean`: l'**ombreggiatura del
 *   corpo**. Il corpo vira verso il colore di `bodyShade` avvicinandosi al contorno: al massimo dell'alfa sul
 *   contorno, a zero a `bodyShadeDepth` pixel da lì, con caduta quadratica. `bodyShadeLean` la sbilancia: è
 *   un vettore nel sistema del campo (y verso il basso) che punta al lato più ombreggiato, lungo quanto lo
 *   sbilanciamento (0 = uguale su tutto il perimetro, 1 = tutta su quel lato e niente sull'opposto). È la
 *   parte del volume che non ha bisogno di uno sfondo da piegare: su un fondo piatto la lente non fa niente,
 *   e questo resta. Come `thickness`, `bodyShadeDepth` non deve superare mezzo lato corto;
 * - `float dim`: 0..1, lo scurimento **sotto** il vetro, cioè di ciò che trasmette, prima del velo: è lo
 *   strato che la variante `Clear` mette dietro di sé per tenere leggibile il contenuto (R4: 35%);
 * - `float grain`: l'ampiezza, in livelli su 255, di una grana fissa sommata al colore. Non è un effetto:
 *   velo e ombreggiatura sono rampe di pochi livelli su decine di pixel, e su una pagina piatta a 8 bit
 *   diventano scalini larghi; mezzo livello di grana li scioglie. Zero la spegne.
 *
 * ### Maschera e blocchi
 * - `float aa`: la larghezza in pixel della rampa di copertura sul contorno (1,25 px, come oggi);
 * - `float lightInBody`: 0 a riposo, 1 in viaggio. Vedi [GlassBodyBlock].
 */
@Language("AGSL")
internal const val GlassBodyUniformsBlock = """
uniform float thickness;
uniform float refraction;
uniform float dome;
uniform float dispersion;
uniform float magnify;
uniform float materialize;
layout(color) uniform half4 veilColor;
uniform float veilAdapt;
layout(color) uniform half4 tintColor;
uniform float tintMode;
uniform float tintFollow;
layout(color) uniform half4 bodyShade;
uniform float bodyShadeDepth;
uniform float2 bodyShadeLean;
uniform float dim;
uniform float grain;
uniform float aa;
uniform float lightInBody;"""

/**
 * Il corpo vero e proprio, uguale per le due varianti: cambia solo il nucleo che gli sta sopra.
 *
 * È una funzione, `half4 glassBody(float2 coord)`, e non un `main`: una stringa con un `main` è per il banco
 * e per il lint una sorgente completa, da compilare da sola e da registrare nella sonda, e questa da sola non
 * compila. Il `main` lo scrivono i due programmi, ed è una riga.
 *
 * Si aspetta, già dichiarati: `uniform shader content`, il nucleo (`glassFieldPoint`, `glassField`,
 * `glassCircleMap`), [GlassBodyUniformsBlock], e i due blocchi `glassLightParts` e `glassTravelShadow`.
 *
 * ### L'ordine dentro un pixel
 * 1. il campo, una volta: distanza, normale, centro; da qui la copertura;
 * 2. la **lente**: lo sfondo campionato nel punto spostato (`content.eval(coord + spostamento)`: lo
 *    spostamento si calcola nel sistema del campo e si applica alla coordinata del layer; con la flessione
 *    di F6.1 le due differiscono di una matrice vicina all'identità, che qui non si applica allo
 *    spostamento);
 * 3. lo scurimento `dim`, sotto tutto il resto;
 * 4. il **velo**, in `SrcOver` sopra lo sfondo;
 * 5. la **tinta**, sopra il velo;
 * 6. l'**ombreggiatura**, per ultima: scurisce anche un tasto tinto, come fa il vetro di Apple (R4 A7: il
 *    rosso ha un gradiente laterale);
 * 7. la grana;
 * 8. con `lightInBody`: la luce che si somma, il filo sopra, l'ombra sotto (vedi qui sotto);
 * 9. la copertura, una volta, su tutto.
 *
 * Velo, tinta e scurimento si compongono in `SrcOver` e non moltiplicando l'ingresso: dove la cattura è
 * trasparente (un fondale vuoto) il vetro ha lo stesso il suo colore, come oggi la pellicola.
 *
 * ### `lightInBody`: luce e ombra in corpo
 * A riposo vale 0: il corpo non chiama né `glassLightParts` né `glassTravelShadow`, ed esce subito dove la
 * copertura è nulla. La luce la disegna il suo programma, sopra, in due disegni; l'ombra le due maschere,
 * sotto.
 *
 * In viaggio vale 1 (la sagoma cambia a ogni fotogramma e il corpo si riesegue comunque: piano 3.5), e il
 * corpo compone tutto da sé, in quest'ordine, dal basso:
 *
 *     ombra    glassTravelShadow(p, d) * materialize        fuori dalla sagoma, e scavata sotto di essa
 *     corpo    i passi 2-7                                   dentro la sagoma
 *     luce     glowing * materialize                         SOMMATA al corpo, saturando a 1: è `Plus`
 *     filo     rim * materialize                             sopra, in SrcOver
 *
 *     acceso = min(corpo + luce, 1)
 *     uscita = (filo sopra acceso) * copertura + ombra * (1 - copertura)
 *
 * La luce ha due parti perché si compongono in due modi (F1A.3b, vedi [GlassLightBlock]): riga chiara, alone
 * e bagliore del dito sono luce e **si sommano**; il filo **copre**. `glassLightParts` le restituisce
 * insieme, coi conti comuni fatti una volta. L'ordine è quello dei due disegni della luce a riposo, prima la
 * somma (`Plus`) e poi il filo (`SrcOver`): così il bagliore non schiarisce il filo, e fra viaggio e riposo
 * non c'è uno scalino. La somma è su tutti e quattro i canali, come `Plus`: su un corpo opaco l'alfa resta 1,
 * e su una cattura trasparente (un fondale vuoto) la luce porta con sé la propria alfa.
 *
 * L'ombra è scavata con `1 - copertura`, non composta sotto il corpo: sotto un vetro che lascia passare
 * metà dello sfondo un'ombra non scavata si vedrebbe attraverso, e il vetro di Apple non lo fa (R4 A3: «mai
 * una linea di contatto netta»). È la stessa regola delle maschere a riposo. La luce prende la copertura
 * insieme al corpo: sul contorno filo e corpo sfumano con la stessa rampa e non resta un orlo di corpo
 * senza filo. Luce e ombra prendono `materialize` qui, non nei loro blocchi, che restituiscono il valore
 * pieno.
 *
 * Con gli uniform di luce e d'ombra a zero i due blocchi restituiscono zero, e l'uscita con `lightInBody` a
 * 1 è identica a quella con 0, pixel per pixel: `(0 sopra min(corpo + 0, 1)) * c + 0 * (1 - c) = corpo * c`
 * (lo prova il banco: `prova-corpo-luce-in-corpo-0` contro `-1`, `compare` a differenza zero).
 */
@Language("AGSL")
internal const val GlassBodyBlock = """
half4 glassBodyOver(half4 top, half4 under) {
    return top + under * (1.0 - top.a);
}

half4 glassBody(float2 coord) {
    float2 p = glassFieldPoint(coord);
    float2 normal;
    float2 center;
    float d = glassField(p, normal, center);
    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    float amount = clamp(materialize, 0.0, 1.0);

    if (coverage <= 0.0) {
        if (lightInBody < 0.5) return half4(0.0);
        return glassTravelShadow(p, d) * half(amount);
    }

    float depth = max(-d, 0.0);

    // La lente: spostamento lungo la normale (piu' la cupola) nella banda del bordo, e l'ingrandimento.
    float band = 1.0 - clamp(depth / max(thickness, 0.0001), 0.0, 1.0);
    float bend = glassCircleMap(band) * refraction * amount;
    float2 fromCenter = p - center;
    float fromCenterLen = length(fromCenter);
    float2 lensDir = normal + dome * (fromCenterLen > 0.0001 ? fromCenter / fromCenterLen : float2(0.0));
    // Accorciata a 1, non normalizzata: dove le normali di due pezzi si annullano (in mezzo a un ponte) lo
    // spostamento deve spegnersi, non cambiare verso di colpo.
    lensDir /= max(length(lensDir), 1.0);
    float2 at = coord - fromCenter * (magnify * amount) + bend * lensDir;

    half4 seen;
    if (dispersion > 0.0 && abs(bend) > 0.01) {
        // Tre campioni: il blu piega di piu', il rosso di meno, il verde sta in mezzo.
        float2 spread = lensDir * (bend * dispersion);
        half4 mid = content.eval(at);
        seen = half4(content.eval(at - spread).r, mid.g, content.eval(at + spread).b, mid.a);
    } else {
        seen = content.eval(at);
    }

    // Lo scurimento sotto il vetro (variante limpida): nero in SrcOver sopra cio' che si trasmette.
    half dimming = half(clamp(dim, 0.0, 1.0) * amount);
    seen = glassBodyOver(half4(0.0, 0.0, 0.0, dimming), seen);

    half3 lumaWeights = half3(0.2126, 0.7152, 0.0722);
    half backdropLuma = dot(seen.rgb, lumaWeights);

    // Il velo: piu' spesso dove lo sfondo gli somiglia, se `veilAdapt` lo chiede.
    half likeness = 1.0 - abs(backdropLuma - dot(veilColor.rgb, lumaWeights));
    half veil = clamp(veilColor.a * (1.0 + half(veilAdapt) * likeness * likeness), 0.0, 1.0) * half(amount);
    half4 body = glassBodyOver(half4(veilColor.rgb * veil, veil), seen);

    // La tinta: pellicola fissa (tintMode 0) o colore la cui luminosita' segue lo sfondo (tintMode 1).
    half follow = 1.0 - half(clamp(tintMode, 0.0, 1.0) * tintFollow) * (1.0 - backdropLuma);
    half tinting = tintColor.a * half(amount);
    body = glassBodyOver(half4(tintColor.rgb * follow * tinting, tinting), body);

    // L'ombreggiatura: il corpo vira verso `bodyShade` avvicinandosi al contorno, di piu' sul lato di `bodyShadeLean`.
    float2 outward = normal / max(length(normal), 1.0);
    float shadeBand = 1.0 - clamp(depth / max(bodyShadeDepth, 0.0001), 0.0, 1.0);
    float facing = (1.0 + dot(outward, bodyShadeLean)) / (1.0 + length(bodyShadeLean));
    half shading = bodyShade.a * half(shadeBand * shadeBand * max(facing, 0.0) * amount);
    body.rgb = mix(body.rgb, bodyShade.rgb * body.a, shading);

    // La grana: mezzo livello di rumore fisso, contro gli scalini delle rampe a 8 bit.
    if (grain > 0.0) {
        float noise = fract(52.9829189 * fract(dot(coord, float2(0.06711056, 0.00583715)))) - 0.5;
        body.rgb = clamp(body.rgb + half(noise * grain * amount / 255.0) * body.a, half3(0.0), half3(body.a));
    }

    if (lightInBody < 0.5) return body * half(coverage);

    // In viaggio: la luce si somma al corpo e satura (e' `Plus`), il filo le si posa sopra, l'ombra sta sotto
    // ed e' scavata dalla sagoma.
    half4 glowing;
    half4 rim;
    glassLightParts(p, d, normal, true, glowing, rim);
    half4 lit = min(body + glowing * half(amount), half4(1.0));
    lit = glassBodyOver(rim * half(amount), lit);
    half4 under = glassTravelShadow(p, d) * half(amount);
    return lit * half(coverage) + under * half(1.0 - coverage);
}"""

/**
 * Il corpo di **una lastra**: un rettangolo arrotondato, senza ciclo. È il programma di ogni vetro fermo che
 * non fa parte di un gruppo, cioè della grande maggioranza.
 *
 * Ingresso: `uniform shader content`, l'uscita della catena a monte. Uniform: quelli di [GlassFieldKernel1],
 * di [GlassBodyUniformsBlock], di [GlassLightBlock] e di [GlassTravelShadowBlock] (questi ultimi due si
 * leggono solo con `lightInBody` a 1, ma sono dichiarati sempre: uno uniform dichiarato e non impostato vale
 * zero, uno impostato e non dichiarato lancia).
 *
 * Con un pezzo solo dà gli stessi pixel di [GlassBody8ShaderString]: lo prova il banco
 * (`scenes/f1a/prova-corpo-variante-1.json` contro `-8`, `compare` a differenza zero).
 */
@Language("AGSL")
internal const val GlassBody1ShaderString = """
uniform shader content;
$GlassFieldKernel1
$GlassBodyUniformsBlock
$GlassLightBlock
$GlassTravelShadowBlock
$GlassBodyBlock

half4 main(float2 coord) {
    return glassBody(coord);
}"""

/**
 * Il corpo di un **gruppo**: fino a [GlassFieldMaxPieces] lastre fuse, con uscita su `pieceCount`. Gruppi,
 * palco di una trasformazione, viaggi fra lastre. Dopo S1 (cancello C3) è la sola variante oltre a quella a
 * un pezzo: il ciclo a otto che esce sul conteggio costa come uno tagliato a due o a quattro.
 *
 * Nella zona di fusione lente, cupola e ombreggiatura sono continue perché usano la normale e il centro che
 * il campo ha già mescolato con lo stesso peso dello smooth minimum, e perché quella normale qui **non si
 * normalizza**: si accorcia a 1 se è più lunga. In mezzo a un ponte le normali dei due pezzi si guardano e
 * la loro mescola passa per zero: normalizzata cambierebbe verso di colpo (una cucitura, vista sul banco con
 * la griglia), così com'è spegne lo spostamento, che è ciò che fa una superficie piatta. Su un pezzo solo la
 * normale è lunga 1 e con la cupola di più, quindi non cambia niente. Velo e tinta non dipendono dalla
 * geometria e non hanno una cucitura da mostrare.
 */
@Language("AGSL")
internal const val GlassBody8ShaderString = """
uniform shader content;
$GlassFieldKernel8
$GlassBodyUniformsBlock
$GlassLightBlock
$GlassTravelShadowBlock
$GlassBodyBlock

half4 main(float2 coord) {
    return glassBody(coord);
}"""

/**
 * I valori di serie del corpo: **l'unico posto** in cui stanno i numeri che ne decidono l'aspetto. La
 * ricetta e il profilo (F1A.5) li leggono da qui; le scene di prova del banco
 * (`tools/glassbench/scenes/f1a/prova-corpo-*.json`) le scrive uno script che li legge da questo file
 * (`artifacts/liquid-glass/bench/f1a-2/genera_scene.py`), quindi un ritocco qui e una corsa le riallineano.
 *
 * Le lunghezze sono in dp. I colori sono `0xAARRGGBB`, non premoltiplicati, come `Color(...)` di Compose.
 * Dove c'è scritto «a occhio» la data è il 2026-11-11 e l'occhio è quello di chi ha tarato sul banco: la
 * scelta di Alessio arriva con le tavole di F1A.5 e va scritta qui al posto di questa.
 */
internal object GlassBodyDefaults {
  /** `aa`: la rampa della maschera, in pixel dello spazio dello shader. È quella di oggi. */
  const val Aa: Float = 1.25f

  /**
   * `grain`: **spenta**. Mezzo livello di grana scioglie gli scalini delle rampe, ma alza il gradiente medio
   * di una superficie liscia a circa 0,5 livelli per pixel, cioè falsa la «nitidezza che resta» di `measure`
   * proprio dove si tara la smerigliatura (pavimento all'8% invece dell'1–4%). Sul banco gli scalini sono di
   * un livello e larghi 10–13 px: se sul dispositivo si vedono (voce del backlog di F1A), il rimedio è 0,5.
   */
  const val Grain: Float = 0f

  /** `pieceSmooth` dei grandi a riposo: lo `smoothing` di iOS dell'engine (`ContinuousCornerShape`). */
  const val CornerSmoothing: Float = 0.6f

  /**
   * La saturazione della catena, prima della sfocatura. Oggi è 1,6 (`vibrancy`); 1,5 perché sotto un velo
   * al 50% il colore resti «quello dello sfondo» senza diventare acido su una foto. A occhio.
   */
  const val Saturation: Float = 1.5f

  /**
   * Il **raggio di sfocatura** della catena per tasti, capsule e barre: è lui la smerigliatura (a riposo
   * `w = 0`). Tarato con `measure` sulla capsula di R4 A5 (`prova-corpo-rif-capsula-testo`), dove il vetro di
   * iOS 26 corrisponde a 4,5 dp (26% di contrasto, 10–12% di nitidezza: è il raggio del ruolo `Floating` di
   * oggi): con 12 dp resta il **9,8% del contrasto** (R5 M1: 6–13%, alta nel rapporto) e il **4,3% della
   * nitidezza, cioè il 38–43% di quella di iOS 26** (R5 M1: 35–45%, alta nel rapporto). Sull'arte sintetica
   * di `rif27-tabbar` lo stesso raggio lascia il 22% (fuori): quella scena ha più basse frequenze dell'arte
   * di Apple, e nemmeno il vetro «iOS 26» ci sta nei suoi numeri (48% contro 19–27%). Vedi il rapporto di F1A.2.
   */
  const val FrostBlurDp: Float = 12f

  /**
   * Il raggio per menu e pannelli: «non meno smerigliati di una barra» (piano, tabella di F1A), e in iOS 26
   * lo erano il doppio (R4 A4). Un passo sopra la barra, **a occhio**: è una delle scelte di gusto («i grandi»).
   */
  const val FrostBlurLargeDp: Float = 18f

  /**
   * La variante «ancora più smerigliata» delle tavole: sull'arte di `rif27-tabbar` porta il contrasto al 13%
   * e l'intervallo del testo dietro la barra a 16 livelli (R5 M1: 16), ma sul testo di R4 A5 lascia il 10%
   * della nitidezza di iOS 26 invece del 35–45%. Non è di serie.
   */
  const val FrostBlurWideDp: Float = 24f

  /**
   * `veilColor` sul lato chiaro: un grigio quasi bianco al 50%. Misurato: +4 su pagina 238 (R5 M4: +2…+5,
   * alta), −4/−5 su bianco 253 e 255 (R5 M4: −4…−11, alta), 48% su colore saturo e scuro (R5 M1: ≈0,5,
   * alta), 148 su foto a luma 50 (R5 M3: 141–146 ±2, media). Fuori di mezzo livello: +5,5 su pagina 235
   * (R5 M1: +8 ±2). Un velo costante non può dare insieme +8 su 235 e 146 su 51: lo dà `veilAdapt` (F2).
   */
  const val VeilLight: Long = 0x80F6F6F8

  /**
   * `veilColor` sul lato scuro, proposta **più chiara**: 67 sulla pagina `#0D0D0F`, 60 sul nero, 71 sul varco
   * nero di `rif27-scuro` (R5 M3: 58–78, bassa). Il tema scuro è quasi non misurato: **a occhio**, ed è
   * una delle scelte di gusto.
   */
  const val VeilDark: Long = 0x80787880

  /** `veilColor` sul lato scuro, proposta **più profonda**: 50 sulla pagina scura, 45 sul nero. A occhio. */
  const val VeilDarkDeep: Long = 0x9E48484E

  /**
   * `veilAdapt`: zero in F1A (lato e velo vengono dal tema). Il valore che sul banco chiude i bersagli di
   * R5 sul lato chiaro è 0,8 (vedi il rapporto di F1A.2): è un punto di partenza per F2, non una taratura.
   */
  const val VeilAdapt: Float = 0f

  /**
   * `bodyShade` sul lato chiaro: nero al 7%. Con profondità e sbilanciamento qui sotto, il tondo su bianco
   * 253 misura 243 in alto, 249 al centro, 248 in basso (R5 M4: 243 in alto, 249 in basso, media).
   */
  const val ShadeLight: Long = 0x12000000

  /** `bodyShade` sul lato scuro: più forte, perché un 7% di nero su un corpo a 60 non si vede. A occhio. */
  const val ShadeDark: Long = 0x33000000

  /** `bodyShadeDepth`: mezzo tasto da 44 dp; chi prepara gli uniform la limita a mezzo lato corto. A occhio. */
  const val ShadeDepthDp: Float = 22f

  /**
   * La lunghezza di `bodyShadeLean`, verso l'alto `(0, -ShadeLean)`: l'ombreggiatura è 4 volte più forte in
   * alto che in basso, come il tondo di R5 M4. Zero dà il «più scuro verso i bordi» della tab bar disegnata;
   * girato in diagonale dà il gradiente del tondo di iOS 26 (R4 A2), che resta una variante.
   */
  const val ShadeLean: Float = 0.6f

  /** `tintFollow`: su nero la tinta scende al 58% del suo valore, su bianco resta piena (R4 A7, alta). */
  const val TintFollow: Float = 0.42f

  /** `dispersion` dove il ruolo la chiede (oggi i soli tasti): il blu piega il 30% in più. A occhio. */
  const val Dispersion: Float = 0.3f

  /**
   * La **lente**, tre proposte (né R4 né R5 la misurano: è una scelta di gusto, «a occhio»). Ognuna sono tre
   * numeri: `Band` = `thickness` come frazione del lato corto (mai oltre [LensBandMaxDp]); `Bend` =
   * `refraction` come multiplo di `thickness`; `Dome` = la cupola di un pezzo da 44 dp, che scende a zero a
   * [LensDomeFadeEndDp] di lato corto. La leggera lascia fermo il 68% centrale di una lente tonda (R4 A2,
   * bassa: ≈60%), la media il 52%; la marcata su una capsula da 56 dp è la lente del ruolo `Floating` di
   * oggi (19 dp di banda, 24 di spostamento). Sotto la smerigliatura di serie le tre si distinguono poco:
   * si vedono in viaggio, nella comparsa e nella variante limpida.
   */
  const val LensLightBand: Float = 0.16f

  /** Vedi [LensLightBand]. */
  const val LensLightBend: Float = 0.6f

  /** Vedi [LensLightBand]. */
  const val LensLightDome: Float = 0.3f

  /** Vedi [LensLightBand]. La media è quella delle scene di prova. */
  const val LensMediumBand: Float = 0.24f

  /** Vedi [LensLightBand]. */
  const val LensMediumBend: Float = 0.9f

  /** Vedi [LensLightBand]. */
  const val LensMediumDome: Float = 0.5f

  /** Vedi [LensLightBand]. */
  const val LensStrongBand: Float = 0.34f

  /** Vedi [LensLightBand]. */
  const val LensStrongBend: Float = 1.25f

  /** Vedi [LensLightBand]. */
  const val LensStrongDome: Float = 0.8f

  /** Il tetto della banda: su un pannello la lente è un bordo, non mezzo pannello. */
  const val LensBandMaxDp: Float = 40f

  /** Fino a questo lato corto la cupola è piena. */
  const val LensDomeFadeStartDp: Float = 44f

  /** Da questo lato corto in su la cupola è zero: un pannello non è una goccia. */
  const val LensDomeFadeEndDp: Float = 120f
}
