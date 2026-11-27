package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.roboxr.ai.configureAppCheck
import com.example.roboxr.ui.RoboXRViewModel
import com.example.roboxr.ui.StudioTab
import com.example.roboxr.ui.components.GuidedDemoBanner
import com.example.roboxr.ui.screens.AuditLogScreen
import com.example.roboxr.ui.screens.ExportProgramDialog
import com.example.roboxr.ui.screens.JamRecoveryScreen
import com.example.roboxr.ui.screens.TeachInScreen
import com.example.roboxr.ui.screens.TwinInspectorScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import com.example.roboxr.xr.XrealAdditiveOpticalHud
import com.example.roboxr.xr.XrealDiagnosticsDialog
import com.example.roboxr.xr.XrealHardwareManager
import com.example.roboxr.xr.XrealHudStatusStrip
import com.example.roboxr.xr.XrealOpticalPresentation
import com.example.roboxr.xr.XrealSpatialHost
import com.example.ui.theme.RoboXRTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val roboXRViewModel: RoboXRViewModel by viewModels()
    private lateinit var xrealHardwareManager: XrealHardwareManager
    private var opticalPresentation: XrealOpticalPresentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModel creation.
        configureAppCheck(this, intent)

        // STEP 2: Initialize XREAL USB-C HID / Optical Presentation / 3DoF Hardware Manager.
        xrealHardwareManager = XrealHardwareManager(this)

        // STEP 3: Full edge-to-edge industrial XR HUD rendering.
        enableEdgeToEdge()

        setContent {
            RoboXRTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RoboXRStudioApp(
                        viewModel = roboXRViewModel,
                        hardwareManager = xrealHardwareManager
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        xrealHardwareManager.start { externalDisplay ->
            updateOpticalPresentation(externalDisplay)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-scan when Android delivers USB_DEVICE_ATTACHED for XREAL glasses
        xrealHardwareManager.rescanAllHardware()
    }

    override fun onStop() {
        super.onStop()
        dismissOpticalPresentation()
        xrealHardwareManager.stop()
    }

    private fun updateOpticalPresentation(display: Display?) {
        runOnUiThread {
            if (display == null) {
                dismissOpticalPresentation()
                return@runOnUiThread
            }
            if (opticalPresentation?.display?.displayId == display.displayId &&
                opticalPresentation?.isShowing == true
            ) {
                return@runOnUiThread
            }
            dismissOpticalPresentation()
            try {
                val presentation = XrealOpticalPresentation(
                    hostActivity = this,
                    display = display,
                    uiStateFlow = roboXRViewModel.uiState,
                    hardwareStateFlow = xrealHardwareManager.state
                )
                presentation.show()
                opticalPresentation = presentation
                xrealHardwareManager.setOpticalPresentationActive(true)
                Log.i(TAG, "XREAL Optical HUD projected on external display ${display.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open XREAL Presentation display", e)
                xrealHardwareManager.setOpticalPresentationActive(false)
            }
        }
    }

    private fun dismissOpticalPresentation() {
        runCatching { opticalPresentation?.dismiss() }
        opticalPresentation = null
        if (::xrealHardwareManager.isInitialized) {
            xrealHardwareManager.setOpticalPresentationActive(false)
        }
    }
}

private data class NavItemSpec(
    val tab: StudioTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val studioNavItems = listOf(
    NavItemSpec(
        tab = StudioTab.TEACH_IN,
        labelRes = R.string.nav_teach_in,
        selectedIcon = Icons.Filled.PrecisionManufacturing,
        unselectedIcon = Icons.Outlined.PrecisionManufacturing,
        testTag = "nav_tab_teach_in"
    ),
    NavItemSpec(
        tab = StudioTab.JAM_RECOVERY,
        labelRes = R.string.nav_jam_recovery,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_tab_jam_recovery"
    ),
    NavItemSpec(
        tab = StudioTab.DIGITAL_TWIN,
        labelRes = R.string.nav_digital_twin,
        selectedIcon = Icons.Filled.ViewInAr,
        unselectedIcon = Icons.Outlined.ViewInAr,
        testTag = "nav_tab_digital_twin"
    ),
    NavItemSpec(
        tab = StudioTab.AUDIT_LOG,
        labelRes = R.string.nav_audit_log,
        selectedIcon = Icons.Filled.Fingerprint,
        unselectedIcon = Icons.Outlined.Fingerprint,
        testTag = "nav_tab_audit_log"
    )
)

@Composable
fun RoboXRStudioApp(
    viewModel: RoboXRViewModel,
    hardwareManager: XrealHardwareManager
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val auditRecords by viewModel.auditRecords.collectAsStateWithLifecycle()
    val hardwareState by hardwareManager.state.collectAsStateWithLifecycle()
    var showXrealDiagnostics by remember { mutableStateOf(false) }

    BackHandler(enabled = state.activeTab != StudioTab.TEACH_IN) {
        viewModel.selectTab(StudioTab.TEACH_IN)
    }

    // Wrap the entire studio inside the Jetpack XR SDK Spatial Host (Subspace + SpatialPanels on Android XR / XREAL Aura)
    XrealSpatialHost(
        hardwareManager = hardwareManager,
        uiState = state,
        onAutoRemediate = viewModel::autoRemediatePlan,
        onToggleSim = viewModel::toggleSimulation,
        onResetSim = viewModel::resetSimulation,
        onScrubSim = viewModel::scrubSimulation
    ) { onToggleSpaceMode ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            studioNavItems.forEach { item ->
                                val selected = state.activeTab == item.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    modifier = Modifier.testTag(item.testTag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(item.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
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
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            studioNavItems.forEach { item ->
                                val selected = state.activeTab == item.tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    modifier = Modifier.testTag(item.testTag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(item.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                        ) {
                            // Live XREAL SDK & Android XR Hardware Telemetry Strip
                            XrealHudStatusStrip(
                                hardwareState = hardwareState,
                                isDemoRunning = state.demoActive,
                                onOpenDiagnostics = { showXrealDiagnostics = true },
                                onStartDemo = viewModel::startGuidedDemo,
                                onToggleSpaceMode = onToggleSpaceMode
                            )

                            // Live Interview / Judge Guided Demo Showcase HUD
                            if (state.demoActive) {
                                GuidedDemoBanner(
                                    stepIndex = state.demoStepIndex,
                                    totalSteps = RoboXRViewModel.DEMO_TOTAL_STEPS,
                                    title = state.demoTitle,
                                    talkingPoint = state.demoTalkingPoint,
                                    autoPlay = state.demoAutoPlay,
                                    onPrev = viewModel::prevDemoStep,
                                    onNext = viewModel::nextDemoStep,
                                    onToggleAutoPlay = viewModel::toggleDemoAutoPlay,
                                    onStop = viewModel::stopGuidedDemo
                                )
                            }

                            // Non-blocking inline XREAL Additive Optical Lens HUD during DEMO Step 1
                            if (state.demoActive && state.demoOpenXrealConsole) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .testTag("demo_inline_xreal_hud"),
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary)
                                ) {
                                    XrealAdditiveOpticalHud(
                                        uiState = state,
                                        hardwareState = hardwareState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(175.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                    )
                                }
                            }

                            val screenModifier = Modifier
                                .weight(1f)
                                .fillMaxSize()

                            when (state.activeTab) {
                                StudioTab.TEACH_IN -> TeachInScreen(
                                    state = state,
                                    onSelectPlannerMode = viewModel::selectPlannerMode,
                                    onApplyPreset = viewModel::applyTeachInPreset,
                                    onInstructionChange = viewModel::updateInstruction,
                                    onPickChange = viewModel::updatePickAnchor,
                                    onPlaceChange = viewModel::updatePlaceAnchor,
                                    onSynthesizePlan = viewModel::synthesizeTeachInPlan,
                                    onAutoRemediate = viewModel::autoRemediatePlan,
                                    onToggleSim = viewModel::toggleSimulation,
                                    onResetSim = viewModel::resetSimulation,
                                    onScrubSim = viewModel::scrubSimulation,
                                    onApproveExport = { viewModel.approveAndGenerateCode(isJamSignOff = false) },
                                    modifier = screenModifier
                                )

                                StudioTab.JAM_RECOVERY -> JamRecoveryScreen(
                                    state = state,
                                    onSelectJam = viewModel::selectJamScenario,
                                    onCustomNoteChange = viewModel::updateCustomJamNote,
                                    onTriggerRecovery = viewModel::triggerJamRecoveryPlan,
                                    onToggleChecklistStep = viewModel::toggleHriChecklistStep,
                                    onAutoRemediate = viewModel::autoRemediatePlan,
                                    onToggleSim = viewModel::toggleSimulation,
                                    onResetSim = viewModel::resetSimulation,
                                    onScrubSim = viewModel::scrubSimulation,
                                    onCommitJamSignOff = { viewModel.approveAndGenerateCode(isJamSignOff = true) },
                                    modifier = screenModifier
                                )

                                StudioTab.DIGITAL_TWIN -> TwinInspectorScreen(
                                    state = state,
                                    onSelectWaypoint = viewModel::selectWaypoint,
                                    onUpdateWaypoint = viewModel::updateWaypoint,
                                    onAutoRemediate = viewModel::autoRemediatePlan,
                                    onToggleSim = viewModel::toggleSimulation,
                                    onResetSim = viewModel::resetSimulation,
                                    onScrubSim = viewModel::scrubSimulation,
                                    modifier = screenModifier
                                )

                                StudioTab.AUDIT_LOG -> AuditLogScreen(
                                    state = state,
                                    records = auditRecords,
                                    onOperatorIdChange = viewModel::updateOperatorId,
                                    onVerifyChain = viewModel::verifyAuditChainIntegrity,
                                    onClearHistory = viewModel::clearAuditHistory,
                                    modifier = screenModifier
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showXrealDiagnostics) {
            XrealDiagnosticsDialog(
                uiState = state,
                hardwareState = hardwareState,
                onRescanHardware = hardwareManager::rescanAllHardware,
                onRecenter3DoF = hardwareManager::recenter3DoF,
                onGazeLockPick = {
                    val anchor = hardwareManager.gazeToWorkcellAnchor(defaultZMm = 85.0)
                    viewModel.updatePickAnchor(anchor.xMm, anchor.yMm, anchor.zMm)
                    showXrealDiagnostics = false
                    viewModel.clearDemoConsoleTrigger()
                },
                onGazeLockPlace = {
                    val anchor = hardwareManager.gazeToWorkcellAnchor(defaultZMm = 115.0)
                    viewModel.updatePlaceAnchor(anchor.xMm, anchor.yMm, anchor.zMm)
                    showXrealDiagnostics = false
                    viewModel.clearDemoConsoleTrigger()
                },
                onToggleSpaceMode = onToggleSpaceMode,
                onDismiss = {
                    showXrealDiagnostics = false
                    viewModel.clearDemoConsoleTrigger()
                }
            )
        }
    }

    if (state.showExportDialog) {
        ExportProgramDialog(
            state = state,
            onSelectFormat = viewModel::selectExportFormat,
            onDismiss = viewModel::dismissExportDialog
        )
    }
}
