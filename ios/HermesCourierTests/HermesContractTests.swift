import XCTest
@testable import HermesCourier

final class HermesContractTests: XCTestCase {

    func testUserFacingVerb() {
        XCTAssertEqual(HermesApprovalDisplay.userFacingVerb(for: "deny"), "Reject")
        XCTAssertEqual(HermesApprovalDisplay.userFacingVerb(for: "reject"), "Reject")
        XCTAssertEqual(HermesApprovalDisplay.userFacingVerb(for: "approve"), "Approve")
        XCTAssertEqual(HermesApprovalDisplay.userFacingVerb(for: "  Deny  "), "Reject")
    }

    func testDecisionSheetNavigationTitle() {
        XCTAssertEqual(HermesApprovalDisplay.decisionSheetNavigationTitle(for: "approve"), "Approve approval")
        XCTAssertEqual(HermesApprovalDisplay.decisionSheetNavigationTitle(for: "deny"), "Reject approval")
        XCTAssertEqual(HermesApprovalDisplay.decisionSheetNavigationTitle(for: "reject"), "Reject approval")
        XCTAssertEqual(HermesApprovalDisplay.decisionSheetNavigationTitle(for: "hold"), "Hold approval")
    }

    func testNormalizeDecisionWire() {
        XCTAssertEqual(HermesApprovalWire.normalizeDecision("reject"), "deny")
        XCTAssertEqual(HermesApprovalWire.normalizeDecision("REJECT"), "deny")
        XCTAssertEqual(HermesApprovalWire.normalizeDecision("Approve"), "approve")
        XCTAssertEqual(HermesApprovalWire.normalizeDecision("deny"), "deny")
    }

    func testMigrateQueuedAction() {
        XCTAssertEqual(HermesApprovalWire.migrateQueuedAction("reject"), "deny")
        XCTAssertEqual(HermesApprovalWire.migrateQueuedAction("Reject"), "deny")
        XCTAssertEqual(HermesApprovalWire.migrateQueuedAction("approve"), "approve")
        XCTAssertEqual(HermesApprovalWire.migrateQueuedAction("deny"), "deny")
    }
}
