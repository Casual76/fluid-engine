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
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSource
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

/**
 * Fluid Engine addition: lo shader di una sorgente dell'engine, o `null` se il dispositivo non ha AGSL o
 * se il suo compilatore la rifiuta ([GlassFieldHealth.usable]). Su `null` il chiamante prende il ramo che
 * ha sotto Android 13.
 *
 * Perché la difesa sta in questa porta e non dentro [RuntimeShaderCache.obtainRuntimeShader]: quella e'
 * l'API pubblica della libreria, che restituisce sempre uno shader e, su una sorgente che non compila,
 * lancia come il costruttore di Android; un'app che le passa una sorgente sua vuole saperlo cosi', e
 * cambiarle il tipo di ritorno romperebbe chi la chiama. Per le sorgenti dell'engine invece un rifiuto
 * non e' un errore di chi scrive ma un fatto del dispositivo, e il disegno deve poter scendere di un
 * gradino invece di cadere: ogni chiamante dell'engine passa da qui, o chiede [GlassFieldHealth.usable]
 * prima (la lente, che deve uscire prima di toccare il margine), o il proprio livello (Fluid-physics).
 * `GlassShaderFallbackTest` fallisce se un file nuovo arriva al compilatore per un'altra strada.
 */
internal fun RuntimeShaderCache.obtainEngineShader(key: String, source: GlassShaderSource): RuntimeShader? =
    if (GlassFieldHealth.usable(source)) obtainRuntimeShader(key, source.text) else null

// Fluid Engine change: `internal` invece di `private`, perche' la sonda degli shader (GlassFieldHealth)
// compila qui dentro: lo shader che la prova accetta e' lo stesso che poi si disegna, e chiedere un
// verdetto non costa una compilazione in piu'.
internal object SharedRuntimeShaders {

    private val shaders = java.util.concurrent.ConcurrentHashMap<String, RuntimeShader>()

    // Fluid Engine change: upstream is a Kotlin Multiplatform module and never sees Android lint.
    // The API 33 constructor below is reachable only through guarded call sites — every engine
    // caller asks `GlassFieldHealth.usable()` (API level and compiler verdict) or, in Fluid-physics,
    // its tier, and the probe itself compiles only after `isRuntimeShaderSupported()` — and the
    // interface cannot carry @RequiresApi without pushing the annotation onto every caller that has
    // already checked. Suppressed here, where the contract is stated, rather than at each use.
    // A source the compiler refuses throws out of `getOrPut` and is not cached.
    @SuppressLint("NewApi")
    fun obtain(@Language("AGSL") source: String): RuntimeShader {
        return shaders.getOrPut(source) { RuntimeShader(source) }
    }
}
