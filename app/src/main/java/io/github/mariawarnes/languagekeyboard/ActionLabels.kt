package io.github.mariawarnes.languagekeyboard

/**
 * Words for the Enter key's actions, in the language being typed, so even the keyboard's own
 * buttons are practice. Send and Search are icons, so only these four need words.
 */
object ActionLabels {

    enum class Action { GO, NEXT, DONE, PREVIOUS }

    // Go, Next, Done, Previous — short button-style forms.
    private val LABELS: Map<String, List<String>> = mapOf(
        "en" to listOf("Go", "Next", "Done", "Prev"),
        "es" to listOf("Ir", "Siguiente", "Listo", "Anterior"),
        "fr" to listOf("Aller", "Suivant", "Terminé", "Précédent"),
        "de" to listOf("Los", "Weiter", "Fertig", "Zurück"),
        "it" to listOf("Vai", "Avanti", "Fine", "Indietro"),
        "pt" to listOf("Ir", "Avançar", "Concluído", "Voltar"),
        "nl" to listOf("Ga", "Volgende", "Klaar", "Vorige"),
        "ca" to listOf("Ves", "Següent", "Fet", "Anterior"),
        "gl" to listOf("Ir", "Seguinte", "Feito", "Anterior"),
        "pl" to listOf("Idź", "Dalej", "Gotowe", "Wstecz"),
        "ro" to listOf("Mergi", "Înainte", "Gata", "Înapoi"),
        "sv" to listOf("Kör", "Nästa", "Klar", "Bakåt"),
        "da" to listOf("Gå", "Næste", "Færdig", "Forrige"),
        "no" to listOf("Gå", "Neste", "Ferdig", "Forrige"),
        "fi" to listOf("Siirry", "Seuraava", "Valmis", "Edellinen"),
        "is" to listOf("Áfram", "Næsta", "Lokið", "Fyrri"),
        "et" to listOf("Mine", "Järgmine", "Valmis", "Eelmine"),
        "lt" to listOf("Eiti", "Kitas", "Atlikta", "Atgal"),
        "lv" to listOf("Aiziet", "Tālāk", "Gatavs", "Atpakaļ"),
        "cs" to listOf("Přejít", "Další", "Hotovo", "Zpět"),
        "sk" to listOf("Prejsť", "Ďalej", "Hotovo", "Späť"),
        "hu" to listOf("Ugrás", "Tovább", "Kész", "Előző"),
        "hr" to listOf("Idi", "Dalje", "Gotovo", "Natrag"),
        "sl" to listOf("Pojdi", "Naprej", "Končano", "Nazaj"),
        "sq" to listOf("Shko", "Tjetër", "U krye", "Prapa"),
        "tr" to listOf("Git", "İleri", "Bitti", "Geri"),
        "ru" to listOf("Перейти", "Далее", "Готово", "Назад"),
        "uk" to listOf("Перейти", "Далі", "Готово", "Назад"),
        "be" to listOf("Перайсці", "Далей", "Гатова", "Назад"),
        "bg" to listOf("Отиди", "Напред", "Готово", "Назад"),
        "mk" to listOf("Оди", "Следно", "Готово", "Назад"),
        "el" to listOf("Μετάβαση", "Επόμενο", "Τέλος", "Πίσω"),
        "he" to listOf("מעבר", "הבא", "סיום", "הקודם"),
        "ar" to listOf("انتقال", "التالي", "تم", "السابق"),
        "fa" to listOf("برو", "بعدی", "تمام", "قبلی"),
        "ur" to listOf("جائیں", "اگلا", "ہو گیا", "پچھلا"),
        "ka" to listOf("გადასვლა", "შემდეგი", "მზადაა", "წინა"),
        "vi" to listOf("Đi", "Tiếp", "Xong", "Trước"),
        "id" to listOf("Buka", "Berikutnya", "Selesai", "Sebelumnya"),
        "ms" to listOf("Pergi", "Seterusnya", "Selesai", "Sebelumnya"),
        "sw" to listOf("Nenda", "Ifuatayo", "Imekamilika", "Iliyotangulia"),
        "tl" to listOf("Pumunta", "Susunod", "Tapos na", "Nakaraan"),
        "af" to listOf("Gaan", "Volgende", "Klaar", "Vorige"),
        "ga" to listOf("Téigh", "Ar aghaidh", "Déanta", "Siar"),
        "cy" to listOf("Ewch", "Nesaf", "Gorffen", "Blaenorol"),
        "ht" to listOf("Ale", "Pwochen", "Fini", "Anvan"),
        "eo" to listOf("Iru", "Sekva", "Farite", "Antaŭa"),
        "mt" to listOf("Mur", "Li jmiss", "Lest", "Lura"),
        "ja" to listOf("実行", "次へ", "完了", "前へ"),
        "zh" to listOf("前往", "下一步", "完成", "上一步"),
        "ko" to listOf("이동", "다음", "완료", "이전"),
        "th" to listOf("ไป", "ถัดไป", "เสร็จ", "ก่อนหน้า"),
        "hi" to listOf("जाएँ", "आगे", "हो गया", "पीछे"),
        "mr" to listOf("जा", "पुढे", "झाले", "मागे"),
        "bn" to listOf("যান", "পরবর্তী", "সম্পন্ন", "পূর্ববর্তী"),
        "gu" to listOf("જાઓ", "આગળ", "થઈ ગયું", "પાછળ"),
        "kn" to listOf("ಹೋಗಿ", "ಮುಂದೆ", "ಮುಗಿದಿದೆ", "ಹಿಂದೆ"),
        "ta" to listOf("செல்", "அடுத்து", "முடிந்தது", "முந்தைய"),
        "te" to listOf("వెళ్ళు", "తదుపరి", "పూర్తయింది", "మునుపటి"),
    )

    /** The label in [language], or English if we don't have that language. */
    fun label(action: Action, language: String): String =
        (LABELS[language] ?: LABELS.getValue("en"))[action.ordinal]
}
