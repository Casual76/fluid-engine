package dev.antigravity.fluidengine.aiui.strings

import androidx.compose.runtime.Immutable

/**
 * Le parole del kit della chat (bolle, composer, azioni sotto una risposta), con i default
 * italiani. Come le altre `Fluid*Strings`: un'app tradotta le passa tutte. Le frasi dello stato
 * dell'assistente ("Ti ascolto…", "Trascrivo…") non sono qui: sono di [FluidAssistantStrings], che la
 * riga della voce del composer legge.
 */
@Immutable
data class FluidChatStrings(
  // --- il composer ------------------------------------------------------------------------------
  val composerPlaceholder: String = "Scrivi un messaggio…",
  /** Mentre l'assistente lavora si scrive e si manda lo stesso: la domanda va in coda. */
  val composerBusyPlaceholder: String = "Intanto scrivi la prossima…",
  val composerEditing: String = "Modifica e rinvia…",
  val voiceStart: String = "Parla",
  val voiceStop: String = "Smetti di ascoltare",
  val stop: String = "Ferma",
  val send: String = "Invia",
  val sendQueued: String = "Manda quando ha finito",
  /** Il tasto che zittisce la voce che sta leggendo. */
  val mute: String = "Zitta",

  // --- le bolle ---------------------------------------------------------------------------------
  /** Le etichette sopra le carte, nello stile `Card` (ClasseViva): "Tu" e "Assistente". */
  val userLabel: String = "Tu",
  val assistantLabel: String = "Assistente",
  val queued: String = "In coda: parte quando ha finito",
  val queuedRemove: String = "Togli dalla coda",
  val queuedAttachments: String = "%1\$d allegati",

  // --- sotto una risposta -----------------------------------------------------------------------
  val copy: String = "Copia",
  val copied: String = "Copiato",
  val share: String = "Condividi",
  val shareChooser: String = "Condividi la risposta",
  val retry: String = "Riprova",
  val regenerate: String = "Rigenera",
  val details: String = "Dettagli",

  // --- gli esiti di una risposta senza testo ----------------------------------------------------
  val cancelledBeforeAnswer: String = "Fermata prima della risposta.",
  /** Una risposta fermata a meta' finisce a meta' frase: senza questa riga sembra tronca per un guasto. */
  val cancelledHere: String = "Fermata qui.",
  val noAnswer: String = "Nessuna risposta: il servizio non ha scritto niente.",
  val interrupted: String = "Interrotta: l'app si è chiusa prima della risposta.",
  val failedGeneric: String = "Qualcosa è andato storto.",
) {
  companion object {
    /** Le stesse parole in inglese (3.0.4); il default dell'engine resta l'italiano. */
    fun english(): FluidChatStrings = FluidChatStrings(
      composerPlaceholder = "Write a message…",
      composerBusyPlaceholder = "Meanwhile, write the next one…",
      composerEditing = "Edit and resend…",
      voiceStart = "Speak",
      voiceStop = "Stop listening",
      stop = "Stop",
      send = "Send",
      sendQueued = "Send when it has finished",
      mute = "Mute",
      userLabel = "You",
      assistantLabel = "Assistant",
      queued = "Queued: it starts when it has finished",
      queuedRemove = "Remove from the queue",
      queuedAttachments = "%1\$d attachments",
      copy = "Copy",
      copied = "Copied",
      share = "Share",
      shareChooser = "Share the answer",
      retry = "Retry",
      regenerate = "Regenerate",
      details = "Details",
      cancelledBeforeAnswer = "Stopped before the answer.",
      cancelledHere = "Stopped here.",
      noAnswer = "No answer: the service wrote nothing.",
      interrupted = "Interrupted: the app closed before the answer.",
      failedGeneric = "Something went wrong.",
    )
  }
}
