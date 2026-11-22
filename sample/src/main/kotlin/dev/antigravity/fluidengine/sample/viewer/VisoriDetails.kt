package dev.antigravity.fluidengine.sample.viewer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.viewer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

private fun section(title: String, vararg fields: Pair<String, String?>) = FluidViewerInfoSection(title,
  fields.mapNotNull { (label, value) -> value?.takeIf(String::isNotBlank)?.let { FluidViewerInfoRow(label, it) } })

@Composable
internal fun visoriDetails(sample: VisoreFile, file: FluidViewerFile, document: FluidViewerDocument?, video: VisoriMedia?,
  audio: VisoriMedia?, resumeVideo: Boolean): FluidViewerInfo {
  val context = LocalContext.current
  val base = remember(file, sample) {
    FluidViewerInfo(file.info, title = if (sample.tipo == VisoreTipo.Audio) sample.title else file.name,
      facts = listOfNotNull(file.name.substringAfterLast('.', "").takeIf(String::isNotBlank)?.uppercase()))
  }
  val metadata by key(file, document) {
    produceState(base) {
      value = withContext(Dispatchers.IO) {
        try { readDetails(context, sample, file, document, base) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { base }
      }
    }
  }
  val reading = section("Lettura", "Preset" to sample.tipo.etichetta, "Aperto da" to decodedBy(document),
    "Non si apre" to document?.failure?.message, "Fonte" to sample.credit, "Oggi in Universal Converter" to sample.today,
    "Sfogliato via" to if (sample.tipo == VisoreTipo.Video) "Si ferma. Al ritorno " +
      (if (resumeVideo) "riprende, se suonava." else "resta in pausa.") else null,
    "Fermate" to video?.takeIf { sample.tipo == VisoreTipo.Video }?.stops?.toString())
  val actions = if (sample.resource == null) emptyList() else listOf(
    FluidViewerInfoAction("Apri con…") { VisoriFiles.openWith(context, sample) },
    FluidViewerInfoAction("Condividi") { VisoriFiles.share(context, sample) })
  return metadata.copy(reading = reading, actions = actions,
    audioWaveform = if (sample.tipo == VisoreTipo.Audio && audio != null) ({ Waveform(audio.session) }) else null)
}

private suspend fun readDetails(context: Context, sample: VisoreFile, file: FluidViewerFile,
  document: FluidViewerDocument?, base: FluidViewerInfo): FluidViewerInfo {
  val disk = File(requireNotNull(file.uri.path))
  val thumb = sample.cover?.let { BitmapFactory.decodeResource(context.resources, it,
    BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 })?.asImageBitmap() }
  val info = base.copy(thumbnail = thumb)
  if (!disk.isFile) return info
  return when (sample.tipo) {
    VisoreTipo.Audio -> audioDetails(disk, info)
    VisoreTipo.Eseguibile -> if (file.name.endsWith(".apk", true)) apkDetails(context, disk, info) else info
    VisoreTipo.Foto -> {
      val exif = ExifInterface(disk.path)
      val location = FloatArray(2)
      val gps = if (exif.getLatLong(location)) location.joinToString(", ") else null
      val camera = section("Fotocamera", "Modello" to exif.getAttribute(ExifInterface.TAG_MODEL),
        "ISO" to exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
        "Tempo (s)" to exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
        "Focale (mm)" to exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
        "Diaframma" to exif.getAttribute(ExifInterface.TAG_F_NUMBER))
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(disk.path, bounds)
      info.copy(title = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: info.title,
        facts = info.facts + listOfNotNull(if (bounds.outWidth > 0) "${bounds.outWidth} × ${bounds.outHeight}" else null,
          bounds.outColorSpace?.name), sections = listOf(camera),
        location = gps?.let { coordinates -> FluidViewerLocation(coordinates, openMap = {
          val uri = android.net.Uri.parse("geo:${location[0]},${location[1]}")
          context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        }) })
    }
    VisoreTipo.Pagine -> {
      val source = (document?.content as? FluidViewerContent.Pages)?.source ?: return info
      if (source.count == 0) return info
      val sizes = (0 until source.count).map { source.size(it) }
      val first = sizes.first()
      val target = IntSize(120, (120L * first.height / first.width.coerceAtLeast(1)).toInt().coerceIn(1, 240))
      val paper = source.render(0, target)
      val version = disk.bufferedReader(Charsets.ISO_8859_1).use { it.readLine()?.take(12)?.removePrefix("%PDF-") }
      val formats = sizes.groupingBy { "${it.width} × ${it.height} pt" }.eachCount()
      info.copy(thumbnail = paper, searchPages = if (Build.VERSION.SDK_INT >= 35) ({ phrase -> searchPdf(disk, phrase) }) else null, subtitle = "Documento a pagine · ${file.sizeBytes?.let(::formatBytes).orEmpty()}",
        facts = listOfNotNull("${source.count} pagine", version?.let { "PDF $it" }),
        sections = listOf(section("Documento", "Titolo" to sample.title, "Versione PDF" to version),
          FluidViewerInfoSection("Pagine", formats.map { FluidViewerInfoRow(it.key, it.value.toString()) }),
          section("Impronta", "SHA-256" to sha256(disk.readBytes()))))
    }
    VisoreTipo.Testo, VisoreTipo.Codice, VisoreTipo.Vuoto -> {
      val source = (document?.content as? FluidViewerContent.Text)?.source ?: return info
      // The registry can keep its pending source as the stable identity after opening the document.
      val text = source as? FluidTextDocument ?: disk.inputStream().use {
        FluidTextDecoder.read(it, file.name, file.mimeType, file.sizeBytes)
      } ?: return info
      val prefix = disk.inputStream().bufferedReader(text.charset.charset).use {
        val chars = CharArray(8192)
        String(chars, 0, it.read(chars).coerceAtLeast(0))
      }
      val newline = when {
        "\r\n" in prefix -> "CRLF"
        '\n' in prefix -> "LF"
        '\r' in prefix -> "CR"
        else -> null
      }
      info.copy(facts = listOfNotNull(text.language, text.charset.charset.name(), newline, "${text.lineCount} righe"),
        sections = listOf(section("Testo", "Codifica" to text.charset.charset.name(), "Fine riga" to newline,
          "Lettura" to if (text.truncated) "Troncato" else "Completa")))
    }
    else -> info
  }
}

private fun audioDetails(disk: File, info: FluidViewerInfo): FluidViewerInfo {
  val metadata = MediaMetadataRetriever()
  try {
    metadata.setDataSource(disk.path)
    fun tag(key: Int) = metadata.extractMetadata(key)
    val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
    val album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM)
    val bitrate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { "${it / 1000} kbps" }
    val format = MediaExtractor().let { extractor ->
      try {
        extractor.setDataSource(disk.path)
        (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
          .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
      } finally { extractor.release() }
    }
    fun number(key: String) = format?.takeIf { it.containsKey(key) }?.getInteger(key)
    val codec = format?.getString(MediaFormat.KEY_MIME)?.substringAfterLast('/')?.let { if (it == "mp4a-latm") "AAC" else it.uppercase() }
    val facts = listOfNotNull(codec, bitrate, number(MediaFormat.KEY_SAMPLE_RATE)?.let { "$it Hz" },
      number(MediaFormat.KEY_CHANNEL_COUNT)?.let { if (it == 1) "Mono" else if (it == 2) "Stereo" else "$it canali" })
    return info.copy(title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: info.title,
      subtitle = listOfNotNull(artist, album).joinToString(" · ").ifBlank { null },
      facts = facts.ifEmpty { info.facts },
      sections = listOf(section("Brano", "Artista" to artist, "Album" to album,
        "Traccia" to tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
        "Anno" to tag(MediaMetadataRetriever.METADATA_KEY_YEAR), "Genere" to tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
        "Formato" to codec, "Bitrate" to bitrate)))
  } finally { metadata.release() }
}

@Suppress("DEPRECATION")
private fun apkDetails(context: Context, disk: File, info: FluidViewerInfo): FluidViewerInfo {
  val pm = context.packageManager
  val flags = PackageManager.GET_PERMISSIONS or if (Build.VERSION.SDK_INT >= 28)
    PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val archive = pm.getPackageArchiveInfo(disk.path, flags) ?: return info
  val app = archive.applicationInfo ?: return info
  app.sourceDir = disk.path
  app.publicSourceDir = disk.path
  val signature = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners?.firstOrNull()
    else archive.signatures?.firstOrNull()
  val cert = signature?.let { CertificateFactory.getInstance("X.509")
    .generateCertificate(it.toByteArray().inputStream()) as X509Certificate }
  val installed = try { pm.getPackageInfo(archive.packageName, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
  val contents = ZipFile(disk).use { zip ->
    val names = zip.entries().asSequence().map { it.name }.toList()
    Triple(names.size, names.count { it.endsWith(".dex") },
      names.filter { it.startsWith("lib/") && it.endsWith(".so") }.map { it.split('/')[1] }.distinct())
  }
  return info.copy(thumbnail = app.takeIf { it.icon != 0 }?.loadIcon(pm)?.toBitmap(192, 192, Bitmap.Config.ARGB_8888)?.asImageBitmap(),
    facts = listOfNotNull("APK", archive.versionName, if (cert != null) "Firmato" else null, if (installed) "Installata" else "Non installata"),
    sections = listOf(
      section("App", "ID" to archive.packageName, "Versione" to archive.versionName,
        "Android minimo / destinazione" to "${app.minSdkVersion} / ${app.targetSdkVersion}"),
      section("Firma", "Certificato" to cert?.subjectX500Principal?.name, "SHA-256" to cert?.encoded?.let(::sha256),
        "Validità" to cert?.let { "${it.notBefore} – ${it.notAfter}" }),
      FluidViewerInfoSection("Permessi", archive.requestedPermissions.orEmpty().mapIndexed { index, name ->
        FluidViewerInfoRow((index + 1).toString(), name)
      }),
      section("Contenuto", "Voci" to contents.first.toString(), "File DEX" to contents.second.toString(),
        "Architetture" to contents.third.joinToString(", "))))
}

private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
  .joinToString(":") { "%02X".format(it) }

@android.annotation.TargetApi(35)
private suspend fun searchPdf(disk: File, query: String): List<FluidViewerPageMatch> = withContext(Dispatchers.IO) {
  android.os.ParcelFileDescriptor.open(disk, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
    android.graphics.pdf.PdfRenderer(descriptor).use { pdf ->
      buildList {
        for (index in 0 until pdf.pageCount) {
          kotlinx.coroutines.yield()
          pdf.openPage(index).use { page ->
            page.searchText(query).forEach { match ->
              add(FluidViewerPageMatch(index, match.bounds.map { rect ->
                androidx.compose.ui.geometry.Rect(rect.left / page.width, rect.top / page.height,
                  rect.right / page.width, rect.bottom / page.height)
              }))
            }
          }
        }
      }
    }
  }
}
