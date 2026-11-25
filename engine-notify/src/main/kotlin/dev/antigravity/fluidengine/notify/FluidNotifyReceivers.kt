package dev.antigravity.fluidengine.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

// F8.19 · I ricevitori dell'engine. Non esportati, tranne quello del ripristino, che riceve gli annunci del sistema
// (riavvio, aggiornamento dell'app, cambio d'ora e di fuso, permesso degli allarmi esatti) ed è spento finché non serve.

/** I tocchi sulle notifiche, i promemoria che scadono e il riepilogo. */
class FluidNotifyReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val notify = FluidNotify.get(context)
    val id = intent.getStringExtra(ExtraId)
    val key = intent.getStringExtra(ExtraKey)
    when (intent.action) {
      ActionReminder -> if (id != null) notify.onMain { notify.reminders.deliver(id, scheduledHint = null, afterRestore = false) }
      ActionDigest -> notify.deliverDigest()
      ActionMessageAction -> if (id != null && key != null) notify.onMain { notify.onMessageAction(id, key) }
      ActionReply -> if (id != null) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(FluidNotify.ReplyKey)?.toString()
        if (!text.isNullOrBlank()) notify.onMain { notify.onReply(id, text) }
      }
      ActionReminderAction -> if (id != null && key != null) {
        notify.dispatch(FluidNotifyEvent.ReminderAction(id, key))
        notify.cancelReminderNotification(id)
      }
      ActionLiveAction -> if (id != null && key != null) {
        val session = intent.getLongExtra(ExtraSession, 0L)
        notify.onMain { notify.liveUpdates.onAction(id, key, session) }
      }
      ActionLiveDismissed -> if (id != null) notify.onMain { notify.liveUpdates.onDismissed(id) }
    }
  }

  internal companion object {
    const val ActionReminder = "dev.antigravity.fluidengine.notify.REMINDER"
    const val ActionDigest = "dev.antigravity.fluidengine.notify.DIGEST"
    const val ActionMessageAction = "dev.antigravity.fluidengine.notify.MESSAGE_ACTION"
    const val ActionReply = "dev.antigravity.fluidengine.notify.REPLY"
    const val ActionReminderAction = "dev.antigravity.fluidengine.notify.REMINDER_ACTION"
    const val ActionLiveAction = "dev.antigravity.fluidengine.notify.LIVE_ACTION"
    const val ActionLiveDismissed = "dev.antigravity.fluidengine.notify.LIVE_DISMISSED"
    const val ExtraId = "fluid.notify.id"
    const val ExtraKey = "fluid.notify.key"
    const val ExtraSession = "fluid.notify.session"
    const val ExtraOpenMessage = "fluid.notify.open"
  }
}

/**
 * Il ripristino: dopo un riavvio (gli allarmi di Android si cancellano allo spegnimento), un aggiornamento dell'app, un
 * cambio d'ora o di fuso e un cambio del permesso degli allarmi esatti, rimette promemoria e riepilogo. Un promemoria
 * scaduto mentre il telefono era spento arriva subito, segnato «in ritardo» nel registro.
 */
class FluidNotifyRestoreReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val notify = FluidNotify.get(context)
    val afterBoot = intent.action == Intent.ACTION_BOOT_COMPLETED
    notify.onMain {
      notify.reminders.restore(afterBoot = afterBoot)
      notify.armDigest()
      notify.center.refresh()
    }
  }
}
