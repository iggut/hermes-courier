import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var showingCertificateImporter = false

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

                    Text("Certificate path: \(viewModel.gatewaySettings.certificatePath.isEmpty ? "None" : viewModel.gatewaySettings.certificatePath)")
                    Text(viewModel.enrollmentStatus)
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Button("Import certificate bundle") {
                        showingCertificateImporter = true
                    }

                    Button("Save secure settings") {
                        viewModel.saveSettings()
                    }
                    .buttonStyle(.borderedProminent)

                    Button("Reconnect gateway") {
                        Task { await viewModel.refresh() }
                    }
                }

                Section("Enrollment fingerprint") {
                    Text(viewModel.deviceFingerprint)
                    Text("Use this fingerprint to enroll the device with your Hermes gateway before importing the issued certificate.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
            .fileImporter(
                isPresented: $showingCertificateImporter,
                allowedContentTypes: [.data],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first {
                        let didStartAccessing = url.startAccessingSecurityScopedResource()
                        defer {
                            if didStartAccessing { url.stopAccessingSecurityScopedResource() }
                        }
                        viewModel.importCertificate(from: url)
                    }
                case .failure(let error):
                    viewModel.enrollmentStatus = "Certificate import failed: \(error.localizedDescription)"
                }
            }
        }
    }
}
