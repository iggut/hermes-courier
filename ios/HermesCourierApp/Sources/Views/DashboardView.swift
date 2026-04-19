import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    section(title: "Mission control", subtitle: "Monitor your Hermes agent and intervene safely.") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Bootstrap: \(viewModel.bootstrapState)")
                            Text("Auth: \(viewModel.authStatus)")
                            Text("Gateway: \(viewModel.gatewaySettings.baseURL)")
                            Text("Stream: \(viewModel.streamStatus)")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    section(title: "Snapshot", subtitle: "Live counts from the secure gateway.") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Active sessions: \(viewModel.dashboard.activeSessionCount)")
                            Text("Pending approvals: \(viewModel.dashboard.pendingApprovalCount)")
                            Text("Last sync: \(viewModel.dashboard.lastSyncLabel)")
                            Text("Connection: \(viewModel.dashboard.connectionState)")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    section(title: "Realtime conversation", subtitle: "Messages arrive from the websocket stream.") {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(viewModel.messages.suffix(5)) { message in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(message.author).font(.headline)
                                    Text(message.body)
                                    Text(message.timestamp).font(.caption).foregroundStyle(.secondary)
                                }
                                if message.id != viewModel.messages.suffix(5).last?.id {
                                    Divider()
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    section(title: "Device enrollment", subtitle: "Use the fingerprint to complete gateway enrollment.") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Fingerprint: \(viewModel.deviceFingerprint)")
                            Text(viewModel.enrollmentStatus)
                            Button("Refresh") {
                                Task { await viewModel.refresh() }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding()
            }
            .navigationTitle("Dashboard")
        }
    }

    private func section<Content: View>(title: String, subtitle: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
            }
            content()
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}
