package dev.antigravity.fluidengine.sample.measure

import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import java.util.Locale

/**
 * Le scene della pagina «Misure» (piano «iOS in Android», F0.6): una per misura della prima sessione sul
 * tablet. Il nome ([key]) è quello che si passa da riga di comando con `--es measure.scene`.
 *
 * Ogni scena è fissa: stesso fondale, stessi elementi, stesso moto a parità di extra. È ciò che rende
 * confrontabili due lanci, e tre ripetizioni dello stesso lancio.
 */
internal enum class MeasureScene(val key: String, val title: String, val detail: String) {
  Base("base", "Base", "Il palco senza vetro: il pavimento da sottrarre a tutte le altre."),
  Compare("compare", "Corpo + luce contro il vetro di oggi", "Tasto tondo, capsula e pannello sopra un fondale che scorre."),
  Uniforms("uniforms", "Soli uniform, cattura ferma", "Pressione, comparsa e bagliore, nei montaggi a un layer e a due."),
  Pieces("pieces", "Pezzi fusi", "Lo stesso campo con 1, 2, 4, 8 pezzi."),
  Edge("edge", "Bordo dello scorrimento", "La striscia a due passate in alto e in basso, piena e a metà risoluzione."),
  Probe("probe", "Lettura della miniatura", "toImageBitmap() di un layer piccolo: tempo, frequenza, filo."),
  Compile("compile", "Prima compilazione", "Ogni variante di shader disegnata per la prima volta, una alla volta."),
  Physics("physics", "Fluid-physics, Full e Balanced", "Un viaggio in ciclo con il livello forzato."),
}

/** Il fondale del palco. Tutti generati in codice, nessuno dipende dal caso. */
internal enum class MeasureBackdropKind(val key: String) {
  Text("text"),
  Photo("photo"),
  Flat("flat"),
  Checker("checker"),
}

/** Che cosa tiene vivo lo schermo a ogni fotogramma. */
internal enum class MeasureMotion(val key: String) {
  /** Il fondale scorre a velocità costante: ogni cattura si rifà, la catena gira a ogni fotogramma. */
  Scroll("scroll"),

  /** Il fondale sta fermo e lo schermo si ridisegna lo stesso: il costo a riposo. */
  Still("still"),
}

internal enum class MeasureElement(val key: String) {
  Round("round"),
  Capsule("capsule"),
  Panel("panel"),
  Large("large"),

  /** I tre del banco insieme: tondo, capsula, pannello. */
  All("all"),
}

internal enum class MeasureGlass(val key: String) {
  /** Il prototipo della via A: corpo a un ingresso, luce sopra. */
  Proto("proto"),

  /** `glassSurface` com'è oggi. */
  Today("today"),

  /** Tutti e due, uno sopra l'altro: per guardare, non per misurare. */
  Both("both"),
  None("none"),
}

/**
 * Come il corpo del prototipo è montato sui layer: è la scelta del cancello C1 (piano 3.5).
 *
 * I primi due sono «corpo a un layer», gli altri due «corpo a due layer»; le varianti dicono *dove* un
 * cambio di uniform smette di ripagare la sfocatura.
 */
internal enum class MeasureMount(val key: String) {
  /** Una cattura, una catena, ricostruita per intero a ogni cambio: ciò che `drawBackdrop` fa oggi. */
  One("one"),

  /**
   * Come [One], ma i nodi a monte dello shader (saturazione, sfocatura, mescola) restano gli stessi
   * oggetti finché i loro numeri non cambiano. Se costa come [Two], Skia riusa la sfocatura.
   */
  OneStable("one-stable"),

  /** Primo layer: saturazione, sfocatura, mescola. Secondo layer: solo lo shader. */
  Two("two"),

  /**
   * Come [Two], ma la mescola `w` è la composizione di due layer (nitido e sfocato) invece di un nodo
   * dell'effetto: così nemmeno la comparsa rifà la sfocatura.
   */
  TwoComposited("two-composited"),
}

internal enum class MeasureAnimation(val key: String) {
  None("none"),

  /** `refraction` che si approfondisce: la pressione di un tasto. */
  Press("press"),

  /** `materialize` da 0 a 1 e `w` da 1 a 0: la comparsa per lente. */
  Appear("appear"),

  /** Solo la luce: il bagliore del dito che si sposta. Il corpo non deve accorgersene. */
  Glow("glow"),
}

internal enum class MeasureStrip(val key: String) {
  TwoPass("two-pass"),

  /** La fascia di oggi (`GlassFalloff.FadeDown`), che il bordo sostituisce: solo in alto. */
  Today("today"),
  None("none"),
}

internal enum class MeasureEdges(val key: String) {
  Both("both"),
  Top("top"),
  Bottom("bottom"),
}

/** Dove girano la lettura della miniatura (`toImageBitmap`) e la copia dei suoi pixel. */
internal enum class MeasureThread(val key: String) {
  /** Tutto sul filo principale. */
  Main("main"),

  /** `toImageBitmap` sul principale, copia dei pixel e statistiche su un filo di lavoro. */
  Mixed("mixed"),

  /** Tutto su un filo di lavoro. */
  Background("background"),
}

internal enum class MeasureJourney(val key: String) {
  /** Cerchio ↔ quadrato arrotondato: il ciclo di misura del Playground. */
  Square("square"),

  /** Capsula ↔ pannello: un tasto che diventa il suo menu. */
  Panel("panel"),

  /** Due tondi ↔ pannello: i ponti liquidi, che esistono solo a `Full`. */
  Pieces("pieces"),
}

/**
 * Tutto ciò che decide una misura. Una scena è una riga di default ([defaults]); ogni campo si può
 * cambiare con un extra `measure.<nome>` ([parse]). La [key] finisce in ogni riga di logcat: due righe
 * con la stessa chiave sono la stessa misura.
 */
internal data class MeasureConfig(
  val scene: MeasureScene,
  val backdrop: MeasureBackdropKind = MeasureBackdropKind.Text,
  val motion: MeasureMotion = MeasureMotion.Scroll,
  /** Velocità dello scorrimento, in dp al secondo. */
  val speedDp: Float = 900f,
  val element: MeasureElement = MeasureElement.All,
  val glass: MeasureGlass = MeasureGlass.Proto,
  val mount: MeasureMount = MeasureMount.One,
  val light: Boolean = true,
  val shadow: Boolean = true,
  val animation: MeasureAnimation = MeasureAnimation.None,
  val pieces: Int = 1,
  /**
   * Quale variante dello shader porta i pezzi: falso, quella da otto con l'uscita anticipata (la sonda
   * com'è); vero, la più piccola che li tiene (1, 2, 4, 8: la scelta per conteggio del piano 3.7).
   */
  val fitVariant: Boolean = false,
  /** Risoluzione della cattura: 1 o 0,5 (ogni altro valore fra 0,1 e 1 è ammesso). */
  val scale: Float = 1f,
  /** Il ritaglio hardware sulla sagoma. Spento, ritaglia solo lo shader (come una lastra che viaggia). */
  val clip: Boolean = true,
  val blurDp: Float = 12f,
  val dispersion: Float = 1f,
  val strip: MeasureStrip = MeasureStrip.TwoPass,
  val edges: MeasureEdges = MeasureEdges.Both,
  val stripHeightDp: Float = 96f,
  /** Ogni quanto si legge la miniatura, in millisecondi. */
  val periodMillis: Long = 120L,
  val thread: MeasureThread = MeasureThread.Main,
  val probes: Int = 1,
  /** Lato della miniatura, in pixel. */
  val probeSide: Int = 16,
  /** Il livello chiesto a Fluid-physics; `null` è il tetto del dispositivo. */
  val tier: FluidPhysicsTier? = null,
  val journey: MeasureJourney = MeasureJourney.Square,
  /** Durata di una finestra di misura, in secondi. */
  val seconds: Float = 8f,
  /** Attesa prima della prima finestra: la galleria finisce di comporsi e gli shader si compilano. */
  val warmupSeconds: Float = 2f,
  val runs: Int = 3,
  val hud: Boolean = true,
) {

  /** Solo ciò che la scena legge davvero, così la chiave non porta numeri che non contano. */
  val key: String
    get() {
      val parts = ArrayList<String>()
      parts += "scene=${scene.key}"
      parts += "backdrop=${backdrop.key}"
      when (scene) {
        MeasureScene.Base -> parts += motionKey()
        MeasureScene.Compare, MeasureScene.Uniforms, MeasureScene.Pieces -> {
          parts += motionKey()
          if (scene == MeasureScene.Pieces) {
            parts += "pieces=$pieces"
            parts += "variant=${if (fitVariant) "fit" else "max"}"
          } else {
            parts += "element=${element.key}"
          }
          parts += "glass=${glass.key}"
          if (glass == MeasureGlass.Proto || glass == MeasureGlass.Both) {
            parts += "mount=${mount.key}"
            parts += "light=${onOff(light)}"
            parts += "shadow=${onOff(shadow)}"
            parts += "blur=${number(blurDp)}"
            parts += "dispersion=${number(dispersion)}"
            parts += "clip=${onOff(clip)}"
          }
          parts += "animate=${animation.key}"
          parts += "scale=${number(scale)}"
        }
        MeasureScene.Edge -> {
          parts += motionKey()
          parts += "strip=${strip.key}"
          parts += "edges=${edges.key}"
          parts += "height=${number(stripHeightDp)}"
          parts += "blur=${number(blurDp)}"
          parts += "scale=${number(scale)}"
        }
        MeasureScene.Probe -> {
          parts += motionKey()
          parts += "period=$periodMillis"
          parts += "thread=${thread.key}"
          parts += "probes=$probes"
          parts += "side=$probeSide"
        }
        MeasureScene.Compile -> Unit
        MeasureScene.Physics -> {
          parts += "tier=${tier?.name?.lowercase() ?: "auto"}"
          parts += "journey=${journey.key}"
        }
      }
      return parts.joinToString(" ")
    }

  private fun motionKey(): String =
    if (motion == MeasureMotion.Scroll) "motion=scroll speed=${number(speedDp)}" else "motion=still"

  companion object {
    const val ExtraScene = "measure.scene"
    private const val Prefix = "measure."

    /** I default di una scena: ciò che il piano chiede di misurare, senza altri extra. */
    fun defaults(scene: MeasureScene): MeasureConfig = when (scene) {
      MeasureScene.Base -> MeasureConfig(scene, glass = MeasureGlass.None)
      MeasureScene.Compare -> MeasureConfig(scene)
      // A cattura ferma: il fondale non scorre, cambia solo un uniform del corpo.
      MeasureScene.Uniforms -> MeasureConfig(
        scene,
        motion = MeasureMotion.Still,
        element = MeasureElement.Capsule,
        animation = MeasureAnimation.Press,
      )
      // La sagoma la ritaglia lo shader: i pezzi stanno dentro una superficie rettangolare.
      MeasureScene.Pieces -> MeasureConfig(scene, clip = false)
      MeasureScene.Edge -> MeasureConfig(scene, glass = MeasureGlass.None, speedDp = 1800f, blurDp = 14f)
      MeasureScene.Probe -> MeasureConfig(scene, glass = MeasureGlass.None)
      MeasureScene.Compile -> MeasureConfig(scene, motion = MeasureMotion.Still, warmupSeconds = 3f, runs = 1)
      MeasureScene.Physics -> MeasureConfig(scene, backdrop = MeasureBackdropKind.Photo, motion = MeasureMotion.Still)
    }

    /**
     * Legge gli extra con [extra] (che dice il valore di una chiave, o nulla). Nulla se `measure.scene`
     * manca o non è una scena: allora la pagina mostra il suo elenco. Un altro valore che non si
     * riconosce si ignora e resta il default, come fa `GalleryLaunch`: una misura che non parte per un
     * refuso è peggio di una che parte di serie (e la chiave in logcat dice con cosa è partita).
     */
    fun parse(extra: (String) -> String?): MeasureConfig? {
      fun value(name: String): String? =
        extra(Prefix + name)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

      val scene = value("scene")?.let { name -> MeasureScene.entries.firstOrNull { it.key == name } } ?: return null
      val base = defaults(scene)
      return base.copy(
        backdrop = value("backdrop")?.let { name -> MeasureBackdropKind.entries.firstOrNull { it.key == name } }
          ?: base.backdrop,
        motion = value("motion")?.let { name -> MeasureMotion.entries.firstOrNull { it.key == name } } ?: base.motion,
        speedDp = value("speed")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 20_000f) ?: base.speedDp,
        element = value("element")?.let { name -> MeasureElement.entries.firstOrNull { it.key == name } }
          ?: base.element,
        glass = value("glass")?.let { name -> MeasureGlass.entries.firstOrNull { it.key == name } } ?: base.glass,
        mount = value("mount")?.let { name -> MeasureMount.entries.firstOrNull { it.key == name } } ?: base.mount,
        light = value("light")?.let(::flag) ?: base.light,
        shadow = value("shadow")?.let(::flag) ?: base.shadow,
        animation = value("animate")?.let { name -> MeasureAnimation.entries.firstOrNull { it.key == name } }
          ?: base.animation,
        pieces = value("pieces")?.toIntOrNull()?.coerceIn(1, MaxPieces) ?: base.pieces,
        fitVariant = when (value("variant")) {
          "fit" -> true
          "max" -> false
          else -> base.fitVariant
        },
        scale = value("scale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.1f, 1f) ?: base.scale,
        clip = value("clip")?.let(::flag) ?: base.clip,
        blurDp = value("blur")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 64f) ?: base.blurDp,
        dispersion = value("dispersion")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
          ?: base.dispersion,
        strip = value("strip")?.let { name -> MeasureStrip.entries.firstOrNull { it.key == name } } ?: base.strip,
        edges = value("edges")?.let { name -> MeasureEdges.entries.firstOrNull { it.key == name } } ?: base.edges,
        stripHeightDp = value("height")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(8f, 400f)
          ?: base.stripHeightDp,
        periodMillis = value("period")?.toLongOrNull()?.coerceIn(8L, 10_000L) ?: base.periodMillis,
        thread = value("thread")?.let { name -> MeasureThread.entries.firstOrNull { it.key == name } } ?: base.thread,
        probes = value("probes")?.toIntOrNull()?.coerceIn(1, 4) ?: base.probes,
        probeSide = value("side")?.toIntOrNull()?.coerceIn(1, 64) ?: base.probeSide,
        tier = when (value("tier")) {
          "full" -> FluidPhysicsTier.Full
          "balanced" -> FluidPhysicsTier.Balanced
          "lite" -> FluidPhysicsTier.Lite
          "auto" -> null
          else -> base.tier
        },
        journey = value("journey")?.let { name -> MeasureJourney.entries.firstOrNull { it.key == name } }
          ?: base.journey,
        seconds = value("seconds")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(1f, 120f) ?: base.seconds,
        warmupSeconds = value("warmup")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 60f)
          ?: base.warmupSeconds,
        runs = value("runs")?.toIntOrNull()?.coerceIn(1, 20) ?: base.runs,
        hud = value("hud")?.let(::flag) ?: base.hud,
      )
    }

    /** Il tetto del campo delle due sonde (`MAX_PIECES` nelle sorgenti). */
    const val MaxPieces = 8

    private fun flag(text: String): Boolean? = when (text) {
      "on", "true", "1", "yes", "si" -> true
      "off", "false", "0", "no" -> false
      else -> null
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    /** Un numero senza zeri inutili e sempre col punto, qualunque sia la lingua del dispositivo. */
    private fun number(value: Float): String =
      if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(Locale.ROOT, "%.2f", value)
  }
}
