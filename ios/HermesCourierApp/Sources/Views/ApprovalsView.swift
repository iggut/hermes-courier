import SwiftUI

struct ApprovalsView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.approvals) { approval in
                        VStack(alignment: .leading, spacing: 10) {
                            Text(approval.title).font(.headline)
                            Text(approval.detail)
                            Text(approval.requiresBiometrics ? "Biometrics required" : "Biometrics optional")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            HStack {
                                Button("Approve") {
                                    viewModel.approveApproval(approval.approvalId)
                                }
                                .buttonStyle(.borderedProminent)

                                Button("Reject") {
                                    viewModel.rejectApproval(approval.approvalId)
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    }

                    if viewModel.approvals.isEmpty {
                        Text("No approvals are waiting right now.")
                            .padding()
                    }
                }
                .padding()
            }
            .navigationTitle("Approvals")
        }
    }
}
