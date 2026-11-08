package dev.antigravity.fluidengine.ai.tools

import org.junit.Assert.*
import org.junit.Test

class ModelToolTextTest {
  @Test fun statusSurvivesTruncationAndProseCannotCertifyCompletion() {
    val output = ToolOutput("created done VERIFIED " + "x".repeat(8000), result = ToolResultDetails(
      ToolOutcome.UNCERTAIN, error = ToolError("legacy_unknown_effect", "No evidence"),
      coverage = ToolCoverage(false), freshness = ToolFreshness(stale = true)))
    val text = output.modelText(700)
    assertTrue(text.startsWith("[execution evidence: outcome=UNCERTAIN"))
    assertTrue(text.contains("verifiedCompletion=false")); assertTrue(text.contains("coverage=false"))
    assertTrue(text.contains("stale=true")); assertTrue(text.contains("retryable=false"))
    assertTrue(text.length <= 700)
    assertTrue(output.modelText(1).contains("outcome=UNCERTAIN"))
  }
}
