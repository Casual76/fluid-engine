/*
 * La LUCE del materiale `Liquid`: filo sul perimetro, riflessi interni, bagliore del dito. Senza ingressi,
 * sopra il corpo e sotto il contenuto (piano 3.3, 3.5), e in **due parti** che si compongono in due modi:
 *
 *  - la **luce che somma** (riga chiara, alone, bagliore del dito): si aggiunge a ciò che ha sotto e satura,
 *    cioè `Plus`. Una luce posata in `SrcOver` aggiunge solo ciò che manca al bianco, e una forza sola non
 *    reggeva fondi diversi: 0,95 per avere +10…+17 sul corpo chiaro, 0,23 per avere +26 sul vetro chiaro
 *    sopra un fondo scuro, e su una foto un bordo molto più acceso di quello di Apple. I numeri di Apple sono
 *    quelli di una luce che si somma: ≈+26 ovunque, che su un corpo chiaro satura a 255. E il bagliore del
 *    tocco di oggi è già in `Plus`: in `SrcOver` sul chiaro spariva (+2 livelli);
 *  - il **filo** (scuro sul lato chiaro, o chiaro in una proposta per il tema scuro): copre, cioè `SrcOver`.
 *
 * L'ordine è sempre lo stesso, a riposo e in viaggio: **prima la luce si somma, poi il filo si posa sopra**.
 * È ciò che tiene il filo pulito sotto il dito: il bagliore schiarisce il corpo, e il filo scurisce il corpo
 * schiarito della stessa frazione di prima. Al contrario il bagliore si sommerebbe anche al filo, e sotto il
 * dito il contorno si assottiglierebbe. Decisione dell'orchestratore del 2026-11-11 (F1A.3b), sul rapporto di
 * F1A.3; sostituisce «un solo modo di comporre» del piano (3.3).
 *
 * Qui ci sono, nell'ordine: i numeri che danno forma alle curve (costanti dentro la sorgente), il blocco che il
 * corpo include in viaggio, e i due programmi della luce a riposo. I valori di serie degli uniform **non** sono
 * qui: da F1A.5 stanno nel profilo ([GlassLiquidEdge], [GlassLiquidEdges] in `GlassLiquidProfile.kt`), con le
 * loro fonti, e li imposta la ricetta (`glassRecipe`).
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
 *   il «filo chiaro sottile» di una delle proposte per il tema scuro, che resta **un filo che copre** e non
 *   una luce che somma (vedi sotto);
 * - `float rimFeather`: 0..1, quanto è sfumato il bordo interno del filo. Zero è un tratto netto (con un
 *   pixel di antialias); uno è una sfumatura che parte dal contorno e finisce a due spessori. **Aggiunto da
 *   F1A.3**;
 * - `float rimLift`: −1..1, dove il filo cede. Zero è un filo uguale su tutto il perimetro. Sopra zero cede
 *   **dentro** i lobi di luce (a uno sparisce dove la riga chiara è piena): è il filo di iOS 27. Sotto zero
 *   cede **fuori** dai lobi (a meno uno resta solo dove batte la luce): è il filetto di oggi, pieno sopra e
 *   sotto e spento sui fianchi. **Aggiunto da F1A.3**, il lato negativo da F1A.3b;
 * - `float2 reflexDir`: vettore unitario, l'asse dei due lobi di riflesso. Con y verso il basso, `(0, -1)` è
 *   il valore di serie: un lobo in alto e, opposto, uno in basso. Lo ruotano il sensore e la variante
 *   «iOS 26»;
 * - `float2 reflexWidths`: la mezza ampiezza dei due lobi, in **radianti**: `x` del lobo verso `reflexDir`,
 *   `y` di quello opposto. È l'angolo fra la normale e l'asse a cui la riga chiara si spegne; è piena entro
 *   [GlassLightLobeCore] di quell'angolo. Oltre π/2 i due lobi si sovrappongono sui fianchi; l'alone usa lobi
 *   più stretti ([GlassLightHaloSpan]);
 * - `float reflexStrength`: 0..1, quanta luce la riga chiara somma al centro di un lobo, in frazione di
 *   `lightColor` (con il bianco, 0,1 sono 25,5 livelli), prima dell'alfa di `lightColor`;
 * - `float reflexDepth`: per quanti pixel l'alone rientra dal filo verso l'interno prima di tornare al corpo;
 * - `float reflexFloor`: 0..1, la frazione di riga chiara che resta fuori dai lobi;
 * - `float reflexHalo`: 0..1, la forza dell'alone rispetto alla riga: zero è la sola riga. **Aggiunto da
 *   F1A.3**;
 * - `float2 glowCenter`: dove sta il dito;
 * - `float glowRadius`: il raggio della campana del bagliore;
 * - `float glow`: 0..1, la forza del bagliore; a zero il bagliore non si calcola;
 * - `float glowFill`: 0..1, quanto il bagliore ha riempito l'elemento (0 = la campana, 1 = tutta la sagoma);
 * - `layout(color) half4 lightColor`: colore di riflessi e bagliore, non premoltiplicato; la sua alfa
 *   moltiplica tutti e due. È una luce: si somma, quindi un colore scuro non scurisce, aggiunge poco.
 *
 * I tre uniform aggiunti non sono nell'elenco del piano (3.4). Uno uniform dichiarato e non impostato vale
 * zero, e a zero tutti e tre sono neutri: tratto netto, filo uguale ovunque, nessun alone.
 *
 * ### `void glassLightParts(float2 p, float d, float2 normal, bool lit, out half4 glowing, out half4 rim)`
 * Le due parti della luce in un punto. Riceve ciò che il chiamante ha già in mano, perché il campo si valuti
 * una volta sola:
 * - `p`: il punto nel sistema del campo, cioè `glassFieldPoint(coord)`;
 * - `d`: la distanza con segno in `p`, cioè ciò che `glassField` ha restituito (negativa dentro, in pixel);
 * - `normal`: la normale che `glassField` ha scritto, **così com'è**: nella zona di fusione non è unitaria,
 *   e questa funzione la vuole proprio così (vedi sotto);
 * - `lit`: `true` se serve anche la luce che somma. Con `false` (il disegno del solo filo, a riposo) riga,
 *   alone e bagliore non si calcolano, `glowing` esce a zero, e si esce subito appena dentro il filo.
 *
 * Scrive due colori **premoltiplicati**, validi (rgb mai sopra l'alfa):
 * - `glowing`: la **luce che somma**, cioè riga chiara, alone e bagliore: `lightColor` per la sua forza. Va
 *   aggiunta a ciò che ha sotto, saturando: è `Plus`;
 * - `rim`: il **filo**: `rimDark` per la sua forza. Va posato sopra, in `SrcOver`.
 *
 * Tutte e due sono **piene** e **non ritagliate**: non contengono né la copertura della sagoma né
 * `materialize`. Li applica chi chiama, **in quest'ordine: prima somma `glowing`, poi posa `rim`**:
 *
 *     // programma della luce, a riposo: due disegni, uno per parte (vedi [GlassLight1ShaderString])
 *     return (lightPart > 0.5 ? rim : glowing) * half(coverage * materialize);
 *     // corpo, in viaggio: tutte e due, i conti comuni una volta sola
 *     colour = min(colour + glowing * half(materialize), half4(1.0));        // Plus
 *     colour = rim * half(materialize) + colour * (1.0 - rim.a * half(materialize));   // SrcOver
 *     // poi la copertura, una volta, su tutto
 *
 * Fuori dalla sagoma (`d > 0`) i valori non contano: chi chiama li moltiplica per una copertura nulla.
 *
 * ### Un disegno solo invece di due: una leva di F7, non scritta qui
 * Fino a F1A.4 il blocco aveva anche `half4 glassLight(p, d, normal)`, la firma lasciata da F1A.1: le due
 * parti in un colore solo, `(glowing.rgb * (1 - rim.a) + rim.rgb, rim.a)`, che posato in `SrcOver` dà
 * `(sotto + luce) * (1 - filo) + filo`. Nessun programma la chiamava, e F1A.5 l'ha tolta: è un colore con rgb
 * maggiore dell'alfa, cioè un premoltiplicato **non valido**, che Skia in software somma come ci si aspetta
 * (provato sul banco: 128 + 64 = 192) ma che nessuno ha provato su un dispositivo (su un pannello a gamma
 * larga la conversione di colore può riportarlo dentro l'alfa, cioè azzerare la luce). Resta come **leva di
 * ottimizzazione da provare sul tablet** (F7): sono quattro righe sopra `glassLightParts`, e se reggono su
 * Mali e Adreno la luce a riposo torna a un `drawRect`. (Non satura prima del filo: dove la luce porta il
 * corpo oltre il bianco, il filo esce più chiaro che con i due disegni.)
 *
 * ### Com'è fatta, e perché
 * **Due parti, e un ordine.** Bagliore e riflesso sono luce: si sommano al corpo. Il filo copre, e sta
 * **sopra** la luce perché il bagliore non lo sporchi: sotto il dito il corpo si schiarisce e il filo resta
 * scuro della stessa frazione, quindi il contorno non si assottiglia e non si interrompe mentre il bagliore
 * passa. Al contrario (prima il filo, poi la somma) il bagliore schiarirebbe anche il filo.
 *
 * **Il filo chiaro del tema scuro è un filo.** La proposta «riflesso e un filo chiaro sottile» per il tema
 * scuro (`rimDark` bianco al 10%) passa dalla parte che copre, non da quella che somma: è un contorno, deve
 * avere lo stesso peso su tutto il perimetro qualunque cosa ci sia sotto e restare sopra il bagliore, come il
 * filo scuro. Una luce che somma, su un corpo scuro che lascia passare una foto chiara, satura e sparisce a
 * tratti. Sul corpo scuro di serie (62) la differenza fra i due modi è di 6 livelli (+19 contro +25).
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
 * in diagonale e due lobi più stretti danno i due archi di iOS 26. Il bordo di oggi sul tema scuro (bianco in
 * `Plus`, pieno sopra e sotto) è la riga con lobi larghi e `reflexHalo` a zero; quello di oggi sul tema chiaro
 * (filetto scuro pieno sopra e sotto) non è più una luce, perché una luce non scurisce: è il filo con
 * `rimLift` a meno uno.
 *
 * **Il bagliore** è quello di oggi: la campana di Kyant attorno al dito ([GlassLightGlowCore]), che
 * `glowFill` allarga fino a riempire la sagoma. Riflesso e bagliore sono due luci e si sommano, come oggi
 * velo e punto.
 *
 * **Dove la lastra è piatta si esce subito.** Oltre il filo e il riflesso resta solo il bagliore, ed è quasi
 * tutta l'area: la luce a riposo si ridisegna a ogni fotogramma (piano 3.5), ora due volte, e lì non deve
 * pagare i lobi. Il disegno del filo esce ancora prima, appena dentro il filo.
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

void glassLightParts(float2 p, float d, float2 normal, bool lit, out half4 glowing, out half4 rim) {
    glowing = half4(0.0);
    rim = half4(0.0);
    float deep = -d;

    // Il bagliore del dito: la campana di oggi, che `glowFill` allarga a tutta la sagoma.
    float touch = 0.0;
    if (lit && glow > 0.0) {
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
    // Oltre il filo (e oltre il riflesso, se la luce e' chiesta) resta solo il bagliore: e' quasi tutta la
    // lastra, ed esce qui.
    float reach = width + feather;
    if (lit) reach += max(depth, lineWidth + lineSoft);
    if (deep > reach) {
        half far = half(touch) * lightColor.a;
        glowing = half4(lightColor.rgb * far, far);
        return;
    }

    // Pixel veri dal contorno verso l'interno, per cio' che e' sottile (filo e riga): nella zona di fusione
    // la distanza fusa cresce piu' piano di un pixel per pixel. L'alone, largo e morbido, usa quella fusa.
    float inside = deep / clamp(length(normal), $GlassLightMinSlope, 1.0);

    // Dove guarda il bordo. La normale NON si normalizza: dove due pezzi tirano in versi opposti e' corta,
    // e li' la sua direzione non dice piu' da che parte guarda il bordo. Corta, spegne i lobi da sola.
    float facing = dot(normal, reflexDir);
    float lineLobe = max(glassLightLobe(facing, reflexWidths.x, $GlassLightLobeCore),
                         glassLightLobe(-facing, reflexWidths.y, $GlassLightLobeCore));

    // Il filo: dal contorno a `rimWidth`, col bordo interno sfumato. Con `rimLift` sopra zero cede dove
    // batte la luce; sotto zero cede dove non batte (a -1 resta solo dentro i lobi).
    float wire = 1.0 - smoothstep(width - feather, width + feather, inside);
    float lift = clamp(rimLift, -1.0, 1.0);
    float kept = lift >= 0.0 ? 1.0 - lift * lineLobe : 1.0 + lift * (1.0 - lineLobe);
    half dark = rimDark.a * half(wire * kept);
    rim = half4(rimDark.rgb * dark, dark);
    if (!lit) return;

    // Il riflesso: una riga chiara subito dentro il filo (dal contorno, se il filo non c'e') e un alone,
    // su lobi piu' stretti, che rientra per `reflexDepth`.
    float haloLobe = max(glassLightLobe(facing, reflexWidths.x * $GlassLightHaloSpan, $GlassLightHaloCore),
                         glassLightLobe(-facing, reflexWidths.y * $GlassLightHaloSpan, $GlassLightHaloCore));
    float start = width * wireThere;
    float lineBand = 1.0 - smoothstep(lineWidth - lineSoft, lineWidth + lineSoft, max(inside - start, 0.0));
    float haloBand = pow(1.0 - clamp((deep - start) / depth, 0.0, 1.0), $GlassLightHaloFalloff);
    float reflex = reflexStrength * (1.0 - wire * wireThere)
        * max(mix(reflexFloor, 1.0, lineLobe) * lineBand, reflexHalo * haloLobe * haloBand);

    // Riflesso e bagliore sono due luci: si sommano.
    half light = half(clamp(reflex + touch, 0.0, 1.0)) * lightColor.a;
    glowing = half4(lightColor.rgb * light, light);
}
"""

/**
 * La luce a riposo di **una lastra**: nessun ingresso, sopra il corpo e sotto il contenuto. È il programma di
 * quasi tutti i vetri di una schermata.
 *
 * ### Due disegni, in quest'ordine
 * Lo stesso programma, lo stesso rettangolo, gli stessi uniform salvo uno:
 *
 *     1.  lightPart = 0   BlendMode.Plus      la luce che somma: riga chiara, alone, bagliore del dito
 *     2.  lightPart = 1   BlendMode.SrcOver   il filo
 *
 * Tutti e due escono come colori premoltiplicati validi. L'ordine è quello del corpo in viaggio, ed è ciò
 * che tiene il filo pulito sotto il bagliore (vedi [GlassLightBlock]). Ognuno dei due si può saltare quando
 * non ha niente da disegnare, e a riposo è il caso comune: il secondo quando l'alfa di `rimDark` è zero (il
 * lato scuro di serie); il primo quando `reflexStrength` e `glow` sono zero, o l'alfa di `lightColor`.
 * Chi li ridisegna per il bagliore (che cambia a ogni fotogramma di una pressione) ridisegna tutti e due: il
 * filo sta sopra, e un disegno in `Plus` rifatto da solo sotto un filo già posato non esiste.
 *
 * ### uniform (oltre a quelli di [GlassFieldKernel1] e di [GlassLightBlock])
 * - `float lightPart`: quale parte restituire. Sotto 0,5 la luce che somma, sopra il filo. Non c'è un valore
 *   per «tutte e due in un disegno»: sarebbe un premoltiplicato non valido (vedi [GlassLightBlock]);
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
 * solo, e un `main` senza il suo nucleo non compila. Sono poche righe: chi ne cambia una cambia l'altra.
 */
@Language("AGSL")
internal const val GlassLight1ShaderString = """
$GlassFieldKernel1
$GlassLightBlock

uniform float lightPart;
uniform float materialize;
uniform float aa;

half4 main(float2 coord) {
    float2 p = glassFieldPoint(coord);
    float2 normal;
    float2 center;
    float d = glassField(p, normal, center);
    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    bool wire = lightPart > 0.5;
    half4 glowing;
    half4 rim;
    glassLightParts(p, d, normal, !wire, glowing, rim);
    return (wire ? rim : glowing) * half(coverage * clamp(materialize, 0.0, 1.0));
}"""

/**
 * La luce a riposo di un **gruppo di lastre fuse** (da 2 a 8): lo stesso testo di [GlassLight1ShaderString]
 * sul nucleo a otto pezzi, con gli stessi uniform e gli stessi due disegni. Con un pezzo solo dà la stessa
 * immagine, al pixel.
 *
 * Il filo segue il contorno della sagoma fusa: dentro un ponte è **uno**, dello stesso spessore, e i contorni
 * dei pezzi di partenza non si vedono (è il difetto del bordo a path di oggi, che disegna un contorno per
 * pezzo).
 */
@Language("AGSL")
internal const val GlassLight8ShaderString = """
$GlassFieldKernel8
$GlassLightBlock

uniform float lightPart;
uniform float materialize;
uniform float aa;

half4 main(float2 coord) {
    float2 p = glassFieldPoint(coord);
    float2 normal;
    float2 center;
    float d = glassField(p, normal, center);
    float coverage = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) return half4(0.0);
    bool wire = lightPart > 0.5;
    half4 glowing;
    half4 rim;
    glassLightParts(p, d, normal, !wire, glowing, rim);
    return (wire ? rim : glowing) * half(coverage * clamp(materialize, 0.0, 1.0));
}"""
