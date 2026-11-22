package dev.antigravity.fluidengine.viewer.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridCell
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridSheet
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/**
 * F6C.13 · Un CSV come griglia, **letto a flusso**: le righe si contano in sottofondo, e la griglia mostra le prime
 * mentre il resto si legge (`FluidGridSheet.countFinal` falso, il conto che cresce ogni [DataProfile.CsvPublishRows]).
 *
 * In memoria resta solo dove comincia ogni riga (8 byte per riga: 100.000 righe sono 800 KB). Un blocco di celle si
 * rilegge dal file con una lettura posizionale e si analizza con le stesse regole del conteggio ([DataCsv.records]),
 * così le righe del conto e quelle lette coincidono anche con a capo dentro le virgolette.
 *
 * Il file deve essere in un charset in cui gli a capo, le virgolette e i separatori sono un byte solo (UTF-8 e i
 * charset a un byte): un UTF-16 lo trascrive prima in UTF-8 chi crea la griglia ([DataFamilyCsv]).
 */
internal class DataCsvGrid(
  private val name: String,
  private val channel: FileChannel,
  private val scan: suspend () -> InputStream,
  private val start: Long,
  val dialect: CsvDialect,
  private val charset: Charset,
  val header: Boolean,
  private val widths: Map<Int, Float>,
  initialColumns: Int,
  private val closeResources: () -> Unit = {},
  private val pace: (suspend (rows: Int) -> Unit)? = null,
  private val onFinished: (DataCsvGrid) -> Unit = {},
) : FluidGridSource, Closeable {

  private val lock = Any()
  private var offsets = LongArray(1024).also { it[0] = start }
  private var completed = 0
  private var columns = initialColumns.coerceAtLeast(1)

  /** Vero quando il conto è finito. */
  @Volatile var finished = false
    private set
  /** Vero se il conto si è fermato al tetto delle righe o delle colonne. */
  @Volatile var truncated = false
    private set
  /** Un errore di lettura a metà: le righe contate restano. */
  @Volatile var failure: String? = null
    private set

  private var state by mutableStateOf(listOf(sheet(0, columns, final = false)))
  override val sheets: List<FluidGridSheet> get() = state

  /** Le righe contate finora. */
  val rowCount: Int get() = synchronized(lock) { completed }
  val columnCount: Int get() = synchronized(lock) { columns }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val job: Job = scope.launch { index() }

  private fun sheet(rows: Int, columns: Int, final: Boolean) = FluidGridSheet(
    name = name,
    rowCount = rows,
    columnCount = columns,
    frozenRows = if (header && rows > 0) 1 else 0,
    columnWidths = widths,
    // Un CSV con l'intestazione la tiene ferma in alto, senza le lettere delle colonne; senza, lettere e numeri.
    headers = !header,
    countFinal = final,
  )

  private fun publish(final: Boolean) {
    val (rows, cols) = synchronized(lock) { completed to columns }
    state = listOf(sheet(rows, cols, final))
  }

  /** Il conteggio: la stessa macchina a stati di [DataCsv.records], sui byte. */
  private suspend fun index() {
    val q = dialect.quote.code.toByte()
    val d = dialect.delimiter.code.toByte()
    val lf = '\n'.code.toByte()
    val cr = '\r'.code.toByte()
    var inQuotes = false
    var pendingQuote = false
    var pendingCr = false
    var fieldStart = true
    var quotedField = false
    var fields = 1
    var position = start
    var rowHasContent = false
    var sincePublish = 0
    val buffer = ByteArray(64 * 1024)
    fun endRow(nextStart: Long): Boolean {
      synchronized(lock) {
        if (completed + 1 >= offsets.size) offsets = offsets.copyOf(offsets.size * 2)
        completed++
        offsets[completed] = nextStart
        if (fields > columns) columns = minOf(fields, DataProfile.MaxCsvColumns)
        if (fields > DataProfile.MaxCsvColumns) truncated = true
      }
      fields = 1
      fieldStart = true
      quotedField = false
      rowHasContent = false
      sincePublish++
      return completed < DataProfile.MaxCsvRows
    }
    try {
      scan().use { input ->
        if (start > 0) {
          var skipped = 0L
          while (skipped < start) {
            val n = input.skip(start - skipped)
            if (n <= 0) break
            skipped += n
          }
        }
        outer@ while (true) {
          val n = input.read(buffer)
          if (n < 0) break
          for (i in 0 until n) {
            val b = buffer[i]
            val at = position + i
            if (pendingCr) {
              pendingCr = false
              if (b == lf) {
                synchronized(lock) { offsets[completed] = at + 1 }
                continue
              }
            }
            if (pendingQuote) {
              pendingQuote = false
              if (b == q) {
                rowHasContent = true
                continue // «""» dentro le virgolette: una virgoletta
              }
              inQuotes = false
            }
            if (inQuotes) {
              if (b == q) pendingQuote = true
              continue
            }
            when (b) {
              q -> {
                rowHasContent = true
                if (fieldStart && !quotedField) {
                  inQuotes = true
                  quotedField = true
                }
                fieldStart = false
              }
              d -> {
                rowHasContent = true
                fields++
                fieldStart = true
                quotedField = false
              }
              lf -> if (!endRow(at + 1)) {
                truncated = true
                break@outer
              }
              cr -> {
                if (!endRow(at + 1)) {
                  truncated = true
                  break@outer
                }
                pendingCr = true
              }
              else -> {
                rowHasContent = true
                fieldStart = false
              }
            }
          }
          position += n
          if (sincePublish >= DataProfile.CsvPublishRows) {
            sincePublish = 0
            publish(final = false)
            pace?.invoke(rowCount)
          }
          kotlin.coroutines.coroutineContext.ensureActive()
        }
      }
      // L'ultima riga senza a capo finale.
      if (!truncated && (rowHasContent || inQuotes || fields > 1)) endRow(position)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      failure = e.message ?: e.javaClass.simpleName
    }
    finished = true
    publish(final = true)
    onFinished(this)
  }

  /** Aspetta la fine del conto (per le prove e per chi vuole il numero finale). */
  suspend fun awaitIndex() {
    job.join()
  }

  override suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange): List<FluidGridCell> {
    if (sheet != 0 || rows.isEmpty() || columns.isEmpty()) return emptyList()
    val (first, last, from, to) = synchronized(lock) {
      if (rows.first >= completed) return emptyList()
      val first = rows.first.coerceAtLeast(0)
      val last = minOf(rows.last, completed - 1)
      Quad(first, last, offsets[first], offsets[last + 1])
    }
    if (to <= from) return emptyList()
    val text = withContext(Dispatchers.IO) {
      val length = (to - from).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
      val buffer = ByteBuffer.allocate(length)
      var read = 0
      while (read < length) {
        val n = channel.read(buffer, from + read)
        if (n <= 0) break
        read += n
      }
      DataText.decodeWith(buffer.array(), 0, read, charset)
    }
    val records = DataCsv.records(text, dialect, maxRecords = last - first + 1)
    val out = ArrayList<FluidGridCell>()
    records.forEachIndexed { k, record ->
      val row = first + k
      for (column in columns) {
        val value = record.getOrNull(column) ?: continue
        if (value.isEmpty()) continue
        val bold = header && row == 0
        out += FluidGridCell(row, column, value, if (bold) HeaderStyle else FluidGridStyle.Default,
          numeric = !bold && DataCsv.looksNumeric(value))
      }
    }
    return out
  }

  override fun close() {
    scope.cancel()
    try {
      channel.close()
    } catch (_: Exception) {
    }
    closeResources()
  }

  private data class Quad(val first: Int, val last: Int, val from: Long, val to: Long)

  companion object {
    private val HeaderStyle = FluidGridStyle(bold = true)
  }
}
