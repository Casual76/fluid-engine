package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.theme.FluidGroupOpenEdge
import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment

/**
 * Il vetro **riga per riga** di [fluidGroupedItems] ([FluidGroupedMaterial.GlassSegments]).
 *
 * Esiste per il banco di misura e per nient'altro, e chiederlo e' una scelta esplicita: la regola del design
 * system dice che **il vetro va sul contenitore, mai sulla riga** (`regole.md`), perche' ogni lastra e' una
 * registrazione di layer e una catena di `RenderEffect` sui propri limiti, e dodici righe a schermo sono dodici
 * lastre. Con `sampleOnce` una lastra ferma non ricattura, ma una riga che entra e' una lastra nuova che cattura;
 * se il costo regge su un telefono vero (meno dell'1% di fotogrammi lenti, `FluidGlassDiagnostics.totalCaptures`
 * fermo a schermo fermo) la variante si promuove e questa annotazione cade; se non regge, la lista lunga resta
 * opaca o tinta e il vetro sta su barra, pillola, menu e fogli.
 */
@RequiresOptIn(
  level = RequiresOptIn.Level.ERROR,
  message = "Il vetro per riga viola la regola «vetro sul contenitore»: esiste per il banco di misura della " +
    "lista lunga. Usalo solo per misurarlo, con @OptIn(FluidExperimentalRowGlass::class).",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class FluidExperimentalRowGlass

/**
 * Il fondo delle righe di [fluidGroupedItems].
 *
 * Non e' un `enum` di proposito: un valore nuovo (il vetro, il giorno in cui la prova lo promuove) in un `enum`
 * pubblico rompe ogni `when` esaustivo di un'app. Qui un valore nuovo e' una costante in piu'.
 *
 * - [Opaque], di serie: il fondo di sempre di un [dev.antigravity.fluidengine.ui.theme.FluidListGroup] senza
 *   vetro (`surfaceContainerLow`). Il piu' economico, e quello che la regola consente sempre.
 * - [Tinted]: la pellicola del vetro di contenuto **dipinta**, senza catturare niente. Sopra un canvas ambientale
 *   lascia vedere la lavata (nitida, non rifratta): il ripiego che QLT-03 autorizza quando il vetro non regge.
 *   Senza canvas e' identico a [Opaque].
 * - [GlassSegments]: una lastra di vetro di contenuto per riga, con `sampleOnce`. Vedi [FluidExperimentalRowGlass].
 */
@Immutable
@JvmInline
value class FluidGroupedMaterial private constructor(private val id: Int) {

  internal val isGlass: Boolean get() = id == GlassId
  internal val isTinted: Boolean get() = id == TintedId

  override fun toString(): String = when (id) {
    TintedId -> "Tinted"
    GlassId -> "GlassSegments"
    else -> "Opaque"
  }

  companion object {
    private const val OpaqueId = 0
    private const val TintedId = 1
    private const val GlassId = 2

    /** Il fondo opaco del gruppo. Di serie. */
    val Opaque: FluidGroupedMaterial = FluidGroupedMaterial(OpaqueId)

    /** La pellicola del vetro, dipinta senza cattura. */
    val Tinted: FluidGroupedMaterial = FluidGroupedMaterial(TintedId)

    /** Una lastra di vetro per riga: solo per il banco di misura. */
    @FluidExperimentalRowGlass
    val GlassSegments: FluidGroupedMaterial = FluidGroupedMaterial(GlassId)
  }
}

/** I valori di serie di [fluidGroupedItems]. */
object FluidGroupedItemsDefaults {
  /**
   * Il tipo di contenuto di serie di una riga: tutte le righe di tutte le sezioni si riciclano fra loro. Un'app
   * con righe di forma diversa (una riga di posta e un'intestazione di giorno) passa i suoi tipi.
   */
  const val ContentType: String = "fluid-grouped-row"
}

/**
 * Lo scope di una riga di [fluidGroupedItems]: quello di un item della lista pigra (per `animateItem`,
 * `fillParentMaxWidth`...) piu' il pezzo che la riga occupa nella sezione.
 *
 * Serve a chi disegna *dentro* il pezzo: la sagoma per ritagliare quello che la riga rivela scorrendo di lato
 * (`Modifier.fluidSwipeActions(shape = segmentShape, fill = rowFill)`), il pezzo per un segnaposto che deve
 * avere gli stessi angoli della riga che sostituira'.
 */
@Stable
interface FluidGroupedItemScope : LazyItemScope {
  /** Il pezzo della sezione: `Whole` se e' l'unica riga, `First`/`Last` agli estremi, `Middle` in mezzo. */
  val segment: FluidGroupSegment

  /** La sagoma del pezzo: angoli tondi solo dove la sezione comincia o finisce. Un'istanza stabile per pezzo. */
  val segmentShape: Shape

  /**
   * Un fondo **opaco** che somiglia a quello della riga: quello da dipingere sotto la riga quando si sposta (lo
   * scorrimento di lato), perche' dietro c'e' il colore dell'azione e un fondo trasparente lo lascerebbe vedere
   * attraverso il testo.
   */
  val rowFill: Color
}

/**
 * Le righe di una sezione come **un item della lista pigra per riga**, raggruppate: angoli tondi solo sulla prima
 * e sull'ultima, separatori fra l'una e l'altra, nessun vuoto in mezzo.
 *
 * ### Perche' esiste accanto a [fluidGlassGroups]
 *
 * [fluidGlassGroups] mette otto righe in un pannello, e il pannello e' l'item della lista. Per una sezione che
 * arriva ordinata e non cambia va benissimo, e resta com'e'. Per un elenco lungo che cambia mentre lo si guarda
 * (una casella che riceve, una lista paginata, un elenco da cui le righe escono con uno scorrimento) ha tre
 * limiti che non si aggirano dall'esterno:
 *
 * - **le chiavi sono per posizione** (`"$key:3"`): se arriva una riga in cima, ogni pannello contiene le righe
 *   spostate di una, e chi stava leggendo a meta' lista le vede scorrere di un posto. Qui ogni riga ha la chiave
 *   che le da' il chiamante (l'identificativo del dato), e la lista si ancora a quella: una riga che entra sopra
 *   la posizione dell'utente **non sposta** quello che sta guardando;
 * - **la lista e' materializzata** (`List<T>`): una lista paginata non e' una `List` e a 50 000 righe non si
 *   tiene tutto in memoria. Qui l'API e' per indice (quante, la chiave di, il tipo di, il contenuto di), cosi'
 *   un'app usa Paging (`itemKey`, segnaposto) senza che l'engine dipenda da Paging;
 * - **niente animazione per riga**: `animateItem` funziona solo su un item diretto. Qui ogni riga lo e', quindi
 *   entra, esce e si sposta con le molle di [FluidMotion] (spente col movimento ridotto).
 *
 * ### Come si richiude il vuoto
 *
 * La lista mette fra due item lo spazio di [LocalFluidItemSpacing], uguale per tutti. Fra due righe della stessa
 * sezione non ci deve essere: la riga che non e' l'ultima **dichiara** un'altezza minore di quella che disegna,
 * esattamente di quello spazio, e la successiva viene posata a filo (lo stesso trucco di [fluidGlassGroups]). Il
 * contenuto della riga resta misurato e posato alla sua altezza intera dentro l'item, quindi anche la fascia in
 * fondo, che sta fuori dai limiti dichiarati, riceve il dito (Compose non ritaglia il tocco di un figlio ai limiti
 * del genitore che non ritaglia). Il separatore da 0,5 dp lo disegna la riga di sopra, rientrato di 16 dp dal lato
 * d'inizio come [dev.antigravity.fluidengine.ui.theme.FluidListDivider].
 *
 * ### Dove si rompe
 *
 * - Quando una riga esce, quella che diventa l'ultima cambia angoli **di colpo**: un raggio animato fuori da
 *   Fluid-physics e' la regola che non si piega. Con l'uscita in dissolvenza non si nota; va guardato nei
 *   fotogrammi.
 * - Le chiavi devono essere uniche **in tutta la lista** e salvabili in un `Bundle` (stringhe, numeri): due
 *   sezioni con gli stessi identificativi si prefissano (`"posta:$id"`).
 * - Inserire in cima non sposta niente solo se la prima riga visibile ha una chiave: senza [key] la lista si
 *   ancora all'indice, e le righe scorrono.
 * - Un segnaposto di Paging (`null` all'indice) lo disegna il chiamante, alla stessa altezza della riga
 *   (`FluidSkeletonRow`), dentro lo stesso scope: il pezzo e gli angoli restano giusti.
 * - [FluidFastScroller] conta gli item della lista (`totalItemsCount`): con Paging servono i segnaposto attivi per
 *   avere il totale, e l'etichetta per indice deve essere O(1) (un indice dei mesi calcolato dall'app). Gli item
 *   prima della sezione (il titolo di [FluidScreen], un'intestazione) spostano gli indici: l'etichetta li sottrae.
 * - Il vetro **non** e' di serie: vedi [FluidGroupedMaterial] e [FluidExperimentalRowGlass].
 *
 * @param count quante righe. Zero o meno: non emette niente.
 * @param key la chiave stabile della riga all'indice dato (l'identificativo del dato, mai l'indice). Null: senza
 *   chiave, e la lista si ancora agli indici (sconsigliato per un elenco che cambia).
 * @param contentType il tipo di contenuto della riga all'indice, per il riciclo dei nodi.
 * @param material il fondo delle righe; [FluidGroupedMaterial.Opaque] di serie.
 * @param dividers il separatore fra due righe della sezione. Spento per righe che ne disegnano uno loro.
 * @param animateItems `animateItem` su ogni riga (entrata, uscita, spostamento con le molle di [FluidMotion]).
 *   Col movimento ridotto non anima comunque; sul vetro solo lo spostamento (mai un'alfa sopra una lastra).
 * @param itemContent la riga all'indice: di solito una [dev.antigravity.fluidengine.ui.theme.FluidListRow].
 */
fun LazyListScope.fluidGroupedItems(
  count: Int,
  key: ((index: Int) -> Any)? = null,
  contentType: (index: Int) -> Any? = { FluidGroupedItemsDefaults.ContentType },
  material: FluidGroupedMaterial = FluidGroupedMaterial.Opaque,
  dividers: Boolean = true,
  animateItems: Boolean = true,
  itemContent: @Composable FluidGroupedItemScope.(index: Int) -> Unit,
) {
  if (count <= 0) return
  items(count = count, key = key, contentType = contentType) { index ->
    FluidGroupedRow(
      itemScope = this,
      index = index,
      count = count,
      material = material,
      dividers = dividers,
      animateItems = animateItems,
      itemContent = itemContent,
    )
  }
}

/**
 * [fluidGroupedItems] su una lista gia' in memoria, come `items(list)`: la chiave e il tipo si chiedono
 * all'elemento. Per un elenco paginato resta la forma per indice.
 */
fun <T> LazyListScope.fluidGroupedItems(
  items: List<T>,
  key: ((item: T) -> Any)? = null,
  contentType: (item: T) -> Any? = { FluidGroupedItemsDefaults.ContentType },
  material: FluidGroupedMaterial = FluidGroupedMaterial.Opaque,
  dividers: Boolean = true,
  animateItems: Boolean = true,
  itemContent: @Composable FluidGroupedItemScope.(item: T) -> Unit,
) {
  fluidGroupedItems(
    count = items.size,
    key = key?.let { itemKey -> { index: Int -> itemKey(items[index]) } },
    contentType = { index -> contentType(items[index]) },
    material = material,
    dividers = dividers,
    animateItems = animateItems,
  ) { index -> itemContent(items[index]) }
}

@Composable
private fun FluidGroupedRow(
  itemScope: LazyItemScope,
  index: Int,
  count: Int,
  material: FluidGroupedMaterial,
  dividers: Boolean,
  animateItems: Boolean,
  itemContent: @Composable FluidGroupedItemScope.(index: Int) -> Unit,
) {
  val segment = fluidGroupedSegment(index, count)
  val shape = fluidGroupedSegmentShape(segment)
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val canvas = LocalFluidCanvasBackdrop.current
  val glass = material.isGlass && canvas != null
  val tinted = material.isTinted && canvas != null
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  // Lo spazio da riassorbire: la spaziatura della lista, piu' due ritagli dei lati aperti sul vetro (lo stesso
  // conto di `fluidGlassGroups`: il pezzo che segue risale sopra il proprio ritaglio e quello del precedente).
  val openEdgePx = if (glass) with(density) { FluidGroupOpenEdge.roundToPx() } else 0
  val gapPx = fluidGroupedGapPx(
    spacingPx = with(density) { LocalFluidItemSpacing.current.roundToPx() },
    openEdgePx = openEdgePx,
  )
  val joins = fluidGroupedJoinsNext(index, count)

  val contentTint = if (glass || tinted) GlassDefaults.contentTint() else null
  val fill = when {
    glass -> Color.Transparent
    tinted -> contentTint!!.overlay
    else -> scheme.surfaceContainerLow
  }
  val rowFill = if (contentTint != null) contentTint.overlay.compositeOver(scheme.surface) else scheme.surfaceContainerLow
  val scope = remember(itemScope, segment, shape, rowFill) {
    FluidGroupedItemScopeImpl(itemScope, segment, shape, rowFill)
  }

  val dividerColor = scheme.onSurface.copy(alpha = FluidGroupedDividerAlpha)
  val showDivider = dividers && joins
  val openTop = glass && (segment == FluidGroupSegment.Middle || segment == FluidGroupSegment.Last)
  val openBottom = glass && (segment == FluidGroupSegment.Middle || segment == FluidGroupSegment.First)

  val animation = with(itemScope) {
    when {
      !animateItems || reducedMotion -> Modifier
      // Sul vetro niente dissolvenza: un'alfa sul layer sopra una lastra la fa lampeggiare (`regole.md`).
      glass -> Modifier.animateItem(fadeInSpec = null, placementSpec = FluidMotion.groupedItemPlacement(), fadeOutSpec = null)
      else -> Modifier.animateItem(
        fadeInSpec = FluidMotion.fadeIn(FluidMotion.GroupedItemFadeInMillis),
        placementSpec = FluidMotion.groupedItemPlacement(),
        fadeOutSpec = FluidMotion.fadeOut(FluidMotion.GroupedItemFadeOutMillis),
      )
    }
  }

  // L'item dichiara meno di quello che disegna (se non e' l'ultima riga); il contenuto dentro resta alla sua
  // altezza intera, e con lui il tocco.
  Box(
    modifier = animation
      .fillMaxWidth()
      .then(
        if (!joins) {
          Modifier
        } else {
          Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, fluidGroupedJoinedHeight(placeable.height, gapPx)) {
              placeable.place(0, 0)
            }
          }
        },
      ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .then(
          if (!openTop && !openBottom) {
            Modifier
          } else {
            // Un lato aperto non porta bordo (come `FluidListGroup`): il bordo speculare della lastra non arriva
            // allo schermo, e i due dp di riempimento che costa li riassorbe la sovrapposizione.
            Modifier.drawWithContent {
              val top = if (openTop) openEdgePx.toFloat() else 0f
              val bottom = size.height - if (openBottom) openEdgePx.toFloat() else 0f
              clipRect(left = 0f, top = top, right = size.width, bottom = bottom) {
                this@drawWithContent.drawContent()
              }
            }
          },
        )
        .then(
          if (glass) {
            Modifier.glassSurface(
              state = canvas!!,
              tint = if (LocalFluidCanvasIsGlass.current) GlassDefaults.stackedContentTint() else contentTint!!,
              shape = shape,
              role = GlassRole.Content,
              optics = if (LocalFluidCanvasIsGlass.current) {
                GlassDefaults.stackedContentOptics()
              } else {
                GlassDefaults.optics(GlassRole.Content)
              },
              sampleOnce = true,
            )
          } else {
            Modifier.background(fill, shape)
          },
        )
        // La tinta di pressione di una riga si ferma sugli angoli del pezzo, come in un gruppo. Una riga di mezzo
        // e' un rettangolo: niente layer di ritaglio per lei.
        .then(if (segment == FluidGroupSegment.Middle) Modifier else Modifier.clip(shape))
        .then(
          if (!showDivider) {
            Modifier
          } else {
            Modifier.drawWithContent {
              drawContent()
              val thickness = FluidGroupedDividerThickness.toPx()
              val inset = FluidGroupedDividerInset.toPx()
              // Sopra la fascia ritagliata del lato aperto, o il ritaglio lo porterebbe via.
              val y = size.height - openEdgePx - thickness / 2f
              val (startX, endX) = if (layoutDirection == LayoutDirection.Ltr) {
                inset to size.width
              } else {
                0f to size.width - inset
              }
              drawLine(dividerColor, Offset(startX, y), Offset(endX, y), strokeWidth = thickness)
            }
          },
        ),
    ) {
      CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        scope.itemContent(index)
      }
    }
  }
}

private class FluidGroupedItemScopeImpl(
  itemScope: LazyItemScope,
  override val segment: FluidGroupSegment,
  override val segmentShape: Shape,
  override val rowFill: Color,
) : FluidGroupedItemScope, LazyItemScope by itemScope

/** Il filo fra due righe: quello di `FluidListDivider` (0,5 dp, `onSurface` al 10%, 16 dp dal lato d'inizio). */
private val FluidGroupedDividerThickness = 0.5.dp
private val FluidGroupedDividerInset = 16.dp
private const val FluidGroupedDividerAlpha = 0.10f

// Le sagome, una per pezzo e una volta sola: un'istanza di `Shape` nuova a ogni composizione rifarebbe il
// ritaglio e il contorno della riga.
private val WholeShape: Shape = ContinuousCornerShape(FluidRadius.Group)
private val FirstShape: Shape = ContinuousCornerShape(topStart = FluidRadius.Group, topEnd = FluidRadius.Group)
private val MiddleShape: Shape = ContinuousCornerShape()
private val LastShape: Shape = ContinuousCornerShape(bottomEnd = FluidRadius.Group, bottomStart = FluidRadius.Group)

/** La sagoma di un pezzo: gli stessi angoli di `FluidListGroup` per lo stesso pezzo. */
internal fun fluidGroupedSegmentShape(segment: FluidGroupSegment): Shape = when (segment) {
  FluidGroupSegment.Whole -> WholeShape
  FluidGroupSegment.First -> FirstShape
  FluidGroupSegment.Middle -> MiddleShape
  FluidGroupSegment.Last -> LastShape
}

/**
 * Il pezzo della riga [index] in una sezione di [count] righe: pura, perche' e' qui che si sbaglia (un angolo
 * tondo nel mezzo, un'ultima riga che resta aperta dopo una rimozione) e un test non deve montare una lista.
 *
 * Dipende solo da indice e conteggio, quindi si ricalcola da se' quando una riga entra o esce: e' cio' che fa
 * richiudere separatori e angoli senza che nessuno lo chieda.
 */
internal fun fluidGroupedSegment(index: Int, count: Int): FluidGroupSegment {
  require(count > 0) { "una sezione vuota non ha pezzi: count era $count" }
  require(index in 0 until count) { "indice $index fuori dalla sezione di $count righe" }
  return when {
    count == 1 -> FluidGroupSegment.Whole
    index == 0 -> FluidGroupSegment.First
    index == count - 1 -> FluidGroupSegment.Last
    else -> FluidGroupSegment.Middle
  }
}

/** Se la riga [index] si unisce alla successiva (tutte tranne l'ultima): e' lei a richiudere il vuoto. */
internal fun fluidGroupedJoinsNext(index: Int, count: Int): Boolean = index < count - 1

/** Lo spazio da riassorbire sotto una riga che si unisce alla successiva. */
internal fun fluidGroupedGapPx(spacingPx: Int, openEdgePx: Int): Int = spacingPx + openEdgePx * 2

/** L'altezza che una riga unita dichiara: quella che disegna meno [gapPx], mai negativa. */
internal fun fluidGroupedJoinedHeight(drawnHeight: Int, gapPx: Int): Int = (drawnHeight - gapPx).coerceAtLeast(0)
