import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    private var connectionModeLine: String {
        let s = viewModel.bootstrapState
        if s.localizedCaseInsensitiveContains("demo") {
            return "Mode: sample data (not connected to a live gateway)."
        }
        if s.localizedCaseInsensitiveContains("unavailable") {
            return "Mode: gateway unavailable."
        }
        if s.localizedCaseInsensitiveContains("negotiating") || s.localizedCaseInsensitiveContains("bootstrapping") {
            return "Mode: connecting…"
        }
        if s.localizedCaseInsensitiveContains("ready") {
            return "Mode: live gateway."
        }
        return "Mode: \(viewModel.bootstrapState)"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    missionControlSection
                    snapshotSection
                    realtimeConversationSection
                    deviceEnrollmentSection
                }
                .padding()
            }
            .navigationTitle("Dashboard")
        }
    }

    private var missionControlSection: some View {
        section(title: "Mission control", subtitle: "Monitor your Hermes agent and intervene safely.") {
            VStack(alignment: .leading, spacing: 8) {
                Text(connectionModeLine)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text("Bootstrap: \(viewModel.bootstrapState)")
                Text("Auth: \(viewModel.authStatus)")
                Text("Gateway: \(viewModel.gatewaySettings.baseURL)")
                Text("Stream: \(viewModel.streamStatus)")
                Text("Stream reconnect: \(viewModel.realtimeReconnectCountdown)")
                ProgressView(value: viewModel.realtimeReconnectProgress) {
                    Text("Reconnect progress")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var snapshotSection: some View {
        section(title: "Snapshot", subtitle: "Live counts from the secure gateway.") {
            VStack(alignment: .leading, spacing: 8) {
                Text("Active sessions: \(viewModel.dashboard.activeSessionCount)")
                Text("Pending approvals: \(viewModel.dashboard.pendingApprovalCount)")
                Text("Last sync: \(viewModel.dashboard.lastSyncLabel)")
                Text("Connection: \(viewModel.dashboard.connectionState)")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var realtimeConversationSection: some View {
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
    }

    private var deviceEnrollmentSection: some View {
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
