import KeyboardCore
import SwiftUI
import Translation

/// Setup steps and language downloads. The keyboard can only use language packs that are already
/// on the phone, and iOS only lets an app download them (with your OK), so that happens here.
struct ContentView: View {
    @AppStorage("learning") private var learning = "es"
    @AppStorage("mine") private var mine = "en"
    @State private var status: LanguageAvailability.Status?
    @State private var config: TranslationSession.Configuration?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Turn on the keyboard") {
                    Label("Open Settings → General → Keyboard → Keyboards", systemImage: "1.circle")
                    Label("Tap Add New Keyboard… and choose Language Keyboard", systemImage: "2.circle")
                    Label("In any app, hold 🌐 and pick Language Keyboard", systemImage: "3.circle")
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                }

                Section {
                    Picker("I'm learning", selection: $learning) {
                        ForEach(Languages.all, id: \.self) { Text(Languages.name($0, in: mine)).tag($0) }
                    }
                    Picker("My language", selection: $mine) {
                        ForEach(Languages.all, id: \.self) { Text(Languages.name($0, in: mine)).tag($0) }
                    }
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(statusText).foregroundStyle(.secondary)
                    }
                    if learning != mine && status != .installed && status != .unsupported {
                        Button("Download languages") { download() }
                    }
                    if let error { Text(error).foregroundStyle(.red).font(.footnote) }
                } header: {
                    Text("Languages")
                } footer: {
                    Text("Translation happens on your phone. Download each pair you want to use, then pick the same languages in the keyboard (⚙ above the keys).")
                }

                Section("How it works") {
                    Text("Practice: type in the language you're learning. It stays in the bar above the keys, and the translation into your language goes into the message. Tap ✓ to keep it.")
                    Text("Tap the other language on the switch to type normally in your own language; the bar then shows what you're writing in the language you're learning.")
                }
            }
            .navigationTitle("Language Keyboard")
            .task(id: "\(learning)-\(mine)") { await checkStatus() }
            .translationTask(config) { session in
                do {
                    try await session.prepareTranslation()
                    error = nil
                } catch {
                    self.error = error.localizedDescription
                }
                await checkStatus()
            }
        }
    }

    private var statusText: String {
        if learning == mine { return "Pick two different languages" }
        switch status {
        case .installed: return "Ready ✓"
        case .supported: return "Needs download"
        case .unsupported: return "Not available"
        default: return "Checking…"
        }
    }

    private func checkStatus() async {
        guard learning != mine else { status = nil; return }
        status = await LanguageAvailability().status(
            from: Locale.Language(identifier: learning), to: Locale.Language(identifier: mine)
        )
    }

    private func download() {
        let source = Locale.Language(identifier: learning), target = Locale.Language(identifier: mine)
        if config?.source == source && config?.target == target {
            config?.invalidate()
        } else {
            config = TranslationSession.Configuration(source: source, target: target)
        }
    }
}
