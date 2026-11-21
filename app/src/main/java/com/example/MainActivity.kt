package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.data.CivicAiRepository
import com.example.data.CivicFirestoreRepository
import com.example.firebase.configureAppCheck
import com.example.ui.CivicViewModel
import com.example.ui.screens.ArchitectureAndGuardrailsSheet
import com.example.ui.screens.AuthLandingScreen
import com.example.ui.screens.MyReportsScreen
import com.example.ui.screens.PublicDashboardScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.theme.CivicLensTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class CivicTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    REPORT("Report", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
    DASHBOARD("Dashboard", Icons.Filled.Public, Icons.Outlined.Public),
    MY_REPORTS("My Cases", Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            CivicLensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CivicLensApp()
                }
            }
        }
    }
}

@Composable
fun CivicLensApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            kotlinx.coroutines.delay(400L)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthLandingScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedCivicShell(
                currentUserId = uid,
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedCivicShell(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CivicViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CivicViewModel(
                    firestoreRepo = CivicFirestoreRepository(db),
                    aiRepo = CivicAiRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    var selectedTab by rememberSaveable { mutableStateOf(CivicTab.REPORT) }
    var showGuardrailsSheet by rememberSaveable { mutableStateOf(false) }

    val composerState by viewModel.composer.collectAsStateWithLifecycle()
    val publicReportsState by viewModel.publicReportsState.collectAsStateWithLifecycle()
    val myReportsState by viewModel.myReportsState.collectAsStateWithLifecycle()
    val policyState by viewModel.policyAssistant.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                CivicTab.REPORT -> "CivicLens • Report Issue"
                                CivicTab.DASHBOARD -> "CivicLens • Public Dashboard"
                                CivicTab.MY_REPORTS -> "CivicLens • My Cases"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showGuardrailsSheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_guardrails_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "AI Architecture and Guardrails",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign out"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        CivicTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        CivicTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                when (selectedTab) {
                    CivicTab.REPORT -> ReportScreen(
                        state = composerState,
                        onTextChange = viewModel::updateCitizenText,
                        onLanguageChange = viewModel::updateLanguage,
                        onWardLandmarkChange = viewModel::updateWardAndLandmark,
                        onSampleScenario = viewModel::applySampleScenario,
                        onPhotoSelected = { uri -> viewModel.attachPhoto(context, uri) },
                        onVideoSelected = { uri -> viewModel.attachVideo(context, uri) },
                        onClearMedia = viewModel::clearAttachedMedia,
                        onRecordingStarted = viewModel::setRecordingStarted,
                        onRecordingStopped = viewModel::setRecordingFinished,
                        onCancelRecording = viewModel::cancelRecording,
                        onClearAudio = viewModel::clearRecordedAudio,
                        onDetectLocation = { viewModel.detectCoarseLocation(context) },
                        onRunAnalysis = viewModel::runAiPipelineAnalysis,
                        onUpdateDraftEdits = viewModel::updateDraftEdits,
                        onSelectDuplicateCluster = viewModel::selectDuplicateCluster,
                        onConsentChange = viewModel::setConsentChecked,
                        onDiscardDraft = viewModel::discardDraft,
                        onSubmitConsented = viewModel::submitConsentedReport,
                        onClearSuccess = viewModel::clearSubmissionSuccess,
                        onViewMyReports = { selectedTab = CivicTab.MY_REPORTS }
                    )

                    CivicTab.DASHBOARD -> PublicDashboardScreen(
                        state = publicReportsState,
                        onNavigateToReport = { selectedTab = CivicTab.REPORT }
                    )

                    CivicTab.MY_REPORTS -> MyReportsScreen(
                        state = myReportsState,
                        preferredLanguage = composerState.preferredLanguage,
                        onAdvanceStatus = viewModel::advanceReportStatus,
                        onDeleteReport = viewModel::deleteReport,
                        onNavigateToReport = { selectedTab = CivicTab.REPORT }
                    )
                }
            }
        }
    }

    if (showGuardrailsSheet) {
        ArchitectureAndGuardrailsSheet(
            assistantState = policyState,
            onQuestionChange = viewModel::updatePolicyQuestion,
            onAskQuestion = viewModel::askPolicyAssistant,
            onDismiss = { showGuardrailsSheet = false }
        )
    }
}
