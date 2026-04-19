
import Foundation

@MainActor
final class AppViewModel: ObservableObject {
    @Published var sessions: [HermesSession] = [
        HermesSession(title: "Build agent", status: "running", updatedAt: "18m ago"),
        HermesSession(title: "Research agent", status: "idle", updatedAt: "3h ago"),
        HermesSession(title: "Deployment agent", status: "waiting approval", updatedAt: "now"),
    ]

    @Published var approvals: [HermesApproval] = [
        HermesApproval(title: "Send message to Slack #ops", detail: "Sensitive external message", requiresBiometrics: true),
        HermesApproval(title: "Restart long-running task", detail: "May interrupt progress", requiresBiometrics: true),
    ]

    @Published var messages: [HermesMessage] = [
        HermesMessage(sender: "Hermes", body: "Awaiting your next instruction."),
        HermesMessage(sender: "You", body: "Review the latest approvals."),
        HermesMessage(sender: "Hermes", body: "I found 2 pending approval requests."),
    ]

    @Published var dashboard = DashboardSnapshot(
        activeSessionCount: 1,
        pendingApprovals: 2,
        lastSyncLabel: "12 seconds ago"
    )
}
