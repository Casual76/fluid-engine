package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dove sta una [FluidFloatingCapsule] rispetto alla barra di navigazione flottante.
 *
 * Una barra di selezione, un mini player, le azioni di una foto: sono la stessa cosa — una capsula di
 * vetro con poche azioni che sta in fondo allo schermo — e ogni app se la posava a mano, ognuna con
 * i suoi margini. Il risultato era una capsula che con la barra di navigazione sotto stava dieci dp
 * piu' su o piu' giu' di quella dell'altra schermata, e che sul telefono coi tre tasti finiva dietro
 * la barra di sistema. I numeri sono quelli della barra ([FluidFloatingTabBarDefaults]), cosi' la
 * capsula e la barra non possono disallinearsi.
 */
enum class FluidFloatingCapsulePlacement {
  /**
   * Nessun margine: la posa chi la usa (dentro una colonna, sopra una mappa, accanto a un altro
   * pezzo). E' l'unico caso in cui la capsula non conosce lo schermo.
   */
  Free,

  /**
   * Al posto della barra di navigazione: stessi margini e stessa altezza, quindi la pagina sotto non
   * deve cambiare lo spazio che si e' lasciata ([FluidFloatingTabBarDefaults.ContentInset]).
   */
  ReplacingTabBar,

  /**
   * Sopra la barra di navigazione, a [FluidFloatingTabBarDefaults.Spacing] da lei: la barra resta
   * dov'e' e la pagina si lascia lo spazio di tutte e due ([FluidFloatingCapsuleDefaults.contentInset]).
   */
  AboveTabBar,
}

/** I numeri della [FluidFloatingCapsule], tutti insieme perche' la barra di selezione e il lettore li condividano. */
object FluidFloatingCapsuleDefaults {

  /**
   * L'altezza minima: quella della barra di navigazione aperta ([FluidFloatingTabBarDefaults.OpenHeight]).
   * Una capsula che prende il posto della barra e' alta quanto lei, e con un'azione da 48 dp piu' sei
   * dp di margine sopra e sotto e' esattamente 60: non serve forzarla.
   */
  val Height: Dp = FluidFloatingTabBarDefaults.OpenHeight

  /** Il lato di un'azione ([FluidCapsuleAction]): il bersaglio minimo di un dito. */
  val ActionSize: Dp = 48.dp

  /** Fra un pezzo e il successivo: le azioni sono vicine, non sparse. */
  val Spacing: Dp = 4.dp

  /** Il margine dentro il vetro: sei dp, quelli della barra di navigazione ([FluidFloatingTabBarDefaults.sizes]) piu' due. */
  val ContentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 6.dp)

  /**
   * La capsula a tutta larghezza non attraversa un tablet: resta della misura di un telefono grande,
   * centrata. Lo stesso tetto che la barra di Fluidweather si dava a mano.
   */
  val MaxWidth: Dp = 560.dp

  /**
   * Da quanto sta staccato il bordo basso della capsula dal bordo basso dello schermo, **senza** la
   * barra di sistema (quella la aggiunge [fluidCapsuleInsets]). Puro, perche' e' il numero che decide
   * se una capsula copre la barra o la sfiora.
   */
  fun bottomOffset(placement: FluidFloatingCapsulePlacement): Dp = when (placement) {
    FluidFloatingCapsulePlacement.Free -> 0.dp
    FluidFloatingCapsulePlacement.ReplacingTabBar -> FluidFloatingTabBarDefaults.BottomMargin
    // La barra occupa ContentInset (la sua altezza piu' il margine basso); sopra ci va lo spazio fra i due.
    FluidFloatingCapsulePlacement.AboveTabBar ->
      FluidFloatingTabBarDefaults.ContentInset + FluidFloatingTabBarDefaults.Spacing
  }

  /**
   * Lo spazio che la pagina deve lasciarsi in fondo perche' la capsula non copra il suo contenuto,
   * senza la barra di sistema. [Free] non ne chiede: non si sa dove stia.
   *
   * [capsuleHeight] e' l'altezza che la capsula ha davvero: [Height] se non e' piu' alta di cosi'.
   */
  fun contentInset(placement: FluidFloatingCapsulePlacement, capsuleHeight: Dp = Height): Dp = when (placement) {
    FluidFloatingCapsulePlacement.Free -> 0.dp
    FluidFloatingCapsulePlacement.ReplacingTabBar ->
      FluidFloatingTabBarDefaults.BottomMargin + maxOf(capsuleHeight, FluidFloatingTabBarDefaults.OpenHeight)
    FluidFloatingCapsulePlacement.AboveTabBar ->
      FluidFloatingTabBarDefaults.contentInsetWithAccessory(capsuleHeight)
  }

  /**
   * Come entra: sale da sotto con la molla del chrome (critica, niente rimbalzo — una barra che
   * rimbalza e' il segno piu' rumoroso di "non e' premium") e si accende. Con le animazioni ridotte
   * non si muove: resta la dissolvenza.
   */
  fun enter(reducedMotion: Boolean = false): EnterTransition =
    if (reducedMotion) {
      fadeIn(FluidMotion.fadeIn(160))
    } else {
      slideInVertically(FluidMotion.intOffset(FluidMotion.DampingChrome, FluidMotion.ResponseStandard)) { it } +
        fadeIn(FluidMotion.fadeIn(180))
    }

  /** Come esce: piu' svelta di come entra (chi la chiude non aspetta), stessa direzione. */
  fun exit(reducedMotion: Boolean = false): ExitTransition =
    if (reducedMotion) {
      fadeOut(FluidMotion.fadeOut(120))
    } else {
      slideOutVertically(FluidMotion.intOffset(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy)) { it } +
        fadeOut(FluidMotion.fadeOut(140))
    }
}

/**
 * I margini di una capsula flottante in fondo allo schermo: la barra di sistema, i margini laterali
 * della barra di navigazione e il suo margine basso, piu' — per [FluidFloatingCapsulePlacement.AboveTabBar]
 * — lo spazio della barra stessa.
 *
 * E' pubblico perche' non e' solo della capsula: un campo di ricerca che prende il posto della barra
 * ([FluidSearchField]) o qualunque altro pezzo di vetro che galleggia in quel posto si posa con
 * questo, non con tre `padding` a mano.
 *
 * @param avoidKeyboard sale con la tastiera (`imePadding`, dopo la barra di sistema: l'inset della
 *   tastiera comprende gia' quello della barra, e dopo la barra non viene contato due volte). Serve a
 *   chi contiene un campo di testo; per [FluidFloatingCapsulePlacement.AboveTabBar] non ha senso,
 *   perche' la barra sotto la tastiera non si vede.
 */
fun Modifier.fluidCapsuleInsets(
  placement: FluidFloatingCapsulePlacement,
  avoidKeyboard: Boolean = false,
): Modifier {
  if (placement == FluidFloatingCapsulePlacement.Free) {
    return if (avoidKeyboard) this.imePadding() else this
  }
  return this
    .navigationBarsPadding()
    .then(if (avoidKeyboard) Modifier.imePadding() else Modifier)
    .padding(
      start = FluidFloatingTabBarDefaults.HorizontalMargin,
      end = FluidFloatingTabBarDefaults.HorizontalMargin,
      bottom = FluidFloatingCapsuleDefaults.bottomOffset(placement),
    )
}

/**
 * Una capsula di vetro con delle azioni dentro, che galleggia sopra la pagina: la barra di una
 * selezione multipla, un mini player, i comandi di una foto, un avviso con un tasto.
 *
 * Quattro app se l'erano scritta da sole (la barra azioni di MyCloud, la barra di Fluidweather, il
 * lettore di Pampa notes, le capsule di Fluid-transit), e ognuna aveva sbagliato qualcosa di
 * diverso: un bersaglio da 40 dp, un margine che non teneva conto della barra di sistema, la capsula
 * che lasciava passare i tocchi al contenuto sotto. Qui e' una cosa sola.
 *
 * **Una lastra sola.** Il vetro sta sulla capsula (`glassSurface`, ruolo `Floating`, la stessa
 * pellicola della barra di navigazione, quindi le due non si distinguono per materiale), mai sulle
 * azioni: [FluidCapsuleAction] e' un'icona piatta che cresce sotto il dito, e ogni lente in piu' e'
 * un layer. Per un'azione che deve staccarsi dal resto — il "Fatto" di una selezione, il play di un
 * lettore — si mette nelle fessure un tasto di vetro vero ([FluidGlassIconButton],
 * [FluidGlassButton]): dentro la capsula campiona il suo materiale (vetro su vetro, dalla 2.13.0)
 * senza passare niente.
 *
 * **Si posa da sola.** Con [placement] diverso da [FluidFloatingCapsulePlacement.Free] porta i
 * margini della barra di navigazione ([fluidCapsuleInsets]); la pagina sotto si lascia
 * [FluidFloatingCapsuleDefaults.contentInset]. Va allineata dal chiamante (`Modifier.align(
 * Alignment.BottomCenter)` dentro un `Box`).
 *
 * **Tiene i tocchi.** Un tocco su un punto della capsula che non e' un'azione non arriva alla card
 * che sta sotto: senza, toccare fra due icone apriva la foto dietro.
 *
 * @param backdrop cio' che la capsula rifrange: la pagina (`FluidScreen.overlay` lo passa). Lo
 *   ricevono anche i tasti di vetro nelle fessure, come `LocalGlassBackdrop`.
 * @param visible entra e esce con [FluidFloatingCapsuleDefaults.enter] e `exit`, senza comparse
 *   secche; falso non lascia niente in composizione. Il default e' vero: una capsula sempre in scena.
 * @param fillWidth a tutta larghezza, fino a [FluidFloatingCapsuleDefaults.MaxWidth] e centrata: il
 *   [content] usa `Modifier.weight(1f)` per prendere lo spazio che resta (una pillola al centro fra due
 *   tasti). Falso: larga quanto serve ai pezzi.
 * @param onPhoto sopra una foto a tutta pagina la pellicola e' quella di `floatingTintOnPhoto`: la
 *   pellicola chiara della barra, sopra una copertina, diventerebbe l'oggetto piu' luminoso dello schermo.
 * @param label come TalkBack annuncia la capsula quando compare («3 selezionate»): e' il titolo del
 *   pannello. Null: non annuncia niente.
 * @param leading in testa, prima del [content]: un tasto che chiude, un segno.
 * @param trailing in coda, dopo il [content]: l'azione principale.
 */
@Composable
fun FluidFloatingCapsule(
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  visible: Boolean = true,
  placement: FluidFloatingCapsulePlacement = FluidFloatingCapsulePlacement.Free,
  fillWidth: Boolean = false,
  onPhoto: Boolean = false,
  avoidKeyboard: Boolean = false,
  minHeight: Dp = FluidFloatingCapsuleDefaults.Height,
  contentPadding: PaddingValues = FluidFloatingCapsuleDefaults.ContentPadding,
  label: String? = null,
  leading: (@Composable RowScope.() -> Unit)? = null,
  trailing: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable RowScope.() -> Unit,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = FluidFloatingCapsuleDefaults.enter(reducedMotion),
    exit = FluidFloatingCapsuleDefaults.exit(reducedMotion),
  ) {
    // I margini stanno **dentro** l'animazione: la capsula sale da sotto il bordo dello schermo, non
    // da dieci dp sotto la sua posizione.
    Box(
      modifier = Modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .fluidCapsuleInsets(placement, avoidKeyboard),
      contentAlignment = Alignment.Center,
    ) {
      Row(
        modifier = Modifier
          .then(if (fillWidth) Modifier.fillMaxWidth().widthIn(max = FluidFloatingCapsuleDefaults.MaxWidth) else Modifier)
          .heightIn(min = minHeight)
          // Tiene i tocchi che nessuna azione ha preso, senza toccare i trascinamenti: una pagina che
          // parte a scorrere da qui scorre lo stesso.
          .pointerInput(Unit) { detectTapGestures { } }
          .fluidFloatingGlass(backdrop = backdrop, onPhoto = onPhoto)
          .padding(contentPadding)
          .semantics {
            isTraversalGroup = true
            if (label != null) paneTitle = label
          },
        horizontalArrangement = Arrangement.spacedBy(FluidFloatingCapsuleDefaults.Spacing),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CompositionLocalProvider(
          LocalGlassBackdrop provides backdrop,
          LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
          leading?.invoke(this)
          content()
          trailing?.invoke(this)
        }
      }
    }
  }
}

/**
 * Un'azione dentro una [FluidFloatingCapsule]: un'icona, la sua descrizione e il bersaglio da 48 dp.
 *
 * Piatta di proposito: il vetro e' la capsula, e un'icona che cresce sotto il dito (la ricetta del
 * tasto, `FluidPressFeedback.Expand`) dice gia' "sono io" senza una lente e un'ombra per ognuna. Il
 * colore e' l'accento, come per le azioni della barra superiore ([FluidBarAction]); [destructive] usa
 * `error`. Spenta, resta al suo posto ma velata: una capsula che cambia larghezza quando un'azione
 * non e' disponibile fa saltare quello che sta accanto.
 *
 * @param size il lato. Sotto i 48 dp il layout e' piu' stretto ma Compose estende comunque l'area
 *   toccabile a 48: serve a una capsula con quattro azioni su un telefono stretto.
 * @param tint il colore dell'icona; di serie l'accento.
 * @param grip di chi e' il dito quando la pagina sotto vuole scorrere, come per [fluidPressable]: di
 *   serie dell'azione ([FluidPressGrip.HoldsFinger]); [FluidPressGrip.YieldsToScroll] se la capsula
 *   sta sopra una lista o un pager che deve partire anche da li'. Dalla 3.0.
 * @param selected lo stato di un'azione che si **accende e si spegne**: il grassetto di una barra di
 *   formattazione sopra la tastiera, un filtro. Null (di serie) e' un'azione semplice, com'e' sempre
 *   stata. Con un valore l'azione e' un **interruttore**: ha il ruolo `Switch` e lo stato che TalkBack
 *   dice (acceso/spento, nella lingua di sistema), e quando e' acceso ha un disco dell'accento dietro
 *   l'icona — il segno **non e' solo di colore**: un disco pieno e un'icona sola si distinguono anche
 *   in scala di grigi, e a chi non vede i colori. Il disco sta dentro il bersaglio di 48 dp e cresce
 *   con l'icona sotto il dito.
 */
@Composable
fun FluidCapsuleAction(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  destructive: Boolean = false,
  tint: Color = Color.Unspecified,
  onLongClick: (() -> Unit)? = null,
  size: Dp = FluidFloatingCapsuleDefaults.ActionSize,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  selected: Boolean? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val color = when {
    destructive -> scheme.error
    tint != Color.Unspecified -> tint
    else -> scheme.primary
  }
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val discAlpha by animateFloatAsState(
    targetValue = if (selected == true) CapsuleActionActiveDiscAlpha else 0f,
    animationSpec = if (reducedMotion) snap() else FluidMotion.flick(),
    label = "fluid capsule action selected",
  )
  Box(
    modifier = modifier
      .size(size)
      .alpha(if (enabled) 1f else CapsuleActionDisabledAlpha)
      // Prima di tutto il resto: la crescita si applica solo a cio' che sta dopo (regola della pressione).
      .fluidPressable(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        role = if (selected != null) Role.Switch else Role.Button,
        focusShape = FluidCapsuleShape,
        feedback = FluidPressFeedback.Expand,
        grip = grip,
      )
      .then(
        if (selected != null) {
          Modifier.semantics { toggleableState = ToggleableState(selected) }
        } else {
          Modifier
        },
      )
      .then(
        // Il disco: dopo la pressione, cosi' cresce con l'icona, e dentro il bersaglio (3 dp d'aria).
        if (discAlpha > 0f) {
          Modifier
            .padding(CapsuleActionDiscInset)
            .clip(FluidCapsuleShape)
            .background(color.copy(alpha = discAlpha))
        } else {
          Modifier
        },
      ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = color,
      modifier = Modifier.size(24.dp),
    )
  }
}

/** Quanto e' pieno il disco di un'azione accesa: il 16% dell'accento, come il velo di una riga scelta nella barra laterale. */
private const val CapsuleActionActiveDiscAlpha = 0.16f

/** L'aria fra il bordo del bersaglio e il disco di un'azione accesa. */
private val CapsuleActionDiscInset = 3.dp

/** Un controllo spento resta leggibile ma si capisce che non risponde: lo stesso 0,38 dei tasti di vetro. */
private const val CapsuleActionDisabledAlpha = 0.38f

/**
 * Il vetro di una capsula flottante: ruolo `Floating` con la pellicola della barra di navigazione.
 *
 * Una sola ricetta per la capsula di azioni e per il campo di ricerca ([FluidSearchField]), cosi' due
 * pillole nella stessa fila sono lo stesso materiale e non due grigi diversi. Mai ottica scritta a
 * mano: la regola della taglia ([glassSizedOptics]) la applica il vetro da solo.
 */
@Composable
internal fun Modifier.fluidFloatingGlass(backdrop: GlassBackdropState, onPhoto: Boolean): Modifier =
  glassSurface(
    state = backdrop,
    tint = if (onPhoto) GlassDefaults.floatingTintOnPhoto() else GlassDefaults.floatingTint(),
    shape = FluidCapsuleShape,
    role = GlassRole.Floating,
  )
