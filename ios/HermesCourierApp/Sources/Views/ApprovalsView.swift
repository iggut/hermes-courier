
import SwiftUI

struct ApprovalsView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(viewModel.approvals) { approval in
                    SectionCard(title: approval.title, subtitle: approval.requiresBiometrics ? "Requires biometrics" : nil) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(approval.detail)
                            HStack {
                                Button("Deny", role: .destructive) { }
                                    .buttonStyle(.bordered)
                                Button("Approve") { }
                                    .buttonStyle(.borderedProminent)
                            }
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Approvals")
    }
}
