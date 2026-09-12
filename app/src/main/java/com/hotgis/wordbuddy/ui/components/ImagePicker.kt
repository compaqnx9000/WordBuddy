package com.hotgis.wordbuddy.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Checks whether the app currently has permission to read images from the gallery.
 */
fun hasImagePermission(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED
        }
        else -> {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Returns the permissions to request for image picking depending on the Android API level.
 */
fun getRequiredImagePermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        }
        else -> {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

/**
 * Creates and remembers an image picker launcher that:
 * 1. Automatically requests runtime photo/media permissions dynamically if not yet granted.
 * 2. Seamlessly opens the system gallery once the user grants permission.
 * 3. Works reliably across all Android versions (Android 6 - 15) and domestic OEM ROMs (HyperOS, MIUI, EMUI, ColorOS, OriginOS).
 */
@Composable
fun rememberImagePickerLauncher(
    onImagePicked: (Uri) -> Unit,
    onError: ((String) -> Unit)? = null,
): () -> Unit {
    val context = LocalContext.current

    // Activity Result Launcher for selecting the image from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
                ?: result.data?.clipData?.let { clip ->
                    if (clip.itemCount > 0) clip.getItemAt(0).uri else null
                }
            if (uri != null) {
                onImagePicked(uri)
            }
        }
    }

    val doLaunchGallery = remember(context, galleryLauncher, onError) {
        {
            launchSystemImagePicker(context, galleryLauncher, onError)
        }
    }

    // Permission launcher for dynamic permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val anyGranted = permissions.values.any { it }
        if (anyGranted) {
            // User clicked "Allow" / "选择照片" -> immediately launch gallery!
            doLaunchGallery()
        } else {
            // If user denied, try launching anyway (some system pickers on Android 13+ don't require permissions).
            // If that still fails, show a helpful message.
            launchSystemImagePicker(context, galleryLauncher) {
                val message = "需要相册访问权限才能选取图片，请在系统设置中允许"
                if (onError != null) {
                    onError(message)
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    return remember(context, doLaunchGallery, permissionLauncher) {
        {
            if (hasImagePermission(context)) {
                doLaunchGallery()
            } else {
                // Dynamically request permission at runtime!
                permissionLauncher.launch(getRequiredImagePermissions())
            }
        }
    }
}

fun launchSystemImagePicker(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    onError: ((String) -> Unit)? = null,
): Boolean {
    val intents = mutableListOf<Intent>()

    // 1. Android 13+ Photo Picker (MediaStore.ACTION_PICK_IMAGES)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intents.add(
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
            },
        )
    }

    // 2. Traditional System Gallery (ACTION_PICK with MediaStore URI)
    intents.add(
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        },
    )

    // 3. ACTION_PICK with type only
    intents.add(
        Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        },
    )

    // 4. Standard content picker (ACTION_GET_CONTENT without CATEGORY_OPENABLE so OEM galleries match)
    intents.add(
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        },
    )

    // 5. ACTION_GET_CONTENT with CATEGORY_OPENABLE (for pure SAF devices)
    intents.add(
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        },
    )

    var launched = false
    var lastError: Throwable? = null
    for (intent in intents) {
        try {
            launcher.launch(intent)
            launched = true
            break
        } catch (e: Throwable) {
            lastError = e
            Log.w("ImagePicker", "Failed to launch candidate intent: $intent", e)
        }
    }

    if (!launched) {
        try {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" },
                "选择图片",
            )
            launcher.launch(chooser)
            launched = true
        } catch (e: Throwable) {
            lastError = e
            Log.w("ImagePicker", "Failed to launch chooser", e)
        }
    }

    if (!launched) {
        val detail = lastError?.message?.takeIf { it.isNotBlank() }
        val message = if (detail != null) {
            "未能打开系统相册 ($detail)"
        } else {
            "未能打开系统相册，请检查相册应用或存储权限"
        }
        if (onError != null) {
            onError(message)
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    return launched
}
