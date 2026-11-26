# Il Worker della diagnostica (F8.24)

Riceve i rapporti di `engine-diagnostics` (solo dalle app dove l'utente ha detto di sì) e li tiene in D1; dà il
cruscotto per app e modello di telefono. La scheda: `skill/fluid-engine/references/moduli/diagnostics.md`.

**Oggi si usa solo in locale.** Niente `wrangler login`, niente deploy, niente `--remote`.

## In locale

Con Node 22.5 o più nuovo, dalla radice dell'engine:

```
node engine-diagnostics/worker/test.mjs
```

Dal vivo, con Wrangler 4 (quello di `Pampa-account` va bene: `node <Pampa-account>/node_modules/wrangler/bin/wrangler.js`),
dalla cartella del Worker:

```
echo DASHBOARD_TOKEN=un-segreto-finto-lungo-almeno-16 > .dev.vars
wrangler d1 migrations apply pampa-diagnostica --local
wrangler dev --local --port 8787 --ip 127.0.0.1
```

Poi un telefono collegato manda i rapporti al PC con `adb reverse tcp:8787 tcp:8787` e l'indirizzo
`http://127.0.0.1:8787/v1/reports` (la galleria lo usa nella scena «Crash e lentezze»). Il cruscotto è in
`http://127.0.0.1:8787/cruscotto`. `.wrangler/` e `.dev.vars` non vanno in git.

## Le strade

| strada | cosa fa |
|---|---|
| `POST /v1/reports` | `{"schema":1,"reports":[…]}`, al massimo 100 rapporti e 128 KiB; ogni campo validato, un campo sconosciuto rifiuta tutto; lo stesso id conta una volta |
| `GET /v1/summary?app=…` | col segreto (`Authorization: Bearer …`): i crash per firma, le chiusure per motivo, i fotogrammi per modello e pagina col ritardo per secondo di Apple; solo le build di release, ultimi 30 giorni |
| `GET /cruscotto` | la pagina che chiede app e segreto e legge `/v1/summary` |
| il cron delle 03:30 UTC | cancella i rapporti di più di 90 giorni |

Nessun indirizzo IP: il codice non legge `CF-Connecting-IP` e `[observability]` è spento.

## Quando si pubblica (lo decide e lo fa Alessio)

1. `wrangler d1 create pampa-diagnostica` e l'id in `wrangler.toml`; `wrangler d1 migrations apply pampa-diagnostica --remote`.
2. `wrangler secret put DASHBOARD_TOKEN`.
3. Il dominio: una riga `routes = [{ pattern = "diagnostica.pampa-apps.com", custom_domain = true }]` (oggi non c'è).
4. `wrangler deploy`, e l'indirizzo `https://diagnostica.pampa-apps.com/v1/reports` nella `FluidDiagnosticsConfig` delle app.

## I nomi veri dalle righe offuscate (R8): un passo del rilascio, non ancora scritto

Una release con R8 manda righe come `a.b.c(SourceFile:3)`. Il piano:
- al rilascio (`engine-release.ps1` per l'engine, lo script di rilascio di ogni app) si carica il `mapping.txt` della build
  in un bucket R2 privato del Worker, con la chiave `<app>/<versionCode>.txt` (una strada `PUT /v1/mapping`, col suo
  segreto);
- il cruscotto, per una firma, ritraduce le righe con quel file (il formato di `mapping.txt` è testo semplice: classi e
  metodi con i loro intervalli di righe), o le si ritraduce sul PC con `retrace` di R8.
