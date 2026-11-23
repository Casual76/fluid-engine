package dev.antigravity.fluidengine.viewer.ar

import android.content.Context
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class ArQuad(
  private val engine: Engine,
  context: Context,
  materialPath: String,
  positions: FloatArray,
  screenSpace: Boolean,
) : Closeable {
  val material = context.assets.open(materialPath).use {
    val bytes = it.readBytes()
    Material.Builder().payload(ByteBuffer.wrap(bytes), bytes.size).build(engine)
  }
  val entity = EntityManager.get().create()
  private val vertices = VertexBuffer.Builder().vertexCount(4).bufferCount(if (screenSpace) 2 else 1)
    .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
    .apply {
      if (screenSpace) attribute(VertexBuffer.VertexAttribute.UV0, 1, VertexBuffer.AttributeType.FLOAT2, 0, 8)
    }.build(engine)
  private val indices = IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)

  init {
    vertices.setBufferAt(engine, 0, positions.toBuffer())
    indices.setBuffer(engine, ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).apply {
      asShortBuffer().put(shortArrayOf(0, 1, 2, 0, 2, 3))
    })
    RenderableManager.Builder(1)
      .boundingBox(Box(0f, 0f, 0f, 1f, 1f, 1f))
      .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices)
      .material(0, material.defaultInstance)
      .priority(if (screenSpace) 0 else 4)
      .culling(!screenSpace)
      .castShadows(false)
      .receiveShadows(!screenSpace)
      .build(engine, entity)
  }

  fun setUv(uv: FloatArray) {
    vertices.setBufferAt(engine, 1, uv.toBuffer())
  }

  override fun close() {
    engine.destroyEntity(entity)
    EntityManager.get().destroy(entity)
    engine.destroyIndexBuffer(indices)
    engine.destroyVertexBuffer(vertices)
    engine.destroyMaterial(material)
  }
}

private fun FloatArray.toBuffer(): ByteBuffer =
  ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
    asFloatBuffer().put(this@toBuffer)
  }
