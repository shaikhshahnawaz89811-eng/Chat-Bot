package com.brain.offlineai.ui.screens.computebridge

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.brain.offlineai.ui.theme.BrainBgPrimary
import com.brain.offlineai.ui.theme.BrainTextMuted
import com.brain.offlineai.ui.theme.BrainTextPrimary
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Real in-app camera QR scanner for Compute Bridge pairing. Opens the
 * device camera (CameraX preview), decodes QR codes on-device via ML Kit's
 * bundled barcode model (no network call - stays inside the app's
 * "100% Offline" posture), and hands the raw decoded text straight to
 * [onResult] the first time a valid-looking payload is seen.
 *
 * This is what was missing before: ComputeBridgeScreen only had a "paste
 * the QR content" text field with no way to actually open the camera and
 * scan from inside the app.
 */
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    // Guards against firing onResult more than once per scan session (ML
    // Kit's analyzer keeps delivering frames until the use case is torn
    // down, so without this a single scan could pair/navigate twice).
    var hasScanned by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BrainBgPrimary)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = BrainTextPrimary)
            }
            Text("Scan Worker QR", color = BrainTextPrimary, fontSize = 20.sp)
        }

        when {
            hasCameraPermission -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CameraPreviewWithScanner(
                        onBarcodeDetected = { text ->
                            if (!hasScanned) {
                                hasScanned = true
                                onResult(text)
                            }
                        }
                    )
                }
                Text(
                    "Point the camera at the Worker app's QR code.",
                    color = BrainTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
            permissionDenied -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Camera permission was denied, so the QR code can't be scanned. " +
                                "You can still pair by pasting the QR's text content on the " +
                                "previous screen, or grant camera access in system settings.",
                            color = BrainTextMuted,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant camera access")
                        }
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        val value = barcodes.firstOrNull {
                                            it.valueType == Barcode.TYPE_TEXT || it.valueType == Barcode.TYPE_UNKNOWN
                                        }?.rawValue ?: barcodes.firstOrNull()?.rawValue
                                        if (!value.isNullOrBlank()) {
                                            onBarcodeDetected(value)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        // Always close the ImageProxy or CameraX stalls the
                                        // analysis pipeline after the first frame.
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (_: Exception) {
                    // Camera bind can legitimately fail (device has no
                    // camera, already in use by another app, etc.) - the
                    // permission-denied-style message on the paste-code
                    // field is still available as a fallback path.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
