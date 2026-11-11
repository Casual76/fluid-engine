package dev.antigravity.fluidengine.ui.glass.field

import java.io.File
import java.util.regex.Pattern
import kotlin.math.ceil
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Il lint delle sorgenti AGSL dell'engine: gira con i test normali, sul JVM, senza Android e senza compilare.
 *
 * Perché esiste. Dalla 3.0.0 alla 3.1.2 una variabile `half4 flat = …` faceva rifiutare due shader di
 * Fluid-physics a ogni dispositivo, e nessun test se n'è accorto per una settimana. Il banco
 * (`glassbench.py check`) compila sul PC ma solo se qualcuno lo lancia; il test strumentato
 * (`GlassShaderCompileInstrumentedTest`) compila sul dispositivo ma solo in sessione. Questo è la rete che c'è
 * sempre: legge ogni stringa di shader che l'engine passa al compilatore e cerca gli errori che si
 * riconoscono dalla sola lettura. Controlla anche che la sonda di salute ([GlassShaderSources]) conosca ogni
 * shader che sta nei sorgenti, perché una sorgente che nessuno prova è come una sorgente che nessuno usa,
 * finché un driver non la rifiuta.
 *
 * Cosa cerca, e da dove viene ogni regola (fonti lette il 2026-11-10):
 * - **Parole chiave e parole riservate di SkSL** (il linguaggio che AGSL è sotto il nome): l'elenco è quello del
 *   lexer, `src/sksl/lex/sksl.lex` di Skia nei rami AOSP `android13-release`, `android14-release`,
 *   `android15-release` e `android16-release`
 *   (https://android.googlesource.com/platform/external/skia/+/refs/heads/android16-release/src/sksl/lex/sksl.lex
 *   e gli altri tre rami allo stesso percorso). Si tiene l'**unione** dei quattro: un nome riservato da
 *   Android 14 in poi rompe lo shader lì anche se su Android 13 compila. Le parole chiave senza posto in uno
 *   shader AGSL (`flat`, `noperspective`, `discard`, quelle dei programmi di calcolo) si segnalano ovunque;
 *   le altre (`in`, `const`, `layout`…) solo dove fanno da nome.
 * - **Tipi usati come nome** (`float half`): `DSLParser::expectIdentifier` in `src/sksl/SkSLDSLParser.cpp`
 *   (`android13-release`), «expected an identifier, but found type»; i tipi da `src/sksl/SkSLBuiltinTypes.cpp`
 *   degli stessi quattro rami.
 * - **Qualificatori e istruzioni che AGSL non ha**: la AGSL quick reference,
 *   https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference («attribute, varying, and
 *   invariant are not supported», «discard is not allowed»).
 * - **Direttive del preprocessore**: https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl
 *   («AGSL doesn't support GLSL style preprocessor directives»); `DSLParser::directive` le rifiuta tutte in uno
 *   shader di runtime.
 * - **Cicli**: solo `for` che il compilatore srotola, cioè `tipo i = costante; i <op> costante; i++ | i += costante`,
 *   indice mai modificato nel corpo, meno di 100000 giri (quick reference, «For loop limitations»; regole e
 *   messaggi esatti in `src/sksl/analysis/SkSLGetLoopUnrollInfo.cpp`); niente `while` né `do`
 *   (`src/sksl/ir/SkSLForStatement.cpp` e `SkSLDoStatement.cpp`, «while loops are not supported»).
 * - **Indici**: solo una costante o l'indice di un ciclo che contiene l'espressione (quick reference, «Arrays can
 *   only be indexed using a constant or a loop variable»; `ES2IndexingVisitor` in `src/sksl/SkSLAnalysis.cpp`,
 *   «index expression must be constant»).
 * - **Derivate**: `dFdx`, `dFdy`, `fwidth` sono dichiarate `$es3` in `src/sksl/sksl_public.sksl`, e uno shader di
 *   runtime è ES2 («AGSL fixes its GLSL feature set at GLSL ES 1.0», agsl-vs-glsl).
 * - **Lessico**: letterali con suffisso (`1.0f`, l'errore tipico di chi scrive Kotlin), ottali impossibili
 *   (`09`), `$` rimasti da un'interpolazione Kotlin non risolta, caratteri fuori dal lessico, parentesi spaiate,
 *   `main` mancante o con la firma sbagliata.
 *
 * Cosa non vede, e lo vedono il banco e il dispositivo: tipi che non tornano, funzioni che non esistono,
 * overload, precisione, e tutto ciò che un driver rifiuta per conto suo.
 */
class ShaderSourceLintTest {

  @Test
  fun everyEngineShaderPassesTheLint() {
    val problems = mutableListOf<String>()
    for (source in GlassShaderSources.all) {
      AgslLint.check(source.text).forEach { problems += "${source.name}, $it" }
    }
    val inline = EngineKotlinSources.shaders().filter { it.name == null }
    for (shader in inline) {
      AgslLint.check(shader.body).forEach { problems += "${shader.where}, $it" }
    }
    if (problems.isNotEmpty()) {
      fail("Sorgenti AGSL che il compilatore rifiuterebbe:\n" + problems.joinToString("\n"))
    }
    println(
      "Lint AGSL pulito su ${GlassShaderSources.all.size} sorgenti registrate " +
        "(${GlassShaderSources.all.joinToString { it.name }}) e ${inline.size} senza nome " +
        "(${inline.joinToString { it.where }}).",
    )
  }

  @Test
  fun everyShaderStringInTheSourcesIsKnownToTheProbe() {
    val found = EngineKotlinSources.shaders()
    val registered = GlassShaderSources.all.associateBy { it.name }
    val problems = mutableListOf<String>()
    for (shader in found) {
      val name = shader.name
      if (name == null) {
        if (shader.file.name !in InlineShaders) {
          problems += "${shader.where}: uno shader senza nome, che la sonda non può provare. Va in una costante " +
            "`…ShaderString` e in GlassShaderSources."
        }
        continue
      }
      val known = registered[name]
      when {
        known == null -> problems += "${shader.where}: `$name` non è in GlassShaderSources, quindi la sonda non lo " +
          "prova e il test strumentato non lo compila."
        known.text != shader.body -> problems += "${shader.where}: il testo di `$name` letto dal file non è quello " +
          "che l'engine compila (un'interpolazione che il lint non sa risolvere?): il lint guarderebbe un'altra cosa."
      }
    }
    val inFiles = found.mapNotNull { it.name }.toSet()
    for (name in registered.keys - inFiles) {
      problems += "`$name` è in GlassShaderSources ma nessun file lo dichiara: elenco vecchio."
    }
    for (file in InlineShaders.keys) {
      if (found.none { it.name == null && it.file.name == file }) {
        problems += "$file non ha più uno shader senza nome: togliere la sua eccezione da InlineShaders."
      }
    }
    if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
  }

  /**
   * Il lint tiene il passo con il riscaldamento (F1A.7): ogni sorgente che trova nei file ed è registrata è del
   * vetro di oggi, oppure è un programma del materiale nuovo e una famiglia di `GlassWarmUp` lo scalda. Un
   * programma nuovo registrato senza famiglia (la striscia del bordo, in F2.4) fa fallire qui: altrimenti il primo
   * vetro che lo usa pagherebbe la compilazione nel suo fotogramma, ed è proprio ciò che il cancello C6 vieta.
   */
  @Test
  fun everyRegisteredSourceIsOfTodayOrWarmedByExactlyOneFamily() {
    val today = GlassShaderSources.today
    val liquid = GlassShaderSources.liquid
    val problems = mutableListOf<String>()
    if (GlassShaderSources.all != today + liquid) {
      problems += "GlassShaderSources.all non è `today + liquid`: una sorgente nuova va messa in uno dei due elenchi."
    }
    for (source in today) {
      if (source in liquid) problems += "${source.name}: è in tutti e due gli elenchi"
    }
    for (source in GlassShaderSources.all) {
      val families = GlassWarmFamily.entries.filter { source in it.sources }
      when {
        source in today && families.isNotEmpty() ->
          problems += "${source.name}: è del vetro di oggi, e il riscaldamento lo compila per niente (${families.joinToString()})"
        source in liquid && families.isEmpty() ->
          problems += "${source.name}: è del materiale nuovo e nessuna famiglia di GlassWarmUp lo scalda"
        families.size > 1 -> problems += "${source.name}: sta in più famiglie (${families.joinToString()})"
      }
    }
    if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    // Le tredici della 4.0.0, per nome: le fasi dopo ne aggiungono, nessuna ne toglie.
    val names = GlassShaderSources.all.map { it.name }
    assertTrue("Meno di tredici sorgenti: $names", names.size >= 13)
    for (name in listOf(
      "GlassBody1ShaderString", "GlassBody8ShaderString", "GlassLight1ShaderString", "GlassLight8ShaderString",
      "GlassGlowShaderString",
    )) {
      assertTrue("Manca $name: $names", name in names)
    }
    println(
      "Sorgenti registrate: ${names.size}, di cui ${today.size} di oggi e ${liquid.size} del materiale nuovo in " +
        "${GlassWarmFamily.entries.size} famiglie di riscaldamento " +
        GlassWarmFamily.entries.joinToString(prefix = "(", postfix = ")") { f -> "${f.name}: ${f.sources.joinToString { it.name }}" },
    )
  }

  /**
   * I programmi del materiale `Liquid` sono composti da blocchi che stanno in file diversi della stessa
   * cartella: il lint deve leggere il testo intero, non una riga di `main` sotto cinque `$Nome` irrisolti.
   */
  @Test
  fun theLintReadsTemplatesFromOtherFilesOfTheSamePackage() {
    val found = EngineKotlinSources.shaders().associateBy { it.name }
    for (source in listOf(
      GlassShaderSources.Body1, GlassShaderSources.Body8, GlassShaderSources.Light1, GlassShaderSources.Light8,
    )) {
      val read = found[source.name] ?: error("${source.name} non è fra le sorgenti lette dai file")
      assertTrue("${source.name}: resta un `\$` irrisolto", '$' !in read.body)
      // Il nucleo (GlassFieldKernel.kt) e la luce (GlassLightShader.kt) stanno in due file.
      assertTrue(source.name, "float glassField(float2 p, out float2 normal, out float2 center)" in read.body)
      assertTrue(source.name, "void glassLightParts(" in read.body)
      assertTrue("${source.name}: il testo letto non è quello compilato", read.body == source.text)
    }
    // Il corpo include anche l'ombra in viaggio (GlassShadow.kt), con le sue costanti scritte come le scrive Kotlin.
    val body = found.getValue(GlassShaderSources.Body1.name).body
    assertTrue("half4 glassTravelShadow(float2 p, float d)" in body)
    assertTrue("3.0 * contactSigma" in body)
    // E il lint, su quel testo intero, ferma ancora un nome riservato messo in un blocco di un altro file.
    val broken = body.replace("float deep = -d;", "float flat = -d;")
    assertNotEquals("Il blocco della luce non ha più `float deep`: la prova va riscritta.", body, broken)
    assertTrue(AgslLint.check(broken).any { "`flat`" in it })
  }

  /** Il difetto della 3.1.2, rimesso apposta nello shader vero: il lint lo deve fermare. */
  @Test
  fun theLintStopsTheFlatOfThreeOneTwo() {
    val fixed = GlassShaderSources.PolyMorphRefraction.text
    val broken = fixed.replace("flatSample", "flat")
    assertNotEquals("Lo shader non ha più `flatSample`: la prova va riscritta.", fixed, broken)
    val findings = AgslLint.check(broken)
    println("PolyMorphRefraction con `half4 flat = …` (3.1.2):\n  " + findings.joinToString("\n  "))
    assertTrue("Il lint non ferma `flat`: $findings", findings.any { "`flat`" in it })

    val minimal = """
uniform shader content;

half4 main(float2 coord) {
    half4 flat = content.eval(coord);
    return flat;
}"""
    val minimalFindings = AgslLint.check(minimal)
    println("Caso minimo `half4 flat = content.eval(coord)`:\n  " + minimalFindings.joinToString("\n  "))
    assertTrue("Il lint non ferma `flat` nel caso minimo: $minimalFindings", minimalFindings.any { "`flat`" in it })
  }

  @Test
  fun theLintStopsTypesKeywordsAndReservedWordsUsedAsNames() {
    assertCaught(shader("float half = 1.0;\n    return half4(half);"), "`half`")
    assertCaught(shader("float in = 1.0;\n    return half4(in);"), "`in`")
    assertCaught(shader("float noperspective = 1.0;\n    return half4(noperspective);"), "`noperspective`")
    assertCaught(shader("float input = 1.0;\n    return half4(input);"), "`input`")
    assertCaught(shader("float class = 1.0;\n    return half4(class);"), "`class`")
    assertCaught(shader("float sampler = 1.0;\n    return half4(sampler);"), "`sampler`")
    assertCaught(shader("float2 p = gl_FragCoord.xy;\n    return half4(p, 0.0, 1.0);"), "`gl_FragCoord`")
    // Riservata solo da Android 14 in poi: compila su Android 13, si rompe sugli altri.
    assertCaught(shader("float atomic = 1.0;\n    return half4(atomic);"), "`atomic`")
    assertCaught(
      """
uniform shader content;
float spread(float2 float3) { return 1.0; }
half4 main(float2 coord) { return content.eval(coord); }""",
      "`float3`",
    )
  }

  @Test
  fun theLintStopsLoopsTheCompilerCannotUnroll() {
    assertCaught(shader("float s = 0.0;\n    while (s < 1.0) { s += 0.25; }\n    return half4(s);"), "`while`")
    assertCaught(shader("float s = 0.0;\n    do { s += 0.25; } while (s < 1.0);\n    return half4(s);"), "`do`")
    assertCaught(
      shader("float s = 0.0;\n    for (int i = 0; i < int(n); ++i) { s += 0.25; }\n    return half4(s);"),
      "il limite di `i` non è una costante",
    )
    assertCaught(
      shader("float s = 0.0;\n    for (int i = 0; i < 8; i += int(n)) { s += 0.25; }\n    return half4(s);"),
      "il terzo pezzo del `for`",
    )
    assertCaught(
      shader("float s = 0.0;\n    for (int i = 0; i < 8; ++i) { s += 0.25; i = i + 1; }\n    return half4(s);"),
      "`i` cambia dentro il ciclo",
    )
    assertCaught(
      shader("float s = 0.0;\n    for (int i = 0; i < 1000000; ++i) { s += 0.25; }\n    return half4(s);"),
      "100000",
    )
    assertCaught(
      shader("float s = 0.0;\n    for (int i = 8; i < 16; --i) { s += 0.25; }\n    return half4(s);"),
      "100000",
    )
    // Le forme che il compilatore accetta non sono un allarme.
    assertClean(shader("float s = 0.0;\n    for (float x = 0.0; x < 4.0; x += 1.0) { s += x; }\n    return half4(s);"))
    assertClean(shader("float s = 0.0;\n    for (int i = 7; i >= 0; i--) { s += 0.25; }\n    return half4(s);"))
  }

  @Test
  fun theLintStopsIndicesThatAreNotConstant() {
    assertCaught(shader("int j = 3;\n    float2 v = verts[j];\n    return half4(v, 0.0, 1.0);"), "indice non costante")
    assertCaught(shader("float2 v = verts[int(n)];\n    return half4(v, 0.0, 1.0);"), "indice non costante")
    // L'indice di un ciclo vale solo dentro il suo ciclo.
    assertCaught(
      shader(
        "float2 v = float2(0.0);\n    for (int i = 0; i < 8; ++i) { v += verts[i]; }\n" +
          "    for (int k = 0; k < 2; ++k) { v += verts[k]; }\n    int i = 2;\n    v += verts[i];\n" +
          "    return half4(v, 0.0, 1.0);",
      ),
      "indice non costante",
    )
    assertClean(
      shader(
        "const int k = 2;\n    float2 v = verts[k + 1] + verts[0];\n" +
          "    for (int i = 1; i < 8; ++i) { v += verts[i] - verts[i - 1]; }\n    return half4(v, 0.0, 1.0);",
      ),
    )
  }

  @Test
  fun theLintStopsWhatAgslDoesNotHave() {
    assertCaught(shader("float d = fwidth(coord.x);\n    return half4(d);"), "`fwidth`")
    assertCaught(shader("float d = dFdx(coord.x);\n    return half4(d);"), "`dFdx`")
    assertCaught(shader("if (coord.x < 0.0) discard;\n    return half4(1.0);"), "`discard`")
    assertCaught("#define K 2.0\n" + shader("return half4(K);"), "`#define`")
    assertCaught(shader("float a = 1.0f;\n    return half4(a);"), "`1.0f`")
    assertCaught(shader("int a = 09;\n    return half4(float(a));"), "`09`")
    // Un'interpolazione Kotlin che non si è risolta arriva al compilatore come testo.
    assertCaught(shader("float2 v = verts[\$PhysicsMaxPieces];\n    return half4(v, 0.0, 1.0);"), "`\$PhysicsMaxPieces`")
    assertCaught(shader("float a = 1.0;\n    return half4(a) @ 2.0;"), "`@`")
  }

  @Test
  fun theLintStopsABrokenShape() {
    assertCaught(shader("float a = 1.0;\n    if (a > 0.0) {\n    return half4(a);"), "mai chiusa")
    assertCaught("uniform shader content;\nhalf4 paint(float2 coord) { return content.eval(coord); }", "`main`")
    assertCaught("uniform shader content;\nfloat4 main(float x) { return content.eval(float2(x)); }", "`main`")
    assertCaught("uniform shader content;\nfloat main(float2 coord) { return 1.0; }", "`main`")
    assertClean("uniform shader content;\nmediump vec4 main(in vec2 fragCoord) { return content.eval(fragCoord); }")
  }

  private fun shader(body: String) = """
uniform shader content;
uniform float n;
uniform float2 verts[8];

half4 main(float2 coord) {
    $body
}"""

  private fun assertCaught(source: String, expected: String) {
    val findings = AgslLint.check(source)
    assertTrue("Il lint non ferma «$expected» in:\n$source\nTrovato: $findings", findings.any { expected in it })
  }

  private fun assertClean(source: String) {
    val findings = AgslLint.check(source)
    assertTrue("Falso allarme del lint su una forma che il compilatore accetta:\n$source\n$findings", findings.isEmpty())
  }
}

/**
 * Gli shader senza nome che il lint conosce (nome del file → perché la sonda non li prova). Il lint li controlla
 * comunque, ma la sonda, il banco e il test strumentato no: un'eccezione qui va motivata, e non dovrebbe
 * restarci. L'ultima era il bagliore di `GlassInteraction.kt`, che dalla 4.0.0 è `GlassGlowShaderString`.
 */
private val InlineShaders = emptyMap<String, String>()

/**
 * Le regole di AGSL che si riconoscono dalla sola lettura. Ogni riga che restituisce comincia con la riga dello
 * shader e dice perché il compilatore la rifiuterebbe. Fonti nel KDoc di [ShaderSourceLintTest].
 */
private object AgslLint {

  fun check(source: String): List<String> = AgslLintRun(source).findings()

  /** Le parole chiave del lexer di SkSL (unione di Android 13–16): valide al loro posto, mai come nome. */
  val Keywords = setOf(
    "true", "false", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
    "discard", "return", "in", "out", "inout", "uniform", "const", "flat", "noperspective", "inline", "noinline",
    "readonly", "writeonly", "buffer", "struct", "layout", "highp", "mediump", "lowp", "workgroup", "pixel_local",
    "sk_has_side_effects",
  )

  private const val Interpolation =
    "qualificatore d'interpolazione di SkSL: in AGSL non ha posto, e come nome fa rifiutare lo shader " +
      "(il Tab S9 sulla 3.1.2: «expected ';', but found 'flat'»)"
  private const val ComputeOnly =
    "parola chiave di SkSL per i programmi di calcolo: in uno shader AGSL non ha posto, e da Android 14 non può " +
      "fare da nome"
  private const val Unrolled =
    "AGSL compila solo cicli `for` a limiti costanti, che si possono srotolare («while loops are not supported»)"
  private const val Derivative =
    "derivata: SkSL la dichiara solo per ES3 (`\$es3` in sksl_public.sksl) e uno shader AGSL è ES2"

  /** Parole che in uno shader AGSL non hanno nessun posto valido: ovunque compaiano, lo shader è rifiutato. */
  val Never: Map<String, String> = mapOf(
    "flat" to Interpolation,
    "noperspective" to Interpolation,
    "discard" to "AGSL non ammette `discard` (AGSL quick reference)",
    "while" to Unrolled,
    "do" to Unrolled,
    "readonly" to ComputeOnly,
    "writeonly" to ComputeOnly,
    "buffer" to ComputeOnly,
    "workgroup" to ComputeOnly,
    "pixel_local" to "parola chiave di SkSL (Android 16): in uno shader AGSL non ha posto né come nome",
    "sk_has_side_effects" to "parola chiave di SkSL (Android 13): non può fare da nome",
    "dFdx" to Derivative,
    "dFdy" to Derivative,
    "fwidth" to Derivative,
  )

  /**
   * `RESERVED` di `sksl.lex`, ramo `android16-release`: contiene alla lettera quelli di Android 13, 14 e 15
   * (`sampler[12]DShadow`, `sampler3DRect`, `sampler2DRectShadow`, `samplerCube`, `sampler[13]D`, `atomic`).
   */
  val Reserved = Regex(
    "atomic|attribute|varying|precision|invariant|asm|class|union|enum|typedef|template|this|packed|goto|" +
      "volatile|public|static|extern|external|interface|long|double|fixed|unsigned|superp|input|output|" +
      "hvec[234]|dvec[234]|dmat[234]|dmat[234]x[234]|fvec[234]|" +
      "(iimage|image|isampler|itexture|uimage|usampler|utexture)" +
      "(1D|2D|3D|1DArray|2DArray|2DRect|2DMS|2DMSArray|Buffer|Cube|CubeArray)|" +
      "(sampler|texture)(1D|1DArray|2DArray|2DMS|2DMSArray|3D|Buffer|Cube|CubeArray)|" +
      "sampler(1DArrayShadow|1DShadow|2DArrayShadow|2DShadow|2DRectShadow|3DRect|CubeArrayShadow|CubeShadow|Shadow)|" +
      "[iu]subpassInput(MS)?|sizeof|cast|namespace|using|gl_[0-9a-zA-Z_]*",
  )

  /** I tipi di SkSL (`SkSLBuiltinTypes.cpp`, unione di Android 13–16), più gli alias GLSL che AGSL accetta. */
  val Types: Set<String> = buildSet {
    for (base in listOf("bool", "int", "uint", "short", "ushort", "float", "half")) {
      add(base)
      for (n in 2..4) add("$base$n")
    }
    for (base in listOf("float", "half")) for (c in 2..4) for (r in 2..4) add("${base}${c}x$r")
    for (n in 2..4) {
      add("vec$n")
      add("ivec$n")
      add("uvec$n")
      add("bvec$n")
      add("mat$n")
      for (r in 2..4) add("mat${n}x$r")
    }
    addAll(
      listOf(
        "void", "shader", "colorFilter", "blender", "sampler", "sampler1D", "sampler2D", "sampler3D",
        "sampler2DRect", "samplerExternalOES", "isampler2D", "texture1D", "texture2D", "texture3D", "texture2DRect",
        "textureExternalOES", "itexture2D", "readonlyTexture2D", "writeonlyTexture2D", "readWriteTexture2D",
        "subpassInput", "subpassInputMS", "atomicUint",
      ),
    )
  }

  /** I tipi che possono fare da indice di un ciclo («invalid type for loop index»: deve essere un numero). */
  val NumberTypes = setOf("int", "uint", "short", "ushort", "float", "half")
  val Precision = setOf("highp", "mediump", "lowp")
  val RelOps = setOf("<", "<=", ">", ">=", "==", "!=")
  val IncDec = setOf("++", "--")
  val AssignOps = setOf("=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=")

  /** Ciò che può stare in un'espressione costante oltre a numeri, costanti, indici e costruttori. */
  val ConstantPunct = setOf(
    "+", "-", "*", "/", "%", "(", ")", "?", ":", "<", ">", "<=", ">=", "==", "!=", "&&", "||", "^^", "!", ".", ",",
  )

  /** Dopo `tipo nome` uno di questi dice che `nome` è il nome di una dichiarazione, di una funzione o di un parametro. */
  val DeclarationEnd = setOf("=", ";", ",", "[", "(", ")")

  val Operators3 = listOf("<<=", ">>=")
  val Operators2 = listOf(
    "++", "--", "<<", ">>", "||", "^^", "&&", "==", "!=", ">=", "<=", "+=", "-=", "*=", "/=", "%=", "|=", "^=", "&=",
  )
  const val Punct1 = "(){}[].,+-*/%|^&~!?:=<>;"

  /** «Loops that run for 100000+ iterations will exceed our program size limit» (`SkSLGetLoopUnrollInfo.cpp`). */
  const val LoopLimit = 100000

  // I letterali di `sksl.lex`, alla lettera.
  val FloatLiteral: Pattern =
    Pattern.compile("[0-9]*\\.[0-9]+([eE][+-]?[0-9]+)?|[0-9]+\\.[0-9]*([eE][+-]?[0-9]+)?|[0-9]+([eE][+-]?[0-9]+)")
  val IntLiteral: Pattern = Pattern.compile("([1-9][0-9]*|0[0-7]*|0[xX][0-9a-fA-F]+)[uU]?")
  val BadOctal: Pattern = Pattern.compile("(0[0-9]+)[uU]?")
}

/** Una passata del lint su una sorgente: lessico, poi le regole sui token. */
private class AgslLintRun(private val source: String) {

  private enum class Kind { Word, Number, Punct, Other }

  private class Tok(val kind: Kind, val text: String, val line: Int)

  /** Un ciclo `for` ben formato: il nome del suo indice e i token del suo corpo (estremi compresi). */
  private class Loop(val index: String, val from: Int, val to: Int)

  private val found = LinkedHashSet<String>()
  private val toks: List<Tok> = tokenize()
  private val pairs: Map<Int, Int> = pairs()
  private val types: Set<String> = AgslLint.Types + structNames()
  private val constants: Set<String> = constantNames()

  fun findings(): List<String> {
    words()
    names()
    val loops = loops()
    indices(loops)
    entry()
    return found.toList()
  }

  private fun report(line: Int, what: String) {
    found += "riga $line: $what"
  }

  private fun report(t: Tok, what: String) = report(t.line, what)

  private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == '_'

  private fun isAsciiWordChar(c: Char) = isAsciiLetter(c) || c in '0'..'9'

  private fun tokenize(): List<Tok> {
    val out = ArrayList<Tok>()
    val text = source
    val n = text.length
    var i = 0
    var line = 1
    while (i < n) {
      val c = text[i]
      when {
        c == '\n' -> {
          line++
          i++
        }
        c.isWhitespace() -> i++
        text.startsWith("//", i) -> while (i < n && text[i] != '\n') i++
        text.startsWith("/*", i) -> {
          val end = text.indexOf("*/", i + 2)
          val stop = if (end < 0) n else end + 2
          if (end < 0) report(line, "commento `/*` mai chiuso")
          line += text.substring(i, stop).count { it == '\n' }
          i = stop
        }
        isAsciiLetter(c) -> {
          val start = i
          while (i < n && isAsciiWordChar(text[i])) i++
          out += Tok(Kind.Word, text.substring(start, i), line)
        }
        c == '$' || c == '#' -> {
          val start = i
          i++
          while (i < n && isAsciiWordChar(text[i])) i++
          val word = text.substring(start, i)
          if (c == '$') {
            report(line, "`$word`: un `\$` in AGSL è un nome privato di SkSL o un'interpolazione Kotlin non risolta")
          } else {
            report(line, "`$word`: direttiva del preprocessore, e AGSL non ha preprocessore (si usa `const`)")
          }
          out += Tok(Kind.Other, word, line)
        }
        c in '0'..'9' || (c == '.' && i + 1 < n && text[i + 1] in '0'..'9') -> {
          val start = i
          val asFloat = lookingAt(AgslLint.FloatLiteral, i)
          val asInt = lookingAt(AgslLint.IntLiteral, i)
          val asBadOctal = lookingAt(AgslLint.BadOctal, i)
          val end = maxOf(asFloat, asInt, asBadOctal, i + 1)
          i = end
          val badOctal = asBadOctal == end && asFloat < end && asInt < end
          var suffixed = false
          while (i < n && isAsciiWordChar(text[i])) {
            i++
            suffixed = true
          }
          val literal = text.substring(start, i)
          when {
            suffixed -> report(line, "`$literal`: letterale numerico con un suffisso, e AGSL non ne ha (salvo `u` degli interi)")
            badOctal -> report(line, "`$literal`: ottale con una cifra 8 o 9")
          }
          out += Tok(Kind.Number, literal, line)
        }
        else -> {
          val op = AgslLint.Operators3.firstOrNull { text.startsWith(it, i) }
            ?: AgslLint.Operators2.firstOrNull { text.startsWith(it, i) }
            ?: c.toString().takeIf { c in AgslLint.Punct1 }
          if (op != null) {
            out += Tok(Kind.Punct, op, line)
            i += op.length
          } else {
            report(line, "`$c`: carattere fuori dal lessico di AGSL")
            out += Tok(Kind.Other, c.toString(), line)
            i++
          }
        }
      }
    }
    return out
  }

  /** La fine del letterale di [pattern] che comincia a [at], o [at] se non ce n'è uno. */
  private fun lookingAt(pattern: Pattern, at: Int): Int {
    val m = pattern.matcher(source).region(at, source.length)
    return if (m.lookingAt()) m.end() else at
  }

  /** Apertura → chiusura per `(`, `[`, `{`; segnala quelle spaiate. */
  private fun pairs(): Map<Int, Int> {
    val open = ArrayDeque<Int>()
    val out = HashMap<Int, Int>()
    for ((k, t) in toks.withIndex()) {
      if (t.kind != Kind.Punct) continue
      when (t.text) {
        "(", "[", "{" -> open.addLast(k)
        ")", "]", "}" -> {
          val want = when (t.text) {
            ")" -> "("
            "]" -> "["
            else -> "{"
          }
          val top = open.lastOrNull()
          if (top == null || toks[top].text != want) {
            report(t, "`${t.text}` senza la sua apertura")
          } else {
            open.removeLast()
            out[top] = k
          }
        }
      }
    }
    for (k in open) report(toks[k], "`${toks[k].text}` aperta e mai chiusa")
    return out
  }

  private fun structNames(): Set<String> = toks.indices
    .filter { toks[it].text == "struct" && toks.getOrNull(it + 1)?.kind == Kind.Word }
    .mapTo(HashSet()) { toks[it + 1].text }

  /** I nomi dichiarati `const`: costanti per le regole dei cicli e degli indici. */
  private fun constantNames(): Set<String> {
    val out = HashSet<String>()
    for ((k, t) in toks.withIndex()) {
      if (t.text != "const") continue
      var j = k + 1
      while (j < toks.size && toks[j].text in AgslLint.Precision) j++
      if (toks.getOrNull(j)?.text !in AgslLint.Types) continue
      j++
      var depth = 0
      var expectName = true
      while (j < toks.size) {
        val x = toks[j]
        if (x.text == "(" || x.text == "[" || x.text == "{") depth++
        if (x.text == ")" || x.text == "]" || x.text == "}") depth--
        if (depth < 0 || (depth == 0 && x.text == ";")) break
        if (expectName && x.kind == Kind.Word) out += x.text
        expectName = depth == 0 && x.text == ","
        j++
      }
    }
    return out
  }

  /** Le parole che non hanno posto in AGSL, e le parole riservate: ovunque compaiano. */
  private fun words() {
    for (t in toks) {
      if (t.kind != Kind.Word) continue
      val never = AgslLint.Never[t.text]
      when {
        never != null -> report(t, "`${t.text}`: $never")
        AgslLint.Reserved.matches(t.text) -> report(t, "`${t.text}` è una parola riservata di SkSL: in AGSL non può comparire, nemmeno come nome")
      }
    }
  }

  /** Un tipo o una parola chiave dove ci vuole un nome. */
  private fun names() {
    for ((k, t) in toks.withIndex()) {
      if (t.kind != Kind.Word) continue
      val next = toks.getOrNull(k + 1) ?: continue
      // `tipo nome …`: il nome di una dichiarazione, di una funzione o di un parametro.
      if (t.text in types && next.kind == Kind.Word && toks.getOrNull(k + 2)?.text in AgslLint.DeclarationEnd) {
        nameCheck(next)
      }
      // `nome = …`: un tipo o una parola chiave usati come variabile.
      if (next.text in AgslLint.AssignOps && toks.getOrNull(k - 1)?.text != ".") nameCheck(t)
    }
  }

  private fun nameCheck(name: Tok) {
    when {
      name.text in types -> report(name, "`${name.text}` è un tipo di AGSL e non può fare da nome")
      name.text in AgslLint.Keywords && name.text !in AgslLint.Never ->
        report(name, "`${name.text}` è una parola chiave di SkSL e non può fare da nome")
    }
  }

  private fun isConstant(expr: List<Tok>, loopIndices: Set<String> = emptySet()): Boolean =
    expr.isNotEmpty() && expr.withIndex().all { (j, t) ->
      when (t.kind) {
        Kind.Number -> true
        Kind.Word ->
          t.text in constants || t.text in loopIndices || t.text in types || t.text == "true" || t.text == "false" ||
            (j > 0 && expr[j - 1].text == ".")
        Kind.Punct -> t.text in AgslLint.ConstantPunct
        Kind.Other -> false
      }
    }

  private fun numberValue(expr: List<Tok>): Double? {
    val (sign, t) = when {
      expr.size == 1 -> 1.0 to expr[0]
      expr.size == 2 && expr[0].text == "-" -> -1.0 to expr[1]
      else -> return null
    }
    if (t.kind != Kind.Number) return null
    val s = t.text.trimEnd('u', 'U')
    val value = when {
      s.startsWith("0x") || s.startsWith("0X") -> s.substring(2).toLongOrNull(16)?.toDouble()
      s.length > 1 && s[0] == '0' && s.all { it in '0'..'9' } -> s.toLongOrNull(8)?.toDouble()
      else -> s.toDoubleOrNull()
    }
    return value?.times(sign)
  }

  private fun text(expr: List<Tok>) = expr.joinToString(" ") { it.text }

  /** I pezzi fra [from] e [to] (escluso) separati da `;` a profondità zero. */
  private fun split(from: Int, to: Int): List<List<Tok>> {
    val parts = mutableListOf<List<Tok>>()
    var current = mutableListOf<Tok>()
    var depth = 0
    for (k in from until to) {
      val t = toks[k]
      if (t.kind == Kind.Punct && t.text in setOf("(", "[", "{")) depth++
      if (t.kind == Kind.Punct && t.text in setOf(")", "]", "}")) depth--
      if (depth == 0 && t.text == ";") {
        parts += current
        current = mutableListOf()
      } else {
        current += t
      }
    }
    parts += current
    return parts
  }

  /** L'ultimo token dell'istruzione che comincia a [start]: un blocco, o fino al `;` (con gli `else`). */
  private fun statementEnd(start: Int): Int {
    val last = toks.size - 1
    if (start > last) return last
    var k = start
    while (k <= last) {
      val t = toks[k]
      if (t.kind == Kind.Punct && t.text in setOf("(", "[", "{")) {
        val close = pairs[k] ?: return last
        if (t.text == "{" && toks.getOrNull(close + 1)?.text != "else") return close
        k = close + 1
        continue
      }
      if (t.text == ";" && toks.getOrNull(k + 1)?.text != "else") return k
      k++
    }
    return last
  }

  private fun loops(): List<Loop> {
    val loops = mutableListOf<Loop>()
    for ((k, t) in toks.withIndex()) {
      if (t.kind != Kind.Word || t.text != "for") continue
      val open = k + 1
      val close = if (toks.getOrNull(open)?.text == "(") pairs[open] else null
      if (close == null) {
        report(t, "`for` senza la sua parentesi")
        continue
      }
      val parts = split(open + 1, close)
      if (parts.size != 3) {
        report(t, "`for` con ${parts.size} pezzi invece di tre")
        continue
      }
      val init = parts[0].dropWhile { it.text in AgslLint.Precision }
      if (init.size < 4 || init[0].text !in types || init[1].kind != Kind.Word || init[2].text != "=") {
        report(t, "il primo pezzo del `for` dev'essere `tipo indice = costante`, non `${text(parts[0])}`")
        continue
      }
      val index = init[1].text
      if (init[0].text !in AgslLint.NumberTypes) {
        report(init[0], "`${init[0].text}` non può fare da tipo dell'indice di un ciclo: serve un numero")
      }
      val startExpr = init.drop(3)
      if (startExpr.any { it.text == "," }) {
        report(init[1], "il `for` dichiara più di un indice: AGSL ne srotola uno solo")
      } else if (!isConstant(startExpr)) {
        report(init[1], "il valore iniziale di `$index` non è una costante (`${text(startExpr)}`): AGSL srotola solo cicli a limiti costanti")
      }

      val condition = parts[1]
      var endExpr: List<Tok>? = null
      if (condition.size < 3 || condition[0].text != index || condition[1].text !in AgslLint.RelOps) {
        report(t, "la condizione del `for` dev'essere `$index <confronto> costante`, non `${text(condition)}`")
      } else if (!isConstant(condition.drop(2))) {
        report(condition[0], "il limite di `$index` non è una costante (`${text(condition.drop(2))}`): AGSL srotola solo cicli a limiti costanti")
      } else {
        endExpr = condition.drop(2)
      }

      val step = parts[2]
      val delta: Double? = when {
        step.size == 2 && step[0].text == index && step[1].text in AgslLint.IncDec ->
          if (step[1].text == "++") 1.0 else -1.0
        step.size == 2 && step[1].text == index && step[0].text in AgslLint.IncDec ->
          if (step[0].text == "++") 1.0 else -1.0
        step.size >= 3 && step[0].text == index && (step[1].text == "+=" || step[1].text == "-=") &&
          isConstant(step.drop(2)) ->
          numberValue(step.drop(2))?.let { if (step[1].text == "-=") -it else it } ?: Double.NaN
        else -> {
          report(t, "il terzo pezzo del `for` dev'essere `$index++`, `$index--` o `$index += costante`, non `${text(step)}`")
          null
        }
      }

      val from = close + 1
      val to = statementEnd(from)
      loops += Loop(index, from, to)
      for (b in from..to) {
        val x = toks.getOrNull(b) ?: break
        if (x.kind != Kind.Word || x.text != index || toks.getOrNull(b - 1)?.text == ".") continue
        if (toks.getOrNull(b + 1)?.text in AgslLint.AssignOps || toks.getOrNull(b + 1)?.text in AgslLint.IncDec ||
          toks.getOrNull(b - 1)?.text in AgslLint.IncDec
        ) {
          report(x, "`$index` cambia dentro il ciclo: l'indice di un `for` AGSL lo muove solo la sua intestazione")
        }
      }

      val start = numberValue(startExpr)
      val end = endExpr?.let { numberValue(it) }
      if (start != null && end != null && delta != null && !delta.isNaN()) {
        val count = iterations(start, end, delta, condition[1].text)
        if (count >= AgslLint.LoopLimit) {
          report(t, "il ciclo su `$index` non finisce entro ${AgslLint.LoopLimit} giri: il compilatore non lo srotola")
        }
      }
    }
    return loops
  }

  /** `calculate_count` e il caso `!=`/`==` di `SkSLGetLoopUnrollInfo.cpp`, alla lettera. */
  private fun iterations(start: Double, end: Double, delta: Double, op: String): Int {
    fun count(forwards: Boolean, inclusive: Boolean): Int {
      if (forwards != (start < end)) return 0
      if (delta == 0.0 || forwards != (delta > 0.0)) return AgslLint.LoopLimit
      val exact = (end - start) / delta
      var c = ceil(exact)
      if (inclusive && c == exact) c += 1.0
      return if (c > AgslLint.LoopLimit || !c.isFinite()) AgslLint.LoopLimit else c.toInt()
    }
    return when (op) {
      "<" -> count(forwards = true, inclusive = false)
      ">" -> count(forwards = false, inclusive = false)
      "<=" -> count(forwards = true, inclusive = true)
      ">=" -> count(forwards = false, inclusive = true)
      "!=" -> {
        val exact = (end - start) / delta
        val c = ceil(exact)
        if (c < 0 || c != exact || !exact.isFinite()) AgslLint.LoopLimit else c.toInt()
      }
      else -> if (start == end) (if (delta != 0.0) 1 else AgslLint.LoopLimit) else 0
    }
  }

  /** Ogni `[…]`: la taglia di un array dichiarato, o un indice. */
  private fun indices(loops: List<Loop>) {
    for ((k, t) in toks.withIndex()) {
      if (t.kind != Kind.Punct || t.text != "[") continue
      val close = pairs[k] ?: continue
      val inner = toks.subList(k + 1, close)
      val prev = toks.getOrNull(k - 1)
      val declaration = prev != null &&
        (prev.text in types || (prev.kind == Kind.Word && toks.getOrNull(k - 2)?.text in types))
      if (declaration) {
        if (!isConstant(inner)) report(t, "la taglia dell'array `${prev?.text}` non è una costante")
        continue
      }
      val inScope = loops.filter { k in it.from..it.to }.mapTo(HashSet()) { it.index }
      if (!isConstant(inner, inScope)) {
        report(
          t,
          "indice non costante in `${prev?.text}[${text(inner)}]`: AGSL indicizza solo con una costante o con " +
            "l'indice di un ciclo che contiene l'espressione",
        )
      }
    }
  }

  /** Uno shader AGSL comincia da `half4 main(float2 coord)` (o `float4`/`vec4` e `vec2`). */
  private fun entry() {
    val mains = toks.indices.filter { k ->
      toks[k].text == "main" && toks.getOrNull(k + 1)?.text == "(" && toks.getOrNull(k - 1)?.kind == Kind.Word
    }
    if (mains.isEmpty()) {
      report(1, "nessuna `main`: uno shader AGSL comincia da `half4 main(float2 coord)`")
      return
    }
    if (mains.size > 1) report(toks[mains[1]], "`main` definita ${mains.size} volte")
    for (k in mains) {
      val ret = toks[k - 1].text
      if (ret !in setOf("half4", "float4", "vec4")) {
        report(toks[k], "`main` deve restituire `half4` (o `float4`, `vec4`), non `$ret`")
      }
      val close = pairs[k + 1] ?: continue
      val params = toks.subList(k + 2, close).filter { it.text != "in" && it.text !in AgslLint.Precision }
      if (params.size != 2 || params[0].text !in setOf("float2", "vec2") || params[1].kind != Kind.Word) {
        report(toks[k], "`main` prende una sola coordinata `float2`, non `${text(toks.subList(k + 2, close))}`")
      }
    }
  }
}

/** Uno shader trovato in un file Kotlin dell'engine, con le interpolazioni risolte come le risolve il compilatore. */
private class KotlinShader(val file: File, val line: Int, val name: String?, val body: String) {
  val where: String get() = "${file.name}:$line"
}

/**
 * Le stringhe di shader dei sorgenti di `engine-ui`, lette dai file come fa `tools/glassbench/kotlin_sources.py`:
 * una stringa grezza con un `main` AGSL, con `$Nome` e `${Nome}` risolti dalle stringhe e dalle costanti
 * numeriche dello stesso file e, se lì non ci sono, **degli altri file della stessa cartella**, cioè dello
 * stesso pacchetto. Serve al materiale `Liquid`: `GlassBody1ShaderString` è una riga di `main` sotto cinque
 * blocchi, e tre stanno in altri file (`$GlassFieldKernel1`, `$GlassLightBlock`, `$GlassTravelShadowBlock`).
 * Finché il lint risolveva solo dentro il file, il testo che leggeva non era quello compilato.
 *
 * Non è un parser di Kotlin; se una forma nuova lo inganna, il confronto con il testo compilato (in
 * [ShaderSourceLintTest.everyShaderStringInTheSourcesIsKnownToTheProbe]) lo dice.
 */
private object EngineKotlinSources {

  private val Name = Regex("""(?:const\s+)?val\s+(\w+)\s*(?::\s*[\w.]+)?\s*=\s*$""")
  private val NumberConstant = Regex("""const\s+val\s+(\w+)\s*(?::\s*\w+)?\s*=\s*(-?\d+(?:\.\d+)?)([fFL]?)\b""")
  private val Interpolation = Regex("""\$\{(\w+)\}|\$([A-Za-z_]\w*)""")
  private val Entry = Regex("""\b(?:half4|float4|vec4)\s+main\s*\(""")

  /** Ciò che un file dichiara: le stringhe grezze, quelle con un nome, e le costanti numeriche. */
  private class Scan(
    val file: File,
    val text: String,
    val raws: List<KotlinRawStrings.Raw>,
    val named: Map<String, String>,
    val nameAt: Map<Int, String>,
    val numbers: Map<String, String>,
  )

  private val cached: List<KotlinShader> by lazy {
    val scans = root().walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .sortedBy { it.path }
      .map { scan(it) }
      .toList()
    val byFolder = scans.groupBy { it.file.parentFile }
    scans.flatMap { shadersIn(it, byFolder.getValue(it.file.parentFile)) }
  }

  fun shaders(): List<KotlinShader> = cached

  /** I sorgenti di `engine-ui`: Gradle lancia i test dalla cartella del modulo, un IDE a volte dalla radice. */
  private fun root(): File {
    val candidates = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin"))
    return candidates.firstOrNull { File(it, "dev/antigravity/fluidengine/ui").isDirectory }
      ?: throw AssertionError(
        "Non trovo i sorgenti di engine-ui da ${File(".").absolutePath}: il lint li deve leggere, non saltarli.",
      )
  }

  private fun scan(file: File): Scan {
    val text = file.readText().replace("\r\n", "\n")
    val raws = KotlinRawStrings.find(text)
    val named = HashMap<String, String>()
    val nameAt = HashMap<Int, String>()
    for (raw in raws) {
      val before = text.substring(maxOf(0, raw.open - 300), raw.open)
      Name.find(before)?.let {
        named[it.groupValues[1]] = raw.body
        nameAt[raw.open] = it.groupValues[1]
      }
    }
    val numbers = NumberConstant.findAll(text).associate {
      it.groupValues[1] to kotlinNumber(it.groupValues[2], it.groupValues[3])
    }
    return Scan(file, text, raws, named, nameAt, numbers)
  }

  private fun shadersIn(own: Scan, folder: List<Scan>): List<KotlinShader> {
    // Prima il file stesso, poi gli altri della cartella nell'ordine dei percorsi: un nome è uno nel pacchetto
    // (Kotlin non ne ammette due), quindi l'ordine conta solo se il lessico si è lasciato ingannare.
    val scopes = listOf(own) + folder.filter { it !== own }

    fun resolve(body: String, trail: Set<String>): String = Interpolation.replace(body) { m ->
      val ref = m.groupValues[1].ifEmpty { m.groupValues[2] }
      val string = scopes.firstNotNullOfOrNull { it.named[ref] }
      val number = scopes.firstNotNullOfOrNull { it.numbers[ref] }
      when {
        ref in trail -> m.value
        string != null -> resolve(string, trail + ref)
        number != null -> number
        else -> m.value // resta com'è: il lint segnala il `$`
      }
    }

    return own.raws.mapNotNull { raw ->
      val name = own.nameAt[raw.open]
      val body = resolve(raw.body, setOfNotNull(name))
      if (!Entry.containsMatchIn(body)) return@mapNotNull null
      KotlinShader(own.file, own.text.substring(0, raw.open).count { it == '\n' } + 1, name, body)
    }
  }

  /** Come Kotlin scrive una costante numerica dentro una stringa: `6`, `1.5` per `1.5f`. */
  private fun kotlinNumber(digits: String, suffix: String): String = when {
    suffix == "f" || suffix == "F" -> digits.toFloat().toString()
    '.' in digits -> digits.toDouble().toString()
    else -> digits
  }
}

/** Quanto basta del lessico di Kotlin per trovare le stringhe grezze: commenti annidati, stringhe, caratteri, `${…}`. */
private object KotlinRawStrings {

  class Raw(val open: Int, val body: String)

  private const val Triple = "\"\"\""

  fun find(text: String): List<Raw> {
    val out = mutableListOf<Raw>()
    code(text, 0, untilBrace = false, out)
    return out
  }

  private fun code(text: String, start: Int, untilBrace: Boolean, out: MutableList<Raw>): Int {
    var i = start
    var depth = 0
    while (i < text.length) {
      when {
        text.startsWith("//", i) -> i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
        text.startsWith("/*", i) -> i = blockComment(text, i)
        text.startsWith(Triple, i) -> i = raw(text, i, out)
        text[i] == '"' -> i = string(text, i, out)
        text[i] == '\'' -> i = char(text, i)
        text[i] == '{' -> {
          depth++
          i++
        }
        text[i] == '}' -> {
          if (untilBrace && depth == 0) return i + 1
          depth--
          i++
        }
        else -> i++
      }
    }
    return i
  }

  private fun blockComment(text: String, start: Int): Int {
    var i = start + 2
    var depth = 1
    while (i < text.length && depth > 0) {
      when {
        text.startsWith("/*", i) -> {
          depth++
          i += 2
        }
        text.startsWith("*/", i) -> {
          depth--
          i += 2
        }
        else -> i++
      }
    }
    return i
  }

  private fun raw(text: String, open: Int, out: MutableList<Raw>): Int {
    var i = open + 3
    while (i < text.length) {
      if (text.startsWith("\${", i)) {
        i = code(text, i + 2, untilBrace = true, out)
        continue
      }
      if (text.startsWith(Triple, i)) {
        // `""""` chiude con l'ultima terna: le virgolette in più sono contenuto.
        var end = i
        while (end + 3 < text.length && text[end + 3] == '"') end++
        out += Raw(open, text.substring(open + 3, end))
        return end + 3
      }
      i++
    }
    throw AssertionError("Stringa grezza aperta al carattere $open e mai chiusa: il lessico del lint non regge questo file.")
  }

  private fun string(text: String, open: Int, out: MutableList<Raw>): Int {
    var i = open + 1
    while (i < text.length) {
      when {
        text[i] == '\\' -> i += 2
        text.startsWith("\${", i) -> i = code(text, i + 2, untilBrace = true, out)
        text[i] == '"' -> return i + 1
        text[i] == '\n' -> return i
        else -> i++
      }
    }
    return i
  }

  private fun char(text: String, open: Int): Int {
    var i = open + 1
    if (i < text.length && text[i] == '\\') i += 2 else i++
    val limit = minOf(text.length, open + 10)
    while (i < limit && text[i] != '\'') i++
    return i + 1
  }
}
