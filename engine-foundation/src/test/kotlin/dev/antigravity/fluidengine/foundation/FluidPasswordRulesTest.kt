package dev.antigravity.fluidengine.foundation

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.3 · le regole della password: la riga di Apple, le spunte, la forza stimata e la password forte proposta. */
class FluidPasswordRulesTest {

  @Test fun `le regole di serie si spuntano una per una mentre si scrive`() {
    val rules = FluidPasswordRules.Default
    fun met(password: String) = rules.check(password).map { it.satisfied }
    assertEquals(listOf(false, false, false, false), met(""))
    assertEquals(listOf(false, true, false, false), met("ciao"))
    assertEquals(listOf(true, true, false, false), met("ciaociao"))
    assertEquals(listOf(true, true, true, false), met("ciaoCiao"))
    assertEquals(listOf(true, true, true, true), met("ciaoCiao7"))
    assertTrue(rules.isSatisfiedBy("ciaoCiao7"))
  }

  @Test fun `la riga delle Password Rules di Apple`() {
    val rules = FluidPasswordRules.parse(
      "minlength: 10; maxlength: 64; required: lower; required: upper; required: digit; " +
        "required: [-().&@?'#,/&quot;+]; max-consecutive: 2",
    )
    assertEquals(10, rules.minLength)
    assertEquals(64, rules.maxLength)
    assertEquals(4, rules.required.size)
    assertEquals(2, rules.maxConsecutive)
    assertTrue('"' in rules.required[3].single())
    assertTrue(',' in rules.required[3].single())
    assertFalse(rules.isSatisfiedBy("Ciaociao77"))
    assertTrue(rules.isSatisfiedBy("Ciaociao7("))
    assertFalse(rules.isSatisfiedBy("Ciaoooo7(x"))
    val requirements = rules.requirements
    assertTrue(requirements.first() is FluidPasswordRequirement.MinLength)
    assertTrue(requirements.any { it is FluidPasswordRequirement.MaxConsecutive })
  }

  @Test fun `più classi in un required valgono una o l'altra, e le proprietà ignote si ignorano`() {
    val rules = FluidPasswordRules.parse("required: upper, digit; colour: blue; minlength: abc; allowed: lower")
    assertEquals(8, rules.minLength)
    assertEquals(listOf(FluidPasswordCharset.Upper, FluidPasswordCharset.Digit), rules.required.single())
    assertTrue(rules.isSatisfiedBy("abcdefg1"))
    assertTrue(rules.isSatisfiedBy("abcdefgH"))
    assertFalse(rules.isSatisfiedBy("abcdefgh"))
    // allowed: lower più i required: un simbolo non è ammesso.
    assertFalse(rules.isSatisfiedBy("abcdefg1!"))
  }

  @Test fun `la password forte è quella di Apple quando le regole lo ammettono`() {
    val random = Random(7)
    repeat(50) {
      val password = FluidPasswordRules.Default.generate(random)
      assertEquals(20, password.length)
      assertEquals(2, password.count { it == '-' })
      assertEquals(1, password.count { it.isDigit() })
      assertEquals(1, password.count { it.isUpperCase() })
      assertEquals(16, password.count { it.isLowerCase() })
      assertTrue(Regex("[A-Za-z0-9]{6}-[A-Za-z0-9]{6}-[A-Za-z0-9]{6}").matches(password))
      assertTrue(FluidPasswordRules.Default.isSatisfiedBy(password))
    }
  }

  @Test fun `con regole che non ammettono il formato di Apple la password le rispetta comunque`() {
    val random = Random(11)
    val noHyphen = FluidPasswordRules.parse("minlength: 12; maxlength: 16; required: lower; required: digit; " +
      "required: [!#]; allowed: upper")
    repeat(50) {
      val password = noHyphen.generate(random)
      assertTrue(password, noHyphen.isSatisfiedBy(password))
      assertFalse(password, '-' in password)
      assertTrue(password.length in 12..16)
    }
    val pin = FluidPasswordRules(minLength = 6, maxLength = 6, required = listOf(listOf(FluidPasswordCharset.Digit)),
      allowed = listOf(FluidPasswordCharset.Digit))
    repeat(20) { assertTrue(Regex("[0-9]{6}").matches(pin.generate(random))) }
  }

  @Test fun `la forza è una stima semplice e dichiarata`() {
    assertEquals(0, FluidPasswordStrength.estimate("").level)
    assertEquals(1, FluidPasswordStrength.estimate("ciao").level)
    assertEquals(1, FluidPasswordStrength.estimate("123456").level)
    // Comune anche con una cifra e un simbolo in coda.
    assertEquals(1, FluidPasswordStrength.estimate("Password1!").level)
    // Ripetizioni e sequenze contano poco.
    assertTrue(FluidPasswordStrength.estimate("aaaaaaaaaa").bits < FluidPasswordStrength.estimate("qmzkxvbtwr").bits)
    assertTrue(FluidPasswordStrength.estimate("abcdefgh").bits < FluidPasswordStrength.estimate("qmzkxvbt").bits)
    assertEquals(3, FluidPasswordStrength.estimate("Tramonto-Blu7").level)
    assertEquals(4, FluidPasswordStrength.estimate(FluidPasswordRules.Default.generate(Random(3))).level)
  }
}
