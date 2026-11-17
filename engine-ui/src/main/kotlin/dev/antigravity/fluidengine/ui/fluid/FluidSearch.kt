package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** La ricerca appartiene alla barra: Automatic sceglierà basso su telefono e alto su tablet (F4B.2). */
enum class FluidSearchPlacement { Automatic, TopBar, BottomBar, Principal }

/** L'app può chiedere il campo sempre visibile o il tasto iniziale, senza animarlo da sola. */
enum class FluidSearchBehavior { Automatic, AlwaysVisible, CollapseToButton }

/** Distingue ricerca e inserimento generico mantenendo un solo contratto per la DSL (piano F4B). */
enum class FluidSearchPurpose { Search, Input }

/** In alto il campo può inglobare i vicini oppure raggiungere la tastiera; il viaggio nasce in F4B.2. */
enum class FluidSearchExpansion { Automatic, StayAtTop, AboveKeyboard }

/**
 * Dichiarazione del campo, condivisa da schermata e toolbar: F4A.2 la posa, F4B.2 ne possiede il comportamento.
 *
 * Nessuna tastiera, stato interno o animazione viene avviata da questo tipo. I primi sei parametri seguono
 * il contratto del piano; i campi successivi permettono suggerimenti, ambiti in linea e inserimento generico
 * senza un secondo metodo nella DSL. Con [purpose] Input, [onSearch] è la conferma dell'inserimento.
 *
 * @property query il testo posseduto dall'app; il componente non ne conserva una copia privata.
 * @property onQueryChange restituisce le modifiche all'app.
 * @property placement la posizione di riposo richiesta.
 * @property behavior se partire dal campo o dal tasto.
 * @property prompt il segnaposto; null usa le parole del campo dell'engine.
 * @property onSearch la conferma della ricerca o dell'inserimento.
 * @property suggestions il contenuto dei suggerimenti, disegnato dal futuro slot unico.
 * @property scopes gli ambiti, sulla riga del campo anziché su una seconda barra.
 * @property purpose ricerca oppure inserimento generico.
 * @property expansion dove va il campo attivo; Automatic delega al componente.
 * @property active lo stato di apertura posseduto dall'app.
 * @property onActiveChange la richiesta di apertura/chiusura, senza cambiare stato nel modello.
 */
@Immutable
data class FluidSearchSpec(
  val query: String,
  val onQueryChange: (String) -> Unit,
  val placement: FluidSearchPlacement = FluidSearchPlacement.Automatic,
  val behavior: FluidSearchBehavior = FluidSearchBehavior.Automatic,
  val prompt: String? = null,
  val onSearch: (() -> Unit)? = null,
  val suggestions: (@Composable () -> Unit)? = null,
  val scopes: (@Composable () -> Unit)? = null,
  val purpose: FluidSearchPurpose = FluidSearchPurpose.Search,
  val expansion: FluidSearchExpansion = FluidSearchExpansion.Automatic,
  val active: Boolean = false,
  val onActiveChange: ((Boolean) -> Unit)? = null,
)
