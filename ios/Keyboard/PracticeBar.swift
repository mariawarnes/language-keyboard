import KeyboardCore
import UIKit

/// The bar above the keys: the ES | EN switch, the practice text (or direct-mode preview), ✕ and ⚙.
final class PracticeBar: UIView {
    var onSwitch: ((Bool) -> Void)?   // true = practice (learning language)
    var onClear: (() -> Void)?
    var onSettings: (() -> Void)?

    private let learningLabel = UILabel()
    private let mineLabel = UILabel()
    private let toggle = UIView()
    let textLabel = UILabel()
    private let clearButton = UIButton(type: .system)
    private let settingsButton = UIButton(type: .system)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .secondarySystemBackground

        toggle.backgroundColor = .systemGray4
        toggle.layer.cornerRadius = 15
        for (label, practice) in [(learningLabel, true), (mineLabel, false)] {
            label.font = .boldSystemFont(ofSize: 13)
            label.textAlignment = .center
            label.layer.cornerRadius = 13
            label.layer.masksToBounds = true
            label.isUserInteractionEnabled = true
            label.addGestureRecognizer(UITapGestureRecognizer(target: self, action: practice ? #selector(tapLearning) : #selector(tapMine)))
        }
        let segments = UIStackView(arrangedSubviews: [learningLabel, mineLabel])
        segments.axis = .horizontal
        segments.spacing = 0
        segments.translatesAutoresizingMaskIntoConstraints = false
        toggle.addSubview(segments)

        textLabel.font = .systemFont(ofSize: 17)
        textLabel.lineBreakMode = .byTruncatingHead // keep the end (where you're typing) in view
        textLabel.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)

        clearButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        clearButton.tintColor = .secondaryLabel
        clearButton.addTarget(self, action: #selector(tapClear), for: .touchUpInside)
        settingsButton.setImage(UIImage(systemName: "gearshape"), for: .normal)
        settingsButton.tintColor = .secondaryLabel
        settingsButton.addTarget(self, action: #selector(tapSettings), for: .touchUpInside)

        let row = UIStackView(arrangedSubviews: [toggle, textLabel, clearButton, settingsButton])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 10
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            row.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -6),
            row.topAnchor.constraint(equalTo: topAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor),
            toggle.heightAnchor.constraint(equalToConstant: 30),
            segments.leadingAnchor.constraint(equalTo: toggle.leadingAnchor, constant: 2),
            segments.trailingAnchor.constraint(equalTo: toggle.trailingAnchor, constant: -2),
            segments.topAnchor.constraint(equalTo: toggle.topAnchor, constant: 2),
            segments.bottomAnchor.constraint(equalTo: toggle.bottomAnchor, constant: -2),
            learningLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 36),
            mineLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 36),
            clearButton.widthAnchor.constraint(equalToConstant: 36),
            settingsButton.widthAnchor.constraint(equalToConstant: 36),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func setLanguages(learning: String, mine: String, practice: Bool) {
        learningLabel.text = Languages.short(learning)
        mineLabel.text = Languages.short(mine)
        for (label, selected) in [(learningLabel, practice), (mineLabel, !practice)] {
            label.backgroundColor = selected ? .systemBlue : .clear
            label.textColor = selected ? .white : .secondaryLabel
        }
    }

    func setClearVisible(_ visible: Bool) { clearButton.isHidden = !visible }

    /// Practice text with a thin cursor at `cursor` (in characters), or a grey hint.
    func show(text: String, cursor: Int?, hint: Bool) {
        let attributed = NSMutableAttributedString(string: text, attributes: [
            .foregroundColor: hint ? UIColor.secondaryLabel : UIColor.label,
            .font: UIFont.systemFont(ofSize: 17),
        ])
        if let cursor {
            let caret = NSTextAttachment(image: Self.caret)
            caret.bounds = CGRect(x: 0, y: -3, width: 2, height: 20)
            let offset = text.index(text.startIndex, offsetBy: min(cursor, text.count)).utf16Offset(in: text)
            attributed.insert(NSAttributedString(attachment: caret), at: offset)
        }
        textLabel.attributedText = attributed
    }

    private static let caret: UIImage = UIGraphicsImageRenderer(size: CGSize(width: 2, height: 20)).image { _ in
        UIColor.systemBlue.setFill()
        UIRectFill(CGRect(x: 0, y: 0, width: 2, height: 20))
    }

    @objc private func tapLearning() { onSwitch?(true) }
    @objc private func tapMine() { onSwitch?(false) }
    @objc private func tapClear() { onClear?() }
    @objc private func tapSettings() { onSettings?() }
}
