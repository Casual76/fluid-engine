# engine-widget

Il kit per un **widget della home** che non sembri di un'altra app: la palette derivata dalle stesse impostazioni del tema, i componenti Glance (superficie, intestazione, righe, pillole, messaggio) e il **budget di
layout** che traduce la dimensione della cella in quante righe ci stanno. Pacchetto `dev.antigravity.fluidengine.widget`, tre file (`EngineWidgetPalette.kt`, `EngineWidgetLayout.kt`, `EngineWidgetComponents.kt`).

Esiste perche' un widget che risolve i colori per conto suo si stacca dall'app la prima volta che l'utente cambia accento, e una home che mostra la palette del mese scorso e' il modo piu' visibile in cui un'app sembra abbandonata.
Dipende da `engine-ui` (la palette e' lo schema colori che l'app disegna, non una seconda copia) e da `engine-foundation` (`EngineSettings`). **Il kit non decide cosa mostrare**: il modello, la scelta delle righe, i deep link e la privacy sono dell'app.
Modello mentale: **Glance consegna RemoteViews, e dopo la consegna non si decide piu' niente** — quindi tutto cio' che dipende dal tema o dallo spazio va deciso *prima* (palette a coppie giorno/notte, budget di righe) e va rifatto *esplicitamente* quando cambia.
Catalogo: [`../api/widget.md`](../api/widget.md); il tema che ne e' la fonte: [`../api/tema.md`](../api/tema.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| i colori di un widget **Glance** coerenti con l'app | `engineWidgetPalette(context, settings, brand)` — gli **stessi** `EngineSettings` del tema |
| quante righe ci stanno nella cella | `resolveEngineWidgetLayout(LocalSize.current, hasFooter = ...)` |
| la superficie, l'intestazione, le righe del widget | `EngineWidgetSurface`, `EngineWidgetHeader`, `EngineWidgetGroup` + `EngineWidgetRow` + `EngineWidgetHairline` |
| un contatore («3 · da fare») | `EngineWidgetPill` |
| un tasto tondo con un'icona (aggiorna) | `EngineWidgetActionButton` |
| «niente da mostrare» / «accedi» | `EngineWidgetMessage` (ha la forma di una riga, non di un errore) |
| un widget **RemoteViews** (non Glance) con i colori dell'app | `engineWidgetArgb(context, settings, launcherIsDark = ...)` |
| sapere se il widget e' scuro dato il launcher | `engineWidgetIsDark(settings, launcherIsDark)` (la regola, pubblica dalla 3.0.1) |
| un ruolo di colore Material che la palette non porta | `engineWidgetColorScheme(...)` (lo schema intero) |
| il widget che si ridisegna quando cambia l'accento | collegare `updateAll` alle impostazioni (**sezione sotto: senza, non succede**) |
| colori fissi scritti a mano nel widget | no: si stacca dall'app; usa la palette |
| un widget per l'orologio | non qui: `engine-wear` |

## La regola che sorprende: il widget non segue il tema da solo

Un widget Glance si ridisegna quando cambiano i **dati** (se l'app lo invalida), **non** quando cambia l'**aspetto**. Il motivo e' strutturale: `provideGlance` costruisce i contenuti, Glance li converte in `RemoteViews` e li consegna al launcher,
che li mostra cosi' finche' qualcuno non chiede un aggiornamento (`update`/`updateAll`, o il periodo del provider). La palette contiene le coppie giorno/notte, quindi il passaggio del **launcher** dal chiaro allo scuro e' coperto dalla coppia (su Android 12+ e' il launcher a scegliere; sotto dipende da Glance, da verificare su un dispositivo);
ma quando cambia **l'impostazione dell'app** (tema, accento, colore dinamico, nero assoluto) nessuno avvisa il widget. Serve un collegamento esplicito, di solito nella `Application` (`templates/EngineBootstrap.kt.txt`):

```kotlin
applicationScope.launch {
  settingsFlow()                                       // il flow delle EngineSettings dell'app
    .map { listOf(it.themeMode, it.accentMode, it.customAccentName, it.dynamicColorEnabled, it.amoledEnabled) }
    .distinctUntilChanged()
    .drop(1)                                           // il primo valore e' quello con cui il widget e' gia' disegnato
    .collect { MioWidget().updateAll(context) }        // MioWidget: la tua sottoclasse di GlanceAppWidget
}
```

Senza, cambiare accento non si vede sulla home fino alla sincronizzazione successiva — e sembra che l'impostazione non abbia funzionato. Tre cose da non sbagliare:
- il filtro elenca i **cinque** campi che cambiano i colori (non `hapticsEnabled`, che non ne cambia nessuno);
- `provideGlance` deve **rileggere** le impostazioni **a ogni chiamata** (`EngineSettingsStore(context).current()` o il tuo flow): se il widget le tiene in un campo della classe resta con quelle di quando e' nato;
- nessun collegamento osserva il **cambio di sfondo** del sistema: con `AccentMode.DYNAMIC` il colore e' quello al momento dell'ultimo ridisegno (letto dal codice: la palette chiama `dynamicLightColorScheme`/`dynamicDarkColorScheme` quando viene costruita).

## La palette: `engineWidgetPalette`, `EngineWidgetPalette`, `EngineWidgetTone`

**Cos'e'.** Ogni colore che un widget disegna, **gia' risolto per entrambi i temi del launcher** come coppia giorno/notte (`androidx.glance.color.ColorProvider`). I valori traslucidi sono **composti in anticipo** sulla superficie su cui staranno: il genitore di un
widget e' lo sfondo del launcher e non la superficie dell'app, quindi un'alfa pensata per ammorbidire una piastrella sopra una card lascerebbe passare il wallpaper.

**Firma essenziale.**

```kotlin
fun engineWidgetPalette(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand): EngineWidgetPalette

data class EngineWidgetPalette(
  val background, card, hairline, onSurface, onSurfaceVariant, accent, accentContainer, onAccentContainer, attention: ColorProvider,
  val primaryTone, secondaryTone, warningTone, dangerTone, successTone, neutralTone: EngineWidgetTone,
)
data class EngineWidgetTone(val container: ColorProvider, val content: ColorProvider)   // sfondo + contenuto leggibile
```

(I campi sono tutti `ColorProvider`/`EngineWidgetTone`: l'elenco sopra e' compresso, le firme complete sono nel [catalogo](../api/widget.md).)

**Uso.**

```kotlin
val settings = EngineSettingsStore(context).current()      // o le tue impostazioni mappate su EngineSettings
val palette = engineWidgetPalette(context, settings)       // brand: il colore del marchio dell'app, se non e' quello di serie
```

**Comportamento.**
- **Un tema fissato nell'app vince su quello del launcher**: con l'app forzata su `DARK`, il widget resta scuro su una home chiara — come ogni altra superficie di quell'app, ed e' cio' che fa leggere il widget come parte di essa. `SYSTEM` segue il launcher.
  Con `LIGHT`/`DARK`/`AMOLED` fissati le due meta' della coppia giorno/notte sono **uguali**.
- **Toni** (contenitore + contenuto leggibile): `primaryTone`/`secondaryTone`/`neutralTone` sono ruoli Material (`primaryContainer`/`onPrimaryContainer`, `secondaryContainer`/…, `surfaceContainerHigh`/`onSurfaceVariant`);
  `dangerTone` e' l'errore del tema in tinta (16% sulla card); `warningTone` e `successTone` sono **coppie fisse** del design system perche' non hanno un ruolo Material (avviso arancio `#E65100`/`#FFB74D`, successo verde `#1B5E20`/`#81C784`, chiaro/scuro), anch'essi al 16% sulla card.
  I toni sono **coppie**, non colori sciolti: un widget non ha un tema a cui ricadere, e una volta separati niente a valle sa piu' quale colore di contenuto andava con quale contenitore.
- `hairline` e' `onSurface` al 10% composto sulla card; `card` e' `surfaceContainerLow`; `attention` e' `error`.
- Colore dinamico: solo con `dynamicColorEnabled`, `accentMode == AccentMode.DYNAMIC` **e** Android 12 o superiore; altrimenti il marchio.

**Trappole.** Passa al widget gli stessi `EngineSettings` (e lo stesso `brand`) che passi a `FluidTheme`: una palette costruita con `EngineSettings()` di serie mostra il tema di serie, non quello scelto dall'utente.

### `engineWidgetIsDark`, `engineWidgetColorScheme`, `engineWidgetArgb` (per i widget `RemoteViews`)

**Cos'e'.** Dalla 3.0.1, per un widget che **non** e' Glance (un `AppWidgetProvider` con `RemoteViews`): la stessa regola e gli stessi colori, come interi.

```kotlin
fun engineWidgetIsDark(settings: EngineSettings, launcherIsDark: Boolean): Boolean
fun engineWidgetColorScheme(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand, launcherIsDark: Boolean): ColorScheme
fun engineWidgetArgb(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand, launcherIsDark: Boolean): EngineWidgetArgb
data class EngineWidgetArgb(val background: Int, card, hairline, onSurface, onSurfaceVariant, accent, accentContainer, onAccentContainer, attention, warning, success: Int, val isDark: Boolean)
```

**Uso.**

```kotlin
val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
val c = engineWidgetArgb(context, settings, launcherIsDark = night)   // launcherIsDark e' l'ultimo parametro: va nominato
views.setTextColor(R.id.title, c.onSurface)
views.setInt(R.id.card, "setBackgroundColor", c.card)
// Da Android 12, con le due serie (launcherIsDark = false e true) si passa il giorno/notte al launcher:
// views.setColorInt(R.id.title, "setTextColor", chiaro.onSurface, scuro.onSurface)
```

**Comportamento.** `engineWidgetIsDark`: `SYSTEM` → `launcherIsDark`; `LIGHT` → `false`; `DARK` e `AMOLED` → `true` (e' **la** regola, usata da tutto il resto: una versione sola, per Glance e `RemoteViews`). `EngineWidgetArgb.isDark` riporta il risultato.
Gli interi sono gli stessi valori che la palette Glance consegna, con i traslucidi gia' composti sulla card; `warning` e `success` sono le coppie fisse, scelte per `isDark`. `engineWidgetColorScheme` dà lo schema Material completo, per un ruolo che la palette non porta.

**Trappole.** `RemoteViews` fissa i colori **quando lo costruisci**: dovrai ricostruirlo quando il launcher cambia tema e, come per Glance, quando cambia un'impostazione dell'app (chiedendo un aggiornamento del widget).
Sotto Android 12 non esiste la coppia giorno/notte: l'intero e' uno solo.

**Versione.** Dalla 3.0.1: `engineWidgetIsDark` e' la regola resa pubblica, `engineWidgetColorScheme` era il privato `widgetScheme`, `engineWidgetArgb` e' nuovo. `EngineWidgetArgbTest` li prova.

## Il budget di layout: `resolveEngineWidgetLayout`, `EngineWidgetLayout`, `EngineWidgetMetrics`

**Cos'e'.** Glance non ha una fase di misura a cui appoggiarsi: il contenuto diventa `RemoteViews` e **cio' che non ci sta viene tagliato dal launcher, a meta' riga, senza callback**. Il kit trasforma la dimensione della cella in un **budget esplicito** (intestazione, righe, piede)
e il contenuto chiede esattamente cio' che il budget permette.

**Firma essenziale.**

```kotlin
data class EngineWidgetLayout(val padding: Dp, val compact: Boolean, val rowLimit: Int, val showSubtitle: Boolean, val showFooter: Boolean)
fun resolveEngineWidgetLayout(size: DpSize, hasFooter: Boolean = false, maxRows: Int = EngineWidgetMetrics.MaxRows): EngineWidgetLayout
```

**Uso.**

```kotlin
override val sizeMode: SizeMode = SizeMode.Exact          // senza, LocalSize non e' la cella vera
// dentro provideContent:
val layout = resolveEngineWidgetLayout(LocalSize.current, hasFooter = contatori.isNotEmpty())
items.take(layout.rowLimit).forEachIndexed { i, item -> ... }
```

**Comportamento — i numeri veri** (da `EngineWidgetMetrics` e dalla funzione):
- **`compact`** se la larghezza e' sotto **240 dp** *o* l'altezza sotto **150 dp**. Compatta: padding **12**, intestazione **32**, riga **34** (un rigo piu' un valore a destra); non compatta: padding **14**, intestazione **48**, riga **48** (titolo 15 sp su sottotitolo 12 sp).
  `showSubtitle = !compact`. Piede **38 dp**, spazio fra le parti **8 dp**.
- Spazio per le righe = `altezza − 2·padding − intestazione − 8`. Il **piede si disegna solo se** `hasFooter` **e** l'avanzo e' almeno `riga + 8 + 38`; in tal caso si toglie `38 + 8` dall'avanzo.
- `rowLimit` = righe intere che ci stanno, **fra 1 e `maxRows`** (4 di serie: «oltre quattro righe un widget della home smette di essere da guardare e diventa una cattiva lista»). Se non ce ne sta nemmeno una, la prima si disegna lo stesso e il launcher la taglia: meglio di una card vuota.
- Alcuni esempi (senza piede / con piede):

  | cella (L × A) | modo | righe senza piede | righe con piede |
  |---|---|---|---|
  | 180 × 110 | compatta | 1 | 1 (il piede non entra) |
  | 180 × 180 | compatta | 3 | 2 |
  | 300 × 150 | normale | 1 | 1 (il piede non entra) |
  | 300 × 240 | normale | 3 | 2 |
  | 300 × 300 | normale | 4 | 3 |
  | 300 × 900 | normale | 4 (tetto) | 4 |

- `hasFooter` e' **chiesto, non dedotto**: un piede che non ha niente da dire non va disegnato, e lo spazio deve tornare alle righe — e questo e' cio' che succede solo se passi `false`.

**Trappole.**
- **Il budget non taglia niente da solo**: `rowLimit` e' un numero; sei tu a fare `take(layout.rowLimit)`. I componenti non lo applicano.
- **`hasFooter = true` senza disegnare il piede (o viceversa)** sbaglia il conto delle righe. Disegna il piede quando `layout.showFooter` e' vero, non quando hai dati.
- Il conto non include le hairline fra le righe (1 dp l'una): e' un limite superiore ragionevole, non una misura al pixel.
- Senza `SizeMode.Exact` (o `Responsive`) `LocalSize` non e' la dimensione reale della cella e il budget sbaglia.

**Test.** `EngineWidgetLayoutTest` (5): la cella piu' piccola tiene una riga e lascia cadere l'opzionale; l'altezza decide la compattezza come la larghezza; un piede prende lo spazio dalle righe; una cella piu' alta compra piu' righe; si ferma al tetto (e `maxRows = 2` vale).

## I componenti

Tutti `@Composable` Glance, tutti prendono `palette` (e quasi tutti `layout`) come parametri espliciti: nessuno li legge da un ambiente, perche' un widget non ne ha.

```kotlin
EngineWidgetSurface(palette, layout, onClick = apriLApp) {
  EngineWidgetHeader(
    title = "Elementi", subtitle = "Aggiornato 07:45", palette = palette, layout = layout,
    trailing = {
      EngineWidgetActionButton(icon = R.drawable.ic_refresh, contentDescription = "Aggiorna",
        action = actionRunCallback<RefreshAction>(), palette = palette, layout = layout)
    },
  )
  Spacer(GlanceModifier.height(EngineWidgetMetrics.Gap))
  EngineWidgetGroup(palette) {
    items.take(layout.rowLimit).forEachIndexed { i, item ->
      if (i > 0) EngineWidgetHairline(palette, layout)
      EngineWidgetRow(title = item.titolo, subtitle = item.dettaglio, trailing = item.etichetta,
        icon = item.icona, tone = palette.warningTone, palette = palette, layout = layout, onClick = apriElemento(item))
    }
  }
  if (layout.showFooter) {
    Spacer(GlanceModifier.height(EngineWidgetMetrics.Gap))
    Row(GlanceModifier.fillMaxWidth()) { EngineWidgetPill("3", "Da fare", palette.primaryTone, GlanceModifier.defaultWeight()) }
  }
}
```

| componente | cos'e' e cosa non dice il nome |
|---|---|
| `EngineWidgetSurface(palette, layout, onClick)` | La superficie esterna: sfondo dell'app, angolo **24 dp**, **un solo bersaglio di tocco dietro a tutto** (`onClick`: la porta d'ingresso dell'app — senza, si cerca il pixel che apre l'app) e padding `layout.padding`. Non aggiungere altro padding: l'ha gia'. Colonna in alto. |
| `EngineWidgetHeader(title, palette, layout, subtitle, subtitleIsWarning, trailing)` | Titolo (17 sp grassetto, 15 compatto), riga di stato opzionale (12 sp, **sparisce in modalita' compatta**, in tono `attention` se `subtitleIsWarning`), **una** azione a destra. |
| `EngineWidgetActionButton(icon, contentDescription, action, palette, layout)` | Tasto tondo, **tinto e non etichettato**: un'etichetta compete con il bersaglio della superficie per spazio e attenzione, e un widget ha posto per una sola azione primaria. 36 dp (30 compatto), icona 18 (15); ha `contentDescription` per l'accessibilita'. |
| `EngineWidgetGroup(palette) { }` | Le righe dentro una sola card arrotondata (**20 dp**), come un elenco raggruppato. |
| `EngineWidgetRow(title, palette, layout, icon, tone, subtitle, trailing, onClick)` | Una riga: tessera con icona, una o due righe di testo (15 sp, 13 compatto), un valore a destra come **`String`** (12 sp grassetto, nel colore del tono). La categoria sta **sulla tessera, non sullo sfondo**: tingere righe intere fa una toppa di blocchi colorati, e a quelle dimensioni non c'e' spazio per rimediare. Il sottotitolo si vede solo se `layout.showSubtitle`. |
| `EngineWidgetHairline(palette, layout)` | Il separatore **rientrato** fino a dove comincia il testo (52 dp, 42 compatto), 1 dp. E' un wrapper perche' i modificatori Glance sono un insieme di proprieta', non una catena ordinata. |
| `EngineWidgetIconTile(icon, tone, compact)` | La tessera della riga: 30 dp (24 compatta), angolo 9, icona 17 (14) tinta col contenuto del tono. Sola, per chi disegna una riga a modo suo. |
| `EngineWidgetPill(value, label, tone, modifier, compact)` | Un conteggio e cio' che conta; `modifier` e' parametro perche' la pillola e' quasi sempre disposta dal genitore (peso in una riga, un'azione di tocco sua). |
| `EngineWidgetMessage(message, palette, layout, icon, tone, detail)` | La card per «niente da elencare»: ha la forma di una riga e non di un errore, perche' quasi sempre non e' un errore (nulla in scadenza, nulla di non letto, non ancora acceduti). `detail` e' una riga in tono `attention`. Il messaggio sta in 2 righe. |
| `EngineWidgetShape` | I raggi: `Container` 24, `Group` 20, `Tile` 9, `Pill` 18 dp. |
| `engineWidgetTextStyle(color, size, weight)` | Glance non ha una scala tipografica da ereditare: i corpi sono dichiarati a ogni chiamata. La rampa del design system a distanza da widget: **17** titolo, **15** riga, **13** riga densa, **12** sottotitolo, **11** valore finale. |
| `EngineWidgetMetrics` | Le misure del budget (sopra). |

## Altre cose che sorprendono in Glance (e che il kit gia' gestisce)

- **`cornerRadius` funziona solo da Android 12.** Sotto, i widget restano squadrati: i colori portano l'identita' da soli, ed e' per questo che nessun componente affida un significato alla sola forma. Non affidarlo nemmeno tu.
- **I modificatori Glance non sono ordinati.** `padding` e `background` sullo stesso elemento danno comunque uno sfondo a tutta larghezza: per rientrare una hairline serve un wrapper (`EngineWidgetHairline`).
- **`RemoteViews` accetta solo alcune classi.** In un layout XML usato come `initialLayout` o `previewLayout` non puoi usare `<View>`: per un separatore usa `<FrameLayout>`.
- **`ColorProvider(day, night)` sta in `androidx.glance.color`**, non in `androidx.glance.unit` (li' c'e' solo la versione a colore singolo).
- **Un raggio maggiore della meta' del lato piu' corto non e' «piu' tondo»**: e' terreno indefinito per l'outline provider dell'host. Per un cerchio `cornerRadius(diametro / 2)` (e' cio' che fa `EngineWidgetActionButton`).
- **Le icone sono `@DrawableRes` tinte** col colore del tono: usa vettoriali **a tratto**, monocromatici. Un glifo pieno, accanto a testo di peso iOS, legge molto piu' pesante.

## Token, costanti, Defaults

| nome | valore |
|---|---|
| `EngineWidgetMetrics.RowHeight` / `CompactRowHeight` | 48 dp / 34 dp |
| `HeaderHeight` / `CompactHeaderHeight` | 48 dp / 32 dp |
| `FooterHeight`, `Gap` | 38 dp, 8 dp |
| `Padding` / `CompactPadding` | 14 dp / 12 dp |
| `EngineWidgetMetrics.MaxRows` | 4 |
| soglie di compattezza | larghezza < 240 dp o altezza < 150 dp |
| `EngineWidgetShape.Container`, `Group`, `Tile`, `Pill` | 24, 20, 9, 18 dp |
| alfa di tessere e toni (`TileAlpha`) / hairline (`HairlineAlpha`) | 0,16 / 0,10 (privati) |

## Cosa e' verificato dai test

- `EngineWidgetLayoutTest` (5): il budget. E' l'unica parte di un widget Glance che un test puo' tenere: sotto c'e' `RemoteViews` e l'host taglia in silenzio.
- `EngineWidgetArgbTest` (3): il tema dell'app vince su quello del launcher (e `SYSTEM` lo segue); gli interi sono i ruoli dello schema, con i traslucidi gia' composti sulla card; la serie scura ha i suoi valori fissi per avviso e successo.
  La derivazione (`engineWidgetArgbOf`) e' `internal` e separata dal `Context` per poterla provare senza un launcher.
- **Non verificato:** la palette Glance (`engineWidgetPalette`) e i componenti `EngineWidget*`: nessun test li disegna, ne' il comportamento col colore dinamico. Si guardano su un dispositivo, con un widget vero e il tema cambiato dall'app.

## Dove guardare

- `engine-widget/src/main/kotlin/dev/antigravity/fluidengine/widget/EngineWidgetPalette.kt` — `EngineWidgetPalette`, `EngineWidgetTone`, `engineWidgetPalette`, `engineWidgetIsDark`, `engineWidgetColorScheme`, `EngineWidgetArgb`/`engineWidgetArgb`, i colori fissi.
- `.../EngineWidgetLayout.kt` — `EngineWidgetLayout`, `EngineWidgetMetrics`, `resolveEngineWidgetLayout` (le soglie).
- `.../EngineWidgetComponents.kt` — i componenti, `EngineWidgetShape`, `engineWidgetTextStyle`.
- `templates/EngineBootstrap.kt.txt` — il collegamento `updateAll` alle impostazioni (punto 3).
- `skill/fluid-engine/references/widget.md`, `docs/05-widget.md` — la stessa materia in forma di ricetta.

## Dipendenze e configurazione minima

**Dipendenze del modulo.** `api` su `engine-foundation` e `engine-ui` (quindi Compose e Material 3 arrivano con lui), `androidx.glance:glance` e `glance-appwidget` (1.1.1 in `versions.gradle`). Applica il plugin Compose.
`tools/engine-install.ps1 -AppRoot . -Modules engine-widget` include foundation e ui. **Un'app senza il plugin Compose nel build root non puo' ospitarlo**: `engine-ui` non riesce nemmeno a configurarsi (vedi `docs/01-integrazione.md`). Nel `build.gradle`:

```groovy
implementation project(':engine-widget')   // widget Glance
```

**Configurazione minima.** Il modulo non porta manifest: la dichiarazione del widget e' dell'app. Servono (1) un `GlanceAppWidgetReceiver` nel manifest con il suo `appwidget-provider` XML; (2) una sottoclasse di `GlanceAppWidget` con
`sizeMode = SizeMode.Exact` che in `provideGlance` legge `EngineSettings` e costruisce palette e layout; (3) il collegamento `updateAll` della sezione sopra; (4) icone vettoriali a tratto.

```kotlin
class MioWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact
  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val settings = EngineSettingsStore(context).current()        // rilette a ogni chiamata
    provideContent {
      val palette = engineWidgetPalette(context, settings)
      val layout = resolveEngineWidgetLayout(LocalSize.current, hasFooter = false)
      EngineWidgetSurface(palette, layout, onClick = actionStartActivity<MainActivity>()) { /* intestazione, gruppo, righe */ }
    }
  }
}
```
