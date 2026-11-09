package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class FluidTokenFieldTest {
  private val create: (String, List<FluidToken>) -> FluidToken? = { raw, _ -> FluidToken(raw, raw, raw == "errore") }
  private fun update(text: String, all: Boolean = false, separators: Set<Char> = FluidTokenFieldDefaults.Separators) =
    FluidTokenFieldDefaults.update(FluidTokenFieldValue(), TextFieldValue(text), separators, all, create)

  @Test fun separatorsAndIncompleteDraft() {
    val value = update("uno,due;三\nbozza")
    assertEquals(listOf("uno", "due", "三"), value.tokens.map { it.label })
    assertEquals("bozza", value.draft.text)
  }
  @Test fun spaceIsOptIn() {
    assertEquals("nome composto", update("nome composto").draft.text)
    assertEquals(2, update("uno due ", separators = setOf(' ')).tokens.size)
  }
  @Test fun pasteThirtyItemsAndFinalRemainder() {
    assertEquals(30, update((1..30).joinToString(";") { "tag$it" }, true).tokens.size)
  }
  @Test fun compositionWaitsForConfirmation() {
    val composing = TextFieldValue("caffè,東京;", TextRange(9), TextRange(0, 9))
    val pending = FluidTokenFieldDefaults.update(FluidTokenFieldValue(), composing, createToken = create)
    assertTrue(pending.tokens.isEmpty())
    assertEquals(composing, pending.draft)
    assertEquals(2, FluidTokenFieldDefaults.update(pending, composing.copy(composition = null), createToken = create).tokens.size)
  }
  @Test fun backspaceIsTwoStepsAndTypingResetsSelection() {
    val value = update("uno;due;", true)
    val selected = FluidTokenFieldDefaults.backspace(value)
    assertEquals("due", selected.selectedTokenId)
    assertEquals(2, selected.tokens.size)
    assertEquals(1, FluidTokenFieldDefaults.backspace(selected).tokens.size)
    val typing = FluidTokenFieldDefaults.update(selected, TextFieldValue("x"), createToken = create)
    assertNull(typing.selectedTokenId)
    assertEquals(typing, FluidTokenFieldDefaults.backspace(typing))
  }
  @Test fun validationAndDuplicateRejectionDoNotLoseText() {
    val value = FluidTokenFieldDefaults.update(FluidTokenFieldValue(), TextFieldValue("uno;uno;errore;no;"),
      createToken = { raw, existing -> if (raw == "no" || existing.any { it.id == raw }) null else create(raw, existing) })
    assertEquals(listOf("uno", "errore"), value.tokens.map { it.id })
    assertTrue(value.tokens.last().isError)
    assertEquals("uno;no;", value.draft.text)
  }
  @Test fun saverRestoresStableIdsErrorDraftSelectionAndDropsComposition() {
    val original = FluidTokenFieldValue(listOf(FluidToken("id", "אבג", true)),
      TextFieldValue("caffè", TextRange(4, 1), TextRange(0, 4)), "id")
    val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
    val saved = with(FluidTokenFieldValue.Saver) { scope.save(original) }!!
    assertEquals(original.copy(draft = original.draft.copy(composition = null)), FluidTokenFieldValue.Saver.restore(saved))
  }
  @Test fun sentinelRoundtripKeepsReverseSelectionAndComposition() {
    val draft = TextFieldValue("日本語", TextRange(3, 1), TextRange(1, 3))
    assertEquals(draft, tokenDraftValue(tokenEditorValue(draft)))
  }
  @Test fun noSeparatorsAndBlankSegments() {
    assertTrue(update(", ;\n").tokens.isEmpty())
    assertEquals("uno,due", update("uno,due", separators = emptySet()).draft.text)
  }
}
