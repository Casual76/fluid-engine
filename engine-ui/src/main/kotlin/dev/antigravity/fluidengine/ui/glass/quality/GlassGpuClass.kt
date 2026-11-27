package dev.antigravity.fluidengine.ui.glass.quality

/**
 * F7.26 · **La classe della scheda grafica, dal nome del SoC.** È la prima ipotesi della fascia, mai l'unica verità:
 * un dispositivo che scatta scende lo stesso (le finestre dei fotogrammi di F7.10), e uno sconosciuto parte prudente e
 * risale se regge.
 *
 * - [High]: le GPU di punta che reggono il vetro pieno con margine. Il Tab S9 (Snapdragon 8 Gen 2, Adreno 740) e il
 *   Galaxy S25 (Snapdragon 8 Elite) di Alessio, e la loro famiglia. Pagine dei documenti fuori dalla memoria grafica.
 * - [Mid]: le punte di qualche anno fa e quelle che scaldano e rallentano: il Galaxy S22 (Exynos 2200 o Snapdragon 8
 *   Gen 1), i Pixel con Tensor. Il vetro parte Ridotto come per uno sconosciuto; le pagine vanno nella memoria grafica.
 * - [Low]: la fascia media e bassa (Snapdragon 7, 6 e 4, Exynos 1xxx e 8xx, Dimensity sotto i 9000): il Galaxy A52s
 *   (Snapdragon 778G) e le serie A. Il vetro parte Protetto e risale alla posa se i fotogrammi reggono.
 * - [Unknown]: nessun nome letto o un nome che la tabella non conosce. Il vetro parte Ridotto (come prima di F7.26) e
 *   le pagine vanno nella memoria grafica finché il vetro non arriva a Pieno con i fotogrammi misurati.
 *
 * Il segnale costa zero: `Build.SOC_MANUFACTURER` e `Build.SOC_MODEL` (Android 12 e dopo) e, se mancano o dicono
 * `unknown`, `Build.BOARD` e `Build.HARDWARE`. Sono campi statici che Android legge dalle proprietà di sistema quando
 * carica la classe `Build` (già nello zigote): nessuna chiamata al sistema, nessun contesto EGL, niente file. Si legge
 * una volta per processo ([dev.antigravity.fluidengine.ui.glass.quality.android.AndroidGlassSignals]).
 *
 * I nomi veri, con la fiducia (26 novembre 2026):
 * - Tab S9 SM-X710: `ro.soc.model` SM8550, `ro.board.platform` kalama, letti nel logcat del tablet il 25 novembre
 *   (sessione F7.18). Fiducia alta.
 * - Qualcomm: `SM` + quattro cifre. Le punte da 8 Gen 2 in poi finiscono in 50 (SM8550, SM8650, SM8750, SM8850);
 *   SM8450 è l'8 Gen 1 dell'S22 americano, SM8475 l'8+ Gen 1, le varianti «s» finiscono in 35 (SM8635, SM8735).
 *   SM7325 è il 778G dell'A52s. Fiducia alta sui numeri, media sulla regola per i modelli futuri.
 * - Samsung Exynos: `s5e` + quattro cifre. s5e9925 è l'Exynos 2200 dell'S22 europeo, s5e9945 il 2400, s5e9955 il 2500,
 *   s5e9840 il 2100, s5e9830 il 990; s5e88x5 sono le serie A (1280, 1380, 1480, 1580). Fiducia media: i nomi vengono
 *   dalle schede tecniche e dai sorgenti dei kernel Samsung, non da un dispositivo nostro.
 * - MediaTek: `MT` + quattro cifre, a volte con un suffisso (MT6983W). MT6989 è il Dimensity 9300, MT6991 il 9400,
 *   MT6983 il 9000, MT6985 il 9200. Fiducia media.
 * - Google Tensor: produttore `Google`, modelli GS101, GS201, Tensor G3 e successivi. Fiducia bassa sui nomi, alta
 *   sulla classe (GPU Mali e PowerVR sotto l'Adreno 740, e scaldano).
 * - Le sigle delle piattaforme Qualcomm in `Build.BOARD` (kalama, pineapple, sun, taro, lahaina, …) servono solo ai
 *   dispositivi aggiornati da Android 11 che non hanno `ro.soc.model`. Fiducia media; `lahaina` è sia lo Snapdragon 888
 *   sia il 778G, quindi vale [Mid].
 */
internal enum class GlassGpuClass { Unknown, Low, Mid, High }

/**
 * La tabella dei SoC noti. Pura: la prova la esercita sul JVM con i nomi veri dei dispositivi tipo. Niente espressioni
 * regolari (la prima compilazione di un `Pattern` costa decine di microsecondi sul thread che apre il processo): prefissi
 * e cifre, letti a mano.
 */
internal object GlassGpuTable {
  /** Le sigle delle piattaforme Qualcomm che si leggono in `Build.BOARD` (minuscole). */
  private val boards = mapOf(
    "kalama" to GlassGpuClass.High, "pineapple" to GlassGpuClass.High, "sun" to GlassGpuClass.High,
    "taro" to GlassGpuClass.Mid, "cape" to GlassGpuClass.Mid, "ukee" to GlassGpuClass.Mid,
    "lahaina" to GlassGpuClass.Mid, "kona" to GlassGpuClass.Mid, "msmnile" to GlassGpuClass.Mid,
    "yupik" to GlassGpuClass.Low, "lito" to GlassGpuClass.Low, "atoll" to GlassGpuClass.Low,
    "holi" to GlassGpuClass.Low, "bengal" to GlassGpuClass.Low, "trinket" to GlassGpuClass.Low,
    "parrot" to GlassGpuClass.Low, "khaje" to GlassGpuClass.Low, "blair" to GlassGpuClass.Low,
    "crow" to GlassGpuClass.Low, "sm6150" to GlassGpuClass.Low, "sdm845" to GlassGpuClass.Low,
  )

  private val qualcommOld = listOf("SDM", "SDA", "MSM", "APQ", "QCM", "QCS")
  private val tensor = listOf("GS1", "GS2", "GS3", "TENSOR", "ZUMA", "LAGUNA", "MALIBU")

  /**
   * La classe di un SoC. [manufacturer] e [model] sono `Build.SOC_MANUFACTURER` e `Build.SOC_MODEL` (null sotto
   * Android 12); [board] e [hardware] `Build.BOARD` e `Build.HARDWARE`, letti solo se il modello non decide.
   */
  fun classify(manufacturer: String?, model: String?, board: String? = null, hardware: String? = null): GlassGpuClass {
    val maker = manufacturer.known()?.uppercase()
    model.known()?.let { name -> byName(name.uppercase().replace(" ", ""), maker)?.let { return it } }
    if (maker == "GOOGLE") return GlassGpuClass.Mid
    for (other in listOf(board, hardware)) {
      val name = other.known() ?: continue
      boards[name.lowercase()]?.let { return it }
      byName(name.uppercase().replace(" ", ""), null)?.let { return it }
    }
    return GlassGpuClass.Unknown
  }

  private fun String?.known(): String? = this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }

  /** Le cifre subito dopo [prefix], se ce ne sono almeno [min]; al più quattro. */
  private fun String.number(prefix: String, min: Int = 4): Int? {
    if (!startsWith(prefix)) return null
    var end = prefix.length
    while (end < length && end - prefix.length < 4 && this[end].isDigit()) end++
    return if (end - prefix.length >= min) substring(prefix.length, end).toInt() else null
  }

  private fun byName(name: String, maker: String?): GlassGpuClass? {
    name.number("SM")?.let { return qualcommClass(it) }
    if (qualcommOld.any { name.startsWith(it) && name.length > it.length && name[it.length].isDigit() }) return GlassGpuClass.Low
    name.number("S5E")?.let { return exynosCodeClass(it) }
    name.number("EXYNOS", min = 3)?.let { return exynosNameClass(it) }
    name.number("MT")?.let { return mediatekClass(it) }
    if (maker == "GOOGLE" || tensor.any { name.startsWith(it) }) return GlassGpuClass.Mid
    return null
  }

  /** Le punte da SM8550 in poi finiscono in 50; il resto della serie 8 (8 Gen 1, le «s») è Mid, sotto è Low. */
  private fun qualcommClass(n: Int) = when {
    n in 8000..8999 && n >= 8550 && n % 100 == 50 -> GlassGpuClass.High
    n in 8000..8999 -> GlassGpuClass.Mid
    else -> GlassGpuClass.Low
  }

  /** s5e9945 (2400) e dopo High; s5e9925 (2200), s5e9840 (2100), s5e9830 (990) Mid; le serie A e il resto Low. */
  private fun exynosCodeClass(n: Int) = when {
    n in 9945..9999 -> GlassGpuClass.High
    n in 9830..9944 -> GlassGpuClass.Mid
    else -> GlassGpuClass.Low
  }

  /** Come [exynosCodeClass] per i nomi commerciali in `Build.HARDWARE` dei Samsung meno recenti (`exynos990`). */
  private fun exynosNameClass(n: Int) = when {
    n in 2400..2999 -> GlassGpuClass.High
    n in 2000..2399 || n == 990 -> GlassGpuClass.Mid
    else -> GlassGpuClass.Low
  }

  /** MT6989 (Dimensity 9300) e dopo High; MT6983 e MT6985 (9000, 9200) Mid; il resto Low. */
  private fun mediatekClass(n: Int) = when {
    n in 6989..6999 -> GlassGpuClass.High
    n in 6983..6988 -> GlassGpuClass.Mid
    else -> GlassGpuClass.Low
  }
}
