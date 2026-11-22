package dev.antigravity.fluidengine.viewer.office

import android.util.Xml
import dev.antigravity.fluidengine.viewer.*
import java.io.*
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser

internal fun damaged(reason: String): Nothing =
  throw FluidViewerException(FluidViewerFailure.Corrupt, reason)

internal fun protectedFile(): Nothing =
  throw FluidViewerException(FluidViewerFailure.Protected, "Documento protetto da password")

internal fun tooLarge(reason: String): Nothing =
  throw FluidViewerException(FluidViewerFailure.TooLarge, reason)

internal fun parseXml(input: InputStream): XmlPullParser =
  Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    setInput(input, null)
  }

internal fun XmlPullParser.attr(key: String): String? =
  (0 until attributeCount)
    .firstOrNull { getAttributeName(it) == key }
    ?.let { getAttributeValue(it) }

/** Mixed XML content, in source order. Only bounded document parts are materialised. */
internal class Node(val name: String, val attrs: Map<String, String>, val content: List<Any>) {
  val kids
    get() = content.filterIsInstance<Node>()

  operator fun get(key: String) = attrs[key]

  fun child(tag: String) = content.firstOrNull { it is Node && it.name == tag } as? Node

  fun all(tag: String): Sequence<Node> = sequence {
    for (item in content) if (item is Node) {
      if (item.name == tag) yield(item)
      yieldAll(item.all(tag))
    }
  }

  fun first(tag: String) = all(tag).firstOrNull()

  fun text(): String = buildString { appendText(this@Node) }

  private fun StringBuilder.appendText(node: Node) {
    for (item in node.content) if (item is Node) appendText(item) else append(item)
  }
}

internal class XmlBudget(var nodes: Int = 0, var chars: Int = 0)

internal fun XmlPullParser.node(budget: XmlBudget = XmlBudget()): Node {
  if (++budget.nodes > 180_000 || depth > 96) tooLarge("Struttura XML oltre il limite")
  val tag = name
  val attrs = buildMap {
    for (i in 0 until attributeCount) {
      val key = getAttributeName(i)
      put(key, getAttributeValue(i))
      if (key == "id" && getAttributeNamespace(i).endsWith("/relationships"))
        put("r:id", getAttributeValue(i))
    }
  }
  val values = mutableListOf<Any>()
  while (true) when (nextToken()) {
    XmlPullParser.START_TAG -> values += node(budget)
    XmlPullParser.TEXT,
    XmlPullParser.CDSECT,
    XmlPullParser.ENTITY_REF -> {
      val value = text ?: damaged("Entità XML sconosciuta")
      budget.chars += value.length
      if (budget.chars > 8_000_000) tooLarge("Testo XML oltre il limite")
      values += value
    }
    XmlPullParser.END_TAG -> return Node(tag, attrs, values)
    XmlPullParser.DOCDECL,
    XmlPullParser.END_DOCUMENT -> damaged("XML non valido")
  }
}

internal fun readRoot(input: InputStream): Node {
  val p = parseXml(input)
  while (p.eventType != XmlPullParser.START_TAG) {
    when (p.nextToken()) {
      XmlPullParser.DOCDECL,
      XmlPullParser.END_DOCUMENT -> damaged("Documento XML non valido")
    }
  }
  val root = p.node()
  while (p.nextToken() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG)
    damaged("XML con più radici")
  return root
}

internal data class Relationship(val type: String, val path: String)

internal class OfficePackage(val zip: ZipFile) {
  private val relations = mutableMapOf<String, Map<String, Relationship>>()
  private var expanded = 0L

  fun stream(path: String): InputStream? {
    val entry = zip.getEntry(path) ?: return null
    if (entry.size !in 0..32_000_000L) tooLarge("Parte del documento oltre 32 MB")
    expanded += entry.size
    if (expanded > 128_000_000L) tooLarge("Contenuto espanso oltre 128 MB")
    val crc = CRC32()
    return object : FilterInputStream(zip.getInputStream(entry)) {
      private var consumed = 0L

      private fun account(length: Int) {
        if (length < 0) {
          if (consumed != entry.size || crc.value != entry.crc)
            damaged("Parte ZIP incompleta o danneggiata")
        } else {
          consumed += length
          if (consumed > entry.size) tooLarge("La parte ZIP supera la dimensione dichiarata")
        }
      }

      override fun read(): Int {
        val byte = super.read()
        if (byte >= 0) crc.update(byte)
        account(if (byte < 0) -1 else 1)
        return byte
      }

      override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val got = super.read(buffer, offset, length)
        if (got > 0) crc.update(buffer, offset, got)
        account(got)
        return got
      }
    }
  }

  fun xml(path: String) = stream(path)?.use(::readRoot)

  fun required(path: String) = xml(path) ?: damaged("Manca la parte $path")

  fun bytes(path: String) = stream(path)?.use { it.readBytes() }

  fun rels(owner: String): Map<String, Relationship> =
    relations.getOrPut(owner) {
      val folder = owner.substringBeforeLast('/', "")
      val parent = if (folder.isEmpty()) "" else "$folder/"
      val file = owner.substringAfterLast('/')
      xml("${parent}_rels/$file.rels")
        ?.kids
        .orEmpty()
        .mapNotNull { r ->
          val id = r["Id"] ?: return@mapNotNull null
          val target = r["Target"] ?: return@mapNotNull null
          if (r["TargetMode"] == "External") return@mapNotNull null
          val segments = mutableListOf<String>()
          for (part in (if (target.startsWith('/')) target else parent + target).split('/')) when (
            part
          ) {
            "",
            "." -> Unit
            ".." ->
              if (segments.isEmpty()) return@mapNotNull null
              else segments.removeAt(segments.lastIndex)
            else -> segments += part
          }
          id to
            Relationship(r["Type"].orEmpty().substringAfterLast('/'), segments.joinToString("/"))
        }
        .toMap()
    }

  fun target(owner: String, type: String) = rels(owner).values.firstOrNull { it.type == type }?.path
}

internal suspend fun copyOfficeInput(request: FluidViewerRequest, target: File) {
  val limit = minOf(128_000_000L, request.options.sourceMaxCopyBytes)
  request.source.open().use { input ->
    target.outputStream().use { out ->
      val buffer = ByteArray(32 * 1024)
      var total = 0L
      while (true) {
        currentCoroutineContext().ensureActive()
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) tooLarge("File oltre il limite di $limit byte")
        out.write(buffer, 0, n)
      }
    }
  }
}

internal fun String?.points(default: Float = 0f): Float {
  val s = this?.trim() ?: return default
  val unit = s.takeLastWhile { it.isLetter() }
  val value = s.removeSuffix(unit).toFloatOrNull()?.takeIf { it.isFinite() } ?: return default
  return value *
    when (unit) {
      "cm" -> 72f / 2.54f
      "mm" -> 72f / 25.4f
      "in" -> 72f
      "pc" -> 12f
      "px" -> .75f
      else -> 1f
    }
}

internal fun String?.twips(default: Float = 0f) = this?.toIntOrNull()?.div(20f) ?: default

internal fun String?.rgb(): Int? =
  this?.removePrefix("#")
    ?.takeIf { it.length in setOf(6, 8) }
    ?.toLongOrNull(16)
    ?.let { (it or 0xff000000L).toInt() }
