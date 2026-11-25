# engine-account-ui

La faccia dell'Account Pampa in ogni app (F10.4 e F10.6), **di base e senza grafica finale**: la riga «Account Pampa»
per le impostazioni e l'avatar, nei quattro stati di [account](account.md), e i componenti delle persone. Pacchetto `dev.antigravity.fluidengine.account.ui`.
**Opt-in**, sopra `engine-ui` ed `engine-account`.

Nessuna chiave e nessuna crittografia (progetto dell'account, T10): un'app che mostra solo chi sei non si porta dietro
la custodia. I componenti delle persone ricevono solo nomi, avatar e id che valgono per quell'app (`PampaPeople`); la
grafica finale arriva con F10.11.

Catalogo completo delle firme: [`../api/account-ui.md`](../api/account-ui.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la riga nelle impostazioni, gia' collegata | `PampaAccountRow(onDownloadStore = { … })` dentro un `FluidListGroup` |
| la riga con uno stato mio (anteprime, prove) | `PampaAccountRow(state, onAction = { … })` |
| l'avatar dell'account altrove | `PampaAccountAvatar(state)` |
| le parole in un'altra lingua | `PampaAccountLabels(…)` |
| cosa mostra la riga, senza Compose | `pampaAccountRowModel(state)` |
| la fila delle persone nel foglio di condivisione | `FluidShareSheet(…, people = { item, dismiss -> FluidPeopleRow(rememberPampaPeopleRow(), onPick = { … PampaPeople.send(…); dismiss() }) })` |
| «Condivisi con te» (se vuota non compare) | `FluidSharedWithYou(items, onOpen = { … }, onAttribution = { item, azione -> … })`, con `PampaPeople.received` |
| «Da Alessio» sulla cosa, col menu delle quattro voci | `FluidAttribution(from, onAction = { … })` |
| «Rispondi» | `FluidQuickReply(visible, to, quoted, onSend = { PampaPeople.reply(…) }, onDismissRequest = { … })` |
| l'avviso delle parole cambiate dopo un rinnovo (Store, Pampa Mail) | `FluidWordsChangedNotice(nome, byThem, onCompare, onDismiss)` |
| i modelli senza Compose | `peopleRowModel`, `sharedSubtitle`, `attributionLabels`, `wordsChangedText`, con `PeopleLabels` |

## Come funziona

| stato | titolo | sottotitolo | al tocco |
|---|---|---|---|
| `NoStore` | Account Pampa | L'Account Pampa si usa con Pampa Store | `onDownloadStore` (la pagina di rilascio la sa l'app) |
| `StoreTooOld` | Account Pampa | Aggiorna Pampa Store per usare l'account | lo Store |
| `SignedOut` | Account Pampa | Accedi con Pampa Store | lo Store sulla pagina d'accesso |
| `SignedIn` | il nome | Account Pampa (o «Entrato col codice di recupero») | lo Store sulla pagina dell'account |

La riga e' una `FluidListRow` dell'engine con un `FluidAvatar` davanti: le iniziali del nome su un colore che viene
dall'id dell'account, quindi uguale in ogni app; senza account, il glifo di una persona. La riga collegata legge
`PampaAccount.state`, lo rilegge quando compare, e apre lo Store con `PampaAccount.storeIntent`.

## Le persone (F10.6)

- **`FluidPeopleRow`**: «I tuoi dispositivi» per primi (scelta Q1), poi fino a otto persone e gruppi nell'ordine dello
  Store, e «Altri…». Senza voci non disegna niente (P1). Scorre col dito ed è nitida.
- **`FluidSharedWithYou`**: la sezione, con l'avatar, «Da Alessio · ieri» e il titolo; le quattro voci anche dal tocco
  lungo della riga. Vuota non compare (scelta D8).
- **`FluidAttribution`**: il chip «Da Alessio» con l'avatar; un tocco apre il foglio di vetro con Rispondi, Apri la
  conversazione, Togli dai condivisi, Non mostrare più da Alessio in questa app.
- **`FluidQuickReply`**: il foglio piccolo con la cosa citata, il campo e «Invia».
- **`FluidWordsChangedNotice`**: «Le parole con Marco sono cambiate perché Marco ha rinnovato le chiavi. Confrontatele
  di nuovo per tenere il segno «verificato».» Non è un allarme: le chiavi nuove sono firmate dalla stessa radice.
- `FluidShareSheet` di `engine-ui` ha lo slot `people`, in cima alle app: null di serie, come prima.

## Le prove

`PeopleComponentsModelTest`: la fila (dispositivi in cima, otto, «Altri…», vuota senza nessuno), «Da X · ieri», le
quattro voci, il testo delle parole cambiate. `PampaAccountRowModelTest`: i quattro stati con le frasi di `b/architettura.md` § 2.4, l'azione di ogni stato, la
sessione di recupero, le parole in un'altra lingua.

## File

`engine-account-ui/src/main/kotlin/dev/antigravity/fluidengine/account/ui/PampaAccountRow.kt`.
