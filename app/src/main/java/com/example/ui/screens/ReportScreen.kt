package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.AiAnalysisDraft
import com.example.domain.AiPipelineStep
import com.example.domain.CivicLanguage
import com.example.domain.PublicCivicReport
import com.example.media.CameraXCaptureMode
import com.example.media.CivicTtsHelper
import com.example.media.VoiceRecorderManager
import com.example.ui.ComposerUiState
import com.example.ui.components.ConfidencePill
import com.example.ui.components.CpgramsPackageActions
import com.example.ui.components.GuardrailNoticeCard
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CivicTeal
import com.example.ui.theme.ResolvedEmerald
import com.example.ui.theme.ResolvedEmeraldContainer
import com.example.ui.theme.ResolvedEmeraldOnContainer

@Composable
fun ReportScreen(
    state: ComposerUiState,
    onTextChange: (String) -> Unit,
    onLanguageChange: (CivicLanguage) -> Unit,
    onWardLandmarkChange: (String, String) -> Unit,
    onSampleScenario: (String, String, String) -> Unit,
    onOpenCamera: (CameraXCaptureMode) -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onVideoSelected: (Uri) -> Unit,
    onClearMedia: () -> Unit,
    onRecordingStarted: () -> Unit,
    onRecordingStopped: (ByteArray?) -> Unit,
    onCancelRecording: () -> Unit,
    onClearAudio: () -> Unit,
    onDetectLocation: () -> Unit,
    onRunAnalysis: () -> Unit,
    onUpdateDraftEdits: (String, String, String, String) -> Unit,
    onSelectDuplicateCluster: (PublicCivicReport?) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onDiscardDraft: () -> Unit,
    onSubmitConsented: () -> Unit,
    onClearSuccess: () -> Unit,
    onViewMyReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorderManager(context) }
    val ttsHelper = remember { CivicTtsHelper(context) }
    var permissionHint by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stopAndDiscard()
            onCancelRecording()
            ttsHelper.shutdown()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("video/")) {
                onVideoSelected(uri)
            } else {
                onPhotoSelected(uri)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionHint = null
            if (recorder.startRecording()) {
                onRecordingStarted()
            } else {
                permissionHint = "Microphone unavailable on this device."
            }
        } else {
            permissionHint = "Microphone permission denied. You can type or attach a photo/video instead."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionHint = null
            onDetectLocation()
        } else {
            permissionHint = "Location permission denied. Enter your ward or locality manually below."
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        val isExpandedLayout = maxWidth >= 960.dp

        val intakeContent: @Composable () -> Unit = {
            // Success confirmation banner
            AnimatedVisibility(visible = state.submissionSuccessTrackingId != null) {
                val tid = state.submissionSuccessTrackingId.orEmpty()
                Surface(
                    color = ResolvedEmeraldContainer,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, ResolvedEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ResolvedEmeraldOnContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Grievance Filed • $tid",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ResolvedEmeraldOnContainer
                                )
                            }
                            IconButton(onClick = onClearSuccess) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss confirmation",
                                    tint = ResolvedEmeraldOnContainer
                                )
                            }
                        }
                        Text(
                            text = "Saved to your private CivicLens tracking inbox and published in anonymized form to the public hotspot dashboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ResolvedEmeraldOnContainer
                        )
                        TextButton(onClick = {
                            onClearSuccess()
                            onViewMyReports()
                        }) {
                            Text("View in My Reports →", color = ResolvedEmeraldOnContainer)
                        }
                    }
                }
            }

            // WhatsApp-style Assistant Greeting Bubble
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CivicLens Intake Assistant",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Speak in your language, snap a photo/video, or describe the civic issue below. AI drafts a CPGRAMS-ready complaint for your explicit review.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Regional Language Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Preferred Language (Voice & Output)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    CivicLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = state.preferredLanguage == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text("${lang.nativeLabel} (${lang.displayName})") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }
            }

            // Quick demo scenario chips for fast testing
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SampleChip("Pothole Hazard") {
                    onSampleScenario(
                        "Large waterlogged pothole at Indiranagar 100ft Road bus stop causing two-wheelers to skid.",
                        "Ward 12 - Indiranagar",
                        "100ft Road Bus Stop"
                    )
                }
                SampleChip("Overflowing Drain") {
                    onSampleScenario(
                        "Stormwater drain blocked and overflowing onto main street near Government Primary School for 3 days.",
                        "Ward 8 - Koramangala",
                        "4th Block School Road"
                    )
                }
                SampleChip("Streetlight Outage") {
                    onSampleScenario(
                        "Five consecutive streetlights are non-functional near the railway underpass creating safety risks at night.",
                        "Ward 19 - Malleswaram",
                        "Railway Underpass Junction"
                    )
                }
            }

            // Multimodal Composer Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.citizenText,
                        onValueChange = onTextChange,
                        label = { Text("Describe the issue (any language)") },
                        placeholder = { Text("e.g., Broken water main flooding street outside Sector 4 market...") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("issue_description_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = state.wardName,
                            onValueChange = { onWardLandmarkChange(it, state.approxLocation) },
                            label = { Text("Ward / Zone") },
                            placeholder = { Text("Ward 12") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ward_input")
                        )
                        OutlinedTextField(
                            value = state.approxLocation,
                            onValueChange = { onWardLandmarkChange(state.wardName, it) },
                            label = { Text("Landmark") },
                            placeholder = { Text("Near Bus Stop") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("landmark_input")
                        )
                    }

                    // Attached Image / Video Frame Preview
                    if (state.previewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(MaterialTheme.shapes.medium)
                        ) {
                            Image(
                                bitmap = state.previewBitmap.asImageBitmap(),
                                contentDescription = "Attached civic evidence preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = if (state.videoUri != null) "Video frame attached" else "Photo evidence attached",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = onClearMedia,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove attached media")
                            }
                        }
                    }

                    // Live Voice Recorder Status / Attached Audio Pill
                    if (state.isRecordingAudio) {
                        LiveRecordingBar(
                            seconds = state.audioDurationSeconds,
                            onStop = {
                                val bytes = recorder.stopAndReadBytes()
                                onRecordingStopped(bytes)
                            }
                        )
                    } else if (state.recordedAudioBytes != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Voice note recorded (${state.audioDurationSeconds}s • ${(state.recordedAudioBytes.size / 1024).coerceAtLeast(1)} KB)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                IconButton(onClick = onClearAudio) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove voice note",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Multimodal Input Toolbar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        OutlinedButton(
                            onClick = {
                                permissionHint = null
                                onOpenCamera(CameraXCaptureMode.PHOTO)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("camera_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera")
                        }

                        OutlinedButton(
                            onClick = {
                                permissionHint = null
                                onOpenCamera(CameraXCaptureMode.VIDEO)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_camera_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Short Video")
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                } catch (_: Exception) {
                                    permissionHint = "Media picker unavailable on this device. Describe the issue via text or voice."
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("media_picker_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Photo/Video")
                        }

                        OutlinedButton(
                            onClick = {
                                if (state.isRecordingAudio) {
                                    val bytes = recorder.stopAndReadBytes()
                                    onRecordingStopped(bytes)
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        if (recorder.startRecording()) {
                                            onRecordingStarted()
                                        }
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("voice_record_button")
                        ) {
                            Icon(
                                imageVector = if (state.isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (state.isRecordingAudio) AlertCrimson else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isRecordingAudio) "Stop" else "Voice Note")
                        }

                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onDetectLocation()
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            enabled = !state.isLocating,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detect_location_button")
                        ) {
                            if (state.isLocating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detect Ward")
                        }
                    }

                    if (!permissionHint.isNullOrBlank()) {
                        Text(
                            text = permissionHint!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Button(
                        onClick = onRunAnalysis,
                        enabled = !state.isAnalyzing && !state.isRecordingAudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("analyze_ai_button")
                    ) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Running CivicLens AI Pipeline...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze & Draft CPGRAMS Complaint")
                        }
                    }
                }
            }
        }

        val triageContent: @Composable (Boolean) -> Unit = { showStandbyPlaceholder ->
            // Live Pipeline Progress Card
            AnimatedVisibility(visible = state.isAnalyzing && state.activePipelineStep != null) {
                state.activePipelineStep?.let { step ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "AI Architecture Pipeline • Step ${step.ordinal + 1}/4",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { (step.ordinal + 1) / 4f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${step.title}: ${step.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!state.analysisError.isNullOrBlank()) {
                GuardrailNoticeCard(
                    title = "AI Analysis Error",
                    message = state.analysisError,
                    isAlert = true
                )
            }

            // Structured Draft & Consent Review Gate
            val draft = state.draft
            if (draft != null) {
                DraftReviewAndConsentCard(
                    draft = draft,
                    state = state,
                    onUpdateEdits = onUpdateDraftEdits,
                    onSelectDuplicate = onSelectDuplicateCluster,
                    onConsentChange = onConsentChange,
                    onSpeakDraft = {
                        val spoken = "${state.editedTitle}. Department: ${draft.route.departmentName}. ${state.editedDescription}"
                        ttsHelper.speak(spoken, state.preferredLanguage)
                    },
                    onDiscard = onDiscardDraft,
                    onSubmitConsented = onSubmitConsented
                )
            } else if (showStandbyPlaceholder && !state.isAnalyzing) {
                AiPipelineStandbyCard()
            }
        }

        if (isExpandedLayout) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .widthIn(max = 1280.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    intakeContent()
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    triageContent(true)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                intakeContent()
                triageContent(false)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AiPipelineStandbyCard() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "CPGRAMS Triage & Review Pane",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Enter a description, attach a photo/video, or record a voice note on the left, then select Analyze to generate a verified complaint package here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            AiPipelineStep.entries.forEachIndexed { idx, step ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${idx + 1}. ${step.title}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = step.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            GuardrailNoticeCard(
                title = "Explicit Review Gate",
                message = "Nothing is filed or published to the anonymized dashboard until you verify all fields and check the consent box.",
                isAlert = false
            )
        }
    }
}

@Composable
private fun SampleChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Text(
            text = "Try: $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LiveRecordingBar(seconds: Int, onStop: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(550), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Surface(
        color = AlertCrimson.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AlertCrimson.copy(alpha = alpha)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AlertCrimson.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Recording voice note... ${seconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = AlertCrimson
                )
            }
            TextButton(onClick = onStop) {
                Text("Stop & Attach", color = AlertCrimson)
            }
        }
    }
}

@Composable
private fun DraftReviewAndConsentCard(
    draft: AiAnalysisDraft,
    state: ComposerUiState,
    onUpdateEdits: (String, String, String, String) -> Unit,
    onSelectDuplicate: (PublicCivicReport?) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onSpeakDraft: () -> Unit,
    onDiscard: () -> Unit,
    onSubmitConsented: () -> Unit
) {
    val context = LocalContext.current

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, CivicTeal.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "AI Complaint Package Ready",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tracking ID: ${draft.trackingId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = onSpeakDraft,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read complaint aloud",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Risk, Severity, Confidence badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                SeverityBadge(draft.severity)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Risk ${draft.riskScore}/100",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                ConfidencePill(
                    confidence = draft.confidence,
                    needsHumanReview = draft.needsHumanReview
                )
            }

            // Guardrail: Low-confidence human review banner
            if (draft.needsHumanReview) {
                GuardrailNoticeCard(
                    title = "Flagged for Human Review",
                    message = stringResource(R.string.guardrail_banner_low_conf),
                    isAlert = true
                )
            }

            // Audio transcript if present
            if (draft.transcript.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Voice Transcript (${draft.language})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = draft.transcript,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Probabilistic Media Evidence Observation (Guardrail: never absolute deepfake claims)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Evidence & Authenticity Observation (Probabilistic)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = draft.mediaAuthNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Official CPGRAMS Department Routing & Source Citation
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CPGRAMS Department Routing • ${draft.route.departmentCode}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = draft.route.departmentName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${draft.route.cpgramsMinistry} • Target SLA: ${draft.route.slaHours}h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { CpgramsPackageActions.openExternalUrl(context, draft.route.citationUrl) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Source: ${draft.route.citationTitle} (${draft.route.citationUrl})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open official citation",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Duplicate Clustering Detector
            if (draft.matchedDuplicates.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${draft.matchedDuplicates.size} Similar Open Hotspot(s) Nearby",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        draft.matchedDuplicates.forEach { cluster ->
                            val selected = state.selectedDuplicateToJoin?.id == cluster.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDuplicate(cluster) }
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectDuplicate(cluster) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cluster.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${cluster.wardName} • ${cluster.reportCount} citizen report(s) clustered",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDuplicate(null) }
                        ) {
                            RadioButton(
                                selected = state.selectedDuplicateToJoin == null,
                                onClick = { onSelectDuplicate(null) }
                            )
                            Text(
                                text = "Create new independent hotspot cluster",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Editable Grievance Fields
            Text(
                text = "Review & Edit Before Filing",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = state.editedTitle,
                onValueChange = { onUpdateEdits(it, state.editedDescription, state.editedWard, state.editedLandmark) },
                label = { Text("Grievance Subject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.editedDescription,
                onValueChange = { onUpdateEdits(state.editedTitle, it, state.editedWard, state.editedLandmark) },
                label = { Text("Formal Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.editedWard,
                    onValueChange = { onUpdateEdits(state.editedTitle, state.editedDescription, it, state.editedLandmark) },
                    label = { Text("Ward / Zone") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.editedLandmark,
                    onValueChange = { onUpdateEdits(state.editedTitle, state.editedDescription, state.editedWard, it) },
                    label = { Text("Landmark") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Explicit Consent Guardrail Checkbox
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConsentChange(!state.userConsented) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Checkbox(
                        checked = state.userConsented,
                        onCheckedChange = onConsentChange,
                        modifier = Modifier.testTag("consent_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.consent_statement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!state.submissionError.isNullOrBlank()) {
                GuardrailNoticeCard(
                    title = "Review Required",
                    message = state.submissionError,
                    isAlert = true
                )
            }

            // Action Buttons
            Button(
                onClick = onSubmitConsented,
                enabled = state.userConsented && !state.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_consented_button")
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Filing & Publishing Anonymized...")
                } else {
                    Text(
                        if (state.selectedDuplicateToJoin != null)
                            "Consent & Join Cluster (+1 Report)"
                        else
                            "Consent & Save Anonymized Report"
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val pkg = CpgramsPackageActions.buildPackageTextFromDraft(
                            draft = draft,
                            editedTitle = state.editedTitle,
                            editedDesc = state.editedDescription,
                            editedWard = state.editedWard,
                            editedLandmark = state.editedLandmark
                        )
                        CpgramsPackageActions.sharePackage(
                            context = context,
                            subject = "CivicLens Grievance ${draft.trackingId}: ${state.editedTitle}",
                            body = pkg
                        )
                    },
                    enabled = state.userConsented,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Package")
                }

                OutlinedButton(
                    onClick = {
                        CpgramsPackageActions.openExternalUrl(context, draft.route.citationUrl)
                    },
                    enabled = state.userConsented,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open CPGRAMS")
                }
            }

            TextButton(
                onClick = onDiscard,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Discard Draft")
            }
        }
    }
}
