/*
 * La LUCE del materiale `Liquid`: filo sul perimetro, riflessi interni, bagliore del dito. Uno strato solo,
 * senza ingressi, che restituisce un colore premoltiplicato da posare in `SrcOver` sopra il corpo e sotto il
 * contenuto (piano 3.3, 3.5).
 *
 * Qui ci sono, nell'ordine: i numeri che danno forma alle curve (costanti dentro la sorgente), i valori di
 * serie degli uniform (che la ricetta e il profilo di F1A.5 raccolgono da qui), il blocco che il corpo include
 * in viaggio, e i due programmi della luce a riposo.
 *
 * ### Da dove viene la forma del bordo
 * Dalle schede M1, M3 e M4 di R5, e da una lettura fatta per questo pacchetto sul ritaglio di Apple
 * `artifacts/liquid-glass/riferimenti/ios27/ritagli/T6_scheda_prominente_269_0519.jpg` (269 @5:19, classe B,
 * 3,18 px per punto): il tondo della scheda prominente su pagina 238, un profilo radiale ogni 15°
 * (2026-11-11; il conto è in `artifacts/liquid-glass/bench/f1a-3/profili.py`, e nel rapporto di F1A.3 c'è la
 * tabella). È una sola inquadratura, quindi fiducia **media**, ma è l'unico tondo di iOS 27 su cui il riflesso
 * si distingue (R5 lo misura su capsule). Dice quattro cose che le schede non dicono:
 *  1. il filo scuro c'è su tutto il perimetro ma **non è uguale**: sui fianchi tocca 150–156 su un corpo a 236
 *     (−82…−88), dentro i lobi di luce 174–197 (−40…−62). R5 M4 dà 172–203 «uguale su tutti i lati» per il
 *     tondo su bianco: i due intervalli si sovrappongono, e la differenza fra i lati sta dentro il bersaglio
 *     del piano (−45…−80);
 *  2. il riflesso è fatto di **due cose**: una riga quasi bianca spessa quanto il filo o poco più (≈4 px,
 *     1,2 pt), e dietro un alone che parte da metà forza e si spegne in ≈20 px (6–7 pt);
 *  3. la riga è piena fra 30° e 150° e fra 210° e 330° (60° per parte dalla verticale) ed è quasi sparita a
 *     15° dall'orizzontale: sui fianchi **non c'è**;
 *  4. l'alone occupa un arco più stretto della riga: a 45° dalla verticale c'è la riga e non l'alone.
 */
package dev.antigravity.fluidengine.ui.glass.field

import org.intellij.lang.annotations.Language

// ---------------------------------------------------------------------------------------------------------
// I numeri che danno forma alle curve. Sono costanti dentro la sorgente e non uniform perché non sono
// manopole di gusto: dicono che cosa *vuol dire* uno uniform (quanto è «larga» una mezza ampiezza, dove
// finisce una riga). Chi vuole un altro bordo cambia gli uniform; chi cambia questi cambia la sorgente, cioè
// un programma nuovo per la cache.
// ---------------------------------------------------------------------------------------------------------

/**
 * Quanto del lobo è **pieno**, in frazione della sua mezza ampiezza: entro questa frazione la riga chiara è
 * al massimo, poi scende e si spegne alla mezza ampiezza.
 *
 * È ciò che dà un senso a `reflexWidths`: senza un tratto pieno il lobo sarebbe al massimo in un punto solo.
 * Con 0,72 e la mezza ampiezza di serie (80°) la riga è piena fino a 58° dalla verticale e spenta a 80°, che è
 * ciò che si legge sul ritaglio T6 (piena a 60°, quasi nulla a 75°). Per i due archi di iOS 26 (mezze
 * ampiezze 55° e 35°) il passaggio da pieno a nullo occupa 15° e 10°: R4 A1 misura 15–30° con un passo di 15°.
 */
internal const val GlassLightLobeCore: Float = 0.72f

/**
 * Lo spessore della riga chiara, in spessori del filo: la riga è a metà forza a `rimWidth` per questo numero,
 * misurato da dove il filo finisce.
 *
 * Legata al filo e non a `reflexDepth` perché è un secondo filo, chiaro, accanto al primo: sul ritaglio T6 il
 * filo scuro è largo 2–3 px e la riga chiara ≈4. Se fosse una frazione del rientro crescerebbe con l'alone, e
 * il bordo diventerebbe una fascia (il difetto che la sottocategoria F1A-b nomina).
 */
internal const val GlassLightLineWidth: Float = 2.0f

/** Quanto è sfumato il bordo interno della riga chiara, in frazione del suo spessore (mai meno di mezzo pixel). */
internal const val GlassLightLineSoft: Float = 0.3f

/**
 * Di quanto i lobi dell'**alone** sono più stretti di quelli della riga: la loro mezza ampiezza è quella di
 * `reflexWidths` per questo numero. Sul ritaglio T6 la riga arriva a 60° dalla verticale, l'alone si vede fino
 * a 30° ed è sparito a 45°.
 */
internal const val GlassLightHaloSpan: Float = 0.75f

/** Quanto del lobo dell'alone è pieno: vedi [GlassLightLobeCore]. Poco: l'alone è al massimo solo in cima. */
internal const val GlassLightHaloCore: Float = 0.35f

/**
 * L'esponente con cui l'alone rientra verso il corpo: `(1 - x / reflexDepth) ^ n`. Sul ritaglio T6 la discesa
 * è quasi una retta (da +11 a zero in ≈20 px); 1,5 la segue e arriva a zero senza spigolo.
 */
internal const val GlassLightHaloFalloff: Float = 1.5f

/**
 * Sopra quale alfa di `rimDark` il filo «c'è» e il riflesso gli lascia il posto, come guadagno: con 8 il filo
 * conta per intero da un alfa di 0,125 in su.
 *
 * Serve a una cosa sola: che gli stessi uniform diano i due bordi. Con il filo (iOS 27) il riflesso nasce
 * dove il filo finisce. Senza filo (iOS 26, tema scuro) deve arrivare al contorno, o fra il contorno e la luce
 * resterebbe una riga di corpo. Il guadagno rende continuo il passaggio quando l'alfa si anima (il giro
 * chiaro/scuro di F2): il riflesso scivola verso il contorno invece di saltarci.
 */
internal const val GlassLightRimGate: Float = 8.0f

/**
 * Fin dove vale la correzione dello spessore nella zona di fusione (vedi [GlassLightBlock]): sotto questa
 * lunghezza della normale mescolata la distanza non si allunga più.
 *
 * La normale mescolata si annulla sulla linea di mezzo di un ponte, dove i due pezzi tirano in versi opposti,
 * e dividere per lei lì darebbe una distanza infinita. Basso quanto basta perché il filo resti dello stesso
 * spessore anche in fondo alla gola di un collo, dove la normale è lunga 0,2–0,4: con 0,5 lì il filo faceva
 * una goccia (provato sul banco, scena `prova-luce-fusione`).
 */
internal const val GlassLightMinSlope: Float = 0.15f

/**
 * La campana del bagliore: la frazione del raggio dentro cui il bagliore è al massimo. È il `core` di
 * `glassButtonGlowRecipe` (`glass/interaction/GlassInteraction.kt`), cioè il numero di Kyant e di LibrePods:
 * mezzo raggio di piano, poi la discesa.
 */
internal const val GlassLightGlowCore: Float = 0.5f

// ---------------------------------------------------------------------------------------------------------
// I valori di serie degli uniform. Stanno qui, in un punto solo, finché la ricetta e il profilo (F1A.5) non
// li raccolgono; le scene `prova-luce-*` del banco li ricopiano a mano, ed è l'unico altro posto. Le
// lunghezze sono in dp: chi imposta gli uniform le porta in pixel dello spazio dello shader.
// ---------------------------------------------------------------------------------------------------------

/**
 * Lo spessore del filo, in dp (`rimWidth`). **Assoluto**: lo stesso su tasto, capsula, menu e pannello.
 *
 * R5 M4 (alta): 2–3 px su una tab bar alta 197 px, cioè 62 pt: 0,63–0,94 pt, e «la compressione allarga». Sul
 * ritaglio T6 il filo dei fianchi pesa quanto 1,7 px pieni, cioè 0,53 pt. R4 A1 dà la regola «assoluto».
 * Sei decimi di dp: 1,3 px sul tablet (densità 2,125) e 1,8 px a densità 3.
 */
internal const val GlassLightRimWidthDp: Float = 0.6f

/**
 * Quanto è sfumato il bordo interno del filo, 0..1 (`rimFeather`). A occhio sul banco, 2026-11-11: abbastanza
 * perché il filo si sciolga nel corpo invece di chiudersi con un secondo bordo netto, non tanto da farne
 * un'ombra interna.
 */
internal const val GlassLightRimFeather: Float = 0.4f

/**
 * L'alfa del filo scuro sul lato chiaro, dove è più forte, cioè sui fianchi (`rimDark`; il colore è nero).
 * R5 M1, M4 (alta): −45…−80 livelli al pixel; sul tondo su bianco 172–203 (−50…−81). Ritaglio T6: 150–156 sui
 * fianchi di un corpo a 236 (−82…−88). Con 0,38 il banco dà −77 sui fianchi nella scena del T6 e −82 sul
 * tondo su bianco: il tetto del bersaglio. Con 0,42, che ricalca il T6, il tondo su bianco usciva a −91.
 */
internal const val GlassLightRimDarkAlpha: Float = 0.38f

/**
 * Di quanto il filo cede dentro i lobi di luce, 0..1 (`rimLift`). Ritaglio T6 (media): il filo in alto e in
 * basso pesa il 64% di quello dei fianchi. Qui 0,30 e non 0,36 perché il filo dei fianchi è già tenuto sotto
 * quello del T6 ([GlassLightRimDarkAlpha]): in alto e in basso resta quello misurato, −40…−60.
 */
internal const val GlassLightRimLift: Float = 0.30f

/**
 * La mezza ampiezza dei due lobi di serie, in radianti (80°): `reflexWidths`. Ritaglio T6 (media): la riga è
 * piena fino a 60° dalla verticale e quasi nulla a 75°; in alto e in basso uguale. R5 M1, M4: «in alto e in
 * basso».
 */
internal const val GlassLightReflexHalfWidth: Float = 1.396f

/**
 * La forza del riflesso sul lato chiaro (`reflexStrength`). Alta perché in `SrcOver` il bianco aggiunge solo
 * ciò che manca al bianco: su un corpo a 243 il massimo possibile è +12, ed è quanto misura R5 M4 (250–254,
 * «saturo»).
 */
internal const val GlassLightReflexStrengthLight: Float = 0.95f

/** La forza del riflesso sul lato scuro. R5 M3 (bassa): +10…+25 su un corpo a 58–78. */
internal const val GlassLightReflexStrengthDark: Float = 0.11f

/**
 * Per quanti dp l'alone rientra nel corpo (`reflexDepth`). R5 M4 (media): «torna al corpo in ≈0,08 H» su una
 * tab bar da 62 pt, cioè 5 pt, dove torna vuol dire che non si distingue più; sul ritaglio T6 arriva a zero in
 * 6–7 pt. È una lunghezza assoluta come il filo; se la ricetta la fa crescere con la taglia è una sua scelta
 * (su un tasto da 44 dp sono 7 dp per parte, a occhio regge).
 */
internal const val GlassLightReflexDepthDp: Float = 7.0f

/** Quanta riga chiara resta sui fianchi, fuori dai lobi (`reflexFloor`). Ritaglio T6: niente. */
internal const val GlassLightReflexFloor: Float = 0.0f

/** La forza dell'alone rispetto alla riga, 0..1 (`reflexHalo`). Ritaglio T6: parte da poco più di metà (media). */
internal const val GlassLightReflexHalo: Float = 0.65f

/**
 * Il bagliore del tocco di oggi tradotto negli uniform della luce: `glow` al massimo della pressione, e
 * `glowFill`. `glassButtonGlowRecipe` stende un velo di 0,10 su tutta la faccia e ci somma un punto di 0,15
 * che segue il dito: è `0,25 * mix(campana, 1, 0,4)`, alla lettera. Il raggio resta il lato lungo.
 *
 * La forma è quella di oggi; **la resa no**, e non può esserlo: oggi il bagliore è bianco in `Plus`, qui è
 * bianco in `SrcOver`, che aggiunge `alfa * (255 - corpo)`. Su un corpo scuro (63) questi numeri danno +47
 * contro i +64 di oggi; su un corpo chiaro (245) danno +2 dove oggi si arriva al bianco. La forza del
 * bagliore è una scelta di F6: qui c'è il fatto.
 */
internal const val GlassLightGlowPeak: Float = 0.25f

/** Vedi [GlassLightGlowPeak]. */
internal const val GlassLightGlowFill: Float = 0.4f

/**
 * La **luce** del materiale `Liquid` come blocco AGSL: filo sul perimetro, riflessi chiari interni, bagliore
 * del dito.
 *
 * È un blocco e non uno shader perché lo includono due programmi. A riposo è il programma della luce
 * ([GlassLight1ShaderString], [GlassLight8ShaderString]), senza ingressi, disegnato sopra il corpo: un
 * bagliore che segue il dito non deve rifare la sfocatura. In viaggio è il corpo stesso, dietro il suo
 * `lightInBody`: la sagoma cambia a ogni fotogramma, il corpo si riesegue comunque, e tenere la luce fuori
 * vorrebbe dire valutare il campo due volte. Lo stesso testo nei due posti è ciò che rende invisibile il
 * passaggio fra viaggio e riposo.
 *
 * ### Dove si include
 * Dopo il nucleo ([GlassFieldKernel1] o [GlassFieldKernel8]) e prima di `main`. Il blocco **dichiara i propri
 * uniform**: chi lo include non li ridichiara. Non dichiara e non legge `materialize` né `aa`, che sono del
 * programma che include (il corpo li ha fra i suoi; il programma della luce li dichiara da sé).
 *
 * ### uniform (piano 3.4). Le lunghezze sono pixel dello spazio dello shader, i punti sono nel sistema del campo
 * - `float rimWidth`: lo spessore del filo, **assoluto**: non cresce con la lastra. È la distanza dal
 *   contorno a cui il filo è a metà forza;
 * - `layout(color) half4 rimDark`: colore del filo, non premoltiplicato; l'alfa è la sua forza. Alfa zero
 *   sul lato scuro: lì il filo non c'è. Il nome dice il caso di serie, non un obbligo: con un colore chiaro è
 *   il «filo chiaro sottile» di una delle proposte per il tema scuro;
 * - `float rimFeather`: 0..1, quanto è sfumato il bordo interno del filo. Zero è un tratto netto (con un
 *   pixel di antialias); uno è una sfumatura che parte dal contorno e finisce a due spessori. **Aggiunto da
 *   F1A.3**;
 * - `float rimLift`: 0..1, di quanto il filo cede dentro i lobi di luce: zero è un filo uguale su tutto il
 *   perimetro, uno un filo che sparisce dove il riflesso è pieno. **Aggiunto da F1A.3**;
 * - `float2 reflexDir`: vettore unitario, l'asse dei due lobi di riflesso. Con y verso il basso, `(0, -1)` è
 *   il valore di serie: un lobo in alto e, opposto, uno in basso. Lo ruotano il sensore e la variante
 *   «iOS 26»;
 * - `float2 reflexWidths`: la mezza ampiezza dei due lobi, in **radianti**: `x` del lobo verso `reflexDir`,
 *   `y` di quello opposto. È l'angolo fra la normale e l'asse a cui la riga chiara si spegne; è piena entro
 *   [GlassLightLobeCore] di quell'angolo. Oltre π/2 i due lobi si sovrappongono sui fianchi; l'alone usa lobi
 *   più stretti ([GlassLightHaloSpan]);
 * - `float reflexStrength`: 0..1, l'alfa della riga chiara al centro di un lobo, prima dell'alfa di
 *   `lightColor`;
 * - `float reflexDepth`: per quanti pixel l'alone rientra dal filo verso l'interno prima di tornare al corpo;
 * - `float reflexFloor`: 0..1, la frazione di riga chiara che resta fuori dai lobi;
 * - `float reflexHalo`: 0..1, la forza dell'alone rispetto alla riga: zero è la sola riga. **Aggiunto da
 *   F1A.3**;
 * - `float2 glowCenter`: dove sta il dito;
 * - `float glowRadius`: il raggio della campana del bagliore;
 * - `float glow`: 0..1, la forza del bagliore; a zero il bagliore non si calcola;
 * - `float glowFill`: 0..1, quanto il bagliore ha riempito l'elemento (0 = la campana, 1 = tutta la sagoma);
 * - `layout(color) half4 lightColor`: colore di riflessi e bagliore, non premoltiplicato; la sua alfa
 *   moltiplica tutti e due.
 *
 * I tre uniform aggiunti non sono nell'elenco del piano (3.4). Uno uniform dichiarato e non impostato vale
 * zero, e a zero tutti e tre sono neutri: tratto netto, filo uguale ovunque, nessun alone.
 *
 * ### `half4 glassLight(float2 p, float d, float2 normal)`
 * Riceve ciò che il chiamante ha già in mano, perché il campo si valuti una volta sola:
 * - `p`: il punto nel sistema del campo, cioè `glassFieldPoint(coord)`;
 * - `d`: la distanza con segno in `p`, cioè ciò che `glassField` ha restituito (negativa dentro, in pixel);
 * - `normal`: la normale che `glassField` ha scritto, **così com'è**: nella zona di fusione non è unitaria,
 *   e questa funzione la vuole proprio così (vedi sotto).
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
 *
 * ### Com'è fatta, e perché
 * **Tre strati, dal basso**: bagliore e riflesso (luce), poi il filo sopra. Il filo sta sopra la luce perché
 * il bagliore non lo sporchi: sotto il dito il corpo si schiarisce e il filo resta scuro della stessa
 * frazione, quindi il contorno non si assottiglia e non si interrompe mentre il bagliore passa.
 *
 * **Il filo** è una banda che parte dal contorno. Il suo bordo esterno non lo disegna questa funzione: è la
 * copertura di chi chiama. Quello interno è una sfumatura centrata su `rimWidth`. È il rischio scritto nel
 * piano: un filo scuro attorno a un corpo quasi chiaro, su una pagina `#F2F2F7`, si legge come un **contorno
 * disegnato**. La scena `prova-luce-filo` lo mostra un ingrediente alla volta, e ciò che lo toglie non è la
 * sfumatura (che da sola cambia poco) ma tre cose insieme, tutte misurate su Apple: il filo **cede dove batte
 * la luce** (`rimLift`), quindi non è un tratto dello stesso grigio tutto intorno; ha accanto una **riga
 * chiara** che lo fa leggere come uno spigolo che prende luce; e dietro la riga c'è l'**alone**, che dà alla
 * faccia una curvatura.
 *
 * **Lo spessore nella zona di fusione.** La distanza fusa di due pezzi non è una distanza: dentro un ponte
 * cresce più piano di un pixel per pixel, tanto più piano quanto più le normali dei due pezzi divergono, e
 * un filo misurato su di lei si allargherebbe proprio nella gola del collo. Ma la sua pendenza è la lunghezza
 * della normale mescolata che il nucleo già restituisce (è il gradiente esatto della distanza fusa: R3
 * cap. 2), e dividere per quella lunghezza rimette filo e riga dello stesso spessore lungo tutto il ponte
 * ([GlassLightMinSlope]). L'alone no: è largo e morbido, e sulla distanza corretta farebbe una riga chiara
 * sull'asse del ponte. Su un pezzo solo la normale è unitaria e non cambia niente: per questo le due varianti
 * danno la stessa immagine.
 *
 * **La normale non si normalizza.** Dove due pezzi tirano in versi opposti la normale mescolata è corta e la
 * sua direzione non dice più da che parte guarda il bordo: normalizzata, accendeva una riga di alone
 * sull'asse di ogni ponte, il fantasma del confine fra i due pezzi. Corta, spegne i lobi da sola, e in fondo
 * alla gola il riflesso sfuma invece di girare.
 *
 * **Il riflesso** nasce dove il filo finisce: una riga sottile ([GlassLightLineWidth]) e l'alone che rientra
 * per `reflexDepth`. Senza filo nasce dal contorno ([GlassLightRimGate]): è così che `rimDark` a zero, l'asse
 * in diagonale e due lobi più stretti danno i due archi di iOS 26, e l'asse verticale con lobi larghi,
 * `reflexHalo` a zero e `lightColor` scuro dà il filetto pieno sopra e sotto di oggi (`SrcOver` non distingue
 * una luce da un'ombra: decide il colore).
 *
 * **Il bagliore** è quello di oggi: la campana di Kyant attorno al dito ([GlassLightGlowCore]), che
 * `glowFill` allarga fino a riempire la sagoma. Si somma al riflesso come due luci (`a + b - a * b`), mai
 * oltre uno.
 *
 * **Dove la lastra è piatta si esce subito.** Oltre il filo e il riflesso resta solo il bagliore, ed è quasi
 * tutta l'area: la luce a riposo si ridisegna a ogni fotogramma (piano 3.5), e lì non deve pagare i lobi.
 */
@Language("AGSL")
internal const val GlassLightBlock = """
uniform float rimWidth;
layout(color) uniform half4 rimDark;
uniform float rimFeather;
uniform float rimLift;
uniform float2 reflexDir;
uniform float2 reflexWidths;
uniform float reflexStrength;
uniform float reflexDepth;
uniform float reflexFloor;
uniform float reflexHalo;
uniform float2 glowCenter;
uniform float glowRadius;
uniform float glow;
uniform float glowFill;
layout(color) uniform half4 lightColor;

float glassLightLobe(float facing, float halfWidth, float full) {
    float low = cos(halfWidth);
    return smoothstep(low, max(cos(halfWidth * full), low + 0.0001), facing);
}

half4 glassLight(float2 p, float d, float2 normal) {
    float slope = length(normal);
    // Pixel veri dal contorno verso l'interno, per cio' che e' sottile (filo e riga): nella zona di fusione
    // la distanza fusa cresce piu' piano di un pixel per pixel. L'alone, largo e morbido, usa quella fusa.
    float deep = -d;
    float inside = deep / clamp(slope, $GlassLightMinSlope, 1.0);

    // Il bagliore del dito: la campana di oggi, che `glowFill` allarga a tutta la sagoma.
    float touch = 0.0;
    if (glow > 0.0) {
        float radius = max(glowRadius, 0.0001);
        float bell = 1.0 - smoothstep(radius * $GlassLightGlowCore, radius, distance(p, glowCenter));
        touch = clamp(glow * mix(bell, 1.0, clamp(glowFill, 0.0, 1.0)), 0.0, 1.0);
    }

    float width = max(rimWidth, 0.0001);
    float feather = max(width * clamp(rimFeather, 0.0, 1.0), 0.5);
    float wireThere = clamp(float(rimDark.a) * $GlassLightRimGate, 0.0, 1.0);
    float lineWidth = width * $GlassLightLineWidth;
    float lineSoft = max(lineWidth * $GlassLightLineSoft, 0.5);
    float depth = max(reflexDepth, 0.0001);
    // Oltre il filo e il riflesso c'e' solo il bagliore: e' quasi tutta la lastra, ed esce qui.
    if (deep > width + feather + max(depth, lineWidth + lineSoft)) {
        half far = half(touch) * lightColor.a;
        return half4(lightColor.rgb * far, far);
    }

    // Dove guarda il bordo: i due lobi della riga chiara, e quelli piu' stretti dell'alone.
    // La normale NON si normalizza: dove due pezzi tirano in versi opposti e' corta, e li' la sua direzione
    // non dice piu' da che parte guarda il bordo. Corta, spegne i lobi da sola.
    float facing = dot(normal, reflexDir);
    float lineLobe = max(glassLightLobe(facing, reflexWidths.x, $GlassLightLobeCore),
                         glassLightLobe(-facing, reflexWidths.y, $GlassLightLobeCore));
    float haloLobe = max(glassLightLobe(facing, reflexWidths.x * $GlassLightHaloSpan, $GlassLightHaloCore),
                         glassLightLobe(-facing, reflexWidths.y * $GlassLightHaloSpan, $GlassLightHaloCore));

    // Il filo: dal contorno a `rimWidth`, col bordo interno sfumato; cede dove batte la luce.
    float wire = 1.0 - smoothstep(width - feather, width + feather, inside);
    half dark = rimDark.a * half(wire * (1.0 - clamp(rimLift, 0.0, 1.0) * lineLobe));

    // Il riflesso: una riga chiara subito dentro il filo (dal contorno, se il filo non c'e') e un alone
    // che rientra per `reflexDepth`.
    float start = width * wireThere;
    float lineBand = 1.0 - smoothstep(lineWidth - lineSoft, lineWidth + lineSoft, max(inside - start, 0.0));
    float haloBand = pow(1.0 - clamp((deep - start) / depth, 0.0, 1.0), $GlassLightHaloFalloff);
    float reflex = reflexStrength * (1.0 - wire * wireThere)
        * max(mix(reflexFloor, 1.0, lineLobe) * lineBand, reflexHalo * haloLobe * haloBand);
    reflex = clamp(reflex, 0.0, 1.0);

    // Due luci si sommano senza superare uno; il filo sta sopra, cosi' il bagliore non lo sporca.
    half light = half(reflex + touch - reflex * touch) * lightColor.a;
    half under = light * (1.0 - dark);
    return half4(lightColor.rgb * under + rimDark.rgb * dark, under + dark);
}"""

/**
 * La luce a riposo di **una lastra**: nessun ingresso, si disegna con un `drawRect` sopra il corpo e sotto il
 * contenuto, in `SrcOver`. È il programma di quasi tutti i vetri di una schermata.
 *
 * ### uniform (oltre a quelli di [GlassFieldKernel1] e di [GlassLightBlock])
 * - `float materialize`: 0 = niente luce, 1 = piena. È lo stesso uniform del corpo e porta lo stesso valore:
 *   `intensity` (piano 3.3) e la comparsa;
 * - `float aa`: la larghezza in pixel dell'antialias della maschera, la stessa del corpo (1,25): filo e
 *   corpo devono finire sullo stesso pixel, o lungo il contorno resterebbe una riga di corpo senza filo.
 *
 * `offset` per la luce vale zero (o meno il margine, dove la lastra cede): si disegna sul nodo, non su una
 * cattura più grande. `flex` va impostata sempre, identità a riposo (vedi [GlassFieldPointBlock]).
 *
 * Il `main` è scritto due volte, qui e in [GlassLight8ShaderString], invece che in un blocco comune: una
 * stringa con un `main` è per il banco e per il lint delle sorgenti un programma intero, da compilare da
 * solo, e un `main` senza il suo nucleo non compila. Sono dieci righe: chi ne cambia una cambia l'altra.
 */
@Language("AGSL")
internal const val GlassLight1ShaderString = """
$GlassFieldKernel1
$GlassLightBlock

uniform float materialize;
uniform float aa;

half4 main(float2 coord) {
    float2 p = glassFieldPoint(coord);
    float2 normal;
    float2 center;
    float d = glassField(p, normal, center);
    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    return glassLight(p, d, normal) * half(coverage * clamp(materialize, 0.0, 1.0));
}"""

/**
 * La luce a riposo di un **gruppo di lastre fuse** (da 2 a 8): lo stesso testo di [GlassLight1ShaderString]
 * sul nucleo a otto pezzi, con gli stessi uniform. Con un pezzo solo dà la stessa immagine, al pixel.
 *
 * Il filo segue il contorno della sagoma fusa: dentro un ponte è **uno**, dello stesso spessore, e i contorni
 * dei pezzi di partenza non si vedono (è il difetto del bordo a path di oggi, che disegna un contorno per
 * pezzo).
 */
@Language("AGSL")
internal const val GlassLight8ShaderString = """
$GlassFieldKernel8
$GlassLightBlock

uniform float materialize;
uniform float aa;

half4 main(float2 coord) {
    float2 p = glassFieldPoint(coord);
    float2 normal;
    float2 center;
    float d = glassField(p, normal, center);
    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    return glassLight(p, d, normal) * half(coverage * clamp(materialize, 0.0, 1.0));
}"""
