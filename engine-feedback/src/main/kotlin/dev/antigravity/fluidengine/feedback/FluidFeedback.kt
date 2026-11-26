package dev.antigravity.fluidengine.feedback

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.diagnostics.FluidDiagnostics
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsKind
import dev.antigravity.fluidengine.diagnostics.FluidLog
import dev.antigravity.fluidengine.diagnostics.FluidPrivacyScrubber
import dev.antigravity.fluidengine.foundation.lab.FluidLab
import java.io.File
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// F8.24 · «Segnala un problema»: la parte senza interfaccia. Cosa si raccoglie (l'app, il telefono, le funzioni in prova,
// gli ultimi crash, gli ultimi eventi del registro coi dati personali tolti), il testo che parte e l'indirizzo della issue
// precompilata. Tutto qui è puro o quasi, e si prova sul computer: la regola della scheda è che parte solo quello che
// l'utente ha visto.

/**
 * Dove si apre una issue su GitHub: `https://github.com/[owner]/[repo]/issues/new`. [template] è il nome di un modello
 * della repo (`.github/ISSUE_TEMPLATE/segnalazione.md`): un modello mette da sé le etichette anche a chi non può
 * metterle, mentre [labels] nell'indirizzo valgono solo per chi può già etichettare la repo (documentazione di GitHub,
 * «Creating an issue from a URL query»).
 */
@Immutable
class FluidFeedbackGitHub(
  val owner: String,
  val repo: String,
  val template: String? = null,
  val labels: List<String> = emptyList(),
) {
  init {
    require(NamePattern.matches(owner) && NamePattern.matches(repo)) { "Repo GitHub non valida: $owner/$repo" }
  }

  /** La pagina della repo. */
  val repoUrl: String get() = "https://github.com/$owner/$repo"

  private companion object {
    val NamePattern = Regex("^[A-Za-z0-9_.-]{1,100}$")
  }
}

/**
 * Come si comporta «Segnala un problema» in un'app.
 *
 * @param appName il nome dell'app nel foglio e nel file.
 * @param github la repo dove aprire la issue; nulla per non offrire GitHub (resta il file).
 * @param logLines quanti eventi del registro si allegano al massimo (gli ultimi).
 * @param crashes quanti crash e chiusure recenti si allegano al massimo, presi dalla coda della diagnostica.
 */
@Immutable
class FluidFeedbackConfig(
  val appName: String,
  val github: FluidFeedbackGitHub? = null,
  val logLines: Int = 40,
  val crashes: Int = 3,
)

/**
 * Cosa sta scrivendo l'utente: la frase, lo screenshot segnato (un file dell'app) e cosa allegare. Un valore intero:
 * il foglio lo sostituisce a ogni cambio, sul thread principale.
 *
 * @param credit come vuole essere ringraziato (un nome o `@utente` di GitHub); vuoto per nessuno.
 */
@Immutable
data class FluidFeedbackDraft(
  val description: String = "",
  val screenshot: File? = null,
  val includeScreenshot: Boolean = true,
  val includeDevice: Boolean = true,
  val includeExperiments: Boolean = true,
  val includeCrashes: Boolean = true,
  val includeLog: Boolean = true,
  val credit: String = "",
) {
  /** Vero se c'è qualcosa da mandare: una frase o uno screenshot. */
  val canSend: Boolean get() = description.isNotBlank() || (screenshot != null && includeScreenshot)

  /** Lo screenshot che parte davvero (nullo se tolto o spento). */
  val attachedScreenshot: File? get() = screenshot?.takeIf { includeScreenshot && it.exists() }
}

/**
 * Le informazioni che una segnalazione può allegare, raccolte quando il foglio si apre ([collect]). Niente di nascosto:
 * il foglio le mostra tutte, e parte solo ciò che l'utente lascia acceso.
 */
@Immutable
class FluidFeedbackInfo(
  val appName: String,
  val appId: String,
  val appVersion: String,
  val versionCode: Long,
  val buildType: String,
  val engineVersion: String,
  val device: String,
  val android: String,
  val language: String,
  val screen: String,
  val appearance: String,
  val experiments: List<String>,
  val crashes: List<String>,
  val log: List<String>,
  val createdAtMillis: Long,
) {
  /** Le righe «etichetta — valore» dell'app e del telefono, nell'ordine in cui si leggono. */
  fun deviceRows(labels: FluidFeedbackLabels): List<Pair<String, String>> = listOf(
    labels.rowApp to "$appName $appVersion ($versionCode)" + if (buildType != "release") " · $buildType" else "",
    labels.rowEngine to engineVersion,
    labels.rowDevice to device,
    labels.rowAndroid to android,
    labels.rowLanguage to language,
    labels.rowScreen to screen,
    labels.rowAppearance to appearance,
  )

  companion object {
    /**
     * Raccoglie le informazioni adesso. Legge la coda della diagnostica (se c'è): da un filo in sottofondo. Il registro
     * esce già coi dati personali tolti ([FluidLog.export]); i crash portano solo il tipo e il primo passaggio del
     * codice dell'app.
     */
    fun collect(
      context: Context,
      config: FluidFeedbackConfig,
      lab: FluidLab? = null,
      diagnostics: FluidDiagnostics? = FluidDiagnostics.get(),
      now: Long = System.currentTimeMillis(),
      locale: Locale = Locale.getDefault(),
    ): FluidFeedbackInfo {
      val app = context.applicationContext
      val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
      val code = if (info == null) 0L else if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
      val debuggable = (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
      val configuration = app.resources.configuration
      val metrics = app.resources.displayMetrics
      val dark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
      val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
      val crashes = diagnostics?.queue?.records().orEmpty()
        .filter { it.kind == FluidDiagnosticsKind.Crash || it.kind == FluidDiagnosticsKind.Exit }
        .takeLast(config.crashes.coerceAtLeast(0))
        .map { record ->
          val what = record.crash?.let { crash ->
            val where = crash.frames.firstOrNull { f -> !f.startsWith("java.") && !f.startsWith("android.") && !f.startsWith("kotlin.") }
            crash.type.substringAfterLast('.') + (where?.let { " · $it" } ?: "")
          } ?: record.exit?.let { exit -> exit.reason + (exit.frames.firstOrNull()?.let { " · $it" } ?: "") } ?: record.kind.wire
          "${format.format(Date(record.hourMillis))} · $what"
        }
      return FluidFeedbackInfo(
        appName = config.appName,
        appId = app.packageName,
        appVersion = info?.versionName.orEmpty(),
        versionCode = code,
        buildType = if (debuggable) "debug" else "release",
        engineVersion = dev.antigravity.fluidengine.foundation.EngineBuild.VERSION,
        device = listOf(Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(locale) }, Build.MODEL.orEmpty())
          .filter { it.isNotBlank() }.joinToString(" "),
        android = "${Build.VERSION.RELEASE.orEmpty()} (API ${Build.VERSION.SDK_INT})",
        language = locale.toLanguageTag(),
        screen = "${metrics.widthPixels} × ${metrics.heightPixels} px · ${metrics.densityDpi} dpi",
        appearance = (if (dark) "scuro" else "chiaro") + " · testo ${Math.round(configuration.fontScale * 100)}%",
        experiments = lab?.enabledExperiments()?.map { it.title }.orEmpty(),
        crashes = crashes.map(FluidPrivacyScrubber::scrub),
        log = FluidLog.export(config.logLines),
        createdAtMillis = now,
      )
    }
  }
}

/**
 * Il testo di una segnalazione (F8.24): lo stesso per il file e per la issue, così quello che si vede nel foglio è
 * quello che parte. Le sezioni spente non ci sono.
 */
object FluidFeedbackText {
  /** La lunghezza massima dell'indirizzo della issue: GitHub rifiuta gli indirizzi troppo lunghi (a occhio, 8 kB). */
  const val MaxIssueUrlLength: Int = 7_500

  /** Il titolo della issue: la prima riga della frase, al massimo 80 caratteri, dopo l'app e la versione. */
  fun title(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, labels: FluidFeedbackLabels = FluidFeedbackLabels()): String {
    val first = FluidPrivacyScrubber.scrub(draft.description.trim().lineSequence().firstOrNull().orEmpty())
    val summary = if (first.length > 80) first.take(79).trimEnd() + "…" else first
    return "[${info.appName} ${info.appVersion}] " + summary.ifBlank { labels.untitled }
  }

  /**
   * Il corpo della issue in Markdown: «Cosa non va», l'app e il telefono in una tabella, le funzioni in prova, gli
   * ultimi crash, gli ultimi [logLimit] eventi in un blocco richiudibile, dove allegare lo screenshot ([screenshotName])
   * e i ringraziamenti. La frase dell'utente passa dal filtro dei dati personali: la issue è pubblica.
   */
  fun issueBody(
    draft: FluidFeedbackDraft,
    info: FluidFeedbackInfo,
    labels: FluidFeedbackLabels = FluidFeedbackLabels(),
    screenshotName: String? = null,
    logLimit: Int = info.log.size,
    crashLimit: Int = info.crashes.size,
  ): String = buildString {
    appendLine("### ${labels.whatsWrong}")
    appendLine(FluidPrivacyScrubber.scrub(draft.description.trim()).ifBlank { labels.noDescription })
    if (draft.includeDevice) {
      appendLine()
      appendLine("### ${labels.where}")
      appendLine("| | |")
      appendLine("|---|---|")
      info.deviceRows(labels).forEach { (label, value) -> appendLine("| $label | ${value.replace("|", "/")} |") }
    }
    if (draft.includeExperiments && info.experiments.isNotEmpty()) {
      appendLine()
      appendLine("### ${labels.experiments}")
      info.experiments.forEach { appendLine("- $it") }
    }
    if (draft.includeCrashes && info.crashes.isNotEmpty() && crashLimit > 0) {
      appendLine()
      appendLine("### ${labels.crashes}")
      info.crashes.takeLast(crashLimit).forEach { appendLine("- `$it`") }
    }
    if (draft.attachedScreenshot != null && screenshotName != null) {
      appendLine()
      appendLine("### ${labels.screenshot}")
      appendLine(labels.issueScreenshotHint(screenshotName))
    }
    if (draft.includeLog && info.log.isNotEmpty() && logLimit > 0) {
      val lines = info.log.takeLast(logLimit)
      appendLine()
      appendLine("<details><summary>${labels.logSummary(lines.size)}</summary>")
      appendLine()
      appendLine("```")
      lines.forEach { appendLine(it.replace("```", "'''")) }
      appendLine("```")
      appendLine("</details>")
    }
    appendLine()
    appendLine("---")
    append(labels.issueCredit(FluidPrivacyScrubber.scrub(draft.credit.trim()), info.appName))
  }

  /**
   * L'indirizzo della pagina «nuova issue» di [github] con titolo e corpo già scritti. Se è troppo lungo per GitHub si
   * accorcia togliendo prima gli eventi più vecchi, poi i crash, poi la coda della frase: non si taglia mai a metà di
   * un carattere codificato.
   */
  fun issueUrl(
    draft: FluidFeedbackDraft,
    info: FluidFeedbackInfo,
    github: FluidFeedbackGitHub,
    labels: FluidFeedbackLabels = FluidFeedbackLabels(),
    screenshotName: String? = null,
    maxLength: Int = MaxIssueUrlLength,
  ): String {
    val title = title(draft, info, labels)
    var logs = info.log.size
    var crashes = info.crashes.size
    var description = draft.description
    while (true) {
      val body = issueBody(draft.copy(description = description), info, labels, screenshotName, logs, crashes)
      val url = buildIssueUrl(github, title, body)
      if (url.length <= maxLength) return url
      when {
        logs > 0 -> logs = (logs - maxOf(1, logs / 4)).coerceAtLeast(0)
        crashes > 0 -> crashes--
        description.length > 200 -> description = description.take(description.length * 3 / 4).trimEnd() + "…"
        else -> return buildIssueUrl(github, title, issueBody(draft.copy(description = description.take(200)), info, labels, null, 0, 0))
      }
    }
  }

  private fun buildIssueUrl(github: FluidFeedbackGitHub, title: String, body: String): String = buildString {
    append(github.repoUrl).append("/issues/new?")
    append("title=").append(encode(title))
    append("&body=").append(encode(body))
    github.template?.let { append("&template=").append(encode(it)) }
    if (github.labels.isNotEmpty()) append("&labels=").append(encode(github.labels.joinToString(",")))
  }

  /** La codifica dei parametri: UTF-8, lo spazio come `%20` (il `+` di URLEncoder in una issue diventa un più). */
  internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  /** Il nome di un file della segnalazione: `Segnalazione-pampa-notes-20261126-1403.zip`. */
  fun fileName(info: FluidFeedbackInfo, extension: String): String {
    val slug = info.appName.lowercase(Locale.ROOT).map { c -> if (c.isLetterOrDigit() && c.code < 128) c else '-' }
      .joinToString("").replace(Regex("-+"), "-").trim('-').ifBlank { "app" }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(info.createdAtMillis))
    return "Segnalazione-$slug-$stamp.$extension"
  }
}
