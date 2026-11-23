package dev.antigravity.fluidengine.viewer.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ui.fluid.FluidWebResponse
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import java.io.File
import java.io.InputStream

/**
 * F6C.13 · Ciò che un'app può dare alla famiglia «data». Di serie niente: la famiglia trovata con `ServiceLoader` apre
 * ogni file da sola, **senza rete** e senza leggere fuori dal file. Un'app che vuole di più installa un'istanza sua:
 *
 * ```kotlin
 * FluidViewerRegistry.install(FluidDataFamily(FluidDataSettings(
 *   resources = { source, path -> folderOf(source)?.resolve(path)?.takeIf { it.isFile }?.inputStream() },
 * )))
 * ```
 *
 * @param resources le risorse relative di un HTML o di un Markdown aperto da una cartella (`style.css`,
 *   `immagini/foto.png`): l'app sa da dove viene il file e se può leggerne la cartella. Il percorso è già pulito: niente
 *   `..` che esce, niente schema, niente host. `null`, o un `null` restituito: la risorsa resta bloccata.
 * @param remoteImages il permesso di caricare le immagini remote di un HTML o di una mail, con il **client dell'app**
 *   (senza cookie, referer né credenziali: vedi `FluidWebContent`). Con questo la pagina mostra «Carica immagini»; senza,
 *   le immagini remote restano bloccate e **nessuna richiesta parte mai**.
 * @param openLink cosa fa un tocco su un collegamento (http, https, mailto, tel). `null`: il gestore di sistema.
 * @param openFile «Apri con…» per un file dentro il file (l'allegato di una mail): l'app riceve una copia in cache.
 *   `null`: l'allegato si apre solo dentro il visore.
 * @param strings le parole delle pagine.
 */
@Immutable
data class FluidDataSettings(
  val resources: ((source: FluidViewerSource, path: String) -> InputStream?)? = null,
  val remoteImages: ((Uri) -> FluidWebResponse)? = null,
  val openLink: ((String) -> Unit)? = null,
  val openFile: ((name: String, mimeType: String?, file: File) -> Unit)? = null,
  val strings: FluidDataStrings = FluidDataStrings(),
)

/** Le parole delle pagine della famiglia «data». Come le altre `Fluid*Strings`: un'app tradotta le passa tutte. */
@Immutable
data class FluidDataStrings(
  val rendered: String = "Reso",
  val source: String = "Sorgente",
  val tree: String = "Albero",
  val text: String = "Testo",
  val find: String = "Trova",
  val showMore: (shown: Int, total: Int) -> String = { shown, total -> "Mostra altri ${minOf(DataProfile.TreePage, total - shown)} · ${DataText.count(shown)} di ${DataText.count(total)}" },
  val truncated: (nodes: Int) -> String = { "Il file continua: mostrati i primi ${DataText.count(it)} valori" },
  val invalid: String = "Il file non è valido",
  val showInText: String = "Mostra nel testo",
  val loadImages: String = "Carica immagini",
  val remoteBlocked: (count: Int) -> String = { if (it == 1) "1 immagine remota bloccata" else "$it immagini remote bloccate" },
  val contents: String = "Indice",
  val previousChapter: String = "Capitolo precedente",
  val nextChapter: String = "Capitolo successivo",
  val chapter: (current: Int, count: Int, percent: Int) -> String = { current, count, percent -> "Capitolo $current di $count · $percent%" },
  val attachments: String = "Allegati",
  val backToMessage: String = "Torna al messaggio",
  val from: String = "Da",
  val to: String = "A",
  val cc: String = "Cc",
  val date: String = "Data",
  val noSubject: String = "(senza oggetto)",
  val addToCalendar: String = "Aggiungi al calendario",
  val addToContacts: String = "Aggiungi ai contatti",
  val allDay: String = "Tutto il giorno",
  val copied: String = "Copiato",
  val empty: String = "Il file è vuoto",
  val webFailed: String = "La pagina non si può mostrare qui",
  val loading: String = "Caricamento",
  val protectedTitle: String = "Protetto da DRM",
  val openWith: String = "Apri con…",
  val share: String = "Condividi",
  /** F6C.19 · Le azioni del tipo dell'albero in «•••». */
  val more: String = "Altro",
  val expandAll: String = "Espandi tutto",
  val collapseAll: String = "Comprimi tutto",
  /**
   * F6C.20 · Le azioni del tipo delle altre pagine in «•••»: Markdown, HTML, EPUB, mail, calendario e contatti. Mai le
   * parole del menu del file («Copia», «Condividi»…), che il visore toglierebbe da «•••»: per questo «Copia il testo».
   * «Indice» del Markdown è [contents], «Allegati» della mail è [attachments].
   */
  val copyText: String = "Copia il testo",
  val textSize: String = "Dimensione del testo",
  val textSizeStep: (percent: Int) -> String = { "$it%" },
  val reload: String = "Ricarica",
  val backToStart: String = "Torna all'inizio",
  val allHeaders: String = "Mostra tutte le intestazioni",
  val fewerHeaders: String = "Mostra meno intestazioni",
  val copyEvents: String = "Copia gli eventi",
  val copyContacts: String = "Copia i contatti",
)
