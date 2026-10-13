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
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.InverseLayerScope

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
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
    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    /** Senza le coordinate del layer, `drawBackdrop` esce senza disegnare: non c'e' niente da tenere. */
    override fun isReadyToSample(): Boolean = layerCoordinates != null

    /**
     * Fluid Engine addition (2.13.0): il nodo che registra questo layer, quando e' uno dei nostri.
     *
     * Serve al vetro su vetro per sapere se una registrazione sta *dentro* il pannello su cui una
     * superficie appoggia (e quindi va sopra al materiale del pannello) o fuori (e quindi sotto).
     */
    internal var ownerNode: Modifier.Node? = null

    /**
     * Fluid Engine addition (2.13.0): registrato solo su richiesta.
     *
     * Vale per il materiale che un pannello pubblica (`exports`, e quello che pubblica da solo per
     * il vetro che ci sta dentro): un pannello che nessuno rifrange non deve pagarne la
     * registrazione. Chi lo campiona si fa contare con [addReader]; il pannello registra solo
     * finche' [readerCount] e' sopra zero. Non tocca chi registra una *pagina*
     * (`layerBackdrop`): quella resta registrata sempre, come prima.
     */
    internal var onDemand: Boolean = false

    private val readers = ReaderCount()

    /** Quante superfici stanno campionando questo layer adesso. Vedi [onDemand]. */
    internal val readerCount: Int get() = readers.count

    /** Chiamato quando arriva il primo lettore: il pannello deve tornare a registrare. */
    internal var onFirstReader: (() -> Unit)? = null

    internal fun addReader() {
        if (readers.add()) onFirstReader?.invoke()
    }

    internal fun removeReader() {
        readers.remove()
    }

    /** Se chi esporta in questo layer deve registrarlo a questo fotogramma. */
    internal val wantsRecording: Boolean get() = exportWanted(onDemand, readerCount)

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
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
            drawLayer(graphicsLayer)
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

    fun remove() {
        if (count > 0) count--
    }
}

/**
 * La regola della registrazione pigra, scritta una volta: un layer normale si registra sempre, uno
 * su richiesta solo se qualcuno lo sta guardando. Pura, per poterla provare in un test.
 */
internal fun exportWanted(onDemand: Boolean, readers: Int): Boolean = !onDemand || readers > 0
