// F8.24 · La prova del Worker della diagnostica, in locale e senza rete: `node engine-diagnostics/worker/test.mjs` dalla
// radice dell'engine (Node 22.5 o più nuovo: usa node:sqlite). Il D1 è finto ma il database è vero: SQLite di Node con le
// migrazioni della cartella `migrations`, dietro la stessa forma di D1 (prepare, bind, run, all, first, batch).
// Controlla:
//   - un invio valido entra, e lo stesso invio ripetuto non conta due volte;
//   - un campo sconosciuto, un messaggio d'eccezione infilato, un nome di filo, un'ora non intera, un corpo troppo grande
//     o un tipo sbagliato rifiutano l'invio intero, e niente entra;
//   - il cruscotto vuole il segreto, raggruppa i crash per firma e i fotogrammi per modello e pagina, con il rapporto
//     dei ritardi di Apple;
//   - il cron cancella i rapporti più vecchi di 90 giorni;
//   - l'indirizzo IP non si legge mai (il codice non nomina CF-Connecting-IP) e i registri di Cloudflare sono spenti.
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { DatabaseSync } from "node:sqlite";
import entry from "./src/index.js";
import { MAX_BODY_BYTES, handlers as worker, validateReport } from "./src/core.js";

const here = dirname(fileURLToPath(import.meta.url));

/** Un D1 finto sopra un SQLite vero. */
function fakeD1() {
  const db = new DatabaseSync(":memory:");
  for (const file of readdirSync(join(here, "migrations")).sort()) db.exec(readFileSync(join(here, "migrations", file), "utf8"));
  const statement = (sql, params = []) => ({
    bind: (...values) => statement(sql, values),
    run: async () => {
      const info = db.prepare(sql).run(...params);
      return { success: true, meta: { changes: Number(info.changes) } };
    },
    all: async () => ({ success: true, results: db.prepare(sql).all(...params) }),
    first: async () => db.prepare(sql).get(...params) ?? null,
  });
  return {
    sqlite: db,
    prepare: (sql) => statement(sql),
    batch: async (statements) => {
      db.exec("BEGIN");
      try {
        const out = [];
        for (const s of statements) out.push(await s.run());
        db.exec("COMMIT");
        return out;
      } catch (e) {
        db.exec("ROLLBACK");
        throw e;
      }
    },
  };
}

const NOW = Date.UTC(2026, 10, 26, 14, 20);
const HOUR = NOW - (NOW % 3_600_000);
const TOKEN = "segreto-finto-della-prova-0123456789";

const source = (extra = {}) => ({
  app: "dev.antigravity.fluidglass", version: "2.0.0", code: 12, engine: "3.1.2", build: "release",
  maker: "samsung", model: "SM-X710", sdk: 36, hz: 120, ...extra,
});
let counter = 0;
const id = () => `00000000-0000-4000-8000-${String(++counter).padStart(12, "0")}`;
const crash = (extra = {}) => ({
  id: id(), kind: "crash", hour: HOUR, source: source(),
  crash: {
    type: "java.lang.IllegalStateException", causes: ["java.io.IOException"], thread: "main", signature: "0123456789abcdef",
    frames: ["dev.antigravity.fluidengine.sample.Foo.bar(Foo.kt:12)", "android.os.Handler.dispatchMessage(Handler.java:99)", "java.lang.Object.wait(Native method)"],
  },
  ...extra,
});
const frames = (page, model, f = 1000, slow = 30, hangs = 1, hitch = 500, duration = 20_000) => ({
  id: id(), kind: "frames", hour: HOUR, source: source({ model }),
  frames: { page, day: "2026-11-26", frames: f, slow, hangs, frozen: 0, hitchMs: hitch, durationMs: duration, worstMs: 300, buckets: [0, 900, 50, 20, 10, 10, 5, 4, 1, 0, 0] },
});
const exit = () => ({
  id: id(), kind: "exit", hour: HOUR, source: source(),
  exit: { reason: "anr", importance: "foreground", pssKb: 120_000, rssKb: 200_000, frames: ["dev.antigravity.fluidengine.sample.Slow.run(Slow.kt:3)"] },
});

const env = { DB: fakeD1(), DASHBOARD_TOKEN: TOKEN };
const post = (body, headers = {}) => worker.fetch(new Request("https://diagnostica.example/v1/reports", {
  method: "POST",
  headers: { "content-type": "application/json", ...headers },
  body: typeof body === "string" ? body : JSON.stringify(body),
}), env, {}, NOW);
const count = () => Number(env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM rapporti").get().n);

// Un invio valido entra.
const first = [crash(), exit(), frames("impostazioni", "SM-X710"), frames("impostazioni", "SM-A528B", 500, 100, 4, 400, 5000), frames("note", "SM-X710")];
let res = await post({ schema: 1, reports: first });
assert.equal(res.status, 200);
assert.deepEqual(await res.json(), { accepted: 5, duplicates: 0 });
assert.equal(count(), 5);

// Lo stesso invio ripetuto (il telefono non ha visto la risposta) non conta due volte.
res = await post({ schema: 1, reports: first });
assert.deepEqual(await res.json(), { accepted: 0, duplicates: 5 });
assert.equal(count(), 5);

// Tutto quello che può portare un dato personale, o che non conosciamo, rifiuta l'invio intero.
const bad = {
  "campo sconosciuto": crash({ note: "ciao" }),
  "messaggio d'eccezione": (() => { const c = crash(); c.crash.message = "Nota «Diario di Marta» non trovata"; return c; })(),
  "nome del filo": (() => { const c = crash(); c.crash.thread = "sync-marta@example.com"; return c; })(),
  "riga del codice con un testo": (() => { const c = crash(); c.crash.frames = ["Nota Diario di Marta"]; return c; })(),
  "id del telefono nella fonte": crash({ source: source({ androidId: "a1b2c3" }) }),
  "ora non intera": crash({ hour: HOUR + 61_000 }),
  "id non a caso": crash({ id: "telefono-di-marta" }),
  "pagina con spazi": frames("Diario di Marta", "SM-X710"),
  "conti impossibili": (() => { const f = frames("note", "SM-X710"); f.frames.slow = 5000; return f; })(),
  "tipo e contenuto diversi": { ...exit(), kind: "crash" },
  "motivo sconosciuto": (() => { const e = exit(); e.exit.reason = "boh"; return e; })(),
};
for (const [what, report] of Object.entries(bad)) {
  res = await post({ schema: 1, reports: [frames("note", "SM-X710"), report] });
  assert.equal(res.status, 400, what);
  assert.ok((await res.json()).error, what);
}
assert.equal(count(), 5, "niente di un invio rifiutato è entrato");
assert.equal((await post({ schema: 2, reports: [crash()] })).status, 400);
assert.equal((await post({ schema: 1, reports: [] })).status, 400);
assert.equal((await post({ schema: 1, reports: [crash()], extra: true })).status, 400);
assert.equal((await post("non è json")).status, 400);
assert.equal((await post({ schema: 1, reports: [crash()] }, { "content-type": "text/plain" })).status, 415);
assert.equal((await post("x".repeat(MAX_BODY_BYTES + 1))).status, 413);
assert.equal((await post({ schema: 1, reports: Array.from({ length: 101 }, () => crash()) })).status, 400);
assert.throws(() => validateReport(crash({ crash: undefined })));

// Il cruscotto vuole il segreto.
const get = (path, token) => worker.fetch(new Request(`https://diagnostica.example${path}`, {
  headers: token ? { authorization: `Bearer ${token}` } : {},
}), env, {}, NOW);
assert.equal((await get("/v1/summary?app=dev.antigravity.fluidglass")).status, 401);
assert.equal((await get("/v1/summary?app=dev.antigravity.fluidglass", "sbagliato")).status, 401);
res = await get("/v1/summary?app=dev.antigravity.fluidglass", TOKEN);
assert.equal(res.status, 200);
const summary = await res.json();
assert.equal(summary.crashes.length, 1);
assert.equal(summary.crashes[0].count, 1);
assert.equal(summary.crashes[0].type, "java.lang.IllegalStateException");
assert.deepEqual(summary.exits.map((e) => [e.reason, e.count]), [["anr", 1]]);
// Il telefono di fascia bassa con più ritardo viene per primo: 400 ms su 5 s = 80 ms/s (critico per Apple, sopra 10).
assert.equal(summary.frames[0].model, "SM-A528B");
assert.equal(summary.frames[0].hitchRatio, 80);
assert.equal(summary.frames[0].slowShare, 20);
assert.equal(summary.frames.find((f) => f.model === "SM-X710" && f.page === "impostazioni").hitchRatio, 25);
assert.equal((await get("/v1/summary?app=nome con spazi", TOKEN)).status, 400);

// Le build di debug non sporcano il cruscotto.
await post({ schema: 1, reports: [{ ...crash(), source: source({ build: "debug" }) }] });
assert.equal((await (await get("/v1/summary?app=dev.antigravity.fluidglass", TOKEN)).json()).crashes[0].count, 1);

// La pagina del cruscotto e il resto.
res = await get("/cruscotto");
assert.equal(res.status, 200);
assert.match(res.headers.get("content-security-policy"), /default-src 'none'/);
assert.match(await res.text(), /Diagnostica/);
for (const path of ["/", "/v1/reports", "/v1/summary/x", "/favicon.ico"]) {
  res = await get(path);
  assert.equal(res.status, 404, path);
  assert.equal(await res.text(), "", path);
}

// Il cron: via quello che ha più di 90 giorni.
env.DB.sqlite.prepare("UPDATE rapporti SET received_day = '2026-08-01' WHERE kind = 'exit'").run();
await worker.scheduled({}, env, {}, NOW);
assert.equal(Number(env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM rapporti WHERE kind = 'exit'").get().n), 0);
assert.equal(count(), 5);

// Niente IP: il codice non lo legge e i registri di Cloudflare sono spenti.
const code = readFileSync(join(here, "src", "core.js"), "utf8") + readFileSync(join(here, "src", "index.js"), "utf8");
assert.ok(!/cf-connecting-ip|x-forwarded-for|request\.cf/i.test(code.replace(/^\/\/.*$/gm, "")), "il Worker non legge l'indirizzo IP");
const toml = readFileSync(join(here, "wrangler.toml"), "utf8");
assert.match(toml, /\[observability\]\s*\nenabled = false/);
assert.match(toml, /workers_dev = false/);
assert.ok(!/^\s*routes\s*=/m.test(toml), "nessuna rotta di produzione");

// Il modulo principale esporta solo il Worker (workerd rifiuta una costante esportata lì).
assert.deepEqual(Object.keys(entry).sort(), ["fetch", "scheduled"]);
assert.equal((await entry.fetch(new Request("https://diagnostica.example/"), env, {})).status, 404);

console.log("Worker della diagnostica: tutti i controlli passano.");
