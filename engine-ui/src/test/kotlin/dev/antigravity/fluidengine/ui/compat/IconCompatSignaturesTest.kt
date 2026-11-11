package dev.antigravity.fluidengine.ui.compat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/*
 * Le firme delle icone e delle barre nella 4.0 (piano «iOS in Android», F1A.11): cosa resta com'era e cosa si
 * aggiunge **accanto**, come lo scrive un'app.
 *
 * Perche' esiste. F1A.11 cambia di serie la taglia e l'inchiostro delle icone dell'engine senza cambiare una firma:
 * e' la condizione perche' un'app aggiorni senza toccare una riga. Le aggiunte sono tre, tutte in coda e con un
 * valore di serie: `FluidBarAction(…, tint)`, `FluidTabItem(…, selectedIcon)` e le API nuove `FluidIcon`,
 * `LocalFluidIconSize`, `FluidMetrics`. Le app compilano l'engine dai sorgenti, quindi la prova e' la compilazione:
 * se una di queste chiamate smette di compilare, il file non compila e il test fallisce prima di girare.
 *
 * Regole come in `Compat307SourceTest`: import da app (a stella sui pacchetti pubblici); le funzioni @Composable non
 * si eseguono, contano perche' compilano; i test qui sotto provano a quale parametro arriva cio' che conta.
 */

// --- Le forme della 3.x, che restano ------------------------------------------------------------------------

@Composable
internal fun BarAndCapsuleCallsAsIn3x(onClick: () -> Unit, icon: ImageVector) {
  // L'azione della barra: tutte le forme di prima compilano, e l'icona prende ora l'inchiostro del tema.
  FluidBarAction(icon, "Cerca", onClick)
  FluidBarAction(icon, null, onClick, Modifier, true, null, FluidMenuOpensOn.LongPress)
  FluidBarAction(icon = icon, contentDescription = "Altro", onClick = onClick, actions = { emptyList() })
  // Con un contenuto suo: un'icona tinta dall'app resta tinta (i tasti colorati restano possibili).
  FluidBarAction(contentDescription = "Preferito", onClick = onClick) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
  }
  // Il tasto di vetro con l'icona scritta dall'app, taglia compresa: resta com'era.
  FluidGlassIconButton(onClick = onClick) { Icon(icon, "Condividi", Modifier, MaterialTheme.colorScheme.primary) }
  // La capsula: l'accento chiesto a mano resta l'accento.
  FluidCapsuleAction(icon, "Preferito", onClick, tint = MaterialTheme.colorScheme.primary)
  FluidCapsuleAction(icon, "Preferito", onClick, Modifier, true, false, Color.Unspecified, null, 48.dp, FluidPressGrip.HoldsFinger, null)
}

// --- Le forme nuove della 4.0, accanto --------------------------------------------------------------------

@Composable
internal fun IconCallsIn40(onClick: () -> Unit, icon: ImageVector) {
  // FluidIcon: per posizione, per nome, con la taglia o il colore scritti.
  FluidIcon(FluidIcons.Share, "Condividi")
  FluidIcon(Icons.Rounded.Share, "Condividi", Modifier)
  FluidIcon(icon, null, Modifier, MaterialTheme.colorScheme.primary)
  FluidIcon(icon, null, Modifier, Color.Unspecified, 20.dp)
  FluidIcon(imageVector = FluidIcons.ArrowBackIosNew, contentDescription = "Indietro", size = FluidMetrics.BackIconSize)
  FluidIcon(imageVector = icon, contentDescription = null, tint = Color.Black)
  // La taglia d'ambiente, fornita da un contenitore dell'app.
  CompositionLocalProvider(LocalFluidIconSize provides 20.dp) { FluidIcon(icon, null) }
  val here: Dp = LocalFluidIconSize.current
  FluidIcon(icon, null, size = here)

  // L'azione della barra con un colore suo, in coda: per nome, e per posizione dopo `opensOn`.
  FluidBarAction(icon = icon, contentDescription = "Elimina", onClick = onClick, tint = MaterialTheme.colorScheme.error)
  FluidBarAction(icon, "Componi", onClick, Modifier, true, null, FluidMenuOpensOn.LongPress, MaterialTheme.colorScheme.primary)

  // Dentro i tasti dell'engine l'icona non scrive la sua taglia.
  FluidGlassIconButton(onClick = onClick) { FluidIcon(FluidIcons.Share, "Condividi") }
  FluidGlassIconButton(onClick = onClick, size = 76.dp) { FluidIcon(FluidIcons.Filled.PlayArrow, "Riproduci") }
  FluidGlassButton(text = "Condividi", onClick = onClick, leading = { FluidIcon(FluidIcons.Share, null) })
}

internal fun metricsAsAppsReadThem(): List<Any> = listOf(
  FluidMetrics.GlassButtonLens,
  FluidMetrics.TouchTarget,
  FluidMetrics.ScreenEdgeMargin,
  FluidMetrics.GlassSpacing,
  FluidMetrics.GroupSpacing,
  FluidMetrics.IconLensRatio,
  FluidMetrics.IconSize,
  FluidMetrics.BarIconSize,
  FluidMetrics.BackIconSize,
  FluidMetrics.CapsuleIconSize,
  FluidMetrics.TabBarIconSize,
  FluidMetrics.MenuIconSize,
  FluidMetrics.TabBarHeight,
  FluidMetrics.TabBarLabelSize,
  FluidMetrics.TabBarLabelLineHeight,
  FluidMetrics.CompactTitleInset,
  FluidMetrics.iconSizeIn(44.dp),
  FluidGlassSize.Regular.iconSize,
)

class IconCompatSignaturesTest {

  @Test
  fun `FluidTabItem - per posizione con tre e quattro argomenti come prima, la gemella piena in coda`() {
    val three = FluidTabItem("home", "Home", Icons.Rounded.Home)
    val four = FluidTabItem("home", "Home", Icons.Rounded.Home, FluidTabBadge.Dot)
    assertNull(three.selectedIcon)
    assertNull(four.selectedIcon)
    assertEquals(FluidTabBadge.Dot, four.badge)

    val five = FluidTabItem("home", "Home", FluidIcons.Home, null, FluidIcons.Filled.Home)
    val named = FluidTabItem(route = "home", label = "Home", icon = FluidIcons.Home, selectedIcon = FluidIcons.Filled.Home)
    assertEquals(five, named)
    assertSame(FluidIcons.Filled.Home, named.selectedIcon)
    // La destrutturazione di prima (quattro componenti) resta quella.
    val (route, label, icon, badge) = four
    assertEquals(listOf("home", "Home", Icons.Rounded.Home, FluidTabBadge.Dot), listOf(route, label, icon, badge))
  }

  @Test
  fun `le misure si leggono come costanti`() {
    assertEquals(18, metricsAsAppsReadThem().size)
    assertEquals(26.dp, FluidGlassSize.Regular.iconSize)
  }
}
