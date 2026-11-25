package dev.antigravity.fluidengine.account.host

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.antigravity.fluidengine.account.AccountProtocol

/**
 * F10.8 · L'avviso dello Store alle app quando cambia qualcosa (`b\api.md` § 5.4): `dev.pampa.account.CHANGED`, con
 * dentro solo `what` (`appearance`, `places`, `state`, …). **Esplicito**, uno per app: si mandano solo alle app che
 * hanno il ricevitore di `engine-account` **e la firma dello Store**. Un'app con un'altra firma non sa nemmeno che
 * qualcosa è cambiato.
 *
 * L'app non si fida del contenuto e rilegge dal provider; un'app ferma dall'utente («Forza arresto») o mai aperta non lo
 * riceve (Android non sveglia i pacchetti fermi), e si rimette in pari alla prossima apertura.
 *
 * Il manifest di `engine-account-host` dichiara la `<queries>` per vedere i ricevitori.
 */
class AccountChangedBroadcaster(private val context: Context) {

  /** I pacchetti che ricevono l'avviso adesso: quelli col ricevitore e la firma dello Store, tranne lo Store. */
  fun receivers(): List<String> {
    val pm = context.packageManager
    val intent = Intent(AccountProtocol.ACTION_CHANGED)
    @Suppress("DEPRECATION")
    val found = if (Build.VERSION.SDK_INT >= 33) {
      pm.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
      pm.queryBroadcastReceivers(intent, 0)
    }
    return found.mapNotNull { it.activityInfo?.packageName }.distinct()
      .filter { it != context.packageName }
      .filter { runCatching { pm.checkSignatures(context.packageName, it) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false) }
  }

  /** Manda l'avviso [what] a ogni app ([receivers]); risponde a quante. */
  fun send(what: List<String>): Int {
    val targets = receivers()
    for (pkg in targets) {
      context.sendBroadcast(
        Intent(AccountProtocol.ACTION_CHANGED)
          .setPackage(pkg)
          .putStringArrayListExtra(AccountProtocol.EXTRA_WHAT, ArrayList(what)),
      )
    }
    return targets.size
  }
}
