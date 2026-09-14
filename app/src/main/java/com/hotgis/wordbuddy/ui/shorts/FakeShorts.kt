package com.hotgis.wordbuddy.ui.shorts

data class ShortClip(
    val id: String,
    val videoUrl: String,
    val title: String,
    val author: String,
    val caption: String,
    val relatedWords: List<String>,
)

object FakeShorts {
    // Public sample MP4s for local UI/playback. Replace with OSS URLs later.
    private const val SAMPLE_A =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
    private const val SAMPLE_B =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    private const val SAMPLE_C =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"
    private const val SAMPLE_D =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
    private const val SAMPLE_E =
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4"

    val clips: List<ShortClip> = listOf(
        ShortClip(
            id = "tiredness",
            videoUrl = SAMPLE_A,
            title = "Tiredness",
            author = "词搭子口语",
            caption = "How to talk about feeling tired without repeating “I'm tired”.",
            relatedWords = listOf("tired", "weary", "exhausted", "fatigue"),
        ),
        ShortClip(
            id = "reluctant",
            videoUrl = SAMPLE_B,
            title = "Reluctant",
            author = "词搭子口语",
            caption = "I was reluctant to speak at first — 不情愿，但还是开口了。",
            relatedWords = listOf("reluctant", "hesitant", "unwilling"),
        ),
        ShortClip(
            id = "vivid",
            videoUrl = SAMPLE_C,
            title = "Vivid",
            author = "词搭子口语",
            caption = "A vivid memory of my first English class.",
            relatedWords = listOf("vivid", "memorable", "striking"),
        ),
        ShortClip(
            id = "awkward",
            videoUrl = SAMPLE_D,
            title = "Awkward",
            author = "词搭子口语",
            caption = "That awkward silence after a joke that didn't land.",
            relatedWords = listOf("awkward", "embarrassed", "uneasy"),
        ),
        ShortClip(
            id = "grateful",
            videoUrl = SAMPLE_E,
            title = "Grateful",
            author = "词搭子口语",
            caption = "I'm grateful for small progress every day.",
            relatedWords = listOf("grateful", "thankful", "appreciate"),
        ),
    )
}
