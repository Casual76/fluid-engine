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

/** `directions`, al tratto. Nel set per: «Indicazioni», la freccia che gira. Si gira da sola nelle lingue da destra a sinistra. */
val FluidIcons.Directions: ImageVector
  get() = _directions ?: icon("FluidIcons.Directions", true, "M10.26 12.08H13.34V13.32Q13.34 13.71 13.69 13.85Q14.03 14 14.31 13.72L16.23 11.8Q16.58 11.45 16.57 11Q16.57 10.54 16.23 10.2L14.31 8.28Q14.03 8 13.69 8.14Q13.34 8.28 13.34 8.68V9.92H9.18Q8.72 9.92 8.41 10.23Q8.1 10.55 8.1 11V13.99Q8.1 14.44 8.41 14.75Q8.72 15.07 9.18 15.07Q9.63 15.07 9.95 14.75Q10.26 14.44 10.26 13.99V12.08ZM12 22.18Q11.58 22.18 11.16 22.01Q10.75 21.84 10.41 21.5L2.5 13.59Q2.16 13.25 1.99 12.84Q1.82 12.42 1.82 12Q1.82 11.57 1.99 11.16Q2.16 10.75 2.5 10.41L10.41 2.5Q10.75 2.16 11.16 1.99Q11.58 1.81 12 1.81Q12.43 1.81 12.84 1.99Q13.25 2.16 13.59 2.5L21.5 10.41Q21.84 10.75 22.01 11.16Q22.19 11.57 22.19 12Q22.19 12.42 22.01 12.84Q21.84 13.25 21.5 13.59L13.59 21.5Q13.25 21.84 12.84 22.01Q12.43 22.18 12 22.18ZM8.05 15.95L12 19.9L19.9 12L12 4.1L4.1 12L8.05 15.95Z").also { _directions = it }

private var _directions: ImageVector? = null

/** `near_me`, al tratto. Nel set per: la posizione: vuota spenta, piena quando la mappa ti segue (la freccia di Mappe). */
val FluidIcons.NearMe: ImageVector
  get() = _nearMe ?: icon("FluidIcons.NearMe", false, "M9.79 14.11L3.24 11.46Q2.87 11.31 2.7 11.02Q2.53 10.72 2.53 10.4Q2.53 10.08 2.71 9.78Q2.9 9.49 3.27 9.34L18.96 3.47Q19.3 3.33 19.62 3.41Q19.94 3.5 20.16 3.72Q20.39 3.95 20.48 4.27Q20.57 4.58 20.42 4.92L14.55 20.63Q14.41 20.99 14.11 21.18Q13.81 21.37 13.49 21.37Q13.18 21.37 12.88 21.19Q12.58 21.02 12.44 20.65L9.79 14.11ZM13.44 17.11L17.41 6.47L6.78 10.45L11.55 12.33L13.44 17.11Z").also { _nearMe = it }

private var _nearMe: ImageVector? = null
