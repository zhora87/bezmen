package io.github.zhora87.bezmen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.zhora87.bezmen.ui.BezmenTheme
import io.github.zhora87.bezmen.ui.PermissionScreen
import io.github.zhora87.bezmen.ui.scan.ScanActions
import io.github.zhora87.bezmen.ui.scan.ScanScreen

class MainActivity : ComponentActivity() {
    private val viewModel: ScanViewModel by viewModels()
    private var cameraGranted by mutableStateOf(false)
    private var askedBefore by mutableStateOf(false)

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        askedBefore = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BezmenTheme {
                if (cameraGranted) {
                    ScanRoute(viewModel)
                } else {
                    PermissionScreen(
                        askedBefore = askedBefore,
                        onRequest = { permission.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = ::openAppSettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}

@Composable
private fun ScanRoute(viewModel: ScanViewModel) {
    val state by viewModel.controller.state.collectAsState()
    val controller = viewModel.controller
    val actions = remember(controller) { ScanActions(controller::onShutter, controller::onRetake, controller::onEdit) }
    ScanScreen(
        state = state,
        frameWidth = viewModel.frame.widthFraction,
        frameHeight = viewModel.frame.heightFraction,
        currencySymbol = viewModel.currencySymbol,
        actions = actions,
    ) { modifier ->
        val owner = LocalLifecycleOwner.current
        val context = androidx.compose.ui.platform.LocalContext.current
        val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        LaunchedEffect(view, owner) { viewModel.camera.bind(owner, view) }
        AndroidView(factory = { view }, modifier = modifier)
    }
}
