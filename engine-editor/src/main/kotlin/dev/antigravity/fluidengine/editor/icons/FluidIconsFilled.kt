// Generato da tools/icons/gen_fluid_icons.py dall'elenco icone-editor.txt: non modificare a mano.
// Sorgenti: Material Symbols Rounded, peso 500, google/material-design-icons@10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7 (2026-10-23),
// licenza Apache-2.0 (LICENSES/MaterialSymbols.md dell'engine). Un'icona in piu': una riga
// nell'elenco e lo stesso comando con `--app`; con `--check` dice se questo file e' rimasto indietro.

package dev.antigravity.fluidengine.editor.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons

// Il riquadro di ogni icona e' 24 x 24 come nelle icone Material, cosi' l'API `icon: ImageVector`
// dell'engine non cambia; il nero pieno e' il segnaposto che `Icon` sostituisce con la sua tinta.
private fun icon(name: String, autoMirror: Boolean, vararg paths: String): ImageVector {
  val builder = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror,
  )
  for (path in paths) {
    builder.addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
  }
  return builder.build()
}

/** `auto_fix_high`, pieno. Nel set per: «Auto», la bacchetta; piena quando e' accesa. */
val FluidIcons.Filled.AutoFixHigh: ImageVector
  get() = _autoFixHighFilled ?: icon("FluidIcons.Filled.AutoFixHigh", false, "M20 7.35L18.95 5.12L16.73 4.08L18.95 3.05L20 0.82L21.02 3.05L23.25 4.08L21.02 5.12ZM8.05 7.35L7 5.12L4.78 4.08L7 3.05L8.05 0.82L9.07 3.05L11.3 4.08L9.07 5.12ZM20 19.3L18.95 17.08L16.73 16.05L18.95 15L20 12.78L21.02 15L23.25 16.05L21.02 17.08ZM5.1 22L2.1 19Q1.75 18.65 1.75 18.18Q1.75 17.7 2.1 17.35L13.25 6.2Q13.6 5.85 14.08 5.85Q14.55 5.85 14.9 6.2L17.9 9.2Q18.25 9.52 18.25 10.01Q18.25 10.5 17.9 10.82L6.72 22Q6.4 22.35 5.91 22.34Q5.42 22.32 5.1 22ZM14.08 11.42L15.48 10L14.08 8.6L12.68 10Z").also { _autoFixHighFilled = it }

private var _autoFixHighFilled: ImageVector? = null
