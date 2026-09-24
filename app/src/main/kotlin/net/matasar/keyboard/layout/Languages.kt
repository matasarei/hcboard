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
        // The iPhone's twelve-key rows: the apostrophe ends the home row and ґ the shift row, so ь
        // needs no apostrophes on its long press.
        phoneRows = listOf("йцукенгшщзхї", "фівапролджє'", "ячсмитьбюґ"),
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
        tag = "pt_BR", nativeName = "Português", englishName = "Portuguese (Brazil)",
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

    val all: List<Language> = listOf(english, ukrainian, russian, bulgarian, french, spanish, german, italian, portuguese, polish)

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
