package dev.antigravity.fluidengine.sample.adaptive

import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel

/** Il tag di logcat della pagina: `adb logcat -s FluidAdattivo`. */
internal const val AdaptiveLogTag = "FluidAdattivo"

/**
 * Le sette scene della pagina «Adattività» (piano «iOS in Android», F2.6). Ognuna mette davanti una voce del backlog di
 * S3: [key] è il valore di `adaptive.scene`, [voice] cosa si guarda.
 */
internal enum class AdaptiveScene(val key: String, val title: String, val voice: String) {
  Flip("bianco-nero", "Bianco → nero sotto un vetro fermo", "il giro chiaro/scuro e l'inchiostro, senza che il vetro si muova"),
  Threshold("soglia", "La soglia", "a che quota di scuro il vetro piccolo gira, salendo e scendendo"),
  Checker("scacchiera", "La scacchiera", "il vetro vicino alla soglia non deve girare avanti e indietro"),
  Scroll("scorrimento", "Scorrimento su foto e testo", "il bordo dello scorrimento e il giro mentre si scorre"),
  Large("grandi", "I grandi", "un pop-up, un foglio e un pannello sopra chiaro e nero: scuriscono in continuo con la quota di nero e il contenuto li segue; un vetro dell'app resta del tema"),
  Cursor("cursore", "Il cursore dell'utente", "il vetro fra trasparente e colorato, coi due estremi a portata di tocco"),
  Variants("varianti", "Clear e tasto tinto", "il vetro di serie, il tinto e Clear sopra una foto chiara e una scura"),
  ;

  /** La scena dopo questa, in giro. */
  fun next(): AdaptiveScene = entries[(ordinal + 1) % entries.size]
}

/** I tre toni del fondo della prima scena: bianco, grigio medio, nero. */
internal enum class AdaptiveTone(val key: String, val title: String, val argb: Long) {
  White("bianco", "bianco", 0xFFFFFFFF),
  Grey("grigio", "grigio medio", 0xFF808080),
  Black("nero", "nero", 0xFF000000),
  ;

  companion object {
    private val cycle = listOf(White, Grey, Black, Grey)

    /** Il tono del cambio numero [step] nel giro bianco → grigio → nero → grigio → bianco (0 = bianco). */
    fun atStep(step: Int): AdaptiveTone = cycle[Math.floorMod(step, cycle.size)]
  }
}

/** Il tono dei riquadri scuri della scacchiera: quanto sono lontani dal bianco, quindi quanto il mezzo sta vicino alla soglia. */
internal enum class AdaptiveDark(val key: String, val title: String, val argb: Long) {
  Black("nero", "nero", 0xFF000000),
  Dark("scuro", "scuro 25%", 0xFF404040),
  Dim("grigio", "grigio 40%", 0xFF666666),
  ;

  fun next(): AdaptiveDark = entries[(ordinal + 1) % entries.size]
}

/**
 * Ciò che cambia la **scena** (e che quindi la fa rinascere): la scena, il tema, il vetro chiesto e la variante dello
 * scorrimento. Il fondo, la quota, il cursore e l'automatismo vivono in [AdaptiveLive]: cambiarli non deve rifare un
 * solo vetro, perché la prova è proprio un vetro **fermo** sopra un fondo che cambia.
 */
internal data class AdaptiveConfig(
  val scene: AdaptiveScene = AdaptiveScene.Flip,
  val dark: Boolean = false,
  val model: FluidGlassModel = FluidGlassModel.Liquid,
  /** Solo per lo scorrimento: i riquadri scuri sono alti e riempiono la zona della barra. */
  val fullBar: Boolean = false,
) {
  /** Una riga leggibile, per logcat e per il sottotitolo. */
  val key: String
    get() = "${scene.key} · ${if (dark) "scuro" else "chiaro"} · ${model.name}" + if (fullBar) " · barra piena" else ""
}

/**
 * Le richieste di lancio che **non** cambiano la scena: dove parte il fondo, la quota di scuro, il cursore e se la pagina
 * si muove da sola.
 */
internal data class AdaptiveStart(
  val tone: AdaptiveTone = AdaptiveTone.White,
  /** La quota di scuro, 0..1, per la soglia e per i grandi. */
  val share: Float = 0f,
  val cursor: Float = 0.5f,
  val dark: AdaptiveDark = AdaptiveDark.Black,
  /** Il lato di una casella della scacchiera, in dp. */
  val cell: Int = 32,
  val auto: Boolean = false,
  /** Solo per i grandi: quale grande è aperto alla partenza (F2.3b). */
  val large: AdaptiveLarge = AdaptiveLarge.Sheet,
)

/**
 * Il grande aperto alla partenza nella scena dei grandi (F2.3b). Uno alla volta: ognuno porta il suo velo sulla pagina, e
 * il secondo aperto coprirebbe il primo.
 */
internal enum class AdaptiveLarge(val key: String) {
  Sheet("foglio"),
  Popup("popup"),
  Panel("pannello"),
}

internal object AdaptiveLaunch {
  const val ExtraScene = "adaptive.scene"
  const val ExtraTheme = "adaptive.theme"
  const val ExtraModel = "adaptive.model"
  const val ExtraAuto = "adaptive.auto"
  const val ExtraTone = "adaptive.tone"
  const val ExtraShare = "adaptive.share"
  const val ExtraCursor = "adaptive.cursor"
  const val ExtraDark = "adaptive.dark"
  const val ExtraCell = "adaptive.cell"
  const val ExtraFullBar = "adaptive.fullbar"
  const val ExtraLarge = "adaptive.large"

  private val truthy = setOf("true", "1", "on", "yes", "si", "sì", "acceso")

  /**
   * Gli extra di lancio (solo nella build di debug). Un valore sconosciuto lascia quello di serie: una pagina che non
   * parte per un refuso in un comando di prova è peggio di una che parte di serie. [inheritedDark] è il tema della
   * galleria, [inherited] il vetro che chiede (`gallery.model`).
   */
  fun parse(
    inheritedDark: Boolean,
    inherited: FluidGlassModel,
    extra: (String) -> String?,
  ): Pair<AdaptiveConfig, AdaptiveStart> {
    fun value(key: String): String? = extra(key)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val scene = value(ExtraScene)?.let { name ->
      AdaptiveScene.entries.firstOrNull { it.key == name || it.name.lowercase() == name }
    } ?: AdaptiveScene.Flip
    val dark = when (value(ExtraTheme)) {
      "scuro", "dark" -> true
      "chiaro", "light" -> false
      else -> inheritedDark
    }
    val model = when (value(ExtraModel)) {
      "classic" -> FluidGlassModel.Classic
      "liquid" -> FluidGlassModel.Liquid
      else -> inherited
    }
    val config = AdaptiveConfig(scene, dark, model, fullBar = value(ExtraFullBar) in truthy)
    val start = AdaptiveStart(
      tone = value(ExtraTone)?.let { name -> AdaptiveTone.entries.firstOrNull { it.key == name } } ?: AdaptiveTone.White,
      share = value(ExtraShare)?.replace(',', '.')?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
      cursor = value(ExtraCursor)?.replace(',', '.')?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
      dark = value(ExtraDark)?.let { name -> AdaptiveDark.entries.firstOrNull { it.key == name } } ?: AdaptiveDark.Black,
      cell = value(ExtraCell)?.toIntOrNull()?.coerceIn(8, 96) ?: 32,
      auto = value(ExtraAuto) in truthy,
      large = value(ExtraLarge)?.let { name -> AdaptiveLarge.entries.firstOrNull { it.key == name } } ?: AdaptiveLarge.Sheet,
    )
    return config to start
  }
}

/**
 * Quanto di un vetro alto da [top] a [bottom] sta sopra una fascia scura che sale dal fondo fino a [edge] (tutti nella
 * stessa unità, y verso il basso): 0..1. Pura, per la riga `COPERTURA` che chi misura confronta con la quota in cui il
 * vetro gira.
 */
internal fun adaptiveCoverage(top: Float, bottom: Float, edge: Float): Float {
  val height = bottom - top
  if (!(height > 0f)) return 0f
  return ((bottom - edge) / height).coerceIn(0f, 1f)
}

/** Dove sta il bordo alto della fascia scura per una quota [share] di uno stage alto [stageHeight] (0 = niente, 1 = tutto). */
internal fun adaptiveEdge(share: Float, stageHeight: Float): Float = stageHeight * (1f - share.coerceIn(0f, 1f))
