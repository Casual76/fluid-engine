package dev.antigravity.fluidengine.viewer.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.viewer.scene.FluidSceneModel
import dev.antigravity.fluidengine.viewer.scene.FluidSceneViewerControls
import dev.antigravity.fluidengine.viewer.scene.FluidSceneViewerExtension
import kotlinx.coroutines.CancellationException

/** Licenze e condizioni per la sezione licenze dell'app che include questo modulo. */
val FluidArViewerLicenses: List<FluidLicense> = listOf(
  FluidLicense("ARCore SDK for Android", "Google", "ARCore Additional Terms of Service",
    "https://developers.google.com/ar/develop/terms", "Posa dei modelli nello spazio"),
)

/** Provider scoperto dal visore 3D quando l'app include questo modulo. */
class FluidArSceneExtension : FluidSceneViewerExtension {
  override fun create(model: FluidSceneModel): FluidSceneViewerControls =
    ArSceneControls { onClose, onFailure -> ArRoom(model, onClose, onFailure) }
}

internal class ArSceneControls(
  private val device: ArDevice = GoogleArDevice,
  private val room: @Composable (onClose: () -> Unit, onFailure: () -> Unit) -> Unit,
) : FluidSceneViewerControls {
  internal val entry = ArEntry()
  private var onOpen: (() -> Unit)? = null

  override val overlayVisible: Boolean get() = entry.step == ArStep.Room

  override fun actions(toolbar: FluidToolbarScope) {
    if (entry.showsAction) toolbar.text("3d.ar", "AR", { onOpen?.invoke() }, FluidToolbarPlacement.BottomBar)
  }

  @Composable
  override fun Overlay(active: Boolean) {
    if (!active) return
    val context = LocalContext.current
    val activity = context.activity()
    val hasPermission = { context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), entry::permissionResult)
    LaunchedEffect(context) {
      entry.availability = try {
        if (activity == null) ArAvailability.Unsupported else device.availability(context.applicationContext)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Log.w(ArLog, "ARCore availability", error)
        ArAvailability.Unsupported
      } catch (error: LinkageError) {
        Log.w(ArLog, "ARCore unavailable", error)
        ArAvailability.Unsupported
      }
    }
    fun prepare(userRequested: Boolean) {
      if (activity == null) return
      try {
        entry.prepared(device.prepare(activity, userRequested), hasPermission())
      } catch (error: UnavailableUserDeclinedInstallationException) {
        entry.fail(ArNotice.InstallDeclined)
      } catch (error: Exception) {
        Log.w(ArLog, "ARCore installation", error)
        entry.fail(ArNotice.Unavailable)
      }
    }
    DisposableEffect(context) {
      onOpen = {
        entry.open()
        if (entry.step == ArStep.Preparing) prepare(true)
      }
      onDispose {
        onOpen = null
        entry.close()
      }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
      if (entry.step == ArStep.Installing) prepare(false)
    }

    when (entry.step) {
      ArStep.ConfirmInstall -> FluidAlert(
        onDismissRequest = entry::close,
        title = stringResource(R.string.ar_install_title),
        message = stringResource(R.string.ar_install_message),
        actions = listOf(
          FluidAlertAction(stringResource(R.string.ar_cancel), entry::close),
          FluidAlertAction(stringResource(R.string.ar_install), { prepare(true) }, FluidAlertAction.Emphasis.Preferred),
        ),
      )
      ArStep.PermissionExplanation -> {
        val uri = LocalUriHandler.current
        FluidAlert(
          onDismissRequest = entry::close,
          title = stringResource(R.string.ar_permission_title),
          message = stringResource(R.string.ar_permission_message),
          actions = listOf(
            FluidAlertAction(stringResource(R.string.ar_cancel), entry::close),
            FluidAlertAction(stringResource(R.string.ar_continue), {
              if (entry.confirmPermission()) permission.launch(Manifest.permission.CAMERA)
            }, FluidAlertAction.Emphasis.Preferred),
          ),
          content = {
            FluidGlassButton(stringResource(R.string.ar_privacy), { uri.openUri("https://policies.google.com/privacy") })
          },
        )
      }
      ArStep.Room -> Dialog(
        onDismissRequest = entry::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
      ) {
        Box(Modifier.fillMaxSize()) {
          room(entry::close) { entry.fail(ArNotice.CameraUnavailable) }
        }
      }
      else -> Unit
    }
    entry.notice?.let { notice ->
      val message = when (notice) {
        ArNotice.PermissionDenied -> R.string.ar_permission_denied
        ArNotice.InstallDeclined -> R.string.ar_install_declined
        ArNotice.Unavailable -> R.string.ar_unavailable
        ArNotice.CameraUnavailable -> R.string.ar_camera_unavailable
      }
      FluidAlert(
        onDismissRequest = entry::close,
        title = stringResource(R.string.ar_title),
        message = stringResource(message),
        actions = listOf(FluidAlertAction(stringResource(R.string.ar_ok), entry::close)),
      )
    }
  }
}

private tailrec fun Context.activity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.activity()
  else -> null
}

internal const val ArLog = "FluidArViewer"
