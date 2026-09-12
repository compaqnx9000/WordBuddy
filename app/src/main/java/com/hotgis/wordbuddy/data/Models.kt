package com.hotgis.wordbuddy.data

data class Definition(
    val pos: String,
    val meaning: String,
    val isUserAdded: Boolean = false,
) {
    val label: String
        get() = if (pos.isBlank()) meaning else "$pos $meaning"
}

data class ExampleSentence(
    val english: String,
    val chinese: String,
)

data class VocabEntry(
    val id: Long = 0L,
    val notebookId: Long = Notebook.DEFAULT_ID,
    val text: String,
    val isPhrase: Boolean = false,
    val ipaUk: String? = null,
    val ipaUs: String? = null,
    val definitions: List<Definition>,
    val examples: List<ExampleSentence> = emptyList(),
    val nearWords: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val imageBlob: ByteArray? = null,
    val sortOrder: Int = 0,
    val addedAtMillis: Long = System.currentTimeMillis(),
) {
    val definitionLine: String
        get() = definitions.joinToString("  ") { it.label }

    val hasImage: Boolean
        get() = imageBlob != null && imageBlob.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VocabEntry) return false
        return id == other.id &&
            notebookId == other.notebookId &&
            text == other.text &&
            isPhrase == other.isPhrase &&
            ipaUk == other.ipaUk &&
            ipaUs == other.ipaUs &&
            definitions == other.definitions &&
            examples == other.examples &&
            nearWords == other.nearWords &&
            synonyms == other.synonyms &&
            antonyms == other.antonyms &&
            sortOrder == other.sortOrder &&
            addedAtMillis == other.addedAtMillis &&
            imageBlob.contentEquals(other.imageBlob)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + notebookId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + isPhrase.hashCode()
        result = 31 * result + (ipaUk?.hashCode() ?: 0)
        result = 31 * result + (ipaUs?.hashCode() ?: 0)
        result = 31 * result + definitions.hashCode()
        result = 31 * result + examples.hashCode()
        result = 31 * result + nearWords.hashCode()
        result = 31 * result + synonyms.hashCode()
        result = 31 * result + antonyms.hashCode()
        result = 31 * result + (imageBlob?.contentHashCode() ?: 0)
        result = 31 * result + sortOrder
        result = 31 * result + addedAtMillis.hashCode()
        return result
    }
}

enum class WordFilter { ALL, WORDS, PHRASES }

data class Notebook(
    val id: Long,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val kind: String = KIND_USER,
    val slug: String? = null,
    val wordCount: Int = 0,
) {
    val isSystem: Boolean
        get() = kind == KIND_CATALOG

    companion object {
        const val DEFAULT_ID = 0L
        const val DEFAULT_NAME = "生词本"
        const val KIND_USER = "user"
        const val KIND_CATALOG = "catalog"
        const val CET4_SLUG = "cet4"
        const val CET6_SLUG = "cet6"
        const val ZHONGKAO_SLUG = "zhongkao"
        const val GAOKAO_SLUG = "gaokao"
    }
}

enum class SortMode { MANUAL, TIME_DESC, TIME_ASC, ALPHA }

enum class Accent { UK, US }

enum class AppTheme(val label: String) {
    Light("明亮"),
    Dark("暗黑"),
}

/** Accent swatches on Settings — each maps to a full [com.hotgis.wordbuddy.ui.lookup.StellarPalette]. */
enum class AccentStyle(val label: String, val swatchArgb: Long) {
    CyberNeon("赛博霓虹", 0xFF00F0FF),
    Emerald("翡翠薄雾", 0xFF2DD4BF),
    Electric("电光潮汐", 0xFF38BDF8),
    Solar("日光金辉", 0xFFF5C542),
    Aurora("极光粉", 0xFFF472B6),
    Ember("余烬珊瑚", 0xFFFF6B6B),
    Frost("霜蓝", 0xFF0E8A96),
    ForestStar("星野翠绿", 0xFF8CC63F),
}

enum class FontSizeOption(val label: String, val scale: Float) {
    Small("小", 0.9f),
    Normal("标准", 1f),
    Large("大", 1.12f),
    ExtraLarge("特大", 1.25f),
    ;

    companion object {
        fun fromScale(scale: Float): FontSizeOption {
            return entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: Normal
        }
    }
}

data class StudySettings(
    val displayName: String = "词搭子",
    val accent: Accent = Accent.US,
    val autoPlayIntervalMs: Long = 2500L,
    val loop: Boolean = true,
    val speakOnPageChange: Boolean = true,
    val dailyReminder: Boolean = true,
    val aiImageAutoGen: Boolean = false,
    val fontScale: Float = FontSizeOption.Normal.scale,
    val appTheme: AppTheme = AppTheme.Dark,
    val accentStyle: AccentStyle = AccentStyle.CyberNeon,
    val defaultNotebookId: Long = Notebook.DEFAULT_ID,
    /** When true and logged in, require fingerprint unlock when reopening the app. */
    val biometricLogin: Boolean = false,
)

data class LookupResult(
    val entry: VocabEntry,
    val saved: Boolean,
)
