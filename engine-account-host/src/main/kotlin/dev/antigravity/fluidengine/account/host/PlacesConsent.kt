package dev.antigravity.fluidengine.account.host

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.account.AccountSections
import dev.antigravity.fluidengine.account.PlacesAccess

/**
 * F10.8 · La richiesta dei luoghi, nello Store (scelta B8; `b\chiavi.md` § 8): la prima volta che un'app li vuole, lo
 * Store chiede «Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?». La domanda è dello Store, come un
 * permesso di Android è del sistema: se la facesse l'app, un'app potrebbe rispondersi da sola.
 *
 * Chi chiede lo dice il sistema: la pagina dello Store si apre con `startActivityForResult` e legge
 * `Activity.callingPackage` ([callerOf]). Aperta in un altro modo, non chiede niente ([Step.Refused]).
 *
 * Dopo un «no» lo Store non chiede più: l'app vede [PlacesAccess.DENIED], e l'utente cambia idea dalla pagina
 * dell'account («Chi usa i tuoi luoghi»), che scrive con [PlacesStore.setAccess] o [PlacesStore.forget]. MyCloud
 * Gallery non arriva mai alla domanda.
 *
 * La grafica della pagina è di F10.7 (lo Store); qui c'è la logica, uguale per ogni Store.
 */
class PlacesConsent(private val places: PlacesStore, private val policy: AppPolicy) {

  sealed interface Step {
    /** Da chiedere all'utente con [question]; poi [answer]. */
    data class Ask(val packageName: String, val appName: String, val placeNames: List<String>, val write: Boolean) : Step {
      /** «Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?» */
      val question: String
        get() {
          val what = if (write) "usare e aggiungere i tuoi luoghi" else "usare i tuoi luoghi"
          val names = if (placeNames.isEmpty()) "" else " (${placeNames.joinToString(", ")})"
          return "$appName vuole $what$names. Consenti?"
        }
    }

    /** Già deciso: niente da chiedere. La pagina si chiude subito. */
    data class Decided(val access: PlacesAccess) : Step

    /** Non si chiede: chi chiama non si sa, o non è un'app che può avere i luoghi. */
    data object Refused : Step
  }

  /** Cosa fare per una richiesta di [callingPackage] (dal sistema), che vuole anche scrivere se [write]. */
  suspend fun begin(callingPackage: String?, write: Boolean): Step {
    if (callingPackage == null) return Step.Refused
    val entry = policy.entryFor(callingPackage) ?: return Step.Refused
    if (entry.places == AppPolicy.Places.NEVER) return Step.Decided(PlacesAccess.NEVER)
    return when (val access = places.accessFor(callingPackage)) {
      PlacesAccess.NOT_ASKED -> Step.Ask(callingPackage, entry.name, places.all().map { it.name }, write)
      // Chi legge già e vuole anche scrivere: si chiede di nuovo, solo per quello.
      PlacesAccess.READ -> if (write) Step.Ask(callingPackage, entry.name, places.all().map { it.name }, write = true) else Step.Decided(access)
      else -> Step.Decided(access)
    }
  }

  /** La risposta dell'utente a [ask]. Gli altri vengono avvisati (`places`) da [PlacesStore]. */
  fun answer(ask: Step.Ask, allow: Boolean): PlacesAccess {
    val access = when {
      !allow -> if (places.accessFor(ask.packageName) == PlacesAccess.READ) PlaceAccess.READ else PlaceAccess.NONE
      ask.write -> PlaceAccess.READ_WRITE
      else -> PlaceAccess.READ
    }
    places.setAccess(ask.packageName, access)
    return places.accessFor(ask.packageName)
  }

  companion object {
    /** È una richiesta dei luoghi? (`ACTION_OPEN` con la sezione [AccountSections.PLACES_REQUEST]). */
    fun isRequest(intent: Intent?): Boolean =
      intent?.action == AccountProtocol.ACTION_OPEN && intent.getStringExtra(AccountProtocol.EXTRA_SECTION) == AccountSections.PLACES_REQUEST

    /** Vuole anche scrivere? */
    fun wantsWrite(intent: Intent?): Boolean = intent?.getBooleanExtra(AccountProtocol.EXTRA_PLACES_WRITE, false) == true

    /**
     * Chi chiede, dal sistema, se ha la stessa firma dello Store; altrimenti `null`. Senza `startActivityForResult`
     * il sistema non lo dice, e la risposta è `null`.
     */
    fun callerOf(activity: Activity): String? {
      val pkg = activity.callingPackage ?: return null
      return pkg.takeIf { sameSignature(activity, it) }
    }

    private fun sameSignature(context: Context, pkg: String): Boolean =
      runCatching { context.packageManager.checkSignatures(context.packageName, pkg) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)
  }
}
