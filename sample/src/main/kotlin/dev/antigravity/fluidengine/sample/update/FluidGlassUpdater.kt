package dev.antigravity.fluidengine.sample.update

import android.content.Context
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.net.EngineHttp
import dev.antigravity.fluidengine.sample.BuildConfig
import dev.antigravity.fluidengine.update.AndroidAppUpdateInstaller
import dev.antigravity.fluidengine.update.EngineAppUpdater
import dev.antigravity.fluidengine.update.UpdateSource

/**
 * Dove Fluid Glass pubblica le sue release.
 *
 * Lo stesso manifest.json che il Pampa Store legge per mostrare l'app: una sola fonte, quindi non
 * esiste il caso in cui lo store offre una versione e l'app in-app ne offre un'altra. Vive nella
 * radice del repo dell'engine, perché l'app È il repo dell'engine aperto da solo.
 */
const val FLUID_GLASS_MANIFEST_URL =
  "https://raw.githubusercontent.com/Casual76/fluid-engine/main/manifest.json"

/** L'aggiornamento in-app, costruito sui moduli dell'engine — stesso specchio di KeyVoice. */
fun fluidGlassUpdater(
  context: Context,
  manifestUrl: String = FLUID_GLASS_MANIFEST_URL,
): AppUpdater {
  val http = EngineHttp(userAgent = "FluidGlassUpdater/${BuildConfig.VERSION_NAME}")
  return EngineAppUpdater(
    http = http,
    source = UpdateSource(
      manifestUrl = manifestUrl,
      applicationId = context.packageName,
    ),
    installer = AndroidAppUpdateInstaller(context.applicationContext, http),
  )
}
