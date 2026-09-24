package net.matasar.keyboard.layout

/** The languages the keyboard ships, in the order the globe key cycles through them. */
object Languages {

    val english = Language(
        tag = "en_US", nativeName = "English", englishName = "English",
        subtypeId = 0x68630001,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = Accents,
    )

    val ukrainian = Language(
        tag = "uk", nativeName = "Українська", englishName = "Ukrainian",
        subtypeId = 0x68630002,
        rows = listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбю"),
        accents = mapOf(
            'г' to listOf("ґ"), 'е' to listOf("ё"), 'и' to listOf("ы"),
            '\'' to listOf("ʼ", "’"),
        ),
        // The apostrophe ends the home row, so ь needs no apostrophes on its long press; ґ is a long
        // press on г, which leaves the shift row its wider Shift and backspace.
        phoneRows = listOf("йцукенгшщзхї", "фівапролджє'", "ячсмитьбю"),
    )

    val russian = Language(
        tag = "ru", nativeName = "Русский", englishName = "Russian",
        subtypeId = 0x68630003,
        rows = listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"),
        accents = mapOf('е' to listOf("ё"), 'ь' to listOf("ъ"), 'и' to listOf("і", "ї", "ѝ"), 'г' to listOf("ґ")),
        // The iPhone leaves ъ to a long press on ь; the 60% board keeps it on `]`.
        phoneRows = listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"),
    )

    val french = Language(
        tag = "fr", nativeName = "Français", englishName = "French",
        subtypeId = 0x68630005,
        rows = listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"),
        accents = mapOf(
            'e' to listOf("é", "è", "ê", "ë"), 'a' to listOf("à", "â", "æ"), 'c' to listOf("ç"),
            'u' to listOf("ù", "û", "ü"), 'o' to listOf("ô", "œ", "ö"), 'i' to listOf("î", "ï"), 'y' to listOf("ÿ"),
        ),
    )

    val spanish = Language(
        tag = "es", nativeName = "Español", englishName = "Spanish",
        subtypeId = 0x68630006,
        rows = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
        accents = mapOf('a' to listOf("á"), 'e' to listOf("é"), 'i' to listOf("í"), 'o' to listOf("ó"), 'u' to listOf("ú", "ü"), 'n' to listOf("ñ")),
    )

    val german = Language(
        tag = "de", nativeName = "Deutsch", englishName = "German",
        subtypeId = 0x68630007,
        rows = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"),
        accents = mapOf('s' to listOf("ß"), 'a' to listOf("ä", "à"), 'o' to listOf("ö"), 'u' to listOf("ü"), 'e' to listOf("é", "è")),
    )

    val italian = Language(
        tag = "it", nativeName = "Italiano", englishName = "Italian",
        subtypeId = 0x68630008,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf('a' to listOf("à"), 'e' to listOf("è", "é"), 'i' to listOf("ì", "í"), 'o' to listOf("ò", "ó"), 'u' to listOf("ù", "ú")),
    )

    val portuguese = Language(
        // One language with two spellings: PortugueseSpelling picks Portugal's or Brazil's word list.
        tag = "pt", nativeName = "Português", englishName = "Portuguese",
        subtypeId = 0x68630009,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to listOf("á", "ã", "à", "â"), 'e' to listOf("é", "ê"), 'i' to listOf("í"),
            'o' to listOf("ó", "õ", "ô"), 'u' to listOf("ú", "ü"), 'c' to listOf("ç"),
        ),
    )

    val polish = Language(
        tag = "pl", nativeName = "Polski", englishName = "Polish",
        subtypeId = 0x6863000A,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to listOf("ą"), 'c' to listOf("ć"), 'e' to listOf("ę"), 'l' to listOf("ł"), 'n' to listOf("ń"),
            'o' to listOf("ó"), 's' to listOf("ś"), 'z' to listOf("ź", "ż"),
        ),
    )

    val dutch = Language(
        tag = "nl", nativeName = "Nederlands", englishName = "Dutch",
        subtypeId = 0x6863000B,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'e' to listOf("é", "ë", "è", "ê"), 'a' to listOf("á", "ä", "à"), 'i' to listOf("ï", "í"),
            'o' to listOf("ó", "ö", "ô"), 'u' to listOf("ü", "ú"),
        ),
    )

    val swedish = Language(
        tag = "sv", nativeName = "Svenska", englishName = "Swedish",
        subtypeId = 0x6863000C,
        rows = listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
        accents = mapOf('e' to listOf("é"), 'a' to listOf("à")),
    )

    val danish = Language(
        tag = "da", nativeName = "Dansk", englishName = "Danish",
        subtypeId = 0x6863000D,
        rows = listOf("qwertyuiopå", "asdfghjklæø", "zxcvbnm"),
        accents = mapOf('e' to listOf("é"), 'o' to listOf("ö"), 'a' to listOf("ä")),
    )

    val finnish = Language(
        tag = "fi", nativeName = "Suomi", englishName = "Finnish",
        subtypeId = 0x6863000E,
        rows = listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
        accents = mapOf('s' to listOf("š"), 'z' to listOf("ž")),
    )

    val czech = Language(
        tag = "cs", nativeName = "Čeština", englishName = "Czech",
        subtypeId = 0x6863000F,
        rows = listOf("qwertzuiopú", "asdfghjklů", "yxcvbnm"),
        accents = mapOf(
            'e' to listOf("ě", "é"), 's' to listOf("š"), 'c' to listOf("č"), 'r' to listOf("ř"),
            'z' to listOf("ž"), 'y' to listOf("ý"), 'a' to listOf("á"), 'i' to listOf("í"), 'd' to listOf("ď"),
            't' to listOf("ť"), 'n' to listOf("ň"), 'o' to listOf("ó"), 'u' to listOf("ú", "ů"),
        ),
    )

    val romanian = Language(
        tag = "ro", nativeName = "Română", englishName = "Romanian",
        subtypeId = 0x68630010,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf('a' to listOf("ă", "â"), 'i' to listOf("î"), 's' to listOf("ș"), 't' to listOf("ț")),
    )

    val greek = Language(
        tag = "el", nativeName = "Ελληνικά", englishName = "Greek",
        subtypeId = 0x68630011,
        rows = listOf(";ςερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ"),
        accents = mapOf(
            'α' to listOf("ά"), 'ε' to listOf("έ"), 'η' to listOf("ή"), 'ι' to listOf("ί", "ϊ", "ΐ"),
            'ο' to listOf("ό"), 'υ' to listOf("ύ", "ϋ", "ΰ"), 'ω' to listOf("ώ"),
        ),
    )

    val croatian = Language(
        tag = "hr", nativeName = "Hrvatski", englishName = "Croatian",
        subtypeId = 0x68630012,
        // The Croatian PC board on the 60% layout (č and ć on ; and ', ž where \ is on a PC, so
        // on a long press of z here); the phone has ž on its home row.
        rows = listOf("qwertzuiopšđ", "asdfghjklčć", "yxcvbnm"),
        accents = mapOf('z' to listOf("ž")),
        phoneRows = listOf("qwertzuiopšđ", "asdfghjklčćž", "yxcvbnm"),
    )

    val slovenian = Language(
        tag = "sl", nativeName = "Slovenščina", englishName = "Slovenian",
        subtypeId = 0x68630013,
        rows = listOf("qwertzuiopš", "asdfghjklčž", "yxcvbnm"),
        accents = mapOf('c' to listOf("ć"), 'd' to listOf("đ")),
    )

    val lithuanian = Language(
        tag = "lt", nativeName = "Lietuvių", englishName = "Lithuanian",
        subtypeId = 0x68630014,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to listOf("ą"), 'c' to listOf("č"), 'e' to listOf("ę", "ė"), 'i' to listOf("į"),
            's' to listOf("š"), 'u' to listOf("ų", "ū"), 'z' to listOf("ž"),
        ),
    )

    val latvian = Language(
        tag = "lv", nativeName = "Latviešu", englishName = "Latvian",
        subtypeId = 0x68630015,
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to listOf("ā"), 'c' to listOf("č"), 'e' to listOf("ē"), 'g' to listOf("ģ"), 'i' to listOf("ī"),
            'k' to listOf("ķ"), 'l' to listOf("ļ"), 'n' to listOf("ņ"), 's' to listOf("š"), 'u' to listOf("ū"),
            'z' to listOf("ž"),
        ),
    )

    val bulgarian = Language(
        tag = "bg", nativeName = "Български", englishName = "Bulgarian",
        subtypeId = 0x68630004,
        rows = listOf("явертъуиопч", "асдфгхйклшщ", "зьцжбнмю"),
        accents = mapOf('и' to listOf("ѝ")),
    )

    /**
     * Bulgarian on the standard board (БДС), as the iPhone lays it out. The same language as
     * [bulgarian] — tag, subtype, word list — so it is not in [all]; [resolve] swaps it in.
     */
    val bulgarianStandard = bulgarian.copy(
        rows = listOf("уеишщксдзцб", "ьяаожгтнвмч", "юйъэфхпрл"),
        phoneRows = listOf("уеишщксдзцб", "ьяаожгтнвмч", "юйъэфхпрл"),
    )

    val all: List<Language> = listOf(
        english, ukrainian, russian, bulgarian, french, spanish, german, italian, portuguese, polish,
        dutch, swedish, danish, finnish, czech, romanian, greek, croatian, slovenian, lithuanian, latvian,
    )

    /** Portuguese's tag before it had two spellings: stored settings, words and backups may still hold it. */
    const val LEGACY_PORTUGUESE_TAG = "pt_BR"

    /** A stored language tag as the keyboard knows it now: [LEGACY_PORTUGUESE_TAG] is Portuguese. */
    fun migrateTag(tag: String): String = if (tag == LEGACY_PORTUGUESE_TAG) portuguese.tag else tag

    /**
     * The word list [tag] loads: Russian with Bulgarian vocabulary when [ruBulgarianVocabulary]
     * asks for it, Portuguese in [portugueseSpelling], every other language its own. Words the
     * user adds are keyed by [tag], so they reach every list a language can load.
     */
    fun assetFor(tag: String, ruBulgarianVocabulary: Boolean, portugueseSpelling: PortugueseSpelling): String = when {
        tag == russian.tag && ruBulgarianVocabulary -> "ru_bg"
        tag == portuguese.tag -> if (portugueseSpelling == PortugueseSpelling.BRAZIL) "pt_BR" else "pt_PT"
        else -> tag
    }

    /** [language] as the user lays it out: Bulgarian's standard board when [bulgarianLayout] asks for it. */
    fun resolve(language: Language, bulgarianLayout: BulgarianLayout): Language =
        if (language.tag == bulgarian.tag && bulgarianLayout == BulgarianLayout.STANDARD) bulgarianStandard else language

    fun byTag(tag: String): Language? = all.firstOrNull { it.tag == tag }

    /** The language whose Android subtype has [id] (`res/xml/method.xml`); null for any other subtype. */
    fun bySubtypeId(id: Int): Language? = all.firstOrNull { it.subtypeId == id }

    /** The shipped language for an ISO 639 code (`uk`, `pt`), regardless of region; null when none. */
    fun byIsoLanguage(code: String): Language? = all.firstOrNull { it.tag.substringBefore('_') == code.lowercase() }

    /**
     * What to switch on before the user has chosen anything: English only.
     * Additional languages are added by the user in settings.
     */
    fun defaultEnabled(systemLocales: List<java.util.Locale> = emptyList()): Set<String> =
        setOf(english.tag)

    /** The next enabled language after [current], wrapping around; [current] itself when it is the only one. */
    fun next(current: Language, enabledTags: Set<String>): Language {
        val enabled = all.filter { it.tag in enabledTags }.ifEmpty { listOf(english) }
        val index = enabled.indexOf(current)
        return if (index < 0) enabled.first() else enabled[(index + 1) % enabled.size]
    }
}
