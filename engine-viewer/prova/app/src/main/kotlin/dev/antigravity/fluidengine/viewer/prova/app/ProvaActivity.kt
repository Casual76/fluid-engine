package dev.antigravity.fluidengine.viewer.prova.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerType

/**
 * F6C.8 · Chiede al registro le famiglie e il tipo di «nota.prova», e lo scrive: con il modulo di prova dice
 * «prova Text»; senza, «sconosciuto». È l'uso minimo del registro che R8 deve conservare.
 */
class ProvaActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val families = FluidViewerRegistry.families.joinToString { it.id }.ifEmpty { "nessuna" }
    val type = FluidViewerType.of(null, "nota.prova")
    setContentView(TextView(this).apply {
      text = "Famiglie: $families\nnota.prova: ${type.family ?: "sconosciuto"} ${type.preset}"
    })
  }
}
