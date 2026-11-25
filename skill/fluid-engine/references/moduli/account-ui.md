# engine-account-ui

La faccia dell'Account Pampa in ogni app (F10.4), **di base e senza grafica finale**: la riga «Account Pampa» per le
impostazioni e l'avatar, nei quattro stati di [account](account.md). Pacchetto `dev.antigravity.fluidengine.account.ui`.
**Opt-in**, sopra `engine-ui` ed `engine-account`.

Nessuna chiave e nessuna crittografia (progetto dell'account, T10): un'app che mostra solo chi sei non si porta dietro
la custodia. I componenti delle persone (`FluidPeopleRow`, «Condivisi con te», «Da X», «Rispondi») arrivano con F10.6;
la grafica finale con F10.11.

Catalogo completo delle firme: [`../api/account-ui.md`](../api/account-ui.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la riga nelle impostazioni, gia' collegata | `PampaAccountRow(onDownloadStore = { … })` dentro un `FluidListGroup` |
| la riga con uno stato mio (anteprime, prove) | `PampaAccountRow(state, onAction = { … })` |
| l'avatar dell'account altrove | `PampaAccountAvatar(state)` |
| le parole in un'altra lingua | `PampaAccountLabels(…)` |
| cosa mostra la riga, senza Compose | `pampaAccountRowModel(state)` |

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

## Le prove

`PampaAccountRowModelTest`: i quattro stati con le frasi di `b/architettura.md` § 2.4, l'azione di ogni stato, la
sessione di recupero, le parole in un'altra lingua.

## File

`engine-account-ui/src/main/kotlin/dev/antigravity/fluidengine/account/ui/PampaAccountRow.kt`.
