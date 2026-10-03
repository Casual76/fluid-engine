package dev.antigravity.fluidengine.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChecksumRejectionTest {

  @Test
  fun blankExpectationIsNotChecked() {
    assertNull(rejectChecksum("", "abc"))
    assertNull(rejectChecksum("   ", "abc"))
  }

  @Test
  fun matchIgnoresCaseAndSpaces() {
    assertNull(rejectChecksum(" ABCDEF ", "abcdef"))
  }

  @Test
  fun mismatchIsRejected() {
    assertNotNull(rejectChecksum("abcdef", "abcdee"))
  }

  @Test
  fun sha256OfAFileIsTheStandardDigest() {
    val file = File.createTempFile("engine-update", ".bin")
    try {
      file.writeText("abc")
      assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        sha256Of(file),
      )
    } finally {
      file.delete()
    }
  }
}
