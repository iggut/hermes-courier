import SwiftUI

private struct PendingApprovalDecision: Identifiable {
    let id = UUID()
    let approvalId: String
    let action: String
    let title: String
}

struct ApprovalsView: View {
    @EnvironmentObject private var viewModel: AppViewModel
    @State private var pendingDecision: PendingApprovalDecision?
    @State private var noteDraft: String = ""

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
                                Button("Approve with note") {
                                    noteDraft = ""
                                    pendingDecision = PendingApprovalDecision(approvalId: approval.approvalId, action: "approve", title: approval.title)
                                }
                                Button("Reject with note") {
                                    noteDraft = ""
                                    pendingDecision = PendingApprovalDecision(approvalId: approval.approvalId, action: "deny", title: approval.title)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    }
                }
                .padding()
            }
            .navigationTitle("Approvals")
            .sheet(item: $pendingDecision) { decision in
                NavigationStack {
                    Form {
                        Section("Decision") {
                            Text(Self.displayVerb(for: decision.action) + " — " + decision.title)
                            Text("Add a note before submitting the approval action.")
                        }
                        Section("Comment") {
                            TextField("Note / comment", text: $noteDraft, axis: .vertical)
                                .lineLimit(3, reservesSpace: true)
                        }
                    }
                    .navigationTitle(Self.displayVerb(for: decision.action))
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { pendingDecision = nil }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Send") {
                                let note = noteDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                                let submittedNote = note.isEmpty ? nil : note
                                if decision.action == "approve" {
                                    viewModel.approveApproval(decision.approvalId, note: submittedNote)
                                } else {
                                    viewModel.rejectApproval(decision.approvalId, note: submittedNote)
                                }
                                pendingDecision = nil
                                noteDraft = ""
                            }
                        }
                    }
                }
            }
        }
    }

    private static func displayVerb(for wireAction: String) -> String {
        HermesApprovalDisplay.userFacingVerb(for: wireAction)
    }
}
