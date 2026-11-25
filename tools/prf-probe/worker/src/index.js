// F10.2 · Il Worker della prova del PRF: su prova.pampa-apps.com serve SOLO /.well-known/assetlinks.json.
//
// È il file che il Gestore password di Google e Samsung Pass leggono per sapere che l'app «Pampa · prova passkey»
// (dev.pampa.prova.passkey, firmata con la chiave di prova) può usare le passkey di prova.pampa-apps.com
// (prerequisiti: https://developer.android.com/identity/credential-manager/prerequisites, fiducia alta):
//   - codice 200, tipo application/json, nessun redirect;
//   - tutto il resto è un 404, senza contenuti;
//   - nessun log: il Worker non scrive niente (e in wrangler.toml l'osservabilità è spenta).
// La radice pampa-apps.com non si tocca (critica S12). Alla fine della prova il Worker si cancella (guida della
// mini-seduta, artifacts\ecosistema\account\f10-2\guida-mini-seduta.md).

// L'impronta SHA-256 del certificato della chiave di prova (tools/prf-probe/keystore/prova-passkey.jks), la stessa di
// EXPECTED_CERT_SHA256 in tools/prf-probe/build.gradle. NON è quella di pampa.jks.
export const PROBE_PACKAGE = "dev.pampa.prova.passkey";
export const PROBE_CERT_SHA256 =
  "D3:DC:F3:C6:EF:11:DD:4C:09:D7:3E:5B:B3:FA:D5:DD:B0:55:E5:32:71:36:FB:94:41:2A:9C:3A:AF:E8:A6:0C";

// Una relazione sola, get_login_creds: l'app di prova usa le passkey e non apre link (scelta di Alessio del
// 25/11/2026, D2 di F10.2). Il file vero dell'account, su pampa-apps.com, tiene anche handle_all_urls.
export const ASSETLINKS = JSON.stringify([
  {
    relation: ["delegate_permission/common.get_login_creds"],
    target: {
      namespace: "android_app",
      package_name: PROBE_PACKAGE,
      sha256_cert_fingerprints: [PROBE_CERT_SHA256],
    },
  },
]);

const HEADERS = {
  "content-type": "application/json",
  "cache-control": "public, max-age=300",
  "x-content-type-options": "nosniff",
};

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/.well-known/assetlinks.json" && (request.method === "GET" || request.method === "HEAD")) {
      return new Response(request.method === "HEAD" ? null : ASSETLINKS, { status: 200, headers: HEADERS });
    }
    return new Response(null, { status: 404 });
  },
};
