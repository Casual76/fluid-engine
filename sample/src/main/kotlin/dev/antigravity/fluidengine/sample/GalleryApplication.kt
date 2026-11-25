package dev.antigravity.fluidengine.sample

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import dev.antigravity.fluidengine.notify.FluidNotify
import dev.antigravity.fluidengine.transfer.FluidTransferHost
import dev.antigravity.fluidengine.transfer.FluidTransfers
import dev.antigravity.fluidengine.transfer.LiveTransferNotifications

/**
 * L'`Application` della galleria (F8.20): fa partire WorkManager **su richiesta** ([Configuration.Provider]). L'avvio
 * automatico di WorkManager resta tolto dal manifesto (F8.21), cosi' il primo fotogramma della galleria non cambia; ma un
 * lavoro in sottofondo che riparte in un processo nuovo (un trasferimento della scena «Centro trasferimenti» con l'app
 * chiusa a meta') trova WorkManager pronto.
 *
 * F8.20b: e' anche il [FluidTransferHost] della coda dei trasferimenti, che mostra fuori dall'app la notifica viva di
 * `engine-notify` (un Live Update su Android 16) invece di quella semplice. La coda nasce solo quando qualcuno la chiede
 * (la scena o il lavoro in sottofondo): all'avvio non succede niente.
 */
class GalleryApplication : Application(), Configuration.Provider, FluidTransferHost {
  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setMinimumLoggingLevel(Log.WARN).build()

  override val fluidTransfers: FluidTransfers by lazy {
    val live = FluidNotify.get(this).liveUpdates
    FluidTransfers.open(this, notifications = LiveTransferNotifications(live, live::foreground))
  }
}
