package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class CivicCategory(val label: String, val shortCode: String) {
    ROADS("Roads & Potholes", "ROADS"),
    SANITATION("Sanitation & Waste", "SANITATION"),
    WATER("Water Supply", "WATER"),
    ELECTRICITY("Streetlights & Power", "ELECTRICITY"),
    DRAINAGE("Stormwater & Drainage", "DRAINAGE"),
    PUBLIC_SAFETY("Public Safety", "PUBLIC_SAFETY"),
    OTHER("Other Municipal", "OTHER");

    companion object {
        fun safeParse(raw: String?): CivicCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}

enum class CivicSeverity(val label: String, val slaDefaultHours: Int) {
    LOW("Low", 168),
    MEDIUM("Medium", 72),
    HIGH("High", 48),
    CRITICAL("Critical", 12);

    companion object {
        fun safeParse(raw: String?): CivicSeverity =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MEDIUM
    }
}

enum class PrivateReportStatus(val label: String) {
    DRAFTED("Drafted"),
    SUBMITTED("Submitted"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved");

    companion object {
        fun safeParse(raw: String?): PrivateReportStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SUBMITTED
    }
}

enum class PublicReportStatus(val label: String) {
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved");

    companion object {
        fun safeParse(raw: String?): PublicReportStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OPEN
    }
}

enum class CivicLanguage(val displayName: String, val nativeLabel: String, val localeTag: String) {
    ENGLISH("English", "English", "en-IN"),
    HINDI("Hindi", "हिन्दी", "hi-IN"),
    TELUGU("Telugu", "తెలుగు", "te-IN"),
    TAMIL("Tamil", "தமிழ்", "ta-IN"),
    BENGALI("Bengali", "বাংলা", "bn-IN"),
    MARATHI("Marathi", "मराठी", "mr-IN"),
    KANNADA("Kannada", "ಕನ್ನಡ", "kn-IN")
}

data class CpgramsRoute(
    val category: CivicCategory,
    val departmentName: String,
    val departmentCode: String,
    val cpgramsMinistry: String,
    val citationTitle: String,
    val citationUrl: String,
    val slaHours: Int,
    val policyBasis: String
)

object CpgramsKnowledgeBase {
    private val routes = mapOf(
        CivicCategory.ROADS to CpgramsRoute(
            category = CivicCategory.ROADS,
            departmentName = "Public Works Department (PWD) — Roads Division",
            departmentCode = "PWD-RD-01",
            cpgramsMinistry = "Ministry of Road Transport & Highways / State PWD",
            citationTitle = "CPGRAMS Civic Infrastructure & Road Repair Charter",
            citationUrl = "https://pgportal.gov.in",
            slaHours = 72,
            policyBasis = "Routed via Municipal PWD / Highway Maintenance Wing per MoRTH & Urban Local Body citizen charter."
        ),
        CivicCategory.SANITATION to CpgramsRoute(
            category = CivicCategory.SANITATION,
            departmentName = "Municipal Solid Waste & Sanitation Wing",
            departmentCode = "SBM-SWM-02",
            cpgramsMinistry = "Ministry of Housing & Urban Affairs (Swachh Bharat Mission)",
            citationTitle = "MoHUA Swachh Bharat Urban Solid Waste Guidelines",
            citationUrl = "https://mohua.gov.in",
            slaHours = 24,
            policyBasis = "Routed to Urban Local Body Sanitation Officer aligned with Solid Waste Management Rules & CPGRAMS Municipal Desk."
        ),
        CivicCategory.WATER to CpgramsRoute(
            category = CivicCategory.WATER,
            departmentName = "Municipal Water Supply & Jal Board",
            departmentCode = "AMRUT-WTR-03",
            cpgramsMinistry = "Ministry of Jal Shakti / Urban Water Supply",
            citationTitle = "AMRUT 2.0 Urban Water & Leak Redressal Framework",
            citationUrl = "https://jalshakti-ddws.gov.in",
            slaHours = 24,
            policyBasis = "Routed to City Water Board / Executive Engineer (Distribution) for pipe leakage or contamination."
        ),
        CivicCategory.ELECTRICITY to CpgramsRoute(
            category = CivicCategory.ELECTRICITY,
            departmentName = "City Streetlight & Power Distribution DISCOM",
            departmentCode = "MOP-SL-04",
            cpgramsMinistry = "Ministry of Power / State Electricity Discom",
            citationTitle = "Electricity (Rights of Consumers) Rules — Service SLA",
            citationUrl = "https://powermin.gov.in",
            slaHours = 24,
            policyBasis = "Routed to Municipal Electrical Cell & DISCOM Nodal Officer for streetlight outage or exposed wiring."
        ),
        CivicCategory.DRAINAGE to CpgramsRoute(
            category = CivicCategory.DRAINAGE,
            departmentName = "Stormwater Drains & Sewerage Engineering",
            departmentCode = "ULB-DRN-05",
            cpgramsMinistry = "Ministry of Housing & Urban Affairs (ULB Drainage)",
            citationTitle = "CPHEEO Sewerage & Stormwater Drain Maintenance Manual",
            citationUrl = "https://mohua.gov.in",
            slaHours = 48,
            policyBasis = "Routed to Municipal Sewerage & Drainage Division to prevent waterlogging and vector-borne hazards."
        ),
        CivicCategory.PUBLIC_SAFETY to CpgramsRoute(
            category = CivicCategory.PUBLIC_SAFETY,
            departmentName = "Traffic Engineering & Civic Safety Cell",
            departmentCode = "MHA-SAF-06",
            cpgramsMinistry = "Municipal Safety & State Police Traffic Cell",
            citationTitle = "CPGRAMS Public Safety & Civic Hazard Escalation",
            citationUrl = "https://pgportal.gov.in",
            slaHours = 12,
            policyBasis = "Prioritized under high-urgency civic hazard protocol; for active emergencies dial 112."
        ),
        CivicCategory.OTHER to CpgramsRoute(
            category = CivicCategory.OTHER,
            departmentName = "Municipal General Grievance Cell",
            departmentCode = "CPG-GEN-07",
            cpgramsMinistry = "Department of Administrative Reforms & Public Grievances (DARPG)",
            citationTitle = "CPGRAMS Citizen Grievance Redressal Portal",
            citationUrl = "https://pgportal.gov.in",
            slaHours = 120,
            policyBasis = "Routed to District Collector / Municipal Commissioner Public Grievance Nodal Officer."
        )
    )

    fun resolveRoute(category: CivicCategory): CpgramsRoute =
        routes[category] ?: routes.getValue(CivicCategory.OTHER)

    fun allRoutes(): List<CpgramsRoute> = routes.values.toList()
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val preferredLanguage: String = "English",
    val homeWard: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = buildMap {
        put("userId", uid)
        put("displayName", displayName.take(80).ifBlank { "Citizen" })
        put("email", email.take(150).ifBlank { "citizen@civiclens.local" })
        put("preferredLanguage", preferredLanguage.take(40).ifBlank { "English" })
        homeWard?.take(80)?.let { put("homeWard", it) }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class PrivateCivicReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val trackingId: String = "",
    val title: String = "",
    val description: String = "",
    val transcript: String? = null,
    val language: String = "English",
    val category: String = CivicCategory.OTHER.name,
    val severity: String = CivicSeverity.MEDIUM.name,
    val riskScore: Long = 50L,
    val confidence: Double = 0.8,
    val needsHumanReview: Boolean = false,
    val departmentName: String = "",
    val departmentCode: String = "",
    val cpgramsMinistry: String = "",
    val citationTitle: String = "",
    val citationUrl: String = "https://pgportal.gov.in",
    val wardName: String = "",
    val approxLocation: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val clusterId: String = "",
    val hasPhoto: Boolean = false,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val mediaAuthNote: String = "",
    val status: String = PrivateReportStatus.SUBMITTED.name,
    val userConsented: Boolean = false,
    val publicReportId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedCategory(): CivicCategory = CivicCategory.safeParse(category)
    fun parsedSeverity(): CivicSeverity = CivicSeverity.safeParse(severity)
    fun parsedStatus(): PrivateReportStatus = PrivateReportStatus.safeParse(status)

    fun toCreateMap(uid: String): Map<String, Any> = buildMap {
        put("userId", uid)
        put("trackingId", trackingId.take(32))
        put("title", title.take(160))
        put("description", description.take(2000))
        transcript?.take(2000)?.takeIf { it.isNotBlank() }?.let { put("transcript", it) }
        put("language", language.take(40))
        put("category", CivicCategory.safeParse(category).name)
        put("severity", CivicSeverity.safeParse(severity).name)
        put("riskScore", riskScore.coerceIn(0L, 100L))
        put("confidence", confidence.coerceIn(0.0, 1.0))
        put("needsHumanReview", needsHumanReview)
        put("departmentName", departmentName.take(160))
        put("departmentCode", departmentCode.take(40))
        put("cpgramsMinistry", cpgramsMinistry.take(160))
        put("citationTitle", citationTitle.take(200))
        put("citationUrl", citationUrl.take(300))
        put("wardName", wardName.take(100))
        put("approxLocation", approxLocation.take(200))
        latitude?.coerceIn(-90.0, 90.0)?.let { put("latitude", it) }
        longitude?.coerceIn(-180.0, 180.0)?.let { put("longitude", it) }
        put("clusterId", clusterId.take(128))
        put("hasPhoto", hasPhoto)
        put("hasVideo", hasVideo)
        put("hasAudio", hasAudio)
        put("mediaAuthNote", mediaAuthNote.take(500))
        put("status", PrivateReportStatus.safeParse(status).name)
        put("userConsented", userConsented)
        publicReportId?.take(128)?.takeIf { it.isNotBlank() }?.let { put("publicReportId", it) }
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@Keep
@IgnoreExtraProperties
data class PublicCivicReport(
    @DocumentId val id: String = "",
    val creatorUid: String = "",
    val visibility: String = "PUBLIC_ANONYMIZED",
    val trackingCode: String = "",
    val title: String = "",
    val category: String = CivicCategory.OTHER.name,
    val severity: String = CivicSeverity.MEDIUM.name,
    val riskScore: Long = 50L,
    val confidence: Double = 0.8,
    val needsHumanReview: Boolean = false,
    val departmentName: String = "",
    val departmentCode: String = "",
    val citationUrl: String = "https://pgportal.gov.in",
    val wardName: String = "",
    val geoCellLat: Double = 12.97,
    val geoCellLng: Double = 77.59,
    val clusterKey: String = "",
    val reportCount: Long = 1L,
    val slaHours: Long = 72L,
    val status: String = PublicReportStatus.OPEN.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedCategory(): CivicCategory = CivicCategory.safeParse(category)
    fun parsedSeverity(): CivicSeverity = CivicSeverity.safeParse(severity)
    fun parsedStatus(): PublicReportStatus = PublicReportStatus.safeParse(status)

    fun toCreateMap(uid: String): Map<String, Any> =
        mapOf(
            "creatorUid" to uid,
            "visibility" to "PUBLIC_ANONYMIZED",
            "trackingCode" to trackingCode.take(32),
            "title" to title.take(160),
            "category" to CivicCategory.safeParse(category).name,
            "severity" to CivicSeverity.safeParse(severity).name,
            "riskScore" to riskScore.coerceIn(0L, 100L),
            "confidence" to confidence.coerceIn(0.0, 1.0),
            "needsHumanReview" to needsHumanReview,
            "departmentName" to departmentName.take(160),
            "departmentCode" to departmentCode.take(40),
            "citationUrl" to citationUrl.take(300),
            "wardName" to wardName.take(100),
            "geoCellLat" to geoCellLat.coerceIn(-90.0, 90.0),
            "geoCellLng" to geoCellLng.coerceIn(-180.0, 180.0),
            "clusterKey" to clusterKey.take(120),
            "reportCount" to 1L,
            "slaHours" to slaHours.coerceIn(1L, 720L),
            "status" to PublicReportStatus.safeParse(status).name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
}

enum class AiPipelineStep(val title: String, val subtitle: String) {
    MULTIMODAL_INGEST("Speech & Vision Model", "Extracting audio transcript & visual cues"),
    LLM_PLANNER_RETRIEVAL("LLM Planner & CPGRAMS Retrieval", "Matching official department & citations"),
    RISK_SCORER("Risk Scorer & Confidence Gate", "Computing hazard score & human-review flag"),
    DUPLICATE_CLUSTERING("Duplicate Clustering", "Checking nearby open reports within 500m")
}

data class AiAnalysisDraft(
    val trackingId: String,
    val title: String,
    val description: String,
    val transcript: String,
    val language: String,
    val category: CivicCategory,
    val severity: CivicSeverity,
    val riskScore: Int,
    val confidence: Double,
    val needsHumanReview: Boolean,
    val route: CpgramsRoute,
    val wardName: String,
    val approxLocation: String,
    val latitude: Double?,
    val longitude: Double?,
    val clusterKey: String,
    val mediaAuthNote: String,
    val matchedDuplicates: List<PublicCivicReport>
)

object GeoClusterUtils {
    fun buildClusterKey(category: CivicCategory, wardName: String, lat: Double?, lng: Double?): String {
        val cleanWard = wardName.uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "WARD_GENERAL" }
        val cellLat = ((lat ?: 12.9716) * 200.0).roundToInt()
        val cellLng = ((lng ?: 77.5946) * 200.0).roundToInt()
        return "${category.name}_${cleanWard}_${cellLat}_${cellLng}".take(110)
    }

    fun coarsenCoord(value: Double?, fallback: Double): Double {
        val v = value ?: fallback
        return (v * 100.0).roundToInt() / 100.0
    }

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun findPotentialDuplicates(
        category: CivicCategory,
        wardName: String,
        lat: Double?,
        lng: Double?,
        title: String,
        existingPublicReports: List<PublicCivicReport>
    ): List<PublicCivicReport> {
        val targetWard = wardName.trim().lowercase()
        val titleTokens = title.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length >= 4 }
            .toSet()

        return existingPublicReports
            .filter { it.parsedStatus() != PublicReportStatus.RESOLVED }
            .filter { report ->
                val sameCategory = report.parsedCategory() == category
                val reportWard = report.wardName.trim().lowercase()
                val sameWard = targetWard.isNotBlank() &&
                    (reportWard.contains(targetWard) || targetWard.contains(reportWard))
                val nearByGeo = if (lat != null && lng != null) {
                    haversineMeters(lat, lng, report.geoCellLat, report.geoCellLng) <= 850.0
                } else {
                    false
                }
                val reportTokens = report.title.lowercase().split(Regex("\\W+")).filter { it.length >= 4 }.toSet()
                val keywordOverlap = titleTokens.intersect(reportTokens).isNotEmpty()

                sameCategory && (sameWard || nearByGeo || keywordOverlap)
            }
            .sortedByDescending { it.reportCount }
            .take(3)
    }
}
