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
    private val samples = listOf(
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
    )

    private data class Script(
        val id: String,
        val title: String,
        val author: String,
        val caption: String,
        val relatedWords: List<String>,
    )

    private val scripts = listOf(
        Script("tiredness", "Tiredness", "词搭子口语", "How to talk about feeling tired without repeating “I'm tired”.", listOf("tired", "weary", "exhausted", "fatigue")),
        Script("reluctant", "Reluctant", "词搭子口语", "I was reluctant to speak at first — 不情愿，但还是开口了。", listOf("reluctant", "hesitant", "unwilling")),
        Script("vivid", "Vivid", "词搭子口语", "A vivid memory of my first English class.", listOf("vivid", "memorable", "striking")),
        Script("awkward", "Awkward", "词搭子口语", "That awkward silence after a joke that didn't land.", listOf("awkward", "embarrassed", "uneasy")),
        Script("grateful", "Grateful", "词搭子口语", "I'm grateful for small progress every day.", listOf("grateful", "thankful", "appreciate")),
        Script("resilient", "Resilient", "词搭子单词", "Resilient people bounce back after a setback.", listOf("resilient", "tough", "recover")),
        Script("concise", "Concise", "词搭子单词", "Keep it concise — 一句话把重点说清。", listOf("concise", "brief", "succinct")),
        Script("genuine", "Genuine", "词搭子口语", "That smile looks genuine, not forced.", listOf("genuine", "sincere", "authentic")),
        Script("overwhelm", "Overwhelm", "词搭子单词", "Don't let new words overwhelm you. One at a time.", listOf("overwhelm", "flood", "overpower")),
        Script("subtle", "Subtle", "词搭子单词", "A subtle difference between “say” and “tell”.", listOf("subtle", "slight", "nuanced")),
        Script("curious", "Curious", "词搭子口语", "Stay curious — 敢问，才记得住。", listOf("curious", "inquisitive", "wonder")),
        Script("confident", "Confident", "词搭子口语", "Speak slowly and you'll sound more confident.", listOf("confident", "assured", "self-assured")),
        Script("hesitate", "Hesitate", "词搭子单词", "Don't hesitate to repeat a word until it sticks.", listOf("hesitate", "pause", "waver")),
        Script("fluent", "Fluent", "词搭子口语", "Fluent doesn't mean perfect. It means you keep going.", listOf("fluent", "smooth", "flowing")),
        Script("distract", "Distract", "词搭子单词", "Put the phone away so it doesn't distract you.", listOf("distract", "divert", "sidetrack")),
        Script("ambitious", "Ambitious", "词搭子单词", "An ambitious goal: 20 new words this week.", listOf("ambitious", "driven", "bold")),
        Script("patient", "Patient", "词搭子口语", "Be patient with your accent. It takes time.", listOf("patient", "calm", "tolerant")),
        Script("criticize", "Criticize", "词搭子单词", "It's easier to criticize than to try speaking.", listOf("criticize", "blame", "judge")),
        Script("empathy", "Empathy", "词搭子单词", "Empathy is feeling with someone, not just for them.", listOf("empathy", "compassion", "understanding")),
        Script("stubborn", "Stubborn", "词搭子口语", "A stubborn word that you keep mixing up? Drill it today.", listOf("stubborn", "obstinate", "persistent")),
    )

    val clips: List<ShortClip> = scripts.mapIndexed { index, script ->
        ShortClip(
            id = script.id,
            videoUrl = samples[index % samples.size],
            title = script.title,
            author = script.author,
            caption = script.caption,
            relatedWords = script.relatedWords,
        )
    }
}
