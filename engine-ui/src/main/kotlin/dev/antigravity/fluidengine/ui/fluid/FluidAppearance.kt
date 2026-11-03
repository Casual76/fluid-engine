package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.fluidAccentPresets

/**
 * Quale accento e' scelto, nei termini in cui lo offre un selettore: **tre strade**, non il prodotto
 * di due campi di [EngineSettings].
 *
 * `EngineSettings` descrive l'accento con una modalita' ([AccentMode]) piu' un nome di preset che ha
 * senso solo in una delle tre, piu' un interruttore del colore dinamico che ha senso solo in un'altra.
 * Chi disegna un selettore ragiona per "quale pallino e' acceso", e ogni app se lo e' ricostruito
 * da se' — compreso il caso che costava un bug: `DYNAMIC` salvato su un telefono che non lo supporta
 * (o con il colore dinamico spento) non e' un pallino acceso, e il tema ripiega sul marchio.
 * [FluidAppearance.accentChoice] e [FluidAppearance.withAccent] sono quella traduzione, in un posto
 * solo e senza Compose.
 */
@Immutable
sealed interface FluidAccentChoice {
  /** Il colore dell'app: il marchio passato a `FluidTheme`. */
  data object Brand : FluidAccentChoice

  /** Quello dello sfondo del telefono (Android 12+). */
  data object Dynamic : FluidAccentChoice

  /** Uno dei preset del selettore, per nome (`AccentPreset.name`). */
  data class Preset(val name: String) : FluidAccentChoice
}

/**
 * Le parole della sezione "Aspetto".
 *
 * **In italiano di serie**, come `fluidLicensesSection`: un'app a una lingua non scrive niente, e
 * un'app tradotta le passa tutte. Lasciarne una sola al default e' una frase italiana in mezzo a una
 * pagina inglese, e nessuno se ne accorge finche' non si gira il telefono. La sezione e' una funzione
 * di `LazyListScope`, quindi non puo' leggere le risorse da se': le legge l'app, prima, e le passa.
 */
@Immutable
data class FluidAppearanceStrings(
  val title: String = "Aspetto",
  val themeSystem: String = "Sistema",
  val themeSystemDetail: String = "Segue le impostazioni del telefono.",
  val themeLight: String = "Chiaro",
  val themeDark: String = "Scuro",
  val pureBlack: String = "Nero assoluto",
  val pureBlackDetail: String = "Sfondi neri nel tema scuro, per gli schermi OLED.",
  val accent: String = "Accento",
  val accentDynamic: String = "Colori di sistema",
  val glass: String = "Vetro",
  val glassDetail: String = "Superfici di vetro dove la schermata le prevede.",
  val haptics: String = "Aptica",
  val hapticsDetail: String = "Le vibrazioni che accompagnano i tocchi.",
) {
  /** Il nome di una delle tre voci di tema. [ThemeMode.AMOLED] e' "Scuro": il nero e' un interruttore. */
  fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> themeSystem
    ThemeMode.LIGHT -> themeLight
    ThemeMode.DARK, ThemeMode.AMOLED -> themeDark
  }
}

/**
 * La traduzione fra quello che una pagina "Aspetto" mostra e [EngineSettings].
 *
 * Tutto puro, perche' sono le regole che le app sbagliavano e che si possono provare senza disegnare
 * niente. Un'app che ha un suo modello di impostazioni ma usa il selettore le chiama dal proprio
 * store (`store.update { FluidAppearance.withAccent(it, choice) }`); chi usa `EngineSettingsStore`
 * le ha gia' nella sezione.
 */
object FluidAppearance {

  /**
   * Le voci di tema che la sezione offre, nell'ordine in cui le disegna.
   *
   * Tre e non quattro: [ThemeMode.AMOLED] non e' un tema ma "scuro, con il nero vero", ed e' un
   * interruttore sotto di esse (`EngineSettings.amoledEnabled` dice la stessa cosa). Quattro righe
   * volevano dire che il nero era un'alternativa a "Scuro", e invece ne e' un modo.
   */
  val ThemeChoices: List<ThemeMode> = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

  /** La voce di tema che risulta scelta: `AMOLED` e' "Scuro". */
  fun themeChoice(settings: EngineSettings): ThemeMode =
    if (settings.themeMode == ThemeMode.AMOLED) ThemeMode.DARK else settings.themeMode

  /** Se il nero e' vero: `amoledEnabled` o il tema `AMOLED`, che producono la stessa tavolozza. */
  fun isPureBlack(settings: EngineSettings): Boolean =
    settings.amoledEnabled || settings.themeMode == ThemeMode.AMOLED

  /**
   * Il tema scelto, **senza perdere il nero** di chi lo aveva.
   *
   * Chi era in `AMOLED` e sceglie "Chiaro" non vuole il nero scuro quando tornera' al tema scuro:
   * `AMOLED` si piega in `amoledEnabled = true`, e il tema nuovo e' quello scelto. Chiedere
   * [ThemeMode.AMOLED] (che la sezione non offre) vuol dire scuro e nero.
   */
  fun withThemeMode(settings: EngineSettings, mode: ThemeMode): EngineSettings = settings.copy(
    themeMode = if (mode == ThemeMode.AMOLED) ThemeMode.DARK else mode,
    amoledEnabled = isPureBlack(settings) || mode == ThemeMode.AMOLED,
  )

  /**
   * Il nero acceso o spento.
   *
   * Spegnerlo da `AMOLED` porta a `DARK`: lasciare `AMOLED` con `amoledEnabled = false` darebbe un
   * interruttore spento sopra una tavolozza ancora nera.
   */
  fun withPureBlack(settings: EngineSettings, on: Boolean): EngineSettings = settings.copy(
    themeMode = if (settings.themeMode == ThemeMode.AMOLED) ThemeMode.DARK else settings.themeMode,
    amoledEnabled = on,
  )

  /**
   * Il nome del preset che il tema usera' davvero per [name]: lo stesso, a meno delle maiuscole, e se
   * non esiste il primo (la regola di `FluidTheme`). Un selettore che accende "niente" perche' il nome
   * salvato non c'e' piu' fra i preset dice una cosa falsa: il tema sta usando il primo.
   */
  fun presetName(name: String, presets: List<AccentPreset> = fluidAccentPresets): String {
    val pool = presets.ifEmpty { fluidAccentPresets }
    return (pool.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: pool.first()).name
  }

  /**
   * Quale pallino e' acceso.
   *
   * @param dynamicSupported se il telefono offre il colore dinamico (Android 12+). `DYNAMIC` salvato
   *   senza o con `dynamicColorEnabled = false` e' [FluidAccentChoice.Brand]: e' quello che il tema
   *   disegna, e un selettore deve dire quello che si vede.
   */
  fun accentChoice(
    settings: EngineSettings,
    presets: List<AccentPreset> = fluidAccentPresets,
    dynamicSupported: Boolean,
  ): FluidAccentChoice = when (settings.accentMode) {
    AccentMode.BRAND -> FluidAccentChoice.Brand
    AccentMode.DYNAMIC ->
      if (dynamicSupported && settings.dynamicColorEnabled) FluidAccentChoice.Dynamic else FluidAccentChoice.Brand
    AccentMode.CUSTOM_PRESET -> FluidAccentChoice.Preset(presetName(settings.customAccentName, presets))
  }

  /**
   * L'accento scelto, nei campi di [EngineSettings].
   *
   * Scegliere [FluidAccentChoice.Dynamic] **riaccende** `dynamicColorEnabled`: chi tocca "Colori di
   * sistema" vuole quelli, e un interruttore spento in un altro punto non deve farlo ripiegare in
   * silenzio sul marchio.
   */
  fun withAccent(settings: EngineSettings, choice: FluidAccentChoice): EngineSettings = when (choice) {
    FluidAccentChoice.Brand -> settings.copy(accentMode = AccentMode.BRAND)
    FluidAccentChoice.Dynamic -> settings.copy(accentMode = AccentMode.DYNAMIC, dynamicColorEnabled = true)
    is FluidAccentChoice.Preset -> settings.copy(
      accentMode = AccentMode.CUSTOM_PRESET,
      customAccentName = choice.name,
    )
  }

  /** Il nome da mostrare per la scelta: quello del marchio, "colori di sistema", o l'etichetta del preset. */
  fun accentLabel(
    choice: FluidAccentChoice,
    brandLabel: String,
    dynamicLabel: String,
    presets: List<AccentPreset> = fluidAccentPresets,
  ): String = when (choice) {
    FluidAccentChoice.Brand -> brandLabel
    FluidAccentChoice.Dynamic -> dynamicLabel
    is FluidAccentChoice.Preset ->
      presets.firstOrNull { it.name.equals(choice.name, ignoreCase = true) }?.label ?: choice.name
  }

  /**
   * Se accendere l'aptica deve far sentire subito un colpo.
   *
   * L'interruttore ha gia' "vibrato" nel momento del tocco, ma con l'aptica ancora spenta, cioe' per
   * niente: chi la accende non sentirebbe mai com'e' fatta. Il primo colpo parte quando
   * l'impostazione arriva (era `false`, adesso `true`); il primo valore letto non conta, altrimenti
   * ogni apertura della pagina con l'aptica accesa vibrerebbe.
   */
  fun hapticsPreview(previous: Boolean?, now: Boolean): Boolean = previous == false && now

  /**
   * Le righe del gruppo degli interruttori, nell'ordine in cui si disegnano (3.0.4): il vetro (solo se l'app lo
   * ha dato) e l'aptica. Vuoto quando non ce n'e' nessuna: il gruppo non si disegna, perche' un contenitore
   * senza righe e' un rettangolo vuoto con un margine.
   */
  fun switchRows(glassRow: Boolean, haptics: Boolean): List<FluidAppearanceSwitch> = buildList {
    if (glassRow) add(FluidAppearanceSwitch.Glass)
    if (haptics) add(FluidAppearanceSwitch.Haptics)
  }
}

/** Una riga del gruppo degli interruttori di `fluidAppearanceSection`. Vedi [FluidAppearance.switchRows]. */
enum class FluidAppearanceSwitch { Glass, Haptics }

/**
 * Il colore che il telefono darebbe con i colori dinamici, o `null` se non li offre.
 *
 * Il pallino "Colori di sistema" mostra **il colore vero** e non una parola: scegliere e' guardare, e
 * un pallino grigio che dice "dinamico" non dice cosa si ottiene. Sotto Android 12 non c'e', e il
 * pallino non si disegna.
 */
@Composable
internal fun fluidDynamicAccentColor(isDark: Boolean): Color? {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
  val context = LocalContext.current
  return if (isDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
}

/**
 * Il selettore dell'accento: una fila di pallini, il marchio dell'app, i colori di sistema e i preset.
 *
 * Questa e' la forma a [FluidAccentChoice], per un'app che ha un suo modello di impostazioni (come
 * ClasseViva, che salva l'accento in un `AppSettings` suo e traduce al bordo); l'altra, a
 * [EngineSettings], e' per chi usa lo store dell'engine.
 *
 * Ogni pallino e' un `FluidColorDot`: bersaglio da 48 dp, ruolo `RadioButton` e nome per chi legge lo
 * schermo, perche' un colore da solo non e' un'etichetta. Il gruppo e' un `selectableGroup`, e a
 * capo va da se' se non ci stanno tutti.
 *
 * @param dynamicColor il colore di sistema; `null` nasconde il pallino (di serie: lo chiede al
 *   telefono, e sotto Android 12 non c'e').
 * @param brandLabel il nome del marchio: lo e' per chi legge lo schermo, e per la sezione che lo scrive.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluidAccentPicker(
  selected: FluidAccentChoice,
  onSelect: (FluidAccentChoice) -> Unit,
  modifier: Modifier = Modifier,
  brand: AccentPreset = FluidDefaultBrand,
  presets: List<AccentPreset> = fluidAccentPresets,
  dynamicColor: Color? = fluidDynamicAccentColor(GlassDefaults.isDarkSurface()),
  brandLabel: String = brand.label,
  dynamicLabel: String = FluidAppearanceStrings().accentDynamic,
) {
  // Lo dice il tema, non la luminanza del fondo: con il fondo trasparente (un'immagine dietro la
  // pagina) la luminanza e' zero, e ogni pallino sarebbe quello scuro.
  val isDark = GlassDefaults.isDarkSurface()
  FlowRow(
    modifier = modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(AccentDotGap),
    verticalArrangement = Arrangement.spacedBy(AccentDotGap),
  ) {
    FluidColorDot(
      color = brand.resolve(isDark),
      selected = selected == FluidAccentChoice.Brand,
      onClick = { onSelect(FluidAccentChoice.Brand) },
      label = brandLabel,
    )
    if (dynamicColor != null) {
      FluidColorDot(
        color = dynamicColor,
        selected = selected == FluidAccentChoice.Dynamic,
        onClick = { onSelect(FluidAccentChoice.Dynamic) },
        label = dynamicLabel,
      )
    }
    presets.forEach { preset ->
      FluidColorDot(
        color = preset.resolve(isDark),
        selected = selected is FluidAccentChoice.Preset && selected.name.equals(preset.name, ignoreCase = true),
        onClick = { onSelect(FluidAccentChoice.Preset(preset.name)) },
        label = preset.label,
      )
    }
  }
}

/**
 * Il selettore dell'accento che parla [EngineSettings]: sceglie il pallino, restituisce le
 * impostazioni nuove (e non le chiama se non cambia niente).
 */
@Composable
fun FluidAccentPicker(
  settings: EngineSettings,
  onSettingsChange: (EngineSettings) -> Unit,
  modifier: Modifier = Modifier,
  brand: AccentPreset = FluidDefaultBrand,
  presets: List<AccentPreset> = fluidAccentPresets,
  dynamicColor: Color? = fluidDynamicAccentColor(GlassDefaults.isDarkSurface()),
  brandLabel: String = brand.label,
  dynamicLabel: String = FluidAppearanceStrings().accentDynamic,
) {
  FluidAccentPicker(
    selected = FluidAppearance.accentChoice(settings, presets, dynamicSupported = dynamicColor != null),
    onSelect = { choice ->
      val next = FluidAppearance.withAccent(settings, choice)
      if (next != settings) onSettingsChange(next)
    },
    modifier = modifier,
    brand = brand,
    presets = presets,
    dynamicColor = dynamicColor,
    brandLabel = brandLabel,
    dynamicLabel = dynamicLabel,
  )
}

/**
 * La sezione "Aspetto" di una pagina di impostazioni: tema, nero assoluto, accento, aptica e (se
 * l'app ce l'ha) vetro, gia' mappati su [EngineSettings].
 *
 * Una estensione di `LazyListScope` e non una schermata, per la stessa ragione di
 * `fluidLicensesSection`: ogni app ha gia' la sua pagina "Impostazioni", con la sua testata e il suo
 * ordine, e una seconda schermata sarebbe un posto in piu' in cui andare e dimenticarsi. Piu' app
 * avevano riscritto la stessa pagina — chi con righe a spunta, chi con un controllo a segmenti, chi
 * con un tasto per ogni preset — e la differenza fra una versione e l'altra era quasi tutta nei bug.
 *
 * Cosa fa, in tre gruppi dentro una testata:
 *
 *  * **tema**: Sistema / Chiaro / Scuro come righe di scelta (spunta a destra), e sotto "Nero
 *    assoluto", un interruttore, spento nel tema chiaro dove non ha effetto. `AMOLED` si legge come
 *    "Scuro" + nero acceso: vedi [FluidAppearance.withThemeMode];
 *  * **accento**: il nome del colore scelto e il [FluidAccentPicker] sotto;
 *  * **aptica** (e **vetro**, se passi [glassEnabled]): due interruttori. Accendere l'aptica fa
 *    sentire subito un colpo, perche' l'interruttore ha vibrato quand'era ancora spenta.
 *
 * **Il vetro non e' un campo di [EngineSettings]**, di proposito: dice se *quella schermata* ha un
 * canvas ambientale e vetro nel contenuto, ed e' una scelta dell'app (alcune ne hanno un livello, altre
 * un automatico). La riga compare solo se passi insieme [glassEnabled] e [onGlassEnabledChange]; il
 * resto e' tuo da cablare.
 *
 * [onSettingsChange] riceve l'[EngineSettings] **intero e nuovo**, e non viene chiamato se la scelta
 * non cambia niente: l'app lo scrive nel suo store (`EngineSettingsStore.update { next }`) e il tema,
 * che gia' lo osserva, fa il resto. Le parole stanno in [strings] e sono in italiano di serie.
 *
 * @param glass le tre liste in vetro, se la schermata ha un canvas ambientale (come `FluidListGroup`).
 * @param dynamicAccent offrire il pallino dei colori di sistema (dove il telefono li ha).
 * @param footnote una nota sotto la sezione, per la spiegazione che un sottotitolo farebbe troppo lunga.
 * @param keyPrefix il prefisso delle chiavi degli item, se la pagina ha due sezioni di questo tipo.
 * @param title la testata della sezione; di serie `strings.title`. Null (3.0.1): niente testata, per una
 *   pagina che si chiama gia' «Aspetto», dove la testata ripeteva il titolo della pagina.
 * @param pureBlack la riga «Nero assoluto» sotto il tema (3.0.4); falso per un'app che non lo offre.
 * @param accent il gruppo dell'accento, con il suo selettore (3.0.4); falso per un'app con un colore solo.
 * @param haptics la riga dell'aptica (3.0.4); falso per un'app che l'ha gia' altrove. Se non resta nessuna riga
 *   nel gruppo (niente aptica e niente vetro) il gruppo non si disegna.
 */
fun LazyListScope.fluidAppearanceSection(
  settings: EngineSettings,
  onSettingsChange: (EngineSettings) -> Unit,
  brand: AccentPreset = FluidDefaultBrand,
  presets: List<AccentPreset> = fluidAccentPresets,
  glassEnabled: Boolean? = null,
  onGlassEnabledChange: ((Boolean) -> Unit)? = null,
  strings: FluidAppearanceStrings = FluidAppearanceStrings(),
  glass: Boolean = false,
  dynamicAccent: Boolean = true,
  footnote: String? = null,
  keyPrefix: String = "fluid-appearance",
  title: String? = strings.title,
  pureBlack: Boolean = true,
  accent: Boolean = true,
  haptics: Boolean = true,
) {
  // Una modifica che non cambia niente non e' una modifica: lo store non la riscrive e il flusso non
  // si muove per niente. Tocca la riga gia' scelta, ed e' il caso piu' comune di tutti.
  val emit: (EngineSettings) -> Unit = { next -> if (next != settings) onSettingsChange(next) }

  if (title != null) {
    item(key = "$keyPrefix-header") {
      FluidSectionHeader(title = title)
    }
  }
  item(key = "$keyPrefix-theme") {
    val choice = FluidAppearance.themeChoice(settings)
    FluidListGroup(modifier = Modifier.selectableGroup(), glass = glass) {
      FluidAppearance.ThemeChoices.forEachIndexed { index, mode ->
        if (index > 0) FluidListDivider()
        FluidChoiceRow(
          title = strings.themeLabel(mode),
          subtitle = if (mode == ThemeMode.SYSTEM) strings.themeSystemDetail else "",
          selected = mode == choice,
          onSelect = { emit(FluidAppearance.withThemeMode(settings, mode)) },
        )
      }
      if (pureBlack) {
        FluidListDivider()
        FluidToggleRow(
          title = strings.pureBlack,
          subtitle = strings.pureBlackDetail,
          checked = FluidAppearance.isPureBlack(settings),
          onCheckedChange = { emit(FluidAppearance.withPureBlack(settings, it)) },
          // Nel tema chiaro il nero non ha dove andare: l'interruttore c'e', ma non risponde.
          enabled = choice != ThemeMode.LIGHT,
        )
      }
    }
  }
  if (accent) item(key = "$keyPrefix-accent") {
    val dynamicColor = if (dynamicAccent) fluidDynamicAccentColor(GlassDefaults.isDarkSurface()) else null
    val chosen = FluidAppearance.accentChoice(settings, presets, dynamicSupported = dynamicColor != null)
    FluidListGroup(glass = glass) {
      FluidListRow(
        title = strings.accent,
        subtitle = FluidAppearance.accentLabel(chosen, brand.label, strings.accentDynamic, presets),
      )
      Box(modifier = Modifier.padding(start = AccentPickerInset, end = 16.dp, bottom = 8.dp)) {
        FluidAccentPicker(
          selected = chosen,
          onSelect = { emit(FluidAppearance.withAccent(settings, it)) },
          brand = brand,
          presets = presets,
          dynamicColor = dynamicColor,
          dynamicLabel = strings.accentDynamic,
        )
      }
    }
  }
  val switchRows = FluidAppearance.switchRows(
    glassRow = glassEnabled != null && onGlassEnabledChange != null,
    haptics = haptics,
  )
  if (switchRows.isNotEmpty()) item(key = "$keyPrefix-switches") {
    FluidListGroup(glass = glass) {
      switchRows.forEachIndexed { index, row ->
        if (index > 0) FluidListDivider()
        when (row) {
          FluidAppearanceSwitch.Glass -> if (glassEnabled != null && onGlassEnabledChange != null) {
            FluidToggleRow(
              title = strings.glass,
              subtitle = strings.glassDetail,
              checked = glassEnabled,
              onCheckedChange = onGlassEnabledChange,
            )
          }
          FluidAppearanceSwitch.Haptics -> {
            FluidToggleRow(
              title = strings.haptics,
              subtitle = strings.hapticsDetail,
              checked = settings.hapticsEnabled,
              onCheckedChange = { emit(settings.copy(hapticsEnabled = it)) },
            )
            FluidHapticsPreview(enabled = settings.hapticsEnabled)
          }
        }
      }
    }
  }
  if (footnote != null) {
    item(key = "$keyPrefix-footnote") {
      FluidSectionFootnote(text = footnote)
    }
  }
}

/** Il colpo di prova quando l'aptica si accende. Non disegna niente. Vedi [FluidAppearance.hapticsPreview]. */
@Composable
private fun FluidHapticsPreview(enabled: Boolean) {
  val haptics = rememberFluidHaptics()
  var previous by remember { mutableStateOf<Boolean?>(null) }
  LaunchedEffect(enabled) {
    if (FluidAppearance.hapticsPreview(previous, enabled)) haptics.play(FluidHapticEvent.ToggleOn)
    previous = enabled
  }
}

/** Lo spazio fra due pallini: i bersagli sono gia' da 48 dp, e quello che si vede fra i dischi e' quasi trenta. */
private val AccentDotGap = 4.dp

/**
 * Dove comincia la fila: il margine di una riga (16 dp) meno l'aria di un pallino dentro il suo
 * bersaglio ((48 - 30) / 2 = 9 dp), cosi' il primo disco sta allineato al testo sopra.
 */
private val AccentPickerInset = 7.dp
