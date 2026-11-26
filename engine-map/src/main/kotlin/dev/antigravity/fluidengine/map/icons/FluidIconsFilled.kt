// Generato da tools/icons/gen_fluid_icons.py dall'elenco icone-map.txt: non modificare a mano.
// Sorgenti: Material Symbols Rounded, peso 500, google/material-design-icons@10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7 (2026-10-23),
// licenza Apache-2.0 (LICENSES/MaterialSymbols.md dell'engine). Un'icona in piu': una riga
// nell'elenco e lo stesso comando con `--app`; con `--check` dice se questo file e' rimasto indietro.

package dev.antigravity.fluidengine.map.icons

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

/** `near_me`, pieno. Nel set per: la posizione: vuota spenta, piena quando la mappa ti segue (la freccia di Mappe). */
val FluidIcons.Filled.NearMe: ImageVector
  get() = _nearMeFilled ?: icon("FluidIcons.Filled.NearMe", false, "M9.79 14.11L3.24 11.46Q2.87 11.31 2.7 11.02Q2.53 10.72 2.53 10.4Q2.53 10.08 2.71 9.78Q2.9 9.49 3.27 9.34L18.96 3.47Q19.3 3.33 19.62 3.41Q19.94 3.5 20.16 3.72Q20.39 3.95 20.48 4.27Q20.57 4.58 20.42 4.92L14.55 20.63Q14.41 20.99 14.11 21.18Q13.81 21.37 13.49 21.37Q13.18 21.37 12.88 21.19Q12.58 21.02 12.44 20.65L9.79 14.11Z").also { _nearMeFilled = it }

private var _nearMeFilled: ImageVector? = null
