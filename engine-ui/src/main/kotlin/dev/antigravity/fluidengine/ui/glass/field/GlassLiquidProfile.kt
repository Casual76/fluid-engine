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
 *  - cinque **pezzi** ([GlassLiquidEdge], [GlassLiquidVeil], [GlassLiquidFrost], [GlassLiquidLens],
 *    [GlassLiquidGrowth]) più l'ombra, che ha già il suo tipo ([GlassShadowSpec]);
 *  - per ogni pezzo un **catalogo** delle varianti che vanno in tavola ad Alessio ([GlassLiquidEdges],
 *    [GlassLiquidVeils], [GlassLiquidFrosts], [GlassLiquidLenses], [GlassLiquidGrowths],
 *    [GlassLiquidShadows]): «iOS 27 / iOS 26 / oggi» e le proposte dove Apple non è misurata. Le varianti sono
 *    numeri, non altri shader;
 *  - [GlassLiquidProfile] mette insieme un pezzo per tipo e per lato del tema; [GlassLiquidProfile.Default] è
 *    la combinazione di serie. Una tavola di gusto è `Default.copy(un pezzo = un'altra variante)`.
 *
 * Chi trasforma un profilo negli uniform è `glassRecipe` (`GlassRecipe.kt`), e nessun altro.
 *
 * Fonti: «R5 M1» è la scheda M1 di `docs/liquid-glass/ricerca/ios27-differenze.md`, «R4 A3» la scheda A3 di
 * `ricerca/riferimenti-misure.md`, «T6» la lettura del ritaglio `T6_scheda_prominente_269_0519.jpg` fatta in
 * F1A.3. Dove c'è scritto «a occhio» la data è l'11 novembre 2026 e l'occhio è quello di chi ha tarato sul
 * banco: la scelta di Alessio sulle tavole va scritta al posto di quella. Le misure del banco citate sono in
 * `artifacts/liquid-glass/bench/f1a-5/misure-contro-bersagli.md`.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
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
   * **Tema scuro, solo il riflesso** (di serie): la luce di [Ios27] senza filo. R5 M3 (bassa): bordo alto
   * +10…+25 sul corpo, nessun filo scuro visibile. Sul banco +19 sul corpo scuro.
   */
  val DarkReflex: GlassLiquidEdge = Ios27.copy(rim = Color.Transparent, rimLift = 0f)

  /**
   * **Tema scuro, riflesso e un filo chiaro sottile**: come [DarkReflex] più un filo bianco al 10% su tutto il
   * perimetro. È un filo che **copre** (non una luce che somma): ha lo stesso peso qualunque cosa ci sia
   * sotto, e resta sopra il bagliore. A occhio.
   */
  val DarkReflexAndWire: GlassLiquidEdge = DarkReflex.copy(rim = Color.White.copy(alpha = 0.10f))

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

  /** **iOS 26**: 4,5 dp, il raggio del ruolo `Floating` di oggi (26% di contrasto, R4 A5 e R5 M1). */
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
   * **iOS 26**: larga, una componente sola. R4 A3 (alta): sotto una capsula 3,7% a 0,20–0,24 H, estesa fino a
   * 0,75 H; R4 ne ricava (bassa) uno scostamento di 0,10–0,15 H e una sfocatura di 0,3–0,4 H: su una capsula da
   * 56 dp sono 6,7 dp e un raggio di 34 dp. Nessun contatto: «mai una linea di contatto netta» (R4 A3).
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
   * **Lato scuro** (di serie): la geometria di [Ios27] con le due alfa moltiplicate per quattro. Né R4 né R5
   * misurano un'ombra sotto un vetro scuro. Sulla pagina `#0D0D0F` non può vedersi (c'è un livello di luce da
   * togliere), e lì il tasto stacca per corpo e riflesso; conta sopra una foto o un testo chiaro, dove il 6%
   * del lato chiaro sotto un vetro a 60–70 di luma sparisce. A occhio.
   */
  val Dark: GlassShadowSpec = Ios27.copy(contactAlpha = 0.096f, ambientAlpha = 0.152f)
}

/** Le tre proposte per **i grandi** (tavola «i grandi»: menu e pannelli; in iOS 27 non sono misurati). A occhio. */
internal object GlassLiquidGrowths {

  /** Come una barra: né smerigliatura né ombra crescono. Sotto un menu l'ombra si legge appena. */
  val None: GlassLiquidGrowth = GlassLiquidGrowth(frost = 0f, shadow = GlassShadowGrowth(radius = 0f, alpha = 0f))

  /**
   * Un passo in più (di serie): a guadagno pieno la sfocatura è una volta e mezza (da 12 a 18 dp), i raggi
   * dell'ombra doppi e le alfa una volta e mezza. «Non meno smerigliati di una barra» (piano), e in iOS 26 lo
   * erano il doppio (R4 A4).
   */
  val Step: GlassLiquidGrowth = GlassLiquidGrowth(frost = 0.5f, shadow = GlassShadowGrowth(radius = 1f, alpha = 0.5f))

  /** Due passi in più: sfocatura doppia, raggi dell'ombra tripli, alfa doppie. */
  val TwoSteps: GlassLiquidGrowth = GlassLiquidGrowth(frost = 1f, shadow = GlassShadowGrowth(radius = 2f, alpha = 1f))
}

// ------------------------------------------------------------------------------------------------------------
// Il profilo
// ------------------------------------------------------------------------------------------------------------

/**
 * Un vetro: un pezzo per tipo, e per bordo, velo e ombra uno per lato del tema.
 *
 * @param smallShadow quanto dell'ombra resta sotto una lastra **piccola** (lato corto fino a
 *   [GlassLiquidSmallShadowStartDp]; sale a uno a [GlassLiquidSmallShadowEndDp]). Uno = il tasto ha l'ombra
 *   della barra. È la riga «il tondo» della tavola dell'ombra.
 */
@Immutable
internal data class GlassLiquidProfile(
  val lightEdge: GlassLiquidEdge,
  val lightVeil: GlassLiquidVeil,
  val lightShadow: GlassShadowSpec,
  val darkEdge: GlassLiquidEdge,
  val darkVeil: GlassLiquidVeil,
  val darkShadow: GlassShadowSpec,
  val frost: GlassLiquidFrost,
  val lens: GlassLiquidLens,
  val growth: GlassLiquidGrowth,
  val smallShadow: Float,
) {

  fun edge(darkSide: Boolean): GlassLiquidEdge = if (darkSide) darkEdge else lightEdge

  fun veil(darkSide: Boolean): GlassLiquidVeil = if (darkSide) darkVeil else lightVeil

  fun shadow(darkSide: Boolean): GlassShadowSpec = if (darkSide) darkShadow else lightShadow

  companion object {

    /**
     * La combinazione **di serie**: il materiale di iOS 27 dove è misurato, le proposte di F1A.5 dove non lo è.
     *
     * [smallShadow] a uno: sotto un tasto tondo su bianco R5 M4 misura meno dell'1% entro 0,15 D, e qui il
     * tasto ha l'ombra della barra (3,0% a 0,03 D). È fuori dal bersaglio **di proposito**: il difetto da
     * chiudere è che il tasto «non sembra su un livello superiore». La variante «come Apple» è
     * [SmallShadowLikeApple]; decide la tavola dell'ombra.
     */
    val Default: GlassLiquidProfile = GlassLiquidProfile(
      lightEdge = GlassLiquidEdges.Ios27,
      lightVeil = GlassLiquidVeils.Ios27,
      lightShadow = GlassLiquidShadows.Ios27,
      darkEdge = GlassLiquidEdges.DarkReflex,
      darkVeil = GlassLiquidVeils.DarkLighter,
      darkShadow = GlassLiquidShadows.Dark,
      frost = GlassLiquidFrosts.Radius12,
      lens = GlassLiquidLenses.Medium,
      growth = GlassLiquidGrowths.Step,
      smallShadow = 1f,
    )

    /** [smallShadow] «come Apple»: sotto un tasto resta un quarto dell'ombra della barra (0,8% a 0,03 D). */
    const val SmallShadowLikeApple: Float = 0.25f
  }
}
