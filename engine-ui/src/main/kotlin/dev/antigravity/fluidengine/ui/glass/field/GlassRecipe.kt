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
 * campo, `GlassFieldPlan`), `magnify` (la goccia in moto), `lightInBody` (lo decide il motore: 1 in viaggio),
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
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
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
  out.paddingDp = max(0f, fullBlur - band)
  out.captureScale = max(
    glassResolutionScale(roleBlur) * optics.backdropResolution,
    GlassLiquidMinCaptureScale,
  ).coerceAtMost(1f)

  // ---- Velo, tinta, ombreggiatura ----------------------------------------------------------------------------
  // Dai piccoli ai grandi il velo passa da quello dei piccoli a quello dei grandi del lato (sul chiaro sono lo
  // stesso), senza scatti: un tasto largo e una card piccola restano vicini.
  val veil = profile.veil(darkSide)
  val largeVeil = profile.largeVeil(darkSide)
  val large = glassLiquidLargeVeilGain(short)
  val veilColor = lerpSrgb(veil.color, largeVeil.color, large)
  out.materialize = amount
  val fixedFilm = film.isSpecified
  out.veilColor = if (fixedFilm || clear) veilColor.copy(alpha = 0f) else veilColor
  out.veilAdapt = if (fixedFilm || clear) 0f else mixFloat(veil.adapt, largeVeil.adapt, large)
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
  out.bodyShade = lerpSrgb(veil.shade, largeVeil.shade, large)
  out.bodyShadeDepthDp = min(mixFloat(veil.shadeDepth.value, largeVeil.shadeDepth.value, large), short / 2f)
  out.bodyShadeLeanX = mixFloat(veil.shadeLeanX, largeVeil.shadeLeanX, large)
  out.bodyShadeLeanY = mixFloat(veil.shadeLeanY, largeVeil.shadeLeanY, large)
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
    (profile.smallShadow + (1f - profile.smallShadow) * small)
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
