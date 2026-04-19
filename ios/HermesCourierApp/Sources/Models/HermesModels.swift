
import Foundation

struct HermesSession: Identifiable, Hashable {
    let id = UUID()
    let title: String
    let status: String
    let updatedAt: String
}

struct HermesApproval: Identifiable, Hashable {
    let id = UUID()
    let title: String
    let detail: String
    let requiresBiometrics: Bool
}

struct HermesMessage: Identifiable, Hashable {
    let id = UUID()
    let sender: String
    let body: String
}
