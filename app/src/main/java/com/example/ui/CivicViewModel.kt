package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CivicAiRepository
import com.example.data.CivicFirestoreRepository
import com.example.domain.AiAnalysisDraft
import com.example.domain.AiPipelineStep
import com.example.domain.CivicLanguage
import com.example.domain.PrivateCivicReport
import com.example.domain.PrivateReportStatus
import com.example.domain.PublicCivicReport
import com.example.domain.UiState
import com.example.media.LocationHelper
import com.example.media.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CivicViewModel"

data class ComposerUiState(
    val citizenText: String = "",
    val preferredLanguage: CivicLanguage = CivicLanguage.ENGLISH,
    val wardName: String = "",
    val approxLocation: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isLocating: Boolean = false,
    val photoUri: Uri? = null,
    val videoUri: Uri? = null,
    val previewBitmap: Bitmap? = null,
    val recordedAudioBytes: ByteArray? = null,
    val audioDurationSeconds: Int = 0,
    val isRecordingAudio: Boolean = false,
    val activePipelineStep: AiPipelineStep? = null,
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val draft: AiAnalysisDraft? = null,
    val editedTitle: String = "",
    val editedDescription: String = "",
    val editedWard: String = "",
    val editedLandmark: String = "",
    val selectedDuplicateToJoin: PublicCivicReport? = null,
    val userConsented: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionSuccessTrackingId: String? = null,
    val submissionError: String? = null
)

data class PolicyAssistantState(
    val question: String = "",
    val isStreaming: Boolean = false,
    val answerText: String = "",
    val errorMessage: String? = null
)

class CivicViewModel(
    private val firestoreRepo: CivicFirestoreRepository,
    private val aiRepo: CivicAiRepository,
    private val currentUserId: String
) : ViewModel() {

    val myReportsState: StateFlow<UiState<List<PrivateCivicReport>>> =
        firestoreRepo.observeMyReports(currentUserId)
            .map<List<PrivateCivicReport>, UiState<List<PrivateCivicReport>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user reports", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load your reports."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val publicReportsState: StateFlow<UiState<List<PublicCivicReport>>> =
        firestoreRepo.observePublicReports()
            .map<List<PublicCivicReport>, UiState<List<PublicCivicReport>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing public dashboard", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load public dashboard."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _composer = MutableStateFlow(ComposerUiState())
    val composer: StateFlow<ComposerUiState> = _composer.asStateFlow()

    private val _policyAssistant = MutableStateFlow(PolicyAssistantState())
    val policyAssistant: StateFlow<PolicyAssistantState> = _policyAssistant.asStateFlow()

    private var recordingTimerJob: Job? = null

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                firestoreRepo.ensureUserProfile(user, CivicLanguage.ENGLISH.displayName)
            }
        }
    }

    fun updateCitizenText(text: String) {
        _composer.update { it.copy(citizenText = text, analysisError = null) }
    }

    fun updateLanguage(language: CivicLanguage) {
        _composer.update { it.copy(preferredLanguage = language) }
    }

    fun updateWardAndLandmark(ward: String, landmark: String) {
        _composer.update { it.copy(wardName = ward, approxLocation = landmark) }
    }

    fun applySampleScenario(sampleText: String, ward: String, landmark: String) {
        _composer.update {
            it.copy(
                citizenText = sampleText,
                wardName = ward,
                approxLocation = landmark,
                analysisError = null,
                submissionSuccessTrackingId = null
            )
        }
    }

    fun attachPhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = MediaUtils.loadScaledBitmap(context, uri)
            _composer.update {
                it.copy(
                    photoUri = uri,
                    videoUri = null,
                    previewBitmap = bmp,
                    analysisError = null
                )
            }
        }
    }

    fun attachVideo(context: Context, uri: Uri) {
        viewModelScope.launch {
            val thumb = MediaUtils.extractVideoThumbnail(context, uri)
            _composer.update {
                it.copy(
                    videoUri = uri,
                    photoUri = null,
                    previewBitmap = thumb,
                    analysisError = null
                )
            }
        }
    }

    fun clearAttachedMedia() {
        _composer.update { it.copy(photoUri = null, videoUri = null, previewBitmap = null) }
    }

    fun setRecordingStarted() {
        recordingTimerJob?.cancel()
        _composer.update {
            it.copy(
                isRecordingAudio = true,
                audioDurationSeconds = 0,
                recordedAudioBytes = null,
                analysisError = null
            )
        }
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _composer.update { state ->
                    state.copy(audioDurationSeconds = state.audioDurationSeconds + 1)
                }
            }
        }
    }

    fun setRecordingFinished(audioBytes: ByteArray?) {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _composer.update {
            it.copy(
                isRecordingAudio = false,
                recordedAudioBytes = audioBytes
            )
        }
    }

    fun cancelRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _composer.update {
            it.copy(
                isRecordingAudio = false,
                audioDurationSeconds = 0
            )
        }
    }

    fun clearRecordedAudio() {
        recordingTimerJob?.cancel()
        _composer.update { it.copy(recordedAudioBytes = null, audioDurationSeconds = 0) }
    }

    fun detectCoarseLocation(context: Context) {
        viewModelScope.launch {
            _composer.update { it.copy(isLocating = true) }
            val resolved = LocationHelper.fetchCoarseLocation(context)
            _composer.update { state ->
                if (resolved != null) {
                    state.copy(
                        isLocating = false,
                        wardName = resolved.wardName,
                        approxLocation = resolved.approxLocation,
                        latitude = resolved.latitude,
                        longitude = resolved.longitude
                    )
                } else {
                    state.copy(
                        isLocating = false,
                        analysisError = "Could not detect coarse location; please enter your ward or locality manually."
                    )
                }
            }
        }
    }

    fun runAiPipelineAnalysis() {
        val snapshot = _composer.value
        if (snapshot.citizenText.isBlank() &&
            snapshot.previewBitmap == null &&
            snapshot.recordedAudioBytes == null
        ) {
            _composer.update {
                it.copy(analysisError = "Please add a photo, video, voice note, or text description first.")
            }
            return
        }

        val currentPublicReports = (publicReportsState.value as? UiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _composer.update {
                it.copy(
                    isAnalyzing = true,
                    analysisError = null,
                    submissionSuccessTrackingId = null,
                    submissionError = null,
                    activePipelineStep = AiPipelineStep.MULTIMODAL_INGEST
                )
            }
            try {
                // Step visual progression while Gemini runs
                val stepperJob = launch {
                    delay(700L)
                    _composer.update { it.copy(activePipelineStep = AiPipelineStep.LLM_PLANNER_RETRIEVAL) }
                    delay(700L)
                    _composer.update { it.copy(activePipelineStep = AiPipelineStep.RISK_SCORER) }
                    delay(600L)
                    _composer.update { it.copy(activePipelineStep = AiPipelineStep.DUPLICATE_CLUSTERING) }
                }

                val draft = aiRepo.analyzeMultimodalReport(
                    citizenText = snapshot.citizenText,
                    preferredLanguage = snapshot.preferredLanguage,
                    wardHint = snapshot.wardName,
                    approxLocationHint = snapshot.approxLocation,
                    latitude = snapshot.latitude,
                    longitude = snapshot.longitude,
                    photoBitmap = snapshot.previewBitmap,
                    audioBytes = snapshot.recordedAudioBytes,
                    hasVideo = snapshot.videoUri != null,
                    existingPublicReports = currentPublicReports
                )
                stepperJob.cancel()

                _composer.update {
                    it.copy(
                        isAnalyzing = false,
                        activePipelineStep = null,
                        draft = draft,
                        editedTitle = draft.title,
                        editedDescription = draft.description,
                        editedWard = draft.wardName,
                        editedLandmark = draft.approxLocation,
                        selectedDuplicateToJoin = draft.matchedDuplicates.firstOrNull(),
                        userConsented = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI pipeline analysis failed", e)
                _composer.update {
                    it.copy(
                        isAnalyzing = false,
                        activePipelineStep = null,
                        analysisError = e.localizedMessage ?: "AI analysis failed. Please retry."
                    )
                }
            }
        }
    }

    fun updateDraftEdits(title: String, description: String, ward: String, landmark: String) {
        _composer.update {
            it.copy(
                editedTitle = title,
                editedDescription = description,
                editedWard = ward,
                editedLandmark = landmark
            )
        }
    }

    fun selectDuplicateCluster(cluster: PublicCivicReport?) {
        _composer.update { it.copy(selectedDuplicateToJoin = cluster) }
    }

    fun setConsentChecked(consented: Boolean) {
        _composer.update { it.copy(userConsented = consented, submissionError = null) }
    }

    fun discardDraft() {
        _composer.update {
            it.copy(
                draft = null,
                userConsented = false,
                selectedDuplicateToJoin = null,
                submissionError = null
            )
        }
    }

    fun submitConsentedReport() {
        val state = _composer.value
        val draft = state.draft ?: return
        if (!state.userConsented) {
            _composer.update {
                it.copy(submissionError = "Guardrail active: Please review the complaint and check consent before filing.")
            }
            return
        }

        viewModelScope.launch {
            _composer.update { it.copy(isSubmitting = true, submissionError = null) }
            val result = firestoreRepo.submitConsentedReport(
                draft = draft,
                editedTitle = state.editedTitle,
                editedDescription = state.editedDescription,
                editedWard = state.editedWard,
                editedLandmark = state.editedLandmark,
                hasPhoto = state.photoUri != null,
                hasVideo = state.videoUri != null,
                hasAudio = state.recordedAudioBytes != null,
                userConsented = state.userConsented,
                joinClusterPublicReport = state.selectedDuplicateToJoin
            )
            result.fold(
                onSuccess = { trackingId ->
                    _composer.update {
                        ComposerUiState(
                            preferredLanguage = it.preferredLanguage,
                            submissionSuccessTrackingId = trackingId
                        )
                    }
                },
                onFailure = { error ->
                    _composer.update {
                        it.copy(
                            isSubmitting = false,
                            submissionError = error.localizedMessage ?: "Failed to save report."
                        )
                    }
                }
            )
        }
    }

    fun clearSubmissionSuccess() {
        _composer.update { it.copy(submissionSuccessTrackingId = null) }
    }

    fun advanceReportStatus(report: PrivateCivicReport, nextStatus: PrivateReportStatus) {
        viewModelScope.launch {
            firestoreRepo.advancePrivateReportStatus(currentUserId, report, nextStatus)
        }
    }

    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            firestoreRepo.deletePrivateReport(currentUserId, reportId)
        }
    }

    fun updatePolicyQuestion(q: String) {
        _policyAssistant.update { it.copy(question = q, errorMessage = null) }
    }

    fun askPolicyAssistant(customQuestion: String? = null) {
        val q = (customQuestion ?: _policyAssistant.value.question).trim()
        if (q.isBlank()) return
        val lang = _composer.value.preferredLanguage
        viewModelScope.launch {
            _policyAssistant.update {
                it.copy(question = q, isStreaming = true, answerText = "", errorMessage = null)
            }
            try {
                aiRepo.streamPolicyExplanation(q, lang).collect { partial ->
                    _policyAssistant.update { it.copy(answerText = partial) }
                }
                _policyAssistant.update { it.copy(isStreaming = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Policy assistant streaming failed", e)
                _policyAssistant.update {
                    it.copy(
                        isStreaming = false,
                        errorMessage = e.localizedMessage ?: "Policy assistant failed to respond."
                    )
                }
            }
        }
    }
}
