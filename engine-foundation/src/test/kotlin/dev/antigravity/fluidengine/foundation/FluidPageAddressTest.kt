package dev.antigravity.fluidengine.foundation

import org.junit.Assert.*
import org.junit.Test

class FluidPageAddressTest {
  private sealed interface Page {
    data object Home : Page
    data class Item(val key: String, val selection: String?) : Page
  }

  private fun table(
    homePublic: Boolean = true,
    itemPublic: Boolean = true,
    homeState: FluidAddressAvailability = FluidAddressAvailability.Available,
    itemState: (Page.Item) -> FluidAddressAvailability = { FluidAddressAvailability.Available },
    parent: (Page.Item) -> Page = { Page.Home },
  ) = FluidAddressTable<Page>("app", listOf(
    FluidAddressPage<Page, Page.Home>(Page.Home::class, "home",
      { p -> Page.Home.takeIf { p == FluidAddressParameters() } }, { FluidAddressParameters() },
      { null }, homePublic, { homeState }),
    FluidAddressPage<Page, Page.Item>(Page.Item::class, "item",
      { p -> p.segments.singleOrNull()?.takeIf { p.query.keys.all { it == "q" } }?.let { Page.Item(it, p.query["q"]) } },
      { FluidAddressParameters(listOf(it.key), it.selection?.let { s -> mapOf("q" to s) }.orEmpty()) },
      parent, itemPublic, itemState),
  ), httpsHost = "links.test")

  @Test fun canonicalEncodingRoundTripAndNaturalStack() {
    val table = table()
    val item = Page.Item("a/b +%2F📚", "è&=+?#")
    val link = table.addressOf(item)
    assertEquals("app://item/a%2Fb%20%2B%252F%F0%9F%93%9A?q=%C3%A8%26%3D%2B%3F%23", link)
    assertEquals(FluidAddressResult.Open(listOf(Page.Home, item)), table.resolve(FluidAddressRequest(link)))
    assertEquals("app://home", table.addressOf(Page.Home))
    val decomposed = "e\u0301+"
    assertEquals(FluidAddressResult.Open(listOf(Page.Home, Page.Item(decomposed, ""))),
      table.resolve(FluidAddressRequest("https://LINKS.TEST/item/$decomposed?q=")))
  }

  @Test fun malformedAndUnknownInputsAreRejected() {
    val table = table()
    for (url in listOf("", "app:item/a", "x://item/a", "app://none/a", "app://item", "app://item//a",
      "app://item/a/", "app://item/%", "app://item/%FG", "app://item/%C0%AF", "app://item/%FF",
      "app://item/%00", "app://item/%0a", "app://item/%2e%2e", "app://item/a?x=1", "app://item/a?=1",
      "app://item/a?q=1&%71=2", "app://item/a?q", "app://item/a#", "app://x@item/a", "app://item:8/a",
      "https://else.test/item/a", "https://user@links.test/item/a", "https://links.test:123/item/a",
      "app://item/\uD800")) {
      assertEquals(url, FluidAddressResult.Rejected, table.resolve(FluidAddressRequest(url)))
    }
  }

  @Test fun integerPageKeysRoundTripAsBoxedKotlinValues() {
    val table = FluidAddressTable("app", listOf(FluidAddressPage<Int, Int>(
      Int::class, "pagina", { it.segments.singleOrNull()?.toIntOrNull() },
      { FluidAddressParameters(listOf(it.toString())) }, { null }, public = true,
    )))
    assertEquals("app://pagina/42", table.addressOf(42))
    assertEquals(FluidAddressResult.Open(listOf(42)), table.resolve(FluidAddressRequest("app://pagina/42")))
  }

  @Test fun accessAndMissingStopAtTheFirstUnavailableAncestor() {
    val request = FluidAddressRequest("app://item/x")
    assertEquals(FluidAddressResult.Missing(listOf(Page.Home)),
      table(itemState = { FluidAddressAvailability.Missing }).resolve(request))
    assertEquals(FluidAddressResult.SignIn(emptyList<Page>(), request),
      table(homeState = FluidAddressAvailability.SignInRequired, itemState = { error("figlio prima dell'accesso") }).resolve(request))
    val trusted = request.copy(trusted = true)
    assertEquals(FluidAddressResult.SignIn(listOf(Page.Home), trusted),
      table(itemPublic = false, itemState = { FluidAddressAvailability.SignInRequired }).resolve(trusted))
  }

  @Test fun privateAncestorsAndTargetsAndCycles() {
    val request = FluidAddressRequest("app://item/x")
    assertEquals(FluidAddressResult.Rejected, table(homePublic = false).resolve(request))
    assertEquals(FluidAddressResult.Rejected, table(itemPublic = false).resolve(request))
    assertTrue(table(homePublic = false, itemPublic = false).resolve(request.copy(trusted = true)) is FluidAddressResult.Open)
    assertEquals(FluidAddressResult.Rejected, table(parent = { it }).resolve(request))
    assertEquals(FluidAddressResult.Rejected, table(parent = { it.copy(key = it.key + "x") }).resolve(request))
  }

  @Test fun onlyAnAllowedPackageWithMatchingSignatureIsTrusted() {
    val allowed = setOf("p.notes", "p.mail")
    val signatures = mapOf("p.notes" to "A", "p.mail" to "B", "other" to "A")
    fun trusted(name: String?) = fluidAddressCallerTrusted(name, allowed) { signatures[it] == "A" }
    assertTrue(trusted("p.notes"))
    assertFalse(trusted("p.mail"))
    assertFalse(trusted("other"))
    assertFalse(trusted(null))
  }

  @Test fun ambiguousTableFailsAtRegistration() {
    fun row(name: String) = FluidAddressPage<Page, Page.Home>(Page.Home::class, name, { Page.Home },
      { FluidAddressParameters() }, { null })
    assertThrows(IllegalArgumentException::class.java) { FluidAddressTable("app", listOf(row("home"), row("home"))) }
    assertThrows(IllegalArgumentException::class.java) { FluidAddressTable("app", listOf(row("home"), row("root"))) }
  }
}
