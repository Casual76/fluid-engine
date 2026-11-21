package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Forum
import dev.antigravity.fluidengine.ui.fluid.icons.Search

/** La ricerca appartiene alla barra: Automatic sceglierà basso su telefono e alto su tablet (F4B.2). */
enum class FluidSearchPlacement { Automatic, TopBar, BottomBar, Principal }

/** L'app può chiedere il campo sempre visibile o il tasto iniziale, senza animarlo da sola. */
enum class FluidSearchBehavior { Automatic, AlwaysVisible, CollapseToButton }

/** Distingue ricerca e inserimento generico mantenendo un solo contratto per la DSL (piano F4B). */
enum class FluidSearchPurpose { Search, Input }

/** In alto il campo può inglobare i vicini oppure raggiungere la tastiera; il viaggio nasce in F4B.2. */
enum class FluidSearchExpansion { Automatic, StayAtTop, AboveKeyboard }

/**
 * Che cosa fa il campo dopo l'invio (F6B.6), cioè dopo aver chiamato [FluidSearchSpec.onSearch]: il tasto della
 * tastiera o il tasto «Invia» del campo.
 *
 * Un tipo a sé e non un valore nuovo di un enum di prima (piano 3.11: un valore nuovo rompe i `when` esaustivi delle app).
 */
enum class FluidSearchSubmit {
  /** Conferma e chiude: la tastiera scende e il campo torna a riposo, con la parola (la ricerca, la nota). */
  Close,

  /** Conferma e resta aperto, con la tastiera: il messaggio appena inviato, il risultato successivo di «trova». */
  StayOpen,
}

/**
 * Il tasto a destra dentro il campo aperto (F6B.6).
 *
 * Il tasto è uno solo, sulla stessa sagoma: con [Send] la X che chiude diventa «Invia» appena c'è del testo, e torna X
 * quando il testo si svuota (lo scambio sfocato di R7). È la strada del tasto a destra a stati del composer (F6B.7).
 */
enum class FluidSearchTrailing {
  /** Il cerchio che svuota il testo e la X che chiude: la ricerca di sempre. */
  Clear,

  /** «Invia»: con del testo la X diventa il tasto che invia, e l'invio chiama [FluidSearchSpec.onSearch]. */
  Send,
}

/**
 * Dichiarazione del campo, condivisa da schermata e toolbar: F4A.2 la posa, F4B.2 ne possiede il comportamento.
 *
 * Nessuna tastiera, stato interno o animazione viene avviata da questo tipo. I primi sei parametri seguono
 * il contratto del piano; i campi successivi permettono suggerimenti, ambiti in linea e inserimento generico
 * senza un secondo metodo nella DSL. Con [purpose] Input, [onSearch] è la conferma dell'inserimento.
 *
 * **I preset d'uso (F6B.6).** Le funzioni del companion danno il campo già deciso per un uso: [search], [message],
 * [note], [assistant] e [findInPage]. Ognuna sceglie l'icona, il segnaposto, il tasto a destra ([trailing]), che cosa
 * fa l'invio ([onSubmit]) e se il testo va a capo ([maxLines]); il resto si passa per nome. Chi costruisce il tipo a
 * mano, come prima di F6B.6, ha i campi nuovi ai valori di serie e lo stesso campo di prima.
 *
 * @property query il testo posseduto dall'app; il componente non ne conserva una copia privata.
 * @property onQueryChange restituisce le modifiche all'app.
 * @property placement la posizione di riposo richiesta.
 * @property behavior se partire dal campo o dal tasto.
 * @property prompt il segnaposto; null usa le parole del campo dell'engine.
 * @property onSearch la conferma della ricerca o dell'inserimento: l'invio della tastiera o il tasto «Invia».
 * @property suggestions il contenuto dei suggerimenti, disegnato dal futuro slot unico.
 * @property scopes gli ambiti, sulla riga del campo anziché su una seconda barra.
 * @property purpose ricerca oppure inserimento generico.
 * @property expansion dove va il campo attivo; Automatic delega al componente.
 * @property active lo stato di apertura posseduto dall'app.
 * @property onActiveChange la richiesta di apertura/chiusura, senza cambiare stato nel modello.
 * @property navigation (F6B.6) il contatore «3/12» e le frecce dei risultati dentro il campo aperto, come in
 *   [FluidSearchField]; null: niente. Il contatore è un'area viva per TalkBack («3 di 12»).
 * @property onSubmit (F6B.6) che cosa fa il campo dopo l'invio. Null, il valore di serie, è il comportamento di prima
 *   di F6B.6: con [onSearch] il campo resta aperto, senza [onSearch] si chiude.
 * @property maxLines (F6B.6) quante righe tiene il testo: 1 (di serie) è una riga sola che scorre di lato; di più, il
 *   testo va a capo e scorre dentro il campo. Il campo che cresce di riga in riga arriva con il composer (F6B.7).
 * @property icon (F6B.6) l'icona a sinistra, che è anche il glifo del tasto a riposo; null: la lente per
 *   [FluidSearchPurpose.Search], la matita per [FluidSearchPurpose.Input].
 * @property trailing (F6B.6) il tasto a destra del campo aperto: cancella (di serie) o invia.
 * @property showsAtRest (F6B.6) falso per un campo che a riposo non c'è, né tasto né campo, e compare solo con
 *   [active], come «trova» ([findInPage]): nasce per lente nella sua riga, non tocca la barra in alto e, quando si
 *   chiude, svanisce per lente con il contenuto sfocato. Un campo così vuole [onActiveChange].
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
  val navigation: FluidSearchNavigation? = null,
  val onSubmit: FluidSearchSubmit? = null,
  val maxLines: Int = 1,
  val icon: ImageVector? = null,
  val trailing: FluidSearchTrailing = FluidSearchTrailing.Clear,
  val showsAtRest: Boolean = true,
) {
  init {
    require(maxLines >= 1) { "maxLines deve essere almeno 1: $maxLines" }
  }

  /**
   * I preset d'uso del campo (F6B.6, piano F6B §5 e §6). Le parole di serie sono quelle italiane di
   * [FluidSearchLabels]: un'app in un'altra lingua passa `prompt`.
   */
  companion object {
    private val Words = FluidSearchLabels()

    /**
     * La ricerca di sempre: la lente, «Cerca», il cerchio che svuota; l'invio chiama [onSearch] e **chiude**,
     * lasciando la parola nel campo a riposo. Una riga.
     */
    fun search(
      query: String,
      onQueryChange: (String) -> Unit,
      prompt: String? = null,
      onSearch: (() -> Unit)? = null,
      placement: FluidSearchPlacement = FluidSearchPlacement.Automatic,
      behavior: FluidSearchBehavior = FluidSearchBehavior.Automatic,
      expansion: FluidSearchExpansion = FluidSearchExpansion.Automatic,
      suggestions: (@Composable () -> Unit)? = null,
      scopes: (@Composable () -> Unit)? = null,
      active: Boolean = false,
      onActiveChange: ((Boolean) -> Unit)? = null,
    ): FluidSearchSpec = FluidSearchSpec(
      query = query, onQueryChange = onQueryChange, placement = placement, behavior = behavior, prompt = prompt,
      onSearch = onSearch, suggestions = suggestions, scopes = scopes, purpose = FluidSearchPurpose.Search,
      expansion = expansion, active = active, onActiveChange = onActiveChange,
      onSubmit = FluidSearchSubmit.Close, maxLines = 1, icon = FluidIcons.Search, trailing = FluidSearchTrailing.Clear,
    )

    /**
     * Il campo di un messaggio: i due fumetti, «Messaggio», il tasto «Invia» che prende il posto della X appena c'è del
     * testo. L'invio chiama [onSend] e **resta aperto**, con la tastiera, pronto per il prossimo: svuotare il testo
     * dopo l'invio è dell'app. Il testo va a capo (fino a [maxLines] righe). Di serie sta in basso, sempre visibile, e
     * sale sopra la tastiera.
     */
    fun message(
      text: String,
      onTextChange: (String) -> Unit,
      onSend: () -> Unit,
      prompt: String = Words.message,
      placement: FluidSearchPlacement = FluidSearchPlacement.BottomBar,
      behavior: FluidSearchBehavior = FluidSearchBehavior.AlwaysVisible,
      expansion: FluidSearchExpansion = FluidSearchExpansion.AboveKeyboard,
      maxLines: Int = 5,
      active: Boolean = false,
      onActiveChange: ((Boolean) -> Unit)? = null,
    ): FluidSearchSpec = FluidSearchSpec(
      query = text, onQueryChange = onTextChange, placement = placement, behavior = behavior, prompt = prompt,
      onSearch = onSend, purpose = FluidSearchPurpose.Input, expansion = expansion, active = active,
      onActiveChange = onActiveChange, onSubmit = FluidSearchSubmit.StayOpen, maxLines = maxLines,
      icon = FluidIcons.Forum, trailing = FluidSearchTrailing.Send,
    )

    /**
     * Una nota breve: la matita, «Nota», il cerchio che svuota; «Fine» sulla tastiera chiama [onDone] e **chiude**. Il
     * testo va a capo. Di serie parte dalla barra come la ricerca e sale sopra la tastiera.
     */
    fun note(
      text: String,
      onTextChange: (String) -> Unit,
      onDone: (() -> Unit)? = null,
      prompt: String = Words.note,
      placement: FluidSearchPlacement = FluidSearchPlacement.Automatic,
      behavior: FluidSearchBehavior = FluidSearchBehavior.Automatic,
      expansion: FluidSearchExpansion = FluidSearchExpansion.AboveKeyboard,
      maxLines: Int = 4,
      active: Boolean = false,
      onActiveChange: ((Boolean) -> Unit)? = null,
    ): FluidSearchSpec = FluidSearchSpec(
      query = text, onQueryChange = onTextChange, placement = placement, behavior = behavior, prompt = prompt,
      onSearch = onDone, purpose = FluidSearchPurpose.Input, expansion = expansion, active = active,
      onActiveChange = onActiveChange, onSubmit = FluidSearchSubmit.Close, maxLines = maxLines,
      icon = FluidIcons.Edit, trailing = FluidSearchTrailing.Clear,
    )

    /**
     * Una domanda all'assistente: la scintilla, «Chiedi all'assistente», «Invia» al posto della X; l'invio chiama
     * [onAsk] e **resta aperto**, per la domanda dopo. Il testo va a capo. Di serie sta in basso e sale sopra la
     * tastiera. Il composer dell'assistente, con l'alone, è di F6B.7 e parte da qui.
     */
    fun assistant(
      question: String,
      onQuestionChange: (String) -> Unit,
      onAsk: () -> Unit,
      prompt: String = Words.assistant,
      placement: FluidSearchPlacement = FluidSearchPlacement.BottomBar,
      behavior: FluidSearchBehavior = FluidSearchBehavior.Automatic,
      expansion: FluidSearchExpansion = FluidSearchExpansion.AboveKeyboard,
      maxLines: Int = 6,
      suggestions: (@Composable () -> Unit)? = null,
      active: Boolean = false,
      onActiveChange: ((Boolean) -> Unit)? = null,
    ): FluidSearchSpec = FluidSearchSpec(
      query = question, onQueryChange = onQuestionChange, placement = placement, behavior = behavior, prompt = prompt,
      onSearch = onAsk, suggestions = suggestions, purpose = FluidSearchPurpose.Input, expansion = expansion,
      active = active, onActiveChange = onActiveChange, onSubmit = FluidSearchSubmit.StayOpen, maxLines = maxLines,
      icon = FluidIcons.AutoAwesome, trailing = FluidSearchTrailing.Send,
    )

    /**
     * «Trova nella pagina» (SwiftUI `findNavigator(isPresented:)`, UIKit `UIFindInteraction`): la lente, «Trova nella
     * pagina», «3/12» con le frecce e il cerchio che svuota. A riposo non c'è; con [active] nasce in fondo, **sale sopra
     * la tastiera e non tocca la barra in alto**, che resta com'è (WWDC25 231). L'invio passa al risultato successivo
     * ([FluidSearchNavigation.onNext]) e resta aperto. [rememberFluidFindState] dà [navigation] per un testo semplice;
     * le app con contenuti complessi la calcolano da sé.
     */
    fun findInPage(
      query: String,
      onQueryChange: (String) -> Unit,
      navigation: FluidSearchNavigation,
      active: Boolean,
      onActiveChange: (Boolean) -> Unit,
      prompt: String = Words.findInPage,
    ): FluidSearchSpec = FluidSearchSpec(
      query = query, onQueryChange = onQueryChange, placement = FluidSearchPlacement.BottomBar,
      behavior = FluidSearchBehavior.AlwaysVisible, prompt = prompt, onSearch = navigation.onNext,
      purpose = FluidSearchPurpose.Search, expansion = FluidSearchExpansion.AboveKeyboard, active = active,
      onActiveChange = onActiveChange, navigation = navigation, onSubmit = FluidSearchSubmit.StayOpen, maxLines = 1,
      icon = FluidIcons.Search, trailing = FluidSearchTrailing.Clear, showsAtRest = false,
    )
  }
}

/** L'icona a sinistra del campo e del tasto a riposo: quella del preset, o la lente e la matita di sempre. */
internal fun fluidSearchIcon(spec: FluidSearchSpec): ImageVector =
  spec.icon ?: if (spec.purpose == FluidSearchPurpose.Search) FluidIcons.Search else FluidIcons.Edit

/** Il tasto della tastiera: «Invia» col tasto a destra che invia, la lente per la ricerca, «Fine» per l'inserimento. */
internal fun fluidSearchImeAction(spec: FluidSearchSpec): ImeAction = when {
  spec.trailing == FluidSearchTrailing.Send -> ImeAction.Send
  spec.purpose == FluidSearchPurpose.Search -> ImeAction.Search
  else -> ImeAction.Done
}

/** Vero se dopo l'invio il campo si chiude: [FluidSearchSpec.onSubmit], o senza la regola di prima di F6B.6. */
internal fun fluidSearchClosesOnSubmit(spec: FluidSearchSpec): Boolean = when (spec.onSubmit) {
  FluidSearchSubmit.Close -> true
  FluidSearchSubmit.StayOpen -> false
  null -> spec.onSearch == null
}

/** Vero se c'è qualcosa da inviare: il tasto «Invia» c'è solo allora, e l'invio a testo vuoto non fa niente. */
internal fun fluidSearchCanSend(spec: FluidSearchSpec): Boolean =
  spec.trailing == FluidSearchTrailing.Send && spec.query.isNotBlank()

/**
 * L'invio, dalla tastiera o dal tasto «Invia»: chiama [FluidSearchSpec.onSearch] e poi chiude se [onSubmit][FluidSearchSpec.onSubmit]
 * lo dice. Col tasto a destra che invia, a testo vuoto non succede niente (non si manda un messaggio vuoto).
 */
internal fun fluidSearchSubmit(spec: FluidSearchSpec, close: () -> Unit) {
  if (spec.trailing == FluidSearchTrailing.Send && !fluidSearchCanSend(spec)) return
  spec.onSearch?.invoke()
  if (fluidSearchClosesOnSubmit(spec)) close()
}
