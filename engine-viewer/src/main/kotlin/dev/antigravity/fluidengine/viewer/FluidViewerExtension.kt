package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.util.Log
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList

/**
 * F8.17 · Un'estensione dei visori: non apre un tipo nuovo (quello lo fa una [FluidViewerFamily]) ma **aggiunge
 * qualcosa al contenuto** di un file già aperto. Il testo nelle immagini (`engine-viewer-ocr`) aggiunge «Leggi testo» a
 * foto, schermate e PDF scansiti. Come una famiglia, si scopre da sola con `ServiceLoader` (`META-INF/services`): basta
 * la dipendenza Gradle, e [rememberFluidViewerFiles] la usa.
 *
 * ```kotlin
 * class FluidLiveTextExtension : FluidViewerExtension {
 *   override val id = "ocr"
 *   override fun decorate(context: Context, file: FluidViewerFile, content: FluidViewerContent) = …
 * }
 * ```
 */
interface FluidViewerExtension {
  /** Un nome stabile e unico, per [FluidViewerExtensions.uninstall]. */
  val id: String

  /**
   * Il contenuto di [file] con l'aggiunta, o [content] com'è se non c'entra (un testo, un video). Si chiama sul filo
   * principale, una volta per apertura e forma: per le pagine sulla sorgente che arriva (lo stesso oggetto resta), per
   * un'immagine quando è decodificata. Deve essere veloce: il lavoro vero va fatto dopo, in sottofondo.
   */
  fun decorate(context: Context, file: FluidViewerFile, content: FluidViewerContent): FluidViewerContent
}

/**
 * F8.17 · Le estensioni dei visori trovate con `ServiceLoader`, più quelle installate a mano, meno quelle tolte (la
 * scena «Senza moduli» della galleria, le prove). Un'app che apre i file da sé con [FluidViewerFile.open] chiama
 * [decorate] sul contenuto.
 */
object FluidViewerExtensions {
  private val installed = CopyOnWriteArrayList<FluidViewerExtension>()
  private val hidden = CopyOnWriteArrayList<String>()
  @Volatile private var discovered: List<FluidViewerExtension>? = null

  /** Le estensioni attive. */
  val extensions: List<FluidViewerExtension>
    get() {
      val manual = installed.toList()
      val ids = manual.map { it.id }.toSet()
      return (discover().filter { it.id !in ids } + manual).filter { it.id !in hidden }
    }

  /** Aggiunge estensioni a mano; una con l'`id` di una trovata la sostituisce. */
  fun install(vararg extension: FluidViewerExtension) {
    for (e in extension) {
      installed.removeAll { it.id == e.id }
      installed += e
      hidden.remove(e.id)
    }
  }

  /** Toglie le estensioni con questi `id`. */
  fun uninstall(vararg id: String) {
    for (i in id) {
      installed.removeAll { it.id == i }
      if (i !in hidden) hidden += i
    }
  }

  /** Rimette le estensioni trovate e tolte (dopo [uninstall]). */
  fun restore(vararg id: String) {
    hidden.removeAll(id.toSet())
  }

  /** [content] con le aggiunte di tutte le estensioni attive, nell'ordine del loro `id`. */
  fun decorate(context: Context, file: FluidViewerFile, content: FluidViewerContent): FluidViewerContent {
    var result = content
    for (e in extensions.sortedBy { it.id }) {
      result = try {
        e.decorate(context.applicationContext ?: context, file, result)
      } catch (failure: RuntimeException) {
        // Un'estensione che cade non toglie il file: resta il contenuto di serie.
        Log.w(Tag, "Estensione del visore saltata: ${e.id}", failure)
        result
      }
    }
    return result
  }

  private fun discover(): List<FluidViewerExtension> {
    discovered?.let { return it }
    synchronized(this) {
      discovered?.let { return it }
      val found = ArrayList<FluidViewerExtension>()
      // La forma che R8 riconosce: `ServiceLoader.load(X::class.java, X::class.java.classLoader)` e il suo iteratore.
      val iterator = ServiceLoader.load(FluidViewerExtension::class.java, FluidViewerExtension::class.java.classLoader).iterator()
      var errors = 0
      while (errors < 8) {
        try {
          if (!iterator.hasNext()) break
          val extension = iterator.next()
          if (found.none { it.id == extension.id }) found += extension
        } catch (e: ServiceConfigurationError) {
          errors++
          Log.w(Tag, "Estensione del visore non caricata", e)
        }
      }
      return found.toList().also { discovered = it }
    }
  }

  private const val Tag = "FluidViewerExtensions"
}
