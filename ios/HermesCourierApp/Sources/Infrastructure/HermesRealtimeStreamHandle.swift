import Foundation

final class HermesRealtimeStreamHandle {
    private let lock = NSLock()
    private var task: Task<Void, Never>?
    private var currentSocket: URLSessionWebSocketTask?

    func start(_ operation: @escaping (HermesRealtimeStreamHandle) async -> Void) {
        task = Task {
            await operation(self)
        }
    }

    func register(socket: URLSessionWebSocketTask) {
        lock.lock()
        currentSocket = socket
        lock.unlock()
    }

    func cancel() {
        lock.lock()
        let socket = currentSocket
        currentSocket = nil
        lock.unlock()
        socket?.cancel(with: .goingAway, reason: nil)
        task?.cancel()
        task = nil
    }
}
