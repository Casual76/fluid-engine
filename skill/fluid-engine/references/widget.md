# Widget Glance

`engine-widget` esiste perché un widget che risolve i colori per conto suo si stacca dall'app la
prima volta che l'utente cambia accento.

## Palette

```kotlin
val palette = engineWidgetPalette(context, settings, brand)   // gli STESSI settings del tema
```

Risolve in anticipo entrambi i temi del launcher come coppie giorno/notte, perché una volta
consegnato il contenuto come RemoteViews non si decide più niente. Un tema fissato nell'app vince su
quello del launcher. Le trasparenze sono già composte sopra la superficie su cui staranno: il
genitore di un widget è il wallpaper, non la superficie dell'app.

Toni: `primaryTone`, `secondaryTone`, `warningTone`, `dangerTone`, `successTone`, `neutralTone`.

## Budget di layout

```kotlin
val layout = resolveEngineWidgetLayout(LocalSize.current, hasFooter = counters.isNotEmpty())
```

Glance non ha una fase di misura: quello che non ci sta viene tagliato dal launcher, a metà riga,
senza avvisare. Il budget traduce la dimensione della cella in `rowLimit`, `showSubtitle`,
`showFooter`, `compact`, `padding`. Le costanti sono in `EngineWidgetMetrics`.

Passa `hasFooter` davvero: un piede senza niente da dire non va disegnato, e lo spazio deve tornare
alle righe.

## Componenti

`EngineWidgetSurface`, `EngineWidgetHeader`, `EngineWidgetActionButton`, `EngineWidgetGroup`,
`EngineWidgetRow`, `EngineWidgetHairline`, `EngineWidgetIconTile`, `EngineWidgetPill`,
`EngineWidgetMessage`. Radii in `EngineWidgetShape`.

Le icone sono `@DrawableRes` tinti con il colore del tono: usa vettoriali **a tratto**, non glifi
pieni — accanto a un testo di peso iOS un glifo pieno legge molto più pesante.

## La trappola: il widget non segue il tema

Il widget si ridisegna quando cambiano i **dati** (se l'app lo invalida), non quando cambia
l'**aspetto**. Serve un collegamento esplicito, di solito nella `Application`:

```kotlin
applicationScope.launch {
  settingsFlow
    .map { listOf(it.themeMode, it.accentMode, it.customAccentName, it.dynamicColorEnabled, it.amoledEnabled) }
    .distinctUntilChanged()
    .drop(1)                       // il primo valore è quello già disegnato
    .collect { MioWidget().updateAll(context) }
}
```

Senza, cambiare accento non si vede sulla home fino alla sincronizzazione successiva — e sembra che
l'impostazione non abbia funzionato.

## Altre cose che sorprendono in Glance

- **`cornerRadius` funziona solo da Android 12.** Sotto, i widget restano squadrati: non affidare mai
  un significato alla sola forma.
- **I modificatori Glance sono un insieme di proprietà, non una catena ordinata.** `padding` e
  `background` sullo stesso elemento danno comunque uno sfondo a tutta larghezza: per rientrare una
  hairline serve un wrapper (è quello che fa `EngineWidgetHairline`).
- **RemoteViews accetta solo alcune classi.** In un layout XML usato come `initialLayout` o
  `previewLayout` non puoi usare `<View>`: usa `<FrameLayout>` per un separatore.
- **`ColorProvider(day, night)` sta in `androidx.glance.color`**, non in `androidx.glance.unit` (lì
  c'è solo la versione a colore singolo).
- **Un raggio maggiore della metà del lato più corto** non è "più tondo": è terreno indefinito per
  l'outline provider dell'host. Per un cerchio, `cornerRadius(diametro / 2)`.

## Cosa il kit non fa

Non decide cosa mostrare: modello, scelta degli elementi, deep link e privacy restano dell'app.
