
import Foundation

protocol HermesGatewayClientProtocol {
    func fetchDashboard() async throws -> DashboardSnapshot
}

struct DashboardSnapshot: Hashable {
    let activeSessionCount: Int
    let pendingApprovals: Int
    let lastSyncLabel: String
}

final class HermesGatewayClient: HermesGatewayClientProtocol {
    func fetchDashboard() async throws -> DashboardSnapshot {
        DashboardSnapshot(activeSessionCount: 1, pendingApprovals: 2, lastSyncLabel: "12 seconds ago")
    }
}
