package dev.antigravity.fluidengine.viewer.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArEntryTest {
  @Test
  fun checkingAndUnsupportedKeepTheModelWithoutAnArAction() {
    val entry = ArEntry()
    for (availability in listOf(ArAvailability.Checking, ArAvailability.Unsupported)) {
      entry.availability = availability
      entry.open()
      assertFalse(entry.showsAction)
      assertEquals(ArStep.Model, entry.step)
    }
  }

  @Test
  fun installIsOfferedBeforeTheCameraPermission() {
    val entry = ArEntry().apply { availability = ArAvailability.Installable }
    assertTrue(entry.showsAction)
    entry.open()
    assertEquals(ArStep.ConfirmInstall, entry.step)
    assertFalse(entry.confirmPermission())
    entry.prepared(installed = false, hasPermission = false)
    assertEquals(ArStep.Installing, entry.step)
    entry.prepared(installed = true, hasPermission = false)
    assertEquals(ArStep.PermissionExplanation, entry.step)
  }

  @Test
  fun denialAndBackBothReturnToTheModel() {
    val entry = ArEntry().apply { availability = ArAvailability.Ready }
    entry.open()
    entry.prepared(installed = true, hasPermission = false)
    assertTrue(entry.confirmPermission())
    entry.permissionResult(false)
    assertEquals(ArNotice.PermissionDenied, entry.notice)
    assertEquals(ArStep.Model, entry.step)
    entry.close()
    entry.open()
    entry.prepared(installed = true, hasPermission = true)
    assertEquals(ArStep.Room, entry.step)
    entry.close()
    assertEquals(ArStep.Model, entry.step)
    assertNull(entry.notice)
  }

  @Test
  fun latePermissionResultCannotOpenAnArViewAfterDismissal() {
    val entry = ArEntry().apply { availability = ArAvailability.Ready }
    entry.open()
    entry.prepared(installed = true, hasPermission = false)
    assertTrue(entry.confirmPermission())
    entry.close()
    entry.permissionResult(true)
    assertEquals(ArStep.Model, entry.step)
  }
}
