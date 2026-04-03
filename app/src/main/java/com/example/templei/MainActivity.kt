package com.example.templei

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.templei.device.DeviceCapabilityProbe
import com.example.templei.device.DeviceCapabilityRegistry
import com.example.templei.device.DeviceCapabilitySnapshot
import com.example.templei.device.PermissionState
import com.example.templei.device.StorageModel
import com.example.templei.feature.mainhub.MainHubIntroStore
import com.example.templei.feature.tutorial.TutorialProgress
import com.example.templei.feature.tutorial.TutorialScreen
import com.example.templei.feature.tutorial.TutorialStatus
import com.example.templei.feature.tutorial.TutorialStore
import com.example.templei.ui.navigation.TopNavigation

/**
 * Entry screen shell that routes to Screens 1-4 and observes host-device readiness.
 *
 * MainActivity remains a router/status console; each screen still owns its own behavior.
 */
class MainActivity : ComponentActivity() {
    private lateinit var introCard: LinearLayout
    private lateinit var introTitleText: TextView
    private lateinit var introBodyText: TextView
    private lateinit var introPrimaryButton: Button
    private lateinit var introSecondaryButton: Button
    private lateinit var deviceStatusText: TextView
    private lateinit var tutorialSummaryText: TextView
    private lateinit var startTutorialButton: Button
    private lateinit var redoTutorialButton: Button
    private lateinit var skipTutorialButton: Button
    private lateinit var tutorialStore: TutorialStore
    private lateinit var introStore: MainHubIntroStore

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshHubChrome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.mainRoot)
        val rootPaddingStart = root.paddingStart
        val rootPaddingTop = root.paddingTop
        val rootPaddingEnd = root.paddingEnd
        val rootPaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPaddingRelative(
                rootPaddingStart + systemBars.left,
                rootPaddingTop + systemBars.top,
                rootPaddingEnd + systemBars.right,
                rootPaddingBottom + systemBars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)

        introCard = findViewById(R.id.mainIntroCard)
        introTitleText = findViewById(R.id.mainIntroTitleText)
        introBodyText = findViewById(R.id.mainIntroBodyText)
        introPrimaryButton = findViewById(R.id.mainIntroPrimaryButton)
        introSecondaryButton = findViewById(R.id.mainIntroSecondaryButton)
        deviceStatusText = findViewById(R.id.deviceStatusText)
        tutorialSummaryText = findViewById(R.id.tutorialSummaryText)
        startTutorialButton = findViewById(R.id.startTutorialButton)
        redoTutorialButton = findViewById(R.id.redoTutorialButton)
        skipTutorialButton = findViewById(R.id.skipTutorialButton)
        tutorialStore = TutorialStore(this)
        introStore = MainHubIntroStore(this)

        findViewById<Button>(R.id.requestPermissionsButton).setOnClickListener {
            requestMissingPermissions()
        }
        introPrimaryButton.setOnClickListener {
            handleIntroPrimaryAction()
        }
        introSecondaryButton.setOnClickListener {
            introStore.dismissIntro()
            updateIntroCard(DeviceCapabilityProbe.snapshot(this), tutorialStore.loadProgress())
        }
        findViewById<Button>(R.id.supportProjectButton).setOnClickListener {
            openSupportPage()
        }
        startTutorialButton.setOnClickListener {
            val progress = tutorialStore.loadProgress()
            if (progress.status == TutorialStatus.IN_PROGRESS) {
                launchTutorialDestination(progress)
            } else {
                tutorialStore.startTutorial()
                launchTutorialDestination(tutorialStore.loadProgress())
            }
        }
        redoTutorialButton.setOnClickListener {
            tutorialStore.redoTutorial()
            refreshHubChrome()
            launchTutorialDestination(tutorialStore.loadProgress())
        }
        skipTutorialButton.setOnClickListener {
            tutorialStore.skipTutorial()
            refreshHubChrome()
            Toast.makeText(this, getString(R.string.mainTutorialSkippedToast), Toast.LENGTH_SHORT).show()
        }

        TopNavigation.bindMainMenuGrid(activity = this)

        refreshHubChrome()
    }

    override fun onResume() {
        super.onResume()
        refreshHubChrome()
    }

    private fun requestMissingPermissions(snapshot: DeviceCapabilitySnapshot = DeviceCapabilityProbe.snapshot(this)) {
        val permissionsToRequest = collectRequestableMissingPermissions(snapshot)

        if (permissionsToRequest.isEmpty()) {
            Toast.makeText(this, getString(R.string.permission_request_none_needed), Toast.LENGTH_SHORT).show()
            refreshHubChrome()
            return
        }

        permissionRequestLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun openSupportPage() {
        startActivity(Intent(this, SupportWebActivity::class.java))
    }

    private fun refreshHubChrome() {
        val snapshot = DeviceCapabilityProbe.snapshot(this)
        val tutorialProgress = tutorialStore.loadProgress()
        refreshDeviceStatus(snapshot)
        updateIntroCard(snapshot, tutorialProgress)
        updateTutorialCard(tutorialProgress)
    }

    private fun refreshDeviceStatus(snapshot: DeviceCapabilitySnapshot) {
        DeviceCapabilityRegistry.update(snapshot)
        deviceStatusText.text = formatSnapshot(snapshot)
    }

    private fun updateIntroCard(snapshot: DeviceCapabilitySnapshot, tutorialProgress: TutorialProgress) {
        val introState = introStore.loadState()
        val shouldShowIntro = !introState.introDismissed && tutorialProgress.status != TutorialStatus.COMPLETED
        introCard.isVisible = shouldShowIntro
        if (!shouldShowIntro) {
            return
        }

        val hasMissingPermissions = collectRequestableMissingPermissions(snapshot).isNotEmpty()
        val introTitleRes = when {
            !introState.setupStarted -> R.string.mainHubIntroTitleWelcome
            hasMissingPermissions -> R.string.mainHubIntroTitlePermissions
            tutorialProgress.status == TutorialStatus.IN_PROGRESS -> R.string.mainHubIntroTitleContinue
            else -> R.string.mainHubIntroTitleReady
        }
        val introBody = when {
            !introState.setupStarted -> getString(R.string.mainHubIntroBodyWelcome)
            hasMissingPermissions -> getString(R.string.mainHubIntroBodyPermissions)
            tutorialProgress.status == TutorialStatus.IN_PROGRESS -> getString(
                R.string.mainHubIntroBodyContinue,
                tutorialLabelFor(tutorialProgress.currentScreen),
            )
            else -> getString(R.string.mainHubIntroBodyReady)
        }
        val primaryButtonRes = when {
            !introState.setupStarted -> R.string.mainHubIntroStartSetup
            hasMissingPermissions -> R.string.mainHubIntroRequestPermissions
            tutorialProgress.status == TutorialStatus.IN_PROGRESS -> R.string.mainHubIntroContinueSetup
            else -> R.string.mainHubIntroOpenTutorial
        }

        introTitleText.text = getString(introTitleRes)
        introBodyText.text = introBody
        introPrimaryButton.text = getString(primaryButtonRes)
        introSecondaryButton.text = getString(
            if (introState.setupStarted) {
                R.string.mainHubIntroHide
            } else {
                R.string.mainHubIntroNotNow
            }
        )
    }

    private fun updateTutorialCard(progress: TutorialProgress) {
        tutorialSummaryText.text = when (progress.status) {
            TutorialStatus.NOT_STARTED -> getString(R.string.mainTutorialSummaryDefault)
            TutorialStatus.IN_PROGRESS -> getString(
                R.string.mainTutorialSummaryInProgress,
                tutorialLabelFor(progress.currentScreen),
            )
            TutorialStatus.SKIPPED -> getString(R.string.mainTutorialSummarySkipped)
            TutorialStatus.COMPLETED -> getString(R.string.mainTutorialSummaryCompleted)
        }
        startTutorialButton.text = getString(
            if (progress.status == TutorialStatus.IN_PROGRESS) {
                R.string.mainTutorialContinue
            } else {
                R.string.mainTutorialStart
            }
        )
    }

    private fun handleIntroPrimaryAction() {
        val snapshot = DeviceCapabilityProbe.snapshot(this)
        introStore.markSetupStarted()
        if (collectRequestableMissingPermissions(snapshot).isNotEmpty()) {
            requestMissingPermissions(snapshot)
            updateIntroCard(snapshot, tutorialStore.loadProgress())
            return
        }
        launchOrStartTutorial()
    }

    private fun launchOrStartTutorial() {
        val progress = tutorialStore.loadProgress()
        if (progress.status == TutorialStatus.IN_PROGRESS) {
            launchTutorialDestination(progress)
            return
        }
        tutorialStore.startTutorial()
        launchTutorialDestination(tutorialStore.loadProgress())
    }

    private fun launchTutorialDestination(progress: TutorialProgress) {
        val destination = when (progress.currentScreen) {
            TutorialScreen.SCREEN2 -> Screen2Activity::class.java
            TutorialScreen.SCREEN3 -> Screen3Activity::class.java
            TutorialScreen.SCREEN4 -> Screen4Activity::class.java
            TutorialScreen.SCREEN1 -> Screen1Activity::class.java
        }
        startActivity(Intent(this, destination))
    }

    private fun tutorialLabelFor(screen: TutorialScreen): String = when (screen) {
        TutorialScreen.SCREEN2 -> getString(R.string.button2text)
        TutorialScreen.SCREEN3 -> getString(R.string.button3text)
        TutorialScreen.SCREEN4 -> getString(R.string.button4text)
        TutorialScreen.SCREEN1 -> getString(R.string.button1text)
    }

    private fun formatSnapshot(snapshot: DeviceCapabilitySnapshot): String {
        val missingRequestable = collectRequestableMissingPermissions(snapshot)
            .joinToString(separator = ", ") { permissionLabel(it) }
            .ifEmpty { getString(R.string.permission_request_none_missing_label) }
        val cameraCaptureReady = snapshot.hasCamera &&
            snapshot.cameraPermission == PermissionState.GRANTED
        val audioRecorderReady = snapshot.hasMicrophone &&
            snapshot.microphonePermission == PermissionState.GRANTED

        return buildString {
            appendLine(getString(R.string.device_status_header, snapshot.sdkInt))
            appendLine(
                getString(
                    R.string.device_status_camera,
                    yesNo(snapshot.hasCamera),
                    permission(snapshot.cameraPermission),
                )
            )
            appendLine(
                getString(
                    R.string.device_status_microphone,
                    yesNo(snapshot.hasMicrophone),
                    permission(snapshot.microphonePermission),
                )
            )
            appendLine(
                getString(
                    R.string.device_status_files,
                    storageModel(snapshot.storageModel),
                    permission(snapshot.readExternalStoragePermission),
                    permission(snapshot.mediaAudioPermission),
                )
            )
            appendLine(getString(R.string.device_status_gate_recorder, yesNo(cameraCaptureReady)))
            appendLine(getString(R.string.device_status_gate_logger, yesNo(audioRecorderReady)))
            appendLine(getString(R.string.device_status_missing_permissions, missingRequestable))
        }
    }

    private fun collectRequestableMissingPermissions(snapshot: DeviceCapabilitySnapshot): List<String> {
        val requestables = mutableListOf<String>()

        if (snapshot.hasCamera && snapshot.cameraPermission == PermissionState.DENIED) {
            requestables += Manifest.permission.CAMERA
        }
        if (snapshot.hasMicrophone && snapshot.microphonePermission == PermissionState.DENIED) {
            requestables += Manifest.permission.RECORD_AUDIO
        }
        if (snapshot.readExternalStoragePermission == PermissionState.DENIED) {
            requestables += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (snapshot.mediaAudioPermission == PermissionState.DENIED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestables += Manifest.permission.READ_MEDIA_AUDIO
        }

        return requestables.distinct()
    }

    private fun permissionLabel(permission: String): String = when (permission) {
        Manifest.permission.CAMERA -> getString(R.string.permission_camera)
        Manifest.permission.RECORD_AUDIO -> getString(R.string.permission_microphone)
        Manifest.permission.READ_EXTERNAL_STORAGE -> getString(R.string.permission_read_external_storage)
        Manifest.permission.READ_MEDIA_AUDIO -> getString(R.string.permission_read_media_audio)
        else -> permission
    }

    private fun yesNo(value: Boolean): String = if (value) {
        getString(R.string.status_yes)
    } else {
        getString(R.string.status_no)
    }

    private fun permission(state: PermissionState): String = when (state) {
        PermissionState.GRANTED -> getString(R.string.permission_granted)
        PermissionState.DENIED -> getString(R.string.permission_denied)
        PermissionState.NOT_APPLICABLE -> getString(R.string.permission_not_applicable)
    }

    private fun storageModel(model: StorageModel): String = when (model) {
        StorageModel.LEGACY_EXTERNAL_STORAGE -> getString(R.string.storage_model_legacy)
        StorageModel.SCOPED_STORAGE -> getString(R.string.storage_model_scoped)
    }
}
