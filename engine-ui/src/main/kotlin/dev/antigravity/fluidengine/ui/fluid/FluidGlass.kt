/*
 * Il vetro come configurazione (piano «iOS in Android», 3.13 e F2.2): il `Glass` di SwiftUI. Chi scrive un'app dice
 * quale vetro vuole (di serie, limpido, nessuno), se è tinto e se reagisce al dito; come si disegna lo decide l'engine.
 *
 * Qui c'è il **tipo**. Il modificatore che lo posa su un elemento, `Modifier.fluidGlass(glass, shape, backdrop)`, nasce
 * in F3.2 col contenitore; fino ad allora il tipo si traduce nel materiale con le funzioni interne in fondo al file.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant

/**
 * Quale vetro: la configurazione del materiale nuovo, come `Glass` in SwiftUI.
 *
 *     FluidGlass.Regular                        // il vetro di serie: smerigliato, adattivo
 *     FluidGlass.Regular.tint(accent)           // tinto: la tonalità resta, la luminosità segue lo sfondo
 *     FluidGlass.Clear.interactive()            // limpido, e reagisce al dito
 *     FluidGlass.Identity                       // nessun vetro, senza cambiare l'albero
 *
 * Le tre varianti non si mescolano (Apple, WWDC25 219):
 *  - [Regular] è il vetro di quasi tutto: leggibile su qualunque sfondo e a qualunque taglia, perché si adatta a ciò
 *    che ha dietro (velo, ombra, lato chiaro o scuro). Va bene sotto il testo;
 *  - [Clear] è **più trasparente** e **non si adatta**: nessuna smerigliatura, e uno scurimento del 35% sotto il vetro
 *    (HIG) che tiene leggibile ciò che gli sta sopra. Va usato solo se tutte e tre: sta sopra contenuto ricco (foto,
 *    video), quel contenuto regge lo scurimento, e ciò che sta sul vetro è chiaro e marcato;
 *  - [Identity] è il vetro spento: il contenuto resta come senza vetro. Serve ad accendere e spegnere il vetro senza
 *    cambiare la struttura della schermata (e quindi senza perdere stato né animazioni).
 *
 * Il vetro di prima ([FluidGlassModel.Classic], e tutto sotto Android 13) non ha varianti: lì [Clear] è [Regular].
 *
 * Uguaglianza strutturale: un'istanza ricreata a ogni ricomposizione non invalida niente.
 */
@Immutable
class FluidGlass private constructor(
  internal val variant: Variant,
  /**
   * La tinta, o [Color.Unspecified] se il vetro non è tinto. È la tinta di Apple: la **tonalità** resta, la
   * **luminosità** segue lo sfondo (su bianco piena, su nero al 58%: R4 A7), e l'alfa del colore è quanto copre. La
   * tinta è per l'azione principale, non di serie (sezione 2 del piano).
   */
  val tint: Color,
  /** Se il vetro reagisce al dito: si gonfia, si illumina sotto il dito, la lente si approfondisce. */
  val isInteractive: Boolean,
) {

  /** Le tre varianti. Interne: un `when` dell'app su un tipo che cresce si romperebbe a ogni valore nuovo. */
  internal enum class Variant { Regular, Clear, Identity }

  /** Vero per [Clear]. */
  val isClear: Boolean get() = variant == Variant.Clear

  /** Vero per [Identity]: il vetro non c'è. */
  val isIdentity: Boolean get() = variant == Variant.Identity

  /** Lo stesso vetro tinto di [color]; `null` o [Color.Unspecified] toglie la tinta. Su [Identity] non cambia niente. */
  fun tint(color: Color?): FluidGlass {
    val next = color?.takeIf { it.isSpecified } ?: Color.Unspecified
    return if (variant == Variant.Identity || next == tint) this else FluidGlass(variant, next, isInteractive)
  }

  /** Lo stesso vetro che reagisce al dito (o no, con [enabled] falso). Su [Identity] non cambia niente. */
  fun interactive(enabled: Boolean = true): FluidGlass =
    if (variant == Variant.Identity || enabled == isInteractive) this else FluidGlass(variant, tint, enabled)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FluidGlass) return false
    return variant == other.variant && tint == other.tint && isInteractive == other.isInteractive
  }

  override fun hashCode(): Int {
    var result = variant.hashCode()
    result = 31 * result + tint.hashCode()
    result = 31 * result + isInteractive.hashCode()
    return result
  }

  override fun toString(): String = buildString {
    append("FluidGlass.").append(variant.name)
    if (tint.isSpecified) append(".tint(").append(tint).append(')')
    if (isInteractive) append(".interactive()")
  }

  companion object {
    /** Il vetro di serie: smerigliato e adattivo. */
    val Regular: FluidGlass = FluidGlass(Variant.Regular, Color.Unspecified, isInteractive = false)

    /** Il vetro limpido: nessuna smerigliatura né adattività, scurimento del 35% sotto. Vedi [FluidGlass]. */
    val Clear: FluidGlass = FluidGlass(Variant.Clear, Color.Unspecified, isInteractive = false)

    /** Nessun vetro: il contenuto com'è, nella stessa struttura. */
    val Identity: FluidGlass = FluidGlass(Variant.Identity, Color.Unspecified, isInteractive = false)
  }
}

// ------------------------------------------------------------------------------------------------------------
// Dal tipo al materiale
// ------------------------------------------------------------------------------------------------------------

/** La variante della ricetta del materiale nuovo. [FluidGlass.Identity] è [GlassLiquidVariant.Regular] a intensità zero. */
internal val FluidGlass.liquidVariant: GlassLiquidVariant
  get() = if (variant == FluidGlass.Variant.Clear) GlassLiquidVariant.Clear else GlassLiquidVariant.Regular

/** Quanto vetro c'è: zero per [FluidGlass.Identity], uno altrimenti. Si moltiplica all'`intensity` della superficie. */
internal val FluidGlass.presence: Float
  get() = if (variant == FluidGlass.Variant.Identity) 0f else 1f

/**
 * La tinta di Apple come la ricetta la vuole (`tint` di `glassRecipe`, cioè `GlassTintMode.Colored`), o
 * [Color.Unspecified].
 */
internal val FluidGlass.recipeTint: Color
  get() = if (variant == FluidGlass.Variant.Identity) Color.Unspecified else tint
