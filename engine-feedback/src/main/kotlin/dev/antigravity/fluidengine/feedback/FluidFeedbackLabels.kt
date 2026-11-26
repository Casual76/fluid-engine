package dev.antigravity.fluidengine.feedback

import androidx.compose.runtime.Immutable

/**
 * Le parole di «Segnala un problema» (F8.24). In italiano di serie; [english] è il punto di partenza per un'app
 * tradotta. Le frasi con un pezzo variabile sono funzioni.
 */
@Immutable
data class FluidFeedbackLabels(
  val title: String = "Segnala un problema",
  val rowSubtitle: String = "Uno screenshot e due righe: arrivano allo sviluppatore",
  val close: String = "Chiudi",
  val whatsWrong: String = "Cosa non va?",
  val whatsWrongPlaceholder: String = "Cosa stavi facendo, cosa ti aspettavi, cosa è successo",
  val noDescription: String = "(nessuna descrizione)",
  val untitled: String = "Segnalazione",
  val screenshot: String = "Screenshot",
  val capturePage: String = "Cattura una pagina",
  val pickPhoto: String = "Scegli dalle foto",
  val markAgain: String = "Segna",
  val removeScreenshot: String = "Togli",
  val screenshotDescription: String = "Lo screenshot della segnalazione",
  val screenshotWarning: String = "Lo screenshot può mostrare dati tuoi: coprili col pennarello prima di mandarlo.",
  val captureHint: String = "Vai alla pagina col problema, poi tocca qui",
  val captureAction: String = "Cattura",
  val captureCancel: String = "Annulla",
  val markTitle: String = "Segna il problema",
  val sends: String = "Cosa parte",
  val sendsScreenshot: String = "Lo screenshot",
  val sendsDevice: String = "L'app e il telefono",
  val sendsExperiments: (Int) -> String = { n -> if (n == 1) "1 funzione in prova accesa" else "$n funzioni in prova accese" },
  val sendsCrashes: (Int) -> String = { n -> if (n == 1) "L'ultimo arresto dell'app" else "Gli ultimi $n arresti dell'app" },
  val sendsLog: (Int) -> String = { n -> "Gli ultimi $n eventi, senza dati personali" },
  val showWhatSends: String = "Mostra tutto quello che parte",
  val hideWhatSends: String = "Nascondi",
  val credit: String = "Il tuo nome per i ringraziamenti",
  val creditPlaceholder: String = "Facoltativo: un nome o @utente di GitHub",
  val sendFile: String = "Manda un file",
  val sendFileDetail: String = "Un file ZIP con tutto, da mandare su WhatsApp o dove vuoi",
  val openIssue: String = "Apri una issue su GitHub",
  val openIssueDetail: String = "Pubblica, col tuo nome utente: ti dà i crediti della segnalazione",
  val issueFootnote: String = "Su GitHub la segnalazione è pubblica. Si apre la pagina già scritta: la mandi tu, dal tuo account.",
  val issueScreenshotSaved: (String) -> String = { name -> "Lo screenshot è in Download ($name): allegalo nella pagina della issue." },
  val preparing: String = "Preparo il file…",
  val failed: String = "Non sono riuscito a preparare il file",
  val thanks: String = "Grazie della segnalazione!",
  val where: String = "Dove",
  val experiments: String = "Funzioni in prova accese",
  val crashes: String = "Ultimi arresti",
  val log: String = "Ultimi eventi",
  val logSummary: (Int) -> String = { n -> "Ultimi eventi ($n)" },
  val issueScreenshotHint: (String) -> String = { name ->
    "Allegalo qui sotto: è nella cartella Download del telefono (`$name`)."
  },
  val issueCredit: (credit: String, appName: String) -> String = { credit, appName ->
    if (credit.isBlank()) "Segnalato con «Segnala un problema» di $appName. Grazie!"
    else "Segnalato da $credit con «Segnala un problema» di $appName. Grazie!"
  },
  val fileTitle: String = "Segnalazione",
  val fileCredit: (String) -> String = { credit -> "Segnalato da $credit" },
  val rowApp: String = "App",
  val rowEngine: String = "Fluid Engine",
  val rowDevice: String = "Telefono",
  val rowAndroid: String = "Android",
  val rowLanguage: String = "Lingua",
  val rowScreen: String = "Schermo",
  val rowAppearance: String = "Aspetto",
  val whatsapp: String = "WhatsApp",
  /** F8.24b: le righe di `LEGGIMI.txt` che dicono cosa c'è negli altri file dello ZIP. */
  val readmeContents: String = "In questo file",
  val readmeJson: (String) -> String = { name -> "- $name: gli stessi dati, per i programmi" },
  val readmeScreenshot: (String) -> String = { name -> "- $name: lo screenshot segnato" },
  val readmeLog: (name: String, lines: Int) -> String = { name, lines ->
    if (lines == 1) "- $name: l'ultimo evento dell'app" else "- $name: gli ultimi $lines eventi dell'app"
  },
  val readmePrivacy: String = "I dati personali (mail, telefoni, chiavi, indirizzi, codici) sono stati tolti sul telefono, " +
    "prima che il file partisse.",
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidFeedbackLabels = FluidFeedbackLabels(
      title = "Report a problem",
      rowSubtitle = "A screenshot and a couple of lines, straight to the developer",
      close = "Close",
      whatsWrong = "What's wrong?",
      whatsWrongPlaceholder = "What you were doing, what you expected, what happened",
      noDescription = "(no description)",
      untitled = "Report",
      screenshot = "Screenshot",
      capturePage = "Capture a page",
      pickPhoto = "Choose from photos",
      markAgain = "Mark up",
      removeScreenshot = "Remove",
      screenshotDescription = "The report's screenshot",
      screenshotWarning = "The screenshot may show your data: cover it with the marker before sending.",
      captureHint = "Go to the page with the problem, then tap here",
      captureAction = "Capture",
      captureCancel = "Cancel",
      markTitle = "Mark the problem",
      sends = "What is sent",
      sendsScreenshot = "The screenshot",
      sendsDevice = "The app and the phone",
      sendsExperiments = { n -> if (n == 1) "1 feature being tested" else "$n features being tested" },
      sendsCrashes = { n -> if (n == 1) "The last crash" else "The last $n crashes" },
      sendsLog = { n -> "The last $n events, without personal data" },
      showWhatSends = "Show everything that is sent",
      hideWhatSends = "Hide",
      credit = "Your name for the credits",
      creditPlaceholder = "Optional: a name or GitHub @username",
      sendFile = "Send a file",
      sendFileDetail = "A ZIP file with everything, for WhatsApp or anywhere",
      openIssue = "Open a GitHub issue",
      openIssueDetail = "Public, with your username: you get the credit",
      issueFootnote = "On GitHub the report is public. The page opens already written: you send it, from your account.",
      issueScreenshotSaved = { name -> "The screenshot is in Downloads ($name): attach it on the issue page." },
      preparing = "Preparing the file…",
      failed = "Couldn't prepare the file",
      thanks = "Thanks for the report!",
      where = "Where",
      experiments = "Features being tested",
      crashes = "Recent crashes",
      log = "Recent events",
      logSummary = { n -> "Recent events ($n)" },
      issueScreenshotHint = { name -> "Attach it below: it's in the phone's Downloads folder (`$name`)." },
      issueCredit = { credit, appName ->
        if (credit.isBlank()) "Reported with $appName's “Report a problem”. Thanks!"
        else "Reported by $credit with $appName's “Report a problem”. Thanks!"
      },
      fileTitle = "Problem report",
      fileCredit = { credit -> "Reported by $credit" },
      rowApp = "App",
      rowEngine = "Fluid Engine",
      rowDevice = "Phone",
      rowAndroid = "Android",
      rowLanguage = "Language",
      rowScreen = "Screen",
      rowAppearance = "Appearance",
      whatsapp = "WhatsApp",
      readmeContents = "In this file",
      readmeJson = { name -> "- $name: the same data, for programs" },
      readmeScreenshot = { name -> "- $name: the marked-up screenshot" },
      readmeLog = { name, lines -> if (lines == 1) "- $name: the app's last event" else "- $name: the app's last $lines events" },
      readmePrivacy = "Personal data (emails, phone numbers, keys, addresses, codes) was removed on the phone, before the file left.",
    )
  }
}
