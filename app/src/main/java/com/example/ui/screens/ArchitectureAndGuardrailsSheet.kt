package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.CpgramsKnowledgeBase
import com.example.ui.PolicyAssistantState
import com.example.ui.components.CpgramsPackageActions
import com.example.ui.components.GuardrailNoticeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchitectureAndGuardrailsSheet(
    assistantState: PolicyAssistantState,
    onQuestionChange: (String) -> Unit,
    onAskQuestion: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Architecture & Guardrails",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close sheet")
                }
            }

            // Pipeline layers
            Text(
                text = "Multi-Stage CivicLens AI Pipeline",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PipelineRow("1. Speech-to-Text & Vision", "Gemini 3.8 Flash transcribes regional voice notes & inspects photo/video frames.")
                PipelineRow("2. LLM Planner & Retrieval", "Maps grievance to official CPGRAMS ministries, SLAs, and citations.")
                PipelineRow("3. Risk Scorer & Confidence", "Scores public safety hazard (0–100) and flags confidence <75% for human review.")
                PipelineRow("4. Duplicate Clusterer", "Deduplicates nearby open reports by category, ward, and geo-grid cell.")
                PipelineRow("5. Consent Agent Layer", "Requires explicit citizen review & consent before filing or exporting.")
            }

            HorizontalDivider()

            Text(
                text = "Enforced Safety & Privacy Guardrails",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            GuardrailNoticeCard(
                title = "5 Active Guardrails",
                message = "• No automatic government form submission without explicit consent.\n" +
                    "• Public dashboard strips all PII (names, emails, exact addresses).\n" +
                    "• Confidence <75% triggers mandatory human review badge.\n" +
                    "• Department routing displays official CPGRAMS / Ministry links.\n" +
                    "• Media checks report probabilistic evidence, never absolute deepfake claims.",
                isAlert = false
            )

            HorizontalDivider()

            // Streaming Gemini Policy Assistant
            Text(
                text = "Ask Gemini CPGRAMS & Policy Assistant",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = assistantState.question,
                onValueChange = onQuestionChange,
                label = { Text("Ask about grievance escalation, SLA, or departments") },
                trailingIcon = {
                    if (assistantState.isStreaming) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { onAskQuestion(null) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask policy assistant")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!assistantState.errorMessage.isNullOrBlank()) {
                Text(
                    text = assistantState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (assistantState.answerText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = assistantState.answerText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Official Routing Sources",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            CpgramsKnowledgeBase.allRoutes().forEach { route ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { CpgramsPackageActions.openExternalUrl(context, route.citationUrl) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${route.category.label} (${route.departmentCode})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "${route.citationTitle} • ${route.citationUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open citation",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PipelineRow(title: String, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
