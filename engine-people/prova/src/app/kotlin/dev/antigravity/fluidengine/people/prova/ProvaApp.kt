package dev.antigravity.fluidengine.people.prova

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import dev.antigravity.fluidengine.account.AccountClientConfig
import dev.antigravity.fluidengine.account.PampaAccount
import dev.antigravity.fluidengine.account.PampaPeople
import java.io.File

/**
 * L'app di prova che riceve (come farà Pampa Notes): all'avvio dice dov'è lo «Store» di prova e registra cosa fare quando
 * arriva qualcosa. Lo Store la avvisa col broadcast esplicito `CHANGED`; `PampaPeople` rilegge dal provider firmato e qui
 * si mostra **la notifica dell'app**.
 */
class ProvaApp : Application() {
  override fun onCreate() {
    super.onCreate()
    PampaAccount.configure(AccountClientConfig(storePackage = STORE, devStorePackage = STORE, devBuild = false))
    val nm = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Condivisi con te", NotificationManager.IMPORTANCE_HIGH))
    PampaPeople.onReceived(this) { items ->
      for (item in items) {
        log(this, "arrivata «${item.title}» da ${item.from.name} (${item.kind.wire}), payload ${item.payload}")
        nm.notify(
          item.id.hashCode(),
          Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${item.from.name} ti ha condiviso una lista")
            .setContentText(item.title)
            .setAutoCancel(true)
            .build(),
        )
      }
    }
    log(this, "== avvio dell'app di prova")
  }

  companion object {
    const val STORE = "dev.antigravity.fluidengine.people.prova.negozio"
    const val CHANNEL = "condivisi"
    const val TAG = "PampaPersoneProva"

    fun log(c: Context, line: String) {
      Log.i(TAG, "app: $line")
      runCatching { File(c.filesDir, "prova.log").appendText("${System.currentTimeMillis()} $line\n") }
    }
  }
}

/** La pagina dell'app di prova: chiede il permesso delle notifiche e mostra il registro. */
class AppActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
    val text = TextView(this).apply {
      textSize = 12f
      setPadding(32, 48, 32, 32)
      text = runCatching { File(filesDir, "prova.log").readLines().takeLast(60).joinToString("\n") }.getOrDefault("")
    }
    setContentView(ScrollView(this).apply { addView(text) })
  }
}
