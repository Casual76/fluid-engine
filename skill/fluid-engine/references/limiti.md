# Limiti

Da leggere prima di rispondere a "si può aggiornare l'engine da remoto per tutte le app?".

## La risposta corretta

**Il comportamento sì, il codice no.**

Modificando il manifest ospitato, senza ricompilare né ripubblicare niente:

- accendere o spegnere un feature flag (scegliere fra percorsi che la build **già contiene**);
- alzare la versione minima dell'engine, e far dire alle app vecchie che devono aggiornarsi;
- mostrare un avviso;
- attivare il kill switch;
- cambiare a quale release punta l'aggiornamento in-app.

L'ultimo punto è quello che completa il quadro: il manifest non aggiorna il codice, ma decide quale
APK le app scaricano. Pubblicata una release e aggiornato il manifest, ogni installazione lo propone
al controllo successivo. Il ciclo "cambio l'engine → arriva a tutti" resta quindi possibile; passa da
una build e da una release, non da un push a un server.

## Perché il codice no

Non esiste, su Android, un modo di sostituire classi compilate in un'app installata che sia insieme
stabile e ammesso.

- Caricare codice scaricato (`DexClassLoader` su un dex remoto) **viola le policy di Google Play** ed
  è una superficie d'attacco enorme: chi controlla quel file controlla l'app.
- Anche ignorando le policy non reggerebbe: R8 rinomina e rimuove a ogni build, Compose genera codice
  in compilazione, i moduli dell'engine sono compilati insieme all'app. Un dex compilato contro una
  versione funzionerebbe solo con quella, e fallirebbe a runtime — cioè sul telefono di qualcuno,
  dopo la pubblicazione.
- Le alternative che si comportano bene (UI dichiarativa da server, layout scaricati) vorrebbero dire
  riscrivere l'engine in un linguaggio interpretato e perdere il compilatore, i test e un design
  system che è codice Kotlin.

**Non proporre queste strade.** Se qualcuno le chiede, spiega il compromesso e indica quello che
l'engine fa al posto loro: aggiornamento a un comando per app, `engine-update-all.ps1` per tutte,
flag remoti per il comportamento e versione minima per ritirare le build vecchie.

## Altre cose vere che conviene sapere prima

- **L'engine è compilato dentro ogni app.** Due app sullo stesso telefono non condividono una copia.
  Un "engine installato una volta sola" su Android sarebbe un'app separata con IPC: peggio su avvio,
  permessi e aggiornamenti.
- **L'engine è un fork del design system dell'app da cui è nato.** Le correzioni non viaggiano da
  sole in nessuna delle due direzioni. La strada giusta è portare l'app *sopra* l'engine; finché non
  succede, si riallinea a mano e consapevolmente.
- **`FluidTheme` richiede Material 3 alpha** (`MotionScheme` non è nella linea stabile). È in
  `versions.gradle` e va guardato a ogni aggiornamento di Compose.
- **Gli angoli arrotondati dei widget richiedono Android 12.**
- **`engine-update` dichiara `REQUEST_INSTALL_PACKAGES`**, che rende l'app non pubblicabile su Google
  Play senza una motivazione accettata. Per app distribuite fuori dallo store va bene; se una app
  deve finire su Play, togli quel modulo dalle dipendenze.
