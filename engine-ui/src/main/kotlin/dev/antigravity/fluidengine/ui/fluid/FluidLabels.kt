package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Le parole che l'engine dice da se', quelle che nessun parametro passava (3.0.1): i nomi per TalkBack di
 * tasti e pannelli che i componenti disegnano senza chiedere niente all'app — la X di una pagina intera,
 * l'occhio di un campo segreto, il tasto indietro della barra, il titolo di un menu di azioni, le azioni di
 * TalkBack di una notifica e dell'indice di sezione.
 *
 * **In italiano di serie**, come `FluidSearchLabels`, `FluidUpdateStrings` e `FluidAppearanceStrings`: un'app
 * in una lingua sola non scrive niente, una tradotta le passa una volta alla radice e valgono per tutto
 * l'albero, menu e pop-up compresi (gli host le leggono dove sono composti):
 *
 * ```kotlin
 * CompositionLocalProvider(LocalFluidLabels provides FluidLabels(
 *   close = stringResource(R.string.close),
 *   back = stringResource(R.string.back),
 *   …
 * )) { App() }
 * ```
 *
 * Un componente non legge le risorse da se': non sa in che lingua e' l'app, e un engine che indovinasse
 * dalla lingua del telefono metterebbe frasi inglesi in un'app italiana su un telefono inglese. Le passa
 * l'app, e le passa **tutte**: una sola lasciata di serie e' una frase italiana in una pagina inglese.
 *
 * Dove un componente ha gia' un parametro per la stessa parola (`FluidPromptAlert(cancelLabel)`,
 * `FluidSlider(contentDescription)`, `FluidSearchLabels`) vince il parametro: queste sono solo le parole
 * che prima erano scritte dentro il componente.
 */
@Immutable
data class FluidLabels(
  /** La X che chiude una pagina intera (`FluidGlassModalPresentation.FullScreen`). */
  val close: String = "Chiudi",
  /** L'occhio di `FluidTextField(secret = true)` col testo nascosto: dice cosa fara' toccandolo. */
  val showSecret: String = "Mostra il testo",
  /** L'occhio di `FluidTextField(secret = true)` col testo in chiaro. */
  val hideSecret: String = "Nascondi il testo",
  /** Il titolo per TalkBack di un menu di azioni (contestuale, del tasto, della barra) che non ne ha uno. */
  val actions: String = "Azioni",
  /** Il tasto indietro della barra (`FluidScreen`, `FluidPageChrome`, la barra dell'host, `FluidTopBar`). */
  val back: String = "Indietro",
  /** Il tocco sul titolo della barra che riporta la lista in cima. */
  val scrollToTop: String = "Torna all'inizio",
  /** Il pannello di una notifica in-app, per TalkBack. */
  val notification: String = "Notifica in-app",
  /** L'azione di TalkBack che apre una notifica (come tirarla giu'). */
  val notificationExpand: String = "Espandi",
  /** L'azione di TalkBack che congeda una notifica. */
  val notificationDismiss: String = "Chiudi",
  /** La X di una notifica aperta. */
  val notificationClose: String = "Chiudi notifica",
  /** L'indice di sezione (`FluidSectionIndex`), per TalkBack. */
  val sectionIndex: String = "Indice sezioni",
  val previousSection: String = "Sezione precedente",
  val nextSection: String = "Sezione successiva",
  /** Dove si e' nell'indice: la sezione, la sua posizione da 1 e quante sono («Marzo, 3 di 12»). */
  val sectionPosition: (label: String, position: Int, total: Int) -> String = { label, position, total ->
    "$label, $position di $total"
  },
  /** La X che nasconde un messaggio (`FluidInlineMessage(onDismiss)`). */
  val hide: String = "Nascondi",
  /** Il tasto che svuota un campo di testo (`FluidTextField`, su una riga): per TalkBack. */
  val clear: String = "Cancella",
)

/** Le parole dell'engine in scope: di serie quelle italiane. Vedi [FluidLabels]. */
val LocalFluidLabels: ProvidableCompositionLocal<FluidLabels> = staticCompositionLocalOf { FluidLabels() }
