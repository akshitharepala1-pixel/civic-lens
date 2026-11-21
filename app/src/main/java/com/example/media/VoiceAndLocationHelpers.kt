package com.example.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import com.example.domain.CivicLanguage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Boolean {
        return try {
            stopAndDiscard()
            val file = File(context.cacheDir, "civic_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            true
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to start recording", e)
            stopAndDiscard()
            false
        }
    }

    fun stopAndReadBytes(): ByteArray? {
        val file = outputFile
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w("VoiceRecorder", "Error stopping MediaRecorder", e)
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        } finally {
            recorder = null
        }
        val bytes = try {
            if (file != null && file.exists() && file.length() > 0L) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to read recorded audio bytes", e)
            null
        } finally {
            file?.delete()
            outputFile = null
        }
        return bytes
    }

    fun stopAndDiscard() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class CivicTtsHelper(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    private var pendingLanguage: CivicLanguage = CivicLanguage.ENGLISH

    fun speak(text: String, language: CivicLanguage) {
        if (text.isBlank()) return
        val existing = tts
        if (existing == null) {
            pendingText = text
            pendingLanguage = language
            tts = TextToSpeech(context.applicationContext) { status ->
                isReady = (status == TextToSpeech.SUCCESS)
                if (isReady) {
                    pendingText?.let { queued ->
                        pendingText = null
                        speakNow(queued, pendingLanguage)
                    }
                }
            }
            return
        }
        if (isReady) {
            speakNow(text, language)
        } else {
            pendingText = text
            pendingLanguage = language
        }
    }

    private fun speakNow(text: String, language: CivicLanguage) {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(language.localeTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.ENGLISH
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "civiclens_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        pendingText = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

data class ResolvedLocation(
    val wardName: String,
    val approxLocation: String,
    val latitude: Double,
    val longitude: Double
)

object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun fetchCoarseLocation(context: Context): ResolvedLocation? = withContext(Dispatchers.IO) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }
            } ?: return@withContext null

            val lat = location.latitude
            val lng = location.longitude
            var ward = "Ward Area (${String.format(Locale.US, "%.2f, %.2f", lat, lng)})"
            var landmark = "Approx. coordinates ${String.format(Locale.US, "%.4f, %.4f", lat, lng)}"

            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                val addr = addresses?.firstOrNull()
                if (addr != null) {
                    val subLocality = addr.subLocality ?: addr.thoroughfare ?: addr.featureName
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    if (!subLocality.isNullOrBlank() && !city.isNullOrBlank()) {
                        ward = "$subLocality, $city"
                    } else if (!city.isNullOrBlank()) {
                        ward = city
                    }
                    landmark = listOfNotNull(addr.featureName, addr.thoroughfare, addr.subLocality)
                        .distinct()
                        .joinToString(", ")
                        .ifBlank { ward }
                }
            } catch (geoEx: Exception) {
                Log.w("LocationHelper", "Geocoder lookup fallback used", geoEx)
            }

            ResolvedLocation(
                wardName = ward.take(90),
                approxLocation = landmark.take(180),
                latitude = lat,
                longitude = lng
            )
        } catch (e: Exception) {
            Log.e("LocationHelper", "Coarse location lookup failed", e)
            null
        }
    }
}

object MediaUtils {
    fun createCameraTempUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_images").apply { mkdirs() }
        val file = File(dir, "civic_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun loadScaledBitmap(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val width = boundsOpts.outWidth
                val height = boundsOpts.outHeight
                if (width <= 0 || height <= 0) return@withContext null

                var sampleSize = 1
                while (width / sampleSize > maxDim || height / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
            } catch (e: Exception) {
                Log.e("MediaUtils", "Failed to decode image bitmap", e)
                null
            }
        }

    suspend fun extractVideoThumbnail(context: Context, videoUri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                frame?.let {
                    val ratio = minOf(1280f / it.width, 1280f / it.height, 1f)
                    if (ratio < 1f) {
                        it.scale(
                            (it.width * ratio).toInt().coerceAtLeast(1),
                            (it.height * ratio).toInt().coerceAtLeast(1)
                        )
                    } else {
                        it
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaUtils", "Failed to extract video frame", e)
                null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
}
