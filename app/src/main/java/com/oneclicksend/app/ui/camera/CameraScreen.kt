package com.oneclicksend.app.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oneclicksend.app.R
import com.oneclicksend.app.camera.saveJpegUpright
import com.oneclicksend.app.data.AppSettings
import com.oneclicksend.app.data.Messenger
import com.oneclicksend.app.send.SendUiState
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    settings: AppSettings,
    sendState: SendUiState,
    onChangeChat: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    if (!hasCameraPermission) {
        PermissionPane(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
        return
    }

    CameraPreviewPane(
        settings = settings,
        sendState = sendState,
        onChangeChat = onChangeChat,
        onPhotoCaptured = onPhotoCaptured,
    )
}

@Composable
private fun PermissionPane(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.camera_permission_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant_camera))
        }
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.open_settings))
        }
    }
}

@Composable
private fun CameraPreviewPane(
    settings: AppSettings,
    sendState: SendUiState,
    onChangeChat: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            keepScreenOn = true
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(85)
            .build()
    }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val shutterInteraction = remember { MutableInteractionSource() }
    var capturing by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(imageCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose { listener.disable() }
    }

    DisposableEffect(captureExecutor) {
        onDispose { captureExecutor.shutdown() }
    }

    LaunchedEffect(lifecycleOwner, imageCapture) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also { previewUseCase ->
            previewUseCase.surfaceProvider = previewView.surfaceProvider
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )
    }

    val capture = {
        if (!capturing) {
            capturing = true
            flash = true
            captureError = null
            takePicture(
                context = context,
                imageCapture = imageCapture,
                executor = captureExecutor,
                onSaved = { file ->
                    capturing = false
                    onPhotoCaptured(file)
                },
                onError = { error ->
                    capturing = false
                    captureError = error.message ?: context.getString(R.string.error_camera)
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                this.contentDescription = context.getString(R.string.shutter_cd)
                this.role = Role.Button
            }
            .clickable(
                enabled = !capturing,
                interactionSource = shutterInteraction,
                indication = null,
                onClick = capture,
            ),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        if (flash) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.35f)))
            LaunchedEffect(Unit) {
                delay(70)
                flash = false
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val destination = buildString {
                when (settings.messenger) {
                    Messenger.TELEGRAM -> append("Telegram · ")
                    Messenger.VK -> append("VK · ")
                    null -> Unit
                }
                append(settings.chatTitle.ifBlank { settings.chatId })
            }
            Text(
                text = stringResource(R.string.to_label, destination),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            val statusText = when {
                sendState.pendingCount > 1 -> stringResource(R.string.queue_count, sendState.pendingCount)
                sendState.retrying -> stringResource(R.string.retrying)
                sendState.pendingCount == 1 -> stringResource(R.string.sending)
                !sendState.lastError.isNullOrBlank() -> sendState.lastError
                sendState.lastSuccess -> stringResource(R.string.sent)
                else -> stringResource(R.string.tap_hint)
            }
            Text(
                text = statusText,
                color = if (!sendState.lastError.isNullOrBlank()) Color(0xFFFF8A80) else Color(0xFFD0D6DE),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (!captureError.isNullOrBlank()) {
                Text(captureError.orEmpty(), color = Color(0xFFFF8A80))
            }
            TextButton(onClick = onChangeChat) {
                Text(stringResource(R.string.change_chat), color = Color.White)
            }
        }

        val shutterColor by animateColorAsState(
            targetValue = if (capturing) Color(0xFF3DDC97) else Color.White,
            label = "shutter",
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .size(132.dp)
                .border(width = 6.dp, color = Color.White, shape = CircleShape)
                .padding(10.dp)
                .clip(CircleShape)
                .background(shutterColor),
        )
    }
}

private suspend fun android.content.Context.awaitCameraProvider(): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(this),
        )
    }
}

private fun takePicture(
    context: android.content.Context,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onSaved: (File) -> Unit,
    onError: (Exception) -> Unit,
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val file = File(context.cacheDir, "ocs_${System.currentTimeMillis()}.jpg")
                    image.saveJpegUpright(file)
                    ContextCompat.getMainExecutor(context).execute { onSaved(file) }
                } catch (error: Exception) {
                    ContextCompat.getMainExecutor(context).execute { onError(error) }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(context).execute { onError(exception) }
            }
        },
    )
}
