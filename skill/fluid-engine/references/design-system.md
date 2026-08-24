# Inventario di `engine-ui`

Tutto sotto `dev.antigravity.fluidengine.ui` (`fluid/` e `theme/`). Se stai per scrivere un
componente, cerca prima qui: quasi tutto quello che serve a una schermata esiste già.

## Struttura di una schermata

| | |
|---|---|
| `FluidScreen(title, subtitle, onBack, actions, titleFacets, isRefreshing, onRefresh, overlay) { LazyListScope }` | la schermata: titolo grande dentro lo scroll, barra di vetro che appare quando il titolo passa sotto; `overlay` riceve il backdrop per chrome flottante sicuro |
| `FluidScreenSurface` | lo sfondo della finestra |
| `FluidTabBar` / `FluidTabRail` | navigazione principale a pillola (`FluidTabItem`) |
| `FluidSheet`, `FluidAlert`, `FluidGrabber` | fogli modali e avvisi (`FluidAlertAction`) |
| `FluidContainerScaffold` | contenitore + dettaglio |
| `FluidSectionIndex` | indice laterale per liste lunghe (`FluidSectionAnchor`) |
| `FluidNotificationHost`, `rememberFluidNotificationHostState` | notifiche contestuali dentro l'app (`FluidNotification`, `FluidNotificationTone`) |
| `ProvideFluidChrome`, `rememberFluidChromeController` | registro della barra, inset della tab bar, bus "torna in cima" |
| `FluidRouteMotionHost`, `FluidRouteMotion`, `rememberRouteMotionSignals` | transizioni di rotta |
| `rememberFluidTouchOrigin`, `MotionOrigin` | apertura dal punto toccato |

## Contenuto

| | |
|---|---|
| `FluidListGroup { }` | il contenitore della lista raggruppata |
| `FluidListRow(title, subtitle, eyebrow, meta, tone, badge, leading, onClick, onLongClick)` | la riga |
| `FluidListDivider()` | il separatore rientrato |
| `FluidListItem` | riga semplice, senza gruppo |
| `FluidCard`, `FluidHeroCard`, `FluidEditorialCard` | superfici |
| `FluidSectionHeader`, `FluidSectionTitle`, `FluidSectionFootnote` | intestazioni e note |
| `FluidStatusBadge`, `FluidStatChip`, `FluidMetricTile`, `FluidMiniChart` | dati in piccolo |
| `FluidEmptyState(title, detail)`, `FluidLoading`, `FluidLoadingBlock`, `FluidInlineMessage` | stati |
| `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`, `SyncStatus.noticeMessage()`, `SyncStatus.lastSyncLabel()` | stato della sincronizzazione |
| `FluidQuickAction`, `FluidTopHeader`, `FluidPillTabs`, `FluidAccentLabel` | varie |

## Controlli

`FluidButton(text, onClick, style, size, enabled, loading, fillWidth, leading)` con
`FluidButtonStyle` = Filled / Tinted / Plain / Destructive.

`FluidSwitch(checked, onCheckedChange)` — passa `onCheckedChange = null` quando è la riga a possedere
il `toggleable` e il target da 48dp.

`FluidSegmentedControl(options, selected, onSelect, label)`, `FluidChip(label, selected, onClick)`,
`FluidTextField`, `FluidColorDot`, `FluidBarAction(icon, contentDescription, onClick)`,
`FluidGlassIconButton`, `glassControlSurface`,
`FluidSpinner`, `FluidProgressBar`, `FluidIndeterminateBar`, `FluidHairline`, `FluidRowValue`.

## Token

- `FluidRadius`: Small 10, Control 12, Card 18, Group 22, Sheet 38 · `FluidCapsuleShape`
- `ContinuousCornerShape(radius, smoothing = 0.6f)`
- `fluidTypography()` · `FluidTextStyles.uppercaseCaption / numeric / largeNumeric`
- `FluidFontFamily` (testo), `FluidDisplayFontFamily` (titoli ≥ 20sp)
- `FluidTone`: Primary, Success, Warning, Danger, Info, Neutral
- `FluidMotion` (molle), `FluidMotionScheme` (le passa a Material), `FluidMotionPolicy`
- `GlassDefaults`, `rememberGlassBackdrop`, `GlassTint`, `GlassEdge`, `GlassFalloff`, `GlassOptics`, `GlassRole`
- `AccentPreset`, `fluidAccentPresets`, `FluidDefaultBrand`, `fluidBrandAccent(isDark, brand)`
- `fluidColorScheme(settings, isDark, brand, dynamicScheme)` — la palette fuori da una composizione,
  per widget e notifiche
- Modificatori: `fluidPressable`, `fluidRowPressable`

## Regola del vetro

Solo chrome e overlay: top bar, tab bar/rail, azioni, notifiche, indici e modali. Card, liste,
campi, chip, segmentati e pulsanti che scorrono con la pagina restano solidi. Un overlay che deve
campionare la schermata va nello slot `FluidScreen.overlay`; un `glassSurface` dentro il body
registrato finirebbe per campionare se stesso. Le lenti annidate usano `glassControlSurface`, che
riusa il blur del genitore senza aggiungere layer.

## Il tema

```kotlin
FluidTheme(
  settings = engineSettings,                 // EngineSettings: themeMode, accentMode, …
  brand = AccentPreset("app", "App", Color(0xFF…), Color(0xFF…)),
) { /* … */ }
```

`EngineSettings` è piccolo apposta. Se l'app ha già un suo modello di impostazioni, mappalo al bordo
con un `map { }` sul flow che esiste già — non sostituirlo e non allargare `EngineSettings` con campi
che riguardano una sola app.

## Schema di una schermata tipica

```kotlin
@Composable
fun SchermataDemo(state: DemoUiState, onApri: (String) -> Unit) {
  FluidScreen(
    title = "Demo",
    subtitle = "Una riga che spiega la pagina",
    isRefreshing = state.isRefreshing,
    onRefresh = state.onRefresh,
  ) {
    if (state.notice != null) {
      item { FluidSyncNotice(status = state.syncStatus, onRetry = state.onRetry) }
    }
    item { FluidSectionHeader(title = "Sezione") }
    item {
      FluidListGroup {
        state.items.forEachIndexed { index, item ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = item.title,
            subtitle = item.subtitle,
            tone = item.tone,
            onClick = { onApri(item.id) },
          )
        }
      }
    }
    if (state.items.isEmpty()) {
      item { FluidEmptyState(title = "Niente qui", detail = "…") }
    }
  }
}
```
