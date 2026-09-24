package com.hotgis.wordbuddy.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hotgis.wordbuddy.data.WordBuddyApi
import com.hotgis.wordbuddy.ui.design.sdp
import com.hotgis.wordbuddy.ui.design.ssp
import com.hotgis.wordbuddy.ui.lookup.Stellar
import com.hotgis.wordbuddy.ui.lookup.stellarGlass
import com.hotgis.wordbuddy.ui.lookup.stellarPanelBackgroundColor
import com.hotgis.wordbuddy.ui.lookup.stellarScreenBackground
import kotlinx.coroutines.delay

private enum class DeletionStep { Notice, Check, Verify, Pending }

private val DELETION_REASONS = listOf(
    "不常使用",
    "担心隐私安全",
    "缺少需要的功能",
    "准备换账号",
    "其他",
)

@Composable
fun AccountDeletionScreen(
    status: WordBuddyApi.AccountDeletionStatus?,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSendCode: (force: Boolean, onResult: (Result<String?>) -> Unit) -> Unit,
    onSubmit: (
        code: String,
        reason: String?,
        force: Boolean,
        onResult: (Result<WordBuddyApi.AccountDeletionStatus>) -> Unit,
    ) -> Unit,
    onCancelDeletion: ((Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(DeletionStep.Notice) }
    var agreed by remember { mutableStateOf(false) }
    var forceAgree by remember { mutableStateOf(false) }
    var forceMode by remember { mutableStateOf(false) }
    var showProtocol by remember { mutableStateOf(false) }
    var selectedReasons by remember { mutableStateOf(setOf<String>()) }
    var code by remember { mutableStateOf("") }
    var countdownSec by remember { mutableIntStateOf(0) }
    var sendingCode by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(status?.pending, status?.dueAt) {
        if (status?.pending == true) step = DeletionStep.Pending
    }
    LaunchedEffect(countdownSec) {
        if (countdownSec <= 0) return@LaunchedEffect
        delay(1000)
        countdownSec -= 1
    }

    fun goBack() {
        when {
            showProtocol -> showProtocol = false
            step == DeletionStep.Notice || step == DeletionStep.Pending -> onBack()
            else -> {
                localError = null
                step = when (step) {
                    DeletionStep.Verify -> DeletionStep.Check
                    DeletionStep.Check -> DeletionStep.Notice
                    else -> DeletionStep.Notice
                }
            }
        }
    }

    BackHandler(onBack = ::goBack)

    val canContinueCheck = status?.allPassed == true || forceAgree

    Box(
        modifier
            .fillMaxSize()
            .stellarScreenBackground(),
    ) {
        Column(Modifier.fillMaxSize()) {
            DeletionTopBar(
                title = if (showProtocol) LegalDocuments.DELETION_TITLE else "注销账号",
                onBack = ::goBack,
            )
            if (status == null && busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Stellar.Cyan, strokeWidth = 2.dp)
                }
            } else if (showProtocol) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = LegalDocuments.deletionNotice,
                        color = Stellar.OnSurfaceVariant,
                        fontSize = 14.ssp(),
                        lineHeight = 22.ssp(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (step) {
                        DeletionStep.Notice -> NoticeStep(
                            agreed = agreed,
                            onAgreed = { agreed = it },
                            onOpenProtocol = { showProtocol = true },
                        )
                        DeletionStep.Check -> CheckStep(
                            status = status,
                            forceAgree = forceAgree,
                            onForceAgree = { forceAgree = it },
                        )
                        DeletionStep.Verify -> VerifyStep(
                            status = status,
                            forceMode = forceMode,
                            code = code,
                            onCode = { code = it.filter { ch -> ch.isDigit() }.take(6) },
                            selectedReasons = selectedReasons,
                            onToggleReason = { reason ->
                                selectedReasons = if (reason in selectedReasons) {
                                    selectedReasons - reason
                                } else {
                                    selectedReasons + reason
                                }
                            },
                            countdownSec = countdownSec,
                            sendingCode = sendingCode,
                            onSendCode = {
                                sendingCode = true
                                localError = null
                                onSendCode(forceMode) { result ->
                                    sendingCode = false
                                    result
                                        .onSuccess { debug ->
                                            countdownSec = 60
                                            if (!debug.isNullOrBlank()) {
                                                Toast.makeText(context, "验证码 $debug", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .onFailure {
                                            localError = it.message ?: "发送失败"
                                        }
                                }
                            },
                        )
                        DeletionStep.Pending -> PendingStep(status = status)
                    }
                    localError?.let {
                        Text(
                            text = it,
                            color = Stellar.Pink,
                            fontSize = 13.ssp(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DeletionPrimaryButton(
                        text = when (step) {
                            DeletionStep.Notice -> "下一步"
                            DeletionStep.Check -> when {
                                forceAgree -> "下一步（强行注销）"
                                status?.allPassed == true -> "下一步"
                                else -> "请先勾选强行注销"
                            }
                            DeletionStep.Verify -> when {
                                submitting && forceMode -> "正在注销…"
                                submitting -> "提交中…"
                                forceMode -> "确认并立即注销"
                                else -> "确认注销"
                            }
                            DeletionStep.Pending -> if (busy || submitting) "处理中…" else "撤销注销"
                        },
                        enabled = when (step) {
                            DeletionStep.Notice -> agreed && !busy
                            DeletionStep.Check -> canContinueCheck && !busy
                            DeletionStep.Verify -> code.length == 6 && !submitting && !sendingCode
                            DeletionStep.Pending -> !busy && !submitting
                        },
                        destructive = step == DeletionStep.Verify || step == DeletionStep.Pending ||
                            (step == DeletionStep.Check && forceAgree),
                        onClick = {
                            localError = null
                            when (step) {
                                DeletionStep.Notice -> step = DeletionStep.Check
                                DeletionStep.Check -> {
                                    forceMode = forceAgree
                                    step = DeletionStep.Verify
                                }
                                DeletionStep.Verify -> {
                                    submitting = true
                                    val reason = selectedReasons.toList().joinToString("、").ifBlank { null }
                                    onSubmit(code, reason, forceMode) { result ->
                                        submitting = false
                                        result
                                            .onSuccess {
                                                if (it.deleted || it.immediate) {
                                                    Toast.makeText(context, "账号已立即注销", Toast.LENGTH_LONG).show()
                                                    onBack()
                                                } else {
                                                    step = DeletionStep.Pending
                                                    Toast.makeText(
                                                        context,
                                                        "已进入${it.cooldownDays}天冷静期",
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                            }
                                            .onFailure { localError = it.message ?: "提交失败" }
                                    }
                                }
                                DeletionStep.Pending -> {
                                    submitting = true
                                    onCancelDeletion { result ->
                                        submitting = false
                                        result
                                            .onSuccess {
                                                Toast.makeText(context, "已撤销注销，账号恢复正常", Toast.LENGTH_LONG).show()
                                                onBack()
                                            }
                                            .onFailure { localError = it.message ?: "撤销失败" }
                                    }
                                }
                            }
                        },
                    )
                    if (step == DeletionStep.Verify) {
                        Text(
                            text = if (forceMode) {
                                "强行注销会立即删除账号，积分、生词本等全部清空，不可恢复。"
                            } else {
                                "跳过原因并注销也可以。提交后有 ${status?.cooldownDays ?: 7} 天冷静期，期间可随时撤销。"
                            },
                            color = if (forceMode) Stellar.Pink else Stellar.OnSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 12.ssp(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletionTopBar(title: String, onBack: () -> Unit) {
    val line = Stellar.Cyan.copy(alpha = 0.20f)
    Box(
        Modifier
            .fillMaxWidth()
            .background(stellarPanelBackgroundColor())
            .drawBehind {
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ArrowBackIosNew,
                contentDescription = "返回",
                tint = Stellar.CyanSoft,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            color = Stellar.OnSurface,
            fontSize = 18.ssp(),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun NoticeStep(
    agreed: Boolean,
    onAgreed: (Boolean) -> Unit,
    onOpenProtocol: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.dp),
    ) {
        Text("注销须知", color = Stellar.OnSurface, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "注销是不可恢复的操作。冷静期届满或强行注销完成后，即使使用相同手机号重新注册，也无法找回本账号中的内容。",
            color = Stellar.Pink,
            fontSize = 14.ssp(),
            lineHeight = 21.ssp(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        NoticeLine("无法继续登录本账号，搭子号与资料将被删除")
        NoticeLine("生词本、收藏、学习记录与助记无法找回")
        NoticeLine("剩余积分视为自愿放弃；也可勾选强行注销立即删除")
        NoticeLine("普通注销有 7 天冷静期；强行注销验证后立即生效")
        NoticeLine("相关日志依法可能保留不少于 6 个月")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DeletionCheckBox(checked = agreed, onChecked = onAgreed)
        Text(
            text = "我已阅读并同意",
            color = Stellar.OnSurface,
            fontSize = 14.ssp(),
            modifier = Modifier.clickable { onAgreed(!agreed) },
        )
        Text(
            text = "《${LegalDocuments.DELETION_TITLE}》",
            color = Stellar.CyanSoft,
            fontSize = 14.ssp(),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onOpenProtocol),
        )
    }
}

@Composable
private fun NoticeLine(text: String) {
    Text(
        text = "· $text",
        color = Stellar.OnSurfaceVariant,
        fontSize = 14.ssp(),
        lineHeight = 22.ssp(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
}

@Composable
private fun CheckStep(
    status: WordBuddyApi.AccountDeletionStatus?,
    forceAgree: Boolean,
    onForceAgree: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("注销检测", color = Stellar.OnSurface, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
        Text(
            if (status?.allPassed == true) {
                "当前满足注销条件。也可勾选强行注销，验证后立即删除。"
            } else {
                "还有未完成事项。若你确认全部放弃，可勾选强行注销，验证后立即删除账号。"
            },
            color = if (status?.allPassed == true) Stellar.OnSurfaceVariant else Stellar.Pink,
            fontSize = 14.ssp(),
            modifier = Modifier.fillMaxWidth(),
        )
        (status?.conditions ?: emptyList()).forEach { item ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    if (item.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (item.ok) Stellar.Cyan else Stellar.Pink,
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = Stellar.OnSurface, fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
                    Text(item.detail, color = Stellar.OnSurfaceVariant, fontSize = 13.ssp())
                }
            }
        }
        if ((status?.remainingPoints ?: 0) > 0) {
            Text(
                text = "当前剩余 ${status?.remainingPoints} 积分，注销后视为放弃。",
                color = Stellar.OnSurfaceVariant,
                fontSize = 13.ssp(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DeletionCheckBox(checked = forceAgree, onChecked = onForceAgree)
        Text(
            text = "我同意强行注销：放弃积分、未完成兑换/提现及全部账号权益，验证后立即删除，不可恢复。",
            color = Stellar.Pink,
            fontSize = 13.ssp(),
            lineHeight = 20.ssp(),
            modifier = Modifier
                .weight(1f)
                .clickable { onForceAgree(!forceAgree) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerifyStep(
    status: WordBuddyApi.AccountDeletionStatus?,
    forceMode: Boolean,
    code: String,
    onCode: (String) -> Unit,
    selectedReasons: Set<String>,
    onToggleReason: (String) -> Unit,
    countdownSec: Int,
    sendingCode: Boolean,
    onSendCode: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (forceMode) "强行注销 · 身份验证" else "身份验证",
            color = Stellar.OnSurface,
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
        )
        Text(
            "验证码将发送至 ${status?.phoneMasked ?: "绑定手机号"}，以确认是你本人操作。",
            color = Stellar.OnSurfaceVariant,
            fontSize = 14.ssp(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Stellar.SurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (code.isEmpty()) {
                    Text("6 位验证码", color = Stellar.OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 16.ssp())
                }
                BasicTextField(
                    value = code,
                    onValueChange = onCode,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(color = Stellar.OnSurface, fontSize = 16.ssp()),
                    cursorBrush = SolidColor(Stellar.Cyan),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = when {
                    sendingCode -> "发送中"
                    countdownSec > 0 -> "${countdownSec}s"
                    else -> "获取验证码"
                },
                color = if (countdownSec > 0 || sendingCode) Stellar.OnSurfaceVariant else Stellar.CyanSoft,
                fontSize = 14.ssp(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = countdownSec <= 0 && !sendingCode, onClick = onSendCode)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        Text("注销原因（可多选，可跳过）", color = Stellar.OnSurface, fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DELETION_REASONS.forEach { reason ->
                val selected = reason in selectedReasons
                Text(
                    text = reason,
                    color = if (selected) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
                    fontSize = 13.ssp(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Stellar.CyanSoft else Stellar.SurfaceHigh)
                        .clickable { onToggleReason(reason) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun PendingStep(status: WordBuddyApi.AccountDeletionStatus?) {
    Column(
        Modifier
            .fillMaxWidth()
            .stellarGlass()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("账号正在注销中", color = Stellar.OnSurface, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
        Text(
            "将于 ${status?.dueAtLabel?.ifBlank { "冷静期结束" } ?: "冷静期结束"} 自动完成注销。期间可继续使用；若还想留下，请点击下方撤销。",
            color = Stellar.OnSurfaceVariant,
            fontSize = 14.ssp(),
            lineHeight = 21.ssp(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "冷静期内不要只退出登录就以为取消了申请。到期未撤销将永久删除账号数据。",
            color = Stellar.Pink,
            fontSize = 13.ssp(),
            lineHeight = 20.ssp(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeletionCheckBox(checked: Boolean, onChecked: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .border(1.5.dp, if (checked) Stellar.Cyan else Stellar.OnSurfaceVariant, RoundedCornerShape(5.dp))
            .background(if (checked) Stellar.Cyan else Color.Transparent)
            .clickable { onChecked(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", color = Stellar.OnPrimary, fontSize = 12.ssp(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeletionPrimaryButton(
    text: String,
    enabled: Boolean,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val fill = when {
        !enabled -> Stellar.SurfaceHigh
        destructive -> Stellar.Pink
        else -> Stellar.Cyan
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) Stellar.OnPrimary else Stellar.OnSurfaceVariant,
            fontSize = 16.ssp(),
            fontWeight = FontWeight.Bold,
        )
    }
}
