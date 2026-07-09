package lesser.evil

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import lesser.evil.ui.AppInstaller
import lesser.evil.ui.theme.OwnDroidTheme

class AppInstallerActivity:FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val vm by viewModels<AppInstallerViewModel>()
        vm.initialize(intent)
        vm.registerInstallerReceiver(this)
        val theme = ThemeSettings(SP.materialYou, SP.darkTheme, SP.blackTheme)
        setContent {
            OwnDroidTheme(theme) {
                val uiState by vm.uiState.collectAsState()
                AppInstaller(
                    uiState, vm::onPackagesAdd, vm::onPackageRemove, vm::startInstall, vm::closeResultDialog
                )
            }
        }
    }
}
