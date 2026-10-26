package com.example

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AutoAcceptHomeScreen()
            }
        }
    }
}

/**
 * Checks whether AutoAcceptService is enabled in Android Accessibility Settings.
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    if (enabledServices != null) {
        for (service in enabledServices) {
            val sInfo = service.resolveInfo?.serviceInfo
            if (sInfo != null &&
                sInfo.packageName == context.packageName &&
                sInfo.name == AutoAcceptService::class.java.name
            ) {
                return true
            }
        }
    }

    try {
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(settingValue)
        val expected = ComponentName(context, AutoAcceptService::class.java)

        while (splitter.hasNext()) {
            val componentStr = splitter.next()
            val component = ComponentName.unflattenFromString(componentStr)
            if (component != null && component == expected) {
                return true
            }
        }
    } catch (_: Exception) {
        // Ignored
    }

    return false
}

fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAcceptHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settingsManager = remember { SettingsManager.getInstance(context) }
    val savedSettings by settingsManager.settingsFlow.collectAsStateWithLifecycle()

    var isServiceEnabledInSettings by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }

    val isServiceConnected by AutoAcceptService.isServiceActive.collectAsStateWithLifecycle()
    val detections by AutoAcceptService.recentDetections.collectAsStateWithLifecycle()

    // Form inputs state initialized from saved preferences
    var isMasterRunning by remember { mutableStateOf(savedSettings.isMasterEnabled) }
    var rapidoToggle by remember { mutableStateOf(savedSettings.isRapidoEnabled) }
    var uberToggle by remember { mutableStateOf(savedSettings.isUberEnabled) }
    var olaToggle by remember { mutableStateOf(savedSettings.isOlaEnabled) }
    var minFareText by remember { mutableStateOf(savedSettings.minFareAmount.toString()) }
    var maxDistanceText by remember { mutableStateOf(savedSettings.maxPickupDistance.toString()) }

    var fareInputError by remember { mutableStateOf(false) }
    var distanceInputError by remember { mutableStateOf(false) }

    var simulatedClickFeedback by remember { mutableStateOf<String?>(null) }
    var simulationCounter by remember { mutableIntStateOf(0) }

    // Synchronize form when external preference changes occur
    LaunchedEffect(savedSettings) {
        isMasterRunning = savedSettings.isMasterEnabled
        rapidoToggle = savedSettings.isRapidoEnabled
        uberToggle = savedSettings.isUberEnabled
        olaToggle = savedSettings.isOlaEnabled
        minFareText = savedSettings.minFareAmount.toString()
        maxDistanceText = savedSettings.maxPickupDistance.toString()
    }

    // Refresh accessibility service status whenever the user returns to this screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabledInSettings = isAccessibilityServiceEnabled(context)
                if (!isServiceEnabledInSettings && isMasterRunning) {
                    isMasterRunning = false
                    settingsManager.isMasterEnabled = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isServiceReady = isServiceEnabledInSettings || isServiceConnected

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Part 2: Dashboard & Filter Preferences",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isServiceEnabledInSettings = isAccessibilityServiceEnabled(context)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Status refreshed.")
                            }
                        },
                        modifier = Modifier.testTag("refresh_status_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.check_status_btn)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                StatusCard(
                    isActive = isServiceReady,
                    onOpenSettings = { openAccessibilitySettings(context) }
                )
            }

            // Master Start / Stop Control Section
            item {
                MasterControlCard(
                    isMasterRunning = isMasterRunning,
                    isServiceReady = isServiceReady,
                    onToggleMaster = {
                        if (!isServiceReady) {
                            openAccessibilitySettings(context)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Enable Accessibility Service first.")
                            }
                        } else {
                            val newMasterState = !isMasterRunning
                            isMasterRunning = newMasterState
                            settingsManager.isMasterEnabled = newMasterState
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (newMasterState) "Automation started!" else "Automation stopped."
                                )
                            }
                        }
                    }
                )
            }

            // Filter Configuration Form Card
            item {
                FilterConfigurationCard(
                    rapidoEnabled = rapidoToggle,
                    onRapidoChange = { rapidoToggle = it },
                    uberEnabled = uberToggle,
                    onUberChange = { uberToggle = it },
                    olaEnabled = olaToggle,
                    onOlaChange = { olaToggle = it },
                    minFareText = minFareText,
                    onMinFareChange = {
                        minFareText = it
                        fareInputError = false
                    },
                    fareError = fareInputError,
                    maxDistanceText = maxDistanceText,
                    onMaxDistanceChange = {
                        maxDistanceText = it
                        distanceInputError = false
                    },
                    distanceError = distanceInputError,
                    onSaveSettings = {
                        // Validate inputs
                        val parsedFare = minFareText.trim().toIntOrNull()
                        val parsedDistance = maxDistanceText.trim().toFloatOrNull()

                        var hasError = false
                        if (parsedFare == null || parsedFare < 0) {
                            fareInputError = true
                            hasError = true
                        }
                        if (parsedDistance == null || parsedDistance <= 0f) {
                            distanceInputError = true
                            hasError = true
                        }

                        if (hasError) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.invalid_input_msg)
                                )
                            }
                        } else {
                            settingsManager.saveSettings(
                                isMasterEnabled = isMasterRunning,
                                isRapidoEnabled = rapidoToggle,
                                isUberEnabled = uberToggle,
                                isOlaEnabled = olaToggle,
                                minFareAmount = parsedFare!!,
                                maxPickupDistance = parsedDistance!!
                            )
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.settings_saved_msg)
                                )
                            }
                        }
                    }
                )
            }

            // Test Playground to verify screen scanner
            item {
                TestPlaygroundCard(
                    simulationCounter = simulationCounter,
                    simulatedClickFeedback = simulatedClickFeedback,
                    onTriggerSimulatedTarget = { label ->
                        simulationCounter++
                        simulatedClickFeedback = "Simulated '$label' triggered ($simulationCounter)"
                    }
                )
            }

            item {
                LiveDetectionFeedHeader(
                    detectionsCount = detections.size,
                    onClear = { AutoAcceptService.clearDetections() }
                )
            }

            if (detections.isEmpty()) {
                item {
                    EmptyDetectionCard(isServiceActive = isServiceReady)
                }
            } else {
                items(detections, key = { it.id }) { item ->
                    DetectionEventCard(item = item)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MasterControlCard(
    isMasterRunning: Boolean,
    isServiceReady: Boolean,
    onToggleMaster: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_control_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMasterRunning && isServiceReady) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isMasterRunning && isServiceReady) {
                            stringResource(R.string.master_active_title)
                        } else {
                            stringResource(R.string.master_inactive_title)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isMasterRunning && isServiceReady) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = if (isMasterRunning && isServiceReady) {
                            "Actively monitoring active platforms & thresholds"
                        } else {
                            "Automation paused. Tap start when ready."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onToggleMaster,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("master_toggle_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMasterRunning && isServiceReady) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(
                    imageVector = if (isMasterRunning && isServiceReady) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMasterRunning && isServiceReady) {
                        stringResource(R.string.master_stop_btn)
                    } else {
                        stringResource(R.string.master_start_btn)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FilterConfigurationCard(
    rapidoEnabled: Boolean,
    onRapidoChange: (Boolean) -> Unit,
    uberEnabled: Boolean,
    onUberChange: (Boolean) -> Unit,
    olaEnabled: Boolean,
    onOlaChange: (Boolean) -> Unit,
    minFareText: String,
    onMinFareChange: (String) -> Unit,
    fareError: Boolean,
    maxDistanceText: String,
    onMaxDistanceChange: (String) -> Unit,
    distanceError: Boolean,
    onSaveSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("filter_configuration_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Preferences & Platform Filters",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Text(
                text = stringResource(R.string.platform_toggles_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Platform Toggles
            PlatformToggleRow(
                title = stringResource(R.string.platform_rapido),
                checked = rapidoEnabled,
                onCheckedChange = onRapidoChange,
                testTag = "rapido_toggle"
            )
            PlatformToggleRow(
                title = stringResource(R.string.platform_uber),
                checked = uberEnabled,
                onCheckedChange = onUberChange,
                testTag = "uber_toggle"
            )
            PlatformToggleRow(
                title = stringResource(R.string.platform_ola),
                checked = olaEnabled,
                onCheckedChange = onOlaChange,
                testTag = "ola_toggle"
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.filter_rules_title),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )

            // Minimum Fare Input
            OutlinedTextField(
                value = minFareText,
                onValueChange = onMinFareChange,
                label = { Text(stringResource(R.string.min_fare_label)) },
                placeholder = { Text(stringResource(R.string.min_fare_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("min_fare_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = fareError,
                supportingText = if (fareError) {
                    { Text("Enter a valid integer amount (e.g. 50)") }
                } else null,
                singleLine = true
            )

            // Maximum Distance Input
            OutlinedTextField(
                value = maxDistanceText,
                onValueChange = onMaxDistanceChange,
                label = { Text(stringResource(R.string.max_distance_label)) },
                placeholder = { Text(stringResource(R.string.max_distance_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("max_distance_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = distanceError,
                supportingText = if (distanceError) {
                    { Text("Enter a positive distance in km (e.g. 4.5)") }
                } else null,
                singleLine = true
            )

            Button(
                onClick = onSaveSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.save_settings_btn),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun PlatformToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun StatusCard(
    isActive: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = if (isActive) "Active" else "Inactive",
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onError
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) stringResource(R.string.status_active)
                        else stringResource(R.string.status_inactive),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isActive) "Accessibility permission granted and ready"
                        else "Requires permission to detect screen accept buttons",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

            if (!isActive) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_accessibility_settings_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.enable_service_btn))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestPlaygroundCard(
    simulationCounter: Int,
    simulatedClickFeedback: String?,
    onTriggerSimulatedTarget: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("test_playground_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.test_section_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Text(
                text = stringResource(R.string.test_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                text = "Simulated Order Card",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "New Ride Request #10${simulationCounter + 1}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "₹185.00",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Pickup: Central Station • Drop: MG Road (4.2 km)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onTriggerSimulatedTarget("Accept") },
                            modifier = Modifier.testTag("simulate_accept_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.simulate_accept_btn))
                        }

                        Button(
                            onClick = { onTriggerSimulatedTarget("Confirm") },
                            modifier = Modifier.testTag("simulate_confirm_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.simulate_confirm_btn))
                        }

                        OutlinedButton(
                            onClick = { onTriggerSimulatedTarget("Take Order") },
                            modifier = Modifier.testTag("simulate_take_order_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.simulate_take_btn))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = simulatedClickFeedback != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                simulatedClickFeedback?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun LiveDetectionFeedHeader(
    detectionsCount: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Live Target Detections",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "$detectionsCount detected nodes (mirrored to Logcat)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (detectionsCount > 0) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.testTag("clear_detections_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear detections"
                )
            }
        }
    }
}

@Composable
fun EmptyDetectionCard(isServiceActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isServiceActive) "Waiting for target text on screen..." else "Service disabled",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isServiceActive) "Tap 'Accept Order' above or open any delivery app. Target nodes will appear here and in Logcat [TAG: AutoAcceptService]."
                else "Turn on the Accessibility Service to begin scanning screen nodes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun DetectionEventCard(item: AutoAcceptService.DetectionEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detection_item_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Keyword: ${item.matchedKeyword}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    text = item.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Matched Text: \"${item.nodeText}\"",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Text(
                text = "App: ${item.packageName} • ViewId: ${item.viewId ?: "n/a"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (item.isAccepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (item.isAccepted) "QUALIFIED" else "REJECTED",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (item.isAccepted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.fareAmount != null) {
                    Text(
                        text = "Fare: ₹${item.fareAmount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (item.distanceKm != null) {
                    Text(
                        text = "• Dist: ${item.distanceKm} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (item.clickExecuted) {
                    Text(
                        text = "• Click Dispatched",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (item.evaluationReason.isNotEmpty()) {
                Text(
                    text = "Decision: ${item.evaluationReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isAccepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }

            Text(
                text = "Class: ${item.nodeClassName} • Clickable: ${item.isClickable} • Bounds: [${item.bounds.left},${item.bounds.top} - ${item.bounds.right},${item.bounds.bottom}]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp
            )
        }
    }
}
