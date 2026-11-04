<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/glossario.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Glossario

Le parole che l'engine usa in un senso suo. Quando il codice e i commenti dicono «goccia», «lastra», «porto», qui
c'è cosa vogliono dire. Per l'implementazione, la scheda indicata.

## Il vetro

| parola | significato |
|---|---|
| **backdrop** | la registrazione di ciò che sta *dietro* una superficie di vetro (la pagina, o il pannello su cui sta), che la lente campiona. `GlassBackdropState`, `currentGlassBackdrop()`. È ciò che costa: `componenti/vetro.md` |
| **sorgente** | un sottoalbero registrato come backdrop (`layerBackdrop`, `glassBackdropSource`). Una sorgente a schermo intero sempre registrata è il costo di prestazione da cercare |
| **lente / ottica** | la rifrazione dello sfondo sotto la superficie: campo di distanza della forma, dispersione cromatica, bordo speculare. Si tara con la lente, non con la sfocatura |
| **tinta** | il velo di colore sul vetro; ridotta a un terzo dalla 1.4.0: la leggibilità si compra col raggio di sfocatura |
| **ruolo** (`GlassRole`) | quanta ottica riceve una superficie: barra, flottante, interattivo, modale… |
| **materiale** | ciò che un pannello è come cosa da guardare attraverso (sfocato, rifratto, tinto, contenuto escluso); dal 2.13 il vetro dentro il vetro campiona *quello* |
| **chrome** | tutto ciò che sta attorno al contenuto: barra superiore, tab bar/rail, azioni, notifica, indice, modali. Vetro, per scelta |
| **fondale** (`FluidAmbient`) | le lavate di colore sotto la pagina che danno al vetro qualcosa da rifrangere |
| **lastra** | una superficie di vetro a riposo (anche quella fisica: «a riposo una superficie fisica è una lastra») |
| **goccia** | la superficie di vetro **in viaggio** (quella che si trasforma: menu, pop-up da un tasto), con la sagoma che si deforma solo per il moto |
| **tier** (`FluidPhysicsTier`) | il livello di costo/qualità con cui Fluid-physics disegna |
| **puck** | il cerchio/pillola mobile della barra a pillola che rifrange le schede |

## Movimento, tatto, pagine

| parola | significato |
|---|---|
| **Fluid-physics** | il motore che trasforma una sagoma di vetro in un'altra (`ui/fluidphysics/`) |
| **viaggio** | la transizione di una forma da una sagoma a un'altra (`FluidPhysicsJourney`); un viaggio cancellato si ferma dove si trova |
| **grip** (`FluidPressGrip`) | di chi è il dito durante una pressione: `HoldsFinger` (del pulsante fino al sollevamento) o `YieldsToScroll` (cede alla fila che scorre) |
| **i pulsanti crescono, le superfici cedono** | la regola della pressione: un tasto cresce al contatto e tiene il dito; righe e card cedono alla pagina |
| **grana** | la vibrazione di un dito che trascina un tasto (`FluidHapticGrain`); di serie quella di LibrePods |
| **un momento** | due eventi aptici entro 70 ms sono lo stesso momento: suona il primo (eccezione: un tap su un pulsante fa tre colpi) |
| **host (di pagine)** | `FluidPageHost`/`FluidNavHost`: tiene le pagine, il back sotto il dito e **l'unica** barra |
| **peer** | una pagina allo stesso livello (`navigateToPeer`), non una pagina dentro un'altra |
| **`touchX`** | la posizione vera del dito nel back: mai il `progress` del gesto di sistema |
| **EdgeOnly / Anywhere** | da dove parte il back col dito: i primi 20 dp (di serie) o ovunque |
| **portale** | `FluidGlassModalPortal`: i pop-up e i fogli vivono dentro la composizione, non in una finestra; serve l'host in radice |

## L'app e l'engine

| parola | significato |
|---|---|
| **pin** | la versione dell'engine agganciata a un'app: `engine.properties` + il tag del submodule |
| **canale** | `stable` o `beta`: quale serie di tag `engine-update -Latest` considera |
| **porto** | un'app che **non può** ospitare `engine-ui` (Compose Multiplatform) e riscrive a mano il look; si dichiara con `engine.mode=port` e `FLUID_PORT_OF` |
| **variante silenziosa** | l'engine modificato dentro un'app e mai committato nel suo repo: sparisce al primo aggiornamento |
| **manifest** | il JSON ospitato (lo stesso del Pampa Store con la sezione `engine`) che decide flag, versione minima, avvisi, kill switch |
| **flag** (`EngineFlag`) | un interruttore remoto fra percorsi che la build contiene già, con un default = come si comporta oggi |
| **kill switch** | l'ultima risorsa: dice a una build installata che non è più utilizzabile |
| **BREAKING** | una voce di changelog che rompe la compilazione di chi aggiorna; major se è una firma pubblica |
| **«Cambia a vista» / «al tatto»** | compila lo stesso ma si nota: da guardare in mano |
| **dominio** | ciò che sa cos'è un voto, una materia, un file: mai nell'engine |

## L'assistente

| parola | significato |
|---|---|
| **BYOK** | *bring your own key*: le chiavi dei provider (Groq, Gemini, OpenRouter) sono dell'utente e stanno cifrate nel Keystore |
| **tool** (`AiTool<C>`) | una funzione che l'assistente può chiamare, col contesto `C` dell'app |
| **gruppo** (`AiToolGroup`) | un insieme di tool: il router sceglie il gruppo, l'orchestratore i tool |
| **tre livelli** | router, chat, profondo: tre classi di modello con compiti diversi |
| **failover** | passare a un altro provider/modello quando uno risponde con un errore o un limite |
| **conferma** (`AiConfirmationGate`) | la finestra in cui l'utente approva un'azione prima che il tool la compia |
| **federato** | un tool esposto da un'app e chiamato da un'altra (`engine-ai-bridge`, il provider verifica che il chiamante sia firmato con la stessa chiave) |
