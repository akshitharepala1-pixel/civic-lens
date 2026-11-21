package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.AiAnalysisDraft
import com.example.domain.CivicCategory
import com.example.domain.CivicLanguage
import com.example.domain.CivicSeverity
import com.example.domain.CpgramsKnowledgeBase
import com.example.domain.GeoClusterUtils
import com.example.domain.PublicCivicReport
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.random.Random

private const val TAG = "CivicAiRepository"

class CivicAiRepository {

    private val civicExtractionSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Concise, actionable civic issue headline (10-120 chars) in English."),
            "description" to Schema.string(description = "Clear, formal grievance description ready for CPGRAMS/municipal filing, synthesizing photo, audio transcript, and citizen text."),
            "transcript" to Schema.string(description = "Accurate verbatim transcription of any spoken audio note in its original language, followed by English translation in parentheses if non-English. Empty string if no audio."),
            "detectedLanguage" to Schema.string(description = "Primary language detected or used by citizen (e.g., English, Hindi, Telugu, Tamil, Bengali, Marathi, Kannada)."),
            "category" to Schema.enumeration(
                values = listOf("ROADS", "SANITATION", "WATER", "ELECTRICITY", "DRAINAGE", "PUBLIC_SAFETY", "OTHER"),
                description = "Primary civic grievance category."
            ),
            "severity" to Schema.enumeration(
                values = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL"),
                description = "Urgency/severity level based on public safety, health hazard, and disruption."
            ),
            "riskScore" to Schema.integer(description = "Integer hazard risk score from 0 to 100."),
            "confidence" to Schema.double(description = "Model classification confidence from 0.0 to 1.0. Use below 0.75 if inputs are ambiguous, blurry, contradictory, or incomplete."),
            "suggestedWard" to Schema.string(description = "Extracted ward, neighborhood, or locality name if mentioned or visible; otherwise fallback to provided ward hint."),
            "suggestedLandmark" to Schema.string(description = "Specific street, bus stop, junction, or landmark extracted from text/audio/image."),
            "mediaAuthenticityObservation" to Schema.string(description = "Careful, non-absolute observation about visual/audio evidence clarity and consistency (never claim certainty about deepfakes; cite visible cues and confidence).")
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = civicExtractionSchema
                temperature = 0.2f
            }
        )
    }

    private val policyAssistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.35f
            }
        )
    }

    suspend fun analyzeMultimodalReport(
        citizenText: String,
        preferredLanguage: CivicLanguage,
        wardHint: String,
        approxLocationHint: String,
        latitude: Double?,
        longitude: Double?,
        photoBitmap: Bitmap?,
        audioBytes: ByteArray?,
        hasVideo: Boolean,
        existingPublicReports: List<PublicCivicReport>
    ): AiAnalysisDraft {
        val promptText = buildString {
            appendLine("You are CivicLens AI, an ethical civic grievance triage and CPGRAMS routing engine.")
            appendLine("Analyze the citizen's multimodal inputs (text, image/video frame, and/or voice recording).")
            appendLine("Citizen preferred language: ${preferredLanguage.displayName} (${preferredLanguage.nativeLabel}).")
            appendLine("Provided ward/area hint: ${wardHint.ifBlank { "Not specified" }}")
            appendLine("Provided landmark/address hint: ${approxLocationHint.ifBlank { "Not specified" }}")
            if (citizenText.isNotBlank()) {
                appendLine("Citizen written/typed input: $citizenText")
            }
            if (hasVideo) {
                appendLine("Note: Citizen attached a video clip; the attached image is a representative frame from that video.")
            }
            appendLine()
            appendLine("Strict Guardrails:")
            appendLine("1. Do NOT invent personal names, phone numbers, or house numbers.")
            appendLine("2. If an audio recording is provided, transcribe it faithfully in 'transcript', even if spoken in a regional language.")
            appendLine("3. Set 'confidence' honestly (< 0.75 if evidence is blurry, vague, or ambiguous) so low-confidence items are flagged for human review.")
            appendLine("4. In 'mediaAuthenticityObservation', describe only observable visual/audio quality and consistency cues with probabilistic confidence—never claim certainty about deepfakes or tampering.")
        }

        val inputContent = content {
            text(promptText)
            if (photoBitmap != null) {
                image(photoBitmap)
            }
            if (audioBytes != null && audioBytes.isNotEmpty() && audioBytes.size < 18 * 1024 * 1024) {
                inlineData(audioBytes, "audio/mp4")
            }
        }

        val response = try {
            withTimeout(60_000L) {
                structuredModel.generateContent(inputContent)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini multimodal analysis timed out", e)
            throw IllegalStateException("AI analysis timed out after 60 seconds. Please check your network and retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini multimodal analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "AI analysis failed.", e)
        }

        val rawJson = response.text
        if (rawJson.isNullOrBlank()) {
            throw IllegalStateException("AI model returned an empty analysis response.")
        }

        val json = JSONObject(rawJson)
        val category = CivicCategory.safeParse(json.optString("category", "OTHER"))
        val severity = CivicSeverity.safeParse(json.optString("severity", "MEDIUM"))
        val riskScore = json.optInt("riskScore", 55).coerceIn(0, 100)
        val confidence = json.optDouble("confidence", 0.75).coerceIn(0.0, 1.0)
        val needsHumanReview = confidence < 0.75

        val route = CpgramsKnowledgeBase.resolveRoute(category)
        val resolvedWard = json.optString("suggestedWard")
            .takeIf { it.isNotBlank() && !it.equals("Not specified", ignoreCase = true) }
            ?: wardHint.ifBlank { "General Municipal Ward" }
        val resolvedLandmark = json.optString("suggestedLandmark")
            .takeIf { it.isNotBlank() && !it.equals("Not specified", ignoreCase = true) }
            ?: approxLocationHint.ifBlank { resolvedWard }

        val title = json.optString("title").ifBlank { "${category.label} issue in $resolvedWard" }
        val description = json.optString("description").ifBlank {
            citizenText.ifBlank { "Citizen reported a ${category.label.lowercase()} civic issue at $resolvedLandmark." }
        }
        val transcript = json.optString("transcript", "")
        val detectedLang = json.optString("detectedLanguage").ifBlank { preferredLanguage.displayName }
        val mediaNote = json.optString("mediaAuthObservation").ifBlank {
            if (photoBitmap == null && audioBytes == null) {
                "Text-only submission; no media attached for visual verification."
            } else {
                "Media evidence attached; automated visual/audio check completed (confidence: ${(confidence * 100).toInt()}%)."
            }
        }

        val matchedDuplicates = GeoClusterUtils.findPotentialDuplicates(
            category = category,
            wardName = resolvedWard,
            lat = latitude,
            lng = longitude,
            title = title,
            existingPublicReports = existingPublicReports
        )

        val clusterKey = matchedDuplicates.firstOrNull()?.clusterKey
            ?: GeoClusterUtils.buildClusterKey(category, resolvedWard, latitude, longitude)

        val trackingId = "CL-2026-${Random.nextInt(100000, 999999)}"

        return AiAnalysisDraft(
            trackingId = trackingId,
            title = title.take(150),
            description = description.take(1800),
            transcript = transcript.take(1800),
            language = detectedLang.take(40),
            category = category,
            severity = severity,
            riskScore = riskScore,
            confidence = confidence,
            needsHumanReview = needsHumanReview,
            route = route,
            wardName = resolvedWard.take(90),
            approxLocation = resolvedLandmark.take(180),
            latitude = latitude,
            longitude = longitude,
            clusterKey = clusterKey,
            mediaAuthNote = mediaNote.take(450),
            matchedDuplicates = matchedDuplicates
        )
    }

    fun streamPolicyExplanation(
        question: String,
        preferredLanguage: CivicLanguage
    ): Flow<String> = flow {
        val officialCatalog = CpgramsKnowledgeBase.allRoutes().joinToString("\n") { r ->
            "- ${r.category.label}: ${r.departmentName} (${r.departmentCode}) | ${r.cpgramsMinistry} | SLA: ${r.slaHours}h | Source: ${r.citationUrl}"
        }
        val prompt = """
            You are the CivicLens Policy & CPGRAMS Assistant.
            Answer the citizen's question concisely in ${preferredLanguage.displayName}.
            Ground every department routing or grievance escalation claim in the official references below and cite the exact URLs visibly.
            Never fabricate policy rules or claim automatic government submission.

            Official CPGRAMS & Municipal Knowledge Base:
            $officialCatalog

            Citizen Question: $question
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(45_000L) {
            policyAssistantModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }
}
