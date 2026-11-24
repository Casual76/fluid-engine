package dev.antigravity.fluidengine.ui.fluid

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import dev.antigravity.fluidengine.ui.R
import dev.antigravity.fluidengine.ui.fluid.icons.ContentCopy
import dev.antigravity.fluidengine.ui.fluid.icons.Event
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Link
import dev.antigravity.fluidengine.ui.fluid.icons.Map
import dev.antigravity.fluidengine.ui.fluid.icons.PhoneAndroid
import java.time.ZoneId
import java.time.ZoneOffset

internal fun FluidDetectedValue.contextActions(
  context: Context,
  additionalActions: List<FluidContextAction>,
): List<FluidContextAction> {
  val (label, icon) = when (kind) {
    FluidDetectedTextKind.Date, FluidDetectedTextKind.Time -> R.string.fluid_detected_calendar to FluidIcons.Event
    FluidDetectedTextKind.Address -> R.string.fluid_detected_map to FluidIcons.Map
    FluidDetectedTextKind.Phone -> R.string.fluid_detected_dial to FluidIcons.PhoneAndroid
    FluidDetectedTextKind.Email -> R.string.fluid_detected_email to null
    FluidDetectedTextKind.Link -> R.string.fluid_detected_open_link to FluidIcons.Link
  }
  return buildList {
    if (kind == FluidDetectedTextKind.Address) {
      add(intentAction(context, R.string.fluid_detected_directions) { directions() })
    }
    add(FluidContextAction(context.getString(label), icon) { launchPrimary(context) })
    when (kind) {
      FluidDetectedTextKind.Date, FluidDetectedTextKind.Time ->
        add(intentAction(context, R.string.fluid_detected_show_calendar) { calendarDay() })
      FluidDetectedTextKind.Phone -> {
        add(intentAction(context, R.string.fluid_detected_sms) { Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", value, null)) })
        add(intentAction(context, R.string.fluid_detected_contact) { contact(ContactsContract.Intents.Insert.PHONE) })
      }
      FluidDetectedTextKind.Email ->
        add(intentAction(context, R.string.fluid_detected_contact) { contact(ContactsContract.Intents.Insert.EMAIL) })
      FluidDetectedTextKind.Address, FluidDetectedTextKind.Link -> Unit
    }
    addAll(additionalActions)
    val copy = if (kind == FluidDetectedTextKind.Address) R.string.fluid_detected_copy_address else R.string.fluid_detected_copy
    add(FluidContextAction(context.getString(copy), FluidIcons.ContentCopy, startsSection = true) {
      val clipboard = context.getSystemService(ClipboardManager::class.java)
      clipboard?.setPrimaryClip(ClipData.newPlainText("", text))
    })
  }
}

internal fun FluidDetectedValue.launchPrimary(context: Context) {
  val intent = when (kind) {
    FluidDetectedTextKind.Date, FluidDetectedTextKind.Time -> calendarInsert()
    FluidDetectedTextKind.Address -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(value)}"))
    FluidDetectedTextKind.Phone -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null))
    FluidDetectedTextKind.Email -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", value, null))
    FluidDetectedTextKind.Link -> Intent(Intent.ACTION_VIEW, Uri.parse(value).normalizeScheme()).addCategory(Intent.CATEGORY_BROWSABLE)
  }
  if (intent != null) context.launchDetectedIntent(intent)
}

private fun intentAction(context: Context, label: Int, intent: () -> Intent?): FluidContextAction =
  FluidContextAction(context.getString(label)) { intent()?.let(context::launchDetectedIntent) }

private fun FluidDetectedValue.calendarDay(): Intent? {
  val day = date ?: return null
  val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
  return Intent(Intent.ACTION_VIEW, ContentUris.appendId(uri, millis).build())
}

private fun FluidDetectedValue.contact(field: String): Intent =
  Intent(Intent.ACTION_INSERT_OR_EDIT)
    .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
    .putExtra(field, value)

private fun FluidDetectedValue.directions(): Intent {
  val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
    .appendQueryParameter("api", "1")
    .appendQueryParameter("destination", value)
    .build()
  return Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
}

private fun FluidDetectedValue.calendarInsert(): Intent? {
  val day = date ?: return null
  val hour = time
  val allDay = hour == null
  val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
  val start = if (hour == null) day.atStartOfDay(zone) else day.atTime(hour).atZone(zone)
  val end = if (allDay) start.plusDays(1) else start.plusHours(1)
  return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toInstant().toEpochMilli())
}

private fun Context.launchDetectedIntent(intent: Intent) {
  // LocalContext può essere un contesto dell'app o un suo wrapper, per esempio in un'anteprima.
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  try {
    startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    Toast.makeText(this, R.string.fluid_detected_no_handler, Toast.LENGTH_SHORT).show()
  } catch (_: SecurityException) {
    Toast.makeText(this, R.string.fluid_detected_no_handler, Toast.LENGTH_SHORT).show()
  }
}
