package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * La data di oggi, che resta oggi.
 *
 * `remember { LocalDate.now() }` sembra innocuo e non lo e': la composizione sopravvive al
 * passaggio in background, quindi il valore si congela al primo disegno e dopo mezzanotte la
 * schermata continua a chiamare "oggi" il giorno prima — il calendario evidenzia la casella
 * sbagliata e un'intestazione che dice "Oggi" mostra le cose di ieri.
 *
 * Qui il giorno si rilegge in due momenti: quando l'app torna in primo piano (il caso normale, il
 * telefono e' rimasto in tasca tutta la notte) e allo scoccare della mezzanotte successiva (il caso
 * di chi tiene la schermata aperta). In mezzo non c'e' nessun timer che gira.
 */
@Composable
fun rememberCurrentDate(): LocalDate {
  var today by remember { mutableStateOf(LocalDate.now()) }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val now = LocalDate.now()
        if (now != today) today = now
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(today) {
    // Un secondo oltre la mezzanotte: l'orologio del sistema e quello della coroutine non sono lo
    // stesso orologio, e svegliarsi un istante prima significherebbe rileggere ancora ieri.
    val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
    delay(untilMidnight.toMillis().coerceAtLeast(0L) + 1_000L)
    today = LocalDate.now()
  }

  return today
}
