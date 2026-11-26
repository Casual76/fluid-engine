# Health Connect

Il modulo facoltativo `engine-health` dipende, **come libreria binaria** (non come sorgente), dal client di Health
Connect di AndroidX.

- Progetto: **AndroidX Health Connect**, dell'Android Open Source Project
- Pagina: https://developer.android.com/jetpack/androidx/releases/health-connect
- Artefatto: `androidx.health.connect:connect-client:1.1.0` (Google Maven), fissato in `versions.gradle`
  (`engine.healthConnect`); porta Guava (`com.google.guava:guava`, android) e `kotlinx-coroutines-guava`
- Licenza: **Apache License 2.0** (anche Guava)

## Cosa usiamo

| Dove sta da noi | Cosa usa |
|---|---|
| `engine-health/src/main/kotlin/dev/antigravity/fluidengine/health/FluidHealthGateway.kt` | `HealthConnectClient` (lettura a pagine, modifiche col segno, scrittura), `PermissionController`, i record dei passi, del sonno, del battito, degli allenamenti, del peso e dell'acqua |
| `engine-health/src/test/…/FluidHealthConnectGatewayTest.kt` | l'interfaccia `HealthConnectClient`, implementata da un client finto nei test |

Health Connect è un servizio del telefono: i dati restano di chi li ha scritti e dell'utente, che dà i permessi misura
per misura. L'avviso viaggia con l'app nell'elenco dei crediti: un'app che include `engine-health` aggiunge
`FluidHealthLicenses` a `fluidLicensesSection(licences = …)`. Il testo della licenza è in
https://www.apache.org/licenses/LICENSE-2.0.
