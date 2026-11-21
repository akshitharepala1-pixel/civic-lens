package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.AiAnalysisDraft
import com.example.domain.GeoClusterUtils
import com.example.domain.PrivateCivicReport
import com.example.domain.PrivateReportStatus
import com.example.domain.PublicCivicReport
import com.example.domain.PublicReportStatus
import com.example.domain.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CivicFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing CivicLens data.")

    suspend fun ensureUserProfile(user: FirebaseUser, preferredLanguage: String): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Authenticated user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = user.displayName ?: "CivicLens Citizen",
                    email = user.email ?: "citizen@civiclens.local",
                    preferredLanguage = preferredLanguage
                )
                docRef.set(profile.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeMyReports(userId: String): Flow<List<PrivateCivicReport>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank." }
        val path = "users/$userId/reports"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("reports")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PrivateCivicReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicReports(): Flow<List<PublicCivicReport>> = flow {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) { "User must be signed in to observe public dashboard." }
        val path = "public_reports"
        emitAll(
            db.collection("public_reports")
                .whereEqualTo("visibility", "PUBLIC_ANONYMIZED")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PublicCivicReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.reportCount }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitConsentedReport(
        draft: AiAnalysisDraft,
        editedTitle: String,
        editedDescription: String,
        editedWard: String,
        editedLandmark: String,
        hasPhoto: Boolean,
        hasVideo: Boolean,
        hasAudio: Boolean,
        userConsented: Boolean,
        joinClusterPublicReport: PublicCivicReport?
    ): Result<String> {
        var currentPath = "users/unknown/reports"
        return try {
            require(userConsented) { "Explicit user review and consent is required before filing." }
            val uid = requireUserId()
            val cleanTitle = editedTitle.trim().take(160)
            val cleanDesc = editedDescription.trim().take(2000)
            val cleanWard = editedWard.trim().take(100)
            val cleanLandmark = editedLandmark.trim().take(200)
            require(cleanTitle.length >= 3) { "Report title must be at least 3 characters." }
            require(cleanDesc.length >= 5) { "Report description must be at least 5 characters." }
            require(cleanWard.length >= 2) { "Ward or locality must be specified." }

            val publicDocId: String
            if (joinClusterPublicReport != null && joinClusterPublicReport.id.isNotBlank() &&
                joinClusterPublicReport.parsedStatus() != PublicReportStatus.RESOLVED
            ) {
                publicDocId = joinClusterPublicReport.id
                currentPath = "public_reports/$publicDocId"
                db.collection("public_reports")
                    .document(publicDocId)
                    .update(
                        mapOf(
                            "reportCount" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            } else {
                val publicRef = db.collection("public_reports").document()
                publicDocId = publicRef.id
                currentPath = publicRef.path
                val publicReport = PublicCivicReport(
                    creatorUid = uid,
                    visibility = "PUBLIC_ANONYMIZED",
                    trackingCode = draft.trackingId,
                    title = cleanTitle,
                    category = draft.category.name,
                    severity = draft.severity.name,
                    riskScore = draft.riskScore.toLong(),
                    confidence = draft.confidence,
                    needsHumanReview = draft.needsHumanReview,
                    departmentName = draft.route.departmentName,
                    departmentCode = draft.route.departmentCode,
                    citationUrl = draft.route.citationUrl,
                    wardName = cleanWard,
                    geoCellLat = GeoClusterUtils.coarsenCoord(draft.latitude, 12.97),
                    geoCellLng = GeoClusterUtils.coarsenCoord(draft.longitude, 77.59),
                    clusterKey = draft.clusterKey,
                    reportCount = 1L,
                    slaHours = draft.route.slaHours.toLong(),
                    status = PublicReportStatus.OPEN.name
                )
                publicRef.set(publicReport.toCreateMap(uid)).await()
            }

            val privateRef = db.collection("users").document(uid).collection("reports").document()
            currentPath = privateRef.path
            val privateReport = PrivateCivicReport(
                userId = uid,
                trackingId = draft.trackingId,
                title = cleanTitle,
                description = cleanDesc,
                transcript = draft.transcript.takeIf { it.isNotBlank() },
                language = draft.language,
                category = draft.category.name,
                severity = draft.severity.name,
                riskScore = draft.riskScore.toLong(),
                confidence = draft.confidence,
                needsHumanReview = draft.needsHumanReview,
                departmentName = draft.route.departmentName,
                departmentCode = draft.route.departmentCode,
                cpgramsMinistry = draft.route.cpgramsMinistry,
                citationTitle = draft.route.citationTitle,
                citationUrl = draft.route.citationUrl,
                wardName = cleanWard,
                approxLocation = cleanLandmark.takeIf { it.length >= 2 } ?: cleanWard,
                latitude = draft.latitude,
                longitude = draft.longitude,
                clusterId = publicDocId,
                hasPhoto = hasPhoto,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                mediaAuthNote = draft.mediaAuthNote,
                status = PrivateReportStatus.SUBMITTED.name,
                userConsented = true,
                publicReportId = publicDocId
            )
            privateRef.set(privateReport.toCreateMap(uid)).await()
            Result.success(draft.trackingId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun advancePrivateReportStatus(
        userId: String,
        report: PrivateCivicReport,
        nextStatus: PrivateReportStatus
    ): Result<Unit> {
        val docPath = "users/$userId/reports/${report.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(report.id.isNotBlank()) { "Report ID cannot be blank." }
            if (report.parsedStatus() == PrivateReportStatus.RESOLVED) {
                return Result.success(Unit)
            }
            db.collection("users")
                .document(uid)
                .collection("reports")
                .document(report.id)
                .update(
                    mapOf(
                        "status" to nextStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            val publicId = report.publicReportId
            if (!publicId.isNullOrBlank()) {
                val mappedPublicStatus = when (nextStatus) {
                    PrivateReportStatus.DRAFTED, PrivateReportStatus.SUBMITTED -> PublicReportStatus.OPEN.name
                    PrivateReportStatus.IN_PROGRESS -> PublicReportStatus.IN_PROGRESS.name
                    PrivateReportStatus.RESOLVED -> PublicReportStatus.RESOLVED.name
                }
                try {
                    val pubDoc = db.collection("public_reports").document(publicId).get().await()
                    val creatorUid = pubDoc.getString("creatorUid")
                    val currentPublicStatus = pubDoc.getString("status")
                    if (creatorUid == uid && currentPublicStatus != PublicReportStatus.RESOLVED.name) {
                        db.collection("public_reports").document(publicId)
                            .update(
                                mapOf(
                                    "status" to mappedPublicStatus,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .await()
                    }
                } catch (pubEx: Exception) {
                    handleFirestoreError(pubEx, OperationType.UPDATE, "public_reports/$publicId")
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePrivateReport(userId: String, reportId: String): Result<Unit> {
        val docPath = "users/$userId/reports/$reportId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(reportId.isNotBlank()) { "Report ID cannot be blank." }
            db.collection("users").document(uid).collection("reports").document(reportId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
