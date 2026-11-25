# «Pampa · prova passkey» (F10.2)

L'app piccola che risponde alla domanda più grossa dell'Account Pampa (scelta Q2): **il PRF delle passkey arriva alle
app native da Credential Manager, col Gestore password di Google e con Samsung Pass?** Se sì, l'impronta che fa entrare
apre anche i dati cifrati (`artifacts\ecosistema\account\b\chiavi.md` § 3.2). Se no, F10.4 segue il ramo «codice di
recupero per tutti».

Usa `engine-auth` come lo userà Pampa Store: le opzioni WebAuthn hanno la forma che manda il server dell'Account Pampa,
il PRF lo scrive il modulo (`FluidPrfInput.PampaV1`), e la risposta torna già senza il PRF.

## Cosa misura

Per ogni gestore, a schermo e nel riassunto:
- il PRF **alla creazione** (dichiarato con `prf.enabled`, e il valore se il gestore lo dà subito);
- il PRF **all'accesso**;
- i flag **BE** (sincronizzabile) e **BS** (sincronizzata), e UV;
- se il PRF resta **uguale fra due accessi** (e fra creazione e accesso);
- **il gestore usato**, dall'AAGUID scritto alla creazione (Google `ea9b8d66-…`, Samsung Pass `53414d53-…`), e la
  **differenza fra i due**.

Il valore del PRF non si scrive mai: a schermo e nel riassunto c'è solo un'impronta di 4 byte
(`SHA-256("pampa/prova/impronta" ‖ prf)`), che basta per dire «uguale» o «diverso».

## Il dominio e la firma

- **Dominio: `prova.pampa-apps.com`**, non `pampa-apps.com` (critica S12). Le passkey di prova restano separate da
  quelle vere, e si tolgono alla fine.
- **Firma: una chiave di prova creata apposta**, non `pampa.jks` (la sua password ce l'ha solo Alessio).
  - Il keystore è `keystore/prova-passkey.jks`, con la password in `keystore/prova-passkey.properties`: tutti e due
    **fuori da git** (`.gitignore` qui accanto). Una copia di riserva è in
    `artifacts\ecosistema\account\f10-2\chiave-prova\` (anche quella fuori da git).
  - Impronta SHA-256 del certificato:
    `D3:DC:F3:C6:EF:11:DD:4C:09:D7:3E:5B:B3:FA:D5:DD:B0:55:E5:32:71:36:FB:94:41:2A:9C:3A:AF:E8:A6:0C`.
    È scritta in `build.gradle` (`EXPECTED_CERT_SHA256`) e in `worker/src/index.js`; `worker/test.mjs` controlla che
    coincidano.
  - Senza il keystore la build usa la chiave di debug di Android Studio: l'app lo scrive in cima («Firma diversa…»), e
    i gestori rifiutano le passkey.
- **Nessun permesso INTERNET.** Il gestore legge `assetlinks.json` da sé.

## Il Worker

`worker/` è il Worker piccolissimo che su `prova.pampa-apps.com` serve **solo** `/.well-known/assetlinks.json` (200,
JSON, nessun redirect; tutto il resto 404, nessun registro). Non lo pubblica un agente: lo pubblica Alessio nella
mini-seduta, e alla fine lo cancella. La guida è `artifacts\ecosistema\account\f10-2\guida-mini-seduta.md`.

Prova in locale, senza rete:

```
node tools/prf-probe/worker/test.mjs
```

## Costruire

È un modulo del build dell'engine (`:prf-probe`), fuori dal lotto standard:

```
gradlew.bat --no-daemon :prf-probe:testDebugUnitTest :prf-probe:assembleDebug
```

L'APK è `build/outputs/apk/debug/prf-probe-debug.apk` (debuggable, per leggere il riassunto con `run-as`). Pacchetto
`dev.pampa.prova.passkey`, nome «Pampa · prova passkey».

## Leggere il riassunto senza screenshot

Il tasto «Copia il riassunto» lo mette negli appunti (segnato come sensibile). In più l'app lo scrive a ogni prova in
due file e nel logcat:

```
adb shell run-as dev.pampa.prova.passkey cat files/riassunto.txt
adb shell cat /sdcard/Android/data/dev.pampa.prova.passkey/files/riassunto.txt
adb logcat -d -s PampaProvaPasskey
```

Il riassunto contiene: la firma dell'app, il telefono, per ogni gestore le voci sopra, le differenze fra Google e
Samsung Pass, l'esito per F10.4 e gli errori con la loro frase. Non contiene dati personali: i nomi delle passkey sono
«Prova PRF 1», «Prova PRF 2»…
