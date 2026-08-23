# 06 · Limiti

Questo documento esiste perché la domanda "posso aggiornare l'engine da remoto e vederlo su tutte le app senza ricompilarle?" ha una risposta parziale, e una risposta parziale detta a metà porta a progettare cose che poi non reggono.

## Cosa si aggiorna da remoto, davvero

Modificando il manifest ospitato, e senza ricompilare o ripubblicare niente:

- accendere o spegnere un **feature flag**, e quindi scegliere fra percorsi che la build già contiene;
- alzare la **versione minima dell'engine**, e far dire alle app più vecchie che devono aggiornarsi;
- mostrare un **avviso** (manutenzione, disservizio noto, cambio di scuola/di endpoint);
- attivare il **kill switch** e fermare una build che sta facendo danno;
- cambiare a quale **release** punta l'aggiornamento in-app: versione, changelog, APK, canale.

Quest'ultimo è il punto che spesso si sottovaluta: il manifest **non** aggiorna il codice, ma decide quale APK le app scaricano. Pubblicata una nuova release e aggiornato il manifest, ogni installazione se ne accorge al controllo successivo e propone l'aggiornamento. Il ciclo completo "cambio l'engine → arriva a tutti" resta quindi possibile — passa da una build e da una release, non da un push a un server.

## Cosa non si aggiorna da remoto

**Il codice.** Non esiste, su Android, un modo di sostituire classi compilate in un'app già installata che sia insieme stabile e ammesso.

- Il caricamento dinamico di codice (`DexClassLoader` su un dex scaricato) tecnicamente funziona, ed è il modo classico per farsi rimuovere da Google Play: le policy vietano di scaricare ed eseguire codice che non fa parte dell'APK. Anche fuori dallo store resta una superficie d'attacco enorme — chi controlla quel file controlla l'app.
- Anche volendo ignorare le policy, non reggerebbe: R8 rinomina e rimuove roba a ogni build, Compose genera codice al momento della compilazione, i moduli dell'engine sono compilati insieme all'app. Un dex compilato contro una versione dell'app funzionerebbe con quella e basta, e fallirebbe a runtime — cioè sul telefono di qualcuno, dopo la pubblicazione, nel modo più difficile da diagnosticare che esista.
- Le alternative che si comportano bene (script, layout scaricati, UI dichiarativa da server) significherebbero riscrivere l'engine in un linguaggio interpretato e perdere esattamente le cose che lo rendono buono: il compilatore, i test, e un design system che è codice Kotlin.

Quindi: **una modifica al design system, un componente nuovo, una correzione dentro l'engine → richiedono una build dell'app.** Su questo l'engine non finge.

## Cosa fa l'engine per rendere quella build indolore

- Aggiornare una app è un comando: `engine-update.ps1 -Version 1.1.0`.
- Aggiornarle tutte è un comando: `engine-update-all.ps1 -Version 1.1.0`, con `-WhatIf` per vedere prima e `-Build` per fermarsi alla prima che non compila.
- Ogni app ha il suo pin, quindi un aggiornamento non può rompere le app che non hai ancora toccato.
- Il `CHANGELOG` viene stampato durante l'aggiornamento, con le voci **BREAKING** in evidenza.
- La versione dell'engine è compilata nell'app, quindi dal manifest si vede sempre quali build sono indietro e si può alzare il pavimento.
- Il rollback è lo stesso comando con il numero di prima, perché il pin è un tag e i tag non si spostano.

## Le altre cose che è bene sapere prima

- **Non ogni app puo' ospitarlo.** `engine-ui` e' scritto su Material 3 androidx: un'app in Compose Multiplatform (`org.jetbrains.compose`) non puo' prenderlo senza migrare, e un'app di sole View puo' usare solo i moduli senza UI. Un AGP vecchio invece non e' un ostacolo: si abbassa il compileSdk dell'engine dal `gradle.properties` dell'app. I requisiti sono in [`01-integrazione.md`](01-integrazione.md).
- **L'engine è compilato dentro ogni app.** Due app sullo stesso telefono non condividono una copia dell'engine: ognuna ha la sua. Non esiste un "engine installato una volta sola" — su Android sarebbe un'app separata con IPC, e sarebbe una scelta molto peggiore per motivi di avvio, permessi e aggiornamenti.
- **L'engine è un fork del design system dell'app da cui è nato.** Le correzioni fatte in ClasseViva Expressive non arrivano da sole nell'engine, e viceversa. La direzione giusta è portare l'app *sopra* l'engine, così che esista una copia sola; finché non succede, le due copie vanno riallineate a mano e consapevolmente.
- **`FluidTheme` richiede Material 3 in versione alpha** (lo `MotionScheme` non è ancora nella linea stabile). È indicato in `versions.gradle` e va tenuto d'occhio a ogni aggiornamento di Compose.
- **Gli angoli arrotondati dei widget richiedono Android 12.** Sotto, Glance ignora `cornerRadius` e i widget restano squadrati: i colori portano l'identità da soli, ed è per questo che nessun componente del kit affida un significato alla sola forma.
- **`REQUEST_INSTALL_PACKAGES`**, che `engine-update` dichiara, rende l'app non pubblicabile su Google Play senza una motivazione accettata. Per app distribuite fuori dallo store non è un problema; se una app dovesse finire su Play, quel modulo va tolto dalle dipendenze.
