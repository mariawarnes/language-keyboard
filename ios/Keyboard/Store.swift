import Foundation

/// The keyboard's settings. Kept inside the keyboard (no "Full Access" needed).
struct Store {
    private let defaults = UserDefaults.standard

    var learning: String {
        get { defaults.string(forKey: "learning") ?? "es" }
        nonmutating set { defaults.set(newValue, forKey: "learning") }
    }

    var mine: String {
        get { defaults.string(forKey: "mine") ?? "en" }
        nonmutating set { defaults.set(newValue, forKey: "mine") }
    }

    /// Practice mode (type in the language you're learning) vs direct mode (type in your own).
    var practice: Bool {
        get { defaults.object(forKey: "practice") as? Bool ?? true }
        nonmutating set { defaults.set(newValue, forKey: "practice") }
    }
}
