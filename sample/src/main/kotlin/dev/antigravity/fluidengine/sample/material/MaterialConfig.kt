package dev.antigravity.fluidengine.sample.material

import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import java.util.Locale

/**
 * Le scene della pagina «Materiale» (piano «iOS in Android», F1A.9): ognuna mette davanti alla sessione sul
 * tablet una o più voci del backlog di F1A.6, F1A.7 e F1A.8. Il nome ([key]) è quello di `--es material.scene`;
 * [voices] dice quali voci copre, con i numeri del `LEGGIMI.md` accanto a questo file.
 */
internal enum class MaterialScene(val key: String, val title: String, val detail: String, val voices: String) {
  Components(
    "components", "Componenti",
    "Tasto tondo, tasto scelto, capsula con tre azioni, tab bar, menu, card, pannello, tinta scritta a mano e tinta colorata.",
    "F1A.6 1, 2, 3, 8, 11",
  ),
  Edge("edge", "Il bordo a taglia vera", "Tondo, capsula e menu fermi, con i riquadri in pixel per i ritagli a 6x.", "F1A.6 2, 3"),
  Corner("corner", "L'angolo dei grandi", "Due pannelli grandi con l'angolo continuo, ritagliati al 98%.", "F1A.6 4"),
  Nested("nested", "Vetro annidato", "Tasti e interruttore su un foglio di vetro; il primo tasto si preme da solo.", "F1A.6 5"),
  Press("press", "Pressione", "Un dito finto preme e scorre su un tasto tondo e su una capsula, in ciclo.", "F1A.6 6"),
  Intensity("intensity", "Intensità", "Capsula e pannello che compaiono e spariscono, intensity da 0 a 1 in ciclo.", "F1A.6 9"),
  ListUnderBar("list", "Lista sotto una barra", "Una pagina vera che scorre da sola sotto la barra in alto e una capsula.", "F1A.6 7"),
  Menu("menu", "Menu da un tasto", "Il tasto che diventa il proprio menu, aperto e chiuso in ciclo.", "F1A.6 10"),
  Modal("modal", "Foglio e scrim", "Un foglio vero dell'host dei modali, con lo scrim dietro, aperto e chiuso in ciclo.", "F1A.6 12"),
  Tints("tints", "Tinte", "Indicatore di sincronizzazione, tasto scelto, capsula su foto, tinte Fixed, Colored e copiata.", "F1A.6 13, F1A.8"),
  Apps("apps", "Lastre delle app", "Le tinte e le ottiche scritte a mano in Fluidify e MyCloud Gallery.", "F1A.8"),
  Contrast("contrast", "Contrasto di sistema", "Gli stessi vetri a contrasto standard, medio e alto.", "F1A.8, F1A-g"),
  Rtl("rtl", "Destra-sinistra", "Gli stessi vetri da sinistra a destra e da destra a sinistra, con angoli diversi.", "F1A.6 14"),
}

/** I sei fondi su cui si giudica il materiale (piano 4.6), più la pagina del tema in uso. */
internal enum class MaterialBackdrop(val key: String, val title: String) {
  /** La pagina vera del tema in uso: `colorScheme.background` di `FluidTheme`. */
  Page("page", "pagina del tema"),
  PageLight("page-light", "pagina chiara #F2F2F7"),
  PageDark("page-dark", "pagina scura #0D0D0F"),
  White("white", "bianco"),
  Black("black", "nero"),
  Text("text", "testo"),
  Photo("photo", "foto"),
}

/** Il fondo sta fermo o scorre sotto le lastre, come nella pagina «Misure». */
internal enum class MaterialMotion(val key: String) {
  Still("still"),
  Scroll("scroll"),
}

/** Quali vetri la pagina mette in scena: i due affiancati, o uno solo a tutta pagina. */
internal enum class MaterialModels(val key: String, val models: List<FluidGlassModel>) {
  Both("both", listOf(FluidGlassModel.Classic, FluidGlassModel.Liquid)),
  Liquid("liquid", listOf(FluidGlassModel.Liquid)),
  Classic("classic", listOf(FluidGlassModel.Classic)),
}

/**
 * Che cosa fanno da sole le scene che si muovono (pressione, intensità, menu, foglio, il tasto del vetro
 * annidato): girano in ciclo, restano nello stato «acceso» per le fotografie, o stanno ferme.
 */
internal enum class MaterialCycle(val key: String) {
  Cycle("cycle"),
  Hold("hold"),
  Off("off"),
}

/**
 * Tutto ciò che decide una scena della pagina «Materiale». Si legge dagli extra `material.<nome>` ([parse]); a
 * mano lo cambia il pannello della pagina. La [key] finisce in ogni riga di logcat: due righe con la stessa chiave
 * sono la stessa scena.
 */
internal data class MaterialConfig(
  val scene: MaterialScene,
  val backdrop: MaterialBackdrop = MaterialBackdrop.Page,
  val motion: MaterialMotion = MaterialMotion.Still,
  /** Velocità del fondo che scorre, in dp al secondo: più lenta che in «Misure», perché qui si guarda. */
  val speedDp: Float = 240f,
  val models: MaterialModels = MaterialModels.Both,
  val cycle: MaterialCycle = MaterialCycle.Cycle,
  /** La durata di un giro delle scene che si muovono, in secondi. */
  val periodSeconds: Float = 3f,
  /** Un valore fisso per `intensity` (scena `intensity`); nullo, il ciclo da 0 a 1. */
  val intensity: Float? = null,
  /** Un livello fisso per la scena `contrast`; nullo, i tre livelli affiancati. */
  val contrast: Float? = null,
  /** Il riquadro di stato sopra la scena. */
  val hud: Boolean = true,
) {

  val key: String
    get() {
      val parts = ArrayList<String>()
      parts += "scene=${scene.key}"
      parts += "backdrop=${backdrop.key}"
      parts += if (motion == MaterialMotion.Scroll) "motion=scroll speed=${number(speedDp)}" else "motion=still"
      parts += "models=${models.key}"
      if (scene in CyclingScenes) {
        parts += "cycle=${cycle.key}"
        parts += "period=${number(periodSeconds)}"
      }
      if (scene == MaterialScene.Intensity && intensity != null) parts += "intensity=${number(intensity)}"
      if (scene == MaterialScene.Contrast && contrast != null) parts += "contrast=${number(contrast)}"
      return parts.joinToString(" ")
    }

  companion object {
    const val ExtraScene = "material.scene"
    private const val Prefix = "material."

    /** Le scene che si muovono da sole: per loro contano `cycle` e `period`. */
    val CyclingScenes: Set<MaterialScene> = setOf(
      MaterialScene.Nested,
      MaterialScene.Press,
      MaterialScene.Intensity,
      MaterialScene.ListUnderBar,
      MaterialScene.Menu,
      MaterialScene.Modal,
    )

    /** I default di una scena: il fondo su cui la sua voce si guarda meglio. */
    fun defaults(scene: MaterialScene): MaterialConfig = when (scene) {
      // L'angolo e il bordo si guardano sulla pagina vera, dove un filo chiaro sparisce (4.6).
      MaterialScene.Edge, MaterialScene.Corner -> MaterialConfig(scene)
      // Il vetro annidato e la pressione vogliono qualcosa da rifrangere.
      MaterialScene.Nested, MaterialScene.Press -> MaterialConfig(scene, backdrop = MaterialBackdrop.Text)
      MaterialScene.Apps, MaterialScene.Tints -> MaterialConfig(scene, backdrop = MaterialBackdrop.Photo)
      // La lista ha la sua pagina: il fondo è quello che la lista porta sotto la barra.
      MaterialScene.ListUnderBar -> MaterialConfig(scene, periodSeconds = 4f)
      MaterialScene.Menu, MaterialScene.Modal -> MaterialConfig(scene, backdrop = MaterialBackdrop.Text, periodSeconds = 4f)
      else -> MaterialConfig(scene)
    }

    /**
     * Legge gli extra con [extra]. Nulla se `material.scene` manca o non è una scena: allora la pagina mostra il
     * suo elenco. Un altro valore che non si riconosce si ignora e resta il default, come in «Misure»: la chiave
     * in logcat dice con che cosa la scena è partita davvero.
     */
    fun parse(extra: (String) -> String?): MaterialConfig? {
      fun value(name: String): String? =
        extra(Prefix + name)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

      val scene = value("scene")?.let { name -> MaterialScene.entries.firstOrNull { it.key == name } } ?: return null
      val base = defaults(scene)
      return base.copy(
        backdrop = value("backdrop")?.let { name -> MaterialBackdrop.entries.firstOrNull { it.key == name } }
          ?: base.backdrop,
        motion = value("motion")?.let { name -> MaterialMotion.entries.firstOrNull { it.key == name } } ?: base.motion,
        speedDp = value("speed")?.let(::decimal)?.coerceIn(0f, 4000f) ?: base.speedDp,
        models = value("model")?.let { name -> MaterialModels.entries.firstOrNull { it.key == name } } ?: base.models,
        cycle = value("cycle")?.let { name -> MaterialCycle.entries.firstOrNull { it.key == name } } ?: base.cycle,
        periodSeconds = value("period")?.let(::decimal)?.coerceIn(0.5f, 60f) ?: base.periodSeconds,
        intensity = when (val text = value("intensity")) {
          null, "cycle" -> base.intensity
          else -> decimal(text)?.coerceIn(0f, 1f) ?: base.intensity
        },
        contrast = when (val text = value("contrast")) {
          null, "all" -> base.contrast
          "standard" -> 0f
          "medium" -> 0.5f
          "high" -> 1f
          else -> decimal(text)?.coerceIn(0f, 1f) ?: base.contrast
        },
        hud = value("hud")?.let(::flag) ?: base.hud,
      )
    }

    private fun decimal(text: String): Float? = text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

    internal fun flag(text: String): Boolean? = when (text) {
      "on", "true", "1", "yes", "si" -> true
      "off", "false", "0", "no" -> false
      else -> null
    }

    /** Un numero senza zeri inutili e sempre col punto, qualunque sia la lingua del dispositivo. */
    internal fun number(value: Float): String =
      if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(Locale.ROOT, "%.2f", value)
  }
}

/**
 * Gli interruttori che valgono per **tutta** la galleria (piano 4.5, S2: «`Classic` forzato» e «livello simulato»
 * su ogni pagina), letti dagli extra `gallery.*` nella sola build di debug, prima del contenuto.
 *
 * | extra | valori | che cosa fa |
 * |---|---|---|
 * | `gallery.model` | `classic` `liquid` | `LocalFluidGlassModel` alla radice |
 * | `gallery.api` | `32` `30` (un livello sotto quello vero, da 26) · `off` | `FluidGlassDiagnostics.simulateApiLevel` |
 * | `gallery.warmup` | `on` `off` | `FluidGlassDiagnostics.warmUpEnabled` |
 * | `gallery.tune` | `edge=ios26,blur=24,…` · `off` | `FluidGlassDiagnostics.tuneLiquidProfile` |
 *
 * Pura e senza Android attorno: la regola si prova sul computer.
 *
 * @property api il livello da simulare; [ApiOff] per togliere la simulazione; nullo se non è stato chiesto.
 * @property tune le manopole del profilo; vuota per tornare a quello di serie; nulla se non è stata chiesta.
 */
internal data class GalleryGlassLaunch(
  val model: FluidGlassModel? = null,
  val api: Int? = null,
  val warmUp: Boolean? = null,
  val tune: Map<String, String>? = null,
) {

  val isEmpty: Boolean get() = model == null && api == null && warmUp == null && tune == null

  companion object {
    const val ExtraModel = "gallery.model"
    const val ExtraApi = "gallery.api"
    const val ExtraWarmUp = "gallery.warmup"
    const val ExtraTune = "gallery.tune"

    /** `gallery.api off`: il livello vero. */
    const val ApiOff = 0

    val None = GalleryGlassLaunch()

    fun parse(extra: (String) -> String?): GalleryGlassLaunch {
      fun value(key: String): String? = extra(key)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
      return GalleryGlassLaunch(
        model = when (value(ExtraModel)) {
          "classic" -> FluidGlassModel.Classic
          "liquid" -> FluidGlassModel.Liquid
          else -> null
        },
        api = value(ExtraApi)?.let { text ->
          when (text) {
            "off", "real", "none", "vero" -> ApiOff
            // Il controllo vero (sotto il livello del dispositivo, non sotto 26) lo fa l'engine; qui solo un numero.
            else -> text.toIntOrNull()?.takeIf { it in 1..99 }
          }
        },
        warmUp = value(ExtraWarmUp)?.let { MaterialConfig.flag(it) },
        tune = value(ExtraTune)?.let(::parseTune),
      )
    }

    /**
     * `edge=ios26,blur=24` → le manopole; `off` (o `default`, `none`) → nessuna. Le coppie senza `=` si
     * scartano: il controllo dei nomi e dei valori lo fa l'engine, che dice quali ha preso.
     */
    fun parseTune(text: String): Map<String, String> {
      val clean = text.trim().lowercase(Locale.ROOT)
      if (clean == "off" || clean == "default" || clean == "none") return emptyMap()
      val out = LinkedHashMap<String, String>()
      for (pair in clean.split(',', ';', ' ')) {
        val at = pair.indexOf('=')
        if (at <= 0 || at == pair.length - 1) continue
        out[pair.substring(0, at).trim()] = pair.substring(at + 1).trim()
      }
      return out
    }

    /** Le manopole scritte come le legge `gallery.tune`: per la riga di logcat e per il comando da copiare. */
    fun tuneText(tune: Map<String, String>): String =
      if (tune.isEmpty()) "off" else tune.entries.joinToString(",") { "${it.key}=${it.value}" }
  }
}
