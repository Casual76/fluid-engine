package dev.antigravity.fluidengine.sample

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ogni shader AGSL dell'engine compila sul compilatore vero di questo dispositivo.
 *
 * Dalla 3.0.0 alla 3.1.2 due shader di Fluid-physics non compilavano su nessun dispositivo (una variabile
 * `flat`, parola riservata di SkSL) e il motore è rimasto a `Balanced` per una settimana senza che un test se
 * ne accorgesse: la sonda ripiegava in silenzio. Il lint delle sorgenti (`ShaderSourceLintTest`) e il banco
 * (`glassbench.py check`) fermano gli errori che si vedono sul PC; questo test ferma il resto, cioè quello che
 * rifiuta lo Skia di un Android preciso o il suo driver. Si lancia a ogni sessione sul dispositivo.
 *
 * Le sorgenti sono interne a `engine-ui`, quindi passa da [FluidGlassDiagnostics.compileAllShaders], che le
 * compila con la stessa sonda che decide i ripieghi del vetro. Un fallimento dice quale sorgente e con quale
 * errore del compilatore; ogni verdetto finisce anche in logcat (tag `GlassShaderCompile`).
 */
@RunWith(AndroidJUnit4::class)
class GlassShaderCompileInstrumentedTest {

  @Before
  fun agslExists() {
    assumeTrue("AGSL esiste da Android 13 (API 33)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
  }

  @Test
  fun everyEngineShaderCompilesOnThisDevice() {
    val names = FluidGlassDiagnostics.shaderSourceNames
    // Almeno le nove della 4.0.0, bagliore del tocco compreso: meno vuol dire che sul dispositivo c'è una build
    // vecchia, e il test proverebbe un elenco che non è più quello dell'engine. Le fasi successive ne aggiungono.
    assertTrue(
      "L'engine dichiara ${names.size} sorgenti AGSL, ne aspetto almeno $MinimumSources: build vecchia? $names",
      names.size >= MinimumSources,
    )
    assertTrue("Manca la sorgente del bagliore del tocco: $names", "GlassGlowShaderString" in names)

    val verdicts = FluidGlassDiagnostics.compileAllShaders()
    for ((name, error) in verdicts) {
      if (error == null) Log.i(Tag, "$name: compila") else Log.e(Tag, "$name: ${error.message}")
    }
    assertEquals("Ogni sorgente dell'engine deve avere un verdetto.", names, verdicts.keys.toList())

    val refused = verdicts.filterValues { it != null }
    if (refused.isNotEmpty()) {
      fail(
        buildString {
          append("Il compilatore AGSL di ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT}) ")
          append("rifiuta ${refused.size} sorgenti su ${verdicts.size}:")
          for ((name, error) in refused) append("\n- ").append(name).append(": ").append(error?.message)
        },
      )
    }
    // I verdetti restano leggibili da fuori, gli stessi oggetti: è da lì che li legge chi li mostra.
    assertEquals(verdicts, FluidGlassDiagnostics.shaderVerdicts)
  }

  /**
   * La prova che il controllo controlla: lo stesso errore della 3.1.2, dato al compilatore vero, deve essere
   * rifiutato. Se un giorno passasse, il test di sopra non direbbe più niente su `flat`.
   */
  @Test
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  fun theRealCompilerRefusesFlatAsAName() {
    val error = runCatching { RuntimeShader(FlatAsAName) }.exceptionOrNull()
    Log.i(Tag, "`half4 flat = …` sul compilatore vero: ${error?.message ?: "ACCETTATO"}")
    assertNotNull("Il compilatore di questo dispositivo accetta `flat` come nome di variabile.", error)
  }

  private companion object {
    const val Tag = "GlassShaderCompile"

    /** Le sorgenti della 4.0.0: lente e riflessi (4), il bagliore del tocco, Fluid-physics (4). */
    const val MinimumSources = 9

    /** Il caso minimo del difetto: la riga 5 è quella che il compilatore deve indicare. */
    const val FlatAsAName = """
uniform shader content;

half4 main(float2 coord) {
    half4 flat = content.eval(coord);
    return flat;
}"""
  }
}
