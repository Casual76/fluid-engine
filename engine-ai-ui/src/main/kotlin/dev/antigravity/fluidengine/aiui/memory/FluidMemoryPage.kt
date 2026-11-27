package dev.antigravity.fluidengine.aiui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.memory.AiMemory
import dev.antigravity.fluidengine.ai.memory.AiMemoryExclusion
import dev.antigravity.fluidengine.ai.memory.AiMemoryNotice
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposal
import dev.antigravity.fluidengine.ai.memory.AiMemorySource
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.icons.Apps
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.History
import dev.antigravity.fluidengine.ui.fluid.icons.Person
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.icons.VisibilityOff
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.time.ZoneId

/**
 * Cosa la pagina «Cosa ricordo di te» chiede di fare. Callback e basta: la memoria (`AiMemoryBook` di
 * `engine-ai-memory`) sta nell'app, come vogliono le regole di `engine-ai-ui`.
 */
class FluidMemoryActions(
  /** Un ricordo scritto a mano. */
  val add: (String) -> Unit,
  /** Il ricordo `id` corretto col testo nuovo. */
  val correct: (id: String, text: String) -> Unit,
  val setPinned: (id: String, pinned: Boolean) -> Unit,
  val forget: (id: String) -> Unit,
  val forgetAll: () -> Unit,
  /** Dimentica i ricordi nati dall'app (il pacchetto). */
  val forgetFromApp: (app: String) -> Unit,
  /** Toglie o ridà all'app il permesso di proporre ricordi. */
  val setAppBlocked: (app: String, blocked: Boolean) -> Unit,
  /** La risposta a una domanda «Da confermare»: Ricorda (vero) o No. */
  val answer: (noticeId: String, accept: Boolean) -> Unit = { _, _ -> },
  /** L'interruttore «Ricorda da solo». */
  val setAutoSave: (Boolean) -> Unit = {},
  /** «Dimentica e non imparare più»: dimentica il ricordo ed esclude il suo argomento. Null: l'azione non c'e'. */
  val forgetAndExclude: ((id: String) -> Unit)? = null,
  /** Toglie un'esclusione: la memoria puo' di nuovo imparare quell'argomento. */
  val include: (AiMemoryExclusion) -> Unit = {},
)

/** I tag delle prove. */
object FluidMemoryPageTags {
  const val Page = "fluid-memory:pagina"
  const val Add = "fluid-memory:aggiungi"
  const val ForgetAll = "fluid-memory:dimentica-tutto"
  const val Editor = "fluid-memory:modifica"
  const val AutoSave = "fluid-memory:ricorda-da-solo"

  fun pending(noticeId: String): String = "fluid-memory:da-confermare:$noticeId"
  fun exclusion(index: Int): String = "fluid-memory:esclusione:$index"

  fun row(id: String): String = "fluid-memory:ricordo:$id"
  fun forgetApp(app: String): String = "fluid-memory:dimentica-app:$app"
  fun blockApp(app: String): String = "fluid-memory:blocca-app:$app"
}

/**
 * **«Cosa ricordo di te»** (F9.3, F9.3b): la pagina che elenca la memoria e la fa correggere e revocare, sul vetro
 * dell'engine.
 *
 * - **In alto** l'interruttore «Ricorda da solo» ([autoSave]; null: non c'e'): spento, di serie, la memoria chiede prima
 *   di ricordare; acceso salva e lo dice con Annulla. Sotto, **«Da confermare»** ([pending]): le domande che aspettano
 *   ancora Ricorda o No, come card «Vuoi che lo ricordi?».
 * - **I gruppi**: «Da te» (detti in chat o scritti qui), uno per app («Da ClasseViva»: quelli proposti dall'app e
 *   quelli detti nella chat «Dalle app» partita da lei), «Dalla memoria di prima». Ogni gruppo e' un pannello di vetro
 *   (`FluidListGroup(glass = true)`).
 * - **Ogni riga** dice il fatto e, sotto, da dove viene, quando, la scadenza, se e' fissato o corretto, e cosa c'era
 *   prima se ha preso il posto di un altro («prima: è in 4F»). Un tocco la modifica; la pressione lunga (e TalkBack, con
 *   le azioni) offre Modifica, Fissa, Dimentica.
 * - **La revoca**: Dimentica sulla riga (e «Dimentica e non imparare più», se c'e' [FluidMemoryActions.forgetAndExclude]);
 *   sotto ogni gruppo di un'app «Dimentica quelli di ClasseViva» (con conferma) e «Non ricordare più da ClasseViva»; in
 *   fondo «Dimentica tutto» (con conferma).
 * - **«Non imparo più»** ([exclusions]): gli argomenti, le app e le fonti esclusi, ognuno con «Impara di nuovo».
 * - I fatti **dedotti** dai dati di un'app (il profilo della ricerca, fuso nella memoria) lo dicono nella riga, coi loro
 *   limiti («dedotto: solo dagli ultimi 30 giorni»).
 * - [memories] null vuol dire «non ancora letti»: la pagina non dice «vuota» prima di saperlo.
 *
 * Vuole intorno un `FluidTheme` e l'host dei modali alla radice (`FluidGlassModalHost`): i dialoghi ci passano.
 */
@Composable
fun FluidMemoryPage(
  memories: List<AiMemory>?,
  actions: FluidMemoryActions,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  strings: FluidMemoryStrings = FluidMemoryStrings(),
  blockedApps: Set<String> = emptySet(),
  nowMillis: Long = System.currentTimeMillis(),
  zone: ZoneId = ZoneId.systemDefault(),
  /** Vero per i ricordi da segnalare («sembra un codice»): di solito `AiMemoryPolicy::looksSecret` sul testo. */
  secretLike: (AiMemory) -> Boolean = { false },
  listState: LazyListState = rememberLazyListState(),
  ambient: FluidAmbient? = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Glow) },
  /** Le domande che aspettano una risposta (`AiMemoryBook.pending`). */
  pending: List<AiMemoryNotice> = emptyList(),
  /** L'interruttore «Ricorda da solo» (`AiMemoryBook.autoSave`); null lo nasconde. */
  autoSave: Boolean? = null,
  /** Le esclusioni (`AiMemoryBook.exclusions`). */
  exclusions: List<AiMemoryExclusion> = emptyList(),
  /** Il nome di un'app dal pacchetto, per le esclusioni. */
  appLabel: (String) -> String? = { null },
) {
  var editing by remember { mutableStateOf<Editing?>(null) }
  var confirmAll by remember { mutableStateOf(false) }
  var confirmApp by remember { mutableStateOf<Pair<String, String>?>(null) }

  FluidScreen(
    title = strings.pageTitle,
    subtitle = strings.pageSubtitle,
    onBack = onBack,
    modifier = modifier.testTag(FluidMemoryPageTags.Page),
    listState = listState,
    itemSpacing = 12.dp,
    ambient = ambient,
  ) {
    val list = memories
    if (autoSave != null) {
      item(key = "ricorda-da-solo") {
        FluidListGroup(glass = true) {
          FluidToggleRow(
            title = strings.autoSave,
            checked = autoSave,
            onCheckedChange = actions.setAutoSave,
            subtitle = if (autoSave) strings.autoSaveOn else strings.autoSaveOff,
            leading = { FluidIcon(FluidIcons.AutoAwesome, contentDescription = null) },
            modifier = Modifier.testTag(FluidMemoryPageTags.AutoSave),
          )
        }
      }
    }
    if (pending.isNotEmpty()) {
      item(key = "da-confermare") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidSectionHeader(title = strings.sectionPending)
          pending.forEach { notice ->
            FluidMemoryNoticeCard(
              notice = notice,
              onUndo = {},
              strings = strings,
              onAnswer = { accept -> actions.answer(notice.id, accept) },
              modifier = Modifier.testTag(FluidMemoryPageTags.pending(notice.id)),
            )
          }
        }
      }
    }
    when {
      list == null -> Unit
      list.isEmpty() -> item(key = "vuota") {
        FluidInlineMessage(title = strings.emptyTitle, message = strings.emptyMessage, tone = FluidTone.Info, glass = true)
      }
      else -> FluidMemoryTexts.groups(list, strings).forEach { group ->
        item(key = "gruppo-${group.app ?: group.title}") {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidSectionHeader(title = group.title)
            FluidListGroup(glass = true) {
              group.memories.forEachIndexed { index, memory ->
                if (index > 0) FluidListDivider()
                MemoryRow(
                  memory = memory,
                  subtitle = FluidMemoryTexts.subtitle(memory, nowMillis, zone, strings, secretLike(memory)),
                  strings = strings,
                  onEdit = { editing = Editing(memory.id, memory.text) },
                  onPin = { actions.setPinned(memory.id, !memory.pinned) },
                  onForget = { actions.forget(memory.id) },
                  onForgetExclude = actions.forgetAndExclude?.takeIf { memory.key != null }?.let { f -> { f(memory.id) } },
                )
              }
            }
            val app = group.app
            if (app != null) {
              val label = group.appLabel ?: app
              val blocked = app in blockedApps
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FluidButton(
                  text = strings.forgetApp.format(label),
                  onClick = { confirmApp = app to label },
                  style = FluidButtonStyle.Plain,
                  size = FluidButtonSize.Small,
                  modifier = Modifier.testTag(FluidMemoryPageTags.forgetApp(app)),
                )
                FluidButton(
                  text = if (blocked) strings.allowApp.format(label) else strings.blockApp.format(label),
                  onClick = { actions.setAppBlocked(app, !blocked) },
                  style = FluidButtonStyle.Plain,
                  size = FluidButtonSize.Small,
                  modifier = Modifier.testTag(FluidMemoryPageTags.blockApp(app)),
                )
              }
            }
          }
        }
      }
    }
    if (exclusions.isNotEmpty()) {
      item(key = "esclusioni") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidSectionHeader(title = strings.sectionExcluded)
          FluidListGroup(glass = true) {
            exclusions.forEachIndexed { index, exclusion ->
              if (index > 0) FluidListDivider()
              val a11y = remember(exclusion, strings) {
                listOf(CustomAccessibilityAction(strings.include) { actions.include(exclusion); true })
              }
              FluidListRow(
                title = FluidMemoryTexts.exclusion(exclusion, strings, appLabel),
                subtitle = strings.includeHint,
                leading = { FluidIcon(FluidIcons.VisibilityOff, contentDescription = null) },
                onClick = { actions.include(exclusion) },
                disclosure = false,
                customActions = a11y,
                modifier = Modifier.testTag(FluidMemoryPageTags.exclusion(index)),
              )
            }
          }
        }
      }
    }
    item(key = "azioni") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FluidButton(
          text = strings.add,
          onClick = { editing = Editing(null, "") },
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          modifier = Modifier.testTag(FluidMemoryPageTags.Add),
        )
        if (!list.isNullOrEmpty()) {
          FluidButton(
            text = strings.forgetAll,
            onClick = { confirmAll = true },
            style = FluidButtonStyle.Plain,
            size = FluidButtonSize.Small,
            modifier = Modifier.testTag(FluidMemoryPageTags.ForgetAll),
          )
        }
      }
    }
  }

  editing?.let { current ->
    var text by remember(current) { mutableStateOf(current.text) }
    FluidAlert(
      onDismissRequest = { editing = null },
      title = if (current.id == null) strings.newTitle else strings.editTitle,
      message = strings.editHint,
      modifier = Modifier.testTag(FluidMemoryPageTags.Editor),
      actions = listOf(
        FluidAlertAction(strings.cancel, { editing = null }),
        FluidAlertAction(
          strings.save,
          {
            val id = current.id
            if (id == null) actions.add(text) else actions.correct(id, text)
            editing = null
          },
          FluidAlertAction.Emphasis.Preferred,
          enabled = text.isNotBlank(),
        ),
      ),
    ) {
      FluidTextField(
        value = text,
        onValueChange = { text = it.take(AiMemoryProposal.MaxTextChars) },
        placeholder = strings.placeholder,
        singleLine = false,
        maxLines = 4,
      )
    }
  }
  if (confirmAll) {
    FluidAlert(
      onDismissRequest = { confirmAll = false },
      title = strings.forgetAllTitle,
      message = strings.forgetAllMessage,
      actions = listOf(
        FluidAlertAction(strings.cancel, { confirmAll = false }),
        FluidAlertAction(strings.forget, { actions.forgetAll(); confirmAll = false }, FluidAlertAction.Emphasis.Destructive),
      ),
    )
  }
  confirmApp?.let { (app, label) ->
    FluidAlert(
      onDismissRequest = { confirmApp = null },
      title = strings.forgetAppTitle.format(label),
      message = strings.forgetAppMessage.format(label),
      actions = listOf(
        FluidAlertAction(strings.cancel, { confirmApp = null }),
        FluidAlertAction(strings.forget, { actions.forgetFromApp(app); confirmApp = null }, FluidAlertAction.Emphasis.Destructive),
      ),
    )
  }
}

private class Editing(val id: String?, val text: String)

@Composable
private fun MemoryRow(
  memory: AiMemory,
  subtitle: String,
  strings: FluidMemoryStrings,
  onEdit: () -> Unit,
  onPin: () -> Unit,
  onForget: () -> Unit,
  onForgetExclude: (() -> Unit)? = null,
) {
  val icon = when {
    memory.pinned -> FluidIcons.Star
    memory.origin == AiMemory.OriginInference -> FluidIcons.AutoAwesome
    memory.source.kind == AiMemorySource.KindImported -> FluidIcons.History
    memory.source.app != null -> FluidIcons.Apps
    else -> FluidIcons.Person
  }
  val pinLabel = if (memory.pinned) strings.unpin else strings.pin
  // Le stesse azioni del menu, per TalkBack: la pressione lunga con un dito solo non c'e'.
  val a11y = remember(memory, strings, onForgetExclude != null) {
    listOfNotNull(
      CustomAccessibilityAction(strings.edit) { onEdit(); true },
      CustomAccessibilityAction(pinLabel) { onPin(); true },
      CustomAccessibilityAction(strings.forget) { onForget(); true },
      onForgetExclude?.let { f -> CustomAccessibilityAction(strings.forgetExclude) { f(); true } },
    )
  }
  FluidListRow(
    title = memory.text,
    subtitle = subtitle,
    subtitleMaxLines = 3,
    tone = if (memory.pinned) FluidTone.Warning else FluidTone.Primary,
    leading = { FluidIcon(icon, contentDescription = null) },
    onClick = onEdit,
    contextActions = {
      listOf(
        FluidContextAction(strings.edit, FluidIcons.Edit, onClick = onEdit),
        FluidContextAction(pinLabel, FluidIcons.Star, onClick = onPin),
        FluidContextAction(strings.forget, FluidIcons.Delete, destructive = true, startsSection = true, onClick = onForget),
      ) + listOfNotNull(
        onForgetExclude?.let { f -> FluidContextAction(strings.forgetExclude, FluidIcons.VisibilityOff, onClick = f) },
      )
    },
    customActions = a11y,
    modifier = Modifier.testTag(FluidMemoryPageTags.row(memory.id)),
  )
}
