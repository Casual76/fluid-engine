package dev.antigravity.fluidengine.sample.playground

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidFormPresets
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La scheda del motore: un pezzo di vetro su un palco rumoroso, e tutto quello che serve per
 * fargli prendere qualsiasi forma — i preset, il disegno a mano libera, le molle, i livelli di
 * qualità, e il lato pratico (tasti→menù→pop-up).
 *
 * È anche il banco di misura: il loop A↔B esiste per `dumpsys gfxinfo`, perché un morph si giudica
 * in framestats oltre che a occhio.
 */
@Composable
internal fun PlaygroundTab(bottomInset: Dp) {
  val density = LocalDensity.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val store = remember { ShapeStore(context) }
  val savedShapes = remember { mutableStateListOf<SavedShape>() }
  LaunchedEffect(Unit) {
    val loaded = withContext(Dispatchers.IO) { store.load() }
    savedShapes.clear()
    savedShapes.addAll(loaded)
  }

  val state = rememberFluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
  var stage by remember { mutableStateOf(IntSize.Zero) }
  var selectedPreset by remember { mutableStateOf(PresetCircle) }
  var drawing by remember { mutableStateOf(false) }
  var springLabel by remember { mutableStateOf("Casa") }
  var tierLabel by remember { mutableStateOf("Auto") }
  var loopLabel by remember { mutableStateOf("No") }
  var blobSeed by remember { mutableIntStateOf(7) }
  var lastFreehand by remember { mutableStateOf<FluidForm.Poly?>(null) }

  val cardRadiusPx = with(density) { FluidRadius.Card.toPx() }
  val groupRadiusPx = with(density) { FluidRadius.Group.toPx() }

  fun presetForm(name: String): FluidForm? =
    playgroundPresetForm(name, stage, cardRadiusPx, groupRadiusPx, blobSeed)

  // Null = la coreografia della casa: rincorsa piano→veloce e rimbalzo di molla sul finale.
  fun springSpec(): AnimationSpec<Float>? = when (springLabel) {
    "Scattante" -> FluidMotion.snappy()
    "Standard" -> FluidMotion.standard()
    "Dolce" -> FluidMotion.smooth()
    else -> null
  }

  val tierOverride: FluidPhysicsTier? = when (tierLabel) {
    "Full" -> FluidPhysicsTier.Full
    "Bilanciato" -> FluidPhysicsTier.Balanced
    "Lite" -> FluidPhysicsTier.Lite
    else -> null
  }

  fun morph(target: FluidForm) {
    scope.launch { state.morphTo(target, springSpec()) }
  }

  // Il palco si misura dopo la prima composizione: la base arriva senza viaggiare.
  LaunchedEffect(stage) {
    if (stage != IntSize.Zero) {
      selectedPreset = PresetCircle
      state.snapTo(presetForm(PresetCircle)!!)
    }
  }

  // Il banco di misura: avanti e indietro fra la base e il bersaglio finché il loop è acceso.
  LaunchedEffect(loopLabel, stage) {
    if (loopLabel == "No" || stage == IntSize.Zero) return@LaunchedEffect
    val target = if (loopLabel == "Blob") PresetBlob else PresetSquare
    while (true) {
      state.morphTo(presetForm(target)!!, FluidMotion.standard())
      delay(140)
      state.morphTo(presetForm(PresetCircle)!!, FluidMotion.standard())
      delay(140)
    }
  }

  FluidScreen(
    title = "Playground",
    subtitle = "Fluid-physics: qualsiasi forma di vetro diventa qualsiasi altra.",
    extraBottomPadding = bottomInset,
    ambient = remember {
      FluidAmbient(tone = FluidHeroTone.TertiaryToPrimary, motif = FluidHeroMotif.Cards)
    },
  ) {
    item(key = "stage") {
      PlaygroundStage(
        state = state,
        tier = tierOverride,
        drawing = drawing,
        onSized = { stage = it },
        onStrokeFinished = { points ->
          val form = FluidFormPresets.fromFreehand(points)
          (form as? FluidForm.Poly)?.let { lastFreehand = it }
          selectedPreset = PresetFreehand
          drawing = false
          morph(form)
        },
      )
    }

    item(key = "presets") {
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PresetNames.forEach { name ->
          FluidChip(
            label = name,
            selected = !drawing && selectedPreset == name,
            onClick = {
              if (stage == IntSize.Zero) return@FluidChip
              if (name == PresetBlob) blobSeed += 1
              drawing = false
              selectedPreset = name
              morph(presetForm(name)!!)
            },
          )
        }
        FluidChip(
          label = "Disegna",
          selected = drawing,
          onClick = { drawing = !drawing },
        )
      }
    }

    item(key = "drawing-hint") {
      if (drawing) {
        Text(
          text = "Disegna una sagoma chiusa sul palco, con un dito: al rilascio il vetro la raggiunge.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "motion-header") { FluidSectionHeader(title = "Molla e materiale") }
    item(key = "motion") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = listOf("Casa", "Scattante", "Standard", "Dolce"),
          selected = springLabel,
          onSelect = { springLabel = it },
          label = { it },
        )
        FluidSegmentedControl(
          options = listOf("Auto", "Full", "Bilanciato", "Lite"),
          selected = tierLabel,
          onSelect = { tierLabel = it },
          label = { it },
        )
        Text(
          text = tierDescription(tierLabel),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "loop") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = listOf("No", "Quadrato", "Blob"),
          selected = loopLabel,
          onSelect = { choice ->
            loopLabel = choice
            if (choice == "No" && stage != IntSize.Zero) {
              selectedPreset = PresetCircle
              morph(presetForm(PresetCircle)!!)
            }
          },
          label = { it },
        )
        Text(
          text = "Loop A↔B per le misure: lascia girare e leggi i framestats con gfxinfo.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "saved-header") { FluidSectionHeader(title = "Forme tue") }
    item(key = "saved") {
      androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (lastFreehand != null) {
          FluidButton(
            text = "Salva l'ultima forma disegnata",
            onClick = {
              val form = lastFreehand ?: return@FluidButton
              savedShapes += SavedShape(
                name = "Forma ${savedShapes.size + 1}",
                rounding = form.rounding,
                vertices = form.vertices,
              )
              scope.launch(Dispatchers.IO) { store.persist(savedShapes.toList()) }
            },
            fillWidth = true,
          )
        }
        if (savedShapes.isEmpty()) {
          Text(
            text = "Disegna una sagoma e salvala: la ritrovi qui, pronta a essere vetro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          FluidListGroup(glass = true) {
            savedShapes.forEachIndexed { index, shape ->
              if (index > 0) FluidListDivider()
              FluidListRow(
                title = shape.name,
                subtitle = "${shape.vertices.size} vertici — tieni premuto per eliminarla",
                onClick = {
                  if (stage == IntSize.Zero) return@FluidListRow
                  selectedPreset = PresetFreehand
                  morph(savedShapeForm(shape, stage))
                },
                contextActions = {
                  listOf(
                    FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) {
                      savedShapes.remove(shape)
                      scope.launch(Dispatchers.IO) { store.persist(savedShapes.toList()) }
                    },
                  )
                },
              )
            }
          }
        }
      }
    }

    item(key = "practical-header") { FluidSectionHeader(title = "In pratica") }
    item(key = "practical") { PracticalDemo(tier = tierOverride) }
  }
}

private const val PresetCircle = "Cerchio"
private const val PresetSquare = "Quadrato"
private const val PresetBlob = "Blob"
private const val PresetFreehand = "Disegno"

private val PresetNames = listOf(
  PresetCircle, PresetSquare, "Rettangolo", "Capsula", "Trapezio", "Stella", PresetBlob,
  "Due pezzi", "Tre pezzi",
)

/** Le geometrie dei preset, in pixel del palco. */
private fun playgroundPresetForm(
  name: String,
  stage: IntSize,
  cardRadiusPx: Float,
  groupRadiusPx: Float,
  blobSeed: Int,
): FluidForm? {
  if (stage == IntSize.Zero) return null
  val w = stage.width.toFloat()
  val h = stage.height.toFloat()
  val minSide = minOf(w, h)
  val center = Offset(w / 2f, h / 2f)

  fun centered(fraction: Float): Rect {
    val side = minSide * fraction
    return Rect(center.x - side / 2f, center.y - side / 2f, center.x + side / 2f, center.y + side / 2f)
  }

  return when (name) {
    PresetCircle -> FluidForm.circle(center, minSide * 0.30f)
    PresetSquare -> FluidFormPresets.square(centered(0.55f), radius = cardRadiusPx)
    "Rettangolo" -> FluidFormPresets.rectangle(
      Rect(w * 0.14f, h * 0.30f, w * 0.86f, h * 0.70f),
      radius = groupRadiusPx,
    )
    "Capsula" -> FluidFormPresets.capsule(Rect(w * 0.17f, h * 0.36f, w * 0.83f, h * 0.64f))
    "Trapezio" -> FluidFormPresets.trapezoid(centered(0.60f), rounding = 14f)
    "Stella" -> FluidFormPresets.star(centered(0.62f))
    PresetBlob -> FluidFormPresets.blob(centered(0.60f), seed = blobSeed)
    "Due pezzi" -> FluidForm.Group(
      pieces = listOf(
        FluidForm.circle(Offset(center.x - w * 0.18f, center.y), minSide * 0.15f),
        FluidForm.circle(Offset(center.x + w * 0.18f, center.y), minSide * 0.15f),
      ),
      blendRadius = minSide * 0.12f,
    )
    "Tre pezzi" -> FluidForm.Group(
      pieces = listOf(
        FluidForm.circle(Offset(center.x, center.y - minSide * 0.20f), minSide * 0.13f),
        FluidFormPresets.square(
          Rect(
            center.x - w * 0.24f - minSide * 0.13f,
            center.y + minSide * 0.07f,
            center.x - w * 0.24f + minSide * 0.13f,
            center.y + minSide * 0.07f + minSide * 0.26f,
          ),
          radius = cardRadiusPx,
        ),
        FluidFormPresets.capsule(
          Rect(
            center.x + w * 0.10f,
            center.y + minSide * 0.10f,
            center.x + w * 0.10f + minSide * 0.30f,
            center.y + minSide * 0.10f + minSide * 0.16f,
          ),
        ),
      ),
      blendRadius = minSide * 0.11f,
    )
    else -> null
  }
}

/** Una forma salvata, riposata al centro del palco corrente. */
private fun savedShapeForm(shape: SavedShape, stage: IntSize): FluidForm {
  val w = stage.width.toFloat()
  val h = stage.height.toFloat()
  val side = minOf(w, h) * 0.62f
  val frame = Rect(w / 2f - side / 2f, h / 2f - side / 2f, w / 2f + side / 2f, h / 2f + side / 2f)
  return FluidForm.Poly(frame = frame, vertices = shape.vertices, rounding = shape.rounding)
}

private fun tierDescription(label: String): String = when (label) {
  "Full" -> "Rifrazione dal campo di distanza e ponti liquidi fra i pezzi."
  "Bilanciato" -> "Sfocatura, vividezza, tinta e bordo: niente lente durante il viaggio."
  "Lite" -> "La tinta piena di ripiego, ritagliata sulla sagoma."
  else -> {
    val ceiling = when {
      Build.VERSION.SDK_INT >= 33 -> "Full"
      Build.VERSION.SDK_INT >= 31 -> "Bilanciato"
      else -> "Lite"
    }
    "Il livello lo sceglie il dispositivo: qui il tetto è $ceiling."
  }
}
