# 05 · Widget

`engine-widget` serve a una cosa sola: un widget della home che non sembri di un'altra app.

Il problema è concreto. Un widget Glance risolve i colori una volta, quando viene costruito, e resta lì finché qualcosa non gli chiede di ridisegnarsi. Se quei colori li ha scritti a mano, la prima volta che l'utente cambia accento il widget resta indietro — e una home che mostra la palette del mese scorso è il modo più visibile in cui un'app sembra abbandonata.

## La palette

```kotlin
val palette = engineWidgetPalette(context, settings, brand)
```

`engineWidgetPalette` prende gli **stessi** `EngineSettings` del tema e ne ricava una palette Glance:

- entrambi i temi del launcher sono risolti in anticipo, come coppie giorno/notte, perché una volta consegnato il contenuto come RemoteViews non si decide più niente;
- un tema fissato nell'app vince su quello del launcher: con l'app forzata su Dark, il widget resta scuro su una home chiara;
- il colore dinamico è supportato dove esiste (Android 12+);
- le trasparenze sono già composte sopra la superficie su cui staranno, perché il genitore di un widget è lo sfondo del launcher, non la superficie dell'app: un'alfa pensata per ammorbidire una piastrella sopra una card lascerebbe passare il wallpaper.

I toni disponibili sono gli stessi del design system: `primaryTone`, `secondaryTone`, `warningTone`, `dangerTone`, `successTone`, `neutralTone`.

## Il budget

```kotlin
val layout = resolveEngineWidgetLayout(LocalSize.current, hasFooter = counters.isNotEmpty())
```

Glance non ha una fase di misura a cui appoggiarsi: quello che non ci sta viene tagliato dal launcher, a metà riga, senza avvisare. Il budget traduce la dimensione della cella in un numero di righe, e dice se ci stanno il sottotitolo e il piede. Le costanti sono in `EngineWidgetMetrics`.

`hasFooter` va passato, non dedotto: un piede che non ha niente da dire non va disegnato, e lo spazio deve tornare alle righe.

## I componenti

```kotlin
EngineWidgetSurface(palette, layout, onClick = openApp) {
  EngineWidgetHeader(
    title = "Oggi",
    subtitle = "Aggiornato 07:45",
    palette = palette,
    layout = layout,
    trailing = {
      EngineWidgetActionButton(
        icon = R.drawable.ic_refresh,
        contentDescription = "Aggiorna",
        action = actionRunCallback<RefreshAction>(),
        palette = palette,
        layout = layout,
      )
    },
  )
  Spacer(GlanceModifier.height(EngineWidgetMetrics.Gap))

  EngineWidgetGroup(palette) {
    items.take(layout.rowLimit).forEachIndexed { index, item ->
      if (index > 0) EngineWidgetHairline(palette, layout)
      EngineWidgetRow(
        title = item.title,
        subtitle = item.subtitle,
        trailing = item.dateLabel,
        icon = item.iconRes,
        tone = palette.warningTone,
        palette = palette,
        layout = layout,
        onClick = openDeepLink(item),
      )
    }
  }

  if (layout.showFooter) {
    Spacer(GlanceModifier.height(EngineWidgetMetrics.Gap))
    Row(GlanceModifier.fillMaxWidth()) {
      EngineWidgetPill("3", "Voti", palette.primaryTone, GlanceModifier.defaultWeight())
    }
  }
}
```

`EngineWidgetMessage` è la card per quando non c'è niente da elencare: ha la forma di una riga, non di un errore, perché la maggior parte delle volte non lo è.

## Le icone

I componenti prendono un `@DrawableRes`, e li disegnano tinti con il colore del tono. Conviene che siano vettoriali a tratto, non glifi pieni: accanto a un testo di peso iOS un'icona Material piena legge molto più pesante a parità di dimensione.

## Ridisegnare quando cambia l'aspetto

Il dato che cambia fa già ridisegnare il widget. L'aspetto no: va collegato a mano, una volta sola, di solito nella `Application`.

```kotlin
applicationScope.launch {
  settingsFlow
    .map { it.appearanceKey() }   // solo i campi che riguardano il tema
    .distinctUntilChanged()
    .drop(1)                       // il primo valore è quello già disegnato
    .collect { MioWidget().updateAll(context) }
}
```

Senza questo, cambiare accento nelle impostazioni non si vede sulla home fino alla sincronizzazione successiva — e a quel punto sembra che l'impostazione non abbia funzionato, non che sia in ritardo.

## Cosa il kit non fa

Non decide cosa mostrare. Il modello, la scelta degli elementi, i deep link, la privacy (cosa mostrare a chi guarda la home) restano dell'app: sono esattamente le cose che cambiano da un'app all'altra.
