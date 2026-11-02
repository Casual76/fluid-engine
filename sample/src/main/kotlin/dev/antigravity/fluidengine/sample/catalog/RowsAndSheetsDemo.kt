package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidChoiceRow
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmSheet
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidPromptAlert
import dev.antigravity.fluidengine.ui.fluid.FluidPromptValidation
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.fluid.fluidPromptNotBlank
import dev.antigravity.fluidengine.ui.haptics.ProvideFluidHapticTrace
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.FluidTone

/** Quale foglio o avviso della demo e' aperto: uno solo alla volta, come in una schermata vera. */
private enum class DemoOverlay { Confirm, Destroy, Rename, Calibrate }

/**
 * Le righe e i fogli della fase 12b, in una pagina: quello che cinque app si erano scritto da sole.
 *
 *  * **Righe**: `FluidToggleRow` (la riga e' l'interruttore, anche disattivata) e `FluidChoiceRow`
 *    (scelta singola con la spunta a destra, un `Tick` solo se la scelta si sposta);
 *  * **Aspetto**: `fluidAppearanceSection`, cablata a un `EngineSettings` locale **che qui il tema
 *    segue davvero** (un `FluidTheme` annidato, senza barre di sistema): scegli Scuro o un altro
 *    accento e la pagina si rimette i colori. Il vetro e' l'unico campo che `EngineSettings` non ha,
 *    e qui e' un interruttore della demo;
 *  * **Fogli**: `FluidConfirmSheet` (semplice e distruttivo, con un corpo) e `FluidPromptAlert` (una
 *    rinomina, e una taratura con un valore che puo' essere fuori misura).
 *
 * I fogli stanno nel `FluidGlassModalHost` della galleria: fuori da un host non si aprono (e non vanno
 * in crash), che e' la regola del portale e non un difetto della demo.
 */
@Composable
fun RowsAndSheetsDemo(bottomInset: Dp = 0.dp, onBack: (() -> Unit)? = null) {
  var settings by remember { mutableStateOf(EngineSettings()) }
  var glassOn by remember { mutableStateOf(true) }
  var notifications by remember { mutableStateOf(true) }
  var background by remember { mutableStateOf(false) }
  var sort by remember { mutableStateOf(DemoSort.Date) }
  var overlay by remember { mutableStateOf<DemoOverlay?>(null) }
  var lastResult by remember { mutableStateOf("Nessuna azione ancora") }
  var name by remember { mutableStateOf("Cucina") }

  // Il tema annidato: la sezione scrive in `settings`, e questo e' cio' che la fa vedere. Senza barre
  // di sistema (`systemBars = false`): e' una pagina dentro la galleria, non la finestra.
  FluidTheme(settings = settings, systemBars = false) {
    // Il tema annidato ricrea l'aptica e toglie il registro a quello che lo conteneva: si rimette.
    ProvideFluidHapticTrace {
      FluidScreen(
        title = "Righe e fogli",
        subtitle = "Interruttori, scelte, aspetto, conferme e avvisi con un campo.",
        onBack = onBack,
        extraBottomPadding = bottomInset,
        ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards) },
      ) {
        item(key = "rows-header") { FluidSectionHeader(title = "Interruttori") }
        item(key = "rows-toggles") {
          FluidListGroup(glass = glassOn) {
            FluidToggleRow(
              title = "Notifiche",
              subtitle = "Un avviso quando qualcosa cambia.",
              checked = notifications,
              onCheckedChange = { notifications = it },
              tone = FluidTone.Info,
              leading = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
              modifier = Modifier.testTag("demo-toggle-notifications"),
            )
            FluidListDivider()
            FluidToggleRow(
              title = "Sincronizza in background",
              subtitle = "Spenta: il tasto e' disattivato, la riga sfuma e non risponde.",
              checked = background,
              onCheckedChange = { background = it },
              enabled = notifications,
              tone = FluidTone.Success,
              leading = { Icon(Icons.Rounded.Sync, contentDescription = null) },
            )
            FluidListDivider()
            FluidToggleRow(
              title = "Sempre disattivata",
              checked = true,
              onCheckedChange = {},
              enabled = false,
            )
          }
        }

        item(key = "rows-choice-header") { FluidSectionHeader(title = "Scelta singola") }
        item(key = "rows-choice") {
          FluidListGroup(modifier = Modifier.selectableGroup(), glass = glassOn) {
            DemoSort.entries.forEachIndexed { index, option ->
              if (index > 0) FluidListDivider()
              FluidChoiceRow(
                title = option.label,
                subtitle = option.detail,
                selected = sort == option,
                onSelect = { sort = option },
                modifier = Modifier.testTag("demo-choice-${option.name}"),
              )
            }
          }
        }

        fluidAppearanceSection(
          settings = settings,
          onSettingsChange = { settings = it },
          glassEnabled = glassOn,
          onGlassEnabledChange = { glassOn = it },
          glass = glassOn,
          footnote = "Il vetro non e' un campo di EngineSettings: la sezione mostra la riga solo se " +
            "l'app le passa un valore e un callback suoi.",
        )

        item(key = "sheets-header") { FluidSectionHeader(title = "Fogli e avvisi") }
        item(key = "sheets") {
          FluidListGroup(glass = glassOn) {
            FluidListRow(
              title = "Conferma semplice",
              subtitle = "Foglio dal basso con icona, frase e due tasti.",
              tone = FluidTone.Primary,
              leading = { Icon(Icons.Rounded.Speed, contentDescription = null) },
              onClick = { overlay = DemoOverlay.Confirm },
            )
            FluidListDivider()
            FluidListRow(
              title = "Conferma distruttiva",
              subtitle = "Tono rosso, un corpo con gli effetti, il tasto che dice cosa fa.",
              tone = FluidTone.Danger,
              leading = { Icon(Icons.Rounded.Delete, contentDescription = null) },
              onClick = { overlay = DemoOverlay.Destroy },
            )
            FluidListDivider()
            FluidListRow(
              title = "Rinomina",
              subtitle = "\"$name\" — avviso con un campo: Invio conferma.",
              leading = { Icon(Icons.Rounded.Edit, contentDescription = null) },
              onClick = { overlay = DemoOverlay.Rename },
            )
            FluidListDivider()
            FluidListRow(
              title = "Calibra",
              subtitle = "Un valore tra 20 e 140: fuori misura il campo lo dice.",
              onClick = { overlay = DemoOverlay.Calibrate },
            )
          }
        }
        item(key = "sheets-result") { FluidSectionFootnote(text = "Ultimo esito: $lastResult") }
      }

      FluidConfirmSheet(
        visible = overlay == DemoOverlay.Confirm,
        onDismissRequest = {
          overlay = null
          lastResult = "Conferma annullata"
        },
        title = "Applicare la modifica?",
        message = "Succede subito e si puo' rifare. Il foglio non si chiude da solo: lo possiede la schermata.",
        icon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
        confirmLabel = "Applica",
        onConfirm = {
          overlay = null
          lastResult = "Modifica applicata"
        },
      )

      FluidConfirmSheet(
        visible = overlay == DemoOverlay.Destroy,
        onDismissRequest = {
          overlay = null
          lastResult = "Eliminazione annullata"
        },
        title = "Eliminare 12 elementi?",
        message = "Non si puo' annullare.",
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        destructive = true,
        confirmLabel = "Elimina dal telefono",
        onConfirm = {
          overlay = null
          lastResult = "12 elementi eliminati"
        },
      ) {
        FluidListGroup {
          FluidListRow(title = "Telefono", subtitle = "Verranno eliminati", tone = FluidTone.Danger, disclosure = false)
          FluidListDivider()
          FluidListRow(title = "Archivio", subtitle = "Restano", disclosure = false)
        }
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "Il corpo scorre se non ci sta, i tasti restano fermi sotto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (overlay == DemoOverlay.Rename) {
        FluidPromptAlert(
          title = "Rinomina",
          message = "Il testo si apre selezionato: si riscrive, o si tocca per ritoccare.",
          initialValue = name,
          placeholder = "Nome",
          onDismissRequest = { overlay = null },
          onConfirm = {
            name = it
            overlay = null
            lastResult = "Rinominato in \"$it\""
          },
        )
      }

      if (overlay == DemoOverlay.Calibrate) {
        FluidPromptAlert(
          title = "Calibra",
          message = "Scrivi il valore dello strumento di riferimento.",
          placeholder = "Es. 54,3",
          confirmLabel = "Calibra",
          validate = ::validateReading,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
          onDismissRequest = { overlay = null },
          onConfirm = {
            overlay = null
            lastResult = "Taratura a $it"
          },
        )
      }
    }
  }
}

/** Un valore tra 20 e 140, con la virgola o con il punto: vuoto tace, fuori misura lo dice. */
private fun validateReading(text: String): FluidPromptValidation {
  if (fluidPromptNotBlank(text) != FluidPromptValidation.Valid) return FluidPromptValidation.Incomplete
  val value = text.replace(',', '.').toDoubleOrNull()
  return if (value != null && value in 20.0..140.0) {
    FluidPromptValidation.Valid
  } else {
    FluidPromptValidation.Invalid("Un valore tra 20 e 140.")
  }
}

private enum class DemoSort(val label: String, val detail: String) {
  Name("Nome", ""),
  Date("Data", "I piu' recenti per primi."),
  Size("Dimensione", "I piu' grandi per primi."),
}
