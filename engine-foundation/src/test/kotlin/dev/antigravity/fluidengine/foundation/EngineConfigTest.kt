package dev.antigravity.fluidengine.foundation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest is the only part of the engine that can change the behaviour of a build that has
 * already shipped, so its parsing and merging are the parts most worth pinning down.
 */
class EngineConfigTest {

  private val manifest = """
    {
      "app": {
        "id": "demo",
        "packageName": "dev.antigravity.demo",
        "repository": { "repoOwner": "owner", "repoName": "demo" },
        "stable": {
          "version": "2.1.0",
          "changelog": "Cose nuove.",
          "releaseTag": "stable-demo-v2.1.0",
          "apkAsset": "demo-2.1.0.apk",
          "sizeBytes": 12345
        }
      },
      "engine": {
        "schema": 1,
        "minimumVersion": "1.0.0",
        "recommendedVersion": "1.2.0",
        "notice": "Manutenzione stanotte.",
        "flags": { "glassChrome": true, "newAgenda": false },
        "killSwitch": { "enabled": false },
        "overrides": {
          "dev.antigravity.other": {
            "flags": { "newAgenda": true },
            "killSwitch": { "enabled": true, "message": "Versione ritirata." }
          }
        }
      }
    }
  """.trimIndent()

  @Test
  fun `a manifest carries both the release pointer and the control section`() {
    val parsed = EngineManifestParser.parse(manifest)

    assertEquals("2.1.0", parsed.app?.stable?.version)
    assertEquals("owner", parsed.app?.repository?.owner)
    assertEquals(12345L, parsed.app?.stable?.sizeBytes)
    assertEquals("1.2.0", parsed.engine?.recommendedVersion)
  }

  @Test
  fun `an app without an override gets the shared values`() {
    val config = resolveEngineConfig(
      manifest = EngineManifestParser.parse(manifest),
      applicationId = "dev.antigravity.demo",
      fetchedAtEpochMillis = 1_000L,
    )

    assertTrue(config.isEnabled(EngineFlag("glassChrome")))
    assertFalse(config.isEnabled(EngineFlag("newAgenda", default = true)))
    assertFalse(config.killSwitch.enabled)
    assertFalse(config.isFallback)
  }

  @Test
  fun `an override merges over the shared flags instead of replacing them`() {
    val config = resolveEngineConfig(
      manifest = EngineManifestParser.parse(manifest),
      applicationId = "dev.antigravity.other",
      fetchedAtEpochMillis = 1_000L,
    )

    assertTrue("the override only spoke about one flag", config.isEnabled(EngineFlag("glassChrome")))
    assertTrue(config.isEnabled(EngineFlag("newAgenda")))
    assertTrue(config.killSwitch.enabled)
    assertEquals("Versione ritirata.", config.killSwitch.message)
  }

  @Test
  fun `an unknown flag falls back to what the build was tested with`() {
    val config = resolveEngineConfig(
      manifest = EngineManifestParser.parse(manifest),
      applicationId = "dev.antigravity.demo",
      fetchedAtEpochMillis = 1_000L,
    )

    assertTrue(config.isEnabled(EngineFlag("somethingTheManifestNeverHeardOf", default = true)))
    assertFalse(config.isEnabled("alsoUnknown"))
  }

  @Test
  fun `a manifest with no engine section leaves the app on its defaults`() {
    val storeOnly = EngineManifestParser.parse("""{"app":{"stable":{"version":"1.0.0"}}}""")

    val config = resolveEngineConfig(storeOnly, applicationId = "any", fetchedAtEpochMillis = 5L)

    assertNull(config.minimumVersion)
    assertTrue(config.flags.isEmpty())
    assertEquals(EngineCompatibility.OK, config.compatibility(engineVersion = "0.1.0"))
  }

  @Test
  fun `fields the app has never heard of are ignored rather than fatal`() {
    val fromTheFuture = """
      {
        "engine": { "minimumVersion": "1.0.0", "somethingNew": { "nested": true } },
        "unrelated": [1, 2, 3]
      }
    """.trimIndent()

    val config = resolveEngineConfig(
      manifest = EngineManifestParser.parse(fromTheFuture),
      applicationId = "any",
      fetchedAtEpochMillis = null,
    )

    assertEquals("1.0.0", config.minimumVersion)
    assertTrue(config.isFallback)
  }

  @Test
  fun `the version floor is what retires an old build`() {
    val config = resolveEngineConfig(
      manifest = EngineManifestParser.parse(manifest),
      applicationId = "dev.antigravity.demo",
      fetchedAtEpochMillis = 1_000L,
    )

    assertEquals(EngineCompatibility.UPDATE_REQUIRED, config.compatibility(engineVersion = "0.9.9"))
    assertEquals(EngineCompatibility.UPDATE_RECOMMENDED, config.compatibility(engineVersion = "1.1.0"))
    assertEquals(EngineCompatibility.OK, config.compatibility(engineVersion = "1.2.0"))
    assertEquals(EngineCompatibility.OK, config.compatibility(engineVersion = "2.0.0"))
  }
}
