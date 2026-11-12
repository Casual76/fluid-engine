/*
 * Il PROFILO del materiale `Liquid`: i numeri che ne decidono l'aspetto, **tutti qui e solo qui**.
 *
 * Gli shader (`GlassBodyShader.kt`, `GlassLightShader.kt`, `GlassShadow.kt`) dicono *come* si disegna un
 * vetro; qui c'è scritto *quale* vetro. Fino a F1A.4 i valori di serie stavano in coda a ognuno dei tre file
 * (`GlassBodyDefaults`, le costanti `GlassLight…`, `GlassShadowDefaults`): tre posti, e chi voleva cambiare
 * «il bordo» doveva sapere che metà stava nella luce e metà nel corpo. Da F1A.5 sono stati **spostati** qui:
 * nei tre file non resta un numero di gusto (restano le costanti che danno forma alle curve, che non sono
 * manopole), e `GlassShadowDefaults` è un rimando a questo file, tenuto per i suoi test.
 *
 * Com'è fatto:
 *  - sei **pezzi** ([GlassLiquidEdge], [GlassLiquidVeil], [GlassLiquidFrost], [GlassLiquidLens],
 *    [GlassLiquidGrowth], e da F1B.1 [GlassLiquidTravel]: come cambia il materiale mentre una sagoma viaggia) più
 *    l'ombra, che ha già il suo tipo ([GlassShadowSpec]);
 *  - per ogni pezzo un **catalogo** delle varianti che vanno in tavola ad Alessio ([GlassLiquidEdges],
 *    [GlassLiquidVeils], [GlassLiquidFrosts], [GlassLiquidLenses], [GlassLiquidGrowths],
 *    [GlassLiquidShadows]): «iOS 27 / iOS 26 / oggi» e le proposte dove Apple non è misurata. Le varianti sono
 *    numeri, non altri shader;
 *  - [GlassLiquidProfile] mette insieme un pezzo per tipo e per lato del tema; [GlassLiquidProfile.Shipped] è
 *    la combinazione di serie, e [GlassLiquidProfile.Default] quella con cui si disegna (la stessa, salvo le
 *    manopole di una build di debug: [GlassLiquidTuning]). Una tavola di gusto è
 *    `Default.copy(un pezzo = un'altra variante)`.
 *
 * Chi trasforma un profilo negli uniform è `glassRecipe` (`GlassRecipe.kt`), e nessun altro.
 *
 * Fonti: «R5 M1» è la scheda M1 di `docs/liquid-glass/ricerca/ios27-differenze.md`, «R4 A3» la scheda A3 di
 * `ricerca/riferimenti-misure.md`, «T6» la lettura del ritaglio `T6_scheda_prominente_269_0519.jpg` fatta in
 * F1A.3. Dove c'è scritto «a occhio» la data è l'11 novembre 2026 e l'occhio è quello di chi ha tarato sul
 * banco. Le misure del banco citate sono in `artifacts/liquid-glass/bench/f1a-5/misure-contro-bersagli.md` e,
 * per il ritocco dopo le scelte, in `artifacts/liquid-glass/bench/f1a-5c/`.
 *
 * **Le scelte di Alessio (11 novembre 2026)**, sulle tavole di `artifacts/liquid-glass/tavole/f1a/`, sono la
 * combinazione di serie ([GlassLiquidProfile.Shipped]): bordo A (iOS 27), velo A (iOS 27), ombra B (iOS 26,
 * larga), smerigliatura D (iOS 26, 4,5 dp), bordo scuro B (riflesso e filo chiaro), velo scuro A (il più
 * chiaro). Le alternative restano nei cataloghi e nelle manopole ([GlassLiquidTuning]): in particolare la
 * smerigliatura C e M, perché la scelta fra C, M e D è stata difficile e si riguarda sul tablet.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassShadowColor

// ------------------------------------------------------------------------------------------------------------
// I pezzi
// ------------------------------------------------------------------------------------------------------------

/**
 * Il **bordo**: il filo che copre e la luce che somma (gli uniform di [GlassLightBlock]).
 *
 * @param rimWidth lo spessore del filo. **Assoluto**: lo stesso su tasto, capsula, menu e pannello (R4 A1; la
 *   ricetta non lo fa crescere con la taglia).
 * @param rim colore del filo, non premoltiplicato; l'alfa è la sua forza dove è più forte. Trasparente = il
 *   filo non c'è, e il riflesso nasce dal contorno.
 * @param rimFeather 0..1, quanto è sfumato il bordo interno del filo.
 * @param rimLift −1..1: sopra zero il filo cede dentro i lobi di luce, sotto zero fuori.
 * @param reflexDirX con [reflexDirY], l'asse dei due lobi, unitario, y verso il basso: `(0, −1)` = alto e basso.
 * @param reflexHalfWidth la mezza ampiezza, in radianti, del lobo verso l'asse; [reflexOppositeHalfWidth]
 *   quella del lobo opposto.
 * @param reflexStrength quanta luce somma la riga chiara, in frazione di [light] (0,1 = 25,5 livelli).
 * @param reflexDepth per quanto l'alone rientra nel corpo. Assoluto come il filo.
 * @param reflexFloor quanta riga chiara resta fuori dai lobi, 0..1.
 * @param reflexHalo la forza dell'alone rispetto alla riga, 0..1.
 * @param light il colore di riflessi e bagliore.
 */
@Immutable
internal data class GlassLiquidEdge(
  val rimWidth: Dp,
  val rim: Color,
  val rimFeather: Float,
  val rimLift: Float,
  val reflexDirX: Float,
  val reflexDirY: Float,
  val reflexHalfWidth: Float,
  val reflexOppositeHalfWidth: Float,
  val reflexStrength: Float,
  val reflexDepth: Dp,
  val reflexFloor: Float,
  val reflexHalo: Float,
  val light: Color = Color.White,
)

/**
 * Il **velo** e l'ombreggiatura del corpo: il colore proprio del vetro (gli uniform `veilColor`, `veilAdapt`,
 * `bodyShade…` di [GlassBodyUniformsBlock]).
 *
 * @param color il velo; l'alfa è la sua forza.
 * @param adapt quanto il velo si ispessisce dove lo sfondo gli somiglia (`veilAdapt`): 0 = uguale ovunque.
 * @param shade il colore verso cui il corpo vira avvicinandosi al contorno; l'alfa è quanto, sul contorno.
 * @param shadeDepth fin dove arriva l'ombreggiatura (la ricetta la ferma a mezzo lato corto).
 * @param shadeLeanX con [shadeLeanY], verso quale lato pende e di quanto (y verso il basso; lunghezza 0..1).
 */
@Immutable
internal data class GlassLiquidVeil(
  val color: Color,
  val adapt: Float,
  val shade: Color,
  val shadeDepth: Dp,
  val shadeLeanX: Float,
  val shadeLeanY: Float,
)

/**
 * La **smerigliatura**: la catena a monte del corpo (piano 3.6, via A).
 *
 * @param blur il raggio di sfocatura per tasti, capsule e barre; i grandi lo fanno crescere
 *   ([GlassLiquidGrowth.frost]).
 * @param mix la mescola `w` a riposo: quanto del **nitido** resta sopra lo sfocato. Zero = la smerigliatura la
 *   fa il solo raggio.
 * @param saturation la saturazione prima della sfocatura, per un'ottica con la `vibrancy` di serie (1,6).
 */
@Immutable
internal data class GlassLiquidFrost(val blur: Dp, val mix: Float, val saturation: Float)

/**
 * La **lente**, in tre numeri che non dipendono dalla taglia.
 *
 * @param band `thickness` come frazione del lato corto (mai oltre [GlassLiquidLensBandMaxDp]).
 * @param bend `refraction` come multiplo di `thickness`.
 * @param dome la cupola di un pezzo da 44 dp di lato corto; scende a zero a [GlassLiquidDomeFadeEndDp].
 */
@Immutable
internal data class GlassLiquidLens(val band: Float, val bend: Float, val dome: Float)

/**
 * Di quanto crescono smerigliatura e ombra dal tasto al pannello, a guadagno pieno della regola della taglia
 * dell'engine (`glassSizeGain`: zero fino a 64 dp di lato corto, uno da 320 dp). Il filo non cresce mai.
 *
 * @param frost il raggio di sfocatura si moltiplica per `1 + frost * guadagno`.
 * @param shadow vedi [GlassShadowSpec.grown].
 */
@Immutable
internal data class GlassLiquidGrowth(val frost: Float, val shadow: GlassShadowGrowth)

/**
 * Il vetro **annidato**: cosa cambia per una lastra che sta *dentro* un'altra lastra di vetro (un tasto in una
 * notifica, un'azione su un foglio, un'app che mette un `glassSurface` dentro un `glassSurface`) rispetto a una
 * lastra posata sulla pagina. Lo dice l'innesto, dal nodo: vedi `GlassLiquidSurface.nested`.
 *
 * Perché: il vetro annidato campiona il *materiale* del pannello che lo contiene (vetro su vetro, 2.13.0), che
 * ha già il suo velo. Il velo del tasto posato una seconda volta copre metà di quel materiale: sul lato scuro il
 * velo neutro dei piccoli (`#787880` al 50%) schiarisce e ingrigisce un pannello tinto (la notifica verde di
 * S1-ter, difetto 4: una toppa grigia appoggiata sopra, non un tasto dentro), e su un foglio o una card scuri,
 * che hanno il velo profondo dei grandi, fa una toppa più chiara.
 *
 * @param veil quanto resta del velo del profilo (l'alfa, moltiplicata). Uno = come sulla pagina. Non tocca la
 *   pellicola di una tinta dipinta com'è (`Fixed`) né la tinta di Apple (`Colored`): quelle sono colori che
 *   l'app ha chiesto, non il velo del materiale.
 * @param shadow quanto resta dell'ombra (le due alfa; i raggi no). Un tasto dentro un pannello sta un gradino
 *   sopra il pannello, non sopra la pagina.
 */
@Immutable
internal data class GlassLiquidNesting(val veil: Float, val shadow: Float)

/**
 * Il materiale **in viaggio** (F1B.1): cosa cambia mentre una sagoma di Fluid-physics si muove, rispetto alla
 * stessa sagoma ferma. È la «ricetta di viaggio»: il bersaglio B1 di R4 (media) dice che in viaggio il vetro di
 * Apple è «limpido e molto rifrangente», e che smerigliatura e ombra piena arrivano negli ultimi 100–150 ms.
 *
 * Ogni numero è il valore **a moto pieno**: la ricetta lo pesa con quanto la sagoma si sta muovendo (la lente in
 * moto di Fluid-physics, `LiquidDrop.lens`: zero esatto a goccia posata, uno alla velocità di
 * `FluidMotion.LiquidLensFullSpeedDp`), quindi **a viaggio fermo il materiale è esattamente quello a riposo**, e
 * alla partenza e all'arrivo non c'è niente da raccordare. È la stessa guida della lente in moto della 3.0
 * (`liquidMaterial`), che sfocava il 45% in meno e piegava l'80% in più: qui il raggio di sfocatura non si può
 * toccare (decide il margine della cattura, e un margine che cambia rifà la cattura a ogni fotogramma), quindi la
 * limpidezza è la mescola `w` del piano 3.6.
 *
 * I valori di serie sono **prudenti e a occhio** (11 novembre 2026, sul banco): si scelgono sui video dal
 * tablet, con le manopole `travel`, `travelclear` e `travelbend` ([GlassLiquidTuning]).
 *
 * @param clarity quanto nitido entra nella mescola a moto pieno, 0..1: la mescola `w` va da quella di riposo
 *   verso uno di questa frazione. Zero = smerigliato come da fermo. È una dissolvenza e non una sfocatura
 *   intermedia (piano 3.6): oltre 0,4 sul testo si legge l'immagine doppia.
 * @param bend di quanto cresce lo spostamento della lente a moto pieno: `refraction` per `1 + bend`.
 * @param dome di quanto la cupola va verso uno a moto pieno, 0..1: la goccia è una lente intera, non un bordo
 *   smussato.
 * @param magnify l'ingrandimento attorno al centro della sagoma a moto pieno (`magnify` del corpo).
 * @param dispersion la frangia cromatica a moto pieno, per una superficie che da ferma non ne ha.
 * @param neck a che frazione del **tempo** di un viaggio di `morphTo` (fino alla posa della molla) il **collo** fra
 *   due pezzi si rompe, 0,05..0,5 (F1B.9): più grande, il collo dura di più. A riposo due pezzi distinti sono
 *   separati del tutto (scelta di Alessio, 2026-11-11): il collo esiste solo in viaggio. Vedi `physicsNeckAt`.
 */
@Immutable
internal data class GlassLiquidTravel(
  val clarity: Float,
  val bend: Float,
  val dome: Float,
  val magnify: Float,
  val dispersion: Float,
  val neck: Float,
)

/**
 * Il materiale **adattivo** (F2.2, piano 3.9): come il vetro cambia con ciò che la sonda vede sotto di sé. Ogni numero
 * agisce solo quando la sonda ha misurato (`GlassAdaptiveMaterial.measured`): prima, e senza sonda, il vetro è quello
 * del lato del tema, al pixel.
 *
 * Il velo segue lo sfondo in due modi, uno per lato, perché i due lati sbagliano in due direzioni opposte (F2-e):
 *  - sul **chiaro** sopra uno sfondo scuro il velo che si ispessisce *dove lo sfondo gli somiglia* (`veilAdapt`) si
 *    assottiglia proprio dove servirebbe: sul nero il corpo scende a 112, il vetro chiaro di Apple sta a 136–143
 *    (R4 A6, alta) e sopra una foto a luma 51 a 141–146 (R5 M3, media). Qui il velo ha un **pavimento**
 *    ([lightFloor]): l'alfa che serve perché il corpo non scenda sotto quella luma sopra la parte scura dello
 *    sfondo ([GlassAdaptiveMaterial.darkLevel]). Sopra uno sfondo chiaro non serve niente e non cambia niente;
 *  - sullo **scuro** sopra uno sfondo chiaro il velo dei piccoli (un grigio a 120) non può scendere sotto il proprio
 *    grigio, e sopra un grigio medio il corpo arriva a 124 (testo chiaro a 3,7:1 contro il 5,5 del vetro di ieri).
 *    Qui il velo dei piccoli **passa a quello dei grandi** man mano che la parte chiara dello sfondo
 *    ([GlassAdaptiveMaterial.lightLevel]) sale da [darkDeepenFrom] a [darkDeepenTo]. Sulla pagina scura non cambia
 *    niente: la scelta di Alessio (velo scuro A, il più chiaro) è stata fatta lì.
 *
 * I **grandi** ([continuous]) non girano: scuriscono in continuo con la quota di nero che hanno sotto (R4 A6, alta:
 * 61 / 89 / 111 al 74 / 47 / 25% di nero, da 143 a 48 in ≈1,5 s). È la mescola fra il lato chiaro e il lato scuro
 * della ricetta (`GlassAdaptiveMaterial.darkness`), e la quota la stima [glassBlackShare]. I piccoli girano (F2.3).
 *
 * @param lightFloor la luma (0..1, sRGB) sotto cui il corpo chiaro non scende sopra la parte scura dello sfondo; zero
 *   = nessun pavimento.
 * @param darkLevelSpread quante deviazioni standard sotto la media sta «la parte scura» dello sfondo, e sopra la
 *   media «la parte chiara» (vedi [glassAdaptiveLevels]).
 * @param darkDeepenFrom con [darkDeepenTo], la luma della parte chiara dello sfondo fra cui il velo scuro dei piccoli
 *   passa a quello dei grandi. Uguali = mai.
 * @param shadowOnText di quanto si moltiplica l'alfa dell'ombra sopra uno sfondo fitto (testo, foto: la trama della
 *   sonda a uno). Uno = nessun rinforzo.
 * @param shadowHue quanto il colore dell'ombra prende la tonalità del colore medio dello sfondo, 0..1. Su uno sfondo
 *   neutro l'ombra resta nera a qualunque valore: conta solo la saturazione (vedi [glassAdaptiveShadowColor]).
 * @param clearDimFollow quanto lo scurimento della variante limpida segue lo sfondo, 0..1: zero = il 35% sempre (HIG,
 *   R1 M2); uno = il 35% sopra uno sfondo chiaro, niente sopra uno già scuro («su contenuto già scuro non serve»).
 * @param tintHalo quanto l'ombra di un vetro tinto (`Colored`) prende il colore della tinta, 0..1: l'alone del suo
 *   colore di R4 A7 («largo circa un diametro»). Non è adattivo: vale anche senza sonda.
 * @param tintRim quanto il filo di un vetro tinto diventa la tinta schiarita e meno satura di iOS 26 (R4 A7: (224, 103,
 *   116) su un rosso (222, 0, 12)), 0..1. Non è adattivo.
 * @param continuous i ruoli che scuriscono in continuo invece di girare: pannelli e fogli (`Modal`), card (`Content`).
 * @param darkCeiling la luma (0..1, sRGB) sopra cui il corpo **scuro** di un grande (un ruolo di [continuous]) non sale
 *   sopra la parte chiara dello sfondo: lo specchio di [lightFloor] (F2.3). Uno = nessun soffitto.
 *   Barre, capsule e tasti girano (3.9).
 */
@Immutable
internal data class GlassLiquidAdaptive(
  val lightFloor: Float,
  val darkLevelSpread: Float,
  val darkDeepenFrom: Float,
  val darkDeepenTo: Float,
  val shadowOnText: Float,
  val shadowHue: Float,
  val clearDimFollow: Float,
  val tintHalo: Float,
  val tintRim: Float,
  val continuous: Set<GlassRole>,
  val darkCeiling: Float = 1f,
) {
  /** Vero se [role] scurisce in continuo con la quota di nero, invece di girare. */
  fun darkensContinuously(role: GlassRole): Boolean = role in continuous
}

/**
 * Il **cursore dell'utente** (F2.2, R5 M2): fra «quasi trasparente» (0) e «colorato» (1), di serie a metà, dove il
 * vetro è quello del profilo **al bit**. Muove insieme la diffusione (il raggio di sfocatura) e il velo (la sua alfa),
 * e soprattutto la diffusione: in R5 «il velo cambia poco fra gli estremi».
 *
 * @param frostClear il raggio di sfocatura all'estremo trasparente, come multiplo di quello del profilo.
 * @param frostColored lo stesso all'estremo colorato.
 * @param veilClear l'alfa del velo all'estremo trasparente, come multiplo di quella del profilo.
 * @param veilColored lo stesso all'estremo colorato.
 */
@Immutable
internal data class GlassLiquidPreference(
  val frostClear: Float,
  val frostColored: Float,
  val veilClear: Float,
  val veilColored: Float,
)

// ------------------------------------------------------------------------------------------------------------
// I numeri che non sono una scelta fra varianti
// ------------------------------------------------------------------------------------------------------------

/** `aa`: la rampa della maschera, in pixel dello spazio dello shader. È quella di oggi. */
internal const val GlassLiquidAa: Float = 1.25f

/**
 * `grain`: **spenta**. Mezzo livello di grana scioglie gli scalini delle rampe a 8 bit, ma alza il gradiente
 * medio di una superficie liscia a circa 0,5 livelli per pixel, cioè falsa la «nitidezza che resta» di
 * `measure` proprio dove si tara la smerigliatura. Sul banco gli scalini sono di un livello e larghi 10–13
 * px: se sul dispositivo si vedono (voce del backlog di F1A), il rimedio è 0,5.
 */
internal const val GlassLiquidGrain: Float = 0f

/** Il tetto della banda della lente: su un pannello la lente è un bordo, non mezzo pannello. A occhio (F1A.2). */
internal const val GlassLiquidLensBandMaxDp: Float = 40f

/** Fino a questo lato corto la cupola è piena. */
internal const val GlassLiquidDomeFadeStartDp: Float = 44f

/** Da questo lato corto in su la cupola è zero: un pannello non è una goccia. A occhio (F1A.2). */
internal const val GlassLiquidDomeFadeEndDp: Float = 120f

/** `dispersion` dove l'ottica la chiede (oggi i soli tasti): il blu piega il 30% in più. A occhio (F1A.2). */
internal const val GlassLiquidDispersion: Float = 0.3f

/** `tintFollow`: su nero la tinta scende al 58% del suo valore, su bianco resta piena (R4 A7, alta). */
internal const val GlassLiquidTintFollow: Float = 0.42f

/** `dim` della variante limpida: lo scurimento sotto il vetro che tiene leggibile il contenuto (R4: 35%). */
internal const val GlassLiquidClearDim: Float = 0.35f

/**
 * Il bagliore del tocco di oggi negli uniform della luce: `glow` al massimo della pressione, e `glowFill`.
 * `glassButtonGlowRecipe` stende un velo di 0,10 su tutta la faccia e ci somma un punto di 0,15 che segue il
 * dito, bianchi e in `Plus`: è `0,25 * mix(campana, 1, 0,4)`, alla lettera (+64 sotto il dito e +25 lontano,
 * su qualunque corpo; sul chiaro satura al bianco, come oggi). Il raggio resta il lato lungo.
 *
 * **Com'è scritto non è detto che sia com'è.** `GlassGlowShaderString` restituisce `color * intensity` con
 * `color` non premoltiplicato: il banco lo somma così com'è e il punto di oggi rende bianco pieno fino a metà
 * raggio. Se il tablet fa lo stesso, la taratura di F6 parte da lì (voce del backlog del dispositivo).
 */
internal const val GlassLiquidGlowPeak: Float = 0.25f

/** Vedi [GlassLiquidGlowPeak]. */
internal const val GlassLiquidGlowFill: Float = 0.4f

/**
 * Sotto questo lato corto l'ombra di una lastra è quella «dei piccoli»: l'ombra del profilo moltiplicata per
 * [GlassLiquidProfile.smallShadow]. È l'altezza di un tasto di una barra.
 */
internal const val GlassLiquidSmallShadowStartDp: Float = 44f

/** Da questo lato corto in su l'ombra è piena: l'altezza di una capsula flottante. Vedi [GlassLiquidSmallShadowStartDp]. */
internal const val GlassLiquidSmallShadowEndDp: Float = 56f

/**
 * Sotto questo lato corto una lastra è un **piccolissimo** (l'indicatore di sincronizzazione da 26 dp, un
 * pallino): filo, riflesso e ombra sono tarati su un tasto da 44 dp e, lasciati com'erano, su un tondo di 26
 * dp facevano un anello chiaro vistoso dentro il filo (il riflesso rientrava 7 dp su un raggio di 13) e
 * un'ombra larga più dell'oggetto. Sotto questa soglia la ricetta rimpicciolisce **in proporzione al lato**
 * l'ombra (raggi e scostamento) e attenua il riflesso ([GlassLiquidTinyReflexFloor]); il filo no, è assoluto.
 * Da 44 dp in su niente cambia. A occhio sul banco (F1A.5c, `bench/f1a-5c/tav-piccoli2.png`).
 */
internal const val GlassLiquidTinyBelowDp: Float = 44f

/** Il lato corto a cui il riflesso di un piccolissimo è sceso al suo minimo. Vedi [GlassLiquidTinyBelowDp]. */
internal const val GlassLiquidTinyReflexFromDp: Float = 20f

/**
 * Quanto del riflesso resta a [GlassLiquidTinyReflexFromDp] e sotto: sale in linea fino a uno a
 * [GlassLiquidTinyBelowDp]. Con 0,35 il tondo da 26 dp tiene una riga chiara (si legge ancora come vetro) ma
 * non più l'anello bianco; con 0,2 diventava un cerchio col solo filo scuro. A occhio (F1A.5c).
 */
internal const val GlassLiquidTinyReflexFloor: Float = 0.35f

/**
 * Fin dove rientra il riflesso, in frazione del lato corto: 0,16 dà i 7 dp di serie a 44 dp (quindi da lì in
 * su non tocca niente) e 4,2 dp a 26 dp, dove i 7 dp facevano dell'alone mezzo raggio. A occhio (F1A.5c).
 */
internal const val GlassLiquidTinyReflexDepthFraction: Float = 0.16f

/**
 * Il **velo scuro dei grandi**: da questo lato corto in su il velo scuro comincia a passare da quello dei
 * piccoli ([GlassLiquidProfile.darkVeil]) a quello dei grandi ([GlassLiquidProfile.darkLargeVeil]), e ci arriva a
 * [GlassLiquidDarkLargeEndDp], con un passo morbido (smoothstep): nessuno scatto fra un tasto largo e una card
 * piccola.
 *
 * Perché: la scelta del velo scuro «più chiaro» (6-A) è stata fatta guardando tasti e capsule; sul tablet
 * (S1-ter, difetto 2) fogli, card, pannello dei controlli e menu scuri diventavano un grigio medio (corpo a
 * luma 74–75 contro 37–41 del vetro di oggi) e il testo secondario dell'engine (`onSurfaceVariant`,
 * `#AAAAB2`) scendeva da 6,3–6,7:1 a 3,8:1. Le soglie: una capsula (56 dp) e una tab bar (62 dp) restano quasi
 * «piccole» (7% e 20% della strada), una notifica a una riga (72 dp) è a metà, una card da 96 dp in su è
 * «grande». In chiaro non c'è un velo dei grandi: il testo vi tiene 4,6–5,0:1 come oggi.
 */
internal const val GlassLiquidDarkLargeStartDp: Float = 48f

/** Vedi [GlassLiquidDarkLargeStartDp]. */
internal const val GlassLiquidDarkLargeEndDp: Float = 96f

/**
 * Il tetto della **sfocatura chiesta in più**: un'ottica scritta a mano con un `blurScale` oltre quello di
 * serie del suo ruolo fa crescere la smerigliatura del profilo nella stessa proporzione, fino a tre volte.
 *
 * Perché non viene ignorata: il pannello del lettore di Fluidify chiede `blurScale = 7` su un ruolo `Modal`
 * che di serie ne ha 3,5, per smerigliare di più una parete di copertine; con la smerigliatura del profilo
 * e basta usciva *meno* smerigliato del vetro di ieri. Perché un tetto: `GlassOptics` accetta fino a 16
 * (`GlassMaxBlurScale`, una rete per i valori malformati), e 16 / 1,8 farebbe nove volte il raggio di un
 * tasto. Tre volte la D (4,5 dp) è 13,5 dp: più della smerigliatura A, meno della C.
 */
internal const val GlassLiquidFrostRequestMax: Float = 3f

/**
 * Le **soglie** con cui un'ottica di oggi ([dev.antigravity.fluidengine.ui.fluid.GlassOptics]) parla al
 * materiale nuovo: sono il valore più basso che ognuno di quei campi ha in un ruolo di serie.
 *
 * In `Liquid` smerigliatura, lente, bordo e ombra li decide il profilo, non il ruolo: è ciò che fa sembrare
 * tasto, capsula, menu e pannello lo stesso vetro. Ma un'ottica scritta a mano che chiede zero sfocatura, o
 * nessuna ombra, deve ancora ottenerle. Quindi ogni campo è un **attenuatore**: da questa soglia in su il
 * materiale è quello del profilo, sotto ne resta la stessa frazione, fino a zero. Con le ottiche di ogni
 * ruolo di serie (`GlassDefaults.optics`, `buttonOptics`, `panelOptics`, `stackedContentOptics`) tutti gli
 * attenuatori valgono uno, salvo l'ombra dove il ruolo non la vuole (`Bar`, `Content`: `shadowAlpha` zero).
 *
 * `blurScale` del ruolo `Content` (0,8).
 */
internal const val GlassLiquidFullBlurScale: Float = 0.8f

/** `refractionHeight` del pannello compatto (7 dp). Vedi [GlassLiquidFullBlurScale]. */
internal const val GlassLiquidFullRefractionHeightDp: Float = 7f

/** `refractionAmount` del pannello compatto (9 dp). Vedi [GlassLiquidFullBlurScale]. */
internal const val GlassLiquidFullRefractionAmountDp: Float = 9f

/** `highlightAlpha` del pannello (0,45). Vedi [GlassLiquidFullBlurScale]. */
internal const val GlassLiquidFullHighlightAlpha: Float = 0.45f

/** `shadowAlpha` del pannello (0,36). Vedi [GlassLiquidFullBlurScale]. */
internal const val GlassLiquidFullShadowAlpha: Float = 0.36f

/** La `vibrancy` di ogni ruolo di serie: con questa la saturazione è [GlassLiquidFrost.saturation]. */
internal const val GlassLiquidReferenceVibrancy: Float = 1.6f

/**
 * La risoluzione più bassa a cui la ricetta lascia scendere la cattura di una lastra `Liquid`.
 *
 * Oggi la cattura scende fino a 0,4 quando la sfocatura passa i 10 dp (`glassResolutionScale`) e la sagoma la
 * ritaglia il ritaglio hardware, a risoluzione piena. In `Liquid` la sagoma la disegna **anche** il corpo, nella
 * cattura: a 0,4 il suo antialias è largo tre pixel di schermo. Vedi `glassRecipe` e il rapporto di F1A.5.
 */
internal const val GlassLiquidMinCaptureScale: Float = 0.4f

// ------------------------------------------------------------------------------------------------------------
// I cataloghi: le varianti che vanno in tavola
// ------------------------------------------------------------------------------------------------------------

/** Le varianti del **bordo** (tavole «il bordo» e «il tema scuro»). */
internal object GlassLiquidEdges {

  /**
   * **iOS 27, lato chiaro** (di serie): filo scuro su tutto il perimetro, riflesso chiaro interno in alto e in
   * basso.
   *
   *  - spessore 0,6 dp: R5 M4 (alta) misura 2–3 px su una tab bar alta 197 px, cioè 0,63–0,94 pt «e la
   *    compressione allarga»; sul ritaglio T6 il filo dei fianchi pesa quanto 1,7 px pieni, 0,53 pt. Sono
   *    1,3 px sul tablet (densità 2,125) e 1,8 px a densità 3;
   *  - filo nero allo 0,32: R5 M1, M4 (alta) −45…−80 livelli al pixel, e sul fianco della tab bar di M1
   *    −35…−70. F1A.3 lo teneva a 0,38, ricalcato sul ritaglio T6 (−82…−88 sui fianchi): sopra il corpo vero
   *    usciva al tetto o oltre su ogni scena (−79 sul fianco di M1, −88 sul tondo su bianco, −66 in alto
   *    contro −45…−51 di M4) e a taglia vera, sulla pagina `#F2F2F7`, pesava più di quello di Apple. Con 0,32
   *    sta dentro ovunque (vedi `misure-contro-bersagli.md`);
   *  - `rimLift` 0,30: sul T6 il filo in alto e in basso pesa il 64% di quello dei fianchi (media);
   *  - sfumatura 0,4: a occhio, perché il filo si sciolga nel corpo senza diventare un'ombra interna;
   *  - lobi di 80° (1,396 rad): sul T6 la riga è piena fino a 60° dalla verticale e quasi nulla a 75°;
   *  - forza 0,12 (30 livelli): una sola per i due lati, perché la luce somma. Sul corpo chiaro satura a 255
   *    (T6: +13…+19; R5 M4: 250–254); sul vetro chiaro sopra uno scuro +25…+29 (R5 M3, media: +26…+27); sul
   *    vetro scuro +19 (R5 M3, bassa: +10…+25). F1A.3b la teneva a 0,10, misurata sopra una campitura: sopra
   *    il corpo vero, che in alto è ombreggiato, dava +21…+24 e a densità 2,125 +16…+21;
   *  - rientro 7 dp: R5 M4 (media) «torna al corpo in ≈0,08 H», 5 pt su una tab bar; T6: zero in 6–7 pt;
   *  - alone 0,45: sul T6, dove non satura, l'alone parte da +11…+14: qui da +14.
   */
  val Ios27: GlassLiquidEdge = GlassLiquidEdge(
    rimWidth = 0.6.dp,
    rim = Color.Black.copy(alpha = 0.32f),
    rimFeather = 0.4f,
    rimLift = 0.30f,
    reflexDirX = 0f,
    reflexDirY = -1f,
    reflexHalfWidth = 1.396f,
    reflexOppositeHalfWidth = 1.396f,
    reflexStrength = 0.12f,
    reflexDepth = 7.dp,
    reflexFloor = 0f,
    reflexHalo = 0.45f,
  )

  /**
   * **iOS 26, lato chiaro**: niente filo, due archi di luce in diagonale. R4 A1 (alta): centri a 130° e 315°
   * (qui un asse solo, 130°, e l'opposto cade a 310°), larghi 110° e 70°, +21…+53 saturando; fuori dagli archi
   * +2…+13; dentro un alone di +3…+5.
   */
  val Ios26: GlassLiquidEdge = Ios27.copy(
    rim = Color.Transparent,
    rimLift = 0f,
    reflexDirX = -0.64279f,
    reflexDirY = -0.76604f,
    reflexHalfWidth = 0.9599f,
    reflexOppositeHalfWidth = 0.6109f,
    reflexStrength = 0.3f,
    reflexFloor = 0.15f,
    reflexHalo = 0.06f,
  )

  /**
   * **Oggi, lato chiaro**: il filetto scuro di `glassRimRecipe` (nero al 28%), pieno sopra e sotto e spento sui
   * fianchi, largo 0,8 dp (`highlightWidth`). È il filo con `rimLift` a −1; la luce che somma è spenta.
   */
  val Today: GlassLiquidEdge = Ios27.copy(
    rimWidth = 0.8.dp,
    rim = Color.Black.copy(alpha = 0.28f),
    rimFeather = 0f,
    rimLift = -1f,
    reflexHalfWidth = 1.5533f,
    reflexOppositeHalfWidth = 1.5533f,
    reflexStrength = 0f,
    reflexHalo = 0f,
  )

  /**
   * **Tema scuro, solo il riflesso** (la A della tavola 6): la luce di [Ios27] senza filo. R5 M3 (bassa): bordo
   * alto +10…+25 sul corpo, nessun filo scuro visibile. Sul banco +20 sul corpo scuro, e i fianchi della lastra
   * senza bordo: è il «disco grigio piatto» che ha fatto scegliere B.
   */
  val DarkReflex: GlassLiquidEdge = Ios27.copy(rim = Color.Transparent, rimLift = 0f)

  /**
   * **Tema scuro, riflesso e un filo chiaro sottile, come nella tavola 6** (la B che Alessio ha scelto, nella
   * forma in cui l'ha vista): come [DarkReflex] più un filo bianco al 10% su tutto il perimetro. È un filo che
   * **copre** (non una luce che somma): ha lo stesso peso qualunque cosa ci sia sotto, e resta sopra il
   * bagliore. Resta come termine di paragone; di serie c'è [DarkWire].
   */
  val DarkReflexAndWire: GlassLiquidEdge = DarkReflex.copy(rim = Color.White.copy(alpha = 0.10f))

  /**
   * **Tema scuro, la B resa più decisa** (di serie). Alessio ha scelto «riflesso e filo chiaro»; sul banco e
   * sul tablet il 10% della tavola lasciava il vetro scuro poco staccato dalla pagina scura. Il ritaglio di
   * Apple (`M3_tab_bar_scura_292_0632.jpg`, letto in F1A.5c) ha un filo chiaro netto **tutto intorno**: +32…+40
   * in alto, +26…+40 in basso e fino a +83 sul capo sinistro, sopra il varco nero. Qui:
   *  - filo bianco al 34% che **copre**, pieno in alto e in basso e al 75% sui fianchi (`rimLift` −0,25: cede
   *    fuori dai lobi della luce, un poco). Con −0,6 i fianchi restavano a +12, troppo poco accanto ad Apple;
   *    con 0 il filo diventava un contorno uguale tutto intorno;
   *  - riflesso 0,20 e alone 0,55 (il chiaro ne ha 0,12 e 0,45): sul corpo scuro la luce che somma conta di
   *    più, e la riga chiara sotto il filo è ciò che lo fa leggere come uno spigolo e non come un tratto.
   *
   * Sul banco, capsula sulla pagina `#0D0D0F` a densità 3 (`measure`, scena `gusto/serie-scuro`): in alto +52
   * sul corpo (picco 112), in basso +57, sui fianchi +34; col 10% della tavola +24 / +29 / +9, col solo
   * riflesso +20 / +29 / niente. Sopra il bersaglio di R5 M3 (+10…+25, fiducia bassa: una grafica in HD) e
   * dentro la lettura del ritaglio di Apple fatta qui. Il corpo resta quello del velo (64–67): più deciso è il
   * bordo, non la lastra. A occhio, tavola `bench/f1a-5c/tav-scuro3.png` (colonna «g»).
   */
  val DarkWire: GlassLiquidEdge = DarkReflex.copy(
    rim = Color.White.copy(alpha = 0.34f),
    rimLift = -0.25f,
    reflexStrength = 0.20f,
    reflexHalo = 0.55f,
  )

  /**
   * **Tema scuro, com'è oggi**: bianco al 50% in `Plus` (`glassRimRecipe`), pieno sopra e sotto, spento sui
   * fianchi, senza alone.
   */
  val DarkToday: GlassLiquidEdge = DarkReflex.copy(
    rimWidth = 0.8.dp,
    reflexHalfWidth = 1.5533f,
    reflexOppositeHalfWidth = 1.5533f,
    reflexStrength = 0.5f,
    reflexHalo = 0f,
  )
}

/** Le varianti del **velo** (tavole «il velo» e «il tema scuro»). */
internal object GlassLiquidVeils {

  /**
   * **iOS 27, lato chiaro** (di serie): un grigio quasi bianco al 46%, che si ispessisce fino al 78% dove lo
   * sfondo gli somiglia (`adapt` 0,7). Su bianco puro porta il tasto *sotto* il bianco, su una pagina grigio
   * chiaro sopra; sopra un colore saturo o uno scuro lascia passare metà.
   *
   * Con il velo costante di F1A.2 (`#F6F6F8` al 50%, `adapt` zero) il banco misurava +4 su pagina 238, −4/−5 su
   * bianco, 148 su foto a luma 50 e **+5 su pagina 235 contro +8** (R5 M1, alta): un velo costante non dà
   * insieme +8 su 235 e 146 su 51. L'orchestratore ha deciso che `adapt` si poteva accendere in F1A.5 se
   * avvicinava i bersagli senza rompere gli altri. Tarato sul banco l'11 novembre 2026, sulle scene scritte
   * dalla ricetta (tabella in `artifacts/liquid-glass/bench/f1a-5/misure-contro-bersagli.md`):
   *
   * | bersaglio | F1A.2 | adesso |
   * |---|---|---|
   * | corpo su pagina 235: +8 (R5 M1, alta) | +5 | +7 |
   * | corpo su pagina 238: +2…+5 (R5 M4, alta) | +4 | +5 |
   * | corpo su bianco: −4…−11 (R5 M4, alta) | −4/−5 | −7/−8 |
   * | corpo chiaro su foto a luma 51: 141–146 (R5 M3, media) | 148 | 141 |
   * | velo su colore saturo: ≈0,5 (R5 M1, alta) | 0,44–0,50 | 0,39–0,47 |
   * | testo dietro la tab bar, dev. std: 5,4 (R5 M1, media) | 6,7 | 6,1 |
   *
   * Sulla pagina vera dell'engine (`#F2F2F7`, 242) il corpo sta 2 livelli sopra la pagina: il tasto non stacca
   * per il colore del corpo (nemmeno quello di Apple, che su 238 sta a +2…+5), stacca per filo, riflesso e
   * ombra.
   *
   * L'ombreggiatura: nero al 7% su 22 dp (mezzo tasto), quattro volte più forte in alto che in basso. Il tondo
   * su bianco 253 ha 6 livelli fra l'alto e il centro, come quello di Apple (R5 M4, media: 243 in alto, 249 in
   * basso), ma due o tre livelli più scuro nell'insieme (240 e 247): è il prezzo del +7 su 235.
   */
  val Ios27: GlassLiquidVeil = GlassLiquidVeil(
    color = Color(0x75F5F5F7),
    adapt = 0.7f,
    shade = Color(0x12000000),
    shadeDepth = 22.dp,
    shadeLeanX = 0f,
    shadeLeanY = -0.6f,
  )

  /**
   * **iOS 26, lato chiaro**: bianco al 40%. R4 A2 (alta): +15…+27 su fondi 174–210, cioè il 28–48% della
   * strada verso il bianco; su bianco il tasto era indistinguibile dalla pagina. Ombreggiatura appena
   * accennata e uguale su tutto il perimetro (il gradiente diagonale del tondo, R4 A2, non è rimisurato).
   */
  val Ios26: GlassLiquidVeil = GlassLiquidVeil(
    color = Color(0x66FFFFFF),
    adapt = 0f,
    shade = Color(0x0A000000),
    shadeDepth = 22.dp,
    shadeLeanX = 0f,
    shadeLeanY = 0f,
  )

  /**
   * **Tema scuro, più chiaro** (di serie): 67 sulla pagina `#0D0D0F`, 60 sul nero, 71 sul varco nero di
   * `rif27-scuro` (R5 M3, bassa: 58–78). L'ombreggiatura è più forte che sul chiaro, perché un 7% di nero su
   * un corpo a 60 non si vede. A occhio.
   */
  val DarkLighter: GlassLiquidVeil = GlassLiquidVeil(
    color = Color(0x80787880),
    adapt = 0f,
    shade = Color(0x33000000),
    shadeDepth = 22.dp,
    shadeLeanX = 0f,
    shadeLeanY = -0.6f,
  )

  /** **Tema scuro, più profondo**: 50 sulla pagina scura, 45 sul nero. A occhio. */
  val DarkDeeper: GlassLiquidVeil = DarkLighter.copy(color = Color(0x9E48484E))

  /**
   * **Tema scuro, i grandi** (di serie per fogli, card, pannelli, menu: vedi [GlassLiquidDarkLargeStartDp]): lo
   * stesso velo di [DarkLighter] con un grigio più profondo, alla **stessa alfa**. Così la pagina passa quanto
   * sotto un tasto (metà: il foglio resta un vetro, col filo chiaro e il riflesso del bordo scuro) e il corpo
   * scende dove il testo secondario dell'engine si legge.
   *
   * Il vincolo è misurato (S1-ter, difetto 2): il testo `onSurfaceVariant` scuro (`#AAAAB2`) sul corpo deve
   * stare ad almeno 6:1 (99,5° percentile del testo contro la mediana del corpo), come sul vetro di oggi
   * (6,3–6,7:1), e mai sotto 4,5:1. Sul banco, foglio e card a densità 2,125 (`bench/f1a-5c/contrasto.py`):
   *
   * | sotto il foglio | con [DarkLighter] | con questo |
   * |---|---|---|
   * | la pagina `#0D0D0F` | corpo 67, 4,3:1 | corpo 34, **6,9:1** |
   * | `#1C1C20`, che riproduce il tablet di S1-ter (lì 74–75 e 3,8:1) | 75, 3,8:1 | 41, **6,3:1** |
   * | nero | – | 27, 7,5:1 |
   *
   * `#3E3E44` dava 6,5:1 sulla pagina ma 5,9:1 sul fondo del tablet. Il corpo resta sopra la pagina (34 contro
   * 13), col filo chiaro e il riflesso: è un vetro scuro, non una lastra nera. A occhio sul resto (F1A.5c).
   */
  val DarkLarge: GlassLiquidVeil = DarkLighter.copy(color = Color(0x8036363C))
}

/** Le varianti della **smerigliatura** (tavola «la smerigliatura»). */
internal object GlassLiquidFrosts {

  /**
   * **12 dp, solo raggio.** Tarata con `measure` sulla capsula di R4 A5 (`rif-capsula-testo`), dove il vetro
   * di iOS 26 corrisponde a 4,5 dp: con 12 dp resta il 9,8% del contrasto (R5 M1: 6–13%) e il 38–43% della
   * nitidezza di iOS 26 (R5 M1: 35–45%, alta nel rapporto). Sull'arte sintetica di `rif27-tabbar` lascia il
   * 22% (fuori: quella scena ha più basse frequenze dell'arte di Apple).
   *
   * La saturazione: oggi è 1,6 (`vibrancy`); 1,5 perché sotto un velo al 50% il colore resti «quello dello
   * sfondo» senza diventare acido su una foto. A occhio (F1A.2).
   */
  val Radius12: GlassLiquidFrost = GlassLiquidFrost(blur = 12.dp, mix = 0f, saturation = 1.5f)

  /**
   * **24 dp, solo raggio**: sull'arte di `rif27-tabbar` porta il contrasto al 13% e l'intervallo del testo
   * dietro la barra a 16 livelli (R5 M1: 16), ma sul testo di R4 A5 lascia il 10% della nitidezza di iOS 26
   * invece del 35–45%.
   */
  val Radius24: GlassLiquidFrost = GlassLiquidFrost(blur = 24.dp, mix = 0f, saturation = 1.5f)

  /**
   * **Larga e mista**: 36 dp con il 12% di nitido sopra. È ciò che più somiglia al ritaglio di Apple, dove il
   * testo dietro la tab bar è tenue ma *nitido* sopra una diffusione larga (scoperta di F1A.2). Il piano
   * (3.6) vietava `w > 0` a riposo perché è una dissolvenza e non una sfocatura; l'orchestratore ha sospeso la
   * regola: si sceglie sulla tavola.
   *
   * Tarata in F1A.5 sopra il velo che si ispessisce (che da solo toglie contrasto senza sfocare): F1A.2
   * provava 0,22, che con quel velo lascia il 12% della nitidezza (più di iOS 26). Con 0,12 il banco misura,
   * accanto al raggio di 12 dp: contrasto sul testo 6,7% contro 8% (bersaglio 6–13%), deviazione standard del
   * testo dietro la tab bar 5,1 contro 6,1 (Apple: 5,4), colore del fondo che si spande su 0,76 H contro
   * 0,19 H (Apple: 0,7–0,9 H); e nitidezza 7% contro 3,6% (bersaglio 3,5–4,5%): è la sola riga in cui sta
   * fuori, ed è il suo carattere. Costa un margine di cattura più largo: 25 dp attorno a un tasto da 44 dp.
   */
  val WideMixed: GlassLiquidFrost = GlassLiquidFrost(blur = 36.dp, mix = 0.12f, saturation = 1.5f)

  /**
   * **Di mezzo** (la M del confronto in movimento, F1A.5b): 8 dp, solo raggio. Nessuna lettera leggibile
   * sotto le scritte, nessun margine di cattura, metà dei pixel elaborati di D; del carattere di iOS 27 non ha
   * la diffusione larga del colore (0,16 H). Contrasto che resta sotto la capsula sul testo 13,6%
   * (`tavole/f1a/smerigliatura-movimento/README.md`). Non è di serie: resta raggiungibile dalle manopole.
   */
  val Middle: GlassLiquidFrost = GlassLiquidFrost(blur = 8.dp, mix = 0f, saturation = 1.5f)

  /**
   * **iOS 26** (di serie, la D: scelta di Alessio dopo il confronto in movimento C | M | D): 4,5 dp, il raggio
   * del ruolo `Floating` di oggi (26% di contrasto, R4 A5 e R5 M1). Sotto una barra le parole restano macchie
   * che si riconoscono come forme; il colore del fondo resta vicino a dov'è (0,13 H). Cattura a risoluzione
   * 0,73 senza margine. È la più mossa delle tre nel lancio (sfarfallio 7,6 contro 2,2 di C): da riguardare sul
   * tablet con `frost=wide` e `frost=mid`.
   */
  val Ios26: GlassLiquidFrost = GlassLiquidFrost(blur = 4.5.dp, mix = 0f, saturation = 1.5f)
}

/**
 * Le tre proposte per la **lente** (tavola «la lente»; né R4 né R5 la misurano: a occhio, F1A.2). La leggera
 * lascia fermo il 68% centrale di una lente tonda (R4 A2, bassa: ≈60%), la media il 52%; la marcata su una
 * capsula da 56 dp è la lente del ruolo `Floating` di oggi (19 dp di banda, 24 di spostamento). Sotto la
 * smerigliatura di serie le tre si distinguono poco: si vedono in viaggio, nella comparsa e nella variante
 * limpida.
 */
internal object GlassLiquidLenses {
  val Light: GlassLiquidLens = GlassLiquidLens(band = 0.16f, bend = 0.6f, dome = 0.3f)
  val Medium: GlassLiquidLens = GlassLiquidLens(band = 0.24f, bend = 0.9f, dome = 0.5f)
  val Strong: GlassLiquidLens = GlassLiquidLens(band = 0.34f, bend = 1.25f, dome = 0.8f)
}

/**
 * Le varianti dell'**ombra** (tavola «l'ombra»). Sono i numeri che F1A.4 teneva in `GlassShadowDefaults`,
 * che ora rimanda qui.
 */
internal object GlassLiquidShadows {

  /**
   * **iOS 27** (di serie, lato chiaro): corta e leggera.
   *
   * Bersaglio (R5 M1 e M4, alta): sotto una tab bar 2–4% al bordo, nulla a 0,2–0,25 H; ai fianchi a 0,2 H
   * niente. Sotto una barra alta 62 dp su pagina 238 il banco misura 2,9% a 0,03 H, 1,2% a 0,1 H, niente a
   * 0,2 H, massimo 3,8% a ridosso del bordo, estesa 0,17 H. Lo scostamento (4 dp in giù) e la divisione fra
   * contatto e ambiente non sono misurati: a occhio, perché l'ombra stia sotto e non attorno e perché a
   * ridosso del bordo ci sia una riga più scura dell'alone.
   */
  val Ios27: GlassShadowSpec = GlassShadowSpec(
    contactRadius = 1.5.dp,
    ambientRadius = 8.dp,
    offset = DpOffset(0.dp, 4.dp),
    contactAlpha = 0.024f,
    ambientAlpha = 0.038f,
  )

  /**
   * **iOS 26** (di serie sul lato chiaro: la B, scelta di Alessio, «anche se in realtà la differenza si nota
   * poco»): larga, una componente sola. R4 A3 (alta): sotto una capsula 3,7% a 0,20–0,24 H, estesa fino a
   * 0,75 H; R4 ne ricava (bassa) uno scostamento di 0,10–0,15 H e una sfocatura di 0,3–0,4 H: su una capsula da
   * 56 dp sono 6,7 dp e un raggio di 34 dp. Nessun contatto: «mai una linea di contatto netta» (R4 A3).
   *
   * Con un'ombra così larga la parità fra riposo (maschera sfumata) e viaggio (campo nello shader) non regge da
   * sola: F1A.4 misurava sul banco 6 livelli sotto il tasto e 4 sotto capsula e menu (con l'ombra corta 3). La
   * chiude F1B (prova F1B.6), non questo profilo.
   */
  val Ios26: GlassShadowSpec = GlassShadowSpec(
    contactRadius = 0.dp,
    ambientRadius = 34.dp,
    offset = DpOffset(0.dp, 6.7.dp),
    contactAlpha = 0f,
    ambientAlpha = 0.09f,
  )

  /**
   * **Oggi**: l'ombra che `glassSurface` disegna nel modello `Classic`, negli stessi numeri. Una maschera sola
   * di raggio [radius] (`GlassOptics.shadowRadius`), scesa di un sesto del raggio, nel colore del lato del tema
   * ([glassShadowColor]) per il peso del ruolo [alpha] (`GlassOptics.shadowAlpha`).
   */
  fun today(radius: Dp, alpha: Float, darkSide: Boolean): GlassShadowSpec = GlassShadowSpec(
    contactRadius = 0.dp,
    ambientRadius = radius,
    offset = DpOffset(0.dp, radius / 6f),
    contactAlpha = 0f,
    ambientAlpha = alpha.coerceIn(0f, 1f),
    color = glassShadowColor(darkSide),
  )

  /**
   * **Lato scuro, ombra corta**: la geometria di [Ios27] con le due alfa moltiplicate per quattro. Era di serie
   * fino alla scelta dell'ombra B. Né R4 né R5 misurano un'ombra sotto un vetro scuro. Sulla pagina `#0D0D0F`
   * non può vedersi (c'è un livello di luce da togliere), e lì il tasto stacca per corpo e riflesso; conta
   * sopra una foto o un testo chiaro, dove il 6% del lato chiaro sotto un vetro a 60–70 di luma sparisce. A
   * occhio.
   */
  val Dark: GlassShadowSpec = Ios27.copy(contactAlpha = 0.096f, ambientAlpha = 0.152f)

  /**
   * **Lato scuro, ombra B** (di serie): la geometria di [Ios26] con l'alfa raddoppiata (0,18). Per la stessa
   * ragione di [Dark] serve più alfa che sul chiaro; non quattro volte, perché un'ombra larga 34 dp al 36%
   * sopra una foto è una nube scura sotto la lastra, non un'ombra. A occhio (F1A.5c).
   */
  val Ios26Dark: GlassShadowSpec = Ios26.copy(ambientAlpha = 0.18f)
}

/** Le tre proposte per **i grandi** (tavola «i grandi»: menu e pannelli; in iOS 27 non sono misurati). A occhio. */
internal object GlassLiquidGrowths {

  /** Come una barra: né smerigliatura né ombra crescono. Sotto un menu l'ombra si legge appena. */
  val None: GlassLiquidGrowth = GlassLiquidGrowth(frost = 0f, shadow = GlassShadowGrowth(radius = 0f, alpha = 0f))

  /**
   * Un passo in più, **tarato sull'ombra corta** (la B della tavola 7, scelta dall'orchestratore): a guadagno
   * pieno la sfocatura è una volta e mezza, i raggi dell'ombra doppi e le alfa una volta e mezza. «Non meno
   * smerigliati di una barra» (piano), e in iOS 26 lo erano il doppio (R4 A4). Con l'ombra B (34 dp) gli
   * stessi fattori portavano un pannello a 68 dp di raggio: sotto il pannello del campione 13 livelli a 24 dp
   * dal bordo, e ai fianchi l'ombra arrivava a 64 dp. Di serie c'è [StepForWideShadow].
   */
  val Step: GlassLiquidGrowth = GlassLiquidGrowth(frost = 0.5f, shadow = GlassShadowGrowth(radius = 1f, alpha = 0.5f))

  /**
   * Un passo in più, **per l'ombra larga** (di serie): la sfocatura cresce come in [Step] (da 4,5 a 6,75 dp a
   * pannello pieno), l'ombra appena: raggi e scostamento per 1,3, alfa per 1,2. L'ombra B è già larga quanto
   * l'alone di un menu di iOS 26; ciò che deve dire un pannello è «più in alto di una barra», non «più grande».
   *
   * Sul banco, pagina `#F2F2F7`, livelli sotto la pagina (sotto il bordo inferiore / ai fianchi, a 1, 16, 32,
   * 48 dp): capsula 13 / 7, 7 / 3, 2 / 1, 0 / 0; pannello del campione (330 × 380 dp, guadagno pieno) 15 / 12,
   * 10 / 7, – / 3, – / 1 (con [Step] 20 / 15, 16 / 11, – / 7, – / 3; senza crescita 13 / 11, 7 / 5, – / 1,
   * – / 0). A occhio, `bench/f1a-5c/tav-grandi.png` e `ombra.py`.
   */
  val StepForWideShadow: GlassLiquidGrowth =
    GlassLiquidGrowth(frost = 0.5f, shadow = GlassShadowGrowth(radius = 0.3f, alpha = 0.2f))

  /** Due passi in più: sfocatura doppia, raggi dell'ombra tripli, alfa doppie. */
  val TwoSteps: GlassLiquidGrowth = GlassLiquidGrowth(frost = 1f, shadow = GlassShadowGrowth(radius = 2f, alpha = 1f))
}

/**
 * A che frazione del tempo di un viaggio di `morphTo` il collo fra due pezzi si rompe (`GlassLiquidTravel.neck`,
 * F1B.9): **0,38**, cioè ~250 ms dopo la partenza con la molla di serie (~660 ms alla posa), ~410 ms prima della posa.
 * Il collo vive per un tempo suo, non col progresso: la molla porta i pezzi quasi all'arrivo in ~160 ms, e un collo
 * legato al progresso (F1B, 0,32 dell'ultima parte del progresso) durava due o tre fotogrammi a 120 Hz.
 *
 * Sul tablet (`moto/f1b/collo/`), con 0,38 il collo si vede ~125 ms in (a), un tasto che diventa due, e ~100–115 ms in
 * (c), un menu che torna gruppo; con 0,325 era 66–74 ms, sotto i ~75 ms della partenza che ad Alessio piacevano. Si
 * rompe una volta sola, durante l'oltrepasso della molla, prima che i lobi si assestino (l'ordine delle fasi di R4 B2
 * (d); Apple rompe ≈200 ms prima del riposo, fiducia media). L'alternativa più breve mostrata ad Alessio è 0,35. Varianti
 * con la manopola `travelneck` (0,05–0,5). Le guide esterne (la barra fra le pagine) tengono il collo nel progresso di
 * F1B (`PhysicsProgressNeckFade`, 0,32).
 */
internal const val GlassLiquidNeck: Float = 0.38f

/**
 * Le varianti del materiale **in viaggio** (F1B.1; né R4 né R5 danno numeri: B1 dice «limpido e rifrangente», a
 * occhio). Tutte hanno lo stesso collo ([GlassLiquidNeck]): quanto dura è del tempo del viaggio, non di quanto è
 * limpido.
 */
internal object GlassLiquidTravels {

  /** Il viaggio non cambia il materiale: la sagoma si muove col vetro di riposo. È il termine di paragone. */
  val Off: GlassLiquidTravel =
    GlassLiquidTravel(clarity = 0f, bend = 0f, dome = 0f, magnify = 0f, dispersion = 0f, neck = GlassLiquidNeck)

  /**
   * **Prudente** (di serie): un quarto di nitido nella mescola, lente una volta e mezza, la cupola a metà strada,
   * l'ingrandimento della 3.0 (`LensMagnify`, 3,5%), metà della frangia di un tasto. Con la smerigliatura di serie
   * (4,5 dp) un quarto di nitido alza il contrasto sotto il vetro senza che su un testo si legga la copia nitida
   * sopra quella sfocata.
   */
  val Soft: GlassLiquidTravel =
    GlassLiquidTravel(clarity = 0.25f, bend = 0.5f, dome = 0.5f, magnify = 0.035f, dispersion = 0.15f, neck = GlassLiquidNeck)

  /**
   * **Marcato**: metà nitido, lente doppia, cupola piena. È la lettura più letterale di B1 («limpido e molto
   * rifrangente»); sul testo la copia nitida si vede. Da guardare sui video prima di sceglierla.
   */
  val Strong: GlassLiquidTravel =
    GlassLiquidTravel(clarity = 0.5f, bend = 1f, dome = 1f, magnify = 0.035f, dispersion = 0.3f, neck = GlassLiquidNeck)
}

/**
 * Le varianti del **vetro annidato** (tavola `tavole/f1a/10-annidato.png`). Né R4 né R5 misurano un vetro dentro un
 * vetro: tarate sul banco con pannelli veri e un tasto figlio che ne campiona il materiale (scene
 * `gusto/annidato-*`, `artifacts/liquid-glass/bench/f1a-annidato/`), contro il difetto 4 di S1-ter.
 */
internal object GlassLiquidNestings {

  /**
   * **Come sulla pagina**: niente cambia. È il vetro annidato fino a F1A.5c (la toppa grigia), e la manopola
   * `nested=off` per rivederlo sul tablet.
   */
  val Unchanged: GlassLiquidNesting = GlassLiquidNesting(veil = 1f, shadow = 1f)

  /**
   * **Lato chiaro** (di serie): il velo resta pieno, l'ombra a metà.
   *
   * Il velo: sul chiaro il vetro su vetro era già pulito (S1-ter: filo bianco e riga scura netti, nessun doppio
   * filo). Sul banco il secondo velo porta il tasto da 236 (la notifica su verde chiaro) a 241, cioè un poco più
   * bianco e un poco meno verde del pannello, e su un foglio non si vede (243 su 243, il velo che si ispessisce
   * dove lo sfondo gli somiglia fa già il lavoro): un tasto più chiaro del pannello è un tasto che sta sopra.
   * Con ×0,35 sarebbe 237: non serve.
   *
   * L'ombra: l'ombra B piena di un tasto, sotto il tasto e sopra un foglio chiaro, toglie 12 livelli a ridosso
   * del bordo e 6 a 16 dp: una nuvola grigia sul pannello. A metà, 6 e 3: il tasto resta staccato dal foglio
   * senza sporcarlo. A occhio, `bench/f1a-annidato/foglio-ombra.png`.
   */
  val Light: GlassLiquidNesting = GlassLiquidNesting(veil = 1f, shadow = 0.5f)

  /**
   * **Lato scuro** (di serie): il velo a ×0,35, l'ombra a metà.
   *
   * Sul banco, tasto da 132 × 40 dp dentro una notifica (pannello vero sopra il verde di S1-ter, luma 54) e dentro
   * un foglio sulla pagina `#0D0D0F` (luma 34), luma del corpo del tasto: col velo pieno 85 e 76 (la toppa), a
   * ×0,7 76 e 63, a ×0,5 69 e 55, **a ×0,35 64 e 48**, senza velo 54 e 34. A ×0,35 il tasto prende il verde
   * del pannello e resta una decina di livelli sopra di lui, col filo chiaro e il riflesso del bordo scuro: si
   * legge come un tasto dentro, non come un buco (senza velo) né come una toppa. Il vetro di ieri sul tablet
   * stava a 62 sullo stesso pannello. L'ombra: a metà come sul chiaro (sullo scuro pesava già poco: −6 livelli a
   * ridosso del tasto, −3 a metà).
   */
  val Dark: GlassLiquidNesting = GlassLiquidNesting(veil = 0.35f, shadow = 0.5f)
}

// ------------------------------------------------------------------------------------------------------------
// L'adattività (F2.2)
// ------------------------------------------------------------------------------------------------------------

/**
 * Sotto questa luminanza media lo sfondo è «scuro tutto»: la quota di nero stimata ([glassBlackShare]) sale verso uno
 * anche se lo sfondo non ha due parti. Da [GlassAdaptiveUniformDarkFrom] (zero) a [GlassAdaptiveUniformDarkTo] (uno).
 * Sono le soglie del giro dei piccoli (0,45, `GlassAdaptiveDarkBelow`) e la pagina scura dell'engine (luma 0,05) con
 * un margine: un grande sopra la pagina scura è scuro quanto un piccolo che ha girato. A occhio (2026-11-12).
 */
internal const val GlassAdaptiveUniformDarkFrom: Float = 0.45f

/** Vedi [GlassAdaptiveUniformDarkFrom]. */
internal const val GlassAdaptiveUniformDarkTo: Float = 0.10f

/**
 * Il soffitto del corpo scuro dei grandi sopra la parte chiara dello sfondo ([GlassLiquidAdaptive.darkCeiling], F2.3):
 * decisione dell'orchestratore (2026-11-12), un pannello del tema scuro sopra una pagina bianca deve tenere il testo
 * chiaro (#F2F2F5) ad almeno 4,5:1. Il conto dice luma 111 per 4,5:1 sul corpo piatto; il valore tiene il margine per
 * l'ombreggiatura del corpo verso il bordo e per la saturazione, ed è verificato sul banco col corpo reso
 * (`artifacts/liquid-glass/bench/f2-3/contrasto.md`).
 */
internal const val GlassLiquidDarkCeiling: Float = 0.40f

/**
 * Quanto ci mettono i bersagli continui del materiale (velo, ombra, colore dell'ombra) a raggiungere una lettura nuova
 * della sonda: il tempo del corpo nel giro (R4 B6, alta: 300 ms, quasi tutto nei primi 200).
 */
internal const val GlassLiquidAdaptiveMillis: Int = 300

/** Quanto ci mette un grande a scurirsi del tutto (R4 A6, alta: da 143 a 48 su nero in ≈1,5 s). */
internal const val GlassLiquidLargeDarkenMillis: Int = 1500

/**
 * L'ombra che prende la tonalità dello sfondo ([GlassLiquidAdaptive.shadowHue]) va verso il colore medio portato a
 * questo valore sul suo canale più forte: una tinta profonda dello stesso colore, non il colore stesso (che
 * schiarirebbe invece di scurire). A occhio (2026-11-12).
 */
internal const val GlassAdaptiveShadowShade: Float = 0.35f

/**
 * Il tetto dell'ombra rinforzata sul testo ([GlassLiquidAdaptive.shadowOnText]): la somma delle due alfa non va oltre.
 * È l'ombra B (0,09) per ×4,9 sul lato chiaro, cioè il 18% di scurimento di R4 A3 sul testo; senza tetto l'ombra B
 * scura (0,18) arriverebbe a 0,88, una nube nera sotto la lastra, e quella dei grandi a 0,53.
 */
internal const val GlassLiquidShadowOnTextCap: Float = 0.45f

/** Il filo del vetro tinto di iOS 26 ([GlassLiquidAdaptive.tintRim]): la tinta schiarita di tanto verso il bianco. R4 A7. */
internal const val GlassLiquidTintRimLighten: Float = 0.42f

/** E la sua forza (il filo copre): a occhio sul banco (2026-11-12). */
internal const val GlassLiquidTintRimAlpha: Float = 0.7f

/**
 * Le varianti dell'**adattività** (tavole di `artifacts/liquid-glass/tavole/f2/adattivo/`). Né R4 né R5 misurano l'ombra
 * di iOS 27 sul testo, il colore dell'ombra, lo scurimento di `Clear` che segue lo sfondo, né bordo e ombra del tasto
 * tinto di iOS 27: quelle sono scelte di gusto, e le lettere stanno nelle tavole.
 */
internal object GlassLiquidAdaptives {

  /**
   * **Di serie.**
   *  - pavimento del velo chiaro 0,55 (luma 140): il vetro chiaro sul nero passa da 112 a 140 (R4 A6: 136
   *    sull'interfaccia vera, 143 sulla clip sintetica). Sopra la foto a luma 51 di R5 M3 sale da 151 a 156 (bersaglio
   *    141–146: il materiale di serie con questa misura ci stava già sopra, il pavimento aggiunge 5). La parte scura
   *    dello sfondo sta 1,25 deviazioni standard sotto la media: così un menu col 47 o il 74% di nero sotto sta a 90 e
   *    59 sopra il nero (Apple 89 e 61), col 25% a 97 (Apple 111). Vedi `bench/f2-2/misure.md`;
   *  - il velo scuro dei piccoli passa a quello dei grandi fra una parte chiara a luma 0,10 e 0,40: sopra il grigio
   *    medio il testo chiaro torna sopra 6:1 (era 3,7, il vetro di ieri 5,5);
   *  - ombra sul testo ×4,9, come iOS 26 (R4 A3, alta): l'ombra B di serie è quella di iOS 26, e la sua misura sul
   *    testo è questa. Scelta di Alessio (2026-11-12, tavola 1: A);
   *  - colore dell'ombra dalla tonalità dello sfondo a metà; `Clear` col 35% fisso (HIG; Alessio, 2026-11-12,
   *    tavola 4: B);
   *  - il tasto tinto di iOS 26: l'ombra prende il colore della tinta (l'alone di R4 A7) e il filo è la tinta schiarita
   *    e meno satura (R4 A7). Scelta di Alessio (2026-11-12, tavola 3: C). Vale solo per un vetro tinto (`Colored`):
   *    né la pellicola dipinta (`Fixed`, la selezione) né il vetro di prima ([FluidGlassModel.Classic], sotto
   *    Android 13) passano di qui.
   */
  val Shipped: GlassLiquidAdaptive = GlassLiquidAdaptive(
    lightFloor = 0.55f,
    darkLevelSpread = 1.25f,
    darkDeepenFrom = 0.10f,
    darkDeepenTo = 0.40f,
    shadowOnText = 4.9f,
    shadowHue = 0.5f,
    clearDimFollow = 0f,
    tintHalo = 1f,
    tintRim = 1f,
    continuous = setOf(GlassRole.Modal, GlassRole.Content),
    darkCeiling = GlassLiquidDarkCeiling,
  )

  /** L'**ombra sul testo** nelle tre proposte della tavola 1: A come iOS 26, B a metà strada, C nessun rinforzo. */
  val ShadowOnTextIos26: Float = 4.9f

  /** Vedi [ShadowOnTextIos26]: a metà fra uno e ×4,9. */
  val ShadowOnTextHalf: Float = 2.95f

  /** Vedi [ShadowOnTextIos26]. */
  val ShadowOnTextNone: Float = 1f

  /** Tavola 3, A: il tasto tinto senza alone e senza filo chiaro (di serie fino alla scelta del 2026-11-12). */
  val TintPlain: GlassLiquidAdaptive = Shipped.copy(tintHalo = 0f, tintRim = 0f)

  /** Tavola 3, B: l'ombra del vetro tinto prende il suo colore (l'alone di R4 A7), il filo resta quello di serie. */
  val TintHalo: GlassLiquidAdaptive = Shipped.copy(tintHalo = 1f, tintRim = 0f)

  /** Tavola 3, C: alone del colore e filo chiaro della tinta, cioè il tasto tinto di iOS 26 (R4 A7). È [Shipped]. */
  val TintHaloAndRim: GlassLiquidAdaptive = Shipped.copy(tintHalo = 1f, tintRim = 1f)

  /** Tavola 4, B: lo scurimento di `Clear` segue lo sfondo (pieno sul chiaro, niente sul già scuro). */
  val ClearDimFollows: GlassLiquidAdaptive = Shipped.copy(clearDimFollow = 1f)

  /** Nessuna adattività: il vetro del lato del tema anche con la sonda. Il termine di paragone. */
  val Off: GlassLiquidAdaptive = Shipped.copy(
    darkCeiling = 1f,
    lightFloor = 0f,
    darkDeepenFrom = 1f,
    darkDeepenTo = 1f,
    shadowOnText = 1f,
    shadowHue = 0f,
    continuous = emptySet(),
  )
}

/** Le varianti del **cursore dell'utente** (tavola 2). */
internal object GlassLiquidPreferences {

  /**
   * **Di serie.** All'estremo colorato il raggio è 2,67 volte quello del profilo (la D da 4,5 dp diventa la A da 12 dp,
   * che sul testo di R4 A5 lascia il 9,8% del contrasto: R5 M2, «del tutto colorato», 9%) e il velo 1,3 volte (0,46
   * diventa 0,60; R5: 0,57–0,71); all'estremo trasparente il raggio è 0,3 volte (1,35 dp: il testo dietro si legge,
   * piegato dalla lente; R5: 28%) e il velo 0,92 (R5: ≈0,5). Sul banco (`bench/f2-2/cursore.md`) resta, del contrasto
   * sotto la barra, il 24% sul testo e il 56% sulla foto all'estremo trasparente (R5: 28–40%), il 2% e il 34%
   * all'estremo colorato (R5: 9–12%): la misura dipende dalla grana del fondo più che dal vetro. R5 M2 ha fiducia
   * **bassa** (gli stati non hanno lo stesso fondo): numeri da rivedere sul tablet. A occhio sul banco (2026-11-12).
   */
  val Shipped: GlassLiquidPreference =
    GlassLiquidPreference(frostClear = 0.3f, frostColored = 2.67f, veilClear = 0.92f, veilColored = 1.3f)
}

// ------------------------------------------------------------------------------------------------------------
// Il profilo
// ------------------------------------------------------------------------------------------------------------

/**
 * Un vetro: un pezzo per tipo, e per bordo, velo e ombra uno per lato del tema.
 *
 * @param darkVeil il velo scuro dei **piccoli** (tasti, capsule, barre).
 * @param darkLargeVeil il velo scuro dei **grandi** (fogli, card, pannelli, menu): la ricetta passa dall'uno
 *   all'altro con il lato corto, fra [GlassLiquidDarkLargeStartDp] e [GlassLiquidDarkLargeEndDp]. Uguale a
 *   [darkVeil] = il velo scuro non cambia con la taglia. Sul lato chiaro non c'è: il testo vi si legge come oggi.
 * @param smallShadow quanto dell'ombra resta sotto una lastra **piccola** (lato corto fino a
 *   [GlassLiquidSmallShadowStartDp]; sale a uno a [GlassLiquidSmallShadowEndDp]). Uno = il tasto ha l'ombra
 *   della barra. È la riga «il tondo» della tavola dell'ombra.
 * @param lightNesting con [darkNesting], cosa cambia per una lastra **annidata** dentro un'altra lastra di vetro,
 *   per lato del tema ([GlassLiquidNesting]). [GlassLiquidNestings.Unchanged] = niente.
 * @param travel cosa cambia mentre una sagoma viaggia ([GlassLiquidTravel], F1B.1). [GlassLiquidTravels.Off] =
 *   niente: il vetro in viaggio è quello di riposo.
 * @param travelNodeClip se una superficie fisica emula nel corpo l'antialias del ritaglio che una lastra ferma ha dal
 *   suo nodo (`GlassDrawInputs.nodeClip`). Vero di serie. È una **manopola di diagnosi** (F1B.2, `travelclip=off`
 *   di `gallery.tune`): la sessione sul tablet dopo F1B.1 ha visto un bordo doppio di 1–2 px sulla capsula di
 *   «Ordina» a +110 ms, che il banco non riproduce; spenta, dice se viene da qui.
 * @param adaptive come il vetro segue ciò che la sonda vede ([GlassLiquidAdaptive], F2.2). Agisce solo con una
 *   misura: senza, il vetro è quello del lato del tema.
 * @param preference come il cursore dell'utente muove diffusione e velo ([GlassLiquidPreference], F2.2). A metà
 *   corsa non tocca niente.
 */
@Immutable
internal data class GlassLiquidProfile(
  val lightEdge: GlassLiquidEdge,
  val lightVeil: GlassLiquidVeil,
  val lightShadow: GlassShadowSpec,
  val darkEdge: GlassLiquidEdge,
  val darkVeil: GlassLiquidVeil,
  val darkLargeVeil: GlassLiquidVeil,
  val darkShadow: GlassShadowSpec,
  val frost: GlassLiquidFrost,
  val lens: GlassLiquidLens,
  val growth: GlassLiquidGrowth,
  val smallShadow: Float,
  val lightNesting: GlassLiquidNesting,
  val darkNesting: GlassLiquidNesting,
  val travel: GlassLiquidTravel = GlassLiquidTravels.Soft,
  val travelNodeClip: Boolean = true,
  val adaptive: GlassLiquidAdaptive = GlassLiquidAdaptives.Shipped,
  val preference: GlassLiquidPreference = GlassLiquidPreferences.Shipped,
) {

  fun edge(darkSide: Boolean): GlassLiquidEdge = if (darkSide) darkEdge else lightEdge

  /** Il velo dei piccoli di quel lato. Vedi [largeVeil]. */
  fun veil(darkSide: Boolean): GlassLiquidVeil = if (darkSide) darkVeil else lightVeil

  /** Il velo dei grandi di quel lato: [darkLargeVeil] sullo scuro, lo stesso dei piccoli sul chiaro. */
  fun largeVeil(darkSide: Boolean): GlassLiquidVeil = if (darkSide) darkLargeVeil else lightVeil

  fun shadow(darkSide: Boolean): GlassShadowSpec = if (darkSide) darkShadow else lightShadow

  /** Il vetro annidato di quel lato. */
  fun nesting(darkSide: Boolean): GlassLiquidNesting = if (darkSide) darkNesting else lightNesting

  companion object {

    /**
     * Il profilo con cui l'engine disegna: [Shipped], salvo in una build di debug a cui
     * `FluidGlassDiagnostics.tuneLiquidProfile` ha dato delle manopole ([GlassLiquidTuning]).
     *
     * È una lettura e non un valore perché le sessioni sul dispositivo devono fotografare le varianti delle
     * tavole sul pannello vero, senza ricompilare. Il campo che la sostituisce lo scrive solo quella funzione,
     * dopo aver controllato che l'app sia `debuggable` (come il livello di API simulato): in un'app di rilascio
     * è [Shipped] per tutta la vita del processo. Chi la legge è una superficie quando nasce: le lastre già in
     * scena tengono il profilo con cui sono nate.
     */
    val Default: GlassLiquidProfile get() = glassLiquidProfileOverride ?: Shipped

    /**
     * La combinazione **di serie**: le scelte di Alessio sulle tavole (11 novembre 2026), lettera per lettera,
     * e le proposte dove non c'era una scelta da fare.
     *
     * | pezzo | scelta | qui |
     * |---|---|---|
     * | bordo | A, iOS 27 | [GlassLiquidEdges.Ios27] |
     * | velo | A, iOS 27 | [GlassLiquidVeils.Ios27] |
     * | ombra | B, iOS 26 | [GlassLiquidShadows.Ios26]; sullo scuro [GlassLiquidShadows.Ios26Dark] |
     * | smerigliatura | D, iOS 26 (4,5 dp) | [GlassLiquidFrosts.Ios26]; C e M nelle manopole |
     * | bordo scuro | B, riflesso e filo chiaro | [GlassLiquidEdges.DarkWire], la B resa più decisa |
     * | velo scuro | A, il più chiaro | [GlassLiquidVeils.DarkLighter] sui piccoli; sui grandi [GlassLiquidVeils.DarkLarge] (vincolo del testo, S1-ter) |
     * | lente | media (orchestratore) | [GlassLiquidLenses.Medium] |
     * | grandi | un passo (orchestratore) | [GlassLiquidGrowths.StepForWideShadow], ritarato sull'ombra B |
     * | annidato | velo ×0,35 sullo scuro, ombra a metà (orchestratore, S1-ter difetto 4) | [GlassLiquidNestings.Light], [GlassLiquidNestings.Dark] |
     * | in viaggio | prudente (F1B.1, da scegliere sui video) | [GlassLiquidTravels.Soft] |
     *
     * [smallShadow] a uno: sotto un tasto tondo su bianco R5 M4 misura meno dell'1% entro 0,15 D, e qui il
     * tasto ha l'ombra della barra. È fuori dal bersaglio **di proposito**: il difetto da chiudere è che il
     * tasto «non sembra su un livello superiore». La variante «come Apple» è [SmallShadowLikeApple].
     */
    val Shipped: GlassLiquidProfile = GlassLiquidProfile(
      lightEdge = GlassLiquidEdges.Ios27,
      lightVeil = GlassLiquidVeils.Ios27,
      lightShadow = GlassLiquidShadows.Ios26,
      darkEdge = GlassLiquidEdges.DarkWire,
      darkVeil = GlassLiquidVeils.DarkLighter,
      darkLargeVeil = GlassLiquidVeils.DarkLarge,
      darkShadow = GlassLiquidShadows.Ios26Dark,
      frost = GlassLiquidFrosts.Ios26,
      lens = GlassLiquidLenses.Medium,
      growth = GlassLiquidGrowths.StepForWideShadow,
      smallShadow = 1f,
      lightNesting = GlassLiquidNestings.Light,
      darkNesting = GlassLiquidNestings.Dark,
      travel = GlassLiquidTravels.Soft,
    )

    /**
     * La combinazione di serie **prima delle scelte** (F1A.5): ombra corta, solo riflesso sullo scuro, velo
     * scuro uguale su ogni taglia, smerigliatura 12 dp, crescita tarata sull'ombra corta, il vetro annidato come
     * sulla pagina (la regola dell'annidato non c'era). Non la disegna
     * nessuno: è il «prima» delle tavole 8 e 9, e la manopola `profile=before` per rivederla sul tablet. Da
     * 44 dp in su la ricetta la rende come la rendeva allora (la regola dei piccolissimi non tocca quelle
     * taglie, e con 12 dp la cattura stava già a 0,4).
     */
    val BeforeChoices: GlassLiquidProfile = Shipped.copy(
      lightShadow = GlassLiquidShadows.Ios27,
      darkEdge = GlassLiquidEdges.DarkReflex,
      darkLargeVeil = GlassLiquidVeils.DarkLighter,
      darkShadow = GlassLiquidShadows.Dark,
      frost = GlassLiquidFrosts.Radius12,
      growth = GlassLiquidGrowths.Step,
      lightNesting = GlassLiquidNestings.Unchanged,
      darkNesting = GlassLiquidNestings.Unchanged,
    )

    /** [smallShadow] «come Apple»: sotto un tasto resta un quarto dell'ombra della barra (0,8% a 0,03 D). */
    const val SmallShadowLikeApple: Float = 0.25f
  }
}

// ------------------------------------------------------------------------------------------------------------
// Le manopole delle sessioni sul dispositivo (F1A.9)
// ------------------------------------------------------------------------------------------------------------

/** Il profilo che una build di debug ha messo al posto di [GlassLiquidProfile.Shipped], o `null`. */
@Volatile
private var glassLiquidProfileOverride: GlassLiquidProfile? = null

/**
 * Mette [profile] al posto di [GlassLiquidProfile.Shipped] per le superfici che nascono da adesso (`null` lo
 * toglie). Il chiamante ha già deciso che può: vedi `FluidGlassDiagnostics.tuneLiquidProfile`.
 */
internal fun installGlassLiquidProfileOverride(profile: GlassLiquidProfile?) {
  glassLiquidProfileOverride = profile
}

/** Il risultato di [GlassLiquidTuning.apply]: il profilo, e le manopole prese e scartate (nomi normalizzati). */
internal class GlassLiquidTuned(
  val profile: GlassLiquidProfile,
  val accepted: Map<String, String>,
  val rejected: Map<String, String>,
)

/**
 * Le **manopole** del profilo: i nomi con cui la galleria chiede, dal vivo, le varianti delle tavole di gusto e i
 * numeri in mezzo, per fotografarle sul pannello vero (piano «iOS in Android», F1A.9 e sessione S2).
 *
 * Due specie. Le **varianti** scelgono un pezzo dai cataloghi qui sopra, con le lettere delle tavole:
 * `profile` (`shipped`, `before`: tutta la combinazione, quella di serie o quella di prima delle scelte; si
 * applica per prima, e le altre manopole la ritoccano), `edge` (`ios27`, `ios26`, `today`), `veil` (`ios27`,
 * `ios26`), `shadow` (`ios27`, `ios26`: sui due lati, ognuno con la sua alfa), `round` (`bar`, `apple`: la riga «il tondo»), `frost`
 * (`r12`, `r24`, `wide`, `mid`, `ios26`: la A, la B, la C «larga e mista», la M «di mezzo» e la D di serie),
 * `lens` (`light`, `medium`, `strong`), `large` (`none`, `step`, `stepwide`, `two`), `darkedge` (`reflex`,
 * `wire`, `faint`, `today`: `wire` è la B di serie, `faint` la B al 10% della tavola), `darkveil` (`lighter`,
 * `deeper`: il velo scuro dei piccoli), `darklarge` (`deep`, `same`: il velo scuro dei grandi, quello di serie o
 * lo stesso dei piccoli), `nested` (`on`, `off`: il vetro annidato di serie, o come sulla pagina, cioè la toppa
 * grigia di S1-ter), `travel` (`off`, `soft`, `strong`: il materiale in viaggio, F1B.1: come da fermo, il
 * prudente di serie, il marcato), e da F2.2 l'adattività: `adaptive` (`on`, `off`), `textshadow` (`ios26`, `half`, `none`:
 * la tavola 1), `tinted` (`plain`, `halo`, `ios26`: la tavola 3), `cleardim` (`fixed`, `follow`: la tavola 4), coi numeri
 * `lightfloor`, `textshadowx`, `shadowhue` (e da F2.3 `darkceiling`, il soffitto del velo scuro). I **numeri**
 * cambiano un campo del pezzo già scelto, e si applicano dopo tutte le varianti qualunque sia l'ordine in cui arrivano:
 * vedi [numbers]. Una chiave o un valore che non si riconosce
 * non cambia niente e si riporta.
 *
 * Non è una API per le app: si raggiunge solo da `FluidGlassDiagnostics`, e solo in un'app `debuggable`.
 */
internal object GlassLiquidTuning {

  private val variants: Map<String, Map<String, (GlassLiquidProfile) -> GlassLiquidProfile>> = linkedMapOf(
    "profile" to linkedMapOf(
      "shipped" to { _ -> GlassLiquidProfile.Shipped },
      "before" to { _ -> GlassLiquidProfile.BeforeChoices },
    ),
    "edge" to linkedMapOf(
      "ios27" to { p -> p.copy(lightEdge = GlassLiquidEdges.Ios27) },
      "ios26" to { p -> p.copy(lightEdge = GlassLiquidEdges.Ios26) },
      "today" to { p -> p.copy(lightEdge = GlassLiquidEdges.Today) },
    ),
    "veil" to linkedMapOf(
      "ios27" to { p -> p.copy(lightVeil = GlassLiquidVeils.Ios27) },
      "ios26" to { p -> p.copy(lightVeil = GlassLiquidVeils.Ios26) },
    ),
    "shadow" to linkedMapOf(
      "ios27" to { p -> p.copy(lightShadow = GlassLiquidShadows.Ios27, darkShadow = GlassLiquidShadows.Dark) },
      "ios26" to { p -> p.copy(lightShadow = GlassLiquidShadows.Ios26, darkShadow = GlassLiquidShadows.Ios26Dark) },
    ),
    "round" to linkedMapOf(
      "bar" to { p -> p.copy(smallShadow = 1f) },
      "apple" to { p -> p.copy(smallShadow = GlassLiquidProfile.SmallShadowLikeApple) },
    ),
    "frost" to linkedMapOf(
      "r12" to { p -> p.copy(frost = GlassLiquidFrosts.Radius12) },
      "r24" to { p -> p.copy(frost = GlassLiquidFrosts.Radius24) },
      "wide" to { p -> p.copy(frost = GlassLiquidFrosts.WideMixed) },
      "mid" to { p -> p.copy(frost = GlassLiquidFrosts.Middle) },
      "ios26" to { p -> p.copy(frost = GlassLiquidFrosts.Ios26) },
    ),
    "lens" to linkedMapOf(
      "light" to { p -> p.copy(lens = GlassLiquidLenses.Light) },
      "medium" to { p -> p.copy(lens = GlassLiquidLenses.Medium) },
      "strong" to { p -> p.copy(lens = GlassLiquidLenses.Strong) },
    ),
    "large" to linkedMapOf(
      "none" to { p -> p.copy(growth = GlassLiquidGrowths.None) },
      "step" to { p -> p.copy(growth = GlassLiquidGrowths.Step) },
      "stepwide" to { p -> p.copy(growth = GlassLiquidGrowths.StepForWideShadow) },
      "two" to { p -> p.copy(growth = GlassLiquidGrowths.TwoSteps) },
    ),
    "darkedge" to linkedMapOf(
      "reflex" to { p -> p.copy(darkEdge = GlassLiquidEdges.DarkReflex) },
      "wire" to { p -> p.copy(darkEdge = GlassLiquidEdges.DarkWire) },
      "faint" to { p -> p.copy(darkEdge = GlassLiquidEdges.DarkReflexAndWire) },
      "today" to { p -> p.copy(darkEdge = GlassLiquidEdges.DarkToday) },
    ),
    "darkveil" to linkedMapOf(
      "lighter" to { p -> p.copy(darkVeil = GlassLiquidVeils.DarkLighter) },
      "deeper" to { p -> p.copy(darkVeil = GlassLiquidVeils.DarkDeeper) },
    ),
    // Dopo `darkveil`: `same` copia il velo dei piccoli già scelto.
    "darklarge" to linkedMapOf(
      "deep" to { p -> p.copy(darkLargeVeil = GlassLiquidVeils.DarkLarge) },
      "same" to { p -> p.copy(darkLargeVeil = p.darkVeil) },
    ),
    "nested" to linkedMapOf(
      "on" to { p -> p.copy(lightNesting = GlassLiquidNestings.Light, darkNesting = GlassLiquidNestings.Dark) },
      "off" to { p -> p.copy(lightNesting = GlassLiquidNestings.Unchanged, darkNesting = GlassLiquidNestings.Unchanged) },
    ),
    "travel" to linkedMapOf(
      "off" to { p -> p.copy(travel = GlassLiquidTravels.Off) },
      "soft" to { p -> p.copy(travel = GlassLiquidTravels.Soft) },
      "strong" to { p -> p.copy(travel = GlassLiquidTravels.Strong) },
    ),
    // F1B.2, diagnosi: il ritaglio emulato delle superfici fisiche (`travelNodeClip`).
    "travelclip" to linkedMapOf(
      "on" to { p -> p.copy(travelNodeClip = true) },
      "off" to { p -> p.copy(travelNodeClip = false) },
    ),
    // F2.2: l'adattività di serie o spenta (il vetro del lato del tema anche con la sonda), e le lettere delle tavole.
    "adaptive" to linkedMapOf(
      "on" to { p -> p.copy(adaptive = GlassLiquidAdaptives.Shipped) },
      "off" to { p -> p.copy(adaptive = GlassLiquidAdaptives.Off) },
    ),
    "textshadow" to linkedMapOf(
      "ios26" to { p -> p.copy(adaptive = p.adaptive.copy(shadowOnText = GlassLiquidAdaptives.ShadowOnTextIos26)) },
      "half" to { p -> p.copy(adaptive = p.adaptive.copy(shadowOnText = GlassLiquidAdaptives.ShadowOnTextHalf)) },
      "none" to { p -> p.copy(adaptive = p.adaptive.copy(shadowOnText = GlassLiquidAdaptives.ShadowOnTextNone)) },
    ),
    "tinted" to linkedMapOf(
      "plain" to { p -> p.copy(adaptive = p.adaptive.copy(tintHalo = 0f, tintRim = 0f)) },
      "halo" to { p -> p.copy(adaptive = p.adaptive.copy(tintHalo = 1f, tintRim = 0f)) },
      "ios26" to { p -> p.copy(adaptive = p.adaptive.copy(tintHalo = 1f, tintRim = 1f)) },
    ),
    "cleardim" to linkedMapOf(
      "fixed" to { p -> p.copy(adaptive = p.adaptive.copy(clearDimFollow = 0f)) },
      "follow" to { p -> p.copy(adaptive = p.adaptive.copy(clearDimFollow = 1f)) },
    ),
  )

  private class Knob(val min: Float, val max: Float, val apply: (GlassLiquidProfile, Float) -> GlassLiquidProfile)

  /**
   * Le manopole a numero, con l'intervallo che accettano. Lato chiaro: `rimwidth` (dp, anche sul lato scuro: il
   * filo è assoluto), `rimalpha` (la forza del filo scuro), `reflex` (la riga chiara), `halo` (l'alone),
   * `veilalpha`, `adapt`; smerigliatura: `blur` (dp, prima della crescita dei grandi), `mix` (il nitido sopra);
   * `bend` (la lente, multiplo della banda); `shadowscale` (le due alfa dell'ombra insieme, sui due lati: il loro
   * rapporto resta quello registrato); lato scuro: `darkwire` (un filo bianco che copre), `darkreflex`,
   * `darkveilalpha`; il vetro annidato: `nestedveil` (quanto resta del velo sullo **scuro**, il numero che
   * conta), `nestedlightveil` (lo stesso sul chiaro), `nestedshadow` (quanto resta dell'ombra, sui due lati); il
   * materiale in viaggio (F1B.1): `travelclear` (quanto nitido a moto pieno), `travelbend` (di quanto cresce la
   * lente), `travelneck` (a che frazione del tempo del viaggio il collo fra due pezzi si rompe, F1B.9).
   */
  private val numbers: Map<String, Knob> = linkedMapOf(
    "rimwidth" to Knob(0f, 4f) { p, v ->
      p.copy(lightEdge = p.lightEdge.copy(rimWidth = v.dp), darkEdge = p.darkEdge.copy(rimWidth = v.dp))
    },
    "rimalpha" to Knob(0f, 1f) { p, v -> p.copy(lightEdge = p.lightEdge.copy(rim = p.lightEdge.rim.copy(alpha = v))) },
    "reflex" to Knob(0f, 1f) { p, v -> p.copy(lightEdge = p.lightEdge.copy(reflexStrength = v)) },
    "halo" to Knob(0f, 1f) { p, v -> p.copy(lightEdge = p.lightEdge.copy(reflexHalo = v)) },
    "veilalpha" to Knob(0f, 1f) { p, v -> p.copy(lightVeil = p.lightVeil.copy(color = p.lightVeil.color.copy(alpha = v))) },
    "adapt" to Knob(0f, 1f) { p, v -> p.copy(lightVeil = p.lightVeil.copy(adapt = v)) },
    "blur" to Knob(0f, 64f) { p, v -> p.copy(frost = p.frost.copy(blur = v.dp)) },
    "mix" to Knob(0f, 1f) { p, v -> p.copy(frost = p.frost.copy(mix = v)) },
    "bend" to Knob(0f, 3f) { p, v -> p.copy(lens = p.lens.copy(bend = v)) },
    "shadowscale" to Knob(0f, 8f) { p, v ->
      p.copy(
        lightShadow = p.lightShadow.copy(
          contactAlpha = (p.lightShadow.contactAlpha * v).coerceAtMost(1f),
          ambientAlpha = (p.lightShadow.ambientAlpha * v).coerceAtMost(1f),
        ),
        darkShadow = p.darkShadow.copy(
          contactAlpha = (p.darkShadow.contactAlpha * v).coerceAtMost(1f),
          ambientAlpha = (p.darkShadow.ambientAlpha * v).coerceAtMost(1f),
        ),
      )
    },
    "darkwire" to Knob(0f, 1f) { p, v -> p.copy(darkEdge = p.darkEdge.copy(rim = Color.White.copy(alpha = v))) },
    "darkreflex" to Knob(0f, 1f) { p, v -> p.copy(darkEdge = p.darkEdge.copy(reflexStrength = v)) },
    "darkveilalpha" to Knob(0f, 1f) { p, v -> p.copy(darkVeil = p.darkVeil.copy(color = p.darkVeil.color.copy(alpha = v))) },
    "nestedveil" to Knob(0f, 1f) { p, v -> p.copy(darkNesting = p.darkNesting.copy(veil = v)) },
    "nestedlightveil" to Knob(0f, 1f) { p, v -> p.copy(lightNesting = p.lightNesting.copy(veil = v)) },
    "nestedshadow" to Knob(0f, 1f) { p, v ->
      p.copy(lightNesting = p.lightNesting.copy(shadow = v), darkNesting = p.darkNesting.copy(shadow = v))
    },
    "travelclear" to Knob(0f, 1f) { p, v -> p.copy(travel = p.travel.copy(clarity = v)) },
    "travelbend" to Knob(0f, 3f) { p, v -> p.copy(travel = p.travel.copy(bend = v)) },
    "travelneck" to Knob(0.05f, 0.5f) { p, v -> p.copy(travel = p.travel.copy(neck = v)) },
    // F2.2: i numeri dell'adattività.
    "lightfloor" to Knob(0f, 1f) { p, v -> p.copy(adaptive = p.adaptive.copy(lightFloor = v)) },
    "textshadowx" to Knob(1f, 8f) { p, v -> p.copy(adaptive = p.adaptive.copy(shadowOnText = v)) },
    "shadowhue" to Knob(0f, 1f) { p, v -> p.copy(adaptive = p.adaptive.copy(shadowHue = v)) },
    // F2.3: il soffitto del velo scuro dei grandi.
    "darkceiling" to Knob(0f, 1f) { p, v -> p.copy(adaptive = p.adaptive.copy(darkCeiling = v)) },
  )

  /** Ogni manopola con i valori che accetta: le varianti per nome, i numeri come `min..max`. */
  val knobs: Map<String, List<String>> = LinkedHashMap<String, List<String>>().apply {
    for ((name, values) in variants) put(name, values.keys.toList())
    for ((name, knob) in numbers) put(name, listOf("${number(knob.min)}..${number(knob.max)}"))
  }

  /**
   * [requested] applicate a [base]: prima le varianti, poi i numeri, ciascuna specie nell'ordine di [knobs].
   * Nomi e valori senza distinzione di maiuscole; nei numeri vale anche la virgola.
   */
  fun apply(base: GlassLiquidProfile, requested: Map<String, String>): GlassLiquidTuned {
    val clean = LinkedHashMap<String, String>()
    for ((key, value) in requested) clean[key.trim().lowercase()] = value.trim().lowercase()
    val accepted = LinkedHashMap<String, String>()
    val rejected = LinkedHashMap<String, String>()
    for ((key, value) in clean) if (key !in variants && key !in numbers) rejected[key] = value
    var profile = base
    for ((name, values) in variants) {
      val value = clean[name] ?: continue
      val change = values[value]
      if (change == null) rejected[name] = value else {
        profile = change(profile)
        accepted[name] = value
      }
    }
    for ((name, knob) in numbers) {
      val value = clean[name] ?: continue
      val v = value.replace(',', '.').toFloatOrNull()
      if (v == null || !v.isFinite() || v < knob.min || v > knob.max) rejected[name] = value else {
        profile = knob.apply(profile, v)
        accepted[name] = number(v)
      }
    }
    return GlassLiquidTuned(profile, accepted, rejected)
  }

  private fun number(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}
