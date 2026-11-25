package dev.antigravity.fluidengine.viewer.office

import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerLiveDetails
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalQueries
import java.util.Locale

/**
 * Metadata for FluidViewerInfoProvider. Read it inside composition to follow progressive
 * pagination.
 */
fun FluidViewerDocument.officeInfo(): FluidViewerInfo {
  // La lista cresce da un filo in sottofondo (l'impaginazione): si legge una copia ferma, una volta sola.
  val details = details.consistentCopy()
  val presentation = file.name.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("pptx", "odp")
  val officePages = (content as? FluidViewerContent.Pages)?.source as? OfficePages
  val facts = buildList {
    add(file.name.substringAfterLast('.').uppercase(java.util.Locale.ROOT))
    when (val form = content) {
      is FluidViewerContent.Pages ->
        add("${form.source.count} pagine" + if (form.source.countFinal) "" else "…")
      is FluidViewerContent.Grid -> add("${form.source.sheets.size} fogli")
      else -> Unit
    }
    details.firstOrNull { it.label == "Parole" }?.let { add("${it.value} parole") }
  }
  val locale = Locale.getDefault(Locale.Category.FORMAT)
  val zone = ZoneId.systemDefault()
  val rows =
    details
      .groupBy { it.section ?: "Documento" }
      .map { (title, values) ->
        FluidViewerInfoSection(title, values.map { detail ->
          val value = if (title == "Documento" && detail.label in setOf("Creato", "Modificato"))
            officeDisplayDate(detail.value, locale, zone) else detail.value
          FluidViewerInfoRow(detail.label, value, copyValue = detail.value)
        })
      }
  return FluidViewerInfo(
    file.info,
    subtitle = when (content) {
      is FluidViewerContent.Grid -> type.label
      is FluidViewerContent.Pages -> if (presentation) "${type.label} · ${officePages?.count ?: 0} slide" else type.label
      else -> null
    },
    facts = facts,
    sections = rows,
    reading =
      failure?.let {
        FluidViewerInfoSection(
          "Lettura",
          listOf(FluidViewerInfoRow("Motivo", it.message.orEmpty())),
        )
      },
    searchPages = officePages?.takeUnless { presentation }?.let { it::search },
  )
}

/** An explicit offset denotes an instant. Without one, Office saved a local date or wall time. */
internal fun officeDisplayDate(raw: String, locale: Locale, zone: ZoneId): String {
  val text = raw.trim()
  return try {
    if ('T' !in text && 't' !in text) {
      LocalDate.parse(text).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    } else {
      val parsed = DateTimeFormatter.ISO_DATE_TIME.parse(text)
      val time = LocalDateTime.from(parsed)
      val offset = parsed.query(TemporalQueries.offset())
      val local = if (offset == null) time else time.atOffset(offset).atZoneSameInstant(zone).toLocalDateTime()
      local.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    }
  } catch (_: DateTimeException) {
    raw
  }
}

/**
 * Una copia ferma delle righe: quelle che l'impaginazione aggiunge in sottofondo ([FluidViewerLiveDetails]) si leggono
 * una volta sola, sempre intere; in composizione la lettura si iscrive ai cambi. F7.20: prima era una copia in
 * un'istantanea di Compose, che non bastava (vedi la classe).
 */
internal fun List<FluidViewerDetail>.consistentCopy(): List<FluidViewerDetail> =
  (this as? FluidViewerLiveDetails)?.current ?: toList()
