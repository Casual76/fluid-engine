package dev.antigravity.fluidengine.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F6C.8 · (8) La build controlla le famiglie (F6C-h). Il grafo vero lo controlla `checkViewerFamilies`, che gira prima
 * di questi test e si prova da solo su grafi finti; qui si legge che i quattro posti che ne parlano dicono la stessa
 * cosa: la lista ammessa (`families.json`), `engine-viewer-all`, `settings.gradle` e `tools/engine-install.ps1`.
 */
class FluidViewerFamiliesBuildTest {

  private val module = File(System.getProperty("user.dir") ?: ".").canonicalFile
  private val root = module.parentFile

  private data class Family(val module: String, val family: String, val pkg: String, val weight: String, val test: Boolean, val libraries: List<String>)

  // Un JSON piccolo e nostro: basta leggere gli oggetti delle famiglie con le loro chiavi.
  private val families: List<Family> by lazy {
    val text = module.resolve("families.json").readText()
    val list = text.substringAfter("\"families\"")
    Regex("""\{\s*"module"(.*?)"libraries"\s*:\s*\[(.*?)]\s*}""", RegexOption.DOT_MATCHES_ALL).findAll(list).map { m ->
      val body = "\"module\"" + m.groupValues[1]
      fun field(name: String) = Regex(""""$name"\s*:\s*"([^"]*)"""").find(body)?.groupValues?.get(1).orEmpty()
      Family(field("module"), field("family"), field("package"), field("weight"), Regex(""""test"\s*:\s*true""").containsMatchIn(body),
        Regex(""""coordinates"\s*:\s*"([^"]*)"""").findAll(m.groupValues[2]).map { it.groupValues[1] }.toList())
    }.toList()
  }

  @Test
  fun `the admitted list has the families of the second wave, AR and the test module`() {
    assertEquals(
      listOf("engine-viewer-office", "engine-viewer-archive", "engine-viewer-data", "engine-viewer-graphics", "engine-viewer-3d", "engine-viewer-ar", "engine-viewer-ocr", "engine-viewer-prova"),
      families.map { it.module },
    )
    assertEquals(listOf("office", "archive", "data", "graphics", "3d", "ar", "ocr", "prova"), families.map { it.family })
    assertEquals(listOf("engine-viewer-prova"), families.filter { it.test }.map { it.module })
    for (f in families) {
      assertTrue("${f.module}: il pacchetto", f.pkg.startsWith("dev.antigravity.fluidengine.viewer."))
      assertTrue("${f.module}: il peso", f.weight.isNotBlank())
    }
    // Le librerie esterne scelte da Alessio il 22 novembre, ognuna nella sua famiglia sola.
    // Archivi: libarchive, piu' il ripiego per i soli 7z cifrati approvato il 22 novembre (F6C.12) con le sue dipendenze.
    assertEquals(
      listOf(
        "me.zhanghai.android.libarchive:library", "org.apache.commons:commons-compress", "org.tukaani:xz",
        "commons-io:commons-io", "commons-codec:commons-codec", "org.apache.commons:commons-lang3",
      ),
      families.single { it.family == "archive" }.libraries,
    )
    assertEquals(listOf("com.caverock:androidsvg-aar", "androidx.exifinterface:exifinterface"), families.single { it.family == "graphics" }.libraries)
    assertTrue(families.single { it.family == "3d" }.libraries.all { it.startsWith("com.google.android.filament:") })
    assertEquals(emptyList<String>(), families.single { it.family == "office" }.libraries)
    assertEquals(emptyList<String>(), families.single { it.family == "data" }.libraries)
    // Ogni libreria ha licenza e peso.
    val text = module.resolve("families.json").readText()
    val libraries = Regex("""\{\s*"coordinates"[^}]*}""").findAll(text).map { it.value }.toList()
    assertEquals(families.sumOf { it.libraries.size }, libraries.size)
    assertTrue(libraries.all { "\"license\"" in it && "\"weight\"" in it })
  }

  @Test
  fun `engine-viewer-all has one line for every real family, never the test module, and only api dependencies`() {
    val script = root.resolve("engine-viewer-all/build.gradle").readLines().map { it.substringBefore("//").trim() }.filter { it.isNotEmpty() }
    val lines = script.mapNotNull { Regex("""^family\('(:[^']+)'\)$""").find(it)?.groupValues?.get(1) }
    assertEquals(families.filter { !it.test }.map { ":" + it.module }, lines)
    val dependencies = script.filter { Regex("""^(api|implementation|compileOnly|runtimeOnly)\b""").containsMatchIn(it) }
    assertEquals(listOf("api project(':engine-viewer')"), dependencies)
    assertTrue("niente codice", root.resolve("engine-viewer-all/src/main").walkTopDown().none { it.isFile && (it.extension == "kt" || it.extension == "java") })
    assertTrue(script.any { it.contains("viewer-families.gradle") })
  }

  @Test
  fun `settings includes engine-viewer-all and the test module, engine-install knows the families`() {
    val settings = root.resolve("settings.gradle").readText()
    assertTrue(settings.contains("include ':engine-viewer-all'"))
    assertTrue(settings.contains("include ':engine-viewer-prova'"))
    assertTrue(settings.contains("include ':engine-viewer-prova-app'"))
    val install = root.resolve("tools/engine-install.ps1").readText()
    assertTrue("engine-install legge la lista dell'engine", install.contains("engine-viewer\\families.json"))
    assertTrue(install.contains("\"engine-viewer-all\""))
    assertTrue("il modulo di prova non si offre", install.contains("\$family.test -eq \$true"))
    assertTrue("solo ASCII: PowerShell 5.1 lo legge con la codepage di sistema", install.all { it.code < 128 })
  }

  @Test
  fun `the check is wired before the tests and proves itself`() {
    val build = module.resolve("build.gradle").readText()
    assertTrue(build.contains("apply from: \"\${projectDir}/viewer-families.gradle\""))
    assertTrue(build.contains("tasks.withType(Test).configureEach { dependsOn 'checkViewerFamilies' }"))
    assertTrue(build.contains("dependsOn ':engine-viewer-all:checkViewerFamilies'"))
    val check = module.resolve("viewer-families.gradle").readText()
    for (case in listOf("libreria fuori lista", "engine-ui arriva a una famiglia", "engine-viewer arriva a una famiglia",
      "modulo non ammesso", "famiglia dimenticata da engine-viewer-all", "il modulo di prova in engine-viewer-all")) {
      assertTrue("la prova del controllo ha il caso «$case»", check.contains("name: '$case'"))
    }
  }
}
