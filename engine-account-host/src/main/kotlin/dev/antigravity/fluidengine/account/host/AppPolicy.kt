package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.WorkKeyGrant

/**
 * La tabella delle app sul telefono (`b\architettura.md` § 2.3; contratto `Pampa-account\contract\v1\apps.json`): chi
 * può chiedere quale pubblico e quali ambiti, chi può avere i luoghi. Sta due volte, qui per rispondere offline
 * «non ammesso» e nel Worker (`src/apps.ts`), che è quella che conta. Tutte e due sono codice rivisto in git.
 *
 * Il pacchetto di chi chiama lo dice il sistema (UID del Binder), mai l'app. Le varianti di sviluppo (`.debug`,
 * `.dev`) contano solo nelle build di sviluppo dello Store ([devBuild]).
 */
class AppPolicy(val apps: Map<String, AppEntry> = DEFAULT, val devBuild: Boolean = false) {

  /** Se l'app può avere i luoghi comuni: mai (MyCloud Gallery: la sua «casa» resta sua), o chiedendo all'utente. */
  enum class Places { NEVER, ASK }

  data class AppEntry(
    val packageName: String,
    val name: String,
    /** Pubblico → ambiti ammessi. Un'app senza pubblici ha solo identità e tema. */
    val audiences: Map<String, Set<String>>,
    val places: Places = Places.NEVER,
    val googleServices: Set<String> = emptySet(),
  )

  /** La voce di [packageName], tenendo conto delle varianti di sviluppo solo se [devBuild]. */
  fun entryFor(packageName: String): AppEntry? {
    apps[packageName]?.let { return it }
    if (!devBuild) return null
    val base = DEV_SUFFIXES.firstNotNullOfOrNull { s -> packageName.takeIf { it.endsWith(s) }?.removeSuffix(s) } ?: return null
    return apps[base]?.copy(packageName = packageName)
  }

  /** L'app può chiedere [audience] con [scopes]? Ambiti vuoti valgono «tutti quelli ammessi». */
  fun allows(packageName: String, audience: String, scopes: Set<String>): Boolean {
    val allowed = entryFor(packageName)?.audiences?.get(audience) ?: return false
    return allowed.containsAll(scopes)
  }

  /** Le chiavi di lavoro da tenere pronte (`CustodyKeyring`): `sync.data` dà la chiave dell'app, `sync.backup` quella del backup. */
  fun workKeyGrants(installed: Collection<String>? = null): List<WorkKeyGrant> = apps.values
    .filter { installed == null || it.packageName in installed }
    .mapNotNull { e ->
      val sync = e.audiences["sync"].orEmpty()
      val purposes = buildSet {
        if ("sync.data" in sync) add(AppKeyPurpose.APP)
        if ("sync.backup" in sync) add(AppKeyPurpose.BACKUP)
      }
      purposes.takeIf { it.isNotEmpty() }?.let { WorkKeyGrant(e.packageName, it) }
    }

  companion object {
    val DEV_SUFFIXES = listOf(".debug", ".dev")

    /** I pacchetti dello Store: il vero e quello di sviluppo (critica M9). */
    const val STORE = "com.pampa.store"
    const val STORE_DEBUG = "com.pampa.store.debug"

    private fun sync(vararg scopes: String) = mapOf("sync" to scopes.toSet())

    /** La tabella di partenza, uguale a `apps.json` del contratto v1 (una prova le confronta). */
    val DEFAULT: Map<String, AppEntry> = listOf(
      AppEntry(STORE, "Pampa Store", mapOf("sync" to setOf("sync.places", "sync.backup"), "persone" to setOf("persone"))),
      AppEntry("dev.pampa.pampanotes", "Pampa Notes", mapOf("notes" to setOf("notes.sync", "notes.share", "notes.relay", "notes.computer"))),
      AppEntry("dev.pampa.pampai", "PampAI", sync("sync.data", "sync.backup"), Places.ASK, setOf("calendar.events", "calendar.readonly")),
      AppEntry("dev.antigravity.classevivaexpressive", "ClasseViva Expressive", sync("sync.backup"), Places.ASK),
      AppEntry("dev.antigravity.fluidtransit", "Fluid Transit", sync("sync.data", "sync.backup"), Places.ASK),
      AppEntry("dev.pampa.fluidweather", "Fluid Weather", sync("sync.backup"), Places.ASK),
      AppEntry("com.p2r3.convert", "Universal Converter", emptyMap()),
      AppEntry("dev.pampa.mycloudgallery", "MyCloud Gallery", emptyMap(), Places.NEVER),
      AppEntry("dev.pampa.fluidify", "Fluidify", sync("sync.backup")),
      AppEntry("dev.pampa.mail", "Pampa Mail", mapOf("persone" to setOf("persone"), "sync" to setOf("sync.backup"))),
    ).associateBy { it.packageName }
  }
}
