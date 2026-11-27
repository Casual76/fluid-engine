package dev.antigravity.fluidengine.ui.fluid

import android.content.Context
import android.content.ContextWrapper
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Il difetto 55 della review R2 (muse-f8-prime): un servizio di classificazione del testo disattivato o
 * guasto non deve far uscire l'eccezione, deve lasciare i dati delle regole locali. Il servizio si guasta
 * in tre punti diversi: chiedendolo al sistema, chiedendo la finestra massima, chiedendo i link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidDetectedTextClassifierTest {
  private val today = LocalDate.of(2025, 11, 27)
  private val kinds = setOf(FluidDetectedTextKind.Email, FluidDetectedTextKind.Link, FluidDetectedTextKind.Phone)
  private val app: Context get() = RuntimeEnvironment.getApplication()

  private fun classify(context: Context, text: String): List<String> = runBlocking {
    classifyFluidText(context, text, Locale.ITALY, today, kinds).map { it.text }
  }

  @Test fun aServiceThatCannotBeReachedLeavesTheLocalRules() {
    val broken = object : ContextWrapper(app) {
      override fun getSystemService(name: String): Any? =
        if (name == Context.TEXT_CLASSIFICATION_SERVICE) throw IllegalStateException("servizio guasto") else super.getSystemService(name)
    }
    assertEquals(listOf("anna@example.org"), classify(broken, "Scrivi ad anna@example.org per la gita."))
  }

  @Test fun aClassifierThatFailsOnItsLimitLeavesTheLocalRules() {
    app.getSystemService(TextClassificationManager::class.java).setTextClassifier(
      object : TextClassifier {
        override fun getMaxGenerateLinksTextLength(): Int = throw IllegalStateException("servizio guasto")
      },
    )
    assertEquals(listOf("bruno@example.org"), classify(app, "Scrivi a bruno@example.org per la cena."))
  }

  @Test fun aClassifierThatFailsOnTheLinksLeavesTheLocalRules() {
    app.getSystemService(TextClassificationManager::class.java).setTextClassifier(
      object : TextClassifier {
        override fun generateLinks(request: TextLinks.Request): TextLinks = throw IllegalStateException("servizio guasto")
      },
    )
    assertEquals(listOf("carla@example.org"), classify(app, "Scrivi a carla@example.org per il pranzo."))
  }
}
