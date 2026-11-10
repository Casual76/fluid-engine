# API · Contenuto web chiuso (FluidWebContent)

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidWebContent.kt`

- `data class FluidWebRequest(val url: Uri, val method: String, val isMainFrame: Boolean)` — Richiesta di una risorsa: nessuna intestazione personale della WebView attraversa questo confine. Il gestore viene chiamato su thread di I/O, anche contemporaneamente. Se scarica la risorsa deve usare un client senza cookie, referer, credenziali o redirect impliciti; [headers] offre solo un UA generico. Schemi inline personalizzati passano dallo stesso gestore; data/blob/about sono interni al renderer.
  - `val headers: Map<String, String> get()` — Intestazioni minime suggerite al client dell'app; non sono quelle ricevute dal renderer.
- `sealed interface FluidWebResponse` — Nessuna alternativa abilita la rete della WebView: l'app puo' soltanto fornire byte o bloccare. Non si accettano header, redirect o Set-Cookie dalla risposta dell'app. La politica delle risorse e la sanificazione dell'HTML restano responsabilita' dell'app, anche quando restituisce [Serve].
  - `data object Block : FluidWebResponse` — Risposta vuota locale, mai `null` (che autorizzerebbe il caricamento nativo).
  - `class Serve(val mimeType: String, val encoding: String? = null, val body: () -> InputStream) : FluidWebResponse` — [body] si apre su I/O; WebView chiude lo stream. Non restituire stream condivisi fra richieste.
- `data class FluidWebLink(val url: String, val visibleText: String? = null)` — Destinazione vera e testo dell'ancora, quando il renderer riesce a fornirlo senza eseguire script.
- `enum class FluidWebPhase` — Stato effimero: il documento rimane nei dati dell'app, mai nel Bundle Android.
- `enum class FluidWebFailure` — Cause su cui l'app puo' offrire testo alternativo o aggiornamento del provider WebView.
- `sealed interface FluidWebSizing` — Altezza sempre limitata: un documento enorme non diventa una texture enorme in una lista lazy.
  - `data class WrapContent(val maxHeight: Dp = 2048.dp) : FluidWebSizing` — Cede lo scroll verticale alla pagina finche' il contenuto entra; oltre il tetto scorre da se'.
  - `data class Viewport(val height: Dp = 600.dp) : FluidWebSizing` — Viewport fissa per documenti lunghi. Inserirla preferibilmente in una pagina senza altro scroll.
- `object FluidWebContentDefaults` — Limiti del contenitore, indipendenti dal dominio dell'app.
  - `const val UserAgent: String` — Nessuna identificazione di dispositivo, piattaforma, versione o app.
  - `const val MaxHtmlCharacters: Int` — Rifiuto prima di copiare l'HTML su I/O; l'app puo' mostrare la propria versione testuale.
- `class FluidWebContentState` — Stato di una sola istanza montata. Non conserva Activity o WebView dopo lo smontaggio. [retry] distrugge e ricrea la vista con un altro profilo: non rianima un renderer morto, e il profilo di un renderer morto da se' non viene piu' riusato. Tutte le azioni e letture UI si eseguono sul thread principale.
  - `var phase: FluidWebPhase by mutableStateOf(FluidWebPhase.Empty)` — Prima pittura confermata dal renderer, non il solo completamento della richiesta HTML.
  - `var failure: FluidWebFailure? by mutableStateOf(null)` — Errore esposto allo slot dell'app; nullo durante una nuova apertura.
  - `var contentHeightPx: Int by mutableIntStateOf(0)` — Altezza fisica del documento (puo' superare quella della viewport).
  - `var scrollsInternally: Boolean by mutableStateOf(false)` — Vero anche nel ripiego automatico oltre il tetto di WrapContent.
  - `fun retry()` — Riprova anche dopo un errore di creazione; non salva l'HTML ne' consensi fra istanze.
  - `fun printAdapter(jobName: String): PrintDocumentAdapter?` — Adattatore per PrintManager.print dell'Activity dell'app; nullo senza prima pittura valida. L'app deve mantenere l'host montato per la durata della stampa.
- `@Composable fun rememberFluidWebContentState(): FluidWebContentState` — Conserva solo lo stato effimero; dopo ricreazione il contenuto viene fornito nuovamente dall'app.
- `@Composable fun FluidWebContent(html: String, onLinkClick: (FluidWebLink) -> Unit, modifier: Modifier = Modifier, state: FluidWebContentState = rememberFluidWebContentState(), onRequest: (FluidWebRequest) -> FluidWebResponse = { FluidWebResponse.Block }, sizing: FluidWebSizing = FluidWebSizing.WrapContent(), darkening: Boolean = false, textScale: Float = 1f, pinchZoom: Boolean = true, onLinkLongPress: ((FluidWebLink) -> Unit)? = null, placeholder: @Composable () -> Unit = { FluidSkeletonText(lines = 6) }, failure: @Composable (FluidWebFailure) -> Unit = {})` — HTML locale in una WebView chiusa. Non e' un sanificatore: l'app filtra HTML, CSS e risorse.
