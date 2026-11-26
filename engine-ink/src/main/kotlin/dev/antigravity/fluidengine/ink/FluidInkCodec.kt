package dev.antigravity.fluidengine.ink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Il salvataggio dei tratti, per riaprire un disegno e continuare: una nota di Pampa Notes con il suo schizzo, la firma
 * salvata, le annotazioni di un PDF ancora da finire. Binario e compresso (un disegno di cento tratti pesa qualche decina
 * di kB), con la versione in testa: una versione piu' nuova dell'engine legge le vecchie.
 *
 * Il formato e' dell'engine, non di androidx.ink (che ha il suo, in protobuf): cosi' un salto di versione della libreria
 * non tocca i disegni salvati, e il formato si legge anche sul computer.
 *
 * Non tiene la cronologia di annulla e ripristina: riaperto, un disegno ricomincia da li', come una nota di Note.
 */
object FluidInkCodec {
  /** «FINK» in testa al file. */
  private const val Magic = 0x46494E4B

  /** La versione che scrive questa build. */
  const val Version: Int = 1

  /** I tratti in byte. */
  fun encode(strokes: List<FluidInkStroke>): ByteArray {
    val bytes = ByteArrayOutputStream()
    write(strokes, bytes)
    return bytes.toByteArray()
  }

  /** I tratti su [output] (non lo chiude). */
  fun write(strokes: List<FluidInkStroke>, output: OutputStream) {
    val head = DataOutputStream(output)
    head.writeInt(Magic)
    head.writeShort(Version)
    val deflater = DeflaterOutputStream(NonClosing(output))
    val out = DataOutputStream(deflater)
    out.writeInt(strokes.size)
    for (s in strokes) {
      out.writeLong(s.id)
      out.writeShort(s.tool.id)
      out.writeInt(s.color.toArgb())
      out.writeFloat(s.size)
      out.writeByte(s.input.id)
      out.writeByte(s.shape?.id ?: 0)
      val p = s.points
      var flags = 0
      if (p.hasPressure) flags = flags or 1
      if (p.hasTilt) flags = flags or 2
      if (p.hasOrientation) flags = flags or 4
      out.writeByte(flags)
      out.writeInt(p.size)
      for (i in 0 until p.size) {
        out.writeFloat(p.x(i))
        out.writeFloat(p.y(i))
        out.writeFloat(p.time(i))
        if (p.hasPressure) out.writeFloat(p.pressure(i))
        if (p.hasTilt) out.writeFloat(p.tilt(i))
        if (p.hasOrientation) out.writeFloat(p.orientation(i))
      }
    }
    out.flush()
    deflater.finish()
    head.flush()
  }

  /** I tratti salvati con [encode]. Un file che non e' un disegno, o rotto, da' [IOException]. */
  @Throws(IOException::class)
  fun decode(bytes: ByteArray): List<FluidInkStroke> = read(ByteArrayInputStream(bytes))

  /** I tratti da [input] (non lo chiude). */
  @Throws(IOException::class)
  fun read(input: InputStream): List<FluidInkStroke> {
    val head = DataInputStream(input)
    if (head.readInt() != Magic) throw IOException("Non e' un disegno di engine-ink")
    val version = head.readUnsignedShort()
    if (version > Version) throw IOException("Disegno salvato da una versione piu' nuova ($version)")
    val data = DataInputStream(InflaterInputStream(input))
    val count = data.readInt()
    if (count < 0 || count > MaxStrokes) throw IOException("Numero di tratti non valido: $count")
    val strokes = ArrayList<FluidInkStroke>(count)
    repeat(count) {
      val id = data.readLong()
      val tool = FluidInkTool.fromId(data.readUnsignedShort())
      val color = Color(data.readInt())
      val size = data.readFloat()
      val input = FluidInkInput.fromId(data.readUnsignedByte())
      val shape = FluidInkShapeKind.fromId(data.readUnsignedByte())
      val flags = data.readUnsignedByte()
      val n = data.readInt()
      if (n < 0 || n > MaxPoints) throw IOException("Numero di punti non valido: $n")
      val builder = FluidInkPointsBuilder(n)
      for (i in 0 until n) {
        val x = data.readFloat()
        val y = data.readFloat()
        val t = data.readFloat()
        val pressure = if (flags and 1 != 0) data.readFloat() else FluidInkPoints.None
        val tilt = if (flags and 2 != 0) data.readFloat() else FluidInkPoints.None
        val orientation = if (flags and 4 != 0) data.readFloat() else FluidInkPoints.None
        builder.add(x, y, t, pressure, tilt, orientation)
      }
      // Uno strumento sconosciuto (di una versione futura) torna penna: il disegno si vede comunque.
      if (size > 0f && builder.size > 0) {
        strokes += FluidInkStroke(id, tool?.takeIf { it.isInking } ?: FluidInkTool.Pen, color, size, builder.build(), input, shape)
      }
    }
    return strokes
  }

  private const val MaxStrokes = 1_000_000
  private const val MaxPoints = 10_000_000

  private class NonClosing(private val target: OutputStream) : OutputStream() {
    override fun write(b: Int) = target.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
    override fun flush() = target.flush()
    override fun close() = target.flush()
  }
}
