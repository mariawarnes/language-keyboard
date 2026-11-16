import XCTest
@testable import KeyboardCore

final class CoreTests: XCTestCase {

    private func typeKorean(_ jamo: String) -> String {
        var text = ""
        for j in jamo {
            let (replace, insert) = Hangul.type(after: text.last, j)
            text = String(text.dropLast(replace)) + insert
        }
        return text
    }

    func testHangulBuildsSyllables() {
        XCTAssertEqual(typeKorean("ㅎㅏㄴㄱㅡㄹ"), "한글")
        XCTAssertEqual(typeKorean("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"), "안녕하세요")
        XCTAssertEqual(typeKorean("ㅎㅏㄴㅏ"), "하나")
        XCTAssertEqual(typeKorean("ㄷㅏㄹㄱㅏ"), "달가")
        XCTAssertEqual(typeKorean("ㅇㅗㅏ"), "와")
        XCTAssertEqual(typeKorean("ㅅㅏㄹㅁ"), "삶")
    }

    func testHangulBackspace() {
        XCTAssertEqual(Hangul.removeLast("한"), "하")
        XCTAssertEqual(Hangul.removeLast("하"), "ㅎ")
        XCTAssertEqual(Hangul.removeLast("와"), "오")
        XCTAssertNil(Hangul.removeLast("a"))
    }

    func testEveryLanguageBuildsAllModes() {
        for language in Languages.all {
            for mode in [KeyboardMode.letters, .symbols, .symbols2] {
                for shifted in [false, true] {
                    for globe in [false, true] {
                        let rows = Layouts.rows(mode, language: language, shifted: shifted, globe: globe)
                        XCTAssertGreaterThanOrEqual(rows.count, 4, "\(language) \(mode)")
                        XCTAssertTrue(rows.last!.contains { $0.kind == .space })
                    }
                }
            }
        }
    }

    func testLanguagesHaveTheirOwnKeys() {
        let letters = { (l: String, s: Bool) in Set(Layouts.rows(.letters, language: l, shifted: s).joined().compactMap(\.output)) }
        XCTAssertTrue(letters("es", false).contains("ñ"))
        XCTAssertTrue(letters("ru", false).contains("ж"))
        XCTAssertTrue(letters("ko", false).contains("ㅎ"))
        XCTAssertTrue(letters("ko", true).contains("ㅃ"))
        XCTAssertTrue(letters("de", false).contains("ü"))
    }

    func testHintsAndLabels() {
        XCTAssertEqual(Languages.typeInHint("es"), "Escribe en español…")
        XCTAssertEqual(Languages.actionLabel(.go, in: "es"), "Ir")
        XCTAssertEqual(Languages.name("es", in: "en"), "Spanish")
    }
}
