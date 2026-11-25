// F10.2 · La prova del Worker, in locale e senza rete: `node tools/prf-probe/worker/test.mjs` dalla radice dell'engine.
// Chiama la fetch del Worker con richieste finte (Node ha Request e Response) e controlla che:
//   - /.well-known/assetlinks.json sia 200, JSON, senza redirect, con l'app di prova, la sua impronta e la sola
//     relazione get_login_creds;
//   - l'impronta sia la stessa di tools/prf-probe/build.gradle (EXPECTED_CERT_SHA256);
//   - tutto il resto sia un 404 vuoto, anche sulla radice e con un POST;
//   - non ci siano pampa-apps.com come RP né il pacchetto di Pampa Store (critica S12).
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import worker, { ASSETLINKS, PROBE_CERT_SHA256, PROBE_PACKAGE } from "./src/index.js";

const here = dirname(fileURLToPath(import.meta.url));
const call = (path, method = "GET") => worker.fetch(new Request(`https://prova.pampa-apps.com${path}`, { method }));

const ok = await call("/.well-known/assetlinks.json");
assert.equal(ok.status, 200);
assert.equal(ok.headers.get("content-type"), "application/json");
assert.equal(ok.headers.get("location"), null);
const body = await ok.json();
assert.deepEqual(body, JSON.parse(ASSETLINKS));
assert.equal(body.length, 1);
assert.equal(body[0].target.namespace, "android_app");
assert.equal(body[0].target.package_name, "dev.pampa.prova.passkey");
assert.deepEqual(body[0].target.sha256_cert_fingerprints, [PROBE_CERT_SHA256]);
// Solo get_login_creds: niente handle_all_urls nel file di prova (D2 di F10.2).
assert.deepEqual(body[0].relation, ["delegate_permission/common.get_login_creds"]);
assert.ok(!ASSETLINKS.includes("handle_all_urls"));
assert.match(PROBE_CERT_SHA256, /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/);

const head = await call("/.well-known/assetlinks.json", "HEAD");
assert.equal(head.status, 200);
assert.equal(await head.text(), "");

for (const [path, method] of [["/", "GET"], ["/index.html", "GET"], ["/.well-known/assetlinks.json", "POST"], ["/.well-known/jwks.json", "GET"], ["/.well-known/assetlinks.json/", "GET"]]) {
  const res = await call(path, method);
  assert.equal(res.status, 404, `${method} ${path}`);
  assert.equal(await res.text(), "", `${method} ${path}`);
}

// La stessa impronta dell'app di prova.
const gradle = readFileSync(join(here, "..", "build.gradle"), "utf8");
const expected = gradle.match(/EXPECTED_CERT_SHA256',\s*'"([0-9A-F:]+)"'/)?.[1];
assert.equal(expected, PROBE_CERT_SHA256, "l'impronta del Worker e quella di build.gradle devono coincidere");
assert.ok(gradle.includes(`applicationId '${PROBE_PACKAGE}'`));

// Niente di Pampa Store, niente impronta di pampa.jks (F10.1, rapporto § 6).
assert.ok(!ASSETLINKS.includes("com.pampa.store"));
assert.ok(!ASSETLINKS.includes("82:57:38:50"));

console.log("Worker di prova: tutti i controlli passano.");
