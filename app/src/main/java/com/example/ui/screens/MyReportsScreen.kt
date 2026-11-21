package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CivicLanguage
import com.example.domain.PrivateCivicReport
import com.example.domain.PrivateReportStatus
import com.example.domain.UiState
import com.example.media.CivicTtsHelper
import com.example.ui.components.ConfidencePill
import com.example.ui.components.CpgramsPackageActions
import com.example.ui.components.GuardrailNoticeCard
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusPill

@Composable
fun MyReportsScreen(
    state: UiState<List<PrivateCivicReport>>,
    preferredLanguage: CivicLanguage,
    onAdvanceStatus: (PrivateCivicReport, PrivateReportStatus) -> Unit,
    onDeleteReport: (String) -> Unit,
    onNavigateToReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsHelper = remember { CivicTtsHelper(context) }
    var statusFilter by remember { mutableStateOf<PrivateReportStatus?>(null) }

    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        val isWideGrid = maxWidth >= 760.dp

        when (state) {
            is UiState.Loading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    GuardrailNoticeCard(
                        title = "Could Not Load Your Reports",
                        message = state.message,
                        isAlert = true
                    )
                }
            }

            is UiState.Success -> {
                val reports = state.data
                val filtered = remember(reports, statusFilter) {
                    if (statusFilter == null) reports else reports.filter { it.parsedStatus() == statusFilter }
                }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = if (isWideGrid) 1240.dp else 820.dp)
                        .fillMaxSize()
                        .padding(horizontal = if (isWideGrid) 20.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = statusFilter == null,
                                onClick = { statusFilter = null },
                                label = { Text("All (${reports.size})") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                            listOf(
                                PrivateReportStatus.SUBMITTED,
                                PrivateReportStatus.IN_PROGRESS,
                                PrivateReportStatus.RESOLVED
                            ).forEach { st ->
                                FilterChip(
                                    selected = statusFilter == st,
                                    onClick = { statusFilter = st },
                                    label = { Text(st.label) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    coil.compose.AsyncImage(
                                        model = R.drawable.img_empty_reports,
                                        contentDescription = "No filed citizen complaints yet",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                    )
                                    Text(
                                        text = "No Tracked Complaints Yet",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Your consented grievances, CPGRAMS routing packages, and tracking IDs are securely stored here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = onNavigateToReport,
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("Create New Report")
                                    }
                                }
                            }
                        }
                    } else if (isWideGrid) {
                        val rows = filtered.chunked(2)
                        items(rows, key = { pair -> pair.joinToString("_") { it.id } }) { pair ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                pair.forEach { report ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        PrivateReportCard(
                                            report = report,
                                            onSpeak = {
                                                val msg = "Tracking ${report.trackingId}, status ${report.parsedStatus().label}. ${report.title}. Routed to ${report.departmentName}."
                                                ttsHelper.speak(msg, preferredLanguage)
                                            },
                                            onAdvanceStatus = { next -> onAdvanceStatus(report, next) },
                                            onDelete = { onDeleteReport(report.id) }
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { report ->
                            PrivateReportCard(
                                report = report,
                                onSpeak = {
                                    val msg = "Tracking ${report.trackingId}, status ${report.parsedStatus().label}. ${report.title}. Routed to ${report.departmentName}."
                                    ttsHelper.speak(msg, preferredLanguage)
                                },
                                onAdvanceStatus = { next -> onAdvanceStatus(report, next) },
                                onDelete = { onDeleteReport(report.id) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateReportCard(
    report: PrivateCivicReport,
    onSpeak: () -> Unit,
    onAdvanceStatus: (PrivateReportStatus) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val status = report.parsedStatus()

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("private_report_card_${report.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${report.trackingId} • ${report.language}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                StatusPill(
                    label = status.label,
                    isResolved = status == PrivateReportStatus.RESOLVED,
                    isInProgress = status == PrivateReportStatus.IN_PROGRESS
                )
            }

            Text(
                text = report.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                SeverityBadge(report.parsedSeverity())
                ConfidencePill(
                    confidence = report.confidence,
                    needsHumanReview = report.needsHumanReview
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${report.wardName} • ${report.departmentCode}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!report.transcript.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Voice Transcript: ${report.transcript}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { CpgramsPackageActions.openExternalUrl(context, report.citationUrl) }
            ) {
                Text(
                    text = "${report.departmentName} (${report.citationUrl})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open official citation",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val pkg = CpgramsPackageActions.buildPackageTextFromReport(report)
                        CpgramsPackageActions.sharePackage(
                            context = context,
                            subject = "CivicLens ${report.trackingId}: ${report.title}",
                            body = pkg
                        )
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export")
                }

                if (status != PrivateReportStatus.RESOLVED) {
                    val nextStatus = if (status == PrivateReportStatus.SUBMITTED || status == PrivateReportStatus.DRAFTED) {
                        PrivateReportStatus.IN_PROGRESS
                    } else {
                        PrivateReportStatus.RESOLVED
                    }
                    OutlinedButton(
                        onClick = { onAdvanceStatus(nextStatus) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            if (nextStatus == PrivateReportStatus.IN_PROGRESS) "Mark In Progress" else "Mark Resolved"
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read report status aloud",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete private report",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
