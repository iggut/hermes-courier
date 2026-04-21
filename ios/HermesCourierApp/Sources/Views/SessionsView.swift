import SwiftUI

struct SessionsView: View {
    @EnvironmentObject private var viewModel: AppViewModel

    private var emptySessionsMessage: String {
        let s = viewModel.bootstrapState
        if s.localizedCaseInsensitiveContains("negotiating") || s.localizedCaseInsensitiveContains("bootstrapping") {
            return "Loading sessions…"
        }
        if s.localizedCaseInsensitiveContains("gateway unavailable") {
            return "Sessions unavailable. The gateway could not be reached. Check connection in Settings, then refresh."
        }
        return "No sessions yet. After you refresh from the dashboard or settings, new gateway activity appears here."
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Browse active and historical Hermes runs.")
                }

                if viewModel.sessions.isEmpty {
                    Text(emptySessionsMessage)
                        .foregroundStyle(.secondary)
                }

                ForEach(viewModel.sessions) { session in
                    NavigationLink(value: session.sessionId) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(session.title).font(.headline)
                            Text(session.status)
                            Text(session.updatedAt).font(.caption).foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle("Sessions")
            .navigationDestination(for: String.self) { sessionId in
                SessionDetailContainerView(sessionId: sessionId)
            }
        }
    }
}
