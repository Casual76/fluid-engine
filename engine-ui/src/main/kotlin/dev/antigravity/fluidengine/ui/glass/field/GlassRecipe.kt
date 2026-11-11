/*
 * La RICETTA del materiale `Liquid`: da ciò che una superficie chiede (ruolo, ottica, taglia, lato del tema,
 * variante, intensità, profondità, qualità, pressione) a **tutti** i numeri che servono a disegnarla: la
 * catena a monte, gli uniform del corpo e della luce, l'ombra.
 *
 * È una sola, per tutte le strade del vetro (`glassSurface`, `glassControlSurface`, e da F1B
 * `fluidPhysicsSurface`, goccia, puck, piano flottante: piano 3.1), ed è pura: niente Compose, niente densità,
 * niente stato. Le lunghezze escono in **dp**; chi imposta gli uniform le porta nello spazio dello shader
 * (`gf/GlassFieldEffects.kt`, F1A.6). I numeri di gusto li legge dal profilo ([GlassLiquidProfile]) e non ne
 * contiene: qui c'è solo *come* un profilo diventa una lastra di una certa taglia.
 *
 * Le scene del banco, le tavole di gusto e le immagini di riferimento `golden/liquid` sono scritte da questa
 * funzione (`test/glass/field/trace/GlassSceneExportTest.kt`): ciò che si misura e si sceglie è ciò che
 * l'engine disegnerà.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassBlurFloor
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.glassEdgeDepth
import dev.antigravity.fluidengine.ui.fluid.glassResolutionScale
import dev.antigravity.fluidengine.ui.fluid.glassSizeGain
import kotlin.math.max
import kotlin.math.min

/**
 * La variante di vetro, nel senso di Apple. Il tipo pubblico (`FluidGlass.Regular` / `Clear` / `Identity`)
 * nasce in F2.2 e si traduce in questo; `Identity` è `intensity` zero.
 */
internal enum class GlassLiquidVariant {
  /** Il materiale del profilo: smerigliato, col suo velo. */
  Regular,

  /**
   * Limpido: nessuna smerigliatura (la mescola `w` va a uno, **il raggio resta**: così fra le due varianti si
   * può animare senza rifare la cattura, piano 3.6), nessun velo, e sotto il vetro lo scurimento
   * [GlassLiquidClearDim] che tiene leggibile il contenuto. Lente, bordo e ombra restano.
   */
  Clear,
}

/**
 * Ciò che [glassRecipe] calcola. È un contenitore che chi chiama **riusa**: la ricetta lo riscrive per intero
 * e non alloca, quindi si può chiamare a ogni fotogramma di una pressione.
 *
 * Le lunghezze sono in dp (`…Dp`), i colori non premoltiplicati, come gli uniform `layout(color)`. I nomi sono
 * quelli degli uniform dove ce n'è uno.
 *
 * Cosa **non** c'è, perché non è del materiale: la geometria (`offset`, `flex`, i pezzi: è il piano del
 * campo, `GlassFieldPlan`), `lightInBody` (lo decide il motore: 1 in viaggio),
 * `glowCenter`, `glowRadius` e il `glow` del momento (li dà il tocco; qui ci sono il massimo e il
 * riempimento), il filetto di `GlassEdge`.
 */
internal class GlassRecipe {

  // ---- La catena a monte: ColorFilter(saturazione) -> Blur(Clamp) -> mescola `w` -> corpo -------------------

  /** La saturazione prima della sfocatura. 1 = nessun filtro. */
  var saturation: Float = 1f

  /** Il raggio di sfocatura **di adesso** (scende con la qualità, mai con l'intensità). */
  var blurDp: Float = 0f

  /** La mescola `w`: quanto del nitido resta sopra lo sfocato. 1 = tutto nitido, 0 = tutto smerigliato. */
  var mix: Float = 1f

  /**
   * Il margine della cattura: `max(0, sfocatura − spessore della lente)`, calcolato sulla sfocatura **a
   * qualità piena** perché resti fermo mentre la qualità scende durante uno scorrimento (un margine che cambia
   * rifà la cattura: invariante 1). Chi monta la catena ci somma il margine del campo.
   */
  var paddingDp: Float = 0f

  /**
   * La frazione di risoluzione a cui catturare, prima del fattore di qualità: quella di oggi
   * (`glassResolutionScale` per `backdropResolution` dell'ottica), mai sotto [GlassLiquidMinCaptureScale].
   *
   * Si decide sulla sfocatura **del ruolo**, prima della regola della taglia: l'innesto la fissa in
   * composizione (`glassLiquidCaptureScale`), quando la taglia non c'è ancora, e la ricetta deve dire lo stesso
   * numero perché scene e lastre vere catturino uguale. Su una lastra grande, che sfoca un poco di più, vuol
   * dire una risoluzione un poco più alta del necessario: dalla parte sicura. Con la smerigliatura di 12 dp le
   * due strade davano lo stesso numero (oltre 10 dp la scala tocca il pavimento); con i 4,5 dp di serie no.
   */
  var captureScale: Float = 1f

  // ---- Il corpo (GlassBodyUniformsBlock) ---------------------------------------------------------------------

  var thicknessDp: Float = 0f

  /** Già **negato**, come lo vuole lo shader: un valore negativo campiona verso l'interno. */
  var refractionDp: Float = 0f
  var dome: Float = 0f
  var dispersion: Float = 0f

  /** L'ingrandimento attorno al centro del pezzo: zero a riposo, lo accende solo [glassTravelRecipe]. */
  var magnify: Float = 0f
  var materialize: Float = 0f
  var veilColor: Color = Color.Transparent
  var veilAdapt: Float = 0f
  var tintColor: Color = Color.Transparent
  var tintMode: Float = 0f
  var tintFollow: Float = 0f
  var bodyShade: Color = Color.Transparent
  var bodyShadeDepthDp: Float = 0f
  var bodyShadeLeanX: Float = 0f
  var bodyShadeLeanY: Float = 0f
  var dim: Float = 0f
  var grain: Float = 0f
  var aa: Float = GlassLiquidAa

  // ---- La luce (GlassLightBlock) -----------------------------------------------------------------------------

  var rimWidthDp: Float = 0f
  var rimDark: Color = Color.Transparent
  var rimFeather: Float = 0f
  var rimLift: Float = 0f
  var reflexDirX: Float = 0f
  var reflexDirY: Float = -1f
  var reflexHalfWidth: Float = 0f
  var reflexOppositeHalfWidth: Float = 0f
  var reflexStrength: Float = 0f
  var reflexDepthDp: Float = 0f
  var reflexFloor: Float = 0f
  var reflexHalo: Float = 0f
  var lightColor: Color = Color.White

  /** `glow` quando la pressione è piena; il `glow` di adesso è questo per la pressione. */
  var glowPeak: Float = 0f
  var glowFill: Float = 0f

  // ---- L'ombra (GlassShadowSpec, e gli uniform di GlassTravelShadowBlock) ------------------------------------

  var shadowContactRadiusDp: Float = 0f
  var shadowAmbientRadiusDp: Float = 0f
  var shadowOffsetXDp: Float = 0f
  var shadowOffsetYDp: Float = 0f
  var shadowContactAlpha: Float = 0f
  var shadowAmbientAlpha: Float = 0f
  var shadowColor: Color = Color.Black

  /** Vero se a riposo c'è una luce da sommare (il disegno in `Plus`); il bagliore del tocco lo riaccende. */
  val drawsReflex: Boolean get() = materialize > 0f && reflexStrength > 0f && lightColor.alpha > 0f

  /** Vero se c'è un filo da posare (il disegno in `SrcOver`): falso sul lato scuro di serie. */
  val drawsRim: Boolean get() = materialize > 0f && rimDark.alpha > 0f

  /** Vero se c'è un'ombra da disegnare. */
  val drawsShadow: Boolean
    get() = (shadowContactAlpha + shadowAmbientAlpha) * shadowColor.alpha > GlassShadowMinAlpha

  /**
   * L'ombra come la vuole il nodo a riposo (`Modifier.glassShadow`), o `null` se non c'è. **Alloca**: chi la
   * legge in disegno la tiene finché i numeri non cambiano.
   */
  fun shadowSpec(): GlassShadowSpec? =
    if (!drawsShadow) {
      null
    } else {
      GlassShadowSpec(
        contactRadius = shadowContactRadiusDp.dp,
        ambientRadius = shadowAmbientRadiusDp.dp,
        offset = DpOffset(shadowOffsetXDp.dp, shadowOffsetYDp.dp),
        contactAlpha = shadowContactAlpha,
        ambientAlpha = shadowAmbientAlpha,
        color = shadowColor,
      )
    }
}

/**
 * La ricetta del materiale `Liquid`.
 *
 * ### Cosa decide chi
 * - il **profilo** decide l'aspetto: velo, bordo, ombra, smerigliatura, lente, e di quanto crescono i grandi;
 * - la **taglia** ([widthDp] x [heightDp]) decide lo spessore: la banda della lente è una frazione del lato
 *   corto, la cupola sparisce sui grandi, e la regola della taglia dell'engine (`glassSizeGain`, per
 *   `sizeResponse` dell'ottica) fa crescere **smerigliatura e ombra**. **Il filo no**: `rimWidthDp` e
 *   `reflexDepthDp` sono gli stessi su un tasto e su un pannello (R4 A1; piano, tabella dei bersagli);
 * - il **lato** ([darkSide]) sceglie bordo, velo e ombra del profilo. In F1A viene dal tema; in F2 dalla sonda.
 *   Sul lato scuro anche il velo dipende dalla taglia: dai piccoli ai grandi passa da [GlassLiquidProfile.darkVeil]
 *   a [GlassLiquidProfile.darkLargeVeil] (vedi [GlassLiquidDarkLargeStartDp]), perché il testo secondario si legga
 *   su fogli, card e pannelli;
 * - i **piccolissimi** (lato corto sotto [GlassLiquidTinyBelowDp]) hanno l'ombra in scala col lato e un
 *   riflesso più tenue e meno profondo; il filo no;
 * - una lastra **annidata** ([nested]: sta dentro un'altra lastra di vetro e ne campiona il materiale) ha il velo
 *   e l'ombra del profilo moltiplicati per [GlassLiquidProfile.nesting] del suo lato: il materiale che campiona
 *   ha già un velo, e posarne un secondo pieno fa una toppa (S1-ter, difetto 4). Filo, riflesso, lente,
 *   smerigliatura, pellicola e tinta restano quelli di una lastra sulla pagina;
 * - l'**ottica** ([optics]) è quella di oggi, e in `Liquid` ogni suo campo è un attenuatore: dal valore più
 *   basso che ha in un ruolo di serie in su il materiale è quello del profilo, sotto ne resta la frazione
 *   (vedi [GlassLiquidFullBlurScale]). Così i ruoli di serie sono un vetro solo, e un'ottica scritta a mano
 *   che chiede zero ottiene zero. **Una eccezione**: `blurScale` oltre quello di serie del ruolo fa crescere la
 *   smerigliatura in proporzione, fino a [GlassLiquidFrostRequestMax] (vedi [glassOpticsFrost]): chi chiede
 *   più smerigliatura la ottiene. Tradotti: `blurScale` → sfocatura; `refractionHeight` → banda;
 *   `refractionAmount` → spostamento; `highlightAlpha` → forza di filo e riflesso; `shadowAlpha` → forza
 *   dell'ombra (zero = nessuna: `Bar`, `Content`). Fanno eccezione, perché non sono attenuatori: `vibrancy` →
 *   saturazione **in proporzione** (1 resta «spenta», 1,6 dà quella del profilo, di più dà di più), e
 *   `dispersion`, `pressedDepthBoost`, `edgeFloor`, `backdropResolution` (mai sotto
 *   [GlassLiquidMinCaptureScale]), `sizeResponse` come oggi. **Non si leggono**:
 *   `depthEffect` (la cupola dipende dalla taglia), `highlightWidth` e `highlightAngle` (il filo è del
 *   profilo), `innerShadow…`, `shadowRadius`;
 * - il **ruolo** ([role]) oggi sceglie soltanto l'ottica di serie, quando [optics] non è data: nessuna misura
 *   di R4 o R5 distingue il materiale per ruolo.
 *
 * ### `intensity`, `opticalDepth`, qualità, pressione (piano 3.3)
 * - [intensity] (0 = il vetro non c'è, 1 = pieno): è `materialize`, che nello shader scala lente, velo, tinta,
 *   ombreggiatura e luce; qui scala la saturazione, la mescola `w` (da nitido a smerigliato) e l'alfa
 *   dell'ombra. **Il raggio non cambia**, quindi il margine della cattura resta fermo. A zero: `w` = 1,
 *   saturazione 1, `materialize` 0, nessuna ombra, cioè lo sfondo com'è;
 * - [opticalDepth]: lo spostamento della lente; bordo e ombra attraverso `edgeFloor` (`glassEdgeDepth`, come
 *   oggi). Non velo né smerigliatura. La banda della lente resta: a profondità zero la lente è piatta perché
 *   lo spostamento è zero, e il margine della cattura non si muove;
 * - [quality] (`FluidGlassQuality`, 0..1): spostamento della lente, forza di bordo e ombra, raggio di
 *   sfocatura fra [FluidGlassBlurFloor] e 1, frangia spenta fino a 0,75. Mai il velo. La risoluzione della
 *   cattura la scala chi monta la catena (`backdropScaleFactor`), come oggi;
 * - [pressed]: approfondisce lo spostamento di `pressedDepthBoost`, come oggi.
 *
 * ### Velo, pellicola, tinta
 * Di serie il velo è quello del profilo. [film] specificato (la pellicola di una tinta che si legge
 * `GlassTintMode.Fixed`: scritta a mano, di serie con un colore cambiato, o resa densa dal contrasto di sistema;
 * vedi `glassTintLiquidMode`) **prende il posto del velo**: è il colore dipinto com'è, cioè il
 * comportamento di oggi, e sommarlo al velo darebbe un vetro opaco. [tint] specificato è la tinta di Apple
 * (`Colored`): sta sopra il velo e la sua luminosità segue lo sfondo.
 *
 * @param optics l'ottica chiesta, già passata da `sanitized()`.
 * @param nested vero per una lastra dentro un'altra lastra di vetro. Non lo decide chi chiama a mano: lo dice il
 *   nodo del vetro, che sa su quale pannello sta (`GlassLiquidSurface.nested`). Falso = una lastra sulla pagina.
 * @param largeVeil quanto la lastra ha del velo dei grandi, 0..1, se lo decide chi chiama; non finito (di serie) =
 *   dalla taglia ([glassLiquidLargeVeilGain]). Lo passa solo una superficie che viaggia (F1B.2,
 *   `PhysicsLargeVeil`): fra un tasto e un pannello il lato corto attraversa la fascia dei due veli in due o tre
 *   fotogrammi, e il velo che lo seguiva cambiava di colpo.
 * @param out il contenitore da riscrivere; di serie uno nuovo.
 */
internal fun glassRecipe(
  role: GlassRole,
  widthDp: Float,
  heightDp: Float,
  darkSide: Boolean,
  variant: GlassLiquidVariant = GlassLiquidVariant.Regular,
  optics: GlassOptics = GlassDefaults.optics(role),
  intensity: Float = 1f,
  opticalDepth: Float = 1f,
  quality: Float = 1f,
  pressed: Float = 0f,
  film: Color = Color.Unspecified,
  tint: Color = Color.Unspecified,
  nested: Boolean = false,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
  largeVeil: Float = Float.NaN,
  out: GlassRecipe = GlassRecipe(),
): GlassRecipe {
  val amount = clampGlassUnit(intensity)
  val q = clampGlassUnit(quality)
  val press = clampGlassUnit(pressed)
  val short = finiteSide(min(widthDp, heightDp))
  val gain = glassSizeGain(short) * clampGlassUnit(optics.sizeResponse)
  val clear = variant == GlassLiquidVariant.Clear

  // ---- La catena ---------------------------------------------------------------------------------------------
  val frost = profile.frost
  // La smerigliatura del ruolo, prima della regola della taglia: è anche quella su cui si decide la
  // risoluzione della cattura (vedi `captureScale`).
  val roleBlur = frost.blur.value * glassOpticsFrost(optics.blurScale, glassLiquidRoleBlurScale(role))
  val fullBlur = roleBlur * (1f + profile.growth.frost * gain)
  out.blurDp = fullBlur * (FluidGlassBlurFloor + (1f - FluidGlassBlurFloor) * q)
  out.mix = 1f - (1f - (if (clear) 1f else clampGlassUnit(frost.mix))) * amount
  // `vibrancy` 1 resta «nessun filtro»; quella di serie (1,6) dà la saturazione del profilo; in mezzo, in
  // proporzione. Sale con il materiale, come oggi: una barra i cui colori si accendono un fotogramma prima di
  // avere la sfocatura si annuncia come un effetto.
  val saturation = 1f + (optics.vibrancy - 1f) * (frost.saturation - 1f) / (GlassLiquidReferenceVibrancy - 1f)
  out.saturation = 1f + (saturation - 1f) * amount

  // ---- La lente ----------------------------------------------------------------------------------------------
  val lens = profile.lens
  val band = min(min(lens.band * short, GlassLiquidLensBandMaxDp), short / 2f) *
    glassOpticsDimmer(optics.refractionHeight.value, GlassLiquidFullRefractionHeightDp)
  val bend = band * lens.bend *
    glassOpticsDimmer(optics.refractionAmount.value, GlassLiquidFullRefractionAmountDp) *
    clampGlassUnit(opticalDepth) * q * (1f + press * optics.pressedDepthBoost)
  out.thicknessDp = band
  out.refractionDp = -bend
  out.dome = lens.dome * clampGlassUnit(
    (GlassLiquidDomeFadeEndDp - short) / (GlassLiquidDomeFadeEndDp - GlassLiquidDomeFadeStartDp),
  )
  // Tre campioni per pixel sono la prima cosa che se ne va: a pagina in movimento la frangia non si distingue.
  out.dispersion = if (optics.dispersion && q > 0.75f) GlassLiquidDispersion else 0f
  out.magnify = 0f
  out.paddingDp = max(0f, fullBlur - band)
  out.captureScale = max(
    glassResolutionScale(roleBlur) * optics.backdropResolution,
    GlassLiquidMinCaptureScale,
  ).coerceAtMost(1f)

  // ---- Velo, tinta, ombreggiatura ----------------------------------------------------------------------------
  // Dai piccoli ai grandi il velo passa da quello dei piccoli a quello dei grandi del lato (sul chiaro sono lo
  // stesso), senza scatti: un tasto largo e una card piccola restano vicini.
  val veil = profile.veil(darkSide)
  val bigVeil = profile.largeVeil(darkSide)
  val large = if (largeVeil.isFinite()) clampGlassUnit(largeVeil) else glassLiquidLargeVeilGain(short)
  // Annidata: il materiale sotto ha già il suo velo; del velo di questa lastra ne resta la frazione del profilo.
  // Si moltiplica l'alfa e non il colore: il grigio resta quello del velo, scende quanto copre.
  val nesting = if (nested) profile.nesting(darkSide) else null
  val sharedVeil = lerpSrgb(veil.color, bigVeil.color, large)
  val veilColor = if (nesting == null) {
    sharedVeil
  } else {
    sharedVeil.copy(alpha = sharedVeil.alpha * clampGlassUnit(nesting.veil))
  }
  out.materialize = amount
  val fixedFilm = film.isSpecified
  out.veilColor = if (fixedFilm || clear) veilColor.copy(alpha = 0f) else veilColor
  out.veilAdapt = if (fixedFilm || clear) 0f else mixFloat(veil.adapt, bigVeil.adapt, large)
  when {
    fixedFilm -> {
      out.tintColor = film
      out.tintMode = 0f
    }
    tint.isSpecified -> {
      out.tintColor = tint
      out.tintMode = 1f
    }
    else -> {
      out.tintColor = Color.Transparent
      out.tintMode = 0f
    }
  }
  out.tintFollow = GlassLiquidTintFollow
  out.bodyShade = lerpSrgb(veil.shade, bigVeil.shade, large)
  out.bodyShadeDepthDp = min(mixFloat(veil.shadeDepth.value, bigVeil.shadeDepth.value, large), short / 2f)
  out.bodyShadeLeanX = mixFloat(veil.shadeLeanX, bigVeil.shadeLeanX, large)
  out.bodyShadeLeanY = mixFloat(veil.shadeLeanY, bigVeil.shadeLeanY, large)
  out.dim = if (clear) GlassLiquidClearDim else 0f
  out.grain = GlassLiquidGrain
  out.aa = GlassLiquidAa

  // ---- La luce -----------------------------------------------------------------------------------------------
  // Quanto bordo mostra la lastra: la profondità (con il suo pavimento) per la qualità, come oggi; l'intensità
  // no, perché è `materialize`, che lo shader applica a tutta la luce.
  val edgeAmount = glassEdgeDepth(optics.edgeFloor, opticalDepth) * q
  val lightAmount = edgeAmount * glassOpticsDimmer(optics.highlightAlpha, GlassLiquidFullHighlightAlpha)
  val edge = profile.edge(darkSide)
  out.rimWidthDp = edge.rimWidth.value
  out.rimDark = edge.rim.copy(alpha = edge.rim.alpha * lightAmount)
  out.rimFeather = edge.rimFeather
  out.rimLift = edge.rimLift
  out.reflexDirX = edge.reflexDirX
  out.reflexDirY = edge.reflexDirY
  out.reflexHalfWidth = edge.reflexHalfWidth
  out.reflexOppositeHalfWidth = edge.reflexOppositeHalfWidth
  // Un piccolissimo tiene il filo (è assoluto) ma non l'anello chiaro: il riflesso cala in forza sotto
  // [GlassLiquidTinyBelowDp] e rientra al più per una frazione del lato (a 44 dp sono i 7 dp di serie).
  val tiny = clampGlassUnit(
    (short - GlassLiquidTinyReflexFromDp) / (GlassLiquidTinyBelowDp - GlassLiquidTinyReflexFromDp),
  )
  out.reflexStrength = edge.reflexStrength * lightAmount *
    (GlassLiquidTinyReflexFloor + (1f - GlassLiquidTinyReflexFloor) * tiny)
  out.reflexDepthDp = min(edge.reflexDepth.value, short * GlassLiquidTinyReflexDepthFraction)
  out.reflexFloor = edge.reflexFloor
  out.reflexHalo = edge.reflexHalo
  out.lightColor = edge.light
  out.glowPeak = GlassLiquidGlowPeak
  out.glowFill = GlassLiquidGlowFill

  // ---- L'ombra -----------------------------------------------------------------------------------------------
  // Cresce con la regola della taglia, cala sotto i piccoli se il profilo lo chiede, e segue intensità,
  // profondità e qualità come oggi. I raggi non dipendono da nessuna delle tre: le maschere restano buone.
  // Sotto [GlassLiquidTinyBelowDp] raggi e scostamento scendono col lato: l'ombra larga di un tasto sotto un
  // pallino da 26 dp sarebbe più grande dell'oggetto.
  val shadow = profile.shadow(darkSide)
  val growth = profile.growth.shadow
  val reach = (1f + growth.radius * gain) * clampGlassUnit(short / GlassLiquidTinyBelowDp)
  val small = clampGlassUnit(
    (short - GlassLiquidSmallShadowStartDp) / (GlassLiquidSmallShadowEndDp - GlassLiquidSmallShadowStartDp),
  )
  val shadowAmount = amount * edgeAmount *
    glassOpticsDimmer(optics.shadowAlpha, GlassLiquidFullShadowAlpha) *
    (1f + growth.alpha * gain) *
    (profile.smallShadow + (1f - profile.smallShadow) * small) *
    (if (nesting == null) 1f else clampGlassUnit(nesting.shadow))
  out.shadowContactRadiusDp = shadow.contactRadius.value * reach
  out.shadowAmbientRadiusDp = shadow.ambientRadius.value * reach
  out.shadowOffsetXDp = shadow.offset.x.value * reach
  out.shadowOffsetYDp = shadow.offset.y.value * reach
  out.shadowContactAlpha = clampGlassUnit(shadow.contactAlpha * shadowAmount)
  out.shadowAmbientAlpha = clampGlassUnit(shadow.ambientAlpha * shadowAmount)
  out.shadowColor = shadow.color
  return out
}

/**
 * Un campo di un'ottica di oggi come attenuatore: uno da [full] in su, la frazione sotto, zero a zero. Vedi
 * [GlassLiquidFullBlurScale].
 */
internal fun glassOpticsDimmer(value: Float, full: Float): Float =
  if (!value.isFinite() || !(full > 0f)) 0f else (value / full).coerceIn(0f, 1f)

/**
 * Il `blurScale` di un'ottica come fattore sulla smerigliatura del profilo: un attenuatore sotto
 * [GlassLiquidFullBlurScale] (come gli altri campi), uno fino a [roleBlurScale] (il più alto dei valori di
 * serie del ruolo), e oltre la **crescita** in proporzione, fino a [GlassLiquidFrostRequestMax]. Non scende mai
 * quando [blurScale] sale: chi chiede più smerigliatura non ne ottiene meno.
 *
 * Il pannello del lettore di Fluidify (`Modal`, `blurScale` 7 su 3,5 di serie) prende due volte la
 * smerigliatura del profilo: 9 dp invece di 4,5, prima della regola della taglia.
 */
internal fun glassOpticsFrost(blurScale: Float, roleBlurScale: Float): Float = when {
  !blurScale.isFinite() -> 0f
  !(roleBlurScale > 0f) || blurScale <= roleBlurScale -> glassOpticsDimmer(blurScale, GlassLiquidFullBlurScale)
  else -> min(blurScale / roleBlurScale, GlassLiquidFrostRequestMax)
}

/**
 * Il `blurScale` più alto fra le ottiche di serie che l'engine usa con [role]: per `Interactive` anche quella
 * dei tasti di vetro, per `Content` quella delle card impilate, per `Modal` le quattro dei pannelli. Fin lì
 * un'ottica non chiede «più del ruolo» (vedi [glassOpticsFrost]). Letto dalle ottiche, non riscritto: se un
 * ruolo di serie cambia, la soglia lo segue.
 */
internal fun glassLiquidRoleBlurScale(role: GlassRole): Float = GlassLiquidRoleBlurScales[role.ordinal]

private val GlassLiquidRoleBlurScales: FloatArray by lazy {
  FloatArray(GlassRole.entries.size) { index ->
    val role = GlassRole.entries[index]
    val stock = when (role) {
      GlassRole.Interactive -> listOf(GlassDefaults.optics(role), GlassDefaults.buttonOptics())
      GlassRole.Content -> listOf(GlassDefaults.optics(role), GlassDefaults.stackedContentOptics())
      GlassRole.Modal -> listOf(
        GlassDefaults.optics(role),
        GlassDefaults.panelOptics(),
        GlassDefaults.panelOptics(compact = true),
        GlassDefaults.panelOptics(overSharpPage = true),
        GlassDefaults.panelOptics(compact = true, overSharpPage = true),
      )
      else -> listOf(GlassDefaults.optics(role))
    }
    stock.maxOf { it.blurScale }
  }
}

/**
 * Quanto una lastra di lato corto [shortSideDp] ha del velo dei grandi: zero fino a
 * [GlassLiquidDarkLargeStartDp], uno da [GlassLiquidDarkLargeEndDp], in mezzo un passo morbido (smoothstep).
 */
internal fun glassLiquidLargeVeilGain(shortSideDp: Float): Float {
  if (!shortSideDp.isFinite()) return 0f
  val t = clampGlassUnit(
    (shortSideDp - GlassLiquidDarkLargeStartDp) / (GlassLiquidDarkLargeEndDp - GlassLiquidDarkLargeStartDp),
  )
  return t * t * (3f - 2f * t)
}

/**
 * Due colori sRGB mescolati componente per componente, alfa compresa (non in Oklab come `lerp` di Compose: il
 * banco rifà lo stesso conto, e due veli dello stesso grigio devono restare grigi). Agli estremi restituisce i
 * colori stessi, al bit.
 */
private fun lerpSrgb(a: Color, b: Color, t: Float): Color = when {
  t <= 0f -> a
  t >= 1f -> b
  else -> Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
  )
}

private fun mixFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun finiteSide(value: Float): Float = if (value.isFinite()) max(value, 0f) else 0f

// ------------------------------------------------------------------------------------------------------------
// In viaggio (F1B.1)
// ------------------------------------------------------------------------------------------------------------

/**
 * Il margine della cattura di una superficie che **viaggia**, deciso una volta per tutta la sua vita.
 *
 * Quello di una lastra ferma è `max(0, sfocatura − spessore della lente)` ([GlassRecipe.paddingDp]), e lo
 * spessore della lente è una frazione del lato corto: un tasto da 44 dp che diventa un pannello lo muoverebbe a
 * ogni fotogramma, e un margine che cambia rifà la cattura (invariante 1, piano 3.7). Qui la lente non si sconta
 * e la taglia non c'entra: è la sfocatura piena del ruolo alla crescita massima dei grandi. È un tetto, non una
 * stima: una sagoma che tocca il bordo del proprio nodo ha sotto di sé pixel veri a qualunque taglia arrivi.
 * Costa qualche pixel di cattura in più per lato (con la smerigliatura di serie 6,75 dp).
 *
 * Il **raggio** di sfocatura invece può cambiare lungo il viaggio, e cambia (cresce con la sagoma): un raggio
 * nuovo è un effetto nuovo, non una cattura nuova.
 *
 * L'ombra in corpo (F1B.2) non c'entra: nel margine il corpo non legge lo sfondo per l'ombra, e il layer di una
 * superficie è comunque ritagliato sul suo nodo (`DrawBackdropModifier`, `clip = true`). Perché l'ombra in viaggio
 * si veda per intero il **nodo** deve contenerla: vedi [glassTravelShadowReachDp].
 *
 * @param optics i capi del viaggio (l'ottica d'arrivo e, se c'è, quella di partenza): vale il più largo.
 */
internal fun glassTravelPaddingDp(
  role: GlassRole,
  optics: GlassOptics,
  opticsFrom: GlassOptics? = null,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
): Float {
  val roleScale = glassLiquidRoleBlurScale(role)
  fun full(o: GlassOptics): Float =
    profile.frost.blur.value * glassOpticsFrost(o.blurScale, roleScale) * (1f + max(profile.growth.frost, 0f))
  val widest = max(full(optics), opticsFrom?.let(::full) ?: 0f)
  return if (widest.isFinite()) max(widest, 0f) else 0f
}

/**
 * Fin dove arriva, oltre il contorno, l'ombra in corpo di una superficie che viaggia con [optics] (F1B.2), in dp: il
 * più largo dei due lati del profilo, alla crescita [gain] della regola della taglia (0 = un tasto, 1 = un pannello
 * grande), zero se l'ottica non ha ombra. Il mezzo pixel della piattaforma è contato come mezzo dp: per eccesso, a
 * ogni densità.
 *
 * Serve a chi ha un nodo **stretto** attorno alla sagoma: il layer del vetro è ritagliato sul nodo, e un'ombra che
 * ne esce è tagliata. La barra fra le pagine allarga di tanto il nodo della superficie che viaggia; i palchi grandi
 * quanto la finestra (menu, modale, Playground) non ne hanno bisogno. Con l'ombra B di serie, per un tasto: 66 dp.
 */
internal fun glassTravelShadowReachDp(
  optics: GlassOptics,
  gain: Float = 1f,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
): Float {
  if (!(optics.shadowAlpha > 0f)) return 0f
  val grow = 1f + max(profile.growth.shadow.radius, 0f) * clampGlassUnit(gain)
  var reach = 0f
  for (dark in booleanArrayOf(false, true)) {
    val spec = profile.shadow(dark)
    if (!(spec.contactAlpha + spec.ambientAlpha > 0f) || !(spec.color.alpha > 0f)) continue
    reach = max(
      reach,
      GlassShadowProfile.reach(
        contactRadius = spec.contactRadius.value * grow,
        ambientRadius = spec.ambientRadius.value * grow,
        contactAlpha = spec.contactAlpha,
        ambientAlpha = spec.ambientAlpha,
        slide = kotlin.math.hypot(spec.offset.x.value, spec.offset.y.value) * grow,
      ),
    )
  }
  return if (reach.isFinite()) reach else 0f
}

/**
 * La ricetta **in viaggio**: [recipe] (quella di riposo per la taglia di adesso) con il materiale di
 * [GlassLiquidTravel] al peso [motion]. Riscrive [recipe] sul posto e la restituisce.
 *
 * Con [motion] a zero non tocca niente, nemmeno un bit: a viaggio fermo, alla partenza e all'arrivo il
 * materiale è quello di riposo. Cambia la mescola `w` (verso il nitido), lo spostamento della lente, la cupola,
 * l'ingrandimento e la frangia; **non** il raggio di sfocatura né la banda della lente (tengono il margine), né
 * velo, tinta, bordo e ombra: una sagoma che viaggia resta dello stesso vetro, più limpido.
 *
 * @param motion quanto la sagoma si sta muovendo, 0..1 (`LiquidDrop.lens`).
 * @param quality la qualità del vetro (`FluidGlassQuality`): sotto 0,75 la frangia non si accende, come a riposo.
 */
internal fun glassTravelRecipe(
  recipe: GlassRecipe,
  travel: GlassLiquidTravel,
  motion: Float,
  quality: Float = 1f,
): GlassRecipe {
  val w = clampGlassUnit(motion)
  if (w <= 0f) return recipe
  val amount = clampGlassUnit(recipe.materialize)
  recipe.mix = recipe.mix + (1f - recipe.mix) * clampGlassUnit(travel.clarity) * w * amount
  recipe.refractionDp = recipe.refractionDp * (1f + max(travel.bend, 0f) * w)
  recipe.dome = recipe.dome + (1f - recipe.dome) * clampGlassUnit(travel.dome) * w
  recipe.magnify = max(travel.magnify, 0f) * w
  if (quality > 0.75f) recipe.dispersion = max(recipe.dispersion, clampGlassUnit(travel.dispersion) * w)
  return recipe
}

/**
 * Due ricette mescolate campo per campo: [a] a zero, [b] a uno, in mezzo `a + (b − a) · t`. Ai due capi
 * restituisce i numeri del capo, al bit (non un'interpolazione che ci arriva vicino): è ciò che fa di un viaggio
 * fra due materiali una cosa che parte dal primo e arriva al secondo senza uno scalino.
 *
 * La usa Fluid-physics per la tinta di partenza e d'arrivo (`tintFrom`, `tint`): le due ricette nascono dalla
 * stessa ottica e dalla stessa taglia e differiscono solo in velo e tinta, quindi su tutti gli altri campi
 * `a + (a − a) · t` restituisce `a`. I colori si mescolano in sRGB componente per componente, come il resto
 * della ricetta. Scrive in [out], che può essere [a] o [b].
 */
internal fun lerpGlassRecipe(a: GlassRecipe, b: GlassRecipe, t: Float, out: GlassRecipe = GlassRecipe()): GlassRecipe {
  val f = clampGlassUnit(t)
  fun mix(x: Float, y: Float): Float = if (f <= 0f) x else if (f >= 1f) y else x + (y - x) * f
  fun mix(x: Color, y: Color): Color = lerpSrgb(x, y, f)
  out.saturation = mix(a.saturation, b.saturation)
  out.blurDp = mix(a.blurDp, b.blurDp)
  out.mix = mix(a.mix, b.mix)
  out.paddingDp = mix(a.paddingDp, b.paddingDp)
  out.captureScale = mix(a.captureScale, b.captureScale)
  out.thicknessDp = mix(a.thicknessDp, b.thicknessDp)
  out.refractionDp = mix(a.refractionDp, b.refractionDp)
  out.dome = mix(a.dome, b.dome)
  out.dispersion = mix(a.dispersion, b.dispersion)
  out.magnify = mix(a.magnify, b.magnify)
  out.materialize = mix(a.materialize, b.materialize)
  out.veilColor = mix(a.veilColor, b.veilColor)
  out.veilAdapt = mix(a.veilAdapt, b.veilAdapt)
  out.tintColor = mix(a.tintColor, b.tintColor)
  out.tintMode = mix(a.tintMode, b.tintMode)
  out.tintFollow = mix(a.tintFollow, b.tintFollow)
  out.bodyShade = mix(a.bodyShade, b.bodyShade)
  out.bodyShadeDepthDp = mix(a.bodyShadeDepthDp, b.bodyShadeDepthDp)
  out.bodyShadeLeanX = mix(a.bodyShadeLeanX, b.bodyShadeLeanX)
  out.bodyShadeLeanY = mix(a.bodyShadeLeanY, b.bodyShadeLeanY)
  out.dim = mix(a.dim, b.dim)
  out.grain = mix(a.grain, b.grain)
  out.aa = mix(a.aa, b.aa)
  out.rimWidthDp = mix(a.rimWidthDp, b.rimWidthDp)
  out.rimDark = mix(a.rimDark, b.rimDark)
  out.rimFeather = mix(a.rimFeather, b.rimFeather)
  out.rimLift = mix(a.rimLift, b.rimLift)
  out.reflexDirX = mix(a.reflexDirX, b.reflexDirX)
  out.reflexDirY = mix(a.reflexDirY, b.reflexDirY)
  out.reflexHalfWidth = mix(a.reflexHalfWidth, b.reflexHalfWidth)
  out.reflexOppositeHalfWidth = mix(a.reflexOppositeHalfWidth, b.reflexOppositeHalfWidth)
  out.reflexStrength = mix(a.reflexStrength, b.reflexStrength)
  out.reflexDepthDp = mix(a.reflexDepthDp, b.reflexDepthDp)
  out.reflexFloor = mix(a.reflexFloor, b.reflexFloor)
  out.reflexHalo = mix(a.reflexHalo, b.reflexHalo)
  out.lightColor = mix(a.lightColor, b.lightColor)
  out.glowPeak = mix(a.glowPeak, b.glowPeak)
  out.glowFill = mix(a.glowFill, b.glowFill)
  out.shadowContactRadiusDp = mix(a.shadowContactRadiusDp, b.shadowContactRadiusDp)
  out.shadowAmbientRadiusDp = mix(a.shadowAmbientRadiusDp, b.shadowAmbientRadiusDp)
  out.shadowOffsetXDp = mix(a.shadowOffsetXDp, b.shadowOffsetXDp)
  out.shadowOffsetYDp = mix(a.shadowOffsetYDp, b.shadowOffsetYDp)
  out.shadowContactAlpha = mix(a.shadowContactAlpha, b.shadowContactAlpha)
  out.shadowAmbientAlpha = mix(a.shadowAmbientAlpha, b.shadowAmbientAlpha)
  out.shadowColor = mix(a.shadowColor, b.shadowColor)
  return out
}
