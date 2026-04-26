import XCTest
@testable import HermesCourier

final class HermesGatewayConfigurationTests: XCTestCase {
    var store: HermesKeychainSettingsStore!
    var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        store = HermesKeychainSettingsStore()
        store.clearBaseURL()
        store.clearIdentityPath()
        store.clearCertificatePassword()

        defaults = UserDefaults.standard
        defaults.removeObject(forKey: "hermes.gateway.baseURL")
        defaults.removeObject(forKey: "hermes.gateway.identityPath")
    }

    override func tearDown() {
        store.clearBaseURL()
        store.clearIdentityPath()
        store.clearCertificatePassword()

        defaults.removeObject(forKey: "hermes.gateway.baseURL")
        defaults.removeObject(forKey: "hermes.gateway.identityPath")
        super.tearDown()
    }

    func testLoadWithDefaults() {
        let config = HermesGatewayConfiguration.load()
        XCTAssertEqual(config.baseURL.absoluteString, "https://gateway.hermes.local")
        XCTAssertNil(config.mtlsIdentityURL)
        XCTAssertNil(config.mtlsIdentityPassword)
    }

    func testLoadFromKeychain() throws {
        try store.saveBaseURL("https://custom.hermes.local")
        try store.saveIdentityPath("/custom/path/cert.p12")
        try store.saveCertificatePassword("secret")

        let config = HermesGatewayConfiguration.load()
        XCTAssertEqual(config.baseURL.absoluteString, "https://custom.hermes.local")
        XCTAssertEqual(config.mtlsIdentityURL?.path, "/custom/path/cert.p12")
        XCTAssertEqual(config.mtlsIdentityPassword, "secret")
    }

    func testSave() throws {
        let settings = HermesGatewaySettings(
            baseURL: "https://save.hermes.local",
            certificatePath: "/save/path.p12",
            certificatePassword: "save-secret"
        )

        HermesGatewayConfiguration.save(settings)

        XCTAssertEqual(store.loadBaseURL(), "https://save.hermes.local")
        XCTAssertEqual(store.loadIdentityPath(), "/save/path.p12")
        XCTAssertEqual(store.loadCertificatePassword(), "save-secret")
    }

    func testSaveClearsPasswordWhenEmpty() throws {
        try store.saveCertificatePassword("old-secret")

        let settings = HermesGatewaySettings(
            baseURL: "https://save.hermes.local",
            certificatePath: "/save/path.p12",
            certificatePassword: ""
        )

        HermesGatewayConfiguration.save(settings)

        XCTAssertNil(store.loadCertificatePassword())
    }

    func testLegacyMigration() {
        defaults.set("https://legacy.hermes.local", forKey: "hermes.gateway.baseURL")
        defaults.set("/legacy/path.p12", forKey: "hermes.gateway.identityPath")

        let config = HermesGatewayConfiguration.load()

        XCTAssertEqual(config.baseURL.absoluteString, "https://legacy.hermes.local")
        XCTAssertEqual(config.mtlsIdentityURL?.path, "/legacy/path.p12")

        XCTAssertEqual(store.loadBaseURL(), "https://legacy.hermes.local")
        XCTAssertEqual(store.loadIdentityPath(), "/legacy/path.p12")

        XCTAssertNil(defaults.string(forKey: "hermes.gateway.baseURL"))
        XCTAssertNil(defaults.string(forKey: "hermes.gateway.identityPath"))
    }

    func testImportCertificate() throws {
        let fileManager = FileManager.default
        let tempDir = fileManager.temporaryDirectory
        let dummyCertURL = tempDir.appendingPathComponent("dummy.p12")

        let dummyData = "dummy content".data(using: .utf8)!
        try dummyData.write(to: dummyCertURL)

        let importedURL = try HermesGatewayConfiguration.importCertificate(from: dummyCertURL)

        XCTAssertTrue(fileManager.fileExists(atPath: importedURL.path))

        let importedData = try Data(contentsOf: importedURL)
        XCTAssertEqual(importedData, dummyData)

        // Clean up
        try? fileManager.removeItem(at: dummyCertURL)
        try? fileManager.removeItem(at: importedURL)
    }
}
