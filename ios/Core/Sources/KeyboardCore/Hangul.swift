/// Builds Hangul syllables from 2-set (Dubeolsik) jamo as you type: ㅎ + ㅏ → 하, + ㄴ → 한;
/// a vowel after a final consonant moves it to a new syllable (한 + ㅏ → 하나); double finals split.
/// Same rules as the Android keyboard's Hangul.kt.
public enum Hangul {
    private static let cho = Array("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ")
    private static let jung = Array("ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ")
    private static let jong = Array(" ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ") // 0 = no final

    private static let vowelPairs: [String: Character] = [
        "ㅗㅏ": "ㅘ", "ㅗㅐ": "ㅙ", "ㅗㅣ": "ㅚ", "ㅜㅓ": "ㅝ", "ㅜㅔ": "ㅞ", "ㅜㅣ": "ㅟ", "ㅡㅣ": "ㅢ",
    ]
    private static let finalPairs: [String: Character] = [
        "ㄱㅅ": "ㄳ", "ㄴㅈ": "ㄵ", "ㄴㅎ": "ㄶ", "ㄹㄱ": "ㄺ", "ㄹㅁ": "ㄻ", "ㄹㅂ": "ㄼ",
        "ㄹㅅ": "ㄽ", "ㄹㅌ": "ㄾ", "ㄹㅍ": "ㄿ", "ㄹㅎ": "ㅀ", "ㅂㅅ": "ㅄ",
    ]

    public static func isJamo(_ c: Character) -> Bool {
        guard let v = c.unicodeScalars.first?.value, c.unicodeScalars.count == 1 else { return false }
        return (0x3131...0x318E).contains(v)
    }

    private static func scalar(_ c: Character) -> UInt32? {
        c.unicodeScalars.count == 1 ? c.unicodeScalars.first?.value : nil
    }

    private static func isSyllable(_ c: Character) -> Bool {
        guard let v = scalar(c) else { return false }
        return (0xAC00...0xD7A3).contains(v)
    }

    private static func syllable(_ c: Int, _ j: Int, _ f: Int) -> String {
        String(Character(Unicode.Scalar(UInt32(0xAC00 + (c * 21 + j) * 28 + f))!))
    }

    private static func parts(_ c: Character) -> (Int, Int, Int) {
        let i = Int(scalar(c)! - 0xAC00)
        return (i / (21 * 28), (i % (21 * 28)) / 28, i % 28)
    }

    private static func split(_ compound: Character, _ pairs: [String: Character]) -> String? {
        pairs.first { $0.value == compound }?.key
    }

    /// Typing jamo `j` right after `prev`: how many characters before the cursor to replace (0 or 1),
    /// and the text to put there.
    public static func type(after prev: Character?, _ j: Character) -> (replace: Int, insert: String) {
        let vowel = jung.contains(j)
        if let prev, isSyllable(prev) {
            let (c, v, f) = parts(prev)
            if !vowel {
                if f == 0 {
                    if let fi = jong.firstIndex(of: j), fi > 0 { return (1, syllable(c, v, fi)) }
                } else if let pair = finalPairs["\(jong[f])\(j)"], let fi = jong.firstIndex(of: pair) {
                    return (1, syllable(c, v, fi))
                }
                return (0, String(j))
            }
            if f == 0 {
                guard let pair = vowelPairs["\(jung[v])\(j)"], let vi = jung.firstIndex(of: pair) else { return (0, String(j)) }
                return (1, syllable(c, vi, 0))
            }
            // The final consonant becomes the start of a new syllable (a double final splits).
            let fin = jong[f]
            let pair = split(fin, finalPairs).map { Array($0) }
            let keep = pair.flatMap { jong.firstIndex(of: $0[0]) } ?? 0
            let move = pair?[1] ?? fin
            guard let ci = cho.firstIndex(of: move), let ji = jung.firstIndex(of: j) else { return (0, String(j)) }
            return (1, syllable(c, v, keep) + syllable(ci, ji, 0))
        }
        if let prev, vowel {
            if let ci = cho.firstIndex(of: prev), let ji = jung.firstIndex(of: j) { return (1, syllable(ci, ji, 0)) }
            if let pair = vowelPairs["\(prev)\(j)"] { return (1, String(pair)) }
        }
        return (0, String(j))
    }

    /// Backspace on syllable `c`: it loses its last jamo (한 → 하 → ㅎ). Nil if `c` isn't a syllable.
    public static func removeLast(_ c: Character) -> String? {
        guard isSyllable(c) else { return nil }
        let (ci, v, f) = parts(c)
        if f != 0 {
            let pair = split(jong[f], finalPairs).map { Array($0) }
            return syllable(ci, v, pair.flatMap { jong.firstIndex(of: $0[0]) } ?? 0)
        }
        if let pair = split(jung[v], vowelPairs).map { Array($0) }, let vi = jung.firstIndex(of: pair[0]) {
            return syllable(ci, vi, 0)
        }
        return String(cho[ci])
    }
}
