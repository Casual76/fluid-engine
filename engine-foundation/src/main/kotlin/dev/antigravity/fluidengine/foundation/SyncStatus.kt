package dev.antigravity.fluidengine.foundation

/** What the last attempt to refresh the app's data did. */
enum class SyncState {
  IDLE,
  SYNCING,
  PARTIAL,
  OFFLINE,
  ERROR,
}

/**
 * The result of the app's last refresh, in the shape the design system's status components read.
 *
 * [notice] is the part worth explaining. Some failures are not failures: a source that has not
 * published this year's data yet, an account without the section you asked for, a server that
 * answers "not yet" on purpose. Reporting those in the same red as an expired session teaches
 * people to ignore red. When [notice] is set, the status components switch to an informational
 * tone, show the sentence as-is, and do not offer a retry — because retrying cannot help.
 */
data class SyncStatus(
  val state: SyncState = SyncState.IDLE,
  val lastSuccessfulSyncEpochMillis: Long? = null,
  val message: String? = null,
  val failedSections: List<String> = emptyList(),
  val notice: String? = null,
)
