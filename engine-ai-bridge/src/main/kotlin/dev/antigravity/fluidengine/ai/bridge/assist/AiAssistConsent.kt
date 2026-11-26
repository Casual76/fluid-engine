package dev.antigravity.fluidengine.ai.bridge.assist

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * Il registro delle scelte dell'utente per le app che chiedono a PampAI: il sì per **usare PampAI** da un'app (F9.1b:
 * la prima volta PampAI chiede) e il sì per **la pagina**. Sta in PampAI (l'ospite), mai nell'app: se lo tenesse l'app,
 * un'app potrebbe rispondersi da sola. PampAI lo scrive dall'Activity del consenso ([AiAssistConsent]) e dalle sue
 * impostazioni («App che usano PampAI»), passando da [AiAssistHost] perche' una revoca fermi anche le domande in corso.
 * Un «no» ([AiAssistConsentState.Denied]) si ricorda: PampAI non chiede piu'.
 */
interface AiAssistConsentStore {
  /** Il sì per usare PampAI da [packageName]. [AiAssistConsentState.Denied] e' anche «bloccata». */
  fun appConsent(packageName: String): AiAssistConsentState
  fun setAppConsent(packageName: String, consent: AiAssistConsentState)
  fun pageConsent(packageName: String): AiAssistConsentState
  fun setPageConsent(packageName: String, consent: AiAssistConsentState)

  /** Dimentica l'app: si torna a «non chiesto» per tutte e due le scelte. */
  fun forget(packageName: String)

  /** Le app di cui c'e' una scelta, per la pagina «App che usano PampAI». */
  fun known(): Set<String>
}

/** Il registro in memoria: per le prove e per un ospite che non deve ricordare. */
class InMemoryAiAssistConsentStore : AiAssistConsentStore {
  private val apps = java.util.concurrent.ConcurrentHashMap<String, AiAssistConsentState>()
  private val pages = java.util.concurrent.ConcurrentHashMap<String, AiAssistConsentState>()

  override fun appConsent(packageName: String): AiAssistConsentState = apps[packageName] ?: AiAssistConsentState.NotAsked
  override fun setAppConsent(packageName: String, consent: AiAssistConsentState) { apps[packageName] = consent }
  override fun pageConsent(packageName: String): AiAssistConsentState = pages[packageName] ?: AiAssistConsentState.NotAsked
  override fun setPageConsent(packageName: String, consent: AiAssistConsentState) { pages[packageName] = consent }
  override fun forget(packageName: String) { apps.remove(packageName); pages.remove(packageName) }
  override fun known(): Set<String> = apps.keys + pages.keys
}

/**
 * Il registro nelle `SharedPreferences` private dell'ospite (un file, [name]). Letture dalla copia in memoria, scritture
 * con `apply`. Un processo solo: l'Activity del consenso e il provider devono stare nello stesso processo dell'ospite.
 */
class SharedPreferencesAiAssistConsentStore(context: Context, name: String = "fluid-ai-assist-consents") : AiAssistConsentStore {
  private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

  override fun appConsent(packageName: String): AiAssistConsentState = AiAssistProtocol.consentOf(prefs.getString(APP + packageName, null))
  override fun setAppConsent(packageName: String, consent: AiAssistConsentState) { prefs.edit().putString(APP + packageName, AiAssistProtocol.consentId(consent)).apply() }
  override fun pageConsent(packageName: String): AiAssistConsentState = AiAssistProtocol.consentOf(prefs.getString(PAGE + packageName, null))
  override fun setPageConsent(packageName: String, consent: AiAssistConsentState) { prefs.edit().putString(PAGE + packageName, AiAssistProtocol.consentId(consent)).apply() }
  override fun forget(packageName: String) { prefs.edit().remove(APP + packageName).remove(PAGE + packageName).apply() }
  override fun known(): Set<String> = prefs.all.keys.mapNotNull { key ->
    when {
      key.startsWith(APP) -> key.removePrefix(APP)
      key.startsWith(PAGE) -> key.removePrefix(PAGE)
      else -> null
    }
  }.toSet()

  private companion object {
    const val APP = "app:"
    const val PAGE = "page:"
  }
}

/** Cio' che l'utente ha scelto nel foglio di PampAI, come lo legge l'app ([AiAssistConsent.resultOf]). */
data class AiAssistConsentAnswer(val app: AiAssistConsentState, val page: AiAssistConsentState)

/**
 * Le domande del consenso, nell'Activity di PampAI (come i luoghi dell'Account Pampa, F10.8). La domanda e' di PampAI,
 * come un permesso di Android e' del sistema. Sono due, e stanno **in un foglio solo** quando servono tutte e due (F9.1b,
 * scelta di Alessio):
 *
 * - **la prima volta che un'app usa PampAI**: «Fluid Weather vuole usare PampAI: quando le chiedi qualcosa da Fluid
 *   Weather, la domanda va a PampAI e al tuo fornitore di IA, con la tua chiave. Consenti?»;
 * - **la pagina**: «ClasseViva vuole mandare a PampAI la pagina che hai davanti quando le chiedi qualcosa…»;
 * - **tutte e due** (la prima domanda porta anche la pagina): una domanda sola, coi tasti «Consenti», «Senza la pagina»
 *   e «Non consentire» ([Step.Ask.choices]).
 *
 * Chi chiede lo dice il sistema: l'app apre l'Activity con `startActivityForResult` (`AiAssistClient.consentIntent`) e
 * PampAI legge `Activity.callingPackage` con la firma ([callerOf]). Aperta in un altro modo, non chiede niente
 * ([Step.Refused]). **Dopo un «no» non chiede piu'**: un'app rifiutata riceve `not_authorized`, e l'utente cambia idea
 * da «App che usano PampAI». La grafica e' di PampAI; qui c'e' la logica.
 */
class AiAssistConsent(private val host: AiAssistHost) {

  /** Un tasto del foglio: cosa vuol dire per l'app e per la pagina. */
  data class Choice(val label: String, val allowApp: Boolean, val allowPage: Boolean)

  sealed interface Step {
    /** Da chiedere all'utente con [question] e i tasti [choices]; poi [answer]. */
    data class Ask(val packageName: String, val appName: String, val askApp: Boolean, val askPage: Boolean) : Step {
      val question: String
        get() = when {
          askApp && askPage -> "$appName vuole usare PampAI e mandarle la pagina che hai davanti quando le chiedi qualcosa. " +
            "Le domande e la pagina vanno al tuo fornitore di IA, con la tua chiave. Consenti?"
          askApp -> "$appName vuole usare PampAI: quando le chiedi qualcosa da $appName, la domanda va a PampAI e al tuo " +
            "fornitore di IA, con la tua chiave. Consenti?"
          else -> "$appName vuole mandare a PampAI la pagina che hai davanti quando le chiedi qualcosa. Il testo va al tuo fornitore di IA. Consenti?"
        }

      /** I tasti, il primo e' quello principale. */
      val choices: List<Choice>
        get() = if (askApp && askPage) {
          listOf(Choice("Consenti", true, true), Choice("Senza la pagina", true, false), Choice("Non consentire", false, false))
        } else {
          listOf(Choice("Consenti", true, true), Choice("Non consentire", false, false))
        }
    }

    /** Gia' deciso: niente da chiedere. */
    data class Decided(val app: AiAssistConsentState, val page: AiAssistConsentState) : Step

    /** Non si chiede: chi chiama non si sa o non e' in tabella. */
    data object Refused : Step
  }

  /**
   * Cosa fare per una richiesta di [callingPackage] (dal sistema) che vuole anche la pagina se [withPage]. Un'app gia'
   * rifiutata non viene richiesta ([Step.Decided] col «no»).
   */
  fun begin(callingPackage: String?, withPage: Boolean = false): Step {
    callingPackage ?: return Step.Refused
    val entry = host.policy.entryFor(callingPackage) ?: return Step.Refused
    val app = host.consents.appConsent(callingPackage)
    val page = host.consents.pageConsent(callingPackage)
    if (app == AiAssistConsentState.Denied) return Step.Decided(app, page)
    val askApp = app == AiAssistConsentState.NotAsked
    val askPage = withPage && page == AiAssistConsentState.NotAsked && AiAssistPolicy.ScopePage in entry.scopes
    return if (askApp || askPage) Step.Ask(callingPackage, entry.name, askApp, askPage) else Step.Decided(app, page)
  }

  /**
   * La risposta dell'utente a [ask] con [choice]. Con «Non consentire» la prima volta, l'app e' rifiutata e la pagina
   * resta da chiedere (non serve piu'); con «Senza la pagina» l'app puo' chiedere e la pagina e' negata.
   */
  fun answer(ask: Step.Ask, choice: Choice): Step.Decided {
    if (ask.askApp) host.setAppConsent(ask.packageName, if (choice.allowApp) AiAssistConsentState.Allowed else AiAssistConsentState.Denied)
    val appAllowed = host.consents.appConsent(ask.packageName) == AiAssistConsentState.Allowed
    if (ask.askPage && appAllowed) host.setPageConsent(ask.packageName, if (choice.allowPage) AiAssistConsentState.Allowed else AiAssistConsentState.Denied)
    return Step.Decided(host.consents.appConsent(ask.packageName), host.consents.pageConsent(ask.packageName))
  }

  companion object {
    /** L'azione dell'Activity del consenso di PampAI. */
    const val ACTION = "dev.antigravity.fluidengine.ai.action.ASSIST_CONSENT"

    /** L'extra della richiesta: l'app vuole anche mandare la pagina (un foglio solo per le due domande). */
    const val EXTRA_WITH_PAGE = "dev.antigravity.fluidengine.ai.extra.ASSIST_WITH_PAGE"

    /** L'extra del risultato per la pagina: `allowed`, `denied` o `not_asked`. */
    const val EXTRA_CONSENT = "dev.antigravity.fluidengine.ai.extra.ASSIST_CONSENT"

    /** L'extra del risultato per l'app (F9.1b). */
    const val EXTRA_APP_CONSENT = "dev.antigravity.fluidengine.ai.extra.ASSIST_APP_CONSENT"

    /** E' una richiesta di consenso? */
    fun isRequest(intent: Intent?): Boolean = intent?.action == ACTION

    /** La richiesta vuole anche la pagina? */
    fun wantsPage(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_WITH_PAGE, false) == true

    /**
     * Chi chiede, dal sistema, se ha la stessa firma dell'ospite; altrimenti null. Senza `startActivityForResult` il
     * sistema non lo dice, e la risposta e' null.
     */
    fun callerOf(activity: Activity): String? {
      val pkg = activity.callingPackage ?: return null
      // Lo stesso pacchetto (un ospite di prova dentro l'app) e' sempre se stesso.
      return pkg.takeIf {
        it == activity.packageName ||
          runCatching { activity.packageManager.checkSignatures(activity.packageName, it) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)
      }
    }

    /** Il risultato da restituire all'app ([Activity.setResult] con `RESULT_OK`). */
    fun resultIntent(decided: Step.Decided): Intent = Intent()
      .putExtra(EXTRA_APP_CONSENT, AiAssistProtocol.consentId(decided.app))
      .putExtra(EXTRA_CONSENT, AiAssistProtocol.consentId(decided.page))

    /**
     * La scelta letta dall'app, dal risultato dell'Activity di PampAI (aperta con `AiAssistClient.consentIntent()` e
     * `ActivityResultContracts.StartActivityForResult`): null se PampAI non c'e' o l'utente e' uscito senza scegliere.
     */
    fun resultOf(resultCode: Int, intent: Intent?): AiAssistConsentAnswer? {
      if (resultCode != Activity.RESULT_OK || intent == null) return null
      val page = intent.getStringExtra(EXTRA_CONSENT) ?: return null
      val app = intent.getStringExtra(EXTRA_APP_CONSENT)?.let { AiAssistProtocol.consentOf(it) } ?: AiAssistConsentState.Allowed
      return AiAssistConsentAnswer(app, AiAssistProtocol.consentOf(page))
    }
  }
}
