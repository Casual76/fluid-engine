package dev.antigravity.fluidengine.ui.glass.backdrop.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import androidx.compose.ui.graphics.layer.GraphicsLayer
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeSchedulerTest

class RecordedLayerGraphTest {
    private class Layer(vararg children: Layer) {
        val draws = children.toMutableList()
    }

    @Test fun sharedCaptureAndReplacementAreAcyclic() {
        val capture = Layer()
        val root = Layer(Layer(capture), Layer(capture))
        assertNull(findLayerCycle(root) { it.draws })
        root.draws[0] = Layer()
        assertNull(findLayerCycle(root) { it.draws })
    }

    @Test fun reportsTheClosedPathWhenAnExistingCaptureGainsAnAncestor() {
        val capture = Layer()
        val body = Layer(Layer(capture))
        val shell = Layer(body)
        val root = Layer(shell)
        assertNull(findLayerCycle(root) { it.draws })
        capture.draws.add(body)
        assertEquals(listOf(body, body.draws.single(), capture, body), findLayerCycle(root) { it.draws })
        capture.draws.clear()
        assertNull(findLayerCycle(root) { it.draws })
    }

    @Test fun readsSingleAndSharedChildrenFromTheComposeTracker() {
        val root = GlassProbeSchedulerTest.fakeLayer()
        val first = GlassProbeSchedulerTest.fakeLayer()
        val second = GlassProbeSchedulerTest.fakeLayer()
        val field = GraphicsLayer::class.java.getDeclaredField("childDependenciesTracker").apply { isAccessible = true }
        val tracker = field.get(root)
        val add = tracker.javaClass.getMethod("onDependencyAdded", GraphicsLayer::class.java)
        val record = tracker.javaClass.getMethod("withTracking", Function1::class.java, Function0::class.java)
        val removed: (GraphicsLayer) -> Unit = {}
        val single: () -> Unit = { add.invoke(tracker, first) }
        record.invoke(tracker, removed, single)
        assertEquals(listOf(first), ComposeDependencies.children(root))
        val both: () -> Unit = { add.invoke(tracker, first); add.invoke(tracker, second) }
        record.invoke(tracker, removed, both)
        assertEquals(setOf(first, second), ComposeDependencies.children(root).toSet())
        assertNull(findLayerCycle(root, ComposeDependencies::children))
    }
}
