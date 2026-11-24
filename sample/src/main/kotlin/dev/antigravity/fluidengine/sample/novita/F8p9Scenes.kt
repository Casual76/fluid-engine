package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidDetectedText
import dev.antigravity.fluidengine.ui.fluid.FluidDetectedTextKind
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextDetection
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import java.util.Locale

internal val F8p9Scenes = listOf(
  NewElementScene("dati-nel-testo", "L’avviso di classe", "Date, orari, indirizzi e contatti in testo semplice") { NoticeScene(it) },
  NewElementScene("dati-mail-markdown", "La mail in Markdown", "Un appuntamento riconosciuto e un link scritto dall’autore") { MailScene(it) },
)

internal val F8p9TryItems = listOf(
  TryItem(
    "f8-9-dati", TryArea.FieldsEditors, "Dati in un avviso",
    "Tocca data, indirizzo e telefono per aprire il menu, prova «Mostra nell’agenda», quindi seleziona una parola senza sottolineatura.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › L’avviso di classe"),
  ),
  TryItem(
    "f8-9-markdown", TryArea.FieldsEditors, "Dati in Markdown",
    "Apri il link Agenda pubblica e tieni premuti orario e mail per vederne le azioni anche sui fondi con foto e con movimento ridotto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La mail in Markdown"),
  ),
)

private const val Notice = "Per l’uscita di venerdì prossimo ci troviamo alle 8:15 in via Roma 12.\n\n" +
  "Per informazioni: tel. +39 02 5550 1234, segreteria@example.org oppure https://example.org/uscita.\n\n" +
  "Portate il quaderno. L’importo è 7,50 € e il codice della circolare è 381204."

@Composable
private fun NoticeScene(onBack: () -> Unit) {
  var task by rememberSaveable { mutableStateOf<String?>(null) }
  DetectionScene("L’avviso di classe", onBack) {
    FluidDetectedText(
      text = Notice,
      detection = FluidTextDetection(locale = Locale.ITALY) { value ->
        if (value.kind == FluidDetectedTextKind.Date) {
          listOf(FluidContextAction("Aggiungi ai compiti") { task = value.text })
        } else emptyList()
      },
      color = MaterialTheme.colorScheme.onSurface,
    )
    task?.let {
      Text("Compito aggiunto: $it", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun MailScene(onBack: () -> Unit) {
  DetectionScene("La mail in Markdown", onBack) {
    FluidMarkdown(
      markdown = "**La riunione** è domani alle 8:30. Scrivi a segreteria@example.org per confermare.\n\n" +
        "Apri l’[Agenda pubblica](https://example.org/calendario). Il modello conserva il codice `ore 15:30`.",
      selectable = true,
      detection = FluidTextDetection(
        kinds = setOf(FluidDetectedTextKind.Date, FluidDetectedTextKind.Time, FluidDetectedTextKind.Email),
        locale = Locale.ITALY,
      ),
    )
  }
}

private enum class TextBackdrop(val label: String) { Page("Pagina"), Light("Foto chiara"), Dark("Foto scura") }

@Composable
private fun DetectionScene(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
  var ground by rememberSaveable { mutableStateOf(TextBackdrop.Page) }
  FluidScreen(title = title, subtitle = "F8.9 · Dati nel testo", onBack = onBack) {
    item(key = "fondo") {
      FluidSegmentedControl(
        options = TextBackdrop.entries,
        selected = ground,
        onSelect = { ground = it },
        modifier = Modifier.fillMaxWidth(),
        label = { it.label },
      )
    }
    item(key = "testo") {
      if (ground == TextBackdrop.Page) {
        Column(Modifier.padding(vertical = 16.dp)) { content() }
      } else {
        val photo = rememberGlassBackdrop()
        val shape = ContinuousCornerShape(FluidRadius.Group)
        Box(Modifier.fillMaxWidth().clip(shape)) {
          Image(
            painter = painterResource(if (ground == TextBackdrop.Light) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().glassBackdropSource(photo),
          )
          Column(
            Modifier.padding(horizontal = 12.dp, vertical = 28.dp)
              .fillMaxWidth()
              .fluidGlassPanel(photo, GlassDefaults.modalTint(), shape, GlassRole.Modal)
              .padding(16.dp),
          ) { content() }
        }
      }
    }
  }
}
