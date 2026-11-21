package com.example.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CivicTeal
import java.io.File
import java.util.Locale

private const val TAG = "CivicCameraX"
private const val MAX_SHORT_VIDEO_SECONDS = 30

enum class CameraXCaptureMode(val label: String) {
    PHOTO("Photo"),
    VIDEO("Short Video (30s)")
}

@Composable
fun CivicCameraXCaptureScreen(
    initialMode: CameraXCaptureMode = CameraXCaptureMode.PHOTO,
    onPhotoCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onPickFromGallery: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var captureMode by remember { mutableStateOf(initialMode) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var canSwitchCamera by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            cameraError = "Camera permission is required for live photo and video capture."
        } else {
            cameraError = null
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        activeRecording?.stop()
        onDismiss()
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission, captureMode, useFrontCamera) {
        if (!hasCameraPermission) {
            return@DisposableEffect onDispose {}
        }

        var disposed = false
        var cameraProviderRef: ProcessCameraProvider? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProviderRef = provider

                    val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    canSwitchCamera = hasBack && hasFront

                    if (!hasBack && !hasFront) {
                        cameraError = "No camera hardware detected on this device. You can pick an existing photo or video instead."
                        return@addListener
                    }

                    val selector = when {
                        useFrontCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                        hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                        else -> CameraSelector.DEFAULT_FRONT_CAMERA
                    }

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    provider.unbindAll()

                    val camera = if (captureMode == CameraXCaptureMode.PHOTO) {
                        val imgCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(
                                if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                            )
                            .build()
                        imageCaptureUseCase = imgCapture
                        videoCaptureUseCase = null
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, imgCapture)
                    } else {
                        val qualitySelector = QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                        val recorder = Recorder.Builder()
                            .setQualitySelector(qualitySelector)
                            .build()
                        val vidCapture = VideoCapture.withOutput(recorder)
                        videoCaptureUseCase = vidCapture
                        imageCaptureUseCase = null
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, vidCapture)
                    }

                    boundCamera = camera
                    hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                    if (captureMode == CameraXCaptureMode.VIDEO && camera.cameraInfo.hasFlashUnit()) {
                        camera.cameraControl.enableTorch(flashEnabled)
                    } else if (camera.cameraInfo.hasFlashUnit()) {
                        camera.cameraControl.enableTorch(false)
                    }
                    cameraError = null
                } catch (e: Exception) {
                    Log.e(TAG, "CameraX bind failed", e)
                    cameraError = e.localizedMessage ?: "Unable to start CameraX preview on this device."
                }
            },
            mainExecutor
        )

        onDispose {
            disposed = true
            try {
                activeRecording?.stop()
                activeRecording = null
                isRecordingVideo = false
                cameraProviderRef?.unbindAll()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing CameraX", e)
            }
        }
    }

    LaunchedEffect(flashEnabled, captureMode, boundCamera) {
        val cam = boundCamera ?: return@LaunchedEffect
        imageCaptureUseCase?.flashMode =
            if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(captureMode == CameraXCaptureMode.VIDEO && flashEnabled)
        }
    }

    fun takePhoto() {
        val imgCapture = imageCaptureUseCase ?: return
        if (isCapturingPhoto) return
        isCapturingPhoto = true
        val outputFile = MediaUtils.createCameraImageFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imgCapture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturingPhoto = false
                    val uri = outputFileResults.savedUri ?: MediaUtils.fileToProviderUri(context, outputFile)
                    onPhotoCaptured(uri)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturingPhoto = false
                    Log.e(TAG, "CameraX photo capture failed", exception)
                    cameraError = exception.localizedMessage ?: "Failed to capture photo."
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun toggleVideoRecording() {
        val existing = activeRecording
        if (existing != null && isRecordingVideo) {
            existing.stop()
            return
        }

        val vidCapture = videoCaptureUseCase ?: return
        val outputFile: File = MediaUtils.createCameraVideoFile(context)
        val fileOutputOptions = FileOutputOptions.Builder(outputFile).build()

        // Immutable builder pattern: assign returned PendingRecording when enabling audio.
        var pendingRecording = vidCapture.output.prepareRecording(context, fileOutputOptions)
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = micGranted
        if (micGranted) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }

        recordingSeconds = 0
        activeRecording = pendingRecording.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecordingVideo = true
                    cameraError = null
                }

                is VideoRecordEvent.Status -> {
                    val elapsedSec = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                    recordingSeconds = elapsedSec
                    if (elapsedSec >= MAX_SHORT_VIDEO_SECONDS) {
                        activeRecording?.stop()
                    }
                }

                is VideoRecordEvent.Finalize -> {
                    isRecordingVideo = false
                    activeRecording = null
                    val hasFatalError = event.hasError() &&
                        event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED &&
                        event.error != VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                    if (!hasFatalError && outputFile.exists() && outputFile.length() > 0L) {
                        onVideoCaptured(MediaUtils.fileToProviderUri(context, outputFile))
                    } else {
                        Log.e(TAG, "Video recording error code=${event.error}", event.cause)
                        cameraError = "Video capture failed (code ${event.error}). Try recording at least 1 second."
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        if (hasCameraPermission && cameraError == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.widthIn(max = 460.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (hasCameraPermission) "Camera Unavailable" else "Camera Permission Needed",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = cameraError
                                ?: "Allow camera access to capture live civic issue photos and short videos directly inside CivicLens.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!hasCameraPermission) {
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text("Grant Camera Permission")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onPickFromGallery()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Photo or Video Instead")
                        }
                    }
                }
            }
        }

        // Top Bar Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(
                onClick = {
                    activeRecording?.stop()
                    onDismiss()
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .minimumInteractiveComponentSize()
                    .testTag("camerax_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close camera",
                    tint = Color.White
                )
            }

            if (isRecordingVideo) {
                Surface(
                    color = AlertCrimson.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "REC 00:%02d / 00:%02d",
                            recordingSeconds,
                            MAX_SHORT_VIDEO_SECONDS
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "CivicLens Evidence Camera",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasFlashUnit) {
                    IconButton(
                        onClick = { flashEnabled = !flashEnabled },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .minimumInteractiveComponentSize()
                            .testTag("camerax_flash_button")
                    ) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (flashEnabled) "Turn flash off" else "Turn flash on",
                            tint = if (flashEnabled) CivicTeal else Color.White
                        )
                    }
                }
                if (canSwitchCamera && !isRecordingVideo) {
                    IconButton(
                        onClick = { useFrontCamera = !useFrontCamera },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .minimumInteractiveComponentSize()
                            .testTag("camerax_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch camera lens",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Controls (Mode Switch + Shutter + Optional Audio Toggle)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            if (captureMode == CameraXCaptureMode.VIDEO && !isRecordingVideo && !hasAudioPermission) {
                Surface(
                    onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap to include microphone audio in video",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            } else if (captureMode == CameraXCaptureMode.VIDEO && hasAudioPermission) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = CivicTeal,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Video audio enabled • Up to ${MAX_SHORT_VIDEO_SECONDS}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // Photo vs Short Video mode toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraXCaptureMode.entries.forEach { mode ->
                    val selected = captureMode == mode
                    Surface(
                        color = if (selected) CivicTeal else Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = !isRecordingVideo && !isCapturingPhoto) {
                                captureMode = mode
                            }
                            .semantics(mergeDescendants = true) { }
                            .testTag(
                                if (mode == CameraXCaptureMode.PHOTO) "camerax_mode_photo" else "camerax_mode_video"
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (mode == CameraXCaptureMode.PHOTO) Icons.Default.PhotoCamera else Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Shutter / Record Trigger
            val innerColor by animateColorAsState(
                targetValue = when {
                    captureMode == CameraXCaptureMode.VIDEO -> AlertCrimson
                    else -> Color.White
                },
                label = "shutterInnerColor"
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(3.5.dp, Color.White, CircleShape)
                    .clickable(enabled = hasCameraPermission && cameraError == null && !isCapturingPhoto) {
                        if (captureMode == CameraXCaptureMode.PHOTO) {
                            takePhoto()
                        } else {
                            toggleVideoRecording()
                        }
                    }
                    .semantics {
                        contentDescription = when {
                            captureMode == CameraXCaptureMode.PHOTO -> "Capture civic issue photo"
                            isRecordingVideo -> "Stop recording video"
                            else -> "Start short video recording"
                        }
                    }
                    .testTag("camerax_shutter_button")
            ) {
                if (isCapturingPhoto) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else if (isRecordingVideo) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AlertCrimson)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(innerColor)
                    )
                }
            }
        }
    }
}
