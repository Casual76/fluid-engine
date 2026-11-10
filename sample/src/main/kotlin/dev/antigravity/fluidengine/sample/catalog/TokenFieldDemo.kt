package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.MatrixTextScale
import dev.antigravity.fluidengine.sample.matrixDemoSettings
import dev.antigravity.fluidengine.sample.rememberMatrixFlag
import dev.antigravity.fluidengine.ui.fluid.*

/** Pagina «Campo a gettoni» della galleria: varianti chiare/scure, testo al 200% e movimento ridotto selezionabili. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenFieldDemo() {
  // Nella galleria gli interruttori partono dalla matrice degli stati (e restano liberi); fuori, da spenti.
  var dark by rememberMatrixFlag { it.theme.isDark }
  var large by rememberMatrixFlag { it.textScale == MatrixTextScale.Largest }
  var reduced by rememberMatrixFlag { it.reducedMotion }
  val density = androidx.compose.ui.platform.LocalDensity.current
  dev.antigravity.fluidengine.ui.theme.FluidTheme(settings = matrixDemoSettings(dark)) {
    CompositionLocalProvider(
      androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, if (large) 2f else density.fontScale),
      LocalFluidMotionPolicy provides FluidMotionPolicy(if (reduced) 0f else 1f),
    ) {
      Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          FluidChip("Scuro", dark, { dark = !dark })
          FluidChip("200%", large, { large = !large })
          FluidChip("Moto ridotto", reduced, { reduced = !reduced })
        }
        Box(Modifier.weight(1f)) { TokenFieldExamples() }
      }
    }
  }
}

@Composable
private fun TokenFieldExamples() {
  var value by rememberSaveable(stateSaver = FluidTokenFieldValue.Saver) { mutableStateOf(FluidTokenFieldValue()) }
  val create: (String, List<FluidToken>) -> FluidToken? = { raw, existing ->
    if (existing.any { it.id == raw }) null else FluidToken(raw, raw, raw.startsWith("!"))
  }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Gettoni · anteponi ! per segnare un errore")
    FluidTokenField(value, { value = it }, create, label = "Etichette", placeholder = "Aggiungi etichette",
      suggestions = listOf(FluidTokenSuggestion("studio", "Studio", "Etichetta suggerita", leading = { Text("S") }),
        FluidTokenSuggestion("casa", "Casa", "Un altro suggerimento")),
      onSuggestionClick = { suggestion ->
        if (value.tokens.none { it.id == suggestion.id }) value = value.copy(
          tokens = value.tokens + FluidToken(suggestion.id, suggestion.title), draft = androidx.compose.ui.text.input.TextFieldValue())
      }, onEditToken = { token -> value = value.copy(tokens = value.tokens.filterNot { it.id == token.id },
        draft = androidx.compose.ui.text.input.TextFieldValue(token.label)) })
    Text("Valido, non valido, testo lungo e RTL")
    var examples by rememberSaveable(stateSaver = FluidTokenFieldValue.Saver) { mutableStateOf(FluidTokenFieldValue(listOf(
      FluidToken("ok", "Valido"), FluidToken("bad", "Non valido", true),
      FluidToken("long", "Una etichetta molto lunga che deve restare contenuta anche al duecento per cento"),
      FluidToken("rtl", "مرحبا بالعالم")))) }
    FluidTokenField(examples, { examples = it }, create, label = "Esempi")
    var many by rememberSaveable(stateSaver = FluidTokenFieldValue.Saver) { mutableStateOf(FluidTokenFieldValue(
      (1..100).map { FluidToken("$it", "Gettone $it") })) }
    Text("Cento gettoni · scorrimento nella pagina")
    FluidTokenField(many, { many = it }, create, label = "Molti gettoni")
    Text("Nessun suggerimento: la lista rimane vuota senza sottrarre spazio al campo")
  }
}
