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
package dev.antigravity.fluidengine.ui.glass.backdrop

import android.annotation.SuppressLint
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    // Fluid Engine change: the cache is process-wide, not per node. Upstream keeps one shader
    // instance per surface, and per surface is per *pipeline*: Skia keys a runtime effect's GPU
    // program on the effect instance, not on its source, so every glass pane entering the viewport
    // compiled the same AGSL from scratch — 100-300 ms stalls on the render thread, once per panel
    // while a grouped list scrolled, and three in a row on the first frame of every modal.
    //
    // Sharing an instance across surfaces is safe because every user sets its uniforms and
    // *immediately* snapshots them on the same thread: `createRuntimeShaderEffect` copies the
    // builder's uniform state at creation, and a paint records its shader's state at record time.
    // Nothing holds the live instance across another surface's set-and-snapshot.
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return SharedRuntimeShaders.obtain(string)
    }

    fun clear() {
        // Niente da liberare: le istanze appartengono al processo, e i sorgenti sono una manciata
        // di costanti. Svuotare qui ricomprerebbe la compilazione al prossimo attach.
    }
}

private object SharedRuntimeShaders {

    private val shaders = java.util.concurrent.ConcurrentHashMap<String, RuntimeShader>()

    // Fluid Engine change: upstream is a Kotlin Multiplatform module and never sees Android lint.
    // The API 33 constructor below is reachable only through guarded call sites — the lens effect
    // and the highlight styles both return early on `isRuntimeShaderSupported()` — and the
    // interface cannot carry @RequiresApi without pushing the annotation onto every caller that has
    // already checked. Suppressed here, where the contract is stated, rather than at each use.
    @SuppressLint("NewApi")
    fun obtain(@Language("AGSL") source: String): RuntimeShader {
        return shaders.getOrPut(source) { RuntimeShader(source) }
    }
}
