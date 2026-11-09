package dev.antigravity.fluidengine.ui.fluid

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** A caller-resolved proposal. The engine never invents domain-specific relative times. */
@Immutable
data class FluidQuickTimeOption(val label: String, val instant: Instant, val detail: String? = null)

/** The absolute result and the zone in which it was chosen; scheduling remains the caller's job. */
@Immutable
data class FluidDateTimeSelection(val instant: Instant, val zone: ZoneId)

/**
 * Generic quick proposals and calendar, presented by the existing glass Sheet portal.
 * Keep this composable mounted while [visible] becomes false, so the host can reverse its opening.
 * The caller closes it in [onDismissRequest] or [onConfirm]; cancellation never confirms.
 *
 * A DST gap is rejected. A repeated wall time requires an explicit UTC offset choice. Bounds are
 * inclusive instants. The custom controls have minute precision; an interval containing no complete
 * minute has no custom selection (exact quick instants remain usable). Confirmation re-reads [clock], so a stale selection cannot escape validation.
 * [locale] controls week start and date formats; the system controls 12/24h unless overridden.
 * No Material picker is used: the existing Fluid rows, press feedback and sheet own the presentation.
 */
@Composable
fun FluidDateTimeSheet(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  onConfirm: (FluidDateTimeSelection) -> Unit,
  quickOptions: List<FluidQuickTimeOption> = emptyList(),
  initialInstant: Instant? = null,
  minimumInstant: Instant? = null,
  maximumInstant: Instant? = null,
  zone: ZoneId = ZoneId.systemDefault(),
  clock: Clock = Clock.systemUTC(),
  locale: Locale = LocalConfiguration.current.locales[0],
  use24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
  title: String = LocalFluidLabels.current.dateTime.title,
  message: String? = null,
) {
  require(minimumInstant == null || maximumInstant == null || maximumInstant >= minimumInstant)
  val labels = LocalFluidLabels.current.dateTime
  val density = LocalDensity.current
  val policy = LocalFluidMotionPolicy.current
  val seed = remember(visible, zone, initialInstant, minimumInstant, maximumInstant, clock) {
    FluidDateTimeMath.initialMinute(initialInstant ?: clock.instant().plusSeconds(3600), clock.instant(), minimumInstant, maximumInstant)
      ?: clock.instant()
  }
  // Save at the declaration site: the modal host keys its layers by transient entry identity.
  val state = rememberSaveable(zone.id, initialInstant, minimumInstant, maximumInstant, saver = DateTimeState.Saver) {
    DateTimeState(seed.atZone(zone).toLocalDateTime().withSecond(0).withNano(0).toString(), quickOptions.isEmpty())
  }
  var wasVisible by rememberSaveable { mutableStateOf(visible) }
  if (visible && !wasVisible) state.reset(seed.atZone(zone).toLocalDateTime().withSecond(0).withNano(0).toString(), quickOptions.isEmpty())
  wasVisible = visible
  val request = DateTimeRequest(quickOptions.toList(), initialInstant, minimumInstant, maximumInstant,
    zone, clock, locale, use24Hour, title, message, labels)
  FluidGlassModalPortal(
    item = request.takeIf { visible }, onDismissRequest = onDismissRequest,
    presentation = FluidGlassModalPresentation.Sheet, paneTitle = title,
  ) { kept ->
    // Portal content runs at the host: preserve feature-local accessibility and motion settings.
    CompositionLocalProvider(LocalDensity provides density, LocalFluidMotionPolicy provides policy) {
      DateTimeBody(kept.options, kept.initial, kept.min, kept.max, kept.zone, kept.clock,
        kept.locale, kept.use24Hour, kept.title, kept.message, kept.words, state, onDismissRequest, onConfirm)
    }
  }
}

private data class DateTimeRequest(
  val options: List<FluidQuickTimeOption>, val initial: Instant?, val min: Instant?, val max: Instant?,
  val zone: ZoneId, val clock: Clock, val locale: Locale, val use24Hour: Boolean,
  val title: String, val message: String?, val words: FluidDateTimeLabels,
)

@Composable
private fun DateTimeBody(
  options: List<FluidQuickTimeOption>, initial: Instant?, min: Instant?, max: Instant?, zone: ZoneId,
  clock: Clock, locale: Locale, use24Hour: Boolean, title: String, message: String?, words: FluidDateTimeLabels,
  state: DateTimeState, dismiss: () -> Unit, confirm: (FluidDateTimeSelection) -> Unit,
) {
  var now by remember(clock) { mutableStateOf(clock.instant()) }
  LaunchedEffect(clock) { while (true) { now = clock.instant(); delay(1000) } }
  var localText by state.local
  val local = LocalDateTime.parse(localText)
  var monthText by state.month
  val month = YearMonth.parse(monthText)
  var custom by state.custom
  var quick by state.quick
  var offsetText by state.offset
  var delivered by state.delivered
  var rejected by remember { mutableStateOf(false) }
  val offsets = zone.rules.getValidOffsets(local)
  val instant = if (custom) FluidDateTimeMath.resolve(local, zone, offsetText?.let(ZoneOffset::of))
    else quick?.let(Instant::parse)
  val valid = FluidDateTimeMath.valid(instant, now, min, max)
  fun update(value: LocalDateTime) { localText = value.toString(); offsetText = null; rejected = false }
  FluidSheetScaffold(
    title = title, message = message,
    actions = {
      FluidButton(words.confirm, {
        if (!delivered && FluidDateTimeMath.valid(instant, clock.instant(), min, max)) {
          delivered = true
          confirm(FluidDateTimeSelection(instant!!, zone))
        } else { now = clock.instant(); rejected = true }
      }, enabled = valid && !delivered, fillWidth = true, size = FluidButtonSize.Large)
      FluidButton(words.cancel, dismiss, style = FluidButtonStyle.Plain, fillWidth = true, size = FluidButtonSize.Large)
    },
  ) {
    if (options.isNotEmpty()) FluidListGroup {
      options.forEachIndexed { index, option ->
        if (index > 0) FluidListDivider()
        DateTimeChoiceRow(option.label, !custom && quick == option.instant.toString(), {
          custom = false; quick = option.instant.toString(); rejected = false
        }, subtitle = option.detail ?: option.instant.atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)),
          enabled = FluidDateTimeMath.valid(option.instant, now, min, max))
      }
      FluidListDivider()
      DateTimeChoiceRow(words.choose, custom, { custom = true })
    }
    if (custom) {
      FluidButton(words.previousMonth, { monthText = month.minusMonths(1).toString() },
        style = FluidButtonStyle.Plain, size = FluidButtonSize.Large, fillWidth = true, grip = FluidPressGrip.YieldsToScroll)
      Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() })
      FluidButton(words.nextMonth, { monthText = month.plusMonths(1).toString() },
        style = FluidButtonStyle.Plain, size = FluidButtonSize.Large, fillWidth = true, grip = FluidPressGrip.YieldsToScroll)
      // Seven true week columns. Large type expands cells and the grid scrolls horizontally rather
      // than shrinking touch targets or clipping two-digit dates on narrow phones.
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = maxOf(48.dp * LocalDensity.current.fontScale, maxWidth / 7)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
          Row {
            repeat(7) { i ->
              val day = FluidDateTimeMath.weekStart(locale).plus(i.toLong())
              Box(Modifier.width(cell).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                Text(day.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelLarge)
              }
            }
          }
          FluidDateTimeMath.cells(month, locale).chunked(7).forEach { week ->
            Row {
              week.forEach { date ->
                if (date == null) Spacer(Modifier.width(cell)) else {
                  val active = date == local.toLocalDate()
                  val available = FluidDateTimeMath.dayAvailable(date, zone, now, min, max)
                  val scheme = MaterialTheme.colorScheme
                  Box(Modifier.width(cell).heightIn(min = cell)
                    .background(if (active) scheme.primaryContainer else scheme.surfaceContainer,
                      ContinuousCornerShape(FluidRadius.Small))
                    .fluidPressable(onClick = { update(LocalDateTime.of(date, local.toLocalTime())) },
                      enabled = available, role = Role.Button, haptic = FluidHapticEvent.Tick,
                      grip = FluidPressGrip.YieldsToScroll)
                    .semantics(mergeDescendants = true) {
                      contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                      selected = active
                    }, contentAlignment = Alignment.Center) {
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyLarge,
                      color = if (active) scheme.onPrimaryContainer else scheme.onSurface)
                  }
                }
              }
            }
          }
        }
      }
      val pattern = DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hm")
      Text(local.format(DateTimeFormatter.ofPattern(pattern, locale)) + " / " + zone.id,
        style = MaterialTheme.typography.titleMedium)
      TimeAdjuster(words.hour, local.hour, 23, words,
        if (use24Hour) local.hour.toString() else local.format(DateTimeFormatter.ofPattern("h a", locale))) { update(local.withHour(it)) }
      TimeAdjuster(words.minute, local.minute, 59, words) { update(local.withMinute(it)) }
      if (offsets.isEmpty()) Text(words.gap, color = MaterialTheme.colorScheme.error)
      if (offsets.size > 1) {
        Text(words.overlap)
        FluidListGroup {
          offsets.forEach { offset ->
            DateTimeChoiceRow("UTC$offset", offsetText == offset.id, { offsetText = offset.id })
          }
        }
      }
    }
    if (rejected || (instant != null && !valid)) Text(words.invalid, color = MaterialTheme.colorScheme.error)
  }
}

@Composable
private fun TimeAdjuster(label: String, value: Int, maximum: Int, words: FluidDateTimeLabels, displayValue: String = value.toString(), change: (Int) -> Unit) {
  val haptics = rememberFluidHaptics()
  fun adjust(delta: Int): Boolean { change(Math.floorMod(value + delta, maximum + 1)); haptics.play(FluidHapticEvent.Tick); return true }
  Column(Modifier.fillMaxWidth().semantics {
    contentDescription = label
    stateDescription = displayValue
    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..maximum.toFloat(), maximum - 1)
    setProgress { requested -> change(requested.toInt().coerceIn(0, maximum)); haptics.play(FluidHapticEvent.Tick); true }
    customActions = listOf(CustomAccessibilityAction(words.increase) { adjust(1) }, CustomAccessibilityAction(words.decrease) { adjust(-1) })
  }) {
    Text("$label: $displayValue", style = MaterialTheme.typography.bodyLarge)
    Row {
      FluidButton("-", { adjust(-1) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "${words.decrease} $label" },
        style = FluidButtonStyle.Plain, haptic = null, grip = FluidPressGrip.YieldsToScroll)
      FluidButton("+", { adjust(1) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "${words.increase} $label" },
        style = FluidButtonStyle.Plain, haptic = null, grip = FluidPressGrip.YieldsToScroll)
    }
  }
}

/** Preserve the engine row family while removing its decorative choice fade at motion scale zero. */
@Composable
private fun DateTimeChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit,
  subtitle: String = "", enabled: Boolean = true) {
  if (!LocalFluidMotionPolicy.current.reducedMotion) {
    FluidChoiceRow(title, selected, onSelect, subtitle = subtitle, enabled = enabled)
  } else {
    val haptics = rememberFluidHaptics()
    FluidListRow(title, subtitle, selected = selected, disclosure = false,
      modifier = Modifier.heightIn(min = 48.dp).fluidRowPressable(
        onClick = { if (!selected) haptics.play(FluidHapticEvent.Tick); onSelect() },
        enabled = enabled, haptic = null,
      ).semantics { this.selected = selected; role = Role.RadioButton; if (!enabled) disabled() },
      badge = { if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
  }
}

/** Strings in the saver survive process recreation; java.time instances need no Android parceling. */
private class DateTimeState(localText: String, customOnly: Boolean) {
  val local = mutableStateOf(localText)
  val month = mutableStateOf(YearMonth.from(LocalDateTime.parse(localText)).toString())
  val custom = mutableStateOf(customOnly)
  val quick = mutableStateOf<String?>(null)
  val offset = mutableStateOf<String?>(null)
  val delivered = mutableStateOf(false)
  fun reset(text: String, customOnly: Boolean) {
    local.value = text; month.value = YearMonth.from(LocalDateTime.parse(text)).toString()
    custom.value = customOnly; quick.value = null; offset.value = null; delivered.value = false
  }
  companion object {
    val Saver = listSaver<DateTimeState, String>(
      save = { listOf(it.local.value, it.month.value, it.custom.value.toString(), it.quick.value.orEmpty(), it.offset.value.orEmpty(), it.delivered.value.toString()) },
      restore = { saved -> DateTimeState(saved[0], saved[2].toBoolean()).apply {
        month.value = saved[1]; quick.value = saved[3].ifEmpty { null }
        offset.value = saved[4].ifEmpty { null }; delivered.value = saved[5].toBoolean()
      } },
    )
  }
}
