import SwiftUI
import UniformTypeIdentifiers
import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

struct SettingsView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var showingCertificateImporter = false
    @State private var showingEnrollmentScanner = false
    @State private var showingEnrollmentShareSheet = false
    @State private var shareItems: [Any] = []

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
                    Text("Realtime status: \(viewModel.streamStatus)")
                    Text("Reconnect backoff: \(viewModel.realtimeReconnectCountdown)")
                    Text("Connection state: \(viewModel.dashboard.connectionState)")
                }

                Section("Enrollment QR") {
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

                    Button("Share enrollment QR") {
                        if let qrImage = makeQRCodeImage(from: viewModel.enrollmentQrPayload) {
                            shareItems = [qrImage, viewModel.enrollmentQrPayload]
                        } else {
                            shareItems = [viewModel.enrollmentQrPayload]
                        }
                        showingEnrollmentShareSheet = true
                    }
                    Button("Copy enrollment QR payload") {
                        viewModel.copyEnrollmentQrPayload()
                    }
                    Button("Scan enrollment QR") {
                        showingEnrollmentScanner = true
                    }
                }

                Section("Queued approval actions") {
                    Text("Queued decisions are persisted locally until the live gateway is reachable again.")
                    Button("Flush queued actions now") {
                        viewModel.retryQueuedApprovalActionsNow()
                    }
                    Button("Reconnect realtime now") {
                        viewModel.reconnectRealtime()
                    }
                    if viewModel.queuedApprovalActionQueue.isEmpty {
                        Text("No queued approval actions.")
                    } else {
                        ForEach(viewModel.queuedApprovalActionQueue, id: \.self) { queued in
                            VStack(alignment: .leading, spacing: 6) {
                                Text("\(queued.action.uppercased()) • \(queued.approvalId)")
                                    .font(.headline)
                                if let note = queued.note, !note.isEmpty {
                                    Text(note)
                                }
                                Text("Queued at: \(queued.createdAt)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    Text("Retry status: \(viewModel.approvalActionStatus)")
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
            .sheet(isPresented: $showingEnrollmentShareSheet) {
                ShareSheetView(items: shareItems)
            }
        }
    }

    private func makeQRCodeImage(from payload: String) -> UIImage? {
        guard !payload.isEmpty else { return nil }
        let data = Data(payload.utf8)
        let filter = CIFilter.qrCodeGenerator()
        filter.setValue(data, forKey: "inputMessage")
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let transform = CGAffineTransform(scaleX: 10, y: 10)
        let scaledImage = outputImage.transformed(by: transform)
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
