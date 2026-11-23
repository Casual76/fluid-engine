package dev.antigravity.fluidengine.viewer.data

import dev.antigravity.fluidengine.viewer.FluidTextDecoder
import dev.antigravity.fluidengine.viewer.FluidTextDocument

/**
 * Un albero scritto come testo: JSON rientrato per oggetti ed elenchi (il «Testo» di un plist binario, che non ha un
 * testo suo, e la copia di un ramo con la pressione lunga), XML per gli elementi. Con un tetto di caratteri.
 */
internal object DataTreeText {
  private const val MaxChars = 4 * 1024 * 1024

  fun render(node: DataNode): String {
    val out = StringBuilder()
    write(node, 0, out, key = false)
    return out.toString()
  }

  fun document(tree: DataTree, name: String): FluidTextDocument? {
    val root = tree.root ?: return null
    val bytes = render(root).toByteArray(Charsets.UTF_8)
    return FluidTextDecoder.decode(bytes, fileName = "$name.json", mimeType = "application/json")
  }

  private fun quote(s: String): String = buildString {
    append('"')
    for (c in s) when (c) {
      '"' -> append("\\\"")
      '\\' -> append("\\\\")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
    }
    append('"')
  }

  private fun write(node: DataNode, depth: Int, out: StringBuilder, key: Boolean) {
    if (out.length > MaxChars) return
    val pad = "  ".repeat(depth)
    if (key) out.append(pad).append(quote(node.name.orEmpty())).append(": ") else out.append(pad)
    when (node.kind) {
      DataKind.Object, DataKind.Array -> {
        val children = node.children.orEmpty()
        val (open, close) = if (node.kind == DataKind.Object) "{" to "}" else "[" to "]"
        if (children.isEmpty()) {
          out.append(open).append(close)
          return
        }
        out.append(open).append('\n')
        children.forEachIndexed { i, child ->
          write(child, depth + 1, out, key = node.kind == DataKind.Object)
          if (i < children.lastIndex) out.append(',')
          out.append('\n')
        }
        out.append(pad).append(close)
      }
      DataKind.Element -> {
        val attributes = node.children.orEmpty().filter { it.kind == DataKind.Attribute }
        val others = node.children.orEmpty().filter { it.kind != DataKind.Attribute }
        out.append('<').append(node.name)
        attributes.forEach { out.append(' ').append(it.name).append("=").append(quote(it.value.orEmpty())) }
        when {
          node.value != null -> out.append('>').append(node.value).append("</").append(node.name).append('>')
          others.isEmpty() -> out.append("/>")
          else -> {
            out.append(">\n")
            others.forEach { write(it, depth + 1, out, key = false); out.append('\n') }
            out.append(pad).append("</").append(node.name).append('>')
          }
        }
      }
      DataKind.String, DataKind.Date, DataKind.Data -> out.append(quote(node.value.orEmpty()))
      DataKind.Text -> out.append(node.value.orEmpty())
      DataKind.Comment -> out.append("<!-- ").append(node.value.orEmpty()).append(" -->")
      DataKind.Instruction -> out.append("<?").append(node.value.orEmpty()).append("?>")
      DataKind.Attribute -> out.append(node.name).append('=').append(quote(node.value.orEmpty()))
      else -> out.append(node.value.orEmpty())
    }
  }
}
