package dev.antigravity.fluidengine.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.ui.fluid.icons.Cloud
import dev.antigravity.fluidengine.ui.fluid.icons.Download
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.PhoneAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * F8.18 · **Le posizioni concesse**: le cartelle che l'utente ha scelto col selettore di sistema dello Storage Access
 * Framework (`ACTION_OPEN_DOCUMENT_TREE`), che restano fra un avvio e l'altro col permesso persistente. Sono il telefono
 * (una cartella della memoria interna o di una scheda) e **le app che hanno un `DocumentsProvider`** (Drive, MyCloud
 * Gallery, un NAS): Android non lascia elencare i provider delle altre app, li mostra solo nel suo selettore, che è
 * quindi la porta di «Aggiungi una posizione…».
 *
 * La lista ([sources]) si legge dai permessi persistenti dell'app (`persistedUriPermissions`), sempre aggiornata: una
 * cartella il cui permesso è stato tolto (da Impostazioni, o un'app disinstallata) sparisce da sola alla prossima
 * lettura ([reload]). [add] prende il permesso, [remove] lo restituisce. Il nome è quello della cartella; la seconda
 * riga dice da dove viene («Sul telefono», «Download», il nome dell'app del provider).
 *
 * **Il permesso di chi è** (R1). Lo sfoglia-file mostra tutte le cartelle concesse all'app, anche quelle che usa un'altra
 * sua funzione (la cartella delle esportazioni, un'importazione). [remove] restituisce il permesso solo se l'ha preso lo
 * sfoglia-file con [add] e nessuno ci scrive; altrimenti la cartella si **nasconde** soltanto (anche ai prossimi avvii),
 * e l'altra funzione non perde l'accesso. Riscelta con [add], torna.
 *
 * Android non concede la radice della memoria interna né la cartella Download intera (da Android 11): il selettore
 * propone di scegliere una cartella dentro. È la regola della piattaforma, non dell'engine.
 */
@Stable
class FluidFileLocations internal constructor(context: Context) {
  private val app = context.applicationContext

  /** Le cartelle concesse, come sorgenti, in ordine di nome. Si aggiorna con [reload]. */
  var sources: List<FluidFileSource> by mutableStateOf(emptyList())
    private set

  /** Vero finché la prima lettura non è finita. */
  var loading: Boolean by mutableStateOf(true)
    private set

  /** Rilegge i permessi persistenti e i nomi delle cartelle (fuori dal thread principale). */
  suspend fun reload() {
    sources = withContext(Dispatchers.IO) { read() }
    loading = false
  }

  /**
   * Prende il permesso persistente di lettura sull'albero [treeUri] (il risultato del selettore) e lo aggiunge.
   * Restituisce la sorgente, o null se il permesso non si può prendere (il selettore non l'ha concesso).
   */
  suspend fun add(treeUri: Uri): FluidFileSource? {
    val taken = withContext(Dispatchers.IO) {
      // Una cartella che l'app teneva già (un'altra funzione) non diventa dello sfoglia-file: togliendola si nasconde.
      val held = app.contentResolver.persistedUriPermissions.any { it.uri == treeUri }
      try {
        app.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val key = treeUri.toString()
        store(owned = if (held) stored(OwnedKey) else stored(OwnedKey) + key, hidden = stored(HiddenKey) - key)
        true
      } catch (_: SecurityException) {
        false
      }
    }
    reload()
    if (!taken) return null
    return sources.firstOrNull { (it as? FluidDocumentTreeSource)?.treeUri == treeUri }
  }

  /**
   * Toglie la cartella dalle posizioni. Se il permesso l'ha preso lo sfoglia-file ([add]) e nessuno ci scrive, lo
   * restituisce; altrimenti (la usa un'altra funzione dell'app) la nasconde soltanto. I file restano dove sono.
   */
  suspend fun remove(source: FluidFileSource) {
    val tree = (source as? FluidDocumentTreeSource)?.treeUri ?: return
    withContext(Dispatchers.IO) {
      val key = tree.toString()
      val held = app.contentResolver.persistedUriPermissions.firstOrNull { it.uri == tree }
      val ours = key in stored(OwnedKey)
      if (ours && held?.isWritePermission != true) {
        try {
          app.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        store(owned = stored(OwnedKey) - key, hidden = stored(HiddenKey) - key)
      } else {
        // R1: prima si restituivano lettura e scrittura, e l'export perdeva la sua cartella.
        store(owned = stored(OwnedKey) - key, hidden = stored(HiddenKey) + key)
      }
    }
    reload()
  }

  /** Vero se [source] è una cartella concessa (si può togliere). */
  fun owns(source: FluidFileSource): Boolean = source is FluidDocumentTreeSource && sources.any { it.id == source.id }

  private fun read(): List<FluidFileSource> {
    val hidden = stored(HiddenKey)
    val trees = app.contentResolver.persistedUriPermissions
      .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) && it.uri.toString() !in hidden }
      .map { it.uri }
      .distinct()
    return trees.map { tree ->
      val authority = tree.authority.orEmpty()
      FluidFileSources.documentTree(app, tree, title = treeTitle(tree), subtitle = providerLabel(authority),
        icon = providerIcon(authority))
    }.sortedBy { it.title.lowercase() }
  }

  private fun treeTitle(tree: Uri): String {
    val document = try {
      DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    } catch (_: IllegalArgumentException) {
      return fallbackTitle(tree)
    }
    val name = try {
      app.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
      null
    }
    return name?.takeIf { it.isNotBlank() } ?: fallbackTitle(tree)
  }

  private fun fallbackTitle(tree: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
      ?.substringAfterLast(':')?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: words.folder

  private fun providerLabel(authority: String): String = when (authority) {
    ExternalStorage -> words.phone
    Downloads -> words.downloads
    Media -> words.media
    else -> try {
      val info = app.packageManager.resolveContentProvider(authority, 0)
      info?.applicationInfo?.loadLabel(app.packageManager)?.toString() ?: authority
    } catch (_: Exception) {
      authority
    }
  }

  private fun providerIcon(authority: String): ImageVector = when (authority) {
    ExternalStorage -> FluidIcons.PhoneAndroid
    Downloads -> FluidIcons.Download
    Media -> FluidIcons.Folder
    else -> FluidIcons.Cloud
  }

  /** Quali cartelle ha preso lo sfoglia-file ([OwnedKey]) e quali ha nascosto ([HiddenKey]): un file corto dell'app. */
  private val prefs by lazy { app.getSharedPreferences(PrefsName, Context.MODE_PRIVATE) }

  private fun stored(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet().orEmpty()

  private fun store(owned: Set<String>, hidden: Set<String>) {
    prefs.edit().putStringSet(OwnedKey, owned).putStringSet(HiddenKey, hidden).apply()
  }

  /** Le parole delle posizioni (le stesse di [FluidFileBrowserStrings]). */
  internal var words: FluidFileBrowserStrings = FluidFileBrowserStrings()

  companion object {
    internal const val ExternalStorage = "com.android.externalstorage.documents"
    internal const val Downloads = "com.android.providers.downloads.documents"
    internal const val Media = "com.android.providers.media.documents"
    private const val PrefsName = "fluid_file_locations"
    private const val OwnedKey = "owned"
    private const val HiddenKey = "hidden"

    /**
     * Dove apre il selettore: la memoria interna del telefono (il documento `primary:`). È un suggerimento: l'utente
     * può scegliere un'altra posizione o un'altra app dal menu del selettore.
     */
    val PhoneHint: Uri = DocumentsContract.buildDocumentUri(ExternalStorage, "primary:")

    /** Per le prove e per chi le crea fuori da Compose. */
    fun of(context: Context): FluidFileLocations = FluidFileLocations(context)
  }
}

/** Le posizioni concesse, lette alla prima composizione. */
@Composable
fun rememberFluidFileLocations(): FluidFileLocations {
  val context = LocalContext.current
  val locations = remember(context) { FluidFileLocations(context) }
  LaunchedEffect(locations) { locations.reload() }
  return locations
}

/**
 * «Aggiungi una posizione…»: la funzione apre il selettore di sistema delle cartelle (sul telefono, di serie
 * [FluidFileLocations.PhoneHint]); la cartella scelta entra fra le posizioni col permesso persistente e arriva a
 * [onAdded]. Se l'utente chiude il selettore non succede niente.
 */
@Composable
fun rememberFluidAddLocation(
  locations: FluidFileLocations,
  hint: Uri? = FluidFileLocations.PhoneHint,
  onAdded: (FluidFileSource) -> Unit = {},
): () -> Unit {
  val scope = rememberCoroutineScope()
  val currentAdded by rememberUpdatedState(onAdded)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
    if (tree != null) scope.launch { locations.add(tree)?.let { currentAdded(it) } }
  }
  return remember(launcher, hint) {
    {
      try {
        launcher.launch(hint)
      } catch (_: android.content.ActivityNotFoundException) {
        // Nessun selettore di documenti (un dispositivo senza DocumentsUI): la posizione non si aggiunge.
      }
    }
  }
}
