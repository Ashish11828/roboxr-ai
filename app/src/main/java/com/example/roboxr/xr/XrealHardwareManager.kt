package com.example.roboxr.xr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.view.Display
import com.example.roboxr.model.SpatialAnchor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.round

private const val TAG = "XrealHardwareManager"

data class XrealHardwareState(
    val isUsbGlassesConnected: Boolean = false,
    val glassesModel: String = "Standby (Connect XREAL USB-C or Android XR)",
    val usbVendorIdHex: String? = null,
    val usbProductIdHex: String? = null,
    val usbInterfaceCount: Int = 0,
    val externalDisplayConnected: Boolean = false,
    val externalDisplayId: Int? = null,
    val externalDisplayName: String? = null,
    val externalDisplayResolution: String? = null,
    val externalDisplayRefreshHz: Float? = null,
    val isOpticalPresentationActive: Boolean = false,
    val isImuActive: Boolean = false,
    val imuSource: String = "Awaiting Sensor",
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val isSpatialUiEnabled: Boolean = false,
    val isContent3dEnabled: Boolean = false,
    val isPassthroughControlEnabled: Boolean = false,
    val displayBlendMode: String = "STANDARD_2D",
    val isXrSessionAvailable: Boolean = false
)

/**
 * Native Android Hardware Bridge for XREAL Smart Glasses (Air, Air 2, Air 2 Pro, Air 2 Ultra, One, Light)
 * and Android XR (Jetpack XR Runtime / Wired XR Glasses).
 *
 * Manages:
 * 1. USB Host HID detection for XREAL VID 0x3318 / 0x0486.
 * 2. Secondary DisplayPort Alt Mode optical display detection via DisplayManager.
 * 3. Live 3DoF orientation telemetry via Android Rotation Vector IMU with instant recenter.
 * 4. Jetpack XR spatial capability & DisplayBlendMode telemetry updates.
 */
class XrealHardwareManager(private val context: Context) : SensorEventListener {

    companion object {
        const val XREAL_VID_PRIMARY = 0x3318   // 13080 (XREAL / Nreal Air, Air 2, Ultra, One)
        const val XREAL_VID_LEGACY = 0x0486    // 1158 (Nreal / XREAL Light & auxiliary bridge)

        private val XREAL_PRODUCT_NAMES = mapOf(
            0x0424 to "XREAL Air",
            0x0428 to "XREAL Air 2",
            0x0432 to "XREAL Air 2 Pro",
            0x0426 to "XREAL Air 2 Ultra (6DoF)",
            0x0435 to "XREAL One",
            0x0436 to "XREAL One Pro",
            0x0573 to "XREAL Light"
        )
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _state = MutableStateFlow(XrealHardwareState())
    val state: StateFlow<XrealHardwareState> = _state.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var yawZeroOffset = 0f
    private var pitchZeroOffset = 0f
    private var rollZeroOffset = 0f
    private var lastRawYaw = 0f
    private var lastRawPitch = 0f
    private var lastRawRoll = 0f

    private var onPresentationDisplayChanged: ((Display?) -> Unit)? = null
    private var receiversRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    scanUsbXrealDevices()
                }
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = scanPresentationDisplays()
        override fun onDisplayRemoved(displayId: Int) = scanPresentationDisplays()
        override fun onDisplayChanged(displayId: Int) = scanPresentationDisplays()
    }

    fun start(onExternalDisplayChanged: (Display?) -> Unit) {
        this.onPresentationDisplayChanged = onExternalDisplayChanged

        if (!receiversRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(usbReceiver, filter)
            }
            displayManager?.registerDisplayListener(displayListener, null)
            receiversRegistered = true
        }

        startImuSensor()
        rescanAllHardware()
    }

    fun stop() {
        if (receiversRegistered) {
            runCatching { context.unregisterReceiver(usbReceiver) }
            runCatching { displayManager?.unregisterDisplayListener(displayListener) }
            receiversRegistered = false
        }
        sensorManager?.unregisterListener(this)
        onPresentationDisplayChanged = null
    }

    fun rescanAllHardware() {
        scanUsbXrealDevices()
        scanPresentationDisplays()
    }

    fun scanUsbXrealDevices() {
        val deviceList = runCatching { usbManager?.deviceList?.values.orEmpty() }
            .getOrDefault(emptyList())
        val xrealDevice: UsbDevice? = deviceList.firstOrNull { dev ->
            dev.vendorId == XREAL_VID_PRIMARY || dev.vendorId == XREAL_VID_LEGACY
        }

        if (xrealDevice != null) {
            val modelName = XREAL_PRODUCT_NAMES[xrealDevice.productId]
                ?: xrealDevice.productName?.takeIf { it.isNotBlank() }
                ?: "XREAL Smart Glasses (PID 0x${xrealDevice.productId.toString(16).uppercase()})"
            _state.update {
                it.copy(
                    isUsbGlassesConnected = true,
                    glassesModel = modelName,
                    usbVendorIdHex = "0x${xrealDevice.vendorId.toString(16).uppercase().padStart(4, '0')}",
                    usbProductIdHex = "0x${xrealDevice.productId.toString(16).uppercase().padStart(4, '0')}",
                    usbInterfaceCount = xrealDevice.interfaceCount
                )
            }
            Log.i(TAG, "Detected XREAL USB-C hardware: $modelName")
        } else {
            _state.update { current ->
                current.copy(
                    isUsbGlassesConnected = false,
                    glassesModel = if (current.isSpatialUiEnabled) {
                        "Android XR Wired Glasses Runtime"
                    } else {
                        "Standby (Plug in XREAL USB-C or Run on Android XR)"
                    },
                    usbVendorIdHex = null,
                    usbProductIdHex = null,
                    usbInterfaceCount = 0
                )
            }
        }
    }

    fun scanPresentationDisplays() {
        val displays = runCatching {
            displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        }.getOrNull().orEmpty()

        val external = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (external != null) {
            val mode = external.mode
            val resolution = "${mode.physicalWidth}×${mode.physicalHeight}"
            val refresh = mode.refreshRate
            _state.update {
                it.copy(
                    externalDisplayConnected = true,
                    externalDisplayId = external.displayId,
                    externalDisplayName = external.name.ifBlank { "XREAL DP Alt Mode Display" },
                    externalDisplayResolution = resolution,
                    externalDisplayRefreshHz = refresh
                )
            }
            onPresentationDisplayChanged?.invoke(external)
        } else {
            _state.update {
                it.copy(
                    externalDisplayConnected = false,
                    externalDisplayId = null,
                    externalDisplayName = null,
                    externalDisplayResolution = null,
                    externalDisplayRefreshHz = null,
                    isOpticalPresentationActive = false
                )
            }
            onPresentationDisplayChanged?.invoke(null)
        }
    }

    fun setOpticalPresentationActive(active: Boolean) {
        _state.update { it.copy(isOpticalPresentationActive = active) }
    }

    fun updateJetpackXrCapabilities(
        isSpatialUiEnabled: Boolean,
        isContent3dEnabled: Boolean,
        isPassthroughControlEnabled: Boolean,
        displayBlendMode: String,
        isXrSessionAvailable: Boolean
    ) {
        _state.update { current ->
            val updatedModel = when {
                current.isUsbGlassesConnected -> current.glassesModel
                isSpatialUiEnabled -> "Android XR Wired Glasses (Full Space)"
                isXrSessionAvailable -> "Android XR Device (Home Space)"
                else -> current.glassesModel
            }
            current.copy(
                glassesModel = updatedModel,
                isSpatialUiEnabled = isSpatialUiEnabled,
                isContent3dEnabled = isContent3dEnabled,
                isPassthroughControlEnabled = isPassthroughControlEnabled,
                displayBlendMode = displayBlendMode,
                isXrSessionAvailable = isXrSessionAvailable
            )
        }
    }

    fun recenter3DoF() {
        yawZeroOffset = lastRawYaw
        pitchZeroOffset = lastRawPitch
        rollZeroOffset = lastRawRoll
        _state.update { it.copy(yawDeg = 0f, pitchDeg = 0f, rollDeg = 0f) }
    }

    /**
     * Projects the current live 3DoF optical reticle orientation onto the CNC worktable plane
     * inside safe cobot workspace bounds so operators can gaze-lock Pick or Place spatial anchors.
     */
    fun gazeToWorkcellAnchor(defaultZMm: Double = 95.0): SpatialAnchor {
        val current = _state.value
        val x = (360.0 + current.pitchDeg * -4.5).coerceIn(140.0, 620.0)
        val y = (current.yawDeg * 6.0).coerceIn(-300.0, 300.0)
        val z = (defaultZMm + current.rollDeg * 1.2).coerceIn(50.0, 260.0)
        return SpatialAnchor(
            xMm = round(x * 10.0) / 10.0,
            yMm = round(y * 10.0) / 10.0,
            zMm = round(z * 10.0) / 10.0
        )
    }

    private fun startImuSensor() {
        val mgr = sensorManager ?: return
        val sensor = mgr.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: mgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (sensor != null) {
            mgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            _state.update {
                it.copy(
                    isImuActive = true,
                    imuSource = sensor.name.ifBlank { "3DoF Rotation Vector IMU" }
                )
            }
        } else {
            _state.update {
                it.copy(
                    isImuActive = false,
                    imuSource = "IMU Unavailable on Host"
                )
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val rawYaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        lastRawYaw = rawYaw
        lastRawPitch = rawPitch
        lastRawRoll = rawRoll

        val relYaw = normalizeAngle(rawYaw - yawZeroOffset)
        val relPitch = normalizeAngle(rawPitch - pitchZeroOffset)
        val relRoll = normalizeAngle(rawRoll - rollZeroOffset)

        _state.update {
            it.copy(
                yawDeg = round(relYaw * 10f) / 10f,
                pitchDeg = round(relPitch * 10f) / 10f,
                rollDeg = round(relRoll * 10f) / 10f
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun normalizeAngle(deg: Float): Float {
        var a = deg % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }
}
