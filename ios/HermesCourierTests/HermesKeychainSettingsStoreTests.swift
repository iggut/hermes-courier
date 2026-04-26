import XCTest
@testable import HermesCourier

final class HermesKeychainSettingsStoreTests: XCTestCase {
    var store: HermesKeychainSettingsStore!

    override func setUp() {
        super.setUp()
        store = HermesKeychainSettingsStore()
        store.clearBaseURL()
        store.clearIdentityPath()
        store.clearCertificatePassword()
    }

    override func tearDown() {
        store.clearBaseURL()
        store.clearIdentityPath()
        store.clearCertificatePassword()
        super.tearDown()
    }

    func testBaseURLPersistence() throws {
        let testURL = "https://test.hermes.local"
        XCTAssertNil(store.loadBaseURL())

        try store.saveBaseURL(testURL)
        XCTAssertEqual(store.loadBaseURL(), testURL)

        store.clearBaseURL()
        XCTAssertNil(store.loadBaseURL())
    }

    func testIdentityPathPersistence() throws {
        let testPath = "/path/to/cert.p12"
        XCTAssertNil(store.loadIdentityPath())

        try store.saveIdentityPath(testPath)
        XCTAssertEqual(store.loadIdentityPath(), testPath)

        store.clearIdentityPath()
        XCTAssertNil(store.loadIdentityPath())
    }

    func testCertificatePasswordPersistence() throws {
        let testPassword = "secret-password"
        XCTAssertNil(store.loadCertificatePassword())

        try store.saveCertificatePassword(testPassword)
        XCTAssertEqual(store.loadCertificatePassword(), testPassword)

        store.clearCertificatePassword()
        XCTAssertNil(store.loadCertificatePassword())
    }
}
