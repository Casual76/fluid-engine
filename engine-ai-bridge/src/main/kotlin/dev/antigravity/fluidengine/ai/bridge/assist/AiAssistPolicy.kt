package dev.antigravity.fluidengine.ai.bridge.assist

/**
 * La tabella delle app che possono chiedere a PampAI, con cosa possono chiedere (l'**ambito**). Sta nel codice, rivista
 * in git, come quella dell'Account Pampa (`AppPolicy` di `engine-account-host`). Il pacchetto di chi chiama lo dice il
 * sistema (l'uid di Binder), mai l'app. Le varianti di sviluppo (`.debug`, `.dev`) contano solo se l'ospite stesso e'
 * una build di sviluppo ([devBuild]).
 *
 * La tabella di serie ([DEFAULT]) e' una scelta di prodotto (domanda 2 di F9.1): le app con testo dell'utente
 * (ClasseViva, Pampa Notes, Pampa Mail) hanno tutti gli ambiti, le altre rispondono e usano i loro tool.
 */
class AiAssistPolicy(val apps: Map<String, Entry> = DEFAULT, val devBuild: Boolean = false) {

  /** Un'app della tabella: il pacchetto, il nome per le domande all'utente, gli ambiti `Scope*`. */
  data class Entry(val packageName: String, val name: String, val scopes: Set<String>)

  /** La voce di [packageName], con le varianti di sviluppo solo se [devBuild]. */
  fun entryFor(packageName: String): Entry? {
    apps[packageName]?.let { return it }
    if (!devBuild) return null
    val base = DEV_SUFFIXES.firstNotNullOfOrNull { s -> packageName.takeIf { it.endsWith(s) }?.removeSuffix(s) } ?: return null
    return apps[base]?.copy(packageName = packageName)
  }

  /** [packageName] puo' usare [scope]? */
  fun allows(packageName: String, scope: String): Boolean = entryFor(packageName)?.scopes?.contains(scope) == true

  companion object {
    /** Una domanda libera. */
    const val ScopeAnswer = "answer"

    /** Riscrivere un testo dell'utente. */
    const val ScopeRewrite = "rewrite"

    /** Riassumere un testo. */
    const val ScopeSummarize = "summarize"

    /** PampAI usa i tool dell'app che chiama. */
    const val ScopeTools = "tools"

    /** L'app puo' mandare la pagina (serve anche il sì dell'utente). */
    const val ScopePage = "page"

    /** Tutti gli ambiti. */
    val AllScopes: Set<String> = setOf(ScopeAnswer, ScopeRewrite, ScopeSummarize, ScopeTools, ScopePage)

    /** L'ambito che serve per un compito; null se il compito non e' conosciuto. */
    fun scopeFor(kind: String): String? = when (kind) {
      AiAssistTask.KindAnswer -> ScopeAnswer
      AiAssistTask.KindRewrite -> ScopeRewrite
      AiAssistTask.KindSummarize -> ScopeSummarize
      else -> null
    }

    val DEV_SUFFIXES = listOf(".debug", ".dev")

    private val Answering = setOf(ScopeAnswer, ScopeTools, ScopePage)

    /** La tabella di partenza (stessi pacchetti dell'Account Pampa). */
    val DEFAULT: Map<String, Entry> = listOf(
      Entry("dev.antigravity.classevivaexpressive", "ClasseViva Expressive", AllScopes),
      Entry("dev.antigravity.fluidtransit", "Fluid Transit", Answering),
      Entry("dev.pampa.fluidweather", "Fluid Weather", Answering),
      Entry("dev.pampa.pampanotes", "Pampa Notes", AllScopes),
      Entry("dev.pampa.mail", "Pampa Mail", AllScopes),
      Entry("dev.pampa.mycloudgallery", "MyCloud Gallery", Answering),
      Entry("com.p2r3.convert", "Universal Converter", Answering),
    ).associateBy { it.packageName }
  }
}
