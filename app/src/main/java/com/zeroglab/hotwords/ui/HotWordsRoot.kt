package com.zeroglab.hotwords.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroglab.hotwords.data.AppTheme
import androidx.compose.ui.graphics.Color
import com.zeroglab.hotwords.data.AccentStyle
import com.zeroglab.hotwords.data.Accent
import com.zeroglab.hotwords.data.Notebook
import com.zeroglab.hotwords.ui.auth.LoginScreen
import com.zeroglab.hotwords.ui.card.CardModeScreen
import com.zeroglab.hotwords.ui.components.MainBottomBar
import com.zeroglab.hotwords.ui.components.MainTab
import com.zeroglab.hotwords.ui.design.DesignScaleProvider
import com.zeroglab.hotwords.ui.design.hotWordsScreen
import com.zeroglab.hotwords.ui.list.WordListScreen
import com.zeroglab.hotwords.ui.lookup.LookupScreen
import com.zeroglab.hotwords.ui.lookup.Stellar
import com.zeroglab.hotwords.ui.lookup.StellarTheme
import com.zeroglab.hotwords.ui.profile.ProfileScreen
import com.zeroglab.hotwords.ui.settings.AppSettingsScreen
import com.zeroglab.hotwords.ui.lookup.stellarScreenBackgroundColor
import com.zeroglab.hotwords.ui.theme.HotWordsTheme
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
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val words by viewModel.filteredWords.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val alphabetLetterIndex by viewModel.alphabetLetterIndex.collectAsStateWithLifecycle()
    val pendingListScrollEntryId by viewModel.pendingListScrollEntryId.collectAsStateWithLifecycle()
    val activeNotebookName = viewModel.activeNotebook()?.name ?: Notebook.DEFAULT_NAME
    val activeWordCount = maxOf(viewModel.activeNotebook()?.wordCount ?: 0, words.size)

    fun requireLogin(hint: String): Boolean {
        if (session != null) return true
        loginHint = hint
        showLogin = true
        return false
    }

    LaunchedEffect(session) {
        if (session != null) {
            showLogin = false
            loginHint = null
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

    DesignScaleProvider(modifier, fontScale = ui.settings.fontScale) {
        StellarTheme(style = ui.settings.accentStyle) {
        HotWordsTheme(
            appTheme = ui.settings.appTheme,
            accentStyle = ui.settings.accentStyle,
        ) {
            val stellarChrome = showAppSettings || (
                overlay == Overlay.Card ||
                    (overlay == Overlay.None && (tab == MainTab.Home || tab == MainTab.Notebook))
                )
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
                    onSendCode = viewModel::sendLoginCode,
                    onLogin = viewModel::submitLogin,
                )
                return@HotWordsTheme
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (stellarChrome) {
                    if (ui.settings.accentStyle == AccentStyle.ForestStar) {
                        Color.Transparent
                    } else {
                        stellarScreenBackgroundColor()
                    }
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
                    CardModeScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .hotWordsScreen(padding, consumeStatusBars = false),
                        entries = if (words.size in 1..80) viewModel.studyDeck() else emptyList(),
                        currentEntry = viewModel.currentCard(),
                        entryCount = words.size,
                        notebookName = activeNotebookName,
                        index = ui.cardIndex,
                        sourceIndex = viewModel.naturalIndexOfCurrentCard(),
                        shuffled = ui.shuffledOrder != null,
                        playing = ui.playing,
                        speakOnPageChange = ui.settings.speakOnPageChange,
                        accent = ui.settings.accent,
                        appTheme = ui.settings.appTheme,
                        onBack = {
                            viewModel.prepareReturnToList()
                            overlay = Overlay.None
                        },
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
                            onNearEnd = viewModel::loadMoreWords,
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
                                .hotWordsScreen(padding, consumeStatusBars = false),
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
                        )
                    }

                    MainTab.Notebook -> {
                        WordListScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .hotWordsScreen(padding, consumeStatusBars = false),
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
                            onRecite = {
                                viewModel.openCard(id = it, shuffled = false)
                                overlay = Overlay.Card
                            },
                            onBack = { tab = MainTab.Home },
                            onLoadMore = viewModel::loadMoreWords,
                            alphabetLetterIndex = alphabetLetterIndex,
                            onSeekAlphabetLetter = viewModel::seekAlphabetLetter,
                            pendingScrollEntryId = pendingListScrollEntryId,
                            onPendingScrollConsumed = viewModel::consumePendingListScroll,
                        )
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
                            onLogin = {
                                loginHint = "登录后可同步收藏与生词本"
                                showLogin = true
                            },
                            onLogout = viewModel::logout,
                        )
                    }
                }
            }
            }
        }
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
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightTheme
            isAppearanceLightNavigationBars = lightTheme
        }
    }
}
