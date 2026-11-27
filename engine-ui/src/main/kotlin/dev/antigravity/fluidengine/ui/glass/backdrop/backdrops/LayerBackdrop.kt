/*
 * Copyright 2025 Kyant — Apache License, Version 2.0.
 *
 * Vendored from Kyant0/AndroidLiquidGlass ("backdrop" v2.0.0, io.github.kyant0:backdrop),
 * commit b18eb0ff12c616546a68c72e7d0097f1ab286c87.
 *   https://github.com/Kyant0/AndroidLiquidGlass
 *
 * Changes made by the Fluid Engine (Antigravity), see LICENSES/AndroidLiquidGlass.md:
 *   - the Kotlin Multiplatform expect/actual pairs were merged into this single Android
 *     source set, so `expect`/`actual` are gone and the Android bodies are the only ones;
 *   - the package was renamed com.kyant.backdrop -> dev.antigravity.fluidengine.ui.glass.backdrop;
 *   - support for io.github.kyant0:shapes' RoundedRectangularShape was dropped from the lens
 *     effect, so the vendored sources pull in no dependency the engine does not already have.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.antigravity.fluidengine.ui.glass.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.fluid.rememberRecordedGraphicsLayer
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.isRecordedByDrawBackdropNode
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.InverseLayerScope
import java.lang.ref.WeakReference

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

// Fluid Engine change (F7.29b, D4): the default layer starts with an empty display list, so a nested glass that samples
// this material before its owner records it (the frame a panel enters) no longer makes Compose record it a second
// time in the same frame.
@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberRecordedGraphicsLayer(),
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw
): LayerBackdrop {
    return remember(graphicsLayer, onDraw) {
        LayerBackdrop(graphicsLayer, onDraw)
    }
}

@Stable
class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val onDraw: ContentDrawScope.() -> Unit
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override val layerSources: List<Any> get() = selfAsSource

    private val selfAsSource = listOf(this)

    // Fluid Engine change: `neverEqualPolicy`, because every positioning callback hands over the
    // *same* `LayoutCoordinates` instance — it mutates internally. Under structural equality a
    // source that moves is a write of an equal value, so nothing reading this in a draw pass is
    // invalidated, and a surface that skips unchanged captures keeps replaying a record made
    // against the source's *old* position. That is a pane of glass whose refraction is fixed to
    // where the page stood mid-transition: a band of the backdrop stays sharp until something else
    // happens to redraw the pane.
    private var ownCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    internal var layerCoordinates: LayoutCoordinates?
        get() = ownCoordinates ?: chromeFallback?.ownCoordinates
        set(value) { ownCoordinates = value }

    /** Senza le coordinate del layer, `drawBackdrop` esce senza disegnare: non c'e' niente da tenere. */
    override fun isReadyToSample(): Boolean = !graphicsLayer.isReleased &&
        layerCoordinates?.isAttached == true && hasPageRecording()

    /**
     * Fluid Engine addition (2.13.0): il nodo che registra questo layer, quando e' uno dei nostri.
     *
     * Serve al vetro su vetro per sapere se una registrazione sta *dentro* il pannello su cui una
     * superficie appoggia (e quindi va sopra al materiale del pannello) o fuori (e quindi sotto).
     */
    internal var ownerNode: Modifier.Node? by mutableStateOf(null)

    /** Prestito per i lettori esterni. Solo la shell registra in questo layer privato. */
    internal var chromeFallback: LayerBackdrop? by mutableStateOf(null)

    /** Una cattura interna deve continuare a vedere solo la propria sorgente, anche se una
     * superficie esterna ha chiesto lo stesso fondale. Vale anche per le sonde fuori dal draw.
     */
    internal fun sourceFor(reader: LayoutCoordinates): LayerBackdrop? {
        if (graphicsLayer.isReleased || !reader.isAttached) return null
        if (ownCoordinates?.isAttached == true) return pageSourceOrSelf()
        val shell = chromeFallback ?: return null
        val pages = shell.ownCoordinates ?: return null
        if (ownerNode != null || !pages.isAttached || shell.graphicsLayer.isReleased) return null
        var ancestor: LayoutCoordinates? = reader
        while (ancestor != null) {
            if (ancestor === pages) return null
            ancestor = ancestor.parentCoordinates
        }
        return shell
    }

    /**
     * Fluid Engine addition (2.13.0): registrato solo su richiesta.
     *
     * Vale per il materiale che un pannello pubblica (`exports`, e quello che pubblica da solo per
     * il vetro che ci sta dentro): un pannello che nessuno rifrange non deve pagarne la
     * registrazione. Chi lo campiona si fa contare con [addReader]; il pannello registra solo
     * finche' [readerCount] e' sopra zero. Vale anche per le sorgenti dell'engine e la scena del foglio.
     * Un layer grezzo dell'app può essere letto direttamente tramite [graphicsLayer].
     */
    internal var onDemand: Boolean = false

    private val readers = ReaderCount()

    /** Quante superfici stanno campionando questo layer adesso. Vedi [onDemand]. */
    internal val readerCount: Int get() = readers.count

    /** Chiamato quando arriva il primo lettore: il pannello deve tornare a registrare. */
    internal var onFirstReader: (() -> Unit)? = null

    internal fun addReader() {
        val first = readers.add()
        // F8.10, diagnostica: chi guarda un layer su richiesta (il materiale di un pannello, la scena di un foglio).
        GlassFrameDiagnostics.event("readerAdd", this) { "lettori=${readers.count} primo=$first onDemand=$onDemand" }
        if (first) {
            updatePageDemand()
            onFirstReader?.invoke()
        }
    }

    internal fun removeReader() {
        val last = readers.remove()
        GlassFrameDiagnostics.event("readerRemove", this) { "lettori=${readers.count} ultimo=$last onDemand=$onDemand" }
        if (last) updatePageDemand()
    }

    /** Se chi esporta in questo layer deve registrarlo a questo fotogramma. */
    internal val wantsRecording: Boolean get() = exportWanted(onDemand, readerCount)

    /**
     * Fluid Engine addition (4.0.0, F2.1): quante volte chi possiede questo layer lo ha registrato di nuovo.
     *
     * Serve alla sonda dello sfondo (`glass/field/GlassProbe.kt`). Una superficie che campiona questo layer
     * non ricattura quando il layer si ri-registra: la sua cattura lo tiene per riferimento e mostra da
     * sola il contenuto nuovo. Quindi sotto un vetro fermo lo sfondo puo' passare da bianco a nero senza
     * che nessuna cattura lo sappia; lo sa solo chi registra. Non e' uno stato di snapshot apposta: cambia
     * nel disegno della sorgente, e chi lo legge non deve essere invalidato da lui.
     *
     * Lo incrementa [bumpRevision]: `layerBackdrop` (la pagina) e, dalla 4.0.0 (F2.3), `DrawBackdropNode` dopo
     * ogni registrazione di un export o del materiale di un pannello. Prima i layer pubblicati non avvisavano, e
     * la sonda dei vetri che li campionano li leggeva a cadenza fissa per sempre, anche a riposo.
     */
    internal var revision: Long = 0L
        private set

    private var revisionListeners: Array<LayerRevisionListener> = NoRevisionListeners

    /** Fluid Engine addition (F2.1): si iscrive alle revisioni. Dal filo principale. */
    internal fun addRevisionListener(listener: LayerRevisionListener) {
        if (revisionListeners.any { it === listener }) return
        revisionListeners += listener
    }

    /** Fluid Engine addition (F2.1): toglie un iscritto. Dal filo principale. */
    internal fun removeRevisionListener(listener: LayerRevisionListener) {
        if (revisionListeners.none { it === listener }) return
        revisionListeners = revisionListeners.filter { it !== listener }.toTypedArray()
    }

    /** Fluid Engine addition (F2.1): quanti si sono iscritti. Per i test. */
    internal val revisionListenerCount: Int get() = revisionListeners.size

    /**
     * Fluid Engine addition (F2.1): il layer e' appena stato registrato di nuovo. Chiamata nel disegno di
     * chi lo registra, quindi gli iscritti non lavorano qui: lasciano un segnale e basta. Non alloca:
     * l'elenco si copia solo quando qualcuno si iscrive o se ne va, e chi se ne va durante l'avviso non
     * disturba il giro in corso.
     */
    internal fun bumpRevision() {
        // Fluid Engine addition (4.0.0, F2.3c): l'avviso risale a chi contiene questo layer nella propria registrazione
        // (vedi [noteContainer]). La guardia ferma un giro chiuso.
        if (bumping) return
        bumping = true
        try {
            revision++
            val listeners = revisionListeners
            for (listener in listeners) listener.onRevision(this, revision)
            val outer = containers
            for (container in outer) container.get()?.bumpRevision()
        } finally {
            bumping = false
        }
    }

    private var bumping = false

    /**
     * Fluid Engine addition (4.0.0, G1): quanto e in che verso scorre il contenuto di questo layer, da chi annuncia lo
     * scorrimento (`GlassScrollAnnouncer`, lo scrittore della chrome). Lo leggono le sonde quando le manopole di debug
     * `flipscroll=rest|ahead` lo chiedono; di serie nessuno lo legge. Non è stato di snapshot.
     */
    internal val scrollMotion: GlassScrollMotion = GlassScrollMotion()

    /**
     * Fluid Engine addition (4.0.0, F2.3c): i layer che contengono questo nella propria registrazione. Una registrazione
     * tiene per riferimento i layer dei figli: quando un figlio si registra di nuovo da solo, la registrazione di fuori
     * mostra già il contenuto nuovo ma non lo sa, e non avvisa. Sul tablet il palco della pagina «Adattività» cambiava
     * colore dentro la registrazione della chrome e la tab bar sopra non girava. Chi sta dentro avvisa anche chi lo
     * contiene. Li impara mentre si disegna dentro la registrazione di fuori ([LayerRecordingStack]).
     */
    // Una pagina può sopravvivere a più registrazioni della shell. L'avviso
    // delle revisioni non deve prolungare la vita dei vecchi contenitori.
    private var containers: Array<WeakReference<LayerBackdrop>> = NoContainers

    /** Fluid Engine addition (4.0.0, F2.3c): [outer] contiene questo layer nella sua registrazione. */
    internal fun noteContainer(outer: LayerBackdrop) {
        if (outer === this || containers.any { it.get() === outer }) return
        containers = (containers.filter { it.get() != null } + WeakReference(outer)).toTypedArray()
    }

    /** Fluid Engine addition (4.0.0, F2.3c): quanti layer contengono questo. Per i test. */
    internal val containerCount: Int get() = containers.count { it.get() != null }

    /**
     * Fluid Engine addition (F2.1): se questo layer avvisa quando si ri-registra. Vero quando lo registra un
     * `layerBackdrop` (vedi [revision]) e, dalla 4.0.0 (Fluid Engine addition, F2.3), quando lo registra un nodo del
     * vetro (un export, il materiale di un pannello); altrimenti chi vuole sapere se e' cambiato deve guardare da se'.
     */
    internal val announcesRevisions: Boolean
        get() = isRecordedByLayerBackdropNode() || isRecordedByDrawBackdropNode() ||
            (ownerNode == null && chromeFallback != null)

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val source = sourceFor(coordinates) ?: return
        val layerCoordinates = source.ownCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val offset =
                try {
                    layerCoordinates.localPositionOf(coordinates)
                } catch (_: Exception) {
                    // TODO: outer transformations lead to wrong position calculation
                    coordinates.positionInWindow() - layerCoordinates.positionInWindow()
                }
            translate(-offset.x, -offset.y)
        }) {
            drawLayer(source.graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}

/**
 * Fluid Engine addition (2.13.0): un layer per il materiale che un pannello pubblica da solo, per il
 * vetro che gli sta dentro. Su richiesta per costruzione: esiste solo perche' qualcuno lo ha chiesto.
 */
internal fun materialLayerBackdrop(graphicsLayer: GraphicsLayer): LayerBackdrop =
    LayerBackdrop(graphicsLayer, DefaultOnDraw).apply { onDemand = true }

/**
 * Il conto dei lettori di un materiale pubblicato. A se', puro, per poterlo provare in un test: un
 * conto che scende sotto zero o che non avvisa al primo lettore e' un pannello che smette di
 * registrare per sempre, e da fuori si vede solo come "il tasto ha perso il vetro".
 */
internal class ReaderCount {

    var count: Int = 0
        private set

    /** True quando questo e' il primo lettore: chi registra deve tornare a farlo. */
    fun add(): Boolean {
        count++
        return count == 1
    }

    fun remove(): Boolean {
        if (count == 0) return false
        count--
        return count == 0
    }
}

/**
 * La regola della registrazione pigra, scritta una volta: un layer normale si registra sempre, uno
 * su richiesta solo se qualcuno lo sta guardando. Pura, per poterla provare in un test.
 */
internal fun exportWanted(onDemand: Boolean, readers: Int): Boolean = !onDemand || readers > 0

private val NoContainers = emptyArray<WeakReference<LayerBackdrop>>()

/**
 * Fluid Engine addition (4.0.0, F2.3c): i layer che si stanno registrando adesso, dal più esterno. Chi si disegna mentre
 * uno di loro registra sta nella sua registrazione: lo ricorda ([LayerBackdrop.noteContainer]), e quando poi si registra
 * di nuovo da solo avvisa anche lui. Solo il filo principale (il disegno di Compose).
 */
internal object LayerRecordingStack {
    private val stack = ArrayList<LayerBackdrop>(4)

    fun enter(layer: LayerBackdrop) {
        for (outer in stack) layer.noteContainer(outer)
        stack.add(layer)
    }

    fun exit(layer: LayerBackdrop) {
        val at = stack.lastIndexOf(layer)
        if (at >= 0) stack.removeAt(at)
    }

    /** Per i test. */
    internal val depth: Int get() = stack.size
}

/**
 * La shell registra le pagine in un layer privato. I vetri esterni segnalano la loro sorgente
 * di base per ricevere [LayerBackdrop.chromeFallback] quando manca il fondale attivo.
 */
internal interface LayerFallbackWriter {
    /** Un vetro fuori dalla pagina chiede lo sfondo [layer]. */
    fun adopt(layer: LayerBackdrop)

    /** Quel vetro non lo campiona più. */
    fun release(layer: LayerBackdrop)
}

/** Fluid Engine addition (4.0.0, F2.3c): gli scrittori di riserva, per finestra (la vista radice). */
internal object LayerFallbackWriters {
    private val byRoot = java.util.WeakHashMap<android.view.View, MutableState<LayerFallbackWriter?>>()
    private fun entry(root: android.view.View) = byRoot.getOrPut(root) { mutableStateOf(null) }

    fun register(root: android.view.View, writer: LayerFallbackWriter) {
        entry(root).value = writer
    }

    fun unregister(root: android.view.View, writer: LayerFallbackWriter) {
        val entry = byRoot[root] ?: return
        if (entry.value === writer) entry.value = null
    }

    fun of(root: android.view.View): LayerFallbackWriter? = entry(root).value
}

/**
 * Fluid Engine addition (4.0.0, F2.3c): vero dentro il contenuto che la chrome registra come riserva. Un vetro lì dentro
 * non chiede mai la registrazione di riserva: campionerebbe una registrazione che contiene sé stesso.
 */
internal const val LayerFallbackWriterKey: String = "dev.antigravity.fluidengine.LayerFallbackWriter"

/**
 * Fluid Engine addition (4.0.0, G1): il moto recente dello scorrimento di un layer, per le varianti «cosa fa il vetro
 * mentre scorri» (`flipscroll`). Tiene gli ultimi [GlassScrollMotionSlots] spostamenti con il loro istante e ne fa una
 * velocità sulla finestra [GlassScrollMotionWindowMillis]: niente allocazioni, niente stato di snapshot. Pura negli
 * istanti: glieli dà chi chiama.
 */
internal class GlassScrollMotion {
    /** Lo scorrimento annidato (il dito, il lancio): `GlassScrollAnnouncer`, lo scrittore della chrome. */
    private val nested = GlassScrollMotionRing()

    /** La posizione della lista, che vede anche lo scorrimento fatto dal codice: `FluidScreen`. */
    private val position = GlassScrollMotionRing()

    /** L'istante dell'ultimo spostamento, o `Long.MIN_VALUE`. */
    val lastAtMillis: Long get() = maxOf(nested.lastAtMillis, position.lastAtMillis)

    /** Il contenuto si è spostato di [deltaY] pixel sullo schermo (negativo: verso l'alto) a [nowMillis], col dito. */
    fun note(nowMillis: Long, deltaY: Float) = nested.note(nowMillis, deltaY)

    /**
     * Lo stesso, dalla posizione della lista. Un canale a parte: col dito arrivano tutti e due per lo stesso passo, e la
     * velocità è quella del canale più svelto, non la loro somma.
     */
    fun notePosition(nowMillis: Long, deltaY: Float) = position.note(nowMillis, deltaY)

    /** La velocità del contenuto a [nowMillis], pixel al secondo (negativa: verso l'alto); 0 se è fermo. */
    fun velocityAt(nowMillis: Long): Float {
        val a = nested.velocityAt(nowMillis)
        val b = position.velocityAt(nowMillis)
        return if (kotlin.math.abs(a) >= kotlin.math.abs(b)) a else b
    }
}

/** Un canale di [GlassScrollMotion]: gli ultimi spostamenti con il loro istante, senza allocare. */
private class GlassScrollMotionRing {
    private val at = LongArray(GlassScrollMotionSlots) { Long.MIN_VALUE }
    private val dy = FloatArray(GlassScrollMotionSlots)
    private var next = 0

    var lastAtMillis: Long = Long.MIN_VALUE
        private set

    fun note(nowMillis: Long, deltaY: Float) {
        if (!deltaY.isFinite() || deltaY == 0f) return
        at[next] = nowMillis
        dy[next] = deltaY
        next = (next + 1) % GlassScrollMotionSlots
        lastAtMillis = nowMillis
    }

    fun velocityAt(nowMillis: Long): Float {
        var sum = 0f
        var any = false
        for (i in 0 until GlassScrollMotionSlots) {
            val t = at[i]
            if (t == Long.MIN_VALUE || nowMillis - t > GlassScrollMotionWindowMillis || t > nowMillis) continue
            sum += dy[i]
            any = true
        }
        return if (any) sum * 1000f / GlassScrollMotionWindowMillis else 0f
    }
}

/** Quanti spostamenti tiene [GlassScrollMotion]: più dei fotogrammi di una finestra a 120 Hz. */
internal const val GlassScrollMotionSlots: Int = 16

/** La finestra su cui [GlassScrollMotion] fa la velocità, ms: oltre, il contenuto è fermo. */
internal const val GlassScrollMotionWindowMillis: Long = 100L

/**
 * Fluid Engine addition (4.0.0, F2.1): chi vuole sapere quando un [LayerBackdrop] si ri-registra.
 * Chiamato nel disegno della sorgente, sul filo principale: niente lavoro e niente stato di snapshot.
 */
internal fun interface LayerRevisionListener {
    fun onRevision(source: LayerBackdrop, revision: Long)
}

private val NoRevisionListeners = emptyArray<LayerRevisionListener>()
