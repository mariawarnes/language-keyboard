/// One key on the keyboard.
public struct Key: Equatable {
    public enum Kind: Equatable { case text, shift, delete, space, enter, symbols, letters, symbols2, globe }

    public var label: String
    public var output: String?
    public var kind: Kind
    public var width: Double
    public var alternates: [String]
    public var hint: String?

    public init(_ label: String, output: String? = nil, kind: Kind = .text, width: Double = 1,
                alternates: [String] = [], hint: String? = nil) {
        self.label = label
        self.output = kind == .text ? (output ?? label) : output
        self.kind = kind
        self.width = width
        self.alternates = alternates
        self.hint = hint
    }

    /// Letters change case with Shift; everything else stays as it is.
    public var isLetter: Bool {
        guard kind == .text, let o = output, o.count == 1 else { return false }
        return o.first!.isLetter
    }
}

/// How one language's letter keys are arranged (same layouts as the Android keyboard).
public struct LayoutSpec {
    public var name: String
    public var rows: [String]
    public var shiftRows: [String]? = nil
    public var alternates: [String: String] = [:]
    public var latin = true
    public var cased = true
    public var hangul = false
    public var lettersLabel = "ABC"
    public var comma = ","
    public var periodAlternates = "? ! … ¿ ¡ - :"
    public var digits = "1234567890"
    public var dedicated = true
}

public enum KeyboardMode { case letters, symbols, symbols2 }

public enum Layouts {
    static let qwerty1 = "q w e r t y u i o p", qwerty2 = "a s d f g h j k l", qwerty3 = "z x c v b n m"
    static let qwertz1 = "q w e r t z u i o p", qwertz3 = "y x c v b n m"

    public static let latinAlternates: [String: String] = [
        "a": "á à â ä ã å ā æ ą", "c": "ç ć č", "d": "ð ď đ", "e": "é è ê ë ē ę ě ė", "g": "ğ ġ ģ",
        "i": "í ì î ï ī į ı", "l": "ł ľ ļ ĺ", "n": "ñ ń ň ņ", "o": "ó ò ô ö õ ø ō œ ő", "r": "ř ŕ",
        "s": "ß ś š ş ș", "t": "ť ț þ", "u": "ú ù û ü ū ů ű ų", "y": "ý ÿ", "z": "ž ź ż",
    ]

    static let specs: [String: LayoutSpec] = [
        "en": LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3]),
        "id": LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3]),
        "es": LayoutSpec(name: "QWERTY (Español)", rows: [qwerty1, qwerty2 + " ñ", qwerty3],
                         alternates: ["a": "á", "e": "é", "i": "í", "o": "ó", "u": "ú ü", "n": "ñ"]),
        "pt": LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3],
                         alternates: ["a": "á à â ã", "e": "é ê", "i": "í", "o": "ó ô õ", "u": "ú ü", "c": "ç"]),
        "it": LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3],
                         alternates: ["a": "à á", "e": "è é", "i": "ì í", "o": "ò ó", "u": "ù ú"]),
        "nl": LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3],
                         alternates: ["e": "é ë è ê", "i": "ï í", "o": "ó ö ô", "u": "ü ú", "a": "á ä"]),
        "pl": LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3],
                         alternates: ["a": "ą", "c": "ć", "e": "ę", "l": "ł", "n": "ń", "o": "ó", "s": "ś", "z": "ż ź"]),
        "vi": LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3], alternates: [
            "a": "à á ả ã ạ ă ằ ắ ẳ ẵ ặ â ầ ấ ẩ ẫ ậ", "e": "è é ẻ ẽ ẹ ê ề ế ể ễ ệ", "i": "ì í ỉ ĩ ị",
            "o": "ò ó ỏ õ ọ ô ồ ố ổ ỗ ộ ơ ờ ớ ở ỡ ợ", "u": "ù ú ủ ũ ụ ư ừ ứ ử ữ ự", "y": "ỳ ý ỷ ỹ ỵ", "d": "đ",
        ]),
        "de": LayoutSpec(name: "QWERTZ", rows: [qwertz1 + " ü", qwerty2 + " ö ä", qwertz3],
                         alternates: ["a": "ä", "o": "ö", "u": "ü", "s": "ß"]),
        "fr": LayoutSpec(name: "AZERTY", rows: ["a z e r t y u i o p", "q s d f g h j k l m", "w x c v b n '"],
                         alternates: ["a": "à â æ", "e": "é è ê ë", "i": "î ï", "o": "ô œ", "u": "ù û ü", "c": "ç", "y": "ÿ"]),
        "tr": LayoutSpec(name: "Türkçe Q", rows: ["q w e r t y u ı o p ğ ü", "a s d f g h j k l ş i", "z x c v b n m ö ç"],
                         alternates: ["a": "â", "u": "û", "i": "î"]),
        "ru": LayoutSpec(name: "ЙЦУКЕН", rows: ["й ц у к е н г ш щ з х", "ф ы в а п р о л д ж э", "я ч с м и т ь б ю"],
                         alternates: ["е": "ё", "ь": "ъ"], latin: false, lettersLabel: "АБВ", periodAlternates: "? ! … - :"),
        "uk": LayoutSpec(name: "ЙЦУКЕН", rows: ["й ц у к е н г ш щ з х ї", "ф і в а п р о л д ж є", "я ч с м и т ь б ю"],
                         alternates: ["г": "ґ", "ь": "ʼ"], latin: false, lettersLabel: "АБВ", periodAlternates: "? ! … - :"),
        "ar": LayoutSpec(name: "العربية", rows: ["ض ص ث ق ف غ ع ه خ ح ج", "ش س ي ب ل ا ت ن م ك ط", "ذ ء ؤ ر ى ة و ز ظ د"],
                         alternates: ["ا": "أ إ آ", "ي": "ئ ى", "و": "ؤ", "ه": "ة"], latin: false, cased: false,
                         lettersLabel: "أبت", comma: "،", periodAlternates: "؟ ! … - :", digits: "١٢٣٤٥٦٧٨٩٠"),
        "ko": LayoutSpec(name: "두벌식", rows: ["ㅂ ㅈ ㄷ ㄱ ㅅ ㅛ ㅕ ㅑ ㅐ ㅔ", "ㅁ ㄴ ㅇ ㄹ ㅎ ㅗ ㅓ ㅏ ㅣ", "ㅋ ㅌ ㅊ ㅍ ㅠ ㅜ ㅡ"],
                         shiftRows: ["ㅃ ㅉ ㄸ ㄲ ㅆ ㅛ ㅕ ㅑ ㅒ ㅖ", "ㅁ ㄴ ㅇ ㄹ ㅎ ㅗ ㅓ ㅏ ㅣ", "ㅋ ㅌ ㅊ ㅍ ㅠ ㅜ ㅡ"],
                         latin: false, cased: false, hangul: true, lettersLabel: "가나다", periodAlternates: "? ! … - :"),
    ]

    /// The layout for `code`, or QWERTY marked as not dedicated (Japanese, Chinese, Hindi, Thai: coming later).
    public static func spec(for code: String) -> LayoutSpec {
        specs[code] ?? LayoutSpec(name: "QWERTY", rows: [qwerty1, qwerty2, qwerty3], dedicated: false)
    }

    private static func split(_ s: String) -> [String] { s.split(separator: " ").map(String.init) }

    private static func bottomRow(total: Double, mode: Key, spec: LayoutSpec, globe: Bool) -> [Key] {
        var row = [mode]
        if globe { row.append(Key("🌐", kind: .globe, width: 1)) } // switches keyboards (iOS asks for it)
        row.append(Key(spec.comma, alternates: split("' \" : ; - _")))
        row.append(Key("", kind: .space, width: total - 5 - (globe ? 1 : 0)))
        row.append(Key(".", alternates: split(spec.periodAlternates)))
        row.append(Key("⏎", kind: .enter, width: 1.5))
        return row
    }

    /// The rows for a mode. `shifted` picks the Shift page for scripts that have one (Korean).
    /// `globe` adds the key iOS asks keyboards to show for switching keyboards.
    public static func rows(_ mode: KeyboardMode, language: String, shifted: Bool = false, globe: Bool = false) -> [[Key]] {
        let spec = spec(for: language)
        switch mode {
        case .letters: return letters(spec, shifted: shifted, globe: globe)
        case .symbols: return symbols(spec, globe: globe)
        case .symbols2: return symbols2(spec, globe: globe)
        }
    }

    static func letters(_ spec: LayoutSpec, shifted: Bool, globe: Bool) -> [[Key]] {
        let rows = ((shifted ? spec.shiftRows : nil) ?? spec.rows).map(split)
        let hasShift = spec.cased || spec.shiftRows != nil
        let fnCount = hasShift ? 2.0 : 1.0
        let last = rows.last!
        let total = max(Double(rows.dropLast().map(\.count).max() ?? 0), Double(last.count) + fnCount, 10)
        let fnWidth = min(max((total - Double(last.count)) / fnCount, 1), 1.5)
        let digits = Array(spec.digits)
        let western = Array("1234567890")

        func letter(_ c: String, digit index: Int?) -> Key {
            let own = spec.alternates[c] ?? (spec.latin ? Layouts.latinAlternates[c] : nil)
            var alternates = split(own ?? "")
            var hint: String?
            if let index, index < digits.count {
                let d = String(digits[index]), w = String(western[index])
                alternates = (d == w ? [d] : [d, w]) + alternates
                hint = d
            }
            return Key(c, alternates: alternates, hint: hint)
        }

        var out: [[Key]] = []
        for (r, row) in rows.dropLast().enumerated() {
            out.append(row.enumerated().map { i, c in letter(c, digit: r == 0 ? i : nil) })
        }
        var lastRow: [Key] = []
        if hasShift { lastRow.append(Key("⇧", kind: .shift, width: fnWidth)) }
        lastRow += last.map { letter($0, digit: nil) }
        lastRow.append(Key("⌫", kind: .delete, width: fnWidth))
        out.append(lastRow)
        out.append(bottomRow(total: total, mode: Key("123", kind: .symbols, width: 1.5), spec: spec, globe: globe))
        return out
    }

    static func symbols(_ spec: LayoutSpec, globe: Bool) -> [[Key]] {
        func s(_ c: String, _ alt: String = "") -> Key { Key(c, alternates: split(alt)) }
        return [
            [s("1", "¹ ½ ⅓ ¼"), s("2", "² ⅔"), s("3", "³ ¾"), s("4"), s("5"), s("6"), s("7"), s("8"), s("9"), s("0", "°")],
            [s("@"), s("#"), s("$", "€ £ ¥ ¢ ₹"), s("_"), s("&"), s("-", "– — ·"), s("+", "±"), s("("), s(")"), s("/")],
            [Key("=\\<", kind: .symbols2, width: 1.5), s("*", "★ †"), s("\"", "“ ” „ « »"), s("'", "‘ ’ ‚ ‹ ›"),
             s(":"), s(";"), s("!", "¡"), s("?", "¿"), Key("⌫", kind: .delete, width: 1.5)],
            bottomRow(total: 10, mode: Key(spec.lettersLabel, kind: .letters, width: 1.5), spec: spec, globe: globe),
        ]
    }

    static func symbols2(_ spec: LayoutSpec, globe: Bool) -> [[Key]] {
        func s(_ c: String) -> Key { Key(c) }
        return [
            "~ ` | • √ π ÷ × ¶ ∆".split(separator: " ").map { s(String($0)) },
            "£ € ¥ ¢ ^ ° = { } \\".split(separator: " ").map { s(String($0)) },
            [Key("123", kind: .symbols, width: 1.5)] + "% © ® ™ ✓ [ ]".split(separator: " ").map { s(String($0)) }
                + [Key("⌫", kind: .delete, width: 1.5)],
            bottomRow(total: 10, mode: Key(spec.lettersLabel, kind: .letters, width: 1.5), spec: spec, globe: globe),
        ]
    }
}
