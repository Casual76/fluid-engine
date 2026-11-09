package dev.antigravity.fluidengine.sample

import dev.antigravity.fluidengine.foundation.EngineSettings

/**
 * Da dove parte la galleria quando la si lancia con degli extra (`adb shell am start ... --es gallery.page LongLists`).
 *
 * Serve a una cosa: il giro di verifica su emulatore (`tools/qa/gallery-tour.sh`) apre ogni pagina nuova, in
 * ogni combinazione della matrice, **senza toccare lo schermo** — un tocco su una coordinata che cambia a ogni
 * versione e' la prova piu' fragile che ci sia. Per questo la galleria legge gli extra solo nella build di debug
 * (nessuna scorciatoia nascosta in quella pubblicata) e li ignora quando non li riconosce.
 *
 * Gli extra sono tutti stringhe (`--es`), senza distinzione fra maiuscole e minuscole:
 *
 * | extra             | valori                                                           |
 * |-------------------|------------------------------------------------------------------|
 * | `gallery.page`    | il nome di una [GalleryPage], o `totry`                          |
 * | `gallery.theme`   | `light`, `dark`, `black`                                         |
 * | `gallery.text`    | `1.0`, `1.3`, `2.0` (anche `100`, `130`, `200`)                  |
 * | `gallery.reduced` | `true` per il movimento ridotto                                  |
 * | `gallery.lang`    | `it`, `en`                                                       |
 * | `gallery.data`    | `content`, `empty`, `loading`, `error`                           |
 *
 * Pura e senza Android attorno: la regola e' tutta qui e si prova sul computer.
 */
internal data class GalleryLaunch(
  val page: GalleryPage? = null,
  val theme: MatrixTheme? = null,
  val textScale: MatrixTextScale? = null,
  val reducedMotion: Boolean? = null,
  val language: MatrixLanguage? = null,
  val data: MatrixData? = null,
) {
  /** Niente di tutto questo e' stato chiesto: la galleria parte com'e' sempre. */
  val isEmpty: Boolean
    get() = page == null && theme == null && textScale == null && reducedMotion == null &&
      language == null && data == null

  /**
   * La pila di partenza. Una pagina si apre **sopra Chrome**, la scheda da cui si arriva a tutte le demo, cosi'
   * l'"Indietro" torna al catalogo; senza pagina la galleria parte dove parte la beta ([GalleryNav.Launch]).
   */
  fun stack(): List<GalleryEntry> = when {
    page == null -> GalleryNav.Launch
    page == GalleryPage.ToTry -> GalleryNav.Launch
    else -> listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(page))
  }

  /** Le impostazioni di partenza: quelle di [base], col tema chiesto se c'e'. */
  fun settings(base: EngineSettings = EngineSettings()): EngineSettings = theme?.applyTo(base) ?: base

  /** La matrice di partenza, di serie per gli assi che non sono stati chiesti. */
  fun matrix(): GalleryMatrix = GalleryMatrix(
    textScale = textScale ?: MatrixTextScale.Normal,
    reducedMotion = reducedMotion ?: false,
    language = language ?: MatrixLanguage.Italian,
    data = data ?: MatrixData.Content,
  )

  companion object {
    const val ExtraPage = "gallery.page"
    const val ExtraTheme = "gallery.theme"
    const val ExtraText = "gallery.text"
    const val ExtraReduced = "gallery.reduced"
    const val ExtraLanguage = "gallery.lang"
    const val ExtraData = "gallery.data"

    /** Nessuna richiesta. */
    val None = GalleryLaunch()

    /**
     * Legge gli extra con [extra] (che dice il valore di una chiave, o nulla). Un valore che non si riconosce
     * si ignora: una galleria che non parte per un refuso in un comando di prova e' peggio di una che parte di serie.
     */
    fun parse(extra: (String) -> String?): GalleryLaunch {
      fun value(key: String): String? = extra(key)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
      return GalleryLaunch(
        page = value(ExtraPage)?.let { name ->
          if (name == "totry") GalleryPage.ToTry else GalleryPage.entries.firstOrNull { it.name.lowercase() == name }
        },
        theme = value(ExtraTheme)?.let { name -> MatrixTheme.entries.firstOrNull { it.name.lowercase() == name } },
        textScale = value(ExtraText)?.let(::textScaleOf),
        reducedMotion = value(ExtraReduced)?.let { it == "true" || it == "1" || it == "yes" },
        language = value(ExtraLanguage)?.let { name ->
          when (name) {
            "it", "italiano", "italian" -> MatrixLanguage.Italian
            "en", "english", "inglese" -> MatrixLanguage.English
            else -> null
          }
        },
        data = value(ExtraData)?.let { name -> MatrixData.entries.firstOrNull { it.name.lowercase() == name } },
      )
    }

    private fun textScaleOf(text: String): MatrixTextScale? = when (text) {
      "1", "1.0", "1,0", "100" -> MatrixTextScale.Normal
      "1.3", "1,3", "130" -> MatrixTextScale.Medium
      "2", "2.0", "2,0", "200" -> MatrixTextScale.Largest
      else -> null
    }
  }
}
