package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.catalog.IdentityDemo
import dev.antigravity.fluidengine.ui.fluid.FluidAvatar
import dev.antigravity.fluidengine.ui.fluid.FluidAvatarItem
import dev.antigravity.fluidengine.ui.fluid.FluidAvatarStack
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionBackHandler
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarRow
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarSection
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Avatar, badge, stato di selezione, barra laterale annidata, azione della barra con un avatar e
 * capsula con stato dentro un albero Compose vero: cio' che TalkBack legge, i bersagli, il testo al 200%.
 *
 * La logica (monogrammi, colori, contrasti, selezione «tutti tranne», saver) la provano i test JVM di
 * `engine-ui`; qui si prova che sia collegata.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidIdentityTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }, content = content)
    }
    compose.waitForIdle()
  }

  private fun showInModalHost(content: @Composable () -> Unit) {
    show {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
  private fun hasToggle(state: ToggleableState) = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)
  private val noDescription = SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)

  // --- Avatar: TalkBack ----------------------------------------------------------------------------

  @Test
  fun `a decorative avatar says nothing - the monogram is never read`() {
    show { FluidAvatar(name = "Anna Rossi", modifier = Modifier.testTag("av")) }

    compose.onNodeWithText("AR").assertDoesNotExist()
    compose.onNodeWithTag("av").assert(noDescription)
  }

  @Test
  fun `an avatar with a description is one node with that description and no monogram`() {
    show { FluidAvatar(name = "Anna Rossi", contentDescription = "Anna Rossi, mittente") }

    compose.onNodeWithContentDescription("Anna Rossi, mittente").assertExists()
    compose.onNodeWithText("AR").assertDoesNotExist()
  }

  @Test
  fun `a clickable avatar without a description is named after the person - never an unlabeled button`() {
    var clicked = 0
    show { FluidAvatar(name = "Anna Rossi", onClick = { clicked++ }) }

    compose.onNodeWithContentDescription("Anna Rossi").assert(hasRole(Role.Button)).performClick()
    compose.waitForIdle()
    assertEquals(1, clicked)
  }

  @Test
  fun `a clickable avatar is at least 48 dp and a plain one is exactly its size`() {
    show {
      Column {
        FluidAvatar(name = "Anna", modifier = Modifier.testTag("plain"))
        FluidAvatar(name = "Bruno", modifier = Modifier.testTag("touch"), onClick = {})
        FluidAvatar(name = "Carla", size = 56.dp, modifier = Modifier.testTag("big"), onClick = {})
      }
    }

    compose.onNodeWithTag("plain").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
    compose.onNodeWithTag("touch").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    // Gia' piu' grande del minimo: non si gonfia.
    compose.onNodeWithTag("big").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
  }

  @Test
  fun `a selectable avatar is a checkbox with its state said in the app's language`() {
    show {
      Column {
        FluidAvatar(name = "Anna", selected = true, modifier = Modifier.testTag("on"))
        FluidAvatar(name = "Bruno", selected = false, modifier = Modifier.testTag("off"))
      }
    }

    compose.onNodeWithTag("on").assert(hasRole(Role.Checkbox)).assert(hasToggle(ToggleableState.On))
    compose.onNodeWithTag("off").assert(hasToggle(ToggleableState.Off))
    compose.onNodeWithTag("on").assert(
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selezionato"),
    )
    compose.onNodeWithTag("off").assert(
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Non selezionato"),
    )
  }

  @Test
  fun `the selection state is said in english with the english labels`() {
    show {
      CompositionLocalProvider(LocalFluidLabels provides FluidLabels.english()) {
        FluidAvatar(name = "Anna", selected = true, modifier = Modifier.testTag("on"))
      }
    }

    compose.onNodeWithTag("on").assert(
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"),
    )
  }

  @Test
  fun `tapping a selectable avatar toggles it and leaves the face when it is on`() {
    show {
      var selected by remember { mutableStateOf<Boolean?>(false) }
      FluidAvatar(
        name = "Anna",
        selected = selected,
        onClick = { selected = !(selected ?: false) },
        modifier = Modifier.testTag("av"),
      )
    }

    compose.onNodeWithTag("av").assert(hasToggle(ToggleableState.Off)).performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("av").assert(hasToggle(ToggleableState.On))
  }

  @Test
  fun `the corner is not read on its own`() {
    show {
      FluidAvatar(
        name = "Anna",
        corner = { FluidBadge(count = 3, contentDescription = "3 novita'") },
        contentDescription = "Anna, 3 novita'",
      )
    }

    compose.onNodeWithContentDescription("Anna, 3 novita'").assertExists()
    // La frase del badge non e' un secondo nodo: il suo contenuto e' dell'avatar.
    compose.onNodeWithContentDescription("3 novita'").assertDoesNotExist()
  }

  @Test
  fun `the avatar keeps its size with text at 200 percent`() {
    show {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        FluidAvatar(name = "Anna Rossi", modifier = Modifier.testTag("av"))
      }
    }

    compose.onNodeWithTag("av").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
  }

  @Test
  fun `an avatar with reduced motion still switches state`() {
    show {
      CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f)) {
        var selected by remember { mutableStateOf<Boolean?>(null) }
        FluidAvatar(
          name = "Anna",
          selected = selected,
          onClick = { selected = true },
          modifier = Modifier.testTag("av"),
        )
      }
    }

    compose.onNodeWithTag("av").performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("av").assert(hasToggle(ToggleableState.On))
  }

  @Test
  fun `a stack is one node with the description it was given and a tap target of 48`() {
    var clicked = 0
    show {
      FluidAvatarStack(
        items = listOf("Anna", "Bruno", "Carla", "Dario", "Elena").map { FluidAvatarItem(it) },
        contentDescription = "Anna, Bruno, Carla e altri 2",
        onClick = { clicked++ },
        modifier = Modifier.testTag("stack"),
      )
    }

    compose.onNodeWithContentDescription("Anna, Bruno, Carla e altri 2").performClick()
    compose.waitForIdle()
    assertEquals(1, clicked)
    compose.onNodeWithTag("stack").assertWidthIsAtLeast(48.dp)
    // L'ultima cella dice «+2» ma nessun testo e' esposto a pezzi.
    compose.onNodeWithText("+2").assertDoesNotExist()
  }

  // --- Badge -------------------------------------------------------------------------------------------

  @Test
  fun `a badge at zero draws nothing and leaves no node`() {
    show { FluidBadge(count = 0, modifier = Modifier.testTag("b")) }
    compose.onNodeWithTag("b").assertDoesNotExist()
  }

  @Test
  fun `a muted badge is not read and a described one is read once with the real quantity`() {
    show {
      Column {
        FluidBadge(count = 3)
        FluidBadge(count = 150, contentDescription = "150 non lette")
      }
    }

    compose.onNodeWithText("3").assertDoesNotExist()
    compose.onNodeWithContentDescription("150 non lette").assertExists()
    // Il disegno dice «99+» ma TalkBack non lo legge: il numero e' detto una volta, ed e' quello vero.
    compose.onNodeWithText("99+").assertDoesNotExist()
  }

  @Test
  fun `the dot does not grow with text but the number does`() {
    var dotWidth = 0f
    var numberWidth1 = 0f
    var numberWidth2 = 0f
    fun measure(scale: Float, record: (Float, Float) -> Unit) {
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          val density = LocalDensity.current
          CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = scale)) {
            Column {
              FluidBadge(contentDescription = "novita'", modifier = Modifier.testTag("dot"))
              FluidBadge(count = 99, contentDescription = "99 non lette", modifier = Modifier.testTag("num"))
            }
          }
        }
      }
      compose.waitForIdle()
      record(
        compose.onNodeWithTag("dot").getBoundsInRoot().width.value,
        compose.onNodeWithTag("num").getBoundsInRoot().width.value,
      )
    }
    measure(1f) { dot, number ->
      dotWidth = dot
      numberWidth1 = number
    }
    compose.activityRule.scenario.recreate()
    measure(2f) { dot, number ->
      assertEquals("il pallino non cresce", dotWidth, dot, 0.01f)
      numberWidth2 = number
    }
    assertEquals(8f, dotWidth, 0.01f)
    assertTrue("il numero cresce col testo: $numberWidth1 -> $numberWidth2", numberWidth2 > numberWidth1)
  }

  // --- Selezione: stato, back, ricreazione ---------------------------------------------------------------

  @Test
  fun `back leaves the selection first and the page only the second time`() {
    var pageBacks = 0
    lateinit var state: FluidSelectionState<Int>
    show {
      state = rememberFluidSelectionState()
      // Come in un'app: il back della pagina e' registrato prima, quello della selezione dopo.
      BackHandler { pageBacks++ }
      FluidSelectionBackHandler(state)
    }

    compose.runOnUiThread { state.toggle(1) }
    compose.waitForIdle()
    assertTrue(state.isActive)

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertFalse("il back ha svuotato la selezione", state.isActive)
    assertEquals("e non e' arrivato alla pagina", 0, pageBacks)

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals("senza selezione il back e' della pagina", 1, pageBacks)
  }

  @Test
  fun `back calls onExit after clearing`() {
    var exits = 0
    lateinit var state: FluidSelectionState<Int>
    show {
      state = rememberFluidSelectionState()
      FluidSelectionBackHandler(state, onExit = { exits++ })
    }
    compose.runOnUiThread { state.selectAll() }
    compose.waitForIdle()

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals(1, exits)
    assertFalse(state.isActive)
  }

  @Test
  fun `an open menu closes before the selection - menu, then selection, then page`() {
    var pageBacks = 0
    var picked = ""
    lateinit var state: FluidSelectionState<Int>
    showInModalHost {
      state = rememberFluidSelectionState()
      BackHandler { pageBacks++ }
      FluidSelectionBackHandler(state)
      Box(Modifier.fillMaxSize()) {
        FluidBarAction(
          contentDescription = "Account",
          onClick = {},
          actions = { listOf(FluidContextAction(label = "Bruno", onClick = { picked = "Bruno" })) },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).testTag("bar"),
        ) { FluidAvatar(name = "Anna Rossi") }
      }
    }
    compose.runOnUiThread { state.toggle(1) }
    compose.waitForIdle()

    compose.onNodeWithTag("bar").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Bruno").assertExists()

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertTrue("il primo back chiude il menu, non la selezione", state.isActive)
    assertEquals(0, pageBacks)

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertFalse("il secondo svuota la selezione", state.isActive)
    assertEquals(0, pageBacks)
    assertEquals("", picked)
  }

  @Test
  fun `the selection survives a recreation of the activity - explicit and all-except`() {
    var state: FluidSelectionState<String>? = null
    val content: @Composable () -> Unit = {
      FluidTheme(settings = remember { EngineSettings() }) {
        val s = rememberFluidSelectionState<String>()
        state = s
        Box(Modifier.fillMaxSize())
      }
    }
    compose.setContent(content)
    compose.waitForIdle()
    compose.runOnIdle {
      state!!.select("a")
      state!!.select("b")
    }
    compose.waitForIdle()

    compose.activityRule.scenario.recreate()
    compose.setContent(content)
    compose.waitForIdle()
    compose.runOnIdle {
      assertTrue(state!!.isSelected("a"))
      assertTrue(state!!.isSelected("b"))
      assertEquals(2, state!!.countIn(0))
      state!!.selectAll(snapshot = "inbox@42")
      state!!.deselect("x")
    }
    compose.waitForIdle()

    compose.activityRule.scenario.recreate()
    compose.setContent(content)
    compose.waitForIdle()
    compose.runOnIdle {
      assertTrue(state!!.isAllExcept)
      assertEquals("inbox@42", state!!.snapshot)
      assertFalse(state!!.isSelected("x"))
      assertTrue(state!!.isSelected("y"))
      assertEquals(49_999, state!!.countIn(50_000))
    }
  }

  // --- Barra laterale annidata ---------------------------------------------------------------------

  @Test
  fun `nested rows are indented by 16 dp a level and keep their selected state and click`() {
    var clicked = ""
    show {
      Column {
        FluidSidebarRow("Radice", selected = false, onClick = { clicked = "radice" }, modifier = Modifier.testTag("l0"))
        FluidSidebarRow("Figlia", selected = true, onClick = { clicked = "figlia" }, level = 1, modifier = Modifier.testTag("l1"))
        FluidSidebarRow("Nipote", selected = false, onClick = { clicked = "nipote" }, level = 2, modifier = Modifier.testTag("l2"))
      }
    }

    val x0 = compose.onNodeWithText("Radice", useUnmergedTree = true).getBoundsInRoot().left
    val x1 = compose.onNodeWithText("Figlia", useUnmergedTree = true).getBoundsInRoot().left
    val x2 = compose.onNodeWithText("Nipote", useUnmergedTree = true).getBoundsInRoot().left
    assertEquals(16f, (x1 - x0).value, 0.5f)
    assertEquals(32f, (x2 - x0).value, 0.5f)
    // Il rientro non toglie larghezza alla riga: il velo di selezione la copre tutta.
    assertEquals(
      compose.onNodeWithTag("l0").getBoundsInRoot().width.value,
      compose.onNodeWithTag("l2").getBoundsInRoot().width.value,
      0.5f,
    )
    compose.onNodeWithTag("l1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    compose.onNodeWithTag("l2").performClick()
    compose.waitForIdle()
    assertEquals("nipote", clicked)
  }

  @Test
  fun `the default sidebar row stays 44 dp and the comfortable one is 48`() {
    show {
      Column {
        FluidSidebarRow("Standard", selected = false, onClick = {}, icon = Icons.Rounded.Folder, modifier = Modifier.testTag("std"))
        FluidSidebarRow(
          "Comoda",
          selected = false,
          onClick = {},
          icon = Icons.Rounded.Folder,
          minHeight = FluidSidebarDefaults.ComfortableRowHeight,
          modifier = Modifier.testTag("comfy"),
        )
      }
    }

    compose.onNodeWithTag("std").assertHeightIsEqualTo(FluidSidebarDefaults.RowHeight)
    compose.onNodeWithTag("comfy").assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun `a sidebar row grows with text at 200 percent and a level past the cap stops moving`() {
    show {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        Column {
          FluidSidebarRow("Normale", selected = false, onClick = {}, modifier = Modifier.testTag("r"))
          FluidSidebarRow("Sesto", selected = false, onClick = {}, level = 6)
          FluidSidebarRow("Decimo", selected = false, onClick = {}, level = 10)
        }
      }
    }

    assertTrue(
      "col testo grande la riga e' piu' alta di 44 dp",
      compose.onNodeWithTag("r").getBoundsInRoot().height > FluidSidebarDefaults.RowHeight,
    )
    assertEquals(
      compose.onNodeWithText("Sesto", useUnmergedTree = true).getBoundsInRoot().left.value,
      compose.onNodeWithText("Decimo", useUnmergedTree = true).getBoundsInRoot().left.value,
      0.5f,
    )
  }

  @Test
  fun `a leading content replaces the tile and a section can carry an avatar and a detail`() {
    show {
      Column {
        FluidSidebarSection(
          title = "anna.rossi@example.com",
          uppercase = false,
          leading = { Text("AVATAR") },
          detail = "12",
        )
        FluidSidebarSection(title = "Materie")
        FluidSidebarRow(
          label = "Con avatar",
          selected = false,
          onClick = {},
          icon = Icons.Rounded.Folder,
          leading = { Text("LEAD") },
        )
      }
    }

    compose.onNodeWithText("AVATAR").assertExists()
    compose.onNodeWithText("12").assertExists()
    // Senza maiuscole: un indirizzo non va in maiuscolo.
    compose.onNodeWithText("anna.rossi@example.com").assertExists()
    // Il titolo di sempre e' ancora in maiuscolo.
    compose.onNodeWithText("MATERIE").assertExists()
    compose.onNodeWithText("LEAD").assertExists()
  }

  // --- Azione della barra con un contenuto ----------------------------------------------------------

  @Test
  fun `a bar action with an avatar is one named button and the avatar is mute`() {
    var clicks = 0
    show {
      FluidBarAction(contentDescription = "Account di Anna", onClick = { clicks++ }, modifier = Modifier.testTag("bar")) {
        FluidAvatar(name = "Anna Rossi")
      }
    }

    compose.onNodeWithContentDescription("Account di Anna").assert(hasRole(Role.Button)).performClick()
    compose.waitForIdle()
    assertEquals(1, clicks)
    compose.onNodeWithText("AR").assertDoesNotExist()
    compose.onNodeWithTag("bar").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun `a bar action with an avatar opens its menu on tap and the menu item runs once`() {
    var clicks = 0
    val picked = mutableListOf<String>()
    showInModalHost {
      Box(Modifier.fillMaxSize()) {
        FluidBarAction(
          contentDescription = "Account",
          onClick = { clicks++ },
          actions = {
            listOf(
              FluidContextAction(label = "Anna Rossi", selected = true, onClick = { picked += "Anna" }),
              FluidContextAction(label = "Bruno Neri", onClick = { picked += "Bruno" }),
            )
          },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).testTag("bar"),
        ) { FluidAvatar(name = "Anna Rossi") }
      }
    }

    compose.onNodeWithTag("bar").performClick()
    compose.waitForIdle()

    compose.onNodeWithText("Bruno Neri").assertExists()
    assertEquals("il tocco apre il menu e non chiama onClick", 0, clicks)

    compose.onNodeWithText("Bruno Neri").performClick()
    compose.waitForIdle()
    assertEquals(listOf("Bruno"), picked)
    compose.onNodeWithText("Bruno Neri").assertDoesNotExist()
  }

  @Test
  fun `a bar action with an avatar opens its menu on a long press like the icon one does`() {
    val picked = mutableListOf<String>()
    var clicks = 0
    showInModalHost {
      Box(Modifier.fillMaxSize()) {
        FluidBarAction(
          contentDescription = "Account",
          onClick = { clicks++ },
          actions = { listOf(FluidContextAction(label = "Impostazioni", onClick = { picked += "set" })) },
          modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).testTag("bar"),
        ) { FluidAvatar(name = "Anna Rossi") }
      }
    }

    compose.onNodeWithTag("bar").performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    compose.onNodeWithTag("bar").performTouchInput { up() }
    compose.waitForIdle()

    compose.onNodeWithText("Impostazioni").assertExists()
    assertEquals(0, clicks)
  }

  @Test
  fun `the icon bar action is unchanged - same description, same click`() {
    var clicks = 0
    show { FluidBarAction(icon = Icons.Rounded.Star, contentDescription = "Preferito", onClick = { clicks++ }) }

    compose.onNodeWithContentDescription("Preferito").assert(hasRole(Role.Button)).performClick()
    compose.waitForIdle()
    assertEquals(1, clicks)
  }

  // --- Capsula con stato ------------------------------------------------------------------------------

  @Test
  fun `a capsule action with a state is a switch and one without stays a button`() {
    show {
      FluidFloatingCapsule(backdrop = rememberGlassBackdrop()) {
        FluidCapsuleAction(Icons.Rounded.FormatBold, "Grassetto", onClick = {}, selected = true)
        FluidCapsuleAction(Icons.Rounded.Folder, "Cartella", onClick = {}, selected = false)
        FluidCapsuleAction(Icons.Rounded.Star, "Stella", onClick = {})
      }
    }

    compose.onNodeWithContentDescription("Grassetto").assert(hasRole(Role.Switch)).assert(hasToggle(ToggleableState.On))
    compose.onNodeWithContentDescription("Cartella").assert(hasRole(Role.Switch)).assert(hasToggle(ToggleableState.Off))
    compose.onNodeWithContentDescription("Stella")
      .assert(hasRole(Role.Button))
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
  }

  @Test
  fun `a capsule switch flips on tap and keeps its 48 dp target`() {
    show {
      var bold by remember { mutableStateOf(false) }
      FluidFloatingCapsule(backdrop = rememberGlassBackdrop()) {
        FluidCapsuleAction(
          Icons.Rounded.FormatBold,
          "Grassetto",
          onClick = { bold = !bold },
          selected = bold,
          modifier = Modifier.testTag("bold"),
        )
      }
    }

    compose.onNodeWithTag("bold").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).assert(hasToggle(ToggleableState.Off))
    compose.onNodeWithTag("bold").performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("bold").assert(hasToggle(ToggleableState.On))
    compose.onNodeWithTag("bold").assertWidthIsEqualTo(48.dp)
  }

  // --- La demo ---------------------------------------------------------------------------------------

  @Test
  fun `the demo composes with all its states and the avatar count responds to select all`() {
    show {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        IdentityDemo()
      }
    }

    compose.onNodeWithTag("identity-demo").assertExists()
    compose.onNodeWithTag("identity-badge-zero").assertExists()
    compose.onNodeWithText("Nessuna selezione").assertExists()
    compose.onNodeWithText("Seleziona tutto").performScrollTo().performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("identity-selection-count").assertExists()
    compose.onNodeWithText("50000 selezionate").assertExists()
  }
}
