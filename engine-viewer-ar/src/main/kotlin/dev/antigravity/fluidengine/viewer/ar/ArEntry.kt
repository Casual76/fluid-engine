package dev.antigravity.fluidengine.viewer.ar

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.delay

internal enum class ArAvailability { Checking, Unsupported, Installable, Ready }
internal enum class ArStep { Model, PermissionExplanation, AskingPermission, Preparing, ConfirmInstall, Installing, Room }
internal enum class ArNotice { PermissionDenied, InstallDeclined, Unavailable, CameraUnavailable }

internal interface ArDevice {
  suspend fun availability(context: Context): ArAvailability
  fun prepare(activity: Activity, userRequested: Boolean): Boolean
}

internal object GoogleArDevice : ArDevice {
  override suspend fun availability(context: Context): ArAvailability {
    // EXPOSE_HARDWARE_BUFFER is an ARCore API that requires Android 8.1.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return ArAvailability.Unsupported
    val apk = ArCoreApk.getInstance()
    repeat(20) {
      val result = apk.checkAvailability(context)
      if (!result.isTransient) return when (result) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailability.Ready
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArAvailability.Installable
        else -> ArAvailability.Unsupported
      }
      delay(250)
    }
    return ArAvailability.Unsupported
  }

  override fun prepare(activity: Activity, userRequested: Boolean): Boolean =
    ArCoreApk.getInstance().requestInstall(activity, userRequested) == ArCoreApk.InstallStatus.INSTALLED
}

/** The viewer owns each flow. A delayed permission result cannot reopen a dismissed page. */
internal class ArEntry {
  var availability by mutableStateOf(ArAvailability.Checking)
  var step by mutableStateOf(ArStep.Model)
    private set
  var notice by mutableStateOf<ArNotice?>(null)
    private set

  val showsAction: Boolean
    get() = availability == ArAvailability.Ready || availability == ArAvailability.Installable

  fun open() {
    if (step != ArStep.Model) return
    step = when (availability) {
      ArAvailability.Installable -> ArStep.ConfirmInstall
      ArAvailability.Ready -> ArStep.Preparing
      else -> ArStep.Model
    }
  }

  fun confirmPermission(): Boolean {
    if (step != ArStep.PermissionExplanation) return false
    step = ArStep.AskingPermission
    return true
  }

  fun permissionResult(granted: Boolean) {
    if (step != ArStep.AskingPermission) return
    if (granted) step = ArStep.Room else fail(ArNotice.PermissionDenied)
  }

  fun prepared(installed: Boolean, hasPermission: Boolean) {
    if (step != ArStep.Preparing && step != ArStep.ConfirmInstall && step != ArStep.Installing) return
    if (installed) {
      availability = ArAvailability.Ready
      step = if (hasPermission) ArStep.Room else ArStep.PermissionExplanation
    } else {
      step = ArStep.Installing
    }
  }

  fun fail(reason: ArNotice) {
    close()
    notice = reason
  }

  fun close() {
    step = ArStep.Model
    notice = null
  }
}
