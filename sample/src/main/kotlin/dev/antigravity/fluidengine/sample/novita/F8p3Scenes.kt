package dev.antigravity.fluidengine.sample.novita

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidPasswordRules
import dev.antigravity.fluidengine.foundation.FluidUnit
import dev.antigravity.fluidengine.foundation.FluidUnitDimension
import dev.antigravity.fluidengine.foundation.FluidUnits
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidCodeField
import dev.antigravity.fluidengine.ui.fluid.FluidCopyButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidPasswordField
import dev.antigravity.fluidengine.ui.fluid.FluidPasteButton
import dev.antigravity.fluidengine.ui.fluid.FluidPasteKind
import dev.antigravity.fluidengine.ui.fluid.FluidPasted
import dev.antigravity.fluidengine.ui.fluid.FluidPrivateVeil
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSpecialFieldLabels
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.FluidUnitField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasIsGlass
import dev.antigravity.fluidengine.ui.fluid.LocalFluidDiscreetMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSpecialFieldLabels
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.fluidPrivate
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCodeFieldState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPrivateState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.math.BigDecimal
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

// F8.3 · Campi speciali: il campo con unità, il campo a codice, il campo password, il valore riservato, la copia delicata
// e il tasto incolla, nei casi delle app di Alessio (Universal Converter, MyCloud Gallery, ClasseViva, PampAI, Pampa Mail,
// Pampa Store), rifatti col solo engine. Ogni scena ha in alto il fondo (pagina, foto chiara, foto scura) e, dove conta,
// la lingua. Nessun numero di movimento qui: tempi, curve e aspetto sono dell'engine.

/** Il fondo di una scena. */
internal enum class F8p3Backdrop(val label: String) {
  Page("Pagina"),
  LightPhoto("Foto chiara"),
  DarkPhoto("Foto scura"),
}

/** I tag dei test. */
internal object F8p3Tags {
  const val Backdrops = "f8p3:fondi"
  const val Languages = "f8p3:lingue"
  const val Code = "f8p3:codice"
  const val Verify = "f8p3:verifica"
  const val Status = "f8p3:stato"

  fun backdrop(b: F8p3Backdrop): String = "f8p3:fondo:${b.name}"
}

/** Le sei scene di F8.3, nella pagina «Elementi nuovi». */
internal val F8p3Section: NewElementSection = NewElementSection(
  pack = "F8.3",
  title = "Campi speciali",
  detail = "unità, codice, password, valore riservato, copia delicata, tasto incolla",
  scenes = listOf(
    NewElementScene("unita", "Il campo con unità", "Universal Converter, Pampa Store · km e mi, °C, €, % in italiano e in inglese") { UnitScene(it) },
    NewElementScene("codice", "Il campo a codice", "MyCloud Gallery, Pampa Store · sei cifre, il PIN, il codice di abbinamento, l'errore") { CodeScene(it) },
    NewElementScene("password", "Il campo password", "ClasseViva, Pampa Mail · regole che si spuntano, forza, password forte") { PasswordScene(it) },
    NewElementScene("riservato", "Il valore riservato", "PampAI, ClasseViva, MyCloud · chiavi, voti in modalità discreta, l'album nascosto") { PrivateScene(it) },
    NewElementScene("copia", "La copia delicata", "PampAI, Spese · la chiave e l'IBAN che si cancellano dagli appunti") { CopyScene(it) },
    NewElementScene("incolla", "Il tasto incolla", "PampAI, MyCloud · compare solo col codice, il link o l'immagine") { PasteScene(it) },
  ),
)

/** Le voci da provare a mano (in coda a `ToTry.kt`). */
internal val F8p3TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-3-unita", TryArea.FieldsEditors, "Il campo con unità",
    "Apri «Il campo con unità», scrivi 1234,5 nei chilometri e cambia unità dalla capsula; poi passa a «English»: " +
      "giudica se il numero resta scrivibile mentre lo scrivi e se la conversione si legge.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il campo con unità"),
  ),
  TryItem(
    "f8-3-codice", TryArea.FieldsEditors, "Il campo a codice",
    "Apri «Il campo a codice», copia il codice di prova e incollalo col tasto, poi scrivi un codice sbagliato: " +
      "giudica la scossa e se le caselle somigliano ai campi accanto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il campo a codice"),
  ),
  TryItem(
    "f8-3-password", TryArea.FieldsEditors, "Il campo password",
    "Apri «Il campo password», tocca il campo vuoto e usa la password forte, poi scrivine una tua: " +
      "giudica se le regole si spuntano mentre scrivi e se la forza ha senso.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il campo password"),
  ),
  TryItem(
    "f8-3-riservato", TryArea.FieldsEditors, "Il valore riservato",
    "Apri «Il valore riservato», scopri la chiave e apri le app recenti: giudica se la pagina non si vede nelle recenti " +
      "e se al ritorno la chiave è di nuovo coperta.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il valore riservato"),
  ),
  TryItem(
    "f8-3-copia", TryArea.FieldsEditors, "La copia delicata",
    "Apri «La copia delicata», copia la chiave con 10 s e guarda l'anteprima degli appunti del sistema: " +
      "giudica se il segreto non si vede e se dopo il tempo gli appunti sono vuoti.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La copia delicata"),
  ),
  TryItem(
    "f8-3-incolla", TryArea.FieldsEditors, "Il tasto incolla",
    "Apri «Il tasto incolla» e copia prima un testo normale, poi un link, poi un'immagine: " +
      "giudica se il tasto compare solo per il dato giusto e mai con gli appunti normali.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il tasto incolla"),
  ),
)

// ================================================================================================================
// I pezzi comuni
// ================================================================================================================

@Composable
private fun BackdropChips(selected: F8p3Backdrop, onSelect: (F8p3Backdrop) -> Unit) {
  Row(
    Modifier.testTag(F8p3Tags.Backdrops).horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    F8p3Backdrop.entries.forEach { b ->
      FluidChip(label = b.label, selected = b == selected, onClick = { onSelect(b) }, modifier = Modifier.testTag(F8p3Tags.backdrop(b)))
    }
  }
}

@Composable
private fun LanguageChips(english: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.testTag(F8p3Tags.Languages), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidChip(label = "Italiano", selected = !english, onClick = { onChange(false) })
    FluidChip(label = "English", selected = english, onClick = { onChange(true) })
  }
}

/**
 * Il palco di un gruppo di elementi: sulla pagina così com'è; sulle foto dentro un pannello di vetro dell'engine che le
 * rifrange, con i campi nel loro pozzo «sopra il vetro».
 */
@Composable
private fun Stage(backdrop: F8p3Backdrop, content: @Composable ColumnScope.() -> Unit) {
  if (backdrop == F8p3Backdrop.Page) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    return
  }
  val glass = rememberGlassBackdrop()
  val photo = if (backdrop == F8p3Backdrop.LightPhoto) R.drawable.viewer_astronaut else R.drawable.viewer_rocket
  Box(Modifier.fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Group))) {
    Image(
      painter = painterResource(photo),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.matchParentSize().glassBackdropSource(glass),
    )
    CompositionLocalProvider(LocalGlassBackdrop provides glass, LocalFluidCanvasBackdrop provides glass) {
      Box(Modifier.padding(horizontal = 12.dp, vertical = 48.dp)) {
        FluidListGroup(glass = true) {
          CompositionLocalProvider(LocalFluidCanvasIsGlass provides true) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
          }
        }
      }
    }
  }
}

@Composable
private fun Caption(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** La pagina di una scena: titolo, fondo, e le sue sezioni. */
@Composable
private fun SceneScreen(
  title: String,
  subtitle: String,
  onBack: () -> Unit,
  backdrop: F8p3Backdrop,
  onBackdrop: (F8p3Backdrop) -> Unit,
  extraControls: (@Composable () -> Unit)? = null,
  body: LazyListScope.() -> Unit,
) {
  FluidScreen(title = title, subtitle = subtitle, onBack = onBack) {
    item(key = "fondi") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackdropChips(backdrop, onBackdrop)
        extraControls?.invoke()
      }
    }
    body()
  }
}

// ================================================================================================================
// Il campo con unità
// ================================================================================================================

@Composable
private fun UnitScene(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p3Backdrop.Page) }
  var english by rememberSaveable { mutableStateOf(false) }
  val locale = if (english) Locale.US else Locale.ITALY
  var distance by remember { mutableStateOf<BigDecimal?>(BigDecimal("12.5")) }
  var distanceUnit by remember { mutableStateOf(FluidUnits.Kilometer) }
  var temperature by remember { mutableStateOf<BigDecimal?>(BigDecimal("-3.5")) }
  var temperatureUnit by remember { mutableStateOf(FluidUnits.Celsius) }
  var price by remember { mutableStateOf<BigDecimal?>(BigDecimal("1249.9")) }
  var discount by remember { mutableStateOf<BigDecimal?>(BigDecimal("15")) }
  val labels = if (english) FluidSpecialFieldLabels.English else FluidSpecialFieldLabels()
  CompositionLocalProvider(LocalFluidSpecialFieldLabels provides labels) {
    SceneScreen(
      title = "Il campo con unità",
      subtitle = "F8.3 · si legge, si scrive e si converte",
      onBack = onBack,
      backdrop = backdrop,
      onBackdrop = { backdrop = it },
      extraControls = { LanguageChips(english) { english = it } },
    ) {
      item(key = "converter") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidSectionHeader(title = "Universal Converter", detail = "La distanza e la temperatura, con la conversione sotto.")
          Stage(backdrop) {
            FluidUnitField(
              value = distance,
              onValueChange = { distance = it },
              unit = distanceUnit,
              units = FluidUnits.of(FluidUnitDimension.Length),
              onUnitChange = { distanceUnit = it },
              convertTo = if (distanceUnit == FluidUnits.Mile) FluidUnits.Kilometer else FluidUnits.Mile,
              label = if (english) "Distance" else "Distanza",
              locale = locale,
            )
            FluidUnitField(
              value = temperature,
              onValueChange = { temperature = it },
              unit = temperatureUnit,
              units = FluidUnits.of(FluidUnitDimension.Temperature),
              onUnitChange = { temperatureUnit = it },
              convertTo = if (temperatureUnit == FluidUnits.Fahrenheit) FluidUnits.Celsius else FluidUnits.Fahrenheit,
              label = if (english) "Temperature" else "Temperatura",
              locale = locale,
            )
          }
        }
      }
      item(key = "store") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidSectionHeader(title = "Pampa Store", detail = "Il prezzo in euro (due cifre, «12,50 €») e lo sconto.")
          Stage(backdrop) {
            FluidUnitField(
              value = price,
              onValueChange = { price = it },
              unit = FluidUnits.Euro,
              label = if (english) "Price" else "Prezzo",
              locale = locale,
            )
            FluidUnitField(
              value = discount,
              onValueChange = { discount = it },
              unit = FluidUnits.Percent,
              label = if (english) "Discount" else "Sconto",
              locale = locale,
            )
          }
        }
      }
      item(key = "valori") {
        FluidSectionFootnote(
          text = "Il campo restituisce il numero, mai la stringa: distanza ${distance?.toPlainString() ?: "—"} ${distanceUnit.id}, " +
            "temperatura ${temperature?.toPlainString() ?: "—"} ${temperatureUnit.id}, prezzo ${price?.toPlainString() ?: "—"} EUR.",
        )
      }
    }
  }
}

// ================================================================================================================
// Il campo a codice
// ================================================================================================================

/** Il codice giusto della scena: quello del tasto «Copia il codice di prova». */
internal const val F8p3RightCode = "482913"

@Composable
private fun CodeScene(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p3Backdrop.Page) }
  val context = LocalContext.current
  val nas = rememberFluidCodeFieldState(6)
  val pin = rememberFluidCodeFieldState(4)
  val pairing = rememberFluidCodeFieldState(8)
  var verify by remember { mutableStateOf<FluidActionState>(FluidActionState.Idle) }
  val scope = rememberCoroutineScope()
  // La verifica finta: il codice giusto è 482913. Dura quanto basta per vedere il lavoro.
  val check: (String) -> Unit = { code ->
    if (verify !is FluidActionState.Working) scope.launch {
      verify = FluidActionState.Working()
      delay(900)
      if (code == F8p3RightCode) {
        verify = FluidActionState.Succeeded
      } else {
        verify = FluidActionState.Failed
        nas.fail()
      }
      delay(1_500)
      verify = FluidActionState.Idle
    }
  }
  SceneScreen("Il campo a codice", "F8.3 · le caselle, l'incolla, la proposta, l'errore", onBack, backdrop, { backdrop = it }) {
    item(key = "nas") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "MyCloud Gallery", detail = "L'accesso al NAS in due passaggi: il codice giusto è $F8p3RightCode.")
        Stage(backdrop) {
          FluidCodeField(
            state = nas,
            onComplete = { check(it) },
            verifying = verify is FluidActionState.Working,
            modifier = Modifier.fillMaxWidth().testTag(F8p3Tags.Code),
            label = "Codice di verifica",
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FluidActionButton(
              preset = FluidActionPreset.custom(
                idle = FluidIcons.Check, success = FluidIcons.Check, failure = FluidActionPreset.Save.failure,
                words = dev.antigravity.fluidengine.ui.fluid.action.FluidActionWords(
                  "Verifica", "Verifica in corso…", "Codice corretto", "Codice non corretto", "Verifica annullata",
                ),
              ),
              state = verify,
              onClick = { if (nas.isComplete) check(nas.code) },
              label = "Verifica",
              modifier = Modifier.testTag(F8p3Tags.Verify),
            )
            FluidGlassButton(
              text = "Copia il codice di prova",
              onClick = {
                context.getSystemService(ClipboardManager::class.java)
                  ?.setPrimaryClip(ClipData.newPlainText("Codice", "Il tuo codice MyCloud è $F8p3RightCode"))
              },
              size = FluidGlassSize.Compact,
              maxLines = 1,
            )
          }
          Caption("Il tasto «Incolla il codice» compare solo se Android dice che negli appunti c'è un codice (da Android 16).")
        }
      }
    }
    item(key = "pin") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Il PIN di riserva", detail = "Quattro cifre nascoste: il blocco dell'app.")
        Stage(backdrop) {
          FluidCodeField(state = pin, onComplete = { if (it != "2580") pin.fail() else pin.clear() }, secret = true,
            label = "PIN", pasteButton = false)
          Caption("Il PIN giusto è 2580: un altro trema e si svuota.")
        }
      }
    }
    item(key = "abbinamento") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Pampa Store", detail = "Il codice di abbinamento di un secondo telefono: lettere e cifre.")
        Stage(backdrop) {
          FluidCodeField(state = pairing, onComplete = {}, digitsOnly = false, label = "Codice di abbinamento")
        }
      }
    }
  }
}

// ================================================================================================================
// Il campo password
// ================================================================================================================

/** Le regole di Pampa Mail scritte come le Password Rules di Apple. */
internal const val F8p3MailRules =
  "minlength: 10; maxlength: 64; required: lower; required: upper; required: digit; required: [-().&@?'#,/\"+]; max-consecutive: 2"

@Composable
private fun PasswordScene(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p3Backdrop.Page) }
  var newPassword by remember { mutableStateOf("") }
  var mailPassword by remember { mutableStateOf("") }
  var signIn by remember { mutableStateOf("") }
  val mailRules = remember { FluidPasswordRules.parse(F8p3MailRules) }
  SceneScreen("Il campo password", "F8.3 · regole, forza, password forte, autocompilazione", onBack, backdrop, { backdrop = it }) {
    item(key = "classeviva") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "ClasseViva", detail = "Il cambio password, con le regole di serie: tocca il campo vuoto.")
        Stage(backdrop) {
          FluidPasswordField(value = newPassword, onValueChange = { newPassword = it }, label = "Nuova password")
        }
      }
    }
    item(key = "mail") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Pampa Mail", detail = "Le regole del servizio nella riga di Apple:")
        Text(F8p3MailRules, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        Stage(backdrop) {
          FluidPasswordField(value = mailPassword, onValueChange = { mailPassword = it }, rules = mailRules,
            label = "Password dell'account")
        }
      }
    }
    item(key = "accesso") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "L'accesso", detail = "Senza regole: solo l'occhio e l'autocompilazione della password.")
        Stage(backdrop) {
          FluidPasswordField(value = signIn, onValueChange = { signIn = it }, rules = null, label = "Password")
        }
      }
    }
  }
}

// ================================================================================================================
// Il valore riservato
// ================================================================================================================

@Composable
private fun PrivateScene(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p3Backdrop.Page) }
  var discreet by rememberSaveable { mutableStateOf(true) }
  SceneScreen("Il valore riservato", "F8.3 · il velo, il tocco, la ricopertura, le recenti", onBack, backdrop, { backdrop = it }) {
    item(key = "pampai") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "PampAI", detail = "Le chiavi dei servizi: si vedono solo al tocco e si ricoprono dopo 30 s.")
        Stage(backdrop) {
          PrivateKeyRow("OpenRouter", "sk-or-v1-7f3a9c2e41b8d605")
          PrivateKeyRow("Anthropic", "sk-ant-api03-Qm4x8LpZ2v")
        }
      }
    }
    item(key = "classeviva") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "ClasseViva", detail = "La modalità discreta vela tutti i voti insieme, in classe o sui mezzi.")
        FluidListGroup { FluidToggleRow(title = "Modalità discreta", checked = discreet, onCheckedChange = { discreet = it }) }
        CompositionLocalProvider(LocalFluidDiscreetMode provides discreet) {
          Stage(backdrop) {
            listOf("Matematica" to "8½", "Italiano" to "7", "Inglese" to "9").forEach { (subject, grade) ->
              val state = rememberFluidPrivateState()
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(subject, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(grade, style = MaterialTheme.typography.titleMedium,
                  modifier = Modifier.width(56.dp).fluidPrivate(state, discreetOnly = true))
              }
            }
          }
        }
      }
    }
    item(key = "mycloud") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "MyCloud Gallery", detail = "L'anteprima dell'album nascosto, col velo sfocato di Messaggi.")
        val state = rememberFluidPrivateState()
        Image(
          painter = painterResource(R.drawable.viewer_flower),
          contentDescription = "Album nascosto",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(ContinuousCornerShape(FluidRadius.Card))
            .fluidPrivate(state, veil = FluidPrivateVeil.Blur),
        )
      }
    }
  }
}

@Composable
private fun PrivateKeyRow(name: String, key: String) {
  val state = rememberFluidPrivateState()
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      key,
      style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
      modifier = Modifier.fillMaxWidth().fluidPrivate(state),
    )
  }
}

// ================================================================================================================
// La copia delicata
// ================================================================================================================

@Composable
private fun CopyScene(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p3Backdrop.Page) }
  var short by rememberSaveable { mutableStateOf(true) }
  val expiry = if (short) 10.seconds else 60.seconds
  SceneScreen(
    "La copia delicata", "F8.3 · riservata nell'anteprima, cancellata dopo il tempo", onBack, backdrop, { backdrop = it },
    extraControls = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidChip(label = "10 s", selected = short, onClick = { short = true })
        FluidChip(label = "60 s", selected = !short, onClick = { short = false })
      }
    },
  ) {
    item(key = "chiave") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "PampAI", detail = "La chiave del servizio: Android non la mostra nell'anteprima degli appunti.")
        Stage(backdrop) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("sk-or-v1-7f3a…d605", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
              modifier = Modifier.weight(1f))
            FluidCopyButton(text = { "sk-or-v1-7f3a9c2e41b8d605" }, label = "Copia", expireAfter = expiry)
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("IT60 X054 2811 1010 0000 0123 456", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FluidCopyButton(text = { "IT60X0542811101000000123456" }, expireAfter = expiry)
          }
          ClipboardStatus()
        }
      }
    }
  }
}

/** Lo stato degli appunti, letto dalla sola descrizione (niente avviso): vuoti, pieni, riservati. */
@Composable
private fun ClipboardStatus() {
  val context = LocalContext.current
  val manager = remember { context.getSystemService(ClipboardManager::class.java) }
  var status by remember { mutableStateOf("") }
  fun read() {
    val d = runCatching { manager?.primaryClipDescription }.getOrNull()
    status = when {
      d == null -> "Appunti: vuoti"
      d.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true -> "Appunti: un dato riservato"
      else -> "Appunti: pieni"
    }
  }
  DisposableEffect(manager) {
    read()
    val listener = ClipboardManager.OnPrimaryClipChangedListener { read() }
    manager?.addPrimaryClipChangedListener(listener)
    onDispose { manager?.removePrimaryClipChangedListener(listener) }
  }
  LaunchedEffect(Unit) { while (true) { delay(1_000); read() } }
  Caption(status)
}

// ================================================================================================================
// Il tasto incolla
// ================================================================================================================

@Composable
private fun PasteScene(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p3Backdrop.Page) }
  val context = LocalContext.current
  val manager = remember { context.getSystemService(ClipboardManager::class.java) }
  val code = rememberFluidCodeFieldState(6)
  var link by remember { mutableStateOf("") }
  var image by remember { mutableStateOf<Uri?>(null) }
  SceneScreen("Il tasto incolla", "F8.3 · solo per i dati concordati: codice, link, immagine", onBack, backdrop, { backdrop = it }) {
    item(key = "copie") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Cosa c'è negli appunti", detail = "Copia un dato di prova e guarda quale tasto compare.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidGlassButton("Un testo normale", onClick = {
            manager?.setPrimaryClip(ClipData.newPlainText("", "Ci vediamo alle otto davanti al cinema"))
          }, size = FluidGlassSize.Compact, maxLines = 1)
          FluidGlassButton("Un link", onClick = {
            manager?.setPrimaryClip(ClipData.newPlainText("", "https://pampa.example/album/estate-2026"))
          }, size = FluidGlassSize.Compact, maxLines = 1)
          FluidGlassButton("Un codice", onClick = {
            manager?.setPrimaryClip(ClipData.newPlainText("", F8p3RightCode))
          }, size = FluidGlassSize.Compact, maxLines = 1)
          FluidGlassButton("Un'immagine", onClick = {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.drawable.viewer_flower}")
            manager?.setPrimaryClip(ClipData(ClipDescription("Screenshot", arrayOf("image/jpeg")), ClipData.Item(uri)))
          }, size = FluidGlassSize.Compact, maxLines = 1)
        }
      }
    }
    item(key = "codice") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Un codice (OTP)", detail = "Il campo a codice ha il suo tasto incolla.")
        Stage(backdrop) { FluidCodeField(state = code, onComplete = {}, modifier = Modifier.fillMaxWidth()) }
      }
    }
    item(key = "link") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Un link (PampAI)", detail = "Il link da riassumere.")
        Stage(backdrop) {
          FluidTextField(value = link, onValueChange = { link = it }, placeholder = "https://…")
          FluidPasteButton(FluidPasteKind.Link, onPaste = { if (it is FluidPasted.Link) link = it.url })
        }
      }
    }
    item(key = "immagine") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Uno screenshot (MyCloud)", detail = "L'immagine da caricare nell'album.")
        Stage(backdrop) {
          val shown = image
          if (shown != null) {
            Image(
              painter = painterResource(R.drawable.viewer_flower),
              contentDescription = "Immagine incollata",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxWidth().height(120.dp).clip(ContinuousCornerShape(FluidRadius.Card)),
            )
          } else {
            Caption("Nessuna immagine: copia «Un'immagine» qui sopra.")
          }
          FluidPasteButton(FluidPasteKind.Image, onPaste = { if (it is FluidPasted.Image) image = it.uri })
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Il tasto legge solo la descrizione degli appunti (il tipo, e da Android 12 la classificazione del testo): " +
          "il contenuto si legge al tocco, così Android non mostra l'avviso prima. Fra Android 12 e 15 Android non dice se " +
          "un testo è un codice: lì il tasto del codice non compare.",
      )
    }
  }
}
