package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Le misure dei tasti, delle barre e delle icone, in un posto solo (4.0, piano «iOS in Android», F1A.11).
 *
 * **Perche' esistono.** Alessio (2026-11-10): «le icone siano un po' piu' grassettate, come Apple [...] anche i tasti
 * devono essere leggermente piu' grandi mi sa». La ricerca R6 (`docs/liquid-glass/ricerca/icone-e-misure.md`) ha
 * misurato che non era il tratto ne' il tasto: il tratto delle icone era gia' quello di Apple (1,6-1,9 dp contro
 * 1,6-2,1 pt) e il vetro gia' 44 dp come i 44 pt di Apple (fiducia alta). Sembravano sottili e piccoli perche'
 * l'icona era **piu' piccola del 20-30%** (0,37 del tasto contro 0,44-0,48), **nel colore d'accento** (contrasto
 * 3,5-6,7:1 contro 15-17:1 di un'icona nera o bianca) e il tasto stava **a 8 dp dal bordo** dello schermo dove Apple
 * ne lascia 16-20. Questi numeri correggono le tre cause; le misure sono di R6, le scelte dell'orchestratore
 * (2026-11-10, sezione 2 del piano).
 *
 * Prima i numeri erano scritti a mano in ogni componente (22 dp nell'azione della barra, 20 nella freccia, 24 nella
 * capsula e nella tab bar, 6 dp di spaziatore): un'icona cambiava taglia in un posto e non nell'altro. Qui un
 * componente li legge, e chi li cambia li cambia per tutti. F3.5 completa l'oggetto con il passo dei gruppi e i
 * margini della capsula; le forme che seguono il contenitore stanno in [FluidConcentricShape]. Il bersaglio minimo
 * e' [TouchTarget].
 */
object FluidMetrics {

  // --- Tasti --------------------------------------------------------------------------------------------------

  /**
   * Il vetro di un tasto tondo solo icona: 46 dp (scelta B di F6.4). La stessa misura guida la riga dei
   * controlli e il passo dentro una capsula, così i tasti conservano le proporzioni in ogni barra.
   */
  val GlassButtonLens: Dp = 46.dp

  /**
   * Il bersaglio del tocco: mai sotto 48 dp, il minimo di Android (Apple ne documenta 44). Il vetro da 46 sta al
   * centro di questo riquadro, con 1 dp d'aria per lato che conta nei margini qui sotto.
   */
  val TouchTarget: Dp = 48.dp

  /**
   * Dal bordo dello schermo al **vetro** del primo tasto di una barra: 16 dp (prima 8). Apple ne lascia 20,1 pt su
   * uno schermo da 440 pt in iOS 27 e 14-16 pt in iOS 26 (R6, media); 16 avvicina il tasto al margine del contenuto
   * (20 dp) invece di farlo sporgere di 12, ed e' la meta' della ragione per cui i tasti sembravano piccoli.
   */
  val ScreenEdgeMargin: Dp = 16.dp

  /**
   * Fra il vetro di due tasti separati della stessa barra: 8 dp (prima 6). Apple ne misura 12,1 pt (R6, alta), ma
   * con tre azioni sciolte 12 dp sono troppi su un telefono da 360 dp: i 12 restano per due **gruppi** ([GroupSpacing]).
   */
  val GlassSpacing: Dp = 8.dp

  /**
   * Fra due gruppi di tasti (una capsula di azioni e un tondo accanto): 12 dp, i 12,1 pt di Apple (R6, alta). Lo usa
   * la barra dichiarata a gruppi (F4A.2); oggi nessun componente dell'engine ha gruppi.
   */
  val GroupSpacing: Dp = 12.dp

  /**
   * Il passo delle icone dentro un gruppo (una capsula di azioni): 46 dp da centro a centro, quindi tre azioni
   * occupano 138 dp più i margini. E' il passo di iOS 26, 1,0 volte l'altezza della capsula (R6, fiducia
   * media, render), e sta su ogni larghezza di telefono. iOS 27 in una barra ne misura 62 pt (R6, alta sul frame ma
   * su una barra sola con simboli larghi): resta un **bersaglio da rivedere** su altri frame, non una regola. Lo usa
   * la barra dichiarata a gruppi (F4A.2): ogni icona tiene comunque il suo bersaglio da [TouchTarget], che sborda
   * di 1 dp per lato nel vicino senza cambiare il passo.
   */
  val GroupItemPitch: Dp = GlassButtonLens

  // --- Capsula flottante e tab bar ------------------------------------------------------------------------------

  /**
   * Dal bordo laterale dello schermo al vetro della capsula flottante e della tab bar: 14 dp, il valore di sempre
   * (`FluidFloatingTabBarDefaults.HorizontalMargin`). Apple ne lascia 18,7 pt (R6, iOS 27, +-0,7 pt); R6 propone di
   * non toccarlo, e il cambio resta una scelta da portare ad Alessio. Qui sta perche' le barre e i fogli di F4B e F5B
   * leggano il margine da un posto solo; un test lo tiene uguale a quello della tab bar.
   */
  val CapsuleSideMargin: Dp = 14.dp

  /**
   * Dal fondo (sopra la barra di sistema) al vetro della capsula flottante e della tab bar: 16 dp, il valore di sempre
   * (`FluidFloatingTabBarDefaults.BottomMargin`). Apple ne misura 20,9 pt dal fondo dello schermo (R6, iOS 27,
   * +-0,7 pt), ma su Android sotto c'e' la barra dei gesti: i 16 dp si sommano a lei, e R6 li lascia invariati.
   */
  val CapsuleBottomMargin: Dp = 16.dp

  // --- Icone --------------------------------------------------------------------------------------------------

  /**
   * Quanto riquadro d'icona sta in un vetro: 0,6 del lato. La sagoma di un'icona occupa circa 0,75 del suo riquadro
   * da 24 (R6), quindi 0,6 dà una sagoma alta 0,44 del vetro: quella di Apple (0,44-0,48, alta). Prima era 0,5.
   */
  const val IconLensRatio: Float = 0.6f

  /**
   * Un'icona fuori da ogni vetro, barra o menu: 24 dp, il riquadro in cui sono disegnate le icone Material e
   * [dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons]. E' la taglia che `Icon` di Material dava gia'
   * senza modificatore: una [FluidIcon] fuori dai componenti non cambia misura.
   */
  val IconSize: Dp = 24.dp

  /** L'icona di un'azione della barra in alto ([FluidBarAction]): 28 dp, cioe' 0,6 del vetro da 46 arrotondato. */
  val BarIconSize: Dp = iconSizeIn(GlassButtonLens)

  /**
   * La freccia del tasto indietro: 25 dp. Mantiene la proporzione della freccia da 24 dp nel vetro da 44 di R6,
   * un po' sotto le altre icone perché ha una sagoma stretta e alta.
   */
  val BackIconSize: Dp = (GlassButtonLens.value * (24f / 44f)).roundToInt().dp

  /** L'icona di un'azione della capsula flottante ([FluidCapsuleAction]): 28 dp, come in barra. */
  val CapsuleIconSize: Dp = BarIconSize

  /**
   * L'icona di una scheda della tab bar: 28 dp (prima 24, quella che l'app scriveva). Le icone della tab bar di
   * iOS 27 hanno sagome di 21-31 pt (R6, alta); con le etichette a 11 sp l'icona torna a comandare la scheda, come
   * in Apple (icona sulla maiuscola dell'etichetta: 3,2 in Apple, 1,8 prima nell'engine).
   */
  val TabBarIconSize: Dp = 28.dp

  /**
   * L'icona di una voce di menu: 22 dp (prima 20); riga e testo invariati. In Apple le icone del menu sono quelle
   * della barra divise per 1,3 (R6): 28 / 1,3 è circa 22, misura che tiene il peso senza toccare la riga da 48.
   */
  val MenuIconSize: Dp = 22.dp

  /**
   * Il riquadro dell'icona che sta bene in un vetro di lato [lens]: [IconLensRatio] del lato, al dp intero.
   * 24, 28 e 32 dp per i vetri da 40, 46 e 54 dp ([FluidGlassSize]); un disco da 76 dp ne prende 46.
   */
  fun iconSizeIn(lens: Dp): Dp = (lens.value * IconLensRatio).roundToInt().dp

  // --- Tab bar ------------------------------------------------------------------------------------------------

  /**
   * L'altezza della tab bar flottante aperta: 62 dp (prima 60), i 62,6 pt di iOS 27 (R6, alta). E' un pavimento:
   * col testo ingrandito la barra cresce.
   */
  val TabBarHeight: Dp = 62.dp

  /**
   * L'etichetta di una scheda: 11 sp (prima 13). Apple ne usa 10 pt (R6, alta); sotto gli 11 l'italiano
   * («Aggiornamenti», «Impostazioni») non guadagna abbastanza da giustificare la lettura peggiore. Se in S2 si legge
   * male, torna a 12 sp qui (piano, F1B).
   */
  val TabBarLabelSize: TextUnit = 11.sp

  /** L'interlinea dell'etichetta a 11 sp: quella di `labelSmall` del tipo dell'engine, una riga sola. */
  val TabBarLabelLineHeight: TextUnit = 14.sp

  // --- Barra in alto ------------------------------------------------------------------------------------------

  /**
   * Quanto aria lascia il titolo agganciato oltre il vetro del tasto indietro: 24 dp, quelli di sempre (prima il
   * vetro stava a 8 + 44 = 52 dp dal bordo e il titolo a 76).
   */
  private val CompactTitleBreath: Dp = 24.dp

  /**
   * Il margine del titolo agganciato da ogni lato: 86 dp. Sono 16 dp fino al vetro, 46 di vetro e i 24 di
   * respiro. Un titolo lungo si tronca 8 dp prima per lato.
   */
  val CompactTitleInset: Dp = ScreenEdgeMargin + GlassButtonLens + CompactTitleBreath
}

/**
 * Il margine fra il bordo dello schermo e il **riquadro da 48 dp** del primo tasto di una barra, perche' il suo vetro
 * stia a [FluidMetrics.ScreenEdgeMargin]: 16 - 1 = 15 dp. Il riquadro e' quello che il layout posa, il vetro sta al
 * suo centro.
 */
internal val FluidBarEdgeInset: Dp =
  FluidMetrics.ScreenEdgeMargin - (FluidMetrics.TouchTarget - FluidMetrics.GlassButtonLens) / 2

/**
 * Lo spazio fra i riquadri da 48 dp di due tasti vicini, perche' fra i due vetri ci siano
 * [FluidMetrics.GlassSpacing]: 8 - 2 = 6 dp.
 */
internal val FluidBarActionGap: Dp =
  FluidMetrics.GlassSpacing - (FluidMetrics.TouchTarget - FluidMetrics.GlassButtonLens)
