package dev.antigravity.fluidengine.ui.fluid.action

import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import org.junit.Assert.*
import org.junit.Test

class FluidActionGlyphsTest {
  private val profile = FluidActionGlyphProfile

  @Test fun `ogni preset ha il suo disegno e Custom conserva lo scambio di serie`() {
    for (kind in FluidActionKind.entries - FluidActionKind.Custom) assertNotNull(FluidActionGlyphs.of(kind))
    assertNull(FluidActionGlyphs.of(FluidActionKind.Custom))
  }

  @Test fun `Salva chiude e spegne il tratto prima dei 267 ms della spunta`() {
    assertNotSame(FluidActionGlyphs.of(FluidActionKind.Save), FluidActionGlyphs.of(FluidActionKind.Download))
    assertEquals(0f, profile.saveClosure(0f), 0f)
    assertTrue(profile.saveClosure(24f) in .1f..0.9f)
    assertEquals(1f, profile.saveClosure(48f), 0f)
    assertEquals(1f, profile.saveRingAlpha(48f), 0f)
    assertEquals(0f, profile.saveRingAlpha(67f), 0f)
    assertEquals(0f, profile.saveReveal(67f), 0f)
    assertTrue(profile.saveReveal(133f) > FluidCheckMask.elbowAt)
    assertEquals(1f, profile.saveReveal(334f), 0f)
  }

  @Test fun `la maschera parte a sinistra scende al gomito e risale fino alla punta`() {
    val tips = (0..100).map { FluidCheckMask.tip(it / 100f) }
    assertEquals(FluidCheckMask.start, tips.first())
    assertEquals(FluidCheckMask.elbow, FluidCheckMask.tip(FluidCheckMask.elbowAt))
    assertEquals(FluidCheckMask.end.x, tips.last().x, .001f)
    assertEquals(FluidCheckMask.end.y, tips.last().y, .001f)
    assertTrue(tips.zipWithNext().all { (a,b) -> b.x > a.x })
    val deepest = tips.maxOf(Offset::y)
    assertTrue(deepest > tips.first().y && deepest > tips.last().y)
  }

  @Test fun `la freccia cade prima del pieno sia per Carica sia per Scarica`() {
    val endDrop = profile.DropMs.toFloat()
    assertEquals(0f, profile.fill(endDrop, true), 0f)
    assertEquals(1f, profile.fill(endDrop + 67f, true), 0f)
    assertEquals(0f, profile.reveal(endDrop + 67f, true), 0f)
    assertEquals(1f, profile.reveal(endDrop + 334f, true), 0f)
  }

  @Test fun `una pausa conserva arco e percentuale e il lettore riprende dallo stesso punto`() {
    var fraction = .42f
    val progress = FluidActionProgress().apply { source = { fraction } }
    for (preset in listOf(FluidActionPreset.Download, FluidActionPreset.Upload)) {
      val face = fluidActionFace(preset, FluidActionState.Working({ progress.read() }))
      assertTrue(face.digits)
      assertEquals(FluidActionRing.Determinate, face.ring)
      fraction = .42f
      repeat(30) { assertEquals(42, fluidActionPercent(progress.read())) }
      fraction = .73f
      assertEquals(73, fluidActionPercent(progress.read()))
    }
  }

  @Test fun `il cuore ottimistico ha un solo rimbalzo e il rifiuto ripristina il verso giusto`() {
    assertEquals(1f, profile.heartScale(0f), 0f)
    assertEquals(1f + profile.HeartGrowth, profile.heartScale(.5f), .0001f)
    assertEquals(1f, profile.heartScale(1f), .0001f)
    for (selected in listOf(false, true)) {
      val preset = FluidActionPreset.favorite(selected)
      val optimistic = fluidActionFace(preset, FluidActionState.Working())
      assertEquals(if (selected) FluidIcons.Favorite else FluidIcons.Filled.Favorite, optimistic.icon)
      val rejected = fluidActionFace(preset, FluidActionState.Failed)
      assertEquals(preset.idle, rejected.icon)
      assertEquals(FluidActionInk.Error, rejected.ink)
    }
  }

  @Test fun `Copia salta il lavoro e conserva la spunta per un secondo`() {
    val preset = FluidActionPreset.Copy
    assertEquals(fluidActionFace(preset, FluidActionState.Idle), fluidActionFace(preset, FluidActionState.Working()))
    assertEquals(1000L, fluidActionHoldMs(preset, FluidActionHold.Preset, FluidActionState.Succeeded))
  }
}
