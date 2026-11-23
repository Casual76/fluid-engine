package dev.antigravity.fluidengine.viewer.scene

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class SceneDeviceTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context get() = instrumentation.targetContext

  @Test
  fun shadowMaterialAndStudioLoadOnThePackagedFilament() = native { engine ->
    val bytes = context.assets.open(GroundAsset).use { ByteBuffer.wrap(it.readBytes()) }
    val material = Material.Builder().payload(bytes, bytes.remaining()).build(engine)
    try {
      assertEquals("FluidViewerGround", material.name)
    } finally {
      engine.destroyMaterial(material)
    }
    val ktx = context.assets.open(StudioAsset).use { ByteBuffer.wrap(it.readBytes()) }
    val lighting = KTX1Loader.createIndirectLight(engine, ktx)
    try {
      assertNotNull(lighting.indirectLight)
      assertNotNull(lighting.cubemap)
    } finally {
      lighting.indirectLight?.let(engine::destroyIndirectLight)
      lighting.cubemap?.let(engine::destroyTexture)
    }
  }

  @Test
  fun nativeLoadsEachFormatAndCanMountModelTwice() {
    val names = listOf("anelli.glb", "tessuto.gltf", "piramide-ascii.stl", "piramide-binaria.stl", "piramide.obj")
    val models = runBlocking { names.map { name -> context.corpusSource(name).use { openFluidSceneModel(it) } } }
    native { engine ->
      val scene = engine.createScene()
      try {
        for ((name, model) in names.zip(models)) {
          model.load(engine).use { loaded ->
            loaded.attach(scene)
            assertTrue(name, loaded.asset.renderableEntities.isNotEmpty())
            assertEquals(name, model.stats.animations, loaded.asset.instance.animator.animationCount)
            loaded.updateResources()
          }
        }
        models.first().load(engine).use { reused -> reused.attach(scene); reused.detach(scene) }
      } finally {
        engine.destroyScene(scene)
      }
    }
  }

  private fun native(test: (Engine) -> Unit) {
    var failure: Throwable? = null
    instrumentation.runOnMainSync {
      try {
        Utils.init()
        val engine = Engine.create()
        try {
          test(engine)
        } finally {
          engine.destroy()
        }
      } catch (t: Throwable) {
        failure = t
      }
    }
    failure?.let { throw it }
  }
}
