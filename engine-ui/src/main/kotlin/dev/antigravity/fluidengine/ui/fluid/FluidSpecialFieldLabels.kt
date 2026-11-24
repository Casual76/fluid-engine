package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Le parole dei campi speciali (F8.3): il campo con unità, il campo a codice, il campo password, il valore riservato, la
 * copia delicata e il tasto incolla. Di serie in italiano; [English] è la versione inglese completa. Un'app in un'altra
 * lingua ne dà una sua con [LocalFluidSpecialFieldLabels].
 *
 * Sono a parte da [FluidLabels] perché i pacchetti di F8 corrono insieme: ognuno porta le sue parole senza toccare la
 * classe di tutti.
 */
@Immutable
data class FluidSpecialFieldLabels(
  // --- Il campo con unità ---
  /** Il nome del tasto dell'unità, per TalkBack: «Unità: km. Cambia unità». */
  val unitPicker: String = "Cambia unità",
  /** La riga della conversione sotto il campo: «≈ 7,77 mi». */
  val converted: (String) -> String = { "≈ $it" },

  // --- Il campo a codice ---
  /** Il nome del campo a codice, se l'app non ne dà uno. */
  val code: String = "Codice",
  /** La posizione per TalkBack: «cifra 3 di 6». */
  val codePosition: (position: Int, total: Int) -> String = { position, total -> "cifra $position di $total" },
  /** Il codice è intero. */
  val codeComplete: String = "codice completo",
  /** L'app sta verificando il codice. */
  val codeVerifying: String = "verifica in corso",
  /** Il codice non è giusto (si sente con l'aptica d'errore e si legge con un'area viva). */
  val codeWrong: String = "Codice non corretto",

  // --- Il campo password ---
  /** I nomi dei quattro livelli della forza, da debole a forte. */
  val strengthLevels: List<String> = listOf("Debole", "Discreta", "Buona", "Forte"),
  /** Come TalkBack legge la forza: «Forza stimata: buona». */
  val strengthSpoken: (String) -> String = { "Forza stimata: ${it.lowercase()}" },
  /** La nota che dice che la forza è una stima. */
  val strengthNote: String = "Stima indicativa",
  val ruleMinLength: (Int) -> String = { "Almeno $it caratteri" },
  val ruleMaxLength: (Int) -> String = { "Al massimo $it caratteri" },
  val ruleLower: String = "Una lettera minuscola",
  val ruleUpper: String = "Una lettera maiuscola",
  val ruleDigit: String = "Un numero",
  val ruleSpecial: String = "Un simbolo",
  val ruleLetter: String = "Una lettera",
  /** Uno di un insieme su misura: «Uno di questi: - ( ) . & @». */
  val ruleCustom: (String) -> String = { "Uno di questi: $it" },
  /** Più insiemi in una regola sola: «Una lettera minuscola o un numero». */
  val ruleOr: String = " o ",
  val ruleMaxConsecutive: (Int) -> String = { "Non più di $it caratteri uguali di fila" },
  val ruleOnlyAllowed: String = "Solo caratteri ammessi",
  /** Lo stato di una regola per TalkBack. */
  val ruleMet: String = "fatto",
  val ruleMissing: String = "da fare",
  /** La proposta: il titolo e il tasto. */
  val suggestedTitle: String = "Password forte",
  val useSuggested: String = "Usa password forte",
  /** Cosa dice la proposta sotto la password: dove nasce. */
  val suggestedNote: String = "Generata su questo dispositivo. Salvala nel tuo gestore di password.",
  /** Rifiuta la proposta e scrivi la tua. */
  val ownPassword: String = "Scegli la mia password",

  // --- Il valore riservato ---
  val tapToReveal: String = "Tocca per vedere",
  /** Come TalkBack legge un valore coperto. */
  val hidden: String = "Valore nascosto",
  /** L'azione di TalkBack sul valore coperto e quella sul valore scoperto. */
  val reveal: String = "Mostra",
  val hide: String = "Nascondi",

  // --- La copia delicata ---
  val copy: String = "Copia",
  val copied: String = "Copiato",
  /** Il tempo che resta prima che gli appunti si cancellino: «Si cancella fra 58 s». */
  val clearsIn: (seconds: Int) -> String = { "Si cancella fra $it s" },
  /** Cosa legge TalkBack quando gli appunti si cancellano da soli. */
  val cleared: String = "Appunti cancellati",

  // --- Il tasto incolla ---
  val pasteCode: String = "Incolla il codice",
  val pasteLink: String = "Incolla il link",
  val pasteImage: String = "Incolla l'immagine",
) {
  companion object {
    /** La versione inglese completa. */
    val English: FluidSpecialFieldLabels = FluidSpecialFieldLabels(
      unitPicker = "Change unit",
      converted = { "≈ $it" },
      code = "Code",
      codePosition = { position, total -> "digit $position of $total" },
      codeComplete = "code complete",
      codeVerifying = "verifying",
      codeWrong = "Incorrect code",
      strengthLevels = listOf("Weak", "Fair", "Good", "Strong"),
      strengthSpoken = { "Estimated strength: ${it.lowercase()}" },
      strengthNote = "Rough estimate",
      ruleMinLength = { "At least $it characters" },
      ruleMaxLength = { "At most $it characters" },
      ruleLower = "A lowercase letter",
      ruleUpper = "An uppercase letter",
      ruleDigit = "A number",
      ruleSpecial = "A symbol",
      ruleLetter = "A letter",
      ruleCustom = { "One of these: $it" },
      ruleOr = " or ",
      ruleMaxConsecutive = { "No more than $it identical characters in a row" },
      ruleOnlyAllowed = "Only allowed characters",
      ruleMet = "done",
      ruleMissing = "to do",
      suggestedTitle = "Strong Password",
      useSuggested = "Use Strong Password",
      suggestedNote = "Created on this device. Save it in your password manager.",
      ownPassword = "Choose My Own Password",
      tapToReveal = "Tap to reveal",
      hidden = "Hidden value",
      reveal = "Show",
      hide = "Hide",
      copy = "Copy",
      copied = "Copied",
      clearsIn = { "Clears in $it s" },
      cleared = "Clipboard cleared",
      pasteCode = "Paste code",
      pasteLink = "Paste link",
      pasteImage = "Paste image",
    )
  }
}

/** Le parole dei campi speciali in uso. */
val LocalFluidSpecialFieldLabels: ProvidableCompositionLocal<FluidSpecialFieldLabels> =
  staticCompositionLocalOf { FluidSpecialFieldLabels() }
