/*
 * L'ombra del materiale `Liquid`: una ricetta, due modi di disegnarla.
 *
 *  - **a riposo** ([GlassShadowLayer], dentro [GlassShadowNode]): due maschere sfumate dal contorno del campo
 *    (contatto e ambiente), scavate sotto la sagoma, registrate una volta in un layer solo;
 *  - **in viaggio** ([GlassTravelShadowBlock]): la stessa ombra calcolata dal campo dentro lo shader del corpo,
 *    che in viaggio si riesegue comunque a ogni fotogramma.
 *
 * I due modi partono dagli stessi numeri ([GlassShadowSpec]) e dalla stessa legge di caduta
 * ([GlassShadowProfile]): ciò che li separa è misurato, e sta scritto in [GlassTravelShadowBlock].
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightLayerMaxPx
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.layerResolutionScale
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.blur
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.intellij.lang.annotations.Language

// ------------------------------------------------------------------------------------------------------------
// La legge di caduta, comune a riposo e viaggio
// ------------------------------------------------------------------------------------------------------------

/**
 * Da raggio di sfocatura a deviazione standard: `sigma = 0,57735 * raggio + 0,5`.
 *
 * Non è un numero di gusto: è il conto con cui la piattaforma traduce il raggio di un `BlurMaskFilter`
 * (`Blur::convertRadiusToSigma` di HWUI, lo stesso di Skia). La maschera a riposo lo subisce; lo shader in
 * viaggio lo ripete, perché le due ombre partano dallo stesso raggio e arrivino alla stessa larghezza.
 */
internal const val GlassShadowSigmaPerRadius: Float = 0.57735f

/** Il mezzo pixel che la piattaforma aggiunge a ogni sigma. Vedi [GlassShadowSigmaPerRadius]. */
internal const val GlassShadowSigmaFloor: Float = 0.5f

/**
 * Fin dove si disegna un'ombra oltre il proprio contorno, in deviazioni standard.
 *
 * A tre sigma una maschera gaussiana vale lo 0,13% del pieno: con un'ombra al 10% è un quarantesimo di
 * livello, e tagliarla lì (il riquadro del layer a riposo, l'uscita anticipata dello shader in viaggio) non si
 * vede. È anche dove la piattaforma taglia la maschera di un `BlurMaskFilter`.
 *
 * Scritto `3.0f` e non `3f` di proposito: entra nella sorgente AGSL come testo, e il lettore del banco
 * ricopia le cifre come sono scritte qui. Con `3f` il banco compilerebbe `3 * sigma` e l'engine
 * `3.0 * sigma`: due sorgenti diverse, e la sonda del banco non sarebbe più la copia della costante.
 */
internal const val GlassShadowReachSigmas: Float = 3.0f

/**
 * Sotto quest'alfa complessiva l'ombra non si disegna e non si registra: un quarto di livello su una pagina
 * bianca. Serve alla comparsa (`materialize` che parte da zero): i primi fotogrammi non pagano due maschere
 * per un'ombra che non si vede.
 */
internal const val GlassShadowMinAlpha: Float = 0.001f

/**
 * Di quanto può spostarsi il peso del contatto sul totale prima che le maschere si rifacciano.
 *
 * Le due maschere stanno in un layer solo, quindi il loro **rapporto** è registrato (vedi [GlassShadowLayer]).
 * Chi scala le due alfa insieme non lo cambia, ma il conto in virgola mobile sì, di un'unghia: senza una
 * soglia un'ombra che compare rifarebbe le maschere a ogni fotogramma. Un 255-esimo del totale è meno di
 * quanto distingue il layer, che è a 8 bit.
 */
internal const val GlassShadowWeightStep: Float = 1f / 255f

/**
 * La legge di caduta dell'ombra in Kotlin puro: gli specchi di `glassShadowSigma`, `glassShadowFall` e
 * `glassTravelShadow` di [GlassTravelShadowBlock], e i conti che il nodo a riposo fa con gli stessi numeri.
 *
 * Esiste perché riposo e viaggio sono due algoritmi (una maschera sfumata dalla piattaforma, una formula
 * nello shader) che devono dare la stessa ombra: l'unico modo di tenerli insieme è che partano dalle stesse
 * funzioni, e che quelle funzioni si possano provare su JVM.
 */
internal object GlassShadowProfile {

  /** La deviazione standard, in pixel, di una maschera sfumata con raggio [radius] (pixel). */
  fun sigma(radius: Float): Float = GlassShadowSigmaPerRadius * max(radius, 0f) + GlassShadowSigmaFloor

  /** L'inverso di [sigma]: il raggio che dà quella deviazione; zero se la deviazione è sotto il mezzo pixel. */
  fun radiusForSigma(sigma: Float): Float = max((sigma - GlassShadowSigmaFloor) / GlassShadowSigmaPerRadius, 0f)

  /**
   * Il raggio da usare in uno spazio rimpicciolito di [scale] perché l'ombra, riportata in scala, sia larga
   * come quella di raggio [radius] a piena risoluzione.
   *
   * Non è `radius * scale`: il mezzo pixel di [GlassShadowSigmaFloor] non scala con il resto. Serve in due
   * posti: al layer a riposo quando il tetto di 1024 px lo rimpicciolisce, e a chi riempie `shadowRadii`
   * quando la cattura del corpo è a risoluzione ridotta.
   */
  fun scaledRadius(radius: Float, scale: Float): Float = radiusForSigma(sigma(radius) * scale)

  /**
   * Quanto resta di una sagoma piena, a [x] pixel fuori dal suo contorno, dopo una sfocatura gaussiana di
   * deviazione [sigma]: un mezzo sul contorno, uno ben dentro, zero lontano. È `0,5 * erfc(x / (sigma * √2))`
   * con l'approssimazione 7.1.27 di Abramowitz e Stegun (errore sotto 0,0005: un ottavo di livello a piena
   * alfa, niente a un'alfa d'ombra), che non chiede `exp` allo shader.
   *
   * È esatto per un bordo dritto. Dove il contorno curva una maschera vera vale un poco meno (sotto di lei
   * c'è meno sagoma): è lo scarto fra riposo e viaggio, misurato in [GlassTravelShadowBlock].
   */
  fun fall(x: Float, sigma: Float): Float {
    val z = abs(x) / (sigma * 1.41421356f)
    var poly = 1f + z * (0.278393f + z * (0.230389f + z * (0.000972f + z * 0.078108f)))
    poly *= poly
    val tail = 0.5f / (poly * poly)
    return if (x >= 0f) tail else 1f - tail
  }

  /**
   * La quota dello scostamento che tocca al contatto: il rapporto fra i due raggi, mai oltre uno.
   *
   * Lo scostamento è uno solo ([GlassShadowSpec.offset], quello dell'ambiente) e il contatto ne prende la
   * parte che gli spetta per il suo raggio: è la stessa luce che allontana di più l'ombra più larga. Così
   * in viaggio le due componenti costano **una** valutazione del campo in più e non due (la distanza del
   * contatto si interpola fra quella del punto e quella del punto spostato), e riposo e viaggio non possono
   * avere due scostamenti diversi.
   */
  fun contactShare(contactRadius: Float, ambientRadius: Float): Float =
    if (ambientRadius > 0f) (contactRadius / ambientRadius).coerceIn(0f, 1f) else 1f

  /**
   * Fin dove arriva l'ombra oltre il contorno della sagoma, in pixel: oltre, lo shader esce senza valutare
   * il campo, e sotto `-reach` restituisce l'ombra piena.
   *
   * @param slide la lunghezza dello scostamento, in pixel.
   */
  fun reach(
    contactRadius: Float,
    ambientRadius: Float,
    contactAlpha: Float,
    ambientAlpha: Float,
    slide: Float,
  ): Float {
    val share = contactShare(contactRadius, ambientRadius)
    val contact = if (contactAlpha > 0f) GlassShadowReachSigmas * sigma(contactRadius) + share * slide else 0f
    val ambient = if (ambientAlpha > 0f) GlassShadowReachSigmas * sigma(ambientRadius) + slide else 0f
    return max(contact, ambient)
  }

  /**
   * `glassTravelShadow` senza il colore: l'alfa dell'ombra in un punto, **non scavata**.
   *
   * @param d la distanza con segno del punto dal contorno, in pixel.
   * @param seen la distanza con segno del punto spostato indietro di tutto lo scostamento (`p - shadowOffset`).
   */
  fun travel(
    d: Float,
    seen: Float,
    contactRadius: Float,
    ambientRadius: Float,
    contactAlpha: Float,
    ambientAlpha: Float,
    slide: Float,
  ): Float {
    val contact = max(contactAlpha, 0f)
    val ambient = max(ambientAlpha, 0f)
    if (contact + ambient <= 0f) return 0f
    val reach = reach(contactRadius, ambientRadius, contact, ambient, slide)
    if (d > reach) return 0f
    if (d < -reach) return min(contact + ambient, 1f)
    val share = contactShare(contactRadius, ambientRadius)
    val amount = contact * fall(d + (seen - d) * share, sigma(contactRadius)) +
      ambient * fall(seen, sigma(ambientRadius))
    return min(amount, 1f)
  }
}

// ------------------------------------------------------------------------------------------------------------
// In viaggio: il blocco AGSL
// ------------------------------------------------------------------------------------------------------------

/**
 * L'**ombra in viaggio** del materiale `Liquid` come blocco AGSL.
 *
 * A riposo l'ombra sono due maschere sfumate in cache ([GlassShadowLayer]). In viaggio la sagoma cambia a ogni
 * fotogramma (un ponte fra due pezzi non ha un path) e rifare due maschere sfocate su CPU a quella cadenza non
 * sta nel fotogramma: l'ombra esce allora dal campo, dentro lo shader del corpo, che in viaggio si riesegue
 * comunque.
 *
 * ### Dove si include
 * Nel solo corpo, dopo il nucleo ([GlassFieldKernel1] o [GlassFieldKernel8]) e prima di `main`: il blocco
 * chiama `glassField` per valutare il campo nel punto spostato. **Dichiara i propri uniform**: chi lo
 * include non li ridichiara. Non dichiara `materialize` né `aa`, che sono del corpo.
 *
 * ### uniform (piano 3.4)
 * - `float2 shadowOffset`: di quanto l'ombra d'**ambiente** è scostata dalla sagoma, in pixel dello spazio
 *   dello shader nel sistema del campo (y positiva = verso il basso). L'ombra in `p` è la sagoma vista da
 *   `p - shadowOffset`. Il **contatto** ne prende la quota `shadowRadii.x / shadowRadii.y`
 *   ([GlassShadowProfile.contactShare]);
 * - `float2 shadowRadii`: i raggi delle due componenti, in pixel dello spazio dello shader: `x` del contatto,
 *   `y` dell'ambiente. Sono **gli stessi numeri** del nodo a riposo (il raggio di sfocatura della maschera):
 *   la funzione li porta alla deviazione standard con il conto della piattaforma. Se la cattura è a
 *   risoluzione ridotta non basta moltiplicarli per la scala: li dà [GlassShadowProfile.scaledRadius];
 * - `float2 shadowAlphas`: 0..1, l'alfa di ogni componente **ben sotto la sagoma**: `x` contatto, `y`
 *   ambiente. Sul contorno, a scostamento nullo, ne resta metà: è così anche per una maschera sfumata;
 * - `layout(color) half4 shadowColor`: il colore dell'ombra, non premoltiplicato; la sua alfa moltiplica le
 *   due componenti.
 *
 * Uno uniform non impostato vale zero: con `shadowAlphas` a zero la funzione esce subito e non disegna.
 *
 * ### `half4 glassTravelShadow(float2 p, float d)`
 * - `p`: il punto nel sistema del campo, cioè `glassFieldPoint(coord)`;
 * - `d`: la distanza con segno in `p` che il corpo ha già calcolato (negativa dentro, in pixel).
 *
 * Restituisce l'ombra in quel punto come colore **premoltiplicato**, e **non scavata**: vale anche sotto la
 * sagoma, e non contiene né la copertura né `materialize`. La scava e la compone chi chiama, sotto il corpo:
 *
 *     half4 shade = glassTravelShadow(p, d) * half(materialize);
 *     return colour * half(coverage) + shade * half(1.0 - coverage);
 *
 * Perché l'ombra si veda il corpo deve disegnare anche **fuori** dalla sagoma: è il caso delle lastre che
 * viaggiano, che hanno il margine del campo e il ritaglio hardware spento. Il margine deve coprire
 * [GlassShadowProfile.reach], o l'ombra finisce contro il bordo della cattura.
 *
 * ### Quanto costa
 * Una valutazione del campo in più (nel punto spostato), e solo nella fascia dove l'ombra esiste: oltre
 * [GlassShadowProfile.reach] dal contorno esce con zero, e più in dentro di altrettanto esce con l'ombra
 * piena, in entrambi i casi senza toccare il campo. Con scostamento nullo non lo valuta mai.
 *
 * ### Le funzioni di servizio
 * `glassShadowSigma(radius)` e `glassShadowFall(x, sigma)` sono gli specchi di [GlassShadowProfile.sigma] e
 * [GlassShadowProfile.fall]: chi tocca una formula qui tocca la gemella là.
 *
 * ### Quanto dista dall'ombra a riposo (misurato, `artifacts/liquid-glass/bench/f1a-4/studio_ombra.py`)
 * La caduta è quella di una maschera sfumata lungo un bordo **dritto**, esatta. Dove il contorno curva, o
 * dove la lastra è piccola rispetto alla sfocatura, una maschera vera vale meno, e questa funzione non lo sa
 * (non conosce la curvatura: le servirebbero altre tre valutazioni del campo). Su una pagina a 242, a
 * densità 3, fuori dalla sagoma:
 *
 * | ombra | tasto 44 dp | capsula 156 x 56 | menu 170 x 192 | pannello 320 x 400 |
 * |---|---|---|---|---|
 * | di serie (corta, iOS 27) | 0,4 livelli | 0,3 | 0,4 | 0,3 |
 * | «iOS 26» (raggio 34 dp) | 5,6 | 3,4 | 3,0 | 2,4 |
 * | «oggi» (raggio 20 dp) | 3,3 | 2,4 | 2,5 | 1,9 |
 *
 * In viaggio è sempre la più forte delle due. Con l'ombra di serie il passaggio viaggio → riposo non può
 * vedersi (sul banco, `prova-ombra-riposo-ios27` contro `prova-ombra-viaggio-ios27`: nessun pixel oltre un
 * livello fuori dalla sagoma); con una delle due ombre larghe sì, e più sul tasto che sul pannello: se una
 * tavola le sceglie, la parità va chiusa in F1B (quattro campioni del campo dimezzano lo scarto; registrare
 * la maschera a riposo con questa stessa funzione lo azzera). In più, sull'anello di un pixel dove vive
 * l'antialias del contorno, la maschera a riposo è scavata con la copertura del path e poi coperta dal
 * corpo: lì porta circa metà dell'ombra (di serie in media mezzo livello in meno, 2 nel conto e fino a 4
 * su qualche pixel del banco), dove in viaggio il corpo la compone intera.
 */
@Language("AGSL")
internal const val GlassTravelShadowBlock = """
uniform float2 shadowOffset;
uniform float2 shadowRadii;
uniform float2 shadowAlphas;
layout(color) uniform half4 shadowColor;

float glassShadowSigma(float radius) {
    return $GlassShadowSigmaPerRadius * max(radius, 0.0) + $GlassShadowSigmaFloor;
}

float glassShadowFall(float x, float sigma) {
    float z = abs(x) / (sigma * 1.41421356);
    float poly = 1.0 + z * (0.278393 + z * (0.230389 + z * (0.000972 + z * 0.078108)));
    poly = poly * poly;
    float tail = 0.5 / (poly * poly);
    return x >= 0.0 ? tail : 1.0 - tail;
}

half4 glassTravelShadow(float2 p, float d) {
    float contactAlpha = max(shadowAlphas.x, 0.0);
    float ambientAlpha = max(shadowAlphas.y, 0.0);
    if (contactAlpha + ambientAlpha <= 0.0) return half4(0.0);

    float contactSigma = glassShadowSigma(shadowRadii.x);
    float ambientSigma = glassShadowSigma(shadowRadii.y);
    float share = shadowRadii.y > 0.0 ? clamp(shadowRadii.x / shadowRadii.y, 0.0, 1.0) : 1.0;
    float slide = length(shadowOffset);
    float contactReach = contactAlpha > 0.0 ? $GlassShadowReachSigmas * contactSigma + share * slide : 0.0;
    float ambientReach = ambientAlpha > 0.0 ? $GlassShadowReachSigmas * ambientSigma + slide : 0.0;
    float reach = max(contactReach, ambientReach);
    if (d > reach) return half4(0.0);

    float amount = contactAlpha + ambientAlpha;
    if (d >= -reach) {
        // La sagoma vista dal punto spostato: l'unica valutazione del campo che l'ombra aggiunge.
        float seen = d;
        if (slide > 0.0) {
            float2 seenNormal;
            float2 seenCenter;
            seen = glassField(p - shadowOffset, seenNormal, seenCenter);
        }
        amount = contactAlpha * glassShadowFall(mix(d, seen, share), contactSigma)
            + ambientAlpha * glassShadowFall(seen, ambientSigma);
    }
    half strength = half(min(amount, 1.0)) * shadowColor.a;
    return half4(shadowColor.rgb * strength, strength);
}"""

// ------------------------------------------------------------------------------------------------------------
// La ricetta: i numeri dell'ombra
// ------------------------------------------------------------------------------------------------------------

/**
 * I numeri di un'ombra: gli stessi a riposo (il nodo) e in viaggio (gli uniform di [GlassTravelShadowBlock]).
 *
 * Due componenti, perché una sola non sa fare tutte e due le cose: il **contatto** è stretto e sta a ridosso
 * del bordo (è la riga più scura che dice «questo oggetto tocca quasi la pagina»), l'**ambiente** è largo,
 * leggero e scende sotto la lastra (dice da che parte viene la luce). Un'ombra sola, larga abbastanza da
 * fare l'ambiente, al bordo è un alone uniforme.
 *
 * @param contactRadius il raggio di sfocatura della maschera di contatto.
 * @param ambientRadius quello della maschera d'ambiente.
 * @param offset di quanto scende l'ambiente. Il contatto ne prende la quota `contactRadius / ambientRadius`
 *   ([GlassShadowProfile.contactShare]): lo scostamento è uno solo, perché in viaggio uno solo ne arriva allo
 *   shader.
 * @param contactAlpha 0..1, l'alfa del contatto ben sotto la sagoma (sul contorno ne resta circa metà).
 * @param ambientAlpha lo stesso per l'ambiente.
 * @param color il colore; la sua alfa moltiplica le due componenti. Cambiarlo non rifà le maschere.
 */
@Immutable
internal data class GlassShadowSpec(
  val contactRadius: Dp,
  val ambientRadius: Dp,
  val offset: DpOffset,
  val contactAlpha: Float,
  val ambientAlpha: Float,
  val color: Color = Color.Black,
) {

  /**
   * La stessa ombra a un'altra forza: [amount] moltiplica le due alfa (è `intensity` per `opticalDepth` per
   * qualità, e più avanti il rinforzo della sonda). Il rapporto fra le due non cambia, quindi le maschere
   * registrate restano buone.
   */
  fun scaled(amount: Float): GlassShadowSpec {
    val k = if (amount.isFinite()) max(amount, 0f) else 0f
    return if (k == 1f) this else copy(contactAlpha = contactAlpha * k, ambientAlpha = ambientAlpha * k)
  }

  /**
   * L'ombra di una lastra più grande: [gain] è il guadagno della regola della taglia dell'engine
   * (`glassSizeGain`: zero fino a 64 dp di lato corto, uno da 320 dp), [growth] dice quanto crescono raggi,
   * scostamento e alfa a guadagno pieno. Lo scostamento cresce con i raggi: la luce viene dalla stessa parte.
   */
  fun grown(gain: Float, growth: GlassShadowGrowth): GlassShadowSpec {
    val g = if (gain.isFinite()) gain.coerceIn(0f, 1f) else 0f
    if (g <= 0f || (growth.radius == 0f && growth.alpha == 0f)) return this
    val reach = 1f + growth.radius * g
    val weight = 1f + growth.alpha * g
    return copy(
      contactRadius = contactRadius * reach,
      ambientRadius = ambientRadius * reach,
      offset = DpOffset(offset.x * reach, offset.y * reach),
      contactAlpha = (contactAlpha * weight).coerceIn(0f, 1f),
      ambientAlpha = (ambientAlpha * weight).coerceIn(0f, 1f),
    )
  }
}

/**
 * Di quanto cresce l'ombra dal tasto al pannello: a guadagno pieno della regola della taglia i raggi (e lo
 * scostamento) si moltiplicano per `1 + radius`, le alfa per `1 + alpha`. Vedi [GlassShadowSpec.grown].
 */
@Immutable
internal data class GlassShadowGrowth(val radius: Float, val alpha: Float)

/**
 * I nomi con cui F1A.4 chiamava i valori di serie dell'ombra. **I numeri non sono più qui**: da F1A.5 stanno
 * nel profilo, in [GlassLiquidShadows] e [GlassLiquidGrowths] (`GlassLiquidProfile.kt`), con le loro fonti, e
 * questi sono rimandi. Restano perché `GlassShadowTest` li nomina; il codice nuovo legge il profilo.
 */
internal object GlassShadowDefaults {

  /** L'ombra di serie del lato chiaro: [GlassLiquidShadows.Ios27]. */
  val Ios27: GlassShadowSpec get() = GlassLiquidShadows.Ios27

  /** La variante «iOS 26» delle tavole: [GlassLiquidShadows.Ios26]. */
  val Ios26: GlassShadowSpec get() = GlassLiquidShadows.Ios26

  /** La variante «oggi» delle tavole: [GlassLiquidShadows.today]. */
  fun today(radius: Dp, alpha: Float, darkSide: Boolean): GlassShadowSpec =
    GlassLiquidShadows.today(radius, alpha, darkSide)

  /** I grandi «come una barra»: l'ombra di [GlassLiquidGrowths.None]. */
  val GrowthNone: GlassShadowGrowth get() = GlassLiquidGrowths.None.shadow

  /** I grandi «un passo in più»: l'ombra di [GlassLiquidGrowths.Step]. */
  val GrowthStep: GlassShadowGrowth get() = GlassLiquidGrowths.Step.shadow

  /** I grandi «due passi in più»: l'ombra di [GlassLiquidGrowths.TwoSteps]. */
  val GrowthTwoSteps: GlassShadowGrowth get() = GlassLiquidGrowths.TwoSteps.shadow
}

// ------------------------------------------------------------------------------------------------------------
// A riposo: la sagoma, i numeri del layer, il contorno
// ------------------------------------------------------------------------------------------------------------

/**
 * La sagoma ferma di cui si fa l'ombra, **nella forma in cui la conosce il campo**: gli stessi pezzi che il
 * corpo riceve negli uniform (`pieceRect`, `pieceRadii`, `pieceSmooth`), in pixel del nodo.
 *
 * L'ombra parte da qui e non da una `Shape` perché nel materiale `Liquid` la sagoma che si vede non la
 * disegna un path: la disegna il campo, con il suo raccordo. Un'ombra presa dal path dell'engine avrebbe,
 * accanto al bordo dei grandi, una curva che dista dal vetro fino allo 0,44% del raggio; presa dagli stessi
 * numeri del corpo non può distare.
 *
 * A riposo i pezzi di un grappolo non si fondono (`pieceBlend` vale zero: il collo esiste solo in viaggio),
 * quindi la sagoma è l'unione netta dei pezzi e qui il raggio di fusione non c'è.
 *
 * È un contenitore che chi chiama riusa: si riempie a ogni disegno senza allocare.
 */
internal class GlassShadowPieces {

  /** Quanti pezzi sono vivi, da 0 a [GlassFieldMaxPieces]. */
  var count: Int = 0
    private set

  /** Per pezzo: centro x, centro y, mezza larghezza, mezza altezza. */
  val rects: FloatArray = FloatArray(GlassFieldMaxPieces * GlassFieldGeometry.FloatsPerPiece)

  /** Per pezzo i raggi nominali: alto-sinistra, alto-destra, basso-destra, basso-sinistra. */
  val radii: FloatArray = FloatArray(GlassFieldMaxPieces * GlassFieldGeometry.FloatsPerPiece)

  /** Per pezzo lo `smoothing` chiesto, 0..1: la riduzione del lato corto la fa il campo. */
  val smooth: FloatArray = FloatArray(GlassFieldMaxPieces)

  fun clear() {
    count = 0
  }

  /** Aggiunge un pezzo; oltre [GlassFieldMaxPieces] non fa niente, come lo shader che non li leggerebbe. */
  fun add(
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    smoothing: Float,
  ): GlassShadowPieces {
    if (count >= GlassFieldMaxPieces) return this
    val at = count * GlassFieldGeometry.FloatsPerPiece
    rects[at] = centerX
    rects[at + 1] = centerY
    rects[at + 2] = halfWidth
    rects[at + 3] = halfHeight
    radii[at] = topLeft
    radii[at + 1] = topRight
    radii[at + 2] = bottomRight
    radii[at + 3] = bottomLeft
    smooth[count] = smoothing
    count++
    return this
  }

  /** Il caso di ogni `glassSurface`: un pezzo solo, grande quanto il nodo. */
  fun setSlab(
    width: Float,
    height: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    smoothing: Float,
  ): GlassShadowPieces {
    clear()
    return add(width / 2f, height / 2f, width / 2f, height / 2f, topLeft, topRight, bottomRight, bottomLeft, smoothing)
  }

  fun copyFrom(other: GlassShadowPieces) {
    count = other.count
    other.rects.copyInto(rects)
    other.radii.copyInto(radii)
    other.smooth.copyInto(smooth)
  }

  /** Gli stessi pezzi, numero per numero: è la «forma» fra le condizioni che rifanno le maschere. */
  fun sameAs(other: GlassShadowPieces): Boolean {
    if (count != other.count) return false
    for (i in 0 until count * GlassFieldGeometry.FloatsPerPiece) {
      if (rects[i] != other.rects[i] || radii[i] != other.radii[i]) return false
    }
    for (i in 0 until count) {
      if (smooth[i] != other.smooth[i]) return false
    }
    return true
  }

  /** Vero se nessun pezzo ha un'area: non c'è niente di cui fare l'ombra. */
  fun isEmpty(): Boolean {
    for (i in 0 until count) {
      val at = i * GlassFieldGeometry.FloatsPerPiece
      if (rects[at + 2] > 0f && rects[at + 3] > 0f) return false
    }
    return true
  }
}

/**
 * Chi sa dire la sagoma ferma di una superficie. Lo implementa chi traduce la forma nel piano del campo
 * (F1A.6, «forma → piano» del piano 3.7): gli stessi numeri che manda al corpo li scrive qui.
 */
internal fun interface GlassShadowOutline {
  /** Scrive in [out] i pezzi della sagoma, in pixel del nodo che sta disegnando (la sua taglia è `size`). */
  fun DrawScope.resolve(out: GlassShadowPieces)
}

/**
 * I numeri del layer dell'ombra a riposo, da soli: dove sta, quanto è grande, a che risoluzione, con quali
 * raggi e scostamenti si registrano le due maschere. Puri, per poterli provare su JVM.
 *
 * È un contenitore che il nodo riusa: [resolve] lo riscrive.
 */
internal class GlassShadowMetrics {

  /** L'angolo in alto a sinistra del layer, in pixel del nodo: interi, perché il layer non si ricampioni. */
  var originX: Float = 0f
    private set
  var originY: Float = 0f
    private set

  /** La frazione di risoluzione a cui il layer è registrato: 1 finché il lato lungo sta in [maxPx]. */
  var resolution: Float = 1f
    private set

  /** La taglia del layer registrato, in pixel suoi. */
  var layerWidth: Int = 0
    private set
  var layerHeight: Int = 0
    private set

  /** I raggi di sfocatura delle due maschere **nel layer** (già corretti per [resolution]). */
  var contactBlur: Float = 0f
    private set
  var ambientBlur: Float = 0f
    private set

  /** Gli scostamenti delle due maschere nel layer (già moltiplicati per [resolution]). */
  var contactOffsetX: Float = 0f
    private set
  var contactOffsetY: Float = 0f
    private set
  var ambientOffsetX: Float = 0f
    private set
  var ambientOffsetY: Float = 0f
    private set

  /**
   * I raggi ([contactRadius], [ambientRadius]) sono quelli delle due maschere in pixel dello schermo;
   * ([offsetX], [offsetY]) è lo scostamento dell'ambiente in pixel, e il contatto ne prende la sua quota;
   * [contact] e [ambient] dicono quali componenti si disegnano: una spenta non allarga il layer.
   *
   * @param maxPx il tetto del lato lungo del layer ([HighlightLayerMaxPx]).
   */
  fun resolve(
    pieces: GlassShadowPieces,
    contactRadius: Float,
    ambientRadius: Float,
    offsetX: Float,
    offsetY: Float,
    contact: Boolean,
    ambient: Boolean,
    maxPx: Float = HighlightLayerMaxPx,
  ) {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (i in 0 until pieces.count) {
      val at = i * GlassFieldGeometry.FloatsPerPiece
      left = min(left, pieces.rects[at] - pieces.rects[at + 2])
      top = min(top, pieces.rects[at + 1] - pieces.rects[at + 3])
      right = max(right, pieces.rects[at] + pieces.rects[at + 2])
      bottom = max(bottom, pieces.rects[at + 1] + pieces.rects[at + 3])
    }
    if (pieces.count <= 0) {
      left = 0f
      top = 0f
      right = 0f
      bottom = 0f
    }

    val share = GlassShadowProfile.contactShare(contactRadius, ambientRadius)
    val contactX = offsetX * share
    val contactY = offsetY * share
    val contactReach = if (contact) GlassShadowReachSigmas * GlassShadowProfile.sigma(contactRadius) else 0f
    val ambientReach = if (ambient) GlassShadowReachSigmas * GlassShadowProfile.sigma(ambientRadius) else 0f
    // Per lato, quanto sporge la maschera che sporge di più; mai meno di zero: lo scavo vuole tutta la sagoma.
    var growLeft = 0f
    var growTop = 0f
    var growRight = 0f
    var growBottom = 0f
    if (contact) {
      growLeft = max(growLeft, contactReach - contactX)
      growTop = max(growTop, contactReach - contactY)
      growRight = max(growRight, contactReach + contactX)
      growBottom = max(growBottom, contactReach + contactY)
    }
    if (ambient) {
      growLeft = max(growLeft, ambientReach - offsetX)
      growTop = max(growTop, ambientReach - offsetY)
      growRight = max(growRight, ambientReach + offsetX)
      growBottom = max(growBottom, ambientReach + offsetY)
    }
    originX = floor(left - growLeft)
    originY = floor(top - growTop)
    val width = ceil(right + growRight) - originX
    val height = ceil(bottom + growBottom) - originY

    resolution = layerResolutionScale(width, height, maxPx)
    layerWidth = max(ceil(width * resolution).toInt(), 1)
    layerHeight = max(ceil(height * resolution).toInt(), 1)
    contactBlur = GlassShadowProfile.scaledRadius(contactRadius, resolution)
    ambientBlur = GlassShadowProfile.scaledRadius(ambientRadius, resolution)
    contactOffsetX = contactX * resolution
    contactOffsetY = contactY * resolution
    ambientOffsetX = offsetX * resolution
    ambientOffsetY = offsetY * resolution
  }
}

/**
 * Il contorno di un pezzo del campo come spezzata: i punti dove `glassSlab` vale zero.
 *
 * Serve alla maschera a riposo, che è un path. Per un angolo circolare un path ha l'arco esatto, e lì la
 * spezzata non si usa ([isCircular]); per l'angolo raccordato (la superellisse di [GlassCornerReachSquare])
 * un path non ha una curva pronta, e questa è la curva del campo campionata abbastanza fitta da distare dal
 * vero meno di 0,04 px (`artifacts/liquid-glass/bench/f1a-4/studio_ombra.py`, e `GlassShadowTest`).
 */
internal object GlassShadowContour {

  const val MinSegments: Int = 6
  const val MaxSegments: Int = 64

  /** Quanti numeri può scrivere [slab]: quattro angoli, ognuno con un punto in più dei suoi segmenti. */
  const val MaxFloats: Int = 4 * (MaxSegments + 1) * 2

  /**
   * In quanti segmenti si divide un angolo che consuma [reachPx] pixel di lato. La freccia di una corda
   * cresce col quadrato della sua lunghezza e cala col raggio, quindi a scarto costante i segmenti crescono
   * con la radice del raggio: tre per radice di pixel tengono lo scarto a 0,035 px da 4 a 480 px.
   */
  fun segments(reachPx: Float): Int =
    ceil(3f * sqrt(max(reachPx, 0f))).toInt().coerceIn(MinSegments, MaxSegments)

  /**
   * Vero se i quattro angoli del pezzo sono archi di cerchio: nessun raccordo da spendere (una capsula, un
   * piccolo che l'engine disegna già circolare, `smoothing` zero). Allora il pezzo è un `RoundRect`, che la
   * piattaforma sfoca senza passare da una maschera su CPU.
   */
  fun isCircular(halfWidth: Float, halfHeight: Float, radii: FloatArray, radiiAt: Int, smoothing: Float): Boolean {
    val minHalf = min(halfWidth, halfHeight)
    for (k in 0 until 4) {
      val radius = max(min(radii[radiiAt + k], minHalf), 0f)
      if (GlassFieldGeometry.effectiveSmoothing(radius, minHalf, smoothing) > 0f) return false
    }
    return true
  }

  /**
   * Scrive in [out] il contorno del pezzo, in senso orario sullo schermo (y verso il basso) a partire dal
   * lato alto, come coppie x, y. Restituisce quanti **punti** ha scritto.
   *
   * Gli argomenti sono quelli di [GlassFieldGeometry.slab]: stesso centro, stessa mezza taglia, stessi raggi
   * nominali, stesso `smoothing` chiesto. Le riduzioni (raggio a mezzo lato corto, raccordo che il lato
   * lascia spendere) sono le sue.
   */
  fun slab(
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    radii: FloatArray,
    radiiAt: Int,
    smoothing: Float,
    out: FloatArray,
  ): Int {
    val minHalf = min(halfWidth, halfHeight)
    var points = 0
    // In senso orario dal lato alto: alto-destra, basso-destra, basso-sinistra, alto-sinistra. Per ogni
    // angolo il lato da cui si entra e quello da cui si esce, come direzioni che puntano fuori dal pezzo.
    for (corner in 0 until 4) {
      val nominal: Float
      val entryX: Float
      val entryY: Float
      val exitX: Float
      val exitY: Float
      when (corner) {
        0 -> { nominal = radii[radiiAt + 1]; entryX = 0f; entryY = -1f; exitX = 1f; exitY = 0f }
        1 -> { nominal = radii[radiiAt + 2]; entryX = 1f; entryY = 0f; exitX = 0f; exitY = 1f }
        2 -> { nominal = radii[radiiAt + 3]; entryX = 0f; entryY = 1f; exitX = -1f; exitY = 0f }
        else -> { nominal = radii[radiiAt]; entryX = -1f; entryY = 0f; exitX = 0f; exitY = -1f }
      }
      val radius = max(min(nominal, minHalf), 0f)
      val s = GlassFieldGeometry.effectiveSmoothing(radius, minHalf, smoothing)
      val reach = GlassFieldGeometry.cornerReach(radius, s)
      val power = GlassFieldGeometry.cornerPower(s)
      // Il vertice dell'angolo e il centro della superellisse, che sta `reach` più in dentro su ogni asse.
      val tipX = centerX + (entryX + exitX) * halfWidth
      val tipY = centerY + (entryY + exitY) * halfHeight
      if (reach <= 0.0001f) {
        out[points * 2] = tipX
        out[points * 2 + 1] = tipY
        points++
        continue
      }
      val coreX = tipX - (entryX + exitX) * reach
      val coreY = tipY - (entryY + exitY) * reach
      val steps = segments(reach)
      val exponent = 2.0 / power
      for (i in 0..steps) {
        val t = (PI / 2.0) * i / steps
        // |x|^n + |y|^n = reach^n: seno e coseno alla 2/n. Con n = 2 è l'arco di cerchio.
        val along = (reach * sin(t).pow(exponent)).toFloat()
        val across = (reach * cos(t).pow(exponent)).toFloat()
        out[points * 2] = coreX + entryX * across + exitX * along
        out[points * 2 + 1] = coreY + entryY * across + exitY * along
        points++
      }
    }
    return points
  }
}

// ------------------------------------------------------------------------------------------------------------
// A riposo: il layer e il nodo
// ------------------------------------------------------------------------------------------------------------

/**
 * L'ombra a riposo di una lastra `Liquid`: **due maschere sfumate in un layer solo**, registrate una volta.
 *
 * ### Cosa c'è nel layer
 * Il contorno del campo ([GlassShadowContour]) disegnato due volte in **bianco**, con due `BlurMaskFilter`:
 * il contatto (stretto, appena scostato) e l'ambiente (largo, scostato in basso), sommati, e poi la sagoma
 * scavata via: sotto il vetro non resta ombra, come fa `ShadowNode` con una maschera sola. Il layer è
 * `Offscreen`, tenuto dalla piattaforma finché non lo si registra di nuovo.
 *
 * ### Cosa non è nel layer: colore e alfa
 * `ShadowNode` registra il colore nel pennello, e un colore che cambia gli rifà la maschera (due sfocature su
 * CPU e un caricamento). Qui le maschere sono sola alfa: il **colore** lo mette il filtro di colore del layer
 * e l'**alfa** la sua proprietà `alpha`, tutte e due in disegno. In F2 una sonda le cambierà più volte al
 * secondo, e non deve costare altro che una proprietà di un `RenderNode`. C'è anche un guadagno di resa: nel
 * layer la maschera usa tutti gli 8 bit, e l'alfa di un'ombra al 4% si applica dopo, in virgola mobile; con
 * l'alfa nel pennello la maschera avrebbe dieci livelli in tutto.
 *
 * Ciò che il layer **non** può separare è il **rapporto** fra le due alfa: le maschere vi si sommano già
 * pesate (un layer ha un'alfa sola). Chi scala le due insieme (forza, comparsa, qualità, rinforzo della
 * sonda) non rifà niente; chi cambia il peso dell'una rispetto all'altra rifà le maschere, come per un
 * raggio. Le separerebbe un layer a due canali con una matrice di colore, ma i canali si mescolano appena la
 * finestra non è sRGB: non vale il rischio per un caso che la ricetta non ha.
 *
 * ### Quando si rifanno le maschere
 * Sagoma (pezzi, raggi, raccordo: quindi anche la taglia), i due raggi, lo scostamento, il rapporto fra le
 * due alfa oltre [GlassShadowWeightStep], una componente che si accende o si spegne. Mai per colore o alfa.
 *
 * ### La lambda di `record` dipende solo da questo layer
 * Registrato una volta non vuol dire eseguito una volta: Compose riesegue la lambda da sé quando la
 * piattaforma ha buttato la display list (`ref/regole.md`, «La lambda di `record` dipende solo dal proprio
 * layer»; è così che è nato il contorno fantasma della 3.x). Per questo la lambda legge soltanto valori
 * catturati alla registrazione e il [path] e i pennelli **di questo oggetto**, che nessun altro riscrive e
 * che cambiano solo un attimo prima di una registrazione nuova dello stesso layer. Niente `Path`, pennello o
 * contenitore condiviso fra superfici: il contorno si calcola in un appoggio, e nel [path] si copia.
 * `GlassShadowTest` lo prova con il doppio della piattaforma di `PhysicsShadowSlotsTest`.
 *
 * @param layer il layer di questa lastra, e di nessun'altra. Chi lo crea lo rilascia.
 */
internal class GlassShadowLayer(private val layer: GraphicsLayer) {

  /** La sagoma registrata, in pixel del layer. La legge la lambda di `record`: è solo di questo oggetto. */
  private val path = Path()

  /** I pennelli delle due maschere e dello scavo: anche questi solo di questo oggetto. */
  private val contactPaint = Paint()
  private val ambientPaint = Paint().apply { blendMode = BlendMode.Plus }
  private val carvePaint = Paint().apply { blendMode = BlendMode.Clear }

  // Ciò che è registrato: se uno di questi non torna, le maschere si rifanno.
  private val recordedPieces = GlassShadowPieces()
  private var recordedContactRadius = Float.NaN
  private var recordedAmbientRadius = Float.NaN
  private var recordedOffsetX = Float.NaN
  private var recordedOffsetY = Float.NaN
  private var recordedWeight = Float.NaN
  private var recorded = false

  // Dove va disegnato ciò che è registrato.
  private val metrics = GlassShadowMetrics()

  // Solo per costruire il path prima di registrare: nessuna lambda li legge.
  private val contour = FloatArray(GlassShadowContour.MaxFloats)
  private val scaledRadii = FloatArray(GlassFieldGeometry.FloatsPerPiece)

  private var tintColor: Color = Color.Unspecified
  private var tint: ColorFilter? = null

  /** Quante volte le maschere sono state registrate: per la diagnostica e per i test. */
  var recordCount: Int = 0
    private set

  init {
    layer.compositingStrategy = CompositingStrategy.Offscreen
  }

  /**
   * Disegna l'ombra di [pieces] con i numeri di [spec], registrando le maschere solo se serve.
   *
   * [scope] è quello del nodo che ospita la lastra: l'origine è il suo angolo in alto a sinistra e i pixel
   * sono i suoi. Va chiamato **prima** del contenuto e fuori dal ritaglio della lastra.
   */
  fun draw(scope: DrawScope, pieces: GlassShadowPieces, spec: GlassShadowSpec) {
    val contactAlpha = finiteUnit(spec.contactAlpha)
    val ambientAlpha = finiteUnit(spec.ambientAlpha)
    val total = contactAlpha + ambientAlpha
    val alpha = (total * spec.color.alpha).coerceIn(0f, 1f)
    if (!(alpha > GlassShadowMinAlpha) || pieces.isEmpty()) return

    // `scope` è anche la densità: i numeri della ricetta sono in dp, il layer è in pixel.
    val contactRadius = finiteLength(with(scope) { spec.contactRadius.toPx() })
    val ambientRadius = finiteLength(with(scope) { spec.ambientRadius.toPx() })
    val offsetX = finiteOffset(with(scope) { spec.offset.x.toPx() })
    val offsetY = finiteOffset(with(scope) { spec.offset.y.toPx() })
    // Il peso del contatto sul totale: è il solo modo in cui le due alfa entrano nella registrazione.
    val weight = contactAlpha / total

    val stale = !recorded ||
      !recordedPieces.sameAs(pieces) ||
      recordedContactRadius != contactRadius ||
      recordedAmbientRadius != ambientRadius ||
      recordedOffsetX != offsetX ||
      recordedOffsetY != offsetY ||
      // Una componente che si accende o si spegne cambia ciò che si disegna, non solo di quanto.
      (weight > 0f) != (recordedWeight > 0f) ||
      (weight < 1f) != (recordedWeight < 1f) ||
      abs(weight - recordedWeight) > GlassShadowWeightStep
    if (stale) {
      record(scope, pieces, contactRadius, ambientRadius, offsetX, offsetY, weight)
    }

    layer.alpha = alpha
    layer.colorFilter = tintFor(spec.color)
    val enlarge = 1f / metrics.resolution
    with(scope) {
      translate(metrics.originX, metrics.originY) {
        if (enlarge == 1f) {
          drawLayer(layer)
        } else {
          scale(enlarge, enlarge, pivot = Offset.Zero) { drawLayer(layer) }
        }
      }
    }
  }

  private fun record(
    scope: DrawScope,
    pieces: GlassShadowPieces,
    contactRadius: Float,
    ambientRadius: Float,
    offsetX: Float,
    offsetY: Float,
    weight: Float,
  ) {
    val drawContact = weight > 0f
    val drawAmbient = weight < 1f
    metrics.resolve(pieces, contactRadius, ambientRadius, offsetX, offsetY, drawContact, drawAmbient)
    buildPath(pieces)

    // Bianco, e il peso nell'alfa del pennello: sommate (`Plus`) le due maschere non superano mai uno, e
    // l'alfa del layer le riporta alle due alfa chieste.
    contactPaint.color = Color.White.copy(alpha = weight)
    contactPaint.blur(metrics.contactBlur)
    ambientPaint.color = Color.White.copy(alpha = 1f - weight)
    ambientPaint.blur(metrics.ambientBlur)

    // Tutto ciò che la lambda legge: valori catturati qui, e il path e i pennelli di questo oggetto.
    val silhouette = path
    val contact = contactPaint
    val ambient = ambientPaint
    val carve = carvePaint
    val contactX = metrics.contactOffsetX
    val contactY = metrics.contactOffsetY
    val ambientX = metrics.ambientOffsetX
    val ambientY = metrics.ambientOffsetY
    with(scope) {
      layer.record(IntSize(metrics.layerWidth, metrics.layerHeight)) {
        val canvas = drawContext.canvas
        if (drawContact) {
          canvas.translate(contactX, contactY)
          canvas.drawPath(silhouette, contact)
          canvas.translate(-contactX, -contactY)
        }
        if (drawAmbient) {
          canvas.translate(ambientX, ambientY)
          canvas.drawPath(silhouette, ambient)
          canvas.translate(-ambientX, -ambientY)
        }
        canvas.drawPath(silhouette, carve)
      }
    }

    recordedPieces.copyFrom(pieces)
    recordedContactRadius = contactRadius
    recordedAmbientRadius = ambientRadius
    recordedOffsetX = offsetX
    recordedOffsetY = offsetY
    recordedWeight = weight
    recorded = true
    recordCount++
  }

  /** La sagoma nel sistema del layer: ogni pezzo spostato dell'origine e portato alla sua risoluzione. */
  private fun buildPath(pieces: GlassShadowPieces) {
    val res = metrics.resolution
    path.rewind()
    for (i in 0 until pieces.count) {
      val at = i * GlassFieldGeometry.FloatsPerPiece
      val halfWidth = pieces.rects[at + 2] * res
      val halfHeight = pieces.rects[at + 3] * res
      if (!(halfWidth > 0f) || !(halfHeight > 0f)) continue
      val centerX = (pieces.rects[at] - metrics.originX) * res
      val centerY = (pieces.rects[at + 1] - metrics.originY) * res
      val minHalf = min(halfWidth, halfHeight)
      for (k in 0 until GlassFieldGeometry.FloatsPerPiece) {
        scaledRadii[k] = max(min(pieces.radii[at + k] * res, minHalf), 0f)
      }
      val smoothing = pieces.smooth[i]
      if (GlassShadowContour.isCircular(halfWidth, halfHeight, scaledRadii, 0, smoothing)) {
        path.addRoundRect(
          RoundRect(
            left = centerX - halfWidth,
            top = centerY - halfHeight,
            right = centerX + halfWidth,
            bottom = centerY + halfHeight,
            topLeftCornerRadius = CornerRadius(scaledRadii[0]),
            topRightCornerRadius = CornerRadius(scaledRadii[1]),
            bottomRightCornerRadius = CornerRadius(scaledRadii[2]),
            bottomLeftCornerRadius = CornerRadius(scaledRadii[3]),
          ),
          Path.Direction.Clockwise,
        )
      } else {
        val points =
          GlassShadowContour.slab(centerX, centerY, halfWidth, halfHeight, scaledRadii, 0, smoothing, contour)
        path.moveTo(contour[0], contour[1])
        for (j in 1 until points) {
          path.lineTo(contour[j * 2], contour[j * 2 + 1])
        }
        path.close()
      }
    }
  }

  /** Il filtro che colora le maschere bianche: uno per colore, non uno per fotogramma. */
  private fun tintFor(color: Color): ColorFilter {
    val opaque = color.copy(alpha = 1f)
    val current = tint
    if (current != null && tintColor == opaque) return current
    val made = ColorFilter.tint(opaque, BlendMode.SrcIn)
    tintColor = opaque
    tint = made
    return made
  }

  /** Dimentica ciò che è registrato: la prossima chiamata a [draw] rifà le maschere. */
  fun invalidate() {
    recorded = false
  }

  private fun finiteUnit(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

  private fun finiteLength(value: Float): Float = if (value.isFinite()) max(value, 0f) else 0f

  private fun finiteOffset(value: Float): Float = if (value.isFinite()) value else 0f
}

/**
 * L'ombra a riposo sotto una lastra `Liquid`: va **prima** della lastra nella catena dei modificatori, fuori
 * dal suo ritaglio (piano 3.3), al posto di `ShadowNode`.
 *
 * @param outline la sagoma ferma nella forma del campo: gli stessi pezzi che riceve il corpo. A ogni disegno
 *   si risolve **prima** di chiedere [shadow].
 * @param shadow i numeri dell'ombra, letti **in disegno**: uno stato letto qui dentro (la forza, il colore
 *   della sonda) ridisegna il layer senza ricomporre e senza rifare le maschere. `null` = nessuna ombra (un
 *   ruolo senza, o una lastra che sta viaggiando e la disegna nello shader).
 */
internal fun Modifier.glassShadow(outline: GlassShadowOutline, shadow: () -> GlassShadowSpec?): Modifier =
  this then GlassShadowElement(outline, shadow)

internal class GlassShadowElement(
  val outline: GlassShadowOutline,
  val shadow: () -> GlassShadowSpec?,
) : ModifierNodeElement<GlassShadowNode>() {

  override fun create(): GlassShadowNode = GlassShadowNode(outline, shadow)

  override fun update(node: GlassShadowNode) {
    node.outline = outline
    node.shadow = shadow
    node.invalidateDraw()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "glassShadow"
    properties["outline"] = outline
    properties["shadow"] = shadow
  }

  override fun equals(other: Any?): Boolean =
    this === other || (other is GlassShadowElement && outline == other.outline && shadow == other.shadow)

  override fun hashCode(): Int = 31 * outline.hashCode() + shadow.hashCode()
}

/**
 * Il nodo dell'ombra a riposo: **un** layer, quello di [GlassShadowLayer]. È il terzo e ultimo layer vivo di
 * una lastra ferma (cattura, posa, ombra); con `shadow` che restituisce `null` il layer resta vuoto e non
 * si disegna.
 */
internal class GlassShadowNode(
  var outline: GlassShadowOutline,
  var shadow: () -> GlassShadowSpec?,
) : DrawModifierNode, Modifier.Node() {

  override val shouldAutoInvalidate: Boolean = false

  private var graphicsLayer: GraphicsLayer? = null
  private var painter: GlassShadowLayer? = null

  /** I pezzi della sagoma di questo fotogramma: del nodo, riempiti a ogni disegno. */
  private val pieces = GlassShadowPieces()

  override fun ContentDrawScope.draw() {
    val painter = painter
    if (painter != null) {
      // Prima la sagoma, poi i numeri (ritocco di F1A.6): l'ombra di una lastra cresce con la sua taglia, e
      // chi dà i numeri la taglia la legge qui, mentre risolve la sagoma (`shadow` non ha un `DrawScope`).
      pieces.clear()
      with(outline) { resolve(pieces) }
      val spec = shadow()
      if (spec != null) painter.draw(this, pieces, spec)
    }
    drawContent()
  }

  override fun onAttach() {
    val layer = requireGraphicsContext().createGraphicsLayer()
    graphicsLayer = layer
    // Un layer nuovo non contiene niente, e con lui nasce un `GlassShadowLayer` che non ha registrato niente.
    painter = GlassShadowLayer(layer)
  }

  override fun onDetach() {
    graphicsLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    graphicsLayer = null
    painter = null
  }
}
