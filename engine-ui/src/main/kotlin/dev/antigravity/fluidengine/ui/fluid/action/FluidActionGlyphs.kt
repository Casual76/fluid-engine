package dev.antigravity.fluidengine.ui.fluid.action

/**
 * I glifi animati dei preset dei tasti con esito: **il file di F6B.3b**. F6B.3a lo lascia vuoto, come punto d'innesto:
 * finché [of] non restituisce niente, ogni preset cambia faccia con lo scambio sfocato di R7 ([FluidActionSwapGlyph]).
 *
 * F6B.3b lo riscrive tutto (l'aeroplanino che «si carica» e parte, la spunta che si disegna con la maschera lungo il
 * tratto, la freccia che cade nell'anello, il cuore che si riempie col rimbalzo) senza toccare `FluidActionButton.kt` né
 * `FluidActionState.kt`: un [FluidActionGlyph] per [FluidActionKind], con le sue varianti per il movimento ridotto.
 */
internal object FluidActionGlyphs {
  /** Il glifo animato di [kind], o null: allora lo scambio sfocato di R7. */
  fun of(kind: FluidActionKind): FluidActionGlyph? = null
}
