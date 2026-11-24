package dev.antigravity.fluidengine.viewer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.viewer.FluidThumbnailPainter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Static page preview. Uses the same raster renderer on Android and in the JVM tests. */
internal object HtmlThumbnail {
  const val MaxHtmlBytes = 512 * 1024
  private const val MaxCssBytes = 96 * 1024
  private const val MaxImageBytes = 768 * 1024

  private val ignored = Regex("""(?is)<!--.*?(?:-->|$)|<(script|title|noscript|template|iframe|object|svg|video|audio)\b[^>]*>.*?(?:</\1\s*>|$)""")
  private const val TagTail = """(?:[^'"<>]++|"[^"]*+"|'[^']*+')*+>"""
  private val stylesAndLinks = Regex("""(?is)<style\b[^>]*>(.*?)</style\s*>|<link\b$TagTail""")
  private val tags = Regex("""(?is)</?([a-z][\w:-]*)$TagTail""")
  private val attributes = Regex("""([\w:-]+)(?:\s*=\s*(?:"([^"]*+)"|'([^']*+)'|([^\s"'=<>]+)))?""")
  private val cssRules = Regex("""([^{}]+)\{([^{}]*)\}""")
  private val cssComments = Regex("""(?s)/\*.*?\*/""")
  private val selector = Regex("""(?:[a-z][\w-]*|\*)?(?:[.#][\w-]+)*""", RegexOption.IGNORE_CASE)
  private val selectorNames = Regex("""[.#]?[\w-]+""")
  private val space = Regex("""\s+""")
  private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "wbr")
  private val mutedTags = setOf("head", "link", "base", "meta", "input", "embed")
  private val containers = setOf("article", "aside", "footer", "header", "main", "nav", "section")
  private val markupTags = setOf("a", "b", "big", "blockquote", "br", "cite", "code", "del", "div", "em", "h1", "h2",
    "h3", "h4", "h5", "h6", "i", "li", "ol", "p", "s", "small", "span", "strike", "strong", "sub", "sup", "tt", "u", "ul")
  private val rasterTypes = setOf("png", "jpeg", "gif", "webp", "bmp", "avif", "heic", "heif")

  suspend fun render(html: String, sizePx: Int, reserve: (Long) -> Boolean = { true }, openLocal: (String) -> InputStream?): ImageBitmap {
    val job = currentCoroutineContext()
    val resources = Resources(openLocal, reserve)
    val rules = mutableListOf<CssRule>()
    val staticHtml = ignored.replace(html, "")
    for (tag in stylesAndLinks.findAll(staticHtml).take(32)) {
      job.ensureActive()
      val inline = tag.groups[1]?.value
      val css = inline ?: run {
        val attrs = attrs(tag.value.substring(5))
        if ("stylesheet" in attrs["rel"].orEmpty().lowercase().split(space)) {
          resources.local(attrs["href"].orEmpty(), MaxCssBytes)?.decodeToString()
        } else null
      }
      if (css != null) addRules(css, rules)
    }

    val source = stylesAndLinks.replace(staticHtml, "")
    val output = StringBuilder(source.length)
    val stack = mutableListOf<Element>()
    val styles = mutableListOf<Map<String, String>>()
    val images = mutableListOf<Pair<Bitmap, Map<String, String>>>()
    var pageStyle = emptyMap<String, String>()
    var cursor = 0
    try {
      for ((index, tag) in tags.findAll(source).withIndex()) {
        if (index and 63 == 0) job.ensureActive()
        if (index >= 2048 || stack.size >= 96) break
        if (stack.lastOrNull()?.hidden != true) output.append(source, cursor, tag.range.first)
        cursor = tag.range.last + 1
        val name = tag.groupValues[1].lowercase()
        if (tag.value.startsWith("</")) {
          val open = stack.indexOfLast { it.name == name }
          if (open >= 0) while (stack.size > open) stack.removeAt(stack.lastIndex).close(output)
          continue
        }
        val element = Element(name, attrs(tag.value.substring(1 + name.length)))
        val style = computedStyle(element, stack, rules)
        element.hidden = stack.lastOrNull()?.hidden == true || name in mutedTags || "hidden" in element.attrs || style["display"] == "none"
        if (!element.hidden) when (name) {
          "html" -> pageStyle = style
          "body" -> pageStyle = pageStyle + style
          "img" -> resources.image(element.attrs["src"].orEmpty())?.let {
            output.append("<img src=\"").append(images.size).append("\">")
            images += it to (element.attrs.filterKeys { key -> key == "width" || key == "height" } + style)
          }
          else -> {
            val markup = when {
              name in containers -> "div"
              name in markupTags -> name
              else -> null
            }
            if (markup == "a" && "href" in element.attrs) output.append("<a href=\"#\">")
            else if (markup != null) output.append('<').append(markup).append('>')
            if (name !in voidTags) {
              element.markup = markup
              if (style.isNotEmpty()) {
                element.styleIndex = styles.size
                styles += style
                output.append("<fluidstyle").append(element.styleIndex).append('>')
              }
            }
          }
        }
        if (name !in voidTags) stack += element
      }
      if (stack.lastOrNull()?.hidden != true) output.append(source, cursor, minOf(source.length, cursor + 12_000))
      while (stack.isNotEmpty()) stack.removeAt(stack.lastIndex).close(output)

      return FluidThumbnailPainter.page(sizePx, clip = false) { canvas, content, unit ->
        color(pageStyle["background-color"] ?: pageStyle["background"])?.let(canvas::drawColor)
        val scale = content.width() / 340f
        val base = unit * 0.72f
        val ink = color(pageStyle["color"]) ?: FluidThumbnailPainter.Ink
        val paint = FluidThumbnailPainter.paint(length(pageStyle["font-size"], scale, base) ?: base, ink)
        paint.typeface = Typeface.create(fontFamily(pageStyle["font-family"] ?: "sans-serif"), fontStyle(pageStyle))
        val openMarks = mutableMapOf<Int, Int>()
        val spans = mutableListOf<Triple<Int, Int, Int>>()
        val rendered = Html.fromHtml(output.toString(), Html.FROM_HTML_MODE_COMPACT, { value ->
          value.toIntOrNull()?.let(images::getOrNull)?.let { ThumbnailImage(it.first, it.second, scale, content.width()) }
        }, { opening, tag, text, _ ->
          val id = tag.takeIf { it.startsWith("fluidstyle") }?.drop(10)?.toIntOrNull()
          if (id != null && id in styles.indices) {
            if (opening) openMarks[id] = text.length
            else openMarks.remove(id)?.let { start -> if (start < text.length) spans += Triple(id, start, text.length) }
          }
        })
        val text = SpannableStringBuilder(rendered)
        for ((id, start, end) in spans.sortedBy { it.first }) {
          if (start < text.length) applySpans(text, styles[id], start, minOf(end, text.length), scale, paint.textSize)
        }
        val layout = FluidThumbnailPainter.layout(text, paint, content.width().toInt())
        canvas.save()
        canvas.clipRect(content)
        FluidThumbnailPainter.draw(canvas, layout, content.left, content.top)
        canvas.restore()
      }
    } finally {
      images.forEach { it.first.recycle() }
    }
  }

  private fun attrs(tag: String) = attributes.findAll(tag).associate {
    val value = it.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
    it.groupValues[1].lowercase() to if ('&' !in value) value else
      Html.fromHtml(value.replace("<", "&lt;").replace(">", "&gt;"), Html.FROM_HTML_MODE_COMPACT).toString()
  }

  private class Element(val name: String, val attrs: Map<String, String>) {
    var hidden = false
    var styleIndex = -1
    var markup: String? = null

    fun close(output: StringBuilder) {
      if (styleIndex >= 0) output.append("</fluidstyle").append(styleIndex).append('>')
      markup?.let { output.append("</").append(it).append('>') }
    }

    fun matches(parts: List<String>) = parts.all { value ->
      when (value.first()) {
        '#' -> attrs["id"] == value.drop(1)
        '.' -> value.drop(1) in attrs["class"].orEmpty().split(space)
        else -> name.equals(value, ignoreCase = true)
      }
    }
  }

  private data class CssRule(val parts: List<List<String>>, val specificity: Int, val declarations: Map<String, String>)

  private fun addRules(css: String, rules: MutableList<CssRule>) {
    for (block in cssRules.findAll(cssComments.replace(css.take(MaxCssBytes), ""))) {
      val values = declarations(block.groupValues[2])
      if (values.isEmpty()) continue
      for (name in block.groupValues[1].split(',')) {
        if (rules.size >= 256) return
        val parts = name.trim().split(space)
        if (parts.size > 8 || parts.any { it.isEmpty() || !selector.matches(it) }) continue
        val match = parts.map { selectorNames.findAll(it).map { token -> token.value }.toList() }
        val score = match.flatten().fold(0) { score, token ->
          score + when (token.first()) { '#' -> 100; '.' -> 10; else -> 1 }
        }
        rules += CssRule(match, score, values)
      }
    }
  }

  private fun declarations(text: String): Map<String, String> = text.split(';').mapNotNull { value ->
    val split = value.indexOf(':')
    if (split < 0) null else value.substring(0, split).trim().lowercase() to value.substring(split + 1).trim()
  }.toMap()

  private fun computedStyle(element: Element, parents: List<Element>, rules: List<CssRule>): Map<String, String> = buildMap {
    val matches = rules.filter { rule ->
      if (!element.matches(rule.parts.last())) return@filter false
      var index = parents.lastIndex
      for (part in rule.parts.dropLast(1).asReversed()) {
        while (index >= 0 && !parents[index].matches(part)) index--
        if (index-- < 0) return@filter false
      }
      true
    }.sortedBy { it.specificity }
    val important = mutableSetOf<String>()
    fun add(style: Map<String, String>) {
      for ((name, value) in style) {
        if (value.endsWith("!important", ignoreCase = true)) {
          important += name
          put(name, value.dropLast(10).trim())
        } else if (name !in important) put(name, value)
      }
    }
    matches.forEach { add(it.declarations) }
    add(declarations(element.attrs["style"].orEmpty()))
  }

  private fun applySpans(out: SpannableStringBuilder, css: Map<String, String>, start: Int, end: Int, scale: Float, base: Float) {
    fun span(value: Any) = out.setSpan(value, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    color(css["color"])?.let { span(ForegroundColorSpan(it)) }
    color(css["background-color"] ?: css["background"])?.let { span(BackgroundColorSpan(it)) }
    css["font-family"]?.let { span(TypefaceSpan(fontFamily(it))) }
    length(css["font-size"], scale, base)?.let { span(AbsoluteSizeSpan(it.roundToInt())) }
    val style = fontStyle(css)
    if (style != Typeface.NORMAL) span(StyleSpan(style))
    val decoration = css["text-decoration"].orEmpty().split(space)
    if ("underline" in decoration) span(UnderlineSpan())
    if ("line-through" in decoration) span(StrikethroughSpan())
  }

  private fun fontFamily(value: String): String {
    val names = value.lowercase().split(',').map { it.trim().trim('"', '\'') }
    return names.firstOrNull { it in setOf("serif", "sans-serif", "monospace", "cursive") }
      ?: if (names.any { it == "georgia" || it == "times new roman" }) "serif" else "sans-serif"
  }

  private fun fontStyle(css: Map<String, String>): Int {
    val weight = css["font-weight"].orEmpty()
    val bold = weight == "bold" || weight == "bolder" || (weight.toIntOrNull() ?: 0) >= 600
    val italic = css["font-style"] == "italic" || css["font-style"] == "oblique"
    return (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
  }

  private fun color(value: String?): Int? {
    val code = value?.trim()?.lowercase() ?: return null
    val hex = if (code.length == 4 && code.startsWith('#')) "#" + code.drop(1).map { "$it$it" }.joinToString("") else code
    return try {
      if (hex == "transparent") Color.TRANSPARENT else Color.parseColor(hex)
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  private fun length(value: String?, scale: Float, reference: Float): Float? {
    val text = value?.trim()?.lowercase() ?: return null
    val result = when {
      text.endsWith("rem") -> text.dropLast(3).toFloatOrNull()?.times(reference)
      text.endsWith("em") -> text.dropLast(2).toFloatOrNull()?.times(reference)
      text.endsWith("%") -> text.dropLast(1).toFloatOrNull()?.times(reference / 100)
      else -> text.removeSuffix("px").toFloatOrNull()?.times(scale)
    }
    return result?.takeIf { it.isFinite() && it >= 0 }?.coerceAtMost(4096f)
  }

  private class ThumbnailImage(private val bitmap: Bitmap, css: Map<String, String>, scale: Float, available: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private val radius = length(css["border-radius"], scale, available) ?: 0f

    init {
      val aspect = bitmap.width.toFloat() / bitmap.height
      val width = length(css["width"], scale, available)
      val height = length(css["height"], scale, available)
      val w = width ?: height?.times(aspect) ?: bitmap.width * scale
      val h = height ?: w / aspect
      val fit = minOf(1f, available / maxOf(1f, w), available * 1.35f / maxOf(1f, h))
      setBounds(0, 0, (w * fit).roundToInt().coerceAtLeast(1), (h * fit).roundToInt().coerceAtLeast(1))
    }

    override fun draw(canvas: Canvas) {
      rect.set(bounds)
      canvas.save()
      if (radius > 0f) {
        clip.rewind()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
      }
      canvas.drawBitmap(bitmap, null, rect, paint)
      canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
  }

  private class Resources(private val openLocal: (String) -> InputStream?, private val reserve: (Long) -> Boolean) {
    private var attempts = 0

    fun local(reference: String, limit: Int): ByteArray? {
      val decoded = Uri.decode(reference.substringBefore('#').substringBefore('?')).trim().replace('\\', '/')
      if (decoded.startsWith('/') || decoded.substringBefore('/').contains(':') ||
        decoded.split('/').any { it == ".." } || decoded.any { it.isISOControl() }) return null
      val path = DataWeb.resolve("", reference) ?: return null
      if (attempts++ >= 16) return null
      return try {
        openLocal(path)?.use { stream ->
          val output = ByteArrayOutputStream()
          val buffer = ByteArray(8192)
          while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count > limit - output.size() || !reserve(4L * count)) return null
            output.write(buffer, 0, count)
          }
          output.toByteArray()
        }
      } catch (_: IOException) {
        null
      } catch (_: SecurityException) {
        null
      }
    }

    fun image(reference: String): Bitmap? {
      val bytes = if (reference.startsWith("data:", ignoreCase = true)) {
        val header = reference.substringBefore(',').lowercase()
        if (!header.startsWith("data:image/") || !header.endsWith(";base64") ||
          header.substringAfter('/').substringBefore(';') !in rasterTypes ||
          reference.length > MaxImageBytes * 4 / 3 || attempts++ >= 16) return null
        try {
          if (!reserve(reference.length * 2L)) return null
          Base64.decode(reference.substringAfter(',', ""), Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
          return null
        }
      } else local(reference, MaxImageBytes)
      if (bytes == null || bytes.isEmpty()) return null
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      val edge = maxOf(bounds.outWidth, bounds.outHeight)
      if (edge <= 0) return null
      var sample = 1
      while (edge / sample > 768) sample *= 2
      while (!reserve(((bounds.outWidth.toLong() + sample - 1) / sample * 16 + 63) / 64 * 64 *
          ((bounds.outHeight.toLong() + sample - 1) / sample))) {
        if (edge / sample <= 1) return null
        sample *= 2
      }
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
  }
}
