package dev.antigravity.fluidengine.config

import dev.antigravity.fluidengine.foundation.EngineBuild
import dev.antigravity.fluidengine.foundation.EngineCompatibility
import dev.antigravity.fluidengine.foundation.EngineConfig
import dev.antigravity.fluidengine.foundation.EngineFlag
import dev.antigravity.fluidengine.foundation.EngineManifestParser
import dev.antigravity.fluidengine.foundation.resolveEngineConfig
import dev.antigravity.fluidengine.net.EngineHttp
import dev.antigravity.fluidengine.storage.EngineConfigCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Where an app reads its control file from, and how often.
 *
 * [applicationId] is what lets one file serve several apps: it selects this app's override. It is
 * passed in rather than read from the package name so that a debug build can pretend to be the
 * release one while you are testing a flag.
 */
data class EngineConfigSource(
  val manifestUrl: String,
  val applicationId: String,
  val engineVersion: String = EngineBuild.VERSION,
  val refreshIntervalMillis: Long = DefaultRefreshIntervalMillis,
) {
  companion object {
    /**
     * Six hours.
     *
     * A control file is not a feed. Checking on every cold start would put a network call in front
     * of the first frame for a value that changes a few times a year, and the cached answer is
     * always good enough to draw with.
     */
    const val DefaultRefreshIntervalMillis: Long = 6L * 60L * 60L * 1000L
  }
}

/**
 * The remote half of the engine: the part that can change a shipped build's behaviour.
 *
 * The contract worth keeping in mind is that **this changes behaviour, never code**. Android has no
 * way to swap compiled classes in a shipped app that is both stable and allowed, so what a hosted
 * file can do is choose between paths the build already contains: turn a finished feature on, take
 * a broken one out of service, raise the floor on which engine version is still trusted, or say
 * something to the user. Anything else needs a new build — see `docs/06-limiti.md`.
 *
 * [config] always emits: before the first fetch it emits [EngineConfig.Fallback], where every flag
 * resolves to the default compiled into the build. An app that never reaches the network behaves
 * exactly as it did the day it was released.
 */
class EngineRemoteConfig(
  private val http: EngineHttp,
  private val cache: EngineConfigCache,
  private val source: EngineConfigSource,
  private val now: () -> Long = { System.currentTimeMillis() },
) {

  val config: Flow<EngineConfig> = cache.cached
    .map { cached ->
      if (cached == null) {
        EngineConfig.Fallback
      } else {
        runCatching {
          resolveEngineConfig(
            manifest = EngineManifestParser.parse(cached.raw),
            applicationId = source.applicationId,
            fetchedAtEpochMillis = cached.fetchedAtEpochMillis,
          )
        }.getOrDefault(EngineConfig.Fallback)
      }
    }
    .distinctUntilChanged()

  /** One flag, as a flow, for a screen that wants to react the moment a refresh lands. */
  fun flag(flag: EngineFlag): Flow<Boolean> =
    config.map { it.isEnabled(flag) }.distinctUntilChanged()

  suspend fun current(): EngineConfig = config.first()

  suspend fun compatibility(): EngineCompatibility =
    current().compatibility(engineVersion = source.engineVersion)

  /**
   * Fetches the manifest and caches it.
   *
   * A failure leaves the previous answer in place rather than falling back to defaults: losing the
   * network is not a reason to change how the app behaves.
   */
  suspend fun refresh(): Result<EngineConfig> = runCatching {
    val raw = http.readText(source.manifestUrl)
    // Parsed before it is stored, so a truncated or malformed download cannot become the cached
    // answer for the next six hours.
    val manifest = EngineManifestParser.parse(raw)
    val fetchedAt = now()
    cache.store(raw = raw, fetchedAtEpochMillis = fetchedAt)
    resolveEngineConfig(
      manifest = manifest,
      applicationId = source.applicationId,
      fetchedAtEpochMillis = fetchedAt,
    )
  }

  /** Refreshes only if the cached copy is older than [EngineConfigSource.refreshIntervalMillis]. */
  suspend fun refreshIfStale(): Result<EngineConfig>? {
    val cached = cache.cached.first()
    val age = cached?.let { now() - it.fetchedAtEpochMillis }
    return if (age == null || age >= source.refreshIntervalMillis) refresh() else null
  }
}
