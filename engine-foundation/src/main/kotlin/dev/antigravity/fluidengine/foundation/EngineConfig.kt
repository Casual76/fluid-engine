package dev.antigravity.fluidengine.foundation

/**
 * A flag, declared where it is used.
 *
 * The default is the value the shipped build was tested with, so an app whose network is down, or
 * whose manifest has not mentioned this flag yet, behaves exactly as it did on the day it was
 * released. A flag with no safe default is not a flag — it is a required config value, and it
 * belongs in the build.
 */
data class EngineFlag(
  val key: String,
  val default: Boolean = false,
)

/**
 * The manifest as it applies to *this* app, at this version, right now.
 *
 * Produced by [resolveEngineConfig] and cached, so the app has an answer before the network does.
 */
data class EngineConfig(
  val schema: Int = EngineManifestSchema,
  val minimumVersion: String? = null,
  val recommendedVersion: String? = null,
  val notice: String? = null,
  val flags: Map<String, Boolean> = emptyMap(),
  val killSwitch: EngineKillSwitch = EngineKillSwitch(),
  val fetchedAtEpochMillis: Long? = null,
) {
  fun isEnabled(flag: EngineFlag): Boolean = flags[flag.key] ?: flag.default

  fun isEnabled(key: String, default: Boolean = false): Boolean = flags[key] ?: default

  /** Whether this build's engine is behind what the manifest asks for. */
  fun compatibility(engineVersion: String = EngineBuild.VERSION): EngineCompatibility =
    engineCompatibility(
      engineVersion = engineVersion,
      minimumVersion = minimumVersion,
      recommendedVersion = recommendedVersion,
    )

  /** True before the first successful fetch: every value here is the built-in default. */
  val isFallback: Boolean get() = fetchedAtEpochMillis == null

  companion object {
    /** What the app runs on until a manifest has been read — deliberately, an empty one. */
    val Fallback = EngineConfig()
  }
}

/**
 * Folds the shared section and this app's override into one answer.
 *
 * An override *merges*: a manifest that turns one flag off for one app does not thereby drop the
 * flags it set for everyone. That is the difference between a control file you can edit in a hurry
 * and one you have to re-derive every time.
 */
fun resolveEngineConfig(
  manifest: EngineManifest,
  applicationId: String,
  fetchedAtEpochMillis: Long?,
): EngineConfig {
  val section = manifest.engine ?: return EngineConfig(fetchedAtEpochMillis = fetchedAtEpochMillis)
  val override = section.overrides[applicationId]
  return EngineConfig(
    schema = section.schema,
    minimumVersion = override?.minimumVersion ?: section.minimumVersion,
    recommendedVersion = override?.recommendedVersion ?: section.recommendedVersion,
    notice = override?.notice ?: section.notice,
    flags = section.flags + override?.flags.orEmpty(),
    killSwitch = override?.killSwitch ?: section.killSwitch,
    fetchedAtEpochMillis = fetchedAtEpochMillis,
  )
}
