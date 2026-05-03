
import Foundation

enum HermesConversationActionState: Equatable {
    case idle
    case sending
    case sent
    case failed
}


struct HermesMessage: Identifiable, Hashable {
    let id = UUID()
    let sender: String
    let body: String
}
