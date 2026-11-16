import KeyboardCore
import UIKit

/// In-keyboard language picker (⚙): "I'm learning" and "My language".
final class SettingsPanel: UIView, UIPickerViewDataSource, UIPickerViewDelegate {
    var onChange: ((String, String) -> Void)?
    var onDone: (() -> Void)?

    private let learningPicker = UIPickerView()
    private let minePicker = UIPickerView()
    private let languages = Languages.all
    private var nameLanguage = "en"

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .systemBackground

        func column(_ title: String, _ picker: UIPickerView) -> UIStackView {
            let label = UILabel()
            label.text = title
            label.font = .preferredFont(forTextStyle: .footnote)
            label.textColor = .secondaryLabel
            label.textAlignment = .center
            picker.dataSource = self
            picker.delegate = self
            let stack = UIStackView(arrangedSubviews: [label, picker])
            stack.axis = .vertical
            return stack
        }
        let columns = UIStackView(arrangedSubviews: [column("I'm learning", learningPicker), column("My language", minePicker)])
        columns.axis = .horizontal
        columns.distribution = .fillEqually

        let note = UILabel()
        note.text = "Download languages in the Language Keyboard app first."
        note.font = .preferredFont(forTextStyle: .caption1)
        note.textColor = .secondaryLabel
        note.numberOfLines = 0

        let done = UIButton(type: .system)
        done.setTitle("Done", for: .normal)
        done.titleLabel?.font = .boldSystemFont(ofSize: 17)
        done.addTarget(self, action: #selector(tapDone), for: .touchUpInside)

        let footer = UIStackView(arrangedSubviews: [note, done])
        footer.axis = .horizontal
        footer.spacing = 8
        let stack = UIStackView(arrangedSubviews: [columns, footer])
        stack.axis = .vertical
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func select(learning: String, mine: String) {
        nameLanguage = mine
        learningPicker.reloadAllComponents()
        minePicker.reloadAllComponents()
        learningPicker.selectRow(languages.firstIndex(of: learning) ?? 0, inComponent: 0, animated: false)
        minePicker.selectRow(languages.firstIndex(of: mine) ?? 0, inComponent: 0, animated: false)
    }

    func numberOfComponents(in pickerView: UIPickerView) -> Int { 1 }

    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int { languages.count }

    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        Languages.name(languages[row], in: nameLanguage)
    }

    func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
        onChange?(languages[learningPicker.selectedRow(inComponent: 0)], languages[minePicker.selectedRow(inComponent: 0)])
    }

    @objc private func tapDone() { onDone?() }
}
