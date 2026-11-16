import KeyboardCore
import UIKit

enum ShiftState { case off, once, locked }

protocol KeysViewDelegate: AnyObject {
    func keysView(_ view: KeysView, typed text: String)
    func keysView(_ view: KeysView, pressed kind: Key.Kind)
    /// Space-bar slide: move the cursor this many characters. Returns true if it moved.
    func keysView(_ view: KeysView, moveCursor steps: Int) -> Bool
    /// The 🌐 key: iOS's keyboard switcher (tap = next keyboard, hold = list).
    func keysView(_ view: KeysView, globeTouch event: UIEvent?)
}

/// The keys, drawn like the Android keyboard: tap, long-press for accents (slide to pick, lift to
/// type), delete repeats, slide along the space bar to move the cursor.
final class KeysView: UIView {
    weak var delegate: KeysViewDelegate?

    var rows: [[Key]] = [] { didSet { placeKeys(); setNeedsDisplay() } }
    var shift: ShiftState = .off { didSet { setNeedsDisplay() } }
    /// Return key: an SF Symbol name, or a word ("Ir", "Siguiente"…).
    var returnSymbol: String? = "arrow.turn.down.left" { didSet { setNeedsDisplay() } }
    var returnWord: String? { didSet { setNeedsDisplay() } }
    var spaceLabel = "" { didSet { setNeedsDisplay() } }

    static let rowHeight: CGFloat = 52
    static let padTop: CGFloat = 4
    static let padBottom: CGFloat = 6
    static func height(rows: Int) -> CGFloat { padTop + CGFloat(rows) * rowHeight + padBottom }

    private struct Placed { let key: Key; let cell: CGRect; let face: CGRect; let row: Int }
    private var placed: [Placed] = []
    private var unit: CGFloat = 30

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = false
        backgroundColor = .clear
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        placeKeys()
        setNeedsDisplay()
    }

    private func placeKeys() {
        placed.removeAll()
        guard bounds.width > 0, !rows.isEmpty else { return }
        let side: CGFloat = 3, gapX: CGFloat = 5, gapY: CGFloat = 9
        let available = bounds.width - 2 * side
        let widest = rows.map { $0.reduce(0) { $0 + $1.width } }.max() ?? 10
        unit = available / CGFloat(widest)
        for (r, row) in rows.enumerated() {
            let rowWidth = CGFloat(row.reduce(0) { $0 + $1.width }) * unit
            var x = side + (available - rowWidth) / 2
            let top = Self.padTop + CGFloat(r) * Self.rowHeight
            for key in row {
                let w = CGFloat(key.width) * unit
                let cell = CGRect(x: x, y: top, width: w, height: Self.rowHeight)
                placed.append(Placed(key: key, cell: cell, face: cell.insetBy(dx: gapX / 2, dy: gapY / 2), row: r))
                x += w
            }
        }
    }

    private func placedKey(at p: CGPoint) -> Placed? {
        guard !placed.isEmpty else { return nil }
        let row = min(max(Int((p.y - Self.padTop) / Self.rowHeight), 0), rows.count - 1)
        let inRow = placed.filter { $0.row == row }
        return inRow.first { $0.cell.minX <= p.x && p.x < $0.cell.maxX }
            ?? inRow.min { abs($0.cell.midX - p.x) < abs($1.cell.midX - p.x) }
    }

    // MARK: - Drawing

    private static func dynamic(_ light: UIColor, _ dark: UIColor) -> UIColor {
        UIColor { $0.userInterfaceStyle == .dark ? dark : light }
    }
    private let keyColor = dynamic(.white, UIColor(white: 0.42, alpha: 1))
    private let functionColor = dynamic(UIColor(red: 0.67, green: 0.69, blue: 0.73, alpha: 1), UIColor(white: 0.27, alpha: 1))
    private let pressedColor = dynamic(UIColor(white: 0.82, alpha: 1), UIColor(white: 0.55, alpha: 1))
    private let accent = UIColor.systemBlue
    private let textColor = UIColor.label
    private let hintColor = UIColor.secondaryLabel

    private func displayLabel(_ key: Key) -> String {
        switch key.kind {
        case .space: return spaceLabel
        case .enter: return returnWord ?? ""
        default: return key.isLetter && shift != .off ? key.label.uppercased() : key.label
        }
    }

    private func symbol(for key: Key) -> String? {
        switch key.kind {
        case .shift: return shift == .locked ? "capslock.fill" : (shift == .once ? "shift.fill" : "shift")
        case .delete: return "delete.left"
        case .enter: return returnWord == nil ? returnSymbol : nil
        case .globe: return "globe"
        default: return nil
        }
    }

    override func draw(_ rect: CGRect) {
        for p in placed { drawKey(p) }
        if let popup { drawPopup(popup) }
    }

    private func drawKey(_ p: Placed) {
        let key = p.key
        let pressed = pressedIndex.map { placed[$0].face == p.face } ?? false && popup == nil
        let accentKey = key.kind == .enter || (key.kind == .shift && shift != .off)
        let fill: UIColor
        if pressed {
            fill = pressedColor
        } else if accentKey {
            fill = accent
        } else if [.shift, .delete, .symbols, .letters, .symbols2, .globe].contains(key.kind) {
            fill = functionColor
        } else {
            fill = keyColor
        }
        let shape = UIBezierPath(roundedRect: p.face, cornerRadius: 6)
        UIColor.black.withAlphaComponent(0.25).setFill()
        UIBezierPath(roundedRect: p.face.offsetBy(dx: 0, dy: 1), cornerRadius: 6).fill()
        fill.setFill()
        shape.fill()

        let ink: UIColor = accentKey && !pressed ? .white : (key.kind == .space ? hintColor : textColor)
        if let name = symbol(for: key),
           let image = UIImage(systemName: name, withConfiguration: UIImage.SymbolConfiguration(pointSize: 19, weight: .regular))?
            .withTintColor(ink, renderingMode: .alwaysOriginal) {
            let size = image.size
            image.draw(in: CGRect(x: p.face.midX - size.width / 2, y: p.face.midY - size.height / 2, width: size.width, height: size.height))
        } else {
            let label = displayLabel(key)
            var fontSize: CGFloat = key.kind == .space ? 13 : (label.count == 1 && key.kind == .text ? 23 : 16)
            var attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: fontSize), .foregroundColor: ink]
            var size = (label as NSString).size(withAttributes: attributes)
            while size.width > p.face.width - 8, fontSize > 9 {
                fontSize -= 1
                attributes[.font] = UIFont.systemFont(ofSize: fontSize)
                size = (label as NSString).size(withAttributes: attributes)
            }
            (label as NSString).draw(at: CGPoint(x: p.face.midX - size.width / 2, y: p.face.midY - size.height / 2), withAttributes: attributes)
        }
        if let hint = key.hint {
            let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: hintColor]
            let size = (hint as NSString).size(withAttributes: attributes)
            (hint as NSString).draw(at: CGPoint(x: p.face.maxX - size.width - 4, y: p.face.minY + 2), withAttributes: attributes)
        }
    }

    // MARK: - Long-press popup

    private struct Popup { let options: [String]; let frame: CGRect; let cell: CGFloat; var selected: Int }
    private var popup: Popup?

    private func showPopup(for p: Placed) {
        let options = p.key.alternates.map { $0.count == 1 && $0.first!.isLetter && shift != .off ? $0.uppercased() : $0 }
        let cell = max(p.face.width, 34)
        let cols = min(options.count, max(1, Int((bounds.width - 6) / cell)))
        let width = cell * CGFloat(cols)
        let height = (Self.rowHeight - 8) * CGFloat((options.count + cols - 1) / cols)
        let x = min(max(p.face.midX - cell / 2, 3), bounds.width - 3 - width)
        let y = max(p.face.minY - height, 0)
        popup = Popup(options: options, frame: CGRect(x: x, y: y, width: width, height: height), cell: cell,
                      selected: min(max(Int((p.face.midX - x) / cell), 0), cols - 1))
        setNeedsDisplay()
    }

    private func updatePopup(_ point: CGPoint) {
        guard var p = popup else { return }
        let cols = max(1, Int((p.frame.width / p.cell).rounded()))
        let rowsCount = (p.options.count + cols - 1) / cols
        let col = min(max(Int((point.x - p.frame.minX) / p.cell), 0), cols - 1)
        let row = min(max(Int((p.frame.maxY - point.y) / (Self.rowHeight - 8)), 0), rowsCount - 1)
        p.selected = min(row * cols + col, p.options.count - 1)
        popup = p
        setNeedsDisplay()
    }

    private func drawPopup(_ p: Popup) {
        let cols = max(1, Int((p.frame.width / p.cell).rounded()))
        UIColor.black.withAlphaComponent(0.25).setFill()
        UIBezierPath(roundedRect: p.frame.offsetBy(dx: 0, dy: 2), cornerRadius: 8).fill()
        keyColor.setFill()
        UIBezierPath(roundedRect: p.frame, cornerRadius: 8).fill()
        let cellHeight = Self.rowHeight - 8
        for (i, option) in p.options.enumerated() {
            let cell = CGRect(x: p.frame.minX + CGFloat(i % cols) * p.cell,
                              y: p.frame.maxY - CGFloat(i / cols + 1) * cellHeight, width: p.cell, height: cellHeight)
            if i == p.selected {
                accent.setFill()
                UIBezierPath(roundedRect: cell.insetBy(dx: 2, dy: 2), cornerRadius: 6).fill()
            }
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 22), .foregroundColor: i == p.selected ? UIColor.white : textColor,
            ]
            let size = (option as NSString).size(withAttributes: attributes)
            (option as NSString).draw(at: CGPoint(x: cell.midX - size.width / 2, y: cell.midY - size.height / 2), withAttributes: attributes)
        }
    }

    // MARK: - Touches

    private var pressedIndex: Int?
    private var downPoint = CGPoint.zero
    private var longPressTimer: Timer?
    private var repeatTimer: Timer?
    private var longPressed = false
    private var repeating = false
    private var sliding = false
    private var slideAnchor: CGFloat = 0
    private static let slideStep: CGFloat = 10

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let point = touch.location(in: self)
        guard let p = placedKey(at: point), let index = placed.firstIndex(where: { $0.face == p.face }) else { return }
        pressedIndex = index
        downPoint = point
        longPressed = false
        repeating = false
        sliding = false
        if p.key.kind == .globe {
            delegate?.keysView(self, globeTouch: event)
        } else if p.key.kind == .delete {
            delegate?.keysView(self, pressed: .delete)
            repeating = true
            repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.4, repeats: false) { [weak self] _ in
                guard let self else { return }
                self.repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
                    guard let self else { return }
                    self.delegate?.keysView(self, pressed: .delete)
                }
            }
        } else if !p.key.alternates.isEmpty {
            longPressTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: false) { [weak self] _ in
                guard let self, let i = self.pressedIndex else { return }
                self.longPressed = true
                self.showPopup(for: self.placed[i])
            }
        }
        setNeedsDisplay()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first, let index = pressedIndex else { return }
        let point = touch.location(in: self)
        let key = placed[index].key
        if key.kind == .globe {
            delegate?.keysView(self, globeTouch: event)
            return
        }
        if popup != nil {
            updatePopup(point)
            return
        }
        if key.kind == .space {
            // Slide on the space bar: one character per ~10 points, relative to the thumb.
            if !sliding {
                guard abs(point.x - downPoint.x) >= Self.slideStep else { return }
                sliding = true
                slideAnchor = downPoint.x
            }
            let steps = Int((point.x - slideAnchor) / Self.slideStep)
            if steps != 0 {
                slideAnchor += CGFloat(steps) * Self.slideStep
                _ = delegate?.keysView(self, moveCursor: steps)
            }
            return
        }
        if repeating || longPressed { return }
        if let p = placedKey(at: point), let i = placed.firstIndex(where: { $0.face == p.face }), i != index {
            longPressTimer?.invalidate()
            pressedIndex = i
            setNeedsDisplay()
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        defer { reset() }
        guard let index = pressedIndex else { return }
        let key = placed[index].key
        if key.kind == .globe {
            delegate?.keysView(self, globeTouch: event)
            return
        }
        if let p = popup {
            delegate?.keysView(self, typed: p.options[p.selected])
            return
        }
        if repeating || longPressed || sliding { return }
        fire(key)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { reset() }

    private func reset() {
        longPressTimer?.invalidate()
        repeatTimer?.invalidate()
        pressedIndex = nil
        popup = nil
        longPressed = false
        repeating = false
        sliding = false
        setNeedsDisplay()
    }

    private func fire(_ key: Key) {
        switch key.kind {
        case .text:
            guard let out = key.output else { return }
            delegate?.keysView(self, typed: key.isLetter && shift != .off ? out.uppercased() : out)
        case .space:
            delegate?.keysView(self, typed: " ")
        default:
            delegate?.keysView(self, pressed: key.kind)
        }
    }
}
