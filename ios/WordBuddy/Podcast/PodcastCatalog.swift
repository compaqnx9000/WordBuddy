import Foundation

struct PodcastEpisode: Identifiable, Equatable, Hashable {
    var id: String
    var title: String
    var audioUrl: String
    var summary: String = ""
    var durationLabel: String = ""
}

struct PodcastShow: Identifiable, Equatable, Hashable {
    var id: String
    var title: String
    var host: String
    var blurb: String
    var rssUrl: String?
    var vinyl: UInt32
    var label: UInt32
    var fallbackEpisodes: [PodcastEpisode]

    var isLive: Bool { rssUrl == nil }
}

enum PodcastCatalog {
    static let shows: [PodcastShow] = [
        PodcastShow(
            id: "npr-live",
            title: "NPR News",
            host: "NPR · Live Radio",
            blurb: "美式英语新闻电台 · 24h 直播 · 大陆可听",
            rssUrl: nil,
            vinyl: 0x0F1A24,
            label: 0x2E7DFF,
            fallbackEpisodes: [
                episode("npr-live-now", "LIVE · NPR News", "https://npr-ice.streamguys1.com/live.mp3", "直播中"),
            ]
        ),
        PodcastShow(
            id: "lbc-live",
            title: "LBC",
            host: "LBC UK · Live Radio",
            blurb: "英式谈话电台 · 新闻访谈 · 大陆可听",
            rssUrl: nil,
            vinyl: 0x1A1010,
            label: 0xE53935,
            fallbackEpisodes: [
                episode("lbc-live-now", "LIVE · LBC UK", "https://media-ice.musicradio.com/LBCUK", "直播中"),
            ]
        ),
        PodcastShow(
            id: "cri-live",
            title: "China Plus",
            host: "CRI · Live Radio",
            blurb: "中国国际广播英文台 · 24h 直播",
            rssUrl: nil,
            vinyl: 0x1C0E10,
            label: 0xE53935,
            fallbackEpisodes: [
                episode("cri-live-now", "LIVE · China Plus Radio", "https://sk.cri.cn/am846.m3u8", "直播中"),
            ]
        ),
        PodcastShow(
            id: "china-plus-special",
            title: "Special English",
            host: "China Plus · CRI",
            blurb: "中国国际广播 · 慢速英语 · 免费官方",
            rssUrl: "https://aezfm.meldingcloud.com/rss/program/3",
            vinyl: 0x1A1014,
            label: 0xC62828,
            fallbackEpisodes: [
                episode("cps-1", "China's space crew assesses emergency choices", "https://radio-res.cgtn.com/ueditor/audio/2607/1084883108332.mp3"),
                episode("cps-2", "Macao int'l youth dance festival", "https://radio-res.cgtn.com/ueditor/audio/2607/1084711170707.mp3"),
                episode("cps-3", "World AI Cooperation Organization", "https://radio-res.cgtn.com/ueditor/audio/2607/1084537044175.mp3"),
            ]
        ),
        PodcastShow(
            id: "china-plus-headlines",
            title: "Headline News",
            host: "China Plus · CRI",
            blurb: "中国国际广播 · 英文短新闻 · 免费官方",
            rssUrl: "https://cgtn-radio-data.cgtn.com/rss/programother/159",
            vinyl: 0x14181C,
            label: 0xD4A017,
            fallbackEpisodes: [
                episode("cph-1", "China receives 58 returned cultural artifacts", "https://radio-res.cgtn.com/ueditor/audio/2609/1189654054791.mp3"),
                episode("cph-2", "Kremlin criticizes US sanctions bill", "https://radio-res.cgtn.com/ueditor/audio/2609/1089648831242.mp3"),
                episode("cph-3", "Chinese runner Zhao Jiaju wins Tor des Geants", "https://radio-res.cgtn.com/ueditor/audio/2609/1089636309299.mp3"),
            ]
        ),
        PodcastShow(
            id: "allears",
            title: "All Ears English",
            host: "Lindsay & Michelle",
            blurb: "日常英语、表达、文化 · 推荐★★★★★",
            rssUrl: "https://feeds.megaphone.fm/allearsenglish",
            vinyl: 0x1A1218,
            label: 0xE84393,
            fallbackEpisodes: [
                episode("allears-1", "Latest All Ears English", "https://traffic.megaphone.fm/ALLE7938430338.mp3"),
                episode("allears-2", "All Ears English episode", "https://traffic.megaphone.fm/ALLE1735931865.mp3"),
                episode("allears-3", "All Ears English episode", "https://traffic.megaphone.fm/ALLE1019691198.mp3"),
            ]
        ),
        PodcastShow(
            id: "bbc6min",
            title: "6 Minute English",
            host: "BBC Learning English",
            blurb: "BBC 短篇英语、词汇、新闻 · 推荐★★★★★",
            rssUrl: "https://podcasts.files.bbci.co.uk/p02pc9tn.rss",
            vinyl: 0x1B1B1F,
            label: 0xBB1919,
            fallbackEpisodes: [
                episode("bbc6-1", "Weight loss drugs", "https://downloads.bbc.co.uk/learningenglish/features/6min/260326_6_minute_english_weight_loss_drugs_download_.mp3", "6 min"),
                episode("bbc6-2", "How do we adapt to the cold?", "https://downloads.bbc.co.uk/learningenglish/features/6min/260319_6_minute_english_how_do_we_adapt_to_the_cold_download_.mp3", "6 min"),
                episode("bbc6-3", "Is coffee good for you?", "https://downloads.bbc.co.uk/learningenglish/features/6min/260312_6_minute_english_is_coffee_good_for_you_download_.mp3", "6 min"),
            ]
        ),
        PodcastShow(
            id: "englishpod",
            title: "English Learning Podcast",
            host: "EnglishPod",
            blurb: "对话、场景英语 · 推荐★★★★",
            rssUrl: "https://anchor.fm/s/11380dcac/podcast/rss",
            vinyl: 0x12181F,
            label: 0x0984E3,
            fallbackEpisodes: [
                episode("epod-1", "EnglishPod lesson", "https://d3ctxlq1ktw2nl.cloudfront.net/staging/2026-5-16/47f5525e-e593-4bf0-60bc-6e2f572aa622.mp3"),
                episode("epod-2", "EnglishPod lesson", "https://d3ctxlq1ktw2nl.cloudfront.net/staging/2026-5-16/61576ad8-c0c2-63aa-be2a-e1275aae2800.mp3"),
                episode("epod-3", "EnglishPod lesson", "https://d3ctxlq1ktw2nl.cloudfront.net/staging/2026-5-16/e2041057-05d2-993c-6ea5-d1feaa31bcaf.mp3"),
            ]
        ),
        PodcastShow(
            id: "bbc-conversations",
            title: "Learning English Conversations",
            host: "BBC Learning English",
            blurb: "日常对话与地道表达 · 推荐★★★★",
            rssUrl: "https://podcasts.files.bbci.co.uk/p02pc9zn.rss",
            vinyl: 0x141820,
            label: 0x0B7A75,
            fallbackEpisodes: [
                episode("bbc-conv-1", "BBC Learning English conversation", "https://downloads.bbc.co.uk/learningenglish/features/6min/260326_6_minute_english_weight_loss_drugs_download_.mp3", "试听"),
            ]
        ),
        PodcastShow(
            id: "daily-easy",
            title: "Daily Easy English Expression",
            host: "Coach Shane",
            blurb: "美国日常表达 · 推荐★★★★",
            rssUrl: "https://dailyeasyenglish.libsyn.com/rss",
            vinyl: 0x1A1610,
            label: 0xF39C12,
            fallbackEpisodes: [
                episode("dee-1", "like PUTTY in my hands", "https://traffic.libsyn.com/secure/dailyeasyenglish/1184_E3_PODCAST_like_PUTTY_in_my_hands.mp3"),
                episode("dee-2", "make no bones about it", "https://traffic.libsyn.com/secure/dailyeasyenglish/1183_E3_PODCAST_make_no_bones_about_it.mp3"),
                episode("dee-3", "unplugged", "https://traffic.libsyn.com/secure/dailyeasyenglish/1182_E3_PODCAST_unplugged.mp3"),
            ]
        ),
        PodcastShow(
            id: "luke",
            title: "Luke's ENGLISH Podcast",
            host: "Luke Thompson",
            blurb: "英国英语、文化、生活 · 推荐★★★★",
            rssUrl: "https://feeds.acast.com/public/shows/62b0ada25c7ea10012f541cb",
            vinyl: 0x1A1423,
            label: 0x6C5CE7,
            fallbackEpisodes: [
                episode("luke-1", "Luke's ENGLISH Podcast", "https://sphinx.acast.com/p/open/s/62b0ada25c7ea10012f541cb/e/6aa3cb7ab54f356fc7d2184c/media.mp3"),
                episode("luke-2", "Luke's ENGLISH Podcast", "https://sphinx.acast.com/p/open/s/62b0ada25c7ea10012f541cb/e/6a987a3dbe98057961a70e78/media.mp3"),
                episode("luke-3", "Luke's ENGLISH Podcast", "https://sphinx.acast.com/p/open/s/62b0ada25c7ea10012f541cb/e/6a7b3d4fd6c287f9ee7e73b1/media.mp3"),
            ]
        ),
        PodcastShow(
            id: "ielts-energy",
            title: "IELTS Energy English 7+",
            host: "All Ears English",
            blurb: "雅思、口语、词汇 · 推荐★★★★",
            rssUrl: "https://feeds.megaphone.fm/ALLE4310393056",
            vinyl: 0x16121A,
            label: 0xA29BFE,
            fallbackEpisodes: [
                episode("ielts-1", "IELTS Energy", "https://traffic.megaphone.fm/ALLE5210085060.mp3"),
                episode("ielts-2", "IELTS Energy", "https://traffic.megaphone.fm/ALLE2885355413.mp3"),
                episode("ielts-3", "IELTS Energy", "https://traffic.megaphone.fm/ALLE1697840416.mp3"),
            ]
        ),
        PodcastShow(
            id: "reallife",
            title: "RealLife English",
            host: "RealLife English",
            blurb: "真实对话、口语 · 推荐★★★★",
            rssUrl: "https://rss.libsyn.com/shows/41632/destinations/126390.xml",
            vinyl: 0x101816,
            label: 0x00B894,
            fallbackEpisodes: [
                episode("rl-1", "RealLife English #464", "https://traffic.libsyn.com/secure/reallifeeng/464.mp3"),
                episode("rl-2", "RealLife English #463", "https://traffic.libsyn.com/secure/reallifeeng/RealLife_English_Podcast_-_463.mp3"),
                episode("rl-3", "RealLife English #462", "https://traffic.libsyn.com/secure/reallifeeng/462.mp3"),
            ]
        ),
        PodcastShow(
            id: "american-english",
            title: "American English Podcast",
            host: "Shana Thompson",
            blurb: "美国文化 + 英语 · 推荐★★★",
            rssUrl: "https://feeds.megaphone.fm/americanenglishpodcast",
            vinyl: 0x12161C,
            label: 0x74B9FF,
            fallbackEpisodes: [
                episode("aep-1", "American English Podcast", "https://traffic.megaphone.fm/IMP9395172794.mp3"),
                episode("aep-2", "American English Podcast", "https://traffic.megaphone.fm/IMP9692690728.mp3"),
                episode("aep-3", "American English Podcast", "https://traffic.megaphone.fm/IMP1571411882.mp3"),
            ]
        ),
        PodcastShow(
            id: "listening-time",
            title: "Listening Time",
            host: "English Practice",
            blurb: "听力训练、慢速英语 · 推荐★★★★",
            rssUrl: "https://feeds.simplecast.com/obaHfEr1",
            vinyl: 0x1C1510,
            label: 0xE17055,
            fallbackEpisodes: [
                episode("lt-1", "Listening Time practice", "https://sonoro.simplecastaudio.com/93c48962-af4a-4b78-82d8-2f6e668e895a/episodes/6e4ea74b-d4c0-4098-b752-7c99bf70983d/audio/128/default.mp3?aid=rss_feed&feed=obaHfEr1"),
                episode("lt-2", "Listening Time practice", "https://sonoro.simplecastaudio.com/93c48962-af4a-4b78-82d8-2f6e668e895a/episodes/1068d71a-88f3-412a-b5c8-fc8193d30a48/audio/128/default.mp3?aid=rss_feed&feed=obaHfEr1"),
                episode("lt-3", "Listening Time practice", "https://sonoro.simplecastaudio.com/93c48962-af4a-4b78-82d8-2f6e668e895a/episodes/550fb076-c7e6-4d6f-95d6-b0428ec6848a/audio/128/default.mp3?aid=rss_feed&feed=obaHfEr1"),
            ]
        ),
    ]

    private static func episode(
        _ id: String,
        _ title: String,
        _ url: String,
        _ duration: String = ""
    ) -> PodcastEpisode {
        PodcastEpisode(id: id, title: title, audioUrl: url, durationLabel: duration)
    }
}
