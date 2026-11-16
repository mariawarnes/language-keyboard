import KeyboardCore
import UIKit

/// The keyboard.
///
/// Practice mode: you type in the language you're learning. That text stays in the bar above the
/// keys; the text field gets the translation as "marked text" (iOS's still-editable underlined
/// text), replaced live as you type. ✓ locks it in.
///
/// Direct mode: you type in your own language straight into the field, and the bar shows the line
/// you're typing in the language you're learning.
final class KeyboardViewController: UIInputViewController, KeysViewDelegate {
    private let store = Store()
    private let bar = PracticeBar()
    private let keys = KeysView()
    private let settings = SettingsPanel()
    private var heightConstraint: NSLayoutConstraint?

    private let forward = Translator()   // learning → mine (practice mode)
    private let backward = Translator()  // mine → learning (direct-mode preview)

    // Practice text
    private var buffer = ""
    private var cursor = 0          // in characters
    private var composed = ""       // marked text currently in the field
    private var phrase = 0          // bumped when a phrase is finished or abandoned
    private var generation = 0
    private var applied = 0
    private var leadingSpace: Bool?
    private var lockPending = false
    private var translating: Task<Void, Never>?

    // Direct-mode preview
    private var preview = ""
    private var previewSource = ""
    private var previewTask: Task<Void, Never>?

    private var mode: KeyboardMode = .letters
    private var shift: ShiftState = .off
    private var lastShiftTap = Date.distantPast
    private var packsMissing = false

    private var practice: Bool { store.practice }
    private var typingLanguage: String { practice ? store.learning : store.mine }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        keys.delegate = self
        settings.isHidden = true

        bar.onSwitch = { [weak self] practice in self?.setPractice(practice) }
        bar.onClear = { [weak self] in self?.clearPhrase() }
        bar.onSettings = { [weak self] in self?.toggleSettings() }
        settings.onChange = { [weak self] learning, mine in self?.setLanguages(learning: learning, mine: mine) }
        settings.onDone = { [weak self] in self?.toggleSettings() }

        for v in [bar, keys, settings] as [UIView] {
            v.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(v)
        }
        NSLayoutConstraint.activate([
            bar.topAnchor.constraint(equalTo: view.topAnchor),
            bar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bar.heightAnchor.constraint(equalToConstant: 46),
            keys.topAnchor.constraint(equalTo: bar.bottomAnchor),
            keys.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keys.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keys.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            settings.topAnchor.constraint(equalTo: bar.bottomAnchor),
            settings.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            settings.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            settings.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        let height = view.heightAnchor.constraint(equalToConstant: 46 + KeysView.height(rows: 4))
        height.priority = UILayoutPriority(999)
        height.isActive = true
        heightConstraint = height
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        configureTranslators()
        autoShift()
        refresh()
        schedulePreview()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        // The app cleared the field (message sent): the phrase is done.
        if !buffer.isEmpty && !textDocumentProxy.hasText { resetPhrase() }
        autoShift()
        schedulePreview()
        refresh()
    }

    private func configureTranslators() {
        forward.configure(source: store.learning, target: store.mine)
        backward.configure(source: store.mine, target: store.learning)
        Task { @MainActor in
            let installed = await (practice ? forward : backward).isInstalled()
            packsMissing = !installed
            refresh()
        }
    }

    // MARK: - Keys

    func keysView(_ view: KeysView, typed text: String) {
        if practice {
            if let h = hangul(text, after: cursor > 0 ? char(at: cursor - 1) : nil, language: store.learning) {
                replaceBefore(h.replace, with: h.insert)
            } else {
                replaceBefore(0, with: text)
            }
            bufferChanged()
        } else {
            let proxy = textDocumentProxy
            if let h = hangul(text, after: proxy.documentContextBeforeInput?.last, language: store.mine) {
                for _ in 0..<h.replace { proxy.deleteBackward() }
                proxy.insertText(h.insert)
            } else {
                proxy.insertText(text)
            }
            schedulePreview()
        }
        if shift == .once { shift = .off }
        autoShift()
        refresh()
    }

    /// Korean jamo join the syllable before the cursor; nil for other keys.
    private func hangul(_ text: String, after prev: Character?, language: String) -> (replace: Int, insert: String)? {
        guard Layouts.spec(for: language).hangul, text.count == 1, let j = text.first, Hangul.isJamo(j) else { return nil }
        return Hangul.type(after: prev, j)
    }

    func keysView(_ view: KeysView, pressed kind: Key.Kind) {
        switch kind {
        case .delete: delete()
        case .enter: enter()
        case .shift: tapShift()
        case .symbols: mode = .symbols
        case .symbols2: mode = .symbols2
        case .letters: mode = .letters
        default: break
        }
        refresh()
    }

    func keysView(_ view: KeysView, moveCursor steps: Int) -> Bool {
        if practice && !buffer.isEmpty {
            let new = min(max(cursor + steps, 0), buffer.count)
            guard new != cursor else { return false }
            cursor = new
            autoShift()
            refresh()
            return true
        }
        let proxy = textDocumentProxy
        if steps < 0, (proxy.documentContextBeforeInput ?? "").isEmpty { return false }
        if steps > 0, (proxy.documentContextAfterInput ?? "").isEmpty { return false }
        proxy.adjustTextPosition(byCharacterOffset: steps)
        schedulePreview()
        return true
    }

    func keysView(_ view: KeysView, globeTouch event: UIEvent?) {
        if let event {
            handleInputModeList(from: view, with: event)
        } else {
            advanceToNextInputMode()
        }
    }

    private func delete() {
        if practice && !buffer.isEmpty {
            guard cursor > 0 else { return }
            if Layouts.spec(for: store.learning).hangul, let shorter = Hangul.removeLast(char(at: cursor - 1)) {
                replaceBefore(1, with: shorter)
            } else {
                replaceBefore(1, with: "")
            }
            bufferChanged()
        } else {
            let proxy = textDocumentProxy
            if Layouts.spec(for: store.mine).hangul, let prev = proxy.documentContextBeforeInput?.last,
               let shorter = Hangul.removeLast(prev) {
                proxy.deleteBackward()
                proxy.insertText(shorter)
            } else {
                proxy.deleteBackward()
            }
            schedulePreview()
        }
        autoShift()
    }

    private func enter() {
        if practice && !buffer.isEmpty {
            lockIn()
        } else {
            textDocumentProxy.insertText("\n")
        }
    }

    private func tapShift() {
        let now = Date()
        switch shift {
        case .off: shift = .once
        case .once: shift = now.timeIntervalSince(lastShiftTap) < 0.35 ? .locked : .off
        case .locked: shift = .off
        }
        lastShiftTap = now
    }

    /// Capitalise the start of a sentence (for languages with capitals).
    private func autoShift() {
        guard shift != .locked, Layouts.spec(for: typingLanguage).cased else {
            if shift != .locked && Layouts.spec(for: typingLanguage).shiftRows == nil { shift = .off }
            return
        }
        guard (textDocumentProxy.autocapitalizationType ?? .sentences) != .none else {
            shift = .off
            return
        }
        let before: String
        if practice && !buffer.isEmpty {
            before = String(buffer.prefix(cursor))
        } else {
            before = textDocumentProxy.documentContextBeforeInput ?? ""
        }
        let trimmed = before.trimmingCharacters(in: .whitespaces)
        let startOfSentence = trimmed.isEmpty || (before.last == " " && ".?!".contains(trimmed.last!)) || before.last == "\n"
        shift = startOfSentence ? .once : .off
    }

    // MARK: - Practice text

    private func char(at i: Int) -> Character { buffer[buffer.index(buffer.startIndex, offsetBy: i)] }

    private func replaceBefore(_ count: Int, with text: String) {
        let start = buffer.index(buffer.startIndex, offsetBy: max(0, cursor - count))
        let end = buffer.index(buffer.startIndex, offsetBy: cursor)
        buffer.replaceSubrange(start..<end, with: text)
        cursor = max(0, cursor - count) + text.count
    }

    private func bufferChanged() {
        generation += 1
        if buffer.isEmpty {
            translating?.cancel()
            applied = generation
            writeComposition("")
        } else {
            scheduleTranslation(immediate: false)
        }
    }

    private func scheduleTranslation(immediate: Bool) {
        translating?.cancel()
        let gen = generation, text = buffer, phraseAtStart = phrase
        translating = Task { @MainActor in
            if !immediate {
                try? await Task.sleep(nanoseconds: 120_000_000)
                if Task.isCancelled { return }
            }
            guard let result = await forward.translate(text) else {
                packsMissing = !(await forward.isInstalled())
                lockPending = false
                refresh()
                return
            }
            guard phraseAtStart == phrase, gen > applied else { return }
            applied = gen
            packsMissing = false
            writeComposition(result)
            if lockPending && gen == generation { finishLockIn() }
            refresh()
        }
    }

    /// Replace the marked text in the field with `translation`.
    private func writeComposition(_ translation: String) {
        let proxy = textDocumentProxy
        var text = translation
        if !text.isEmpty {
            if leadingSpace == nil {
                let before = proxy.documentContextBeforeInput ?? ""
                leadingSpace = before.last.map { !$0.isWhitespace } ?? false
            }
            if leadingSpace == true { text = " " + text }
        }
        guard text != composed else { return }
        proxy.setMarkedText(text, selectedRange: NSRange(location: (text as NSString).length, length: 0))
        if text.isEmpty {
            proxy.unmarkText()
            leadingSpace = nil
        }
        composed = text
    }

    /// ✓: keep the translation once it's up to date, and start a new phrase.
    private func lockIn() {
        if applied == generation {
            finishLockIn()
        } else {
            lockPending = true
            scheduleTranslation(immediate: true)
        }
    }

    private func finishLockIn() {
        if !composed.isEmpty { textDocumentProxy.unmarkText() }
        resetPhrase()
        autoShift()
        refresh()
    }

    /// ✕: remove this phrase's translation from the field and forget what was typed.
    private func clearPhrase() {
        if !composed.isEmpty {
            textDocumentProxy.setMarkedText("", selectedRange: NSRange(location: 0, length: 0))
            textDocumentProxy.unmarkText()
        }
        resetPhrase()
        autoShift()
        refresh()
    }

    private func resetPhrase() {
        translating?.cancel()
        phrase += 1
        buffer = ""
        cursor = 0
        composed = ""
        generation = 0
        applied = 0
        leadingSpace = nil
        lockPending = false
    }

    // MARK: - Direct mode

    private func schedulePreview() {
        guard !practice else {
            preview = ""
            previewSource = ""
            return
        }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let line = String(before.split(separator: "\n", omittingEmptySubsequences: false).last ?? "")
            .trimmingCharacters(in: .whitespaces)
        guard line != previewSource else { return }
        previewSource = line
        previewTask?.cancel()
        if line.isEmpty {
            preview = ""
            refresh()
            return
        }
        previewTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 150_000_000)
            if Task.isCancelled { return }
            let result = await backward.translate(line)
            if Task.isCancelled { return }
            if let result {
                preview = result
                packsMissing = false
            } else {
                packsMissing = !(await backward.isInstalled())
            }
            refresh()
        }
    }

    // MARK: - Switch and settings

    private func setPractice(_ on: Bool) {
        guard on != practice else { return }
        if !buffer.isEmpty { finishLockIn() }
        store.practice = on
        mode = .letters
        previewSource = ""
        configureTranslators()
        autoShift()
        schedulePreview()
        refresh()
    }

    private func setLanguages(learning: String, mine: String) {
        if !buffer.isEmpty { finishLockIn() }
        store.learning = learning
        store.mine = mine
        previewSource = ""
        configureTranslators()
        schedulePreview()
        refresh()
    }

    private func toggleSettings() {
        settings.isHidden.toggle()
        keys.isHidden = !settings.isHidden
        if !settings.isHidden { settings.select(learning: store.learning, mine: store.mine) }
    }

    // MARK: - UI

    private func refresh() {
        let learning = store.learning, mine = store.mine
        bar.setLanguages(learning: learning, mine: mine, practice: practice)
        bar.setClearVisible(practice && !buffer.isEmpty)
        if packsMissing {
            let pair = "\(Languages.name(learning, in: mine)) ↔ \(Languages.name(mine, in: mine))"
            bar.show(text: "Download \(pair) in the Language Keyboard app", cursor: nil, hint: true)
        } else if practice {
            if buffer.isEmpty {
                bar.show(text: Languages.typeInHint(learning), cursor: nil, hint: true)
            } else {
                bar.show(text: buffer, cursor: cursor, hint: false)
            }
        } else {
            bar.show(text: preview.isEmpty ? Languages.typeInHint(mine) : preview, cursor: nil, hint: preview.isEmpty)
        }

        keys.rows = Layouts.rows(mode, language: typingLanguage, shifted: shift != .off, globe: needsInputModeSwitchKey)
        keys.shift = shift
        keys.spaceLabel = practice
            ? "\(Languages.name(learning, in: mine)) → \(Languages.name(mine, in: mine))"
            : Languages.name(mine, in: mine)
        setReturnKey()
        heightConstraint?.constant = 46 + KeysView.height(rows: keys.rows.count)
    }

    private func setReturnKey() {
        if practice && !buffer.isEmpty {
            keys.returnSymbol = "checkmark"
            keys.returnWord = nil
            return
        }
        let language = typingLanguage
        keys.returnWord = nil
        switch textDocumentProxy.returnKeyType ?? .default {
        case .send: keys.returnSymbol = "paperplane"
        case .search, .google, .yahoo: keys.returnSymbol = "magnifyingglass"
        case .go, .route, .join: keys.returnWord = Languages.actionLabel(.go, in: language)
        case .next, .continue: keys.returnWord = Languages.actionLabel(.next, in: language)
        case .done: keys.returnWord = Languages.actionLabel(.done, in: language)
        default: keys.returnSymbol = "arrow.turn.down.left"
        }
    }
}
