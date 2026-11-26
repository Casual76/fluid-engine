// F8.24 · Il cuore del Worker della diagnostica dal campo (engine-diagnostics): la validazione, il salvataggio, il
// cruscotto. Sta a parte da index.js perché workerd tratta ogni esportazione del modulo principale come un punto
// d'ingresso, e una costante lì fa fallire `wrangler dev` (visto il 26/11/2026). Tre strade:
//   POST /v1/reports   i rapporti dei telefoni, col sì dell'utente: {"schema":1,"reports":[…]}
//   GET  /v1/summary   il cruscotto per app e modello di telefono, con il segreto DASHBOARD_TOKEN
//   GET  /cruscotto    la pagina del cruscotto (chiede il segreto e legge /v1/summary)
// Tutto il resto è un 404 vuoto.
//
// Le regole della scheda `crash-e-lentezze`:
//   - nessun indirizzo IP salvato né letto (CF-Connecting-IP non si guarda mai, e i registri di Cloudflare sono spenti);
//   - ogni campo è validato: un campo che non conosciamo, un testo troppo lungo o un tipo sbagliato rifiutano il rapporto
//     intero (niente spazio per un dato personale infilato in un campo libero);
//   - ogni rapporto ha un id a caso: lo stesso invio ripetuto conta una volta (INSERT OR IGNORE);
//   - dopo 90 giorni i rapporti si cancellano (il cron di ogni notte).

export const SCHEMA = 1;
export const MAX_BODY_BYTES = 128 * 1024;
export const MAX_REPORTS = 100;
export const RETENTION_DAYS = 90;

const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const APP_ID = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
const VERSION = /^[0-9A-Za-z.+_-]{1,40}$/;
const PAGE = /^[A-Za-z0-9._:/-]{1,48}$/;
const DAY = /^\d{4}-\d{2}-\d{2}$/;
const SIGNATURE = /^[0-9a-f]{16}$/;
// Una riga del codice: pacchetto.Classe.metodo(File.kt:12). Niente spazi a parte «Native Method» e «Unknown Source».
const FRAME = /^[\w$.<>\-]+\((?:[\w$.\-]+(?::\d+)?|Native [Mm]ethod|Unknown Source)\)$/;
const TYPE = /^[\w$.]{1,200}$/;
const MODEL = /^[\w .()+\-]{1,60}$/;
const REASONS = new Set(["anr", "crash_native", "low_memory", "excessive_resource", "initialization_failure", "dependency_died", "signaled", "freezer"]);
const IMPORTANCE = new Set(["foreground", "background"]);
const BUCKETS = 11;

class Invalid extends Error {}

function check(condition, what) {
  if (!condition) throw new Invalid(what);
}

function onlyKeys(object, allowed, what) {
  check(object !== null && typeof object === "object" && !Array.isArray(object), `${what}: non è un oggetto`);
  for (const key of Object.keys(object)) check(allowed.includes(key), `${what}: campo sconosciuto «${key}»`);
}

const int = (v, min, max) => Number.isInteger(v) && v >= min && v <= max;
const str = (v, re) => typeof v === "string" && re.test(v);

function validateSource(s) {
  onlyKeys(s, ["app", "version", "code", "engine", "build", "maker", "model", "sdk", "hz"], "source");
  check(str(s.app, APP_ID) && s.app.length <= 120, "source.app");
  check(typeof s.version === "string" && s.version.length <= 40 && (s.version === "" || VERSION.test(s.version)), "source.version");
  check(int(s.code, 0, Number.MAX_SAFE_INTEGER), "source.code");
  check(str(s.engine, VERSION), "source.engine");
  check(s.build === "debug" || s.build === "release", "source.build");
  check(typeof s.maker === "string" && s.maker.length <= 40 && (s.maker === "" || MODEL.test(s.maker)), "source.maker");
  check(typeof s.model === "string" && s.model.length <= 60 && (s.model === "" || MODEL.test(s.model)), "source.model");
  check(int(s.sdk, 0, 100), "source.sdk");
  check(int(s.hz, 0, 1000), "source.hz");
}

function validateFrames(list, max, what) {
  check(Array.isArray(list) && list.length <= max, `${what}: righe`);
  for (const f of list) check(typeof f === "string" && f.length <= 300 && FRAME.test(f), `${what}: riga del codice`);
}

/** Valida un rapporto; lancia Invalid col motivo. */
export function validateReport(r) {
  onlyKeys(r, ["id", "kind", "hour", "source", "crash", "exit", "frames"], "rapporto");
  check(str(r.id, ID), "id");
  check(int(r.hour, 0, 8.64e15) && r.hour % 3_600_000 === 0, "hour: l'ora intera");
  validateSource(r.source);
  const payloads = ["crash", "exit", "frames"].filter((k) => r[k] !== undefined);
  check(payloads.length === 1 && payloads[0] === r.kind, "kind e contenuto");
  if (r.kind === "crash") {
    const c = r.crash;
    onlyKeys(c, ["type", "causes", "frames", "thread", "signature"], "crash");
    check(str(c.type, TYPE), "crash.type");
    check(Array.isArray(c.causes) && c.causes.length <= 8 && c.causes.every((t) => str(t, TYPE)), "crash.causes");
    validateFrames(c.frames, 40, "crash.frames");
    check(c.thread === "main" || c.thread === "background", "crash.thread");
    check(str(c.signature, SIGNATURE), "crash.signature");
  } else if (r.kind === "exit") {
    const e = r.exit;
    onlyKeys(e, ["reason", "importance", "pssKb", "rssKb", "frames"], "exit");
    check(REASONS.has(e.reason), "exit.reason");
    check(IMPORTANCE.has(e.importance), "exit.importance");
    check(int(e.pssKb, 0, 1e9) && int(e.rssKb, 0, 1e9), "exit.memoria");
    validateFrames(e.frames, 40, "exit.frames");
  } else {
    const f = r.frames;
    onlyKeys(f, ["page", "day", "frames", "slow", "hangs", "frozen", "hitchMs", "durationMs", "worstMs", "buckets"], "frames");
    check(str(f.page, PAGE), "frames.page");
    check(str(f.day, DAY), "frames.day");
    for (const key of ["frames", "slow", "hangs", "frozen", "hitchMs", "durationMs", "worstMs"]) check(int(f[key], 0, 1e12), `frames.${key}`);
    check(f.slow <= f.frames && f.hangs <= f.frames && f.frozen <= f.hangs, "frames: i conti");
    check(Array.isArray(f.buckets) && f.buckets.length === BUCKETS && f.buckets.every((b) => int(b, 0, 1e12)), "frames.buckets");
  }
}

const today = (now) => new Date(now).toISOString().slice(0, 10);

function rowOf(r, now) {
  const s = r.source;
  const f = r.frames;
  return [
    r.id, r.kind, r.hour, today(now), s.app, s.version, s.code, s.engine, s.build, s.maker, s.model, s.sdk, s.hz,
    r.crash?.signature ?? null, r.exit?.reason ?? null, f?.page ?? null,
    f?.frames ?? null, f?.slow ?? null, f?.hangs ?? null, f?.frozen ?? null, f?.hitchMs ?? null, f?.durationMs ?? null,
    JSON.stringify(r),
  ];
}

const INSERT = `INSERT OR IGNORE INTO rapporti (id, kind, hour, received_day, app, version, code, engine, build, maker, model, sdk, hz,
  signature, reason, page, frames, slow, hangs, frozen, hitch_ms, duration_ms, body)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`;

const json = (status, value) => new Response(JSON.stringify(value), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});
const empty = (status) => new Response(null, { status });

async function receive(request, env, now) {
  const type = request.headers.get("content-type") ?? "";
  if (!type.toLowerCase().startsWith("application/json")) return json(415, { error: "serve application/json" });
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (declared > MAX_BODY_BYTES) return json(413, { error: "troppo grande" });
  const bytes = await request.arrayBuffer();
  if (bytes.byteLength > MAX_BODY_BYTES) return json(413, { error: "troppo grande" });
  let body;
  try {
    body = JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return json(400, { error: "JSON non valido" });
  }
  try {
    onlyKeys(body, ["schema", "reports"], "invio");
    check(body.schema === SCHEMA, "schema");
    check(Array.isArray(body.reports) && body.reports.length >= 1 && body.reports.length <= MAX_REPORTS, "reports");
    body.reports.forEach(validateReport);
  } catch (e) {
    if (e instanceof Invalid) return json(400, { error: e.message });
    throw e;
  }
  const statement = env.DB.prepare(INSERT);
  const results = await env.DB.batch(body.reports.map((r) => statement.bind(...rowOf(r, now))));
  const accepted = results.reduce((n, res) => n + (res.meta?.changes ?? 0), 0);
  return json(200, { accepted, duplicates: body.reports.length - accepted });
}

function sameSecret(given, expected) {
  if (typeof given !== "string" || typeof expected !== "string" || expected.length < 16) return false;
  const a = new TextEncoder().encode(given);
  const b = new TextEncoder().encode(expected);
  let diff = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) diff |= (a[i] ?? 0) ^ (b[i] ?? 0);
  return diff === 0;
}

/** Il cruscotto: per app, i crash per firma, le chiusure per motivo, i fotogrammi per modello e pagina. */
export async function summary(env, app, days = 30, now = Date.now()) {
  const since = today(now - days * 86_400_000);
  const where = "app = ? AND received_day >= ? AND build = 'release'";
  const crashes = await env.DB.prepare(
    `SELECT signature, COUNT(*) AS count, MAX(version) AS last_version, COUNT(DISTINCT model) AS models,
       (SELECT body FROM rapporti r2 WHERE r2.signature = r.signature AND r2.app = r.app ORDER BY hour DESC LIMIT 1) AS sample
     FROM rapporti r WHERE kind = 'crash' AND ${where} GROUP BY signature ORDER BY count DESC LIMIT 50`,
  ).bind(app, since).all();
  const exits = await env.DB.prepare(
    `SELECT reason, COUNT(*) AS count, COUNT(DISTINCT model) AS models FROM rapporti
     WHERE kind = 'exit' AND ${where} GROUP BY reason ORDER BY count DESC`,
  ).bind(app, since).all();
  const frames = await env.DB.prepare(
    `SELECT maker, model, page, SUM(frames) AS frames, SUM(slow) AS slow, SUM(hangs) AS hangs, SUM(frozen) AS frozen,
       SUM(hitch_ms) AS hitch_ms, SUM(duration_ms) AS duration_ms
     FROM rapporti WHERE kind = 'frames' AND ${where} GROUP BY maker, model, page
     ORDER BY CAST(SUM(hitch_ms) AS REAL) / MAX(SUM(duration_ms), 1) DESC LIMIT 200`,
  ).bind(app, since).all();
  return {
    app,
    since,
    crashes: crashes.results.map((c) => {
      const sample = JSON.parse(c.sample ?? "{}");
      return { signature: c.signature, count: c.count, lastVersion: c.last_version, models: c.models, type: sample.crash?.type, top: sample.crash?.frames?.slice(0, 5) ?? [] };
    }),
    exits: exits.results,
    frames: frames.results.map((f) => ({
      ...f,
      // Il rapporto dei ritardi di Apple, in ms per secondo: sotto 5 bene, da 5 a 10 si vede, da 10 disturba.
      hitchRatio: f.duration_ms > 0 ? Math.round((f.hitch_ms * 1000 / f.duration_ms) * 10) / 10 : 0,
      slowShare: f.frames > 0 ? Math.round((f.slow / f.frames) * 1000) / 10 : 0,
    })),
  };
}

export const DASHBOARD_HTML = `<!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Diagnostica Pampa</title><style>body{font:15px system-ui,sans-serif;margin:16px;max-width:960px}table{border-collapse:collapse;width:100%;margin:8px 0 24px}
td,th{border-bottom:1px solid #ddd;padding:4px 6px;text-align:left;font-variant-numeric:tabular-nums}code{font-size:12px}input{font:inherit;padding:4px}</style></head>
<body><h1>Diagnostica</h1><form id="f"><input id="app" placeholder="app (es. dev.antigravity.fluidglass)" size="36" required>
<input id="token" type="password" placeholder="segreto" required> <button>Leggi</button></form><div id="out"></div>
<script>
document.getElementById('f').onsubmit = async (e) => {
  e.preventDefault();
  const app = document.getElementById('app').value, token = document.getElementById('token').value;
  const res = await fetch('/v1/summary?app=' + encodeURIComponent(app), { headers: { authorization: 'Bearer ' + token } });
  const out = document.getElementById('out');
  if (!res.ok) { out.textContent = 'Errore ' + res.status; return; }
  const s = await res.json();
  const esc = (v) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const table = (rows, cols) => '<table><tr>' + cols.map((c) => '<th>' + c[0] + '</th>').join('') + '</tr>' +
    rows.map((r) => '<tr>' + cols.map((c) => '<td>' + c[1](r) + '</td>').join('') + '</tr>').join('') + '</table>';
  out.innerHTML = '<h2>Crash</h2>' + table(s.crashes, [['Quante', (r) => r.count], ['Tipo', (r) => esc(r.type)], ['Dove', (r) => '<code>' + r.top.map(esc).join('<br>') + '</code>'], ['Ultima versione', (r) => esc(r.lastVersion)], ['Telefoni', (r) => r.models]]) +
    '<h2>Chiusure del sistema</h2>' + table(s.exits, [['Motivo', (r) => esc(r.reason)], ['Quante', (r) => r.count], ['Telefoni', (r) => r.models]]) +
    '<h2>Fotogrammi</h2>' + table(s.frames, [['Telefono', (r) => esc(r.maker + ' ' + r.model)], ['Pagina', (r) => esc(r.page)], ['Fotogrammi', (r) => r.frames], ['Lenti %', (r) => r.slowShare], ['Blocchi', (r) => r.hangs], ['Ritardo ms/s', (r) => r.hitchRatio]]);
};
</script></body></html>`;

export const handlers = {
  async fetch(request, env, ctx, now = Date.now()) {
    const url = new URL(request.url);
    if (url.pathname === "/v1/reports" && request.method === "POST") return receive(request, env, now);
    if (url.pathname === "/v1/summary" && request.method === "GET") {
      const auth = request.headers.get("authorization") ?? "";
      if (!sameSecret(auth.replace(/^Bearer /, ""), env.DASHBOARD_TOKEN)) return empty(401);
      const app = url.searchParams.get("app") ?? "";
      if (!APP_ID.test(app)) return json(400, { error: "app" });
      return json(200, await summary(env, app, 30, now));
    }
    if (url.pathname === "/cruscotto" && request.method === "GET") {
      return new Response(DASHBOARD_HTML, {
        headers: {
          "content-type": "text/html; charset=utf-8",
          "content-security-policy": "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'none'",
          "cache-control": "no-store",
        },
      });
    }
    return empty(404);
  },

  /** Ogni notte: via i rapporti di più di 90 giorni. */
  async scheduled(event, env, ctx, now = Date.now()) {
    await env.DB.prepare("DELETE FROM rapporti WHERE received_day < ?").bind(today(now - RETENTION_DAYS * 86_400_000)).run();
  },
};
