package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * La taglia che una [FluidIcon] senza `size` prende dal posto in cui sta.
 *
 * **Perche' esiste.** Le icone delle app erano piccole (R6: 0,37 del tasto contro 0,44-0,48 di Apple) perche' ogni
 * app scriveva la sua taglia a mano, di solito quella di un esempio Material, e il tasto non aveva modo di dirle
 * quanto spazio c'era. Qui lo dice: i tasti di vetro forniscono 0,6 del loro vetro (26 dp nel tasto da 44,
 * [FluidMetrics.iconSizeIn]), la capsula flottante 26, la tab bar 28, la voce di menu 22, l'icona davanti
 * all'etichetta di un [FluidGlassButton] la sua. Chi scrive un'app mette `FluidIcon(FluidIcons.Share, …)` dentro
 * il tasto e la taglia giusta arriva da sola, anche quando l'engine la cambiera'.
 *
 * Fuori da tutto vale [FluidMetrics.IconSize], 24 dp: la misura che `Icon` di Material dava gia' a un'icona senza
 * modificatore, cosi' passare da `Icon` a [FluidIcon] fuori dai componenti non cambia niente.
 *
 * Un'app la fornisce a sua volta per un suo contenitore (`CompositionLocalProvider(LocalFluidIconSize provides 20.dp)`),
 * per esempio la riga di una lista con icone piu' piccole.
 */
val LocalFluidIconSize: ProvidableCompositionLocal<Dp> = compositionLocalOf { FluidMetrics.IconSize }

/**
 * Un'icona che prende **la taglia dal posto in cui sta** ([LocalFluidIconSize]) e **l'inchiostro dal contenuto**
 * (`LocalContentColor`, come `Icon` di Material).
 *
 * **Perche' esiste.** Le icone delle barre si leggevano peggio di quelle di Apple per tre ragioni misurate (R6,
 * `docs/liquid-glass/ricerca/icone-e-misure.md`): erano piu' piccole del 20-30%, nel colore d'accento invece che
 * nell'inchiostro del testo, e i tasti stavano a 8 dp dal bordo. Il tratto no: era gia' quello di Apple. La cura
 * della taglia e' questa: l'icona non sceglie la sua misura, la riceve dal tasto, dalla capsula, dalla tab bar o dal
 * menu che la contiene ([FluidMetrics]). Senza, ogni app avrebbe dovuto ricopiare i numeri nuovi a mano, e alla
 * prossima correzione sarebbero tornati indietro una app alla volta.
 *
 * **Quale icona.** Qualunque `ImageVector`: quelle di [dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons] (al
 * tratto, peso 500: il set che l'engine raccomanda, scelto da Alessio il 2026-11-10), ma anche un'icona Material di
 * oggi o un vettore dell'app. Un'app che passa ancora `Icons.Rounded.Share` funziona come prima, alla taglia nuova;
 * per l'aspetto di Apple cambia il vettore (`tools/icons/corrispondenza.md` dice con quale).
 *
 * **Quale colore.** Di serie l'inchiostro del contenuto: nei componenti dell'engine e' l'inchiostro del tema (quello
 * del testo), nero sul chiaro e bianco sullo scuro, come le icone delle barre di Apple. Un'icona colorata resta
 * possibile e non e' di serie: si passa [tint] (l'accento sull'azione principale, `error` su quella distruttiva).
 * `Color.Unspecified` disegna il vettore con i suoi colori, come in `Icon`.
 *
 * @param imageVector l'icona, in un riquadro da 24 come quelle Material.
 * @param contentDescription cio' che TalkBack dice; null per un'icona che accompagna un testo o un tasto che ha gia'
 *   il suo nome.
 * @param modifier una taglia scritta qui (`Modifier.size(…)`) vince su [size]: e' il modo in cui il codice di prima,
 *   passato da `Icon` a [FluidIcon] senza toccare il modificatore, resta alla sua misura.
 * @param tint il colore; di serie `LocalContentColor`.
 * @param size il lato del riquadro; di serie quello del posto ([LocalFluidIconSize]). Scriverlo fissa la taglia e
 *   l'icona non segue piu' il contenitore.
 */
@Composable
fun FluidIcon(
  imageVector: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
  size: Dp = LocalFluidIconSize.current,
) {
  Icon(
    imageVector = imageVector,
    contentDescription = contentDescription,
    // Prima il modificatore del chiamante, poi la taglia: una `size` gia' scritta fuori vince (il vincolo esterno
    // e' quello che resta), e chi passa da `Icon` a `FluidIcon` col suo modificatore non cambia misura.
    modifier = modifier.size(size),
    tint = tint,
  )
}

/**
 * L'inchiostro delle icone che l'engine disegna nelle sue barre, nei tasti e nei menu: quello del testo del tema
 * (`onSurface`, #121214 sul chiaro e #F2F2F5 sullo scuro), al posto dell'accento di prima (R6: 15-17:1 di contrasto
 * sul vetro contro 3,5-6,7:1). Monocromo e non adattivo: l'inchiostro che segue cio' che il vetro ha dietro arriva
 * con F2.3, e quando arriva cambia qui.
 */
@Composable
@ReadOnlyComposable
internal fun fluidChromeInk(): Color = MaterialTheme.colorScheme.onSurface
