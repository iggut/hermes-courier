import SwiftUI
#if canImport(VisionKit)
import VisionKit
#endif
import UIKit

struct EnrollmentScannerView: UIViewControllerRepresentable {
    let onScan: (String) -> Void
    let onDismiss: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onScan: onScan)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        #if canImport(VisionKit)
        guard DataScannerViewController.isSupported, DataScannerViewController.isAvailable else {
            return ScannerUnavailableView(onDismiss: onDismiss)
        }
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode()],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: true,
            isHighlightingEnabled: true
        )
        scanner.delegate = context.coordinator
        try? scanner.startScanning()
        return scanner
        #else
        return ScannerUnavailableView(onDismiss: onDismiss)
        #endif
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    final class Coordinator: NSObject {
        fileprivate let onScan: (String) -> Void
        fileprivate var hasDelivered = false

        init(onScan: @escaping (String) -> Void) {
            self.onScan = onScan
        }
    }
}

#if canImport(VisionKit)
extension EnrollmentScannerView.Coordinator: DataScannerViewControllerDelegate {
    func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
        guard !hasDelivered else { return }
        if let payload = extractFirstBarcodePayload(from: addedItems) {
            hasDelivered = true
            dataScanner.stopScanning()
            onScan(payload)
        }
    }

    private func extractFirstBarcodePayload(from items: [RecognizedItem]) -> String? {
        for item in items {
            if case let .barcode(barcode) = item, let payload = barcode.payloadStringValue {
                return payload
            }
        }
        return nil
    }
}
#endif

private final class ScannerUnavailableView: UIViewController {
    private let onDismiss: () -> Void

    init(onDismiss: @escaping () -> Void) {
        self.onDismiss = onDismiss
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let label = createMessageLabel()
        view.addSubview(label)

        let button = createDismissButton()
        view.addSubview(button)

        setupConstraints(label: label, button: button)
    }

    private func createMessageLabel() -> UILabel {
        let label = UILabel()
        label.text = "QR scanning is unavailable on this device."
        label.textAlignment = .center
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }

    private func createDismissButton() -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle("Dismiss", for: .normal)
        button.addTarget(self, action: #selector(dismissTapped), for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }

    private func setupConstraints(label: UILabel, button: UIButton) {
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -16),
            label.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),
            button.topAnchor.constraint(equalTo: label.bottomAnchor, constant: 16),
            button.centerXAnchor.constraint(equalTo: view.centerXAnchor),
        ])
    }

    @objc private func dismissTapped() {
        dismiss(animated: true) { [onDismiss] in
            onDismiss()
        }
    }
}
