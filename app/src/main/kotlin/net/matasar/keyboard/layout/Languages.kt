package net.matasar.keyboard.layout

/** The languages the keyboard ships, in the order the globe key cycles through them. */
object Languages {

    val english = Language(
        tag = "en_US", nativeName = "English", englishName = "English",
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = Accents,
    )

    val ukrainian = Language(
        tag = "uk", nativeName = "Українська", englishName = "Ukrainian",
        rows = listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбю"),
        accents = mapOf('г' to listOf("ґ"), 'ь' to listOf("'", "ʼ"), 'е' to listOf("ё"), 'и' to listOf("ы")),
    )

    val russian = Language(
        tag = "ru", nativeName = "Русский", englishName = "Russian",
        rows = listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю"),
        accents = mapOf('е' to listOf("ё"), 'ь' to listOf("ъ"), 'и' to listOf("і", "ї"), 'г' to listOf("ґ")),
    )

    val french = Language(
        tag = "fr", nativeName = "Français", englishName = "French",
        rows = listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"),
        accents = mapOf(
            'e' to listOf("é", "è", "ê", "ë"), 'a' to listOf("à", "â", "æ"), 'c' to listOf("ç"),
            'u' to listOf("ù", "û", "ü"), 'o' to listOf("ô", "œ", "ö"), 'i' to listOf("î", "ï"), 'y' to listOf("ÿ"),
        ),
    )

    val spanish = Language(
        tag = "es", nativeName = "Español", englishName = "Spanish",
        rows = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
        accents = mapOf('a' to listOf("á"), 'e' to listOf("é"), 'i' to listOf("í"), 'o' to listOf("ó"), 'u' to listOf("ú", "ü"), 'n' to listOf("ñ")),
    )

    val german = Language(
        tag = "de", nativeName = "Deutsch", englishName = "German",
        rows = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"),
        accents = mapOf('s' to listOf("ß"), 'a' to listOf("ä", "à"), 'o' to listOf("ö"), 'u' to listOf("ü"), 'e' to listOf("é", "è")),
    )

    val italian = Language(
        tag = "it", nativeName = "Italiano", englishName = "Italian",
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf('a' to listOf("à"), 'e' to listOf("è", "é"), 'i' to listOf("ì", "í"), 'o' to listOf("ò", "ó"), 'u' to listOf("ù", "ú")),
    )

    val portuguese = Language(
        tag = "pt_BR", nativeName = "Português", englishName = "Portuguese (Brazil)",
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to listOf("á", "ã", "à", "â"), 'e' to listOf("é", "ê"), 'i' to listOf("í"),
            'o' to listOf("ó", "õ", "ô"), 'u' to listOf("ú", "ü"), 'c' to listOf("ç"),
        ),
    )

    val polish = Language(
        tag = "pl", nativeName = "Polski", englishName = "Polish",
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to listOf("ą"), 'c' to listOf("ć"), 'e' to listOf("ę"), 'l' to listOf("ł"), 'n' to listOf("ń"),
            'o' to listOf("ó"), 's' to listOf("ś"), 'z' to listOf("ź", "ż"),
        ),
    )

    val all: List<Language> = listOf(english, ukrainian, russian, french, spanish, german, italian, portuguese, polish)

    fun byTag(tag: String): Language? = all.firstOrNull { it.tag == tag }

    /** The shipped language for an ISO 639 code (`uk`, `pt`), regardless of region; null when none. */
    fun byIsoLanguage(code: String): Language? = all.firstOrNull { it.tag.substringBefore('_') == code.lowercase() }

    /**
     * What to switch on before the user has chosen anything: English, plus every shipped
     * language among the phone's own languages, so a phone set to Ukrainian and Russian starts
     * with those and English rather than English alone.
     */
    fun defaultEnabled(systemLocales: List<java.util.Locale>): Set<String> =
        setOf(english.tag) + systemLocales.mapNotNull { byIsoLanguage(it.language)?.tag }

    /** The next enabled language after [current], wrapping around; [current] itself when it is the only one. */
    fun next(current: Language, enabledTags: Set<String>): Language {
        val enabled = all.filter { it.tag in enabledTags }.ifEmpty { listOf(english) }
        val index = enabled.indexOf(current)
        return if (index < 0) enabled.first() else enabled[(index + 1) % enabled.size]
    }
}
