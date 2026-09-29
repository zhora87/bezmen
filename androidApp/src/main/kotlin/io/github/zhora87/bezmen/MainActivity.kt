package io.github.zhora87.bezmen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import io.github.zhora87.bezmen.ui.AppScreen
import io.github.zhora87.bezmen.ui.BezmenTheme
import io.github.zhora87.bezmen.ui.PermissionScreen
import io.github.zhora87.bezmen.ui.compare.ComparisonActions
import io.github.zhora87.bezmen.ui.compare.ComparisonScreen
import io.github.zhora87.bezmen.ui.scan.ScanActions
import io.github.zhora87.bezmen.ui.scan.ScanDisplay
import io.github.zhora87.bezmen.ui.scan.ScanScreen
import io.github.zhora87.bezmen.ui.scan.Viewfinder
import io.github.zhora87.bezmen.ui.settings.SettingsActions
import io.github.zhora87.bezmen.ui.settings.SettingsScreen

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
    val screen by viewModel.screen.collectAsState()
    val pack by viewModel.pack.collectAsState()
    val currencySymbol = pack.currency.symbols.first()
    when (screen) {
        AppScreen.SETTINGS -> SettingsRoute(viewModel)
        AppScreen.COMPARE -> ComparisonRoute(viewModel, currencySymbol)
        AppScreen.SCAN -> CameraRoute(viewModel, currencySymbol)
    }
}

@Composable
private fun SettingsRoute(viewModel: ScanViewModel) {
    BackHandler { viewModel.open(AppScreen.SCAN) }
    val settings by viewModel.settings.state.collectAsState()
    val actions = remember(viewModel) {
        SettingsActions(
            onUnits = viewModel.settings::setUnits,
            onPack = viewModel.settings::setPack,
            onBack = { viewModel.open(AppScreen.SCAN) },
        )
    }
    SettingsScreen(settings, viewModel.autoPackId, actions)
}

@Composable
private fun ComparisonRoute(viewModel: ScanViewModel, currencySymbol: String) {
    BackHandler { viewModel.open(AppScreen.SCAN) }
    val comparison by viewModel.comparison.state.collectAsState()
    val settings by viewModel.settings.state.collectAsState()
    val actions = remember(viewModel) {
        ComparisonActions(
            onRemove = viewModel.comparison::remove,
            onClear = viewModel.comparison::clear,
            onShootMore = { viewModel.open(AppScreen.SCAN) },
        )
    }
    ComparisonScreen(comparison, currencySymbol, settings.units, actions)
}

@Composable
private fun CameraRoute(viewModel: ScanViewModel, currencySymbol: String) {
    val state by viewModel.controller.state.collectAsState()
    val comparison by viewModel.comparison.state.collectAsState()
    val settings by viewModel.settings.state.collectAsState()
    val controller = viewModel.controller
    val actions = remember(viewModel) {
        ScanActions(
            onShutter = controller::onShutter,
            onRetake = viewModel::retake,
            onEdit = controller::onEdit,
            onAddToComparison = viewModel::addToComparison,
            onOpenComparison = { viewModel.open(AppScreen.COMPARE) },
            onOpenSettings = { viewModel.open(AppScreen.SETTINGS) },
        )
    }
    ScanScreen(
        state = state,
        display = ScanDisplay(
            frame = Viewfinder(viewModel.frame.widthFraction, viewModel.frame.heightFraction),
            currencySymbol = currencySymbol,
            units = settings.units,
            comparisonCount = comparison.items.size,
        ),
        actions = actions,
    ) { modifier ->
        val owner = LocalLifecycleOwner.current
        val context = androidx.compose.ui.platform.LocalContext.current
        val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
        val attempt by viewModel.cameraAttempt.collectAsState()
        LaunchedEffect(view, owner, attempt) { viewModel.bindCamera(owner, view) }
        AndroidView(factory = { view }, modifier = modifier)
    }
}
