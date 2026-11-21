package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CivicCategory
import com.example.domain.CivicSeverity
import com.example.domain.PublicCivicReport
import com.example.domain.PublicReportStatus
import com.example.domain.UiState
import com.example.ui.components.ConfidencePill
import com.example.ui.components.CpgramsPackageActions
import com.example.ui.components.GuardrailNoticeCard
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.severityColor
import com.example.ui.theme.CivicTeal
import com.example.ui.theme.HazardAmber
import com.example.ui.theme.ResolvedEmerald
import kotlin.math.abs

private enum class DashboardFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved"),
    HIGH_RISK("High Risk")
}

@Composable
fun PublicDashboardScreen(
    state: UiState<List<PublicCivicReport>>,
    onNavigateToReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(DashboardFilter.ALL) }
    var highlightedClusterId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        val isExpandedLayout = maxWidth >= 960.dp
        val isMediumLayout = maxWidth >= 640.dp

        when (state) {
            is UiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Syncing anonymized civic telemetry...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        title = "Public Dashboard Unavailable",
                        message = state.message,
                        isAlert = true
                    )
                }
            }

            is UiState.Success -> {
                val reports = state.data
                val filtered = remember(reports, selectedFilter) {
                    when (selectedFilter) {
                        DashboardFilter.ALL -> reports
                        DashboardFilter.OPEN -> reports.filter { it.parsedStatus() == PublicReportStatus.OPEN }
                        DashboardFilter.IN_PROGRESS -> reports.filter { it.parsedStatus() == PublicReportStatus.IN_PROGRESS }
                        DashboardFilter.RESOLVED -> reports.filter { it.parsedStatus() == PublicReportStatus.RESOLVED }
                        DashboardFilter.HIGH_RISK -> reports.filter {
                            it.riskScore >= 70L ||
                                it.parsedSeverity() == CivicSeverity.HIGH ||
                                it.parsedSeverity() == CivicSeverity.CRITICAL
                        }
                    }
                }

                val openCount = reports.count { it.parsedStatus() == PublicReportStatus.OPEN }
                val progressCount = reports.count { it.parsedStatus() == PublicReportStatus.IN_PROGRESS }
                val resolvedCount = reports.count { it.parsedStatus() == PublicReportStatus.RESOLVED }
                val totalClustered = reports.sumOf { it.reportCount }
                val avgSla = if (reports.isEmpty()) 0 else (reports.sumOf { it.slaHours } / reports.size)

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = if (isExpandedLayout) 1280.dp else 860.dp)
                        .fillMaxSize()
                        .padding(horizontal = if (isMediumLayout) 20.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        // Privacy Anonymization Banner
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PrivacyTip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Public Transparency View: All records are anonymized and aggregated by ward & geo-grid. Zero personal citizen PII is published.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // KPI Bento Row (6-across on desktop/laptop, 3x2 on phone/tablet)
                    item {
                        if (isExpandedLayout) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                KpiCard("Open", openCount.toString(), CivicTeal, Modifier.weight(1f))
                                KpiCard("In Progress", progressCount.toString(), HazardAmber, Modifier.weight(1f))
                                KpiCard("Resolved", resolvedCount.toString(), ResolvedEmerald, Modifier.weight(1f))
                                KpiCard(
                                    label = "Clustered Reports",
                                    value = totalClustered.toString(),
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiCard(
                                    label = "Hotspot Zones",
                                    value = reports.count { it.reportCount >= 2L }.toString(),
                                    accent = HazardAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiCard(
                                    label = "Avg Dept SLA",
                                    value = if (avgSla > 0) "${avgSla}h" else "—",
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                KpiCard("Open", openCount.toString(), CivicTeal, Modifier.weight(1f))
                                KpiCard("In Progress", progressCount.toString(), HazardAmber, Modifier.weight(1f))
                                KpiCard("Resolved", resolvedCount.toString(), ResolvedEmerald, Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                KpiCard(
                                    label = "Clustered Reports",
                                    value = totalClustered.toString(),
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiCard(
                                    label = "Hotspot Zones",
                                    value = reports.count { it.reportCount >= 2L }.toString(),
                                    accent = HazardAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                KpiCard(
                                    label = "Avg Dept SLA",
                                    value = if (avgSla > 0) "${avgSla}h" else "—",
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (reports.isEmpty()) {
                        item {
                            EmptyDashboardCard(onNavigateToReport = onNavigateToReport)
                        }
                    } else {
                        if (isExpandedLayout) {
                            item {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.weight(1.15f)) {
                                        HotspotClusterMapCard(
                                            reports = filtered.ifEmpty { reports },
                                            highlightedClusterId = highlightedClusterId
                                        )
                                    }
                                    Box(modifier = Modifier.weight(0.85f)) {
                                        CategoryDistributionCard(reports = reports)
                                    }
                                }
                            }
                        } else {
                            // Hotspot Cluster Canvas Map
                            item {
                                HotspotClusterMapCard(
                                    reports = filtered.ifEmpty { reports },
                                    highlightedClusterId = highlightedClusterId
                                )
                            }

                            // Category Breakdown Chart
                            item {
                                CategoryDistributionCard(reports = reports)
                            }
                        }

                        // Filter Chips
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                DashboardFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text(filter.label) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }
                        }

                        if (isMediumLayout) {
                            val rows = filtered.chunked(2)
                            items(rows, key = { pair -> pair.joinToString("_") { it.id } }) { pair ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    pair.forEach { report ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            PublicClusterCard(
                                                report = report,
                                                isHighlighted = highlightedClusterId == report.id,
                                                onClick = {
                                                    highlightedClusterId = if (highlightedClusterId == report.id) null else report.id
                                                }
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
                                PublicClusterCard(
                                    report = report,
                                    isHighlighted = highlightedClusterId == report.id,
                                    onClick = {
                                        highlightedClusterId = if (highlightedClusterId == report.id) null else report.id
                                    }
                                )
                            }
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
private fun KpiCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HotspotClusterMapCard(
    reports: List<PublicCivicReport>,
    highlightedClusterId: String?
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Anonymized Ward Hotspot Map",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${reports.size} cluster(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cols = 6
                    val rows = 4
                    for (i in 1 until cols) {
                        val x = size.width * (i.toFloat() / cols)
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }
                    for (j in 1 until rows) {
                        val y = size.height * (j.toFloat() / rows)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }

                    val minLat = reports.minOf { it.geoCellLat }
                    val maxLat = reports.maxOf { it.geoCellLat }
                    val minLng = reports.minOf { it.geoCellLng }
                    val maxLng = reports.maxOf { it.geoCellLng }
                    val latSpan = (maxLat - minLat).takeIf { it > 0.005 } ?: 0.05
                    val lngSpan = (maxLng - minLng).takeIf { it > 0.005 } ?: 0.05

                    reports.forEachIndexed { idx, item ->
                        val hashOffsetX = ((abs(item.clusterKey.hashCode()) % 70) / 100f) * 0.18f
                        val hashOffsetY = ((abs(item.id.hashCode()) % 70) / 100f) * 0.18f
                        val normX = if (maxLng - minLng > 0.005) {
                            ((item.geoCellLng - minLng) / lngSpan).toFloat().coerceIn(0.1f, 0.9f)
                        } else {
                            (0.18f + (idx % 4) * 0.2f + hashOffsetX).coerceIn(0.12f, 0.88f)
                        }
                        val normY = if (maxLat - minLat > 0.005) {
                            (1f - ((item.geoCellLat - minLat) / latSpan).toFloat()).coerceIn(0.14f, 0.86f)
                        } else {
                            (0.22f + (idx % 3) * 0.25f + hashOffsetY).coerceIn(0.14f, 0.86f)
                        }

                        val center = Offset(size.width * normX, size.height * normY)
                        val radius = (16f + item.reportCount.coerceAtMost(8L) * 6f)
                        val bubbleColor = severityColor(item.parsedSeverity())
                        val isSelected = item.id == highlightedClusterId

                        drawCircle(
                            color = bubbleColor.copy(alpha = 0.22f),
                            radius = radius * 1.45f,
                            center = center
                        )
                        drawCircle(
                            color = bubbleColor.copy(alpha = 0.85f),
                            radius = radius,
                            center = center
                        )
                        if (isSelected) {
                            drawCircle(
                                color = primaryColor,
                                radius = radius * 1.6f,
                                center = center,
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                }
            }

            Text(
                text = "Bubble size reflects duplicate reports clustered; color indicates risk severity. Tap any card below to highlight its zone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryDistributionCard(reports: List<PublicCivicReport>) {
    val grouped = remember(reports) {
        reports.groupBy { it.parsedCategory() }
            .mapValues { (_, list) -> list.sumOf { it.reportCount } }
            .entries
            .sortedByDescending { it.value }
            .take(5)
    }
    val maxTotal = grouped.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Clustered Reports by Department Category",
                style = MaterialTheme.typography.titleMedium
            )
            grouped.forEach { (category, count) ->
                val fraction = (count.toFloat() / maxTotal.toFloat()).coerceIn(0.08f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "$count report(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(8.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(CivicTeal)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicClusterCard(
    report: PublicCivicReport,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val status = report.parsedStatus()

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isHighlighted) 2.dp else 1.dp,
            color = if (isHighlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("public_cluster_card_${report.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${report.wardName} • ${report.trackingCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    label = status.label,
                    isResolved = status == PublicReportStatus.RESOLVED,
                    isInProgress = status == PublicReportStatus.IN_PROGRESS
                )
            }

            Text(
                text = report.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                SeverityBadge(report.parsedSeverity())
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${report.reportCount}x Clustered",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                ConfidencePill(
                    confidence = report.confidence,
                    needsHumanReview = report.needsHumanReview
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${report.departmentCode} • SLA ${report.slaHours}h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { CpgramsPackageActions.openExternalUrl(context, report.citationUrl) }
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Official Source",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open official citation",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboardCard(onNavigateToReport: () -> Unit) {
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
                contentDescription = "No civic hotspot reports yet",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Text(
                text = "No Public Hotspots Reported Yet",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Submit your first civic issue with voice, photo, or text. Once you review and consent, an anonymized cluster card will appear here in real time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onNavigateToReport,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Report Civic Issue")
            }
        }
    }
}
