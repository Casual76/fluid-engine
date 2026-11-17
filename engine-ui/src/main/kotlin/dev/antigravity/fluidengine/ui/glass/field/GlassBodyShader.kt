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
 * I numeri che decidono l'aspetto non stanno nelle sorgenti e non stanno in questo file: sono uniform, i loro
 * valori di serie sono nel profilo ([GlassLiquidProfile], `GlassLiquidProfile.kt`) e li imposta la ricetta
 * (`glassRecipe`). Fino a F1A.4 stavano qui in coda, in `GlassBodyDefaults`: F1A.5 li ha spostati.
 */
package dev.antigravity.fluidengine.ui.glass.field

import dev.antigravity.fluidengine.ui.fluid.glassMixRadius
import org.intellij.lang.annotations.Language

// ------------------------------------------------------------------------------------------------------------
// La comparsa e la scomparsa per lente (F3.3, R4 B3): che cosa fa `materialize` mentre un vetro nasce o se ne va.
//
// Il piano flottante dà a ogni pezzo che compare o scompare un `materialize` 0..1 (la molla di `GlassPlaneJourney`,
// stirata in 430 ms per la comparsa e 470 per la scomparsa) e la sagoma che parte 1,09 più larga. Qui quel numero
// diventa tre canali, perché in Apple il vetro non si accende tutto insieme:
//
//  - il **contorno** (filo e riflesso, cioè tutta la luce): c'è per primo e se ne va per ultimo. Nei primi 67 ms della
//    comparsa si vede solo lui, già 8–10% più largo, con la rifrazione quasi nulla; nella scomparsa resta da 400 a
//    470 ms, poi niente;
//  - la **lente** e il **corpo** (spostamento, velo, tinta, ombreggiatura, smerigliatura, ombra): crescono dopo il
//    contorno («la rifrazione cresce», 100–230 ms) e se ne vanno prima («la rifrazione scende a zero», 130–400 ms);
//  - il **simbolo** (il contenuto): compare dilatato due volte e traslucido, poi si condensa alla sua forma e diventa
//    opaco (270–370 ms); nella scomparsa svanisce per primo (0–130 ms).
//
// **L'opacità del corpo non entra mai**: la copertura della sagoma resta piena, ed è il **raggio di sfocatura** che porta
// lo sfondo da nitido a smerigliato, crescendo con la lente (`glassMixRadius` di F3.4, `ui/fluid/FluidGlassContentTransition.kt`).
// Non la mescola a monte `w`: `w` è una dissolvenza (piano 3.6), e per i 430 ms della comparsa le lettere nitide e
// grigie restavano sopra la loro copia sfocata (F3.4 l'ha misurato: 15 fotogrammi ambigui con la rampa lineare, 5 con
// una S stretta, che diventa uno scatto). Un ramo solo, nessuna immagine doppia a nessun istante; il margine della
// cattura resta quello del raggio pieno (invariante 1: la cattura non si rifà). Il prezzo è un `RenderEffect` nuovo a
// ogni fotogramma della comparsa, da misurare in S3. A `materialize` zero il vetro è lo sfondo nitido com'è: niente
// lampo all'ingresso.
//
// I canali sono funzioni del solo `materialize`, non del tempo: un viaggio interrotto (una comparsa che diventa
// scomparsa a metà) li porta con sé senza salti, perché il piano tiene continuo `materialize`. Il prezzo è che la
// molla del piano corre nei primi istanti: nella scomparsa la lente se ne va in 10–125 ms invece che in 130–400. L'ordine
// delle fasi (simbolo, lente, contorno) resta quello di Apple; i tempi assoluti di B3 sono di fiducia media.
// ------------------------------------------------------------------------------------------------------------

/** Fin dove di `materialize` sale il contorno: a 0,3 (≈60 ms della comparsa) è pieno. */
internal const val GlassMaterializeContourEnd: Float = 0.3f

/** Da dove a dove di `materialize` salgono lente e corpo: quasi nulli fino a 67 ms, pieni a ≈200 ms. */
internal const val GlassMaterializeLensFrom: Float = 0.25f

/** Vedi [GlassMaterializeLensFrom]. */
internal const val GlassMaterializeLensTo: Float = 0.97f

/** Il simbolo: traslucido da 0,5 a 0,8 di `materialize` (≈90–150 ms), opaco e condensato da 0,95 a 1 (≈220–330 ms). */
internal const val GlassMaterializeSymbolFrom: Float = 0.5f

/** Vedi [GlassMaterializeSymbolFrom]. */
internal const val GlassMaterializeSymbolHalf: Float = 0.8f

/** Vedi [GlassMaterializeSymbolFrom]. */
internal const val GlassMaterializeSymbolCondense: Float = 0.95f

/** Quanto è dilatato il simbolo quando compare (R4 B3: ≈2×). */
internal const val GlassMaterializeSymbolScale: Float = 2f

/** I tre canali della comparsa a un dato `materialize`: vedi la nota in testa al file. */
internal class GlassMaterializeChannels {
  /** Il contorno, 0..1: filo e riflesso. */
  var contour: Float = 1f

  /** La lente e il corpo, 0..1: mai sopra [contour]. */
  var lens: Float = 1f

  /** L'opacità del simbolo, 0..1. */
  var symbolAlpha: Float = 1f

  /** La scala del simbolo attorno al suo centro: da [GlassMaterializeSymbolScale] a 1. */
  var symbolScale: Float = 1f
}

private fun glassMaterializeStep(from: Float, to: Float, x: Float): Float {
  val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/** I canali della comparsa a [materialize] (0 = il vetro non c'è, 1 = pieno), scritti in [out]. Pura. */
internal fun glassMaterializeChannels(
  materialize: Float,
  out: GlassMaterializeChannels = GlassMaterializeChannels(),
): GlassMaterializeChannels {
  val m = if (materialize.isFinite()) materialize.coerceIn(0f, 1f) else 1f
  out.contour = glassMaterializeStep(0f, GlassMaterializeContourEnd, m)
  out.lens = minOf(glassMaterializeStep(GlassMaterializeLensFrom, GlassMaterializeLensTo, m), out.contour)
  out.symbolAlpha = 0.5f * glassMaterializeStep(GlassMaterializeSymbolFrom, GlassMaterializeSymbolHalf, m) +
    0.5f * glassMaterializeStep(GlassMaterializeSymbolCondense, 1f, m)
  out.symbolScale = GlassMaterializeSymbolScale -
    (GlassMaterializeSymbolScale - 1f) * glassMaterializeStep(GlassMaterializeSymbolCondense, 1f, m)
  return out
}

/**
 * Porta una ricetta **piena** ([recipe], fatta con `intensity` 1) alla comparsa di [materialize] (F3.3), con soli
 * uniform che il corpo ha già: `materialize` diventa il contorno (lo shader lo applica a tutto, luce compresa) e lente,
 * velo, tinta, ombreggiatura, scurimento, grana e ombra si scalano della quota della lente sul contorno, cosi' che nel
 * prodotto dello shader valgano la lente. Il raggio di sfocatura e la saturazione seguono la lente (il raggio con
 * `glassMixRadius`, un ramo solo: vedi la nota in testa); la mescola a monte resta quella del profilo, e il margine
 * della cattura resta quello del raggio pieno: la cattura non si rifà.
 *
 * A [materialize] 1 la ricetta resta com'è, al bit: un vetro fermo non passa mai di qui con un valore diverso.
 */
internal fun glassMaterializeRecipe(
  recipe: GlassRecipe,
  materialize: Float,
  channels: GlassMaterializeChannels = GlassMaterializeChannels(),
): GlassRecipe {
  val c = glassMaterializeChannels(materialize, channels)
  if (c.contour >= 1f && c.lens >= 1f) return recipe
  val share = if (c.contour > 0f) (c.lens / c.contour).coerceIn(0f, 1f) else 0f
  recipe.materialize = recipe.materialize * c.contour
  recipe.refractionDp *= share
  recipe.magnify *= share
  recipe.veilColor = recipe.veilColor.copy(alpha = recipe.veilColor.alpha * share)
  recipe.tintColor = recipe.tintColor.copy(alpha = recipe.tintColor.alpha * share)
  recipe.bodyShade = recipe.bodyShade.copy(alpha = recipe.bodyShade.alpha * share)
  recipe.dim *= share
  recipe.grain *= share
  // L'ombra della ricetta porta già l'intensità (chi la mette nel corpo la divide per `materialize`): qui la lente.
  recipe.shadowContactAlpha *= c.lens
  recipe.shadowAmbientAlpha *= c.lens
  // `w = 1` è il nitido (lente zero), `w = 0` il raggio pieno: la sfocatura cresce con la lente, un ramo solo.
  recipe.blurDp = glassMixRadius(1f - c.lens, recipe.blurDp)
  recipe.saturation = 1f + (recipe.saturation - 1f) * c.lens
  return recipe
}

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
 *   la distanza fra lo sfondo e il velo **sul canale in cui distano di più**. È la legge che i bersagli di R5
 *   chiedono e che un velo costante non dà (su bianco e su pagina chiara il corpo di iOS 27 è quasi opaco, su
 *   un colore saturo lascia passare metà): vale per i due lati senza cambiare formula, perché un velo scuro
 *   somiglia agli sfondi scuri. Il valore lo dà il profilo ([GlassLiquidVeil.adapt]). La parte guidata dalla
 *   sonda (F2.2, piano 3.9) non passa di qui ma dagli stessi uniform, impostati dalla ricetta poche volte al secondo:
 *   l'alfa di `veilColor` col pavimento del velo chiaro, il colore di `veilColor` che sul lato scuro va verso quello
 *   dei grandi, e la mescola dei due lati ([GlassLiquidAdaptive]). Un pavimento **per pixel** chiederebbe un uniform
 *   in più, e con lui ogni scrittore di scene del banco: la misura media basta al bersaglio di R4 A6 (vetro chiaro sul
 *   nero 140, bersaglio 136–143; vedi `artifacts/liquid-glass/bench/f2-2/misure.md`). La forma della legge è «a occhio», 2026-11-11. **Ritoccata da F1A.5**: fino a
 *   F1A.2 la distanza era fra le sole luminanze, e accesa portava il velo sopra un giallo saturo (luminoso
 *   quanto il velo) da 0,44 a 0,77, fuori dal bersaglio più solido di R5 (M1, alta: ≈0,5 su colore saturo).
 *   Sul canale più distante il giallo resta a 0,39–0,47 e i grigi non cambiano di un livello. Con `veilAdapt`
 *   a zero le due leggi danno gli stessi pixel;
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
 * - `float lightInBody`: 0 per una lastra di `glassSurface`, che la luce la disegna sul nodo; 1 per Fluid-physics,
 *   ferma o in viaggio. Vedi [GlassBodyBlock];
 * - `float2 nodeClip`: il **ritaglio del nodo**, per chi non ce l'ha. Una lastra di `glassSurface` ha lo strato
 *   ritagliato sulla sagoma, con il suo antialias, e la copertura del campo gli si moltiplica: lì `x` è 0. Chi
 *   disegna su un palco rettangolare (Fluid-physics, F1B.1) quel ritaglio non ce l'ha, e mette `x` a 1: il corpo
 *   moltiplica la copertura per quella che il ritaglio avrebbe dato, `clamp(0.5 - d + margine, 0, 1)`. Senza,
 *   il pixel di contorno di un pezzo in viaggio è il doppio più pieno di quello della stessa lastra ferma, e al
 *   passaggio di consegne il contorno ingrassa di mezzo pixel (misurato sul banco: fino a 66 livelli su un
 *   anello largo un pixel). `y` è il **margine d'angolo** in pixel: il ritaglio di una lastra raccordata sta al
 *   98% del raggio (`GlassSmoothedClipScale`), cioè negli angoli passa più largo della sagoma di
 *   `(1 - 0,98) · raggio · (|nx| + |ny| - 1)` e lì non taglia; zero per gli angoli circolari, dove ritaglio e
 *   sagoma coincidono. Tutto in pixel dello spazio dello shader: chi lo accende cattura a scala 1.
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
uniform float lightInBody;
uniform float2 nodeClip;"""

/** Con una sola pellicola il percorso a riposo conserva la composizione della ricetta. */
@Language("AGSL")
internal const val GlassSingleFilmBlock = """
half4 glassFilm(float2 p, half4 seen, half4 veiled, half luma, half amount) {
    half follow = 1.0 - half(clamp(tintMode, 0.0, 1.0) * tintFollow) * (1.0 - luma);
    half alpha = tintColor.a * amount;
    return half4(tintColor.rgb * follow * alpha, alpha) + veiled * (1.0 - alpha);
}"""

/** Pesa le pellicole nello spazio del campo, prima di luce, copertura e ombra comuni. */
@Language("AGSL")
internal const val GlassSlabFilmBlock = """
uniform float filmModes[$GlassFieldMaxPieces];
layout(color) uniform half4 film0;
layout(color) uniform half4 film1;
layout(color) uniform half4 film2;
layout(color) uniform half4 film3;
layout(color) uniform half4 film4;
layout(color) uniform half4 film5;
layout(color) uniform half4 film6;
layout(color) uniform half4 film7;

half4 glassFilmAt(int i) {
    if (i == 0) return film0;
    if (i == 1) return film1;
    if (i == 2) return film2;
    if (i == 3) return film3;
    if (i == 4) return film4;
    if (i == 5) return film5;
    if (i == 6) return film6;
    return film7;
}

half4 glassGroupFilm(float2 p, half4 seen, half4 veiled, half luma, half amount) {
    float distance = 0.0;
    half4 result = veiled;
    for (int i = 0; i < $GlassFieldMaxPieces; ++i) {
        if (float(i) >= pieceCount) break;
        float sd = glassSlab(p - pieceRect[i].xy, pieceRect[i].zw, pieceRadii[i], pieceSmooth[i]).x;
        half4 ink = glassFilmAt(i);
        float mode = filmModes[i];
        half follow = mode > 0.5 ? 1.0 - half(tintFollow) * (1.0 - luma) : 1.0;
        half alpha = ink.a * amount;
        half4 under = mode < -0.5 ? seen : veiled;
        half4 layer = half4(ink.rgb * follow * alpha, alpha) + under * (1.0 - alpha);
        if (i == 0) {
            distance = sd;
            result = layer;
        } else {
            float k = max(pieceBlend[i], 0.0001);
            float width = max(k, pieceSoft[i]);
            // Anche a collo rotto due lastre possono essere ancora sovrapposte.
            width = max(width, min(0.5 * min(pieceRect[i].z, pieceRect[i].w), max(0.0, -max(sd, distance))));
            float h = clamp(0.5 + 0.5 * (sd - distance) / width, 0.0, 1.0);
            result = mix(layer, result, half(h));
            float geo = clamp(0.5 + 0.5 * (sd - distance) / k, 0.0, 1.0);
            distance = mix(sd, distance, geo) - k * geo * (1.0 - geo) *
                (1.0 - smoothstep(0.0, k, -max(sd, distance)));
        }
    }
    return result;
}"""

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
 * Con la copertura piena il secondo termine è zero e l'ombra non si calcola: gli stessi pixel, ma sotto un menu
 * (sagoma ad anello) la maschera dell'ombra non rifà il ciclo sui lati in ogni pixel del corpo.
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
    // Il ritaglio del nodo, per chi non ce l'ha: vedi `nodeClip`.
    if (nodeClip.x > 0.5) {
        float corner = max(abs(normal.x) + abs(normal.y) - 1.0, 0.0);
        coverage *= clamp(0.5 - d + nodeClip.y * corner, 0.0, 1.0);
    }
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
    // Il profilo di spessore in un ponte: dove le normali di due pezzi si guardano la loro mescola si accorcia
    // (vale |1 - 2h| sull'asse fra i due) e li' il vetro e' piatto. La cupola si spegne con lo stesso peso: il
    // suo versore, da solo, girerebbe di mezzo giro attraversando l'asse (il centro mescolato passa per il
    // punto) e lascerebbe una piega. Il fattore 1.01 rende il peso esattamente 1 su un pezzo solo, dove la
    // normale e' unitaria a meno dell'arrotondamento: li' non cambia un bit.
    float flatness = clamp(length(normal) * 1.01, 0.0, 1.0);
    float2 lensDir = normal
        + (dome * flatness) * (fromCenterLen > 0.0001 ? fromCenter / fromCenterLen : float2(0.0));
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

    // Il velo: piu' spesso dove lo sfondo gli somiglia, se `veilAdapt` lo chiede. La somiglianza e' sul canale
    // che dista di piu': un giallo saturo e' luminoso quanto il velo, ma non gli somiglia.
    half3 apart = abs(seen.rgb - veilColor.rgb);
    half likeness = 1.0 - max(apart.r, max(apart.g, apart.b));
    half veil = clamp(veilColor.a * (1.0 + half(veilAdapt) * likeness * likeness), 0.0, 1.0) * half(amount);
    half4 body = glassBodyOver(half4(veilColor.rgb * veil, veil), seen);

    body = glassSurfaceFilm(p, seen, body, backdropLuma, half(amount));

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
    // Dove la sagoma copre tutto l'ombra è scavata a zero: non si calcola (giro di correzione di F1B.2, riga 15).
    // Sotto un menu la sua maschera rifaceva il ciclo dell'anello in quasi ogni pixel, per poi moltiplicarlo per 0.
    if (coverage >= 1.0) return lit;
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
$GlassShadowMask1
$GlassTravelShadowFunctionBlock
$GlassSingleFilmBlock
half4 glassSurfaceFilm(float2 p, half4 seen, half4 veiled, half luma, half amount) {
    return glassFilm(p, seen, veiled, luma, amount);
}
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
 *
 * La **cupola** ha bisogno dello stesso profilo (F1B.1): il suo versore punta via dal centro mescolato, che
 * nel collo attraversa il punto, e da solo girerebbe di mezzo giro in un paio di dp (una riga verticale
 * sull'asse mediale, con la sua frangia cromatica: vista sul banco, `bench/f1b-1/prova`). Il corpo la pesa con
 * la lunghezza della normale mescolata (`flatness`), che sull'asse fra due pezzi che si guardano vale zero e
 * su un pezzo solo uno.
 */
@Language("AGSL")
internal const val GlassGroupPrelude = """
uniform shader content;
$GlassFieldKernel8
$GlassBodyUniformsBlock
$GlassLightBlock
$GlassTravelShadowBlock
$GlassShadowMask8
$GlassTravelShadowFunctionBlock
"""

@Language("AGSL")
internal const val GlassBody8ShaderString = """
$GlassGroupPrelude
$GlassSingleFilmBlock
half4 glassSurfaceFilm(float2 p, half4 seen, half4 veiled, half luma, half amount) {
    return glassFilm(p, seen, veiled, luma, amount);
}
$GlassBodyBlock

half4 main(float2 coord) {
    return glassBody(coord);
}"""

/** Specializzazione per i gruppi con pellicole diverse. Le altre superfici conservano il programma più piccolo. */
@Language("AGSL")
internal const val GlassBodyFilms8ShaderString = """
$GlassGroupPrelude
$GlassSlabFilmBlock
half4 glassSurfaceFilm(float2 p, half4 seen, half4 veiled, half luma, half amount) {
    return glassGroupFilm(p, seen, veiled, luma, amount);
}
$GlassBodyBlock

half4 main(float2 coord) {
    return glassBody(coord);
}"""

/**
 * Il corpo di una **sagoma ad anello** (F1B.1): la goccia in viaggio e le sagome libere di Fluid-physics
 * (`FluidForm.Poly`), cioè ciò che non è un'unione di rettangoli arrotondati. Lo stesso [GlassBodyBlock] dei due
 * programmi a lastre sopra il nucleo [GlassFieldKernelRing]: lente, velo, tinta, ombreggiatura, luce e ombra in
 * corpo sono le stesse righe, e una goccia che si posa sulla sua lastra cambia nucleo senza cambiare materiale.
 *
 * Un anello non ha un programma di luce a riposo: tiene `lightInBody` a 1 anche da fermo (piano 3.4), cioè il
 * bagliore di una sagoma libera ferma ripaga la catena. Sono il Playground e poco altro.
 *
 * Costa il ciclo sui lati (al più [GlassFieldRingVertices]) per ogni pixel della fascia in cui il contorno può
 * stare (`bbox`, e per la goccia `dropReach`): fuori e nel vetro piatto il nucleo esce prima del ciclo. Con
 * l'ombra in viaggio accesa il ciclo gira una seconda volta nella sola fascia dell'ombra (`glassTravelShadow`).
 */
@Language("AGSL")
internal const val GlassBodyRingShaderString = """
uniform shader content;
$GlassFieldKernelRing
$GlassBodyUniformsBlock
$GlassLightBlock
$GlassTravelShadowBlock
$GlassShadowMaskRing
$GlassTravelShadowFunctionBlock
$GlassSingleFilmBlock
half4 glassSurfaceFilm(float2 p, half4 seen, half4 veiled, half luma, half amount) {
    return glassFilm(p, seen, veiled, luma, amount);
}
$GlassBodyBlock

half4 main(float2 coord) {
    return glassBody(coord);
}"""
