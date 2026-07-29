package lesser.evil

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import lesser.evil.dpm.AddApnSetting
import lesser.evil.dpm.AddApnSettingScreen
import lesser.evil.dpm.AddDelegatedAdmin
import lesser.evil.dpm.AddDelegatedAdminScreen
import lesser.evil.dpm.AddPreferentialNetworkServiceConfig
import lesser.evil.dpm.AddPreferentialNetworkServiceConfigScreen
import lesser.evil.dpm.AffiliationId
import lesser.evil.dpm.AffiliationIdScreen
import lesser.evil.dpm.AlwaysOnVpnPackage
import lesser.evil.dpm.AlwaysOnVpnPackageScreen
import lesser.evil.dpm.ApplicationDetails
import lesser.evil.dpm.ApplicationDetailsScreen
import lesser.evil.dpm.ApplicationsDetails
import lesser.evil.dpm.ApplicationsDetailsScreen
import lesser.evil.dpm.ApplicationsFeatures
import lesser.evil.dpm.ApplicationsFeaturesScreen
import lesser.evil.dpm.AutoTimePolicy
import lesser.evil.dpm.AutoTimePolicyScreen
import lesser.evil.dpm.AutoTimeZonePolicy
import lesser.evil.dpm.AutoTimeZonePolicyScreen
import lesser.evil.dpm.BlockUninstall
import lesser.evil.dpm.CaCert
import lesser.evil.dpm.CaCertScreen
import lesser.evil.dpm.ChangeTime
import lesser.evil.dpm.ChangeTimeScreen
import lesser.evil.dpm.ChangeTimeZone
import lesser.evil.dpm.ChangeTimeZoneScreen
import lesser.evil.dpm.ChangeUsername
import lesser.evil.dpm.ChangeUsernameScreen
import lesser.evil.dpm.ClearAppStorage
import lesser.evil.dpm.ClearAppStorageScreen
import lesser.evil.dpm.ContentProtectionPolicy
import lesser.evil.dpm.ContentProtectionPolicyScreen
import lesser.evil.dpm.CreateUser
import lesser.evil.dpm.CreateUserScreen
import lesser.evil.dpm.CreateWorkProfile
import lesser.evil.dpm.CreateWorkProfileScreen
import lesser.evil.dpm.CredentialManagerPolicy
import lesser.evil.dpm.CredentialManagerPolicyScreen
import lesser.evil.dpm.CrossProfileIntentFilter
import lesser.evil.dpm.CrossProfileIntentFilterScreen
import lesser.evil.dpm.CrossProfilePackages
import lesser.evil.dpm.CrossProfileWidgetProviders
import lesser.evil.dpm.DelegatedAdmins
import lesser.evil.dpm.DelegatedAdminsScreen
import lesser.evil.dpm.DeleteWorkProfile
import lesser.evil.dpm.DeleteWorkProfileScreen
import lesser.evil.dpm.DeviceInfo
import lesser.evil.dpm.DeviceInfoScreen
import lesser.evil.dpm.DhizukuServerSettings
import lesser.evil.dpm.DhizukuServerSettingsScreen
import lesser.evil.dpm.DisableAccountManagement
import lesser.evil.dpm.DisableAccountManagementScreen
import lesser.evil.dpm.DisableMeteredData
import lesser.evil.dpm.DisableUserControl
import lesser.evil.dpm.EditAppGroup
import lesser.evil.dpm.EditAppGroupScreen
import lesser.evil.dpm.EnableSystemApp
import lesser.evil.dpm.EnableSystemAppScreen
import lesser.evil.dpm.FrpPolicy
import lesser.evil.dpm.FrpPolicyScreen
import lesser.evil.dpm.HardwareMonitor
import lesser.evil.dpm.HardwareMonitorScreen
import lesser.evil.dpm.Hide
import lesser.evil.dpm.InstallExistingApp
import lesser.evil.dpm.InstallExistingAppScreen
import lesser.evil.dpm.InstallSystemUpdate
import lesser.evil.dpm.InstallSystemUpdateScreen
import lesser.evil.dpm.KeepUninstalledPackages
import lesser.evil.dpm.Keyguard
import lesser.evil.dpm.KeyguardDisabledFeatures
import lesser.evil.dpm.KeyguardDisabledFeaturesScreen
import lesser.evil.dpm.KeyguardScreen
import lesser.evil.dpm.LockScreenInfo
import lesser.evil.dpm.LockScreenInfoScreen
import lesser.evil.dpm.LockTaskMode
import lesser.evil.dpm.LockTaskModeScreen
import lesser.evil.dpm.ManageAppGroups
import lesser.evil.dpm.ManageAppGroupsScreen
import lesser.evil.dpm.ManagedConfiguration
import lesser.evil.dpm.ManagedConfigurationScreen
import lesser.evil.dpm.ManualConfiguration
import lesser.evil.dpm.ManualConfigurationScreen
import lesser.evil.dpm.ManualConfigurations
import lesser.evil.dpm.MtePolicy
import lesser.evil.dpm.MtePolicyScreen
import lesser.evil.dpm.MultiplePermissions
import lesser.evil.dpm.MultiplePermissionsScreen
import lesser.evil.dpm.NearbyStreamingPolicy
import lesser.evil.dpm.NearbyStreamingPolicyScreen
import lesser.evil.dpm.Network
import lesser.evil.dpm.NetworkLogging
import lesser.evil.dpm.NetworkLoggingScreen
import lesser.evil.dpm.NetworkOptions
import lesser.evil.dpm.NetworkOptionsScreen
import lesser.evil.dpm.NetworkScreen
import lesser.evil.dpm.NetworkStatsScreen
import lesser.evil.dpm.NetworkStatsViewer
import lesser.evil.dpm.NetworkStatsViewerScreen
import lesser.evil.dpm.OrganizationOwnedProfile
import lesser.evil.dpm.OrganizationOwnedProfileScreen
import lesser.evil.dpm.OverrideApn
import lesser.evil.dpm.OverrideApnScreen
import lesser.evil.dpm.PackageFunctionScreen
import lesser.evil.dpm.Password
import lesser.evil.dpm.PasswordInfo
import lesser.evil.dpm.PasswordInfoScreen
import lesser.evil.dpm.PasswordScreen
import lesser.evil.dpm.PermissionPolicy
import lesser.evil.dpm.PermissionPolicyScreen
import lesser.evil.dpm.PermissionsManager
import lesser.evil.dpm.PermissionsManagerScreen
import lesser.evil.dpm.PermittedAccessibilityServices
import lesser.evil.dpm.PermittedAsAndImPackages
import lesser.evil.dpm.PermittedInputMethods
import lesser.evil.dpm.EditPolicyToggle
import lesser.evil.dpm.EditPolicyToggleScreen
import lesser.evil.dpm.PolicyToggles
import lesser.evil.dpm.PolicyTogglesScreen
import lesser.evil.dpm.PreferentialNetworkService
import lesser.evil.dpm.PreferentialNetworkServiceInfo
import lesser.evil.dpm.PreferentialNetworkServiceScreen
import lesser.evil.dpm.PrivateDns
import lesser.evil.dpm.PrivateDnsScreen
import lesser.evil.dpm.QueryNetworkStats
import lesser.evil.dpm.RecommendedGlobalProxy
import lesser.evil.dpm.RecommendedGlobalProxyScreen
import lesser.evil.dpm.RequiredPasswordComplexity
import lesser.evil.dpm.RequiredPasswordComplexityScreen
import lesser.evil.dpm.RequiredPasswordQuality
import lesser.evil.dpm.RequiredPasswordQualityScreen
import lesser.evil.dpm.ResetPassword
import lesser.evil.dpm.ResetPasswordScreen
import lesser.evil.dpm.ResetPasswordToken
import lesser.evil.dpm.ResetPasswordTokenScreen
import lesser.evil.dpm.SecurityLogging
import lesser.evil.dpm.SecurityLoggingScreen
import lesser.evil.dpm.SetDefaultDialer
import lesser.evil.dpm.SetDefaultDialerScreen
import lesser.evil.dpm.SetSystemUpdatePolicy
import lesser.evil.dpm.SupportMessage
import lesser.evil.dpm.SupportMessageScreen
import lesser.evil.dpm.Suspend
import lesser.evil.dpm.SuspendPersonalApp
import lesser.evil.dpm.SuspendPersonalAppScreen
import lesser.evil.dpm.SystemManager
import lesser.evil.dpm.SystemManagerScreen
import lesser.evil.dpm.SystemOptions
import lesser.evil.dpm.SystemOptionsScreen
import lesser.evil.dpm.SystemUpdatePolicyScreen
import lesser.evil.dpm.TransferOwnership
import lesser.evil.dpm.TransferOwnershipScreen
import lesser.evil.dpm.UninstallApp
import lesser.evil.dpm.UninstallAppScreen
import lesser.evil.dpm.UpdateNetwork
import lesser.evil.dpm.UpdateNetworkScreen
import lesser.evil.dpm.UserInfo
import lesser.evil.dpm.UserInfoScreen
import lesser.evil.dpm.UserOperation
import lesser.evil.dpm.UserOperationScreen
import lesser.evil.dpm.UserRestriction
import lesser.evil.dpm.UserRestrictionEditor
import lesser.evil.dpm.UserRestrictionEditorScreen
import lesser.evil.dpm.UserRestrictionOptions
import lesser.evil.dpm.UserRestrictionOptionsScreen
import lesser.evil.dpm.UserRestrictionScreen
import lesser.evil.dpm.UserSessionMessage
import lesser.evil.dpm.UserSessionMessageScreen
import lesser.evil.dpm.Users
import lesser.evil.dpm.UsersOptions
import lesser.evil.dpm.UsersOptionsScreen
import lesser.evil.dpm.UsersScreen
import lesser.evil.dpm.WiFi
import lesser.evil.dpm.WifiScreen
import lesser.evil.dpm.WifiSecurityLevel
import lesser.evil.dpm.WifiSecurityLevelScreen
import lesser.evil.dpm.WifiSsidPolicyScreen
import lesser.evil.dpm.WipeData
import lesser.evil.dpm.WipeDataScreen
import lesser.evil.dpm.WorkModes
import lesser.evil.dpm.WorkModesScreen
import lesser.evil.dpm.WorkProfile
import lesser.evil.dpm.WorkProfileScreen
import lesser.evil.dpm.dhizukuErrorStatus
import lesser.evil.ui.NavTransition
import lesser.evil.ui.theme.OwnDroidTheme
import kotlinx.serialization.Serializable
import java.util.Locale

@ExperimentalMaterial3Api
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val context = applicationContext
        val locale = context.resources?.configuration?.locale
        zhCN = locale == Locale.SIMPLIFIED_CHINESE || locale == Locale.CHINESE || locale == Locale.CHINA
        val vm by viewModels<MyViewModel>()
        if (
            VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        registerPackageRemovedReceiver(this) {
            vm.onPackageRemoved(it)
        }
        if (Privilege.status.value.activated) {
            LockTaskUtils.restoreStaleTemporaryAppStates(this)
        }
        setContent {
            var appLockDialog by rememberSaveable { mutableStateOf(false) }
            val theme by vm.theme.collectAsStateWithLifecycle()
            OwnDroidTheme(theme) {
                Home(vm) { appLockDialog = true }
                if (appLockDialog) {
                    val restricted by vm.restrictedMode.collectAsStateWithLifecycle()
                    AppLockDialog(
                        onSucceed = {
                            vm.exitRestrictedMode()
                            appLockDialog = false
                        },
                        onEnterRestricted = if (restricted || SP.lockPasswordHash.isNullOrEmpty()) null
                        else ({
                            vm.enterRestrictedMode()
                            appLockDialog = false
                        }),
                        onDismiss = {
                            if (vm.restrictedMode.value) appLockDialog = false
                            else moveTaskToBack(true)
                        }
                    )
                }
            }
        }
    }

}

@ExperimentalMaterial3Api
@Composable
fun Home(vm: MyViewModel, onLock: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val focusMgr = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val restricted by vm.restrictedMode.collectAsStateWithLifecycle()
    fun navigateUp() { navController.navigateUp() }
    fun navigate(destination: Any) {
        navController.navigate(destination) {
            launchSingleTop = true
        }
    }
    fun choosePackage() {
        navController.navigate(ApplicationsList(false, true))
    }
    fun chooseSinglePackage() {
        navController.navigate(ApplicationsList(false, false))
    }
    fun navigateToAppGroups() {
        navController.navigate(ManageAppGroups)
    }
    LaunchedEffect(Unit) {
        if(!Privilege.status.value.activated) {
            navController.navigate(WorkModes(false)) {
                popUpTo<Home> { inclusive = true }
            }
        }
    }
    @Suppress("NewApi") NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusMgr.clearFocus() }) },
        enterTransition = { NavTransition.enterTransition },
        exitTransition = { NavTransition.exitTransition },
        popEnterTransition = { NavTransition.popEnterTransition },
        popExitTransition = { NavTransition.popExitTransition }
    ) {
        composable<Home> { HomeScreen(restricted, ::navigate, onLock) }
        composable<WorkModes> {
            WorkModesScreen(vm, it.toRoute(), ::navigateUp, {
                navController.navigate(Home) {
                    popUpTo<WorkModes> { inclusive = true }
                }
            }, {
                navController.navigate(WorkModes(false)) {
                    popUpTo(Home) { inclusive = true }
                }
            }, ::navigate)
        }
        composable<DhizukuServerSettings> {
            DhizukuServerSettingsScreen(vm.dhizukuClients, vm::getDhizukuClients,
                vm::updateDhizukuClient, vm::getDhizukuServerEnabled, vm::setDhizukuServerEnabled,
                ::navigateUp)
        }

        composable<DelegatedAdmins> {
            DelegatedAdminsScreen(vm.delegatedAdmins, vm::getDelegatedAdmins, ::navigateUp, ::navigate)
        }
        composable<AddDelegatedAdmin>{
            AddDelegatedAdminScreen(vm.chosenPackage, ::chooseSinglePackage, it.toRoute(),
                vm::setDelegatedAdmin,  ::navigateUp)
        }
        composable<DeviceInfo> { DeviceInfoScreen(vm, ::navigateUp) }
        composable<LockScreenInfo> {
            LockScreenInfoScreen(vm::getLockScreenInfo, vm::setLockScreenInfo, ::navigateUp)
        }
        composable<SupportMessage> {
            SupportMessageScreen(vm::getShortSupportMessage, vm::getLongSupportMessage,
                vm::setShortSupportMessage, vm::setLongSupportMessage, ::navigateUp)
        }
        composable<TransferOwnership> {
            TransferOwnershipScreen(vm.deviceAdminReceivers, vm::getDeviceAdminReceivers,
                vm::transferOwnership, ::navigateUp) {
                navController.navigate(WorkModes(false)) {
                    popUpTo(Home) { inclusive = true }
                }
            }
        }

        composable<SystemManager> { SystemManagerScreen(vm, ::navigateUp, ::navigate) }
        composable<SystemOptions> { SystemOptionsScreen(vm, ::navigateUp) }
        composable<Keyguard> {
            KeyguardScreen(vm::setKeyguardDisabled, vm::lockScreen, ::navigateUp)
        }
        composable<HardwareMonitor> {
            HardwareMonitorScreen(vm.hardwareProperties, vm::getHardwareProperties,
                vm::setHpRefreshInterval, ::navigateUp)
        }
        composable<ChangeTime> { ChangeTimeScreen(vm::setTime, ::navigateUp) }
        composable<ChangeTimeZone> { ChangeTimeZoneScreen(vm::setTimeZone, ::navigateUp) }
        composable<AutoTimePolicy> {
            AutoTimePolicyScreen(vm::getAutoTimePolicy, vm::setAutoTimePolicy, ::navigateUp)
        }
        composable<AutoTimeZonePolicy> {
            AutoTimeZonePolicyScreen(vm::getAutoTimeZonePolicy, vm::setAutoTimeZonePolicy,
                ::navigateUp)
        }
        //composable<> { KeyPairs(::navigateUp) }
        composable<ContentProtectionPolicy> {
            ContentProtectionPolicyScreen(vm::getContentProtectionPolicy,
                vm::setContentProtectionPolicy, ::navigateUp)
        }
        composable<PermissionPolicy> {
            PermissionPolicyScreen(vm::getPermissionPolicy, vm::setPermissionPolicy, ::navigateUp)
        }
        composable<MtePolicy> {
            MtePolicyScreen(vm::getMtePolicy, vm::setMtePolicy, ::navigateUp)
        }
        composable<NearbyStreamingPolicy> {
            NearbyStreamingPolicyScreen(vm::getNsAppPolicy, vm::setNsAppPolicy,
                vm::getNsNotificationPolicy, vm::setNsNotificationPolicy, ::navigateUp)
        }
        composable<LockTaskMode> {
            LockTaskModeScreen(
                vm.chosenPackage, ::chooseSinglePackage, ::choosePackage, vm.lockTaskPackages,
                vm::getLockTaskPackages, vm::setLockTaskPackage, vm::startLockTaskMode,
                vm:: getLockTaskFeatures, vm::setLockTaskFeatures,
                vm.lockTaskProfiles, vm::getLockTaskProfiles, vm::buildLockTaskProfile,
                vm::addLockTaskProfile, vm::deleteLockTaskProfile, vm::startLockTaskProfile,
                { ShortcutUtils.setLockTaskProfileShortcut(context, it) }, ::navigateUp
            )
        }
        composable<CaCert> {
            CaCertScreen(vm.installedCaCerts, vm::getCaCerts, vm.selectedCaCert, vm::selectCaCert, vm::installCaCert, vm::parseCaCert,
                vm::exportCaCert, vm::uninstallCaCert, vm::uninstallAllCaCerts, ::navigateUp)
        }
        composable<SecurityLogging> {
            SecurityLoggingScreen(vm::getSecurityLoggingEnabled, vm::setSecurityLoggingEnabled,
                vm::exportSecurityLogs, vm::getSecurityLogsCount, vm::deleteSecurityLogs,
                vm::getPreRebootSecurityLogs, vm::exportPreRebootSecurityLogs, ::navigateUp)
        }
        composable<DisableAccountManagement> {
            DisableAccountManagementScreen(vm.mdAccountTypes, vm::getMdAccountTypes,
                vm::setMdAccountType, ::navigateUp)
        }
        composable<SetSystemUpdatePolicy> {
            SystemUpdatePolicyScreen(vm::getSystemUpdatePolicy, vm::setSystemUpdatePolicy,
                vm::getPendingSystemUpdate, ::navigateUp)
        }
        composable<InstallSystemUpdate> {
            InstallSystemUpdateScreen(vm::installSystemUpdate, ::navigateUp)
        }
        composable<FrpPolicy> {
            FrpPolicyScreen(vm.getFrpPolicy(), vm::setFrpPolicy, ::navigateUp)
        }
        composable<WipeData> { WipeDataScreen(vm::wipeData, ::navigateUp) }

        composable<Network> { NetworkScreen(::navigateUp, ::navigate) }
        composable<WiFi> {
            WifiScreen(vm, ::navigateUp, ::navigate) { navController.navigate(UpdateNetwork(it)) }
        }
        composable<NetworkOptions> {
            NetworkOptionsScreen(vm::getLanEnabled, vm::setLanEnabled, ::navigateUp)
        }
        composable<UpdateNetwork> {
            val info = vm.configuredNetworks.collectAsStateWithLifecycle().value[
                (it.toRoute() as UpdateNetwork).index
            ]
            UpdateNetworkScreen(info, vm::setWifi, ::navigateUp)
        }
        composable<WifiSecurityLevel> {
            WifiSecurityLevelScreen(vm::getMinimumWifiSecurityLevel,
                vm::setMinimumWifiSecurityLevel, ::navigateUp)
        }
        composable<WifiSsidPolicyScreen> {
            WifiSsidPolicyScreen(vm::getSsidPolicy, vm::setSsidPolicy, ::navigateUp)
        }
        composable<QueryNetworkStats> {
            NetworkStatsScreen(vm.chosenPackage, ::chooseSinglePackage, vm::getPackageUid,
                vm::queryNetworkStats, ::navigateUp) { navController.navigate(NetworkStatsViewer) }
        }
        composable<NetworkStatsViewer> {
            NetworkStatsViewerScreen(vm.networkStatsData, vm::clearNetworkStats, ::navigateUp)
        }
        composable<PrivateDns> {
            PrivateDnsScreen(vm::getPrivateDns, vm::setPrivateDns, ::navigateUp)
        }
        composable<AlwaysOnVpnPackage> {
            AlwaysOnVpnPackageScreen(vm::getAlwaysOnVpnPackage, vm::getAlwaysOnVpnLockdown,
                vm::setAlwaysOnVpn, vm.chosenPackage, ::chooseSinglePackage, ::navigateUp)
        }
        composable<RecommendedGlobalProxy> {
            RecommendedGlobalProxyScreen(vm::setRecommendedGlobalProxy, ::navigateUp)
        }
        composable<NetworkLogging> {
            NetworkLoggingScreen(vm::getNetworkLoggingEnabled, vm::setNetworkLoggingEnabled,
                vm::getNetworkLogsCount, vm::exportNetworkLogs, vm::deleteNetworkLogs, ::navigateUp)
        }
        //composable<WifiAuthKeypair> { WifiAuthKeypairScreen(::navigateUp) }
        composable<PreferentialNetworkService> {
            PreferentialNetworkServiceScreen(vm::getPnsEnabled, vm::setPnsEnabled, vm.pnsConfigs,
                vm::getPnsConfigs, ::navigateUp, ::navigate)
        }
        composable<AddPreferentialNetworkServiceConfig> {
            val info = vm.pnsConfigs.collectAsStateWithLifecycle().value.getOrNull(
                it.toRoute<AddPreferentialNetworkServiceConfig>().index
            ) ?: PreferentialNetworkServiceInfo()
            AddPreferentialNetworkServiceConfigScreen(info, vm::setPnsConfig, ::navigateUp)
        }
        composable<OverrideApn> {
            OverrideApnScreen(vm.apnConfigs, vm::getApnConfigs, vm::getApnEnabled,
                vm::setApnEnabled, ::navigateUp) { navController.navigate(AddApnSetting(it)) }
        }
        composable<AddApnSetting> {
            val origin = vm.apnConfigs.collectAsStateWithLifecycle().value.getOrNull((it.toRoute() as AddApnSetting).index)
            AddApnSettingScreen(vm::setApnConfig, vm::removeApnConfig, origin, ::navigateUp)
        }

        composable<WorkProfile> { WorkProfileScreen(::navigateUp, ::navigate) }
        composable<OrganizationOwnedProfile> {
            OrganizationOwnedProfileScreen(vm::activateOrgProfileByShizuku, ::navigateUp)
        }
        composable<CreateWorkProfile> {
            CreateWorkProfileScreen(vm::createWorkProfile, ::navigateUp)
        }
        composable<SuspendPersonalApp> {
            SuspendPersonalAppScreen(
                vm::getPersonalAppsSuspendedReason, vm::setPersonalAppsSuspended,
                vm::getProfileMaxTimeOff, vm::setProfileMaxTimeOff, ::navigateUp
            )
        }
        composable<CrossProfileIntentFilter> {
            CrossProfileIntentFilterScreen(vm::addCrossProfileIntentFilter, ::navigateUp)
        }
        composable<DeleteWorkProfile> { DeleteWorkProfileScreen(vm::wipeData, ::navigateUp) }

        composable<ApplicationsList> {
            val params = it.toRoute<ApplicationsList>()
            AppChooserScreen(
                params, vm.installedPackages, vm.refreshPackagesProgress, { name ->
                if (params.canSwitchView) {
                    val chosen = name?.split('\n') ?: emptyList()
                    when {
                        chosen.isEmpty() -> navigateUp()
                        chosen.size == 1 -> navigate(ApplicationDetails(chosen.first()))
                        else -> navigate(ApplicationsDetails(chosen))
                    }
                } else {
                    if (name != null) vm.chosenPackage.trySend(name)
                    navigateUp()
                }
            }, {
                navController.navigate(ApplicationsFeatures) {
                    popUpTo(Home)
                }
            }, vm::refreshPackageList,
                vm.appGroups, vm.autoAppGroups, vm::refreshAutoAppGroups
            ) { id, name, apps -> navController.navigate(EditAppGroup(id, name, apps)) }
        }
        composable<ApplicationsFeatures> {
            ApplicationsFeaturesScreen(::navigateUp, ::navigate) {
                navController.navigate(ApplicationsList(true, true)) {
                    popUpTo(Home)
                }
            }
        }
        composable<ApplicationDetails> {
            ApplicationDetailsScreen(it.toRoute(), vm, ::navigateUp, ::navigate)
        }
        composable<ApplicationsDetails> {
            ApplicationsDetailsScreen(it.toRoute(), vm, ::navigateUp, ::navigate)
        }
        composable<Suspend> {
            PackageFunctionScreen(
                R.string.suspend, vm.suspendedPackages, vm::getSuspendedPackaged,
                vm::setPackageSuspended, ::navigateUp, vm.chosenPackage, ::choosePackage,
                ::navigateToAppGroups, vm.appGroups, vm.autoAppGroups, vm::refreshAutoAppGroups,
                R.string.info_suspend_app
            )
        }
        composable<Hide> {
            PackageFunctionScreen(
                R.string.hide, vm.hiddenPackages, vm::getHiddenPackages, vm::setPackageHidden,
                ::navigateUp, vm.chosenPackage, ::choosePackage, ::navigateToAppGroups, vm.appGroups,
                vm.autoAppGroups, vm::refreshAutoAppGroups
            )
        }
        composable<BlockUninstall> {
            PackageFunctionScreen(
                R.string.block_uninstall, vm.ubPackages, vm::getUbPackages, vm::setPackageUb,
                ::navigateUp, vm.chosenPackage, ::choosePackage, ::navigateToAppGroups, vm.appGroups,
                vm.autoAppGroups, vm::refreshAutoAppGroups
            )
        }
        composable<DisableUserControl> {
            PackageFunctionScreen(
                R.string.disable_user_control, vm.ucdPackages, vm::getUcdPackages,
                vm::setPackageUcd, ::navigateUp, vm.chosenPackage, ::choosePackage,
                ::navigateToAppGroups, vm.appGroups, vm.autoAppGroups, vm::refreshAutoAppGroups,
                R.string.info_disable_user_control
            )
        }
        composable<PermissionsManager> {
            PermissionsManagerScreen(
                vm.packagePermissions, vm::getPackagePermissions, vm::setPackagePermission,
                vm::getAppInfo, vm.appsWithPermissions, vm::getAppsWithPermissions,
                vm::clearPackagePermissions, ::navigateUp, it.toRoute(), vm.chosenPackage,
                ::choosePackage
            )
        }
        composable<DisableMeteredData> {
            PackageFunctionScreen(
                R.string.disable_metered_data, vm.mddPackages, vm::getMddPackages,
                vm::setPackageMdd, ::navigateUp, vm.chosenPackage, ::choosePackage,
                ::navigateToAppGroups, vm.appGroups, vm.autoAppGroups, vm::refreshAutoAppGroups
            )
        }
        composable<ClearAppStorage> {
            ClearAppStorageScreen(
                vm.chosenPackage, ::chooseSinglePackage, vm::clearAppData, ::navigateUp
            )
        }
        composable<UninstallApp> {
            UninstallAppScreen(
                vm.chosenPackage, ::chooseSinglePackage, vm::uninstallPackage, ::navigateUp
            )
        }
        composable<KeepUninstalledPackages> {
            PackageFunctionScreen(
                R.string.keep_uninstalled_packages, vm.kuPackages, vm::getKuPackages,
                vm::setPackageKu, ::navigateUp, vm.chosenPackage, ::choosePackage,
                ::navigateToAppGroups, vm.appGroups, vm.autoAppGroups, vm::refreshAutoAppGroups,
                R.string.info_keep_uninstalled_apps
            )
        }
        composable<InstallExistingApp> {
            InstallExistingAppScreen(
                vm.chosenPackage, ::chooseSinglePackage, vm::installExistingApp, ::navigateUp
            )
        }
        composable<CrossProfilePackages> {
            PackageFunctionScreen(
                R.string.cross_profile_apps, vm.cpPackages,
                vm::getCpPackages, vm::setPackageCp, ::navigateUp, vm.chosenPackage,
                ::choosePackage, ::navigateToAppGroups, vm.appGroups, vm.autoAppGroups,
                vm::refreshAutoAppGroups
            )
        }
        composable<CrossProfileWidgetProviders> {
            PackageFunctionScreen(R.string.cross_profile_widget, vm.cpwProviders,
                vm::getCpwProviders, vm::setCpwProvider, ::navigateUp, vm.chosenPackage,
                ::choosePackage, ::navigateToAppGroups, vm.appGroups, vm.autoAppGroups,
                vm::refreshAutoAppGroups)
        }
        composable<CredentialManagerPolicy> {
            CredentialManagerPolicyScreen(
                vm.chosenPackage, ::choosePackage, vm.cmPackages, vm::getCmPolicy,
                vm::setCmPackage, vm::setCmPolicy, ::navigateUp
            )
        }
        composable<PermittedAccessibilityServices> {
            PermittedAsAndImPackages(
                R.string.permitted_accessibility_services,
                R.string.system_accessibility_always_allowed, vm.chosenPackage, ::choosePackage,
                vm.pasPackages, vm::getPasPackages, vm::setPasPackage, vm::setPasPolicy,
                ::navigateUp
            )
        }
        composable<PermittedInputMethods> {
            PermittedAsAndImPackages(
                R.string.permitted_ime, R.string.system_ime_always_allowed,
                vm.chosenPackage, ::choosePackage, vm.pimPackages, vm::getPimPackages,
                vm::setPimPackage, vm::setPimPolicy, ::navigateUp
            )
        }
        composable<EnableSystemApp> {
            EnableSystemAppScreen(
                vm.chosenPackage, ::chooseSinglePackage, vm::enableSystemApp, ::navigateUp
            )
        }
        composable<SetDefaultDialer> {
            SetDefaultDialerScreen(
                vm.chosenPackage, ::chooseSinglePackage, vm::setDefaultDialer, ::navigateUp
            )
        }
        composable<ManagedConfiguration> {
            val params: ManagedConfiguration = it.toRoute()
            ManagedConfigurationScreen(
                params, vm.appRestrictions, vm::getAppRestrictions, vm::setAppRestrictions,
                vm::clearAppRestrictions, { navigate(ManualConfiguration(params.packageName)) },
                ::navigateUp
            )
        }
        composable<ManualConfiguration> {
            ManualConfigurationScreen(
                listOf(it.toRoute<ManualConfiguration>().packageName), vm.manualRestrictions,
                vm::getManualRestrictions, vm::setManualRestriction, vm::removeManualRestriction,
                vm::getAppInfo, vm.appsWithRestrictions, vm::getAppsWithRestrictions,
                vm::clearAppRestrictionsOf, false, vm.chosenPackage, ::choosePackage, ::navigateUp
            )
        }
        composable<ManualConfigurations> {
            ManualConfigurationScreen(
                it.toRoute<ManualConfigurations>().packages, vm.manualRestrictions,
                vm::getManualRestrictions, vm::setManualRestriction, vm::removeManualRestriction,
                vm::getAppInfo, vm.appsWithRestrictions, vm::getAppsWithRestrictions,
                vm::clearAppRestrictionsOf, true, vm.chosenPackage, ::choosePackage, ::navigateUp
            )
        }
        composable<MultiplePermissions> {
            MultiplePermissionsScreen(
                it.toRoute(), vm.packagePermissions, vm::getPackagePermissions,
                vm::setPackagePermission, vm::getAppInfo, vm.appsWithPermissions,
                vm::getAppsWithPermissions, vm::clearPackagePermissions, vm.chosenPackage,
                ::choosePackage, ::navigateUp
            )
        }
        composable<ManageAppGroups> {
            ManageAppGroupsScreen(
                vm.appGroups, vm.autoAppGroups, vm::refreshAutoAppGroups,
                vm::exportAppGroups, vm::importAppGroups,
                { id, name, apps -> navController.navigate(EditAppGroup(id, name, apps)) },
                ::navigateUp
            )
        }
        composable<EditAppGroup> {
            EditAppGroupScreen(
                it.toRoute(), vm::getAppInfo, ::navigateUp, vm::setAppGroup,
                vm::deleteAppGroup, ::choosePackage, vm.chosenPackage
            )
        }

        composable<UserRestriction> {
            UserRestrictionScreen(vm::getUserRestrictions, ::navigateUp, ::navigate)
        }
        composable<UserRestrictionEditor> {
            UserRestrictionEditorScreen(vm.userRestrictions, vm::setUserRestriction, ::navigateUp)
        }
        composable<UserRestrictionOptions> {
            UserRestrictionOptionsScreen(it.toRoute(), vm.userRestrictions,
                vm::setUserRestriction, vm::createUserRestrictionShortcut, ::navigateUp)
        }

        composable<PolicyToggles> {
            PolicyTogglesScreen(vm.policyToggles, vm::getPolicyToggles, vm::switchPolicyToggle,
                vm::createPolicyToggleShortcut, restricted, ::navigateUp) { navigate(EditPolicyToggle(it)) }
        }
        composable<EditPolicyToggle> {
            EditPolicyToggleScreen(it.toRoute(), vm.policyToggles, vm::setPolicyToggle,
                vm::deletePolicyToggle, vm.chosenPackage, ::chooseSinglePackage, ::navigateUp)
        }

        composable<Users> { UsersScreen(vm, ::navigateUp, ::navigate) }
        composable<UserInfo> { UserInfoScreen(vm::getUserInformation, ::navigateUp) }
        composable<UsersOptions> {
            UsersOptionsScreen(vm::getLogoutEnabled, vm::setLogoutEnabled, ::navigateUp)
        }
        composable<UserOperation> {
            UserOperationScreen(vm::getUserIdentifiers, vm::doUserOperation,
                vm::createUserOperationShortcut, ::navigateUp)
        }
        composable<CreateUser> { CreateUserScreen(vm::createUser, ::navigateUp) }
        composable<ChangeUsername> { ChangeUsernameScreen(vm::setProfileName, ::navigateUp) }
        composable<UserSessionMessage> {
            UserSessionMessageScreen(vm::getUserSessionMessages, vm::setStartUserSessionMessage,
                vm::setEndUserSessionMessage, ::navigateUp)
        }
        composable<AffiliationId> {
            AffiliationIdScreen(vm.affiliationIds, vm::getAffiliationIds, vm::setAffiliationId,
                ::navigateUp)
        }

        composable<Password> { PasswordScreen(vm, ::navigateUp, ::navigate) }
        composable<PasswordInfo> {
            PasswordInfoScreen(vm::getPasswordComplexity, vm::isPasswordComplexitySufficient,
                vm::isUsingUnifiedPassword, ::navigateUp)
        }
        composable<ResetPasswordToken> {
            ResetPasswordTokenScreen(vm::getRpTokenState, vm::setRpToken,
                vm::createActivateRpTokenIntent, vm::clearRpToken, ::navigateUp)
        }
        composable<ResetPassword> { ResetPasswordScreen(vm::resetPassword, ::navigateUp) }
        composable<RequiredPasswordComplexity> {
            RequiredPasswordComplexityScreen(vm::getRequiredPasswordComplexity,
                vm::setRequiredPasswordComplexity, ::navigateUp)
        }
        composable<KeyguardDisabledFeatures> {
            KeyguardDisabledFeaturesScreen(vm::getKeyguardDisableConfig,
                vm::setKeyguardDisableConfig, ::navigateUp)
        }
        composable<RequiredPasswordQuality> { RequiredPasswordQualityScreen(::navigateUp) }

        composable<Settings> { SettingsScreen(restricted, ::navigateUp, ::navigate) }
        composable<SettingsOptions> {
            SettingsOptionsScreen(vm::getDisplayDangerousFeatures, vm::getShortcutsEnabled,
                vm::setDisplayDangerousFeatures, vm::setShortcutsEnabled, ::navigateUp)
        }
        composable<Appearance> {
            AppearanceScreen(::navigateUp, vm.theme, vm::changeTheme)
        }
        composable<AppLockSettings> {
            AppLockSettingsScreen(vm.getAppLockConfig(), vm::setAppLockConfig, ::navigateUp)
        }
        composable<ApiSettings> {
            ApiSettings(vm::getApiEnabled, vm::setApiKey, ::navigateUp)
        }
        composable<Notifications> {
            NotificationsScreen(vm.enabledNotifications, vm::getEnabledNotifications,
                vm::setNotificationEnabled, ::navigateUp)
        }
        composable<About> { AboutScreen(::navigateUp) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                (event == Lifecycle.Event.ON_CREATE && !SP.lockPasswordHash.isNullOrEmpty()) ||
                (event == Lifecycle.Event.ON_RESUME && SP.lockWhenLeaving)
            ) {
                onLock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(Unit) {
        val profileNotActivated = !SP.managedProfileActivated && Privilege.status.value.work
        if(profileNotActivated) {
            Privilege.DPM.setProfileEnabled(Privilege.DAR)
            SP.managedProfileActivated = true
            context.popToast(R.string.work_profile_activated)
        }
    }
    DhizukuErrorDialog {
        dhizukuErrorStatus.value = 0
        Privilege.updateStatus()
        navController.navigate(WorkModes(false)) {
            popUpTo<Home> { inclusive = true }
            launchSingleTop = true
        }
    }
}

@Serializable private object Home

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(restricted: Boolean, onNavigate: (Any) -> Unit, onLock: () -> Unit) {
    val privilege by Privilege.status.collectAsStateWithLifecycle()
    val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        Modifier.nestedScroll(sb.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                {
                    Text(stringResource(
                        if (restricted) R.string.app_name_user_profile else R.string.app_name
                    ))
                },
                actions = {
                    if (restricted) {
                        IconButton(onLock) { Icon(painterResource(R.drawable.lock_fill0), null) }
                    } else {
                        IconButton({ onNavigate(WorkModes(true)) }) { Icon(painterResource(R.drawable.security_fill0), null) }
                    }
                    IconButton({ onNavigate(Settings) }) { Icon(Icons.Default.Settings, null) }
                },
                scrollBehavior = sb
            )
        },
        contentWindowInsets = adaptiveInsets()
    ) {
        Column(Modifier
            .fillMaxSize()
            .padding(it)
            .verticalScroll(rememberScrollState())) {
            if(privilege.device || privilege.profile) {
                HomePageItem(R.string.system, R.drawable.android_fill0) { onNavigate(SystemManager) }
                HomePageItem(R.string.network, R.drawable.wifi_fill0) { onNavigate(Network) }
            }
            if(privilege.work) {
                HomePageItem(R.string.work_profile, R.drawable.work_fill0) {
                    onNavigate(WorkProfile)
                }
            }
            if(privilege.device || privilege.profile) {
                HomePageItem(R.string.applications, R.drawable.apps_fill0) {
                    onNavigate(ApplicationsFeatures)
                }
                if(VERSION.SDK_INT >= 24) {
                    HomePageItem(R.string.user_restriction, R.drawable.person_off) { onNavigate(UserRestriction) }
                }
                HomePageItem(R.string.mode_switches, R.drawable.toggle_off_fill0) { onNavigate(PolicyToggles) }
                HomePageItem(R.string.users,R.drawable.manage_accounts_fill0) { onNavigate(Users) }
                HomePageItem(R.string.password_and_keyguard, R.drawable.password_fill0) { onNavigate(Password) }
            }
            Spacer(Modifier.height(BottomPadding))
        }
    }
}

@Composable
fun HomePageItem(name: Int, imgVector: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.padding(start = 30.dp))
        Icon(
            painter = painterResource(imgVector),
            contentDescription = null
        )
        Spacer(Modifier.padding(start = 15.dp))
        Text(
            text = stringResource(name),
            style = typography.headlineSmall,
            modifier = Modifier.padding(bottom = if(zhCN) { 2 } else { 0 }.dp)
        )
    }
}

@Composable
private fun DhizukuErrorDialog(onClose: () -> Unit) {
    val status by dhizukuErrorStatus.collectAsState()
    if (status != 0) {
        LaunchedEffect(Unit) {
            SP.dhizuku = false
        }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClose) {
                    Text(stringResource(R.string.confirm))
                }
            },
            title = { Text(stringResource(R.string.dhizuku)) },
            text = {
                val text = stringResource(
                    when(status){
                        1 -> R.string.failed_to_init_dhizuku
                        2 -> R.string.dhizuku_permission_not_granted
                        else -> R.string.failed_to_init_dhizuku
                    }
                )
                Text(text)
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }
}
