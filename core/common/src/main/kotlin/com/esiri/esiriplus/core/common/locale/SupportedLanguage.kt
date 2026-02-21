package com.esiri.esiriplus.core.common.locale

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRecommended: Boolean = false,
)

val supportedLanguages = listOf(
    // Recommended (fully supported)
    SupportedLanguage("en", "English", "English", isRecommended = true),
    SupportedLanguage("sw", "Swahili", "Kiswahili", isRecommended = true),
    // Other languages (coming soon)
    SupportedLanguage("af", "Afrikaans", "Afrikaans"),
    SupportedLanguage("am", "Amharic", "\u12A0\u121B\u122D\u129B"),
    SupportedLanguage("ar", "Arabic", "\u0627\u0644\u0639\u0631\u0628\u064A\u0629"),
    SupportedLanguage("bn", "Bengali", "\u09AC\u09BE\u0982\u09B2\u09BE"),
    SupportedLanguage("zh", "Chinese", "\u4E2D\u6587"),
    SupportedLanguage("nl", "Dutch", "Nederlands"),
    SupportedLanguage("fi", "Finnish", "Suomi"),
    SupportedLanguage("fr", "French", "Fran\u00E7ais"),
    SupportedLanguage("de", "German", "Deutsch"),
    SupportedLanguage("el", "Greek", "\u0395\u03BB\u03BB\u03B7\u03BD\u03B9\u03BA\u03AC"),
    SupportedLanguage("ha", "Hausa", "Hausa"),
    SupportedLanguage("he", "Hebrew", "\u05E2\u05D1\u05E8\u05D9\u05EA"),
    SupportedLanguage("hi", "Hindi", "\u0939\u093F\u0928\u094D\u0926\u0940"),
    SupportedLanguage("ig", "Igbo", "Igbo"),
    SupportedLanguage("id", "Indonesian", "Bahasa Indonesia"),
    SupportedLanguage("it", "Italian", "Italiano"),
    SupportedLanguage("ja", "Japanese", "\u65E5\u672C\u8A9E"),
    SupportedLanguage("ko", "Korean", "\uD55C\uAD6D\uC5B4"),
    SupportedLanguage("ms", "Malay", "Bahasa Melayu"),
    SupportedLanguage("no", "Norwegian", "Norsk"),
    SupportedLanguage("pl", "Polish", "Polski"),
    SupportedLanguage("pt", "Portuguese", "Portugu\u00EAs"),
    SupportedLanguage("ru", "Russian", "\u0420\u0443\u0441\u0441\u043A\u0438\u0439"),
    SupportedLanguage("es", "Spanish", "Espa\u00F1ol"),
    SupportedLanguage("sv", "Swedish", "Svenska"),
    SupportedLanguage("tl", "Tagalog", "Tagalog"),
    SupportedLanguage("th", "Thai", "\u0E44\u0E17\u0E22"),
    SupportedLanguage("tr", "Turkish", "T\u00FCrk\u00E7e"),
    SupportedLanguage("uk", "Ukrainian", "\u0423\u043A\u0440\u0430\u0457\u043D\u0441\u044C\u043A\u0430"),
    SupportedLanguage("ur", "Urdu", "\u0627\u0631\u062F\u0648"),
    SupportedLanguage("vi", "Vietnamese", "Ti\u1EBFng Vi\u1EC7t"),
    SupportedLanguage("yo", "Yoruba", "Yor\u00F9b\u00E1"),
    SupportedLanguage("zu", "Zulu", "isiZulu"),
)
