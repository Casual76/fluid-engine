/*
 * Il profilo della GOCCIA (F1B.3, piano «iOS in Android», sezione F1B): il vetro del pomello dell'interruttore e del
 * cursore, della pillola del segmentato, dell'indicatore del rail, della lente dell'indice e della goccia delle voci
 * di menù, cioè di tutto ciò che passa da `glassDrop` (`ui/glass/interaction/GlassDrop.kt`).
 *
 * Non ha numeri del materiale suoi, salvo quelli dichiarati qui sotto: ogni profilo è **derivato** da quello di
 * serie ([GlassLiquidProfile.Default]) con `copy`, quindi se domani cambia il bordo, l'ombra, l'ombreggiatura del
 * corpo o la lente di serie, la goccia li segue. Bordo e ombra sono quelli di serie **per intero**: la goccia ha la
 * stessa luce dei tasti (stesso filo, stesso riflesso, stessa ombra, che la regola del vetro annidato riduce da sola
 * quando la goccia sta dentro una lastra: la pillola del segmentato, la goccia del menù).
 *
 * ### Cosa dice Apple
 * R4 B5 (media), `B5_interruttore_f7752-7880_ogni2.jpg`: il pomello bianco **diventa una lente limpida** in 70–100 ms
 * e cresce oltre la pista (≈×1,5); attraversandola, sotto la lente il colore della pista si vede **rifratto**, piegato
 * fin sul bordo; la lente si richiude nel pomello bianco in ~130 ms. Letti sul ritaglio (f7766, a pressione piena):
 *  - **nessun velo chiaro**: il verde della pista sotto la lente è 82/158/83 contro 83/178/88 fuori, la piastra grigia
 *    172 contro 195. Il vetro *scurisce* appena (−20…−23 livelli, l'11–12%), non schiarisce;
 *  - **nessuna smerigliatura**: i bordi della pista restano netti sotto la lente;
 *  - **un bordo stretto che piega forte attorno a un interno piatto**: il verde della pista corre tutto attorno al
 *    contorno della lente, anche in alto dove sotto c'è la piastra grigia, mentre al centro la pista passa dritta
 *    (`B5_cursore`: la pista del cursore attraversa la lente senza ingrandirsi e si piega solo al bordo);
 *  - **un filo chiaro sottile** sul contorno, e nessun alone grigio all'interno.
 *
 * ### Come lo fa l'engine
 * La goccia a riposo è la faccia piena (bianca, o grigia in una lastra scura: `GlassDropFaces`), disegnata **sopra**
 * il corpo; sotto il dito la faccia sfuma e resta il corpo. La geometria e i tempi «faccia piena → lente» non cambiano
 * (`glassDrop`): cambia il corpo, che non è più la ricetta di Kyant (sfocatura 8 dp, lente 10/14 dp, bagliore
 * `Ambient`, ombra interna, ombra fissa al 5%) ma **il materiale**, `Body1` con la luce e l'ombra della ricetta:
 *  - **limpido** ([glassDropProfile]): la mescola `w` a uno e la saturazione spenta (la catena salta la sfocatura), e
 *    al posto del velo chiaro di serie un tono scuro leggero ([GlassDropTone]). Resta l'ombreggiatura del corpo verso
 *    il contorno;
 *  - **rifrangente** ([glassDropLens]): la banda della lente di serie, con lo spostamento del materiale **a moto
 *    pieno** (`GlassLiquidTravel`: per `1 + bend`) portato ancora di [GlassDropBend], e **senza cupola**
 *    ([GlassDropDome]): un bordo stretto che piega forte attorno a un interno piatto. È la stessa idea di B1 (R4): il
 *    vetro che si muove sotto il dito è limpido e piega di più; se Alessio sceglie un altro materiale in viaggio
 *    (`travel=strong` sul tablet), la goccia lo segue. Senza frangia ([GlassDropOptics]);
 *  - **la lente cresce con la pressione**: è la profondità ottica della ricetta (`opticalDepth` = pressione, con
 *    [GlassDropOptics], il cui `edgeFloor` uno tiene filo e ombra fermi), come la lente di Kyant cresceva con `p`.
 *    La banda resta, quindi il margine della cattura non si muove;
 *  - **la luce viene con la lente** ([glassDropLightAmount], giro di correzione dopo S2): filo e riflesso dei tasti
 *    compaiono in proporzione alla pressione, mentre la faccia sfuma. A riposo la goccia è la faccia pulita con la
 *    sua ombra, come il pomello di Apple.
 *
 * Fuori da qui (F5A): il «punto di luce forte» sulla goccia limpida fra 33 e 67 ms (R4 B1 b).
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.sanitized

/**
 * Il **tono** della goccia: il velo chiaro del profilo (46% di un quasi bianco, il «secondo vetro» da togliere) non
 * c'è, e al suo posto un nero di questa alfa, uguale ovunque. Sul ritaglio di Apple (vedi in testa) sotto la lente il
 * fondo non schiarisce: scurisce dell'11–12% (−20…−23 livelli su una piastra a 195), in tutta la lente.
 *
 * Sul banco, cursore sopra la pagina `#F2F2F7` a pressione piena: con la sola ombreggiatura del corpo del profilo
 * (che la goccia tiene) −6,5 livelli, e l'interno della lente non si distingue dalla pagina; con il 5% −18,5; con l'8%
 * −25, e la lente torna grigia come quella di oggi sul tablet (F1B.0, (i), difetto 2). Il 5%, prudente: il ritaglio
 * è un'animazione di presentazione (classe C), e la manopola sul tablet dirà. Sullo scuro sposta un livello o due.
 */
internal const val GlassDropTone: Float = 0.05f

/**
 * Lo spostamento della lente della goccia, in multipli di quello del materiale a moto pieno (la lente di serie per
 * `1 + travel.bend`): una volta e mezza. Con la lente media e il viaggio prudente di serie fa `0,24 · 0,9 · 1,5 · 1,5`
 * = 0,49 del lato corto: 11,7 dp sul pomello da 24, 17,5 una volta cresciuto a ×1,5, cioè quasi mezza altezza della
 * lente. Sul ritaglio di Apple (B5) la pista corre attorno a tutto il contorno della lente anche dove sotto c'è la
 * piastra: lo spostamento al bordo arriva alla pista, mezza altezza o quasi. Col solo moto pieno (×1) il cursore sopra
 * un testo piegava appena il bordo delle lettere; con ×1,6 e la banda stretta lo spostamento passava il centro e la
 * pista disegnava una seconda riga lungo il bordo alto. A occhio sul banco, `bench/f1b-3/` (varianti A–O).
 */
internal const val GlassDropBend: Float = 1.5f

/**
 * La cupola della goccia: **nessuna**. Il materiale in viaggio porta la cupola verso uno («la goccia è una lente
 * intera»), ma sul pomello di Apple (B5, cursore e interruttore) l'interno è piatto: la pista passa dritta sotto la
 * lente, senza ingrandirsi, e piega solo al bordo. Con la cupola di serie (0,5) o a metà del viaggio (0,75) la pista
 * sotto il cursore si gonfiava verso il centro; con 0,3 appena. A occhio sul banco (varianti C, F, O contro I, M).
 */
internal const val GlassDropDome: Float = 0f

/**
 * L'ottica che la goccia chiede alla ricetta: quella dei tasti (`GlassDefaults.buttonOptics`, ruolo
 * [GlassRole.Interactive]: la stessa luce, la stessa ombra), con `edgeFloor` a uno e **senza frangia**.
 *
 * Il pavimento: la pressione della goccia passa da `opticalDepth`, che scala lo spostamento della lente; con il
 * pavimento a uno filo, riflesso e ombra non la seguono, e restano quelli di un tasto a riposo.
 *
 * La frangia: è una frazione dello spostamento (il blu piega il 30% in più), e la goccia piega il doppio di un tasto
 * premuto. Sul banco il bordo della lente diventava un arcobaleno giallo e viola (varianti G e J), e un ciuffo
 * arancione dove la lente esce dalla pista dell'interruttore; sul ritaglio di Apple il bordo della lente non ha
 * colori suoi. Spenta.
 */
internal val GlassDropOptics: GlassOptics =
  GlassDefaults.buttonOptics().copy(edgeFloor = 1f, dispersion = false).sanitized()

/** Il ruolo con cui la goccia chiede la ricetta: quello dei tasti. */
internal val GlassDropRole: GlassRole = GlassRole.Interactive

/**
 * La lente della goccia: la banda di [base], lo spostamento di [base] **a moto pieno** del materiale in viaggio
 * [travel] (per `1 + bend`, come `glassTravelRecipe` con il moto a uno) portato di [GlassDropBend], e la cupola
 * [GlassDropDome]. Pura.
 */
internal fun glassDropLens(base: GlassLiquidLens, travel: GlassLiquidTravel): GlassLiquidLens = GlassLiquidLens(
  band = base.band,
  bend = base.bend * (1f + maxOf(travel.bend, 0f)) * GlassDropBend,
  dome = GlassDropDome,
)

/**
 * Il profilo della goccia: [base] limpido (al posto del velo il tono [GlassDropTone], niente smerigliatura né
 * saturazione) e rifrangente ([glassDropLens]). Bordo, ombra, ombreggiatura del corpo, regola dei piccoli e del vetro
 * annidato restano quelli di [base]. Il materiale in viaggio è spento: la goccia non viaggia con Fluid-physics, e il
 * suo «moto» è la pressione.
 *
 * @param tone l'alfa del nero al posto del velo; di serie [GlassDropTone].
 */
internal fun glassDropProfile(
  base: GlassLiquidProfile = GlassLiquidProfile.Default,
  tone: Float = GlassDropTone,
): GlassLiquidProfile {
  val film = Color.Black.copy(alpha = clampUnit(tone))
  // Il velo resta un velo (la regola del vetro annidato lo scala come quello di un tasto), ma è un nero che non si
  // ispessisce: l'ombreggiatura verso il contorno è quella del profilo.
  fun clear(v: GlassLiquidVeil): GlassLiquidVeil = v.copy(color = film, adapt = 0f)
  return base.copy(
    lightVeil = clear(base.lightVeil),
    darkVeil = clear(base.darkVeil),
    darkLargeVeil = clear(base.darkLargeVeil),
    frost = base.frost.copy(mix = 1f, saturation = 1f),
    lens = glassDropLens(base.lens, base.travel),
    travel = GlassLiquidTravels.Off,
  )
}

/**
 * La ricetta della goccia a una pressione, come la calcola la superficie (`GlassLiquidSurface` con il profilo
 * [glassDropProfile], [GlassDropOptics] e la pressione come profondità ottica): per i test e per le scene del banco.
 * La faccia non c'è: è un disegno sopra il corpo (`glassDropFaceAlpha`).
 *
 * @param widthDp con [heightDp], la taglia del **nodo** (non quella scalata dal `layerBlock` della pressione).
 * @param nested vero se la goccia sta dentro una lastra di vetro (la pillola del segmentato, la goccia del menù).
 */
internal fun glassDropRecipe(
  widthDp: Float,
  heightDp: Float,
  darkSide: Boolean,
  pressure: Float,
  nested: Boolean = false,
  quality: Float = 1f,
  profile: GlassLiquidProfile = glassDropProfile(),
  out: GlassRecipe = GlassRecipe(),
): GlassRecipe = glassRecipe(
  role = GlassDropRole,
  widthDp = widthDp,
  heightDp = heightDp,
  darkSide = darkSide,
  optics = GlassDropOptics,
  opticalDepth = pressure,
  quality = quality,
  nested = nested,
  profile = profile,
  out = out,
)

// ------------------------------------------------------------------------------------------------------------
// La luce della goccia: solo sotto il dito (F1B.3, giro di correzione)
// ------------------------------------------------------------------------------------------------------------

/**
 * Quanta della luce dei tasti (il filo e il riflesso) porta la goccia alla pressione [pressure]: **quanta ne porta la
 * lente**, cioè la pressione stessa. A riposo zero, sotto il dito tutta.
 *
 * Perché: a riposo la goccia è la faccia piena, bianca sui pomelli. Con la luce dei tasti sopra, sul chiaro il filo
 * scuro disegnava una riga grigia di un pixel attorno al pomello (S2, parte 2.1: 202 fra la pista a 214 e il bianco a
 * 244; `moto/f1b/S2/per-alessio/ferma-manopola-prima-dopo-x4.png`), che la partenza non aveva e il pomello di Apple
 * (B5) non ha: il pomello bianco di Apple a riposo è una faccia pulita con la sua ombra. Il filo è del **vetro**, e il
 * vetro c'è quando la faccia sfuma: per questo segue la pressione, proporzionale, come la lente. Il riflesso va con lui:
 * su una faccia bianca non si vede, e su quella grigia della lastra scura disegnava un secondo contorno chiaro.
 * L'ombra no: resta ferma, a riposo e premuta.
 *
 * [restRim] vero (la manopola di debug `knobrim=on`, [GlassDropTuning]) rimette la luce di prima, piena a ogni
 * pressione: la scelta consigliata ad Alessio è la nuova, e può ancora dire «com'era».
 */
internal fun glassDropLightAmount(pressure: Float, restRim: Boolean = GlassDropTuning.restRim): Float =
  if (restRim) 1f else clampUnit(pressure)

/**
 * La manopola di debug della goccia, `knobrim` (`on` | `off`): `on` rimette il filo e il riflesso sul pomello a
 * riposo, com'erano dopo F1B.3. Come le manopole del profilo ([GlassLiquidTuning]) vale solo in un'app `debuggable` e
 * per ciò che si disegna da adesso; chi la installa è lo stesso che installa quelle (`FluidGlassDiagnostics`).
 *
 * Sta qui e non fra le varianti di [GlassLiquidTuning] perché non è un numero del materiale: è la scelta di quando la
 * goccia mostra la luce.
 */
internal object GlassDropTuning {

  /** Il nome della manopola, come lo scrive `gallery.tune`. */
  const val Knob: String = "knobrim"

  /** I valori che accetta. */
  val Values: List<String> = listOf("on", "off")

  /** Vero se la luce sta anche sul pomello a riposo (la manopola a `on`). Di serie falso. */
  @Volatile
  var restRim: Boolean = false
    private set

  /**
   * Prende la manopola da [knobs] (nome e valore senza distinzione di maiuscole) e la applica; senza la manopola, o
   * con `null`, torna di serie. Restituisce il valore preso (`on` o `off`), `null` se la manopola non c'era, o se il
   * suo valore non si riconosce (allora non cambia niente).
   */
  fun apply(knobs: Map<String, String>?): String? {
    val raw = knobs?.entries?.firstOrNull { it.key.trim().lowercase() == Knob }?.value
    if (raw == null) {
      restRim = false
      return null
    }
    return when (raw.trim().lowercase()) {
      "on" -> "on".also { restRim = true }
      "off" -> "off".also { restRim = false }
      else -> null
    }
  }
}

private fun clampUnit(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
