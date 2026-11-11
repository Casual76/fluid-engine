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
 *     effect, so the vendored sources pull in no dependency the engine does not already have;
 *   - the two answers listen to the API level a debug build may simulate (Fluid Engine addition,
 *     4.0.0: see glassApiLevel below and FluidGlassDiagnostics.simulateApiLevel).
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

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

// Fluid Engine change (4.0.0): both answers ask [glassApiLevel] instead of `SDK_INT`. Outside a debug
// build that is `SDK_INT` itself; a debug build can lower it, never raise it, so a `true` here still
// means the platform really has the API and the lint contract below stays true.
@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
fun isRenderEffectSupported(): Boolean = glassApiLevel() >= Build.VERSION_CODES.S

@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
fun isRuntimeShaderSupported(): Boolean = glassApiLevel() >= Build.VERSION_CODES.TIRAMISU

/**
 * Fluid Engine addition (4.0.0): il livello di API su cui il vetro decide i suoi ripieghi.
 *
 * È `SDK_INT`, salvo in una build di debug a cui [FluidGlassDiagnostics.simulateApiLevel] ha chiesto di
 * rispondere come Android 12 o 11: serve a vedere sul tablet, accanto all'APK di riferimento, i rami che
 * l'engine prende senza AGSL e senza `RenderEffect` (piano «iOS in Android», 4.4, capoverso NV). Il livello
 * simulato può solo **scendere**: un valore pari o sopra quello vero vale come nessuna simulazione, perché
 * fingere un'API che il dispositivo non ha sarebbe un crash, non una prova.
 */
internal fun glassApiLevel(): Int {
    val real = Build.VERSION.SDK_INT
    val simulated = simulatedGlassApiLevel
    return if (simulated in 1 until real) simulated else real
}

/**
 * Fluid Engine addition (4.0.0): il livello simulato, `0` se nessuno.
 *
 * Lo scrive solo [installSimulatedGlassApiLevel], che chiama solo [FluidGlassDiagnostics.simulateApiLevel] dopo
 * aver controllato che l'app sia `debuggable`: in un'app di rilascio resta zero per tutta la vita del processo,
 * e [glassApiLevel] è `SDK_INT`.
 */
@Volatile
private var simulatedGlassApiLevel: Int = 0

/** Fluid Engine addition (4.0.0): il livello simulato in vigore, o `null`. */
internal fun simulatedGlassApiLevel(): Int? = simulatedGlassApiLevel.takeIf { it > 0 }

/**
 * Fluid Engine addition (4.0.0): imposta il livello simulato (`null` lo toglie). Il chiamante ha già deciso che
 * può farlo: vedi [FluidGlassDiagnostics.simulateApiLevel].
 */
internal fun installSimulatedGlassApiLevel(level: Int?) {
    simulatedGlassApiLevel = if (level != null && level > 0) level else 0
}
