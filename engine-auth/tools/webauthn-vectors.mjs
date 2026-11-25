// F10.2 · I vettori WebAuthn dei test JVM di engine-auth, fatti con il server vero dell'Account Pampa.
//
// Cosa fa. Prende una copia del server (la repo Pampa-account, o una sua estrazione a un commit) e, tutto in locale,
// senza rete:
//   1. genera le opzioni di creazione e di accesso con le funzioni del server (src/webauthn.ts, @simplewebauthn/server);
//   2. fa rispondere l'autenticatore finto del server (test/fake/authenticator.ts) come risponde Credential Manager,
//      con i risultati del PRF dentro, come li dà il gestore al telefono;
//   3. toglie i risultati del PRF, come deve fare engine-auth prima di spedire (chiavi.md § 3.2, K6);
//   4. fa verificare al server la risposta pulita, e controlla che quella col PRF dentro sia rifiutata.
// Il test JVM (FluidWebAuthnContractTest) controlla che engine-auth tolga gli stessi campi e legga gli stessi flag.
//
// Uso, dalla radice dell'engine:
//   node engine-auth/tools/webauthn-vectors.mjs <cartella del server> <cartella con node_modules> > \
//     engine-auth/src/test/resources/pampa-account/webauthn-vectors.json
// La prima cartella può essere la repo stessa o un `git archive f4f23b2 src test contract` estratto altrove; la seconda
// è quella che ha `node_modules` (di solito la repo). Le chiavi delle passkey finte sono nuove a ogni giro: i vettori
// cambiano, la forma no.
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { createRequire } from "node:module";

const [serverDir, modulesDir = serverDir] = process.argv.slice(2).map((p) => resolve(p));
if (!serverDir) {
  console.error("uso: node webauthn-vectors.mjs <cartella del server> [cartella con node_modules]");
  process.exit(2);
}
const require = createRequire(join(modulesDir, "package.json"));
const esbuild = require("esbuild");

// L'impronta di pampa.jks letta dagli APK pubblicati (F10.1, rapporto § 6) e l'RP ID del server.
const CERT = "82:57:38:50:0C:E9:60:54:29:FF:BA:3C:40:19:22:8F:99:2F:2C:37:D1:B7:0A:22:30:A7:CE:2C:25:63:7E:43";
const RP_ID = "pampa-apps.com";

const entry = `
import { FakeAuthenticator } from "./test/fake/authenticator";
import { registrationOptions, authenticationOptions, verifyRegistration, verifyAuthentication, androidOrigin } from "./src/webauthn";
export { FakeAuthenticator, registrationOptions, authenticationOptions, verifyRegistration, verifyAuthentication, androidOrigin };
`;
const out = join(mkdtempSync(join(tmpdir(), "webauthn-vectors-")), "bundle.mjs");
await esbuild.build({
  stdin: { contents: entry, resolveDir: serverDir, loader: "ts" },
  bundle: true,
  platform: "node",
  format: "esm",
  outfile: out,
  nodePaths: [join(modulesDir, "node_modules")],
  logLevel: "error",
});
const lib = await import(pathToFileURL(out).href);

const env = { RP_ID, RP_NAME: "Account Pampa", SIGNING_CERT_SHA256: CERT };
const origin = lib.androidOrigin(CERT);

const b64url = (bytes) => Buffer.from(bytes).toString("base64url");
const fixed = (seed, n = 32) => b64url(Uint8Array.from({ length: n }, (_, i) => (seed * 31 + i * 7) & 255));

/** Quello che fa engine-auth prima di spedire: via `prf.results`, e via `prf` se resta vuoto. */
function forServer(response) {
  const copy = structuredClone(response);
  const prf = copy.clientExtensionResults?.prf;
  if (prf) {
    delete prf.results;
    if (Object.keys(prf).length === 0) delete copy.clientExtensionResults.prf;
  }
  return copy;
}

async function rejection(fn) {
  try {
    await fn();
    return null;
  } catch (err) {
    return err?.details?.reason ?? err?.message ?? String(err);
  }
}

async function makeCase(name, alg, synced, seed) {
  const authenticator = new lib.FakeAuthenticator({ origin, alg, synced, packageName: "com.pampa.store" });
  const regChallenge = fixed(seed);
  const userId = fixed(seed + 1, 16);
  const registrationOptions = await lib.registrationOptions(env, {
    challenge: regChallenge,
    webauthnUserId: userId,
    displayName: "Persona",
    exclude: [],
  });
  const prfAtCreation = fixed(seed + 2);
  const created = await authenticator.create(registrationOptions, { prfResults: prfAtCreation });
  const createdForServer = forServer(created);
  const rawCreationRejected = await rejection(() => lib.verifyRegistration(env, created, regChallenge));
  const registered = await lib.verifyRegistration(env, createdForServer, regChallenge);

  const loginChallenge = fixed(seed + 3);
  const authenticationOptions = await lib.authenticationOptions(env, loginChallenge);
  const prfAtLogin = fixed(seed + 4);
  const asserted = await authenticator.get(authenticationOptions, { prfResults: prfAtLogin });
  const assertedForServer = forServer(asserted);
  const stored = {
    credential_id: registered.credentialId,
    public_key: registered.publicKey,
    sign_count: registered.counter,
    transports: registered.transports ? JSON.stringify(registered.transports) : null,
  };
  const rawAssertionRejected = await rejection(() => lib.verifyAuthentication(env, asserted, loginChallenge, stored));
  const verified = await lib.verifyAuthentication(env, assertedForServer, loginChallenge, stored);

  return {
    name,
    alg,
    synced,
    registrationOptions,
    creation: {
      credentialManager: created,
      forServer: createdForServer,
      prfFirst: prfAtCreation,
      rawRejected: rawCreationRejected,
      server: {
        credentialId: registered.credentialId,
        alg: registered.alg,
        backupEligible: registered.backupEligible,
        backedUp: registered.backedUp,
        aaguid: registered.aaguid,
      },
    },
    authenticationOptions,
    assertion: {
      credentialManager: asserted,
      forServer: assertedForServer,
      prfFirst: prfAtLogin,
      rawRejected: rawAssertionRejected,
      server: { newCounter: verified.newCounter, backedUp: verified.backedUp },
    },
  };
}

const swa = JSON.parse(readFileSync(join(modulesDir, "node_modules/@simplewebauthn/server/package.json"), "utf8")).version;
const vectors = {
  _readme:
    "Generato da engine-auth/tools/webauthn-vectors.mjs con il server dell'Account Pampa (src/webauthn.ts e " +
    "test/fake/authenticator.ts), in locale. «credentialManager» è la risposta come la dà Credential Manager, coi " +
    "risultati del PRF; «forServer» è la stessa senza, ed è quella che il server ha verificato; «rawRejected» è il " +
    "motivo per cui il server rifiuta quella col PRF dentro. Le chiavi sono finte.",
  source: { repo: "Pampa-account", commit: "f4f23b2", simplewebauthn: swa },
  rpId: RP_ID,
  origin,
  cases: [
    await makeCase("es256-sincronizzata", -7, true, 1),
    await makeCase("eddsa-sincronizzata", -8, true, 2),
    await makeCase("es256-solo-qui", -7, false, 3),
  ],
};
writeFileSync(1, JSON.stringify(vectors, null, 2) + "\n");
