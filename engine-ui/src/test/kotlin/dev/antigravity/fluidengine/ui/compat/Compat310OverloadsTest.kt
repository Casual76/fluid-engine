package dev.antigravity.fluidengine.ui.compat

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.ui.fluid.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/*
 * Le firme nuove della 3.1.0 che stanno **accanto** a quelle della 3.0.7 (vedi `Compat307SourceTest`):
 * `FluidTabRail(…, header, …)`, `FluidSidebarRow(…, level, leading, trailing, minHeight, contextActions)`
 * e `tab(key, title, icon, badge, onClick, indication)`. Si raggiungono per nome, come le chiamano galleria e test, e la lambda
 * finale resta quella di sempre (reselect, menu, indicazione).
 */

@Composable
internal fun TabRailCallsIn310(items: List<FluidTabItem>, backdrop: GlassBackdropState, wide: Boolean, log: MutableList<String>) {
  FluidTabRail(items = items, selectedRoute = "home", onSelect = {}, backdrop = backdrop, header = { Text("Scrivi") })
  FluidTabRail(
    items = items,
    selectedRoute = "home",
    onSelect = {},
    backdrop = backdrop,
    modifier = Modifier,
    header = if (wide) { { Text("Scrivi") } } else null,
  )
  // La lambda finale resta il reselect anche con l'intestazione.
  FluidTabRail(items, "home", {}, backdrop, header = { Text("Scrivi") }) { val item: FluidTabItem = it; log += item.route }
  val header: @Composable ColumnScope.() -> Unit = { Text("Scrivi") }
  FluidTabRail(items, "home", {}, backdrop, header, Modifier) { log += "re" }
}

@Composable
internal fun SidebarCallsIn310(actions: () -> List<FluidContextAction>) {
  FluidSidebarRow(label = "Cartella", selected = false, onClick = {}, level = 2)
  FluidSidebarRow(
    label = "Cartella",
    selected = false,
    onClick = {},
    leading = { Text("A") },
    trailing = { FluidBadge(count = 3) },
    minHeight = FluidSidebarDefaults.ComfortableRowHeight,
    contextActions = actions,
  )
  // La lambda finale resta il menu anche con le aggiunte.
  FluidSidebarRow("Cartella", false, {}, level = 1) { listOf(FluidContextAction("Sposta") {}) }
  FluidSidebarSection("Account", leading = { Text("A") }, detail = "anna@example.com", uppercase = false)
}

internal fun FluidFloatingTabBarScope.tabCallsIn310(indication: @Composable () -> Indication, badge: FluidTabBadge?) {
  tab(key = "dot", title = {}, icon = {}, onClick = {}, badge = FluidTabBadge.Dot)
  tab(key = "maybe", title = {}, icon = {}, onClick = {}, badge = badge)
  tab(key = "dot+trailing", title = {}, icon = {}, onClick = {}, badge = FluidTabBadge(count = 3)) { LocalIndication.current }
  tab("positional", {}, {}, FluidTabBadge.Dot, {}, indication)
}

class Compat310OverloadsTest {

  private val ind: @Composable () -> Indication = { LocalIndication.current }

  @Test
  fun `il tab col segno su un'implementazione della 3_0_7 inoltra al tab di sempre, senza segno`() {
    // L'implementazione di serie del membro nuovo: un'app che implementava l'interfaccia compila com'era,
    // e una chiamata col segno le arriva come la zona di sempre, con la sua indicazione.
    val scope = Recording307Scope()
    scope.tabCallsIn310(ind, badge = null)

    val byKey = scope.tabs.associateBy { it.key }
    assertEquals(listOf("dot", "maybe", "dot+trailing", "positional"), scope.tabs.map { it.key })
    assertNull(byKey.getValue("dot").indication)
    assertNull(byKey.getValue("maybe").indication)
    assertEquals(true, byKey.getValue("dot+trailing").indication != null)
    assertSame(ind, byKey.getValue("positional").indication)
  }

  @Test
  fun `FluidTabItem col segno - in coda, con default`() {
    val item = FluidTabItem("chat", "Chat", Icons.Rounded.Home, FluidTabBadge(count = 2))
    assertEquals(FluidTabBadge(count = 2), item.badge)
    assertNull(FluidTabItem("chat", "Chat", Icons.Rounded.Home).badge)
  }
}
