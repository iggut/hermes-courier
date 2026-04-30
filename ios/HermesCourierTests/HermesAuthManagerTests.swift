import XCTest
@testable import HermesCourier

final class MockGatewayClient: HermesGatewayClientProtocol, @unchecked Sendable {
    var bootstrapResult: Result<HermesAuthSession, Error>?

    func bootstrap(device: HermesDeviceIdentity) async throws -> HermesAuthSession {
        guard let result = bootstrapResult else {
            fatalError("bootstrapResult not set")
        }
        switch result {
        case .success(let session):
            return session
        case .failure(let error):
            throw error
        }
    }

    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot { fatalError() }
    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary] { fatalError() }
    func fetchSessionDetail(session: HermesAuthSession, sessionId: String) async throws -> HermesSessionSummary { fatalError() }
    func submitSessionControlAction(session: HermesAuthSession, sessionId: String, action: String) async throws -> HermesSessionControlActionResult { fatalError() }
    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] { fatalError() }
    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] { fatalError() }
    func submitConversationMessage(session: HermesAuthSession, message: String) async throws -> HermesConversationEvent? { fatalError() }
    func submitApprovalAction(session: HermesAuthSession, approvalId: String, action: String, note: String?) async throws -> HermesApprovalActionResult { fatalError() }
    func connectRealtime(session: HermesAuthSession, onStatus: @escaping (String) -> Void, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void) -> HermesRealtimeStreamHandle { fatalError() }
}

final class HermesAuthManagerTests: XCTestCase {
    private var mockGatewayClient: MockGatewayClient!
    private var authManager: HermesAuthManager!

    override func setUp() {
        super.setUp()
        mockGatewayClient = MockGatewayClient()
        authManager = HermesAuthManager(gatewayClient: mockGatewayClient)
    }

    override func tearDown() {
        authManager = nil
        mockGatewayClient = nil
        super.tearDown()
    }

    func testBootstrapSessionSuccess() async throws {
        // Given
        let expectedSession = HermesAuthSession(
            sessionId: "test-session",
            accessToken: "test-access",
            refreshToken: "test-refresh",
            expiresAt: "2025-01-01T00:00:00Z",
            gatewayUrl: "https://gateway.test",
            mtlsRequired: false,
            scope: ["test"]
        )
        mockGatewayClient.bootstrapResult = .success(expectedSession)

        // When
        let result = try await authManager.bootstrapSession()

        // Then
        XCTAssertEqual(result.sessionId, expectedSession.sessionId)
        XCTAssertEqual(result.accessToken, expectedSession.accessToken)
    }

    func testBootstrapSessionFailure() async {
        // Given
        let expectedError = NSError(domain: "test", code: 123, userInfo: nil)
        mockGatewayClient.bootstrapResult = .failure(expectedError)

        // When/Then
        do {
            _ = try await authManager.bootstrapSession()
            XCTFail("Expected error to be thrown")
        } catch {
            let nsError = error as NSError
            XCTAssertEqual(nsError.domain, expectedError.domain)
            XCTAssertEqual(nsError.code, expectedError.code)
        }
    }
}
