import Foundation

/// The languages Apple's on-device translation covers, with names and hints.
public enum Languages {
    /// Codes usable with Apple's Translation framework (iOS 18+).
    public static let all: [String] = [
        "ar", "de", "en", "es", "fr", "hi", "id", "it", "ja", "ko", "nl", "pl", "pt", "ru", "th", "tr", "uk", "vi", "zh",
    ]

    /// The name of `code` written in `language` ("es" in "en" → "Spanish").
    public static func name(_ code: String, in language: String) -> String {
        let name = Locale(identifier: language).localizedString(forLanguageCode: code) ?? code
        return name.prefix(1).uppercased() + name.dropFirst()
    }

    /// Short label for the switch, e.g. "ES".
    public static func short(_ code: String) -> String { code.uppercased() }

    /// "Type in <language>…", written in that language.
    public static func typeInHint(_ code: String) -> String {
        [
            "en": "Type in English…", "es": "Escribe en español…", "fr": "Écris en français…",
            "de": "Schreib auf Deutsch…", "it": "Scrivi in italiano…", "pt": "Escreva em português…",
            "nl": "Typ in het Nederlands…", "pl": "Pisz po polsku…", "ru": "Пиши по-русски…",
            "uk": "Пиши українською…", "tr": "Türkçe yaz…", "ar": "اكتب بالعربية…", "ko": "한국어로 입력…",
            "ja": "日本語で入力…", "zh": "用中文输入…", "hi": "हिंदी में लिखें…", "th": "พิมพ์เป็นภาษาไทย…",
            "id": "Ketik dalam bahasa Indonesia…", "vi": "Nhập bằng tiếng Việt…",
        ][code] ?? "Type in \(name(code, in: "en"))…"
    }

    public enum Action: Int { case go = 0, next, done, previous }

    /// Return-key words in `language` (Send and Search are icons).
    public static func actionLabel(_ action: Action, in language: String) -> String {
        let labels: [String: [String]] = [
            "en": ["Go", "Next", "Done", "Prev"], "es": ["Ir", "Siguiente", "Listo", "Anterior"],
            "fr": ["Aller", "Suivant", "Terminé", "Précédent"], "de": ["Los", "Weiter", "Fertig", "Zurück"],
            "it": ["Vai", "Avanti", "Fine", "Indietro"], "pt": ["Ir", "Avançar", "Concluído", "Voltar"],
            "nl": ["Ga", "Volgende", "Klaar", "Vorige"], "pl": ["Idź", "Dalej", "Gotowe", "Wstecz"],
            "ru": ["Перейти", "Далее", "Готово", "Назад"], "uk": ["Перейти", "Далі", "Готово", "Назад"],
            "tr": ["Git", "İleri", "Bitti", "Geri"], "ar": ["انتقال", "التالي", "تم", "السابق"],
            "ko": ["이동", "다음", "완료", "이전"], "ja": ["実行", "次へ", "完了", "前へ"],
            "zh": ["前往", "下一步", "完成", "上一步"], "hi": ["जाएँ", "आगे", "हो गया", "पीछे"],
            "th": ["ไป", "ถัดไป", "เสร็จ", "ก่อนหน้า"], "id": ["Buka", "Berikutnya", "Selesai", "Sebelumnya"],
            "vi": ["Đi", "Tiếp", "Xong", "Trước"],
        ]
        return (labels[language] ?? labels["en"]!)[action.rawValue]
    }
}
