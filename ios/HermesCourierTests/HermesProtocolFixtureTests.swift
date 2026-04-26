import XCTest
@testable import HermesCourier

final class HermesProtocolFixtureTests: XCTestCase {

    func testSessionDetail_decodes() throws {
        let s = try decodeFixture(HermesSessionSummary.self, name: "session-detail")
        XCTAssertEqual(s.sessionId, "sess-fixture-1")
        XCTAssertEqual(s.title, "Fixture session")
        XCTAssertEqual(s.status, "Running")
        XCTAssertEqual(s.updatedAt, "2026-04-21T12:00:00Z")
    }

    func testApprovalDecisionSuccess_decodes() throws {
        let r = try decodeFixture(HermesApprovalActionResult.self, name: "approval-decision-success")
        XCTAssertEqual(r.approvalId, "appr-99")
        XCTAssertEqual(r.action, "deny")
        XCTAssertEqual(r.status, "accepted")
    }

    func testSessionControlSuccess_decodesEndpointAndSupported() throws {
        let r = try decodeFixture(HermesSessionControlActionResult.self, name: "session-control-success")
        XCTAssertEqual(r.sessionId, "sess-fixture-1")
        XCTAssertEqual(r.action, "pause")
        XCTAssertEqual(r.status, "accepted")
        XCTAssertEqual(r.endpoint, "/v1/sessions/sess-fixture-1/actions")
        XCTAssertEqual(r.supported, true)
    }

    func testSessionControlUnsupported_decodes() throws {
        let r = try decodeFixture(HermesSessionControlActionResult.self, name: "session-control-unsupported")
        XCTAssertEqual(r.status, "unsupported")
        XCTAssertEqual(r.supported, false)
    }

    func testRealtimeConversationEvent_usesKindAndEventAliases() throws {
        let env = try decodeFixture(HermesRealtimeEnvelope.self, name: "realtime-conversation-event")
        XCTAssertEqual(env.type, "conversation")
        let ev = try XCTUnwrap(env.conversation)
        XCTAssertEqual(ev.eventId, "ev-fix-1")
        XCTAssertEqual(env.eventId, "env-fix-1")
        XCTAssertEqual(env.eventTimestamp, "2026-04-21T12:04:01Z")
    }

    func testRealtimeApprovalResult_usesSnakeCaseAlias() throws {
        let env = try decodeFixture(HermesRealtimeEnvelope.self, name: "realtime-approval-result")
        let r = try XCTUnwrap(env.approvalResult)
        XCTAssertEqual(r.approvalId, "appr-42")
        XCTAssertEqual(r.action, "approve")
    }

    func testRealtimeSessionControlResult_usesSnakeCaseAlias() throws {
        let env = try decodeFixture(HermesRealtimeEnvelope.self, name: "realtime-session-control-result")
        let r = try XCTUnwrap(env.sessionControlResult)
        XCTAssertEqual(r.sessionId, "sess-fix-x")
        XCTAssertEqual(r.action, "resume")
        XCTAssertEqual(r.endpoint, "/v1/sessions/sess-fix-x/actions")
        XCTAssertEqual(r.supported, true)
    }

    func testSessionsList_itemsWrapper() throws {
        let decoded = try decodeSessionListFixture(name: "sessions-list-items")
        XCTAssertEqual(decoded.count, 1)
        XCTAssertEqual(decoded[0].sessionId, "sess-items-1")
    }

    func testSessionsList_dataWrapper() throws {
        let decoded = try decodeSessionListFixture(name: "sessions-list-data")
        XCTAssertEqual(decoded[0].sessionId, "sess-data-1")
    }

    func testSessionsList_resultsWrapper() throws {
        let decoded = try decodeSessionListFixture(name: "sessions-list-results")
        XCTAssertEqual(decoded[0].sessionId, "sess-results-1")
    }

    // MARK: - Helpers (match HermesGatewayClient.decodeCollection behavior)

    private func loadFixtureData(name: String) throws -> Data {
        let bundle = Bundle(for: HermesProtocolFixtureTests.self)
        let base = name.components(separatedBy: "/").last ?? name
        if let url = bundle.url(forResource: base, withExtension: "json") {
            return try Data(contentsOf: url)
        }
        if let url = bundle.url(forResource: base, withExtension: "json", subdirectory: "shared/fixtures/protocol") {
            return try Data(contentsOf: url)
        }
        throw NSError(
            domain: "HermesProtocolFixtureTests",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "Missing \(name).json in test bundle (add shared/fixtures/protocol to HermesCourierTests resources)"],
        )
    }

    private func decodeFixture<T: Decodable>(_ type: T.Type, name: String) throws -> T {
        let data = try loadFixtureData(name: name)
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func decodeSessionListFixture(name: String) throws -> [HermesSessionSummary] {
        let data = try loadFixtureData(name: name)
        if let plain = try? JSONDecoder().decode([HermesSessionSummary].self, from: data) {
            return plain
        }
        struct Items: Decodable { let items: [HermesSessionSummary] }
        struct DataBody: Decodable { let data: [HermesSessionSummary] }
        struct Results: Decodable { let results: [HermesSessionSummary] }
        if let w = try? JSONDecoder().decode(Items.self, from: data) { return w.items }
        if let w = try? JSONDecoder().decode(DataBody.self, from: data) { return w.data }
        if let w = try? JSONDecoder().decode(Results.self, from: data) { return w.results }
        throw NSError(domain: "HermesProtocolFixtureTests", code: 2, userInfo: [NSLocalizedDescriptionKey: "Could not decode session list wrapper"])
    }
}
