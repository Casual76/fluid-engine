package dev.antigravity.fluidengine.account.prova

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import kotlin.concurrent.thread

/**
 * Quattro tasti e il registro. Ogni passo si lancia anche da adb con
 * `--es passo prepara|velocita|stato|campanello|campanello-fra [--ei secondi 180]`.
 */
class ProvaActivity : Activity() {
  private lateinit var out: TextView
  private var secondi = 180L

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    out = TextView(this).apply { textSize = 12f; setPadding(32, 32, 32, 32) }
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      for ((label, step) in listOf("1 · Prepara" to "prepara", "2 · Velocità StrongBox" to "velocita", "3 · Stato" to "stato", "4 · Campanello fra 3 minuti" to "campanello-fra")) {
        addView(Button(context).apply { text = label; setOnClickListener { run(step) } })
      }
      addView(out)
    }
    setContentView(ScrollView(this).apply { addView(column) })
    secondi = intent?.getIntExtra("secondi", 180)?.toLong() ?: 180L
    intent?.getStringExtra("passo")?.let(::run)
    refresh()
  }

  private fun run(step: String) = thread {
    when (step) {
      "prepara" -> Banco.prepara(applicationContext)
      "velocita" -> Banco.velocita(applicationContext)
      "stato" -> Banco.stato(applicationContext)
      "campanello" -> Banco.campanello(applicationContext)
      "campanello-fra" -> Banco.campanelloFra(applicationContext, secondi)
    }
    runOnUiThread { refresh() }
  }

  private fun refresh() {
    out.text = runCatching { File(filesDir, "prova.log").readLines().takeLast(60).joinToString("\n") }.getOrDefault("")
  }
}

/** Il campanello finto: lo suona adb a schermo bloccato. Il lavoro va su un filo, come farebbe lo Store vero. */
class CampanelloReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    thread {
      try {
        Banco.campanello(context.applicationContext)
      } finally {
        pending.finish()
      }
    }
  }
}
