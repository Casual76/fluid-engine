package dev.antigravity.fluidengine.ui.compat

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * La prova di compatibilita' di sorgente con la 3.0.7: chiamate scritte **solo con l'API della 3.0.7**,
 * in tutte le forme che la 3.0.7 accettava, per le firme che la 3.1.0 ha toccato.
 *
 * Perche' esiste. Le app compilano l'engine dai sorgenti: la regola e' che un'app che compila sulla 3.0.7
 * compili sulla versione nuova senza cambiare nulla e si comporti come prima. La candidata 3.1.0 aveva
 * aggiunto un parametro **dopo** l'ultima lambda di `FluidTabRail`, `FluidSidebarRow` e `tab(…)`: la lambda
 * finale smetteva di compilare o, per il rail, finiva nell'intestazione e il reselect spariva senza errori.
 * Questo file non compila se succede di nuovo; i test qui sotto e `Compat307BehaviourTest` della galleria
 * provano a quale parametro arriva la lambda.
 *
 * Regole per chi lo tocca:
 * - qui **niente API nuova** (ne' `header`, ne' `badge`, ne' `level`…): il file deve compilare identico
 *   anche contro l'engine 3.0.7. Le prove delle firme nuove stanno in `Compat310OverloadsTest`;
 * - gli import sono quelli di un'app (a stella sui pacchetti pubblici, da un pacchetto che non e'
 *   dell'engine), perche' la risoluzione degli overload sia quella che vede un'app;
 * - le funzioni @Composable non si eseguono: contano perche' compilano. Il comportamento lo provano i test.
 * - quando una versione tocca un'altra firma pubblica, la sua forma 3.0.x si aggiunge qui.
 */

// --- Barra a pillola: FluidFloatingTabBarScope.tab(…) ------------------------------------------------

/** Un'implementazione dell'interfaccia scritta per la 3.0.7: ridefinisce esattamente i suoi due membri. */
internal class Recording307Scope : FluidFloatingTabBarScope {
  data class Tab(val key: Any, val indication: (@Composable () -> Indication)?)

  val tabs = mutableListOf<Tab>()
  var standalone: Any? = null

  override fun tab(
    key: Any,
    title: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    indication: (@Composable () -> Indication)?,
  ) {
    tabs += Tab(key, indication)
  }

  override fun standaloneTab(
    key: Any,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    indication: (@Composable () -> Indication)?,
    title: @Composable () -> Unit,
  ) {
    standalone = key
  }
}

/** Le chiamate della 3.0.7 a `tab`: per nome, per posizione, con la lambda finale (l'indicazione). */
internal fun FluidFloatingTabBarScope.tabCallsAsIn307(indication: @Composable () -> Indication) {
  tab(key = "named", title = { Text("A") }, icon = { Icon(Icons.Rounded.Home, null) }, onClick = {})
  tab("positional", { Text("B") }, { Icon(Icons.Rounded.Home, null) }, {})
  tab("positional+indication", {}, {}, {}, indication)
  tab("positional+null", {}, {}, {}, null)
  tab("trailing", {}, {}, {}) { LocalIndication.current }
  tab(key = "named+trailing", title = {}, icon = {}, onClick = {}) { LocalIndication.current }
  tab(key = "named+indication", title = {}, icon = {}, onClick = {}, indication = indication)
  tab(key = "named+null", title = {}, icon = {}, onClick = {}, indication = null)
  standaloneTab(key = "write", icon = {}, onClick = {})
  standaloneTab("write", {}, {}, indication) { Text("Scrivi") }
}

/** Un riferimento al membro, col tipo atteso della 3.0.7. */
internal val tabReferenceAsIn307:
  FluidFloatingTabBarScope.(Any, @Composable () -> Unit, @Composable () -> Unit, () -> Unit, (@Composable () -> Indication)?) -> Unit =
  FluidFloatingTabBarScope::tab

@Composable
internal fun FloatingTabBarAsIn307(selected: String, scroll: FluidFloatingTabBarScrollConnection) {
  FluidFloatingTabBar(selectedTabKey = selected, scrollConnection = scroll) {
    tab(key = "mail", title = { Text("Posta") }, icon = { Icon(Icons.Rounded.Home, null) }, onClick = {})
    tab("chat", { Text("Chat") }, { Icon(Icons.Rounded.Star, null) }, {}) { LocalIndication.current }
  }
}

// --- Rail: FluidTabRail ----------------------------------------------------------------------------

private fun select(item: FluidTabItem) = Unit
private fun reselect(item: FluidTabItem) = Unit

@Composable
internal fun TabRailCallsAsIn307(items: List<FluidTabItem>, backdrop: GlassBackdropState, log: MutableList<String>) {
  // Per nome, come le app dell'ecosistema.
  FluidTabRail(
    items = items,
    selectedRoute = "home",
    onSelect = { log += it.route },
    backdrop = backdrop,
    modifier = Modifier,
    onReselect = { log += "re:" + it.route },
  )
  // Per posizione.
  FluidTabRail(items, null, {}, backdrop)
  FluidTabRail(items, "home", {}, backdrop, Modifier)
  FluidTabRail(items, "home", { log += it.route }, backdrop, Modifier, { log += "re:" + it.route })
  FluidTabRail(items, "home", ::select, backdrop, Modifier, ::reselect)
  FluidTabRail(items = items, selectedRoute = "home", onSelect = ::select, backdrop = backdrop, onReselect = ::reselect)
  // Lambda finale: il reselect. Con il parametro esplicito, con `it` (che esiste solo se la lambda e' il
  // reselect: in un'intestazione con ricevitore ColumnScope non c'e'), e senza parametri.
  FluidTabRail(items, "home", {}, backdrop) { item -> log += "re:" + item.route }
  FluidTabRail(items, "home", {}, backdrop) { val item: FluidTabItem = it; log += "re:" + item.route }
  FluidTabRail(items, "home", {}, backdrop) { log += "re" }
  FluidTabRail(items, "home", {}, backdrop, Modifier) { log += "re" }
  FluidTabRail(items = items, selectedRoute = "home", onSelect = {}, backdrop = backdrop) { log += "re" }
}

// --- Barra laterale: FluidSidebarRow, FluidSidebarSection -------------------------------------------

private fun menu(): List<FluidContextAction> = emptyList()

@Composable
internal fun SidebarCallsAsIn307(actions: () -> List<FluidContextAction>) {
  FluidSidebarRow(label = "Posta", selected = true, onClick = {})
  FluidSidebarRow("Posta", true, {})
  FluidSidebarRow("Posta", false, {}, Modifier, Icons.Rounded.Home, Color.Red, "12", "3")
  // Tutti e nove per posizione: il nono e' il menu.
  FluidSidebarRow("Posta", false, {}, Modifier, Icons.Rounded.Home, Color.Red, "12", "3", actions)
  FluidSidebarRow("Posta", false, {}, Modifier, null, null, null, null, null)
  FluidSidebarRow("Posta", false, {}, Modifier, null, null, null, null) { listOf(FluidContextAction("Sposta") {}) }
  // Lambda finale: il menu.
  FluidSidebarRow("Posta", false, {}) { listOf(FluidContextAction("Sposta") {}) }
  FluidSidebarRow("Posta", false, {}, icon = Icons.Rounded.Home, detail = "12") { emptyList() }
  FluidSidebarRow(label = "Posta", selected = false, onClick = {}) { actions() }
  FluidSidebarRow(label = "Posta", selected = false, onClick = {}, contextActions = actions)
  FluidSidebarRow(label = "Posta", selected = false, onClick = {}, contextActions = ::menu)
  FluidSidebarRow(label = "Posta", selected = false, onClick = {}, contextActions = null)

  FluidSidebarSection("Materie")
  FluidSidebarSection("Materie", Modifier)
  FluidSidebarSection(title = "Materie", modifier = Modifier)
}

// --- Le altre firme che la 3.1.0 ha toccato (aggiunte in coda o overload nuovi) -----------------------

@Composable
internal fun OtherCallsAsIn307(onClick: () -> Unit, icon: ImageVector) {
  FluidCapsuleAction(icon, "Preferito", onClick)
  FluidCapsuleAction(icon, "Preferito", onClick, Modifier, true, false, Color.Unspecified, null, 48.dp, FluidPressGrip.HoldsFinger)
  FluidCapsuleAction(icon = icon, contentDescription = "Preferito", onClick = onClick, grip = FluidPressGrip.YieldsToScroll)

  FluidBarAction(icon, "Preferito", onClick)
  FluidBarAction(icon, null, onClick, Modifier, true, null, FluidMenuOpensOn.LongPress)
  FluidBarAction(icon = icon, contentDescription = "Altro", onClick = onClick, actions = { emptyList() })

  FluidEmptyState("Vuoto", "Niente qui")
  FluidEmptyState("Vuoto", "Niente qui", Modifier)
  FluidEmptyState("Vuoto", "Niente qui", Modifier, null)
  FluidEmptyState("Vuoto", "Niente qui") { Icon(icon, null) }
  FluidEmptyState("Vuoto", "Niente qui", Modifier) { Icon(icon, null) }
  FluidEmptyState(title = "Vuoto", detail = "Niente qui", illustration = { Icon(icon, null) })

  FluidStatusBadge("Attivo")
  FluidStatusBadge("Attivo", Modifier)
  FluidStatusBadge("Attivo", Modifier, FluidTone.Success)
  FluidStatusBadge(label = "Attivo", tone = FluidTone.Success)

  FluidListRow("Titolo", "Sottotitolo")
  FluidListRow(
    "Titolo", "Sottotitolo", Modifier, "eyebrow", "meta", FluidTone.Neutral, null, null, 2, null,
    onClick, null, null, true, false, false, true, true, 3,
  )
  FluidListRow(AnnotatedString("Titolo"), "Sottotitolo", onClick = onClick)
  FluidListRow(
    AnnotatedString("Titolo"), "Sottotitolo", Modifier, null, null, FluidTone.Neutral, null, null, 2, null,
    onClick, null, null, true, false, false, true, true, 3,
  )
  FluidListRow(title = "Titolo", subtitle = "Sottotitolo", contextActions = { emptyList() })
}

// --- Prove che si eseguono --------------------------------------------------------------------------

class Compat307SourceTest {

  private val ind: @Composable () -> Indication = { LocalIndication.current }

  @Test
  fun `tab della 3_0_7 - la lambda finale e' l'indicazione e un null al quinto posto non e' ambiguo`() {
    val scope = Recording307Scope()
    scope.tabCallsAsIn307(ind)

    val byKey = scope.tabs.associateBy { it.key }
    assertEquals(8, scope.tabs.size)
    assertNull(byKey.getValue("named").indication)
    assertNull(byKey.getValue("positional").indication)
    assertSame(ind, byKey.getValue("positional+indication").indication)
    assertNull(byKey.getValue("positional+null").indication)
    assertTrue(byKey.getValue("trailing").indication != null)
    assertTrue(byKey.getValue("named+trailing").indication != null)
    assertSame(ind, byKey.getValue("named+indication").indication)
    assertNull(byKey.getValue("named+null").indication)
    assertEquals("write", scope.standalone)
  }

  @Test
  fun `il riferimento a tab col tipo della 3_0_7 chiama la firma della 3_0_7`() {
    val scope = Recording307Scope()
    scope.tabReferenceAsIn307("ref", {}, {}, {}, ind)
    assertEquals(listOf(Recording307Scope.Tab("ref", ind)), scope.tabs)
  }

  @Test
  fun `FluidTabItem della 3_0_7 - posizione, scomposizione e copy`() {
    val item = FluidTabItem("home", "Home", Icons.Rounded.Home)
    val (route, label, icon) = item
    assertEquals("home", route)
    assertEquals("Home", label)
    assertSame(Icons.Rounded.Home, icon)
    assertEquals(item, FluidTabItem(route = "home", label = "Home", icon = Icons.Rounded.Home))
    assertEquals("star", item.copy(route = "star").route)
    assertEquals(item, item.copy())
  }

  @Test
  fun `FluidNotification della 3_0_7 - posizione, scomposizione e copy`() {
    val action = FluidNotificationAction("Annulla") {}
    val notification = FluidNotification("id", "Titolo", "Messaggio", FluidNotificationTone.Success, 4_000L, listOf(action))
    val (id, title, message, tone, duration, actions) = notification
    assertEquals("id", id)
    assertEquals("Titolo", title)
    assertEquals("Messaggio", message)
    assertEquals(FluidNotificationTone.Success, tone)
    assertEquals(4_000L, duration)
    assertEquals(listOf(action), actions)
    assertEquals("altro", notification.copy(id = "altro").id)
    assertEquals(notification, FluidNotification(id = "id", title = "Titolo", message = "Messaggio",
      tone = FluidNotificationTone.Success, durationMillis = 4_000L, actions = listOf(action)))
  }

  @Test
  fun `FluidLabels della 3_0_7 - costruzione per nome e copy`() {
    val labels = FluidLabels(message = "Avviso")
    assertEquals("Avviso", labels.message)
    assertEquals("Note", FluidLabels.english().copy(message = "Note").message)
  }
}
