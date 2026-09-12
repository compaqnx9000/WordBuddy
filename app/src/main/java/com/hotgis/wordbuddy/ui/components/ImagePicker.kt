package com.hotgis.wordbuddy.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
 * Opens the system photo gallery/picker without requesting dangerous storage permissions.
 *
 * Traditional `ACTION_PICK` and system pickers grant a read URI via the activity result contract.
 * We prioritize `ACTION_PICK` with MediaStore.Images.Media.EXTERNAL_CONTENT_URI, which is universally
 * supported by all domestic Android OEMs (Xiaomi/MIUI/HyperOS, Huawei/HarmonyOS, OPPO/ColorOS,
 * Vivo/OriginOS, Samsung, etc.).
 *
 * NOTE: When setting up ACTION_PICK with EXTERNAL_CONTENT_URI, NEVER call intent.type = "image/..."
 * because Intent.setType() clears mData to null in Android framework, breaking intent resolution!
 * Use setDataAndType() if both are required, or keep the URI as-is.
 */
@Composable
fun rememberImagePickerLauncher(
    onImagePicked: (Uri) -> Unit,
    onError: ((String) -> Unit)? = null,
): () -> Unit {
    val context = LocalContext.current
    var launchToken by remember { mutableIntStateOf(0) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val intent = result.data
        val uri = intent?.data
            ?: intent?.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).uri else null
            }
        if (uri != null) {
            onImagePicked(uri)
        }
    }

    fun report(message: String) {
        if (onError != null) onError(message) else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchNow() {
        val candidates = buildList {
            // 1. Universal Gallery Intent: ACTION_PICK with MediaStore URI (No type = "image/*" to avoid wiping mData!)
            add(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            // 2. Targeted for Xiaomi/MIUI Gallery (com.miui.gallery)
            add(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    setPackage("com.miui.gallery")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            // 3. ACTION_PICK with both URI and MIME type via setDataAndType (retains both URI and type)
            add(
                Intent(Intent.ACTION_PICK).apply {
                    setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            // 4. Android 13+ standard Photo Picker
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                        type = "image/*"
                    }
                )
            }

            // 5. ACTION_GET_CONTENT without CATEGORY_OPENABLE (matches OEM galleries)
            add(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            // 6. ACTION_PICK with MIME type only
            add(
                Intent(Intent.ACTION_PICK).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            // 7. ACTION_GET_CONTENT with CATEGORY_OPENABLE (SAF document provider)
            add(
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            // 8. ACTION_OPEN_DOCUMENT (SAF)
            add(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )

            // 9. Chooser with ACTION_PICK
            add(
                Intent.createChooser(
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    "选择图片",
                )
            )

            // 10. Chooser with ACTION_GET_CONTENT
            add(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" },
                    "选择图片",
                )
            )
        }

        var launched = false
        var lastError: Throwable? = null
        for (intent in candidates) {
            try {
                galleryLauncher.launch(intent)
                launched = true
                Log.d("ImagePicker", "Successfully launched gallery intent: $intent")
                break
            } catch (e: Throwable) {
                lastError = e
                Log.w("ImagePicker", "Failed candidate intent: $intent", e)
            }
        }

        if (!launched) {
            val detail = lastError?.message?.takeIf { it.isNotBlank() }
                ?: lastError?.javaClass?.simpleName
            val msg = if (detail != null) "未能打开系统相册 ($detail)" else "未能打开系统相册，请检查相册应用"
            report(msg)
        }
    }

    LaunchedEffect(launchToken) {
        if (launchToken == 0) return@LaunchedEffect
        delay(150)
        launchNow()
    }

    return remember {
        { launchToken += 1 }
    }
}
