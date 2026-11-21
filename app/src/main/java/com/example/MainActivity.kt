package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DownloadStatus
import com.example.ui.AppTab
import com.example.ui.DownloadManagerViewModel
import com.example.ui.components.PdmTopHeader
import com.example.ui.dialogs.AddDownloadSheet
import com.example.ui.dialogs.DownloadDetailSheet
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransfersScreen
import com.example.ui.theme.PdmBrandCoral
import com.example.ui.theme.PdmTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            PdmApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            viewModel.onIncomingSharedText(sharedText)
        }
    }
}

private fun tabIcons(tab: AppTab): Pair<ImageVector, ImageVector> = when (tab) {
    AppTab.TRANSFERS -> Icons.Filled.Download to Icons.Outlined.Download
    AppTab.CATEGORIES -> Icons.Filled.GridView to Icons.Outlined.GridView
    AppTab.SETTINGS -> Icons.Filled.Settings to Icons.Outlined.Settings
}

@Composable
fun PdmApp(viewModel: DownloadManagerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val probeState by viewModel.probeState.collectAsStateWithLifecycle()
    val inspectedDownload by viewModel.inspectedDownload.collectAsStateWithLifecycle()
    val inspectedSegments by viewModel.inspectedSegments.collectAsStateWithLifecycle()
    val checksumState by viewModel.checksumState.collectAsStateWithLifecycle()
    val pendingSharedUrl by viewModel.pendingSharedUrl.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.TRANSFERS) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var addSheetInitialUrl by remember { mutableStateOf("") }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(pendingSharedUrl) {
        val incoming = pendingSharedUrl ?: return@LaunchedEffect
        addSheetInitialUrl = incoming
        showAddSheet = true
        viewModel.consumePendingSharedUrl()
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = currentTab != AppTab.TRANSFERS || isSearchOpen) {
        if (isSearchOpen) {
            isSearchOpen = false
            viewModel.setSearchQuery("")
        } else {
            currentTab = AppTab.TRANSFERS
        }
    }

    PdmTheme(themePreference = uiState.settings.themePreference) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedWidth = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    PdmTopHeader(
                        activeCount = uiState.activeCount,
                        aggregateSpeedBps = uiState.aggregateSpeedBps,
                        showSearchAction = currentTab == AppTab.TRANSFERS,
                        isSearchOpen = isSearchOpen,
                        onToggleSearch = {
                            if (isSearchOpen) viewModel.setSearchQuery("")
                            isSearchOpen = !isSearchOpen
                        }
                    )
                },
                bottomBar = {
                    if (!isExpandedWidth) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                val (filled, outlined) = tabIcons(tab)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) filled else outlined,
                                            contentDescription = tab.labelFa
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.labelFa,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (currentTab == AppTab.TRANSFERS) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                addSheetInitialUrl = ""
                                showAddSheet = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = PdmBrandCoral,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "افزودن لینک دانلود جدید"
                                )
                            },
                            text = { Text("دانلود جدید") },
                            modifier = Modifier.testTag("fab_add_download")
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedWidth) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                val (filled, outlined) = tabIcons(tab)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) filled else outlined,
                                            contentDescription = tab.labelFa
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.labelFa,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 840.dp)
                        ) {
                            when (currentTab) {
                                AppTab.TRANSFERS -> TransfersScreen(
                                    uiState = uiState,
                                    isSearchOpen = isSearchOpen,
                                    onSearchChange = viewModel::setSearchQuery,
                                    onStatusFilterChange = viewModel::setStatusFilter,
                                    onCategoryFilterChange = viewModel::setCategoryFilter,
                                    onSortChange = viewModel::setSortOption,
                                    onPauseAll = viewModel::pauseAll,
                                    onResumeAll = viewModel::resumeAll,
                                    onClearCompleted = viewModel::clearCompleted,
                                    onTogglePauseResume = { item ->
                                        val running = item.statusEnum == DownloadStatus.DOWNLOADING ||
                                            item.statusEnum == DownloadStatus.CONNECTING
                                        if (running) viewModel.pause(item.id)
                                        else viewModel.startOrResume(item.id)
                                    },
                                    onInspectDownload = { item -> viewModel.openInspector(item.id) },
                                    onOpenFile = { item ->
                                        val intent = viewModel.createOpenFileIntent(item)
                                        if (intent != null) {
                                            runCatching { context.startActivity(intent) }
                                                .onFailure {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("برنامه‌ای برای باز کردن این فایل پیدا نشد.")
                                                    }
                                                }
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("فایل روی حافظه پیدا نشد.")
                                            }
                                        }
                                    },
                                    onAddNewClick = {
                                        addSheetInitialUrl = ""
                                        showAddSheet = true
                                    }
                                )

                                AppTab.CATEGORIES -> CategoriesScreen(
                                    uiState = uiState,
                                    onOpenCategory = { catId ->
                                        viewModel.setCategoryFilter(catId)
                                        currentTab = AppTab.TRANSFERS
                                    },
                                    onAddCustomCategory = viewModel::addCustomCategory,
                                    onDeleteCustomCategory = viewModel::deleteCustomCategory
                                )

                                AppTab.SETTINGS -> {
                                    val nightQueue = uiState.queues.firstOrNull { !it.isDefault }
                                    SettingsScreen(
                                        settings = uiState.settings,
                                        nightQueue = nightQueue,
                                        storageInfo = uiState.storageInfo,
                                        onUpdateTheme = viewModel::updateTheme,
                                        onUpdateDefaultThreads = viewModel::updateDefaultThreads,
                                        onUpdateMaxConcurrent = viewModel::updateMaxConcurrent,
                                        onUpdateGlobalSpeedLimit = viewModel::updateGlobalSpeedLimit,
                                        onUpdateWifiOnly = viewModel::updateWifiOnly,
                                        onUpdateLowBatteryPause = viewModel::updateLowBatteryPause,
                                        onUpdateAutoRetryCount = viewModel::updateAutoRetryCount,
                                        onUpdateAutoExportPublic = viewModel::updateAutoExportPublic,
                                        onUpdateUserAgent = viewModel::updateCustomUserAgent,
                                        onUpdateProxy = viewModel::updateProxy,
                                        onUpdateNightQueue = viewModel::updateQueue,
                                        onCleanOrphanedFiles = viewModel::cleanOrphanedFiles
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showAddSheet) {
                AddDownloadSheet(
                    initialUrl = addSheetInitialUrl,
                    categories = uiState.categories,
                    queues = uiState.queues,
                    defaultThreadCount = uiState.settings.defaultThreadCount,
                    probeState = probeState,
                    onProbeUrl = viewModel::probeUrl,
                    onClearProbe = viewModel::clearProbe,
                    onDismiss = { showAddSheet = false },
                    onSubmitSingle = { url, name, catId, queueId, prio, threads, limit, ref, ua, auth, startNow, pSize, pMime, pResume, pEtag ->
                        viewModel.enqueueSingleDownload(
                            url = url,
                            fileName = name,
                            categoryId = catId,
                            queueId = queueId,
                            priority = prio,
                            threadCount = threads,
                            speedLimitKbps = limit,
                            referer = ref,
                            customUserAgent = ua,
                            authHeader = auth,
                            startImmediately = startNow,
                            probedSize = pSize,
                            probedMime = pMime,
                            probedSupportsResume = pResume,
                            probedEtag = pEtag
                        )
                    },
                    onSubmitBatch = { lines, queueId, prio, threads, startNow ->
                        viewModel.enqueueBatchUrls(
                            rawLines = lines,
                            queueId = queueId,
                            priority = prio,
                            threadCount = threads,
                            startImmediately = startNow
                        )
                    }
                )
            }

            inspectedDownload?.let { target ->
                val catName = uiState.categories.firstOrNull { it.id == target.categoryId }?.nameFa
                    ?: "سایر"
                DownloadDetailSheet(
                    download = target,
                    categoryName = catName,
                    segments = inspectedSegments,
                    telemetry = uiState.telemetry[target.id],
                    checksumState = checksumState?.takeIf { it.downloadId == target.id },
                    onDismiss = viewModel::closeInspector,
                    onTogglePauseResume = {
                        val running = target.statusEnum == DownloadStatus.DOWNLOADING ||
                            target.statusEnum == DownloadStatus.CONNECTING
                        if (running) viewModel.pause(target.id)
                        else viewModel.startOrResume(target.id)
                    },
                    onOpenFile = {
                        val intent = viewModel.createOpenFileIntent(target)
                        if (intent != null) {
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("برنامه‌ای برای باز کردن این فایل پیدا نشد.")
                                    }
                                }
                        }
                    },
                    onShareFile = {
                        val intent = viewModel.createShareFileIntent(target)
                        if (intent != null) {
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل"))
                            }
                        }
                    },
                    onExportPublic = { viewModel.exportToPublicDownloads(target) },
                    onCalculateChecksum = { viewModel.calculateChecksums(target) },
                    onVerifyExpectedHash = { hash -> viewModel.verifyAgainstExpectedHash(target, hash) },
                    onUpdateExpiredLink = { newUrl -> viewModel.updateExpiredLink(target.id, newUrl) },
                    onDelete = { deleteFile -> viewModel.deleteDownload(target.id, deleteFile) },
                    onCopySuccess = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )
            }
        }
    }
}
