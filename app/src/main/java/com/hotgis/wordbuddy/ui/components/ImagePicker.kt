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
 * =========================================================================================
 * 【相册选图核心组件与避坑备忘录】
 * =========================================================================================
 *
 * 以后若相册选图功能在真机或新 Android 版本上出现异常，请重点参考本组件的实现与如下四大避坑要点：
 *
 * 1. 【避坑点一：FragmentActivity 16 位 requestCode 崩溃】
 *    - 现象：在真机上调用相册选图时崩溃或弹出 Toast:
 *      `java.lang.IllegalArgumentException: Can only use lower 16 bits for requestCode`
 *    - 原因：项目若依赖了旧版 `androidx.biometric:1.1.0`，会隐式引入远古的 `androidx.fragment:fragment:1.2.5`。
 *      在旧版 `FragmentActivity.checkForValidRequestCode()` 中强制要求 requestCode 高 16 位全为 0：
 *      `(requestCode & 0xffff0000) != 0` 即抛出异常。而 Compose 的 `rememberLauncherForActivityResult`
 *      由 ActivityResultRegistry 动态分发的 requestCode 经常超过 16 位范围。
 *    - 正确解法：在 `gradle/libs.versions.toml` 中显式指定 `fragment = "1.8.5"`，并在 `build.gradle.kts`
 *      中声明 `implementation(libs.androidx.fragment.ktx)`，新版 FragmentActivity 彻底修复了此问题。
 *
 * 2. 【避坑点二：Intent.setType() 会清空 mData 导致小米/澎湃OS等相册匹配失败】
 *    - 现象：小米澎湃 OS（HyperOS/MIUI）或部分国产手机弹出“未能打开系统相册，请检查是否安装了相册应用”。
 *    - 原因：若按如下方式构建 Intent：
 *      `Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }`
 *      在 Android Framework 原生 `Intent.java` 源码中：
 *      ```java
 *      public @NonNull Intent setType(@Nullable String type) {
 *          mData = null;  // <-- 致命！调用 setType 会直接将 mData 清空置 null！
 *          mType = type;
 *          return this;
 *      }
 *      ```
 *      小米相册（`com.miui.gallery`）的 Activity 过滤器依赖 `content://media/external/images/media` 进行意图匹配，
 *      mData 被置 null 后系统无法匹配到小米相册，导致 ActivityNotFoundException。
 *    - 正确解法：
 *      - 方案 A（推荐）：直接使用 `Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)`，不设置 type。
 *      - 方案 B：若必须同时指定 URI 和 MIME 类型，严禁单独调用 `intent.type = ...`，必须使用
 *        `intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")`。
 *
 * 3. 【避坑点三：Android 11+ 包可见性 Package Visibility 与严禁依赖 resolveActivity】
 *    - 现象：明明安装了相册，代码若使用 `intent.resolveActivity(packageManager) != null` 判断却返回 false，
 *      直接跳过有效 Intent。
 *    - 原因：Android 11（API 30+）引入软件包可见性，未在 AndroidManifest.xml 的 `<queries>` 中显式声明的
 *      包名或意图，`resolveActivity` 会返回 null。但即使返回 null，直接通过 `launch(intent)` 仍可正常拉起系统相册！
 *    - 正确解法：
 *      - 在 `AndroidManifest.xml` 的 `<queries>` 中声明常见厂商相册包名（小米、华为、OPPO、vivo、三星等）及相册 Intent。
 *      - 运行时不要用 `resolveActivity` 做前置阻断，而是按照候选优先级逐个在 try-catch 中 `galleryLauncher.launch(intent)`，
 *        成功拉起任意一个即退出循环。
 *
 * 4. 【避坑点四：用户选图期间 Activity 可能被回收的状态保存】
 *    - 现象：选图返回后，图片没有设置到对应单词上，或者报错目标 ID 为 null。
 *    - 原因：用户跳转到相册应用时，当前 App 被压入后台。若系统内存吃紧，宿主 Activity 会被销毁重建。
 *    - 正确解法：在调用方页面（如 `CardModeScreen.kt`）中，`imageTargetId` 必须使用 `rememberSaveable`
 *      持久化保存；同时在选图回调中增加 `currentEntry?.id` 保底。
 * =========================================================================================
 */
@Composable
fun rememberImagePickerLauncher(
    onImagePicked: (Uri) -> Unit,
    onError: ((String) -> Unit)? = null,
): () -> Unit {
    val context = LocalContext.current
    var launchToken by remember { mutableIntStateOf(0) }

    fun report(message: String) {
        if (onError != null) onError(message) else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        Log.d("ImagePicker", "galleryLauncher onResult: code=${result.resultCode}, data=${result.data}")
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val intent = result.data
        val uri = intent?.data
            ?: intent?.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).uri else null
            }
        Log.d("ImagePicker", "galleryLauncher got uri: $uri")
        if (uri != null) {
            onImagePicked(uri)
        } else {
            report("未能获取图片路径")
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
