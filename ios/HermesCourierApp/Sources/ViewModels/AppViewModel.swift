import Foundation

@MainActor
final class AppViewModel: ObservableObject {
    @Published var bootstrapState: String = "Bootstrapping secure gateway"
    @Published var authStatus: String = "Waiting for device-bound challenge"
    @Published var dashboard = HermesDashboardSnapshot(
        activeSessionCount: 0,
        pendingApprovalCount: 0,
        lastSyncLabel: "Never",
        connectionState: "Disconnected"
    )
    @Published var sessions: [HermesSessionSummary] = []
    @Published var approvals: [HermesApprovalSummary] = []
    @Published var messages: [HermesConversationEvent] = [
        HermesConversationEvent(eventId: "boot-1", author: "Hermes", body: "Awaiting secure gateway bootstrap.", timestamp: "now")
    ]
    @Published var gatewaySettings = HermesGatewaySettings()
    @Published var deviceFingerprint: String = "pending-device-enrollment"
    @Published var enrollmentStatus: String = "No certificate imported yet"
    @Published var streamStatus: String = "Realtime stream disconnected"
    @Published var approvalActionStatus: String = "No approval action submitted"

    private let fallbackClient: HermesGatewayClientProtocol = HermesDemoGatewayClient()
    private var currentSession: HermesAuthSession?
    private var realtimeHandle: HermesRealtimeStreamHandle?

    init() {
        hydrateSettings()
        Task {
            await refresh()
        }
    }

    func refresh() async {
        hydrateSettings()
        bootstrapState = "Negotiating secure gateway"
        authStatus = "Requesting device challenge"
        realtimeHandle?.cancel()
        realtimeHandle = nil

        do {
            let authManager = HermesAuthManager()
            let liveClient = HermesGatewayClient()
            let session = try await authManager.bootstrapSession()
            currentSession = session
            bootstrapState = "Secure gateway ready"
            authStatus = "Session \(session.sessionId) authenticated through \(session.gatewayUrl)"
            dashboard = try await liveClient.fetchDashboard(session: session)
            sessions = try await liveClient.fetchSessions(session: session)
            approvals = try await liveClient.fetchApprovals(session: session)
            messages = try await liveClient.fetchConversation(session: session)
            connectRealtime(using: liveClient, session: session)
        } catch {
            do {
                let session = try await fallbackClient.bootstrap(device: currentDeviceIdentity())
                currentSession = session
                bootstrapState = "Demo fallback active"
                authStatus = "Using offline-safe sample data (\(error.localizedDescription))"
                dashboard = try await fallbackClient.fetchDashboard(session: session)
                sessions = try await fallbackClient.fetchSessions(session: session)
                approvals = try await fallbackClient.fetchApprovals(session: session)
                messages = try await fallbackClient.fetchConversation(session: session)
                connectRealtime(using: fallbackClient, session: session)
            } catch {
                bootstrapState = "Gateway unavailable"
                authStatus = error.localizedDescription
                streamStatus = "Realtime stream unavailable"
            }
        }
    }

    func updateGatewayBaseURL(_ value: String) {
        gatewaySettings.baseURL = value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func updateCertificatePassword(_ value: String) {
        gatewaySettings.certificatePassword = value
    }

    func importCertificate(from url: URL) {
        do {
            let copiedURL = try HermesGatewayConfiguration.importCertificate(from: url)
            gatewaySettings.certificatePath = copiedURL.path
            enrollmentStatus = "Imported certificate bundle: \(copiedURL.lastPathComponent)"
            HermesGatewayConfiguration.save(gatewaySettings)
            Task { await refresh() }
        } catch {
            enrollmentStatus = "Certificate import failed: \(error.localizedDescription)"
        }
    }

    func saveSettings() {
        HermesGatewayConfiguration.save(gatewaySettings)
        enrollmentStatus = enrollmentStatusMessage(for: gatewaySettings)
        Task { await refresh() }
    }

    func approveApproval(_ approvalId: String) {
        submitApprovalAction(approvalId: approvalId, action: "approve", note: nil)
    }

    func rejectApproval(_ approvalId: String) {
        submitApprovalAction(approvalId: approvalId, action: "reject", note: nil)
    }

    private func submitApprovalAction(approvalId: String, action: String, note: String?) {
        Task {
            guard let session = currentSession else {
                approvalActionStatus = "No authenticated session available for approval actions"
                return
            }
            do {
                let client = HermesGatewayClient()
                let result = try await client.submitApprovalAction(session: session, approvalId: approvalId, action: action, note: note)
                approvalActionStatus = "\(result.action.capitalized) approval \(result.approvalId): \(result.status)"
                await refresh()
            } catch {
                approvalActionStatus = "Approval action failed: \(error.localizedDescription)"
            }
        }
    }

    private func hydrateSettings() {
        let loaded = HermesGatewayConfiguration.load()
        gatewaySettings = HermesGatewaySettings(
            baseURL: loaded.baseURL.absoluteString,
            certificatePath: loaded.mtlsIdentityURL?.path ?? "",
            certificatePassword: loaded.mtlsIdentityPassword ?? ""
        )
        deviceFingerprint = currentDeviceIdentity().publicKeyFingerprint
        enrollmentStatus = enrollmentStatusMessage(for: gatewaySettings)
    }

    private func connectRealtime(using client: HermesGatewayClientProtocol, session: HermesAuthSession) {
        realtimeHandle?.cancel()
        realtimeHandle = client.connectRealtime(session: session, onStatus: { [weak self] status in
            Task { @MainActor in
                self?.streamStatus = status
            }
        }, onEnvelope: { [weak self] envelope in
            Task { @MainActor in
                guard let self else { return }
                if let dashboard = envelope.dashboard {
                    self.dashboard = dashboard
                }
                if let sessions = envelope.sessions {
                    self.sessions = sessions
                }
                if let approvals = envelope.approvals {
                    self.approvals = approvals
                }
                if let conversation = envelope.conversation {
                    self.messages.append(conversation)
                }
                if let result = envelope.approvalResult {
                    self.approvalActionStatus = "\(result.action.capitalized) approval \(result.approvalId): \(result.status)"
                }
                self.streamStatus = "Realtime event: \(envelope.type)"
            }
        })
    }

    private func currentDeviceIdentity() -> HermesDeviceIdentity {
        HermesDeviceIdentity(
            deviceId: HermesAuthManager.makeDeviceID(),
            platform: "ios",
            appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.1.0",
            publicKeyFingerprint: HermesKeychainChallengeSigner().publicKeyFingerprint()
        )
    }

    private func enrollmentStatusMessage(for settings: HermesGatewaySettings) -> String {
        guard !settings.certificatePath.isEmpty else { return "No certificate imported yet" }
        guard !settings.certificatePassword.isEmpty else { return "Certificate imported; password required for mTLS enrollment" }
        return "Certificate bundle enrolled and ready"
    }
}
