package com.agentpilot.shared.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * Full-screen QR code scanner.
 *
 * Requests camera permission on first launch. Once a QR code whose raw value starts with
 * "ws://" or "wss://" is detected, [onScanned] is called exactly once with the URL.
 *
 * Note: add <uses-permission android:name="android.permission.CAMERA"/> to AndroidManifest.xml.
 */
@Composable
fun QrScannerScreen(
    onScanned: (url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                onQrDetected = { raw ->
                    if (raw.startsWith("ws://") || raw.startsWith("wss://")) {
                        onScanned(raw)
                    }
                }
            )
        } else {
            Text(
                text = "Camera permission is required to scan QR codes.",
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close scanner")
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: LifecycleOwner,
    onQrDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    // Guard against multiple callbacks on consecutive frames with the same QR
    val alreadyScanned = remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val barcodeScanner = BarcodeScanning.getClient()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                            val mediaImage = proxy.image
                            if (mediaImage != null && !alreadyScanned.value) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    proxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val raw = barcodes.firstOrNull()?.rawValue
                                        if (raw != null && !alreadyScanned.value) {
                                            alreadyScanned.value = true
                                            onQrDetected(raw)
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            } else {
                                proxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
