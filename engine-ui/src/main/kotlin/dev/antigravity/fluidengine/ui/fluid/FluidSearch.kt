package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.acceptsTap
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

/** Che cosa fa il tasto a destra: interno, così una faccia nuova non rompe i `when` delle app (piano 3.11). */
internal enum class FluidSearchTrailingKind { Clear, Send, Voice, Stop }

/**
 * Il tasto a destra dentro il campo (F6B.6, F6B.7).
 *
 * Il tasto è **uno solo, sulla stessa sagoma**: le sue facce si scambiano sfocate (R7) e non sono mai due tasti. Le
 * facce dipendono da questo valore e dal testo:
 * - [Clear]: il cerchio che svuota il testo e la X che chiude; la ricerca di sempre;
 * - [Send]: la X che chiude diventa «Invia» appena c'è del testo, e torna X quando il testo si svuota;
 * - [voice] (F6B.7, il composer): il microfono a campo vuoto, «Invia» appena c'è del testo; si vede anche a riposo, come
 *   in Messaggi;
 * - [stop] (F6B.7): lo stop, per esempio mentre l'assistente lavora o mentre si ascolta; l'invio della tastiera con del
 *   testo resta l'invio.
 *
 * **Un tipo con i valori nel companion, non un enum** (F6B.7, piano 3.11): era un enum in F6B.6, mai uscito in una
 * versione; le facce nuove non sono valori aggiunti a un enum pubblico, e chi scrive `FluidSearchTrailing.Clear` o
 * `FluidSearchTrailing.Send` scrive come prima.
 *
 * **Niente doppio invio.** Con un tasto che invia, l'invio a testo vuoto non fa niente; mentre [sending] lavora
 * ([FluidActionState.Working], il cambio di stato di F6B.3a) il tasto è spento; e subito dopo un invio il campo non ne
 * accetta un altro dello stesso testo, né un tocco sul tasto che sta cambiando faccia, finché la faccia nuova non si
 * legge.
 *
 * @property onVoice il microfono ([voice]): l'app avvia la dettatura.
 * @property onStop lo stop ([stop]).
 * @property sending lo stato dell'invio (F6B.3a) sulla faccia «Invia»: l'anello mentre lavora, la spunta, il fallimento.
 *   Di serie a riposo.
 * @property stopLabel il nome dello stop per TalkBack; null: «Interrompi» ([FluidSearchLabels.stop]).
 * @property stopIcon il simbolo dello stop; null: il quadrato pieno.
 */
@Immutable
class FluidSearchTrailing private constructor(
  internal val kind: FluidSearchTrailingKind,
  val onVoice: (() -> Unit)? = null,
  val onStop: (() -> Unit)? = null,
  val sending: FluidActionState = FluidActionState.Idle,
  val stopLabel: String? = null,
  val stopIcon: ImageVector? = null,
) {
  override fun equals(other: Any?): Boolean = this === other || other is FluidSearchTrailing && other.kind == kind &&
    other.onVoice === onVoice && other.onStop === onStop && other.sending == sending && other.stopLabel == stopLabel &&
    other.stopIcon == stopIcon

  override fun hashCode(): Int {
    var h = kind.hashCode()
    h = 31 * h + System.identityHashCode(onVoice)
    h = 31 * h + System.identityHashCode(onStop)
    h = 31 * h + sending.hashCode()
    h = 31 * h + (stopLabel?.hashCode() ?: 0)
    return 31 * h + (stopIcon?.hashCode() ?: 0)
  }

  override fun toString(): String = "FluidSearchTrailing.$kind"

  companion object {
    /** Il cerchio che svuota il testo e la X che chiude: la ricerca di sempre. */
    val Clear: FluidSearchTrailing = FluidSearchTrailing(FluidSearchTrailingKind.Clear)

    /** «Invia»: con del testo la X diventa il tasto che invia, e l'invio chiama [FluidSearchSpec.onSearch]. */
    val Send: FluidSearchTrailing = FluidSearchTrailing(FluidSearchTrailingKind.Send)

    /** «Invia» con lo stato dell'invio (F6B.3a): mentre [sending] lavora il tasto è spento e mostra l'anello. */
    fun send(sending: FluidActionState): FluidSearchTrailing =
      FluidSearchTrailing(FluidSearchTrailingKind.Send, sending = sending)

    /**
     * Il tasto del composer (F6B.7): il microfono a campo vuoto ([onVoice]), «Invia» appena c'è del testo, sulla stessa
     * sagoma. C'è anche a riposo.
     */
    fun voice(onVoice: () -> Unit, sending: FluidActionState = FluidActionState.Idle): FluidSearchTrailing =
      FluidSearchTrailing(FluidSearchTrailingKind.Voice, onVoice = onVoice, sending = sending)

    /**
     * Lo stop sulla stessa sagoma (F6B.7): l'assistente lavora, o si sta ascoltando. [label] e [icon] cambiano nome e
     * simbolo (per esempio «Zittisci» con l'altoparlante barrato).
     */
    fun stop(onStop: () -> Unit, label: String? = null, icon: ImageVector? = null): FluidSearchTrailing =
      FluidSearchTrailing(FluidSearchTrailingKind.Stop, onStop = onStop, stopLabel = label, stopIcon = icon)
  }
}

/**
 * Ciò che si disegna **attorno al vetro** del campo di un composer (F6B.7): l'alone del composer dell'assistente. Il
 * campo lo chiama a ogni disegno con la sagoma del vetro **dov'è adesso**, anche in viaggio o mentre cresce, nelle
 * coordinate della riga.
 *
 * Il disegno sta nella barra, sopra la pagina: **non entra nella cattura del vetro**, che rifrange la pagina e non la
 * barra, quindi un alone che si muove non fa ricatturare niente. Chi anima l'alone legge il suo tempo dentro il disegno
 * (non in composizione) e a riposo non chiede fotogrammi.
 */
@Stable
interface FluidComposerHalo {
  /** Dietro al vetro: dentro la sagoma il vetro lo copre, fuori sborda. [glass] è la sagoma del vetro, in px. */
  fun DrawScope.drawBehindGlass(glass: RoundRect) {}

  /** Sopra al vetro: di solito un filo sul bordo della sagoma. */
  fun DrawScope.drawOverGlass(glass: RoundRect) {}

  /**
   * Il campo dice se è aperto (col fuoco e la tastiera), a ogni cambio, anche quando la sua apertura la tiene lo schermo e
   * non l'app: un alone che si anima solo a campo attivo lo sa da qui.
   */
  fun fieldActiveChanged(active: Boolean) {}
}

/**
 * Le parti del composer dentro il campo (F6B.7, piano F6B §7), per [FluidSearchSpec.message] e
 * [FluidSearchSpec.assistant]. Il campo cresce di riga in riga fino a `maxLines` e poi scorre dentro: il bordo dalla
 * parte della tastiera resta fermo e sale quello opposto (in basso, quello alto).
 *
 * @property leading lo slot a sinistra, al posto dell'icona del preset: il «+». Sta in fondo, accanto all'ultima riga.
 * @property attachments il vassoio degli allegati, dentro il vetro e sopra il testo: cresce verso l'alto e, quando l'app
 *   lo toglie (null), il suo contenuto esce sfocato e il vetro si richiude senza spostare il bordo dalla parte della
 *   tastiera.
 * @property trailing il tasto a destra a stati ([FluidSearchTrailing.voice], [FluidSearchTrailing.stop], «Invia»); null:
 *   «Invia» ([FluidSearchTrailing.Send]).
 * @property voiceLine la riga della voce, **al posto del testo**, finché c'è: l'app la dà solo mentre ascolta. Il testo
 *   esce sfocato e la riga entra sfocata (R7), poi il contrario.
 * @property halo ciò che si disegna attorno al vetro ([FluidComposerHalo]): l'alone del composer dell'assistente.
 */
@Immutable
data class FluidComposerSlots(
  val leading: (@Composable () -> Unit)? = null,
  val attachments: (@Composable () -> Unit)? = null,
  val trailing: FluidSearchTrailing? = null,
  val voiceLine: (@Composable () -> Unit)? = null,
  val halo: FluidComposerHalo? = null,
)

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
 * @property composer (F6B.7) le parti del composer: lo slot a sinistra, il vassoio degli allegati, il tasto a destra a
 *   stati, la riga della voce e l'alone ([FluidComposerSlots]). Null: niente. Un campo con più righe ([maxLines]) o con
 *   il composer **cresce** di riga in riga: il vetro sale dal bordo opposto alla tastiera, e il piano lo segue senza
 *   viaggio (`FluidGlassAnimation.None`, la scheda del piano flottante).
 * @property tokens (F6B.8b) i gettoni dentro il campo, prima del testo: SwiftUI `searchable(text:tokens:)`. Posseduti
 *   dall'app, come [query]: il campo non ne tiene una copia. Il testo scorre dopo di loro; se non ci stanno con un po' di
 *   testo, i primi si raccolgono in un gettone «+N» (come il campo «A:» di Mail). Il campo di una riga li mostra; un campo
 *   che cresce (il composer) no.
 * @property onTokensChange (F6B.8b) i gettoni nuovi: una pillola di [filters] scelta (aggiunta in fondo), un gettone
 *   toccato o tolto col tasto «cancella» a testo vuoto (il primo colpo lo sceglie, il secondo lo toglie, come in iOS).
 *   Null: i gettoni si vedono ma non cambiano.
 * @property filters (F6B.8b) i gettoni suggeriti, SwiftUI `suggestedTokens`: una fila di pillole di vetro accanto al campo
 *   aperto (sopra il campo in basso, sotto quello in alto). La pillola scelta **diventa gettone** con un viaggio: il suo
 *   vetro vola dentro il campo e diventa il gettone, e il suo posto nella fila si richiude; il gettone tolto rifà il
 *   viaggio al contrario e torna pillola. Le pillole che sono già gettoni (stesso id) non stanno nella fila. In
 *   [FluidFilterMode.Single] un gettone della fila prende il posto dell'altro. Il preset è [FluidFilterPresets.tokens].
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
  val composer: FluidComposerSlots? = null,
  val tokens: List<FluidToken> = emptyList(),
  val onTokensChange: ((List<FluidToken>) -> Unit)? = null,
  val filters: FluidFilterSet? = null,
) {
  init {
    require(maxLines >= 1) { "maxLines deve essere almeno 1: $maxLines" }
    require(tokens.map { it.id }.toSet().size == tokens.size) { "FluidSearchSpec: gli id dei gettoni devono essere unici" }
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
     *
     * Con [tokens] e [filters] (F6B.8b) è la ricerca a gettoni di SwiftUI (`searchable(text:tokens:suggestedTokens:)`):
     * le pillole di [filters] stanno accanto al campo aperto e, scelte, diventano gettoni nel campo; [onTokensChange]
     * riceve i gettoni nuovi. Il preset dei gettoni suggeriti è [FluidFilterPresets.tokens] («da: persona»).
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
      tokens: List<FluidToken> = emptyList(),
      onTokensChange: ((List<FluidToken>) -> Unit)? = null,
      filters: FluidFilterSet? = null,
    ): FluidSearchSpec = FluidSearchSpec(
      query = query, onQueryChange = onQueryChange, placement = placement, behavior = behavior, prompt = prompt,
      onSearch = onSearch, suggestions = suggestions, scopes = scopes, purpose = FluidSearchPurpose.Search,
      expansion = expansion, active = active, onActiveChange = onActiveChange,
      onSubmit = FluidSearchSubmit.Close, maxLines = 1, icon = FluidIcons.Search, trailing = FluidSearchTrailing.Clear,
      tokens = tokens, onTokensChange = onTokensChange, filters = filters,
    )

    /**
     * Il campo di un messaggio: i due fumetti, «Messaggio», il tasto «Invia» che prende il posto della X appena c'è del
     * testo. L'invio chiama [onSend] e **resta aperto**, con la tastiera, pronto per il prossimo: svuotare il testo
     * dopo l'invio è dell'app. Il testo va a capo e il campo **cresce** di riga in riga fino a [maxLines], poi scorre
     * dentro (F6B.7). Di serie sta in basso, sempre visibile, e sale sopra la tastiera.
     *
     * Con [composer] (F6B.7) è il composer dentro il campo: il «+» a sinistra, gli allegati sopra il testo, il tasto a
     * destra a stati (di serie «Invia»: [FluidComposerSlots.trailing]) e la riga della voce.
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
      composer: FluidComposerSlots? = null,
    ): FluidSearchSpec = FluidSearchSpec(
      query = text, onQueryChange = onTextChange, placement = placement, behavior = behavior, prompt = prompt,
      onSearch = onSend, purpose = FluidSearchPurpose.Input, expansion = expansion, active = active,
      onActiveChange = onActiveChange, onSubmit = FluidSearchSubmit.StayOpen, maxLines = maxLines,
      icon = FluidIcons.Forum, trailing = composer?.trailing ?: FluidSearchTrailing.Send, composer = composer,
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
     * [onAsk] e **resta aperto**, per la domanda dopo. Il testo va a capo e il campo cresce (F6B.7). Di serie sta in
     * basso e sale sopra la tastiera. Con [composer] è il composer: il composer dell'assistente, con l'alone, lo dà
     * già pieno `fluidAssistantField` di engine-ai-ui (F6B.7).
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
      composer: FluidComposerSlots? = null,
    ): FluidSearchSpec = FluidSearchSpec(
      query = question, onQueryChange = onQuestionChange, placement = placement, behavior = behavior, prompt = prompt,
      onSearch = onAsk, suggestions = suggestions, purpose = FluidSearchPurpose.Input, expansion = expansion,
      active = active, onActiveChange = onActiveChange, onSubmit = FluidSearchSubmit.StayOpen, maxLines = maxLines,
      icon = FluidIcons.AutoAwesome, trailing = composer?.trailing ?: FluidSearchTrailing.Send, composer = composer,
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
  fluidSearchSendsText(spec) -> ImeAction.Send
  spec.purpose == FluidSearchPurpose.Search -> ImeAction.Search
  else -> ImeAction.Done
}

/**
 * Vero se il tasto a destra è di un campo che **invia** il suo testo ([FluidSearchTrailing.Send], [voice]
 * [FluidSearchTrailing.voice], [stop][FluidSearchTrailing.stop]): l'invio a testo vuoto non fa niente e un invio non si
 * ripete (F6B.7).
 */
internal fun fluidSearchSendsText(spec: FluidSearchSpec): Boolean = spec.trailing.kind != FluidSearchTrailingKind.Clear

/**
 * Vero se il campo **cresce** di riga in riga (F6B.7): più di una riga, o le parti del composer. Un campo di una riga
 * senza composer resta quello di F6B.6, al pixel.
 */
internal fun fluidSearchGrows(spec: FluidSearchSpec): Boolean = spec.maxLines > 1 || spec.composer != null

/**
 * Quanto il vetro del campo che cresce supera la sua riga, in px (F6B.7): l'altezza naturale del contenuto (il vassoio
 * degli allegati e le righe del testo, al più `maxLines`) meno quella della riga. Mai negativo; zero per un campo chiuso
 * in una lente o assente.
 */
internal fun fluidSearchFieldExtraPx(contentPx: Int, rowPx: Int, compact: Boolean): Int =
  if (compact) 0 else (contentPx - rowPx).coerceAtLeast(0)

/**
 * L'altezza naturale del contenuto del campo che cresce, in px (F6B.7): la regola del suo layout. Il testo sta fra due
 * margini di [padPx] (8 dp, quelli del campo di testo) e tiene [lines] righe alte [linePx], al più [maxLines]: oltre,
 * scorre dentro e il campo non cresce più. Mai meno della riga ([rowPx], 40 dp). Sopra, il vassoio degli allegati
 * ([trayPx], zero senza). Pura: la usano le prove e le tracce del banco; il campo la ottiene dal layout.
 */
internal fun fluidSearchFieldContentPx(lines: Int, maxLines: Int, linePx: Int, rowPx: Int, padPx: Int, trayPx: Int = 0): Int =
  maxOf(rowPx, 2 * padPx + lines.coerceIn(1, maxOf(1, maxLines)) * linePx) + trayPx.coerceAtLeast(0)

/** Vero se dopo l'invio il campo si chiude: [FluidSearchSpec.onSubmit], o senza la regola di prima di F6B.6. */
internal fun fluidSearchClosesOnSubmit(spec: FluidSearchSpec): Boolean = when (spec.onSubmit) {
  FluidSearchSubmit.Close -> true
  FluidSearchSubmit.StayOpen -> false
  null -> spec.onSearch == null
}

/**
 * Vero se il tasto a destra mostra «Invia»: un tasto che invia ([FluidSearchTrailing.Send] o il microfono del composer)
 * e qualcosa da inviare. A testo vuoto c'è la X o il microfono.
 */
internal fun fluidSearchCanSend(spec: FluidSearchSpec): Boolean =
  (spec.trailing.kind == FluidSearchTrailingKind.Send || spec.trailing.kind == FluidSearchTrailingKind.Voice) &&
    spec.query.isNotBlank()

/**
 * Vero se l'invio può partire adesso (F6B.7): un campo che invia ha del testo, e il suo invio non sta già lavorando
 * ([FluidSearchTrailing.sending], F6B.3a). Un campo che non invia (la ricerca, «trova») conferma sempre.
 */
internal fun fluidSearchSubmittable(spec: FluidSearchSpec): Boolean =
  !fluidSearchSendsText(spec) || spec.query.isNotBlank() && spec.trailing.sending.acceptsTap

/**
 * L'invio, dalla tastiera o dal tasto «Invia»: chiama [FluidSearchSpec.onSearch] e poi chiude se [onSubmit][FluidSearchSpec.onSubmit]
 * lo dice. Col tasto a destra che invia, a testo vuoto o con l'invio che lavora non succede niente (non si manda un
 * messaggio vuoto, né due volte lo stesso).
 */
internal fun fluidSearchSubmit(spec: FluidSearchSpec, close: () -> Unit) {
  if (!fluidSearchSubmittable(spec)) return
  spec.onSearch?.invoke()
  if (fluidSearchClosesOnSubmit(spec)) close()
}

/**
 * Il cancello dell'invio del campo che invia (F6B.7, «senza doppio invio»): subito dopo un invio il campo non ne accetta
 * un altro **dello stesso testo**, né un tocco sul tasto a destra, per [windowMs]: il tempo in cui la faccia del tasto
 * cambia (da «Invia» al microfono, a testo svuotato) e non si legge ancora. Così un doppio tocco su «Invia» non manda due
 * volte il messaggio e non avvia la dettatura col secondo tocco. Un testo diverso passa subito.
 *
 * Di serie [windowMs] è lo scambio sfocato del contenuto fino alla faccia nuova piena
 * ([GlassContentDefaults.DurationMs] × [GlassContentDefaults.EnterEnd], 345 ms; R7, fiducia alta sul tempo dello scambio).
 */
@Stable
internal class FluidSearchSubmitGate(
  private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
  private val windowMs: Long = FluidSearchSubmitGateMs,
) {
  private var lastAt = Long.MIN_VALUE / 2
  private var lastText: String? = null

  /** Vero se l'invio di [text] può partire, e allora lo segna. */
  fun trySubmit(text: String): Boolean {
    val now = clockMs()
    if (text == lastText && now - lastAt < windowMs) return false
    lastAt = now
    lastText = text
    return true
  }

  /** Vero se il tasto a destra risponde a un tocco: non nella finestra dopo un invio. */
  fun tapAllowed(): Boolean = clockMs() - lastAt >= windowMs
}

/** La finestra del cancello: lo scambio sfocato fino alla faccia nuova piena (R7). */
internal val FluidSearchSubmitGateMs: Long =
  kotlin.math.round(GlassContentDefaults.DurationMs * GlassContentDefaults.EnterEnd).toLong()
