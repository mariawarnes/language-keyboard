import Foundation
import Translation

/// Apple's on-device translation for one language pair. Language packs are downloaded in the
/// Language Keyboard app (iOS asks for consent there); the keyboard only uses installed ones.
@MainActor
final class Translator {
    private var session: TranslationSession?
    private(set) var source = ""
    private(set) var target = ""

    func configure(source: String, target: String) {
        guard source != self.source || target != self.target else { return }
        self.source = source
        self.target = target
        session = source == target ? nil : TranslationSession(
            installedSource: Locale.Language(identifier: source),
            target: Locale.Language(identifier: target)
        )
    }

    /// Whether the language packs for this pair are on the phone.
    func isInstalled() async -> Bool {
        guard source != target else { return false }
        let status = await LanguageAvailability().status(
            from: Locale.Language(identifier: source), to: Locale.Language(identifier: target)
        )
        return status == .installed
    }

    func translate(_ text: String) async -> String? {
        guard let session, !text.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        do {
            return try await session.translate(text).targetText
        } catch {
            return nil
        }
    }
}
