package dev.antigravity.fluidengine.ui.fluid

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.LruCache
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Android può fare IPC per ogni finestra di testo. Due chiamate contemporanee bastano anche
// quando entrano molti paragrafi in una lista, e non occupano il dispatcher di calcolo.
private val textDetectionDispatcher = Dispatchers.IO.limitedParallelism(2)
private const val CacheCharacters = 256 * 1024
private const val CacheEntryCost = 512
private const val ClassifierWindow = 4_096
private const val ClassifierOverlap = 96

private data class DetectionKey(
  val text: String,
  val locale: Locale,
  val today: LocalDate,
  val kinds: Set<FluidDetectedTextKind>,
  val classifier: TextClassifier?,
)

private val textDetectionCache = object : LruCache<DetectionKey, List<FluidDetectedValue>>(CacheCharacters) {
  override fun sizeOf(key: DetectionKey, value: List<FluidDetectedValue>): Int =
    CacheEntryCost + key.text.length + value.sumOf { it.text.length + it.value.length + 32 }
}

internal suspend fun classifyFluidText(
  context: Context,
  text: String,
  locale: Locale,
  today: LocalDate,
  kinds: Set<FluidDetectedTextKind>,
): List<FluidDetectedValue> = withContext(textDetectionDispatcher) {
  val entityTypes = classifierKinds.filterValues { it in kinds }.keys
  // Il servizio di sistema e' fuori dal nostro controllo: disattivato o guasto, chiederlo puo' lanciare
  // (un IPC). Allora restano le sole regole locali, come per un servizio che manca (R2, difetto 55).
  var complete = true
  val classifier = if (Build.VERSION.SDK_INT >= 28 && entityTypes.isNotEmpty()) {
    platformCall({ complete = false }) { context.getSystemService(TextClassificationManager::class.java)?.textClassifier }
  } else null
  val key = DetectionKey(text, locale, today, kinds, classifier)
  textDetectionCache.get(key)?.let { return@withContext it }

  // Le regole locali hanno la precedenza sui suggerimenti del servizio di sistema.
  val localValues = detectTextRules(text, locale, today, kinds)
  if (classifier == null || Build.VERSION.SDK_INT < 28) {
    if (complete) textDetectionCache.put(key, localValues)
    return@withContext localValues
  }
  val values = DetectedRanges()
  localValues.forEach(values::add)
  val windowLength = (platformCall({ complete = false }) { classifier.maxGenerateLinksTextLength } ?: 0).coerceIn(0, ClassifierWindow)
  val step = windowLength - minOf(windowLength / 4, ClassifierOverlap)
  if (step > 0) {
    var start = 0
    while (start < text.length) {
      ensureActive()
      val end = safeBoundary(text, start + minOf(windowLength, text.length - start))
      if (end <= start) break
      val nextStart = if (end == text.length) end else safeBoundary(text, start + step).coerceAtLeast(start + 1)
      val window = text.substring(start, end)
      val request = TextLinks.Request.Builder(window)
        .setDefaultLocales(LocaleList(locale))
        .setEntityConfig(TextClassifier.EntityConfig.createWithExplicitEntityList(entityTypes))
        .build()
      // Un servizio disattivato o un modello non disponibile lascia comunque i dati locali.
      val links = platformCall({ complete = false }) { classifier.generateLinks(request).links } ?: break
      for (link in links) {
        val from = link.start
        val to = link.end
        if (from !in 0 until to || to > window.length || start + from >= nextStart || link.entityCount == 0) continue
        // Il margine destro è per le entità che iniziano nella finestra precedente. Il testo
        // di appoggio non produce duplicati e una fine tagliata non diventa un link valido.
        if (end < text.length && to == window.length) continue
        platformValue(text, start + from, start + to, link.getEntity(0), kinds)?.let(values::add)
      }
      start = nextStart
    }
  }
  val result = values.toList()
  if (complete) textDetectionCache.put(key, result)
  result
}

/**
 * Una chiamata al servizio di classificazione: se lancia (servizio disattivato, modello assente, IPC
 * rotto) torna null dopo [onFailure]. L'annullamento della coroutine passa sempre.
 */
private inline fun <T> CoroutineScope.platformCall(onFailure: () -> Unit, call: () -> T): T? = try {
  call()
} catch (e: RuntimeException) {
  ensureActive()
  if (e is kotlinx.coroutines.CancellationException) throw e
  onFailure()
  null
}

private fun safeBoundary(text: String, end: Int): Int =
  if (end < text.length && text[end].isLowSurrogate()) end - 1 else end

// TextLinks restituisce intervalli e tipi, senza data/ora normalizzata. Le date vengono risolte
// dalle regole locali: l'agenda deve ricevere un giorno calcolato, non una semplice ipotesi.
private val classifierKinds = mapOf(
  TextClassifier.TYPE_ADDRESS to FluidDetectedTextKind.Address,
  TextClassifier.TYPE_PHONE to FluidDetectedTextKind.Phone,
  TextClassifier.TYPE_EMAIL to FluidDetectedTextKind.Email,
  TextClassifier.TYPE_URL to FluidDetectedTextKind.Link,
)

private fun platformValue(text: String, start: Int, end: Int, entity: String, kinds: Set<FluidDetectedTextKind>): FluidDetectedValue? {
  val kind = classifierKinds[entity]?.takeIf { it in kinds } ?: return null
  val raw = text.substring(start, end)
  val normalized = when (kind) {
    FluidDetectedTextKind.Address -> raw.takeIf { it.any(Char::isLetter) && it.none(Char::isISOControl) }
    FluidDetectedTextKind.Phone -> normalizedPhone(text, start, end)
    FluidDetectedTextKind.Email -> raw.takeIf(::isDetectedEmail)
    FluidDetectedTextKind.Link -> normalizedWebLink(raw) ?: normalizedWebLink("https://$raw").takeIf { "://" !in raw && ':' !in raw }
    else -> null
  } ?: return null
  return FluidDetectedValue(kind, start, end, raw, normalized)
}
