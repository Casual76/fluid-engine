package dev.antigravity.fluidengine.foundation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The one file that every app built on the engine reads.
 *
 * It carries two unrelated things on purpose. `app` is the release pointer an in-app updater needs
 * — the shape the Pampa Store already publishes, so an existing manifest keeps working untouched.
 * `engine` is the part that can change how an already-installed build behaves: flags, a floor on
 * the engine version, a message, a kill switch. Editing that section changes every app that reads
 * the file, at their next check, without a single rebuild.
 *
 * Everything is optional. A manifest with only `app` is a store manifest; one with only `engine` is
 * a control file; one with both is what a shipped app usually points at.
 */
data class EngineManifest(
  val app: AppReleases? = null,
  val engine: EngineSection? = null,
)

data class AppReleases(
  val id: String = "",
  val packageName: String = "",
  val repository: ReleaseRepository? = null,
  val stable: AppRelease? = null,
  val beta: AppRelease? = null,
)

data class ReleaseRepository(
  val owner: String,
  val name: String,
)

data class AppRelease(
  val version: String,
  val changelog: String = "",
  val releaseTag: String = "",
  val apkAsset: String = "",
  val sizeBytes: Long = 0L,
  val releaseDate: String = "",
)

/**
 * The remotely controlled half of the manifest.
 *
 * [overrides] is keyed by application id: one file can serve every app you ship, and still say
 * something different to one of them. An override is merged over the shared values rather than
 * replacing them, so turning one flag off for one app does not silently drop the rest.
 */
data class EngineSection(
  val schema: Int = EngineManifestSchema,
  val minimumVersion: String? = null,
  val recommendedVersion: String? = null,
  val notice: String? = null,
  val flags: Map<String, Boolean> = emptyMap(),
  val killSwitch: EngineKillSwitch = EngineKillSwitch(),
  val overrides: Map<String, EngineOverride> = emptyMap(),
)

data class EngineOverride(
  val minimumVersion: String? = null,
  val recommendedVersion: String? = null,
  val notice: String? = null,
  val flags: Map<String, Boolean> = emptyMap(),
  val killSwitch: EngineKillSwitch? = null,
)

/**
 * The last resort: a remote instruction to stop.
 *
 * For the case where a shipped build is actively doing harm — talking to an endpoint that now
 * charges money, corrupting stored data, spamming a server. What the app does with it is the app's
 * choice; the engine only reports it, with the sentence to show.
 */
data class EngineKillSwitch(
  val enabled: Boolean = false,
  val message: String? = null,
)

/** Bumped only when the meaning of an existing field changes; new optional fields do not count. */
const val EngineManifestSchema: Int = 1

/**
 * Reads a manifest without a serialization plugin.
 *
 * Hand-parsed through the `JsonElement` API for a reason: `@Serializable` would drag the Kotlin
 * serialization compiler plugin into the engine and therefore into every app that hosts it, for a
 * document with fifteen fields. Unknown fields are ignored, which is what lets a newer manifest be
 * served to an older app.
 */
object EngineManifestParser {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parse(raw: String): EngineManifest {
    val root = json.parseToJsonElement(raw).jsonObject
    return EngineManifest(
      app = root.obj("app")?.toAppReleases(),
      engine = root.obj("engine")?.toEngineSection(),
    )
  }

  private fun JsonObject.toAppReleases(): AppReleases = AppReleases(
    id = string("id").orEmpty(),
    packageName = string("packageName").orEmpty(),
    repository = obj("repository")?.let { repository ->
      val owner = repository.string("repoOwner") ?: repository.string("owner")
      val name = repository.string("repoName") ?: repository.string("name")
      if (owner.isNullOrBlank() || name.isNullOrBlank()) null else ReleaseRepository(owner, name)
    },
    stable = obj("stable")?.toRelease(),
    beta = obj("beta")?.toRelease(),
  )

  private fun JsonObject.toRelease(): AppRelease? {
    val version = string("version")?.takeIf { it.isNotBlank() } ?: return null
    return AppRelease(
      version = version,
      changelog = string("changelog").orEmpty(),
      releaseTag = string("releaseTag").orEmpty(),
      apkAsset = string("apkAsset").orEmpty(),
      sizeBytes = long("sizeBytes") ?: 0L,
      releaseDate = string("releaseDate").orEmpty(),
    )
  }

  private fun JsonObject.toEngineSection(): EngineSection = EngineSection(
    schema = long("schema")?.toInt() ?: EngineManifestSchema,
    minimumVersion = string("minimumVersion"),
    recommendedVersion = string("recommendedVersion"),
    notice = string("notice"),
    flags = obj("flags").toFlags(),
    killSwitch = obj("killSwitch")?.toKillSwitch() ?: EngineKillSwitch(),
    overrides = obj("overrides")?.mapValues { (_, value) ->
      (value as? JsonObject)?.toOverride() ?: EngineOverride()
    }.orEmpty(),
  )

  private fun JsonObject.toOverride(): EngineOverride = EngineOverride(
    minimumVersion = string("minimumVersion"),
    recommendedVersion = string("recommendedVersion"),
    notice = string("notice"),
    flags = obj("flags").toFlags(),
    killSwitch = obj("killSwitch")?.toKillSwitch(),
  )

  private fun JsonObject.toKillSwitch(): EngineKillSwitch = EngineKillSwitch(
    enabled = bool("enabled") ?: false,
    message = string("message"),
  )

  private fun JsonObject?.toFlags(): Map<String, Boolean> = this
    ?.mapNotNull { (key, value) ->
      val flag = runCatching { value.jsonPrimitive.booleanOrNull }.getOrNull() ?: return@mapNotNull null
      key to flag
    }
    ?.toMap()
    .orEmpty()

  private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

  private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it != "null" }

  private fun JsonObject.long(key: String): Long? =
    runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()

  private fun JsonObject.bool(key: String): Boolean? =
    runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()
}
