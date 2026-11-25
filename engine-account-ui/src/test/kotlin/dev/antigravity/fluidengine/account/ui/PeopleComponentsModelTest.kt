package dev.antigravity.fluidengine.account.ui

import dev.antigravity.fluidengine.account.PeopleRowEntry
import dev.antigravity.fluidengine.account.PeopleRowKind
import dev.antigravity.fluidengine.account.ShareKind
import dev.antigravity.fluidengine.account.SharedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** I modelli dei componenti delle persone, senza Compose: la fila, «Da X · ieri», le quattro voci, le parole cambiate. */
class PeopleComponentsModelTest {
  private fun person(i: Int) = PeopleRowEntry("id$i", PeopleRowKind.PERSON, "Persona $i", "P$i", null, false)
  private val devices = PeopleRowEntry("dev", PeopleRowKind.DEVICES, "I tuoi dispositivi", "", null, true)

  @Test
  fun `la fila - i tuoi dispositivi in cima, otto persone, poi Altri`() {
    val m = peopleRowModel(listOf(person(1)) + devices + (2..10).map(::person))
    assertEquals(PeopleRowKind.DEVICES, m.entries.first().kind)
    assertEquals(9, m.entries.size)
    assertTrue(m.hasMore)
    assertFalse(peopleRowModel(listOf(devices, person(1))).hasMore)
    assertTrue("senza nessuno la fila non c'è (P1)", peopleRowModel(emptyList()).entries.isEmpty())
  }

  @Test
  fun `«Da Alessio · ieri», e le quattro voci del tocco`() {
    val day = 86_400_000L
    val item = SharedItem("x", person(1).copy(name = "Alessio"), ShareKind.LIVE, "La spesa", "{}", receivedAt = 10 * day)
    assertEquals("Da Alessio · oggi", sharedSubtitle(item, 10 * day + 1000))
    assertEquals("Da Alessio · ieri", sharedSubtitle(item, 11 * day + 5))
    assertEquals("Da Alessio · 3 giorni fa", sharedSubtitle(item, 13 * day + 5))
    val actions = attributionLabels("Alessio")
    assertEquals(listOf(AttributionAction.REPLY, AttributionAction.OPEN_CONVERSATION, AttributionAction.REMOVE, AttributionAction.MUTE_SENDER), actions.keys.toList())
    assertEquals("Non mostrare più da Alessio in questa app", actions[AttributionAction.MUTE_SENDER])
  }

  @Test
  fun `le parole cambiate dicono chi ha rinnovato e che la verifica è da rifare`() {
    assertEquals(
      "Le parole con Marco sono cambiate perché Marco ha rinnovato le chiavi. Confrontatele di nuovo per tenere il segno «verificato».",
      wordsChangedText("Marco", byThem = true),
    )
    assertTrue(wordsChangedText("Marco", byThem = false).startsWith("Le parole con Marco sono cambiate perché hai rinnovato le tue chiavi"))
  }
}
