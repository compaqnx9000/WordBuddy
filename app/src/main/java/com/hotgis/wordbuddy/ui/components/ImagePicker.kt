package com.hotgis.wordbuddy.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Opens the system photo picker without requesting storage/media permissions.
 *
 * Photo Picker / SAF already grant a read URI; asking for READ_MEDIA_* first is what
 * prevents the gallery from appearing on many OEM phones. Launch is deferred so a
 * Compose Dialog can finish dismissing first — otherwise the picker starts behind
 * (or is immediately cancelled by) the dialog window.
 */
@Composable
fun rememberImagePickerLauncher(
    onImagePicked: (Uri) -> Unit,
    onError: ((String) -> Unit)? = null,
): () -> Unit {
    val context = LocalContext.current
    var launchToken by remember { mutableIntStateOf(0) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }
    val getContent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }
    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }
    val fallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data
            ?: result.data?.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).uri else null
            }
        if (uri != null) onImagePicked(uri)
    }

    fun report(message: String) {
        if (onError != null) onError(message) else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchNow() {
        try {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            return
        } catch (e: Throwable) {
            Log.w("ImagePicker", "PickVisualMedia failed", e)
        }
        try {
            getContent.launch("image/*")
            return
        } catch (e: Throwable) {
            Log.w("ImagePicker", "GetContent failed", e)
        }
        try {
            openDocument.launch(arrayOf("image/*"))
            return
        } catch (e: Throwable) {
            Log.w("ImagePicker", "OpenDocument failed", e)
        }
        if (!launchLegacyPicker(context, fallbackLauncher)) {
            report("未能打开系统相册，请检查是否安装了相册应用")
        }
    }

    LaunchedEffect(launchToken) {
        if (launchToken == 0) return@LaunchedEffect
        delay(200)
        launchNow()
    }

    return remember {
        { launchToken += 1 }
    }
}

private fun launchLegacyPicker(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
): Boolean {
    val candidates = listOf(
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        },
        Intent.createChooser(
            Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" },
            "选择图片",
        ),
    )
    for (intent in candidates) {
        try {
            launcher.launch(intent)
            return true
        } catch (e: Throwable) {
            Log.w("ImagePicker", "Failed to launch $intent", e)
        }
    }
    return false
}
