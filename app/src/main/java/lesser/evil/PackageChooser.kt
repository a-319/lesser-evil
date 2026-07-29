package lesser.evil

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import lesser.evil.dpm.AppGroup
import lesser.evil.dpm.AppGroupsList
import lesser.evil.dpm.AutoAppGroup

data class AppInfo(
    val name: String,
    val label: String,
    val icon: Drawable,
    val flags: Int,
    /** True when the app has a launcher entry, i.e. the user can see and open it */
    val launchable: Boolean = false
)

private fun searchInString(query: String, content: String)
    = query.split(' ').all { content.contains(it, true) }

/** The tabs the application list can be filtered by */
private enum class AppChooserTab(val title: Int) {
    User(R.string.apps_tab_user),
    Visual(R.string.apps_tab_visual),
    Background(R.string.apps_tab_background),
    All(R.string.apps_tab_all),
    Groups(R.string.apps_tab_groups)
}

@Serializable data class ApplicationsList(val canSwitchView: Boolean, val multiSelect: Boolean)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppChooserScreen(
    params: ApplicationsList, packageList: MutableStateFlow<List<AppInfo>>,
    refreshProgress: MutableStateFlow<Float>, onChoosePackage: (String?) -> Unit,
    onSwitchView: () -> Unit, onRefresh: () -> Unit,
    appGroups: StateFlow<List<AppGroup>>, autoAppGroups: StateFlow<List<AutoAppGroup>>,
    onRefreshAutoGroups: () -> Unit, onEditGroup: (Int?, String, List<String>) -> Unit
) {
    val packages by packageList.collectAsStateWithLifecycle()
    val hf = LocalHapticFeedback.current
    val progress by refreshProgress.collectAsStateWithLifecycle()
    val groups by appGroups.collectAsStateWithLifecycle()
    val autoGroups by autoAppGroups.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var gridView by rememberSaveable { mutableStateOf(SP.applicationsGridView) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    val tab = AppChooserTab.entries[tabIndex]
    val filteredPackages = packages.filter {
        val inTab = when(tab) {
            AppChooserTab.User -> it.flags and ApplicationInfo.FLAG_SYSTEM == 0
            AppChooserTab.Visual -> it.launchable
            AppChooserTab.Background -> !it.launchable
            AppChooserTab.All -> true
            AppChooserTab.Groups -> false
        }
        inTab && (query.isEmpty() || searchInString(query, it.label) || searchInString(query, it.name))
    }
    val selectedPackages = remember { mutableStateListOf<AppInfo>() }
    /** Selected groups, by the key the group list identifies them with, with their packages */
    val selectedGroups = remember { mutableStateMapOf<String, List<String>>() }
    val selection = (selectedPackages.map { it.name } + selectedGroups.values.flatten()).distinct()
    val focusMgr = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if(packages.size <= 1) onRefresh()
    }
    LaunchedEffect(tab) {
        if(tab == AppChooserTab.Groups) onRefreshAutoGroups()
    }
    fun onItemClick(app: AppInfo) {
        if (selection.isEmpty()) {
            focusMgr.clearFocus()
            onChoosePackage(app.name)
        } else {
            if (app in selectedPackages) selectedPackages -= app
            else selectedPackages += app
        }
    }
    fun onItemLongClick(app: AppInfo) {
        if (params.multiSelect && app !in selectedPackages) {
            selectedPackages += app
            hf.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    fun onGroupSelect(key: String, apps: List<String>) {
        if (key in selectedGroups) selectedGroups -= key else selectedGroups[key] = apps
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    actions = {
                        val appsShown = tab != AppChooserTab.Groups
                        if(!searchMode) {
                            if (appsShown) {
                                IconButton({
                                    gridView = !gridView
                                    SP.applicationsGridView = gridView
                                }) {
                                    Icon(
                                        painterResource(if(gridView) R.drawable.list_lines_fill0 else R.drawable.apps_fill0),
                                        stringResource(if(gridView) R.string.list_view else R.string.grid_view)
                                    )
                                }
                                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                                IconButton({ searchMode = true }) {
                                    Icon(painter = painterResource(R.drawable.search_fill0), contentDescription = stringResource(R.string.search))
                                }
                            }
                            if (selection.isEmpty()) {
                                IconButton(onRefresh, enabled = progress == 1F) {
                                    Icon(Icons.Default.Refresh, null)
                                }
                                if (params.canSwitchView) IconButton(onSwitchView) {
                                    Icon(Icons.AutoMirrored.Default.List, null)
                                }
                            }
                        }
                        if (selection.isNotEmpty()) {
                            FilledIconButton({ onChoosePackage(selection.joinToString("\n")) }) {
                                Icon(Icons.Default.Check, null)
                            }
                        }
                    },
                    title = {
                        if (searchMode) {
                            val fr = remember { FocusRequester() }
                            LaunchedEffect(Unit) { fr.requestFocus() }
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions { focusMgr.clearFocus() },
                                placeholder = { Text(stringResource(R.string.search)) },
                                trailingIcon = {
                                    IconButton({
                                        query = ""
                                        searchMode = false
                                    }) {
                                        Icon(Icons.Outlined.Clear, null)
                                    }
                                },
                                textStyle = typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().focusRequester(fr)
                            )
                        } else {
                            if (selection.isNotEmpty()) {
                                Text(selection.size.toString())
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton({ onChoosePackage(null) }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.surfaceContainer)
                )
                ScrollableTabRow(
                    tabIndex, containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    edgePadding = 0.dp
                ) {
                    AppChooserTab.entries.forEachIndexed { index, entry ->
                        Tab(
                            index == tabIndex,
                            {
                                tabIndex = index
                                if (entry == AppChooserTab.Groups) searchMode = false
                            },
                            text = { Text(stringResource(entry.title)) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = adaptiveInsets()
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            if (progress < 1F) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
            if (tab == AppChooserTab.Groups) {
                Box(Modifier.fillMaxSize()) {
                    AppGroupsList(
                        groups, autoGroups, onEditGroup, Modifier.fillMaxSize(),
                        selectedGroups.keys.toList(),
                        if (params.multiSelect) ::onGroupSelect else null
                    )
                    FloatingActionButton(
                        { onEditGroup(null, "", emptyList()) },
                        Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                    }
                }
            } else {
                if (gridView) LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.fillMaxSize()) {
                    gridItems(filteredPackages, { it.name }) {
                        AppGridItem(
                            it, Modifier
                                .animateItem()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (it in selectedPackages) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.background
                                )
                                .combinedClickable(
                                    onLongClick = { onItemLongClick(it) },
                                    onClick = { onItemClick(it) }
                                )
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(BottomPadding)) }
                } else LazyColumn(Modifier.fillMaxSize()) {
                    items(filteredPackages, { it.name }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onLongClick = { onItemLongClick(it) },
                                    onClick = { onItemClick(it) }
                                )
                                .background(
                                    if (it in selectedPackages) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.background
                                )
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .animateItem()
                        ) {
                            Image(
                                painter = rememberDrawablePainter(it.icon), contentDescription = null,
                                modifier = Modifier.padding(start = 12.dp, end = 18.dp).size(40.dp)
                            )
                            Column {
                                Text(text = it.label, style = typography.titleLarge)
                                Text(text = it.name, modifier = Modifier.alpha(0.8F))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(BottomPadding)) }
                }
            }
        }
    }
}

/** A launcher-like application item: only the icon and the label, without the package name */
@Composable
private fun AppGridItem(info: AppInfo, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(rememberDrawablePainter(info.icon), null, Modifier.size(48.dp))
        Text(
            info.label, Modifier.padding(top = 6.dp), style = typography.bodyMedium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

val getInstalledAppsFlags =
    if(Build.VERSION.SDK_INT >= 24) PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES else 0
