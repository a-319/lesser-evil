package lesser.evil.dpm

import android.os.Build.VERSION
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lesser.evil.BottomPadding
import lesser.evil.HorizontalPadding
import lesser.evil.PolicyToggle
import lesser.evil.PolicyToggleManager
import lesser.evil.R
import lesser.evil.TogglePolicy
import lesser.evil.UserRestrictionsRepository
import lesser.evil.adaptiveInsets
import lesser.evil.parsePackageNames
import lesser.evil.popToast
import lesser.evil.showOperationResultToast
import lesser.evil.ui.FullWidthCheckBoxItem
import lesser.evil.ui.FullWidthRadioButtonItem
import lesser.evil.ui.NavIcon
import lesser.evil.ui.Notes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable object PolicyToggles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyTogglesScreen(
    toggles: StateFlow<List<PolicyToggle>>, getToggles: () -> Unit,
    onSwitch: (Int, Boolean) -> Boolean, onCreateShortcut: (Int) -> Boolean,
    restricted: Boolean, onNavigateUp: () -> Unit, onEdit: (Int) -> Unit
) {
    val context = LocalContext.current
    val allToggles by toggles.collectAsStateWithLifecycle()
    val list = if (restricted) allToggles.filter { it.userAllowed } else allToggles
    LaunchedEffect(Unit) { getToggles() }
    Scaffold(
        topBar = {
            TopAppBar(
                { Text(stringResource(R.string.mode_switches)) },
                navigationIcon = { NavIcon(onNavigateUp) }
            )
        },
        floatingActionButton = {
            if (!restricted) FloatingActionButton({ onEdit(-1) }) {
                Icon(Icons.Default.Add, null)
            }
        },
        contentWindowInsets = adaptiveInsets()
    ) { paddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(paddingValues)) {
            items(list, { it.id }) { toggle ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            enabled = !restricted,
                            onClick = { onEdit(toggle.id) },
                            onLongClick = {
                                if (!onCreateShortcut(toggle.id)) context.popToast(R.string.unsupported)
                            }
                        )
                        .padding(HorizontalPadding, 8.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1F)) {
                        Text(toggle.name, style = typography.titleMedium)
                        Text(
                            stringResource(R.string.n_policies, toggle.policies.size),
                            style = typography.bodyMedium,
                            color = colorScheme.onBackground.copy(alpha = 0.8F)
                        )
                    }
                    Switch(
                        toggle.enabled,
                        { context.showOperationResultToast(onSwitch(toggle.id, it)) },
                        Modifier.padding(start = 8.dp)
                    )
                }
            }
            item {
                Column(Modifier.padding(HorizontalPadding, 8.dp)) {
                    Notes(R.string.mode_switches_note)
                }
                Spacer(Modifier.height(BottomPadding))
            }
        }
    }
}

@Serializable data class EditPolicyToggle(val id: Int = -1)

private enum class TogglePolicyType(val label: Int, val requiresApi: Int) {
    UserRestriction(R.string.user_restriction, 21),
    HideApp(R.string.hide, 21),
    SuspendApp(R.string.suspend, 24),
    AlwaysOnVpn(R.string.always_on_vpn, 24),
    BlockMeteredData(R.string.disable_metered_data, 28)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPolicyToggleScreen(
    params: EditPolicyToggle, toggles: StateFlow<List<PolicyToggle>>,
    onSave: (Int?, String, List<TogglePolicy>, Boolean) -> Boolean, onDelete: (Int) -> Unit,
    chosenPackage: Channel<String>, onChoosePackage: () -> Unit, onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val initial = remember { toggles.value.find { it.id == params.id } }
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var userAllowed by rememberSaveable { mutableStateOf(initial?.userAllowed == true) }
    val policies = rememberSaveable(saver = listSaver(
        save = { list -> list.map { PolicyToggleManager.json.encodeToString<TogglePolicy>(it) } },
        restore = { saved ->
            saved.map { PolicyToggleManager.json.decodeFromString<TogglePolicy>(it) }.toMutableStateList()
        }
    )) { (initial?.policies ?: emptyList()).toMutableStateList() }
    var type by rememberSaveable { mutableStateOf(TogglePolicyType.UserRestriction) }
    var restrictionId by rememberSaveable { mutableStateOf("") }
    var packageInput by rememberSaveable { mutableStateOf("") }
    var lockdown by rememberSaveable { mutableStateOf(false) }
    var excludedInput by rememberSaveable { mutableStateOf("") }
    var blockedWhenOn by rememberSaveable { mutableStateOf(true) }
    var restrictionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val pkg = chosenPackage.receive()
        if (type == TogglePolicyType.BlockMeteredData) {
            excludedInput = (excludedInput.trim() + "\n" + pkg).trim()
        } else {
            packageInput = pkg
        }
    }
    fun buildPolicy(): TogglePolicy? = when (type) {
        TogglePolicyType.UserRestriction ->
            if (restrictionId.isNotBlank()) {
                TogglePolicy.UserRestriction(restrictionId.trim(), blockedWhenOn)
            } else null
        TogglePolicyType.HideApp ->
            if (packageInput.isValidPackageName) {
                TogglePolicy.HideApp(packageInput.trim(), blockedWhenOn)
            } else null
        TogglePolicyType.SuspendApp ->
            if (packageInput.isValidPackageName) {
                TogglePolicy.SuspendApp(packageInput.trim(), blockedWhenOn)
            } else null
        TogglePolicyType.AlwaysOnVpn ->
            if (packageInput.isValidPackageName) {
                TogglePolicy.AlwaysOnVpn(packageInput.trim(), lockdown, blockedWhenOn)
            } else null
        TogglePolicyType.BlockMeteredData ->
            TogglePolicy.BlockMeteredData(parsePackageNames(excludedInput), blockedWhenOn)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                { Text(stringResource(if (params.id == -1) R.string.add else R.string.edit)) },
                navigationIcon = { NavIcon(onNavigateUp) },
                actions = {
                    if (params.id != -1) IconButton({
                        onDelete(params.id)
                        onNavigateUp()
                    }) {
                        Icon(Icons.Outlined.Delete, null)
                    }
                    IconButton(
                        {
                            context.showOperationResultToast(
                                onSave(params.id.takeIf { it != -1 }, name, policies, userAllowed)
                            )
                            onNavigateUp()
                        },
                        enabled = name.isNotBlank() && policies.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
        },
        contentWindowInsets = adaptiveInsets()
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HorizontalPadding)
        ) {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                label = { Text(stringResource(R.string.name)) }
            )
            FullWidthCheckBoxItem(R.string.available_to_user_profile, userAllowed) {
                userAllowed = it
            }
            policies.forEachIndexed { index, policy ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1F)) {
                        Text(policyTitle(policy), style = typography.titleMedium)
                        Text(
                            stringResource(
                                if (policy.blockedWhenOn) R.string.blocked_while_on
                                else R.string.released_while_on
                            ),
                            style = typography.bodyMedium,
                            color = colorScheme.onBackground.copy(alpha = 0.8F)
                        )
                    }
                    IconButton({ policies.removeAt(index) }) {
                        Icon(Icons.Outlined.Delete, null)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.add_policy), style = typography.titleMedium)
            TogglePolicyType.entries.filter { VERSION.SDK_INT >= it.requiresApi }.forEach {
                FullWidthRadioButtonItem(it.label, type == it) { type = it }
            }
            Spacer(Modifier.padding(vertical = 2.dp))
            when (type) {
                TogglePolicyType.UserRestriction -> {
                    val restriction = UserRestrictionsRepository.findRestrictionOrNull(restrictionId)
                    OutlinedTextField(
                        restrictionId, { restrictionId = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.user_restriction)) },
                        supportingText = if (restriction != null) {
                            { Text(stringResource(restriction.name)) }
                        } else null,
                        trailingIcon = {
                            IconButton({ restrictionDialog = true }) {
                                Icon(Icons.AutoMirrored.Default.List, null)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done
                        )
                    )
                }
                TogglePolicyType.HideApp, TogglePolicyType.SuspendApp -> {
                    PackageNameTextField(packageInput, onChoosePackage) { packageInput = it }
                }
                TogglePolicyType.AlwaysOnVpn -> {
                    PackageNameTextField(packageInput, onChoosePackage) { packageInput = it }
                    FullWidthCheckBoxItem(R.string.enable_lockdown, lockdown) { lockdown = it }
                }
                TogglePolicyType.BlockMeteredData -> {
                    Notes(R.string.block_metered_data_note)
                    Spacer(Modifier.padding(vertical = 2.dp))
                    OutlinedTextField(
                        excludedInput, { excludedInput = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.excluded_packages)) },
                        minLines = 2,
                        trailingIcon = {
                            IconButton(onChoosePackage) {
                                Icon(Icons.AutoMirrored.Default.List, null)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                }
            }
            Spacer(Modifier.padding(vertical = 2.dp))
            Text(stringResource(R.string.while_the_switch_is_on), style = typography.titleSmall)
            FullWidthRadioButtonItem(R.string.blocked_while_on, blockedWhenOn) {
                blockedWhenOn = true
            }
            FullWidthRadioButtonItem(R.string.released_while_on, !blockedWhenOn) {
                blockedWhenOn = false
            }
            Notes(R.string.switch_off_restores_note)
            Spacer(Modifier.padding(vertical = 2.dp))
            Button(
                {
                    buildPolicy()?.let { policies += it }
                    restrictionId = ""
                    packageInput = ""
                    lockdown = false
                    excludedInput = ""
                },
                Modifier.fillMaxWidth(),
                enabled = buildPolicy() != null
            ) {
                Text(stringResource(R.string.add))
            }
            Spacer(Modifier.height(BottomPadding))
        }
    }
    if (restrictionDialog) AlertDialog(
        onDismissRequest = { restrictionDialog = false },
        confirmButton = {
            TextButton({ restrictionDialog = false }) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.user_restriction)) },
        text = {
            val allRestrictions = remember { UserRestrictionsRepository.getAllRestrictions() }
            LazyColumn {
                items(allRestrictions, { it.id }) { restriction ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                restrictionId = restriction.id
                                restrictionDialog = false
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(stringResource(restriction.name), style = typography.titleSmall)
                        Text(
                            restriction.id, Modifier.alpha(0.8F),
                            style = typography.bodySmall
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun policyTitle(policy: TogglePolicy): String = when (policy) {
    is TogglePolicy.UserRestriction -> {
        val restriction = remember(policy.restriction) {
            UserRestrictionsRepository.findRestrictionOrNull(policy.restriction)
        }
        stringResource(R.string.user_restriction) + ": " +
                (restriction?.let { stringResource(it.name) } ?: policy.restriction)
    }
    is TogglePolicy.HideApp -> stringResource(R.string.hide) + ": " + policy.packageName
    is TogglePolicy.SuspendApp -> stringResource(R.string.suspend) + ": " + policy.packageName
    is TogglePolicy.AlwaysOnVpn -> stringResource(R.string.always_on_vpn) + ": " + policy.packageName
    is TogglePolicy.BlockMeteredData -> stringResource(R.string.disable_metered_data) + " (" +
            stringResource(R.string.excluded_packages) + ": " + policy.excludedPackages.size + ")"
}
