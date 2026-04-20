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
    @Published var enrollmentQrPayload: String = ""
    @Published var queuedApprovalActions: Int = 0
    @Published var queuedApprovalActionQueue: [HermesQueuedApprovalAction] = []

    private let fallbackClient: HermesGatewayClientProtocol = HermesDemoGatewayClient()
    private var currentSession: HermesAuthSession?
    private var realtimeHandle: HermesRealtimeStreamHandle?
    private var isDemoGateway = false
    private var queuedActions: [HermesQueuedApprovalAction] = []
    private let queuedActionsURL: URL

    init() {
        let supportDirectory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        queuedActionsURL = supportDirectory.appendingPathComponent("hermes-queued-approval-actions.json")
        try? FileManager.default.createDirectory(at: supportDirectory, withIntermediateDirectories: true)
        loadQueuedApprovalActions()
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
            isDemoGateway = false
            let session = try await authManager.bootstrapSession()
            currentSession = session
            bootstrapState = "Secure gateway ready"
            authStatus = "Session \(session.sessionId) authenticated through \(session.gatewayUrl)"
            dashboard = try await liveClient.fetchDashboard(session: session)
            sessions = try await liveClient.fetchSessions(session: session)
            approvals = try await liveClient.fetchApprovals(session: session)
            messages = try await liveClient.fetchConversation(session: session)
            connectRealtime(using: liveClient, session: session)
            await flushQueuedApprovalActions(using: liveClient, session: session)
        } catch {
            do {
                let session = try await fallbackClient.bootstrap(device: currentDeviceIdentity())
                currentSession = session
                isDemoGateway = true
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
        let updated = gatewaySettings.with(baseURL: value.trimmingCharacters(in: .whitespacesAndNewlines))
        gatewaySettings = updated
        enrollmentStatus = enrollmentStatusMessage(for: updated)
        enrollmentQrPayload = enrollmentPayload(for: updated)
    }

    func updateCertificatePassword(_ value: String) {
        gatewaySettings = gatewaySettings.with(certificatePassword: value)
    }

    func importCertificate(from url: URL) {
        do {
            let copiedURL = try HermesGatewayConfiguration.importCertificate(from: url)
            let updated = gatewaySettings.with(certificatePath: copiedURL.path)
            gatewaySettings = updated
            enrollmentStatus = enrollmentStatusMessage(for: updated)
            enrollmentQrPayload = enrollmentPayload(for: updated)
            HermesGatewayConfiguration.save(gatewaySettings)
            Task { await refresh() }
        } catch {
            enrollmentStatus = "Certificate import failed: \(error.localizedDescription)"
        }
    }

    func importEnrollment(from scannedPayload: String) {
        guard let payload = parseEnrollmentPayload(scannedPayload) else {
            enrollmentStatus = "Enrollment QR could not be parsed"
            return
        }
        let updated = gatewaySettings.with(baseURL: payload.gatewayUrl)
        gatewaySettings = updated
        enrollmentStatus = "Enrollment QR scanned for \(payload.gatewayUrl)"
        enrollmentQrPayload = enrollmentPayload(for: updated)
    }

    func saveSettings() {
        HermesGatewayConfiguration.save(gatewaySettings)
        enrollmentStatus = enrollmentStatusMessage(for: gatewaySettings)
        enrollmentQrPayload = enrollmentPayload(for: gatewaySettings)
        Task { await refresh() }
    }

    func retryQueuedApprovalActionsNow() {
        Task {
            guard let session = currentSession, !isDemoGateway else {
                approvalActionStatus = "Queued approval actions can be retried after connecting to the live gateway"
                return
            }
            let liveClient = HermesGatewayClient()
            await flushQueuedApprovalActions(using: liveClient, session: session)
        }
    }

    func reconnectRealtime() {
        Task { await refresh() }
    }

    func approveApproval(_ approvalId: String, note: String? = nil) {
        submitApprovalAction(approvalId: approvalId, action: "approve", note: note)
    }

    func rejectApproval(_ approvalId: String, note: String? = nil) {
        submitApprovalAction(approvalId: approvalId, action: "reject", note: note)
    }

    private func submitApprovalAction(approvalId: String, action: String, note: String?) {
        Task {
            guard let session = currentSession else {
                queueApprovalAction(approvalId, action, note, reason: "No authenticated session available; queued locally")
                return
            }
            guard !isDemoGateway else {
                queueApprovalAction(approvalId, action, note, reason: "Offline demo session; approval action queued locally")
                return
            }
            let client = HermesGatewayClient()
            do {
                let result = try await client.submitApprovalAction(session: session, approvalId: approvalId, action: action, note: note)
                approvalActionStatus = "\(result.action.capitalized) approval \(result.approvalId): \(result.status)"
                await refresh()
            } catch {
                queueApprovalAction(approvalId, action, note, reason: "Offline; approval action queued for retry")
            }
        }
    }

    private func connectRealtime(using client: HermesGatewayClientProtocol, session: HermesAuthSession) {
        realtimeHandle?.cancel()
        realtimeHandle = client.connectRealtime(session: session, onStatus: { [weak self] status in
            Task { @MainActor in
                guard let self else { return }
                self.streamStatus = status
                if status.localizedCaseInsensitiveContains("connected"), !self.isDemoGateway {
                    await self.flushQueuedApprovalActions(using: client, session: session)
                }
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

    private func queueApprovalAction(_ approvalId: String, _ action: String, _ note: String?, reason: String) {
        let queued = HermesQueuedApprovalAction(
            approvalId: approvalId,
            action: action,
            note: note,
            createdAt: Date().timeIntervalSince1970
        )
        queuedActions.append(queued)
        persistQueuedApprovalActions()
        approvalActionStatus = reason
        queuedApprovalActions = queuedActions.count
        queuedApprovalActionQueue = queuedActions
    }

    private func flushQueuedApprovalActions(using client: HermesGatewayClientProtocol, session: HermesAuthSession) async {
        guard !isDemoGateway, !queuedActions.isEmpty else {
            queuedApprovalActions = queuedActions.count
            queuedApprovalActionQueue = queuedActions
            return
        }
        while !queuedActions.isEmpty {
            let queued = queuedActions[0]
            do {
                let result = try await client.submitApprovalAction(session: session, approvalId: queued.approvalId, action: queued.action, note: queued.note)
                queuedActions.removeFirst()
                persistQueuedApprovalActions()
                approvalActionStatus = "Flushed queued \(result.action) for \(result.approvalId): \(result.status)"
                queuedApprovalActions = queuedActions.count
                queuedApprovalActionQueue = queuedActions
            } catch {
                approvalActionStatus = "Queued approval action still pending: \(error.localizedDescription)"
                queuedApprovalActions = queuedActions.count
                queuedApprovalActionQueue = queuedActions
                return
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
        let identity = currentDeviceIdentity()
        deviceFingerprint = identity.publicKeyFingerprint
        enrollmentStatus = enrollmentStatusMessage(for: gatewaySettings)
        enrollmentQrPayload = enrollmentPayload(for: gatewaySettings)
        queuedApprovalActions = queuedActions.count
        queuedApprovalActionQueue = queuedActions
    }

    private func currentDeviceIdentity() -> HermesDeviceIdentity {
        HermesDeviceIdentity(
            deviceId: HermesAuthManager.makeDeviceID(),
            platform: "ios",
            appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.1.0",
            publicKeyFingerprint: "pending-keychain-bootstrap"
        )
    }

    private func parseEnrollmentPayload(_ text: String) -> HermesEnrollmentPayload? {
        guard let components = URLComponents(string: text), components.scheme == "hermes-courier-enroll" else {
            return nil
        }
        let items = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).compactMap { item in
            guard let value = item.value else { return nil }
            return (item.name, value)
        })
        guard let gatewayUrl = items["gatewayUrl"] else { return nil }
        return HermesEnrollmentPayload(
            gatewayUrl: gatewayUrl,
            deviceId: items["deviceId"] ?? currentDeviceIdentity().deviceId,
            publicKeyFingerprint: items["publicKeyFingerprint"] ?? deviceFingerprint,
            appVersion: items["appVersion"] ?? "0.1.0",
            issuedAt: items["issuedAt"] ?? ISO8601DateFormatter().string(from: Date())
        )
    }

    private func enrollmentPayload(for settings: HermesGatewaySettings) -> String {
        let identity = currentDeviceIdentity()
        var components = URLComponents()
        components.scheme = "hermes-courier-enroll"
        components.host = "gateway"
        components.queryItems = [
            URLQueryItem(name: "gatewayUrl", value: settings.baseURL),
            URLQueryItem(name: "deviceId", value: identity.deviceId),
            URLQueryItem(name: "publicKeyFingerprint", value: identity.publicKeyFingerprint),
            URLQueryItem(name: "appVersion", value: identity.appVersion),
            URLQueryItem(name: "issuedAt", value: ISO8601DateFormatter().string(from: Date())),
        ]
        return components.url?.absoluteString ?? settings.baseURL
    }

    private func enrollmentStatusMessage(for settings: HermesGatewaySettings) -> String {
        if settings.certificatePath.isEmpty {
            return "No certificate imported yet"
        }
        if settings.certificatePassword.isEmpty {
            return "Certificate imported; password required for mTLS enrollment"
        }
        return "Certificate bundle enrolled and ready"
    }

    private func persistQueuedApprovalActions() {
        do {
            let data = try JSONEncoder().encode(queuedActions)
            try data.write(to: queuedActionsURL, options: [.atomic])
        } catch {
            approvalActionStatus = "Unable to persist queued actions: \(error.localizedDescription)"
        }
    }

    private func loadQueuedApprovalActions() {
        guard let data = try? Data(contentsOf: queuedActionsURL) else {
            queuedActions = []
            queuedApprovalActions = 0
            queuedApprovalActionQueue = []
            return
        }
        do {
            queuedActions = try JSONDecoder().decode([HermesQueuedApprovalAction].self, from: data)
            queuedApprovalActions = queuedActions.count
            queuedApprovalActionQueue = queuedActions
        } catch {
            queuedActions = []
            queuedApprovalActions = 0
            queuedApprovalActionQueue = []
        }
    }

    private func persistSettingsAndRefresh() {
        HermesGatewayConfiguration.save(gatewaySettings)
        Task { await refresh() }
    }

    private func createApplicationSupportDirectoryIfNeeded() {
        let directory = queuedActionsURL.deletingLastPathComponent()
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
}

private extension HermesGatewaySettings {
    func with(baseURL: String? = nil, certificatePath: String? = nil, certificatePassword: String? = nil) -> HermesGatewaySettings {
        HermesGatewaySettings(
            baseURL: baseURL ?? self.baseURL,
            certificatePath: certificatePath ?? self.certificatePath,
            certificatePassword: certificatePassword ?? self.certificatePassword
        )
    }
}
