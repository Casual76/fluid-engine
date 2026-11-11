package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.richtext.FluidRichTextLabels
import dev.antigravity.fluidengine.richtext.LocalFluidRichTextLabels
import dev.antigravity.fluidengine.ui.fluid.FluidAppearance
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Tune

// ======================================================================================================
// La matrice degli stati della galleria (F1.6)
//
// Una demo di componente si giudica in sei modi, e quattro di questi non dipendono dalla demo: il tema (chiaro,
// scuro, nero), la scala del testo (1,0 / 1,3 / 2,0), il movimento (normale o ridotto) e la lingua (italiano o
// inglese). Il quinto — i dati (con dati, vuoto, caricamento, errore) — dipende dalla demo, e vale dove la demo lo
// prevede. Il sesto e' la direzione di scrittura, che le demo che lo richiedono mostrano da se'.
//
// Questo file ha tutto quello che serve a non scrivere quei controlli dieci volte: lo stato ([GalleryMatrix]), la
// sua fotografia che le demo possono leggere ([MatrixSnapshot], [LocalGalleryMatrix]), il modo in cui si applica a
// una pagina ([MatrixScope]) e il pannello che lo cambia ([MatrixPanel]).
// ======================================================================================================

/**
 * I tre temi della matrice. Il tema e' **delle impostazioni** della galleria (le stesse della scheda
 * Impostazioni › Aspetto), non della matrice: cambiarlo qui lo cambia anche li', come in un'app vera, dove il tema
 * e' uno solo e lo seguono anche la barra, i menu e i fogli.
 */
internal enum class MatrixTheme(val label: String) {
  Light("Chiaro"),
  Dark("Scuro"),

  /** Scuro con il nero vero (`amoledEnabled`): le superfici di vetro sul nero sono il caso piu' duro. */
  Black("Nero"),
  ;

  val isDark: Boolean get() = this != Light

  /** Le impostazioni che dicono questo tema, tenendo tutto il resto (accento, aptica) com'e'. */
  fun applyTo(settings: EngineSettings): EngineSettings = when (this) {
    Light -> FluidAppearance.withThemeMode(settings, ThemeMode.LIGHT)
    Dark -> FluidAppearance.withPureBlack(FluidAppearance.withThemeMode(settings, ThemeMode.DARK), on = false)
    Black -> FluidAppearance.withPureBlack(FluidAppearance.withThemeMode(settings, ThemeMode.DARK), on = true)
  }

  companion object {
    /** Il tema che [settings] dice (con [systemDark] per `SYSTEM`): il nero conta solo da scuro. */
    fun of(settings: EngineSettings, systemDark: Boolean): MatrixTheme {
      val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
      }
      return when {
        !dark -> Light
        FluidAppearance.isPureBlack(settings) -> Black
        else -> Dark
      }
    }
  }
}

/** Le tre scale del testo: la `Density.fontScale` del contenuto, non quella del sistema. */
internal enum class MatrixTextScale(val label: String, val scale: Float) {
  Normal("1,0×", 1f),
  Medium("1,3×", 1.3f),
  Largest("2,0×", 2f),
}

/** La lingua delle etichette dell'engine (`FluidLabels`, quelle dell'editor): i testi di prova restano in italiano. */
internal enum class MatrixLanguage(val label: String) {
  Italian("Italiano"),
  English("English"),
}

/** Gli stati dei dati, per le demo che li prevedono (le liste): con dati, vuoto, caricamento, errore. */
internal enum class MatrixData(val label: String) {
  Content("Con dati"),
  Empty("Vuoto"),
  Loading("Caricamento"),
  Error("Errore"),
}

/**
 * La matrice com'e' in un dato momento, **con il tema gia' risolto**: e' quello che le demo leggono da
 * [LocalGalleryMatrix] e quello che [MatrixScope] applica.
 *
 * @property settings le impostazioni della galleria, per le demo che si fanno un tema proprio: partono da queste
 *   e non dai default, cosi' l'accento scelto in Impostazioni arriva anche li'.
 */
@Immutable
internal data class MatrixSnapshot(
  val theme: MatrixTheme = MatrixTheme.Light,
  val textScale: MatrixTextScale = MatrixTextScale.Normal,
  val reducedMotion: Boolean = false,
  val language: MatrixLanguage = MatrixLanguage.Italian,
  val data: MatrixData = MatrixData.Content,
  val settings: EngineSettings = EngineSettings(),
) {
  /** Nessun asse mosso (il tema no: e' globale). Il tasto della matrice si accende quando non lo e'. */
  val isDefault: Boolean
    get() = textScale == MatrixTextScale.Normal && !reducedMotion &&
      language == MatrixLanguage.Italian && data == MatrixData.Content

  /** Una riga che dice com'e': il tema sempre, il resto solo se non e' di serie. */
  val summary: String
    get() = buildList {
      add(theme.label)
      if (textScale != MatrixTextScale.Normal) add("testo ${textScale.label}")
      if (reducedMotion) add("movimento ridotto")
      if (language != MatrixLanguage.Italian) add(language.label)
      if (data != MatrixData.Content) add(data.label.lowercase())
    }.joinToString(" · ")
}

/**
 * Lo stato della matrice, alla radice della galleria (come [GalleryTools]): sopravvive al cambio di pagina, cosi'
 * un 200% scelto su una pagina resta sulla successiva. Il tema non e' qui, e' in [EngineSettings].
 */
@Stable
internal class GalleryMatrix(
  textScale: MatrixTextScale = MatrixTextScale.Normal,
  reducedMotion: Boolean = false,
  language: MatrixLanguage = MatrixLanguage.Italian,
  data: MatrixData = MatrixData.Content,
) {
  var textScale by mutableStateOf(textScale)
  var reducedMotion by mutableStateOf(reducedMotion)
  var language by mutableStateOf(language)
  var data by mutableStateOf(data)

  fun snapshot(settings: EngineSettings, systemDark: Boolean): MatrixSnapshot = MatrixSnapshot(
    theme = MatrixTheme.of(settings, systemDark),
    textScale = textScale,
    reducedMotion = reducedMotion,
    language = language,
    data = data,
    settings = settings,
  )

  /** Rimette di serie gli assi che sono della matrice (il tema lo rimette chi ha le impostazioni). */
  fun reset() {
    textScale = MatrixTextScale.Normal
    reducedMotion = false
    language = MatrixLanguage.Italian
    data = MatrixData.Content
  }
}

/**
 * La matrice in vigore, per le demo che hanno **interruttori propri** (tema, testo, movimento, lingua) e che
 * quindi non seguirebbero da sole quello che la galleria ha scelto. Nulla fuori dalla matrice (anteprime, test
 * di un pezzo): in quel caso le demo si comportano come prima.
 *
 * Si legge con [rememberMatrixFlag] e [matrixDemoSettings], mai direttamente: sono loro a far partire
 * l'interruttore dal valore della matrice e a lasciarlo libero finche' la matrice non cambia.
 */
internal val LocalGalleryMatrix = staticCompositionLocalOf<MatrixSnapshot?> { null }

/** La fotografia della matrice per [settings]: legge se il sistema e' scuro per il tema `SYSTEM`. */
@Composable
internal fun rememberMatrixSnapshot(matrix: GalleryMatrix, settings: EngineSettings): MatrixSnapshot {
  val systemDark = isSystemInDarkTheme()
  return matrix.snapshot(settings, systemDark)
}

/**
 * Applica la matrice a [content]: la scala del testo (`LocalDensity`), il movimento (`LocalFluidMotionPolicy` a
 * scala 0), la lingua (`LocalFluidLabels` e le etichette dell'editor) e [LocalGalleryMatrix]. Il tema no: arriva
 * dalle impostazioni, che stanno sopra il `FluidTheme` della radice.
 *
 * Con [active] falso non fa niente — non una scala 1,0 che sovrascrive quella del sistema, ma proprio niente — e
 * le altre schede e le pagine di prima restano com'erano. Va attorno al **contenuto e agli host** della radice
 * (modali, menu, avvisi), perche' un menu aperto da una demo al 200% si compone nell'host, e li' non e' al 200% se
 * l'host non e' dentro: non attorno alla barra delle schede, che e' chrome dell'app e non contenuto.
 */
@Composable
internal fun MatrixScope(snapshot: MatrixSnapshot, active: Boolean, content: @Composable () -> Unit) {
  if (!active) {
    content()
    return
  }
  val density = LocalDensity.current
  val english = snapshot.language == MatrixLanguage.English
  val labels = remember(english) { if (english) FluidLabels.english() else FluidLabels() }
  val richTextLabels = remember(english) { if (english) FluidRichTextLabels.english() else FluidRichTextLabels() }
  val motion = if (snapshot.reducedMotion) FluidMotionPolicy(durationScale = 0f) else LocalFluidMotionPolicy.current
  CompositionLocalProvider(
    LocalGalleryMatrix provides snapshot,
    LocalDensity provides Density(density.density, snapshot.textScale.scale),
    LocalFluidMotionPolicy provides motion,
    LocalFluidLabels provides labels,
    LocalFluidRichTextLabels provides richTextLabels,
    content = content,
  )
}

/**
 * L'interruttore di una demo che ne ha uno suo, **fatto partire dalla matrice**.
 *
 * Parte da [read] della matrice (o da falso fuori dalla galleria) e resta libero: toccare l'interruttore della
 * demo lo cambia come sempre. Quando e' la matrice a cambiare, l'interruttore riparte dal suo valore: l'ultimo
 * che ha parlato vince. Sostituisce `remember { mutableStateOf(false) }` e basta.
 */
@Composable
internal fun rememberMatrixFlag(read: (MatrixSnapshot) -> Boolean): MutableState<Boolean> {
  val seed = LocalGalleryMatrix.current?.let(read) ?: false
  return rememberSaveable(seed) { mutableStateOf(seed) }
}

/**
 * Le impostazioni del tema di una demo che se lo fa da se' (`FluidTheme(...)` dentro la demo): chiaro o scuro
 * secondo [dark], **col nero se la matrice dice nero** e con l'accento delle impostazioni della galleria. Fuori
 * dalla galleria sono quelle di sempre, `EngineSettings(themeMode = LIGHT|DARK)`.
 */
@Composable
internal fun matrixDemoSettings(dark: Boolean): EngineSettings {
  val snapshot = LocalGalleryMatrix.current
  val black = snapshot?.theme == MatrixTheme.Black
  val base = snapshot?.settings
  return remember(dark, black, base) {
    val theme = when {
      !dark -> MatrixTheme.Light
      black -> MatrixTheme.Black
      else -> MatrixTheme.Dark
    }
    theme.applyTo(base ?: EngineSettings())
  }
}

// --- Il tasto ------------------------------------------------------------------------------------------

/** La frase che TalkBack legge sul tasto: dice anche com'e' la matrice. */
internal fun matrixButtonDescription(snapshot: MatrixSnapshot): String =
  "Matrice degli stati: ${snapshot.summary}"

/**
 * Il tasto della matrice come azione della barra di una [FluidScreen][dev.antigravity.fluidengine.ui.fluid.FluidScreen]
 * (le pagine che la galleria compone da se'). Un pallino dice che qualcosa non e' di serie.
 */
@Composable
internal fun MatrixBarAction(snapshot: MatrixSnapshot, onClick: () -> Unit) {
  // Il tag sta sul contenitore: il tasto della barra azzera la semantica dei suoi figli, tag compreso.
  Box(modifier = Modifier.testTag(MatrixButtonTag)) {
    FluidBarAction(
      icon = FluidIcons.Tune,
      contentDescription = matrixButtonDescription(snapshot),
      onClick = onClick,
    )
    if (!snapshot.isDefault) FluidBadge(modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
  }
}

/**
 * Lo stesso tasto per le pagine che hanno la barra **dentro la demo** (la demo e' una `FluidScreen` o una colonna
 * a tutto schermo): galleggia nello stesso punto, con la stessa lente, sul fondale che la pagina registra.
 */
@Composable
internal fun MatrixOverlayButton(
  snapshot: MatrixSnapshot,
  onClick: () -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    FluidGlassIconButton(
      onClick = onClick,
      backdrop = backdrop,
      modifier = Modifier.testTag(MatrixButtonTag),
    ) {
      FluidIcon(
        imageVector = FluidIcons.Tune,
        contentDescription = matrixButtonDescription(snapshot),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
    }
    if (!snapshot.isDefault) FluidBadge(modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
  }
}

internal const val MatrixButtonTag = "matrix-button"
internal const val MatrixPanelTag = "matrix-panel"

// --- Il pannello ---------------------------------------------------------------------------------------

/**
 * Il contenuto del foglio della matrice: cinque scelte e «Ripristina».
 *
 * Il foglio sta nell'host dei modali della radice, che e' **dentro** [MatrixScope]: senza accorgimenti si
 * leggerebbe al 200% anche lui, e scegliere "1,0×" da un pannello al 200% e' una prova di pazienza. Per questo
 * il contenuto rimette la densita' [baseDensity] (quella della radice) e le etichette italiane: la matrice si
 * legge sempre allo stesso modo, qualunque cosa stia comandando.
 *
 * @param supportsData la pagina ha stati dei dati (vuoto, caricamento, errore): la riga compare solo allora.
 * @param note cosa la pagina fa e non fa con la matrice, in una frase.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatrixPanel(
  snapshot: MatrixSnapshot,
  matrix: GalleryMatrix,
  onSettingsChange: (EngineSettings) -> Unit,
  baseDensity: Density,
  supportsData: Boolean,
  note: String,
) {
  CompositionLocalProvider(LocalDensity provides baseDensity, LocalFluidLabels provides FluidLabels()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 34.dp)
        .testTag(MatrixPanelTag),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Matrice degli stati",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = note,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      MatrixRow("Tema") {
        FluidSegmentedControl(
          options = MatrixTheme.entries,
          selected = snapshot.theme,
          onSelect = { onSettingsChange(it.applyTo(snapshot.settings)) },
          label = { it.label },
          modifier = Modifier.testTag("matrix-theme"),
        )
      }
      MatrixRow("Scala del testo") {
        FluidSegmentedControl(
          options = MatrixTextScale.entries,
          selected = snapshot.textScale,
          onSelect = { matrix.textScale = it },
          label = { it.label },
          modifier = Modifier.testTag("matrix-text"),
        )
      }
      MatrixRow("Movimento") {
        FluidSegmentedControl(
          options = listOf(false, true),
          selected = snapshot.reducedMotion,
          onSelect = { matrix.reducedMotion = it },
          label = { if (it) "Ridotto" else "Normale" },
          modifier = Modifier.testTag("matrix-motion"),
        )
      }
      MatrixRow("Lingua delle etichette") {
        FluidSegmentedControl(
          options = MatrixLanguage.entries,
          selected = snapshot.language,
          onSelect = { matrix.language = it },
          label = { it.label },
          modifier = Modifier.testTag("matrix-language"),
        )
      }
      if (supportsData) {
        MatrixRow("Dati") {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("matrix-data"),
          ) {
            MatrixData.entries.forEach { state ->
              FluidChip(
                label = state.label,
                selected = snapshot.data == state,
                onClick = { matrix.data = state },
              )
            }
          }
        }
      }
      FluidButton(
        text = "Ripristina",
        onClick = {
          matrix.reset()
          // Il tema di partenza della galleria e' quello del sistema.
          onSettingsChange(
            FluidAppearance.withPureBlack(
              FluidAppearance.withThemeMode(snapshot.settings, ThemeMode.SYSTEM),
              on = false,
            ),
          )
        },
        style = FluidButtonStyle.Tinted,
        enabled = !snapshot.isDefault || snapshot.settings.themeMode != ThemeMode.SYSTEM,
        modifier = Modifier.testTag("matrix-reset"),
      )
    }
  }
}

@Composable
private fun MatrixRow(title: String, control: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    control()
  }
}
