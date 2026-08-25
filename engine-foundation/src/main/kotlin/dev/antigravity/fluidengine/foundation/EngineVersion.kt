package dev.antigravity.fluidengine.foundation

/**
 * Which engine this app was built against.
 *
 * Compiled in rather than read at runtime, because that is exactly the fact a remote check needs:
 * the manifest can then say "anything older than 1.4.0 must update" and every app answers honestly.
 *
 * Kept in step with the `ENGINE_VERSION` file at the root of the engine by `tools/engine-release.ps1`,
 * and checked by `tools/engine-doctor.ps1`.
 */
object EngineBuild {
  const val VERSION: String = "1.9.0"
}

/**
 * Orders two versions the way a release train does: numerically segment by segment, with a
 * pre-release suffix sorting *before* the release it leads to, so `1.2.0-beta1` < `1.2.0`.
 *
 * Missing segments count as zero, so `1.2` and `1.2.0` are the same version.
 */
fun compareVersions(left: String, right: String): Int {
  val leftParts = left.substringBefore("-").split(".").map { it.trim().toIntOrNull() ?: 0 }
  val rightParts = right.substringBefore("-").split(".").map { it.trim().toIntOrNull() ?: 0 }
  for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
    val leftValue = leftParts.getOrElse(index) { 0 }
    val rightValue = rightParts.getOrElse(index) { 0 }
    if (leftValue != rightValue) return leftValue.compareTo(rightValue)
  }
  val leftPreRelease = left.substringAfter("-", "")
  val rightPreRelease = right.substringAfter("-", "")
  return when {
    leftPreRelease.isBlank() && rightPreRelease.isNotBlank() -> 1
    leftPreRelease.isNotBlank() && rightPreRelease.isBlank() -> -1
    else -> leftPreRelease.compareTo(rightPreRelease)
  }
}

/**
 * Whether [candidate] is a stable release newer than [current].
 *
 * Pre-releases never qualify: an app on the stable channel that offers its user a `-beta` build has
 * no way back short of a sideload.
 */
fun isStableVersionNewer(candidate: String, current: String): Boolean {
  if (candidate.contains("-")) return false
  return compareVersions(candidate, current) > 0
}

/** How far behind the engine this app carries is. */
enum class EngineCompatibility {
  /** Nothing to do. */
  OK,

  /** A newer engine exists and the app should ship it, but the current one still works. */
  UPDATE_RECOMMENDED,

  /**
   * The engine is older than the manifest's floor.
   *
   * This is the lever that makes one hosted file able to retire an old build across every app: the
   * app cannot rebuild itself, but it can stop trusting a contract it no longer implements ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â and
   * say so, instead of failing in some way the user has to interpret.
   */
  UPDATE_REQUIRED,
}

fun engineCompatibility(
  engineVersion: String = EngineBuild.VERSION,
  minimumVersion: String?,
  recommendedVersion: String?,
): EngineCompatibility = when {
  !minimumVersion.isNullOrBlank() && compareVersions(engineVersion, minimumVersion) < 0 ->
    EngineCompatibility.UPDATE_REQUIRED
  !recommendedVersion.isNullOrBlank() && compareVersions(engineVersion, recommendedVersion) < 0 ->
    EngineCompatibility.UPDATE_RECOMMENDED
  else -> EngineCompatibility.OK
}
