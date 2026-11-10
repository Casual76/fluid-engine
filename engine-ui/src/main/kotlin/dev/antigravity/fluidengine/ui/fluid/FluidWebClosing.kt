package dev.antigravity.fluidengine.ui.fluid

// La chiusura di una vista di FluidWebContent: la macchina a stati, senza una riga di Android.
// Kotlin puro di proposito: attese, tempi limite e ogni esito sono provati sul JVM (FluidWebClosingTest).
// Le chiamate vere alla vista e al renderer stanno in FluidWebPlatform.kt, dietro [ClosingPlatform].

/** Cio' che [RendererClosing] chiede alla piattaforma, per una sola vista gia' staccata e ferma.
 *
 * Una sola realizzazione vera (`WebViewClosing`, che e' anche il client della vista) e una finta nelle prove
 * JVM. Ogni metodo viene chiamato sul thread principale, e [schedule] deve richiamare li'.
 */
internal interface ClosingPlatform {
  /** Lancia se il chiamante non e' sul thread principale. */
  fun requireMainThread()

  /** Prende il riferimento al processo renderer della vista. False se il provider non lo da'; non lancia. */
  fun acquireRenderer(): Boolean

  /** Diventa il client della vista: da qui nessuna richiesta passa, e la morte del renderer arriva alla
   * chiusura ([RendererClosing.rendererGone]) e mai all'app.
   */
  fun takeOver()

  /** Chiede al provider di terminare il renderer preso con [acquireRenderer]. True: richiesta accolta, la
   * conferma arrivera' con [RendererClosing.rendererGone]. False: il processo non c'e', o sta ancora partendo.
   * `null`: il provider non sa terminare un renderer. Non lancia.
   */
  fun terminate(): Boolean?

  /** Distrugge la vista e ne lascia il riferimento. Chiamato una volta sola. */
  fun destroyView()

  /** Esegue [action] fra [delayMillis] millisecondi, salvo [cancelScheduled]. */
  fun schedule(delayMillis: Long, action: () -> Unit)

  /** Toglie dalla coda tutto cio' che questa chiusura ha messo con [schedule]. */
  fun cancelScheduled()
}

/** La chiusura di una vista: termina il suo renderer, aspetta che il provider lo confermi, poi la distrugge.
 *
 * Il provider da' a ogni profilo un processo renderer suo, e quel processo sopravvive a `destroy()` finche'
 * vive l'app: senza terminarlo, ogni documento aperto in una sessione ne lascerebbe uno (decine di megabyte).
 * Si puo' terminare senza toccare nessun altro proprio perche' il profilo e' di questa sola vista: le WebView
 * dell'app stanno su altri profili, quindi su altri processi. Senza un profilo dedicato ([profile] nullo: la
 * creazione e' fallita prima) il renderer sarebbe il loro, e non si prende nemmeno.
 *
 * L'ordine conta. La chiusura diventa il client della vista ([ClosingPlatform.takeOver]), cosi' la morte del
 * renderer che ha chiesto lei viene riportata a lei ([rendererGone]) e mai all'app: il client predefinito
 * risponderebbe false e il sistema chiuderebbe l'app. La vista, gia' staccata e ferma, viene distrutta solo
 * a morte confermata: e' quella conferma a dire che nel profilo non gira piu' niente del documento, ed e' la
 * condizione perche' [ClosedProfilePool] possa pulire il profilo e darlo a un documento successivo. Un
 * documento nuovo non puo' quindi ricevere la morte del renderer di uno vecchio.
 *
 * `terminate()` risponde false in due casi che da qui non si distinguono: il processo non c'e', oppure sta
 * ancora partendo (un documento chiuso nei primi istanti, per esempio sfogliando in fretta). Nel secondo
 * caso finirebbe di partire e resterebbe orfano, quindi si riprova a intervalli crescenti ([RetryDelays]). La
 * vista si tiene solo per i primi tentativi (pochi secondi); poi viene distrutta, il profilo non si riusa
 * piu', e i tentativi continuano sul solo riferimento al processo, per meno di un minuto in tutto.
 *
 * Dove il provider non sa dare o terminare il renderer la vista viene distrutta subito, come nella 3.1.0.
 *
 * Gli esiti, tutti: la vista viene distrutta una volta sola, e il profilo (se c'e') riconsegnato una volta
 * sola a [profiles].
 *
 * | che cosa succede | vista distrutta | profilo |
 * |---|---|---|
 * | nessun profilo dedicato | subito | (nessuno) |
 * | renderer gia' morto da se', o il provider non lo da' o non lo sa terminare | subito | ritirato |
 * | terminazione accolta, morte confermata senza crash | alla conferma | riciclato |
 * | terminazione accolta, il renderer muore per un crash | alla notizia | ritirato |
 * | terminazione accolta, nessuna conferma entro [ConfirmationMillis] | allo scadere | ritirato |
 * | il renderer muore prima che la terminazione sia chiesta con successo | alla notizia | ritirato |
 * | il processo sta ancora partendo dopo [HeldAttempts] tentativi | a quel tentativo | ritirato |
 *
 * Chi possiede cosa: la chiusura possiede la vista (attraverso la piattaforma) da [start] fino a
 * `destroyView`, e il riferimento al renderer finche' restano tentativi; non possiede il profilo, che
 * riconsegna. Vive solo sul thread principale: ci arriva da [start], da [rendererGone] (che WebView chiama
 * li') e dai tentativi rimessi in coda dalla piattaforma.
 */
internal class RendererClosing(
  private val platform: ClosingPlatform,
  private val profile: String?,
  /** Vero quando il renderer e' gia' morto da se': non c'e' nulla da terminare, e il profilo non si riusa. */
  private val alreadyGone: Boolean,
  private val profiles: ProfileReturn,
) {
  private var hasRenderer = false
  private var finished = false
  /** Il provider ha accolto una richiesta di terminazione: da qui si aspetta solo la conferma. */
  private var asked = false
  /** Dentro la chiamata a `terminate()`: una morte riportata proprio adesso e' quella chiesta da qui. */
  private var asking = false
  private var attempts = 0

  fun start() {
    platform.requireMainThread()
    hasRenderer = profile != null && !alreadyGone && platform.acquireRenderer()
    platform.takeOver()
    if (!hasRenderer) finish(confirmed = false) else attempt()
  }

  /** Il provider riporta la morte del renderer della vista; [crashed] se e' morto per un crash. */
  fun rendererGone(crashed: Boolean) {
    // Il processo non c'e' piu': niente da riprovare. Conferma solo la morte chiesta da qui: un crash
    // arrivato nel frattempo non fa riusare il profilo.
    platform.cancelScheduled()
    finish(confirmed = (asked || asking) && !crashed)
  }

  private fun attempt() {
    platform.requireMainThread()
    if (!hasRenderer) return
    asking = true
    val terminated = platform.terminate()
    asking = false
    when {
      terminated == null -> finish(confirmed = false)
      terminated -> {
        asked = true
        // La conferma arriva in qualche decina di millisecondi. Se non arrivasse, la vista non resta in giro.
        if (!finished) platform.schedule(ConfirmationMillis) { finish(confirmed = false) }
      }
      else -> {
        if (attempts == HeldAttempts) finish(confirmed = false)
        if (attempts < RetryDelays.size) platform.schedule(RetryDelays[attempts++], ::attempt)
      }
    }
  }

  private fun finish(confirmed: Boolean) {
    platform.requireMainThread()
    if (finished) return
    finished = true
    // Dopo una terminazione riuscita non resta nulla da riprovare. Se invece il processo stava ancora
    // partendo, i tentativi restano in coda: non toccano piu' la vista, solo il riferimento al processo.
    if (asked) platform.cancelScheduled()
    platform.destroyView()
    if (profile != null) {
      if (confirmed) profiles.recycle(profile) else profiles.retire(profile)
    }
  }

  internal companion object {
    /** Attese fra un tentativo di terminazione e il successivo, in millisecondi. */
    val RetryDelays = longArrayOf(50, 150, 400, 1_000, 2_500, 6_000, 15_000, 30_000)
    /** Tentativi falliti dopo i quali la vista non si tiene piu' (circa quattro secondi). */
    const val HeldAttempts = 5
    const val ConfirmationMillis = 5_000L
  }
}
