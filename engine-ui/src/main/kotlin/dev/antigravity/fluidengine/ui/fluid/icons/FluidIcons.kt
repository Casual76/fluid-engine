// Generato da tools/icons/gen_fluid_icons.py dall'elenco tools/icons/fluid-icons.txt: non modificare a mano.
// Sorgenti: Material Symbols Rounded, peso 500, google/material-design-icons@10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7 (2026-10-23),
// licenza Apache-2.0 (LICENSES/MaterialSymbols.md dell'engine). Un'icona in piu': una riga
// nell'elenco e `python tools/icons/gen_fluid_icons.py`; con `--check` dice se questo file e' rimasto indietro.

package dev.antigravity.fluidengine.ui.fluid.icons

/**
 * Le icone dell'engine: Material Symbols arrotondate a peso 500, al tratto.
 *
 * **Perche' un set proprio.** Le icone Material di Compose (`Icons.Rounded`) sono piene dove Apple
 * disegna al tratto, e la libreria e' ferma. Alessio ha scelto (2026-11-10, sulle prove di R6) icone
 * al tratto «un po' piu' grassettate, come Apple»: il peso 500 da' un tratto di circa 2,5 dp a 26 dp,
 * quanto la freccia indietro di iOS. Il peso e' del set, non un parametro: due pesi nella stessa barra
 * si vedono subito, e l'ingrossamento automatico di un vettore qualsiasi chiude i fori delle sagome.
 *
 * **Come si usa.** Ogni icona e' un `ImageVector` in un riquadro da 24, come quelle di Material: si
 * passa dove l'engine chiede `icon: ImageVector`, e `Icon` di Compose la tinge. Si importa per nome
 * (`import dev.antigravity.fluidengine.ui.fluid.icons.Delete`), lo stesso import porta anche la variante in [Filled]. Le icone che
 * nelle lingue da destra a sinistra devono girarsi (frecce, invio, annulla) lo fanno da sole: il
 * segno viene dalla sorgente di Google, non da una scelta nostra.
 *
 * **Quanto costa.** Ogni icona e' una proprieta' costruita al primo uso e tenuta: chi non la nomina
 * non la paga, e R8 toglie il suo tracciato dall'APK. Il set nasce da un generatore
 * (`tools/icons/gen_fluid_icons.py`) e un elenco (`tools/icons/fluid-icons.txt`); la tabella `tools/icons/corrispondenza.md` dice, per ogni icona
 * Material usata dalle app, qual e' l'equivalente qui.
 *
 * Un'app che vuole altre icone allo stesso peso lancia lo stesso generatore sul proprio elenco: le
 * icone escono come estensioni di [FluidIcons] in un pacchetto suo, e si scrivono allo stesso modo.
 */
object FluidIcons {
  /**
   * Le stesse sagome a riempimento pieno, solo per le icone che ne hanno bisogno: la scheda della
   * tab bar (in Apple la tab bar e' piena e le barre al tratto, R6), la forma «accesa» di uno stato
   * (preferito, scelto) e i comandi di riproduzione. Nelle barre si usa la forma al tratto.
   */
  object Filled
}
