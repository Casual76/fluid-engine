// Generato da tools/icons/gen_fluid_icons.py dall'elenco icone-health.txt: non modificare a mano.
// Sorgenti: Material Symbols Rounded, peso 500, google/material-design-icons@10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7 (2026-10-23),
// licenza Apache-2.0 (LICENSES/MaterialSymbols.md dell'engine). Un'icona in piu': una riga
// nell'elenco e lo stesso comando con `--app`; con `--check` dice se questo file e' rimasto indietro.

package dev.antigravity.fluidengine.health.icons

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

/** `directions_run`, al tratto. Nel set per: gli allenamenti. Si gira da sola nelle lingue da destra a sinistra. */
val FluidIcons.DirectionsRun: ImageVector
  get() = _directionsRun ?: icon("FluidIcons.DirectionsRun", true, "M12.91 22V17.05L10.95 15.19L10.23 18.43Q10.11 18.88 9.73 19.13Q9.35 19.37 8.89 19.28L3.98 18.29Q3.52 18.2 3.27 17.83Q3.02 17.45 3.11 16.99Q3.2 16.53 3.58 16.27Q3.96 16.01 4.42 16.12L8.13 16.9L9.68 9.04L8.04 9.67V12.01Q8.04 12.47 7.72 12.79Q7.41 13.1 6.95 13.1Q6.49 13.1 6.17 12.78Q5.85 12.47 5.85 12.01V9Q5.85 8.65 6.04 8.38Q6.23 8.1 6.55 7.96L9.91 6.53Q10.81 6.15 11.23 6.03Q11.64 5.92 12 5.92Q12.56 5.92 13.02 6.21Q13.48 6.5 13.77 6.97L14.78 8.57Q15.29 9.39 16.1 9.99Q16.92 10.6 18 10.8Q18.46 10.89 18.78 11.22Q19.09 11.55 19.09 12.01Q19.09 12.47 18.77 12.78Q18.46 13.08 18.01 13.02Q16.68 12.82 15.53 12.21Q14.37 11.59 13.55 10.69L13 13.48L14.74 15.13Q14.92 15.3 15 15.51Q15.09 15.73 15.09 15.96V22Q15.09 22.46 14.78 22.78Q14.46 23.09 14 23.09Q13.54 23.09 13.22 22.78Q12.91 22.46 12.91 22ZM13.51 5.41Q12.65 5.41 12.03 4.8Q11.42 4.18 11.42 3.31Q11.42 2.45 12.03 1.84Q12.65 1.23 13.51 1.23Q14.38 1.23 14.99 1.84Q15.6 2.45 15.6 3.31Q15.6 4.18 14.99 4.8Q14.38 5.41 13.51 5.41Z").also { _directionsRun = it }

private var _directionsRun: ImageVector? = null

/** `footprint`, al tratto. Nel set per: i passi. */
val FluidIcons.Footprint: ImageVector
  get() = _footprint ?: icon("FluidIcons.Footprint", false, "M7.85 18.1Q5.95 18.1 4.91 16.69Q3.87 15.28 3.9 13.42V13.4L3.47 12.75Q3.1 12.17 2.5 10.63Q1.9 9.08 1.9 6.98Q1.9 4.38 3.21 2.64Q4.51 0.9 6.49 0.9Q8.61 0.9 9.85 2.78Q11.1 4.66 11.1 6.99Q11.1 8.34 10.73 9.54Q10.37 10.73 10.01 11.64L10.18 11.92Q10.44 12.36 10.77 13.14Q11.1 13.92 11.1 14.72Q11.1 16.15 10.18 17.12Q9.26 18.1 7.85 18.1ZM5.28 11.43L7.92 10.9Q8.24 10.06 8.56 9.08Q8.88 8.1 8.88 6.97Q8.88 5.51 8.22 4.31Q7.55 3.12 6.5 3.12Q5.45 3.12 4.78 4.32Q4.12 5.51 4.12 7.03Q4.12 8.44 4.51 9.59Q4.89 10.75 5.28 11.43ZM7.85 15.88Q8.3 15.88 8.59 15.56Q8.88 15.25 8.88 14.69Q8.88 14.3 8.69 13.85Q8.5 13.41 8.3 13.08L6.12 13.54Q6.13 14.51 6.55 15.19Q6.97 15.88 7.85 15.88ZM16.16 23.1Q14.74 23.1 13.82 22.12Q12.9 21.15 12.9 19.72Q12.9 18.92 13.23 18.14Q13.56 17.36 13.82 16.92L13.99 16.64Q13.63 15.74 13.27 14.54Q12.9 13.34 12.9 11.99Q12.9 9.66 14.15 7.78Q15.39 5.9 17.51 5.9Q19.49 5.9 20.79 7.64Q22.1 9.38 22.1 11.98Q22.1 14.08 21.5 15.61Q20.91 17.14 20.53 17.73L20.1 18.4V18.42Q20.11 20.28 19.08 21.69Q18.05 23.1 16.16 23.1ZM18.72 16.4Q19.1 15.77 19.49 14.6Q19.88 13.43 19.88 12Q19.88 10.51 19.22 9.32Q18.55 8.12 17.5 8.12Q16.45 8.12 15.78 9.31Q15.12 10.51 15.12 11.98Q15.12 13.07 15.42 14.04Q15.73 15 16.09 15.94L18.72 16.4ZM16.14 20.88Q17.02 20.88 17.45 20.19Q17.87 19.49 17.88 18.52L15.68 18.11Q15.48 18.45 15.3 18.87Q15.12 19.3 15.12 19.68Q15.12 20.13 15.4 20.51Q15.68 20.88 16.14 20.88Z").also { _footprint = it }

private var _footprint: ImageVector? = null

/** `monitor_weight`, al tratto. Nel set per: il peso. */
val FluidIcons.MonitorWeight: ImageVector
  get() = _monitorWeight ?: icon("FluidIcons.MonitorWeight", false, "M12 12.07Q13.25 12.07 14.12 11.2Q15 10.32 15 9.07Q15 7.82 14.12 6.95Q13.25 6.07 12 6.07Q10.75 6.07 9.88 6.95Q9 7.82 9 9.07Q9 10.32 9.88 11.2Q10.75 12.07 12 12.07ZM10.5 9.57Q10.3 9.57 10.15 9.42Q10 9.27 10 9.07Q10 8.87 10.15 8.72Q10.3 8.57 10.5 8.57Q10.7 8.57 10.85 8.72Q11 8.87 11 9.07Q11 9.27 10.85 9.42Q10.7 9.57 10.5 9.57ZM12 9.57Q11.8 9.57 11.65 9.42Q11.5 9.27 11.5 9.07Q11.5 8.87 11.65 8.72Q11.8 8.57 12 8.57Q12.2 8.57 12.35 8.72Q12.5 8.87 12.5 9.07Q12.5 9.27 12.35 9.42Q12.2 9.57 12 9.57ZM13.5 9.57Q13.3 9.57 13.15 9.42Q13 9.27 13 9.07Q13 8.87 13.15 8.72Q13.3 8.57 13.5 8.57Q13.7 8.57 13.85 8.72Q14 8.87 14 9.07Q14 9.27 13.85 9.42Q13.7 9.57 13.5 9.57ZM5.07 21.2Q4.13 21.2 3.46 20.54Q2.8 19.87 2.8 18.93V5.07Q2.8 4.13 3.46 3.46Q4.13 2.8 5.07 2.8H18.93Q19.87 2.8 20.54 3.46Q21.2 4.13 21.2 5.07V18.93Q21.2 19.87 20.54 20.54Q19.87 21.2 18.93 21.2H5.07ZM5.07 18.93H18.93V5.07H5.07V18.93ZM5.07 5.07V18.93V5.07Z").also { _monitorWeight = it }

private var _monitorWeight: ImageVector? = null

/** `smartphone`, al tratto. Nel set per: un dato dal telefono. */
val FluidIcons.Smartphone: ImageVector
  get() = _smartphone ?: icon("FluidIcons.Smartphone", false, "M7.09 23.19Q6.14 23.19 5.48 22.52Q4.81 21.86 4.81 20.91V3.09Q4.81 2.14 5.48 1.48Q6.14 0.81 7.09 0.81H16.91Q17.86 0.81 18.52 1.48Q19.19 2.14 19.19 3.09V20.91Q19.19 21.86 18.52 22.52Q17.86 23.19 16.91 23.19H7.09ZM7.09 19.91V20.91H16.91V19.91H7.09ZM7.09 17.91H16.91V6.09H7.09V17.91ZM7.09 4.09H16.91V3.09H7.09V4.09ZM7.09 4.09V3.09V4.09ZM7.09 19.91V20.91V19.91Z").also { _smartphone = it }

private var _smartphone: ImageVector? = null

/** `watch`, al tratto. Nel set per: un dato dall'orologio. */
val FluidIcons.Watch: ImageVector
  get() = _watch ?: icon("FluidIcons.Watch", false, "M10.63 22.36Q9.89 22.36 9.28 21.92Q8.67 21.48 8.44 20.74L7.51 17.61Q6.29 16.63 5.55 15.17Q4.8 13.7 4.8 12Q4.8 10.3 5.55 8.83Q6.29 7.37 7.51 6.39L8.44 3.26Q8.67 2.52 9.28 2.08Q9.89 1.64 10.63 1.64H13.37Q14.11 1.64 14.72 2.08Q15.33 2.52 15.56 3.26L16.49 6.39Q17.71 7.37 18.45 8.83Q19.2 10.3 19.2 12Q19.2 13.7 18.45 15.17Q17.71 16.63 16.49 17.61L15.56 20.74Q15.33 21.48 14.72 21.92Q14.11 22.36 13.37 22.36H10.63ZM12 16.93Q14.04 16.93 15.49 15.49Q16.93 14.04 16.93 12Q16.93 9.96 15.49 8.51Q14.04 7.07 12 7.07Q9.96 7.07 8.51 8.51Q7.07 9.96 7.07 12Q7.07 14.04 8.51 15.49Q9.96 16.93 12 16.93ZM10.17 5.03Q10.64 4.91 11.09 4.84Q11.54 4.78 12 4.78Q12.46 4.78 12.91 4.84Q13.36 4.91 13.83 5.03L13.43 3.81H10.57L10.17 5.03ZM10.57 20.19H13.43L13.83 18.97Q13.36 19.08 12.91 19.14Q12.46 19.2 12 19.2Q11.54 19.2 11.09 19.14Q10.64 19.08 10.17 18.97L10.57 20.19ZM10.17 3.81H10.57H13.43H13.83Q13.36 3.81 12.91 3.81Q12.46 3.81 12 3.81Q11.54 3.81 11.09 3.81Q10.64 3.81 10.17 3.81ZM10.57 20.19H10.17Q10.64 20.19 11.09 20.19Q11.54 20.19 12 20.19Q12.46 20.19 12.91 20.19Q13.36 20.19 13.83 20.19H13.43H10.57Z").also { _watch = it }

private var _watch: ImageVector? = null

/** `water_drop`, al tratto. Nel set per: l'acqua. */
val FluidIcons.WaterDrop: ImageVector
  get() = _waterDrop ?: icon("FluidIcons.WaterDrop", false, "M12 22.2Q8.49 22.2 6.14 19.8Q3.8 17.39 3.8 13.8Q3.8 12.33 4.44 10.83Q5.08 9.34 6.1 7.9Q7.12 6.47 8.38 5.15Q9.63 3.83 10.86 2.73Q11.1 2.51 11.39 2.41Q11.69 2.31 12 2.31Q12.31 2.31 12.61 2.41Q12.9 2.51 13.14 2.73Q14.37 3.83 15.62 5.15Q16.88 6.47 17.9 7.9Q18.92 9.34 19.56 10.83Q20.2 12.33 20.2 13.8Q20.2 17.39 17.86 19.8Q15.51 22.2 12 22.2ZM12 19.93Q14.57 19.93 16.25 18.19Q17.93 16.44 17.93 13.8Q17.93 12.02 16.44 9.75Q14.95 7.48 12 4.76Q9.05 7.48 7.56 9.75Q6.07 12.02 6.07 13.8Q6.07 16.44 7.75 18.19Q9.43 19.93 12 19.93ZM12.29 18.97Q12.62 18.94 12.85 18.72Q13.08 18.48 13.08 18.16Q13.08 17.79 12.84 17.56Q12.59 17.33 12.22 17.35Q11.16 17.43 10.08 16.79Q9.01 16.16 8.72 14.58Q8.67 14.3 8.44 14.1Q8.22 13.91 7.91 13.91Q7.53 13.91 7.29 14.19Q7.04 14.47 7.12 14.84Q7.55 17.13 9.12 18.1Q10.7 19.07 12.29 18.97Z").also { _waterDrop = it }

private var _waterDrop: ImageVector? = null
