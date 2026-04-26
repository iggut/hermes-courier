import XCTest
@testable import HermesCourier

final class HermesEnrollmentSupportTests: XCTestCase {

    func testExtractPayload_Success() throws {
        let payload = HermesEnrollmentPayload(
            gatewayUrl: "https://gateway.example.com",
            deviceId: "device-123",
            publicKeyFingerprint: "sha256:fingerprint",
            appVersion: "1.0.0",
            issuedAt: "2024-05-20T12:00:00Z"
        )
        let jsonData = try JSONEncoder().encode(payload)
        let base64Payload = jsonData.base64EncodedString()
        let uri = "hermes://enroll?payload=\(base64Payload)"

        let extracted = HermesEnrollmentSupport.extractPayloadFromURI(uri)

        XCTAssertNotNil(extracted)
        XCTAssertEqual(extracted?.gatewayUrl, payload.gatewayUrl)
        XCTAssertEqual(extracted?.deviceId, payload.deviceId)
        XCTAssertEqual(extracted?.publicKeyFingerprint, payload.publicKeyFingerprint)
        XCTAssertEqual(extracted?.appVersion, payload.appVersion)
        XCTAssertEqual(extracted?.issuedAt, payload.issuedAt)
    }

    func testExtractPayload_InvalidScheme() {
        let uri = "http://enroll?payload=dW51c2Vk"
        XCTAssertNil(HermesEnrollmentSupport.extractPayloadFromURI(uri))
    }

    func testExtractPayload_InvalidHost() {
        let uri = "hermes://invalid?payload=dW51c2Vk"
        XCTAssertNil(HermesEnrollmentSupport.extractPayloadFromURI(uri))
    }

    func testExtractPayload_MissingPayload() {
        let uri = "hermes://enroll?other=dW51c2Vk"
        XCTAssertNil(HermesEnrollmentSupport.extractPayloadFromURI(uri))
    }

    func testExtractPayload_InvalidBase64() {
        let uri = "hermes://enroll?payload=not-base64!!!"
        XCTAssertNil(HermesEnrollmentSupport.extractPayloadFromURI(uri))
    }

    func testExtractPayload_InvalidJSON() {
        let invalidJsonBase64 = Data("not json".utf8).base64EncodedString()
        let uri = "hermes://enroll?payload=\(invalidJsonBase64)"
        XCTAssertNil(HermesEnrollmentSupport.extractPayloadFromURI(uri))
    }

    func testExtractPayload_MalformedURI() {
        let uri = "not a uri"
        XCTAssertNil(HermesEnrollmentSupport.extractPayloadFromURI(uri))
    }
}
