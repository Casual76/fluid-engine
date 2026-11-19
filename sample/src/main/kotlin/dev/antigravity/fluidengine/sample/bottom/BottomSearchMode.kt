package dev.antigravity.fluidengine.sample.bottom

import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchExpansion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPurpose
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec

/**
 * Le sedi della ricerca che la galleria prova sulla pagina «Raccolta», una alla volta.
 *
 * Una sola dichiarazione cambia di sede: così si confrontano i viaggi sugli stessi vicini, e chi riprende sul tablet
 * cambia una riga invece di cercare un'altra pagina. [Bottom] tiene il titolo della riga che le riprese di F4B.2
 * toccano («Mostra la ricerca in basso»).
 */
internal enum class BottomSearchMode(val title: String, val detail: String) {
  Bottom("Mostra la ricerca in basso", "In basso con gli ambiti in linea; attiva sale sopra la tastiera"),
  Automatic("Ricerca automatica", "In basso sul telefono, in alto da 600 dp; con più di due vicini parte dal tasto"),
  Minimized("Ricerca minimizzata", "Sempre un tasto a riposo: si apre in campo e torna tasto"),
  Top("Ricerca in alto", "Dal tasto della barra in alto; il campo resta in cima e ingloba i vicini"),
  TopToKeyboard("Ricerca in alto fino alla tastiera", "Si apre in alto e viaggia sopra la tastiera"),
  Input("Campo di inserimento", "Una nota invece di una ricerca: matita al posto della lente, «Fine» sulla tastiera"),
  ;

  /** Il titolo della riga quando la sede è quella in uso: la prima conserva la frase delle riprese di F4B.2. */
  val activeTitle: String get() = if (this == Bottom) "Nascondi la ricerca" else title

  /**
   * La dichiarazione per questa sede. Ambiti e suggerimenti valgono per la ricerca; l'inserimento non li ha, perché
   * scrive e non sceglie.
   */
  fun spec(
    query: String,
    onQueryChange: (String) -> Unit,
    scopes: @Composable () -> Unit,
    suggestions: @Composable () -> Unit,
  ): FluidSearchSpec = when (this) {
    Bottom -> FluidSearchSpec(query, onQueryChange, placement = FluidSearchPlacement.BottomBar, prompt = "Cerca nelle foto",
      scopes = scopes, suggestions = suggestions)
    Automatic -> FluidSearchSpec(query, onQueryChange, placement = FluidSearchPlacement.Automatic, prompt = "Cerca nelle foto",
      scopes = scopes, suggestions = suggestions)
    Minimized -> FluidSearchSpec(query, onQueryChange, placement = FluidSearchPlacement.Automatic,
      behavior = FluidSearchBehavior.CollapseToButton, prompt = "Cerca nelle foto", scopes = scopes, suggestions = suggestions)
    Top -> FluidSearchSpec(query, onQueryChange, placement = FluidSearchPlacement.TopBar,
      behavior = FluidSearchBehavior.CollapseToButton, expansion = FluidSearchExpansion.StayAtTop, prompt = "Cerca nelle foto",
      scopes = scopes, suggestions = suggestions)
    TopToKeyboard -> FluidSearchSpec(query, onQueryChange, placement = FluidSearchPlacement.TopBar,
      behavior = FluidSearchBehavior.CollapseToButton, expansion = FluidSearchExpansion.AboveKeyboard,
      prompt = "Cerca nelle foto", scopes = scopes, suggestions = suggestions)
    Input -> FluidSearchSpec(query, onQueryChange, placement = FluidSearchPlacement.BottomBar, prompt = "Scrivi una nota",
      onSearch = { onQueryChange("") }, purpose = FluidSearchPurpose.Input)
  }
}
