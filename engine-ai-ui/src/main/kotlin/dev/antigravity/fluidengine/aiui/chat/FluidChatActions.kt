package dev.antigravity.fluidengine.aiui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.markdown.FluidMarkdownParser
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.fluidPressable

/** Quanto sbiadisce un'azione spenta: si vede che c'e', e che adesso non si puo'. */
private const val DisabledAlpha = 0.38f

/**
 * Un'azione sotto un messaggio: quarantotto dp di bersaglio, il minimo di Android per un dito (erano
 * 32, poi 44), e icone leggibili, non un grigio al 55% che si perdeva sul fondo. Spenta, l'icona
 * sbiadisce e il tocco non passa.
 */
@Composable
fun FluidSmallAction(
  icon: ImageVector,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Box(
    modifier = modifier
      .size(48.dp)
      .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button, haptic = null),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp).alpha(if (enabled) 1f else DisabledAlpha),
    )
  }
}

/**
 * Un'azione con la parola accanto all'icona ("Riprova", "Dettagli"): sotto una risposta fallita
 * l'icona da sola non basta a dire cosa fare. La pillola e' bassa, il bersaglio del tocco resta di
 * quarantotto dp.
 */
@Composable
fun FluidPillAction(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Box(
    modifier = modifier
      .heightIn(min = 48.dp)
      .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button, haptic = null),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      Modifier
        .height(32.dp)
        .alpha(if (enabled) 1f else DisabledAlpha)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), FluidCapsuleShape)
        .padding(start = 10.dp, end = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}

/**
 * Mette [text] negli appunti. Da Android 13 la conferma la mostra il sistema; prima no, e il tocco
 * sembrava non fare niente, quindi sotto il 33 si mostra un avviso con [confirmation].
 */
fun fluidCopyToClipboard(context: Context, text: String, label: String = "Assistente", confirmation: String? = null) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
  if (Build.VERSION.SDK_INT < 33 && confirmation != null) Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}

/**
 * Condivide la risposta come testo semplice: gli asterischi e i cancelletti del Markdown in un
 * messaggio sono rumore ([FluidMarkdownParser.plainText] con i segni degli elenchi).
 */
fun fluidShareText(context: Context, markdown: String, chooserTitle: String) {
  val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, FluidMarkdownParser.plainText(markdown, includeCode = true, bullets = true))
  }
  runCatching { context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
