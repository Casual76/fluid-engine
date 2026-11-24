package dev.antigravity.fluidengine.sample.novita

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidAddressAvailability
import dev.antigravity.fluidengine.foundation.FluidAddressPage
import dev.antigravity.fluidengine.foundation.FluidAddressParameters
import dev.antigravity.fluidengine.foundation.FluidAddressRequest
import dev.antigravity.fluidengine.foundation.FluidAddressTable
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.navigation.FluidAddressNavigation
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.newFluidAddress
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.navigation.takeInitialFluidAddress
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme

private sealed interface DemoPage {
  data object Home : DemoPage
  data class Book(val id: Long) : DemoPage
  data class Note(val id: Long) : DemoPage
}

private fun FluidAddressParameters.id(): Long? =
  segments.singleOrNull()?.toLongOrNull()?.takeIf { it > 0 && query.isEmpty() }

private fun demoTable(signedIn: () -> Boolean): FluidAddressTable<DemoPage> = FluidAddressTable(
  "fluidglass",
  listOf(
    FluidAddressPage<DemoPage, DemoPage.Home>(DemoPage.Home::class, "home",
      { params -> DemoPage.Home.takeIf { params == FluidAddressParameters() } }, { FluidAddressParameters() },
      parent = { null }, public = true),
    FluidAddressPage<DemoPage, DemoPage.Book>(DemoPage.Book::class, "quaderno",
      { it.id()?.let(DemoPage::Book) }, { FluidAddressParameters(listOf(it.id.toString())) },
      parent = { DemoPage.Home }, public = true,
      availability = { if (it.id == 2L) FluidAddressAvailability.Available else FluidAddressAvailability.Missing }),
    FluidAddressPage<DemoPage, DemoPage.Note>(DemoPage.Note::class, "nota",
      { it.id()?.let(DemoPage::Note) }, { FluidAddressParameters(listOf(it.id.toString())) },
      parent = { DemoPage.Book(2) }, public = true,
      availability = {
        when {
          it.id == 7L && !signedIn() -> FluidAddressAvailability.SignInRequired
          it.id == 123L || it.id == 7L -> FluidAddressAvailability.Available
          else -> FluidAddressAvailability.Missing
        }
      }),
  ),
)

private fun routeOf(page: DemoPage): String = when (page) {
  DemoPage.Home -> "home"
  is DemoPage.Book -> "quaderno/${page.id}"
  is DemoPage.Note -> "nota/${page.id}"
}

private fun demoIntent(context: Context, url: String) =
  Intent(Intent.ACTION_VIEW, Uri.parse(url), context, AddressDemoActivity::class.java)

private val probeLinks = listOf(
  "Apri la lista del viaggio" to "fluidglass://nota/123",
  "Apri una nota cancellata" to "fluidglass://nota/404",
  "Apri il diario con accesso" to "fluidglass://nota/7",
)

internal val F8p12Scenes = listOf(
  NewElementScene("indirizzi-pagine", "Indirizzi delle pagine", "Apri una nota e torna al suo quaderno.") { onBack ->
    val context = LocalContext.current
    FluidScreen(title = "Indirizzi delle pagine", onBack = onBack) {
      item { ProbeLinks { context.startActivity(demoIntent(context, it)) } }
    }
  },
)

internal val F8p12TryItems = listOf(
  TryItem("f8-12-indirizzi", TryArea.Pages, "Dalla nota al quaderno",
    "Apri la lista del viaggio, copia il link, torna col dito al quaderno e poi alla home.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Indirizzi delle pagine")),
  TryItem("f8-12-recupero", TryArea.Pages, "Una nota cancellata e una protetta",
    "Torna al quaderno dalla nota cancellata, poi apri il diario, entra e verifica il link della nota.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Indirizzi delle pagine")),
)

@Composable
private fun ProbeLinks(open: (String) -> Unit) {
  FluidListGroup {
    probeLinks.forEachIndexed { index, (title, url) ->
      if (index > 0) FluidListDivider()
      FluidListRow(title = title, subtitle = url, onClick = { open(url) })
    }
  }
}

/** L'Activity dell'app di prova riceve lo stesso ACTION_VIEW dalla galleria, da adb e da altre app. */
class AddressDemoActivity : ComponentActivity() {
  private var deliver: ((FluidAddressRequest) -> Unit)? = null
  private val waiting = ArrayDeque<FluidAddressRequest>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val initial = takeInitialFluidAddress()
    enableEdgeToEdge()
    setContent {
      FluidMotionPolicyProvider {
        FluidTheme(settings = remember { EngineSettings() }) {
          val signedIn = rememberSaveable { mutableStateOf(false) }
          val table = remember { demoTable { signedIn.value } }
          val navigation = remember { FluidAddressNavigation(table, ::routeOf, "mancante", "accesso") }
          val nav = rememberFluidNavController()
          val back: () -> Unit = { if (!nav.popBackStack()) finish() }
          FluidNavHost(nav, "home", navigation, initial, Modifier.fillMaxSize()) {
            composable("home") {
              NotePage("I miei quaderni", table.addressOf(DemoPage.Home), null) {
                item { FluidListGroup { FluidListRow(title = "Quaderno di viaggio", subtitle = "Due note", onClick = {
                  navigation.open(nav, FluidAddressRequest(table.addressOf(DemoPage.Book(2))))
                }) } }
              }
            }
            composable("quaderno/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
              val book = DemoPage.Book(checkNotNull(entry.arguments).getLong("id"))
              NotePage("Quaderno di viaggio", table.addressOf(book), back)
            }
            composable("nota/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
              val note = DemoPage.Note(checkNotNull(entry.arguments).getLong("id"))
              val diary = note.id == 7L
              NotePage(if (diary) "Il mio diario" else "Lista del viaggio", table.addressOf(note), back) {
                item {
                  Image(
                    painterResource(if (diary) R.drawable.viewer_rocket else R.drawable.viewer_flower),
                    contentDescription = if (diary) "Un razzo sulla rampa al tramonto" else "Un fiore fotografato da vicino",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(ContinuousCornerShape(FluidRadius.Group)),
                  )
                }
                item {
                  FluidListGroup {
                    FluidListRow(
                      title = if (diary) "Una sera al planetario" else "Da mettere nello zaino",
                      subtitle = if (diary) "Conservare il biglietto e le foto della visita." else "Borraccia, taccuino e macchina fotografica.",
                    )
                  }
                }
              }
            }
            composable("mancante") {
              FluidScreen(title = "Questa nota non c’è più", subtitle = "Il quaderno è ancora qui.", onBack = back) {
                item { FluidListGroup { FluidListRow(title = "Torna al quaderno", subtitle = "Quaderno di viaggio", onClick = back) } }
              }
            }
            composable("accesso") {
              FluidScreen(title = "Prima devi entrare", subtitle = "Il diario ti aspetta dopo l’accesso.", onBack = back) {
                item { FluidListGroup { FluidListRow(title = "Entra nella demo", subtitle = "Accesso di prova", onClick = {
                  signedIn.value = true
                  navigation.resumeAfterSignIn(nav)
                }) } }
              }
            }
          }
          DisposableEffect(nav, navigation) {
            deliver = { navigation.open(nav, it) }
            while (waiting.isNotEmpty()) navigation.open(nav, waiting.removeFirst())
            onDispose { deliver = null }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val request = newFluidAddress(intent) ?: return
    val open = deliver
    if (open == null) waiting.addLast(request) else open(request)
  }
}

@Composable
private fun NotePage(title: String, url: String, back: (() -> Unit)?, body: LazyListScope.() -> Unit = {}) {
  val context = LocalContext.current
  FluidScreen(title = title, subtitle = url, onBack = back, modifier = Modifier.testTag("indirizzo:$url")) {
    body()
    item {
      FluidListGroup {
        FluidListRow(title = "Copia link", subtitle = url, onClick = {
          context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, url))
        })
      }
    }
    item { ProbeLinks { context.startActivity(demoIntent(context, it)) } }
  }
}
