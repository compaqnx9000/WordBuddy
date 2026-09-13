package com.hotgis.wordbuddy.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hotgis.wordbuddy.auth.BiometricAuth
import com.hotgis.wordbuddy.data.Accent
import com.hotgis.wordbuddy.data.AppTheme
import com.hotgis.wordbuddy.data.Notebook
import com.hotgis.wordbuddy.data.VocabEntry
import com.hotgis.wordbuddy.data.WordHomophone
import com.hotgis.wordbuddy.ui.auth.BiometricUnlockScreen
import com.hotgis.wordbuddy.ui.auth.LoginScreen
import com.hotgis.wordbuddy.ui.card.CardModeScreen
import com.hotgis.wordbuddy.ui.components.MainBottomBar
import com.hotgis.wordbuddy.ui.components.MainTab
import com.hotgis.wordbuddy.ui.design.DesignScaleProvider
import com.hotgis.wordbuddy.ui.design.FoldableDualPaneRow
import com.hotgis.wordbuddy.ui.design.FoldableLayoutProvider
import com.hotgis.wordbuddy.ui.design.LocalFoldableLayout
import com.hotgis.wordbuddy.ui.design.foldableCenteredContent
import com.hotgis.wordbuddy.ui.design.hotWordsScreen
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.list.WordListScreen
import com.hotgis.wordbuddy.ui.lookup.LookupScreen
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.StellarTheme
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.hasStellarWallpaperBackground
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackgroundColor
import com.hotgis.wordbuddy.ui.profile.ProfileScreen
import com.hotgis.wordbuddy.ui.settings.AppSettingsScreen
import com.hotgis.wordbuddy.ui.theme.HotWordsTheme
import kotlinx.coroutines.delay

private enum class Overlay { None, Card, Settings }

@Composable
fun HotWordsRoot(
    viewModel: VocabViewModel,
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    var tab by remember { mutableStateOf(MainTab.Home) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var showAppSettings by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var loginHint by remember { mutableStateOf<String?>(null) }
    var pendingExit by remember { mutableStateOf(false) }
    var biometricUnlocked by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val words by viewModel.filteredWords.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val avatarBitmap by viewModel.avatarBitmap.collectAsStateWithLifecycle()
    val avatarBusy by viewModel.avatarBusy.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val alphabetLetterIndex by viewModel.alphabetLetterIndex.collectAsStateWithLifecycle()
    val pendingListScrollEntryId by viewModel.pendingListScrollEntryId.collectAsStateWithLifecycle()
    val favoriteRevision by viewModel.favoriteRevision.collectAsStateWithLifecycle()
    val homophones by viewModel.homophones.collectAsStateWithLifecycle()
    val activeNotebookName = viewModel.activeNotebook()?.name ?: Notebook.DEFAULT_NAME
    val activeWordCount = maxOf(viewModel.activeNotebook()?.wordCount ?: 0, words.size)
    val needsBiometricUnlock =
        session != null &&
            ui.settings.biometricLogin &&
            !biometricUnlocked &&
            // Password / SMS login already proved identity; only gate restored sessions.
            !showLogin

    fun requireLogin(hint: String): Boolean {
        if (session != null) return true
        loginHint = hint
        showLogin = true
        return false
    }

    fun promptBiometricUnlock() {
        val host = activity
        if (host == null) {
            biometricError = "无法启动指纹验证"
            return
        }
        biometricError = null
        BiometricAuth.authenticate(
            activity = host,
            onSuccess = {
                biometricUnlocked = true
                biometricError = null
            },
            onError = { message -> biometricError = message },
            onCancel = {},
        )
    }

    fun setBiometricLoginEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModel.updateSettings { it.copy(biometricLogin = false) }
            return
        }
        val host = activity
        if (host == null) {
            Toast.makeText(context, "无法启动指纹验证", Toast.LENGTH_SHORT).show()
            return
        }
        val blocked = BiometricAuth.statusMessage(context)
        if (blocked != null) {
            Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
            return
        }
        BiometricAuth.authenticate(
            activity = host,
            title = "开启指纹解锁",
            subtitle = "验证指纹后，下次打开应用将需要指纹解锁（账号密码登录成功后不会再要求）",
            onSuccess = {
                viewModel.updateSettings { it.copy(biometricLogin = true) }
                biometricUnlocked = true
                Toast.makeText(context, "已开启指纹解锁", Toast.LENGTH_SHORT).show()
            },
            onError = { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
        )
    }

    LaunchedEffect(session) {
        if (session != null) {
            val cameFromLogin = showLogin
            if (cameFromLogin) {
                // Interactive login (password / SMS) counts as unlocked for this process.
                biometricUnlocked = true
                biometricError = null
            }
            showLogin = false
            loginHint = null
        } else {
            biometricUnlocked = false
            biometricError = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sessionReplacedMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            showLogin = true
            loginHint = message
            tab = MainTab.Home
            overlay = Overlay.None
            showAppSettings = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.validateSessionNow()
        }
    }

    LaunchedEffect(needsBiometricUnlock) {
        if (needsBiometricUnlock) {
            promptBiometricUnlock()
        }
    }

    LaunchedEffect(tab, overlay) {
        if (tab != MainTab.Home || overlay != Overlay.None) {
            pendingExit = false
        }
        if (tab == MainTab.Notebook && overlay == Overlay.None) {
            viewModel.onNotebookTabOpened()
        }
    }

    LaunchedEffect(pendingExit) {
        if (pendingExit) {
            delay(2000)
            pendingExit = false
        }
    }

    BackHandler {
        when {
            needsBiometricUnlock -> onExit()
            showLogin -> {
                pendingExit = false
                showLogin = false
                loginHint = null
            }
            showAppSettings -> {
                pendingExit = false
                showAppSettings = false
            }
            overlay == Overlay.Settings -> {
                pendingExit = false
                overlay = Overlay.Card
            }
            overlay == Overlay.Card -> {
                pendingExit = false
                viewModel.prepareReturnToList()
                overlay = Overlay.None
            }
            tab == MainTab.Home -> {
                if (pendingExit) {
                    onExit()
                } else {
                    pendingExit = true
                    Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
            tab == MainTab.Notebook || tab == MainTab.Me -> {
                pendingExit = false
                tab = MainTab.Home
            }
        }
    }

    FoldableLayoutProvider(modifier = modifier) {
    DesignScaleProvider(fontScale = ui.settings.fontScale) {
        StellarTheme(style = ui.settings.accentStyle) {
        HotWordsTheme(
            appTheme = ui.settings.appTheme,
            accentStyle = ui.settings.accentStyle,
        ) {
            // Include Me: otherwise Scaffold uses a solid theme color behind MainBottomBar,
            // which reads as a separate opaque strip (unlike NotebookBottomBar drawn on wallpaper).
            val stellarChrome = showAppSettings ||
                overlay == Overlay.Card ||
                overlay == Overlay.Settings ||
                (overlay == Overlay.None && (
                    tab == MainTab.Home ||
                        tab == MainTab.Notebook ||
                        tab == MainTab.Me
                    ))
            StellarSystemBars(
                lightTheme = ui.settings.appTheme == AppTheme.Light,
            )
            if (showLogin) {
                LoginScreen(
                    modifier = Modifier.fillMaxSize(),
                    phone = login.phone,
                    code = login.code,
                    password = login.password,
                    passwordConfirm = login.passwordConfirm,
                    mode = login.mode,
                    needPassword = login.needPassword,
                    sending = login.sending,
                    loggingIn = login.loggingIn,
                    countdownSec = login.countdownSec,
                    error = login.error,
                    hint = loginHint,
                    onBack = {
                        showLogin = false
                        loginHint = null
                    },
                    onPhoneChange = viewModel::setLoginPhone,
                    onCodeChange = viewModel::setLoginCode,
                    onPasswordChange = viewModel::setLoginPassword,
                    onPasswordConfirmChange = viewModel::setLoginPasswordConfirm,
                    onModeChange = viewModel::setLoginMode,
                    onSendCode = viewModel::sendLoginCode,
                    onLogin = viewModel::submitLogin,
                )
                return@HotWordsTheme
            }
            if (needsBiometricUnlock) {
                BiometricUnlockScreen(
                    modifier = Modifier.fillMaxSize(),
                    phoneHint = session?.phone,
                    error = biometricError,
                    onUnlock = ::promptBiometricUnlock,
                    onLogout = {
                        biometricUnlocked = false
                        biometricError = null
                        viewModel.logout()
                    },
                )
                return@HotWordsTheme
            }
            val foldable = LocalFoldableLayout.current
            val useNotebookSplit = foldable.supportsDualPaneListCard && tab == MainTab.Notebook && overlay == Overlay.None
            Scaffold(
                // Paint wallpaper under bottomBar too — same continuous look as list/card bars.
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        when {
                            !stellarChrome -> Modifier
                            hasStellarWallpaperBackground() -> Modifier.stellarScreenBackground()
                            else -> Modifier.background(stellarScreenBackgroundColor())
                        },
                    ),
                containerColor = if (stellarChrome) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.background
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (overlay == Overlay.None && tab != MainTab.Notebook) {
                        MainBottomBar(
                            selected = tab,
                            onSelect = {
                                showAppSettings = false
                                tab = it
                            },
                            stellar = showAppSettings || tab == MainTab.Home || tab == MainTab.Me,
                        )
                    }
                },
            ) { padding ->
            if (showAppSettings) {
                AppSettingsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .hotWordsScreen(padding, consumeStatusBars = false),
                    settings = ui.settings,
                    notebooks = notebooks,
                    onBack = { showAppSettings = false },
                    onChange = viewModel::updateSettings,
                    loggedIn = session != null,
                    onBiometricLoginChange = ::setBiometricLoginEnabled,
                    onLogout = {
                        showAppSettings = false
                        viewModel.logout()
                    },
                    onSwitchAccount = {
                        showAppSettings = false
                        viewModel.logout()
                    },
                )
            } else when (overlay) {
                Overlay.Card -> {
                    HotWordsStudyCard(
                        modifier = Modifier
                            .fillMaxSize()
                            .hotWordsScreen(padding, consumeStatusBars = false)
                            .foldableCenteredContent(),
                        viewModel = viewModel,
                        ui = ui,
                        words = words,
                        homophones = homophones,
                        activeNotebookName = activeNotebookName,
                        onBack = {
                            viewModel.prepareReturnToList()
                            overlay = Overlay.None
                        },
                    )
                }

                Overlay.Settings -> {
                    AppSettingsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .hotWordsScreen(padding),
                        settings = ui.settings,
                        notebooks = notebooks,
                        onBack = { overlay = Overlay.Card },
                        onChange = viewModel::updateSettings,
                        loggedIn = session != null,
                        onBiometricLoginChange = ::setBiometricLoginEnabled,
                        onLogout = {
                            overlay = Overlay.Card
                            viewModel.logout()
                        },
                        onSwitchAccount = {
                            overlay = Overlay.Card
                            viewModel.logout()
                        },
                    )
                }

                Overlay.None -> when (tab) {
                    MainTab.Home -> {
                        LookupScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .hotWordsScreen(padding, consumeStatusBars = false)
                                .foldableCenteredContent(),
                            ui = ui,
                            wordCount = activeWordCount,
                            userName = ui.settings.displayName,
                            onToggleTheme = {
                                viewModel.updateSettings { settings ->
                                    settings.copy(
                                        appTheme = if (settings.appTheme == AppTheme.Light) {
                                            AppTheme.Dark
                                        } else {
                                            AppTheme.Light
                                        },
                                    )
                                }
                            },
                            onQuery = viewModel::setLookupQuery,
                            onSubmit = viewModel::submitLookup,
                            onToggleStar = {
                                if (requireLogin("收藏生词需要先登录或注册")) {
                                    viewModel.toggleStar()
                                }
                            },
                            onSpeak = viewModel::speak,
                            onSpeakText = viewModel::speakText,
                            onChangeAccent = { accent ->
                                viewModel.updateSettings { it.copy(accent = accent) }
                            },
                            onToggleRelatedStar = { entry ->
                                if (requireLogin("收藏生词需要先登录或注册")) {
                                    viewModel.toggleSaveRelatedWord(entry)
                                }
                            },
                            isRelatedWordSaved = viewModel::isWordSaved,
                            onPickLookupImage = viewModel::setLookupImage,
                            onGenerateAiForLookup = viewModel::generateAiForLookup,
                            onClearImageError = viewModel::clearImageError,
                            onUpdateDefinitions = viewModel::updateDefinitions,
                            homophones = homophones,
                            onLoadHomophones = viewModel::loadHomophones,
                            onSubmitHomophone = viewModel::submitHomophone,
                            onToggleHomophoneLike = viewModel::toggleHomophoneLike,
                            onLoadHomophoneLikers = { id, offset ->
                                viewModel.loadHomophoneLikers(id, offset)
                            },
                        )
                    }

                    MainTab.Notebook -> {
                        var openMoreRequest by remember { mutableIntStateOf(0) }
                        val previewInSplit: (Long) -> Unit = { id ->
                            viewModel.openCard(id = id, shuffled = false)
                        }
                        val onReciteWord: (Long?) -> Unit = { id ->
                            id?.let {
                                viewModel.openCard(id = it, shuffled = false)
                                if (!useNotebookSplit) {
                                    overlay = Overlay.Card
                                }
                            }
                        }
                        LaunchedEffect(useNotebookSplit, words.firstOrNull()?.id) {
                            if (useNotebookSplit && words.isNotEmpty() && viewModel.currentCard() == null) {
                                viewModel.openCard(id = words.first().id, shuffled = false)
                            }
                        }
                        val listContent: @Composable (Modifier, Boolean) -> Unit = { listModifier, splitBody ->
                            WordListScreen(
                                modifier = listModifier,
                                entries = words,
                                totalCount = activeWordCount,
                                ui = ui,
                                notebooks = notebooks,
                                activeNotebookName = activeNotebookName,
                                onSelectNotebook = viewModel::selectNotebook,
                                onCreateNotebookClick = {
                                    requireLogin("新建生词本需要先登录或注册")
                                },
                                onCreateNotebook = { name ->
                                    viewModel.createNotebook(name) { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDeleteNotebook = { id ->
                                    viewModel.deleteNotebook(id) { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onMoveEntries = { ids, targetId ->
                                    viewModel.moveEntriesToNotebook(ids, targetId) { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                wordCountInNotebook = viewModel::wordCountInNotebook,
                                onToggleHide = viewModel::toggleHideDefinitions,
                                onReveal = viewModel::toggleReveal,
                                onSpeak = viewModel::speak,
                                onDelete = viewModel::deleteWord,
                                onDeleteEntries = viewModel::deleteWords,
                                onReorder = viewModel::reorderWords,
                                onRecite = onReciteWord,
                                onBack = { tab = MainTab.Home },
                                onLoadMore = viewModel::loadMoreWords,
                                isWordFavorited = viewModel::isWordSaved,
                                favoriteRevision = favoriteRevision,
                                onToggleFavorite = { entry, onDone ->
                                    if (!requireLogin("收藏生词需要先登录或注册")) {
                                        onDone(null)
                                    } else {
                                        viewModel.toggleSaveRelatedWord(entry) { saved ->
                                            if (saved == null) {
                                                Toast.makeText(context, "收藏失败，请检查网络后重试", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    if (saved) "已加入生词本" else "已移出生词本",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                            onDone(saved)
                                        }
                                    }
                                },
                                alphabetLetterIndex = alphabetLetterIndex,
                                onSeekAlphabetLetter = viewModel::seekAlphabetLetter,
                                pendingScrollEntryId = pendingListScrollEntryId,
                                onPendingScrollConsumed = viewModel::consumePendingListScroll,
                                onPreviewEntry = if (splitBody) previewInSplit else null,
                                splitPaneBody = splitBody,
                                openMoreRequest = if (splitBody) openMoreRequest else 0,
                            )
                        }
                        if (useNotebookSplit) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .hotWordsScreen(padding, consumeStatusBars = false),
                            ) {
                                NotebookSplitTopBar(
                                    title = activeNotebookName,
                                    onBack = { tab = MainTab.Home },
                                    onOpenMore = { openMoreRequest += 1 },
                                )
                                FoldableDualPaneRow(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    listPane = {
                                        listContent(Modifier.fillMaxSize(), true)
                                    },
                                    detailPane = {
                                        if (words.isNotEmpty()) {
                                            HotWordsStudyCard(
                                                modifier = Modifier.fillMaxSize(),
                                                viewModel = viewModel,
                                                ui = ui,
                                                words = words,
                                                homophones = homophones,
                                                activeNotebookName = activeNotebookName,
                                                onBack = { viewModel.prepareReturnToList() },
                                                showPlaybackControls = false,
                                                showTopBar = false,
                                            )
                                        }
                                    },
                                )
                            }
                        } else {
                            listContent(
                                Modifier
                                    .fillMaxSize()
                                    .hotWordsScreen(padding, consumeStatusBars = false),
                                false,
                            )
                        }
                    }

                    MainTab.Me -> {
                        ProfileScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .hotWordsScreen(padding),
                            wordCount = activeWordCount,
                            userName = ui.settings.displayName,
                            exportFileName = viewModel.suggestedExportFileName(),
                            onOpenSettings = { showAppSettings = true },
                            onExportContent = viewModel::exportNotebookJson,
                            onImportContent = viewModel::importNotebookJson,
                            phone = session?.phone,
                            level = session?.level ?: 0,
                            onLogin = {
                                loginHint = "登录后可同步收藏与生词本"
                                showLogin = true
                            },
                            onChangePassword = viewModel::changePassword,
                            avatarBitmap = avatarBitmap,
                            avatarBusy = avatarBusy,
                            onUploadAvatar = viewModel::uploadAvatar,
                        )
                    }
                }
            }
            }
        }
        }
        }
    }
}

@Composable
private fun HotWordsStudyCard(
    modifier: Modifier,
    viewModel: VocabViewModel,
    ui: VocabUiState,
    words: List<VocabEntry>,
    homophones: List<WordHomophone>,
    activeNotebookName: String,
    onBack: () -> Unit,
    showPlaybackControls: Boolean = true,
    showTopBar: Boolean = true,
) {
    CardModeScreen(
        modifier = modifier,
        entries = if (words.size in 1..80) viewModel.studyDeck() else emptyList(),
        currentEntry = viewModel.currentCard(),
        prevEntry = viewModel.cardAtPlaybackIndex(ui.cardIndex - 1),
        nextEntry = viewModel.cardAtPlaybackIndex(ui.cardIndex + 1),
        entryCount = words.size,
        notebookName = activeNotebookName,
        index = ui.cardIndex,
        sourceIndex = viewModel.naturalIndexOfCurrentCard(),
        shuffled = ui.shuffledOrder != null,
        playing = ui.playing,
        speakOnPageChange = ui.settings.speakOnPageChange,
        accent = ui.settings.accent,
        appTheme = ui.settings.appTheme,
        onBack = onBack,
        onPrev = { viewModel.step(-1) },
        onNext = { viewModel.step(1) },
        onPageSelected = viewModel::selectCard,
        onSeekPage = viewModel::seekToNaturalIndex,
        onPlayToggle = viewModel::toggleAutoPlay,
        onShuffle = viewModel::toggleShuffle,
        onSpeak = viewModel::speakCurrent,
        onToggleSpeak = viewModel::toggleCardSpeak,
        onSpeakAccent = { uk ->
            viewModel.currentCard()?.let { word ->
                viewModel.updateSettings { settings ->
                    settings.copy(accent = if (uk) Accent.UK else Accent.US)
                }
                viewModel.speak(word)
            }
        },
        imageBusy = ui.imageBusy,
        imageError = ui.imageError,
        onPickImage = viewModel::setEntryImage,
        onGenerateAi = viewModel::generateAiImage,
        onClearImageError = viewModel::clearImageError,
        onUpdateDefinitions = viewModel::updateDefinitions,
        onSpeakText = viewModel::speakText,
        onSpeakTextSlow = viewModel::speakTextSlow,
        onSpeakSyllables = viewModel::speakSyllables,
        onToggleRelatedStar = viewModel::toggleSaveRelatedWord,
        isRelatedWordSaved = viewModel::isWordSaved,
        homophones = homophones,
        onLoadHomophones = viewModel::loadHomophones,
        onSubmitHomophone = viewModel::submitHomophone,
        onToggleHomophoneLike = viewModel::toggleHomophoneLike,
        onLoadHomophoneLikers = { id, offset -> viewModel.loadHomophoneLikers(id, offset) },
        onNearEnd = viewModel::loadMoreWords,
        showPlaybackControls = showPlaybackControls,
        showTopBar = showTopBar,
    )
}

@Composable
private fun NotebookSplitTopBar(
    title: String,
    onBack: () -> Unit,
    onOpenMore: () -> Unit,
) {
    val line = Stellar.Cyan.copy(alpha = 0.20f)
    Row(
        Modifier
            .fillMaxWidth()
            .background(stellarPanelBackgroundColor())
            .drawBehind {
                drawLine(
                    color = line,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.sdp())
            .padding(horizontal = 8.sdp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "返回",
                tint = Stellar.OnSurfaceVariant,
                modifier = Modifier.size(18.sdp()),
            )
        }
        Text(
            text = title.ifBlank { "生词本" },
            color = Stellar.CyanSoft,
            fontSize = 18.ssp(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenMore) {
            Text(
                text = "···",
                color = Stellar.Cyan,
                fontSize = 18.ssp(),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StellarSystemBars(
    lightTheme: Boolean,
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // Keep system nav bar fully transparent so our Compose panels aren't covered by a scrim.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightTheme
            isAppearanceLightNavigationBars = lightTheme
        }
    }
}
