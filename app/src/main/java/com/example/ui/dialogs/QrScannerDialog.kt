package com.example.ui.dialogs

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.QrCodeDecoder
import com.example.ui.theme.CardBackground
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RedDisconnect
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Play-Policy compliant QR code scanner dialog:
 * - Uses CameraX + ML Kit BarcodeScanning with runtime CAMERA permission flow.
 * - Uses zero-permission Android Photo Picker (`PickVisualMedia`) to scan QR codes directly
 *   from screenshots or saved screen images without `READ_EXTERNAL_STORAGE`/`READ_MEDIA_IMAGES`.
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrConfigDecoded: (String) -> Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var scanStatusText by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var hasDeliveredResult by remember { mutableStateOf(false) }

    val handleDecodedText: (String) -> Unit = remember {
        { rawPayload ->
            if (!hasDeliveredResult) {
                val extracted = QrCodeDecoder.extractSupportedConfigUri(rawPayload)
                if (extracted != null && onQrConfigDecoded(extracted)) {
                    hasDeliveredResult = true
                    onDismiss()
                } else {
                    isErrorStatus = true
                    scanStatusText = "QR detected, but it does not contain a supported VLESS/VMess/Trojan/SS/WARP link."
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            isErrorStatus = true
            scanStatusText = "Camera permission denied. You can still scan a screenshot using Pick Screen Image below."
        } else {
            isErrorStatus = false
            scanStatusText = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isErrorStatus = false
            scanStatusText = "Analyzing image for QR code..."
            scope.launch {
                val decoded = QrCodeDecoder.decodeFromUri(context, uri)
                if (decoded != null) {
                    handleDecodedText(decoded)
                } else {
                    isErrorStatus = true
                    scanStatusText = "No readable VPN QR code found in the selected image."
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172B))
                .border(1.5.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "QR CONFIG SCANNER",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Scan Camera or Screen Image",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_qr_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close QR Scanner", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Camera Preview Area or Permission Prompt
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF070C18))
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                ) {
                    if (hasCameraPermission) {
                        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                        DisposableEffect(Unit) {
                            onDispose { analysisExecutor.shutdown() }
                        }

                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also { analysis ->
                                                analysis.setAnalyzer(analysisExecutor) { proxy ->
                                                    QrCodeDecoder.analyzeImageProxy(
                                                        imageProxy = proxy,
                                                        onQrDetected = { raw ->
                                                            previewView.post {
                                                                handleDecodedText(raw)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        cameraProvider.unbindAll()
                                        cameraControl = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        isErrorStatus = true
                                        scanStatusText = "Camera unavailable on this device: ${e.localizedMessage ?: "Use image picker below"}"
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Cyber Viewfinder Corners
                        Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                            val corner = 24.dp.toPx()
                            val stroke = 3.dp.toPx()
                            val w = size.width
                            val h = size.height
                            // Top-left
                            drawLine(CyanPrimary, Offset(0f, 0f), Offset(corner, 0f), stroke)
                            drawLine(CyanPrimary, Offset(0f, 0f), Offset(0f, corner), stroke)
                            // Top-right
                            drawLine(CyanPrimary, Offset(w - corner, 0f), Offset(w, 0f), stroke)
                            drawLine(CyanPrimary, Offset(w, 0f), Offset(w, corner), stroke)
                            // Bottom-left
                            drawLine(CyanPrimary, Offset(0f, h), Offset(corner, h), stroke)
                            drawLine(CyanPrimary, Offset(0f, h - corner), Offset(0f, h), stroke)
                            // Bottom-right
                            drawLine(CyanPrimary, Offset(w - corner, h), Offset(w, h), stroke)
                            drawLine(CyanPrimary, Offset(w, h - corner), Offset(w, h), stroke)
                        }

                        // Torch toggle overlay
                        IconButton(
                            onClick = {
                                torchEnabled = !torchEnabled
                                cameraControl?.cameraControl?.enableTorch(torchEnabled)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xAA0A1120))
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Toggle Flashlight",
                                tint = if (torchEnabled) CyanPrimary else TextSecondary
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Point camera at a VLESS, VMess, Trojan, or Shadowsocks QR code, or import directly from a screenshot.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF00363D)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("grant_camera_permission_button")
                            ) {
                                Text("Enable Camera Scanner", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (!scanStatusText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isErrorStatus) RedDisconnect.copy(alpha = 0.14f) else EmeraldConnected.copy(alpha = 0.14f))
                            .border(
                                1.dp,
                                if (isErrorStatus) RedDisconnect.copy(alpha = 0.45f) else EmeraldConnected.copy(alpha = 0.45f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = scanStatusText!!,
                            color = if (isErrorStatus) RedDisconnect else EmeraldConnected,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Zero-permission Photo Picker for scanning QR screenshots directly from screen
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground, contentColor = CyanPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .testTag("pick_qr_image_button")
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Screenshot / Gallery QR Image", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clip.primaryClip?.getItemAt(0)?.text?.toString()
                            if (clipText.isNullOrBlank()) {
                                isErrorStatus = true
                                scanStatusText = "Clipboard is empty."
                            } else {
                                handleDecodedText(clipText)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_paste_clipboard_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("From Clipboard", color = CyanPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text("Close", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Uses Android Photo Picker (zero storage permission required) and on-device ML Kit + ZXing decoding.",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
