package com.hotgis.wordbuddy.ui.shorts

data class ShortClip(
    val id: String,
    val videoUrl: String,
    val title: String,
    val author: String,
    val caption: String,
    val relatedWords: List<String>,
    val category: String = "speaking",
    val categoryName: String = "口语",
    val coverUrl: String? = null,
    val favorited: Boolean = false,
)

/** Local fallback when the feed API is empty or unreachable. */
object FakeShorts {
    private val samples = listOf(
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
    )

    val clips: List<ShortClip> = listOf(
        ShortClip(
            id = "local-demo",
            videoUrl = samples[0],
            title = "Demo",
            author = "词搭子",
            caption = "网络短视频加载中…",
            relatedWords = listOf("demo"),
            category = "speaking",
            categoryName = "口语",
        ),
    )
}
