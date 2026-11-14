package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Multi-engine on-device QR decoder for VLESS/VMess/Trojan/SS/WARP/Worker links:
 * 1. CameraX live frames -> ML Kit BarcodeScanner
 * 2. Zero-permission Photo Picker screenshot/image Uri -> ML Kit with offline ZXing fallback
 * 3. Direct Bitmap decoding -> ZXing QRCodeReader (zero external service dependency)
 */
object QrCodeDecoder {

    private val qrScannerOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }

    private val decodeHints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    /**
     * Decodes a QR code directly from an in-memory [Bitmap] using bundled ZXing.
     * Works 100% offline with zero Google Play Services requirement.
     */
    fun decodeFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = QRCodeReader()

        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), decodeHints)?.text?.trim()
        } catch (_: Exception) {
            try {
                reader.reset()
                reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), decodeHints)?.text?.trim()
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Loads an image from a Photo Picker [Uri] (zero storage permissions required) and decodes any QR code.
     * Uses ML Kit first and seamlessly falls back to offline ZXing [decodeFromBitmap].
     */
    suspend fun decodeFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = loadScaledBitmap(context, uri) ?: return@withContext null
        try {
            val mlKitResult = tryDecodeWithMlKit(InputImage.fromBitmap(bitmap, 0))
            if (!mlKitResult.isNullOrBlank()) {
                return@withContext mlKitResult.trim()
            }
        } catch (_: Exception) {
            // Fall through to offline ZXing decoder
        }
        decodeFromBitmap(bitmap)
    }

    /**
     * Processes a CameraX [ImageProxy] frame using ML Kit [BarcodeScanning].
     * Always closes [imageProxy] upon completion.
     */
    @OptIn(ExperimentalGetImage::class)
    fun analyzeImageProxy(
        imageProxy: ImageProxy,
        onQrDetected: (String) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient(qrScannerOptions)
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.trim() }
                    if (!raw.isNullOrBlank()) {
                        onQrDetected(raw)
                    }
                }
                .addOnFailureListener { e ->
                    onError(e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            imageProxy.close()
            onError(e)
        }
    }

    /**
     * Generates a square monochrome QR [Bitmap] for testing and config sharing.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Extracts the first supported VPN configuration link from raw QR text
     * (handles multiline QR payloads or surrounding whitespace).
     */
    fun extractSupportedConfigUri(rawQr: String): String? {
        val trimmed = rawQr.trim()
        if (UriParser.parse(trimmed) != null) return trimmed

        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (UriParser.parse(line) != null) return line
        }

        val schemeRegex = Regex(
            """(?i)\b(vless|vmess|trojan|ss|warp|fxvpn|revtun|https?)://[^\s"'<>]+"""
        )
        return schemeRegex.findAll(trimmed)
            .map { it.value.trim() }
            .firstOrNull { UriParser.parse(it) != null }
    }

    private suspend fun tryDecodeWithMlKit(image: InputImage): String? =
        suspendCancellableCoroutine { cont ->
            try {
                val scanner = BarcodeScanning.getClient(qrScannerOptions)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                        if (cont.isActive) cont.resume(raw)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > 1600) {
                        val scale = 1600f / maxDim
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Exception) {
                // Fall back to ContentResolver + BitmapFactory
            }
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }
}
