
import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    SectionCard(title: "Mission control", subtitle: "Monitor your Hermes agent and intervene safely.") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Bootstrap: \(viewModel.bootstrapState)")
                            Text("Auth: \(viewModel.authStatus)")
                            Text("Active session: \(viewModel.dashboard.activeSessionCount)")
                            Text("Pending approvals: \(viewModel.dashboard.pendingApprovalCount)")
                            Text("Last sync: \(viewModel.dashboard.lastSyncLabel)")
                        }
                    }

                    SectionCard(title: "Security model", subtitle: "Zero-trust mobile control") {
                        Text("Mutual TLS, hardware-backed secrets, biometric gating, and direct gateway connectivity.")
                    }

                    SectionCard(title: "Next actions") {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("• open live chat")
                            Text("• review approvals")
                            Text("• browse sessions and logs")
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Hermes Courier")
        }
    }
}
