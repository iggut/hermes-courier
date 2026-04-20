import SwiftUI
import UniformTypeIdentifiers
import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

struct SettingsView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var showingCertificateImporter = false
    @State private var showingEnrollmentScanner = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Connection") {
                    TextField("Gateway URL", text: Binding(
                        get: { viewModel.gatewaySettings.baseURL },
                        set: { viewModel.updateGatewayBaseURL($0) }
                    ))
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)

                    SecureField("PKCS#12 password", text: Binding(
                        get: { viewModel.gatewaySettings.certificatePassword },
                        set: { viewModel.updateCertificatePassword($0) }
                    ))

                    Text("Certificate path: \(viewModel.gatewaySettings.certificatePath.isEmpty ? "Not imported" : viewModel.gatewaySettings.certificatePath)")
                    Text("Enrollment status: \(viewModel.enrollmentStatus)")
                    Text("Queued approvals: \(viewModel.queuedApprovalActions)")
                }

                Section("QR enrollment") {
                    if let qrImage = makeQRCodeImage(from: viewModel.enrollmentQrPayload) {
                        Image(uiImage: qrImage)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(maxWidth: 240, maxHeight: 240)
                            .frame(maxWidth: .infinity)
                    }
                    Text(viewModel.enrollmentQrPayload)
                        .font(.caption)
                        .textSelection(.enabled)
                    Button("Scan enrollment QR") {
                        showingEnrollmentScanner = true
                    }
                }

                Section("Bootstrap") {
                    Button("Import PKCS#12 certificate") {
                        showingCertificateImporter = true
                    }
                    Button("Save settings") {
                        viewModel.saveSettings()
                    }
                    Button("Refresh connection") {
                        Task { await viewModel.refresh() }
                    }
                }
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $showingCertificateImporter) {
                DocumentPickerView(allowedContentTypes: [UTType.data]) { url in
                    viewModel.importCertificate(from: url)
                }
            }
            .sheet(isPresented: $showingEnrollmentScanner, onDismiss: {
                showingEnrollmentScanner = false
            }) {
                EnrollmentScannerView { payload in
                    viewModel.importEnrollment(from: payload)
                    showingEnrollmentScanner = false
                } onDismiss: {
                    showingEnrollmentScanner = false
                }
            }
        }
    }

    private func makeQRCodeImage(from payload: String) -> UIImage? {
        guard !payload.isEmpty else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(payload.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
