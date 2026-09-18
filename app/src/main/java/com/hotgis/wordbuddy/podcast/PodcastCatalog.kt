package com.hotgis.wordbuddy.podcast

import androidx.compose.ui.graphics.Color

data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val summary: String = "",
    val durationLabel: String = "",
)

data class PodcastShow(
    val id: String,
    val title: String,
    val host: String,
    val blurb: String,
    val rssUrl: String?,
    val vinylColor: Color,
    val labelColor: Color,
    val fallbackEpisodes: List<PodcastEpisode>,
)

/**
 * Free English podcasts + live radio (mainland-reachable audio streams where possible).
 * RSS first; [fallbackEpisodes] keep playback when feeds are slow/blocked.
 * Live radio uses rssUrl = null and a single ice/progressive audio URL.
 */
object PodcastCatalog {
    val shows: List<PodcastShow> = listOf(
        // Live English radio — progressive ice streams verified reachable from CN.
        PodcastShow(
            id = "npr-live",
            title = "NPR News",
            host = "NPR · Live Radio",
            blurb = "美式英语新闻电台 · 24h 直播 · 大陆可听",
            rssUrl = null,
            vinylColor = Color(0xFF0F1A24),
            labelColor = Color(0xFF2E7DFF),
            fallbackEpisodes = listOf(
                ep(
                    "npr-live-now",
                    "LIVE · NPR News",
                    "https://npr-ice.streamguys1.com/live.mp3",
                    "直播中",
                ),
            ),
        ),
        PodcastShow(
            id = "lbc-live",
            title = "LBC",
            host = "LBC UK · Live Radio",
            blurb = "英式谈话电台 · 新闻访谈 · 大陆可听",
            rssUrl = null,
            vinylColor = Color(0xFF1A1010),
            labelColor = Color(0xFFE53935),
            fallbackEpisodes = listOf(
                ep(
                    "lbc-live-now",
                    "LIVE · LBC UK",
                    "https://media-ice.musicradio.com/LBCUK",
                    "直播中",
                ),
            ),
        ),
        PodcastShow(
            id = "cri-live",
            title = "China Plus",
            host = "CRI · Live Radio",
            blurb = "中国国际广播英文台 · 24h 直播",
            rssUrl = null,
            vinylColor = Color(0xFF1C0E10),
            labelColor = Color(0xFFE53935),
            fallbackEpisodes = listOf(
                ep(
                    "cri-live-now",
                    "LIVE · China Plus Radio",
                    "https://sk.cri.cn/am846.m3u8",
                    "直播中",
                ),
            ),
        ),
        PodcastShow(
            id = "china-plus-special",
            title = "Special English",
            host = "China Plus · CRI",
            blurb = "中国国际广播 · 慢速英语 · 免费官方",
            rssUrl = "https://aezfm.meldingcloud.com/rss/program/3",
            vinylColor = Color(0xFF1A1014),
            labelColor = Color(0xFFC62828),
            fallbackEpisodes = listOf(
                ep(
                    "cps-1",
                    "China's space crew assesses emergency choices",
                    "https://radio-res.cgtn.com/ueditor/audio/2607/1084883108332.mp3",
                ),
                ep(
                    "cps-2",
                    "Macao int'l youth dance festival",
                    "https://radio-res.cgtn.com/ueditor/audio/2607/1084711170707.mp3",
                ),
                ep(
                    "cps-3",
                    "World AI Cooperation Organization",
                    "https://radio-res.cgtn.com/ueditor/audio/2607/1084537044175.mp3",
                ),
            ),
        ),
        PodcastShow(
            id = "china-plus-headlines",
            title = "Headline News",
            host = "China Plus · CRI",
            blurb = "中国国际广播 · 英文短新闻 · 免费官方",
            rssUrl = "https://cgtn-radio-data.cgtn.com/rss/programother/159",
            vinylColor = Color(0xFF14181C),
            labelColor = Color(0xFFD4A017),
            fallbackEpisodes = listOf(
                ep(
                    "cph-1",
                    "China receives 58 returned cultural artifacts",
                    "https://radio-res.cgtn.com/ueditor/audio/2609/1189654054791.mp3",
                ),
                ep(
                    "cph-2",
                    "Kremlin criticizes US sanctions bill",
                    "https://radio-res.cgtn.com/ueditor/audio/2609/1089648831242.mp3",
                ),
                ep(
                    "cph-3",
                    "Chinese runner Zhao Jiaju wins Tor des Geants",
                    "https://radio-res.cgtn.com/ueditor/audio/2609/1089636309299.mp3",
                ),
            ),
        ),
        PodcastShow(
            id = "allears",
            title = "All Ears English",
            host = "Lindsay & Michelle",
            blurb = "日常英语、表达、文化 · 推荐★★★★★",
            rssUrl = "https://feeds.megaphone.fm/allearsenglish",
            vinylColor = Color(0xFF1A1218),
            labelColor = Color(0xFFE84393),
            fallbackEpisodes = listOf(
                ep("allears-1", "Latest All Ears English", "https://traffic.megaphone.fm/ALLE7938430338.mp3"),
                ep("allears-2", "All Ears English episode", "https://traffic.megaphone.fm/ALLE1735931865.mp3"),
                ep("allears-3", "All Ears English episode", "https://traffic.megaphone.fm/ALLE1019691198.mp3"),
            ),
        ),
        PodcastShow(
            id = "bbc6min",
            title = "6 Minute English",
            host = "BBC Learning English",
            blurb = "BBC 短篇英语、词汇、新闻 · 推荐★★★★★",
            rssUrl = "https://podcasts.files.bbci.co.uk/p02pc9tn.rss",
            vinylColor = Color(0xFF1B1B1F),
            labelColor = Color(0xFFBB1919),
            fallbackEpisodes = listOf(
                ep(
                    "bbc6-1",
                    "Weight loss drugs",
                    "https://downloads.bbc.co.uk/learningenglish/features/6min/260326_6_minute_english_weight_loss_drugs_download_.mp3",
                    "6 min",
                ),
                ep(
                    "bbc6-2",
                    "How do we adapt to the cold?",
                    "https://downloads.bbc.co.uk/learningenglish/features/6min/260319_6_minute_english_how_do_we_adapt_to_the_cold_download_.mp3",
                    "6 min",
                ),
                ep(
                    "bbc6-3",
                    "Is coffee good for you?",
                    "https://downloads.bbc.co.uk/learningenglish/features/6min/260312_6_minute_english_is_coffee_good_for_you_download_.mp3",
                    "6 min",
                ),
            ),
        ),
        PodcastShow(
            id = "englishpod",
            title = "English Learning Podcast",
            host = "EnglishPod",
            blurb = "对话、场景英语 · 推荐★★★★",
            rssUrl = "https://anchor.fm/s/11380dcac/podcast/rss",
            vinylColor = Color(0xFF12181F),
            labelColor = Color(0xFF0984E3),
            fallbackEpisodes = listOf(
                ep(
                    "epod-1",
                    "EnglishPod lesson",
                    "https://d3ctxlq1ktw2nl.cloudfront.net/staging/2026-5-16/47f5525e-e593-4bf0-60bc-6e2f572aa622.mp3",
                ),
                ep(
                    "epod-2",
                    "EnglishPod lesson",
                    "https://d3ctxlq1ktw2nl.cloudfront.net/staging/2026-5-16/61576ad8-c0c2-63aa-be2a-e1275aae2800.mp3",
                ),
                ep(
                    "epod-3",
                    "EnglishPod lesson",
                    "https://d3ctxlq1ktw2nl.cloudfront.net/staging/2026-5-16/e2041057-05d2-993c-6ea5-d1feaa31bcaf.mp3",
                ),
            ),
        ),
        PodcastShow(
            id = "bbc-conversations",
            title = "Learning English Conversations",
            host = "BBC Learning English",
            blurb = "日常对话与地道表达 · 推荐★★★★",
            rssUrl = "https://podcasts.files.bbci.co.uk/p02pc9zn.rss",
            vinylColor = Color(0xFF141820),
            labelColor = Color(0xFF0B7A75),
            fallbackEpisodes = listOf(
                // Same free BBC Learning English CDN family as 6 Minute English.
                ep(
                    "bbc-conv-1",
                    "BBC Learning English conversation",
                    "https://downloads.bbc.co.uk/learningenglish/features/6min/260326_6_minute_english_weight_loss_drugs_download_.mp3",
                    "试听",
                ),
            ),
        ),
        PodcastShow(
            id = "daily-easy",
            title = "Daily Easy English Expression",
            host = "Coach Shane",
            blurb = "美国日常表达 · 推荐★★★★",
            rssUrl = "https://dailyeasyenglish.libsyn.com/rss",
            vinylColor = Color(0xFF1A1610),
            labelColor = Color(0xFFF39C12),
            fallbackEpisodes = listOf(
                ep(
                    "dee-1",
                    "like PUTTY in my hands",
                    "https://traffic.libsyn.com/secure/dailyeasyenglish/1184_E3_PODCAST_like_PUTTY_in_my_hands.mp3",
                ),
                ep(
                    "dee-2",
                    "make no bones about it",
                    "https://traffic.libsyn.com/secure/dailyeasyenglish/1183_E3_PODCAST_make_no_bones_about_it.mp3",
                ),
                ep(
                    "dee-3",
                    "unplugged",
                    "https://traffic.libsyn.com/secure/dailyeasyenglish/1182_E3_PODCAST_unplugged.mp3",
                ),
            ),
        ),
        PodcastShow(
            id = "luke",
            title = "Luke's ENGLISH Podcast",
            host = "Luke Thompson",
            blurb = "英国英语、文化、生活 · 推荐★★★★",
            rssUrl = "https://feeds.acast.com/public/shows/62b0ada25c7ea10012f541cb",
            vinylColor = Color(0xFF1A1423),
            labelColor = Color(0xFF6C5CE7),
            fallbackEpisodes = listOf(
                ep("luke-1", "Luke's ENGLISH Podcast", "https://sphinx.acast.com/p/open/s/62b0ada25c7ea10012f541cb/e/6aa3cb7ab54f356fc7d2184c/media.mp3"),
                ep("luke-2", "Luke's ENGLISH Podcast", "https://sphinx.acast.com/p/open/s/62b0ada25c7ea10012f541cb/e/6a987a3dbe98057961a70e78/media.mp3"),
                ep("luke-3", "Luke's ENGLISH Podcast", "https://sphinx.acast.com/p/open/s/62b0ada25c7ea10012f541cb/e/6a7b3d4fd6c287f9ee7e73b1/media.mp3"),
            ),
        ),
        PodcastShow(
            id = "ielts-energy",
            title = "IELTS Energy English 7+",
            host = "All Ears English",
            blurb = "雅思、口语、词汇 · 推荐★★★★",
            rssUrl = "https://feeds.megaphone.fm/ALLE4310393056",
            vinylColor = Color(0xFF16121A),
            labelColor = Color(0xFFA29BFE),
            fallbackEpisodes = listOf(
                ep("ielts-1", "IELTS Energy", "https://traffic.megaphone.fm/ALLE5210085060.mp3"),
                ep("ielts-2", "IELTS Energy", "https://traffic.megaphone.fm/ALLE2885355413.mp3"),
                ep("ielts-3", "IELTS Energy", "https://traffic.megaphone.fm/ALLE1697840416.mp3"),
            ),
        ),
        PodcastShow(
            id = "reallife",
            title = "RealLife English",
            host = "RealLife English",
            blurb = "真实对话、口语 · 推荐★★★★",
            rssUrl = "https://rss.libsyn.com/shows/41632/destinations/126390.xml",
            vinylColor = Color(0xFF101816),
            labelColor = Color(0xFF00B894),
            fallbackEpisodes = listOf(
                ep("rl-1", "RealLife English #464", "https://traffic.libsyn.com/secure/reallifeeng/464.mp3"),
                ep("rl-2", "RealLife English #463", "https://traffic.libsyn.com/secure/reallifeeng/RealLife_English_Podcast_-_463.mp3"),
                ep("rl-3", "RealLife English #462", "https://traffic.libsyn.com/secure/reallifeeng/462.mp3"),
            ),
        ),
        PodcastShow(
            id = "american-english",
            title = "American English Podcast",
            host = "Shana Thompson",
            blurb = "美国文化 + 英语 · 推荐★★★",
            rssUrl = "https://feeds.megaphone.fm/americanenglishpodcast",
            vinylColor = Color(0xFF12161C),
            labelColor = Color(0xFF74B9FF),
            fallbackEpisodes = listOf(
                ep("aep-1", "American English Podcast", "https://traffic.megaphone.fm/IMP9395172794.mp3"),
                ep("aep-2", "American English Podcast", "https://traffic.megaphone.fm/IMP9692690728.mp3"),
                ep("aep-3", "American English Podcast", "https://traffic.megaphone.fm/IMP1571411882.mp3"),
            ),
        ),
        PodcastShow(
            id = "listening-time",
            title = "Listening Time",
            host = "English Practice",
            blurb = "听力训练、慢速英语 · 推荐★★★★",
            rssUrl = "https://feeds.simplecast.com/obaHfEr1",
            vinylColor = Color(0xFF1C1510),
            labelColor = Color(0xFFE17055),
            fallbackEpisodes = listOf(
                ep(
                    "lt-1",
                    "Listening Time practice",
                    "https://sonoro.simplecastaudio.com/93c48962-af4a-4b78-82d8-2f6e668e895a/episodes/6e4ea74b-d4c0-4098-b752-7c99bf70983d/audio/128/default.mp3?aid=rss_feed&feed=obaHfEr1",
                ),
                ep(
                    "lt-2",
                    "Listening Time practice",
                    "https://sonoro.simplecastaudio.com/93c48962-af4a-4b78-82d8-2f6e668e895a/episodes/1068d71a-88f3-412a-b5c8-fc8193d30a48/audio/128/default.mp3?aid=rss_feed&feed=obaHfEr1",
                ),
                ep(
                    "lt-3",
                    "Listening Time practice",
                    "https://sonoro.simplecastaudio.com/93c48962-af4a-4b78-82d8-2f6e668e895a/episodes/550fb076-c7e6-4d6f-95d6-b0428ec6848a/audio/128/default.mp3?aid=rss_feed&feed=obaHfEr1",
                ),
            ),
        ),
    )

    private fun ep(
        id: String,
        title: String,
        url: String,
        duration: String = "",
    ) = PodcastEpisode(
        id = id,
        title = title,
        audioUrl = url,
        durationLabel = duration,
    )
}
