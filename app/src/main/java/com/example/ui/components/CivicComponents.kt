package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AiAnalysisDraft
import com.example.domain.CivicSeverity
import com.example.domain.PrivateCivicReport
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AlertCrimsonContainer
import com.example.ui.theme.AlertCrimsonOnContainer
import com.example.ui.theme.CivicTeal
import com.example.ui.theme.CivicTealContainer
import com.example.ui.theme.CivicTealOnContainer
import com.example.ui.theme.HazardAmber
import com.example.ui.theme.HazardAmberContainer
import com.example.ui.theme.HazardAmberOnContainer
import com.example.ui.theme.ResolvedEmerald
import com.example.ui.theme.ResolvedEmeraldContainer
import com.example.ui.theme.ResolvedEmeraldOnContainer

@Composable
fun SeverityBadge(severity: CivicSeverity, modifier: Modifier = Modifier) {
    val (bg, fg) = when (severity) {
        CivicSeverity.CRITICAL -> AlertCrimsonContainer to AlertCrimsonOnContainer
        CivicSeverity.HIGH -> HazardAmberContainer to HazardAmberOnContainer
        CivicSeverity.MEDIUM -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CivicSeverity.LOW -> CivicTealContainer to CivicTealOnContainer
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = severity.label.uppercase(),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    isResolved: Boolean,
    isInProgress: Boolean,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when {
        isResolved -> ResolvedEmeraldContainer to ResolvedEmeraldOnContainer
        isInProgress -> HazardAmberContainer to HazardAmberOnContainer
        else -> CivicTealContainer to CivicTealOnContainer
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ConfidencePill(
    confidence: Double,
    needsHumanReview: Boolean,
    modifier: Modifier = Modifier
) {
    val pct = (confidence * 100).toInt().coerceIn(0, 100)
    val bg = if (needsHumanReview) HazardAmberContainer else CivicTealContainer
    val fg = if (needsHumanReview) HazardAmberOnContainer else CivicTealOnContainer
    val icon = if (needsHumanReview) Icons.Default.Flag else Icons.Default.CheckCircle

    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (needsHumanReview) "AI $pct% • Review" else "AI $pct%",
                color = fg,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun GuardrailNoticeCard(
    title: String,
    message: String,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    val container = if (isAlert) HazardAmberContainer else CivicTealContainer
    val onContainer = if (isAlert) HazardAmberOnContainer else CivicTealOnContainer
    val border = if (isAlert) HazardAmber else CivicTeal
    val icon = if (isAlert) Icons.Default.Warning else Icons.Default.Shield

    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, border.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
        }
    }
}

fun severityColor(severity: CivicSeverity): Color = when (severity) {
    CivicSeverity.CRITICAL -> AlertCrimson
    CivicSeverity.HIGH -> HazardAmber
    CivicSeverity.MEDIUM -> CivicTeal
    CivicSeverity.LOW -> ResolvedEmerald
}

object CpgramsPackageActions {
    fun buildPackageText(
        trackingId: String,
        title: String,
        description: String,
        category: String,
        severity: String,
        riskScore: Int,
        departmentName: String,
        departmentCode: String,
        ministry: String,
        wardName: String,
        approxLocation: String,
        citationUrl: String,
        transcript: String?
    ): String = buildString {
        appendLine("CIVICLENS STRUCTURED GRIEVANCE PACKAGE (CPGRAMS-ALIGNED)")
        appendLine("Tracking Reference: $trackingId")
        appendLine("Category: $category | Severity: $severity (Risk Score: $riskScore/100)")
        appendLine("Suggested Department: $departmentName [$departmentCode]")
        appendLine("Nodal Ministry / Agency: $ministry")
        appendLine("Ward / Locality: $wardName")
        appendLine("Landmark / Area: $approxLocation")
        appendLine()
        appendLine("Subject: $title")
        appendLine("Details: $description")
        if (!transcript.isNullOrBlank()) {
            appendLine()
            appendLine("Audio Transcript: $transcript")
        }
        appendLine()
        appendLine("Official Portal Citation: $citationUrl")
        appendLine("Prepared via CivicLens (Explicit Citizen Review & Consent Confirmed)")
    }

    fun buildPackageTextFromDraft(
        draft: AiAnalysisDraft,
        editedTitle: String,
        editedDesc: String,
        editedWard: String,
        editedLandmark: String
    ): String = buildPackageText(
        trackingId = draft.trackingId,
        title = editedTitle,
        description = editedDesc,
        category = draft.category.label,
        severity = draft.severity.label,
        riskScore = draft.riskScore,
        departmentName = draft.route.departmentName,
        departmentCode = draft.route.departmentCode,
        ministry = draft.route.cpgramsMinistry,
        wardName = editedWard,
        approxLocation = editedLandmark,
        citationUrl = draft.route.citationUrl,
        transcript = draft.transcript
    )

    fun buildPackageTextFromReport(report: PrivateCivicReport): String = buildPackageText(
        trackingId = report.trackingId,
        title = report.title,
        description = report.description,
        category = report.parsedCategory().label,
        severity = report.parsedSeverity().label,
        riskScore = report.riskScore.toInt(),
        departmentName = report.departmentName,
        departmentCode = report.departmentCode,
        ministry = report.cpgramsMinistry,
        wardName = report.wardName,
        approxLocation = report.approxLocation,
        citationUrl = report.citationUrl,
        transcript = report.transcript
    )

    fun sharePackage(context: Context, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Export CPGRAMS Complaint Package"))
        } catch (_: ActivityNotFoundException) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(subject, body))
            Toast.makeText(context, "Complaint package copied to clipboard.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExternalUrl(context: Context, url: String) {
        val fallbackUrl = context.getString(R.string.cpgrams_portal_url)
        val safeUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else fallbackUrl
        val intent = Intent(Intent.ACTION_VIEW, safeUrl.toUri())
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Open $safeUrl in your browser.", Toast.LENGTH_LONG).show()
        }
    }
}
