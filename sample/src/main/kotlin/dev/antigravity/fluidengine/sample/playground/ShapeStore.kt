package dev.antigravity.fluidengine.sample.playground

import android.content.Context
import androidx.compose.ui.geometry.Offset
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Una forma disegnata dall'utente, come la salva il Playground: vertici normalizzati 0..1 e il
 * raccordo. Il frame non si salva — una forma salvata si riapplica al palco che c'è, non a quello
 * che c'era.
 */
internal data class SavedShape(
  val name: String,
  val rounding: Float,
  val vertices: List<Offset>,
)

/**
 * Persistenza delle forme disegnate: un file JSON in filesDir, scritto per intero a ogni modifica.
 *
 * Niente DataStore e niente serializzazione generata di proposito: sono tre campi, il file è letto
 * una volta all'apertura della scheda, e ogni dipendenza in più del campione finirebbe nell'APK
 * pubblicato. `org.json` è nella piattaforma.
 */
internal class ShapeStore(context: Context) {

  private val file = File(context.filesDir, "playground_shapes.json")

  fun load(): List<SavedShape> = runCatching {
    if (!file.exists()) return emptyList()
    val array = JSONArray(file.readText())
    buildList {
      for (i in 0 until array.length()) {
        val entry = array.getJSONObject(i)
        val flat = entry.getJSONArray("vertices")
        val vertices = ArrayList<Offset>(flat.length() / 2)
        var j = 0
        while (j + 1 < flat.length()) {
          vertices += Offset(flat.getDouble(j).toFloat(), flat.getDouble(j + 1).toFloat())
          j += 2
        }
        if (vertices.size >= 3) {
          add(
            SavedShape(
              name = entry.optString("name", "Forma ${i + 1}"),
              rounding = entry.optDouble("rounding", 6.0).toFloat(),
              vertices = vertices,
            ),
          )
        }
      }
    }
  }.getOrElse { emptyList() }

  fun persist(shapes: List<SavedShape>) {
    runCatching {
      val array = JSONArray()
      for (shape in shapes) {
        val flat = JSONArray()
        for (v in shape.vertices) {
          flat.put(v.x.toDouble())
          flat.put(v.y.toDouble())
        }
        array.put(
          JSONObject()
            .put("name", shape.name)
            .put("rounding", shape.rounding.toDouble())
            .put("vertices", flat),
        )
      }
      file.writeText(array.toString())
    }
  }
}

/**
 * F7.21 · I salvataggi delle forme. [save] si chiama dal thread principale, quello che cambia la lista: la copia si
 * prende lì, subito, e un filo solo la scrive dopo con [write], fuori dal principale. Due salvataggi vicini non si
 * scavalcano: se il disco è lento, si scrive l'ultima lista e quelle in mezzo si saltano.
 *
 * Prima la copia si prendeva dentro `launch(Dispatchers.IO)`: scorrere una lista di stato da un filo mentre il
 * principale la cambia dà liste a metà o una `ConcurrentModificationException` (vedi `regole.md`, «Uno stato di
 * Compose si scrive dal thread principale»), e due salvataggi su due fili potevano arrivare al disco in ordine
 * inverso, perdendo l'ultima forma.
 */
internal class ShapeSaves(
  scope: CoroutineScope,
  private val write: (List<SavedShape>) -> Unit,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val pending = Channel<List<SavedShape>>(Channel.CONFLATED)

  init {
    scope.launch(dispatcher) { for (shapes in pending) write(shapes) }
  }

  /** Salva [shapes] com'è adesso. Dal thread che la cambia. */
  fun save(shapes: List<SavedShape>) {
    pending.trySend(shapes.toList())
  }
}
