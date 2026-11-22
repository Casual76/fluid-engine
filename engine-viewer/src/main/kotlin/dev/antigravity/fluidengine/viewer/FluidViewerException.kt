package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.Immutable
import java.io.IOException

/**
 * Perché un file non si è aperto. Non è un `enum` di proposito (regola 3.11): un motivo nuovo è una costante in più.
 * Ogni motivo porta allo stesso ripiego, lo sconosciuto con «Apri con…»: il motivo serve al testo che lo spiega.
 */
@Immutable
@JvmInline
value class FluidViewerFailure private constructor(private val id: Int) {

  override fun toString(): String = when (id) {
    CorruptId -> "Corrupt"
    ProtectedId -> "Protected"
    TooLargeId -> "TooLarge"
    UnsupportedId -> "Unsupported"
    else -> "Unreadable"
  }

  companion object {
    private const val UnreadableId = 0
    private const val CorruptId = 1
    private const val ProtectedId = 2
    private const val TooLargeId = 3
    private const val UnsupportedId = 4

    /** Il file non si legge: sparito, permesso tolto, un `Uri` che non apre niente. */
    val Unreadable: FluidViewerFailure = FluidViewerFailure(UnreadableId)
    /** I byte non sono quello che il tipo promette: un PDF rotto, un'immagine troncata. */
    val Corrupt: FluidViewerFailure = FluidViewerFailure(CorruptId)
    /** Un PDF con la password: il `PdfRenderer` della piattaforma non lo apre. */
    val Protected: FluidViewerFailure = FluidViewerFailure(ProtectedId)
    /** Oltre il tetto di copia (un PDF da un `Uri` non ricercabile più grande del tetto). */
    val TooLarge: FluidViewerFailure = FluidViewerFailure(TooLargeId)
    /** La piattaforma non sa decodificare questo tipo (un HEIF sotto Android 9, un formato sconosciuto). */
    val Unsupported: FluidViewerFailure = FluidViewerFailure(UnsupportedId)
  }
}

/** Un file che il modulo non ha potuto aprire, con il motivo. */
class FluidViewerException(
  val failure: FluidViewerFailure,
  message: String,
  cause: Throwable? = null,
) : IOException(message, cause)
