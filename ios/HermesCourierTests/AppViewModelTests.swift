import XCTest
@testable import HermesCourier

final class MockAuthManager: HermesAuthManaging, @unchecked Sendable {
    var bootstrapSessionResult: Result<HermesAuthSession, Error>?

    func bootstrapSession() async throws -> HermesAuthSession {
        guard let result = bootstrapSessionResult else {
            fatalError("bootstrapSessionResult not set")
        }
        switch result {
        case .success(let session):
            return session
        case .failure(let error):
            throw error
        }
    }
}

final class AppViewModelMockGatewayClient: HermesGatewayClientProtocol, @unchecked Sendable {
    var bootstrapResult: Result<HermesAuthSession, Error>?
    var fetchDashboardResult: Result<HermesDashboardSnapshot, Error>?
    var fetchSessionsResult: Result<[HermesSessionSummary], Error>?
    var fetchApprovalsResult: Result<[HermesApprovalSummary], Error>?
    var fetchConversationResult: Result<[HermesConversationEvent], Error>?

    func bootstrap(device: HermesDeviceIdentity) async throws -> HermesAuthSession {
        guard let result = bootstrapResult else { fatalError("bootstrapResult not set") }
        switch result {
        case .success(let session): return session
        case .failure(let error): throw error
        }
    }

    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot {
        guard let result = fetchDashboardResult else { fatalError("fetchDashboardResult not set") }
        switch result {
        case .success(let dashboard): return dashboard
        case .failure(let error): throw error
        }
    }

    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary] {
        guard let result = fetchSessionsResult else { return [] }
        switch result {
        case .success(let sessions): return sessions
        case .failure(let error): throw error
        }
    }

    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] {
        guard let result = fetchApprovalsResult else { return [] }
        switch result {
        case .success(let approvals): return approvals
        case .failure(let error): throw error
        }
    }

    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] {
        guard let result = fetchConversationResult else { return [] }
        switch result {
        case .success(let events): return events
        case .failure(let error): throw error
        }
    }

    func fetchSessionDetail(session: HermesAuthSession, sessionId: String) async throws -> HermesSessionSummary { fatalError() }
    func submitSessionControlAction(session: HermesAuthSession, sessionId: String, action: String) async throws -> HermesSessionControlActionResult { fatalError() }
    func submitConversationMessage(session: HermesAuthSession, message: String) async throws -> HermesConversationEvent? { fatalError() }
    func submitApprovalAction(session: HermesAuthSession, approvalId: String, action: String, note: String?) async throws -> HermesApprovalActionResult { fatalError() }
    func connectRealtime(session: HermesAuthSession, onStatus: @escaping (String) -> Void, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void) -> HermesRealtimeStreamHandle {
        return HermesRealtimeStreamHandle()
    }
}

final class AppViewModelTests: XCTestCase {
    private var mockAuthManager: MockAuthManager!
    private var mockLiveClient: AppViewModelMockGatewayClient!
    private var mockFallbackClient: AppViewModelMockGatewayClient!
    private var viewModel: AppViewModel!

    @MainActor
    override func setUp() {
        super.setUp()
        mockAuthManager = MockAuthManager()
        mockLiveClient = AppViewModelMockGatewayClient()
        mockFallbackClient = AppViewModelMockGatewayClient()

        // Provide defaults to avoid fatalError during init's refresh
        mockAuthManager.bootstrapSessionResult = .failure(NSError(domain: "init", code: 0))
        mockFallbackClient.bootstrapResult = .failure(NSError(domain: "init", code: 0))

        viewModel = AppViewModel(
            authManager: mockAuthManager,
            liveClient: mockLiveClient,
            fallbackClient: mockFallbackClient
        )
    }

    @MainActor
    func testRefreshFallbackFetchDashboardFailure() async {
        // Given
        let liveError = NSError(domain: "LiveError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Live connection failed"])
        mockAuthManager.bootstrapSessionResult = .failure(liveError)

        let fallbackSession = HermesAuthSession(
            sessionId: "fallback-session",
            accessToken: "fallback-token",
            refreshToken: "fallback-refresh",
            expiresAt: "2025-01-01T00:00:00Z",
            gatewayUrl: "https://fallback.gateway",
            mtlsRequired: false,
            scope: []
        )
        mockFallbackClient.bootstrapResult = .success(fallbackSession)

        let dashboardError = NSError(domain: "DashboardError", code: 2, userInfo: [NSLocalizedDescriptionKey: "Dashboard fetch failed"])
        mockFallbackClient.fetchDashboardResult = .failure(dashboardError)

        // Setup other results for async lets in attemptFallbackConnection
        mockFallbackClient.fetchSessionsResult = .success([])
        mockFallbackClient.fetchApprovalsResult = .success([])
        mockFallbackClient.fetchConversationResult = .success([])

        // When
        await viewModel.refresh()

        // Then
        XCTAssertEqual(viewModel.bootstrapState, "Gateway unavailable")
        XCTAssertEqual(viewModel.authStatus, dashboardError.localizedDescription)
        XCTAssertEqual(viewModel.streamStatus, "Realtime stream unavailable")
    }
}
